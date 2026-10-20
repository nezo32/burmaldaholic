package dev.nezo.burmaldaholic.core.ui.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nezo.burmaldaholic.games.extras.present.ExtrasLayout;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Owner playtest B6 ("some games are fullscreen, some widget-like, play-field heights differ"): every v2 game screen lays
 * out in the same {@link TableGrid} of the {@link ViewportPolicy} frame, so the TopBar / PlayArea / Dock rows are one
 * function of the frame size — the same for cards, craps, roulette, Plinko, Wheel, Coin Flip, Scratch and Dice — at
 * 427 × 240 (4K GUI Auto), 480 × 270, 640 × 360 and 960 × 540 (ROOMY). The screens cannot change it (the kit's
 * viewport hooks are final; {@code V2ScreenUnityTest} checks the classes); the extras scenes stay inside the PlayArea.
 * The real screens are checked on real windows by {@code ResolutionMatrixClientGameTests} (lane RS, "every game").
 */
class PlayFieldUnityTest {
	/** The four layout sizes and a real window + GUI scale that gives each (the player's scale is kept, §3.7). */
	static final int[][] CELLS = {
		{427, 240, 1280, 720, 3}, // 4K GUI Auto has the same layout (k9)
		{480, 270, 960, 540, 2},
		{640, 360, 1280, 720, 2},
		{960, 540, 1920, 1080, 2},
	};

	@Test
	void policyGivesTheFourLayoutsAndTheirRoom() {
		for (int[] c : CELLS) {
			Viewport vp = ViewportPolicy.of(c[2], c[3], c[4]);
			String at = c[2] + "x" + c[3] + " GUI " + c[4];
			assertEquals(c[0], vp.frameW(), at);
			assertEquals(c[1], vp.frameH(), at);
			assertFalse(vp.framed(), at);
			assertEquals(c[0] >= 800 ? Room.ROOMY : Room.NORMAL, vp.room(), at);
			assertEquals(vp.room(), new TableGrid(vp).room, at);
		}
		assertEquals(ViewportPolicy.of(1280, 720, 3), ViewportPolicy.of(1280, 720, 3)); // pure
		Viewport k9 = ViewportPolicy.of(3840, 2160, 9);
		assertEquals(427, k9.frameW());
		assertEquals(240, k9.frameH());
	}

	@Test
	void everyGameSharesTheSameRows() {
		for (int[] c : CELLS) {
			TableGrid g = new TableGrid(ViewportPolicy.of(c[2], c[3], c[4]));
			String at = c[0] + "x" + c[1];
			Tier t = Tier.of(c[0], c[1]);
			assertEquals(new Box(0, 0, c[0], t.topBar), g.topBar, at);
			assertEquals(new Box(0, c[1] - t.dock, c[0], t.dock), g.dock, at);
			assertEquals(new Box(0, t.topBar, c[0], c[1] - t.topBar - t.dock), g.play, at + ": the PlayArea is the rows between the bars");
			// a frame of the same size gives the same rows wherever it sits (framed vs full window)
			TableGrid framed = new TableGrid(new Viewport(c[0] + 200, c[1] + 100, 2, 1f, 100, 50, c[0], c[1]));
			assertEquals(g.play, framed.play, at + " framed");
			assertEquals(g.dock, framed.dock, at + " framed");
		}
	}

	@Test
	void extrasScenesStayInTheirPlayArea() {
		Map<String, Function<TableGrid, ExtrasLayout.Scene>> scenes = new LinkedHashMap<>();
		scenes.put("plinko", ExtrasLayout::plinko);
		scenes.put("wheel", ExtrasLayout::wheel);
		scenes.put("scratch", ExtrasLayout::scratch);
		scenes.put("coin", ExtrasLayout::coin);
		scenes.put("dice", ExtrasLayout::dice);
		for (int[] c : CELLS) {
			TableGrid g = new TableGrid(ViewportPolicy.of(c[2], c[3], c[4]));
			for (var e : scenes.entrySet()) {
				ExtrasLayout.Scene s = e.getValue().apply(g);
				String at = e.getKey() + " at " + c[0] + "x" + c[1];
				assertTrue(s.object().inside(g.play), at + ": object " + s.object() + " outside the PlayArea " + g.play);
				if (!s.side().isEmpty()) {
					assertTrue(s.side().inside(g.play), at + ": options column " + s.side() + " outside " + g.play);
					assertFalse(s.side().intersects(s.object()), at + ": the column overlaps the object");
				}
			}
		}
	}

	/** "Start a Plinko Battle" was cut to "Start a Plinko Ba…" at 427 × 240; the toggle now says the mode's name. */
	@Test
	void plinkoBattleToggleFitsItsRow() throws IOException {
		String key = "gui.burmaldaholic.pvp.plinko.title"; // PlinkoScreen.BATTLE_TOGGLE
		Path dir = Path.of(System.getProperty("burmaldaholic.projectDir", "."), "src/main/lang/pvp");
		for (String code : new String[] {"en_us", "ru_ru"}) {
			JsonObject o = JsonParser.parseString(Files.readString(dir.resolve(code + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
			String label = o.get(key).getAsString();
			for (int[] c : CELLS) {
				ExtrasLayout.Scene s = ExtrasLayout.plinko(new TableGrid(ViewportPolicy.of(c[2], c[3], c[4])));
				int room = s.side().w() - 8; // V2Button keeps 4 px each side
				assertTrue(StatusFitTest.FONT.applyAsInt(label) <= room, code + " '" + label + "' (" + StatusFitTest.FONT.applyAsInt(label)
					+ " px) in a " + room + " px toggle at " + c[0] + "x" + c[1]);
			}
		}
	}
}
