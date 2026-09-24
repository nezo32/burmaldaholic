import { describe, expect, it } from 'vitest';
import { seededRng } from '../../../core/logic/rng';
import { DEFAULT_RULES, type SeatInfo } from './rules';
import { CrapsTable } from './table';

const seatsOf = (t: CrapsTable, ids: string[]): SeatInfo[] => ids.map((id, i) => ({ id, seat: i + 1, hasLineBet: t.hasLineBet(id) }));

describe('CrapsTable placement rules', () => {
  it('line bets only on the come-out, come bets only with a point', () => {
    const t = new CrapsTable(DEFAULT_RULES);
    expect(t.placeError('a', 'pass')).toBeUndefined();
    expect(t.placeError('a', 'come')).toBe('needs_point');
    expect(t.placeError('a', 'dont_come')).toBe('needs_point');
    t.addBet('a', 'pass', 10);
    expect(t.placeError('a', 'pass')).toBe('already_placed');
    expect(t.placeError('a', 'dont_pass')).toBeUndefined();
    t.roll([3, 3]); // point 6
    expect(t.point).toBe(6);
    expect(t.placeError('a', 'dont_pass')).toBe('line_only_come_out');
    expect(t.placeError('a', 'come')).toBeUndefined();
    t.addBet('a', 'come', 5);
    expect(t.placeError('a', 'come')).toBe('already_placed');
    t.roll([4, 5]); // come moves to 9
    expect(t.placeError('a', 'come')).toBeUndefined();
    expect(t.betsOf('a').find((b) => b.kind === 'come')?.point).toBe(9);
  });

  it('field bets are one-roll', () => {
    const t = new CrapsTable(DEFAULT_RULES);
    t.addBet('a', 'field', 10);
    expect(t.placeError('a', 'field')).toBe('already_placed');
    const r = t.roll([1, 1]);
    expect(r.resolutions[0]).toMatchObject({ outcome: 'win', totalReturn: 30 });
    expect(t.bets).toEqual([]);
  });
});

describe('CrapsTable odds', () => {
  it('pass odds need a point and respect multiples and 3-4-5x', () => {
    const t = new CrapsTable(DEFAULT_RULES);
    const b = t.addBet('a', 'pass', 10);
    expect(t.oddsError(b, 10)).toEqual({ code: 'no_point' });
    expect(t.oddsTargets('a')).toEqual([]);
    t.roll([2, 4]); // point 6
    const info = t.oddsInfo(b)!;
    expect(info).toMatchObject({ side: 'take', point: 6, unit: 5, max: 50, room: 50, off: false });
    expect(t.oddsError(b, 7)).toEqual({ code: 'multiple', unit: 5 });
    expect(t.oddsError(b, 55)).toEqual({ code: 'max', max: 50 });
    expect(t.oddsError(b, 20)).toBeUndefined();
    t.addOdds(b.id, 20);
    expect(t.oddsInfo(b)!.room).toBe(30);
    t.addOdds(b.id, 30);
    expect(t.oddsTargets('a')).toEqual([]);
    const r = t.roll([5, 1]); // point made
    expect(r.event.kind).toBe('point_made');
    expect(r.resolutions[0]).toMatchObject({ outcome: 'win', totalReturn: 20 + 50 + 60 });
    expect(r.pointsInRow).toBe(1);
  });

  it("lay odds behind don't pass", () => {
    const t = new CrapsTable(DEFAULT_RULES);
    const b = t.addBet('a', 'dont_pass', 10);
    t.roll([1, 3]); // point 4
    expect(t.oddsInfo(b)).toMatchObject({ side: 'lay', unit: 2, max: 60 });
    t.addOdds(b.id, 60);
    const r = t.roll([3, 4]);
    expect(r.resolutions[0]).toMatchObject({ outcome: 'win', totalReturn: 20 + 60 + 30 });
  });

  it('come odds are marked off during the come-out and returned on a 7', () => {
    const t = new CrapsTable(DEFAULT_RULES);
    t.addBet('a', 'pass', 10);
    t.roll([4, 4]); // point 8
    const c = t.addBet('a', 'come', 10);
    t.roll([2, 3]); // come -> 5
    t.addOdds(c.id, 20);
    t.roll([4, 4]); // point made -> come-out
    expect(t.comeOut).toBe(true);
    expect(t.oddsInfo(t.bet(c.id)!)).toMatchObject({ off: true });
    const r = t.roll([3, 4]); // natural 7 on come-out: pass (new) none; come on 5 loses flat, odds back
    const res = r.resolutions.find((x) => x.bet.id === c.id)!;
    expect(res).toMatchObject({ outcome: 'lose', totalReturn: 20, oddsReturned: true });
  });
});

