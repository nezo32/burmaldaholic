/** Cabinet plan input (lane B-L10 `CabinetSpin`): every time comes from the spin timeline (F10). */
import { describe, expect, it } from 'vitest';
import { SHARED_PROFILE } from '../../../../core/logic/anim/timeline';
import { cabinetSpinOf } from './cabinet-spin';
import { defaultMachine } from './config';
import { drawSpin, evaluateSpin } from './engine';
import { emptyPools, poolValues } from './jackpots';
import { fxSlotRng } from './test-rng';
import { SLOT_BEAT, buildSlotTimeline } from './timeline';
import type { MachineId, SpinTape } from './types';

function sample(m: MachineId, seed: number, want: (t: SpinTape) => boolean, buy = false): SpinTape {
  const def = defaultMachine(m);
  const rng = fxSlotRng(seed);
  for (let i = 0; i < 2_000_000; i++) {
    const t = drawSpin({ def, bet: 100, buy, owned: false, pools: poolValues(def.jackpot, emptyPools()) }, rng);
    if (want(t)) return t;
  }
  throw new Error('no sample');
}

describe('cabinetSpinOf', () => {
  it('land times = REEL_LAND ends per segment, one spin per reel segment', () => {
    for (const m of ['overworld', 'nether', 'end'] as const) {
      const def = defaultMachine(m);
      const t = sample(m, 9, (x) => !!x.freeSpins);
      const tl = buildSlotTimeline(t, def, SHARED_PROFILE, SHARED_PROFILE, 1);
      const c = cabinetSpinOf(def, t, tl);
      expect(c.spins).toHaveLength(1 + t.freeSpins!.spins.length);
      const base = tl.beats.filter((b) => b.kind === SLOT_BEAT.REEL_LAND && b.group === 0).map((b) => b.at + b.dur);
      expect(c.spins[0]!.landMs).toEqual(base);
      expect(c.spins[0]!.stops).toEqual(t.stops);
      expect(c.featureMs).toBe(tl.beats.find((b) => b.kind === SLOT_BEAT.FS_INTRO || b.kind === SLOT_BEAT.BONUS_INTRO)!.at);
      expect(c.endMs).toBe(tl.endMs());
    }
  });
  it('Nether tumbles and the final window; Hoard lock masks; wheel rings', () => {
    const ne = defaultMachine('nether');
    const t = sample('nether', 5, (x) => !x.freeSpins && !x.hoard && evaluateSpin(ne, x.stops, false).chain!.steps.length >= 3);
    const c = cabinetSpinOf(ne, t, buildSlotTimeline(t, ne, SHARED_PROFILE, SHARED_PROFILE, 1));
    const chain = evaluateSpin(ne, t.stops, false).chain!;
    expect(c.spins[0]!.tumbles!.map((x) => x.winMask)).toEqual(chain.steps.slice(0, -1).map((s) => s.result.winMask));
    expect(c.spins[0]!.finalWindow).toEqual(chain.finalWindow);
    const h = sample('nether', 44, (x) => !!x.hoard);
    const hc = cabinetSpinOf(ne, h, buildSlotTimeline(h, ne, SHARED_PROFILE, SHARED_PROFILE, 1));
    expect(hc.hoard![0]!.holdMask).toBe(h.hoard!.initialCells.reduce((a, c2) => a | (1 << c2), 0));
    expect(hc.hoard!.length).toBe(1 + h.hoard!.respinCells.filter((x) => x.length).length);
    const end = defaultMachine('end');
    const w = sample('end', 21, (x) => !!x.wheel);
    const wc = cabinetSpinOf(end, w, buildSlotTimeline(w, end, SHARED_PROFILE, SHARED_PROFILE, 1));
    expect(wc.wheel!.map((x) => [x.ring, x.segment])).toEqual(w.wheel!.segments.map((s, i) => [i, s]));
  });
});
