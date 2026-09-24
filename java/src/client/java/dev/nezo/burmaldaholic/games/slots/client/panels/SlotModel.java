package dev.nezo.burmaldaholic.games.slots.client.panels;

import dev.nezo.burmaldaholic.games.slots.v2.logic.MachineDef;

/**
 * What the slot screen shows outside the reels (filled from the server's machine state by the real screen, from
 * fixtures by the preview): bet ladder, balance, jackpot pools, buy / autoplay / turbo availability, RTP lines.
 * Plain mutable holder; the screen owns it.
 */
public final class SlotModel {
	public MachineDef def;
	/** Bet levels allowed for this player (VIP-gated, multiples of 5). */
	public long[] bets = {5, 10, 25, 50, 100};
	public int betIndex = 3;
	public long balance;
	/** Jackpot pools Mini … Grand (chips; 0 = not shown, e.g. owned machines with fixed prizes). */
	public long[] pools = new long[4];
	public boolean turboAllowed = true;
	public boolean turbo;
	public boolean buyAllowed;
	public boolean autoplayAllowed = true;
	public int[] autoCounts = {10, 25, 50, 100};
	public int[] lossLimits = {10, 25, 50, 100};
	/** Autoplay spins left (−1 = off). */
	public int autoLeft = -1;
	public boolean playable = true;
	/** Return to player in hundredths of a percent (e.g. 9507), 0 = unknown. */
	public int rtpBasisPoints;
	public int buyRtpBasisPoints;
	/** Error line key (null = none). */
	public String errorKey;
	public long errorAt;

	public long bet() {
		return bets[Math.max(0, Math.min(bets.length - 1, betIndex))];
	}

	public long buyPrice() {
		return def == null ? 0 : (long) def.buyPriceFifths() * bet() / 5;
	}

	public boolean canBuy() {
		return buyAllowed && def != null && def.buyPriceFifths() > 0;
	}
}
