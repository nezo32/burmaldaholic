/**
 * VIP tiers (GAME_DESIGN §12). PURE.
 * Tier by lifetime chips wagered W (never decreases; tiers are never lost).
 */

export const TIER_COUNT = 6;
export const MAX_TIER = 5;
/** Tier indices. */
export const BRONZE = 0;
export const SILVER = 1;
export const GOLD = 2;
export const PLATINUM = 3;
export const DIAMOND = 4;
export const NETHERITE = 5;

/** Default §12 thresholds for Silver..Netherite (index 0 = Silver). */
export const DEFAULT_THRESHOLDS: readonly number[] = [5_000, 25_000, 100_000, 500_000, 2_500_000];

/**
 * Make thresholds strictly ascending (CONFIG.md: "must be ascending across tiers").
 * A misconfigured value is raised to previous + 1 so a higher tier is never cheaper.
 */
export function sanitizeThresholds(raw: readonly number[]): number[] {
  const out: number[] = [];
  let prev = 0;
  for (let i = 0; i < MAX_TIER; i++) {
    let v = Math.floor(Number(raw[i]));
    if (!Number.isFinite(v) || v < 1) v = DEFAULT_THRESHOLDS[i] as number;
    const x = v > prev ? v : prev + 1;
    out.push(x);
    prev = x;
  }
  return out;
}

/** Tier earned by lifetime wagered W with (sanitized) thresholds. */
export function tierFor(wagered: number, thresholds: readonly number[]): number {
  const th = sanitizeThresholds(thresholds);
  let tier = 0;
  for (let i = 0; i < th.length; i++) if (wagered >= (th[i] as number)) tier = i + 1;
  return tier;
}

/** Threshold of `tier` (0 for Bronze). */
export function thresholdOf(tier: number, thresholds: readonly number[]): number {
  if (tier <= 0) return 0;
  return sanitizeThresholds(thresholds)[Math.min(tier, MAX_TIER) - 1] as number;
}

export interface TierProgress {
  tier: number;
  /** next tier index, undefined at Netherite */
  next?: number;
  /** W needed for next */
  target?: number;
  /** 0..1 progress from the current tier threshold to the next */
  fraction: number;
}

/** Progress to the next tier. `current` = stored tier (tiers are never lost). */
export function progress(wagered: number, thresholds: readonly number[], current = 0): TierProgress {
  const tier = Math.max(current, tierFor(wagered, thresholds));
  if (tier >= MAX_TIER) return { tier, fraction: 1 };
  const from = thresholdOf(tier, thresholds);
  const target = thresholdOf(tier + 1, thresholds);
  const fraction = Math.max(0, Math.min(1, (wagered - from) / Math.max(1, target - from)));
  return { tier, next: tier + 1, target, fraction };
}

/** New tiers reached when going from `prev` to `next` (for one announcement per tier). */
export function promotions(prev: number, next: number): number[] {
  const out: number[] = [];
  for (let t = Math.max(0, prev) + 1; t <= Math.min(MAX_TIER, next); t++) out.push(t);
  return out;
}

// ---- perks ------------------------------------------------------------------------------

/** Cashback rate for a tier; `rates` = [gold, platinum, diamond, netherite]. Below Gold: 0. */
export function cashbackRate(tier: number, rates: readonly number[]): number {
  if (tier < GOLD) return 0;
  const r = Number(rates[Math.min(tier, MAX_TIER) - GOLD] ?? 0);
  return Number.isFinite(r) ? Math.max(0, Math.min(0.5, r)) : 0;
}

/** Contract reward bonus (fraction): Silver `silver`, Gold and above `gold`. */
export function contractBonus(tier: number, silver: number, gold: number): number {
  if (tier >= GOLD) return gold;
  if (tier >= SILVER) return silver;
  return 0;
}

/** Contract slots: base (3), Platinum base + 1, Diamond+ base + 2. */
export function contractSlots(tier: number, base: number): number {
  return base + (tier >= DIAMOND ? 2 : tier >= PLATINUM ? 1 : 0);
}

/** Loan product [principal, days, minVip] (CONFIG.md loan.products). Largest principal for this tier. */
export function maxLoanFor(tier: number, products: readonly (readonly number[])[]): number {
  let best = 0;
  for (const p of products) {
    const principal = Number(p[0]);
    const minVip = Number(p[2] ?? 0);
    if (minVip <= tier && Number.isFinite(principal) && principal > best) best = principal;
  }
  return best;
}

/** Highest loan principal first unlocked exactly at `tier` (undefined if none new). */
export function loanUnlockedAt(tier: number, products: readonly (readonly number[])[]): number | undefined {
  const at = maxLoanFor(tier, products);
  return at > 0 && (tier === 0 || at > maxLoanFor(tier - 1, products)) ? at : undefined;
}

export type Perk =
  | { key: string }
  | { key: string; chips: number }
  | { key: string; percent: number }
  | { key: string; count: number };

export interface PerkParams {
  loanProducts: readonly (readonly number[])[];
  contractBonusSilver: number;
  contractBonusGold: number;
  baseSlots: number;
  cashback: readonly number[];
}

