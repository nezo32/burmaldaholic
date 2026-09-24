package dev.nezo.burmaldaholic.games.slots.v2.present;

import dev.nezo.burmaldaholic.games.slots.v2.logic.Machine;

/**
 * Per-machine presentation data of the symbols and the machine theme (slots.md §3.1, §3.2): symbol ids (lang
 * keys {@code gui.burmaldaholic.slots.symbol.<id>}), way-path colours, jackpot badge colours and the theme
 * palette. Symbol indices are the SLOTS.md §2 order ({@code WD SC BN|CN …}), the same order as
 * {@code MachineDef.codes()} and the rows of the symbol sheets (slots.md §9.1). PURE data (ARGB ints).
 */
public final class SymbolStyle {
	/** Symbol ids per machine, SLOTS.md §2 order. */
	private static final String[][] IDS = {
		{"totem", "compass", "chest", "diamond", "emerald", "gold_ingot", "iron_ingot", "apple", "carrot", "wheat", "sweet_berries"},
		{"lava_bucket", "ghast_tear", "piglin_coin", "wither_skull", "blaze_rod", "magma_cream", "quartz", "nether_wart", "crimson_fungus",
			"warped_fungus", "glowstone"},
		{"dragon_egg", "ender_eye", "end_crystal", "dragon_head", "elytra", "shulker_shell", "chorus_fruit", "ender_pearl", "purpur", "end_rod",
			"end_stone"}};

	/** Way-path colour per symbol (slots.md §3.2 table). */
	private static final int[][] PATH = {
		{0xFFFFD640, 0xFFFF5A4A, 0xFFC8903C, 0xFF5CE8E0, 0xFF40D060, 0xFFFFC400, 0xFFD8D8E0, 0xFFE83030, 0xFFFF9020, 0xFFE0C050, 0xFFC02050},
		{0xFFFF7A1A, 0xFFBFEFFF, 0xFFFFC400, 0xFF8A8A8A, 0xFFFFB030, 0xFFFF6A00, 0xFFF4ECE0, 0xFFA01010, 0xFFC02020, 0xFF20B0A0, 0xFFFFE070},
		{0xFFB040FF, 0xFF40E0A0, 0xFFFF9AE8, 0xFF6A2A8A, 0xFF8C8CB0, 0xFFA77BA7, 0xFF9A5A9A, 0xFF208070, 0xFFC8A0C8, 0xFFF4ECF8, 0xFFE8E4A8}};

	/** Jackpot badge colours Mini, Minor, Major, Grand (slots.md §3.2); index = tier − 1. */
	public static final int[] JACKPOT_COLORS = {0xFF80FF40, 0xFF4080FF, 0xFFFFD640, 0xFFFF40C0};

	/** Symbol index of the wild / scatter / bonus-or-coin (SLOTS.md §2: always 0 / 1 / 2). */
	public static final int WILD = 0;
	public static final int SCATTER = 1;
	public static final int BONUS = 2;

	private SymbolStyle() {}

	public static int count(Machine m) {
		return IDS[m.ordinal()].length;
	}

	/** Lang id of symbol {@code s} ({@code gui.burmaldaholic.slots.symbol.<id>}). */
	public static String id(Machine m, int s) {
		String[] ids = IDS[m.ordinal()];
		return ids[Math.floorMod(s, ids.length)];
	}

	public static String nameKey(Machine m, int s) {
		return "gui.burmaldaholic.slots.symbol." + id(m, s);
	}

	public static int pathColor(Machine m, int s) {
		int[] c = PATH[m.ordinal()];
		return c[Math.floorMod(s, c.length)];
	}

	/** Lighter flow-dash colour of a path (+45 % towards white). */
	public static int flowColor(int argb) {
		int a = argb >>> 24;
		int r = (argb >> 16) & 0xFF;
		int g = (argb >> 8) & 0xFF;
		int b = argb & 0xFF;
		r += (255 - r) * 45 / 100;
		g += (255 - g) * 45 / 100;
		b += (255 - b) * 45 / 100;
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	/** High-value or special symbol (idle flourishes play only on these, slots.md §3.2). */
	public static boolean flourishes(int s) {
		return s <= 6;
	}

	/** Theme colours of a machine (slots.md §3.1), base game and free spins. */
	public record Theme(int trim, int accent, int drumTop, int drumBottom, int skyTop, int skyBottom, int fsSkyTop, int fsSkyBottom,
			int bonusAccent, int glow) {}

	private static final Theme[] THEMES = {
		new Theme(0xFFC06A3C, 0xFF5FA38A, 0xFFF4ECD8, 0xFFE2D6BC, 0xFF7EC0EE, 0xFFCFE8FF, 0xFF10183A, 0xFF28306A, 0xFF8B5A2B, 0xFFFFD640),
		new Theme(0xFFFFD640, 0xFFFF7A1A, 0xFF5A1414, 0xFF2A0808, 0xFF2A0A0A, 0xFF6A1A08, 0xFF3A0000, 0xFF8A1A00, 0xFFFFC400, 0xFFFF7A1A),
		new Theme(0xFFA77BA7, 0xFFF4ECF8, 0xFF1A1028, 0xFF0C0814, 0xFF06040C, 0xFF1A1028, 0xFF0A0418, 0xFF3A1458, 0xFFFF9AE8, 0xFFB040FF)};

	public static Theme theme(Machine m) {
		return THEMES[m.ordinal()];
	}
}
