package dev.nezo.burmaldaholic.games.slots.cabinet;

import dev.nezo.burmaldaholic.core.anim.Ease;
import dev.nezo.burmaldaholic.core.anim.FrameModel;
import dev.nezo.burmaldaholic.core.anim.SeedMix;
import dev.nezo.burmaldaholic.core.anim.Timeline;
import dev.nezo.burmaldaholic.core.anim.WinTier;
import dev.nezo.burmaldaholic.games.slots.cabinet.CabinetSync.Hoard;
import dev.nezo.burmaldaholic.games.slots.cabinet.CabinetSync.Hunt;
import dev.nezo.burmaldaholic.games.slots.cabinet.CabinetSync.Spin;
import dev.nezo.burmaldaholic.games.slots.cabinet.CabinetSync.Tumble;
import dev.nezo.burmaldaholic.games.slots.cabinet.CabinetSync.Wheel;
import java.util.List;

/**
 * PURE frame sampler of the in-world cabinet (slots.md §5.2): reels, landing squash, anticipation glow, win frames,
 * Nether tumbles (explode, gravity fall, {@code ×N}), End expanding sticky eggs, Piglin's Hoard grid, Treasure Hunt
 * board, the Dragon Wheel and the floating tier text — all as a function of the sync and the shared time
 * {@code t} (ms since {@code startTick}). Fidelity: at and after {@link CabinetSync#endMs()} the frame equals
 * {@link #terminal}, whose window is the server's final window (tested).
 *
 * <p>Motion modes: {@link Motion#FULL} (≤ 24 blocks), {@link Motion#REDUCED} ({@code anim.reduceMotion}: no scroll,
 * 300 ms cross-fades, no bounce/squash, static glows), {@link Motion#SETTLED} (far LOD: no tweens, every beat snaps).
 */
public final class CabinetFrames implements FrameModel<CabinetSync, CabinetFrame> {
	public static final CabinetFrames INSTANCE = new CabinetFrames();

	public enum Motion {
		FULL,
		REDUCED,
		SETTLED
	}

	/** Durations at 100 % (slots.md §2.3, §4.5, §4.6, §5.2). */
	public static final int SQUASH_MS = 200;
	public static final int CROSSFADE_MS = 300;
	public static final int WIN_FRAMES_MS = 3000;
	public static final int WIN_BLINK_MS = 250;
	public static final int EXPLODE_MS = 250;
	public static final int FALL_MS = 300;
	public static final int TUMBLE_MS = 750;
	public static final int REEL_FALL_DELAY_MS = 30;
	/** Gravity in rows/ms² (0.0022 px/ms² on a 44 px cell ≈ one row in 200 ms). */
	public static final double GRAVITY = 0.0022 / 44;
	public static final int BOUNCE_MS = 100;
	public static final float BOUNCE_ROWS = 4f / 44f;
	public static final int MULT_TEXT_MS = 1000;
	public static final int TIER_TEXT_MS = 3000;
	public static final int CELEBRATE_MS = 3000;
	public static final int CRACK_MS = 150;
	public static final int EXPAND_START_MS = 270;
	public static final int EXPAND_MS = 300;
	public static final int STICK_MS = 200;
	public static final int STICK_END_MS = EXPAND_START_MS + EXPAND_MS + STICK_MS;
	public static final int HOARD_FADE_MS = 200;
	public static final int HOARD_LAND_MS = 500;
	public static final int HOARD_STAGGER_MS = 30;
	public static final int HOARD_COLLECT_MS = 120;
	public static final int WHEEL_RISE_MS = 700;
	public static final int WHEEL_SINK_MS = 400;
	public static final int WHEEL_UP_MS = 800;
	public static final double WHEEL_IDLE_DEG_PER_MS = 30.0 / 1000.0;
	public static final int WHEEL_TURNS = 3;
	/** The pointer lands inside the central 60 % of the drawn wedge (slots.md §4.10, F4). */
	public static final double WHEEL_SAFE = 0.3;

	private CabinetFrames() {}

	@Override
	public CabinetFrame frame(CabinetSync o, Timeline timeline, double tMs) {
		CabinetFrame f = new CabinetFrame();
		sample(o, tMs, Motion.FULL, f);
		return f;
	}

	@Override
	public CabinetFrame terminal(CabinetSync o) {
		CabinetFrame f = new CabinetFrame();
		sample(o, Math.max(o.endMs(), 0) + 1e9, Motion.FULL, f);
		return f;
	}

