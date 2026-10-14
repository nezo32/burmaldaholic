package dev.nezo.burmaldaholic.core.anim.motion;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Pure voicing rules for motion sounds (PHASE2B §3.1; PL §6, RO §6, DI §7, CS §7), used by the client
 * {@code SoundCursor}. Sounds are OFFERED as their events fire during a frame, then {@link #flush flushed}:
 *
 * <ol>
 *   <li><b>Lead-object voicing:</b> while more than {@code crowdAbove} objects are in flight, contacts of objects other
 *       than the lead are voiced only every {@code othersEvery}-th time (0 = never): Plinko voices the lead ball and
 *       every second contact of the others; craps voices FELT/TIP of the lead die only.</li>
 *   <li><b>Merge window:</b> offers of the same id within {@code mergeMs} (default 30 ms, event time) become ONE sound,
 *       louder by {@code mergeBoost} per extra offer (two dice hitting the wall together = one "double clack"). An
 *       offer within the window of an id already voiced in an earlier frame is dropped.</li>
 *   <li><b>Per-id limiter:</b> at most {@link #limit} voiced sounds per second per id (event time; e.g.
 *       {@code plinko_peg} 12/s, dice 10/s); ids marked {@link #always} (bin, rest, outcome sounds) bypass the limiter
 *       and the lead rule.</li>
 * </ol>
 *
 * <p>The hitch rule (late events are silent) is the {@link EventCursor}'s: silent events are never offered. Time is
 * the caller's common clock in ms (pass the same clock for every object so the windows compare like with like).
 * Allocation-free after the first offer of each id; not thread-safe.
 */
public final class SoundMixer {
	/** Default merge window (ms). */
	public static final double MERGE_MS = 30;
	/** Default volume boost per merged extra offer. */
	public static final float MERGE_BOOST = 0.1f;

	/** {@link #offer} result: queued for the next flush. */
	public static final int QUEUED = 0;
	/** {@link #offer} result: merged into another sound (queued or already voiced). */
	public static final int MERGED = 1;
	/** {@link #offer} result: dropped by the per-id limiter. */
	public static final int LIMITED = 2;
	/** {@link #offer} result: dropped by the lead-object rule. */
	public static final int NOT_LEAD = 3;
	/** {@link #offer} result: dropped because the frame batch is full. */
	public static final int FULL = 4;

	/** Receives the voiced sounds of a {@link #flush}. */
	@FunctionalInterface
	public interface Voice {
		/**
		 * @param positional true for a world sound at (x, y, z), false for a personal (GUI) sound
		 */
		void play(String id, float volume, float pitch, int obj, boolean positional, double x, double y, double z);
	}

	private static final class IdState {
		int perSecond;
		boolean always;
		double[] ring;
		double lastVoiced = Double.NEGATIVE_INFINITY;
	}

	private static final int CAP = 32;
	private static final int OBJ_SLOTS = 16;

	private final double mergeMs;
	private final float mergeBoost;
	private final int defaultPerSecond;
	private final Map<String, IdState> ids = new HashMap<>();

	private int crowdAbove = Integer.MAX_VALUE;
	private int othersEvery = 1;
	private int crowd;
	private int lead = -1;
	private final int[] objKey = new int[OBJ_SLOTS];
	private final int[] objCount = new int[OBJ_SLOTS];

	private final String[] bId = new String[CAP];
	private final IdState[] bState = new IdState[CAP];
	private final float[] bVol = new float[CAP];
	private final float[] bPitch = new float[CAP];
	private final int[] bObj = new int[CAP];
	private final double[] bT = new double[CAP];
	private final boolean[] bPos = new boolean[CAP];
	private final double[] bx = new double[CAP];
	private final double[] by = new double[CAP];
	private final double[] bz = new double[CAP];
	private int bn;

	/** Defaults: 30 ms merge window, +0.1 per merged offer, 15 voiced sounds per second per id (the FxSounds cap). */
	public SoundMixer() {
		this(MERGE_MS, MERGE_BOOST, 15);
	}

	/**
	 * @param mergeMs          merge window (ms)
	 * @param mergeBoost       volume added per merged extra offer (capped at 1)
	 * @param defaultPerSecond per-id limit for ids without {@link #limit}
	 */
	public SoundMixer(double mergeMs, float mergeBoost, int defaultPerSecond) {
		if (mergeMs < 0 || defaultPerSecond < 1) throw new IllegalArgumentException("mixer");
		this.mergeMs = mergeMs;
		this.mergeBoost = mergeBoost;
		this.defaultPerSecond = defaultPerSecond;
		Arrays.fill(objKey, Integer.MIN_VALUE);
	}

	/** At most {@code perSecond} voiced sounds of {@code id} per second. */
	public SoundMixer limit(String id, int perSecond) {
		if (perSecond < 1) throw new IllegalArgumentException("perSecond " + perSecond);
		IdState s = state(id);
		s.perSecond = perSecond;
		s.ring = filled(perSecond);
		return this;
	}

	/** {@code id} always passes the limiter and the lead rule (bin, rest, outcome sounds). */
	public SoundMixer always(String id) {
		state(id).always = true;
		return this;
	}

	/**
	 * Lead-object voicing: with more than {@code crowdAbove} objects in flight, non-lead objects are voiced every
	 * {@code othersEvery}-th contact (0 = never, 1 = always).
	 */
	public SoundMixer leadVoicing(int crowdAbove, int othersEvery) {
		if (crowdAbove < 0 || othersEvery < 0) throw new IllegalArgumentException("lead");
		this.crowdAbove = crowdAbove;
		this.othersEvery = othersEvery;
		return this;
	}

	/** Current crowd: objects in flight and the lead object (call each frame before offering). */
	public void crowd(int objectsInFlight, int leadObj) {
		this.crowd = objectsInFlight;
		this.lead = leadObj;
	}

	/** Offers a personal (GUI) sound; see the class comment. Returns {@link #QUEUED}, {@link #MERGED}, …. */
	public int offer(String id, float volume, float pitch, int obj, double tMs) {
		return offer(id, volume, pitch, obj, tMs, false, 0, 0, 0);
	}

	/** Offers a positional (world) sound at (x, y, z). */
	public int offerAt(String id, float volume, float pitch, int obj, double tMs, double x, double y, double z) {
		return offer(id, volume, pitch, obj, tMs, true, x, y, z);
	}

	private int offer(String id, float volume, float pitch, int obj, double tMs, boolean pos, double x, double y, double z) {
		IdState s = state(id);
		if (!s.always && obj >= 0 && obj != lead && crowd > crowdAbove) {
			int c = ++objCount[slot(obj)];
			if (othersEvery == 0 || c % othersEvery != 0) return NOT_LEAD;
		}
		for (int i = 0; i < bn; i++) {
			if (bState[i] == s && Math.abs(bT[i] - tMs) <= mergeMs) {
				bVol[i] = Math.min(1f, Math.max(bVol[i], volume) + mergeBoost);
				return MERGED;
			}
		}
		if (tMs >= s.lastVoiced && tMs - s.lastVoiced < mergeMs) return MERGED;
		if (!s.always && !acquire(s, tMs)) return LIMITED;
		if (bn >= CAP) return FULL;
		int i = bn++;
		bId[i] = id;
		bState[i] = s;
		bVol[i] = volume;
		bPitch[i] = pitch;
		bObj[i] = obj;
		bT[i] = tMs;
		bPos[i] = pos;
		bx[i] = x;
		by[i] = y;
		bz[i] = z;
		return QUEUED;
	}

	/** Voices the queued sounds in offer order and clears the batch; returns how many were voiced. */
	public int flush(Voice voice) {
		int n = bn;
		for (int i = 0; i < n; i++) {
			IdState s = bState[i];
			if (bT[i] > s.lastVoiced) s.lastVoiced = bT[i];
			if (voice != null) voice.play(bId[i], bVol[i], bPitch[i], bObj[i], bPos[i], bx[i], by[i], bz[i]);
			bId[i] = null;
			bState[i] = null;
		}
		bn = 0;
		return n;
	}

	/** Sounds queued for the next flush. */
	public int pending() {
		return bn;
	}

	/** Forgets the batch, the limiter windows, the merge history and the lead counters (keeps the configuration). */
	public void reset() {
		for (int i = 0; i < bn; i++) {
			bId[i] = null;
			bState[i] = null;
		}
		bn = 0;
		for (IdState s : ids.values()) {
			s.lastVoiced = Double.NEGATIVE_INFINITY;
			if (s.ring != null) Arrays.fill(s.ring, Double.NEGATIVE_INFINITY);
		}
		Arrays.fill(objKey, Integer.MIN_VALUE);
		Arrays.fill(objCount, 0);
	}

	private IdState state(String id) {
		IdState s = ids.get(id);
		if (s == null) {
			s = new IdState();
			s.perSecond = defaultPerSecond;
			s.ring = filled(defaultPerSecond);
			ids.put(id, s);
		}
		return s;
	}

	private static double[] filled(int n) {
		double[] a = new double[n];
		Arrays.fill(a, Double.NEGATIVE_INFINITY);
		return a;
	}

	/** Sliding 1-second window per id (event time); a clock that went back forgets the "future" stamps. */
	private static boolean acquire(IdState s, double tMs) {
		double[] r = s.ring;
		int oldest = 0;
		for (int i = 0; i < r.length; i++) {
			if (r[i] > tMs) r[i] = Double.NEGATIVE_INFINITY;
			if (r[i] < r[oldest]) oldest = i;
		}
		if (tMs - r[oldest] < 1000) return false;
		r[oldest] = tMs;
		return true;
	}

	private int slot(int obj) {
		int h = Math.floorMod(obj, OBJ_SLOTS);
		for (int k = 0; k < OBJ_SLOTS; k++) {
			int i = (h + k) % OBJ_SLOTS;
			if (objKey[i] == obj) return i;
			if (objKey[i] == Integer.MIN_VALUE) {
				objKey[i] = obj;
				objCount[i] = 0;
				return i;
			}
		}
		objKey[h] = obj; // table full: recycle (counters are cosmetic)
		objCount[h] = 0;
		return h;
	}
}
