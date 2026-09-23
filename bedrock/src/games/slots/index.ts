/**
 * Slots module (GAME_DESIGN §8, UI.md §6): three machine tiers, progressive jackpots, the
 * §14 streak re-draw via ctx.odds.draw, and the chaos trigger stream (see ./api.ts).
 *
 * Flow: machine block (`burmaldaholic:table` {game: 'slots', variant: <tier>}) → machine form →
 * Spin: stake placed → outcome drawn (final) → pool updated → actionbar reel animation →
 * settle → result/effects → machine form again. Auto ×10 settles each spin at once and shows a
 * summary. Leaving mid-animation (walk away, casino off, broken block) settles immediately;
 * a disconnect settles in playerLeave (before the player object becomes invalid).
 */
import { type Player, system, world } from '@minecraft/server';
import { ActionFormData } from '@minecraft/server-ui';
import {
  type CasinoModule,
  type HouseRef,
  type ModuleContext,
  type Raw,
  type TableRef,
  type TableSession,
  type WagerTicket,
  BANK,
  HudPriority,
  chips,
  chipsAcc,
  color,
  join,
  lines,
  lit,
  mathRng,
  promptAmount,
  readJson,
  showForm,
  t,
  unit,
  worldJson,
  writeJson,
} from '../../core';
import { CHAOS_SERVICE } from '../../chaos/api';
import { JACKPOT_SHOWER_RADIUS, SLOTS_SERVICE, type SlotsApi, type SlotsHouse, type SlotsHouseResolver, type SlotsSpinEvent, type SlotsTriggerEvent } from './api';
import {
  AUTO_BIG_WIN_MULTIPLE,
  AUTO_SPINS,
  DEFAULT_LINE_BETS,
  type Grid,
  type LineBetConfig,
  PROGRESSIVE,
  type PoolState,
  SPECIAL_EVENT,
  type SlotTable,
  type SpecialSym,
  type SpinOutcome,
  type Sym,
  TIERS,
  TIER_LINES,
  type Tier,
  clampLineBet,
  drawGrid,
  isTier,
  lineBetRange,
  loadPool,
  machineMaxSpinBet,
  makeTable,
  newPool,
  resolveSpin,
  totalRtp,
  weightEntries,
  worstCaseReturn,
} from './logic';
import { gridRaw, machineName, paytableLines } from './render';

const POOL_PROP = 'burmaldaholic:slots.jackpot';
const BET_PROP = 'burmaldaholic:slots.line_bet';
const HUD_CHANNEL = 'slots.spin';
const FRAME_TICKS = 3;
const AUTO_GAP_TICKS = 20;

interface MachineState {
  tier: Tier;
  lineBet: number;
  last?: SpinOutcome;
  /** animation or auto-spin running: ignore re-opens */
  busy: boolean;
  stopAuto: boolean;
}

interface Pending {
  player: Player;
  session: TableSession;
  ticket: WagerTicket;
  outcome: SpinOutcome;
  tier: Tier;
  owned: boolean;
  anim?: number;
}

const wait = (ticks: number) => new Promise<void>((r) => system.runTimeout(() => r(), ticks));

class SlotsGame implements SlotsApi {
  private readonly pending = new Map<string, Pending>();
  private readonly triggerListeners = new Set<(e: SlotsTriggerEvent) => void>();
  private readonly spinListeners = new Set<(e: SlotsSpinEvent) => void>();
  private readonly tables = new Map<string, { table: SlotTable; rtp: number }>();
  private pools: Partial<Record<Tier, PoolState>> | undefined;
  private resolver: SlotsHouseResolver | undefined;

  constructor(private readonly ctx: ModuleContext) {}

  // ---- config / math ----------------------------------------------------------------------

  private lineCfg(tier: Tier): LineBetConfig {
    const c = this.ctx.config;
    return {
      minLineBet: tier === 'netherite' ? c.int('slots.netherite.minLineBet') : DEFAULT_LINE_BETS[tier].minLineBet,
      maxLineBet: c.int(`slots.${tier}.maxLineBet`),
    };
  }

