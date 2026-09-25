package dev.nezo.burmaldaholic.vip;

import dev.nezo.burmaldaholic.core.CoreContent;
import dev.nezo.burmaldaholic.core.PlayerSync;
import dev.nezo.burmaldaholic.core.advancement.CasinoAdvancements;
import dev.nezo.burmaldaholic.core.bots.BotRounds;
import dev.nezo.burmaldaholic.core.bots.logic.BotEconomyMath;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.sections.VipConfig;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.events.CasinoEvents;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.service.VipTiers;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.vip.logic.CashbackRules;
import dev.nezo.burmaldaholic.vip.logic.ContractRules;
import dev.nezo.burmaldaholic.vip.logic.VipRules;
import dev.nezo.burmaldaholic.vip.net.VipErrorPayload;
import dev.nezo.burmaldaholic.vip.net.VipSyncPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * VIP runtime (GAME_DESIGN.md §12): lifetime wagered from {@code PLAY_RESOLVED}, the tier provider for
 * core ({@code CoreServices.setVip}), promotions (title + sound + chat, Netherite broadcast), daily
 * cashback (rate × theoretical loss), cosmetics (win particles, Netherite aura, Diamond Casino Card,
 * name color / chat title via {@code PlayerMixin}) and the client sync for the Casino Menu and HUD.
 */
public final class VipService {
	private static final Set<UUID> DIRTY = new HashSet<>();
	private static final Map<UUID, VipSyncPayload> LAST = new HashMap<>();

	/** §19 advancement per tier (index = tier; Bronze has none). */
	static final String[] VIP_ADVANCEMENTS = {"", "vip_silver", "vip_gold", "vip_platinum", "vip_diamond", "vip_netherite"};

	private VipService() {}

	// ---- config ------------------------------------------------------------------------------

	public static long[] thresholds() {
		VipConfig.Threshold t = CasinoConfig.vip().threshold;
		return new long[] {t.silver, t.gold, t.platinum, t.diamond, t.netherite};
	}

	public static double[] cashbackRates() {
		VipConfig.Cashback c = CasinoConfig.vip().cashback;
		return new double[] {c.gold, c.platinum, c.diamond, c.netherite};
	}

	// ---- state -------------------------------------------------------------------------------

	/** Earned tier (never lost): max(announced tier, tier by lifetime wagered). Works offline. */
	public static int tier(MinecraftServer server, UUID id) {
		if (server == null) {
			return VipRules.BRONZE;
		}
		VipData.Record r = VipData.get(server).peek(id);
		return r == null ? VipRules.BRONZE : Math.max(r.tier, VipRules.tierFor(r.wagered, thresholds()));
	}

	public static long wagered(MinecraftServer server, UUID id) {
		VipData.Record r = VipData.get(server).peek(id);
		return r == null ? 0 : r.wagered;
	}

	/** Admin: sets lifetime wagered (tiers already reached are kept). */
	public static void setWagered(ServerPlayer player, long wagered) {
		MinecraftServer server = player.level().getServer();
		VipData.get(server).player(player.getUUID()).wagered = Math.max(0, wagered);
		VipData.get(server).setDirty();
		checkPromotion(player);
		markSync(player);
	}

	// ---- settled wagers ----------------------------------------------------------------------

	static void onPlayResolved(ServerPlayer player, CasinoEvents.PlayResult result) {
		if (!CasinoMode.isEnabled(player)) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		VipData data = VipData.get(server);
		VipData.Record rec = data.player(player.getUUID());
		long bet = Math.max(0, result.bet());
		long credit = wagerCredit(result, bet);
		rec.wagered = rec.wagered > Long.MAX_VALUE - credit ? Long.MAX_VALUE : rec.wagered + credit;
		long best = VipRules.biggestWin(rec.biggestWin, bet, result.payout());
		if (best != rec.biggestWin) {
			rec.biggestWin = best;
			rec.biggestWinGame = result.gameId();
		}
		boolean eligible = CashbackRules.eligible(result.houseBanked(), result.ownedCasino(), result.pawn()) && !BotRounds.vsBots(result);
		CashbackRules.Roll roll = CashbackRules.record(rec.ledger, Contracts.today(server), bet, result.payout(), result.houseEdge(), eligible);
		rec.ledger = roll.ledger();
		data.setDirty();
		if (roll.closed() != null) {
			payCashback(player, roll.closed());
		}
		checkPromotion(player);
		if (result.won() && !result.deferred()) {
			winParticles(player);
		}
		Map<String, Long> progress = new java.util.LinkedHashMap<>(ContractRules.playContracts(result.gameId(), bet, result.payout(), result.tags()));
		if (progress.containsKey("wager")) {
			if (credit > 0) {
				progress.put("wager", credit); // the `wager` contract gets the same (bot-weighted) credit as VIP (BOTS.md §5.3)
			} else {
				progress.remove("wager");
			}
		}
		progress.forEach((id, n) -> Contracts.progress(player, id, n));
		markSync(player);
	}

