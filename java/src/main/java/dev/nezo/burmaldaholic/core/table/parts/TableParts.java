package dev.nezo.burmaldaholic.core.table.parts;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlock;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.table.MultiBlockTableBlock;
import dev.nezo.burmaldaholic.core.text.Plural;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

/**
 * Single entry point for multi-block casino tables in the world (MT-DESIGN §3–§5): property, part →
 * main resolution, room checks, placing / unfolding parts, the footprint box, first-tick integrity and
 * legacy migration. Pure maths lives in {@link TableFootprint}; this class only touches the world.
 */
public final class TableParts {
	/** {@code part} of the 3×2 table blocks. Default (first) value SINGLE = legacy / compact table. */
	public static final EnumProperty<TablePart> TABLE_PART = EnumProperty.create("part", TablePart.class,
		TablePart.SINGLE, TablePart.MAIN, TablePart.LEFT, TablePart.RIGHT, TablePart.BACK_LEFT, TablePart.BACK, TablePart.BACK_RIGHT);

	/** MT-3 {@code part} of a 1 × 2 slot cabinet. */
	public static final EnumProperty<TablePart> SLOT_PART = EnumProperty.create("part", TablePart.class, MachineKind.SLOT.partValues());
	/** MT-3 {@code part} of the 3 × 3 upright machines (Wheel of Fortune, Plinko). */
	public static final EnumProperty<TablePart> UPRIGHT_PART = EnumProperty.create("part", TablePart.class, MachineKind.WHEEL.partValues());
	/** MT-3 {@code part} of the 3 × 2 cashier booth. */
	public static final EnumProperty<TablePart> BOOTH_PART = EnumProperty.create("part", TablePart.class, MachineKind.CASHIER.partValues());

	/** The {@code part} property of a machine kind. */
	public static EnumProperty<TablePart> propertyFor(MachineKind kind) {
		return switch (kind) {
			case SLOT -> SLOT_PART;
			case WHEEL, PLINKO -> UPRIGHT_PART;
			case CASHIER -> BOOTH_PART;
		};
	}

	/** Machine kind of a block state, or null for tables / other blocks. */
	public static @Nullable MachineKind machineOf(BlockState state) {
		MultiBlockTableBlock b = blockOf(state);
		return b == null ? null : b.machine();
	}

	/** Players that already saw the "compact table" hint this server session. */
	private static final Set<UUID> HINTED = new HashSet<>();
	/** >0 while this class places or rearranges cells itself (server thread): integrity checks stand aside. */
	private static int placing;

	private TableParts() {}

	// ---- state helpers -------------------------------------------------------------------------

	public static @Nullable MultiBlockTableBlock blockOf(BlockState state) {
		return state.getBlock() instanceof MultiBlockTableBlock b ? b : null;
	}

	/** The block's part; SINGLE for compact tables and every non-multi-block table. */
	public static TablePart partOf(BlockState state) {
		MultiBlockTableBlock b = blockOf(state);
		return b == null ? TablePart.SINGLE : state.getValue(b.partProperty());
	}

	/** Footprint kind of a table block state, or null for 1×1 machines / other blocks. */
	public static @Nullable TableGame gameOf(BlockState state) {
		MultiBlockTableBlock b = blockOf(state);
		return b == null ? null : b.game();
	}

	public static Direction facingOf(BlockState state) {
		return state.hasProperty(CasinoTableBlock.FACING) ? state.getValue(CasinoTableBlock.FACING) : Direction.NORTH;
	}

	/** Main block position for a block of a table at {@code pos} (pure maths; {@code pos} itself for SINGLE / non-tables). */
	public static BlockPos mainPos(BlockPos pos, BlockState state) {
		MultiBlockTableBlock b = blockOf(state);
		if (b == null) {
			return pos;
		}
		TablePart part = state.getValue(b.partProperty());
		if (part.isController()) {
			return pos;
		}
		LocalCell cell = b.cellOf(part);
		if (cell == null) {
			return pos;
		}
		Direction facing = facingOf(state);
		int[] o = cell.offset(facing.getStepX(), facing.getStepZ());
		return pos.offset(-o[0], -o[1], -o[2]);
	}

