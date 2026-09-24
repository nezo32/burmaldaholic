package dev.nezo.burmaldaholic.pvp.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Pure rules of the pvp module: reveal order / places, taunt ids, formatting, the five PvP advancements. */
class PvpUiLogicTest {
	// ---- RevealOrder (PVP.md §3.11.4) ----------------------------------------------------------

	@Test
	void placesFollowRankOrderWithSharedLoserPlaces() {
		// 4 players: #2 best, then #0, then #1 and #3 tied on points
		int[] order = {2, 0, 1, 3};
		long[] points = {50, 10, 90, 10};
		assertArrayEquals(new int[] {2, 3, 1, 3}, RevealOrder.places(order, points, new int[] {2}));
	}

	@Test
	void everyPotSharerIsFirst() {
		int[] order = {1, 0, 2};
		long[] points = {40, 40, 5};
		assertArrayEquals(new int[] {1, 1, 3}, RevealOrder.places(order, points, new int[] {1, 0}));
	}

	@Test
	void tieBrokenByModeKeepsSeparatePlacesForTheWinner() {
		// same points, but the mode's tie-break gave the pot to #0 alone
		int[] order = {0, 1};
		long[] points = {7, 7};
		assertArrayEquals(new int[] {1, 2}, RevealOrder.places(order, points, new int[] {0}));
	}

	@Test
	void revealGoesFromLastToFirst() {
		assertArrayEquals(new int[] {3, 1, 0, 2}, RevealOrder.sequence(new int[] {2, 0, 1, 3}));
		assertEquals(1, RevealOrder.visibleRows(4, 0, 10));
		assertEquals(2, RevealOrder.visibleRows(4, 10, 10));
		assertEquals(4, RevealOrder.visibleRows(4, 1000, 10));
		assertEquals(4, RevealOrder.visibleRows(4, 0, 0));
	}

	@Test
	void bellRisesFromPointEightToOnePointSix() {
		assertEquals(0.8f, RevealOrder.bellPitch(0, 5), 1e-6);
		assertEquals(1.6f, RevealOrder.bellPitch(4, 5), 1e-6);
		assertTrue(RevealOrder.bellPitch(2, 5) > RevealOrder.bellPitch(1, 5));
		assertEquals(1.2f, RevealOrder.bellPitch(0, 1), 1e-6);
	}

	// ---- Taunts, text ----------------------------------------------------------------------------

	@Test
	void eightTauntsParsedByIdOrNumber() {
		assertEquals(8, Taunts.IDS.size());
		assertEquals(0, Taunts.parse("gg"));
		assertEquals(7, Taunts.parse("RESPECT"));
		assertEquals(2, Taunts.parse("3"));
		assertEquals(-1, Taunts.parse("9"));
		assertEquals(-1, Taunts.parse("hello"));
		assertEquals("gui.burmaldaholic.pvp.taunt.rigged", Taunts.key(3));
	}

	@Test
	void percentFromBasisPoints() {
		assertEquals("3", PvpText.percent(300));
		assertEquals("2.5", PvpText.percent(250));
		assertEquals("0.25", PvpText.percent(25));
		assertEquals("0.05", PvpText.percent(5));
		assertEquals("0", PvpText.percent(0));
		assertEquals("10", PvpText.percent(1000));
	}

	@Test
	void countdownSecondsRoundUp() {
		assertEquals(0, PvpText.seconds(0));
		assertEquals(1, PvpText.seconds(1));
		assertEquals(1, PvpText.seconds(20));
		assertEquals(2, PvpText.seconds(21));
		assertEquals(90, PvpText.seconds(1800));
		assertEquals(58, PvpText.ticksLeft(1000, 1800, 1000 + 1800 - 58) );
		assertEquals(0, PvpText.ticksLeft(0, 10, 50));
	}

	// ---- advancements (PVP.md §11) ---------------------------------------------------------------

	private static PvpAchievementRules.Facts win(int humans) {
		return new PvpAchievementRules.Facts(true, false, humans, false, false, PvpAchievementRules.Streak.NONE);
	}

