package dev.nezo.burmaldaholic.games.craps.present;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.TableGrid;
import dev.nezo.burmaldaholic.games.craps.logic.CrapsBeats;
import dev.nezo.burmaldaholic.games.craps.present.CrapsChipBeats.Flight;
import dev.nezo.burmaldaholic.games.craps.present.CrapsChipBeats.Kind;
import dev.nezo.burmaldaholic.games.craps.present.CrapsChipBeats.Res;
import dev.nezo.burmaldaholic.games.craps.present.CrapsLayout.Layer;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * CS §9.2 CrapsChipBeats: every flight ends exactly on its target box, takes come before pays, the payout disc count is
 * the greedy split of the server's payout, a seven-out takes every losing stack in one wave (come points included), the
 * money shown is the server's ({@code last_res}), and the stickman ends at dice home.
 */
class CrapsChipBeatsTest {
	static final CrapsLayout L = CrapsLayout.of(TableGrid.of(480, 270));
	static final Box BANK = new Box(240, 30, 12, 12), BALANCE = new Box(400, 4, 40, 12), TRAY = new Box(60, 240, 14, 14);
	static final CrapsChipBeats.Boxes B = new CrapsChipBeats.Boxes() {
		@Override
		public Box stack(String kind, int point, Layer layer) {
			return L.stack(kind, point, layer);
		}

		@Override
		public Box bank() {
			return BANK;
		}

		@Override
		public Box tray(long amount) {
			return TRAY;
		}

		@Override
		public Box balance() {
			return BALANCE;
		}

		@Override
		public Box exit(boolean left) {
			return new Box(left ? 100 : 380, 260, 12, 12);
		}
	};

	@Test
	void sevenOutTakesEveryLoserInOneWaveThenNothingIsPaid() {
		CrapsBeats b = CrapsBeats.of(1700);
		List<Res> res = List.of(new Res("pass", 0, true, "lose", 10, 20, 0, 0), new Res("come", 6, true, "lose", 5, 0, 0, 0),
			new Res("come", 9, false, "lose", 15, 0, 0, 0), new Res("dont_pass", 0, false, "win", 10, 0, 20, 0));
		CrapsChipBeats.Plan p = CrapsChipBeats.plan(res, B, b, true);
		List<Flight> takes = p.flights().stream().filter(f -> f.kind() == Kind.TAKE).toList();
		assertEquals(4, takes.size(), "pass flat + odds, two come points");
		for (int i = 0; i < takes.size(); i++) {
			Flight f = takes.get(i);
			assertEquals(b.CHIPS + i * CrapsChipBeats.TAKE_STAGGER, f.start(), 1e-9, "one wave, 40 ms apart");
			if (i > 0) assertTrue(f.x0() >= takes.get(i - 1).x0(), "left to right");
			endsOn(f, BANK.x() + (BANK.w() - f.size()) / 2.0, BANK.bottom() - L.stackH());
		}
		long taken = takes.stream().filter(Flight::mine).mapToLong(Flight::amount).sum();
		assertEquals(35, taken, "the money shown is last_res");
		assertTrue(p.flights().stream().noneMatch(f -> f.kind() == Kind.PAY), "you were paid nothing");
		assertTrue(p.flights().stream().anyMatch(f -> f.kind() == Kind.EXIT && !f.mine()), "the other player's win leaves the felt");
	}

