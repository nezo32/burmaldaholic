package dev.nezo.burmaldaholic.client.fx;

/**
 * Pooled GUI particles (chips, coins, confetti, sparkles; global.md §2.9: ≤ 96 live, drop newest beyond).
 * Struct-of-arrays, no allocation per frame. Positions in GUI px, time in ms. SKELETON: simulation is
 * complete; drawing by sprite id is lane J-L1's (sprites come from the generated GUI atlas).
 */
public final class GuiParticlePool {
	public static final int CAPACITY = 96;

	private final float[] x = new float[CAPACITY];
	private final float[] y = new float[CAPACITY];
	private final float[] vx = new float[CAPACITY];
	private final float[] vy = new float[CAPACITY];
	private final float[] gravity = new float[CAPACITY];
	private final int[] sprite = new int[CAPACITY];
	private final long[] born = new long[CAPACITY];
	private final int[] life = new int[CAPACITY];
	private int count;

	/** Adds a particle; returns false when the pool is full (the newest is dropped). */
	public boolean spawn(int spriteId, float px, float py, float pvx, float pvy, float g, long nowMs, int lifeMs) {
		if (count >= CAPACITY) return false;
		int i = count++;
		sprite[i] = spriteId;
		x[i] = px;
		y[i] = py;
		vx[i] = pvx;
		vy[i] = pvy;
		gravity[i] = g;
		born[i] = nowMs;
		life[i] = lifeMs;
		return true;
	}

	/** Integrates to {@code nowMs} with step {@code dtMs} and removes expired particles (swap-remove). */
	public void step(long nowMs, float dtMs) {
		for (int i = 0; i < count; ) {
			if (nowMs - born[i] >= life[i]) {
				int last = --count;
				x[i] = x[last];
				y[i] = y[last];
				vx[i] = vx[last];
				vy[i] = vy[last];
				gravity[i] = gravity[last];
				sprite[i] = sprite[last];
				born[i] = born[last];
				life[i] = life[last];
				continue;
			}
			vy[i] += gravity[i] * dtMs;
			x[i] += vx[i] * dtMs;
			y[i] += vy[i] * dtMs;
			i++;
		}
	}

	public int count() {
		return count;
	}

	public float x(int i) {
		return x[i];
	}

	public float y(int i) {
		return y[i];
	}

	public int sprite(int i) {
		return sprite[i];
	}

	/** Age in [0, 1] (for alpha fade / flipbook frame). */
	public float age(int i, long nowMs) {
		return Math.min(1f, (nowMs - born[i]) / (float) life[i]);
	}

	public void clear() {
		count = 0;
	}
}
