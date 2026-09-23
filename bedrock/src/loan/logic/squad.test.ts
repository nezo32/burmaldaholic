import { describe, expect, it } from 'vitest';
import { seededRng } from '../../core/logic/rng';
import { MCD, emptyRecord, takeLoan, advance, parseProducts } from './loan';
import { appraisalKey, bestAppraised, planRepossession, VARIANTS } from './repo';
import { classify, findSpawn, freeSlot, horizontalDistance, ringPoint, standY, DEFAULT_SPAWN_RULES, type Cell } from './spawn';
import {
  DEFAULT_TIMERS,
  type SquadObservation,
  answerNegotiation,
  baseComposition,
  newSquad,
  onDebtorJoin,
  partialPayment,
  scaledHealth,
  scheduleFirstWave,
  squadComposition,
  squadMembers,
  stepSquad,
  waveDue,
  waveQueued,
  waveRetry,
  waveSpawned,
} from './squad';

const CFG = { squadMax: 10, escalationMax: 3 };
const total = (c: Record<string, number>): number => Object.values(c).reduce((a, b) => a + b, 0);

describe('squad composition', () => {
  it('base table by owed amount', () => {
    expect(baseComposition(499)).toEqual({ debt_collector: 2, repo_man: 0, accountant: 0, enforcer: 0 });
    expect(baseComposition(500)).toEqual({ debt_collector: 2, repo_man: 1, accountant: 0, enforcer: 0 });
    expect(baseComposition(2999)).toEqual({ debt_collector: 2, repo_man: 1, accountant: 0, enforcer: 0 });
    expect(baseComposition(3000)).toEqual({ debt_collector: 3, repo_man: 2, accountant: 1, enforcer: 0 });
    expect(baseComposition(14_999)).toEqual({ debt_collector: 3, repo_man: 2, accountant: 1, enforcer: 0 });
    expect(baseComposition(15_000)).toEqual({ debt_collector: 4, repo_man: 2, accountant: 1, enforcer: 1 });
  });
  it('difficulty modifier: Easy −1 (min 1), Hard +1', () => {
    expect(squadComposition(100, 1, 'easy', CFG).debt_collector).toBe(1);
    expect(squadComposition(100, 1, 'normal', CFG).debt_collector).toBe(2);
    expect(squadComposition(100, 1, 'hard', CFG).debt_collector).toBe(3);
  });
  it('escalation + min(w − 1, 3)', () => {
    expect(squadComposition(100, 2, 'normal', CFG).debt_collector).toBe(3);
    expect(squadComposition(100, 4, 'normal', CFG).debt_collector).toBe(5);
    expect(squadComposition(100, 9, 'normal', CFG).debt_collector).toBe(5);
    expect(squadComposition(100, 9, 'normal', { ...CFG, escalationMax: 0 }).debt_collector).toBe(2);
  });
  it('hard cap of 10 members (Collectors trimmed first)', () => {
    const c = squadComposition(20_000, 5, 'hard', CFG); // 4+1+3 = 8 collectors + 4 others = 12
    expect(total(c)).toBe(10);
    expect(c).toEqual({ debt_collector: 6, repo_man: 2, accountant: 1, enforcer: 1 });
    const tiny = squadComposition(20_000, 5, 'hard', { ...CFG, squadMax: 2 });
    expect(total(tiny)).toBe(2);
    expect(tiny.accountant).toBe(1);
  });
  it('the leader is spawned first (Accountant, else a Collector)', () => {
    expect(squadMembers(squadComposition(3000, 1, 'normal', CFG))[0]).toBe('accountant');
    expect(squadMembers(squadComposition(100, 1, 'normal', CFG))).toEqual(['debt_collector', 'debt_collector']);
  });
  it('health scaling', () => {
    expect(scaledHealth('debt_collector', 1)).toBe(24);
    expect(scaledHealth('debt_collector', 0.75)).toBe(18);
    expect(scaledHealth('enforcer', 1.25)).toBe(100);
    expect(scaledHealth('accountant', 0.01)).toBe(1);
  });
});

