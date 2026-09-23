import { describe, expect, it } from 'vitest';
import { seededRng } from '../../core/logic/rng';
import {
  BUFFS,
  CHAOS_EVENTS,
  CURSES,
  DEFAULT_WEIGHTS,
  EVENT_KIND,
  LETHAL_EFFECTS,
  chipShowerSplit,
  cooldownReady,
  diamondRainCount,
  isBigWin,
  isChaosEvent,
  mobWaveComposition,
  mobWaveSize,
  nextWeather,
  pickEvent,
  resolveEvent,
  rollEffect,
  rollRange,
  splitEven,
  sunsetDue,
} from './events';
import { GH_WARN_TICKS, INITIAL_GH, canStart, goldenBonus, isActive, startState } from './golden-hour';
import { type BlockInfo, type LandingColumn, type PlayerSnapshot, evaluate, isSafeLanding, isSolidGround, randomOffset, safeDropSurface } from './safety';
import { streakSegment } from './streak-hud';

const b = (typeId: string, o: Partial<BlockInfo> = {}): BlockInfo => ({ typeId, isAir: typeId === 'minecraft:air', isLiquid: /water|lava/.test(typeId), ...o });
const AIR = b('minecraft:air');
const STONE = b('minecraft:stone');

describe('event catalog (§13.2)', () => {
  it('has the nine events with spec weights (sum 100)', () => {
    expect(CHAOS_EVENTS).toHaveLength(9);
    expect(Object.values(DEFAULT_WEIGHTS).reduce((a, x) => a + x, 0)).toBe(100);
    expect(DEFAULT_WEIGHTS.golden_hour).toBe(2);
    expect(EVENT_KIND.mob_wave).toBe('bad');
    expect(isChaosEvent('mob_wave')).toBe(true);
    expect(isChaosEvent('nope')).toBe(false);
  });

  it('Monte-Carlo: ambient picks follow the weights', () => {
    const rng = seededRng(42);
    const counts = new Map<string, number>();
    const N = 200_000;
    for (let i = 0; i < N; i++) {
      const e = pickEvent(rng, DEFAULT_WEIGHTS) as string;
      counts.set(e, (counts.get(e) ?? 0) + 1);
    }
    for (const e of CHAOS_EVENTS) expect(Math.abs((counts.get(e) ?? 0) / N - DEFAULT_WEIGHTS[e] / 100)).toBeLessThan(0.005);
  });

  it('weight 0 / exclusions / nothing left', () => {
    const rng = seededRng(1);
    for (let i = 0; i < 1000; i++) expect(pickEvent(rng, { ...DEFAULT_WEIGHTS, curse: 0 }, ['mob_wave'])).not.toMatch(/curse|mob_wave/);
    expect(pickEvent(rng, {})).toBeUndefined();
    expect(pickEvent(rng, { curse: 5 }, ['curse'])).toBeUndefined();
  });

  it('mob_wave in Peaceful is rerolled from the remaining events (never GH, never mob_wave)', () => {
    const rng = seededRng(7);
    for (let i = 0; i < 2000; i++) {
      const e = resolveEvent(rng, 'mob_wave', { peaceful: true, overworld: true }, DEFAULT_WEIGHTS);
      expect(e).toBeDefined();
      expect(e).not.toBe('mob_wave');
      expect(e).not.toBe('golden_hour');
    }
    expect(resolveEvent(rng, 'mob_wave', { peaceful: false, overworld: true }, DEFAULT_WEIGHTS)).toBe('mob_wave');
    // outside the overworld the reroll cannot pick weather
    for (let i = 0; i < 2000; i++) expect(resolveEvent(rng, 'mob_wave', { peaceful: true, overworld: false }, DEFAULT_WEIGHTS)).not.toBe('weather_change');
  });

  it('weather outside the Overworld rerolls once from good events', () => {
    const rng = seededRng(9);
    for (let i = 0; i < 2000; i++) {
      const e = resolveEvent(rng, 'weather_change', { peaceful: false, overworld: false }, DEFAULT_WEIGHTS);
      expect(e && EVENT_KIND[e]).toBe('good');
      expect(e).not.toBe('golden_hour');
    }
    expect(resolveEvent(rng, 'weather_change', { peaceful: false, overworld: true }, DEFAULT_WEIGHTS)).toBe('weather_change');
    expect(resolveEvent(rng, 'weather_change', { peaceful: false, overworld: false }, { weather_change: 5, curse: 5 })).toBeUndefined();
  });
});

