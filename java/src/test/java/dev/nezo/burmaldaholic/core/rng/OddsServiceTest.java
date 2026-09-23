package dev.nezo.burmaldaholic.core.rng;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OddsServiceTest {
	private static final OddsContext CTX = new OddsContext(UUID.randomUUID(), "slots", 10);

	@Test
	void modifiersApplyInOrderAndClamp() {
		OddsService service = new OddsService(new SplittableRandom(1));
		service.addModifier("b", 200, (ctx, p) -> p * 10);
		service.addModifier("a", 100, (ctx, p) -> p + 0.05);
		assertEquals(1.0, service.adjusted(CTX, 0.5), 1e-9);
		assertEquals((0.01 + 0.05) * 10, service.adjusted(CTX, 0.01), 1e-9);
	}

	@Test
	void chanceMatchesProbabilityStatistically() {
		OddsService service = new OddsService(new SplittableRandom(42));
		service.addModifier("x", 0, (ctx, p) -> p * 2); // 0.2 -> 0.4
		CasinoRng rng = service.rng(CTX);
		int hits = 0;
		int n = 100_000;
		for (int i = 0; i < n; i++) {
			if (rng.chance(0.2)) {
				hits++;
			}
		}
		assertEquals(0.4, hits / (double) n, 0.01);
	}

	@Test
	void weightedRescalesFavourableGroup() {
		OddsService service = new OddsService(new SplittableRandom(7));
		service.addModifier("x", 0, (ctx, p) -> 0.5); // favourable share forced to 50%
		CasinoRng rng = service.rng(CTX);
		List<CasinoRng.Weighted<String>> outcomes = List.of(
			new CasinoRng.Weighted<>("lose", 90), new CasinoRng.Weighted<>("win", 9), new CasinoRng.Weighted<>("jackpot", 1));
		int fav = 0;
		int n = 100_000;
		for (int i = 0; i < n; i++) {
			String r = rng.weighted(outcomes, s -> !s.equals("lose"));
			if (!r.equals("lose")) {
				fav++;
			}
		}
		assertEquals(0.5, fav / (double) n, 0.01);
	}

	@Test
	void shuffleIsPermutation() {
		CasinoRng rng = new OddsService(new SplittableRandom(3)).rng(CTX);
		List<Integer> deck = new ArrayList<>();
		for (int i = 0; i < 52; i++) {
			deck.add(i);
		}
		rng.shuffle(deck);
		assertEquals(52, deck.stream().distinct().count());
		assertTrue(deck.stream().allMatch(i -> i >= 0 && i < 52));
	}
}
