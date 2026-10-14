package dev.nezo.burmaldaholic.core.anim.motion;

/**
 * Maps stage units (art px, tray units, arena die units) to viewer coordinates (layout px or blocks): an affine map
 * per axis plus a height scale and an optional mirror (PHASE2B §3.1: the craps spec's {@code ArenaMap} is MK
 * {@code StageMap}; DI §3.1: the right duel lane is the left lane mirrored, {@code x → W − x}).
 *
 * @param ox      viewer x of stage x = 0 (after mirroring)
 * @param oy      viewer y of stage y = 0
 * @param sx      viewer units per stage unit along x
 * @param sy      viewer units per stage unit along y
 * @param zScale  viewer units per stage unit of height (screen: lift up = subtract from y)
 * @param mirrorW stage width to mirror about; NaN = not mirrored
 */
public record StageMap(double ox, double oy, double sx, double sy, double zScale, double mirrorW) {
	/** An unmirrored map. */
	public static StageMap of(double ox, double oy, double sx, double sy, double zScale) {
		return new StageMap(ox, oy, sx, sy, zScale, Double.NaN);
	}

	/** The same map mirrored left ↔ right about a stage of width {@code w} ({@code x → w − x}). */
	public StageMap mirrored(double w) {
		return new StageMap(ox, oy, sx, sy, zScale, w);
	}

	/** True when this map mirrors x. */
	public boolean isMirrored() {
		return !Double.isNaN(mirrorW);
	}

	/** Viewer x of stage x. */
	public double x(double u) {
		return ox + sx * (isMirrored() ? mirrorW - u : u);
	}

	/** Viewer y of stage y (no height). */
	public double y(double v) {
		return oy + sy * v;
	}

	/** Viewer y of stage y lifted by height z (screen space: up is smaller y). */
	public double yLifted(double v, double z) {
		return oy + sy * v - zScale * z;
	}

	/** Viewer length of a height z. */
	public double lift(double z) {
		return zScale * z;
	}

	/** Stage x of a viewer x (inverse of {@link #x}). */
	public double u(double viewX) {
		double u = (viewX - ox) / sx;
		return isMirrored() ? mirrorW - u : u;
	}

	/** Stage y of a viewer y (inverse of {@link #y}). */
	public double v(double viewY) {
		return (viewY - oy) / sy;
	}
}