describe('buffs, curses, amounts', () => {
  it('never lethal effects', () => {
    for (const e of [...BUFFS, ...CURSES]) expect(LETHAL_EFFECTS).not.toContain(e.id);
    expect(BUFFS.find((e) => e.id === 'speed')?.amplifier).toBe(1);
    expect(BUFFS.find((e) => e.id === 'regeneration')?.amplifier).toBe(0);
  });
  it('durations within config range', () => {
    const rng = seededRng(3);
    for (let i = 0; i < 1000; i++) {
      const r = rollEffect(rng, BUFFS, 1200, 3600);
      expect(r.ticks).toBeGreaterThanOrEqual(1200);
      expect(r.ticks).toBeLessThanOrEqual(3600);
    }
    expect(rollRange(rng, 10, 5)).toBeGreaterThanOrEqual(5);
    expect(rollRange(rng, -5, -1)).toBe(0);
  });
  it('mob wave sizes (§2.3)', () => {
    const c = { easy: 3, normal: 4, hard: 6 };
    expect(mobWaveSize('Peaceful', false, c)).toBe(0);
    expect(mobWaveSize('Easy', false, c)).toBe(3);
    expect(mobWaveSize('Normal', false, c)).toBe(4);
    expect(mobWaveSize('Hard', false, c)).toBe(6);
    expect(mobWaveSize('Normal', true, c)).toBe(6);
  });
  it('wave composition: equal split, no creepers, per dimension', () => {
    const rng = seededRng(5);
    const w = mobWaveComposition(rng, 'overworld', 6);
    expect(w).toHaveLength(6);
    for (const m of ['minecraft:zombie', 'minecraft:skeleton', 'minecraft:spider']) expect(w.filter((x) => x === m)).toHaveLength(2);
    expect(mobWaveComposition(rng, 'nether', 4).every((m) => /skeleton|magma_cube/.test(m))).toBe(true);
    expect(mobWaveComposition(rng, 'the_end', 3).every((m) => /endermite|skeleton/.test(m))).toBe(true);
    for (const d of ['overworld', 'nether', 'the_end'] as const) expect(mobWaveComposition(rng, d, 12)).not.toContain('minecraft:creeper');
  });
  it('diamond rain 3-6, Hard 2-5', () => {
    const rng = seededRng(11);
    for (let i = 0; i < 500; i++) {
      const n = diamondRainCount(rng, 3, 6, false);
      expect(n >= 3 && n <= 6).toBe(true);
      const h = diamondRainCount(rng, 3, 6, true);
      expect(h >= 2 && h <= 5).toBe(true);
    }
  });
  it('chip shower split keeps the exact total; Monte-Carlo mean ≈ 60', () => {
    const rng = seededRng(13);
    let sum = 0;
    const N = 20000;
    for (let i = 0; i < N; i++) {
      const amount = rollRange(rng, 20, 100);
      const { fives, ones } = chipShowerSplit(rng, amount);
      expect(fives * 5 + ones).toBe(amount);
      expect(fives).toBeGreaterThanOrEqual(0);
      expect(ones).toBeGreaterThanOrEqual(0);
      sum += amount;
    }
    expect(Math.abs(sum / N - 60)).toBeLessThan(0.6);
  });
  it('splitEven', () => {
    expect(splitEven(100, 6)).toEqual([17, 17, 17, 17, 16, 16]);
    expect(splitEven(0, 3)).toEqual([0, 0, 0]);
    expect(splitEven(5, 0)).toEqual([5]);
  });
  it('weather cycle', () => {
    expect(nextWeather('Clear')).toBe('Rain');
    expect(nextWeather('Rain')).toBe('Thunder');
    expect(nextWeather('Thunder')).toBe('Clear');
  });
});

describe('triggers (§13.1)', () => {
  it('big win: net ≥ 50 × stake and ≥ 500', () => {
    expect(isBigWin(10, 500, 50, 500)).toBe(true);
    expect(isBigWin(10, 499, 50, 500)).toBe(false);
    expect(isBigWin(20, 900, 50, 500)).toBe(false);
    expect(isBigWin(0, 10000, 50, 500)).toBe(false);
    expect(isBigWin(10, -5, 50, 500)).toBe(false);
  });
  it('per-player cooldown', () => {
    expect(cooldownReady(undefined, 100, 3000)).toBe(true);
    expect(cooldownReady(100, 3099, 3000)).toBe(false);
    expect(cooldownReady(100, 3100, 3000)).toBe(true);
    expect(cooldownReady(5000, 100, 3000)).toBe(true); // clock went backwards
  });
  it('sunset roll once per day at 12000', () => {
    expect(sunsetDue(11980, 12000, 3, 2)).toBe(true);
    expect(sunsetDue(11980, 12000, 3, 3)).toBe(false);
    expect(sunsetDue(undefined, 12000, 3, 2)).toBe(false);
    expect(sunsetDue(12000, 12020, 3, 2)).toBe(false);
    expect(sunsetDue(23990, 10, 4, 3)).toBe(false);
  });
});

