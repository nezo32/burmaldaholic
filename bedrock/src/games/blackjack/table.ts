/**
 * Blackjack table runtime (GAME_DESIGN §6.3): one BjTable per table key (block or dealer NPC).
 * Owns the shoe, the betting lobby, the current BlackjackRound (pure logic), the per-decision
 * timers and every form. Money only moves through ctx.wagers (place / raise / settle / refund).
 *
 *   betting  -> seated players bet ("Deal"); starts when all seated players have bet or
 *               blackjack.betTimerTicks after the first bet (one player: immediately)
 *   playing  -> insurance (timer -> No), player turns (timer -> Stand), dealer, settle
 *   result   -> result forms, 60 ticks -> betting
 *
 * Leaving: before the deal the bet is refunded; mid-round the seat auto-stands and is settled
 * with the others (GAME_DESIGN §4.1). A disconnected player's payout is applied when they
 * rejoin (core cannot credit offline players) — see `pending`.
 */
import { type Player, system } from '@minecraft/server';
import { ActionFormData, uiManager } from '@minecraft/server-ui';
import {
  HudPriority,
  type ModuleContext,
  type Raw,
  type TableLimits,
  type TableRef,
  type TableSession,
  type WagerTicket,
  chips,
  color,
  duration,
  isFormOpen,
  join,
  lines,
  lit,
  mathRng,
  promptAmount,
  showForm,
  t,
} from '../../core';
import { type Action, BlackjackRound, type BlackjackRules, Shoe, maxInsurance, normalizeRules } from './logic';
import { netRaw, summaryRaw, tableRaw } from './render';

export const HIGH_ROLLER = 'high_roller';
/** Minimum VIP tier for High-Roller tables (Gold, GAME_DESIGN §6.4). */
const HIGH_ROLLER_TIER = 2;
const RESULT_TICKS = 60;
const HUD = 'blackjack.table';

interface Bet {
  player: Player;
  seat: number;
  amount: number;
  ticket: WagerTicket;
}

interface Participant {
  player: Player;
  playerId: string;
  name: string;
  seat: number;
  ticket: WagerTicket;
  paid: boolean;
  /** left / disconnected mid-round: auto-stand, settle without forms */
  away: boolean;
}

export type Pending = { kind: 'settle' | 'refund'; ticket: WagerTicket; ret: number; summary?: Raw; net: number };

/** Payouts/refunds of players who disconnected, applied on their next spawn (same server run). */
export const pending = new Map<string, Pending[]>();

export function readRules(ctx: ModuleContext): BlackjackRules {
  const c = ctx.config;
  return normalizeRules({
    decks: c.int('blackjack.decks'),
    penetration: c.num('blackjack.penetration'),
    dealerHitsSoft17: c.bool('blackjack.dealerHitsSoft17'),
    blackjackPayout: c.num('blackjack.blackjackPayout'),
    doubleAfterSplit: c.bool('blackjack.doubleAfterSplit'),
    maxHands: c.int('blackjack.maxHands'),
    resplitAces: c.bool('blackjack.resplitAces'),
    insurance: c.bool('blackjack.insurance'),
    lateSurrender: c.bool('blackjack.lateSurrender'),
  });
}

export function limitsFor(ctx: ModuleContext, ref: TableRef): TableLimits {
  if (ref.variant === HIGH_ROLLER) {
    return {
      min: ctx.config.int('blackjack.highRollerMinBet'),
      tierMultiplier: ctx.config.num('blackjack.highRollerMaxMultiplier'),
      minTier: HIGH_ROLLER_TIER,
    };
  }
  return { min: ctx.config.int('blackjack.minBet') };
}

export const highRollerTier = HIGH_ROLLER_TIER;

export class BjTable {
  phase: 'betting' | 'playing' | 'result' = 'betting';
  private readonly bets = new Map<string, Bet>();
  private round: BlackjackRound | undefined;
  private readonly parts = new Map<number, Participant>();
  private readonly lastBet = new Map<string, number>();
  private readonly intents = new Map<string, 'again' | 'change'>();
  private readonly playerNames = new Map<string, string>();
  private shoe: Shoe | undefined;
  private betTimer: number | undefined;
  private timer: number | undefined;
  private deadline = 0;

