package dev.nezo.burmaldaholic.games.slots.client.features;

import dev.nezo.burmaldaholic.core.anim.Beat;
import dev.nezo.burmaldaholic.core.anim.Ease;
import dev.nezo.burmaldaholic.core.anim.RollUp;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.slots.client.SlotSounds;
import dev.nezo.burmaldaholic.games.slots.client.SlotStage;
import dev.nezo.burmaldaholic.games.slots.client.fx.ScreenParticles;
import dev.nezo.burmaldaholic.games.slots.client.fx.SlotDraw;
import dev.nezo.burmaldaholic.games.slots.client.fx.SlotSprites;
import dev.nezo.burmaldaholic.games.slots.client.panels.CabinetArt;
import dev.nezo.burmaldaholic.games.slots.client.reels.SymbolSheet;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotTimeline;
import dev.nezo.burmaldaholic.games.slots.v2.present.CelebrationPlan;
import dev.nezo.burmaldaholic.games.slots.v2.present.FeatureMotion;
import dev.nezo.burmaldaholic.games.slots.v2.present.SlotBeats;
import dev.nezo.burmaldaholic.games.slots.v2.present.SymbolStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Free spins (slots.md §4.7; JS7): the intro (scatters pop and fly to the centre, the FREE SPINS banner with the
 * awarded count rolling up, an iris wipe into the machine's free-spin theme, the banner shrinking into the counter
 * plate), per-spin counter flips and the feature total, retriggers (a "+N" chip flies to the counter, which punches),
 * the outro banner with the feature total roll-up, and the theme swapping back. Music: a looping stream that fades
 * in during the intro and out at the outro. Reduce motion: static banner, no flying scatters, instant theme swap.
 */
public final class FreeSpinsView {
	private SlotSounds.Loop music;
	private final double[] pt = new double[2];
	private final dev.nezo.burmaldaholic.games.slots.v2.present.SoundPlan.Gate outroTicks =
		new dev.nezo.burmaldaholic.games.slots.v2.present.SoundPlan.Gate(CelebrationPlan.TICK_GAP_MS);

	public void reset() {
		stopLoops();
	}

	public void stopLoops() {
		if (music != null) music.release();
		music = null;
	}

	private Beat intro(SlotStage s) {
		var l = s.beats(SlotTimeline.FS_INTRO);
		return l.isEmpty() ? null : l.get(0);
	}

	private Beat outro(SlotStage s) {
		var l = s.beats(SlotTimeline.FS_OUTRO);
		return l.isEmpty() ? null : l.get(0);
	}

	public void cue(SlotStage s, Beat b) {
		switch (b.kind()) {
			case SlotTimeline.FS_INTRO -> SlotSounds.fsIntro(1f, 1f);
			case SlotTimeline.FS_RETRIGGER -> {
				SlotSounds.fsIntro(1.2f, 0.6f);
				s.particles().burst(ScreenParticles.SPARKLE, s.wx() + s.windowW() / 2f, s.wy() + s.windowH() / 2f, 16, 0.1f, 0, 600, s.now());
			}
			case SlotTimeline.FS_OUTRO -> {
				SlotSounds.fsOutro();
				if (music != null) music.release();
				music = null;
			}
			default -> {
			}
		}
	}

	public void update(SlotStage s) {
		Beat in = intro(s);
		Beat out = outro(s);
		double t = s.t();
		boolean inFeature = in != null && t >= in.at() + 1200 && (out == null || t < out.at());
		if (inFeature && music == null && !s.clock().fastForwarding()) {
			music = SlotSounds.Loop.start("slots.fs_music." + s.machine().id, 0.5f, 1f, 800);
		}
		if (!inFeature && music != null) {
			music.release();
			music = null;
		}
	}

	/** 0 = base theme, 1 = free-spin theme (the iris wipe in, the reverse wipe out). */
	public double themeAmount(SlotStage s) {
		if (s.script() == null) return 0;
		Beat in = intro(s);
		if (in == null) return 0;
		double t = s.t();
		if (t < in.at()) return 0;
		Beat out = outro(s);
		if (out != null && t >= out.end() - 600) return 1 - Math.min(1, (t - (out.end() - 600)) / 600.0);
		if (t < in.end()) return FeatureMotion.iris(t - in.at(), s.reduceMotion());
		return 1;
	}

	/** Running free spin: {i (0-based), spinsTotal} or null outside the feature. */
	public int[] counter(SlotStage s) {
		if (s.script() == null) return null;
		Beat in = intro(s);
		double t = s.t();
		if (in == null || t < in.end() - 200) return null;
		Beat out = outro(s);
		if (out != null && t >= out.at()) return null;
		Beat spin = s.script().latest(SlotTimeline.FS_SPIN, -1, t);
		int total = in.arg(0);
		Beat re = s.script().latest(SlotTimeline.FS_RETRIGGER, -1, t);
		if (re != null) total = re.arg(1);
		if (spin == null) return new int[] {0, total, 0};
		return new int[] {spin.arg(0), Math.max(total, spin.arg(0) + 1), (int) (t - spin.at())};
	}

