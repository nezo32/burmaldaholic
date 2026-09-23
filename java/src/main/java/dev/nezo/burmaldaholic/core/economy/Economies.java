package dev.nezo.burmaldaholic.core.economy;

import java.util.Objects;

/** Service locator for the active {@link Economy} implementation (core may swap it). */
public final class Economies {
	private static Economy economy = new AttachmentEconomy();

	private Economies() {}

	public static Economy get() {
		return economy;
	}

	/** Core only. */
	public static void set(Economy impl) {
		economy = Objects.requireNonNull(impl);
	}
}
