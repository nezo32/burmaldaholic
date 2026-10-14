package dev.nezo.burmaldaholic.core.ui.v2;

/**
 * How a {@link HitMap} target was pressed — the kit's own meaning, never a raw mouse-button number. The client maps the
 * real event to this at the edge ({@code client/ui/v2/V2Input}, via the runtime-resolved {@code Inputs}): Minecraft 26.3
 * (SDL3) renumbered every mouse button (left 0 → 1, right 1 → 3), so raw numbers must not travel into the kit or the games.
 */
public enum Press {
	/** Left click, a bound key, or Enter on the focused target: the target's main action. */
	LEFT,
	/** Right click: take back (e.g. remove a chip). */
	RIGHT,
	/** Mouse wheel up over a target: step +. */
	SCROLL_UP,
	/** Mouse wheel down over a target: step −. */
	SCROLL_DOWN;

	public boolean left() {
		return this == LEFT;
	}

	public boolean right() {
		return this == RIGHT;
	}

	/** +1 for {@link #SCROLL_UP}, −1 for {@link #SCROLL_DOWN}, 0 otherwise. */
	public int scroll() {
		return this == SCROLL_UP ? 1 : this == SCROLL_DOWN ? -1 : 0;
	}
}
