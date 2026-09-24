package dev.nezo.burmaldaholic.games.poker.logic;

import static dev.nezo.burmaldaholic.games.poker.logic.PokerTestSupport.EASY;
import static dev.nezo.burmaldaholic.games.poker.logic.PokerTestSupport.EASY_TAG;
import static dev.nezo.burmaldaholic.games.poker.logic.PokerTestSupport.HARD;
import static dev.nezo.burmaldaholic.games.poker.logic.PokerTestSupport.NEVER;
import static dev.nezo.burmaldaholic.games.poker.logic.PokerTestSupport.NORMAL;
import static dev.nezo.burmaldaholic.games.poker.logic.PokerTestSupport.POLICY;
import static dev.nezo.burmaldaholic.games.poker.logic.PokerTestSupport.act;
import static dev.nezo.burmaldaholic.games.poker.logic.PokerTestSupport.bot;
import static dev.nezo.burmaldaholic.games.poker.logic.PokerTestSupport.res;
import static dev.nezo.burmaldaholic.games.poker.logic.PokerTestSupport.seq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotProfile;
import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import dev.nezo.burmaldaholic.core.bots.logic.BotWork;
import dev.nezo.burmaldaholic.core.bots.logic.Personality;
import dev.nezo.burmaldaholic.games.poker.logic.Hand.Action;
import dev.nezo.burmaldaholic.games.poker.logic.PokerBotPolicy.Position;
import dev.nezo.burmaldaholic.games.poker.logic.PokerTestSupport.V;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** The poker bot policy (BOTS.md §4.3) — same vectors as Bedrock {@code bots.test.ts}. */
class PokerBotPolicyTest {
	private static int chen(String hand) {
		return Equity.chen(Cards.parseAll(hand));
	}

	// ---- Chen ------------------------------------------------------------------------------------------

	@Test
	void chenScores() {
		String[][] cases = {{"As Ah", "20"}, {"Ks Kh", "16"}, {"2s 2h", "5"}, {"6s 6h", "6"}, {"As Ks", "12"}, {"As Kh", "10"},
			{"Ts 9s", "8"}, {"Js Ts", "9"}, {"7s 2h", "-1"}, {"As 2h", "5"}, {"5s 4s", "6"}, {"Ks Jh", "7"}};
		for (String[] c : cases) {
			assertEquals(Integer.parseInt(c[1]), chen(c[0]), c[0]);
		}
		int t = Equity.chenThreshold(0.4);
		assertTrue(t >= 4 && t <= 7, "top 40 % threshold " + t);
	}

	// ---- positions / view ---------------------------------------------------------------------------------

