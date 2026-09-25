package dev.nezo.burmaldaholic.games.slots.client.features;

import dev.nezo.burmaldaholic.core.anim.Beat;
import dev.nezo.burmaldaholic.core.anim.Ease;
import dev.nezo.burmaldaholic.core.anim.SeedMix;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.slots.client.SlotSounds;
import dev.nezo.burmaldaholic.games.slots.client.SlotStage;
import dev.nezo.burmaldaholic.games.slots.client.fx.ScreenParticles;
import dev.nezo.burmaldaholic.games.slots.client.fx.SlotDraw;
import dev.nezo.burmaldaholic.games.slots.client.fx.SlotSprites;
import dev.nezo.burmaldaholic.games.slots.client.panels.CabinetArt;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotTimeline;
import dev.nezo.burmaldaholic.games.slots.v2.present.SlotBeats;
import dev.nezo.burmaldaholic.games.slots.v2.present.SoundPlan;
import dev.nezo.burmaldaholic.games.slots.v2.present.SymbolStyle;
import dev.nezo.burmaldaholic.games.slots.v2.present.WheelMotion;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Dragon Wheel (slots.md §4.10; JS10): over a dark veil the three-ring wheel rises from below the cabinet, waits for
 * "Spin the wheel" (or autoplay), and the active ring spins with {@code outCubic} to the DRAWN wedge — the pointer
 * stops inside the wedge's central 60 % (F4) — the pointer claw deflects at every peg with a rising tick, the
 * winning wedge glows and its value pops. UP zooms into the next ring (the finished ring fades); the core darkens
 * the backdrop with drifting dragon breath. Wedge values come from the machine's wheel config (default: SLOTS.md
 * §3.3).
 */
public final class DragonWheelView {
	private static final int[] RING_R0 = {70, 44, 18};
	private static final int[] RING_R1 = {100, 70, 44};
	private static final String[] RING_KEYS = {"outer", "middle", "core"};

	private Beat intro;
	private final WheelMotion[] motion = new WheelMotion[3];
	private final Beat[] spins = new Beat[3];
	private final int[] pegs = new int[3];
	private final SoundPlan.Gate ticks = new SoundPlan.Gate(WheelMotion.MIN_TICK_GAP_MS);
	private double lastPegT = -1e9;
	private boolean interactive;
	private boolean landedSound;
	private final boolean[] landedRing = new boolean[3];

	public void reset(SlotStage s) {
		intro = null;
		interactive = false;
		java.util.Arrays.fill(motion, null);
		java.util.Arrays.fill(spins, null);
		java.util.Arrays.fill(pegs, 0);
		java.util.Arrays.fill(landedRing, false);
		rings = s.def().features().wheelRings();
		for (Beat b : s.beats(SlotTimeline.BONUS_INTRO)) if (b.arg(0) == SlotBeats.FEATURE_WHEEL) intro = b;
		if (intro == null) return;
		for (Beat b : s.beats(SlotTimeline.WHEEL_SPIN)) {
			int ring = Math.max(0, Math.min(2, b.lane()));
			spins[ring] = b;
			double start = ring == 0 ? 30.0 * (b.at() - intro.at()) / 1000.0 : 0;
			motion[ring] = new WheelMotion(wedges(ring).length, b.arg(0), start, b.dur(), SeedMix.mix(s.seed(), ring));
		}
	}

	private int[][] rings = new int[0][];

	/** Wedges of a ring from the machine definition the server sent (config {@code slots.end.wheel.*}). */
	private int[] wedges(int ring) {
		return rings.length == 0 ? new int[1] : rings[Math.max(0, Math.min(rings.length - 1, ring))];
	}

	public void registerHold(SlotStage s) {
		if (intro == null || spins[0] == null) return;
		interactive = true;
		s.clock().holdAt(intro.end());
	}

	public boolean waitingForSpin(SlotStage s) {
		return interactive && intro != null && s.clock().holding() && Math.abs(s.t() - intro.end()) < 1;
	}

	/** "Spin the wheel" (button, click, Space). */
	public boolean click(SlotStage s) {
		if (!waitingForSpin(s)) return false;
		SlotSounds.click();
		s.clock().release(spins[0].at());
		return true;
	}

