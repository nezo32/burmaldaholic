package dev.nezo.burmaldaholic.multiplayer.logic;

import java.util.Collection;
import java.util.Map;
import java.util.OptionalLong;

/**
 * Owner bankroll solvency (GAME_DESIGN.md §18.2). PURE.
 *
 * <p>The per-stake reservation itself is done by core ({@code CasinoTableBlockEntity#placeBet} reserves the
 * game's worst case in the bankroll). This class knows the worst-case TOTAL return of one round per chip of
 * base bet for every table (used for the insolvency rule "bankroll &lt; the smallest worst case of any table
 * at its minimum bet") and the owner's withdrawal limit {@code bankroll − reserved}.
 */
public final class Solvency {
	/**
	 * Worst-case total return (stake included) per chip of the round's base bet. Keys are table registry names
	 * (checked first, e.g. {@code slot_machine_copper}) or game ids (e.g. {@code roulette}).
	 * <ul>
	 *   <li>blackjack: 4 split hands, all doubled, all win 1:1 (16×) + insurance ½ bet at 2:1 (1.5×) = 17.5×</li>
	 *   <li>roulette: straight-up 35:1 → 36×; craps: Pass + 5× odds on 6/8 at 6:5 → 13×</li>
	 *   <li>slots: Copper best line 150×; Gold / Netherite owned machines 1000× (3 stars, §8.5)</li>
	 *   <li>plinko: High bin 170×; wheel: X segment 10×; coin flip / dice duel 2×</li>
	 *   <li>poker: PvP — the owner only takes rake, no exposure (0)</li>
	 * </ul>
	 */
	public static final Map<String, Double> WORST_CASE_PER_CHIP = Map.ofEntries(
		Map.entry("blackjack", 17.5),
		Map.entry("roulette", 36.0),
		Map.entry("craps", 13.0),
		Map.entry("slots", 1000.0),
		Map.entry("slot_machine_copper", 150.0),
		Map.entry("slot_machine_gold", 1000.0),
		Map.entry("slot_machine_netherite", 1000.0),
		Map.entry("plinko", 170.0),
		Map.entry("plinko_machine", 170.0),
		Map.entry("wheel_of_fortune", 10.0),
		Map.entry("scratch_card", 250.0),
		Map.entry("coin_flip", 2.0),
		Map.entry("dice_duel", 2.0),
		Map.entry("poker", 0.0));

	/** For a table whose game is unknown (conservative: a straight-up roulette bet). */
	public static final double UNKNOWN_WORST_CASE_PER_CHIP = 36.0;

	private Solvency() {}

	/** Worst-case multiplier: table name first, then game id, then the conservative fallback. */
	public static double worstCasePerChip(String game, String tableName) {
		if (tableName != null) {
			Double v = WORST_CASE_PER_CHIP.get(tableName);
			if (v != null) {
				return v;
			}
		}
		if (game != null) {
			Double v = WORST_CASE_PER_CHIP.get(game);
			if (v != null) {
				return v;
			}
		}
		return UNKNOWN_WORST_CASE_PER_CHIP;
	}

	/** Worst-case total return of one round at {@code bet} (whole chips, rounded up). */
	public static long worstCaseAt(double perChip, long bet) {
		if (perChip <= 0 || bet <= 0) {
			return 0;
		}
		return (long) Math.ceil(perChip * bet);
	}

	/** One owned table as seen by the insolvency rule. */
	public record ExposedTable(String game, String tableName, long minBet, boolean open) {}

	/** The smallest worst case among open tables that expose the bankroll; empty if none risk it. */
	public static OptionalLong cheapestWorstCase(Collection<ExposedTable> tables) {
		long best = Long.MAX_VALUE;
		for (ExposedTable t : tables) {
			if (!t.open()) {
				continue;
			}
			long wc = worstCaseAt(worstCasePerChip(t.game(), t.tableName()), Math.max(1, t.minBet()));
			if (wc > 0 && wc < best) {
				best = wc;
			}
		}
		return best == Long.MAX_VALUE ? OptionalLong.empty() : OptionalLong.of(best);
	}

	/**
	 * Insolvency (§18.2): broke while the bankroll balance is below the cheapest worst case of the open tables.
	 * A casino without exposed tables (none, or only poker) is never broke.
	 */
	public static boolean isBroke(long bankroll, Collection<ExposedTable> tables) {
		OptionalLong need = cheapestWorstCase(tables);
		return need.isPresent() && bankroll < need.getAsLong();
	}

	/** What the owner may withdraw: {@code bankroll − reserved}. */
	public static long withdrawable(long bankroll, long reserved) {
		return Math.max(0, bankroll - Math.max(0, reserved));
	}
}
