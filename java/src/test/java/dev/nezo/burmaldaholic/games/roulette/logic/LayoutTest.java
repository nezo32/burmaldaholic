package dev.nezo.burmaldaholic.games.roulette.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LayoutTest {
	private static Spot hit(double x, double y) {
		return Layout.hit(x, y).orElseThrow(() -> new AssertionError("nothing at " + x + "," + y));
	}

	@Test
	void everySpotIsClickableAtItsChipPosition() {
		for (BetType t : BetType.values()) {
			for (Spot s : Spot.all(t)) {
				int[] c = Layout.center(s);
				assertEquals(s, hit(c[0], c[1]), s.key());
				// A little wobble around the chip position still hits the same spot.
				assertEquals(s, hit(c[0] + 1.5, c[1] + 1.5), s.key());
				assertEquals(s, hit(c[0] - 1.5, c[1] - 1.5), s.key());
			}
		}
	}

	@Test
	void everyPointHitsAValidSpotAndAllSpotsAreReachable() {
		Set<Spot> seen = new HashSet<>();
		for (double x = 0; x < Layout.WIDTH; x += 0.5) {
			for (double y = 0; y < Layout.HEIGHT; y += 0.5) {
				Optional<Spot> s = Layout.hit(x, y);
				s.ifPresent(spot -> {
					assertTrue(spot.isValid(), spot.key());
					seen.add(spot);
				});
			}
		}
		int total = 0;
		for (BetType t : BetType.values()) {
			total += Spot.all(t).size();
		}
		assertEquals(total, seen.size());
	}

	@Test
	void examples() {
		int cw = Layout.CW;
		int ch = Layout.CH;
		int zw = Layout.ZW;
		// Middle of the "17" cell: grid column 5, 2nd column (visual row 1).
		assertEquals(Spot.of(BetType.STRAIGHT, 17), hit(zw + 5 * cw + 12, ch + 9));
		// Edge between 17 and 20.
		assertEquals(Spot.of(BetType.SPLIT, 17, 20), hit(zw + 6 * cw, ch + 9));
		// Edge between 17 and 18 (18 is above 17).
		assertEquals(Spot.of(BetType.SPLIT, 17, 18), hit(zw + 5 * cw + 12, ch));
		// Corner 17-18-20-21.
		assertEquals(Spot.of(BetType.CORNER, 17, 18, 20, 21), hit(zw + 6 * cw, ch));
		// Outer edge below 16 → street 16-17-18; outer intersection → six line 16..21.
		assertEquals(Spot.of(BetType.STREET, 16, 17, 18), hit(zw + 5 * cw + 12, 3 * ch));
		assertEquals(Spot.of(BetType.SIX_LINE, 16, 17, 18, 19, 20, 21), hit(zw + 6 * cw, 3 * ch + 1));
		// Zero: cell, splits, trios, first four.
		assertEquals(Spot.of(BetType.STRAIGHT, 0), hit(5, 20));
		assertEquals(Spot.of(BetType.SPLIT, 0, 3), hit(zw, 8));
		assertEquals(Spot.of(BetType.SPLIT, 0, 1), hit(zw, 2 * ch + 9));
		assertEquals(Spot.of(BetType.TRIO, 0, 2, 3), hit(zw, ch));
		assertEquals(Spot.of(BetType.TRIO, 0, 1, 2), hit(zw, 2 * ch));
		assertEquals(Spot.of(BetType.FIRST_FOUR, 0, 1, 2, 3), hit(zw, 3 * ch));
		// Outside boxes.
		assertEquals(BetType.COLUMN, hit(Layout.GRID_RIGHT + 5, 5).type());
		assertEquals(3, hit(Layout.GRID_RIGHT + 5, 5).outsideIndex());
		assertEquals(2, hit(zw + 5 * cw, Layout.DOZEN_Y + 5).outsideIndex());
		assertEquals(BetType.LOW, hit(zw + 1, Layout.EVEN_Y + 5).type());
		assertEquals(BetType.RED, hit(zw + 5 * cw, Layout.EVEN_Y + 5).type());
		assertEquals(BetType.HIGH, hit(Layout.GRID_RIGHT - 1, Layout.EVEN_Y + 5).type());
		// Empty corners of the layout.
		assertEquals(Optional.empty(), Layout.hit(2, Layout.EVEN_Y + 5));
		assertEquals(Optional.empty(), Layout.hit(Layout.GRID_RIGHT + 5, Layout.DOZEN_Y + 5));
		assertEquals(Optional.empty(), Layout.hit(-1, 5));
	}

	@Test
	void cellsTileTheGrid() {
		for (int n = 1; n <= 36; n++) {
			Layout.Rect r = Layout.cell(n);
			assertEquals(Spot.of(BetType.STRAIGHT, n), hit(r.cx(), r.cy()));
		}
	}

	@Test
	void spinAnimationEndsOnTheResult() {
		for (int result = 0; result <= 36; result++) {
			for (int start = 0; start < 37; start += 5) {
				assertEquals(result, SpinAnimation.pocketAt(SpinAnimation.position(result, 1.0, start, 3)));
				double prev = SpinAnimation.position(result, 0, start, 3);
				assertEquals(start, prev, 1e-9);
				for (double p = 0.05; p <= 1.0; p += 0.05) {
					double cur = SpinAnimation.position(result, p, start, 3);
					assertTrue(cur >= prev, "monotonic");
					prev = cur;
				}
			}
		}
	}
}
