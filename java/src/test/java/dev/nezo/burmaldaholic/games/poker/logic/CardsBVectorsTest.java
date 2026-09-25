package dev.nezo.burmaldaholic.games.poker.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nezo.burmaldaholic.core.anim.Timeline;
import dev.nezo.burmaldaholic.games.uth.logic.UthBeats;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Golden vectors of the Hold'em and UTH beat schedules (docs/architecture/animation.md §3.4, topic "cards", lane J-L5
 * part: {@code src/test/resources/fx/vectors/cards_poker_uth.json}). The server paces publication with these timelines
 * and every client samples them, so a change must be deliberate: regenerate with
 * {@code FX_DUMP_VECTORS=1 ./gradlew test --tests '*CardsBVectorsTest'}.
 */
class CardsBVectorsTest {
	@Test
	void vectorsMatch() throws IOException {
		Path file = Path.of(System.getProperty("burmaldaholic.projectDir", "."), "src/test/resources/fx/vectors/cards_poker_uth.json");
		JsonObject actual = compute();
		if (System.getenv("FX_DUMP_VECTORS") != null || !Files.isRegularFile(file)) {
			Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
			Files.createDirectories(file.getParent());
			Files.writeString(file, gson.toJson(actual) + "\n", StandardCharsets.UTF_8);
			return;
		}
		JsonObject expected = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
		assertEquals(expected, actual);
	}

	private static JsonObject compute() {
		PokerBeats.Pacing p = PokerBeats.Pacing.DEFAULT;
		JsonObject o = new JsonObject();
		o.addProperty("v", 1);
		JsonArray poker = new JsonArray();
		add(poker, "deal 2 sb0", PokerBeats.deal(2, 0, p, 11));
		add(poker, "deal 6 sb1", PokerBeats.deal(6, 1, p, 12));
		add(poker, "deal 9 sb8", PokerBeats.deal(9, 8, p, 13));
		for (int s = 1; s <= 3; s++) add(poker, "street " + s, PokerBeats.street(s, p, 20 + s));
		add(poker, "finish uncontested", PokerBeats.finish(new PokerBeats.Finish(true, 0, false, 0, 1), p, 30));
		add(poker, "finish river showdown", PokerBeats.finish(new PokerBeats.Finish(true, 0, false, 2, 1), p, 31));
		add(poker, "finish runout from flop", PokerBeats.finish(new PokerBeats.Finish(true, 1, true, 3, 3), p, 32));
		add(poker, "finish runout from river", PokerBeats.finish(new PokerBeats.Finish(false, 3, true, 2, 2), p, 33));
		o.add("poker", poker);
		JsonArray uth = new JsonArray();
		add(uth, "deal 1", UthBeats.deal(1, 41));
		add(uth, "deal 6", UthBeats.deal(6, 42));
		add(uth, "flop", UthBeats.street(1, 43));
		add(uth, "turn river", UthBeats.street(2, 44));
		add(uth, "showdown 3", UthBeats.showdown(3, 45));
		add(uth, "result", UthBeats.result(46));
		o.add("uth", uth);
		return o;
	}

	private static void add(JsonArray out, String name, Timeline tl) {
		JsonObject c = new JsonObject();
		c.addProperty("case", name);
		c.addProperty("sharedEndMs", tl.sharedEndMs());
		c.add("timeline", JsonParser.parseString(tl.toCanonicalJson()));
		out.add(c);
	}
}