  constructor(
    private readonly ctx: ModuleContext,
    readonly ref: TableRef,
    private readonly onEmpty: (t: BjTable) => void,
  ) {}

  // ---- helpers ----------------------------------------------------------------------------

  private sessions(): TableSession[] {
    return this.ctx.tables.sessionsAt(this.ref.key);
  }

  private sessionOf(player: Player): TableSession | undefined {
    const s = this.ctx.tables.sessionOf(player);
    return s && s.table.key === this.ref.key ? s : undefined;
  }

  private title(): Raw {
    return t(this.ref.variant === HIGH_ROLLER ? 'gui.burmaldaholic.blackjack.title_high_roller' : 'gui.burmaldaholic.blackjack.title');
  }

  private partOf(playerId: string): Participant | undefined {
    for (const p of this.parts.values()) if (p.playerId === playerId) return p;
    return undefined;
  }

  private names(): Map<number, string> {
    return new Map([...this.parts.values()].map((p) => [p.seat, p.name]));
  }

  private hud(player: Player, msg: Raw, ttl = 60): void {
    if (player.isValid) this.ctx.hud.actionbar(player, HUD, msg, HudPriority.game, ttl);
  }

  private hudAll(msg: Raw, ttl = 60, except?: string): void {
    for (const s of this.sessions()) if (s.playerId !== except) this.hud(s.player, msg, ttl);
  }

  private closeForms(player: Player): void {
    if (player.isValid) uiManager.closeAllForms(player);
  }

  private clearTimer(): void {
    if (this.timer !== undefined) system.clearRun(this.timer);
    this.timer = undefined;
  }

  private clearBetTimer(): void {
    if (this.betTimer !== undefined) system.clearRun(this.betTimer);
    this.betTimer = undefined;
  }

  private startTimer(ticks: number, fn: () => void): void {
    this.clearTimer();
    this.deadline = system.currentTick + ticks;
    this.timer = system.runTimeout(() => {
      this.timer = undefined;
      this.safe(fn);
    }, Math.max(1, ticks));
  }

  private safe(fn: () => void | Promise<void>): void {
    try {
      const r = fn();
      if (r instanceof Promise) r.catch((e: unknown) => this.ctx.log.error('blackjack table', e));
    } catch (e) {
      this.ctx.log.error('blackjack table', e);
    }
  }

  private timeLeft(): number {
    return Math.max(0, this.deadline - system.currentTick);
  }

  dispose(): void {
    this.clearTimer();
    this.clearBetTimer();
  }

  // ---- entry points (TableHandler) ----------------------------------------------------------

  open(s: TableSession, rejoined: boolean): void {
    this.playerNames.set(s.playerId, s.player.name);
    if (!rejoined) {
      for (const o of this.sessions()) if (o.playerId !== s.playerId) o.player.sendMessage(t('msg.burmaldaholic.blackjack.player_joined', lit(s.player.name)));
    }
    this.safe(() => this.showState(s));
  }

  leave(s: TableSession): void {
    const name = this.playerNames.get(s.playerId) ?? '';
    this.playerNames.delete(s.playerId);
    for (const o of this.sessions()) if (o.playerId !== s.playerId) o.player.sendMessage(t('msg.burmaldaholic.blackjack.player_left', lit(name)));
    this.intents.delete(s.playerId);

    if (this.phase === 'betting') {
      const b = this.bets.get(s.playerId);
      if (b) {
        this.bets.delete(s.playerId);
        if (b.player.isValid) this.ctx.wagers.refund(b.ticket, b.player);
        else addPending(s.playerId, { kind: 'refund', ticket: b.ticket, ret: b.ticket.value, net: 0 });
      }
      if (!this.bets.size) this.clearBetTimer();
      else this.startIfAllReady();
    } else if (this.phase === 'playing' && this.round) {
      const part = this.partOf(s.playerId);
      if (part && !part.paid) {
        part.away = true;
        const r = this.round;
        const before = r.phase;
        const wasCurrent = r.current()?.seat.seat === part.seat;
        r.standAll(part.seat);
        if (wasCurrent || r.phase !== before) this.step();
      }
    }
    this.maybeEmpty();
  }

