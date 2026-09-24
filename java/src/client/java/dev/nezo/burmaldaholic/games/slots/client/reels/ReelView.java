package dev.nezo.burmaldaholic.games.slots.client.reels;

import dev.nezo.burmaldaholic.core.anim.Beat;
import dev.nezo.burmaldaholic.core.anim.SeedMix;
import dev.nezo.burmaldaholic.games.slots.client.SlotSounds;
import dev.nezo.burmaldaholic.games.slots.client.SlotStage;
import dev.nezo.burmaldaholic.games.slots.client.fx.ScreenParticles;
import dev.nezo.burmaldaholic.games.slots.client.fx.SlotDraw;
import dev.nezo.burmaldaholic.games.slots.client.fx.SlotSprites;
import dev.nezo.burmaldaholic.games.slots.client.panels.CabinetArt;
import dev.nezo.burmaldaholic.games.slots.v2.logic.Machine;
import dev.nezo.burmaldaholic.games.slots.v2.logic.MachineDef;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotTimeline;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SymbolRole;
import dev.nezo.burmaldaholic.games.slots.v2.present.ReelMotion;
import dev.nezo.burmaldaholic.games.slots.v2.present.SlotBeats;
import dev.nezo.burmaldaholic.games.slots.v2.present.SlotFrames;
import dev.nezo.burmaldaholic.games.slots.v2.present.SlotScript;
import dev.nezo.burmaldaholic.games.slots.v2.present.SoundPlan;
import dev.nezo.burmaldaholic.games.slots.v2.present.SymbolStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The five reel drums (slots.md §4.1–§4.3, §3.3; JS1 + the anticipation visuals of JS4). Draws what
 * {@link SlotFrames.Sampler} says is visible — the real strip scrolling down and landing exactly on the stop —
 * with blur frames at speed, the landing squash and special-symbol pops, the honest anticipation frame and
 * heartbeat, drum shading, the glass sheen, idle flourishes, the outcome-free pre-roll and the reject path.
 * Reduce motion: no scrolling; each reel cross-fades from blur to the landed window over 300 ms, keeping the
 * stop stagger (anticipation timing still reads).
 */
public final class ReelView {
	private static final int DRUM_SHADE_PX = 14;

	private final boolean[][] stopSounded = new boolean[64][5];
	private SlotSounds.Loop spinLoop;
	private SlotSounds.Loop antLoop;
	private Beat antBeat;
	// pre-roll / reject (before the tape arrives)
	private long preRollStart = -1;
	private long rejectStart = -1;
	private final double[] rejectSpeed = new double[5];
	private final double[] preRollTop = new double[5];
	private final ReelMotion[] preProbe = new ReelMotion[5];
	// idle flourish
	private long nextFlourish;
	private long flourishStart = -1;
	private int flourishCell = -1;

	public void reset(SlotStage s) {
		for (boolean[] a : stopSounded) java.util.Arrays.fill(a, false);
		preRollStart = -1;
		rejectStart = -1;
		antBeat = null;
		flourishStart = -1;
	}

	public void preRoll(SlotStage s, long now) {
		MachineDef def = s.def();
		for (int r = 0; r < 5; r++) {
			int start = s.restStops()[r];
			preProbe[r] = new ReelMotion(120, Integer.MAX_VALUE / 2, 350, false, start, start, def.stripLength(r));
		}
		preRollStart = now;
		rejectStart = -1;
		if (spinLoop == null || spinLoop.released()) spinLoop = SlotSounds.Loop.start("slots.spin_loop", 0.6f, 1f, 150);
	}

	public boolean preRolling() {
		return preRollStart >= 0 || rejectStart >= 0;
	}

	public void reject(long now) {
		if (preRollStart < 0) return;
		double t = now - preRollStart;
		for (int r = 0; r < 5; r++) rejectSpeed[r] = preProbe[r] == null ? 0 : preProbe[r].cruiseVel(t - 30.0 * r);
		preRollStart = -1;
		rejectStart = now;
		if (spinLoop != null) spinLoop.release();
	}

