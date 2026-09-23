package dev.nezo.burmaldaholic.core.config;

/** Static access to the core config section (registered by CoreModule). */
public final class CoreConfigs {
	private static ConfigHandle<CoreConfig> core;

	private CoreConfigs() {}

	public static void bind(ConfigHandle<CoreConfig> handle) {
		core = handle;
	}

	public static CoreConfig core() {
		return core == null ? new CoreConfig() : core.get();
	}
}
