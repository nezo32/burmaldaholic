package dev.nezo.burmaldaholic.core.fx;

import dev.nezo.burmaldaholic.core.module.ModuleContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Core-owned casino particle types (global.md §5.1; docs/architecture/animation.md §2.3): the shared set every
 * module may spawn ({@code ServerLevel.sendParticles}, ≤ 3 calls per event) or add client-side. Module-owned
 * types ({@code slots} ember/void, {@code extras} foil, {@code craps} dice dust) are registered by their modules.
 * Client providers: {@code client.fx.CasinoParticle} (budgets); sprites: {@code assets/burmaldaholic/particles/<id>.json}
 * (vanilla placeholder sprites until the X-L0 generator writes {@code textures/particle/burmaldaholic/<id>_<n>.png}).
 */
public final class CoreParticles {
	/** Ids in registration order. */
	public static final List<String> IDS = List.of("chip_pop", "chip_glint", "sparkle", "gold_burst", "golden_mote", "diamond_glint",
		"curse_wisp", "summon_rune", "teleport_ring", "collector_smoke", "coin_burst", "confetti", "jackpot_burst");

	private static final Map<String, SimpleParticleType> TYPES = new LinkedHashMap<>();

	private CoreParticles() {}

	static void register(ModuleContext ctx) {
		for (String id : IDS) {
			if (TYPES.containsKey(id)) continue;
			ctx.checkOwned(id);
			TYPES.put(id, Registry.register(BuiltInRegistries.PARTICLE_TYPE, ctx.id(id), FabricParticleTypes.simple()));
		}
	}

	/** The registered type, or {@code null} before core init. */
	public static SimpleParticleType get(String id) {
		return TYPES.get(id);
	}

	public static Map<String, SimpleParticleType> all() {
		return java.util.Collections.unmodifiableMap(TYPES);
	}
}
