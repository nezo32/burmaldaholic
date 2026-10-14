package dev.nezo.burmaldaholic.worldgen.logic.blueprint;

import dev.nezo.burmaldaholic.core.table.parts.TableGame;
import dev.nezo.burmaldaholic.worldgen.logic.BlockSpec;
import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import dev.nezo.burmaldaholic.worldgen.logic.NpcRole;

/**
 * Furniture kits (architecture doc §3.4), shared by every style (lanes CB-3 / CB-4 reuse them).
 *
 * <p>Kit frame: {@code (x, y, z)} is the anchor on the floor row; {@code front} is the side users approach
 * from. Footprints below are given as {@code r} = cells to the RIGHT of a user facing the kit
 * ({@code front.rotate(-1)}) and {@code f} = cells towards the users (+f = in front, −f = behind).
 *
 * <p>Footprint rules every kit obeys (checked by {@link Checks}):
 * <ul>
 *   <li>game blocks sit on the floor row; the {@link #BOARD_CLEARANCE} volume above a table and the
 *       player-side cell in front of every game block stay air;</li>
 *   <li>decor never occupies a player-side cell, stools stand beside it (diagonal), so players walk up
 *       to the table and can click every seat;</li>
 *   <li>decor is physically supported (hanging lanterns hang from chains / fences / blocks, candles and
 *       pots stand on full tops, carpets lie on blocks);</li>
 *   <li>a kit writes only inside its documented footprint (listed per kit) — overlapping kits are a bug.</li>
 * </ul>
 */
public final class Kits {
	private Kits() {}

	/** Air kept above a table's footprint and walkway for the in-world board: rows y + 1..y + 3. */
	public static final int BOARD_CLEARANCE = 3;

	// ---- local frame helpers ---------------------------------------------------------------------------

	static int lx(int x, Facing front, int r, int f) {
		return x + r * front.rotate(-1).dx() + f * front.dx();
	}

	static int lz(int z, Facing front, int r, int f) {
		return z + r * front.rotate(-1).dz() + f * front.dz();
	}

	private static void put(Blueprint b, int x, int y, int z, Facing front, int r, int f, BlockSpec s) {
		b.set(lx(x, front, r, f), y, lz(z, front, r, f), s);
	}

	private static void put(Blueprint b, int x, int y, int z, Facing front, int r, int f, Role role) {
		b.set(lx(x, front, r, f), y, lz(z, front, r, f), role);
	}

	static BlockSpec lantern(Blueprint b, boolean hanging) {
		return BlockSpec.of(b.palette().get(Role.LIGHT_HANGING).full(), "hanging", Boolean.toString(hanging), "waterlogged", "false");
	}

	static BlockSpec chain(String axis) {
		return BlockSpec.mc("iron_chain", "axis", axis, "waterlogged", "false");
	}

	private static final java.util.List<String> WOODS = java.util.List.of("oak", "spruce", "birch", "jungle", "acacia",
		"dark_oak", "mangrove", "cherry", "pale_oak", "bamboo", "crimson", "warped");

	/**
	 * Wood type of a role's block ({@code dark_oak} for {@code stripped_dark_oak_log}, {@code crimson} for
	 * {@code crimson_stem} …); dark oak when the role is not wooden.
	 */
	public static String wood(Blueprint b, Role role) {
		String n = b.palette().get(role).full();
		String p = n.substring(n.indexOf(':') + 1).replace("stripped_", "");
		for (String suffix : new String[] {"_planks", "_log", "_wood", "_stem", "_hyphae", "_block"}) {
			if (p.endsWith(suffix) && WOODS.contains(p.substring(0, p.length() - suffix.length()))) {
				return p.substring(0, p.length() - suffix.length());
			}
		}
		return "dark_oak";
	}

	/** A fence of the FRAME wood (lamp posts, chandelier arms). */
	static BlockSpec fence(Blueprint b) {
		return BlockSpec.mc(wood(b, Role.FRAME) + "_fence");
	}

