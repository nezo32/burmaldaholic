/**
 * Wager service: the generic round lifecycle every house-banked game uses (GAME_DESIGN §4.1):
 *   IDLE -> place() [STAKED: stake taken, bet locked, persisted] -> settle() [SETTLED: payout
 *   credited, streak/VIP/contract hooks fired]      (refund() cancels a STAKED round)
 * Stakes: chips, the held item, XP levels, temporary max hearts, Hardcore Soul Wager (§4.3-4.4).
 * Houses: the world bank or an owned casino bankroll (reservation rule §18.2).
 * Open rounds are persisted per player. After a server restart (GAME_DESIGN §4.1, review M1):
 *  - a round whose outcome was already DRAWN (the game called draw(): roulette ball, slot grid,
 *    dealt blackjack cards, craps point...) is SETTLED at that drawn result through the offline
 *    path (parked at world load, applied on the next join, msg.burmaldaholic.core.round_played_out).
 *    Quitting while a losing result animates therefore changes nothing.
 *  - a round without a draw is refunded on the player's next join (config
 *    core.roundTimeoutRefund) with msg.burmaldaholic.core.round_refunded.
 *
 * Integration hooks (installed by feature modules, applied to EVERY game automatically):
 *  - house resolver (multiplayer): which bankroll banks a round at a table (`tableKey`)
 *  - limits resolver (multiplayer): owner min/max narrowing
 *  - vetoes (loan Asset Freeze, owner can't play / closed / broke)
 * The table key defaults to the player's current table session.
 *
 * Offline settlement: settle/refund/record work when the player has disconnected (see
 * logic/offline.ts): chips/pawns are parked and applied on the next join, onSettled fires
 * then (with `deferred: true`). Calling settle/refund twice for a ticket is a no-op.
 */
import {
  EntityComponentTypes,
  type EntityEquippableComponent,
  type EntityHealthComponent,
  type EntityInventoryComponent,
  EquipmentSlot,
  ItemComponentTypes,
  type ItemDurabilityComponent,
  type ItemEnchantableComponent,
  ItemStack,
  type Player,
  system,
  world,
} from '@minecraft/server';
import { isCasinoEnabled } from './casino';
import type { ConfigService } from './config';
import { type Economy, type HouseRef, BANK, isPlayerBanked } from './economy';
import { clearSlot, giveItems, heldItem, itemAt } from './items';
import type { Limits, TableLimits } from './limits';
import { isChipAmount } from './logic/economy-math';
import { houseEdgeOf, theoreticalLoss } from './logic/house-edge';
import {
  type DeferredSettle,
  type OfflineEntry,
  hasDrawn,
  planRecovery,
  withAchievement,
  withItem,
  withMessage,
  withResolved,
  withSettled,
} from './logic/offline';
import { outcomeOfNet } from './logic/streak';
import { type Raw, chips, chipsAcc, t, unit } from './logic/rawtext';
import {
  type HeartPenalty,
  activeHearts,
  advanceDormancy,
  cappedMaxHealth,
  checkHeartStake,
  checkItemStake,
  checkXpStake,
  pawnSettlement,
  rebasePenalties,
  soulValue,
  xpAfterStake,
  xpStakeValue,
} from './logic/wager-math';
import { createLogger } from './log';
import { livePlayer, offlineStore } from './offline';
import { readJson, worldJson, worldTick, writeJson } from './store';
import type { StreakService } from './streak';

const log = createLogger('core.wagers');
const TICKETS_PROP = 'burmaldaholic:core.wagers';
const HEARTS_PROP = 'burmaldaholic:core.hearts';
const SOUL_CD_PROP = 'burmaldaholic:core.soul_cooldown';
const BOOT_PROP = 'burmaldaholic:core.boot';
/**
 * World-level copy of every open ticket with a drawn outcome, per player
 * (`<prefix><playerId>` = { [ticketId]: StoredTicket }). World properties can be written while
 * the player is offline, so a round drawn after its player disconnected is covered too.
 */
const DRAWN_PREFIX = 'burmaldaholic:core.drawn.';
/** Accumulated ticks casino mode was off (heart penalties pause, review M2). */
const DORMANT_PROP = 'burmaldaholic:core.dormant_ticks';
const ACTIVE_TICK_PROP = 'burmaldaholic:core.active_tick';
/** Player flag: a Soul Wager lost while offline, applied once casino mode is on (review M2). */
const SOUL_PENDING_PROP = 'burmaldaholic:core.soul_pending';
const TOTEM_ID = 'minecraft:totem_of_undying';
/** Pawn stake at an owned table (Java-only key in STRINGS terms; a manual line in lang/core). */
const PAWN_OWNED_TABLE = 'gui.burmaldaholic.error.pawn_owned_table';
/** Tag set on a player killed by a lost Soul Wager (Last Chance must not save them). */
export const SOUL_WAGER_TAG = 'burmaldaholic_core_soul_wager';

/** Game ids used for labels (`gui.burmaldaholic.common.game.<id>`) and RNG classification. */
export const GAME_IDS = ['blackjack', 'poker', 'slots', 'roulette', 'craps', 'coin_flip', 'wheel', 'scratch', 'plinko', 'dice_duel', 'baccarat', 'uth'] as const;
export type GameId = (typeof GAME_IDS)[number];
/** Games whose odds the streak may tilt (§14); the rest are "always honest". */
export const RNG_GAMES: readonly GameId[] = ['slots', 'wheel', 'plinko', 'scratch', 'coin_flip'];
export const gameLabel = (g: GameId): Raw => t(`gui.burmaldaholic.common.game.${g}`);

export type Stake =
  | { kind: 'chips'; amount: number }
  /** a hotbar/inventory stack (appraisal table, undamaged/unenchanted/unnamed); default: the held slot */
  | { kind: 'item'; slot?: number }
  | { kind: 'xp'; levels: number }
  | { kind: 'hearts'; hearts: number }
  | { kind: 'soul' };

