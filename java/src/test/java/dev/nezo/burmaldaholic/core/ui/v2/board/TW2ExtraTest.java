package dev.nezo.burmaldaholic.core.ui.v2.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.table.parts.FeltFrame;
import dev.nezo.burmaldaholic.core.table.parts.TableFootprint;
import dev.nezo.burmaldaholic.core.table.parts.TableGame;
import dev.nezo.burmaldaholic.core.table.parts.TablePart;
import dev.nezo.burmaldaholic.core.ui.v2.board.BoardGeometry.Felt;
import dev.nezo.burmaldaholic.core.ui.v2.board.BoardGeometry.Holdem;
import dev.nezo.burmaldaholic.games.blackjack.present.BlackjackWorld;
import dev.nezo.burmaldaholic.games.roulette.present.RouletteWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/** Tester T-W2 adversarial geometry cases (findings/T-W2.md), kept as regression tests. Each collects all failures. */
class TW2ExtraTest {
	private static Felt designFelt(TableGame g) {
		return switch (g.shape()) {
			case CRESCENT -> Felt.CRESCENT;
			case OVAL -> Felt.OVAL;
			case RECT, ROULETTE -> Felt.RECT;
		};
	}

	private static FullTableMap map(TableGame g) {
		return FullTableMap.of(FeltFrame.of(g, TablePart.MAIN), designFelt(g), RouletteWorld.WHEEL_X, RouletteWorld.WHEEL_Z, RouletteWorld.WHEEL_R);
	}

	private static void report(List<String> fails) {
		if (!fails.isEmpty()) {
			StringBuilder b = new StringBuilder(fails.size() + " failures:\n");
			for (int i = 0; i < Math.min(40, fails.size()); i++) b.append("  ").append(fails.get(i)).append('\n');
			throw new AssertionError(b.toString());
		}
	}

	/** {fx, fz, yRot} for S, W, N, E (Direction.toYRot). */
	private static final int[][] FACINGS = {{0, 1, 0}, {-1, 0, 90}, {0, -1, 180}, {1, 0, 270}};

	/** BoardRenderer's pose (rotateAround Y −yRot about (0.5, 0, 0.5)) must agree with MT's TableFootprint.toWorld. */
	@Test
	void facingRotationMatchesFootprintToWorld() {
		List<String> fails = new ArrayList<>();
		for (int[] fc : FACINGS) {
			Matrix4f m = new Matrix4f().rotateAround(new Quaternionf().rotationY((float) Math.toRadians(-fc[2])), 0.5f, 0f, 0.5f);
			for (float x = -1.5f; x <= 2.5f; x += 0.25f) {
				for (float z = -1.5f; z <= 1.5f; z += 0.25f) {
					Vector3f v = m.transformPosition(new Vector3f(x, 0.9f, z));
					double[] w = TableFootprint.toWorld(x, z, fc[0], fc[1]);
					if (Math.abs(v.x - w[0]) > 1e-4 || Math.abs(v.z - w[1]) > 1e-4)
						fails.add("yRot " + fc[2] + " (" + x + "," + z + "): pose " + v.x + "," + v.z + " vs toWorld " + w[0] + "," + w[1]);
				}
			}
			// round trip
			double[] w = TableFootprint.toWorld(1.7, -0.6, fc[0], fc[1]);
			double[] l = TableFootprint.toLocal(w[0], w[1], fc[0], fc[1]);
			if (Math.abs(l[0] - 1.7) > 1e-9 || Math.abs(l[1] + 0.6) > 1e-9) fails.add("toLocal round trip " + fc[2]);
		}
		report(fails);
	}

	/** Plates of every table game (incl. roulette) and 1..10 seats: on the rail, off the felt, not over the floor. */
	@Test
	void platesOnRailEveryGameEverySeatCount() {
		List<String> fails = new ArrayList<>();
		for (TableGame g : TableGame.values()) {
			FeltFrame f = FeltFrame.of(g, TablePart.MAIN);
			Felt felt = designFelt(g);
			FullTableMap m = map(g);
			for (int n = 1; n <= 10; n++) {
				for (int s = 0; s < n; s++) {
					float[] spot = felt == Felt.OVAL ? Holdem.seat(s, n, Holdem.SEAT_R) : felt == Felt.CRESCENT ? BoardGeometry.crescentSeat(s, n)
						: new float[] {0.5f + (s - (n - 1) / 2f) * 1.6f / Math.max(1, n), 1.0f};
					float[] d = BoardGeometry.plateAnchor(felt, spot[0], spot[1]);
					float[] a = m.plateAnchor(f, felt, d[0], d[2]);
					String what = g + " " + n + "s seat " + s + " @" + a[0] + "," + a[2];
					if (!Float.isFinite(a[0]) || !Float.isFinite(a[2])) fails.add(what + " NaN");
					if (f.onFelt(a[0], a[2])) fails.add(what + " on felt");
					// outside the whole 3x2 footprint = over the floor
					if (a[0] < -1 - 1e-4 || a[0] > 2 + 1e-4 || a[2] < -1 - 1e-4 || a[2] > 1 + 1e-4) fails.add(what + " over the floor (outside 3x2 footprint)");
				}
			}
		}
		report(fails);
	}

