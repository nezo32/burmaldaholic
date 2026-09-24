/**
 * Registers the extras-owned PvP modes with the core engine (called once from extras' onWorldLoad), so
 * the two mode developers of extras never edit extras/index.ts. Machine-form entries ("Start a Wheel
 * Party", "Join the battle", Lucky Coin on a player) are added by each mode's owner in their own files
 * (`pvp/<mode>/ui.ts`) and call `ctx.pvp`.
 */
import type { ModuleContext } from '../../../core';
import { createCoinDuelMode } from './coin/logic/mode';
import { createPlinkoBattleMode } from './plinko/logic/mode';
import { createScratchShowdownMode } from './scratch/logic/mode';
import { createWheelPartyMode } from './wheel/logic/mode';

export function registerExtrasPvp(ctx: ModuleContext): void {
  ctx.pvp.registerMode(createCoinDuelMode(ctx.config));
  ctx.pvp.registerMode(createWheelPartyMode(ctx.config));
  ctx.pvp.registerMode(createPlinkoBattleMode(ctx.config));
  ctx.pvp.registerMode(createScratchShowdownMode(ctx.config));
}
