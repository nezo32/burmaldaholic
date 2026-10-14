package dev.nezo.burmaldaholic.core.ui.v2;

/**
 * How much room a table screen has beyond its tier (docs/architecture/ui-v2.md §3.7). {@link #ROOMY} from a 800 × 450
 * frame up (1080p at GUI 2, 1440p at GUI 2 / 3, 4K at GUI 2–4): the chrome keeps T3 metrics and games that opt in
 * ({@code grid.room()}) give the extra space to content — longer histories, seat plates with stack and last action,
 * larger card sprites — never to a pose-scaled ("zoomed in") copy of the 640 × 360 layout. Pure.
 */
public enum Room {
	NORMAL,
	ROOMY;

	public static final int ROOMY_W = 800, ROOMY_H = 450;

	/** The room of a {@code w × h} frame (layout px). */
	public static Room of(int w, int h) {
		return w >= ROOMY_W && h >= ROOMY_H ? ROOMY : NORMAL;
	}
}
