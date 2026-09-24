/**
 * Ultimate Texas Hold'em config (CONFIG.md §uth). Every `uth.*` key, including the two
 * paytable maps (`uth.blindPays`, `uth.tripsPays`), comes from the generated core catalog.
 */
import type { ModuleContext } from '../../core';
import { DEFAULT_BLIND_PAYS, DEFAULT_TRIPS_PAYS, type Paytable, type UthRules, normalizePaytable } from './logic';

/** Names are relative: `uth.<name>`. */
const full = (name: string): string => `uth.${name}`;

function paytable(ctx: ModuleContext, map: 'blindPays' | 'tripsPays', fallback: Readonly<Paytable>, integers: boolean): Paytable {
  return normalizePaytable(ctx.config.json(full(map)), fallback, integers);
}

export const blindPays = (ctx: ModuleContext): Paytable => paytable(ctx, 'blindPays', DEFAULT_BLIND_PAYS, false);
export const tripsPays = (ctx: ModuleContext): Paytable => paytable(ctx, 'tripsPays', DEFAULT_TRIPS_PAYS, true);

export function readRules(ctx: ModuleContext): UthRules {
  return {
    allow3x: ctx.config.bool(full('allow3x')),
    autoPlayMadeHands: ctx.config.bool(full('autoPlayMadeHands')),
    blindPays: blindPays(ctx),
    tripsPays: tripsPays(ctx),
  };
}
