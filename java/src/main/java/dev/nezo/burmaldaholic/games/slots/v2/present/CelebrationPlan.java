package dev.nezo.burmaldaholic.games.slots.v2.present;

import dev.nezo.burmaldaholic.core.anim.Ease;
import dev.nezo.burmaldaholic.core.anim.RollUp;
import dev.nezo.burmaldaholic.core.anim.WinTier;
import dev.nezo.burmaldaholic.core.anim.WinTierTable;

/**
 * The slot big-win sequence and jackpot celebrations (slots.md §4.11–§4.13, SLOTS.md §10.1; PURE).
 *
 * <p>Big / Mega / Epic: the overlay starts at the NICE word and UPGRADES at the moments the rolling value passes
 * 15×, 40× and 100× the bet (the upgrade times are the exact inverse of the roll-up curve), with a punch, a ray
 * colour change, a coin-rate step and the tier stem; the final word always equals the SERVER tier. The value is
 * monotonic ({@code floor(win × outCubic(t))}) and the last frame prints the exact amount (F5).
 */
public final class CelebrationPlan {
	/** Hold at the final value before the skip hint (ms). */
	public static final int HOLD_MS = 800;
	public static final int PUNCH_MS = 150;
	/** Ray colours by word: Nice gold, Big gold, Mega orange, Epic magenta. */
	public static final int[] RAY_COLORS = {0xFFFFD640, 0xFFFFD640, 0xFFFF8A1A, 0xFFFF40C0};
	/** GUI coins per second by word (Nice, Big, Mega, Epic). */
	public static final int[] COIN_RATE = {20, 20, 40, 60};

	private CelebrationPlan() {}

	/** Slot roll-up duration (ms, normal speed). */
	public static int rollUpMs(long winChips, long bet) {
		return RollUp.durationMs(winChips, bet, 600, 8000);
	}

	/**
	 * Word shown at progress {@code u} of the roll-up for a spin of final tier {@code finalTier}: NICE until the value
	 * passes the next threshold, … ; at {@code u ≥ 1} exactly {@code finalTier}. Below BIG the word is the final tier
	 * all along.
	 */
	public static WinTier wordAt(double u, long winChips, long bet, WinTierTable table, WinTier finalTier) {
		if (u >= 1 || !finalTier.isOverlay()) return finalTier;
		long shown = RollUp.valueAt(winChips, u);
		WinTier word = table.multiple(WinTier.NICE) > 0 ? WinTier.NICE : WinTier.BIG;
		for (long[] p : table.upgradePoints(bet, WinTier.WIN, finalTier)) if (shown >= p[1]) word = WinTier.values()[(int) p[0]];
		return word.ordinal() > finalTier.ordinal() ? finalTier : word;
	}

	/**
	 * Progress (0..1) of the roll-up at which the value first reaches {@code amount}: the inverse of
	 * {@code floor(win × outCubic(u))} ≥ amount. 1 when the amount is the total or more.
	 */
	public static double progressAt(long amount, long winChips) {
		if (amount <= 0) return 0;
		if (amount >= winChips) return 1;
		double f = (double) amount / winChips;
		double u = 1 - Math.cbrt(1 - f);
		// the floor may need a hair more
		for (int i = 0; i < 8 && RollUp.valueAt(winChips, u) < amount; i++) u = Math.min(1, u + 1e-6);
		return u;
	}

	/** Times (progress 0..1) of the word upgrades, in order; pairs {@code [tierOrdinal, progress×1e6]}. */
	public static long[][] upgrades(long winChips, long bet, WinTierTable table, WinTier finalTier) {
		long[][] pts = table.upgradePoints(bet, WinTier.NICE, finalTier);
		long[][] out = new long[pts.length][];
		for (int i = 0; i < pts.length; i++) out[i] = new long[] {pts[i][0], Math.round(progressAt(pts[i][1], winChips) * 1e6)};
		return out;
	}

