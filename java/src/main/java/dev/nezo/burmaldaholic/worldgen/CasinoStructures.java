package dev.nezo.burmaldaholic.worldgen;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoPack;
import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import dev.nezo.burmaldaholic.worldgen.logic.Gates;
import dev.nezo.burmaldaholic.worldgen.logic.Geometry;
import dev.nezo.burmaldaholic.worldgen.logic.Layout;
import dev.nezo.burmaldaholic.worldgen.logic.Layouts;
import dev.nezo.burmaldaholic.worldgen.logic.Vec;
import dev.nezo.burmaldaholic.worldgen.mixin.TemplateStructurePieceAccessor;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Piece;
import dev.nezo.burmaldaholic.worldgen.logic.site.EndSite;
import dev.nezo.burmaldaholic.worldgen.logic.site.NetherSite;
import dev.nezo.burmaldaholic.worldgen.mixin.SinglePoolElementAccessor;
import dev.nezo.burmaldaholic.worldgen.structure.CasinoGroundPiece;
import dev.nezo.burmaldaholic.worldgen.structure.FitSite;
import dev.nezo.burmaldaholic.worldgen.logic.pieces.HighRollerLounge;
import dev.nezo.burmaldaholic.worldgen.logic.pieces.PiglinParlor;
import dev.nezo.burmaldaholic.worldgen.structure.CasinoTunnelPiece;
import dev.nezo.burmaldaholic.worldgen.structure.DensityField;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * Casino rooms appended to vanilla structures while their pieces are generated
 * ({@code StructureMixin}, right after {@code Structure.generate} builds the pieces):
 * <ul>
 *   <li><b>Piglin Parlor</b> (§16.2, owner playtest W1): {@code worldgen.piglinParlor.chance} of bastion remnants get
 *       the Parlor beside the bastion at a probed site ({@link NetherSite}): above the lava sea, a front tunnel to
 *       open air, a back tunnel into the bastion ({@link CasinoTunnelPiece}).</li>
 *   <li><b>High Roller Lounge</b> (§16.3, owner playtest W2): {@code worldgen.highRoller.chance} of End Cities get the
 *       Lounge standing on the island beside the city ({@link EndSite}, at least 24 blocks from every city piece),
 *       its own skirt continued to the ground ({@link CasinoGroundPiece}).</li>
 * </ul>
 * The room is a regular pool-element piece of the host structure (saved with it, placed chunk by
 * chunk). Gates hash the world seed + structure chunk, so generation is reproducible per seed.
 */
public final class CasinoStructures {
	static final String BASTION = "minecraft:bastion_remnant";
	static final String END_CITY = "minecraft:end_city";
	private static final Set<String> TOWER_TOPS = Set.of("tower_top", "fat_tower_top");
	/** Structure starts are referenced by chunks up to 8 chunks away; keep the room inside that. */
	private static final int MAX_CHUNK_REACH = 7;

	private CasinoStructures() {}

	public static void append(Holder<Structure> structure, RegistryAccess registries, StructureTemplateManager templates,
			long seed, ChunkPos chunk, StructurePiecesBuilder builder, ChunkGenerator generator, RandomState randomState,
			LevelHeightAccessor height) {
		if (builder.isEmpty()) {
			return;
		}
		String id = structure.unwrapKey().map(k -> k.identifier().toString()).orElse("");
		boolean bastion = id.equals(BASTION);
		boolean endCity = id.equals(END_CITY);
		if (!bastion && !endCity) {
			return;
		}
		if (!WorldgenRuntime.active()) {
			return;
		}
		try {
			if (bastion && Gates.pass(WorldgenRuntime.parlorChance(), seed, chunk.x(), chunk.z(), Gates.SALT_PARLOR)) {
				appendParlor(registries, templates, seed, chunk, builder, generator, randomState, height);
			} else if (endCity && Gates.pass(WorldgenRuntime.loungeChance(), seed, chunk.x(), chunk.z(), Gates.SALT_LOUNGE)) {
				appendLounge(registries, templates, seed, chunk, builder, generator, randomState, height);
			}
		} catch (RuntimeException e) {
			// Never break vanilla generation because of a casino.
			Burmaldaholic.LOGGER.warn("[worldgen] could not append a casino to {} at {}", id, chunk, e);
		}
	}

