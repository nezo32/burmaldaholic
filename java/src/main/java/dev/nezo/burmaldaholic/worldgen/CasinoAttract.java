package dev.nezo.burmaldaholic.worldgen;

import dev.nezo.burmaldaholic.core.module.ModuleContext;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoRecord;
import dev.nezo.burmaldaholic.worldgen.logic.Geometry;
import dev.nezo.burmaldaholic.worldgen.net.CasinoViewPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server half of the casino attract mode (global.md §4.13, lane J-L3): tells each modded client which generated
 * casinos are near (bounds + kind) so it can light their rooflines and float themed motes, and which one the player
 * just entered (arrival flourish). Checked with the entering scan ({@link CasinoTracker}, every second); a payload
 * only when the nearby set changes or on arrival.
 */
public final class CasinoAttract {
	/** Casinos whose bounds come within this many blocks of the player are sent. */
	static final int NEAR = 72;

	private static final Map<UUID, Integer> LAST = new HashMap<>();

	private CasinoAttract() {}

	private static net.minecraft.core.particles.SimpleParticleType bulb;
	private static net.minecraft.core.particles.SimpleParticleType mote;

	static void register(ModuleContext ctx) {
		CasinoViewPayload.TYPE = ctx.payloads().clientbound("worldgen_attract", CasinoViewPayload.CODEC);
		// tinted, full-bright marquee bulb and ambient mote (velocity args carry the colour; client provider)
		bulb = net.minecraft.core.Registry.register(net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE, ctx.id("worldgen/bulb"),
			net.fabricmc.fabric.api.particle.v1.FabricParticleTypes.simple(true));
		mote = net.minecraft.core.Registry.register(net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE, ctx.id("worldgen/mote"),
			net.fabricmc.fabric.api.particle.v1.FabricParticleTypes.simple(true));
	}

	public static net.minecraft.core.particles.SimpleParticleType bulb() {
		return bulb;
	}

	public static net.minecraft.core.particles.SimpleParticleType mote() {
		return mote;
	}

	static void clear() {
		LAST.clear();
	}

	static void forget(UUID player) {
		LAST.remove(player);
	}

	/** Sends the nearby casinos when they changed; {@code arrived} = the casino the player just entered, or null. */
	static void update(ServerPlayer player, CasinoRecord arrived) {
		if (CasinoViewPayload.TYPE == null || !ServerPlayNetworking.canSend(player, CasinoViewPayload.TYPE)) {
			return;
		}
		String dim = player.level().dimension().identifier().toString();
		List<CasinoViewPayload.Casino> near = new ArrayList<>();
		int arrivedIndex = -1;
		for (CasinoRecord c : CasinoIndex.get(player.level().getServer()).casinos()) {
			Geometry.Box b = c.box();
			if (b == null || c.kind() == null || !dim.equals(c.dimension())) {
				continue;
			}
			double dx = Math.max(0, Math.max(b.minX() - player.getX(), player.getX() - b.maxX() - 1));
			double dz = Math.max(0, Math.max(b.minZ() - player.getZ(), player.getZ() - b.maxZ() - 1));
			if (dx * dx + dz * dz > NEAR * NEAR || near.size() >= CasinoViewPayload.MAX) {
				continue;
			}
			if (c == arrived) {
				arrivedIndex = near.size();
			}
			near.add(new CasinoViewPayload.Casino(c.kind().ordinal(), b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()));
		}
		int hash = near.hashCode();
		Integer last = LAST.put(player.getUUID(), hash);
		if (arrivedIndex < 0 && last != null && last == hash) {
			return;
		}
		ServerPlayNetworking.send(player, new CasinoViewPayload(near, arrivedIndex));
	}
}
