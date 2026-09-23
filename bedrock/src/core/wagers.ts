/**
 * Wager service: the generic round lifecycle every house-banked game uses (GAME_DESIGN §4.1):
 *   IDLE -> place() [STAKED: stake taken, bet locked, persisted] -> settle() [SETTLED: payout
 *   credited, streak/VIP/contract hooks fired]      (refund() cancels a STAKED round)
 * Stakes: chips, the held item, XP levels, temporary max hearts, Hardcore Soul Wager (§4.3-4.4).
 * Houses: the world bank or an owned casino bankroll (reservation rule §18.2).
 * Open rounds are persisted per player; after a server restart they are refunded on the
 * player's next join (config core.roundTimeoutRefund) with msg.burmaldaholic.core.round_refunded.
 */
import {
  EntityComponentTypes,
  type EntityHealthComponent,
  ItemComponentTypes,
  type ItemDurabilityComponent,
  type ItemEnchantableComponent,
  ItemStack,
  type Player,
  system,
  world,
} from '@minecraft/server';
import type { ConfigService } from './config';
import { type Economy, type HouseRef, BANK } from './economy';
import { clearSlot, giveItems, heldItem } from './items';
import type { Limits, TableLimits } from './limits';
import { outcomeOfNet } from './logic/streak';
import { type Raw, chips, chipsAcc, t, unit } from './logic/rawtext';
import {
  type HeartPenalty,
  activeHearts,
  cappedMaxHealth,
  checkHeartStake,
  checkItemStake,
  checkXpStake,
  pawnSettlement,
  soulValue,
  xpAtLevel,
  xpStakeValue,
} from './logic/wager-math';
import { createLogger } from './log';
import { readJson, worldJson, worldTick, writeJson } from './store';
import type { StreakService } from './streak';

const log = createLogger('core.wagers');
const TICKETS_PROP = 'burmaldaholic:core.wagers';
const HEARTS_PROP = 'burmaldaholic:core.hearts';
const SOUL_CD_PROP = 'burmaldaholic:core.soul_cooldown';
const BOOT_PROP = 'burmaldaholic:core.boot';
/** Tag set on a player killed by a lost Soul Wager (Last Chance must not save them). */
export const SOUL_WAGER_TAG = 'burmaldaholic_core_soul_wager';

/** Game ids used for labels (`gui.burmaldaholic.common.game.<id>`) and RNG classification. */
export const GAME_IDS = ['blackjack', 'poker', 'slots', 'roulette', 'craps', 'coin_flip', 'wheel', 'scratch', 'plinko', 'dice_duel'] as const;
export type GameId = (typeof GAME_IDS)[number];
/** Games whose odds the streak may tilt (§14); the rest are "always honest". */
export const RNG_GAMES: readonly GameId[] = ['slots', 'wheel', 'plinko', 'scratch', 'coin_flip'];
export const gameLabel = (g: GameId): Raw => t(`gui.burmaldaholic.common.game.${g}`);

export type Stake =
  | { kind: 'chips'; amount: number }
  /** the player's held stack (appraisal table, undamaged/unenchanted/unnamed) */
  | { kind: 'item' }
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
  /** per-round data a game may keep with the ticket (not persisted) */
  data?: unknown;
}

export type PlaceResult = { ok: true; ticket: WagerTicket } | { ok: false; error: Raw };

export interface SettledEvent {
  player: Player;
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
}
export type SettledListener = (e: SettledEvent) => void;

export class WagerService {
  private readonly open = new Map<string, WagerTicket>();
  private readonly listeners: SettledListener[] = [];
  private boot = 0;
  private seq = 0;

  constructor(
    private readonly economy: Economy,
    private readonly limits: Limits,
    private readonly config: ConfigService,
    private readonly streaks: StreakService,
  ) {}

  onSettled(l: SettledListener): void {
    this.listeners.push(l);
  }

