package dev.nezo.burmaldaholic.games.roulette.client;

import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.client.fx.FxText;
import dev.nezo.burmaldaholic.client.table.fx.TableChrome;
import dev.nezo.burmaldaholic.client.table.fx.TableGfx;
import dev.nezo.burmaldaholic.client.table.fx.TableTheme;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.roulette.logic.RouletteBallPath;
import dev.nezo.burmaldaholic.games.roulette.logic.Wheel;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * The pre-rendered roulette wheel (docs/design/visual/tables.md §3.1; animation/tables.md §1.4): the static bowl, one
 * of 74 head frames (upright baked pocket digits; a tangential blur frame above 180°/s), the ball on the shared
 * {@link RouletteBallPath} with its trail, the deflector spark, the pocket glow and the number badge once the ball has
 * settled (u ≥ 0.90; nothing earlier names the result). Also the idle mini wheel of the betting view and the compact
 * spin view (37 pocket-step frames). Draw-only; every value comes from the path at the given time.
 */
final class RouletteWheelView {
	static final int HEAD_FRAMES = 74;
	static final int HEAD_COLS = 10;
	static final int MINI_FRAMES = 37;
	/** Ball-path unit Rw on the big wheel (px) and on the compact wheel. */
	static final double RW_BIG = 92;
	static final double RW_MINI = 31.4;
	private static final Identifier HEAD = TableGfx.sheet("roulette/wheel_head");
	private static final Identifier HEAD_BLUR = TableGfx.sheet("roulette/wheel_head_blur");
	private static final Identifier SHADOW = TableGfx.sheet("roulette/wheel_shadow");
	private static final Identifier MINI_HEAD = TableGfx.sheet("roulette/wheel_mini_head");

	private RouletteWheelView() {}

	/** Pocket colours for the result badge. */
	static int pocketColor(int n) {
		return switch (Wheel.color(n)) {
			case RED -> 0xFFD83440;
			case BLACK -> 0xFF26202C;
			case GREEN -> 0xFF2E9A48;
		};
	}

	/** The idle mini wheel (72²): frame k has pocket WHEEL_ORDER[k] under the ball at the top; -1 = pocket 0 frame. */
	static void mini(GuiGraphicsExtractor g, TableTheme t, int x, int y, int lastResult) {
		int k = lastResult >= 0 ? Wheel.wheelIndex(lastResult) : 0;
		TableGfx.region(g, TableGfx.sheet("roulette/wheel_mini_" + t.id), 720, 288, (k % 10) * 72, (k / 10) * 72, 72, 72, x, y, 0xFFFFFFFF);
	}

	/**
	 * Head angle at rest before a spin: the last result's pocket at the top (as the idle mini wheel shows it), so the
	 * wheel continues from where it stopped. The spin adds its own turn to this base ({@link #headAngle}).
	 */
	static double baseAngle(int lastResult) {
		return lastResult >= 0 ? -RouletteBallPath.pocketAngle(lastResult) : 0;
	}

	/** Absolute head angle of a spin started from {@code base}. */
	static double headAngle(RouletteBallPath path, double tMs, double base, boolean reduced) {
		return base + (reduced ? path.headReduced(tMs) : path.head(tMs)) - path.h0;
	}

