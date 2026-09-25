package dev.nezo.burmaldaholic.games.slots.cabinet;

import dev.nezo.burmaldaholic.core.anim.TimelineSeed;
import dev.nezo.burmaldaholic.core.anim.TimingProfile;
import dev.nezo.burmaldaholic.core.anim.WinTier;
import dev.nezo.burmaldaholic.games.slots.v2.logic.Anticipation;
import dev.nezo.burmaldaholic.games.slots.v2.logic.Machine;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * What a slot cabinet publishes to spectators once per spin ("SpinSync", slots.md §5.2, SLOTS.md §10.5,
 * docs/architecture/animation.md §2.5): the {@link TimelineSeed} plus the PUBLIC section of the tape the in-world
 * cabinet may show, flattened into explicit times so every viewer lands on the same tick as the acting player's
 * screen without needing the machine config (fidelity F10).
 *
 * <p>All times are integer ms from {@code startTick} at the SHARED speed (turbo already applied by the server,
 * which takes them from the same {@code SlotTimeline} its settle gate uses). {@link Builder} fills any time the
 * server leaves out with the SLOTS.md §10.2 defaults, so the module can publish before lane S-J3 lands.
 *
 * <p>Travels as one {@code int[]} ({@link #encode()} / {@link #decode(int[])}) in the block entity update tag.
 * PURE: no Minecraft imports (JUnit + the server settle maths use it).
 *
 * @param machine     machine (art, strip lengths)
 * @param seq         spin counter of the block (a change restarts the client animation)
 * @param startTick   server game time of ms 0
 * @param seed        cosmetic seed ({@code SeedMix.mix(posHash, seq)}); wheel rest offsets, idle choice
 * @param speedPct    shared speed of the acting player (100, or 200 for machine turbo)
 * @param gateMs      reveal gate (shared end): tier text, marquee celebration and world FX start here
 * @param strips      the machine's 5 reel strips (symbol indices in SLOTS.md §2 order) — config data, public
 * @param prevStops   stops the reels rested on before this spin (continuity of the spin-up)
 * @param spins       base spin (index 0, absent for a bought feature) then every free spin, in tape order
 * @param hoard       Piglin's Hoard or {@code null}
 * @param wheel       Dragon Wheel or {@code null}
 * @param hunt        Treasure Hunt progress or {@code null} (published again after every pick, F7)
 * @param tier        server tier of the whole spin (slot table), never derived by the client
 * @param totalChips  spin total excluding progressive awards
 * @param jackpotTier 0 none, 1 Mini … 4 Grand (highest awarded)
 * @param maxWin      the max-win cap ended the spin
 */
public record CabinetSync(Machine machine, int seq, long startTick, int seed, int speedPct, int gateMs, int[][] strips,
		int[] prevStops, List<Spin> spins, Hoard hoard, Wheel wheel, Hunt hunt, WinTier tier, long totalChips, int jackpotTier,
		boolean maxWin) {
	/** Codec version (first int of {@link #encode()}). */
	public static final int VERSION = 1;
	public static final int REELS = 5;
	public static final int ROWS = 3;
	public static final int CELLS = 15;

	/**
	 * One spin on the reels (base or free).
	 *
	 * @param startMs   spin-up start
	 * @param stops     5 stops (strip index shown in the top row)
	 * @param stopMs    per reel, when it is settled, ms from {@code startMs} (anticipation included, F3)
	 * @param anticipationMask bit r: reel r anticipated (honest plan from the server)
	 * @param heldMask  bit r: reel r does not spin (End sticky wild from an earlier free spin)
	 * @param window    the 15 landed cells (cell = reel × 3 + row) — the strip window
	 * @param tumbles   Nether tumble steps in order (empty elsewhere)
	 * @param winMask   winning cells of the FINAL window of this spin (after tumbles)
	 * @param winShowMs when the final win frames start, ms from {@code startMs}
	 * @param stickyAfter bit r: reel r is a sticky expanded wild after this spin (End free spins)
	 */
	public record Spin(int startMs, int[] stops, int[] stopMs, int anticipationMask, int heldMask, int[] window,
			List<Tumble> tumbles, int winMask, int winShowMs, int stickyAfter) {
		public Spin {
			stops = check(stops, REELS);
			stopMs = check(stopMs, REELS);
			window = check(window, CELLS);
			tumbles = List.copyOf(tumbles);
		}

		/** Settled time of the last spinning reel (ms from {@code startMs}). */
		public int lastStopMs() {
			int m = 0;
			for (int r = 0; r < REELS; r++) if ((heldMask & 1 << r) == 0) m = Math.max(m, stopMs[r]);
			return m;
		}

		/** The window after every tumble (what this spin paid on last). */
		public int[] finalWindow() {
			return tumbles.isEmpty() ? window.clone() : tumbles.getLast().window();
		}

		/** Newly stuck reels this spin. */
		public int newSticky() {
			return stickyAfter & ~heldMask;
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Spin s && s.startMs == startMs && Arrays.equals(s.stops, stops) && Arrays.equals(s.stopMs, stopMs)
				&& s.anticipationMask == anticipationMask && s.heldMask == heldMask && Arrays.equals(s.window, window)
				&& s.tumbles.equals(tumbles) && s.winMask == winMask && s.winShowMs == winShowMs && s.stickyAfter == stickyAfter;
		}

		@Override
		public int hashCode() {
			return Arrays.hashCode(window) * 31 + startMs;
		}
	}

	/**
	 * One Nether tumble step.
	 *
	 * @param atMs        explode start, ms from the spin start (the step's win overview runs just before)
	 * @param explodeMask cells that took part in a win and burn away
	 * @param multiplier  ladder multiplier of this step ({@code ×N} text)
	 * @param window      the 15 cells after the fall
	 */
	public record Tumble(int atMs, int explodeMask, int multiplier, int[] window) {
		public Tumble {
			window = check(window, CELLS);
		}

		@Override
		public int[] window() {
			return window.clone();
		}

		/** Symbol of cell {@code i} after the fall (no copy). */
		public int cell(int i) {
			return window[i];
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Tumble t && t.atMs == atMs && t.explodeMask == explodeMask && t.multiplier == multiplier
				&& Arrays.equals(t.window, window);
		}

		@Override
		public int hashCode() {
			return Arrays.hashCode(window) * 31 + atMs;
		}
	}

	/**
	 * Piglin's Hoard (hold and spin).
	 *
	 * @param startMs   intro start
	 * @param initialMask coin cells at the start
	 * @param values    per cell: coin value code (positive = × bet, -1…-4 = Mini…Grand; 0 = no coin ever)
	 * @param stepMs    respin starts (ms from sync start)
	 * @param stepMasks coin mask after each respin
	 * @param endMs     end of the collect; the cabinet returns to the reels
	 */
	public record Hoard(int startMs, int initialMask, int[] values, int[] stepMs, int[] stepMasks, int endMs) {
		public Hoard {
			values = check(values, CELLS);
			if (stepMs.length != stepMasks.length) throw new IllegalArgumentException("hoard steps");
			stepMs = stepMs.clone();
			stepMasks = stepMasks.clone();
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Hoard h && h.startMs == startMs && h.initialMask == initialMask && Arrays.equals(h.values, values)
				&& Arrays.equals(h.stepMs, stepMs) && Arrays.equals(h.stepMasks, stepMasks) && h.endMs == endMs;
		}

		@Override
		public int hashCode() {
			return startMs * 31 + initialMask;
		}
	}

	/**
	 * Dragon Wheel on top of the End cabinet.
	 *
	 * @param startMs   intro start (the wheel rises)
	 * @param ringSizes wedges per ring reached (outer 20, middle 16, core 12)
	 * @param segments  landed wedge per ring reached (1–3 rings)
	 * @param spinMs    per ring, spin start (ms from sync start)
	 * @param spinDurMs per ring, spin duration
	 * @param endMs     the wheel starts to sink
	 */
	public record Wheel(int startMs, int[] ringSizes, int[] segments, int[] spinMs, int[] spinDurMs, int endMs) {
		public Wheel {
			int n = segments.length;
			if (n < 1 || n > 3 || ringSizes.length != n || spinMs.length != n || spinDurMs.length != n)
				throw new IllegalArgumentException("wheel rings");
			ringSizes = ringSizes.clone();
			segments = segments.clone();
			spinMs = spinMs.clone();
			spinDurMs = spinDurMs.clone();
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Wheel w && w.startMs == startMs && Arrays.equals(w.ringSizes, ringSizes)
				&& Arrays.equals(w.segments, segments) && Arrays.equals(w.spinMs, spinMs) && Arrays.equals(w.spinDurMs, spinDurMs)
				&& w.endMs == endMs;
		}

		@Override
		public int hashCode() {
			return Arrays.hashCode(segments) * 31 + startMs;
		}
	}

	/**
	 * Treasure Hunt board: only the chests opened so far (entry i is published at pick i, D6/F7).
	 *
	 * @param startMs board appears
	 * @param cells   opened chest cells in pick order
	 * @param entries their contents (1…25 coins × bet, -1…-4 Mini…Grand, 0 Creeper)
	 * @param endMs   board ends (-1 = still running)
	 */
	public record Hunt(int startMs, int[] cells, int[] entries, int endMs) {
		public Hunt {
			if (cells.length != entries.length) throw new IllegalArgumentException("hunt picks");
			cells = cells.clone();
			entries = entries.clone();
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Hunt h && h.startMs == startMs && Arrays.equals(h.cells, cells) && Arrays.equals(h.entries, entries)
				&& h.endMs == endMs;
		}

		@Override
		public int hashCode() {
			return Arrays.hashCode(cells) * 31 + startMs;
		}
	}

	public CabinetSync {
		if (strips.length != REELS) throw new IllegalArgumentException("5 strips expected");
		int[][] s = new int[REELS][];
		for (int r = 0; r < REELS; r++) {
			if (strips[r].length < ROWS) throw new IllegalArgumentException("strip " + r + " too short");
			s[r] = strips[r].clone();
		}
		strips = s;
		prevStops = check(prevStops, REELS);
		spins = List.copyOf(spins);
		if (speedPct < 25 || speedPct > 400) throw new IllegalArgumentException("speedPct " + speedPct);
		if (jackpotTier < 0 || jackpotTier > 4) throw new IllegalArgumentException("jackpotTier " + jackpotTier);
	}

	private static int[] check(int[] a, int n) {
		if (a.length != n) throw new IllegalArgumentException(n + " values expected, got " + a.length);
		return a.clone();
	}

	/** The seed every consumer rebuilds the timeline from ({@code slots.<machine>}). */
	public TimelineSeed timelineSeed() {
		return new TimelineSeed("slots." + machine.id, seq, startTick, seed, speedPct);
	}

	/** Symbol at strip index {@code i} (any integer) of reel {@code r}. */
	public int symbolAt(int r, int i) {
		int[] s = strips[r];
		return s[Math.floorMod(i, s.length)];
	}

	/** Strip window for 5 stops (cell = reel × 3 + row), same rule as {@code MachineDef.symbolAt}. */
	public static int[] windowOf(int[][] strips, int[] stops) {
		int[] w = new int[CELLS];
		for (int r = 0; r < REELS; r++) {
			for (int y = 0; y < ROWS; y++) w[r * ROWS + y] = strips[r][Math.floorMod(stops[r] + y, strips[r].length)];
		}
		return w;
	}

	/** The window the machine rests on before this spin. */
	public int[] restWindow() {
		return windowOf(strips, prevStops);
	}

	/** The window the player finally sees on the reels (last spin, after its tumbles), or the rest window. */
	public int[] finalWindow() {
		return spins.isEmpty() ? restWindow() : spins.getLast().finalWindow();
	}

	/** Stops the reels rest on after this sync (the next spin starts from here). */
	public int[] finalStops() {
		return spins.isEmpty() ? prevStops.clone() : spins.getLast().stops().clone();
	}

	/**
	 * End of everything the cabinet animates (ms from start); after this the frame is constant
	 * ({@link CabinetFrames#terminal}). Includes the tier text and the win frames.
	 */
	public int endMs() {
		int end = gateMs + CabinetFrames.TIER_TEXT_MS;
		for (Spin s : spins) {
			end = Math.max(end, s.startMs + s.lastStopMs() + CabinetFrames.SQUASH_MS);
			end = Math.max(end, s.startMs + s.winShowMs + CabinetFrames.WIN_FRAMES_MS);
			for (Tumble t : s.tumbles) end = Math.max(end, s.startMs + t.atMs + CabinetFrames.TUMBLE_MS + CabinetFrames.MULT_TEXT_MS);
			if (s.newSticky() != 0) end = Math.max(end, s.startMs + s.lastStopMs() + CabinetFrames.STICK_END_MS);
		}
		if (hoard != null) end = Math.max(end, hoard.endMs);
		if (wheel != null) end = Math.max(end, wheel.endMs + CabinetFrames.WHEEL_SINK_MS);
		if (hunt != null) end = Math.max(end, hunt.endMs < 0 ? Integer.MAX_VALUE / 4 : hunt.endMs);
		return end;
	}

	// ---- codec ----------------------------------------------------------------------------------

	/** Compact int encoding for the block entity update tag (≈ 4 bytes per value, ≤ ~1 000 values). */
	public int[] encode() {
		IntWriter w = new IntWriter();
		w.put(VERSION).put(machine.ordinal()).put(seq).putLong(startTick).put(seed).put(speedPct).put(gateMs);
		w.put(tier.ordinal()).putLong(totalChips).put(jackpotTier).put(maxWin ? 1 : 0);
		for (int r = 0; r < REELS; r++) w.put(strips[r].length).putAll(strips[r]);
		w.putAll(prevStops);
		w.put(spins.size());
		for (Spin s : spins) {
			w.put(s.startMs).putAll(s.stops).putAll(s.stopMs).put(s.anticipationMask).put(s.heldMask).putAll(s.window)
				.put(s.winMask).put(s.winShowMs).put(s.stickyAfter).put(s.tumbles.size());
			for (Tumble t : s.tumbles) w.put(t.atMs).put(t.explodeMask).put(t.multiplier).putAll(t.window);
		}
		if (hoard == null) {
			w.put(0);
		} else {
			w.put(1).put(hoard.startMs).put(hoard.initialMask).putAll(hoard.values).put(hoard.endMs).put(hoard.stepMs.length)
				.putAll(hoard.stepMs).putAll(hoard.stepMasks);
		}
		if (wheel == null) {
			w.put(0);
		} else {
			w.put(wheel.segments.length).put(wheel.startMs).put(wheel.endMs).putAll(wheel.ringSizes).putAll(wheel.segments)
				.putAll(wheel.spinMs).putAll(wheel.spinDurMs);
		}
		if (hunt == null) {
			w.put(0);
		} else {
			w.put(1).put(hunt.startMs).put(hunt.endMs).put(hunt.cells.length).putAll(hunt.cells).putAll(hunt.entries);
		}
		return w.toArray();
	}

	/** Inverse of {@link #encode()}; throws {@link IllegalArgumentException} on a malformed or newer array. */
	public static CabinetSync decode(int[] a) {
		IntReader in = new IntReader(a);
		int v = in.get();
		if (v != VERSION) throw new IllegalArgumentException("cabinet sync version " + v);
		Machine machine = Machine.values()[in.index(Machine.values().length)];
		int seq = in.get();
		long startTick = in.getLong();
		int seed = in.get();
		int speed = in.get();
		int gate = in.get();
		WinTier tier = WinTier.values()[in.index(WinTier.values().length)];
		long total = in.getLong();
		int jackpot = in.get();
		boolean maxWin = in.get() != 0;
		int[][] strips = new int[REELS][];
		for (int r = 0; r < REELS; r++) strips[r] = in.array(in.count(512));
		int[] prev = in.array(REELS);
		int nSpins = in.count(256);
		List<Spin> spins = new ArrayList<>(nSpins);
		for (int i = 0; i < nSpins; i++) {
			int start = in.get();
			int[] stops = in.array(REELS);
			int[] stopMs = in.array(REELS);
			int ant = in.get();
			int held = in.get();
			int[] window = in.array(CELLS);
			int winMask = in.get();
			int winShow = in.get();
			int sticky = in.get();
			int nT = in.count(64);
			List<Tumble> tumbles = new ArrayList<>(nT);
			for (int k = 0; k < nT; k++) tumbles.add(new Tumble(in.get(), in.get(), in.get(), in.array(CELLS)));
			spins.add(new Spin(start, stops, stopMs, ant, held, window, tumbles, winMask, winShow, sticky));
		}
		Hoard hoard = null;
		if (in.get() != 0) {
			int start = in.get();
			int mask = in.get();
			int[] values = in.array(CELLS);
			int end = in.get();
			int n = in.count(64);
			hoard = new Hoard(start, mask, values, in.array(n), in.array(n), end);
		}
		Wheel wheel = null;
		int rings = in.get();
		if (rings != 0) {
			if (rings < 1 || rings > 3) throw new IllegalArgumentException("wheel rings " + rings);
			int start = in.get();
			int end = in.get();
			wheel = new Wheel(start, in.array(rings), in.array(rings), in.array(rings), in.array(rings), end);
		}
		Hunt hunt = null;
		if (in.get() != 0) {
			int start = in.get();
			int end = in.get();
			int n = in.count(CELLS);
			hunt = new Hunt(start, in.array(n), in.array(n), end);
		}
		if (!in.done()) throw new IllegalArgumentException("trailing data in cabinet sync");
		return new CabinetSync(machine, seq, startTick, seed, speed, gate, strips, prev, spins, hoard, wheel, hunt, tier, total,
			jackpot, maxWin);
	}

	private static final class IntWriter {
		private int[] buf = new int[256];
		private int n;

		IntWriter put(int v) {
			if (n == buf.length) buf = Arrays.copyOf(buf, n * 2);
			buf[n++] = v;
			return this;
		}

		IntWriter putLong(long v) {
			return put((int) (v >>> 32)).put((int) v);
		}

		IntWriter putAll(int[] a) {
			for (int v : a) put(v);
			return this;
		}

		int[] toArray() {
			return Arrays.copyOf(buf, n);
		}
	}

	private static final class IntReader {
		private final int[] a;
		private int i;

		IntReader(int[] a) {
			this.a = a;
		}

		int get() {
			if (i >= a.length) throw new IllegalArgumentException("truncated cabinet sync");
			return a[i++];
		}

		long getLong() {
			long hi = get();
			return hi << 32 | get() & 0xFFFFFFFFL;
		}

		int count(int max) {
			int c = get();
			if (c < 0 || c > max) throw new IllegalArgumentException("bad count " + c);
			return c;
		}

		int index(int size) {
			int c = get();
			if (c < 0 || c >= size) throw new IllegalArgumentException("bad index " + c);
			return c;
		}

		int[] array(int len) {
			if (i + len > a.length) throw new IllegalArgumentException("truncated cabinet sync");
			int[] out = Arrays.copyOfRange(a, i, i + len);
			i += len;
			return out;
		}

		boolean done() {
			return i == a.length;
		}
	}

	// ---- builder with the SLOTS.md §10.2 default schedule ----------------------------------------

	public static Builder builder(Machine machine, int seq, long startTick, int seed, int speedPct, int[][] strips, int[] prevStops) {
		return new Builder(machine, seq, startTick, seed, speedPct, strips, prevStops);
	}

	/**
	 * Assembles a sync in tape order. Every "At" is optional: when the server gives no explicit time (its
	 * {@code SlotTimeline} is not in yet) the SLOTS.md §10.2 / slots.md §2.3 tokens are used, scaled by the
	 * shared speed. Free spins use {@code FS_SPIN_SCALE} = 0.8.
	 */
	public static final class Builder {
		/** slots.md §2.3 tokens (normal speed). */
		public static final int WIN_DELAY_MS = 150;
		public static final int WIN_ALL_MS = 900;
		public static final int STEP_OVERVIEW_MS = 600;
		public static final int REST_MS = 200;
		public static final int FS_INTRO_MS = 2000;
		public static final int FS_OUTRO_MS = 1500;
		public static final int HOARD_INTRO_MS = 800;
		public static final int HOARD_RESPIN_MS = 900;
		public static final int HOARD_COLLECT_PER_COIN_MS = 120;
		public static final int WHEEL_INTRO_MS = 700;
		public static final int WHEEL_UP_MS = 800;
		public static final int WHEEL_LAND_MS = 600;
		public static final int[] WHEEL_SPIN_MS = {4500, 4000, 5000};
		public static final int[] WHEEL_RINGS = {20, 16, 12};

		private final Machine machine;
		private final int seq;
		private final long startTick;
		private final int seed;
		private final TimingProfile speed;
		private final int[][] strips;
		private final int[] prevStops;
		private final List<Spin> spins = new ArrayList<>();
		private Hoard hoard;
		private Wheel wheel;
		private Hunt hunt;
		private WinTier tier = WinTier.LOSS;
		private long total;
		private int jackpot;
		private boolean maxWin;
		private int cursor;
		private int gate = -1;

		private Builder(Machine machine, int seq, long startTick, int seed, int speedPct, int[][] strips, int[] prevStops) {
			this.machine = machine;
			this.seq = seq;
			this.startTick = startTick;
			this.seed = seed;
			this.speed = TimingProfile.SHARED.withSpeed(speedPct);
			this.strips = strips;
			this.prevStops = prevStops.clone();
		}

		private int ms(int base, boolean free) {
			return speed.scale(free ? base * 4 / 5 : base);
		}

		/** Current end of the schedule (ms). */
		public int cursor() {
			return cursor;
		}

		/** Moves the schedule to an explicit time (e.g. the server timeline's FS_SPIN start). */
		public Builder at(int ms) {
			cursor = ms;
			return this;
		}

		/** Adds a pause (FS intro, bonus intro) at normal speed. */
		public Builder pause(int ms) {
			cursor += speed.scale(ms);
			return this;
		}

		/** Free-spin intro banner (FS_INTRO). */
		public Builder freeSpinsIntro() {
			return pause(FS_INTRO_MS);
		}

		/**
		 * Starts a spin at the cursor with the given stops.
		 *
		 * @param stopMs  per-reel settle times from the spin start (server anticipation plan), or {@code null}
		 *                for the base schedule
		 * @param antMask reels that anticipated (0 without a plan)
		 */
		public SpinBuilder spin(int[] stops, int[] stopMs, int antMask) {
			return new SpinBuilder(this, stops, stopMs, antMask, !spins.isEmpty());
		}

		public SpinBuilder spin(int[] stops) {
			return spin(stops, null, 0);
		}

		/** Piglin's Hoard: intro at the cursor, one respin per mask, then the collect. */
		public Builder hoard(int initialMask, int[] values, int[] stepMasks) {
			int start = cursor;
			int t = start + speed.scale(HOARD_INTRO_MS);
			int[] stepMs = new int[stepMasks.length];
			for (int i = 0; i < stepMasks.length; i++) {
				stepMs[i] = t;
				t += speed.scale(HOARD_RESPIN_MS);
			}
			int finalMask = stepMasks.length == 0 ? initialMask : stepMasks[stepMasks.length - 1];
			t += speed.scale(HOARD_COLLECT_PER_COIN_MS * Integer.bitCount(finalMask) + 600);
			hoard = new Hoard(start, initialMask, values, stepMs, stepMasks, t);
			cursor = t;
			return this;
		}

		/** Dragon Wheel: rises at the cursor, then one spin per ring reached (UP zooms in between). */
		public Builder wheel(int[] segments) {
			int n = segments.length;
			int start = cursor;
			int t = start + speed.scale(WHEEL_INTRO_MS);
			int[] rings = Arrays.copyOf(WHEEL_RINGS, n);
			int[] at = new int[n];
			int[] dur = new int[n];
			for (int i = 0; i < n; i++) {
				at[i] = t;
				dur[i] = speed.scale(WHEEL_SPIN_MS[i]);
				t += dur[i] + speed.scale(WHEEL_LAND_MS) + (i < n - 1 ? speed.scale(WHEEL_UP_MS) : 0);
			}
			wheel = new Wheel(start, rings, segments, at, dur, t);
			cursor = t;
			return this;
		}

		/** Treasure Hunt board with the picks made so far ({@code ended} closes the board at the cursor). */
		public Builder hunt(int startMs, int[] cells, int[] entries, boolean ended) {
			hunt = new Hunt(startMs, cells, entries, ended ? Math.max(cursor, startMs) : -1);
			return this;
		}

		/** Server result (tier from {@code SlotTiers}, never derived here). */
		public Builder result(WinTier tier, long totalChips, int jackpotTier, boolean maxWin) {
			this.tier = tier;
			this.total = totalChips;
			this.jackpot = jackpotTier;
			this.maxWin = maxWin;
			return this;
		}

		/** Explicit reveal gate (the server's {@code timeline.sharedEndMs()}); default = end of the schedule. */
		public Builder gate(int ms) {
			gate = ms;
			return this;
		}

		public CabinetSync build() {
			int g = gate >= 0 ? gate : cursor;
			return new CabinetSync(machine, seq, startTick, seed, speed.speedPct(), g, strips, prevStops, spins, hoard, wheel, hunt, tier,
				total, jackpot, maxWin);
		}
	}

	/** One spin under construction; {@link #done()} returns to the sync builder with the cursor after it. */
	public static final class SpinBuilder {
		private final Builder parent;
		private final boolean free;
		private final int start;
		private final int[] stops;
		private final int[] stopMs;
		private final int antMask;
		private int held;
		private int sticky;
		private int winMask;
		private int winShowAt = -1;
		private final List<Tumble> tumbles = new ArrayList<>();
		private int stepCursor;

		private SpinBuilder(Builder parent, int[] stops, int[] stopMs, int antMask, boolean free) {
			this.parent = parent;
			this.free = free;
			this.start = parent.cursor;
			this.stops = stops.clone();
			if (stopMs != null) {
				this.stopMs = stopMs.clone();
			} else {
				int[] base = Anticipation.baseStopTimes();
				this.stopMs = new int[REELS];
				for (int r = 0; r < REELS; r++) this.stopMs[r] = parent.ms(base[r], free);
			}
			this.antMask = antMask;
		}

		private int lastStop() {
			int m = 0;
			for (int r = 0; r < REELS; r++) if ((held & 1 << r) == 0) m = Math.max(m, stopMs[r]);
			return m;
		}

		/** Reels held by sticky wilds from earlier free spins (they do not spin). */
		public SpinBuilder held(int reelMask) {
			held = reelMask;
			return this;
		}

		/** Sticky reels after this spin (End free spins; includes {@link #held}). */
		public SpinBuilder stickyAfter(int reelMask) {
			sticky = reelMask;
			return this;
		}

		/** A tumble step: explode {@code explodeMask}, fall into {@code windowAfter}; {@code atMs} ≤ 0 = default. */
		public SpinBuilder tumble(int explodeMask, int multiplier, int[] windowAfter, int atMs) {
			if (stepCursor == 0) stepCursor = lastStop() + parent.ms(Builder.WIN_DELAY_MS, free);
			int at = atMs > 0 ? atMs : stepCursor + parent.ms(Builder.STEP_OVERVIEW_MS, free);
			tumbles.add(new Tumble(at, explodeMask, multiplier, windowAfter));
			stepCursor = at + parent.ms(CabinetFrames.TUMBLE_MS, free);
			return this;
		}

		public SpinBuilder tumble(int explodeMask, int multiplier, int[] windowAfter) {
			return tumble(explodeMask, multiplier, windowAfter, 0);
		}

		/** Winning cells of the final window; {@code atMs} ≤ 0 = default (last stop / last tumble + 150). */
		public SpinBuilder wins(int mask, int atMs) {
			winMask = mask;
			winShowAt = atMs;
			return this;
		}

		public SpinBuilder wins(int mask) {
			return wins(mask, 0);
		}

		public Builder done() {
			int settle = tumbles.isEmpty() ? lastStop() : stepCursor;
			int winAt = winShowAt > 0 ? winShowAt : settle + parent.ms(Builder.WIN_DELAY_MS, free);
			int end = winMask != 0 ? winAt + parent.ms(Builder.WIN_ALL_MS, free) : settle + parent.ms(Builder.REST_MS, free);
			if ((sticky & ~held) != 0) end = Math.max(end, lastStop() + CabinetFrames.STICK_END_MS);
			int[] window = windowOf(parent.strips, stops);
			parent.spins.add(new Spin(start, stops, stopMs, antMask, held, window, tumbles, winMask, winAt, sticky | held));
			parent.cursor = start + end;
			return parent;
		}
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof CabinetSync c)) return false;
		return Arrays.equals(encode(), c.encode());
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(encode());
	}
}
