/**
 * Cashier (GAME_DESIGN §3.2, UI.md §3): blocks `burmaldaholic:cashier` and
 * `burmaldaholic:nether_cashier` (custom component `burmaldaholic:cashier`, param
 * `{ "nether": true }` adds gold-ingot exchange). Deposit all / held chips, withdraw
 * (≤ withdrawable, greedy or chosen denomination), buy/sell chips for emeralds (gold).
 * Modules add buttons (Contracts, Shop...) with `ctx.cashier.add(...)`.
 */
import { type Player, type StartupEvent, system } from '@minecraft/server';
import { ActionFormData, ModalFormData } from '@minecraft/server-ui';
import { isCasinoEnabled } from './casino';
import type { ConfigService } from './config';
import type { Economy } from './economy';
import { ModalLayout, showForm } from './forms';
import { createLogger } from './log';
import { detach } from './logic/async';
import { chipValueInInventory, countItems, giveChips, giveItems, removeItems, takeAllChips, takeHeldChips } from './items';
import type { Limits } from './limits';
import { parseAmount } from './logic/bet';
import { CHIP_VALUES, type ChipValue, buyChips, sellChips, sellCount } from './logic/economy-math';
import { type Raw, chips, color, lines as joinLines, t, unit } from './logic/rawtext';
import type { MenuEntry } from './menu';

const log = createLogger('core.cashier');
export const CASHIER_COMPONENT = 'burmaldaholic:cashier';

type Currency = { item: string; unit: 'emerald' | 'gold_ingot'; buyKey: string; sellKey: string; buyRate: number; sellRate: number };

export class Cashier {
  private extra: MenuEntry[] = [];

  constructor(
    private readonly economy: Economy,
    private readonly config: ConfigService,
    private readonly limits: Limits,
  ) {}

  /** Add a cashier button (e.g. Contracts, Shop). Same MenuEntry shape as the Casino Menu. */
  add(e: MenuEntry): void {
    this.extra = this.extra.filter((x) => x.id !== e.id);
    this.extra.push(e);
    this.extra.sort((a, b) => a.order - b.order);
  }

  registerComponent(event: StartupEvent): void {
    event.blockComponentRegistry.registerCustomComponent(CASHIER_COMPONENT, {
      onPlayerInteract: (e, p) => {
        const player = e.player;
        if (!player) return;
        const nether = (p.params as { nether?: boolean } | undefined)?.nether === true;
        system.run(() => detach(this.open(player, nether), (err) => log.error('cashier form', err)));
      },
    });
  }

  private currencies(player: Player, nether: boolean): Currency[] {
    const c = this.config;
    const gold = this.limits.tier(player) >= 2;
    const list: Currency[] = [
      {
        item: 'minecraft:emerald',
        unit: 'emerald',
        buyKey: 'gui.burmaldaholic.cashier.buy',
        sellKey: 'gui.burmaldaholic.cashier.sell',
        buyRate: c.int(gold ? 'economy.emeraldBuyRateGoldVip' : 'economy.emeraldBuyRate'),
        sellRate: c.int('economy.emeraldSellRate'),
      },
    ];
    if (nether)
      list.push({
        item: 'minecraft:gold_ingot',
        unit: 'gold_ingot',
        buyKey: 'gui.burmaldaholic.cashier.buy_gold',
        sellKey: 'gui.burmaldaholic.cashier.sell_gold',
        buyRate: c.int('economy.goldBuyRate'),
        sellRate: c.int('economy.goldSellRate'),
      });
    return list;
  }

  async open(player: Player, nether = false, error?: Raw): Promise<void> {
    if (!isCasinoEnabled()) return player.sendMessage(t('gui.burmaldaholic.error.casino_off'));
    const e = this.economy;
    const owed = e.owed(player);
    const body: Raw[] = [];
    if (error) body.push(color('§c', error));
    body.push(t('gui.burmaldaholic.common.balance', chips(e.balance(player))));
    body.push(
      e.inDefault(player)
        ? color('§c', t('gui.burmaldaholic.cashier.withdraw_blocked'))
        : owed > 0
          ? t('gui.burmaldaholic.cashier.withdrawable_loan', chips(e.withdrawable(player)), chips(owed))
          : t('gui.burmaldaholic.cashier.withdrawable', chips(e.withdrawable(player))),
    );
    const cur = this.currencies(player, nether);
    const actions: (() => Promise<void> | void)[] = [];
    const form = new ActionFormData()
      .title(t(nether ? 'block.burmaldaholic.nether_cashier' : 'gui.burmaldaholic.cashier.title'))
      .body(joinLines(...body));
    const button = (label: Raw, fn: () => Promise<void> | void, icon?: string) => {
      form.button(label, icon);
      actions.push(fn);
    };
    button(t('gui.burmaldaholic.cashier.deposit_all'), () => this.deposit(player, nether, false), 'textures/items/core/chip_100');
    button(t('gui.burmaldaholic.cashier.deposit_held'), () => this.deposit(player, nether, true));
    button(t('gui.burmaldaholic.cashier.withdraw'), () => this.withdraw(player, nether));
    for (const c of cur) {
      button(t(c.buyKey, chips(c.buyRate)), () => this.exchange(player, nether, c, 'buy'));
      button(t(c.sellKey, chips(c.sellRate)), () => this.exchange(player, nether, c, 'sell'));
    }
    for (const x of this.extra) if (x.visible?.(player) ?? true) button(x.label, () => x.open(player), x.icon);
    button(t('gui.burmaldaholic.common.close'), () => {});
    const res = await showForm(player, form);
    if (!res || res.canceled || res.selection === undefined) return;
    await actions[res.selection]?.();
  }

