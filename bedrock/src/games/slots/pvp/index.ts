/** Registers Slot Showdown with the core engine (called once from slots' onWorldLoad). */
import type { ModuleContext } from '../../../core';
import { createSlotShowdownMode } from './logic/mode';

export function registerSlotsPvp(ctx: ModuleContext): void {
  ctx.pvp.registerMode(createSlotShowdownMode(ctx.config));
}
