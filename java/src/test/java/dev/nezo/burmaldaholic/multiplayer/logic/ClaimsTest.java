package dev.nezo.burmaldaholic.multiplayer.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.multiplayer.logic.CasinoBook.Casino;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimsTest {
	private static final String OW = "minecraft:overworld";
	private static final String NETHER = "minecraft:the_nether";
	private final UUID a = UUID.randomUUID();
	private final UUID b = UUID.randomUUID();

	private Casino casino(String id, UUID owner, int x, int z, int radius, String dim) {
		return new Casino(id, owner, "n", dim, x, 64, z, radius, 0);
	}

	private static Claims.Rules rules(int radius, int max) {
		return new Claims.Rules(true, radius, max, null, 0, 0, 0);
	}

	@Test
	void claimIsAFullHeightCylinderPerDimension() {
		Casino c = casino("c1", a, 100, 100, 24, OW);
		assertTrue(Claims.inClaim(c, OW, 124, 100));
		assertFalse(Claims.inClaim(c, OW, 125, 100));
		assertFalse(Claims.inClaim(c, NETHER, 100, 100));
		// diagonal edge: 16² + 17² = 545 ≤ 576, 17² + 17² = 578 > 576
		Casino o = casino("c2", a, 0, 0, 24, OW);
		assertTrue(Claims.inClaim(o, OW, 16, 17));
		assertFalse(Claims.inClaim(o, OW, 17, 17));
		assertTrue(Claims.inClaim(o, OW, -16, -17));
	}

	@Test
	void claimAtFindsTheContainingCasino() {
		List<Casino> list = List.of(casino("c1", a, 0, 0, 24, OW), casino("c2", b, 100, 0, 24, OW));
		assertEquals("c2", Claims.claimAt(list, OW, 90, 5).orElseThrow().id);
		assertTrue(Claims.claimAt(list, OW, 50, 0).isEmpty());
	}

	@Test
	void overlapsAreRejected() {
		List<Casino> list = List.of(casino("c1", a, 0, 0, 24, OW));
		assertEquals(Claims.Check.OVERLAP, Claims.check(list, b, OW, 47, 0, rules(24, 1)));
		assertEquals(Claims.Check.OK, Claims.check(list, b, OW, 48, 0, rules(24, 1)));
		assertEquals(Claims.Check.OK, Claims.check(list, b, NETHER, 0, 0, rules(24, 1)));
		// different radii: 24 + 10 = 34
		assertEquals(Claims.Check.OVERLAP, Claims.check(list, b, OW, 33, 0, rules(10, 1)));
		assertEquals(Claims.Check.OK, Claims.check(list, b, OW, 34, 0, rules(10, 1)));
	}

	@Test
	void spawnProtectionOnlyInTheSpawnDimension() {
		Claims.Rules r = new Claims.Rules(true, 24, 1, OW, 0, 0, 16);
		assertEquals(Claims.Check.OVERLAP, Claims.check(List.of(), a, OW, 39, 0, r));
		assertEquals(Claims.Check.OK, Claims.check(List.of(), a, OW, 40, 0, r));
		assertEquals(Claims.Check.OK, Claims.check(List.of(), a, NETHER, 0, 0, r));
	}

	@Test
	void limitAndDisabled() {
		List<Casino> list = List.of(casino("c1", a, 1000, 1000, 24, OW));
		assertEquals(Claims.Check.LIMIT, Claims.check(list, a, OW, 0, 0, rules(24, 1)));
		assertEquals(Claims.Check.OK, Claims.check(list, a, OW, 0, 0, rules(24, 2)));
		assertEquals(Claims.Check.DISABLED, Claims.check(List.of(), a, OW, 0, 0, rules(24, 0)));
		assertEquals(Claims.Check.DISABLED, Claims.check(List.of(), a, OW, 0, 0, new Claims.Rules(false, 24, 1, null, 0, 0, 0)));
	}

	@Test
	void breakPermissions() {
		assertTrue(Claims.mayBreak(true, false, true, true));
		assertTrue(Claims.mayBreak(false, true, true, false));
		assertFalse(Claims.mayBreak(false, false, true, false));
		assertTrue(Claims.mayBreak(false, false, false, false));
		assertFalse(Claims.mayBreak(false, false, false, true), "the charter is always protected");
	}

	@Test
	void idsAndBankrolls() {
		assertEquals("c1", Claims.nextId(0));
		assertEquals("c42", Claims.nextId(41));
		assertEquals("multiplayer:charter/c7", Claims.bankrollId("c7"));
	}
}
