package dev.nezo.burmaldaholic.client.table.cards;

import dev.nezo.burmaldaholic.client.fx.FxSprites;
import dev.nezo.burmaldaholic.core.anim.cards.CardMotion;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * K7 stamps (docs/design/visual/cards.md §8.4, animation/cards.md §0.3): a nine-slice {@code stamp/<kind>} (32 × 16,
 * border 5) sized to its runtime text (+14 px), scaled in 1.4 → 1 with outBack and rotated −6° … +6°; reduced motion
 * fades it in without rotation. Gold stamps use dark brown ink without a shadow, the others white with a shadow.
 */
public final class TableStamp {
	public enum Kind {
		GOLD("gold", 0xFF5A2A00, false),
		RED("red", 0xFFFFFFFF, true),
		GREEN("green", 0xFFFFFFFF, true),
		VIOLET("violet", 0xFFFFFFFF, true);

		final String id;
		final int ink;
		final boolean shadow;

		Kind(String id, int ink, boolean shadow) {
			this.id = id;
			this.ink = ink;
			this.shadow = shadow;
		}
	}

	private static final int[] FALLBACK = {0xFFFFD640, 0xFFD83440, 0xFF2FA64A, 0xFF8A3AAA};

	private TableStamp() {}

	/**
	 * Draws a stamp centred on (cx, cy) {@code ageMs} after it appeared (negative = not yet), rotated {@code deg}.
	 */
	public static void draw(GuiGraphicsExtractor g, Font font, Component text, Kind kind, double cx, double cy, double deg, double ageMs,
			boolean reduced, CardMotion.Pose pose) {
		if (ageMs < 0) return;
		CardMotion.stamp(pose, ageMs / CardMotion.STAMP_MS, deg, reduced);
		if (pose.alpha <= 0.01) return;
		int w = font.width(text) + 14;
		int h = 16;
		CardGfx.pushBox(g, cx - w / 2.0, cy - h / 2.0, w, h, pose.rot, pose.scale, pose.scale);
		CardGfx.sprite(g, FxSprites.sprite("cards/stamp/" + kind.id), 0, 0, w, h, CardGfx.white(pose.alpha), FALLBACK[kind.ordinal()]);
		CardGfx.text(g, font, text, 7, 4, CardGfx.alpha(kind.ink, pose.alpha), kind.shadow);
		CardGfx.pop(g);
	}
}
