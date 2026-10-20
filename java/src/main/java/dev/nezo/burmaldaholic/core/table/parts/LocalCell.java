package dev.nezo.burmaldaholic.core.table.parts;

/**
 * One cell of a multi-block footprint in the block's local frame: {@code r} cells to the players'
 * right, {@code u} cells up, {@code f} cells toward the players, holding {@code part}. The main
 * (controller) cell is (0, 0, 0). Pure.
 */
public record LocalCell(int r, int u, int f, TablePart part) {
	/** World offset {dx, dy, dz} from the main block for facing (fx, fz). */
	public int[] offset(int fx, int fz) {
		int[] o = TableFootprint.cellOffset(r, f, fx, fz);
		return new int[] {o[0], u, o[1]};
	}
}
