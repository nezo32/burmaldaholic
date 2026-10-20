package dev.nezo.burmaldaholic.games.slots.v2.present;

/**
 * What the v2 kit Dock needs to know about the slot machine screen (owner B6: the slots are on the v2 kit like every other
 * game). Built by the screen from the server state AND the running stage ({@code busy}); {@link SlotFlow} maps it to the
 * gates. Pure value.
 *
 * @param playable        the machine accepts spins (enabled, VIP ok, a bet offered)
 * @param locked          above the player's VIP tier ({@code lockArgs} = required tier, the player's tier)
 * @param busy            a spin is playing on the screen (SPIN is STOP = skip)
 * @param autoRunning     autoplay is running (its spins left)
 * @param bet             the selected total bet
 * @param betIndex        index on the offered ladder
 * @param betCount        bets offered (VIP / owner capped)
 * @param balance         the player's chips
 * @param coin            coin value of the "coin × multiplier" display (owner R3)
 * @param buyAllowed      the buy feature is offered here
 * @param buyPrice        its price at the selected bet
 * @param autoplayAllowed autoplay is enabled
 * @param turboAllowed    turbo is enabled
 * @param turbo           turbo is on
 * @param showdown        the Slot Showdown entry (label and args; its action is {@link SlotFlow#SHOWDOWN}), or null
 */
public record SlotView(boolean playable, boolean locked, String[] lockArgs, boolean busy, boolean autoRunning, long bet, int betIndex,
		int betCount, long balance, long coin, boolean buyAllowed, long buyPrice, boolean autoplayAllowed, boolean turboAllowed, boolean turbo,
		dev.nezo.burmaldaholic.core.ui.v2.Gate showdown) {
	public SlotView {
		lockArgs = lockArgs == null ? new String[0] : lockArgs.clone();
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof SlotView v && v.playable == playable && v.locked == locked && java.util.Arrays.equals(v.lockArgs, lockArgs) && v.busy == busy
			&& v.autoRunning == autoRunning && v.bet == bet && v.betIndex == betIndex && v.betCount == betCount && v.balance == balance && v.coin == coin
			&& v.buyAllowed == buyAllowed && v.buyPrice == buyPrice && v.autoplayAllowed == autoplayAllowed && v.turboAllowed == turboAllowed
			&& v.turbo == turbo && sameGate(v.showdown, showdown);
	}

	private static boolean sameGate(dev.nezo.burmaldaholic.core.ui.v2.Gate a, dev.nezo.burmaldaholic.core.ui.v2.Gate b) {
		if (a == null || b == null) return a == b;
		return a.labelKey().equals(b.labelKey()) && java.util.Arrays.equals(a.labelArgs(), b.labelArgs()) && a.enabled() == b.enabled();
	}

	@Override
	public int hashCode() {
		return java.util.Objects.hash(playable, busy, autoRunning, bet, betIndex, balance, turbo, showdown == null);
	}
}
