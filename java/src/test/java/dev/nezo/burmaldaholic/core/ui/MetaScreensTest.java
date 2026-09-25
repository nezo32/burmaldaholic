package dev.nezo.burmaldaholic.core.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.chips.ChipMath;
import org.junit.jupiter.api.Test;

/** Lane J-L2 review fixes: the cashier's counting tray, the HUD placement, the loan's readable due time and meter. */
class MetaScreensTest {
	// ---- counting tray ------------------------------------------------------------------------------------------------

	@Test
	void trayKeepsTheRealCountsAndCapsTheDrawnDiscs() {
		CountingTray t = new CountingTray(new long[] {3, 4, 0, 2, 20});
		assertEquals(3, t.count(0));
		assertEquals(20, t.count(4));
		assertEquals(3, t.discs(0));
		assertEquals(CountingTray.MAX_DISCS, t.discs(4));
		assertEquals(0, t.discs(2));
		assertEquals(29, t.chips());
		assertEquals(3 + 4 + 2 + CountingTray.MAX_DISCS, t.flights());
	}

	@Test
	void trayDenominationOrderMatchesTheServerSplit() {
		// the cashier sends counts in ChipMath.DENOMINATIONS order; the tray indexes ChipColumns.DENOMS
		assertArrayEquals(ChipMath.DENOMINATIONS, ChipColumns.DENOMS);
		long[] counts = ChipMath.split(1910);
		assertArrayEquals(new long[] {3, 4, 0, 2, 0}, counts);
		long sum = 0;
		for (int i = 0; i < counts.length; i++) sum += counts[i] * ChipColumns.DENOMS[i];
		assertEquals(1910, sum);
	}

	@Test
	void trayFlightsAreStaggeredLargestFirstAndAllLand() {
		CountingTray t = new CountingTray(new long[] {1, 2, 0, 0, 0});
		assertEquals(CountingTray.STAGGER_MS, t.staggerMs());
		assertTrue(t.flight(0, 0, 0) >= 0 && t.flight(0, 0, 0) < 1, "the 500 chip launches first");
		assertTrue(t.flight(1, 0, 0) < 0, "the 100s wait their turn");
		assertEquals(0, t.landed(0));
		long done = t.flightsDoneMs();
		assertEquals(2L * CountingTray.STAGGER_MS + CountingTray.FLIGHT_MS, done);
		assertEquals(3, t.landed(done));
		assertEquals(2, t.landed(1, done));
		assertEquals(2, t.landed(1, done + 10_000), "the stacks stay after the count");
	}

	@Test
	void bigMovesCountInAboutASecond() {
		CountingTray t = new CountingTray(new long[] {50, 50, 50, 50, 50});
		assertEquals(5 * CountingTray.MAX_DISCS, t.flights());
		assertTrue(t.staggerMs() >= CountingTray.MIN_STAGGER_MS && t.staggerMs() < CountingTray.STAGGER_MS);
		assertTrue(t.flightsDoneMs() <= CountingTray.LAUNCH_BUDGET_MS + CountingTray.FLIGHT_MS + CountingTray.MIN_STAGGER_MS);
		CountingTray empty = new CountingTray(new long[0]);
		assertEquals(0, empty.flights());
		assertEquals(0, empty.flightsDoneMs());
	}

	@Test
	void discsStackUpFromTheBase() {
		assertEquals(97, CountingTray.discY(100, 0));
		assertEquals(97 - ChipColumns.PITCH * 6, CountingTray.discY(100, 6));
	}

	// ---- HUD placement ------------------------------------------------------------------------------------------------

	@Test
	void hudStaysInTheCornerWithoutBossBars() {
		assertEquals(HudPlacement.MARGIN, HudPlacement.top(4, 120, 320, 200, 0, 0, false, 0));
	}

	@Test
	void hudMovesBelowBossBarsItWouldCross() {
		// GUI scale 4 on 1280 × 800: 320 × 200, the boss bar spans 69–251
		int y = HudPlacement.top(4, 120, 320, 200, 1, 60, false, 0);
		assertTrue(y >= 12 + 5, "below the first bar: " + y);
		int two = HudPlacement.top(4, 120, 320, 200, 2, 60, false, 0);
		assertTrue(two >= 12 + 19 + 5, "below the second bar: " + two);
		// scale 1 on 1280 wide: the bars are far from the left corner
		assertEquals(HudPlacement.MARGIN, HudPlacement.top(4, 120, 1280, 800, 2, 60, false, 0));
		// a long boss name widens the span
		assertTrue(HudPlacement.top(4, 120, 640, 400, 1, 520, false, 0) > HudPlacement.MARGIN);
	}

	@Test
	void vanillaStopsDrawingBossBarsAtAThirdOfTheScreen() {
		assertEquals(1, HudPlacement.visibleBossBars(5, 60)); // guiH / 3 = 20: only the first bar
		assertEquals(5, HudPlacement.visibleBossBars(5, 800));
		assertEquals(0, HudPlacement.visibleBossBars(0, 800));
	}

	@Test
	void hudTopRightClearsTheEffectRows() {
		assertTrue(HudPlacement.top(200, 116, 320, 200, 0, 0, true, 1) >= 26);
		assertTrue(HudPlacement.top(200, 116, 320, 200, 0, 0, true, 2) >= 52);
		assertEquals(HudPlacement.MARGIN, HudPlacement.top(4, 116, 320, 200, 0, 0, false, 2), "effects are top-right only");
	}

	@Test
	void hudAtTheBottomClearsTheHotbarAndStatusBars() {
		// scale 4 (320 wide): a 120-px block at the right crosses the hotbar column (69–251 + the off-hand slot)
		int y = HudPlacement.bottom(196, 120, 40, 320, 200);
		assertTrue(y + 40 <= 200 - 49, "above armour / air: " + (y + 40));
		// scale 1 (1280 wide): the corner is clear of the hotbar
		assertEquals(800 - 40 - HudPlacement.MARGIN - HudPlacement.BOTTOM_LIFT, HudPlacement.bottom(1156, 120, 40, 1280, 800));
	}

	// ---- loan ---------------------------------------------------------------------------------------------------------------

	@Test
	void dueInIsDaysAndHours() {
		assertArrayEquals(new long[] {2, 5}, LoanLook.dueIn(2 * 24_000 + 5_000 + 999));
		assertArrayEquals(new long[] {0, 23}, LoanLook.dueIn(23_999));
		assertArrayEquals(new long[] {1, 0}, LoanLook.dueIn(24_000));
		assertArrayEquals(new long[] {0, 0}, LoanLook.dueIn(999));
		assertArrayEquals(new long[] {0, 0}, LoanLook.dueIn(-5_000));
	}

	@Test
	void debtMeterFollowsRepaymentsAndFees() {
		assertEquals(575 / 750.0, LoanLook.debtFill(575, 500), 1e-9);
		assertTrue(LoanLook.debtFill(300, 500) < LoanLook.debtFill(575, 500), "repaying empties it");
		assertEquals(1, LoanLook.debtFill(900, 500), 1e-9);
		assertEquals(0, LoanLook.debtFill(0, 500), 1e-9);
	}
}