  private contribution(tier: Tier): number {
    return PROGRESSIVE.includes(tier) ? this.ctx.config.num(`slots.jackpot.contribution.${tier}`) : 0;
  }

  private seed(tier: Tier): number {
    return PROGRESSIVE.includes(tier) ? this.ctx.config.int(`slots.jackpot.seed.${tier}`) : 0;
  }

  /** Machine math from config (cached; invalidated on any slots.* change). */
  machine(tier: Tier, owned: boolean): { table: SlotTable; rtp: number } {
    const key = `${tier}|${owned}`;
    let m = this.tables.get(key);
    if (!m) {
      const c = this.ctx.config;
      const pays = { ...c.json<Record<string, number>>(`slots.${tier}.pays`) };
      const progressive = PROGRESSIVE.includes(tier) && !owned;
      if (owned && PROGRESSIVE.includes(tier)) pays.star = c.num('slots.ownedStarPays');
      const table = makeTable({
        weights: c.json<Record<string, number>>(`slots.${tier}.weights`),
        pays,
        berryPartial: c.json<number[]>(`slots.${tier}.berryPartial`),
        lines: TIER_LINES[tier],
        progressive,
      });
      m = { table, rtp: totalRtp(table, progressive ? this.contribution(tier) : 0) };
      this.tables.set(key, m);
    }
    return m;
  }

  invalidate(): void {
    this.tables.clear();
  }

  /** RTP > 0.99 warnings (CONFIG.md slots.validateRtp). */
  rtpWarnings(): Raw[] {
    const out: Raw[] = [];
    for (const tier of TIERS) {
      for (const owned of PROGRESSIVE.includes(tier) ? [false, true] : [false]) {
        const { rtp, table } = this.machine(tier, owned);
        if (weightEntries(table).length === 0 || rtp > 0.99) {
          const pct = `${(rtp * 100).toFixed(2)} %`;
          out.push(t('gui.burmaldaholic.menu.admin.rtp_warning', machineName(tier), pct));
          this.ctx.log.warn(`slots ${tier}${owned ? ' (owned)' : ''}: RTP ${pct} > 99 % (weights/pays in config)`);
        }
      }
    }
    return out;
  }

  // ---- jackpot pools ------------------------------------------------------------------------

  private loadPools(): Partial<Record<Tier, PoolState>> {
    if (!this.pools) {
      const raw = worldJson.read<Partial<Record<Tier, PoolState>>>(POOL_PROP, {});
      this.pools = {};
      for (const tier of PROGRESSIVE) this.pools[tier] = loadPool(raw[tier], this.seed(tier));
      this.savePools();
    }
    return this.pools;
  }

  private savePools(): void {
    worldJson.write(POOL_PROP, this.pools);
  }

  pool(tier: Tier): PoolState {
    return this.loadPools()[tier] ?? newPool(this.seed(tier));
  }

  jackpotPool(tier: Tier): number {
    return PROGRESSIVE.includes(tier) ? this.pool(tier).pool : 0;
  }

  resetJackpots(tier?: Tier): void {
    const pools = this.loadPools();
    for (const x of PROGRESSIVE) if (!tier || tier === x) pools[x] = newPool(this.seed(x));
    this.savePools();
  }

  // ---- API ------------------------------------------------------------------------------------

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

  private houseAt(table: TableRef): SlotsHouse | undefined {
    try {
      return this.resolver?.(table.dimension.id, table.location);
    } catch (e) {
      this.ctx.log.error('slots house resolver failed', e);
      return undefined;
    }
  }

  // ---- table handler --------------------------------------------------------------------------

  tierOf(table: TableRef): Tier {
    if (isTier(table.variant)) return table.variant;
    const m = /slot_machine_(\w+)$/.exec(table.blockTypeId ?? '');
    return isTier(m?.[1]) ? m[1] : 'copper';
  }

