import { describe, expect, it } from 'vitest';
import { effectiveRange, parseAmount, sliderStep, validateBet, vipTierKey } from './bet';
import { formatClock, formatDhm, formatNumber, formatSigned } from './format';
import { SegmentList } from './hud-status';
import { dismissSetup, parseSetup, shouldShowSetup } from './mode';
import { SeatRegistry, tableKeyOf, within } from './sessions';
import { activeHearts, cappedMaxHealth, checkHeartStake, checkItemStake, checkXpStake, pawnSettlement, soulValue, xpAtLevel, xpPointsForLevels, xpStakeValue } from './wager-math';

describe('bet limits', () => {
  const l = { min: 1, tableMax: 1000, tierMax: 100 };
  it('validates', () => {
    expect(validateBet(50, l, 500)).toBeUndefined();
    expect(validateBet(0, l)).toEqual({ key: 'gui.burmaldaholic.error.invalid_amount' });
    expect(validateBet(2.5, l)).toEqual({ key: 'gui.burmaldaholic.error.invalid_amount' });
    expect(validateBet(150, l)).toEqual({ key: 'gui.burmaldaholic.error.bet_too_high', max: 100 });
    expect(validateBet(150, { min: 1, tableMax: 120, tierMax: 250 })).toEqual({ key: 'gui.burmaldaholic.error.table_max', max: 120 });
    expect(validateBet(5, { min: 10, tierMax: 100 })).toEqual({ key: 'gui.burmaldaholic.error.bet_too_low', min: 10 });
    expect(validateBet(60, l, 50)).toEqual({ key: 'gui.burmaldaholic.error.insufficient_funds', balance: 50 });
  });
  it('range', () => expect(effectiveRange(l)).toEqual({ min: 1, max: 100 }));
  it('slider step', () => {
    expect(sliderStep(1, 100)).toBe(1);
    expect(sliderStep(1, 1000)).toBe(25);
    expect(sliderStep(100, 50000)).toBe(500);
  });
  it('parses amounts', () => {
    expect(parseAmount('1 000')).toBe(1000);
    expect(parseAmount('-5')).toBeUndefined();
    expect(parseAmount('abc')).toBeUndefined();
  });
  it('tier keys', () => expect(vipTierKey(2)).toBe('gui.burmaldaholic.vip.tier.gold'));
});

describe('format', () => {
  it('groups from 10 000', () => {
    expect(formatNumber(2500)).toBe('2500');
    expect(formatNumber(12500)).toBe('12 500');
    expect(formatNumber(1000000)).toBe('1 000 000');
    expect(formatNumber(-50)).toBe('−50');
    expect(formatNumber(0.5)).toBe('0.5');
    expect(formatSigned(150)).toBe('+150');
  });
  it('clock', () => {
    expect(formatClock(3600)).toBe('03:00');
    expect(formatClock(20 * 3725)).toBe('1:02:05');
    expect(formatDhm(24000 + 1200 * 15)).toEqual([1, '00:15']);
  });
});

