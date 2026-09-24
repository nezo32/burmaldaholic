/**
 * Slots v2 service (SLOTS.md §1.2, §5, §6, §8; docs/architecture/animation.md §7.2). Lane B-L8, tasks S-B5
 * (round lifecycle, reveal gate, skip, reservation, cut-over), S-B6 (v1 → v2 migration) and S-B7 (integrations).
 *
 * Round: CONFIRM (stake through `ctx.wagers.place`, owned reservation `cap × bet`, jackpot contributions) →
 * DRAW the whole tape (`drawSpin`, streak re-draw through `ctx.odds.draw`, never for bought features) → award
 * jackpots at draw time (pools debited now; other players' meters show `pool + pending`) → PERSIST
 * (`wagers.draw` for the wager return + the `{v:2,…}` record in `burmaldaholic:slots:rounds`) → PRESENT (the
 * shared `SlotTimeline`; presenters are pluggable, lane B-L9/B-L10) → SETTLE at the reveal gate
 * (`startTick + timeline.sharedEndTicks()`), or at once on skip / close / leave / disconnect / restart.
 *
 * Money: the wager return (spin total excl. progressive awards) settles through `wagers.settle` (streak, VIP,
 * Golden Hour, contracts see it); progressive pool money is credited separately (`slots.jackpot`), so Golden
 * Hour never multiplies pool money (SLOTS.md §8.3). Pool money owed to an offline player (restart, disconnect)
 * waits in `burmaldaholic:slots:owed` and is paid on their next spawn.
 *
 * CUT-OVER FLAG: `SLOTS_V2_ENABLED` (index.ts) is OFF until the strings (SLOTS.md §13), CONFIG.md `slots`
 * section (§12), advancements (§14), the B-L9 form presenter and the Java cut-over (S-J5) land together
 * (docs/architecture/animation.md §9.3 merge order).
 */
import { type Dimension, type Player, type Vector3, system, world } from '@minecraft/server';
import { ActionFormData, ModalFormData } from '@minecraft/server-ui';
import {
  type HouseRef,
  type ModuleContext,
  type Raw,
  type TableRef,
  type TableSession,
  type WagerTicket,
  BANK,
  HudPriority,
  anim,
  chips,
  chipsAcc,
  color,
  detach,
  joinWith,
  isAchievement,
  lines,
  lit,
  mathRng,
  presentation,
  readJson,
  showForm,
  t,
  worldJson,
  worldSharded,
  worldTick,
  writeJson,
} from '../../core';
import { CHAOS_SERVICE, type ChaosApi } from '../../chaos/api';
import { JACKPOT_SHOWER_RADIUS, type SlotTier, type SlotsApi, type SlotsHouse, type SlotsHouseResolver, type SlotsSpinEvent, type SlotsTriggerEvent } from './api';
import {
  type AllPools,
  type AutoStop,
  type AutoplayState,
  type MachineDef,
  type MachineId,
  type MachineStats,
  type RoundRecord,
  type SlotRng,
  type SlotsGlobalConfig,
  type SpinTape,
  MACHINE_IDS,
  REMOVED_V1_KEYS,
  SLOT_BEAT,
  SLOT_TIER_WORDS,
  SYMBOLS,
  addStats,
  applyAwards,
  achievementsFor,
  autoplayStop,
  buildMachineDef,
  buildSlotTimeline,
  buyError,
  chaosFor,
  computeRtp,
  contribute,
  displayedPools,
  drawSpin,
  encodeTape,
  evaluateSpin,
  featureTriggered,
  cabinetSpinOf,
  huntOpens,
  huntPauseMs,
  isLosingSpin,
  isRoundRecord,
  loadPools,
  migrateV1Pools,
  offeredBets,
  poolValues,
  readGlobalConfig,
  readMachineConfig,
  resetPools,
  rtpWarnings,
  rtpTablesDefault,
  sampleRtp,
  settlement,
  slotTier,
  symbolAt,
  tapeTotalChips,
  windowFromStops,
  type RtpBreakdown,
  type CabinetSpinData,
} from './v2/logic';

// ---- persistence ids ------------------------------------------------------------------------------

/** Pools per machine type per world (SLOTS.md §5.1). */
export const POOLS_PROP = 'burmaldaholic:slots:jp';
/** Persisted rounds, sharded, keyed by the machine's table key (SLOTS.md §8.1). */
export const ROUNDS_PROP = 'burmaldaholic:slots:rounds';
/** Pool money owed to offline players. */
export const OWED_PROP = 'burmaldaholic:slots:owed';
/** v1 pool store (read once for the migration, SLOTS.md §5.3). */
export const V1_POOLS_PROP = 'burmaldaholic:slots.jackpot';
/** Per-player statistics (SLOTS.md §8.8). */
export const STATS_PROP = 'burmaldaholic:slots.stats';
/** Per-player last bet per machine. */
export const BET_PROP_V2 = 'burmaldaholic:slots.bet';
/** Per-player turbo preference (SLOTS.md §6.4; shared speed is published with the seed). */
export const TURBO_PROP = 'burmaldaholic:slots.turbo';

const GATE_TIMER = 'slots.v2.gate';

/** Autoplay stop notices (SLOTS.md §13.3); running out of spins needs none. */
const AUTO_STOP_KEY: Record<AutoStop, string | undefined> = {
  jackpot: 'gui.burmaldaholic.slots.auto_stopped_jackpot',
  max_win: 'gui.burmaldaholic.slots.auto_stopped_big_win',
  feature: 'gui.burmaldaholic.slots.auto_stopped_feature',
  big_win: 'gui.burmaldaholic.slots.auto_stopped_big_win',
  loss: 'gui.burmaldaholic.slots.auto_stopped_loss',
  funds: 'gui.burmaldaholic.slots.auto_stopped_funds',
  done: undefined,
};
const AUTO_TIMER = 'slots.v2.auto';
const HUD = 'slots.status';

/** Block variant / id → machine (block ids unchanged, SLOTS.md §0). */
export function machineOfTable(table: Pick<TableRef, 'variant' | 'blockTypeId'>): MachineId {
  const v = table.variant ?? /slot_machine_(\w+)$/.exec(table.blockTypeId ?? '')?.[1];
  return v === 'gold' || v === 'nether' ? 'nether' : v === 'netherite' || v === 'end' ? 'end' : 'overworld';
}

const V1_TIER: Record<SlotTier, MachineId> = { copper: 'overworld', gold: 'nether', netherite: 'end' };

// ---- presenter contract (lanes B-L9 / B-L10 implement it; a classic fallback is below) -----------------

export interface MachineView {
  session: TableSession;
  machine: MachineId;
  def: MachineDef;
  bet: number;
  bets: number[];
  /** buy price in chips, undefined = no buy here */
  buyPrice?: number;
  /** meters (pool + pending), index 1..4; undefined at owned machines */
  meters?: number[];
  last?: SpinTape;
  notice: Raw[];
  autoplay: boolean;
}

export type MachineAction =
  | { kind: 'spin' }
  | { kind: 'buy' }
  | { kind: 'bet'; bet: number }
  | { kind: 'auto'; count: number; lossLimit: number; stopOnFeature: boolean; stopOnWin: number }
  | { kind: 'paytable' }
  | { kind: 'leave' };

