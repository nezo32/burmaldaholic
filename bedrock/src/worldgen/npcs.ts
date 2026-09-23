/**
 * Worldgen NPCs: Croupier (village casino shop), Piglin Dealer (Parlor, seats you at the nearest
 * table), Shulker Croupier (High Roller Lounge, gold scratch cards). Loan Shark / Piglin Moneylender
 * belong to the loan module; worldgen only spawns and respawns them.
 */
import { type Block, type BlockCustomComponentInstance, type Entity, ItemTypes, type Player, world } from '@minecraft/server';
import { ActionFormData } from '@minecraft/server-ui';
import {
  GAME_IDS,
  type GameId,
  type ModuleContext,
  NEWLINE,
  type Raw,
  TABLE_COMPONENT,
  chips,
  chipsAcc,
  color,
  gameLabel,
  giveItems,
  join,
  mathRng,
  showForm,
  t,
  variant,
} from '../core';
import { tableKeyOf } from '../core/logic/sessions';
import { CROUPIER_OFFERS, GREETINGS, SHULKER_OFFERS, type ShopOffer, checkPurchase } from './logic/casinos';

export const NPC_TYPES = {
  croupier: 'burmaldaholic:croupier',
  piglinDealer: 'burmaldaholic:piglin_dealer',
  shulkerCroupier: 'burmaldaholic:shulker_croupier',
} as const;

const itemName = (id: string): Raw => t(`item.burmaldaholic.${id.replace(/^burmaldaholic:/, '')}`);

export class Npcs {
  constructor(private readonly ctx: ModuleContext) {}

  subscribe(): void {
    world.afterEvents.playerInteractWithEntity.subscribe((e) => {
      const type = e.target.typeId;
      if (type !== NPC_TYPES.croupier && type !== NPC_TYPES.piglinDealer && type !== NPC_TYPES.shulkerCroupier) return;
      if (!this.ctx.isCasinoEnabled()) {
        e.player.sendMessage(t('gui.burmaldaholic.error.casino_off'));
        return;
      }
      const run = type === NPC_TYPES.croupier ? this.croupier(e.player) : type === NPC_TYPES.piglinDealer ? this.dealer(e.player, e.target) : this.shulker(e.player);
      run.catch((err: unknown) => this.ctx.log.error('npc form failed', err));
    });
  }

  private async croupier(p: Player): Promise<void> {
    const form = new ActionFormData()
      .title(t('gui.burmaldaholic.croupier.title'))
      .body(variant(mathRng, 'dialog.burmaldaholic.croupier.greeting', GREETINGS.croupier))
      .button(t('gui.burmaldaholic.croupier.shop'))
      .button(t('gui.burmaldaholic.common.close'));
    const r = await showForm(p, form);
    if (r?.selection === 0) await this.shop(p, t('gui.burmaldaholic.croupier.shop'), CROUPIER_OFFERS);
  }

  private async shulker(p: Player): Promise<void> {
    await this.shop(p, t('entity.burmaldaholic.shulker_croupier'), SHULKER_OFFERS, variant(mathRng, 'dialog.burmaldaholic.shulker_croupier.greeting', GREETINGS.shulker_croupier));
  }

  /** Offer list; buying re-opens it so players can buy several. */
  private async shop(p: Player, title: Raw, offers: readonly ShopOffer[], greeting?: Raw): Promise<void> {
    for (;;) {
      const available = offers.filter((o) => ItemTypes.get(o.item));
      const bodyLines: Raw[] = [];
      if (greeting) bodyLines.push(greeting, NEWLINE, NEWLINE);
      bodyLines.push(t('gui.burmaldaholic.common.balance', chips(this.ctx.economy.balance(p))));
      if (available.length === 0) bodyLines.push(NEWLINE, NEWLINE, t('gui.burmaldaholic.error.disabled'));
      const form = new ActionFormData().title(title).body(join(...bodyLines));
      for (const o of available) {
        const label = t('gui.burmaldaholic.worldgen.shop.offer', itemName(o.item), chips(o.price));
        form.button(
          this.ctx.limits.tier(p) < o.minTier
            ? join(label, NEWLINE, color('§4', t('gui.burmaldaholic.common.requires_vip', this.ctx.limits.tierName(o.minTier))))
            : label,
        );
      }
      form.button(t('gui.burmaldaholic.common.close'));
      const r = await showForm(p, form);
      const offer = r?.selection === undefined ? undefined : available[r.selection];
      if (!offer || !this.ctx.isCasinoEnabled()) return;
      this.buy(p, offer);
    }
  }

