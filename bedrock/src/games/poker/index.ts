/**
 * Texas Hold'em (GAME_DESIGN §7, UI.md §5). No-Limit cash game at `burmaldaholic:poker_table`
 * blocks: humans sit with a buy-in, house bots fill empty seats, hands run on a shared table
 * state machine (./logic/engine) with per-action timers, side pots and rake.
 *
 * Money: the buy-in moves from the balance to the table stack (economy.transact to the bank);
 * standing up moves the stack back. Hands are PvP pots (reported with wagers.recordPvp), so
 * nothing is house-banked; bots are funded by the house and their chips come from / go to the
 * bank. Table stacks are persisted in the world property `burmaldaholic:poker.stacks`, so a
 * crash or disconnect never loses chips: anything left there is paid on the player's next join
 * (a hand interrupted by a server stop is therefore refunded to its start stacks).
 */
import { type Player, system, world } from '@minecraft/server';
import { ActionFormData } from '@minecraft/server-ui';
import {
  type CasinoModule,
  type HouseRef,
  HudPriority,
  type LeaveReason,
  type ModuleContext,
  type Raw,
  type TableRef,
  type TableSession,
  NEWLINE,
  chips,
  color,
  duration,
  join,
  joinWith,
  lit,
  mathRng,
  promptAmount,
  randInt,
  showForm,
  t,
  worldJson,
} from '../../core';
import { MULTIPLAYER_SERVICE, type MultiplayerApi } from '../../multiplayer/api';
import { POKER_SERVICE, type PokerApi } from './api';
import { type BotTier, type BotView, botView, decideBot, opponentRanges, samplesFor } from './logic/bots';
import { type Action, type HandState, applyAction, coerce, legal, potTotal } from './logic/engine';
import { equityJob } from './logic/equity';
import { STAKE_LEVELS, STAKE_MIN_TIER, type StakeLevel, TableModel, buyInRange, isStakeLevel, smallBlind } from './logic/table';
import { eventRaw, resultLines, showdownBody, tableBody, toCallRaw } from './text';

const STACKS_PROP = 'burmaldaholic:poker.stacks';
const GAME = 'poker';

/** Optional multiplayer hook (not in MultiplayerApi yet): which house owns a table. */
type OwnedTables = MultiplayerApi & { houseOf?(tableKey: string): HouseRef | undefined };

interface LiveTable {
  key: string;
  ref: TableRef;
  stake: StakeLevel;
  model: TableModel;
  players: Map<string, Player>;
  /** index of the next hand event to stream */
  eventIdx: number;
  nextHandTimer?: number;
  actionTimer?: number;
  /** deadline (system tick) of the current human action */
  deadline: number;
  botSeq: number;
}

class PokerGame implements PokerApi {
  private readonly tables = new Map<string, LiveTable>();
  private botIds = 0;

  constructor(private readonly ctx: ModuleContext) {}

  // ---- PokerApi -------------------------------------------------------------------------

  isSeated(playerId: string): boolean {
    return [...this.tables.values()].some((l) => !!l.model.seatOf(playerId));
  }
  tableStack(playerId: string): number {
    for (const l of this.tables.values()) {
      const s = l.model.seatOf(playerId);
      if (s) return this.liveStack(l, playerId);
    }
    return 0;
  }
  tableCount(): number {
    return this.tables.size;
  }

  // ---- setup ----------------------------------------------------------------------------

  start(): void {
    const cfg = this.ctx.config;
    this.ctx.tables.register({
      id: GAME,
      seats: () => cfg.int('maxSeats'),
      // Core ends the session far beyond poker.maxDistance; closer walk-aways sit the player out.
      get maxDistance() {
        return Math.max(cfg.int('maxDistance') * 3, cfg.int('multiplayer.tableLeaveDistance'));
      },
      canJoin: (p, table) => this.canJoin(p, table),
      onOpen: (s, rejoined) => this.onOpen(s, rejoined),
      onLeave: (s, reason) => this.onLeave(s, reason),
    });
    for (const p of world.getAllPlayers()) this.payPending(p);
    world.afterEvents.playerSpawn.subscribe((e) => {
      if (e.initialSpawn) system.run(() => this.payPending(e.player));
    });
  }