export interface RoundView {
  session: TableSession;
  machine: MachineId;
  def: MachineDef;
  tape: SpinTape;
  timeline: anim.Timeline;
  /** tick the timeline started (t = 0) */
  startTick: number;
  seed: number;
  /** manual Treasure Hunt: the presenter asks for picks (`SlotsV2Service.pick`) at the hunt section */
  manualHunt: boolean;
}

export interface SlotsV2Presenter {
  /** Machine screen between spins; resolves with the player's choice (undefined = closed). */
  machine(view: MachineView): Promise<MachineAction | undefined>;
  /** Present a drawn round; must end on the tape's terminal state (fidelity F8: stop = reveal). */
  play(round: RoundView): void;
  /** A Treasure Hunt pick was confirmed: entry `i` may be shown now (D6). */
  picked?(round: RoundView, i: number, entry: number): void;
  /** The round settled (gate, skip, leave): stop any animation and show the terminal state. */
  settled(round: RoundView, stop: AutoStop | undefined): void;
  /** Tell the player why a spin was refused. */
  error(session: TableSession, error: Raw): void;
}

/**
 * Presentation modules wired in by index.ts at the cut-over (they live in lanes B-L9 / B-L10 and are not on this
 * branch yet): `roundFromTape` (`v2/present/frames.ts`), `playCabinet` / `finishCabinet` (`cabinet.ts`).
 */
export interface SlotsV2Deps<R = unknown> {
  roundFromTape?(def: MachineDef, tape: SpinTape, opts: { tier: anim.WinTier; rest?: readonly number[] }): R;
  playCabinet?(dim: Dimension, pos: Vector3, spin: CabinetSpinData, startTick: number): void;
  finishCabinet?(dim: Dimension, pos: Vector3): void;
}

/** B-L9 `SpinStart`: the round to present and the timeline the server settles by. */
export interface SpinStartLike<R> {
  readonly round: R;
  readonly timeline: anim.Timeline;
  readonly startMs?: number;
}

/** Slack after the last Treasure Hunt pick for the local end reveal before the server settles (F6). */
export const HUNT_SETTLE_SLACK_MS = 1000;

// ---- the service -----------------------------------------------------------------------------------

interface Live {
  session: TableSession;
  player: Player;
  name: string;
  ticket: WagerTicket;
  record: RoundRecord;
  def: MachineDef;
  tape: SpinTape;
  view: RoundView;
  owned: boolean;
  auto?: AutoplayState;
  /** pool values before this spin's awards (other players' meters, F6) */
  metersBefore: number[];
  /** started by a B-L9 `SlotHost` form: that form presents and re-opens itself */
  viaHost: boolean;
  /** chests this hunt opens (`huntOpens`) */
  huntLength: number;
}

interface MachineState {
  machine: MachineId;
  bet: number;
  last?: SpinTape;
  busy: boolean;
  auto?: AutoplayState;
  notice: Raw[];
}

export class SlotsV2Service implements SlotsApi {
  private cfg!: SlotsGlobalConfig;
  private readonly defs = new Map<MachineId, MachineDef>();
  private readonly bets = new Map<MachineId, { ladder: number[]; defaultBet: number; minVipTier: number; enabled: boolean }>();
  private readonly rtp = new Map<MachineId, RtpBreakdown>();
  private pools!: AllPools;
  private readonly live = new Map<string, Live>();
  private readonly triggerListeners = new Set<(e: SlotsTriggerEvent) => void>();
  private readonly spinListeners = new Set<(e: SlotsSpinEvent) => void>();
  private resolver: SlotsHouseResolver | undefined;
  private seq = 0;
  readonly warnings: string[] = [];

  constructor(
    private readonly ctx: ModuleContext,
    private presenter: SlotsV2Presenter = new ClassicPresenter(),
    private readonly rng: SlotRng = { nextInt: (b) => Math.floor(mathRng.next() * b) },
    readonly deps: SlotsV2Deps = {},
  ) {}

  setPresenter(p: SlotsV2Presenter): void {
    this.presenter = p;
  }

  // ---- config ------------------------------------------------------------------------------------

  private get(key: string): unknown {
    try {
      return this.ctx.config.get(key);
    } catch {
      return undefined; // not in CONFIG.md yet → SLOTS.md §12 default
    }
  }

  /** (Re)load config: machine definitions, bet ladders, RTP (validateRtp). */
  loadConfig(): void {
    this.cfg = readGlobalConfig((k) => this.get(k));
    this.warnings.length = 0;
    for (const m of MACHINE_IDS) {
      const { config, errors } = readMachineConfig(m, (k) => this.get(k));
      for (const e of errors) this.ctx.log.warn(`config rejected, default kept: ${e}`);
      const def = buildMachineDef(m, config);
      this.defs.set(m, def);
      this.bets.set(m, { ladder: config.bets, defaultBet: config.defaultBet, minVipTier: config.minVipTier, enabled: config.enabled });
      // Nether: the shipped §7.1 numbers hold while the game tables are default (a changed buy price or
      // contribution is still validated exactly); other changes are re-checked by sampling
      const r = computeRtp(def, rtpTablesDefault(m, config));
      if (r) this.rtp.set(m, r);
      else this.sampleNether(def);
    }
    if (this.cfg.validateRtp) this.checkRtp();
  }

  /** Changed Nether tables: 10⁶-spin sample spread over ticks (SLOTS.md §7.5). */
  private sampleNether(def: MachineDef): void {
    const gen = sampleRtp(def, 1_000_000, this.rng, 500);
    const job = function* (this: SlotsV2Service): Generator<void, void, void> {
      let r = gen.next();
      while (!r.done) {
        yield;
        r = gen.next();
      }
      const total = r.value;
      this.rtp.set(def.machine, { machine: def.machine, base: 0, scatter: 0, freeSpins: 0, bonus: 0, jackpotSeed: 0, contributions: 0, total, owned: total, method: 'sample' });
      if (this.cfg.validateRtp) this.checkRtp();
    }.call(this);
    try {
      system.runJob(job);
    } catch (e) {
      this.ctx.log.error('slots RTP sample failed', e);
    }
  }

  private checkRtp(): void {
    for (const r of this.rtp.values()) {
      for (const w of rtpWarnings(r)) {
        const msg = `slots ${w.machine}: ${w.kind === 'rtp' ? 'RTP' : 'buy RTP'} ${(w.value * 100).toFixed(3)} % ${w.kind === 'buy_parity' ? '<' : '>'} ${(w.limit * 100).toFixed(3)} %`;
        if (!this.warnings.includes(msg)) this.warnings.push(msg);
        this.ctx.log.warn(msg);
      }
    }
  }

  rtpOf(m: MachineId): RtpBreakdown | undefined {
    return this.rtp.get(m);
  }

  def(m: MachineId): MachineDef {
    return this.defs.get(m)!;
  }

  // ---- pools (SLOTS.md §5) --------------------------------------------------------------------------

