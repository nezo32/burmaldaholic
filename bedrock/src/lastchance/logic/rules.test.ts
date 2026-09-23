import { describe, expect, it } from 'vitest';
import { seededRng } from '../../core/logic/rng';
import {
  DEFAULT_LC_CONFIG,
  type HitInfo,
  type LcConfig,
  type PlayerFacts,
  type WorldFacts,
  cappedHealing,
  chanceFor,
  cooldownFor,
  cooldownRemaining,
  decide,
  flip,
  highStakesEligible,
  isLethal,
  modeFor,
  parseRecord,
  planSuccess,
  readyDue,
  scarredMax,
} from './rules';

const cfg = (over: Partial<LcConfig> = {}): LcConfig => ({ ...DEFAULT_LC_CONFIG, ...over });
const hs = (over: Partial<LcConfig['hardcore']> = {}): LcConfig => cfg({ hardcoreMode: 'HIGH_STAKES', hardcore: { ...DEFAULT_LC_CONFIG.hardcore, ...over } });
const hit = (over: Partial<HitInfo> = {}): HitInfo => ({ damage: 10, health: 4, cause: 'entityAttack', attackerTypes: ['minecraft:zombie'], ...over });
const player = (over: Partial<PlayerFacts> = {}): PlayerFacts => ({
  balance: 1000,
  chipsCarried: 0,
  baseMaxHealth: 20,
  scarHp: 0,
  holdsTotem: false,
  soulWager: false,
  lastUsedAt: undefined,
  ...over,
});
const world = (over: Partial<WorldFacts> = {}): WorldFacts => ({ casinoOn: true, hardcore: false, difficulty: 'normal', now: 100000, ...over });

describe('isLethal', () => {
  it('needs damage >= health', () => {
    expect(isLethal(4, 4)).toBe(true);
    expect(isLethal(5, 4)).toBe(true);
    expect(isLethal(3.9, 4)).toBe(false);
    expect(isLethal(10, 0)).toBe(false); // already dead
  });
});

describe('chances and modes', () => {
  it('uses §2.3 defaults per difficulty', () => {
    expect(chanceFor('peaceful', cfg())).toBe(0.6);
    expect(chanceFor('easy', cfg())).toBe(0.6);
    expect(chanceFor('normal', cfg())).toBe(0.5);
    expect(chanceFor('hard', cfg())).toBe(0.4);
  });
  it('clamps configured chances', () => {
    expect(chanceFor('hard', cfg({ chance: { easy: 2, normal: -1, hard: Number.NaN } }))).toBe(0);
    expect(chanceFor('easy', cfg({ chance: { easy: 2, normal: -1, hard: 0 } }))).toBe(1);
  });
  it('Hardcore is disabled by default', () => {
    expect(modeFor(false, cfg())).toBe('standard');
    expect(modeFor(true, cfg())).toBeUndefined();
    expect(modeFor(true, hs())).toBe('high_stakes');
    expect(modeFor(false, cfg({ enabled: false }))).toBeUndefined();
    expect(modeFor(true, cfg({ enabled: false, hardcoreMode: 'HIGH_STAKES' }))).toBeUndefined();
  });
  it('cooldown per mode', () => {
    expect(cooldownFor('standard', cfg())).toBe(24000);
    expect(cooldownFor('high_stakes', cfg())).toBe(120000);
  });
});

describe('decide', () => {
  it('flips on a lethal hit with the difficulty chance', () => {
    expect(decide(hit(), player(), world(), cfg())).toEqual({ kind: 'flip', mode: 'standard', chance: 0.5, cooldownTicks: 24000 });
    expect(decide(hit(), player(), world({ difficulty: 'hard' }), cfg())).toMatchObject({ chance: 0.4 });
  });
  it('skips in every excluded situation', () => {
    const r = (h: HitInfo, p: PlayerFacts, w: WorldFacts, c: LcConfig) => {
      const d = decide(h, p, w, c);
      return d.kind === 'skip' ? d.reason : 'flip';
    };
    expect(r(hit({ damage: 2 }), player(), world(), cfg())).toBe('not_lethal');
    expect(r(hit(), player(), world({ casinoOn: false }), cfg())).toBe('casino_off');
    expect(r(hit(), player(), world(), cfg({ enabled: false }))).toBe('disabled');
    expect(r(hit(), player(), world({ hardcore: true }), cfg())).toBe('hardcore_disabled');
    expect(r(hit(), player({ holdsTotem: true }), world(), cfg())).toBe('totem');
    for (const cause of ['void', 'override', 'selfDestruct']) expect(r(hit({ cause }), player(), world(), cfg())).toBe('excluded_cause');
    expect(r(hit(), player({ soulWager: true }), world(), cfg())).toBe('soul_wager');
    expect(r(hit(), player({ lastUsedAt: 100000 - 23999 }), world(), cfg())).toBe('cooldown');
    expect(r(hit(), player({ lastUsedAt: 100000 - 24000 }), world(), cfg())).toBe('flip');
  });
  it('collectors only block Last Chance in Hardcore', () => {
    const h = hit({ attackerTypes: ['burmaldaholic:enforcer'] });
    expect(decide(h, player(), world(), cfg()).kind).toBe('flip');
    expect(decide(h, player({ chipsCarried: 500 }), world({ hardcore: true }), hs())).toEqual({ kind: 'skip', reason: 'squad_hardcore' });
    expect(decide(hit({ attackerTypes: ['minecraft:arrow', 'burmaldaholic:repo_man'] }), player(), world({ hardcore: true }), hs())).toMatchObject({ reason: 'squad_hardcore' });
  });
  it('High Stakes: fixed chance, long cooldown, eligibility', () => {
    expect(decide(hit(), player(), world({ hardcore: true, difficulty: 'hard' }), hs())).toEqual({ kind: 'flip', mode: 'high_stakes', chance: 0.5, cooldownTicks: 120000 });
    expect(decide(hit(), player({ balance: 50, chipsCarried: 49 }), world({ hardcore: true }), hs())).toMatchObject({ reason: 'not_eligible' });
    expect(decide(hit(), player({ balance: 50, chipsCarried: 50 }), world({ hardcore: true }), hs()).kind).toBe('flip');
    expect(decide(hit(), player({ scarHp: 13 }), world({ hardcore: true }), hs())).toMatchObject({ reason: 'not_eligible' });
    expect(decide(hit(), player({ scarHp: 12 }), world({ hardcore: true }), hs()).kind).toBe('flip');
    expect(decide(hit(), player({ lastUsedAt: 100000 - 119999 }), world({ hardcore: true }), hs())).toMatchObject({ reason: 'cooldown' });
  });
});

