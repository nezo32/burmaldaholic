/**
 * Baccarat runtime (GAME_DESIGN §20, UI.md §14): one shared coup per table for up to
 * `baccarat.seats` players, one 8-deck shoe per table (persisted), Bedrock forms and the
 * action-bar reveal.
 *
 *  - House coups (Punto Banco): one chip ticket per bettor per coup (place() for the first bet,
 *    raise() for the next), `wagers.draw` for every bettor at DEAL before the reveal animates
 *    (§4.1), `wagers.settle` once with the total return. Owned tables are banked by the owner's
 *    bankroll through core's house resolver (per-ticket worst case over the 12 classes).
 *  - Chemin de fer (§20.9, variant `player_banked`): ./chemmy.ts drives the bank; money moves
 *    with economy.transact through the bank sink (escrow), saved in ./store.ts on every change.
 *
 * Leave / disconnect / break / restart follow §20.5 (see onLeave, breakTable, closeAll).
 */
import { type Player, system, world } from '@minecraft/server';
import { ActionFormData, type ActionFormResponse, ModalFormData, type ModalFormResponse } from '@minecraft/server-ui';
import {
  HudPriority,
  type LeavePolicy,
  type LeaveReason,
  type ModuleContext,
  ModalLayout,
  NEWLINE,
  type Raw,
  type TableRef,
  type TableSession,
  type WagerTicket,
  chips,
  color,
  detach,
  duration,
  formatSigned,
  isAchievement,
  join,
  joinWith,
  lines,
  lit,
  mathRng,
  onlinePlayer,
  parseAmount,
  plural,
  showForm,
  sliderStep,
  t,
} from '../../core';
import { tableKeyOf } from '../../core/logic/sessions';
import { CHAOS_SERVICE, type ChaosApi } from '../../chaos/api';
import { type TablePreset, WORLDGEN_SERVICE, type WorldgenApi } from '../../worldgen/api';
import {
  BACCARAT_BLOCKS,
  BACCARAT_DEALER_ENTITY,
  BACCARAT_HIGH_ROLLER_TAG,
  type BaccaratApi,
  type BaccaratCoupEntry,
  type BaccaratCoupEvent,
} from './api';
import { ChemmyController } from './chemmy';
import {
  BaccaratShoe,
  BeadPlate,
  type Box,
  type Coup,
  CoupClock,
  type PayRules,
  type RevealFrame,
  type SeatOccupant,
  type Slip,
  type SlipLimits,
  type SlipResult,
  addToSlip,
  boxEdge,
  boxMin,
  checkAdd,
  checkSlip,
  dealCoup,
  dealOrder,
  fitSlip,
  maxAddable,
  revealFrames,
  settleSlip,
  slipBoxes,
  slipLimits,
  slipTotal,
  snapToStep,
  tieRunFires,
  winningNatural,
  worstCaseReturn,
} from './logic';
import { GAME, HIGH_ROLLER, HUD_CHANNEL, PLAYER_BANKED } from './shared';
import { pendingAchievements, tableStore } from './store';
import { K, actionbarRaw, beadsRaw, betLine, betResultLine, boxButton, boxName, coupRaw, pairsRaw, resultRaw, slipErrorText } from './text';

const NO_MORE_BETS_TICKS = 20;
const SHUFFLE_TICKS = 40;
const RESULT_TICKS = 60;
const HISTORY_SHOWN = 12;
const READY_MARK = lit('§a✔§r ');
const NO_MARK = lit('');

export type TableKind = 'standard' | 'high_roller' | 'chemmy';

/** Per-player result of the last coup (result form). */
export interface SeatResult {
  slip: Slip;
  res: SlipResult;
}

export interface TableRt {
  readonly key: string;
  ref: TableRef;
  readonly kind: TableKind;
  readonly clock: CoupClock;
  shoe: BaccaratShoe;
  plate: BeadPlate;
  /** house coup: slips and tickets by player id */
  readonly slips: Map<string, Slip>;
  readonly tickets: Map<string, WagerTicket>;
  readonly names: Map<string, string>;
  /** the coup being revealed / settled */
  coup?: Coup;
  /** last settled coup (table body) */
  last?: Coup;
  frames?: RevealFrame[];
  revealStart: number;
  shownFrame: number;
  /** mode fixed at NO_MORE_BETS for the running coup */
  mode: 'house' | 'chemmy';
  lastTieWinners: Set<string>;
  broken: boolean;
  /** players who asked for "Same bets again" before betting reopened */
  readonly queuedRebet: Set<string>;
  readonly results: Map<string, SeatResult>;
  /** shuffle announcement pending for the reveal line */
  burned?: number;
  chem?: ChemmyController;
}

type MainAction = Box | 'clear' | 'rebet' | 'ready' | 'rules' | 'refresh' | 'leave';

export class BaccaratGame implements BaccaratApi {
  readonly tables = new Map<string, TableRt>();
  /** last coup's slip per player (Rebet) */
  private readonly lastSlips = new Map<string, Slip>();
  /** form generation per player: bumped when the server closes forms */
  private readonly epochs = new Map<string, number>();
  private readonly listeners: ((e: BaccaratCoupEvent) => void)[] = [];

  constructor(readonly ctx: ModuleContext) {}

  // ---- BaccaratApi ----------------------------------------------------------------------

  onCoup(listener: (e: BaccaratCoupEvent) => void): void {
    this.listeners.push(listener);
  }

  activeTables(): number {
    return [...this.tables.values()].filter((x) => !x.clock.canBet()).length;
  }

  emit(e: BaccaratCoupEvent): void {
    for (const l of this.listeners) {
      try {
        l(e);
      } catch (err) {
        this.ctx.log.error('onCoup listener failed', err);
      }
    }
  }

  // ---- lifecycle ------------------------------------------------------------------------

