package dev.nezo.burmaldaholic.core.anim.motion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * PHASE2B §4 MK ArtScaleTest: the rows of PL §3.1, DI §4.1, RO §3.1 and CS §4.1, 4K at GUI 4/5/6/8/9 and the §2.4
 * matrix. Play-area sizes are the spec tables' values; for sizes the specs do not list, the layout's chrome per tier
 * (T1 54, T2 66, T3 82 px of height; 16 px of width) reproduces every listed row.
 */
class ArtScaleTest {
	// ---- PL §3.1: Plinko board 272×204 --------------------------------------------------------------------------

	@Test
	void plinkoRows() {
		// layout w, h, k, PlayArea h, expected n (NaN = fractional), board w, board h
		plinko(427, 240, 9, 186, 8, 242, 181);
		plinko(480, 270, 4, 204, 4, 272, 204);
		plinko(455, 256, 3, 190, Double.NaN, 253, 190);
		plinko(640, 360, 3, 278, 4, 363, 272);
		plinko(640, 360, 4, 278, 5, 340, 255);
		plinko(960, 540, 2, 458, 4, 544, 408);
		plinko(960, 540, 4, 458, 8, 544, 408);
		// §8.1-8 extra: 1280×720 at GUI 3 (427×240 k3) is fractional (r ≈ 2.7) and still fits
		ArtScale.Result r = ArtScale.pick(272, 204, 0.7 * 427, 186, 3);
		assertTrue(r.integral() || r.n() < 1 || Math.floor(r.r()) < 0.8 * r.r());
		assertTrue(r.fits(0.7 * 427, 186));
	}

	@Test
	void degenerateBoxesNeverGiveScaleZero() {
		double[][] boxes = {{0, 0}, {-5, 100}, {100, -1}, {Double.NaN, 100}, {100, Double.NaN}, {0.0001, 0.0001}};
		for (double[] b : boxes) {
			for (int k = 1; k <= 12; k++) {
				ArtScale.Result r = ArtScale.pick(272, 204, b[0], b[1], k);
				assertTrue(r.scale() > 0 && Double.isFinite(r.scale()), "scale > 0 for box " + b[0] + "x" + b[1]);
				assertTrue(r.n() >= ArtScale.MIN_RATIO);
			}
		}
	}

	private static void plinko(int w, int h, int k, double playH, double n, int bw, int bh) {
		double boxW = 0.7 * w; // columns 1–9 of the PlayArea: never the binding limit (spec: "width limit is checked too")
		ArtScale.Result r = ArtScale.pick(272, 204, boxW, playH, k);
		row("plinko " + w + "x" + h + " k" + k, r, boxW, playH, n, bw, bh);
	}

	// ---- DI §4.1: dice arena 360×140 ---------------------------------------------------------------------------

	@Test
	void diceRows() {
		dice(427, 240, 9, 411, 138, 8, 320, 124);
		dice(480, 270, 4, 464, 148, 4, 360, 140);
		dice(455, 256, 3, 439, 134, Double.NaN, 345, 134);
		dice(640, 360, 3, 624, 214, 4, 480, 187);
		dice(640, 360, 4, 624, 214, 6, 540, 210);
		dice(960, 540, 2, 936, 394, 5, 900, 350); // width-bound r ≈ 5.2
		dice(960, 540, 4, 936, 394, 10, 900, 350); // width-bound r ≈ 10.4
	}

	private static void dice(int w, int h, int k, double boxW, double boxH, double n, int aw, int ah) {
		ArtScale.Result r = ArtScale.pick(360, 140, boxW, boxH, k);
		row("dice " + w + "x" + h + " k" + k, r, boxW, boxH, n, aw, ah);
	}

	// ---- RO §3.1: focus wheel, native 104/144/208 × integer n ---------------------------------------------------

	private static final int[] WHEELS = {104, 144, 208};

	@Test
	void rouletteRows() {
		roulette(9, 186, 411, 144, 1, 9);
		roulette(4, 204, 464, 144, 1, 4);
		roulette(3, 190, 439, 144, 1, 3);
		roulette(3, 278, 624, 208, 1, 3);
		roulette(4, 278, 624, 208, 1, 4);
		roulette(2, 458, 936, 208, 2, 4);
		roulette(4, 458, 936, 208, 2, 8);
	}

	private static void roulette(int k, double playH, double playW, int size, int mult, int physPerArt) {
		double avail = Math.min(playH - 16, 0.55 * playW);
		ArtScale.Result r = ArtScale.pickNative(WHEELS, avail, k);
		String c = "roulette k" + k + " h" + playH;
		assertEquals(size, r.artW(), c);
		assertEquals(mult, r.scale(), 1e-12, c);
		assertEquals(physPerArt, r.physPerArt(), c);
		assertTrue(r.integral(), c);
		assertTrue(r.fits(avail, avail), c);
	}

	// ---- CS §4.1: craps dice cells drawn 1:1 ---------------------------------------------------------------------

	@Test
	void crapsRows() {
		craps(9, 14);
		craps(3, 18);
		craps(4, 18);
		craps(3, 22);
		craps(4, 22);
		craps(2, 30);
		craps(4, 30);
	}

	private static void craps(int k, int cell) {
		ArtScale.Result r = ArtScale.pick(cell, cell, cell, cell, k);
		String c = "craps cell " + cell + " k" + k;
		assertTrue(r.integral(), c);
		assertEquals(k, r.physPerArt(), c); // one art px = one layout px = k physical px
		assertEquals(cell * k, r.physPerArt() * cell, c); // die width cell·G physical px (CS §9.3 crispness)
		assertEquals(cell, r.w(), 1e-12, c);
	}

