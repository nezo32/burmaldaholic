package dev.nezo.burmaldaholic.core.pvp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.pvp.logic.PvpMath;
import dev.nezo.burmaldaholic.core.pvp.logic.Settlement;
import dev.nezo.burmaldaholic.core.pvp.logic.Settlement.Seat;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** PVP.md §16.1 C5, C7, §16.4 W3, BOTS.md §5.1 (bot rake share to the bank) and §12.5 attribution. */
class SettlementTest {
	/** C5: Σ debits = Σ payouts + Σ rake exactly; no balance below 0; the bank's net = its rake share + bank-bot results. */
	@Test
	void conservationC5() {
		SplittableRandom r = new SplittableRandom(0xC5);
		for (int k = 0; k < 100_000; k++) {
			int n = 2 + r.nextInt(7);
			boolean wheel = r.nextInt(5) == 0;
			long entry = 10 + r.nextInt(5000);
			boolean owned = r.nextBoolean();
			List<Seat> seats = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				long stake = wheel ? 10 + r.nextInt(5000) : entry;
				int kind = i == 0 ? 0 : r.nextInt(3); // seat 0 is always a human host
				seats.add(kind == 0 ? Seat.human(stake) : owned ? Seat.bankrollBot(stake) : Seat.bankBot(stake));
			}
			int[] order = permutation(r, n);
			int[] winners;
			if (r.nextInt(10) == 0) {
				int ties = 2 + r.nextInt(n - 1);
				winners = new int[ties];
				System.arraycopy(permutation(r, n), 0, winners, 0, ties);
			} else {
				winners = new int[] {r.nextInt(n)};
			}
			int bp = r.nextInt(1001);
			Settlement.Result res = Settlement.settle(seats, winners, order, bp, owned);
			long pot = 0;
			long paid = 0;
			for (int i = 0; i < n; i++) {
				pot += seats.get(i).stake();
				paid += res.payouts()[i];
				assertTrue(res.payouts()[i] >= 0);
			}
			assertEquals(pot, res.pot());
			assertEquals(pot, paid + res.rake(), "Σ stakes = Σ payouts + rake");
			assertTrue(res.rakeToBankroll() >= 0 && res.rakeToBankroll() <= res.rake());
			if (!owned) {
				assertEquals(0, res.rakeToBankroll());
			}
			// Real chips: escrow into the bank, then the settlement legs. Every account ends ≥ 0 and the bank's
			// net is exactly its rake share plus what bank-funded bots won or lost.
			long houseNet = Settlement.escrowToHouse(seats) + res.houseDelta();
			long bankrollNet = -Settlement.escrowFromBankroll(seats) + res.bankrollDelta();
			long humansNet = 0;
			long bankBotsNet = 0;
			for (int i = 0; i < n; i++) {
				Seat s = seats.get(i);
				long net = res.payouts()[i] - s.stake();
				if (!s.bot()) {
					humansNet += net;
				} else if (!s.bankrollBot()) {
					bankBotsNet += net;
				}
			}
			assertEquals(0, humansNet + houseNet + bankrollNet, "real chips are conserved (every batch balances)");
			assertEquals(res.rake() - res.rakeToBankroll() + bankBotsNet, houseNet, "bank net = its rake share + bank-bot results");
			assertTrue(-res.houseDelta() <= pot, "the bank pays out at most the pot");
		}
	}

	/** C7: owned anchor → the rake goes to the bankroll; with bots, their share of it goes to the bank (BOTS.md §5.1). */
	@Test
	void ownedAnchorRakeC7() {
		List<Seat> humans = List.of(Seat.human(100), Seat.human(100));
		Settlement.Result r = Settlement.settle(humans, new int[] {1}, new int[] {0, 1}, 300, true);
		assertEquals(6, r.rake());
		assertEquals(6, r.rakeToBankroll());
		assertEquals(6, r.bankrollDelta());
		assertEquals(-(194 + 6), r.houseDelta());
		// 1 human + 1 bankroll bot: floor(6 × 100 / 200) = 3 of the rake goes to the bank sink.
		List<Seat> vsBot = List.of(Seat.human(100), Seat.bankrollBot(100));
		Settlement.Result b = Settlement.settle(vsBot, new int[] {0}, new int[] {0, 1}, 300, true);
		assertEquals(3, b.rakeToBankroll());
		assertEquals(3, b.bankrollDelta());
		assertEquals(-(194 + 3), b.houseDelta());
		// Bot wins: its payout returns to the bankroll together with the rake share.
		Settlement.Result c = Settlement.settle(vsBot, new int[] {1}, new int[] {0, 1}, 300, true);
		assertEquals(194 + 3, c.bankrollDelta());
		// Unowned: nothing to a bankroll; bank bots move nothing.
		Settlement.Result u = Settlement.settle(List.of(Seat.human(100), Seat.bankBot(100)), new int[] {1}, new int[] {0, 1}, 300, false);
		assertEquals(0, u.houseDelta());
		assertEquals(0, u.bankrollDelta());
		assertEquals(100, Settlement.escrowToHouse(List.of(Seat.human(100), Seat.bankBot(100))));
	}

	/** W3: exact EV of Wheel Party stakes [50, 150, 800] at 300 bp: −1.5 / −4.5 / −24 (Lemma 2). */
	@Test
	void wheelExactEvW3() {
		long[] stakes = {50, 150, 800};
		List<Seat> seats = List.of(Seat.human(50), Seat.human(150), Seat.human(800));
		long pot = 1000;
		long[] sum = new long[3];
		for (long u = 0; u < pot; u++) {
			int w = PvpMath.sliceOwner(stakes, u);
			Settlement.Result r = Settlement.settle(seats, new int[] {w}, new int[] {0, 1, 2}, 300, false);
			for (int i = 0; i < 3; i++) {
				sum[i] += r.payouts()[i] - stakes[i];
			}
		}
		assertEquals(-1.5, sum[0] / (double) pot, 1e-9);
		assertEquals(-4.5, sum[1] / (double) pot, 1e-9);
		assertEquals(-24.0, sum[2] / (double) pot, 1e-9);
	}

	/** BOTS.md §5.3 bot share and §5.4 PvP attribution. */
	@Test
	void botShareAndAttribution() {
		List<Seat> seats = List.of(Seat.human(100), Seat.human(100), Seat.bankBot(100), Seat.bankBot(100));
		assertEquals(2.0 / 3, Settlement.botShare(seats, 0), 1e-12);
		long[] humanWins = {388, 0, 0, 0};
		assertEquals(Math.floorDiv(288L * 200, 300), Settlement.botAttributedNet(seats, humanWins, 0));
		long[] botWins = {0, 0, 388, 0};
		assertEquals(-100, Settlement.botAttributedNet(seats, botWins, 0));
		long[] humanOtherWins = {0, 388, 0, 0};
		assertEquals(0, Settlement.botAttributedNet(seats, humanOtherWins, 0));
		assertArrayEquals(new long[] {0, 388, 0, 0}, humanOtherWins);
	}

	private static int[] permutation(SplittableRandom r, int n) {
		int[] p = new int[n];
		for (int i = 0; i < n; i++) {
			p[i] = i;
		}
		for (int i = n - 1; i > 0; i--) {
			int j = r.nextInt(i + 1);
			int t = p[i];
			p[i] = p[j];
			p[j] = t;
		}
		return p;
	}
}