  private canJoin(p: Player, table: TableRef): Raw | undefined {
    if (!this.ctx.config.bool('enabled')) return t('gui.burmaldaholic.error.disabled');
    const fixed = this.tables.get(table.key)?.stake ?? (isStakeLevel(table.variant) ? table.variant : undefined);
    if (fixed && this.ctx.limits.tier(p) < STAKE_MIN_TIER[fixed]) return t('gui.burmaldaholic.error.vip_required', this.ctx.limits.tierName(STAKE_MIN_TIER[fixed]));
    return undefined;
  }

  // ---- persistence of table stacks ------------------------------------------------------

  private stacks(): Record<string, number> {
    return worldJson.read<Record<string, number>>(STACKS_PROP, {});
  }
  private saveStack(id: string, amount: number | undefined): void {
    const all = this.stacks();
    if (amount === undefined || amount <= 0) delete all[id];
    else all[id] = Math.floor(amount);
    worldJson.write(STACKS_PROP, Object.keys(all).length ? all : undefined);
  }

  /** Stack left on a table by a disconnect or server stop: return it to the balance. */
  private payPending(p: Player): void {
    if (!p.isValid || this.isSeated(p.id)) return;
    const amount = this.stacks()[p.id] ?? 0;
    if (amount <= 0) return;
    this.saveStack(p.id, undefined);
    this.credit(p, amount);
    p.sendMessage(t('msg.burmaldaholic.poker.removed', chips(amount)));
  }

  private credit(p: Player, amount: number): void {
    if (amount <= 0) return;
    this.ctx.economy.transact([
      { account: p, delta: amount },
      { account: 'bank', delta: -amount },
    ], 'poker.cashout');
  }

  // ---- table lookup ---------------------------------------------------------------------

  private bbFor(stake: StakeLevel): number {
    return this.ctx.config.int(`poker.stakes.${stake}.bb`);
  }

  private createTable(ref: TableRef, stake: StakeLevel): LiveTable {
    const cfg = this.ctx.config;
    const live: LiveTable = {
      key: ref.key,
      ref,
      stake,
      model: new TableModel({
        maxSeats: cfg.int('maxSeats'),
        bb: this.bbFor(stake),
        rake: { percent: cfg.num('rakePercent'), capBb: cfg.int('rakeCapBb'), noFlopNoDrop: cfg.bool('rakeNoFlopNoDrop') },
      }),
      players: new Map(),
      eventIdx: 0,
      deadline: 0,
      botSeq: 0,
    };
    this.tables.set(ref.key, live);
    return live;
  }

  private destroy(live: LiveTable): void {
    if (live.nextHandTimer !== undefined) system.clearRun(live.nextHandTimer);
    if (live.actionTimer !== undefined) system.clearRun(live.actionTimer);
    live.botSeq++;
    this.tables.delete(live.key);
  }

  private player(live: LiveTable, id: string): Player | undefined {
    const p = live.players.get(id);
    return p && p.isValid ? p : undefined;
  }

  private humansOnline(live: LiveTable): Player[] {
    return live.model.humans().flatMap((s) => {
      const p = this.player(live, s.id);
      return p && !s.disconnected ? [p] : [];
    });
  }

  private liveStack(live: LiveTable, id: string): number {
    const h = live.model.hand;
    const k = live.model.handIndexOf(id);
    if (h && k >= 0) return h.players[k]!.stack;
    return live.model.seatOf(id)?.stack ?? 0;
  }

  // ---- session entry points ---------------------------------------------------------------

  private async onOpen(s: TableSession, _rejoined: boolean): Promise<void> {
    const live = this.tables.get(s.table.key);
    if (live?.model.seatOf(s.playerId)) {
      live.players.set(s.playerId, s.player);
      const h = live.model.hand;
      if (h && !h.complete && h.players[h.toAct]?.id === s.playerId) return this.showAction(live, s.player);
      return this.showStatus(live, s.player);
    }
    await this.joinFlow(s);
  }

