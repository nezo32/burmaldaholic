package dev.nezo.burmaldaholic.games.blackjack.present;

/**
 * In-world geometry of the blackjack (and baccarat) crescent table (REDESIGN.md §9.1, docs/architecture/ui-v2.md §7):
 * block-local coordinates on the felt top (x → right, z → toward the players at the front, y = 1 the felt), the
 * dealer along the straight far edge (z small), the seats on the arc near the front. The felt is 2.25 × 1.25 blocks,
 * centred on the block (render-only overhang). Pure; sizes are in blocks.
 */
public final class BlackjackWorld {
	private BlackjackWorld() {}

	/** Felt size (crescent tables) and its local bounds. */
	public static final float FELT_W = 2.25f, FELT_D = 1.25f;
	public static final float X0 = 0.5f - FELT_W / 2, X1 = 0.5f + FELT_W / 2, Z0 = 0.5f - FELT_D / 2, Z1 = 0.5f + FELT_D / 2;
	/** Dealer cards (1.5 × real, REDESIGN §9.1) and seat cards. */
	public static final float DEALER_W = 0.20f, DEALER_H = 0.28f, SEAT_W = 0.16f, SEAT_H = 0.22f;
	/** Fan steps. */
	public static final float DEALER_STEP = 0.12f, SEAT_STEP = 0.075f;
	/** Dealer row (card centres). */
	public static final float DEALER_Z = Z0 + 0.25f;
	/** The point the seats' cards face (just behind the dealer row). */
	public static final float FACE_X = 0.5f, FACE_Z = Z0 - 0.4f;
	/** Plates: outside the rail and above the felt (ui-v2.md §2.4). */
	public static final float PLATE_OUT = 0.12f, PLATE_UP = 0.35f;
	/** Board: centred over the dealer side, above the felt. */
	public static final float BOARD_UP = 1.25f;

	/** Centre x of dealer card {@code i} of {@code n}. */
	public static float dealerX(int i, int n) {
		return 0.5f + (i - (n - 1) / 2f) * DEALER_STEP;
	}

	/** The shoe (deal origin) at the dealer's right, and the discard tray at the left. */
	public static float[] shoe() {
		return new float[] {X1 - 0.35f, Z0 + 0.22f};
	}

	public static float[] discard() {
		return new float[] {X0 + 0.35f, Z0 + 0.22f};
	}

	/**
	 * Anchor of seat slot {@code slot} of {@code slots} on the arc: {x, z, yaw°}; yaw turns the cards so their top edge
	 * faces the dealer (the formula of {@code CardTableRenderer}: up = (sin yaw, −cos yaw)).
	 */
	public static float[] seat(int slot, int slots) {
		float u = slots <= 1 ? 0.5f : slot / (float) (slots - 1);
		float d = 2 * u - 1;
		float x = 0.5f + d * (FELT_W / 2 - 0.36f);
		float z = Z0 + 0.62f + 0.30f * (1 - d * d);
		float yaw = (float) Math.toDegrees(Math.atan2(FACE_X - x, -(FACE_Z - z)));
		return new float[] {x, z, yaw};
	}

	/**
	 * Centre of card {@code k} of hand {@code h} ({@code hands} hands) at a seat: hands side by side across the seat,
	 * each fanned along the seat's own x axis. Returns {x, z}.
	 */
	public static float[] seatCard(float[] seat, int h, int hands, int k, int n) {
		float handPitch = SEAT_W + SEAT_STEP * 2 + 0.03f;
		float off = (h - (hands - 1) / 2f) * handPitch + (k - (n - 1) / 2f) * SEAT_STEP;
		double a = Math.toRadians(seat[2]);
		float lift = -k * 0.02f; // later cards a little toward the dealer, so the index of each stays visible
		return new float[] {(float) (seat[0] + off * Math.cos(a) - lift * Math.sin(a)), (float) (seat[1] + off * Math.sin(a) + lift * Math.cos(a))};
	}

	/** The seat's bet stack: toward the player from the cards. */
	public static float[] stack(float[] seat) {
		double a = Math.toRadians(seat[2]);
		float d = SEAT_H / 2 + 0.10f;
		return new float[] {(float) (seat[0] - d * Math.sin(a)), (float) (seat[1] + d * Math.cos(a))};
	}

	/** Plate anchor of a seat: the rail edge on the seat's side, {@link #PLATE_OUT} outside the felt. Returns {x, y, z}. */
	public static float[] plate(float[] seat) {
		double a = Math.toRadians(seat[2]);
		float dx = (float) -Math.sin(a);
		float dz = (float) Math.cos(a);
		// march outward until the felt edge
		float t = 0;
		while (t < 2 && inside(seat[0] + dx * t, seat[1] + dz * t)) t += 0.01f;
		t += PLATE_OUT;
		return new float[] {seat[0] + dx * t, 1 + PLATE_UP, seat[1] + dz * t};
	}

	public static boolean inside(float x, float z) {
		return x >= X0 && x <= X1 && z >= Z0 && z <= Z1;
	}

	/** The table board anchor: over the dealer side. Returns {x, y, z}. */
	public static float[] board() {
		return new float[] {0.5f, 1 + BOARD_UP, Z0 + 0.1f};
	}

	/** W-atlas face index (rank-major, ace high: {@code (rank − 2) × 4 + suit}) of a game card code ({@code suit × 13 + rank − 1}). */
	public static int face(int code) {
		if (code < 0 || code >= 52) return -1;
		int rank = code % 13 + 1; // 1..13, ace = 1
		int suit = code / 13;
		int high = rank == 1 ? 14 : rank;
		return (high - 2) * 4 + suit;
	}
}
