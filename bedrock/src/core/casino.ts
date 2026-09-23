import { world } from '@minecraft/server';
import { CASINO_MODE_PROP, resolveCasinoEnabled } from './logic/mode';

const OVERRIDE_ID = CASINO_MODE_PROP;

let cachedPackSettings: Record<string, unknown> | undefined;

function packSettings(): Record<string, unknown> {
  if (!cachedPackSettings) {
    try {
      cachedPackSettings = world.getPackSettings();
    } catch {
      cachedPackSettings = {};
    }
  }
  return cachedPackSettings;
}

/** Global casino-mode switch. All features must check it (ModuleContext.guard does). */
export function isCasinoEnabled(): boolean {
  return resolveCasinoEnabled(world.getDynamicProperty(OVERRIDE_ID), packSettings());
}

/** Admin override; `undefined` clears it (falls back to pack setting / default). */
export function setCasinoEnabled(value: boolean | undefined): void {
  world.setDynamicProperty(OVERRIDE_ID, value);
}
