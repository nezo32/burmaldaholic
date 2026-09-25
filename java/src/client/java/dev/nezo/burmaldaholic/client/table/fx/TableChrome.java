package dev.nezo.burmaldaholic.client.table.fx;

import dev.nezo.burmaldaholic.core.anim.dice.ChipStacks;
import dev.nezo.burmaldaholic.core.text.Texts;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The shared frame of the table screens (docs/design/visual/tables.md §6.1): the themed room backdrop, the felt tile
 * framed by the padded rail, the top bar (title plaque, seat plates, balance plaque) and the bottom bar texts, the bet
 * timer ring, banners and chip stacks. Coordinates are frame-relative ({@code ox, oy} = the frame's top-left); the
 * frame is 427 × 240 ({@link #FULL}) or 284 × 160 ({@link #COMPACT}) and never scales.
 */
public final class TableChrome {
	public static final int GOLD = 0xFFFFD640;
	public static final int BONE = 0xFFF4ECF8;
	public static final int INK = 0xFF180A28;
	public static final int MUTED = 0xFFB8A8C8;
	public static final int RED = 0xFFFF6E6A;
	public static final int GREEN = 0xFF80FF40;
	/** Seat tints 1–8 (animation/tables.md §0.4). */
	public static final int[] SEATS = {0xFF4FC3F7, 0xFFFFB74D, 0xFFBA68C8, 0xFF81C784, 0xFFF06292, 0xFFFFF176, 0xFFA1887F, 0xFF90A4AE};

	/** Frame geometry (frame-relative). */
	public record Frame(int w, int h, int railX, int railY, int railW, int railH, int feltX, int feltY, int feltW, int feltH, int barY,
			boolean compact) {}

	public static final Frame FULL = new Frame(427, 240, 4, 21, 419, 184, 14, 31, 399, 164, 206, false);
	public static final Frame COMPACT = new Frame(284, 160, 2, 13, 280, 124, 12, 23, 260, 104, 136, true);

	/** A seat plate on the top bar. {@code kind}: other, you, active, bot. */
	public record Seat(Component name, String kind, int tint) {}

	private TableChrome() {}

	/** Client GameTests / previews: the compact frame at any screen size (screenshots of the compact layout). */
	public static boolean forceCompact;

	/** The frame that fits the screen (compact below 427 × 240). */
	public static Frame frameFor(int screenW, int screenH) {
		return !forceCompact && screenW >= FULL.w() && screenH >= FULL.h() ? FULL : COMPACT;
	}

	/** Seat tint of seat index {@code i} (0-based). */
	public static int seatTint(int i) {
		return SEATS[Math.floorMod(i, SEATS.length)];
	}

	// ---- backdrop, felt, rail -----------------------------------------------------------------------------------------

	/**
	 * The 640 × 360 room backdrop centred on the screen; beyond it the edge columns / rows repeat (calm wall), so any
	 * GUI size is filled without scaling the art.
	 */
	public static void backdrop(GuiGraphicsExtractor g, TableTheme t, int screenW, int screenH) {
		var tex = TableGfx.sheet("core/backdrop_" + t.id);
		int bx = screenW / 2 - 320;
		int by = screenH / 2 - 180;
		int x0 = Math.max(0, bx);
		int y0 = Math.max(0, by);
		int x1 = Math.min(screenW, bx + 640);
		int y1 = Math.min(screenH, by + 360);
		TableGfx.region(g, tex, 640, 360, x0 - bx, y0 - by, x1 - x0, y1 - y0, x0, y0, 0xFFFFFFFF);
		if (bx > 0 || by > 0 || x1 < screenW || y1 < screenH) {
			// stretch the outermost pixel rows / columns (flat wall) over the rest of the screen
			if (x0 > 0) {
				g.blit(tex, 0, y0, x0, y1, 0.5f / 640, 1.5f / 640, (y0 - by) / 360f, (y1 - by) / 360f);
			}
			if (x1 < screenW) {
				g.blit(tex, x1, y0, screenW, y1, 638.5f / 640, 639.5f / 640, (y0 - by) / 360f, (y1 - by) / 360f);
			}
			if (y0 > 0) {
				g.blit(tex, 0, 0, screenW, y0, 0, 1, 0.5f / 360, 1.5f / 360);
			}
			if (y1 < screenH) {
				g.blit(tex, 0, y1, screenW, screenH, 0, 1, 358.5f / 360, 359.5f / 360);
			}
		}
	}

	/** Felt tile + rail nine-slice. */
	public static void table(GuiGraphicsExtractor g, TableTheme t, Frame f, int ox, int oy) {
		TableGfx.tile(g, TableGfx.sheet("core/felt_" + t.id), 64, 64, ox + f.feltX(), oy + f.feltY(), f.feltW(), f.feltH());
		TableGfx.blit(g, "core/rail_" + t.id, ox + f.railX(), oy + f.railY(), f.railW(), f.railH());
	}

	// ---- top bar ------------------------------------------------------------------------------------------------------

	/** Title plaque at the top-left; returns the x right of it. */
	public static int title(GuiGraphicsExtractor g, Font font, TableTheme t, Frame f, int ox, int oy, Component title, int maxW) {
		FormattedCharSequence seq = fit(font, title, maxW - 12);
		int w = font.width(seq) + 12;
		int h = f.compact() ? 12 : 15;
		int y = f.compact() ? 1 : 3;
		int x = f.compact() ? 2 : 4;
		TableGfx.blit(g, "core/plate_" + t.id, ox + x, oy + y, w, h);
		g.text(font, seq, ox + x + 6, oy + y + (h - 8) / 2 + (f.compact() ? 1 : 1), BONE, true);
		return x + w;
	}

	/** Seat plates from {@code x}; returns the x right of the last one. */
	public static int seats(GuiGraphicsExtractor g, Font font, TableTheme t, int ox, int oy, int x, List<Seat> seats, int maxX) {
		for (Seat s : seats) {
			int w = font.width(s.name()) + 20;
			if (x + w > maxX) {
				break;
			}
			TableGfx.blit(g, "core/seat_" + s.kind() + "_" + t.id, ox + x, oy + 3, w, 15);
			if ("bot".equals(s.kind())) {
				TableGfx.blit(g, "core/bot_badge", ox + x + 2, oy + 5, 11, 10);
			} else {
				TableGfx.blit(g, "core/seat_stripe", ox + x + 3, oy + 5, 9, 10, s.tint());
			}
			g.text(font, s.name(), ox + x + 16, oy + 7, "active".equals(s.kind()) ? GOLD : BONE, true);
			x += w + 4;
		}
		return x;
	}

	/** Gold balance plaque at the top-right; returns its x (frame-relative). */
	public static int balance(GuiGraphicsExtractor g, Font font, TableTheme t, Frame f, int ox, int oy, long balance) {
		Component text = Texts.number(balance);
		int bw = font.width(text) + 26;
		int h = f.compact() ? 12 : 15;
		int y = f.compact() ? 1 : 3;
		int x = f.w() - (f.compact() ? 2 : 4) - bw;
		TableGfx.blit(g, "core/plate_gold_" + t.id, ox + x, oy + y, bw, h);
		TableGfx.blit(g, "core/stack/chip_500", ox + x + 4, oy + y + (h - 11) / 2, 12, 11);
		g.text(font, text, ox + x + 19, oy + y + (h - 8) / 2 + 1, GOLD, true);
		return x;
	}

	/** Width of the balance plaque (to place the icon buttons left of it). */
	public static int balanceWidth(Font font, long balance) {
		return font.width(Texts.number(balance)) + 26;
	}

	// ---- bottom bar ---------------------------------------------------------------------------------------------------

	/** The phase line (gold) and the summary line (bone) centred on {@code cx}. */
	public static void status(GuiGraphicsExtractor g, Font font, int cx, int y, int maxW, Component phase, Component sub, int phaseColor) {
		if (phase != null) {
			FormattedCharSequence p = fit(font, phase, maxW);
			g.text(font, p, cx - font.width(p) / 2, y, phaseColor, true);
		}
		if (sub != null) {
			FormattedCharSequence s = fit(font, sub, maxW);
			g.text(font, s, cx - font.width(s) / 2, y + 11, BONE, true);
		}
	}

	/** The bet timer ring: {@code left} of {@code total} seconds (12 → 0 segments), urgent for the last 3 s. */
	public static void timer(GuiGraphicsExtractor g, int x, int y, double leftSec, double totalSec, boolean pulse) {
		if (leftSec < 0 || totalSec <= 0) {
			return;
		}
		int seg = (int) Math.ceil(12 * Math.min(1, leftSec / totalSec));
		boolean urgent = leftSec <= 3;
		int frame = 12 - Math.max(0, Math.min(12, seg));
		int off = 0;
		if (pulse && leftSec <= 5 && leftSec > 0) {
			double ph = leftSec - Math.floor(leftSec);
			off = ph > 0.85 ? 1 : 0;
		}
		TableGfx.frame(g, urgent ? "core/timer_urgent" : "core/timer", 16, 16, 13, frame, x - off, y - off, 0xFFFFFFFF);
	}

	/** A result / phase banner (nine-slice {@code banner_<theme|win|lose>}) with one or two centred lines. */
	public static void banner(GuiGraphicsExtractor g, Font font, String sprite, int x, int y, int w, int h, Component line1, int c1, Component line2,
			int c2, double alpha) {
		TableGfx.blit(g, "core/" + sprite, x, y, w, h, TableGfx.fade(alpha));
		if (alpha < 0.5) {
			return;
		}
		int cx = x + w / 2;
		if (line2 == null) {
			FormattedCharSequence a = fit(font, line1, w - 16);
			g.text(font, a, cx - font.width(a) / 2, y + (h - 8) / 2, c1, true);
			return;
		}
		FormattedCharSequence a = fit(font, line1, w - 16);
		FormattedCharSequence b = fit(font, line2, w - 16);
		g.text(font, a, cx - font.width(a) / 2, y + (h - 18) / 2, c1, true);
		g.text(font, b, cx - font.width(b) / 2, y + (h - 18) / 2 + 10, c2, true);
	}

	/** Small plaque label ({@code +1 750} over a winning stack), centred on {@code cx}. */
	public static void label(GuiGraphicsExtractor g, Font font, TableTheme t, Component text, int cx, int y, double alpha) {
		int w = font.width(text) + 8;
		TableGfx.blit(g, "core/plate_" + t.id, cx - w / 2, y, w, 13, TableGfx.fade(alpha));
		if (alpha >= 0.5) {
			g.text(font, text, cx - font.width(text) / 2, y + 3, GOLD, true);
		}
	}

	/** Truncation-free fit: the text as-is when it fits, else cut with "…" (RU fallbacks stay readable in tooltips). */
	public static FormattedCharSequence fit(Font font, Component text, int maxW) {
		if (font.width(text) <= maxW) {
			return text.getVisualOrderText();
		}
		// the cut text AND the ellipsis (a bare cut read as a typo: "Европейская рулет")
		return net.minecraft.locale.Language.getInstance().getVisualOrder(net.minecraft.network.chat.FormattedText.composite(
			font.substrByWidth(text, Math.max(8, maxW - font.width("…"))), net.minecraft.network.chat.FormattedText.of("…"))); // literal-ok: ellipsis
	}

	// ---- chips ----------------------------------------------------------------------------------------------------------

	private static final int[] DISCS = new int[ChipStacks.MAX_DISCS];

	/**
	 * A chip stack whose base point is (x, y) (discs drawn 3 px apart upwards). {@code tint} ≠ 0 draws grey chips in a
	 * seat colour (other players); {@code argb} fades the whole stack.
	 */
	public static void stack(GuiGraphicsExtractor g, long amount, int x, int y, int tint, int argb) {
		int n = ChipStacks.discs(amount, DISCS);
		for (int k = 0; k < n; k++) {
			String sprite = tint != 0 ? "core/stack/chip_grey" : "core/stack/chip_" + DISCS[k];
			int c = tint != 0 ? mul(tint, argb) : argb;
			TableGfx.blit(g, sprite, x - 6, y - 8 - ChipStacks.DISC_STEP * k, 12, 11, c);
		}
	}

	/**
	 * The exact value of a stack (tables.md §0.4: the disc count is visual, the number is the server's amount), in a
	 * small half-scale tag just above the top disc. Needs ≥ 2 real pixels per GUI pixel to stay legible, so it is
	 * skipped at GUI scale 1 (the tooltips and the status line still carry the amounts).
	 */
	public static void stackValue(GuiGraphicsExtractor g, Font font, long amount, int x, int y, int argb) {
		if (amount <= 0 || net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale() < 2) {
			return;
		}
		int a = argb >>> 24;
		if (a < 0x40) {
			return;
		}
		Component n = Texts.number(amount);
		int w = font.width(n);
		int top = y - stackHeight(amount) - 5;
		g.pose().pushMatrix();
		g.pose().translate(x, top);
		g.pose().scale(0.5f, 0.5f);
		g.fill(-w / 2 - 2, -1, w / 2 + 2 + (w & 1), 8, (Math.min(a, 0xC0) << 24) | 0x180A28);
		g.text(font, n, -w / 2, 0, (a << 24) | (GOLD & 0xFFFFFF), false);
		g.pose().popMatrix();
	}

	/** Height of a stack in px above its base point (label placement). */
	public static int stackHeight(long amount) {
		int n = ChipStacks.discs(amount, DISCS);
		return 8 + ChipStacks.DISC_STEP * Math.max(0, n - 1);
	}

	/** One disc of denomination {@code denom} (flights, payout discs, the ghost chip). */
	public static void disc(GuiGraphicsExtractor g, int denom, int x, int y, int argb) {
		TableGfx.blit(g, "core/stack/chip_" + denom, x - 6, y - 8, 12, 11, argb);
	}

	/** Colour multiply (tint × fade). */
	public static int mul(int a, int b) {
		int aa = ((a >>> 24) * (b >>> 24)) / 255;
		int r = (((a >> 16) & 0xFF) * ((b >> 16) & 0xFF)) / 255;
		int gg = (((a >> 8) & 0xFF) * ((b >> 8) & 0xFF)) / 255;
		int bb = ((a & 0xFF) * (b & 0xFF)) / 255;
		return (aa << 24) | (r << 16) | (gg << 8) | bb;
	}
}
