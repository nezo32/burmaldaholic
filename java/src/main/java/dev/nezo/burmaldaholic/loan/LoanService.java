package dev.nezo.burmaldaholic.loan;

import dev.nezo.burmaldaholic.core.advancement.CasinoAdvancements;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.sections.LoanConfig;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.service.DebtProvider;
import dev.nezo.burmaldaholic.core.service.VipTiers;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.loan.logic.LoanRecord;
import dev.nezo.burmaldaholic.loan.logic.LoanRecord.Status;
import dev.nezo.burmaldaholic.loan.logic.LoanRules;
import dev.nezo.burmaldaholic.loan.logic.LoanRules.Band;
import dev.nezo.burmaldaholic.loan.logic.LoanRules.Product;
import dev.nezo.burmaldaholic.loan.logic.SquadRules;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import org.jspecify.annotations.Nullable;

/**
 * Loan bookkeeping (GAME_DESIGN.md §5.2–§5.6, §5.8): taking and repaying, deadline warnings, default,
 * late fees, garnishment (economy credit hook), Asset Freeze, dormancy while casino mode / loans are off,
 * and core's {@link DebtProvider}. Server thread only; state lives in {@link LoanData}.
 */
public final class LoanService {
	static final Transaction TAKE = new Transaction(LoanModule.ID, "take", Transaction.Kind.TRANSFER);
	static final Transaction PAY = new Transaction(LoanModule.ID, "pay", Transaction.Kind.TRANSFER);
	static final Transaction FREEZE = new Transaction(LoanModule.ID, "asset_freeze", Transaction.Kind.TRANSFER);
	static final Transaction REPOSSESS = new Transaction(LoanModule.ID, "repossess", Transaction.Kind.TRANSFER);
	/** Ticks without a loan tick that count as dormancy (normal cadence is 20). */
	private static final long DORMANT_GAP = 200;

	private LoanService() {}

	// ---- state ----------------------------------------------------------------------------------

	/** Loans are live: casino mode on and {@code loan.enabled}. Otherwise everything is dormant. */
	public static boolean active(MinecraftServer server) {
		return CasinoMode.isEnabled(server) && CasinoConfig.loan().enabled;
	}

	/** World game time (monotonic, world-bound). */
	public static long now(MinecraftServer server) {
		return server.overworld().getGameTime();
	}

	public static Band band(MinecraftServer server) {
		return LoanRules.band(server.getWorldData().getDifficulty().getId(), server.isHardcore());
	}

	public static boolean peaceful(MinecraftServer server) {
		return !server.isHardcore() && server.getWorldData().getDifficulty() == Difficulty.PEACEFUL;
	}

	/** Collectors hunt defaulters; otherwise (Peaceful or collectors off) Asset Freeze applies. */
	public static boolean collectorsMode(MinecraftServer server) {
		return CasinoConfig.loan().collectors.enabled && !peaceful(server);
	}

	public static LoanRecord record(MinecraftServer server, UUID player) {
		return LoanData.get(server).record(player);
	}

	public static long owed(MinecraftServer server, UUID player) {
		if (!active(server)) {
			return 0;
		}
		LoanRecord r = LoanData.get(server).peek(player);
		return r == null ? 0 : r.owed;
	}

	public static boolean inDefault(MinecraftServer server, UUID player) {
		if (!active(server)) {
			return false;
		}
		LoanRecord r = LoanData.get(server).peek(player);
		return r != null && r.status == Status.DEFAULT;
	}

	/** Asset Freeze (§5.6): in default without collectors → no wagering, 100 % garnishment. */
	public static boolean frozen(MinecraftServer server, UUID player) {
		return inDefault(server, player) && !collectorsMode(server);
	}

	public static int tier(MinecraftServer server, UUID player) {
		return VipTiers.clamp(CoreServices.vip().tier(server, player));
	}

	public static List<Product> products() {
		return LoanRules.products(CasinoConfig.loan().products);
	}

	public static LoanRules.RateConfig rateConfig(MinecraftServer server) {
		LoanConfig c = CasinoConfig.loan();
		double base = switch (band(server)) {
			case EASY -> c.rate.easy;
			case NORMAL -> c.rate.normal;
			case HARD -> c.rate.hard;
		};
		return new LoanRules.RateConfig(base, c.goodStandingDiscount, c.goodStandingMaxSteps, c.minRate, c.platinumDiscount);
	}

	public static double rateFor(MinecraftServer server, UUID player) {
		return LoanRules.rate(rateConfig(server), record(server, player).goodStanding, tier(server, player));
	}

