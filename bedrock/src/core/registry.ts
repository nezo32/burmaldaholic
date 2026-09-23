/**
 * Bootstrapper: wires every CasinoModule into the engine lifecycle with per-module error
 * isolation, so one broken feature cannot take down the others.
 */
import { system, world } from '@minecraft/server';
import { isCasinoEnabled } from './casino';
import { registerCommand } from './commands';
import { ConfigStore, registerConfig } from './config';
import { Economy } from './economy';
import { Hud } from './hud';
import { OddsService } from './logic/odds';
import { createLogger } from './log';
import type { CasinoModule, ModuleContext } from './module';
import { Services } from './services';

/** Shared singletons (created once; exposed to modules only via their context). */
export const runtime = {
  economy: new Economy(),
  odds: new OddsService(),
  hud: new Hud(),
  services: new Services(),
};

export function bootstrap(modules: readonly CasinoModule[]): void {
  const ids = new Set<string>();
  for (const m of modules) {
    if (ids.has(m.id)) throw new Error(`duplicate module id ${m.id}`);
    ids.add(m.id);
    if (m.config) registerConfig(m.id, m.config);
  }

  system.beforeEvents.startup.subscribe((event) => {
    for (const m of modules) {
      const log = createLogger(m.id);
      try {
        m.onStartup?.({
          moduleId: m.id,
          event,
          customCommandRegistry: event.customCommandRegistry,
          registerCommand: (spec) => registerCommand(event.customCommandRegistry, spec, log),
          log,
        });
      } catch (e) {
        log.error('onStartup failed', e);
      }
    }
  });

  world.afterEvents.worldLoad.subscribe(() => {
    runtime.economy.init();
    runtime.hud.start();
    for (const m of modules) {
      const log = createLogger(m.id);
      const ctx: ModuleContext = {
        moduleId: m.id,
        config: new ConfigStore(m.id),
        economy: runtime.economy,
        odds: runtime.odds,
        hud: runtime.hud,
        services: runtime.services,
        log,
        isCasinoEnabled,
        guard:
          (fn) =>
          (...args) => {
            if (!isCasinoEnabled()) return;
            try {
              fn(...args);
            } catch (e) {
              log.error('handler failed', e);
            }
          },
      };
      try {
        m.onWorldLoad?.(ctx);
      } catch (e) {
        log.error('onWorldLoad failed', e);
      }
    }
  });
}
