package dev.nezo.burmaldaholic.core.anim.dice;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Test trays: the duel lanes, world lanes and two craps fixtures built the way CM's {@code CrapsThrow} will (back wall
 * with pyramids and mustHit, side and shooter rails, COME-row rest zone, keep-outs for chips / puck / total pill).
 * Units: tray u = die half-edge (a T2 18-px die = 2 u, so 1 GUI px ≈ 0.111 u).
 */
final class DiceFixtures {
	private DiceFixtures() {}

	record Case(String name, DiceTray tray, ThrowSpec spec, int targetMs) {}

	/** Craps table {@code w × h} u, back wall at y = 0, shooter rail at y = h. */
	static DiceTray craps(double w, double h, DiceTray.Box zone, List<DiceTray.Box> keepOuts) {
		List<DiceTray.Wall> walls = new ArrayList<>();
		walls.add(new DiceTray.Wall(0, 0, w, 0, 0.58, 0.44, true)); // back wall (pyramids), interior +y
		walls.add(new DiceTray.Wall(w, 0, w, h, 0.5, 0, false)); // right rail, interior −x
		walls.add(new DiceTray.Wall(w, h, 0, h, 0.5, 0, false)); // shooter rail, interior −y
		walls.add(new DiceTray.Wall(0, h, 0, 0, 0.5, 0, false)); // left rail, interior +x
		DiceTray.Poly floor = new DiceTray.Poly(new double[] {0, 0, w, 0, w, h, 0, h});
		return new DiceTray(140, floor, walls, List.of(), List.of(zone), keepOuts, Optional.empty(), 2.4);
	}

	static DiceTray crapsBig() {
		return craps(52, 21, new DiceTray.Box(23, 4.6, 43.5, 8.2),
			List.of(new DiceTray.Box(45, 4.5, 50.5, 8.5), new DiceTray.Box(8, 1.6, 12, 3.6), new DiceTray.Box(28, 1.6, 32, 3.6),
				new DiceTray.Box(30, 10.5, 34, 12.5)));
	}

	static DiceTray crapsCompact() {
		return craps(40, 16, new DiceTray.Box(17.5, 4.0, 32.5, 6.4),
			List.of(new DiceTray.Box(34, 3.5, 38.5, 7), new DiceTray.Box(20, 1.6, 23, 3.0)));
	}

	static List<Case> all() {
		List<Case> l = new ArrayList<>();
		l.add(new Case("duelL", DiceTray.duelLane(DiceTray.Side.LEFT), ThrowSpec.cup(DiceTray.Side.LEFT), 1200));
		l.add(new Case("duelR", DiceTray.duelLane(DiceTray.Side.RIGHT), ThrowSpec.cup(DiceTray.Side.RIGHT), 1200));
		for (int len : new int[] {2, 4, 8}) l.add(new Case("world" + len, DiceTray.worldLane(len), ThrowSpec.chest(), 1200));
		l.add(new Case("crapsBig", crapsBig(), ThrowSpec.hand(0, 47.5, 18.6), 1700));
		l.add(new Case("crapsCompact", crapsCompact(), ThrowSpec.hand(1, 4.5, 13.8), 1700));
		// CM: the shipped craps tray (CrapsThrow) from both ends of the Pass line, at both ends of its target range
		l.add(new Case("crapsCmR", dev.nezo.burmaldaholic.games.craps.present.CrapsThrow.tray(),
			dev.nezo.burmaldaholic.games.craps.present.CrapsThrow.spec(dev.nezo.burmaldaholic.games.craps.present.CrapsThrow.RIGHT),
			dev.nezo.burmaldaholic.games.craps.present.CrapsThrow.TARGET_MIN));
		l.add(new Case("crapsCmL", dev.nezo.burmaldaholic.games.craps.present.CrapsThrow.tray(8),
			dev.nezo.burmaldaholic.games.craps.present.CrapsThrow.spec(dev.nezo.burmaldaholic.games.craps.present.CrapsThrow.LEFT),
			1700));
		return l;
	}
}