	/** Fills {@code out} with the cabinet at {@code t} ms since {@code startTick}. No allocation. */
	public static void sample(CabinetSync s, double t, Motion motion, CabinetFrame out) {
		out.reset();
		out.settled = t >= s.endMs();
		List<Spin> spins = s.spins();
		int idx = -1;
		for (int i = 0; i < spins.size(); i++) if (spins.get(i).startMs() <= t) idx = i;
		boolean reelsMoving = false;
		if (idx < 0) {
			restWindow(s, out);
		} else {
			out.spin = idx;
			reelsMoving = spinFrame(s, idx, t - spins.get(idx).startMs(), motion, out);
		}
		boolean feature = idx > 0;
		feature |= hoardFrame(s, t, motion, out);
		feature |= huntFrame(s, t, out);
		feature |= wheelFrame(s, t, motion, out);
		texts(s, t, motion, out);
		out.marquee = marquee(s, t, reelsMoving, feature, out);
	}

	// ---- reels ------------------------------------------------------------------------------------

	private static void restWindow(CabinetSync s, CabinetFrame out) {
		int[] stops = s.prevStops();
		for (int r = 0; r < CabinetSync.REELS; r++) {
			for (int y = 0; y < 3; y++) out.cell(r, s.symbolAt(r, stops[r] + y), y, 1f, 1f, false);
		}
	}

	private static double k(CabinetSync s, int spinIdx) {
		return 100.0 / s.speedPct() * (spinIdx > 0 ? 0.8 : 1.0);
	}

	/** @return true while any reel of the spin still scrolls */
	private static boolean spinFrame(CabinetSync s, int idx, double ts, Motion motion, CabinetFrame out) {
		Spin sp = s.spins().get(idx);
		int[] prev = idx == 0 ? s.prevStops() : s.spins().get(idx - 1).stops();
		double k = k(s, idx);
		boolean moving = false;
		for (int r = 0; r < CabinetSync.REELS; r++) {
			boolean held = (sp.heldMask() & 1 << r) != 0;
			int stopMs = sp.stopMs()[r];
			if (held || ts >= stopMs) {
				landedReel(s, sp, idx, r, ts, motion, out);
				if (!held && motion == Motion.FULL) out.squash[r] = ReelMotion.squash(ts - stopMs, s.speedPct());
			} else {
				moving = true;
				boolean ant = (sp.anticipationMask() & 1 << r) != 0;
				if (motion == Motion.FULL) {
					double o = ReelMotion.offset(ts, prev[r], sp.stops()[r], stopMs, s.speedPct(), ant);
					boolean blur = ReelMotion.velocity(ts, stopMs, s.speedPct(), ant) > ReelMotion.BLUR_SPEED;
					scrollCells(s, r, o, 1f, blur, out);
				} else {
					double fadeStart = stopMs - CROSSFADE_MS * k;
					if (motion == Motion.REDUCED && ts >= fadeStart) {
						float u = (float) Math.min(1, (ts - fadeStart) / (CROSSFADE_MS * k));
						scrollCells(s, r, prev[r], 1f - u, true, out);
						for (int y = 0; y < 3; y++) out.cell(r, sp.window()[r * 3 + y], y, u, 1f, false);
					} else {
						scrollCells(s, r, prev[r], 1f, ts > 0, out);
					}
				}
				if (ant) out.anticipate[r] = anticipation(sp, r, ts, motion);
			}
		}
		winFrames(s, sp, idx, ts, motion, out);
		sticky(s, sp, ts, motion, out);
		return moving;
	}

	private static void scrollCells(CabinetSync s, int r, double o, float a, boolean blur, CabinetFrame out) {
		int first = (int) Math.floor(o);
		for (int j = first; j <= first + 3; j++) out.cell(r, s.symbolAt(r, j), (float) (j - o), a, 1f, blur);
	}

	/** Anticipation glow: from the previous spinning reel's stop until this reel lands (2 Hz pulse, static when reduced). */
	private static float anticipation(Spin sp, int r, double ts, Motion motion) {
		int from = 0;
		for (int q = 0; q < r; q++) if ((sp.heldMask() & 1 << q) == 0) from = Math.max(from, sp.stopMs()[q]);
		if (ts < from) return 0;
		if (motion != Motion.FULL) return 1f;
		return (float) (0.7 + 0.3 * Math.sin((ts - from) * Math.PI * 2 / 500.0));
	}

