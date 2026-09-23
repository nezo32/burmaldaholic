/**
 * Public API of the slots module for other modules (types only + service name).
 * Provide it in onWorldLoad: ctx.services.provide(SLOTS_SERVICE, impl).
 */
export const SLOTS_SERVICE = 'slots';

// eslint-disable-next-line @typescript-eslint/no-empty-object-type
export interface SlotsApi {}
