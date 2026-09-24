package dev.nezo.burmaldaholic.games.slots.client.features;

import dev.nezo.burmaldaholic.core.anim.Beat;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.slots.client.SlotSounds;
import dev.nezo.burmaldaholic.games.slots.client.SlotStage;
import dev.nezo.burmaldaholic.games.slots.client.fx.ScreenParticles;
import dev.nezo.burmaldaholic.games.slots.client.fx.SlotDraw;
import dev.nezo.burmaldaholic.games.slots.client.fx.SlotSprites;
import dev.nezo.burmaldaholic.games.slots.client.panels.CabinetArt;
import dev.nezo.burmaldaholic.games.slots.client.reels.SymbolSheet;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotTimeline;
import dev.nezo.burmaldaholic.games.slots.v2.present.FeatureMotion;
import dev.nezo.burmaldaholic.games.slots.v2.present.SlotBeats;
import dev.nezo.burmaldaholic.games.slots.v2.present.SlotFrames;
import dev.nezo.burmaldaholic.games.slots.v2.present.SlotScript;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Nether tumbles (slots.md §4.5; JS5): winning cells burn away (scale 1 → 1.25, fade, a flame eating the symbol from
 * the bottom, embers), the step amount floats up in the ladder colour, the survivors fall with gravity and one small
 * bounce, new symbols drop in from above (30 ms per reel, lower cells first), and the ladder plate steps up with a
 * pop. The cells always end on the chain's window from the beat (F2); reduce motion: fade out 150 ms, new window
 * appears.
 */
public final class TumbleView {
	private final int[] newRow = new int[15];
	private final double[] startOffset = new double[15];
	private final int[] order = new int[15];

	public void reset() {}

	public void cue(SlotStage s, Beat b) {
		switch (b.kind()) {
			case SlotTimeline.TUMBLE_EXPLODE -> {
				SlotSounds.tumble();
				int mask = b.arg(SlotBeats.TUMBLE_MASK);
				int spawned = 0;
				for (int c = 0; c < 15 && spawned < 40; c++) {
					if ((mask & (1 << c)) == 0) continue;
					s.particles().rise(ScreenParticles.EMBER, s.cellX(c / 3) + s.cell() / 2f, s.cellY(c % 3) + s.cell() * 0.7f, s.cell() * 0.6f, 4, s.now());
					spawned += 4;
				}
			}
			case SlotTimeline.MULT_UP -> SlotSounds.multUp(b.arg(SlotBeats.MULT_INDEX) - 1);
			case SlotTimeline.TUMBLE_FALL -> {
			}
			default -> {
			}
		}
	}

	private static Beat explodeFor(SlotStage s, int spin, int step) {
		for (Beat b : s.beats(SlotTimeline.TUMBLE_EXPLODE)) if (b.arg(SlotBeats.SPIN) == spin && b.arg(SlotBeats.TUMBLE_STEP) == step) return b;
		return null;
	}

	/** Cells the reel view must not draw because this view animates them. */
	public boolean hides(SlotStage s, int cell) {
		SlotFrames.Sampler f = s.frames();
		if (f == null) return false;
		if (f.fall != null) return true;
		Beat ex = s.script().active(SlotTimeline.TUMBLE_EXPLODE, s.t());
		return ex != null && ex.arg(SlotBeats.SPIN) == f.spin && (ex.arg(SlotBeats.TUMBLE_MASK) & (1 << cell)) != 0;
	}

	public void draw(SlotStage s, GuiGraphicsExtractor g) {
		SlotFrames.Sampler f = s.frames();
		if (f == null || f.phase < 0) return;
		double t = s.t();
		int cell = s.cell();
		Beat ex = s.script().active(SlotTimeline.TUMBLE_EXPLODE, t);
		if (ex != null && ex.arg(SlotBeats.SPIN) == f.spin && f.fall == null) {
			double ms = t - ex.at();
			int mask = ex.arg(SlotBeats.TUMBLE_MASK);
			g.enableScissor(s.wx(), s.wy(), s.wx() + s.windowW(), s.wy() + s.windowH());
			for (int c = 0; c < 15; c++) {
				if ((mask & (1 << c)) == 0) continue;
				int x = s.cellX(c / 3);
				int y = s.cellY(c % 3);
				double a = FeatureMotion.explodeAlpha(ms, s.reduceMotion());
				double sc = s.reduceMotion() ? 1 : FeatureMotion.explodeScale(ms);
				SymbolSheet.draw(g, s.machine(), f.cells[c], SymbolSheet.COL_WIN, x, y, cell, sc, sc, a);
				if (!s.reduceMotion()) {
					// flame eats the symbol from the bottom (5 frames)
					int frame = FeatureMotion.burnFrame(ms);
					if (CabinetArt.ART) {
						SlotSprites.frame(g, SlotSprites.BURN, 44, 44, 5, frame, x, y,
							cell, cell, s.flashes() ? 0xFFFFFFFF : 0xC0FFFFFF);
					} else {
						int h = cell * (frame + 1) / 5;
						g.fillGradient(x + 2, y + cell - h, x + cell - 2, y + cell, s.flashes() ? 0xA0FFB030 : 0x80FF7A1A, 0xE0C02010);
					}
				}
			}
			g.disableScissor();
		}
		if (f.fall != null) drawFall(s, g, f, t);
	}

