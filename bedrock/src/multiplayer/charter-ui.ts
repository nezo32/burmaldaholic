/**
 * Casino Charter screens (UI.md §11, Bedrock): ActionForm hub (Overview in the body) →
 * Tables (ModalForm per table: Open, Min, Max, Bots) · Bankroll (Deposit / Withdraw) · Stats ·
 * Link unlinked tables in range. Owners manage everything; operators may view and edit tables
 * but never move bankroll chips.
 */
import type { Player } from '@minecraft/server';
import { ActionFormData, ModalFormData } from '@minecraft/server-ui';
import { type ModuleContext, type Raw, ModalLayout, chips, color, isOperator, join, lines, lit, promptAmount, showForm, t, worldTick } from '../core';
import { type Casino, type Tally, dayOf, parseLimits, profit, statsFor } from './logic';
import type { Ownership } from './ownership';

export class CharterUi {
  constructor(
    private readonly ctx: ModuleContext,
    private readonly own: Ownership,
  ) {}

  private alive(c: Casino): boolean {
    return !!this.own.store.casino(c.id);
  }

  private canManage(p: Player, c: Casino): boolean {
    return this.own.isOwner(p, c) || isOperator(p);
  }

  private overview(p: Player, c: Casino): Raw {
    const b = this.ctx.economy.bankroll(c.id);
    const s = statsFor(this.own.store.stats(c.id), dayOf(worldTick()));
    return lines(
      this.own.isOwner(p, c) ? undefined : t('gui.burmaldaholic.charter.owner', c.ownerName),
      t('gui.burmaldaholic.charter.bankroll_value', chips(b.balance)),
      t('gui.burmaldaholic.charter.reserved', chips(b.reserved)),
      t('gui.burmaldaholic.charter.available', chips(Math.max(0, b.balance - b.reserved))),
      c.broke ? color('§c', t('gui.burmaldaholic.charter.status_broke')) : color('§a', t('gui.burmaldaholic.charter.status_open')),
      tallyLine('gui.burmaldaholic.charter.today', s.today),
    );
  }

  /** "My Casino" (Casino Menu): the owner's casino, or a chooser when they own several. */
  async openMine(p: Player): Promise<void> {
    const mine = this.own.store.ownedBy(p.id);
    if (mine.length <= 1) return mine[0] ? this.open(p, mine[0]) : undefined;
    const form = new ActionFormData().title(t('gui.burmaldaholic.menu.my_casino'));
    for (const c of mine) form.button(t('gui.burmaldaholic.charter.table_row', t('gui.burmaldaholic.charter.title'), lit([c.x, c.y, c.z].join(', '))));
    const res = await showForm(p, form);
    const c = res && !res.canceled && res.selection !== undefined ? mine[res.selection] : undefined;
    if (c) await this.open(p, c);
  }

  /** Hub. */
  async open(p: Player, c: Casino, error?: Raw): Promise<void> {
    if (!this.alive(c) || !this.canManage(p, c)) return;
    const owner = this.own.isOwner(p, c);
    const actions: (() => Promise<void>)[] = [];
    const form = new ActionFormData().title(t('gui.burmaldaholic.charter.title')).body(error ? lines(color('§c', error), this.overview(p, c)) : this.overview(p, c));
    form.button(t('gui.burmaldaholic.charter.tables'));
    actions.push(() => this.tables(p, c));
    if (owner) {
      form.button(t('gui.burmaldaholic.charter.bankroll'));
      actions.push(() => this.bankroll(p, c));
    }
    form.button(t('gui.burmaldaholic.charter.stats'));
    actions.push(() => this.stats(p, c));
    if (owner) {
      form.button(t('gui.burmaldaholic.charter.link_tables'));
      actions.push(() => this.link(p, c));
    }
    form.button(t('gui.burmaldaholic.common.close'));
    const res = await showForm(p, form);
    if (!res || res.canceled || res.selection === undefined) return;
    await actions[res.selection]?.();
  }

  // ---- tables ----------------------------------------------------------------------------

  private async tables(p: Player, c: Casino): Promise<void> {
    const list = this.own.store.tablesOf(c.id);
    const form = new ActionFormData().title(t('gui.burmaldaholic.charter.tables'));
    form.body(list.length ? t('gui.burmaldaholic.charter.tables_count', list.length) : t('gui.burmaldaholic.charter.no_tables'));
    for (const [key, tbl] of list) {
      const pos = key.slice(key.indexOf('|') + 1).replace(/,/g, ', ');
      const state = tbl.open ? color('§2', t('gui.burmaldaholic.charter.table_open')) : color('§4', t('gui.burmaldaholic.charter.table_closed'));
      form.button(lines(t('gui.burmaldaholic.charter.table_row', this.own.tableLabel(tbl), lit(pos)), state));
    }
    form.button(t('gui.burmaldaholic.common.back'));
    const res = await showForm(p, form);
    if (!res || res.canceled || res.selection === undefined) return;
    const picked = list[res.selection];
    if (!picked) return this.open(p, c);
    await this.tableSettings(p, c, picked[0]);
    return this.tables(p, c);
  }

