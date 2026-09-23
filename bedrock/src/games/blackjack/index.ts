/**
 * Blackjack module (GAME_DESIGN §6, UI.md §4).
 *  - Tables: blocks `burmaldaholic:blackjack_table` / `_high_roller` (core `burmaldaholic:table`
 *    component) and the dealer NPC `burmaldaholic:blackjack_dealer` (table key `npc:<id>`).
 *  - Rules and the round state machine: ./logic (pure, unit-tested incl. RTP Monte-Carlo).
 *  - Runtime (forms, timers, wagers): ./table.ts.
 */
import { type Player, world } from '@minecraft/server';
import { type CasinoModule, type ModuleContext, type TableRef, t } from '../../core';
import { BLACKJACK_DEALER_ENTITY, BLACKJACK_HIGH_ROLLER_TAG, BLACKJACK_SERVICE, type BlackjackApi } from './api';
import { BjTable, HIGH_ROLLER, highRollerTier, pending } from './table';

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

/** Pay out / refund rounds of players who disconnected mid-round (same server run). */
function applyPending(ctx: ModuleContext, player: Player): void {
  const list = pending.get(player.id);
  if (!list) return;
  pending.delete(player.id);
  for (const p of list) {
    if (p.kind === 'refund') ctx.wagers.refund(p.ticket, player);
    else {
      ctx.wagers.settle(p.ticket, player, p.ret);
      if (p.summary) player.sendMessage(t('msg.burmaldaholic.core.auto_completed', p.summary));
    }
  }
}

export const blackjackModule: CasinoModule = {
  id: 'blackjack',

  onWorldLoad(ctx) {
    ctx.tables.register({
      id: 'blackjack',
      seats: () => ctx.config.int('blackjack.seats'),
      canJoin: (player, table) => {
        if (!ctx.config.bool('blackjack.enabled')) return t('gui.burmaldaholic.error.disabled');
        if (table.variant === HIGH_ROLLER && ctx.limits.tier(player) < highRollerTier) {
          return t('gui.burmaldaholic.error.vip_required', ctx.limits.tierName(highRollerTier));
        }
        return undefined;
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

    world.afterEvents.playerSpawn.subscribe((e) => {
      if (!e.initialSpawn || !pending.has(e.player.id)) return;
      try {
        applyPending(ctx, e.player);
      } catch (err) {
        ctx.log.error('pending blackjack payout failed', err);
      }
    });

    const api: BlackjackApi = { activeTables: () => [...tables.values()].filter((x) => x.phase !== 'betting').length };
    ctx.services.provide(BLACKJACK_SERVICE, api);
  },
};
