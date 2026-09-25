package dev.nezo.burmaldaholic.core.pvp.logic;

/**
 * Public information a bot gets for one PvP decision (PVP.md §3.15.4, BOTS.md §4.8). The same values a
 * human in that seat sees; never the tape.
 *
 * <ul>
 *   <li>{@code coin.don_offer} (chain loser): return 1 = Double or nothing (side = {@code option}), 0 = walk away</li>
 *   <li>{@code coin.let_it_ride} (chain winner): 1 = let it ride, 0 = take the money</li>
 *   <li>{@code coin.side}: 0 heads, 1 tails</li>
 *   <li>{@code wheel.stake}: the stake to place (min … cap); {@code wheel.top_up}: extra chips (0 = none)</li>
 * </ul>
 *
 * @param decision       decision id, as above
 * @param seat           the bot's participant index
 * @param link           Coin Flip Duel chain link (1-based; 0 = n/a)
 * @param deficit        chain deficit D before rake
 * @param minStake       {@code pvp.minStake}
 * @param cap            Wheel Party cap C (or the bot's max stake)
 * @param currentStake   the bot's stake so far
 * @param medianHumanStake median human stake so far (Wheel Party NORMAL rule)
 * @param ticksLeft      ticks until the decision closes (No more bets, offer timeout)
 */
public record DecisionView(String decision, int seat, int link, long deficit, long minStake, long cap, long currentStake,
		long medianHumanStake, int ticksLeft) {}
