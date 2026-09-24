/** Translated bot name pool (BOTS.md §7.1, §11.3). PURE. Same ids as Java BotRoster. */
import { type Rng, pick } from '../rng';
import { pickPersonality } from './personality';
import type { BotDifficulty, BotLevel, BotProfile } from './types';

export type NameTheme = 'any' | 'piglin' | 'ender';

export const NAMES_ANY = ['lucky_steve', 'grandpa_pavel', 'creeper42', 'mr_blocksworth', 'diamond_dora', 'aunt_zoya', 'redstone_rick', 'emerald_emma', 'sir_oinksalot', 'baba_valya', 'uncle_grisha', 'kuzmich', 'cobble_carl', 'slime_sam', 'brewing_bella', 'captain_boat', 'bee_bea', 'torch_tanya', 'axolotl_al', 'lady_luckless', 'iron_ivan'] as const;
export const NAMES_PIGLIN = ['nether_nick', 'goldie_nuggets', 'piglin_pete', 'bartering_boris', 'madame_crimson', 'tusk_tony'] as const;
export const NAMES_ENDER = ['enderman_ed', 'madame_ender', 'shulker_shura', 'pearl_polly', 'void_viktor'] as const;
/** Old poker names saved by earlier versions → new ids. */
export const LEGACY_NAMES: Readonly<Record<string, string>> = { diamond_dave: 'diamond_dora', 'Diamond Dave': 'diamond_dora' };

export const nameKey = (id: string): string => `gui.burmaldaholic.bots.name.${id}`;

const themed = (t: NameTheme): readonly string[] => (t === 'piglin' ? NAMES_PIGLIN : t === 'ender' ? NAMES_ENDER : []);

/** An unused name id (themed first); if all are used, any of the pool. */
export function drawName(rng: Rng, theme: NameTheme, used: ReadonlySet<string>): string {
  let free = themed(theme).filter((id) => !used.has(id));
  if (free.length === 0) free = [...themed(theme), ...NAMES_ANY].filter((id) => !used.has(id));
  return free.length ? pick(rng, free) : pick(rng, [...themed(theme), ...NAMES_ANY]);
}

/** Resolve a difficulty setting to a level: MIXED draws from `mix` = [easy, normal, hard] weights. */
export function pickLevel(rng: Rng, setting: BotDifficulty, mix: readonly number[]): BotLevel {
  if (setting !== 'MIXED') return setting;
  const w = [0, 1, 2].map((i) => Math.max(0, mix[i] ?? 0));
  const total = w[0]! + w[1]! + w[2]!;
  if (total <= 0) return 'NORMAL';
  let r = rng.next() * total;
  for (const [i, lvl] of (['EASY', 'NORMAL', 'HARD'] as const).entries()) {
    r -= w[i]!;
    if (r < 0) return lvl;
  }
  return 'HARD';
}

export function newBotId(rng: Rng): string {
  let id = 'b';
  for (let i = 0; i < 7; i++) id += Math.floor(rng.next() * 36).toString(36);
  return id;
}

/** A complete new bot. */
export function createBot(rng: Rng, setting: BotDifficulty, mix: readonly number[], theme: NameTheme, usedNames: ReadonlySet<string>, personalities = true): BotProfile {
  const level = pickLevel(rng, setting, mix);
  return { id: newBotId(rng), nameId: drawName(rng, theme, usedNames), level, personality: pickPersonality(rng, level, personalities) };
}
