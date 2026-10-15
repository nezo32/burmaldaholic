package dev.nezo.burmaldaholic.games.extras.client;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.client.fx.motion.MotionFx;
import dev.nezo.burmaldaholic.client.fx.motion.SnapDraw;
import dev.nezo.burmaldaholic.client.table.fx.TableGfx;
import dev.nezo.burmaldaholic.client.table.fx.dice.DiceAudio;
import dev.nezo.burmaldaholic.client.table.fx.dice.DiceDraw;
import dev.nezo.burmaldaholic.client.table.fx.dice.DieAtlas;
import dev.nezo.burmaldaholic.client.ui.v2.ChipStack;
import dev.nezo.burmaldaholic.client.ui.v2.V2Draw;
import dev.nezo.burmaldaholic.core.anim.Ease;
import dev.nezo.burmaldaholic.core.anim.SeedMix;
import dev.nezo.burmaldaholic.core.anim.dice.CubeGroup;
import dev.nezo.burmaldaholic.core.anim.dice.DiceEvent;
import dev.nezo.burmaldaholic.core.anim.dice.DiceRoll;
import dev.nezo.burmaldaholic.core.anim.dice.DiceRollCache;
import dev.nezo.burmaldaholic.core.anim.dice.DiceSolver;
import dev.nezo.burmaldaholic.core.anim.dice.DiceTray;
import dev.nezo.burmaldaholic.core.anim.dice.DieAtlasTable;
import dev.nezo.burmaldaholic.core.anim.dice.DieSample;
import dev.nezo.burmaldaholic.core.anim.dice.DuelTimeline;
import dev.nezo.burmaldaholic.core.anim.dice.Quat;
import dev.nezo.burmaldaholic.core.anim.dice.Shake;
import dev.nezo.burmaldaholic.core.anim.dice.ThrowSpec;
import dev.nezo.burmaldaholic.core.anim.motion.EventCursor;
import dev.nezo.burmaldaholic.core.anim.motion.MotionEvent;
import dev.nezo.burmaldaholic.core.anim.motion.MotionRng;
import dev.nezo.burmaldaholic.core.anim.motion.Oscillation;
import dev.nezo.burmaldaholic.core.anim.motion.PixelSnap;
import dev.nezo.burmaldaholic.core.anim.motion.StageMap;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.games.extras.present.DiceStage;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Everything that moves in the Dice Duel arena (DI §3–§5, §9; PHASE2B §4 DCb), drawn in ART px inside the
 * {@link DiceStage} pose (origin snapped, scale {@code n/k}); moving things are snapped to physical px ({@code 1/n} art
 * px). Rolls come from {@link DiceSolver} (contacts first, faces only by relabelling) via a {@link DiceRollCache}, drawn
 * with the cell-32 orientation atlas ({@link DiceDraw}); the cups follow {@link DuelTimeline.Cup} (shake, wind-up, flick,
 * pour hold, recover); sounds come from the roll's events ({@link DiceAudio}) and the cups' rattle; dust, sparks and rail
 * glints are pure functions of the events (no particle state, identical at any frame rate).
 *
 * <p>Layers (DI §4.3, arena-local): arena (the dealer half shakes under the house stamp), lane glow, vs badge, shadows,
 * dice still behind their cup (pouring out / swept in), cups, dice by depth, pip count-up, dust and sparks, plaques,
 * stamp, tie banner, chips. Reduced motion (DI §9): static cups, dice fade in at rest 300 ms after their throw, pips lit
 * at once, static outline / dim at the compare, the stamp placed, the sweep a fade.
 */
final class DiceArena {
	/** Atlas cell of a duel die (32 art px). */
	static final int CELL = 32;
	static final int FRAME = DieAtlasTable.FRAME[DieAtlasTable.cellIndex(CELL)];
	/** Half-edge of the die art in px (the atlas raster: cell − 2 border). */
	static final double HP = (CELL - 2) / 2.0;
	static final double PX = DiceTray.DUEL_PX;
	/** Tray → arena art px; the right lane of a PvP opponent is the left lane mirrored. */
	static final StageMap LEFT = StageMap.of(0, 0, PX, PX, PX * DiceDraw.LIFT);
	static final StageMap MIRROR = LEFT.mirrored(DiceTray.DUEL_W);
	/** Cup sprite (40 × 40, 6 frames: rest, shake L, shake R, up, tip 20°, tip 35°) and where it stands. */
	static final int CUP = 40, CUP_X = 8, CUP_Y = 38;
	private static final int[] CUP_FRAME = {0, 1, 1, 2, 2, 3, 4, 5, 5, 0};
	/** Lane boxes of the arena art (outline px), plaques on the front rail, the pot. */
	static final int LANE_X0 = 40, LANE_X1 = 163, LANE_Y0 = 58, LANE_Y1 = 114;
	static final int PLAQUE_W = 32, PLAQUE_H = 24, PLAQUE_Y = 113;
	static final int POT_X = 180, POT_Y = 126;
	private static final Identifier CUP_TEX = TableGfx.spriteFile("extras/dice_duel_cup");
	private static final Identifier GLINT = TableGfx.sprite("core/dice/rest_glint_32");
	private static final Identifier DUST = TableGfx.spriteFile("core/dice/dust");
	private static final Identifier SPARK = TableGfx.spriteFile("core/dice/spark");
	private static final double CP = Math.cos(Math.toRadians(DieAtlasTable.PITCH_DEG));
	private static final double SP = Math.sin(Math.toRadians(DieAtlasTable.PITCH_DEG));
	private static final int GOLD = 0xFFFFC83C, GOLD_HI = 0xFFFFF0A0, GOLD_RIM = 0xFFC88A1C;
	/** Loser tint (DI §5.2). */
	static final int DIM = 0xFF9A90A8;
	/** After-image on the big duel die: only a fast spin, faint (the 32-px die reads as a box at the kit's 0.35). */
	static final double AFTER_OMEGA = 13, AFTER_ALPHA = 0.2;

	private final DiceRollCache cache = new DiceRollCache(16);
	private final DiceDraw.Scratch scratch = new DiceDraw.Scratch();
	private final DieSample s = new DieSample();
	private final DieSample t = new DieSample();
	private final double[] vq = new double[4];
	private final double[] yawQ = new double[4];
	private final double[] pt = new double[3];
	private final DiceAudio[] audio = {DiceAudio.gui(), DiceAudio.gui().volume(0.8)};
	private final DiceAudio[] rattleAudio = {DiceAudio.gui(), DiceAudio.gui().volume(0.7)};
	private EventCursor[] cursors = new EventCursor[2];
	private EventCursor[] rattles = new EventCursor[2];
	private Show cursorShow;
	private int cursorRound = -1;
	private double rattleUntil = Double.NaN;
	private int beats;