	public void stopLoops() {
		if (spinLoop != null) spinLoop.release();
		if (antLoop != null) antLoop.release();
		spinLoop = null;
		antLoop = null;
	}

	// ---- time ---------------------------------------------------------------------------------------------------

	public void updateIdle(SlotStage s) {
		long now = s.now();
		if (rejectStart >= 0 && now - rejectStart > ReelMotion.REJECT_MS) rejectStart = -1;
		if (s.spinning() || s.reduceMotion()) {
			flourishStart = -1;
			return;
		}
		if (nextFlourish == 0) nextFlourish = now + 4000;
		if (now >= nextFlourish && flourishStart < 0) {
			SeedMix.FxRng rng = new SeedMix.FxRng(SeedMix.mix((int) (now / 1000), s.machine().ordinal()));
			// one visible high or special symbol flourishes (never implies anything: same for every outcome)
			int[] cells = s.restCells();
			int pick = -1;
			for (int tries = 0; tries < 15 && pick < 0; tries++) {
				int c = rng.nextInt(15);
				if (SymbolStyle.flourishes(cells[c])) pick = c;
			}
			flourishCell = pick;
			flourishStart = now;
			nextFlourish = now + 4000 + rng.nextInt(3000);
		}
		if (flourishStart >= 0 && now - flourishStart > 600) flourishStart = -1;
	}

	public void update(SlotStage s, double lastT, double t, boolean jump) {
		preRollStart = -1;
		rejectStart = -1;
		SlotFrames.Sampler f = s.frames();
		if (f.phase < 0) return;
		SlotScript.Phase p = s.script().phase(f.phase);
		double local = t - p.spinUpAt;
		for (int r = 0; r < 5; r++) {
			ReelMotion m = p.reels[r];
			if (m == null) continue;
			// stop click at u ≈ 0.8 of the landing (slots.md §4.2), pitch ladder C D E G A
			double cueAt = m.landAt() + 0.8 * (m.stopMs() - m.landAt());
			int ph = Math.min(stopSounded.length - 1, f.phase);
			if (!stopSounded[ph][r] && local >= cueAt) {
				stopSounded[ph][r] = true;
				if (!jump) SlotSounds.reelStop(r, p.spin > 0 ? 0.8f : 1f);
			}
		}
		// spin loop ends with the last reel (fade 120 ms)
		if (spinLoop != null && t >= p.lastStopMs) {
			spinLoop.release();
			spinLoop = null;
		}
		if (antLoop != null) {
			if (antBeat == null || t >= antBeat.end()) {
				antLoop.release();
				antLoop = null;
				antBeat = null;
			} else {
				antLoop.target(0.7f, SoundPlan.anticipation((t - antBeat.at()) / Math.max(1, antBeat.dur())));
			}
		}
	}

	public void cue(SlotStage s, Beat b) {
		switch (b.kind()) {
			case SlotTimeline.SPIN_UP -> {
				if (spinLoop == null || spinLoop.released()) spinLoop = SlotSounds.Loop.start("slots.spin_loop", 0.6f, 1f, 150);
			}
			case SlotTimeline.ANTICIPATE -> {
				antBeat = b;
				if (antLoop == null) antLoop = SlotSounds.Loop.start("slots.anticipation", 0.7f, 0.8f, 100);
			}
			case SlotTimeline.SYMBOL_LAND -> {
				int r = b.lane();
				int row = b.arg(SlotBeats.SYM_ROW);
				int sym = b.arg(SlotBeats.SYM_SYMBOL);
				SymbolRole role = s.def().roles()[Math.floorMod(sym, s.def().roles().length)];
				boolean anticipated = isAnticipated(s, b.arg(SlotBeats.SPIN), r);
				float cx = s.cellX(r) + s.cell() / 2f;
				float cy = s.cellY(row) + s.cell() / 2f;
				if (role == SymbolRole.SCATTER) {
					SlotSounds.scatterLand(b.arg(SlotBeats.SYM_NTH));
					s.particles().burst(ScreenParticles.SPARKLE, cx, cy, anticipated ? 12 : 6, 0.06f, 0, 500, s.now());
				} else if (role == SymbolRole.BONUS || role == SymbolRole.COIN) {
					SlotSounds.bonusLand(s.machine(), role == SymbolRole.COIN ? 0.6f : 1f);
					s.particles().burst(ScreenParticles.SPARKLE, cx, cy, anticipated ? 12 : 6, 0.06f, 0, 500, s.now());
				}
			}
			default -> {
			}
		}
	}

