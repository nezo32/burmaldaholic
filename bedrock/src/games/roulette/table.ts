/**
 * Roulette runtime: one shared RouletteRound per table block, money through ctx.wagers (one
 * chip ticket per player per spin: place() for the first bet, raise() for the next ones,
 * settle() once with the total return), Bedrock forms (UI.md §7) and the action-bar spin.
 *
 * Disconnects (GAME_DESIGN §4.1): the spin proceeds. Bettors who are offline at settlement are
 * settled through core anyway (core parks the payout and applies it on their next join, with
 * the result text); only the VIP `roulette_red` contract progress waits here for the join.
 * Table broken / casino mode off: open bets are refunded.
 */
import { type Player, system, world } from '@minecraft/server';
import { ActionFormData, type ActionFormResponse, ModalFormData, type ModalFormResponse } from '@minecraft/server-ui';
import {
  HudPriority,
  type ModuleContext,
  ModalLayout,
  NEWLINE,
  type Raw,
  type TableRef,
  type TableSession,
  type WagerTicket,
  chips,
  color,
  duration,
  formatSigned,
  join,
  lines,
  lit,
  mathRng,
  parseAmount,
  showForm,
  sliderStep,
  t,
  unit,
} from '../../core';
import { VIP_SERVICE, type VipApi } from '../../vip/api';
import { WORLDGEN_SERVICE, type WorldgenApi } from '../../worldgen/api';
import type { RouletteApi, RouletteSpinEntry, RouletteSpinEvent } from './api';
import {
  type Bet,
  type BetType,
  type Frame,
  INSIDE_TYPES,
  OUTSIDE_TYPES,
  RouletteRound,
  type SlipError,
  type SlipLimits,
  type Spot,
  allSpots,
  checkAdd,
  checkSpin,
  isEvenMoney,
  isInside,
  maxAddable,
  mergeBet,
  settleSlip,
  slipLimits,
  spinFrames,
  totalStaked,
  wheelIndex,
  worstCase,
} from './logic';
import { betLine, betTypeLabel, historyRaw, positionRaw, resultRaw, stripRaw } from './text';

const K = 'gui.burmaldaholic.roulette';
const HUD_CHANNEL = 'roulette.table';
const NO_MORE_BETS_TICKS = 20;
const RESULT_TICKS = 60;

interface TableRt {
  readonly key: string;
  readonly ref: TableRef;
  readonly highRoller: boolean;
  readonly round: RouletteRound;
  /** open wager per bettor (player id) */
  readonly tickets: Map<string, WagerTicket>;
  anim?: { start: number; frames: Frame[]; shown: number };
  wheelPos: number;
}


type MainAction = 'add' | 'rebet' | 'clear' | 'ready' | 'refresh' | 'leave';

export class RouletteGame implements RouletteApi {
  private readonly tables = new Map<string, TableRt>();
  /** last spin's slip per player (Rebet) */
  private readonly lastBets = new Map<string, Bet[]>();
  /** winning red bets of players who were offline at settlement (vip contract, reported on join) */
  private readonly pendingRed = new Map<string, number>();
  /** form generation per player: bumped when the server closes forms, stale responses are ignored */
  private readonly epochs = new Map<string, number>();
  /** last dropdown index per player and bet type */
  private readonly lastPos = new Map<string, number>();
  private readonly listeners: ((e: RouletteSpinEvent) => void)[] = [];

  constructor(private readonly ctx: ModuleContext) {}

  // ---- RouletteApi ------------------------------------------------------------------------

  onSpin(listener: (e: RouletteSpinEvent) => void): void {
    this.listeners.push(listener);
  }

  history(tableKey: string): readonly number[] {
    return this.tables.get(tableKey)?.round.history ?? [];
  }

  // ---- lifecycle --------------------------------------------------------------------------

