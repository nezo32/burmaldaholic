package dev.nezo.burmaldaholic.worldgen;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import dev.nezo.burmaldaholic.worldgen.logic.Gates;
import dev.nezo.burmaldaholic.worldgen.logic.Geometry;
import dev.nezo.burmaldaholic.worldgen.logic.Layout;
import dev.nezo.burmaldaholic.worldgen.logic.Layouts;
import dev.nezo.burmaldaholic.worldgen.logic.Vec;
import dev.nezo.burmaldaholic.worldgen.mixin.TemplateStructurePieceAccessor;
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
 *   <li><b>Piglin Parlor</b> (§16.2): {@code worldgen.piglinParlor.chance} of bastion remnants get the
 *       21×21 room appended next to the bastion on its lowest level, main entrance facing out, back
 *       door facing the bastion.</li>
 *   <li><b>High Roller Lounge</b> (§16.3): {@code worldgen.highRoller.chance} of End Cities get the
 *       13×13 lounge as a top floor on their highest tower top.</li>
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
			long seed, ChunkPos chunk, StructurePiecesBuilder builder) {
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
				appendParlor(registries, templates, seed, chunk, builder);
			} else if (endCity && Gates.pass(WorldgenRuntime.loungeChance(), seed, chunk.x(), chunk.z(), Gates.SALT_LOUNGE)) {
				appendLounge(registries, templates, seed, chunk, builder);
			}
		} catch (RuntimeException e) {
			// Never break vanilla generation because of a casino.
			Burmaldaholic.LOGGER.warn("[worldgen] could not append a casino to {} at {}", id, chunk, e);
		}
	}

	private static void appendParlor(RegistryAccess registries, StructureTemplateManager templates, long seed, ChunkPos chunk,
			StructurePiecesBuilder builder) {
		Layout layout = Layouts.piglinParlorLayout();
		Optional<StructurePoolElement> element = CasinoElements.element(registries.lookupOrThrow(Registries.TEMPLATE_POOL), layout);
		if (element.isEmpty()) {
			return;
		}
		Geometry.Box host = box(builder.getBoundingBox());
		int first = (int) (Gates.unit(seed, chunk.x(), chunk.z(), Gates.SALT_PARLOR ^ 0x51DE) * 4);
		for (int i = 0; i < 4; i++) {
			Facing side = Facing.values()[(first + i) % 4];
			if (tryAdd(templates, element.get(), Geometry.beside(host, layout.size(), side, 0), chunk, builder)) {
				return;
			}
		}
	}

	private static void appendLounge(RegistryAccess registries, StructureTemplateManager templates, long seed, ChunkPos chunk,
			StructurePiecesBuilder builder) {
		Layout layout = Layouts.highRollerLayout();
		Optional<StructurePoolElement> element = CasinoElements.element(registries.lookupOrThrow(Registries.TEMPLATE_POOL), layout);
		if (element.isEmpty()) {
			return;
		}
		List<StructurePiece> tops = new ArrayList<>();
		for (StructurePiece piece : builder.build().pieces()) {
			if (piece instanceof TemplateStructurePiece t
				&& TOWER_TOPS.contains(((TemplateStructurePieceAccessor) t).burmaldaholic$templateName())) {
				tops.add(piece);
			}
		}
		tops.sort(Comparator.comparingInt((StructurePiece p) -> p.getBoundingBox().maxY()).reversed());
		Facing facing = Facing.values()[(int) (Gates.unit(seed, chunk.x(), chunk.z(), Gates.SALT_LOUNGE ^ 0x51DE) * 4)];
		for (StructurePiece top : tops) {
			if (tryAdd(templates, element.get(), Geometry.onTop(box(top.getBoundingBox()), layout.size(), facing), chunk, builder)) {
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