  loadPools(): void {
    const raw = worldJson.read<{ v?: number; migrated?: boolean } & Partial<Record<MachineId, unknown>>>(POOLS_PROP, {});
    this.pools = { overworld: loadPools(raw.overworld), nether: loadPools(raw.nether), end: loadPools(raw.end) };
    if (!raw.migrated) {
      // S-B6: v1 Golden Reels / Netherite increments → Nether / End Grand, once (SLOTS.md §5.3)
      const v1 = worldJson.read<{ gold?: { pool?: number }; netherite?: { pool?: number } } | undefined>(V1_POOLS_PROP, undefined);
      // the v1 seed keys leave the catalog at the cut-over: fall back to the stored raw config, then the v1 default
      const seedOf = (k: string, d: number): number => {
        const v = this.get(k) ?? rawConfigValue(k);
        return typeof v === 'number' && Number.isFinite(v) && v >= 0 ? v : d;
      };
      const moved = migrateV1Pools(this.pools, v1, { gold: seedOf('slots.jackpot.seed.gold', 5000), netherite: seedOf('slots.jackpot.seed.netherite', 50_000) });
      if (v1) this.ctx.log.info(`slots v2: v1 jackpots migrated (Nether Grand +${moved.nether}, End Grand +${moved.end})`);
      this.savePools();
    }
  }

  private savePools(): void {
    worldJson.write(POOLS_PROP, { v: 2, migrated: true, ...this.pools });
  }

  /** Meter values for other players: pool + awards drawn but not revealed yet (F6). */
  meters(m: MachineId): number[] {
    const pending: Array<{ tier: number; before: number }> = [];
    for (const l of this.live.values()) if (l.def.machine === m) for (const a of l.record.jackpotAwards) pending.push({ tier: a.tier, before: l.metersBefore[a.tier] ?? 0 });
    return displayedPools(poolValues(this.def(m).jackpot, this.pools[m]), pending);
  }

  jackpotPool(tier: SlotTier): number {
    return this.meters(V1_TIER[tier])[4] ?? 0;
  }

  resetJackpots(tier?: SlotTier): void {
    for (const m of MACHINE_IDS) {
      if (tier && V1_TIER[tier] !== m) continue;
      const removed = resetPools(this.pools[m]);
      this.ctx.log.info(`slots ${m}: jackpots reset to seed (${removed} chips removed)`);
    }
    this.savePools();
  }

  // ---- api ------------------------------------------------------------------------------------------

  onTrigger(l: (e: SlotsTriggerEvent) => void): () => void {
    this.triggerListeners.add(l);
    return () => void this.triggerListeners.delete(l);
  }

  onSpin(l: (e: SlotsSpinEvent) => void): () => void {
    this.spinListeners.add(l);
    return () => void this.spinListeners.delete(l);
  }

  setHouseResolver(r: SlotsHouseResolver | undefined): void {
    this.resolver = r;
  }

  private houseAt(table: TableRef, player?: Player): SlotsHouse | undefined {
    try {
      if (this.resolver) return this.resolver(table.dimension.id, table.location);
      return player ? { house: this.ctx.wagers.resolveHouse(player, 'slots', table.key) } : undefined;
    } catch (e) {
      this.ctx.log.error('slots house resolver failed', e);
      return undefined;
    }
  }

  // ---- table handler -------------------------------------------------------------------------------

  canJoin(player: Player, table: TableRef): Raw | undefined {
    const m = machineOfTable(table);
    if (!this.cfg.enabled) return t('gui.burmaldaholic.error.disabled');
    if (!this.bets.get(m)!.enabled) return t('gui.burmaldaholic.slots.out_of_order');
    const min = this.bets.get(m)!.minVipTier;
    if (this.ctx.limits.tier(player) < min) return t('gui.burmaldaholic.error.vip_required', this.ctx.limits.tierName(min));
    const h = this.houseAt(table, player);
    if (h?.closed) return t('gui.burmaldaholic.error.table_closed');
    if (h?.ownerId && h.ownerId === player.id) return t('gui.burmaldaholic.error.owner_cannot_play');
    return this.ctx.wagers.check(player, 'slots', table.key);
  }

  private state(s: TableSession): MachineState {
    let st = s.data.slotsV2 as MachineState | undefined;
    if (!st) {
      const machine = machineOfTable(s.table);
      const saved = readJson<Partial<Record<MachineId, number>>>(s.player, BET_PROP_V2, {});
      st = { machine, bet: saved[machine] ?? this.bets.get(machine)!.defaultBet, busy: false, notice: [] };
      s.data.slotsV2 = st;
    }
    return st;
  }

  /** Bets offered to this player here (VIP max, owner min/max; SLOTS.md §6.1). */
  offered(s: TableSession, m: MachineId): number[] {
    const ladder = this.bets.get(m)!.ladder;
    const l = this.ctx.wagers.limitsFor(s.player, 'slots', { min: ladder[0], tableMax: ladder[ladder.length - 1] }, s.table.key);
    return offeredBets(ladder, this.ctx.limits.tierMax(s.player), l.min ?? 0, l.tableMax ?? Number.MAX_SAFE_INTEGER);
  }

  onOpen(s: TableSession): void {
    const st = this.state(s);
    if (st.busy) return;
    detach(this.showMachine(s), (e) => this.ctx.log.error('slots form', e));
  }

  onLeave(s: TableSession): void {
    const st = s.data.slotsV2 as MachineState | undefined;
    if (st) st.auto = undefined;
    const l = this.live.get(s.playerId);
    if (l) this.settle(l, !s.player.isValid ? 'disconnect' : 'leave');
    if (s.player.isValid) this.ctx.hud.clear(s.player, HUD);
  }

  async showMachine(s: TableSession): Promise<void> {
    if (!s.isActive()) return;
    const st = this.state(s);
    const def = this.def(st.machine);
    const bets = this.offered(s, st.machine);
    if (bets.length && !bets.includes(st.bet)) st.bet = bets.reduce((a, b) => (Math.abs(b - st.bet) < Math.abs(a - st.bet) ? b : a), bets[0]!);
    const owned = this.houseAt(s.table, s.player)?.house.kind === 'bankroll';
    const view: MachineView = {
      session: s,
      machine: st.machine,
      def,
      bet: st.bet,
      bets,
      buyPrice: def.buyPriceFifths > 0 && this.cfg.buyEnabled ? (def.buyPriceFifths * st.bet) / 5 : undefined,
      meters: owned ? undefined : this.meters(st.machine),
      last: st.last,
      notice: st.notice.splice(0),
      autoplay: this.cfg.autoplayEnabled,
    };
    const a = await this.presenter.machine(view);
    if (!s.isActive() || st.busy) return;
    if (!a || a.kind === 'leave') return s.leave();
    switch (a.kind) {
      case 'bet':
        if (bets.includes(a.bet)) {
          st.bet = a.bet;
          const saved = readJson<Partial<Record<MachineId, number>>>(s.player, BET_PROP_V2, {});
          saved[st.machine] = a.bet;
          writeJson(s.player, BET_PROP_V2, saved);
        } else st.notice.push(color('§c', t('gui.burmaldaholic.slots.error.bet_unavailable')));
        return this.showMachine(s);
      case 'paytable':
        await this.paytable(s, st.machine);
        return this.showMachine(s);
      case 'auto':
        if (!this.cfg.autoplayEnabled) return this.showMachine(s);
        if (!this.cfg.autoplayLossLimits.includes(a.lossLimit)) {
          st.notice.push(color('§c', t('gui.burmaldaholic.slots.error.loss_limit_required')));
          return this.showMachine(s);
        }
        st.auto = { left: a.count, startBalance: this.ctx.economy.balance(s.player), lossLimit: a.lossLimit, stopOnFeature: a.stopOnFeature, stopOnWin: a.stopOnWin, bet: st.bet };
        return this.spin(s, false);
      case 'buy':
        return this.spin(s, true);
      default:
        return this.spin(s, false);
    }
  }