	/** Main block position of the table a block at {@code pos} belongs to (reads the state). */
	public static BlockPos mainOf(BlockGetter level, BlockPos pos) {
		return mainPos(pos, level.getBlockState(pos));
	}

	/** World positions of every cell of the table whose main block is {@code main} (state = main's state). */
	public static List<BlockPos> cellPositions(BlockPos main, BlockState mainState) {
		MultiBlockTableBlock b = blockOf(mainState);
		List<BlockPos> out = new ArrayList<>();
		if (b == null || partOf(mainState) == TablePart.SINGLE) {
			out.add(main);
			return out;
		}
		Direction facing = facingOf(mainState);
		for (LocalCell c : b.cells()) {
			int[] o = c.offset(facing.getStepX(), facing.getStepZ());
			out.add(main.offset(o[0], o[1], o[2]));
		}
		return out;
	}

	/** Box over the whole footprint (a single block for compact tables). */
	public static AABB footprintBox(BlockPos main, BlockState mainState) {
		List<BlockPos> cells = cellPositions(main, mainState);
		AABB box = new AABB(cells.get(0));
		for (BlockPos p : cells) {
			box = box.minmax(new AABB(p));
		}
		return box;
	}

	/** Squared distance from {@code at} to the nearest point of the table's footprint box (main block at {@code main}). */
	public static double distanceSqToTable(Vec3 at, BlockPos main, BlockState mainState) {
		AABB b = footprintBox(main, mainState);
		double dx = Math.max(0, Math.max(b.minX - at.x, at.x - b.maxX));
		double dy = Math.max(0, Math.max(b.minY - at.y, at.y - b.maxY));
		double dz = Math.max(0, Math.max(b.minZ - at.z, at.z - b.maxZ));
		return dx * dx + dy * dy + dz * dz;
	}

	/**
	 * Distance check used by menus and seats: centre distance for compact tables (unchanged since v0.1), nearest
	 * point of the footprint box for full tables.
	 */
	public static boolean inRange(Vec3 at, Level level, BlockPos main, double max) {
		BlockState state = level.getBlockState(main);
		if (partOf(state) == TablePart.SINGLE) {
			return at.distanceToSqr(Vec3.atCenterOf(main)) <= max * max;
		}
		return distanceSqToTable(at, main, state) <= max * max;
	}

	// ---- room checks ---------------------------------------------------------------------------

	public static boolean isCarpet(BlockState state) {
		return state.getBlock() instanceof CarpetBlock;
	}

	private static boolean hasFloor(Level level, BlockPos cell) {
		BlockState below = level.getBlockState(cell.below());
		return !below.isAir() && below.getFluidState().isEmpty();
	}

	/**
	 * How many cells of a full table at {@code main} (state {@code mainState}) block placement: inside the world
	 * border and build height, loaded, replaceable or carpet (★1), no entity in the way, a floor below (only for
	 * the bottom row). The main cell itself is checked by {@code BlockItem} except for its floor.
	 */
	public static int blockedCells(BlockPlaceContext ctx, BlockPos main, BlockState mainState) {
		Level level = ctx.getLevel();
		MultiBlockTableBlock b = blockOf(mainState);
		if (b == null) {
			return 0;
		}
		Direction facing = facingOf(mainState);
		int blocked = 0;
		for (LocalCell c : b.cells()) {
			int[] o = c.offset(facing.getStepX(), facing.getStepZ());
			BlockPos cell = main.offset(o[0], o[1], o[2]);
			BlockState partState = mainState.setValue(b.partProperty(), c.part());
			boolean ok;
			if (level.isOutsideBuildHeight(cell) || !level.getWorldBorder().isWithinBounds(cell) || !level.hasChunkAt(cell)) {
				ok = false;
			} else {
				BlockState here = level.getBlockState(cell);
				boolean isMain = c.part() == TablePart.MAIN;
				ok = isMain || here.canBeReplaced(BlockPlaceContext.at(ctx, cell, Direction.UP)) || isCarpet(here);
				ok = ok && (c.u() > 0 || hasFloor(level, cell));
				CollisionContext cc = ctx.getPlayer() == null ? CollisionContext.empty() : CollisionContext.placementContext(ctx.getPlayer());
				ok = ok && (isMain || level.isUnobstructed(partState, cell, cc));
			}
			if (!ok) {
				blocked++;
			}
		}
		return blocked;
	}

