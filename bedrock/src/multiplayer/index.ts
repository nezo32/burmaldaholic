/**
 * Multiplayer module (owner: multiplayer feature dev). Stub - implement here.
 * Rules: gameplay math goes in ./logic (pure, unit-tested); player text via t()/plural()
 * with keys from lang/multiplayer/*.lang; assets under packs/multiplayer/.
 */
import type { CasinoModule } from '../core';

export const multiplayerModule: CasinoModule = {
  id: 'multiplayer',
  config: [{ type: 'bool', name: 'enabled', default: true }],
  // onStartup(ctx) { ctx.registerCommand({ name: 'multiplayer', description: '...', run: (p) => {} }); },
  // onWorldLoad(ctx) { world.afterEvents.playerInteractWithEntity.subscribe(ctx.guard((e) => {})); },
};
