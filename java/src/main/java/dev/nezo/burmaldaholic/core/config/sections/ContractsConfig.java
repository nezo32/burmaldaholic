package dev.nezo.burmaldaholic.core.config.sections;

import dev.nezo.burmaldaholic.core.config.Family;
import dev.nezo.burmaldaholic.core.config.Maps;
import dev.nezo.burmaldaholic.core.config.Range;
import java.util.Map;

/** Config section `contracts` — keys, defaults and ranges from docs/design/CONFIG.md. */
public final class ContractsConfig {
	public boolean enabled = true;
	/** Base slots (VIP adds, §12). */
	@Range(min = 1, max = 8) public int slots = 3;
	@Range(min = 0, max = 100000) public int rerollCost = 10;
	/** Target and reward scaling per VIP tier index. */
	@Range(min = 0, max = 2) public double tierScaling = 0.25;
	/** Global multiplier on contract rewards. */
	@Range(min = 0, max = 100) public double rewardMultiplier = 1.0;
	/** Pool weight per contract id (§3.4.4); 0 disables it. */
	@Family @Range(min = 0, max = 1000) public Map<String, Integer> weight = Maps.of("mine_iron", 10, "mine_coal", 8, "mine_diamond", 5, "kill_zombie", 10, "kill_skeleton", 8, "kill_creeper", 6, "kill_any", 8, "trade", 8, "fish", 6, "harvest", 6, "wager", 8, "win_blackjack", 5, "spin_slots", 5, "roulette_red", 4, "play_poker", 3, "explore_nether", 3, "smelt", 3);
}
