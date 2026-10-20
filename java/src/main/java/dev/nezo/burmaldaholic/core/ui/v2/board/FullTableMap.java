package dev.nezo.burmaldaholic.core.ui.v2.board;

import dev.nezo.burmaldaholic.core.table.parts.FeltFrame;
import dev.nezo.burmaldaholic.core.ui.v2.board.BoardGeometry.Felt;

/**
 * The design → footprint mapping of a full 3×2 table (MT-DESIGN §2) and the heights the board draws at on MT's
 * furniture: felt {@link FeltFrame#FELT_Y} (14/16), padded rail top {@link FeltFrame#RAIL_TOP} (15.5/16). A design
 * point p lands at {@code origin + scale · (p − (0.5, TOP, 0.5))} in footprint-local coordinates (before the FACING
 * rotation). Pure; the client's {@code BoardRenderer.layout} and plate anchors use it, BoardLayersTest proves it.
 *
 * @param scale   uniform design → footprint scale
 * @param originX footprint x of the design centre (0.5)
 * @param originY footprint y of {@link BoardGeometry#TOP}: the felt top plus {@link FeltFrame#CONTENT_LIFT}
 * @param originZ footprint z of the design centre (0.5)
 */
public record FullTableMap(float scale, float originX, float originY, float originZ) {
	/** Lift of the felt print decal above the baked felt: under every prop that stands on the felt (MT's tray floor is 0.1 px). */
	public static final float DECAL_LIFT = 1 / 256f;
	/** Plate anchors (and their leader lines' ends) stand this far above the padded rail top. */
	public static final float PLATE_ABOVE_RAIL = 0.03f;
	/** Footprint y of a full table's plate anchors. */
	public static final float PLATE_Y = (float) FeltFrame.RAIL_TOP + PLATE_ABOVE_RAIL;
	/** The roulette rotor's radius as a fraction of the bowl radius (just inside the rim). */
	public static final float WHEEL_FILL = 0.96f;

	/**
	 * Mapping of the design felt {@code wanted} onto the full felt {@code f}. Roulette ({@link FeltFrame#hasWheel()}):
	 * the design wheel centred ({@code wheelX}, {@code wheelZ}) from the design centre, of radius {@code wheelR} lands on the furniture's bowl;
	 * otherwise the design felt fits the felt uniformly ({@link BoardGeometry#fullScale}), centre on centre.
	 */
	public static FullTableMap of(FeltFrame f, Felt wanted, float wheelX, float wheelZ, float wheelR) {
		float y = (float) (f.top() + FeltFrame.CONTENT_LIFT);
		if (f.hasWheel()) {
			float k = (float) (f.wheelDiameter() / 2 * WHEEL_FILL / wheelR);
			return new FullTableMap(k, (float) f.wheelCx() - k * wheelX, y, (float) f.wheelCz() - k * wheelZ);
		}
		return new FullTableMap(BoardGeometry.fullScale(wanted, f.w(), f.d()), (float) f.cx(), y, (float) f.cz());
	}

	public float footX(float x) {
		return originX + scale * (x - 0.5f);
	}

	public float footY(float y) {
		return originY + scale * (y - BoardGeometry.TOP);
	}

	public float footZ(float z) {
		return originZ + scale * (z - 0.5f);
	}

	public float designX(float fx) {
		return 0.5f + (fx - originX) / scale;
	}

	public float designY(float fy) {
		return BoardGeometry.TOP + (fy - originY) / scale;
	}

	public float designZ(float fz) {
		return 0.5f + (fz - originZ) / scale;
	}

	/** Footprint y of the print decal. */
	public static float decalY(FeltFrame f) {
		return (float) f.top() + DECAL_LIFT;
	}

	/**
	 * A full table's plate anchor, FOOTPRINT-local {x, y, z}, for the seat whose design spot (or design anchor) is
	 * (sx, sz): from the dealer (crescent: just inside the back of the felt), the wheel side (roulette) or the felt
	 * centre, through the seat, out of the felt to the middle of the padded rail — the seat point on the rail where the
	 * leader line ends — at {@link #PLATE_Y}.
	 */
	public float[] plateAnchor(FeltFrame f, Felt felt, float sx, float sz) {
		double fx = footX(sx), fz = footZ(sz);
		double ox = f.cx(), oz = felt == Felt.CRESCENT ? f.cz() - f.d() / 2 + 0.2 : f.cz();
		if (f.hasWheel()) ox = 0.5;
		double dx = fx - ox, dz = fz - oz;
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 1e-4) {
			dx = 0;
			dz = 1;
			len = 1;
		}
		dx /= len;
		dz /= len;
		double x = fx, z = fz;
		// an anchor given outside the felt (the design anchors sit past the design rail): walk back onto the felt first
		for (int i = 0; i < 160 && !f.onFelt(x, z); i++) {
			x -= dx / 32;
			z -= dz / 32;
		}
		for (int i = 0; i < 160 && f.onFelt(x, z); i++) {
			x += dx / 32;
			z += dz / 32;
		}
		// on the padded rail at the seat (visual QA round 1: plates and leader lines ended over the floor)
		x += dx * FeltFrame.RAIL * 0.5;
		z += dz * FeltFrame.RAIL * 0.5;
		return new float[] {(float) x, PLATE_Y, (float) z};
	}
}
