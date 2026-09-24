package dev.nezo.burmaldaholic.games.slots.client.fx;

import dev.nezo.burmaldaholic.client.fx.GuiParticlePool;
import dev.nezo.burmaldaholic.core.anim.SeedMix;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * GUI particles of the slot screen on the shared {@link GuiParticlePool} (≤ 96 alive, newest dropped; global §2.9):
 * coins, sparkles, confetti, embers, void motes, stars and smoke, drawn as small pixel shapes (the generated
 * particle sprites replace them later). Cosmetic randomness is seeded ({@link SeedMix.FxRng}) — never game RNG.
 */
public final class ScreenParticles {
	public static final int COIN = 0;
	public static final int SPARKLE = 1;
	public static final int CONFETTI = 2;
	public static final int EMBER = 3;
	public static final int MOTE = 4;
	public static final int STAR = 5;
	public static final int SMOKE = 6;

	private static final int[] CONFETTI_COLORS = {0xFFFF5A4A, 0xFFFFD640, 0xFF40D060, 0xFF4080FF, 0xFFD696FF, 0xFFFF9020};

	private final GuiParticlePool pool = new GuiParticlePool();
	private SeedMix.FxRng rng = new SeedMix.FxRng(1);
	private long lastStep = -1;
	private boolean enabled = true;

	public void seed(int seed) {
		rng = new SeedMix.FxRng(seed);
	}

	/** Reduce motion: no particles at all. */
	public void enabled(boolean on) {
		enabled = on;
		if (!on) pool.clear();
	}

	public void clear() {
		pool.clear();
	}

	public int count() {
		return pool.count();
	}

	/** Radial burst of {@code n} particles of {@code kind} from (x, y); speed in px/ms. */
	public void burst(int kind, float x, float y, int n, float speed, float gravity, int lifeMs, long now) {
		if (!enabled) return;
		for (int i = 0; i < n; i++) {
			double a = rng.nextDouble() * Math.PI * 2;
			double s = speed * (0.4 + 0.6 * rng.nextDouble());
			int variant = rng.nextInt(6);
			pool.spawn(kind * 16 + variant, x, y, (float) (Math.cos(a) * s), (float) (Math.sin(a) * s - speed * 0.5), gravity, now,
				(int) (lifeMs * (0.7 + 0.3 * rng.nextDouble())));
		}
	}

	/** Fountain upwards (coins): spread horizontally, strong up velocity, gravity. */
	public void fountain(int kind, float x, float y, float width, int n, long now) {
		if (!enabled) return;
		for (int i = 0; i < n; i++) {
			float px = x + (float) ((rng.nextDouble() - 0.5) * width);
			float vx = (float) ((rng.nextDouble() - 0.5) * 0.12);
			float vy = (float) (-0.18 - rng.nextDouble() * 0.14);
			pool.spawn(kind * 16 + rng.nextInt(6), px, y, vx, vy, 0.00045f, now, 1400 + rng.nextInt(500));
		}
	}

	/** Rain from the top edge (confetti). */
	public void rain(int kind, float x0, float x1, float y, int n, long now) {
		if (!enabled) return;
		for (int i = 0; i < n; i++) {
			float px = (float) (x0 + rng.nextDouble() * (x1 - x0));
			pool.spawn(kind * 16 + rng.nextInt(6), px, y - rng.nextInt(20), (float) ((rng.nextDouble() - 0.5) * 0.03), 0.03f + (float) rng.nextDouble() * 0.04f,
				0.00002f, now, 2200 + rng.nextInt(800));
		}
	}

	/** Slow rise (embers, motes). */
	public void rise(int kind, float x, float y, float spread, int n, long now) {
		if (!enabled) return;
		for (int i = 0; i < n; i++) {
			float px = x + (float) ((rng.nextDouble() - 0.5) * spread);
			pool.spawn(kind * 16 + rng.nextInt(6), px, y, (float) ((rng.nextDouble() - 0.5) * 0.02), -0.02f - (float) rng.nextDouble() * 0.03f, 0,
				now, 700 + rng.nextInt(600));
		}
	}

	public void draw(GuiGraphicsExtractor g, long now) {
		float dt = lastStep < 0 ? 0 : Math.min(50, now - lastStep);
		lastStep = now;
		pool.step(now, dt);
		for (int i = 0; i < pool.count(); i++) {
			int id = pool.sprite(i);
			int kind = id / 16;
			int v = id % 16;
			int x = Math.round(pool.x(i));
			int y = Math.round(pool.y(i));
			float age = pool.age(i, now);
			double fade = age < 0.7 ? 1 : 1 - (age - 0.7) / 0.3;
			switch (kind) {
				case COIN -> {
					// spinning coin: width oscillates 1..4 px
					int w = 1 + (int) Math.round(3 * Math.abs(Math.cos(now / 90.0 + v)));
					g.fill(x - w / 2, y - 2, x - w / 2 + w, y + 2, SlotDraw.alpha(0xFFFFC400, fade));
					g.fill(x - w / 2, y - 2, x - w / 2 + Math.max(1, w / 2), y - 1, SlotDraw.alpha(0xFFFFF0A0, fade));
				}
				case SPARKLE, STAR -> {
					int c = kind == STAR ? 0xFFFFF4C0 : (v % 2 == 0 ? 0xFFFFD640 : 0xFFD696FF);
					int r = kind == STAR ? 3 : 2;
					double pulse = 0.5 + 0.5 * Math.sin(age * Math.PI);
					int a = (int) Math.round(r * pulse) + 1;
					g.fill(x - a, y, x + a + 1, y + 1, SlotDraw.alpha(c, fade));
					g.fill(x, y - a, x + 1, y + a + 1, SlotDraw.alpha(c, fade));
				}
				case CONFETTI -> {
					int c = CONFETTI_COLORS[v % CONFETTI_COLORS.length];
					boolean flip = ((now / 120) + v) % 2 == 0;
					g.fill(x, y, x + (flip ? 3 : 1), y + (flip ? 1 : 3), SlotDraw.alpha(c, fade));
				}
				case EMBER -> g.fill(x, y, x + 2, y + 2, SlotDraw.alpha(SlotDraw.lerp(0xFFFFB030, 0xFFC02010, age), fade));
				case MOTE -> g.fill(x, y, x + 2, y + 2, SlotDraw.alpha(v % 2 == 0 ? 0xFFB040FF : 0xFFD696FF, fade * 0.9));
				case SMOKE -> {
					int r = 2 + (int) (age * 4);
					g.fill(x - r, y - r, x + r, y + r, SlotDraw.alpha(0xFFB8B8B8, fade * 0.5));
				}
				default -> g.fill(x, y, x + 1, y + 1, 0xFFFFFFFF);
			}
		}
	}
}
