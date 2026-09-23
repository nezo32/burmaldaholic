import { describe, expect, it } from 'vitest';
import { applyOwnerLimits, dayOf, effectiveMin, emptyStats, newOwnedTable, parseLimits, profit, recordRake, recordRound, statsFor } from './tables';

describe('owner limits form', () => {
  it('empty fields mean no owner limit', () => {
    expect(parseLimits('', ' ', 50_000)).toEqual({ ok: true, min: undefined, max: undefined });
  });
  it('parses numbers with spaces', () => {
    expect(parseLimits('10', '1 000', 50_000)).toEqual({ ok: true, min: 10, max: 1000 });
  });
  it('rejects junk, zero and min > max', () => {
    expect(parseLimits('abc', '', 50_000)).toEqual({ ok: false, error: 'invalid' });
    expect(parseLimits('0', '', 50_000)).toEqual({ ok: false, error: 'invalid' });
    expect(parseLimits('-5', '', 50_000)).toEqual({ ok: false, error: 'invalid' });
    expect(parseLimits('500', '100', 50_000)).toEqual({ ok: false, error: 'min_over_max' });
  });
  it('max may not exceed the global table max', () => {
    expect(parseLimits('', '60000', 50_000)).toEqual({ ok: false, error: 'over_global', limit: 50_000 });
    expect(parseLimits('', '50000', 50_000)).toEqual({ ok: true, min: undefined, max: 50_000 });
  });
});

describe('applyOwnerLimits', () => {
  it('only tightens the game limits', () => {
    expect(applyOwnerLimits({ min: 5, tableMax: 1000 }, { min: 2, max: 5000 })).toEqual({ min: 5, tableMax: 1000 });
    expect(applyOwnerLimits({ min: 5, tableMax: 1000 }, { min: 20, max: 200 })).toEqual({ min: 20, tableMax: 200 });
    expect(applyOwnerLimits({ tierMultiplier: 5 }, { max: 300 })).toEqual({ tierMultiplier: 5, tableMax: 300 });
    expect(applyOwnerLimits({ min: 1 }, undefined)).toEqual({ min: 1 });
  });
  it('effectiveMin', () => {
    expect(effectiveMin({})).toBe(1);
    expect(effectiveMin({ min: 25 })).toBe(25);
    expect(effectiveMin({ min: 2 }, 10)).toBe(10);
  });
  it('new tables are open with bots', () => {
    expect(newOwnedTable('c1', 'p', 'poker')).toMatchObject({ casinoId: 'c1', ownerId: 'p', game: 'poker', open: true, bots: true });
  });
});

describe('stats', () => {
  it('records handle, payouts and profit', () => {
    let s = recordRound(undefined, 3, 100, 0);
    s = recordRound(s, 3, 50, 100);
    s = recordRake(s, 3, 7);
    expect(s.today).toEqual({ handle: 150, paid: 100, rake: 7, rounds: 2 });
    expect(profit(s.today)).toBe(57);
    expect(s.total).toEqual(s.today);
  });
  it('a new day resets today but keeps totals', () => {
    let s = recordRound(undefined, 3, 100, 0);
    s = recordRound(s, 4, 10, 20);
    expect(s.day).toBe(4);
    expect(s.today).toEqual({ handle: 10, paid: 20, rake: 0, rounds: 1 });
    expect(s.total).toEqual({ handle: 110, paid: 20, rake: 0, rounds: 2 });
    expect(profit(s.today)).toBe(-10);
    expect(statsFor(s, 9).today.handle).toBe(0);
    expect(statsFor(undefined, 1)).toEqual(emptyStats(1));
  });
  it('does not mutate its input', () => {
    const a = recordRound(undefined, 1, 10, 0);
    const b = recordRound(a, 1, 10, 0);
    expect(a.today.handle).toBe(10);
    expect(b.today.handle).toBe(20);
  });
  it('dayOf', () => {
    expect(dayOf(0)).toBe(0);
    expect(dayOf(23_999)).toBe(0);
    expect(dayOf(24_000)).toBe(1);
  });
});
