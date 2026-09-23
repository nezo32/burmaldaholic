/**
 * Public API of the craps module for other modules (types only + service name).
 * Provided in onWorldLoad: ctx.services.provide(CRAPS_SERVICE, impl).
 */
import type { Player } from '@minecraft/server';

export const CRAPS_SERVICE = 'craps';

/** Fired when a shooter makes their point (advancements: `hot_shooter` = 3 in a row). */
export type PointMadeListener = (shooter: Player, pointsInRow: number) => void;

export interface CrapsApi {
  onPointMade(listener: PointMadeListener): void;
}