	/** One result on the arena (a house roll or a PvP duel), with its solved rolls. */
	static final class Show {
		final int seq;
		final int seed;
		final boolean pvp;
		/** PvP: game tick of the duel start (the shared clock); house: unused. */
		final long start;
		/** PvP: this player's side (0 = A, 1 = B); house: 0. */
		final int me;
		final List<int[][]> rounds;
		final String outcome;
		final long net;
		final boolean pawn;
		final DiceRoll[][] rolls;
		final StageMap[] maps = new StageMap[2];
		final Shake[] shakes = new Shake[2];
		/** Round clock 0 on the shake clock ({@link DuelTimeline#SHAKE_LEAD} for PvP; house: set when the result is in). */
		double offset;
		/** Per [round][order][die]: roll ms when the die clears its cup (lazily computed, NaN = not yet). */
		final double[][][] emerge;

		Show(int seq, int seed, boolean pvp, long start, int me, List<int[][]> rounds, String outcome, long net, boolean pawn, DiceRollCache cache) {
			this.seq = seq;
			this.seed = seed;
			this.pvp = pvp;
			this.start = start;
			this.me = me;
			this.rounds = List.copyOf(rounds);
			this.outcome = outcome;
			this.net = net;
			this.pawn = pawn;
			this.offset = pvp ? DuelTimeline.SHAKE_LEAD : Double.NaN;
			int n = this.rounds.size();
			this.rolls = new DiceRoll[n][2];
			this.emerge = new double[n][2][2];
			for (int i = 0; i < n; i++) {
				int[][] rd = this.rounds.get(i);
				for (int order = 0; order < 2; order++) {
					int sideAB = pvp ? (order == 0 ? me : 1 - me) : order;
					// house: the dealer throws in the right lane (its own tray); PvP: every roll is a left-lane roll keyed by the
					// PLAYER side and the opponent's is shown mirrored, so A's dice match on both screens and in the world
					DiceTray.Side lane = !pvp && order == 1 ? DiceTray.Side.RIGHT : DiceTray.Side.LEFT;
					int rs = DuelTimeline.rollSeed(seed, i, sideAB);
					int[] faces = {clamp(rd[order][0]), clamp(rd[order][1])};
					long key = DiceRollCache.key(seq * 4 + i, lane.ordinal() * 2 + sideAB, rs ^ (faces[0] * 7 + faces[1]));
					rolls[i][order] = cache.get(key, () -> DiceSolver.solve(DiceTray.duelLane(lane), ThrowSpec.cup(lane), rs, faces, DuelTimeline.T_DUEL));
					emerge[i][order][0] = Double.NaN;
					emerge[i][order][1] = Double.NaN;
				}
			}
			maps[0] = LEFT;
			maps[1] = pvp ? MIRROR : LEFT;
			for (int order = 0; order < 2; order++) {
				int sideAB = pvp ? (order == 0 ? me : 1 - me) : order;
				shakes[order] = Shake.of(SeedMix.mix(seed, 0x435550, sideAB), order == 1);
			}
		}

		int roundCount() {
			return rounds.size();
		}

		boolean last(int round) {
			return round >= rounds.size() - 1;
		}

		int[] faces(int round, int order) {
			return rounds.get(round)[order];
		}

		int total(int round, int order) {
			int[] f = faces(round, order);
			return f[0] + f[1];
		}

		/** Which pair won the duel (screen order), −1 for none (push / refund). */
		int winner() {
			return switch (outcome) {
				case "win" -> 0;
				case "lose", "house_tie" -> 1;
				default -> -1;
			};
		}

		/** Rest of pair {@code order} in round {@code round} (round clock). */
		double land(int round, int order) {
			return DuelTimeline.throwAt(0, order) + rolls[round][order].restMs();
		}

		DuelTimeline.Cup cup(int round, int order) {
			double windUp = Double.isNaN(offset) ? Double.POSITIVE_INFINITY : DuelTimeline.windUp(order, offset);
			return new DuelTimeline.Cup(shakes[order], windUp, rolls[round][order].releaseMs(1));
		}
	}

	DiceRollCache cache() {
		return cache;
	}

	private static int clamp(int f) {
		return Math.max(1, Math.min(6, f));
	}

	/** Everything the arena needs for one frame. */
	record Frame(DiceStage stage, Font font, boolean reduced, long nowMs) {}

	// ---- one frame ---------------------------------------------------------------------------------------------------

	/**
	 * Draws the arena (inside the stage pose: art px) for a shown result at duel ms {@code t} (or idle / waiting with
	 * {@code show == null}). {@code waitShakeMs}: house only, ms of shaking since Roll while the result is not in (NaN
	 * otherwise). {@code prev}: the previous result being swept into the cups ({@code prevSweep} 0..1, NaN = none).
	 */
	void draw(GuiGraphicsExtractor g, Frame f, Show show, double t, double waitShakeMs, Show prev, double prevSweep, String theme) {
		int n = f.stage().n();
		boolean reduced = f.reduced();
		int round = show == null ? 0 : DuelTimeline.roundAt(t, show.roundCount());
		double rt = show == null ? -1 : t - DuelTimeline.roundStart(round);
		boolean last = show != null && show.last(round);
		boolean compared = show != null && rt >= DuelTimeline.COMPARE;
		int winner = show != null && last && compared ? show.winner() : -1;
		boolean houseTie = show != null && last && compared && "house_tie".equals(show.outcome);
		// house stamp impact shakes the dealer half (DI §5.2)
		double stampT = houseTie ? rt - DuelTimeline.COMPARE - stampImpact() : -1;
		double half = stampT >= 0 && !reduced && MotionFx.shake("dice_stamp") ? PixelSnap.snap(Oscillation.shake(2, 60, 22, stampT), n) : 0;

		arena(g, theme, half, n);
		if (winner >= 0) laneGlow(g, winner, rt - DuelTimeline.COMPARE, reduced);
		vs(g, show, round, rt, winner, compared && last);

		// cups and the dice: shake clock of this round
		double shakeMs = show == null || Double.isNaN(show.offset) ? waitShakeMs : rt + show.offset;
		DuelTimeline.Cup[] cups = new DuelTimeline.Cup[2];
		for (int order = 0; order < 2; order++) {
			if (show != null && !Double.isNaN(show.offset)) cups[order] = show.cup(round, order);
		}
		// shadows first (all dice)
		if (prev != null && !Double.isNaN(prevSweep)) drawSwept(g, prev, prevSweep, n, reduced, true);
		if (show != null && !Double.isNaN(show.offset)) {
			for (int order = 0; order < 2; order++) for (int d = 0; d < 2; d++) die(g, f, show, round, order, d, rt, winner, half, Pass.SHADOW);
			for (int order = 0; order < 2; order++) for (int d = 0; d < 2; d++) die(g, f, show, round, order, d, rt, winner, half, Pass.BEHIND);
		}
		if (prev != null && !Double.isNaN(prevSweep)) drawSwept(g, prev, prevSweep, n, reduced, false);
		for (int order = 0; order < 2; order++) {
			Shake shake = show != null ? show.shakes[order] : Shake.of(order, order == 1);
			DuelTimeline.Cup cup = cups[order] != null ? cups[order] : new DuelTimeline.Cup(shake, Double.POSITIVE_INFINITY, 0);
			double ms = Double.isNaN(shakeMs) ? 0 : shakeMs;
			cup(g, order, cup, reduced ? 0 : ms, n, order == 1 ? half : 0);
		}
		if (show != null && !Double.isNaN(show.offset)) {
			// dice by depth: the die further down the screen (nearer the viewer) last
			int[] ord = depthOrder(show, round, rt);
			for (int i : ord) die(g, f, show, round, i >> 1, i & 1, rt, winner, half, Pass.FRONT);
			for (int order = 0; order < 2; order++) particles(g, show, round, order, rt, reduced, n, order == 1 ? half : 0);
			for (int order = 0; order < 2; order++) plaque(g, f, show, round, order, rt, winner, order == 1 ? half : 0);
			if (houseTie) stamp(g, rt - DuelTimeline.COMPARE, reduced, n);
			if (!last && compared && rt < DuelTimeline.COMPARE + DuelTimeline.TIE_BANNER_MS) tieBanner(g, f, round, show.roundCount(), rt - DuelTimeline.COMPARE);
			if (last) chips(g, show, rt, reduced);
			else if (rt < DuelTimeline.COMPARE) ChipStack.draw(g, Box.centredAt(POT_X, POT_Y, 16, 16), Math.max(1, Math.abs(show.net)));
		}
	}

