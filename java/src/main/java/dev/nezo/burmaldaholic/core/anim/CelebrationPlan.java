package dev.nezo.burmaldaholic.core.anim;

/**
 * Pure timing and value maths of the Java {@code CelebrationOverlay} (global.md §2.6 table; lead decision L2:
 * per-game tier tables and words). Everything the overlay draws is a function of the local time {@code t}
 * (ms since the celebration started) through this class, so the fidelity rules are unit-testable:
 * <ul>
 *   <li>the roll-up is monotonic and its last frame is the exact server amount (global §6.2);</li>
 *   <li>the word only upgrades (never downgrades) and always ends on the server tier;</li>
 *   <li>skip jumps to the final frame (amount + word), holds 300 ms, then exits;</li>
 *   <li>reduce motion: roll-up ≤ 300 ms, static banner (no scale-in, no rays, no particles, no shake);</li>
 *   <li>{@code anim.celebrations = off}: every tier uses the WIN banner style (the word and amount stay).</li>
 * </ul>
 * Java only: Bedrock tells the same story with titles ({@code fx.celebrate}, ticks) and has its own plan.
 * Immutable; {@link #withSkipAt} returns a copy. Word thresholds and upgrade times are computed once at
 * construction, so sampling is allocation-free.
 */
public final class CelebrationPlan {
	/** Banner slide-in (8 px from below). */
	public static final int BANNER_IN_MS = 150;
	/** Hold after a skip before the exit. */
	public static final int SKIP_HOLD_MS = 300;
	/** Word swap punch (scale 1.2 → 1) after an upgrade. */
	public static final int PUNCH_MS = 150;
	/** Maximum count-up ticks per second (global §2.6). */
	public static final int MAX_TICKS_PER_S = 15;

	/** Roll-up maxima per tier ordinal (LOSS … JACKPOT), ms (global §2.6: WIN 400, BIG 1200 …). */
	private static final int[] ROLL_MAX = {0, 0, 0, 400, 600, 1200, 1800, 2400, 3000};
	/** Hold per tier ordinal (the table's "hold → exit" row; WIN's 0.8 s total = 400 + 200 + 200). */
	private static final int[] HOLD = {600, 600, 600, 200, 400, 500, 500, 400, 500};
	private static final int[] EXIT = {200, 200, 200, 200, 200, 300, 300, 400, 500};
	/** Backdrop dim per tier ordinal (0 = none), 0–1. */
	private static final float[] BACKDROP = {0, 0, 0, 0, 0, 0.30f, 0.40f, 0.50f, 0.55f};

	/**
	 * Builds the plan of one celebration.
	 *
	 * @param celebrationsOff {@code anim.celebrations = off}: WIN banner style for every tier
	 */
	public static CelebrationPlan of(WinTier tier, long ret, long stake, WinTierTable table, TimingProfile profile,
			boolean celebrationsOff) {
		WinTier style = celebrationsOff && tier.isWin() ? WinTier.WIN : tier;
		int max = ROLL_MAX[style.ordinal()];
		if (profile.reduceMotion()) max = Math.min(max, 300);
		int roll = tier.isWin() && ret > 0 ? RollUp.durationMs(ret, stake, Math.min(400, max), max) : 0;
		return new CelebrationPlan(tier, style, ret, stake, table, profile.scale(roll), profile.scale(HOLD[style.ordinal()]),
			profile.scale(EXIT[style.ordinal()]), profile.reduceMotion(), profile.speedPct(), -1);
	}

	private final WinTier tier;
	private final WinTier style;
	private final long ret;
	private final long stake;
	private final WinTierTable table;
	private final int rollMs;
	private final int holdMs;
	private final int exitMs;
	private final boolean reduced;
	private final int speedPct;
	private final int skipAt;
	/** Upgrade words (ordinals) and the amounts at which they appear, ascending. */
	private final WinTier[] upWords;
	private final long[] upAmounts;
	private final int[] upTimes;

