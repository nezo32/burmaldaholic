package dev.nezo.burmaldaholic.games.slots.v2.logic;

/**
 * Honest anticipation (SLOTS.md §10.3): {@code stopTimes(tape)} is the ONLY source of reel stop times for
 * the server settle timer, the Java screen, the BER and Bedrock. A reel slows down only because of symbols
 * already visible on stopped reels.
 *
 * <p>After reel k has stopped, the next reel anticipates (stops {@value #ANTICIPATE_GAP_MS} ms after reel k instead
 * of {@value #STAGGER_MS} ms) iff the cells already visible on reels 1…k contain:
 * <ul>
 *   <li>≥ 2 scatters and a later reel can still show a scatter (its strip has one; a sticky reel cannot); or</li>
 *   <li>base game only: the bonus symbol on every bonus reel except the last one, all of them stopped, and the last
 *       bonus reel not stopped (Overworld chests on 1 and 3 with reel 5 spinning; End crystals on 2 and 3 with reel 4
 *       spinning); or</li>
 *   <li>base game only (Nether): ≥ 4 coins and the remaining reels can still reach the Hoard trigger (6), using the
 *       most coins each remaining reel's strip can show in one window.</li>
 * </ul>
 * Nothing else ever changes stop times.
 */
public final class Anticipation {
	/** Base stop of reel r (0-based): 600 + 150 × r ms (SLOTS.md §10.2). */
	public static final int FIRST_STOP_MS = 600;
	public static final int STAGGER_MS = 150;
	public static final int ANTICIPATE_GAP_MS = 1000;

	private Anticipation() {}

	/** Base-game stop times; {@code enabled} = {@code slots.anticipation}. */
	public static int[] stopTimes(MachineDef def, Window landed, boolean enabled) {
		return stopTimes(def, landed, enabled, false, 0);
	}

	/**
	 * Stop time of each reel in ms from spin start, at normal speed.
	 *
	 * @param landed       the window as it lands (End free spins: sticky reels already WWW)
	 * @param free         a free spin (bonus symbols and coins are inert)
	 * @param stickyBefore End free spins: reels that are sticky (they cannot add a scatter)
	 */
	public static int[] stopTimes(MachineDef def, Window landed, boolean enabled, boolean free, int stickyBefore) {
		boolean[] a = anticipated(def, landed, enabled, free, stickyBefore);
		int[] t = new int[5];
		t[0] = FIRST_STOP_MS;
		for (int r = 1; r < 5; r++) t[r] = t[r - 1] + (a[r] ? ANTICIPATE_GAP_MS : STAGGER_MS);
		return t;
	}

	/** {@code result[r]}: reel r (0-based) anticipates, i.e. the condition holds on reels 0…r−1. */
	public static boolean[] anticipated(MachineDef def, Window landed, boolean enabled, boolean free, int stickyBefore) {
		boolean[] out = new boolean[5];
		if (!enabled) return out;
		for (int r = 1; r < 5; r++) out[r] = condition(def, landed, r - 1, free, stickyBefore);
		return out;
	}

	/** The anticipation condition after reel {@code k} (0-based) has stopped. */
	public static boolean condition(MachineDef def, Window w, int k, boolean free, int stickyBefore) {
		if (k >= 4) return false;
		int sc = def.scatter();
		int scat = 0;
		for (int r = 0; r <= k; r++) for (int y = 0; y < 3; y++) if (w.at(r, y) == sc) scat++;
		if (scat >= 2) {
			for (int r = k + 1; r < 5; r++) {
				boolean sticky = free && r >= 1 && r <= 3 && (stickyBefore >> (r - 1) & 1) != 0;
				if (!sticky && stripHas(def, r, sc)) return true;
			}
		}
		if (free) return false;
		int bonusMask = def.bonusReelsMask();
		int bo = def.bonus();
		if (bonusMask != 0 && bo >= 0) {
			int last = 31 - Integer.numberOfLeadingZeros(bonusMask);
			if (k < last) {
				boolean ok = true;
				int stoppedBonusReels = 0;
				for (int r = 0; r <= k; r++) {
					if ((bonusMask >> r & 1) == 0) continue;
					stoppedBonusReels++;
					boolean has = false;
					for (int y = 0; y < 3; y++) has |= w.at(r, y) == bo;
					ok &= has;
				}
				if (ok && stoppedBonusReels == Integer.bitCount(bonusMask) - 1) return true;
			}
		}
		int co = def.coin();
		int trigger = def.features().holdTrigger();
		if (co >= 0 && trigger > 0) {
			int coins = 0;
			for (int r = 0; r <= k; r++) for (int y = 0; y < 3; y++) if (w.at(r, y) == co) coins++;
			if (coins >= 4) {
				int more = 0;
				for (int r = k + 1; r < 5; r++) more += maxInWindow(def, r, co);
				if (coins + more >= trigger && more > 0) return true;
			}
		}
		return false;
	}

	private static boolean stripHas(MachineDef def, int reel, int symbol) {
		for (int x : def.strips()[reel]) if (x == symbol) return true;
		return false;
	}

	/** Most cells of {@code symbol} one window of reel {@code reel} can show. */
	static int maxInWindow(MachineDef def, int reel, int symbol) {
		int best = 0;
		for (int t = 0; t < def.stripLength(reel); t++) {
			int c = 0;
			for (int y = 0; y < 3; y++) if (def.symbolAt(reel, t, y) == symbol) c++;
			best = Math.max(best, c);
		}
		return best;
	}

	/** Schedule without anticipation (used when the rule never fires). */
	public static int[] baseStopTimes() {
		int[] t = new int[5];
		for (int r = 0; r < 5; r++) t[r] = FIRST_STOP_MS + STAGGER_MS * r;
		return t;
	}
}
