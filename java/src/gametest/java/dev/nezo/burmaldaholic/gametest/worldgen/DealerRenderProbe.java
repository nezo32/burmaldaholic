package dev.nezo.burmaldaholic.gametest.worldgen;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Counts real-frame house dealer render-state extractions per entity type id (fed by HouseDealerRenderProbeMixin). */
public final class DealerRenderProbe {
	public static final Map<String, AtomicInteger> CALLS = new ConcurrentHashMap<>();

	private DealerRenderProbe() {}

	public static void hit(String typeId) {
		CALLS.computeIfAbsent(typeId, k -> new AtomicInteger()).incrementAndGet();
	}

	public static int count(String typeId) {
		AtomicInteger n = CALLS.get(typeId);
		return n == null ? 0 : n.get();
	}
}
