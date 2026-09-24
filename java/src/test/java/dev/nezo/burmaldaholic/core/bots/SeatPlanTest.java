package dev.nezo.burmaldaholic.core.bots;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotRole;
import dev.nezo.burmaldaholic.core.bots.logic.BotSettings;
import dev.nezo.burmaldaholic.core.bots.logic.BotSpeed;
import dev.nezo.burmaldaholic.core.bots.logic.BotsMode;
import dev.nezo.burmaldaholic.core.bots.logic.OwnerControls;
import dev.nezo.burmaldaholic.core.bots.logic.SeatPlan;
import dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy;
import dev.nezo.burmaldaholic.core.bots.logic.SeatingMath;
import dev.nezo.burmaldaholic.core.bots.logic.SeatingMath.YieldCandidate;
import dev.nezo.burmaldaholic.core.bots.logic.SeatingMath.YieldRule;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Safe-point planning: BOTS.md §12.4 S1–S9, S13, §5.1 buy-ins, §5.4 heat, §7.5 world limits. */
class SeatPlanTest {
	private static final BotSettings POKER = new BotSettings(SeatPolicy.MIXED, 5, BotDifficulty.MIXED, true, true, BotSpeed.NORMAL);
	private static final OwnerControls UNOWNED = OwnerControls.unowned(6);
	private static final UUID B = UUID.randomUUID();
	private static final UUID C = UUID.randomUUID();

	/** Builder over SeatPlan.Input with poker-like defaults (6 seats, bank purse, plenty of budget). */
	private static final class In {
		BotSettings settings = POKER;
		boolean enabled = true;
		int seats = 6;
		int humans = 1;
		List<UUID> claimants = new ArrayList<>();
		List<SeatPlan.Bot> bots = new ArrayList<>();
		YieldRule rule = YieldRule.POKER_BIG_BLIND;
		OwnerControls owner = UNOWNED;
		BotRole role = BotRole.MONEY;
		int atmosphereCap = 3;
		int budget = 64;
		boolean tableSlot = true;
		int affordable = Integer.MAX_VALUE;
		boolean hardOnly;
		boolean sulk;
		BotDifficulty relevel;

		In bots(int n) {
			for (int i = 0; i < n; i++) {
				// seat i+1 (the human sits in seat 0); bot i posted the BB i hands ago
				bots.add(bot("b" + i, i + 1, 200, i + 1, i, BotDifficulty.NORMAL));
			}
			return this;
		}

		SeatPlan.Plan plan() {
			return SeatPlan.plan(new SeatPlan.Input(settings, enabled, seats, humans, claimants, bots, rule, owner, role, atmosphereCap, budget,
				tableSlot, affordable, hardOnly, sulk, relevel));
		}
	}

	private static SeatPlan.Bot bot(String key, int seat, long stack, int joinOrder, int sinceBb, BotDifficulty level) {
		return new SeatPlan.Bot(key, seat, stack, joinOrder, false, sinceBb, level, true, false);
	}

	@Test
	void s1FillsLeavingOneSeatFree() {
		SeatPlan.Plan p = new In().plan();
		assertEquals(4, p.join());
		assertTrue(p.leave().isEmpty());
		assertEquals(SeatPlan.Limit.NONE, p.limit());
	}

	@Test
	void s2SecondHumanTakesTheFreeSeatThenOneBotLeaves() {
		In in = new In().bots(4);
		in.humans = 2;
		SeatPlan.Plan p = in.plan();
		assertEquals(List.of("b0"), p.leave(), "5 − 2 − 1 = 3 bots: the bot that just posted the BB leaves");
		assertTrue(p.yielded().isEmpty(), "nobody claimed: an ordinary departure");
		assertEquals(0, p.join());
	}

	@Test
	void s3ClaimantGetsTheSeatOfTheBigBlindBot() {
		In in = new In().bots(5);
		in.settings = POKER.withCount(5);
		in.settings = new BotSettings(SeatPolicy.MIXED, 5, BotDifficulty.MIXED, false, true, BotSpeed.NORMAL);
		in.claimants.add(B);
		SeatPlan.Plan p = in.plan();
		assertEquals(List.of("b0"), p.leave());
		assertEquals(List.of("b0"), p.yielded());
		assertEquals(List.of(B), p.seatedClaimants());
		assertEquals(0, p.join());
	}

