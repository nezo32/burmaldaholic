/**
 * Casino loot chests (GAME_DESIGN §16.1–16.3). PURE.
 * Chests are filled by script right after a casino is built, so items owned by modules that are
 * missing in a build are simply skipped instead of breaking a data-driven loot table.
 *
 * Weights the spec leaves open (entries listed without "w") use DEFAULT_WEIGHT.
 */
import { type Rng, randInt, weightedPick } from '../../core/logic/rng';
import type { LootTableId } from './layouts';

export const DEFAULT_WEIGHT = 20;

export interface LootEntry {
  readonly item: string;
  readonly min: number;
  readonly max: number;
  readonly weight: number;
  /** enchanted book: add one random enchantment */
  readonly enchant?: boolean;
}

export interface LootTable {
  readonly rolls: readonly [number, number];
  readonly entries: readonly LootEntry[];
}

const e = (item: string, min: number, max: number, weight = DEFAULT_WEIGHT, enchant?: boolean): LootEntry => ({ item, min, max, weight, enchant });

export const LOOT: Readonly<Record<LootTableId, LootTable>> = {
  // §16.1 (4–7 rolls)
  village_casino: {
    rolls: [4, 7],
    entries: [
      e('burmaldaholic:chip_1', 5, 20, 30),
      e('burmaldaholic:chip_5', 2, 8, 25),
      e('burmaldaholic:chip_25', 1, 3, 12),
      e('burmaldaholic:scratch_card', 1, 3, 15),
      e('minecraft:emerald', 2, 6, 12),
      e('minecraft:golden_carrot', 2, 5, 8),
      e('burmaldaholic:lucky_coin', 1, 1, 5),
      e('burmaldaholic:casino_card', 1, 1, 3),
    ],
  },
  // §16.2 (5–8 rolls)
  piglin_parlor: {
    rolls: [5, 8],
    entries: [
      e('minecraft:gold_ingot', 4, 12),
      e('minecraft:gold_block', 1, 1, 8),
      e('burmaldaholic:chip_25', 2, 6),
      e('burmaldaholic:chip_100', 1, 2, 10),
      e('burmaldaholic:scratch_card_gold', 1, 1, 8),
      e('minecraft:netherite_scrap', 1, 1, 3),
      e('burmaldaholic:lucky_coin', 1, 1, 5),
    ],
  },
  // §16.3 (3–5 rolls)
  high_roller: {
    rolls: [3, 5],
    entries: [
      e('burmaldaholic:chip_100', 2, 5),
      e('burmaldaholic:chip_500', 1, 2, 10),
      e('minecraft:diamond', 2, 6),
      e('minecraft:enchanted_book', 1, 1, 10, true),
      e('burmaldaholic:scratch_card_gold', 1, 2),
      e('burmaldaholic:golden_chip', 1, 1, 4),
    ],
  },
};

export interface LootStack {
  readonly item: string;
  readonly count: number;
  readonly enchant?: boolean;
}

/** Roll a table: one stack per roll (stacks of the same item are not merged, like vanilla). */
export function rollLoot(table: LootTable, rng: Rng): LootStack[] {
  const rolls = randInt(rng, table.rolls[0], table.rolls[1]);
  const out: LootStack[] = [];
  for (let i = 0; i < rolls; i++) {
    const entry = weightedPick(
      rng,
      table.entries.map((en) => [en, en.weight] as const),
    );
    out.push({ item: entry.item, count: randInt(rng, entry.min, entry.max), enchant: entry.enchant });
  }
  return out;
}

/** Spread stacks over distinct random slots of a container (vanilla-like scatter). */
export function scatterSlots(stacks: number, containerSize: number, rng: Rng): number[] {
  const free = Array.from({ length: containerSize }, (_, i) => i);
  const out: number[] = [];
  for (let i = 0; i < Math.min(stacks, containerSize); i++) {
    const j = Math.floor(rng.next() * free.length);
    out.push(free.splice(j, 1)[0] as number);
  }
  return out;
}

/** Enchantments a High Roller enchanted book can carry (id, max level). */
export const BOOK_ENCHANTMENTS: readonly (readonly [string, number])[] = [
  ['sharpness', 5],
  ['protection', 4],
  ['efficiency', 5],
  ['unbreaking', 3],
  ['fortune', 3],
  ['looting', 3],
  ['feather_falling', 4],
  ['power', 5],
  ['mending', 1],
  ['silk_touch', 1],
];