	/** A reel that has landed (or is held): the spin window, animated through the tumble chain. */
	private static void landedReel(CabinetSync s, Spin sp, int idx, int r, double ts, Motion motion, CabinetFrame out) {
		List<Tumble> steps = sp.tumbles();
		double k = k(s, idx);
		int step = -1;
		for (int i = 0; i < steps.size(); i++) if (ts >= steps.get(i).atMs()) step = i;
		if (step < 0) {
			for (int y = 0; y < 3; y++) out.cell(r, sp.window()[r * 3 + y], y, 1f, 1f, false);
			return;
		}
		Tumble tu = steps.get(step);
		double te = ts - tu.atMs();
		double explode = EXPLODE_MS * k;
		double fall = FALL_MS * k;
		if (te >= explode + fall + REEL_FALL_DELAY_MS * k * 4 + BOUNCE_MS * k * 2 || motion == Motion.SETTLED && te >= explode) {
			for (int y = 0; y < 3; y++) out.cell(r, tu.cell(r * 3 + y), y, 1f, 1f, false);
			return;
		}
		// window before this step
		int b0;
		int b1;
		int b2;
		if (step == 0) {
			b0 = sp.window()[r * 3];
			b1 = sp.window()[r * 3 + 1];
			b2 = sp.window()[r * 3 + 2];
		} else {
			Tumble p = steps.get(step - 1);
			b0 = p.cell(r * 3);
			b1 = p.cell(r * 3 + 1);
			b2 = p.cell(r * 3 + 2);
		}
		int mask = tu.explodeMask();
		if (te < explode || motion == Motion.REDUCED && te < CROSSFADE_MS * k / 2) {
			double dur = motion == Motion.REDUCED ? CROSSFADE_MS * k / 2 : explode;
			float u = (float) Math.min(1, te / dur);
			for (int y = 0; y < 3; y++) {
				int sym = y == 0 ? b0 : y == 1 ? b1 : b2;
				boolean ex = (mask & 1 << r * 3 + y) != 0;
				if (!ex) {
					out.cell(r, sym, y, 1f, 1f, false);
				} else {
					float in = (float) Ease.IN_CUBIC.apply(u);
					int n = out.count[r];
					out.cell(r, sym, y, 1f - in, motion == Motion.FULL ? 1f + 0.25f * in : 1f, false);
					if (out.count[r] > n) out.burn[r][n] = 1f - u;
				}
			}
			return;
		}
		if (motion == Motion.REDUCED) {
			for (int y = 0; y < 3; y++) out.cell(r, tu.cell(r * 3 + y), y, 1f, 1f, false);
			return;
		}
		// FULL: gravity fall of the survivors, new cells from above
		int m = 0;
		for (int y = 0; y < 3; y++) if ((mask & 1 << r * 3 + y) != 0) m++;
		double tf = te - explode - REEL_FALL_DELAY_MS * k * r;
		int dest = m;
		for (int y = 0; y < 3; y++) {
			if ((mask & 1 << r * 3 + y) != 0) continue;
			int sym = y == 0 ? b0 : y == 1 ? b1 : b2;
			out.cell(r, sym, fallRow(y, dest, tf, k), 1f, 1f, false);
			dest++;
		}
		for (int j = 0; j < m; j++) out.cell(r, tu.cell(r * 3 + j), fallRow(j - m, j, tf, k), 1f, 1f, false);
	}

	/** Row of a falling cell: gravity from {@code from} to {@code to}, clamped, then a single small bounce. */
	static float fallRow(int from, int to, double tf, double k) {
		if (tf <= 0 || from == to) {
			if (from == to) return to;
			return from;
		}
		double g = GRAVITY / (k * k);
		double dist = to - from;
		double land = Math.sqrt(2 * dist / g);
		if (tf < land) return (float) (from + 0.5 * g * tf * tf);
		double dl = tf - land;
		double b = BOUNCE_MS * k;
		if (dl >= b) return to;
		double u = dl / b;
		return (float) (to - BOUNCE_ROWS * Math.sin(Math.PI * u) * (1 - u));
	}