	/** Feature total shown on the plate (short roll-ups ≤ 600 ms as each spin wins). */
	public long featureTotal(SlotStage s) {
		if (s.script() == null) return 0;
		double t = s.t();
		Beat spin = s.script().latest(SlotTimeline.FS_SPIN, -1, t);
		if (spin == null) return 0;
		long total = spin.arg(2);
		int spinIndex = spin.arg(0) + 1;
		for (Beat w : s.beats(SlotTimeline.WIN_SHOW)) {
			if (w.arg(SlotBeats.SPIN) != spinIndex || w.at() > t) continue;
			double u = Math.min(1, (t - w.at()) / 600.0);
			total += RollUp.valueAt(w.arg(SlotBeats.WIN_PAY), u);
		}
		return total;
	}

	/** Counter punch after a retrigger. */
	public double punch(SlotStage s) {
		Beat re = s.script() == null ? null : s.script().latest(SlotTimeline.FS_RETRIGGER, -1, s.t());
		return re == null ? 1 : FeatureMotion.punch(s.t() - re.at() - 500);
	}

	public void draw(SlotStage s, GuiGraphicsExtractor g, int sw, int sh) {
		if (s.script() == null) return;
		double t = s.t();
		Beat in = intro(s);
		if (in != null && t >= in.at() && t < in.end()) drawIntro(s, g, in, t - in.at(), sw, sh);
		Beat re = s.script().latest(SlotTimeline.FS_RETRIGGER, -1, t);
		if (re != null && t < re.end()) drawRetrigger(s, g, re, t - re.at());
		Beat out = outro(s);
		if (out != null && t >= out.at() && t < out.end()) drawOutro(s, g, out, t - out.at(), sw, sh);
	}

	private void drawIntro(SlotStage s, GuiGraphicsExtractor g, Beat in, double ms, int sw, int sh) {
		boolean rm = s.reduceMotion();
		double veil = rm ? 0.5 : FeatureMotion.veil(ms, 300, 500);
		g.fill(0, 0, sw, sh, SlotDraw.withAlpha(0xFF06040C, veil));
		int mask = in.arg(1);
		float cx = s.wx() + s.windowW() / 2f;
		float cy = s.wy() + s.windowH() / 2f;
		// scatters pop one by one, then fly along arcs into the banner emblem
		int i = 0;
		for (int c = 0; c < 15; c++) {
			if ((mask & (1 << c)) == 0) continue;
			double x0 = s.cellX(c / 3);
			double y0 = s.cellY(c % 3);
			double pop = rm ? 1 : FeatureMotion.introPop(ms, i);
			double u = rm ? 0 : Math.max(0, Math.min(1, (ms - 300) / 500.0));
			if (ms < 800 && !rm) {
				FeatureMotion.arc(x0, y0, cx - s.cell() / 2.0, cy - 40 - s.cell() / 2.0, 30, Ease.IN_OUT_SINE.apply(u), pt);
				SymbolSheet.draw(g, s.machine(), 1, SymbolSheet.COL_WIN + (int) (ms / 80) % 8, (int) pt[0], (int) pt[1], s.cell(), pop, pop, 1);
				if (u > 0 && u < 1 && (int) ms % 3 == 0) s.particles().burst(ScreenParticles.SPARKLE, (float) pt[0] + s.cell() / 2f, (float) pt[1] + s.cell() / 2f, 1, 0.02f, 0, 400, s.now());
			}
			i++;
		}
		// iris ring expanding from the banner: the theme swap
		double iris = FeatureMotion.iris(ms, rm);
		if (iris > 0 && iris < 1 && !rm) {
			int radius = (int) (iris * Math.hypot(sw, sh));
			int color = SlotDraw.withAlpha(SymbolStyle.theme(s.machine()).glow(), 0.6 * (1 - iris));
			ring(g, (int) cx, (int) cy - 20, radius, color);
			ring(g, (int) cx, (int) cy - 20, radius - 3, SlotDraw.withAlpha(0xFFFFFFFF, 0.3 * (1 - iris)));
		}
		double scale = rm ? (ms >= 600 && ms < 2000 ? 1 : 0) : FeatureMotion.introBanner(ms);
		if (scale <= 0.01) return;
		// emblem: the scatter drawn 3× above the banner
		if (ms >= 800 || rm) SymbolSheet.draw(g, s.machine(), 1, SymbolSheet.COL_WIN + (int) (ms / 80) % 8, (int) cx - s.cell() / 2, (int) cy - 58 - s.cell() / 2,
			s.cell(), 1.2 * scale, 1.2 * scale, 1);
		Component title = Component.translatable("gui.burmaldaholic.slots.fs.title");
		Component name = Component.translatable("gui.burmaldaholic.slots.fs.name." + s.machine().id);
		Component awarded = Component.translatable("gui.burmaldaholic.slots.fs.awarded", Texts.number(FeatureMotion.introCount(rm ? 2000 : ms, in.arg(0))));
		banner(s, g, cx, cy, scale, title, name, awarded, s.windowW() + 40);
	}

