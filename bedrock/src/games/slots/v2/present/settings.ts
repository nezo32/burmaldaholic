/**
 * Slot presentation settings (animation/slots.md §2.4, §6.4, §6.5; task BS10; lane B-L9).
 *
 * - Global FX settings `burmaldaholic:anim.*` (core `presentation.fxSettings`): reduce motion, flashes, speed,
 *   volume, celebrations.
 * - Slot-only: turbo `burmaldaholic:anim.turbo` (beats × 0.5, published to spectators as shared speed 200),
 *   NICE "watch the cabinet" `burmaldaholic:slots.fx.cabinet_view` (off by default, disabled by reduce motion).
 * - Skip: the Stop button, or sneaking within `SKIP_RADIUS` blocks of the cabinet.
 *
 * The pure part (profiles, options, budgets) is exported separately for tests; `slotSettings(player)` reads the
 * dynamic properties.
 */
import type { Player } from '@minecraft/server';
import { presentation, type anim } from '../../../../core';
import type { FrameOptions } from './frames';

type FxSettings = presentation.FxSettings;
type TimingProfile = anim.TimingProfile;

export const SLOT_FX_PROP = {
  turbo: 'burmaldaholic:anim.turbo',
  cabinetView: 'burmaldaholic:slots.fx.cabinet_view',
} as const;

/** Sneak-to-skip radius around the cabinet (blocks). */
export const SKIP_RADIUS = 4;
/** Reduce motion scales particle counts (`variable.count` × 0.3). */
export const REDUCED_PARTICLES = 0.3;

export interface SlotSettings {
  readonly fx: FxSettings;
  readonly turbo: boolean;
  /** NICE: close the form during a spin and ease the camera to the cabinet */
  readonly cabinetView: boolean;
  /** `§` codes tint glyphs ([V] B-S0; config `slots.bedrock.glyphTint`) */
  readonly tinting: boolean;
  /** DDUI live form preferred (config `slots.bedrock.ddui`) */
  readonly ddui: boolean;
}

export interface SlotConfigFlags {
  ddui: boolean;
  tinting: boolean;
}

export const DEFAULT_SLOT_FLAGS: SlotConfigFlags = { ddui: true, tinting: true };

/** PURE: combines the stored values (reduce motion disables the cabinet view, §6.5). */
export function resolveSlotSettings(fx: FxSettings, turbo: boolean, cabinetView: boolean, flags: SlotConfigFlags = DEFAULT_SLOT_FLAGS): SlotSettings {
  return { fx, turbo, cabinetView: cabinetView && !fx.reduceMotion, tinting: flags.tinting, ddui: flags.ddui };
}

/**
 * Shared profile: the spinning player's turbo is the only thing that changes shared speed (SLOTS.md §6.4);
 * reduce motion changes how beats look, never when they happen.
 */
export function sharedProfile(s: SlotSettings): TimingProfile {
  return { speedPct: s.turbo ? 200 : 100, reduceMotion: false, flashes: true };
}

/** Local beats (roll-ups, jackpots, banners): viewer speed; the turbo preference overrides it (× 0.5). */
export function slotLocalProfile(s: SlotSettings): TimingProfile {
  return { speedPct: s.turbo ? 200 : s.fx.speedPct, reduceMotion: s.fx.reduceMotion, flashes: s.fx.flashes && !s.fx.reduceMotion };
}

export function frameOptions(s: SlotSettings): FrameOptions {
  return { reduceMotion: s.fx.reduceMotion, flashes: s.fx.flashes && !s.fx.reduceMotion, tinting: s.tinting };
}

export const particleCount = (s: SlotSettings, n: number): number => (s.fx.reduceMotion ? Math.max(1, Math.ceil(n * REDUCED_PARTICLES)) : n);

/** Sound volume × `anim.volume` (0 = silent). */
export const soundVolume = (s: SlotSettings, v: number): number => (v * s.fx.volume) / 100;

/** Celebrations setting: `off` → every tier uses the in-form Win line; `mine` → others' cabinets hide jackpot FX. */
export const showOverlay = (s: SlotSettings): boolean => s.fx.celebrations !== 'off';
export const camerasAllowed = (s: SlotSettings): boolean => !s.fx.reduceMotion;

export interface Vec3 {
  x: number;
  y: number;
  z: number;
}

/** PURE: within the sneak-skip radius of the cabinet centre. */
export function withinSkipRange(p: Vec3, cabinet: Vec3, radius = SKIP_RADIUS): boolean {
  const dx = p.x - cabinet.x;
  const dy = p.y - cabinet.y;
  const dz = p.z - cabinet.z;
  return dx * dx + dy * dy + dz * dz <= radius * radius;
}

// ---------------------------------------------------------------------------------------------------------
// Runtime (dynamic properties)
// ---------------------------------------------------------------------------------------------------------

function readBool(p: Player, id: string, fallback: boolean): boolean {
  try {
    const v = p.getDynamicProperty(id);
    return typeof v === 'boolean' ? v : fallback;
  } catch {
    return fallback;
  }
}

export function slotSettings(p: Player, flags: SlotConfigFlags = DEFAULT_SLOT_FLAGS): SlotSettings {
  return resolveSlotSettings(presentation.fxSettings(p), readBool(p, SLOT_FX_PROP.turbo, false), readBool(p, SLOT_FX_PROP.cabinetView, false), flags);
}

export function setTurbo(p: Player, on: boolean): void {
  p.setDynamicProperty(SLOT_FX_PROP.turbo, on ? true : undefined);
}

export function setCabinetView(p: Player, on: boolean): void {
  p.setDynamicProperty(SLOT_FX_PROP.cabinetView, on ? true : undefined);
}

/** Sneak-to-skip (animation/slots.md §6.4): sneaking within 4 blocks of the cabinet. */
export function wantsSneakSkip(p: Player, cabinet: Vec3 | undefined): boolean {
  try {
    return p.isSneaking && (!cabinet || withinSkipRange(p.location, cabinet));
  } catch {
    return false;
  }
}

/** Rows for Casino Menu → Settings (lane B-L2 renders them as toggles). Lang keys per SLOTS.md §13 / slots.md §10. */
export interface SlotSettingRow {
  readonly id: 'turbo' | 'cabinetView';
  readonly labelKey: string;
  readonly tooltipKey?: string;
  get(p: Player): boolean;
  set(p: Player, on: boolean): void;
}

export const SLOT_SETTING_ROWS: readonly SlotSettingRow[] = [
  { id: 'turbo', labelKey: 'gui.burmaldaholic.slots.turbo', get: (p) => readBool(p, SLOT_FX_PROP.turbo, false), set: setTurbo },
  {
    id: 'cabinetView',
    labelKey: 'gui.burmaldaholic.slots.fx.cabinet_view',
    tooltipKey: 'gui.burmaldaholic.slots.fx.cabinet_view.tooltip',
    get: (p) => readBool(p, SLOT_FX_PROP.cabinetView, false),
    set: setCabinetView,
  },
];
