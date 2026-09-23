/**
 * Public API of the vip module for other modules (types only + service name).
 * Provide it in onWorldLoad: ctx.services.provide(VIP_SERVICE, impl).
 */
export const VIP_SERVICE = 'vip';

// eslint-disable-next-line @typescript-eslint/no-empty-object-type
export interface VipApi {}