export interface PlaceOptions {
  game: GameId;
  stake: Stake;
  /** Table limits checked against the player's VIP max (ignored with skipLimits). */
  limits?: TableLimits;
  /** Doubles/splits "may exceed the max" — use raise() instead; this skips min/max checks. */
  skipLimits?: boolean;
  /** Who banks the round. Default: the world bank. */
  house?: HouseRef;
  /**
   * Worst-case TOTAL return of this bet (for bankroll reservation). Default 2 × stake value.
   * E.g. blackjack 8 × bet + insurance, roulette max over the 37 outcomes (§18.2).
   */
  worstCase?: number;
  /** Allow item/XP/heart stakes (coin flip, dice duel vs house, wheel, roulette even-money). */
  pawnAllowed?: boolean;
  /** Allow the Hardcore Soul Wager (coin flip only). */
  soulAllowed?: boolean;
  /** Send the error to the player's chat (default true). Forms may prefer to show it inline. */
  notify?: boolean;
  /**
   * Table the round is played at (house resolver, owner limits, vetoes). Default: the key of
   * the player's current table session.
   */
  tableKey?: string;
  /** House edge of this bet for the theoretical loss (default: the game's, core/logic/house-edge). */
  houseEdge?: number;
}

/** Who banks a round at a table; undefined = the world bank. */
export type HouseResolver = (player: Player, game: GameId, tableKey: string | undefined) => HouseRef | undefined;
/** Narrow a game's table limits (owner min/max). Must be idempotent. */
export type LimitsResolver = (player: Player, game: GameId, tableKey: string | undefined, base: TableLimits) => TableLimits;
/** Refuse a new stake (Raw error) or allow it (undefined). */
export type WagerVeto = (player: Player, info: { game: GameId; tableKey: string | undefined; stake?: Stake }) => Raw | undefined;

/** A prepaid round (e.g. a bought scratch card): the stake was already paid elsewhere. */
export interface RecordOptions {
  game: GameId;
  /** chips the player paid for the round */
  staked: number;
  /** total return (credited by core) */
  totalReturn: number;
  house?: HouseRef;
  tableKey?: string;
  houseEdge?: number;
}

/** An open (STAKED) round. Treat as opaque; pass it back to settle/refund/raise. */
export interface WagerTicket {
  readonly id: string;
  readonly playerId: string;
  readonly game: GameId;
  readonly kind: Stake['kind'];
  /** stake value V in chips (all chips put at risk so far for chip stakes) */
  value: number;
  readonly house: HouseRef;
  reserved: number;
  /** pawn data */
  item?: { typeId: string; amount: number };
  xpRemoved?: number;
  xpLevels?: number;
  hearts?: number;
  boot: number;
  /** table the round is played at */
  readonly tableKey?: string;
  /** Σ stake × house edge so far (VIP cashback) */
  theo: number;
  /**
   * Total return of the round's outcome once it is drawn (set by draw(); persisted). A restart
   * settles the round at this result instead of refunding it.
   */
  drawn?: number;
  /** per-round data a game may keep with the ticket (not persisted) */
  data?: unknown;
}

/** A ticket as persisted (player property / drawn store). */
type StoredTicket = Omit<WagerTicket, 'data'>;

export type PlaceResult = { ok: true; ticket: WagerTicket } | { ok: false; error: Raw };

export interface SettledEvent {
  /** the (online) player; for a deferred event the Player object of their join */
  player: Player;
  playerId: string;
  game: GameId;
  /** chips put at risk (stake value V for pawns) — lifetime wagered for VIP */
  staked: number;
  /** total return (stake included) */
  totalReturn: number;
  /** totalReturn − staked */
  net: number;
  stakeKind: Stake['kind'];
  house: HouseRef;
  /** false for PvP rounds (poker pots, dice duels) reported with recordPvp */
  houseBanked: boolean;
  /** table the round was played at, if any */
  tableKey?: string;
  /** Σ stake × house edge (VIP cashback base; 0 for PvP) */
  theoreticalLoss: number;
  /** settled while the player was offline; fired on their next join */
  deferred?: boolean;
}
export type SettledListener = (e: SettledEvent) => void;

export class WagerService {
  private readonly open = new Map<string, WagerTicket>();
  private readonly listeners: SettledListener[] = [];
  private boot = 0;
  private seq = 0;
  private houseResolver: HouseResolver | undefined;
  private limitsResolver: LimitsResolver | undefined;
  private readonly vetoes: WagerVeto[] = [];
  private tableKeyOf: (player: Player) => string | undefined = () => undefined;
  private achievementSink: ((playerId: string, id: string) => void) | undefined;

  constructor(
    private readonly economy: Economy,
    private readonly limits: Limits,
    private readonly config: ConfigService,
    private readonly streaks: StreakService,
  ) {}

  onSettled(l: SettledListener): void {
    this.listeners.push(l);
  }

  // ---- integration hooks -----------------------------------------------------------------

  /** Multiplayer: bankroll of an owned table (applies to every game that does not pass `house`). */
  setHouseResolver(r: HouseResolver | undefined): void {
    this.houseResolver = r;
  }

  /** Multiplayer: owner min/max narrowing of every game's limits. */
  setLimitsResolver(r: LimitsResolver | undefined): void {
    this.limitsResolver = r;
  }

  /** Loan Asset Freeze, owned-table checks... Checked by place() (not raise/settle). */
  addVeto(v: WagerVeto): void {
    this.vetoes.push(v);
  }

  /** Core wiring: the player's current table key (tables service). */
  setTableKeyProvider(fn: (player: Player) => string | undefined): void {
    this.tableKeyOf = fn;
  }

  /** Core wiring: achievements (queued for offline players by the owner of the sink). */
  setAchievementSink(fn: (playerId: string, id: string) => void): void {
    this.achievementSink = fn;
  }

  private keyFor(player: Player, tableKey: string | undefined): string | undefined {
    if (tableKey !== undefined) return tableKey;
    try {
      return this.tableKeyOf(player);
    } catch {
      return undefined;
    }
  }