	/**
	 * Lifetime-wagered / {@code wager} contract credit of a settled round: PvP-engine matches only with
	 * {@code pvp.countsTowardVip}; rounds against money bots weighted by {@code bots.vipWagerWeight} × the bots'
	 * share of the counterparty money (BOTS.md §5.3).
	 */
	static long wagerCredit(CasinoEvents.PlayResult result, long bet) {
		if ("pvp".equals(result.gameId()) && !CasinoConfig.pvp().countsTowardVip) {
			return 0;
		}
		if (BotRounds.vsBots(result)) {
			return BotEconomyMath.vipCredit(bet, CasinoConfig.bots().vipWagerWeight, BotRounds.botShare(result));
		}
		return bet;
	}

	// ---- cashback ----------------------------------------------------------------------------

	/** Pays the closed day's cashback when the world day changed (online players, checked every second). */
	static void rollDay(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		VipData data = VipData.get(server);
		VipData.Record rec = data.player(player.getUUID());
		CashbackRules.Roll roll = CashbackRules.roll(rec.ledger, Contracts.today(server));
		if (roll.closed() == null && rec.ledger != null) {
			return;
		}
		rec.ledger = roll.ledger();
		data.setDirty();
		markSync(player);
		if (roll.closed() != null) {
			payCashback(player, roll.closed());
		}
	}

	private static void payCashback(ServerPlayer player, CashbackRules.DayLedger day) {
		double rate = VipRules.cashbackRate(tier(player.level().getServer(), player.getUUID()), cashbackRates());
		long amount = CashbackRules.amount(day.theo(), rate);
		if (amount <= 0) {
			return;
		}
		long got = Economies.get().deposit(player, amount, new Transaction(VipModule.ID, "cashback", Transaction.Kind.EARNING));
		if (got > 0) {
			player.sendSystemMessage(Component.translatable("msg.burmaldaholic.vip.cashback", Texts.chips(got)).withStyle(ChatFormatting.GREEN));
		}
	}

	// ---- promotion ---------------------------------------------------------------------------

	/** Announces each newly reached tier once (also after an admin lowers the thresholds). */
	static void checkPromotion(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		VipData data = VipData.get(server);
		VipData.Record rec = data.player(player.getUUID());
		int now = tier(server, player.getUUID());
		for (int t = VipRules.SILVER; t <= Math.min(now, VipRules.NETHERITE); t++) {
			CasinoAdvancements.grant(player, VIP_ADVANCEMENTS[t]); // no-op once granted
		}
		if (now <= rec.tier) {
			return;
		}
		List<Integer> gained = VipRules.promotions(rec.tier, now);
		int before = rec.tier;
		rec.tier = now;
		data.setDirty();
		for (int i = 0; i < gained.size(); i++) {
			announce(player, gained.get(i), i == gained.size() - 1, before);
		}
		Contracts.topUp(player);
		if (now >= VipRules.DIAMOND) {
			swapCards(player);
		}
		PlayerSync.markDirty(player);
		markSync(player);
	}

