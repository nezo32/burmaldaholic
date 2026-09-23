/**
 * Public API of the roulette module for other modules (types only + service name).
 * Provide it in onWorldLoad: ctx.services.provide(ROULETTE_SERVICE, impl).
 */
export const ROULETTE_SERVICE = 'roulette';

// eslint-disable-next-line @typescript-eslint/no-empty-object-type
export interface RouletteApi {}