  private maybeEmpty(): void {
    if (this.phase === 'betting' && !this.bets.size && !this.sessions().length) {
      this.dispose();
      this.onEmpty(this);
    }
  }

  /** Show whatever this player should see now (also used when they re-open the table). */
  private async showState(s: TableSession): Promise<void> {
    if (!s.isActive()) return;
    if (this.phase === 'betting') return this.bets.has(s.playerId) ? this.showLobby(s) : this.promptBet(s);
    const part = this.partOf(s.playerId);
    const r = this.round;
    if (this.phase === 'playing' && r && part && !part.away) {
      if (r.insuranceOffer(part.seat)) return this.showInsurance(part);
      if (r.current()?.seat.seat === part.seat) return this.showTurn(part);
    }
    if (this.phase === 'result' && part && !part.away) return this.showResult(part);
    return this.showWatch(s);
  }

  // ---- betting ------------------------------------------------------------------------------

  private async promptBet(s: TableSession): Promise<void> {
    const p = s.player;
    const limits = limitsFor(this.ctx, this.ref);
    const range = this.ctx.limits.range(p, limits);
    if (range.max < range.min) {
      p.sendMessage(this.ctx.limits.check(p, range.min, limits) ?? t('gui.burmaldaholic.error.table_max', chips(range.max)));
      return;
    }
    const rules = readRules(this.ctx);
    const info: Raw[] = [
      t('gui.burmaldaholic.common.balance', chips(this.ctx.economy.balance(p))),
      t('gui.burmaldaholic.common.limits', range.min, range.max),
    ];
    if (rules.decks === 6 && !rules.dealerHitsSoft17 && rules.blackjackPayout === 1.5) info.push(color('§7', t('gui.burmaldaholic.blackjack.rules.1')));
    if (rules.dealerHitsSoft17) info.push(color('§7', t('gui.burmaldaholic.blackjack.rules.h17')));
    if (rules.doubleAfterSplit && rules.maxHands === 4) info.push(color('§7', t('gui.burmaldaholic.blackjack.rules.2')));
    if (!rules.resplitAces && rules.insurance) info.push(color('§7', t('gui.burmaldaholic.blackjack.rules.3')));
    if (this.bets.size) info.push(t('gui.burmaldaholic.common.waiting_players'));
    const amount = await promptAmount(p, {
      title: t('gui.burmaldaholic.blackjack.bet_title'),
      info,
      min: range.min,
      max: range.max,
      default: this.lastBet.get(s.playerId) ?? range.min,
      submit: t('gui.burmaldaholic.common.deal'),
      validate: (a) => this.ctx.limits.check(p, a, limits, this.ctx.economy.balance(p)),
    });
    if (amount === undefined || !s.isActive()) return;
    if (this.phase !== 'betting' || this.bets.has(s.playerId)) return this.showState(s);
    this.placeBet(s, amount);
  }

  /** "Play again" with the same bet. */
  private quickBet(s: TableSession, amount: number | undefined): void {
    const p = s.player;
    const limits = limitsFor(this.ctx, this.ref);
    const err = amount === undefined ? undefined : this.ctx.limits.check(p, amount, limits, this.ctx.economy.balance(p));
    if (amount === undefined || err) {
      if (err) p.sendMessage(err);
      this.safe(() => this.promptBet(s));
      return;
    }
    this.placeBet(s, amount);
  }

  private placeBet(s: TableSession, amount: number): void {
    const p = s.player;
    const r = this.ctx.wagers.place(p, {
      game: 'blackjack',
      stake: { kind: 'chips', amount },
      limits: limitsFor(this.ctx, this.ref),
      // GAME_DESIGN §18.2: 4 hands doubled + insurance
      worstCase: amount * 8 + maxInsurance(amount) * 3,
    });
    if (!r.ok) return;
    this.bets.set(s.playerId, { player: p, seat: s.seat, amount, ticket: r.ticket });
    this.lastBet.set(s.playerId, amount);
    const others = this.sessions().filter((o) => !this.bets.has(o.playerId));
    if (this.bets.size === 1 && others.length) {
      const ticks = this.ctx.config.int('blackjack.betTimerTicks');
      this.clearBetTimer();
      this.betTimer = system.runTimeout(() => {
        this.betTimer = undefined;
        this.safe(() => this.startRound());
      }, ticks);
      this.deadline = system.currentTick + ticks;
      for (const o of others) {
        this.hud(o.player, t('gui.burmaldaholic.blackjack.betting_open'), 40);
        o.player.sendMessage(t('msg.burmaldaholic.blackjack.round_starts_in', duration(ticks)));
        if (!isFormOpen(o.player)) this.safe(() => this.promptBet(o));
      }
    }
    if (!this.startIfAllReady()) this.hud(p, t('gui.burmaldaholic.common.waiting_players'), 100);
  }

