/**
 * Lucky / Unlucky streak HUD segment (GAME_DESIGN §14 "HUD", UI.md §1). PURE.
 * S > 0: "Lucky ×S" in gold; S < 0: "Unlucky ×|S|" in gray-blue; S = 0 hidden;
 * |S| ≥ 7 pulses (alternates with a brighter color on each status refresh).
 */
export interface StreakSegment {
  key: 'hud.burmaldaholic.streak.lucky' | 'hud.burmaldaholic.streak.unlucky';
  n: number;
  color: string;
}

export const PULSE_FROM = 7;

/** `phase` = any counter that advances once per HUD refresh (e.g. floor(tick / 40)). */
export function streakSegment(s: number, phase: number): StreakSegment | undefined {
  if (!Number.isFinite(s) || s === 0) return undefined;
  const n = Math.abs(Math.trunc(s));
  if (n === 0) return undefined;
  const pulse = n >= PULSE_FROM && Math.abs(Math.floor(phase)) % 2 === 1;
  return s > 0
    ? { key: 'hud.burmaldaholic.streak.lucky', n, color: pulse ? '§e§l' : '§6' }
    : { key: 'hud.burmaldaholic.streak.unlucky', n, color: pulse ? '§b§l' : '§9' };
}
