package dev.nezo.burmaldaholic.chaos;

import dev.nezo.burmaldaholic.chaos.logic.ChaosEvent;
import dev.nezo.burmaldaholic.chaos.net.ChaosFxPayload;
import dev.nezo.burmaldaholic.core.anim.SeedMix;
import dev.nezo.burmaldaholic.core.fx.ServerFx;
import dev.nezo.burmaldaholic.core.module.ModuleContext;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Server side of the chaos presentation (global.md §4.6, lane J-L3): the event intro through the shared {@code fx}
 * channel ({@code ServerFx.event(CHAOS)}: kind tone, sound, chaos card, shake / vignette on the client) and the world
 * decoration at real spots ({@link ChaosFxPayload}) for the target and spectators within 16 blocks. Players whose
 * client lacks the channel get the {@code fallback} (the vanilla particles / sounds of before).
 */
public final class ChaosFxNet {
	/** Spectator radius of chaos decoration (global §4.6). */
	static final double SPECTATORS = 16;

	private ChaosFxNet() {}

	/** Flat summon-rune decal (mob wave, global §4.6): lies on the ground where a mob will appear. */
	private static net.minecraft.core.particles.SimpleParticleType rune;

	static void register(ModuleContext ctx) {
		ChaosFxPayload.TYPE = ctx.payloads().clientbound("chaos_fx", ChaosFxPayload.CODEC);
		rune = net.minecraft.core.Registry.register(net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE, ctx.id("chaos/rune"),
			net.fabricmc.fabric.api.particle.v1.FabricParticleTypes.simple(true));
	}

	/** The rune particle type (client provider: {@code chaos.client.ChaosParticles}), or null before init. */
	public static net.minecraft.core.particles.SimpleParticleType rune() {
		return rune;
	}

	/** True when this player's client draws the chaos FX itself. */
	static boolean modded(ServerPlayer p) {
		return ChaosFxPayload.TYPE != null && ServerPlayNetworking.canSend(p, ChaosFxPayload.TYPE);
	}

	/** The intro of {@code event} for its target (tone sound, card, shake / vignette / veil). */
	static void intro(ServerPlayer p, ChaosEvent event) {
		ServerFx.get().event(p, ServerFx.Kind.CHAOS, "chaos." + event.id(), event.ordinal());
	}

	/**
	 * Sends decoration {@code fx} at {@code points} to every player within {@link #SPECTATORS} of {@code center} (and
	 * always to {@code target}); players without the channel get {@code fallback} instead (may be null).
	 */
	static void points(ServerLevel level, ServerPlayer target, Vec3 center, String fx, List<Vec3> points, int arg,
			Consumer<ServerPlayer> fallback) {
		if (points.isEmpty()) return;
		int seed = SeedMix.mix(SeedMix.hash(fx), (int) level.getGameTime(), (int) Math.floor(center.x), (int) Math.floor(center.z));
		ChaosFxPayload payload = new ChaosFxPayload(fx, points, arg, seed);
		double r2 = SPECTATORS * SPECTATORS;
		for (ServerPlayer p : level.players()) {
			if (p != target && p.distanceToSqr(center) > r2) continue;
			if (modded(p)) ServerPlayNetworking.send(p, payload);
			else if (fallback != null) fallback.accept(p);
		}
		if (target != null && target.level() != level) {
			if (modded(target)) ServerPlayNetworking.send(target, payload);
			else if (fallback != null) fallback.accept(target);
		}
	}
}
