package dev.nezo.burmaldaholic.chaos.logic;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Golden Hour state machine and bonus rule (GAME_DESIGN.md §13.3). PURE; times are world game
 * ticks (monotonic). Persisted by the world layer.
 */
public final class GoldenHourState {
	/** Warn this many ticks before the end (30 s). */
	public static final long WARN_TICKS = 600;

	/** Sequential id of the current / last Golden Hour (0 = never). */
	public int id;
	/** Start tick (-1 = never). */
	public long start = -1;
	/** End tick (exclusive). */
	public long end = -1;
	/** A new Golden Hour may start at/after this tick. */
	public long nextAllowed;
	/** "Ending soon" already announced for {@link #id}. */
	public boolean warned;
	/** End already announced for {@link #id}. */
	public boolean ended = true;
	/** Bonus paid per player during {@link #id}. */
	public final Map<UUID, Long> paid = new HashMap<>();

	public boolean isActive(long now) {
		return start >= 0 && now >= start && now < end;
	}

	public long remaining(long now) {
		return isActive(now) ? end - now : 0;
	}

	/** Not active and the cooldown (counted from the end of the previous one) has elapsed. A clock that went backwards resets it. */
	public boolean canStart(long now) {
		if (isActive(now)) {
			return false;
		}
		if (start >= 0 && now < start) {
			return true;
		}
		return now >= nextAllowed;
	}

	public void start(long now, long durationTicks, long cooldownTicks) {
		id++;
		start = now;
		end = now + Math.max(1, durationTicks);
		nextAllowed = end + Math.max(0, cooldownTicks);
		warned = false;
		ended = false;
		paid.clear();
	}

	/** Ends the current Golden Hour now (admin); the cooldown counts from now. */
	public void stop(long now, long cooldownTicks) {
		if (isActive(now)) {
			end = now;
			nextAllowed = now + Math.max(0, cooldownTicks);
		}
	}

	public record Bonus(long amount, boolean capReached) {
		public static final Bonus NONE = new Bonus(0, false);
	}

	/**
	 * Bonus for one settled win: {@code floor(netWin × (m − 1))}, limited by what is left of the
	 * per-player cap. {@code capReached} is true when this payment makes the player hit the cap.
	 */
	public static Bonus bonus(long net, double multiplier, long paidSoFar, long cap) {
		if (net <= 0 || !(multiplier > 1)) {
			return Bonus.NONE;
		}
		long raw = (long) Math.floor(net * (multiplier - 1));
		long left = Math.max(0, cap - Math.max(0, paidSoFar));
		long amount = Math.min(raw, left);
		return new Bonus(amount, amount > 0 && amount == left && raw >= left);
	}

	/** Applies {@link #bonus} for {@code player} and records it; returns the bonus to pay. */
	public Bonus award(UUID player, long net, double multiplier, long cap) {
		Bonus b = bonus(net, multiplier, paid.getOrDefault(player, 0L), cap);
		if (b.amount() > 0) {
			paid.merge(player, b.amount(), Long::sum);
		}
		return b;
	}
}