	public static Component noRoomMessage(int blocked) {
		return Component.translatable(Plural.key("msg.burmaldaholic.table.no_room", blocked), blocked);
	}

	// ---- placing -------------------------------------------------------------------------------

	/**
	 * Places the non-main parts round an existing main block (UPDATE_ALL, BFS order). Carpets in the cells pop as
	 * items first (★1) so no placed part ever sees a neighbour change it doesn't expect.
	 */
	public static void placeParts(Level level, BlockPos main, BlockState mainState) {
		MultiBlockTableBlock b = blockOf(mainState);
		if (b == null) {
			return;
		}
		placing++;
		try {
			placePartsUnguarded(level, main, mainState, b);
		} finally {
			placing--;
		}
	}

	/**
	 * A player breaks a non-main part: the main block is destroyed (its loot = the one item unless creative; the block
	 * entity settles its stakes) and the other parts are cleared without drops (with break particles). The broken part
	 * itself is left for the vanilla break to remove, so the break events (ownership AFTER) fire for it.
	 */
	public static void breakFromPart(Level level, BlockPos partPos, BlockState partState, Player player) {
		BlockPos main = mainPos(partPos, partState);
		BlockState mainState = level.getBlockState(main);
		MultiBlockTableBlock b = blockOf(partState);
		if (b == null || !mainState.is(b) || partOf(mainState) != TablePart.MAIN) {
			return;
		}
		List<BlockPos> cells = cellPositions(main, mainState);
		placing++;
		try {
			boolean drop = !player.isCreative() && (!(level instanceof net.minecraft.server.level.ServerLevel sl) || TableProtection.get().dropsAllowed(sl, main));
			level.destroyBlock(main, drop, player);
			for (BlockPos c : cells) {
				if (!c.equals(main) && !c.equals(partPos) && level.getBlockState(c).is(b)) {
					// destroyBlock (no drops) plays the break particles + sound; LevelEvent ids differ between 26.2 and 26.3,
					// so the constant is never inlined here
					level.destroyBlock(c, false, player);
				}
			}
		} finally {
			placing--;
		}
	}

	/** True while {@link #placeParts} runs: a part's neighbour may legitimately be missing for a moment. */
	public static boolean placing() {
		return placing > 0;
	}

	private static void placePartsUnguarded(Level level, BlockPos main, BlockState mainState, MultiBlockTableBlock b) {
		Direction facing = facingOf(mainState);
		List<LocalCell> parts = new ArrayList<>();
		for (LocalCell c : b.cells()) {
			if (c.part() != TablePart.MAIN) {
				parts.add(c);
			}
		}
		for (LocalCell c : parts) {
			int[] o = c.offset(facing.getStepX(), facing.getStepZ());
			BlockPos cell = main.offset(o[0], o[1], o[2]);
			if (isCarpet(level.getBlockState(cell))) {
				level.destroyBlock(cell, true);
			}
		}
		for (LocalCell c : parts) {
			int[] o = c.offset(facing.getStepX(), facing.getStepZ());
			level.setBlock(main.offset(o[0], o[1], o[2]), mainState.setValue(b.partProperty(), c.part()), Block.UPDATE_ALL);
		}
	}

	/**
	 * Places a FULL table of {@code block} with its main block at {@code main} (tests, worldgen, commands); replaces
	 * whatever is in the cells. Returns the main block state.
	 */
	public static BlockState placeFull(Level level, BlockPos main, MultiBlockTableBlock block, Direction facing) {
		BlockState mainState = block.defaultBlockState().setValue(CasinoTableBlock.FACING, facing).setValue(block.partProperty(), TablePart.MAIN);
		level.setBlock(main, mainState, Block.UPDATE_ALL);
		placeParts(level, main, mainState);
		return mainState;
	}

	// ---- unfolding / migration -----------------------------------------------------------------