  private buy(p: Player, o: ShopOffer): void {
    const { economy, limits } = this.ctx;
    switch (checkPurchase(o, economy.balance(p), limits.tier(p))) {
      case 'vip':
        p.sendMessage(t('gui.burmaldaholic.error.vip_required', limits.tierName(o.minTier)));
        return;
      case 'funds':
        p.sendMessage(t('gui.burmaldaholic.error.insufficient_funds', chips(economy.balance(p))));
        return;
      case 'ok':
        if (!economy.debit(p, o.price, 'worldgen.shop')) {
          p.sendMessage(t('gui.burmaldaholic.error.insufficient_funds', chips(economy.balance(p))));
          return;
        }
        if (giveItems(p, o.item, 1)) p.sendMessage(t('gui.burmaldaholic.error.inventory_full'));
        p.sendMessage(t('msg.burmaldaholic.worldgen.bought', itemName(o.item), chipsAcc(o.price)));
    }
  }

  /** Nearest game table (a block with the core table component) around the dealer. */
  private nearestTable(npc: Entity): { block: Block; game: string; variant?: string } | undefined {
    const dim = npc.dimension;
    const c = npc.location;
    let best: { block: Block; game: string; variant?: string; d: number } | undefined;
    for (let dx = -4; dx <= 4; dx++)
      for (let dz = -4; dz <= 4; dz++)
        for (let dy = -1; dy <= 1; dy++) {
          let block: Block | undefined;
          try {
            block = dim.getBlock({ x: Math.floor(c.x) + dx, y: Math.floor(c.y) + dy, z: Math.floor(c.z) + dz });
          } catch {
            continue;
          }
          if (!block || !block.typeId.startsWith('burmaldaholic:')) continue;
          const comp = block.getComponent(TABLE_COMPONENT) as BlockCustomComponentInstance | undefined;
          const params = comp?.customComponentParameters.params as { game?: string; variant?: string } | undefined;
          if (!params?.game) continue;
          const d = dx * dx + dy * dy + dz * dz;
          if (!best || d < best.d) best = { block, game: params.game, variant: params.variant, d };
        }
    return best;
  }

  private async dealer(p: Player, npc: Entity): Promise<void> {
    const table = this.nearestTable(npc);
    const form = new ActionFormData()
      .title(t('entity.burmaldaholic.piglin_dealer'))
      .body(variant(mathRng, 'dialog.burmaldaholic.piglin_dealer.greeting', GREETINGS.piglin_dealer));
    const game = table && (GAME_IDS as readonly string[]).includes(table.game) ? (table.game as GameId) : undefined;
    if (table && game) form.button(t('gui.burmaldaholic.worldgen.dealer.play', gameLabel(game)));
    form.button(t('gui.burmaldaholic.common.close'));
    const r = await showForm(p, form);
    if (!table || !game || r?.selection !== 0 || !this.ctx.isCasinoEnabled()) return;
    const b = table.block;
    if (!b.isValid) return;
    this.ctx.tables.open(p, {
      key: tableKeyOf(b.dimension.id, b.location),
      game: table.game,
      variant: table.variant,
      dimension: b.dimension,
      location: b.center(),
      blockTypeId: b.typeId,
    });
  }
}