	/**
	 * Piglin Parlor (owner playtest W1): on a real noise Nether the site comes from {@link NetherSite} (terrain probe,
	 * front tunnel to open air, back tunnel into the bastion); no site = no Parlor. Other generators (flat test
	 * worlds) keep the v0.1 placement beside the bastion on its lowest level.
	 */
	private static void appendParlor(RegistryAccess registries, StructureTemplateManager templates, long seed, ChunkPos chunk,
			StructurePiecesBuilder builder, ChunkGenerator generator, RandomState randomState, LevelHeightAccessor height) {
		Optional<CasinoElements.Choice> choice = CasinoElements.preferV2(registries.lookupOrThrow(Registries.TEMPLATE_POOL), CasinoPack.parlor(),
			Layouts.piglinParlorLayout());
		if (choice.isEmpty()) {
			return;
		}
		Layout layout = choice.get().layout();
		StructurePoolElement element = choice.get().element();
		Geometry.Box host = box(builder.getBoundingBox());
		int first = (int) (Gates.unit(seed, chunk.x(), chunk.z(), Gates.SALT_PARLOR ^ 0x51DE) * 4);
		if (!(generator instanceof NoiseBasedChunkGenerator) || !layout.id().equals(PiglinParlor.ID)) {
			for (int i = 0; i < 4; i++) {
				Facing side = Facing.values()[(first + i) % 4];
				if (tryAdd(templates, element, Geometry.beside(host, layout.size(), side, 0), chunk, builder)) {
					return;
				}
			}
			return;
		}
		List<StructurePiece> hostPieces = builder.build().pieces();
		List<Geometry.Box> boxes = new ArrayList<>();
		for (StructurePiece p : hostPieces) {
			boxes.add(box(p.getBoundingBox()));
		}
		NetherSite.Doors doors = parlorDoors(layout);
		DensityField field = DensityField.of(generator, randomState, height);
		Optional<NetherSite.Plan> plan = NetherSite.plan(host, boxes, layout.size(), doors, field, new HostCells(templates, hostPieces),
			generator.getSeaLevel(), chunk.x(), chunk.z(), first);
		if (plan.isEmpty()) {
			Burmaldaholic.LOGGER.debug("[worldgen] no Piglin Parlor site at the bastion in {}", chunk);
			return;
		}
		NetherSite.Plan p = plan.get();
		if (!tryAdd(templates, element, p.placement(), chunk, builder)) {
			return;
		}
		builder.addPiece(new CasinoTunnelPiece(p.front(), 0));
		if (p.back() != null) {
			builder.addPiece(new CasinoTunnelPiece(p.back(), 0));
		}
	}

	/**
	 * Door cells of the Parlor template for the tunnels: the front entry on the +z face, and the first cell of the back
	 * passage, one step outside the back entry on the -z face (CR-2 contract: {@code PiglinParlor.FRONT_ENTRY},
	 * {@code BACK_ENTRY}). A layout without a back entry has no back tunnel.
	 */
	public static NetherSite.Doors parlorDoors(Layout layout) {
		Vec size = layout.size();
		Optional<Piece> piece = CasinoPack.piece(layout.id());
		Vec front = new Vec(size.x() / 2, 1, size.z() - 1);
		Vec back = null;
		if (piece.isPresent()) {
			for (Vec e : piece.get().entries()) {
				if (e.z() == size.z() - 1) {
					front = e;
				} else if (e.z() == 0) {
					back = new Vec(e.x(), e.y(), -1);
				}
			}
		}
		return new NetherSite.Doors(front, back);
	}