	/** Win frames: the next tumble step's overview, then the final window's wins for 3 s (2 Hz blink). */
	private static void winFrames(CabinetSync s, Spin sp, int idx, double ts, Motion motion, CabinetFrame out) {
		double k = k(s, idx);
		List<Tumble> steps = sp.tumbles();
		double overview = CabinetSync.Builder.STEP_OVERVIEW_MS * k;
		int mask = 0;
		double since = 0;
		for (int i = 0; i < steps.size(); i++) {
			Tumble tu = steps.get(i);
			if (ts >= tu.atMs() - overview && ts < tu.atMs()) {
				mask = tu.explodeMask();
				since = ts - (tu.atMs() - overview);
			}
		}
		if (mask == 0 && sp.winMask() != 0 && ts >= sp.winShowMs() && ts < sp.winShowMs() + WIN_FRAMES_MS * k) {
			mask = sp.winMask();
			since = ts - sp.winShowMs();
		}
		if (mask == 0) return;
		out.winMask = mask;
		out.winOn = motion != Motion.FULL || (long) Math.floor(since / WIN_BLINK_MS) % 2 == 0;
		// non-winning landed cells dim so the wins read from a distance
		for (int r = 0; r < CabinetSync.REELS; r++) {
			for (int i = 0; i < out.count[r]; i++) {
				int row = Math.round(out.y[r][i]);
				if (row >= 0 && row < 3 && (mask & 1 << r * 3 + row) == 0) out.bright[r][i] = 0.55f;
			}
		}
	}

	/** End free spins: held reels show the full egg + chains; newly stuck reels crack, expand, then chain. */
	private static void sticky(CabinetSync s, Spin sp, double ts, Motion motion, CabinetFrame out) {
		int all = sp.stickyAfter() | sp.heldMask();
		if (all == 0) return;
		double k = 100.0 / s.speedPct();
		for (int r = 0; r < CabinetSync.REELS; r++) {
			if ((all & 1 << r) == 0) continue;
			out.eggRow[r] = eggRow(sp, r);
			if ((sp.heldMask() & 1 << r) != 0) {
				out.eggGrow[r] = 1f;
				out.chain[r] = 1f;
				continue;
			}
			double dt = ts - sp.stopMs()[r];
			if (dt < 0) continue;
			if (motion != Motion.FULL) {
				out.eggGrow[r] = 1f;
				out.chain[r] = motion == Motion.SETTLED || dt >= STICK_END_MS * k ? 1f : (float) Math.min(1, dt / (STICK_END_MS * k));
				continue;
			}
			double e0 = EXPAND_START_MS * k;
			double e1 = e0 + EXPAND_MS * k;
			if (dt < e0) {
				out.eggGrow[r] = 1f / 3f;
			} else if (dt < e1) {
				out.eggGrow[r] = (float) (1.0 / 3 + 2.0 / 3 * Ease.outBack(1.4, (dt - e0) / (e1 - e0)));
			} else {
				out.eggGrow[r] = 1f;
				double u = Math.min(1, (dt - e1) / (STICK_MS * k));
				out.chain[r] = (float) u;
				out.chainScale[r] = (float) (1.1 - 0.1 * Ease.OUT_BACK.apply(u));
			}
		}
	}

	/** Row of the wild on reel {@code r} (symbol index 0 = WD in SLOTS.md §2 order); 1 when none is visible. */
	static int eggRow(Spin sp, int r) {
		for (int y = 0; y < 3; y++) if (sp.window()[r * 3 + y] == 0) return y;
		return 1;
	}

	// ---- bonus boards -----------------------------------------------------------------------------