describe('CrapsTable shooter', () => {
  it('single player is always the shooter and needs a line bet on the come-out', () => {
    const t = new CrapsTable(DEFAULT_RULES);
    t.ensureShooter(seatsOf(t, ['a']));
    expect(t.shooter).toBe('a');
    expect(t.canRoll()).toBe(false);
    t.addBet('a', 'pass', 1);
    expect(t.canRoll()).toBe(true);
    t.roll([3, 3], seatsOf(t, ['a']));
    t.roll([3, 4], seatsOf(t, ['a'])); // seven-out
    expect(t.shooter).toBe('a');
    expect(t.comeOut).toBe(true);
  });

  it('dice pass clockwise on a seven-out, not on a point made', () => {
    const t = new CrapsTable(DEFAULT_RULES);
    const ids = ['a', 'b', 'c'];
    t.addBet('a', 'pass', 1);
    t.addBet('b', 'pass', 1);
    t.ensureShooter(seatsOf(t, ids));
    expect(t.shooter).toBe('a');
    t.roll([2, 2], seatsOf(t, ids)); // point 4
    t.roll([2, 2], seatsOf(t, ids)); // made
    expect(t.shooter).toBe('a');
    expect(t.pointsInRow).toBe(1);
    t.addBet('a', 'pass', 1);
    t.roll([5, 5], seatsOf(t, ids)); // point 10
    const r = t.roll([6, 1], seatsOf(t, ids)); // seven-out
    expect(r.sevenOut).toBe(true);
    expect(r.shooter).toBe('a');
    expect(t.shooter).toBe('b');
    expect(t.pointsInRow).toBe(0);
  });

  it('come-out shooter without a line bet keeps the dice until passed on', () => {
    const t = new CrapsTable(DEFAULT_RULES);
    t.shooter = 'b';
    t.addBet('c', 'pass', 1);
    expect(t.ensureShooter(seatsOf(t, ['a', 'b', 'c']), false)).toBe(false);
    expect(t.shooter).toBe('b');
    expect(t.ensureShooter(seatsOf(t, ['a', 'b', 'c']))).toBe(true);
    expect(t.shooter).toBe('c');
  });

  it('a shooter who leaves mid-point hands the dice to the next seated player', () => {
    const t = new CrapsTable(DEFAULT_RULES);
    t.addBet('a', 'pass', 1);
    t.addBet('b', 'pass', 1);
    t.ensureShooter(seatsOf(t, ['a', 'b']));
    t.roll([3, 2], seatsOf(t, ['a', 'b'])); // point 5
    const left = t.removeOwner('a');
    expect(left).toHaveLength(1);
    t.ensureShooter(seatsOf(t, ['b']));
    expect(t.shooter).toBe('b');
    expect(t.point).toBe(5);
    expect(t.canRoll()).toBe(true);
    t.ensureShooter([]);
    expect(t.shooter).toBeUndefined();
  });
});

describe('drawn point bets (review M1)', () => {
  it('only bets with an established point are drawn; the play-out does not change the table', () => {
    const t = new CrapsTable(DEFAULT_RULES);
    t.addBet('a', 'pass', 10);
    t.addBet('b', 'field', 10);
    expect(t.pointBets()).toEqual([]); // come-out: nothing decided yet -> refundable
    expect(t.playOut(seededRng(1)).size).toBe(0);
    t.roll([2, 2]); // point 4
    const come = t.addBet('a', 'come', 5);
    expect(t.pointBets().map((b) => b.kind)).toEqual(['pass']); // the new come bet is undrawn
    t.roll([4, 5]); // come moves to 9
    t.addOdds(come.id, 5);
    const before = JSON.stringify(t.bets);
    const res = t.playOut(seededRng(2));
    expect([...res.keys()].sort()).toEqual(t.pointBets().map((b) => b.id).sort());
    expect(JSON.stringify(t.bets)).toBe(before);
    expect(t.point).toBe(4);
    // Pass on 4 returns 0 or 2 × flat; come on 9 with 5 odds returns 0 or 10 + 5 + 7 (3:2)
    const pass = t.pointBets().find((b) => b.kind === 'pass')!;
    expect([0, 20]).toContain(res.get(pass.id));
    expect([0, 22]).toContain(res.get(come.id));
  });

  it('the play-out follows the true odds (Pass on a point of 4 wins 1/3)', () => {
    const t = new CrapsTable(DEFAULT_RULES);
    const pass = t.addBet('a', 'pass', 10);
    t.roll([1, 3]);
    const rng = seededRng(9);
    let wins = 0;
    const n = 6000;
    for (let i = 0; i < n; i++) if ((t.playOut(rng).get(pass.id) ?? 0) > 0) wins++;
    expect(wins / n).toBeGreaterThan(0.3);
    expect(wins / n).toBeLessThan(0.367);
  });
});
