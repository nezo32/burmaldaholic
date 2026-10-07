package dev.nezo.burmaldaholic.worldgen.logic.pieces;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.worldgen.logic.BlockSpec;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoKind;
import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import dev.nezo.burmaldaholic.worldgen.logic.Grid;
import dev.nezo.burmaldaholic.worldgen.logic.VillageStyle;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Blueprint;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Facades;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.FacadeKind;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Material;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Palette;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.RoofKind;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Role;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Roofs;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Overworld styles, palettes, roofs and facades (architecture doc §7.2, lane CB-3). */
class StylesTest {
	/** Blocks no casino palette may use: unpowered lamps, gravity blocks, torches, mineable treasure. */
	static final Set<String> FORBIDDEN = Set.of("minecraft:redstone_lamp", "minecraft:sand", "minecraft:red_sand", "minecraft:gravel",
		"minecraft:torch", "minecraft:wall_torch", "minecraft:gold_block", "minecraft:diamond_block", "minecraft:emerald_block",
		"minecraft:netherite_block", "minecraft:iron_block", "minecraft:lapis_block");

	@Test
	void fiveStylesInOrderWithDistinctShapes() {
		List<Style> all = OverworldStyles.all();
		assertEquals(List.of("tudor", "oasis", "safari", "alpine", "ice_palace"), all.stream().map(Style::id).toList());
		Set<RoofKind> roofs = EnumSet.noneOf(RoofKind.class);
		Set<FacadeKind> facades = EnumSet.noneOf(FacadeKind.class);
		for (Style s : all) {
			assertTrue(roofs.add(s.roof()), s.id() + " repeats a roof");
			assertTrue(facades.add(s.facade()), s.id() + " repeats a facade");
			assertEquals("burmaldaholic:has_structure/casino_" + s.id(), s.biomeTag());
			assertFalse(OverworldStyles.biomes(s).isEmpty());
			assertTrue(OverworldStyles.biomes(s).getFirst().startsWith("#minecraft:has_structure/village_"));
			assertEquals(s, OverworldStyles.byId(s.id()).orElseThrow());
		}
	}

	@Test
	void villageTypesMapToTheirStyle() {
		assertEquals("tudor", OverworldStyles.forVillage(VillageStyle.PLAINS).id());
		assertEquals("oasis", OverworldStyles.forVillage(VillageStyle.DESERT).id());
		assertEquals("safari", OverworldStyles.forVillage(VillageStyle.SAVANNA).id());
		assertEquals("alpine", OverworldStyles.forVillage(VillageStyle.TAIGA).id());
		assertEquals("ice_palace", OverworldStyles.forVillage(VillageStyle.SNOWY).id());
	}

	@Test
	void palettesAreCompleteSafeAndPairwiseDistinct() {
		List<Palette> ps = OverworldStyles.all().stream().map(Style::palette).toList();
		for (Palette p : ps) {
			p.check();
			if (p.id().equals("tudor")) {
				continue; // lane CB-2's palette
			}
			for (Role r : Role.values()) {
				Material m = p.get(r);
				for (String id : new String[] {m.full(), m.stairs(), m.slab()}) {
					if (id == null) {
						continue;
					}
					assertTrue(id.startsWith("minecraft:") && !id.contains("["), p.id() + " " + r + " bad id " + id);
					assertFalse(FORBIDDEN.contains(id), p.id() + " " + r + " uses " + id);
				}
				if (m.stairs() != null) {
					assertTrue(m.stairs().endsWith("_stairs"), p.id() + " " + r);
				}
				if (m.slab() != null) {
					assertTrue(m.slab().endsWith("_slab"), p.id() + " " + r);
				}
			}
		}
		for (int i = 0; i < ps.size(); i++) {
			for (int j = i + 1; j < ps.size(); j++) {
				int diff = 0;
				for (Role r : Role.values()) {
					if (!ps.get(i).get(r).full().equals(ps.get(j).get(r).full())) {
						diff++;
					}
				}
				assertTrue(diff >= Role.values().length * 2 / 3, ps.get(i).id() + " vs " + ps.get(j).id() + " differ in only " + diff + " roles");
				// the look-defining roles must all differ
				for (Role r : new Role[] {Role.WALL, Role.ROOF, Role.CARPET}) {
					assertFalse(ps.get(i).get(r).full().equals(ps.get(j).get(r).full()), ps.get(i).id() + " vs " + ps.get(j).id() + " share " + r);
				}
			}
		}
	}

