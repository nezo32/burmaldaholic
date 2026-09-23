/**
 * Craps table runtime: shared table state per table block, the Bedrock form flow (UI.md §8),
 * money through ctx.wagers (one wager ticket per bet, odds added with raise), timers (betting
 * window, auto roll) and leave handling (GAME_DESIGN §4.1: bets stay working -> played out).
 */
import { type Player, system } from '@minecraft/server';
import { ActionFormData } from '@minecraft/server-ui';
import {
  HudPriority,
  type ModuleContext,
  type Raw,
  type LeaveReason,
  type TableSession,
  type WagerTicket,
  chips,
  color,
  dieGlyph,
  glyphRaw,
  duration,
  join,
  lines,
  mathRng,
  promptAmount,
  showForm,
  t,
} from '../../core';
import type { PointMadeListener } from './api';
import {
  type Bet,
  type BetKind,
  type BetResolution,
  type CrapsRules,
  type OddsInfo,
  type PlaceError,
  type RollEvent,
  type SeatInfo,
  CrapsTable,
  autoComplete,
  flatWorstCase,
  oddsWorstCase,
  rollDice,
  snapOdds,
} from './logic';

const K = 'gui.burmaldaholic.craps';
/** Die face glyph (core font sheet glyph_E1.png, U+E160–U+E165). */
const dieRaw = (n: number): Raw => glyphRaw(dieGlyph(n));
const M = 'msg.burmaldaholic.craps';

interface Entry {
  ticket: WagerTicket;
  player: Player;
}

interface Runtime {
  key: string;
  table: CrapsTable;
  tickets: Map<string, Entry>;
  lastRollTick: number;
  windowTimer?: number;
  rollTimer?: number;
  /** rollCount the auto-roll timer was armed for */
  rollTimerFor: number;
  rollAt: number;
}

/** Per-session view state kept in TableSession.data. */
interface View {
  /** the main table form is on screen (safe to close and re-show) */
  onTable?: boolean;
  /** re-show after a programmatic close */
  refresh?: boolean;
  /** a view loop is running */
  looping?: boolean;
}

type Deferred = { ticket: WagerTicket; totalReturn: number } | { ticket: WagerTicket; refund: true };

export class CrapsRuntime {
  private readonly tables = new Map<string, Runtime>();
  private readonly pointListeners: PointMadeListener[] = [];

  constructor(private readonly ctx: ModuleContext) {}

  onPointMade(l: PointMadeListener): void {
    this.pointListeners.push(l);
  }

  start(): void {
    this.ctx.tables.register({
      id: 'craps',
      seats: () => this.ctx.config.int('craps.seats'),
      canJoin: (p, table) => (this.ctx.config.bool('craps.enabled') ? this.ctx.wagers.check(p, 'craps', table.key) : t('gui.burmaldaholic.error.disabled')),
      onOpen: (s) => this.open(s),
      onLeave: (s, reason) => this.leave(s, reason),
    });
  }

  // ---- config ---------------------------------------------------------------------------

  private rules(): CrapsRules {
    const c = this.ctx.config;
    return {
      fieldPays2: c.int('craps.fieldPays2'),
      fieldPays12: c.int('craps.fieldPays12'),
      maxOdds4_10: c.int('craps.maxOdds4_10'),
      maxOdds5_9: c.int('craps.maxOdds5_9'),
      maxOdds6_8: c.int('craps.maxOdds6_8'),
    };
  }

  private minBet(): number {
    return this.ctx.config.int('craps.minBet');
  }

  // ---- table state ----------------------------------------------------------------------

  private runtime(key: string): Runtime {
    let rt = this.tables.get(key);
    if (!rt) {
      rt = { key, table: new CrapsTable(this.rules()), tickets: new Map(), lastRollTick: system.currentTick, rollTimerFor: -1, rollAt: 0 };
      this.tables.set(key, rt);
    }
    rt.table.rules = this.rules();
    return rt;
  }

  private sessions(rt: Runtime): TableSession[] {
    return this.ctx.tables.sessionsAt(rt.key);
  }

