package dev.nezo.burmaldaholic.worldgen.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MarkersAndRecordsTest {
	@Test
	void everyLayoutMarkerRoundTrips() {
		for (Layout l : Layouts.all().values()) {
			for (Layout.PlacedMarker m : l.markers()) {
				assertEquals(m.marker(), Markers.parse(m.marker().metadata()).orElseThrow());
			}
		}
	}

	@Test
	void foreignAndBrokenMarkersAreIgnored() {
		assertTrue(Markers.parse(null).isEmpty());
		assertTrue(Markers.parse("").isEmpty());
		assertTrue(Markers.parse("ChestSouth").isEmpty());
		assertTrue(Markers.parse("burmaldaholic:worldgen npc wizard south").isEmpty());
		assertTrue(Markers.parse("burmaldaholic:worldgen npc croupier up").isEmpty());
		assertTrue(Markers.parse("burmaldaholic:worldgen casino nope").isEmpty());
		assertTrue(Markers.parse("burmaldaholic:worldgen chest village_casino").isEmpty());
		assertTrue(Markers.parse("burmaldaholic:worldgen dance").isEmpty());
	}

	@Test
	void npcRolesFallBack() {
		assertEquals(List.of("burmaldaholic:piglin_moneylender", "burmaldaholic:loan_shark"), NpcRole.PIGLIN_MONEYLENDER.entityIds());
		assertEquals(List.of("burmaldaholic:croupier"), NpcRole.CROUPIER.entityIds());
	}

	@Test
	void recordFindsContainingCasinoOnlyWhenAnchored() {
		CasinoRecord r = new CasinoRecord(CasinoRecord.idFor("minecraft:the_nether", 0, 40, 0), "minecraft:the_nether");
		assertFalse(r.contains("minecraft:the_nether", 5, 41, 5), "no bounds before the anchor");
		r.setAnchor("piglin_parlor", CasinoKind.PIGLIN_PARLOR, new Geometry.Box(0, 40, 0, 20, 51, 20));
		assertTrue(r.contains("minecraft:the_nether", 5, 41, 5));
		assertFalse(r.contains("minecraft:overworld", 5, 41, 5));
		assertSame(r, CasinoRecord.findAt(List.of(r), "minecraft:the_nether", 20.5, 51.9, 0));
		assertNull(CasinoRecord.findAt(List.of(r), "minecraft:the_nether", 21.0, 45, 0));
	}

	@Test
	void npcSlotsDeduplicateByHome() {
		CasinoRecord r = new CasinoRecord("x", "d");
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		r.addNpc(NpcRole.LOAN_SHARK, new Vec(1, 2, 3), 0f, a);
		r.addNpc(NpcRole.LOAN_SHARK, new Vec(1, 2, 3), 0f, b);
		r.addNpc(NpcRole.CROUPIER, new Vec(4, 2, 3), 90f, null);
		assertEquals(2, r.npcs().size());
		assertEquals(b, r.npcs().getFirst().entity);
	}

	@Test
	void respawnRule() {
		long respawn = 24_000;
		RespawnRule.Decision d = RespawnRule.decide(true, 5, 100, respawn);
		assertEquals(RespawnRule.NONE, d.missingSince());
		assertFalse(d.respawn());
		d = RespawnRule.decide(false, RespawnRule.NONE, 1000, respawn);
		assertEquals(1000, d.missingSince());
		assertFalse(d.respawn(), "first miss only starts the timer");
		d = RespawnRule.decide(false, 1000, 1000 + respawn - 1, respawn);
		assertFalse(d.respawn());
		d = RespawnRule.decide(false, 1000, 1000 + respawn, respawn);
		assertTrue(d.respawn());
		assertEquals(RespawnRule.NONE, d.missingSince());
		// respawn 0 still waits the minimum (entity loading races)
		assertFalse(RespawnRule.decide(false, 1000, 1100, 0).respawn());
		assertTrue(RespawnRule.decide(false, 1000, 1000 + RespawnRule.MIN_MISSING_TICKS, 0).respawn());
	}

	@Test
	void nbtRoundTrip() {
		Map<String, Object> root = TemplateExporter.template(Layouts.highRollerLayout());
		Map<String, Object> back = Nbt.readGzip(Nbt.writeGzip(root));
		assertEquals(root.keySet(), back.keySet());
		assertEquals(root.get("DataVersion"), back.get("DataVersion"));
		assertEquals(((Nbt.TagList) root.get("blocks")).items().size(), ((Nbt.TagList) back.get("blocks")).items().size());
		assertEquals(Nbt.END, ((Nbt.TagList) back.get("entities")).elementType());
		assertEquals(List.of(15, 9, 15), ((Nbt.TagList) back.get("size")).items());
	}
}
