/**
 * Last Chance rules (GAME_DESIGN §15), pure: trigger decision, the flip, success costs,
 * cooldowns and the Hardcore "scar". No @minecraft imports; the script layer feeds plain values.
 */
import type { Rng } from '../../core/logic/rng';

export type LcDifficulty = 'peaceful' | 'easy' | 'normal' | 'hard';
export type HardcoreMode = 'DISABLED' | 'HIGH_STAKES';
export type LcMode = 'standard' | 'high_stakes';

export interface LcConfig {
  enabled: boolean;
  chance: { easy: number; normal: number; hard: number };
  cooldownTicks: number;
  costPercent: number;
  hardcoreMode: HardcoreMode;
  hardcore: { chance: number; cooldownTicks: number; minStake: number; heartCost: number; minMaxHealth: number };
}

/** CONFIG.md defaults (used by tests and as a fallback). */
export const DEFAULT_LC_CONFIG: LcConfig = {
  enabled: true,
  chance: { easy: 0.6, normal: 0.5, hard: 0.4 },
  cooldownTicks: 24000,
  costPercent: 10,
  hardcoreMode: 'DISABLED',
  hardcore: { chance: 0.5, cooldownTicks: 120000, minStake: 100, heartCost: 2, minMaxHealth: 8 },
};

/**
 * Damage causes Last Chance never intercepts (§15.1): void, `/kill` and scripted kills
 * (`Entity.kill()`, used by the Soul Wager, arrives as `override`), self-destruct.
 */
export const EXCLUDED_CAUSES: ReadonlySet<string> = new Set(['void', 'override', 'selfDestruct']);

/** Debt-collector squad members (GAME_DESIGN §5.5); no Last Chance against them in Hardcore (§5.7). */
export const SQUAD_TYPES: ReadonlySet<string> = new Set([
  'burmaldaholic:debt_collector',
  'burmaldaholic:repo_man',
  'burmaldaholic:accountant',
  'burmaldaholic:enforcer',
]);

export interface HitInfo {
  /** Damage the hit will deal (before-event value). */
  damage: number;
  /** Current health (without absorption). */
  health: number;
  /** EntityDamageCause string. */
  cause: string;
  /** typeIds of the attacker and its projectile, if any. */
  attackerTypes: readonly string[];
}

export interface PlayerFacts {
  balance: number;
  /** Total value of chip items carried. */
  chipsCarried: number;
  /** Max health as the engine reports it (effectiveMax). */
  baseMaxHealth: number;
  /** Permanent HP removed by High-Stakes saves. */
  scarHp: number;
  holdsTotem: boolean;
  /** Killed by a lost Soul Wager (core SOUL_WAGER_TAG). */
  soulWager: boolean;
  /** World tick of the last use, undefined if never used. */
  lastUsedAt: number | undefined;
}

export interface WorldFacts {
  casinoOn: boolean;
  hardcore: boolean;
  difficulty: LcDifficulty;
  now: number;
}

export type SkipReason =
  | 'not_lethal'
  | 'casino_off'
  | 'disabled'
  | 'hardcore_disabled'
  | 'totem'
  | 'excluded_cause'
  | 'soul_wager'
  | 'squad_hardcore'
  | 'cooldown'
  | 'not_eligible';

export type Decision = { kind: 'skip'; reason: SkipReason } | { kind: 'flip'; mode: LcMode; chance: number; cooldownTicks: number };

/** A hit is lethal when it takes at least the remaining health. */
export const isLethal = (damage: number, health: number): boolean => health > 0 && damage >= health;

export const clamp01 = (p: number): number => (Number.isFinite(p) ? Math.min(1, Math.max(0, p)) : 0);

/** Standard success chance for a (non-Hardcore) difficulty; Peaceful uses the Easy value. */
export function chanceFor(difficulty: LcDifficulty, cfg: LcConfig): number {
  switch (difficulty) {
    case 'peaceful':
    case 'easy':
      return clamp01(cfg.chance.easy);
    case 'normal':
      return clamp01(cfg.chance.normal);
    case 'hard':
      return clamp01(cfg.chance.hard);
  }
}

/** Which variant applies in this world, or undefined when Last Chance is off entirely. */
export function modeFor(hardcore: boolean, cfg: LcConfig): LcMode | undefined {
  if (!cfg.enabled) return undefined;
  if (!hardcore) return 'standard';
  return cfg.hardcoreMode === 'HIGH_STAKES' ? 'high_stakes' : undefined;
}

export const cooldownFor = (mode: LcMode, cfg: LcConfig): number => Math.max(0, Math.trunc(mode === 'high_stakes' ? cfg.hardcore.cooldownTicks : cfg.cooldownTicks));

/** Ticks until Last Chance is ready again (0 = ready). */
export function cooldownRemaining(now: number, lastUsedAt: number | undefined, cooldownTicks: number): number {
  if (lastUsedAt === undefined) return 0;
  return Math.max(0, lastUsedAt + cooldownTicks - now);
}

