package dev.nezo.burmaldaholic.games.roulette.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.games.roulette.logic.Bets.Bet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The art-sized layouts hit-test the shapes the generated felt draws (visual/tables.md §3.2), and the racetrack. */
class LayoutGridTest {
	private static final List<LayoutGrid> GRIDS = List.of(LayoutGrid.BIG, LayoutGrid.COMPACT, LayoutGrid.LEGACY);

	@Test
	void sizesMatchTheArt() {
		assertEquals(278, LayoutGrid.BIG.width());
		assertEquals(98, LayoutGrid.BIG.height());
		assertEquals(194, LayoutGrid.COMPACT.width());
		assertEquals(67, LayoutGrid.COMPACT.height());
		assertEquals(Layout.WIDTH, LayoutGrid.LEGACY.width());
		assertEquals(Layout.HEIGHT, LayoutGrid.LEGACY.height());
		// number cell n: x = 18 + 20·col, y = 22·row (visual §3.2)
		assertEquals(new Layout.Rect(18 + 20 * 5, 22, 20, 22), LayoutGrid.BIG.cell(17));
		assertEquals(new Layout.Rect(18, 0, 20, 22), LayoutGrid.BIG.cell(3));
		assertEquals(new Layout.Rect(258, 0, 20, 22), LayoutGrid.BIG.outsideBox(Spot.of(BetType.COLUMN, Spot.outsideNumbers(BetType.COLUMN, 3))));
		assertEquals(new Layout.Rect(98, 66, 80, 16), LayoutGrid.BIG.outsideBox(Spot.of(BetType.DOZEN, Spot.outsideNumbers(BetType.DOZEN, 2))));
		assertEquals(new Layout.Rect(98, 82, 40, 16), LayoutGrid.BIG.outsideBox(Spot.of(BetType.RED, Spot.outsideNumbers(BetType.RED, 0))));
	}

	@Test
	void everySpotIsClickableAtItsChipPosition() {
		for (LayoutGrid g : GRIDS) {
			for (BetType t : BetType.values()) {
				for (Spot s : Spot.all(t)) {
					int[] c = g.center(s);
					for (double d : new double[] {0, 1.4, -1.4}) {
						Optional<Spot> hit = g.hit(c[0] + d, c[1] + d);
						assertEquals(Optional.of(s), hit, g + " " + s.key() + " at " + d);
					}
				}
			}
		}
	}

	@Test
	void everyPointHitsAValidSpotAndAllSpotsAreReachable() {
		int total = 0;
		for (BetType t : BetType.values()) {
			total += Spot.all(t).size();
		}
		for (LayoutGrid g : GRIDS) {
			Set<Spot> seen = new HashSet<>();
			for (double x = 0; x < g.width(); x += 0.5) {
				for (double y = 0; y < g.height(); y += 0.5) {
					g.hit(x, y).ifPresent(spot -> {
						assertTrue(spot.isValid(), spot.key());
						seen.add(spot);
					});
				}
			}
			assertEquals(total, seen.size(), g.toString());
		}
	}

	@Test
	void cellsTileTheGrid() {
		for (LayoutGrid g : GRIDS) {
			for (int n = 0; n <= 36; n++) {
				Layout.Rect r = g.cell(n);
				assertEquals(Optional.of(Spot.of(BetType.STRAIGHT, n)), g.hit(n == 0 ? 3 : r.cx(), r.cy()));
			}
		}
	}

	@Test
	void racetrackCellsAreTheWheelOrder() {
		List<Racetrack.Cell> cells = Racetrack.cells();
		assertEquals(Wheel.POCKETS, cells.size());
		Set<Integer> seen = new HashSet<>();
		for (Racetrack.Cell c : cells) {
			assertTrue(seen.add(c.n()), "each number once: " + c.n());
			assertEquals(Optional.of(c.n()), Racetrack.numberAt(c.cx(), c.cy()), "cell " + c.n() + " is clickable at its label");
		}
		// consecutive cells are wheel neighbours (clockwise around the stadium)
		for (int i = 0; i < cells.size(); i++) {
			int a = Wheel.wheelIndex(cells.get(i).n());
			int b = Wheel.wheelIndex(cells.get((i + 1) % cells.size()).n());
			assertEquals(1, Math.floorMod(b - a, Wheel.POCKETS), cells.get(i).n() + " → " + cells.get((i + 1) % cells.size()).n());
		}
		assertEquals(24, cells.get(0).n());
		assertEquals(Optional.of(Racetrack.Section.TIER), Racetrack.sectionAt(50, 22));
		assertEquals(Optional.of(Racetrack.Section.ORPHELINS), Racetrack.sectionAt(117, 22));
		assertEquals(Optional.of(Racetrack.Section.VOISINS), Racetrack.sectionAt(186, 22));
		assertEquals(Optional.of(Racetrack.Section.ZERO), Racetrack.sectionAt(240, 22));
		assertEquals(Optional.empty(), Racetrack.sectionAt(2, 2));
	}

	@Test
	void callBetsAreStandardBets() {
		assertEquals(List.of(5, 24, 16, 33, 1), Racetrack.neighbours(16, 2));
		assertEquals(List.of(3, 26, 0, 32, 15), Racetrack.neighbours(0, 2));
		int[] chips = {6, 5, 9, 4};
		Racetrack.Section[] sections = Racetrack.Section.values();
		for (int i = 0; i < sections.length; i++) {
			List<Spot> spots = Racetrack.spots(sections[i]);
			assertEquals(chips[i], spots.size(), sections[i].id());
			for (Spot s : spots) {
				assertTrue(s.isValid(), s.key());
			}
			List<Bet> bets = Racetrack.bets(sections[i], 10);
			assertEquals(10L * chips[i], Bets.totalStaked(bets));
		}
		// Tier + Orphelins + Voisins cover the whole wheel
		Set<Integer> all = new HashSet<>();
		all.addAll(Racetrack.covered(Racetrack.Section.TIER));
		all.addAll(Racetrack.covered(Racetrack.Section.ORPHELINS));
		all.addAll(Racetrack.covered(Racetrack.Section.VOISINS));
		assertEquals(Wheel.POCKETS, all.size());
		assertEquals(50, Bets.totalStaked(Racetrack.neighbourBets(17, 10)));
	}
}
