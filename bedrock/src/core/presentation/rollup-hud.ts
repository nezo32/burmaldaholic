/**
 * Title / subtitle / action-bar roll-ups (global.md §2.6 Bedrock column): `setTitle` once, then
 * `updateSubtitle` every 2 t (no re-fade) with values from the shared `rollUpValue`, last frame exact.
 * Reduce motion: 2 steps. SKELETON helper for `fx.celebrate` (lane B-L1).
 */
import type { Player } from '@minecraft/server';
import { system } from '@minecraft/server';
import { rollUpValue } from '../logic/anim/rollup';
import type { Raw } from '../logic/rawtext';

export interface RollUpTitle {
  title: Raw;
  /** Builds the subtitle for a shown amount (e.g. `§a+N`). */
  subtitle: (amount: number) => Raw;
  total: number;
  durationTicks: number;
  fadeIn: number;
  stay: number;
  fadeOut: number;
  reduceMotion: boolean;
}

/** Starts the roll-up; returns a cancel function that jumps to the exact final value. */
export function titleRollUp(p: Player, r: RollUpTitle): () => void {
  const steps = r.reduceMotion ? 2 : Math.max(1, Math.floor(r.durationTicks / 2));
  let i = 0;
  const show = (amount: number): void => {
    try {
      p.onScreenDisplay.updateSubtitle(r.subtitle(amount));
    } catch {
      /* player left */
    }
  };
  try {
    p.onScreenDisplay.setTitle(r.title, { fadeInDuration: r.fadeIn, stayDuration: r.stay, fadeOutDuration: r.fadeOut, subtitle: r.subtitle(0) });
  } catch {
    return () => {};
  }
  const id = system.runInterval(() => {
    i++;
    show(i >= steps ? r.total : rollUpValue(r.total, i / steps));
    if (i >= steps) system.clearRun(id);
  }, r.reduceMotion ? Math.max(1, r.durationTicks) : 2);
  return () => {
    system.clearRun(id);
    show(r.total);
  };
}
