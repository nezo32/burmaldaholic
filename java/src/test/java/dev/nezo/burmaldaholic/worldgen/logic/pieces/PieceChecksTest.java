package dev.nezo.burmaldaholic.worldgen.logic.pieces;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.worldgen.logic.CasinoPack;
import dev.nezo.burmaldaholic.worldgen.logic.Layout;
import dev.nezo.burmaldaholic.worldgen.logic.Markers;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Checks;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Piece;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Runs {@link Checks} on every generated v0.2 piece (architecture doc §9.1): the design checks and the valuables scan
 * over {@link CasinoPack#pieces()} (Overworld styles, village annexes, Parlor, Lounge); pieces listed in
 * {@link Checks#PENDING_CLEARANCE} skip only the side-clearance rule, those in {@link Checks#PENDING_VALUABLES} skip
 * only the valuables scan, until their owners delete the entries.
 */
class PieceChecksTest {
	static List<Piece> pieces() {
		return OverworldPieces.all();
	}

	static List<Piece> allPieces() {
		return CasinoPack.pieces();
	}

	@TestFactory
	List<DynamicTest> everyPiecePassesChecks() {
		return allPieces().stream().map(p -> DynamicTest.dynamicTest(p.id(), () -> {
			List<String> problems = Checks.run(p);
			assertEquals(List.of(), problems, p.id());
		})).toList();
	}

	/** {@link Checks#FORBIDDEN} storage blocks (also part of {@link Checks#run}; kept separate for a clear report). */
	@TestFactory
	List<DynamicTest> noValuableBlocks() {
		return allPieces().stream().filter(p -> !Checks.PENDING_VALUABLES.contains(p.id())).map(p -> DynamicTest.dynamicTest(p.id(), () -> {
			var g = p.layout().grid();
			var s = g.size();
			for (int x = 0; x < s.x(); x++) {
				for (int y = 0; y < s.y(); y++) {
					for (int z = 0; z < s.z(); z++) {
						var b = g.get(x, y, z);
						assertFalse(Checks.forbidden(b), p.id() + " holds " + b + " at " + x + "," + y + "," + z);
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