  start(): void {
    const ctx = this.ctx;
    ctx.tables.register({
      id: 'roulette',
      seats: () => ctx.config.int('roulette.maxBettors'),
      canJoin: (p, table) => (ctx.config.bool('roulette.enabled') ? ctx.wagers.check(p, 'roulette', table.key) : t('gui.burmaldaholic.error.disabled')),
      onOpen: (s) => this.onOpen(s),
      onLeave: (s, reason) => {
        if (reason === 'broken' || reason === 'casino_off') this.refundPlayer(this.tables.get(s.table.key), s.playerId, s.player);
        this.epochs.delete(s.playerId);
      },
    });
    system.runInterval(() => {
      try {
        this.tick();
      } catch (e) {
        ctx.log.error('roulette tick failed', e);
      }
    }, 1);
    world.afterEvents.playerSpawn.subscribe((e) => {
      if (e.initialSpawn) system.runTimeout(() => this.flushPending(e.player), 20);
    });
    // The vip module may load after us: announce the roulette_red contract source lazily.
    system.run(() => this.vip()?.registerContractSource('roulette_red'));
  }

  private table(ref: TableRef): TableRt {
    let rt = this.tables.get(ref.key);
    if (!rt) {
      rt = {
        key: ref.key,
        ref,
        highRoller: ref.variant === 'high_roller' || this.presetAt(ref)?.id === 'high_roller_roulette',
        round: new RouletteRound(this.timings(), this.ctx.config.int('roulette.historyLength')),
        tickets: new Map(),
        wheelPos: 0,
      };
      this.tables.set(ref.key, rt);
    }
    return rt;
  }

  private timings() {
    const c = this.ctx.config;
    return { betTicks: c.int('roulette.betTimerTicks'), noMoreBetsTicks: NO_MORE_BETS_TICKS, spinTicks: c.int('roulette.spinTicks'), resultTicks: RESULT_TICKS };
  }

  private limitsFor(p: Player, rt: TableRt): SlipLimits {
    const c = this.ctx.config;
    const mult = rt.highRoller ? c.num('roulette.highRollerMaxMultiplier') : 1;
    const preset = this.presetAt(rt.ref);
    // Owner min/max of an owned table (multiplayer, via core's limits resolver).
    const own = this.ctx.wagers.limitsFor(p, 'roulette', { min: Math.max(c.int('roulette.minBet'), preset?.minBet ?? 0) }, rt.key);
    const tierMax = this.ctx.limits.tierMax(p, mult * (preset?.tierMultiplier ?? 1));
    return slipLimits({
      tierMax: own.tableMax === undefined ? tierMax : Math.min(tierMax, own.tableMax),
      minBet: own.min ?? c.int('roulette.minBet'),
      insideMaxFraction: c.num('roulette.insideMaxFraction'),
      minTotal: rt.highRoller ? c.int('roulette.highRollerMinTotal') : 0,
    });
  }

  /** Worldgen table preset (High Roller Lounge roulette: min bet 100). */
  private presetAt(ref: TableRef) {
    try {
      return this.ctx.services.get<WorldgenApi>(WORLDGEN_SERVICE)?.tablePreset(ref.dimension.id, ref.location);
    } catch {
      return undefined;
    }
  }

  private seated(rt: TableRt): TableSession[] {
    return this.ctx.tables.sessionsAt(rt.key);
  }

  // ---- money ------------------------------------------------------------------------------

  /** Validate, pay and add bets to the player's slip. Returns an error text or undefined. */
  private addBets(p: Player, rt: TableRt, bets: readonly Bet[]): Raw | undefined {
    if (!this.ctx.isCasinoEnabled()) return t('gui.burmaldaholic.error.casino_off');
    if (!rt.round.canBet()) return t(`${K}.no_more_bets`);
    const slip = rt.round.bets(p.id);
    const err = checkAdd(slip, bets, this.limitsFor(p, rt));
    if (err) return slipErrorText(err);
    const amount = totalStaked(bets);
    const balance = this.ctx.economy.balance(p);
    if (amount > balance) return t('gui.burmaldaholic.error.insufficient_funds', chips(balance));
    const la = this.ctx.config.bool('roulette.laPartage');
    let merged = slip.slice();
    for (const b of bets) merged = mergeBet(merged, b);
    const before = worstCase(slip, la);
    const after = worstCase(merged, la);
    const ticket = rt.tickets.get(p.id);
    if (!ticket) {
      // House: the world bank, or an owner's bankroll at an owned table (core house resolver).
      const r = this.ctx.wagers.place(p, { game: 'roulette', stake: { kind: 'chips', amount }, skipLimits: true, tableKey: rt.key, worstCase: after, notify: false });
      if (!r.ok) return r.error;
      rt.tickets.set(p.id, r.ticket);
    } else if (!this.ctx.wagers.raise(ticket, p, amount, Math.max(0, after - before))) {
      return t('gui.burmaldaholic.error.insufficient_funds', chips(this.ctx.economy.balance(p)));
    }
    rt.round.addBets(p.id, bets);
    return undefined;
  }

