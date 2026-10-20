package dev.nezo.burmaldaholic.worldgen.structure;

import dev.nezo.burmaldaholic.Burmaldaholic;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.structure.StructureType;

/** Registration of the structure type {@code burmaldaholic:casino} and its ground piece (architecture doc §6.1). */
public final class CasinoStructureTypes {
	private CasinoStructureTypes() {}

	public static final StructureType<CasinoStructure> CASINO = Registry.register(BuiltInRegistries.STRUCTURE_TYPE, Burmaldaholic.id("casino"),
		() -> CasinoStructure.CODEC);

	/** The casino's carve / skirt pieces ({@link CasinoGroundPiece}); saved with every v0.2 casino start. */
	public static final net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType GROUND = Registry.register(
		BuiltInRegistries.STRUCTURE_PIECE, Burmaldaholic.id("casino_ground"),
		(net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType) CasinoGroundPiece::new);

	/** Walk tunnels of the Piglin Parlor (lane WG, {@link CasinoTunnelPiece}); saved with the bastion start. */
	public static final net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType TUNNEL = Registry.register(
		BuiltInRegistries.STRUCTURE_PIECE, Burmaldaholic.id("casino_tunnel"),
		(net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType) CasinoTunnelPiece::new);

	/** Lava guard of the Piglin Parlor (lane WG, {@link CasinoLavaGuardPiece}); the last piece of the bastion start. */
	public static final net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType LAVA_GUARD = Registry.register(
		BuiltInRegistries.STRUCTURE_PIECE, Burmaldaholic.id("casino_lava_guard"),
		(net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType) CasinoLavaGuardPiece::new);

	/** Called once from {@code WorldgenModule.register} (mod init, before registries freeze). */
	public static void init() {
		// class loading registers the type
	}
}
