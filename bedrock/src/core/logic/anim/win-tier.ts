/**
 * Win tiers with per-game threshold tables (global.md §2.4 ⚠ CHANGED by the lead decision; twin of Java
 * `core.anim.{WinTier, WinTierTable, TierWords}`). Computed by the server side of the script and passed to
 * the presentation; never re-derived by a presenter.
 */
export const WIN_TIERS = ['LOSS', 'RETURN', 'PUSH', 'WIN', 'NICE', 'BIG', 'MEGA', 'EPIC', 'JACKPOT'] as const;
export type WinTier = (typeof WIN_TIERS)[number];

export const tierOrdinal = (t: WinTier): number => WIN_TIERS.indexOf(t);
export const isWinTier = (t: WinTier): boolean => tierOrdinal(t) >= tierOrdinal('WIN');
export const isOverlayTier = (t: WinTier): boolean => tierOrdinal(t) >= tierOrdinal('BIG');

/** Thresholds for NICE, BIG, MEGA, EPIC (multiples of the stake; 0 = none) and their net floors. */
export interface WinTierTable {
  readonly multiples: readonly [number, number, number, number];
  readonly minNet: readonly [number, number, number, number];
  /** Net that always means EPIC (core.bigWinThreshold), 0 = off. */
  readonly epicNet: number;
  /** return = stake is PUSH (tables) — slots: 1× is a WIN. */
  readonly evenIsPush: boolean;
}

export const DEFAULT_TIERS: WinTierTable = { multiples: [0, 10, 25, 50], minNet: [0, 100, 250, 500], epicNet: 5000, evenIsPush: true };
export const SLOT_TIERS: WinTierTable = { multiples: [5, 15, 40, 100], minNet: [0, 0, 0, 0], epicNet: 0, evenIsPush: false };

const THRESHOLD_TIERS: readonly WinTier[] = ['NICE', 'BIG', 'MEGA', 'EPIC'];

export function reachesTier(table: WinTierTable, t: WinTier, ret: number, stake: number): boolean {
  const i = THRESHOLD_TIERS.indexOf(t);
  if (i < 0) return false;
  const net = ret - stake;
  if (t === 'EPIC' && table.epicNet > 0 && net >= table.epicNet) return true;
  const m = table.multiples[i]!;
  return m > 0 && ret >= m * stake && net >= table.minNet[i]!;
}

export function winTierOf(ret: number, stake: number, table: WinTierTable, jackpot = false, floor?: WinTier): WinTier {
  if (jackpot) return 'JACKPOT';
  if (ret <= 0) return 'LOSS';
  if (ret < stake) return 'RETURN';
  if (ret === stake && table.evenIsPush) return 'PUSH';
  let tier: WinTier = 'WIN';
  for (const t of ['EPIC', 'MEGA', 'BIG', 'NICE'] as const) {
    if (reachesTier(table, t, ret, stake)) {
      tier = t;
      break;
    }
  }
  if (floor && isWinTier(floor) && tierOrdinal(floor) > tierOrdinal(tier)) return floor;
  return tier;
}

/** Roll-up upgrade points `[tier, amount]` above `start` up to `final` (global.md §2.6). */
export function upgradePoints(table: WinTierTable, stake: number, start: WinTier, final: WinTier): Array<[WinTier, number]> {
  const out: Array<[WinTier, number]> = [];
  THRESHOLD_TIERS.forEach((t, i) => {
    if (tierOrdinal(t) <= tierOrdinal(start) || tierOrdinal(t) > tierOrdinal(final)) return;
    const m = table.multiples[i]!;
    if (m <= 0) return;
    out.push([t, Math.max(m * stake, table.minNet[i]! + stake)]);
  });
  return out;
}

/** Lang keys per tier, supplied by the calling game (slots.md §0.3 D4). */
export type TierWords = Readonly<Record<WinTier, string>> & { readonly maxWin?: string };

export const CORE_TIER_WORDS: TierWords = {
  LOSS: 'gui.burmaldaholic.fx.tier.loss',
  RETURN: 'gui.burmaldaholic.fx.returned',
  PUSH: 'gui.burmaldaholic.fx.tier.push',
  WIN: 'gui.burmaldaholic.fx.tier.win',
  NICE: 'gui.burmaldaholic.fx.tier.nice',
  BIG: 'gui.burmaldaholic.fx.tier.big',
  MEGA: 'gui.burmaldaholic.fx.tier.mega',
  EPIC: 'gui.burmaldaholic.fx.tier.epic',
  JACKPOT: 'gui.burmaldaholic.fx.tier.jackpot',
};
