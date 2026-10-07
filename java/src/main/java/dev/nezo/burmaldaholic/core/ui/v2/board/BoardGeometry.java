package dev.nezo.burmaldaholic.core.ui.v2.board;

/**
 * Geometry of the in-world table board (docs/architecture/ui-v2.md §7.1, REDESIGN.md §9.1), in BLOCK-LOCAL table
 * coordinates: x → right, z → toward the front (the side the block faces, where the players stand: z = 1), y up; the
 * block's own top face is y = 1, the block centre is (0.5, 0.5). The dealer side is z small. The board renderer
 * rotates this frame by the block's {@code FACING}, so per-game code never deals with directions.
 *
 * <p>The felt is a render-only overhang centred on the block: a rounded rectangle {@link Felt#w} × {@link Felt#d}
 * lying {@link #FELT_LIFT} above the block top with a {@link #RAIL} lip and an {@link #APRON} below it. When a
 * neighbour blocks the overhang the felt falls back to the block's own 1 × 1 top ({@link #fallback}).
 *
 * <p>Space a table needs (worldgen / casino buildings): the overhang reaches {@link #reachX} beyond the block on the
 * left and right (perpendicular to {@code FACING}) and {@link #reachZ} at the front and back, so a table needs ONE free
 * (non-colliding) block on each long side and two tables standing side by side need TWO free blocks between them
 * (centre pitch ≥ 3). Pure; no Minecraft types.
 */
public final class BoardGeometry {
	/** Felt surface above the block top (no z-fighting with the block's own felt texture). */
	public static final float FELT_LIFT = 1 / 64f;
	/** y of the felt surface. */
	public static final float TOP = 1f + FELT_LIFT;
	/** Width of the rail lip printed around the felt (blocks). */
	public static final float RAIL = 0.08f;
	/** Height of the wooden apron under the felt edge (blocks). */
	public static final float APRON = 0.12f;

	/** In-world card size: 1.5 × the screen card, readable from 3–8 blocks (REDESIGN §9.1). */
	public static final float CARD_W = 0.22f, CARD_H = 0.30f;
	/** Cards lie 1/32 above the felt and lean 20° toward their owner. */
	public static final float CARD_LIFT = 1 / 32f, CARD_TILT = 20f;
	/** Layer step between overlapping cards (no z-fighting). */
	public static final float LAYER = 0.0015f;

	/** Chip disc radius / height; stacks hold ≤ {@link #CHIP_MAX} discs. */
	public static final float CHIP_R = 0.07f, CHIP_H = 0.025f;
	public static final int CHIP_MAX = 6;
	/** Chip denominations of {@code chips_w.png}, left to right. */
	public static final long[] DENOMS = {1, 5, 25, 100, 500, 1000, 5000};

	/** Seat plate anchor: outside the felt edge by this much, raised this much above the felt. */
	public static final float PLATE_OUT = 0.12f, PLATE_UP = 0.35f;
	/** Table board: centred over the dealer side, this high above the felt. */
	public static final float BOARD_UP = 1.25f;

	/** Felt shapes. */
	public enum Felt {
		/** Blackjack, baccarat, UTH: 2.25 × 1.25, rounded at the front (the players' arc). */
		CRESCENT("crescent", 2.25f, 1.25f, 0.45f, 0.10f),
		/** Roulette, craps: 2.0 × 1.4. */
		RECT("rect", 2.0f, 1.4f, 0.14f, 0.14f),
		/** Hold'em: 2.0 × 2.0 oval. */
		OVAL("oval", 2.0f, 2.0f, 0.75f, 0.75f),
		/** The block's own top (overhang blocked). */
		FALLBACK("fallback", 1.0f, 1.0f, 0.06f, 0.06f);

		public final String id;
		/** Width (along x) and depth (along z) in blocks. */
		public final float w, d;
		/** Corner radii at the front (z max) and back (z min). */
		public final float frontR, backR;

		Felt(String id, float w, float d, float frontR, float backR) {
			this.id = id;
			this.w = w;
			this.d = d;
			this.frontR = frontR;
			this.backR = backR;
		}

