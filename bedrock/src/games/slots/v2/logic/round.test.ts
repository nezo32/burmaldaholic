/** Round lifecycle maths (SLOTS.md §6, §8, §14; §15 items 11, 14, 15, 17). */
import { describe, expect, it } from 'vitest';
import { defaultMachine } from './config';
import { drawSpin, evaluateSpin } from './engine';
import { emptyPools, poolValues } from './jackpots';
import { GLOBAL_DEFAULTS, achievementsFor, addStats, autoplayStop, buyError, chaosFor, isLosingSpin, readGlobalConfig, settlement } from './round';
import { scriptedRng, fxSlotRng } from './test-rng';
import type { MachineId, SpinTape } from './types';

const pools = (m: MachineId): number[] => poolValues(defaultMachine(m).jackpot, emptyPools());
const tape = (over: Partial<SpinTape>): SpinTape => ({ machine: 'overworld', bet: 10, bought: false, stops: [0, 0, 0, 0, 0], jackpots: [], totalFifths: 0, capHit: false, ...over });

describe('settlement and streak rule', () => {
  it('wager return excludes pool money; owned fixed jackpots are inside the return', () => {
    const def = defaultMachine('end');
    const t = tape({ machine: 'end', bet: 100, totalFifths: 60, jackpots: [{ tier: 1, chips: 1500, owned: false }] });
    expect(settlement(def, t)).toEqual({ stake: 100, wagerReturn: 1200, poolChips: 1500 });
    expect(settlement(def, { ...t, bought: true })).toMatchObject({ stake: 10_900 });
    expect(settlement(def, tape({ machine: 'end', bet: 100, totalFifths: 5000 + 60, jackpots: [{ tier: 1, chips: 1500, owned: true }] })).poolChips).toBe(0);
  });
  it('a spin is losing only when its whole return (incl. jackpots) is below the bet', () => {
    expect(isLosingSpin(tape({ totalFifths: 4 }))).toBe(true);
    expect(isLosingSpin(tape({ totalFifths: 5 }))).toBe(false);
    expect(isLosingSpin(tape({ totalFifths: 0, jackpots: [{ tier: 1, chips: 100, owned: false }] }))).toBe(false);
  });
});

describe('chaos mapping (SLOTS.md §8.4): one event per spin, by priority', () => {
  it('jackpots first (Major/Grand → jackpot, Mini/Minor → chip_shower)', () => {
    const def = defaultMachine('overworld');
    expect(chaosFor(def, tape({ jackpots: [{ tier: 1, chips: 1, owned: false }, { tier: 3, chips: 1, owned: false }] }))).toEqual({ event: 'jackpot', tier: 3 });
    expect(chaosFor(def, tape({ jackpots: [{ tier: 2, chips: 1, owned: false }], hunt: { entries: [0], opened: 1 } }))).toEqual({ event: 'chip_shower', tier: 2 });
  });
  it('Overworld: creeper on the first chest; 5 compasses with free spins → golden_hour', () => {
    const def = defaultMachine('overworld');
    expect(chaosFor(def, tape({ hunt: { entries: [0, 1, 2], opened: 1 } }))).toEqual({ event: 'mob_wave', flavour: 'creeper_friends' });
    expect(chaosFor(def, tape({ hunt: { entries: [1, 0], opened: 2 } }))).toBeUndefined();
    // find 5 scatters: stops showing a compass on every reel
    const sc = def.codes.indexOf('SC');
    const stops = def.strips.map((s) => s.indexOf(sc));
    expect(evaluateSpin(def, stops, false).scatters).toBe(5);
    expect(chaosFor(def, tape({ stops, freeSpins: { awarded: 15, spins: [], payFifths: 0 } }))).toEqual({ event: 'golden_hour' });
  });
  it('Nether: 8 tumbles → lucky_buff; End: all sticky → xp_fountain', () => {
    expect(chaosFor(defaultMachine('nether'), tape({ machine: 'nether', stops: [10, 16, 17, 0, 0] }))).toMatchObject({ event: 'lucky_buff', tumbles: 8 });
    const fs = { awarded: 9, spins: [{ stops: [0, 0, 0, 0, 0], stickyMaskAfter: 7, retrigger: false, payFifths: 0 }], payFifths: 0 };
    expect(chaosFor(defaultMachine('end'), tape({ machine: 'end', bought: true, stops: [], freeSpins: fs }))).toEqual({ event: 'xp_fountain', flavour: 'void_walker' });
  });
  it('End: 5 Dragon Heads → random_teleport', () => {
    const def = defaultMachine('end');
    // Dragon Head stacks start at R1 25, R2 29, R3 23, R4 0, R5 0
    expect(chaosFor(def, tape({ machine: 'end', stops: [25, 29, 23, 0, 0] }))).toEqual({ event: 'random_teleport', flavour: 'dragon_fling' });
  });
});

