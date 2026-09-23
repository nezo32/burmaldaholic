/**
 * Admin page (ops): casino mode, World settings (every config key, grouped by CONFIG.md
 * section, typed controls with ranges and translated labels), give/take chips, and actions
 * other modules add with `ctx.admin.addAction` (clear debt, reset jackpots...).
 * Also `/scriptevent burmaldaholic:config set|reset|get <key> [value]`.
 */
import { type Player, world } from '@minecraft/server';
import { ActionFormData, ModalFormData } from '@minecraft/server-ui';
import { isCasinoEnabled, setCasinoEnabled } from './casino';
import { type ConfigService, configDefs } from './config';
import type { Economy } from './economy';
import { ModalLayout, showForm } from './forms';
import { parseAmount } from './logic/bet';
import { type ConfigDef, type ConfigValue, formatValue, parseInput } from './logic/config-schema';
import { type Raw, chipsAcc, color, join, lines, lit, t } from './logic/rawtext';
import { isOperator } from './menu';

export interface AdminAction {
  id: string;
  label: Raw;
  run(admin: Player): void | Promise<void>;
}

/** Controls per ModalForm page (keeps forms short on phones). */
const PAGE = 10;

export const configLabel = (d: ConfigDef): Raw =>
  d.labelArg ? t(d.label, d.labelArg.key ? t(d.labelArg.key) : lit(d.labelArg.id ?? '')) : t(d.label);

export class Admin {
  private readonly actions: AdminAction[] = [];

  constructor(
    private readonly config: ConfigService,
    private readonly economy: Economy,
  ) {}

  addAction(a: AdminAction): void {
    this.actions.push(a);
  }

  async open(player: Player): Promise<void> {
    if (!isOperator(player)) return player.sendMessage(t('gui.burmaldaholic.error.no_permission'));
    const on = isCasinoEnabled();
    const form = new ActionFormData()
      .title(t('gui.burmaldaholic.menu.admin'))
      .body(join(t('config.burmaldaholic.core.casinoMode'), lit(': '), on ? color('§a', t('gui.burmaldaholic.common.on')) : color('§c', t('gui.burmaldaholic.common.off'))))
      .button(join(t('config.burmaldaholic.core.casinoMode'), lit(': '), t(on ? 'gui.burmaldaholic.common.off' : 'gui.burmaldaholic.common.on')))
      .button(t('gui.burmaldaholic.menu.admin.world_settings'))
      .button(t('gui.burmaldaholic.menu.admin.give_chips'))
      .button(t('gui.burmaldaholic.menu.admin.take_chips'));
    for (const a of this.actions) form.button(a.label);
    const res = await showForm(player, form);
    if (!res || res.canceled || res.selection === undefined) return;
    switch (res.selection) {
      case 0:
        setCasinoEnabled(!on);
        world.sendMessage(t(on ? 'msg.burmaldaholic.core.mode_disabled' : 'msg.burmaldaholic.core.mode_enabled'));
        return;
      case 1:
        return this.worldSettings(player);
      case 2:
      case 3:
        return this.chipsForm(player, res.selection === 2 ? 1 : -1);
      default:
        await this.actions[res.selection - 4]?.run(player);
    }
  }

  /** Section list -> pages -> typed ModalForm. */
  async worldSettings(player: Player): Promise<void> {
    const pages: { section: string; defs: ConfigDef[]; index: number; count: number }[] = [];
    const bySection = new Map<string, ConfigDef[]>();
    for (const d of configDefs.values()) {
      const list = bySection.get(d.section) ?? [];
      list.push(d);
      bySection.set(d.section, list);
    }
    for (const [section, defs] of bySection) {
      const count = Math.ceil(defs.length / PAGE);
      for (let i = 0; i < count; i++) pages.push({ section, defs: defs.slice(i * PAGE, (i + 1) * PAGE), index: i + 1, count });
    }
    const form = new ActionFormData().title(t('config.burmaldaholic.title'));
    for (const p of pages) {
      const name = t(`config.burmaldaholic.section.${p.section}`);
      form.button(p.count > 1 ? join(name, lit(' '), p.index, lit('/'), p.count) : name);
    }
    const res = await showForm(player, form);
    if (!res || res.canceled || res.selection === undefined) return;
    const page = pages[res.selection];
    if (page) await this.editPage(player, page.defs, t(`config.burmaldaholic.section.${page.section}`));
  }