  private async paytable(s: TableSession, m: MachineId): Promise<void> {
    const def = this.def(m);
    const r = this.rtp.get(m);
    const body: Raw[] = [t('gui.burmaldaholic.slots.paytable.per_way')];
    for (let p = 0; p < def.roles.length; p++) {
      if (def.roles[p] !== 'PAY') continue;
      const pay = def.paysFifths[p]!.map((f) => chips((f * this.state(s).bet) / 5));
      body.push(t('gui.burmaldaholic.slots.paytable.row', t(`gui.burmaldaholic.slots.symbol.${SYMBOLS[m][p]!.id}`), pay[0]!, pay[1]!, pay[2]!));
    }
    if (r) body.push(t('gui.burmaldaholic.slots.paytable.rtp', lit((r.total * 100).toFixed(2))));
    body.push(t('gui.burmaldaholic.slots.paytable.max_win', lit(def.capMultiple)));
    if (def.hunt) body.push(t('gui.burmaldaholic.slots.help.pick_fair'));
    body.push(t('gui.burmaldaholic.slots.help.anticipation'));
    await showForm(s.player, new ActionFormData().title(t('gui.burmaldaholic.slots.paytable.title')).body(lines(...body)).button(t('gui.burmaldaholic.common.back')));
  }

  // ---- CONFIRM → DRAW → PERSIST → PRESENT --------------------------------------------------------------

  /** Stake, draw, persist and present one spin (or buy). Returns an error (also shown) or the live round. */
  start(s: TableSession, buy: boolean, viaHost = false): Live | Raw {
    const p = s.player;
    const st = this.state(s);
    const m = st.machine;
    const def = this.def(m);
    if (!this.ctx.isCasinoEnabled()) return t('gui.burmaldaholic.error.casino_off');
    if (!this.cfg.enabled) return t('gui.burmaldaholic.error.disabled');
    if (!this.bets.get(m)!.enabled) return t('gui.burmaldaholic.slots.out_of_order');
    if (this.live.has(s.playerId)) return t('gui.burmaldaholic.error.disabled');
    const h = this.houseAt(s.table, p);
    if (h?.closed) return t('gui.burmaldaholic.error.table_closed');
    const owned = h?.house.kind === 'bankroll';
    const house: HouseRef = h?.house ?? BANK;
    const bets = this.offered(s, m);
    if (!bets.includes(st.bet)) return t('gui.burmaldaholic.slots.error.bet_unavailable');
    if (buy) {
      const err = buyError(def, st.bet, this.cfg.buyEnabled, true, this.ctx.limits.tierMax(p), this.cfg.buyTierMaxMultiple, bets);
      if (err) return t(`gui.burmaldaholic.slots.error.${err}`);
    }
    const rtp = this.rtp.get(m);
    const stake = buy ? (def.buyPriceFifths * st.bet) / 5 : st.bet;
    const edge = rtp ? 1 - (buy ? (rtp.buy ?? rtp.total) : owned ? rtp.owned : rtp.total) : undefined;
    const r = this.ctx.wagers.place(p, {
      game: 'slots',
      stake: { kind: 'chips', amount: stake },
      limits: buy ? undefined : { min: bets[0], tableMax: bets[bets.length - 1], minTier: this.bets.get(m)!.minVipTier },
      skipLimits: buy,
      house,
      tableKey: s.table.key,
      worstCase: def.capMultiple * st.bet, // owned reservation (SLOTS.md §8.6), also for a buy
      houseEdge: edge !== undefined ? Math.max(0, edge) : undefined,
      notify: false,
    });
    if (!r.ok) return r.error;
    const pools = this.pools[m];
    if (!owned) contribute(def.jackpot, pools, stake);
    const metersBefore = poolValues(def.jackpot, pools);
    const req = { def, bet: st.bet, buy, owned, pools: owned ? [] : metersBefore };
    const tape = buy
      ? drawSpin(req, this.rng) // bought features are never re-drawn (SLOTS.md §8.2)
      : this.ctx.odds.draw(p.id, rtp?.total ?? 0.95, mathRng, () => drawSpin(req, this.rng), isLosingSpin).result;
    if (!owned) applyAwards(def, pools, tape);
    this.savePools();
    const split = settlement(def, tape);
    this.ctx.wagers.draw(r.ticket, split.wagerReturn);
    const startTick = system.currentTick;
    const seed = anim.seedMix(anim.seedHash(s.table.key), ++this.seq);
    const shared: anim.TimingProfile = { speedPct: this.cfg.turboAllowed && readJson<boolean>(p, TURBO_PROP, false) ? 200 : 100, reduceMotion: false, flashes: true };
    const fx = presentation.fxSettings(p);
    const timeline = buildSlotTimeline(tape, def, shared, presentation.localProfile(fx), seed, { anticipation: this.cfg.anticipation, bigWinTiers: this.cfg.bigWinTiers });
    const record: RoundRecord = {
      v: 2,
      machine: m,
      playerId: p.id,
      bet: st.bet,
      price: buy ? stake : undefined,
      tape: encodeTape(tape),
      total: split.wagerReturn,
      jackpotAwards: tape.jackpots.filter((j) => !j.owned).map((j) => ({ tier: j.tier, chips: j.chips })),
      startTick: worldTick(),
      gateTick: worldTick() + timeline.sharedEndTicks(),
      ticket: r.ticket.id,
    };
    this.persistRound(s.table.key, record);
    const manualHunt = !!tape.hunt && !st.auto;
    const view: RoundView = { session: s, machine: m, def, tape, timeline, startTick, seed, manualHunt };
    const live: Live = { session: s, player: p, name: p.name, ticket: r.ticket, record, def, tape, view, owned, auto: st.auto, metersBefore, viaHost, huntLength: huntOpens(def, tape) };
    this.live.set(s.playerId, live);
    return live;
  }

  spin(s: TableSession, buy: boolean): void {
    const st = this.state(s);
    const res = this.start(s, buy);
    if (!('ticket' in res)) {
      st.auto = undefined;
      this.presenter.error(s, res);
      st.notice.push(color('§c', res));
      return detach(this.showMachine(s), (e) => this.ctx.log.error('slots form', e));
    }
    this.presenter.play(res.view);
    this.begin(res);
  }

  /** After CONFIRM/DRAW/PERSIST: arm the reveal gate, start the cabinet. */
  begin(l: Live): void {
    const s = l.session;
    const st = this.state(s);
    st.busy = true;
    if (st.auto && !l.viaHost) st.auto.left--;
    const tl = l.view.timeline;
    try {
      this.deps.playCabinet?.(s.table.dimension, s.table.location, cabinetSpinOf(l.def, l.tape, tl, this.cfg.bigWinTiers), l.view.startTick);
    } catch (e) {
      this.ctx.log.error('slots cabinet failed', e);
    }
    const pause = huntPauseMs(tl);
    if (pause === undefined) {
      s.setTimer(GATE_TIMER, tl.sharedEndTicks(), () => this.settle(l, 'gate'));
      return;
    }
    // Treasure Hunt: the timeline pauses after the board intro until the picks are done (autoplay: all at once)
    s.setTimer(GATE_TIMER, anim.ceilTicks(pause), () => {
      if (!l.view.manualHunt) this.pickAll(l);
    });
  }

