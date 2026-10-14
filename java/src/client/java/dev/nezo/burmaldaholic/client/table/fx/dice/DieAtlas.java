package dev.nezo.burmaldaholic.client.table.fx.dice;

import dev.nezo.burmaldaholic.client.fx.motion.SnapDraw;
import dev.nezo.burmaldaholic.client.table.fx.TableGfx;
import dev.nezo.burmaldaholic.core.anim.dice.DieAtlasTable;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * The die orientation atlas on screen (PHASE2B §3.1, CS §4.2): sheet {@code gui/tables/core/dice_atlas_<cell>.png} of
 * {@link DieAtlasTable#FRAMES} frames, 20 columns, the die centre at the frame centre. Frame choice is the pure
 * {@link DieAtlasTable#frame} (nearest quaternion, so a rest pose is texel-exact); drawing is a 1:1 region blit at a
 * float position snapped to physical pixels ({@link SnapDraw}), never scaled.
 */
public final class DieAtlas {
	private static final Identifier[] SHEETS = new Identifier[DieAtlasTable.CELLS.length];

	static {
		for (int i = 0; i < SHEETS.length; i++) SHEETS[i] = TableGfx.sheet("core/dice_atlas_" + DieAtlasTable.CELLS[i]);
	}

	private DieAtlas() {}

	/** Index of {@code cell} in {@link DieAtlasTable#CELLS}; throws for a size without a sheet. */
	public static int cellIndex(int cell) {
		int i = DieAtlasTable.cellIndex(cell);
		if (i < 0) throw new IllegalArgumentException("no dice atlas for cell " + cell);
		return i;
	}

	/** Frame size in px of a cell. */
	public static int frameSize(int cell) {
		return DieAtlasTable.FRAME[cellIndex(cell)];
	}

	/** The sheet texture of a cell. */
	public static Identifier sheet(int cell) {
		return SHEETS[cellIndex(cell)];
	}

	/**
	 * Draws frame {@code frame} of the {@code cell} sheet centred on GUI point (cx, cy), snapped to {@code 1/k}.
	 *
	 * @param argb tint / alpha multiplier (0xFFFFFFFF = as is)
	 */
	public static void blit(GuiGraphicsExtractor g, int cell, int frame, double cx, double cy, int k, int argb) {
		int ci = cellIndex(cell);
		int f = DieAtlasTable.FRAME[ci];
		int size = f * DieAtlasTable.COLS;
		int u = (frame % DieAtlasTable.COLS) * f;
		int v = (frame / DieAtlasTable.COLS) * f;
		SnapDraw.regionF(g, SHEETS[ci], size, size, u, v, f, f, cx - f / 2.0, cy - f / 2.0, argb, k);
	}

	/** Draws the frame nearest to orientation {@code q} centred on (cx, cy). */
	public static void blit(GuiGraphicsExtractor g, int cell, double[] q, double cx, double cy, int k, int argb) {
		blit(g, cell, DieAtlasTable.frame(q), cx, cy, k, argb);
	}
}
