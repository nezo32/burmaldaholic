package dev.nezo.burmaldaholic.core.anim.dice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Parity of the generated {@link DieAtlasTable} with {@code dice_atlas.json} and the Java {@link CubeGroup}. */
class DieAtlasTableTest {
	@Test
	void tableMatchesTheJson() throws IOException {
		Path file = Path.of(System.getProperty("burmaldaholic.projectDir", "."),
			"src/main/resources/assets/burmaldaholic/textures/gui/tables/core/dice_atlas.json");
		JsonObject j = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
		JsonArray frames = j.getAsJsonArray("frames");
		assertEquals(DieAtlasTable.FRAMES, frames.size());
		for (int i = 0; i < frames.size(); i++) {
			JsonArray q = frames.get(i).getAsJsonObject().getAsJsonArray("q");
			for (int k = 0; k < 4; k++) assertEquals(q.get(k).getAsDouble(), DieAtlasTable.Q[4 * i + k], 0, "frame " + i);
			assertEquals(frames.get(i).getAsJsonObject().get("up").getAsInt(), DieAtlasTable.UP[i]);
		}
		JsonArray cells = j.getAsJsonArray("cells");
		for (int c = 0; c < cells.size(); c++) {
			assertEquals(cells.get(c).getAsJsonObject().get("cell").getAsInt(), DieAtlasTable.CELLS[c]);
			assertEquals(cells.get(c).getAsJsonObject().get("frame").getAsInt(), DieAtlasTable.FRAME[c]);
		}
	}

	private static double[] offset(int j) {
		double[] yawQ = Quat.axisAngle(0, 0, 1, Math.toRadians(j == 15 ? 45 : new double[] {0, -15, 15}[j / 5]), new double[4]);
		if (j == 15 || j % 5 == 0) return yawQ;
		int t = j % 5;
		double[] tilt = Quat.axisAngle(t <= 2 ? 1 : 0, t <= 2 ? 0 : 1, 0, Math.toRadians(t % 2 == 1 ? 30 : -30), new double[4]);
		return Quat.mul(tilt, yawQ, new double[4]);
	}

	@Test
	void framesAreOffsetsOfTheJavaGroupElements() {
		double[] q = new double[4];
		for (int g = 0; g < 24; g++)
			for (int j = 0; j < 16; j++) {
				Quat.mul(offset(j), CubeGroup.element(g), q);
				int i = 16 * g + j;
				double d = Math.abs(Quat.dot(q, new double[] {DieAtlasTable.Q[4 * i], DieAtlasTable.Q[4 * i + 1], DieAtlasTable.Q[4 * i + 2], DieAtlasTable.Q[4 * i + 3]}));
				assertEquals(1, d, 1e-12, "frame " + i);
				assertEquals(CubeGroup.faceUp(q), DieAtlasTable.UP[i]);
			}
	}

	@Test
	void restPosesHitTheirExactFrame24x3() {
		for (int g = 0; g < 24; g++)
			for (int y = 0; y < 3; y++) {
				double yaw = new double[] {0, -15, 15}[y];
				double[] q = Quat.mul(Quat.axisAngle(0, 0, 1, Math.toRadians(yaw), new double[4]), CubeGroup.element(g), new double[4]);
				// any sign of q is the same orientation
				assertEquals(16 * g + 5 * y, DieAtlasTable.frame(q));
				assertEquals(16 * g + 5 * y, DieAtlasTable.frame(new double[] {-q[0], -q[1], -q[2], -q[3]}));
			}
	}

	@Test
	void solvedRollsRestOnTheirFaceFrame() {
		DieSample s = new DieSample();
		for (DiceFixtures.Case c : DiceFixtures.all())
			for (int f = 1; f <= 6; f++) {
				DiceRoll r = DiceSolver.solve(c.tray(), c.spec(), 31 * f, new int[] {f, 7 - f}, c.targetMs());
				r.sample(0, r.restMs() + 1, s);
				int fr = DieAtlasTable.frame(s.q);
				assertEquals(0, (fr % 16) % 5, "a yaw-0 rest frame");
				assertEquals(f, DieAtlasTable.UP[fr]);
			}
		assertTrue(DieAtlasTable.cellIndex(32) >= 0 && DieAtlasTable.cellIndex(7) < 0);
	}
}
