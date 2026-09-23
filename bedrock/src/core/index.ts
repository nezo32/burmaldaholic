/**
 * Core public API. Feature modules import from here (`../../core` or `../core`) and from
 * `core/logic/*` (pure) - never from other core files directly.
 */
export type { CasinoModule, ModuleContext, StartupContext } from './module';
export type { CommandSpec } from './commands';
export type { ConfigDef, ConfigValue } from './logic/config-schema';
export type { Economy } from './economy';
export { HudPriority, type Hud } from './hud';
export type { Logger } from './log';
export type { Services } from './services';
export { isCasinoEnabled } from './casino';
export { NS, nsId, langKey, type ModuleId } from './logic/ids';
export { t, plural, join, lit, type Raw, type Arg } from './logic/rawtext';
export { pluralKey, pluralSuffix } from './logic/plural';
export { type Rng, mathRng, seededRng, randInt, chance, pick, weightedPick, shuffle } from './logic/rng';
export { coreModule } from './module-core';
export { type Card, type Rank, type Suit, SUITS, RANKS, cardId, parseCard, newShoe, rankKey, suitKey } from './logic/cards';
