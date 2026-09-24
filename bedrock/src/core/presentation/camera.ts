/**
 * Camera helpers honouring per-player FX settings (global.md §2.8, A1): `camera.fade` (stable) and
 * `camerashake` via command. Under reduce motion both are skipped; with flashes off, fades are skipped.
 * Every camera beat has a title-only baseline, so skipping never hides information.
 */
import type { Player } from '@minecraft/server';
import { type FxSettings, fxSettings } from './settings';

export interface FadeSpec {
  red: number;
  green: number;
  blue: number;
  inS: number;
  holdS: number;
  outS: number;
}

export function fade(p: Player, f: FadeSpec, s: FxSettings = fxSettings(p)): boolean {
  if (s.reduceMotion || !s.flashes) return false;
  try {
    p.camera.fade({ fadeColor: { red: f.red, green: f.green, blue: f.blue }, fadeTime: { fadeInTime: f.inS, holdTime: f.holdS, fadeOutTime: f.outS } });
    return true;
  } catch {
    return false;
  }
}

export function shake(p: Player, intensity: number, seconds: number, s: FxSettings = fxSettings(p)): boolean {
  if (s.reduceMotion) return false;
  try {
    p.runCommand(`camerashake add @s ${intensity.toFixed(2)} ${seconds.toFixed(2)} positional`);
    return true;
  } catch {
    return false;
  }
}
