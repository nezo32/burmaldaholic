package dev.nezo.burmaldaholic.client.fx;

import dev.nezo.burmaldaholic.core.fx.CoreParticles;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SimpleAnimatedParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/**
 * Shared provider base of every casino world particle (docs/architecture/animation.md §2.3) with the global
 * budget (global.md §2.9): ≤ {@link #MAX_ALIVE} casino particles alive in total — the provider returns
 * {@code null} (no particle) beyond it. Per-burst limits (≤ 60, JACKPOT ≤ 150) are enforced by
 * {@link #burst}. Module-owned particle types (slots, extras, craps) register their providers through
 * {@link #register} with their own {@link Spec} so they share the same budget.
 */
public class CasinoParticle extends SimpleAnimatedParticle {
	public static final int MAX_ALIVE = 400;
	public static final int MAX_BURST = 60;
	public static final int MAX_BURST_JACKPOT = 150;

	private static final AtomicInteger ALIVE = new AtomicInteger();

	/**
	 * Look and motion of one particle type.
	 *
	 * @param colors   ARGB tints (one picked at random; 0xFFFFFFFF = sprite colours)
	 * @param gravity  vanilla gravity (negative rises)
	 * @param lifeMin  min lifetime, ticks
	 * @param lifeMax  max lifetime, ticks
	 * @param size     quad size multiplier (1 = vanilla 0.1–0.2 blocks)
	 * @param friction velocity damping per tick
	 */
	public record Spec(int[] colors, float gravity, int lifeMin, int lifeMax, float size, float friction) {}

	/** Core set (global.md §5.1 + slots/extras shared ones). Colours are palette tokens. */
	public static final Map<String, Spec> CORE = Map.ofEntries(
		Map.entry("chip_pop", new Spec(new int[] {0xFFD83440, 0xFFF4ECF8, 0xFF3CB44B, 0xFF783CBE}, 0.6f, 14, 20, 1.2f, 0.94f)),
		Map.entry("chip_glint", new Spec(new int[] {0xFFFFD640}, 0f, 8, 12, 0.8f, 0.8f)),
		Map.entry("sparkle", new Spec(new int[] {0xFFD696FF, 0xFFF4ECF8}, 0f, 12, 18, 0.9f, 0.85f)),
		Map.entry("gold_burst", new Spec(new int[] {0xFFFFD640, 0xFFB07010}, 0.1f, 16, 24, 1f, 0.9f)),
		Map.entry("golden_mote", new Spec(new int[] {0xFFFFD640}, -0.01f, 40, 60, 0.7f, 0.96f)),
		Map.entry("diamond_glint", new Spec(new int[] {0xFF5CE8E0, 0xFFE8F4FF}, 0f, 8, 12, 0.8f, 0.8f)),
		Map.entry("curse_wisp", new Spec(new int[] {0xFF6FA86A, 0xFF3A1450}, -0.02f, 26, 34, 1f, 0.95f)),
		Map.entry("summon_rune", new Spec(new int[] {0xFFBE5AFF}, 0f, 26, 34, 1.2f, 0.9f)),
		Map.entry("teleport_ring", new Spec(new int[] {0xFFD696FF}, 0f, 14, 18, 1.1f, 0.85f)),
		Map.entry("collector_smoke", new Spec(new int[] {0xFF5A4A58, 0xFF3A2A40}, -0.03f, 30, 40, 1.6f, 0.96f)),
		Map.entry("coin_burst", new Spec(new int[] {0xFFFFD640, 0xFFFFE680}, 0.8f, 20, 30, 1f, 0.95f)),
		Map.entry("confetti", new Spec(new int[] {0xFFFFD640, 0xFF80FF40, 0xFFD83440, 0xFFBE5AFF, 0xFF5CE8E0}, 0.3f, 40, 60, 0.8f, 0.97f)),
		Map.entry("jackpot_burst", new Spec(new int[] {0xFFFFD640, 0xFFF4ECF8}, 0.4f, 26, 34, 1.3f, 0.93f)));

	private boolean counted = true;

	protected CasinoParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites, Spec spec,
			RandomSource random) {
		super(level, x, y, z, sprites, spec.gravity());
		ALIVE.incrementAndGet();
		this.xd = dx;
		this.yd = dy;
		this.zd = dz;
		this.friction = spec.friction();
		this.lifetime = spec.lifeMin() + (spec.lifeMax() > spec.lifeMin() ? random.nextInt(spec.lifeMax() - spec.lifeMin() + 1) : 0);
		this.quadSize *= spec.size();
		int c = spec.colors()[random.nextInt(spec.colors().length)];
		setColor(c & 0xFFFFFF);
		setFadeColor(c & 0xFFFFFF);
		setSpriteFromAge(sprites);
		this.hasPhysics = false;
	}

	@Override
	public void remove() {
		if (counted) {
			counted = false;
			ALIVE.decrementAndGet();
		}
		super.remove();
	}

	/** Casino particles currently alive (approximate across level changes; see {@link #resetBudget}). */
	public static int alive() {
		return Math.max(0, ALIVE.get());
	}

	/** Level change / disconnect: the engine drops particles without {@link #remove}, so the count restarts. */
	public static void resetBudget() {
		ALIVE.set(0);
	}

	/** How many of {@code wanted} particles a burst may add now (per-burst cap, then the global budget). */
	public static int burstAllowance(int wanted, boolean jackpot) {
		int cap = Math.min(wanted, jackpot ? MAX_BURST_JACKPOT : MAX_BURST);
		if (FxSettings.reduceMotion()) cap = (int) Math.ceil(cap * 0.3);
		return Math.max(0, Math.min(cap, MAX_ALIVE - alive()));
	}

	/**
	 * Spawns a client-side burst of {@code count} particles of {@code type} at a point, spread by {@code speed}
	 * (blocks/tick) with an upward bias; returns how many were added (budgets applied). {@code seed} makes the
	 * scatter deterministic (public values only).
	 */
	public static int burst(ClientLevel level, SimpleParticleType type, double x, double y, double z, int count, double speed, boolean jackpot,
			int seed) {
		if (level == null || type == null) return 0;
		int n = burstAllowance(count, jackpot);
		RandomSource r = RandomSource.create(seed);
		for (int i = 0; i < n; i++) {
			double a = r.nextDouble() * Math.PI * 2;
			double h = r.nextDouble() * speed;
			level.addParticle(type, x, y, z, Math.cos(a) * h, speed * (0.6 + r.nextDouble()), Math.sin(a) * h);
		}
		return n;
	}

	/** Registers a provider for {@code type} with {@code spec} under the shared budget. */
	public static void register(SimpleParticleType type, Spec spec) {
		ParticleProviderRegistry.getInstance().register(type, sprites -> new Provider(sprites, spec));
	}

	/** Registers the core particle types (called by {@link ClientFx#init}). */
	static void registerCore() {
		for (Map.Entry<String, SimpleParticleType> e : CoreParticles.all().entrySet()) {
			Spec spec = CORE.get(e.getKey());
			if (spec != null) register(e.getValue(), spec);
		}
	}

	/** Provider with the budget check. */
	public record Provider(SpriteSet sprites, Spec spec) implements ParticleProvider<SimpleParticleType> {
		@Override
		public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz,
				RandomSource random) {
			if (ALIVE.get() >= MAX_ALIVE) return null;
			return new CasinoParticle(level, x, y, z, dx, dy, dz, sprites, spec, random);
		}
	}
}
