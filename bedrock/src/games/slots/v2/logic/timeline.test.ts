/**
 * Anticipation plan and spin timeline (SLOTS.md §10.2–§10.4, slots.md §2.1–§2.3): honest anticipation (property
 * test, §10.3 / §15.12), fidelity (the timeline lands on the paid window; terminal == server result), turbo and
 * local-speed scaling, the reveal gate, reduce motion.
 */
import { describe, expect, it } from 'vitest';
import { LOCAL, SHARED, SHARED_PROFILE, type TimingProfile } from '../../../../core/logic/anim/timeline';
import { ANTICIPATE_GAP_MS, STAGGER_MS, anticipatedReels, baseStopTimes, stopTimes } from './anticipation';
import { defaultMachine } from './config';
import { drawSpin, evaluateSpin, huntOpens } from './engine';
import { emptyPools, poolValues } from './jackpots';
import { encodeTape } from './tape-codec';
import { fxSlotRng } from './test-rng';
import { BONUS_CODE, SLOT_BEAT, buildSlotTimeline, huntPauseMs, slotTerminal } from './timeline';
import { MACHINE_IDS, type MachineDef, type MachineId, type SpinTape, windowFromStops } from './types';

const TURBO: TimingProfile = { speedPct: 200, reduceMotion: false, flashes: true };
const RM: TimingProfile = { speedPct: 100, reduceMotion: true, flashes: false };

/** Independent restatement of the §10.3 condition, reading the rules literally. */
function condition(def: MachineDef, w: readonly number[], k: number): boolean {
  const code = (r: number, y: number): string => def.codes[w[r * 3 + y]!]!;
  const vis = (c: string): number => {
    let n = 0;
    for (let r = 0; r < k; r++) for (let y = 0; y < 3; y++) if (code(r, y) === c) n++;
    return n;
  };
  const onReel = (r: number, c: string): boolean => [0, 1, 2].some((y) => code(r, y) === c);
  if (vis('SC') >= 2) return true; // every later reel of the default strips carries a scatter
  if (def.machine === 'overworld') return k >= 3 && k < 5 && onReel(0, 'BN') && onReel(2, 'BN');
  if (def.machine === 'end') return k === 3 && onReel(1, 'BN') && onReel(2, 'BN');
  const coins = vis('CN');
  // Nether: no window of the default strips shows more than 2 coins on one reel
  let possible = 0;
  for (let r = k; r < 5; r++) possible += 2;
  return coins >= 4 && coins + possible >= 6;
}

describe('honest anticipation (SLOTS.md §10.3, §15.12)', () => {
  it('base schedule 600 / 750 / 900 / 1 050 / 1 200 and the switch', () => {
    expect(baseStopTimes()).toEqual([600, 750, 900, 1050, 1200]);
    const def = defaultMachine('overworld');
    // two scatters on reels 1 and 2: R1 stop 11 shows SC at row 0; R2 stop 11 shows SC at row 0
    const w = windowFromStops(def, [11, 11, 0, 0, 0]);
    expect(stopTimes(def, w, true)).toEqual([600, 750, 1750, 2750, 3750]);
    expect(stopTimes(def, w, false)).toEqual(baseStopTimes());
    expect(anticipatedReels(stopTimes(def, w, true))).toEqual([2, 3, 4]);
  });
  for (const m of MACHINE_IDS) {
    it(`${m}: anticipation happens iff the condition holds on already-stopped reels (random tapes)`, () => {
      const def = defaultMachine(m);
      const rng = fxSlotRng(m.length * 17);
      let anticipated = 0;
      const n = 20_000;
      for (let i = 0; i < n; i++) {
        const stops = def.strips.map((s) => rng.nextInt(s.length));
        const w = windowFromStops(def, stops);
        const t = stopTimes(def, w, true);
        for (let k = 1; k < 5; k++) {
          const gap = t[k]! - t[k - 1]!;
          expect(gap).toBe(condition(def, w, k) ? ANTICIPATE_GAP_MS : STAGGER_MS);
          if (gap === ANTICIPATE_GAP_MS) anticipated++;
        }
      }
      expect(anticipated).toBeGreaterThan(0);
    }, 120_000);
  }
});

const pools = (m: MachineId): number[] => poolValues(defaultMachine(m).jackpot, emptyPools());