  start(): void {
    const ctx = this.ctx;
    ctx.tables.register({
      id: 'baccarat',
      seats: () => ctx.config.int('baccarat.seats'),
      canJoin: (p, table) => this.canJoin(p, table),
      onOpen: (s, rejoined) => this.onOpen(s, rejoined),
      onLeave: (s, reason, policy) => this.onLeave(s, reason, policy),
    });
    // Restart: settle / refund chemin de fer escrows and return every bank (§20.9).
    ChemmyController.recoverAll(this);
    this.flushPendingAchievements();
    system.runInterval(() => {
      try {
        this.tick(ctx.isCasinoEnabled());
      } catch (e) {
        ctx.log.error('baccarat tick failed', e);
      }
    }, 1);

    // Dealer NPC: the entity itself hosts a table (§20.6, Bedrock).
    world.afterEvents.playerInteractWithEntity.subscribe(
      ctx.guard((e) => {
        const npc = e.target;
        if (npc.typeId !== BACCARAT_DEALER_ENTITY) return;
        ctx.tables.open(e.player, {
          key: `npc:${npc.id}`,
          game: 'baccarat',
          variant: npc.hasTag(BACCARAT_HIGH_ROLLER_TAG) ? HIGH_ROLLER : undefined,
          dimension: npc.dimension,
          location: npc.location,
        });
      }),
    );
    // Table break (§20.5): the dealer NPC removed or the table block broken (also when nobody
    // sits there). While casino mode is off every table was already closed out (closeAll).
    world.afterEvents.entityRemove.subscribe(
      ctx.guard((e) => {
        if (e.typeId === BACCARAT_DEALER_ENTITY) this.breakTable(`npc:${e.removedEntityId}`);
      }),
    );
    world.afterEvents.playerBreakBlock.subscribe(
      ctx.guard((e) => {
        const id = e.brokenBlockPermutation.type.id;
        if ((BACCARAT_BLOCKS as readonly string[]).includes(id)) this.breakTable(tableKeyOf(e.dimension.id, e.block.location));
      }),
    );
  }

  // ---- config / table lookup ------------------------------------------------------------

  rules(): PayRules {
    const c = this.ctx.config;
    return { commission: c.num('baccarat.bankerCommission'), tiePays: c.int('baccarat.tiePays'), pairPays: c.int('baccarat.pairPays') };
  }

  preset(ref: TableRef): TablePreset | undefined {
    try {
      return this.ctx.services.get<WorldgenApi>(WORLDGEN_SERVICE)?.tablePreset(ref.dimension.id, ref.location);
    } catch {
      return undefined;
    }
  }

  kindOf(ref: TableRef): TableKind {
    if (ref.variant === PLAYER_BANKED) return 'chemmy';
    if (ref.variant === HIGH_ROLLER) return 'high_roller';
    const p = this.preset(ref);
    // Worldgen hook: a High-Roller preset (id `high_roller_baccarat…`, or VIP / multiplier terms).
    if (p && (p.id.startsWith('high_roller_baccarat') || (p.minTier ?? 0) > 0 || (p.tierMultiplier ?? 1) > 1)) return 'high_roller';
    return 'standard';
  }

  private minTier(ref: TableRef): number {
    return this.preset(ref)?.minTier ?? this.ctx.config.int('baccarat.highRollerMinVipTier');
  }

  table(ref: TableRef): TableRt {
    let rt = this.tables.get(ref.key);
    if (rt) {
      rt.ref = ref;
      return rt;
    }
    const c = this.ctx.config;
    const stored = tableStore.get(ref.key);
    const decks = c.int('baccarat.decks');
    const cap = c.int('baccarat.historyLength');
    const kind = this.kindOf(ref);
    rt = {
      key: ref.key,
      ref,
      kind,
      clock: new CoupClock(this.timings()),
      shoe: BaccaratShoe.fromData(stored?.shoe, decks),
      plate: BeadPlate.fromData(stored?.hist, cap),
      slips: new Map(),
      tickets: new Map(),
      names: new Map(),
      revealStart: 0,
      shownFrame: -1,
      mode: 'house',
      lastTieWinners: new Set(),
      broken: false,
      queuedRebet: new Set(),
      results: new Map(),
    };
    if (kind === 'chemmy') rt.chem = new ChemmyController(this, rt);
    this.tables.set(ref.key, rt);
    return rt;
  }

  timings() {
    const c = this.ctx.config;
    return { betTicks: c.int('baccarat.betTimerTicks'), noMoreBetsTicks: NO_MORE_BETS_TICKS, shuffleTicks: SHUFFLE_TICKS, revealTicks: c.int('baccarat.revealTicks'), resultTicks: RESULT_TICKS };
  }

  /** Occupants in seat order (humans today; bots plug in here later). */
  occupants(rt: TableRt): SeatOccupant[] {
    return this.ctx.tables
      .sessionsAt(rt.key)
      .filter((s) => s.player.isValid)
      .map((s) => ({ id: s.playerId, name: s.player.name, kind: 'human' as const, seat: s.seat }))
      .sort((a, b) => a.seat - b.seat);
  }

  sessions(rt: TableRt): TableSession[] {
    return this.ctx.tables.sessionsAt(rt.key).filter((s) => s.player.isValid);
  }

  session(rt: TableRt, id: string): TableSession | undefined {
    return this.sessions(rt).find((s) => s.playerId === id);
  }

  /** Seated players and spectators within multiplayer.spectatorRadius (§18.1). */
  nearby(rt: TableRt): Player[] {
    const out = new Map<string, Player>();
    for (const s of this.sessions(rt)) out.set(s.playerId, s.player);
    try {
      const r = this.ctx.config.int('multiplayer.spectatorRadius');
      if (r > 0) for (const p of rt.ref.dimension.getPlayers({ location: rt.ref.location, maxDistance: r })) out.set(p.id, p);
    } catch {
      /* dimension unloaded */
    }
    return [...out.values()];
  }

  tell(rt: TableRt, msg: Raw, exceptId?: string): void {
    for (const p of this.nearby(rt)) if (p.id !== exceptId) p.sendMessage(msg);
  }

  // ---- limits ---------------------------------------------------------------------------

  /** House-coup limits of a player at a table (§20.4, owner min/max, worldgen preset). */
  limitsFor(p: Player, rt: TableRt): SlipLimits {
    const c = this.ctx.config;
    const preset = this.preset(rt.ref);
    const hr = rt.kind === 'high_roller';
    const mult = hr ? (preset?.tierMultiplier ?? c.num('baccarat.highRollerMaxMultiplier')) : (preset?.tierMultiplier ?? 1);
    const baseMin = c.int('baccarat.minBet');
    const own = this.ctx.wagers.limitsFor(p, GAME, { min: baseMin }, rt.key);
    const tierMax = this.ctx.limits.tierMax(p, mult);
    // Owned tables: the owner's min and max are per coup (§20.6); every bet still ≥ minBet.
    const ownerMin = own.min !== undefined && own.min > baseMin ? own.min : 0;
    return slipLimits({
      max: own.tableMax === undefined ? tierMax : Math.min(tierMax, own.tableMax),
      minBet: baseMin,
      commission: c.num('baccarat.bankerCommission'),
      sideMaxFraction: c.num('baccarat.sideMaxFraction'),
      minTotal: Math.max(ownerMin, hr ? (preset?.minBet ?? c.int('baccarat.highRollerMinTotal')) : 0),
      pairs: c.bool('baccarat.pairBets'),
    });
  }