	// ---- statics -----------------------------------------------------------------------------------------------------

	private void arena(GuiGraphicsExtractor g, String theme, double half, int n) {
		Identifier tex = TableGfx.sheet("extras/dice_duel_arena_" + theme);
		if (half == 0) {
			TableGfx.region(g, tex, DiceStage.W, DiceStage.H, 0, 0, DiceStage.W, DiceStage.H, 0, 0, 0xFFFFFFFF);
			return;
		}
		int h = DiceStage.W / 2;
		TableGfx.region(g, tex, DiceStage.W, DiceStage.H, 0, 0, h, DiceStage.H, 0, 0, 0xFFFFFFFF);
		SnapDraw.regionF(g, tex, DiceStage.W, DiceStage.H, h, 0, h, DiceStage.H, h + half, 0, 0xFFFFFFFF, n);
	}

	/** A gold pulse on the winner's lane outline (static with reduced motion). */
	private void laneGlow(GuiGraphicsExtractor g, int order, double since, boolean reduced) {
		if (since < 0) return;
		double in = Math.min(1, since / 200);
		double pulse = reduced || !MotionFx.motion("dice_lane_glow") ? 0.75 : 0.6 + 0.3 * Math.sin(since / 1000 * Math.PI * 2 * 1.2);
		int a = (int) Math.round(255 * in * pulse);
		int c = (a << 24) | (GOLD & 0xFFFFFF);
		int x0 = order == 0 ? LANE_X0 : DiceStage.W - 1 - LANE_X1;
		int x1 = order == 0 ? LANE_X1 : DiceStage.W - 1 - LANE_X0;
		g.fill(x0, LANE_Y0, x1 + 1, LANE_Y0 + 1, c);
		g.fill(x0, LANE_Y1, x1 + 1, LANE_Y1 + 1, c);
		g.fill(x0, LANE_Y0 + 1, x0 + 1, LANE_Y1, c);
		g.fill(x1, LANE_Y0 + 1, x1 + 1, LANE_Y1, c);
		int soft = ((int) Math.round(a * 0.16) << 24) | (GOLD & 0xFFFFFF);
		g.fill(x0 + 1, LANE_Y0 + 1, x1, LANE_Y1, soft);
	}

	/** The vs badge: tilts away from a die that banks off it (120 ms), and toward the loser at the compare. */
	private void vs(GuiGraphicsExtractor g, Show show, int round, double rt, int winner, boolean final_) {
		int frame = 0;
		if (final_ && winner >= 0) {
			frame = winner == 0 ? 2 : 1;
		} else if (show != null && !Double.isNaN(show.offset)) {
			for (int order = 0; order < 2; order++) {
				double rollMs = rt - DuelTimeline.throwAt(0, order);
				for (MotionEvent e : show.rolls[round][order].events()) {
					if (e.kind() != DiceEvent.BUMPER) continue;
					double age = rollMs - e.tMs();
					if (age >= 0 && age < 120) {
						// the hit comes from this lane: the badge rocks toward the impact side (DI §3.1)
						boolean fromLeft = show.maps[order].x(e.x()) < 180;
						frame = fromLeft ? 1 : 2;
					}
				}
			}
		}
		TableGfx.frame(g, "extras/dice_duel_vs", 32, 32, 3, frame, 164, 54, 0xFFFFFFFF);
	}

	// ---- cups ---------------------------------------------------------------------------------------------------------

	private void cup(GuiGraphicsExtractor g, int order, DuelTimeline.Cup cup, double ms, int n, double half) {
		int frame = CUP_FRAME[cup.frame(ms)];
		double dx = cup.offsetX(ms);
		double lift = cup.lift(ms);
		double x = order == 0 ? CUP_X + dx : DiceStage.W - CUP_X - CUP + dx + half;
		double y = CUP_Y - lift;
		g.pose().pushMatrix();
		g.pose().translate(PixelSnap.snapF(x, n), PixelSnap.snapF(y, n));
		if (order == 0) TableGfx.region(g, CUP_TEX, CUP, CUP * 6, 0, frame * CUP, CUP, CUP, 0, 0, 0xFFFFFFFF);
		else TableGfx.regionMirrored(g, CUP_TEX, CUP, CUP * 6, 0, frame * CUP, CUP, CUP, 0, 0);
		g.pose().popMatrix();
	}

	/** Cup sprite box (art px) of pair {@code order} (rest position, for the pour-out test). */
	private static boolean overlapsCup(int order, double cx, double cy) {
		double x0 = order == 0 ? CUP_X : DiceStage.W - CUP_X - CUP;
		// the die's visible body: ±15 px wide, ±19 px tall around its drawn centre; the cup's opaque body inset 3 px
		return cx + 15 > x0 + 3 && cx - 15 < x0 + CUP - 3 && cy + 19 > CUP_Y + 4 && cy - 19 < CUP_Y + CUP - 2;
	}

	/** Roll ms at which die {@code d} of pair {@code order} no longer overlaps its cup after release (cached). */
	private double emerge(Show show, int round, int order, int d) {
		double v = show.emerge[round][order][d];
		if (!Double.isNaN(v)) return v;
		DiceRoll roll = show.rolls[round][order];
		double rel = roll.releaseMs(d);
		double out = rel;
		for (double ms = rel; ms <= rel + 400; ms += 4) {
			roll.sample(d, ms, t);
			if (t.phase == DieSample.HELD) continue;
			double cx = show.maps[order].x(t.x);
			double cy = show.maps[order].y(t.y) - show.maps[order].lift(t.zc);
			out = ms;
			if (!overlapsCup(order, cx, cy)) break;
		}
		show.emerge[round][order][d] = out;
		return out;
	}

	// ---- dice ---------------------------------------------------------------------------------------------------------

	private enum Pass {
		SHADOW, BEHIND, FRONT
	}

