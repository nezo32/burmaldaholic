package dev.nezo.burmaldaholic.vip;

import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.sections.ContractsConfig;
import dev.nezo.burmaldaholic.core.config.sections.VipConfig;
import dev.nezo.burmaldaholic.core.earnings.EarningToasts;
import dev.nezo.burmaldaholic.core.earnings.Earnings;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.rng.CasinoRng;
import dev.nezo.burmaldaholic.core.rng.OddsContext;
import dev.nezo.burmaldaholic.core.rng.OddsService;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.vip.logic.ContractRules;
import dev.nezo.burmaldaholic.vip.logic.VipRules;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntUnaryOperator;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Daily contracts runtime (GAME_DESIGN.md §3.4.4). The spec lists contracts under core, which ships
 * none on Java (same decision as Bedrock), so the vip module runs them: slots, targets and rewards
 * depend on the VIP tier.
 *
 * <p>Sources: ores / mature crops (block break), kills (death event), fish and villager trades
 * (vanilla stats {@code fish_caught} / {@code traded_with_villager} via {@code ServerPlayerMixin}),
 * smelting (furnace output slot via {@code FurnaceResultSlotMixin}), Nether travel (sampled every
 * second), wagers / blackjack wins / slot spins / poker hands ({@code PLAY_RESOLVED}).
 * {@code roulette_red} needs bet details {@code PlayResult} does not carry: it joins the pool once a
 * game reports it through {@link VipApi} ({@code "burmaldaholic:vip/contract"}).
 */
public final class Contracts {
	/** Contracts no Java event can observe yet (need bet details from the game). */
	static final List<String> UNOBSERVABLE = List.of("roulette_red");
	/** Unobservable contracts a game has reported through {@link VipApi} (join the pool from then on). */
	private static final java.util.Set<String> SOURCES = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private static final Map<UUID, double[]> LAST_POS = new HashMap<>();
	private static final Map<UUID, Double> TRAVEL = new HashMap<>();

	private Contracts() {}

	static void register() {
		PlayerBlockBreakEvents.AFTER.register(Contracts::onBlockBroken);
		ServerLivingEntityEvents.AFTER_DEATH.register(Contracts::onDeath);
	}

	static void registerSource(String id) {
		if (UNOBSERVABLE.contains(id)) {
			SOURCES.add(id);
		}
	}

	static void forget(UUID id) {
		LAST_POS.remove(id);
		TRAVEL.remove(id);
	}

	public static boolean enabled() {
		return CasinoConfig.contracts().enabled;
	}

	static long today(MinecraftServer server) {
		return server.overworld().getGameTime() / 24000L;
	}

	static long ticksToReset(MinecraftServer server) {
		return 24000L - server.overworld().getGameTime() % 24000L;
	}

	static ContractRules.Params params(MinecraftServer server, UUID id) {
		ContractsConfig c = CasinoConfig.contracts();
		VipConfig v = CasinoConfig.vip();
		int tier = VipService.tier(server, id);
		Map<String, Integer> weights = new HashMap<>(c.weight);
		UNOBSERVABLE.stream().filter(k -> !SOURCES.contains(k)).forEach(k -> weights.put(k, 0));
		return new ContractRules.Params(tier, c.tierScaling, VipRules.contractBonus(tier, v.contractBonus.silver, v.contractBonus.gold),
			c.rewardMultiplier, weights);
	}

	static int slots(MinecraftServer server, UUID id) {
		return VipRules.contractSlots(VipService.tier(server, id), CasinoConfig.contracts().slots);
	}

	private static IntUnaryOperator rng(UUID id) {
		CasinoRng rng = OddsService.get().rng(new OddsContext(id, VipModule.ID, 0));
		return rng::nextInt;
	}

