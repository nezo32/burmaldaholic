/**
 * Debt Collector squads (GAME_DESIGN §5.5): composition by owed amount, difficulty and wave,
 * per-unit stats, and the squad behavior state machine. PURE.
 *
 * APPROACH (non-hostile, approachTicks max) ── leader within 6 blocks ──▶ NEGOTIATE
 *   NEGOTIATE ── pay in full ──▶ LEAVING (paid) | pay ≥ share ──▶ LEAVING (partial)
 *             ── refuse / timeout / squad attacked ──▶ HOSTILE
 *   APPROACH timeout / squad attacked ──▶ HOSTILE
 * HOSTILE ── hostileTicks / debtor died ──▶ LEAVING;  far (> 96 blocks for 200 t),
 *            dimension change, debtor offline ──▶ despawn now
 * LEAVING ── 100 t ──▶ despawn.  Debt reaches 0 at any time ──▶ LEAVING (paid).
 * Every member dead ──▶ done (defeated).
 */
import { type Band, type LoanRecord, nextBoundary } from './loan';

export const UNITS = ['debt_collector', 'repo_man', 'accountant', 'enforcer'] as const;
export type UnitId = (typeof UNITS)[number];

export interface UnitStats {
  /** base max health (before the difficulty multiplier) */
  health: number;
  /** melee damage (0 = none: ranged / audit) */
  melee: number;
  speed: number;
}

export const UNIT_STATS: Readonly<Record<UnitId, UnitStats>> = {
  debt_collector: { health: 24, melee: 5, speed: 0.33 },
  repo_man: { health: 24, melee: 0, speed: 0.3 },
  accountant: { health: 28, melee: 0, speed: 0.28 },
  enforcer: { health: 80, melee: 12, speed: 0.3 },
};

/** Accountant "Audit": 6 damage, every 100 ticks, Weakness I for 200 ticks. */
export const AUDIT = { damage: 6, cooldown: 100, weaknessTicks: 200, range: 12, fangs: 5 } as const;
/** Enforcer melee knockback strength. */
export const ENFORCER_KNOCKBACK = 1.5;

export type Composition = Record<UnitId, number>;

export interface CompositionConfig {
  /** hard cap on squad members (10) */
  squadMax: number;
  /** max extra Collectors from later waves (3) */
  escalationMax: number;
}

/** Base table by owed amount D (§5.5). */
export function baseComposition(owed: number): Composition {
  if (owed >= 15_000) return { debt_collector: 4, repo_man: 2, accountant: 1, enforcer: 1 };
  if (owed >= 3_000) return { debt_collector: 3, repo_man: 2, accountant: 1, enforcer: 0 };
  if (owed >= 500) return { debt_collector: 2, repo_man: 1, accountant: 0, enforcer: 0 };
  return { debt_collector: 2, repo_man: 0, accountant: 0, enforcer: 0 };
}

/**
 * Squad for wave `wave` (1-based): base table, then difficulty (Easy −1 Collector, min 1; Hard
 * +1), then escalation `+ min(wave − 1, escalationMax)` Collectors, then the hard cap (extra
 * Collectors go first, then Repo Men, then the Enforcer; never below one member).
 */
export function squadComposition(owed: number, wave: number, band: Band, cfg: CompositionConfig): Composition {
  const c = baseComposition(owed);
  if (band === 'easy') c.debt_collector = Math.max(1, c.debt_collector - 1);
  if (band === 'hard') c.debt_collector += 1;
  c.debt_collector += Math.min(Math.max(0, wave - 1), Math.max(0, cfg.escalationMax));
  const max = Math.max(1, Math.floor(cfg.squadMax));
  const total = (): number => c.debt_collector + c.repo_man + c.accountant + c.enforcer;
  while (total() > max) {
    if (c.debt_collector > 1) c.debt_collector--;
    else if (c.repo_man > 0) c.repo_man--;
    else if (c.enforcer > 0) c.enforcer--;
    else if (c.debt_collector > 0 && c.accountant > 0) c.debt_collector--;
    else break;
  }
  return c;
}

