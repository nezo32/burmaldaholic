package dev.nezo.burmaldaholic.worldgen.structure;

import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * Ground work of the High Roller Lounge standing on an End island (lane WG, owner playtest W2), two pieces around the
 * template piece:
 * <ul>
 *   <li>{@link Mode#CARVE} (before the template): terrain above the floor plate inside the footprint and above the
 *       front steps is cleared ({@link #CLEAR_ROWS} rows), so no island bump stays on the terrace or in the hall;</li>
 *   <li>{@link Mode#FILL} (after the template): every footprint column is solid from the plate down to the island
 *       (the template's stem rows included) — the skirt over the ≤ 4-block slope — and a 3-wide flight of end stone
 *       brick stairs leads from the door down to the ground.</li>
 * </ul>
 */
public final class CasinoFootingPiece extends StructurePiece {
	public enum Mode { CARVE, FILL }

	static final int CLEAR_ROWS = 8;
	/** Deepest fill below the template's bottom row. */
	static final int MAX_DEPTH = 16;
	/** Template rows under the plate (the Lounge's stem rows 0..2). */
	private static final int UNDER = 3;

	private final Mode mode;
	private final int minX;
	private final int minZ;
	private final int maxX;
	private final int maxZ;
	private final int plateY;
	private final Facing door;
	private final int outX;
	private final int outZ;
	private final int steps;

	/**
	 * @param outX  first cell outside the door (centre column of the steps)
	 * @param steps number of stairs (0 = the ground is flush with the plate)
	 */
	public CasinoFootingPiece(Mode mode, int minX, int minZ, int maxX, int maxZ, int plateY, Facing door, int outX, int outZ, int steps) {
		super(CasinoStructureTypes.FOOTING, 0, bounds(mode, minX, minZ, maxX, maxZ, plateY, door, outX, outZ, steps));
		this.mode = mode;
		this.minX = minX;
		this.minZ = minZ;
		this.maxX = maxX;
		this.maxZ = maxZ;
		this.plateY = plateY;
		this.door = door;
		this.outX = outX;
		this.outZ = outZ;
		this.steps = steps;
	}

	public CasinoFootingPiece(StructurePieceSerializationContext ctx, CompoundTag tag) {
		super(CasinoStructureTypes.FOOTING, tag);
		this.mode = tag.getIntOr("mode", 0) == 1 ? Mode.FILL : Mode.CARVE;
		int[] b = tag.getIntArray("fp").orElse(new int[4]);
		this.minX = b[0];
		this.minZ = b[1];
		this.maxX = b[2];
		this.maxZ = b[3];
		this.plateY = tag.getIntOr("plate", 0);
		this.door = Facing.values()[Math.floorMod(tag.getIntOr("door", 0), 4)];
		this.outX = tag.getIntOr("ox", 0);
		this.outZ = tag.getIntOr("oz", 0);
		this.steps = tag.getIntOr("steps", 0);
	}

	private static BoundingBox bounds(Mode mode, int minX, int minZ, int maxX, int maxZ, int plateY, Facing door, int outX, int outZ,
			int steps) {
		int ax = Math.min(minX, outX - 1);
		int az = Math.min(minZ, outZ - 1);
		int bx = Math.max(maxX, outX + 1);
		int bz = Math.max(maxZ, outZ + 1);
		if (steps > 0) {
			int ex = outX + door.dx() * (steps - 1);
			int ez = outZ + door.dz() * (steps - 1);
			ax = Math.min(ax, ex - 1);
			az = Math.min(az, ez - 1);
			bx = Math.max(bx, ex + 1);
			bz = Math.max(bz, ez + 1);
		}
		return mode == Mode.CARVE
			? new BoundingBox(ax, plateY + 1, az, bx, plateY + CLEAR_ROWS, bz)
			: new BoundingBox(ax, plateY - UNDER - MAX_DEPTH, az, bx, plateY, bz);
	}

	public Mode mode() {
		return mode;
	}

	public int plateY() {
		return plateY;
	}

	public int steps() {
		return steps;
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
		tag.putInt("mode", mode.ordinal());
		tag.putIntArray("fp", new int[] {minX, minZ, maxX, maxZ});
		tag.putInt("plate", plateY);
		tag.putInt("door", door.ordinal());
		tag.putInt("ox", outX);
		tag.putInt("oz", outZ);
		tag.putInt("steps", steps);
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random, BoundingBox chunkBox,
			ChunkPos chunk, BlockPos pivot) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		Facing side = door.rotate(1);
		if (mode == Mode.CARVE) {
			for (int x = Math.max(minX, chunkBox.minX()); x <= Math.min(maxX, chunkBox.maxX()); x++) {
				for (int z = Math.max(minZ, chunkBox.minZ()); z <= Math.min(maxZ, chunkBox.maxZ()); z++) {
					clear(level, p, x, z, plateY + 1, plateY + CLEAR_ROWS);
				}
			}
			for (int k = 0; k <= steps; k++) {
				for (int l = -1; l <= 1; l++) {
					int x = outX + door.dx() * k + side.dx() * l;
					int z = outZ + door.dz() * k + side.dz() * l;
					if (chunkBox.isInside(x, chunkBox.minY(), z)) {
						clear(level, p, x, z, plateY - k + 1, plateY + 4);
					}
				}
			}
			return;
		}
		BlockState fill = Blocks.END_STONE_BRICKS.defaultBlockState();
		for (int x = Math.max(minX, chunkBox.minX()); x <= Math.min(maxX, chunkBox.maxX()); x++) {
			for (int z = Math.max(minZ, chunkBox.minZ()); z <= Math.min(maxZ, chunkBox.maxZ()); z++) {
				// template rows under the plate: anything not a full block becomes fill
				for (int y = plateY - UNDER; y < plateY; y++) {
					p.set(x, y, z);
					BlockState s = level.getBlockState(p);
					if (!s.isCollisionShapeFullBlock(level, p) || !s.getFluidState().isEmpty()) {
						level.setBlock(p, fill, 2);
					}
				}
				down(level, p, x, z, plateY - UNDER - 1, fill);
			}
		}
		BlockState stair = Blocks.END_STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, dir(door.opposite()))
			.setValue(StairBlock.HALF, Half.BOTTOM);
		for (int k = 0; k < steps; k++) {
			for (int l = -1; l <= 1; l++) {
				int x = outX + door.dx() * k + side.dx() * l;
				int z = outZ + door.dz() * k + side.dz() * l;
				if (!chunkBox.isInside(x, plateY - k, z)) {
					continue;
				}
				p.set(x, plateY - k, z);
				level.setBlock(p, stair, 2);
				down(level, p, x, z, plateY - k - 1, fill);
			}
		}
	}

	private static void clear(WorldGenLevel level, BlockPos.MutableBlockPos p, int x, int z, int from, int to) {
		for (int y = from; y <= to; y++) {
			p.set(x, y, z);
			if (!level.isOutsideBuildHeight(p) && !level.getBlockState(p).isAir()) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
			}
		}
	}

	/** Fills downwards from {@code top} until a full solid block (the island) or {@link #MAX_DEPTH}. */
	private static void down(WorldGenLevel level, BlockPos.MutableBlockPos p, int x, int z, int top, BlockState fill) {
		for (int y = top; y > top - MAX_DEPTH; y--) {
			p.set(x, y, z);
			if (level.isOutsideBuildHeight(p)) {
				return;
			}
			BlockState s = level.getBlockState(p);
			if (s.isCollisionShapeFullBlock(level, p) && s.getFluidState().isEmpty()) {
				return;
			}
			level.setBlock(p, fill, 2);
		}
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