	private double end() {
		double e = intro == null ? 0 : intro.end();
		for (Beat b : spins) if (b != null) e = Math.max(e, b.end() + 600 + 800);
		return e + 400;
	}

	public void cue(SlotStage s, Beat b) {
		if (b.kind().equals(SlotTimeline.BONUS_INTRO) && b.arg(0) == SlotBeats.FEATURE_WHEEL) {
			SlotSounds.play("slots.wheel_up", 0.8f, 1f);
		} else if (b.kind().equals(SlotTimeline.WHEEL_UP)) {
			SlotSounds.play("slots.wheel_up", b.lane() == 0 ? 1.0f : 1.2f, 1f);
		}
	}

	public void update(SlotStage s, boolean jump) {
		if (intro == null) return;
		double t = s.t();
		for (int ring = 0; ring < 3; ring++) {
			Beat b = spins[ring];
			if (b == null || t < b.at()) continue;
			double ms = Math.min(b.dur(), t - b.at());
			int passed = motion[ring].boundariesPassed(ms);
			if (passed != pegs[ring]) {
				pegs[ring] = passed;
				lastPegT = t;
				if (!jump && t < b.end() && ticks.tryFire(s.now())) SlotSounds.play("slots.wheel_tick", motion[ring].tickPitch(ms), 0.5f);
			}
			if (t >= b.end() && !landedRing[ring]) {
				landedRing[ring] = true;
				int v = wedges(ring)[b.arg(0)];
				if (!jump) {
					if (v >= 40 || v < 0) SlotSounds.bigWin();
					else if (v > 0) SlotSounds.winNice();
					float[] c = center(s);
					s.particles().burst(ScreenParticles.SPARKLE, c[0], c[1] - radiusScale(s) * 100, 14, 0.08f, 0, 600, s.now());
				}
			}
		}
	}

	private float[] center(SlotStage s) {
		return new float[] {s.wx() + s.windowW() / 2f, s.wy() + s.windowH() / 2f + 4};
	}

	private static float radiusScale(SlotStage s) {
		return s.cell() < 40 ? 0.6f : 0.82f;
	}

