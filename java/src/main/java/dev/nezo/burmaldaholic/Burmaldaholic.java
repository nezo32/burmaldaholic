package dev.nezo.burmaldaholic;

import dev.nezo.burmaldaholic.core.module.ModuleLoader;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common (both sides) entrypoint. Owned by core — feature developers never edit this file;
 * they put their code in their module's {@code register} method (see {@link ModuleList}).
 */
public final class Burmaldaholic implements ModInitializer {
	public static final String MOD_ID = "burmaldaholic";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModuleLoader.loadCommon(ModuleList.create());
	}
}
