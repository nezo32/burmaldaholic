/**
 * SKELETON — PVP.md §6 (mode id `wheel`). Owner: dev B (coin + wheel), task B-M2 (docs/architecture/pvp-bots.md §7).
 * PURE (logic/): no @minecraft imports. Test vectors: PVP.md §16 (same as Java). `enabled()` stays false
 * until implemented, so the engine and the hub never offer the mode.
 */
import type { PvpConfigReader, PvpMode } from '../../../../../core/logic/pvp/mode';

export type Params = { cap: number };
export type Tape = { seatOrder: number[]; u: number };

/** TODO: set to true once draw / score / timeline and the machine UI are done. */
const IMPLEMENTED = false as boolean;

const todo = (): never => {
  throw new Error('TODO(B-M2)');
};

export function createWheelPartyMode(cfg: PvpConfigReader): PvpMode<Params, Tape> {
  return {
    id: 'wheel',
    minPlayers: 2,
    maxPlayers: () => cfg.int('pvp.wheel.maxPlayers'),
    anchor: 'wheel_of_fortune',
    equalStakes: false,
    enabled: () => IMPLEMENTED && cfg.bool('pvp.enabled') && cfg.bool('pvp.wheel.enabled'),
    validate: () => undefined,
    defaults: todo,
    draw: todo,
    score: todo,
    timeline: todo,
    botDecide(): number {
      throw new Error('TODO(B-M2): BOTS.md §4.8 / PVP.md §3.15.4');
    },
    encodeParams: todo,
    decodeParams: todo,
    encodeTape: todo,
    decodeTape: todo,
  };
}
