/**
 * Public API of the lastchance module for other modules (types only + service name).
 * Provide it in onWorldLoad: ctx.services.provide(LASTCHANCE_SERVICE, impl).
 */
export const LASTCHANCE_SERVICE = 'lastchance';

// eslint-disable-next-line @typescript-eslint/no-empty-object-type
export interface LastchanceApi {}