	private static LoanRules.AdvanceConfig advanceConfig(MinecraftServer server) {
		LoanConfig c = CasinoConfig.loan();
		double fee = switch (band(server)) {
			case EASY -> c.lateFee.easy;
			case NORMAL -> c.lateFee.normal;
			case HARD -> c.lateFee.hard;
		};
		return new LoanRules.AdvanceConfig(fee, c.lateFeeCapMultiplier, c.warningTicks);
	}

	// ---- taking / paying --------------------------------------------------------------------------

	/** Takes product {@code index} (all anti-abuse checks). Returns the error, or null on success. */
	public static @Nullable Component take(ServerPlayer player, int index) {
		MinecraftServer server = player.level().getServer();
		if (!CasinoMode.isEnabled(server)) {
			return Component.translatable("gui.burmaldaholic.error.casino_off");
		}
		if (!CasinoConfig.loan().enabled) {
			return Component.translatable("gui.burmaldaholic.error.disabled");
		}
		List<Product> products = products();
		if (index < 0 || index >= products.size()) {
			return Component.translatable("gui.burmaldaholic.error.invalid_amount");
		}
		tick(player); // bring the record up to date first
		Product product = products.get(index);
		long now = now(server);
		LoanData data = LoanData.get(server);
		LoanRecord rec = data.record(player.getUUID());
		LoanRules.TakeError err = LoanRules.takeError(rec, product, tier(server, player.getUUID()), now);
		if (err != null) {
			return switch (err) {
				case IN_DEFAULT -> Component.translatable("gui.burmaldaholic.error.in_default");
				case ONE_AT_A_TIME -> Component.translatable("msg.burmaldaholic.loan.one_at_a_time");
				case COOLDOWN -> Component.translatable("msg.burmaldaholic.loan.cooldown", days(rec.cooldownUntil - now));
				case VIP -> Component.translatable("gui.burmaldaholic.error.vip_required", VipTiers.name(product.minTier()));
			};
		}
		// Review m5: the principal must land in full — never record a debt for chips lost to the balance cap.
		long room = CasinoConfig.economy().maxBalance - Economies.get().balance(player);
		if (product.principal() > room) {
			return Component.translatable("msg.burmaldaholic.loan.balance_cap", Texts.chips(Math.max(0, room)));
		}
		LoanRules.take(rec, product, rateFor(server, player.getUUID()), now);
		data.setDirty();
		Economies.get().deposit(player, product.principal(), TAKE);
		CasinoAdvancements.grant(player, "loan_taken");
		// Day number on the world's game-time calendar (day 1 = first day of the world).
		long deadlineDay = rec.deadlineTick / LoanRules.MCD + 1;
		player.sendSystemMessage(Component.translatable("msg.burmaldaholic.loan.taken", Texts.chips(product.principal()),
			Texts.chipsAcc(rec.due), Texts.number(deadlineDay)).withStyle(ChatFormatting.GOLD));
		LoanModule.LOG.info("{} took loan {} (due {})", player.getName().getString(), product.principal(), rec.due);
		return null;
	}

	/** Pays up to {@code amount} from the balance. Returns chips paid (0 = nothing to pay / no funds). */
	public static long pay(ServerPlayer player, long amount, boolean announce) {
		MinecraftServer server = player.level().getServer();
		if (!active(server) || amount <= 0) {
			return 0;
		}
		LoanRecord rec = record(server, player.getUUID());
		long want = Math.min(Math.min(amount, rec.owed), Economies.get().balance(player));
		if (want <= 0 || !Economies.get().tryWithdraw(player, want, PAY)) {
			return 0;
		}
		return applyToDebt(server, player.getUUID(), want, announce);
	}

	/**
	 * Reduces the debt by chips already taken from the player (payment, garnishment, seizure,
	 * repossessed item). Closing messages are always sent; {@code announce} adds the partial line.
	 */
	public static long applyToDebt(MinecraftServer server, UUID id, long amount, boolean announce) {
		LoanData data = LoanData.get(server);
		LoanRecord rec = data.record(id);
		LoanRules.PayResult r = LoanRules.pay(rec, amount, now(server), CasinoConfig.loan().defaultCooldownDays);
		if (r.paid() <= 0) {
			return 0;
		}
		data.setDirty();
		ServerPlayer online = server.getPlayerList().getPlayer(id);
		if (r.closed() && r.onTime()) {
			CasinoAdvancements.grant(server, id, "clean_slate");
		}
		if (online != null) {
			if (r.closed()) {
				online.sendSystemMessage(Component.translatable(r.onTime() ? "msg.burmaldaholic.loan.repaid_on_time" : "msg.burmaldaholic.loan.repaid")
					.withStyle(ChatFormatting.GREEN));
			} else if (announce) {
				online.sendSystemMessage(Component.translatable("msg.burmaldaholic.loan.paid_partial", Texts.chips(r.paid()), Texts.chips(rec.owed)));
			}
		}
		if (rec.owed <= 0) {
			LoanSquads.onDebtCleared(server, id);
		}
		return r.paid();
	}

