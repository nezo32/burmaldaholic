package dev.nezo.burmaldaholic.worldgen;

import dev.nezo.burmaldaholic.worldgen.logic.CasinoRecord;
import dev.nezo.burmaldaholic.worldgen.logic.Geometry;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Clears natural tree foliage that grew into a generated casino (INTEGRATION-CB O3). Casinos place at
 * {@code surface_structures}; trees of neighbouring chunks decorate later and their canopies fill any air cell in range,
 * also through walls. Once every chunk within one chunk of the casino box is loaded (so all features that can reach it
 * have run), a main-thread pass removes non-persistent leaves that stand under a roof (a solid, non-tree block above
 * them in the box). Template leaves (hedges, palms) are persistent and stay; open-air canopies outside stay.
 * Runs once per casino per server session; a re-run is harmless.
 */
public final class CasinoFoliage {
	static final int INTERVAL = 20;
	private static final Set<String> DONE = new HashSet<>();

	private CasinoFoliage() {}

	static void clear() {
		DONE.clear();
	}

	static void tick(MinecraftServer server) {
		if (server.getTickCount() % INTERVAL != 0) {
			return;
		}
		for (CasinoRecord casino : CasinoIndex.get(server).casinos()) {
			if (casino.box() == null || DONE.contains(casino.id())) {
				continue;
			}
			ServerLevel level = level(server, casino.dimension());
			if (level != null && settled(level, casino.box())) {
				sweep(level, casino.box());
				DONE.add(casino.id());
			}
		}
	}

	/** Every chunk the box touches, plus a one-chunk ring (features write into their neighbours), is loaded. */
	static boolean settled(ServerLevel level, Geometry.Box b) {
		for (int cx = SectionPos.blockToSectionCoord(b.minX()) - 1; cx <= SectionPos.blockToSectionCoord(b.maxX()) + 1; cx++) {
			for (int cz = SectionPos.blockToSectionCoord(b.minZ()) - 1; cz <= SectionPos.blockToSectionCoord(b.maxZ()) + 1; cz++) {
				if (!level.hasChunk(cx, cz)) {
					return false;
				}
			}
		}
		return true;
	}

	/** Removes natural leaves under a roof inside {@code b}; returns how many. */
	public static int sweep(ServerLevel level, Geometry.Box b) {
		int removed = 0;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int x = b.minX(); x <= b.maxX(); x++) {
			for (int z = b.minZ(); z <= b.maxZ(); z++) {
				boolean roofed = false;
				for (int y = b.maxY(); y >= b.minY(); y--) {
					p.set(x, y, z);
					BlockState s = level.getBlockState(p);
					if (s.isAir()) {
						continue;
					}
					if (s.is(BlockTags.LEAVES)) {
						if (roofed && natural(s)) {
							level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
							removed++;
						}
					} else if (!s.is(BlockTags.LOGS) && !s.getCollisionShape(level, p).isEmpty()) {
						roofed = true;
					}
				}
			}
		}
		if (removed > 0) {
			dev.nezo.burmaldaholic.Burmaldaholic.LOGGER.info("[worldgen] cleared {} tree leaves inside the casino at {}", removed, b);
		}
		return removed;
	}

	private static boolean natural(BlockState s) {
		return !s.hasProperty(LeavesBlock.PERSISTENT) || !s.getValue(LeavesBlock.PERSISTENT);
	}

	private static ServerLevel level(MinecraftServer server, String dimension) {
		for (ServerLevel level : server.getAllLevels()) {
			if (level.dimension().identifier().toString().equals(dimension)) {
				return level;
			}
		}
		return null;
	}
}