  /** Who banks a round of `game` at `tableKey` (default: the player's current table). */
  resolveHouse(player: Player, game: GameId, tableKey?: string): HouseRef {
    const key = this.keyFor(player, tableKey);
    try {
      return this.houseResolver?.(player, game, key) ?? BANK;
    } catch (e) {
      log.error('house resolver failed', e);
      return BANK;
    }
  }

  /** The game's limits after owner narrowing: use it for bet prompts so they match place(). */
  limitsFor(player: Player, game: GameId, base: TableLimits = {}, tableKey?: string): TableLimits {
    const key = this.keyFor(player, tableKey);
    try {
      return this.limitsResolver ? this.limitsResolver(player, game, key, base) : base;
    } catch (e) {
      log.error('limits resolver failed', e);
      return base;
    }
  }

  /**
   * Whether the player may stake at all right now (Asset Freeze, owner at own table, closed or
   * broke house). Also for PvP entry points (poker buy-in, dice duels) and canJoin.
   */
  check(player: Player, game: GameId, tableKey?: string, stake?: Stake): Raw | undefined {
    const key = this.keyFor(player, tableKey);
    for (const v of this.vetoes) {
      try {
        const err = v(player, { game, tableKey: key, stake });
        if (err) return err;
      } catch (e) {
        log.error('wager veto failed', e);
      }
    }
    // Built-in rule (GAME_DESIGN §4.3 ⚠ CHANGED, review m4, both editions): pawn stakes (item,
    // XP, hearts, soul) are house-only. At a table or machine linked to a player-owned casino
    // (the house resolver names a bankroll) only chips are accepted: the owner's bankroll cannot
    // hold items, levels or hearts. Checked after the module vetoes so "owner can't play",
    // "closed" and "broke" keep their own messages.
    if (stake && stake.kind !== 'chips' && this.resolveHouse(player, game, key).kind !== 'bank') return t(PAWN_OWNED_TABLE);
    return undefined;
  }

  // ---- lifecycle -----------------------------------------------------------------------

  /** Called by core at world load. */
  start(): void {
    this.boot = (worldJson.read<number>(BOOT_PROP, 0) || 0) + 1;
    worldJson.write(BOOT_PROP, this.boot);
    this.economy.resetReservations();
    this.parkDrawnRounds();
    for (const p of world.getAllPlayers()) this.join(p);
    world.afterEvents.playerSpawn.subscribe((e) => {
      if (e.initialSpawn) this.join(e.player);
      if (this.activeNow()) this.enforceHearts(e.player);
    });
    // GAME_DESIGN §2.1: while casino mode is off the mod is dormant, so lost hearts are not
    // enforced (their expiry moves forward by the dormant time) and a Soul Wager lost while
    // offline waits (review M2).
    system.runInterval(() => {
      if (!this.activeNow()) return;
      for (const p of world.getAllPlayers()) {
        this.enforceHearts(p);
        this.applyPendingSoul(p);
      }
    }, 10);
  }

  /** Casino mode on? Also advances the dormancy counter when it just came back on. */
  private activeNow(): boolean {
    if (!isCasinoEnabled()) return false;
    const now = worldTick();
    const next = advanceDormancy({ last: worldJson.read<number | undefined>(ACTIVE_TICK_PROP, undefined), total: this.dormant() }, now);
    worldJson.write(ACTIVE_TICK_PROP, next.last);
    if (next.total !== this.dormant()) worldJson.write(DORMANT_PROP, next.total);
    return true;
  }

  /** Ticks casino mode has been off in total (heart penalty clock). */
  private dormant(): number {
    const v = worldJson.read<number>(DORMANT_PROP, 0);
    return typeof v === 'number' && Number.isFinite(v) ? v : 0;
  }

  /** Open rounds of a player (this session only). */
  openFor(player: Player): WagerTicket[] {
    return [...this.open.values()].filter((w) => w.playerId === player.id);
  }

  /**
   * Take a stake and open a round. Validates VIP/table limits, funds, pawn rules and bankroll
   * exposure. On failure nothing is taken and `error` explains why (also sent to chat unless
   * notify=false).
   */
  place(player: Player, o: PlaceOptions): PlaceResult {
    const r = this.tryPlace(player, o);
    if (!r.ok && o.notify !== false) player.sendMessage(r.error);
    return r;
  }

