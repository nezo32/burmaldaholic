/**
 * Ultimate Texas Hold'em table runtime (GAME_DESIGN §21.4–§21.9, UI.md §15). One UthTable per
 * table key (block or Hold'em Dealer NPC). Shared board and dealer hand, per-seat decisions with
 * one shared timer per street, safe default actions, and the optional player bank (dealer seat).
 *
 *   BETTING  → seated players confirm Ante (+ Blind) and optional Trips ("Deal"); starts when all
 *              seated players have bet, or uth.betTimerTicks after the first confirmed bet
 *   PLAYING  → DEAL (deck drawn → wagers.draw), PREFLOP / FLOP / RIVER decisions, SHOWDOWN
 *   RESULT   → result forms, 80 ticks → BETTING
 *
 * Money only moves through core: seat bets via ctx.wagers (house = bank, an owner's bankroll
 * through core's resolver, or the banker's escrow bankroll), the escrow via economy.transact.
 *
 * Seats are abstract (`Occupant` + `StakeHandle`) and every decision goes through
 * `DecisionPolicy` or the human forms, so the future core bot framework (docs/design/BOTS.md)
 * can seat bots without touching the round flow.
 */
import { type Player, system, world } from '@minecraft/server';
import { ActionFormData, ModalFormData, uiManager } from '@minecraft/server-ui';
import {
  type GameId,
  type HouseRef,
  HudPriority,
  ModalLayout,
  type ModuleContext,
  type Raw,
  type SettledEvent,
  type TableLimits,
  type TableRef,
  type TableSession,
  type WagerTicket,
  chips,
  color,
  duration,
  isFormOpen,
  join,
  joinWith,
  lines,
  lit,
  livePlayer,
  mathRng,
  parseAmount,
  showForm,
  sliderStep,
  t,
} from '../../core';
import { CHAOS_SERVICE, type ChaosApi } from '../../chaos/api';
import { MULTIPLAYER_SERVICE, type MultiplayerApi } from '../../multiplayer/api';
import { WORLDGEN_SERVICE, type WorldgenApi } from '../../worldgen/api';
import { type BankEscrow, type Escrow } from './bank';
import { readRules } from './config';
import {
  type BaseTableLimits,
  type BetError,
  type BetLimits,
  type Decision,
  type DecisionPolicy,
  type Settlement,
  type UthRules,
  CAT,
  PLAY_MULTIPLE,
  UthRound,
  anteRange,
  bankRound,
  bankTooLow,
  categoryOf,
  checkBanker,
  confirmCost,
  houseSeatEarned,
  maxCoveredAnte,
  nextClockwise,
  returnForStaked,
  seatHooks,
  seatReservation,
  shuffledDeck,
  tableLimits,
  tripsEdge,
  validateBets,
  worstCaseTotal,
} from './logic';
import { betsRaw, cardsRaw, paytableRaw, rulesRaw, seatTagRaw, settlementLines, streetRaw, valueRaw } from './render';

/** Wager game id (core GAME_IDS). */
export const UTH_GAME: GameId = 'uth';
const HUD = 'uth.table';
const RESULT_TICKS = 80;
const REVEAL_TICKS = 20;
/** Element of risk of Ante + Blind + Play (§21.3, 0.53–0.55 %): cashback base per chip. */
const BASE_EDGE = 0.0055;

// ---- abstract seats (humans now, bots later) -------------------------------------------------

/** Chips a seat put at risk this round (a wager ticket for humans). */
export interface StakeHandle {
  value(): number;
  raise(amount: number): boolean;
  draw(totalReturn: number): void;
  settle(totalReturn: number): SettledEvent | undefined;
  refund(): void;
  /** casino mode off: settle at the draw, or refund if undrawn */
  closeOut(): void;
}

export interface Occupant {
  readonly kind: 'player' | 'bot';
  readonly id: string;
  readonly name: string;
  /** the online Player (humans), undefined when offline or a bot */
  player(): Player | undefined;
  /** bots decide through a policy; humans through forms */
  readonly policy?: DecisionPolicy;
  balance(): number;
}

class HumanOccupant implements Occupant {
  readonly kind = 'player' as const;
  readonly id: string;
  readonly name: string;
  constructor(
    private readonly ctx: ModuleContext,
    private readonly p: Player,
  ) {
    this.id = p.id;
    this.name = p.name;
  }
  player(): Player | undefined {
    return livePlayer(this.p, this.id);
  }
  balance(): number {
    const p = this.player();
    return p ? this.ctx.economy.balance(p) : 0;
  }
}

class TicketStake implements StakeHandle {
  constructor(
    private readonly ctx: ModuleContext,
    private readonly occ: Occupant,
    readonly ticket: WagerTicket,
    private readonly edge: number,
  ) {}
  value(): number {
    return this.ticket.value;
  }
  raise(amount: number): boolean {
    const p = this.occ.player();
    // The Play bet was reserved at confirmation (§21.6): no extra bankroll exposure.
    return !!p && this.ctx.wagers.raise(this.ticket, p, amount, 0, this.edge);
  }
  draw(ret: number): void {
    this.ctx.wagers.draw(this.ticket, ret);
  }
  settle(ret: number): SettledEvent | undefined {
    return this.ctx.wagers.settle(this.ticket, this.occ.player(), ret);
  }
  refund(): void {
    this.ctx.wagers.refund(this.ticket, this.occ.player());
  }
  closeOut(): void {
    this.ctx.wagers.closeOut(this.ticket, this.occ.player());
  }
}

interface BetEntry {
  occ: Occupant;
  seat: number;
  ante: number;
  trips: number;
  stake: StakeHandle;
  /** disconnected during BETTING: the bet stays in play (§21.5) */
  away: boolean;
}

interface Participant extends BetEntry {
  paid: boolean;
  /** automatic ×1 of an offline seat that could not be debited (net-equivalent settlement) */
  unpaidPlay: number;
  settlement?: Settlement;
}

interface Banker {
  escrow: Escrow;
  /** player seat number held while banking (core session seat) */
  leaving: boolean;
  away: boolean;
  rounds: number;
  tooLow: boolean;
}

export interface TableDeps {
  ctx: ModuleContext;
  bank: BankEscrow;
}

export class UthTable {
  phase: 'betting' | 'playing' | 'result' = 'betting';
  private readonly bets = new Map<string, BetEntry>();
  private round: UthRound | undefined;
  private rules: UthRules;
  private readonly parts = new Map<number, Participant>();
  private readonly lastBets = new Map<string, { ante: number; trips: number }>();
  private readonly intents = new Map<string, 'again' | 'change'>();
  private readonly names = new Map<string, string>();
  private banker: Banker | undefined;
  /** who banks the round being bet / played (fixed at the start of BETTING) */
  private roundBanker: Banker | undefined;
  private offer: string | undefined;
  private betTimer: number | undefined;
  private timer: number | undefined;
  private deadline = 0;
  private dealer?: { value: number };

  constructor(
    private readonly deps: TableDeps,
    readonly ref: TableRef,
    private readonly onEmpty: (t: UthTable) => void,
  ) {
    this.rules = readRules(deps.ctx);
  }