  /** Clear bets (betting phase only): full refund, no streak effect. */
  private clearBets(p: Player, rt: TableRt): void {
    if (!rt.round.canBet()) return;
    const ticket = rt.tickets.get(p.id);
    if (ticket) this.ctx.wagers.refund(ticket, p);
    rt.tickets.delete(p.id);
    rt.round.clear(p.id);
  }

  /** Refund a player's open bets at a table (table broken, casino off, High-Roller minimum). */
  private refundPlayer(rt: TableRt | undefined, playerId: string, player?: Player): void {
    if (!rt) return;
    const bets = rt.round.clear(playerId);
    const ticket = rt.tickets.get(playerId);
    rt.tickets.delete(playerId);
    if (!ticket) return;
    // Offline-safe: core parks the refund for a disconnected player.
    this.ctx.wagers.refund(ticket, player ?? onlinePlayer(playerId));
    this.ctx.wagers.tell(playerId, t('msg.burmaldaholic.roulette.bets_refunded', chips(totalStaked(bets) || ticket.value)));
  }

  /** VIP contract progress of spins settled while the player was offline. */
  private flushPending(player: Player): void {
    const red = this.pendingRed.get(player.id);
    if (!red || !player.isValid) return;
    this.pendingRed.delete(player.id);
    this.reportRed(player, red);
  }

  private vip(): VipApi | undefined {
    return this.ctx.services.get<VipApi>(VIP_SERVICE);
  }

  /** Daily contract "win bets on red" (vip module; no-op when absent). */
  private reportRed(p: Player, wins: number): void {
    if (wins <= 0) return;
    try {
      this.vip()?.reportContract(p, 'roulette_red', wins);
    } catch (e) {
      this.ctx.log.error('vip.reportContract failed', e);
    }
  }

  // ---- the shared table loop ----------------------------------------------------------------

  private tick(): void {
    const now = system.currentTick;
    const casinoOn = this.ctx.isCasinoEnabled();
    for (const rt of this.tables.values()) {
      if (!casinoOn) {
        // Casino closed: refund everything that is not settled yet and reset the table.
        for (const id of rt.round.bettors()) this.refundPlayer(rt, id);
        rt.anim = undefined;
        if (rt.round.phase !== 'betting') this.tables.delete(rt.key);
        continue;
      }
      if (rt.round.phase === 'betting' && !rt.round.hasBets() && !rt.anim) continue;
      const sessions = this.seated(rt);
      this.animate(rt, sessions, now);
      const minTotal = rt.highRoller ? this.ctx.config.int('roulette.highRollerMinTotal') : 0;
      const tr = rt.round.update(now, sessions.map((s) => s.playerId), mathRng, minTotal);
      if (!tr) {
        this.countdown(rt, sessions, now);
        continue;
      }
      switch (tr.to) {
        case 'no_more_bets':
          this.dropBelowMinimum(rt, tr.dropped, minTotal);
          for (const id of rt.round.bettors()) this.lastBets.set(id, [...rt.round.bets(id)]);
          for (const s of sessions) {
            this.closeFor(s);
            this.ctx.hud.actionbar(s.player, HUD_CHANNEL, t(`${K}.no_more_bets`), HudPriority.game, 40);
          }
          break;
        case 'spin':
          rt.anim = { start: now, frames: spinFrames(tr.result, this.ctx.config.int('roulette.spinTicks'), rt.wheelPos), shown: -1 };
          for (const s of sessions) s.player.sendMessage(t('msg.burmaldaholic.roulette.spinning'));
          break;
        case 'result':
          rt.anim = undefined;
          rt.wheelPos = wheelIndex(tr.result);
          this.settleAll(rt, tr.result, tr.slips, sessions);
          break;
        case 'betting':
          this.dropBelowMinimum(rt, tr.dropped, minTotal);
          for (const s of sessions) this.ctx.hud.actionbar(s.player, HUD_CHANNEL, t(`${K}.place_bets`), HudPriority.game, 60);
          if (!tr.reset) for (const s of sessions) this.reshowMain(s);
          break;
      }
    }
  }

