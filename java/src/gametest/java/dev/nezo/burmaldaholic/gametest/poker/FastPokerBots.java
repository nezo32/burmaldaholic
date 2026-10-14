package dev.nezo.burmaldaholic.gametest.poker;

import dev.nezo.burmaldaholic.core.config.CasinoConfig;

/**
 * Fast poker bots for GameTests ({@code poker.botThinkMinTicks / MaxTicks} = 0 / 2), shared by every test that
 * wants them. The config is global and the server GameTests of one batch run side by side, so a plain
 * "save, set, restore" per test races: a test that started first and finishes first restores the slow defaults
 * under the other one (and the other one then "restores" 0 / 2 for the rest of the run). Reference-counted: the
 * first {@link #acquire} saves the configured values, the last {@link #release} puts them back. Server thread only.
 */
public final class FastPokerBots {
	public static final int THINK_MIN = 0;
	public static final int THINK_MAX = 2;

	private static int users;
	private static int savedMin;
	private static int savedMax;

	private FastPokerBots() {}

	/** Turns fast bots on; returns a handle whose {@link Handle#release} is idempotent. */
	public static Handle acquire() {
		if (users++ == 0) {
			savedMin = CasinoConfig.poker().botThinkMinTicks;
			savedMax = CasinoConfig.poker().botThinkMaxTicks;
		}
		CasinoConfig.poker().botThinkMinTicks = THINK_MIN;
		CasinoConfig.poker().botThinkMaxTicks = THINK_MAX;
		return new Handle();
	}

	public static final class Handle {
		private boolean released;

		private Handle() {}

		public void release() {
			if (released) {
				return;
			}
			released = true;
			if (--users == 0) {
				CasinoConfig.poker().botThinkMinTicks = savedMin;
				CasinoConfig.poker().botThinkMaxTicks = savedMax;
			}
		}
	}
}