  private tryPlace(player: Player, o: PlaceOptions): PlaceResult {
    const fail = (error: Raw): PlaceResult => ({ ok: false, error });
    const tableKey = this.keyFor(player, o.tableKey);
    const veto = this.check(player, o.game, tableKey, o.stake);
    if (veto) return fail(veto);
    const house = o.house ?? this.resolveHouse(player, o.game, tableKey);
    const limits = o.limits || !o.skipLimits ? this.limitsFor(player, o.game, o.limits ?? {}, tableKey) : undefined;
    const edge = typeof o.houseEdge === 'number' && o.houseEdge >= 0 ? o.houseEdge : houseEdgeOf(o.game);
    const s = o.stake;
    if (s.kind !== 'chips') {
      if (s.kind === 'soul' ? !o.soulAllowed : !o.pawnAllowed) return fail(t('gui.burmaldaholic.error.invalid_bet_position'));
      // House-only (§4.3, review m4): an explicit bankroll house is refused like an owned table.
      if (house.kind !== 'bank') return fail(t(PAWN_OWNED_TABLE));
      if (s.kind !== 'soul' && !this.config.bool('wager.pawnEnabled')) return fail(t('gui.burmaldaholic.error.disabled'));
    }
    const tierMax = this.limits.tierMax(player, limits?.tierMultiplier);
    const ticket: WagerTicket = { id: `${this.boot}.${++this.seq}`, playerId: player.id, game: o.game, kind: s.kind, value: 0, house, reserved: 0, boot: this.boot, tableKey, theo: 0 };

    // 1. Compute the stake value and validate (nothing taken yet).
    let value: number;
    let take: () => void = () => {};
    switch (s.kind) {
      case 'chips': {
        value = Math.floor(s.amount);
        if (!o.skipLimits) {
          const err = this.limits.check(player, value, limits, this.economy.balance(player));
          if (err) return fail(err);
        } else if (value > this.economy.balance(player) || value <= 0) {
          return fail(t('gui.burmaldaholic.error.insufficient_funds', chips(this.economy.balance(player))));
        }
        take = () => void this.economy.debit(player, value, `${o.game}.stake`);
        break;
      }
      case 'item': {
        if (!this.config.bool('wager.items.enabled')) return fail(t('gui.burmaldaholic.error.disabled'));
        const { stack, slot } = s.slot === undefined ? heldItem(player) : itemAt(player, s.slot);
        if (!stack) return fail(t('gui.burmaldaholic.error.pawn_not_accepted'));
        const short = stack.typeId.replace(/^minecraft:/, '');
        const key = `wager.appraisal.${short}`;
        const appraisal = stack.typeId.startsWith('minecraft:') && this.config.def(key) ? this.config.int(key) : 0;
        const chk = checkItemStake({ appraisal, count: stack.amount, damaged: isDamaged(stack), enchanted: isEnchanted(stack), named: !!stack.nameTag });
        if (!chk.ok) return fail(t(chk.key));
        value = chk.value ?? 0;
        if (value > tierMax) return fail(t('gui.burmaldaholic.error.pawn_too_valuable', chips(value)));
        ticket.item = { typeId: stack.typeId, amount: stack.amount };
        take = () => clearSlot(player, slot);
        break;
      }
      case 'xp': {
        if (!this.config.bool('wager.xp.enabled')) return fail(t('gui.burmaldaholic.error.disabled'));
        const chk = checkXpStake(player.level, s.levels, this.config.int('wager.xp.maxLevels'));
        if (!chk.ok) return fail(t(chk.key));
        value = xpStakeValue(player.level, s.levels, this.config.int('wager.xp.pointsPerChip'));
        if (value > tierMax) return fail(t('gui.burmaldaholic.error.pawn_too_valuable', chips(value)));
        if (value < 1) return fail(t('gui.burmaldaholic.error.invalid_amount'));
        take = () => {
          // Whole levels only; the progress towards the next level stays (§4.3.2, review m3).
          const before = player.getTotalXp();
          const target = xpAfterStake(before, player.level, s.levels);
          player.resetLevel();
          player.addExperience(target);
          ticket.xpRemoved = before - target;
          ticket.xpLevels = s.levels;
        };
        break;
      }
      case 'hearts': {
        if (!this.config.bool('wager.hearts.enabled')) return fail(t('gui.burmaldaholic.error.disabled'));
        const chk = checkHeartStake(s.hearts, {
          maxPerBet: this.config.int('wager.hearts.maxPerBet'),
          maxTotal: this.config.int('wager.hearts.maxTotal'),
          active: activeHearts(this.penalties(player), worldTick()),
          baseMaxHealth: baseMaxHealth(player),
        });
        if (!chk.ok) return fail(t(chk.key));
        value = s.hearts * this.config.int('wager.hearts.valuePerHeart');
        if (value > tierMax) return fail(t('gui.burmaldaholic.error.pawn_too_valuable', chips(value)));
        ticket.hearts = s.hearts;
        break;
      }
      case 'soul': {
        if (!world.isHardcore || !this.config.bool('wager.hardcoreSoulWager')) return fail(t('gui.burmaldaholic.error.disabled'));
        const cd = (player.getDynamicProperty(SOUL_CD_PROP) as number | undefined) ?? 0;
        if (worldTick() < cd) return fail(t('msg.burmaldaholic.wager.soul_cooldown', unitTime(cd - worldTick())));
        value = soulValue(this.economy.balance(player), this.config.int('wager.soul.minValue'));
        break;
      }
    }
    ticket.value = value;
    ticket.theo = theoreticalLoss(value, edge);

    // 2. Bankroll exposure.
    if (house.kind === 'bankroll') {
      const wc = Math.ceil(o.worstCase ?? value * 2);
      if (!this.economy.reserve(house.id, wc)) return fail(t('gui.burmaldaholic.error.exposure'));
      ticket.reserved = wc;
    }

    // 3. Take it and persist.
    take();
    this.open.set(ticket.id, ticket);
    this.persist(player);
    return { ok: true, ticket };
  }

  /**
   * Add chips to an open chip stake (double down, split, insurance, craps odds). Not limited
   * by the max bet (§6.4) but by the balance and the bankroll exposure.
   */
  raise(ticket: WagerTicket, player: Player, amount: number, extraWorstCase = amount * 2, houseEdge?: number): boolean {
    // Only a positive whole number of chips (review m9: a negative raise would credit the player).
    if (!isChipAmount(amount) || !(extraWorstCase >= 0)) return false;
    if (ticket.kind !== 'chips' || !this.open.has(ticket.id)) return false;
    if (ticket.house.kind === 'bankroll' && !this.economy.reserve(ticket.house.id, extraWorstCase)) {
      player.sendMessage(t('gui.burmaldaholic.error.exposure'));
      return false;
    }
    if (!this.economy.charge(player, amount, `${ticket.game}.raise`)) {
      if (ticket.house.kind === 'bankroll') this.economy.release(ticket.house.id, extraWorstCase);
      return false;
    }
    ticket.value += Math.floor(amount);
    ticket.theo += theoreticalLoss(Math.floor(amount), typeof houseEdge === 'number' && houseEdge >= 0 ? houseEdge : houseEdgeOf(ticket.game));
    if (ticket.house.kind === 'bankroll') ticket.reserved += extraWorstCase;
    // A raise keeps the drawn result (the game draws again with the new outcome); the stored
    // copy must carry the new stake so a restart settles the chips actually at risk.
    if (hasDrawn(ticket)) this.storeDrawn(ticket);
    this.persist(player);
    return true;
  }