  private dropBelowMinimum(rt: TableRt, dropped: readonly string[], minTotal: number): void {
    for (const id of dropped) {
      const ticket = rt.tickets.get(id);
      rt.tickets.delete(id);
      if (!ticket) continue;
      this.ctx.wagers.refund(ticket, onlinePlayer(id));
      this.ctx.wagers.tell(id, t(`${K}.error.min_total`, chips(minTotal)));
      this.ctx.wagers.tell(id, t('msg.burmaldaholic.roulette.bets_refunded', chips(ticket.value)));
    }
  }

  /** Once a second while the bet timer runs: "Time left: 12 seconds" on the action bar. */
  private countdown(rt: TableRt, sessions: TableSession[], now: number): void {
    const left = rt.round.phase === 'betting' ? rt.round.remaining(now) : undefined;
    if (left === undefined || left % 20 !== 0) return;
    for (const s of sessions) this.ctx.hud.actionbar(s.player, HUD_CHANNEL, t('gui.burmaldaholic.common.timer', duration(left)), HudPriority.game, 25);
  }

  private animate(rt: TableRt, sessions: TableSession[], now: number): void {
    const a = rt.anim;
    if (!a) return;
    const elapsed = now - a.start;
    let i = a.shown;
    while (i + 1 < a.frames.length && a.frames[i + 1]!.at <= elapsed) i++;
    if (i === a.shown || i < 0) return;
    a.shown = i;
    const strip = stripRaw(a.frames[i]!.index);
    for (const s of sessions) this.ctx.hud.actionbar(s.player, HUD_CHANNEL, strip, HudPriority.game, 40);
  }

  private settleAll(rt: TableRt, result: number, slips: ReadonlyMap<string, readonly Bet[]>, sessions: TableSession[]): void {
    const la = this.ctx.config.bool('roulette.laPartage');
    const entries: RouletteSpinEntry[] = [];
    const outcome = new Map<string, { bets: readonly Bet[]; returns: number[]; staked: number; totalReturn: number }>();
    for (const [id, bets] of slips) {
      const s = settleSlip(bets, result, la);
      const ticket = rt.tickets.get(id);
      rt.tickets.delete(id);
      const p = onlinePlayer(id);
      const text = resultRaw(result, s.staked, s.totalReturn);
      if (ticket) {
        const redWins = bets.filter((b, i) => b.type === 'red' && (s.returns[i] ?? 0) > b.amount).length;
        // Offline-safe: core parks the payout of a disconnected bettor until they rejoin.
        this.ctx.wagers.settle(ticket, p, s.totalReturn);
        if (p) this.reportRed(p, redWins);
        else {
          if (redWins > 0) this.pendingRed.set(id, (this.pendingRed.get(id) ?? 0) + redWins);
          this.ctx.wagers.tell(id, t('msg.burmaldaholic.core.auto_completed', text));
        }
      }
      if (p) {
        p.sendMessage(text);
        if (result === 0 && bets.some((b) => b.type === 'straight' && b.numbers[0] === 0)) p.sendMessage(t('msg.burmaldaholic.roulette.zero_hero'));
      }
      outcome.set(id, { bets, returns: s.returns, staked: s.staked, totalReturn: s.totalReturn });
      entries.push({
        playerId: id,
        player: p,
        bets: bets.map((b, i) => ({ type: b.type, numbers: b.numbers, amount: b.amount, totalReturn: s.returns[i] ?? 0 })),
        staked: s.staked,
        totalReturn: s.totalReturn,
      });
    }
    for (const s of sessions) {
      const o = outcome.get(s.playerId);
      this.ctx.hud.actionbar(s.player, HUD_CHANNEL, resultRaw(result, o?.staked ?? 0, o?.totalReturn ?? 0), HudPriority.game, 60);
      this.closeFor(s);
      void this.showResult(s, rt, result, o);
    }
    const ev: RouletteSpinEvent = { table: rt.key, result, entries };
    for (const l of this.listeners) {
      try {
        l(ev);
      } catch (e) {
        this.ctx.log.error('onSpin listener failed', e);
      }
    }
  }

