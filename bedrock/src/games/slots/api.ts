/**
 * Public API of the slots module for other modules (types only + service name).
 * Get it with `ctx.services.get<SlotsApi>(SLOTS_SERVICE)` (lazily: services are provided in
 * onWorldLoad, so look it up inside your handlers or in a system.run after world load).
 *
 * ## Chaos trigger contract (GAME_DESIGN §8.1, §8.5, §13.1 triggers 2 and 4)
 *
 * After a spin is settled (payout already credited) and its reel animation has finished, the
 * slots module emits AT MOST ONE `SlotsTriggerEvent` per spin, for the highest-priority special
 * that hit on any payline: Star > Clock > Pearl > TNT/Creeper. The consumer (chaos) then:
 *
 * | symbol            | `event`           | chaos action                                                        |
 * |-------------------|-------------------|---------------------------------------------------------------------|
 * | creeper / tnt     | `mob_wave`        | `mob_wave` for `player`                                             |
 * | pearl             | `random_teleport` | `random_teleport` for `player`                                      |
 * | clock (Netherite) | `golden_hour`     | start Golden Hour server-wide unless on cooldown (the 50× is paid)   |
 * | star (progressive)| `jackpot`         | `diamond_rain` for `player` + `chip_shower` for every player within `JACKPOT_SHOWER_RADIUS` blocks |
 *
 * The named event bypasses the ambient chance but still respects `chaos.enabled`,
 * `chaos.event.<id>.enabled`, the safety rules and the per-player cooldown (§13.1 trigger 2);
 * when blocked, nothing happens — the payout was already made. Slots never starts Golden Hour
 * or spawns anything itself: it only reports. After the listeners ran, slots reads
 * `ctx.goldenHour` to tell the player whether the clocks started Golden Hour
 * (`msg.burmaldaholic.slots.three_clocks`) or it was recharging (`…three_clocks_cooldown`),
 * so a `golden_hour` listener should start it synchronously.
 *
 * The jackpot server-wide announcement (`msg.burmaldaholic.slots.jackpot_broadcast`) is sent by
 * slots, not by chaos.
 *
 * Fallback: if nobody subscribed with `onTrigger`, slots calls `trigger(player, eventId)` on the
 * chaos service (CHAOS_SERVICE) when it exposes such a function, with `'diamond_rain'` for the
 * winner and `'chip_shower'` for each nearby player on a jackpot.
 */
import type { Player, Vector3 } from '@minecraft/server';

export const SLOTS_SERVICE = 'slots';

/** chip_shower radius around a jackpot winner (§8.5). */
export const JACKPOT_SHOWER_RADIUS = 16;

export type SlotTier = 'copper' | 'gold' | 'netherite';
export type SlotSpecialSymbol = 'creeper' | 'tnt' | 'pearl' | 'clock' | 'star';
export type SlotChaosEvent = 'mob_wave' | 'random_teleport' | 'golden_hour' | 'jackpot';

export interface SlotsTriggerEvent {
  player: Player;
  /** named chaos trigger (see the table above) */
  event: SlotChaosEvent;
  symbol: SlotSpecialSymbol;
  tier: SlotTier;
  /** machine block (center) */
  dimensionId: string;
  location: Vector3;
  /** chips won with the jackpot (event 'jackpot' only) */
  jackpotAward?: number;
  /** other online players within JACKPOT_SHOWER_RADIUS of the winner (event 'jackpot' only) */
  nearbyPlayers?: Player[];
}

export interface SlotsLineWin {
  /** 1-based payline: 1 middle, 2 top, 3 bottom, 4 diagonal ↘, 5 diagonal ↗ */
  line: number;
  kind: 'three' | 'wild' | 'berry1' | 'berry2' | 'special';
  /** config symbol id: berries, apple, golden_carrot, emerald, diamond, seven, wild, creeper, tnt, pearl, clock, star */
  symbol: string;
  multiplier: number;
  payout: number;
}

/** Every settled spin (achievements `three_sevens` / `jackpot`, statistics...). */
export interface SlotsSpinEvent {
  player: Player;
  tier: SlotTier;
  lineBet: number;
  spinBet: number;
  totalReturn: number;
  wins: SlotsLineWin[];
  jackpotAward: number;
  /** any payline won with Redstone Sevens (wild-substituted included) */
  threeSevens: boolean;
  /** played at an owned casino machine (§18, no progressive) */
  owned: boolean;
  // ---- slots v2 (SLOTS.md; set only by the v2 service — `lineBet` is then the bet, `spinBet` the stake) ----
  /** v2 machine id: 'overworld' | 'nether' | 'end' */
  machine?: string;
  /** persisted tape string (SLOTS.md §8.1) */
  tape?: string;
  /** free spins or a bonus game triggered by the spin (not bought): contracts `slots_feature` (SLOTS.md §8.7) */
  featureTriggered?: boolean;
  /** a bought feature (counts for `spin_slots`, never for `slots_feature`) */
  bought?: boolean;
  /** jackpot tiers won, 1 Mini … 4 Grand, in tape order */
  jackpotTiers?: number[];
  /** slot win tier of the whole spin (SLOTS.md §10.1): LOSS … EPIC */
  winTier?: string;
}

/** Owned-casino hook (multiplayer): who banks a machine at this location. */
export interface SlotsHouse {
  house: { kind: 'bank' } | { kind: 'bankroll'; id: string };
  /** owner's player id: owners cannot play their own machines */
  ownerId?: string;
  /** machine switched off by its owner / casino closed */
  closed?: boolean;
}
export type SlotsHouseResolver = (dimensionId: string, location: Vector3) => SlotsHouse | undefined;

export interface SlotsApi {
  /** Chaos trigger stream (contract above). Returns an unsubscribe function. */
  onTrigger(listener: (e: SlotsTriggerEvent) => void): () => void;
  /** Every settled spin. Returns an unsubscribe function. */
  onSpin(listener: (e: SlotsSpinEvent) => void): () => void;
  /** Current progressive pool (chips) of a tier; 0 for copper. */
  jackpotPool(tier: SlotTier): number;
  /** Reset one or all progressive pools to their seed (admin). */
  resetJackpots(tier?: SlotTier): void;
  /** Multiplayer: resolve owned-casino machines (default: every machine is banked by the world bank). */
  setHouseResolver(resolver: SlotsHouseResolver | undefined): void;
}
