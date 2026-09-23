/**
 * Scratch Cards (GAME_DESIGN §11.3, UI.md §9). Items `scratch_card` (Basic) and
 * `scratch_card_gold`; bought at the Cashier "Shop" (or from worldgen croupiers via ExtrasApi).
 *
 * Unscratched cards stack (16) and are fungible: the outcome is drawn on the FIRST scratch
 * (streak re-draw applies), one card is taken from the stack and the card in progress is stored
 * on the player (dynamic property) so closing the form keeps its progress; using any card
 * again resumes it. When the last cell is revealed the round is recorded through the wager
 * service (the card's price is the stake: it is credited back as a voucher and staked, then
 * settled with the prize) and a `scratch_card_used` is given.
 */
import type { Player } from '@minecraft/server';
import { ActionFormData } from '@minecraft/server-ui';
import {
  type Raw,
  type TableSession,
  chips,
  chipsAcc,
  color,
  countItems,
  giveItems,
  join,
  lines,
  lit,
  mathRng,
  readJson,
  removeItems,
  showForm,
  t,
  writeJson,
} from '../../core';
import {
  CELLS,
  CREEPER,
  type ScratchCard,
  type ScratchKind,
  MIN_TIER,
  drawScratch,
  isScratchCard,
  newCard,
  prizeTable,
  scratch,
  scratchRtp,
} from './logic';
import { ctx, gameEnabled, openVirtual, resultLine, triggerChaos } from './shared';

export const SCRATCH_GAME = 'extras_scratch';
export const SCRATCH_ITEMS: Readonly<Record<ScratchKind, string>> = { basic: 'burmaldaholic:scratch_card', gold: 'burmaldaholic:scratch_card_gold' };
export const SCRATCH_USED = 'burmaldaholic:scratch_card_used';
const CARD_PROP = 'burmaldaholic:extras.scratch_card';

const title = (kind: ScratchKind): Raw => t(kind === 'gold' ? 'gui.burmaldaholic.extras.scratch.title_gold' : 'gui.burmaldaholic.extras.scratch.title');

export function scratchPrice(kind: ScratchKind): number {
  return ctx().config.int(`extras.scratch.${kind}.price`);
}

export function scratchTable(kind: ScratchKind) {
  return prizeTable(ctx().config.json(`extras.scratch.${kind}.prizes`), kind);
}

const loadCard = (p: Player): ScratchCard | undefined => {
  const c = readJson<unknown>(p, CARD_PROP, undefined);
  return isScratchCard(c) ? c : undefined;
};
const saveCard = (p: Player, card: ScratchCard | undefined) => writeJson(p, CARD_PROP, card);

/** Which card kind the player just used (set by the item component before opening). */
const requested = new Map<string, ScratchKind>();

export function useScratchCard(p: Player, kind: ScratchKind): void {
  if (!gameEnabled('extras.scratch.enabled')) return p.sendMessage(t('gui.burmaldaholic.error.disabled'));
  requested.set(p.id, kind);
  openVirtual(p, SCRATCH_GAME);
}

/** Draw a new card from the player's inventory (first scratch). */
function startCard(p: Player, kind: ScratchKind): ScratchCard | undefined {
  if (removeItems(p, SCRATCH_ITEMS[kind], 1) !== 1) return undefined;
  const c = ctx();
  const price = scratchPrice(kind);
  const table = scratchTable(kind);
  const creeper = c.config.num('extras.scratch.creeperChance');
  const { result } = c.odds.draw(p.id, scratchRtp(table, price), mathRng, () => drawScratch(mathRng, table, creeper), (o) => o.prize < price);
  const card = newCard(mathRng, kind, price, table, result);
  saveCard(p, card);
  return card;
}

function cell(card: ScratchCard, i: number): Raw {
  if (i >= card.revealed) return lit('§8▒▒▒');
  const v = card.cells[i] ?? 0;
  if (v === CREEPER) return color('§2', t('gui.burmaldaholic.extras.scratch.cell.creeper'));
  const winning = card.prize > 0 && v === card.prize;
  return join(lit(winning && card.revealed >= CELLS ? '§a' : '§f'), v, lit('§r'));
}

function grid(card: ScratchCard): Raw {
  const row = (r: number) => join(cell(card, r * 3), lit(' §7|§r '), cell(card, r * 3 + 1), lit(' §7|§r '), cell(card, r * 3 + 2));
  return lines(row(0), row(1), row(2));
}

function outcomeText(card: ScratchCard): Raw {
  if (card.top) return color('§6', t('gui.burmaldaholic.extras.scratch.top_prize', chips(card.prize)));
  if (card.prize > 0) return color('§a', t('gui.burmaldaholic.extras.scratch.win', chipsAcc(card.prize)));
  if (card.creeper) return color('§2', t('gui.burmaldaholic.extras.scratch.creeper'));
  return color('§c', t('gui.burmaldaholic.extras.scratch.lose'));
}

/**
 * Record the finished card as a wager round (streak, VIP lifetime, contracts...) and pay it.
 * Returns the round's net.
 */
