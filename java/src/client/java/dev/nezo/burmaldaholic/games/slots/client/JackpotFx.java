package dev.nezo.burmaldaholic.games.slots.client;

import dev.nezo.burmaldaholic.core.anim.Beat;
import dev.nezo.burmaldaholic.core.anim.Ease;
import dev.nezo.burmaldaholic.core.anim.RollUp;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.slots.client.fx.ScreenParticles;
import dev.nezo.burmaldaholic.games.slots.client.fx.SlotDraw;
import dev.nezo.burmaldaholic.games.slots.v2.logic.Machine;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotTimeline;
import dev.nezo.burmaldaholic.games.slots.v2.present.CelebrationPlan;
import dev.nezo.burmaldaholic.games.slots.v2.present.FeatureMotion;
import dev.nezo.burmaldaholic.games.slots.v2.present.SymbolStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Jackpot celebrations by sub-tier (slots.md §4.11; JS11), played after the step that awarded them and before the
 * spin total, in tape order (each later one at 70 %). Mini: a 30 % veil, the plate pops out of its meter and flies
 * to the centre, "MINI JACKPOT!" at 2×. Minor: + rays. Major: + double counter-rotating rays, outElastic word, one
 * 25 % gold flash, confetti. Grand: the meter plate shatters into coins, the badge re-forms in the centre, a letter
 * wave, a 30 % flash and a 4 px shake. Every flash respects {@code anim.flashes}; reduce motion shows a static plaque.
 */
public final class JackpotFx {
	private static final String[] TIER_KEYS = {"mini", "minor", "major", "grand"};
	private final double[] pt = new double[2];
	private Beat burstDone;
	private int lastTickIndex = -1;

	public void reset() {
		burstDone = null;
		lastTickIndex = -1;
	}

	/** The celebration running now, or null. */
	public Beat active(SlotStage s) {
		return s.script() == null ? null : s.script().active(SlotTimeline.JACKPOT, s.t());
	}

	/** Consumes a skip before the tier's "skippable after" time. */
	public boolean skip(SlotStage s) {
		Beat b = active(s);
		if (b == null) return false;
		return s.t() - b.at() < CelebrationPlan.jackpot(b.arg(0)).skippableMs();
	}

	public void cue(SlotStage s, Beat b) {
		if (!b.kind().equals(SlotTimeline.JACKPOT)) return;
		int tier = b.arg(0);
		CelebrationPlan.Jackpot p = CelebrationPlan.jackpot(tier);
		SlotSounds.jackpot(p.pitch(), tier == 1 ? 0.6f : 1f);
		if (tier == 4) {
			SlotSounds.epicWin();
			if (s.machine() == Machine.END) SlotSounds.vanilla(SoundEvents.ENDER_DRAGON_GROWL, 1.6f, 0.3f);
		}
	}

	public void update(SlotStage s, boolean jump) {
		Beat b = active(s);
		if (b == null || jump) return;
		double ms = s.t() - b.at();
		int tier = b.arg(0);
		CelebrationPlan.Jackpot p = CelebrationPlan.jackpot(tier);
		if (burstDone != b && ms > 400) {
			burstDone = b;
			float cx = s.wx() + s.windowW() / 2f;
			float cy = s.wy() + s.windowH() / 2f;
			int coins = Math.min(p.coins(), 60);
			s.particles().fountain(ScreenParticles.COIN, cx, cy + 20, s.windowW(), coins, s.now());
			if (tier >= 3) s.particles().rain(ScreenParticles.CONFETTI, s.wx() - 60, s.wx() + s.windowW() + 60, s.wy() - 30, 20, s.now());
			if (tier == 4) s.particles().burst(ScreenParticles.STAR, cx, cy - 20, 8, 0.12f, 0, 900, s.now());
		}
		int idx = (int) (ms / 70);
		if (ms < p.rollMs() && idx != lastTickIndex) {
			lastTickIndex = idx;
			SlotSounds.play("slots.rollup_tick", RollUp.tickPitch(idx), 0.6f);
		}
	}

