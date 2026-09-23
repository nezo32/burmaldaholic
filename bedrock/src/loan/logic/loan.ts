/**
 * Loan products, interest, deadlines, late fees, garnishment and the loan state machine
 * (GAME_DESIGN §5.2–§5.4, §5.6). PURE: no @minecraft imports; all time is absolute world ticks.
 *
 *   NONE ──take──▶ ACTIVE ──paid in full──▶ NONE (goodStanding+1)
 *                    │ tick ≥ deadline
 *                    ▼
 *                DEFAULT ──paid in full──▶ NONE (goodStanding=0, cooldown)
 *                    │ each MCD boundary after the deadline: owed += ceil(owedAtDeadline × lateFee),
 *                    │ capped at capMultiplier × dueAtIssue
 */

/** 1 Minecraft day in ticks. */
export const MCD = 24_000;

export type Difficulty = 'peaceful' | 'easy' | 'normal' | 'hard';
/** Config band used for rates, late fees and collector scaling. */
export type Band = 'easy' | 'normal' | 'hard';

/** Peaceful/Easy -> easy, Normal -> normal, Hard/Hardcore -> hard (§2.3). */
export function difficultyBand(d: Difficulty, hardcore: boolean): Band {
  if (hardcore || d === 'hard') return 'hard';
  return d === 'normal' ? 'normal' : 'easy';
}

/** Parse the engine difficulty name ('Peaceful', 'Easy'...) case-insensitively. */
export function parseDifficulty(s: string): Difficulty {
  const v = s.toLowerCase();
  return v === 'peaceful' || v === 'easy' || v === 'hard' ? v : 'normal';
}

// ---- products -------------------------------------------------------------------------

/** Lang ids of the five products (gui.burmaldaholic.loan.product.<id>), by config index. */
export const PRODUCT_IDS = ['pocket', 'rent', 'business', 'serious', 'life_changing'] as const;
export type ProductId = (typeof PRODUCT_IDS)[number];

export interface Product {
  index: number;
  id: ProductId;
  principal: number;
  days: number;
  minTier: number;
}

export const DEFAULT_PRODUCTS: readonly (readonly [number, number, number])[] = [
  [100, 3, 0],
  [500, 3, 0],
  [2000, 5, 1],
  [10000, 7, 2],
  [50000, 7, 4],
];

const clampInt = (v: unknown, min: number, max: number): number | undefined =>
  typeof v === 'number' && Number.isFinite(v) ? Math.max(min, Math.min(max, Math.floor(v))) : undefined;

/**
 * `loan.products` (list of [principal, days, minTier]) -> products. Malformed rows are skipped;
 * a malformed list falls back to the defaults. Extra rows reuse the last product name.
 */
export function parseProducts(json: unknown): Product[] {
  const rows = Array.isArray(json) ? json : DEFAULT_PRODUCTS;
  const out: Product[] = [];
  rows.forEach((row, i) => {
    if (!Array.isArray(row)) return;
    const principal = clampInt(row[0], 1, 1e9);
    const days = clampInt(row[1], 1, 100);
    const minTier = clampInt(row[2], 0, 5);
    if (principal === undefined || days === undefined || minTier === undefined) return;
    out.push({ index: i, id: PRODUCT_IDS[Math.min(i, PRODUCT_IDS.length - 1)]!, principal, days, minTier });
  });
  return out.length || !Array.isArray(json) ? out : parseProducts(undefined);
}

/** Products the tier may take, and how many are locked. */
export function availableProducts(products: readonly Product[], tier: number): { available: Product[]; locked: number } {
  const available = products.filter((p) => tier >= p.minTier);
  return { available, locked: products.length - available.length };
}

// ---- interest ---------------------------------------------------------------------------

export interface RateConfig {
  /** base rate for the current difficulty band */
  base: number;
  /** discount per on-time loan (0.02) */
  goodStandingDiscount: number;
  /** max discounted loans (5) */
  goodStandingMaxSteps: number;
  /** floor (0.10) */
  minRate: number;
  /** extra discount at Platinum (tier ≥ 3) */
  platinumDiscount: number;
}

