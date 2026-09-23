/**
 * Spin animation schedule (UI.md §7: action-bar numbers in wheel order, slowing down). PURE.
 * The result is decided before the animation starts; frames only replay it.
 */
import { WHEEL_ORDER, wheelIndex } from './wheel';

export interface Frame {
  /** ticks after the spin started */
  at: number;
  /** index into WHEEL_ORDER of the pocket under the ball */
  index: number;
}

/**
 * Frames for a spin that lasts `totalTicks` and stops on `result`. The ball passes
 * `laps` full turns plus the distance from `startIndex`, with a quadratic ease-out so the
 * steps get further apart. The last frame is the result, at or before `totalTicks`.
 */
export function spinFrames(result: number, totalTicks: number, startIndex = 0, laps = 2): Frame[] {
  const n = WHEEL_ORDER.length;
  const target = wheelIndex(result);
  const steps = laps * n + ((target - startIndex + n) % n);
  const end = Math.max(1, Math.floor(totalTicks) - 10);
  const frames: Frame[] = [];
  let lastAt = -1;
  for (let k = 1; k <= steps; k++) {
    // ease-out: progress p = k / steps maps to time t with p = 1 - (1 - t)^2
    const p = k / steps;
    const t = 1 - Math.sqrt(1 - p);
    const at = Math.max(lastAt, Math.round(t * end));
    const index = (startIndex + k) % n;
    if (at === lastAt && frames.length) frames[frames.length - 1] = { at, index };
    else frames.push({ at, index });
    lastAt = at;
  }
  if (!frames.length) frames.push({ at: 0, index: target });
  return frames;
}

/** Pockets around `index` on the wheel (radius each side), for the action-bar strip. */
export function wheelWindow(index: number, radius = 3): number[] {
  const n = WHEEL_ORDER.length;
  const out: number[] = [];
  for (let d = -radius; d <= radius; d++) out.push(WHEEL_ORDER[(((index + d) % n) + n) % n]!);
  return out;
}