  // ---- forms ------------------------------------------------------------------------------

  private epoch(id: string): number {
    return this.epochs.get(id) ?? 0;
  }

  /** Server-side close: bump the epoch so the pending response is ignored. */
  private closeFor(s: TableSession): void {
    this.epochs.set(s.playerId, this.epoch(s.playerId) + 1);
    s.closeForms();
  }

  private reshowMain(s: TableSession): void {
    this.closeFor(s);
    void this.showMain(s);
  }

  /** Show a form; undefined if closed by the player, by the server, or the session ended. */
  private async show<R extends ActionFormResponse | ModalFormResponse>(s: TableSession, form: ActionFormData | ModalFormData): Promise<R | undefined> {
    const e = this.epoch(s.playerId);
    const res = (await showForm(s.player, form as ActionFormData)) as R | undefined;
    if (!res || res.canceled || this.epoch(s.playerId) !== e || !s.isActive()) return undefined;
    return res;
  }

  private onOpen(s: TableSession): void {
    if (!this.ctx.config.bool('roulette.enabled')) {
      s.player.sendMessage(t('gui.burmaldaholic.error.disabled'));
      s.leave();
      return;
    }
    this.table(s.table);
    this.reshowMain(s);
  }

  private title(rt: TableRt): Raw {
    return t(rt.highRoller ? `${K}.title_high_roller` : `${K}.title`);
  }

  private async showMain(s: TableSession, error?: Raw): Promise<void> {
    const rt = this.tables.get(s.table.key);
    if (!rt || !s.isActive()) return;
    const p = s.player;
    const round = rt.round;
    const sessions = this.seated(rt);
    const multi = sessions.length > 1;
    const bets = round.bets(p.id);
    const l = this.limitsFor(p, rt);
    const now = system.currentTick;

    const body: (Raw | undefined)[] = [];
    if (error) body.push(color('§c', error));
    if (round.canBet()) {
      body.push(color('§e', t(`${K}.place_bets`)));
      const left = round.remaining(now);
      if (left !== undefined) body.push(t('gui.burmaldaholic.common.timer', duration(left)));
      if (round.isReady(p.id) && multi) body.push(color('§7', t('gui.burmaldaholic.common.waiting_players')));
    } else if (round.phase === 'result' && round.result !== undefined) {
      body.push(color('§6', resultRaw(round.result, 0, 0)));
    } else {
      body.push(color('§6', t(round.phase === 'no_more_bets' ? `${K}.no_more_bets` : 'msg.burmaldaholic.roulette.spinning')));
    }
    if (multi) {
      const bettors = round.bettors();
      body.push(t(`${K}.players`, unit('player', sessions.length)));
      if (round.canBet() && bettors.length) body.push(t(`${K}.ready_count`, bettors.filter((id) => round.isReady(id)).length, bettors.length));
    }
    body.push(t('gui.burmaldaholic.common.balance', chips(this.ctx.economy.balance(p))));
    body.push(t('gui.burmaldaholic.common.limits', chips(l.minBet), chips(l.totalMax)));
    if (l.minTotal > 0) body.push(t(`${K}.error.min_total`, chips(l.minTotal)));
    body.push(NEWLINE);
    body.push(color('§l', t(`${K}.your_bets`)));
    if (bets.length) {
      for (const b of bets) body.push(join(lit(' • '), betLine(b)));
      body.push(t('gui.burmaldaholic.common.total_bet', chips(totalStaked(bets))));
    } else body.push(color('§7', t(`${K}.no_bets`)));
    const hist = historyRaw(round.history);
    if (hist) body.push(NEWLINE, hist);

    const form = new ActionFormData().title(this.title(rt)).body(lines(...body));
    const actions: MainAction[] = [];
    const add = (a: MainAction, label: Raw) => {
      form.button(label);
      actions.push(a);
    };
    if (round.canBet()) {
      add('add', t(`${K}.add_bet`));
      const last = this.lastBets.get(p.id);
      if (!bets.length && last?.length) add('rebet', t('gui.burmaldaholic.common.rebet'));
      if (bets.length) {
        add('clear', t(`${K}.clear_bets`));
        if (!round.isReady(p.id)) add('ready', t(multi ? 'gui.burmaldaholic.common.ready' : 'gui.burmaldaholic.common.spin'));
      }
    }
    if (!round.canBet() || round.isReady(p.id)) add('refresh', t('gui.burmaldaholic.common.ok'));
    add('leave', t('gui.burmaldaholic.common.leave'));

    const res = await this.show<ActionFormResponse>(s, form);
    if (!res || res.selection === undefined) return;
    const action = actions[res.selection];
    switch (action) {
      case 'add':
        return this.showAddBet(s);
      case 'rebet': {
        const err = this.addBets(p, rt, this.lastBets.get(p.id) ?? []);
        return this.showMain(s, err);
      }
      case 'clear':
        this.clearBets(p, rt);
        return this.showMain(s);
      case 'ready': {
        if (!round.canBet()) return this.showMain(s);
        const err = checkSpin(round.bets(p.id), this.limitsFor(p, rt));
        if (err) return this.showMain(s, slipErrorText(err));
        round.setReady(p.id);
        // Single player: the spin starts on the next tick and closes this form. Multiplayer:
        // show "waiting for other players".
        if (multi) return this.showMain(s);
        return;
      }
      case 'refresh':
        return this.showMain(s);
      case 'leave':
        s.leave();
        return;
    }
  }

