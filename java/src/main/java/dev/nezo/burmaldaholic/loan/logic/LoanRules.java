package dev.nezo.burmaldaholic.loan.logic;

import dev.nezo.burmaldaholic.loan.logic.LoanRecord.Status;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Loan products, interest, deadlines, late fees, garnishment and the loan state machine
 * (GAME_DESIGN.md §5.2–§5.4, §5.6). PURE: no Minecraft imports; all time is absolute world ticks.
 *
 * <pre>
 * NONE ──take──▶ ACTIVE ──paid in full──▶ NONE (goodStanding+1)
 *                  │ tick ≥ deadline
 *                  ▼
 *              DEFAULT ──paid in full──▶ NONE (goodStanding=0, cooldown)
 *                  │ each MCD boundary after the deadline: owed += ceil(owedAtDeadline × lateFee),
 *                  │ capped at capMultiplier × dueAtIssue
 * </pre>
 */
public final class LoanRules {
	/** 1 Minecraft day in ticks. */
	public static final long MCD = 24_000;
	public static final int PLATINUM_TIER = 3;
	/** Lang ids of the products ({@code gui.burmaldaholic.loan.product.<id>}), by config index. */
	public static final String[] PRODUCT_IDS = {"pocket", "rent", "business", "serious", "life_changing"};

	private LoanRules() {}

	/** Config band for rates, late fees and collector scaling (§2.3). */
	public enum Band {
		EASY, NORMAL, HARD
	}

	/** Peaceful/Easy → EASY, Normal → NORMAL, Hard/Hardcore → HARD. {@code difficulty}: 0 peaceful … 3 hard. */
	public static Band band(int difficulty, boolean hardcore) {
		if (hardcore || difficulty >= 3) {
			return Band.HARD;
		}
		return difficulty == 2 ? Band.NORMAL : Band.EASY;
	}

	// ---- products ------------------------------------------------------------------------------

	public record Product(int index, String id, long principal, int days, int minTier) {}

	/** {@code loan.products} rows [principal, days, minTier] → products (rows after the 5th reuse the last name). */
	public static List<Product> products(int[][] rows) {
		List<Product> out = new ArrayList<>();
		if (rows == null) {
			return out;
		}
		for (int i = 0; i < rows.length; i++) {
			int[] r = rows[i];
			if (r == null || r.length < 3) {
				continue;
			}
			long principal = Math.max(1, Math.min(1_000_000_000L, r[0]));
			int days = Math.max(1, Math.min(100, r[1]));
			int tier = Math.max(0, Math.min(5, r[2]));
			out.add(new Product(i, PRODUCT_IDS[Math.min(i, PRODUCT_IDS.length - 1)], principal, days, tier));
		}
		return out;
	}

	/** Number of products the tier may NOT take. */
	public static int lockedCount(List<Product> products, int tier) {
		return (int) products.stream().filter(p -> tier < p.minTier()).count();
	}

	// ---- interest --------------------------------------------------------------------------------

	/**
	 * @param base                 base rate for the current difficulty band
	 * @param goodStandingDiscount discount per on-time loan (0.02)
	 * @param goodStandingMaxSteps max discounted loans (5)
	 * @param minRate              floor (0.10)
	 * @param platinumDiscount     extra discount at Platinum+ (tier ≥ 3)
	 */
	public record RateConfig(double base, double goodStandingDiscount, int goodStandingMaxSteps, double minRate, double platinumDiscount) {}

	/** rate = base − d × min(goodStanding, steps) − (tier ≥ Platinum ? pd : 0), floored at minRate. */
	public static double rate(RateConfig cfg, int goodStanding, int tier) {
		int steps = Math.min(Math.max(0, goodStanding), Math.max(0, cfg.goodStandingMaxSteps()));
		double r = cfg.base() - cfg.goodStandingDiscount() * steps - (tier >= PLATINUM_TIER ? cfg.platinumDiscount() : 0);
		return round6(Math.max(cfg.minRate(), r));
	}

	static double round6(double x) {
		return Math.round(x * 1e6) / 1e6;
	}

	/** ceil() that ignores floating-point noise (500 × 1.2 = 600.0000000000001 → 600). */
	public static long ceilSafe(double x) {
		return (long) Math.ceil(round6(x));
	}

	/** due = ceil(principal × (1 + rate)). */
	public static long due(long principal, double rate) {
		return ceilSafe(principal * (1 + rate));
	}

	/** Interest as a display percentage: 0.2 → "20", 0.175 → "17.5". Language neutral. */
	public static String percent(double rate) {
		double p = Math.round(rate * 1000) / 10.0;
		return p == Math.rint(p) ? Long.toString((long) p) : Double.toString(p);
	}

	// ---- taking ----------------------------------------------------------------------------------

