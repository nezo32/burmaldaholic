/** Compact tape string, identical to Java `TapeCodec` (SLOTS.md §8.1; ≤ 1 200 chars worst case). Lane S-B2. */
import type { SpinTape } from './types';

export function encodeTape(_t: SpinTape): string {
  throw new Error('slots v2 tape codec: not implemented yet');
}

export function decodeTape(_s: string): SpinTape {
  throw new Error('slots v2 tape codec: not implemented yet');
}