  private async showAddBet(s: TableSession): Promise<void> {
    const rt = this.tables.get(s.table.key);
    if (!rt) return;
    const types: BetType[] = [];
    const form = new ActionFormData().title(t(`${K}.add_bet`));
    const rules = [t(`${K}.rules.1`)];
    if (this.ctx.config.bool('roulette.laPartage')) rules.push(t('config.burmaldaholic.roulette.laPartage.tooltip'));
    else rules.push(t(`${K}.rules.2`));
    form.body(lines(...rules));
    form.header(t(`${K}.inside`));
    for (const ty of INSIDE_TYPES) {
      form.button(betTypeLabel(ty));
      types.push(ty);
    }
    form.header(t(`${K}.outside`));
    for (const ty of OUTSIDE_TYPES) {
      form.button(betTypeLabel(ty));
      types.push(ty);
    }
    form.button(t('gui.burmaldaholic.common.back'));
    const res = await this.show<ActionFormResponse>(s, form);
    if (!res) return; // closed with X: still seated, using the table re-opens the main form
    // ActionFormResponse.selection is "the index of the button that was pushed" (server-ui
    // 2.1.0 typings): headers/labels/dividers are not counted, so `types` (buttons only) matches.
    const type = res.selection === undefined ? undefined : types[res.selection];
    if (!type) return this.showMain(s);
    return this.showBetDetails(s, type);
  }