	// ---- time -----------------------------------------------------------------------------------

	/** Every second per online player: warnings, default, late fees, Asset Freeze, collector waves. */
	public static void tick(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		LoanData data = LoanData.get(server);
		LoanRecord rec = data.peek(player.getUUID());
		if (rec == null || rec.status == Status.NONE) {
			return;
		}
		long now = now(server);
		long owedBefore = rec.owed;
		LoanRules.AdvanceEvents ev = LoanRules.advance(rec, now, advanceConfig(server));
		boolean changed = ev.defaulted() || ev.warning() >= 0 || !ev.lateFees().isEmpty();
		if (ev.warning() >= 0) {
			String key = ev.warning() >= LoanRules.MCD ? "msg.burmaldaholic.loan.warning_day" : "msg.burmaldaholic.loan.warning_final";
			player.sendSystemMessage(Component.translatable(key, Texts.chips(rec.owed)).withStyle(ChatFormatting.YELLOW));
			sound(player, net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F);
		}
		boolean collectors = collectorsMode(server);
		if (ev.defaulted()) {
			CasinoAdvancements.grant(player, "knock_knock");
			title(player, Component.translatable("msg.burmaldaholic.loan.defaulted_title").withStyle(ChatFormatting.RED), null);
			player.sendSystemMessage(Component.translatable("msg.burmaldaholic.loan.defaulted", Texts.chips(rec.owed)).withStyle(ChatFormatting.RED));
			sound(player, net.minecraft.sounds.SoundEvents.EVOKER_PREPARE_ATTACK, 0.8F);
			if (collectors) {
				SquadRules.scheduleFirstWave(rec, now, CasinoConfig.loan().firstWaveDelayTicks);
			}
		}
		long running = owedBefore;
		for (long fee : ev.lateFees()) {
			running += fee;
			player.sendSystemMessage(Component.translatable("msg.burmaldaholic.loan.late_fee", Texts.chips(fee), Texts.chips(Math.min(running, rec.owed)))
				.withStyle(ChatFormatting.RED));
		}
		int seizures = 0;
		if (rec.status == Status.DEFAULT) {
			if (collectors) {
				// Switched from Asset Freeze to collectors mid-default: next wave at the next boundary.
				if (rec.nextWaveTick <= 0) {
					rec.nextWaveTick = LoanRules.nextBoundary(rec.deadlineTick, now);
					changed = true;
				}
				if (rec.freezeMark >= 0) {
					rec.freezeMark = -1;
					changed = true;
				}
			} else {
				int before = rec.freezeMark;
				seizures = LoanRules.freezeSteps(rec, now, ev.defaulted());
				changed |= before != rec.freezeMark;
				if (rec.nextWaveTick > 0) {
					rec.nextWaveTick = 0;
					rec.queued = false;
					changed = true;
				}
			}
		}
		if (changed) {
			data.setDirty();
		}
		for (int i = 0; i < seizures; i++) {
			freezeSeize(player);
		}
		if (collectors && rec.status == Status.DEFAULT) {
			LoanSquads.maybeSpawn(player, rec, now);
		}
	}

	private static void freezeSeize(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		LoanRecord rec = record(server, player.getUUID());
		long seize = LoanRules.seizeAmount(Economies.get().balance(player), CasinoConfig.loan().peacefulSeizePercent, rec.owed);
		if (seize > 0 && Economies.get().tryWithdraw(player, seize, FREEZE)) {
			applyToDebt(server, player.getUUID(), seize, false);
		} else {
			seize = 0;
		}
		player.sendSystemMessage(Component.translatable("msg.burmaldaholic.loan.asset_freeze", Texts.chips(seize)).withStyle(ChatFormatting.RED));
	}

	/**
	 * While dormant (casino mode or loans off) nothing may happen, so when loans come back all pending
	 * timers move forward by the dormant time. Called every second while active.
	 */
	public static void dormancy(MinecraftServer server) {
		LoanData data = LoanData.get(server);
		long now = now(server);
		long last = data.lastActiveTick;
		data.lastActiveTick = now;
		data.setDirty();
		if (last < 0 || now - last <= DORMANT_GAP) {
			return; // first activation, normal cadence or a lag spike
		}
		long gap = now - last;
		for (LoanRecord r : data.records().values()) {
			LoanRules.shiftTimers(r, gap);
		}
		for (int i = 0; i < data.respawns().size(); i++) {
			LoanData.Respawn r = data.respawns().get(i);
			data.respawns().set(i, new LoanData.Respawn(r.dimension(), r.pos(), r.piglin(), r.at() + gap));
		}
	}

