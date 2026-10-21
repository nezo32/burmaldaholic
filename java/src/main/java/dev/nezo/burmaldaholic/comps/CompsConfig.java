package dev.nezo.burmaldaholic.comps;

import dev.nezo.burmaldaholic.core.config.Range;

/**
 * Config section {@code comps} (EC-SPEC §4): comp points switch and the daily bonus. The comp-point rates per tier
 * live in {@code vip.compRate} (validated against the cashback rates there).
 */
public final class CompsConfig {
	/** Comp points, redemptions, free-bet tokens and the daily bonus. */
	public boolean enabled = true;

	public static final class Daily {
		@Range(min = 0, max = 1000000) public long bronze = 25;
		@Range(min = 0, max = 1000000) public long silver = 50;
		@Range(min = 0, max = 1000000) public long gold = 120;
		@Range(min = 0, max = 1000000) public long platinum = 300;
		@Range(min = 0, max = 1000000) public long diamond = 750;
		@Range(min = 0, max = 1000000) public long netherite = 2000;
		/** Lifetime chips wagered before the daily bonus unlocks. */
		@Range(min = 0, max = 1000000000) public long minLifetimeWager = 1000;
		/** Active minutes today before the daily bonus can be claimed. */
		@Range(min = 0, max = 1200) public int minActiveMinutes = 10;
		/** Dedicated servers: one claim per IP address per in-game day. */
		public boolean onePerIp = true;

		public long[] amounts() {
			return new long[] {bronze, silver, gold, platinum, diamond, netherite};
		}
	}
	public Daily daily = new Daily();

	/** Rental duration of the horse / strider ride rewards, in minutes. */
	@Range(min = 1, max = 120) public int rentalMinutes = 10;
}