  canJoin(player: Player, table: TableRef): Raw | undefined {
    if (!this.ctx.config.bool('slots.enabled')) return t('gui.burmaldaholic.error.disabled');
    const tier = this.tierOf(table);
    if (tier === 'netherite') {
      const min = this.ctx.config.int('slots.netherite.minVipTier');
      if (this.ctx.limits.tier(player) < min) return t('gui.burmaldaholic.error.vip_required', this.ctx.limits.tierName(min));
    }
    const h = this.houseAt(table);
    if (h?.closed) return t('gui.burmaldaholic.error.table_closed');
    if (h?.ownerId && h.ownerId === player.id) return t('gui.burmaldaholic.error.owner_cannot_play');
    return undefined;
  }

  private state(s: TableSession): MachineState {
    let st = s.data.slots as MachineState | undefined;
    if (!st) {
      const tier = this.tierOf(s.table);
      const saved = readJson<Partial<Record<Tier, number>>>(s.player, BET_PROP, {});
      st = { tier, lineBet: saved[tier] ?? this.lineCfg(tier).minLineBet, busy: false, stopAuto: false };
      s.data.slots = st;
    }
    return st;
  }

  private range(player: Player, tier: Tier): { min: number; max: number } {
    return lineBetRange(tier, this.lineCfg(tier), this.ctx.limits.tierMax(player));
  }

  onOpen(s: TableSession): void {
    const st = this.state(s);
    if (st.busy) return;
    void this.showMachine(s);
  }

  onLeave(s: TableSession): void {
    const st = s.data.slots as MachineState | undefined;
    if (st) st.stopAuto = true;
    const p = this.pending.get(s.playerId);
    if (p) this.finish(p, !s.player.isValid);
    this.ctx.hud.clear(s.player, HUD_CHANNEL);
  }

  /** Disconnect: settle while the player object is still valid (restricted execution). */
  onPlayerLeave(player: Player): void {
    const p = this.pending.get(player.id);
    if (p) this.finish(p, true);
  }

  // ---- forms ------------------------------------------------------------------------------------

  async showMachine(s: TableSession, notice: Raw[] = []): Promise<void> {
    if (!s.isActive()) return;
    const p = s.player;
    const st = this.state(s);
    const house = this.houseAt(s.table);
    const owned = house?.house.kind === 'bankroll';
    const { table } = this.machine(st.tier, owned);
    const r = this.range(p, st.tier);
    st.lineBet = clampLineBet(st.lineBet, r);
    const spinBet = st.lineBet * table.lines;

    const body: (Raw | undefined)[] = [...notice];
    if (table.progressive) body.push(color('§6', t('gui.burmaldaholic.slots.jackpot', this.jackpotPool(st.tier))));
    if (st.last) body.push(gridRaw(st.last.grid));
    else if (weightEntries(table).length) body.push(gridRaw(drawGrid(mathRng, table)));
    if (st.last) body.push(...this.resultLines(st.last));
    body.push(t('gui.burmaldaholic.slots.line_bet', chips(st.lineBet)), t('gui.burmaldaholic.slots.lines', table.lines));
    body.push(t('gui.burmaldaholic.common.total_bet', chips(spinBet)));
    body.push(t('gui.burmaldaholic.common.balance', chips(this.ctx.economy.balance(p))));

    const form = new ActionFormData()
      .title(machineName(st.tier))
      .body(lines(...body))
      .button(t('gui.burmaldaholic.slots.spin', chips(spinBet)), 'textures/items/emerald')
      .button(t('gui.burmaldaholic.slots.auto'), 'textures/items/clock_item')
      .button(t('gui.burmaldaholic.common.change_bet'), 'textures/items/gold_ingot')
      .button(t('gui.burmaldaholic.common.paytable'), 'textures/items/book_normal')
      .button(t('gui.burmaldaholic.common.leave'), 'textures/items/door_wood');
    const res = await showForm(p, form);
    if (!s.isActive() || st.busy) return;
    if (!res || res.canceled || res.selection === undefined) return s.leave();
    switch (res.selection) {
      case 0:
        return this.spin(s);
      case 1:
        return this.autoSpin(s);
      case 2:
        await this.changeBet(s);
        return this.showMachine(s);
      case 3:
        await this.paytable(s);
        return this.showMachine(s);
      default:
        return s.leave();
    }
  }