  /** Min and max of one player's chemin de fer stake / bank coverage. */
  pvpLimits(p: Player, rt: TableRt): { min: number; max: number } {
    const own = this.ctx.wagers.limitsFor(p, GAME, { min: this.ctx.config.int('baccarat.minBet') }, rt.key);
    const tierMax = this.ctx.limits.tierMax(p, 1);
    return { min: Math.max(1, own.min ?? 1), max: own.tableMax === undefined ? tierMax : Math.min(tierMax, own.tableMax) };
  }

  private canJoin(p: Player, table: TableRef): Raw | undefined {
    const c = this.ctx.config;
    if (!c.bool('baccarat.enabled')) return t('gui.burmaldaholic.error.disabled');
    const kind = this.kindOf(table);
    if (kind === 'chemmy' && !c.bool('baccarat.chemmy.enabled')) return t('gui.burmaldaholic.error.disabled');
    if (kind === 'high_roller') {
      const tier = this.minTier(table);
      if (this.ctx.limits.tier(p) < tier) return t('gui.burmaldaholic.error.vip_required', this.ctx.limits.tierName(tier));
    }
    // Owner can't play at their own table, closed / broke casino, Asset Freeze.
    return this.ctx.wagers.check(p, GAME, table.key);
  }

  /** The loan rule of PvP coups (§20.9, §5.8). */
  owes(p: Player): boolean {
    try {
      return this.ctx.economy.owed(p) > 0 || this.ctx.economy.inDefault(p);
    } catch {
      return false;
    }
  }

  // ---- house bets ----------------------------------------------------------------------

  /** A house coup takes bets now (Punto Banco table, or a chemin de fer table's house coup). */
  houseBetting(rt: TableRt): boolean {
    return rt.clock.canBet() && (!rt.chem || rt.chem.isHouseCoup());
  }

  /** Validate, pay and add a bet. Returns an error text or undefined. */
  addBet(p: Player, rt: TableRt, box: Box, amount: number): Raw | undefined {
    if (!this.ctx.isCasinoEnabled()) return t('gui.burmaldaholic.error.casino_off');
    if (!this.houseBetting(rt)) return t(`${K}.no_more_bets`);
    const l = this.limitsFor(p, rt);
    const slip = rt.slips.get(p.id) ?? {};
    const err = checkAdd(slip, box, amount, l);
    if (err) return slipErrorText(err);
    const balance = this.ctx.economy.balance(p);
    if (amount > balance) return t('gui.burmaldaholic.error.insufficient_funds', chips(balance));
    const r = this.rules();
    const after = addToSlip(slip, box, amount);
    const wcAfter = worstCaseReturn(after, r);
    const edge = boxEdge(box, r);
    const ticket = rt.tickets.get(p.id);
    if (!ticket) {
      const res = this.ctx.wagers.place(p, { game: GAME, stake: { kind: 'chips', amount }, skipLimits: true, tableKey: rt.key, worstCase: wcAfter, notify: false, houseEdge: edge });
      if (!res.ok) return res.error;
      rt.tickets.set(p.id, res.ticket);
    } else if (!this.ctx.wagers.raise(ticket, p, amount, Math.max(0, wcAfter - worstCaseReturn(slip, r)), edge)) {
      return t('gui.burmaldaholic.error.insufficient_funds', chips(this.ctx.economy.balance(p)));
    }
    rt.slips.set(p.id, after);
    rt.names.set(p.id, p.name);
    rt.clock.onBet(system.currentTick);
    rt.clock.unready(p.id);
    try {
      p.playSound('random.click', { volume: 0.6, pitch: 1.4 });
    } catch {
      /* sound missing */
    }
    return undefined;
  }

  /** Clear own bets during betting: full refund, no streak effect. */
  clearBets(id: string, rt: TableRt, player?: Player): number {
    const ticket = rt.tickets.get(id);
    const slip = rt.slips.get(id);
    rt.tickets.delete(id);
    rt.slips.delete(id);
    rt.clock.unready(id);
    if (ticket) this.ctx.wagers.refund(ticket, player ?? onlinePlayer(id));
    return slip ? slipTotal(slip) : 0;
  }

  /** Rebet: last coup's bets fitted into the current limits. */
  rebet(p: Player, rt: TableRt): Raw | undefined {
    const last = this.lastSlips.get(p.id);
    if (!last) return undefined;
    const fitted = fitSlip(last, this.limitsFor(p, rt));
    if (!slipTotal(fitted)) return slipErrorText({ code: 'total_max', max: this.limitsFor(p, rt).max });
    for (const b of slipBoxes(fitted)) {
      const err = this.addBet(p, rt, b, fitted[b]!);
      if (err) return err;
    }
    return undefined;
  }

  // ---- the shared table loop -------------------------------------------------------------

  private tick(on: boolean): void {
    const now = system.currentTick;
    for (const rt of [...this.tables.values()]) {
      try {
        if (!on) {
          this.closeAll(rt);
          continue;
        }
        rt.chem?.tick(now);
        this.tickCoup(rt, now);
      } catch (e) {
        this.ctx.log.error(`baccarat table ${rt.key} failed`, e);
      }
    }
  }

