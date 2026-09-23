/**
 * Bootstrapper: wires every CasinoModule into the engine lifecycle with per-module error
 * isolation, so one broken feature cannot take down the others.
 */
import { system, world } from '@minecraft/server';
import { Admin } from './admin';
import { isCasinoEnabled } from './casino';
import { Cashier } from './cashier';
import { registerCommand } from './commands';
import { ConfigService, ConfigStore, registerModuleConfig } from './config';
import { Earning } from './earning';
import { Economy } from './economy';
import { GoldenHour } from './golden-hour';
import { Hud } from './hud';
import { Limits } from './limits';
import { OddsService } from './logic/odds';
import { chips, lines, t } from './logic/rawtext';
import { createLogger } from './log';
import { MenuRegistry } from './menu';
import type { CasinoModule, ModuleContext } from './module';
import { Services } from './services';
import { StreakService } from './streak';
import { Tables } from './tables';
import { WagerService } from './wagers';

const config = new ConfigService();
const hud = new Hud();
const economy = new Economy(config, hud);
const streak = new StreakService(config);
const limits = new Limits(config);

/** Shared singletons (created once; exposed to modules only via their context). */
export const runtime = {
  config,
  hud,
  economy,
  streak,
  limits,
  odds: new OddsService(0, 0.95, streak),
  wagers: new WagerService(economy, limits, config, streak),
  tables: new Tables(config),
  goldenHour: new GoldenHour(),
  menu: new MenuRegistry((p) => {
    const s = streak.of(p);
    return lines(
      t('gui.burmaldaholic.common.balance', chips(economy.balance(p))),
      t('gui.burmaldaholic.vip.current', limits.tierName(limits.tier(p))),
      s === 0 ? t('gui.burmaldaholic.menu.wallet.streak_none') : t('gui.burmaldaholic.menu.wallet.streak', t(s > 0 ? 'hud.burmaldaholic.streak.lucky' : 'hud.burmaldaholic.streak.unlucky', Math.abs(s))),
    );
  }),
  cashier: new Cashier(economy, config, limits),
  admin: new Admin(config, economy),
  earning: new Earning(economy, config, isCasinoEnabled),
  services: new Services(),
};

export function bootstrap(modules: readonly CasinoModule[]): void {
  const ids = new Set<string>();
  for (const m of modules) {
    if (ids.has(m.id)) throw new Error(`duplicate module id ${m.id}`);
    ids.add(m.id);
    try {
      if (m.config) registerModuleConfig(m.id, m.config);
    } catch (e) {
      createLogger(m.id).error('config declaration rejected', e);
    }
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
          tables: runtime.tables,
          log,
        });
      } catch (e) {
        log.error('onStartup failed', e);
      }
    }
  });

  world.afterEvents.worldLoad.subscribe(() => {
    const coreLog = createLogger('core');
    const start = (what: string, fn: () => void) => {
      try {
        fn();
      } catch (e) {
        coreLog.error(`${what} failed to start`, e);
      }
    };
    start('economy', () => runtime.economy.init());
    start('hud', () => runtime.hud.start(() => isCasinoEnabled() && runtime.config.bool('core.hud.enabled')));
    start('wagers', () => runtime.wagers.start());
    start('tables', () => runtime.tables.start());
    start('earning', () => runtime.earning.start());
    for (const m of modules) {
      const log = createLogger(m.id);
      const ctx: ModuleContext = {
        moduleId: m.id,
        config: new ConfigStore(m.id, runtime.config),
        economy: runtime.economy,
        wagers: runtime.wagers,
        limits: runtime.limits,
        odds: runtime.odds,
        streak: runtime.streak,
        tables: runtime.tables,
        hud: runtime.hud,
        goldenHour: runtime.goldenHour,
        menu: runtime.menu,
        cashier: runtime.cashier,
        admin: runtime.admin,
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
