package dev.nezo.burmaldaholic.client.table.cards;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.fx.FxSprites;
import dev.nezo.burmaldaholic.core.anim.cards.CardMotion;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * K12 action tags and hand-name bubbles (docs/design/visual/cards.md §8.4): {@code tag/bubble} (nine-slice, 11 px high,
 * width = text + 8) with {@code tag/tail} under its centre, ink text. {@link #timed} slides it up 6 px (180 ms), holds
 * 1.5 s and fades out (300 ms); {@link #draw} shows it steadily (hand names). Also the total badges
 * ({@code badge/total}, {@code badge/total_gold}) with the K10 roll.
 */
public final class ActionTag {
	private ActionTag() {}

	/** Widest tag text (px): longer (RU) text is scaled / trimmed so a tag never outgrows its seat. */
	public static final int MAX_TEXT_W = 92;

	/** A steady bubble centred on {@code cx} with its top at {@code y}. */
	public static void draw(GuiGraphicsExtractor g, Font font, Component text, int cx, int y, double alpha) {
		int tw = CardGfx.fittedWidth(font, text, MAX_TEXT_W);
		int w = tw + 8;
		int a = CardGfx.white(alpha);
		CardGfx.sprite(g, FxSprites.sprite("cards/tag/bubble"), cx - w / 2, y, w, 11, a, CasinoPalette.BONE);
		CardGfx.sprite(g, FxSprites.sprite("cards/tag/tail"), cx - 2, y + 10, 5, 3, a);
		CardGfx.fitted(g, font, text, cx - w / 2 + 4, y + 2, MAX_TEXT_W, CardGfx.alpha(CasinoPalette.INK, alpha), false);
	}

	/** A K12 tag {@code ageMs} after the action; nothing once it has faded. */
	public static void timed(GuiGraphicsExtractor g, Font font, Component text, int cx, int y, long ageMs, boolean reduced) {
		if (ageMs < 0 || ageMs > CardMotion.tagTotalMs()) return;
		double a = CardMotion.tagAlpha(ageMs);
		int dy = reduced ? 0 : (int) Math.round(CardMotion.tagDy(ageMs));
		draw(g, font, text, cx, y + dy, a);
	}

	/** Width of a total badge for {@code text}. */
	public static int badgeWidth(Font font, Component text) {
		return font.width(text) + 5;
	}

	/**
	 * A total badge at (x, y): bone on ink, or ink on gold ({@code gold}: 21, blackjack, a natural, the active hand);
	 * {@code shakeDx} for the bust shake.
	 */
	public static void badge(GuiGraphicsExtractor g, Font font, Component text, int x, int y, boolean gold, double alpha) {
		int w = badgeWidth(font, text);
		CardGfx.sprite(g, FxSprites.sprite(gold ? "cards/badge/total_gold" : "cards/badge/total"), x, y, w, 11, CardGfx.white(alpha),
			gold ? CasinoPalette.GOLD : CasinoPalette.INK);
		CardGfx.text(g, font, text, x + 3, y + 2, CardGfx.alpha(gold ? CasinoPalette.INK : CasinoPalette.BONE, alpha), !gold);
	}

	/** A badge whose text changed {@code ageMs} ago: K10 roll (old digits slide up, the new ones in from below). */
	public static void badgeRoll(GuiGraphicsExtractor g, Font font, Component oldText, Component text, int x, int y, boolean gold, long ageMs,
			boolean reduced) {
		if (reduced || oldText == null || ageMs >= CardMotion.BADGE_MS || ageMs < 0) {
			badge(g, font, text, x, y, gold, 1);
			return;
		}
		double t = ageMs / (double) CardMotion.BADGE_MS;
		int w = Math.max(badgeWidth(font, text), badgeWidth(font, oldText));
		CardGfx.sprite(g, FxSprites.sprite(gold ? "cards/badge/total_gold" : "cards/badge/total"), x, y, w, 11, 0xFFFFFFFF,
			gold ? CasinoPalette.GOLD : CasinoPalette.INK);
		int ink = gold ? CasinoPalette.INK : CasinoPalette.BONE;
		int oy = (int) Math.round(CardMotion.badgeOldDy(t));
		int ny = (int) Math.round(CardMotion.badgeNewDy(t));
		// no scissor (it would ignore the canvas transform): the rolling digits fade instead of being clipped
		CardGfx.text(g, font, oldText, x + 3, y + 2 + oy, CardGfx.alpha(ink, 1 - t), false);
		CardGfx.text(g, font, text, x + 3, y + 2 + ny, CardGfx.alpha(ink, t), false);
	}
}
