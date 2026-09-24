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

	private CasinoPalette() {}

	/** Replaces the alpha of an ARGB colour ({@code alpha} 0–1). */
	public static int withAlpha(int argb, float alpha) {
		int a = Math.round(Math.max(0, Math.min(1, alpha)) * 255);
		return (a << 24) | (argb & 0x00FFFFFF);
	}
}