	/**
	 * High Roller Lounge (owner playtest W2): standalone on the island surface beside the End City ({@link EndSite}),
	 * at least 24 blocks from every city piece; a skirt piece after it reaches the ground where the probe was off.
	 * Other generators (and the v0.1 layout) keep the v0.1 tower-top placement.
	 */
	private static void appendLounge(RegistryAccess registries, StructureTemplateManager templates, long seed, ChunkPos chunk,
			StructurePiecesBuilder builder, ChunkGenerator generator, RandomState randomState, LevelHeightAccessor height) {
		Optional<CasinoElements.Choice> choice = CasinoElements.preferV2(registries.lookupOrThrow(Registries.TEMPLATE_POOL), CasinoPack.lounge(),
			Layouts.highRollerLayout());
		if (choice.isEmpty()) {
			return;
		}
		Layout layout = choice.get().layout();
		StructurePoolElement element = choice.get().element();
		List<StructurePiece> cityPieces = builder.build().pieces();
		if (!(generator instanceof NoiseBasedChunkGenerator)) {
			legacyLounge(templates, element, layout, seed, chunk, builder, cityPieces);
			return;
		}
		if (!layout.id().equals(HighRollerLounge.ID)) {
			legacyLounge(templates, element, layout, seed, chunk, builder, cityPieces);
			return;
		}
		List<Geometry.Box> city = new ArrayList<>();
		int low = Integer.MAX_VALUE;
		for (StructurePiece p : cityPieces) {
			city.add(box(p.getBoundingBox()));
			low = Math.min(low, p.getBoundingBox().minY());
		}
		DensityField field = DensityField.of(generator, randomState, height, 8, 4);
		int scanLow = Math.max(height.getMinY() + 1, low - 40);
		int scanHigh = Math.min(height.getMaxY() - 1, low + 40);
		EndSite.Surface surface = (x, z) -> {
			int t = field.top(x, z, scanLow, scanHigh);
			return t == Integer.MIN_VALUE ? EndSite.Surface.VOID : t;
		};
		Optional<EndSite.Plan> plan = EndSite.plan(city, LOUNGE_SHAPE, surface, scanLow + 1, chunk.x(), chunk.z());
		if (plan.isEmpty()) {
			Burmaldaholic.LOGGER.debug("[worldgen] no High Roller Lounge site beside the End City in {}", chunk);
			return;
		}
		Geometry.Placement placement = plan.get().placement();
		if (!tryAdd(templates, element, placement, chunk, builder)) {
			return;
		}
		// the piece's own skirt is SKIRT rows deep; where the probe was a block off, the skirt continues to the ground
		Geometry.Box fp = placement.box();
		builder.addPiece(new CasinoGroundPiece(CasinoGroundPiece.Mode.SKIRT, List.of(new FitSite.Box(fp.minX(), fp.minZ(), fp.maxX(), fp.maxZ())),
			fp.minY() + 3, fp.maxY()));
	}

	/** The v0.2 Lounge's shape (CR-2 contract): plate rows z 0..PZ on row FLOOR, front stair foot. */
	public static final EndSite.Shape LOUNGE_SHAPE = new EndSite.Shape(new Vec(HighRollerLounge.SX, HighRollerLounge.SY, HighRollerLounge.SZ),
		HighRollerLounge.PZ, HighRollerLounge.FLOOR, HighRollerLounge.STAIR_FOOT);