	// ---- garnishment ----------------------------------------------------------------------------

	/** Economy credit hook: in default, a share of every PAYOUT/EARNING credit goes to the debt first. */
	static long beforeCredit(MinecraftServer server, UUID id, long amount, Transaction reason) {
		if (amount <= 0 || LoanModule.ID.equals(reason.moduleId()) || !active(server)) {
			return amount;
		}
		LoanRecord rec = LoanData.get(server).peek(id);
		if (rec == null || rec.status != Status.DEFAULT) {
			return amount;
		}
		int pct = collectorsMode(server) ? CasinoConfig.loan().garnishPercent : 100;
		long take = LoanRules.garnishAmount(amount, pct, rec.owed);
		if (take <= 0) {
			return amount;
		}
		long applied = applyToDebt(server, id, take, false);
		ServerPlayer online = server.getPlayerList().getPlayer(id);
		if (online != null && applied > 0) {
			online.sendOverlayMessage(Component.translatable("msg.burmaldaholic.loan.garnished", Texts.chips(applied)).withStyle(ChatFormatting.RED));
		}
		return amount - applied;
	}

	/** Core's view of the debt (Cashier withdrawable/freeze, HUD line). */
	static final DebtProvider PROVIDER = new DebtProvider() {
		@Override
		public long owed(MinecraftServer server, UUID player) {
			return LoanService.owed(server, player);
		}

		@Override
		public boolean inDefault(MinecraftServer server, UUID player) {
			return LoanService.inDefault(server, player);
		}

		@Override
		public long ticksToDeadline(MinecraftServer server, UUID player) {
			if (!active(server)) {
				return 0;
			}
			LoanRecord r = LoanData.get(server).peek(player);
			return r == null || r.status != Status.ACTIVE ? 0 : Math.max(0, r.deadlineTick - now(server));
		}

		@Override
		public long principal(MinecraftServer server, UUID player) {
			if (!active(server)) {
				return 0;
			}
			LoanRecord r = LoanData.get(server).peek(player);
			return r == null || r.status == Status.NONE ? 0 : Math.max(0, r.principal);
		}
	};

	// ---- admin ----------------------------------------------------------------------------------

	/** Operator edits (§5.8.5): {@code set n} (0 = clear) or force {@code default}. Returns the new owed amount. */
	public static long adminSet(MinecraftServer server, UUID id, long owed) {
		LoanData data = LoanData.get(server);
		LoanRecord rec = data.record(id);
		LoanRules.adminSetDebt(rec, owed, now(server), 3);
		data.setDirty();
		if (rec.owed <= 0) {
			LoanSquads.onDebtCleared(server, id);
		}
		ServerPlayer online = server.getPlayerList().getPlayer(id);
		if (online != null) {
			tick(online);
		}
		return rec.owed;
	}

	public static void adminDefault(MinecraftServer server, UUID id) {
		LoanData data = LoanData.get(server);
		LoanRules.adminForceDefault(data.record(id), now(server));
		data.setDirty();
		ServerPlayer online = server.getPlayerList().getPlayer(id);
		if (online != null) {
			tick(online);
		}
	}

	// ---- helpers --------------------------------------------------------------------------------

	/** "3 days" (at least 1 while any time is left). */
	public static MutableComponent days(long ticks) {
		return Texts.plural("unit.burmaldaholic.day", LoanRules.dayCount(ticks));
	}

	/** "1d 04:12" for a remaining tick count (UI.md §1). */
	public static MutableComponent dhm(long ticks) {
		long t = Math.max(0, ticks);
		return Component.translatable("hud.burmaldaholic.time.dhm", Texts.number(t / LoanRules.MCD),
			Texts.raw(dev.nezo.burmaldaholic.core.text.Numbers.hoursMinutes(t % LoanRules.MCD)));
	}

	static void sound(ServerPlayer player, SoundEvent sound, float pitch) {
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 1.0F, pitch);
	}

	static void title(ServerPlayer player, Component title, @Nullable Component subtitle) {
		player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 60, 10));
		if (subtitle != null) {
			player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
		}
		player.connection.send(new ClientboundSetTitleTextPacket(title));
	}
}