	/** A trapdoor of the FRAME wood (armrests, lamp shades, planter boxes, sconce brackets). */
	static BlockSpec trapdoor(Blueprint b, Facing facing, boolean open, String half) {
		return BlockSpec.mc(wood(b, Role.FRAME) + "_trapdoor", "facing", facing.id(), "half", half, "open", Boolean.toString(open),
			"powered", "false", "waterlogged", "false");
	}

	static BlockSpec candle(String color, int count) {
		return BlockSpec.mc(color == null ? "candle" : color + "_candle", "candles", Integer.toString(count), "lit", "true",
			"waterlogged", "false");
	}

	// ---- tables and machines ---------------------------------------------------------------------------

	/**
	 * Table pod (MT-DESIGN §6) — the full 3 × 2 table with its main block at the anchor (footprint r −1..1 × f −1..0),
	 * the players' walkway of CARPET_ACCENT at f +1 (r −1..1), stools at r ±2 f +1, the dealer at the table's dealer
	 * cell ({@code TableFootprint.dealerCell}: f −2, r 0 or r −1 for roulette) — an NPC of role {@code dealer}, or a
	 * dealer cell left as two blocks of air when {@code dealer} is null (the MT contract test: a bot dealer stands there) — and a keep-clear volume over footprint ∪ walkway for
	 * y + 1..y + 3 (the in-world board). Footprint r −2..2 × f −2..+1; {@link TableClearance} checks it.
	 */
	public static Kit tablePod(Game game, NpcRole dealer) {
		return (b, x, y, z, front) -> {
			if (b.compactTables()) {
				legacyPod(b, game, dealer, x, y, z, front);
				return;
			}
			b.game(game, x, y, z, front);
			Material stool = b.palette().get(Role.STOOL);
			for (int r = -1; r <= 1; r++) {
				put(b, x, y, z, front, r, 1, Role.CARPET_ACCENT);
				put(b, x, y + 1, z, front, r, 1, Blueprint.AIR);
			}
			for (int r : new int[] {-2, 2}) {
				put(b, x, y, z, front, r, 1, stool.slab("bottom"));
			}
			TableGame tg = game.tableGame();
			int dr = tg == null ? 0 : tg.dealerR(), df = tg == null ? -1 : tg.dealerF();
			int dx = lx(x, front, dr, df), dz = lz(z, front, dr, df);
			b.set(dx, y + 1, dz, Blueprint.AIR);
			if (dealer != null) {
				b.set(dx, y, dz, Blueprint.AIR);
				b.npc(dealer, dx, y, dz, front);
			} else {
				b.set(dx, y, dz, Blueprint.AIR); // MT contract: the dealer cell is two blocks of air (an NPC or a bot dealer)
			}
			int f0 = tg == null ? 0 : -1;
			b.keepClear(lx(x, front, -1, f0), y + 1, lz(z, front, -1, f0), lx(x, front, 1, 1), y + BOARD_CLEARANCE, lz(z, front, 1, 1));
			b.keepClear(lx(x, front, -1, 1), y, lz(z, front, -1, 1), lx(x, front, 1, 1), y + 1, lz(z, front, 1, 1));
		};
	}

	/**
	 * The pre-multi-block pod of legacy pieces ({@link Blueprint#compactTables()}): compact table at the anchor, player
	 * row of CARPET_ACCENT at f +1, stools at r ±2 f +1, dealer (NPC or lectern) at f −1, 3 × 3 × 3 keep-clear above.
	 */
	private static void legacyPod(Blueprint b, Game game, NpcRole dealer, int x, int y, int z, Facing front) {
		b.game(game, x, y, z, front);
		Material stool = b.palette().get(Role.STOOL);
		for (int r = -1; r <= 1; r++) {
			put(b, x, y, z, front, r, 1, Role.CARPET_ACCENT);
		}
		for (int r : new int[] {-2, 2}) {
			put(b, x, y, z, front, r, 1, stool.slab("bottom"));
		}
		int dx = lx(x, front, 0, -1), dz = lz(z, front, 0, -1);
		if (dealer != null) {
			b.set(dx, y, dz, Blueprint.AIR);
			b.npc(dealer, dx, y, dz, front);
		} else {
			b.set(dx, y, dz, BlockSpec.mc("lectern", "facing", front.id(), "has_book", "false", "powered", "false"));
		}
		b.keepClear(x - 1, y + 1, z - 1, x + 1, y + BOARD_CLEARANCE, z + 1);
		b.keepClear(x + front.dx(), y, z + front.dz(), x + front.dx(), y + 1, z + front.dz());
	}

