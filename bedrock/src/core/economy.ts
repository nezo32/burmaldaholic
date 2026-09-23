/**
 * Chip balance, transactions and owner bankrolls (GAME_DESIGN §3, §18.2).
 *
 * Source of truth: player dynamic property `burmaldaholic:balance` (safe integer), mirrored to
 * scoreboard objective `burmaldaholic_balance` (int32, clamped). Balances never go negative
 * and are capped at `economy.maxBalance` (excess is lost with a message). Debt is a separate
 * number owned by the loan module (see `setDebtProvider`).
 */
import { type Player, system, world } from '@minecraft/server';
import type { ConfigService } from './config';
import type { Hud } from './hud';
import { applyCredit, isChipAmount, withdrawable } from './logic/economy-math';
import { type BankrollState, type TxLeg, applyDelta, bankrollAvailable, planTransaction, releaseBankroll, settleBankroll, toScore, tryReserve } from './logic/ledger';
import { withChips, withMessage } from './logic/offline';
import { type Raw, chips, t } from './logic/rawtext';
import { offlineStore, onlinePlayer } from './offline';
import { worldJson } from './store';

export const BALANCE_PROP = 'burmaldaholic:balance';
export const BALANCE_OBJECTIVE = 'burmaldaholic_balance';
const BANKROLL_PREFIX = 'burmaldaholic:core.bankroll.';

export type BalanceListener = (player: Player, balance: number, delta: number, reason: string) => void;

/** Provided by the loan module (`ctx.economy.setDebtProvider`). Default: no debt. */
export interface DebtProvider {
  owed(player: Player): number;
  inDefault(player: Player): boolean;
}

/** Who banks a round: the world bank (mints/burns) or an owned casino's bankroll. */
export type HouseRef = { kind: 'bank' } | { kind: 'bankroll'; id: string };
export const BANK: HouseRef = { kind: 'bank' };

/** One leg of `transact`: a player's balance, a bankroll, or the bank. */
export type TxAccount = Player | { bankroll: string } | 'bank';
export interface TxOp {
  account: TxAccount;
  delta: number;
}

export class Economy {
  private readonly listeners: BalanceListener[] = [];
  private debt: DebtProvider = { owed: () => 0, inDefault: () => false };
  private readonly toasts = new Map<string, { amount: number; source: Raw; tick: number }>();

  constructor(
    private readonly config: ConfigService,
    private readonly hud: Hud,
  ) {}

  init(): void {
    const sb = world.scoreboard;
    if (!sb.getObjective(BALANCE_OBJECTIVE)) sb.addObjective(BALANCE_OBJECTIVE, BALANCE_OBJECTIVE);
  }

  onChange(l: BalanceListener): void {
    this.listeners.push(l);
  }

  setDebtProvider(p: DebtProvider): void {
    this.debt = p;
  }

  maxBalance(): number {
    return this.config.num('economy.maxBalance');
  }

  balance(player: Player): number {
    const v = player.getDynamicProperty(BALANCE_PROP);
    return typeof v === 'number' && Number.isSafeInteger(v) ? v : 0;
  }

  /** Whether the player has ever had a balance (first-join detection). */
  hasAccount(player: Player): boolean {
    return player.getDynamicProperty(BALANCE_PROP) !== undefined;
  }

  /** Create the balance property (0) if missing. */
  openAccount(player: Player): void {
    if (!this.hasAccount(player)) player.setDynamicProperty(BALANCE_PROP, 0);
  }

  owed(player: Player): number {
    return this.debt.owed(player);
  }

  inDefault(player: Player): boolean {
    return this.debt.inDefault(player);
  }

  /** balance − owed, or 0 while in default (cashier withdraw, player transfers). */
  withdrawable(player: Player): number {
    return withdrawable(this.balance(player), this.debt.owed(player), this.debt.inDefault(player));
  }

