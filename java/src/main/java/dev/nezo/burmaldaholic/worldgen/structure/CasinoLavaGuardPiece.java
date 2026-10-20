package dev.nezo.burmaldaholic.worldgen.structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * Lava guard of the Piglin Parlor (lane WG, owner playtest W1), the last piece of the bastion start. Inside the
 * Parlor envelope and the tunnel walk volumes ({@code inner} boxes), plus a {@link #MARGIN}-block margin around them,
 * real blocks are checked after placement:
 * <ul>
 *   <li>lava (a source or flowing) next to an air cell of an inner box is replaced, so no lava can enter walkable
 *       space;</li>
 *   <li>outside the envelope, a block that a vanilla Nether lava spring may replace (netherrack, soul sand, gravel,
 *       magma, blackstone: {@code NetherFeatures.SPRING_*}) and that faces inner air is re-lined. Springs are placed
 *       after structures, so the open rock faces of the pocket and the tunnels must not be spring-valid.</li>
 * </ul>
 * Lining: smooth basalt around the envelope, polished blackstone bricks around the tunnels. Neither is spring-valid.
 * The Parlor's own sealed lava (glass-cased falls, channels under glass) touches no air and is left alone.
 */
public final class CasinoLavaGuardPiece extends StructurePiece {
	static final int MARGIN = 2;
	/** Blocks the vanilla Nether lava springs may replace (spring_lava_nether's valid set; open/closed use netherrack). */
	static final Set<Block> SPRING_VALID = Set.of(Blocks.NETHERRACK, Blocks.SOUL_SAND, Blocks.GRAVEL, Blocks.MAGMA_BLOCK, Blocks.BLACKSTONE);

	private final BoundingBox envelope;
	private final List<BoundingBox> tunnels;

	public CasinoLavaGuardPiece(BoundingBox envelope, List<BoundingBox> tunnels) {
		super(CasinoStructureTypes.LAVA_GUARD, 0, bounds(envelope, tunnels));
		this.envelope = envelope;
		this.tunnels = List.copyOf(tunnels);
	}

	public CasinoLavaGuardPiece(StructurePieceSerializationContext ctx, CompoundTag tag) {
		super(CasinoStructureTypes.LAVA_GUARD, tag);
		int[] e = tag.getIntArray("env").orElse(new int[6]);
		this.envelope = new BoundingBox(e[0], e[1], e[2], e[3], e[4], e[5]);
		int[] t = tag.getIntArray("tunnels").orElse(new int[0]);
		List<BoundingBox> list = new ArrayList<>();
		for (int i = 0; i + 5 < t.length; i += 6) {
			list.add(new BoundingBox(t[i], t[i + 1], t[i + 2], t[i + 3], t[i + 4], t[i + 5]));
		}
		this.tunnels = List.copyOf(list);
	}

	private static BoundingBox bounds(BoundingBox envelope, List<BoundingBox> tunnels) {
		BoundingBox b = envelope;
		for (BoundingBox t : tunnels) {
			b = new BoundingBox(Math.min(b.minX(), t.minX()), Math.min(b.minY(), t.minY()), Math.min(b.minZ(), t.minZ()),
				Math.max(b.maxX(), t.maxX()), Math.max(b.maxY(), t.maxY()), Math.max(b.maxZ(), t.maxZ()));
		}
		return b.inflatedBy(MARGIN);
	}

	public BoundingBox envelope() {
		return envelope;
	}

	public List<BoundingBox> tunnels() {
		return tunnels;
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
		tag.putIntArray("env", flat(envelope));
		int[] t = new int[tunnels.size() * 6];
		for (int i = 0; i < tunnels.size(); i++) {
			System.arraycopy(flat(tunnels.get(i)), 0, t, i * 6, 6);
		}
		tag.putIntArray("tunnels", t);
	}

	private static int[] flat(BoundingBox b) {
		return new int[] {b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()};
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random, BoundingBox chunkBox,
			ChunkPos chunk, BlockPos pivot) {
		guard(level, chunkBox);
	}

	/** The guard over the part of the piece inside {@code area} (a chunk during worldgen; the whole piece in tests). */
	public void guard(WorldGenLevel level, BoundingBox area) {
		BoundingBox box = getBoundingBox();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
		for (int x = Math.max(box.minX(), area.minX()); x <= Math.min(box.maxX(), area.maxX()); x++) {
			for (int z = Math.max(box.minZ(), area.minZ()); z <= Math.min(box.maxZ(), area.maxZ()); z++) {
				for (int y = Math.max(box.minY(), area.minY()); y <= Math.min(box.maxY(), area.maxY()); y++) {
					p.set(x, y, z);
					if (level.isOutsideBuildHeight(p)) {
						continue;
					}
					BlockState s = level.getBlockState(p);
					boolean lava = s.getFluidState().is(FluidTags.LAVA);
					boolean spring = !lava && !envelope.isInside(p) && SPRING_VALID.contains(s.getBlock());
					if (!lava && !spring) {
						continue;
					}
					BoundingBox facing = innerAirNeighbour(level, area, p, n);
					if (facing != null) {
						level.setBlock(p, facing == envelope ? Blocks.SMOOTH_BASALT.defaultBlockState()
							: Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 2);
					}
				}
			}
		}
	}

	/**
	 * The inner box (envelope or a tunnel) holding an air neighbour of {@code p}, or null. A neighbour outside
	 * {@code area} (another chunk, maybe not carved yet) counts as air: chunks are placed one by one.
	 */
	private BoundingBox innerAirNeighbour(WorldGenLevel level, BoundingBox area, BlockPos p, BlockPos.MutableBlockPos n) {
		for (Direction d : Direction.values()) {
			n.setWithOffset(p, d);
			if (area.isInside(n) && !level.getBlockState(n).isAir()) {
				continue;
			}
			for (BoundingBox t : tunnels) {
				if (t.isInside(n)) {
					return t;
				}
			}
			if (envelope.isInside(n)) {
				return envelope;
			}
		}
		return null;
	}
}
