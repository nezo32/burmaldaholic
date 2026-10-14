package dev.nezo.burmaldaholic.games.baccarat.present;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.Gate;
import dev.nezo.burmaldaholic.core.ui.v2.LayoutAudit;
import dev.nezo.burmaldaholic.core.ui.v2.TableGrid;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Baccarat v2: public tag encoding, exhaustive gates (ui-v2.md §5.3), layout audit at every size with 1–7 plates. */
class BaccaratPresentTest {
	@Test
	void pubRoundTrips() {
		BaccaratPub p = new BaccaratPub(42, BaccaratPub.RESULT, false, 10, 1_000, 99_999_999_999L, 500, new int[] {3, -1, 12}, new int[] {0, 10, 40},
			new int[] {20, -1, 60}, new int[] {7, 8}, new int[] {5, 15}, new int[] {25, 30}, new long[] {100, 25, 5, 0, 1}, 2, 0,
			List.of(new BaccaratPub.SeatPub(0, BaccaratPub.READY, new long[] {100, 0, 5, 0, 0}),
				new BaccaratPub.SeatPub(6, BaccaratPub.BOT, new long[] {0, 25, 0, 0, 1})));
		assertEquals(p, BaccaratPub.decode(p.encode()));
		assertEquals(BaccaratPub.P, p.seat(0).mainBox());
		assertEquals(BaccaratPub.EMPTY, BaccaratPub.decode(BaccaratPub.EMPTY.encode()));
		int[] e = p.encode();
		assertSame(BaccaratPub.EMPTY, BaccaratPub.decode(java.util.Arrays.copyOf(e, e.length - 1)));
		assertSame(BaccaratPub.EMPTY, BaccaratPub.decode(new int[] {1, 2}));
		assertEquals(6, BaccaratPub.total(new int[] {3, 11, 1})); // 4 + Q (0) + 2
		assertEquals("result", p.phaseId());
	}

	@Test
	void neverStuck() {
		String[] phases = {"idle", "betting", "no_more_bets", "shuffle", "reveal", "result", "bank_offer", "waiting"};
		int cases = 0;
		for (String phase : phases) {
			for (boolean chemmy : new boolean[] {false, true}) {
				for (boolean seated : new boolean[] {false, true}) {
					for (long balance : new long[] {0, 50, 10_000}) {
						for (long bet : new long[] {0, 5, 100}) {
							for (boolean ready : new boolean[] {false, true}) {
								for (boolean offerYou : new boolean[] {false, true}) {
									for (boolean youBank : new boolean[] {false, true}) {
										for (boolean banco : new boolean[] {false, true}) {
											for (boolean pending : new boolean[] {false, true}) {
												BaccaratView v = new BaccaratView(seated, true, chemmy, phase, 10, 1_000, 10, balance,
													new long[] {chemmy ? 0 : bet, 0, 0, 0, 0}, ready, bet == 0, true, 9, false, chemmy ? bet : 0, offerYou, false,
													youBank, true, 500, 0, banco, 100, "Alex", pending);
												check(v);
												cases++;
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}
		assertTrue(cases > 5_000);
	}

	private static void check(BaccaratView v) {
		List<Gate> all = new ArrayList<>();
		all.add(BaccaratFlow.INSTANCE.primary(v));
		all.addAll(BaccaratFlow.INSTANCE.actions(v));
		for (String t : new String[] {"undo", "clear", "rebet", "double"}) all.add(BaccaratFlow.INSTANCE.tool(t, v));
		for (long d : v.chips()) {
			all.add(BaccaratFlow.INSTANCE.chip(d, v));
			for (int b = 0; b < BaccaratPub.BOXES; b++) all.add(BaccaratFlow.box(b, d, v));
		}
		Gate p = all.get(0);
		assertTrue(all.stream().anyMatch(Gate::enabled) || p.reasonKey() != null, () -> "stuck: " + v);
		for (Gate g : all) assertTrue(g.enabled() || g.reasonKey() != null, () -> g.toString());
		if (v.seated() && v.houseOpen() && !v.ready() && !v.pending() && v.balance() >= v.min()) {
			assertTrue(v.chips().stream().anyMatch(d -> BaccaratFlow.INSTANCE.chip(d, v).enabled() && BaccaratFlow.box(BaccaratPub.P, d, v).enabled()),
				() -> "no chip: " + v);
		}
		if (v.seated() && v.houseOpen() && !v.ready() && !v.pending() && v.total() >= v.minTotal()) {
			assertTrue(p.enabled() && p.action().equals("deal"), () -> "deal: " + v);
		}
		assertEquals(2, BaccaratFlow.status(v).length);
	}

	@Test
	void layoutFitsAtEverySize() {
		List<int[]> sizes = new ArrayList<>(List.of(new int[] {320, 240}, new int[] {427, 240}, new int[] {480, 270}, new int[] {640, 360},
			new int[] {854, 480}, new int[] {630, 270}, new int[] {960, 539}));
		for (int h = 240; h < 540; h += 23) {
			for (int w = Math.max(320, h * 4 / 3); w <= Math.min(1000, h * 21 / 9 + 1); w += 53) sizes.add(new int[] {w, h});
		}
		boolean[][] opens = {{true, true, true, true, true}, {true, true, true, false, false}, {true, true, false, false, false}};
		for (int[] s : sizes) {
			TableGrid g = TableGrid.of(s[0], s[1]);
			for (boolean[] open : opens) {
				for (int seats = 1; seats <= 7; seats++) {
					BaccaratLayout l = BaccaratLayout.of(g, open, seats, new int[] {36, 36});
					LayoutAudit a = new LayoutAudit(g.play);
					a.add("player", l.player()).add("banker", l.banker());
					for (int k = 0; k < BaccaratPub.BOXES; k++) a.add("box" + k, l.boxes()[k]);
					for (int i = 0; i < l.plates().length; i++) a.add("plate" + i, l.plates()[i]);
					List<String> p = new ArrayList<>(a.problems());
					for (int k = 0; k < BaccaratPub.BOXES; k++) {
						Box b = l.boxes()[k];
						if (b.isEmpty()) continue;
						for (Box in : new Box[] {l.boxName()[k], l.boxPays()[k], l.mine()[k], l.mineValue()[k], l.others()[k]}) {
							if (!in.isEmpty() && !in.inside(b)) p.add("box " + k + " content " + in + " outside " + b);
						}
						if (!l.mine()[k].isEmpty() && l.mine()[k].intersects(l.boxPays()[k])) p.add("box " + k + " stack over payout");
						assertTrue(b.w() >= 30, s[0] + "x" + s[1] + " box " + k + " " + b);
					}
					for (Box c : new Box[] {l.playerCards(), l.bankerCards()}) {
						if (!c.inside(l.player()) && !c.inside(l.banker())) p.add("cards " + c + " outside the hand box");
					}
					String where = s[0] + "x" + s[1] + " seats " + seats;
					assertTrue(p.isEmpty(), () -> where + ": " + p);
				}
			}
		}
	}
}