  private get ctx(): ModuleContext {
    return this.deps.ctx;
  }

  // ---- table configuration -------------------------------------------------------------------

  private preset() {
    try {
      return this.ctx.services.get<WorldgenApi>(WORLDGEN_SERVICE)?.tablePreset(this.ref.dimension.id, this.ref.location);
    } catch {
      return undefined;
    }
  }

  base(): BaseTableLimits {
    const c = this.ctx.config;
    return tableLimits(this.ref.variant, this.preset(), {
      highRollerMinAnte: c.int('uth.highRollerMinAnte'),
      highRollerMaxMultiplier: c.num('uth.highRollerMaxMultiplier'),
      highRollerMinVipTier: c.int('uth.highRollerMinVipTier'),
      minAnte: c.int('uth.minAnte'),
    });
  }

  isPlayerBanked(): boolean {
    return this.base().kind === 'player_banked';
  }

  private pvpOn(): boolean {
    return this.isPlayerBanked() && this.ctx.config.bool('uth.pvp.enabled');
  }

  private coreLimits(p?: Player): TableLimits {
    const b = this.base();
    const base: TableLimits = { min: b.min, tierMultiplier: b.tierMultiplier, minTier: b.minTier };
    return p ? this.ctx.wagers.limitsFor(p, UTH_GAME, base, this.ref.key) : base;
  }

  /** Bet limits for a player: min Ante, max W (§21.2, owner min/max apply to Ante / W). */
  betLimits(p: Player): BetLimits {
    const lim = this.coreLimits(p);
    const range = this.ctx.limits.range(p, lim);
    return { minAnte: range.min, maxTotal: range.max, tripsEnabled: this.ctx.config.bool('uth.tripsEnabled') };
  }

  private title(): Raw {
    const k = this.base().kind;
    return t(k === 'high_roller' ? 'gui.burmaldaholic.uth.title_high_roller' : k === 'player_banked' ? 'gui.burmaldaholic.uth.title_player_banked' : 'gui.burmaldaholic.uth.title');
  }

  // ---- helpers ------------------------------------------------------------------------------------

  private sessions(): TableSession[] {
    return this.ctx.tables.sessionsAt(this.ref.key);
  }

  private sessionOf(playerId: string): TableSession | undefined {
    return this.sessions().find((s) => s.playerId === playerId);
  }

  /** Seated players who are not the banker. */
  private playerSessions(): TableSession[] {
    return this.sessions().filter((s) => s.playerId !== this.banker?.escrow.bankerId);
  }

  private partOf(id: string): Participant | undefined {
    for (const p of this.parts.values()) if (p.occ.id === id) return p;
    return undefined;
  }

  private hud(p: Player | undefined, msg: Raw, ttl = 60): void {
    if (p?.isValid) this.ctx.hud.actionbar(p, HUD, msg, HudPriority.game, ttl);
  }

  private tell(id: string, msg: Raw): void {
    this.ctx.wagers.tell(id, msg);
  }

