/**
 * Spin timeline builder (slots.md §2.2–§2.3), twin of Java `SlotTimeline`; beat kinds are the same strings.
 * Drives the DDUI/classic form frames, the `slot_reels` entity driver and the settle timeout. Lane S-B3
 * (vectors `test/fx/vectors/slots_timeline.json`).
 */
import type { Timeline, TimingProfile } from '../../../../core/logic/anim/timeline';
import type { MachineDef, SpinTape } from './types';

export const SLOT_BEAT = {
  SPIN_UP: 'slots.spin_up',
  REEL_LAND: 'slots.reel_land',
  ANTICIPATE: 'slots.anticipate',
  SYMBOL_LAND: 'slots.symbol_land',
  WIN_SHOW: 'slots.win_show',
  WAY_CYCLE: 'slots.way_cycle',
  TUMBLE_EXPLODE: 'slots.tumble_explode',
  TUMBLE_FALL: 'slots.tumble_fall',
  MULT_UP: 'slots.mult_up',
  WILD_EXPAND: 'slots.wild_expand',
  WILD_STICK: 'slots.wild_stick',
  FS_INTRO: 'slots.fs_intro',
  FS_SPIN: 'slots.fs_spin',
  FS_RETRIGGER: 'slots.fs_retrigger',
  FS_OUTRO: 'slots.fs_outro',
  BONUS_INTRO: 'slots.bonus_intro',
  HUNT_OPEN: 'slots.hunt_open',
  HOARD_RESPIN: 'slots.hoard_respin',
  HOARD_COLLECT: 'slots.hoard_collect',
  WHEEL_SPIN: 'slots.wheel_spin',
  WHEEL_UP: 'slots.wheel_up',
  JACKPOT: 'slots.jackpot',
  ROLLUP: 'slots.rollup',
  MAX_WIN: 'slots.max_win',
  END: 'slots.end',
} as const;

export function buildSlotTimeline(_tape: SpinTape, _def: MachineDef, _shared: TimingProfile, _local: TimingProfile, _seed: number): Timeline {
  throw new Error('slots v2 timeline: not implemented yet');
}