  private async tableSettings(p: Player, c: Casino, key: string, error?: Raw): Promise<void> {
    const tbl = this.own.store.tables.get(key);
    if (!tbl || tbl.casinoId !== c.id || !this.alive(c)) return;
    const layout = new ModalLayout();
    const form = new ModalFormData().title(this.own.tableLabel(tbl));
    if (error) {
      form.label(color('§c', error));
      layout.passive();
    }
    form.label(t('gui.burmaldaholic.charter.limit_hint', chips(this.own.globalMax())));
    layout.passive();
    form.toggle(t('gui.burmaldaholic.charter.table_open'), { defaultValue: tbl.open });
    const iOpen = layout.control();
    form.textField(t('gui.burmaldaholic.charter.table_min'), t('gui.burmaldaholic.common.amount'), { defaultValue: tbl.min === undefined ? '' : String(tbl.min) });
    const iMin = layout.control();
    form.textField(t('gui.burmaldaholic.charter.table_max'), t('gui.burmaldaholic.common.amount'), { defaultValue: tbl.max === undefined ? '' : String(tbl.max) });
    const iMax = layout.control();
    let iBots = -1;
    if (tbl.game === 'poker') {
      form.toggle(t('gui.burmaldaholic.charter.table_bots'), { defaultValue: tbl.bots });
      iBots = layout.control();
    }
    // slots (SLOTS.md §8.6): the owner may switch the bonus buy and autoplay off at a machine
    let iBuy = -1;
    let iAuto = -1;
    if (tbl.game === 'slots') {
      form.toggle(t('gui.burmaldaholic.charter.table_slots_buy'), { defaultValue: tbl.slotsBuy !== false });
      iBuy = layout.control();
      form.toggle(t('gui.burmaldaholic.charter.table_slots_autoplay'), { defaultValue: tbl.slotsAutoplay !== false });
      iAuto = layout.control();
    }
    form.submitButton(t('gui.burmaldaholic.common.confirm'));
    const res = await showForm(p, form);
    if (!res || res.canceled) return;
    const lim = parseLimits(String(layout.value(res, iMin) ?? ''), String(layout.value(res, iMax) ?? ''), this.own.globalMax());
    if (!lim.ok) {
      const err =
        lim.error === 'invalid'
          ? t('gui.burmaldaholic.error.invalid_amount')
          : lim.error === 'min_over_max'
            ? t('gui.burmaldaholic.charter.error_min_max')
            : t('gui.burmaldaholic.error.table_max', chips(lim.limit ?? this.own.globalMax()));
      return this.tableSettings(p, c, key, err);
    }
    const cur = this.own.store.tables.get(key);
    if (!cur || cur.casinoId !== c.id) return;
    this.own.saveTable(key, {
      ...cur,
      open: layout.value(res, iOpen) === true,
      min: lim.min,
      max: lim.max,
      bots: iBots >= 0 ? layout.value(res, iBots) === true : cur.bots,
      slotsBuy: iBuy >= 0 ? layout.value(res, iBuy) === true : cur.slotsBuy,
      slotsAutoplay: iAuto >= 0 ? layout.value(res, iAuto) === true : cur.slotsAutoplay,
    });
    p.sendMessage(t('msg.burmaldaholic.multiplayer.table_saved', this.own.tableLabel(cur)));
  }

  // ---- bankroll --------------------------------------------------------------------------

  private async bankroll(p: Player, c: Casino): Promise<void> {
    if (!this.own.isOwner(p, c) || !this.alive(c)) return;
    const b = this.ctx.economy.bankroll(c.id);
    const form = new ActionFormData()
      .title(t('gui.burmaldaholic.charter.bankroll'))
      .body(
        lines(
          t('gui.burmaldaholic.charter.bankroll_value', chips(b.balance)),
          t('gui.burmaldaholic.charter.reserved', chips(b.reserved)),
          t('gui.burmaldaholic.charter.available', chips(Math.max(0, b.balance - b.reserved))),
          t('gui.burmaldaholic.common.balance', chips(this.ctx.economy.balance(p))),
        ),
      )
      .button(t('gui.burmaldaholic.charter.deposit'))
      .button(t('gui.burmaldaholic.charter.withdraw'))
      .button(t('gui.burmaldaholic.common.back'));
    const res = await showForm(p, form);
    if (!res || res.canceled || res.selection === undefined) return;
    if (res.selection === 0) await this.deposit(p, c);
    else if (res.selection === 1) await this.withdraw(p, c);
    else return this.open(p, c);
    return this.bankroll(p, c);
  }

