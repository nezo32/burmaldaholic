package dev.nezo.burmaldaholic.games.roulette.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotProfile;
import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import dev.nezo.burmaldaholic.core.bots.logic.Personality;
import dev.nezo.burmaldaholic.games.roulette.logic.Bets.Bet;
import dev.nezo.burmaldaholic.games.roulette.logic.RouletteBettor.Memory;
import dev.nezo.burmaldaholic.games.roulette.logic.RouletteBettor.View;
import dev.nezo.burmaldaholic.games.roulette.logic.RouletteRound.Transition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Roulette atmosphere bettors (BOTS.md §4.7, §12.1, §12.3); same vectors as Bedrock {@code roulette/logic/bots.test.ts}. */
class RouletteBettorTest {
	private static final long MIN = 10;

	private static BotProfile bot(Personality p) {
		return new BotProfile("b" + p.ordinal(), "creeper42", BotDifficulty.NORMAL, p);
	}

	private static View view(Memory m) {
		return new View(MIN, 250, 500, m);
	}

	private static List<Bet> act(Personality p, Memory m, BotRng rng) {
		return RouletteBettor.INSTANCE.act(bot(p), view(m), null, rng);
	}

	@Test
	void rockBetsOneColourAndFlipsAfterThreeLosses() {
		BotRng rng = BotRng.seeded(1);
		Memory m = RouletteBettor.newMemory(rng, Personality.ROCK, MIN);
		List<Bet> bets = act(Personality.ROCK, m, rng);
		assertEquals(1, bets.size());
		assertEquals(m.color(), bets.getFirst().type());
		assertTrue(bets.getFirst().amount() >= 20 && bets.getFirst().amount() <= 30);
		int losing = m.color() == BetType.RED ? 2 : 1; // 2 is black, 1 is red
		Memory after = m;
		for (int i = 0; i < 2; i++) {
			after = RouletteBettor.afterSpin(Personality.ROCK, after, bets, losing, false);
			assertEquals(m.color(), after.color());
		}
		after = RouletteBettor.afterSpin(Personality.ROCK, after, bets, losing, false);
		assertNotEquals(m.color(), after.color());
		assertEquals(0, after.losses());
	}

	@Test
	void stationBetsTwoDifferentDozens() {
		BotRng rng = BotRng.seeded(2);
		for (int i = 0; i < 500; i++) {
			Memory m = RouletteBettor.newMemory(rng, Personality.STATION, MIN);
			List<Bet> bets = act(Personality.STATION, m, rng);
			assertEquals(2, bets.size());
			assertTrue(bets.stream().allMatch(b -> b.type() == BetType.DOZEN));
			assertNotEquals(bets.get(0).spot().outsideIndex(), bets.get(1).spot().outsideIndex());
		}
	}

	@Test
	void maniacSprinklesFiveToEightInsideChips() {
		BotRng rng = BotRng.seeded(3);
		for (int i = 0; i < 500; i++) {
			Memory m = RouletteBettor.newMemory(rng, Personality.MANIAC, MIN);
			List<Bet> bets = RouletteBettor.INSTANCE.decide(bot(Personality.MANIAC), view(m), null, rng);
			assertTrue(bets.size() >= 5 && bets.size() <= 8);
			assertTrue(bets.stream().allMatch(b -> b.type().inside() && b.amount() >= 60 && b.amount() <= 110));
		}
	}

	@Test
	void tagBacksAFavouriteAndItsWheelNeighbours() {
		BotRng rng = BotRng.seeded(4);
		Memory m = RouletteBettor.newMemory(rng, Personality.TAG, MIN);
		List<Bet> bets = act(Personality.TAG, m, rng);
		int[] nb = RouletteBettor.wheelNeighbours(m.favourite());
		assertEquals(List.of(m.favourite(), nb[0], nb[1]), bets.stream().map(b -> b.spot().numbers().getFirst()).toList());
		assertEquals(List.of(26, 32), List.of(RouletteBettor.wheelNeighbours(0)[0], RouletteBettor.wheelNeighbours(0)[1]));
	}