  private write(player: Player, next: number, delta: number, reason: string): void {
    player.setDynamicProperty(BALANCE_PROP, next);
    try {
      world.scoreboard.getObjective(BALANCE_OBJECTIVE)?.setScore(player, toScore(next));
    } catch {
      /* scoreboard identity not ready yet (first tick of join) */
    }
    for (const l of this.listeners) l(player, next, delta, reason);
  }

  /**
   * Credit chips (capped at economy.maxBalance; the excess is lost and the player told).
   * Returns the amount actually credited.
   */
  credit(player: Player, amount: number, reason: string): number {
    if (!(amount > 0)) return 0;
    const r = applyCredit(this.balance(player), Math.floor(amount), this.maxBalance());
    if (r.credited > 0) this.write(player, r.balance, r.credited, reason);
    if (r.capped) player.sendMessage(t('msg.burmaldaholic.core.balance_capped'));
    return r.credited;
  }

  /**
   * Credit a player who may be offline (owner payouts, offline round results): credited now
   * when online, otherwise queued and applied on their next join. `message` is sent with it.
   */
  creditById(playerId: string, amount: number, reason: string, message?: Raw): void {
    const p = onlinePlayer(playerId);
    if (p) {
      if (amount > 0) this.credit(p, amount, reason);
      if (message) p.sendMessage(message);
      return;
    }
    offlineStore.update(playerId, (e) => (message ? withMessage(withChips(e, amount), message) : withChips(e, amount)));
  }

  /**
   * Debit exactly `amount`; false (nothing changes) if the balance is too low. 0 is a no-op;
   * a negative or non-integer amount is refused (review m9: `debit(-x)` used to credit x past
   * economy.maxBalance).
   */
  debit(player: Player, amount: number, reason: string): boolean {
    if (amount === 0) return true;
    if (!isChipAmount(amount)) return false;
    const r = applyDelta(this.balance(player), -amount);
    if (!r.ok) return false;
    this.write(player, r.balance, -amount, reason);
    return true;
  }

  /** Low-level signed change (debit fails on overdraft; credit is capped). */
  change(player: Player, delta: number, reason: string): boolean {
    if (delta >= 0) {
      this.credit(player, delta, reason);
      return true;
    }
    return this.debit(player, -delta, reason);
  }

  /** Take a bet; returns false after telling the player they cannot afford it. */
  charge(player: Player, amount: number, reason: string): boolean {
    if (this.debit(player, amount, reason)) return true;
    player.sendMessage(t('gui.burmaldaholic.error.insufficient_funds', chips(this.balance(player))));
    return false;
  }

  /** Credit winnings. */
  pay(player: Player, amount: number, reason: string): void {
    this.credit(player, amount, reason);
  }

  /**
   * Credit an earning (ores, mobs, trades, contracts...) with the merged action-bar toast
   * `msg.burmaldaholic.core.earned` "+20 chips (Diamond Ore)" (toasts within 20 ticks merge).
   */
  earn(player: Player, amount: number, source: Raw, reason: string): number {
    const got = this.credit(player, amount, reason);
    if (got <= 0) return 0;
    const now = system.currentTick;
    const prev = this.toasts.get(player.id);
    const total = prev && now - prev.tick <= 20 ? prev.amount + got : got;
    this.toasts.set(player.id, { amount: total, source, tick: now });
    this.hud.actionbar(player, 'core.earned', t('msg.burmaldaholic.core.earned', chips(total), source), 5, 40);
    return got;
  }

  /** Move chips between two players (fails without change if `from` can't afford it). */
  transfer(from: Player, to: Player, amount: number, reason: string): boolean {
    return this.transact([
      { account: from, delta: -amount },
      { account: to, delta: amount },
    ], reason);
  }

