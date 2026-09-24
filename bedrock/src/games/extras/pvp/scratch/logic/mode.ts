/**
 * SKELETON — PVP.md §8 (mode id `scratch`). Owner: dev D (plinko + scratch), task B-M5 (docs/architecture/pvp-bots.md §7).
 * PURE (logic/): no @minecraft imports. Test vectors: PVP.md §16 (same as Java). `enabled()` stays false
 * until implemented, so the engine and the hub never offer the mode.
 */
import type { PvpConfigReader, PvpMode } from '../../../../../core/logic/pvp/mode';

export type Params = Record<string, never>;
export type Tape = { seatOrder: number[]; cells: number[][] };

/** TODO: set to true once draw / score / timeline and the machine UI are done. */
const IMPLEMENTED = false as boolean;

const todo = (): never => {
  throw new Error('TODO(B-M5)');
};

export function createScratchShowdownMode(cfg: PvpConfigReader): PvpMode<Params, Tape> {
  return {
    id: 'scratch',
    minPlayers: 2,
    maxPlayers: () => cfg.int('pvp.scratch.maxPlayers'),
    anchor: 'none',
    equalStakes: true,
    enabled: () => IMPLEMENTED && cfg.bool('pvp.enabled') && cfg.bool('pvp.scratch.enabled'),
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