  private tickCoup(rt: TableRt, now: number): void {
    const chem = rt.chem;
    const running = !rt.clock.canBet();
    if (!running && chem && !chem.isChemmyCoup() && !chem.isHouseCoup()) return; // bank offer / waiting
    const chemCoup = running ? rt.mode === 'chemmy' : !!chem?.isChemmyCoup();
    const bettors = chemCoup && chem ? chem.bettorIds() : [...rt.slips.keys()];
    if (!running && !bettors.length) {
      if (!chem) this.maybeDrop(rt);
      return;
    }
    this.animate(rt, now);
    const seated = this.sessions(rt).map((s) => s.playerId);
    const pen = this.ctx.config.num('baccarat.penetration');
    const tr = rt.clock.update(now, bettors, seated, () => rt.shoe.needsShuffle(pen));
    if (!tr) {
      this.countdown(rt, now);
      return;
    }
    switch (tr.to) {
      case 'no_more_bets':
        rt.mode = chemCoup ? 'chemmy' : 'house';
        this.onNoMoreBets(rt);
        break;
      case 'shuffle':
        this.shuffle(rt);
        break;
      case 'reveal':
        this.deal(rt, now);
        break;
      case 'result':
        this.result(rt);
        break;
      case 'betting':
        this.nextCoup(rt);
        break;
    }
  }

  /** Forget an idle table nobody sits at (the shoe stays persisted). */
  private maybeDrop(rt: TableRt): void {
    if (this.sessions(rt).length || rt.slips.size || rt.chem?.holdsChips()) return;
    this.tables.delete(rt.key);
  }

  private onNoMoreBets(rt: TableRt): void {
    if (rt.mode === 'house') {
      // High Roller: bettors below the per-coup minimum are refunded (§20.4).
      for (const [id, slip] of [...rt.slips]) {
        const p = onlinePlayer(id);
        const l = p ? this.limitsFor(p, rt) : undefined;
        if (l && l.minTotal > 0 && slipTotal(slip) < l.minTotal) {
          const back = this.clearBets(id, rt, p);
          this.ctx.wagers.tell(id, t(`${K}.error.min_total`, chips(l.minTotal)));
          this.ctx.wagers.tell(id, t('msg.burmaldaholic.baccarat.bets_refunded', chips(back)));
        }
      }
      for (const [id, slip] of rt.slips) this.lastSlips.set(id, { ...slip });
    }
    for (const s of this.sessions(rt)) {
      this.closeFor(s);
      this.ctx.hud.actionbar(s.player, HUD_CHANNEL, t(`${K}.no_more_bets`), HudPriority.game, 40);
    }
  }

  private shuffle(rt: TableRt): void {
    const r = rt.shoe.shuffle(mathRng, this.ctx.config.bool('baccarat.burnCards'));
    rt.plate.clear();
    rt.burned = r.burned;
    this.persist(rt);
    const msg = r.burned > 0 ? join(t('msg.burmaldaholic.baccarat.new_shoe'), lit(' §7('), plural(`${K}.burned`, r.burned), lit(')')) : t(`${K}.shuffling`);
    this.tell(rt, msg);
  }

  /** DEAL (§20.5): draw the complete coup and persist it with every stake BEFORE the reveal. */
  private deal(rt: TableRt, now: number): void {
    // The clock passes through SHUFFLE when due; this only guards a shoe changed by config.
    if (rt.shoe.remaining < 6 || rt.shoe.isNew) this.shuffle(rt);
    const coup = dealCoup(() => rt.shoe.draw());
    rt.coup = coup;
    if (rt.mode === 'chemmy') rt.chem?.onDealt(coup);
    this.persist(rt);
    if (rt.mode === 'house') {
      const r = this.rules();
      for (const [id, ticket] of rt.tickets) this.ctx.wagers.draw(ticket, settleSlip(rt.slips.get(id) ?? {}, coup, r).totalReturn);
    }
    rt.frames = revealFrames(coup.player.length, coup.banker.length, coup.natural, this.ctx.config.int('baccarat.revealTicks'));
    rt.revealStart = now;
    rt.shownFrame = -1;
  }

  private animate(rt: TableRt, now: number): void {
    const f = rt.frames;
    const coup = rt.coup;
    if (!f || !coup || rt.clock.phase !== 'reveal') return;
    let i = rt.shownFrame;
    while (i + 1 < f.length && f[i + 1]!.at <= now - rt.revealStart) i++;
    if (i === rt.shownFrame || i < 0) return;
    rt.shownFrame = i;
    const fr = f[i]!;
    const note = fr.note === 'natural' ? t(`${K}.natural`, Math.max(coup.playerTotal, coup.bankerTotal)) : fr.note === 'player_stands' ? t(`${K}.player_stands`, coup.playerTotal) : fr.note === 'banker_stands' ? t(`${K}.banker_stands`, coup.bankerTotal) : fr.note ? t(`${K}.${fr.note}`) : undefined;
    const bar = note ? join(actionbarRaw(coup, fr.player, fr.banker), lit(' §7— §e'), note) : actionbarRaw(coup, fr.player, fr.banker);
    for (const p of this.nearby(rt)) this.ctx.hud.actionbar(p, HUD_CHANNEL, bar, HudPriority.game, 40);
  }

  /** Once a second while the bet timer runs: "Bets close in 12 seconds". */
  private countdown(rt: TableRt, now: number): void {
    if (!rt.clock.canBet()) return;
    const left = rt.clock.remaining(now);
    if (left === undefined || left % 20 !== 0) return;
    for (const s of this.sessions(rt)) this.ctx.hud.actionbar(s.player, HUD_CHANNEL, t(`${K}.bets_close_in`, duration(left)), HudPriority.game, 25);
  }

  private result(rt: TableRt): void {
    const coup = rt.coup;
    if (!coup) return;
    const tieRun = rt.plate.record(coup);
    rt.results.clear();
    const ev: BaccaratCoupEvent =
      rt.mode === 'house' ? this.settleHouse(rt, coup, tieRun) : (rt.chem?.settle(coup) ?? this.emptyEvent(rt, coup, 'chemmy'));
    this.persist(rt);
    const summary = join(coupRaw(coup), lit('  '), resultRaw(coup));
    for (const p of this.nearby(rt)) this.ctx.hud.actionbar(p, HUD_CHANNEL, summary, HudPriority.game, RESULT_TICKS);
    for (const s of this.sessions(rt)) {
      this.closeFor(s);
      detach(this.showResult(s, rt, coup), (e) => this.ctx.log.error('baccarat result form', e));
    }
    this.emit(ev);
  }

  emptyEvent(rt: TableRt, coup: Coup, mode: 'house' | 'chemmy'): BaccaratCoupEvent {
    return {
      table: rt.key,
      mode,
      winner: coup.winner,
      playerTotal: coup.playerTotal,
      bankerTotal: coup.bankerTotal,
      playerPair: coup.playerPair,
      bankerPair: coup.bankerPair,
      cards: dealOrder(coup).map((c) => `${c.rank}${c.suit}`),
      entries: [],
    };
  }