export const PLATINUM_TIER = 3;

/** rate = base − d × min(goodStanding, steps) − (tier ≥ Platinum ? pd : 0), floored at minRate. */
export function loanRate(cfg: RateConfig, goodStanding: number, tier: number): number {
  const steps = Math.min(Math.max(0, Math.floor(goodStanding)), cfg.goodStandingMaxSteps);
  const r = cfg.base - cfg.goodStandingDiscount * steps - (tier >= PLATINUM_TIER ? cfg.platinumDiscount : 0);
  return round6(Math.max(cfg.minRate, r));
}

const round6 = (x: number): number => Math.round(x * 1e6) / 1e6;
/** ceil() that ignores floating-point noise (500 × 1.2 = 600.0000000000001 -> 600). */
export const ceilSafe = (x: number): number => Math.ceil(round6(x));

/** due = ceil(principal × (1 + rate)). */
export function dueAmount(principal: number, rate: number): number {
  return ceilSafe(principal * (1 + rate));
}

/** Interest in whole percent for display (0.2 -> 20, 0.18 -> 18). */
export const ratePercent = (rate: number): number => Math.round(rate * 1000) / 10;

// ---- record ---------------------------------------------------------------------------

export type LoanStatus = 'none' | 'active' | 'default';

export interface LoanRecord {
  status: LoanStatus;
  /** product index taken (−1 = none / admin-set) */
  product: number;
  principal: number;
  /** due at issue (principal + interest) */
  due: number;
  /** currently owed */
  owed: number;
  rate: number;
  issueTick: number;
  deadlineTick: number;
  /** owed when the loan entered DEFAULT (late-fee base) */
  owedAtDeadline: number;
  /** MCD boundaries after the deadline already charged */
  feeDays: number;
  /** loans repaid on time (reset to 0 on default) */
  goodStanding: number;
  /** no new loan before this tick (post-default cooldown) */
  cooldownUntil: number;
  /** warning thresholds (ticks before deadline) already sent */
  warned: number[];
  /** default episode: waves spawned so far */
  wave: number;
  /** next collector wave may spawn at/after this tick (0 = none scheduled) */
  nextWaveTick: number;
  /** a wave was missed while offline (spawn `offlineWaveDelayTicks` after join) */
  queued: boolean;
  /**
   * Asset freeze: last MCD boundary index already seized in this default episode
   * (0 = the seizure at DEFAULT itself; −1 = none yet / freeze not active when it defaulted).
   */
  freezeMark: number;
}

export function emptyRecord(): LoanRecord {
  return {
    status: 'none',
    product: -1,
    principal: 0,
    due: 0,
    owed: 0,
    rate: 0,
    issueTick: 0,
    deadlineTick: 0,
    owedAtDeadline: 0,
    feeDays: 0,
    goodStanding: 0,
    cooldownUntil: 0,
    warned: [],
    wave: 0,
    nextWaveTick: 0,
    queued: false,
    freezeMark: -1,
  };
}

/** Repair a persisted record (unknown shape, corrupt fields) into a valid one. */
export function normalizeRecord(raw: unknown): LoanRecord {
  const base = emptyRecord();
  if (!raw || typeof raw !== 'object') return base;
  const r = raw as Record<string, unknown>;
  const num = (k: keyof LoanRecord): number => {
    const v = r[k];
    return typeof v === 'number' && Number.isFinite(v) ? v : (base[k] as number);
  };
  const status: LoanStatus = r.status === 'active' || r.status === 'default' ? r.status : 'none';
  const rec: LoanRecord = {
    status,
    product: num('product'),
    principal: Math.max(0, Math.floor(num('principal'))),
    due: Math.max(0, Math.floor(num('due'))),
    owed: Math.max(0, Math.floor(num('owed'))),
    rate: num('rate'),
    issueTick: num('issueTick'),
    deadlineTick: num('deadlineTick'),
    owedAtDeadline: Math.max(0, Math.floor(num('owedAtDeadline'))),
    feeDays: Math.max(0, Math.floor(num('feeDays'))),
    goodStanding: Math.max(0, Math.floor(num('goodStanding'))),
    cooldownUntil: num('cooldownUntil'),
    warned: Array.isArray(r.warned) ? r.warned.filter((x): x is number => typeof x === 'number') : [],
    wave: Math.max(0, Math.floor(num('wave'))),
    nextWaveTick: num('nextWaveTick'),
    queued: r.queued === true,
    freezeMark: Math.max(-1, Math.floor(num('freezeMark'))),
  };
  if (rec.status !== 'none' && rec.owed <= 0) return closeRecord(rec);
  return rec;
}

