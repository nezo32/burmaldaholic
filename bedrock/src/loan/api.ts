/**
 * Public API of the loan module for other modules (types only + service name).
 * Provide it in onWorldLoad: ctx.services.provide(LOAN_SERVICE, impl).
 */
export const LOAN_SERVICE = 'loan';

// eslint-disable-next-line @typescript-eslint/no-empty-object-type
export interface LoanApi {}