  private resultLines(o: SpinOutcome): Raw[] {
    const out: Raw[] = [];
    for (const w of o.wins) {
      if (w.payout <= 0) continue;
      out.push(color('§a', t('gui.burmaldaholic.slots.line_win', w.line, w.multiplier, chips(w.payout))));
    }
    if (o.jackpotAward > 0) out.push(color('§6', t('msg.burmaldaholic.slots.jackpot_self', chipsAcc(o.jackpotAward))));
    out.push(o.totalReturn > 0 ? color('§a', t('gui.burmaldaholic.slots.spin_total', chips(o.totalReturn))) : color('§7', t('gui.burmaldaholic.slots.no_win')));
    return out;
  }

  private async changeBet(s: TableSession): Promise<void> {
    const st = this.state(s);
    const r = this.range(s.player, st.tier);
    const lines_ = TIER_LINES[st.tier];
    const v = await promptAmount(s.player, {
      title: t('gui.burmaldaholic.common.change_bet'),
      info: [t('gui.burmaldaholic.slots.lines', lines_), t('gui.burmaldaholic.common.limits', chips(r.min), chips(Math.max(r.min, r.max)))],
      min: r.min,
      max: Math.max(r.min, r.max),
      default: st.lineBet,
      sliderLabel: t('gui.burmaldaholic.common.bet'),
    });
    if (v === undefined || !s.isActive()) return;
    st.lineBet = clampLineBet(v, r);
    const saved = readJson<Partial<Record<Tier, number>>>(s.player, BET_PROP, {});
    saved[st.tier] = st.lineBet;
    writeJson(s.player, BET_PROP, saved);
  }

  private async paytable(s: TableSession): Promise<void> {
    const st = this.state(s);
    const owned = this.houseAt(s.table)?.house.kind === 'bankroll';
    const form = new ActionFormData()
      .title(t('gui.burmaldaholic.common.paytable'))
      .body(lines(...paytableLines(this.machine(st.tier, owned).table)))
      .button(t('gui.burmaldaholic.common.back'));
    await showForm(s.player, form);
  }

  // ---- spinning --------------------------------------------------------------------------------

  /** Place the stake and draw the (final) outcome. Returns an error to show, or the pending round. */
  private start(s: TableSession): Pending | Raw {
    const p = s.player;
    const st = this.state(s);
    if (!this.ctx.isCasinoEnabled()) return t('gui.burmaldaholic.error.casino_off');
    if (!this.ctx.config.bool('slots.enabled')) return t('gui.burmaldaholic.error.disabled');
    const h = this.houseAt(s.table);
    if (h?.closed) return t('gui.burmaldaholic.error.table_closed');
    const owned = h?.house.kind === 'bankroll';
    const house: HouseRef = h?.house ?? BANK;
    const { table, rtp } = this.machine(st.tier, owned);
    if (weightEntries(table).length === 0) return t('gui.burmaldaholic.error.disabled');
    const cfg = this.lineCfg(st.tier);
    const lineBet = st.lineBet;
    const r = this.ctx.wagers.place(p, {
      game: 'slots',
      stake: { kind: 'chips', amount: lineBet * table.lines },
      limits: {
        min: cfg.minLineBet * table.lines,
        tableMax: cfg.maxLineBet * table.lines,
        minTier: st.tier === 'netherite' ? this.ctx.config.int('slots.netherite.minVipTier') : undefined,
      },
      house,
      worstCase: worstCaseReturn(table, lineBet),
      notify: false,
    });
    if (!r.ok) return r.error;
    const progressive = table.progressive;
    const outcome = resolveSpin({
      table,
      lineBet,
      rng: mathRng,
      draw: (drawFn, isLosing) => this.ctx.odds.draw(p.id, rtp, mathRng, drawFn, isLosing),
      jackpot: progressive
        ? { state: this.pool(st.tier), rate: this.contribution(st.tier), seed: this.seed(st.tier), maxSpinBet: machineMaxSpinBet(st.tier, cfg) }
        : undefined,
    });
    if (progressive && outcome.pool) {
      this.loadPools()[st.tier] = outcome.pool;
      this.savePools();
      if (outcome.toppedUp > 0) this.ctx.log.info(`bank topped up the ${st.tier} jackpot by ${outcome.toppedUp}`);
    }
    return { player: p, session: s, ticket: r.ticket, outcome, tier: st.tier, owned };
  }