	private static Hand deal(int n, int button) {
		List<Hand.Seed> seeds = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			seeds.add(new Hand.Seed("p" + i, i == 0, 1000));
		}
		return new Hand(seeds, PokerRng.of(new SplittableRandom(1)), new Hand.Options(5, 10, button, Pots.RakeConfig.NONE, null));
	}

	@Test
	void positionsSixMaxAndHeadsUp() {
		Hand s = deal(6, 0);
		List<Position> pos = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			pos.add(PokerBotPolicy.positionOf(s, i));
		}
		assertEquals(List.of(Position.BTN, Position.SB, Position.BB, Position.EP, Position.MP, Position.CO), pos);
		Hand hu = deal(2, 1);
		assertEquals(Position.BB, PokerBotPolicy.positionOf(hu, 0));
		assertEquals(Position.BTN, PokerBotPolicy.positionOf(hu, 1));
	}

	@Test
	void viewHoldsPublicInformationOnly() {
		Hand s = deal(3, 0);
		s.apply(Action.call()); // p0 (button) limps
		Ranges.HumanStats station = new Ranges.HumanStats(20, 0.5, 0, 0.5);
		PokerBotPolicy.View v = PokerBotPolicy.view(s, 1, id -> id.equals("p0") ? station : null, false);
		assertTrue(Arrays.equals(s.player(1).hole(), v.hole()));
		// structural no-peeking check (BOTS.md §12.1.2): no field holds another seat's cards or the deck
		for (RecordComponent rc : PokerBotPolicy.View.class.getRecordComponents()) {
			String n = rc.getName().toLowerCase(java.util.Locale.ROOT);
			assertFalse(n.contains("deck") || n.contains("players") || n.contains("rng"), "view field " + n);
		}
		for (int c : s.player(0).hole()) {
			assertFalse(contains(v.hole(), c) || contains(v.board(), c));
		}
		for (int c : s.player(2).hole()) {
			assertFalse(contains(v.hole(), c) || contains(v.board(), c));
		}
		assertEquals(1, v.limpers());
		assertEquals(5, v.toCall());
		assertEquals(25, v.pot());
		assertEquals(Ranges.OpponentType.STATION, v.opps().get(0).type());
		assertTrue(v.opps().get(0).human());
		assertEquals(Ranges.Tag.ANY, v.opps().get(0).tag());
		assertFalse(v.opps().get(1).human());
		assertNull(v.opps().get(1).stats(), "bots are never modelled");
	}

	private static boolean contains(int[] a, int c) {
		for (int x : a) {
			if (x == c) {
				return true;
			}
		}
		return false;
	}

	// ---- hand reading ------------------------------------------------------------------------------------

	@Test
	void handReading() {
		assertEquals(0, PokerBotPolicy.madeCategory(Cards.parseAll("As Qh"), Cards.parseAll("7d 7c 2s")), "board pair only");
		assertEquals(3, PokerBotPolicy.madeCategory(Cards.parseAll("As 7h"), Cards.parseAll("7d 7c 2s")));
		assertEquals(9, PokerBotPolicy.drawOuts(Cards.parseAll("As 5s"), Cards.parseAll("Ks 7s 2d")));
		assertEquals(8, PokerBotPolicy.drawOuts(Cards.parseAll("9h 8d"), Cards.parseAll("7c 6s 2d")));
		assertEquals(0, PokerBotPolicy.drawOuts(Cards.parseAll("9h 8d"), Cards.parseAll("7c 6s 2d Kc Qc")), "none on the river");
		assertEquals(0, PokerBotPolicy.drawOuts(Cards.parseAll("Ah 2d"), Cards.parseAll("Kc 6s 9d")));
	}

	// ---- EASY ----------------------------------------------------------------------------------------------

	private static V shove(String hole) {
		V v = new V(hole).facing(1000);
		v.stack = 1000;
		v.canRaise = false;
		v.maxRaiseTo = 1000;
		return v;
	}

	@Test
	void fishAlwaysCallsPremiumsFacingAShoveEvenThroughMistakes() {
		for (String h : List.of("As Ah", "Ts Th", "As Ks", "Qs Qd")) {
			assertTrue(PokerBotPolicy.fishPremium(Cards.parseAll(h)), h);
			for (double r : new double[] {0.0, 0.1, 0.5, 0.99}) {
				assertEquals(Action.call(), act(EASY, shove(h), null, seq(r)), h + " @" + r + " (research §1.6.1)");
			}
		}
		assertFalse(PokerBotPolicy.fishPremium(Cards.parseAll("9s 9h")));
		assertEquals(Action.fold(), act(EASY, shove("9s 9h"), null, NEVER));
	}

	@Test
	void fishPreflop() {
		assertEquals(Action.call(), act(EASY_TAG, new V("9s 8h"), null, seq(0.5, 0.99)), "limps C ≥ 4 (65 %)");
		assertEquals(Action.fold(), act(EASY_TAG, new V("9s 8h"), null, seq(0.7, 0.99)));
		assertEquals(Action.raiseTo(30), act(EASY_TAG, new V("As Kh"), null, NEVER), "raises 3 BB with C ≥ 10");
		assertEquals(Action.fold(), act(EASY_TAG, new V("7s 2h"), null, NEVER));
		V free = new V("7s 2h");
		free.canCheck = true;
		free.toCall = 0;
		assertEquals(Action.check(), act(EASY_TAG, free, null, NEVER));
	}

	@Test
	void fishCallsSmallRaisesWithPairsAcesSuitedBroadways() {
		V v = new V("4s 4h").facing(100);
		assertEquals(Action.call(), act(EASY_TAG, v, null, NEVER));
		V big = new V("4s 4h").facing(200);
		assertEquals(Action.fold(), act(EASY_TAG, big, null, NEVER));
		assertEquals(Action.call(), act(bot(BotDifficulty.EASY, Personality.STATION), big, null, NEVER), "a station calls 1.5× wider");
	}

	@Test
	void fishPostflopCallsPairsDrawsAndFloats() {
		assertEquals(Action.call(), act(EASY_TAG, new V("Ks Qh").board("Kd 7c 2s").flopBet(), null, NEVER), "a pair");
		assertEquals(Action.call(), act(EASY_TAG, new V("As 5s").board("Ks 7s 2d").flopBet(), null, NEVER), "a flush draw");
		assertEquals(Action.call(), act(EASY_TAG, new V("As Qh").board("9d 7c 2s").flopBet(), null, seq(0.2, 0.99)), "floats overcards 35 %");
		assertEquals(Action.fold(), act(EASY_TAG, new V("As Qh").board("9d 7c 2s").flopBet(), null, seq(0.5, 0.99)));
		assertEquals(Action.raiseTo(50), act(EASY_TAG, new V("Ks 7h").board("Kd 7c 2s").flop(), null, NEVER), "two pair: ½ pot");
		assertEquals(Action.raiseTo(50), act(EASY_TAG, new V("As Qh").board("Kd 7c 2s").flop(), null, seq(0.01, 0.99)), "5 % bluff");
		assertEquals(Action.check(), act(EASY_TAG, new V("As Qh").board("Kd 7c 2s").flop(), null, NEVER));
	}

	@Test
	void fishMistakesAndTilt() {
		V v = new V("Ks Qh").board("Kd 7c 2s").flopBet();
		assertEquals(Action.fold(), act(EASY_TAG, v, null, seq(0.1, 0.2)), "15 %: next-lower");
		assertEquals(Action.raiseTo(100), act(EASY_TAG, v, null, seq(0.1, 0.8)), "15 %: next-higher");
		V tilt = new V("9s 6h"); // C 3
		assertEquals(Action.fold(), act(EASY_TAG, tilt, null, seq(0.5, 0.99)));
		tilt.tilt = true;
		assertEquals(Action.call(), act(EASY_TAG, tilt, null, seq(0.5, 0.99)), "tilt: C − 1 looser");
	}

	// ---- NORMAL ------------------------------------------------------------------------------------------

	@Test
	void regularOpensByPosition() {
		V ep = new V("Ts 9s"); // C 8
		ep.position = Position.EP;
		assertEquals(Action.raiseTo(30), act(NORMAL, ep, null, NEVER));
		V ep7 = new V("7s 6s"); // C 7
		ep7.position = Position.EP;
		assertEquals(Action.fold(), act(NORMAL, ep7, null, NEVER));
		V mp = new V("7s 6s");
		mp.limpers = 2;
		assertEquals(Action.raiseTo(50), act(NORMAL, mp, null, NEVER), "+1 BB per limper");
		V bb = new V("7s 2h");
		bb.position = Position.BB;
		bb.canCheck = true;
		bb.toCall = 0;
		assertEquals(Action.check(), act(NORMAL, bb, null, NEVER));
	}

	@Test
	void regularFacingARaiseAndPostflop() {
		assertEquals(Action.raiseTo(90), act(NORMAL, new V("As Ks").facing(30), null, NEVER), "3-bet ≥ 11");
		assertEquals(Action.call(), act(NORMAL, new V("As Js").facing(30), null, NEVER), "call ≥ 9");
		assertEquals(Action.fold(), act(NORMAL, new V("Ts 9s").facing(30), null, NEVER));
		assertEquals(Action.call(), act(NORMAL, new V("As Ks").facing(30), null, seq(0.05)), "8 %: next-lower");
		V turn = new V("As Ks");
		turn.street = Hand.Street.TURN;
		turn.pot = 200;
		turn.currentBet = 100;
		turn.toCall = 100;
		turn.minRaiseTo = 200;
		assertEquals(Action.raiseTo(298), act(NORMAL, turn, res(0.7), NEVER), "value ≥ 0.65: 66 % pot");
		assertEquals(Action.call(), act(NORMAL, turn, res(0.4), NEVER), "E ≥ po + 0.05");
		assertEquals(Action.fold(), act(NORMAL, turn, res(0.36), NEVER));
	}

	@Test
	void regularContinuationBetsNeverBluffsTheRiver() {
		V v = new V("7s 2h").board("Kd 9c 4s").flop();
		v.preflopRaiser = true;
		v.opponents = 1;
		assertEquals(Action.raiseTo(50), act(NORMAL, v, res(0.2), seq(0.4, 0.99)), "50 % heads-up");
		v.opponents = 2;
		assertEquals(Action.check(), act(NORMAL, v, res(0.2), seq(0.4, 0.99)), "25 % multiway");
		V river = new V("7s 2h").board("Kd 9c 4s 3h 2c").flop();
		river.street = Hand.Street.RIVER;
		river.preflopRaiser = true;
		assertEquals(Action.check(), act(NORMAL, river, res(0.1), seq(0.01)));
	}

	// ---- HARD ----------------------------------------------------------------------------------------------

	@Test
	void sharkOpensByPositionAndSteals() {
		V ep = new V("Ts 9s");
		ep.position = Position.EP;
		assertEquals(Action.fold(), act(HARD, ep, null, NEVER), "EP needs 9");
		assertEquals(Action.raiseTo(25), act(HARD, new V("Ts 9s"), null, NEVER), "MP 8, 2.5 BB");
		V btn = new V("8s 7h"); // C 5
		btn.position = Position.BTN;
		assertEquals(Action.raiseTo(25), act(HARD, btn, null, NEVER));
		V junk = new V("9s 6s"); // C 5 < CO 7
		junk.position = Position.CO;
		junk.foldedTo = true;
		assertEquals(Action.raiseTo(25), act(HARD, junk, null, seq(0.3)), "steals 40 %");
		assertEquals(Action.fold(), act(HARD, junk, null, seq(0.5)));
	}

	@Test
	void sharkFacingAShove() {
		V qq = new V("Qs Qh").facing(1000);
		qq.toCall = 990;
		qq.stack = 990;
		qq.bet = 10;
		qq.pot = 1015;
		qq.canRaise = false;
		assertTrue(PokerBotPolicy.bigPreflop(qq.build()));
		assertEquals(Action.call(), act(HARD, qq, res(0.4), NEVER), "top 5 % always");
		V aj = new V("As Jd").facing(1000);
		aj.toCall = 990;
		aj.stack = 990;
		aj.bet = 10;
		aj.pot = 1015;
		aj.canRaise = false;
		assertEquals(Action.call(), act(HARD, aj, res(0.52), NEVER), "E ≥ po ≈ 0.49");
		assertEquals(Action.fold(), act(HARD, aj, res(0.45), NEVER));
		PokerBotPolicy.Config cfg = new PokerBotPolicy.Config(300, 700);
		assertEquals(700, PokerBotPolicy.samplesFor(BotDifficulty.HARD, qq.build(), cfg));
		assertEquals(0, PokerBotPolicy.samplesFor(BotDifficulty.NORMAL, qq.build(), cfg));
	}

	@Test
	void sharkMinimumDefenceStationsAndBudgetFallback() {
		V v = new V("Qs Jh").board("Qd 7c 2s").flopBet();
		assertEquals(Action.fold(), act(HARD, v, res(0.2, 0.2, 700), NEVER));
		assertEquals(Action.call(), act(HARD, v, res(0.2, 0.6, 700), NEVER), "b = 0.5 → top 67 % of its range");
		// budget fallback: < 50 samples → the NORMAL rule on those samples
		assertEquals(Action.call(), act(HARD, v, res(0.2, 0.9, 700), NEVER));
		assertEquals(Action.fold(), act(HARD, v, res(0.2, 0.9, 30), NEVER));
		assertEquals(Action.call(), act(HARD, v, res(0.33, 0.9, 30), NEVER));

		V st = new V("Qs Jh").board("Qd 7c 2s").flop();
		st.opps = List.of(new PokerBotPolicy.Opp(true, Ranges.Tag.ANY, null, Ranges.OpponentType.STATION, false));
		st.opponents = 1;
		st.preflopRaiser = true;
		assertEquals(Action.raiseTo(50), act(HARD, st, res(0.6), seq(0)), "thin value vs a station: ½ pot");
		assertEquals(Action.raiseTo(100), act(HARD, st, res(0.8), seq(0)), "strong value vs a station: pot");
		assertEquals(Action.check(), act(HARD, st, res(0.2), seq(0.01)), "never bluffs a station");
		st.opps = List.of(new PokerBotPolicy.Opp(true, Ranges.Tag.ANY, null, Ranges.OpponentType.REGULAR, false));
		assertNotEquals(Action.check(), act(HARD, st, res(0.2), seq(0.01, 0.01)));
	}

	// ---- policy contract --------------------------------------------------------------------------------------

	@Test
	void workIsTheRangeEquityAndActLegalizes() {
		V v = new V("As Ks").board("Ad 7c 2s").flop();
		BotWork w = POLICY.work(HARD, v.build(), BotRng_seeded(1));
		assertNotNull(w);
		while (!w.done()) {
			w.step(25);
		}
		assertEquals(Action.Kind.RAISE, POLICY.act(HARD, v.build(), w.result(), BotRng_seeded(2)).kind());
		assertNull(POLICY.work(EASY, v.build(), BotRng_seeded(1)), "EASY does not simulate");
		assertEquals(0, PokerBotPolicy.samplesFor(BotDifficulty.NORMAL, new V("As Ks").build(), POLICY.config()), "NORMAL preflop: no simulation");
	}

	private static BotRng BotRng_seeded(long s) {
		return BotRng.seeded(s);
	}

	/** BOTS.md §12.2 "Legalize": every bot action on random hands is legal as is. */
	@Test
	void everyBotActionIsLegal() {
		BotRng rng = BotRng.seeded(77);
		PokerRng deckRng = PokerRng.of(new SplittableRandom(77));
		PokerBotPolicy policy = new PokerBotPolicy(new PokerBotPolicy.Config(20, 20));
		BotDifficulty[] levels = {BotDifficulty.EASY, BotDifficulty.NORMAL, BotDifficulty.HARD};
		int actions = 0;
		for (int n = 0; n < 300; n++) {
			int players = 2 + rng.nextInt(5);
			List<Hand.Seed> seeds = new ArrayList<>();
			for (int i = 0; i < players; i++) {
				seeds.add(new Hand.Seed("p" + i, false, 50 + rng.nextInt(3000)));
			}
			Hand s = new Hand(seeds, deckRng, new Hand.Options(5, 10, n, Pots.RakeConfig.NONE, null));
			while (!s.complete()) {
				BotProfile b = bot(levels[rng.nextInt(3)], Personality.values()[rng.nextInt(Personality.values().length)]);
				Action a = policy.decideNow(b, PokerBotPolicy.view(s, s.toAct(), id -> null, rng.chance(0.2)), rng);
				Hand.Legal l = s.legal();
				if (a.kind() == Action.Kind.RAISE) {
					assertTrue(a.to() >= l.minRaiseTo() && a.to() < l.maxRaiseTo(), "raise " + a + " within " + l);
				}
				assertEquals(a, s.coerce(a), "already legal: " + a);
				s.apply(a);
				actions++;
			}
		}
		assertTrue(actions > 500);
	}

	@Test
	void sizeTo() {
		V bet = new V("As Ks");
		bet.pot = 100;
		bet.currentBet = 0;
		bet.toCall = 0;
		assertEquals(50, PokerBotPolicy.sizeTo(bet.build(), 0.5));
		V raise = new V("As Ks");
		raise.pot = 150;
		raise.currentBet = 50;
		raise.toCall = 50;
		assertEquals(250, PokerBotPolicy.sizeTo(raise.build(), 1));
	}

	// ---- levels, names and stake gates -----------------------------------------------------------------------

	@Test
	void pokerNamesOfTheLevels() {
		assertEquals(List.of("fish", "regular", "shark"),
			List.of(PokerBotPolicy.tierOf(BotDifficulty.EASY), PokerBotPolicy.tierOf(BotDifficulty.NORMAL), PokerBotPolicy.tierOf(BotDifficulty.HARD)));
		assertEquals(BotDifficulty.EASY, BotDifficulty.fromPokerTier("fish"), "legacy tier ids still load");
		assertEquals(BotDifficulty.HARD, BotDifficulty.fromPokerTier("shark"));
	}

	@Test
	void easyOnlyAtMicroAndLowByDefault() {
		List<Boolean> allowed = new ArrayList<>();
		for (StakeLevel s : StakeLevel.values()) {
			allowed.add(PokerBotPolicy.easyAllowed(s, "LOW"));
		}
		assertEquals(List.of(true, true, false, false), allowed);
		assertTrue(PokerBotPolicy.easyAllowed(StakeLevel.HIGH, "HIGH"));
		assertFalse(PokerBotPolicy.easyAllowed(StakeLevel.LOW, "MICRO"));
		assertEquals(BotDifficulty.NORMAL, PokerBotPolicy.gatedLevel(BotDifficulty.EASY, StakeLevel.MID, "LOW"));
		assertEquals(BotDifficulty.EASY, PokerBotPolicy.gatedLevel(BotDifficulty.EASY, StakeLevel.MICRO, "LOW"));
		assertEquals(BotDifficulty.HARD, PokerBotPolicy.gatedLevel(BotDifficulty.HARD, StakeLevel.HIGH, "LOW"));
	}

	/** BOTS.md §12.2 "Stake gate": at Mid and High 10 000 MIXED fills never produce EASY; Micro mix 45/45/10 ± 1.5 %. */
	@Test
	void mixedFillsRespectTheStakeGate() {
		BotRng rng = BotRng.seeded(12);
		for (StakeLevel s : List.of(StakeLevel.MID, StakeLevel.HIGH)) {
			int[] mix = PokerBotPolicy.gatedMix(s == StakeLevel.MID ? new int[] {10, 55, 35} : new int[] {50, 45, 55}, s, "LOW");
			for (int i = 0; i < 10_000; i++) {
				assertNotEquals(BotDifficulty.EASY, BotDifficulty.MIXED.pick(rng, mix));
			}
		}
		int[] count = new int[3];
		int[] micro = PokerBotPolicy.gatedMix(new int[] {45, 45, 10}, StakeLevel.MICRO, "LOW");
		for (int i = 0; i < 10_000; i++) {
			count[BotDifficulty.MIXED.pick(rng, micro).ordinal()]++;
		}
		assertEquals(0.45, count[0] / 10_000.0, 0.015);
		assertEquals(0.45, count[1] / 10_000.0, 0.015);
		assertEquals(0.10, count[2] / 10_000.0, 0.015);
	}
}