describe('pawn stakes', () => {
  it('xp values (vanilla curve)', () => {
    expect(xpAtLevel(16)).toBe(352);
    expect(xpAtLevel(30)).toBe(1395);
    expect(xpPointsForLevels(30, 30)).toBe(1395);
    expect(xpStakeValue(30, 5, 4)).toBe(Math.floor((1395 - xpAtLevel(25)) / 4));
    expect(checkXpStake(3, 5, 30)).toEqual({ ok: false, key: 'gui.burmaldaholic.error.xp_not_enough' });
    expect(checkXpStake(40, 31, 30).ok).toBe(false);
  });
  it('hearts cap and min 10 HP', () => {
    const o = { maxPerBet: 3, maxTotal: 5, active: 3, baseMaxHealth: 20 };
    expect(checkHeartStake(2, o).ok).toBe(true);
    expect(checkHeartStake(3, o)).toEqual({ ok: false, key: 'gui.burmaldaholic.error.hearts_cap' });
    expect(checkHeartStake(4, { ...o, active: 0 }).ok).toBe(false);
    expect(checkHeartStake(1, { ...o, active: 0, baseMaxHealth: 10 }).ok).toBe(false);
    expect(activeHearts([{ hearts: 2, until: 10 }, { hearts: 1, until: 100 }], 50)).toBe(1);
    expect(cappedMaxHealth(20, 3)).toBe(14);
    expect(cappedMaxHealth(20, 9)).toBe(10);
  });
  it('items', () => {
    expect(checkItemStake({ appraisal: 20, count: 3, damaged: false, enchanted: false, named: false })).toEqual({ ok: true, value: 60 });
    expect(checkItemStake({ appraisal: 0, count: 1, damaged: false, enchanted: false, named: false }).ok).toBe(false);
    expect(checkItemStake({ appraisal: 500, count: 1, damaged: true, enchanted: false, named: false })).toEqual({ ok: false, key: 'gui.burmaldaholic.error.pawn_damaged' });
  });
  it('settlement', () => {
    expect(pawnSettlement(100, 196)).toEqual({ returnPawn: true, chips: 96 });
    expect(pawnSettlement(100, 100)).toEqual({ returnPawn: true, chips: 0 });
    expect(pawnSettlement(100, 50)).toEqual({ returnPawn: false, chips: 50 });
    expect(soulValue(300, 1000)).toBe(1000);
    expect(soulValue(5000, 1000)).toBe(5000);
  });
});

describe('seats', () => {
  it('seats, busy, full, rejoin', () => {
    const r = new SeatRegistry();
    expect(r.join('a', 't1', 2)).toEqual({ ok: true, seat: 1, rejoined: false });
    expect(r.join('b', 't1', 2)).toEqual({ ok: true, seat: 2, rejoined: false });
    expect(r.join('c', 't1', 2)).toEqual({ ok: false, reason: 'full' });
    expect(r.join('a', 't2', 2)).toEqual({ ok: false, reason: 'busy' });
    expect(r.join('a', 't1', 2)).toEqual({ ok: true, seat: 1, rejoined: true });
    r.leave('a');
    expect(r.join('c', 't1', 2).ok && r.of('c')?.seat).toBe(1);
    expect(r.at('t1').map((x) => x.playerId)).toEqual(['c', 'b']);
  });
  it('keys and distance', () => {
    expect(tableKeyOf('minecraft:overworld', { x: 1.5, y: 64, z: -2.2 })).toBe('minecraft:overworld|1,64,-3');
    expect(within({ x: 0, y: 0, z: 0 }, { x: 3, y: 4, z: 0 }, 5)).toBe(true);
    expect(within({ x: 0, y: 0, z: 0 }, { x: 3, y: 4, z: 1 }, 5)).toBe(false);
  });
});

describe('hud segments', () => {
  it('orders, replaces and skips hidden/failing segments', () => {
    const l = new SegmentList<string, string>();
    l.add({ id: 'b', order: 10, render: () => 'B' });
    l.add({ id: 'a', order: 0, render: (p) => `A:${p}` });
    l.add({ id: 'x', order: 5, render: () => undefined });
    l.add({ id: 'e', order: 7, render: () => { throw new Error('boom'); } });
    const errs: string[] = [];
    expect(l.render('p', (id) => errs.push(id))).toEqual(['A:p', 'B']);
    expect(errs).toEqual(['e']);
    l.add({ id: 'b', order: 1, render: () => 'B2' });
    expect(l.render('p')).toEqual(['A:p', 'B2']);
  });
});

describe('setup form state', () => {
  it('shows to ops until answered or dismissed 3 times', () => {
    let s = parseSetup(undefined);
    expect(shouldShowSetup(s, 'op', true)).toBe(true);
    expect(shouldShowSetup(s, 'x', false)).toBe(false);
    for (let i = 0; i < 3; i++) s = dismissSetup(s, 'op');
    expect(shouldShowSetup(s, 'op', true)).toBe(false);
    expect(shouldShowSetup(parseSetup('{"done":true}'), 'op', true)).toBe(false);
    expect(parseSetup('garbage').done).toBe(false);
  });
});