	/** The plate (rail anchor) of a seat should be on the same side of the table as MT's seat point where the player stands. */
	@Test
	void plateSideMatchesMtSeatPoint() {
		List<String> fails = new ArrayList<>();
		for (TableGame g : TableGame.values()) {
			if (g == TableGame.ROULETTE || g == TableGame.CRAPS) continue; // their seat spots belong to RO / CM renderers
			FeltFrame f = FeltFrame.of(g, TablePart.MAIN);
			Felt felt = designFelt(g);
			FullTableMap m = map(g);
			for (int n = 2; n <= 7; n++) {
				for (int s = 0; s < n; s++) {
					float[] spot = felt == Felt.OVAL ? Holdem.seat(s, n, Holdem.SEAT_R) : BoardGeometry.crescentSeat(s, n);
					float[] d = BoardGeometry.plateAnchor(felt, spot[0], spot[1]);
					float[] a = m.plateAnchor(f, felt, d[0], d[2]);
					double[] p = TableFootprint.seatPoint(g, TablePart.MAIN, s, n);
					double dist = Math.hypot(a[0] - p[0], a[2] - p[1]);
					if (dist > 0.6) fails.add(g + " " + n + "s seat " + s + ": plate " + a[0] + "," + a[2] + " vs player " + p[0] + "," + p[1] + " d=" + dist);
				}
			}
		}
		report(fails);
	}

	/** Hold'em with 1 seat (n clamps differ: W2 max(2,n), MT max(1,n)). */
	@Test
	void holdemOneSeatMatchesMt() {
		FeltFrame f = FeltFrame.of(TableGame.POKER, TablePart.MAIN);
		FullTableMap m = map(TableGame.POKER);
		float[] c = Holdem.seat(0, 1, Holdem.SEAT_R);
		double[] p = TableFootprint.seatPoint(TableGame.POKER, TablePart.MAIN, 0, 1);
		double a1 = Math.atan2(m.footZ(c[1]) - f.cz(), m.footX(c[0]) - f.cx()), a2 = Math.atan2(p[1] - f.cz(), p[0] - f.cx());
		double diff = Math.toDegrees(Math.abs(Math.IEEEremainder(a1 - a2, 2 * Math.PI)));
		assertTrue(diff < 16, "1 seat: cards " + Math.toDegrees(a1) + " vs player " + Math.toDegrees(a2));
	}

	/** Blackjack marks / UTH spot groups / baccarat texts land on the felt for 1..10 seats (FeltPrint positions). */
	@Test
	void feltPrintRuntimeSpotsOnFelt() {
		List<String> fails = new ArrayList<>();
		FeltFrame f = FeltFrame.of(TableGame.BLACKJACK, TablePart.MAIN);
		FullTableMap m = map(TableGame.BLACKJACK);
		for (int seats = 1; seats <= 10; seats++) {
			int n = Math.max(5, seats);
			for (int i = 0; i < n; i++) {
				float[] st = BlackjackWorld.stack(BlackjackWorld.seat(i, n));
				for (int k = 0; k < 8; k++) {
					double ang = Math.PI * k / 4;
					float x = st[0] + 0.115f * (float) Math.cos(ang), z = st[1] + 0.115f * (float) Math.sin(ang);
					if (!f.onFelt(m.footX(x), m.footZ(z))) fails.add("BJ mark " + n + "s seat " + i + " off felt");
				}
			}
		}
		FullTableMap mu = map(TableGame.UTH);
		FeltFrame fu = FeltFrame.of(TableGame.UTH, TablePart.MAIN);
		float[] g = new float[12], q = new float[12];
		for (int n = 1; n <= 10; n++) {
			for (int i = 0; i < n; i++) {
				// the spot groups FeltPrint draws (BoardGeometry.uthSpots: x, z, half size x ante, blind, trips, play)
				BoardGeometry.uthSpots(i, n, g);
				for (int s = 0; s < 4; s++)
					for (int k = 0; k < 4; k++) {
						float x = g[s * 3] + ((k & 1) == 0 ? -g[s * 3 + 2] : g[s * 3 + 2]), z = g[s * 3 + 1] + ((k & 2) == 0 ? -g[s * 3 + 2] : g[s * 3 + 2]);
						if (!fu.onFelt(mu.footX(x), mu.footZ(z))) fails.add("UTH mark " + n + "s seat " + i + " off felt at " + x + "," + z);
					}
				// neighbouring seats' groups must not overlap
				if (i > 0 && n <= 6) {
					BoardGeometry.uthSpots(i - 1, n, q);
					for (int s1 = 0; s1 < 4; s1++)
						for (int s2 = 0; s2 < 4; s2++) {
							float r1 = g[s1 * 3 + 2], r2 = q[s2 * 3 + 2];
							float ov = Math.min(g[s1 * 3] + r1, q[s2 * 3] + r2) - Math.max(g[s1 * 3] - r1, q[s2 * 3] - r2);
							float oz = Math.min(g[s1 * 3 + 1] + r1, q[s2 * 3 + 1] + r2) - Math.max(g[s1 * 3 + 1] - r1, q[s2 * 3 + 1] - r2);
							if (ov > 0 && oz > 0) fails.add("UTH " + n + "s seats " + (i - 1) + "/" + i + " marks overlap " + ov + " x " + oz + " (design)");
						}
				}
			}
		}
		// baccarat straight texts (centre x, z, width)
		FullTableMap mb = map(TableGame.BACCARAT);
		FeltFrame fb = FeltFrame.of(TableGame.BACCARAT, TablePart.MAIN);
		float[][] bac = {{0.10f, 0.62f, 0.40f}, {0.90f, 0.62f, 0.40f}, {0.50f, 0.70f, 0.26f}, {-0.32f, 0.52f, 0.26f}, {1.32f, 0.52f, 0.26f}};
		for (float[] t : bac)
			for (int k = -1; k <= 1; k += 2)
				if (!fb.onFelt(mb.footX(t[0] + k * t[2] / 2), mb.footZ(t[1]))) fails.add("baccarat text at " + t[0] + " end off felt");
		report(fails);
	}