	@Test
	void s5CountDownLeavesTheBotsThatPayTheBigBlindLast() {
		In in = new In().bots(4);
		in.settings = POKER.withCount(2);
		SeatPlan.Plan p = in.plan();
		assertEquals(List.of("b0", "b1"), p.leave());
	}

	@Test
	void s8LastHumanLeavesEveryBotLeaves() {
		In in = new In().bots(4);
		in.humans = 0;
		SeatPlan.Plan p = in.plan();
		assertEquals(4, p.leave().size());
		assertEquals(0, p.join());
		assertEquals(0, p.target());
	}

	@Test
	void s9ChemmyPunterYieldsBeforeTheBanker() {
		In in = new In();
		in.rule = YieldRule.CHEMMY_PUNTER_FIRST;
		in.settings = new BotSettings(SeatPolicy.MIXED, 2, BotDifficulty.MIXED, false, true, BotSpeed.NORMAL);
		in.seats = 3;
		in.bots.add(new SeatPlan.Bot("banker", 1, 500, 1, true, Integer.MAX_VALUE, BotDifficulty.NORMAL, true, false));
		in.bots.add(new SeatPlan.Bot("punter", 2, 0, 2, false, Integer.MAX_VALUE, BotDifficulty.NORMAL, true, false));
		in.claimants.add(B);
		assertEquals(List.of("punter"), in.plan().yielded());
		// no punter bot: the banker yields
		in.bots.remove(1);
		in.humans = 2;
		assertEquals(List.of("banker"), in.plan().yielded());
	}

	@Test
	void s13BankrollTooShortNoBotSits() {
		In in = new In();
		in.affordable = 0; // bankroll 150, buy-in 200
		SeatPlan.Plan p = in.plan();
		assertEquals(0, p.join());
		assertEquals(SeatPlan.Limit.PURSE, p.limit());
	}

	@Test
	void dailyBuyInBudgetLimitsReplacements() {
		In in = new In().bots(4);
		in.bots.set(2, new SeatPlan.Bot("b2", 3, 0, 3, false, 2, BotDifficulty.NORMAL, true, true));
		in.affordable = 0; // the 11th buy-in today
		SeatPlan.Plan p = in.plan();
		assertEquals(List.of("b2"), p.leave(), "a busted bot always leaves");
		assertEquals(0, p.join(), "...and is not replaced");
		assertEquals(SeatPlan.Limit.PURSE, p.limit());
		in.affordable = 1;
		assertEquals(1, in.plan().join(), "replaced while the budget allows");
	}

	@Test
	void heatHardOnlyReplacesEasyAndNormalHouseBots() {
		In in = new In();
		in.bots.add(bot("easy", 1, 200, 1, 3, BotDifficulty.EASY));
		in.bots.add(bot("hard", 2, 200, 2, 2, BotDifficulty.HARD));
		in.bots.add(new SeatPlan.Bot("owner", 3, 200, 3, false, 1, BotDifficulty.NORMAL, false, false)); // bankroll bot
		in.hardOnly = true;
		SeatPlan.Plan p = in.plan();
		assertEquals(List.of("easy"), p.leave());
		assertEquals(BotDifficulty.HARD, p.forcedLevel());
		assertEquals(2, p.join(), "4 wanted, 2 stay; the 2 newcomers are HARD");
	}

	@Test
	void newConcreteDifficultyReplacesOtherLevels() {
		In in = new In();
		in.bots.add(bot("n", 1, 200, 1, 1, BotDifficulty.NORMAL));
		in.bots.add(bot("h", 2, 200, 2, 0, BotDifficulty.HARD));
		in.relevel = BotDifficulty.HARD;
		SeatPlan.Plan p = in.plan();
		assertEquals(List.of("n"), p.leave());
		assertEquals(3, p.join());
		in.relevel = BotDifficulty.MIXED;
		assertTrue(in.plan().leave().isEmpty(), "MIXED keeps everyone");
	}

	@Test
	void sulkingEmptiesHouseBots() {
		In in = new In().bots(4);
		in.sulk = true;
		SeatPlan.Plan p = in.plan();
		assertEquals(4, p.leave().size());
		assertEquals(0, p.join());
	}

