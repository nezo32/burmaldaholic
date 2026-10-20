package dev.nezo.burmaldaholic.client.table;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nezo.burmaldaholic.core.table.parts.BoardFrame;
import dev.nezo.burmaldaholic.core.table.parts.FeltFrame;
import dev.nezo.burmaldaholic.core.table.parts.MachineKind;
import dev.nezo.burmaldaholic.core.table.parts.TableFootprint;
import dev.nezo.burmaldaholic.core.table.parts.TableGame;
import dev.nezo.burmaldaholic.core.table.parts.TablePart;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

/**
 * MT bridge for the v0.1 in-world table renderers (cards, chips, dice, roulette wheel), which lay their content out
 * on a 1×1 block top centred on the block. For a full (multi-block) table this moves the pose so the same content
 * lands on the full felt: centred on the felt (or the roulette bowl), at the full table's felt height, scaled up.
 * Compact tables ({@code part=single}) are untouched. The W2 board renderer replaces this with footprint-local
 * {@link FeltFrame} geometry.
 */
public final class TableFrames {
	/** Content scale on a full felt: the 1×1 layout grows to fill the 1.75-deep felt. */
	public static final float FULL_SCALE = 1.6f;

	private TableFrames() {}

	public static boolean full(TablePart part, @Nullable TableGame game) {
		return game != null && part.isMultiBlock();
	}

	/**
	 * For a full table: translate/scale so content authored for a 1×1 top at height {@code legacyTop} (block-local,
	 * centred on (0.5, 0.5)) draws centred on the full felt at the felt height. {@code atWheel} centres on the roulette
	 * bowl instead and keeps the scale (the bowl is one block wide).
	 */
	public static void toFullFelt(PoseStack pose, TablePart part, @Nullable TableGame game, Direction facing, float legacyTop, boolean atWheel) {
		if (!full(part, game) || facing.getAxis().isVertical()) {
			return;
		}
		float[] t = feltTransform(game, part, facing, atWheel);
		pose.translate(t[0], t[1], t[2]);
		pose.scale(t[3], t[3], t[3]);
		pose.translate(-0.5f, -legacyTop, -0.5f);
	}

	/** Cached per game × part × facing × wheel: {tx, top, tz, scale} (frames are constants; no per-frame records). */
	private static final float[][][][][] FELT = new float[TableGame.values().length][TablePart.values().length][4][2][];

	private static float[] feltTransform(TableGame game, TablePart part, Direction facing, boolean atWheel) {
		float[][] slot = FELT[game.ordinal()][part.ordinal()][facing.get2DDataValue()];
		int w = atWheel ? 1 : 0;
		float[] t = slot[w];
		if (t == null) {
			FeltFrame f = FeltFrame.of(game, part);
			double lx = atWheel && f.hasWheel() ? f.wheelCx() : f.cx();
			double lz = atWheel && f.hasWheel() ? f.wheelCz() : f.cz();
			double[] at = TableFootprint.toWorld(lx, lz, facing.getStepX(), facing.getStepZ());
			float top = (float) (atWheel ? f.top() : f.top() + FeltFrame.CONTENT_LIFT);
			t = new float[] {(float) at[0], top, (float) at[1], atWheel ? 1f : FULL_SCALE};
			slot[w] = t;
		}
		return t;
	}

	/**
	 * MT-3: for a full (multi-block) machine, maps content drawn for the v0.1 1×1 machine (wheel, Plinko ball and
	 * lamps) onto the full face: {@code full.centre + s·(p − compact.centre)} with {@code s} =
	 * {@link BoardFrame#scaleCompactToFull}. Compact machines and slots (s = 1, same centre) are untouched.
	 */
	public static void toFullFace(PoseStack pose, TablePart part, @Nullable MachineKind kind, Direction facing) {
		if (kind == null || !part.isMultiBlock() || facing.getAxis().isVertical()) {
			return;
		}
		float[] t = faceTransform(kind, facing);
		pose.translate(t[0], t[1], t[2]);
		pose.scale(t[3], t[3], t[3]);
		pose.translate(t[4], t[5], t[6]);
	}

	/** Cached per machine kind × facing: {full x, y, z, scale, −compact x, y, z}. */
	private static final float[][][] FACE = new float[MachineKind.values().length][4][];

	private static float[] faceTransform(MachineKind kind, Direction facing) {
		float[] t = FACE[kind.ordinal()][facing.get2DDataValue()];
		if (t == null) {
			BoardFrame c = BoardFrame.of(kind, TablePart.SINGLE);
			BoardFrame f = BoardFrame.of(kind, TablePart.MAIN);
			float s = (float) BoardFrame.scaleCompactToFull(kind);
			double[] cw = TableFootprint.toWorld(c.cx(), c.faceZ(), facing.getStepX(), facing.getStepZ());
			double[] fw = TableFootprint.toWorld(f.cx(), f.faceZ(), facing.getStepX(), facing.getStepZ());
			t = new float[] {(float) fw[0], (float) f.cy(), (float) fw[1], s, (float) -cw[0], (float) -c.cy(), (float) -cw[1]};
			FACE[kind.ordinal()][facing.get2DDataValue()] = t;
		}
		return t;
	}
}
