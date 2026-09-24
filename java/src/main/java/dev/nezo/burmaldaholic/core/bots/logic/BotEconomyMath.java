package dev.nezo.burmaldaholic.core.bots.logic;

/**
 * Pure money rules for rounds against MONEY bots (BOTS.md §5.3, §5.4). Integer arithmetic, floor,
 * identical in both editions.
 */
public final class BotEconomyMath {
	private BotEconomyMath() {}

	/** Minecraft day of a world time: {@code floor(worldTime / 24000)}. */
	public static long mcDay(long worldTime) {
		return Math.floorDiv(worldTime, 24000L);
	}

	/** Daily cap on net winnings from house-funded bots: {@code max(min, multiple × tierMax)}. */
	public static long dailyCap(long tierMax, long capMin, long tierMultiple) {
		return Math.max(capMin, Math.multiplyExact(tierMultiple, Math.max(0, tierMax)));
	}

	/**
	 * VIP lifetime wagered / {@code wager} contract credit of a bot round (§5.3):
	 * {@code floor(wagered × (1 − (1 − weight) × botShare))}.
	 */
	public static long vipCredit(long wagered, double weight, double botShare) {
		return (long) Math.floor(wagered * (1 - (1 - weight) * botShare));
	}

	/** PvP at an owned anchor: the bots' share {@code floor(rake × botStakes / pot)} goes to the bank sink (§5.1). */
	public static long pvpRakeToBank(long rake, long botStakes, long pot) {
		return pot <= 0 ? 0 : Math.floorDiv(Math.multiplyExact(rake, botStakes), pot);
	}

	/**
	 * Poker, one pot: the human's net vs bots in that pot.
	 * {@code floor(won × botContrib / pot) − floor(contrib × botWon / pot)}.
	 */
	public static long pokerPot(long humanWon, long humanContrib, long botContrib, long botWon, long pot) {
		if (pot <= 0) {
			return 0;
		}
		return Math.floorDiv(Math.multiplyExact(humanWon, botContrib), pot) - Math.floorDiv(Math.multiplyExact(humanContrib, botWon), pot);
	}

	/**
	 * PvP match: attribution of a human's net {@code n} to bots.
	 * n &gt; 0: {@code floor(n × botStakes / otherStakes)}; n &lt; 0: {@code floor(n × botPayouts / otherPayouts)}.
	 */
	public static long pvp(long net, long botStakes, long otherStakes, long botPayouts, long otherPayouts) {
		if (net > 0) {
			return otherStakes <= 0 ? 0 : Math.floorDiv(Math.multiplyExact(net, botStakes), otherStakes);
		}
		if (net < 0) {
			return otherPayouts <= 0 ? 0 : Math.floorDiv(Math.multiplyExact(net, botPayouts), otherPayouts);
		}
		return 0;
	}
}