	@Test
	void worldLimits() {
		In in = new In();
		in.budget = 2;
		SeatPlan.Plan p = in.plan();
		assertEquals(2, p.join());
		assertEquals(SeatPlan.Limit.WORLD, p.limit());
		in.budget = 64;
		in.tableSlot = false;
		p = in.plan();
		assertEquals(0, p.join(), "bots.maxActiveTables reached: a new session gets none");
		assertEquals(SeatPlan.Limit.WORLD, p.limit());
		in = new In().bots(1);
		in.tableSlot = false; // a table that already has bots keeps its slot (caller passes true) — plan honours the flag only when empty
		assertEquals(3, in.plan().join());
	}

	@Test
	void ownerControlsAndAtmosphereCaps() {
		In in = new In();
		in.owner = new OwnerControls(BotsMode.ATMOSPHERE, true, 5, false);
		assertEquals(0, in.plan().join(), "Atmosphere only: no money bots");
		in.role = BotRole.ATMOSPHERE;
		in.settings = new BotSettings(SeatPolicy.MIXED, 5, BotDifficulty.NORMAL, true, true, BotSpeed.NORMAL);
		assertEquals(3, in.plan().join(), "atmosphere cap");
		in.owner = new OwnerControls(BotsMode.ALLOWED, true, 2, true);
		assertEquals(2, in.plan().join(), "owner max bots");
		in.owner = new OwnerControls(BotsMode.OFF, true, 5, true);
		assertEquals(0, in.plan().join());
		in = new In();
		in.role = BotRole.ATMOSPHERE;
		in.affordable = 0;
		in.settings = new BotSettings(SeatPolicy.MIXED, 2, BotDifficulty.NORMAL, true, true, BotSpeed.NORMAL);
		assertEquals(2, in.plan().join(), "atmosphere bots are free: the purse never limits them");
		in.enabled = false;
		assertEquals(0, in.plan().join(), "bots.enabled = false");
	}

	@Test
	void botsOnlyExactCountAndNoForcedLevelOutsideHeat() {
		In in = new In();
		in.settings = new BotSettings(SeatPolicy.BOTS_ONLY, 3, BotDifficulty.HARD, true, true, BotSpeed.INSTANT);
		SeatPlan.Plan p = in.plan();
		assertEquals(3, p.join());
		assertNull(p.forcedLevel());
	}

	@Test
	void keepFreeOffClaimantsAlwaysGetSeats() {
		In in = new In().bots(5);
		in.settings = new BotSettings(SeatPolicy.MIXED, 5, BotDifficulty.NORMAL, false, true, BotSpeed.NORMAL);
		in.claimants.add(B);
		in.claimants.add(C);
		SeatPlan.Plan p = in.plan();
		assertEquals(List.of("b0", "b1"), p.leave());
		assertEquals(List.of(B, C), p.seatedClaimants());
		assertEquals(0, p.join());
		assertEquals(in.seats, in.humans + (5 - p.leave().size()) + p.seatedClaimants().size() + p.join(), "every seat accounted");
	}

	@Test
	void yieldRules() {
		List<YieldCandidate> c = List.of(
			new YieldCandidate("a", 1, 300, 1, false, 2),
			new YieldCandidate("b", 2, 100, 3, true, 0),
			new YieldCandidate("c", 3, 50, 2, false, 0));
		assertEquals(List.of("c", "b", "a"), SeatingMath.yieldOrder(YieldRule.POKER_BIG_BLIND, c), "BB just posted; tie → smallest stack");
		assertEquals(List.of("c", "a", "b"), SeatingMath.yieldOrder(YieldRule.CHEMMY_PUNTER_FIRST, c), "punters (highest seat) first, banker last");
		assertEquals(List.of("c", "b", "a"), SeatingMath.yieldOrder(YieldRule.HIGHEST_SEAT, c));
		assertEquals(List.of("b", "c", "a"), SeatingMath.yieldOrder(YieldRule.LAST_JOINED, c));
	}

	@Test
	void hostSelection() {
		UUID keeper = UUID.randomUUID();
		UUID a = UUID.randomUUID();
		assertEquals(a, SeatingMath.host(keeper, List.of(a, B)), "keeper not seated: longest seated");
		assertEquals(keeper, SeatingMath.host(keeper, List.of(a, keeper)), "a seated keeper is always the host");
		assertEquals(B, SeatingMath.host(null, List.of(B)), "S7: the next-longest human takes over");
		assertNull(SeatingMath.host(keeper, List.of()));
	}
}