  private startIfAllReady(): boolean {
    if (this.phase !== 'betting' || !this.bets.size) return false;
    if (this.sessions().some((s) => !this.bets.has(s.playerId))) return false;
    this.startRound();
    return true;
  }

  private async showLobby(s: TableSession): Promise<void> {
    const body: Raw[] = [];
    for (const o of this.sessions()) {
      const b = this.bets.get(o.playerId);
      body.push(t('gui.burmaldaholic.blackjack.seat_player', o.seat, lit(o.player.name)));
      body.push(join(lit('   '), b ? t('gui.burmaldaholic.common.bet_amount', chips(b.amount)) : color('§7', t('gui.burmaldaholic.blackjack.no_bet'))));
    }
    body.push(lit(''));
    body.push(this.betTimer !== undefined ? t('msg.burmaldaholic.blackjack.round_starts_in', duration(this.timeLeft())) : t('gui.burmaldaholic.common.waiting_players'));
    await this.showInfoForm(s, lines(...body));
  }

  // ---- the round ----------------------------------------------------------------------------

  private startRound(): void {
    this.clearBetTimer();
    if (this.phase !== 'betting') return;
    const seated = new Set(this.sessions().map((s) => s.playerId));
    const all = [...this.bets.values()];
    this.bets.clear();
    const bets: Bet[] = [];
    for (const b of all) {
      if (b.player.isValid && seated.has(b.player.id)) bets.push(b);
      else if (b.player.isValid) this.ctx.wagers.refund(b.ticket, b.player);
      else addPending(b.ticket.playerId, { kind: 'refund', ticket: b.ticket, ret: b.ticket.value, net: 0 });
    }
    if (!bets.length) return;
    const rules = readRules(this.ctx);
    if (!this.shoe || this.shoe.decks !== rules.decks) this.shoe = new Shoe(mathRng, rules.decks);
    if (this.shoe.needsShuffle(rules.penetration)) {
      this.shoe.shuffle();
      this.hudAll(t('gui.burmaldaholic.blackjack.shuffling'), 40);
    }
    this.parts.clear();
    for (const b of bets) {
      this.parts.set(b.seat, { player: b.player, playerId: b.player.id, name: b.player.name, seat: b.seat, ticket: b.ticket, paid: false, away: false });
    }
    this.round = new BlackjackRound(rules, this.shoe, bets.map((b) => ({ seat: b.seat, id: b.player.id, bet: b.amount })));
    this.phase = 'playing';
    for (const s of this.sessions()) this.closeForms(s.player);
    if (this.round.peeked && this.round.phase !== 'insurance') this.hudAll(t('gui.burmaldaholic.blackjack.dealer_peeks'), 30);
    this.step();
  }

