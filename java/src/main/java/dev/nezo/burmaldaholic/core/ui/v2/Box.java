package dev.nezo.burmaldaholic.core.ui.v2;

/**
 * An integer rectangle in GUI px of the table screen's virtual GUI ({@link Viewport}). The one geometry type of the
 * table UI v2 kit (docs/architecture/ui-v2.md §3): layout, drawing and hit-testing all use the same boxes, so what is
 * drawn is exactly what is clicked.
 */
public record Box(int x, int y, int w, int h) {
	public static final Box EMPTY = new Box(0, 0, 0, 0);

	public Box {
		if (w < 0 || h < 0) throw new IllegalArgumentException("negative size " + w + "x" + h);
	}

	public int right() {
		return x + w;
	}

	public int bottom() {
		return y + h;
	}

	public int cx() {
		return x + w / 2;
	}

	public int cy() {
		return y + h / 2;
	}

	public boolean isEmpty() {
		return w == 0 || h == 0;
	}

	/** Half-open hit test: {@code [x, x + w) × [y, y + h)}. */
	public boolean contains(double px, double py) {
		return px >= x && px < x + w && py >= y && py < y + h;
	}

	public boolean intersects(Box o) {
		return !isEmpty() && !o.isEmpty() && x < o.right() && o.x < right() && y < o.bottom() && o.y < bottom();
	}

	public boolean inside(Box outer) {
		return x >= outer.x && y >= outer.y && right() <= outer.right() && bottom() <= outer.bottom();
	}

	public Box inset(int d) {
		return new Box(x + d, y + d, Math.max(0, w - 2 * d), Math.max(0, h - 2 * d));
	}

	public Box offset(int dx, int dy) {
		return new Box(x + dx, y + dy, w, h);
	}

	/** A {@code w × h} box centred on this one (rounded down, may stick out when larger). */
	public Box centred(int bw, int bh) {
		return new Box(x + (w - bw) / 2, y + (h - bh) / 2, bw, bh);
	}

	public static Box centredAt(int cx, int cy, int w, int h) {
		return new Box(cx - w / 2, cy - h / 2, w, h);
	}
}