const snap = (o: Partial<PlayerSnapshot> = {}): PlayerSnapshot => ({
  gameMode: 'Survival',
  dead: false,
  sleeping: false,
  gliding: false,
  riding: false,
  falling: false,
  formOpen: false,
  inRound: false,
  nearBoss: false,
  inClaim: false,
  peaceful: false,
  dimension: 'overworld',
  ...o,
});
const cfg = { respawnGraceTicks: 200 };

describe('safety rules (§13.4)', () => {
  it('general skips', () => {
    for (const gm of ['Creative', 'Spectator']) expect(evaluate('chip_shower', snap({ gameMode: gm }), cfg)).toEqual({ action: 'skip', reason: 'game_mode' });
    expect(evaluate('curse', snap({ dead: true }), cfg).action).toBe('skip');
    expect(evaluate('curse', snap({ ticksSinceRespawn: 150 }), cfg)).toEqual({ action: 'skip', reason: 'respawn' });
    expect(evaluate('curse', snap({ ticksSinceRespawn: 250 }), cfg)).toEqual({ action: 'run' });
    expect(evaluate('curse', snap({ sleeping: true }), cfg).action).toBe('skip');
    expect(evaluate('curse', snap({ gameMode: 'Adventure' }), cfg).action).toBe('run');
  });
  it('open casino form defers', () => {
    expect(evaluate('diamond_rain', snap({ formOpen: true }), cfg)).toEqual({ action: 'defer' });
    // skip reasons still win over deferral
    expect(evaluate('diamond_rain', snap({ formOpen: true, gameMode: 'Creative' }), cfg).action).toBe('skip');
  });
  it('mob wave', () => {
    expect(evaluate('mob_wave', snap({ peaceful: true }), cfg)).toEqual({ action: 'skip', reason: 'peaceful' });
    expect(evaluate('mob_wave', snap({ nearBoss: true }), cfg)).toEqual({ action: 'skip', reason: 'boss' });
    expect(evaluate('mob_wave', snap({ inClaim: true }), cfg)).toEqual({ action: 'skip', reason: 'claim' });
    expect(evaluate('mob_wave', snap(), cfg)).toEqual({ action: 'run' });
  });
  it('teleport', () => {
    for (const k of ['gliding', 'riding', 'falling', 'nearBoss', 'inRound'] as const) expect(evaluate('random_teleport', snap({ [k]: true }), cfg).action).toBe('skip');
    expect(evaluate('random_teleport', snap(), cfg).action).toBe('run');
  });
  it('weather only in the Overworld; golden hour never player-checked', () => {
    expect(evaluate('weather_change', snap({ dimension: 'nether' }), cfg)).toEqual({ action: 'skip', reason: 'dimension' });
    expect(evaluate('golden_hour', snap({ gameMode: 'Creative' }), cfg).action).toBe('run');
  });
});

const col = (o: Partial<LandingColumn> = {}): LandingColumn => ({ dimension: 'overworld', y: 70, ground: STONE, feet: AIR, head: AIR, below: [STONE, STONE, STONE], minY: -64, ...o });