describe('eligibility helpers', () => {
  it('scarredMax never goes below 1', () => {
    expect(scarredMax(20, 4)).toBe(16);
    expect(scarredMax(20, 40)).toBe(1);
    expect(scarredMax(20, -3)).toBe(20);
  });
  it('negative balances do not count toward the stake', () => {
    expect(highStakesEligible({ balance: -500, chipsCarried: 100, baseMaxHealth: 20, scarHp: 0 }, hs())).toBe(true);
  });
});

describe('planSuccess', () => {
  it('standard: 10 % fee floored, half health rounded up', () => {
    expect(planSuccess('standard', cfg(), 1234, 20, 0)).toEqual({ fee: 123, destroyChips: false, addScarHp: 0, newMax: 20, reviveHealth: 10 });
    expect(planSuccess('standard', cfg(), 9, 20, 0).fee).toBe(0);
    expect(planSuccess('standard', cfg(), -50, 20, 0).fee).toBe(0);
    expect(planSuccess('standard', cfg(), 100, 21, 0).reviveHealth).toBe(11);
    expect(planSuccess('standard', cfg({ costPercent: 100 }), 777, 20, 0).fee).toBe(777);
    expect(planSuccess('standard', cfg({ costPercent: 0 }), 777, 20, 0).fee).toBe(0);
  });
  it('standard keeps existing scars', () => {
    expect(planSuccess('standard', cfg(), 0, 20, 4)).toMatchObject({ newMax: 16, reviveHealth: 8 });
  });
  it('high stakes: everything, plus a heart forever, revive at half of the new max', () => {
    expect(planSuccess('high_stakes', hs(), 5000, 20, 0)).toEqual({ fee: 5000, destroyChips: true, addScarHp: 2, newMax: 18, reviveHealth: 9 });
    expect(planSuccess('high_stakes', hs({ heartCost: 3 }), 0, 20, 2)).toMatchObject({ addScarHp: 3, newMax: 15, reviveHealth: 8 });
  });
});

describe('cooldowns and records', () => {
  it('remaining', () => {
    expect(cooldownRemaining(100, undefined, 24000)).toBe(0);
    expect(cooldownRemaining(100, 50, 24000)).toBe(23950);
    expect(cooldownRemaining(30000, 50, 24000)).toBe(0);
  });
  it('ready message is due once', () => {
    expect(readyDue({ usedAt: 0 }, 24000, 24000)).toBe(true);
    expect(readyDue({ usedAt: 0 }, 23999, 24000)).toBe(false);
    expect(readyDue({ usedAt: 0, notified: true }, 30000, 24000)).toBe(false);
    expect(readyDue({}, 30000, 24000)).toBe(false);
    expect(readyDue({ usedAt: 0 }, 30000, 0)).toBe(false); // no cooldown -> no spam
  });
  it('parseRecord is defensive', () => {
    expect(parseRecord(undefined)).toEqual({});
    expect(parseRecord('x')).toEqual({});
    expect(parseRecord({ usedAt: 5, notified: true, scarHp: 4.7 })).toEqual({ usedAt: 5, notified: true, scarHp: 4 });
    expect(parseRecord({ usedAt: 'no', scarHp: -2, notified: 1 })).toEqual({});
  });
  it('cappedHealing', () => {
    expect(cappedHealing(10, 4, 18)).toBe(4);
    expect(cappedHealing(16, 4, 18)).toBe(2);
    expect(cappedHealing(18, 4, 18)).toBe(0);
    expect(cappedHealing(19, 4, 18)).toBe(0);
  });
});

describe('the coin (Monte Carlo)', () => {
  it('success rate matches p within tolerance', () => {
    const rng = seededRng(20260923);
    const n = 200000;
    for (const p of [0.6, 0.5, 0.4]) {
      let heads = 0;
      for (let i = 0; i < n; i++) if (flip(rng, p)) heads++;
      expect(Math.abs(heads / n - p)).toBeLessThan(0.005);
    }
  });
  it('edges are exact', () => {
    const rng = seededRng(1);
    for (let i = 0; i < 1000; i++) {
      expect(flip(rng, 0)).toBe(false);
      expect(flip(rng, 1)).toBe(true);
    }
  });
});
