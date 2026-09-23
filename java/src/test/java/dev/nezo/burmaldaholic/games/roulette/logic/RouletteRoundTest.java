package dev.nezo.burmaldaholic.games.roulette.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.games.roulette.logic.Bets.Bet;
import dev.nezo.burmaldaholic.games.roulette.logic.RouletteRound.Phase;
import dev.nezo.burmaldaholic.games.roulette.logic.RouletteRound.Timings;
import dev.nezo.burmaldaholic.games.roulette.logic.RouletteRound.Transition;
import java.util.List;
import org.junit.jupiter.api.Test;

class RouletteRoundTest {
	private static final Timings T = new Timings(500, 20, 100, 60);
	private static final Bet RED10 = new Bet(Spot.all(BetType.RED).get(0), 10);
	private static final Bet S17 = new Bet(Spot.of(BetType.STRAIGHT, 17), 5);

	@Test
	void singlePlayerSpinsWhenReady() {
		RouletteRound<String> round = new RouletteRound<>(T, 12);
		assertNull(round.update(0, List.of("a"), () -> 17, 0));
		round.addBets("a", List.of(RED10, S17));
		assertNull(round.update(1, List.of("a"), () -> 17, 0));
		assertEquals(-1, round.endsAt(), "no bet timer with one player");
		round.setReady("a", true);
		assertInstanceOf(Transition.NoMoreBets.class, round.update(2, List.of("a"), () -> 17, 0));
		assertFalse(round.canBet());
		assertThrows(IllegalStateException.class, () -> round.addBets("a", List.of(RED10)));
		assertNull(round.update(21, List.of("a"), () -> 17, 0));
		Transition<String> spin = round.update(22, List.of("a"), () -> 17, 0);
		assertEquals(new Transition.Spin<String>(17), spin);
		assertEquals(Phase.SPIN, round.phase());
		assertNull(round.update(121, List.of("a"), () -> 3, 0));
		Transition.Result<String> result = (Transition.Result<String>) round.update(122, List.of("a"), () -> 3, 0);
		assertEquals(17, result.result());
		assertEquals(List.of(RED10, S17), result.slips().get("a"));
		assertEquals(List.of(17), round.history());
		assertFalse(round.hasBets());
		assertInstanceOf(Transition.Reset.class, round.update(182, List.of("a"), () -> 3, 0));
		assertTrue(round.canBet());
	}

	@Test
	void multiplayerBetTimerStartsAtFirstBet() {
		RouletteRound<String> round = new RouletteRound<>(T, 12);
		List<String> seated = List.of("a", "b");
		assertNull(round.update(10, seated, () -> 0, 0));
		round.addBets("a", List.of(RED10));
		assertNull(round.update(10, seated, () -> 0, 0));
		assertEquals(510, round.endsAt());
		round.setReady("a", true);
		assertInstanceOf(Transition.NoMoreBets.class, round.update(11, seated, () -> 0, 0), "b has no bets and does not block");
	}

	@Test
	void timerClosesBettingEvenIfNotReady() {
		RouletteRound<String> round = new RouletteRound<>(T, 12);
		List<String> seated = List.of("a", "b");
		round.addBets("a", List.of(RED10));
		round.addBets("b", List.of(S17));
		round.update(0, seated, () -> 0, 0);
		round.setReady("a", true);
		assertNull(round.update(499, seated, () -> 0, 0));
		assertEquals(1, round.readyCount());
		assertInstanceOf(Transition.NoMoreBets.class, round.update(500, seated, () -> 0, 0));
	}

	@Test
	void addingBetsClearsReady() {
		RouletteRound<String> round = new RouletteRound<>(T, 12);
		round.addBets("a", List.of(RED10));
		round.setReady("a", true);
		round.addBets("a", List.of(RED10));
		assertFalse(round.isReady("a"));
		assertEquals(20, round.total("a"));
		assertEquals(1, round.bets("a").size());
	}

	@Test
	void departedBettorCountsAsReady() {
		RouletteRound<String> round = new RouletteRound<>(T, 12);
		round.addBets("a", List.of(RED10));
		assertInstanceOf(Transition.NoMoreBets.class, round.update(0, List.of(), () -> 0, 0), "spin proceeds after disconnect");
	}

	@Test
	void highRollerMinimumDropsSmallSlips() {
		RouletteRound<String> round = new RouletteRound<>(T, 12);
		round.addBets("a", List.of(RED10));
		round.addBets("b", List.of(new Bet(RED10.spot(), 100)));
		round.setReady("a", true);
		round.setReady("b", true);
		Transition<String> t = round.update(0, List.of("a", "b"), () -> 0, 100);
		assertEquals(new Transition.NoMoreBets<>(List.of("a")), t);
		assertEquals(List.of("b"), List.copyOf(round.bettors()));

		RouletteRound<String> lonely = new RouletteRound<>(T, 12);
		lonely.addBets("a", List.of(RED10));
		lonely.setReady("a", true);
		assertEquals(new Transition.Abandoned<>(List.of("a")), lonely.update(0, List.of("a"), () -> 0, 100));
		assertTrue(lonely.canBet());
		assertFalse(lonely.hasBets());
	}

	@Test
	void historyIsCappedAndNewestFirst() {
		RouletteRound<String> round = new RouletteRound<>(T, 3);
		long now = 0;
		for (int i = 1; i <= 5; i++) {
			int result = i;
			round.addBets("a", List.of(RED10));
			round.setReady("a", true);
			for (int k = 0; k < 4; k++) {
				round.update(now, List.of("a"), () -> result, 0);
				now += 200;
			}
		}
		assertEquals(List.of(5, 4, 3), round.history());
		round.configure(T, 1);
		assertEquals(List.of(5), round.history());
		RouletteRound<String> none = new RouletteRound<>(T, 0);
		none.setHistory(List.of(1, 2));
		assertEquals(List.of(), none.history());
	}

	@Test
	void clearAndAbort() {
		RouletteRound<String> round = new RouletteRound<>(T, 12);
		round.addBets("a", List.of(RED10));
		round.addBets("b", List.of(S17));
		assertEquals(List.of(RED10), round.clear("a"));
		assertEquals(List.of(), round.clear("a"));
		round.setReady("b", true);
		round.update(0, List.of("b"), () -> 0, 0);
		assertEquals(List.of(S17), round.abort().get("b"));
		assertTrue(round.canBet());
	}

	@Test
	void rejectsInvalidDraw() {
		RouletteRound<String> round = new RouletteRound<>(T, 12);
		round.addBets("a", List.of(RED10));
		round.setReady("a", true);
		round.update(0, List.of("a"), () -> 37, 0);
		assertThrows(IllegalStateException.class, () -> round.update(100, List.of("a"), () -> 37, 0));
	}
}