	/**
	 * Slot bank — {@code count} machines in a row starting at the anchor and running to the RIGHT of a
	 * user facing the bank ({@code front.rotate(-1)}; for {@code front = SOUTH} that is +x), a neon strip
	 * (NEON_A) two rows above, LIGHT every other machine on the row above the strip. Footprint: count × 1,
	 * 4 rows tall; front cells stay clear.
	 */
	public static Kit slotBank(Game slots, int count) {
		return (b, x, y, z, front) -> {
			Facing along = front.rotate(-1);
			for (int i = 0; i < count; i++) {
				int sx = x + i * along.dx(), sz = z + i * along.dz();
				b.game(slots, sx, y, sz, front);
				b.set(sx, y + 2, sz, Role.NEON_A);
				b.set(sx, y + 3, sz, i % 2 == 0 ? Role.LIGHT : Role.SIGN_BACK);
				b.keepClear(sx + front.dx(), y, sz + front.dz(), sx + front.dx(), y + 1, sz + front.dz());
				if (b.compactTables()) { // a full slot (MT-3) is 2 tall: its topper fills y + 1
					b.set(sx, y + 1, sz, Blueprint.AIR);
					b.keepClear(sx, y + 1, sz, sx, y + 1, sz);
				}
			}
		};
	}

	/**
	 * Cashier cage — r −2..2 × f −1..0, 4 rows: the cashier block at the anchor with a lantern hanging over
	 * the window, COUNTER double slabs + iron-bar screens at r ±1 ±2, side screens at r ±2 f −1, the
	 * Croupier behind the window (sells chips and scratch cards), a COUNTER slab canopy with a TRIM crest.
	 */
	public static Kit cashierCage(Game cashier) {
		return (b, x, y, z, front) -> {
			b.game(cashier, x, y, z, front);
			BlockSpec bars = BlockSpec.mc("iron_bars");
			Material counter = b.palette().get(Role.COUNTER);
			boolean booth = !b.compactTables() && cashier.machineKind() != null; // MT-3: the cashier is a 3 × 2 booth
			for (int r : booth ? new int[] {-2, 2} : new int[] {-2, -1, 1, 2}) {
				put(b, x, y, z, front, r, 0, counter.slab("double"));
				put(b, x, y + 1, z, front, r, 0, bars);
			}
			for (int r : new int[] {-2, 2}) {
				put(b, x, y, z, front, r, -1, counter.slab("double"));
				put(b, x, y + 1, z, front, r, -1, bars);
				put(b, x, y + 2, z, front, r, -1, counter.slab("top"));
			}
			for (int r = -1; r <= 1; r++) {
				put(b, x, y, z, front, r, -1, Blueprint.AIR);
				put(b, x, y + 1, z, front, r, -1, Blueprint.AIR);
				put(b, x, y + 2, z, front, r, -1, counter.slab("top"));
			}
			for (int r = -2; r <= 2; r++) {
				put(b, x, y + 2, z, front, r, 0, counter.slab("top"));
				put(b, x, y + 3, z, front, r, 0, Math.abs(r) == 2 ? b.palette().block(Role.LIGHT) : b.palette().block(Role.TRIM));
			}
			if (!booth) {
				put(b, x, y + 1, z, front, 0, 0, lantern(b, true));
			}
			b.npc(NpcRole.CROUPIER, lx(x, front, 0, -1), y, lz(z, front, 0, -1), front);
			b.keepClear(x + front.dx(), y, z + front.dz(), x + front.dx(), y + 1, z + front.dz());
		};
	}

