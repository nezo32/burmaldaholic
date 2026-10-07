package dev.nezo.burmaldaholic.worldgen.logic.blueprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.worldgen.logic.BlockSpec;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoKind;
import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import dev.nezo.burmaldaholic.worldgen.logic.Grid;
import dev.nezo.burmaldaholic.worldgen.logic.Markers;
import dev.nezo.burmaldaholic.worldgen.logic.NpcRole;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Every kit, in every facing, placed in the middle of a lit test hall passes {@link Checks} (games usable and
 * reachable, decor supported, nothing dark) and writes only inside its documented footprint.
 */
class KitsTest {
	private static final int N = 15, H = 10, C = 7, ROW = 2;

	/** Lit hall: floor at y 1 over a slab at y 0, ceiling at y 9 with lights, doors on all four sides. */
	private static Blueprint hall() {
		Blueprint b = Blueprint.of("casino/test/kits", CasinoKind.VILLAGE_CASINO, N, H, N, Palettes.TUDOR);
		b.fill(0, 0, 0, N - 1, 0, N - 1, Role.FOUNDATION);
		b.room(0, 1, 0, N - 1, H - 1, N - 1);
		for (int x = 2; x < N - 1; x += 3) {
			for (int z = 2; z < N - 1; z += 3) {
				b.set(x, H - 1, z, Role.LIGHT);
			}
		}
		for (Facing f : Facing.values()) {
			int ex = C + f.dx() * C, ez = C + f.dz() * C;
			b.clear(ex, ROW, ez, ex, ROW + 1, ez);
			b.entry(ex, ROW, ez);
		}
		return b;
	}

	private static Map<String, Kit> kits() {
		Map<String, Kit> m = new LinkedHashMap<>();
		m.put("tablePod", Kits.tablePod(Game.BLACKJACK, null));
		m.put("tablePodDealer", Kits.tablePod(Game.BACCARAT, NpcRole.BACCARAT_DEALER));
		m.put("slotBank", Kits.slotBank(Game.SLOTS_GOLD, 3));
		m.put("cashierCage", Kits.cashierCage(Game.CASHIER));
		m.put("machineAlcove", Kits.machineAlcove(Game.WHEEL));
		m.put("bar", Kits.bar(5));
		m.put("vipSofa", Kits.vipSofa(3));
		m.put("planter", Kits.planter());
		m.put("pottedPlant", Kits.pottedPlant());
		m.put("ropeBarrier", Kits.ropeBarrier(5));
		m.put("duelPit", Kits.duelPit());
		m.put("chipStacks", Kits.chipStacks());
		m.put("fireplace", Kits.fireplace(5));
		m.put("portico", Kits.portico(7, 3, 4));
		m.put("lampPost", Kits.lampPost(4));
		return m;
	}

	@Test
	void everyKitPassesChecksInEveryFacing() {
		for (Map.Entry<String, Kit> e : kits().entrySet()) {
			for (Facing f : Facing.values()) {
				Blueprint b = hall();
				b.kit(e.getValue(), C, ROW, C, f);
				List<String> problems = Checks.run(b.build());
				assertEquals(List.of(), problems, e.getKey() + " facing " + f);
			}
		}
	}

	@Test
	void hangingKitsPassChecks() {
		for (Kit k : List.of(Kits.chandelier(3), Kits.grandChandelier(3), Kits.pendantLamp(3))) {
			Blueprint b = hall();
			b.kit(k, C, H - 1, C, Facing.SOUTH);
			assertEquals(List.of(), Checks.run(b.build()));
		}
		Blueprint b = hall();
		b.kit(Kits.wallSconce(), C, ROW + 2, 1, Facing.SOUTH);
		assertEquals(List.of(), Checks.run(b.build()));
	}

	@Test
	void tablePodFacesThePlayersAndPutsTheDealerBehind() {
		for (Facing f : Facing.values()) {
			Blueprint b = hall();
			b.kit(Kits.tablePod(Game.UTH, NpcRole.BACCARAT_DEALER), C, ROW, C, f);
			Piece p = b.build();
			assertEquals(f.id(), p.layout().grid().get(C, ROW, C).properties().get("facing"));
			Markers.Npc npc = (Markers.Npc) p.layout().markers().getFirst().marker();
			assertEquals(f, npc.facing());
			assertEquals(C - f.dx(), p.layout().markers().getFirst().pos().x());
			assertEquals(C - f.dz(), p.layout().markers().getFirst().pos().z());
		}
	}

	@Test
	void slotBankRunsToTheRightOfTheUser() {
		Blueprint b = hall();
		b.kit(Kits.slotBank(Game.SLOTS_COPPER, 3), 3, ROW, 1, Facing.SOUTH);
		Grid g = b.build().layout().grid();
		for (int x = 3; x <= 5; x++) {
			assertEquals(Game.SLOTS_COPPER.blockId(), g.get(x, ROW, 1).name());
		}
	}

	@Test
	void kitsStayInsideTheirFootprints() {
		// r × f half extents and rows per kit (see the kit javadocs)
		Map<String, int[]> fp = Map.of(
			"tablePod", new int[] {-2, 2, -1, 1, 0, 3},
			"cashierCage", new int[] {-2, 2, -1, 0, 0, 3},
			"machineAlcove", new int[] {-1, 1, 0, 1, 0, 4},
			"planter", new int[] {-1, 1, -1, 1, 0, 1},
			"chipStacks", new int[] {0, 0, 0, 0, 0, 1},
			"duelPit", new int[] {-2, 2, -2, 2, -2, 1});
		for (Map.Entry<String, int[]> e : fp.entrySet()) {
			for (Facing f : Facing.values()) {
				Blueprint ref = hall();
				Blueprint b = hall();
				b.kit(kits().get(e.getKey()), C, ROW, C, f);
				Grid a = ref.build().layout().grid(), g = b.build().layout().grid();
				int[] r = e.getValue();
				Facing right = f.rotate(-1);
				for (int x = 1; x < N - 1; x++) {
					for (int y = 0; y < H - 1; y++) {
						for (int z = 1; z < N - 1; z++) {
							if (java.util.Objects.equals(a.get(x, y, z), g.get(x, y, z))) {
								continue;
							}
							int dx = x - C, dz = z - C;
							int rr = dx * right.dx() + dz * right.dz(), ff = dx * f.dx() + dz * f.dz(), dy = y - ROW;
							assertTrue(rr >= r[0] && rr <= r[1] && ff >= r[2] && ff <= r[3] && dy >= r[4] && dy <= r[5],
								e.getKey() + " " + f + " wrote outside its footprint at r=" + rr + " f=" + ff + " dy=" + dy);
						}
					}
				}
			}
		}
	}

	@Test
	void woodFollowsTheFrameRole() {
		Blueprint b = hall();
		assertEquals("dark_oak", Kits.wood(b, Role.FRAME));
		assertEquals("crimson", Kits.wood(b, Role.STOOL));
		assertEquals("dark_oak", Kits.wood(b, Role.WALL));
		assertEquals(BlockSpec.mc("dark_oak_fence"), Kits.fence(b));
	}
}
