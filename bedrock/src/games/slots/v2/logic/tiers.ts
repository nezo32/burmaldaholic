/** Slot tier table + words for the shared celebration API (SLOTS.md §10.1; slots.md §0.3 D4). */
import { SLOT_TIERS, type TierWords, type WinTier, type WinTierTable, winTierOf } from '../../../../core/logic/anim/win-tier';

export const SLOT_TIER_TABLE: WinTierTable = SLOT_TIERS;

export const SLOT_TIER_WORDS: TierWords = {
  LOSS: 'gui.burmaldaholic.slots.win',
  RETURN: 'gui.burmaldaholic.slots.returned',
  PUSH: 'gui.burmaldaholic.slots.win',
  WIN: 'gui.burmaldaholic.slots.win',
  NICE: 'gui.burmaldaholic.slots.tier.nice',
  BIG: 'gui.burmaldaholic.slots.tier.big',
  MEGA: 'gui.burmaldaholic.slots.tier.mega',
  EPIC: 'gui.burmaldaholic.slots.tier.epic',
  JACKPOT: 'gui.burmaldaholic.slots.jackpot.won',
  maxWin: 'gui.burmaldaholic.slots.max_win',
};

/** Tier of a whole spin (excluding progressive awards) from its total in fifths. */
export function slotTier(totalFifths: number, bigWinTiers?: readonly [number, number, number, number]): WinTier {
  const table = bigWinTiers ? { ...SLOT_TIERS, multiples: bigWinTiers } : SLOT_TIERS;
  return winTierOf(totalFifths, 5, table);
}