	private int[] depthOrder(Show show, int round, double rt) {
		int[] idx = {0, 1, 2, 3};
		double[] y = new double[4];
		for (int i = 0; i < 4; i++) {
			int order = i >> 1;
			show.rolls[round][order].sample(i & 1, rt - DuelTimeline.throwAt(0, order), t);
			y[i] = t.y;
		}
		// insertion sort by floor y (ascending: far first)
		for (int i = 1; i < 4; i++) {
			int v = idx[i];
			int j = i - 1;
			while (j >= 0 && y[idx[j]] > y[v]) {
				idx[j + 1] = idx[j];
				j--;
			}
			idx[j + 1] = v;
		}
		return idx;
	}

	/**
	 * One die in one pass. Reduced motion: no flight, the die fades in at its rest pose 300 ms after the throw.
	 * Compare (last round): the winner pair hops 3 px with a yaw wiggle and turns gold, the loser dims and sinks 1 px;
	 * push: both pairs shake 1 px twice. Tie (not the last round): swept back into its own cup at SWEEP_AT.
	 */
	private void die(GuiGraphicsExtractor g, Frame f, Show show, int round, int order, int d, double rt, int winner, double half, Pass pass) {
		int n = f.stage().n();
		boolean reduced = f.reduced();
		DiceRoll roll = show.rolls[round][order];
		StageMap map = show.maps[order];
		double rollMs = rt - DuelTimeline.throwAt(0, order);
		double alpha = 1;
		boolean last = show.last(round);
		if (reduced) {
			double in = rollMs - 300;
			if (in < 0) return;
			alpha = Math.min(1, in / 150);
			roll.sample(d, roll.restTime(d) + 1e6, s);
		} else {
			roll.sample(d, rollMs, s);
		}
		if (s.phase == DieSample.HELD) return;
		boolean behind = !reduced && rollMs < emerge(show, round, order, d);
		// the PvP tie sweep: back into the own cup (IN_CUBIC, 2 px hop), behind the lip at the end
		double sweep = !last && rt >= DuelTimeline.SWEEP_AT ? Math.min(1, (rt - DuelTimeline.SWEEP_AT) / DuelTimeline.SWEEP_MS) : 0;
		if (sweep >= 1) return;
		double sx = 0;
		double sy = 0;
		if (sweep > 0) {
			if (reduced) {
				alpha *= 1 - Math.min(1, (rt - DuelTimeline.SWEEP_AT) / 200);
			} else {
				double e = Ease.IN_CUBIC.apply(sweep);
				double cx = map.x(s.x);
				double tx = order == 0 ? CUP_X + CUP * 0.55 : DiceStage.W - CUP_X - CUP * 0.55;
				sx = (tx - cx) * e;
				sy = (CUP_Y + 20 - map.y(s.y) + map.lift(s.zc)) * e - 2 * Math.sin(Math.PI * sweep) * 1;
				behind = sweep > 0.55;
			}
		}
		if (pass == Pass.BEHIND && !behind || pass == Pass.FRONT && behind) return;
		// compare reactions (last round only)
		double since = rt - DuelTimeline.COMPARE;
		boolean compared = last && since >= 0;
		boolean push = compared && winner < 0 && !"refund".equals(show.outcome);
		boolean win = compared && winner == order;
		boolean lose = compared && winner >= 0 && winner != order;
		boolean hopper = win && !"house_tie".equals(show.outcome);
		double hop = 0;
		double wiggle = 0;
		if (hopper && !reduced && MotionFx.motion("dice_hop")) {
			double u = since / 220.0;
			if (u < 1) {
				hop = 3 * 4 * u * (1 - u);
				wiggle = u > 0.35 && u < 0.65 ? (u < 0.5 ? 1 : -1) * (order == 0 ? 1 : -1) : 0;
			}
		}
		double shakeX = 0;
		if (push && !reduced && MotionFx.shake("dice_push")) {
			double u = since / 160.0;
			if (u < 1) shakeX = Math.sin(u * Math.PI * 4) >= 0 ? 1 : -1;
		}
		double sink = lose ? Math.min(1, since / 200) : 0;
		if (pass == Pass.SHADOW) {
			if (sweep > 0.55) return;
			t.set(s);
			t.zLow += hop / map.zScale();
			double shadowA = alpha * (1 - 0.25 * sink) * (sweep > 0 ? 1 - sweep : 1);
			shadowAt(g, t, map, n, shadowA, sx + (order == 1 ? half : 0) + shakeX, sy);
			return;
		}
		// the drawn orientation (mirrored lanes keep the top number) with the hop's yaw wiggle on the atlas yaw frames
		double[] q = map.isMirrored() ? Quat.mirrorXKeepUp(s.q, vq) : Quat.copy(s.q, vq);
		if (wiggle != 0) {
			Quat.axisAngle(0, 0, 1, Math.toRadians(15) * wiggle, yawQ);
			Quat.mul(yawQ, q, vq);
			q = vq;
		}
		double cx = map.x(s.x) + sx + (order == 1 ? half : 0) + shakeX;
		double cy = map.y(s.y) - map.lift(s.zc) + sy - hop + sink;
		int tint = 0xFFFFFFFF;
		if (lose) tint = TableGfx.lerp(0xFFFFFFFF, DIM, reduced ? 1 : sink);
		int a = (int) Math.round(255 * alpha);
		tint = (a << 24) | (tint & 0xFFFFFF);
		if (!reduced && s.omega > AFTER_OMEGA && MotionFx.streak("dice_after_image")) {
			roll.sample(d, rollMs - DiceDraw.AFTER_MS, t);
			double[] qb = map.isMirrored() ? Quat.mirrorXKeepUp(t.q, yawQ) : t.q;
			double bx = map.x(t.x) + sx + (order == 1 ? half : 0);
			double by = map.y(t.y) - map.lift(t.zc) + sy;
			DieAtlas.blit(g, CELL, qb, bx, by, n, (((int) Math.round(255 * AFTER_ALPHA * alpha)) << 24) | 0xFFFFFF);
		}
		DieAtlas.blit(g, CELL, q, cx, cy, n, tint);
		if (pass == Pass.FRONT && s.phase == DieSample.REST && sweep == 0) restOverlays(g, f, show, round, order, d, rt, q, cx, cy, win, lose, since);
	}

	private void shadowAt(GuiGraphicsExtractor g, DieSample smp, StageMap map, int n, double alpha, double dx, double dy) {
		if (alpha <= 0.01) return;
		g.pose().pushMatrix();
		g.pose().translate((float) dx, (float) dy);
		DiceDraw.shadow(g, smp, map, CELL, n, alpha);
		g.pose().popMatrix();
	}