function sample(m: MachineId, seed: number, want: (t: SpinTape) => boolean, buy = false): SpinTape {
  const def = defaultMachine(m);
  const rng = fxSlotRng(seed);
  for (let i = 0; i < 2_000_000; i++) {
    const t = drawSpin({ def, bet: 100, buy, owned: false, pools: pools(m) }, rng);
    if (want(t)) return t;
  }
  throw new Error('no sample');
}

describe('spin timeline (slots.md §2.2)', () => {
  it('fidelity: REEL_LAND lands every reel on the drawn stop at the anticipation plan time; terminal = server result', () => {
    for (const m of MACHINE_IDS) {
      const def = defaultMachine(m);
      const rng = fxSlotRng(m.length);
      for (let i = 0; i < 300; i++) {
        const t = drawSpin({ def, bet: 100, buy: false, owned: false, pools: pools(m) }, rng);
        const tl = buildSlotTimeline(t, def, SHARED_PROFILE, SHARED_PROFILE, 1);
        const base = tl.beats.filter((b) => b.kind === SLOT_BEAT.REEL_LAND && b.group === 0);
        expect(base.map((b) => b.args[0])).toEqual(t.stops);
        const e = evaluateSpin(def, t.stops, false);
        const landed = e.chain ? e.chain.steps[0]!.window : e.finalWindow;
        const times = stopTimes(def, landed, true);
        expect(base.map((b) => b.at + b.dur)).toEqual(times);
        const term = slotTerminal(t, def);
        expect(term.totalFifths).toBe(t.totalFifths);
        const roll = tl.beats.find((b) => b.kind === SLOT_BEAT.ROLLUP);
        if (t.totalFifths > 0) expect(roll!.args[1]).toBe(t.totalFifths);
        else expect(roll).toBeUndefined();
        expect(tl.beats.at(-1)!.kind).toBe(SLOT_BEAT.END);
        // the local part starts at the reveal gate
        for (const b of tl.beats) if (b.clock === LOCAL) expect(b.at).toBeGreaterThanOrEqual(tl.sharedEndMs());
      }
    }
  });

  it('turbo halves the shared part; the local profile only scales local beats; reduce motion caps roll-ups', () => {
    const def = defaultMachine('overworld');
    const t = sample('overworld', 3, (x) => x.totalFifths >= 50 && !x.freeSpins && !x.hunt);
    const n = buildSlotTimeline(t, def, SHARED_PROFILE, SHARED_PROFILE, 1);
    const f = buildSlotTimeline(t, def, TURBO, SHARED_PROFILE, 1);
    expect(f.sharedEndMs()).toBe(Math.floor(n.sharedEndMs() / 2));
    const roll = (tl: typeof n): number => tl.beats.find((b) => b.kind === SLOT_BEAT.ROLLUP)!.dur;
    expect(roll(f)).toBe(roll(n));
    const slow = buildSlotTimeline(t, def, SHARED_PROFILE, { speedPct: 50, reduceMotion: false, flashes: true }, 1);
    expect(slow.sharedEndMs()).toBe(n.sharedEndMs());
    expect(roll(slow)).toBe(2 * roll(n));
    expect(roll(buildSlotTimeline(t, def, SHARED_PROFILE, RM, 1))).toBeLessThanOrEqual(300);
    // reduce motion never changes shared timing (spectators stay in sync)
    expect(buildSlotTimeline(t, def, SHARED_PROFILE, RM, 1).sharedEndMs()).toBe(n.sharedEndMs());
  });

  it('Nether tumbles: one WIN_SHOW / EXPLODE / MULT_UP / FALL per winning step, ladder values in MULT_UP', () => {
    const def = defaultMachine('nether');
    const t = sample('nether', 5, (x) => !x.freeSpins && !x.hoard && evaluateSpin(def, x.stops, false).chain!.steps.length >= 4);
    const tl = buildSlotTimeline(t, def, SHARED_PROFILE, SHARED_PROFILE, 7);
    const chain = evaluateSpin(def, t.stops, false).chain!;
    const wins = chain.steps.filter((s) => s.result.payFifths > 0);
    expect(tl.beats.filter((b) => b.kind === SLOT_BEAT.TUMBLE_EXPLODE)).toHaveLength(wins.length);
    expect(tl.beats.filter((b) => b.kind === SLOT_BEAT.MULT_UP).map((b) => b.args[0])).toEqual(wins.map((s) => def.ladder[Math.min(s.step + 1, 3)]));
    expect(tl.beats.filter((b) => b.kind === SLOT_BEAT.WIN_SHOW).map((b) => b.args[1])).toEqual(wins.map((s) => s.payFifths));
    // explode 250 then fall 300 then pause 200 per step
    const ex = tl.beats.filter((b) => b.kind === SLOT_BEAT.TUMBLE_EXPLODE);
    for (let i = 1; i < ex.length; i++) expect(ex[i]!.at - ex[i - 1]!.at).toBe(1350);
  });

  it('free spins: intro, one FS_SPIN per spin at 0.8×, sticky reels do not spin, expand + stick beats', () => {
    const def = defaultMachine('end');
    const t = sample('end', 8, (x) => x.freeSpins!.spins.some((s) => s.stickyMaskAfter), true);
    const tl = buildSlotTimeline(t, def, SHARED_PROFILE, SHARED_PROFILE, 1);
    expect(tl.beats.filter((b) => b.kind === SLOT_BEAT.FS_SPIN)).toHaveLength(t.freeSpins!.spins.length);
    expect(tl.beats.filter((b) => b.kind === SLOT_BEAT.SPIN_UP)[0]!.dur).toBe(96);
    let sticky = 0;
    const lands = tl.beats.filter((b) => b.kind === SLOT_BEAT.REEL_LAND);
    let li = 0;
    for (const s of t.freeSpins!.spins) {
      for (let r = 0; r < 5; r++) {
        const bit = r >= 1 && r <= 3 ? 1 << (r - 1) : 0;
        if (bit && sticky & bit) continue;
        expect(lands[li]!.lane).toBe(r);
        expect(lands[li]!.args[0]).toBe(s.stops[r]);
        expect(lands[li]!.dur).toBe(280);
        li++;
      }
      sticky = s.stickyMaskAfter;
    }
    expect(li).toBe(lands.length);
    const newSticky = t.freeSpins!.spins.reduce((acc, s, i, a) => acc + popcount(s.stickyMaskAfter & ~(i ? a[i - 1]!.stickyMaskAfter : 0)), 0);
    expect(tl.beats.filter((b) => b.kind === SLOT_BEAT.WILD_EXPAND)).toHaveLength(newSticky);
    expect(tl.beats.filter((b) => b.kind === SLOT_BEAT.WILD_STICK)).toHaveLength(newSticky);
    expect(tl.beats.find((b) => b.kind === SLOT_BEAT.FS_OUTRO)!.args[0]).toBe(t.freeSpins!.payFifths);
  });

  it('bonus steps: the hunt pauses after its intro (args[0] = 1, no HUNT_OPEN in the spin timeline); wheel rings; jackpots', () => {
    const ow = defaultMachine('overworld');
    const h = sample('overworld', 12, (x) => !!x.hunt && huntOpens(ow, x) >= 2);
    expect(h.hunt!.opened).toBe(0);
    const tl = buildSlotTimeline(h, ow, SHARED_PROFILE, SHARED_PROFILE, 1);
    expect(tl.beats.filter((b) => b.kind === SLOT_BEAT.HUNT_OPEN)).toEqual([]);
    const intro = tl.beats.find((b) => b.kind === SLOT_BEAT.BONUS_INTRO)!;
    expect(intro.args[0]).toBe(BONUS_CODE.hunt);
    expect(huntPauseMs(tl)).toBe(intro.at + intro.dur);
    const end = defaultMachine('end');
    const w = sample('end', 21, (x) => !!x.wheel && x.wheel.segments.length >= 2);
    const wt = buildSlotTimeline(w, end, SHARED_PROFILE, SHARED_PROFILE, 1);
    expect(wt.beats.find((b) => b.kind === SLOT_BEAT.BONUS_INTRO)!.args[0]).toBe(BONUS_CODE.wheel);
    const spins = wt.beats.filter((b) => b.kind === SLOT_BEAT.WHEEL_SPIN);
    expect(spins.map((b) => b.args[0])).toEqual(w.wheel!.segments);
    expect(spins.map((b) => b.lane)).toEqual(w.wheel!.segments.map((_, i) => i));
    expect(spins[0]!.dur).toBe(4500 + 600);
    expect(wt.beats.filter((b) => b.kind === SLOT_BEAT.WHEEL_UP)).toHaveLength(w.wheel!.segments.length - 1);
    expect(huntPauseMs(wt)).toBeUndefined();
    // the i-th JACKPOT beat is tape.jackpots[i] (local, before the spin roll-up)
    const j = sample('end', 13, (x) => x.jackpots.length > 0);
    const jt = buildSlotTimeline(j, end, SHARED_PROFILE, SHARED_PROFILE, 1);
    const jb = jt.beats.filter((b) => b.kind === SLOT_BEAT.JACKPOT);
    expect(jb.map((b) => [b.args[0], b.args[1], b.clock])).toEqual(j.jackpots.map((x, i) => [x.tier, i, LOCAL]));
    const roll = jt.beats.find((b) => b.kind === SLOT_BEAT.ROLLUP);
    if (roll) expect(roll.at).toBeGreaterThanOrEqual(jb.at(-1)!.at + jb.at(-1)!.dur);
  });

  it('Hoard: BONUS_INTRO args[0] = 2, one HOARD_RESPIN per respin, one collect', () => {
    const def = defaultMachine('nether');
    const t = sample('nether', 44, (x) => !!x.hoard);
    const tl = buildSlotTimeline(t, def, SHARED_PROFILE, SHARED_PROFILE, 1);
    expect(tl.beats.find((b) => b.kind === SLOT_BEAT.BONUS_INTRO)!.args[0]).toBe(BONUS_CODE.hoard);
    expect(tl.beats.filter((b) => b.kind === SLOT_BEAT.HOARD_RESPIN).map((b) => b.args[0])).toEqual(t.hoard!.respinCells.map((_, i) => i));
    expect(tl.beats.filter((b) => b.kind === SLOT_BEAT.HOARD_COLLECT)).toHaveLength(1);
  });

  it('WAY_CYCLE only when the base spin is the last reel segment (frames ordinal contract)', () => {
    const def = defaultMachine('overworld');
    const fs = sample('overworld', 5, (x) => !!x.freeSpins && evaluateSpin(def, x.stops, false).payFifths > 0);
    expect(buildSlotTimeline(fs, def, SHARED_PROFILE, SHARED_PROFILE, 1).beats.filter((b) => b.kind === SLOT_BEAT.WAY_CYCLE)).toEqual([]);
    const plain = sample('overworld', 6, (x) => !x.freeSpins && !x.hunt && x.totalFifths >= 10);
    expect(buildSlotTimeline(plain, def, SHARED_PROFILE, SHARED_PROFILE, 1).beats.filter((b) => b.kind === SLOT_BEAT.WAY_CYCLE).length).toBeGreaterThan(0);
  });

  it('beat args never reveal hunt entries (the picks reveal them, D6/F7)', () => {
    const ow = defaultMachine('overworld');
    const h = sample('overworld', 31, (x) => !!x.hunt);
    const tl = buildSlotTimeline(h, ow, SHARED_PROFILE, SHARED_PROFILE, 1);
    const pause = huntPauseMs(tl)!;
    for (const b of tl.beats) if (b.at < pause) expect([SLOT_BEAT.HUNT_OPEN, SLOT_BEAT.JACKPOT]).not.toContain(b.kind);
  });

  it('max win: shared cue + local plate before the roll-up', () => {
    const def = defaultMachine('nether');
    const t = { ...sample('nether', 1, (x) => x.totalFifths > 0 && !x.freeSpins), capHit: true };
    const tl = buildSlotTimeline(t, def, SHARED_PROFILE, SHARED_PROFILE, 1);
    const mw = tl.beats.filter((b) => b.kind === SLOT_BEAT.MAX_WIN);
    expect(mw.map((b) => b.clock)).toEqual([SHARED, LOCAL]);
    expect(mw[1]!.at).toBeLessThanOrEqual(tl.beats.find((b) => b.kind === SLOT_BEAT.ROLLUP)!.at);
  });

  it('identical tapes build byte-identical timelines (the vector contract)', () => {
    const def = defaultMachine('end');
    const t = sample('end', 2, (x) => !!x.freeSpins);
    const a = buildSlotTimeline(t, def, TURBO, SHARED_PROFILE, 99).toCanonicalJson();
    expect(buildSlotTimeline(t, def, TURBO, SHARED_PROFILE, 99).toCanonicalJson()).toBe(a);
    expect(encodeTape(t).length).toBeGreaterThan(0);
  });
});

const popcount = (x: number): number => {
  let n = 0;
  for (; x; x &= x - 1) n++;
  return n;
};
