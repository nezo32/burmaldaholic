package dev.nezo.burmaldaholic.worldgen.logic.pieces;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.worldgen.logic.BlockSpec;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoKind;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoLoot;
import dev.nezo.burmaldaholic.worldgen.logic.Grid;
import dev.nezo.burmaldaholic.worldgen.logic.Layout;
import dev.nezo.burmaldaholic.worldgen.logic.Markers;
import dev.nezo.burmaldaholic.worldgen.logic.Nbt;
import dev.nezo.burmaldaholic.worldgen.logic.NpcRole;
import dev.nezo.burmaldaholic.worldgen.logic.TemplateExporter;
import dev.nezo.burmaldaholic.worldgen.logic.Vec;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Checks;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Game;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Palette;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Palettes;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Piece;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Role;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Lane CB-4: the Piglin Parlor and High Roller Lounge v2 pieces (architecture doc §4.3, §4.4, §7.3, §7.4). */
class OtherworldTest {
	private static final Pattern BLOCK_ID = Pattern.compile("[a-z0-9_]+:[a-z0-9_/]+");

	@Test
	void registryHoldsBothPiecesWithContractSizesAndKinds() {
		List<Piece> all = OtherworldPieces.all();
		assertEquals(2, all.size());
		Piece parlor = OtherworldPieces.parlor();
		assertEquals("casino/parlor", parlor.id());
		assertEquals(new Vec(PiglinParlor.SX, PiglinParlor.SY, PiglinParlor.SZ), parlor.layout().size());
		assertEquals(CasinoKind.PIGLIN_PARLOR, parlor.layout().kind());
		Piece lounge = OtherworldPieces.lounge();
		assertEquals("casino/lounge", lounge.id());
		assertEquals(new Vec(HighRollerLounge.SX, HighRollerLounge.SY, HighRollerLounge.SZ), lounge.layout().size());
		assertEquals(CasinoKind.HIGH_ROLLER, lounge.layout().kind());
		assertTrue(parlor.layout().jigsaws().isEmpty() && lounge.layout().jigsaws().isEmpty(), "annexes are single templates");
	}

	@Test
	void designChecksPass() {
		for (Piece p : OtherworldPieces.all()) {
			List<String> problems = Checks.run(p);
			assertTrue(problems.isEmpty(), p.id() + ":\n" + String.join("\n", problems));
		}
	}

	@Test
	void everyGameFamilyIsInEachBuilding() {
		for (Piece p : OtherworldPieces.all()) {
			assertEquals(Game.Family.REQUIRED, Checks.families(List.of(p)), p.id());
		}
		assertTrue(OtherworldPieces.parlor().games().stream().anyMatch(g -> g.game() == Game.NETHER_CASHIER));
		assertTrue(OtherworldPieces.lounge().games().stream().anyMatch(g -> g.game() == Game.SLOTS_NETHERITE));
		assertTrue(OtherworldPieces.lounge().games().stream().anyMatch(g -> g.game() == Game.BLACKJACK_HIGH_ROLLER));
	}

	@Test
	void gameBlocksFaceTheirPlayers() {
		for (Piece p : OtherworldPieces.all()) {
			for (Piece.GameSpot spot : p.games()) {
				Vec v = spot.pos();
				BlockSpec b = p.layout().grid().get(v.x(), v.y(), v.z());
				assertEquals(spot.game().blockId(), b.name(), p.id() + " " + v);
				assertEquals(spot.playerSide().id(), b.properties().get("facing"), p.id() + " " + v);
			}
		}
	}

	@Test
	void markers() {
		Layout parlor = OtherworldPieces.parlor().layout();
		assertEquals(1, count(parlor, Markers.Anchor.class));
		assertEquals(List.of(NpcRole.PIGLIN_DEALER, NpcRole.PIGLIN_DEALER, NpcRole.PIGLIN_DEALER, NpcRole.PIGLIN_MONEYLENDER),
			npcs(parlor));
		assertEquals(List.of(CasinoLoot.Table.PIGLIN_PARLOR), chests(parlor));
		Layout lounge = OtherworldPieces.lounge().layout();
		assertEquals(1, count(lounge, Markers.Anchor.class));
		assertEquals(List.of(NpcRole.BACCARAT_DEALER, NpcRole.SHULKER_CROUPIER), npcs(lounge));
		assertEquals(List.of(CasinoLoot.Table.HIGH_ROLLER), chests(lounge));
		for (Layout l : List.of(parlor, lounge)) {
			for (Layout.PlacedMarker m : l.markers()) {
				Vec v = m.pos();
				BlockSpec at = l.grid().get(v.x(), v.y(), v.z());
				assertTrue(at == null || at.is("minecraft:air"), l.id() + ": marker " + m.marker().metadata() + " sits in " + at);
			}
		}
	}