  private seats(rt: Runtime): SeatInfo[] {
    return this.sessions(rt).map((s) => ({ id: s.playerId, seat: s.seat, hasLineBet: rt.table.hasLineBet(s.playerId) }));
  }

  private nameOf(rt: Runtime, id: string | undefined): string {
    return this.sessions(rt).find((s) => s.playerId === id)?.player.name ?? '?';
  }

  private windowEnd(rt: Runtime): number {
    const multi = this.sessions(rt).length > 1;
    return rt.lastRollTick + (multi ? this.ctx.config.int('craps.betWindowTicks') : 0);
  }

  private clearTimers(rt: Runtime): void {
    if (rt.windowTimer !== undefined) system.clearRun(rt.windowTimer);
    if (rt.rollTimer !== undefined) system.clearRun(rt.rollTimer);
    rt.windowTimer = rt.rollTimer = undefined;
    rt.rollTimerFor = -1;
  }

  private dispose(rt: Runtime): void {
    this.clearTimers(rt);
    this.tables.delete(rt.key);
  }

  /**
   * Re-evaluate shooter and timers after anything changed (bet, join, leave, roll).
   * Returns true when something the players should see changed.
   */
  private arm(rt: Runtime): boolean {
    if (!this.sessions(rt).length) {
      this.dispose(rt);
      return false;
    }
    let changed = rt.table.ensureShooter(this.seats(rt), false);
    const wait = this.windowEnd(rt) - system.currentTick;
    if (wait > 0) {
      if (rt.windowTimer === undefined) {
        rt.windowTimer = system.runTimeout(() => {
          rt.windowTimer = undefined;
          if (this.tables.get(rt.key) !== rt) return;
          if (this.windowClosed(rt)) this.refreshViews(rt);
        }, wait);
      }
    } else if (this.windowClosed(rt)) changed = true;
    if (changed) this.announceShooterIfNew(rt);
    return changed;
  }

  private lastAnnounced = new Map<string, string | undefined>();

  private announceShooterIfNew(rt: Runtime): void {
    const sh = rt.table.shooter;
    if (this.lastAnnounced.get(rt.key) === sh || !sh) return;
    this.lastAnnounced.set(rt.key, sh);
    if (this.sessions(rt).length < 2) return;
    const name = this.nameOf(rt, sh);
    for (const s of this.sessions(rt)) s.player.sendMessage(t(`${M}.new_shooter`, name));
  }

  /** Betting window over: pass the dice if needed and arm the auto roll. */
  private windowClosed(rt: Runtime): boolean {
    let changed = rt.table.ensureShooter(this.seats(rt), true);
    if (changed) this.announceShooterIfNew(rt);
    if (rt.table.canRoll() && rt.rollTimerFor !== rt.table.rollCount) {
      if (rt.rollTimer !== undefined) system.clearRun(rt.rollTimer);
      const ticks = this.ctx.config.int('craps.rollTimerTicks');
      const forRoll = rt.table.rollCount;
      rt.rollTimerFor = forRoll;
      rt.rollAt = system.currentTick + ticks;
      rt.rollTimer = system.runTimeout(() => {
        rt.rollTimer = undefined;
        if (this.tables.get(rt.key) !== rt || rt.table.rollCount !== forRoll || !rt.table.canRoll()) return;
        if (!this.ctx.isCasinoEnabled()) return;
        for (const s of this.sessions(rt)) s.player.sendMessage(t(`${M}.auto_roll`));
        this.roll(rt);
      }, ticks);
      changed = true;
    }
    return changed;
  }

  private canShooterRollNow(rt: Runtime, playerId: string): boolean {
    return rt.table.shooter === playerId && rt.table.canRoll() && system.currentTick >= this.windowEnd(rt);
  }

  // ---- the roll ------------------------------------------------------------------------