describe('squad state machine', () => {
  const obs = (o: Partial<SquadObservation>): SquadObservation => ({
    now: 0,
    debtorOnline: true,
    sameDimension: true,
    debtorDead: false,
    leaderDistance: 30,
    nearestDistance: 30,
    alive: 3,
    owed: 600,
    attacked: false,
    ...o,
  });

  it('approach -> negotiate when the leader is within 6 blocks', () => {
    let m = newSquad(0);
    expect(stepSquad(m, obs({ now: 20, leaderDistance: 7 })).m.state).toBe('approach');
    const r = stepSquad(m, obs({ now: 40, leaderDistance: 6 }));
    expect(r.m.state).toBe('negotiate');
    expect(r.effects).toEqual([{ type: 'negotiate' }]);
    m = r.m;
    expect(stepSquad(m, obs({ now: 40 + DEFAULT_TIMERS.negotiateTicks })).m.state).toBe('hostile');
  });
  it('approach timeout or being attacked -> hostile', () => {
    expect(stepSquad(newSquad(0), obs({ now: 600 })).effects).toEqual([{ type: 'hostile' }]);
    expect(stepSquad(newSquad(0), obs({ now: 5, attacked: true })).m.state).toBe('hostile');
  });
  it('negotiation answers', () => {
    const neg = { state: 'negotiate' as const, since: 0 };
    expect(answerNegotiation(neg, 'pay_all', true, 5).m).toMatchObject({ state: 'leaving', reason: 'paid' });
    expect(answerNegotiation(neg, 'pay_part', true, 5).m).toMatchObject({ state: 'leaving', reason: 'partial' });
    expect(answerNegotiation(neg, 'pay_all', false, 5).m.state).toBe('hostile');
    expect(answerNegotiation(neg, 'refuse', false, 5).m.state).toBe('hostile');
    expect(answerNegotiation(neg, 'timeout', false, 5).m.state).toBe('hostile');
    expect(answerNegotiation(newSquad(0), 'pay_all', true, 5).effects).toEqual([]);
  });
  it('debt reaching 0 at any time -> PAID (leaving), then despawn after 100 t', () => {
    const hostile = { state: 'hostile' as const, since: 0 };
    const r = stepSquad(hostile, obs({ now: 10, owed: 0 }));
    expect(r.m).toMatchObject({ state: 'leaving', reason: 'paid' });
    expect(stepSquad(r.m, obs({ now: 109, owed: 0 })).m.state).toBe('leaving');
    expect(stepSquad(r.m, obs({ now: 110, owed: 0 })).effects).toEqual([{ type: 'despawn', reason: 'paid' }]);
  });
  it('hostile ends: timeout, debtor died, far for 200 t, dimension, offline, defeated', () => {
    const h = { state: 'hostile' as const, since: 0 };
    expect(stepSquad(h, obs({ now: 6000 })).m).toMatchObject({ state: 'leaving', reason: 'timeout' });
    expect(stepSquad(h, obs({ now: 5, debtorDead: true })).m).toMatchObject({ state: 'leaving', reason: 'debtor_died' });
    let m = stepSquad(h, obs({ now: 100, nearestDistance: 97 })).m;
    expect(m.farSince).toBe(100);
    expect(stepSquad(m, obs({ now: 299, nearestDistance: 120 })).m.state).toBe('hostile');
    expect(stepSquad(m, obs({ now: 300, nearestDistance: 120 })).effects).toEqual([{ type: 'despawn', reason: 'far' }]);
    m = stepSquad(m, obs({ now: 200, nearestDistance: 20 })).m;
    expect(m.farSince).toBeUndefined();
    expect(stepSquad(h, obs({ now: 5, sameDimension: false })).m).toMatchObject({ state: 'done', reason: 'dimension' });
    expect(stepSquad(h, obs({ now: 5, debtorOnline: false })).m).toMatchObject({ state: 'done', reason: 'offline' });
    expect(stepSquad(h, obs({ now: 5, alive: 0 })).m).toMatchObject({ state: 'done', reason: 'defeated' });
  });
  it('partial payment share', () => {
    expect(partialPayment(600, 0.5)).toBe(300);
    expect(partialPayment(601, 0.5)).toBe(301);
    expect(partialPayment(10, 0)).toBe(1);
    expect(partialPayment(10, 1)).toBe(10);
  });
});

