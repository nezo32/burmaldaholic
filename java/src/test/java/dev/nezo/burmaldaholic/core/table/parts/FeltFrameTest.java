package dev.nezo.burmaldaholic.core.table.parts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FeltFrameTest {
	@Test
	void fullFeltInsideTheRail() {
		for (TableGame g : TableGame.values()) {
			FeltFrame f = FeltFrame.of(g, TablePart.BACK_LEFT);
			assertFalse(f.compact());
			assertEquals(14 / 16.0, f.top(), 1e-12, "felt 14/16 (★3)");
			assertEquals(0.125, f.rail(), 1e-12);
			// felt stays inside the footprint minus the rail: x∈[-1+rail, 2-rail], z∈[-1+rail, 1-rail]
			assertTrue(f.cx() - f.w() / 2 >= -1 + f.rail() - 1e-9, g + " left");
			assertTrue(f.cx() + f.w() / 2 <= 2 - f.rail() + 1e-9, g + " right");
			assertTrue(f.cz() - f.d() / 2 >= -1 + f.rail() - 1e-9);
			assertTrue(f.cz() + f.d() / 2 <= 1 - f.rail() + 1e-9);
			assertTrue(f.onFelt(f.cx(), f.cz()));
			assertFalse(f.onFelt(-1.05, 0), "rail");
			assertEquals(f, FeltFrame.of(g, TablePart.MAIN), "every part sees the same frame");
		}
		FeltFrame bj = FeltFrame.of(TableGame.BLACKJACK, TablePart.MAIN);
		assertEquals(2.75, bj.w(), 1e-12);
		assertEquals(1.75, bj.d(), 1e-12);
		assertEquals(0.5, bj.cx(), 1e-12);
		assertEquals(0.0, bj.cz(), 1e-12);
		// crescent: front corner cut, back corner square
		assertFalse(bj.onFelt(-0.85, 0.85), "front-left corner rounded");
		assertTrue(bj.onFelt(-0.85, -0.85), "back-left corner square");
		FeltFrame poker = FeltFrame.of(TableGame.POKER, TablePart.MAIN);
		assertFalse(poker.onFelt(-0.85, -0.85), "oval back corner rounded");
		FeltFrame craps = FeltFrame.of(TableGame.CRAPS, TablePart.MAIN);
		assertTrue(craps.onFelt(-0.85, 0.85), "rect corner");
	}

	@Test
	void rouletteWheelInLeftColumn() {
		FeltFrame r = FeltFrame.of(TableGame.ROULETTE, TablePart.MAIN);
		assertTrue(r.hasWheel());
		assertEquals(-0.5, r.wheelCx(), 1e-12);
		assertEquals(0.0, r.wheelCz(), 1e-12);
		assertTrue(r.wheelDiameter() <= 1.5);
		assertTrue(r.wheelCx() - r.wheelDiameter() / 2 >= -1 - 1e-9 && r.wheelCx() + r.wheelDiameter() / 2 <= 0 + 1e-9, "bowl inside column r-1");
		assertTrue(r.onFelt(-0.5, 0), "bowl");
		assertEquals(1.875, r.w(), 1e-12);
		assertTrue(r.cx() - r.w() / 2 >= 0 - 1e-9, "layout on r0..1");
		assertEquals(2 - 0.125, r.cx() + r.w() / 2, 1e-12);
	}

	@Test
	void compactKeepsLegacyFelt() {
		FeltFrame c = FeltFrame.of(TableGame.BLACKJACK, TablePart.SINGLE);
		assertTrue(c.compact());
		assertEquals(1 + 1 / 64.0, c.top(), 1e-12);
		assertEquals(0.5, c.cx(), 1e-12);
		assertEquals(0.5, c.cz(), 1e-12);
		assertEquals(1.0, c.w(), 1e-12);
		assertFalse(c.hasWheel());
		assertEquals(0, c.outerCornerRadius(true), 1e-12);
	}

	@Test
	void partOrigins() {
		assertEquals(-1.0, FeltFrame.partOrigin(TablePart.BACK_LEFT)[0]);
		assertEquals(-1.0, FeltFrame.partOrigin(TablePart.BACK_LEFT)[1]);
		assertEquals(0.0, FeltFrame.partOrigin(TablePart.MAIN)[0]);
	}
}
