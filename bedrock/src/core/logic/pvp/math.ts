/** Pure PvP money math (PVP.md §3.4, §6.1). Integer only; same vectors as Java PvpMath (§16.1). */

/** rake = floor((pot × bp + 5000) / 10000) — round half up. */
export const rake = (pot: number, basisPoints: number): number => (pot <= 0 || basisPoints <= 0 ? 0 : Math.floor((pot * basisPoints + 5000) / 10000));

/** floor(W / k) each; the W mod k odd chips one each to the winners earliest in `seatOrder`. */
export function split(w: number, winners: readonly number[], seatOrder: readonly number[], n: number): number[] {
  const out = new Array<number>(n).fill(0);
  const k = winners.length;
  if (k === 0 || w <= 0) return out;
  const each = Math.floor(w / k);
  let odd = w - each * k;
  const isWinner = new Set(winners);
  for (const i of winners) out[i] = each;
  for (const i of seatOrder) {
    if (odd <= 0) break;
    if (isWinner.has(i)) {
      out[i]! += 1;
      odd--;
    }
  }
  return out;
}

/** Wheel Party: owner of u ∈ [0, P) with slices of size stakes[i] in join order. */
export function sliceOwner(stakes: readonly number[], u: number): number {
  let c = 0;
  for (let i = 0; i < stakes.length; i++) {
    c += stakes[i]!;
    if (u < c) return i;
  }
  throw new Error('u outside the wheel');
}

export const pot = (stakes: readonly number[]): number => stakes.reduce((a, b) => a + b, 0);
