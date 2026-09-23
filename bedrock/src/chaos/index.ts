/**
 * Chaos Events module (owner: chaos feature dev). Stub - implement here.
 * Rules: gameplay math goes in ./logic (pure, unit-tested); player text via t()/plural()
 * with keys from lang/chaos/*.lang; assets under packs/chaos/.
 */
import type { CasinoModule } from '../core';

export const chaosModule: CasinoModule = {
  id: 'chaos',
  config: [{ type: 'bool', name: 'enabled', default: true }],
  // onStartup(ctx) { ctx.registerCommand({ name: 'chaos', description: '...', run: (p) => {} }); },
  // onWorldLoad(ctx) { world.afterEvents.playerInteractWithEntity.subscribe(ctx.guard((e) => {})); },
};
