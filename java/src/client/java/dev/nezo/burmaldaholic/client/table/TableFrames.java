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
		if (!full(part, game)) {
			return;
		}
		FeltFrame f = FeltFrame.of(game, part);
		double lx = atWheel && f.hasWheel() ? f.wheelCx() : f.cx();
		double lz = atWheel && f.hasWheel() ? f.wheelCz() : f.cz();
		double[] w = TableFootprint.toWorld(lx, lz, facing.getStepX(), facing.getStepZ());
		float s = atWheel ? 1f : FULL_SCALE;
		float top = (float) (atWheel ? f.top() : f.top() + FeltFrame.CONTENT_LIFT);
		pose.translate((float) w[0], top, (float) w[1]);
		pose.scale(s, s, s);
		pose.translate(-0.5f, -legacyTop, -0.5f);
	}

	/**
	 * MT-3: for a full (multi-block) machine, maps content drawn for the v0.1 1×1 machine (wheel, Plinko ball and
	 * lamps) onto the full face: {@code full.centre + s·(p − compact.centre)} with {@code s} =
	 * {@link BoardFrame#scaleCompactToFull}. Compact machines and slots (s = 1, same centre) are untouched.
	 */
	public static void toFullFace(PoseStack pose, TablePart part, @Nullable MachineKind kind, Direction facing) {
		if (kind == null || !part.isMultiBlock()) {
			return;
		}
		BoardFrame c = BoardFrame.of(kind, TablePart.SINGLE);
		BoardFrame f = BoardFrame.of(kind, TablePart.MAIN);
		float s = (float) BoardFrame.scaleCompactToFull(kind);
		double[] cw = TableFootprint.toWorld(c.cx(), c.faceZ(), facing.getStepX(), facing.getStepZ());
		double[] fw = TableFootprint.toWorld(f.cx(), f.faceZ(), facing.getStepX(), facing.getStepZ());
		pose.translate((float) fw[0], (float) f.cy(), (float) fw[1]);
		pose.scale(s, s, s);
		pose.translate((float) -cw[0], (float) -c.cy(), (float) -cw[1]);
	}
}