	private static boolean isAnticipated(SlotStage s, int spin, int reel) {
		for (SlotScript.Phase p : s.script().phases()) if (p.spin == spin) return p.anticipated[reel];
		return false;
	}

	// ---- drawing ------------------------------------------------------------------------------------------------

	public void draw(SlotStage s, GuiGraphicsExtractor g) {
		Machine m = s.machine();
		SymbolStyle.Theme theme = SymbolStyle.theme(m);
		int cell = s.cell();
		double fs = s.freeSpins().themeAmount(s);
		int drumTop = SlotDraw.lerp(theme.drumTop(), SlotDraw.shade(theme.fsSkyBottom(), 1.6), fs * 0.5);
		int drumBottom = SlotDraw.lerp(theme.drumBottom(), theme.fsSkyTop(), fs * 0.5);
		SlotFrames.Sampler f = s.frames();
		for (int r = 0; r < 5; r++) {
			int x = s.cellX(r);
			g.fillGradient(x, s.wy(), x + cell, s.wy() + s.windowH(), drumTop, drumBottom);
		}
		g.enableScissor(s.wx(), s.wy(), s.wx() + s.windowW(), s.wy() + s.windowH());
		if (f == null || f.phase < 0 && !s.active()) {
			drawRest(s, g);
		} else if (f == null || f.phase < 0) {
			drawRest(s, g);
		} else {
			drawSpin(s, g, f);
		}
		g.disableScissor();
	}

	private void drawRest(SlotStage s, GuiGraphicsExtractor g) {
		long now = s.now();
		int cell = s.cell();
		int[] cells = s.restCells();
		for (int r = 0; r < 5; r++) {
			if (preRollStart >= 0 || rejectStart >= 0) {
				drawMovingBeforeTape(s, g, r, now);
				continue;
			}
			for (int y = 0; y < 3; y++) {
				int c = r * 3 + y;
				int col = SymbolSheet.COL_BASE;
				if (c == flourishCell && flourishStart >= 0) col = SymbolSheet.COL_IDLE + Math.min(5, (int) ((now - flourishStart) / 100));
				SymbolSheet.draw(g, s.machine(), cells[c], col, s.cellX(r), s.cellY(y), cell, 1, 1, 1);
			}
		}
		drawStackBrackets(s, g, cells, null, -1);
	}

