package dev.nezo.burmaldaholic.worldgen.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/** DL roles: marker round trip, the type per dimension, the respawn delay. */
class NpcRoleDlTest {
	@Test
	void markersRoundTrip() {
		for (NpcRole r : List.of(NpcRole.HOUSE_DEALER, NpcRole.ENDER_CROUPIER, NpcRole.PIT_BOSS, NpcRole.MERCHANT)) {
			for (Facing f : Facing.values()) {
				Markers.Npc m = new Markers.Npc(r, f);
				assertEquals(m, Markers.parse(m.metadata()).orElseThrow());
			}
			assertEquals(r, NpcRole.byId(r.id()));
		}
	}

	@Test
	void typesFollowTheDimension() {
		assertEquals(List.of("burmaldaholic:house_dealer"), NpcRole.HOUSE_DEALER.entityIds("minecraft:overworld"));
		assertEquals(List.of("burmaldaholic:piglin_house_dealer"), NpcRole.HOUSE_DEALER.entityIds("minecraft:the_nether"));
		assertEquals(List.of("burmaldaholic:ender_house_dealer"), NpcRole.HOUSE_DEALER.entityIds("minecraft:the_end"));
		assertEquals(List.of("burmaldaholic:merchant"), NpcRole.MERCHANT.entityIds("some:modded_dimension"));
		assertEquals(List.of("burmaldaholic:piglin_merchant"), NpcRole.MERCHANT.entityIds("minecraft:the_nether"));
		assertEquals(List.of("burmaldaholic:ender_merchant"), NpcRole.MERCHANT.entityIds("minecraft:the_end"));
		assertEquals(NpcRole.CROUPIER.entityIds(), NpcRole.CROUPIER.entityIds("minecraft:the_end"));
	}

	@Test
	void dealersComeBackFast() {
		assertEquals(RespawnRule.MIN_MISSING_TICKS, NpcRole.HOUSE_DEALER.respawnTicks(24000));
		assertEquals(RespawnRule.MIN_MISSING_TICKS, NpcRole.MERCHANT.respawnTicks(24000));
		assertEquals(24000, NpcRole.LOAN_SHARK.respawnTicks(24000));
	}

	@Test
	void shopsAndGreetings() {
		assertEquals(NpcShop.SHULKER_CROUPIER, NpcShop.of(NpcRole.ENDER_CROUPIER));
		assertEquals(NpcShop.CROUPIER, NpcShop.of(NpcRole.PIT_BOSS));
		assertEquals("dialog.burmaldaholic.merchant.greeting.2", NpcShop.greetingKey(NpcRole.MERCHANT, 5));
	}

	/** CR-2/DL: v0.2 pieces carry one house-dealer marker per dealer cell and no old table-dealer markers. */
	@Test
	void v2LayoutsHaveOneHouseDealerPerCellAndNoOldDealers() {
		int dealers = 0;
		for (Layout l : Layouts.all().values()) {
			if (!l.id().startsWith("casino/")) {
				continue;
			}
			java.util.Set<Vec> cells = new java.util.HashSet<>();
			java.util.Set<Vec> houseCells = new java.util.HashSet<>();
			int merchants = 0;
			for (Layout.PlacedMarker m : l.markers()) {
				if (!(m.marker() instanceof Markers.Npc npc)) {
					continue;
				}
				org.junit.jupiter.api.Assertions.assertNotEquals(NpcRole.BACCARAT_DEALER, npc.role(), l.id() + ": old baccarat dealer marker at " + m.pos());
				org.junit.jupiter.api.Assertions.assertTrue(cells.add(m.pos()), l.id() + ": two NPC markers at " + m.pos());
				if (npc.role() == NpcRole.HOUSE_DEALER) {
					houseCells.add(m.pos());
					dealers++;
				}
				merchants += npc.role() == NpcRole.MERCHANT ? 1 : 0;
			}
			org.junit.jupiter.api.Assertions.assertTrue(merchants <= 1, l.id() + ": at most one merchant per piece");
		}
		org.junit.jupiter.api.Assertions.assertTrue(dealers > 0, "house dealers placed");
	}
}