	private static boolean hoardFrame(CabinetSync s, double t, Motion motion, CabinetFrame out) {
		Hoard h = s.hoard();
		if (h == null || t < h.startMs() || t >= h.endMs()) return false;
		double k = 100.0 / s.speedPct();
		out.hoard = true;
		out.hoardFade = motion == Motion.FULL ? (float) Math.min(1, (t - h.startMs()) / (HOARD_FADE_MS * k)) : 1f;
		System.arraycopy(h.values(), 0, out.coinValue, 0, CabinetSync.CELLS);
		int mask = h.initialMask();
		int pips = 3;
		// intro lock pop, 60 ms stagger
		for (int c = 0; c < CabinetSync.CELLS; c++) {
			if ((mask & 1 << c) == 0) continue;
			double dt = t - h.startMs() - HOARD_FADE_MS * k - 60 * k * readingOrder(c);
			if (motion == Motion.FULL && dt >= 0 && dt < 150 * k) out.coinPop[c] = (float) (1.15 - 0.15 * Ease.OUT_QUAD.apply(dt / (150 * k)));
		}
		int[] stepMs = h.stepMs();
		int[] masks = h.stepMasks();
		for (int i = 0; i < stepMs.length; i++) {
			double ds = t - stepMs[i];
			if (ds < 0) break;
			int next = masks[i] | mask;
			double end = (HOARD_LAND_MS + HOARD_STAGGER_MS * 14) * k;
			for (int c = 0; c < CabinetSync.CELLS; c++) {
				if ((mask & 1 << c) != 0) continue;
				double land = (HOARD_LAND_MS + HOARD_STAGGER_MS * readingOrder(c)) * k;
				double start = HOARD_STAGGER_MS * readingOrder(c) * k;
				if (ds < land) {
					if (ds >= start && motion == Motion.FULL) out.cellSpin[c] = (float) ((ds - start) / (60 * k) % 1.0);
					else if (ds >= start) out.cellSpin[c] = 0f;
				} else if ((next & 1 << c) != 0) {
					out.coinMask |= 1 << c;
					double dl = ds - land;
					if (motion == Motion.FULL && dl < 150 * k) out.coinPop[c] = (float) (0.85 + 0.15 * Ease.OUT_BACK.apply(dl / (150 * k)));
				}
			}
			if (ds >= end) {
				pips = next != mask ? 3 : pips - 1;
				mask = next;
			}
		}
		out.coinMask |= mask;
		out.pips = Math.max(0, pips);
		// collect sweep after the last respin
		double collectAt = stepMs.length == 0 ? h.startMs() + 800 * k : stepMs[stepMs.length - 1] + 900 * k;
		if (t >= collectAt) {
			int n = 0;
			for (int ro = 0; ro < CabinetSync.CELLS; ro++) {
				int c = cellOfReadingOrder(ro);
				if ((out.coinMask & 1 << c) == 0) continue;
				double dc = t - collectAt - n * HOARD_COLLECT_MS * k;
				if (dc >= 0) out.coinLit[c] = motion == Motion.FULL ? (float) Math.max(0.35, 1 - dc / (400 * k)) : 1f;
				n++;
			}
		}
		return true;
	}

	/** Reading order (row by row, left to right) of cell {@code reel × 3 + row}. */
	static int readingOrder(int cell) {
		return cell % 3 * 5 + cell / 3;
	}

	static int cellOfReadingOrder(int ro) {
		return ro % 5 * 3 + ro / 5;
	}

	private static boolean huntFrame(CabinetSync s, double t, CabinetFrame out) {
		Hunt h = s.hunt();
		if (h == null || t < h.startMs() || h.endMs() >= 0 && t >= h.endMs()) return false;
		out.hunt = true;
		int[] cells = h.cells();
		int[] entries = h.entries();
		for (int i = 0; i < cells.length; i++) if (cells[i] >= 0 && cells[i] < CabinetSync.CELLS) out.chest[cells[i]] = entries[i];
		return true;
	}

	private static boolean wheelFrame(CabinetSync s, double t, Motion motion, CabinetFrame out) {
		Wheel w = s.wheel();
		if (w == null || t < w.startMs() || t >= w.endMs() + WHEEL_SINK_MS * 100.0 / s.speedPct()) return false;
		double k = 100.0 / s.speedPct();
		if (t < w.endMs()) {
			double u = Math.min(1, (t - w.startMs()) / (WHEEL_RISE_MS * k));
			out.wheelRise = motion == Motion.FULL ? (float) Ease.OUT_BACK.apply(u) : 1f;
		} else {
			double u = (t - w.endMs()) / (WHEEL_SINK_MS * k);
			out.wheelRise = motion == Motion.FULL ? (float) (1 - Ease.IN_CUBIC.apply(u)) : 0f;
			if (out.wheelRise <= 0f) return false;
		}
		int n = w.segments().length;
		int visible = 1;
		for (int i = 1; i < n; i++) if (t >= w.spinMs()[i] - WHEEL_UP_MS * k) visible = i + 1;
		out.ringCount = visible;
		out.activeRing = visible - 1;
		for (int i = 0; i < n; i++) {
			out.ringSize[i] = w.ringSizes()[i];
			out.ringAngle[i] = (float) ringAngle(s, i, t, motion);
			out.ringBright[i] = i == out.activeRing ? 1f : i < out.activeRing ? 0.25f : 0.4f;
		}
		return true;
	}

