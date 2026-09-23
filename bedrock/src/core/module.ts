/**
 * The module contract. Every feature folder exports exactly one CasinoModule from its
 * index.ts; src/modules.ts lists them all (pre-populated, never edited by feature devs).
 * See docs/architecture/bedrock.md "Core API for feature devs".
 */
import type { CustomCommandRegistry, StartupEvent } from '@minecraft/server';
import type { Admin } from './admin';
import type { Cashier } from './cashier';
import type { CommandSpec } from './commands';
import type { ConfigStore } from './config';
import type { Economy } from './economy';
import type { GoldenHour } from './golden-hour';
import type { Hud } from './hud';
import type { Limits } from './limits';
import type { Logger } from './log';
import type { ModuleConfigDef } from './logic/config-schema';
import type { ModuleId } from './logic/ids';
import type { OddsService } from './logic/odds';
import type { MenuRegistry } from './menu';
import type { Services } from './services';
import type { StreakService } from './streak';
import type { Tables } from './tables';
import type { WagerService } from './wagers';

export interface StartupContext {
  readonly moduleId: ModuleId;
  /** Raw startup event (block/item custom components, dimension registry...). Early execution! */
  readonly event: StartupEvent;
  readonly customCommandRegistry: CustomCommandRegistry;
  /** Register a `/burmaldaholic:<name>` command; the handler runs deferred (system.run) and casino-guarded. */
  registerCommand(spec: CommandSpec): void;
  /** Table handlers may be registered here or in onWorldLoad. */
  readonly tables: Tables;
  readonly log: Logger;
}

export interface ModuleContext {
  readonly moduleId: ModuleId;
  /** Config: full CONFIG.md keys (`blackjack.decks`) or names relative to this module (`enabled`). */
  readonly config: ConfigStore;
  /** Balances, credit/debit, earn toasts, atomic transact, bankrolls, debt provider hook. */
  readonly economy: Economy;
  /** Stake -> settle lifecycle for every house-banked round (chips, items, XP, hearts, soul). */
  readonly wagers: WagerService;
  /** VIP tier + bet-limit checks (the vip module installs the real tier provider). */
  readonly limits: Limits;
  /** Binary-outcome modifiers and the streak re-draw for RNG games. */
  readonly odds: OddsService;
  /** Persistent Lucky/Unlucky streak (updated automatically by wagers.settle). */
  readonly streak: StreakService;
  /** Table blocks / sessions / timers / leave handling. */
  readonly tables: Tables;
  /** Actionbar arbitration, titles, status-line segments. */
  readonly hud: Hud;
  /** Server-wide Golden Hour state (chaos drives it). */
  readonly goldenHour: GoldenHour;
  /** Casino Menu hub buttons. */
  readonly menu: MenuRegistry;
  /** Cashier buttons (Contracts, Shop...). */
  readonly cashier: Cashier;
  /** Admin page actions (ops). */
  readonly admin: Admin;
  /** Cross-module public APIs. Provide yours from onWorldLoad; consume lazily. */
  readonly services: Services;
  readonly log: Logger;
  /** Current casino-mode state. Every gameplay entry point must bail out when false. */
  isCasinoEnabled(): boolean;
  /**
   * Wrap an event handler so it silently does nothing while casino mode is off and
   * errors are logged instead of breaking other modules.
   */
  guard<A extends unknown[]>(fn: (...args: A) => void): (...args: A) => void;
}

export interface CasinoModule {
  readonly id: ModuleId;
  /**
   * Extra config keys NOT in docs/design/CONFIG.md (catalog keys need no declaration).
   * Relative names get the module prefix (`enabled` -> `slots.enabled`).
   */
  readonly config?: readonly ModuleConfigDef[];
  /**
   * system.beforeEvents.startup (EARLY EXECUTION: world state is not available).
   * Register custom commands, block/item custom components, table handlers here.
   */
  onStartup?(ctx: StartupContext): void;
  /** world.afterEvents.worldLoad: subscribe to events, start intervals, provide services. */
  onWorldLoad?(ctx: ModuleContext): void;
}