	// ---- 4K GUI 4/5/6/8/9 and the §2.4 matrix ---------------------------------------------------------------------

	@Test
	void fourKAtEveryGuiScale() {
		// 3840×2160: GUI 4 → 960×540 k4, 5 → 768×432 k5, 6 → 640×360 k6, 8 → 480×270 k8, 9 → 427×240 k9 (PHASE2B §2.2.1)
		int[][] cells = {{960, 540, 4}, {768, 432, 5}, {640, 360, 6}, {480, 270, 8}, {427, 240, 9}};
		for (int[] c : cells) everyStage(c[0], c[1], c[2]);
		// the owner's 4K GUI 9: Plinko 8 phys px per art px, dice 8, roulette focus 144 at 9
		assertEquals(8, ArtScale.pick(272, 204, 0.7 * 427, playH(240), 9).physPerArt());
		assertEquals(8, ArtScale.pick(360, 140, 411, 138, 9).physPerArt());
		// 4K GUI 5 (768×432, T3): Plinko r = 350·5/204 = 8.58 → 8
		ArtScale.Result p5 = ArtScale.pick(272, 204, 0.7 * 768, playH(432), 5);
		assertTrue(p5.integral());
		assertEquals(8, p5.physPerArt());
	}

	@Test
	void resolutionMatrix() {
		// PHASE2B §2.4: (layout w, h, k); framed cells use the 1067×600 frame
		int[][] cells = {
			{427, 240, 3}, {640, 360, 2}, // 1280×720 Auto, GUI2
			{480, 270, 4}, {960, 540, 2}, {640, 360, 3}, // 1920×1080 Auto, GUI2, GUI3
			{427, 240, 6}, {1067, 600, 2}, {854, 480, 3}, {640, 360, 4}, // 2560×1440 Auto, GUI2 (frame), GUI3, GUI4
			{427, 240, 9}, {960, 540, 4}, {1067, 600, 3}, {640, 360, 6}, // 3840×2160 Auto, GUI2/GUI4, GUI3 (frame), GUI6
			{455, 256, 3}, {854, 480, 2}, {683, 384, 2}, {640, 512, 2}}; // extras: 1366×768 GUI3, 854×480, 1366×768 GUI2, 1280×1024 GUI2
		for (int[] c : cells) everyStage(c[0], c[1], c[2]);
	}

	/** Every stage at one layout size: fits, integer n whenever floor(r) ≥ 0.8·r, origin on 1/k, crisp art columns. */
	private static void everyStage(int w, int h, int k) {
		double ph = playH(h);
		double pw = w - 16;
		check("plinko", ArtScale.pick(272, 204, 0.7 * w, ph, k), 0, 0.7 * w, ph);
		check("dice", ArtScale.pick(360, 140, pw, ph - 48, k), 0, pw, ph - 48);
		double avail = Math.min(ph - 16, 0.55 * pw);
		ArtScale.Result wheel = ArtScale.pickNative(WHEELS, avail, k);
		check("wheel", wheel, 0, avail, avail);
		int cell = h >= 450 ? 30 : h >= 360 ? 22 : h >= 256 ? 18 : 14;
		check("craps", ArtScale.pick(cell, cell, cell, cell, k), 0, cell, cell);
	}

	private static void check(String what, ArtScale.Result r, double boxX, double boxW, double boxH) {
		String c = what + " k" + r.k() + " box " + boxW + "x" + boxH;
		assertTrue(r.fits(boxW, boxH), c + " fits");
		double fl = Math.floor(r.r());
		if (fl >= 1 && fl >= ArtScale.INTEGER_KEEP * r.r()) {
			assertTrue(r.integral(), c + " integral");
		}
		if (r.integral()) {
			assertEquals(Math.rint(r.n()), r.n(), 0, c + " n integer");
			// art column boundaries land on physical px: origin·k and n are integers
			double ox = r.originX(boxX, boxW);
			assertEquals(Math.rint(ox * r.k()), ox * r.k(), 1e-9, c + " origin on 1/k");
			for (int i = 0; i <= r.artW(); i += 7) {
				double edge = (ox + i * r.scale()) * r.k();
				assertEquals(Math.rint(edge), edge, 1e-6, c + " column " + i);
			}
		}
		double ox = r.originX(boxX, boxW);
		double oy = r.originY(0, boxH);
		assertTrue(ox >= boxX - 1e-9 && ox + r.w() <= boxX + boxW + 1e-9, c + " inside x");
		assertTrue(oy >= -1e-9 && oy + r.h() <= boxH + 1e-9, c + " inside y");
		assertTrue(Math.abs((ox - boxX) - (boxX + boxW - ox - r.w())) <= 2.0 / r.k() + 1e-9, c + " centred");
	}

	private static double playH(int h) {
		int chrome = h < 256 ? 54 : h < 360 ? 66 : 82;
		return h - chrome;
	}

	private static void row(String c, ArtScale.Result r, double boxW, double boxH, double n, int w, int h) {
		if (Double.isNaN(n)) {
			assertFalse(r.integral(), c + " fractional");
			assertEquals(r.r(), r.n(), 1e-12, c);
		} else {
			assertTrue(r.integral(), c + " integral");
			assertEquals(n, r.n(), 0, c + " n");
		}
		assertEquals(w, r.w(), 1.0, c + " w");
		assertEquals(h, r.h(), 1.0, c + " h");
		assertTrue(r.fits(boxW, boxH), c + " fits");
		double ox = r.originX(10, boxW);
		assertEquals(Math.rint(ox * r.k()), ox * r.k(), 1e-9, c + " origin snapped");
	}
}