	/** Outcome-free spin-up / reject: reels scroll from the rest window; no result is shown or implied. */
	private void drawMovingBeforeTape(SlotStage s, GuiGraphicsExtractor g, int r, long now) {
		MachineDef def = s.def();
		int cell = s.cell();
		int start = s.restStops()[r];
		double top;
		boolean blur;
		if (rejectStart >= 0) {
			double u = Math.min(1, (now - rejectStart) / (double) ReelMotion.REJECT_MS);
			top = ReelMotion.rejectTop(start, u, rejectSpeed[r]);
			blur = u < 0.5 && rejectSpeed[r] > ReelMotion.BLUR_SPEED;
		} else {
			ReelMotion probe = preProbe[r];
			double t = now - preRollStart - 30.0 * r;
			top = probe.top(t);
			blur = probe.blurred(t);
		}
		preRollTop[r] = top;
		if (s.reduceMotion()) {
			for (int y = 0; y < 3; y++) SymbolSheet.draw(g, s.machine(), s.restCells()[r * 3 + y], SymbolSheet.COL_BLUR, s.cellX(r), s.cellY(y), cell, 1, 1, 1);
			return;
		}
		int base = (int) Math.floor(top);
		double off = ReelMotion.offset(top);
		for (int k = -1; k <= 3; k++) {
			int idx = base + k;
			int d = idx - start;
			int sym = d >= 0 && d < 3 ? s.restCells()[r * 3 + d] : def.symbolAt(r, Math.floorMod(idx, def.stripLength(r)), 0);
			int y = (int) Math.round(s.wy() + (k - off) * cell);
			SymbolSheet.draw(g, s.machine(), sym, blur ? SymbolSheet.COL_BLUR : SymbolSheet.COL_BASE, s.cellX(r), y, cell, 1, 1, 1);
		}
	}

	private void drawSpin(SlotStage s, GuiGraphicsExtractor g, SlotFrames.Sampler f) {
		SlotScript.Phase p = s.script().phase(f.phase);
		double t = s.t();
		double local = t - p.spinUpAt;
		int cell = s.cell();
		Beat ant = s.script().active(SlotTimeline.ANTICIPATE, t);
		int antReel = ant != null && ant.arg(SlotBeats.SPIN) == p.spin ? ant.lane() : -1;
		for (int r = 0; r < 5; r++) {
			ReelMotion m = p.reels[r];
			int shiver = s.wild().shiver(s, r);
			if (m != null && f.moving[r]) {
				if (s.reduceMotion()) {
					double a = m.crossfade(local);
					for (int y = 0; y < 3; y++) {
						SymbolSheet.draw(g, s.machine(), p.rest[r * 3 + y], SymbolSheet.COL_BLUR, s.cellX(r), s.cellY(y), cell, 1, 1, 1 - a);
						if (a > 0) SymbolSheet.draw(g, s.machine(), p.landed[r * 3 + y], SymbolSheet.COL_BASE, s.cellX(r), s.cellY(y), cell, 1, 1, a);
					}
					continue;
				}
				double top = f.top[r];
				int base = (int) Math.floor(top);
				double off = ReelMotion.offset(top);
				for (int k = -1; k <= 3; k++) {
					int sym = f.movingCell(r, k);
					int y = (int) Math.round(s.wy() + (k - off) * cell);
					SymbolSheet.draw(g, s.machine(), sym, f.blur[r] ? SymbolSheet.COL_BLUR : SymbolSheet.COL_BASE, s.cellX(r), y, cell, 1, 1, 1);
				}
				continue;
			}
			// at rest in this phase
			double since = f.sinceStop[r];
			for (int y = 0; y < 3; y++) {
				int c = r * 3 + y;
				if (s.tumble().hides(s, c)) continue;
				int sym = f.cells[c];
				double sx = 1;
				double sy = 1;
				if (since >= 0 && !s.reduceMotion() && f.fall == null) {
					sy = ReelMotion.squashY(since);
					sx = ReelMotion.squashX(since);
					if (isSpecialLanded(s, p.spin, r, y)) {
						double pop = ReelMotion.landPop(since);
						sx *= pop;
						sy *= pop;
					}
				}
				double heart = antTrigger(s, ant, p, c) ? ReelMotion.heartbeat(t - ant.at()) : 1;
				double pulse = s.winShow().pulse(s, c);
				int col = s.winShow().winning(s, c) ? SymbolSheet.COL_WIN + (int) ((s.now() / 80) % 8) : SymbolSheet.COL_BASE;
				if (antTrigger(s, ant, p, c)) col = SymbolSheet.COL_WIN + (int) ((s.now() / 80) % 8);
				double scale = heart * pulse;
				SymbolSheet.draw(g, s.machine(), sym, col, s.cellX(r) + shiver, s.cellY(y), cell, sx * scale, sy * scale, 1);
				if (antTrigger(s, ant, p, c) && !s.reduceMotion()) {
					SlotDraw.glow(g, s.cellX(r) + 3, s.cellY(y) + 3, cell - 6, cell - 6, SymbolStyle.theme(s.machine()).glow(), 3,
						0.5 + 0.3 * Math.sin(s.now() / 120.0));
				}
			}
			// landed reels dim to 70 % while another reel anticipates
			if (antReel >= 0 && r < antReel) g.fill(s.cellX(r), s.wy(), s.cellX(r) + cell, s.wy() + s.windowH(), 0x4D000000);
		}
		drawStackBrackets(s, g, f.cells, p, f.phase);
		if (antReel >= 0) drawAnticipationFrame(s, g, antReel, ant);
	}