  private async deposit(p: Player, c: Casino): Promise<void> {
    const max = this.ctx.economy.withdrawable(p);
    if (max < 1) return p.sendMessage(t('gui.burmaldaholic.error.insufficient_funds', chips(this.ctx.economy.balance(p))));
    const amount = await promptAmount(p, {
      title: t('gui.burmaldaholic.charter.deposit'),
      info: [t('gui.burmaldaholic.common.balance', chips(this.ctx.economy.balance(p)))],
      min: 1,
      max,
      sliderLabel: t('gui.burmaldaholic.common.amount'),
    });
    if (amount === undefined || !this.alive(c)) return;
    if (!this.ctx.economy.bankrollDeposit(c.id, p, amount)) {
      return p.sendMessage(t('gui.burmaldaholic.error.insufficient_funds', chips(this.ctx.economy.balance(p))));
    }
    p.sendMessage(t('msg.burmaldaholic.multiplayer.bankroll_deposit', chips(amount), chips(this.ctx.economy.bankroll(c.id).balance)));
    this.own.checkSolvency(c);
  }

  private async withdraw(p: Player, c: Casino): Promise<void> {
    const b = this.ctx.economy.bankroll(c.id);
    const room = Math.max(0, this.ctx.economy.maxBalance() - this.ctx.economy.balance(p));
    const max = Math.min(b.balance, room);
    if (max < 1 || this.ctx.economy.bankrollAvailable(c.id) < 1) {
      return p.sendMessage(t('msg.burmaldaholic.multiplayer.bankroll_reserved', chips(Math.min(room, this.ctx.economy.bankrollAvailable(c.id)))));
    }
    const amount = await promptAmount(p, {
      title: t('gui.burmaldaholic.charter.withdraw'),
      info: [t('gui.burmaldaholic.charter.available', chips(this.ctx.economy.bankrollAvailable(c.id)))],
      min: 1,
      max,
      sliderLabel: t('gui.burmaldaholic.common.amount'),
      validate: (n) => {
        const avail = this.ctx.economy.bankrollAvailable(c.id);
        return n > avail ? t('msg.burmaldaholic.multiplayer.bankroll_reserved', chips(avail)) : undefined;
      },
    });
    if (amount === undefined || !this.alive(c)) return;
    if (!this.ctx.economy.bankrollWithdraw(c.id, p, amount)) {
      return p.sendMessage(t('msg.burmaldaholic.multiplayer.bankroll_reserved', chips(this.ctx.economy.bankrollAvailable(c.id))));
    }
    p.sendMessage(t('msg.burmaldaholic.multiplayer.bankroll_withdraw', chips(amount), chips(this.ctx.economy.bankroll(c.id).balance)));
    this.own.checkSolvency(c);
  }

  // ---- stats / link ----------------------------------------------------------------------

  private async stats(p: Player, c: Casino): Promise<void> {
    const s = statsFor(this.own.store.stats(c.id), dayOf(worldTick()));
    const form = new ActionFormData()
      .title(t('gui.burmaldaholic.charter.stats'))
      .body(
        lines(
          tallyLine('gui.burmaldaholic.charter.today', s.today),
          tallyLine('gui.burmaldaholic.charter.total', s.total),
          t('gui.burmaldaholic.charter.rake', chips(s.total.rake)),
          t('gui.burmaldaholic.charter.rounds', s.total.rounds),
          t('gui.burmaldaholic.charter.tables_count', this.own.store.tablesOf(c.id).length),
        ),
      )
      .button(t('gui.burmaldaholic.common.back'));
    const res = await showForm(p, form);
    if (res && !res.canceled) return this.open(p, c);
  }

  private async link(p: Player, c: Casino): Promise<void> {
    const n = await this.own.linkInRange(p, c);
    if (!p.isValid) return;
    if (n === 0) p.sendMessage(t('msg.burmaldaholic.multiplayer.linked_none'));
    return this.open(p, c);
  }
}

function tallyLine(key: string, x: Tally): Raw {
  const pr = profit(x);
  return t(key, chips(x.handle), chips(x.paid), join(lit(pr < 0 ? '§c' : '§a'), chips(pr), lit('§r')));
}
