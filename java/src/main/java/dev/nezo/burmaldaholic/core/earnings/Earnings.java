package dev.nezo.burmaldaholic.core.earnings;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.sections.EconomyConfig;
import dev.nezo.burmaldaholic.core.data.CasinoWorldData;
import dev.nezo.burmaldaholic.core.data.PlayerRecord;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Chip earnings from normal play (GAME_DESIGN.md §3.4): ores, mob kills, villager trades. All guarded
 * by casino mode; credits go through the economy (so garnishment applies) and show a merged toast.
 * Other modules mark their mobs as non-rewarding with {@link #markNoReward(Entity)} (chaos waves,
 * debt collectors).
 */
public final class Earnings {
	private static final Map<String, TagKey<Block>> ORE_TAGS = new java.util.LinkedHashMap<>();
	private static final Map<UUID, Map<String, RewardRules.KillWindow>> KILLS = new HashMap<>();
	private static AttachmentType<Boolean> noReward;

	static {
		for (String tag : RewardRules.ORE_TAGS) {
			ORE_TAGS.put(tag, TagKey.create(Registries.BLOCK, Identifier.withDefaultNamespace(tag)));
		}
	}

	private Earnings() {}

	public static void register() {
		noReward = AttachmentRegistry.createPersistent(Burmaldaholic.id("no_reward"), Codec.BOOL);
		EarningToasts.register();
		PlayerBlockBreakEvents.AFTER.register(Earnings::onBlockBroken);
		ServerLivingEntityEvents.AFTER_DEATH.register(Earnings::onDeath);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> KILLS.remove(handler.getPlayer().getUUID()));
	}

	/** This mob never pays a kill reward (chaos waves, debt collectors, spawner mobs). */
	public static void markNoReward(Entity entity) {
		entity.setAttached(noReward, true);
	}

	public static boolean isNoReward(Entity entity) {
		return Boolean.TRUE.equals(entity.getAttached(noReward));
	}

	private static void credit(ServerPlayer player, long amount, Component source, String sourceKey, String detail) {
		if (amount <= 0) {
			return;
		}
		long credited = Economies.get().deposit(player, amount, Transaction.earning(detail));
		EarningToasts.show(player, credited > 0 ? credited : amount, source, sourceKey);
	}

	// ---- ores -----------------------------------------------------------------------------------

	private static void onBlockBroken(Level level, Player player, BlockPos pos, BlockState state, net.minecraft.world.level.block.entity.BlockEntity be) {
		if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel serverLevel) || !CasinoMode.isEnabled(level)) {
			return;
		}
		if (sp.isCreative() || sp.isSpectator()) {
			return;
		}
		String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		String tag = null;
		for (Map.Entry<String, TagKey<Block>> e : ORE_TAGS.entrySet()) {
			if (state.is(e.getValue())) {
				tag = e.getKey();
				break;
			}
		}
		if (!BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals("minecraft")) {
			return;
		}
		int chips = RewardRules.ore(id, tag, CasinoConfig.economy().ore);
		if (chips <= 0) {
			return;
		}
		// Must drop its normal loot: correct tool, no Silk Touch (§3.4.1).
		if (state.requiresCorrectToolForDrops() && !sp.hasCorrectToolForDrops(state)) {
			return;
		}
		ItemStack tool = sp.getItemInHand(InteractionHand.MAIN_HAND);
		var silk = serverLevel.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH);
		if (EnchantmentHelper.getItemEnchantmentLevel(silk, tool) > 0) {
			return;
		}
		if (state.is(Blocks.ANCIENT_DEBRIS)
			&& CasinoWorldData.get(serverLevel.getServer()).consumePlacedDebris(serverLevel.dimension().identifier().toString(), pos)) {
			return;
		}
		credit(sp, chips, state.getBlock().getName(), "ore:" + id, "ore");
	}

	/** Called by the BlockItem mixin when a player places ancient debris. */
	public static void onBlockPlaced(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.is(Blocks.ANCIENT_DEBRIS)) {
			CasinoWorldData.get(level.getServer()).addPlacedDebris(level.dimension().identifier().toString(), pos,
				CasinoConfig.economy().ore.placedDebrisLedgerSize);
		}
	}

	// ---- mobs -----------------------------------------------------------------------------------

	private static void onDeath(LivingEntity entity, net.minecraft.world.damagesource.DamageSource source) {
		if (!(entity.level() instanceof ServerLevel level) || !CasinoMode.isEnabled(level) || entity instanceof Player) {
			return;
		}
		ServerPlayer killer = null;
		if (entity.getLastHurtByPlayer() instanceof ServerPlayer p) {
			killer = p;
		} else if (source.getEntity() instanceof ServerPlayer p) {
			killer = p;
		}
		if (killer == null || killer.isCreative() || killer.isSpectator()) {
			return;
		}
		EconomyConfig.Mob cfg = CasinoConfig.economy().mob;
		if (isNoReward(entity) && !cfg.spawnerRewards) {
			return;
		}
		Identifier typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
		if (!typeId.getNamespace().equals("minecraft")) {
			return;
		}
		MinecraftServer server = level.getServer();
		CasinoWorldData data = CasinoWorldData.get(server);
		int cube = entity instanceof AbstractCubeMob cubeMob ? cubeMob.getSize() : 0;
		boolean trident = entity.getMainHandItem().is(Items.TRIDENT) || entity.getOffhandItem().is(Items.TRIDENT);
		long base = RewardRules.mob(typeId.getPath(), cube, trident, data.dragonKilled(), cfg);
		if (typeId.getPath().equals("ender_dragon") && !data.dragonKilled()) {
			data.setDragonKilled();
		}
		if (base <= 0) {
			return;
		}
		boolean hard = level.getDifficulty() == Difficulty.HARD || server.isHardcore();
		long amount = RewardRules.applyDifficulty(base, hard, cfg.hardMultiplier);
		int n = KILLS.computeIfAbsent(killer.getUUID(), k -> new HashMap<>())
			.computeIfAbsent(typeId.getPath(), k -> new RewardRules.KillWindow())
			.record(server.overworld().getGameTime(), cfg.windowTicks);
		amount = RewardRules.diminished(amount, n, cfg.fullRewardKills, cfg.reducedRewardKills, cfg.reducedRewardFactor);
		credit(killer, amount, entity.getType().getDescription(), "mob:" + typeId.getPath(), "mob");
	}

	// ---- trades ---------------------------------------------------------------------------------

	/** Called by the AbstractVillager mixin after every completed villager / wandering-trader trade. */
	public static void onTrade(ServerPlayer player, MerchantOffer offer) {
		if (!CasinoMode.isEnabled(player)) {
			return;
		}
		int emeralds = 0;
		for (ItemStack s : new ItemStack[] {offer.getCostA(), offer.getCostB(), offer.getResult()}) {
			if (s.is(Items.EMERALD)) {
				emeralds += s.getCount();
			}
		}
		EconomyConfig cfg = CasinoConfig.economy();
		MinecraftServer server = player.level().getServer();
		CasinoWorldData data = CasinoWorldData.get(server);
		PlayerRecord rec = data.player(player.getUUID());
		long day = server.overworld().getGameTime() / 24000L;
		if (rec.tradeDay != day) {
			rec.tradeDay = day;
			rec.tradeChips = 0;
		}
		long amount = Math.min(RewardRules.trade(emeralds, cfg.trade), RewardRules.tradeAllowance(rec.tradeChips, cfg.trade.dailyCap));
		if (amount <= 0) {
			return;
		}
		rec.tradeChips += amount;
		data.setDirty();
		credit(player, amount, Component.translatable("msg.burmaldaholic.core.source.trade"), "trade", "trade");
	}
}
