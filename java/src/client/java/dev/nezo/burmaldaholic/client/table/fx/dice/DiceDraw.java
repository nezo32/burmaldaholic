package dev.nezo.burmaldaholic.client.table.fx.dice;

import dev.nezo.burmaldaholic.client.fx.motion.MotionFx;
import dev.nezo.burmaldaholic.client.fx.motion.SnapDraw;
import dev.nezo.burmaldaholic.client.table.fx.TableGfx;
import dev.nezo.burmaldaholic.core.anim.dice.DiceRoll;
import dev.nezo.burmaldaholic.core.anim.dice.DieAtlasTable;
import dev.nezo.burmaldaholic.core.anim.dice.DieSample;
import dev.nezo.burmaldaholic.core.anim.dice.Quat;
import dev.nezo.burmaldaholic.core.anim.motion.HeightShadow;
import dev.nezo.burmaldaholic.core.anim.motion.StageMap;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Screen dice (PHASE2B §3.3, CS §4.2, DI §4.2): a {@link DieSample} drawn as its orientation-atlas frame (real pips,
 * rotation-driven faces), lifted by its centre height, over a floor shadow, with an after-image while spinning fast.
 * Everything moving is placed at float GUI positions snapped to physical pixels ({@code 1/k}) and blitted 1:1.
 *
 * <p>Coordinates: a {@link StageMap} maps tray units to GUI px. {@link #stage} builds the map that matches the atlas
 * art (1 u = the die half-edge in px; y foreshortened by the 22° camera pitch; height lifts by 0.9 half-edge per u, DI κ_lift). A
 * mirrored map (a left-lane roll shown in the right lane) mirrors the orientation too and keeps the top number.
 *
 * <p>Draw order per frame (CS §4.2 / DI §4.3): all shadows ({@link #shadow}), then dice sorted by y ({@link #die});
 * {@link #roll} does shadow + after-image + die for one die. Allocation-free with a reused {@link Scratch}.
 */
public final class DiceDraw {
	/** After-image above this angular speed, rad/s. */
	public static final double AFTER_OMEGA = 9;
	/** After-image look-back, ms. */
	public static final double AFTER_MS = 14;
	/** After-image alpha. */
	public static final double AFTER_ALPHA = 0.35;
	private static final double PITCH = Math.toRadians(DieAtlasTable.PITCH_DEG);
	/** Screen lift per unit of centre height, in half-edges (DI §4.2 κ_lift 0.9: the flight height reads). */
	public static final double LIFT = 0.9;
	/** Rest shadow offset from the floor point, in half-edges: down-right, so it peeks out under the die (light top-left). */
	public static final double SHADOW_DX = 0.3, SHADOW_DY = 0.45;
	private static final HeightShadow.Out SHADOW = new HeightShadow.Out();
	private static final Identifier[][] SHADOWS = new Identifier[DieAtlasTable.CELLS.length][3];
	private static final Identifier[] GLINTS = new Identifier[DieAtlasTable.CELLS.length];

	static {
		for (int i = 0; i < DieAtlasTable.CELLS.length; i++) {
			for (int s = 0; s < 3; s++) SHADOWS[i][s] = TableGfx.sprite("core/dice/shadow_" + DieAtlasTable.CELLS[i] + "_" + s);
			GLINTS[i] = TableGfx.sprite("core/dice/rest_glint_" + DieAtlasTable.CELLS[i]);
		}
	}

	private DiceDraw() {}

	/** Reusable buffers of one drawing site (one per screen / renderer). */
	public static final class Scratch {
		final DieSample now = new DieSample();
		final DieSample before = new DieSample();
		final double[] q = new double[4];
	}

	/** Half-edge of a die of {@code cell} px in GUI px (= tray units → px). */
	public static double halfEdge(int cell) {
		return (cell - 2) / 2.0;
	}

	/**
	 * The stage map that matches the atlas art for a tray drawn with its origin at GUI (ox, oy): x 1 u = half-edge px,
	 * y foreshortened by {@code cos 22°}, height {@link #LIFT} half-edges per u.
	 */
	public static StageMap stage(double ox, double oy, int cell) {
		double h = halfEdge(cell);
		return StageMap.of(ox, oy, h, h * Math.cos(PITCH), h * LIFT);
	}

	private static double lift(StageMap map, int cell, double zc) {
		return map.zScale() > 0 ? map.lift(zc) : zc * halfEdge(cell) * LIFT;
	}

	private static double[] viewQ(DieSample s, StageMap map, double[] buf) {
		return map.isMirrored() ? Quat.mirrorXKeepUp(s.q, buf) : s.q;
	}

	/** Shadow sprite id (size 0 = 100 %, 1 = 80 %, 2 = 60 %). */
	public static Identifier shadowSprite(int cell, int size) {
		return SHADOWS[DieAtlas.cellIndex(cell)][size];
	}

	/** The floor shadow of a die (alpha and size from its height, light from the top-left; CS §4.2). */
	public static void shadow(GuiGraphicsExtractor g, DieSample s, StageMap map, int cell, int k, double alphaMul) {
		if (s.phase == DieSample.HELD) return;
		HeightShadow.apply(HeightShadow.CRAPS, s.zLow, 1, SHADOW);
		double fx = shadowX(s, map, cell);
		double fy = shadowY(s, map, cell);
		double kk = SHADOW.shadowSize == 0 ? 1 : SHADOW.shadowSize == 1 ? 0.8 : 0.6;
		int w = Math.max(4, (int) Math.round(cell * kk));
		int h = Math.max(2, (int) Math.round(cell * 0.42 * kk));
		int a = (int) Math.round(255 * Math.max(0, Math.min(1, SHADOW.shadowAlpha * alphaMul)));
		if (a <= 0) return;
		SnapDraw.blitF(g, shadowSprite(cell, SHADOW.shadowSize), fx - w / 2.0, fy - h / 2.0, w, h, k, (a << 24) | 0xFFFFFF);
	}

	/** GUI x of the shadow centre (floor point + light offset + height slope). */
	public static double shadowX(DieSample s, StageMap map, int cell) {
		double z = Math.max(0, s.zLow);
		return map.x(s.x) + (0.25 * z * Math.abs(map.sx()) + SHADOW_DX * halfEdge(cell)) * (map.isMirrored() ? -1 : 1);
	}

	/** GUI y of the shadow centre. */
	public static double shadowY(DieSample s, StageMap map, int cell) {
		double z = Math.max(0, s.zLow);
		return map.y(s.y) + 0.15 * z * map.sy() + SHADOW_DY * halfEdge(cell);
	}

	/** The die itself: the atlas frame of its (model) orientation centred on its lifted centre. */
	public static void die(GuiGraphicsExtractor g, DieSample s, StageMap map, int cell, int k, int argb, Scratch sc) {
		double cx = map.x(s.x);
		double cy = map.y(s.y) - lift(map, cell, s.zc);
		DieAtlas.blit(g, cell, viewQ(s, map, sc.q), cx, cy, k, argb);
	}

	/** GUI centre of a die sample (for probes, particles and overlays): {@code {x, y}}. */
	public static void centre(DieSample s, StageMap map, int cell, double[] out) {
		out[0] = map.x(s.x);
		out[1] = map.y(s.y) - lift(map, cell, s.zc);
	}

	/** The rest glint overlay (a rest pose only: the identity-yaw frame), CS §4.3 REST. */
	public static void restGlint(GuiGraphicsExtractor g, DieSample s, StageMap map, int cell, int k, int argb) {
		if (s.phase != DieSample.REST) return;
		int f = DieAtlas.frameSize(cell);
		double cx = map.x(s.x);
		double cy = map.y(s.y) - lift(map, cell, s.zc);
		SnapDraw.blitF(g, GLINTS[DieAtlas.cellIndex(cell)], cx - f / 2.0, cy - f / 2.0, f, f, k, argb);
	}

	/**
	 * One die of a roll at {@code ms}: shadow, after-image (|ω| > 9 rad/s, the frame 14 ms earlier at α 0.35; off with
	 * reduced motion) and the die. Returns the sample in {@code sc} for the caller's overlays.
	 */
	public static DieSample roll(GuiGraphicsExtractor g, DiceRoll roll, int die, double ms, StageMap map, int cell, int k, Scratch sc) {
		DieSample s = sc.now;
		roll.sample(die, ms, s);
		shadow(g, s, map, cell, k, 1);
		if (s.omega > AFTER_OMEGA && MotionFx.streak("dice_after_image")) {
			roll.sample(die, ms - AFTER_MS, sc.before);
			int a = (int) Math.round(255 * AFTER_ALPHA);
			die(g, sc.before, map, cell, k, (a << 24) | 0xFFFFFF, sc);
		}
		die(g, s, map, cell, k, 0xFFFFFFFF, sc);
		return s;
	}
}
