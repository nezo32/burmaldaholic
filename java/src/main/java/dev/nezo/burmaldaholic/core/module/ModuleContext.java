package dev.nezo.burmaldaholic.core.module;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.config.ConfigHandle;
import dev.nezo.burmaldaholic.core.config.ConfigManager;
import dev.nezo.burmaldaholic.core.network.Payloads;
import dev.nezo.burmaldaholic.core.registry.ModRegistrar;
import dev.nezo.burmaldaholic.core.table.TableRegistrar;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;

/**
 * Everything a module needs to register content. All ids created through the context are
 * validated to live in the module's namespace ({@code burmaldaholic:<moduleId>_...}), so
 * two developers can never collide.
 */
public final class ModuleContext {
	private final String moduleId;
	private final ModRegistrar registrar;
	private final TableRegistrar tables;
	private final Payloads payloads;

	public ModuleContext(String moduleId) {
		this.moduleId = moduleId;
		this.registrar = new ModRegistrar(this);
		this.tables = new TableRegistrar(this);
		this.payloads = new Payloads(this);
	}

	public String moduleId() {
		return moduleId;
	}

	/**
	 * Returns {@code burmaldaholic:<path>} after checking that {@code path} is owned by this module
	 * (module id or one of its namespaces in java/config/namespaces.properties, followed by
	 * {@code _}, {@code .}, {@code /} or nothing).
	 */
	public Identifier id(String path) {
		checkOwned(path);
		return Burmaldaholic.id(path);
	}

	public void checkOwned(String path) {
		if (!Namespaces.get().owns(moduleId, path)) {
			throw new IllegalArgumentException("Module '" + moduleId + "' does not own the name '" + path
				+ "'. Use your module id as prefix or ask core to add a namespace in java/config/namespaces.properties");
		}
	}

	/** Translation key helper: {@code key("gui", "title")} -> {@code gui.burmaldaholic.<module>.title}. */
	public String key(String category, String suffix) {
		return category + "." + Burmaldaholic.MOD_ID + "." + moduleId + "." + suffix;
	}

	/** Blocks, items, block entities, menus, sounds, creative tab entries. */
	public ModRegistrar registry() {
		return registrar;
	}

	/** Casino table blocks (block + item + block entity + menu in one call). */
	public TableRegistrar tables() {
		return tables;
	}

	/** Custom network payloads (ids are namespaced to the module). */
	public Payloads payloads() {
		return payloads;
	}

	/**
	 * An EXTRA config section for module-private settings that are not in docs/design/CONFIG.md.
	 * Every CONFIG.md key already exists and is read with {@code CasinoConfig.<section>()} (registered
	 * by core, so {@code ctx.config("blackjack", ...)} would clash). The section must be owned by this
	 * module (namespaces.properties), e.g. {@code "blackjack_debug"}. {@code type} is a plain class with
	 * public mutable fields (nested classes allowed, {@code @Range} for clamping) and a no-arg constructor.
	 */
	public <T> ConfigHandle<T> config(String section, Class<T> type, Supplier<T> defaults) {
		checkOwned(section);
		return ConfigManager.get().register(section, type, defaults);
	}
}