		public float x0() {
			return 0.5f - w / 2;
		}

		public float x1() {
			return 0.5f + w / 2;
		}

		public float z0() {
			return 0.5f - d / 2;
		}

		public float z1() {
			return 0.5f + d / 2;
		}

		/** Overhang beyond the block on the left / right. */
		public float reachX() {
			return Math.max(0, (w - 1) / 2);
		}

		/** Overhang beyond the block at the front / back. */
		public float reachZ() {
			return Math.max(0, (d - 1) / 2);
		}
	}

	private BoardGeometry() {}

	/** The felt actually drawn: {@code felt}, or {@link Felt#FALLBACK} when the overhang is blocked. */
	public static Felt fallback(Felt felt, boolean blocked) {
		return blocked ? Felt.FALLBACK : felt;
	}

	/**
	 * Scale of the content laid out for {@code wanted} when {@code drawn} is the felt actually drawn: 1 normally; on
	 * the fallback felt the inside of the rail of the wanted felt maps into the inside of the drawn one (uniform, so
	 * cards keep their aspect).
	 */
	public static float contentScale(Felt wanted, Felt drawn) {
		if (wanted == drawn) return 1f;
		float sx = (drawn.w - 2 * RAIL) / (wanted.w - 2 * RAIL);
		float sz = (drawn.d - 2 * RAIL) / (wanted.d - 2 * RAIL);
		return Math.min(1f, Math.min(sx, sz));
	}

	/**
	 * Cells (dx, dz in table-local block steps, before rotation) the overhang covers beside the block: used for the
	 * blocked check. Returns pairs {dx0, dz0, dx1, dz1, …}.
	 */
	public static int[] overhangCells(Felt felt) {
		int rx = (int) Math.ceil(felt.reachX() - 1e-4);
		int rz = (int) Math.ceil(felt.reachZ() - 1e-4);
		int[] out = new int[((2 * rx + 1) * (2 * rz + 1) - 1) * 2];
		int n = 0;
		for (int dx = -rx; dx <= rx; dx++) {
			for (int dz = -rz; dz <= rz; dz++) {
				if (dx == 0 && dz == 0) continue;
				out[n++] = dx;
				out[n++] = dz;
			}
		}
		return out;
	}

	/**
	 * Outline of the felt (counter-clockwise seen from above, block-local x, z pairs): a rounded rectangle with
	 * {@code seg} segments per corner. Used for the apron quads; the felt texture is generated from the same shape.
	 */
	public static float[] outline(Felt f, int seg) {
		float[] out = new float[4 * (seg + 1) * 2];
		int n = 0;
		// corner centres + radius: back-left, front-left, front-right, back-right (z grows toward the front)
		float[][] corners = {
			{f.x0() + f.backR, f.z0() + f.backR, f.backR},
			{f.x0() + f.frontR, f.z1() - f.frontR, f.frontR},
			{f.x1() - f.frontR, f.z1() - f.frontR, f.frontR},
			{f.x1() - f.backR, f.z0() + f.backR, f.backR},
		};
		// angles measured from +x toward +z; the walk is continuous around the rectangle
		float[][] sweep = {{270, 180}, {180, 90}, {90, 0}, {360, 270}};
		for (int c = 0; c < 4; c++) {
			float[] k = corners[c];
			for (int i = 0; i <= seg; i++) {
				double a = Math.toRadians(sweep[c][0] + (sweep[c][1] - sweep[c][0]) * i / (double) seg);
				out[n++] = k[0] + k[2] * (float) Math.cos(a);
				out[n++] = k[1] + k[2] * (float) Math.sin(a);
			}
		}
		return out;
	}

	/** True when (x, z) lies on the felt (inside the rounded rectangle). */
	public static boolean inside(Felt f, float x, float z) {
		if (x < f.x0() || x > f.x1() || z < f.z0() || z > f.z1()) return false;
		float r = z > 0.5f ? f.frontR : f.backR;
		float cx = Math.max(f.x0() + r, Math.min(f.x1() - r, x));
		float cz = z > 0.5f ? Math.min(z, f.z1() - r) : Math.max(z, f.z0() + r);
		float dx = x - cx;
		float dz = z - cz;
		return dx * dx + dz * dz <= r * r + 1e-6f;
	}

