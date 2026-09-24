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

	/** Ticks until the next Minecraft day starts (heat reset, {@code …bots.error.capped} countdown). */
	public static long ticksToNextDay(long worldTime) {
		return 24000L - Math.floorMod(worldTime, 24000L);
	}

	/** The sulk line: {@code ceil(cap × sulkMultiplier)} (multiplier below 1 is treated as 1). */
	public static long sulkLine(long cap, double sulkMultiplier) {
		return (long) Math.ceil(cap * Math.max(1.0, sulkMultiplier));
	}

	/**
	 * Heat stage for a day's net against house bots (§5.4). {@code cap ≤ 0} disables heat.
	 * {@code net ≥ sulkLine} → SULKING; {@code net ≥ cap} → HARD_ONLY; else NONE.
	 */
	public static HeatStage heatStage(long netToday, long cap, double sulkMultiplier) {
		if (cap <= 0) {
			return HeatStage.NONE;
		}
		if (netToday >= sulkLine(cap, sulkMultiplier)) {
			return HeatStage.SULKING;
		}
		return netToday >= cap ? HeatStage.HARD_ONLY : HeatStage.NONE;
	}

	/**
	 * Money bots a purse can still fund with {@code buyIn} each: {@code buyIn ≤ 0} → unlimited; else
	 * {@code floor(available / buyIn)} (BANKROLL: {@code balance − reserved}; BANK: daily buy-ins left).
	 */
	public static int affordable(long available, long buyIn) {
		if (buyIn <= 0) {
			return Integer.MAX_VALUE;
		}
		return (int) Math.min(Integer.MAX_VALUE, Math.max(0, available) / buyIn);
	}

	/** House-funded buy-ins a table still has today ({@code perDay} 0 = unlimited). */
	public static int buyInsLeft(int perDay, long usedToday) {
		if (perDay <= 0) {
			return Integer.MAX_VALUE;
		}
		return (int) Math.max(0, perDay - usedToday);
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
