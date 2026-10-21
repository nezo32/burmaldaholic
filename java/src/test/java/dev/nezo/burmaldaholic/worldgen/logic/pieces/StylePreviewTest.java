package dev.nezo.burmaldaholic.worldgen.logic.pieces;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.worldgen.logic.CasinoKind;
import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import dev.nezo.burmaldaholic.worldgen.logic.Nbt;
import dev.nezo.burmaldaholic.worldgen.logic.TemplateExporter;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Blueprint;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Facades;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Piece;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.RoofKind;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Role;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Roofs;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Style massing at the standalone-casino scale (31-wide front, architecture doc §4.1 / §7.1): roof + facade +
 * signature of every style on a plain hall, so the CB-3 verbs are reviewed at the size lane CB-2's
 * {@code OverworldCasino} uses them. Not a shipped piece. {@code BURMALDAHOLIC_PREVIEW_DIR} exports
 * {@code style_<id>.nbt} for the renderer.
 */
class StylePreviewTest {
	static final int SX = 31, SY = 21, SZ = 25, FRONT = 21, CEIL = 9;

	static Piece massing(Style s) {
		Blueprint b = Blueprint.of("casino/preview/" + s.id(), CasinoKind.VILLAGE_CASINO, SX, SY, SZ, s.palette());
		b.foundation(0, 0, SX - 1, FRONT, 0);
		b.room(1, 0, 0, SX - 2, CEIL, FRONT).frame(5).windows(Facing.WEST, 5, 2, 4).windows(Facing.EAST, 5, 2, 4).done();
		for (int z = 0; z <= FRONT - 2; z += 5) {
			for (int x : new int[] {0, SX - 1}) {
				b.fill(x, 1, z, x, CEIL - 1, z, Role.FRAME);
			}
		}
		b.fill(2, 1, 1, SX - 3, 1, FRONT - 1, Role.CARPET);
		Roofs.roof(b, s.roof(), 0, 0, SX - 1, FRONT, CEIL, SY - 1);
		if (s.roof() == RoofKind.HIP) {
			Roofs.gable(b, 9, 12, 21, FRONT, 15, SY - 1, false, Role.ROOF, Role.ROOF, Role.WALL);
			Roofs.cupola(b, 15, 10, SY - 4, SY - 2);
		}
		Facades.front(b, s.facade(), new Facades.Front(0, SX - 1, FRONT, 0, 15, 5, 4, 7, SY - 2, 3, "CASINO"));
		s.signature().centrepiece(b, 15, 1, 12, Facing.SOUTH);
		for (int x = 5; x <= 25; x += 10) {
			for (int z = 4; z <= 16; z += 6) {
				b.set(x, CEIL, z, Role.LIGHT);
			}
		}
		return b.build();
	}

	@Test
	void everyStyleBuildsAtCasinoScale() throws Exception {
		String dir = System.getenv("BURMALDAHOLIC_PREVIEW_DIR");
		for (Style s : OverworldStyles.all()) {
			Piece p = massing(s);
			byte[] bytes = Nbt.writeGzip(TemplateExporter.template(p.layout()));
			assertTrue(bytes.length > 0);
			if (dir != null && !dir.isBlank()) {
				Files.write(Path.of(dir, "style_" + s.id() + ".nbt"), bytes);
			}
		}
	}
}
