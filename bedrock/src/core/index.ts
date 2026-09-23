/**
 * Core public API. Feature modules import from here (`../../core` or `../core`) and from
 * `core/logic/*` (pure) - never from other core files directly.
 * Reference: docs/architecture/bedrock.md "Core API for feature devs".
 */
export type { CasinoModule, ModuleContext, StartupContext } from './module';
export type { CommandSpec } from './commands';
export type { ConfigStore, ConfigListener } from './config';
export type { ConfigDef, ConfigValue, JsonValue, ModuleConfigDef } from './logic/config-schema';
export { type Economy, type DebtProvider, type HouseRef, type TxOp, type TxAccount, BANK, BALANCE_PROP, BALANCE_OBJECTIVE } from './economy';
export { HudPriority, type Hud, type HudSegment } from './hud';
export type { GoldenHour } from './golden-hour';
export type { Limits, VipProvider, TableLimits } from './limits';
export type { StreakService, StreakListener } from './streak';
export {
  type WagerService,
  type WagerTicket,
  type Stake,
  type PlaceOptions,
  type PlaceResult,
  type SettledEvent,
  type SettledListener,
  type RecordOptions,
  type HouseResolver,
  type LimitsResolver,
  type WagerVeto,
  type GameId,
  GAME_IDS,
  RNG_GAMES,
  SOUL_WAGER_TAG,
  gameLabel,
} from './wagers';
export { type Tables, type TableHandler, type TableSession, type TableRef, type LeaveReason, TABLE_COMPONENT } from './tables';
export { type MenuRegistry, type MenuEntry, isOperator } from './menu';
export type { Cashier } from './cashier';
export type { Admin, AdminAction } from './admin';
export type { Achievements, AchievementListener } from './achievements';
export { ACHIEVEMENTS, type AchievementDef, isAchievement, HOUSE_PROFIT_ACHIEVEMENT } from './logic/achievements';
export { showForm, isFormOpen, promptAmount, ModalLayout, type AmountPrompt } from './forms';
export { giveItems, giveStack, giveChips, chipValueInInventory, takeAllChips, countItems, removeItems, heldItem, itemAt } from './items';
export { onlinePlayer, livePlayer } from './offline';
export { NO_REWARD_TAG } from './earning';
export { CASINO_CARD_ID } from './module-core';
export type { Logger } from './log';
export type { Services } from './services';
export { isCasinoEnabled } from './casino';
export { worldTick, readJson, writeJson, worldJson, worldSharded } from './store';
export { NS, nsId, langKey, type ModuleId } from './logic/ids';
export { t, plural, join, joinWith, lines, NEWLINE, lit, color, chips, chipsAcc, unit, duration, variant, type Unit, type Raw, type Arg } from './logic/rawtext';
export { formatNumber, formatSigned, formatMultiplier, formatClock, formatDhm } from './logic/format';
export { pluralKey, pluralSuffix } from './logic/plural';
export { type Rng, mathRng, seededRng, randInt, chance, pick, weightedPick, shuffle } from './logic/rng';
export { type OddsService, type OddsModifier, type OddsQuery } from './logic/odds';
export { GAME_RTP, type RoundOutcome } from './logic/streak';
export { VIP_TIERS, VIP_COLORS, vipTierKey, sliderStep, parseAmount, validateBet } from './logic/bet';
export { CHIP_VALUES, chipItemId, chipValueOf } from './logic/economy-math';
export { coreModule } from './module-core';
export { CHIP_GLYPH, CARD_BACK_GLYPH, STREAK_FLAME_GLYPH, STREAK_CLOUD_GLYPH, cardGlyph, suitGlyph, dieGlyph, vipBadgeGlyph, glyphRaw } from './logic/glyphs';
export { type Card, type Rank, type Suit, SUITS, RANKS, cardId, parseCard, newShoe, rankLabel, suitLabel, cardName, hiddenCard } from './logic/cards';
