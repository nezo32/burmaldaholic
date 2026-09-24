package dev.nezo.burmaldaholic.core.wager;

import java.util.Map;

/**
 * House edge per game (GAME_DESIGN.md §17), used for the <i>theoretical loss</i> of a round
 * ({@code stake × edge}, VIP cashback §12 CHANGED 2026-09). Pure.
 *
 * <p>Where a game has several bets/variants this table holds the LOWEST edge of the game
 * (conservative: cashback never exceeds the real edge). Games that know better pass the bet's own
 * edge in {@code PlayResult#houseEdge} (slots per tier, plinko per risk, scratch per card, craps per
 * bet with Odds = 0 %). Same numbers as Bedrock's {@code core/logic/house-edge.ts}.
 */
public final class HouseEdges {
	public static final double BLACKJACK = 0.0041;
	public static final double BLACKJACK_INSURANCE = 0.074;
	public static final double ROULETTE = 0.027;
	public static final double SLOTS_COPPER = 0.1024;
	public static final double SLOTS_GOLD = 0.0629;
	public static final double SLOTS_NETHERITE = 0.0396;
	public static final double CRAPS_PASS = 0.0141;
	public static final double CRAPS_DONT = 0.0136;
	public static final double CRAPS_FIELD = 0.0278;
	public static final double COIN_FLIP = 0.02;
	public static final double WHEEL = 1 - 51.5 / 54;
	public static final double SCRATCH_BASIC = 0.205;
	public static final double SCRATCH_GOLD = 0.15;
	public static final double PLINKO_LOW = 0.0344;
	public static final double PLINKO_MEDIUM = 0.0343;
	public static final double PLINKO_HIGH = 0.033;
	public static final double DICE_DUEL = 0.0278;

	/** Lowest edge per game id (ids as used in transactions / {@code PlayResult#gameId}). */
	private static final Map<String, Double> LOWEST = Map.ofEntries(
		Map.entry("blackjack", BLACKJACK),
		Map.entry("roulette", ROULETTE),
		Map.entry("slots", SLOTS_NETHERITE),
		Map.entry("craps", CRAPS_DONT),
		Map.entry("poker", 0.0),
		Map.entry("coin_flip", COIN_FLIP),
		Map.entry("wheel_of_fortune", WHEEL),
		Map.entry("scratch_card", SCRATCH_GOLD),
		Map.entry("plinko", PLINKO_HIGH),
		Map.entry("dice_duel", DICE_DUEL),
		Map.entry("dice_duel_pvp", 0.0));

	private HouseEdges() {}

	/** The game's lowest edge (0 for PvP and unknown ids). */
	public static double of(String gameId) {
		Double e = gameId == null ? null : LOWEST.get(gameId);
		return e == null || !(e > 0) ? 0 : e;
	}

	/** Theoretical loss of {@code stake} chips at {@code edge} (never negative). */
	public static double theoreticalLoss(long stake, double edge) {
		return stake > 0 && edge > 0 ? stake * edge : 0;
	}
}
