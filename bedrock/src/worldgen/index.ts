/**
 * Casino Buildings module (owner: worldgen feature dev). Stub - implement here.
 * Rules: gameplay math goes in ./logic (pure, unit-tested); player text via t()/plural()
 * with keys from lang/worldgen/*.lang; assets under packs/worldgen/.
 */
import type { CasinoModule } from '../core';

export const worldgenModule: CasinoModule = {
  id: 'worldgen',
  config: [{ type: 'bool', name: 'enabled', default: true }],
  // onStartup(ctx) { ctx.registerCommand({ name: 'worldgen', description: '...', run: (p) => {} }); },
  // onWorldLoad(ctx) { world.afterEvents.playerInteractWithEntity.subscribe(ctx.guard((e) => {})); },
};