  // ---- lifecycle -----------------------------------------------------------------------

  /** Called by core at world load. */
  start(): void {
    this.boot = (worldJson.read<number>(BOOT_PROP, 0) || 0) + 1;
    worldJson.write(BOOT_PROP, this.boot);
    this.economy.resetReservations();
    for (const p of world.getAllPlayers()) this.recover(p);
    world.afterEvents.playerSpawn.subscribe((e) => {
      if (e.initialSpawn) this.recover(e.player);
      this.enforceHearts(e.player);
    });
    system.runInterval(() => {
      for (const p of world.getAllPlayers()) this.enforceHearts(p);
    }, 10);
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
    const house = o.house ?? BANK;
    const s = o.stake;
    if (s.kind !== 'chips') {
      if (s.kind === 'soul' ? !o.soulAllowed : !o.pawnAllowed) return fail(t('gui.burmaldaholic.error.invalid_bet_position'));
      if (house.kind !== 'bank') return fail(t('gui.burmaldaholic.error.pawn_not_accepted'));
      if (s.kind !== 'soul' && !this.config.bool('wager.pawnEnabled')) return fail(t('gui.burmaldaholic.error.disabled'));
    }
    const tierMax = this.limits.tierMax(player, o.limits?.tierMultiplier);
    const ticket: WagerTicket = { id: `${this.boot}.${++this.seq}`, playerId: player.id, game: o.game, kind: s.kind, value: 0, house, reserved: 0, boot: this.boot };

    // 1. Compute the stake value and validate (nothing taken yet).
    let value: number;
    let take: () => void = () => {};
    switch (s.kind) {
      case 'chips': {
        value = Math.floor(s.amount);
        if (!o.skipLimits) {
          const err = this.limits.check(player, value, o.limits, this.economy.balance(player));
          if (err) return fail(err);
        } else if (value > this.economy.balance(player) || value <= 0) {
          return fail(t('gui.burmaldaholic.error.insufficient_funds', chips(this.economy.balance(player))));
        }
        take = () => void this.economy.debit(player, value, `${o.game}.stake`);
        break;
      }
      case 'item': {
        if (!this.config.bool('wager.items.enabled')) return fail(t('gui.burmaldaholic.error.disabled'));
        const { stack, slot } = heldItem(player);
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
          const before = player.getTotalXp();
          const target = xpAtLevel(player.level - s.levels);
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
  raise(ticket: WagerTicket, player: Player, amount: number, extraWorstCase = amount * 2): boolean {
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
    if (ticket.house.kind === 'bankroll') ticket.reserved += extraWorstCase;
    this.persist(player);
    return true;
  }

  /**
   * Settle a round. `totalReturn` = everything the player gets back INCLUDING the stake, in
   * chips (0 = lost, stake = push, 2 × stake = 1:1 win). Pawn stakes are returned on a
   * win/push and forfeited on a loss. `player` may be offline-safe (pass the Player you had).
   */
  settle(ticket: WagerTicket, player: Player, totalReturn: number): SettledEvent | undefined {
    if (!this.open.delete(ticket.id)) return undefined;
    const ret = Math.max(0, Math.floor(totalReturn));
    const staked = ticket.value;
    const net = ret - staked;
    try {
      if (ticket.kind === 'chips') this.payout(ticket, player, ret);
      else this.settlePawn(ticket, player, ret);
    } finally {
      this.persist(player);
    }
    if (staked >= 1 && player.isValid) this.streaks.recordFor(player, outcomeOfNet(net));
    const ev: SettledEvent = { player, game: ticket.game, staked, totalReturn: ret, net, stakeKind: ticket.kind, house: ticket.house, houseBanked: true };
    this.announce(ev);
    this.emit(ev);
    return ev;
  }

  /** Cancel an open round and give the stake back (no streak/VIP effect). */
  refund(ticket: WagerTicket, player: Player): void {
    if (!this.open.delete(ticket.id)) return;
    this.refundStored(ticket, player);
    this.persist(player);
  }

  /**
   * Report a settled PvP round (poker hand, dice duel) that moved chips with
   * economy.transact: updates the streak and fires onSettled (houseBanked=false).
   */
  recordPvp(player: Player, game: GameId, staked: number, net: number): void {
    if (staked >= 1) this.streaks.recordFor(player, outcomeOfNet(net));
    this.emit({ player, game, staked, totalReturn: staked + net, net, stakeKind: 'chips', house: BANK, houseBanked: false });
  }

  // ---- internals -----------------------------------------------------------------------

  private payout(ticket: WagerTicket, player: Player, ret: number): void {
    if (ticket.house.kind === 'bankroll') {
      this.economy.settleBankroll(ticket.house.id, { reserved: ticket.reserved, stake: ticket.value, payout: ret });
    }
    if (ret > 0) this.economy.credit(player, ret, `${ticket.game}.payout`);
  }

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
      list.push({ hearts: ticket.hearts, until: worldTick() + this.config.int('wager.hearts.durationTicks') });
      writeJson(player, HEARTS_PROP, list);
      player.sendMessage(t('msg.burmaldaholic.wager.hearts_lost', unit('heart', ticket.hearts)));
      this.enforceHearts(player);
    }
  }

  private returnPawn(ticket: Pick<WagerTicket, 'item' | 'xpRemoved'>, player: Player): void {
    if (ticket.item) giveItems(player, ticket.item.typeId, ticket.item.amount);
    if (ticket.xpRemoved) player.addExperience(ticket.xpRemoved);
  }

  private refundStored(ticket: WagerTicket, player: Player): void {
    if (ticket.house.kind === 'bankroll' && ticket.boot === this.boot) this.economy.release(ticket.house.id, ticket.reserved);
    if (ticket.kind === 'chips') this.economy.credit(player, ticket.value, `${ticket.game}.refund`);
    else this.returnPawn(ticket, player);
  }

  private killBySoulWager(player: Player): void {
    player.addTag(SOUL_WAGER_TAG);
    world.sendMessage(t('death.attack.burmaldaholic.soul_wager', player.name));
    player.kill();
    system.runTimeout(() => player.isValid && player.removeTag(SOUL_WAGER_TAG), 40);
  }

  private announce(e: SettledEvent): void {
    if (!this.config.bool('core.announceBigWins') || e.net < this.config.num('core.bigWinThreshold')) return;
    world.sendMessage(t('msg.burmaldaholic.core.big_win', e.player.name, chipsAcc(e.net), gameLabel(e.game)));
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

  private persist(player: Player): void {
    if (!player.isValid) return;
    const mine = this.openFor(player).map(({ data: _d, ...rest }) => rest);
    writeJson(player, TICKETS_PROP, mine.length ? mine : undefined);
  }

  /** Refund rounds left open by a previous server run (§4.1). */
  private recover(player: Player): void {
    const stored = readJson<WagerTicket[]>(player, TICKETS_PROP, []);
    const stale = stored.filter((w) => w.boot !== this.boot);
    if (!stale.length) return;
    for (const w of stale) {
      if (this.config.bool('core.roundTimeoutRefund')) {
        this.refundStored(w, player);
        player.sendMessage(t('msg.burmaldaholic.core.round_refunded', chips(w.value)));
      }
      log.info(`refunded stale ${w.game} round ${w.id} of ${player.name} (${w.value})`);
    }
    this.persist(player);
  }

  // ---- heart penalties -------------------------------------------------------------------

  private penalties(player: Player): HeartPenalty[] {
    return readJson<HeartPenalty[]>(player, HEARTS_PROP, []);
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

const unitTime = (ticks: number): Raw => (ticks >= 1200 ? unit('minute', Math.ceil(ticks / 1200)) : unit('second', Math.ceil(ticks / 20)));