  /**
   * Atomic multi-leg transaction: every leg succeeds or nothing changes. Player credits above
   * the cap are dropped (told); bankroll debits may not touch reserved chips; 'bank' is
   * unlimited. Example (poker pot to winner, rake to owner):
   *   economy.transact([{account: winner, delta: pot - rake}, {account: {bankroll: id}, delta: rake}, {account: 'bank', delta: -pot}], 'poker.pot')
   */
  transact(ops: readonly TxOp[], reason: string): boolean {
    const players = new Map<string, Player>();
    const legs: TxLeg[] = ops.map((o) => {
      if (o.account === 'bank') return { account: 'bank', delta: o.delta };
      if ('bankroll' in o.account) return { account: `bankroll:${o.account.bankroll}`, delta: o.delta };
      players.set(o.account.id, o.account);
      return { account: `player:${o.account.id}`, delta: o.delta };
    });
    const max = this.maxBalance();
    const plan = planTransaction(legs, (acc) => {
      if (acc === 'bank') return { balance: Infinity };
      if (acc.startsWith('bankroll:')) {
        const b = this.bankroll(acc.slice(9));
        return { balance: b.balance, locked: b.reserved };
      }
      return { balance: this.balance(players.get(acc.slice(7))!), max };
    });
    if (!plan.ok) return false;
    for (const [acc, next] of plan.balances) {
      if (acc.startsWith('bankroll:')) {
        const id = acc.slice(9);
        this.saveBankroll(id, { ...this.bankroll(id), balance: next });
      } else if (acc.startsWith('player:')) {
        const p = players.get(acc.slice(7))!;
        this.write(p, next, next - this.balance(p), reason);
        if (plan.dropped.has(acc)) p.sendMessage(t('msg.burmaldaholic.core.balance_capped'));
      }
    }
    return true;
  }

  // ---- bankrolls (owned casinos) -------------------------------------------------------

  bankroll(id: string): BankrollState {
    const b = worldJson.read<Partial<BankrollState>>(BANKROLL_PREFIX + id, {});
    return { balance: typeof b.balance === 'number' ? b.balance : 0, reserved: typeof b.reserved === 'number' ? b.reserved : 0 };
  }

  private saveBankroll(id: string, b: BankrollState): void {
    worldJson.write(BANKROLL_PREFIX + id, b);
  }

  /** Chips the owner may withdraw (bankroll − reserved). */
  bankrollAvailable(id: string): number {
    return bankrollAvailable(this.bankroll(id));
  }

  /** Owner deposits from their balance into the bankroll. */
  bankrollDeposit(id: string, owner: Player, amount: number): boolean {
    return this.transact([{ account: owner, delta: -amount }, { account: { bankroll: id }, delta: amount }], 'core.bankroll.deposit');
  }

  /** Owner withdraws unreserved bankroll chips to their balance. */
  bankrollWithdraw(id: string, owner: Player, amount: number): boolean {
    if (amount > this.bankrollAvailable(id)) return false;
    return this.transact([{ account: { bankroll: id }, delta: -amount }, { account: owner, delta: amount }], 'core.bankroll.withdraw');
  }

  /** Reservation rule (§18.2). Used by the wager service; false = exposure too large. */
  reserve(id: string, worstCase: number): boolean {
    const next = tryReserve(this.bankroll(id), worstCase);
    if (!next) return false;
    this.saveBankroll(id, next);
    return true;
  }

  release(id: string, reserved: number): void {
    this.saveBankroll(id, releaseBankroll(this.bankroll(id), reserved));
  }

  /** Stake in, payout out, reservation released. */
  settleBankroll(id: string, o: { reserved: number; stake: number; payout: number }): void {
    this.saveBankroll(id, settleBankroll(this.bankroll(id), o));
  }

  /** Remove a bankroll (charter broken); returns what was left for the owner. */
  closeBankroll(id: string): number {
    const b = this.bankroll(id);
    worldJson.write(BANKROLL_PREFIX + id, undefined);
    return bankrollAvailable(b);
  }

  /** After a restart no round is open any more: drop every stale reservation. */
  resetReservations(): void {
    for (const id of world.getDynamicPropertyIds()) {
      if (!id.startsWith(BANKROLL_PREFIX)) continue;
      const bid = id.slice(BANKROLL_PREFIX.length);
      const b = this.bankroll(bid);
      if (b.reserved) this.saveBankroll(bid, { balance: b.balance, reserved: 0 });
    }
  }
}