	/**
	 * Crescent seat {@code seat} of {@code seats} (0 = the players' left end, seen from the front): the betting spot
	 * on an arc around the dealer. Returns {x, z, yaw°}: yaw turns a card's top edge toward the dealer.
	 */
	public static float[] crescentSeat(int seat, int seats) {
		int n = Math.max(1, seats);
		float u = n == 1 ? 0.5f : seat / (float) (n - 1);
		double a = Math.toRadians((u - 0.5) * 2 * 62); // −62° … 62°
		float x = 0.5f + 0.86f * (float) Math.sin(a);
		float z = 0.10f + 0.66f * (float) Math.cos(a);
		// the seat looks at the dealer (0.5, 0.10): yaw 0 = card top toward −z
		float yaw = (float) Math.toDegrees(a);
		return new float[] {x, z, -yaw};
	}

	/** Oval seat {@code seat} of {@code seats} around the centre (0 at the front), {x, z, yaw°}. */
	public static float[] ovalSeat(int seat, int seats) {
		int n = Math.max(2, seats);
		double a = 2 * Math.PI * Math.floorMod(seat, n) / n;
		float x = 0.5f + 0.70f * (float) Math.sin(a);
		float z = 0.5f + 0.70f * (float) Math.cos(a);
		return new float[] {x, z, (float) -Math.toDegrees(a)};
	}

	/** Dealer card row slot {@code k} of {@code n} on the dealer side (cards face the room). */
	public static float[] dealerSlot(Felt f, int k, int n) {
		float pitch = CARD_W * 0.62f;
		float x = 0.5f + (k - (n - 1) / 2f) * pitch;
		float z = f.z0() + RAIL + CARD_H / 2 + 0.06f;
		return new float[] {x, z, 0};
	}

	/**
	 * Seat plate anchor for a seat whose spot is (sx, sz): the point where the ray from the felt centre through the
	 * spot leaves the felt, pushed {@link #PLATE_OUT} further out, {@link #PLATE_UP} above the felt. Returns {x, y, z}.
	 */
	public static float[] plateAnchor(Felt f, float sx, float sz) {
		float cx = 0.5f;
		float cz = f == Felt.CRESCENT ? 0.10f : 0.5f;
		float dx = sx - cx;
		float dz = sz - cz;
		float len = (float) Math.sqrt(dx * dx + dz * dz);
		if (len < 1e-4f) {
			dx = 0;
			dz = 1;
			len = 1;
		}
		dx /= len;
		dz /= len;
		// march outwards to the felt edge (cheap, deterministic; ≤ 64 steps of 1/32 block)
		float t = 0;
		float x = sx;
		float z = sz;
		for (int i = 0; i < 96 && inside(f, x, z); i++) {
			t += 1 / 32f;
			x = sx + dx * t;
			z = sz + dz * t;
		}
		return new float[] {x + dx * PLATE_OUT, TOP + PLATE_UP, z + dz * PLATE_OUT};
	}

	/** Board anchor (bottom centre of the board): over the dealer side, {x, y, z}. */
	public static float[] boardAnchor(Felt f) {
		float z = f == Felt.OVAL || f == Felt.FALLBACK ? 0.5f : f.z0() + 0.25f;
		return new float[] {0.5f, TOP + BOARD_UP, z};
	}

	/**
	 * Splits {@code amount} into ≤ {@link #CHIP_MAX} discs, largest at the bottom (greedy over {@link #DENOMS}); writes
	 * denomination indices into {@code out} and returns the count. A huge amount shows the top denomination ×
	 * {@link #CHIP_MAX}; a positive amount below 1 never happens (whole chips).
	 */
	public static int discs(long amount, int[] out) {
		int n = 0;
		long left = amount;
		for (int d = DENOMS.length - 1; d >= 0 && n < CHIP_MAX && n < out.length; d--) {
			while (left >= DENOMS[d] && n < CHIP_MAX && n < out.length) {
				left -= DENOMS[d];
				out[n++] = d;
			}
		}
		return n;
	}
}
