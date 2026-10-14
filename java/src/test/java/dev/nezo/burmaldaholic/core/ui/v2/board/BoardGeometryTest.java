package dev.nezo.burmaldaholic.core.ui.v2.board;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.ui.v2.board.BoardGeometry.Felt;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** The in-world board kit's pure geometry (docs/architecture/ui-v2.md §7): felt shapes, anchors, chips, LOD, plates. */
class BoardGeometryTest {
	@Test
	void feltShapesAreCentredAndReachTheNeighbours() {
		assertEquals(0.625f, Felt.CRESCENT.reachX(), 1e-6);
		assertEquals(0.125f, Felt.CRESCENT.reachZ(), 1e-6);
		assertEquals(0.5f, Felt.RECT.reachX(), 1e-6);
		assertEquals(0.5f, Felt.OVAL.reachZ(), 1e-6);
		assertEquals(0f, Felt.FALLBACK.reachX(), 1e-6);
		assertEquals(Felt.FALLBACK, BoardGeometry.fallback(Felt.CRESCENT, true));
		assertEquals(Felt.CRESCENT, BoardGeometry.fallback(Felt.CRESCENT, false));
		for (Felt f : Felt.values()) {
			assertTrue(BoardGeometry.inside(f, 0.5f, 0.5f), f + " centre");
			assertFalse(BoardGeometry.inside(f, f.x1() + 0.01f, 0.5f), f + " right");
			// the rounded corners are cut
			assertFalse(BoardGeometry.inside(f, f.x0() + 0.001f, f.z1() - 0.001f), f + " front-left corner");
		}
	}

	/** MT-DESIGN §2: the design felt maps uniformly onto the full 3×2 felt (FeltFrame 2.75 × 1.75 inside the rail). */
	@Test
	void designFeltMapsOntoTheFullTable() {
		var crescent = dev.nezo.burmaldaholic.core.table.parts.FeltFrame.of(dev.nezo.burmaldaholic.core.table.parts.TableGame.BLACKJACK,
			dev.nezo.burmaldaholic.core.table.parts.TablePart.MAIN);
		float k = BoardGeometry.fullScale(Felt.CRESCENT, crescent.w(), crescent.d());
		assertEquals(2.75f / 2.25f, k, 1e-5);
		// the mapped design felt fits inside the full felt
		assertTrue(Felt.CRESCENT.w * k <= crescent.w() + 1e-5 && Felt.CRESCENT.d * k <= crescent.d() + 1e-5);
		assertEquals(1.25f, BoardGeometry.fullScale(Felt.RECT, 2.75, 1.75), 1e-5);
		assertEquals(0.875f, BoardGeometry.fullScale(Felt.OVAL, 2.75, 1.75), 1e-5);
		// the dealer row of a full blackjack table lands on the felt, at the back
		float[] d = BoardGeometry.dealerRect(Felt.CRESCENT);
		float z0 = (d[1] - 0.5f) * k, z1 = (d[3] - 0.5f) * k;
		assertTrue(crescent.onFelt(0.5, z0 + 0.01) && crescent.onFelt(0.5, z1), "dealer row on the felt");
		assertTrue(z1 < 0, "dealer row behind the centre");
	}

	@Test
	void outlineWalksTheRoundedRectangle() {
		for (Felt f : Felt.values()) {
			float[] o = BoardGeometry.outline(f, 4);
			assertEquals(4 * 5 * 2, o.length);
			for (int i = 0; i < o.length; i += 2) {
				float x = o[i], z = o[i + 1];
				assertTrue(x >= f.x0() - 1e-4 && x <= f.x1() + 1e-4 && z >= f.z0() - 1e-4 && z <= f.z1() + 1e-4, f + " point " + i);
				// every outline point lies on the edge: inside a hair in, outside a hair out (toward the centre / away)
				float dx = 0.5f - x, dz = 0.5f - z;
				float len = (float) Math.sqrt(dx * dx + dz * dz);
				assertTrue(BoardGeometry.inside(f, x + dx / len * 0.01f, z + dz / len * 0.01f), f + " in " + i);
			}
		}
	}