	private void drawRetrigger(SlotStage s, GuiGraphicsExtractor g, Beat re, double ms) {
		if (ms > 700) return;
		int[] target = s.host().featurePanelCenter();
		double u = s.reduceMotion() ? 1 : Math.min(1, ms / 500.0);
		FeatureMotion.arc(s.wx() + s.windowW() / 2.0, s.wy() + s.windowH() / 2.0, target[0], target[1], 50,
			Ease.IN_OUT_SINE.apply(u), pt);
		Component chip = Component.translatable("gui.burmaldaholic.slots.fs.retrigger", Texts.number(re.arg(0)));
		SlotDraw.outlined(g, s.font(), chip, (float) pt[0], (float) pt[1], u < 1 ? 2f : 1.5f, 0xFFFFD640, 0xFF180A28);
	}

	private void drawOutro(SlotStage s, GuiGraphicsExtractor g, Beat out, double ms, int sw, int sh) {
		double veil = s.reduceMotion() ? 0.5 : FeatureMotion.veil(ms, 0, 200);
		double tail = out.dur() - ms;
		if (tail < 600) veil *= tail / 600.0;
		g.fill(0, 0, sw, sh, SlotDraw.withAlpha(0xFF06040C, veil));
		long chips = out.arg(0);
		int r0 = CelebrationPlan.rollUpMs(chips, Math.max(1, s.bet()));
		double rollMs = out.dur() * (double) r0 / (r0 + 1500);
		if (s.reduceMotion()) rollMs = Math.min(rollMs, 300);
		double u = Math.min(1, Math.max(0, (ms - 200) / Math.max(1, rollMs)));
		long shown = RollUp.valueAt(chips, u);
		double scale = ms < 200 ? 0 : s.reduceMotion() ? 1 : Ease.OUT_BACK.apply(Math.min(1, (ms - 200) / 250.0));
		if (tail < 400 && !s.reduceMotion()) scale *= Math.max(0, tail / 400.0);
		if (scale <= 0.01) return;
		float cx = s.wx() + s.windowW() / 2f;
		float cy = s.wy() + s.windowH() / 2f;
		Component title = Component.translatable("gui.burmaldaholic.slots.fs.end", Texts.chips(shown));
		Component name = Component.translatable("gui.burmaldaholic.slots.fs.name." + s.machine().id);
		banner(s, g, cx, cy, scale, title, name, null, s.windowW() + 60);
		if (u > 0 && u < 1 && outroTicks.tryFire(s.now())) SlotSounds.play("slots.rollup_tick", 1f + (float) u * 0.4f, 0.6f);
	}

	/** Theme banner: a plate in the machine colours with up to three centred lines (the first at 2× if it fits). */
	private static void banner(SlotStage s, GuiGraphicsExtractor g, float cx, float cy, double scale, Component l1, Component l2, Component l3, int maxW) {
		SymbolStyle.Theme theme = SymbolStyle.theme(s.machine());
		var font = s.font();
		int s1 = SlotDraw.fitScale(font, l1, 2, maxW - 16);
		int w = Math.min(maxW, Math.max(font.width(l1) * s1, Math.max(font.width(l2), l3 == null ? 0 : font.width(l3))) + 24);
		int h = 12 + 9 * s1 + 12 + (l3 == null ? 0 : 12);
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().scale((float) scale, (float) scale);
		if (CabinetArt.ART) {
			SlotDraw.glow(g, -w / 2, -h / 2, w, h, theme.glow(), 3, 0.7);
			SlotSprites.blit(g, SlotSprites.machine(s.machine(), "banner"), -w / 2 - 4, -h / 2 - 4, w + 8, h + 8);
		} else {
			SlotDraw.plate(g, -w / 2, -h / 2, w, h, SlotDraw.shade(theme.fsSkyBottom(), 1.3), theme.fsSkyTop(), theme.trim());
			SlotDraw.glow(g, -w / 2, -h / 2, w, h, theme.glow(), 3, 0.7);
		}
		int y = -h / 2 + 8;
		SlotDraw.outlined(g, font, l1, 0, y + 4.5f * s1, s1, 0xFFFFD640, 0xFF180A28);
		y += 9 * s1 + 4;
		SlotDraw.centeredFit(g, font, l2, 0, y, w - 8, 0xFFF4ECF8);
		if (l3 != null) SlotDraw.centeredFit(g, font, l3, 0, y + 12, w - 8, 0xFFFFD640);
		g.pose().popMatrix();
	}

	private static void ring(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
		if (r <= 0) return;
		int steps = Math.max(24, r / 2);
		for (int i = 0; i < steps; i++) {
			double a = i * Math.PI * 2 / steps;
			int x = cx + (int) Math.round(Math.cos(a) * r);
			int y = cy + (int) Math.round(Math.sin(a) * r);
			g.fill(x - 1, y - 1, x + 2, y + 2, color);
		}
	}
}