  /**
   * Treasure Hunt pick i (D6: entry i is revealed only now). Idempotent for i < opened; refuses skipping ahead.
   * After the last pick the rest of the shared timeline plays and the round settles at its end.
   */
  huntPick(l: Live, i: number): number | undefined {
    const h = l.tape.hunt;
    if (!h || this.live.get(l.session.playerId) !== l) return undefined;
    if (i < h.opened) return h.entries[i];
    if (i !== h.opened || i >= l.huntLength) return undefined;
    h.opened = i + 1;
    l.record.opened = h.opened;
    this.persistRound(l.session.table.key, l.record);
    if (!l.viaHost) this.presenter.picked?.(l.view, i, h.entries[i]!);
    if (h.opened >= l.huntLength) {
      const tl = l.view.timeline;
      const rest = tl.sharedEndMs() - (huntPauseMs(tl) ?? 0) + HUNT_SETTLE_SLACK_MS;
      l.session.setTimer(GATE_TIMER, anim.ceilTicks(rest), () => this.settle(l, 'gate'));
    }
    return h.entries[i];
  }

  private pickAll(l: Live): void {
    while (l.tape.hunt && l.tape.hunt.opened < l.huntLength) if (this.huntPick(l, l.tape.hunt.opened) === undefined) break;
  }

  /** Treasure Hunt pick from the classic form ("Open a chest" / "Open all"). */
  pick(s: TableSession, all = false): void {
    const l = this.live.get(s.playerId);
    if (!l?.tape.hunt) return;
    if (all) this.pickAll(l);
    else this.huntPick(l, l.tape.hunt.opened);
  }

  liveOf(playerId: string): Live | undefined {
    return this.live.get(playerId);
  }

  /** Skip / slam stop: the result is already persisted, settle now (the presenter jumps to the terminal frame). */
  skip(s: TableSession): void {
    const l = this.live.get(s.playerId);
    if (l) this.settle(l, 'skip');
  }

  // ---- SETTLE ------------------------------------------------------------------------------------------

  settle(l: Live, reason: 'gate' | 'skip' | 'leave' | 'disconnect'): void {
    if (this.live.get(l.session.playerId) !== l) return;
    this.live.delete(l.session.playerId);
    l.session.clearTimer(GATE_TIMER);
    const quiet = reason === 'leave' || reason === 'disconnect';
    const valid = l.player.isValid;
    const split = settlement(l.def, l.tape);
    try {
      this.ctx.wagers.settle(l.ticket, valid ? l.player : undefined, split.wagerReturn);
    } catch (e) {
      this.ctx.log.error('slots settle failed', e);
    }
    this.payPool(l.record.playerId, valid ? l.player : undefined, split.poolChips);
    this.deleteRound(l.session.table.key);
    try {
      this.deps.finishCabinet?.(l.session.table.dimension, l.session.table.location);
    } catch (e) {
      this.ctx.log.error('slots cabinet failed', e);
    }
    const st = l.session.data.slotsV2 as MachineState | undefined;
    if (st) {
      st.last = l.tape;
      st.busy = false;
    }
    if (valid) this.afterSettle(l, quiet);
    let stop: AutoStop | undefined;
    if (st?.auto && valid && !quiet) {
      const next = l.tape.bought ? (l.def.buyPriceFifths * st.bet) / 5 : st.bet;
      stop = autoplayStop(st.auto, l.tape, this.ctx.economy.balance(l.player), next);
      if (stop) {
        const key = AUTO_STOP_KEY[stop];
        if (key) st.notice.push(color('§e', t(key)));
        st.auto = undefined;
      }
    }
    if (l.viaHost) return; // the B-L9 form shows the terminal state and re-opens itself
    this.presenter.settled(l.view, stop);
    if (quiet || !valid || !l.session.isActive()) return;
    if (st?.auto) {
      const s = l.session;
      s.setTimer(AUTO_TIMER, 20, () => this.spin(s, false));
      return;
    }
    detach(this.showMachine(l.session), (e) => this.ctx.log.error('slots form', e));
  }

  private payPool(playerId: string, player: Player | undefined, chipsOwed: number): void {
    if (chipsOwed <= 0) return;
    if (player?.isValid) {
      this.ctx.economy.credit(player, chipsOwed, 'slots.jackpot');
      return;
    }
    const owed = worldJson.read<Record<string, number>>(OWED_PROP, {});
    owed[playerId] = (owed[playerId] ?? 0) + chipsOwed;
    worldJson.write(OWED_PROP, owed);
  }

  /** Pay pool money owed from an offline settlement or a restart. */
  payOwed(player: Player): void {
    const owed = worldJson.read<Record<string, number>>(OWED_PROP, {});
    const n = owed[player.id];
    if (!n) return;
    delete owed[player.id];
    worldJson.write(OWED_PROP, owed);
    this.ctx.economy.credit(player, n, 'slots.jackpot');
    player.sendMessage(color('§6', t('gui.burmaldaholic.slots.spin_total', chipsAcc(n))));
  }

  private afterSettle(l: Live, quiet: boolean): void {
    const { player, def, tape } = l;
    // statistics (SLOTS.md §8.8)
    const all = readJson<Partial<Record<MachineId, MachineStats>>>(player, STATS_PROP, {});
    all[def.machine] = addStats(all[def.machine], def, tape);
    writeJson(player, STATS_PROP, all);
    // advancements (§14; ids unknown to core yet are skipped)
    for (const id of achievementsFor(def, tape, this.cfg.bigWinTiers)) if (isAchievement(id)) this.ctx.achievements.unlock(player, id);
    // announcements: jackpots from announceMinTier up; Epic wins to players ≤ 32 blocks (SLOTS.md §10.1)
    for (const j of tape.jackpots) {
      if (j.tier < this.cfg.announceMinTier) continue;
      try {
        world.sendMessage(color('§6', t('msg.burmaldaholic.slots.jackpot_broadcast', lit(l.name), t(`gui.burmaldaholic.slots.jackpot.tier.${['', 'mini', 'minor', 'major', 'grand'][j.tier]}`), chipsAcc(j.chips), t(`gui.burmaldaholic.slots.machine.${def.machine}`))));
      } catch (e) {
        this.ctx.log.error('jackpot broadcast failed', e);
      }
    }
    if (!quiet && slotTier(tape.totalFifths, this.cfg.bigWinTiers) === 'EPIC') {
      const msg = t('msg.burmaldaholic.slots.big_win_broadcast', lit(l.name), lit(Math.floor(tape.totalFifths / 5)), t(`gui.burmaldaholic.slots.machine.${def.machine}`));
      for (const q of world.getAllPlayers()) if (q.dimension.id === player.dimension.id && dist2(q.location, player.location) <= 32 * 32) q.sendMessage(msg);
    }
    this.emitSpin(l);
    if (!quiet) this.chaos(l);
  }

  private emitSpin(l: Live): void {
    const { tape, def } = l;
    const split = settlement(def, tape);
    const tier: SlotTier = def.machine === 'overworld' ? 'copper' : def.machine === 'nether' ? 'gold' : 'netherite';
    const e: SlotsSpinEvent = {
      player: l.player,
      tier,
      lineBet: tape.bet,
      spinBet: split.stake,
      totalReturn: split.wagerReturn + split.poolChips,
      wins: [],
      jackpotAward: split.poolChips,
      threeSevens: false,
      owned: l.owned,
      machine: def.machine,
      tape: l.record.tape,
      featureTriggered: featureTriggered(tape),
      bought: tape.bought,
      jackpotTiers: tape.jackpots.map((j) => j.tier),
      winTier: slotTier(tape.totalFifths, this.cfg.bigWinTiers),
    };
    for (const fn of this.spinListeners) {
      try {
        fn(e);
      } catch (err) {
        this.ctx.log.error('onSpin listener failed', err);
      }
    }
  }