	/** Today's contracts; generated (and announced if {@code announce}) on the first call of a new world day. */
	static ContractRules.State state(ServerPlayer player, boolean announce) {
		MinecraftServer server = player.level().getServer();
		VipData data = VipData.get(server);
		VipData.Record rec = data.player(player.getUUID());
		long today = today(server);
		if (ContractRules.isStale(rec.contracts, today)) {
			rec.contracts = ContractRules.generate(rng(player.getUUID()), today, slots(server, player.getUUID()), params(server, player.getUUID()));
			data.setDirty();
			if (announce && !rec.contracts.list.isEmpty()) {
				player.sendSystemMessage(Component.translatable("msg.burmaldaholic.contracts.new").withStyle(ChatFormatting.YELLOW));
			}
			VipService.markSync(player);
		}
		return rec.contracts;
	}

	/** Daily rollover / first join of the day. */
	static void tick(ServerPlayer player) {
		if (enabled()) {
			state(player, true);
			if (player.level().dimension() == Level.NETHER) {
				sampleTravel(player);
			} else {
				forget(player.getUUID());
			}
		}
	}

	/** After a VIP promotion: extra slots appear immediately. */
	static void topUp(ServerPlayer player) {
		if (!enabled()) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		ContractRules.State s = state(player, false);
		if (!ContractRules.topUp(rng(player.getUUID()), s, slots(server, player.getUUID()), params(server, player.getUUID())).isEmpty()) {
			VipData.get(server).setDirty();
			VipService.markSync(player);
		}
	}

	/** Adds progress to today's open contracts with this id and pays completed ones. */
	public static void progress(ServerPlayer player, String id, long amount) {
		if (amount <= 0 || !enabled() || !CasinoMode.isEnabled(player) || player.hasDisconnected()) {
			return;
		}
		ContractRules.State s = state(player, true);
		if (!s.wants(id)) {
			return;
		}
		List<ContractRules.Contract> done = ContractRules.addProgress(s, id, amount);
		VipData.get(player.level().getServer()).setDirty();
		VipService.markSync(player);
		for (ContractRules.Contract c : done) {
			complete(player, c);
		}
		if (!done.isEmpty() && s.allDone()) {
			player.sendSystemMessage(Component.translatable("msg.burmaldaholic.contracts.all_done").withStyle(ChatFormatting.GOLD));
		}
	}

	static Component task(String id, long target) {
		return Component.translatable("gui.burmaldaholic.contracts.task." + id, Texts.number(target));
	}

	private static void complete(ServerPlayer player, ContractRules.Contract c) {
		long got = 0;
		if (c.reward > 0) {
			got = Economies.get().deposit(player, c.reward, new Transaction(VipModule.ID, "contract", Transaction.Kind.EARNING));
			EarningToasts.show(player, got > 0 ? got : c.reward, Component.translatable("msg.burmaldaholic.core.source.contract"), "vip:contract");
		}
		player.sendSystemMessage(Component.translatable("msg.burmaldaholic.contracts.completed", task(c.id, c.target), Texts.chips(got))
			.withStyle(ChatFormatting.GREEN));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
	}

	/** Reroll request from the Casino Menu (§3.4.4). Returns an error to show, or null. */
	public static Component reroll(ServerPlayer player, int index) {
		if (!enabled()) {
			return Component.translatable("gui.burmaldaholic.vip.contracts_off");
		}
		MinecraftServer server = player.level().getServer();
		ContractRules.State s = state(player, true);
		ContractRules.RerollError err = ContractRules.canReroll(s, index);
		if (err == ContractRules.RerollError.REROLLED) {
			return Component.translatable("gui.burmaldaholic.contracts.rerolled_already");
		}
		if (err != null) {
			return Component.translatable("gui.burmaldaholic.vip.contract_unavailable");
		}
		long cost = Math.max(0, CasinoConfig.contracts().rerollCost);
		Transaction tx = new Transaction(VipModule.ID, "contract_reroll", Transaction.Kind.OTHER);
		if (cost > 0 && !Economies.get().tryWithdraw(player, cost, tx)) {
			return Component.translatable("gui.burmaldaholic.error.insufficient_funds", Texts.number(Economies.get().balance(player)));
		}
		ContractRules.RerollResult r = ContractRules.reroll(rng(player.getUUID()), s, index, params(server, player.getUUID()));
		if (!r.ok()) {
			if (cost > 0) {
				Economies.get().deposit(player, cost, new Transaction(VipModule.ID, "contract_reroll_refund", Transaction.Kind.REFUND));
			}
			return Component.translatable("gui.burmaldaholic.vip.contract_unavailable");
		}
		VipData.get(server).setDirty();
		VipService.markSync(player);
		player.sendSystemMessage(Component.translatable("msg.burmaldaholic.contracts.rerolled", Texts.chipsAcc(cost)));
		return null;
	}