describe('teleport landing', () => {
  it('accepts solid ground with 2 air', () => {
    expect(isSafeLanding(col())).toBe(true);
    expect(isSafeLanding(col({ feet: b('minecraft:short_grass') }))).toBe(true);
  });
  it('rejects hazards', () => {
    for (const g of ['minecraft:lava', 'minecraft:magma', 'minecraft:fire', 'minecraft:campfire', 'minecraft:cactus', 'minecraft:sweet_berry_bush', 'minecraft:powder_snow', 'minecraft:pointed_dripstone', 'minecraft:water', 'minecraft:air']) {
      expect(isSafeLanding(col({ ground: b(g) }))).toBe(false);
    }
    expect(isSafeLanding(col({ feet: b('minecraft:water') }))).toBe(false);
    expect(isSafeLanding(col({ head: STONE }))).toBe(false);
    expect(isSafeLanding(col({ feet: b('minecraft:sweet_berry_bush') }))).toBe(false);
    expect(isSafeLanding(col({ ground: b('minecraft:oak_slab') }))).toBe(false);
  });
  it('min Y, Nether roof, End rules', () => {
    expect(isSafeLanding(col({ y: -59 }))).toBe(false);
    expect(isSafeLanding(col({ y: -58 }))).toBe(true);
    expect(isSafeLanding(col({ dimension: 'nether', y: 119, minY: 0 }))).toBe(false);
    expect(isSafeLanding(col({ dimension: 'nether', y: 100, minY: 0, ground: b('minecraft:netherrack') }))).toBe(true);
    expect(isSafeLanding(col({ dimension: 'nether', y: 127, minY: 0, ground: b('minecraft:bedrock') }))).toBe(false);
    const end = b('minecraft:end_stone');
    expect(isSafeLanding(col({ dimension: 'the_end', ground: end, below: [end, end, end], minY: 0 }))).toBe(true);
    expect(isSafeLanding(col({ dimension: 'the_end', ground: end, below: [end, AIR, end], minY: 0 }))).toBe(false);
    expect(isSafeLanding(col({ dimension: 'the_end', ground: b('minecraft:obsidian'), below: [end, end, end], minY: 0 }))).toBe(false);
  });
  it('solid ground helper', () => {
    expect(isSolidGround(STONE)).toBe(true);
    expect(isSolidGround(b('minecraft:tall_grass'))).toBe(false);
    expect(isSolidGround(b('minecraft:torch'))).toBe(false);
  });
  it('random offset distance within bounds', () => {
    const rng = seededRng(21);
    for (let i = 0; i < 2000; i++) {
      const { dx, dz } = randomOffset(rng.next(), rng.next(), 32, 256);
      const d = Math.hypot(dx, dz);
      expect(d).toBeGreaterThanOrEqual(31);
      expect(d).toBeLessThanOrEqual(257);
    }
  });
  it('item drops never into lava/void', () => {
    expect(safeDropSurface(undefined)).toBe(false);
    expect(safeDropSurface(b('minecraft:lava'))).toBe(false);
    expect(safeDropSurface(STONE)).toBe(true);
  });
});

describe('Golden Hour (§13.3)', () => {
  it('start, active window, cooldown from the end', () => {
    expect(canStart(INITIAL_GH, 0)).toBe(true);
    const s = startState(INITIAL_GH, 1000, 3600, 24000);
    expect(s.id).toBe(1);
    expect(isActive(s, 1000)).toBe(true);
    expect(isActive(s, 4599)).toBe(true);
    expect(isActive(s, 4600)).toBe(false);
    expect(canStart(s, 2000)).toBe(false);
    expect(canStart(s, 4600 + 23999)).toBe(false);
    expect(canStart(s, 4600 + 24000)).toBe(true);
    expect(canStart(s, 500)).toBe(true); // world clock reset
    expect(GH_WARN_TICKS).toBe(600);
  });
  it('bonus = floor(net × (m − 1)), capped per player', () => {
    expect(goldenBonus(101, 2, 0, 5000)).toEqual({ bonus: 101, capReached: false });
    expect(goldenBonus(101, 1.5, 0, 5000)).toEqual({ bonus: 50, capReached: false });
    expect(goldenBonus(0, 2, 0, 5000).bonus).toBe(0);
    expect(goldenBonus(-50, 2, 0, 5000).bonus).toBe(0);
    expect(goldenBonus(100, 1, 0, 5000).bonus).toBe(0);
    expect(goldenBonus(3000, 2, 4000, 5000)).toEqual({ bonus: 1000, capReached: true });
    expect(goldenBonus(1000, 2, 4000, 5000)).toEqual({ bonus: 1000, capReached: true });
    expect(goldenBonus(10, 2, 5000, 5000)).toEqual({ bonus: 0, capReached: false });
  });
  it('Monte-Carlo: total bonus never exceeds the cap', () => {
    const rng = seededRng(99);
    let paid = 0;
    for (let i = 0; i < 10000; i++) {
      const net = Math.floor(rng.next() * 2000) - 500;
      paid += goldenBonus(net, 2, paid, 5000).bonus;
    }
    expect(paid).toBe(5000);
  });
});

describe('streak HUD (§14)', () => {
  it('hidden at 0, lucky/unlucky keys and colors', () => {
    expect(streakSegment(0, 0)).toBeUndefined();
    expect(streakSegment(4, 0)).toEqual({ key: 'hud.burmaldaholic.streak.lucky', n: 4, color: '§6' });
    expect(streakSegment(-3, 1)).toEqual({ key: 'hud.burmaldaholic.streak.unlucky', n: 3, color: '§9' });
  });
  it('|S| ≥ 7 pulses', () => {
    expect(streakSegment(7, 0)?.color).toBe('§6');
    expect(streakSegment(7, 1)?.color).toBe('§e§l');
    expect(streakSegment(-10, 1)?.color).toBe('§b§l');
    expect(streakSegment(6, 1)?.color).toBe('§6');
  });
});
