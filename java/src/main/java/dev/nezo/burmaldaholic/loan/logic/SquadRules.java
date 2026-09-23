package dev.nezo.burmaldaholic.loan.logic;

import dev.nezo.burmaldaholic.loan.logic.LoanRecord.Status;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Debt Collector squads (GAME_DESIGN.md §5.5): composition by owed amount, difficulty and wave,
 * per-unit stats, the squad behavior state machine, the wave schedule and Repossession. PURE.
 *
 * <pre>
 * APPROACH (non-hostile, approachTicks max) ── leader within 6 blocks ──▶ NEGOTIATE
 *   NEGOTIATE ── pay in full ──▶ LEAVING (paid) | pay ≥ share ──▶ LEAVING (partial)
 *             ── refuse / timeout / squad attacked ──▶ HOSTILE
 *   APPROACH timeout / squad attacked ──▶ HOSTILE
 * HOSTILE ── hostileTicks / debtor died ──▶ LEAVING;  far (&gt; 96 blocks for 200 t),
 *            dimension change, debtor offline ──▶ despawn now
 * LEAVING ── 100 t ──▶ despawn.  Debt reaches 0 at any time ──▶ LEAVING (paid).
 * Every member dead ──▶ DONE (defeated).
 * </pre>
 */
public final class SquadRules {
	private SquadRules() {}

	public enum Unit {
		/** Collector: melee 5, vindicator-like. */
		DEBT_COLLECTOR("debt_collector", 24, 5, 0.33),
		/** Repo Man: crossbow, keeps distance. */
		REPO_MAN("repo_man", 24, 0, 0.30),
		/** Accountant: "Audit" fangs, squad leader. */
		ACCOUNTANT("accountant", 28, 0, 0.28),
		/** Enforcer: melee 12, big guy. */
		ENFORCER("enforcer", 80, 12, 0.30);

		public final String id;
		public final double health;
		public final double melee;
		public final double speed;

		Unit(String id, double health, double melee, double speed) {
			this.id = id;
			this.health = health;
			this.melee = melee;
			this.speed = speed;
		}
	}

	/** Accountant "Audit": 5 fangs, 6 damage, every 100 ticks, Weakness I for 200 ticks, range 12. */
	public static final int AUDIT_FANGS = 5;
	public static final float AUDIT_DAMAGE = 6;
	public static final int AUDIT_COOLDOWN = 100;
	public static final int AUDIT_WEAKNESS_TICKS = 200;
	public static final double AUDIT_RANGE = 12;
	/** Enforcer knockback strength and resistance. */
	public static final double ENFORCER_KNOCKBACK = 1.5;
	public static final double ENFORCER_KNOCKBACK_RESISTANCE = 0.75;

	// ---- composition ---------------------------------------------------------------------------

	/** Base table by owed amount D (§5.5). */
	public static Map<Unit, Integer> baseComposition(long owed) {
		Map<Unit, Integer> c = new EnumMap<>(Unit.class);
		if (owed >= 15_000) {
			put(c, 4, 2, 1, 1);
		} else if (owed >= 3_000) {
			put(c, 3, 2, 1, 0);
		} else if (owed >= 500) {
			put(c, 2, 1, 0, 0);
		} else {
			put(c, 2, 0, 0, 0);
		}
		return c;
	}

	private static void put(Map<Unit, Integer> c, int collectors, int repo, int accountant, int enforcer) {
		c.put(Unit.DEBT_COLLECTOR, collectors);
		c.put(Unit.REPO_MAN, repo);
		c.put(Unit.ACCOUNTANT, accountant);
		c.put(Unit.ENFORCER, enforcer);
	}