const P = 'gui.burmaldaholic.vip.perk.';
const pct = (f: number): number => Math.round(f * 1000) / 10;

/** Perks newly granted at `tier` (§12 table), with values from config. */
export function perksAt(tier: number, c: PerkParams): Perk[] {
  const out: Perk[] = [];
  const loan = loanUnlockedAt(tier, c.loanProducts);
  switch (tier) {
    case BRONZE:
      out.push({ key: `${P}poker_micro` });
      break;
    case SILVER:
      out.push({ key: `${P}poker_low` }, { key: `${P}scratch_gold` });
      break;
    case GOLD:
      out.push({ key: `${P}netherite_slots` }, { key: `${P}high_roller` }, { key: `${P}poker_mid` }, { key: `${P}emerald_rate` });
      break;
    case PLATINUM:
      out.push({ key: `${P}loan_discount` });
      break;
    case DIAMOND:
      out.push({ key: `${P}poker_high` });
      break;
    default:
      break;
  }
  if (loan !== undefined) out.push({ key: `${P}loan`, chips: loan });
  const bonus = contractBonus(tier, c.contractBonusSilver, c.contractBonusGold);
  if (bonus > 0 && bonus !== contractBonus(tier - 1, c.contractBonusSilver, c.contractBonusGold)) out.push({ key: `${P}contract_bonus`, percent: pct(bonus) });
  const slots = contractSlots(tier, c.baseSlots);
  if (tier > 0 && slots !== contractSlots(tier - 1, c.baseSlots)) out.push({ key: `${P}contract_slots`, count: slots });
  const cb = cashbackRate(tier, c.cashback);
  if (cb > 0) out.push({ key: `${P}cashback`, percent: pct(cb) });
  const cosmetic = ['', 'cosmetic_name', 'cosmetic_particles', 'cosmetic_title', 'cosmetic_card', 'cosmetic_aura'][tier];
  if (cosmetic) out.push({ key: `${P}${cosmetic}` });
  return out;
}

// ---- cashback ledger ---------------------------------------------------------------------

/** Per-player daily totals (house-banked, bank-paid rounds only for the cashback part). */
export interface DayLedger {
  day: number;
  /** all settled rounds today (wallet "today's result") */
  staked: number;
  returned: number;
  /** cashback-eligible rounds today */
  cbStaked: number;
  cbReturned: number;
  /** Σ stake × house edge of the eligible rounds (the cashback base since 2026-09) */
  cbTheo: number;
}

export const emptyLedger = (day: number): DayLedger => ({ day, staked: 0, returned: 0, cbStaked: 0, cbReturned: 0, cbTheo: 0 });

/**
 * Cashback = floor(rate × theoretical loss) (§12, CHANGED 2026-09). The theoretical loss is
 * Σ stake × house edge of the day's eligible rounds, i.e. the EXPECTED loss, whatever the
 * player actually won or lost. Since rate ≤ 0.5 < 1, cashback < expected loss for every game
 * and tier: the effective edge is HE × (1 − rate) > 0. (The old rate × max(0, net loss)
 * formula was +EV on low-edge games: a max(0, ·) of a high-variance day result is worth far
 * more than the edge.)
 */
export function cashbackAmount(theoreticalLoss: number, rate: number): number {
  if (!(theoreticalLoss > 0) || !(rate > 0)) return 0;
  return Math.floor(theoreticalLoss * Math.min(rate, 1) + 1e-9);
}

export interface RollResult {
  ledger: DayLedger;
  /** the closed day, when the stored ledger belonged to an earlier day */
  closed?: DayLedger;
}

/** Roll the ledger to `today`; returns the closed day (to pay cashback on) if it changed. */
export function rollLedger(ledger: DayLedger | undefined, today: number): RollResult {
  if (!ledger || typeof ledger.day !== 'number') return { ledger: emptyLedger(today) };
  // Ledgers stored before the 2026-09 change have no cbTheo: they pay no cashback.
  const l = typeof ledger.cbTheo === 'number' ? ledger : { ...ledger, cbTheo: 0 };
  if (l.day >= today) return { ledger: l };
  return { ledger: emptyLedger(today), closed: l };
}

/** Add a settled round to today's ledger (rolls first; the closed day is returned). */
export function recordRound(ledger: DayLedger | undefined, today: number, staked: number, returned: number, cashbackEligible: boolean, theoreticalLoss = 0): RollResult {
  const r = rollLedger(ledger, today);
  const l = { ...r.ledger };
  l.staked += staked;
  l.returned += returned;
  if (cashbackEligible) {
    l.cbStaked += staked;
    l.cbReturned += returned;
    l.cbTheo += Math.max(0, theoreticalLoss);
  }
  return { ledger: l, closed: r.closed };
}

// ---- milestones (GAME_DESIGN §19 vip_* entries; Bedrock Achievements use the same keys) ---

/** Achievement id for reaching `tier` (Silver..Netherite), undefined for Bronze. */
export const vipAchievement = (tier: number): string | undefined =>
  tier >= SILVER && tier <= MAX_TIER ? `vip_${['silver', 'gold', 'platinum', 'diamond', 'netherite'][tier - 1]}` : undefined;
