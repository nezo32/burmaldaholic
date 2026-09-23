/**
 * Independent tester suite: Texas Hold'em (§7.2 ranking, §7.3 pots / rake / odd chip).
 * The evaluator is checked against a brute-force reference written here from the spec text.
 */
import { describe, expect, it } from 'vitest';
import { seededRng } from '../../src/core/logic/rng';
import { pcs, rankOf, suitOf, type PCard } from '../../src/games/poker/logic/cards';
import { categoryOf, evaluate, handName } from '../../src/games/poker/logic/evaluator';
import { buildPots, rakeFor, splitPot, uncalledBet } from '../../src/games/poker/logic/pots';

/** Reference: score a 5-card hand as a comparable array [category, tiebreak ranks...]. */
function score5(cards: PCard[]): number[] {
  const ranks = cards.map(rankOf).sort((a, b) => b - a);
  const flush = new Set(cards.map(suitOf)).size === 1;
  const uniq = [...new Set(ranks)];
  let straightHigh = 0;
  if (uniq.length === 5 && ranks[0]! - ranks[4]! === 4) straightHigh = ranks[0]!;
  if (uniq.length === 5 && ranks.join() === '14,5,4,3,2') straightHigh = 5; // the wheel
  const groups = uniq.map((r) => [ranks.filter((x) => x === r).length, r] as [number, number]).sort((a, b) => b[0] - a[0] || b[1] - a[1]);
  const byGroup = groups.map((g) => g[1]);
  if (straightHigh && flush) return [8, straightHigh];
  if (groups[0]![0] === 4) return [7, ...byGroup];
  if (groups[0]![0] === 3 && groups[1]![0] === 2) return [6, ...byGroup];
  if (flush) return [5, ...ranks];
  if (straightHigh) return [4, straightHigh];
  if (groups[0]![0] === 3) return [3, ...byGroup];
  if (groups[0]![0] === 2 && groups[1]![0] === 2) return [2, ...byGroup];
  if (groups[0]![0] === 2) return [1, ...byGroup];
  return [0, ...ranks];
}
const cmp = (a: number[], b: number[]) => {
  for (let i = 0; i < Math.max(a.length, b.length); i++) if ((a[i] ?? 0) !== (b[i] ?? 0)) return Math.sign((a[i] ?? 0) - (b[i] ?? 0));
  return 0;
};
function best7(cards: PCard[]): number[] {
  let best: number[] = [-1];
  for (let i = 0; i < 7; i++)
    for (let j = i + 1; j < 7; j++) {
      const five = cards.filter((_, k) => k !== i && k !== j);
      const s = score5(five);
      if (cmp(s, best) > 0) best = s;
    }
  return best;
}

describe('poker evaluator §7.2', () => {
  it('orders 3000 random 7-card showdowns exactly like the brute-force reference', () => {
    const rng = seededRng(2026);
    let mismatches = 0;
    for (let n = 0; n < 3000; n++) {
      const deck = Array.from({ length: 52 }, (_, i) => i);
      for (let i = deck.length - 1; i > 0; i--) {
        const j = Math.floor(rng.next() * (i + 1));
        [deck[i], deck[j]] = [deck[j]!, deck[i]!];
      }
      const board = deck.slice(0, 5);
      const a = [...board, ...deck.slice(5, 7)];
      const b = [...board, ...deck.slice(7, 9)];
      const want = cmp(best7(a), best7(b));
      const got = Math.sign(evaluate(a) - evaluate(b));
      if (want !== got) mismatches++;
      if (categoryOf(evaluate(a)) !== best7(a)[0]) mismatches++;
    }
    expect(mismatches).toBe(0);
  });
  it('named edge cases', () => {
    expect(handName(evaluate(pcs('As Ks Qs Js Ts 2d 3c')))).toBe('royal_flush');
    // wheel is a 5-high straight: loses to a 6-high straight
    expect(evaluate(pcs('Ah 2d 3c 4s 5h Kd Qc'))).toBeLessThan(evaluate(pcs('2d 3c 4s 5h 6d Kd Qc')));
    // steel wheel is a straight flush, beats quads
    expect(evaluate(pcs('Ah 2h 3h 4h 5h Kd Kc'))).toBeGreaterThan(evaluate(pcs('Kh Kd Ks Kc 2d 3c 9s')));
    // suits never break ties: identical best five -> equal value
    expect(evaluate(pcs('As Ks Qd Jc 9h 2d 3c'))).toBe(evaluate(pcs('Ad Kd Qs Jh 9c 2s 3h')));
    // board plays: both players' hole cards irrelevant -> split
    const board = 'Ts Js Qs Ks As';
    expect(evaluate(pcs(`${board} 2d 3c`))).toBe(evaluate(pcs(`${board} 4h 5h`)));
    // kicker decides a pair
    expect(evaluate(pcs('Ah Ad Kc 7s 5d 3h 2c'))).toBeGreaterThan(evaluate(pcs('As Ac Qc 7d 5c 3s 2d')));
    // three pairs: best two pairs + best kicker (the third pair's rank can be the kicker)
    expect(evaluate(pcs('Kh Kd Qc Qs 9d 9h 2c'))).toBe(evaluate(pcs('Ks Kc Qh Qd 9s 8h 2d')));
    // two trips = full house of the higher trips over the lower
    expect(handName(evaluate(pcs('9h 9d 9c 5s 5d 5h 2c')))).toBe('full_house');
    // a flush beats a straight on the same board
    expect(evaluate(pcs('2h 7h 9h Jh Kh Tc Qd'))).toBeGreaterThan(evaluate(pcs('9c Tc Jd Qd Kc 2s 3s')));
  });
});

