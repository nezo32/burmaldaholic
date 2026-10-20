package dev.nezo.burmaldaholic.worldgen.logic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** T-MT D2: v0.1 generated table positions are found back from a casino record's layout id and rotated box. */
class LegacyHouseTablesTest {
	@Test
	void generatedPositionsInEveryRotation() {
		Vec origin = new Vec(100, 64, -300);
		Vec size = new Vec(17, 10, 17);
		String id = CasinoRecord.idFor("minecraft:overworld", origin.x(), origin.y(), origin.z());
		for (int q = 0; q < 4; q++) {
			Geometry.Box box = Geometry.templateBox(origin, size, q);
			Vec bj = Geometry.toWorld(origin, new Vec(4, 1, 10), q);
			Vec baccarat = Geometry.toWorld(origin, new Vec(12, 1, 13), q);
			Vec aisle = Geometry.toWorld(origin, new Vec(8, 1, 10), q);
			assertTrue(LegacyHouseTables.generatedAt("village_casino_plains", box, id, bj.x(), bj.y(), bj.z()), "blackjack q" + q);
			assertTrue(LegacyHouseTables.generatedAt("village_casino_snowy", box, id, baccarat.x(), baccarat.y(), baccarat.z()), "baccarat q" + q);
			assertFalse(LegacyHouseTables.generatedAt("village_casino_plains", box, id, aisle.x(), aisle.y(), aisle.z()), "aisle q" + q);
			assertFalse(LegacyHouseTables.generatedAt("village_casino_plains", box, id, bj.x(), bj.y() + 1, bj.z()), "one block up q" + q);
		}
		// the real rotation only: a q=1 position does not count in a q=0 casino (origin from the record id)
		Geometry.Box box0 = Geometry.templateBox(origin, size, 0);
		Vec rotated = Geometry.toWorld(origin, new Vec(4, 1, 10), 2);
		Vec straight = Geometry.toWorld(origin, new Vec(4, 1, 10), 0);
		if (!rotated.equals(straight)) {
			assertFalse(LegacyHouseTables.generatedAt("village_casino_plains", box0, id, rotated.x(), rotated.y(), rotated.z()));
		}
		Geometry.Box parlor = Geometry.templateBox(origin, new Vec(21, 12, 21), 1);
		Vec craps = Geometry.toWorld(origin, new Vec(6, 1, 6), 1);
		assertTrue(LegacyHouseTables.generatedAt("piglin_parlor", parlor, id, craps.x(), craps.y(), craps.z()));
		Geometry.Box lounge = Geometry.templateBox(origin, new Vec(15, 9, 15), 3);
		Vec uth = Geometry.toWorld(origin, new Vec(10, 1, 10), 3);
		assertTrue(LegacyHouseTables.generatedAt("high_roller_lounge", lounge, id, uth.x(), uth.y(), uth.z()));
		assertFalse(LegacyHouseTables.generatedAt("grand_casino", lounge, id, uth.x(), uth.y(), uth.z()), "v0.2 layouts: no legacy positions");
		assertFalse(LegacyHouseTables.generatedAt(null, lounge, id, uth.x(), uth.y(), uth.z()));
	}

	@Test
	void originFromRecordId() {
		Vec o = LegacyHouseTables.originOf(CasinoRecord.idFor("minecraft:the_nether", -12, 70, 400));
		assertTrue(o != null && o.equals(new Vec(-12, 70, 400)));
		assertTrue(LegacyHouseTables.originOf("gametest/whatever") == null);
	}
}
