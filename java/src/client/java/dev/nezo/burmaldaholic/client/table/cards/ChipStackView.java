package dev.nezo.burmaldaholic.client.table.cards;

import dev.nezo.burmaldaholic.client.fx.FxSprites;
import dev.nezo.burmaldaholic.core.anim.cards.CardLayout;
import dev.nezo.burmaldaholic.core.anim.cards.CardMotion;
import dev.nezo.burmaldaholic.core.text.Texts;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Chip stacks on bet spots, pots and racks (docs/design/visual/cards.md §5, animation/cards.md §0.4): at most five 13 × 8
 * table discs with a 2 px step, largest at the bottom, the exact amount as a runtime label under the stack; other
 * players' stacks use the greyscale disc × their seat tint, atmosphere bots' stacks add the hatch overlay. Also the chip
 * rack buttons ({@code big_<d>} with the gold {@code select} ring). Positions are the stack's base centre.
 */
public final class ChipStackView {
	public static final int DISC_W = 13, DISC_H = 8, STEP = 2;
	/** Seat tints (tables.md §0.4 seat colours). */
	public static final int[] SEAT_TINTS = {0xFFFFB74D, 0xFFBA68C8, 0xFF4FC3F7, 0xFF81C784, 0xFFF06292, 0xFFFFF176, 0xFF90A4AE};
	private static final int[] DENOMS = {1, 5, 25, 100, 500};
	private static final Identifier[] DISC = new Identifier[DENOMS.length];
	private static final Identifier[] BIG = new Identifier[DENOMS.length];
	private static final Identifier DISC_TINT = FxSprites.sprite("cards/chip/disc_tint");
	private static final Identifier DISC_HATCH = FxSprites.sprite("cards/chip/disc_hatch");
	private static final Identifier SELECT = FxSprites.sprite("cards/chip/select");
	private static final int[] FALLBACK = {0xFFF4ECF8, 0xFFD83440, 0xFF3CB44B, 0xFF222222, 0xFF783CBE};

	static {
		for (int i = 0; i < DENOMS.length; i++) {
			DISC[i] = FxSprites.sprite("cards/chip/disc_" + DENOMS[i]);
			BIG[i] = FxSprites.sprite("cards/chip/big_" + DENOMS[i]);
		}
	}

	private ChipStackView() {}

	private static int index(int denom) {
		for (int i = 0; i < DENOMS.length; i++) if (DENOMS[i] == denom) return i;
		return 0;
	}

	/** Seat tint of seat index {@code seat}. */
	public static int seatTint(int seat) {
		return SEAT_TINTS[Math.floorMod(seat, SEAT_TINTS.length)];
	}

	/**
	 * A stack of {@code amount} with its base centre at (cx, baseY). {@code tint} 0 = the real disc colours, else the
	 * seat tint; {@code hatch} for atmosphere bots; {@code alpha} fades the whole stack.
	 */
	public static void stack(GuiGraphicsExtractor g, int cx, int baseY, long amount, int maxDiscs, int tint, boolean hatch, double alpha) {
		int[] discs = CardLayout.discs(amount, maxDiscs);
		int a = CardGfx.white(alpha);
		for (int i = 0; i < discs.length; i++) {
			int x = cx - 6;
			int y = baseY - DISC_H - i * STEP;
			if (tint != 0) CardGfx.sprite(g, DISC_TINT, x, y, DISC_W, DISC_H, CardGfx.alpha(tint, alpha), tint);
			else CardGfx.sprite(g, DISC[index(discs[i])], x, y, DISC_W, DISC_H, a, FALLBACK[index(discs[i])]);
			if (hatch) CardGfx.sprite(g, DISC_HATCH, x, y, DISC_W, DISC_H, a);
		}
	}

	/** Height in px of a stack of {@code amount}. */
	public static int height(long amount, int maxDiscs) {
		int n = CardLayout.discs(amount, maxDiscs).length;
		return n == 0 ? 0 : DISC_H + (n - 1) * STEP;
	}

	/** The amount label under a stack (centred). */
	public static void label(GuiGraphicsExtractor g, Font font, int cx, int baseY, long amount, int color, double alpha) {
		Component t = Texts.number(amount);
		CardGfx.centered(g, font, t, cx, baseY + 1, CardGfx.alpha(color, alpha), true);
	}

	/**
	 * A stack in flight from (sx, sy) to (tx, ty) at progress {@code t} (K chip flight), or swept (inCubic + fade).
	 */
	public static void flying(GuiGraphicsExtractor g, CardMotion.Pose pose, int sx, int sy, int tx, int ty, double t, boolean sweep, long amount,
			int tint, boolean hatch, boolean reduced) {
		if (sweep) CardMotion.sweep(pose, sx, sy, tx, ty, t, reduced);
		else CardMotion.chipFlight(pose, sx, sy, tx, ty, t, reduced);
		if (pose.alpha <= 0.01) return;
		if (pose.scale != 1) {
			g.pose().pushMatrix();
			g.pose().translate((float) pose.x, (float) pose.y);
			g.pose().scale((float) pose.scale, (float) pose.scale);
			stack(g, 0, 0, amount, 5, tint, hatch, pose.alpha);
			g.pose().popMatrix();
		} else {
			stack(g, (int) Math.round(pose.x), (int) Math.round(pose.y), amount, 5, tint, hatch, pose.alpha);
		}
	}

	/** Chip rack button of {@code denom} (22 × 22) at (x, y); the selected one lifts 2 px inside the gold ring. */
	public static void big(GuiGraphicsExtractor g, int denom, int x, int y, boolean selected, boolean hovered, boolean enabled) {
		int lift = selected || (hovered && enabled) ? 2 : 0;
		if (selected) CardGfx.sprite(g, SELECT, x - 2, y - 2, 26, 26, 0xFFFFFFFF);
		CardGfx.sprite(g, BIG[index(denom)], x, y - lift, 22, 22, enabled ? 0xFFFFFFFF : 0xFF7A7A80, FALLBACK[index(denom)]);
	}
}