	/**
	 * The big spin view centred on (cx, cy): {@code tMs} since the spin started (≥ spin length = settled), {@code alpha}
	 * and {@code dy} for the rise / sink. {@code path} null = the wheel at rest on {@code lastResult} (NO MORE BETS: the
	 * number is not drawn yet).
	 */
	static void big(GuiGraphicsExtractor g, Font font, TableTheme t, @org.jspecify.annotations.Nullable RouletteBallPath path, double tMs, int cx, int cy,
			double alpha, int dy, int lastResult) {
		boolean reduced = FxSettings.reduceMotion();
		int fade = TableGfx.fade(alpha);
		cy += dy;
		TableGfx.region(g, SHADOW, 216, 216, 0, 0, 216, 216, cx - 110, cy - 112, TableGfx.fade(alpha * 0.9));
		TableGfx.region(g, TableGfx.sheet("roulette/wheel_bowl_" + t.id), 208, 208, 0, 0, 208, 208, cx - 104, cy - 104, fade);
		double base = baseAngle(lastResult);
		if (path == null) {
			int f0 = RouletteBallPath.headFrame(base, HEAD_FRAMES);
			TableGfx.region(g, HEAD, 1520, 1216, (f0 % HEAD_COLS) * 152, (f0 / HEAD_COLS) * 152, 152, 152, cx - 76, cy - 76, fade);
			if (lastResult >= 0 && alpha >= 0.4) {
				double fa = Math.toRadians(RouletteBallPath.frameAngle(f0, HEAD_FRAMES) - base);
				drawBall(g, cx + 55.5 * Math.sin(fa), cy - 55.5 * Math.cos(fa), 2, 1, true);
			}
			return;
		}
		double head = headAngle(path, tMs, base, reduced);
		int f = RouletteBallPath.headFrame(head, HEAD_FRAMES);
		double frameAngle = RouletteBallPath.frameAngle(f, HEAD_FRAMES);
		boolean blur = !reduced && path.headSpeed(tMs) > 180;
		if (blur) {
			int b = (int) ((Util.getMillis() / 100) % 4);
			TableGfx.region(g, HEAD_BLUR, 152, 608, 0, b * 152, 152, 152, cx - 76, cy - 76, fade);
		} else {
			TableGfx.region(g, HEAD, 1520, 1216, (f % HEAD_COLS) * 152, (f / HEAD_COLS) * 152, 152, 152, cx - 76, cy - 76, fade);
		}
		if (alpha < 0.4) {
			return;
		}
		ball(g, path, tMs, cx, cy, RW_BIG, head, frameAngle, reduced, true);
		if (path.settled(tMs)) {
			double pa = Math.toRadians(frameAngle + RouletteBallPath.pocketAngle(path.result));
			int px = (int) Math.round(cx + 55.5 * Math.sin(pa));
			int py = (int) Math.round(cy - 55.5 * Math.cos(pa));
			if (FxSettings.flashes()) {
				TableGfx.blit(g, "roulette/pocket_glow", px - 10, py - 10, 20, 20);
			} else {
				TableGfx.frame(g, "roulette/pocket_glow", 20, 20, 4, 0, px - 10, py - 10, 0xFFFFFFFF);
			}
			badge(g, font, path.result, cx, cy, tMs - RouletteBallPath.U_SETTLED * path.spinMs);
		}
	}

	/** Compact spin view (72² bowl + 56² head, 37 pocket-step frames) centred on (cx, cy). */
	static void compact(GuiGraphicsExtractor g, Font font, TableTheme t, RouletteBallPath path, double tMs, int cx, int cy, double alpha, int lastResult) {
		boolean reduced = FxSettings.reduceMotion();
		int fade = TableGfx.fade(alpha);
		TableGfx.region(g, TableGfx.sheet("roulette/wheel_mini_bowl_" + t.id), 72, 72, 0, 0, 72, 72, cx - 36, cy - 36, fade);
		double head = headAngle(path, tMs, baseAngle(lastResult), reduced);
		int f = RouletteBallPath.headFrame(head, MINI_FRAMES);
		double frameAngle = RouletteBallPath.frameAngle(f, MINI_FRAMES);
		TableGfx.region(g, MINI_HEAD, 560, 224, (f % 10) * 56, (f / 10) * 56, 56, 56, cx - 28, cy - 28, fade);
		if (alpha < 0.4) {
			return;
		}
		ball(g, path, tMs, cx, cy, RW_MINI, head, frameAngle, reduced, false);
	}

