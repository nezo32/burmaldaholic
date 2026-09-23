package dev.nezo.burmaldaholic.games.slots;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.sections.SlotsConfig;
import dev.nezo.burmaldaholic.games.slots.logic.LineBets;
import dev.nezo.burmaldaholic.games.slots.logic.SlotRtp;
import dev.nezo.burmaldaholic.games.slots.logic.SlotTable;
import dev.nezo.burmaldaholic.games.slots.logic.Tier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Machine math built from the live config ({@code slots.*}), cached until the config changes
 * ({@link #invalidate()} is hooked to {@code ConfigManager} listeners in {@link SlotsModule}).
 */
public final class SlotsMath {
	/** A machine's table and its total RTP (incl. jackpot contribution) for the §14 streak cap. */
	public record Machine(SlotTable table, double rtp) {}

	private static final Map<String, Machine> CACHE = new ConcurrentHashMap<>();

	private SlotsMath() {}

	public static void invalidate() {
		CACHE.clear();
	}

	public static Machine machine(Tier tier, boolean owned) {
		return CACHE.computeIfAbsent(tier.id() + "|" + owned, k -> build(tier, owned));
	}

	private static Machine build(Tier tier, boolean owned) {
		SlotsConfig c = CasinoConfig.slots();
		Map<String, Integer> weights;
		Map<String, Double> pays;
		double[] berry;
		switch (tier) {
			case COPPER -> {
				weights = c.copper.weights;
				pays = c.copper.pays;
				berry = c.copper.berryPartial;
			}
			case GOLD -> {
				weights = c.gold.weights;
				pays = c.gold.pays;
				berry = c.gold.berryPartial;
			}
			default -> {
				weights = c.netherite.weights;
				pays = c.netherite.pays;
				berry = c.netherite.berryPartial;
			}
		}
		Map<String, Double> p = new HashMap<>(pays == null ? Map.of() : pays);
		boolean progressive = tier.progressive() && !owned;
		if (owned && tier.progressive()) {
			p.put("star", c.ownedStarPays);
		}
		SlotTable table = SlotTable.of(weights, p, berry, tier.lines(), progressive);
		return new Machine(table, SlotRtp.totalRtp(table, progressive ? contribution(tier) : 0));
	}

	public static LineBets lineBets(Tier tier) {
		SlotsConfig c = CasinoConfig.slots();
		return switch (tier) {
			case COPPER -> new LineBets(1, c.copper.maxLineBet);
			case GOLD -> new LineBets(1, c.gold.maxLineBet);
			case NETHERITE -> new LineBets(c.netherite.minLineBet, c.netherite.maxLineBet);
		};
	}

	public static double contribution(Tier tier) {
		SlotsConfig.Jackpot j = CasinoConfig.slots().jackpot;
		return switch (tier) {
			case GOLD -> j.contribution.gold;
			case NETHERITE -> j.contribution.netherite;
			default -> 0;
		};
	}

	public static long seed(Tier tier) {
		SlotsConfig.Jackpot j = CasinoConfig.slots().jackpot;
		return switch (tier) {
			case GOLD -> j.seed.gold;
			case NETHERITE -> j.seed.netherite;
			default -> 0;
		};
	}

	/** Required VIP tier to play (Netherite: {@code slots.netherite.minVipTier}). */
	public static int minVipTier(Tier tier) {
		return tier == Tier.NETHERITE ? CasinoConfig.slots().netherite.minVipTier : 0;
	}

	/** A machine whose RTP is above 99 %. */
	public record RtpWarning(Tier tier, boolean owned, double rtp) {
		public String percent() {
			return String.format(Locale.ROOT, "%.2f %%", rtp * 100);
		}
	}

	/**
	 * {@code slots.validateRtp}: machines whose RTP (incl. contribution) exceeds 99 % or that have no symbols.
	 * Logged loudly; never auto-fixed.
	 */
	public static List<RtpWarning> rtpWarnings() {
		List<RtpWarning> out = new ArrayList<>();
		for (Tier tier : Tier.values()) {
			for (boolean owned : tier.progressive() ? new boolean[] {false, true} : new boolean[] {false}) {
				Machine m = machine(tier, owned);
				if (m.table().empty() || m.rtp() > 0.99) {
					String pct = String.format(Locale.ROOT, "%.2f %%", m.rtp() * 100);
					out.add(new RtpWarning(tier, owned, m.rtp()));
					Burmaldaholic.LOGGER.warn("!!! slots {}{}: RTP {} exceeds 99 % (or no symbols) — check slots.{}.weights/pays in the config",
						tier.id(), owned ? " (owned)" : "", pct, tier.id());
				}
			}
		}
		return out;
	}
}
