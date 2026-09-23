import { describe, expect, it } from 'vitest';
import { buildPots, orderFromButton, rakeFor, splitPot, uncalledBet } from './pots';

const RAKE = { percent: 0.05, capBb: 3, noFlopNoDrop: true };

describe('uncalledBet', () => {
  it('returns the excess above the second-largest contribution', () => expect(uncalledBet([100, 300, 50])).toEqual({ player: 1, amount: 200 }));
  it('nothing when matched', () => expect(uncalledBet([200, 200, 50])).toBeUndefined());
  it('counts folded contributions as callers', () => expect(uncalledBet([0, 20, 60])).toEqual({ player: 2, amount: 40 }));
});

describe('buildPots', () => {
  it('single pot when everyone matched', () => {
    expect(buildPots([100, 100, 100], [false, false, false])).toEqual([{ amount: 300, eligible: [0, 1, 2], contributors: [0, 1, 2] }]);
  });

  it('one short all-in makes a side pot', () => {
    const pots = buildPots([50, 200, 200], [false, false, false]);
    expect(pots).toEqual([
      { amount: 150, eligible: [0, 1, 2], contributors: [0, 1, 2] },
      { amount: 300, eligible: [1, 2], contributors: [1, 2] },
    ]);
  });

  it('three all-in levels', () => {
    const pots = buildPots([25, 100, 250, 250], [false, false, false, false]);
    expect(pots.map((p) => p.amount)).toEqual([100, 225, 300]);
    expect(pots.map((p) => p.eligible)).toEqual([
      [0, 1, 2, 3],
      [1, 2, 3],
      [2, 3],
    ]);
    expect(pots.reduce((a, p) => a + p.amount, 0)).toBe(625);
  });

  it('folded chips go in but folded players are never eligible', () => {
    const pots = buildPots([80, 30, 80, 10], [false, false, true, true]);
    // levels from live players: 30, 80
    expect(pots).toEqual([
      { amount: 30 + 30 + 30 + 10, eligible: [0, 1], contributors: [0, 1, 2, 3] },
      { amount: 50 + 50, eligible: [0], contributors: [0, 2] },
    ]);
  });

  it('merges levels with the same eligible set (folded player in between)', () => {
    const pots = buildPots([100, 100, 60], [false, false, true]);
    expect(pots).toEqual([{ amount: 260, eligible: [0, 1], contributors: [0, 1, 2] }]);
  });

  it('total chips are conserved', () => {
    const totals = [13, 400, 77, 400, 250, 0];
    const folded = [true, false, false, false, true, true];
    expect(buildPots(totals, folded).reduce((a, p) => a + p.amount, 0)).toBe(totals.reduce((a, b) => a + b, 0));
  });
});

describe('rake', () => {
  it('5 % floored', () => expect(rakeFor(199, 2, true, 10, RAKE)).toBe(9));
  it('capped at 3 BB', () => expect(rakeFor(10_000, 3, true, 10, RAKE)).toBe(30));
  it('no flop, no drop', () => expect(rakeFor(500, 2, false, 10, RAKE)).toBe(0));
  it('pre-flop rake when the option is off', () => expect(rakeFor(500, 2, false, 10, { ...RAKE, noFlopNoDrop: false })).toBe(25));
  it('never with fewer than 2 human contributors (the bots are the house)', () => {
    expect(rakeFor(500, 1, true, 10, RAKE)).toBe(0);
    expect(rakeFor(500, 0, true, 10, RAKE)).toBe(0);
  });
  it('zero percent', () => expect(rakeFor(500, 2, true, 10, { ...RAKE, percent: 0 })).toBe(0));
  it('rake is at most 5 % of any pot', () => {
    for (let pot = 1; pot < 5000; pot += 37) expect(rakeFor(pot, 2, true, 50, RAKE)).toBeLessThanOrEqual(pot * 0.05);
  });
});

describe('splitPot', () => {
  it('even split', () => expect(splitPot(100, [3, 1])).toEqual([50, 50]));
  it('odd chip to the first winner left of the button', () => expect(splitPot(101, [2, 0])).toEqual([51, 50]));
  it('three-way with two odd chips', () => expect(splitPot(302, [1, 2, 3])).toEqual([101, 101, 100]));
  it('order from button', () => {
    // button at 2 of 4: seat 3 is first, then 0, 1, 2
    expect([3, 0, 1, 2].map((i) => orderFromButton(i, 2, 4))).toEqual([0, 1, 2, 3]);
  });
});
