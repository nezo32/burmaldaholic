/**
 * Player banks for the dealer seat (GAME_DESIGN §21.9). The escrow is a core bankroll account
 * (`economy.bankroll(id)`), so seat bets placed with `house: {kind: 'bankroll', id}` get core's
 * reservation rule and bankroll settlement - also for rounds settled after a restart.
 *
 * Persisted (world properties):
 *  - `burmaldaholic:uth.banks`: tableKey -> open escrow (an orphan found on load is returned)
 * A bank whose banker is offline when banking ends is paid through core's offline credit
 * (`economy.payOutBankroll`, applied on their next join with msg.burmaldaholic.uth.bank_returned).
 */
import type { Player } from '@minecraft/server';
import { type ModuleContext, chips, t, worldJson, worldTick } from '../../core';

const BANKS_PROP = 'burmaldaholic:uth.banks';

export interface Escrow {
  tableKey: string;
  bankrollId: string;
  bankerId: string;
  bankerName: string;
}

type Banks = Record<string, Escrow>;

export class BankEscrow {
  constructor(private readonly ctx: ModuleContext) {}

  private banks(): Banks {
    const v = worldJson.read<Banks>(BANKS_PROP, {});
    return v && typeof v === 'object' ? v : {};
  }

  /** Move `amount` from the player's balance into a new escrow for this table. */
  open(player: Player, tableKey: string, amount: number): Escrow | undefined {
    const bankrollId = `uth_${worldTick().toString(36)}_${Math.floor(Math.random() * 1e9).toString(36)}`;
    if (!this.ctx.economy.transact([{ account: player, delta: -amount }, { account: { bankroll: bankrollId }, delta: amount }], 'uth.bank.escrow')) return undefined;
    const rec: Escrow = { tableKey, bankrollId, bankerId: player.id, bankerName: player.name };
    const all = this.banks();
    all[tableKey] = rec;
    worldJson.write(BANKS_PROP, all);
    return rec;
  }

  /** Bank balance and reserved chips. */
  state(e: Escrow): { balance: number; reserved: number; available: number } {
    const b = this.ctx.economy.bankroll(e.bankrollId);
    return { balance: b.balance, reserved: b.reserved, available: Math.max(0, b.balance - b.reserved) };
  }

  total(): number {
    return Object.values(this.banks()).reduce((s, e) => s + this.state(e).balance, 0);
  }

  /** Return the rest of the bank to the banker (now, or on their next join). Idempotent. */
  release(e: Escrow): void {
    const all = this.banks();
    if (all[e.tableKey]?.bankrollId === e.bankrollId) {
      delete all[e.tableKey];
      worldJson.write(BANKS_PROP, Object.keys(all).length ? all : undefined);
    }
    if (!this.ctx.economy.payOutBankroll(e.bankrollId, e.bankerId, 'uth.bank.return', (amount) => t('msg.burmaldaholic.uth.bank_returned', chips(amount)))) {
      // online transfer refused (should not happen: the balance cap drops the excess): keep it open for the next load
      all[e.tableKey] = e;
      worldJson.write(BANKS_PROP, all);
    }
  }

  /**
   * World load: every escrow still open belonged to a table of the previous run. Its drawn seat
   * rounds were already settled against it by core (wagers.parkDrawnRounds), so return it.
   */
  returnOrphans(): void {
    for (const e of Object.values(this.banks())) this.release(e);
  }
}