	@Test
	void winnersArePaidBesideTheBetThenCollected() {
		CrapsBeats b = CrapsBeats.of(1800, 1.5);
		// pass 10 with odds 20 on a 6 made: 10 + 24 won; field 5 lost; come 10 travels to 6
		List<Res> res = List.of(new Res("pass", 0, true, "win", 10, 20, 64, 0), new Res("field", 0, true, "lose", 5, 0, 0, 0),
			new Res("come", 0, true, "move", 10, 0, 0, 6));
		CrapsChipBeats.Plan p = CrapsChipBeats.plan(res, B, b, false);
		double lastTake = p.flights().stream().filter(f -> f.kind() == Kind.TAKE).mapToDouble(Flight::start).max().orElseThrow();
		double firstPay = p.flights().stream().filter(f -> f.kind() == Kind.PAY).mapToDouble(Flight::start).min().orElseThrow();
		assertTrue(lastTake < firstPay, "take before pay");
		List<Flight> pays = p.flights().stream().filter(f -> f.kind() == Kind.PAY).toList();
		assertEquals(CrapsChipBeats.discs(34).size(), pays.size(), "payout discs = greedy split of the server's payout");
		Box col = CrapsChipBeats.payColumn(B, res.get(0), L.stack("pass", 0, Layer.MINE));
		for (int i = 0; i < pays.size(); i++) endsOn(pays.get(i), col.x(), col.bottom() - col.w() - i * CrapsChipBeats.STEP);
		Flight travel = p.flights().stream().filter(f -> f.kind() == Kind.TRAVEL).findFirst().orElseThrow();
		Box to = L.stack("come", 6, Layer.MINE);
		endsOn(travel, to.x(), to.y());
		assertEquals(travel.end(), p.travelLanding("come", 6, true), 1e-9);
		List<Flight> collect = p.flights().stream().filter(f -> f.kind() == Kind.COLLECT).toList();
		assertEquals(3, collect.size(), "flat, odds and payout column to the balance");
		for (Flight f : collect) {
			assertTrue(f.start() >= b.COLLECT);
			endsOn(f, BALANCE.x() + (BALANCE.w() - f.size()) / 2.0, BALANCE.y() + (BALANCE.h() - L.stackH()) / 2.0);
		}
		assertTrue(p.collectEnd() <= b.IDLE, "collected before the table is idle");
		CrapsChipBeats.Pill net = p.pills().stream().filter(x -> x.net() == 34).findFirst().orElseThrow();
		assertTrue(net.from() >= firstPay, "the net pill pops when the last disc lands");
	}

	@Test
	void pushAndOddsOffGoBackToTheTray() {
		CrapsBeats b = CrapsBeats.of(1700);
		List<Res> res = List.of(new Res("dont_pass", 0, true, "push", 10, 0, 10, 0), new Res("come", 8, true, "lose", 10, 15, 15, 0));
		CrapsChipBeats.Plan p = CrapsChipBeats.plan(res, B, b, false);
		List<Flight> back = p.flights().stream().filter(f -> f.kind() == Kind.PUSH).toList();
		assertEquals(2, back.size(), "the push and the come odds that were off");
		for (Flight f : back) endsOn(f, TRAY.x() + (TRAY.w() - f.size()) / 2.0, TRAY.y() + (TRAY.h() - L.stackH()) / 2.0);
		assertEquals(1, p.flights().stream().filter(f -> f.kind() == Kind.TAKE).count(), "only the come flat is taken");
	}

	@Test
	void stickReturnsTheDiceHome() {
		CrapsBeats b = CrapsBeats.of(1700);
		double[] rest = {300, 110, 280, 120};
		double[] home = {440, 200, 458, 199};
		CrapsStick.Out o = new CrapsStick.Out();
		CrapsStick.sample(b.STICK - 1, b, rest, home, 20, 18, o);
		assertEquals(CrapsStick.BEFORE, o.phase);
		assertEquals(300, o.x[0], 0);
		CrapsStick.sample((b.stickHook() + b.stickDrag()) / 2, b, rest, home, 20, 18, o);
		assertEquals(CrapsStick.HOOK, o.phase);
		double prev = Double.NaN;
		for (double t = b.STICK; t < b.stickGone() + 50; t += 4) {
			CrapsStick.sample(t, b, rest, home, 20, 18, o);
			if (!Double.isNaN(prev)) assertTrue(Math.abs(o.x[0] - prev) < 12, "continuous at " + t);
			prev = o.x[0];
		}
		CrapsStick.sample(b.stickGone() + 1, b, rest, home, 20, 18, o);
		assertEquals(CrapsStick.DONE, o.phase);
		assertEquals(440, o.x[0], 0);
		assertEquals(200, o.y[0], 0);
		assertEquals(458, o.x[1], 0);
	}

	private static void endsOn(Flight f, double x, double y) {
		CrapsChipBeats.Pos p = new CrapsChipBeats.Pos();
		CrapsChipBeats.sample(f, f.end(), p);
		assertEquals(x, p.x, 1e-9, f.kind() + " x");
		assertEquals(y, p.y, 1e-9, f.kind() + " y");
	}
}
