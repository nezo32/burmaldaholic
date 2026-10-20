package dev.nezo.burmaldaholic.games.roulette.client;

import dev.nezo.burmaldaholic.client.fx.FxText;
import dev.nezo.burmaldaholic.client.fx.motion.SnapDraw;
import dev.nezo.burmaldaholic.client.table.fx.TableChrome;
import dev.nezo.burmaldaholic.client.table.fx.TableGfx;
import dev.nezo.burmaldaholic.client.table.fx.TableTheme;
import dev.nezo.burmaldaholic.core.anim.Ease;
import dev.nezo.burmaldaholic.core.anim.motion.HeightShadow;
import dev.nezo.burmaldaholic.core.anim.motion.MotionEvent;
import dev.nezo.burmaldaholic.core.anim.motion.PixelSnap;
import dev.nezo.burmaldaholic.core.anim.motion.Streak;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.roulette.logic.RouletteSpin;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * The pre-rendered roulette wheel (ROULETTE-ANIM-SPEC §3; docs/design/visual/tables.md §3.1) drawn from a
 * {@link RouletteSpin}: the static bowl, the rotor (222 head frames per size for the 104 / 144 wheels — 1.62° per frame,
 * frame index shared by every size — and the 208 head rotated by the pose: PHASE2B D8 VRAM budget), the pocket glow, the
 * ball with its height (lift + shadow) and shutter streak, the deflector diamond flash and contact sparks (fired purely
 * from the spin's events, so any frame rate and a late join show the same thing), and the result badge.
 *
 * <p>Layer order (§3.4): shadow, bowl, diamond flash, head, pocket glow, ball shadow, streak, ball, sparks, badge. The
 * ball always rides the DRAWN head (frame angle + its angle relative to the rotor), so ball and pockets never slide
 * against each other. Moving things are snapped to physical pixels ({@link SnapDraw}); statics sit on the art grid.
 */
final class RouletteWheelView {
	/** Head frames per medium size (6 per pocket); frame f = head angle f·360/222. */
	static final int HEAD_FRAMES = 222;
	static final int HEAD_COLS = 20;
	static final int MINI_FRAMES = 37;
	/** Ball-path unit Rw on the 208 wheel (px) and on the 72 mini wheel. */
	static final double RW_BIG = 92;
	static final double RW_MINI = 31.4;
	static final Identifier HEAD_208 = TableGfx.sheet("roulette/wheel_head_208");
	private static final Identifier MINI_HEAD = TableGfx.sheet("roulette/wheel_mini_head");
	private static final int IVORY = 0xFFF4ECE0;

	private RouletteWheelView() {}

	/** Everything one wheel draw needs (one instance per screen / view, reused every frame). */
	static final class Wheel {
		/** Native sprite size: 72 (mini), 104, 144 or 208. */
		int art = 104;
		/** Centre (layout px) and pose scale (layout px per art px; 1 or 2 when native, fractional while sliding). */
		double cx, cy, scale = 1;
		int k = 1;
		double alpha = 1;
		/** Rotor angle (deg, continuous). */
		double rotor;
		boolean ball;
		double ballAlpha = 1;
		/** Ball angle relative to the rotor, radius (fraction of Rw), height (208 art px), frame. */
		double ballRel, ballR = RouletteSpin.R_POCKET, ballZ;
		int ballFrame = RouletteSpin.FRAME_POCKET;
		/** Shutter streak: the spin and its time (null = none) and the last frame's duration. */
		@Nullable RouletteSpin streakSpin;
		double t, frameDt = 16.7;
		/** Contact sparks and the diamond flash from {@code effects}' events at {@link #t} (null = none). */
		@Nullable RouletteSpin effects;
		boolean sparks, flash;
		/** Pocket glow: the pocket number (-1 = none), alpha and pulse phase (ms since rest). */
		int glow = -1;
		double glowAlpha, glowMs;
		/** Result badge: the number (-1 = none) and ms since it may show. */
		int badge = -1;
		double badgeMs;
	}

	/** Head frame nearest to {@code angle} (of {@code frames} per turn). */
	static int headFrame(double angle, int frames) {
		double pitch = 360.0 / frames;
		double a = RouletteSpin.wrap360(angle);
		return (int) Math.floor(a / pitch + 0.5) % frames;
	}

	static double frameAngle(int f, int frames) {
		return f * 360.0 / frames;
	}

	/** The rotor angle a wheel of {@code art} actually draws (its nearest frame; the 208 head turns continuously). */
	static double drawnRotor(int art, double rotor) {
		if (art >= 208) return rotor;
		int frames = art < 100 ? MINI_FRAMES : HEAD_FRAMES;
		int f = headFrame(rotor, frames);
		// keep the drawn angle on the same turn as the continuous one (continuity for streak samples)
		return rotor + wrap180(frameAngle(f, frames) - rotor);
	}

	static double wrap180(double d) {
		double x = ((d + 180) % 360 + 360) % 360 - 180;
		return x;
	}

	/** Radius (208 art px) of the upright pocket numbers on the 208 head. */
	static final double DIGIT_R = 68.6;

	/** Pocket number {@code n} upright, centred on (x, y), at {@code scale}. */
	static void digit(GuiGraphicsExtractor g, int n, double x, double y, double scale, int argb, int k) {
		SnapDraw.frameF(g, "roulette/wheel_digits", 12, 7, 37, n, x - 6 * scale, y - 3.5 * scale, argb, scale, k);
	}

	/** Pocket colours for the result badge. */
	static int pocketColor(int n) {
		return switch (dev.nezo.burmaldaholic.games.roulette.logic.Wheel.color(n)) {
			case RED -> 0xFFD83440;
			case BLACK -> 0xFF26202C;
			case GREEN -> 0xFF2E9A48;
		};
	}

	/** Rw (ball-path unit) in art px of a wheel of {@code art}. */
	static double rw(int art) {
		return art < 100 ? RW_MINI : RW_BIG * art / 208.0;
	}

	/** Screen x of a point at angle {@code a} (deg) and radius {@code r} (fraction of Rw) on wheel {@code w}. */
	static double px(Wheel w, double a, double r) {
		return w.cx + w.scale * r * rw(w.art) * Math.sin(Math.toRadians(a));
	}

	static double py(Wheel w, double a, double r) {
		return w.cy - w.scale * r * rw(w.art) * Math.cos(Math.toRadians(a));
	}

	// ---- drawing ---------------------------------------------------------------------------------------------------------

	static void draw(GuiGraphicsExtractor g, Font font, TableTheme t, Wheel w) {
		int fade = TableGfx.fade(w.alpha);
		if ((fade >>> 24) == 0) return;
		double rot = drawnRotor(w.art, w.rotor);
		// 1–2. shadow and bowl (static: on the art grid, snapped as a whole)
		Identifier sh = shadowTex(w.art);
		if (sh != null) {
			int size = shadowSize(w.art);
			double q = w.art / 208.0;
			// as on the 208 wheel (216 shadow at −110, −112): centred, nudged by (−2, −4)·q
			SnapDraw.regionF(g, sh, size, size, 0, 0, size, size, w.cx - w.scale * (size / 2.0 + 2 * q), w.cy - w.scale * (size / 2.0 + 4 * q),
				TableGfx.fade(w.alpha * 0.9), w.scale, w.k);
		}
		SnapDraw.regionF(g, bowlTex(w.art, t), w.art, w.art, 0, 0, w.art, w.art, w.cx - w.scale * w.art / 2.0, w.cy - w.scale * w.art / 2.0, fade, w.scale,
			w.k);
		// 3. diamond flash under the head's edge (the diamonds are on the bowl)
		if (w.effects != null && w.flash) diamondFlash(g, w);
		// 4. head
		head(g, w, rot, fade);
		if (w.alpha < 0.35) return;
		// 5. pocket glow under the ball
		if (w.glow >= 0 && w.glowAlpha > 0) glow(g, w, rot);
		// 6–8. ball shadow, streak, ball
		if (w.ball && w.ballAlpha > 0) ball(g, w, rot);
		// 9. sparks
		if (w.effects != null && w.sparks) sparks(g, w);
		// 11. badge over the turret
		if (w.badge >= 0) badge(g, font, w);
	}

	private static Identifier bowlTex(int art, TableTheme t) {
		return switch (art) {
			case 72 -> TableGfx.sheet("roulette/wheel_mini_bowl_" + t.id);
			case 104, 144 -> TableGfx.sheet("roulette/wheel_bowl_m" + art + "_" + t.id);
			default -> TableGfx.sheet("roulette/wheel_bowl_" + t.id);
		};
	}

	private static @Nullable Identifier shadowTex(int art) {
		return switch (art) {
			case 104, 144 -> TableGfx.sheet("roulette/wheel_shadow_m" + art);
			case 208 -> TableGfx.sheet("roulette/wheel_shadow");
			default -> null;
		};
	}

	private static int shadowSize(int art) {
		return switch (art) {
			case 104 -> 112;
			case 144 -> 152;
			default -> 216;
		};
	}

	private static void head(GuiGraphicsExtractor g, Wheel w, double rot, int fade) {
		if (w.art >= 208) {
			// D8: one native frame turned by the pose (continuous; no 20 MB frame sheet)
			g.pose().pushMatrix();
			g.pose().translate(PixelSnap.snapF(w.cx, w.k), PixelSnap.snapF(w.cy, w.k));
			g.pose().rotate((float) Math.toRadians(rot));
			g.pose().scale((float) w.scale, (float) w.scale);
			TableGfx.region(g, HEAD_208, 152, 152, 0, 0, 152, 152, -76, -76, fade);
			g.pose().popMatrix();
			// the numbers stay upright (as on the pre-rendered frames), riding the turning number ring
			for (int i = 0; i < 37; i++) {
				int n = dev.nezo.burmaldaholic.games.roulette.logic.Wheel.ORDER.get(i);
				double a = Math.toRadians(rot + i * RouletteSpin.STEP);
				double x = w.cx + w.scale * DIGIT_R * Math.sin(a);
				double y = w.cy - w.scale * DIGIT_R * Math.cos(a);
				digit(g, n, x, y, w.scale, fade, w.k);
			}
			return;
		}
		if (w.art < 100) {
			int f = headFrame(rot, MINI_FRAMES);
			SnapDraw.regionF(g, MINI_HEAD, 560, 224, (f % 10) * 56, (f / 10) * 56, 56, 56, w.cx - 28 * w.scale, w.cy - 28 * w.scale, fade, w.scale, w.k);
			return;
		}
		int hs = w.art == 104 ? 76 : 105;
		int f = headFrame(rot, HEAD_FRAMES);
		Identifier sheet = TableGfx.sheet("roulette/wheel_head_m" + w.art);
		int rows = (HEAD_FRAMES + HEAD_COLS - 1) / HEAD_COLS;
		SnapDraw.regionF(g, sheet, hs * HEAD_COLS, hs * rows, (f % HEAD_COLS) * hs, (f / HEAD_COLS) * hs, hs, hs, w.cx - hs / 2.0 * w.scale,
			w.cy - hs / 2.0 * w.scale, fade, w.scale, w.k);
	}

	private static void glow(GuiGraphicsExtractor g, Wheel w, double rot) {
		double a = rot + RouletteSpin.pocketAngle(w.glow);
		double x = px(w, a, RouletteSpin.R_POCKET);
		double y = py(w, a, RouletteSpin.R_POCKET);
		// 2 Hz × 2 pulses, then steady
		double pulse = w.glowMs < 1000 ? 0.65 + 0.35 * Math.cos(w.glowMs / 1000.0 * 2 * Math.PI * 2) : 1;
		int argb = TableGfx.fade(w.glowAlpha * pulse * w.alpha);
		if (w.art < 100) return;
		String sprite = w.art == 104 ? "roulette/pocket_glow_m104" : w.art == 144 ? "roulette/pocket_glow_m144" : "roulette/pocket_glow";
		int s = w.art == 104 ? 12 : w.art == 144 ? 16 : 20;
		int f = w.glowMs < 1000 ? (int) (w.glowMs / 62.5) % 4 : 2;
		SnapDraw.frameF(g, sprite, s, s, 4, f, x - s / 2.0 * w.scale, y - s / 2.0 * w.scale, argb, w.scale, w.k);
	}

	private static final HeightShadow.Out HS = new HeightShadow.Out();
	private static final Streak STREAK = new Streak();
	private static final double[] TRAIL = new double[16];
	private static final RouletteSpin.Sample TMP = new RouletteSpin.Sample();

	private static void ball(GuiGraphicsExtractor g, Wheel w, double rot) {
		double a = rot + w.ballRel;
		double gx = px(w, a, w.ballR);
		double gy = py(w, a, w.ballR);
		double q = w.art / 208.0 * w.scale;
		boolean mini = w.art < 100;
		double bs = mini ? 1 : Math.max(1, Math.floor(w.scale + 1e-9)); // ball sprite scale: ×2 on the 208×2 focus wheel
		HeightShadow.apply(HeightShadow.ROULETTE, w.ballZ, q, HS);
		if (!mini && HS.shadowAlpha > 0.02) {
			SnapDraw.blitF(g, TableGfx.sprite("roulette/ball_shadow"), gx + HS.shadowDx - 3.5 * bs, gy + HS.shadowDy - 1.5 * bs, 7, 4,
				TableGfx.fade(HS.shadowAlpha * w.ballAlpha * w.alpha / 0.55), bs, w.k);
		}
		// streak: shutter samples of the same pure path (dots 2×2 → 1×1 art px, ivory, α 0.45 → 0.08)
		RouletteSpin sp = w.streakSpin;
		if (sp != null && !mini) {
			Streak.Sampler sampler = (ms, xy) -> {
				sp.sample(ms, TMP);
				double aa = drawnRotor(w.art, TMP.rotor) + TMP.rel;
				xy[0] = px(w, aa, TMP.r);
				xy[1] = py(w, aa, TMP.r);
			};
			int n = STREAK.samples(sampler, w.t, w.frameDt, 1.5 * Math.max(1, w.scale), TRAIL);
			for (int i = 0; i < n; i++) {
				double al = Streak.alpha(i, n, 0.45, 0.08) * w.ballAlpha * w.alpha;
				double size = (i < n / 2 ? 2 : 1) * bs;
				dot(g, TRAIL[2 * i] - size / 2, TRAIL[2 * i + 1] - size / 2, size, TableGfx.alpha(IVORY, al), w.k);
			}
		}
		double bx = gx + HS.dx;
		double by = gy + HS.dy;
		if (mini) {
			// T-C2 V10: an ink ring around the 2 px ball keeps it visible on the ivory / red / black pockets of the mini wheel
			dot(g, bx - 2, by - 2, 4, TableGfx.alpha(TableChrome.INK, 0.85 * w.ballAlpha * w.alpha), w.k);
			dot(g, bx - 1, by - 1, 2, TableGfx.alpha(IVORY, w.ballAlpha * w.alpha), w.k);
			return;
		}
		SnapDraw.frameF(g, "roulette/ball", 7, 7, 3, w.ballFrame, bx - 3.5 * bs, by - 3.5 * bs, TableGfx.fade(w.ballAlpha * w.alpha), bs, w.k);
	}

	/** A filled square at a float position snapped to the physical grid. */
	static void dot(GuiGraphicsExtractor g, double x, double y, double size, int argb, int k) {
		if ((argb >>> 24) == 0) return;
		g.pose().pushMatrix();
		g.pose().translate(PixelSnap.snapF(x, k), PixelSnap.snapF(y, k));
		g.pose().scale((float) size, (float) size);
		g.fill(0, 0, 1, 1, argb);
		g.pose().popMatrix();
	}

	/** The diamond overlay at the ACTUAL diamond (fixes the 84 px spark bug): 3 frames over 90 ms, 4 gold sparks. */
	private static void diamondFlash(GuiGraphicsExtractor g, Wheel w) {
		RouletteSpin sp = w.effects;
		for (MotionEvent e : sp.events()) {
			if (e.kind() != RouletteSpin.EV_DEFLECTOR) continue;
			double age = w.t - e.tMs();
			if (age < 0 || age > 160) continue;
			double da = RouletteSpin.DIAMOND0 + e.aux() * RouletteSpin.DIAMOND_PITCH;
			double x = px(w, da, RouletteSpin.R_DIAMOND);
			double y = py(w, da, RouletteSpin.R_DIAMOND);
			if (age < 90 && w.art >= 100) {
				int s = w.art == 104 ? 5 : w.art == 144 ? 7 : 9;
				String sprite = w.art == 104 ? "roulette/diamond_flash_m104" : w.art == 144 ? "roulette/diamond_flash_m144" : "roulette/diamond_flash_208";
				SnapDraw.frameF(g, sprite, s, s, 3, (int) (age / 30), x - s / 2.0 * w.scale, y - s / 2.0 * w.scale, TableGfx.fade(w.alpha), w.scale, w.k);
			}
			// 4 gold sparks flying outward 6 px
			double k = age / 160.0;
			double dist = 6 * Ease.OUT_CUBIC.apply(k) * w.scale;
			for (int i = 0; i < 4; i++) {
				double sa = Math.toRadians(da + (i - 1.5) * 28);
				double sx = x + dist * Math.sin(sa);
				double sy = y - dist * Math.cos(sa);
				double size = (i % 2 == 0 ? 2 : 1) * Math.max(1, w.scale * 0.75);
				dot(g, sx - size / 2, sy - size / 2, size, TableGfx.alpha(0xFFFFD640, (1 - k) * w.alpha), w.k);
			}
		}
	}

	/** A 2-frame spark (60 ms) at each rattle landing and at the first rotor contact. */
	private static void sparks(GuiGraphicsExtractor g, Wheel w) {
		RouletteSpin sp = w.effects;
		double rot = drawnRotor(w.art, w.rotor);
		for (MotionEvent e : sp.events()) {
			int kind = e.kind();
			if (kind != RouletteSpin.EV_FRET && kind != RouletteSpin.EV_FLOOR && kind != RouletteSpin.EV_KICKBACK && kind != RouletteSpin.EV_CONTACT) continue;
			double age = w.t - e.tMs();
			if (age < 0 || age >= 60) continue;
			// x = landing in pocket units (relative to the rotor), y = radius: the spark rides the rotor
			double a = rot + e.x() * RouletteSpin.STEP;
			double r = e.y();
			double x = px(w, a, r);
			double y = py(w, a, r);
			int f = age < 30 ? 0 : 1;
			int argb = TableGfx.alpha(0xFFFFFFFF, 0.7 * e.strength() * w.alpha);
			double s = Math.max(1, Math.min(2, w.scale * w.art / 208.0 * 1.5));
			SnapDraw.frameF(g, "core/spark", 7, 7, 4, f, x - 3.5 * s, y - 3.5 * s, argb, s, w.k);
		}
	}

	/** The result badge over the turret: the number in a disc of the pocket colour. */
	private static void badge(GuiGraphicsExtractor g, Font font, Wheel w) {
		double kk = Math.min(1, Math.max(0, w.badgeMs / 250.0));
		if (kk <= 0) return;
		double pop = Ease.OUT_BACK.apply(kk);
		double base = Math.max(8, 13 * w.art / 208.0 * w.scale + (w.art < 208 ? 2 : 0));
		int r = (int) Math.round(base * pop);
		if (r < 2) return;
		int cx = (int) Math.round(w.cx);
		int cy = (int) Math.round(w.cy);
		int n = w.badge;
		TableGfx.disc(g, cx, cy, r + 1, TableChrome.INK);
		TableGfx.disc(g, cx, cy, r, pocketColor(n));
		TableGfx.disc(g, cx, cy, Math.max(1, r - 2), TableGfx.lerp(pocketColor(n), 0xFFFFFFFF, 0.12));
		if (kk >= 1) {
			float ts = r >= 12 ? 2f : 1f;
			FxText.outlinedCentered(g, font, Texts.number(n), cx, cy - (int) (4 * ts), ts, TableChrome.BONE, TableChrome.INK);
		}
	}

	/** The idle mini wheel (72²): frame k has pocket WHEEL_ORDER[k] under the ball at the top. */
	static void mini(GuiGraphicsExtractor g, TableTheme t, int x, int y, int lastResult) {
		int k = lastResult >= 0 ? dev.nezo.burmaldaholic.games.roulette.logic.Wheel.wheelIndex(lastResult) : 0;
		TableGfx.region(g, TableGfx.sheet("roulette/wheel_mini_" + t.id), 720, 288, (k % 10) * 72, (k / 10) * 72, 72, 72, x, y, 0xFFFFFFFF);
	}
}