	private static boolean isSpecialLanded(SlotStage s, int spin, int r, int row) {
		for (Beat b : s.beats(SlotTimeline.SYMBOL_LAND)) {
			if (b.lane() == r && b.arg(SlotBeats.SPIN) == spin && b.arg(SlotBeats.SYM_ROW) == row) return true;
		}
		return false;
	}

	/** A trigger symbol already visible while a reel anticipates (the 2 scatters, the chests, the coins). */
	private static boolean antTrigger(SlotStage s, Beat ant, SlotScript.Phase p, int c) {
		if (ant == null || ant.arg(SlotBeats.SPIN) != p.spin) return false;
		int r = c / 3;
		if (r >= ant.lane()) return false;
		int sym = s.frames().cells[c];
		SymbolRole role = s.def().roles()[Math.floorMod(sym, s.def().roles().length)];
		return switch (ant.arg(1)) {
			case 1 -> role == SymbolRole.SCATTER;
			case 2 -> role == SymbolRole.BONUS && (s.def().bonusReelsMask() & (1 << r)) != 0;
			case 3 -> role == SymbolRole.COIN;
			default -> false;
		};
	}

	/** Animated border on the anticipating reel (gold / flame / purple); static outline with reduce motion. */
	private static void drawAnticipationFrame(SlotStage s, GuiGraphicsExtractor g, int reel, Beat ant) {
		int x = s.cellX(reel);
		int y = s.wy();
		int w = s.cell();
		int h = s.windowH();
		int color = SymbolStyle.theme(s.machine()).glow();
		if (s.reduceMotion() || !s.flashes()) {
			SlotDraw.frame(g, x, y, w, h, 2, SlotDraw.withAlpha(0xFFFFD640, 0.9));
			return;
		}
		if (CabinetArt.ART && CabinetArt.ANIMATED_STRIPS) {
			// generated 48 × 140 animated frame (8 frames, frametime 1)
			SlotSprites.blit(g, SlotSprites.machine(s.machine(), "anticipation"),
				x - 2, y - 4, w + 4, h + 8);
			return;
		}
		double pulse = 0.55 + 0.45 * Math.sin(s.now() / 90.0);
		SlotDraw.glow(g, x + 1, y + 1, w - 2, h - 2, color, 4, pulse);
		SlotDraw.frame(g, x, y, w, h, 2, SlotDraw.withAlpha(color, 0.9));
		// chasing sparks along the frame
		double per = 2.0 * (w + h);
		for (int i = 0; i < 4; i++) {
			double d = ((s.now() * 0.25) + i * per / 4) % per;
			int px;
			int py;
			if (d < w) {
				px = (int) (x + d);
				py = y;
			} else if (d < w + h) {
				px = x + w - 1;
				py = (int) (y + d - w);
			} else if (d < 2 * w + h) {
				px = (int) (x + w - (d - w - h));
				py = y + h - 1;
			} else {
				px = x;
				py = (int) (y + h - (d - 2 * w - h));
			}
			g.fill(px - 1, py - 1, px + 2, py + 2, 0xFFFFFFFF);
		}
	}