  private roll(rt: Runtime): void {
    const seats = this.seats(rt);
    const shooterName = this.nameOf(rt, rt.table.shooter);
    const r = rt.table.roll(rollDice(mathRng), seats);
    rt.lastRollTick = system.currentTick;
    if (rt.rollTimer !== undefined) system.clearRun(rt.rollTimer);
    rt.rollTimer = undefined;
    rt.rollTimerFor = -1;

    const personal = new Map<string, Raw[]>();
    const add = (id: string, m: Raw) => {
      const list = personal.get(id) ?? [];
      list.push(m);
      personal.set(id, list);
    };
    for (const res of r.resolutions) {
      const owner = res.bet.owner;
      if (res.outcome === 'move' && res.movedTo !== undefined) {
        add(owner, t(`${M}.come_moved`, res.movedTo));
        continue;
      }
      if (res.outcome === 'stay') continue;
      const entry = rt.tickets.get(res.bet.id);
      rt.tickets.delete(res.bet.id);
      if (entry) this.ctx.wagers.settle(entry.ticket, entry.player, res.totalReturn);
      if (res.oddsReturned) add(owner, t(`${M}.odds_returned`));
      add(owner, this.resultLine(res));
    }

    const [d1, d2] = r.dice;
    const headline = t(`${M}.rolled`, shooterName, dieRaw(d1), dieRaw(d2), r.total);
    const event = this.eventLine(r.event);
    for (const s of this.sessions(rt)) {
      s.player.sendMessage(headline);
      if (event) s.player.sendMessage(event);
      for (const m of personal.get(s.playerId) ?? []) s.player.sendMessage(m);
      this.ctx.hud.actionbar(s.player, 'craps.roll', join(t(`${K}.last_roll`, dieRaw(d1), dieRaw(d2), r.total), ' · ', this.pointLine(rt)), HudPriority.game, 100);
    }

    if (r.event.kind === 'point_made' && r.shooter) {
      const sh = this.sessions(rt).find((s) => s.playerId === r.shooter)?.player;
      if (sh) for (const l of this.pointListeners) l(sh, r.pointsInRow);
    }
    if (r.sevenOut) this.lastAnnounced.delete(rt.key);
    this.arm(rt);
    this.announceShooterIfNew(rt);
    this.refreshViews(rt);
  }

  private eventLine(e: RollEvent): Raw | undefined {
    switch (e.kind) {
      case 'natural':
        return color('§a', t(`${M}.natural`, e.total));
      case 'craps':
        return e.total === 12 ? lines(color('§c', t(`${M}.craps`, e.total)), color('§7', t(`${M}.bar_12`))) : color('§c', t(`${M}.craps`, e.total));
      case 'point_set':
        return color('§e', t(`${M}.point_set`, e.point));
      case 'point_made':
        return color('§a', t(`${M}.point_made`, e.point));
      case 'seven_out':
        return color('§c', t(`${M}.seven_out`));
      case 'roll':
        return undefined;
    }
  }

  private resultLine(res: BetResolution): Raw {
    const staked = res.bet.flat + res.bet.odds;
    const net = res.totalReturn - staked;
    const label = this.betLabel(res.bet);
    if (res.bet.kind === 'field' && net > 0) return color('§a', t(`${M}.field_win`, chips(net)));
    const outcome =
      net > 0
        ? color('§a', t('gui.burmaldaholic.common.result.win', chips(net)))
        : net < 0
          ? color('§c', t('gui.burmaldaholic.common.result.loss', chips(-net)))
          : color('§7', t('gui.burmaldaholic.common.result.push'));
    return t(`${M}.bet_result`, label, outcome);
  }

  // ---- labels ---------------------------------------------------------------------------

  private kindLabel(kind: BetKind): Raw {
    return t(`${K}.${kind}`);
  }

  private betLabel(b: Bet): Raw {
    if (b.kind === 'come' && b.point !== undefined) return t(`${K}.come_point`, b.point);
    if (b.kind === 'dont_come' && b.point !== undefined) return t(`${K}.dont_come_point`, b.point);
    return this.kindLabel(b.kind);
  }

  private pointLine(rt: Runtime): Raw {
    return rt.table.point === undefined ? t(`${K}.point_off`) : t(`${K}.point_on`, rt.table.point);
  }