	/** The previous result's dice gathered back into the cups when a new roll starts (250 ms; a fade with reduced motion). */
	private void drawSwept(GuiGraphicsExtractor g, Show prev, double u, int n, boolean reduced, boolean shadows) {
		int round = prev.roundCount() - 1;
		for (int order = 0; order < 2; order++) {
			for (int d = 0; d < 2; d++) {
				DiceRoll roll = prev.rolls[round][order];
				StageMap map = prev.maps[order];
				roll.sample(d, roll.restTime(d) + 1e6, s);
				double cx = map.x(s.x);
				double cy = map.y(s.y) - map.lift(s.zc);
				double alpha = 1;
				double dx = 0;
				double dy = 0;
				if (reduced) {
					alpha = 1 - u;
				} else {
					double e = Ease.IN_CUBIC.apply(u);
					double tx = order == 0 ? CUP_X + CUP * 0.55 : DiceStage.W - CUP_X - CUP * 0.55;
					dx = (tx - cx) * e;
					dy = (CUP_Y + 20 - cy) * e - 2 * Math.sin(Math.PI * u);
					if (u > 0.55) alpha = 0; // behind the lip: hidden by the cup drawn after
				}
				if (alpha <= 0) continue;
				if (shadows) {
					shadowAt(g, s, map, n, alpha * (1 - u), dx, dy);
				} else {
					double[] q = map.isMirrored() ? Quat.mirrorXKeepUp(s.q, vq) : s.q;
					DieAtlas.blit(g, CELL, q, cx + dx, cy + dy, n, (((int) Math.round(255 * alpha)) << 24) | 0xFFFFFF);
				}
			}
		}
	}

	// ---- count-up, glint, compare -------------------------------------------------------------------------------------

	/** Round-clock time pip {@code k} (0-based over both dice) of pair {@code order} lights up. */
	private static double pipAt(Show show, int round, int order, int k) {
		int[] f = show.faces(round, order);
		return show.land(round, order) + DuelTimeline.LAND_RIM_MS + k * DuelTimeline.pipMs(order, f[0] + f[1]);
	}

	/** Count of pips of pair {@code order} lit at round time {@code rt}. */
	static int pipsLit(Show show, int round, int order, double rt, boolean reduced) {
		int[] f = show.faces(round, order);
		int all = f[0] + f[1];
		double land = show.land(round, order);
		if (rt < land) return 0;
		if (reduced) return all;
		double since = rt - land - DuelTimeline.LAND_RIM_MS;
		if (since < 0) return 0;
		return Math.min(all, 1 + (int) Math.floor(since / DuelTimeline.pipMs(order, all)));
	}

	private void restOverlays(GuiGraphicsExtractor g, Frame f, Show show, int round, int order, int d, double rt, double[] q, double cx, double cy, boolean win,
			boolean lose, double sinceCompare) {
		int n = f.stage().n();
		boolean reduced = f.reduced();
		double land = show.land(round, order);
		double sinceLand = rt - land;
		if (sinceLand < 0) return;
		// land rim: a white glint over the die for 60 ms (flashes only)
		if (!reduced && sinceLand < DuelTimeline.LAND_RIM_MS + 40 && MotionFx.flash("dice_land_rim")) {
			double a = 1 - sinceLand / (DuelTimeline.LAND_RIM_MS + 40);
			SnapDraw.blitF(g, GLINT, cx - FRAME / 2.0, cy - FRAME / 2.0, FRAME, FRAME, (((int) Math.round(255 * a)) << 24) | 0xFFFFFF, n);
		}
		int[] faces = show.faces(round, order);
		int lit = pipsLit(show, round, order, rt, reduced);
		int mine = d == 0 ? Math.min(lit, faces[0]) : Math.max(0, lit - faces[0]);
		if (mine <= 0) return;
		double pipAlpha;
		boolean compared = show.last(round) && sinceCompare >= 0;
		if (compared && lose) pipAlpha = Math.max(0, 0.5 * (1 - sinceCompare / 200));
		else if (compared && win) pipAlpha = reduced ? 1 : 0.85 + 0.15 * Math.sin(sinceCompare / 1000 * Math.PI * 2 * 1.5);
		else {
			// fresh pips flare to full, then settle at 0.5 once the pair's count is done
			double done = pipAt(show, round, order, faces[0] + faces[1] - 1) + 120;
			pipAlpha = reduced ? 0.5 : rt < done ? 1 : Math.max(0.5, 1 - (rt - done) / 300 * 0.5);
		}
		if (pipAlpha > 0.01) pips(g, q, faces[d], mine, cx, cy, n, pipAlpha, show, round, order, d, rt, reduced);
	}

	/** Pip centres of the TOP face {@code face} on the screen for orientation {@code q}: frame px offsets from the centre. */
	static int pipCentres(double[] q, int face, double[] out) {
		double[][] at = PIP_AT[face];
		int[] nrm = CubeGroup.normal(face);
		double[][] b = FACE_BASIS[face];
		double[] p = new double[3];
		for (int i = 0; i < at.length; i++) {
			double s0 = at[i][0];
			double t0 = at[i][1];
			double bx = HP * (nrm[0] + s0 * b[0][0] + t0 * b[1][0]);
			double by = HP * (nrm[1] + s0 * b[0][1] + t0 * b[1][1]);
			double bz = HP * (nrm[2] + s0 * b[0][2] + t0 * b[1][2]);
			Quat.rotate(q, bx, by, bz, p, 0);
			out[2 * i] = p[0];
			out[2 * i + 1] = p[1] * CP - p[2] * SP;
		}
		return at.length;
	}

	private final double[] pipXY = new double[12];
	private final int[] pipOrder = new int[6];

	/** Lights the first {@code count} pips (reading order) of a resting die gold (DI §5.1). */
	private void pips(GuiGraphicsExtractor g, double[] q, int face, int count, double cx, double cy, int n, double alpha, Show show, int round, int order, int d,
			double rt, boolean reduced) {
		int np = pipCentres(q, face, pipXY);
		for (int i = 0; i < np; i++) pipOrder[i] = i;
		// reading order: top to bottom, then left to right
		for (int i = 1; i < np; i++) {
			int v = pipOrder[i];
			int j = i - 1;
			while (j >= 0 && (pipXY[2 * pipOrder[j] + 1] > pipXY[2 * v + 1] + 0.5
				|| Math.abs(pipXY[2 * pipOrder[j] + 1] - pipXY[2 * v + 1]) <= 0.5 && pipXY[2 * pipOrder[j]] > pipXY[2 * v])) {
				pipOrder[j + 1] = pipOrder[j];
				j--;
			}
			pipOrder[j + 1] = v;
		}
		double r = (face == 1 ? 0.3 : 0.2) * HP;
		// the frame's top-left exactly as DieAtlas snaps it, so the glow sits on the atlas pips
		float fx = PixelSnap.snapF(cx - FRAME / 2.0, n);
		float fy = PixelSnap.snapF(cy - FRAME / 2.0, n);
		g.pose().pushMatrix();
		g.pose().translate(fx, fy);
		int firstPip = d == 0 ? 0 : show.faces(round, order)[0];
		for (int i = 0; i < Math.min(count, np); i++) {
			int pi = pipOrder[i];
			double px = FRAME / 2.0 + pipXY[2 * pi];
			double py = FRAME / 2.0 + pipXY[2 * pi + 1];
			double a = alpha;
			if (!reduced) {
				// each pip flares white for 40 ms when it lights
				double age = rt - pipAt(show, round, order, firstPip + i);
				if (age >= 0 && age < 40 && MotionFx.flash("dice_pip_flare")) {
					disc(g, px, py, r + 0.6, CP, TableGfx.alpha(0xFFFFFFFF, a));
					continue;
				}
			}
			disc(g, px, py, r + 0.6, CP, TableGfx.alpha(GOLD_RIM, a));
			disc(g, px, py, r - 0.4, CP, TableGfx.alpha(GOLD, a));
			disc(g, px - r * 0.3, py - r * 0.3, Math.max(0.6, r * 0.35), CP, TableGfx.alpha(GOLD_HI, a));
		}
		g.pose().popMatrix();
	}

