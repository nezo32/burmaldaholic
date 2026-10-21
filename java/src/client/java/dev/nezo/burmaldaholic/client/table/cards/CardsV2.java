package dev.nezo.burmaldaholic.client.table.cards;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * Cards on the v2 card-table screens (blackjack, baccarat; Hold'em / UTH consume it): draws a card into a layout box
 * of the tier's card size — S 13 × 17, M 21 × 29, L 37 × 49 from the existing atlases and XL 49 × 65 from
 * {@code faces_xl*.png} / {@code backs_xl.png} (drawn natively, never scaled) — plus the deal slide from the shoe and
 * the flip of a card that turns face up. Pure drawing; the layout decides every box.
 */
public final class CardsV2 {
	private CardsV2() {}

	public static final int XL_W = 49, XL_H = 65;
	private static final Identifier[] XL = {Burmaldaholic.id("textures/gui/core/cards/faces_xl.png"),
		Burmaldaholic.id("textures/gui/core/cards/faces_xl_classic.png")};
	private static final Identifier XL_BACKS = Burmaldaholic.id("textures/gui/core/cards/backs_xl.png");
	/** Deal slide and flip durations (ms). */
	public static final int DEAL_MS = 260, FLIP_MS = 240;

	/** The atlas size for a card box width: XL / L / M / S ({@link CardSprites} sizes, XL = 3). */
	public static int sizeFor(int w) {
		if (w >= XL_W) return 3;
		if (w >= CardSprites.w(CardSprites.L)) return CardSprites.L;
		if (w >= CardSprites.w(CardSprites.M)) return CardSprites.M;
		return CardSprites.S;
	}

	public static int w(int size) {
		return size == 3 ? XL_W : CardSprites.w(size);
	}

	public static int h(int size) {
		return size == 3 ? XL_H : CardSprites.h(size);
	}

	/**
	 * Draws card {@code code} (−1 = back of design {@code back}) at (x, y) in the atlas {@code size}; {@code flip} 0..1
	 * narrows and widens it (back first), {@code argb} dims.
	 */
	public static void card(GuiGraphicsExtractor g, Font font, int code, int back, int size, int x, int y, float flip, int argb) {
		int w = w(size);
		int h = h(size);
		boolean flipping = flip > 0 && flip < 1 && code >= 0;
		boolean showFace = code >= 0 && (!flipping || flip >= 0.5f);
		if (flipping) {
			float sx = Math.max(0.05f, (float) Math.abs(Math.cos(Math.PI * flip)));
			g.pose().pushMatrix();
			g.pose().translate(x + w / 2f, y);
			g.pose().scale(sx, 1f);
			g.pose().translate(-(x + w / 2f), -y);
		}
		// XL (3) uses the L shadow; L = 0, M = 1, S = 2, so min(size, L) gave every card the L shadow (big dark boxes at T1)
		CardSprites.shadow(g, size == 3 ? CardSprites.L : size, x, y, 0.5 * (argb >>> 24) / 255.0);
		if (size == 3) {
			if (showFace) {
				CardGfx.tex(g, XL[CardSprites.classicDeck ? 1 : 0], x, y, w, h, (CardSprites.rank(code) - 1) * w, CardSprites.suit(code) * h, w, h, 13 * w, 4 * h,
					argb);
				int color = CardGfx.alpha(CardSprites.suitColor(code), (argb >>> 24) / 255.0);
				var idx = CardSprites.rankText(CardSprites.rank(code));
				CardGfx.text(g, font, idx, x + 3, y + 3, color, false);
				int tw = Math.max(1, font.width(idx) - 1);
				g.pose().pushMatrix();
				g.pose().translate(x + w - 3 - tw / 2f, y + h - 7f);
				g.pose().rotate((float) Math.PI);
				CardGfx.text(g, font, idx, -(tw + 1) / 2, -4, color, false);
				g.pose().popMatrix();
			} else {
				CardGfx.tex(g, XL_BACKS, x, y, w, h, 0, Math.max(0, Math.min(5, back)) * h, w, h, w, 6 * h, argb);
			}
		} else if (showFace) {
			CardSprites.face(g, font, code, size, x, y, argb);
		} else {
			CardSprites.back(g, back, size, x, y, argb);
		}
		if (flipping) g.pose().popMatrix();
	}

	/** Draws a card into a box of the tier's card size (centred when the atlas card is smaller). */
	public static void card(GuiGraphicsExtractor g, Font font, int code, int back, Box box, float flip, int argb) {
		int size = sizeFor(box.w());
		int x = box.x() + (box.w() - w(size)) / 2;
		int y = box.y() + (box.h() - h(size)) / 2;
		card(g, font, code, back, size, x, y, flip, argb);
	}

	/** Slide progress 0..1 of a card dealt at {@code dealMs} (level ms); 1 with reduced motion. */
	public static float deal(double nowMs, double dealMs) {
		if (FxSettings.reduceMotion()) return nowMs >= dealMs ? 1 : 0;
		double t = (nowMs - dealMs) / DEAL_MS;
		return (float) Math.max(0, Math.min(1, t));
	}

	/**
	 * Slide progress of deal index {@code key} on {@code anchors} (SY-SPEC §3: waits in the shoe until first seen; a late
	 * card still flies, ≥ 120 ms); 0/1 with reduced motion.
	 */
	public static float deal(DealAnchors anchors, long key, double nowMs, double dealMs) {
		if (FxSettings.reduceMotion()) return nowMs >= dealMs ? 1 : 0;
		return anchors.u(key, nowMs, dealMs, DEAL_MS);
	}

	/** outCubic. */
	public static float ease(float u) {
		float k = 1 - u;
		return 1 - k * k * k;
	}

	/** Flip progress of a card first seen face up at local time {@code seenMs} (0 = no flip). */
	public static float flip(long seenMs) {
		if (seenMs <= 0 || FxSettings.reduceMotion()) return 1;
		return Math.max(0, Math.min(1, (Util.getMillis() - seenMs) / (float) FLIP_MS));
	}

	/** Gold glow around the acting hand. */
	public static void glow(GuiGraphicsExtractor g, Box b, int argb) {
		g.fill(b.x() - 2, b.y() - 2, b.right() + 2, b.y() - 1, argb);
		g.fill(b.x() - 2, b.bottom() + 1, b.right() + 2, b.bottom() + 2, argb);
		g.fill(b.x() - 2, b.y() - 1, b.x() - 1, b.bottom() + 1, argb);
		g.fill(b.right() + 1, b.y() - 1, b.right() + 2, b.bottom() + 1, argb);
	}
}
