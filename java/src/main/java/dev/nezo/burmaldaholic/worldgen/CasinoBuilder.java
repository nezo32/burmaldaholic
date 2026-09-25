package dev.nezo.burmaldaholic.worldgen;

import dev.nezo.burmaldaholic.worldgen.logic.CasinoKind;
import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import dev.nezo.burmaldaholic.worldgen.logic.Geometry;
import dev.nezo.burmaldaholic.worldgen.logic.Layout;
import dev.nezo.burmaldaholic.worldgen.logic.Layouts;
import dev.nezo.burmaldaholic.worldgen.logic.Vec;
import dev.nezo.burmaldaholic.worldgen.logic.VillageStyle;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import dev.nezo.burmaldaholic.worldgen.mixin.MinecraftServerAccessor;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;

/**
 * Builds a casino right now (ops: {@code /casino worldgen build <kind>}; GameTests). Uses the same
 * pool element as world generation, so markers (NPCs, chest, registration) run the same way.
 */
public final class CasinoBuilder {
	private CasinoBuilder() {}

	/** Layout for a kind at a position (village casinos pick the local village style). */
	public static Layout layoutFor(ServerLevel level, CasinoKind kind, BlockPos at) {
		String biome = level.getBiome(at).unwrapKey().map(k -> k.identifier().toString()).orElse("minecraft:plains");
		return Layouts.forKind(kind, VillageStyle.forBiome(biome));
	}

	/** Builds in front of a viewer at {@code feet} looking {@code look}, entrance facing them. */
	public static Optional<BoundingBox> buildInFront(ServerLevel level, Layout layout, BlockPos feet, Facing look) {
		Geometry.Placement p = Geometry.inFront(new Vec(feet.getX(), feet.getY(), feet.getZ()), layout.size(), look, 2);
		return build(level, layout, new BlockPos(p.origin().x(), p.origin().y(), p.origin().z()), CasinoStructures.rotation(p.quarterTurns()));
	}

	/** The server's structure template manager (works on 26.2 and 26.3). */
	public static StructureTemplateManager templates(MinecraftServer server) {
		return ((MinecraftServerAccessor) server).burmaldaholic$structureTemplateManager();
	}

	/** Places the template at {@code origin}; empty if the data pack (pool) is not loaded. */
	public static Optional<BoundingBox> build(ServerLevel level, Layout layout, BlockPos origin, Rotation rotation) {
		Optional<StructurePoolElement> element = CasinoElements.element(
			level.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL), layout);
		if (element.isEmpty() || !(element.get() instanceof SinglePoolElement single)) {
			return Optional.empty();
		}
		StructureTemplateManager templates = templates(level.getServer());
		BoundingBox box = single.getBoundingBox(templates, origin, rotation);
		boolean ok = single.place(templates, level, level.structureManager(), level.getChunkSource().getGenerator(), origin, origin,
			rotation, box, level.getRandom(), LiquidSettings.IGNORE_WATERLOGGING, false);
		return ok ? Optional.of(box) : Optional.empty();
	}
}