	/** A filled ellipse of whole px (centre (cx, cy), radius r, y squashed by {@code ky}): pixels whose centre is inside. */
	private static void disc(GuiGraphicsExtractor g, double cx, double cy, double r, double ky, int argb) {
		if ((argb >>> 24) == 0 || r <= 0) return;
		double ry = r * ky;
		int y0 = (int) Math.floor(cy - ry);
		int y1 = (int) Math.ceil(cy + ry);
		for (int y = y0; y <= y1; y++) {
			double v = (y + 0.5 - cy) / ry;
			if (v * v > 1) continue;
			double hw = r * Math.sqrt(1 - v * v);
			int x0 = (int) Math.ceil(cx - hw - 0.5);
			int x1 = (int) Math.floor(cx + hw - 0.5);
			if (x1 >= x0) g.fill(x0, y, x1 + 1, y + 1, argb);
		}
	}

	// ---- particles (pure functions of the events) ----------------------------------------------------------------------

	private void particles(GuiGraphicsExtractor g, Show show, int round, int order, double rt, boolean reduced, int n, double half) {
		if (reduced) return;
		DiceRoll roll = show.rolls[round][order];
		StageMap map = show.maps[order];
		double rollMs = rt - DuelTimeline.throwAt(0, order);
		MotionEvent[] ev = roll.events();
		for (int i = 0; i < ev.length; i++) {
			MotionEvent e = ev[i];
			double age = rollMs - e.tMs();
			if (age < 0 || age > 200) continue;
			double x = map.x(e.x()) + half;
			double y = map.y(e.y());
			switch (e.kind()) {
				case DiceEvent.FLOOR -> {
					if (e.strength() * DiceEvent.STRENGTH_SPEED < 6 || age >= 160 || !MotionFx.particles("dice_dust", 4)) continue;
					MotionRng r = MotionRng.of(show.seed, "DUST", round * 64 + order * 32 + i);
					int count = 2 + r.nextInt(3);
					dust(g, r, count, x, y + 4, age, n);
				}
				case DiceEvent.WALL -> {
					if (age >= 160) continue;
					if (MotionFx.particles("dice_dust", 3)) dust(g, MotionRng.of(show.seed, "WDUST", round * 64 + order * 32 + i), 3, x, y, age, n);
					if (age < 40 && MotionFx.flash("dice_rail_glint")) {
						double a = 0.55 * (1 - age / 40);
						g.fill((int) Math.round(x) - 2, (int) Math.round(y) - 1, (int) Math.round(x) + 2, (int) Math.round(y) + 1, TableGfx.alpha(0xFFFFF6D8, a));
					}
				}
				case DiceEvent.CLACK -> {
					if (age < 60 && MotionFx.flash("dice_clack_spark")) {
						int fr = Math.min(2, (int) (age / 20));
						SnapDraw.frameF(g, "core/dice/spark", 3, 3, 3, fr, x - 1.5, y - 1.5 - PX * DiceDraw.LIFT, 0xFFFFFFFF, n);
					}
				}
				default -> {
				}
			}
		}
		// win sparks: 4 gold sparks per winning die flying up and out after the compare
		if (show.last(round) && rt >= DuelTimeline.COMPARE && show.winner() == order && !"house_tie".equals(show.outcome)
			&& rt < DuelTimeline.COMPARE + 450 && MotionFx.particles("dice_win_spark", 8)) {
			double since = rt - DuelTimeline.COMPARE;
			for (int d = 0; d < 2; d++) {
				roll.sample(d, roll.restTime(d) + 1e6, t);
				double cx = map.x(t.x);
				double cy = map.y(t.y) - map.lift(t.zc);
				MotionRng r = MotionRng.of(show.seed, "WSPARK", order * 2 + d);
				for (int i = 0; i < 4; i++) {
					double ang = -Math.PI / 2 + r.range(-1.1, 1.1);
					double sp = r.range(30, 55);
					double u = since / 1000.0;
					double px = cx + Math.cos(ang) * sp * u;
					double py = cy - 10 + Math.sin(ang) * sp * u + 60 * u * u;
					int fr = Math.min(2, (int) (since / 150));
					double a = 1 - since / 450;
					SnapDraw.frameF(g, "core/dice/spark", 3, 3, 3, fr, px - 1.5, py - 1.5, TableGfx.alpha(0xFFFFD45A, a), n);
				}
			}
		}
	}

	/** {@code count} dust puffs from a contact at (x, y), 160 ms (4 frames), seeded scatter, drifting out and up. */
	private static void dust(GuiGraphicsExtractor g, MotionRng r, int count, double x, double y, double age, int n) {
		int fr = Math.min(3, (int) (age / 40));
		double u = age / 160;
		for (int i = 0; i < count; i++) {
			double ang = r.range(-Math.PI, 0);
			double dist = r.range(4, 9);
			double px = x + Math.cos(ang) * dist * (0.4 + 0.6 * u);
			double py = y + Math.sin(ang) * dist * 0.45 * (0.4 + 0.6 * u) - 2 * u;
			SnapDraw.frameF(g, "core/dice/dust", 5, 5, 4, fr, px - 2.5, py - 2.5, TableGfx.fade(0.85 * (1 - 0.5 * u)), n);
		}
	}

	// ---- plaques, stamp, banner, chips -------------------------------------------------------------------------------

	static int plaqueX(int order) {
		int lane = (LANE_X0 + LANE_X1) / 2 - PLAQUE_W / 2;
		return order == 0 ? lane : DiceStage.W - lane - PLAQUE_W;
	}

