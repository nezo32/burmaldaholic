package dev.nezo.burmaldaholic.core.config.sections;

import dev.nezo.burmaldaholic.core.config.Range;
import dev.nezo.burmaldaholic.core.config.Validatable;

/** Config section `vip` — keys, defaults and ranges from docs/design/CONFIG.md. */
public final class VipConfig implements Validatable {
	public static final class Threshold {
		/** Lifetime wagered. Must be ascending across tiers (validated). */
		@Range(min = 1, max = 1000000000000.0) public long silver = 5000L;
		@Range(min = 1, max = 1000000000000.0) public long gold = 25000L;
		@Range(min = 1, max = 1000000000000.0) public long platinum = 100000L;
		@Range(min = 1, max = 1000000000000.0) public long diamond = 500000L;
		@Range(min = 1, max = 1000000000000.0) public long netherite = 2500000L;
	}
	public Threshold threshold = new Threshold();

	public static final class MaxBet {
		/** Tier max bet. */
		@Range(min = 1, max = 1000000000.0) public int bronze = 100;
		@Range(min = 1, max = 1000000000.0) public int silver = 250;
		@Range(min = 1, max = 1000000000.0) public int gold = 1000;
		@Range(min = 1, max = 1000000000.0) public int platinum = 2500;
		@Range(min = 1, max = 1000000000.0) public int diamond = 10000;
		@Range(min = 1, max = 1000000000.0) public int netherite = 50000;
	}
	public MaxBet maxBet = new MaxBet();

	public static final class Cashback {
		/** Fraction of daily net loss. */
		@Range(min = 0, max = 0.5) public double gold = 0.02;
		@Range(min = 0, max = 0.5) public double platinum = 0.03;
		@Range(min = 0, max = 0.5) public double diamond = 0.04;
		@Range(min = 0, max = 0.5) public double netherite = 0.05;
	}
	public Cashback cashback = new Cashback();

	public static final class ContractBonus {
		@Range(min = 0, max = 5) public double silver = 0.05;
		/** Applies to Gold and above. */
		@Range(min = 0, max = 5) public double gold = 0.1;
	}
	public ContractBonus contractBonus = new ContractBonus();

	public boolean announceNetherite = true;

	@Override
	public void validate(Issues issues) {
		long[] t = {threshold.silver, threshold.gold, threshold.platinum, threshold.diamond, threshold.netherite};
		String[] names = {"silver", "gold", "platinum", "diamond", "netherite"};
		for (int i = 1; i < t.length; i++) {
			if (t[i] <= t[i - 1]) {
				issues.clamped("threshold." + names[i], t[i], t[i - 1] + 1);
				t[i] = t[i - 1] + 1;
			}
		}
		threshold.gold = t[1];
		threshold.platinum = t[2];
		threshold.diamond = t[3];
		threshold.netherite = t[4];
	}
}
