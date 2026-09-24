/**
 * Title / subtitle roll-ups (global.md §2.6 Bedrock column): `setTitle` once, then `updateSubtitle` every 2 t (no
 * re-fade) with values from the shared `rollUpValue`, last frame exact. Reduce motion: 2 steps. Runs on the shared
 * scheduler (one session, stops itself). `fx.celebrate` builds its own richer plan; this is the plain helper for
 * games that roll a number under their own title (payout titles, feature totals).
 */
import type { Player } from '@minecraft/server';
import { rollUpValue } from '../logic/anim/rollup';
import type { Raw } from '../logic/rawtext';
import { LOCAL, Timeline } from '../logic/anim/timeline';
import { playTimeline } from './scheduler';

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
  let shown = Number.NaN;
  const show = (amount: number): void => {
    if (amount === shown) return;
    shown = amount;
    try {
      p.onScreenDisplay.updateSubtitle(r.subtitle(amount));
    } catch {
      /* player left */
    }
  };
  try {
    p.onScreenDisplay.setTitle(r.title, { fadeInDuration: r.fadeIn, stayDuration: r.stay, fadeOutDuration: r.fadeOut, subtitle: r.subtitle(0) });
    shown = 0;
  } catch {
    return () => {};
  }
  const durMs = Math.max(0, r.durationTicks * 50);
  const tl = Timeline.builder('fx.title_rollup', 0).clock(LOCAL).add(0, durMs, 'fx.rollup', -1).build();
  // reduce motion: one mid value and the exact total (2 steps)
  const sample = (tMs: number): number => (durMs <= 0 || tMs >= durMs ? r.total : r.reduceMotion ? (tMs * 2 >= durMs ? rollUpValue(r.total, 0.5) : 0) : rollUpValue(r.total, tMs / durMs));
  const session = playTimeline(tl, { frame: (t) => show(sample(t)), end: () => show(r.total) }, { alive: () => p.isValid, periodTicks: r.reduceMotion ? Math.max(1, Math.floor(r.durationTicks / 2)) : 2 });
  return () => session.finishNow();
}