/** Back to NONE, keeping the history fields (goodStanding, cooldown). */
function closeRecord(rec: LoanRecord): LoanRecord {
  return { ...emptyRecord(), goodStanding: rec.goodStanding, cooldownUntil: rec.cooldownUntil };
}

// ---- taking ---------------------------------------------------------------------------

export type TakeError = 'one_at_a_time' | 'in_default' | 'cooldown' | 'vip';

/** Anti-abuse §5.8.1: one loan at a time, none in default or during the cooldown; VIP gate. */
export function takeError(rec: LoanRecord, product: Product, tier: number, now: number): TakeError | undefined {
  if (rec.status === 'default') return 'in_default';
  if (rec.status === 'active') return 'one_at_a_time';
  if (now < rec.cooldownUntil) return 'cooldown';
  if (tier < product.minTier) return 'vip';
  return undefined;
}

/** Open a loan (caller checked takeError). deadline = issue + days × MCD. */
export function takeLoan(rec: LoanRecord, product: Product, rate: number, now: number): LoanRecord {
  const due = dueAmount(product.principal, rate);
  return {
    ...emptyRecord(),
    goodStanding: rec.goodStanding,
    cooldownUntil: rec.cooldownUntil,
    status: 'active',
    product: product.index,
    principal: product.principal,
    due,
    owed: due,
    rate,
    issueTick: now,
    deadlineTick: now + product.days * MCD,
  };
}

// ---- paying ---------------------------------------------------------------------------

export interface PayResult {
  rec: LoanRecord;
  /** chips actually applied (≤ owed) */
  paid: number;
  closed: boolean;
  /** closed while ACTIVE (before the deadline) -> goodStanding + 1 */
  onTime: boolean;
  /** closed from DEFAULT -> goodStanding = 0 + cooldown */
  fromDefault: boolean;
}

/** Apply a payment of up to `amount` chips (§5.3). Early repayment does not reduce interest. */
export function applyPayment(rec: LoanRecord, amount: number, now: number, cooldownDays: number): PayResult {
  const paid = Math.max(0, Math.min(Math.floor(amount), rec.owed));
  if (rec.status === 'none' || paid <= 0) return { rec, paid: 0, closed: false, onTime: false, fromDefault: false };
  const owed = rec.owed - paid;
  if (owed > 0) return { rec: { ...rec, owed }, paid, closed: false, onTime: false, fromDefault: false };
  if (rec.status === 'active') {
    return { rec: { ...closeRecord(rec), goodStanding: rec.goodStanding + 1 }, paid, closed: true, onTime: true, fromDefault: false };
  }
  return {
    rec: { ...closeRecord(rec), goodStanding: 0, cooldownUntil: now + Math.max(0, cooldownDays) * MCD },
    paid,
    closed: true,
    onTime: false,
    fromDefault: true,
  };
}

// ---- time -----------------------------------------------------------------------------

export interface AdvanceConfig {
  /** late fee per overdue MCD for the current band */
  lateFee: number;
  /** owed ≤ due × capMultiplier */
  capMultiplier: number;
  /** warnings: ticks before the deadline, e.g. [24000, 2400] */
  warningTicks: readonly number[];
}