describe('poker pots §7.3', () => {
  it('uncalled excess returns to its owner before pots are built', () => {
    expect(uncalledBet([100, 40, 40])).toEqual({ player: 0, amount: 60 });
    expect(uncalledBet([50, 50])).toBeUndefined();
  });
  it('three-way all-in: main pot + side pot, eligibility by contribution', () => {
    // A all-in 50, B all-in 120, C calls 120 (matched: no uncalled)
    const pots = buildPots([50, 120, 120], [false, false, false]);
    expect(pots.map((p) => p.amount)).toEqual([150, 140]);
    expect(pots[0]!.eligible.sort()).toEqual([0, 1, 2]);
    expect(pots[1]!.eligible.sort()).toEqual([1, 2]);
  });
  it('folded chips go into the pots but folded players are never eligible', () => {
    // D folded after putting 30; A all-in 20; B and C 80
    const pots = buildPots([20, 80, 80, 30], [false, false, false, true]);
    expect(pots.reduce((s, p) => s + p.amount, 0)).toBe(210);
    expect(pots[0]).toMatchObject({ amount: 80, eligible: [0, 1, 2] });
    expect(pots[1]!.eligible).toEqual([1, 2]);
    expect(pots.every((p) => !p.eligible.includes(3))).toBe(true);
  });
  it('conserves chips on 500 random contribution vectors', () => {
    const rng = seededRng(7);
    for (let n = 0; n < 500; n++) {
      const k = 2 + Math.floor(rng.next() * 5);
      const totals = Array.from({ length: k }, () => Math.floor(rng.next() * 200));
      const folded = totals.map(() => rng.next() < 0.3);
      if (folded.every(Boolean)) folded[0] = false;
      const unc = uncalledBet(totals);
      if (unc) totals[unc.player]! -= unc.amount;
      const pots = buildPots(totals, folded);
      expect(pots.reduce((s, p) => s + p.amount, 0)).toBe(totals.reduce((s, c) => s + c, 0));
    }
  });
  it('odd chips go to the first winners left of the button', () => {
    expect(splitPot(101, [3, 1])).toEqual([51, 50]);
    expect(splitPot(100, [0, 1, 2])).toEqual([34, 33, 33]);
  });
  it('rake = min(floor(5 % of pot), 3 BB); no flop no drop; ≥ 2 human contributors', () => {
    const cfg = { percent: 0.05, capBb: 3, noFlopNoDrop: true };
    expect(rakeFor(99, 2, true, 10, cfg)).toBe(4);
    expect(rakeFor(10_000, 2, true, 10, cfg)).toBe(30);
    expect(rakeFor(1000, 2, false, 10, cfg)).toBe(0);
    expect(rakeFor(1000, 1, true, 10, cfg)).toBe(0); // one human vs bots
  });
});
