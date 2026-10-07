package dev.nezo.burmaldaholic.worldgen.structure;

import dev.nezo.burmaldaholic.Burmaldaholic;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.structure.StructureType;

/** Registration of the structure type {@code burmaldaholic:casino} (architecture doc §6.1). */
public final class CasinoStructureTypes {
	private CasinoStructureTypes() {}

	public static final StructureType<CasinoStructure> CASINO = Registry.register(BuiltInRegistries.STRUCTURE_TYPE, Burmaldaholic.id("casino"),
		() -> CasinoStructure.CODEC);

	/** Called once from {@code WorldgenModule.register} (mod init, before registries freeze). */
	public static void init() {
		// class loading registers the type
	}
}