	@Test
	void seatsLieOnTheFeltAndPlatesOutsideIt() {
		for (int n = 1; n <= 7; n++) {
			for (int i = 0; i < n; i++) {
				float[] s = BoardGeometry.crescentSeat(i, n);
				assertTrue(BoardGeometry.inside(Felt.CRESCENT, s[0], s[1]), "seat " + i + "/" + n);
				float[] a = BoardGeometry.plateAnchor(Felt.CRESCENT, s[0], s[1]);
				assertFalse(BoardGeometry.inside(Felt.CRESCENT, a[0], a[2]), "plate anchor outside the felt " + i + "/" + n);
				assertEquals(BoardGeometry.TOP + BoardGeometry.PLATE_UP, a[1], 1e-6);
			}
		}
		for (int n = 2; n <= 6; n++) {
			for (int i = 0; i < n; i++) {
				float[] s = BoardGeometry.ovalSeat(i, n);
				assertTrue(BoardGeometry.inside(Felt.OVAL, s[0], s[1]), "oval seat " + i + "/" + n);
			}
		}
		// seat spots of a 7-seat crescent are at least a card apart
		for (int i = 1; i < 7; i++) {
			float[] a = BoardGeometry.crescentSeat(i - 1, 7), b = BoardGeometry.crescentSeat(i, 7);
			assertTrue(Math.hypot(a[0] - b[0], a[1] - b[1]) >= BoardGeometry.CARD_W, "seat pitch " + i);
		}
		float[] board = BoardGeometry.boardAnchor(Felt.CRESCENT);
		assertEquals(BoardGeometry.TOP + BoardGeometry.BOARD_UP, board[1], 1e-6);
	}

	@Test
	void chipsSplitGreedilyIntoAtMostSixDiscs() {
		int[] d = new int[BoardGeometry.CHIP_MAX];
		assertEquals(0, BoardGeometry.discs(0, d));
		assertEquals(1, BoardGeometry.discs(1, d));
		assertEquals(0, d[0]);
		assertEquals(3, BoardGeometry.discs(130, d)); // 100 + 25 + 5
		assertArrayEquals(new int[] {3, 2, 1}, new int[] {d[0], d[1], d[2]});
		assertEquals(4, BoardGeometry.discs(4, d));
		assertEquals(6, BoardGeometry.discs(1_000_000, d));
		assertEquals(BoardGeometry.DENOMS.length - 1, d[0]);
	}

	@Test
	void readableTextMeetsTheMeasuredTargets() {
		// orchestrator's measured targets at 1920 × 1080, FOV 70: cap ≥ 18 px up to 5 blocks, ≥ 13 px at 8
		for (double d = 2; d <= 8; d += 0.5) {
			float plate = HoloScale.readablePx(HoloScale.PLATE_READ, d, 70, 1080);
			float board = HoloScale.readablePx(HoloScale.BOARD_READ, d, 70, 1080);
			double want = d <= 5 ? 18 : 13;
			assertTrue(HoloScale.capPx(plate, d, 70, 1080) >= want - 1e-6, "plate at " + d);
			assertTrue(HoloScale.capPx(board, d, 70, 1080) >= want - 1e-6, "board at " + d);
		}
		assertEquals(HoloScale.PLATE_READ, HoloScale.readablePx(HoloScale.PLATE_READ, 3, 70, 1080));
	}

