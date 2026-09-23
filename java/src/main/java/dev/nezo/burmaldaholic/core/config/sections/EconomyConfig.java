package dev.nezo.burmaldaholic.core.config.sections;

import dev.nezo.burmaldaholic.core.config.Range;
import dev.nezo.burmaldaholic.core.config.Validatable;

/** Config section `economy` — keys, defaults and ranges from docs/design/CONFIG.md. */
public final class EconomyConfig implements Validatable {
	/** Balance cap. */
	@Range(min = 1000, max = 9007199254740991.0) public long maxBalance = 1000000000L;
	/** Credited on first join. */
	@Range(min = 0, max = 1000000) public int startingBalance = 50;
	/** Chips per emerald when buying chips. */
	@Range(min = 1, max = 1000) public int emeraldBuyRate = 8;
	/** Same for Gold VIP+. */
	@Range(min = 1, max = 1000) public int emeraldBuyRateGoldVip = 9;
	/** Chips per emerald when selling chips. Must be ≥ buy rate (else clamped). */
	@Range(min = 1, max = 1000) public int emeraldSellRate = 10;
	/** Nether cashier: chips per gold ingot. */
	@Range(min = 1, max = 1000) public int goldBuyRate = 3;
	/** Nether cashier: chips per gold ingot sold. */
	@Range(min = 1, max = 1000) public int goldSellRate = 12;
	public static final class Ore {
		@Range(min = 0, max = 1000) public int coal = 1;
		@Range(min = 0, max = 1000) public int copper = 1;
		@Range(min = 0, max = 1000) public int iron = 2;
		@Range(min = 0, max = 1000) public int gold = 4;
		@Range(min = 0, max = 1000) public int redstone = 1;
		@Range(min = 0, max = 1000) public int lapis = 2;
		@Range(min = 0, max = 1000) public int emerald = 15;
		@Range(min = 0, max = 1000) public int diamond = 20;
		@Range(min = 0, max = 1000) public int netherQuartz = 1;
		@Range(min = 0, max = 1000) public int netherGold = 1;
		@Range(min = 0, max = 10000) public int ancientDebris = 50;
		/** FIFO size of the placed-debris ledger. */
		@Range(min = 0, max = 65536) public int placedDebrisLedgerSize = 4096;
	}
	public Ore ore = new Ore();

	public static final class Mob {
		/** Category value (§3.4.2). */
		@Range(min = 0, max = 1000) public int common = 2;
		@Range(min = 0, max = 1000) public int creeper = 3;
		@Range(min = 0, max = 1000) public int phantom = 3;
		@Range(min = 0, max = 1000) public int pillager = 3;
		@Range(min = 0, max = 1000) public int piglin = 3;
		@Range(min = 0, max = 1000) public int enderman = 4;
		@Range(min = 0, max = 1000) public int blaze = 4;
		/** Also zoglin. */
		@Range(min = 0, max = 1000) public int hoglin = 4;
		@Range(min = 0, max = 1000) public int guardian = 4;
		@Range(min = 0, max = 1000) public int witch = 5;
		@Range(min = 0, max = 1000) public int vindicator = 5;
		@Range(min = 0, max = 1000) public int witherSkeleton = 5;
		@Range(min = 0, max = 1000) public int creaking = 5;
		@Range(min = 0, max = 1000) public int ghast = 6;
		@Range(min = 0, max = 1000) public int breeze = 8;
		@Range(min = 0, max = 1000) public int shulker = 8;
		@Range(min = 0, max = 1000) public int piglinBrute = 10;
		@Range(min = 0, max = 10000) public int evoker = 20;
		@Range(min = 0, max = 10000) public int ravager = 25;
		@Range(min = 0, max = 10000) public int elderGuardian = 100;
		@Range(min = 0, max = 100000) public int warden = 250;
		@Range(min = 0, max = 100000) public int wither = 500;
		/** First dragon kill in the world. */
		@Range(min = 0, max = 1000000) public int enderDragonFirst = 1000;
		/** Later dragon kills. */
		@Range(min = 0, max = 1000000) public int enderDragonRepeat = 200;
		/** Multiplier on Hard/Hardcore. */
		@Range(min = 0, max = 10) public double hardMultiplier = 1.25;
		/** Diminishing-returns window. */
		@Range(min = 20, max = 240000) public int windowTicks = 6000;
		/** Kills per type per window at 100 %. */
		@Range(min = 0, max = 10000) public int fullRewardKills = 20;
		/** Kills up to which 25 % is paid. */
		@Range(min = 0, max = 10000) public int reducedRewardKills = 60;
		@Range(min = 0, max = 1) public double reducedRewardFactor = 0.25;
		/** Pay for spawner-spawned mobs. */
		public boolean spawnerRewards = false;
	}
	public Mob mob = new Mob();

	public static final class Trade {
		/** Chips per trade. */
		@Range(min = 0, max = 1000) public int perTradeBase = 1;
		/** Plus chips per emerald in the trade. */
		@Range(min = 0, max = 1000) public int perEmerald = 1;
		@Range(min = 0, max = 10000) public int perTradeCap = 10;
		/** Per player per MCD. */
		@Range(min = 0, max = 1000000) public int dailyCap = 200;
	}
	public Trade trade = new Trade();


	@Override
	public void validate(Issues issues) {
		int maxBuy = Math.max(emeraldBuyRate, emeraldBuyRateGoldVip);
		if (emeraldSellRate < maxBuy) {
			issues.clamped("emeraldSellRate", emeraldSellRate, maxBuy);
			emeraldSellRate = maxBuy;
		}
		if (goldSellRate < goldBuyRate) {
			issues.clamped("goldSellRate", goldSellRate, goldBuyRate);
			goldSellRate = goldBuyRate;
		}
		if (mob.reducedRewardKills < mob.fullRewardKills) {
			issues.clamped("mob.reducedRewardKills", mob.reducedRewardKills, mob.fullRewardKills);
			mob.reducedRewardKills = mob.fullRewardKills;
		}
	}
}
