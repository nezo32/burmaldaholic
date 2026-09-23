/**
 * The core module itself: admin/balance commands and core config.
 * Economy/HUD/config services are started by registry.ts before any onWorldLoad.
 */
import { DisplaySlotId, system, world } from '@minecraft/server';
import { setCasinoEnabled } from './casino';
import { openAdminMenu } from './admin';
import { BALANCE_OBJECTIVE, BALANCE_PROP } from './economy';
import { t, plural } from './logic/rawtext';
import type { CasinoModule } from './module';
import { runtime } from './registry';

export const coreModule: CasinoModule = {
  id: 'core',
  config: [
    { type: 'int', name: 'starting_balance', default: 100, min: 0, max: 100000, step: 50 },
    { type: 'bool', name: 'show_balance_sidebar', default: true },
  ],
  onStartup(ctx) {
    ctx.registerCommand({
      name: 'casino',
      description: 'Casino admin settings',
      permission: 'admin',
      bypassCasinoGuard: true,
      run: (player) => {
        if (player) void openAdminMenu(player);
      },
    });
    ctx.registerCommand({
      name: 'balance',
      description: 'Show your chip balance',
      run: (player) => {
        if (player) player.sendMessage(t('msg.burmaldaholic.core.balance', plural('msg.burmaldaholic.core.chips', runtime.economy.balance(player))));
      },
    });
  },
  onWorldLoad(ctx) {
    // GAME_DESIGN §2: `/scriptevent burmaldaholic:admin casino_mode true|false` (works while mode is OFF).
    system.afterEvents.scriptEventReceive.subscribe(
      (e) => {
        if (e.id !== 'burmaldaholic:admin') return;
        const [key, value] = e.message.trim().split(/\s+/);
        if (key === 'casino_mode' && (value === 'true' || value === 'false')) {
          setCasinoEnabled(value === 'true');
          ctx.log.info(`casino_mode set to ${value}`);
        }
      },
      { namespaces: ['burmaldaholic'] },
    );
    world.afterEvents.playerSpawn.subscribe(
      ctx.guard((e) => {
        if (!e.initialSpawn) return;
        const p = e.player;
        if (p.getDynamicProperty(BALANCE_PROP) === undefined) {
          ctx.economy.pay(p, ctx.config.int('starting_balance'), 'core.starting_balance');
        }
        const obj = world.scoreboard.getObjective(BALANCE_OBJECTIVE);
        if (obj && ctx.config.bool('show_balance_sidebar')) {
          world.scoreboard.setObjectiveAtDisplaySlot(DisplaySlotId.Sidebar, { objective: obj });
        }
      }),
    );
  },
};
