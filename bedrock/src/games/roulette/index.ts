/**
 * Roulette module (owner: roulette feature dev). Stub - implement here.
 * Rules: gameplay math goes in ./logic (pure, unit-tested); player text via t()/plural()
 * with keys from lang/roulette/*.lang; assets under packs/roulette/.
 */
import type { CasinoModule } from '../../core';

export const rouletteModule: CasinoModule = {
  id: 'roulette',
  config: [{ type: 'bool', name: 'enabled', default: true }],
  // onStartup(ctx) { ctx.registerCommand({ name: 'roulette', description: '...', run: (p) => {} }); },
  // onWorldLoad(ctx) { world.afterEvents.playerInteractWithEntity.subscribe(ctx.guard((e) => {})); },
};