export interface AdvanceEvents {
  /** thresholds (ticks before the deadline) whose warning is due now */
  warnings: number[];
  /** the loan just went ACTIVE -> DEFAULT */
  defaulted: boolean;
  /** late fees charged now, in order */
  lateFees: number[];
}

/** Number of whole MCD boundaries passed since the deadline (0 before/at deadline + 1 MCD). */
export const boundariesPassed = (deadline: number, now: number): number => (now < deadline ? 0 : Math.floor((now - deadline) / MCD));

/** First MCD boundary after `now` (deadline + k × MCD, k ≥ 1). */
export const nextBoundary = (deadline: number, now: number): number => deadline + (boundariesPassed(deadline, now) + 1) * MCD;

/** Late fee for one boundary: ceil(owedAtDeadline × fee). */
export const lateFeeAmount = (owedAtDeadline: number, fee: number): number => (fee > 0 ? ceilSafe(owedAtDeadline * fee) : 0);

/**
 * Bring a record up to `now`: deadline warnings, ACTIVE -> DEFAULT, and every late fee for the
 * boundaries passed (works for offline catch-up too: several fees at once).
 */
export function advance(rec: LoanRecord, now: number, cfg: AdvanceConfig): { rec: LoanRecord; events: AdvanceEvents } {
  const events: AdvanceEvents = { warnings: [], defaulted: false, lateFees: [] };
  if (rec.status === 'none') return { rec, events };
  let r = rec;
  if (r.status === 'active') {
    if (now >= r.deadlineTick) {
      r = { ...r, status: 'default', owedAtDeadline: r.owed, feeDays: 0, wave: 0, freezeMark: -1, queued: false, nextWaveTick: 0 };
      events.defaulted = true;
    } else {
      const left = r.deadlineTick - now;
      const term = r.deadlineTick - r.issueTick;
      // Thresholds not shorter than the whole term would fire right after signing: skip them.
      const due = [...new Set(cfg.warningTicks)].filter((w) => w > 0 && w < term && left <= w && !r.warned.includes(w)).sort((a, b) => b - a);
      if (due.length) {
        r = { ...r, warned: [...r.warned, ...due] };
        // Only the most urgent one is worth telling (the others are stale after offline time).
        events.warnings.push(due[due.length - 1]!);
      }
    }
  }
  if (r.status === 'default') {
    const n = boundariesPassed(r.deadlineTick, now);
    const cap = Math.floor(r.due * cfg.capMultiplier);
    let owed = r.owed;
    for (let k = r.feeDays + 1; k <= n; k++) {
      const fee = Math.min(lateFeeAmount(r.owedAtDeadline, cfg.lateFee), Math.max(0, cap - owed));
      if (fee > 0) {
        owed += fee;
        events.lateFees.push(fee);
      }
    }
    if (n > r.feeDays || owed !== r.owed) r = { ...r, owed, feeDays: Math.max(n, r.feeDays) };
  }
  return { rec: r, events };
}

/**
 * Asset Freeze schedule (§5.6): one seizure at DEFAULT and one at every later MCD boundary.
 * Returns how many seizures are due now. If the freeze starts mid-default (difficulty switched
 * to Peaceful) the first seizure is at the next boundary.
 */
export function freezeSteps(rec: LoanRecord, now: number, justDefaulted: boolean): { rec: LoanRecord; seizures: number } {
  if (rec.status !== 'default') return { rec, seizures: 0 };
  const n = boundariesPassed(rec.deadlineTick, now);
  if (rec.freezeMark < 0) {
    if (justDefaulted) return { rec: { ...rec, freezeMark: n }, seizures: n + 1 };
    return { rec: { ...rec, freezeMark: n }, seizures: 0 };
  }
  if (n <= rec.freezeMark) return { rec, seizures: 0 };
  return { rec: { ...rec, freezeMark: n }, seizures: n - rec.freezeMark };
}

// ---- garnishment, seizure, admin --------------------------------------------------------