	/** Classifies the non-main cells of a would-be full table at a compact table's position (§5). */
	public static List<MigrationDecision.CellCheck> survey(ServerLevel level, BlockPos main, BlockState fullMain) {
		MultiBlockTableBlock b = blockOf(fullMain);
		List<MigrationDecision.CellCheck> out = new ArrayList<>();
		if (b == null) {
			return out;
		}
		Direction facing = facingOf(fullMain);
		BlockPos dealer = b.game() == null ? null : TableFootprint.dealerPos(main, facing, b.game());
		for (LocalCell c : b.cells()) {
			if (c.part() == TablePart.MAIN) {
				continue;
			}
			int[] o = c.offset(facing.getStepX(), facing.getStepZ());
			BlockPos cell = main.offset(o[0], o[1], o[2]);
			out.add(check(level, cell, c.u() > 0, dealer));
		}
		return out;
	}

	private static MigrationDecision.CellCheck check(ServerLevel level, BlockPos cell, boolean upper, @Nullable BlockPos dealer) {
		if (!level.hasChunkAt(cell) || !level.hasChunkAt(cell.below())) {
			return MigrationDecision.CellCheck.UNLOADED;
		}
		if (level.isOutsideBuildHeight(cell) || !level.getWorldBorder().isWithinBounds(cell)) {
			return MigrationDecision.CellCheck.BLOCKED;
		}
		BlockState here = level.getBlockState(cell);
		boolean carpet = isCarpet(here);
		if (!here.isAir() && !carpet) {
			return MigrationDecision.CellCheck.BLOCKED;
		}
		if (!upper && !hasFloor(level, cell)) {
			return MigrationDecision.CellCheck.FLOOR_MISSING;
		}
		AABB box = new AABB(cell);
		List<Entity> inside = level.getEntities((Entity) null, box, e -> e instanceof LivingEntity && e.isAlive());
		boolean dealerHere = false;
		for (Entity e : inside) {
			if (e instanceof Player) {
				return MigrationDecision.CellCheck.PLAYER;
			}
		}
		for (Entity e : inside) {
			if (isCasinoNpc(e)) {
				dealerHere = true;
			} else {
				return MigrationDecision.CellCheck.OTHER_LIVING;
			}
		}
		if (dealerHere) {
			return dealer != null && standable(level, dealer) ? MigrationDecision.CellCheck.DEALER_MOVABLE : MigrationDecision.CellCheck.DEALER_STUCK;
		}
		return carpet ? MigrationDecision.CellCheck.CARPET : MigrationDecision.CellCheck.FREE;
	}

	/** A casino NPC (croupier, dealers…): a pathfinder mob of this mod. Core does not depend on worldgen classes. */
	public static boolean isCasinoNpc(Entity e) {
		return e instanceof PathfinderMob && BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace().equals(Burmaldaholic.MOD_ID);
	}

	/** Two blocks of air (or no collision) above a floor. */
	public static boolean standable(Level level, BlockPos p) {
		return level.hasChunkAt(p) && hasFloor(level, p)
			&& level.getBlockState(p).getCollisionShape(level, p).isEmpty()
			&& level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty();
	}

	/**
	 * Tries to unfold the compact table at {@code pos} into a full table: SINGLE → MAIN on the same block (block
	 * entity and open stakes kept), then the parts; dealers in the way step to the dealer cell. Returns the
	 * decision taken; nothing is touched unless it is GROW.
	 */
	public static MigrationDecision.Outcome tryUnfold(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		MultiBlockTableBlock b = blockOf(state);
		if (b == null || state.getValue(b.partProperty()) != TablePart.SINGLE) {
			return MigrationDecision.Outcome.COMPACT;
		}
		BlockState full = state.setValue(b.partProperty(), TablePart.MAIN);
		MigrationDecision.Outcome outcome = MigrationDecision.of(survey(level, pos, full));
		if (outcome != MigrationDecision.Outcome.GROW) {
			return outcome;
		}
		Direction facing = facingOf(full);
		if (b.game() != null) {
			BlockPos dealer = TableFootprint.dealerPos(pos, facing, b.game());
			for (BlockPos cell : cellPositions(pos, full)) {
				if (cell.equals(pos)) {
					continue;
				}
				for (Entity e : level.getEntities((Entity) null, new AABB(cell), TableParts::isCasinoNpc)) {
					e.teleportTo(dealer.getX() + 0.5, dealer.getY(), dealer.getZ() + 0.5);
					e.setYRot(facing.toYRot());
				}
			}
		}
		level.setBlock(pos, full, Block.UPDATE_ALL);
		placeParts(level, pos, full);
		return MigrationDecision.Outcome.GROW;
	}