describe('wave schedule', () => {
  const p = parseProducts(undefined)[0]!;
  const defaulted = advance(takeLoan(emptyRecord(), p, 0.2, 0), 3 * MCD, { lateFee: 0.1, capMultiplier: 2, warningTicks: [] }).rec;
  it('first wave after the delay, then one per MCD boundary', () => {
    let r = scheduleFirstWave(defaulted, 3 * MCD, 600);
    expect(waveDue({ now: 3 * MCD + 599, nextWaveTick: r.nextWaveTick, hasLiveSquad: false })).toBe(false);
    expect(waveDue({ now: 3 * MCD + 600, nextWaveTick: r.nextWaveTick, hasLiveSquad: false })).toBe(true);
    expect(waveDue({ now: 3 * MCD + 600, nextWaveTick: r.nextWaveTick, hasLiveSquad: true })).toBe(false);
    r = waveSpawned(r, 3 * MCD + 600);
    expect(r).toMatchObject({ wave: 1, nextWaveTick: 4 * MCD });
    r = waveSpawned(r, 4 * MCD + 5);
    expect(r).toMatchObject({ wave: 2, nextWaveTick: 5 * MCD });
    expect(waveRetry(r, 100).nextWaveTick).toBe(700);
  });
  it('offline: one queued wave 1200 t after joining', () => {
    let r = scheduleFirstWave(defaulted, 3 * MCD, 600);
    r = waveQueued(r);
    const j = onDebtorJoin(r, 10 * MCD, 1200);
    expect(j.missed).toBe(true);
    expect(j.rec).toMatchObject({ queued: false, nextWaveTick: 10 * MCD + 1200 });
    const early = onDebtorJoin(scheduleFirstWave(defaulted, 3 * MCD, 600), 3 * MCD + 10, 1200);
    expect(early.missed).toBe(false);
    expect(onDebtorJoin(emptyRecord(), 5, 1200).missed).toBe(false);
  });
});