  /**
   * The round's outcome is now drawn (GAME_DESIGN §4.1, review M1): record its total return
   * (same meaning as settle's `totalReturn`) on the ticket and persist it BEFORE the result is
   * shown or animated. If the server stops before settle(), the round is settled at this result
   * on restart (never refunded). Call again whenever the outcome changes (a new card, a raise).
   * Offline-safe; a no-op for a closed ticket.
   */
  draw(ticket: WagerTicket, totalReturn: number): void {
    if (!this.open.has(ticket.id)) return;
    const ret = Math.max(0, Math.floor(totalReturn));
    if (!Number.isFinite(ret)) return;
    ticket.drawn = ret;
    this.storeDrawn(ticket);
    const live = livePlayer(undefined, ticket.playerId);
    if (live) this.persist(live);
  }

  /**
   * Settle a round. `totalReturn` = everything the player gets back INCLUDING the stake, in
   * chips (0 = lost, stake = push, 2 × stake = 1:1 win). Pawn stakes are returned on a
   * win/push and forfeited on a loss. `player` may have disconnected: the result is then
   * parked and applied on their next join (onSettled fires then, `deferred: true`).
   * Returns undefined when the ticket was already settled/refunded.
   */
  settle(ticket: WagerTicket, player: Player | undefined, totalReturn: number): SettledEvent | undefined {
    if (!this.open.delete(ticket.id)) return undefined;
    this.dropDrawn(ticket);
    const ret = Math.max(0, Math.floor(totalReturn));
    const staked = ticket.value;
    const net = ret - staked;
    const live = livePlayer(player, ticket.playerId);
    if (ticket.house.kind === 'bankroll' && ticket.kind === 'chips') {
      this.economy.settleBankroll(ticket.house.id, { reserved: ticket.reserved, stake: ticket.value, payout: ret });
    }
    const base: DeferredSettle = { game: ticket.game, staked, totalReturn: ret, stakeKind: ticket.kind, house: ticket.house, tableKey: ticket.tableKey, theoreticalLoss: ticket.theo };
    if (!live) {
      offlineStore.update(ticket.playerId, (e) => withSettled(withResolved(this.settleOffline(e, ticket, ret), ticket.id), base));
      log.info(`settled ${ticket.game} round ${ticket.id} of offline player ${ticket.playerId} (return ${ret})`);
      return { ...base, game: ticket.game, stakeKind: ticket.kind, player: player as Player, playerId: ticket.playerId, net, houseBanked: !isPlayerBanked(ticket.house), deferred: true };
    }
    try {
      if (ticket.kind === 'chips') {
        if (ret > 0) this.economy.credit(live, ret, `${ticket.game}.payout`);
      } else this.settlePawn(ticket, live, ret);
    } finally {
      this.persist(live);
    }
    const ev = this.finish(live, base, true);
    this.announce(ev);
    return ev;
  }

  /** Cancel an open round and give the stake back (no streak/VIP effect). Offline-safe. */
  refund(ticket: WagerTicket, player: Player | undefined): void {
    if (!this.open.delete(ticket.id)) return;
    this.dropDrawn(ticket);
    const live = livePlayer(player, ticket.playerId);
    if (ticket.house.kind === 'bankroll' && ticket.boot === this.boot) this.economy.release(ticket.house.id, ticket.reserved);
    if (!live) {
      offlineStore.update(ticket.playerId, (e) => withResolved(this.refundOffline(e, ticket), ticket.id));
      return;
    }
    this.refundTo(ticket, live);
    this.persist(live);
  }

  /**
   * Close a round because casino mode turned off (GAME_DESIGN §4.1 ⚠ CHANGED, both editions;
   * §2.1 dormancy never cancels a decided round): a round whose outcome is already drawn
   * (draw() was called) is SETTLED at that persisted result, exactly like a restart would; only
   * a round without a draw is refunded. Offline-safe. Returns what happened, or undefined when
   * the ticket was already closed.
   */
  closeOut(ticket: WagerTicket, player: Player | undefined): 'settled' | 'refunded' | undefined {
    if (!this.open.has(ticket.id)) return undefined;
    if (hasDrawn(ticket)) {
      this.settle(ticket, player, ticket.drawn);
      return 'settled';
    }
    this.refund(ticket, player);
    return 'refunded';
  }

  /**
   * Record a prepaid round (the stake was paid earlier, e.g. a bought scratch card): credits
   * `totalReturn`, updates the streak, announces big wins and fires onSettled. No limits,
   * no vetoes (the purchase already happened). Offline-safe.
   */
  record(player: Player, o: RecordOptions): SettledEvent {
    const staked = Math.max(0, Math.floor(o.staked));
    const ret = Math.max(0, Math.floor(o.totalReturn));
    const edge = typeof o.houseEdge === 'number' && o.houseEdge >= 0 ? o.houseEdge : houseEdgeOf(o.game);
    const house = o.house ?? BANK;
    const base: DeferredSettle = { game: o.game, staked, totalReturn: ret, stakeKind: 'chips', house, tableKey: o.tableKey, theoreticalLoss: theoreticalLoss(staked, edge) };
    if (house.kind === 'bankroll') {
      // The prepaid stake went to the bank: move the house result into the bankroll.
      this.economy.transact([{ account: { bankroll: house.id }, delta: staked - ret }, { account: 'bank', delta: ret - staked }], `${o.game}.record`);
    }
    const pid = player.isValid ? player.id : safeId(player);
    const live = pid ? livePlayer(player, pid) : undefined;
    if (!live) {
      if (pid) offlineStore.update(pid, (e) => withSettled({ ...e, chips: e.chips + ret }, base));
      else log.warn(`${o.game}: prepaid round of an unknown offline player lost (${ret})`);
      return { ...base, game: o.game, stakeKind: 'chips', player, playerId: pid ?? '', net: ret - staked, houseBanked: !isPlayerBanked(base.house), deferred: true };
    }
    if (ret > 0) this.economy.credit(live, ret, `${o.game}.payout`);
    const ev = this.finish(live, base, true);
    this.announce(ev);
    return ev;
  }

