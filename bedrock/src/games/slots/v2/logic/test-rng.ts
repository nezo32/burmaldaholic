/** Reference `SlotRng`s for tests and shared vectors (docs/architecture/animation.md §7.3: FxRng-backed). PURE. */
import { FxRng } from '../../../../core/logic/anim/seed';
import type { SlotRng } from './types';

export const fxSlotRng = (seed: number): SlotRng => {
  const r = new FxRng(seed);
  return { nextInt: (b) => r.nextInt(b) };
};

/** Scripted rng: returns the queued values (mod bound), then falls back to a seeded rng. */
export const scriptedRng = (values: number[], seed = 1): SlotRng => {
  const fb = fxSlotRng(seed);
  return { nextInt: (b) => (values.length ? values.shift()! % b : fb.nextInt(b)) };
};