describe('advancements (SLOTS.md §14)', () => {
  it('maps tapes to ids', () => {
    const end = defaultMachine('end');
    expect(achievementsFor(end, tape({ machine: 'end', stops: [25, 29, 23, 0, 0], totalFifths: 600 }))).toEqual(expect.arrayContaining(['top_five', 'epic_win']));
    expect(achievementsFor(end, tape({ machine: 'end', stops: [0, 0, 0, 0, 0], wheel: { segments: [1, 7, 0] }, jackpots: [{ tier: 4, chips: 1, owned: false }] }))).toEqual(
      expect.arrayContaining(['dragon_core', 'mini_jackpot', 'jackpot']),
    );
    const bought = tape({ machine: 'end', bought: true, stops: [], freeSpins: { awarded: 9, spins: [{ stops: [0, 0, 0, 0, 0], stickyMaskAfter: 7, retrigger: false, payFifths: 0 }], payFifths: 0 } });
    expect(achievementsFor(end, bought)).toEqual(['void_walker']); // a bought feature is not "triggered free spins"
    expect(achievementsFor(defaultMachine('overworld'), tape({ hunt: { entries: [1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0], opened: 11 } }))).toContain('treasure_hunter');
    expect(achievementsFor(defaultMachine('nether'), tape({ machine: 'nether', stops: [10, 16, 17, 0, 0] }))).toContain('tumble_six');
    expect(achievementsFor(defaultMachine('nether'), tape({ capHit: true, machine: 'nether', totalFifths: 10_000 }))).toEqual(expect.arrayContaining(['max_win', 'epic_win']));
  });
});

describe('autoplay stop rules (SLOTS.md §6.4, §15.17)', () => {
  const s = { left: 5, startBalance: 1000, lossLimit: 25, stopOnFeature: true, stopOnWin: 50, bet: 10 };
  it('jackpot, max win, feature, single win, loss limit, funds, count', () => {
    expect(autoplayStop(s, tape({ jackpots: [{ tier: 1, chips: 1, owned: false }] }), 990, 10)).toBe('jackpot');
    expect(autoplayStop(s, tape({ capHit: true }), 990, 10)).toBe('max_win');
    expect(autoplayStop(s, tape({ freeSpins: { awarded: 8, spins: [], payFifths: 0 } }), 990, 10)).toBe('feature');
    expect(autoplayStop({ ...s, stopOnFeature: false }, tape({ freeSpins: { awarded: 8, spins: [], payFifths: 0 } }), 990, 10)).toBeUndefined();
    expect(autoplayStop(s, tape({ totalFifths: 250 }), 1400, 10)).toBe('big_win');
    expect(autoplayStop(s, tape({}), 750, 10)).toBe('loss');
    expect(autoplayStop(s, tape({}), 760, 10)).toBeUndefined();
    expect(autoplayStop({ ...s, startBalance: 12 }, tape({}), 5, 10)).toBe('funds');
    expect(autoplayStop({ ...s, left: 0 }, tape({}), 990, 10)).toBe('done');
  });
});

describe('statistics, buy limits, global config', () => {
  it('stats accumulate', () => {
    const def = defaultMachine('overworld');
    const rng = fxSlotRng(1);
    let st;
    for (let i = 0; i < 100; i++) st = addStats(st, def, drawSpin({ def, bet: 10, buy: false, owned: false, pools: pools('overworld') }, rng));
    expect(st!.spins).toBe(100);
    expect(st!.wagered).toBe(1000);
  });
  it('buy: disabled / not on the ladder / above tier max × 25', () => {
    const ne = defaultMachine('nether');
    const ladder = [10, 20, 50];
    expect(buyError(ne, 10, true, true, 100, 25, ladder)).toBeUndefined();
    expect(buyError(ne, 10, false, true, 100, 25, ladder)).toBe('buy_disabled');
    expect(buyError(ne, 10, true, false, 100, 25, ladder)).toBe('buy_disabled');
    expect(buyError(defaultMachine('overworld'), 10, true, true, 100, 25, ladder)).toBe('buy_disabled');
    expect(buyError(ne, 15, true, true, 100, 25, ladder)).toBe('bet_unavailable');
    expect(buyError(ne, 50, true, true, 20, 25, ladder)).toBe('buy_limit'); // 920 > 500
  });
  it('global keys: defaults, valid overrides, invalid ignored', () => {
    expect(readGlobalConfig(() => undefined)).toEqual(GLOBAL_DEFAULTS);
    const c = readGlobalConfig((k) => ({ 'slots.bigWinTiers': [4, 12, 30, 90], 'slots.jackpot.announceMinTier': 'MINOR', 'slots.inWorld.radius': 99, 'slots.turboAllowed': false })[k]);
    expect(c.bigWinTiers).toEqual([4, 12, 30, 90]);
    expect(c.announceMinTier).toBe(2);
    expect(c.inWorldRadius).toBe(24);
    expect(c.turboAllowed).toBe(false);
  });
  it('scripted rng helper', () => {
    expect(scriptedRng([5]).nextInt(3)).toBe(2);
  });
});
