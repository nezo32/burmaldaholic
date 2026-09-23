/**
 * Chaos Events module (GAME_DESIGN §13 + §14 HUD): ambient / special / big-win / jackpot /
 * sunset triggers, the nine events with §13.4 safety rules, Golden Hour (bonus payouts via
 * ctx.wagers.onSettled, state in ctx.goldenHour) and the Lucky/Unlucky streak HUD segment.
 * Other modules trigger events through the ChaosApi service (./api.ts).
 */
import { type Player, system, world } from '@minecraft/server';
import { ActionFormData } from '@minecraft/server-ui';
import { type CasinoModule, type ModuleContext, color, showForm, t } from '../core';
import { CHAOS_SERVICE, type ChaosTriggerResult } from './api';
import { ChaosEngine } from './engine';
import { CHAOS_EVENTS, isChaosEvent } from './logic/events';
import { streakSegment } from './logic/streak-hud';

function resultMessage(r: ChaosTriggerResult) {
  return t(`msg.burmaldaholic.chaos.admin.result.${r}`);
}

async function adminTrigger(engine: ChaosEngine, op: Player): Promise<void> {
  const form = new ActionFormData().title(t('gui.burmaldaholic.chaos.admin.trigger'));
  for (const e of CHAOS_EVENTS) form.button(t(`gui.burmaldaholic.chaos.event.${e}`));
  const res = await showForm(op, form);
  if (!res || res.canceled || res.selection === undefined) return;
  const e = CHAOS_EVENTS[res.selection];
  if (!e) return;
  // let the admin form close first, otherwise the event would be deferred
  system.runTimeout(() => {
    if (op.isValid) op.sendMessage(resultMessage(engine.trigger(op, e, { source: 'admin', ignoreCooldown: true })));
  }, 5);
}

export const chaosModule: CasinoModule = {
  id: 'chaos',

  onWorldLoad(ctx: ModuleContext) {
    const engine = new ChaosEngine(ctx);
    ctx.services.provide(CHAOS_SERVICE, engine);
    engine.start();

    // §14 HUD: replaces core's plain streak segment (same id) with the pulsing version.
    ctx.hud.addSegment({
      id: 'core.streak',
      order: 10,
      render: (p) => {
        const seg = streakSegment(ctx.streak.of(p), Math.floor(system.currentTick / 40));
        return seg ? color(seg.color, t(seg.key, seg.n)) : undefined;
      },
    });

    ctx.admin.addAction({ id: 'chaos.trigger', label: t('gui.burmaldaholic.chaos.admin.trigger'), run: (op) => adminTrigger(engine, op) });

    // `/scriptevent burmaldaholic:chaos <event>` (ops; runs on the source player).
    system.afterEvents.scriptEventReceive.subscribe(
      ctx.guard((e) => {
        if (e.id !== 'burmaldaholic:chaos') return;
        const src = e.sourceEntity;
        if (src?.typeId !== 'minecraft:player') return;
        const p = world.getEntity(src.id) as Player | undefined;
        const name = e.message.trim();
        if (!p || !isChaosEvent(name)) return;
        p.sendMessage(resultMessage(engine.trigger(p, name, { source: 'admin', ignoreCooldown: true })));
      }),
      { namespaces: ['burmaldaholic'] },
    );
  },
};