  private settleHouse(rt: TableRt, coup: Coup, tieRun: number): BaccaratCoupEvent {
    const r = this.rules();
    const ev = this.emptyEvent(rt, coup, 'house');
    const entries: BaccaratCoupEntry[] = [];
    const nat = winningNatural(coup);
    const tieWinners = new Set<string>();
    for (const [id, slip] of rt.slips) {
      const res = settleSlip(slip, coup, r);
      const ticket = rt.tickets.get(id);
      const p = onlinePlayer(id);
      rt.results.set(id, { slip, res });
      const line = t('msg.burmaldaholic.baccarat.coup', rt.plate.coupNo, join(resultRaw(coup), lit(' · '), this.netRaw(res.totalReturn - res.staked)));
      if (ticket) {
        // Offline-safe: core parks the payout of a disconnected bettor until they rejoin.
        this.ctx.wagers.settle(ticket, p, res.totalReturn);
        if (!p) this.ctx.wagers.tell(id, t('msg.burmaldaholic.core.auto_completed', line));
      }
      p?.sendMessage(line);
      // Natural 9 on the winning side (§20.7): flourish + advancement for the winning bet.
      if (nat === 9 && coup.winner !== 'tie' && (slip[coup.winner] ?? 0) > 0) {
        p?.sendMessage(color('§6', t('msg.burmaldaholic.baccarat.natural_nine')));
        this.unlock(id, 'baccarat_natural');
      }
      if (coup.winner === 'tie' && (slip.tie ?? 0) > 0) tieWinners.add(id);
      entries.push({ playerId: id, player: p, bets: { ...slip }, staked: res.staked, totalReturn: res.totalReturn });
    }
    // tie_streak: the same player wins Tie bets on two consecutive coups of this table.
    for (const id of tieWinners) if (rt.lastTieWinners.has(id)) this.unlock(id, 'tie_streak');
    rt.lastTieWinners = tieWinners;
    // Tie run (§20.7): chip_shower for the Tie winners of the coup completing the run.
    const threshold = this.ctx.config.int('baccarat.tieStreakChaos');
    if (coup.winner === 'tie' && tieRunFires(tieRun, threshold)) {
      this.tell(rt, color('§6', plural('msg.burmaldaholic.baccarat.tie_run', tieRun)));
      const chaos = this.ctx.services.get<ChaosApi>(CHAOS_SERVICE);
      for (const id of tieWinners) {
        const p = onlinePlayer(id);
        if (!p || !chaos) continue;
        try {
          chaos.trigger(p, 'chip_shower', { source: 'baccarat' });
        } catch (e) {
          this.ctx.log.error('chaos trigger failed', e);
        }
      }
    }
    rt.slips.clear();
    rt.tickets.clear();
    return { ...ev, entries };
  }

  private nextCoup(rt: TableRt): void {
    rt.last = rt.coup ?? rt.last;
    rt.coup = undefined;
    rt.frames = undefined;
    rt.burned = undefined;
    if (rt.broken) return this.finalizeBroken(rt);
    if (rt.chem) {
      rt.chem.afterCoup();
      return;
    }
    this.reopenBetting(rt);
  }

  /** Betting is open again: queued "Same bets again" are placed, forms re-shown. */
  reopenBetting(rt: TableRt): void {
    for (const s of this.sessions(rt)) this.ctx.hud.actionbar(s.player, HUD_CHANNEL, t(`${K}.place_bets`), HudPriority.game, 60);
    for (const id of [...rt.queuedRebet]) {
      rt.queuedRebet.delete(id);
      const s = this.session(rt, id);
      if (!s) continue;
      const err = this.houseBetting(rt) ? this.rebet(s.player, rt) : undefined;
      this.reshowMain(s, err);
    }
  }

  // ---- leave / break / casino off -------------------------------------------------------

  private onOpen(s: TableSession, rejoined: boolean): void {
    const c = this.ctx.config;
    if (!c.bool('baccarat.enabled')) {
      s.player.sendMessage(t('gui.burmaldaholic.error.disabled'));
      return s.leave();
    }
    const rt = this.table(s.table);
    rt.names.set(s.playerId, s.player.name);
    if (!rejoined) this.tell(rt, t('msg.burmaldaholic.baccarat.player_joined', lit(s.player.name)), s.playerId);
    rt.chem?.onJoin(s);
    this.reshowMain(s);
  }

  private onLeave(s: TableSession, reason: LeaveReason, policy: LeavePolicy): void {
    this.epochs.delete(s.playerId);
    const rt = this.tables.get(s.table.key);
    if (!rt) return;
    // Every reason but casino-off plays the round out (core leavePolicy). A broken table is
    // additionally handled table-wide by breakTable() from the block / NPC removal events.
    if (policy === 'refund') return; // casino mode off: closeAll() on the next tick
    this.tell(rt, t('msg.burmaldaholic.baccarat.player_left', lit(rt.names.get(s.playerId) ?? s.player.name)), s.playerId);
    rt.queuedRebet.delete(s.playerId);
    // §20.5: Leave / walk away during BETTING clears and refunds; a disconnect keeps the bets.
    if (this.houseBetting(rt) && rt.slips.has(s.playerId) && (reason === 'leave' || reason === 'distance')) {
      this.clearBets(s.playerId, rt, s.player);
      this.ctx.wagers.tell(s.playerId, t('msg.burmaldaholic.baccarat.left_refunded'));
    }
    rt.chem?.onLeave(s.playerId, reason);
  }

  /**
   * Table break (block broken, dealer NPC removed): BETTING / NO_MORE_BETS / SHUFFLE → every bet
   * refunded; REVEAL / RESULT → the drawn coup is settled first. The shoe is discarded (§20.5).
   */
  breakTable(key: string): void {
    const rt = this.tables.get(key);
    if (rt && !rt.broken) {
      rt.broken = true;
      const drawn = rt.coup !== undefined && (rt.clock.phase === 'reveal' || rt.clock.phase === 'result');
      if (!drawn) {
        for (const id of [...rt.slips.keys()]) {
          const back = this.clearBets(id, rt);
          if (back > 0) this.ctx.wagers.tell(id, t('msg.burmaldaholic.baccarat.bets_refunded', chips(back)));
        }
        rt.chem?.abort('broken');
        this.finalizeBroken(rt);
      }
    }
    if (!rt || !rt.coup) tableStore.delete(key);
    this.ctx.tables.closeTable(key, 'broken');
  }

