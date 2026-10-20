package dev.nezo.burmaldaholic.worldgen.structure;

import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import dev.nezo.burmaldaholic.worldgen.logic.Geometry;
import dev.nezo.burmaldaholic.worldgen.logic.site.Tunnel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * A walk tunnel of a casino (lane WG, owner playtest W1): the Piglin Parlor's front passage to open air and its back
 * passage into the bastion ({@link Tunnel}, planned by {@code NetherSite}). Placed after the casino template, chunk
 * by chunk:
 * <ul>
 *   <li>the walk volume (3 wide, 4 high) is cleared;</li>
 *   <li>outside the first {@code keep} steps (those inside the building) the floor is polished blackstone bricks,
 *       a step between two heights is a stair;</li>
 *   <li>the shell (walls, ceiling, under the floor, the far end) is sealed where it holds a fluid, where a fluid
 *       touches it from outside, or where it would fall (gravel): polished blackstone bricks (not spring-valid), lava
 *       at eye level behind glass;</li>
 *   <li>a shroomlight in the ceiling every {@link #LIGHT_EVERY} steps where the ceiling is rock.</li>
 * </ul>
 */
public final class CasinoTunnelPiece extends StructurePiece {
	static final int LIGHT_EVERY = 5;

	private final Tunnel tunnel;
	private final int keep;

	public CasinoTunnelPiece(Tunnel tunnel, int keep) {
		super(CasinoStructureTypes.TUNNEL, 0, bounds(tunnel));
		this.tunnel = tunnel;
		this.keep = keep;
	}

	public CasinoTunnelPiece(StructurePieceSerializationContext ctx, CompoundTag tag) {
		super(CasinoStructureTypes.TUNNEL, tag);
		this.tunnel = new Tunnel(tag.getIntOr("tx", 0), tag.getIntOr("tz", 0), Facing.values()[Math.floorMod(tag.getIntOr("dir", 0), 4)],
			tag.getIntArray("feet").orElse(new int[] {0}));
		this.keep = tag.getIntOr("keep", 0);
	}

	private static BoundingBox bounds(Tunnel t) {
		Geometry.Box b = t.box();
		Facing d = t.dir();
		// one more step for the sealed far end
		return new BoundingBox(Math.min(b.minX(), b.minX() + d.dx()), b.minY(), Math.min(b.minZ(), b.minZ() + d.dz()),
			Math.max(b.maxX(), b.maxX() + d.dx()), b.maxY(), Math.max(b.maxZ(), b.maxZ() + d.dz()));
	}

	public Tunnel tunnel() {
		return tunnel;
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
		tag.putInt("tx", tunnel.x());
		tag.putInt("tz", tunnel.z());
		tag.putInt("dir", tunnel.dir().ordinal());
		tag.putIntArray("feet", tunnel.feet());
		tag.putInt("keep", keep);
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random, BoundingBox chunkBox,
			ChunkPos chunk, BlockPos pivot) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		Facing s = tunnel.side();
		int n = tunnel.length();
		// 1. carve the walk volume and lay the floor
		for (int i = 0; i < n; i++) {
			int f = tunnel.feet(i);
			for (int l = -1; l <= 1; l++) {
				int x = tunnel.cx(i) + s.dx() * l;
				int z = tunnel.cz(i) + s.dz() * l;
				for (int y = f; y < f + Tunnel.HEIGHT; y++) {
					if (in(chunkBox, x, y, z)) {
						p.set(x, y, z);
						if (!level.getBlockState(p).isAir()) {
							level.setBlock(p, Blocks.CAVE_AIR.defaultBlockState(), 2);
						}
					}
				}
				if (i >= keep && in(chunkBox, x, f - 1, z)) {
					p.set(x, f - 1, z);
					Facing st = tunnel.stair(i);
					BlockState floor = st == null ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
						: Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, dir(st))
							.setValue(StairBlock.HALF, Half.BOTTOM);
					level.setBlock(p, floor, 2);
				}
			}
		}
		// 2. seal the shell
		for (int i = keep; i <= n; i++) {
			boolean end = i == n;
			int f = tunnel.feet(Math.min(i, n - 1));
			int cx = tunnel.cx(i);
			int cz = tunnel.cz(i);
			for (int l = -2; l <= 2; l++) {
				int x = cx + s.dx() * l;
				int z = cz + s.dz() * l;
				for (int y = f - 2; y <= f + Tunnel.HEIGHT; y++) {
					boolean walk = !end && Math.abs(l) <= 1 && y >= f - 1 && y < f + Tunnel.HEIGHT;
					if (walk || !in(chunkBox, x, y, z)) {
						continue;
					}
					p.set(x, y, z);
					BlockState state = level.getBlockState(p);
					boolean ceiling = y == f + Tunnel.HEIGHT;
					boolean fluid = !state.getFluidState().isEmpty();
					boolean falls = ceiling && state.getBlock() instanceof FallingBlock;
					if (fluid || falls || (!state.isCollisionShapeFullBlock(level, p) && touchesFluid(level, x, y, z))) {
						boolean glass = fluid && !ceiling && !end && Math.abs(l) == 2 && (y == f + 1 || y == f + 2);
						level.setBlock(p, glass ? Blocks.GLASS.defaultBlockState() : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 2);
					} else if (ceiling && l == 0 && !end && i % LIGHT_EVERY == 2 && state.isCollisionShapeFullBlock(level, p)) {
						level.setBlock(p, Blocks.SHROOMLIGHT.defaultBlockState(), 2);
					}
				}
			}
		}
	}

	/** A fluid in a neighbour of this shell cell that is not part of the walk volume. */
	private static boolean touchesFluid(WorldGenLevel level, int x, int y, int z) {
		BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
		for (Direction d : Direction.values()) {
			q.set(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ());
			if (!level.getFluidState(q).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	private static boolean in(BoundingBox b, int x, int y, int z) {
		return b.isInside(x, y, z);
	}

	private static Direction dir(Facing f) {
		return switch (f) {
			case NORTH -> Direction.NORTH;
			case EAST -> Direction.EAST;
			case SOUTH -> Direction.SOUTH;
			case WEST -> Direction.WEST;
		};
	}
}
