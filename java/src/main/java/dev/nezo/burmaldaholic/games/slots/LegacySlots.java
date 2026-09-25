package dev.nezo.burmaldaholic.games.slots;

import dev.nezo.burmaldaholic.games.slots.logic.SlotEngine;
import dev.nezo.burmaldaholic.games.slots.logic.SlotTable;
import dev.nezo.burmaldaholic.games.slots.logic.Symbol;
import dev.nezo.burmaldaholic.games.slots.logic.Tier;
import net.minecraft.nbt.CompoundTag;
import org.jspecify.annotations.Nullable;

/**
 * Settle-only v1 evaluator (SLOTS.md §8.1, docs/architecture/animation.md A7): after the v2 cut-over, records written
 * by v1 (3×3 grids) are paid at their stored payout with the kept v1 {@link SlotEngine}; removed one release after the
 * cut-over together with {@code games.slots.logic}.
 *
 * <p>Java v1 never persisted a drawn grid: a v1 spin lived in memory until its timer and a crash refunded the open
 * stake (core). The only v1 rounds that can meet v2 are therefore (a) a spin still in memory when an admin flips
 * {@code slots.v2} at runtime — the block entity finishes it through its v1 path — and (b) the record format below, which
 * v1 screens received as {@code result} and which tests use as the fixture: {@code {tier, grid[9], line_bet, lines}}.
 */
public final class LegacySlots {
	private LegacySlots() {}

	/**
	 * Payout of a v1 record with the v1 paytable of {@code table} (wins only, no jackpot: v1 jackpots were paid from the
	 * pool at settlement, and the v1 pools have moved to the v2 Grand increments, SLOTS.md §5.3), or null if malformed.
	 */
	public static @Nullable Long payout(CompoundTag record, SlotTable table) {
		int[] g = record.getIntArray("grid").orElse(null);
		long lineBet = record.getLongOr("line_bet", -1);
		if (g == null || g.length != 9 || lineBet <= 0) return null;
		Symbol[] symbols = Symbol.values();
		Symbol[][] grid = new Symbol[3][3];
		for (int i = 0; i < 9; i++) {
			if (g[i] < 0 || g[i] >= symbols.length) return null;
			grid[i / 3][i % 3] = symbols[g[i]];
		}
		return SlotEngine.evaluate(grid, table, lineBet).basePayout();
	}

	/** The v1 tier of a v1 record ({@code copper/gold/netherite}), or null. */
	public static @Nullable Tier tier(CompoundTag record) {
		return Tier.byId(record.getStringOr("tier", ""));
	}
}