	public void draw(SlotStage s, GuiGraphicsExtractor g, int sw, int sh) {
		if (intro == null) return;
		double t = s.t();
		if (t < intro.at() || t >= end()) return;
		double exitMs = t - (end() - 400);
		double veil = Math.min(0.6, (t - intro.at()) / 300.0 * 0.6);
		int ringNow = 0;
		for (int r = 0; r < 3; r++) if (spins[r] != null && t >= spins[r].at()) ringNow = r;
		if (ringNow == 2) veil = Math.min(0.75, veil + 0.15);
		if (exitMs > 0) veil *= Math.max(0, 1 - exitMs / 400.0);
		g.fill(0, 0, sw, sh, SlotDraw.withAlpha(0xFF06020C, veil));
		float[] c = center(s);
		double rise = s.reduceMotion() ? 0 : WheelMotion.rise(t - intro.at());
		double sink = exitMs > 0 && !s.reduceMotion() ? 160 * Ease.IN_CUBIC.apply(Math.min(1, exitMs / 400.0)) : 0;
		float cx = c[0];
		float cy = (float) (c[1] + rise + sink);
		// zoom after each UP: the next ring grows to the old outer size
		double zoom = 1;
		for (Beat up : s.beats(SlotTimeline.WHEEL_UP)) {
			if (t < up.at()) continue;
			int from = up.lane();
			double target = (double) RING_R1[0] / RING_R1[from + 1];
			double prev = (double) RING_R1[0] / RING_R1[from];
			double u = s.reduceMotion() ? 1 : WheelMotion.zoom(t - up.at());
			zoom = prev + (target - prev) * u;
		}
		float scale = (float) (radiusScale(s) * zoom);
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().scale(scale, scale);
		// backing disc + rim
		SlotDraw.disc(g, 0, 0, RING_R1[0] + 4, 0xFF140C1C);
		SlotDraw.disc(g, 0, 0, RING_R1[0] + 2, 0xFFA77BA7);
		for (int ring = 0; ring < 3; ring++) {
			boolean active = ring == ringNow;
			boolean done = ring < ringNow;
			double angle = ringAngle(ring, t);
			double dim = active ? 1 : done ? 0.25 : 0.4;
			drawRing(s, g, ring, angle, dim, active && spins[ring] != null && t >= spins[ring].end() ? spins[ring] : null, t);
		}
		SlotDraw.disc(g, 0, 0, 12, 0xFF6A2A8A);
		SlotDraw.disc(g, -3, -3, 5, 0xFFB040FF);
		g.pose().popMatrix();
		drawPointer(s, g, cx, (float) (cy - RING_R1[0] * scale - 2), t);
		// ring title, ready button / hint
		int titleRing = ringNow;
		Component title = Component.translatable("gui.burmaldaholic.slots.wheel.ring." + RING_KEYS[titleRing]);
		SlotDraw.outlined(g, s.font(), title, cx, (float) (cy - RING_R1[0] * scale - 18), 1f, 0xFFFF9AE8, 0xFF180A28);
		if (waitingForSpin(s)) {
			Component spin = Component.translatable("gui.burmaldaholic.slots.wheel.spin");
			int w = s.font().width(spin) + 16;
			int bx = (int) cx - w / 2;
			int by = (int) (cy + RING_R1[0] * scale + 6);
			double pulse = 0.6 + 0.4 * Math.sin(s.now() / 250.0);
			SlotDraw.plate(g, bx, by, w, 16, 0xFF8A3AAA, 0xFF4A1A6A, SlotDraw.lerp(0xFFA77BA7, 0xFFFFFFFF, pulse * 0.5));
			g.centeredText(s.font(), spin, (int) cx, by + 4, 0xFFFFFFFF);
		}
		for (Beat up : s.beats(SlotTimeline.WHEEL_UP)) {
			double ms = t - up.at();
			if (ms >= 0 && ms < 1200) {
				SlotDraw.outlined(g, s.font(), Component.translatable("gui.burmaldaholic.slots.wheel.up"), cx, cy, 1.5f, SlotDraw.withAlpha(0xFFFF9AE8, 1 - ms / 1200.0),
					SlotDraw.withAlpha(0xFF180A28, 1 - ms / 1200.0));
			}
		}
		if (ringNow == 2 && !s.reduceMotion() && s.now() % 125 < 20) {
			s.particles().burst(ScreenParticles.MOTE, s.wx(), (float) (cy + (s.now() % 60) - 30), 1, 0.05f, 0, 1800, s.now());
		}
	}

	private double ringAngle(int ring, double t) {
		Beat b = spins[ring];
		if (b == null || motion[ring] == null) return ring == 0 && intro != null ? 30.0 * Math.max(0, t - intro.at()) / 1000.0 : 0;
		if (t < b.at()) return ring == 0 ? 30.0 * Math.max(0, t - intro.at()) / 1000.0 : 0;
		return motion[ring].angle(t - b.at());
	}