	private void plaque(GuiGraphicsExtractor g, Frame f, Show show, int round, int order, double rt, int winner, double half) {
		boolean reduced = f.reduced();
		double land = show.land(round, order);
		double appear = land + (reduced ? 0 : DuelTimeline.LAND_RIM_MS);
		double since = rt - appear;
		if (since < 0) return;
		int[] faces = show.faces(round, order);
		int total = faces[0] + faces[1];
		int lit = pipsLit(show, round, order, rt, reduced);
		double countEnd = reduced ? appear : pipAt(show, round, order, total - 1);
		boolean counted = rt >= countEnd;
		boolean last = show.last(round);
		double sinceCompare = rt - DuelTimeline.COMPARE;
		boolean compared = last && sinceCompare >= 0;
		boolean tie = show.total(round, 0) == show.total(round, 1);
		int state = compared && winner >= 0 ? winner == order ? 1 : -1 : 0;
		// slide up from the rail when it appears; pop (2 px up, out-back) when the count is done
		double in = reduced ? 1 : Ease.OUT_BACK.apply(Math.min(1, since / 200));
		double dy = 8 * (1 - in);
		double pop = 0;
		if (!reduced && counted && MotionFx.motion("dice_plaque_pop")) {
			double u = (rt - countEnd) / DuelTimeline.POP_MS;
			if (u < 1) pop = 3 * Math.sin(Math.PI * Math.min(1, u)) * (1 - u * 0.3);
		}
		double dx = tie && compared && !reduced && sinceCompare < 300 && MotionFx.shake("dice_tie_plaques") ? Math.round(Math.sin(sinceCompare / 300 * Math.PI * 4)) : 0;
		if (!last && tie && rt >= DuelTimeline.COMPARE && rt < DuelTimeline.COMPARE + 300 && !reduced) {
			dx = Math.round(Math.sin(sinceCompare / 300 * Math.PI * 4));
		}
		// the tie round's plaques leave with the sweep
		double fade = !last && rt >= DuelTimeline.SWEEP_AT ? Math.max(0, 1 - (rt - DuelTimeline.SWEEP_AT) / 200) : 1;
		if (fade <= 0) return;
		int x = plaqueX(order);
		double y = PLAQUE_Y + dy - pop - (state > 0 ? 2 : 0);
		int n = f.stage().n();
		String sprite = state > 0 ? "win" : state < 0 ? "lose" : "neutral";
		g.pose().pushMatrix();
		g.pose().translate(PixelSnap.snapF(x + dx + half, n), PixelSnap.snapF(y, n));
		int base = TableGfx.fade(fade * (state < 0 ? 0.8 : 1));
		TableGfx.blit(g, "extras/dice_duel_plaque_" + sprite, 0, 0, PLAQUE_W, PLAQUE_H, base);
		int shown = counted ? total : lit;
		Component num = Texts.number(shown);
		int color = state > 0 ? CasinoPalette.V2_GOLD : state < 0 ? 0xFF8C7CA8 : tie && compared ? 0xFFB8B0C0 : counted ? CasinoPalette.V2_TEXT : 0xFFE8DCF0;
		int w = f.font().width(num) * 2;
		V2Draw.text(g, f.font(), num, PLAQUE_W / 2 - w / 2 + 1, 4, 2, TableGfx.alpha(color, fade), true);
		if (state < 0) TableGfx.frame(g, "extras/dice_duel_crack", 32, 24, 2, reduced || sinceCompare > 120 ? 1 : 0, 0, 0, TableGfx.fade(fade));
		g.pose().popMatrix();
	}

	/** Time after the compare at which the stamp hits (OUT_BOUNCE's first contact). */
	private static double stampImpact() {
		return 250 * 0.364;
	}

	private void stamp(GuiGraphicsExtractor g, double since, boolean reduced, int n) {
		if (since < 0) return;
		double k = reduced ? 1 : Math.min(1, since / 250.0);
		double scale = reduced ? 1 : 1 + 0.6 * (1 - Ease.OUT_BOUNCE.apply(k));
		double cx = plaqueX(1) + PLAQUE_W / 2.0;
		double cy = PLAQUE_Y + PLAQUE_H / 2.0 - 4;
		g.pose().pushMatrix();
		g.pose().translate(PixelSnap.snapF(cx, n), PixelSnap.snapF(cy, n));
		if (scale != 1) g.pose().scale((float) scale, (float) scale);
		TableGfx.blit(g, "extras/dice_duel_house_stamp", -21, -21, 42, 42, 0xFFFFFFFF);
		double hit = since - stampImpact();
		if (!reduced && hit >= 0 && hit < 34 && MotionFx.flash("dice_stamp_flash")) {
			TableGfx.blit(g, "extras/dice_duel_house_stamp", -21, -21, 42, 42, 0x90FFFFFF);
			g.fill(-18, -18, 18, 18, 0x50FFFFFF);
		}
		g.pose().popMatrix();
		if (!reduced && hit >= 0 && hit < 200 && MotionFx.particles("dice_stamp_dust", 8)) {
			MotionRng r = MotionRng.of(0x57A4, "STAMP", 0);
			dust(g, r, 8, cx, cy + 14, hit * 0.8, n);
		}
	}

	private void tieBanner(GuiGraphicsExtractor g, Frame f, int round, int rounds, double since) {
		Component text = Component.translatable("gui.burmaldaholic.extras.dice.fx.tie_reroll", Texts.number(round + 2), Texts.number(rounds));
		// on the front rail between the two plaques: never over a die
		int room = plaqueX(1) - (plaqueX(0) + PLAQUE_W) - 6;
		int w = Math.min(room, f.font().width(text) + 16);
		Box b = new Box(180 - w / 2, PLAQUE_Y + 3, w, 18);
		double in = f.reduced() ? 1 : Ease.OUT_BACK.apply(Math.min(1, since / 180));
		g.pose().pushMatrix();
		g.pose().translate(0, (float) Math.round(6 * (1 - in)));
		V2Draw.pill(g, b, CasinoPalette.V2_PANEL, CasinoPalette.V2_GOLD);
		V2Draw.centred(g, f.font(), text, b, 1, CasinoPalette.V2_GOLD, true);
		g.pose().popMatrix();
	}

	/** The pot on the front rail, then it crosses to the winner (house tie: the house) at CHIPS. */
	private void chips(GuiGraphicsExtractor g, Show show, double rt, boolean reduced) {
		long stake = Math.max(1, Math.abs(show.net));
		if (rt < DuelTimeline.CHIPS) {
			ChipStack.draw(g, Box.centredAt(POT_X, POT_Y, 16, 16), stake);
			return;
		}
		double k = reduced ? 1 : Math.min(1, (rt - DuelTimeline.CHIPS) / 400.0);
		switch (show.outcome) {
			case "win" -> {
				int x = (int) Math.round(POT_X + (plaqueX(0) - 18 - POT_X) * Ease.IN_CUBIC.apply(k));
				if (k < 1) ChipStack.draw(g, Box.centredAt(x, POT_Y, 16, 16), 2 * stake, (float) (1 - 0.8 * k));
			}
			case "lose", "house_tie" -> {
				int x = (int) Math.round(POT_X + (plaqueX(1) + PLAQUE_W + 18 - POT_X) * Ease.IN_CUBIC.apply(k));
				if (k < 1) ChipStack.draw(g, Box.centredAt(x, POT_Y, 16, 16), stake, (float) (1 - 0.8 * k));
			}
			default -> {
				if (k < 1) ChipStack.draw(g, Box.centredAt(POT_X, POT_Y + (int) Math.round(10 * k), 16, 16), stake, (float) (1 - k));
			}
		}
	}

	// ---- sounds -------------------------------------------------------------------------------------------------------

