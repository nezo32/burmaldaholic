package dev.nezo.burmaldaholic.core.table;

import dev.nezo.burmaldaholic.Burmaldaholic;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Review M1: a table that stops running with a round in play settles it instead of refunding it on the next
 * load. Breaking a table already plays out ({@code preRemoveSideEffects}); this adds the two other ways a
 * table stops:
 *
 * <ul>
 *   <li><b>Chunk unload</b> — {@code FULL_CHUNK_STATUS_CHANGE} to {@code INACCESSIBLE}, which vanilla runs
 *       <i>before</i> the unloading chunk is saved, so the saved block entity no longer carries the stakes.</li>
 *   <li><b>Server stop</b> (dedicated stop, singleplayer "Save and Quit") — {@code SERVER_STOPPING}, before
 *       players are removed and the world is saved.</li>
 * </ul>
 *
 * Only a crash (no clean stop) still leaves open stakes on disk; those are refunded on load (§4.1).
 *
 * <p>Vanilla fires {@code FULL_CHUNK_STATUS_CHANGE} from inside {@code DistanceManager.runAllUpdates}; a play-out that
 * touches a chunk there re-entered it and crashed the server (T-HR round 2). So during a distance-manager update
 * ({@code ServerChunkCacheMixin}) the unloading chunk is only queued, and played out when the outermost update returns:
 * still before {@code ChunkMap.processUnloads} saves it.
 */
public final class TableLifecycle {
	/** Loaded tables (identity set; entries vanish with their block entity). Server thread only. */
	private static final Set<CasinoTableBlockEntity> LOADED = Collections.newSetFromMap(new WeakHashMap<>());

	/** Unloading chunks seen during a distance-manager update, played out when it returns. Server thread only. */
	private static final DeferredUnloads<LevelChunk> UNLOADS = new DeferredUnloads<>(TableLifecycle::playOutChunk);

	private TableLifecycle() {}

	/** {@code ServerChunkCache.runDistanceManagerUpdates} starts (it may nest). */
	public static void enterChunkUpdates() {
		UNLOADS.enter();
	}

	/** {@code ServerChunkCache.runDistanceManagerUpdates} returned: the outermost one plays out what unloaded meanwhile. */
	public static void exitChunkUpdates() {
		UNLOADS.exit();
	}

	public static void register() {
		ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((be, level) -> {
			if (be instanceof CasinoTableBlockEntity table) {
				LOADED.add(table);
			}
		});
		ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((be, level) -> {
			if (be instanceof CasinoTableBlockEntity table) {
				LOADED.remove(table);
			}
		});
		ServerChunkEvents.FULL_CHUNK_STATUS_CHANGE.register((level, chunk, from, to) -> {
			if (to == FullChunkStatus.INACCESSIBLE) {
				onChunkUnloading(chunk);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(TableLifecycle::onServerStopping);
		// a distance-manager update that threw never reached its exit: never let unloading tables wait until server stop
		ServerTickEvents.START_SERVER_TICK.register(server -> {
			if (UNLOADS.tickStart()) {
				Burmaldaholic.LOGGER.warn("Table lifecycle: a chunk update did not finish; played out the tables of unloaded chunks now");
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LOADED.clear();
			UNLOADS.clear();
		});
	}

	/** Whether core knows the table as loaded (it will be played out on server stop). */
	public static boolean tracked(CasinoTableBlockEntity table) {
		return LOADED.contains(table);
	}

	/** The chunk is about to be saved and unloaded: play out every table in it (after the distance-manager update). */
	static void onChunkUnloading(LevelChunk chunk) {
		UNLOADS.offer(chunk);
	}

	private static void playOutChunk(LevelChunk chunk) {
		boolean changed = false;
		for (BlockEntity be : new ArrayList<>(chunk.getBlockEntities().values())) {
			if (be instanceof CasinoTableBlockEntity table && !table.isRemoved()) {
				changed |= playOut(table, "chunk unloaded");
			}
		}
		if (changed) {
			chunk.markUnsaved(); // the chunk may no longer be reachable through the level: save the settled state
		}
	}

	/** Server stopping: play out every loaded table before the world is saved. */
	static void onServerStopping(MinecraftServer server) {
		List<CasinoTableBlockEntity> tables = new ArrayList<>(LOADED);
		for (CasinoTableBlockEntity table : tables) {
			if (!table.isRemoved() && table.getLevel() != null && table.getLevel().getServer() == server) {
				playOut(table, CasinoTableBlockEntity.SERVER_STOPPING);
			}
		}
	}

	private static boolean playOut(CasinoTableBlockEntity table, String why) {
		try {
			return table.playOutNow(why);
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.error("Table {} at {}: play-out ({}) failed", table.gameId(), table.getBlockPos(), why, e);
			return false;
		}
	}
}
