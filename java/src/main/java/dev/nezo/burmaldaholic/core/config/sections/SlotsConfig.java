package dev.nezo.burmaldaholic.core.config.sections;

import dev.nezo.burmaldaholic.core.config.Member;
import dev.nezo.burmaldaholic.core.config.Maps;
import dev.nezo.burmaldaholic.core.config.Range;
import dev.nezo.burmaldaholic.core.config.Size;
import java.util.Map;

/** Config section `slots` — keys, defaults and ranges from docs/design/CONFIG.md. */
public final class SlotsConfig {
	public boolean enabled = true;
	public static final class Copper {
		/** Also capped by tier max. */
		@Range(min = 1, max = 1000000) public int maxLineBet = 50;
		/** Symbol weights (§8). */
		@Range(min = 0, max = 10000) public Map<String, Integer> weights = Maps.of("berries", 24, "apple", 20, "golden_carrot", 16, "emerald", 12, "diamond", 8, "seven", 5, "creeper", 15);
		/** 3-of-a-kind multipliers (§8). */
		@Range(min = 0, max = 100000) public Map<String, Double> pays = Maps.of("berries", 10.0, "apple", 10.0, "golden_carrot", 20.0, "emerald", 30.0, "diamond", 60.0, "seven", 150.0, "creeper", 0.0);
		/** Pays for 1 and 2 leading berries. */
		@Range(min = 0, max = 100) @Size(min = 2, max = 2) public double[] berryPartial = {2, 3};
	}
	@Member("block.burmaldaholic.slot_machine_copper")
	public Copper copper = new Copper();

	public static final class Gold {
		/** Also ≤ tier max / 3. */
		@Range(min = 1, max = 1000000) public int maxLineBet = 100;
		/** Symbol weights (§8). */
		@Range(min = 0, max = 10000) public Map<String, Integer> weights = Maps.of("berries", 22, "apple", 19, "golden_carrot", 16, "emerald", 12, "diamond", 8, "seven", 5, "wild", 3, "creeper", 8, "pearl", 5, "star", 2);
		/** 3-of-a-kind multipliers (§8). */
		@Range(min = 0, max = 100000) public Map<String, Double> pays = Maps.of("berries", 8.0, "apple", 7.0, "golden_carrot", 11.0, "emerald", 25.0, "diamond", 50.0, "seven", 100.0, "wild", 200.0, "creeper", 0.0, "pearl", 10.0, "star", 0.0);
		/** Pays for 1 and 2 leading berries. */
		@Range(min = 0, max = 100) @Size(min = 2, max = 2) public double[] berryPartial = {2, 3};
	}
	@Member("block.burmaldaholic.slot_machine_gold")
	public Gold gold = new Gold();

	public static final class Netherite {
		@Range(min = 1, max = 1000000) public int minLineBet = 2;
		/** Also ≤ tier max / 5. */
		@Range(min = 1, max = 1000000) public int maxLineBet = 500;
		/** 2 = Gold. */
		@Range(min = 0, max = 5) public int minVipTier = 2;
		/** Symbol weights (§8). */
		@Range(min = 0, max = 10000) public Map<String, Integer> weights = Maps.of("berries", 20, "apple", 19, "golden_carrot", 16, "emerald", 12, "diamond", 9, "seven", 6, "wild", 3, "tnt", 6, "pearl", 5, "clock", 2, "star", 2);
		/** 3-of-a-kind multipliers (§8). */
		@Range(min = 0, max = 100000) public Map<String, Double> pays = Maps.of("berries", 8.0, "apple", 9.0, "golden_carrot", 14.0, "emerald", 25.0, "diamond", 50.0, "seven", 100.0, "wild", 250.0, "tnt", 0.0, "pearl", 10.0, "clock", 50.0, "star", 0.0);
		/** Pays for 1 and 2 leading berries. */
		@Range(min = 0, max = 100) @Size(min = 2, max = 2) public double[] berryPartial = {2, 3};
	}
	@Member("block.burmaldaholic.slot_machine_netherite")
	public Netherite netherite = new Netherite();

	public static final class Jackpot {
		public static final class Contribution {
			@Range(min = 0, max = 0.1) public double gold = 0.01;
			@Range(min = 0, max = 0.1) public double netherite = 0.015;
		}
		public Contribution contribution = new Contribution();

		public static final class Seed {
			@Range(min = 0, max = 1000000000) public int gold = 5000;
			@Range(min = 0, max = 1000000000) public int netherite = 50000;
		}
		public Seed seed = new Seed();

	}
	public Jackpot jackpot = new Jackpot();

	/** Fixed 3-star pay at owned machines. */
	@Range(min = 0, max = 100000) public double ownedStarPays = 1000.0;
	/** Animation length. */
	@Range(min = 10, max = 200) public int spinTicks = 50;
	/** On load, compute RTP from weights/pays; if a tier > 0.99 (incl. contribution) log a loud warning and show it on the admin page (never auto-fix). */
	public boolean validateRtp = true;
}