  private async joinFlow(s: TableSession): Promise<void> {
    const p = s.player;
    if (this.isSeated(p.id)) {
      // stood up mid-hand at another table: that stack settles when its hand ends
      p.sendMessage(t('gui.burmaldaholic.error.busy'));
      return s.leave();
    }
    let live = this.tables.get(s.table.key);
    let stake: StakeLevel | undefined = live?.stake ?? (isStakeLevel(s.table.variant) ? s.table.variant : undefined);
    if (!stake) {
      stake = await this.chooseStakes(p);
      if (!stake || !s.isActive()) return s.leave();
      live = this.tables.get(s.table.key);
      if (live && live.stake !== stake) return this.joinFlow(s); // someone sat first with other stakes
    }
    if (this.ctx.limits.tier(p) < STAKE_MIN_TIER[stake]) {
      p.sendMessage(t('gui.burmaldaholic.error.vip_required', this.ctx.limits.tierName(STAKE_MIN_TIER[stake])));
      return s.leave();
    }
    const cfg = this.ctx.config;
    const bb = this.bbFor(stake);
    const balance = this.ctx.economy.balance(p);
    const range = buyInRange(bb, cfg.int('minBuyInBb'), cfg.int('maxBuyInBb'), balance);
    const fullMin = Math.min(cfg.int('minBuyInBb'), cfg.int('maxBuyInBb')) * bb;
    if (range.max < range.min) {
      p.sendMessage(t('gui.burmaldaholic.error.insufficient_funds', chips(balance)));
      return s.leave();
    }
    const amount = await promptAmount(p, {
      title: t('gui.burmaldaholic.poker.title'),
      info: [
        t('gui.burmaldaholic.poker.blinds', smallBlind(bb), bb),
        t('gui.burmaldaholic.poker.buy_in_range', fullMin, Math.max(cfg.int('minBuyInBb'), cfg.int('maxBuyInBb')) * bb),
        t('gui.burmaldaholic.common.balance', chips(balance)),
        this.rakeInfo(bb),
      ],
      min: range.min,
      max: range.max,
      default: range.max,
      sliderLabel: t('gui.burmaldaholic.poker.buy_in'),
      submit: t('gui.burmaldaholic.poker.sit_down'),
    });
    if (amount === undefined || !s.isActive()) return s.leave();
    if (!this.ctx.isCasinoEnabled()) return s.leave();
    live = this.tables.get(s.table.key) ?? this.createTable(s.table, stake);
    if (live.stake !== stake) return this.joinFlow(s);
    if (live.model.seatOf(p.id)) return this.showStatus(live, p);
    let idx = live.model.addHuman(p.id, p.name, amount);
    if (idx < 0 && !live.model.inHand()) {
      live.model.makeRoom();
      idx = live.model.addHuman(p.id, p.name, amount);
    }
    if (idx < 0) {
      p.sendMessage(t(live.model.inHand() ? 'gui.burmaldaholic.error.round_in_progress' : 'gui.burmaldaholic.error.table_full'));
      if (!live.model.humans().length) this.destroy(live);
      return s.leave();
    }
    const paid = this.ctx.economy.transact([
      { account: p, delta: -amount },
      { account: 'bank', delta: amount },
    ], 'poker.buyin');
    if (!paid) {
      live.model.removeSeat(p.id);
      p.sendMessage(t('gui.burmaldaholic.error.insufficient_funds', chips(this.ctx.economy.balance(p))));
      if (!live.model.humans().length) this.destroy(live);
      return s.leave();
    }
    this.saveStack(p.id, amount);
    live.players.set(p.id, p);
    this.ctx.hud.actionbar(p, 'poker.table', t('gui.burmaldaholic.poker.waiting_hand'), HudPriority.game, 80);
    if (!live.model.inHand()) this.scheduleHand(live, 40);
  }

  private rakeInfo(bb: number): Raw {
    const pct = Math.round(this.ctx.config.num('rakePercent') * 1000) / 10;
    return color('§7', t('gui.burmaldaholic.poker.rake_info', pct, this.ctx.config.int('rakeCapBb') * bb));
  }

  private async chooseStakes(p: Player): Promise<StakeLevel | undefined> {
    const tier = this.ctx.limits.tier(p);
    const form = new ActionFormData().title(t('gui.burmaldaholic.poker.title')).body(t('gui.burmaldaholic.poker.choose_stakes'));
    for (const lvl of STAKE_LEVELS) {
      const bb = this.bbFor(lvl);
      const label = t(`gui.burmaldaholic.poker.stakes.${lvl}`, smallBlind(bb), bb);
      const min = STAKE_MIN_TIER[lvl];
      form.button(tier >= min ? label : join(label, NEWLINE, color('§c', t('gui.burmaldaholic.common.requires_vip', this.ctx.limits.tierName(min)))));
    }
    form.button(t('gui.burmaldaholic.common.close'));
    const res = await showForm(p, form);
    if (!res || res.canceled || res.selection === undefined) return undefined;
    const lvl = STAKE_LEVELS[res.selection];
    if (!lvl) return undefined;
    if (tier < STAKE_MIN_TIER[lvl]) {
      p.sendMessage(t('gui.burmaldaholic.error.vip_required', this.ctx.limits.tierName(STAKE_MIN_TIER[lvl])));
      return this.chooseStakes(p);
    }
    return lvl;
  }

