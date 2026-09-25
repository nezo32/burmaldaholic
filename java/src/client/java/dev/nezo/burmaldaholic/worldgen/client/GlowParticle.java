package dev.nezo.burmaldaholic.worldgen.client;

import dev.nezo.burmaldaholic.client.fx.CasinoParticle;
import dev.nezo.burmaldaholic.worldgen.CasinoAttract;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/**
 * Attract-mode glow particles (global.md §4.13, lane J-L3): {@code worldgen/bulb} — a marquee bulb that lights for
 * half a second in place; {@code worldgen/mote} — a themed mote drifting up for two seconds. Both are white sprites
 * tinted by the spawner: the three velocity arguments carry the colour (r, g, b in 0–1), the particles never move by
 * them. Full-bright, translucent, under the casino particle budget.
 */
final class GlowParticle extends SingleQuadParticle {
	private static final int FULL_BRIGHT = 0xF000F0;
	private final SpriteSet sprites;
	private final boolean mote;
	private int token;

	private GlowParticle(ClientLevel level, double x, double y, double z, float r, float g, float b, SpriteSet sprites, boolean mote,
			RandomSource random, int token) {
		super(level, x, y, z, sprites.first());
		this.sprites = sprites;
		this.mote = mote;
		this.token = token;
		this.hasPhysics = false;
		this.gravity = 0;
		this.friction = 0.96f;
		this.xd = 0;
		this.zd = 0;
		this.yd = mote ? 0.012 + random.nextFloat() * 0.01 : 0;
		this.lifetime = mote ? 36 + random.nextInt(14) : 10;
		this.quadSize = mote ? 0.05f + random.nextFloat() * 0.03f : 0.2f;
		setColor(r, g, b);
		setAlpha(mote ? 0f : 1f);
		setSpriteFromAge(sprites);
	}

	static void register() {
		SimpleParticleType bulb = CasinoAttract.bulb();
		SimpleParticleType mote = CasinoAttract.mote();
		if (bulb != null) ParticleProviderRegistry.getInstance().register(bulb, sprites -> new Provider(sprites, false));
		if (mote != null) ParticleProviderRegistry.getInstance().register(mote, sprites -> new Provider(sprites, true));
	}

	@Override
	public void tick() {
		super.tick();
		if (removed) return;
		setSpriteFromAge(sprites);
		float u = age / (float) lifetime;
		if (mote) {
			setAlpha(Math.min(1f, u * 4f) * (1f - u) * 0.9f);
			this.xd += Math.sin((age + x * 5) * 0.25) * 0.001;
		} else {
			setAlpha(u < 0.7f ? 1f : (1f - u) / 0.3f);
		}
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

	private record Provider(SpriteSet sprites, boolean mote) implements ParticleProvider<SimpleParticleType> {
		@Override
		public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz,
				RandomSource random) {
			int token = CasinoParticle.acquire(level);
			return token < 0 ? null : new GlowParticle(level, x, y, z, (float) dx, (float) dy, (float) dz, sprites, mote, random, token);
		}
	}
}
