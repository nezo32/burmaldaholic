import { describe, expect, it } from 'vitest';
import { addBuyIn, clearLive, liveEntry, liveKey, normalizeStacks, park, parkedKey, setLive, splitCashOut, takePayable } from './stacks';

const T1 = 'overworld|1,2,3';
const T2 = 'overworld|9,9,9';

describe('poker stack store (review M1)', () => {
  it('reads legacy { id: amount } stores as payable parked entries', () => {
    const s = normalizeStacks({ A: 500, B: 0, C: 'x', D: { p: 'D', t: T1, a: 70, b: 50 } });
    expect(Object.keys(s).sort()).toEqual(['A', 'D']);
    const r = takePayable(s, 'A', () => false);
    expect(r).toMatchObject({ amount: 500, buyIn: 500 });
    expect(normalizeStacks(undefined)).toEqual({});
  });

  it('a new buy-in never overwrites a parked cash-out', () => {
    // A disconnected mid-hand; the seat was removed while offline -> parked X = 800.
    let s = addBuyIn({}, 'A', T1, 400);
    s = setLive(s, 'A', T1, 800);
    s = park(s, 'A', T1, 7, 800);
    expect(s[liveKey('A', T1)]).toBeUndefined();
    expect(s[parkedKey('A', T1, 7)]).toEqual({ p: 'A', t: T1, a: 800, b: 400 });
    // A buys in again at the same table and at another one: both stay separate.
    s = addBuyIn(s, 'A', T1, 300);
    s = addBuyIn(s, 'A', T2, 200);
    expect(s[parkedKey('A', T1, 7)]!.a).toBe(800);
    // On (re)join, parked chips are paid even while seated; live seats stay.
    const r = takePayable(s, 'A', (t) => t === T1 || t === T2);
    expect(r.amount).toBe(800);
    expect(r.buyIn).toBe(400);
    expect(Object.keys(r.rest).sort()).toEqual([liveKey('A', T1), liveKey('A', T2)].sort());
  });

  it('parking onto the same key merges instead of replacing', () => {
    let s = addBuyIn({}, 'A', T1, 100);
    s = park(s, 'A', T1, 3, 150);
    s = addBuyIn(s, 'A', T1, 100);
    s = park(s, 'A', T1, 3, 60);
    expect(s[parkedKey('A', T1, 3)]).toEqual({ p: 'A', t: T1, a: 210, b: 200 });
  });

  it('live entries of a table that is gone (server stop) are payable; other players untouched', () => {
    let s = addBuyIn({}, 'A', T1, 400);
    s = addBuyIn(s, 'B', T1, 400);
    s = setLive(s, 'A', T1, 650);
    const r = takePayable(s, 'A', () => false);
    expect(r).toMatchObject({ amount: 650, buyIn: 400 });
    expect(Object.keys(r.rest)).toEqual([liveKey('B', T1)]);
  });

  it('top-ups add to the buy-in; setLive keeps it; 0 removes the entry', () => {
    let s = addBuyIn({}, 'A', T1, 400);
    s = addBuyIn(s, 'A', T1, 100);
    s = setLive(s, 'A', T1, 450);
    expect(liveEntry(s, 'A', T1)).toEqual({ amount: 450, buyIn: 500 });
    expect(liveEntry(clearLive(s, 'A', T1), 'A', T1)).toEqual({ amount: 0, buyIn: 0 });
    expect(setLive(s, 'A', T1, 0)).toEqual({});
  });

  it('cash-out splits into the returned buy-in and the (garnishable) winnings', () => {
    expect(splitCashOut(1500, 400)).toEqual({ stake: 400, winnings: 1100 });
    expect(splitCashOut(300, 400)).toEqual({ stake: 300, winnings: 0 });
    expect(splitCashOut(0, 400)).toEqual({ stake: 0, winnings: 0 });
  });
});