  private spin(s: TableSession): void {
    const st = this.state(s);
    const started = this.start(s);
    if (!('ticket' in started)) return void this.showMachine(s, [color('§c', started)]);
    const pd = started;
    this.pending.set(s.playerId, pd);
    st.busy = true;
    const { table } = this.machine(pd.tier, pd.owned);
    const total = Math.max(10, this.ctx.config.int('slots.spinTicks'));
    const stops = [Math.floor(total * 0.5), Math.floor(total * 0.75), total];
    let elapsed = 0;
    const entries = weightEntries(table);
    s.player.playSound('random.click');
    pd.anim = system.runInterval(() => {
      if (!this.pending.has(s.playerId)) return;
      elapsed += FRAME_TICKS;
      const noise = drawGrid(mathRng, table, entries);
      const frame: Grid = noise.map((row, r) => row.map((sym, c): Sym => (elapsed >= stops[c]! ? pd.outcome.grid[r]![c]! : sym)));
      if (s.player.isValid) {
        this.ctx.hud.actionbar(s.player, HUD_CHANNEL, gridRaw(frame), HudPriority.game, 20);
        if (stops.some((x) => elapsed >= x && elapsed - FRAME_TICKS < x)) s.player.playSound('random.click');
      }
      if (elapsed >= total) {
        this.finish(pd, false);
        st.busy = false;
        void this.showMachine(s);
      }
    }, FRAME_TICKS);
  }

  /** Settle a pending spin; `quiet` = the player is leaving (no forms / chaos events). */
  private finish(pd: Pending, quiet: boolean): void {
    if (this.pending.get(pd.player.id) !== pd) return;
    this.pending.delete(pd.player.id);
    if (pd.anim !== undefined) system.clearRun(pd.anim);
    const st = pd.session.data.slots as MachineState | undefined;
    if (st) {
      st.last = pd.outcome;
      st.busy = false;
    }
    try {
      this.ctx.wagers.settle(pd.ticket, pd.player, pd.outcome.totalReturn);
    } catch (e) {
      this.ctx.log.error('slots settle failed', e);
    }
    this.afterSettle(pd.player, pd.session.table, pd.tier, pd.owned, pd.outcome, quiet);
  }

  private async autoSpin(s: TableSession): Promise<void> {
    const st = this.state(s);
    st.busy = true;
    st.stopAuto = false;
    let spins = 0;
    let bet = 0;
    let won = 0;
    let stop: Raw | undefined;
    for (let i = 0; i < AUTO_SPINS; i++) {
      if (!s.isActive() || st.stopAuto) break;
      const spinBet = st.lineBet * TIER_LINES[st.tier];
      if (this.ctx.economy.balance(s.player) < spinBet) {
        stop = t('gui.burmaldaholic.slots.auto_stopped_funds');
        break;
      }
      const started = this.start(s);
      if (!('ticket' in started)) {
        stop = started;
        break;
      }
      spins++;
      bet += started.outcome.spinBet;
      won += started.outcome.totalReturn;
      st.last = started.outcome;
      try {
        this.ctx.wagers.settle(started.ticket, s.player, started.outcome.totalReturn);
      } catch (e) {
        this.ctx.log.error('slots settle failed', e);
      }
      this.afterSettle(s.player, s.table, started.tier, started.owned, started.outcome, false);
      if (started.outcome.totalReturn >= AUTO_BIG_WIN_MULTIPLE * started.outcome.spinBet) {
        stop = t('gui.burmaldaholic.slots.auto_stopped_big_win');
        break;
      }
      if (i < AUTO_SPINS - 1) await wait(AUTO_GAP_TICKS);
    }
    st.busy = false;
    if (!s.isActive()) return;
    const notice: Raw[] = [];
    if (stop) notice.push(color('§e', stop));
    if (spins > 0) notice.push(t('gui.burmaldaholic.slots.auto_summary', unit('spin', spins), chips(bet), chips(won)));
    await wait(10);
    return this.showMachine(s, notice);
  }