	/**
	 * Machine alcove for a wheel / plinko — r −2..2, 6 rows: the machine at the anchor, ACCENT pilasters at r ±2
	 * (3 rows) topped by BULBs, a TRIM lintel at y + 4 with a NEON_A keystone, a BULB crown; CARPET_ACCENT in front.
	 * The renderers draw the Wheel disc / Plinko board 0.3 into the side cells and their name plate up to y + 3.3,
	 * so the side cells r ±1 (y..y + 2) and the column above the machine (y + 1..y + 3) stay air
	 * ({@link TableClearance}).
	 */
	public static Kit machineAlcove(Game game) {
		return (b, x, y, z, front) -> {
			b.game(game, x, y, z, front);
			for (int r : new int[] {-2, 2}) {
				for (int dy = 0; dy <= 2; dy++) {
					put(b, x, y + dy, z, front, r, 0, Role.ACCENT);
				}
				put(b, x, y + 3, z, front, r, 0, Role.BULB);
			}
			int machineRows = b.compactTables() || game.machineKind() == null ? 1 : game.machineKind().height();
			for (int r = -1; r <= 1; r++) {
				for (int dy = r == 0 || !b.compactTables() ? machineRows : 0; dy <= 3; dy++) {
					put(b, x, y + dy, z, front, r, 0, Blueprint.AIR);
				}
			}
			for (int r = -2; r <= 2; r++) {
				put(b, x, y + 4, z, front, r, 0, r == 0 ? Role.NEON_A : Role.TRIM);
			}
			put(b, x, y + 5, z, front, 0, 0, Role.BULB);
			put(b, x, y, z, front, 0, 1, Role.CARPET_ACCENT);
			if (b.compactTables()) {
				b.keepClear(x, y + 1, z, x, y + 3, z);
			}
			b.keepClear(x + front.dx(), y, z + front.dz(), x + front.dx(), y + 1, z + front.dz());
		};
	}

	// ---- lighting ---------------------------------------------------------------------------------------

	/** Chandelier hanging from the ceiling cell at the anchor: chain + hanging lantern ({@code front} unused). */
	public static Kit chandelier(int drop) {
		return (b, x, y, z, front) -> {
			for (int i = 1; i < drop; i++) {
				b.set(x, y - i, z, chain("y"));
			}
			b.set(x, y - drop, z, lantern(b, true));
		};
	}

