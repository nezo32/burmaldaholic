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
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotTimeline;
import dev.nezo.burmaldaholic.games.slots.v2.present.CelebrationPlan;
import dev.nezo.burmaldaholic.games.slots.v2.present.FeatureMotion;
import dev.nezo.burmaldaholic.games.slots.v2.present.SlotBeats;
import dev.nezo.burmaldaholic.games.slots.v2.present.SoundPlan;
import dev.nezo.burmaldaholic.games.slots.v2.present.SymbolStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Piglin's Hoard (slots.md §4.9; JS9): the non-coin symbols fade to dark blackstone cells, the coins lock with a
 * clasping gold frame and show their value, three respin pips appear. Each respin spins every empty cell as a mini
 * reel of NEUTRAL EMBER BLUR (D5: a coin never scrolls past, no fake near-miss); a new coin lands with a heavy thump
 * and resets the pips to 3, no coin drains one pip. Collect: every coin lights in reading order and its value flies to
 * the bonus total; filling all 15 cells flashes the grid and slams ALL 15 FILLED before the Grand.
 */
public final class HoardView {
	private final int[] values = new int[15];
	private final double[] landedAt = new double[15];
	private final int[] readingOrder = new int[15];
	private final double[] pt = new double[2];
	private final SoundPlan.Gate tick = new SoundPlan.Gate(60);
	private int lastCollected = -1;

	public void reset() {
		lastCollected = -1;
		tick.reset();
	}

	private static Beat intro(SlotStage s) {
		for (Beat b : s.beats(SlotTimeline.BONUS_INTRO)) if (b.arg(0) == SlotBeats.FEATURE_HOARD) return b;
		return null;
	}

	private static Beat collect(SlotStage s) {
		var l = s.beats(SlotTimeline.HOARD_COLLECT);
		return l.isEmpty() ? null : l.get(0);
	}

	private double coverEnd(SlotStage s) {
		Beat c = collect(s);
		return c == null ? Double.NEGATIVE_INFINITY : c.end() + 300;
	}

	public boolean covers(SlotStage s) {
		Beat in = intro(s);
		return in != null && s.t() >= in.at() && s.t() < coverEnd(s);
	}

	public double coverAlpha(SlotStage s) {
		Beat in = intro(s);
		if (!covers(s)) return 0;
		double t = s.t();
		return Math.min(Math.min(1, (t - in.at()) / 200.0), Math.max(0, (coverEnd(s) - t) / 300.0));
	}

	public void cue(SlotStage s, Beat b) {
		if (b.kind().equals(SlotTimeline.BONUS_INTRO) && b.arg(0) == SlotBeats.FEATURE_HOARD) {
			int n = Integer.bitCount(b.arg(1));
			for (int i = 0; i < Math.min(n, 4); i++) SlotSounds.coinLand(1f + 0.06f * i);
		} else if (b.kind().equals(SlotTimeline.HOARD_RESPIN)) {
			if (b.arg(0) != 0) {
				SlotSounds.coinLand(1f);
				SlotSounds.play("slots.respin_reset", 1f, 1f);
			} else {
				SlotSounds.vanilla(SoundEvents.NOTE_BLOCK_HAT, 0.7f, 0.5f);
			}
		} else if (b.kind().equals(SlotTimeline.HOARD_COLLECT) && b.arg(1) != 0) {
			SlotSounds.jackpot(1f, 0.8f);
		}
	}

	/** Coins visible at t (mask), with their values and landing times filled into the arrays. */
	private int state(SlotStage s, double t) {
		Beat in = intro(s);
		int mask = in.arg(1);
		int vi = 2;
		for (int c = 0; c < 15; c++) {
			if ((mask & (1 << c)) == 0) continue;
			values[c] = in.arg(vi++);
			landedAt[c] = in.at();
		}
		for (Beat r : s.beats(SlotTimeline.HOARD_RESPIN)) {
			if (r.at() > t) break;
			int nm = r.arg(0);
			int k = 0;
			int vj = 2;
			int empties = 0;
			for (int c = 0; c < 15; c++) {
				if ((mask & (1 << c)) != 0) continue;
				int stop = FeatureMotion.miniReelStop(empties++);
				if ((nm & (1 << c)) != 0) {
					double at = r.at() + stop;
					values[c] = r.arg(vj++);
					landedAt[c] = at;
					k++;
				}
			}
			for (int c = 0; c < 15; c++) if ((nm & (1 << c)) != 0 && landedAt[c] <= t) mask |= 1 << c;
			if (k > 0 && r.end() <= t) mask |= nm;
		}
		return mask;
	}

	/** Respins left shown by the pips. */
	public int respinsLeft(SlotStage s) {
		int left = 3;
		for (Beat r : s.beats(SlotTimeline.HOARD_RESPIN)) if (r.at() + 700 <= s.t()) left = r.arg(1);
		return left;
	}