  /** SLOTS.md §8.4: at most one chaos event per spin, after settlement. */
  private chaos(l: Live): void {
    const c = chaosFor(l.def, l.tape);
    if (!c) return;
    const p = l.player;
    if (this.triggerListeners.size > 0) {
      // v1 listener contract (api.ts): the listener decides; slots only reports
      const ev: SlotsTriggerEvent = {
        player: p,
        event: c.event === 'jackpot' || c.event === 'chip_shower' ? 'jackpot' : c.event === 'golden_hour' ? 'golden_hour' : c.event === 'random_teleport' ? 'random_teleport' : 'mob_wave',
        symbol: c.event === 'jackpot' || c.event === 'chip_shower' ? 'star' : c.event === 'golden_hour' ? 'clock' : c.event === 'random_teleport' ? 'pearl' : 'creeper',
        tier: l.def.machine === 'overworld' ? 'copper' : l.def.machine === 'nether' ? 'gold' : 'netherite',
        dimensionId: l.session.table.dimension.id,
        location: l.session.table.location,
        jackpotAward: settlement(l.def, l.tape).poolChips || undefined,
        nearbyPlayers:
          c.event === 'jackpot' ? world.getAllPlayers().filter((q) => q.id !== p.id && q.dimension.id === p.dimension.id && dist2(q.location, p.location) <= JACKPOT_SHOWER_RADIUS ** 2) : undefined,
      };
      for (const fn of this.triggerListeners) {
        try {
          fn(ev);
        } catch (err) {
          this.ctx.log.error('onTrigger listener failed', err);
        }
      }
      return;
    }
    const chaos = this.ctx.services.get<ChaosApi>(CHAOS_SERVICE);
    if (!chaos?.isEnabled()) return;
    try {
      switch (c.event) {
        case 'jackpot':
          return chaos.jackpot(p);
        case 'chip_shower':
          chaos.trigger(p, 'chip_shower', { source: 'slots', ignoreCooldown: true });
          return;
        case 'golden_hour': {
          const r = chaos.startGoldenHour(p, 'slots');
          p.sendMessage(color('§6', t(r === 'started' ? 'msg.burmaldaholic.slots.golden_scatters' : 'msg.burmaldaholic.slots.golden_scatters_cooldown')));
          return;
        }
        default: {
          const r = chaos.trigger(p, c.event, { source: 'slots' });
          if (r === 'started' || r === 'deferred') p.sendMessage(color('§e', 'tumbles' in c ? t(`msg.burmaldaholic.slots.${c.flavour}`, lit(c.tumbles)) : t(`msg.burmaldaholic.slots.${c.flavour}`)));
        }
      }
    } catch (e) {
      this.ctx.log.error('slots chaos trigger failed', e);
    }
  }

  // ---- persistence of rounds (SLOTS.md §8.1) ----------------------------------------------------------

  private rounds(): Record<string, RoundRecord> {
    const r = worldSharded.read<Record<string, unknown>>(ROUNDS_PROP, {});
    const out: Record<string, RoundRecord> = {};
    for (const [k, v] of Object.entries(r)) if (isRoundRecord(v)) out[k] = v;
    return out;
  }

  private persistRound(key: string, rec: RoundRecord): void {
    const all = this.rounds();
    all[key] = rec;
    worldSharded.write(ROUNDS_PROP, all);
  }

  private deleteRound(key: string): void {
    const all = this.rounds();
    if (!(key in all)) return;
    delete all[key];
    worldSharded.write(ROUNDS_PROP, Object.keys(all).length ? all : undefined);
  }

  /**
   * Restart recovery: the wager return of every persisted round is settled by core (the ticket was drawn); here
   * the pool money of those rounds becomes owed to its player (paid on spawn). Unknown/corrupt records are
   * dropped with a log line.
   */
  recoverRounds(): void {
    const all = this.rounds();
    const keys = Object.keys(all);
    if (!keys.length) return;
    for (const k of keys) {
      const rec = all[k]!;
      const pool = rec.jackpotAwards.reduce((a, j) => a + j.chips, 0);
      this.payPool(rec.playerId, undefined, pool);
      this.ctx.log.info(`slots v2: round of ${rec.playerId} at ${k} settled from its tape after a restart (return ${rec.total}, pool ${pool})`);
    }
    worldSharded.write(ROUNDS_PROP, undefined);
  }

  /** v1 config keys still set are ignored after the cut-over: one warning lists them (SLOTS.md §11). */
  warnV1Keys(): void {
    try {
      const raw = world.getDynamicProperty('burmaldaholic:config');
      if (typeof raw !== 'string') return;
      const set = Object.keys(JSON.parse(raw) as object).filter((k) => REMOVED_V1_KEYS.includes(k));
      if (set.length) this.ctx.log.warn(`slots v2: v1 config keys are ignored: ${set.join(', ')}`);
    } catch {
      /* corrupt config is reported by core */
    }
  }

  /** Holders of the retired `three_sevens` are granted `top_five` (SLOTS.md §11). */
  grantTopFive(p: Player): void {
    if (isAchievement('three_sevens') && isAchievement('top_five') && this.ctx.achievements.has(p, 'three_sevens')) this.ctx.achievements.unlock(p, 'top_five', { silent: true });
  }

  /** Terminal window of a tape (the last frame of every presentation). */
  terminalWindow(tape: SpinTape): number[] {
    const def = this.def(tape.machine);
    return tape.bought ? [] : evaluateSpin(def, tape.stops, false).finalWindow.slice();
  }

  stateOf(s: TableSession): MachineState {
    return this.state(s);
  }

  buyPrice(m: MachineId, bet: number): number | undefined {
    const def = this.def(m);
    return def.buyPriceFifths > 0 && this.cfg.buyEnabled ? (def.buyPriceFifths * bet) / 5 : undefined;
  }

  bigWinTiers(): [number, number, number, number] {
    return this.cfg.bigWinTiers;
  }

  balance(p: Player): number {
    return this.ctx.economy.balance(p);
  }

  paytableFor(s: TableSession): Promise<void> {
    return this.paytable(s, this.state(s).machine);
  }

  /** Test hook: active rounds. */
  liveCount(): number {
    return this.live.size;
  }
}

/** A key of the stored config JSON (`burmaldaholic:config`), also for keys no longer in the catalog. */
function rawConfigValue(key: string): unknown {
  try {
    const raw = world.getDynamicProperty('burmaldaholic:config');
    return typeof raw === 'string' ? (JSON.parse(raw) as Record<string, unknown>)[key] : undefined;
  } catch {
    return undefined;
  }
}

const dist2 = (a: { x: number; y: number; z: number }, b: { x: number; y: number; z: number }): number => (a.x - b.x) ** 2 + (a.y - b.y) ** 2 + (a.z - b.z) ** 2;

// ---- B-L9 SlotHost adapter (structurally `present/ddui-form.ts` SlotHost) ------------------------------------

/**
 * The service side of the B-L9 machine form: the form calls `spin`, plays the round and calls `presented` exactly
 * once (gate or interrupt); Treasure Hunt picks come through `huntPick`. The server gate timer stays armed as the
 * authority (settling twice is a no-op), so a closed form or a disconnect never changes the result.
 */
