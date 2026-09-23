package dev.nezo.burmaldaholic.core.module;

/**
 * Contract every feature package implements exactly once ({@code <Feature>Module}).
 *
 * <ul>
 *   <li>{@link #id()} is the module id: lower-case, {@code [a-z0-9]+}. It is the prefix of every
 *       registry id, lang key, asset file, sound event and config section the module owns.</li>
 *   <li>{@link #register(ModuleContext)} runs once during mod init on BOTH physical sides.
 *       Register blocks/items/payloads/events here. Do NOT touch the world or config values here
 *       (config is loaded, but the server does not exist yet).</li>
 *   <li>Constructors must be trivial (no registry access) — unit tests instantiate the list.</li>
 *   <li>All gameplay must be guarded by {@code CasinoMode.isEnabled(...)}.</li>
 * </ul>
 */
public interface CasinoModule {
	String id();

	void register(ModuleContext ctx);
}
