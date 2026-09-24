/**
 * Identifiers shared by every module. PURE: no @minecraft imports.
 * The module id list mirrors bedrock/modules.json (checked by scripts/check-arch.mjs).
 */
export const NS = 'burmaldaholic';

export const MODULE_IDS = [
  'core',
  'blackjack',
  'poker',
  'slots',
  'roulette',
  'craps',
  'baccarat',
  'extras',
  'loan',
  'chaos',
  'lastchance',
  'worldgen',
  'vip',
  'multiplayer',
] as const;

export type ModuleId = (typeof MODULE_IDS)[number];

/** `burmaldaholic:<name>` namespaced identifier (items, blocks, entities, commands, dynamic properties). */
export const nsId = (name: string): string => `${NS}:${name}`;

/** Lang key owned by a module: `msg.burmaldaholic.<module>.<rest>` (scheme from docs/design). */
export const langKey = (module: ModuleId, rest: string): string => `msg.${NS}.${module}.${rest}`;