export class SlotsV2Host<R> {
  constructor(
    private readonly svc: SlotsV2Service,
    private readonly session: TableSession,
    private readonly roundFromTape: NonNullable<SlotsV2Deps<R>['roundFromTape']>,
  ) {}

  private get st(): MachineState {
    return this.svc.stateOf(this.session);
  }

  get title(): Raw {
    return t(`gui.burmaldaholic.slots.machine.${this.st.machine}`);
  }

  jackpotLine(): Raw {
    const m = this.svc.meters(this.st.machine);
    return joinWith(lit(' · '), [4, 3, 2, 1].map((i) => t('gui.burmaldaholic.slots.jackpot.meter', t(`gui.burmaldaholic.slots.jackpot.tier.${TIER_KEYS[i]}`), chipsAcc(m[i]!))));
  }

  spinLabel(): Raw {
    return t('gui.burmaldaholic.slots.spin', chips(this.st.bet));
  }

  betLabel(): Raw {
    return t('gui.burmaldaholic.slots.bet', chips(this.st.bet));
  }

  buyLabel(): Raw | undefined {
    const price = this.svc.buyPrice(this.st.machine, this.st.bet);
    return price === undefined ? undefined : t('gui.burmaldaholic.slots.buy.button', chips(price));
  }

  spin(buy: boolean): SpinStartLike<R> | Raw {
    const l = this.svc.start(this.session, buy, true);
    if (!('ticket' in l)) {
      this.st.auto = undefined;
      return l;
    }
    if (this.st.auto) this.st.auto.left--;
    this.svc.begin(l);
    const last = this.st.last;
    const rest = last && !last.bought ? this.svc.terminalWindow(last) : undefined;
    return { round: this.roundFromTape(l.def, l.tape, { tier: slotTier(l.tape.totalFifths, this.svc.bigWinTiers()), rest }), timeline: l.view.timeline };
  }

  presented(_start: SpinStartLike<R>, interrupted: boolean): void {
    const l = this.svc.liveOf(this.session.playerId);
    if (l) this.svc.settle(l, interrupted ? 'skip' : 'gate');
  }

  huntPick(_start: SpinStartLike<R>, i: number): number | undefined {
    const l = this.svc.liveOf(this.session.playerId);
    return l ? this.svc.huntPick(l, i) : undefined;
  }

  betDown(): void {
    this.step(-1);
  }

  betUp(): void {
    this.step(1);
  }

  private step(d: number): void {
    const bets = this.svc.offered(this.session, this.st.machine);
    const i = bets.indexOf(this.st.bet);
    const next = bets[Math.max(0, Math.min(bets.length - 1, (i < 0 ? 0 : i) + d))];
    if (next !== undefined) this.st.bet = next;
  }

  auto(): void {
    detach(
      (async () => {
        const a = await new ClassicPresenter().autoplayDialog(this.session.player);
        if (a && a.kind === 'auto') this.st.auto = { left: a.count, startBalance: this.svc.balance(this.session.player), lossLimit: a.lossLimit, stopOnFeature: a.stopOnFeature, stopOnWin: a.stopOnWin, bet: this.st.bet };
      })(),
      () => undefined,
    );
  }

  paytable(): void {
    detach(this.svc.paytableFor(this.session), () => undefined);
  }

  leave(): void {
    this.session.leave();
  }

  toggleTurbo(): void {
    const p = this.session.player;
    writeJson(p, TURBO_PROP, !readJson<boolean>(p, TURBO_PROP, false));
  }

  autoplaying(): boolean {
    return this.st.auto !== undefined && this.st.auto.left > 0;
  }
}

const TIER_KEYS = ['', 'mini', 'minor', 'major', 'grand'];

// ---- classic fallback presenter (ActionForm + action bar; B-L9 replaces it with LiveForm/DDUI) --------------

const glyphBase: Record<MachineId, number> = { overworld: 0xe200, nether: 0xe210, end: 0xe220 };
const glyph = (m: MachineId, s: number): string => String.fromCodePoint(glyphBase[m] + s);

/** Three glyph rows of a window. */
export function windowRows(m: MachineId, w: readonly number[]): Raw {
  const rows: Raw[] = [];
  for (let y = 0; y < 3; y++) {
    let s = '§f';
    for (let r = 0; r < 5; r++) s += (r ? ' ' : '') + glyph(m, w[r * 3 + y]!);
    s += '§r';
    rows.push(lit(s));
  }
  return lines(...rows);
}

/**
 * Minimal presenter: machine ActionForm between spins; during a spin the action bar scrolls the REAL strip at
 * 10 rows/s and lands each reel at its `REEL_LAND` end (F2: the last three cells are the paid window).
 */
export class ClassicPresenter implements SlotsV2Presenter {
  private readonly sessions = new Map<string, presentation.PresentationSession>();

  async machine(v: MachineView): Promise<MachineAction | undefined> {
    const p = v.session.player;
    const body: Raw[] = [...v.notice];
    if (v.meters) body.push(lines(...[4, 3, 2, 1].map((i) => color('§6', t('gui.burmaldaholic.slots.jackpot.meter', t(`gui.burmaldaholic.slots.jackpot.tier.${['', 'mini', 'minor', 'major', 'grand'][i]}`), chipsAcc(v.meters![i]!))))));
    if (v.last?.stops.length) body.push(windowRows(v.machine, windowFromStops(v.def, v.last.stops)));
    if (v.last) body.push(resultLine(v.last));
    body.push(t('gui.burmaldaholic.slots.bet', chips(v.bet)));
    const f = new ActionFormData().title(t(`gui.burmaldaholic.slots.machine.${v.machine}`)).body(lines(...body));
    const acts: Array<MachineAction | 'down' | 'up'> = [];
    f.button(t('gui.burmaldaholic.slots.spin', chips(v.bet)));
    acts.push({ kind: 'spin' });
    const i = v.bets.indexOf(v.bet);
    if (i > 0) {
      f.button(t('gui.burmaldaholic.slots.bet_down'));
      acts.push({ kind: 'bet', bet: v.bets[i - 1]! });
    }
    if (i >= 0 && i < v.bets.length - 1) {
      f.button(t('gui.burmaldaholic.slots.bet_up'));
      acts.push({ kind: 'bet', bet: v.bets[i + 1]! });
    }
    if (v.buyPrice !== undefined) {
      f.button(t('gui.burmaldaholic.slots.buy.button', chips(v.buyPrice)));
      acts.push({ kind: 'buy' });
    }
    if (v.autoplay) {
      f.button(t('gui.burmaldaholic.slots.auto'));
      acts.push('down');
    }
    f.button(t('gui.burmaldaholic.slots.paytable.title'));
    acts.push({ kind: 'paytable' });
    f.button(t('gui.burmaldaholic.common.leave'));
    acts.push({ kind: 'leave' });
    const res = await showForm(p, f);
    if (!res || res.canceled || res.selection === undefined) return undefined;
    const a = acts[res.selection];
    if (a === 'down' || a === 'up') return this.autoplayForm(v);
    return a;
  }

  private autoplayForm(v: MachineView): Promise<MachineAction | undefined> {
    return this.autoplayDialog(v.session.player);
  }