/** Max health after scars (never below 1 HP). */
export const scarredMax = (baseMax: number, scarHp: number): number => Math.max(1, baseMax - Math.max(0, scarHp));

/** High-Stakes eligibility (§15.3): enough chips (balance + carried) and enough max health. */
export function highStakesEligible(p: Pick<PlayerFacts, 'balance' | 'chipsCarried' | 'baseMaxHealth' | 'scarHp'>, cfg: LcConfig): boolean {
  return Math.max(0, p.balance) + Math.max(0, p.chipsCarried) >= cfg.hardcore.minStake && scarredMax(p.baseMaxHealth, p.scarHp) >= cfg.hardcore.minMaxHealth;
}

/** Decide whether a hit triggers a coin flip (§15.1, §15.3, §5.7). */
export function decide(hit: HitInfo, player: PlayerFacts, w: WorldFacts, cfg: LcConfig): Decision {
  const skip = (reason: SkipReason): Decision => ({ kind: 'skip', reason });
  if (!isLethal(hit.damage, hit.health)) return skip('not_lethal');
  if (!w.casinoOn) return skip('casino_off');
  if (!cfg.enabled) return skip('disabled');
  const mode = modeFor(w.hardcore, cfg);
  if (!mode) return skip('hardcore_disabled');
  if (player.holdsTotem) return skip('totem');
  if (EXCLUDED_CAUSES.has(hit.cause)) return skip('excluded_cause');
  if (player.soulWager) return skip('soul_wager');
  if (w.hardcore && hit.attackerTypes.some((id) => SQUAD_TYPES.has(id))) return skip('squad_hardcore');
  const cd = cooldownFor(mode, cfg);
  if (cooldownRemaining(w.now, player.lastUsedAt, cd) > 0) return skip('cooldown');
  if (mode === 'high_stakes') {
    if (!highStakesEligible(player, cfg)) return skip('not_eligible');
    return { kind: 'flip', mode, chance: clamp01(cfg.hardcore.chance), cooldownTicks: cd };
  }
  return { kind: 'flip', mode, chance: chanceFor(w.difficulty, cfg), cooldownTicks: cd };
}

/** The coin: heads (true) with probability p. */
export const flip = (rng: Rng, p: number): boolean => rng.next() < clamp01(p);

export interface SuccessPlan {
  /** Chips taken from the balance. */
  fee: number;
  /** High stakes: destroy every carried chip item too. */
  destroyChips: boolean;
  /** HP added to the permanent scar. */
  addScarHp: number;
  /** Max health after this save. */
  newMax: number;
  /** Health to set: ceil(newMax / 2). */
  reviveHealth: number;
}

/** What a successful flip costs and where it leaves the player (§15.1 / §15.3). */
export function planSuccess(mode: LcMode, cfg: LcConfig, balance: number, baseMaxHealth: number, scarHp: number): SuccessPlan {
  const bal = Math.max(0, Math.floor(balance));
  if (mode === 'high_stakes') {
    const add = Math.max(0, Math.trunc(cfg.hardcore.heartCost));
    const newMax = scarredMax(baseMaxHealth, scarHp + add);
    return { fee: bal, destroyChips: true, addScarHp: add, newMax, reviveHealth: Math.ceil(newMax / 2) };
  }
  const pct = Math.min(100, Math.max(0, cfg.costPercent));
  const newMax = scarredMax(baseMaxHealth, scarHp);
  return { fee: Math.floor((bal * pct) / 100), destroyChips: false, addScarHp: 0, newMax, reviveHealth: Math.ceil(newMax / 2) };
}

/** Heal allowed under a scar: never above the scarred maximum. Returns 0 to cancel. */
export function cappedHealing(current: number, healing: number, cap: number): number {
  return Math.max(0, Math.min(healing, cap - current));
}

/** Persisted per-player state. */
export interface LcRecord {
  usedAt?: number;
  /** "Recharged" message already sent for this use. */
  notified?: boolean;
  scarHp?: number;
}

/** True when the "Last Chance has recharged" message is due. */
export function readyDue(rec: LcRecord, now: number, cooldownTicks: number): boolean {
  return rec.usedAt !== undefined && !rec.notified && cooldownTicks > 0 && cooldownRemaining(now, rec.usedAt, cooldownTicks) === 0;
}

/** Parse a stored record defensively. */
export function parseRecord(v: unknown): LcRecord {
  if (!v || typeof v !== 'object') return {};
  const o = v as Record<string, unknown>;
  const num = (x: unknown): number | undefined => (typeof x === 'number' && Number.isFinite(x) ? x : undefined);
  const rec: LcRecord = {};
  const usedAt = num(o.usedAt);
  if (usedAt !== undefined) rec.usedAt = usedAt;
  if (o.notified === true) rec.notified = true;
  const scar = num(o.scarHp);
  if (scar !== undefined && scar > 0) rec.scarHp = Math.trunc(scar);
  return rec;
}
