/**
 * Craps module (owner: craps feature dev). Stub - implement here.
 * Rules: gameplay math goes in ./logic (pure, unit-tested); player text via t()/plural()
 * with keys from lang/craps/*.lang; assets under packs/craps/.
 */
import type { CasinoModule } from '../../core';

export const crapsModule: CasinoModule = {
  id: 'craps',
  config: [{ type: 'bool', name: 'enabled', default: true }],
  // onStartup(ctx) { ctx.registerCommand({ name: 'craps', description: '...', run: (p) => {} }); },
  // onWorldLoad(ctx) { world.afterEvents.playerInteractWithEntity.subscribe(ctx.guard((e) => {})); },
};