	/** Coins count (for the panel). */
	public int coins(SlotStage s) {
		return intro(s) == null ? 0 : Integer.bitCount(state(s, s.t()));
	}

	/** The bonus total rolled so far by the collect sweep (chips). */
	public long collected(SlotStage s) {
		Beat c = collect(s);
		if (c == null || s.t() < c.at()) return 0;
		double u = Math.min(1, (s.t() - c.at()) / Math.max(1, c.dur() - 300.0));
		return RollUp.valueAt(c.arg(0), u);
	}

	public void draw(SlotStage s, GuiGraphicsExtractor g) {
		double a = coverAlpha(s);
		if (a <= 0) return;
		double t = s.t();
		Beat in = intro(s);
		int cell = s.cell();
		int mask = state(s, t);
		// reading order (rows left → right) for the collect sweep
		int n = 0;
		for (int row = 0; row < 3; row++) for (int reel = 0; reel < 5; reel++) {
			int c = reel * 3 + row;
			if ((mask & (1 << c)) != 0) readingOrder[n++] = c;
		}
		Beat col = collect(s);
		Beat respin = s.script().active(SlotTimeline.HOARD_RESPIN, t);
		boolean full = col != null && col.arg(1) != 0;
		int empties = 0;
		for (int c = 0; c < 15; c++) {
			int x = s.cellX(c / 3);
			int y = s.cellY(c % 3);
			boolean coin = (mask & (1 << c)) != 0;
			// blackstone cell with a faint lava seam
			if (CabinetArt.ART) SlotSprites.blit(g, SlotSprites.EMPTY_CELL, x, y, cell, cell, SlotDraw.withAlpha(0xFFFFFFFF, a));
			else g.fill(x + 1, y + 1, x + cell - 1, y + cell - 1, SlotDraw.withAlpha(0xFF1C1418, a));
			g.fill(x + 3, y + cell - 8, x + cell - 3, y + cell - 7, SlotDraw.withAlpha(0xFFFF7A1A, a * (0.35 + 0.15 * Math.sin(s.now() / 400.0 + c))));
			if (a < 0.9) continue;
			if (!coin) {
				int idx = empties++;
				if (respin != null && t - respin.at() < FeatureMotion.miniReelStop(idx) + 120) {
					// neutral ember blur mini reel (never a coin in the filler)
					double off = s.reduceMotion() ? 0 : FeatureMotion.miniReelOffset(t - respin.at(), FeatureMotion.miniReelStop(idx));
					g.enableScissor(x + 1, y + 1, x + cell - 1, y + cell - 1);
					for (int k = -1; k <= 1; k++) {
						int yy = (int) (y + (k + off) * cell);
						if (CabinetArt.ART) SlotSprites.blit(g, SlotSprites.EMBER_BLUR, x, yy, cell, cell);
						else g.fillGradient(x + 6, yy + 6, x + cell - 6, yy + cell - 6, 0x80FF7A1A, 0x40C02010);
					}
					g.disableScissor();
				}
				continue;
			}
			double since = t - landedAt[c];
			double sq = s.reduceMotion() || since > 200 || since < 0 ? 1 : 0.85 + 0.15 * Ease.OUT_BACK.apply(since / 200.0);
			int order = indexOf(readingOrder, n, c);
			boolean lit = col != null && t >= col.at() && FeatureMotion.collectIndex(t - col.at(), n) >= order;
			drawCoin(s, g, x, y, values[c], sq, lit, FeatureMotion.lockFrame(t - in.at() - 200, order));
		}
		drawPips(s, g);
		// banner and reset line
		double introMs = t - in.at();
		if (introMs < 1600) {
			double slide = s.reduceMotion() ? 0 : Math.max(0, 1 - Ease.OUT_CUBIC.apply(Math.min(1, introMs / 300.0)));
			SlotDraw.outlined(g, s.font(), Component.translatable("gui.burmaldaholic.slots.bonus.hold"), s.wx() + s.windowW() / 2f,
				(float) (s.wy() + 10 - slide * 20), 1.5f, 0xFFFFC400, 0xFF2A0808);
		}
		if (respin != null && respin.arg(0) != 0 && t - respin.at() > 500 && t - respin.at() < 900) {
			SlotDraw.centeredFit(g, s.font(), Component.translatable("gui.burmaldaholic.slots.hold.reset"), s.wx() + s.windowW() / 2, s.wy() + s.windowH() - 10,
				s.windowW(), 0xFFFFE070);
		}
		if (col != null && t >= col.at()) drawCollect(s, g, col, n, full);
	}

	private static int indexOf(int[] arr, int n, int v) {
		for (int i = 0; i < n; i++) if (arr[i] == v) return i;
		return 0;
	}

