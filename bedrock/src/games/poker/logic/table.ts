/**
 * Cash-game table model: seats (humans + house bots), button movement, bot filling, sit-out
 * and timeout bookkeeping, stakes (GAME_DESIGN §7.1, §7.3, §7.4). PURE - the Minecraft layer
 * drives it and does the money moves.
 */
import { type Rng, pick } from '../../../core/logic/rng';
import { BOT_NAMES, type BotTier, pickTier } from './bots';
import { type HandState, startHand } from './engine';
import { type RakeConfig } from './pots';

export const STAKE_LEVELS = ['micro', 'low', 'mid', 'high'] as const;
export type StakeLevel = (typeof STAKE_LEVELS)[number];
/** Minimum VIP tier per stake level (§7.1): Bronze, Silver, Gold, Diamond. */
export const STAKE_MIN_TIER: Record<StakeLevel, number> = { micro: 0, low: 1, mid: 2, high: 4 };

/** SB = BB / 2 (floor, min 1) - CONFIG.md poker.stakes.*.bb. */
export const smallBlind = (bb: number): number => Math.max(1, Math.floor(bb / 2));

export const isStakeLevel = (s: unknown): s is StakeLevel => typeof s === 'string' && (STAKE_LEVELS as readonly string[]).includes(s);

/** Buy-in range in chips, clamped by the balance: {min, max}; max < min = cannot sit. */
export function buyInRange(bb: number, minBb: number, maxBb: number, balance: number, current = 0): { min: number; max: number } {
  const lo = Math.min(minBb, maxBb) * bb;
  const hi = Math.max(minBb, maxBb) * bb;
  if (current > 0) {
    // top-up: up to the max buy-in in total (§7.1 "top-up allowed up to 100 BB")
    return { min: 1, max: Math.min(balance, Math.max(0, hi - current)) };
  }
  return { min: lo, max: Math.min(hi, balance) };
}

export interface Seat {
  id: string;
  name: string;
  kind: 'human' | 'bot';
  tier?: BotTier;
  stack: number;
  /** auto check/fold, still posts blinds */
  sittingOut: boolean;
  /** consecutive action timeouts */
  timeouts: number;
  /** hands played while sitting out */
  sitOutHands: number;
  /** leaves at the end of the current hand (stood up, walked away, disconnected) */
  leaving: boolean;
  /** disconnected: stack is paid on the next join */
  disconnected: boolean;
  /** VPIP of the last 20 hands (humans; sharks read it) */
  vpipHistory: boolean[];
}

export interface TableSettings {
  maxSeats: number;
  bb: number;
  rake: RakeConfig;
}

export interface BotFillOptions {
  enabled: boolean;
  mix: readonly number[];
  buyIn: number;
}

export class TableModel {
  seats: (Seat | undefined)[];
  /** seat index of the button, -1 before the first hand */
  button = -1;
  handNo = 0;
  hand?: HandState;
  /** hand player index -> seat index */
  handSeats: number[] = [];

  constructor(public settings: TableSettings) {
    this.seats = new Array<Seat | undefined>(settings.maxSeats).fill(undefined);
  }

  get bb(): number {
    return this.settings.bb;
  }
  get sb(): number {
    return smallBlind(this.settings.bb);
  }

  occupied(): Seat[] {
    return this.seats.filter((s): s is Seat => !!s);
  }
  humans(): Seat[] {
    return this.occupied().filter((s) => s.kind === 'human');
  }
  bots(): Seat[] {
    return this.occupied().filter((s) => s.kind === 'bot');
  }
  seatIndexOf(id: string): number {
    return this.seats.findIndex((s) => s?.id === id);
  }
  seatOf(id: string): Seat | undefined {
    return this.seats.find((s) => s?.id === id);
  }
  inHand(): boolean {
    return !!this.hand && !this.hand.complete;
  }
  /** Hand player index of a seat id in the running hand, or -1. */
  handIndexOf(id: string): number {
    if (!this.hand) return -1;
    return this.hand.players.findIndex((p) => p.id === id);
  }

  private newSeat(id: string, name: string, kind: Seat['kind'], stack: number, tier?: BotTier): Seat {
    return { id, name, kind, tier, stack, sittingOut: false, timeouts: 0, sitOutHands: 0, leaving: false, disconnected: false, vpipHistory: [] };
  }

  /** Seat a human in the first empty seat; returns the seat index or -1 when full. */
  addHuman(id: string, name: string, stack: number): number {
    const i = this.seats.findIndex((s) => !s);
    if (i < 0) return -1;
    this.seats[i] = this.newSeat(id, name, 'human', stack);
    return i;
  }

  /** A human can take a seat now or after bots make room at the end of the hand. */
  canSeatHuman(): boolean {
    return this.humans().length < this.settings.maxSeats;
  }

  /** Free a seat for a waiting human by removing a bot that is not in the running hand. */
  makeRoom(): Seat | undefined {
    if (this.seats.some((s) => !s)) return undefined;
    const i = this.seats.findIndex((s) => s?.kind === 'bot' && (!this.inHand() || this.handIndexOf(s.id) < 0 || this.hand!.players[this.handIndexOf(s.id)]!.folded));
    if (i < 0 || this.inHand()) return undefined;
    const s = this.seats[i];
    this.seats[i] = undefined;
    return s;
  }

  removeSeat(id: string): Seat | undefined {
    const i = this.seatIndexOf(id);
    if (i < 0) return undefined;
    const s = this.seats[i];
    this.seats[i] = undefined;
    return s;
  }

