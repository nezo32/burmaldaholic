package dev.nezo.burmaldaholic.core.table.parts;

/**
 * Where the playing face of an upright machine sits (MT-3; the FeltFrame equivalent for machines). Footprint-local
 * like {@link TableFootprint}: x = players' right (main block x∈[0,1]), y = up from the main block's bottom, the
 * face plane at z = {@link #faceZ} (main block z∈[0,1], players on the +z side; 1.0 = the cabinet's front face).
 * Renderers (PL: Plinko board, W2: wheel / slot reels / cashier window) map their art onto
 * [cx − w/2, cx + w/2] × [cy − h/2, cy + h/2] at z = faceZ, facing +z. Pure.
 *
 * @param cx      face centre x
 * @param cy      face centre y
 * @param w       face width (blocks)
 * @param h       face height (blocks)
 * @param faceZ   depth of the face plane (1.0 = flush with the cabinet front; smaller = recessed)
 * @param compact legacy 1×1 machine ({@link TablePart#SINGLE}): today's v0.1 front-face geometry
 */
public record BoardFrame(MachineKind kind, double cx, double cy, double w, double h, double faceZ, boolean compact) {
	/** Plinko art: 272 × 204 px (4:3). */
	public static final double PLINKO_ASPECT = 272.0 / 204.0;

	/** Face of {@code kind} drawn by a block of {@code part}; any non-SINGLE part gives the full machine's face. */
	public static BoardFrame of(MachineKind kind, TablePart part) {
		if (part == TablePart.SINGLE) {
			return switch (kind) {
				// v0.1 geometry the renderers draw today: slot reels in the cabinet above the block (SlotCabinetRenderer
				// WIN_*), the wheel standing on its block (WheelOfFortuneRenderer R = 0.8, CY = 1.98), the Plinko face and
				// the cashier window painted on the block's front
				case SLOT -> new BoardFrame(kind, 0.5, 1.53125, 0.75, 0.5625, 0.875, true);
				case WHEEL -> new BoardFrame(kind, 0.5, 1.98, 1.6, 1.6, 0.56, true);
				case PLINKO -> new BoardFrame(kind, 0.5, 0.5, 1.0, 1.0, 1.0, true);
				case CASHIER -> new BoardFrame(kind, 0.5, 0.75, 0.75, 0.375, 1.0, true);
			};
		}
		return switch (kind) {
			// the slot keeps its v0.1 cabinet: the upper block now really exists under the drawn cabinet
			case SLOT -> new BoardFrame(kind, 0.5, 1.53125, 0.75, 0.5625, 0.875, false);
			// wheel disc Ø 2.625 on its stand, centre at 1.625 (the v0.1 wheel scaled × 1.64, post down to the floor)
			case WHEEL -> new BoardFrame(kind, 0.5, 1.625, 2.625, 2.625, 0.56, false);
			// 4:3 board 2.75 × 2.0625, bottom at 0.5 (cabinet base below, marquee above)
			case PLINKO -> new BoardFrame(kind, 0.5, 0.5 + 2.75 / PLINKO_ASPECT / 2, 2.75, 2.75 / PLINKO_ASPECT, 15.5 / 16.0, false);
			// grille window over the counter's centre cell
			case CASHIER -> new BoardFrame(kind, 0.5, 1.5, 1.0, 0.75, 12 / 16.0, false);
		};
	}

	/**
	 * Uniform scale + offset that maps v0.1 compact content onto the full machine's face: a point p of the compact
	 * frame goes to {@code full.centre + scale·(p − compact.centre)} (footprint-local, x/y and z alike). For Plinko the
	 * compact square face maps onto the board's height (the 4:3 width gets side frames). Slots: identity.
	 */
	public static double scaleCompactToFull(MachineKind kind) {
		BoardFrame c = of(kind, TablePart.SINGLE);
		BoardFrame f = of(kind, TablePart.MAIN);
		return f.h() / c.h();
	}

	public double left() {
		return cx - w / 2;
	}

	public double right() {
		return cx + w / 2;
	}

	public double bottom() {
		return cy - h / 2;
	}

	public double top() {
		return cy + h / 2;
	}
}