/** Share of a chip credit that goes to the debt while in default: floor(credit × pct/100), ≤ owed. */
export function garnishAmount(credit: number, percent: number, owed: number): number {
  if (credit <= 0 || owed <= 0 || percent <= 0) return 0;
  const p = Math.min(100, percent);
  return Math.min(owed, p >= 100 ? Math.floor(credit) : Math.floor((credit * p) / 100));
}

/** Asset freeze / repossession: min(owed, floor(balance × pct/100)). */
export function seizeAmount(balance: number, percent: number, owed: number): number {
  if (balance <= 0 || owed <= 0 || percent <= 0) return 0;
  return Math.min(owed, Math.floor((balance * Math.min(100, percent)) / 100));
}

/**
 * Credit reasons that are NOT garnished on the balance change: refunds, the player's own
 * chip items deposited at the cashier, admin grants, the loan module's own payouts, returned
 * poker buy-ins (`poker.stake_return`; the winnings above the buy-in are `poker.cashout`), and
 * house-banked round returns (`<game>.payout` / `.pawn` / `.soul`, and `core.offline` which
 * carries rounds settled while offline). A round return includes the player's own stake, so
 * those are garnished on the settled round instead, on the net winnings only
 * ({@link garnishableWinnings}).
 */
export function isGarnishable(reason: string): boolean {
  if (reason.startsWith('loan.')) return false;
  if (reason.endsWith('.refund')) return false;
  // The player's own chips coming back (poker buy-in returned at cash-out): only the net
  // winnings above it (`poker.cashout`) are income.
  if (reason.endsWith('.stake_return')) return false;
  if (reason.endsWith('.payout') || reason.endsWith('.pawn') || reason.endsWith('.soul')) return false;
  return reason !== 'core.cashier.deposit' && reason !== 'core.admin.give' && reason !== 'core.offline';
}

/**
 * The garnishable part of a settled house-banked round: the net winnings (payout − stake) when
 * positive, else 0. A push (stake returned) or a partial return is never garnished. PvP rounds
 * are not counted here (their pot credits go through the balance change).
 */
export function garnishableWinnings(ev: { houseBanked: boolean; staked: number; totalReturn: number }): number {
  if (!ev.houseBanked) return 0;
  const net = Math.floor(ev.totalReturn) - Math.floor(ev.staked);
  return net > 0 ? net : 0;
}

/**
 * Operator `debt <player> set <n>`: 0 clears the debt (no standing change); on a player without
 * a loan a positive amount opens an ACTIVE admin debt due in `days` MCD.
 */
export function adminSetDebt(rec: LoanRecord, owed: number, now: number, days = 3): LoanRecord {
  const n = Math.max(0, Math.floor(owed));
  if (n === 0) return closeRecord(rec);
  if (rec.status === 'none') {
    return { ...emptyRecord(), goodStanding: rec.goodStanding, cooldownUntil: rec.cooldownUntil, status: 'active', product: -1, principal: n, due: n, owed: n, issueTick: now, deadlineTick: now + days * MCD };
  }
  return { ...rec, owed: n, due: Math.max(rec.due, rec.status === 'active' ? n : rec.due) };
}

/** Operator helper: move the deadline to now so the loan defaults on the next tick. */
export function adminForceDefault(rec: LoanRecord, now: number): LoanRecord {
  return rec.status === 'active' ? { ...rec, deadlineTick: now } : rec;
}

/**
 * Casino mode (or loans) was off for `gap` ticks: nothing happens while dormant (§2.1), so every
 * pending timer moves forward by the dormant time instead of firing at once on re-enable.
 */
export function shiftTimers(rec: LoanRecord, gap: number): LoanRecord {
  if (gap <= 0) return rec;
  return {
    ...rec,
    issueTick: rec.status === 'none' ? rec.issueTick : rec.issueTick + gap,
    deadlineTick: rec.status === 'none' ? rec.deadlineTick : rec.deadlineTick + gap,
    nextWaveTick: rec.nextWaveTick > 0 ? rec.nextWaveTick + gap : 0,
    cooldownUntil: rec.cooldownUntil > 0 ? rec.cooldownUntil + gap : 0,
  };
}
