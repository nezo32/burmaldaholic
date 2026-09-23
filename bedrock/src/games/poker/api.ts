/**
 * Public API of the poker module for other modules (types only + service name).
 * Provide it in onWorldLoad: ctx.services.provide(POKER_SERVICE, impl).
 */
export const POKER_SERVICE = 'poker';

// eslint-disable-next-line @typescript-eslint/no-empty-object-type
export interface PokerApi {}
