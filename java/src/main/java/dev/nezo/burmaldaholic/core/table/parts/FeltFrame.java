package dev.nezo.burmaldaholic.core.table.parts;

/**
 * Where the felt of a table lies, in footprint-local coordinates (see {@link TableFootprint}: main
 * block x∈[0,1], z∈[0,1]; x = players' right, z = toward the players). Pure; consumed by the board
 * renderer (W2: {@code BoardGeometry.Felt} → {@code FeltFrame.of(game, part)}), seat points, the
 * asset contract test and worldgen.
 *
 * @param shape       felt outline
 * @param cx          felt centre x (footprint-local)
 * @param cz          felt centre z (footprint-local)
 * @param w           felt width (along x), inside the rail
 * @param d           felt depth (along z), inside the rail
 * @param top         felt surface height above the block's y; content is drawn at {@code top + CONTENT_LIFT}
 * @param rail        padded rail width around the felt (0 for compact)
 * @param cornerRadius felt corner radius of the rounded corners (front corners for CRESCENT, all for OVAL)
 * @param wheelCx     roulette wheel bowl centre x (NaN when there is no bowl)
 * @param wheelCz     roulette wheel bowl centre z (NaN when there is no bowl)
 * @param wheelDiameter roulette wheel bowl diameter (0 when there is no bowl)
 * @param compact     legacy 1×1 table ({@link TablePart#SINGLE})
 */
public record FeltFrame(TableGame.FeltShape shape, double cx, double cz, double w, double d, double top, double rail,
		double cornerRadius, double wheelCx, double wheelCz, double wheelDiameter, boolean compact) {
	/** Felt surface of a full table: 14/16 (★3). */
	public static final double FELT_Y = 14 / 16.0;
	/** Top of the padded rail of a full table. */
	public static final double RAIL_TOP = 15 / 16.0;
	/** Lift of drawn content (cards, chips, decal) above the felt. */
	public static final double CONTENT_LIFT = 1 / 64.0;
	/** Rail width round the felt. */
	public static final double RAIL = 0.125;
	/** Compact (SINGLE) felt: today's 1×1 block top. */
	public static final double COMPACT_TOP = 1.0 + CONTENT_LIFT;
	/** Rounded felt corner radius (CRESCENT front corners, OVAL all corners). */
	public static final double CORNER = 0.875;

	/** Felt frame of {@code game} drawn by a block of {@code part}; any non-SINGLE part gives the whole-table frame. */
	public static FeltFrame of(TableGame game, TablePart part) {
		if (part == TablePart.SINGLE) {
			return new FeltFrame(game.shape(), 0.5, 0.5, 1.0, 1.0, COMPACT_TOP, 0.0, 0.0, Double.NaN, Double.NaN, 0.0, true);
		}
		double fullW = game.width() - 2 * RAIL; // 2.75
		double fullD = game.depth() - 2 * RAIL; // 1.75
		double cx = 0.5;
		double cz = 0.0;
		return switch (game.shape()) {
			case CRESCENT, OVAL -> new FeltFrame(game.shape(), cx, cz, fullW, fullD, FELT_Y, RAIL, CORNER, Double.NaN, Double.NaN, 0, false);
			case RECT -> new FeltFrame(game.shape(), cx, cz, fullW, fullD, FELT_Y, RAIL, 0, Double.NaN, Double.NaN, 0, false);
			// wheel bowl fills column r-1 (centre x=-0.5, Ø1.0: its wooden rim is the rail there), betting layout 1.875 wide on r0..1 inside the right rail
			case ROULETTE -> new FeltFrame(game.shape(), 2 - RAIL - 1.875 / 2, cz, 1.875, fullD, FELT_Y, RAIL, 0, -0.5, 0.0, 1.0, false);
		};
	}

	public boolean hasWheel() {
		return wheelDiameter > 0;
	}

	/** Outer rail corner radius of the front ({@code front=true}, toward players) or back corners. */
	public double outerCornerRadius(boolean front) {
		boolean rounded = switch (shape) {
			case OVAL -> true;
			case CRESCENT -> front;
			default -> false;
		};
		return rounded && !compact ? cornerRadius + rail : 0;
	}

	/** Footprint-local origin {x, z} of a part block's min corner (the main block is at (0, 0)). */
	public static double[] partOrigin(TablePart part) {
		return new double[] {part.r(), part.f()};
	}

	/**
	 * Whether footprint-local (x, z) lies on the felt (inside the rail, honouring rounded corners).
	 * Roulette: the betting layout rectangle or the wheel bowl.
	 */
	public boolean onFelt(double x, double z) {
		if (hasWheel() && Math.hypot(x - wheelCx, z - wheelCz) <= wheelDiameter / 2) {
			return true;
		}
		double hx = w / 2;
		double hz = d / 2;
		double px = Math.abs(x - cx);
		double pz = Math.abs(z - cz);
		if (px > hx || pz > hz) {
			return false;
		}
		boolean front = z - cz > 0;
		double rc = switch (shape) {
			case OVAL -> cornerRadius;
			case CRESCENT -> front ? cornerRadius : 0;
			default -> 0;
		};
		rc = Math.min(rc, Math.min(hx, hz));
		if (rc > 0 && px > hx - rc && pz > hz - rc) {
			return Math.hypot(px - (hx - rc), pz - (hz - rc)) <= rc;
		}
		return true;
	}
}