	@Test
	void lagDoublesAnEvenMoneyBetAfterALossAndResetsPastEight() {
		BotRng rng = BotRng.seeded(5);
		Memory m = RouletteBettor.newMemory(rng, Personality.LAG, MIN);
		assertTrue(m.unit() >= 60 && m.unit() <= 110);
		List<Long> multiples = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			List<Bet> bets = RouletteBettor.INSTANCE.decide(bot(Personality.LAG), view(m), null, rng);
			assertTrue(bets.getFirst().type().evenMoney());
			assertEquals(m.unit() * m.multiple(), bets.getFirst().amount());
			multiples.add(m.multiple());
			m = RouletteBettor.afterSpin(Personality.LAG, m, bets, 0, false); // zero: every even-money bet loses
		}
		assertEquals(List.of(1L, 2L, 4L, 8L, 1L), multiples);
		List<Bet> win = List.of(new Bet(Spot.of(BetType.RED, Spot.outsideNumbers(BetType.RED, 0)), 10));
		assertEquals(1, RouletteBettor.afterSpin(Personality.LAG, new Memory(BetType.RED, 0, 1, 2, 0, BetType.RED, 10, 4), win, 1, false).multiple());
	}

	@Test
	void legalizeKeepsValidSpotsWithinTheLimits() {
		BotRng rng = BotRng.seeded(6);
		SplittableRandom r = new SplittableRandom(6);
		for (int i = 0; i < 10_000; i++) {
			Personality p = Personality.values()[r.nextInt(5)];
			long min = 1 + r.nextInt(50);
			long total = min * (1 + r.nextInt(60));
			long inside = Math.max(min, total / (1 + r.nextInt(4)));
			View v = new View(min, inside, total, RouletteBettor.newMemory(rng, p, min));
			List<Bet> slip = RouletteBettor.INSTANCE.act(bot(p), v, null, rng);
			assertTrue(Bets.totalStaked(slip) <= total);
			for (Bet b : slip) {
				assertTrue(b.spot().isValid());
				assertTrue(b.amount() >= min);
				if (b.type().inside()) {
					assertTrue(b.amount() <= inside);
				}
			}
		}
	}

	/**
	 * 1 000 spins at one table like the table: the human's slip is in the shared round (and would be a
	 * stake); bots keep VIRTUAL slips beside it, decided with their own rng.
	 */
	private record Run(List<Integer> results, List<Long> human, long bank, int calls) {}

	private static Run run(boolean withBots) {
		SplittableRandom game = new SplittableRandom(42);
		int[] calls = {0};
		BotRng botRng = BotRng.seeded(0xB07);
		RouletteRound<UUID> round = new RouletteRound<>(new RouletteRound.Timings(10, 1, 1, 1), 12);
		UUID alex = new UUID(0, 1);
		List<Personality> ps = withBots ? List.of(Personality.values()) : List.of();
		List<Memory> mem = new ArrayList<>();
		List<List<Bet>> slips = new ArrayList<>();
		for (Personality p : ps) {
			mem.add(RouletteBettor.newMemory(botRng, p, MIN));
			slips.add(List.of());
		}
		List<Integer> results = new ArrayList<>();
		List<Long> human = new ArrayList<>();
		long bank = 0;
		long tick = 0;
		while (results.size() < 1000) {
			tick++;
			if (round.canBet() && !round.hasBets()) {
				round.addBets(alex, List.of(new Bet(Spot.of(BetType.RED, Spot.outsideNumbers(BetType.RED, 0)), 10), new Bet(Spot.of(BetType.STRAIGHT, 17), 5)));
				round.setReady(alex, true);
				for (int i = 0; i < ps.size(); i++) {
					slips.set(i, RouletteBettor.INSTANCE.act(bot(ps.get(i)), view(mem.get(i)), null, botRng));
				}
			}
			Transition<UUID> t = round.update(tick, List.of(alex), () -> {
				calls[0]++;
				return game.nextInt(Wheel.POCKETS);
			}, 0);
			if (t instanceof Transition.Result<UUID> res) {
				results.add(res.result());
				assertEquals(List.of(alex), List.copyOf(res.slips().keySet()));
				for (Map.Entry<UUID, List<Bet>> e : res.slips().entrySet()) {
					long ret = Bets.totalReturn(e.getValue(), res.result(), false);
					human.add(ret);
					bank += Bets.totalStaked(e.getValue()) - ret; // the only money that moves
				}
				for (int i = 0; i < ps.size(); i++) {
					mem.set(i, RouletteBettor.afterSpin(ps.get(i), mem.get(i), slips.get(i), res.result(), false)); // virtual only
				}
			}
		}
		return new Run(results, human, bank, calls[0]);
	}

	@Test
	void wheelResultsGameRngDrawsHumanReturnsAndTheBankAreIdenticalWithAndWithoutBots() {
		Run a = run(false);
		Run b = run(true);
		assertEquals(a.results(), b.results());
		assertEquals(a.calls(), b.calls());
		assertEquals(a.human(), b.human());
		assertEquals(a.bank(), b.bank());
	}
}