  // ---- after a spin: feedback, events ---------------------------------------------------------

  private afterSettle(player: Player, table: TableRef, tier: Tier, owned: boolean, o: SpinOutcome, quiet: boolean): void {
    const valid = player.isValid;
    const threeSevens = o.wins.some((w) => w.symbol === 'seven' && w.kind === 'three');
    // server-wide jackpot announcement (also when the winner is leaving)
    if (o.jackpotAward > 0) {
      try {
        world.sendMessage(color('§6', t('msg.burmaldaholic.slots.jackpot_broadcast', lit(player.name), chipsAcc(o.jackpotAward), machineName(tier))));
      } catch (e) {
        this.ctx.log.error('jackpot broadcast failed', e);
      }
    }
    if (!quiet && valid) this.feedback(player, o, threeSevens);
    this.emitSpin({ player, tier, lineBet: o.lineBet, spinBet: o.spinBet, totalReturn: o.totalReturn, wins: o.wins.map((w) => ({ ...w })), jackpotAward: o.jackpotAward, threeSevens, owned });
    if (!quiet && valid && o.special) this.trigger(player, table, tier, o.special, o.jackpotAward);
  }

  private feedback(player: Player, o: SpinOutcome, threeSevens: boolean): void {
    const hud = this.ctx.hud;
    const summary = o.totalReturn > 0 ? color('§a', t('gui.burmaldaholic.slots.spin_total', chips(o.totalReturn))) : color('§7', t('gui.burmaldaholic.slots.no_win'));
    hud.actionbar(player, HUD_CHANNEL, lines(gridRaw(o.grid), summary), HudPriority.game, 60);
    if (o.jackpotAward > 0) {
      hud.title(player, color('§6', t('gui.burmaldaholic.slots.jackpot', o.jackpotAward)), t('msg.burmaldaholic.slots.jackpot_self', chipsAcc(o.jackpotAward)), 5, 80, 20);
      player.sendMessage(color('§6', t('msg.burmaldaholic.slots.jackpot_self', chipsAcc(o.jackpotAward))));
      player.playSound('random.levelup');
    } else if (threeSevens) {
      hud.title(player, color('§c', t('msg.burmaldaholic.slots.seven_title')), color('§a', t('gui.burmaldaholic.slots.spin_total', chips(o.totalReturn))));
      player.playSound('random.levelup');
    } else if (o.totalReturn > 0) {
      player.playSound('random.orb');
    }
    if (o.totalReturn > 0) player.sendMessage(join(machineIcon(), summary));
  }

  private emitSpin(e: SlotsSpinEvent): void {
    for (const l of this.spinListeners) {
      try {
        l(e);
      } catch (err) {
        this.ctx.log.error('onSpin listener failed', err);
      }
    }
  }

