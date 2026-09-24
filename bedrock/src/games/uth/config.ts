/**
 * Ultimate Texas Hold'em config (CONFIG.md §uth).
 *
 * The generated core catalog (core/logic/config-catalog.ts) does not contain the `uth.*` keys
 * yet (tools/gen-config.mjs needs the new CONFIG.md sections and map defaults), so the module
 * declares them itself with the CONFIG.md names and ranges. The two paytable maps
 * (`uth.blindPays`, `uth.tripsPays`) cannot be declared by a module (ModuleConfigDef has no
 * json type): until the catalog has them, each row is a key of its own
 * (`uth.blindPays.royal` ...). Once the catalog defines the map keys they take precedence.
 */
import type { ModuleConfigDef, ModuleContext } from '../../core';
import { DEFAULT_BLIND_PAYS, DEFAULT_TRIPS_PAYS, PAY_HANDS, type Paytable, type UthRules, normalizePaytable } from './logic';

export const UTH_CONFIG: readonly ModuleConfigDef[] = [
  { type: 'bool', name: 'enabled', default: true },
  { type: 'int', name: 'seats', default: 6, min: 1, max: 6 },
  { type: 'bool', name: 'allow3x', default: true },
  { type: 'int', name: 'minAnte', default: 1, min: 1, max: 1_000_000 },
  { type: 'int', name: 'highRollerMinAnte', default: 50, min: 1, max: 1_000_000 },
  { type: 'double', name: 'highRollerMaxMultiplier', default: 2.0, min: 1.0, max: 10.0 },
  { type: 'int', name: 'highRollerMinVipTier', default: 2, min: 0, max: 5 },
  { type: 'bool', name: 'tripsEnabled', default: true },
  ...PAY_HANDS.filter((h) => DEFAULT_BLIND_PAYS[h] !== undefined).map(
    (h): ModuleConfigDef => ({ type: 'double', name: `blindPays.${h}`, default: DEFAULT_BLIND_PAYS[h]!, min: 0, max: 1000 }),
  ),
  ...PAY_HANDS.map((h): ModuleConfigDef => ({ type: 'int', name: `tripsPays.${h}`, default: DEFAULT_TRIPS_PAYS[h]!, min: 0, max: 1000 })),
  { type: 'bool', name: 'validateEdge', default: true },
  { type: 'int', name: 'betTimerTicks', default: 300, min: 100, max: 2400 },
  { type: 'int', name: 'decisionTimerTicks', default: 400, min: 200, max: 2400 },
  { type: 'bool', name: 'autoPlayMadeHands', default: true },
  { type: 'bool', name: 'pvp.enabled', default: true },
  { type: 'int', name: 'pvp.minBank', default: 1000, min: 505, max: 1_000_000_000 },
  { type: 'int', name: 'pvp.minBankerVip', default: 2, min: 0, max: 5 },
  { type: 'double', name: 'pvp.rakePercent', default: 0.01, min: 0, max: 0.1 },
  { type: 'int', name: 'pvp.bankerRounds', default: 10, min: 0, max: 1000 },
  { type: 'bool', name: 'pvp.houseRoundsWhenNoBanker', default: true },
];

/** Names are relative: `uth.<name>` (declared ones get the module prefix). */
const full = (name: string): string => `uth.${name}`;

function paytable(ctx: ModuleContext, map: 'blindPays' | 'tripsPays', fallback: Readonly<Paytable>, integers: boolean): Paytable {
  try {
    // A catalog map key (once gen-config knows CONFIG.md §uth) wins.
    return normalizePaytable(ctx.config.json(full(map)), fallback, integers);
  } catch {
    const raw: Record<string, number> = {};
    for (const h of PAY_HANDS) {
      try {
        raw[h] = ctx.config.num(full(`${map}.${h}`));
      } catch {
        /* row not declared (e.g. the Blind has no trips row) */
      }
    }
    return normalizePaytable(raw, fallback, integers);
  }
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