/** Spawn order; the first entry is the leader (Accountant if present, else a Collector). */
export function squadMembers(c: Composition): UnitId[] {
  const out: UnitId[] = [];
  const add = (u: UnitId): void => {
    for (let i = 0; i < c[u]; i++) out.push(u);
  };
  add('accountant');
  add('debt_collector');
  add('enforcer');
  add('repo_man');
  return out;
}

/** Health after the difficulty multiplier (Easy ×0.75, Hard ×1.25), at least 1. */
export function scaledHealth(unit: UnitId, multiplier: number): number {
  return Math.max(1, Math.round(UNIT_STATS[unit].health * multiplier));
}

// ---- behavior state machine ------------------------------------------------------------

export type SquadState = 'approach' | 'negotiate' | 'hostile' | 'leaving' | 'done';

export type EndReason = 'paid' | 'partial' | 'timeout' | 'debtor_died' | 'far' | 'dimension' | 'offline' | 'defeated' | 'left';

export interface SquadTimers {
  approachTicks: number;
  negotiateTicks: number;
  hostileTicks: number;
  /** walk-away time before despawn */
  leaveTicks: number;
  /** despawn when farther than farDistance for farTicks while hostile */
  farDistance: number;
  farTicks: number;
  /** leader distance that starts the negotiation */
  negotiateDistance: number;
}

export const DEFAULT_TIMERS: SquadTimers = {
  approachTicks: 600,
  negotiateTicks: 200,
  hostileTicks: 6000,
  leaveTicks: 100,
  farDistance: 96,
  farTicks: 200,
  negotiateDistance: 6,
};

export interface SquadMachine {
  state: SquadState;
  /** tick the current state started */
  since: number;
  /** first tick the squad was continuously too far from the debtor */
  farSince?: number;
  reason?: EndReason;
}

/** What the world looks like this tick (script side gathers it). */
export interface SquadObservation {
  now: number;
  debtorOnline: boolean;
  /** debtor in the squad's dimension */
  sameDimension: boolean;
  debtorDead: boolean;
  /** distance leader -> debtor (undefined: no leader alive or other dimension) */
  leaderDistance?: number;
  /** distance of the nearest alive member (undefined: none alive) */
  nearestDistance?: number;
  alive: number;
  /** current debt of the debtor */
  owed: number;
  /** a squad member was damaged by the debtor since the last step */
  attacked: boolean;
}

export type SquadEffect =
  | { type: 'negotiate' }
  | { type: 'hostile' }
  | { type: 'leave'; reason: EndReason }
  | { type: 'despawn'; reason: EndReason };

export const newSquad = (now: number): SquadMachine => ({ state: 'approach', since: now });

const to = (state: SquadState, now: number, reason?: EndReason): SquadMachine => ({ state, since: now, reason });

/** Advance the machine one observation. Effects are for the script (events, lines, despawn). */
export function stepSquad(m: SquadMachine, o: SquadObservation, t: SquadTimers = DEFAULT_TIMERS): { m: SquadMachine; effects: SquadEffect[] } {
  const effects: SquadEffect[] = [];
  const done = (reason: EndReason): { m: SquadMachine; effects: SquadEffect[] } => {
    effects.push({ type: 'despawn', reason });
    return { m: to('done', o.now, reason), effects };
  };
  const leave = (reason: EndReason): { m: SquadMachine; effects: SquadEffect[] } => {
    effects.push({ type: 'leave', reason });
    return { m: to('leaving', o.now, reason), effects };
  };
  if (m.state === 'done') return { m, effects };
  if (o.alive <= 0) return done('defeated');
  if (m.state === 'leaving') return o.now - m.since >= t.leaveTicks ? done(m.reason ?? 'left') : { m, effects };
  if (!o.debtorOnline) return done('offline');
  if (!o.sameDimension) return done('dimension');
  if (o.owed <= 0) return leave('paid');

  switch (m.state) {
    case 'approach': {
      if (o.attacked || o.now - m.since >= t.approachTicks) {
        effects.push({ type: 'hostile' });
        return { m: to('hostile', o.now), effects };
      }
      if (o.leaderDistance !== undefined && o.leaderDistance <= t.negotiateDistance && !o.debtorDead) {
        effects.push({ type: 'negotiate' });
        return { m: to('negotiate', o.now), effects };
      }
      return { m, effects };
    }
    case 'negotiate': {
      if (o.attacked || o.now - m.since >= t.negotiateTicks) {
        effects.push({ type: 'hostile' });
        return { m: to('hostile', o.now), effects };
      }
      return { m, effects };
    }
    case 'hostile': {
      if (o.debtorDead) return leave('debtor_died');
      if (o.now - m.since >= t.hostileTicks) return leave('timeout');
      const far = o.nearestDistance === undefined || o.nearestDistance > t.farDistance;
      if (!far) return { m: m.farSince === undefined ? m : { ...m, farSince: undefined }, effects };
      const farSince = m.farSince ?? o.now;
      if (o.now - farSince >= t.farTicks) return done('far');
      return { m: { ...m, farSince }, effects };
    }
  }
}

