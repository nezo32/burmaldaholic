/**
 * Public API of the multiplayer module for other modules (types only + service name).
 * Provide it in onWorldLoad: ctx.services.provide(MULTIPLAYER_SERVICE, impl).
 */
export const MULTIPLAYER_SERVICE = 'multiplayer';

// eslint-disable-next-line @typescript-eslint/no-empty-object-type
export interface MultiplayerApi {}