	/**
	 * Advances the sound cursors of the current round (after drawing): the cups' rattle (shake clock), each pair's
	 * contacts (roll clock), the beats (drumroll, count-up ticks, hop, stamp, outcome). A jump (skip, a late open, a
	 * hitch) fires the passed events silently.
	 */
	void sounds(Show show, double t, double waitShakeMs, Show waitingFor, boolean reduced) {
		if (show == null || Double.isNaN(show.offset)) {
			// waiting for the house result: both cups rattle (looping shake)
			if (!Double.isNaN(waitShakeMs) && !reduced) waitingRattle(waitingFor, waitShakeMs);
			return;
		}
		int round = DuelTimeline.roundAt(t, show.roundCount());
		double rt = t - DuelTimeline.roundStart(round);
		if (cursorShow != show || cursorRound != round) {
			// a new result / round / a skip: start the cursors at NOW, what already passed stays silent (DI §3.3 skip)
			for (int order = 0; order < 2; order++) {
				cursors[order] = DiceAudio.cursor(show.rolls[round][order].events());
				rattles[order] = DiceAudio.cursor(show.shakes[order].rattle(DuelTimeline.windUp(order, show.offset), 2));
				cursors[order].reset(rt - DuelTimeline.throwAt(0, order), true);
				rattles[order].reset(rt + show.offset, true);
			}
			beats = rt > 200 ? beatsDone(show, round, rt) : 0;
			cursorShow = show;
			cursorRound = round;
		}
		for (int order = 0; order < 2; order++) {
			if (!reduced) rattleAudio[order].advance(rattles[order], rt + show.offset, 0, null);
			if (!reduced) audio[order].advance(cursors[order], rt - DuelTimeline.throwAt(0, order), 0, null);
			else silentAdvance(show, round, order, rt);
		}
		beats(show, round, rt, reduced);
	}

	private void silentAdvance(Show show, int round, int order, double rt) {
		// reduced motion: no contact sounds; one settle per pair at its rest
		EventCursor c = cursors[order];
		c.advance(rt - DuelTimeline.throwAt(0, order), (e, silent) -> {
			if (!silent && e.kind() == DiceEvent.REST && e.obj() == 1) FxSounds.play("dice_settle", 0.8f, 1.4f);
		});
	}

	private Show rattleShow;
	private EventCursor waitRattle0;
	private EventCursor waitRattle1;

	private void waitingRattle(Show forShow, double ms) {
		if (waitRattle0 == null || rattleShow != forShow || ms + 1 < rattleUntil - 4000) {
			int seed = forShow == null ? 0 : forShow.seed;
			waitRattle0 = DiceAudio.cursor(Shake.of(seed, false).rattle(ms + 4000, 2));
			waitRattle1 = DiceAudio.cursor(Shake.of(seed + 1, true).rattle(ms + 4000, 2));
			waitRattle0.reset(ms, true);
			waitRattle1.reset(ms, true);
			rattleUntil = ms + 4000;
			rattleShow = forShow;
		}
		rattleAudio[0].advance(waitRattle0, ms, 0, null);
		rattleAudio[1].advance(waitRattle1, ms, 0, null);
		if (ms > rattleUntil - 1000) waitRattle0 = null;
	}

	private static final int B_DRUM = 1, B_PIP0 = 2, B_PIP1 = 4, B_PIP2 = 8, B_PIP3 = 16, B_COMPARE = 32, B_STAMP = 64, B_OUTCOME = 128, B_TOTAL0 = 256,
		B_TOTAL1 = 512;

	private static int beatsDone(Show show, int round, double rt) {
		int b = 0;
		if (rt >= DuelTimeline.YOU_LAND) b |= B_DRUM;
		if (rt >= DuelTimeline.COMPARE) b |= B_COMPARE | B_STAMP | B_PIP0 | B_PIP1 | B_PIP2 | B_PIP3 | B_TOTAL0 | B_TOTAL1;
		if (rt >= DuelTimeline.CHIPS) b |= B_OUTCOME;
		return b;
	}

	private void beats(Show show, int round, double rt, boolean reduced) {
		boolean last = show.last(round);
		if ((beats & B_DRUM) == 0 && rt >= DuelTimeline.YOU_LAND) {
			beats |= B_DRUM;
			if (!reduced) FxSounds.play("pvp_drumroll", 0.3f, 1f);
		}
		for (int order = 0; order < 2; order++) {
			int[] f = show.faces(round, order);
			// a soft tick when each die's pips are counted, rising (1.4 → 2.0), and the total's pop
			for (int d = 0; d < 2; d++) {
				int bit = order == 0 ? (d == 0 ? B_PIP0 : B_PIP1) : (d == 0 ? B_PIP2 : B_PIP3);
				int k = d == 0 ? f[0] - 1 : f[0] + f[1] - 1;
				if ((beats & bit) == 0 && rt >= pipAt(show, round, order, k)) {
					beats |= bit;
					if (!reduced) FxSounds.play("dice_pip", 0.5f, d == 0 ? 1.4f + 0.05f * f[0] : 1.75f + 0.02f * (f[0] + f[1]));
				}
			}
		}
		if (!last) return;
		if ((beats & B_COMPARE) == 0 && rt >= DuelTimeline.COMPARE) {
			beats |= B_COMPARE;
			if (!reduced && show.winner() >= 0 && !"house_tie".equals(show.outcome)) FxSounds.play("dice_bounce", 0.8f, 1.4f);
		}
		if ((beats & B_STAMP) == 0 && "house_tie".equals(show.outcome) && rt >= DuelTimeline.COMPARE + stampImpact()) {
			beats |= B_STAMP;
			FxSounds.play("dice_stamp", 1f, 1.8f);
		}
		if ((beats & B_OUTCOME) == 0 && rt >= DuelTimeline.CHIPS) {
			beats |= B_OUTCOME;
			switch (show.outcome) {
				case "win" -> FxSounds.play(show.pvp ? "win_nice" : "win_small", 1f);
				case "lose", "house_tie" -> FxSounds.play("lose", 1f);
				default -> FxSounds.play("push", 1f);
			}
			if (show.pvp && "win".equals(show.outcome)) FxSounds.play("pvp_victory", 0.8f, 1f);
		}
	}

	/** Forgets the sound cursors (a skip: everything up to now has been heard or is skipped). */
	void silence() {
		cursorShow = null;
		cursorRound = -1;
	}

	// ---- the generator's pip layout (tools/assets/modules/tables/dice.mjs PIP_AT / FACE_BASIS) ------------------------

	static final double[][][] PIP_AT = {null, {{0, 0}}, {{-0.5, -0.5}, {0.5, 0.5}}, {{-0.5, -0.5}, {0, 0}, {0.5, 0.5}},
		{{-0.5, -0.5}, {0.5, -0.5}, {-0.5, 0.5}, {0.5, 0.5}}, {{-0.5, -0.5}, {0.5, -0.5}, {0, 0}, {-0.5, 0.5}, {0.5, 0.5}},
		{{-0.5, -0.55}, {-0.5, 0}, {-0.5, 0.55}, {0.5, -0.55}, {0.5, 0}, {0.5, 0.55}}};
	static final double[][][] FACE_BASIS = {null, {{1, 0, 0}, {0, 1, 0}}, {{0, 0, -1}, {1, 0, 0}}, {{0, 1, 0}, {0, 0, 1}}, {{0, -1, 0}, {0, 0, 1}},
		{{0, 0, 1}, {1, 0, 0}}, {{1, 0, 0}, {0, -1, 0}}};
}
