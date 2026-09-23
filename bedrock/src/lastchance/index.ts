/**
 * Last Chance module (owner: lastchance feature dev). Stub - implement here.
 * Rules: gameplay math goes in ./logic (pure, unit-tested); player text via t()/plural()
 * with keys from lang/lastchance/*.lang; assets under packs/lastchance/.
 */
import type { CasinoModule } from '../core';

export const lastchanceModule: CasinoModule = {
  id: 'lastchance',
  config: [{ type: 'bool', name: 'enabled', default: true }],
  // onStartup(ctx) { ctx.registerCommand({ name: 'lastchance', description: '...', run: (p) => {} }); },
  // onWorldLoad(ctx) { world.afterEvents.playerInteractWithEntity.subscribe(ctx.guard((e) => {})); },
};
