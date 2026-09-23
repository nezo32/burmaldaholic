/**
 * Public API of the chaos module for other modules (types only + service name).
 *
 * Trigger contract (GAME_DESIGN §13.1):
 * - Slot / wheel / scratch specials call `trigger(player, event, { source })` AFTER crediting
 *   the payout (§8.1: at most one event per spin, priority Star > Clock > Pearl > TNT/Creeper).
 *   It bypasses the ambient chance but respects `chaos.enabled`, `chaos.event.<id>.enabled`,
 *   the per-player cooldown and the §13.4 safety rules. If blocked, nothing happens: the game
 *   still pays its payout and needs no fallback.
 * - Three Clocks: `trigger(player, 'golden_hour', { source: 'slots' })` returns 'started' when
 *   Golden Hour began (show msg.burmaldaholic.slots.three_clocks) or 'cooldown' / 'disabled'
 *   (show msg.burmaldaholic.slots.three_clocks_cooldown). `canStartGoldenHour()` tells upfront.
 * - Progressive jackpot: `jackpot(winner)` = `diamond_rain` for the winner + `chip_shower`
 *   for every other player within 16 blocks (§8.5, §13.1.4).
 * - Big wins (§13.1.3) and Golden Hour bonuses (§13.3) need no call: chaos listens to
 *   ctx.wagers.onSettled itself.
 *
 * Consumers: `const chaos = ctx.services.get<ChaosApi>(CHAOS_SERVICE); chaos?.trigger(...)`.
 */
import type { Player } from '@minecraft/server';
import type { ChaosEventId } from './logic/events';

export type { ChaosEventId } from './logic/events';

export const CHAOS_SERVICE = 'chaos';

/** Where a trigger came from (logging, Golden Hour "rang in by" announcement). */
export type ChaosSource = 'ambient' | 'slots' | 'wheel' | 'scratch' | 'coin_flip' | 'plinko' | 'big_win' | 'jackpot' | 'sunset' | 'admin' | (string & {});

export interface ChaosTriggerOptions {
  source?: ChaosSource;
  /** Ignore the per-player cooldown (jackpots, admin). Safety rules still apply. */
  ignoreCooldown?: boolean;
}

/**
 * - `started`: the event runs now.
 * - `deferred`: a casino form is open; it runs when the form closes (max chaos.deferMaxTicks).
 * - `skipped`: blocked by a safety rule (§13.4) or no safe spot.
 * - `cooldown`: per-player cooldown, or (golden_hour) the Golden Hour cooldown / already active.
 * - `disabled`: casino mode off, chaos off, or this event is disabled in config.
 */
export type ChaosTriggerResult = 'started' | 'deferred' | 'skipped' | 'cooldown' | 'disabled';

export interface ChaosApi {
  /** Fire a named event for `player` (§13.1.2). The event may be rerolled per §13.4. */
  trigger(player: Player, event: ChaosEventId, options?: ChaosTriggerOptions): ChaosTriggerResult;
  /** Progressive jackpot celebration (§8.5). */
  jackpot(winner: Player): void;
  /** Start Golden Hour server-wide (respects its cooldown). `by` = player who rang it in. */
  startGoldenHour(by?: Player, source?: ChaosSource): ChaosTriggerResult;
  /** True when a Golden Hour could start right now (enabled, not active, off cooldown). */
  canStartGoldenHour(): boolean;
  /** chaos.enabled && casino mode on. */
  isEnabled(): boolean;
}