	/**
	 * Grand chandelier hanging from the ceiling cell at the anchor — 3 × 3 × (drop + 1): chain down to a
	 * TRIM hub at y − drop with four fence arms, five lanterns hanging one row below (arm ends + hub).
	 */
	public static Kit grandChandelier(int drop) {
		return (b, x, y, z, front) -> {
			for (int i = 1; i < drop; i++) {
				b.set(x, y - i, z, chain("y"));
			}
			int hy = y - drop;
			b.set(x, hy, z, Role.TRIM);
			b.set(x, hy - 1, z, lantern(b, true));
			for (Facing f : Facing.values()) {
				b.set(x + f.dx(), hy, z + f.dz(), fence(b));
				b.set(x + f.dx(), hy - 1, z + f.dz(), lantern(b, true));
			}
			for (int[] d : new int[][] {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) {
				b.set(x + d[0], hy + 1, z + d[1], chain("y"));
				b.set(x + d[0], hy, z + d[1], lantern(b, true));
			}
		};
	}

	/**
	 * Pendant (billiard) lamp over a table, hanging from the ceiling cell at the anchor — 3 × 3 × drop:
	 * chain down to a LIGHT block shaded by four open trapdoors at y − drop. Hang it so that the lamp row
	 * is above the table's board clearance.
	 */
	public static Kit pendantLamp(int drop) {
		return (b, x, y, z, front) -> {
			for (int i = 1; i < drop; i++) {
				b.set(x, y - i, z, chain("y"));
			}
			int ly = y - drop;
			b.set(x, ly, z, Role.LIGHT);
			for (Facing f : Facing.values()) {
				b.set(x + f.dx(), ly, z + f.dz(), trapdoor(b, f, true, "top"));
			}
		};
	}

	/**
	 * Lamp post — r −1..1, height + 1 rows: a fence column, a cross arm at the top with two lanterns
	 * hanging under it and one lantern standing on top.
	 */
	public static Kit lampPost(int height) {
		return (b, x, y, z, front) -> {
			put(b, x, y, z, front, 0, 0, Role.FOUNDATION);
			for (int i = 1; i < height; i++) {
				put(b, x, y + i, z, front, 0, 0, fence(b));
			}
			int top = y + height - 1;
			put(b, x, top, z, front, -1, 0, fence(b));
			put(b, x, top, z, front, 1, 0, fence(b));
			put(b, x, top - 1, z, front, -1, 0, lantern(b, true));
			put(b, x, top - 1, z, front, 1, 0, lantern(b, true));
			put(b, x, top + 1, z, front, 0, 0, lantern(b, false));
		};
	}

	/** Wall sconce on the wall behind the anchor — 1 × 2: a closed top trapdoor bracket with a lantern on it. */
	public static Kit wallSconce() {
		return (b, x, y, z, front) -> {
			b.set(x, y, z, trapdoor(b, front, false, "top"));
			b.set(x, y + 1, z, lantern(b, false));
		};
	}

	// ---- bar, lounge and decor -------------------------------------------------------------------------

	/**
	 * Bar — r 0..length−1 (plus end caps at r −1 and r length), f −2..+1, 3 rows: stools at f +1 every other
	 * cell, a COUNTER of upside-down stairs at f 0 with bottles (brewing stands), candles and pots on it, the
	 * bartender's aisle at f −1 (open at the right end), the back bar at f −2: barrels with bottles and
	 * candles on top and a second row of casks.
	 */
	public static Kit bar(int length) {
		return (b, x, y, z, front) -> {
			Material counter = b.palette().get(Role.COUNTER);
			Material stool = b.palette().get(Role.STOOL);
			put(b, x, y, z, front, -1, 0, counter.slab("double"));
			put(b, x, y, z, front, -1, -1, counter.slab("double"));
			put(b, x, y + 1, z, front, -1, 0, candle("red", 3));
			for (int r = 0; r < length; r++) {
				put(b, x, y, z, front, r, 0, counter.stairs(front.opposite(), true));
				put(b, x, y, z, front, r, 1, r % 2 == 0 ? stool.slab("bottom") : Blueprint.AIR);
				put(b, x, y, z, front, r, -1, Blueprint.AIR);
				put(b, x, y + 1, z, front, r, -1, Blueprint.AIR);
				BlockSpec onTop = switch (r % 4) {
					case 1 -> BlockSpec.mc("brewing_stand", "has_bottle_0", "true", "has_bottle_1", "false", "has_bottle_2", "true");
					case 3 -> candle(r % 8 == 3 ? "yellow" : "white", 2);
					default -> Blueprint.AIR;
				};
				put(b, x, y + 1, z, front, r, 0, onTop);
				put(b, x, y, z, front, r, -2, BlockSpec.mc("barrel", "facing", front.id(), "open", "false"));
				BlockSpec shelf = switch (r % 3) {
					case 0 -> BlockSpec.mc("brewing_stand", "has_bottle_0", "true", "has_bottle_1", "true", "has_bottle_2", "true");
					case 1 -> candle("orange", 4);
					default -> BlockSpec.mc("potted_fern");
				};
				put(b, x, y + 1, z, front, r, -2, shelf);
				put(b, x, y + 2, z, front, r, -2, r % 3 == 1 ? b.palette().block(Role.LIGHT)
					: BlockSpec.mc("barrel", "facing", front.id(), "open", "false"));
			}
		};
	}

	/**
	 * VIP sofa — r −1..width, f 0: a row of STOOL stairs (seat facing the users, backrest behind) between two
	 * open-trapdoor armrests, and a coffee table (STOOL top slab with a candle) at f +2 in the middle.
	 */
	public static Kit vipSofa(int width) {
		return (b, x, y, z, front) -> {
			Material stool = b.palette().get(Role.STOOL);
			for (int r = 0; r < width; r++) {
				put(b, x, y, z, front, r, 0, stool.stairs(front.opposite(), false));
			}
			Facing right = front.rotate(-1);
			put(b, x, y, z, front, -1, 0, trapdoor(b, right.opposite(), true, "bottom"));
			put(b, x, y, z, front, width, 0, trapdoor(b, right, true, "bottom"));
			int mid = (width - 1) / 2;
			put(b, x, y, z, front, mid, 2, stool.slab("top"));
			put(b, x, y + 1, z, front, mid, 2, candle("red", 3));
			if (width % 2 == 0) {
				put(b, x, y, z, front, mid + 1, 2, stool.slab("top"));
				put(b, x, y + 1, z, front, mid + 1, 2, BlockSpec.mc("potted_red_tulip"));
			}
		};
	}

	/**
	 * Planter — plus-shaped r −1..1 × f −1..1, 2 rows: a moss block with a flowering azalea on it, boxed by
	 * four open trapdoors hugging its sides.
	 */
	public static Kit planter() {
		return (b, x, y, z, front) -> {
			b.set(x, y, z, BlockSpec.mc("moss_block"));
			b.set(x, y + 1, z, BlockSpec.mc("flowering_azalea"));
			for (Facing f : Facing.values()) {
				b.set(x + f.dx(), y, z + f.dz(), trapdoor(b, f, true, "bottom"));
			}
		};
	}

	/** Potted plant on a pedestal — 1 × 1, 2 rows: COUNTER double slab + the PLANTER pot. */
	public static Kit pottedPlant() {
		return (b, x, y, z, front) -> {
			b.set(x, y, z, b.palette().get(Role.COUNTER).slab("double"));
			b.set(x, y + 1, z, Role.PLANTER);
		};
	}

	/**
	 * Velvet-rope barrier — r 0..length−1, 1 row: glowing end-rod stanchions at both ends (and every 4),
	 * chain ropes between them. Never put it across a player cell.
	 */
	public static Kit ropeBarrier(int length) {
		return (b, x, y, z, front) -> {
			Facing right = front.rotate(-1);
			String axis = right.dx() != 0 ? "x" : "z";
			for (int r = 0; r < length; r++) {
				boolean post = r == 0 || r == length - 1 || r % 4 == 0;
				put(b, x, y, z, front, r, 0, post ? BlockSpec.mc("end_rod", "facing", "up") : chain(axis));
			}
		};
	}

	/**
	 * Duel pit for Dice Duel / Coin Flip throws — 5 × 5 centred on the anchor: a ring of COUNTER stairs
	 * stepping down one block into a 3 × 3 green-felt floor, glowing end-rod posts on the ring corners.
	 * Writes the floor-block layer (y − 1) and the one below it (y − 2).
	 */
	public static Kit duelPit() {
		return (b, x, y, z, front) -> {
			Material counter = b.palette().get(Role.COUNTER);
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					b.set(x + dx, y, z + dz, Blueprint.AIR);
					b.set(x + dx, y + 1, z + dz, Blueprint.AIR);
					boolean ring = Math.abs(dx) == 2 || Math.abs(dz) == 2;
					if (ring) {
						Facing out = Math.abs(dx) == 2 ? (dx > 0 ? Facing.EAST : Facing.WEST) : (dz > 0 ? Facing.SOUTH : Facing.NORTH);
						b.set(x + dx, y - 1, z + dz, counter.stairs(out, false));
					} else {
						b.set(x + dx, y - 2, z + dz, Role.FLOOR_ACCENT);
						b.set(x + dx, y - 1, z + dz, BlockSpec.mc("green_carpet"));
					}
				}
			}
			for (int[] d : new int[][] {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
				b.set(x + d[0], y - 1, z + d[1], counter.slab("double"));
				b.set(x + d[0], y, z + d[1], BlockSpec.mc("end_rod", "facing", "up"));
			}
			b.keepClear(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1);
		};
	}