  private placeErrorText(e: PlaceError): Raw {
    return e === 'line_only_come_out' ? t(`${K}.line_only_come_out`) : t('gui.burmaldaholic.error.invalid_bet_position');
  }

  // ---- views ----------------------------------------------------------------------------

  private view(s: TableSession): View {
    return s.data as View;
  }

  /** Close and re-show the main table form of everyone looking at it. */
  private refreshViews(rt: Runtime): void {
    for (const s of this.sessions(rt)) {
      const v = this.view(s);
      if (!v.onTable) continue;
      v.refresh = true;
      s.closeForms();
    }
  }

  private open(s: TableSession): void {
    const rt = this.runtime(s.table.key);
    if (this.arm(rt)) this.refreshViews(rt);
    void this.loop(s);
  }

  private async loop(s: TableSession): Promise<void> {
    const v = this.view(s);
    if (v.looping) return;
    v.looping = true;
    try {
      while (s.isActive() && this.ctx.isCasinoEnabled()) {
        if (!(await this.showTable(s))) break;
      }
    } finally {
      v.looping = false;
      v.onTable = false;
    }
  }

  /** One table form; returns false when the player closed it (stays seated, bets keep working). */
  private async showTable(s: TableSession): Promise<boolean> {
    const rt = this.tables.get(s.table.key);
    if (!rt) return false;
    const p = s.player;
    const me = s.playerId;
    const table = rt.table;
    const now = system.currentTick;

    const body: (Raw | undefined)[] = [this.pointLine(rt)];
    if (table.shooter === me) body.push(color('§e', t(`${K}.you_shoot`)));
    else if (table.shooter) body.push(t(`${K}.shooter`, this.nameOf(rt, table.shooter)));
    if (table.lastRoll) body.push(t(`${K}.last_roll`, dieRaw(table.lastRoll[0]), dieRaw(table.lastRoll[1]), table.lastRoll[0] + table.lastRoll[1]));
    if (!table.canRoll()) {
      if (table.comeOut) body.push(color('§7', t(`${K}.need_line_bet`)));
    } else if (now < this.windowEnd(rt)) {
      body.push(color('§7', t(`${K}.bet_window`, duration(this.windowEnd(rt) - now))));
    } else {
      if (table.shooter !== me) body.push(color('§7', t(`${K}.waiting_shooter`, this.nameOf(rt, table.shooter))));
      if (rt.rollTimer !== undefined) body.push(color('§7', t(`${K}.auto_roll_in`, duration(Math.max(0, rt.rollAt - now)))));
    }
    body.push(undefined);
    body.push(t(`${K}.your_bets`));
    const mine = table.betsOf(me);
    if (!mine.length) body.push(color('§7', t(`${K}.no_bets`)));
    for (const b of mine) {
      const line = b.odds > 0 ? t(`${K}.bet_with_odds`, this.betLabel(b), chips(b.flat), chips(b.odds)) : t(`${K}.bet_flat`, this.betLabel(b), chips(b.flat));
      const off = b.kind === 'come' && b.odds > 0 && b.point !== undefined && table.comeOut;
      body.push(off ? join(line, ' ', color('§7', t(`${K}.odds_off`))) : line);
    }
    const range = this.ctx.limits.range(p, { min: this.minBet() });
    body.push(t('gui.burmaldaholic.common.balance', chips(this.ctx.economy.balance(p))));
    body.push(color('§7', t('gui.burmaldaholic.common.limits', chips(range.min), chips(range.max))));

    const form = new ActionFormData().title(t(`${K}.title`)).body(lines(...body.map((x) => x ?? join(''))));
    const actions: (() => Promise<boolean> | boolean)[] = [];
    const button = (label: Raw, fn: () => Promise<boolean> | boolean) => {
      form.button(label);
      actions.push(fn);
    };
    if (this.canShooterRollNow(rt, me))
      button(color('§2', t(`${K}.roll`)), () => {
        if (this.canShooterRollNow(rt, me) && this.tables.get(rt.key) === rt) this.roll(rt);
        return true;
      });
    for (const kind of ['pass', 'dont_pass', 'come', 'dont_come', 'field'] as const) {
      if (table.placeError(me, kind) === undefined) button(this.kindLabel(kind), () => this.placeBet(s, kind));
    }
    if (table.oddsTargets(me).length) button(t(`${K}.odds`), () => this.placeOdds(s));
    button(t('gui.burmaldaholic.common.rules'), () => this.showRules(s));
    button(t('gui.burmaldaholic.common.leave'), () => {
      s.leave();
      return false;
    });

    const v = this.view(s);
    v.onTable = true;
    v.refresh = false;
    const res = await showForm(p, form);
    v.onTable = false;
    if (!res || res.canceled || res.selection === undefined) {
      if (v.refresh) {
        v.refresh = false;
        return s.isActive();
      }
      return false;
    }
    const fn = actions[res.selection];
    if (!fn || !s.isActive() || !this.ctx.isCasinoEnabled()) return false;
    return await fn();
  }

