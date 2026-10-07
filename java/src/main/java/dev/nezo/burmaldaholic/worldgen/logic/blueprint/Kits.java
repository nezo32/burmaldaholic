package dev.nezo.burmaldaholic.worldgen.logic.blueprint;

import dev.nezo.burmaldaholic.worldgen.logic.BlockSpec;
import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import dev.nezo.burmaldaholic.worldgen.logic.NpcRole;

/**
 * Furniture kits (architecture doc §3.4). Skeleton set; lane B1 owns this file and adds BAR, VIP_SOFA,
 * PLANTER, ROPE_BARRIER, DUEL_PIT, LAMP_POST, PORTICO.
 *
 * <p>Footprint rules every kit obeys (checked by {@link Checks}):
 * <ul>
 *   <li>game blocks sit on the floor row; the {@link #BOARD_CLEARANCE} volume above a table and the
 *       player-side cell in front of every game block stay air;</li>
 *   <li>decor never occupies a player-side cell, stools stand beside it (diagonal), so players walk up
 *       to the table and can click every seat;</li>
 *   <li>a kit writes only inside its documented footprint (listed per kit) — overlapping kits are a bug.</li>
 * </ul>
 */
public final class Kits {
	private Kits() {}

	/** Air kept above a table for the in-world board renderer: 3 × 3 cells wide, 3 rows tall. */
	public static final int BOARD_CLEARANCE = 3;

	/**
	 * Table pod — footprint 5 (across) × 4 (deep) around the table at the anchor: player row in front
	 * (the {@code front} side), stools at both ends of it, dealer cell behind the table with a dealer NPC
	 * when {@code dealer} is set, accent carpet under the player row, keep-clear volume for the board.
	 */
	public static Kit tablePod(Game game, NpcRole dealer) {
		return (b, x, y, z, front) -> {
			Facing across = front.rotate(1);
			b.game(game, x, y, z, front);
			// player row: three cells in front of the table stay walkable (accent carpet on the floor row)
			for (int t = -1; t <= 1; t++) {
				int px = x + front.dx() + t * across.dx();
				int pz = z + front.dz() + t * across.dz();
				b.set(px, y, pz, Role.CARPET_ACCENT);
			}
			// stools beside the player row ends (decor, diagonal to the table)
			Material stool = b.palette().get(Role.STOOL);
			for (int t : new int[] {-2, 2}) {
				int sx = x + front.dx() + t * across.dx();
				int sz = z + front.dz() + t * across.dz();
				b.set(sx, y, sz, stool.slab("bottom"));
			}
			// dealer cell behind the table
			int dx = x - front.dx(), dz = z - front.dz();
			if (dealer != null) {
				b.npc(dealer, dx, y, dz, front);
			}
			// board clearance above the table
			b.keepClear(x - 1, y + 1, z - 1, x + 1, y + BOARD_CLEARANCE, z + 1);
			b.keepClear(x + front.dx(), y, z + front.dz(), x + front.dx(), y + 1, z + front.dz());
		};
	}

	/**
	 * Slot bank — {@code count} machines in a row starting at the anchor and running to the RIGHT of a
	 * user facing the bank ({@code front.rotate(-1)}; for {@code front = SOUTH} that is +x), a neon strip (NEON_A) two rows above, LIGHT every other
	 * machine on the row above the strip. Footprint: count × 1, 4 rows tall; front cells stay clear.
	 */
	public static Kit slotBank(Game slots, int count) {
		return (b, x, y, z, front) -> {
			Facing along = front.rotate(-1);
			for (int i = 0; i < count; i++) {
				int sx = x + i * along.dx(), sz = z + i * along.dz();
				b.game(slots, sx, y, sz, front);
				b.set(sx, y + 2, sz, Role.NEON_A);
				if (i % 2 == 0) {
					b.set(sx, y + 3, sz, Role.LIGHT);
				}
				b.keepClear(sx + front.dx(), y, sz + front.dz(), sx + front.dx(), y + 1, sz + front.dz());
				b.keepClear(sx, y + 1, sz, sx, y + 1, sz);
			}
		};
	}

	/**
	 * Cashier cage — 5 × 3: the cashier block at the anchor, iron-bar screens on both sides of it,
	 * Croupier NPC behind (sells chips and scratch cards), COUNTER slabs, LIGHT_HANGING above the window.
	 */
	public static Kit cashierCage(Game cashier) {
		return (b, x, y, z, front) -> {
			Facing across = front.rotate(1);
			b.game(cashier, x, y, z, front);
			BlockSpec bars = BlockSpec.mc("iron_bars", "east", "false", "west", "false", "north", "false", "south", "false",
				"waterlogged", "false");
			for (int t : new int[] {-2, -1, 1, 2}) {
				int cx = x + t * across.dx(), cz = z + t * across.dz();
				b.set(cx, y, cz, b.palette().get(Role.COUNTER).slab("double"));
				b.set(cx, y + 1, cz, bars);
			}
			b.npc(NpcRole.CROUPIER, x - front.dx(), y, z - front.dz(), front);
			b.set(x, y + 2, z, BlockSpec.mc("lantern", "hanging", "false", "waterlogged", "false"));
			b.keepClear(x + front.dx(), y, z + front.dz(), x + front.dx(), y + 1, z + front.dz());
		};
	}

	/** Chandelier hanging from the ceiling cell at the anchor: chain + hanging lantern ({@code front} unused). */
	public static Kit chandelier(int drop) {
		return (b, x, y, z, front) -> {
			for (int i = 1; i < drop; i++) {
				b.set(x, y - i, z, BlockSpec.mc("iron_chain", "axis", "y", "waterlogged", "false"));
			}
			b.set(x, y - drop, z, BlockSpec.of(b.palette().get(Role.LIGHT_HANGING).full(), "hanging", "true", "waterlogged", "false"));
		};
	}
}