  private async showBetDetails(s: TableSession, type: BetType, error?: Raw): Promise<void> {
    const rt = this.tables.get(s.table.key);
    if (!rt) return;
    const p = s.player;
    if (!rt.round.canBet()) return this.showMain(s, t(`${K}.no_more_bets`));
    const spots = allSpots(type);
    const l = this.limitsFor(p, rt);
    const slip = rt.round.bets(p.id);
    const balance = this.ctx.economy.balance(p);
    // slider upper bound: the most any position of this type could still take
    const room = Math.min(balance, maxAddable(slip, spots.length === 1 ? spots[0]! : ({ type, numbers: [] } as Spot), l));
    if (room < l.minBet) {
      const err = balance < l.minBet ? t('gui.burmaldaholic.error.insufficient_funds', chips(balance)) : isInside(type) && l.insideMax < l.minBet ? t(`${K}.error.inside_max`, chips(l.insideMax)) : t(`${K}.error.total_max`, chips(l.totalMax));
      return this.showMain(s, err);
    }

    const layout = new ModalLayout();
    const form = new ModalFormData().title(betTypeLabel(type));
    if (error) {
      form.label(color('§c', error));
      layout.passive();
    }
    form.label(lines(t('gui.burmaldaholic.common.balance', chips(balance)), t('gui.burmaldaholic.common.limits', chips(l.minBet), chips(room))));
    layout.passive();
    let iPos = -1;
    const posKey = `${p.id}|${type}`;
    if (spots.length > 1) {
      form.dropdown(t(`${K}.position`), spots.map(positionRaw), { defaultValueIndex: Math.min(spots.length - 1, this.lastPos.get(posKey) ?? 0) });
      iPos = layout.control();
    }
    form.slider(t('gui.burmaldaholic.common.bet'), l.minBet, Math.max(l.minBet, room), { valueStep: sliderStep(l.minBet, room), defaultValue: l.minBet });
    const iSlider = layout.control();
    form.textField(t('gui.burmaldaholic.common.exact_amount'), t('gui.burmaldaholic.common.amount'));
    const iText = layout.control();
    form.submitButton(t('gui.burmaldaholic.common.place_bet'));

    const res = await this.show<ModalFormResponse>(s, form);
    if (!res) return this.showMain(s);
    const pos = iPos >= 0 ? Number(layout.value(res, iPos) ?? 0) : 0;
    const spot = spots[pos];
    if (!spot) return this.showBetDetails(s, type, t('gui.burmaldaholic.error.invalid_bet_position'));
    this.lastPos.set(posKey, pos);
    const typed = String(layout.value(res, iText) ?? '').trim();
    const amount = typed ? parseAmount(typed) : Number(layout.value(res, iSlider));
    if (amount === undefined || !Number.isSafeInteger(amount) || amount <= 0) return this.showBetDetails(s, type, t('gui.burmaldaholic.error.invalid_amount'));
    const err = this.addBets(p, rt, [{ ...spot, amount }]);
    if (err) {
      if (!rt.round.canBet()) return this.showMain(s, err);
      return this.showBetDetails(s, type, err);
    }
    return this.showMain(s);
  }

  private async showResult(s: TableSession, rt: TableRt, result: number, o: { bets: readonly Bet[]; returns: number[]; staked: number; totalReturn: number } | undefined): Promise<void> {
    const body: (Raw | undefined)[] = [color('§l', resultRaw(result, o?.staked ?? 0, o?.totalReturn ?? 0))];
    if (o && o.staked > 0) {
      const la = this.ctx.config.bool('roulette.laPartage');
      if (result === 0 && la && o.bets.some((b) => isEvenMoney(b.type))) body.push(color('§6', t(`${K}.la_partage`)));
      body.push(NEWLINE);
      o.bets.forEach((b, i) => {
        const ret = o.returns[i] ?? 0;
        body.push(ret > 0 ? color('§a', join(betLine(b), lit(' → '), chips(ret))) : color('§7', betLine(b)));
      });
      const net = o.totalReturn - o.staked;
      body.push(NEWLINE, color(net > 0 ? '§a' : net < 0 ? '§c' : '§7', t('gui.burmaldaholic.common.result.net', formatSigned(net))));
    }
    body.push(t('gui.burmaldaholic.common.balance', chips(this.ctx.economy.balance(s.player))));
    const hist = historyRaw(rt.round.history);
    if (hist) body.push(NEWLINE, hist);
    const form = new ActionFormData()
      .title(this.title(rt))
      .body(lines(...body))
      .button(t('gui.burmaldaholic.common.play_again'))
      .button(t('gui.burmaldaholic.common.leave'));
    const res = await this.show<ActionFormResponse>(s, form);
    if (!res) return;
    if (res.selection === 1) s.leave();
    else void this.showMain(s);
  }
}

function slipErrorText(e: SlipError): Raw {
  switch (e.code) {
    case 'invalid_amount':
      return t('gui.burmaldaholic.error.invalid_amount');
    case 'invalid_position':
      return t('gui.burmaldaholic.error.invalid_bet_position');
    case 'bet_too_low':
      return t('gui.burmaldaholic.error.bet_too_low', chips(e.min));
    case 'inside_max':
      return t(`${K}.error.inside_max`, chips(e.max));
    case 'total_max':
      return t(`${K}.error.total_max`, chips(e.max));
    case 'min_total':
      return t(`${K}.error.min_total`, chips(e.min));
  }
}

function onlinePlayer(id: string): Player | undefined {
  return world.getAllPlayers().find((p) => p.id === id && p.isValid);
}

