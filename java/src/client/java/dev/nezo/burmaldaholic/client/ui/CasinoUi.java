package dev.nezo.burmaldaholic.client.ui;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.fx.FxSprites;
import dev.nezo.burmaldaholic.core.text.Numbers;
import dev.nezo.burmaldaholic.core.ui.UiLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/**
 * Drawing helpers of the casino UI kit (lane J-L2): panels, plates, rows, bars, icons, backdrops and frames. Every
 * sprite draw degrades to a flat fill in the palette when the art is missing (global.md §1.5), and every position is an
 * integer. Stateless; call from {@code extract*} methods.
 */
public final class CasinoUi {
	private CasinoUi() {}

	// ---- primitives ------------------------------------------------------------------------------------------------

	/** An atlas sprite (nine-slice / tile / animation from its mcmeta), tinted {@code argb}; false when missing. */
	public static boolean sprite(GuiGraphicsExtractor g, Identifier id, int x, int y, int w, int h, int argb) {
		if (w <= 0 || h <= 0 || (argb >>> 24) == 0) return true;
		return FxSprites.blit(g, id, x, y, w, h, argb);
	}

	public static boolean sprite(GuiGraphicsExtractor g, Identifier id, int x, int y, int w, int h) {
		return sprite(g, id, x, y, w, h, 0xFFFFFFFF);
	}

	/** A sprite, or {@code fallbackFill} with a 1 px {@code fallbackRim} when the art is missing. */
	public static void sprite(GuiGraphicsExtractor g, Identifier id, int x, int y, int w, int h, int fallbackFill, int fallbackRim) {
		if (sprite(g, id, x, y, w, h)) return;
		g.fill(x, y, x + w, y + h, fallbackFill);
		if ((fallbackRim >>> 24) != 0) g.outline(x, y, w, h, fallbackRim);
	}

	/** Region {@code (u, v, uw, vh)} of a sheet drawn 1:1 at (x, y). */
	public static void sheet(GuiGraphicsExtractor g, UiSprites.Sheet s, int u, int v, int uw, int vh, int x, int y) {
		sheet(g, s, u, v, uw, vh, x, y, uw, vh);
	}

	/** Region of a sheet stretched into (x, y, w, h) (use whole multiples only: pixel art). */
	public static void sheet(GuiGraphicsExtractor g, UiSprites.Sheet s, int u, int v, int uw, int vh, int x, int y, int w, int h) {
		if (w <= 0 || h <= 0) return;
		g.blit(s.id(), x, y, x + w, y + h, u / (float) s.w(), (u + uw) / (float) s.w(), v / (float) s.h(), (v + vh) / (float) s.h());
	}

	// ---- scenes ----------------------------------------------------------------------------------------------------

	/** The theme's backdrop cropped at the centre to fill {@code r} (never scaled). */
	public static void backdrop(GuiGraphicsExtractor g, CasinoTheme theme, UiLayout.Rect r) {
		int w = Math.min(r.w(), theme.backdropW);
		int h = Math.min(r.h(), theme.backdropH);
		int u = (theme.backdropW - w) / 2;
		int v = (theme.backdropH - h) / 2;
		int x = r.x() + (r.w() - w) / 2;
		int y = r.y() + (r.h() - h) / 2;
		if (w < r.w() || h < r.h()) g.fill(r.x(), r.y(), r.right(), r.bottom(), CasinoPalette.BG_DARKEST);
		sheet(g, new UiSprites.Sheet(theme.backdrop, theme.backdropW, theme.backdropH), u, v, w, h, x, y);
	}

	/** The theme's gold nine-slice frame around {@code r} (transparent centre). */
	public static void frame(GuiGraphicsExtractor g, CasinoTheme theme, UiLayout.Rect r) {
		if (sprite(g, theme.frame, r.x(), r.y(), r.w(), r.h())) return;
		g.outline(r.x(), r.y(), r.w(), r.h(), CasinoPalette.INK);
		g.outline(r.x() + 1, r.y() + 1, r.w() - 2, r.h() - 2, CasinoPalette.GOLD);
		g.outline(r.x() + 2, r.y() + 2, r.w() - 4, r.h() - 4, CasinoPalette.GOLD_SHADE);
	}