	private static void legacyLounge(StructureTemplateManager templates, StructurePoolElement element, Layout layout, long seed, ChunkPos chunk,
			StructurePiecesBuilder builder, List<StructurePiece> cityPieces) {
		List<StructurePiece> tops = new ArrayList<>();
		for (StructurePiece piece : cityPieces) {
			if (piece instanceof TemplateStructurePiece t
				&& TOWER_TOPS.contains(((TemplateStructurePieceAccessor) t).burmaldaholic$templateName())) {
				tops.add(piece);
			}
		}
		tops.sort(Comparator.comparingInt((StructurePiece p) -> p.getBoundingBox().maxY()).reversed());
		Facing facing = Facing.values()[(int) (Gates.unit(seed, chunk.x(), chunk.z(), Gates.SALT_LOUNGE ^ 0x51DE) * 4)];
		for (StructurePiece top : tops) {
			if (tryAdd(templates, element, Geometry.onTop(box(top.getBoundingBox()), layout.size(), facing), chunk, builder)) {
				return;
			}
		}
	}

	private static boolean tryAdd(StructureTemplateManager templates, StructurePoolElement element, Geometry.Placement placement,
			ChunkPos chunk, StructurePiecesBuilder builder) {
		if (Geometry.chunkReach(placement.box(), chunk.x(), chunk.z()) > MAX_CHUNK_REACH) {
			return false;
		}
		Vec o = placement.origin();
		BlockPos origin = new BlockPos(o.x(), o.y(), o.z());
		Rotation rotation = rotation(placement.quarterTurns());
		BoundingBox bb = element.getBoundingBox(templates, origin, rotation);
		if (builder.findCollisionPiece(bb) != null) {
			return false;
		}
		builder.addPiece(new PoolElementStructurePiece(templates, element, origin, 0, rotation, bb, LiquidSettings.IGNORE_WATERLOGGING));
		return true;
	}

	/**
	 * The host structure's template blocks for the back tunnel: inside a piece box a cell is {@code AIR} where the
	 * piece's template has air (rooms, halls), else {@code SOLID}; outside every piece {@code OUTSIDE}.
	 */
	static final class HostCells implements NetherSite.Host {
		private final StructureTemplateManager templates;
		private final List<StructurePiece> pieces;
		private final Map<StructurePiece, Set<Long>> air = new HashMap<>();

		HostCells(StructureTemplateManager templates, List<StructurePiece> pieces) {
			this.templates = templates;
			this.pieces = pieces;
		}

		@Override
		public int cell(int x, int y, int z) {
			int found = OUTSIDE;
			for (StructurePiece p : pieces) {
				if (p.getBoundingBox().isInside(x, y, z)) {
					if (air.computeIfAbsent(p, this::airOf).contains(BlockPos.asLong(x, y, z))) {
						return AIR;
					}
					found = SOLID;
				}
			}
			return found;
		}

		private Set<Long> airOf(StructurePiece piece) {
			Set<Long> out = new HashSet<>();
			if (piece instanceof PoolElementStructurePiece pe && pe.getElement() instanceof SinglePoolElement single) {
				try {
					StructureTemplate template = ((SinglePoolElementAccessor) single).burmaldaholic$template().map(templates::getOrCreate, t -> t);
					StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(pe.getRotation());
					for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(pe.getPosition(), settings, Blocks.AIR)) {
						out.add(info.pos().asLong());
					}
				} catch (RuntimeException e) {
					// unknown template: the piece counts as solid
				}
			}
			return out;
		}
	}

	static Geometry.Box box(BoundingBox bb) {
		return new Geometry.Box(bb.minX(), bb.minY(), bb.minZ(), bb.maxX(), bb.maxY(), bb.maxZ());
	}

	public static Rotation rotation(int quarterTurns) {
		return switch (Math.floorMod(quarterTurns, 4)) {
			case 1 -> Rotation.CLOCKWISE_90;
			case 2 -> Rotation.CLOCKWISE_180;
			case 3 -> Rotation.COUNTERCLOCKWISE_90;
			default -> Rotation.NONE;
		};
	}

	public static int quarterTurns(Rotation rotation) {
		return switch (rotation) {
			case CLOCKWISE_90 -> 1;
			case CLOCKWISE_180 -> 2;
			case COUNTERCLOCKWISE_90 -> 3;
			default -> 0;
		};
	}
}