	@Test
	void firstWinAlsoAgainstBots() {
		assertEquals(List.of("pvp_first_win"), PvpAchievementRules.earned(win(0)));
		assertEquals(List.of(), PvpAchievementRules.earned(new PvpAchievementRules.Facts(false, true, 3, false, false, PvpAchievementRules.Streak.NONE)));
	}

	@Test
	void allInNeedsTheWin() {
		assertTrue(PvpAchievementRules.earned(new PvpAchievementRules.Facts(true, true, 0, false, false, null)).contains("pvp_all_in"));
		assertFalse(PvpAchievementRules.earned(new PvpAchievementRules.Facts(false, true, 1, false, false, null)).contains("pvp_all_in"));
	}

	@Test
	void fullHouseNeedsFiveHumanOpponents() {
		assertFalse(PvpAchievementRules.earned(win(4)).contains("pvp_full_house"));
		assertTrue(PvpAchievementRules.earned(win(5)).contains("pvp_full_house"));
	}

	@Test
	void revengeOnlyForTheUnderdogOfAGrudgeMatch() {
		assertTrue(PvpAchievementRules.earned(new PvpAchievementRules.Facts(true, false, 1, true, true, null)).contains("pvp_revenge"));
		assertFalse(PvpAchievementRules.earned(new PvpAchievementRules.Facts(true, false, 1, true, false, null)).contains("pvp_revenge"));
		assertFalse(PvpAchievementRules.earned(new PvpAchievementRules.Facts(true, false, 1, false, true, null)).contains("pvp_revenge"));
		assertFalse(PvpAchievementRules.earned(new PvpAchievementRules.Facts(false, false, 1, true, true, null)).contains("pvp_revenge"));
	}

	@Test
	void underdogDetectedBeforeOrAfterTheRecordUpdate() {
		assertTrue(PvpAchievementRules.underdogWon(-3, 3)); // record not updated yet: the losing run
		assertTrue(PvpAchievementRules.underdogWon(-5, 3));
		assertTrue(PvpAchievementRules.underdogWon(1, 3)); // updated: the run just flipped to +1
		assertFalse(PvpAchievementRules.underdogWon(3, 3)); // the favourite won (not updated)
		assertFalse(PvpAchievementRules.underdogWon(4, 3)); // the favourite won (updated)
		assertFalse(PvpAchievementRules.underdogWon(-2, 3));
	}

	@Test
	void rampageNeedsFiveWinsAndTwoDistinctHumans() {
		PvpAchievementRules.Streak s = PvpAchievementRules.Streak.NONE;
		for (int i = 0; i < 5; i++) {
			s = s.after(true, Set.of("alex"));
		}
		assertEquals(5, s.wins());
		assertFalse(s.rampage(), "one opponent only (alt farming)");
		s = s.after(true, Set.of("bob"));
		assertTrue(s.rampage());
		assertTrue(PvpAchievementRules.earned(new PvpAchievementRules.Facts(true, false, 1, false, false, s)).contains("pvp_rampage"));
	}

	@Test
	void botOnlyMatchesNeitherExtendNorBreakTheStreak() {
		PvpAchievementRules.Streak s = PvpAchievementRules.Streak.NONE.after(true, Set.of("alex")).after(true, Set.of("bob"));
		assertEquals(s, s.after(false, Set.of()));
		assertEquals(s, s.after(true, Set.of()));
		assertEquals(PvpAchievementRules.Streak.NONE, s.after(false, Set.of("carl")));
	}

	@Test
	void mixedMatchCountsOnlyHumans() {
		PvpAchievementRules.Streak s = PvpAchievementRules.Streak.NONE;
		for (int i = 0; i < 5; i++) {
			s = s.after(true, Set.of("alex")); // bots in the same matches are not passed as opponents
		}
		assertEquals(Set.of("alex"), s.opponents());
		assertFalse(s.rampage());
	}
}