  private async showRules(s: TableSession): Promise<boolean> {
    const form = new ActionFormData()
      .title(t(`${K}.title`))
      .body(lines(t(`${K}.rules.1`), t(`${K}.rules.2`), t(`${K}.rules.3`)))
      .button(t('gui.burmaldaholic.common.back'));
    await showForm(s.player, form);
    return s.isActive();
  }

  // ---- bets -----------------------------------------------------------------------------

  private async placeBet(s: TableSession, kind: BetKind): Promise<boolean> {
    const p = s.player;
    const min = this.minBet();
    const range = this.ctx.limits.range(p, { min });
    if (range.max < range.min) {
      p.sendMessage(this.ctx.limits.check(p, range.min, { min }) ?? t('gui.burmaldaholic.error.invalid_amount'));
      return true;
    }
    const amount = await promptAmount(p, {
      title: this.kindLabel(kind),
      info: [t('gui.burmaldaholic.common.balance', chips(this.ctx.economy.balance(p)))],
      min: range.min,
      max: range.max,
      validate: (a) => this.ctx.limits.check(p, a, { min }, this.ctx.economy.balance(p)),
    });
    if (amount === undefined || !s.isActive() || !this.ctx.isCasinoEnabled()) return s.isActive();
    const rt = this.tables.get(s.table.key);
    if (!rt) return false;
    // The table may have rolled while the amount form was open: re-check.
    const err = rt.table.placeError(s.playerId, kind);
    if (err) {
      p.sendMessage(this.placeErrorText(err));
      return true;
    }
    const r = this.ctx.wagers.place(p, {
      game: 'craps',
      stake: { kind: 'chips', amount },
      limits: { min },
      tableKey: s.table.key,
      worstCase: flatWorstCase(kind, amount, rt.table.rules),
    });
    if (!r.ok) return true;
    const bet = rt.table.addBet(s.playerId, kind, amount);
    r.ticket.data = bet.id;
    rt.tickets.set(bet.id, { ticket: r.ticket, player: p });
    if (this.arm(rt)) this.refreshViews(rt);
    return true;
  }

