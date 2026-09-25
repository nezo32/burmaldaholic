package dev.nezo.burmaldaholic.vip.logic;

import dev.nezo.burmaldaholic.core.wager.HouseEdges;

/**
 * Daily VIP cashback (GAME_DESIGN.md §12, CHANGED 2026-09). Pure.
 *
 * <p>{@code cashback = floor(rate × theoreticalLossToday)} with
 * {@code theoreticalLossToday = Σ stake × houseEdge(game)} over the day's eligible (house-banked
 * chip) rounds, paid at the MCD boundary whatever the actual result was. Because {@code rate ≤ 0.5 < 1}
 * the expected cashback is always below the expected loss: the effective edge is {@code HE × (1 − rate) > 0}.
 *
 * <p>Each round carries its own edge ({@code PlayResult#houseEdge}: slots per tier, plinko per risk,
 * scratch per card, craps per bet with Odds = 0 %, otherwise the game's lowest edge from core's
 * {@code HouseEdges}). Only world-bank chip rounds count: never PvP, never owned casinos, never pawn stakes.
 */
public final class CashbackRules {
	private CashbackRules() {}

	/** The game's lowest §17 edge (core {@code HouseEdges}); 0 for PvP / unknown ids. */
	public static double houseEdge(String gameId) {
		return HouseEdges.of(gameId);
	}

	/** §12: bank-banked chip rounds only (never PvP poker/dice, never owned casinos, never pawn stakes). */
	public static boolean eligible(boolean houseBanked, boolean ownedCasino, boolean pawn) {
		return houseBanked && !ownedCasino && !pawn;
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

	/**
	 * Adds a settled round (rolls first; a closed day is returned for payment).
	 *
	 * @param edge     house edge of the round's bet(s)
	 * @param eligible counts for cashback ({@link #eligible(boolean, boolean, boolean)})
	 */
	public static Roll record(DayLedger ledger, long today, long bet, long payout, double edge, boolean eligible) {
		Roll r = roll(ledger, today);
		DayLedger l = r.ledger();
		long stake = Math.max(0, bet);
		double theo = eligible ? theoreticalLoss(stake, edge) : 0;
		DayLedger next = new DayLedger(l.day(), l.staked() + stake, l.returned() + Math.max(0, payout), l.theo() + theo);
		return new Roll(next, r.closed());
	}
}