  private closeForms(p: Player | undefined): void {
    if (p?.isValid) uiManager.closeAllForms(p);
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
      if (r instanceof Promise) r.catch((e: unknown) => this.ctx.log.error('uth table', e));
    } catch (e) {
      this.ctx.log.error('uth table', e);
    }
  }

  private timeLeft(): number {
    return Math.max(0, this.deadline - system.currentTick);
  }

  private edgeFor(ante: number, trips: number, house: HouseRef | undefined): number {
    if (house?.kind === 'bankroll' && this.roundBanker && house.id === this.roundBanker.escrow.bankrollId) return 0; // player bank: no cashback
    const stake = confirmCost(ante, trips);
    return stake > 0 ? (2 * ante * BASE_EDGE + trips * tripsEdge(this.rules.tripsPays)) / stake : BASE_EDGE;
  }

  isActive(): boolean {
    return this.phase !== 'betting' || this.bets.size > 0;
  }

  hasPlayer(id: string): boolean {
    return this.bets.has(id) || !!this.partOf(id) || this.banker?.escrow.bankerId === id;
  }

  dispose(): void {
    this.clearTimer();
    this.clearBetTimer();
  }

  private maybeEmpty(): void {
    if (this.phase === 'betting' && !this.bets.size && !this.sessions().length && !this.banker) {
      this.dispose();
      this.onEmpty(this);
    }
  }

  // ---- entry points --------------------------------------------------------------------------

  /** Seat limit check (the dealer seat is extra on player-banked tables). */
  seatsFull(playerId: string): boolean {
    if (this.sessionOf(playerId)) return false;
    return this.playerSessions().length >= this.ctx.config.int('uth.seats');
  }

  open(s: TableSession, rejoined: boolean): void {
    this.names.set(s.playerId, s.player.name);
    if (!rejoined) {
      for (const o of this.sessions()) if (o.playerId !== s.playerId) o.player.sendMessage(t('msg.burmaldaholic.uth.player_joined', lit(s.player.name)));
      if (this.phase !== 'betting') s.player.sendMessage(t('gui.burmaldaholic.uth.waiting_next'));
    }
    this.safe(() => this.showState(s));
  }

  /** Session ended (leave, walk away, disconnect, table broken, casino off). */
  leave(s: TableSession, reason: string, policy: 'play_out' | 'refund'): void {
    const name = this.names.get(s.playerId) ?? '';
    this.names.delete(s.playerId);
    this.intents.delete(s.playerId);
    for (const o of this.sessions()) if (o.playerId !== s.playerId) o.player.sendMessage(t('msg.burmaldaholic.uth.player_left', lit(name)));

    if (policy === 'refund') {
      this.shutdown();
      return;
    }
    if (this.banker?.escrow.bankerId === s.playerId) {
      this.bankerLeft(reason !== 'leave');
    }
    const b = this.bets.get(s.playerId);
    if (b && this.phase === 'betting') {
      if (reason === 'disconnect') {
        // §21.5: the bets stay in play; the round is played with default actions.
        b.away = true;
        this.startIfAllReady();
      } else {
        this.bets.delete(s.playerId);
        b.stake.refund();
        this.tell(s.playerId, t('msg.burmaldaholic.uth.left_refunded'));
        if (!this.bets.size) this.clearBetTimer();
        else this.startIfAllReady();
      }
    }
    const part = this.partOf(s.playerId);
    if (part && this.phase === 'playing' && !part.paid) {
      part.away = true;
      // §21.5: pending decisions take their default action at once.
      if (this.round && this.round.needsDecision(this.round.seat(part.seat)!)) this.applyDefault(part, false);
    }
    this.maybeEmpty();
  }

  /** Casino mode off: drawn rounds settle at their draw, undrawn bets are refunded (§4.1). */
  private shutdown(): void {
    this.clearTimer();
    this.clearBetTimer();
    for (const b of this.bets.values()) {
      b.stake.refund();
      this.tell(b.occ.id, t('msg.burmaldaholic.uth.bets_refunded'));
    }
    this.bets.clear();
    for (const p of this.parts.values()) {
      if (!p.paid) p.stake.closeOut();
      p.paid = true;
    }
    this.parts.clear();
    this.round = undefined;
    this.phase = 'betting';
    if (this.banker) this.endBanking(false);
    this.roundBanker = undefined;
    this.maybeEmpty();
  }

  /** Show whatever this player should see now (also when they re-open the table). */
  private async showState(s: TableSession): Promise<void> {
    if (!s.isActive()) return;
    if (this.banker?.escrow.bankerId === s.playerId) return this.showBanker(s);
    if (this.phase === 'betting') {
      if (this.bets.has(s.playerId)) return this.showLobby(s);
      if (this.pvpOn()) return this.showHub(s);
      return this.promptBets(s);
    }
    const part = this.partOf(s.playerId);
    const r = this.round;
    if (this.phase === 'playing' && r && part && !part.away && r.needsDecision(r.seat(part.seat)!)) return this.showDecision(part);
    if (this.phase === 'result' && part && part.settlement) return this.showResult(s, part);
    return this.showWatch(s);
  }

  // ---- betting ----------------------------------------------------------------------------------

  /** Player-banked table hub: bets, dealer seat, paytable, leave. */
  private async showHub(s: TableSession): Promise<void> {
    const body: Raw[] = [this.dealerSeatRaw(), lit(''), color('§7', t('gui.burmaldaholic.uth.pvp.rules.1')), color('§7', this.pvpRules2())];
    const form = new ActionFormData().title(this.title()).body(lines(...body));
    const actions: (() => Promise<void> | void)[] = [];
    form.button(t('gui.burmaldaholic.uth.bet_title'));
    actions.push(() => this.promptBets(s));
    if (!this.banker) {
      form.button(t('gui.burmaldaholic.uth.pvp.take_seat'));
      actions.push(() => this.promptTakeSeat(s));
    }
    form.button(t('gui.burmaldaholic.common.paytable'));
    actions.push(() => this.showPaytable(s));
    form.button(t('gui.burmaldaholic.common.leave'));
    actions.push(() => s.leave());
    const res = await showForm(s.player, form);
    if (!res || res.canceled || res.selection === undefined || !s.isActive()) return;
    await actions[res.selection]?.();
  }

  private pvpRules2(): Raw {
    const per = 4 + 1 + Math.max(0, ...Object.values(this.rules.blindPays).map((x) => x ?? 0));
    const tripsMax = Math.max(0, ...Object.values(this.rules.tripsPays).map((x) => x ?? 0));
    return t('gui.burmaldaholic.uth.pvp.rules.2', per, tripsMax, Math.round(this.ctx.config.num('uth.pvp.rakePercent') * 1000) / 10);
  }

  private dealerSeatRaw(): Raw {
    const b = this.banker;
    if (!b) return t('gui.burmaldaholic.uth.pvp.dealer_seat', t('gui.burmaldaholic.uth.pvp.the_house'));
    const st = this.deps.bank.state(b.escrow);
    return lines(
      t('gui.burmaldaholic.uth.pvp.dealer_seat', lit(b.escrow.bankerName)),
      t('gui.burmaldaholic.uth.pvp.bank', chips(st.balance), chips(st.reserved)),
      b.leaving ? color('§7', t('gui.burmaldaholic.uth.pvp.leaving_after_round')) : undefined,
    );
  }

  private betError(p: Player, e: BetError): Raw {
    switch (e.key) {
      case 'ante_min':
        return t('gui.burmaldaholic.uth.error.ante_min', chips(e.min));
      case 'trips_min':
        return t('gui.burmaldaholic.uth.error.ante_min', chips(e.min));
      case 'worst_case_max':
        return t('gui.burmaldaholic.uth.error.worst_case_max', chips(e.max));
      case 'trips_off':
        return t('gui.burmaldaholic.uth.error.trips_off');
      case 'trips_needs_ante':
        return t('gui.burmaldaholic.uth.error.trips_needs_ante');
      case 'insufficient':
        return t('gui.burmaldaholic.error.insufficient_funds', chips(this.ctx.economy.balance(p)));
      case 'keep_for_river':
        return t('gui.burmaldaholic.uth.error.keep_for_river', chips(e.keep));
    }
  }

  /** Every check before a bet is taken; undefined = OK. */
  private checkBets(p: Player, ante: number, trips: number): Raw | undefined {
    const lim = this.coreLimits(p);
    if (lim.minTier !== undefined && this.ctx.limits.tier(p) < lim.minTier) return t('gui.burmaldaholic.error.vip_required', this.ctx.limits.tierName(lim.minTier));
    const e = validateBets(ante, trips, this.betLimits(p), this.ctx.economy.balance(p));
    if (e) return this.betError(p, e);
    if (this.roundBanker) {
      const covered = maxCoveredAnte(this.deps.bank.state(this.roundBanker.escrow).available, trips, this.rules.blindPays, this.rules.tripsPays);
      if (ante > covered) return t('gui.burmaldaholic.uth.error.bank_cover', chips(covered));
    }
    return undefined;
  }

  /** Bets form (UI.md §15 Bedrock flow 1). */
  private async promptBets(s: TableSession, firstError?: Raw): Promise<void> {
    const p = s.player;
    let error = firstError;
    const last = this.lastBets.get(s.playerId);
    let ante = last?.ante ?? 0;
    let trips = last?.trips ?? 0;
    for (;;) {
      if (!s.isActive() || this.phase !== 'betting' || this.bets.has(s.playerId)) return;
      const lim = this.betLimits(p);
      const range = anteRange(lim);
      if (range.max < range.min) {
        p.sendMessage(t('gui.burmaldaholic.uth.error.worst_case_max', chips(lim.maxTotal)));
        return;
      }
      ante = Math.min(range.max, Math.max(range.min, ante || range.min));
      const layout = new ModalLayout();
      const form = new ModalFormData().title(t('gui.burmaldaholic.uth.bet_title'));
      if (error) {
        form.label(color('§c', error));
        layout.passive();
      }
      form.label(
        joinWith(lit(' · '), [
          t('gui.burmaldaholic.common.balance', chips(this.ctx.economy.balance(p))),
          t('gui.burmaldaholic.uth.ante_limits', range.min, range.max, lim.maxTotal),
        ]),
      );
      layout.passive();
      if (this.roundBanker) {
        form.label(t('gui.burmaldaholic.uth.pvp.covers_up_to', chips(maxCoveredAnte(this.deps.bank.state(this.roundBanker.escrow).available, 0, this.rules.blindPays, this.rules.tripsPays))));
        layout.passive();
      }
      form.label(color('§7', rulesRaw(this.rules.allow3x)));
      layout.passive();
      form.slider(t('gui.burmaldaholic.uth.ante'), range.min, range.max, { valueStep: sliderStep(range.min, range.max), defaultValue: ante });
      const iAnte = layout.control();
      form.textField(t('gui.burmaldaholic.common.exact_amount'), t('gui.burmaldaholic.common.amount'));
      const iExact = layout.control();
      form.label(color('§7', t('gui.burmaldaholic.uth.blind_equals_ante')));
      layout.passive();
      let iTrips: number | undefined;
      if (lim.tripsEnabled) {
        const tripsMax = Math.max(lim.minAnte, lim.maxTotal - 6 * range.min);
        form.slider(t('gui.burmaldaholic.uth.trips_optional'), 0, tripsMax, { valueStep: sliderStep(0, tripsMax), defaultValue: Math.min(tripsMax, trips) });
        iTrips = layout.control();
      }
      form.submitButton(t('gui.burmaldaholic.common.deal'));
      const res = await showForm(p, form);
      if (!res || res.canceled || !s.isActive()) return;
      const typed = String(layout.value(res, iExact) ?? '').trim();
      ante = Number(layout.value(res, iAnte));
      if (typed) {
        const a = parseAmount(typed);
        if (a === undefined) {
          error = t('gui.burmaldaholic.error.invalid_amount');
          continue;
        }
        ante = a;
      }
      trips = iTrips === undefined ? 0 : Number(layout.value(res, iTrips));
      if (this.phase !== 'betting' || this.bets.has(s.playerId)) return this.showState(s);
      error = this.checkBets(p, ante, trips);
      if (error) continue;
      error = this.placeBets(s, ante, trips);
      if (!error) return;
    }
  }

  /** Take the confirmed bets (2 × Ante + Trips). Returns an error text on failure. */
  private placeBets(s: TableSession, ante: number, trips: number): Raw | undefined {
    const p = s.player;
    const err = this.checkBets(p, ante, trips);
    if (err) return err;
    const house: HouseRef | undefined = this.roundBanker ? { kind: 'bankroll', id: this.roundBanker.escrow.bankrollId, playerBanked: true } : undefined;
    const edge = this.edgeFor(ante, trips, house);
    const r = this.ctx.wagers.place(p, {
      game: UTH_GAME,
      stake: { kind: 'chips', amount: confirmCost(ante, trips) },
      limits: this.coreLimits(),
      tableKey: this.ref.key,
      house,
      // §21.6: the house's largest net loss assuming the ×4 Play bet
      worstCase: seatReservation(ante, trips, this.rules.blindPays, this.rules.tripsPays),
      houseEdge: edge,
      notify: false,
    });
    if (!r.ok) return r.error;
    const occ = new HumanOccupant(this.ctx, p);
    this.bets.set(s.playerId, { occ, seat: s.seat, ante, trips, stake: new TicketStake(this.ctx, occ, r.ticket, edge), away: false });
    this.lastBets.set(s.playerId, { ante, trips });
    const others = this.playerSessions().filter((o) => !this.bets.has(o.playerId));
    if (this.bets.size === 1 && others.length) {
      const ticks = this.ctx.config.int('uth.betTimerTicks');
      this.clearBetTimer();
      this.betTimer = system.runTimeout(() => {
        this.betTimer = undefined;
        this.safe(() => this.startRound());
      }, ticks);
      this.deadline = system.currentTick + ticks;
      for (const o of others) {
        o.player.sendMessage(t('msg.burmaldaholic.uth.round_starts_in', duration(ticks)));
        if (!isFormOpen(o.player)) this.safe(() => this.showState(o));
      }
    }
    if (!this.startIfAllReady()) this.hud(p, t('gui.burmaldaholic.common.waiting_players'), 100);
    return undefined;
  }

  private startIfAllReady(): boolean {
    if (this.phase !== 'betting' || !this.bets.size) return false;
    if (this.playerSessions().some((s) => !this.bets.has(s.playerId))) return false;
    this.startRound();
    return true;
  }

  private async showLobby(s: TableSession): Promise<void> {
    const body: Raw[] = [];
    if (this.isPlayerBanked()) body.push(this.dealerSeatRaw(), lit(''));
    for (const o of this.playerSessions()) {
      const b = this.bets.get(o.playerId);
      body.push(join(t('gui.burmaldaholic.common.seat', o.seat), lit(' '), lit(o.player.name)));
      body.push(join(lit('   '), b ? betsRaw(b.ante, b.trips, 0) : color('§7', t('gui.burmaldaholic.common.waiting_players'))));
    }
    body.push(lit(''));
    body.push(this.betTimer !== undefined ? t('msg.burmaldaholic.uth.round_starts_in', duration(this.timeLeft())) : t('gui.burmaldaholic.common.waiting_players'));
    await this.showInfoForm(s, lines(...body));
  }

  // ---- the round ----------------------------------------------------------------------------------

  private startRound(): void {
    this.clearBetTimer();
    if (this.phase !== 'betting' || !this.bets.size) return;
    this.rules = readRules(this.ctx);
    const entries = [...this.bets.values()].sort((a, b) => a.seat - b.seat);
    this.bets.clear();
    const round = new UthRound(
      shuffledDeck(mathRng),
      entries.map((b) => ({ seat: b.seat, id: b.occ.id, ante: b.ante, trips: b.trips })),
      this.rules,
    );
    this.round = round;
    this.parts.clear();
    for (const b of entries) this.parts.set(b.seat, { ...b, paid: false, unpaidPlay: 0 });
    this.phase = 'playing';
    this.dealer = undefined;
    for (const s of this.sessions()) this.closeForms(s.player);
    // DEAL: the deck is drawn → persist every seat's default-play result now (§4.1, §21.5).
    this.drawAll();
    this.beginStreet();
  }

  /** Can an automatic ×1 river bet be made for this seat (online: balance; offline: virtual)? */
  private canAuto(part: Participant): boolean {
    const p = part.occ.player();
    return !p || part.occ.balance() >= part.ante;
  }

  private drawSeat(part: Participant): void {
    const r = this.round;
    if (!r || part.paid) return;
    const pr = r.projected(part.seat, this.canAuto(part));
    // The draw is only used if the server stops: the automatic ×1 is then never debited, so it
    // is settled net-equivalent (returnForStaked), like an offline seat's.
    part.stake.draw(returnForStaked(pr.settlement, part.unpaidPlay + pr.extraPlay));
  }

  private drawAll(): void {
    for (const part of this.parts.values()) this.drawSeat(part);
  }

  /** Start the current street: defaults for away seats, bots, then human forms + timer. */
  private beginStreet(): void {
    const r = this.round;
    if (!r || this.phase !== 'playing') return;
    this.clearTimer();
    if (r.street === 'showdown') return this.showdown();
    for (const seat of r.pending()) {
      const part = this.parts.get(seat.seat)!;
      if (part.away || (part.occ.kind === 'player' && !part.occ.player())) this.applyDefault(part, false);
      else if (part.occ.policy) this.decide(part, this.botDecision(part, part.occ.policy), false);
    }
    if (this.round !== r || this.phase !== 'playing') return;
    if (!r.pending().length) return this.finishStreet();
    const ticks = this.ctx.config.int('uth.decisionTimerTicks');
    this.startTimer(ticks, () => {
      if (this.round !== r) return;
      for (const seat of r.pending()) {
        const part = this.parts.get(seat.seat)!;
        this.applyDefault(part, true);
        this.closeForms(part.occ.player());
      }
      this.finishStreet();
    });
    this.hudAll();
    for (const seat of r.pending()) {
      const part = this.parts.get(seat.seat)!;
      this.safe(() => this.showDecision(part));
    }
  }

  private botDecision(part: Participant, policy: DecisionPolicy): Decision {
    const r = this.round!;
    const s = r.seat(part.seat)!;
    const d = policy.decide({ street: r.street, hole: s.hole, board: r.visibleBoard(), ante: s.ante, trips: s.trips, options: r.legal(part.seat, part.occ.balance()), blindPays: this.rules.blindPays });
    return r.isLegal(part.seat, d) ? d : r.defaultDecision(part.seat, this.canAuto(part));
  }

  /** Apply the §21.4 default action (timeout, leave, disconnect, table break). */
  private applyDefault(part: Participant, notify: boolean): void {
    const r = this.round;
    if (!r) return;
    const seat = r.seat(part.seat)!;
    this.decide(part, r.defaultDecision(part.seat, this.canAuto(part)), true);
    const d = seat.last;
    if (!notify) return;
    const p = part.occ.player();
    if (!p) return;
    if (d === 'check') p.sendMessage(t('msg.burmaldaholic.uth.auto_check'));
    else if (d === 'fold') p.sendMessage(t('msg.burmaldaholic.uth.auto_fold'));
    else if (d === 'bet1') p.sendMessage(t('msg.burmaldaholic.uth.auto_play', valueRaw(r.playerValue(seat))));
  }

  /**
   * Record a decision (taking the Play bet). Returns false when it could not be applied (e.g.
   * the raise failed). Ends the street when nobody is pending.
   */
  private decide(part: Participant, d: Decision, auto: boolean): boolean {
    const r = this.round;
    if (!r || !r.isLegal(part.seat, d)) return false;
    const amount = PLAY_MULTIPLE[d] * part.ante;
    if (amount > 0) {
      if (part.occ.player()) {
        if (!part.stake.raise(amount)) {
          if (!auto) return false;
          // an automatic bet that cannot be paid falls back to the no-risk option
          d = r.street === 'river' ? 'fold' : 'check';
        }
      } else part.unpaidPlay += amount; // offline automatic ×1 (net-equivalent)
    }
    r.decide(part.seat, d, auto);
    this.drawSeat(part);
    if (this.timer !== undefined && !r.pending().length) {
      this.clearTimer();
      system.run(() => this.safe(() => this.finishStreet()));
    } else if (r.pending().length) this.hudAll();
    return true;
  }

  /** All decided: reveal the next street after a short pause. */
  private finishStreet(): void {
    const r = this.round;
    if (!r || this.phase !== 'playing' || r.pending().length || r.street === 'showdown') return;
    r.advance();
    this.hudAll();
    for (const part of this.parts.values()) {
      const p = part.occ.player();
      if (p && !part.away && !r.needsDecision(r.seat(part.seat)!)) this.closeForms(p);
    }
    this.startTimer(REVEAL_TICKS, () => this.beginStreet());
  }

  private hudAll(): void {
    const r = this.round;
    if (!r) return;
    const pending = r.pending().length;
    const board = r.visibleBoard();
    const boardRaw = board.length ? cardsRaw(board, 5 - board.length) : cardsRaw([], 5);
    const status = pending ? t('gui.burmaldaholic.uth.still_deciding', pending) : streetRaw(r.street);
    const msg = t('gui.burmaldaholic.uth.actionbar', streetRaw(r.street), boardRaw, status);
    for (const s of this.sessions()) this.hud(s.player, msg, 80);
    this.hudSpectators(msg);
  }

  /** Spectators within multiplayer.spectatorRadius see the public state (§18.1: action bar). */
  private hudSpectators(msg: Raw): void {
    const radius = this.ctx.config.int('multiplayer.spectatorRadius');
    if (radius <= 0) return;
    try {
      for (const p of this.ref.dimension.getPlayers({ location: this.ref.location, maxDistance: radius })) {
        if (!this.sessionOf(p.id)) this.hud(p, msg, 60);
      }
    } catch {
      /* unloaded */
    }
  }

  private showdown(): void {
    const r = this.round;
    if (!r) return;
    this.clearTimer();
    this.phase = 'result';
    const dealerValue = r.dealerValue();
    this.dealer = { value: dealerValue };
    const reveal = t('msg.burmaldaholic.uth.dealer_reveals', join(cardsRaw(r.dealer), lit(' '), valueRaw(dealerValue)));
    const seatNets: number[] = [];
    let staked = 0;
    for (const part of this.parts.values()) {
      if (part.paid) continue;
      const s = r.seat(part.seat)!;
      const st = r.settle(part.seat);
      part.settlement = st;
      part.paid = true;
      const ret = returnForStaked(st, part.unpaidPlay);
      staked += part.stake.value();
      seatNets.push(ret - part.stake.value());
      part.stake.settle(ret);
      const pv = r.playerValue(s);
      const summary = lines(reveal, settlementLines(st, s, pv, dealerValue, { blind: this.rules.blindPays, trips: this.rules.tripsPays }));
      if (part.away || !part.occ.player()) this.tell(part.occ.id, t('msg.burmaldaholic.core.auto_completed', summary));
      this.hooks(part, st, pv);
    }
    const banker = this.roundBanker;
    if (banker) this.settleBank(banker, seatNets, staked);
    for (const part of this.parts.values()) {
      const p = part.occ.player();
      const sess = p && this.sessionOf(p.id);
      if (!sess || part.away) continue;
      this.closeForms(p);
      this.safe(() => this.showResult(sess, part));
    }
    if (banker) {
      const bs = this.sessionOf(banker.escrow.bankerId);
      if (bs) this.safe(() => this.showBanker(bs));
    }
    this.startTimer(RESULT_TICKS, () => this.toBetting());
  }

  /** Advancements, chaos, royal broadcast (§21.7). */
  private hooks(part: Participant, st: Settlement, playerValue: number): void {
    const who = part.occ.player() ?? part.occ.id;
    if (part.occ.kind !== 'player') return;
    const h = seatHooks(this.round?.seat(part.seat)?.multiple ?? 0, st, playerValue);
    for (const id of h.achievements) this.ctx.achievements.unlock(who, id);
    if (h.royalBlind) {
      world.sendMessage(t('msg.burmaldaholic.uth.royal_broadcast', lit(part.occ.name), chipsOf(st.net)));
      const p = part.occ.player();
      if (p) {
        try {
          this.ctx.services.get<ChaosApi>(CHAOS_SERVICE)?.trigger(p, 'diamond_rain', { source: 'jackpot' });
        } catch (e) {
          this.ctx.log.warn(`uth: diamond rain failed: ${String(e)}`);
        }
      }
    }
  }

  // ---- player bank (§21.9) -------------------------------------------------------------------------

  private settleBank(b: Banker, seatNets: number[], staked: number): void {
    const res = bankRound(seatNets, this.ctx.config.num('uth.pvp.rakePercent'));
    if (res.rake > 0) {
      const mp = this.ctx.services.get<MultiplayerApi>(MULTIPLAYER_SERVICE);
      const info = mp?.tableInfo(this.ref.key);
      const ok = this.ctx.economy.transact(
        [
          { account: { bankroll: b.escrow.bankrollId }, delta: -res.rake },
          info ? { account: { bankroll: info.casinoId }, delta: res.rake } : { account: 'bank', delta: res.rake },
        ],
        'uth.pvp.rake',
      );
      if (ok && info) mp?.recordRake(this.ref.key, res.rake);
    }
    b.rounds++;
    const bp = livePlayer(undefined, b.escrow.bankerId);
    const msg = t('gui.burmaldaholic.uth.pvp.round_result', joinWith(lit(''), [lit(res.bankDelta >= 0 ? '+' : '−'), chips(Math.abs(res.bankDelta))]), chips(res.rake));
    if (bp) {
      bp.sendMessage(msg);
      this.ctx.wagers.recordPvp(bp, UTH_GAME, staked, res.bankDelta);
    } else this.tell(b.escrow.bankerId, msg);
    if (houseSeatEarned(res.bankDelta, seatNets.length)) this.ctx.achievements.unlock(bp ?? b.escrow.bankerId, 'uth_house_seat');
    const st = this.deps.bank.state(b.escrow);
    if (bankTooLow(st.balance, this.ctx.config.int('uth.pvp.minBank'), this.base().min, this.rules.blindPays)) {
      b.tooLow = true;
      this.tell(b.escrow.bankerId, t('msg.burmaldaholic.uth.pvp.bank_too_low'));
    }
  }

  /** Banker left: before DEAL the round becomes a house round; afterwards the bank plays out. */
  private bankerLeft(away: boolean): void {
    const b = this.banker;
    if (!b) return;
    b.away = away || b.away;
    b.leaving = true;
    if (this.phase === 'betting') this.endBanking(true);
  }

  /** Release the bank to the banker and (during BETTING) move confirmed seat bets to the house. */
  private endBanking(announce: boolean): void {
    const b = this.banker;
    if (!b) return;
    const wasRound = this.roundBanker === b;
    this.banker = undefined;
    if (this.roundBanker === b) this.roundBanker = undefined;
    if (wasRound && this.phase === 'betting' && this.bets.size) {
      // §21.9: confirmed bets stay; the bank's reservations are released and the house re-checks.
      for (const [id, e] of [...this.bets]) {
        e.stake.refund();
        this.bets.delete(id);
        const sess = this.sessionOf(id);
        if (!sess || e.away) continue;
        const err = this.placeBets(sess, e.ante, e.trips);
        if (err) sess.player.sendMessage(err);
      }
    }
    this.deps.bank.release(b.escrow);
    if (announce) for (const s of this.sessions()) s.player.sendMessage(t('msg.burmaldaholic.uth.pvp.left_seat', lit(b.escrow.bankerName)));
  }

  private async promptTakeSeat(s: TableSession, firstError?: Raw): Promise<void> {
    const p = s.player;
    const minBank = this.ctx.config.int('uth.pvp.minBank');
    let error = firstError;
    for (;;) {
      const pre = this.bankerError(p, minBank);
      if (pre) return p.sendMessage(pre);
      const balance = this.ctx.economy.balance(p);
      const layout = new ModalLayout();
      const form = new ModalFormData().title(t('gui.burmaldaholic.uth.pvp.take_seat'));
      if (error) {
        form.label(color('§c', error));
        layout.passive();
      }
      form.label(lines(t('gui.burmaldaholic.common.balance', chips(balance)), color('§7', t('gui.burmaldaholic.uth.pvp.rules.1')), color('§7', this.pvpRules2())));
      layout.passive();
      const max = Math.max(minBank, balance);
      form.slider(t('gui.burmaldaholic.uth.pvp.bank_amount', chips(minBank)), minBank, max, { valueStep: sliderStep(minBank, max), defaultValue: minBank });
      const iSlider = layout.control();
      form.textField(t('gui.burmaldaholic.common.exact_amount'), t('gui.burmaldaholic.common.amount'));
      const iText = layout.control();
      form.submitButton(t('gui.burmaldaholic.uth.pvp.take_seat_submit'));
      const res = await showForm(p, form);
      if (!res || res.canceled || !s.isActive()) return;
      const typed = String(layout.value(res, iText) ?? '').trim();
      const bank = typed ? parseAmount(typed) : Number(layout.value(res, iSlider));
      if (bank === undefined) {
        error = t('gui.burmaldaholic.error.invalid_amount');
        continue;
      }
      const err = this.bankerError(p, minBank, bank);
      if (err) {
        error = err;
        continue;
      }
      this.takeSeat(s, bank);
      return;
    }
  }

  private bankerError(p: Player, minBank: number, bank = minBank): Raw | undefined {
    if (!this.pvpOn()) return t('gui.burmaldaholic.error.disabled');
    const mp = this.ctx.services.get<MultiplayerApi>(MULTIPLAYER_SERVICE);
    const minVip = this.ctx.config.int('uth.pvp.minBankerVip');
    const e = checkBanker({
      vipTier: this.ctx.limits.tier(p),
      minVip,
      owes: this.ctx.economy.owed(p) > 0,
      isOwner: mp?.tableInfo(this.ref.key)?.ownerId === p.id,
      seatTaken: !!this.banker,
      betsConfirmed: this.phase !== 'betting' || this.bets.size > 0,
      bank,
      minBank,
      balance: this.ctx.economy.balance(p),
    });
    switch (e) {
      case undefined:
        return undefined;
      case 'vip':
        return t('gui.burmaldaholic.error.vip_required', this.ctx.limits.tierName(minVip));
      case 'pvp_owing':
        return t('gui.burmaldaholic.uth.error.pvp_owing');
      case 'owner':
        return t('gui.burmaldaholic.error.owner_cannot_play');
      case 'seat_taken':
        return t('gui.burmaldaholic.uth.error.seat_taken');
      case 'seat_next_round':
        return t('gui.burmaldaholic.uth.error.seat_next_round');
      case 'min_bank':
        return t('gui.burmaldaholic.uth.error.min_bank', chips(minBank));
      case 'insufficient':
        return t('gui.burmaldaholic.error.insufficient_funds', chips(this.ctx.economy.balance(p)));
    }
  }

  private takeSeat(s: TableSession, bank: number): void {
    const escrow = this.deps.bank.open(s.player, this.ref.key, bank);
    if (!escrow) return s.player.sendMessage(t('gui.burmaldaholic.error.insufficient_funds', chips(this.ctx.economy.balance(s.player))));
    this.banker = { escrow, leaving: false, away: false, rounds: 0, tooLow: false };
    this.roundBanker = this.banker;
    this.lastBets.delete(s.playerId);
    for (const o of this.sessions()) o.player.sendMessage(t('msg.burmaldaholic.uth.pvp.took_seat', lit(s.player.name), chips(bank)));
    this.safe(() => this.showBanker(s));
    this.startIfAllReady();
  }

  private async showBanker(s: TableSession): Promise<void> {
    const b = this.banker;
    if (!b || b.escrow.bankerId !== s.playerId) return this.showState(s);
    const body: Raw[] = [color('§e', t('gui.burmaldaholic.uth.pvp.you_bank')), this.dealerSeatRaw(), lit('')];
    const r = this.round;
    if (r && this.phase !== 'betting') {
      body.push(join(t('gui.burmaldaholic.uth.board'), lit(': '), cardsRaw(r.visibleBoard(), 5 - r.visibleBoard().length)));
      for (const part of this.parts.values()) {
        const seat = r.seat(part.seat)!;
        body.push(join(t('gui.burmaldaholic.common.seat', part.seat), lit(' '), lit(part.occ.name), lit('  '), seatTagRaw(seat, r.needsDecision(seat)), lit('  '), betsRaw(part.ante, part.trips, seat.play)));
      }
    } else {
      for (const [, e] of this.bets) body.push(join(t('gui.burmaldaholic.common.seat', e.seat), lit(' '), lit(e.occ.name), lit('  '), betsRaw(e.ante, e.trips, 0)));
      if (!this.bets.size) body.push(t('gui.burmaldaholic.common.waiting_players'));
    }
    const form = new ActionFormData()
      .title(this.title())
      .body(lines(...body))
      .button(b.leaving ? t('gui.burmaldaholic.uth.pvp.leaving_after_round') : t('gui.burmaldaholic.uth.pvp.leave_seat'))
      .button(t('gui.burmaldaholic.common.close'));
    const res = await showForm(s.player, form);
    if (!res || res.canceled || res.selection !== 0 || this.banker !== b) return;
    b.leaving = true;
    if (this.phase === 'betting') this.endBanking(true);
    else s.player.sendMessage(t('gui.burmaldaholic.uth.pvp.leaving_after_round'));
  }

  /** After a round: banker leaving / too low / rotation (§21.9). */
  private bankerBetweenRounds(): void {
    const b = this.banker;
    if (!b) return;
    if (b.leaving || b.away || b.tooLow || !this.sessionOf(b.escrow.bankerId) || !this.pvpOn()) {
      this.endBanking(true);
      return;
    }
    const every = this.ctx.config.int('uth.pvp.bankerRounds');
    if (every > 0 && b.rounds >= every) {
      b.rounds = 0;
      const bankerSeat = this.sessionOf(b.escrow.bankerId)?.seat ?? 0;
      const minBank = this.ctx.config.int('uth.pvp.minBank');
      const candidates = this.playerSessions().filter((c) => this.eligibleBanker(c.player, minBank));
      const next = nextClockwise(
        candidates.map((s) => s.seat),
        bankerSeat,
      );
      const s = candidates.find((c) => c.seat === next);
      if (s) this.safe(() => this.offerSeat(s, b));
    }
  }

  /** Could this player bank the table with the minimum bank (ignoring the seat being taken)? */
  private eligibleBanker(p: Player, minBank: number): boolean {
    const mp = this.ctx.services.get<MultiplayerApi>(MULTIPLAYER_SERVICE);
    return !checkBanker({
      vipTier: this.ctx.limits.tier(p),
      minVip: this.ctx.config.int('uth.pvp.minBankerVip'),
      owes: this.ctx.economy.owed(p) > 0,
      isOwner: mp?.tableInfo(this.ref.key)?.ownerId === p.id,
      seatTaken: false,
      betsConfirmed: false,
      bank: minBank,
      minBank,
      balance: this.ctx.economy.balance(p),
    });
  }

  /** Offer the dealer seat to the next seat clockwise; the banker keeps it if declined. */
  private async offerSeat(s: TableSession, current: Banker): Promise<void> {
    this.offer = s.playerId;
    s.player.sendMessage(t('msg.burmaldaholic.uth.pvp.seat_offered'));
    const form = new ActionFormData()
      .title(this.title())
      .body(lines(t('msg.burmaldaholic.uth.pvp.seat_offered'), this.dealerSeatRaw()))
      .button(t('gui.burmaldaholic.uth.pvp.take_seat'))
      .button(t('gui.burmaldaholic.common.no'));
    const res = await showForm(s.player, form);
    const accepted = !!res && !res.canceled && res.selection === 0;
    if (this.offer !== s.playerId) return;
    this.offer = undefined;
    if (!accepted || this.banker !== current || this.phase !== 'betting' || this.bets.size || !s.isActive()) return;
    this.endBanking(true);
    await this.promptTakeSeat(s);
  }

  private toBetting(): void {
    this.phase = 'betting';
    this.round = undefined;
    this.parts.clear();
    this.dealer = undefined;
    this.bankerBetweenRounds();
    if (this.banker && this.pvpOn()) this.roundBanker = this.banker;
    else this.roundBanker = undefined;
    const intents = new Map(this.intents);
    this.intents.clear();
    const waitForBanker = this.isPlayerBanked() && !this.banker && !this.ctx.config.bool('uth.pvp.houseRoundsWhenNoBanker');
    for (const s of this.playerSessions()) {
      const intent = intents.get(s.playerId);
      const last = this.lastBets.get(s.playerId);
      if (!waitForBanker && intent === 'again' && last) {
        const err = this.placeBets(s, last.ante, last.trips);
        if (err) this.safe(() => this.promptBets(s, err));
      } else if (!waitForBanker && intent === 'change') this.safe(() => this.promptBets(s));
      else this.hud(s.player, t('gui.burmaldaholic.common.waiting_players'), 60);
    }
    this.maybeEmpty();
  }

  // ---- decision / result / watch forms -------------------------------------------------------------

  private boardBody(viewer: Participant | undefined, extra: (Raw | undefined)[]): Raw {
    const r = this.round;
    if (!r) return lines(...extra);
    const b = r.visibleBoard();
    const showdown = this.phase === 'result';
    const out: (Raw | undefined)[] = [
      join(t('gui.burmaldaholic.uth.dealer'), lit(': '), showdown ? cardsRaw(r.dealer) : cardsRaw([], 2), showdown && this.dealer ? join(lit('  '), valueRaw(this.dealer.value)) : lit('')),
      join(t('gui.burmaldaholic.uth.board'), lit(': '), cardsRaw(b, 5 - b.length), lit('   '), color('§7', streetRaw(r.street))),
    ];
    if (this.isPlayerBanked()) out.push(this.dealerSeatRaw());
    out.push(lit(''));
    for (const part of this.parts.values()) {
      const seat = r.seat(part.seat)!;
      const mine = viewer?.seat === part.seat;
      const head = join(t('gui.burmaldaholic.common.seat', part.seat), lit(' '), mine ? color('§e', t('gui.burmaldaholic.common.you')) : lit(part.occ.name));
      const cards = mine || showdown ? cardsRaw(seat.hole) : cardsRaw([], 2);
      out.push(join(head, lit('  '), cards, lit('  '), seatTagRaw(seat, r.needsDecision(seat))));
    }
    if (viewer) {
      const seat = r.seat(viewer.seat)!;
      const v = r.visibleValue(seat);
      out.push(lit(''));
      out.push(join(t('gui.burmaldaholic.uth.your_cards'), lit(': '), cardsRaw(seat.hole)));
      if (v !== undefined) out.push(t('gui.burmaldaholic.uth.your_hand', valueRaw(v)));
      out.push(betsRaw(viewer.ante, viewer.trips, seat.play));
      const risk = worstCaseTotal(viewer.ante, viewer.trips) - (seat.play ? 4 * viewer.ante - seat.play : 0);
      if (!seat.folded && !seat.play) out.push(color('§7', t('gui.burmaldaholic.uth.at_risk', chips(risk))));
    }
    return lines(...out, ...extra);
  }

  private async showDecision(part: Participant): Promise<void> {
    const r = this.round;
    const p = part.occ.player();
    if (!r || !p || part.away) return;
    const seat = r.seat(part.seat)!;
    if (!r.needsDecision(seat)) return;
    const street = r.street;
    const options = r.legal(part.seat, part.occ.balance());
    const pending = r.pending().length - 1;
    const auto = r.defaultDecision(part.seat, this.canAuto(part));
    const autoKey = auto === 'check' ? 'gui.burmaldaholic.uth.check' : auto === 'fold' ? 'gui.burmaldaholic.uth.fold' : 'gui.burmaldaholic.uth.bet_1x';
    const form = new ActionFormData()
      .title(this.title())
      .body(
        this.boardBody(part, [
          lit(''),
          color('§e', t('gui.burmaldaholic.uth.your_decision')),
          pending > 0 ? t('gui.burmaldaholic.uth.still_deciding', pending) : undefined,
          t('gui.burmaldaholic.common.auto_action', auto === 'bet1' ? t(autoKey, chips(part.ante)) : t(autoKey), duration(this.timeLeft(), true)),
        ]),
      );
    const shown: Decision[] = [];
    for (const o of options) {
      // Unaffordable Play bets are not offered (a disabled button cannot be drawn in an
      // ActionForm); the tooltip text explains it on the next line.
      if (!o.affordable) continue;
      const label = o.decision === 'check' ? t('gui.burmaldaholic.uth.check') : o.decision === 'fold' ? t('gui.burmaldaholic.uth.fold') : t(`gui.burmaldaholic.uth.${betKey(o.decision)}`, chips(o.amount));
      const tip = t(`gui.burmaldaholic.uth.${o.decision === 'check' ? 'check' : o.decision === 'fold' ? 'fold' : betKey(o.decision)}.tooltip`);
      form.button(lines(label, color('§8', tip)));
      shown.push(o.decision);
    }
    if (options.some((o) => !o.affordable)) form.label(color('§7', t('gui.burmaldaholic.uth.unaffordable')));
    const res = await showForm(p, form);
    if (!res || res.canceled || res.selection === undefined) return;
    if (this.round !== r || r.street !== street || part.away || !r.needsDecision(seat)) return;
    const d = shown[res.selection];
    if (!d) return;
    if (!this.decide(part, d, false)) {
      p.sendMessage(t('gui.burmaldaholic.uth.unaffordable'));
      this.safe(() => this.showDecision(part));
      return;
    }
    if (seat.play > 0) this.hud(p, t('gui.burmaldaholic.uth.waiting_showdown', t('gui.burmaldaholic.uth.play_multiple', seat.multiple)), 100);
  }

  private async showResult(s: TableSession, part: Participant): Promise<void> {
    const r = this.round;
    const st = part.settlement;
    if (!r || !st) return;
    const seat = r.seat(part.seat)!;
    const dealerValue = r.dealerValue();
    const q = st.qualifies ? color('§a', t('gui.burmaldaholic.uth.qualifies')) : color('§7', t('gui.burmaldaholic.uth.not_qualifies'));
    const form = new ActionFormData()
      .title(this.title())
      .body(this.boardBody(part, [lit(''), q, settlementLines(st, seat, r.playerValue(seat), dealerValue, { blind: this.rules.blindPays, trips: this.rules.tripsPays })]))
      .button(t('gui.burmaldaholic.common.play_again'))
      .button(t('gui.burmaldaholic.common.change_bet'))
      .button(t('gui.burmaldaholic.common.paytable'))
      .button(t('gui.burmaldaholic.common.leave'));
    const res = await showForm(s.player, form);
    if (!res || res.canceled || res.selection === undefined || !s.isActive()) return;
    if (res.selection === 3) return s.leave();
    if (res.selection === 2) return this.showPaytable(s);
    const intent = res.selection === 0 ? 'again' : 'change';
    if (this.phase !== 'betting') {
      this.intents.set(s.playerId, intent);
      this.hud(s.player, t('gui.burmaldaholic.common.waiting_players'), RESULT_TICKS);
      return;
    }
    if (this.bets.has(s.playerId)) return this.showLobby(s);
    const last = this.lastBets.get(s.playerId);
    if (intent === 'again' && last) {
      const err = this.placeBets(s, last.ante, last.trips);
      if (err) await this.promptBets(s, err);
    } else await this.promptBets(s);
  }

  private async showPaytable(s: TableSession): Promise<void> {
    const form = new ActionFormData()
      .title(t('gui.burmaldaholic.common.paytable'))
      .body(lines(paytableRaw(this.rules.blindPays, this.rules.tripsPays), lit(''), color('§7', rulesRaw(this.rules.allow3x))))
      .button(t('gui.burmaldaholic.common.back'));
    await showForm(s.player, form);
    if (s.isActive()) await this.showState(s);
  }

  /** Spectator / waiting view: table state + Close / Leave. */
  private async showWatch(s: TableSession): Promise<void> {
    const part = this.partOf(s.playerId);
    const extra: Raw[] = [];
    if (!part && this.phase !== 'betting') extra.push(lit(''), color('§e', t('gui.burmaldaholic.uth.waiting_next')));
    else if (part && this.round) {
      const seat = this.round.seat(part.seat)!;
      if (seat.play > 0 && this.phase === 'playing') extra.push(lit(''), t('gui.burmaldaholic.uth.waiting_showdown', t('gui.burmaldaholic.uth.play_multiple', seat.multiple)));
      else if (this.round.pending().length) extra.push(lit(''), t('gui.burmaldaholic.uth.still_deciding', this.round.pending().length));
    }
    await this.showInfoForm(s, this.boardBody(part, extra));
  }

  private async showInfoForm(s: TableSession, body: Raw): Promise<void> {
    const form = new ActionFormData().title(this.title()).body(body).button(t('gui.burmaldaholic.common.close')).button(t('gui.burmaldaholic.common.leave'));
    const res = await showForm(s.player, form);
    if (res && !res.canceled && res.selection === 1 && s.isActive()) s.leave();
  }
}

const betKey = (d: Decision): string => (d === 'bet4' ? 'bet_4x' : d === 'bet3' ? 'bet_3x' : d === 'bet2' ? 'bet_2x' : 'bet_1x');
const chipsOf = (n: number): Raw => chips(Math.max(0, n));

/** Whether a hand is a straight or better (the auto-play rule), exported for the admin tools. */
export const isMadeHand = (value: number): boolean => categoryOf(value) >= CAT.straight;
