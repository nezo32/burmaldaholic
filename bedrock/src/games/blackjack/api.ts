/**
 * Public API of the blackjack module for other modules (types only + service name).
 * Provide it in onWorldLoad: ctx.services.provide(BLACKJACK_SERVICE, impl).
 */
export const BLACKJACK_SERVICE = 'blackjack';

// eslint-disable-next-line @typescript-eslint/no-empty-object-type
export interface BlackjackApi {}
