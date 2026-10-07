package dev.nezo.burmaldaholic.worldgen.logic.pieces;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.worldgen.logic.BlockSpec;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoKind;
import dev.nezo.burmaldaholic.worldgen.logic.Layout;
import dev.nezo.burmaldaholic.worldgen.logic.Markers;
import dev.nezo.burmaldaholic.worldgen.logic.Nbt;
import dev.nezo.burmaldaholic.worldgen.logic.NpcRole;
import dev.nezo.burmaldaholic.worldgen.logic.TemplateExporter;
import dev.nezo.burmaldaholic.worldgen.logic.Vec;
import dev.nezo.burmaldaholic.worldgen.logic.VillageStyle;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Checks;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Game;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Piece;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Village annex v2 (architecture doc §4.2 / §7.5). Set {@code BURMALDAHOLIC_PREVIEW_DIR=<dir>} to also write
 * the five templates there for the isometric renderer (docs/design/visual/render_structure.py).
 */
class VillageAnnexTest {
	@Test
	void everyAnnexPassesTheDesignChecks() {
		for (Piece p : AnnexPieces.all()) {
			List<String> problems = Checks.run(p);
			assertTrue(problems.isEmpty(), p.id() + ":\n" + String.join("\n", problems));
		}
	}

	@Test
	void everyAnnexIsAFullCasinoInsideTheStreetEndFootprint() {
		for (VillageStyle v : VillageStyle.values()) {
			Piece p = AnnexPieces.forStyle(v);
			assertEquals("casino/annex/" + v.id(), p.id());
			assertEquals(CasinoKind.VILLAGE_CASINO, p.layout().kind());
			Vec s = p.layout().size();
			assertTrue(s.x() <= 17 && s.z() <= 17 && s.y() <= 25, p.id() + " too big: " + s);
			assertTrue(Checks.families(List.of(p)).containsAll(Game.Family.REQUIRED), p.id() + " misses a game: "
				+ Checks.families(List.of(p)));
		}
	}

	@Test
	void npcsChestAnchorAndStreetConnector() {
		for (Piece p : AnnexPieces.all()) {
			Layout l = p.layout();
			Set<NpcRole> npcs = EnumSet.noneOf(NpcRole.class);
			int anchors = 0, chests = 0;
			for (Layout.PlacedMarker m : l.markers()) {
				switch (m.marker()) {
					case Markers.Npc n -> npcs.add(n.role());
					case Markers.Anchor a -> {
						anchors++;
						assertEquals(p.id(), a.layoutId());
					}
					case Markers.Chest c -> chests++;
				}
				BlockSpec at = l.grid().get(m.pos().x(), m.pos().y(), m.pos().z());
				// markers become air (or the chest) after placement: never put one into a wall
				assertTrue(at == null || at.is("minecraft:air") || at.name().endsWith("_carpet"), p.id() + " marker in " + at + " at " + m.pos());
			}
			assertEquals(EnumSet.of(NpcRole.CROUPIER, NpcRole.LOAN_SHARK, NpcRole.BACCARAT_DEALER), npcs, p.id());
			assertEquals(1, anchors, p.id());
			assertEquals(1, chests, p.id());
			assertEquals(1, l.jigsaws().size(), p.id());
			Layout.Jigsaw j = l.jigsaws().getFirst();
			assertEquals(new Vec(8, 0, l.size().z() - 1), j.pos(), "street connector in the porch floor at the front edge");
			assertEquals("south_up", j.orientation());
			assertEquals("minecraft:street", j.name());
			assertEquals("minecraft:street", j.target());
			assertEquals("minecraft:empty", j.pool());
		}
	}

	@Test
	void theDoorThroatIsOpenAndTheFloorIsLevelWithTheStreet() {
		for (Piece p : AnnexPieces.all()) {
			var g = p.layout().grid();
			for (int z = VillageAnnex.FRONT - 1; z < VillageAnnex.SZ; z++) {
				for (int x = 7; x <= 9; x++) {
					BlockSpec floor = g.get(x, 0, z);
					assertNotNull(floor, p.id() + " hole in the floor at " + x + ",0," + z);
					for (int y = 1; y <= 3; y++) {
						BlockSpec c = g.get(x, y, z);
						assertTrue(c != null && (c.is("minecraft:air") || c.name().endsWith("_carpet")),
							p.id() + " door throat blocked at " + x + "," + y + "," + z + " by " + c);
					}
				}
			}
		}
	}

	@Test
	void stylesLookDifferent() {
		Set<String> facades = new HashSet<>();
		for (Piece p : AnnexPieces.all()) {
			StringBuilder sb = new StringBuilder();
			var g = p.layout().grid();
			for (int y = 0; y < g.size().y(); y++) {
				for (int x = 0; x < g.size().x(); x++) {
					BlockSpec c = g.get(x, y, VillageAnnex.FRONT);
					sb.append(c == null ? "-" : c.name()).append(';');
				}
			}
			assertTrue(facades.add(sb.toString()), p.id() + " has the same front as another style");
		}
	}

	@Test
	void deterministicAndExportable() throws Exception {
		String dir = System.getenv("BURMALDAHOLIC_PREVIEW_DIR");
		for (VillageStyle v : VillageStyle.values()) {
			Piece a = VillageAnnex.build(v), b = VillageAnnex.build(v);
			byte[] bytes = Nbt.writeGzip(TemplateExporter.template(a.layout()));
			assertArrayEquals(bytes, Nbt.writeGzip(TemplateExporter.template(b.layout())), v.id());
			assertEquals("data/burmaldaholic/structure/worldgen/casino/annex/" + v.id() + ".nbt", TemplateExporter.templateFile(a.layout()));
			if (dir != null && !dir.isBlank()) {
				Path out = Path.of(dir, "annex_" + v.id() + ".nbt");
				Files.createDirectories(out.getParent());
				Files.write(out, bytes);
			}
		}
		assertFalse(AnnexPieces.all().isEmpty());
	}
}