	/**
	 * Squad for wave {@code wave} (1-based): base table, then difficulty (Easy −1 Collector, min 1;
	 * Hard +1), then escalation {@code + min(wave − 1, escalationMax)} Collectors, then the hard cap
	 * (extra Collectors go first, then Repo Men, then the Enforcer; never below one member).
	 */
	public static Map<Unit, Integer> composition(long owed, int wave, LoanRules.Band band, int squadMax, int escalationMax) {
		Map<Unit, Integer> c = baseComposition(owed);
		int col = c.get(Unit.DEBT_COLLECTOR);
		if (band == LoanRules.Band.EASY) {
			col = Math.max(1, col - 1);
		} else if (band == LoanRules.Band.HARD) {
			col += 1;
		}
		col += Math.min(Math.max(0, wave - 1), Math.max(0, escalationMax));
		c.put(Unit.DEBT_COLLECTOR, col);
		int max = Math.max(1, squadMax);
		while (total(c) > max) {
			if (c.get(Unit.DEBT_COLLECTOR) > 1) {
				dec(c, Unit.DEBT_COLLECTOR);
			} else if (c.get(Unit.REPO_MAN) > 0) {
				dec(c, Unit.REPO_MAN);
			} else if (c.get(Unit.ENFORCER) > 0) {
				dec(c, Unit.ENFORCER);
			} else if (c.get(Unit.DEBT_COLLECTOR) > 0 && c.get(Unit.ACCOUNTANT) > 0) {
				dec(c, Unit.DEBT_COLLECTOR);
			} else {
				break;
			}
		}
		return c;
	}

	private static void dec(Map<Unit, Integer> c, Unit u) {
		c.put(u, c.get(u) - 1);
	}

	public static int total(Map<Unit, Integer> c) {
		return c.values().stream().mapToInt(Integer::intValue).sum();
	}

	/** Spawn order; the first entry is the leader (Accountant if present, else a Collector). */
	public static List<Unit> members(Map<Unit, Integer> c) {
		List<Unit> out = new ArrayList<>();
		for (Unit u : new Unit[] {Unit.ACCOUNTANT, Unit.DEBT_COLLECTOR, Unit.ENFORCER, Unit.REPO_MAN}) {
			for (int i = 0; i < c.getOrDefault(u, 0); i++) {
				out.add(u);
			}
		}
		return out;
	}

	/** Max health after the difficulty multiplier (Easy ×0.75, Hard ×1.25), at least 1. */
	public static double scaledHealth(Unit unit, double multiplier) {
		return Math.max(1, Math.round(unit.health * multiplier));
	}

	// ---- behavior state machine ----------------------------------------------------------------

	public enum State {
		APPROACH, NEGOTIATE, HOSTILE, LEAVING, DONE
	}

	public enum EndReason {
		PAID, PARTIAL, TIMEOUT, DEBTOR_DIED, FAR, DIMENSION, OFFLINE, DEFEATED, LEFT
	}

	public record Timers(int approachTicks, int negotiateTicks, int hostileTicks, int leaveTicks, double farDistance, int farTicks,
			double negotiateDistance) {
		public static final Timers DEFAULT = new Timers(600, 200, 6000, 100, 96, 200, 6);
	}

	/** Mutable machine: current state, when it started, "too far" timer and the end reason. */
	public static final class Machine {
		public State state = State.APPROACH;
		public long since;
		/** First tick the squad was continuously too far from the debtor (−1 = not far). */
		public long farSince = -1;
		public EndReason reason;

		public Machine(long now) {
			this.since = now;
		}

		void to(State s, long now, EndReason why) {
			state = s;
			since = now;
			farSince = -1;
			reason = why;
		}
	}

	/**
	 * What the world looks like this tick.
	 * @param leaderDistance  distance leader → debtor (NaN: no leader alive or other dimension)
	 * @param nearestDistance distance of the nearest alive member (NaN: none)
	 * @param alive           members still alive (members in unloaded chunks count as alive)
	 * @param attacked        a squad member was damaged by the debtor since the last step
	 */
	public record Observation(long now, boolean debtorOnline, boolean sameDimension, boolean debtorDead, double leaderDistance,
			double nearestDistance, int alive, long owed, boolean attacked) {}

