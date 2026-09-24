/**
 * Ultimate Texas Hold'em module (GAME_DESIGN §21, UI.md §15).
 *  - Tables: blocks `burmaldaholic:uth_table` / `_high_roller` / `_player_banked` (core
 *    `burmaldaholic:table` component, game 'uth') and the Hold'em Dealer NPC
 *    `burmaldaholic:uth_dealer` (table key `npc:<id>`, High Roller with its tag).
 *  - Rules, settlement, strategy R, exact Trips edge: ./logic (pure, reuses the poker evaluator).
 *  - Runtime (forms, timers, wagers, player bank): ./table.ts, ./bank.ts.
 */
import { world } from '@minecraft/server';
import { type CasinoModule, type ModuleContext, type TableRef, t } from '../../core';
import { MULTIPLAYER_SERVICE, type MultiplayerApi } from '../../multiplayer/api';
import { UTH_DEALER_ENTITY, UTH_HIGH_ROLLER_TAG, UTH_SERVICE, type UthApi } from './api';
import { BankEscrow } from './bank';
import { UTH_CONFIG, blindPays, tripsPays } from './config';
import { reservationPerAnte, tripsEdge } from './logic';
import { UTH_GAME, UthTable } from './table';

const tables = new Map<string, UthTable>();

function tableFor(ctx: ModuleContext, bank: BankEscrow, ref: TableRef): UthTable {
  let tb = tables.get(ref.key);
  if (!tb) {
    tb = new UthTable({ ctx, bank }, ref, (x) => {
      if (tables.get(x.ref.key) === x) tables.delete(x.ref.key);
    });
    tables.set(ref.key, tb);
  }
  return tb;
}

export const uthModule: CasinoModule = {
  id: 'uth',
  config: UTH_CONFIG,

  onWorldLoad(ctx) {
    const bank = new BankEscrow(ctx);

    ctx.tables.register({
      id: 'uth',
      // player seats + the dealer seat (player-banked tables); the player count is checked in canJoin
      seats: () => ctx.config.int('uth.seats') + 1,
      canJoin: (player, table) => {
        if (!ctx.config.bool('uth.enabled')) return t('gui.burmaldaholic.error.disabled');
        const tb = tables.get(table.key) ?? new UthTable({ ctx, bank }, table, () => {});
        const base = tb.base();
        if (base.minTier !== undefined && ctx.limits.tier(player) < base.minTier) return t('gui.burmaldaholic.error.vip_required', ctx.limits.tierName(base.minTier));
        if (tb.seatsFull(player.id)) return t('gui.burmaldaholic.error.table_full');
        // Owner can't play at their own table, closed / broke casino, Asset Freeze.
        return ctx.wagers.check(player, UTH_GAME, table.key);
      },
      onOpen: (s, rejoined) => tableFor(ctx, bank, s.table).open(s, rejoined),
      onLeave: (s, reason, policy) => tables.get(s.table.key)?.leave(s, reason, policy),
    });

    // Hold'em Dealer NPC: the entity itself hosts a table.
    world.afterEvents.playerInteractWithEntity.subscribe(
      ctx.guard((e) => {
        const npc = e.target;
        if (npc.typeId !== UTH_DEALER_ENTITY) return;
        ctx.tables.open(e.player, {
          key: `npc:${npc.id}`,
          game: 'uth',
          variant: npc.hasTag(UTH_HIGH_ROLLER_TAG) ? 'high_roller' : undefined,
          dimension: npc.dimension,
          location: npc.location,
        });
      }),
    );
    world.afterEvents.entityRemove.subscribe(
      ctx.guard((e) => {
        if (e.typeId === UTH_DEALER_ENTITY) ctx.tables.closeTable(`npc:${e.removedEntityId}`, 'broken');
      }),
    );

    // Player banks: orphans of the previous run go back to their bankers; waiting ones on join.
    try {
      bank.returnOrphans();
    } catch (e) {
      ctx.log.error('uth: returning orphaned banks failed', e);
    }
    world.afterEvents.playerSpawn.subscribe(
      ctx.guard((e) => {
        if (e.initialSpawn) bank.onJoin(e.player);
      }),
    );

    // Owned casinos: the insolvency test uses the reservation at Ante 1 (505, §21.6).
    try {
      ctx.services.get<MultiplayerApi>(MULTIPLAYER_SERVICE)?.setWorstCase('uth', reservationPerAnte(blindPays(ctx)));
    } catch (e) {
      ctx.log.warn(`uth: multiplayer worst case not set: ${String(e)}`);
    }

    // §21.1 design decision: warn loudly about a Trips edge of 1 % or less (never auto-fix).
    if (ctx.config.bool('uth.validateEdge')) {
      const edge = tripsEdge(tripsPays(ctx));
      if (edge <= 0.01) ctx.log.warn(`uth: the Trips paytable has a house edge of ${(edge * 100).toFixed(2)} % (≤ 1 %)`);
    }

    const api: UthApi = {
      activeTables: () => [...tables.values()].filter((x) => x.isActive()).length,
      isSeated: (id) => [...tables.values()].some((x) => x.hasPlayer(id)),
      escrowedBanks: () => bank.total(),
    };
    ctx.services.provide(UTH_SERVICE, api);
  },
};
