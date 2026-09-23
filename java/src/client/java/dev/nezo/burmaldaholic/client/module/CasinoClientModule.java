package dev.nezo.burmaldaholic.client.module;

/** Client half of a feature module. Same {@link #id()} as its common module. */
public interface CasinoClientModule {
	String id();

	void registerClient(ClientModuleContext ctx);
}