  private onLeave(s: TableSession, reason: LeaveReason): void {
    const live = this.tables.get(s.table.key);
    const seat = live?.model.seatOf(s.playerId);
    if (!live || !seat) return;
    const player = s.player.isValid ? s.player : undefined;
    if (reason === 'broken' || reason === 'casino_off') {
      // The table is going away: cancel the running hand (everyone keeps their start stack).
      if (live.model.inHand()) {
        live.model.abortHand();
        live.botSeq++;
        if (live.actionTimer !== undefined) system.clearRun(live.actionTimer);
        live.actionTimer = undefined;
      }
      this.cashOut(live, s.playerId, player);
      if (!live.model.humans().length) this.destroy(live);
      return;
    }
    seat.leaving = true;
    if (reason === 'disconnect') seat.disconnected = true;
    const h = live.model.hand;
    const k = live.model.handIndexOf(s.playerId);
    if (h && !h.complete && k >= 0 && !h.players[k]!.folded) {
      // Still in the hand: fold now if it is our turn, otherwise auto-fold when it comes.
      if (h.toAct === k) this.act(live, { type: 'fold' });
      if (live.model.inHand()) return; // cashed out at the end of the hand
    }
    if (live.model.seatOf(s.playerId)) this.cashOut(live, s.playerId, player);
    this.afterHumanLeft(live);
  }

  /** Remove a human seat and return the stack (or keep it pending if they are offline). */
  private cashOut(live: LiveTable, id: string, player: Player | undefined): void {
    const amount = this.liveStack(live, id);
    const seat = live.model.removeSeat(id);
    live.players.delete(id);
    if (!seat) return;
    if (player && player.isValid && !seat.disconnected) {
      this.saveStack(id, undefined);
      this.credit(player, amount);
      player.sendMessage(t('msg.burmaldaholic.poker.removed', chips(amount)));
      this.ctx.hud.clear(player, 'poker.table');
      const sess = this.ctx.tables.sessionOf(player);
      if (sess && sess.table.key === live.key) sess.leave();
    } else {
      this.saveStack(id, amount);
    }
  }

  private afterHumanLeft(live: LiveTable): void {
    if (live.model.humans().length) return;
    if (live.model.inHand()) live.model.abortHand();
    this.destroy(live);
  }

  // ---- hand loop ------------------------------------------------------------------------

  private scheduleHand(live: LiveTable, delay: number): void {
    if (live.nextHandTimer !== undefined) return;
    live.nextHandTimer = system.runTimeout(() => {
      live.nextHandTimer = undefined;
      try {
        this.beginHand(live);
      } catch (e) {
        this.ctx.log.error('poker: beginHand failed', e);
      }
    }, delay);
  }

  private beginHand(live: LiveTable): void {
    if (this.tables.get(live.key) !== live || live.model.inHand()) return;
    if (!this.ctx.isCasinoEnabled()) return;
    const cfg = this.ctx.config;
    const m = live.model;
    this.removeFinished(live);
    if (!m.humans().length) return this.destroy(live);
    // Walked away (> poker.maxDistance) at hand start -> sitting out.
    for (const s of m.humans()) {
      const p = this.player(live, s.id);
      if (!p) {
        s.disconnected = true;
        continue;
      }
      const far = p.dimension.id !== live.ref.dimension.id || dist(p.location, live.ref.location) > cfg.int('maxDistance');
      if (far && !s.sittingOut) {
        m.sitOut(s.id);
        p.sendMessage(t('gui.burmaldaholic.error.too_far'));
      }
    }
    this.removeFinished(live);
    if (!m.humans().length) return this.destroy(live);
    const { joined, left } = m.fillBots(
      { enabled: cfg.bool('botsEnabled'), mix: this.botMix(live.stake), buyIn: cfg.int('botBuyInBb') * m.bb },
      mathRng,
      () => `bot:${++this.botIds}`,
    );
    for (const b of left) if (b.stack <= 0) this.broadcast(live, t('msg.burmaldaholic.poker.bot_busts', lit(b.name)));
    for (const b of joined) this.broadcast(live, t('msg.burmaldaholic.poker.bot_joins', lit(b.name), t(`gui.burmaldaholic.poker.bot.${b.tier}`)));
    if (!m.canStart()) {
      for (const p of this.humansOnline(live)) this.ctx.hud.actionbar(p, 'poker.table', t('gui.burmaldaholic.common.waiting_players'), HudPriority.game, 100);
      return;
    }
    const h = m.startHand(mathRng);
    if (!h) return;
    live.eventIdx = 0;
    for (const s of m.humans()) this.saveStack(s.id, s.stack);
    this.broadcast(live, t('msg.burmaldaholic.poker.new_hand', m.handNo, m.sb, m.bb));
    this.streamEvents(live);
    this.drive(live);
  }