	@Test
	void everyRoofStaysInsideItsBox() {
		for (Style s : OverworldStyles.all()) {
			for (RoofKind k : RoofKind.values()) {
				Blueprint b = Blueprint.of("casino/test/roof", CasinoKind.VILLAGE_CASINO, 25, 12, 21, s.palette());
				Roofs.roof(b, k, 2, 2, 22, 18, 3, 10);
				Grid g = b.build().layout().grid();
				int cells = 0;
				for (int x = 0; x < 25; x++) {
					for (int y = 0; y < 12; y++) {
						for (int z = 0; z < 21; z++) {
							BlockSpec c = g.get(x, y, z);
							if (c == null || c.is("minecraft:air")) {
								continue;
							}
							cells++;
							boolean finial = c.equals(Roofs.FINIAL) || c.equals(Roofs.CHIMNEY_FIRE);
							assertTrue(x >= 2 && x <= 22 && z >= 2 && z <= 18 && y >= 3 && (y <= 10 || finial && y == 11),
								s.id() + " " + k + " writes outside its box at " + x + "," + y + "," + z + ": " + c);
						}
					}
				}
				assertTrue(cells > 100, s.id() + " " + k + " built almost nothing");
			}
		}
	}

	@Test
	void marqueeReadsLeftToRightFromEverySide() {
		// "C" then "A": the C's open side is on its right for the reader, so the C's first column is solid
		for (Facing side : Facing.values()) {
			Blueprint b = Blueprint.of("casino/test/sign", CasinoKind.VILLAGE_CASINO, 21, 9, 21, OverworldStyles.TUDOR.palette());
			Facades.SignBox box = Facades.marquee(b, "CA", side, 10, 1, side == Facing.SOUTH || side == Facing.EAST ? 20 : 0);
			assertEquals(8, box.width());
			Grid g = b.build().layout().grid();
			int s = side.rotate(-1).dx() + side.rotate(-1).dz();
			int first = s > 0 ? box.alongFrom() : box.alongTo(); // reader's leftmost column
			int plane = side == Facing.SOUTH || side == Facing.EAST ? 20 : 0;
			// column pad+0 (letter C, left stroke) is lit on every text row; column pad+1 is lit only top/bottom
			int col0 = first + s, col1 = first + 2 * s;
			Set<String> lit = new HashSet<>();
			for (int y = 2; y <= 6; y++) {
				lit.add(cellName(g, side, plane, col0, y));
			}
			assertEquals(Set.of(OverworldStyles.TUDOR.palette().get(Role.NEON_A).full()), lit, side + " left stroke of C");
			assertEquals(OverworldStyles.TUDOR.palette().get(Role.SIGN_BACK).full(), cellName(g, side, plane, col1, 4), side + " C is open");
		}
	}

	private static String cellName(Grid g, Facing side, int plane, int along, int y) {
		BlockSpec c = side == Facing.NORTH || side == Facing.SOUTH ? g.get(along, y, plane) : g.get(plane, y, along);
		return c.name();
	}

	@Test
	void everyFacadeBuildsInsideItsBox() {
		for (Style s : OverworldStyles.all()) {
			for (FacadeKind k : FacadeKind.values()) {
				Blueprint b = Blueprint.of("casino/test/front", CasinoKind.VILLAGE_CASINO, 31, 18, 12, s.palette());
				Facades.front(b, k, new Facades.Front(0, 30, 8, 0, 15, 5, 4, 7, 17, 3, "CASINO"));
				Grid g = b.build().layout().grid();
				for (int z = 0; z < 8; z++) {
					for (int y = 0; y < 18; y++) {
						for (int x = 0; x < 31; x++) {
							assertEquals(null, g.get(x, y, z), s.id() + " " + k + " writes behind the wall at " + x + "," + y + "," + z);
						}
					}
				}
				for (int y = 1; y <= 4; y++) {
					for (int x = 13; x <= 17; x++) {
						assertTrue(g.get(x, y, 8).is("minecraft:air"), s.id() + " " + k + " door blocked at " + x + "," + y);
					}
				}
			}
		}
	}
}
