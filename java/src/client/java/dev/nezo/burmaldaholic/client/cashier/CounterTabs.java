package dev.nezo.burmaldaholic.client.cashier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Client registry of counter tabs: id → factory. The server decides which tabs a source shows
 * ({@code CounterSessions.tabsFor}); the cashier block asks for {@link #CASHIER_TABS}. EC-2 registers
 * {@code CounterTabs.register("comps", CompsTab::new)} in its client module.
 */
public final class CounterTabs {
	/** Tabs of the cashier block screen, in order (missing factories are skipped). */
	public static final List<String> CASHIER_TABS = List.of("exchange", "store", "comps");

	private static final Map<String, Function<CounterShell, CounterTab>> FACTORIES = new ConcurrentHashMap<>();

	private CounterTabs() {}

	public static void register(String id, Function<CounterShell, CounterTab> factory) {
		FACTORIES.put(id, factory);
	}

	/** New tab instances for {@code ids} (unknown ids skipped). */
	public static List<CounterTab> create(List<String> ids, CounterShell shell) {
		List<CounterTab> out = new ArrayList<>();
		for (String id : ids) {
			Function<CounterShell, CounterTab> f = FACTORIES.get(id);
			if (f != null) out.add(f.apply(shell));
		}
		return out;
	}
}
