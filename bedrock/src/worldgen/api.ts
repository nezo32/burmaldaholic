/**
 * Public API of the worldgen module for other modules (types only + service name).
 * Provided in onWorldLoad: ctx.services.provide(WORLDGEN_SERVICE, impl).
 *
 * Typical consumers:
 *  - poker: `tablePreset(dim, loc)?.poker` -> fixed Parlor config (Low stakes, 3 bots, Regular-heavy)
 *  - blackjack / roulette: `tablePreset(...)` -> High Roller limits (min 100, 2× tier max, Gold VIP)
 *  - advancements: `onEnter` (Enter a Piglin Parlor) and `casinoAt` on a settled bet (High Roller)
 */
import type { Player } from '@minecraft/server';
import type { TablePreset } from './logic/casinos';
import type { CasinoKind, Vec3 } from './logic/layouts';

export const WORLDGEN_SERVICE = 'worldgen';

export type { CasinoKind, TablePreset, Vec3 };

export interface CasinoInfo {
  readonly id: string;
  readonly kind: CasinoKind;
  /** template id, e.g. `village_casino_desert`, `piglin_parlor`, `high_roller_lounge` */
  readonly layout: string;
  readonly dimensionId: string;
  /** inclusive block bounds */
  readonly min: Vec3;
  readonly max: Vec3;
}

export interface WorldgenApi {
  /** The generated casino containing this block position, if any. */
  casinoAt(dimensionId: string, location: Vec3): CasinoInfo | undefined;
  /**
   * Fixed config of a table placed by worldgen (house table, bank-funded). `undefined` for
   * player-placed tables.
   */
  tablePreset(dimensionId: string, location: Vec3): TablePreset | undefined;
  /** Called when a player walks into a generated casino (once per visit). */
  onEnter(listener: (player: Player, casino: CasinoInfo) => void): void;
  /** Every generated casino in this world. */
  list(): readonly CasinoInfo[];
}