	/**
	 * @param tier     final (server) tier
	 * @param style    presentation style: the tier, or {@link WinTier#WIN} when celebrations are off
	 * @param ret      total return rolled up (exact final frame)
	 * @param stake    stake the multiples refer to
	 * @param table    the caller's thresholds (upgrade beats)
	 * @param rollMs   roll-up duration (0 = no roll-up; non-win tiers)
	 * @param holdMs   hold after the roll-up
	 * @param exitMs   fade-out duration
	 * @param reduced  reduce motion
	 * @param speedPct {@code anim.speed} (all durations already scaled)
	 * @param skipAt   local ms of a skip, or −1
	 */
	public CelebrationPlan(WinTier tier, WinTier style, long ret, long stake, WinTierTable table, int rollMs, int holdMs,
			int exitMs, boolean reduced, int speedPct, int skipAt) {
		this.tier = tier;
		this.style = style;
		this.ret = ret;
		this.stake = stake;
		this.table = table;
		this.rollMs = Math.max(0, rollMs);
		this.holdMs = Math.max(0, holdMs);
		this.exitMs = Math.max(0, exitMs);
		this.reduced = reduced;
		this.speedPct = speedPct;
		this.skipAt = skipAt;
		boolean upgrades = tier.isOverlay() && tier != WinTier.JACKPOT && style == tier;
		WinTier start = upgrades ? firstWord(tier, table) : tier;
		long[][] pts = upgrades ? table.upgradePoints(stake, start, tier) : new long[0][];
		int n = 0;
		for (long[] p : pts) if (p[1] <= ret) n++;
		upWords = new WinTier[n + 1];
		upAmounts = new long[n + 1];
		upWords[0] = start;
		upAmounts[0] = 0;
		int k = 1;
		for (long[] p : pts) {
			if (p[1] > ret) continue;
			upWords[k] = WinTier.values()[(int) p[0]];
			upAmounts[k++] = p[1];
		}
		upTimes = new int[n];
		for (int i = 0; i < n; i++) upTimes[i] = firstMsReaching(upAmounts[i + 1]);
	}

	private static WinTier firstWord(WinTier tier, WinTierTable table) {
		for (WinTier w : new WinTier[] {WinTier.NICE, WinTier.BIG, WinTier.MEGA, WinTier.EPIC})
			if (table.multiple(w) > 0 && w.ordinal() <= tier.ordinal()) return w;
		return tier;
	}

	/** Smallest integer ms at which the (unskipped) roll-up shows at least {@code amount}. */
	private int firstMsReaching(long amount) {
		if (rollMs <= 0) return 0;
		int lo = 0;
		int hi = rollMs;
		while (lo < hi) {
			int mid = (lo + hi) >>> 1;
			if (RollUp.valueAt(ret, mid / (double) rollMs) >= amount) hi = mid;
			else lo = mid + 1;
		}
		return lo;
	}

	public WinTier tier() {
		return tier;
	}

	public WinTier style() {
		return style;
	}

	public long ret() {
		return ret;
	}

	public long stake() {
		return stake;
	}

	public WinTierTable table() {
		return table;
	}

	public int rollMs() {
		return rollMs;
	}

	public int holdMs() {
		return holdMs;
	}

	public int exitMs() {
		return exitMs;
	}

	public boolean reduced() {
		return reduced;
	}

	public int speedPct() {
		return speedPct;
	}

	public int skipAt() {
		return skipAt;
	}

	/** Same plan skipped at local {@code ms} (a second skip keeps the first). */
	public CelebrationPlan withSkipAt(int ms) {
		if (skipAt >= 0) return this;
		return new CelebrationPlan(tier, style, ret, stake, table, rollMs, holdMs, exitMs, reduced, speedPct, Math.max(0, ms));
	}

	public boolean skipped() {
		return skipAt >= 0;
	}

	/** True when the style uses the full overlay (backdrop, big word; BIG and above). */
	public boolean overlay() {
		return style.isOverlay();
	}

	/** Local ms at which the exit fade starts. */
	public int exitStart() {
		int normal = rollMs + holdMs;
		return skipAt >= 0 ? Math.min(normal, skipAt + SKIP_HOLD_MS) : normal;
	}

	/** Total length; the overlay is removed at this local ms. */
	public int endMs() {
		return exitStart() + exitMs;
	}

	public boolean done(double t) {
		return t >= endMs();
	}

	/** Roll-up progress in [0, 1] (1 after a skip). */
	public double rollProgress(double t) {
		if (skipAt >= 0 && t >= skipAt) return 1;
		if (rollMs <= 0) return 1;
		return Math.max(0, Math.min(1, t / rollMs));
	}

	/** Amount shown at {@code t}: monotonic, exact {@link #ret} from the end of the roll-up (or a skip). */
	public long amountAt(double t) {
		return RollUp.valueAt(ret, rollProgress(t));
	}

	/**
	 * The tier word shown at {@code t}. Non-overlay tiers show their own word. Overlay tiers start on the
	 * lowest word the caller's table defines above WIN (BIG for the default table, NICE for slots) and
	 * upgrade when the rolling amount passes each threshold of the table; JACKPOT shows its word at once.
	 * From the end of the roll-up (or a skip) the word is always the server tier.
	 */
	public WinTier wordAt(double t) {
		if (upWords.length == 1 && upWords[0] == tier) return tier;
		if (rollProgress(t) >= 1) return tier;
		long shown = amountAt(t);
		WinTier word = upWords[0];
		for (int i = 1; i < upWords.length; i++) if (shown >= upAmounts[i]) word = upWords[i];
		return word;
	}