	/** Every game's flat layers keep ≥ 1/2048 world gaps; the compact table too (scale = contentScale of each felt). */
	@Test
	void compactLayersGap() {
		List<String> fails = new ArrayList<>();
		for (Felt w : new Felt[] {Felt.CRESCENT, Felt.RECT, Felt.OVAL}) {
			float k = BoardGeometry.contentScale(w, Felt.FALLBACK);
			float[] y = {0, BoardGeometry.MARK_Y, BoardGeometry.TEXT_Y, BoardGeometry.SHADOW_Y, BoardGeometry.CHIP_SHADOW_Y, BoardGeometry.CHIP_BASE_Y,
				BoardGeometry.CARD_LIFT};
			for (int i = 1; i < y.length; i++) if ((y[i] - y[i - 1]) * k < 1 / 2048.0) fails.add("compact " + w + " scale " + k + " layer " + i + " gap " + (y[i] - y[i - 1]) * k);
		}
		report(fails);
	}

	/** Extreme pitch / distance / FOV / window heights: finite, positive, never above the cap. */
	@Test
	void holoScaleExtremes() {
		List<String> fails = new ArrayList<>();
		float[] pitches = {-90, -45, 0, 54.9f, 55, 60, 64.99f, 65, 89.9f, 90};
		float[] dists = {0, 0.01f, 0.5f, 1.2f, 1.5f, 1.8f, 3, 8, 64, 1e6f};
		for (float p : pitches)
			for (float d : dists) {
				float a = HoloScale.boardFade(p, d);
				if (!(a >= 0 && a <= 1)) fails.add("boardFade(" + p + "," + d + ")=" + a);
			}
		if (HoloScale.boardFade(-90, 5) != 1f) fails.add("looking up should be fully visible");
		int[] hs = {240, 480, 720, 1080, 1440, 2160, 4320};
		double[] fovs = {1.5, 30, 70, 110, 178};
		double[] ds = {0, 0.1, 0.5, 1, 3, 5, 8, 12, 32, 500};
		for (int h : hs)
			for (double fov : fovs)
				for (double d : ds) {
					for (float base : new float[] {HoloScale.BOARD_READ, HoloScale.PLATE_READ}) {
						float px = HoloScale.readablePx(base, d, fov, h);
						if (!(px > 0) || !Float.isFinite(px)) fails.add("readablePx NaN/≤0 h" + h + " fov" + fov + " d" + d);
						double cap = HoloScale.capPx(px, Math.max(d, 0.5), fov, h);
						if (cap > HoloScale.capMax(h) + 1e-3) fails.add("cap exceeded h" + h + " fov" + fov + " d" + d + ": " + cap);
					}
				}
		report(fails);
	}