	public enum EffectType {
		NEGOTIATE, HOSTILE, LEAVE, DESPAWN
	}

	public record Effect(EffectType type, EndReason reason) {}

	/** Advances the machine one observation; the effects are for the world side (lines, targets, despawn). */
	public static List<Effect> step(Machine m, Observation o, Timers t) {
		List<Effect> fx = new ArrayList<>();
		if (m.state == State.DONE) {
			return fx;
		}
		if (o.alive() <= 0) {
			return done(m, o, fx, EndReason.DEFEATED);
		}
		if (m.state == State.LEAVING) {
			return o.now() - m.since >= t.leaveTicks() ? done(m, o, fx, m.reason == null ? EndReason.LEFT : m.reason) : fx;
		}
		if (!o.debtorOnline()) {
			return done(m, o, fx, EndReason.OFFLINE);
		}
		if (!o.sameDimension()) {
			return done(m, o, fx, EndReason.DIMENSION);
		}
		if (o.owed() <= 0) {
			return leave(m, o, fx, EndReason.PAID);
		}
		switch (m.state) {
			case APPROACH -> {
				if (o.attacked() || o.now() - m.since >= t.approachTicks()) {
					m.to(State.HOSTILE, o.now(), null);
					fx.add(new Effect(EffectType.HOSTILE, null));
				} else if (!Double.isNaN(o.leaderDistance()) && o.leaderDistance() <= t.negotiateDistance() && !o.debtorDead()) {
					m.to(State.NEGOTIATE, o.now(), null);
					fx.add(new Effect(EffectType.NEGOTIATE, null));
				}
			}
			case NEGOTIATE -> {
				if (o.attacked() || o.now() - m.since >= t.negotiateTicks()) {
					m.to(State.HOSTILE, o.now(), null);
					fx.add(new Effect(EffectType.HOSTILE, null));
				}
			}
			case HOSTILE -> {
				if (o.debtorDead()) {
					return leave(m, o, fx, EndReason.DEBTOR_DIED);
				}
				if (o.now() - m.since >= t.hostileTicks()) {
					return leave(m, o, fx, EndReason.TIMEOUT);
				}
				boolean far = Double.isNaN(o.nearestDistance()) || o.nearestDistance() > t.farDistance();
				if (!far) {
					m.farSince = -1;
				} else {
					if (m.farSince < 0) {
						m.farSince = o.now();
					}
					if (o.now() - m.farSince >= t.farTicks()) {
						return done(m, o, fx, EndReason.FAR);
					}
				}
			}
			default -> {
			}
		}
		return fx;
	}

	private static List<Effect> done(Machine m, Observation o, List<Effect> fx, EndReason why) {
		m.to(State.DONE, o.now(), why);
		fx.add(new Effect(EffectType.DESPAWN, why));
		return fx;
	}

	private static List<Effect> leave(Machine m, Observation o, List<Effect> fx, EndReason why) {
		m.to(State.LEAVING, o.now(), why);
		fx.add(new Effect(EffectType.LEAVE, why));
		return fx;
	}

	/** Switches any live machine to LEAVING (paid); used when the debt hits 0 by other means. */
	public static List<Effect> paid(Machine m, long now) {
		List<Effect> fx = new ArrayList<>();
		if (m.state == State.DONE || m.state == State.LEAVING) {
			return fx;
		}
		m.to(State.LEAVING, now, EndReason.PAID);
		fx.add(new Effect(EffectType.LEAVE, EndReason.PAID));
		return fx;
	}

	public enum Choice {
		PAY_ALL, PAY_PART, REFUSE, TIMEOUT
	}