	@Test
	void blockIdsAreWellFormed() {
		for (Piece p : OtherworldPieces.all()) {
			Grid g = p.layout().grid();
			forEach(g, (x, y, z, b) -> assertTrue(BLOCK_ID.matcher(b.name()).matches(), p.id() + " " + b));
		}
	}

	/** Lava (the pit and the tower windows) can neither flow nor ignite anything: every neighbour is lava or fireproof solid. */
	@Test
	void parlorLavaIsSealed() {
		Grid g = OtherworldPieces.parlor().layout().grid();
		int[] lava = {0};
		forEach(g, (x, y, z, b) -> {
			if (!b.is("minecraft:lava")) {
				return;
			}
			lava[0]++;
			for (int[] d : new int[][] {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
				BlockSpec n = g.get(x + d[0], y + d[1], z + d[2]);
				assertTrue(n != null && !n.is("minecraft:air") && !n.name().endsWith("_carpet") && !n.name().endsWith("_banner"),
					"lava at " + x + "," + y + "," + z + " touches " + n);
			}
		});
		assertTrue(lava[0] >= 12, "lava falls and channels present");
	}

	/** The Parlor is built on the MIT Piglin Manor shell: its towers and roof are there, and the licence ships in the jar. */
	@Test
	void parlorStandsOnTheManorShellWithItsLicence() throws Exception {
		Grid g = OtherworldPieces.parlor().layout().grid();
		assertEquals("minecraft:polished_basalt", PiglinManorShell.at(1, 4, 1).name(), "a tower corner");
		assertEquals(PiglinManorShell.at(1, 10, 1), g.get(1, 10, 1), "tower wall kept");
		try (var in = OtherworldTest.class.getResourceAsStream("/META-INF/licenses/bygone-nether-MIT.txt")) {
			assertTrue(in != null && new String(in.readAllBytes()).contains("Copyright (c) 2022 izofar"));
		}
	}

	@Test
	void palettesAreCompleteAndDistinct() {
		List<Palette> all = new ArrayList<>(OtherworldPalettes.all());
		all.add(Palettes.TUDOR);
		for (Palette p : all) {
			p.check();
		}
		for (int i = 0; i < all.size(); i++) {
			for (int j = i + 1; j < all.size(); j++) {
				int same = 0;
				for (Role r : Role.values()) {
					if (all.get(i).get(r).full().equals(all.get(j).get(r).full())) {
						same++;
					}
				}
				assertTrue(same <= Role.values().length / 4, all.get(i).id() + " vs " + all.get(j).id() + ": " + same + " shared roles");
			}
		}
	}

	@Test
	void generationIsDeterministic() {
		for (Piece p : OtherworldPieces.all()) {
			Piece again = p.id().equals("casino/parlor") ? PiglinParlor.build() : HighRollerLounge.build();
			assertArrayEquals(Nbt.writeGzip(TemplateExporter.template(p.layout())), Nbt.writeGzip(TemplateExporter.template(again.layout())),
				p.id());
		}
	}

	@Test
	void contentBudget() {
		for (Piece p : OtherworldPieces.all()) {
			assertTrue(p.games().size() <= 40, p.id());
			assertTrue(npcs(p.layout()).size() <= 6, p.id());
			int[] cells = {0};
			forEach(p.layout().grid(), (x, y, z, b) -> cells[0]++);
			assertTrue(cells[0] <= 60_000, p.id() + " " + cells[0]);
		}
	}

	// ---- helpers ------------------------------------------------------------------------------------

	private interface CellVisitor {
		void visit(int x, int y, int z, BlockSpec b);
	}

	private static void forEach(Grid g, CellVisitor v) {
		Vec s = g.size();
		for (int x = 0; x < s.x(); x++) {
			for (int y = 0; y < s.y(); y++) {
				for (int z = 0; z < s.z(); z++) {
					BlockSpec b = g.get(x, y, z);
					if (b != null) {
						v.visit(x, y, z, b);
					}
				}
			}
		}
	}

	private static long count(Layout l, Class<?> type) {
		return l.markers().stream().filter(m -> type.isInstance(m.marker())).count();
	}

	private static List<NpcRole> npcs(Layout l) {
		Map<String, NpcRole> sorted = new TreeMap<>();
		int i = 0;
		for (Layout.PlacedMarker m : l.markers()) {
			if (m.marker() instanceof Markers.Npc n) {
				sorted.put(n.role().name() + "#" + i++, n.role());
			}
		}
		return List.copyOf(sorted.values());
	}

	private static List<CasinoLoot.Table> chests(Layout l) {
		List<CasinoLoot.Table> out = new ArrayList<>();
		for (Layout.PlacedMarker m : l.markers()) {
			if (m.marker() instanceof Markers.Chest c) {
				out.add(c.table());
			}
		}
		return out;
	}
}
