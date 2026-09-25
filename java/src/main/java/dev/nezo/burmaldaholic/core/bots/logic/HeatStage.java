package dev.nezo.burmaldaholic.core.bots.logic;

/**
 * Daily heat stage of a player against HOUSE-funded money bots (BOTS.md §5.4). Checked after every
 * settlement; the round that crosses a line is always kept (no clawback).
 */
public enum HeatStage {
	/** Below the cap: bots as configured. */
	NONE,
	/** {@code botNetToday ≥ cap}: "Word got around" — at poker only HARD house bots sit with the player. */
	HARD_ONLY,
	/** {@code botNetToday ≥ sulkMultiplier × cap}: house-funded bots refuse the player until the next MCD. */
	SULKING;

	public boolean atLeast(HeatStage other) {
		return ordinal() >= other.ordinal();
	}
}
