package dev.nezo.burmaldaholic.core.economy;

import java.util.Objects;

/** Service locator for the active {@link Economy} implementation (core may swap it). */
public final class Economies {
	private static Economy economy = new LedgerEconomy();

	private Economies() {}

	public static Economy get() {
		return economy;
	}

	/** Core / tests only. */
	public static void set(Economy impl) {
		economy = Objects.requireNonNull(impl);
	}
}
