package dev.nezo.burmaldaholic.core.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.advancement.CasinoAdvancements;
import dev.nezo.burmaldaholic.core.events.CasinoEvents.PlayResult;
import dev.nezo.burmaldaholic.core.wager.HouseEdges;
import dev.nezo.burmaldaholic.core.wager.Stake;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Enriched PlayResult (edge, stake kind, tags, PvP, offline queue persistence) and the core advancement rules. */
class PlayResultTest {
	@Test
	void defaultsAreAHouseBankedChipRoundWithTheGameEdge() {
		PlayResult r = PlayResult.of("roulette", 100, 0);
		assertTrue(r.houseBanked());
		assertFalse(r.pawn());
		assertFalse(r.ownedCasino());
		assertEquals(HouseEdges.ROULETTE, r.houseEdge(), 1e-12);
		assertEquals(2.7, r.theoreticalLoss(), 1e-9);
		PlayResult pvp = PlayResult.of("poker", 100, 300).pvp();
		assertFalse(pvp.houseBanked());
		assertEquals(0, pvp.theoreticalLoss());
	}

	@Test
	void tagsEdgeAndKind() {
		PlayResult r = PlayResult.of("craps", 40, 80).withEdge(0.0).withTags("pass", "", "odds", "pass").withKind(Stake.Kind.ITEM);
		assertEquals(List.of("pass", "odds"), r.tags());
		assertTrue(r.hasTag("odds"));
		assertTrue(r.pawn());
		assertEquals(0, r.theoreticalLoss(), "craps odds carry 0 %");
	}

	@Test
	void offlineQueueRoundTrip() {
		PlayResult r = PlayResult.of("slots", 15, 60).withEdge(HouseEdges.SLOTS_GOLD).withTags("gold").withGoldenHour(true).withKind(Stake.Kind.CHIPS);
		PlayResult back = PlayResult.load(r.save());
		assertEquals(r, back, "persisted fields survive (deferred is set on delivery)");
		assertTrue(back.asDeferred().deferred());
	}

	@Test
	void coreAdvancementsOfARound() {
		assertEquals(List.of(), CasinoAdvancements.wagerAdvancements(PlayResult.of("slots", 0, 0), 0));
		assertEquals(List.of("first_bet"), CasinoAdvancements.wagerAdvancements(PlayResult.of("slots", 5, 0), -3));
		assertEquals(List.of("first_bet", "beginners_luck", "heart_on_the_line"),
			CasinoAdvancements.wagerAdvancements(PlayResult.of("coin_flip", 50, 98).withKind(Stake.Kind.HEARTS), 1));
		assertEquals(List.of("first_bet", "beginners_luck", "devils_deal", "golden_hour", "on_fire"),
			CasinoAdvancements.wagerAdvancements(PlayResult.of("coin_flip", 50, 100).withKind(Stake.Kind.SOUL).withGoldenHour(true), 10));
		assertEquals(List.of("first_bet", "beginners_luck"),
			CasinoAdvancements.wagerAdvancements(PlayResult.of("poker", 50, 100).pvp().withGoldenHour(true), 0), "PvP: no golden_hour");
		assertEquals(List.of("first_bet", "black_cat"), CasinoAdvancements.wagerAdvancements(PlayResult.of("slots", 5, 0), -10));
		assertEquals(45, CasinoAdvancements.IDS.size());
	}
}
