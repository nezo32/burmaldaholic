/**
 * The core module itself: commands, first join (starting balance, Casino Card, welcome),
 * setup form, HUD segments, Casino Menu core pages, scriptevents, block/item components.
 * Services are started by registry.ts before any onWorldLoad.
 */
import { type Player, system, world } from '@minecraft/server';
import { ModalFormData } from '@minecraft/server-ui';
import { isCasinoEnabled, setCasinoEnabled } from './casino';
import { ModalLayout, showForm } from './forms';
import { giveItems } from './items';
import { formatClock } from './logic/format';
import { chips, chipsAcc, color, lit, t } from './logic/rawtext';
import type { CasinoModule } from './module';
import { isOperator } from './menu';
import { runtime } from './registry';
import { maybeShowSetup } from './setup';

export const CASINO_CARD_ID = 'burmaldaholic:casino_card';
export const CASINO_CARD_COMPONENT = 'burmaldaholic:casino_card';
const CARD_GIVEN_PROP = 'burmaldaholic:core.card_given';

export const coreModule: CasinoModule = {
  id: 'core',
  onStartup(ctx) {
    runtime.tables.registerComponent(ctx.event);
    runtime.cashier.registerComponent(ctx.event);
    ctx.event.itemComponentRegistry.registerCustomComponent(CASINO_CARD_COMPONENT, {
      onUse: (e) => {
        const p = e.source;
        system.run(() => {
          if (isCasinoEnabled()) void runtime.menu.open(p);
          else p.sendMessage(t('gui.burmaldaholic.error.casino_off'));
        });
      },
    });
    ctx.registerCommand({
      name: 'casino',
      description: 'Casino admin settings',
      permission: 'admin',
      bypassCasinoGuard: true,
      run: (player) => {
        if (player) void runtime.admin.open(player);
      },
    });
    ctx.registerCommand({
      name: 'menu',
      description: 'Open the Casino Menu',
      run: (player) => {
        if (player) void runtime.menu.open(player);
      },
    });
    ctx.registerCommand({
      name: 'balance',
      description: 'Show your chip balance',
      run: (player) => player?.sendMessage(t('gui.burmaldaholic.common.balance', chips(runtime.economy.balance(player)))),
    });
  },

  onWorldLoad(ctx) {
    const { economy, hud, limits, streak, goldenHour, menu, admin, config } = runtime;

    // GAME_DESIGN §2.1 / CONFIG.md: scriptevents work while casino mode is OFF.
    system.afterEvents.scriptEventReceive.subscribe(
      (e) => {
        const src = e.sourceEntity?.typeId === 'minecraft:player' ? (e.sourceEntity as Player) : undefined;
        if (e.id === 'burmaldaholic:admin') {
          const [key, value] = e.message.trim().split(/\s+/);
          if (key === 'casino_mode' && (value === 'true' || value === 'false')) {
            setCasinoEnabled(value === 'true');
            world.sendMessage(t(value === 'true' ? 'msg.burmaldaholic.core.mode_enabled' : 'msg.burmaldaholic.core.mode_disabled'));
            ctx.log.info(`casino_mode set to ${value}`);
          }
        } else if (e.id === 'burmaldaholic:config') {
          admin.scriptEvent(e.message, (m) => (src ? src.sendMessage(m) : ctx.log.info(JSON.stringify(m))));
        }
      },
      { namespaces: ['burmaldaholic'] },
    );

    world.afterEvents.playerSpawn.subscribe((e) => {
      if (!e.initialSpawn) return;
      const p = e.player;
      system.runTimeout(() => {
        if (!p.isValid) return;
        void maybeShowSetup(p, config).catch((err: unknown) => ctx.log.error('setup form failed', err));
        if (ctx.isCasinoEnabled()) firstJoin(p);
      }, 40);
    });

    // HUD status line: balance · streak · VIP · Golden Hour (+ module segments).
    hud.addSegment({
      id: 'core.balance',
      order: 0,
      render: (p) => color(goldenHour.isActive() ? '§6' : '§e', t('hud.burmaldaholic.balance', economy.balance(p))),
    });
    hud.addSegment({
      id: 'core.streak',
      order: 10,
      render: (p) => {
        const s = streak.of(p);
        if (s === 0) return undefined;
        return s > 0 ? color('§6', t('hud.burmaldaholic.streak.lucky', s)) : color('§9', t('hud.burmaldaholic.streak.unlucky', -s));
      },
    });
    hud.addSegment({ id: 'core.vip', order: 20, render: (p) => t('hud.burmaldaholic.vip', limits.tierName(limits.tier(p))) });
    hud.addSegment({
      id: 'core.golden_hour',
      order: 30,
      render: () => (goldenHour.isActive() ? color('§6', t('hud.burmaldaholic.golden_hour', lit(formatClock(goldenHour.remainingTicks())))) : undefined),
    });

    // Casino Menu core pages.
    menu.add({ id: 'core.settings', order: 80, label: t('gui.burmaldaholic.menu.settings'), open: (p) => settings(p) });
    menu.add({ id: 'core.admin', order: 90, label: t('gui.burmaldaholic.menu.admin'), visible: isOperator, open: (p) => admin.open(p) });
  },
};

function firstJoin(p: Player): void {
  const { economy, config } = runtime;
  if (!economy.hasAccount(p)) {
    const start = config.int('economy.startingBalance');
    economy.openAccount(p);
    economy.credit(p, start, 'core.starting_balance');
    p.sendMessage(t('msg.burmaldaholic.core.welcome', chipsAcc(start)));
    p.sendMessage(t('msg.burmaldaholic.core.welcome_hint'));
  }
  if (config.bool('core.giveCasinoCardOnJoin') && p.getDynamicProperty(CARD_GIVEN_PROP) !== true) {
    giveItems(p, CASINO_CARD_ID, 1);
    p.setDynamicProperty(CARD_GIVEN_PROP, true);
    p.sendMessage(t('msg.burmaldaholic.core.card_given'));
  }
}

async function settings(p: Player): Promise<void> {
  const layout = new ModalLayout();
  const form = new ModalFormData().title(t('gui.burmaldaholic.menu.settings'));
  form.toggle(t('gui.burmaldaholic.menu.settings.hud'), { defaultValue: !runtime.hud.isHiddenFor(p) });
  const iHud = layout.control();
  form.submitButton(t('gui.burmaldaholic.common.confirm'));
  const res = await showForm(p, form);
  if (!res || res.canceled) return;
  runtime.hud.setHiddenFor(p, layout.value(res, iHud) === false);
}
