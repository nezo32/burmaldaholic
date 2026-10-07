package dev.nezo.burmaldaholic.client.ui.v2;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.SeatColumn;
import dev.nezo.burmaldaholic.core.ui.v2.Stamps;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * A result stamp: the outcome WORD only (net in the value pill; docs/architecture/ui-v2.md D8), in the seat's outcome
 * slot above the cards, 2× for the hero and 1× for others, or a coloured dot when no level fits ({@link Stamps},
 * {@link SeatColumn#table}). Pops in over 120 ms (reduce motion: none).
 */
public final class Stamp {
	public static final int POP_MS = 120;

	private Stamp() {}

	public static int fill(Stamps.Kind k) {
		return switch (k) {
			case WIN -> CasinoPalette.V2_WIN_FILL;
			case LOSE -> CasinoPalette.V2_LOSE_FILL;
			case PUSH -> CasinoPalette.V2_PUSH_FILL;
			case GOLD -> CasinoPalette.V2_GOLD_FILL;
		};
	}

	public static int textColor(Stamps.Kind k) {
		return k == Stamps.Kind.GOLD ? 0xFFFFF0B0 : CasinoPalette.V2_TEXT;
	}

	/** Width of the stamp for {@code word} at {@code scale}. */
	public static int width(Font font, Component word, int scale) {
		return Stamps.width(font.width(word), scale);
	}

	/** The stamp word of an outcome. */
	public static Component word(Stamps.Outcome o) {
		return Component.translatable(o.wordKey);
	}

	/** Draws {@code word} as a stamp in {@code box} at {@code scale} (1 or 2), {@code ageMs} since it appeared. */
	public static void draw(GuiGraphicsExtractor g, Font font, Box box, Component word, Stamps.Kind kind, int scale, long ageMs) {
		if (box.isEmpty()) return;
		float s = FxSettings.reduceMotion() || ageMs >= POP_MS ? 1f : popScale(ageMs);
		boolean pushed = s != 1f;
		if (pushed) {
			g.pose().pushMatrix();
			g.pose().translate(box.cx(), box.cy());
			g.pose().scale(s, s);
			g.pose().translate(-box.cx(), -box.cy());
		}
		int fill = fill(kind);
		if (!V2Draw.sprite(g, V2Sprites.stamp(kind), box, 0xFFFFFFFF)) {
			V2Draw.fill(g, box, CasinoPalette.V2_INK);
			g.fill(box.x() + 1, box.y() + 1, box.right() - 1, box.bottom() - 1, fill);
			g.fill(box.x() + 1, box.y() + 1, box.right() - 1, box.y() + 2, V2Draw.alpha(0xFFFFFFFF, 0.35f));
			g.fill(box.x() + 1, box.bottom() - 2, box.right() - 1, box.bottom() - 1, V2Draw.darker(fill, 0.7f));
		}
		V2Draw.centred(g, font, word, box, scale, textColor(kind), true);
		if (pushed) g.pose().popMatrix();
	}

	/** Draws the outcome resolved by {@link SeatColumn#table}: {@code level} of {@link Stamps#levels} or the dot. */
	public static void drawResolved(GuiGraphicsExtractor g, Font font, Box outcomeBox, Stamps.Outcome outcome, int level, boolean hero,
			long ageMs) {
		int scale = Stamps.scaleOf(level, hero);
		if (scale == 0) dot(g, outcomeBox, outcome.kind);
		else draw(g, font, outcomeBox, word(outcome), outcome.kind, scale, ageMs);
	}

	/** The last-resort outcome: a {@link SeatColumn#DOT}² coloured dot. */
	public static void dot(GuiGraphicsExtractor g, Box box, Stamps.Kind kind) {
		Box d = box.centred(SeatColumn.DOT, SeatColumn.DOT);
		if (V2Draw.sprite(g, V2Sprites.stampDot(kind), d, 0xFFFFFFFF)) return;
		g.fill(d.x() + 2, d.y(), d.right() - 2, d.bottom(), CasinoPalette.V2_INK);
		g.fill(d.x(), d.y() + 2, d.right(), d.bottom() - 2, CasinoPalette.V2_INK);
		g.fill(d.x() + 1, d.y() + 1, d.right() - 1, d.bottom() - 1, CasinoPalette.V2_INK);
		g.fill(d.x() + 2, d.y() + 1, d.right() - 2, d.bottom() - 1, fill(kind));
		g.fill(d.x() + 1, d.y() + 2, d.right() - 1, d.bottom() - 2, fill(kind));
	}

	/** 0.6 → 1.15 → 1 over {@link #POP_MS}. */
	static float popScale(long ms) {
		float t = Math.max(0, Math.min(1, ms / (float) POP_MS));
		return t < 0.7f ? 0.6f + 0.55f * (t / 0.7f) : 1.15f - 0.15f * ((t - 0.7f) / 0.3f);
	}
}
