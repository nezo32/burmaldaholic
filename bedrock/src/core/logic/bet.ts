/**
 * Bet limits and amount entry (GAME_DESIGN.md §4.2, UI.md §4). PURE.
 * Max bet = min(table max, VIP tier max); the UI never offers amounts outside [min, max] and
 * the server re-validates every bet.
 */

/** VIP tiers (§12). Index = tier number; lang `gui.burmaldaholic.vip.tier.<id>`. */
export const VIP_TIERS = ['bronze', 'silver', 'gold', 'platinum', 'diamond', 'netherite'] as const;
export type VipTierId = (typeof VIP_TIERS)[number];
export const vipTierKey = (tier: number): string => `gui.burmaldaholic.vip.tier.${VIP_TIERS[Math.max(0, Math.min(5, tier))]}`;
/** UI.md §0.1 tier colors. */
export const VIP_COLORS = ['§c', '§7', '§6', '§f', '§b', '§5'] as const;

export interface BetLimits {
  /** table/machine minimum (default 1) */
  min: number;
  /** table maximum (undefined = no table cap) */
  tableMax?: number;
  /** player's VIP tier max bet (already multiplied, e.g. high-roller ×2) */
  tierMax: number;
}

export type BetError =
  | { key: 'gui.burmaldaholic.error.invalid_amount' }
  | { key: 'gui.burmaldaholic.error.bet_too_low'; min: number }
  | { key: 'gui.burmaldaholic.error.bet_too_high'; max: number }
  | { key: 'gui.burmaldaholic.error.table_max'; max: number }
  | { key: 'gui.burmaldaholic.error.insufficient_funds'; balance: number };

/** Effective [min, max] for a player (max < min means the player cannot bet here). */
export function effectiveRange(l: BetLimits): { min: number; max: number } {
  const max = Math.min(l.tierMax, l.tableMax ?? Number.MAX_SAFE_INTEGER);
  return { min: Math.max(1, l.min), max };
}

/** Validate a bet amount. `balance` omitted = skip the funds check. */
export function validateBet(amount: number, l: BetLimits, balance?: number): BetError | undefined {
  if (!Number.isSafeInteger(amount) || amount <= 0) return { key: 'gui.burmaldaholic.error.invalid_amount' };
  const { min } = effectiveRange(l);
  if (amount < min) return { key: 'gui.burmaldaholic.error.bet_too_low', min };
  if (l.tableMax !== undefined && amount > l.tableMax && l.tableMax <= l.tierMax) return { key: 'gui.burmaldaholic.error.table_max', max: l.tableMax };
  if (amount > l.tierMax) return { key: 'gui.burmaldaholic.error.bet_too_high', max: l.tierMax };
  if (balance !== undefined && amount > balance) return { key: 'gui.burmaldaholic.error.insufficient_funds', balance };
  return undefined;
}

/** Parse an amount typed into a text field ('1 000', '250'). undefined = not a whole number. */
export function parseAmount(text: string): number | undefined {
  const s = text.replace(/[\s\u00a0_]/g, '');
  if (!/^\d{1,15}$/.test(s)) return undefined;
  return Number(s);
}

/**
 * Slider step for a bet slider so it has ≤ 100 steps (UI.md §4):
 * step = max(1, ceil((max − min) / 100)) rounded up to 1/5/25/100/500/1000/5000/...
 */
export function sliderStep(min: number, max: number): number {
  const raw = Math.max(1, Math.ceil((max - min) / 100));
  const nice = [1, 5, 25, 100, 500, 1000, 5000, 10000, 50000, 100000, 500000, 1000000];
  for (const n of nice) if (n >= raw) return n;
  return raw;
}
