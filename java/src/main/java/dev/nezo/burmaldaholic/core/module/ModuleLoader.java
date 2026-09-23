package dev.nezo.burmaldaholic.core.module;

import dev.nezo.burmaldaholic.Burmaldaholic;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Validates and runs the modules from {@link dev.nezo.burmaldaholic.ModuleList}. */
public final class ModuleLoader {
	public static final Pattern MODULE_ID = Pattern.compile("[a-z0-9]+");

	private ModuleLoader() {}

	/** Pure validation, used by unit tests too. */
	public static void validate(List<String> ids) {
		Set<String> seen = new HashSet<>();
		for (String id : ids) {
			if (!MODULE_ID.matcher(id).matches()) {
				throw new IllegalStateException("Bad module id '" + id + "': must match " + MODULE_ID);
			}
			if (!seen.add(id)) {
				throw new IllegalStateException("Duplicate module id '" + id + "'");
			}
		}
		if (ids.isEmpty() || !ids.getFirst().equals("core")) {
			throw new IllegalStateException("CoreModule must be first in ModuleList");
		}
	}

	public static void loadCommon(List<CasinoModule> modules) {
		validate(modules.stream().map(CasinoModule::id).toList());
		for (CasinoModule module : modules) {
			if (!Namespaces.get().knows(module.id())) {
				throw new IllegalStateException("Module '" + module.id() + "' missing from java/config/namespaces.properties");
			}
		}
		for (CasinoModule module : modules) {
			long start = System.nanoTime();
			try {
				module.register(new ModuleContext(module.id()));
			} catch (RuntimeException e) {
				throw new IllegalStateException("Module '" + module.id() + "' failed to register", e);
			}
			Burmaldaholic.LOGGER.debug("Registered module {} in {} ms", module.id(), (System.nanoTime() - start) / 1_000_000);
		}
		Burmaldaholic.LOGGER.info("Burmaldaholic loaded {} modules", modules.size());
	}
}