  async editPage(player: Player, defs: readonly ConfigDef[], title: Raw): Promise<void> {
    const layout = new ModalLayout();
    const form = new ModalFormData().title(title);
    const idx: number[] = [];
    for (const d of defs) {
      const label = configLabel(d);
      const cur = this.config.get(d.key);
      const range = d.min !== undefined && d.max !== undefined ? t('config.burmaldaholic.range', d.min, d.max) : undefined;
      const tooltip = d.tooltip ? (range ? lines(t(d.tooltip), range) : t(d.tooltip)) : range;
      if (d.type === 'bool') form.toggle(label, { defaultValue: cur === true, tooltip });
      else if (d.type === 'enum') {
        const opts = d.options ?? [];
        form.dropdown(label, opts.map((o, i) => (d.optionLabels?.[i] ? t(d.optionLabels[i]!) : lit(o))), { defaultValueIndex: Math.max(0, opts.indexOf(String(cur))), tooltip });
      } else if ((d.type === 'int' || d.type === 'long') && d.min !== undefined && d.max !== undefined && d.max - d.min <= 100) {
        form.slider(label, d.min, d.max, { defaultValue: Number(cur), valueStep: 1, tooltip });
      } else form.textField(label, lit(formatValue(d.default)), { defaultValue: formatValue(cur), tooltip });
      idx.push(layout.control());
    }
    form.toggle(t('config.burmaldaholic.reset'), { defaultValue: false });
    const iReset = layout.control();
    form.submitButton(t('gui.burmaldaholic.common.confirm'));
    const res = await showForm(player, form);
    if (!res || res.canceled) return;
    const reset = layout.value(res, iReset) === true;
    const notes: Raw[] = [];
    defs.forEach((d, i) => {
      if (reset) return this.config.reset(d.key);
      const raw = layout.value(res, idx[i]!);
      let v: ConfigValue | undefined;
      if (d.type === 'bool') v = raw === true;
      else if (d.type === 'enum') v = d.options?.[Number(raw)];
      else if (typeof raw === 'number') v = raw;
      else v = parseInput(d, String(raw ?? ''));
      if (v === undefined) return void notes.push(t('config.burmaldaholic.invalid', configLabel(d)));
      if (JSON.stringify(v) === JSON.stringify(this.config.get(d.key))) return;
      const r = this.config.set(d.key, v);
      if (r.invalid) notes.push(t('config.burmaldaholic.invalid', configLabel(d)));
      else if (r.clamped) notes.push(t('config.burmaldaholic.clamped', configLabel(d), lit(formatValue(r.value))));
    });
    for (const n of notes) player.sendMessage(color('§e', n));
    player.sendMessage(color('§a', t('config.burmaldaholic.saved')));
  }

  /** Give (+1) or take (−1) chips from an online player. */
  private async chipsForm(admin: Player, sign: 1 | -1): Promise<void> {
    const players = world.getAllPlayers();
    const layout = new ModalLayout();
    const form = new ModalFormData()
      .title(t(sign > 0 ? 'gui.burmaldaholic.menu.admin.give_chips' : 'gui.burmaldaholic.menu.admin.take_chips'))
      .dropdown(t('gui.burmaldaholic.menu.admin.player'), players.map((p) => lit(p.name)));
    const iPlayer = layout.control();
    form.textField(t('gui.burmaldaholic.common.amount'), lit('100'));
    const iAmount = layout.control();
    const res = await showForm(admin, form);
    if (!res || res.canceled) return;
    const target = players[Number(layout.value(res, iPlayer))];
    const amount = parseAmount(String(layout.value(res, iAmount) ?? ''));
    if (!target?.isValid || !amount) return admin.sendMessage(t('gui.burmaldaholic.error.invalid_amount'));
    const done = sign > 0 ? this.economy.credit(target, amount, 'core.admin.give') : Math.min(amount, this.economy.balance(target));
    if (sign < 0) this.economy.debit(target, done, 'core.admin.take');
    admin.sendMessage(t('gui.burmaldaholic.menu.admin.done', join(lit(target.name), lit(sign > 0 ? ': +' : ': −'), chipsAcc(done))));
  }

  /** `/scriptevent burmaldaholic:config set <key> <value>` | `reset <key>` | `get <key>`. */
  scriptEvent(message: string, reply: (m: Raw) => void): void {
    const [op, key, ...rest] = message.trim().split(/\s+/);
    const d = key ? this.config.def(key) : undefined;
    if (!d) return reply(t('config.burmaldaholic.unknown_key', lit(key ?? '')));
    if (op === 'reset') {
      this.config.reset(d.key);
      return reply(t('config.burmaldaholic.saved'));
    }
    if (op === 'set') {
      const v = parseInput(d, rest.join(' '));
      if (v === undefined) return reply(t('config.burmaldaholic.invalid', configLabel(d)));
      const r = this.config.set(d.key, v);
      if (r.invalid) return reply(t('config.burmaldaholic.invalid', configLabel(d)));
      if (r.clamped) reply(t('config.burmaldaholic.clamped', configLabel(d), lit(formatValue(r.value))));
      return reply(t('config.burmaldaholic.saved'));
    }
    reply(join(configLabel(d), lit(': '), lit(formatValue(this.config.get(d.key)))));
  }
}