  /**
   * Report a settled PvP round (poker hand, dice duel) that moved chips with
   * economy.transact: updates the streak and fires onSettled (houseBanked=false).
   */
  recordPvp(player: Player, game: GameId, staked: number, net: number): void {
    if (!player.isValid) return;
    if (staked >= 1) this.streaks.recordFor(player, outcomeOfNet(net));
    this.emit({ player, playerId: player.id, game, staked, totalReturn: staked + net, net, stakeKind: 'chips', house: BANK, houseBanked: false, theoreticalLoss: 0 });
  }

  /**
   * Send a message to a player now, or on their next join when offline (e.g. "your bets were
   * played out: +40 chips").
   */
  tell(playerId: string, message: Raw): void {
    const p = livePlayer(undefined, playerId);
    if (p) p.sendMessage(message);
    else offlineStore.update(playerId, (e) => withMessage(e, message));
  }

  /** Streak + onSettled for a live player. */
  private finish(player: Player, d: DeferredSettle, houseBanked: boolean, deferred = false): SettledEvent {
    const net = d.totalReturn - d.staked;
    if (d.staked >= 1) this.streaks.recordFor(player, outcomeOfNet(net));
    const ev: SettledEvent = {
      player,
      playerId: player.id,
      game: d.game as GameId,
      staked: d.staked,
      totalReturn: d.totalReturn,
      net,
      stakeKind: d.stakeKind as Stake['kind'],
      house: d.house,
      houseBanked: houseBanked && !isPlayerBanked(d.house),
      tableKey: d.tableKey,
      theoreticalLoss: d.theoreticalLoss,
      deferred: deferred || undefined,
    };
    this.emit(ev);
    return ev;
  }

  // ---- internals -----------------------------------------------------------------------

  private settlePawn(ticket: WagerTicket, player: Player, ret: number): void {
    const { returnPawn, chips: paid } = pawnSettlement(ticket.value, ret);
    if (ticket.kind === 'soul') {
      if (returnPawn) {
        if (paid > 0) this.economy.credit(player, paid, `${ticket.game}.soul`);
      } else this.killBySoulWager(player);
      player.setDynamicProperty(SOUL_CD_PROP, worldTick() + this.config.int('wager.soul.cooldownTicks'));
      return;
    }
    if (paid > 0) this.economy.credit(player, paid, `${ticket.game}.pawn`);
    if (returnPawn) {
      this.returnPawn(ticket, player);
      if (ticket.item) player.sendMessage(t('msg.burmaldaholic.wager.item_returned', itemName(ticket.item.typeId), chipsAcc(paid)));
      return;
    }
    if (ticket.item) player.sendMessage(t('msg.burmaldaholic.wager.item_lost', itemName(ticket.item.typeId)));
    if (ticket.xpLevels) player.sendMessage(t('msg.burmaldaholic.wager.xp_lost', unit('level', ticket.xpLevels)));
    if (ticket.hearts) {
      const list = this.penalties(player).filter((p) => p.until > worldTick());
      list.push({ hearts: ticket.hearts, until: worldTick() + this.config.int('wager.hearts.durationTicks'), d: this.dormant() });
      writeJson(player, HEARTS_PROP, list);
      player.sendMessage(t('msg.burmaldaholic.wager.hearts_lost', unit('heart', ticket.hearts)));
      this.enforceHearts(player);
    }
  }

  private returnPawn(ticket: Pick<WagerTicket, 'item' | 'xpRemoved'>, player: Player): void {
    if (ticket.item) giveItems(player, ticket.item.typeId, ticket.item.amount);
    if (ticket.xpRemoved) player.addExperience(ticket.xpRemoved);
  }

  private refundTo(ticket: WagerTicket, player: Player): void {
    if (ticket.kind === 'chips') this.economy.credit(player, ticket.value, `${ticket.game}.refund`);
    else this.returnPawn(ticket, player);
  }

  // ---- offline settlement ----------------------------------------------------------------

  /** Park the result of a round for an offline player (pure on the entry, except config reads). */
  private settleOffline(e: OfflineEntry, ticket: StoredTicket, ret: number): OfflineEntry {
    if (ticket.kind === 'chips') return { ...e, chips: e.chips + ret };
    const { returnPawn, chips: paid } = pawnSettlement(ticket.value, ret);
    let n: OfflineEntry = { ...e, chips: e.chips + paid };
    if (ticket.kind === 'soul') {
      if (!returnPawn) n.soulDeath = true;
      n.soulCooldown = worldTick() + this.config.int('wager.soul.cooldownTicks');
      return n;
    }
    if (returnPawn) return this.refundOffline(n, ticket, false);
    if (ticket.hearts) n = { ...n, hearts: [...n.hearts, { hearts: ticket.hearts, until: worldTick() + this.config.int('wager.hearts.durationTicks'), d: this.dormant() }] };
    return n;
  }

  private refundOffline(e: OfflineEntry, ticket: StoredTicket, chipsToo = true): OfflineEntry {
    if (ticket.kind === 'chips') return chipsToo ? { ...e, chips: e.chips + ticket.value } : e;
    let n = e;
    if (ticket.item) n = withItem(n, ticket.item.typeId, ticket.item.amount);
    if (ticket.xpRemoved) n = { ...n, xp: n.xp + ticket.xpRemoved };
    return n;
  }

  /** Apply what was parked while the player was offline. Returns the resolved ticket ids. */
  private applyOffline(player: Player): string[] {
    const e = offlineStore.take(player.id);
    if (e.chips > 0) this.economy.credit(player, e.chips, 'core.offline');
    for (const i of e.items) giveItems(player, i.typeId, i.amount);
    if (e.xp > 0) player.addExperience(e.xp);
    if (e.hearts.length) {
      const list = this.penalties(player).filter((p) => p.until > worldTick());
      writeJson(player, HEARTS_PROP, rebasePenalties([...list, ...e.hearts], this.dormant()).list);
      if (isCasinoEnabled()) this.enforceHearts(player);
    }
    if (e.soulCooldown) player.setDynamicProperty(SOUL_CD_PROP, e.soulCooldown);
    for (const m of e.messages) player.sendMessage(m as Raw);
    for (const d of e.settled) this.finish(player, d, true, true);
    for (const id of e.achievements) this.achievementSink?.(player.id, id);
    if (e.soulDeath) {
      // Kept on the player until casino mode is on (a dormant mod kills nobody, review M2).
      player.setDynamicProperty(SOUL_PENDING_PROP, true);
      this.applyPendingSoul(player);
    }
    return e.resolved;
  }