  private finalizeBroken(rt: TableRt): void {
    rt.chem?.abort('broken');
    this.tables.delete(rt.key);
    tableStore.delete(rt.key);
  }

  /**
   * Casino mode off (§4.1 ⚠ CHANGED): drawn coups are settled at their draw, undrawn bets
   * refunded; chemin de fer banks are returned. The shoe stays saved.
   */
  private closeAll(rt: TableRt): void {
    const drawn = rt.coup !== undefined;
    for (const [id, ticket] of [...rt.tickets]) {
      const slip = rt.slips.get(id) ?? {};
      const how = this.ctx.wagers.closeOut(ticket, onlinePlayer(id));
      if (how === 'settled' && rt.coup) this.ctx.wagers.tell(id, t('msg.burmaldaholic.core.auto_completed', join(resultRaw(rt.coup), lit(' · '), this.netRaw((ticket.drawn ?? 0) - ticket.value))));
      else if (how === 'refunded') this.ctx.wagers.tell(id, t('msg.burmaldaholic.baccarat.bets_refunded', chips(slipTotal(slip) || ticket.value)));
    }
    rt.slips.clear();
    rt.tickets.clear();
    if (rt.chem) {
      if (drawn && rt.mode === 'chemmy' && rt.coup) rt.chem.settle(rt.coup);
      rt.chem.abort('casino_off');
    }
    if (drawn && rt.coup) rt.plate.record(rt.coup);
    rt.coup = undefined;
    this.persist(rt);
    this.tables.delete(rt.key);
  }

  // ---- persistence / achievements ------------------------------------------------------

  persist(rt: TableRt): void {
    if (rt.broken && !rt.chem?.holdsChips()) return;
    tableStore.update(rt.key, (st) => ({ ...st, shoe: rt.shoe.toData(), hist: rt.plate.toData(), chem: rt.chem?.toStored() }));
  }

  /**
   * Unlock a §19 advancement. Core's list may not know the baccarat ids yet (needed core
   * change): then the unlock is kept here and replayed once core registers the id.
   */
  unlock(playerId: string, id: string): void {
    if (isAchievement(id)) this.ctx.achievements.unlock(playerId, id);
    else pendingAchievements.add(playerId, id);
  }

  private flushPendingAchievements(): void {
    const all = pendingAchievements.all();
    let changed = false;
    for (const [pid, ids] of Object.entries(all)) {
      const rest = ids.filter((id) => {
        if (!isAchievement(id)) return true;
        this.ctx.achievements.unlock(pid, id);
        return false;
      });
      if (rest.length !== ids.length) {
        changed = true;
        if (rest.length) all[pid] = rest;
        else delete all[pid];
      }
    }
    if (changed) pendingAchievements.write(all);
  }

  netRaw(net: number): Raw {
    return color(net > 0 ? '§a' : net < 0 ? '§c' : '§7', t('gui.burmaldaholic.common.result.net', formatSigned(net)));
  }

  // ---- forms ----------------------------------------------------------------------------

  private epoch(id: string): number {
    return this.epochs.get(id) ?? 0;
  }

  /** Server-side close: bump the epoch so a pending response is ignored. */
  closeFor(s: TableSession): void {
    this.epochs.set(s.playerId, this.epoch(s.playerId) + 1);
    s.closeForms();
  }

  reshowMain(s: TableSession, error?: Raw, info?: Raw): void {
    this.closeFor(s);
    detach(this.showMain(s, error, info), (e) => this.ctx.log.error('baccarat main form', e));
  }

  /** Show a form; undefined if closed by the player, by the server, or the session ended. */
  async show<R extends ActionFormResponse | ModalFormResponse>(s: TableSession, form: ActionFormData | ModalFormData): Promise<R | undefined> {
    const e = this.epoch(s.playerId);
    const res = (await showForm(s.player, form as ActionFormData)) as R | undefined;
    if (!res || res.canceled || this.epoch(s.playerId) !== e || !s.isActive()) return undefined;
    return res;
  }

  title(rt: TableRt): Raw {
    return t(rt.kind === 'chemmy' ? `${K}.title_chemmy` : rt.kind === 'high_roller' ? `${K}.title_high_roller` : `${K}.title`);
  }

  /** Status lines shared by every table form: phase, last coup, bead plate, shoe. */
  statusLines(rt: TableRt, now: number): (Raw | undefined)[] {
    const out: (Raw | undefined)[] = [];
    const ph = rt.clock.phase;
    if (ph === 'reveal' && rt.coup && rt.frames) {
      const f = rt.frames[Math.max(0, rt.shownFrame)];
      out.push(coupRaw(rt.coup, f?.player ?? 0, f?.banker ?? 0));
    } else if (ph === 'result' && rt.coup) {
      out.push(coupRaw(rt.coup), color('§6', resultRaw(rt.coup)));
    } else if (ph === 'shuffle') out.push(color('§6', t(`${K}.shuffling`)));
    else if (ph === 'no_more_bets') out.push(color('§6', t(`${K}.no_more_bets`)));
    else if (rt.last) out.push(join(coupRaw(rt.last), lit('  '), color('§7', resultRaw(rt.last))));
    if (ph === 'betting') {
      const left = rt.clock.remaining(now);
      if (left !== undefined) out.push(t(`${K}.bets_close_in`, duration(left)));
    }
    return out;
  }

  historyLines(rt: TableRt): (Raw | undefined)[] {
    const beads = beadsRaw(rt.plate.last(HISTORY_SHOWN));
    const [p, b, ti] = rt.plate.stats;
    return [
      beads ? join(color('§7', t(`${K}.history`)), lit(': '), beads) : undefined,
      rt.plate.shoeCoups ? color('§7', t(`${K}.stats`, p, b, ti)) : undefined,
      rt.shoe.isNew ? undefined : color('§8', plural(`${K}.shoe_left`, rt.shoe.remaining)),
    ];
  }

