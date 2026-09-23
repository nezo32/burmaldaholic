/**
 * Public API of the lastchance module for other modules (types only + service name).
 * Provided in onWorldLoad: ctx.services.provide(LASTCHANCE_SERVICE, impl).
 */
import type { Player } from '@minecraft/server';

export const LASTCHANCE_SERVICE = 'lastchance';

export type LastchanceMode = 'standard' | 'high_stakes';

export interface LastchanceStatus {
  /** undefined = Last Chance does not apply in this world (disabled, casino off, Hardcore DISABLED). */
  mode: LastchanceMode | undefined;
  /** Success probability of the next flip (before any odds modifiers). */
  chance: number;
  cooldownTicks: number;
  /** 0 = ready. */
  remainingTicks: number;
  /** Permanent HP removed by High-Stakes saves. */
  scarHp: number;
}

/** Fired after a successful save (achievements `not_today` / `scarred`, contracts...). */
export interface LastchanceSavedEvent {
  player: Player;
  mode: LastchanceMode;
  /** Chips taken from the balance (plus destroyed chip items value in high stakes). */
  paid: number;
}

export interface LastchanceApi {
  status(player: Player): LastchanceStatus;
  onSaved(listener: (e: LastchanceSavedEvent) => void): void;
}