export type NegotiationChoice = 'pay_all' | 'pay_part' | 'refuse' | 'timeout';

/**
 * Apply the debtor's answer (only meaningful in NEGOTIATE). `paidOk` = the payment went through.
 * Paying in full ends with PAID, a partial payment ≥ the share sends the wave away, anything else
 * (refuse, timeout, failed payment) turns the squad hostile.
 */
export function answerNegotiation(m: SquadMachine, choice: NegotiationChoice, paidOk: boolean, now: number): { m: SquadMachine; effects: SquadEffect[] } {
  if (m.state !== 'negotiate') return { m, effects: [] };
  if (choice === 'pay_all' && paidOk) return { m: to('leaving', now, 'paid'), effects: [{ type: 'leave', reason: 'paid' }] };
  if (choice === 'pay_part' && paidOk) return { m: to('leaving', now, 'partial'), effects: [{ type: 'leave', reason: 'partial' }] };
  return { m: to('hostile', now), effects: [{ type: 'hostile' }] };
}

/** Minimum partial payment that sends a wave away: ceil(owed × share), at least 1. */
export const partialPayment = (owed: number, share: number): number => Math.max(1, Math.min(owed, Math.ceil(Math.round(owed * share * 1e6) / 1e6)));

// ---- wave schedule -----------------------------------------------------------------------

/** A debtor may get a new wave when: collectors on, no live squad, and the scheduled tick passed. */
export function waveDue(o: { now: number; nextWaveTick: number; hasLiveSquad: boolean }): boolean {
  return !o.hasLiveSquad && o.nextWaveTick > 0 && o.now >= o.nextWaveTick;
}

/** Schedule the first wave right after the loan defaulted. */
export const scheduleFirstWave = (rec: LoanRecord, now: number, delay: number): LoanRecord => ({ ...rec, nextWaveTick: now + Math.max(0, delay), queued: false });

/** A wave spawned: count it; the next one comes at the next MCD boundary after the deadline. */
export const waveSpawned = (rec: LoanRecord, now: number): LoanRecord => ({ ...rec, wave: rec.wave + 1, nextWaveTick: nextBoundary(rec.deadlineTick, now), queued: false });

/** No valid spawn spot: retry the whole wave after `retry` ticks. */
export const waveRetry = (rec: LoanRecord, now: number, retry = 600): LoanRecord => ({ ...rec, nextWaveTick: now + retry });

/** The debtor logged off with a live or due wave: remember one queued wave (max one). */
export const waveQueued = (rec: LoanRecord): LoanRecord => (rec.status === 'default' ? { ...rec, queued: true } : rec);

/**
 * Debtor joined: a queued wave, or one whose time passed while offline, spawns `delay` ticks
 * after joining (max one). Returns whether the "they came by while you were away" line applies.
 */
export function onDebtorJoin(rec: LoanRecord, now: number, delay: number): { rec: LoanRecord; missed: boolean } {
  if (rec.status !== 'default') return { rec, missed: false };
  const missed = rec.queued || (rec.nextWaveTick > 0 && rec.nextWaveTick <= now);
  if (!missed) return { rec, missed };
  return { rec: { ...rec, queued: false, nextWaveTick: now + Math.max(0, delay) }, missed };
}