	/** Readability at every resolution: the 1080p-equivalent cap at 3..8 blocks meets the target (UI every resolution). */
	@Test
	void readableEquallyAtEveryResolution() {
		List<String> fails = new ArrayList<>();
		for (int h : new int[] {720, 1080, 1440, 2160}) {
			for (double d = 3; d <= 8; d += 0.5) {
				float px = HoloScale.readablePx(HoloScale.PLATE_READ, d, 70, h);
				double eq1080 = HoloScale.capPx(px, d, 70, h) * 1080.0 / h;
				if (eq1080 < HoloScale.capTarget(d) - 0.05) fails.add("h" + h + " d" + d + ": cap " + String.format("%.1f", eq1080) + " px@1080-eq < target "
					+ String.format("%.1f", HoloScale.capTarget(d)));
			}
		}
		report(fails);
	}

	/** LOD hysteresis: oscillating ±0.9 around each boundary never flips once settled. */
	@Test
	void lodNoFlicker() {
		List<String> fails = new ArrayList<>();
		for (double b : new double[] {8, 16, 32}) {
			for (double start : new double[] {b - 0.95, b + 0.95}) {
				HoloScale.Lod l = HoloScale.lod(start, null);
				for (int i = 0; i < 50; i++) {
					double d = b + ((i & 1) == 0 ? 0.9 : -0.9);
					HoloScale.Lod n = HoloScale.lod(d, l);
					if (i > 0 && n != l) fails.add("flip at " + b + " d=" + d + " " + l + "->" + n);
					l = n;
				}
			}
			// and a clear move past the margin does switch
			if (HoloScale.lod(b + 1.1, HoloScale.lod(b - 0.5)) == HoloScale.lod(b - 0.5)) fails.add("no switch past margin at " + b);
		}
		report(fails);
	}

	/** PlateLayout.place: random scenes, placed plates never overlap (gap respected), outputs finite. */
	@Test
	void placeDisjointRandom() {
		Random r = new Random(42);
		PlateLayout.Scratch sc = new PlateLayout.Scratch(16);
		List<String> fails = new ArrayList<>();
		for (int scene = 0; scene < 3000; scene++) {
			int n = 1 + r.nextInt(12);
			float[] au = new float[n], av = new float[n], w = new float[n], h = new float[n], ou = new float[n], ov = new float[n];
			int[] order = new int[n];
			int[] shift = new int[n];
			boolean[] lead = new boolean[n];
			for (int i = 0; i < n; i++) {
				au[i] = (r.nextFloat() - 0.5f) * (scene % 3 == 0 ? 0.01f : 3);
				av[i] = r.nextFloat() * 0.5f;
				w[i] = 0.2f + r.nextFloat() * 0.6f;
				h[i] = 0.08f + r.nextFloat() * 0.1f;
				order[i] = i;
				shift[i] = r.nextInt(6) - 1;
			}
			int obs = r.nextInt(5);
			float[] o0 = new float[obs], o1 = new float[obs], ot = new float[obs];
			for (int k = 0; k < obs; k++) {
				o0[k] = (r.nextFloat() - 0.5f) * 3;
				o1[k] = o0[k] + r.nextFloat();
				ot[k] = r.nextFloat();
			}
			float gap = 0.02f;
			PlateLayout.place(au, av, w, h, order, n, o0, o1, ot, obs, gap, shift, ou, ov, lead, sc);
			for (int i = 0; i < n; i++) {
				if (!Float.isFinite(ou[i]) || !Float.isFinite(ov[i])) fails.add("scene " + scene + " NaN");
				for (int k = 0; k < obs; k++)
					if (o1[k] > ou[i] - w[i] / 2 - gap && o0[k] < ou[i] + w[i] / 2 + gap && ov[i] < ot[k] + gap - 1e-4) fails.add("scene " + scene + " plate " + i + " under obstacle " + k);
				for (int j = 0; j < i; j++)
					if (PlateLayout.overlaps(ou[i], ov[i], w[i], h[i], ou[j], ov[j], w[j], h[j], gap - 1e-4f)) fails.add("scene " + scene + " plates " + j + "/" + i + " overlap");
			}
		}
		report(fails);
	}

	/** resolveGlobal with degenerate input: an empty table, NaN rects, many tables. */
	@Test
	void resolveGlobalDegenerate() {
		List<PlateLayout.TablePlates> ts = new ArrayList<>();
		PlateLayout.TablePlates a = new PlateLayout.TablePlates();
		a.rank = 1;
		a.key = 1;
		ts.add(a);
		PlateLayout.TablePlates b = new PlateLayout.TablePlates();
		b.rank = 0;
		b.key = 2;
		b.add(1, 0, 0, 1, 1, true);
		for (int i = 0; i < 20; i++) b.add(10 + i, 0, 0, 1, 1, true); // past MAX: ignored
		ts.add(b);
		PlateLayout.resolveGlobal(ts, 0.01f);
		assertEquals(PlateLayout.TablePlates.MAX, b.count);
		assertTrue(b.shown(1, false));
		assertFalse(b.shown(10, true), "second coincident plate of the same table is hidden (its own layout should prevent this)");
	}
}
