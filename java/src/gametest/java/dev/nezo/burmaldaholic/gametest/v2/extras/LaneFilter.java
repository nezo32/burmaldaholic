package dev.nezo.burmaldaholic.gametest.v2.extras;

/**
 * The opt-in filter of the v2 lane client tests (ui-v2.md §11.2): {@code BURMALDAHOLIC_CGT=<lane>[,<lane>…]} runs only
 * those lanes' classes so one lane can iterate with a single client run; unset = everything runs.
 */
final class LaneFilter {
	private LaneFilter() {}

	static boolean on(String lane) {
		String v = System.getenv("BURMALDAHOLIC_CGT");
		if (v == null || v.isBlank()) return true;
		for (String s : v.split(",")) if (s.trim().equalsIgnoreCase(lane)) return true;
		return false;
	}
}