describe('spawning', () => {
  it('ring points stay within 24–40 blocks', () => {
    const rng = seededRng(3);
    for (let i = 0; i < 2000; i++) {
      const p = ringPoint(rng, { x: 100.5, z: -20.5 }, 24, 40);
      const d = horizontalDistance({ x: p.x + 0.5, z: p.z + 0.5 }, { x: 100.5, z: -20.5 });
      expect(d).toBeGreaterThanOrEqual(23);
      expect(d).toBeLessThanOrEqual(41.5);
    }
  });
  const flat = (groundY: number, liquid = false) => (_x: number, y: number): Cell | undefined => (y < groundY ? (liquid ? 'liquid' : 'solid') : 'air');
  it('stands on a solid block with 2 air above', () => {
    expect(standY(flat(64), 0, 0, 64, DEFAULT_SPAWN_RULES)).toBe(63);
    expect(standY(flat(70), 0, 0, 64, DEFAULT_SPAWN_RULES)).toBe(69);
    expect(standY(flat(90), 0, 0, 64, DEFAULT_SPAWN_RULES)).toBeUndefined();
    expect(standY(flat(64, true), 0, 0, 64, DEFAULT_SPAWN_RULES)).toBeUndefined();
    expect(standY(() => undefined, 0, 0, 64, DEFAULT_SPAWN_RULES)).toBeUndefined();
    // 1-block gap is not enough
    const lowCeiling = (_x: number, y: number): Cell => (y < 64 || y === 65 ? 'solid' : 'air');
    expect(standY(lowCeiling, 0, 0, 64, DEFAULT_SPAWN_RULES)).not.toBe(63);
  });
  it('findSpawn returns a centered feet position or gives up after the attempts', () => {
    const s = findSpawn(seededRng(1), { x: 0, y: 64, z: 0 }, flat(64));
    expect(s?.y).toBe(64);
    expect(Math.abs(s!.x % 1)).toBe(0.5);
    expect(findSpawn(seededRng(1), { x: 0, y: 64, z: 0 }, flat(64, true))).toBeUndefined();
  });
  it('findSpawn skips forbidden spots (claims) and the world border', () => {
    // only spots east of the debtor are allowed
    const s = findSpawn(seededRng(3), { x: 0, y: 64, z: 0 }, flat(64), DEFAULT_SPAWN_RULES, (p) => p.x > 0);
    expect(s && s.x > 0).toBe(true);
    expect(findSpawn(seededRng(3), { x: 0, y: 64, z: 0 }, flat(64), DEFAULT_SPAWN_RULES, () => false)).toBeUndefined();
    const edge = { ...DEFAULT_SPAWN_RULES, maxCoord: 100 };
    for (let i = 0; i < 20; i++) {
      const p = findSpawn(seededRng(i), { x: 95, y: 64, z: 95 }, flat(64), edge);
      if (p) expect(Math.max(Math.abs(p.x), Math.abs(p.z))).toBeLessThanOrEqual(100.5);
    }
  });
  it('debtor slots: lowest free slot, wraps when full', () => {
    expect(freeSlot([], 8)).toBe(0);
    expect(freeSlot([0, 1, 3], 8)).toBe(2);
    expect(freeSlot([0, 1, 2, 3, 4, 5, 6, 7], 8)).toBe(0);
  });
  it('classifies blocks', () => {
    expect(classify('minecraft:stone', false, false)).toBe('solid');
    expect(classify('minecraft:grass_block', false, false)).toBe('solid');
    expect(classify('minecraft:short_grass', false, false)).toBe('passable');
    expect(classify('minecraft:oak_door', false, false)).toBe('passable');
    expect(classify('minecraft:water', false, true)).toBe('liquid');
    expect(classify('minecraft:air', true, false)).toBe('air');
  });
});

describe('repossession', () => {
  const values: Record<string, number> = { 'minecraft:diamond': 20, 'minecraft:elytra': 500, 'minecraft:emerald': 8 };
  const appraisal = (id: string): number => values[id] ?? 0;
  it('picks the highest appraisal × count', () => {
    const best = bestAppraised(
      [
        { slot: 0, typeId: 'minecraft:diamond', amount: 30 },
        { slot: 1, typeId: 'minecraft:elytra', amount: 1 },
        { slot: 2, typeId: 'minecraft:dirt', amount: 64 },
      ],
      appraisal,
    );
    expect(best).toMatchObject({ value: 600, stack: { slot: 0 } });
    expect(bestAppraised([{ slot: 0, typeId: 'minecraft:dirt', amount: 1 }], appraisal)).toBeUndefined();
  });
  it('seizes half the balance, then the item covers the rest (capped at owed)', () => {
    expect(planRepossession(1000, 2000, 50, 600)).toEqual({ seized: 500, itemCredit: 600 });
    expect(planRepossession(1000, 700, 50, 600)).toEqual({ seized: 500, itemCredit: 200 });
    expect(planRepossession(0, 700, 50, 0)).toEqual({ seized: 0, itemCredit: 0 });
  });
  it('appraisal keys and dialogue variant counts', () => {
    expect(appraisalKey('minecraft:diamond')).toBe('wager.appraisal.diamond');
    expect(appraisalKey('burmaldaholic:chip_1')).toBeUndefined();
    expect(VARIANTS['dialog.burmaldaholic.loan.greeting']).toBe(5);
  });
});
