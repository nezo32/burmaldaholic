package dev.nezo.burmaldaholic.games.craps.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotProfile;
import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import dev.nezo.burmaldaholic.core.bots.logic.Personality;
import dev.nezo.burmaldaholic.core.bots.logic.VirtualSeats;
import dev.nezo.burmaldaholic.games.craps.logic.CrapsBettor.Action;
import dev.nezo.burmaldaholic.games.craps.logic.ShooterRotation.SeatInfo;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Craps atmosphere bettors (BOTS.md §3.1, §4.7, §12.1, §12.3); same vectors as Bedrock {@code craps/logic/bots.test.ts}. */
class CrapsBettorTest {
	private static final CrapsRules RULES = CrapsRules.DEFAULT;

	private static BotProfile bot(Personality p) {
		return new BotProfile("b" + p.ordinal(), "creeper42", BotDifficulty.NORMAL, p);
	}

	private static UUID owner(Personality p) {
		return VirtualSeats.botUuid(bot(p).key());
	}

	/** One bot at the shadow table: bet, then roll the given dice (the real table's roll). */
	private static void bet(CrapsBotTable t, Personality p, int point, BotRng rng) {
		t.sync(point, RULES);
		t.apply(owner(p), CrapsBettor.INSTANCE.act(bot(p), t.view(owner(p), 10), null, rng));
	}

	private static long count(CrapsBotTable t, Personality p, BetKind kind) {
		return t.shadow().betsOf(owner(p)).stream().filter(b -> b.kind() == kind).count();
	}

	@Test
	void rockPlaysTheDarkSideWithLayOdds() {
		BotRng rng = BotRng.seeded(1);
		CrapsBotTable t = new CrapsBotTable(RULES);
		bet(t, Personality.ROCK, 0, rng);
		assertEquals(1, count(t, Personality.ROCK, BetKind.DONT_PASS));
		long flat = t.shadow().betsOf(owner(Personality.ROCK)).getFirst().flat();
		assertTrue(flat >= 20 && flat <= 30);
		t.roll(0, 2, 2); // point 4
		bet(t, Personality.ROCK, 4, rng);
		Bet dp = t.shadow().betsOf(owner(Personality.ROCK)).getFirst();
		long max = CrapsMath.maxOdds(OddsSide.LAY, 4, dp.flat(), RULES);
		int unit = CrapsMath.oddsUnit(OddsSide.LAY, 4);
		assertTrue(dp.odds() > 0 && dp.odds() <= max && max - dp.odds() < unit, dp.odds() + " of " + max); // full lay odds
		bet(t, Personality.ROCK, 4, rng); // nothing more to add
		assertEquals(1, t.shadow().betsOf(owner(Personality.ROCK)).size());
	}

	@Test
	void stationPlaysTheFieldEveryRoll() {
		BotRng rng = BotRng.seeded(2);
		CrapsBotTable t = new CrapsBotTable(RULES);
		int point = 0;
		for (int i = 0; i < 20; i++) {
			bet(t, Personality.STATION, point, rng);
			assertEquals(1, count(t, Personality.STATION, BetKind.FIELD));
			point = t.roll(point, 1 + i % 6, 1 + (i * 5) % 6).result().event().nextPoint();
		}
	}

	@Test
	void maniacClimbsTheComeLadderWithOdds() {
		BotRng rng = BotRng.seeded(3);
		CrapsBotTable t = new CrapsBotTable(RULES);
		bet(t, Personality.MANIAC, 0, rng);
		assertEquals(1, count(t, Personality.MANIAC, BetKind.PASS));
		t.roll(0, 3, 3); // point 6
		bet(t, Personality.MANIAC, 6, rng);
		assertEquals(1, count(t, Personality.MANIAC, BetKind.COME));
		assertTrue(t.shadow().betsOf(owner(Personality.MANIAC)).stream().anyMatch(b -> b.kind() == BetKind.PASS && b.odds() > 0));
		t.roll(6, 4, 4); // the come bet travels to 8
		bet(t, Personality.MANIAC, 6, rng);
		assertEquals(2, count(t, Personality.MANIAC, BetKind.COME)); // a new Come each roll
		assertTrue(t.shadow().betsOf(owner(Personality.MANIAC)).stream().anyMatch(b -> b.kind() == BetKind.COME && b.point() == 8 && b.odds() > 0));
	}

	@Test
	void tagTakesFullOddsAndLagAddsTheField() {
		BotRng rng = BotRng.seeded(4);
		CrapsBotTable t = new CrapsBotTable(RULES);
		bet(t, Personality.TAG, 0, rng);
		bet(t, Personality.LAG, 0, rng);
		assertEquals(1, count(t, Personality.LAG, BetKind.FIELD));
		t.roll(0, 2, 3); // point 5 (the Field loses)
		bet(t, Personality.TAG, 5, rng);
		bet(t, Personality.LAG, 5, rng);
		Bet pass = t.shadow().betsOf(owner(Personality.TAG)).getFirst();
		assertEquals(CrapsMath.maxOdds(OddsSide.TAKE, 5, pass.flat(), RULES), pass.odds()); // 4× on 5
		assertTrue(pass.flat() >= 30 && pass.flat() <= 60);
		assertEquals(1, count(t, Personality.LAG, BetKind.FIELD));
		assertTrue(t.shadow().betsOf(owner(Personality.LAG)).stream().anyMatch(b -> b.kind() == BetKind.PASS && b.odds() > 0));
	}