  private botMix(stake: StakeLevel): number[] {
    const v = this.ctx.config.json<number[]>(`poker.botMix.${stake}`);
    return Array.isArray(v) ? v.map((x) => Number(x) || 0) : [50, 40, 10];
  }

  /** Remove humans who stood up, disconnected, went broke or sat out too long. */
  private removeFinished(live: LiveTable): void {
    if (live.model.inHand()) return;
    for (const s of live.model.toRemove(this.ctx.config.int('sitOutHandsToRemove'))) {
      this.cashOut(live, s.id, this.player(live, s.id));
    }
  }

  private broadcast(live: LiveTable, msg: Raw): void {
    for (const p of this.humansOnline(live)) p.sendMessage(msg);
  }

  /** Stream new hand events (blinds, actions, streets) to every seated human's action bar. */
  private streamEvents(live: LiveTable): void {
    const h = live.model.hand;
    if (!h) return;
    const fresh = h.events.slice(live.eventIdx);
    live.eventIdx = h.events.length;
    if (!fresh.length) return;
    const msg = joinWith(lit(' §8·§r '), fresh.slice(-3).map((e) => eventRaw(live.model, h, e)));
    for (const p of this.humansOnline(live)) this.ctx.hud.actionbar(p, 'poker.table', msg, HudPriority.game, 80);
  }

  /** Hand in progress: hand the turn to the next actor (human form / bot / auto). */
  private drive(live: LiveTable): void {
    const h = live.model.hand;
    if (!h) return;
    if (live.actionTimer !== undefined) system.clearRun(live.actionTimer);
    live.actionTimer = undefined;
    if (h.complete) return this.endHand(live);
    const p = h.players[h.toAct]!;
    const seat = live.model.seatOf(p.id);
    const seq = h.seq;
    if (!seat || seat.kind === 'bot') return this.scheduleBot(live, h, seq);
    const player = this.player(live, p.id);
    if (!player || seat.sittingOut || seat.leaving || seat.disconnected) {
      // Auto check/fold on the next tick (sitting out / gone).
      live.actionTimer = system.runTimeout(() => {
        if (this.isCurrent(live, h, seq)) this.act(live, { type: 'fold' });
      }, 10);
      return;
    }
    const ticks = this.ctx.config.int('actionTimerTicks');
    live.deadline = system.currentTick + ticks;
    live.actionTimer = system.runTimeout(() => this.onTimeout(live, h, seq), ticks);
    this.ctx.hud.actionbar(player, 'poker.turn', color('§a', t('gui.burmaldaholic.poker.your_turn')), HudPriority.game, 60);
    const sess = this.ctx.tables.sessionOf(player);
    if (sess) sess.closeForms();
    system.run(() => void this.showAction(live, player));
  }

  private isCurrent(live: LiveTable, h: HandState, seq: number): boolean {
    return this.tables.get(live.key) === live && live.model.hand === h && !h.complete && h.seq === seq && this.ctx.isCasinoEnabled();
  }

  private onTimeout(live: LiveTable, h: HandState, seq: number): void {
    if (!this.isCurrent(live, h, seq)) return;
    live.actionTimer = undefined;
    const id = h.players[h.toAct]!.id;
    const l = legal(h);
    const auto: Action = l.canCheck ? { type: 'check' } : { type: 'fold' };
    const player = this.player(live, id);
    if (player) {
      player.sendMessage(t('msg.burmaldaholic.poker.timeout', t(`gui.burmaldaholic.poker.${auto.type}`)));
      this.ctx.tables.sessionOf(player)?.closeForms();
    }
    if (live.model.recordTimeout(id, this.ctx.config.int('timeoutsToSitOut')) && player) player.sendMessage(t('msg.burmaldaholic.poker.sat_out'));
    this.act(live, auto);
  }

