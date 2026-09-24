package dev.nezo.burmaldaholic.core.anim;

import java.lang.ref.WeakReference;

/**
 * The live casino world-particle budget (global.md §2.9: ≤ 400 alive, ≤ 60 per burst, ≤ 150 for a JACKPOT; reduce
 * motion × 0.3), shared by every casino particle provider (core and module-owned). PURE so the accounting is
 * unit-tested; the client provider base {@code client.fx.CasinoParticle} owns the instance.
 *
 * <p>Leak-proof accounting (review of lane J-L1): the particle engine drops its particles WITHOUT calling
 * {@code remove()} when the level changes (dimension change, respawn, reconnect), so a plain counter kept the dead
 * particles forever and after a few dimension changes near a casino the budget stayed full (no casino particle
 * ever again). Every acquisition returns a token of the current generation; a new level (or {@link #reset})
 * starts a new generation with a zero count, and releases of an older generation are ignored. Client thread only.
 */
public final class ParticleBudget {
	private final int maxAlive;
	private final int maxBurst;
	private final int maxBurstJackpot;
	private int alive;
	private int generation;
	private WeakReference<Object> level = new WeakReference<>(null);

	public ParticleBudget(int maxAlive, int maxBurst, int maxBurstJackpot) {
		this.maxAlive = maxAlive;
		this.maxBurst = maxBurst;
		this.maxBurstJackpot = maxBurstJackpot;
	}

	/** Starts a new generation when {@code world} is not the level the current count belongs to. */
	public void sync(Object world) {
		if (world != null && level.get() != world) {
			level = new WeakReference<>(world);
			reset();
		}
	}

	/** Takes one slot for a particle in {@code world}; returns its token, or −1 when the budget is spent. */
	public int acquire(Object world) {
		sync(world);
		if (alive >= maxAlive) return -1;
		alive++;
		return generation;
	}

	/** Gives a slot back (a particle was removed); tokens of an older generation are ignored. */
	public void release(int token) {
		if (token >= 0 && token == generation && alive > 0) alive--;
	}

	/** Forgets every live particle (level change, disconnect). */
	public void reset() {
		generation = (generation + 1) & Integer.MAX_VALUE;
		alive = 0;
	}

	public int alive() {
		return alive;
	}

	/** How many of {@code wanted} particles a burst may add now (per-burst cap, reduce motion, then the live budget). */
	public int allowance(Object world, int wanted, boolean jackpot, boolean reduceMotion) {
		sync(world);
		int cap = Math.min(Math.max(0, wanted), jackpot ? maxBurstJackpot : maxBurst);
		if (reduceMotion) cap = (int) Math.ceil(cap * 0.3);
		return Math.max(0, Math.min(cap, maxAlive - alive));
	}
}
