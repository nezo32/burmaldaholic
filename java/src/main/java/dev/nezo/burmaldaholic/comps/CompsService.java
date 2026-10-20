package dev.nezo.burmaldaholic.comps;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.comps.logic.CompsRules;
import dev.nezo.burmaldaholic.comps.net.CompsSyncPayload;
import dev.nezo.burmaldaholic.core.bots.BotRounds;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.ConfigHandle;
import dev.nezo.burmaldaholic.core.config.sections.VipConfig;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.events.CasinoEvents;
import dev.nezo.burmaldaholic.core.fx.ServerFx;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.util.Inventories;
import dev.nezo.burmaldaholic.core.wager.Stake;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Comps runtime (EC-SPEC §4): points from {@code PLAY_RESOLVED}, the activity gate, the daily bonus, redemptions and
 * the Comps tab sync. State lives in the persistent player attachment {@code burmaldaholic:comps}. Server thread only.
 */
public final class CompsService {
	public static final String ID = CompsModule.ID;

	static final Codec<CompsRules.State> STATE_CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.LONG.optionalFieldOf("points_milli", 0L).forGetter(CompsRules.State::pointsMilli),
		Codec.LONG.optionalFieldOf("lifetime_milli", 0L).forGetter(CompsRules.State::lifetimeMilli),
		Codec.LONG.optionalFieldOf("earned_today_milli", 0L).forGetter(CompsRules.State::earnedTodayMilli),
		Codec.LONG.optionalFieldOf("day", -1L).forGetter(CompsRules.State::day),
		Codec.LONG.optionalFieldOf("daily_claim_day", -1L).forGetter(CompsRules.State::dailyClaimDay),
		Codec.LONG.optionalFieldOf("active_ticks_today", 0L).forGetter(CompsRules.State::activeTicksToday),
		Codec.LONG.optionalFieldOf("suite_day", -1L).forGetter(CompsRules.State::suiteDay),
		Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("redeemed_today", Map.of()).forGetter(CompsRules.State::redeemedToday)
	).apply(i, CompsRules.State::new));

	static AttachmentType<CompsRules.State> TYPE;
	static @Nullable ConfigHandle<CompsConfig> config;

	private static final Map<UUID, Long> LAST_ACTIVITY = new HashMap<>();
	private static final Map<UUID, double[]> LAST_POS = new HashMap<>();

	private CompsService() {}

	static void registerAttachment() {
		TYPE = AttachmentRegistry.create(Burmaldaholic.id("comps"), b -> b.persistent(STATE_CODEC).copyOnDeath());
	}

	public static CompsConfig cfg() {
		ConfigHandle<CompsConfig> h = config;
		return h == null ? new CompsConfig() : h.get();
	}

	public static boolean enabled() {
		return cfg().enabled;
	}

	static long today(MinecraftServer server) {
		return server.overworld().getGameTime() / CompsRules.TICKS_PER_DAY;
	}

	/** The player's state, rolled to today. */
	public static CompsRules.State state(ServerPlayer player) {
		long today = today(player.level().getServer());
		CompsRules.State s = player.getAttached(TYPE);
		if (s == null) {
			s = CompsRules.State.empty(today);
		}
		return s.roll(today);
	}

	public static void put(ServerPlayer player, CompsRules.State s) {
		player.setAttached(TYPE, s);
	}

	// ---- rates ------------------------------------------------------------------------------

	static int tier(ServerPlayer player) {
		return CoreServices.vip().tier(player.level().getServer(), player.getUUID());
	}

	/** Cashback rate of a tier (vip.cashback, Gold+; same clamp as the VIP module). */
	static double cashbackRate(int tier) {
		VipConfig.Cashback c = CasinoConfig.vip().cashback;
		double r = switch (Math.min(tier, CompsRules.NETHERITE)) {
			case CompsRules.GOLD -> c.gold;
			case CompsRules.PLATINUM -> c.platinum;
			case CompsRules.DIAMOND -> c.diamond;
			case CompsRules.NETHERITE -> c.netherite;
			default -> 0;
		};
		return dev.nezo.burmaldaholic.core.economy.RebateCaps.cashback(r);
	}

	/** Comp rate of the player's tier, after the comp + cashback ≤ 0.35 clamp. */
	public static double rate(int tier) {
		return CompsRules.rate(tier, CasinoConfig.vip().compRate.toArray(), cashbackRate(tier));
	}

	// ---- earning ----------------------------------------------------------------------------

	/** EC-SPEC §4.1: exactly the cashback rule ({@code houseBanked, !owned, chips}) and not vs bots. */
	static boolean eligible(CasinoEvents.PlayResult r) {
		return r.houseBanked() && !r.ownedCasino() && r.stakeKind() == Stake.Kind.CHIPS && !BotRounds.vsBots(r) && !"pvp".equals(r.gameId());
	}

	static void onPlayResolved(ServerPlayer player, CasinoEvents.PlayResult result) {
		if (!enabled() || !CasinoMode.isEnabled(player)) {
			return;
		}
		if (!eligible(result)) {
			return;
		}
		long milli = CompsRules.pointsMilli(result.bet(), result.houseEdge(), rate(tier(player)));
		if (milli <= 0) {
			return;
		}
		put(player, state(player).earn(milli));
		markSync(player);
	}

	// ---- activity ---------------------------------------------------------------------------

	static void interacted(ServerPlayer player) {
		LAST_ACTIVITY.put(player.getUUID(), player.level().getServer().overworld().getGameTime());
	}

	/** Every second: samples movement, adds 20 active ticks when the player was active in the last 60 s. */
	static void sampleActivity(ServerPlayer player, long now) {
		double[] last = LAST_POS.get(player.getUUID());
		double x = player.getX(), y = player.getY(), z = player.getZ();
		if (last == null || CompsRules.moved(x - last[0], y - last[1], z - last[2])) {
			LAST_POS.put(player.getUUID(), new double[] {x, y, z});
			if (last != null) {
				LAST_ACTIVITY.put(player.getUUID(), now);
			}
		}
		if (CompsRules.active(now, LAST_ACTIVITY.getOrDefault(player.getUUID(), -1L))) {
			put(player, state(player).active(20));
		}
	}

	static void forget(UUID id) {
		LAST_ACTIVITY.remove(id);
		LAST_POS.remove(id);
		DIRTY.remove(id);
	}

	// ---- daily bonus ------------------------------------------------------------------------

	static long minActiveTicks() {
		return Math.max(0, cfg().daily.minActiveMinutes) * 1200L;
	}

	static long lifetimeWagered(MinecraftServer server, UUID id) {
		Object fn = FabricLoader.getInstance().getObjectShare().get("burmaldaholic:vip/wagered");
		if (fn instanceof BiFunction<?, ?, ?> f) {
			@SuppressWarnings("unchecked")
			Long w = ((BiFunction<MinecraftServer, UUID, Long>) f).apply(server, id);
			return w == null ? 0 : w;
		}
		return 0;
	}

	static boolean ipChecked(MinecraftServer server) {
		return cfg().daily.onePerIp && server.isDedicatedServer();
	}

	public static CompsRules.Daily dailyState(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		long today = today(server);
		boolean ipUsed = ipChecked(server) && CompsWorldData.get(server).ipUsed(today, player.getIpAddress());
		return CompsRules.daily(state(player), today, lifetimeWagered(server, player.getUUID()), cfg().daily.minLifetimeWager, minActiveTicks(), ipUsed);
	}

	/** Claims the daily bonus. @return null on success, else the translated reason. */
	public static @Nullable Component claimDaily(ServerPlayer player) {
		if (!enabled() || !CasinoMode.isEnabled(player)) {
			return Component.translatable("gui.burmaldaholic.error.disabled");
		}
		CompsRules.Daily d = dailyState(player);
		if (d != CompsRules.Daily.CLAIMABLE) {
			return dailyMessage(player, d);
		}
		MinecraftServer server = player.level().getServer();
		long today = today(server);
		long amount = CompsRules.dailyAmount(tier(player), cfg().daily.amounts());
		put(player, state(player).claimed(today)); // first: a re-entrant second claim sees CLAIMED
		if (ipChecked(server)) {
			CompsWorldData.get(server).markIp(today, player.getIpAddress());
		}
		long got = amount > 0 ? Economies.get().deposit(player, amount, new Transaction(ID, "daily_bonus", Transaction.Kind.EARNING)) : 0;
		ServerFx.get().toast(player, "toast.burmaldaholic.daily.title", Component.translatable("toast.burmaldaholic.daily.body", Texts.chips(got)),
			ServerFx.ToastIcon.CHIP);
		markSync(player);
		return null;
	}

	/** The daily card line for a state, with its argument (amount, countdown, wager or minutes left). */
	public static Component dailyMessage(ServerPlayer player, CompsRules.Daily d) {
		MinecraftServer server = player.level().getServer();
		return switch (d) {
			case CLAIMABLE -> Component.translatable(dailyKey(d), Texts.chips(CompsRules.dailyAmount(tier(player), cfg().daily.amounts())));
			case CLAIMED -> Component.translatable(dailyKey(d), Texts.plural("unit.burmaldaholic.minute",
				Math.max(1, (CompsRules.ticksToNextDay(server.overworld().getGameTime()) + 1199) / 1200)));
			case LOCKED_WAGER -> Component.translatable(dailyKey(d), Texts.chips(cfg().daily.minLifetimeWager));
			case LOCKED_ACTIVE -> Component.translatable(dailyKey(d), Texts.plural("unit.burmaldaholic.minute",
				Math.max(1, (minActiveTicks() - state(player).activeTicksToday() + 1199) / 1200)));
			case LOCKED_IP -> Component.translatable(dailyKey(d));
		};
	}

	static String dailyKey(CompsRules.Daily d) {
		return switch (d) {
			case CLAIMABLE -> "gui.burmaldaholic.comps.daily.claim";
			case CLAIMED -> "gui.burmaldaholic.comps.daily.wait";
			case LOCKED_WAGER -> "gui.burmaldaholic.comps.daily.locked_wager";
			case LOCKED_ACTIVE -> "gui.burmaldaholic.comps.daily.locked_active";
			case LOCKED_IP -> "gui.burmaldaholic.comps.daily.locked_ip";
		};
	}

	// ---- redemptions ------------------------------------------------------------------------

	static CompsRules.Dim dim(ServerPlayer player) {
		var d = player.level().dimension();
		return d == Level.OVERWORLD ? CompsRules.Dim.OVERWORLD : d == Level.NETHER ? CompsRules.Dim.NETHER : CompsRules.Dim.ANY;
	}

	/** Redeems {@code qty} (1..64) of reward {@code id}. @return null on success, else the reason. */
	public static @Nullable Component redeem(ServerPlayer player, String id, int qty) {
		if (!enabled() || !CasinoMode.isEnabled(player)) {
			return Component.translatable("gui.burmaldaholic.error.disabled");
		}
		CompsRules.Reward r = CompsRules.REWARDS.get(id);
		if (r == null) {
			return Component.translatable("gui.burmaldaholic.error.invalid_amount");
		}
		int n = Math.max(1, Math.min(64, qty));
		int done = 0;
		Component err = null;
		for (int i = 0; i < n; i++) {
			CompsRules.State s = state(player);
			int tier = tier(player);
			String lock = CompsRules.lock(r, tier, s, dim(player));
			if (lock != null) {
				err = Component.translatable("gui.burmaldaholic.comps.lock." + lock);
				break;
			}
			long cost = CompsRules.cost(r, tier, s);
			if (!grant(player, r)) {
				err = Component.translatable("gui.burmaldaholic.comps.lock.unavailable");
				break;
			}
			CompsRules.State after = state(player).spend(r.id(), cost);
			if (r.kind() == CompsRules.Kind.SUITE) {
				after = after.suite(today(player.level().getServer()));
			}
			put(player, after);
			done++;
		}
		markSync(player);
		if (done > 0) {
			player.sendSystemMessage(Component.translatable("msg.burmaldaholic.comps.redeemed", Component.translatable("gui.burmaldaholic.comps.reward." + r.id()),
				Texts.number(done)).withStyle(ChatFormatting.GREEN));
			return null;
		}
		return err;
	}

	/** Gives the reward itself; false = could not (nothing is charged). */
	private static boolean grant(ServerPlayer player, CompsRules.Reward r) {
		long now = player.level().getServer().overworld().getGameTime();
		switch (r.kind()) {
			case ITEMS -> {
				for (ItemStack s : plate(r.id())) {
					Inventories.giveOrDrop(player, s);
				}
				return true;
			}
			case SUITE -> {
				return true;
			}
			case TOKEN -> {
				Inventories.giveOrDrop(player, FreeBets.create(player.getUUID(), r.tokenValue(), now + CompsRules.TOKEN_LIFETIME_TICKS));
				return true;
			}
			case RIDE_ITEMS -> {
				ItemStack rockets = new ItemStack(Items.FIREWORK_ROCKET, 16);
				rockets.set(DataComponents.FIREWORKS, new Fireworks(2, List.of()));
				Inventories.giveOrDrop(player, rockets);
				return true;
			}
			case HORSE, STRIDER -> {
				return Rentals.spawn(player, r.kind() == CompsRules.Kind.STRIDER, cfg().rentalMinutes * 1200L);
			}
			default -> {
				return false;
			}
		}
	}

	static List<ItemStack> plate(String id) {
		return switch (id) {
			case "snack" -> List.of(new ItemStack(Items.COOKED_BEEF, 8), new ItemStack(Items.BREAD, 4));
			case "chef" -> List.of(new ItemStack(Items.GOLDEN_CARROT, 8), new ItemStack(Items.CAKE), new ItemStack(Items.PUMPKIN_PIE, 4));
			case "feast" -> List.of(new ItemStack(Items.GOLDEN_APPLE, 2), new ItemStack(Items.GOLDEN_CARROT, 16), new ItemStack(Items.CAKE));
			default -> List.of();
		};
	}

	// ---- sync -------------------------------------------------------------------------------

	private static final java.util.Set<UUID> DIRTY = new java.util.HashSet<>();

	static void markSync(ServerPlayer player) {
		DIRTY.add(player.getUUID());
	}

	/** Players who asked for comps data (opened the tab); only they get pushes. */
	private static final java.util.Set<UUID> WATCHING = new java.util.HashSet<>();

	static void watch(ServerPlayer player) {
		WATCHING.add(player.getUUID());
	}

	static long[] cashbackEstimate(MinecraftServer server, UUID id) {
		Object fn = FabricLoader.getInstance().getObjectShare().get("burmaldaholic:vip/cashback_estimate");
		if (fn instanceof BiFunction<?, ?, ?> f) {
			@SuppressWarnings("unchecked")
			long[] v = ((BiFunction<MinecraftServer, UUID, long[]>) f).apply(server, id);
			if (v != null && v.length >= 2) {
				return v;
			}
		}
		return new long[] {0, 0};
	}

	public static CompsSyncPayload snapshot(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		CompsRules.State s = state(player);
		int tier = tier(player);
		long[] cb = cashbackEstimate(server, player.getUUID());
		List<CompsSyncPayload.RewardView> rows = new ArrayList<>();
		CompsRules.Dim here = dim(player);
		for (CompsRules.Reward r : CompsRules.REWARDS.values()) {
			String lock = enabled() ? CompsRules.lock(r, tier, s, here) : "disabled";
			rows.add(new CompsSyncPayload.RewardView(r.id(), CompsRules.cost(r, tier, s), r.minTier(), r.limit(), s.redeemed(r.id()), lock == null ? "" : lock));
		}
		return new CompsSyncPayload(s.pointsMilli(), (int) Math.round(rate(tier) * 1000), cb[0], cb[1], dailyState(player).name(),
			CompsRules.dailyAmount(tier, cfg().daily.amounts()), CompsRules.ticksToNextDay(server.overworld().getGameTime()), s.activeTicksToday(),
			minActiveTicks(), cfg().daily.minLifetimeWager, List.copyOf(rows));
	}

	public static void send(ServerPlayer player) {
		if (CompsSyncPayload.TYPE != null && ServerPlayNetworking.canSend(player, CompsSyncPayload.TYPE)) {
			ServerPlayNetworking.send(player, snapshot(player));
		}
	}

	static void tick(MinecraftServer server) {
		if (!CasinoMode.isEnabled(server) || !enabled()) {
			DIRTY.clear();
			return;
		}
		if (server.getTickCount() % 20 == 0) {
			long now = server.overworld().getGameTime();
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				sampleActivity(p, now);
				if (server.getTickCount() % 100 == 0) {
					FreeBets.sweepExpired(p);
					Rentals.sweepSticks(p);
				}
			}
			Rentals.tick(server);
		}
		if (server.getTickCount() % 20 == 10) {
			for (UUID id : List.copyOf(DIRTY)) {
				ServerPlayer p = server.getPlayerList().getPlayer(id);
				if (p != null && WATCHING.contains(id)) {
					send(p);
				}
			}
			DIRTY.clear();
		}
	}

	static void unwatchAll(UUID id) {
		WATCHING.remove(id);
	}
}
