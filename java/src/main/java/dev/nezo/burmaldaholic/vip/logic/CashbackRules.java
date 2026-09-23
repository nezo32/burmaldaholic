package dev.nezo.burmaldaholic.vip.logic;

import java.util.Map;

/**
 * Daily VIP cashback (GAME_DESIGN.md §12, CHANGED 2026-09). Pure.
 *
 * <p>{@code cashback = floor(rate × theoreticalLossToday)} with
 * {@code theoreticalLossToday = Σ stake × houseEdge(game)} over the day's eligible (house-banked
 * chip) rounds, paid at the MCD boundary whatever the actual result was. Because {@code rate ≤ 0.5 < 1}
 * the expected cashback is always below the expected loss: the effective edge is {@code HE × (1 − rate) > 0}.
 *
 * <p>Java's {@code PlayResult} carries only the game id (no bet type / variant), so the edge is the
 * LOWEST edge that game can have (conservative: cashback can never exceed the real edge of any bet).
 */
public final class CashbackRules {
	/**
	 * Lowest §17 edge per game id. Craps is 0 because its Odds bet has 0 % edge and a craps result
	 * cannot tell Odds from flat bets. Poker is PvP (never eligible). Unknown ids: 0.
	 */
	public static final Map<String, Double> LOWEST_EDGE = Map.ofEntries(
		Map.entry("blackjack", 0.0041),
		Map.entry("roulette", 0.027),
		Map.entry("slots", 0.0396),
		Map.entry("slot_machine", 0.0396),
		Map.entry("craps", 0.0),
		Map.entry("poker", 0.0),
		// extras: coin flip 2 %, wheel 4.63 %, scratch 15 %, plinko ≈ 3.3 %, dice duel 2.78 % -> lowest 2 %
		Map.entry("extras", 0.02),
		Map.entry("coin_flip", 0.02),
		Map.entry("lucky_coin", 0.02),
		Map.entry("wheel_of_fortune", 1 - 51.5 / 54),
		Map.entry("scratch_card", 0.15),
		Map.entry("plinko", 0.033),
		Map.entry("dice_duel", 0.0278));

	private CashbackRules() {}

	public static double houseEdge(String gameId) {
		Double e = gameId == null ? null : LOWEST_EDGE.get(gameId);
		return e == null || !(e > 0) ? 0 : e;
	}

	/** PvP poker never earns cashback (§12). */
	public static boolean eligible(String gameId) {
		return gameId != null && !gameId.equals("poker");
	}

	/** Theoretical loss of one round ({@code stake × edge}, never negative). */
	public static double theoreticalLoss(long stake, double edge) {
		return stake > 0 && edge > 0 ? stake * edge : 0;
	}

	/** {@code floor(rate × theoreticalLoss)}; rate is clamped to [0, 1]. */
	public static long amount(double theoreticalLoss, double rate) {
		if (!(theoreticalLoss > 0) || !(rate > 0)) {
			return 0;
		}
		return (long) Math.floor(theoreticalLoss * Math.min(rate, 1) + 1e-9);
	}

	/**
	 * One player's totals for world day {@code day}.
	 * @param staked   all settled wagers today (Wallet "today's result")
	 * @param returned all returns today
	 * @param theo     Σ stake × edge of the cashback-eligible rounds
	 */
	public record DayLedger(long day, long staked, long returned, double theo) {
		public static DayLedger empty(long day) {
			return new DayLedger(day, 0, 0, 0);
		}

		public long net() {
			return returned - staked;
		}
	}

	/** @param closed the finished day to pay cashback for (null if the ledger was already today's) */
	public record Roll(DayLedger ledger, DayLedger closed) {}

	/** Rolls the ledger to {@code today}. */
	public static Roll roll(DayLedger ledger, long today) {
		if (ledger == null) {
			return new Roll(DayLedger.empty(today), null);
		}
		if (ledger.day() >= today) {
			return new Roll(ledger, null);
		}
		return new Roll(DayLedger.empty(today), ledger);
	}

	/** Adds a settled round (rolls first; a closed day is returned for payment). */
	public static Roll record(DayLedger ledger, long today, String gameId, long bet, long payout) {
		Roll r = roll(ledger, today);
		DayLedger l = r.ledger();
		long stake = Math.max(0, bet);
		double theo = eligible(gameId) ? theoreticalLoss(stake, houseEdge(gameId)) : 0;
		DayLedger next = new DayLedger(l.day(), l.staked() + stake, l.returned() + Math.max(0, payout), l.theo() + theo);
		return new Roll(next, r.closed());
	}
}