	/** Overworld: a fully visible 2-high Totem stack gets its gold bracket (fades in 150 ms after the stop). */
	private static void drawStackBrackets(SlotStage s, GuiGraphicsExtractor g, int[] cells, SlotScript.Phase p, int phase) {
		if (s.machine() != Machine.OVERWORLD) return;
		int wild = 0;
		for (int r = 1; r <= 3; r++) {
			double since = p == null ? 1000 : s.frames().sinceStop[r];
			if (p != null && (s.frames().moving[r] || since < 0)) continue;
			for (int y = 0; y < 2; y++) {
				if (cells[r * 3 + y] != wild || cells[r * 3 + y + 1] != wild) continue;
				double a = Math.min(1, since / 150.0);
				if (CabinetArt.ART && CabinetArt.ANIMATED_STRIPS) {
					SlotSprites.blit(g, SlotSprites.STACK_BRACKET,
						s.cellX(r), s.cellY(y) + 2, s.cell(), s.cell() * 2 + 4, SlotDraw.withAlpha(0xFFFFFFFF, a));
					break;
				}
				int x = s.cellX(r) + 1;
				int top = s.cellY(y) + 2;
				int h = s.cell() * 2 - 4;
				int color = SlotDraw.withAlpha(0xFFFFD640, a * (0.75 + 0.25 * Math.sin(s.now() / 200.0)));
				g.fill(x, top, x + 2, top + h, color);
				g.fill(x + s.cell() - 4, top, x + s.cell() - 2, top + h, color);
				g.fill(x, top, x + 6, top + 2, color);
				g.fill(x + s.cell() - 8, top, x + s.cell() - 2, top + 2, color);
				g.fill(x, top + h - 2, x + 6, top + h, color);
				g.fill(x + s.cell() - 8, top + h - 2, x + s.cell() - 2, top + h, color);
				break;
			}
		}
	}

	/** Drum shading: dark gradients top and bottom, a left highlight and dark dividers (research §2.4). */
	public void drawShading(SlotStage s, GuiGraphicsExtractor g) {
		int cell = s.cell();
		int shade = cell < 40 ? 10 : DRUM_SHADE_PX;
		for (int r = 0; r < 5; r++) {
			int x = s.cellX(r);
			g.fillGradient(x, s.wy(), x + cell, s.wy() + shade, 0xB0000000, 0x00000000);
			g.fillGradient(x, s.wy() + s.windowH() - shade, x + cell, s.wy() + s.windowH(), 0x00000000, 0xB0000000);
			g.fill(x, s.wy(), x + 1, s.wy() + s.windowH(), 0x4DFFFFFF);
			if (r > 0) g.fill(x - 1, s.wy(), x, s.wy() + s.windowH(), 0xFF140C1C);
		}
	}

	/** Reel glass: a diagonal 8 % sheen band that slides across every 8 s; the 20 s idle attract shimmer. */
	public void drawGlass(SlotStage s, GuiGraphicsExtractor g) {
		if (s.reduceMotion()) return;
		long now = s.now();
		double u = (now % 8000) / 1200.0;
		boolean attract = !s.spinning() && s.idleMs() > 20_000;
		double a = attract ? 0.16 : 0.08;
		if (u > 1 && !attract) return;
		if (attract) u = (s.idleMs() % 6000) / 600.0;
		if (u > 1) return;
		int w = s.windowW();
		int h = s.windowH();
		int bx = (int) (s.wx() - 40 + (w + 80) * u);
		g.enableScissor(s.wx(), s.wy(), s.wx() + w, s.wy() + h);
		for (int i = 0; i < 12; i++) SlotDraw.line(g, bx + i, s.wy() + h + 4, bx + i + h / 2.0, s.wy() - 4, 1, SlotDraw.withAlpha(0xFFFFFFFF, a));
		g.disableScissor();
	}

	/** Top strip index per reel of the pre-roll (for tests). */
	public double preRollTop(int r) {
		return preRollTop[r];
	}
}
