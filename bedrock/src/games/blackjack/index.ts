/**
 * Blackjack module (GAME_DESIGN §6, UI.md §4).
 *  - Tables: blocks `burmaldaholic:blackjack_table` / `_high_roller` (core `burmaldaholic:table`
 *    component) and the dealer NPC `burmaldaholic:blackjack_dealer` (table key `npc:<id>`).
 *  - Rules and the round state machine: ./logic (pure, unit-tested incl. RTP Monte-Carlo).
 *  - Runtime (forms, timers, wagers): ./table.ts.
 */
import { world } from '@minecraft/server';
import { type CasinoModule, type ModuleContext, type TableRef, t } from '../../core';
import { BLACKJACK_DEALER_ENTITY, BLACKJACK_HIGH_ROLLER_TAG, BLACKJACK_SERVICE, type BlackjackApi } from './api';
import { BjTable, HIGH_ROLLER, highRollerTier, isHighRoller } from './table';

const tables = new Map<string, BjTable>();

function tableFor(ctx: ModuleContext, ref: TableRef): BjTable {
  let tb = tables.get(ref.key);
  if (!tb) {
    tb = new BjTable(ctx, ref, (x) => {
      if (tables.get(x.ref.key) === x) tables.delete(x.ref.key);
    });
    tables.set(ref.key, tb);
  }
  return tb;
}

export const blackjackModule: CasinoModule = {
  id: 'blackjack',

  onWorldLoad(ctx) {
    ctx.tables.register({
      id: 'blackjack',
      seats: () => ctx.config.int('blackjack.seats'),
      canJoin: (player, table) => {
        if (!ctx.config.bool('blackjack.enabled')) return t('gui.burmaldaholic.error.disabled');
        if (isHighRoller(ctx, table) && ctx.limits.tier(player) < highRollerTier) {
          return t('gui.burmaldaholic.error.vip_required', ctx.limits.tierName(highRollerTier));
        }
        // Owner can't play at their own table, closed / broke casino, Asset Freeze.
        return ctx.wagers.check(player, 'blackjack', table.key);
      },
      onOpen: (s, rejoined) => tableFor(ctx, s.table).open(s, rejoined),
      onLeave: (s) => tables.get(s.table.key)?.leave(s),
    });

    // Dealer NPC: the entity itself is the table.
    world.afterEvents.playerInteractWithEntity.subscribe(
      ctx.guard((e) => {
        const npc = e.target;
        if (npc.typeId !== BLACKJACK_DEALER_ENTITY) return;
        ctx.tables.open(e.player, {
          key: `npc:${npc.id}`,
          game: 'blackjack',
          variant: npc.hasTag(BLACKJACK_HIGH_ROLLER_TAG) ? HIGH_ROLLER : undefined,
          dimension: npc.dimension,
          location: npc.location,
        });
      }),
    );
    world.afterEvents.entityRemove.subscribe((e) => {
      if (e.typeId === BLACKJACK_DEALER_ENTITY) ctx.tables.closeTable(`npc:${e.removedEntityId}`, 'broken');
    });

    const api: BlackjackApi = { activeTables: () => [...tables.values()].filter((x) => x.phase !== 'betting').length };
    ctx.services.provide(BLACKJACK_SERVICE, api);
  },
};