  private async placeOdds(s: TableSession): Promise<boolean> {
    const p = s.player;
    let rt = this.tables.get(s.table.key);
    if (!rt) return false;
    const targets = rt.table.oddsTargets(s.playerId);
    let target: OddsInfo | undefined = targets[0];
    if (targets.length > 1) {
      const form = new ActionFormData().title(t(`${K}.odds`)).body(t(`${K}.choose_odds`));
      for (const x of targets) form.button(this.betLabel(x.bet));
      const res = await showForm(p, form);
      if (!res || res.canceled || res.selection === undefined) return s.isActive();
      target = targets[res.selection];
    }
    if (!target) return true;
    const { unit } = target;
    const info = [t(`${K}.odds_multiple`, chips(unit)), t(`${K}.odds_max`, chips(target.max))];
    if (target.off) info.push(color('§7', t(`${K}.odds_off`)));
    const raw = await promptAmount(p, {
      title: t(`${K}.odds_on`, this.betLabel(target.bet)),
      info,
      min: unit,
      max: target.room,
      default: target.room,
      validate: (a) => (snapOdds(a, unit) < unit ? t(`${K}.odds_multiple`, chips(unit)) : undefined),
    });
    if (raw === undefined || !s.isActive() || !this.ctx.isCasinoEnabled()) return s.isActive();
    rt = this.tables.get(s.table.key);
    if (!rt) return false;
    const bet = rt.table.bet(target.bet.id);
    const entry = rt.tickets.get(target.bet.id);
    if (!bet || !entry) return true;
    const amount = snapOdds(raw, unit);
    const err = rt.table.oddsError(bet, amount);
    if (err) {
      p.sendMessage(err.code === 'multiple' ? t(`${K}.odds_multiple`, chips(err.unit)) : err.code === 'max' ? t(`${K}.odds_max`, chips(err.max)) : t('gui.burmaldaholic.error.invalid_bet_position'));
      return true;
    }
    const now = rt.table.oddsInfo(bet)!;
    // Odds pay true odds: 0 % house edge (VIP cashback base).
    if (!this.ctx.wagers.raise(entry.ticket, p, amount, oddsWorstCase(now.side, now.point, amount), 0)) return true;
    rt.table.addOdds(bet.id, amount);
    return true;
  }

  // ---- leaving --------------------------------------------------------------------------

  private leave(s: TableSession, reason: LeaveReason): void {
    this.view(s).onTable = false;
    const rt = this.tables.get(s.table.key);
    if (!rt) return;
    const point = rt.table.point;
    const bets = rt.table.removeOwner(s.playerId);
    const entries = bets.map((b) => ({ bet: b, entry: rt.tickets.get(b.id) }));
    for (const b of bets) rt.tickets.delete(b.id);

    if (entries.length) {
      const player = s.player;
      if (reason === 'broken' || reason === 'casino_off') {
        // Not the player's doing: give everything back.
        const list: Deferred[] = entries.filter((x) => x.entry).map((x) => ({ ticket: x.entry!.ticket, refund: true as const }));
        this.applyDeferred(s.playerId, player, list);
      } else {
        // GAME_DESIGN §4.1: craps bets stay working until resolved -> play them out.
        const results = autoComplete(point, bets, mathRng, rt.table.rules);
        const list: Deferred[] = entries.filter((x) => x.entry).map((x) => ({ ticket: x.entry!.ticket, totalReturn: results.get(x.bet.id) ?? x.bet.flat + x.bet.odds }));
        this.applyDeferred(s.playerId, player, list);
      }
    }

    if (!this.sessions(rt).length) {
      this.dispose(rt);
      this.lastAnnounced.delete(rt.key);
      return;
    }
    if (this.arm(rt)) this.refreshViews(rt);
  }

  /** Settle / refund a leaver's bets. Offline-safe: core parks results for a disconnected player. */
  private applyDeferred(playerId: string, player: Player, list: Deferred[]): void {
    let net = 0;
    let played = false;
    let refunded = false;
    for (const d of list) {
      if ('refund' in d) {
        this.ctx.wagers.refund(d.ticket, player);
        refunded = true;
      } else {
        const staked = d.ticket.value;
        const ev = this.ctx.wagers.settle(d.ticket, player, d.totalReturn);
        if (ev) {
          net += d.totalReturn - staked;
          played = true;
        }
      }
    }
    if (refunded) this.ctx.wagers.tell(playerId, t(`${M}.bets_refunded`));
    if (played) {
      const res = net > 0 ? color('§a', t('gui.burmaldaholic.common.result.win', chips(net))) : net < 0 ? color('§c', t('gui.burmaldaholic.common.result.loss', chips(-net))) : color('§7', t('gui.burmaldaholic.common.result.push'));
      this.ctx.wagers.tell(playerId, t(`${M}.bets_played_out`, res));
    }
  }
}
