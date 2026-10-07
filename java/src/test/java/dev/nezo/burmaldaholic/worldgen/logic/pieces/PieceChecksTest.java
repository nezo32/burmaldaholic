package dev.nezo.burmaldaholic.worldgen.logic.pieces;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.worldgen.logic.Layout;
import dev.nezo.burmaldaholic.worldgen.logic.Markers;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Checks;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Piece;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Runs {@link Checks} on every generated v0.2 piece (architecture doc §9.1). Lane CB-1 widens the source to
 * {@code CasinoPack.all()} once the registry exists; until then it covers every Overworld piece.
 */
class PieceChecksTest {
	static List<Piece> pieces() {
		return OverworldPieces.all();
	}

	@TestFactory
	List<DynamicTest> everyPiecePassesChecks() {
		return pieces().stream().map(p -> DynamicTest.dynamicTest(p.id(), () -> {
			List<String> problems = Checks.run(p);
			assertEquals(List.of(), problems, p.id());
		})).toList();
	}

	/** Blocks players would strip a casino for (resource farming). Copper / iron decor (chains, bars) is fine. */
	private static final List<String> VALUABLE = List.of("gold_block", "iron_block", "diamond", "emerald", "netherite",
		"raw_gold", "raw_iron", "lapis_block", "redstone_block", "ancient_debris", "beacon", "weighted_pressure_plate");

	@TestFactory
	List<DynamicTest> noValuableBlocks() {
		return pieces().stream().map(p -> DynamicTest.dynamicTest(p.id(), () -> {
			var g = p.layout().grid();
			var s = g.size();
			for (int x = 0; x < s.x(); x++) {
				for (int y = 0; y < s.y(); y++) {
					for (int z = 0; z < s.z(); z++) {
						var b = g.get(x, y, z);
						if (b != null) {
							for (String v : VALUABLE) {
								assertFalse(b.name().contains(v), p.id() + " holds " + b + " at " + x + "," + y + "," + z);
							}
						}
					}
				}
			}
		})).toList();
	}

	@TestFactory
	List<DynamicTest> anchorsOnlyInStartPiecesAndEveryPieceHasEntries() {
		return pieces().stream().map(p -> DynamicTest.dynamicTest(p.id(), () -> {
			long anchors = p.layout().markers().stream().filter(m -> m.marker() instanceof Markers.Anchor).count();
			boolean start = p.id().endsWith("/" + OverworldCasino.Part.ENTRANCE.suffix());
			assertEquals(start ? 1 : 0, anchors, p.id());
			assertFalse(p.entries().isEmpty(), p.id() + " declares no entry");
			for (Layout.PlacedMarker m : p.layout().markers()) {
				assertTrue(p.layout().grid().inside(m.pos().x(), m.pos().y(), m.pos().z()), m.marker().metadata());
			}
		})).toList();
	}
}
