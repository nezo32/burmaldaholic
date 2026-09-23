/**
 * Pots, side pots, uncalled bets, rake and odd-chip splitting (GAME_DESIGN §7.3). PURE.
 * Players are referred to by index; `totals[i]` = chips player i put in this hand.
 */

export interface Pot {
  amount: number;
  /** non-folded players who can win it */
  eligible: number[];
  /** everyone who put chips into it (folded players included) */
  contributors: number[];
}

/**
 * Uncalled excess of the largest contribution: the part above the second-largest contribution
 * goes back to its owner before pots are built. undefined when the top bet was matched.
 */
export function uncalledBet(totals: readonly number[]): { player: number; amount: number } | undefined {
  let top = -1;
  let second = 0;
  for (let i = 0; i < totals.length; i++) {
    const v = totals[i]!;
    if (top < 0 || v > totals[top]!) {
      if (top >= 0) second = Math.max(second, totals[top]!);
      top = i;
    } else second = Math.max(second, v);
  }
  if (top < 0) return undefined;
  const amount = totals[top]! - second;
  return amount > 0 ? { player: top, amount } : undefined;
}

/**
 * Build the main pot and side pots: levels = distinct contributions of non-folded players,
 * ascending; pot(L) = Σ min(c, L) − previous levels, eligible = non-folded with c ≥ L. Folded
 * chips above the top level fall into the last pot. Adjacent pots with the same eligible set
 * are merged. Call after removing the uncalled bet.
 */
export function buildPots(totals: readonly number[], folded: readonly boolean[]): Pot[] {
  const levels = [...new Set(totals.filter((c, i) => !folded[i] && c > 0))].sort((a, b) => a - b);
  const pots: Pot[] = [];
  let prev = 0;
  let assigned = 0;
  const grand = totals.reduce((s, c) => s + c, 0);
  levels.forEach((level, li) => {
    let amount = 0;
    const contributors: number[] = [];
    const eligible: number[] = [];
    totals.forEach((c, i) => {
      const part = Math.min(c, level) - Math.min(c, prev);
      if (part > 0) {
        amount += part;
        contributors.push(i);
      }
      if (!folded[i] && c >= level) eligible.push(i);
    });
    if (li === levels.length - 1) {
      // folded chips above the top live level (cannot happen after uncalledBet, but stay safe)
      totals.forEach((c, i) => {
        if (c > level && !contributors.includes(i)) contributors.push(i);
      });
      amount = grand - assigned;
    }
    assigned += amount;
    prev = level;
    const last = pots[pots.length - 1];
    if (last && sameSet(last.eligible, eligible)) {
      last.amount += amount;
      for (const c of contributors) if (!last.contributors.includes(c)) last.contributors.push(c);
    } else if (amount > 0) {
      pots.push({ amount, eligible, contributors });
    }
  });
  if (!pots.length && grand > 0) {
    // everyone folded (should not happen: someone always remains) - give it to nobody-safe bucket
    pots.push({ amount: grand, eligible: [], contributors: totals.flatMap((c, i) => (c > 0 ? [i] : [])) });
  }
  return pots;
}

function sameSet(a: readonly number[], b: readonly number[]): boolean {
  return a.length === b.length && a.every((x) => b.includes(x));
}

export interface RakeConfig {
  /** 0.05 = 5 % */
  percent: number;
  /** cap in big blinds */
  capBb: number;
  /** no flop, no drop */
  noFlopNoDrop: boolean;
}

/**
 * Rake for one pot: `min(floor(pot × percent), capBb × BB)`, only when the hand saw a flop
 * (with no-flop-no-drop) and at least 2 humans contributed to that pot (pots where a single
 * human faces bots are never raked - the bots are the house).
 */
export function rakeFor(amount: number, humanContributors: number, sawFlop: boolean, bb: number, cfg: RakeConfig): number {
  if (humanContributors < 2) return 0;
  if (cfg.noFlopNoDrop && !sawFlop) return 0;
  const r = Math.min(Math.floor(amount * cfg.percent), Math.max(0, cfg.capBb) * bb);
  return Math.max(0, Math.min(amount, r));
}

/**
 * Split `amount` between `winners` (already ordered clockwise starting left of the button).
 * Odd chips go one at a time to the first winners in that order.
 */
export function splitPot(amount: number, winners: readonly number[]): number[] {
  if (!winners.length) return [];
  const base = Math.floor(amount / winners.length);
  let odd = amount - base * winners.length;
  return winners.map(() => base + (odd-- > 0 ? 1 : 0));
}

/** Seat distance clockwise from the button: 0 = first seat left of the button. */
export const orderFromButton = (i: number, button: number, n: number): number => (i - button - 1 + n * 2) % n;