  /** §8.1: at most one chaos event per spin, after crediting; nothing when chaos is off. */
  private trigger(player: Player, table: TableRef, tier: Tier, special: SpecialSym, jackpotAward: number): void {
    const c = this.ctx.config;
    const event = SPECIAL_EVENT[special];
    const chaosOn = c.bool('chaos.enabled');
    if (chaosOn) {
      const gh = this.ctx.goldenHour;
      const before = gh.remainingTicks();
      const nearby =
        event === 'jackpot'
          ? world.getAllPlayers().filter((q) => q.id !== player.id && q.dimension.id === player.dimension.id && dist2(q.location, player.location) <= JACKPOT_SHOWER_RADIUS * JACKPOT_SHOWER_RADIUS)
          : undefined;
      const ev: SlotsTriggerEvent = {
        player,
        event,
        symbol: special,
        tier,
        dimensionId: table.dimension.id,
        location: table.location,
        jackpotAward: event === 'jackpot' ? jackpotAward : undefined,
        nearbyPlayers: nearby,
      };
      this.dispatch(ev);
      if (special === 'clock') {
        const started = gh.remainingTicks() > before;
        player.sendMessage(color('§6', t(started ? 'msg.burmaldaholic.slots.three_clocks' : 'msg.burmaldaholic.slots.three_clocks_cooldown')));
        return;
      }
    }
    if (!chaosOn || !c.bool(`chaos.event.${event === 'jackpot' ? 'diamond_rain' : event}.enabled`)) return;
    const key = { creeper: 'three_creepers', tnt: 'three_tnt', pearl: 'three_pearls' } as Partial<Record<SpecialSym, string>>;
    const k = key[special];
    if (k) player.sendMessage(color('§e', t(`msg.burmaldaholic.slots.${k}`)));
  }

  private dispatch(ev: SlotsTriggerEvent): void {
    if (this.triggerListeners.size > 0) {
      for (const l of this.triggerListeners) {
        try {
          l(ev);
        } catch (err) {
          this.ctx.log.error('onTrigger listener failed', err);
        }
      }
      return;
    }
    // Fallback: a chaos service exposing trigger(player, eventId).
    const chaos = this.ctx.services.get<{ trigger?: (p: Player, id: string) => unknown }>(CHAOS_SERVICE);
    if (typeof chaos?.trigger !== 'function') return;
    try {
      if (ev.event === 'jackpot') {
        chaos.trigger(ev.player, 'diamond_rain');
        for (const q of ev.nearbyPlayers ?? []) chaos.trigger(q, 'chip_shower');
      } else chaos.trigger(ev.player, ev.event);
    } catch (err) {
      this.ctx.log.error('chaos.trigger failed', err);
    }
  }
}

const dist2 = (a: { x: number; y: number; z: number }, b: { x: number; y: number; z: number }) => (a.x - b.x) ** 2 + (a.y - b.y) ** 2 + (a.z - b.z) ** 2;

/** Gold "slot" prefix for chat lines. */
const machineIcon = (): Raw => lit('§6» §r');

export const slotsModule: CasinoModule = {
  id: 'slots',
  onWorldLoad(ctx) {
    const game = new SlotsGame(ctx);
    ctx.services.provide<SlotsApi>(SLOTS_SERVICE, game);
    ctx.tables.register({
      id: 'slots',
      seats: 1,
      canJoin: (p, table) => game.canJoin(p, table),
      onOpen: (s) => game.onOpen(s),
      onLeave: (s) => game.onLeave(s),
    });
    world.beforeEvents.playerLeave.subscribe((e) => {
      try {
        game.onPlayerLeave(e.player);
      } catch (err) {
        ctx.log.error('slots disconnect settle failed', err);
      }
    });
    ctx.config.onChange((key) => {
      if (!key.startsWith('slots.')) return;
      game.invalidate();
      if (ctx.config.bool('slots.validateRtp') && /\.(weights|pays|berryPartial)$|ownedStarPays|contribution/.test(key)) game.rtpWarnings();
    });
    ctx.admin.addAction({
      id: 'slots.reset_jackpots',
      label: t('gui.burmaldaholic.menu.admin.reset_jackpots'),
      run: (op) => {
        game.resetJackpots();
        op.sendMessage(t('gui.burmaldaholic.menu.admin.done', t('gui.burmaldaholic.menu.admin.reset_jackpots')));
      },
    });
    if (ctx.config.bool('slots.validateRtp')) {
      const warnings = game.rtpWarnings();
      if (warnings.length) {
        ctx.admin.addAction({
          id: 'slots.rtp_warning',
          label: color('§c', warnings[0]!),
          run: (op) => {
            for (const w of game.rtpWarnings()) op.sendMessage(color('§c', w));
          },
        });
      }
    }
  },
};
