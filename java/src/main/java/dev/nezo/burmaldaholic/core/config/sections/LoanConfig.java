package dev.nezo.burmaldaholic.core.config.sections;

import dev.nezo.burmaldaholic.core.config.Range;
import dev.nezo.burmaldaholic.core.config.Size;
import dev.nezo.burmaldaholic.core.config.Validatable;

/** Config section `loan` — keys, defaults and ranges from docs/design/CONFIG.md. */
public final class LoanConfig implements Validatable {
	public boolean enabled = true;
	/** Loan products as [principal, days, minTier] (principal 1–10⁹, days 1–100, tier 0–5). */
	@Size(min = 1, max = 32) public int[][] products = {{100, 3, 0}, {500, 3, 0}, {2000, 5, 1}, {10000, 7, 2}, {50000, 7, 4}};
	public static final class Rate {
		/** Also Peaceful. */
		@Range(min = 0, max = 5) public double easy = 0.15;
		@Range(min = 0, max = 5) public double normal = 0.2;
		/** Also Hardcore. */
		@Range(min = 0, max = 5) public double hard = 0.25;
	}
	public Rate rate = new Rate();

	/** Per on-time loan. */
	@Range(min = 0, max = 0.5) public double goodStandingDiscount = 0.02;
	@Range(min = 0, max = 50) public int goodStandingMaxSteps = 5;
	@Range(min = 0, max = 5) public double minRate = 0.1;
	/** Extra VIP discount (Platinum+). */
	@Range(min = 0, max = 0.5) public double platinumDiscount = 0.02;
	public static final class LateFee {
		/** Per overdue MCD, of owed-at-deadline. */
		@Range(min = 0, max = 1) public double easy = 0.05;
		@Range(min = 0, max = 1) public double normal = 0.1;
		@Range(min = 0, max = 1) public double hard = 0.15;
	}
	public LateFee lateFee = new LateFee();

	/** Owed ≤ due × this. */
	@Range(min = 1, max = 10) public double lateFeeCapMultiplier = 2.0;
	/** In default. */
	@Range(min = 0, max = 100) public int garnishPercent = 50;
	@Range(min = 0, max = 100) public int defaultCooldownDays = 5;
	/** Warnings before deadline. */
	@Range(min = 0, max = 240000) @Size(min = 0, max = 16) public int[] warningTicks = {24000, 2400};
	public static final class Collectors {
		/** False → Asset Freeze on all difficulties. */
		public boolean enabled = true;
	}
	public Collectors collectors = new Collectors();

	@Range(min = 0, max = 24000) public int firstWaveDelayTicks = 600;
	/** After join. */
	@Range(min = 0, max = 24000) public int offlineWaveDelayTicks = 1200;
	@Range(min = 1, max = 20) public int squadMax = 10;
	/** Extra Collectors from later waves. */
	@Range(min = 0, max = 10) public int escalationMax = 3;
	@Range(min = 100, max = 6000) public int approachTicks = 600;
	@Range(min = 100, max = 2400) public int negotiateTicks = 200;
	@Range(min = 600, max = 48000) public int hostileTicks = 6000;
	/** Share of owed to send a wave away. */
	@Range(min = 0, max = 1) public double partialPaymentMin = 0.5;
	@Range(min = 0, max = 100) public int repossessBalancePercent = 50;
	/** Take the most valuable appraised item on death. */
	public boolean repossessItem = true;
	public static final class CollectorHealthMultiplier {
		@Range(min = 0.1, max = 10) public double easy = 0.75;
		@Range(min = 0.1, max = 10) public double hard = 1.25;
	}
	public CollectorHealthMultiplier collectorHealthMultiplier = new CollectorHealthMultiplier();

	/** Asset Freeze seizure per MCD. */
	@Range(min = 0, max = 100) public int peacefulSeizePercent = 50;

	@Override
	public void validate(Issues issues) {
		for (int i = 0; i < products.length; i++) {
			int[] p = products[i];
			if (p.length != 3) {
				int[][] def = new LoanConfig().products;
				issues.invalid("products", java.util.Arrays.deepToString(products), java.util.Arrays.deepToString(def));
				products = def;
				return;
			}
			p[0] = clamp(issues, "products[" + i + "][0]", p[0], 1, 1_000_000_000);
			p[1] = clamp(issues, "products[" + i + "][1]", p[1], 1, 100);
			p[2] = clamp(issues, "products[" + i + "][2]", p[2], 0, 5);
		}
	}

	private static int clamp(Issues issues, String key, int value, int min, int max) {
		int c = Math.max(min, Math.min(max, value));
		if (c != value) {
			issues.clamped(key, value, c);
		}
		return c;
	}
}