	/**
	 * The theme's title banner centred on {@code cx} with its top at {@code y}: {@code max(110, textWidth + 32)} × 22
	 * (extras.md §2.2), title in {@code gold}. Returns the banner rectangle.
	 */
	public static UiLayout.Rect banner(GuiGraphicsExtractor g, Font font, CasinoTheme theme, Component title, int cx, int y, int maxW) {
		FormattedCharSequence seq = fit(font, title, maxW - 24);
		int w = UiLayout.bannerWidth(font.width(seq), maxW);
		int x = cx - w / 2;
		sprite(g, theme.banner, x, y, w, 22, CasinoPalette.BG_DEEP, CasinoPalette.GOLD);
		g.text(font, seq, cx - font.width(seq) / 2, y + 7, CasinoPalette.GOLD, true);
		return new UiLayout.Rect(x, y, w, 22);
	}

	// ---- panels and plates -----------------------------------------------------------------------------------------

	/** {@code panel/casino}: the purple casino card (menus, overlays). */
	public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		sprite(g, UiSprites.PANEL_CASINO, x, y, w, h, CasinoPalette.BG_DEEP, CasinoPalette.FRAME);
	}

	/** {@code panel/inset}: a dark well (text on dark, amounts, history strips). */
	public static void inset(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		sprite(g, UiSprites.PANEL_INSET, x, y, w, h, 0xCC140822, CasinoPalette.FRAME);
	}

	/** A blank plate ({@code gold}: the gold-rimmed variant for balances and prizes). */
	public static void plate(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean gold) {
		sprite(g, gold ? UiSprites.PLATE_GOLD : UiSprites.PLATE, x, y, w, h, CasinoPalette.BG_DARKEST, gold ? CasinoPalette.GOLD : CasinoPalette.FRAME);
	}

	/** The brass nameplate (menu header, 20 high) with {@code title} centred in {@code bone}. */
	public static void header(GuiGraphicsExtractor g, Font font, Component title, int x, int y, int w) {
		sprite(g, UiSprites.HEADER, x, y, w, 20, CasinoPalette.BG_DEEP, CasinoPalette.GOLD);
		FormattedCharSequence seq = fit(font, title, w - 16);
		g.text(font, seq, x + (w - font.width(seq)) / 2, y + 6, CasinoPalette.BONE, true);
	}

	/**
	 * The gold-framed balance plaque (20 high): the animated chip icon on the left and {@code amount} right-aligned in
	 * {@code gold} (the caller passes the ticker value).
	 */
	public static void balancePlaque(GuiGraphicsExtractor g, Font font, long amount, int x, int y, int w, int color) {
		sprite(g, UiSprites.BALANCE, x, y, w, 20, CasinoPalette.BG_DARKEST, CasinoPalette.GOLD);
		chipIcon(g, x + 5, y + 4);
		String s = Numbers.format(amount);
		g.text(font, s, x + w - 6 - font.width(s), y + 6, color, true);
	}

	/** The 12² animated chip icon (falls back to a red disc). */
	public static void chipIcon(GuiGraphicsExtractor g, int x, int y) {
		if (sprite(g, UiSprites.CHIP_ICON, x, y, 12, 12)) return;
		g.fill(x + 2, y + 1, x + 10, y + 11, CasinoPalette.CHIP_RED);
		g.fill(x + 1, y + 2, x + 11, y + 10, CasinoPalette.CHIP_RED);
		g.fill(x + 4, y + 4, x + 8, y + 8, CasinoPalette.BONE);
	}

	/** A ledger row: 0 = normal, 1 = alternate shading, 2 = highlight (gold side rules), 3 = the Loan Shark's steel. */
	public static void row(GuiGraphicsExtractor g, int kind, int x, int y, int w, int h) {
		Identifier id = switch (kind) {
			case 1 -> UiSprites.ROW_ALT;
			case 2 -> UiSprites.ROW_HIGHLIGHT;
			case 3 -> UiSprites.ROW_LOAN;
			default -> UiSprites.ROW;
		};
		if (!sprite(g, id, x, y, w, h)) g.fill(x, y, x + w, y + h, kind == 1 ? 0x40FFFFFF : 0x20FFFFFF);
	}

	/**
	 * A progress bar: the {@code menu/progress} frame (h ≥ 8) and a tiled fill inset 2 px, {@code fraction} of the
	 * inner width (never 0 px for a positive fraction).
	 */
	public static void progress(GuiGraphicsExtractor g, int x, int y, int w, int h, double fraction, UiSprites.Fill fill) {
		sprite(g, UiSprites.PROGRESS, x, y, w, h, 0xFF140822, CasinoPalette.FRAME);
		int innerW = w - 4;
		int px = UiLayout.fillPixels(innerW, fraction);
		if (px <= 0) return;
		if (!sprite(g, fill.id, x + 2, y + 2, px, h - 4)) {
			int c = switch (fill) {
				case GOLD -> CasinoPalette.GOLD;
				case GREEN -> CasinoPalette.BONUS;
				case RED -> CasinoPalette.CHIP_RED;
				case LILAC -> CasinoPalette.LILAC;
			};
			g.fill(x + 2, y + 2, x + 2 + px, y + h - 2, c);
		}
	}

	/** A menu / tab icon at 16, 20 or 40 px ({@code alpha}: no tint on sheets, so dim icons are drawn under a veil). */
	public static void tabIcon(GuiGraphicsExtractor g, UiSprites.TabIcon icon, int size, int x, int y) {
		if (icon == null) return;
		UiSprites.Sheet s = size >= 40 ? UiSprites.TAB_ICONS_40 : size >= 20 ? UiSprites.TAB_ICONS_20 : UiSprites.TAB_ICONS_16;
		int cell = s.h();
		sheet(g, s, icon.ordinal() * cell, 0, cell, cell, x, y);
	}

	// ---- text ------------------------------------------------------------------------------------------------------

	/** {@code text} cut with an ellipsis to fit {@code maxW} (RU fallback; fixed boxes should be budgeted to fit). */
	public static FormattedCharSequence fit(Font font, Component text, int maxW) {
		if (font.width(text) <= maxW) return text.getVisualOrderText();
		FormattedText ell = FormattedText.of("…"); // literal-ok: ellipsis
		FormattedText cut = font.substrByWidth(text, Math.max(0, maxW - font.width(ell)));
		return Language.getInstance().getVisualOrder(FormattedText.composite(cut, ell));
	}

	/** Left-aligned text with the vanilla shadow, cut to {@code maxW}. */
	public static void text(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int maxW, int color) {
		g.text(font, fit(font, text, maxW), x, y, color, true);
	}

	/** Right-aligned at {@code right}. */
	public static void textRight(GuiGraphicsExtractor g, Font font, Component text, int right, int y, int color) {
		g.text(font, text, right - font.width(text), y, color, true);
	}

	/** Queues {@code message} on the vanilla narrator when it is active (no-op otherwise). */
	public static void say(Component message) {
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		if (mc != null && mc.getNarrator().isActive()) mc.getNarrator().saySystemQueued(message);
	}

	/** Linear ARGB mix ({@code t} 0 → a, 1 → b). */
	public static int mix(int a, int b, float t) {
		float k = Math.max(0, Math.min(1, t));
		int ca = (int) (((a >>> 24) & 0xFF) + (((b >>> 24) & 0xFF) - ((a >>> 24) & 0xFF)) * k);
		int cr = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * k);
		int cg = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * k);
		int cb = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * k);
		return (ca << 24) | (cr << 16) | (cg << 8) | cb;
	}
}