  /** Apply an action for the player to act (coerced to a legal one) and continue. */
  private act(live: LiveTable, a: Action): void {
    const h = live.model.hand;
    if (!h || h.complete) return;
    try {
      applyAction(h, coerce(h, a));
    } catch (e) {
      this.ctx.log.warn(`poker: illegal action ${JSON.stringify(a)} coerced to fold/check`, e);
      applyAction(h, legal(h).canCheck ? { type: 'check' } : { type: 'fold' });
    }
    this.streamEvents(live);
    this.drive(live);
  }

  private scheduleBot(live: LiveTable, h: HandState, seq: number): void {
    const cfg = this.ctx.config;
    const lo = Math.min(cfg.int('botThinkMinTicks'), cfg.int('botThinkMaxTicks'));
    const hi = Math.max(cfg.int('botThinkMinTicks'), cfg.int('botThinkMaxTicks'));
    const botSeq = live.botSeq;
    live.actionTimer = system.runTimeout(() => {
      live.actionTimer = undefined;
      if (!this.isCurrent(live, h, seq) || live.botSeq !== botSeq) return;
      const i = h.toAct;
      const seat = live.model.seatOf(h.players[i]!.id);
      const tier = seat?.tier ?? 'regular';
      const view = botView(h, i, live.model.vpipMap());
      const samples = samplesFor(tier, h.street, { regularSamples: cfg.int('bot.regularSamples'), sharkSamples: cfg.int('bot.sharkSamples') });
      if (samples <= 0) return this.act(live, decideBot(tier, view, undefined, mathRng));
      const ranges = tier === 'shark' ? opponentRanges(h, i) : undefined;
      // Monte-Carlo spread over ticks (GAME_DESIGN §7.4 performance note).
      system.runJob(this.botJob(live, h, seq, botSeq, tier, view, samples, ranges));
    }, randInt(mathRng, lo, hi));
  }

  private *botJob(
    live: LiveTable,
    h: HandState,
    seq: number,
    botSeq: number,
    tier: BotTier,
    view: BotView,
    samples: number,
    ranges: (number | undefined)[] | undefined,
  ): Generator<void, void, void> {
    try {
      const e: number = yield* equityJob({ hole: view.hole, board: view.board, opponents: view.opponents, samples, ranges }, mathRng);
      if (this.isCurrent(live, h, seq) && live.botSeq === botSeq) this.act(live, decideBot(tier, view, e, mathRng));
    } catch (err) {
      this.ctx.log.error('poker: bot job failed', err);
      if (this.isCurrent(live, h, seq)) this.act(live, { type: 'fold' });
    }
  }

  private endHand(live: LiveTable): void {
    const m = live.model;
    const h = m.hand;
    if (!h || !h.result) return;
    const r = h.result;
    // Chat: winners. Humans: showdown form (if it went to showdown) and PvP stats.
    for (const line of resultLines(m, h)) this.broadcast(live, line);
    h.players.forEach((p, k) => {
      if (!p.human) return;
      const player = this.player(live, p.id);
      if (player) this.ctx.wagers.recordPvp(player, GAME, p.total, r.net[k]!);
    });
    if (r.rake > 0) this.payRake(live, r.rake);
    m.settleHand();
    for (const s of m.humans()) this.saveStack(s.id, s.stack);
    for (const s of m.humans()) {
      if (s.leaving || s.disconnected) continue;
      const p = this.player(live, s.id);
      if (p && !r.uncontested && h.players.some((x) => x.id === s.id)) void this.showShowdown(live, h, p);
    }
    this.removeFinished(live);
    if (!m.humans().length) return this.destroy(live);
    this.scheduleHand(live, r.uncontested ? 60 : 120);
  }

  /** Rake: to the owner's bankroll at owned tables, otherwise removed from the game. */
  private payRake(live: LiveTable, rake: number): void {
    const house = this.ctx.services.get<OwnedTables>(MULTIPLAYER_SERVICE)?.houseOf?.(live.key);
    if (house?.kind === 'bankroll') {
      this.ctx.economy.transact([
        { account: { bankroll: house.id }, delta: rake },
        { account: 'bank', delta: -rake },
      ], 'poker.rake');
    }
  }

  // ---- forms ----------------------------------------------------------------------------