	private static void ball(GuiGraphicsExtractor g, RouletteBallPath path, double tMs, int cx, int cy, double rw, double head, double frameAngle,
			boolean reduced, boolean big) {
		double u = tMs / path.spinMs;
		boolean settled = path.settled(tMs);
		double ballAlpha = 1;
		if (reduced) {
			if (!settled) {
				return; // reduced motion: the ball is hidden, then fades into the result pocket (§0.5)
			}
			ballAlpha = Math.min(1, (tMs - RouletteBallPath.U_SETTLED * path.spinMs) / 300.0);
		}
		// from the hops on, the ball rides the DRAWN head frame so it rests exactly in the drawn pocket
		double k = Math.max(0, Math.min(1, (u - RouletteBallPath.U_HOP2) / (RouletteBallPath.U_SETTLE - RouletteBallPath.U_HOP2)));
		double base = head + wrap(frameAngle - head) * k;
		double rel = path.rel(tMs);
		double angle = base + rel;
		double r = path.radius(tMs) * rw;
		int frame = settled ? 2 : Math.abs(u - path.hop1End()) < 0.012 || Math.abs(u - RouletteBallPath.U_HOP1) < 0.012 ? 1 : 0;
		if (big && !reduced && path.ballSpeed(tMs) > 360) {
			double[] trail = {0.6, 0.4, 0.2};
			for (int i = 0; i < 3; i++) {
				double a = Math.toRadians(angle + 7 * (i + 1));
				drawBall(g, cx + r * Math.sin(a), cy - r * Math.cos(a), 0, trail[i], big);
			}
		}
		double a = Math.toRadians(angle);
		drawBall(g, cx + r * Math.sin(a), cy - r * Math.cos(a), frame, ballAlpha, big);
		// deflector kick: a spark at the diamond for 80 ms
		double kick = Math.abs(u - RouletteBallPath.U_KICK) * path.spinMs;
		if (big && !reduced && kick < 80) {
			int sf = (int) Math.min(3, kick / 20);
			double sa = Math.toRadians(Math.round((angle) / 45.0) * 45.0 + 22.5);
			int sx = (int) Math.round(cx + 84 * Math.sin(sa));
			int sy = (int) Math.round(cy - 84 * Math.cos(sa));
			TableGfx.frame(g, "core/spark", 7, 7, 4, sf, sx - 3, sy - 3, FxSettings.flashes() ? 0xFFFFFFFF : 0xB0FFFFFF);
		}
	}

	private static double wrap(double d) {
		double x = ((d + 180) % 360 + 360) % 360 - 180;
		return x;
	}

	private static void drawBall(GuiGraphicsExtractor g, double x, double y, int frame, double alpha, boolean big) {
		int ix = (int) Math.round(x);
		int iy = (int) Math.round(y);
		if (big) {
			TableGfx.frame(g, "roulette/ball", 7, 7, 3, frame, ix - 3, iy - 3, TableGfx.fade(alpha));
		} else {
			g.fill(ix - 1, iy - 1, ix + 1, iy + 1, TableGfx.alpha(0xFFF4ECE0, alpha));
		}
	}

	/** The result badge over the turret: the number in a disc of the pocket colour with its shape marker. */
	private static void badge(GuiGraphicsExtractor g, Font font, int n, int cx, int cy, double sinceSettle) {
		double k = FxSettings.reduceMotion() ? 1 : Math.min(1, Math.max(0, sinceSettle / 250.0));
		if (k <= 0) {
			return;
		}
		double pop = dev.nezo.burmaldaholic.core.anim.Ease.OUT_BACK.apply(k);
		int r = (int) Math.round(13 * pop);
		if (r < 2) {
			return;
		}
		TableGfx.disc(g, cx, cy, r + 1, TableChrome.INK);
		TableGfx.disc(g, cx, cy, r, pocketColor(n));
		TableGfx.disc(g, cx, cy, Math.max(1, r - 2), TableGfx.lerp(pocketColor(n), 0xFFFFFFFF, 0.12));
		if (k >= 1) {
			FxText.outlinedCentered(g, font, Texts.number(n), cx, cy - 8, 2f, TableChrome.BONE, TableChrome.INK);
		}
	}
}
