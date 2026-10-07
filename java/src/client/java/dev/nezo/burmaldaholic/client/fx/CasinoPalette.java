package dev.nezo.burmaldaholic.client.fx;

/**
 * Palette tokens (global.md §2.1), ARGB. The asset generator's palette
 * ({@code tools/assets/lib/palette.mjs}) holds the same hex values; change both together.
 */
public final class CasinoPalette {
	public static final int BG_DEEP = 0xE626103C;
	public static final int BG_DARKEST = 0xFF140822;
	public static final int INK = 0xFF180A28;
	public static final int FRAME = 0xFF783CBE;
	public static final int GLINT = 0xFFBE5AFF;
	public static final int LILAC = 0xFFD696FF;
	public static final int FELT = 0xFF1E5E3A;
	public static final int FELT_BORDER = 0xFF0E2E1C;
	public static final int CHIP_RED = 0xFFD83440;
	public static final int CHIP_RED_LIGHT = 0xFFFF6E6A;
	public static final int CHIP_RED_DARK = 0xFF8C1834;
	public static final int BONE = 0xFFF4ECF8;
	public static final int BONE_SHADE = 0xFFC0B0DC;
	public static final int GOLD = 0xFFFFD640;
	public static final int GOLD_SHADE = 0xFFB07010;
	public static final int BONUS = 0xFF80FF40;
	public static final int CURSE = 0xFF6FA86A;
	public static final int CURSE_BG = 0xFF3A1450;
	public static final int COOL = 0xFF8FA8C8;

	/** VIP tiers Bronze … Netherite. */
	public static final int[] VIP = {0xFFC8763C, 0xFFC8C8D8, 0xFFFFD640, 0xFFE8F4FF, 0xFF5CE8E0, 0xFF5A4A58};

	// ---- table UI v2 tokens (docs/design/visual/v2/REDESIGN.md §5; K lane). Themes swap only felt / rail / print. ----
	public static final int V2_INK = 0xFF180A28;
	public static final int V2_PANEL = 0xFF22122F;
	public static final int V2_PANEL_HI = 0xFF341E48;
	public static final int V2_EDGE = 0xFF783CBE;
	public static final int V2_TEXT = 0xFFF4ECF8;
	public static final int V2_MUTED = 0xFFC0B0DC;
	public static final int V2_GOLD = 0xFFFFD640;
	public static final int V2_GOLD_SHADE = 0xFFB07010;
	public static final int V2_WIN = 0xFF80FF40;
	public static final int V2_LOSE = 0xFFFF6E6A;
	public static final int V2_PUSH = 0xFF8FA8C8;
	public static final int V2_WIN_FILL = 0xFF2E8C28;
	public static final int V2_LOSE_FILL = 0xFFB0242E;
	public static final int V2_PUSH_FILL = 0xFF4A5A78;
	public static final int V2_GOLD_FILL = 0xFFC88A1C;
	public static final int V2_YOU = 0xFF5CE8E0;
	public static final int V2_FELT = 0xFF1E5E3A;
	public static final int V2_FELT_PRINT = 0xFFE8D9A0;
	public static final int V2_RAIL = 0xFF5A3418;
	public static final int V2_RAIL_DARK = 0xFF3A2010;
	public static final int V2_RAIL_HI = 0xFF8A5628;
	public static final int V2_BTN = 0xFF4E287C;
	public static final int V2_BTN_HI = 0xFF7046A4;
	public static final int V2_DANGER = 0xFF9C2430;
	public static final int V2_DANGER_HI = 0xFFC03440;

	private CasinoPalette() {}

	/** Replaces the alpha of an ARGB colour ({@code alpha} 0–1). */
	public static int withAlpha(int argb, float alpha) {
		int a = Math.round(Math.max(0, Math.min(1, alpha)) * 255);
		return (a << 24) | (argb & 0x00FFFFFF);
	}
}
