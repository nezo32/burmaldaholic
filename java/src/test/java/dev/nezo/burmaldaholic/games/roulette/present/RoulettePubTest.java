package dev.nezo.burmaldaholic.games.roulette.present;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.nezo.burmaldaholic.games.roulette.logic.BetType;
import dev.nezo.burmaldaholic.games.roulette.logic.Spot;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RoulettePubTest {
	private static Map<Spot, Long> bets(Object... kv) {
		Map<Spot, Long> m = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) m.put((Spot) kv[i], ((Number) kv[i + 1]).longValue());
		return m;
	}

	@Test
	void buildsPlayersStacksAndTopWin() {
		Spot red = Spot.of(BetType.RED, Spot.outsideNumbers(BetType.RED, 0));
		Spot s17 = Spot.of(BetType.STRAIGHT, 17);
		RoulettePub p = RoulettePub.build(List.of(new RoulettePub.Slip("Steve", 0, false, bets(red, 20, s17, 5), true, 155),
			new RoulettePub.Slip("Alex", 2, false, bets(red, 30), true, -30), new RoulettePub.Slip("[BOT] Creeper42_with_a_long_name", -1, true, bets(s17, 1), true, 35)), List.of(17, 0, 32, 8, 19, 26, 3, 11, 5));
		assertEquals(3, p.players().size());
		assertEquals(25, p.players().get(0).total());
		assertEquals(2, p.players().get(0).spots());
		assertEquals(16, p.players().get(2).name().length());
		assertEquals("Steve", p.topName());
		assertEquals(155, p.topNet());
		assertEquals(List.of(17, 0, 32, 8, 19, 26, 3, 11), p.history());
		assertEquals(2, p.stacks().size());
		assertEquals(red, p.stacks().get(0).spotValue());
		assertEquals(50, p.stacks().get(0).amount());
		assertEquals(1, p.stacks().get(0).owner()); // Alex owns most of red
		assertEquals(6, p.stacks().get(1).amount());
		RoulettePub back = RoulettePub.decode(p.encode(), p.names());
		assertEquals(p, back);
	}

	@Test
	void capsStacksAndRejectsGarbage() {
		Map<Spot, Long> m = new LinkedHashMap<>();
		for (int n = 0; n <= 36; n++) m.put(Spot.of(BetType.STRAIGHT, n), (long) n + 1);
		RoulettePub p = RoulettePub.build(List.of(new RoulettePub.Slip("A", 0, false, m, false, 0)), List.of());
		assertEquals(RoulettePub.MAX_STACKS, p.stacks().size());
		assertEquals(37, p.stacks().get(0).amount());
		assertEquals(p, RoulettePub.decode(p.encode(), p.names()));
		assertEquals(RoulettePub.EMPTY, RoulettePub.decode(RoulettePub.EMPTY.encode(), RoulettePub.EMPTY.names()));
		assertThrows(IllegalArgumentException.class, () -> RoulettePub.decode(new long[] {9, 0, 0, 0, 0}, List.of("")));
	}
}
