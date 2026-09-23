/**
 * Chip balance. Source of truth: player dynamic property `burmaldaholic:balance` (double, holds
 * safe integers). Mirrored to scoreboard objective `burmaldaholic_balance` (int32, clamped) so it can
 * be shown on the sidebar and used in commands. Physical chip items (burmaldaholic:chip_*) are
 * converted to/from balance by the core cashier (B-core, wave 2).
 */
import { type Player, world } from '@minecraft/server';
import { applyDelta, toScore } from './logic/ledger';
import { plural } from './logic/rawtext';

export const BALANCE_PROP = 'burmaldaholic:balance';
export const BALANCE_OBJECTIVE = 'burmaldaholic_balance';

export type BalanceListener = (player: Player, balance: number, delta: number, reason: string) => void;

export class Economy {
  private readonly listeners: BalanceListener[] = [];

  init(): void {
    const sb = world.scoreboard;
    if (!sb.getObjective(BALANCE_OBJECTIVE)) sb.addObjective(BALANCE_OBJECTIVE, BALANCE_OBJECTIVE);
  }

  onChange(l: BalanceListener): void {
    this.listeners.push(l);
  }

  balance(player: Player): number {
    const v = player.getDynamicProperty(BALANCE_PROP);
    return typeof v === 'number' ? v : 0;
  }

  /** Adds (delta > 0) or removes (delta < 0) chips. Returns false if it would go negative. */
  change(player: Player, delta: number, reason: string): boolean {
    const r = applyDelta(this.balance(player), delta);
    if (!r.ok) return false;
    player.setDynamicProperty(BALANCE_PROP, r.balance);
    world.scoreboard.getObjective(BALANCE_OBJECTIVE)?.setScore(player, toScore(r.balance));
    for (const l of this.listeners) l(player, r.balance, delta, reason);
    return true;
  }

  /** Take a bet; returns false (and tells the player) if they cannot afford it. */
  charge(player: Player, amount: number, reason: string): boolean {
    if (this.change(player, -amount, reason)) return true;
    player.sendMessage(plural('msg.burmaldaholic.core.not_enough_chips', amount));
    return false;
  }

  pay(player: Player, amount: number, reason: string): void {
    this.change(player, amount, reason);
  }
}