  /** Drive the round: pay settled seats, ask the next decision or finish. */
  private step(): void {
    const r = this.round;
    if (!r || this.phase !== 'playing') return;
    this.clearTimer();
    this.payNewlySettled();

    if (r.phase === 'insurance') {
      for (const seat of r.pendingInsurance()) {
        const part = this.parts.get(seat.seat);
        if (!part || part.away || !part.player.isValid) r.decideInsurance(seat.seat, false);
      }
      if (r.phase !== 'insurance') return this.step();
      this.startTimer(this.ctx.config.int('blackjack.insuranceTimerTicks'), () => {
        for (const seat of r.pendingInsurance()) {
          const part = this.parts.get(seat.seat);
          r.decideInsurance(seat.seat, false);
          if (part) this.closeForms(part.player);
        }
        this.step();
      });
      for (const seat of r.pendingInsurance()) {
        const part = this.parts.get(seat.seat);
        if (part) this.safe(() => this.showInsurance(part));
      }
      return;
    }

    if (r.phase === 'turns') {
      const cur = r.current();
      const part = cur && this.parts.get(cur.seat.seat);
      if (!cur || !part || part.away || !part.player.isValid) {
        if (cur) r.standAll(cur.seat.seat);
        return this.step();
      }
      this.startTimer(this.ctx.config.int('blackjack.turnTimerTicks'), () => {
        r.standAll(part.seat);
        if (part.player.isValid) {
          part.player.sendMessage(t('msg.burmaldaholic.blackjack.auto_stand'));
          this.closeForms(part.player);
        }
        this.step();
      });
      this.hudAll(t('gui.burmaldaholic.blackjack.turn_of', lit(part.name)), 60, part.playerId);
      this.hud(part.player, t('gui.burmaldaholic.blackjack.your_turn'), 60);
      this.safe(() => this.showTurn(part));
      return;
    }

    this.finish();
  }

  private payNewlySettled(): void {
    const r = this.round;
    if (!r) return;
    for (const s of r.seats) {
      const part = this.parts.get(s.seat);
      if (!s.settled || !part || part.paid) continue;
      part.paid = true;
      const ret = r.returnOf(s.seat);
      const net = ret - r.stakedOf(s.seat);
      const summary = summaryRaw(r, s);
      if (part.player.isValid) {
        this.ctx.wagers.settle(part.ticket, part.player, ret);
        part.player.sendMessage(part.away ? t('msg.burmaldaholic.core.auto_completed', summary) : summary);
      } else {
        addPending(part.playerId, { kind: 'settle', ticket: part.ticket, ret, summary, net });
      }
    }
  }

  private finish(): void {
    this.clearTimer();
    this.payNewlySettled();
    this.phase = 'result';
    for (const part of this.parts.values()) {
      if (part.away || !part.player.isValid || !this.sessionOf(part.player)) continue;
      this.closeForms(part.player);
      this.safe(() => this.showResult(part));
    }
    this.startTimer(RESULT_TICKS, () => this.toBetting());
  }

  private toBetting(): void {
    this.phase = 'betting';
    this.round = undefined;
    this.parts.clear();
    const intents = new Map(this.intents);
    this.intents.clear();
    for (const s of this.sessions()) {
      const intent = intents.get(s.playerId);
      if (intent === 'again') this.quickBet(s, this.lastBet.get(s.playerId));
      else if (intent === 'change') this.safe(() => this.promptBet(s));
      else this.hud(s.player, t('gui.burmaldaholic.blackjack.betting_open'), 60);
    }
    this.maybeEmpty();
  }

  // ---- decision forms -----------------------------------------------------------------------

  private body(part: Participant | undefined, ...extra: (Raw | undefined)[]): Raw {
    const r = this.round;
    const table = r ? tableRaw(r, this.names(), part?.seat) : lit('');
    return lines(table, lit(''), ...extra);
  }

  private async showInsurance(part: Participant): Promise<void> {
    const r = this.round;
    const offer = r?.insuranceOffer(part.seat);
    if (!r || !offer) return;
    const seat = r.seat(part.seat)!;
    const even = offer === 'even_money';
    const form = new ActionFormData()
      .title(this.title())
      .body(
        this.body(
          part,
          t(even ? 'gui.burmaldaholic.blackjack.even_money_prompt' : 'gui.burmaldaholic.blackjack.insurance_prompt'),
          t('gui.burmaldaholic.common.auto_action', t(even ? 'gui.burmaldaholic.blackjack.decline_even_money' : 'gui.burmaldaholic.blackjack.no_insurance'), duration(this.timeLeft(), true)),
        ),
      )
      .button(even ? t('gui.burmaldaholic.blackjack.even_money') : t('gui.burmaldaholic.blackjack.insure', chips(maxInsurance(seat.bet))))
      .button(t(even ? 'gui.burmaldaholic.blackjack.decline_even_money' : 'gui.burmaldaholic.blackjack.no_insurance'));
    const res = await showForm(part.player, form);
    if (!res || res.canceled || res.selection === undefined) return;
    if (this.round !== r || r.insuranceOffer(part.seat) !== offer || part.away) return;
    let take = res.selection === 0;
    if (take && !even && !this.ctx.wagers.raise(part.ticket, part.player, maxInsurance(seat.bet))) take = false;
    r.decideInsurance(part.seat, take);
    if (r.phase !== 'insurance') this.step();
    else {
      this.payNewlySettled();
      this.hud(part.player, t('gui.burmaldaholic.common.waiting_players'), 100);
    }
  }