	private void drawCoin(SlotStage s, GuiGraphicsExtractor g, int x, int y, int value, double scale, boolean lit, int lock) {
		int cell = s.cell();
		float cx = x + cell / 2f;
		float cy = y + cell / 2f;
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().scale(1, (float) scale);
		int r = cell / 2 - 7;
		SlotDraw.disc(g, 0, 0, r + 1, 0xFF7A5A10);
		SlotDraw.disc(g, 0, 0, r, lit ? 0xFFFFF0A0 : 0xFFFFC400);
		SlotDraw.disc(g, -2, -2, r / 2, lit ? 0xFFFFFFFF : 0xFFFFE070);
		g.pose().popMatrix();
		// lock frame clasps (3 frames)
		if (CabinetArt.ART) {
			SlotSprites.frame(g, SlotSprites.COIN_LOCK, 44, 44, 3, lock, x, y, cell, cell, lit ? 0xFFFFFFFF : 0xFFE0E0E0);
		}
		int f = 1 + lock;
		int gold = lit ? 0xFFFFFFFF : 0xFFFFD640;
		if (CabinetArt.ART) gold = 0;
		g.fill(x + 2, y + 2, x + 2 + 4 * f, y + 4, gold);
		g.fill(x + 2, y + 2, x + 4, y + 2 + 4 * f, gold);
		g.fill(x + cell - 2 - 4 * f, y + cell - 4, x + cell - 2, y + cell - 2, gold);
		g.fill(x + cell - 4, y + cell - 2 - 4 * f, x + cell - 2, y + cell - 2, gold);
		Component label;
		int color = 0xFF2A1400;
		if (value > 0) {
			label = Texts.number(value * s.bet());
		} else {
			int tier = Math.max(1, Math.min(3, -value));
			label = Component.translatable("gui.burmaldaholic.slots.jackpot.tier." + new String[] {"mini", "minor", "major"}[tier - 1]);
			color = 0xFF180A28;
			SlotDraw.frame(g, x + 1, y + 1, cell - 2, cell - 2, 1, SymbolStyle.JACKPOT_COLORS[tier - 1]);
		}
		SlotDraw.centeredFit(g, s.font(), label, (int) cx, (int) cy - 4, cell - 6, color);
	}

	private void drawPips(SlotStage s, GuiGraphicsExtractor g) {
		int left = respinsLeft(s);
		int cx = s.wx() + s.windowW() - 22;
		int y = s.wy() + 3;
		g.fill(cx - 18, y - 2, cx + 18, y + 8, 0xB0100808);
		for (int i = 0; i < 3; i++) {
			int x = cx - 14 + i * 12;
			if (CabinetArt.ART) SlotSprites.blit(g, i < left ? SlotSprites.PIP_ON : SlotSprites.PIP_OFF, x, y - 1, 8, 8);
			else SlotDraw.disc(g, x + 3, y + 3, 3, i < left ? 0xFFFF7A1A : 0xFF3A2020);
		}
	}

	private void drawCollect(SlotStage s, GuiGraphicsExtractor g, Beat col, int n, boolean full) {
		double ms = s.t() - col.at();
		int idx = FeatureMotion.collectIndex(ms, n);
		if (idx >= 0 && idx != lastCollected) {
			lastCollected = idx;
			if (tick.tryFire(s.now())) SlotSounds.play("slots.rollup_tick", SoundPlan.collect(idx), 0.7f);
		}
		// the value of the coin being collected flies to the bonus total (300 ms arcs)
		if (idx >= 0 && !s.reduceMotion()) {
			int c = readingOrder[idx];
			double u = Math.min(1, (ms - idx * 120) / 300.0);
			int[] target = s.host().featurePanelCenter();
			FeatureMotion.arc(s.cellX(c / 3) + s.cell() / 2.0, s.cellY(c % 3) + s.cell() / 2.0, target[0], target[1], 30, u, pt);
			if (values[c] > 0) {
				SlotDraw.outlined(g, s.font(), Texts.number(values[c] * s.bet()), (float) pt[0], (float) pt[1], 1f, 0xFFFFD640, 0xFF2A1400);
			}
		}
		if (full && ms < 400) {
			if (s.flashes() && ms < 300) g.fill(s.wx(), s.wy(), s.wx() + s.windowW(), s.wy() + s.windowH(), SlotDraw.withAlpha(0xFFFFD640, CelebrationPlan.flash(ms, true, 0.3)));
		}
		if (full) {
			double slam = CelebrationPlan.plateSlam(ms);
			SlotDraw.outlined(g, s.font(), Component.translatable("gui.burmaldaholic.slots.hold.full"), s.wx() + s.windowW() / 2f, s.wy() + s.windowH() / 2f,
				(float) (1.5 * slam), 0xFFFFD640, 0xFF2A0808);
		}
		if (ms > 0 && idx < 0 && ms < col.dur() && !s.reduceMotion() && (int) ms / 200 % 5 == 0) {
			s.particles().burst(ScreenParticles.COIN, s.host().featurePanelCenter()[0], s.host().featurePanelCenter()[1], 1, 0.08f, 0.0004f, 700, s.now());
		}
	}
}
