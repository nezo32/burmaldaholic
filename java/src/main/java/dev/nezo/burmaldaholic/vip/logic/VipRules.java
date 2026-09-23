package dev.nezo.burmaldaholic.vip.logic;

import java.util.ArrayList;
import java.util.List;

/**
 * VIP tier math (GAME_DESIGN.md §12). Pure: no Minecraft classes.
 * Tier by lifetime chips wagered W (never decreases; tiers are never lost).
 */
public final class VipRules {
	public static final int BRONZE = 0, SILVER = 1, GOLD = 2, PLATINUM = 3, DIAMOND = 4, NETHERITE = 5;
	public static final int MAX_TIER = NETHERITE;
	/** Default Silver..Netherite thresholds. */
	public static final long[] DEFAULT_THRESHOLDS = {5_000, 25_000, 100_000, 500_000, 2_500_000};

	private VipRules() {}

	/**
	 * Strictly ascending Silver..Netherite thresholds (CONFIG.md: "must be ascending"). Missing or
	 * non-positive values fall back to the defaults; a value not above the previous one becomes previous + 1.
	 */
	public static long[] sanitize(long[] raw) {
		long[] out = new long[MAX_TIER];
		long prev = 0;
		for (int i = 0; i < MAX_TIER; i++) {
			long v = raw != null && i < raw.length ? raw[i] : DEFAULT_THRESHOLDS[i];
			if (v < 1) {
				v = DEFAULT_THRESHOLDS[i];
			}
			out[i] = v > prev ? v : prev + 1;
			prev = out[i];
		}
		return out;
	}

	/** Tier earned by lifetime wagered {@code wagered}. */
	public static int tierFor(long wagered, long[] thresholds) {
		long[] th = sanitize(thresholds);
		int tier = BRONZE;
		for (int i = 0; i < th.length; i++) {
			if (wagered >= th[i]) {
				tier = i + 1;
			}
		}
		return tier;
	}

	/** W needed for {@code tier} (0 for Bronze). */
	public static long thresholdOf(int tier, long[] thresholds) {
		if (tier <= BRONZE) {
			return 0;
		}
		return sanitize(thresholds)[Math.min(tier, MAX_TIER) - 1];
	}

	/**
	 * Progress towards the next tier.
	 * @param next     next tier index, -1 at Netherite
	 * @param from     threshold of the current tier
	 * @param target   threshold of the next tier (0 at Netherite)
	 * @param fraction 0..1
	 */
	public record Progress(int tier, int next, long from, long target, double fraction) {
		public boolean maxed() {
			return next < 0;
		}
	}

	/** {@code stored} = tier already granted (never lost even if thresholds are raised). */
	public static Progress progress(long wagered, long[] thresholds, int stored) {
		int tier = Math.max(clamp(stored), tierFor(wagered, thresholds));
		if (tier >= MAX_TIER) {
			return new Progress(tier, -1, thresholdOf(tier, thresholds), 0, 1.0);
		}
		long from = thresholdOf(tier, thresholds);
		long target = thresholdOf(tier + 1, thresholds);
		double fraction = Math.max(0, Math.min(1, (double) (wagered - from) / Math.max(1, target - from)));
		return new Progress(tier, tier + 1, from, target, fraction);
	}

	/** Tiers newly reached going from {@code prev} to {@code next} (one announcement each). */
	public static List<Integer> promotions(int prev, int next) {
		List<Integer> out = new ArrayList<>();
		for (int t = Math.max(0, prev) + 1; t <= Math.min(MAX_TIER, next); t++) {
			out.add(t);
		}
		return out;
	}

	public static int clamp(int tier) {
		return Math.max(BRONZE, Math.min(MAX_TIER, tier));
	}

	// ---- perks ------------------------------------------------------------------------------

	/** Cashback rate for a tier; {@code rates} = [gold, platinum, diamond, netherite]. Below Gold: 0. */
	public static double cashbackRate(int tier, double[] rates) {
		if (tier < GOLD || rates == null) {
			return 0;
		}
		int i = Math.min(tier, MAX_TIER) - GOLD;
		double r = i < rates.length ? rates[i] : 0;
		return Double.isFinite(r) ? Math.max(0, Math.min(0.5, r)) : 0;
	}

	/** Contract reward bonus (fraction): Silver {@code silver}, Gold and above {@code gold}. */
	public static double contractBonus(int tier, double silver, double gold) {
		if (tier >= GOLD) {
			return gold;
		}
		return tier >= SILVER ? silver : 0;
	}