  private async showAction(live: LiveTable, player: Player): Promise<void> {
    const h = live.model.hand;
    if (!h || h.complete || h.players[h.toAct]?.id !== player.id) return;
    const seq = h.seq;
    const l = legal(h);
    const p = h.players[h.toAct]!;
    const pot = potTotal(h);
    const left = Math.max(0, live.deadline - system.currentTick);
    const auto = l.canCheck ? t('gui.burmaldaholic.poker.check') : t('gui.burmaldaholic.poker.fold');
    const form = new ActionFormData()
      .title(t('gui.burmaldaholic.poker.title'))
      .body(tableBody(live.model, player.id, [lit(''), color('§a', t('gui.burmaldaholic.poker.your_turn')), toCallRaw(h), t('gui.burmaldaholic.common.auto_action', auto, duration(left))]));
    const options: Action[] = [];
    const add = (label: Raw, a: Action) => {
      form.button(label);
      options.push(a);
    };
    if (!l.canCheck) add(t('gui.burmaldaholic.poker.fold'), { type: 'fold' });
    if (l.canCheck) add(t('gui.burmaldaholic.poker.check'), { type: 'check' });
    else add(p.stack <= l.toCall ? t('gui.burmaldaholic.poker.all_in', p.bet + p.stack) : t('gui.burmaldaholic.poker.call', l.toCall), { type: 'call' });
    if (l.canRaise) {
      const seen = new Set<number>();
      const quick: [string, number][] = [
        ['gui.burmaldaholic.poker.half_pot', 0.5],
        ['gui.burmaldaholic.poker.three_quarter_pot', 0.75],
        ['gui.burmaldaholic.poker.pot_size', 1],
      ];
      for (const [key, f] of quick) {
        const to = h.currentBet === 0 ? Math.round(pot * f) : Math.round(h.currentBet + f * (pot + l.toCall));
        if (to < l.minRaiseTo || to >= l.maxRaiseTo || seen.has(to)) continue;
        seen.add(to);
        add(join(t(key), lit(' §7('), to, lit(')§r')), { type: 'raise', to });
      }
      if (l.minRaiseTo < l.maxRaiseTo) add(join(t(l.isBet ? 'gui.burmaldaholic.poker.bet' : 'gui.burmaldaholic.poker.raise'), lit('…')), { type: 'raise', to: -1 });
      add(t('gui.burmaldaholic.poker.all_in', l.maxRaiseTo), { type: 'allin' });
    }
    const res = await showForm(player, form);
    if (!res || res.canceled || res.selection === undefined) return; // timer keeps running
    if (!this.isCurrent(live, h, seq)) return;
    let a = options[res.selection];
    if (!a) return;
    if (a.type === 'raise' && a.to < 0) {
      const to = await promptAmount(player, {
        title: t(l.isBet ? 'gui.burmaldaholic.poker.bet' : 'gui.burmaldaholic.poker.raise'),
        info: [t('gui.burmaldaholic.poker.pot', pot), ...(l.toCall > 0 ? [t('gui.burmaldaholic.poker.to_call', l.toCall)] : []), t('gui.burmaldaholic.poker.stack', p.stack)],
        min: l.minRaiseTo,
        max: l.maxRaiseTo,
        default: l.minRaiseTo,
        sliderLabel: t(l.isBet ? 'gui.burmaldaholic.poker.bet' : 'gui.burmaldaholic.poker.raise'),
        submit: t(l.isBet ? 'gui.burmaldaholic.poker.bet' : 'gui.burmaldaholic.poker.raise'),
      });
      if (!this.isCurrent(live, h, seq)) return;
      if (to === undefined) return this.showAction(live, player);
      a = to >= l.maxRaiseTo ? { type: 'allin' } : { type: 'raise', to };
    }
    live.model.recordAction(player.id);
    this.act(live, a);
  }

