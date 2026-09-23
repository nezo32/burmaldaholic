/**
 * Extra Games module (owner: extras feature dev). Stub - implement here.
 * Rules: gameplay math goes in ./logic (pure, unit-tested); player text via t()/plural()
 * with keys from lang/extras/*.lang; assets under packs/extras/.
 */
import type { CasinoModule } from '../../core';

export const extrasModule: CasinoModule = {
  id: 'extras',
  config: [{ type: 'bool', name: 'enabled', default: true }],
  // onStartup(ctx) { ctx.registerCommand({ name: 'extras', description: '...', run: (p) => {} }); },
  // onWorldLoad(ctx) { world.afterEvents.playerInteractWithEntity.subscribe(ctx.guard((e) => {})); },
};
