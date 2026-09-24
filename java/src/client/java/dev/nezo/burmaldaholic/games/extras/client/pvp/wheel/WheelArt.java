package dev.nezo.burmaldaholic.games.extras.client.pvp.wheel;

import dev.nezo.burmaldaholic.core.pvp.logic.PvpMath;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.extras.pvp.wheel.WheelMath;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Wheel Party drawing: proportional slices in join order, one of the 16 dye colours each (PVP.md §6.1, §6.5). */
public final class WheelArt {
	/** The 16 dye colours, ordered for contrast between neighbouring slices. */
	public static final int[] COLORS = {
		0xFFB02E26, 0xFF3C44AA, 0xFFFED83D, 0xFF5E7C16, 0xFF8932B8, 0xFFF9801D, 0xFF169C9C, 0xFFC74EBD,
		0xFF80C71F, 0xFF3AB3DA, 0xFFF38BAA, 0xFF835432, 0xFFF9FFFE, 0xFF474F52, 0xFF9D9D97, 0xFF1D1D21,
	};
	private static final int STEP_DEG = 2;

	private WheelArt() {}

	public static int color(int index) {
		return COLORS[Math.floorMod(index, COLORS.length)];
	}

	/**
	 * Draws the wheel: slice i covers {@code [C_{i−1}, C_i)} of the pot, starting at the top and going clockwise,
	 * rotated by {@code rotationDeg} (the pointer is fixed at the top).
	 */
	public static void wheel(GuiGraphicsExtractor g, int cx, int cy, int r, long[] stakes, double rotationDeg) {
		long pot = PvpMath.pot(stakes);
		disc(g, cx, cy, r + 3, 0xFF5A3A1A);
		if (pot <= 0) {
			disc(g, cx, cy, r, 0xFF444444);
		} else {
			int hw = (int) Math.ceil(r * Math.sin(Math.toRadians(STEP_DEG / 2.0 + 0.6))) + 1;
			for (int deg = 0; deg < 360; deg += STEP_DEG) {
				long u = Math.min(pot - 1, (long) Math.floor((deg + STEP_DEG / 2.0) / 360.0 * pot));
				int owner = WheelMath.winner(stakes, u);
				g.pose().pushMatrix();
				g.pose().translate(cx, cy);
				g.pose().rotate((float) Math.toRadians(rotationDeg + deg + STEP_DEG / 2.0 - 90));
				g.fill(0, -hw, r, hw, color(owner));
				g.pose().popMatrix();
			}
		}
		disc(g, cx, cy, 6, 0xFF3A2410);
		disc(g, cx, cy, 3, 0xFFFFD700);
		pointer(g, cx, cy - r + 6, 8, 0xFFFFFFFF);
	}

	static void disc(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
		for (int dy = -r; dy <= r; dy++) {
			int half = (int) Math.round(Math.sqrt((double) r * r - (double) dy * dy));
			g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
		}
	}

	static void pointer(GuiGraphicsExtractor g, int cx, int tipY, int h, int color) {
		for (int i = 0; i < h; i++) {
			g.fill(cx - i, tipY - i - 1, cx + i + 1, tipY - i, color);
		}
	}

	/** Share of the wheel as a percentage label ({@code gui.burmaldaholic.pvp.percent}), one decimal when below 10 %. */
	public static Component share(long stake, long pot) {
		long bp = WheelMath.shareBasisPoints(stake, pot);
		String plain = bp >= 1000 || bp % 100 == 0 ? Long.toString(Math.round(bp / 100.0)) : String.format(java.util.Locale.ROOT, "%.1f", bp / 100.0);
		return Component.translatable("gui.burmaldaholic.pvp.percent", Texts.decimal(plain));
	}
}