  private async showStatus(live: LiveTable, player: Player): Promise<void> {
    const m = live.model;
    const seat = m.seatOf(player.id);
    if (!seat) return;
    const inHand = m.inHand() && m.handIndexOf(player.id) >= 0 && !m.hand!.players[m.handIndexOf(player.id)]!.folded;
    const status = seat.sittingOut ? t('gui.burmaldaholic.poker.sitting_out') : m.inHand() ? undefined : t('gui.burmaldaholic.poker.waiting_hand');
    const form = new ActionFormData()
      .title(t('gui.burmaldaholic.poker.title'))
      .body(tableBody(m, player.id, [lit(''), status, this.rakeInfo(m.bb)]));
    const actions: (() => void | Promise<void>)[] = [];
    const add = (label: Raw, fn: () => void | Promise<void>) => {
      form.button(label);
      actions.push(fn);
    };
    if (seat.sittingOut) {
      add(t('gui.burmaldaholic.poker.sit_in'), () => {
        m.sitIn(player.id);
        if (!m.inHand()) this.scheduleHand(live, 20);
      });
    } else {
      add(t('gui.burmaldaholic.poker.sit_out'), () => m.sitOut(player.id));
    }
    if (!inHand) add(t('gui.burmaldaholic.poker.top_up'), () => this.topUp(live, player));
    add(t('gui.burmaldaholic.poker.stand_up'), () => this.ctx.tables.sessionOf(player)?.leave());
    add(t('gui.burmaldaholic.common.close'), () => {});
    const res = await showForm(player, form);
    if (!res || res.canceled || res.selection === undefined) return;
    if (this.tables.get(live.key) !== live || !m.seatOf(player.id)) return;
    await actions[res.selection]?.();
  }

  private async topUp(live: LiveTable, player: Player): Promise<void> {
    const m = live.model;
    const cfg = this.ctx.config;
    const seat = m.seatOf(player.id);
    if (!seat) return;
    const balance = this.ctx.economy.balance(player);
    const range = buyInRange(m.bb, cfg.int('minBuyInBb'), cfg.int('maxBuyInBb'), balance, seat.stack);
    if (range.max < range.min) {
      player.sendMessage(range.max <= 0 && balance > 0 ? t('gui.burmaldaholic.error.table_max', chips(seat.stack)) : t('gui.burmaldaholic.error.insufficient_funds', chips(balance)));
      return;
    }
    const amount = await promptAmount(player, {
      title: t('gui.burmaldaholic.poker.top_up'),
      info: [t('gui.burmaldaholic.poker.stack', seat.stack), t('gui.burmaldaholic.common.balance', chips(balance))],
      min: range.min,
      max: range.max,
      default: range.max,
      sliderLabel: t('gui.burmaldaholic.poker.top_up'),
      submit: t('gui.burmaldaholic.poker.top_up'),
    });
    if (amount === undefined || m.seatOf(player.id) !== seat) return;
    const inHand = m.inHand() && m.handIndexOf(player.id) >= 0 && !m.hand!.players[m.handIndexOf(player.id)]!.folded;
    if (inHand) return player.sendMessage(t('gui.burmaldaholic.error.round_in_progress'));
    const ok = this.ctx.economy.transact([
      { account: player, delta: -amount },
      { account: 'bank', delta: amount },
    ], 'poker.buyin');
    if (!ok) return player.sendMessage(t('gui.burmaldaholic.error.insufficient_funds', chips(this.ctx.economy.balance(player))));
    // A folded player in the running hand gets the chips on their hand stack too.
    const k = m.handIndexOf(player.id);
    if (m.inHand() && k >= 0) m.hand!.players[k]!.stack += amount;
    seat.stack += amount;
    this.saveStack(player.id, this.liveStack(live, player.id));
    player.sendMessage(t('gui.burmaldaholic.poker.stack', this.liveStack(live, player.id)));
    if (!m.inHand()) this.scheduleHand(live, 20);
  }

  private async showShowdown(live: LiveTable, h: HandState, player: Player): Promise<void> {
    const form = new ActionFormData()
      .title(t('gui.burmaldaholic.poker.title'))
      .body(showdownBody(live.model, h, player.id))
      .button(t('gui.burmaldaholic.poker.next_hand'))
      .button(t('gui.burmaldaholic.poker.stand_up'));
    const res = await showForm(player, form);
    if (!res || res.canceled) return;
    if (res.selection === 1 && live.model.seatOf(player.id)) this.ctx.tables.sessionOf(player)?.leave();
  }
}

function dist(a: { x: number; y: number; z: number }, b: { x: number; y: number; z: number }): number {
  return Math.hypot(a.x - b.x, a.y - b.y, a.z - b.z);
}

export const pokerModule: CasinoModule = {
  id: 'poker',
  onWorldLoad(ctx) {
    const game = new PokerGame(ctx);
    game.start();
    ctx.services.provide<PokerApi>(POKER_SERVICE, game);
  },
};
