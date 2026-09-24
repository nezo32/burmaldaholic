/**
 * Honest anticipation plan (SLOTS.md §10.3) — the only source of reel stop times. PURE. Lane S-B3.
 *
 * After reel k (1…4) has stopped, reel k+1 is anticipated (stops 1 000 ms after reel k instead of 150 ms) iff the
 * cells ALREADY VISIBLE on reels 1…k show:
 *  - ≥ 2 scatters and at least one later reel whose strip holds a scatter; or
 *  - (bonus machines, base game only) the bonus symbol on every bonus reel but the last (all of them stopped) and
 *    the last bonus reel not stopped yet (Overworld: chests on 1 and 3, reel 5 pending; End: crystals on 2 and 3,
 *    reel 4 pending); or
 *  - (coin machines, base game only) ≥ 4 coins and the remaining reels can still bring the total to the trigger.
 * Nothing else ever changes stop times. Sticky reels (End free spins) are part of the landed window as WWW.
 */
import { bonusOf, coinOf, scatterOf } from './engine';
import type { MachineDef, Window } from './types';
import { REELS, ROWS } from './types';

export const FIRST_STOP_MS = 600;
export const STAGGER_MS = 150;
export const ANTICIPATE_GAP_MS = 1000;
/** Coins that make the Hoard condition start (SLOTS.md §10.3: "≥ 4 coins"). */
export const HOARD_ANTICIPATION_COINS = 4;

export const baseStopTimes = (): number[] => [0, 1, 2, 3, 4].map((r) => FIRST_STOP_MS + STAGGER_MS * r);

const stripHas = (def: MachineDef, r: number, sym: number): boolean => sym >= 0 && def.strips[r]!.includes(sym);

/** Most copies of `sym` a window of reel r can show (from the real strip). */
function maxVisible(def: MachineDef, r: number, sym: number): number {
  const s = def.strips[r]!;
  let best = 0;
  for (let t = 0; t < s.length; t++) {
    let n = 0;
    for (let y = 0; y < ROWS; y++) if (s[(t + y) % s.length] === sym) n++;
    best = Math.max(best, n);
  }
  return best;
}

/**
 * The §10.3 condition on reels 0…k−1 (k reels stopped, 1 ≤ k ≤ 4). `features` = false in free spins (bonus
 * symbols and coins are inert there).
 */
export function anticipates(def: MachineDef, landed: Window, k: number, features = true): boolean {
  const sc = scatterOf(def);
  let scat = 0;
  for (let r = 0; r < k; r++) for (let y = 0; y < ROWS; y++) if (landed[r * ROWS + y] === sc) scat++;
  if (scat >= 2) for (let r = k; r < REELS; r++) if (stripHas(def, r, sc)) return true;
  if (!features) return false;
  const bn = bonusOf(def);
  if (bn >= 0 && def.bonusReelsMask) {
    // every bonus reel but the last has stopped and shows the symbol; the last one has not stopped yet
    const reels: number[] = [];
    for (let r = 0; r < REELS; r++) if (def.bonusReelsMask & (1 << r)) reels.push(r);
    const last = reels[reels.length - 1]!;
    const need = reels.slice(0, -1);
    if (need.length > 0 && need[need.length - 1]! < k && last >= k) {
      let ok = true;
      for (const r of need) {
        let has = false;
        for (let y = 0; y < ROWS; y++) if (landed[r * ROWS + y] === bn) has = true;
        if (!has) ok = false;
      }
      if (ok) return true;
    }
  }
  const cn = coinOf(def);
  if (cn >= 0 && def.hoard) {
    let coins = 0;
    for (let r = 0; r < k; r++) for (let y = 0; y < ROWS; y++) if (landed[r * ROWS + y] === cn) coins++;
    if (coins >= HOARD_ANTICIPATION_COINS) {
      let possible = 0;
      for (let r = k; r < REELS; r++) possible += maxVisible(def, r, cn);
      if (coins + possible >= def.hoard.trigger) return true;
    }
  }
  return false;
}

/**
 * Stop time of each reel (ms from spin start, normal speed). `enabled` = `slots.anticipation`; `features` =
 * false in free spins.
 */
export function stopTimes(def: MachineDef, landed: Window, enabled: boolean, features = true): number[] {
  const t = [FIRST_STOP_MS];
  for (let k = 1; k < REELS; k++) t.push(t[k - 1]! + (enabled && anticipates(def, landed, k, features) ? ANTICIPATE_GAP_MS : STAGGER_MS));
  return t;
}

/** Reels (0-based) whose stop was delayed by anticipation. */
export function anticipatedReels(times: readonly number[]): number[] {
  const out: number[] = [];
  for (let r = 1; r < times.length; r++) if (times[r]! - times[r - 1]! === ANTICIPATE_GAP_MS) out.push(r);
  return out;
}