	/** Blocked-cell count for a sneak-use retry message (cells that are not air / carpet / floor-backed). */
	public static int blockedForUnfold(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		MultiBlockTableBlock b = blockOf(state);
		if (b == null) {
			return 0;
		}
		int n = 0;
		for (MigrationDecision.CellCheck c : survey(level, pos, state.setValue(b.partProperty(), TablePart.MAIN))) {
			if (c != MigrationDecision.CellCheck.FREE && c != MigrationDecision.CellCheck.CARPET && c != MigrationDecision.CellCheck.DEALER_MOVABLE) {
				n++;
			}
		}
		return n;
	}

	/**
	 * First server tick of a table block entity: a MAIN with missing parts regrows them when the cells are free,
	 * else it folds to SINGLE (partial copies, /setblock, WorldEdit, worldgen cut at a chunk edge); a legacy
	 * SINGLE (saved before v0.2) unfolds when there is room (§5). Returns true if the block entity must be saved.
	 */
	public static boolean firstTick(ServerLevel level, CasinoTableBlockEntity be, boolean legacy) {
		BlockPos pos = be.getBlockPos();
		BlockState state = level.getBlockState(pos);
		MultiBlockTableBlock b = blockOf(state);
		if (b == null) {
			return false;
		}
		TablePart part = state.getValue(b.partProperty());
		if (part == TablePart.MAIN) {
			if (intact(level, pos, state)) {
				return false;
			}
			List<BlockPos> cells = cellPositions(pos, state);
			for (BlockPos c : cells) {
				if (!level.hasChunkAt(c)) {
					return false; // never act across unloaded chunks
				}
			}
			boolean free = true;
			for (BlockPos c : cells) {
				BlockState s = level.getBlockState(c);
				if (!c.equals(pos) && !s.is(b) && !s.isAir() && !isCarpet(s)) {
					free = false;
				}
			}
			if (free) {
				placeParts(level, pos, state);
			} else {
				// fold: clear our own stray parts first (no drops), then the main becomes compact
				for (BlockPos c : cells) {
					if (!c.equals(pos) && level.getBlockState(c).is(b)) {
						level.setBlock(c, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
							Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_KNOWN_SHAPE);
					}
				}
				level.setBlock(pos, state.setValue(b.partProperty(), TablePart.SINGLE), Block.UPDATE_ALL);
			}
			return true;
		}
		if (part == TablePart.SINGLE && legacy) {
			MigrationDecision.Outcome o = tryUnfold(level, pos);
			Burmaldaholic.LOGGER.debug("Legacy table {} at {}: {}", BuiltInRegistries.BLOCK.getKey(b), pos, o);
			return o != MigrationDecision.Outcome.RETRY;
		}
		return false;
	}

	/** Every expected cell holds this block with the main's facing and the right part. */
	public static boolean intact(Level level, BlockPos main, BlockState mainState) {
		MultiBlockTableBlock b = blockOf(mainState);
		if (b == null) {
			return true;
		}
		Direction facing = facingOf(mainState);
		for (LocalCell c : b.cells()) {
			int[] o = c.offset(facing.getStepX(), facing.getStepZ());
			BlockState s = level.getBlockState(main.offset(o[0], o[1], o[2]));
			if (!s.is(b) || facingOf(s) != facing || s.getValue(b.partProperty()) != c.part()) {
				return false;
			}
		}
		return true;
	}

	/** "Compact table — free a 3×2 space, then sneak-use to unfold": once per player per server session. */
	public static void compactHint(ServerPlayer player) {
		if (HINTED.add(player.getUUID())) {
			player.sendSystemMessage(Component.translatable("msg.burmaldaholic.table.compact_hint"));
		}
	}

	/** Server stopped: forget who saw the hint. */
	public static void clearSession() {
		HINTED.clear();
	}
}
