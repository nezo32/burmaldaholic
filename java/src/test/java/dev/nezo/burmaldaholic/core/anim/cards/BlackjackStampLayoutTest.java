package dev.nezo.burmaldaholic.core.anim.cards;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.anim.cards.CardLayout.Blackjack;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * v0.1.1: the hand stamps (BLACKJACK! / BUST / PUSH, tilted ±6°) never cover a seat plate (a bot's BLACKJACK! used to
 * hide the neighbouring bot's name), for every seat, plate width, hand size and EN / RU word. The full layout is one
 * canvas at GUI scales 2, 3 and 4 (the canvas is scaled as a whole); the compact layout draws no plates (§6.7).
 */
class BlackjackStampLayoutTest {
	/** Art widths (text + 14): BUST / PUSH, «ПЕРЕБОР», BLACKJACK! / «БЛЭКДЖЕК!», a long word. */
	private static final int[] ARTS = {40, 58, 70, 96};
	/** Plate widths: short name / sub-line up to the 84 px cap + 30. */
	private static final int[] PLATES = {44, 70, 96, 114};

	private static boolean hits(double cx, double cy, double[] half, int[] p) {
		return cx - half[0] < p[0] + p[2] && p[0] < cx + half[0] && cy - half[1] < p[1] + p[3] && p[1] < cy + half[1];
	}

	/** Stamp centres as BlackjackScreen#drawStamps computes them (canvas px): other seats, then the viewer's hands. */
	private static List<double[]> centres() {
		List<double[]> out = new ArrayList<>();
		for (int pos = 0; pos < 4; pos++) {
			for (int n = 2; n <= 5; n++) {
				int[] spot = Blackjack.spot(pos, false);
				int w = CardLayout.handWidth(n, CardLayout.M_W, Blackjack.OTHER_STEP);
				double x0 = CardLayout.TABLE_X + spot[0] - w / 2;
				double y0 = CardLayout.TABLE_Y + spot[1] - 48;
				out.add(new double[] {x0 + w / 2.0, y0 + 14, x0, y0, w, CardLayout.M_H});
			}
		}
		for (int n = 1; n <= 4; n++) {
			for (int active = 0; active < n; active++) {
				for (int h = 0; h < n; h++) {
					int size = Blackjack.handSize(h, n, active, false);
					int cw = size == 0 ? CardLayout.L_W : CardLayout.M_W;
					int ch = size == 0 ? CardLayout.L_H : CardLayout.M_H;
					int w = CardLayout.handWidth(2, cw, size == 0 ? 14 : 9);
					double x0 = CardLayout.TABLE_X + Blackjack.handX(h, n, active, 2, false);
					double y0 = CardLayout.TABLE_Y + Blackjack.handY(size, false);
					out.add(new double[] {x0 + w / 2.0, y0 + ch / 2.0, x0, y0, w, ch});
				}
			}
		}
		return out;
	}

	@Test
	void handStampsNeverCoverASeatPlate() {
		boolean coveredBefore = false;
		for (int pw : PLATES) {
			List<int[]> plates = new ArrayList<>();
			for (int pos = 0; pos < 4; pos++) plates.add(Blackjack.plateBox(pos, pw));
			for (double[] c : centres()) {
				for (int art : ARTS) {
					for (double deg : new double[] {-6, 6}) {
						double[] half = Blackjack.stampHalf(art, deg);
						for (int[] p : plates) coveredBefore |= hits(c[0], c[1], half, p);
						double cy = Blackjack.clearOfPlates(c[0], c[1], art, deg, plates);
						String at = "plate " + pw + ", art " + art + ", " + deg + "°, hand at (" + c[2] + ", " + c[3] + ") → cy " + cy;
						for (int[] p : plates) assertFalse(hits(c[0], cy, half, p), at + " covers the plate at " + java.util.Arrays.toString(p));
						// still on the felt and on (or right next to) its own hand
						assertTrue(cy - half[1] >= 0 && cy + half[1] <= CardLayout.CONSOLE_Y, at + " leaves the felt");
						assertTrue(cy + half[1] >= c[3] - 4 && cy - half[1] <= c[3] + c[5] + 4, at + " drifted off its hand");
					}
				}
			}
		}
		assertTrue(coveredBefore, "the unplaced stamps did cover a plate (the v0.1.0 bug)");
	}

	@Test
	void platesStayOnTheCanvas() {
		for (int pw : PLATES) {
			for (int pos = 0; pos < 4; pos++) {
				int[] p = Blackjack.plateBox(pos, pw);
				assertTrue(p[0] >= 2 && p[0] + p[2] <= CardLayout.CANVAS_W - 2, "plate " + pos + " off the canvas");
			}
		}
	}
}