	public void draw(SlotStage s, GuiGraphicsExtractor g, int sw, int sh) {
		Beat b = active(s);
		if (b == null) return;
		double ms = s.t() - b.at();
		int tier = Math.max(1, Math.min(4, b.arg(0)));
		long chips = Integer.toUnsignedLong(b.arg(1));
		CelebrationPlan.Jackpot p = CelebrationPlan.jackpot(tier);
		boolean rm = s.reduceMotion();
		double scaleTime = b.dur() / (double) p.lengthMs();
		double fade = Math.min(1, ms / 150.0) * Math.min(1, (b.dur() - ms) / 250.0);
		int color = SymbolStyle.JACKPOT_COLORS[tier - 1];
		double shake = tier == 4 ? CelebrationPlan.shake(ms - 600 * scaleTime, rm, s.seed()) * p.shakePx() / 4.0 : 0;
		g.pose().pushMatrix();
		g.pose().translate((float) shake, (float) (shake * 0.6));
		g.fill(0, 0, sw, sh, SlotDraw.withAlpha(0xFF06040C, p.veil() * fade));
		float cx = s.wx() + s.windowW() / 2f;
		float cy = s.wy() + s.windowH() / 2f;
		if (!rm && p.rays() > 0) {
			double ang = ms / 1000.0 * Math.toRadians(20);
			SlotDraw.rays(g, cx, cy, 150, 14, ang, SlotDraw.withAlpha(color, 0.6 * fade));
			if (p.rays() > 1) SlotDraw.rays(g, cx, cy, 120, 10, -ang * 1.3, SlotDraw.withAlpha(0xFFFFD640, 0.5 * fade));
		}
		if (tier == 4 && s.flashes() && !rm) {
			// gold edge vignette at 1 Hz
			double v = 0.15 + 0.15 * Math.sin(ms / 1000.0 * Math.PI * 2);
			SlotDraw.frame(g, 0, 0, sw, sh, 10, SlotDraw.withAlpha(0xFFFFD640, v * fade));
		}
		// the meter plate flies out of the bar (Grand: shatters and re-forms)
		int[] meter = s.host().meterCenter(tier);
		double fly = rm ? 1 : Math.min(1, ms / 400.0);
		if (tier == 4 && !rm && ms < 600) {
			if (ms < 30) s.particles().burst(ScreenParticles.COIN, meter[0], meter[1], 24, 0.2f, 0.0005f, 900, s.now());
			fly = 0;
		}
		FeatureMotion.arc(meter[0], meter[1], cx, cy - 34, 40, Ease.IN_OUT_SINE.apply(fly), pt);
		if (tier < 4 || ms >= 600 || rm) {
			double reform = tier == 4 && !rm ? Ease.OUT_BACK.apply(Math.min(1, (ms - 600) / 300.0)) : 1;
			SlotDraw.gem(g, (int) pt[0], (int) pt[1], (int) Math.round(12 * reform), tier == 4 ? SlotDraw.rainbow(s.now() / 1200.0) : color);
		}
		// flash at the word (Major) / the re-form (Grand)
		double flashAt = tier == 4 ? 600 : 500;
		double flash = CelebrationPlan.flash(ms - flashAt * scaleTime, s.flashes() && !rm, p.flashPeak());
		if (flash > 0) g.fill(0, 0, sw, sh, SlotDraw.withAlpha(0xFFFFD640, flash));
		// the word
		Component tierWord = Component.translatable("gui.burmaldaholic.slots.jackpot.tier." + TIER_KEYS[tier - 1]);
		Component word = Component.translatable("gui.burmaldaholic.slots.jackpot.won", tierWord);
		double in = rm ? 1 : CelebrationPlan.wordIn(ms - 300, p.elastic());
		int scale = SlotDraw.fitScale(s.font(), word, p.wordScale(), sw - 20);
		if (in > 0) {
			if (tier == 4 && !rm) {
				letterWave(g, s.font(), word, cx, cy, (float) (scale * in), ms, color);
			} else {
				SlotDraw.outlined(g, s.font(), word, cx, cy, (float) (scale * in), tier == 4 ? SlotDraw.rainbow(s.now() / 1500.0) : color, 0xFF180A28);
			}
		}
		double rollMs = p.rollMs() * scaleTime;
		double u = rm ? 1 : Math.min(1, Math.max(0, (ms - 400) / rollMs));
		long shown = RollUp.valueAt(chips, u);
		SlotDraw.outlined(g, s.font(), Texts.chips(shown), cx, cy + 12 + 5 * scale, 2f, 0xFFFFFFFF, 0xFF180A28);
		g.pose().popMatrix();
	}

	/** Grand: every character bobs on its own phase ({@code 2·sin(t·8 + i)}). */
	private static void letterWave(GuiGraphicsExtractor g, Font font, Component word, float cx, float cy, float scale, double ms, int color) {
		String str = word.getString();
		int total = font.width(str);
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().scale(scale, scale);
		float x = -total / 2f;
		for (int i = 0; i < str.length(); i++) {
			String ch = String.valueOf(str.charAt(i));
			int y = (int) Math.round(CelebrationPlan.letterWave(ms, i)) - 4;
			int c = SlotDraw.rainbow(ms / 1500.0 + i * 0.05);
			g.text(font, ch, Math.round(x) + 1, y + 1, 0xFF180A28, false); // literal-ok: one character of a translated word
			g.text(font, ch, Math.round(x), y, c, false); // literal-ok: one character of a translated word
			x += font.width(ch);
		}
		g.pose().popMatrix();
	}
}
