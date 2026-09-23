/**
 * Slots module (owner: slots feature dev). Stub - implement here.
 * Rules: gameplay math goes in ./logic (pure, unit-tested); player text via t()/plural()
 * with keys from lang/slots/*.lang; assets under packs/slots/.
 */
import type { CasinoModule } from '../../core';

export const slotsModule: CasinoModule = {
  id: 'slots',
  config: [{ type: 'bool', name: 'enabled', default: true }],
  // onStartup(ctx) { ctx.registerCommand({ name: 'slots', description: '...', run: (p) => {} }); },
  // onWorldLoad(ctx) { world.afterEvents.playerInteractWithEntity.subscribe(ctx.guard((e) => {})); },
};
