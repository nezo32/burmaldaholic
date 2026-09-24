/** Money rules for rounds against MONEY bots (BOTS.md §5.1, §5.3, §5.4). PURE, integer, floor. */

export const mcDay = (worldTime: number): number => Math.floor(worldTime / 24000);

/** Heat threshold: max(min, multiple × tierMax). */
export const dailyCap = (tierMax: number, capMin: number, tierMultiple: number): number => Math.max(capMin, tierMultiple * Math.max(0, tierMax));

/** VIP / `wager` contract credit: floor(wagered × (1 − (1 − weight) × botShare)). */
export const vipCredit = (wagered: number, weight: number, botShare: number): number => Math.floor(wagered * (1 - (1 - weight) * botShare));

/** Poker, one pot: floor(won × botContrib / pot) − floor(contrib × botWon / pot). */
export function pokerPotNet(humanWon: number, humanContrib: number, botContrib: number, botWon: number, pot: number): number {
  if (pot <= 0) return 0;
  return Math.floor((humanWon * botContrib) / pot) - Math.floor((humanContrib * botWon) / pot);
}

/** PvP: n > 0 → floor(n × botStakes / otherStakes); n < 0 → floor(n × botPayouts / otherPayouts). */
export function pvpNet(net: number, botStakes: number, otherStakes: number, botPayouts: number, otherPayouts: number): number {
  if (net > 0) return otherStakes <= 0 ? 0 : Math.floor((net * botStakes) / otherStakes);
  if (net < 0) return otherPayouts <= 0 ? 0 : Math.floor((net * botPayouts) / otherPayouts);
  return 0;
}

/** PvP at an owned anchor: the bots' share of the rake goes to the bank sink, the rest to the bankroll. */
export function pvpRakeSplit(rake: number, botStakes: number, pot: number): { bank: number; bankroll: number } {
  const bank = pot <= 0 ? 0 : Math.floor((rake * botStakes) / pot);
  return { bank, bankroll: rake - bank };
}

/** Poker rake excluding bot chips (BOTS.md §5.1): only when ≥ 2 humans contributed. */
export function pokerRake(pot: number, botContrib: number, humanContributors: number, rakePercent: number, capChips: number): number {
  if (humanContributors < 2) return 0;
  return Math.min(Math.floor((pot - botContrib) * rakePercent), capChips);
}