	// ---- event sources ------------------------------------------------------------------------

	private static boolean counts(Player player) {
		return player instanceof ServerPlayer sp && !sp.isCreative() && !sp.isSpectator() && CasinoMode.isEnabled(sp) && enabled();
	}

	private static void onBlockBroken(Level level, Player player, BlockPos pos, BlockState state, BlockEntity be) {
		if (!counts(player) || !(level instanceof ServerLevel serverLevel)) {
			return;
		}
		ServerPlayer sp = (ServerPlayer) player;
		Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		if (!id.getNamespace().equals("minecraft")) {
			return;
		}
		String ore = ContractRules.oreContract(id.getPath());
		if (ore != null) {
			// Only ore that drops its normal loot (correct tool, no Silk Touch), like chip earnings (§3.4.1).
			if (state.requiresCorrectToolForDrops() && !sp.hasCorrectToolForDrops(state)) {
				return;
			}
			var silk = serverLevel.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH);
			if (EnchantmentHelper.getItemEnchantmentLevel(silk, sp.getItemInHand(InteractionHand.MAIN_HAND)) > 0) {
				return;
			}
			progress(sp, ore, 1);
		} else if (ContractRules.isHarvestCrop(id.getPath()) && state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
			progress(sp, "harvest", 1);
		}
	}

	private static void onDeath(LivingEntity entity, DamageSource source) {
		if (!(entity.level() instanceof ServerLevel) || entity instanceof Player) {
			return;
		}
		ServerPlayer killer = entity.getLastHurtByPlayer() instanceof ServerPlayer p ? p
			: source.getEntity() instanceof ServerPlayer p2 ? p2 : null;
		if (killer == null || !counts(killer) || Earnings.isNoReward(entity)) {
			return;
		}
		Identifier typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
		if (!typeId.getNamespace().equals("minecraft")) {
			return;
		}
		for (String id : ContractRules.killContracts(typeId.getPath(), entity instanceof Enemy)) {
			progress(killer, id, 1);
		}
	}

	/** Called by {@code ServerPlayerMixin} for every stat increase. */
	public static void onStat(ServerPlayer player, Stat<?> stat, int amount) {
		if (amount <= 0 || stat.getType() != Stats.CUSTOM || !counts(player)) {
			return;
		}
		Object value = stat.getValue();
		if (Stats.FISH_CAUGHT.equals(value)) {
			progress(player, "fish", amount);
		} else if (Stats.TRADED_WITH_VILLAGER.equals(value)) {
			progress(player, "trade", amount);
		}
	}

	/** Called by {@code FurnaceResultSlotMixin} when a player takes smelted items. */
	public static void onSmelted(Player player, int count) {
		if (count > 0 && counts(player)) {
			progress((ServerPlayer) player, "smelt", count);
		}
	}

	private static void sampleTravel(ServerPlayer player) {
		UUID id = player.getUUID();
		if (!VipData.get(player.level().getServer()).player(id).contracts.wants("explore_nether")) {
			forget(id);
			return;
		}
		double[] now = {player.getX(), player.getZ()};
		double[] prev = LAST_POS.put(id, now);
		if (prev == null || player.isCreative() || player.isSpectator()) {
			return;
		}
		double acc = TRAVEL.getOrDefault(id, 0.0) + ContractRules.travelStep(prev[0], prev[1], now[0], now[1], 80);
		long whole = (long) Math.floor(acc);
		TRAVEL.put(id, acc - whole);
		if (whole > 0) {
			progress(player, "explore_nether", whole);
		}
	}
}
