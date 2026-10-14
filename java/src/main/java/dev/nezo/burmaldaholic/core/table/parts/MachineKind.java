package dev.nezo.burmaldaholic.core.table.parts;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Upright multi-block machines (MT-3): one row deep, {@link #width()} cells wide (always odd, so mirror() only swaps
 * LEFT/RIGHT parts — MT-DESIGN §1), {@link #height()} cells tall. The main block (block entity) is the BOTTOM CENTRE
 * cell; players stand in front (+FACING). Pure.
 *
 * <ul>
 *   <li>{@link #SLOT}: 1 × 2 cabinet — reels at eye level across the top of the lower block, lit topper sign in the
 *       upper block. Slot banks stand side by side with no gap.</li>
 *   <li>{@link #WHEEL}: 3 × 3 upright Wheel of Fortune on a stand.</li>
 *   <li>{@link #PLINKO}: 3 × 3 cabinet whose face carries the 272 × 204 art-px (4:3) board, 2.75 × 2.0625 blocks.</li>
 *   <li>{@link #CASHIER}: 3 × 2 booth — counter row with a grille window above the centre.</li>
 * </ul>
 */
public enum MachineKind {
	SLOT(1, 2),
	WHEEL(3, 3),
	PLINKO(3, 3),
	CASHIER(3, 2);

	private final int width;
	private final int height;

	MachineKind(int width, int height) {
		this.width = width;
		this.height = height;
	}

	public int width() {
		return width;
	}

	public int height() {
		return height;
	}

	/** Every cell (main first, then bottom row sideways, then upwards row by row). */
	public List<LocalCell> localCells() {
		List<LocalCell> out = new ArrayList<>(width * height);
		int half = width / 2;
		out.add(new LocalCell(0, 0, 0, TablePart.MAIN));
		for (int u = 0; u < height; u++) {
			for (int r = -half; r <= half; r++) {
				if (r == 0 && u == 0) {
					continue;
				}
				TablePart p = TablePart.at(r, u, 0);
				if (p == null) {
					throw new IllegalStateException(this + ": no part at " + r + "," + u);
				}
				out.add(new LocalCell(r, u, 0, p));
			}
		}
		return out;
	}

	/** The {@code part} values of this machine's block: SINGLE (compact) first, then the footprint cells. */
	public List<TablePart> partValues() {
		List<TablePart> out = new ArrayList<>();
		out.add(TablePart.SINGLE);
		for (LocalCell c : localCells()) {
			out.add(c.part());
		}
		return out;
	}

	/** Kind of a machine block registry path, or null for tables / other blocks. */
	public static @Nullable MachineKind ofBlockName(String path) {
		if (path.startsWith("slot_machine")) {
			return SLOT;
		}
		if (path.equals("wheel_of_fortune")) {
			return WHEEL;
		}
		if (path.equals("plinko_machine")) {
			return PLINKO;
		}
		if (path.equals("cashier") || path.equals("nether_cashier")) {
			return CASHIER;
		}
		return null;
	}
}