	@Test
	void legalizeDropsLineBetsWithAPointComeOnTheComeOutAndOverMaxOdds() {
		CrapsBotTable t = new CrapsBotTable(RULES);
		UUID o = owner(Personality.TAG);
		t.sync(0, RULES);
		t.apply(o, List.of(new Action.Flat(BetKind.PASS, 10)));
		int passId = t.shadow().betsOf(o).getFirst().id();
		CrapsBettor.View comeOut = t.view(o, 10);
		assertEquals(List.of(), CrapsBettor.INSTANCE.legalize(comeOut, List.of(new Action.Flat(BetKind.COME, 10), new Action.Flat(BetKind.PASS, 10))));
		t.roll(0, 4, 6); // point 10
		CrapsBettor.View pointOn = t.view(o, 10);
		List<Action> legal = CrapsBettor.INSTANCE.legalize(pointOn, List.of(new Action.Flat(BetKind.DONT_PASS, 10), new Action.Flat(BetKind.COME, 5),
			new Action.Flat(BetKind.COME, 10), new Action.Odds(passId, 1_000)));
		assertEquals(List.of(new Action.Flat(BetKind.COME, 10), new Action.Odds(passId, 30)), legal); // min 10; 3× on 10
		SplittableRandom r = new SplittableRandom(9);
		BotRng rng = BotRng.seeded(9);
		for (int i = 0; i < 5000; i++) {
			Personality p = Personality.values()[r.nextInt(5)];
			int pt = r.nextInt(3) == 0 ? 0 : CrapsMath.POINT_NUMBERS[r.nextInt(6)];
			t.sync(pt, RULES);
			CrapsBettor.View v = t.view(owner(p), 10);
			for (Action a : CrapsBettor.INSTANCE.act(bot(p), v, null, rng)) {
				if (a instanceof Action.Flat f) {
					assertTrue(f.amount() >= 10);
					assertTrue(!f.kind().isLine() || pt == 0);
					assertTrue(!f.kind().isCome() || pt != 0);
				}
			}
		}
	}

	/** 1 000 rolls: two humans at the real table, five bots on the shadow table. */
	private record Run(List<String> dice, List<UUID> shooters, List<String> human, long bank, int calls) {}

	private static Run run(boolean withBots) {
		SplittableRandom game = new SplittableRandom(42);
		int[] calls = {0};
		BotRng botRng = BotRng.seeded(0xB07);
		CrapsTable table = new CrapsTable(RULES);
		CrapsBotTable bots = new CrapsBotTable(RULES);
		UUID alex = new UUID(0, 1);
		UUID bob = new UUID(0, 2);
		List<String> dice = new ArrayList<>();
		List<UUID> shooters = new ArrayList<>();
		List<String> human = new ArrayList<>();
		long bank = 0;
		for (int i = 0; i < 1000; i++) {
			for (UUID h : List.of(alex, bob)) {
				if (table.placeError(h, BetKind.PASS) == null) {
					table.addBet(h, BetKind.PASS, 10);
					bank += 10;
				}
			}
			List<SeatInfo> seats = List.of(new SeatInfo(alex, 0, table.hasLineBet(alex)), new SeatInfo(bob, 2, table.hasLineBet(bob)));
			table.ensureShooter(seats, true);
			if (withBots) {
				bots.sync(table.point(), table.rules());
				for (Personality p : Personality.values()) {
					bots.apply(owner(p), CrapsBettor.INSTANCE.act(bot(p), bots.view(owner(p), 10), null, botRng));
				}
			}
			int before = table.point();
			shooters.add(table.shooter());
			int[] d = CrapsResolver.rollDice(bound -> {
				calls[0]++;
				return game.nextInt(bound);
			});
			CrapsTable.TableRoll r = table.roll(d[0], d[1], seats);
			dice.add(d[0] + "," + d[1]);
			if (withBots) {
				List<Bet> shadowBefore = bots.shadow().bets();
				CrapsTable.TableRoll shadow = bots.roll(before, d[0], d[1]);
				// the shadow resolves exactly like the real rules with the same dice
				assertEquals(CrapsResolver.apply(before, shadowBefore, d[0], d[1], RULES).resolutions().stream().map(x -> x.outcome() + ":" + x.totalReturn()).toList(),
					shadow.result().resolutions().stream().map(x -> x.outcome() + ":" + x.totalReturn()).toList());
				assertEquals(table.point(), bots.shadow().point());
				assertEquals(null, shadow.shooter()); // the shadow table never has a shooter
			}
			for (BetResolution x : r.result().resolutions()) {
				if (!x.outcome().resolved()) {
					continue;
				}
				human.add(x.bet().owner() + ":" + x.outcome() + ":" + x.totalReturn());
				bank -= x.totalReturn();
			}
		}
		return new Run(dice, shooters, human, bank, calls[0]);
	}

	@Test
	void diceShootersGameRngDrawsHumanResultsAndTheBankAreIdenticalWithAndWithoutBots() {
		Run a = run(false);
		Run b = run(true);
		assertEquals(a.dice(), b.dice());
		assertEquals(a.calls(), b.calls());
		assertEquals(a.shooters(), b.shooters());
		assertEquals(a.human(), b.human());
		assertEquals(a.bank(), b.bank());
		Set<UUID> shooters = new HashSet<>(b.shooters());
		assertEquals(Set.of(new UUID(0, 1), new UUID(0, 2)), shooters); // a bot never holds the dice
	}

	@Test
	void kRanges() {
		assertEquals(Map.of("ROCK", "1-2", "STATION", "1-2", "MANIAC", "5-10", "TAG", "2-5", "LAG", "5-10"),
			Map.of("ROCK", k(Personality.ROCK), "STATION", k(Personality.STATION), "MANIAC", k(Personality.MANIAC), "TAG", k(Personality.TAG),
				"LAG", k(Personality.LAG)));
	}

	private static String k(Personality p) {
		int[] k = CrapsBettor.kRange(p);
		return k[0] + "-" + k[1];
	}
}
