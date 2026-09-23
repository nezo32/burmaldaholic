import { describe, expect, it } from 'vitest';
import { seededRng } from '../../core/logic/rng';
import { BOOK_ENCHANTMENTS, DEFAULT_WEIGHT, LOOT, rollLoot, scatterSlots } from './loot';

describe('casino loot (GAME_DESIGN §16)', () => {
  it('matches the spec tables', () => {
    expect(LOOT.village_casino.rolls).toEqual([4, 7]);
    expect(LOOT.piglin_parlor.rolls).toEqual([5, 8]);
    expect(LOOT.high_roller.rolls).toEqual([3, 5]);
    const w = (t: keyof typeof LOOT, item: string) => LOOT[t].entries.find((e) => e.item === item);
    expect(w('village_casino', 'burmaldaholic:chip_1')).toMatchObject({ min: 5, max: 20, weight: 30 });
    expect(w('village_casino', 'burmaldaholic:casino_card')).toMatchObject({ min: 1, max: 1, weight: 3 });
    expect(w('piglin_parlor', 'minecraft:gold_ingot')).toMatchObject({ min: 4, max: 12, weight: DEFAULT_WEIGHT });
    expect(w('piglin_parlor', 'minecraft:netherite_scrap')).toMatchObject({ weight: 3 });
    expect(w('high_roller', 'burmaldaholic:golden_chip')).toMatchObject({ weight: 4 });
    expect(w('high_roller', 'minecraft:enchanted_book')).toMatchObject({ enchant: true, weight: 10 });
    expect(LOOT.village_casino.entries.reduce((s, e) => s + e.weight, 0)).toBe(110);
  });

  it('rolls within the roll and count ranges', () => {
    const rng = seededRng(42);
    for (const table of Object.values(LOOT))
      for (let i = 0; i < 2000; i++) {
        const stacks = rollLoot(table, rng);
        expect(stacks.length).toBeGreaterThanOrEqual(table.rolls[0]);
        expect(stacks.length).toBeLessThanOrEqual(table.rolls[1]);
        for (const s of stacks) {
          const e = table.entries.find((x) => x.item === s.item);
          expect(e).toBeDefined();
          expect(s.count).toBeGreaterThanOrEqual(e!.min);
          expect(s.count).toBeLessThanOrEqual(e!.max);
        }
      }
  });

  it('picks entries in proportion to their weights (Monte Carlo)', () => {
    const rng = seededRng(7);
    for (const table of Object.values(LOOT)) {
      const hits = new Map<string, number>();
      let total = 0;
      for (let i = 0; i < 40_000; i++)
        for (const s of rollLoot(table, rng)) {
          hits.set(s.item, (hits.get(s.item) ?? 0) + 1);
          total++;
        }
      const weightSum = table.entries.reduce((s, e) => s + e.weight, 0);
      for (const e of table.entries) expect(Math.abs((hits.get(e.item) ?? 0) / total - e.weight / weightSum)).toBeLessThan(0.006);
    }
  });

  it('scatters stacks over distinct slots', () => {
    const rng = seededRng(3);
    const slots = scatterSlots(8, 27, rng);
    expect(new Set(slots).size).toBe(8);
    expect(slots.every((s) => s >= 0 && s < 27)).toBe(true);
    expect(scatterSlots(40, 27, rng)).toHaveLength(27);
  });

  it('books get sane enchantments', () => {
    for (const [id, max] of BOOK_ENCHANTMENTS) {
      expect(id).toMatch(/^[a-z_]+$/);
      expect(max).toBeGreaterThanOrEqual(1);
    }
  });
});