	/** Contract slots: base, Platinum base + 1, Diamond+ base + 2 (§3.4.4: 3 / 4 / 5). */
	public static int contractSlots(int tier, int base) {
		return base + (tier >= DIAMOND ? 2 : tier >= PLATINUM ? 1 : 0);
	}

	/** Largest loan principal available at {@code tier}; {@code products} = [principal, days, minTier]. */
	public static long maxLoanFor(int tier, int[][] products) {
		long best = 0;
		if (products == null) {
			return 0;
		}
		for (int[] p : products) {
			if (p == null || p.length == 0) {
				continue;
			}
			int minTier = p.length > 2 ? p[2] : 0;
			if (minTier <= tier && p[0] > best) {
				best = p[0];
			}
		}
		return best;
	}

	/** Loan principal first unlocked at exactly {@code tier}, or 0. */
	public static long loanUnlockedAt(int tier, int[][] products) {
		long at = maxLoanFor(tier, products);
		return at > 0 && (tier == BRONZE || at > maxLoanFor(tier - 1, products)) ? at : 0;
	}

	/** How a perk's single argument is rendered. */
	public enum ArgKind {
		NONE, CHIPS, PERCENT, COUNT
	}

	/** A perk line: translation key + one optional argument ({@code value}; percent in tenths of a percent). */
	public record Perk(String key, ArgKind kind, long value) {}

	public record PerkParams(int[][] loanProducts, double contractBonusSilver, double contractBonusGold, int baseSlots, double[] cashback) {}

	private static final String P = "gui.burmaldaholic.vip.perk.";
	private static final String[] COSMETIC = {"", "cosmetic_name", "cosmetic_particles", "cosmetic_title", "cosmetic_card", "cosmetic_aura"};

	/** Percentage in tenths ({@code 0.05 -> 50} = 5.0 %). */
	public static long tenthsOfPercent(double fraction) {
		return Math.round(fraction * 1000);
	}

	/** Perks newly granted at {@code tier} (§12 table), values from config. */
	public static List<Perk> perksAt(int tier, PerkParams c) {
		List<Perk> out = new ArrayList<>();
		switch (tier) {
			case BRONZE -> out.add(new Perk(P + "poker_micro", ArgKind.NONE, 0));
			case SILVER -> {
				out.add(new Perk(P + "poker_low", ArgKind.NONE, 0));
				out.add(new Perk(P + "scratch_gold", ArgKind.NONE, 0));
			}
			case GOLD -> {
				out.add(new Perk(P + "netherite_slots", ArgKind.NONE, 0));
				out.add(new Perk(P + "high_roller", ArgKind.NONE, 0));
				out.add(new Perk(P + "poker_mid", ArgKind.NONE, 0));
				out.add(new Perk(P + "emerald_rate", ArgKind.NONE, 0));
			}
			case PLATINUM -> out.add(new Perk(P + "loan_discount", ArgKind.NONE, 0));
			case DIAMOND -> out.add(new Perk(P + "poker_high", ArgKind.NONE, 0));
			default -> {
			}
		}
		long loan = loanUnlockedAt(tier, c.loanProducts());
		if (loan > 0) {
			out.add(new Perk(P + "loan", ArgKind.CHIPS, loan));
		}
		double bonus = contractBonus(tier, c.contractBonusSilver(), c.contractBonusGold());
		if (bonus > 0 && bonus != contractBonus(tier - 1, c.contractBonusSilver(), c.contractBonusGold())) {
			out.add(new Perk(P + "contract_bonus", ArgKind.PERCENT, tenthsOfPercent(bonus)));
		}
		int slots = contractSlots(tier, c.baseSlots());
		if (tier > BRONZE && slots != contractSlots(tier - 1, c.baseSlots())) {
			out.add(new Perk(P + "contract_slots", ArgKind.COUNT, slots));
		}
		double cb = cashbackRate(tier, c.cashback());
		if (cb > 0) {
			out.add(new Perk(P + "cashback", ArgKind.PERCENT, tenthsOfPercent(cb)));
		}
		if (tier >= SILVER && tier <= MAX_TIER) {
			out.add(new Perk(P + COSMETIC[tier], ArgKind.NONE, 0));
		}
		return out;
	}

	/** Advancement id for reaching {@code tier} (§19 {@code vip_*}), null for Bronze. */
	public static String advancementId(int tier) {
		return switch (tier) {
			case SILVER -> "vip_silver";
			case GOLD -> "vip_gold";
			case PLATINUM -> "vip_platinum";
			case DIAMOND -> "vip_diamond";
			case NETHERITE -> "vip_netherite";
			default -> null;
		};
	}
}
