/**
 * SKELETON — PVP.md §4 (mode id `coin`). Owner: dev B (coin + wheel), task B-M1 (docs/architecture/pvp-bots.md §7).
 * PURE (logic/): no @minecraft imports. Test vectors: PVP.md §16 (same as Java). `enabled()` stays false
 * until implemented, so the engine and the hub never offer the mode.
 */
import type { PvpConfigReader, PvpMode } from '../../../../../core/logic/pvp/mode';

export type Params = { stake: number; challengerHeads: boolean };
export type Tape = { seatOrder: number[]; heads: boolean };

/** TODO: set to true once draw / score / timeline and the machine UI are done. */
const IMPLEMENTED = false as boolean;

const todo = (): never => {
  throw new Error('TODO(B-M1)');
};

export function createCoinDuelMode(cfg: PvpConfigReader): PvpMode<Params, Tape> {
  return {
    id: 'coin',
    minPlayers: 2,
    maxPlayers: () => 2,
    anchor: 'none',
    equalStakes: true,
    enabled: () => IMPLEMENTED && cfg.bool('pvp.enabled') && cfg.bool('pvp.coin.enabled'),
    validate: () => undefined,
    defaults: todo,
    draw: todo,
    score: todo,
    timeline: todo,
    botDecide(): number {
      throw new Error('TODO(B-M1): BOTS.md §4.8 / PVP.md §3.15.4');
    },
    encodeParams: todo,
    decodeParams: todo,
    encodeTape: todo,
    decodeTape: todo,
  };
}