	private void drawRing(SlotStage s, GuiGraphicsExtractor g, int ring, double angleDeg, double dim, Beat landed, double t) {
		int[] w = wedges(ring);
		int n = w.length;
		double a = 360.0 / n;
		int r0 = RING_R0[ring];
		int r1 = RING_R1[ring];
		int win = landed == null ? -1 : landed.arg(0);
		double winMs = landed == null ? 0 : t - landed.end();
		for (int i = 0; i < n; i++) {
			int base = wedgeColor(w[i], ring, i);
			double bright = dim;
			if (i == win && winMs < 600) bright = Math.min(1.4, dim + 0.4 * (0.5 + 0.5 * Math.sin(winMs / 60.0)));
			else if (i == win) bright = 1.25;
			int color = SlotDraw.shade(base, bright);
			double from = i * a;
			for (double d = from + 0.6; d < from + a - 0.6; d += 2.2) {
				double rad = Math.toRadians(d + angleDeg - 90);
				SlotDraw.line(g, Math.cos(rad) * r0, Math.sin(rad) * r0, Math.cos(rad) * r1, Math.sin(rad) * r1, 3, color);
			}
			// wedge boundary (peg)
			double br = Math.toRadians(from + angleDeg - 90);
			SlotDraw.line(g, Math.cos(br) * r0, Math.sin(br) * r0, Math.cos(br) * (r1 + 1), Math.sin(br) * (r1 + 1), 1, SlotDraw.shade(0xFF140C1C, 1));
			g.fill((int) Math.round(Math.cos(br) * (r1 - 2)) - 1, (int) Math.round(Math.sin(br) * (r1 - 2)) - 1, (int) Math.round(Math.cos(br) * (r1 - 2)) + 1,
				(int) Math.round(Math.sin(br) * (r1 - 2)) + 1, 0xFFF4ECF8);
			// radial value text
			double mid = Math.toRadians(from + a / 2 + angleDeg - 90);
			float tr = (r0 + r1) / 2f;
			Component label = label(w[i]);
			g.pose().pushMatrix();
			g.pose().translate((float) (Math.cos(mid) * tr), (float) (Math.sin(mid) * tr));
			g.pose().rotate((float) mid);
			float ts = ring == 2 ? 0.6f : 0.7f;
			double pop = i == win && winMs >= 0 && winMs < 300 ? 1 + 0.2 * Math.sin(Math.PI * winMs / 300.0) : 1;
			g.pose().scale((float) (ts * pop), (float) (ts * pop));
			int tw = s.font().width(label);
			g.text(s.font(), label, -tw / 2, -4, SlotDraw.shade(0xFFFFFFFF, Math.min(1, bright)), true);
			g.pose().popMatrix();
		}
	}

	private static Component label(int v) {
		if (v == 0) return Component.translatable("gui.burmaldaholic.slots.fx.wheel_up_wedge");
		if (v < 0) return Component.translatable("gui.burmaldaholic.slots.jackpot.tier." + new String[] {"mini", "minor", "major", "grand"}[Math.min(4, -v) - 1]);
		return Component.translatable("gui.burmaldaholic.slots.fx.times", Texts.number(v));
	}

	private static int wedgeColor(int v, int ring, int i) {
		if (v < 0) return SymbolStyle.JACKPOT_COLORS[Math.min(4, -v) - 1];
		if (v == 0) return 0xFFFF40C0;
		int[] outer = {0xFFE8E4A8, 0xFFC8C084};
		int[] middle = {0xFFC8A0C8, 0xFFA77BA7};
		int[] core = {0xFF8A3AAA, 0xFF6A2A8A};
		int[] pal = ring == 0 ? outer : ring == 1 ? middle : core;
		int c = pal[i % 2];
		if (v >= 40 && ring == 0) c = 0xFFFFD640;
		if (v >= 100 && ring == 1) c = 0xFFFFD640;
		if (v >= 500) c = 0xFFFFC400;
		return c;
	}

	private void drawPointer(SlotStage s, GuiGraphicsExtractor g, float x, float y, double t) {
		double deflect = s.reduceMotion() ? 0 : WheelMotion.pointerDeflect(t - lastPegT);
		g.pose().pushMatrix();
		g.pose().translate(x, y - 8);
		g.pose().rotate((float) Math.toRadians(deflect));
		if (CabinetArt.ART) {
			boolean grip = false;
			for (Beat up : s.beats(SlotTimeline.WHEEL_UP)) if (t >= up.at() && t < up.at() + 300) grip = true;
			SlotSprites.frame(g, SlotSprites.POINTER, 16, 24, 2, grip ? 1 : 0, -8, -6, 16, 24, 0xFFFFFFFF);
			g.pose().popMatrix();
			return;
		}
		// dragon claw: a downward triangle with a glowing tip
		for (int i = 0; i < 12; i++) {
			int half = Math.max(0, 7 - i * 7 / 12);
			g.fill(-half, i, half + 1, i + 1, i < 3 ? 0xFF3A1A4A : 0xFF6A2A8A);
		}
		g.fill(-1, 10, 2, 14, 0xFFFF9AE8);
		g.pose().popMatrix();
	}
}
