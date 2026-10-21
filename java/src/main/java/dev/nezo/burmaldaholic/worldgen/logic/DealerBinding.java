package dev.nezo.burmaldaholic.worldgen.logic;

import dev.nezo.burmaldaholic.core.table.parts.TableFootprint;
import dev.nezo.burmaldaholic.core.table.parts.TableGame;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Where a house dealer's table is (PURE; DL-SPEC §1 "Binding to a table"). A dealer stands on its table's dealer cell
 * ({@link TableFootprint#dealerOffset}) and faces the players, i.e. the table's FACING; the candidates are the inverse
 * of that offset for every {@link TableGame}, plus the legacy compact table one cell in front of the dealer (f − 1
 * pods of the v0.1 templates). The caller accepts a candidate only if the block there is a table's main block of that
 * game with the same facing (or a compact table for the legacy candidate).
 */
public final class DealerBinding {
	private DealerBinding() {}

	/**
	 * @param dx   world x offset from the dealer's cell to the table's main block
	 * @param dz   world z offset
	 * @param game the table kind expected there, {@code null} = a legacy compact (SINGLE) table
	 */
	public record Candidate(int dx, int dz, @Nullable TableGame game) {}

	/** Candidates for a dealer facing {@code (fx, fz)} (the unit step it looks along, e.g. SOUTH = (0, 1)). */
	public static List<Candidate> candidates(int fx, int fz) {
		List<Candidate> out = new ArrayList<>();
		for (TableGame g : TableGame.values()) {
			int[] o = TableFootprint.dealerOffset(g, fx, fz);
			out.add(new Candidate(-o[0], -o[1], g));
		}
		out.add(new Candidate(fx, fz, null));
		return out;
	}
}
