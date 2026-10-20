package dev.nezo.burmaldaholic.client.ui.v2;

/**
 * Where the {@link BetBar} sends what the player does (docs/architecture/ui-v2.md §4.2): each game maps chips and tools to
 * its server actions. Implementations send through {@code V2TableScreen#sendTracked} / {@link V2Chrome#send} so the
 * acknowledgement watchdog sees every action. Bets stay server-authoritative (D7): no money logic here.
 */
public interface BetPort {
	/** A chip clicked in a single-spot game: add {@code denom} to the bet. */
	void add(long denom);

	/** Right-click on a chip: remove one chip of {@code denom} (default: undo the last chip). */
	default void remove(long denom) {
		tool(dev.nezo.burmaldaholic.core.ui.v2.BetGates.UNDO);
	}

	/** A bet tool ({@code undo}, {@code clear}, {@code rebet}, {@code double}). */
	void tool(String toolId);

	/**
	 * Whether {@link #amount} sets the bet (single-spot games). False (default): the bet-entry control's amount becomes
	 * the unit a felt click places instead ({@link BetBar#unit}).
	 */
	default boolean setsAmount() {
		return false;
	}

	/**
	 * The bet-entry control's OK (lane HR): the bet becomes {@code amount} (already clamped to the limits and the
	 * balance; the server still validates). Returns false when the game cannot set an amount.
	 */
	default boolean amount(long amount) {
		return false;
	}
}
