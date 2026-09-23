/**
 * Public API of the extras module for other modules (types only + service name).
 * Provide it in onWorldLoad: ctx.services.provide(EXTRAS_SERVICE, impl).
 */
export const EXTRAS_SERVICE = 'extras';

// eslint-disable-next-line @typescript-eslint/no-empty-object-type
export interface ExtrasApi {}