	public enum TakeError {
		IN_DEFAULT, ONE_AT_A_TIME, COOLDOWN, VIP
	}

	/** Anti-abuse §5.8.1: one loan at a time, none in default or during the cooldown; VIP gate. Null = ok. */
	public static TakeError takeError(LoanRecord rec, Product product, int tier, long now) {
		if (rec.status == Status.DEFAULT) {
			return TakeError.IN_DEFAULT;
		}
		if (rec.status == Status.ACTIVE) {
			return TakeError.ONE_AT_A_TIME;
		}
		if (now < rec.cooldownUntil) {
			return TakeError.COOLDOWN;
		}
		if (tier < product.minTier()) {
			return TakeError.VIP;
		}
		return null;
	}

	/** Opens a loan (caller checked {@link #takeError}). deadline = issue + days × MCD. */
	public static void take(LoanRecord rec, Product product, double rate, long now) {
		rec.close();
		rec.status = Status.ACTIVE;
		rec.product = product.index();
		rec.principal = product.principal();
		rec.due = due(product.principal(), rate);
		rec.owed = rec.due;
		rec.rate = rate;
		rec.issueTick = now;
		rec.deadlineTick = now + product.days() * MCD;
	}

	// ---- paying ----------------------------------------------------------------------------------

	/**
	 * @param paid       chips actually applied (≤ owed)
	 * @param onTime     closed while ACTIVE → goodStanding + 1
	 * @param fromDefault closed from DEFAULT → goodStanding = 0 + cooldown
	 */
	public record PayResult(long paid, boolean closed, boolean onTime, boolean fromDefault) {
		static final PayResult NOTHING = new PayResult(0, false, false, false);
	}

	/** Applies a payment of up to {@code amount} chips (§5.3). Early repayment does not reduce interest. */
	public static PayResult pay(LoanRecord rec, long amount, long now, int cooldownDays) {
		long paid = Math.max(0, Math.min(amount, rec.owed));
		if (rec.status == Status.NONE || paid <= 0) {
			return PayResult.NOTHING;
		}
		rec.owed -= paid;
		if (rec.owed > 0) {
			return new PayResult(paid, false, false, false);
		}
		if (rec.status == Status.ACTIVE) {
			rec.goodStanding += 1;
			rec.close();
			return new PayResult(paid, true, true, false);
		}
		rec.goodStanding = 0;
		rec.cooldownUntil = now + Math.max(0, cooldownDays) * MCD;
		rec.close();
		return new PayResult(paid, true, false, true);
	}

	// ---- time ------------------------------------------------------------------------------------

	/**
	 * @param lateFee       late fee per overdue MCD for the current band
	 * @param capMultiplier owed ≤ due × capMultiplier
	 * @param warningTicks  warnings: ticks before the deadline, e.g. {24000, 2400}
	 */
	public record AdvanceConfig(double lateFee, double capMultiplier, int[] warningTicks) {}

	/**
	 * @param warning   threshold (ticks before the deadline) whose warning is due now, or −1
	 * @param defaulted the loan just went ACTIVE → DEFAULT
	 * @param lateFees  late fees charged now, in order
	 */
	public record AdvanceEvents(long warning, boolean defaulted, List<Long> lateFees) {}

	/** Whole MCD boundaries passed since the deadline. */
	public static long boundariesPassed(long deadline, long now) {
		return now < deadline ? 0 : (now - deadline) / MCD;
	}

	/** First MCD boundary strictly after {@code now} (deadline + k × MCD, k ≥ 1). */
	public static long nextBoundary(long deadline, long now) {
		return deadline + (boundariesPassed(deadline, now) + 1) * MCD;
	}

	/** Late fee for one boundary: ceil(owedAtDeadline × fee). */
	public static long lateFeeAmount(long owedAtDeadline, double fee) {
		return fee > 0 ? ceilSafe(owedAtDeadline * fee) : 0;
	}