	/** Punch scale of the word {@code ms} after an upgrade (1.25 → 1 over 150 ms). */
	public static double punch(double msSinceUpgrade) {
		if (msSinceUpgrade < 0 || msSinceUpgrade >= PUNCH_MS) return 1;
		return 1.25 - 0.25 * Ease.OUT_CUBIC.apply(msSinceUpgrade / PUNCH_MS);
	}

	/** Index into {@link #RAY_COLORS} / {@link #COIN_RATE} for a word. */
	public static int wordIndex(WinTier word) {
		return switch (word) {
			case BIG -> 1;
			case MEGA -> 2;
			case EPIC, JACKPOT -> 3;
			default -> 0;
		};
	}

	/** Epic / Max Win shake (px) {@code ms} after the upgrade: 4 px decaying over 400 ms; reduce motion 0. */
	public static double shake(double ms, boolean reduceMotion, int seed) {
		if (reduceMotion || ms < 0 || ms >= 400) return 0;
		double decay = 1 - ms / 400.0;
		return 4 * decay * Math.sin(ms * 0.9 + seed);
	}

	/** Screen flash alpha (one flash ≤ 30 %, 300 ms) {@code ms} after its cue; 0 with flashes off. */
	public static double flash(double ms, boolean flashes, double peak) {
		if (!flashes || ms < 0 || ms >= 300) return 0;
		return Math.min(0.30, peak) * (1 - ms / 300.0);
	}

	/** Roll-up tick sound gate: ≤ 15 ticks per second. */
	public static final int TICK_GAP_MS = 67;

	// ---- jackpots (slots.md §4.11) ----------------------------------------------------------------------------

	/**
	 * Jackpot sub-tier parameters.
	 *
	 * @param lengthMs      whole celebration (normal speed)
	 * @param skippableMs   skippable after
	 * @param rollMs        amount roll-up
	 * @param veil          backdrop veil alpha
	 * @param rays          0 none, 1 rays, 2 double rays
	 * @param wordScale     word scale
	 * @param elastic       word enters with outElastic (else outBack)
	 * @param coins         GUI coins
	 * @param flashPeak     flash alpha (0 none)
	 * @param shakePx       shake (0 none)
	 * @param pitch         {@code jackpot} sound pitch
	 */
	public record Jackpot(int lengthMs, int skippableMs, int rollMs, float veil, int rays, int wordScale, boolean elastic, int coins,
			float flashPeak, int shakePx, float pitch) {}

	private static final Jackpot[] JACKPOTS = {
		new Jackpot(2000, 500, 1000, 0.30f, 0, 2, false, 30, 0f, 0, 1.3f),
		new Jackpot(2500, 500, 1400, 0.40f, 1, 3, false, 45, 0f, 0, 1.15f),
		new Jackpot(3000, 800, 2000, 0.50f, 2, 3, true, 80, 0.25f, 0, 1.0f),
		new Jackpot(4000, 1000, 3000, 0.55f, 2, 3, true, 96, 0.30f, 4, 1.0f)};

	/** Parameters of sub-tier 1 Mini … 4 Grand. */
	public static Jackpot jackpot(int tier) {
		return JACKPOTS[Math.max(1, Math.min(4, tier)) - 1];
	}

	/** Grand word letter wave: y offset of char i at {@code ms} ({@code 2·sin(t·8 + i)}, t in s). */
	public static double letterWave(double ms, int i) {
		return 2 * Math.sin(ms / 1000.0 * 8 + i);
	}

	/** Word entrance scale (0 → 1) {@code ms} after the jackpot start: outBack (Mini, Minor) or outElastic. */
	public static double wordIn(double ms, boolean elastic) {
		if (ms <= 0) return 0;
		double u = Math.min(1, ms / 500.0);
		return elastic ? Ease.OUT_ELASTIC.apply(u) : Ease.OUT_BACK.apply(u);
	}

	/** Max Win plate slam: scale 2 → 1 over 200 ms (inCubic). */
	public static double plateSlam(double ms) {
		if (ms <= 0) return 2;
		if (ms >= 200) return 1;
		return 2 - Ease.IN_CUBIC.apply(ms / 200.0);
	}
}
