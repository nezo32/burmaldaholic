package dev.nezo.burmaldaholic.core.table.parts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * MT §8: the generated furniture assets (tools/assets/modules/tables/furniture.mjs) agree with the Java side —
 * 4 facings × 7 parts = 28 blockstate variants per table block, every part model exists, the felt top sits at
 * {@link FeltFrame#FELT_Y}·16 px and no element pokes above the rail top (craps walls excepted), and the loot table
 * drops only from part ∈ {main, single}.
 */
class TableAssetContractTest {
	private static final Path RES = Path.of("src/main/resources");
	private static final Path ASSETS = RES.resolve("assets/burmaldaholic");
	/** The seven {@code part} values of a table block (SINGLE + the 3×2 cells; MT-3 machine parts excluded). */
	private static final List<TablePart> TABLE_PARTS = List.of(TablePart.SINGLE, TablePart.MAIN, TablePart.LEFT, TablePart.RIGHT,
		TablePart.BACK_LEFT, TablePart.BACK, TablePart.BACK_RIGHT);
	private static final List<String> TABLES = List.of("blackjack_table", "blackjack_table_high_roller", "baccarat_table",
		"baccarat_table_high_roller", "baccarat_table_player_banked", "uth_table", "uth_table_high_roller", "uth_table_player_banked",
		"poker_table", "roulette_table", "roulette_table_high_roller", "craps_table");

	private static JsonObject read(Path p) throws IOException {
		return JsonParser.parseString(Files.readString(p)).getAsJsonObject();
	}

	@Test
	void everyTableNameHasAFootprint() {
		for (String t : TABLES) {
			assertTrue(TableGame.ofBlockName(t) != null, t);
		}
	}

	@Test
	void blockstatesHave28VariantsAndModelsExist() throws IOException {
		for (String t : TABLES) {
			JsonObject variants = read(ASSETS.resolve("blockstates/" + t + ".json")).getAsJsonObject("variants");
			assertEquals(28, variants.size(), t + " variants");
			for (String facing : List.of("north", "east", "south", "west")) {
				for (TablePart part : TABLE_PARTS) {
					String key = "facing=" + facing + ",part=" + part.getSerializedName();
					assertTrue(variants.has(key), t + " " + key);
					String model = variants.getAsJsonObject(key).get("model").getAsString();
					Path file = ASSETS.resolve("models/" + model.substring("burmaldaholic:".length()) + ".json");
					assertTrue(Files.exists(file), t + " " + key + " → " + file);
				}
			}
		}
	}

	@Test
	void feltTopIs14AndNothingAboveTheRail() throws IOException {
		double felt = FeltFrame.FELT_Y * 16;
		for (String t : TABLES) {
			boolean craps = t.startsWith("craps");
			double maxFelt = 0;
			for (TablePart part : TABLE_PARTS) {
				if (part == TablePart.SINGLE) continue;
				JsonObject m = read(ASSETS.resolve("models/block/tables/" + t + "/" + part.getSerializedName() + ".json"));
				Set<String> textures = new HashSet<>(m.getAsJsonObject("textures").keySet());
				assertTrue(textures.contains("particle"), t + " particle texture");
				for (JsonElement e : m.getAsJsonArray("elements")) {
					JsonObject el = e.getAsJsonObject();
					JsonArray from = el.getAsJsonArray("from");
					JsonArray to = el.getAsJsonArray("to");
					for (int i = 0; i < 3; i++) {
						// the padded rail bulges 1 px past the footprint; lit props (placard, tote board) stand above the block
						// rotated segments (curved rails, bowl rim, pedestal feet) are bounded before their rotation
						double lo = i == 1 ? 0 : el.has("rotation") ? -8 : -1;
						double hi = i == 1 ? 32 : el.has("rotation") ? 24 : 17;
						assertTrue(from.get(i).getAsDouble() >= lo && to.get(i).getAsDouble() <= hi && from.get(i).getAsDouble() <= to.get(i).getAsDouble(),
							t + " " + part + " element inside the block");
					}
					double top = to.get(1).getAsDouble();
					String tex = el.getAsJsonObject("faces").getAsJsonObject("up").get("texture").getAsString();
					// lit props and the raised roulette bowl rim stand above the rail
					boolean prop = tex.equals("#placard") || tex.equals("#tote") || (tex.equals("#trim") && top > 16) || (el.has("rotation") && top > 16);
					assertTrue(prop || top <= (craps ? 16 : FeltFrame.RAIL_TOP * 16 + 0.5), t + " " + part + " element top " + top);
					if (tex.equals("#felt")) {
						assertEquals(felt, top, 1e-9, t + " " + part + " felt top");
						maxFelt = Math.max(maxFelt, top);
					}
				}
			}
			assertEquals(felt, maxFelt, 1e-9, t + " has felt at " + felt);
		}
	}

	@Test
	void lootOnlyFromMainOrSingle() throws IOException {
		for (String t : TABLES) {
			String loot = Files.readString(RES.resolve("data/burmaldaholic/loot_table/blocks/" + t + ".json"));
			assertTrue(loot.contains("\"minecraft:survives_explosion\""), t);
			assertTrue(loot.contains("\"minecraft:block_state_property\""), t);
			assertTrue(loot.contains("\"part\": \"main\"") && loot.contains("\"part\": \"single\""), t);
			assertTrue(!loot.contains("\"part\": \"left\""), t);
		}
	}
}
