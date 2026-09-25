package dev.nezo.burmaldaholic.chaos;

import dev.nezo.burmaldaholic.chaos.logic.Safety;
import dev.nezo.burmaldaholic.chaos.logic.Safety.BlockInfo;
import dev.nezo.burmaldaholic.chaos.logic.Safety.LandingColumn;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Block / spot / surroundings helpers for chaos events (thin Minecraft layer over {@link Safety}). */
final class ChaosWorld {
	private static final Set<String> BOSSES = Set.of("wither", "warden", "ender_dragon");

	private ChaosWorld() {}

	/** "overworld", "the_nether", "the_end" (or the path of a custom dimension). */
	static String dimension(ServerLevel level) {
		return level.dimension().identifier().getPath();
	}

	static BlockInfo info(ServerLevel level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		String id = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
		return new BlockInfo(id, s.isAir(), !s.getFluidState().isEmpty(), s.isFaceSturdy(level, pos, Direction.UP),
			!s.getCollisionShape(level, pos).isEmpty());
	}

	private static LandingColumn column(ServerLevel level, int x, int y, int z) {
		List<BlockInfo> below = new ArrayList<>(3);
		for (int i = 1; i <= 3; i++) {
			below.add(info(level, new BlockPos(x, y - i, z)));
		}
		return new LandingColumn(dimension(level), y, level.getMinY(), info(level, new BlockPos(x, y, z)),
			info(level, new BlockPos(x, y + 1, z)), info(level, new BlockPos(x, y + 2, z)), below);
	}

