package dev.nezo.burmaldaholic.games.baccarat.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotProfile;
import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import dev.nezo.burmaldaholic.core.bots.logic.Personality;
import dev.nezo.burmaldaholic.games.baccarat.logic.BaccaratRules.Side;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** BOTS.md §4.7: Punto Banco atmosphere bettors by personality (same vectors as Bedrock). */
class BaccaratBettorTest {
	static final Slips.Limits L = Slips.Limits.of(5, 1000, 0.1, 20, 0, true);

	static EnumMap<BetKind, Long> bet(Personality p, BaccaratBettor.View v, long seed) {
		return BaccaratBettor.INSTANCE.act(new BotProfile("b0000001", "creeper42", BotDifficulty.NORMAL, p), v, null, BotRng.seeded(seed));
	}

	static BaccaratBettor.View view(Side last, long coupNo) {
		return new BaccaratBettor.View(L, last, coupNo);
	}

	@Test
	void rockAndTagBetBankerOnly() {
		for (int s = 1; s < 50; s++) {
			EnumMap<BetKind, Long> rock = bet(Personality.ROCK, view(null, 1), s);
			assertEquals(Set.of(BetKind.BANKER), rock.keySet());
			assertEquals(0, rock.get(BetKind.BANKER) % L.step());
			assertTrue(rock.get(BetKind.BANKER) >= 40 && rock.get(BetKind.BANKER) <= 60, "min 20 + 1–2 × 20");
			EnumMap<BetKind, Long> tag = bet(Personality.TAG, view(null, 1), s);
			assertEquals(Set.of(BetKind.BANKER), tag.keySet());
			assertTrue(tag.get(BetKind.BANKER) >= 60 && tag.get(BetKind.BANKER) <= 120);
		}
	}

	@Test
	void stationFollowsLagChases() {
		assertEquals(Side.BANKER, BaccaratBettor.lastSide(List.of(0, 1, 2)), "ties skipped (bead = winner ordinal | pair flags)");
		assertEquals(Side.PLAYER, BaccaratBettor.lastSide(List.of(0 | 4, 2 | 8)));
		assertNull(BaccaratBettor.lastSide(List.of(2)));
		assertEquals(Set.of(BetKind.PLAYER), bet(Personality.STATION, view(Side.PLAYER, 1), 1).keySet());
		assertEquals(Set.of(BetKind.BANKER), bet(Personality.STATION, view(Side.BANKER, 1), 1).keySet());
		assertEquals(Set.of(BetKind.PLAYER), bet(Personality.LAG, view(Side.BANKER, 1), 1).keySet());
		assertEquals(Set.of(BetKind.BANKER), bet(Personality.LAG, view(Side.PLAYER, 1), 1).keySet());
	}

	@Test
	void maniacHuntsTiesAndPairs() {
		EnumMap<BetKind, Long> a = bet(Personality.MANIAC, view(null, 4), 1);
		assertTrue(a.get(BetKind.TIE) > 0);
		assertFalse(a.containsKey(BetKind.PLAYER_PAIR));
		EnumMap<BetKind, Long> b = bet(Personality.MANIAC, view(null, 6), 1);
		assertTrue(b.get(BetKind.PLAYER_PAIR) > 0 && b.get(BetKind.BANKER_PAIR) > 0);
		for (EnumMap<BetKind, Long> s : List.of(a, b)) {
			for (BetKind k : List.of(BetKind.TIE, BetKind.PLAYER_PAIR, BetKind.BANKER_PAIR)) {
				assertTrue(s.getOrDefault(k, 0L) <= L.sideMax());
			}
		}
		Slips.Limits noPairs = Slips.Limits.of(5, 1000, 0.1, 20, 0, false);
		assertFalse(bet(Personality.MANIAC, new BaccaratBettor.View(noPairs, null, 6), 1).containsKey(BetKind.PLAYER_PAIR));
	}

	@Test
	void virtualAmountsStayWithinTheLimits() {
		Slips.Limits tight = Slips.Limits.of(5, 50, 0.1, 20, 0, true);
		for (Personality p : Personality.values()) {
			for (int s = 1; s < 20; s++) {
				EnumMap<BetKind, Long> slip = bet(p, new BaccaratBettor.View(tight, Side.PLAYER, s), s);
				assertTrue(Slips.total(slip) <= 50);
				assertTrue(tight.checkSlip(slip).isEmpty() || slip.isEmpty(), p + " " + slip);
			}
		}
	}
}
