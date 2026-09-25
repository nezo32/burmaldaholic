package dev.nezo.burmaldaholic.chaos.client;

import dev.nezo.burmaldaholic.chaos.ChaosFxNet;
import dev.nezo.burmaldaholic.chaos.logic.ChaosFxMath;
import dev.nezo.burmaldaholic.client.fx.CasinoParticle;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/**
 * The mob-wave summon rune (global.md §4.6): a ground decal that lies flat where a mob will appear, grows in over
 * 200 ms, pulses through its 4 frames and lives exactly {@link ChaosFxMath#RUNE_LEAD_TICKS} + 6 ticks (it is still on
 * the ground when the mob steps out of it). Full-bright, translucent; shares the casino particle budget.
 */
final class RuneParticle extends SingleQuadParticle {
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final float SIZE = 0.62f;
	private final SpriteSet sprites;
	private int token;

	private RuneParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites, int token) {
		super(level, x, y, z, sprites.first());
		this.sprites = sprites;
		this.token = token;
		this.lifetime = ChaosFxMath.RUNE_LEAD_TICKS + 6;
		this.hasPhysics = false;
		this.gravity = 0;
		this.xd = 0;
		this.yd = 0;
		this.zd = 0;
		this.quadSize = 0.01f;
	}

	static void register() {
		SimpleParticleType type = ChaosFxNet.rune();
		if (type != null) ParticleProviderRegistry.getInstance().register(type, sprites -> new Provider(sprites));
	}

	@Override
	public void tick() {
		super.tick();
		if (removed) return;
		float t = age / 4f;
		quadSize = SIZE * Math.min(1f, t) * (FxSettings.reduceMotion() ? 1f : 1f + 0.06f * (float) Math.sin(age * 0.8));
		setSprite(sprites.get(age % 8 < 4 ? age % 4 : 3 - age % 4, 3));
		int left = lifetime - age;
		setAlpha(left < 4 ? left / 4f : 0.95f);
	}

	@Override
	public FacingCameraMode getFacingCameraMode() {
		// flat on the ground, whatever the camera does
		return (rotation, camera, partialTick) -> rotation.rotationX((float) (-Math.PI / 2));
	}

	@Override
	public void remove() {
		CasinoParticle.release(token);
		token = -1;
		super.remove();
	}

	@Override
	protected Layer getLayer() {
		return Layer.TRANSLUCENT;
	}

	@Override
	protected int getLightCoords(float partialTick) {
		return FULL_BRIGHT;
	}

	private record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
		@Override
		public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz,
				RandomSource random) {
			int token = CasinoParticle.acquire(level);
			return token < 0 ? null : new RuneParticle(level, x, y, z, sprites, token);
		}
	}
}
