import { describe, expect, it } from 'vitest';
import { seededRng } from '../../core/logic/rng';
import {
  CONTRACT_DEFS,
  CONTRACT_IDS,
  type ContractId,
  type ContractParams,
  addProgress,
  allDone,
  drawId,
  generate,
  isMatureCrop,
  isStale,
  killContracts,
  oreContract,
  reroll,
  scaledReward,
  scaledTarget,
  topUp,
  travelStep,
} from './contracts';

const base: ContractParams = { tier: 0, scaling: 0.25, bonus: 0, multiplier: 1, weights: {} };

describe('scaling (§3.4.4)', () => {
  it('target = ceil(base × (1 + 0.25 t))', () => {
    expect(scaledTarget(24, 0, 0.25)).toBe(24);
    expect(scaledTarget(24, 1, 0.25)).toBe(30);
    expect(scaledTarget(3, 1, 0.25)).toBe(4); // 3.75 -> 4
    expect(scaledTarget(5, 5, 0.25)).toBe(12); // 11.25 -> 12
    expect(scaledTarget(500, 2, 0.25)).toBe(750);
  });
  it('reward = floor(base × (1 + 0.25 t) × (1 + bonus) × multiplier)', () => {
    expect(scaledReward(50, 0, 0.25, 0, 1)).toBe(50);
    expect(scaledReward(50, 1, 0.25, 0.05, 1)).toBe(65); // 62.5 × 1.05 = 65.625
    expect(scaledReward(25, 2, 0.25, 0.1, 1)).toBe(41); // 37.5 × 1.1 = 41.25
    expect(scaledReward(80, 5, 0.25, 0.1, 2)).toBe(396); // 180 × 1.1 × 2
    expect(scaledReward(80, 5, 0.25, 0.1, 0)).toBe(0);
  });
});

describe('generation', () => {
  it('draws distinct contracts, one per slot', () => {
    for (let seed = 1; seed < 200; seed++) {
      const s = generate(seededRng(seed), 7, 5, base);
      expect(s.day).toBe(7);
      expect(s.list).toHaveLength(5);
      expect(new Set(s.list.map((c) => c.id)).size).toBe(5);
      for (const c of s.list) {
        expect(c.target).toBe(CONTRACT_DEFS[c.id].target);
        expect(c.progress).toBe(0);
        expect(c.done || c.rerolled).toBe(false);
      }
    }
  });
  it('weight 0 disables a contract; small pools yield fewer contracts', () => {
    const weights: Partial<Record<ContractId, number>> = {};
    for (const id of CONTRACT_IDS) weights[id] = 0;
    weights.fish = 1;
    weights.trade = 1;
    const s = generate(seededRng(3), 1, 5, { ...base, weights });
    expect(s.list.map((c) => c.id).sort()).toEqual(['fish', 'trade']);
    expect(drawId(seededRng(1), { ...base, weights }, new Set(['fish', 'trade']))).toBeUndefined();
  });
  it('draw frequencies follow the weights', () => {
    const rng = seededRng(99);
    const counts = new Map<ContractId, number>();
    const n = 200_000;
    for (let i = 0; i < n; i++) {
      const id = drawId(rng, base, new Set())!;
      counts.set(id, (counts.get(id) ?? 0) + 1);
    }
    const total = CONTRACT_IDS.reduce((s, id) => s + CONTRACT_DEFS[id].weight, 0);
    for (const id of CONTRACT_IDS) expect((counts.get(id) ?? 0) / n).toBeCloseTo(CONTRACT_DEFS[id].weight / total, 2);
  });
  it('staleness by world day', () => {
    const s = generate(seededRng(1), 4, 3, base);
    expect(isStale(undefined, 4)).toBe(true);
    expect(isStale(s, 4)).toBe(false);
    expect(isStale(s, 5)).toBe(true);
  });
  it('topUp adds slots after a promotion without duplicates', () => {
    const s = generate(seededRng(5), 1, 3, base);
    const added = topUp(seededRng(6), s, 5, { ...base, tier: 4 });
    expect(added).toHaveLength(2);
    expect(s.list).toHaveLength(5);
    expect(new Set(s.list.map((c) => c.id)).size).toBe(5);
    expect(topUp(seededRng(6), s, 5, base)).toHaveLength(0);
  });
});

describe('progress and reroll', () => {
  const state = () => ({
    day: 1,
    list: [
      { id: 'mine_iron' as const, target: 3, reward: 50, progress: 0, done: false, rerolled: false },
      { id: 'wager' as const, target: 500, reward: 30, progress: 0, done: false, rerolled: false },
    ],
  });
  it('completes exactly once and clamps progress', () => {
    const s = state();
    expect(addProgress(s, 'mine_iron', 2)).toHaveLength(0);
    const done = addProgress(s, 'mine_iron', 5);
    expect(done.map((c) => c.id)).toEqual(['mine_iron']);
    expect(s.list[0]!.progress).toBe(3);
    expect(addProgress(s, 'mine_iron', 1)).toHaveLength(0);
    expect(addProgress(s, 'fish', 1)).toHaveLength(0);
    expect(addProgress(s, 'wager', 0)).toHaveLength(0);
    expect(allDone(s)).toBe(false);
    addProgress(s, 'wager', 1000);
    expect(allDone(s)).toBe(true);
  });
  it('one reroll per slot, never a done slot, never a duplicate', () => {
    const s = state();
    const r = reroll(seededRng(2), s, 1, base);
    expect(r.ok).toBe(true);
    if (!r.ok) return;
    expect(r.contract.rerolled).toBe(true);
    expect(['mine_iron', 'wager']).not.toContain(r.contract.id);
    expect(reroll(seededRng(3), s, 1, base)).toEqual({ ok: false, error: 'rerolled' });
    addProgress(s, 'mine_iron', 3);
    expect(reroll(seededRng(3), s, 0, base)).toEqual({ ok: false, error: 'done' });
    expect(reroll(seededRng(3), s, 7, base)).toEqual({ ok: false, error: 'no_slot' });
  });
});

describe('event classification', () => {
  it('ores incl. deepslate', () => {
    expect(oreContract('minecraft:deepslate_iron_ore')).toBe('mine_iron');
    expect(oreContract('minecraft:coal_ore')).toBe('mine_coal');
    expect(oreContract('minecraft:diamond_ore')).toBe('mine_diamond');
    expect(oreContract('minecraft:gold_ore')).toBeUndefined();
  });
  it('mature crops only', () => {
    expect(isMatureCrop('minecraft:wheat', 7)).toBe(true);
    expect(isMatureCrop('minecraft:wheat', 6)).toBe(false);
    expect(isMatureCrop('minecraft:beetroot', undefined)).toBe(false);
    expect(isMatureCrop('minecraft:melon_block', 7)).toBe(false);
  });
  it('kills', () => {
    expect(killContracts('minecraft:husk')).toEqual(['kill_zombie', 'kill_any']);
    expect(killContracts('minecraft:stray')).toEqual(['kill_skeleton', 'kill_any']);
    expect(killContracts('minecraft:creeper')).toEqual(['kill_creeper', 'kill_any']);
    expect(killContracts('minecraft:blaze')).toEqual(['kill_any']);
    expect(killContracts('minecraft:cow')).toEqual([]);
  });
  it('nether travel ignores teleports', () => {
    expect(travelStep({ x: 0, z: 0 }, { x: 3, z: 4 }, 80)).toBe(5);
    expect(travelStep({ x: 0, z: 0 }, { x: 300, z: 0 }, 80)).toBe(0);
  });
});
