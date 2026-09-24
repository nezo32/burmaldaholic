/**
 * Seats & Bots core types (docs/design/BOTS.md). PURE. Java twin: core/bots/logic/*.java.
 * Games keep seat occupants ABSTRACT over `SeatOccupant` and route every bot decision through their
 * `BotPolicy` (policy.ts).
 */

/** Who may sit (BOTS.md §2.1). */
export const SEAT_POLICIES = ['HUMANS_ONLY', 'BOTS_ONLY', 'MIXED'] as const;
export type SeatPolicy = (typeof SEAT_POLICIES)[number];

/** Setting (MIXED = draw per bot). A bot's own level is a `BotLevel`. */
export const BOT_DIFFICULTIES = ['EASY', 'NORMAL', 'HARD', 'MIXED'] as const;
export type BotDifficulty = (typeof BOT_DIFFICULTIES)[number];
export type BotLevel = Exclude<BotDifficulty, 'MIXED'>;

export const BOT_SPEEDS = ['NORMAL', 'FAST', 'INSTANT'] as const;
export type BotSpeed = (typeof BOT_SPEEDS)[number];

/** MONEY = real chips from a purse (poker, chemmy, PvP); ATMOSPHERE = virtual bets beside humans. */
export type BotRole = 'MONEY' | 'ATMOSPHERE';

/** Owner's per-table Bots control (BOTS.md §6.2). */
export type BotsMode = 'OFF' | 'ATMOSPHERE' | 'ALLOWED';

export const PERSONALITIES = ['ROCK', 'STATION', 'MANIAC', 'TAG', 'LAG'] as const;
export type Personality = (typeof PERSONALITIES)[number];

/** Where a money bot's chips come from / return to (BOTS.md §5.1). */
export type Purse = { kind: 'NONE' } | { kind: 'BANK' } | { kind: 'BANKROLL'; id: string };

export interface BotProfile {
  /** `b` + 7 base-36 chars, unique per table session / match */
  readonly id: string;
  /** stable name id: `gui.burmaldaholic.bots.name.<nameId>` */
  readonly nameId: string;
  readonly level: BotLevel;
  readonly personality: Personality;
}

/** A seat / participant: a human (player id + name) or a bot. */
export type SeatOccupant =
  | { readonly kind: 'human'; readonly id: string; readonly name: string }
  | { readonly kind: 'bot'; readonly profile: BotProfile; readonly role: BotRole; readonly purse: Purse };

/** Seat / participant key: the player id, or `bot:<id>`. */
export const occupantKey = (o: SeatOccupant): string => (o.kind === 'human' ? o.id : `bot:${o.profile.id}`);
export const isBotKey = (key: string): boolean => key.startsWith('bot:');

/** Per-table / per-match settings (BOTS.md §2.2). Applied at the next safe point. */
export interface BotSettings {
  readonly policy: SeatPolicy;
  /** MIXED: upper bound; BOTS_ONLY: exact (≥ 1) */
  readonly count: number;
  readonly difficulty: BotDifficulty;
  readonly keepFree: boolean;
  readonly chatter: boolean;
  readonly speed: BotSpeed;
}

export const HUMANS_ONLY_SETTINGS: BotSettings = { policy: 'HUMANS_ONLY', count: 0, difficulty: 'NORMAL', keepFree: true, chatter: true, speed: 'NORMAL' };

/** FAST / INSTANT only in BOTS_ONLY. */
export const effectiveSpeed = (s: BotSettings): BotSpeed => (s.policy === 'BOTS_ONLY' ? s.speed : 'NORMAL');

/** Owner / keeper limits (BOTS.md §6.2). */
export interface OwnerControls {
  readonly botsMode: BotsMode;
  readonly hostMayChange: boolean;
  readonly maxBots: number;
  readonly allowPrivate: boolean;
}

export const unownedControls = (seats: number): OwnerControls => ({ botsMode: 'ALLOWED', hostMayChange: true, maxBots: Math.max(0, seats - 1), allowPrivate: true });
export const ownedDefaultControls = (seats: number): OwnerControls => ({ botsMode: 'ATMOSPHERE', hostMayChange: true, maxBots: Math.max(0, seats - 1), allowPrivate: false });

export const modeAllows = (mode: BotsMode, role: BotRole): boolean => mode === 'ALLOWED' || (mode === 'ATMOSPHERE' && role === 'ATMOSPHERE');

/** Translation keys (BOTS.md §11.2). */
export const policyKey = (p: SeatPolicy): string => `gui.burmaldaholic.bots.policy.${p.toLowerCase()}`;
export const levelKey = (d: BotDifficulty): string => `gui.burmaldaholic.bots.level.${d.toLowerCase()}`;
export const styleKey = (d: BotDifficulty): string => `gui.burmaldaholic.bots.style.${d.toLowerCase()}`;
export const speedKey = (s: BotSpeed): string => `gui.burmaldaholic.bots.speed.${s.toLowerCase()}`;
export const personalityKey = (p: Personality): string => `gui.burmaldaholic.bots.personality.${p.toLowerCase()}`;
/** Legacy poker tier id → level (saved tables, old config). */
export const levelFromPokerTier = (tier: string): BotLevel => (tier === 'fish' ? 'EASY' : tier === 'shark' ? 'HARD' : 'NORMAL');