	/** Final wheel angle of ring {@code i} (degrees; pointer at 0): inside the central 60 % of the landed wedge. */
	public static double ringFinalAngle(CabinetSync s, int i) {
		Wheel w = s.wheel();
		double wedge = 360.0 / w.ringSizes()[i];
		SeedMix.FxRng rng = new SeedMix.FxRng(SeedMix.mix(s.seed(), 0x57EE1, i));
		double off = (rng.nextDouble() * 2 - 1) * WHEEL_SAFE;
		return -(w.segments()[i] * wedge + wedge * (0.5 + off));
	}

	/** Extra spin (0–360°) beyond the full turns, seeded. */
	static double ringExtra(CabinetSync s, int i) {
		SeedMix.FxRng rng = new SeedMix.FxRng(SeedMix.mix(s.seed(), 0x57EE2, i));
		return rng.nextDouble() * 360;
	}

	/** Wedge under the pointer for a wheel angle (degrees). */
	public static int wedgeAt(double angle, int size) {
		double a = ((-angle) % 360 + 360) % 360;
		return (int) Math.floor(a / (360.0 / size)) % size;
	}

	static double ringAngle(CabinetSync s, int i, double t, Motion motion) {
		Wheel w = s.wheel();
		double fin = ringFinalAngle(s, i);
		double start = fin - (WHEEL_TURNS * 360 + ringExtra(s, i));
		double at = w.spinMs()[i];
		double dur = w.spinDurMs()[i];
		if (t >= at + dur || motion == Motion.SETTLED && t >= at) return fin;
		if (t < at) return motion == Motion.FULL ? start - WHEEL_IDLE_DEG_PER_MS * (at - t) : start;
		double u = (t - at) / dur;
		return start + (fin - start) * Ease.OUT_CUBIC.apply(u);
	}

	// ---- texts, marquee ---------------------------------------------------------------------------

	private static void texts(CabinetSync s, double t, Motion motion, CabinetFrame out) {
		if (out.spin >= 0) {
			Spin sp = s.spins().get(out.spin);
			double ts = t - sp.startMs();
			double k = k(s, out.spin);
			for (int i = 0; i < sp.tumbles().size(); i++) {
				Tumble tu = sp.tumbles().get(i);
				double d = ts - tu.atMs();
				if (d >= 0 && d < MULT_TEXT_MS * k) {
					double u = d / (MULT_TEXT_MS * k);
					out.multValue = tu.multiplier();
					out.multRise = motion == Motion.FULL ? (float) Ease.OUT_CUBIC.apply(u) : 0f;
					out.multAlpha = fade(u, 0.1, 0.3);
				}
			}
		}
		boolean show = s.tier().isOverlay() || s.jackpotTier() > 0 || s.maxWin();
		double d = t - s.gateMs();
		out.tier = s.tier();
		out.amount = s.totalChips();
		out.jackpotTier = s.jackpotTier();
		out.maxWin = s.maxWin();
		if (show && d >= 0 && d < TIER_TEXT_MS) {
			double u = d / TIER_TEXT_MS;
			out.tierAlpha = fade(u, 0.05, 0.133);
			out.tierRise = motion == Motion.FULL ? (float) (0.3 * Ease.OUT_CUBIC.apply(u)) : 0f;
		}
	}

	/** Alpha that fades in over the first {@code in} and out over the last {@code outPart} of [0, 1]. */
	static float fade(double u, double in, double outPart) {
		if (u < 0 || u >= 1) return 0f;
		double a = Math.min(1, u / in);
		double b = Math.min(1, (1 - u) / outPart);
		return (float) Math.min(a, b);
	}

	private static Marquee.Pattern marquee(CabinetSync s, double t, boolean reelsMoving, boolean feature, CabinetFrame out) {
		if (out.hunt) return Marquee.Pattern.FEATURE; // player-paced board: the gate comes after it
		double d = t - s.gateMs();
		if (d >= 0 && d < CELEBRATE_MS) {
			if (s.jackpotTier() > 0) return Marquee.Pattern.JACKPOT;
			if (s.tier().isOverlay() || s.maxWin()) return Marquee.Pattern.BIG;
			if (s.tier().ordinal() >= WinTier.WIN.ordinal()) return Marquee.Pattern.WIN;
		}
		if (t >= 0 && t < s.gateMs()) {
			if (feature || out.hoard || out.hunt || out.wheelRise > 0) return Marquee.Pattern.FEATURE;
			if (reelsMoving) return Marquee.Pattern.SPIN;
			if (out.winMask != 0) return Marquee.Pattern.WIN;
		}
		return Marquee.Pattern.IDLE;
	}
}
