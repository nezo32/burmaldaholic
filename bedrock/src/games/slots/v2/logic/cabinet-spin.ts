/**
 * What the service tells the in-world cabinet about one presented round (lane B-L10 `CabinetSpin`,
 * `present/cabinet-driver.ts`; SLOTS.md §10.6). PURE; every time comes from the built spin timeline, so the
 * cabinet lands on the same ticks as the player's form (F10). Structurally identical to B-L10's `CabinetSpin`.
 */
import type { Timeline } from '../../../../core/logic/anim/timeline';
import { evaluateSpin, ladderAt } from './engine';
import { slotTier } from './tiers';
import { SLOT_BEAT } from './timeline';
import type { MachineDef, MachineId, SpinTape } from './types';
import { REELS } from './types';

export interface CabinetReelSpinData {
  stops: readonly number[];
  startMs: number;
  /** stop time of each reel = END of its REEL_LAND beat (sticky reels: the segment start) */
  landMs: readonly number[];
  winMask: number;
  winShowMs?: number;
  tumbles?: ReadonlyArray<{ atMs: number; winMask: number; mult: number }>;
  finalWindow?: readonly number[];
  stickyMask?: number;
  stickyMs?: number;
  baseMult?: number;
}

export interface CabinetSpinData {
  machine: MachineId;
  spins: readonly CabinetReelSpinData[];
  featureMs?: number;
  hoard?: ReadonlyArray<{ atMs: number; holdMask: number }>;
  wheel?: ReadonlyArray<{ atMs: number; ring: number; segment: number }>;
  celebrate?: { atMs: number; state: 'win' | 'big' | 'jackpot' };
  endMs: number;
}

export function cabinetSpinOf(def: MachineDef, tape: SpinTape, tl: Timeline, bigWinTiers?: readonly [number, number, number, number]): CabinetSpinData {
  const beats = tl.beats;
  // segment starts: SPIN_UP of the base spin, FS_SPIN of each free spin
  const starts: number[] = [];
  if (!tape.bought) starts.push(beats.find((b) => b.kind === SLOT_BEAT.SPIN_UP)?.at ?? 0);
  for (const b of beats) if (b.kind === SLOT_BEAT.FS_SPIN) starts.push(b.at);
  const segEnd = (i: number): number => starts[i + 1] ?? Number.POSITIVE_INFINITY;
  const inSeg = (i: number, at: number): boolean => at >= starts[i]! && at < segEnd(i);
  const spinsIn: Array<{ stops: number[]; free: boolean; sticky: number; stickyAfter: number }> = [];
  if (!tape.bought) spinsIn.push({ stops: tape.stops, free: false, sticky: 0, stickyAfter: 0 });
  let sticky = 0;
  for (const s of tape.freeSpins?.spins ?? []) {
    spinsIn.push({ stops: s.stops, free: true, sticky, stickyAfter: s.stickyMaskAfter });
    sticky = s.stickyMaskAfter;
  }
  const spins: CabinetReelSpinData[] = spinsIn.map((sp, i) => {
    const e = evaluateSpin(def, sp.stops, sp.free, sp.sticky);
    const landMs: number[] = new Array<number>(REELS).fill(starts[i]!);
    for (const b of beats) if (b.kind === SLOT_BEAT.REEL_LAND && inSeg(i, b.at) && b.lane >= 0 && b.lane < REELS) landMs[b.lane] = b.at + b.dur;
    const shows = beats.filter((b) => b.kind === SLOT_BEAT.WIN_SHOW && inSeg(i, b.at));
    const explodes = beats.filter((b) => b.kind === SLOT_BEAT.TUMBLE_EXPLODE && inSeg(i, b.at));
    const stick = beats.find((b) => b.kind === SLOT_BEAT.WILD_STICK && inSeg(i, b.at));
    const ladder = sp.free ? def.ladderFree : def.ladder;
    const first = e.chain ? e.chain.steps[0]!.result : e.ways!;
    const out: CabinetReelSpinData = { stops: sp.stops, startMs: starts[i]!, landMs, winMask: first.winMask, winShowMs: shows[0]?.at };
    if (e.chain && explodes.length) {
      out.tumbles = explodes.map((b, k) => ({ atMs: b.at, winMask: e.chain!.steps[k]!.result.winMask, mult: ladderAt(ladder, k + 1) }));
      out.finalWindow = e.chain.finalWindow.slice();
    }
    if (def.ladder.length) out.baseMult = ladderAt(ladder, 0);
    if (sp.free && def.machine === 'end') {
      out.stickyMask = sp.stickyAfter;
      if (stick) out.stickyMs = stick.at;
    }
    return out;
  });
  const res: CabinetSpinData = { machine: def.machine, spins, endMs: tl.endMs() };
  const feature = beats.find((b) => b.kind === SLOT_BEAT.BONUS_INTRO || b.kind === SLOT_BEAT.FS_INTRO);
  if (feature) res.featureMs = feature.at;
  if (tape.hoard) {
    const intro = beats.find((b) => b.kind === SLOT_BEAT.BONUS_INTRO)!;
    let mask = 0;
    for (const c of tape.hoard.initialCells) mask |= 1 << c;
    const steps = [{ atMs: intro.at, holdMask: mask }];
    const respins = beats.filter((b) => b.kind === SLOT_BEAT.HOARD_RESPIN);
    tape.hoard.respinCells.forEach((cells, i) => {
      for (const c of cells) mask |= 1 << c;
      if (cells.length) steps.push({ atMs: respins[i]!.at + respins[i]!.dur, holdMask: mask });
    });
    res.hoard = steps;
  }
  if (tape.wheel) res.wheel = beats.filter((b) => b.kind === SLOT_BEAT.WHEEL_SPIN).map((b) => ({ atMs: b.at, ring: b.lane, segment: b.args[0]! }));
  const tier = slotTier(tape.totalFifths, bigWinTiers);
  const gate = tl.sharedEndMs();
  if (tape.jackpots.length) res.celebrate = { atMs: gate, state: 'jackpot' };
  else if (tier === 'BIG' || tier === 'MEGA' || tier === 'EPIC') res.celebrate = { atMs: gate, state: 'big' };
  else if (tier === 'WIN' || tier === 'NICE') res.celebrate = { atMs: gate, state: 'win' };
  return res;
}
