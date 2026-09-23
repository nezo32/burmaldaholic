/**
 * The module contract. Every feature folder exports exactly one CasinoModule from its
 * index.ts; src/modules.ts lists them all (pre-populated, never edited by feature devs).
 */
import type { CustomCommandRegistry, StartupEvent } from '@minecraft/server';
import type { ConfigDef } from './logic/config-schema';
import type { ModuleId } from './logic/ids';
import type { OddsService } from './logic/odds';
import type { CommandSpec } from './commands';
import type { ConfigStore } from './config';
import type { Economy } from './economy';
import type { Hud } from './hud';
import type { Logger } from './log';
import type { Services } from './services';

export interface StartupContext {
  readonly moduleId: ModuleId;
  /** Raw startup event (block/item custom components, dimension registry...). Early execution! */
  readonly event: StartupEvent;
  readonly customCommandRegistry: CustomCommandRegistry;
  /** Register a `/burmaldaholic:<name>` command; the handler runs deferred (system.run) and casino-guarded. */
  registerCommand(spec: CommandSpec): void;
  readonly log: Logger;
}

export interface ModuleContext {
  readonly moduleId: ModuleId;
  /** Typed accessors for this module's own config values (`<module>.<name>`). */
  readonly config: ConfigStore;
  readonly economy: Economy;
  readonly odds: OddsService;
  readonly hud: Hud;
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
  /** Config entries shown in the admin form; stored as `burmaldaholic:cfg.<id>.<name>`. */
  readonly config?: readonly ConfigDef[];
  /**
   * system.beforeEvents.startup (EARLY EXECUTION: world state is not available).
   * Register custom commands, block/item custom components here.
   */
  onStartup?(ctx: StartupContext): void;
  /** world.afterEvents.worldLoad: subscribe to events, start intervals, provide services. */
  onWorldLoad?(ctx: ModuleContext): void;
}
