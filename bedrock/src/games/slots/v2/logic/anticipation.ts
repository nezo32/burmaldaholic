/** Honest anticipation plan (SLOTS.md §10.3) — the only source of reel stop times. Lane S-B3. */
import type { MachineDef, Window } from './types';

export const FIRST_STOP_MS = 600;
export const STAGGER_MS = 150;
export const ANTICIPATE_GAP_MS = 1000;

export const baseStopTimes = (): number[] => [0, 1, 2, 3, 4].map((r) => FIRST_STOP_MS + STAGGER_MS * r);

export function stopTimes(_def: MachineDef, _landed: Window, _enabled: boolean): number[] {
  throw new Error('slots v2 anticipation: not implemented yet');
}
