package dev.nezo.burmaldaholic.worldgen.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LayoutsTest {
	private static long count(Layout l, String block) {
		long n = 0;
		Vec s = l.size();
		for (int x = 0; x < s.x(); x++) {
			for (int y = 0; y < s.y(); y++) {
				for (int z = 0; z < s.z(); z++) {
					BlockSpec b = l.grid().get(x, y, z);
					if (b != null && b.is(block)) {
						n++;
					}
				}
			}
		}
		return n;
	}

	private static long npcs(Layout l, NpcRole role) {
		return l.markers().stream().filter(m -> m.marker() instanceof Markers.Npc n && n.role() == role).count();
	}

	@Test
	void sevenLayoutsWithSpecSizes() {
		assertEquals(7, Layouts.all().size());
		for (VillageStyle s : VillageStyle.values()) {
			assertEquals(new Vec(17, 10, 17), Layouts.village(s).size(), s.id());
			assertEquals(CasinoKind.VILLAGE_CASINO, Layouts.village(s).kind());
		}
		assertEquals(new Vec(21, 12, 21), Layouts.piglinParlorLayout().size());
		assertEquals(new Vec(15, 9, 15), Layouts.highRollerLayout().size());
	}

	@Test
	void villageContentsPerSpec() {
		for (VillageStyle s : VillageStyle.values()) {
			Layout l = Layouts.village(s);
			assertEquals(1, count(l, Layouts.CASHIER));
			assertEquals(1, count(l, Layouts.BLACKJACK));
			assertEquals(1, count(l, Layouts.ROULETTE));
			assertEquals(3, count(l, Layouts.SLOTS_COPPER));
			assertEquals(1, count(l, Layouts.SLOTS_GOLD));
			assertEquals(1, count(l, Layouts.WHEEL));
			assertEquals(1, count(l, Layouts.UTH));
			assertEquals(1, npcs(l, NpcRole.LOAN_SHARK));
			assertEquals(1, npcs(l, NpcRole.CROUPIER));
			assertEquals(1, l.markers().stream().filter(m -> m.marker() instanceof Markers.Chest c
				&& c.table() == CasinoLoot.Table.VILLAGE_CASINO).count());
			assertEquals(1, l.jigsaws().size(), "street connector");
			assertTrue(count(l, "minecraft:glowstone") > 0 && count(l, "minecraft:redstone_lamp") > 0, "CASINO sign");
		}
	}

	@Test
	void villagePalettesDiffer() {
		Set<String> floors = new HashSet<>();
		for (VillageStyle s : VillageStyle.values()) {
			floors.add(Layouts.village(s).grid().get(8, 0, 8).name() + Layouts.village(s).grid().get(0, 2, 0).name());
		}
		assertEquals(VillageStyle.values().length, floors.size(), "each village type has its own palette");
	}

	@Test
	void parlorContentsPerSpec() {
		Layout l = Layouts.piglinParlorLayout();
		assertEquals(1, count(l, Layouts.CRAPS));
		assertEquals(1, count(l, Layouts.POKER));
		assertEquals(2, count(l, Layouts.SLOTS_GOLD));
		assertEquals(1, count(l, Layouts.PLINKO));
		assertEquals(1, count(l, Layouts.NETHER_CASHIER));
		assertEquals(1, count(l, Layouts.BACCARAT));
		assertEquals(2, npcs(l, NpcRole.PIGLIN_DEALER));
		assertEquals(1, npcs(l, NpcRole.PIGLIN_MONEYLENDER));
		// rows 9..11 keep the Nether rock (structure void)
		assertNull(l.grid().get(10, 10, 10));
		// back door towards the bastion + main entrance
		assertTrue(l.grid().get(10, 2, 0).is("minecraft:air") && l.grid().get(10, 2, 20).is("minecraft:air"));
	}

	@Test
	void loungeContentsPerSpec() {
		Layout l = Layouts.highRollerLayout();
		assertEquals(2, count(l, Layouts.SLOTS_NETHERITE));
		assertEquals(1, count(l, Layouts.BLACKJACK_HIGH_ROLLER));
		assertEquals(1, count(l, Layouts.ROULETTE_HIGH_ROLLER));
		assertEquals(1, count(l, Layouts.CASHIER));
		assertEquals(1, count(l, Layouts.BACCARAT_HIGH_ROLLER));
		assertEquals(1, count(l, Layouts.UTH_HIGH_ROLLER));
		assertEquals(1, npcs(l, NpcRole.SHULKER_CROUPIER));
		assertEquals(1, npcs(l, NpcRole.BACCARAT_DEALER));
	}

	@Test
	void markersSitInClearCellsInsideAndEveryLayoutHasOneAnchor() {
		for (Layout l : Layouts.all().values()) {
			long anchors = l.markers().stream().filter(m -> m.marker() instanceof Markers.Anchor a && a.layoutId().equals(l.id())).count();
			assertEquals(1, anchors, l.id());
			Set<Vec> seen = new HashSet<>();
			for (Layout.PlacedMarker m : l.markers()) {
				Vec p = m.pos();
				assertTrue(l.grid().inside(p.x(), p.y(), p.z()), l.id() + " marker outside " + p);
				assertTrue(seen.add(p), l.id() + " two markers at " + p);
				if (m.marker() instanceof Markers.Npc) {
					// NPC stands in the cell below the marker: must not be a solid wall/table
					BlockSpec stand = l.grid().get(p.x(), p.y() - 1, p.z());
					assertNotNull(stand);
					assertTrue(stand.is("minecraft:air") || stand.name().endsWith("_carpet"), l.id() + " npc stands in " + stand);
					BlockSpec head = l.grid().get(p.x(), p.y() + 1, p.z());
					assertTrue(head.is("minecraft:air"), l.id() + " npc head in " + head);
				}
			}
		}
	}

	@Test
	void tablesFaceSomewhereAndAreOnTheFloorRow() {
		for (Layout l : Layouts.all().values()) {
			Vec s = l.size();
			for (int x = 0; x < s.x(); x++) {
				for (int y = 0; y < s.y(); y++) {
					for (int z = 0; z < s.z(); z++) {
						BlockSpec b = l.grid().get(x, y, z);
						if (b != null && b.name().startsWith("burmaldaholic:")) {
							assertEquals(1, y, l.id() + " table off the floor row: " + b);
							assertTrue(b.properties().containsKey("facing"), b.toString());
						}
					}
				}
			}
		}
	}

	@Test
	void entranceIsOpenOnPlusZ() {
		for (Layout l : Layouts.all().values()) {
			int midX = l.size().x() / 2;
			int z = l.size().z() - 1;
			assertTrue(l.grid().get(midX, 1, z).is("minecraft:air") && l.grid().get(midX, 2, z).is("minecraft:air"), l.id());
		}
	}

	@Test
	void signSpellsCasino() {
		Layouts.Sign sign = Layouts.signPixels("CASINO");
		assertEquals(3 + 3 + 3 + 1 + 3 + 3, sign.width());
		List<Integer> letters = new ArrayList<>();
		sign.pixels().forEach(p -> {
			if (!letters.contains(p.letter())) {
				letters.add(p.letter());
			}
		});
		assertEquals(List.of(0, 1, 2, 3, 4, 5), letters);
		assertFalse(sign.pixels().isEmpty());
	}

	@Test
	void templateNbtHasPaletteBlocksAndMarkers() {
		for (Layout l : Layouts.all().values()) {
			Map<String, Object> root = TemplateExporter.template(l);
			assertEquals(TemplateExporter.DATA_VERSION, root.get("DataVersion"));
			Nbt.TagList blocks = (Nbt.TagList) root.get("blocks");
			Nbt.TagList palette = (Nbt.TagList) root.get("palette");
			long structureBlocks = blocks.items().stream().filter(b -> {
				@SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) b;
				@SuppressWarnings("unchecked") Map<String, Object> state = (Map<String, Object>) palette.items().get((Integer) m.get("state"));
				return state.get("Name").equals("minecraft:structure_block");
			}).count();
			assertEquals(l.markers().size(), structureBlocks, l.id());
			for (Object b : blocks.items()) {
				@SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) b;
				assertTrue((Integer) m.get("state") < palette.items().size());
			}
		}
	}

	@Test
	void villageStyleMapping() {
		assertEquals(VillageStyle.SNOWY, VillageStyle.forBiome("minecraft:snowy_plains"));
		assertEquals(VillageStyle.DESERT, VillageStyle.forBiome("minecraft:desert"));
		assertEquals(VillageStyle.SAVANNA, VillageStyle.forBiome("minecraft:savanna_plateau"));
		assertEquals(VillageStyle.TAIGA, VillageStyle.forBiome("minecraft:old_growth_spruce_taiga"));
		assertEquals(VillageStyle.PLAINS, VillageStyle.forBiome("minecraft:meadow"));
		assertEquals(VillageStyle.PLAINS, VillageStyle.byCasinoPool("minecraft:village/plains/terminators").orElseThrow());
		assertTrue(VillageStyle.byCasinoPool("minecraft:village/plains/zombie/terminators").isEmpty(), "no casinos in zombie villages");
		assertTrue(VillageStyle.byCasinoPool("minecraft:village/plains/houses").isEmpty());
	}
}
