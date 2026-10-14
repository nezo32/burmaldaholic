package dev.nezo.burmaldaholic.worldgen.structure;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * The standalone casino's ground work (R-CB M7 "foundation skirt"; casino-buildings.md §6.1): two extra pieces of every
 * casino start, around the template pieces.
 * <ul>
 *   <li>{@link Mode#CARVE} (first piece): inside every piece box, everything above the floor block is cleared before
 *       the templates are placed — a hillside the fit left above the floor (≤ the cut limit) would otherwise stay
 *       inside the rooms ({@code beard_thin} only softens terrain near the box bottom).</li>
 *   <li>{@link Mode#SKIRT} (last piece): every column whose bottom foundation block (template y 0) stands on air,
 *       water or plants is continued downwards with that same block until it reaches solid ground (≤ {@link #SKIRT_DEPTH}),
 *       so the low side of a slope never floats.</li>
 * </ul>
 * Boxes are the template pieces' horizontal boxes; {@code floorY} is the floor block row (template y 3).
 */
public final class CasinoGroundPiece extends StructurePiece {
	public enum Mode { CARVE, SKIRT }

	/** Deepest skirt below the foundation (blocks). */
	static final int SKIRT_DEPTH = 16;
	/** Template rows below the floor block (foundation y 0..2). */
	static final int FOUNDATION_ROWS = 3;

	private final Mode mode;
	private final int floorY;
	private final int topY;
	private final List<FitSite.Box> boxes;

	public CasinoGroundPiece(Mode mode, List<FitSite.Box> boxes, int floorY, int topY) {
		super(CasinoStructureTypes.GROUND, 0, bounds(mode, boxes, floorY, topY));
		this.mode = mode;
		this.floorY = floorY;
		this.topY = topY;
		this.boxes = List.copyOf(boxes);
	}

	public CasinoGroundPiece(StructurePieceSerializationContext ctx, CompoundTag tag) {
		super(CasinoStructureTypes.GROUND, tag);
		this.mode = tag.getIntOr("mode", 0) == 1 ? Mode.SKIRT : Mode.CARVE;
		this.floorY = tag.getIntOr("floor", 0);
		this.topY = tag.getIntOr("top", 0);
		int[] flat = tag.getIntArray("boxes").orElse(new int[0]);
		List<FitSite.Box> list = new ArrayList<>();
		for (int i = 0; i + 3 < flat.length; i += 4) {
			list.add(new FitSite.Box(flat[i], flat[i + 1], flat[i + 2], flat[i + 3]));
		}
		this.boxes = List.copyOf(list);
	}

	private static BoundingBox bounds(Mode mode, List<FitSite.Box> boxes, int floorY, int topY) {
		int minX = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (FitSite.Box b : boxes) {
			minX = Math.min(minX, b.minX());
			minZ = Math.min(minZ, b.minZ());
			maxX = Math.max(maxX, b.maxX());
			maxZ = Math.max(maxZ, b.maxZ());
		}
		return mode == Mode.CARVE
			? new BoundingBox(minX, floorY + 1, minZ, maxX, Math.max(floorY + 1, topY), maxZ)
			: new BoundingBox(minX, floorY - FOUNDATION_ROWS - SKIRT_DEPTH, minZ, maxX, floorY - FOUNDATION_ROWS, maxZ);
	}

	public Mode mode() {
		return mode;
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
		tag.putInt("mode", mode.ordinal());
		tag.putInt("floor", floorY);
		tag.putInt("top", topY);
		int[] flat = new int[boxes.size() * 4];
		for (int i = 0; i < boxes.size(); i++) {
			FitSite.Box b = boxes.get(i);
			flat[4 * i] = b.minX();
			flat[4 * i + 1] = b.minZ();
			flat[4 * i + 2] = b.maxX();
			flat[4 * i + 3] = b.maxZ();
		}
		tag.putIntArray("boxes", flat);
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random, BoundingBox chunkBox,
			ChunkPos chunk, BlockPos pivot) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (FitSite.Box b : boxes) {
			int x0 = Math.max(b.minX(), chunkBox.minX());
			int x1 = Math.min(b.maxX(), chunkBox.maxX());
			int z0 = Math.max(b.minZ(), chunkBox.minZ());
			int z1 = Math.min(b.maxZ(), chunkBox.maxZ());
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					if (mode == Mode.CARVE) {
						carve(level, p, x, z);
					} else {
						skirt(level, p, x, z);
					}
				}
			}
		}
	}

	private void carve(WorldGenLevel level, BlockPos.MutableBlockPos p, int x, int z) {
		for (int y = floorY + 1; y <= topY; y++) {
			p.set(x, y, z);
			BlockState s = level.getBlockState(p);
			if (!s.isAir() && !level.isOutsideBuildHeight(p)) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
			}
		}
	}

	private void skirt(WorldGenLevel level, BlockPos.MutableBlockPos p, int x, int z) {
		int base = floorY - FOUNDATION_ROWS;
		p.set(x, base, z);
		BlockState foundation = level.getBlockState(p);
		if (foundation.isAir() || !foundation.getFluidState().isEmpty() || foundation.hasBlockEntity()
				|| !foundation.isCollisionShapeFullBlock(level, p)) {
			return; // no solid foundation in this column (forecourt, structure void)
		}
		for (int y = base - 1; y >= base - SKIRT_DEPTH; y--) {
			p.set(x, y, z);
			if (level.isOutsideBuildHeight(p)) {
				return;
			}
			BlockState s = level.getBlockState(p);
			if (s.isCollisionShapeFullBlock(level, p) && s.getFluidState().isEmpty()) {
				return; // ground reached
			}
			level.setBlock(p, foundation, 2);
		}
	}
}