  /** Bots wanted: 0 without humans, else max seats − humans − 1 (a seat kept for walk-ins), at least 1 when a human is alone. */
  botTarget(enabled: boolean): number {
    const humans = this.humans().length;
    if (!enabled || humans === 0) return 0;
    const free = this.settings.maxSeats - humans;
    return Math.max(humans === 1 ? Math.min(1, free) : 0, free - 1);
  }

  /**
   * Between hands: remove busted bots and surplus bots, add bots up to the target.
   * Returns the bots that joined and left (for announcements).
   */
  fillBots(o: BotFillOptions, rng: Rng, nextId: () => string): { joined: Seat[]; left: Seat[] } {
    const joined: Seat[] = [];
    const left: Seat[] = [];
    if (this.inHand()) return { joined, left };
    for (let i = 0; i < this.seats.length; i++) {
      const s = this.seats[i];
      if (s?.kind === 'bot' && s.stack <= 0) {
        left.push(s);
        this.seats[i] = undefined;
      }
    }
    const target = this.botTarget(o.enabled);
    let bots = this.bots();
    while (bots.length > target) {
      const b = bots[bots.length - 1]!;
      this.removeSeat(b.id);
      left.push(b);
      bots = this.bots();
    }
    const used = new Set(this.bots().map((b) => b.name));
    while (this.bots().length < target) {
      const i = this.seats.findIndex((s) => !s);
      if (i < 0) break;
      const free = BOT_NAMES.filter((n) => !used.has(n));
      const name = pick(rng, free.length ? free : BOT_NAMES);
      used.add(name);
      const seat = this.newSeat(nextId(), name, 'bot', o.buyIn, pickTier(rng, o.mix));
      this.seats[i] = seat;
      joined.push(seat);
    }
    return { joined, left };
  }

  /** Seats that will be dealt in: occupied, chips > 0, not leaving. */
  dealable(): number[] {
    return this.seats.flatMap((s, i) => (s && s.stack > 0 && !s.leaving ? [i] : []));
  }

  canStart(): boolean {
    return !this.inHand() && this.humans().some((h) => !h.leaving && h.stack > 0) && this.dealable().length >= 2;
  }

  /** Move the button one seat clockwise (to the next dealt-in seat) and deal. */
  startHand(rng: Rng): HandState | undefined {
    if (!this.canStart()) return undefined;
    const seats = this.dealable();
    const n = this.seats.length;
    let btnSeat = seats[0]!;
    if (this.button >= 0) {
      for (let k = 1; k <= n; k++) {
        const i = (this.button + k) % n;
        if (seats.includes(i)) {
          btnSeat = i;
          break;
        }
      }
    }
    this.button = btnSeat;
    this.handSeats = seats;
    this.handNo++;
    this.hand = startHand(
      seats.map((i) => {
        const s = this.seats[i]!;
        return { id: s.id, human: s.kind === 'human', stack: s.stack };
      }),
      rng,
      { sb: this.sb, bb: this.bb, button: seats.indexOf(btnSeat), rake: this.settings.rake },
    );
    return this.hand;
  }

  /** Copy the finished hand's stacks back to the seats and update sit-out / VPIP counters. */
  settleHand(): void {
    const h = this.hand;
    if (!h || !h.complete) return;
    h.players.forEach((p, k) => {
      const s = this.seats[this.handSeats[k]!];
      if (!s || s.id !== p.id) return;
      s.stack = p.stack;
      if (s.kind === 'human') {
        s.vpipHistory.push(p.vpip);
        if (s.vpipHistory.length > 20) s.vpipHistory.shift();
        if (s.sittingOut) s.sitOutHands++;
      }
    });
  }

  /** Undo a hand in progress (table broken / casino off): everyone gets their start stack. */
  abortHand(): void {
    const h = this.hand;
    if (!h || h.complete) return;
    h.players.forEach((p, k) => {
      const s = this.seats[this.handSeats[k]!];
      if (s && s.id === p.id) s.stack = p.startStack;
    });
    this.hand = undefined;
  }

  /** VPIP (0..1) per human id, for sharks; only humans with ≥ 5 recorded hands. */
  vpipMap(): Map<string, number> {
    const m = new Map<string, number>();
    for (const s of this.humans()) {
      if (s.vpipHistory.length >= 5) m.set(s.id, s.vpipHistory.filter(Boolean).length / s.vpipHistory.length);
    }
    return m;
  }

  /**
   * Record a timed-out action. Returns true when the player has just been moved to sitting out
   * (after `limit` consecutive timeouts).
   */
  recordTimeout(id: string, limit: number): boolean {
    const s = this.seatOf(id);
    if (!s) return false;
    s.timeouts++;
    if (!s.sittingOut && s.timeouts >= limit) {
      s.sittingOut = true;
      s.sitOutHands = 0;
      return true;
    }
    return false;
  }

  /** A manual action resets the timeout streak. */
  recordAction(id: string): void {
    const s = this.seatOf(id);
    if (s) s.timeouts = 0;
  }

  sitIn(id: string): void {
    const s = this.seatOf(id);
    if (!s) return;
    s.sittingOut = false;
    s.timeouts = 0;
    s.sitOutHands = 0;
  }

  sitOut(id: string): void {
    const s = this.seatOf(id);
    if (!s || s.sittingOut) return;
    s.sittingOut = true;
    s.sitOutHands = 0;
  }

  /** Humans to remove after a hand: leaving, disconnected, broke, or sitting out too long. */
  toRemove(sitOutHandsToRemove: number): Seat[] {
    return this.humans().filter((s) => s.leaving || s.disconnected || s.stack <= 0 || (s.sittingOut && s.sitOutHands >= sitOutHandsToRemove));
  }
}