	/** First word of the celebration (before any upgrade). */
	public WinTier startWord() {
		return upWords[0];
	}

	/**
	 * Local ms of every threshold upgrade (word change → {@code win_big} sting + punch), ascending. The final
	 * forced switch to the server tier at the end of the roll-up (e.g. EPIC reached only by the net rule) is
	 * not in this list; the overlay stings it when {@link #wordAt} changes.
	 */
	public int[] upgradeTimes() {
		return upTimes.clone();
	}

	/** Number of threshold upgrades. */
	public int upgradeCount() {
		return upTimes.length;
	}

	/** Local ms of upgrade {@code i}. */
	public int upgradeTime(int i) {
		return upTimes[i];
	}

	/** Word scale multiplier at {@code t}: scale-in (BIG/MEGA outBack 300 ms, EPIC/JACKPOT outElastic 600 ms) × punch. */
	public float wordScale(double t) {
		if (reduced) return 1;
		double s = 1;
		if (style.isOverlay()) {
			boolean elastic = style == WinTier.EPIC || style == WinTier.JACKPOT;
			int d = elastic ? 600 : 300;
			double p = Math.max(0, Math.min(1, t / Math.max(1, d * 100 / speedPct)));
			s = (elastic ? Ease.OUT_ELASTIC : Ease.OUT_BACK).apply(p);
		}
		for (int u : upTimes) {
			if (skipAt >= 0 && u >= skipAt) break;
			double dt = t - u;
			if (dt >= 0 && dt < PUNCH_MS) s *= 1.2 - 0.2 * Ease.OUT_CUBIC.apply(dt / PUNCH_MS);
		}
		return (float) Math.max(0, s);
	}

	/** Banner vertical offset (px, positive = below its rest position): 8 px → 0 over 150 ms outCubic. */
	public float bannerOffset(double t) {
		if (reduced) return 0;
		double p = Math.max(0, Math.min(1, t / BANNER_IN_MS));
		return (float) (8 * (1 - Ease.OUT_CUBIC.apply(p)));
	}

	/** Overall alpha: 1 until the exit, then linear to 0. */
	public float alpha(double t) {
		int e = exitStart();
		if (t <= e) return 1;
		if (exitMs <= 0) return 0;
		return (float) Math.max(0, 1 - (t - e) / exitMs);
	}

	/** Backdrop dim 0–1 (fades in over 200 ms, scaled by {@link #alpha}). */
	public float backdrop(double t) {
		float max = BACKDROP[style.ordinal()];
		if (max <= 0) return 0;
		double in = reduced ? 1 : Math.min(1, t / 200.0);
		return (float) (max * in) * alpha(t);
	}

	/** Rays rotation in degrees at {@code t} (20°/s; 0 under reduce motion). */
	public float raysDegrees(double t) {
		return reduced ? 0 : (float) (t * 0.02);
	}

	/**
	 * Count-up tick index at {@code t} (−1 = none yet). Ticks are evenly spaced over the roll-up at most
	 * {@link #MAX_TICKS_PER_S} per second, BIG+ styles only; the overlay plays one {@code chip_count} whenever the
	 * index grows.
	 */
	public int tickIndex(double t) {
		if (!style.isOverlay() || rollMs <= 0) return -1;
		if (skipAt >= 0 && t >= skipAt) return -1;
		int n = tickCount();
		if (n <= 0 || t < 0 || t >= rollMs) return -1;
		return (int) Math.min(n - 1, Math.floor(t / rollMs * n));
	}

	/** Number of count-up ticks over the whole roll-up. */
	public int tickCount() {
		if (!style.isOverlay() || rollMs <= 0) return 0;
		return Math.max(1, rollMs * MAX_TICKS_PER_S / 1000);
	}

	/** Pitch of tick {@code i}: 0.9 → 1.4 across the roll-up (global §2.6). */
	public float tickPitch(int i) {
		int n = tickCount();
		return n <= 1 ? 0.9f : (float) (0.9 + 0.5 * i / (n - 1));
	}

	/** Multiplier shown under a JACKPOT amount ({@code ×M}, one decimal floored, 0 when no stake). */
	public double multiple() {
		return stake <= 0 ? 0 : Math.floor(ret * 10.0 / stake) / 10.0;
	}
}