function settleCard(p: Player, card: ScratchCard): number {
  const c = ctx();
  const credited = c.economy.credit(p, card.price, 'scratch.card');
  if (credited === card.price) {
    const r = c.wagers.place(p, { game: 'scratch', stake: { kind: 'chips', amount: card.price }, skipLimits: true, notify: false });
    if (r.ok) return c.wagers.settle(r.ticket, p, card.prize)?.net ?? card.prize - card.price;
  }
  // Could not stake the voucher (balance cap): pay the prize directly.
  if (credited > 0) c.economy.debit(p, credited, 'scratch.card');
  if (card.prize > 0) c.economy.credit(p, card.prize, 'scratch.payout');
  return card.prize - card.price;
}

function finishCard(p: Player, card: ScratchCard): Raw {
  saveCard(p, undefined);
  const net = settleCard(p, card);
  giveItems(p, SCRATCH_USED, 1);
  const text = outcomeText(card);
  p.sendMessage(text);
  if (card.top) ctx().hud.title(p, color('§6', t('gui.burmaldaholic.extras.scratch.top_prize', chips(card.prize))));
  if (card.creeper) triggerChaos(p, 'mob_wave');
  return lines(text, resultLine(net));
}

interface ScratchData {
  busy?: boolean;
}

export async function scratchFlow(s: TableSession, rejoined: boolean): Promise<void> {
  const data = s.data as ScratchData;
  if (rejoined && data.busy) return;
  data.busy = true;
  const p = s.player;
  try {
    let kind = requested.get(p.id) ?? 'basic';
    requested.delete(p.id);
    for (;;) {
      let card = loadCard(p);
      if (!card) {
        card = startCard(p, kind);
        if (!card) return;
      }
      kind = card.kind;
      // Scratching.
      let result: Raw | undefined;
      while (!result) {
        const form = new ActionFormData()
          .title(title(card.kind))
          .body(lines(t('gui.burmaldaholic.extras.scratch.hint'), lit(''), grid(card)))
          .button(t('gui.burmaldaholic.extras.scratch.next'))
          .button(t('gui.burmaldaholic.extras.scratch.all'))
          .button(t('gui.burmaldaholic.common.close'));
        const res = await showForm(p, form);
        if (!res || res.canceled || res.selection === undefined || res.selection === 2 || !s.isActive()) return; // card keeps progress
        const done = scratch(card, res.selection === 1);
        if (done) result = finishCard(p, card);
        else saveCard(p, card);
      }
      // Result: [Play again] only when another card of this kind is in the inventory.
      const more = countItems(p, SCRATCH_ITEMS[kind]) > 0;
      const end = new ActionFormData().title(title(card.kind)).body(lines(grid(card), lit(''), result, t('gui.burmaldaholic.common.balance', chips(ctx().economy.balance(p)))));
      if (more) end.button(t('gui.burmaldaholic.common.play_again'));
      end.button(t('gui.burmaldaholic.common.close'));
      const res = await showForm(p, end);
      if (!more || !res || res.canceled || res.selection !== 0 || !s.isActive()) return;
    }
  } finally {
    data.busy = false;
    if (s.isActive()) s.leave();
  }
}

// ---- Shop (Cashier "Shop" tab, croupier NPCs) --------------------------------------------

export async function openShop(p: Player, error?: Raw): Promise<void> {
  const c = ctx();
  if (!gameEnabled('extras.scratch.enabled')) return p.sendMessage(t('gui.burmaldaholic.error.disabled'));
  const tier = c.limits.tier(p);
  const kinds: ScratchKind[] = ['basic', 'gold'];
  const form = new ActionFormData()
    .title(t('gui.burmaldaholic.cashier.shop'))
    .body(lines(error ? color('§c', error) : undefined, t('gui.burmaldaholic.common.balance', chips(c.economy.balance(p)))));
  for (const k of kinds) {
    const label = t('gui.burmaldaholic.cashier.shop.buy_item', t(`item.burmaldaholic.${k === 'gold' ? 'scratch_card_gold' : 'scratch_card'}`), chips(scratchPrice(k)));
    form.button(tier >= MIN_TIER[k] ? label : lines(label, color('§8', t('gui.burmaldaholic.common.requires_vip', c.limits.tierName(MIN_TIER[k])))));
  }
  form.button(t('gui.burmaldaholic.common.close'));
  const res = await showForm(p, form);
  if (!res || res.canceled || res.selection === undefined) return;
  const kind = kinds[res.selection];
  if (!kind) return;
  if (tier < MIN_TIER[kind]) return openShop(p, t('gui.burmaldaholic.error.vip_required', c.limits.tierName(MIN_TIER[kind])));
  const price = scratchPrice(kind);
  if (!c.economy.debit(p, price, `scratch.buy.${kind}`)) return openShop(p, t('gui.burmaldaholic.error.insufficient_funds', chips(c.economy.balance(p))));
  if (giveItems(p, SCRATCH_ITEMS[kind], 1)) p.sendMessage(t('gui.burmaldaholic.error.inventory_full'));
  p.sendMessage(t('msg.burmaldaholic.extras.scratch.bought', { translate: `item.burmaldaholic.${kind === 'gold' ? 'scratch_card_gold' : 'scratch_card'}` }, chipsAcc(price)));
  return openShop(p);
}