	/**
	 * Safe teleport landing in column (x, z) per §13.4: inside the world border; the chunk must be
	 * loaded, or loadable if {@code mayLoad} (the caller allows one synchronous load per event).
	 * Overworld/End: the topmost motion-blocking block; Nether: scanned down from Y 117 (never the
	 * roof). Returns the ground y, or {@link Integer#MIN_VALUE}.
	 */
	static int findLanding(ServerLevel level, int x, int z, boolean mayLoad) {
		if (!level.getWorldBorder().isWithinBounds(new BlockPos(x, 0, z))) {
			return Integer.MIN_VALUE;
		}
		if (!level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) {
			if (!mayLoad) {
				return Integer.MIN_VALUE;
			}
			level.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z)); // synchronous load/generate
		}
		int minY = level.getMinY();
		if (dimension(level).equals("the_nether")) {
			for (int y = 117; y > minY + 5; y--) {
				if (Safety.isSafeLanding(column(level, x, y, z))) {
					return y;
				}
			}
			return Integer.MIN_VALUE;
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
		if (y <= minY) {
			return Integer.MIN_VALUE;
		}
		return Safety.isSafeLanding(column(level, x, y, z)) ? y : Integer.MIN_VALUE;
	}

	/**
	 * Mob spawn spot near column (x, z): sturdy ground with 2 free blocks above, within ±6 of
	 * {@code nearY} (closest first), loaded chunk only. Returns the standing position or null.
	 */
	static BlockPos findSpawnSpot(ServerLevel level, int x, int z, int nearY) {
		if (!level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) {
			return null;
		}
		int minY = level.getMinY();
		int maxY = level.getMaxY() - 3;
		int[] order = {0, -1, 1, -2, 2, -3, 3, -4, 4, -5, 5, -6, 6};
		for (int d : order) {
			int y = nearY - 1 + d;
			if (y <= minY || y >= maxY) {
				continue;
			}
			BlockInfo ground = info(level, new BlockPos(x, y, z));
			BlockInfo feet = info(level, new BlockPos(x, y + 1, z));
			BlockInfo head = info(level, new BlockPos(x, y + 2, z));
			if (Safety.isSolidGround(ground) && Safety.isFreeSpace(feet) && Safety.isFreeSpace(head)) {
				return new BlockPos(x, y + 1, z);
			}
		}
		return null;
	}

	/**
	 * Where to drop an item near {@code origin} at horizontal offset (dx, dz), up to {@code height}
	 * blocks up: the player's feet if there is no headroom or it would fall into lava/void.
	 */
	static Vec3 dropSpot(ServerLevel level, Vec3 origin, int dx, int dz, int height) {
		Vec3 feet = new Vec3(origin.x, origin.y + 0.2, origin.z);
		int x = (int) Math.floor(origin.x + dx);
		int z = (int) Math.floor(origin.z + dz);
		int y0 = (int) Math.floor(origin.y);
		if (!level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) {
			return feet;
		}
		int h = height;
		for (int i = 0; i <= height; i++) {
			BlockInfo b = info(level, new BlockPos(x, y0 + i, z));
			if (!Safety.isFreeSpace(b) && !b.air()) {
				if (i == 0) {
					return feet;
				}
				h = i - 1;
				break;
			}
		}
		BlockInfo below = null;
		for (int y = y0 - 1; y >= Math.max(level.getMinY(), y0 - 24); y--) {
			BlockInfo b = info(level, new BlockPos(x, y, z));
			if (!b.air()) {
				below = b;
				break;
			}
		}
		if (!Safety.safeDropSurface(below)) {
			return feet;
		}
		return new Vec3(x + 0.5, y0 + Math.max(0, h) + 0.5, z + 0.5);
	}

	/**
	 * A teleport landing chosen earlier is still safe now (§13.4, re-checked when the teleport happens after its
	 * veil): inside the border, chunk loaded (no new load), and the column still passes the landing rules.
	 */
	static boolean landingStillSafe(ServerLevel level, int x, int y, int z) {
		return level.getWorldBorder().isWithinBounds(new BlockPos(x, 0, z))
			&& level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))
			&& Safety.isSafeLanding(column(level, x, y, z));
	}

	/**
	 * An item drop spot chosen earlier is still safe now (§13.4, re-checked when the diamond lands after its glint
	 * column): chunk loaded, not inside a fluid or hazard, and the first non-air block below is solid and safe.
	 */
	static boolean dropStillSafe(ServerLevel level, Vec3 at) {
		BlockPos pos = BlockPos.containing(at);
		if (!level.hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()))) {
			return false;
		}
		BlockInfo here = info(level, pos);
		if (here.fluid() || Safety.UNSAFE_DROP_SURFACE.contains(here.id())) {
			return false;
		}
		for (int y = pos.getY() - 1; y >= Math.max(level.getMinY(), pos.getY() - 24); y--) {
			BlockInfo b = info(level, new BlockPos(pos.getX(), y, pos.getZ()));
			if (!b.air()) {
				return !b.fluid() && Safety.safeDropSurface(b);
			}
		}
		return false;
	}

	/** Within {@code radius} of a Wither, Warden or Ender Dragon. */
	static boolean nearBoss(ServerPlayer player, int radius) {
		if (radius <= 0) {
			return false;
		}
		AABB box = player.getBoundingBox().inflate(radius);
		return !player.level().getEntitiesOfClass(LivingEntity.class, box, ChaosWorld::isBoss).isEmpty();
	}

	static boolean isBoss(Entity e) {
		var key = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
		return key.getNamespace().equals("minecraft") && BOSSES.contains(key.getPath()) && e.isAlive();
	}

	/** A casino menu (any menu of this mod) is open → events are deferred. */
	static boolean casinoScreenOpen(ServerPlayer player) {
		var menu = player.containerMenu;
		return menu != null && menu != player.inventoryMenu && menu.getClass().getName().startsWith("dev.nezo.burmaldaholic.");
	}

	/** Seated at a casino table with an open stake within 2 chunks (a teleport would break the round). */
	static boolean inTableRound(ServerPlayer player) {
		ServerLevel level = player.level();
		int cx = SectionPos.blockToSectionCoord(player.getBlockX());
		int cz = SectionPos.blockToSectionCoord(player.getBlockZ());
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
				if (chunk == null) {
					continue;
				}
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					if (be instanceof CasinoTableBlockEntity table && table.isSeated(player) && table.stakeOf(player.getUUID()) > 0) {
						return true;
					}
				}
			}
		}
		return false;
	}
}