  private async showTurn(part: Participant): Promise<void> {
    const r = this.round;
    const cur = r?.current();
    if (!r || !cur || cur.seat.seat !== part.seat) return;
    const snapshot = `${cur.handIndex}:${cur.hand.cards.length}:${cur.seat.hands.length}`;
    const legal = r.legal(part.seat, this.ctx.economy.balance(part.player));
    const form = new ActionFormData()
      .title(this.title())
      .body(
        this.body(
          part,
          color('§e', t('gui.burmaldaholic.blackjack.your_turn')),
          t('gui.burmaldaholic.common.auto_action', t('gui.burmaldaholic.blackjack.stand'), duration(this.timeLeft(), true)),
        ),
      );
    for (const a of legal) form.button(t(`gui.burmaldaholic.blackjack.${a}`));
    const res = await showForm(part.player, form);
    if (!res || res.canceled || res.selection === undefined) return;
    const now = r.current();
    if (this.round !== r || !now || now.seat.seat !== part.seat || `${now.handIndex}:${now.hand.cards.length}:${now.seat.hands.length}` !== snapshot) return;
    const action = legal[res.selection];
    if (action) this.act(part, action);
  }

  private act(part: Participant, action: Action): void {
    const r = this.round!;
    if (!r.legal(part.seat, this.ctx.economy.balance(part.player)).includes(action)) return this.step();
    const extra = r.extraStake(part.seat, action);
    if (extra > 0 && !this.ctx.wagers.raise(part.ticket, part.player, extra)) return this.step();
    r.act(part.seat, action);
    this.step();
  }

  private async showResult(part: Participant): Promise<void> {
    const r = this.round;
    if (!r) return;
    const net = r.returnOf(part.seat) - r.stakedOf(part.seat);
    const form = new ActionFormData()
      .title(this.title())
      .body(this.body(part, netRaw(net)))
      .button(t('gui.burmaldaholic.common.play_again'))
      .button(t('gui.burmaldaholic.common.change_bet'))
      .button(t('gui.burmaldaholic.common.leave'));
    const res = await showForm(part.player, form);
    if (!res || res.canceled || res.selection === undefined) return;
    const s = this.sessionOf(part.player);
    if (!s) return;
    if (res.selection === 2) return s.leave();
    const intent = res.selection === 0 ? 'again' : 'change';
    if (this.phase !== 'betting') {
      this.intents.set(s.playerId, intent);
      this.hud(s.player, t('gui.burmaldaholic.common.waiting_players'), RESULT_TICKS);
      return;
    }
    if (this.bets.has(s.playerId)) return this.showLobby(s);
    if (intent === 'again') this.quickBet(s, this.lastBet.get(s.playerId));
    else await this.promptBet(s);
  }

  /** Spectator view: table state + Close / Leave. */
  private async showWatch(s: TableSession): Promise<void> {
    const r = this.round;
    const cur = r?.current();
    const status = cur ? t('gui.burmaldaholic.blackjack.turn_of', lit(this.parts.get(cur.seat.seat)?.name ?? '?')) : t('gui.burmaldaholic.common.waiting_players');
    await this.showInfoForm(s, this.body(this.partOf(s.playerId), status));
  }

  private async showInfoForm(s: TableSession, body: Raw): Promise<void> {
    const form = new ActionFormData()
      .title(this.title())
      .body(body)
      .button(t('gui.burmaldaholic.common.close'))
      .button(t('gui.burmaldaholic.common.leave'));
    const res = await showForm(s.player, form);
    if (res && !res.canceled && res.selection === 1 && s.isActive()) s.leave();
  }
}

function addPending(playerId: string, p: Pending): void {
  const list = pending.get(playerId) ?? [];
  list.push(p);
  pending.set(playerId, list);
}