  seatedLine(rt: TableRt): Raw | undefined {
    const names = this.occupants(rt).map((o) => {
      const ready = rt.clock.isReady(o.id) ? READY_MARK : NO_MARK;
      return join(ready, lit(o.name));
    });
    return names.length > 1 ? t(`${K}.seated`, joinWith(lit(', '), names)) : undefined;
  }

  async showMain(s: TableSession, error?: Raw, info?: Raw): Promise<void> {
    const rt = this.tables.get(s.table.key);
    if (!rt || !s.isActive()) return;
    if (rt.chem && !rt.chem.isHouseCoup()) return rt.chem.showMain(s, error, info);
    const p = s.player;
    const now = system.currentTick;
    const r = this.rules();
    const multi = this.sessions(rt).length > 1;
    const canBet = this.houseBetting(rt);
    const slip = rt.slips.get(p.id) ?? {};
    const l = this.limitsFor(p, rt);

    const body: (Raw | undefined)[] = [];
    if (error) body.push(color('§c', error));
    if (info) body.push(color('§6', info));
    if (rt.chem?.isHouseCoup()) body.push(color('§7', t(`${K}.chemmy.house_coup`)));
    if (canBet) body.push(color('§e', t(`${K}.place_bets`)));
    body.push(...this.statusLines(rt, now));
    if (!canBet && !rt.slips.has(p.id)) body.push(color('§7', t(`${K}.waiting_next`)));
    if (canBet && multi) {
      const bettors = [...rt.slips.keys()];
      if (bettors.length) body.push(t(`${K}.ready_count`, bettors.filter((id) => rt.clock.isReady(id)).length, bettors.length));
      if (rt.clock.isReady(p.id)) body.push(color('§7', t('gui.burmaldaholic.common.waiting_players')));
    }
    body.push(this.seatedLine(rt));
    body.push(t('gui.burmaldaholic.common.balance', chips(this.ctx.economy.balance(p))));
    body.push(t('gui.burmaldaholic.common.limits', chips(l.minBet), chips(l.max)));
    if (l.step > 1) body.push(color('§7', t(`${K}.limits_banker`, l.step)));
    if (l.minTotal > 0) body.push(color('§7', t(`${K}.error.min_total`, chips(l.minTotal))));
    body.push(NEWLINE, color('§l', t(`${K}.your_bets`)));
    if (slipTotal(slip) > 0) {
      for (const b of slipBoxes(slip)) body.push(join(lit(' • '), betLine(b, slip[b]!)));
      body.push(t('gui.burmaldaholic.common.total_bet', chips(slipTotal(slip))));
    } else body.push(color('§7', t(`${K}.no_bets`)));
    body.push(NEWLINE, ...this.historyLines(rt));

    const form = new ActionFormData().title(this.title(rt)).body(lines(...body));
    const actions: MainAction[] = [];
    const add = (a: MainAction, label: Raw) => {
      form.button(label);
      actions.push(a);
    };
    if (canBet) {
      add('player', boxButton('player', r));
      add('banker', boxButton('banker', r));
      add('tie', boxButton('tie', r));
      if (l.pairs) {
        add('player_pair', boxButton('player_pair', r));
        add('banker_pair', boxButton('banker_pair', r));
      }
      const has = slipTotal(slip) > 0;
      if (has) add('clear', t(`${K}.clear_bets`));
      else if (this.lastSlips.has(p.id)) add('rebet', t('gui.burmaldaholic.common.rebet'));
      if (has && !rt.clock.isReady(p.id)) add('ready', t(multi ? 'gui.burmaldaholic.common.ready' : 'gui.burmaldaholic.common.deal'));
    } else add('refresh', t('gui.burmaldaholic.common.ok'));
    add('rules', t('gui.burmaldaholic.common.rules'));
    add('leave', t('gui.burmaldaholic.common.leave'));

    const res = await this.show<ActionFormResponse>(s, form);
    if (!res || res.selection === undefined) return;
    const action = actions[res.selection];
    switch (action) {
      case undefined:
        return;
      case 'clear':
        if (this.houseBetting(rt)) this.clearBets(p.id, rt, p);
        return this.showMain(s);
      case 'rebet':
        return this.showMain(s, this.rebet(p, rt));
      case 'ready': {
        if (!this.houseBetting(rt)) return this.showMain(s);
        const err = checkSlip(rt.slips.get(p.id) ?? {}, this.limitsFor(p, rt), true);
        if (err) return this.showMain(s, slipErrorText(err));
        rt.clock.setReady(p.id);
        // Single bettor: the coup starts next tick and closes this form.
        if (multi) return this.showMain(s);
        return;
      }
      case 'rules':
        return this.showRules(s);
      case 'refresh':
        return this.showMain(s);
      case 'leave':
        return s.leave();
      default:
        return this.showBetAmount(s, action);
    }
  }

