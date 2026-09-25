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
 */
public final class TableLifecycle {
	/** Loaded tables (identity set; entries vanish with their block entity). Server thread only. */
	private static final Set<CasinoTableBlockEntity> LOADED = Collections.newSetFromMap(new WeakHashMap<>());

	private TableLifecycle() {}

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
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LOADED.clear());
	}

	/** Whether core knows the table as loaded (it will be played out on server stop). */
	public static boolean tracked(CasinoTableBlockEntity table) {
		return LOADED.contains(table);
	}

	/** The chunk is about to be saved and unloaded: play out every table in it. */
	static void onChunkUnloading(LevelChunk chunk) {
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
