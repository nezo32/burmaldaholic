/**
 * SKELETON — PVP.md §5 (mode id `slots`). Owner: dev C (slots), task B-M3 (docs/architecture/pvp-bots.md §7).
 * PURE (logic/): no @minecraft imports. Test vectors: PVP.md §16 (same as Java). `enabled()` stays false
 * until implemented, so the engine and the hub never offer the mode.
 */
import type { PvpConfigReader, PvpMode } from '../../../../core/logic/pvp/mode';

export type Params = { tier: 'copper' | 'gold' | 'netherite'; spins: number };
export type Tape = { seatOrder: number[]; hot: number[]; grids: number[][][] };

/** TODO: set to true once draw / score / timeline and the machine UI are done. */
const IMPLEMENTED = false as boolean;

const todo = (): never => {
  throw new Error('TODO(B-M3)');
};

export function createSlotShowdownMode(cfg: PvpConfigReader): PvpMode<Params, Tape> {
  return {
    id: 'slots',
    minPlayers: 2,
    maxPlayers: () => cfg.int('pvp.slots.maxPlayers'),
    anchor: 'slot_machine',
    equalStakes: true,
    enabled: () => IMPLEMENTED && cfg.bool('pvp.enabled') && cfg.bool('pvp.slots.enabled'),
    validate: () => undefined,
    defaults: todo,
    draw: todo,
    score: todo,
    timeline: todo,
    encodeParams: todo,
    decodeParams: todo,
    encodeTape: todo,
    decodeTape: todo,
  };
}
