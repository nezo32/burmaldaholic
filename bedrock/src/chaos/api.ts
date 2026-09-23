/**
 * Public API of the chaos module for other modules (types only + service name).
 * Provide it in onWorldLoad: ctx.services.provide(CHAOS_SERVICE, impl).
 */
export const CHAOS_SERVICE = 'chaos';

// eslint-disable-next-line @typescript-eslint/no-empty-object-type
export interface ChaosApi {}
