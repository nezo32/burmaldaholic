package dev.nezo.burmaldaholic.worldgen;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.worldgen.logic.Layout;
import dev.nezo.burmaldaholic.worldgen.logic.Layouts;
import dev.nezo.burmaldaholic.worldgen.mixin.SinglePoolElementAccessor;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

/**
 * Casino template-pool elements. The pools ({@code burmaldaholic:worldgen/<layout>}) live in the
 * built-in data pack {@code burmaldaholic:casinos}; when that pack is disabled they are absent and no
 * casino is generated (GAME_DESIGN §2.1 Java).
 */
public final class CasinoElements {
	private CasinoElements() {}

	public static ResourceKey<StructureTemplatePool> poolKey(Layout layout) {
		return ResourceKey.create(Registries.TEMPLATE_POOL, Burmaldaholic.id(layout.templatePath()));
	}

	/** The single element of the layout's pool, if the data pack is loaded. */
	public static Optional<StructurePoolElement> element(HolderGetter<StructureTemplatePool> pools, Layout layout) {
		return pools.get(poolKey(layout)).flatMap(holder -> {
			List<com.mojang.datafixers.util.Pair<StructurePoolElement, Integer>> templates = holder.value().getTemplates();
			return templates.isEmpty() ? Optional.empty() : Optional.of(templates.getFirst().getFirst());
		});
	}

	/** The casino layout an element places, if it is one of ours. */
	public static Optional<Layout> layoutOf(StructurePoolElement element) {
		if (!(element instanceof SinglePoolElement single)) {
			return Optional.empty();
		}
		Optional<Identifier> location = ((SinglePoolElementAccessor) single).burmaldaholic$template().left();
		if (location.isEmpty() || !location.get().getNamespace().equals(Burmaldaholic.MOD_ID)) {
			return Optional.empty();
		}
		String path = location.get().getPath();
		return path.startsWith("worldgen/") ? Layouts.byId(path.substring("worldgen/".length())) : Optional.empty();
	}
}
