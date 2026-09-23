package dev.nezo.burmaldaholic.worldgen.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Casino building layouts (GAME_DESIGN §16). PURE — one source of truth for the committed structure
 * templates ({@code TemplateExporter}) and for the code that places them.
 *
 * <p>Same buildings as the Bedrock edition ({@code bedrock/src/worldgen/logic/layouts.ts}), adapted
 * to Java: game tables are part of the template (a table whose module is missing loads as air), NPCs
 * and loot chests are data markers handled in code. Village casinos have one template per village
 * type (the spec's "palette variant"): Java 26.3 changed the processor-list JSON format, separate
 * templates stay valid on both supported versions.
 */
public final class Layouts {
	private Layouts() {}

	// ---- table ids (owned by other modules; ids from STRINGS.md) --------------------------------

	public static final String CASHIER = "burmaldaholic:cashier";
	public static final String NETHER_CASHIER = "burmaldaholic:nether_cashier";
	public static final String BLACKJACK = "burmaldaholic:blackjack_table";
	public static final String BLACKJACK_HIGH_ROLLER = "burmaldaholic:blackjack_table_high_roller";
	public static final String POKER = "burmaldaholic:poker_table";
	public static final String ROULETTE = "burmaldaholic:roulette_table";
	public static final String ROULETTE_HIGH_ROLLER = "burmaldaholic:roulette_table_high_roller";
	public static final String CRAPS = "burmaldaholic:craps_table";
	public static final String SLOTS_COPPER = "burmaldaholic:slot_machine_copper";
	public static final String SLOTS_GOLD = "burmaldaholic:slot_machine_gold";
	public static final String SLOTS_NETHERITE = "burmaldaholic:slot_machine_netherite";
	public static final String WHEEL = "burmaldaholic:wheel_of_fortune";
	public static final String PLINKO = "burmaldaholic:plinko_machine";

	private static final BlockSpec AIR = BlockSpec.mc("air");

	private static BlockSpec table(String id, Facing facing) {
		return BlockSpec.of(id, "facing", facing.id());
	}

	// ---- "CASINO" pixel font (3×5, I is 1 wide; letters alternate materials, no gaps) -----------

	private static final Map<Character, String[]> FONT = Map.of(
		'C', new String[] {"###", "#..", "#..", "#..", "###"},
		'A', new String[] {"###", "#.#", "###", "#.#", "#.#"},
		'S', new String[] {"###", "#..", "###", "..#", "###"},
		'I', new String[] {"#", "#", "#", "#", "#"},
		'N', new String[] {"##.", "#.#", "#.#", "#.#", "#.#"},
		'O', new String[] {"###", "#.#", "#.#", "#.#", "###"});

	/** A lit pixel of the sign: column from the left, row from the top (0..4), letter index. */
	public record SignPixel(int col, int row, int letter) {}

	public record Sign(List<SignPixel> pixels, int width) {}

	public static Sign signPixels(String word) {
		List<SignPixel> pixels = new ArrayList<>();
		int col = 0;
		for (int letter = 0; letter < word.length(); letter++) {
			String[] glyph = FONT.get(word.charAt(letter));
			if (glyph == null) {
				throw new IllegalArgumentException("no glyph for '" + word.charAt(letter) + "'");
			}
			for (int row = 0; row < glyph.length; row++) {
				for (int dx = 0; dx < glyph[row].length(); dx++) {
					if (glyph[row].charAt(dx) == '#') {
						pixels.add(new SignPixel(col + dx, row, letter));
					}
				}
			}
			col += glyph[0].length();
		}
		return new Sign(pixels, col);
	}

	// ---- village casino "Lucky Villager" 17 × 10 × 17 (§16.1) -----------------------------------

	private record VillagePalette(BlockSpec floor, BlockSpec wall, BlockSpec corner, BlockSpec roof, BlockSpec foundation, BlockSpec carpet) {}

	private static VillagePalette palette(VillageStyle style) {
		return switch (style) {
			case PLAINS -> new VillagePalette(BlockSpec.mc("oak_planks"), BlockSpec.mc("oak_planks"), BlockSpec.mc("cobblestone"),
				BlockSpec.mc("dark_oak_planks"), BlockSpec.mc("cobblestone"), BlockSpec.mc("red_carpet"));
			case DESERT -> new VillagePalette(BlockSpec.mc("cut_sandstone"), BlockSpec.mc("smooth_sandstone"), BlockSpec.mc("chiseled_sandstone"),
				BlockSpec.mc("sandstone"), BlockSpec.mc("sandstone"), BlockSpec.mc("red_carpet"));
			case SAVANNA -> new VillagePalette(BlockSpec.mc("acacia_planks"), BlockSpec.mc("acacia_planks"), BlockSpec.mc("orange_terracotta"),
				BlockSpec.mc("orange_terracotta"), BlockSpec.mc("cobblestone"), BlockSpec.mc("red_carpet"));
			case TAIGA -> new VillagePalette(BlockSpec.mc("spruce_planks"), BlockSpec.mc("spruce_planks"), BlockSpec.mc("mossy_cobblestone"),
				BlockSpec.mc("dark_oak_planks"), BlockSpec.mc("cobblestone"), BlockSpec.mc("green_carpet"));
			case SNOWY -> new VillagePalette(BlockSpec.mc("spruce_planks"), BlockSpec.mc("spruce_planks"), BlockSpec.mc("packed_ice"),
				BlockSpec.mc("snow_block"), BlockSpec.mc("cobblestone"), BlockSpec.mc("red_carpet"));
		};
	}

	/** Glass pane in a wall running along x (connects east/west) or along z (north/south). */
	private static BlockSpec pane(boolean alongX) {
		return alongX
			? BlockSpec.mc("glass_pane", "east", "true", "west", "true", "north", "false", "south", "false", "waterlogged", "false")
			: BlockSpec.mc("glass_pane", "east", "false", "west", "false", "north", "true", "south", "true", "waterlogged", "false");
	}

	private static Layout villageCasino(VillageStyle style) {
		VillagePalette p = palette(style);
		Grid g = new Grid(17, 10, 17);
		// Clear the whole volume (terrain, grass, tree canopies) then build.
		g.box(0, 0, 0, 16, 9, 16, AIR);
		g.box(0, 0, 0, 16, 0, 16, p.floor());
		g.walls(0, 0, 0, 16, 0, 16, p.foundation());
		g.walls(0, 1, 0, 16, 4, 16, p.wall());
		for (int[] c : new int[][] {{0, 0}, {16, 0}, {0, 16}, {16, 16}}) {
			g.box(c[0], 1, c[1], c[0], 4, c[1], p.corner());
		}
		// windows
		for (int z : new int[] {3, 4, 11, 12}) {
			for (int x : new int[] {0, 16}) {
				g.box(x, 2, z, x, 3, z, pane(false));
			}
		}
		for (int x : new int[] {3, 4, 12, 13}) {
			g.box(x, 2, 0, x, 3, 0, pane(true));
			g.box(x, 2, 16, x, 3, 16, pane(true));
		}
		// roof + ceiling lights
		g.box(0, 5, 0, 16, 5, 16, p.roof());
		for (int[] c : new int[][] {{4, 3}, {12, 3}, {4, 9}, {12, 9}, {4, 13}, {12, 13}, {8, 11}}) {
			g.set(c[0], 5, c[1], BlockSpec.mc("glowstone"));
		}
		// back room (z 1..4) behind an inner wall at z = 5 with a doorway
		g.box(1, 1, 5, 15, 4, 5, p.wall());
		g.box(8, 1, 5, 8, 2, 5, AIR);
		g.box(1, 1, 1, 3, 2, 1, BlockSpec.mc("bookshelf"));
		g.box(13, 1, 1, 15, 2, 1, BlockSpec.mc("bookshelf"));
		// hall carpet (tables are placed instead of it)
		g.box(1, 1, 6, 15, 1, 15, p.carpet());
		// entrance (3 wide, 3 high) on the +z face
		g.box(7, 1, 16, 9, 3, 16, AIR);
		g.box(7, 0, 16, 9, 0, 16, p.floor());
		// "CASINO" sign on the front edge of the roof: glowstone / lit lamps (powered from behind)
		Sign sign = signPixels("CASINO");
		int left = (17 - sign.width()) / 2;
		BlockSpec back = BlockSpec.mc("black_wool");
		g.box(0, 6, 16, 16, 9, 16, back);
		g.box(0, 6, 15, 16, 9, 15, back);
		for (SignPixel px : sign.pixels()) {
			int x = left + px.col();
			int y = 9 - px.row();
			boolean lamp = px.letter() % 2 == 1;
			g.set(x, y, 16, lamp ? BlockSpec.mc("redstone_lamp", "lit", "true") : BlockSpec.mc("glowstone"));
			if (lamp) {
				g.set(x, y, 15, BlockSpec.mc("redstone_block"));
			}
		}
		// tables (§16.1: Cashier, Blackjack, Roulette, Copper Bandit ×3, Golden Reels, Wheel of Fortune)
		g.set(2, 1, 6, table(SLOTS_COPPER, Facing.SOUTH));
		g.set(3, 1, 6, table(SLOTS_COPPER, Facing.SOUTH));
		g.set(4, 1, 6, table(SLOTS_COPPER, Facing.SOUTH));
		g.set(11, 1, 6, table(SLOTS_GOLD, Facing.SOUTH));
		g.set(13, 1, 6, table(WHEEL, Facing.SOUTH));
		g.set(4, 1, 10, table(BLACKJACK, Facing.SOUTH));
		g.set(12, 1, 10, table(ROULETTE, Facing.SOUTH));
		g.set(15, 1, 13, table(CASHIER, Facing.WEST));

		String id = "village_casino_" + style.id();
		List<Layout.PlacedMarker> markers = List.of(
			new Layout.PlacedMarker(new Vec(8, 3, 11), new Markers.Anchor(id)),
			new Layout.PlacedMarker(new Vec(2, 2, 13), new Markers.Npc(NpcRole.CROUPIER, Facing.EAST)),
			new Layout.PlacedMarker(new Vec(8, 2, 7), new Markers.Npc(NpcRole.LOAN_SHARK, Facing.SOUTH)),
			new Layout.PlacedMarker(new Vec(8, 1, 1), new Markers.Chest(CasinoLoot.Table.VILLAGE_CASINO, Facing.SOUTH)));
		// Street-end connector (like vanilla street terminators): the door faces the end of a village road.
		List<Layout.Jigsaw> jigsaws = List.of(new Layout.Jigsaw(new Vec(8, 0, 16), "south_up",
			"minecraft:street", "minecraft:street", "minecraft:empty", p.floor().toString(), "aligned"));
		return new Layout(id, CasinoKind.VILLAGE_CASINO, g, markers, jigsaws);
	}

	// ---- Piglin Parlor 21 × 12 × 21 (§16.2) ------------------------------------------------------

	private static Layout piglinParlor() {
		Grid g = new Grid(21, 12, 21);
		BlockSpec bricks = BlockSpec.mc("polished_blackstone_bricks");
		// rows 0..8 are the room; 9..11 stay structure void so the Nether rock above is kept
		g.box(0, 0, 0, 20, 8, 20, AIR);
		g.box(0, 0, 0, 20, 0, 20, bricks);
		g.walls(0, 1, 0, 20, 7, 20, bricks);
		g.box(0, 8, 0, 20, 8, 20, BlockSpec.mc("blackstone"));
		// gold pillars every 5 blocks + gilded band
		for (int v : new int[] {0, 5, 10, 15, 20}) {
			for (int[] c : new int[][] {{v, 0}, {v, 20}, {0, v}, {20, v}}) {
				g.box(c[0], 1, c[1], c[0], 7, c[1], BlockSpec.mc("gold_block"));
			}
		}
		for (int v = 1; v < 20; v++) {
			if (v % 5 == 0) {
				continue;
			}
			for (int[] c : new int[][] {{v, 0}, {v, 20}, {0, v}, {20, v}}) {
				g.set(c[0], 5, c[1], BlockSpec.mc("gilded_blackstone"));
			}
		}
		// crimson floor centre with a carpet runner, shroomlight ceiling grid
		g.box(3, 0, 3, 17, 0, 17, BlockSpec.mc("crimson_planks"));
		g.box(9, 1, 8, 11, 1, 19, BlockSpec.mc("red_carpet"));
		for (int x = 2; x <= 18; x += 4) {
			for (int z = 2; z <= 18; z += 4) {
				g.set(x, 8, z, BlockSpec.mc("shroomlight"));
			}
		}
		// main entrance on +z (faces away from the bastion) and a back door towards the bastion
		g.box(9, 1, 20, 11, 3, 20, AIR);
		g.box(9, 1, 0, 11, 3, 0, AIR);
		// tables (§16.2: Craps, Poker, Golden Reels ×2, Plinko, Nether Cashier)
		g.set(6, 1, 6, table(CRAPS, Facing.SOUTH));
		g.set(14, 1, 6, table(POKER, Facing.SOUTH));
		g.set(1, 1, 11, table(SLOTS_GOLD, Facing.EAST));
		g.set(1, 1, 13, table(SLOTS_GOLD, Facing.EAST));
		g.set(19, 1, 11, table(PLINKO, Facing.WEST));
		g.set(19, 1, 15, table(NETHER_CASHIER, Facing.WEST));

		List<Layout.PlacedMarker> markers = List.of(
			new Layout.PlacedMarker(new Vec(10, 4, 10), new Markers.Anchor("piglin_parlor")),
			new Layout.PlacedMarker(new Vec(6, 2, 4), new Markers.Npc(NpcRole.PIGLIN_DEALER, Facing.SOUTH)),
			new Layout.PlacedMarker(new Vec(14, 2, 4), new Markers.Npc(NpcRole.PIGLIN_DEALER, Facing.SOUTH)),
			new Layout.PlacedMarker(new Vec(10, 2, 3), new Markers.Npc(NpcRole.PIGLIN_MONEYLENDER, Facing.SOUTH)),
			new Layout.PlacedMarker(new Vec(1, 1, 19), new Markers.Chest(CasinoLoot.Table.PIGLIN_PARLOR, Facing.EAST)));
		return new Layout("piglin_parlor", CasinoKind.PIGLIN_PARLOR, g, markers, List.of());
	}

	// ---- End City High Roller Lounge 13 × 9 × 13 (§16.3) ----------------------------------------

	private static Layout highRollerLounge() {
		Grid g = new Grid(13, 9, 13);
		BlockSpec purpur = BlockSpec.mc("purpur_block");
		g.box(0, 0, 0, 12, 8, 12, AIR);
		g.box(0, 0, 0, 12, 0, 12, purpur);
		g.box(1, 0, 1, 11, 0, 11, BlockSpec.mc("obsidian"));
		g.box(5, 0, 5, 7, 0, 7, BlockSpec.mc("crying_obsidian"));
		g.walls(0, 1, 0, 12, 1, 12, purpur);
		g.walls(0, 2, 0, 12, 4, 12, BlockSpec.mc("magenta_stained_glass"));
		g.walls(0, 5, 0, 12, 5, 12, purpur);
		for (int[] c : new int[][] {{0, 0}, {12, 0}, {0, 12}, {12, 12}}) {
			g.box(c[0], 1, c[1], c[0], 5, c[1], purpur);
			g.set(c[0], 7, c[1], BlockSpec.mc("end_rod", "facing", "up"));
		}
		g.box(0, 6, 0, 12, 6, 12, purpur);
		for (int[] c : new int[][] {{3, 3}, {9, 3}, {3, 9}, {9, 9}}) {
			g.set(c[0], 5, c[1], BlockSpec.mc("end_rod", "facing", "down"));
		}
		g.box(1, 1, 1, 11, 1, 11, BlockSpec.mc("purple_carpet"));
		g.box(5, 1, 12, 7, 3, 12, AIR);
		// tables (§16.3: Netherite High Roller ×2, High-Roller Blackjack, High-Roller Roulette, Cashier)
		g.set(2, 1, 1, table(SLOTS_NETHERITE, Facing.SOUTH));
		g.set(4, 1, 1, table(SLOTS_NETHERITE, Facing.SOUTH));
		g.set(4, 1, 6, table(BLACKJACK_HIGH_ROLLER, Facing.SOUTH));
		g.set(8, 1, 6, table(ROULETTE_HIGH_ROLLER, Facing.SOUTH));
		g.set(10, 1, 1, table(CASHIER, Facing.SOUTH));

		List<Layout.PlacedMarker> markers = List.of(
			new Layout.PlacedMarker(new Vec(6, 3, 6), new Markers.Anchor("high_roller_lounge")),
			new Layout.PlacedMarker(new Vec(10, 2, 9), new Markers.Npc(NpcRole.SHULKER_CROUPIER, Facing.WEST)),
			new Layout.PlacedMarker(new Vec(6, 1, 1), new Markers.Chest(CasinoLoot.Table.HIGH_ROLLER, Facing.SOUTH)));
		return new Layout("high_roller_lounge", CasinoKind.HIGH_ROLLER, g, markers, List.of());
	}

	// ---- registry --------------------------------------------------------------------------------

	private static Map<String, Layout> cache;

	public static synchronized Map<String, Layout> all() {
		if (cache == null) {
			Map<String, Layout> m = new LinkedHashMap<>();
			for (VillageStyle s : VillageStyle.values()) {
				Layout l = villageCasino(s);
				m.put(l.id(), l);
			}
			Layout parlor = piglinParlor();
			m.put(parlor.id(), parlor);
			Layout lounge = highRollerLounge();
			m.put(lounge.id(), lounge);
			cache = Collections.unmodifiableMap(m); // keeps insertion order
		}
		return cache;
	}

	public static Optional<Layout> byId(String id) {
		return Optional.ofNullable(all().get(id));
	}

	public static Layout village(VillageStyle style) {
		return all().get("village_casino_" + style.id());
	}

	public static Layout piglinParlorLayout() {
		return all().get("piglin_parlor");
	}

	public static Layout highRollerLayout() {
		return all().get("high_roller_lounge");
	}

	/** The layout used for a (non-village) kind. */
	public static Layout forKind(CasinoKind kind, VillageStyle villageStyle) {
		return switch (kind) {
			case VILLAGE_CASINO -> village(villageStyle);
			case PIGLIN_PARLOR -> piglinParlorLayout();
			case HIGH_ROLLER -> highRollerLayout();
		};
	}
}