	@Test
	void lodHasHysteresis() {
		assertEquals(HoloScale.Lod.FULL, HoloScale.lod(8.3, HoloScale.Lod.FULL));
		assertEquals(HoloScale.Lod.BOARD, HoloScale.lod(9.1, HoloScale.Lod.FULL));
		assertEquals(HoloScale.Lod.BOARD, HoloScale.lod(7.8, HoloScale.Lod.BOARD));
		assertEquals(HoloScale.Lod.FULL, HoloScale.lod(6.9, HoloScale.Lod.BOARD));
		assertEquals(HoloScale.Lod.MODEL, HoloScale.lod(33, null));
		assertEquals(HoloScale.Lod.NO_TEXT, HoloScale.lod(32.3, HoloScale.Lod.NO_TEXT));
		// a jump across two levels is taken at once
		assertEquals(HoloScale.Lod.MODEL, HoloScale.lod(40, HoloScale.Lod.FULL));
	}

	@Test
	void platesStayAboveTheirOwnContentAndApart() {
		Random r = new Random(11);
		int[] shift = new int[8];
		for (int run = 0; run < 2000; run++) {
			int n = 1 + r.nextInt(8);
			float[] au = new float[n], av = new float[n], w = new float[n], h = new float[n], ou = new float[n], ov = new float[n];
			boolean[] leader = new boolean[n];
			int[] order = new int[n];
			for (int i = 0; i < n; i++) {
				au[i] = -1.2f + 2.4f * r.nextFloat();
				av[i] = -0.1f + 0.3f * r.nextFloat();
				w[i] = 0.3f + 0.5f * r.nextFloat();
				h[i] = 0.12f + 0.1f * r.nextFloat();
				order[i] = i;
			}
			int obs = 1 + r.nextInt(12);
			float[] o0 = new float[obs], o1 = new float[obs], ot = new float[obs];
			for (int k = 0; k < obs; k++) {
				float c = -1.2f + 2.4f * r.nextFloat();
				o0[k] = c - 0.12f;
				o1[k] = c + 0.12f;
				ot[k] = 0.4f * r.nextFloat();
			}
			java.util.Arrays.fill(shift, run % 3 == 0 ? -1 : r.nextInt(5));
			PlateLayout.place(au, av, w, h, order, o0, o1, ot, obs, 0.02f, shift, ou, ov, leader);
			for (int i = 0; i < n; i++) {
				for (int k = 0; k < obs; k++) {
					boolean overU = o1[k] > ou[i] - w[i] / 2 && o0[k] < ou[i] + w[i] / 2;
					if (overU) assertTrue(ov[i] >= ot[k] + 0.02f - 1e-4, "run " + run + " plate " + i + " over content " + k);
				}
				for (int j = i + 1; j < n; j++) {
					boolean apart = Math.abs(ou[i] - ou[j]) >= (w[i] + w[j]) / 2 - 1e-4 || ov[i] >= ov[j] + h[j] - 1e-4 || ov[j] >= ov[i] + h[i] - 1e-4;
					assertTrue(apart, "run " + run + " plates " + i + "/" + j);
				}
			}
		}
	}

	@Test
	void theMemoryKeepsAPlateOnItsSideWhileItIsStillFree() {
		// two plates on one anchor: the second goes sideways; with memory it stays on the side it took last frame
		float[] au = {0, 0}, av = {0, 0}, w = {0.4f, 0.4f}, h = {0.2f, 0.2f}, ou = new float[2], ov = new float[2];
		boolean[] leader = new boolean[2];
		int[] shift = {-1, -1};
		float[] none = new float[0];
		PlateLayout.place(au, av, w, h, new int[] {0, 1}, none, none, none, 0, 0.02f, shift, ou, ov, leader);
		float first = ou[1];
		// pretend last frame chose the opposite side for plate 1 (index 4 = −0.5 w): still free → kept
		shift[1] = 4;
		PlateLayout.place(au, av, w, h, new int[] {0, 1}, none, none, none, 0, 0.02f, shift, ou, ov, leader);
		assertEquals(-0.2f, ou[1], 1e-6);
		assertTrue(first != ou[1] || first == -0.2f);
	}
}
