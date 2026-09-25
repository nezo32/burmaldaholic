package dev.nezo.burmaldaholic.games.baccarat.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The baccarat reveal never lets a card's schedule depend on cards that are not public yet (cards.md §0.7.3). */
class BaccaratRevealTest {
	@Test
	void theFirstFourCardsAreScheduledAlikeForEveryCoup() {
		for (int cap : new int[] {20, 60, 90, 120, 160, 300}) {
			BaccaratReveal.Config cfg = BaccaratReveal.Config.DEFAULT.withCap(cap);
			BaccaratReveal ref = BaccaratReveal.build(2, 2, true, cfg);
			int[][] coups = {{2, 2, 0}, {3, 2, 0}, {2, 3, 0}, {3, 3, 0}};
			for (int[] c : coups) {
				BaccaratReveal r = BaccaratReveal.build(c[0], c[1], false, cfg);
				for (int side = 0; side < 2; side++) {
					for (int i = 0; i < 2; i++) {
						assertEquals(ref.dealAt(side, i), r.dealAt(side, i), "deal cap " + cap);
						assertEquals(ref.revealStep(side, i), r.revealStep(side, i), "reveal cap " + cap);
					}
				}
				assertEquals(ref.announce(0), r.announce(0));
			}
		}
	}

	@Test
	void everySqueezeHasTheSameWindowWithinACoup() {
		BaccaratReveal.Config cfg = BaccaratReveal.Config.DEFAULT.withCap(90);
		BaccaratReveal r = BaccaratReveal.build(3, 3, false, cfg);
		int len = -1;
		for (BaccaratReveal.Step st : r.steps()) {
			if (st.kind() != BaccaratReveal.Kind.SQUEEZE) continue;
			if (len < 0) len = st.dur();
			assertEquals(len, st.dur());
		}
		assertTrue(len >= 10);
	}
}