	private static void announce(ServerPlayer player, int tier, boolean loud, int fromTier) {
		Component name = VipTiers.name(tier);
		if (loud && VipFx.celebrate(player, fromTier, tier)) {
			// modded client: the tier-up overlay replaces the vanilla title (global §4.11); spectators get the ring
		} else if (loud) {
			player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
			player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("msg.burmaldaholic.vip.promoted_title", name)));
			player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("gui.burmaldaholic.vip.max_bet", Texts.chips(VipTiers.maxBet(tier)))));
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1.0f);
			ring(player, ParticleTypes.TOTEM_OF_UNDYING, 1.0, 0.5, 14);
			if (tier >= VipRules.NETHERITE) {
				ring(player, ParticleTypes.SOUL_FIRE_FLAME, 1.2, 0.2, 18);
			}
		}
		player.sendSystemMessage(Component.translatable("msg.burmaldaholic.vip.promoted", name, Texts.chips(VipTiers.maxBet(tier))).withStyle(ChatFormatting.GOLD));
		if (tier == VipRules.NETHERITE && CasinoConfig.vip().announceNetherite) {
			Component msg = Component.translatable("msg.burmaldaholic.vip.netherite_broadcast", player.getName()).withStyle(ChatFormatting.DARK_PURPLE);
			for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {
				if (other != player) {
					other.sendSystemMessage(msg);
				}
			}
			VipFx.netheriteToast(player);
		}
	}

	// ---- cosmetics ---------------------------------------------------------------------------

	private static void ring(ServerPlayer player, ParticleOptions type, double radius, double y, int n) {
		ServerLevel level = player.level();
		for (int i = 0; i < n; i++) {
			double a = i * Math.PI * 2 / n;
			level.sendParticles(type, player.getX() + Math.cos(a) * radius, player.getY() + y, player.getZ() + Math.sin(a) * radius, 1, 0, 0.05, 0, 0.01);
		}
	}

	/** Gold+: gold sparkle on wins; Netherite: soul-fire aura (UI/§12 cosmetics); one particle call per layer. */
	static void winParticles(ServerPlayer player) {
		VipFx.winAura(player, tier(player.level().getServer(), player.getUUID()));
	}

	/** Diamond+: every Casino Card in the inventory becomes the Diamond Casino Card (same menu). */
	static void swapCards(ServerPlayer player) {
		if (VipModule.DIAMOND_CARD == null || tier(player.level().getServer(), player.getUUID()) < VipRules.DIAMOND) {
			return;
		}
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (!stack.isEmpty() && stack.is(CoreContent.CASINO_CARD)) {
				inv.setItem(i, new ItemStack(VipModule.DIAMOND_CARD, stack.getCount()));
			}
		}
	}

	// ---- client sync -------------------------------------------------------------------------

	public static void markSync(ServerPlayer player) {
		DIRTY.add(player.getUUID());
	}

	static void forget(UUID id) {
		DIRTY.remove(id);
		LAST.remove(id);
	}

	static VipSyncPayload snapshot(ServerPlayer player, boolean open) {
		MinecraftServer server = player.level().getServer();
		VipData.Record rec = VipData.get(server).player(player.getUUID());
		boolean on = Contracts.enabled();
		List<VipSyncPayload.ContractView> list = new ArrayList<>();
		if (on && rec.contracts != null && rec.contracts.day == Contracts.today(server)) {
			for (ContractRules.Contract c : rec.contracts.list) {
				list.add(new VipSyncPayload.ContractView(c.id, c.target, c.progress, c.reward, c.done, c.rerolled));
			}
		}
		CashbackRules.DayLedger l = rec.ledger != null && rec.ledger.day() == Contracts.today(server) ? rec.ledger : null;
		return new VipSyncPayload(open, rec.wagered, tier(server, player.getUUID()), l == null ? 0 : l.staked(), l == null ? 0 : l.returned(),
			on, Contracts.ticksToReset(server), Math.max(0, CasinoConfig.contracts().rerollCost), List.copyOf(list),
			Math.max(0, dev.nezo.burmaldaholic.core.bots.BotLedger.netToday(server, player.getUUID())),
			CasinoConfig.bots().enabled ? Math.max(0, dev.nezo.burmaldaholic.core.bots.BotLedger.threshold(server, player.getUUID())) : 0,
			rec.biggestWin, rec.biggestWinGame == null ? "" : rec.biggestWinGame);
	}

	/** Sends the state if it changed (or {@code force}); {@code open} also opens the menu. */
	static void send(ServerPlayer player, boolean open, boolean force) {
		if (VipSyncPayload.TYPE == null || !ServerPlayNetworking.canSend(player, VipSyncPayload.TYPE)) {
			return;
		}
		VipSyncPayload now = snapshot(player, open);
		if (!force && !open && now.sameContent(LAST.get(player.getUUID()))) {
			return;
		}
		ServerPlayNetworking.send(player, now);
		LAST.put(player.getUUID(), now.withoutOpen());
	}

	static void sendError(ServerPlayer player, Component message) {
		if (VipErrorPayload.TYPE != null && ServerPlayNetworking.canSend(player, VipErrorPayload.TYPE)) {
			ServerPlayNetworking.send(player, new VipErrorPayload(message));
		} else {
			player.sendSystemMessage(message.copy().withStyle(ChatFormatting.RED));
		}
	}

	/** Every server tick: flush dirty players. Every second: day rollover, promotion, contracts, cards. */
	static void tick(MinecraftServer server) {
		if (!CasinoMode.isEnabled(server)) {
			DIRTY.clear();
			return;
		}
		boolean second = server.getTickCount() % 20 == 0;
		if (second) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				rollDay(player);
				checkPromotion(player);
				Contracts.tick(player);
				if (server.getTickCount() % 100 == 0) {
					swapCards(player);
				}
			}
		}
		if (DIRTY.isEmpty()) {
			return;
		}
		for (UUID id : List.copyOf(DIRTY)) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player != null) {
				send(player, false, false);
			}
		}
		DIRTY.clear();
	}
}