	/** Chip stacks — 1 × 1, 2 rows: a COUNTER pedestal with three lit red candles (a stack of chips). */
	public static Kit chipStacks() {
		return (b, x, y, z, front) -> {
			b.set(x, y, z, b.palette().get(Role.COUNTER).slab("double"));
			b.set(x, y + 1, z, candle("red", 3));
		};
	}

	/**
	 * Fireplace against the wall behind the anchor — r −2..2 × f −1..0, {@code height} rows: WALL_BASE surround
	 * with a lit campfire in the firebox (f −1), a COUNTER mantel (upside-down stairs) at y + 2 with candles,
	 * the chimney breast up to y + height − 1.
	 */
	public static Kit fireplace(int height) {
		return (b, x, y, z, front) -> {
			Material base = b.palette().get(Role.WALL_BASE);
			for (int r = -2; r <= 2; r++) {
				for (int f = -1; f <= 0; f++) {
					for (int dy = 0; dy < height; dy++) {
						boolean opening = Math.abs(r) <= 1 && f == 0 && dy <= 1 || r == 0 && f == -1 && dy <= 1;
						boolean breast = Math.abs(r) <= 1 || f == -1;
						if (opening) {
							put(b, x, y + dy, z, front, r, f, Blueprint.AIR);
						} else if (dy <= 1 || breast) {
							put(b, x, y + dy, z, front, r, f, base.block());
						}
					}
				}
			}
			put(b, x, y, z, front, 0, -1, BlockSpec.mc("campfire", "facing", front.id(), "lit", "true", "signal_fire", "false",
				"waterlogged", "false"));
			for (int r = -2; r <= 2; r++) {
				put(b, x, y + 2, z, front, r, 0, b.palette().get(Role.COUNTER).stairs(front.opposite(), true));
			}
			put(b, x, y + 3, z, front, -2, 0, candle("white", 3));
			put(b, x, y + 3, z, front, 2, 0, candle("white", 3));
			for (int dy = 3; dy < height; dy++) {
				put(b, x, y + dy, z, front, -2, 0, dy == 3 ? candle("white", 3) : Blueprint.AIR);
				put(b, x, y + dy, z, front, 2, 0, dy == 3 ? candle("white", 3) : Blueprint.AIR);
			}
		};
	}