	private void drawFall(SlotStage s, GuiGraphicsExtractor g, SlotFrames.Sampler f, double t) {
		Beat fall = f.fall;
		int cell = s.cell();
		double ms = t - fall.at();
		Beat ex = explodeFor(s, fall.arg(SlotBeats.SPIN), fall.arg(SlotBeats.TUMBLE_STEP));
		int mask = ex == null ? 0 : ex.arg(SlotBeats.TUMBLE_MASK);
		g.enableScissor(s.wx(), s.wy(), s.wx() + s.windowW(), s.wy() + s.windowH());
		for (int r = 0; r < 5; r++) {
			// survivors keep their order and fall; m new cells enter from above
			int m = 0;
			for (int y = 0; y < 3; y++) if ((mask & (1 << (r * 3 + y))) != 0) m++;
			int n = 0;
			for (int y = 0; y < 3; y++) {
				if ((mask & (1 << (r * 3 + y))) != 0) continue;
				int ny = m + n++;
				startOffset[r * 3 + ny] = (ny - y) * (double) cell;
			}
			for (int i = 0; i < m; i++) startOffset[r * 3 + i] = m * (double) cell;
			for (int y = 0; y < 3; y++) {
				newRow[r * 3 + y] = y;
				order[r * 3 + y] = 2 - y;
			}
			for (int y = 0; y < 3; y++) {
				int c = r * 3 + y;
				int sym = f.cells[c];
				double dist = startOffset[c];
				double off = s.reduceMotion() ? 0 : FeatureMotion.fallOffset(ms, r, order[c], dist);
				if (s.reduceMotion() && y < m && ms < 150) continue;
				SymbolSheet.draw(g, s.machine(), sym, SymbolSheet.COL_BASE, s.cellX(r), (int) Math.round(s.cellY(y) - off), cell, 1, 1, 1);
			}
		}
		g.disableScissor();
	}

	/** The step amount floating up from the cluster ("+48 ×2"), above the reels. */
	public void drawFloats(SlotStage s, GuiGraphicsExtractor g) {
		if (s.script() == null) return;
		double t = s.t();
		for (Beat ex : s.beats(SlotTimeline.TUMBLE_EXPLODE)) {
			double ms = t - ex.at();
			if (ms < 0 || ms > 600) continue;
			int mask = ex.arg(SlotBeats.TUMBLE_MASK);
			double sx = 0;
			double sy = 0;
			int n = 0;
			for (int c = 0; c < 15; c++) {
				if ((mask & (1 << c)) == 0) continue;
				sx += s.cellX(c / 3) + s.cell() / 2.0;
				sy += s.cellY(c % 3) + s.cell() / 2.0;
				n++;
			}
			if (n == 0) continue;
			Component text = Component.translatable("gui.burmaldaholic.slots.fx.step_win", Texts.number(ex.arg(SlotBeats.TUMBLE_PAY)),
				Texts.number(ex.arg(SlotBeats.TUMBLE_MULT)));
			double rise = s.reduceMotion() ? 0 : FeatureMotion.floatRise(ms);
			SlotDraw.outlined(g, s.font(), text, (float) (sx / n), (float) (sy / n - rise), 1.5f, SlotDraw.withAlpha(0xFFFFB030, FeatureMotion.floatAlpha(ms)),
				SlotDraw.withAlpha(0xFF2A0808, FeatureMotion.floatAlpha(ms)));
		}
	}

	/** Ladder value lit now (the latest MULT_UP of the running phase, else the first rung). */
	public int multiplier(SlotStage s) {
		SlotFrames.Sampler f = s.frames();
		if (f == null || f.phase < 0) return ladder(s, false)[0];
		SlotScript.Phase p = s.script().phase(f.phase);
		Beat m = s.script().latest(SlotTimeline.MULT_UP, p.spin, s.t());
		int[] ladder = ladder(s, p.spin > 0);
		return m == null ? ladder[0] : m.arg(SlotBeats.MULT_VALUE);
	}

	/** Index of the lit plate and ms since it lit (for the plate pop). */
	public double plateSince(SlotStage s) {
		SlotFrames.Sampler f = s.frames();
		if (f == null || f.phase < 0) return 10_000;
		Beat m = s.script().latest(SlotTimeline.MULT_UP, s.script().phase(f.phase).spin, s.t());
		return m == null ? 10_000 : s.t() - m.at();
	}

	public boolean inFreeSpins(SlotStage s) {
		SlotFrames.Sampler f = s.frames();
		return f != null && f.spin > 0;
	}

	public static int[] ladder(SlotStage s, boolean free) {
		int[] l = free ? s.def().ladderFree() : s.def().ladder();
		return l.length == 0 ? new int[] {1} : l;
	}
}
