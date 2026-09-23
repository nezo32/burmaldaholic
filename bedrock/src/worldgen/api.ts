/**
 * Public API of the worldgen module for other modules (types only + service name).
 * Provide it in onWorldLoad: ctx.services.provide(WORLDGEN_SERVICE, impl).
 */
export const WORLDGEN_SERVICE = 'worldgen';

// eslint-disable-next-line @typescript-eslint/no-empty-object-type
export interface WorldgenApi {}
