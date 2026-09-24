/**
 * SKELETON — PVP.md §7 (mode id `plinko`). Owner: dev D (plinko + scratch), task B-M4 (docs/architecture/pvp-bots.md §7).
 * PURE (logic/): no @minecraft imports. Test vectors: PVP.md §16 (same as Java). `enabled()` stays false
 * until implemented, so the engine and the hub never offer the mode.
 */
import type { PvpConfigReader, PvpMode } from '../../../../../core/logic/pvp/mode';

export type Params = { risk: 'low' | 'medium' | 'high'; balls: number };
export type Tape = { seatOrder: number[]; paths: number[][] };

/** TODO: set to true once draw / score / timeline and the machine UI are done. */
const IMPLEMENTED = false as boolean;

const todo = (): never => {
  throw new Error('TODO(B-M4)');
};

export function createPlinkoBattleMode(cfg: PvpConfigReader): PvpMode<Params, Tape> {
  return {
    id: 'plinko',
    minPlayers: 2,
    maxPlayers: () => cfg.int('pvp.plinko.maxPlayers'),
    anchor: 'plinko_machine',
    equalStakes: true,
    enabled: () => IMPLEMENTED && cfg.bool('pvp.enabled') && cfg.bool('pvp.plinko.enabled'),
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