  /** Autoplay dialog (count, mandatory loss limit, stop rules; SLOTS.md §6.4). */
  async autoplayDialog(player: Player): Promise<MachineAction | undefined> {
    const counts = [10, 25, 50, 100];
    const limits = [10, 25, 50, 100];
    const wins = [0, 10, 50, 100];
    const f = new ModalFormData()
      .title(t('gui.burmaldaholic.slots.auto.title'))
      .dropdown(t('gui.burmaldaholic.slots.auto.count'), counts.map((n) => lit(n)), { defaultValueIndex: 0 })
      .dropdown(t('gui.burmaldaholic.slots.auto.loss_limit'), limits.map((n) => lit('×' + n)), { defaultValueIndex: 1 })
      .toggle(t('gui.burmaldaholic.slots.auto.stop_feature'), { defaultValue: true })
      .dropdown(t('gui.burmaldaholic.slots.auto.stop_win'), wins.map((n) => (n ? lit('×' + n) : t('gui.burmaldaholic.slots.auto.off'))), { defaultValueIndex: 2 })
      .submitButton(t('gui.burmaldaholic.slots.auto.start'));
    const r = await showForm(player, f);
    if (!r || r.canceled || !r.formValues) return undefined;
    const [c, l, sf, w] = r.formValues as [number, number, boolean, number];
    return { kind: 'auto', count: counts[c] ?? 10, lossLimit: limits[l] ?? 25, stopOnFeature: sf, stopOnWin: wins[w] ?? 50 };
  }

  play(round: RoundView): void {
    const { session, tape, def, machine, timeline } = round;
    const p = session.player;
    const landEnd = [0, 0, 0, 0, 0];
    for (const b of timeline.beats) if (b.kind === SLOT_BEAT.REEL_LAND && b.group === 0) landEnd[b.lane] = b.at + b.dur;
    const stops = tape.stops;
    const final = tape.bought ? [] : windowFromStops(def, stops);
    const frame = (tMs: number): void => {
      if (!p.isValid || !stops.length) return;
      const w: number[] = [];
      for (let r = 0; r < 5; r++) {
        const remaining = Math.max(0, Math.ceil((landEnd[r]! - tMs) / 100)); // 10 rows/s, lands exactly on t_r
        for (let y = 0; y < 3; y++) w.push(remaining === 0 ? final[r * 3 + y]! : symbolAt(def, r, stops[r]! - remaining, y));
      }
      presentationHud(round, windowRows(machine, w));
    };
    const s = presentation.playTimeline(timeline, {
      beat: (b) => {
        if (!p.isValid) return;
        if (b.kind === SLOT_BEAT.REEL_LAND) p.playSound('random.click');
        if (b.kind === SLOT_BEAT.FS_INTRO) presentationHud(round, t('gui.burmaldaholic.slots.fs.awarded', lit(b.args[0]!)));
        if (b.kind === SLOT_BEAT.JACKPOT) p.playSound('random.levelup'); // a LOCAL beat after the gate (never clock S)
      },
      frame: (tMs) => {
        if (tMs <= Math.max(...landEnd) + 100) frame(tMs);
      },
      end: () => this.sessions.delete(session.playerId),
    }, { alive: () => p.isValid && session.isActive() });
    this.sessions.set(session.playerId, s);
  }

  settled(round: RoundView, _stop: AutoStop | undefined): void {
    const s = this.sessions.get(round.session.playerId);
    s?.stop();
    const p = round.session.player;
    if (!p.isValid) return;
    const lines_: Raw[] = [];
    if (round.tape.stops.length) lines_.push(windowRows(round.machine, windowFromStops(round.def, round.tape.stops)));
    lines_.push(resultLine(round.tape));
    presentationHud(round, lines(...lines_));
    const tier = slotTier(round.tape.totalFifths);
    if (tier === 'BIG' || tier === 'MEGA' || tier === 'EPIC') p.onScreenDisplay.setTitle(t(SLOT_TIER_WORDS[tier]), { subtitle: chips(tapeTotalChips(round.tape)), fadeInDuration: 5, stayDuration: 50, fadeOutDuration: 10 });
    for (const j of round.tape.jackpots) p.sendMessage(color('§6', t('msg.burmaldaholic.slots.jackpot_self', t(`gui.burmaldaholic.slots.jackpot.tier.${['', 'mini', 'minor', 'major', 'grand'][j.tier]}`), chipsAcc(j.chips))));
  }

  error(session: TableSession, error: Raw): void {
    if (session.player.isValid) session.player.sendMessage(color('§c', error));
  }
}

/** The HUD service arbitrates the action bar; the game channel is `slots.status`. */
function presentationHud(round: RoundView, raw: Raw): void {
  const p = round.session.player;
  if (p.isValid) hudSink?.(p, raw);
}

/** Action-bar writer installed by the module (ctx.hud). */
let hudSink: ((p: Player, raw: Raw) => void) | undefined;
export const setSlotsHud = (fn: (p: Player, raw: Raw) => void): void => {
  hudSink = fn;
};

/** "Win 120" / "Returned 40" / "No win this time" (no loss disguised as a win, SLOTS.md §1.4). */
export function resultLine(tape: SpinTape): Raw {
  const c = tapeTotalChips(tape);
  if (c <= 0) return color('§7', t('gui.burmaldaholic.slots.no_win'));
  if (c < tape.bet) return color('§7', t('gui.burmaldaholic.slots.returned', chips(c)));
  return color(tape.capHit ? '§6' : '§a', t(tape.capHit ? 'gui.burmaldaholic.slots.max_win' : 'gui.burmaldaholic.slots.win', chips(c)));
}

/** Module wiring of the v2 service (called by index.ts when `SLOTS_V2_ENABLED`). */
export function startSlotsV2(ctx: ModuleContext, presenter?: SlotsV2Presenter, deps: SlotsV2Deps = {}): SlotsV2Service {
  const svc = new SlotsV2Service(ctx, presenter, undefined, deps);
  setSlotsHud((p, raw) => ctx.hud.actionbar(p, HUD, raw, HudPriority.game, 60));
  svc.loadConfig();
  svc.loadPools();
  svc.recoverRounds();
  svc.warnV1Keys();
  ctx.tables.register({
    id: 'slots',
    seats: 1,
    canJoin: (p, table) => svc.canJoin(p, table),
    onOpen: (s) => svc.onOpen(s),
    onLeave: (s) => svc.onLeave(s),
  });
  world.afterEvents.playerSpawn.subscribe(
    ctx.guard((e) => {
      if (!e.initialSpawn) return;
      svc.payOwed(e.player);
      svc.grantTopFive(e.player);
    }),
  );
  ctx.config.onChange((key) => {
    if (key.startsWith('slots.')) svc.loadConfig();
  });
  ctx.admin.addAction({
    id: 'slots.reset_jackpots',
    label: t('gui.burmaldaholic.menu.admin.reset_jackpots'),
    run: (op) => {
      svc.resetJackpots();
      op.sendMessage(t('gui.burmaldaholic.menu.admin.done', t('gui.burmaldaholic.menu.admin.reset_jackpots')));
    },
  });
  if (svc.warnings.length) {
    ctx.admin.addAction({
      id: 'slots.rtp_warning',
      label: color('§c', t('gui.burmaldaholic.menu.admin.rtp_warning', t('gui.burmaldaholic.common.game.slots'), lit(svc.warnings.length))),
      run: (op) => {
        for (const w of svc.warnings) op.sendMessage(color('§c', lit(w)));
      },
    });
  }
  return svc;
}