	/**
	 * Brings a record up to {@code now}: deadline warning, ACTIVE → DEFAULT, and every late fee for
	 * the boundaries passed (also works as offline catch-up: several fees at once).
	 */
	public static AdvanceEvents advance(LoanRecord rec, long now, AdvanceConfig cfg) {
		long warning = -1;
		boolean defaulted = false;
		List<Long> fees = new ArrayList<>();
		if (rec.status == Status.NONE) {
			return new AdvanceEvents(warning, false, fees);
		}
		if (rec.status == Status.ACTIVE) {
			if (now >= rec.deadlineTick) {
				rec.status = Status.DEFAULT;
				rec.owedAtDeadline = rec.owed;
				rec.feeDays = 0;
				rec.wave = 0;
				rec.freezeMark = -1;
				rec.queued = false;
				rec.nextWaveTick = 0;
				defaulted = true;
			} else {
				long left = rec.deadlineTick - now;
				long term = rec.deadlineTick - rec.issueTick;
				TreeSet<Long> due = new TreeSet<>();
				for (int w : cfg.warningTicks()) {
					// Thresholds not shorter than the whole term would fire right after signing: skip them.
					if (w > 0 && w < term && left <= w && !rec.warned.contains((long) w)) {
						due.add((long) w);
					}
				}
				if (!due.isEmpty()) {
					rec.warned.addAll(due);
					warning = due.first(); // only the most urgent one is worth telling
				}
			}
		}
		if (rec.status == Status.DEFAULT) {
			long n = boundariesPassed(rec.deadlineTick, now);
			long cap = (long) Math.floor(rec.due * cfg.capMultiplier());
			for (long k = rec.feeDays + 1; k <= n; k++) {
				long fee = Math.min(lateFeeAmount(rec.owedAtDeadline, cfg.lateFee()), Math.max(0, cap - rec.owed));
				if (fee > 0) {
					rec.owed += fee;
					fees.add(fee);
				}
			}
			rec.feeDays = (int) Math.max(rec.feeDays, Math.min(Integer.MAX_VALUE, n));
		}
		return new AdvanceEvents(warning, defaulted, fees);
	}

	/**
	 * Asset Freeze schedule (§5.6): one seizure at DEFAULT and one at every later MCD boundary.
	 * Returns how many seizures are due now. If the freeze starts mid-default (difficulty switched to
	 * Peaceful), the first seizure is at the next boundary.
	 */
	public static int freezeSteps(LoanRecord rec, long now, boolean justDefaulted) {
		if (rec.status != Status.DEFAULT) {
			return 0;
		}
		int n = (int) Math.min(Integer.MAX_VALUE - 1, boundariesPassed(rec.deadlineTick, now));
		if (rec.freezeMark < 0) {
			rec.freezeMark = n;
			return justDefaulted ? n + 1 : 0;
		}
		if (n <= rec.freezeMark) {
			return 0;
		}
		int steps = n - rec.freezeMark;
		rec.freezeMark = n;
		return steps;
	}

	// ---- garnishment, seizure, admin ------------------------------------------------------------

	/** Share of a chip credit that goes to the debt while in default: floor(credit × pct/100), ≤ owed. */
	public static long garnishAmount(long credit, int percent, long owed) {
		if (credit <= 0 || owed <= 0 || percent <= 0) {
			return 0;
		}
		int p = Math.min(100, percent);
		return Math.min(owed, p >= 100 ? credit : Math.floorDiv(credit * p, 100));
	}

	/** Asset freeze / repossession: min(owed, floor(balance × pct/100)). */
	public static long seizeAmount(long balance, int percent, long owed) {
		if (balance <= 0 || owed <= 0 || percent <= 0) {
			return 0;
		}
		return Math.min(owed, Math.floorDiv(balance * Math.min(100, percent), 100));
	}

	/**
	 * Operator {@code set <n>}: 0 clears the debt (no standing change); on a player without a loan a
	 * positive amount opens an ACTIVE admin debt due in {@code days} MCD.
	 */
	public static void adminSetDebt(LoanRecord rec, long owed, long now, int days) {
		long n = Math.max(0, owed);
		if (n == 0) {
			rec.close();
			return;
		}
		if (rec.status == Status.NONE) {
			rec.close();
			rec.status = Status.ACTIVE;
			rec.product = -1;
			rec.principal = n;
			rec.due = n;
			rec.owed = n;
			rec.issueTick = now;
			rec.deadlineTick = now + days * MCD;
			return;
		}
		rec.owed = n;
		if (rec.status == Status.ACTIVE) {
			rec.due = Math.max(rec.due, n);
		}
	}

	/** Operator helper: moves the deadline to now so the loan defaults on the next tick. */
	public static void adminForceDefault(LoanRecord rec, long now) {
		if (rec.status == Status.ACTIVE) {
			rec.deadlineTick = now;
			rec.issueTick = Math.min(rec.issueTick, now);
		}
	}

	/**
	 * Casino mode (or loans) was off for {@code gap} ticks: nothing happens while dormant (§2.1), so
	 * every pending timer moves forward by the dormant time instead of firing at once on re-enable.
	 */
	public static void shiftTimers(LoanRecord rec, long gap) {
		if (gap <= 0) {
			return;
		}
		if (rec.status != Status.NONE) {
			rec.issueTick += gap;
			rec.deadlineTick += gap;
		}
		if (rec.nextWaveTick > 0) {
			rec.nextWaveTick += gap;
		}
		if (rec.cooldownUntil > 0) {
			rec.cooldownUntil += gap;
		}
	}

	/** Whole MCD count for "no loans for 3 days" (at least 1 while any time is left). */
	public static long dayCount(long ticks) {
		return Math.max(1, (ticks + MCD - 1) / MCD);
	}
}