  /** A Soul Wager lost while offline: the death happens once the player is on and the casino open. */
  private applyPendingSoul(player: Player): void {
    if (player.getDynamicProperty(SOUL_PENDING_PROP) !== true || !isCasinoEnabled()) return;
    // The flag is cleared only when the death happens, so logging out during the delay keeps it pending.
    system.runTimeout(() => {
      if (!player.isValid || !isCasinoEnabled()) return;
      if (player.getDynamicProperty(SOUL_PENDING_PROP) !== true) return;
      player.setDynamicProperty(SOUL_PENDING_PROP, undefined);
      this.killBySoulWager(player);
    }, 40);
  }

  /** First spawn: offline results, then refund rounds of an earlier server run (§4.1). */
  private join(player: Player): void {
    let resolved: string[] = [];
    try {
      resolved = this.applyOffline(player);
    } catch (err) {
      log.error(`offline results of ${player.name} failed`, err);
    }
    this.recover(player, resolved);
  }

  /**
   * GAME_DESIGN §4.4: the death bypasses totems. Bedrock script has no custom damage type and a
   * Totem of Undying may pop on kill(), so totems are first moved out of both hands into the
   * inventory (dropped at the player's feet if it is full): they then drop / are kept exactly
   * like the rest of the inventory (review m4). The death message is ours; it respects the
   * vanilla showDeathMessages rule. Edition note: the engine's generic death line cannot be
   * suppressed without changing a vanilla game rule (§2.2 forbids that).
   */
  private killBySoulWager(player: Player): void {
    player.addTag(SOUL_WAGER_TAG);
    try {
      stashTotems(player);
    } catch (e) {
      log.warn('soul wager: could not move totems', e);
    }
    if (showDeathMessages()) world.sendMessage(t('death.attack.burmaldaholic.soul_wager', player.name));
    player.kill();
    system.runTimeout(() => player.isValid && player.removeTag(SOUL_WAGER_TAG), 40);
  }

  private announce(e: SettledEvent): void {
    if (!this.config.bool('core.announceBigWins') || e.net < this.config.num('core.bigWinThreshold')) return;
    world.sendMessage(t('msg.burmaldaholic.core.big_win', e.player.name, chipsAcc(e.net), gameLabel(e.game)));
  }

  /** Offline achievements: queue for a player id (used by core achievements). */
  queueAchievement(playerId: string, id: string): void {
    offlineStore.update(playerId, (e) => withAchievement(e, id));
  }

  private emit(e: SettledEvent): void {
    for (const l of this.listeners) {
      try {
        l(e);
      } catch (err) {
        log.error('onSettled listener failed', err);
      }
    }
  }

  // ---- drawn rounds (review M1) -----------------------------------------------------------

  private readDrawn(playerId: string): Record<string, StoredTicket> {
    const v = worldJson.read<Record<string, StoredTicket> | undefined>(DRAWN_PREFIX + playerId, undefined);
    return v && typeof v === 'object' ? v : {};
  }

  private storeDrawn(ticket: WagerTicket): void {
    const { data: _d, ...rest } = ticket;
    const all = this.readDrawn(ticket.playerId);
    all[ticket.id] = rest;
    worldJson.write(DRAWN_PREFIX + ticket.playerId, all);
  }

  private dropDrawn(ticket: WagerTicket): void {
    if (!hasDrawn(ticket)) return;
    const all = this.readDrawn(ticket.playerId);
    if (!(ticket.id in all)) return;
    delete all[ticket.id];
    worldJson.write(DRAWN_PREFIX + ticket.playerId, Object.keys(all).length ? all : undefined);
  }

  /** World load: settle every drawn round of the previous run through the offline path. */
  private parkDrawnRounds(): void {
    for (const id of world.getDynamicPropertyIds()) {
      if (!id.startsWith(DRAWN_PREFIX)) continue;
      const all = worldJson.read<Record<string, StoredTicket> | undefined>(id, undefined);
      for (const w of Object.values(all && typeof all === 'object' ? all : {})) {
        try {
          if (w && typeof w.id === 'string' && w.boot !== this.boot) this.parkDrawn(w);
        } catch (err) {
          log.error(`drawn round ${String(w?.id)} failed`, err);
        }
      }
      worldJson.write(id, undefined);
    }
  }

  /**
   * Settle a drawn round of an earlier run at its drawn result, like an offline settle(): the
   * result is parked for the player (applied on join, onSettled fires then). Idempotent: a
   * ticket already resolved in the player's offline entry is skipped.
   */
  private parkDrawn(w: StoredTicket): void {
    if (!hasDrawn(w)) return;
    if (offlineStore.read(w.playerId).resolved.includes(w.id)) return;
    const ret = Math.max(0, Math.floor(w.drawn));
    // Reservations of an earlier run were already dropped (economy.resetReservations).
    if (w.house.kind === 'bankroll' && w.kind === 'chips') this.economy.settleBankroll(w.house.id, { reserved: w.boot === this.boot ? w.reserved : 0, stake: w.value, payout: ret });
    const base: DeferredSettle = { game: w.game, staked: w.value, totalReturn: ret, stakeKind: w.kind, house: w.house, tableKey: w.tableKey, theoreticalLoss: w.theo };
    const note = t('msg.burmaldaholic.core.round_played_out', gameLabel(w.game), netResult(ret - w.value));
    offlineStore.update(w.playerId, (e) => withMessage(withSettled(withResolved(this.settleOffline(e, w, ret), w.id), base), note));
    log.info(`settled drawn ${w.game} round ${w.id} of ${w.playerId} after restart (return ${ret})`);
  }