  private deposit(player: Player, nether: boolean, heldOnly: boolean): Promise<void> | void {
    const value = heldOnly ? takeHeldChips(player) : takeAllChips(player);
    if (value <= 0) return this.open(player, nether, t('msg.burmaldaholic.core.no_chips_to_deposit'));
    const got = this.economy.credit(player, value, 'core.cashier.deposit');
    player.sendMessage(t('msg.burmaldaholic.core.deposited', chips(got), chips(this.economy.balance(player))));
  }

  private async withdraw(player: Player, nether: boolean): Promise<void> {
    const e = this.economy;
    if (e.inDefault(player)) return this.open(player, nether, t('gui.burmaldaholic.cashier.withdraw_blocked'));
    const max = e.withdrawable(player);
    const layout = new ModalLayout();
    const form = new ModalFormData()
      .title(t('gui.burmaldaholic.cashier.withdraw'))
      .label(t('gui.burmaldaholic.cashier.withdrawable', chips(max)));
    layout.passive();
    form.textField(t('gui.burmaldaholic.common.amount'), chips(Math.min(max, 100)));
    const iAmount = layout.control();
    form.dropdown(t('gui.burmaldaholic.cashier.denomination'), [t('gui.burmaldaholic.cashier.denomination.auto'), ...CHIP_VALUES.map((v) => chips(v))]);
    const iDenom = layout.control();
    form.submitButton(t('gui.burmaldaholic.cashier.withdraw'));
    const res = await showForm(player, form);
    if (!res || res.canceled) return;
    const amount = parseAmount(String(layout.value(res, iAmount) ?? ''));
    if (!amount) return this.open(player, nether, t('gui.burmaldaholic.error.invalid_amount'));
    if (amount > e.withdrawable(player)) return this.open(player, nether, t('gui.burmaldaholic.error.insufficient_funds', chips(e.withdrawable(player))));
    const d = Number(layout.value(res, iDenom) ?? 0);
    const only = d > 0 ? (CHIP_VALUES[d - 1] as ChipValue) : undefined;
    if (!e.debit(player, amount, 'core.cashier.withdraw')) return;
    if (giveChips(player, amount, only)) player.sendMessage(t('gui.burmaldaholic.error.inventory_full'));
    player.sendMessage(t('msg.burmaldaholic.core.withdrawn', chips(amount), chips(e.balance(player))));
  }

  private async exchange(player: Player, nether: boolean, c: Currency, dir: 'buy' | 'sell'): Promise<void> {
    const e = this.economy;
    const have = countItems(player, c.item);
    const max = dir === 'buy' ? Math.min(64, have) : Math.min(64, Math.floor(e.withdrawable(player) / c.sellRate));
    if (max < 1) {
      return this.open(player, nether, dir === 'buy' ? t('gui.burmaldaholic.error.insufficient_funds', unit(c.unit, 0)) : t('gui.burmaldaholic.error.insufficient_funds', chips(e.balance(player))));
    }
    const layout = new ModalLayout();
    const form = new ModalFormData().title(t(dir === 'buy' ? c.buyKey : c.sellKey, chips(dir === 'buy' ? c.buyRate : c.sellRate)));
    form.slider(t('gui.burmaldaholic.cashier.times', ''), 1, max, { defaultValue: 1, valueStep: 1 });
    const iCount = layout.control();
    form.submitButton(t('gui.burmaldaholic.common.confirm'));
    const res = await showForm(player, form);
    if (!res || res.canceled) return;
    let n = Math.max(1, Math.min(max, Math.floor(Number(layout.value(res, iCount) ?? 1))));
    if (dir === 'sell') {
      // Re-check at submit (review m3): the loan may have defaulted, or the balance changed,
      // while the slider was open. Selling chips for emeralds is a withdrawal.
      if (e.inDefault(player)) return this.open(player, nether, t('gui.burmaldaholic.cashier.withdraw_blocked'));
      n = sellCount(n, max, e.withdrawable(player), c.sellRate);
      if (n < 1) return this.open(player, nether, t('gui.burmaldaholic.error.insufficient_funds', chips(e.withdrawable(player))));
    }
    if (dir === 'buy') {
      const removed = removeItems(player, c.item, n);
      const got = e.credit(player, buyChips(removed, c.buyRate), 'core.cashier.buy');
      player.sendMessage(t('msg.burmaldaholic.core.bought_chips', chips(got), unit(c.unit, removed)));
    } else {
      const { emeralds, cost } = sellChips(n * c.sellRate, c.sellRate);
      if (!e.debit(player, cost, 'core.cashier.sell')) return;
      if (giveItems(player, c.item, emeralds)) player.sendMessage(t('gui.burmaldaholic.error.inventory_full'));
      player.sendMessage(t('msg.burmaldaholic.core.sold_chips', chips(cost), unit(c.unit, emeralds)));
    }
  }
}

/** Chips carried as items (for Last Chance High-Stakes eligibility etc.). */
export const carriedChipValue = chipValueInInventory;