	/**
	 * Portico (porte-cochère) in front of a door — r 0..width−1 × f 0..−(depth−1) (anchor = the outer left
	 * column), height + 2 rows: paired ACCENT columns at both ends of the outer row (r 0, 2 and width−3,
	 * width−1) leaving the middle open, a canopy deck at y + height whose front fascia alternates TRIM and
	 * BULB (a marquee edge that glows at night), ROOF_TRIM slabs on top, lanterns hanging under the deck.
	 */
	public static Kit portico(int width, int depth, int height) {
		return (b, x, y, z, front) -> {
			Material cap = b.palette().get(Role.ROOF_TRIM);
			for (int r = 0; r < width; r++) {
				boolean column = r == 0 || r == 2 || r == width - 3 || r == width - 1;
				for (int dy = 0; dy < height; dy++) {
					put(b, x, y + dy, z, front, r, 0, column ? b.palette().block(Role.ACCENT) : Blueprint.AIR);
				}
				for (int f = 0; f > -depth; f--) {
					boolean edge = f == 0 || r == 0 || r == width - 1;
					BlockSpec deck = edge ? (f == 0 && r % 2 == 1 ? b.palette().block(Role.BULB) : b.palette().block(Role.TRIM))
						: b.palette().block(Role.CEILING);
					put(b, x, y + height, z, front, r, f, deck);
					put(b, x, y + height + 1, z, front, r, f, edge ? cap.stairs(f == 0 ? front.opposite()
						: r == 0 ? front.rotate(-1) : r == width - 1 ? front.rotate(1) : front.opposite(), false) : cap.slab("bottom"));
					for (int dy = 0; dy < height && f < 0; dy++) {
						put(b, x, y + dy, z, front, r, f, Blueprint.AIR);
					}
				}
				if (!column && (r == width / 2 - 2 || r == width / 2 + 2)) {
					put(b, x, y + height - 1, z, front, r, -1, lantern(b, true));
				}
			}
		};
	}
}
