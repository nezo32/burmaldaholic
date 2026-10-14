package dev.nezo.burmaldaholic.core.table.parts;

import java.util.Collection;

/**
 * Pure decision for unfolding a legacy compact table into a full 3×2 table (MT-DESIGN §5). The
 * caller classifies every non-main footprint cell (and its floor) and the dealer cell; this class
 * decides. It never asks to touch a foreign block.
 */
public final class MigrationDecision {
	/** What lives in one footprint cell (non-main cells only). */
	public enum CellCheck {
		/** Air (replaceable, non-fluid) with a standable floor below. */
		FREE,
		/** A carpet that will pop as an item (★1) with a floor below. */
		CARPET,
		/** No solid block under the cell (or a fluid there). */
		FLOOR_MISSING,
		/** A player stands in the cell. */
		PLAYER,
		/** A casino NPC dealer stands in the cell and the dealer cell is standable: it gets teleported there. */
		DEALER_MOVABLE,
		/** A casino NPC dealer stands in the cell but the dealer cell is not standable. */
		DEALER_STUCK,
		/** Some other living entity (mob, villager, armour stand…) is in the cell. */
		OTHER_LIVING,
		/** The cell's chunk isn't loaded. */
		UNLOADED,
		/** Any other block (incl. fluids, other tables, decorations): never touched. */
		BLOCKED
	}

	public enum Outcome {
		/** Grow now: SINGLE → MAIN on the same block, place the parts. */
		GROW,
		/** Stay a compact 1×1 table (playable; sneak-use retries). */
		COMPACT,
		/** Something temporary is in the way: try again on the next load. */
		RETRY
	}

	private MigrationDecision() {}

	/**
	 * Permanent obstacles (a block, a missing floor, a dealer with nowhere to go) → COMPACT; else
	 * temporary ones (player, other living entity, unloaded chunk) → RETRY; else GROW.
	 */
	public static Outcome of(Collection<CellCheck> cells) {
		boolean retry = false;
		for (CellCheck c : cells) {
			switch (c) {
				case BLOCKED, FLOOR_MISSING, DEALER_STUCK -> {
					return Outcome.COMPACT;
				}
				case PLAYER, OTHER_LIVING, UNLOADED -> retry = true;
				default -> {}
			}
		}
		return retry ? Outcome.RETRY : Outcome.GROW;
	}
}
