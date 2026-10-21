package dev.nezo.burmaldaholic.worldgen.npc;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.cashier.CashierBlockEntity;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.table.parts.TableFootprint;
import dev.nezo.burmaldaholic.core.table.parts.TableGame;
import dev.nezo.burmaldaholic.core.table.parts.TablePart;
import dev.nezo.burmaldaholic.core.table.parts.TableParts;
import dev.nezo.burmaldaholic.worldgen.CasinoIndex;
import dev.nezo.burmaldaholic.worldgen.CasinoNpcs;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoRecord;
import dev.nezo.burmaldaholic.worldgen.logic.Geometry;
import dev.nezo.burmaldaholic.worldgen.logic.NpcRole;
import dev.nezo.burmaldaholic.worldgen.logic.Vec;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * House-dealer migration and upkeep of generated casinos (DL-SPEC §2 "Migration"): every house table of a casino gets
 * exactly one {@link HouseDealer} on its dealer cell. Runs from {@code CasinoTracker} every 100 ticks for records whose
 * {@link CasinoRecord#dealersVersion} is below {@link #VERSION} and whose whole box is loaded (blocks and entities);
 * the pass is idempotent and only marks the record done when it saw every chunk.
 *
 * <p>Per house table (inside the box, not player-owned): adopt a dealer already bound to / standing on its cell, else
 * discard a legacy baccarat / piglin dealer standing on the cell or one row in front (f − 1) and drop its slot, then
 * spawn a dealer if the cell is two blocks of air over a solid floor, and record its NPC slot. At most
 * {@link #MAX_PER_CASINO} dealers per casino.
 */
public final class HouseDealers {
	/** Current migration version (bump to re-run the pass on every casino). */
	public static final int VERSION = 1;
	public static final int MAX_PER_CASINO = 12;
	private static final Set<String> LEGACY_DEALERS = Set.of("burmaldaholic:baccarat_dealer", "burmaldaholic:piglin_dealer");

	private HouseDealers() {}

	/** Runs the pass for every pending casino with a loaded box. */
	public static void ensure(MinecraftServer server) {
		CasinoIndex index = CasinoIndex.get(server);
		for (CasinoRecord casino : List.copyOf(index.casinos())) {
			if (casino.dealersVersion >= VERSION || casino.box() == null) {
				continue;
			}
			ServerLevel level = level(server, casino.dimension());
			if (level != null && ensure(level, casino)) {
				casino.dealersVersion = VERSION;
				index.changed();
			}
		}
	}

	/** One pass over {@code casino}; true if it was complete (every chunk loaded). */
	public static boolean ensure(ServerLevel level, CasinoRecord casino) {
		List<BlockPos> tables = houseTables(level, casino);
		if (tables == null) {
			return false;
		}
		int dealers = (int) casino.npcs().stream().filter(s -> s.role() == NpcRole.HOUSE_DEALER).count();
		for (BlockPos main : tables) {
			BlockState state = level.getBlockState(main);
			Direction facing = TableParts.facingOf(state);
			BlockPos cell = dealerCell(main, state);
			HouseDealer dealer = existing(level, main, cell);
			if (dealer == null) {
				// the legacy dealer goes only once its replacement stands (a casino never ends up dealer-less)
				if (dealers >= MAX_PER_CASINO) {
					continue;
				}
				if (!standable(level, cell)) {
					Burmaldaholic.LOGGER.info("[worldgen] no room for the house dealer of the table at {} (cell {})", main, cell);
					continue;
				}
				Entity e = CasinoNpcs.spawn(level, NpcRole.HOUSE_DEALER, cell, facing.toYRot(), casino.id());
				if (!(e instanceof HouseDealer spawned)) {
					continue; // type absent (cannot happen: worldgen owns it)
				}
				dealer = spawned;
				dealers++;
				discardLegacy(level, casino, cell, facing);
			}
			BlockPos home = dealer.home() != null ? dealer.home() : cell;
			Vec v = new Vec(home.getX(), home.getY(), home.getZ());
			boolean known = casino.npcs().stream().anyMatch(s -> s.home().equals(v));
			casino.addNpc(NpcRole.HOUSE_DEALER, v, facing.toYRot(), dealer.getUUID());
			if (!known && dealer.home() == null) {
				dealer.setHome(cell, facing.toYRot());
			}
		}
		return true;
	}

	/**
	 * Main blocks of the casino's house tables (game tables inside the box that no player owns), or null while a chunk
	 * of the box (blocks or entities) is not loaded.
	 */
	public static @Nullable List<BlockPos> houseTables(ServerLevel level, CasinoRecord casino) {
		Geometry.Box box = casino.box();
		if (box == null) {
			return null;
		}
		List<LevelChunk> chunks = new ArrayList<>();
		for (int cx = SectionPos.blockToSectionCoord(box.minX()); cx <= SectionPos.blockToSectionCoord(box.maxX()); cx++) {
			for (int cz = SectionPos.blockToSectionCoord(box.minZ()); cz <= SectionPos.blockToSectionCoord(box.maxZ()); cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null || !level.areEntitiesLoaded(ChunkPos.pack(cx, cz))) {
					return null;
				}
				chunks.add(chunk);
			}
		}
		List<BlockPos> out = new ArrayList<>();
		for (LevelChunk chunk : chunks) {
			for (BlockEntity be : chunk.getBlockEntities().values()) {
				BlockPos pos = be.getBlockPos();
				if (!(be instanceof CasinoTableBlockEntity table) || be instanceof CashierBlockEntity
						|| !box.contains(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0)) {
					continue;
				}
				BlockState state = be.getBlockState();
				TablePart part = TableParts.partOf(state);
				if (TableParts.gameOf(state) == null || part != TablePart.MAIN && part != TablePart.SINGLE) {
					continue;
				}
				if (isHouseTable(table)) {
					out.add(pos.immutable());
				}
			}
		}
		out.sort((a, b) -> Long.compare(a.asLong(), b.asLong())); // deterministic order (the 12-dealer budget)
		return out;
	}

	/** Fallback of CB-G's {@code HouseGames.isHouseTable}: a table inside a generated casino that no player owns. */
	static boolean isHouseTable(CasinoTableBlockEntity table) {
		return table.getLevel() instanceof ServerLevel sl && dev.nezo.burmaldaholic.worldgen.HouseGames.isHouseTable(sl, table.getBlockPos(), table)
			&& table.ownership().isEmpty();
	}

	/** The dealer cell of the table whose main block is {@code main}: the footprint's dealer cell, f − 1 for compact tables. */
	public static BlockPos dealerCell(BlockPos main, BlockState state) {
		Direction facing = TableParts.facingOf(state);
		TableGame game = TableParts.gameOf(state);
		if (game == null || TableParts.partOf(state) == TablePart.SINGLE) {
			return main.relative(facing.getOpposite());
		}
		return TableFootprint.dealerPos(main, facing, game);
	}

	/** A live house dealer serving this table: bound to it, or standing on its cell. */
	static @Nullable HouseDealer existing(ServerLevel level, BlockPos main, BlockPos cell) {
		List<HouseDealer> near = level.getEntitiesOfClass(HouseDealer.class, new AABB(cell).inflate(1.5), Entity::isAlive);
		HouseDealer pick = null;
		for (HouseDealer d : near) {
			if (main.equals(d.table())) {
				return d;
			}
			if (pick == null && d.blockPosition().equals(cell)) {
				pick = d;
			}
		}
		return pick;
	}

	/** Discards the v0.1 baccarat / piglin dealer of a table (cell or f − 1) and drops its NPC slot. */
	static void discardLegacy(ServerLevel level, CasinoRecord casino, BlockPos cell, Direction facing) {
		for (BlockPos at : new BlockPos[] {cell, cell.relative(facing)}) {
			for (Entity e : level.getEntities((Entity) null, new AABB(at), HouseDealers::legacy)) {
				Burmaldaholic.LOGGER.info("[worldgen] replacing the legacy dealer {} at {} with a house dealer", e.getType(), at);
				e.discard();
				casino.removeNpc(new Vec(at.getX(), at.getY(), at.getZ()));
				casino.npcs().removeIf(s -> s.role() != NpcRole.HOUSE_DEALER && e.getUUID().equals(s.entity)); // moved by an unfold
			}
		}
	}

	static boolean legacy(Entity e) {
		return e.entityTags().contains(CasinoNpcs.NPC_TAG) && LEGACY_DEALERS.contains(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString());
	}

	/**
	 * Room for a dealer: the cell is air or a low block such as carpet ({@link #floorHeight}; v0.1.3 village casinos and
	 * Lounges carpet their dealer cells), nothing solid above it, and a solid floor under it.
	 */
	static boolean standable(ServerLevel level, BlockPos cell) {
		return floorHeight(level, cell) >= 0 && level.getBlockState(cell.above()).getCollisionShape(level, cell.above()).isEmpty()
			&& level.getBlockState(cell.below()).isFaceSturdy(level, cell.below(), Direction.UP);
	}

	/** Max height of a block a dealer can stand on inside its cell (carpet 1/16, a thin snow layer). */
	private static final double LOW_BLOCK = 0.1875;

	/**
	 * Height the dealer's feet stand at inside {@code cell}: 0 for air or a block without collision, the top of a low
	 * block (carpet), or −1 when the cell is blocked.
	 */
	public static double floorHeight(BlockGetter level, BlockPos cell) {
		requireOwnerThread(level);
		BlockState s = level.getBlockState(cell);
		if (s.isAir()) {
			return 0;
		}
		VoxelShape shape = s.getCollisionShape(level, cell);
		if (shape.isEmpty()) {
			return s.getFluidState().isEmpty() ? 0 : -1;
		}
		double top = shape.max(Direction.Axis.Y);
		return top <= LOW_BLOCK ? top : -1;
	}

	/**
	 * Guard: a live {@link ServerLevel} may only be read on its server thread. Off-thread reads (worldgen workers)
	 * block on a main-thread chunk future and can deadlock; worldgen must pass its WorldGenRegion instead.
	 */
	static void requireOwnerThread(BlockGetter level) {
		if (level instanceof ServerLevel sl && !sl.getServer().isSameThread()) {
			throw new IllegalStateException("live ServerLevel read off the server thread (" + Thread.currentThread().getName()
				+ "); worldgen must use its WorldGenRegion");
		}
	}

	private static @Nullable ServerLevel level(MinecraftServer server, String dimension) {
		for (ServerLevel level : server.getAllLevels()) {
			if (level.dimension().identifier().toString().equals(dimension)) {
				return level;
			}
		}
		return null;
	}
}
