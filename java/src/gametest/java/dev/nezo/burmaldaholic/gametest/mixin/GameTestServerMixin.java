package dev.nezo.burmaldaholic.gametest.mixin;

import net.minecraft.gametest.framework.GameTestServer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * GameTest server only (CB-P, "Saving chunks" hang): {@code MinecraftServer.forceSynchronousWrites()} is true unless
 * a subclass says otherwise, and {@code GameTestServer} does not, so every chunk of the test world is written to its
 * region file with {@code DSYNC} on shutdown. The casino QA tests load thousands of chunks (each placement region
 * ~121), and on a busy Windows disk the final save took 28–33 min after the tests had finished (jstack: server thread
 * parked in {@code ChunkMap.saveAllChunks}, the only busy thread an IO worker in {@code FileDispatcherImpl.pwrite0}).
 * The test world is throw-away, so its writes need no fsync.
 */
@Mixin(GameTestServer.class)
public abstract class GameTestServerMixin {
	/** Overrides {@code MinecraftServer.forceSynchronousWrites()} for the GameTest server. */
	public boolean forceSynchronousWrites() {
		return false;
	}
}
