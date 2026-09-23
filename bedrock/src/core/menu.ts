/**
 * Casino Menu (Casino Card / `/burmaldaholic:menu`, UI.md §2): an ActionForm hub whose buttons
 * modules extend with `ctx.menu.add(...)`. Core provides Wallet, Settings and Admin (ops).
 */
import { type Player, PlayerPermissionLevel } from '@minecraft/server';
import { ActionFormData } from '@minecraft/server-ui';
import { showForm } from './forms';
import { type Raw, color, lines, t } from './logic/rawtext';
import { createLogger } from './log';

const log = createLogger('core.menu');

export interface MenuEntry {
  /** unique id, e.g. 'loan', 'vip' */
  id: string;
  /** UI.md order: wallet 10, contracts 20, loan 30, achievements 40, challenges 50, my_casino 60, rules 70, settings 80, admin 90 */
  order: number;
  label: Raw;
  /** optional button icon texture path, e.g. 'textures/items/emerald' */
  icon?: string;
  /** hide the button for this player */
  visible?(player: Player): boolean;
  open(player: Player): void | Promise<void>;
}

export const isOperator = (p: Player): boolean => p.playerPermissionLevel >= PlayerPermissionLevel.Operator;

export class MenuRegistry {
  private entries: MenuEntry[] = [];

  constructor(private readonly body: (player: Player) => Raw) {}

  /** Add or replace (same id) a hub button. */
  add(e: MenuEntry): void {
    this.entries = this.entries.filter((x) => x.id !== e.id);
    this.entries.push(e);
    this.entries.sort((a, b) => a.order - b.order);
  }

  async open(player: Player, error?: Raw): Promise<void> {
    const list = this.entries.filter((e) => {
      try {
        return e.visible?.(player) ?? true;
      } catch {
        return false;
      }
    });
    const body = error ? lines(color('§c', error), this.body(player)) : this.body(player);
    const form = new ActionFormData().title(t('gui.burmaldaholic.menu.title')).body(body);
    for (const e of list) form.button(e.label, e.icon);
    const res = await showForm(player, form);
    if (!res || res.canceled || res.selection === undefined) return;
    const e = list[res.selection];
    if (!e) return;
    try {
      await e.open(player);
    } catch (err) {
      log.error(`menu entry ${e.id} failed`, err);
    }
  }
}
