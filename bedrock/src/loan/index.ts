/**
 * Loan Shark module (owner: loan feature dev). Stub - implement here.
 * Rules: gameplay math goes in ./logic (pure, unit-tested); player text via t()/plural()
 * with keys from lang/loan/*.lang; assets under packs/loan/.
 */
import type { CasinoModule } from '../core';

export const loanModule: CasinoModule = {
  id: 'loan',
  config: [{ type: 'bool', name: 'enabled', default: true }],
  // onStartup(ctx) { ctx.registerCommand({ name: 'loan', description: '...', run: (p) => {} }); },
  // onWorldLoad(ctx) { world.afterEvents.playerInteractWithEntity.subscribe(ctx.guard((e) => {})); },
};
