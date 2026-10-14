package dev.nezo.burmaldaholic.worldgen.logic;

import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Blueprint;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Game;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Kits;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Palettes;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Piece;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Role;
import java.util.List;

/**
 * PLATFORM TEST FIXTURE, never shipped (only with {@code -Dburmaldaholic.casinoPlaceholders=true}, see
 * {@link CasinoPack}): plain boxes with the contract's piece ids, sizes, connectors, floor row and every game
 * family (architecture doc §4.1), so the platform — pack generation, the {@code burmaldaholic:casino} structure
 * type, completeness, one-record-per-start, the spawn survey and {@code /locate} — can be proven before the real
 * buildings exist. Ugly on purpose.
 *
 * <p>Vertical frame shared by every piece: foundation rows y 0..1, FLOOR blocks at y = 2 (with
 * {@code start_height -3} that is the ground level), floor row (first air) y = 3, connectors in the floor
 * block row of each doorway, replaced by the FLOOR block.
 */
public final class PlaceholderPieces {
	private PlaceholderPieces() {}

	static final int FLOOR_Y = 2;
	static final int ROW = 3;

	public static List<Piece> style(String style) {
		return List.of(entrance(style), floor(style, "floor_a", false), floor(style, "floor_b", true), bar(style), office(style), vip(style));
	}

	private static String id(String style, String piece) {
		return "casino/" + style + "/" + piece;
	}

	private static String conn(String name) {
		return "burmaldaholic:casino/" + name;
	}

	private static Blueprint shell(String style, String piece, int sx, int sy, int sz, int ceilingY) {
		Blueprint b = Blueprint.of(id(style, piece), CasinoKind.CASINO, sx, sy, sz, Palettes.TUDOR);
		b.foundation(0, 0, sx - 1, sz - 1, FLOOR_Y - 1);
		b.room(0, FLOOR_Y, 0, sx - 1, ceilingY, sz - 1).frame(5).done();
		for (int x = 2; x < sx - 1; x += 4) {
			for (int z = 2; z < sz - 1; z += 4) {
				b.set(x, ceilingY, z, Role.LIGHT);
			}
		}
		return b;
	}

	/** Entry connector of a child piece: front (+z) wall, floor block row, centred on {@code x}. */
	private static void entry(Blueprint b, int x, int sz, String name) {
		b.clear(x - 1, ROW, sz - 1, x + 1, ROW + 2, sz - 1);
		b.jigsaw(x, FLOOR_Y, sz - 1, Facing.SOUTH, conn(name), "minecraft:empty", "minecraft:empty", b.palette().block(Role.FLOOR));
	}

	static Piece entrance(String style) {
		Blueprint b = shell(style, "entrance", 31, 18, 11, 10);
		b.clear(13, ROW, 10, 17, ROW + 3, 10); // 5-wide front door
		b.clear(14, ROW, 0, 16, ROW + 2, 0); // 3-wide back arch
		b.jigsaw(15, FLOOR_Y, 0, Facing.NORTH, conn("entrance_back"), conn("floor_front"), PackFiles.stylePoolId(style, "floor"),
			b.palette().block(Role.FLOOR));
		b.kit(Kits.cashierCage(Game.CASHIER), 15, ROW, 5, Facing.SOUTH);
		b.game(Game.WHEEL, 3, ROW, 2, Facing.SOUTH);
		b.game(Game.PLINKO, 27, ROW, 2, Facing.SOUTH);
		b.marquee("CASINO", 15, 16, 10);
		b.anchor(15, ROW + 3, 8);
		return b.build();
	}

	static Piece floor(String style, String piece, boolean mirrored) {
		Blueprint b = shell(style, piece, 31, 14, 17, 12);
		entry(b, 15, 17, "floor_front");
		b.clear(30, ROW, 7, 30, ROW + 2, 9); // side arch to the bar
		b.jigsaw(30, FLOOR_Y, 8, Facing.EAST, conn("floor_side"), conn("bar_side"), PackFiles.stylePoolId(style, "bar"),
			b.palette().block(Role.FLOOR));
		b.clear(14, ROW, 0, 16, ROW + 2, 0); // back arch to the rear room
		b.jigsaw(15, FLOOR_Y, 0, Facing.NORTH, conn("floor_back"), conn("rear_front"), PackFiles.stylePoolId(style, "rear"),
			b.palette().block(Role.FLOOR));
		Game[] row1 = mirrored ? new Game[] {Game.POKER, Game.UTH, Game.BACCARAT, Game.CRAPS}
			: new Game[] {Game.BLACKJACK, Game.ROULETTE, Game.CRAPS, Game.BACCARAT};
		Game[] row2 = mirrored ? new Game[] {Game.ROULETTE, Game.BLACKJACK} : new Game[] {Game.UTH, Game.POKER};
		int[] xs = {4, 9, 21, 26};
		for (int i = 0; i < row1.length; i++) {
			b.kit(Kits.tablePod(row1[i], row1[i] == Game.BACCARAT ? NpcRole.BACCARAT_DEALER : null), xs[i], ROW, 5, Facing.SOUTH);
		}
		for (int i = 0; i < row2.length; i++) {
			b.kit(Kits.tablePod(row2[i], null), xs[i], ROW, 11, Facing.SOUTH);
		}
		b.kit(Kits.slotBank(Game.SLOTS_COPPER, 6), 18, ROW, 1, Facing.SOUTH);
		b.kit(Kits.slotBank(Game.SLOTS_GOLD, 4), 24, ROW, 1, Facing.SOUTH);
		return b.build();
	}

	static Piece bar(String style) {
		Blueprint b = shell(style, "bar", 17, 11, 13, 9);
		entry(b, 8, 13, "bar_side");
		b.kit(Kits.slotBank(Game.SLOTS_GOLD, 3), 2, ROW, 1, Facing.SOUTH);
		b.npc(NpcRole.LOAN_SHARK, 8, ROW, 6, Facing.SOUTH);
		return b.build();
	}

	static Piece office(String style) {
		Blueprint b = shell(style, "office", 15, 9, 9, 7);
		entry(b, 7, 9, "rear_front");
		b.chest(CasinoLoot.Table.VILLAGE_CASINO, 7, ROW, 1, Facing.SOUTH);
		return b.build();
	}

	static Piece vip(String style) {
		Blueprint b = shell(style, "vip", 17, 12, 13, 10);
		entry(b, 8, 13, "rear_front");
		b.kit(Kits.slotBank(Game.SLOTS_NETHERITE, 2), 2, ROW, 1, Facing.SOUTH);
		return b.build();
	}
}