	/**
	 * Applies the debtor's answer (only in NEGOTIATE). {@code paidOk} = the payment went through.
	 * Paying in full → PAID, a partial payment ≥ the share sends the wave away, anything else
	 * (refuse, timeout, failed payment) turns the squad hostile.
	 */
	public static List<Effect> answer(Machine m, Choice choice, boolean paidOk, long now) {
		List<Effect> fx = new ArrayList<>();
		if (m.state != State.NEGOTIATE) {
			return fx;
		}
		if (choice == Choice.PAY_ALL && paidOk) {
			m.to(State.LEAVING, now, EndReason.PAID);
			fx.add(new Effect(EffectType.LEAVE, EndReason.PAID));
		} else if (choice == Choice.PAY_PART && paidOk) {
			m.to(State.LEAVING, now, EndReason.PARTIAL);
			fx.add(new Effect(EffectType.LEAVE, EndReason.PARTIAL));
		} else {
			m.to(State.HOSTILE, now, null);
			fx.add(new Effect(EffectType.HOSTILE, null));
		}
		return fx;
	}

	/** Minimum partial payment that sends a wave away: ceil(owed × share), 1 … owed. */
	public static long partialPayment(long owed, double share) {
		return Math.max(1, Math.min(owed, LoanRules.ceilSafe(owed * share)));
	}

	// ---- wave schedule -------------------------------------------------------------------------

	/** A debtor may get a new wave when: no live squad and the scheduled tick passed. */
	public static boolean waveDue(long now, long nextWaveTick, boolean hasLiveSquad) {
		return !hasLiveSquad && nextWaveTick > 0 && now >= nextWaveTick;
	}

	/** Schedules the first wave right after the loan defaulted. */
	public static void scheduleFirstWave(LoanRecord rec, long now, int delay) {
		rec.nextWaveTick = now + Math.max(0, delay);
		rec.queued = false;
	}

	/** A wave spawned: count it; the next one comes at the next MCD boundary after the deadline. */
	public static void waveSpawned(LoanRecord rec, long now) {
		rec.wave += 1;
		rec.nextWaveTick = LoanRules.nextBoundary(rec.deadlineTick, now);
		rec.queued = false;
	}

	/** No valid spawn spot: retry the whole wave after {@code retry} ticks. */
	public static void waveRetry(LoanRecord rec, long now, int retry) {
		rec.nextWaveTick = now + retry;
	}

	/** The debtor logged off with a live wave: remember one queued wave (max one). */
	public static void waveQueued(LoanRecord rec) {
		if (rec.status == Status.DEFAULT) {
			rec.queued = true;
		}
	}

	/**
	 * Debtor joined: a queued wave, or one whose time passed while offline, spawns {@code delay} ticks
	 * after joining (max one). Returns whether the "they came by while you were away" line applies.
	 */
	public static boolean onDebtorJoin(LoanRecord rec, long now, int delay) {
		if (rec.status != Status.DEFAULT) {
			return false;
		}
		boolean missed = rec.queued || (rec.nextWaveTick > 0 && rec.nextWaveTick <= now);
		if (missed) {
			rec.queued = false;
			rec.nextWaveTick = now + Math.max(0, delay);
		}
		return missed;
	}

	// ---- repossession --------------------------------------------------------------------------

	/**
	 * @param seized     chips moved from the balance to the debt
	 * @param itemCredit debt reduction credited for the item (≤ owed after the chips)
	 */
	public record Repossession(long seized, long itemCredit) {}

	/** seized = min(owed, floor(balance × pct)); the item then covers up to the rest of the debt. */
	public static Repossession repossession(long balance, long owed, int percent, long itemValue) {
		long seized = LoanRules.seizeAmount(balance, percent, owed);
		long item = Math.max(0, Math.min(owed - seized, itemValue));
		return new Repossession(seized, item);
	}

	/** Index of the stack with the highest value (ties: first), −1 if none is worth anything. */
	public static int bestAppraised(long[] values) {
		int best = -1;
		for (int i = 0; i < values.length; i++) {
			if (values[i] > 0 && (best < 0 || values[i] > values[best])) {
				best = i;
			}
		}
		return best;
	}
}