  private persist(player: Player): void {
    if (!player.isValid) return;
    const mine = this.openFor(player).map(({ data: _d, ...rest }) => rest);
    writeJson(player, TICKETS_PROP, mine.length ? mine : undefined);
  }

  /**
   * Rounds left open by a previous server run (§4.1): drawn ones are settled at their drawn
   * result (normally already parked at world load; this is the fallback), the others refunded.
   * Rounds settled offline are dropped.
   */
  private recover(player: Player, resolved: readonly string[]): void {
    const stored = readJson<StoredTicket[]>(player, TICKETS_PROP, []);
    const plan = planRecovery(stored, resolved, this.boot);
    if (!plan.refund.length && !plan.settle.length && !plan.dropped.length) return;
    if (plan.settle.length) {
      for (const w of plan.settle) this.parkDrawn(w);
      try {
        this.applyOffline(player);
      } catch (err) {
        log.error(`drawn rounds of ${player.name} failed`, err);
      }
    }
    for (const w of plan.refund) {
      if (this.config.bool('core.roundTimeoutRefund')) {
        this.refundTo(w, player);
        player.sendMessage(t('msg.burmaldaholic.core.round_refunded', chips(w.value)));
      }
      log.info(`refunded stale ${w.game} round ${w.id} of ${player.name} (${w.value})`);
    }
    this.persist(player);
  }

  // ---- heart penalties -------------------------------------------------------------------

  /** Heart penalties with their expiry moved forward by any dormant time (review M2). */
  private penalties(player: Player): HeartPenalty[] {
    const r = rebasePenalties(readJson<HeartPenalty[]>(player, HEARTS_PROP, []), this.dormant());
    if (r.changed) writeJson(player, HEARTS_PROP, r.list.length ? r.list : undefined);
    return r.list;
  }

  /** Active hearts lost by a player (for UI). */
  heartsLost(player: Player): number {
    return activeHearts(this.penalties(player), worldTick());
  }

  /**
   * Edition note: Bedrock script cannot change max_health, so the penalty is enforced by
   * clamping current health to the reduced maximum (the extra hearts show empty).
   */
  private enforceHearts(player: Player): void {
    const list = this.penalties(player);
    if (!list.length) return;
    const now = worldTick();
    const live = list.filter((p) => p.until > now);
    if (live.length !== list.length) {
      writeJson(player, HEARTS_PROP, live.length ? live : undefined);
      if (!live.length) player.sendMessage(t('msg.burmaldaholic.wager.hearts_restored'));
    }
    if (!live.length) return;
    const h = player.getComponent(EntityComponentTypes.Health) as EntityHealthComponent | undefined;
    if (!h) return;
    const cap = cappedMaxHealth(h.effectiveMax, activeHearts(live, now));
    if (h.currentValue > cap) h.setCurrentValue(cap);
  }
}

function showDeathMessages(): boolean {
  try {
    return world.gameRules.showDeathMessages !== false;
  } catch {
    return true;
  }
}

/** Move Totems of Undying out of the main hand and offhand (into free inventory slots, else drop them). */
function stashTotems(player: Player): void {
  const inv = (player.getComponent(EntityComponentTypes.Inventory) as EntityInventoryComponent | undefined)?.container;
  const eq = player.getComponent(EntityComponentTypes.Equippable) as EntityEquippableComponent | undefined;
  const taken: ItemStack[] = [];
  const off = eq?.getEquipment(EquipmentSlot.Offhand);
  if (off?.typeId === TOTEM_ID) {
    eq!.setEquipment(EquipmentSlot.Offhand, undefined);
    taken.push(off);
  }
  const sel = player.selectedSlotIndex;
  const main = inv?.getItem(sel);
  if (inv && main?.typeId === TOTEM_ID) {
    inv.setItem(sel, undefined);
    taken.push(main);
  }
  for (const stack of taken) {
    let placed = false;
    if (inv) {
      // Hotbar slots other than the selected one are fine too: only the hands pop a totem.
      for (let i = 0; i < inv.size && !placed; i++) {
        if (i === sel || inv.getItem(i)) continue;
        inv.setItem(i, stack);
        placed = true;
      }
    }
    if (!placed) player.dimension.spawnItem(stack, player.location);
  }
}

/** Id of a possibly-invalid Player object (undefined when the engine refuses to read it). */
function safeId(p: Player): string | undefined {
  try {
    return p.id;
  } catch {
    return undefined;
  }
}

function baseMaxHealth(player: Player): number {
  return (player.getComponent(EntityComponentTypes.Health) as EntityHealthComponent | undefined)?.effectiveMax ?? 20;
}

function isEnchanted(stack: ItemStack): boolean {
  const e = stack.getComponent(ItemComponentTypes.Enchantable) as ItemEnchantableComponent | undefined;
  return !!e && e.getEnchantments().length > 0;
}

function isDamaged(stack: ItemStack): boolean {
  const d = stack.getComponent(ItemComponentTypes.Durability) as ItemDurabilityComponent | undefined;
  return !!d && d.damage > 0;
}

/** Vanilla item name rawtext via the stack's localization key. */
function itemName(typeId: string): Raw {
  try {
    return { translate: new ItemStack(typeId, 1).localizationKey };
  } catch {
    return { translate: `item.${typeId.replace(/^minecraft:/, '')}.name` };
  }
}

/** "Win! +40 chips" / "Lost 40 chips" / "Push". */
const netResult = (net: number): Raw =>
  net > 0 ? t('gui.burmaldaholic.common.result.win', chips(net)) : net < 0 ? t('gui.burmaldaholic.common.result.loss', chips(-net)) : t('gui.burmaldaholic.common.result.push');

const unitTime = (ticks: number): Raw => (ticks >= 1200 ? unit('minute', Math.ceil(ticks / 1200)) : unit('second', Math.ceil(ticks / 20)));