  private async showBetAmount(s: TableSession, box: Box, error?: Raw): Promise<void> {
    const rt = this.tables.get(s.table.key);
    if (!rt) return;
    const p = s.player;
    if (!this.houseBetting(rt)) return this.showMain(s, t(`${K}.no_more_bets`));
    const l = this.limitsFor(p, rt);
    const slip = rt.slips.get(p.id) ?? {};
    const balance = this.ctx.economy.balance(p);
    const cur = slip[box] ?? 0;
    let room = Math.min(maxAddable(slip, box, l), balance);
    if (box === 'banker') room = snapToStep(room, l.step);
    const minAdd = box === 'banker' ? Math.max(l.step, boxMin(box, l) - cur) : Math.max(1, boxMin(box, l) - cur);
    if (room < minAdd) {
      const err =
        balance < minAdd
          ? t('gui.burmaldaholic.error.insufficient_funds', chips(balance))
          : box === 'player_pair' || box === 'banker_pair'
            ? l.pairs
              ? t(`${K}.error.side_max`, chips(l.sideMax))
              : t(`${K}.error.pairs_off`)
            : box === 'tie'
              ? t(`${K}.error.side_max`, chips(l.sideMax))
              : t(`${K}.error.total_max`, chips(l.max));
      return this.showMain(s, err);
    }
    const r = this.rules();
    const layout = new ModalLayout();
    const form = new ModalFormData().title(t(`${K}.bet_form_title`, boxName(box)));
    if (error) {
      form.label(color('§c', error));
      layout.passive();
    }
    const info: Raw[] = [t('gui.burmaldaholic.common.balance', chips(balance)), t('gui.burmaldaholic.common.limits', chips(minAdd), chips(room))];
    if (box === 'banker') info.push(color('§7', t(`${K}.limits_banker`, l.step)), color('§7', t(`${K}.bet.banker.tooltip`, lit(Math.round(r.commission * 100)))));
    if (box === 'tie') info.push(color('§7', t(`${K}.bet.tie.tooltip`)));
    if (box === 'player_pair' || box === 'banker_pair') info.push(color('§7', t(`${K}.bet.pair.tooltip`)));
    form.label(lines(...info));
    layout.passive();
    const step = box === 'banker' ? l.step : sliderStep(minAdd, room);
    // Slider steps stay ≤ 100 (UI.md §0.3): for large Banker rooms use a coarser multiple of k.
    const bStep = box === 'banker' ? l.step * Math.max(1, Math.ceil((room - minAdd) / l.step / 100)) : step;
    form.slider(t('gui.burmaldaholic.common.bet'), minAdd, Math.max(minAdd, room), { valueStep: bStep, defaultValue: minAdd });
    const iSlider = layout.control();
    form.textField(t('gui.burmaldaholic.common.exact_amount'), t('gui.burmaldaholic.common.amount'));
    const iText = layout.control();
    form.submitButton(t('gui.burmaldaholic.common.place_bet'));

    const res = await this.show<ModalFormResponse>(s, form);
    if (!res) return this.showMain(s);
    const typed = String(layout.value(res, iText) ?? '').trim();
    let amount = typed ? parseAmount(typed) : Number(layout.value(res, iSlider));
    if (amount === undefined || !Number.isSafeInteger(amount) || amount <= 0) return this.showBetAmount(s, box, t('gui.burmaldaholic.error.invalid_amount'));
    // The Banker box snaps DOWN to the step (§20.1, like craps odds) and says so.
    let info2: Raw | undefined;
    if (box === 'banker' && amount % l.step !== 0) {
      const snapped = snapToStep(amount, l.step);
      if (snapped + cur < boxMin(box, l)) return this.showBetAmount(s, box, t(`${K}.error.banker_step`, l.step));
      amount = snapped;
      info2 = t(`${K}.snapped`, chips(snapped));
    }
    const err = this.addBet(p, rt, box, amount);
    if (err) {
      if (!this.houseBetting(rt)) return this.showMain(s, err);
      return this.showBetAmount(s, box, err);
    }
    return this.showMain(s, undefined, info2);
  }

  async showRules(s: TableSession): Promise<void> {
    const rt = this.tables.get(s.table.key);
    if (!rt) return;
    const c = this.ctx.config;
    const r = this.rules();
    const step = slipLimits({ max: 1, minBet: 1, commission: r.commission, sideMaxFraction: 1 }).step;
    const body: (Raw | undefined)[] = [
      t(`${K}.rules.1`, c.int('baccarat.decks'), lit(Math.round(r.commission * 1000) / 10), r.tiePays),
      t(`${K}.rules.2`),
      t(`${K}.rules.3`),
      t(`${K}.rules.4`),
      c.bool('baccarat.pairBets') ? t(`${K}.rules.5`, r.pairPays) : undefined,
      step > 1 ? t(`${K}.rules.banker_step`, step) : undefined,
      NEWLINE,
      t(`${K}.rules.natural`),
      t(`${K}.rules.player`),
      t(`${K}.rules.banker_no_draw`),
      t(`${K}.rules.banker_title`),
      join(lit('  '), t(`${K}.rules.banker_0_2`)),
      join(lit('  '), t(`${K}.rules.banker_3`)),
      join(lit('  '), t(`${K}.rules.banker_4`)),
      join(lit('  '), t(`${K}.rules.banker_5`)),
      join(lit('  '), t(`${K}.rules.banker_6`)),
      join(lit('  '), t(`${K}.rules.banker_7`)),
    ];
    if (rt.kind === 'chemmy') {
      body.push(NEWLINE, t(`${K}.chemmy.rules.1`), t(`${K}.chemmy.rules.2`), t(`${K}.chemmy.rules.3`, lit(Math.round(c.num('baccarat.chemmy.rakePercent') * 1000) / 10)));
    }
    const form = new ActionFormData().title(t('gui.burmaldaholic.common.rules')).body(lines(...body)).button(t('gui.burmaldaholic.common.back'));
    const res = await this.show<ActionFormResponse>(s, form);
    if (res) return this.showMain(s);
  }

  private async showResult(s: TableSession, rt: TableRt, coup: Coup): Promise<void> {
    const body: (Raw | undefined)[] = [coupRaw(coup), color('§l', resultRaw(coup)), pairsRaw(coup)];
    const nat = winningNatural(coup);
    if (nat !== undefined) body.push(color('§6', t(`${K}.natural`, nat)));
    const mine = rt.results.get(s.playerId);
    const chemLines = rt.chem?.resultLines(s.playerId);
    if (mine && mine.res.staked > 0) {
      body.push(NEWLINE);
      for (const b of slipBoxes(mine.slip)) body.push(betResultLine(boxName(b), mine.slip[b]!, mine.res.returns[b] ?? 0));
      if (mine.res.commission > 0) body.push(color('§7', t(`${K}.line.commission`, chips(mine.res.commission))));
      body.push(this.netRaw(mine.res.totalReturn - mine.res.staked));
    } else if (chemLines) body.push(NEWLINE, ...chemLines);
    body.push(t('gui.burmaldaholic.common.balance', chips(this.ctx.economy.balance(s.player))));
    const form = new ActionFormData().title(this.title(rt)).body(lines(...body));
    const house = !!mine && mine.res.staked > 0;
    if (house) form.button(t(`${K}.same_bets`)).button(t(`${K}.change_bets`));
    else form.button(t('gui.burmaldaholic.common.ok'));
    form.button(t('gui.burmaldaholic.common.leave'));
    const res = await this.show<ActionFormResponse>(s, form);
    if (!res || res.selection === undefined) return;
    const leaveIdx = house ? 2 : 1;
    if (res.selection === leaveIdx) return s.leave();
    if (house && res.selection === 0) {
      if (this.houseBetting(rt)) return this.showMain(s, this.rebet(s.player, rt));
      rt.queuedRebet.add(s.playerId);
    }
    return this.showMain(s);
  }
}
