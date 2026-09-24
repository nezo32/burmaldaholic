/**
 * Shared slots vectors (docs/architecture/animation.md §3.4, §7.3): `test/fx/vectors/slots_engine.json` (windows,
 * way wins, tumble chains, seeded draws → tape strings, codec) and `slots_timeline.json` (20 tapes per machine →
 * canonical beat lists). Every output is recomputed here from the inputs. The files are produced by
 * `FX_DUMP_VECTORS=1 npx vitest run src/games/slots/v2/logic/vectors.test.ts`; once the Java lane's generator
 * (`java/src/test/resources/fx/vectors/`) lands, `npm run sync:vectors` makes these byte-identical to Java's and
 * this test must stay green on them.
 */
import fs from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';
import { seedMix } from '../../../../core/logic/anim/seed';
import { SHARED_PROFILE, type TimingProfile } from '../../../../core/logic/anim/timeline';
import { defaultMachine } from './config';
import { drawSpin, evaluateWays, featureCode, runTumbles } from './engine';
import { emptyPools, poolValues } from './jackpots';
import { decodeTape, encodeTape } from './tape-codec';
import { fxSlotRng } from './test-rng';
import { buildSlotTimeline } from './timeline';
import { MACHINE_IDS, type MachineId, windowFromStops } from './types';

const DIR = path.resolve(__dirname, '../../../../../test/fx/vectors');
const ENGINE = path.join(DIR, 'slots_engine.json');
const TIMELINE = path.join(DIR, 'slots_timeline.json');
const DUMP = process.env.FX_DUMP_VECTORS === '1';

interface WindowVec {
  machine: MachineId;
  stops: number[];
  window: number[];
  wins: number[][];
  pay: number;
  scatters: number;
  bonusCount: number;
  coins: number;
}
interface TumbleVec {
  stops: number[];
  windows: number[][];
  pays: number[];
  multipliers: number[];
  tops: number[][];
  total: number;
}
interface DrawVec {
  machine: MachineId;
  bet: number;
  buy: boolean;
  owned: boolean;
  seed: number;
  pools: number[];
  tape: string;
}
interface EngineVectors {
  v: 1;
  windows: WindowVec[];
  tumbles: TumbleVec[];
  draws: DrawVec[];
}
interface TimelineVec {
  tape: string;
  sharedPct: number;
  localPct: number;
  reduceMotion: boolean;
  seed: number;
  timeline: string;
}

const profile = (pct: number, rm = false): TimingProfile => ({ speedPct: pct, reduceMotion: rm, flashes: !rm });

const windowVec = (machine: MachineId, stops: number[]): WindowVec => {
  const def = defaultMachine(machine);
  const window = windowFromStops(def, stops).slice();
  const r = evaluateWays(def, window);
  return { machine, stops, window, wins: r.wins.map((w) => [w.symbol, w.k, w.ways, w.payFifths, w.cellMask]), pay: r.payFifths, scatters: r.scatters, bonusCount: r.bonusCount, coins: r.coins };
};

const tumbleVec = (stops: number[]): TumbleVec => {
  const def = defaultMachine('nether');
  const c = runTumbles(def, stops, def.ladder);
  return { stops, windows: c.steps.map((s) => s.window.slice()), pays: c.steps.map((s) => s.payFifths), multipliers: c.steps.map((s) => s.multiplier), tops: c.steps.map((s) => s.tops), total: c.payFifths };
};

const drawVec = (machine: MachineId, bet: number, buy: boolean, owned: boolean, seed: number): DrawVec => {
  const def = defaultMachine(machine);
  const pools = owned ? [] : poolValues(def.jackpot, emptyPools());
  return { machine, bet, buy, owned, seed, pools, tape: encodeTape(drawSpin({ def, bet, buy, owned, pools }, fxSlotRng(seed))) };
};

function generateEngine(): EngineVectors {
  const windows: WindowVec[] = [];
  for (const m of MACHINE_IDS) {
    const def = defaultMachine(m);
    const rng = fxSlotRng(seedMix(1, m.length));
    for (let i = 0; i < 10; i++) windows.push(windowVec(m, def.strips.map((s) => rng.nextInt(s.length))));
  }
  const tumbles = [
    [10, 16, 17, 0, 0], // 8 tumbles
    [11, 18, 15, 0, 0], // 7
    [2, 23, 15, 0, 0], // 6
    [0, 22, 16, 0, 0], // 5
    [0, 0, 0, 0, 0],
    [5, 1, 23, 13, 30],
    [25, 27, 21, 19, 3],
    [13, 12, 7, 1, 17],
    [29, 25, 18, 10, 0],
    [31, 31, 31, 31, 31],
  ].map(tumbleVec);
  const draws: DrawVec[] = [];
  for (const m of MACHINE_IDS) {
    const def = defaultMachine(m);
    const bets = [5, 10, 20, 50, 100, 250, 500, 1000, 2500, 5000].filter((b) => b >= def.jackpot.ref / 100 && b <= def.jackpot.ref);
    let plain = 0;
    let featured = 0;
    for (let seed = 1; plain + featured < 20; seed++) {
      const bet = bets[seed % bets.length]!;
      const v = drawVec(m, bet, false, seed % 5 === 0, seed);
      const f = featureCode(decodeTape(v.tape)) > 0;
      if (f && featured < 12) {
        featured++;
        draws.push(v);
      } else if (!f && plain < 8) {
        plain++;
        draws.push(v);
      }
    }
    if (def.buyPriceFifths > 0) for (let seed = 1; seed <= 3; seed++) draws.push(drawVec(m, bets[0]!, true, false, 10_000 + seed));
  }
  return { v: 1, windows, tumbles, draws };
}

function generateTimelines(engine: EngineVectors): TimelineVec[] {
  const out: TimelineVec[] = [];
  const perMachine: Record<string, number> = {};
  for (const d of engine.draws) {
    if ((perMachine[d.machine] = (perMachine[d.machine] ?? 0) + 1) > 20) continue;
    const i = perMachine[d.machine]!;
    const sharedPct = i % 4 === 0 ? 200 : 100;
    const localPct = i % 3 === 0 ? 150 : i % 5 === 0 ? 50 : 100;
    const reduceMotion = i % 7 === 0;
    const seed = seedMix(d.seed, i);
    out.push({ tape: d.tape, sharedPct, localPct, reduceMotion, seed, timeline: timelineOf(d.tape, sharedPct, localPct, reduceMotion, seed) });
  }
  // + one jackpot tape per machine (the first seeded draw at the reference bet that awards a jackpot): pins the
  // local order ROLLUP → JACKPOT (animation/slots.md §2.5, the jackpot is the climax)
  for (const m of MACHINE_IDS) {
    const def = defaultMachine(m);
    for (let seed = JACKPOT_SEED0; seed < JACKPOT_SEED0 + 2_000_000; seed++) {
      const tape = drawVec(m, def.jackpot.ref, false, false, seed).tape;
      if (!decodeTape(tape).jackpots.length) continue;
      out.push({ tape, sharedPct: 100, localPct: 100, reduceMotion: false, seed, timeline: timelineOf(tape, 100, 100, false, seed) });
      break;
    }
  }
  return out;
}

const JACKPOT_SEED0 = 100_000;

function timelineOf(tape: string, sharedPct: number, localPct: number, rm: boolean, seed: number): string {
  const t = decodeTape(tape);
  return buildSlotTimeline(t, defaultMachine(t.machine), profile(sharedPct), profile(localPct, rm), seed).toCanonicalJson();
}

const json = (v: unknown): string => `${JSON.stringify(v, null, 1)}\n`;

if (DUMP) {
  fs.mkdirSync(DIR, { recursive: true });
  const e = generateEngine();
  fs.writeFileSync(ENGINE, json(e));
  fs.writeFileSync(TIMELINE, json({ v: 1, vectors: generateTimelines(e) }));
}

const engine = JSON.parse(fs.readFileSync(ENGINE, 'utf8')) as EngineVectors;
const timelines = (JSON.parse(fs.readFileSync(TIMELINE, 'utf8')) as { vectors: TimelineVec[] }).vectors;

describe('slots_engine.json', () => {
  it('windows and way wins', () => {
    expect(engine.windows.length).toBeGreaterThanOrEqual(30);
    for (const w of engine.windows) expect(windowVec(w.machine, w.stops)).toEqual(w);
  });
  it('tumble chains (incl. a 7- and an 8-tumble chain)', () => {
    expect(engine.tumbles.map((t) => t.pays.length - 1)).toContain(8);
    expect(engine.tumbles.map((t) => t.pays.length - 1)).toContain(7);
    for (const t of engine.tumbles) expect(tumbleVec(t.stops)).toEqual(t);
  });
  it('seeded draws → tape strings; codec round trip', () => {
    for (const d of engine.draws) {
      expect(drawVec(d.machine, d.bet, d.buy, d.owned, d.seed)).toEqual(d);
      expect(encodeTape(decodeTape(d.tape))).toBe(d.tape);
    }
    expect(engine.draws.filter((d) => featureCode(decodeTape(d.tape)) > 0).length).toBeGreaterThanOrEqual(36);
  });
});

describe('slots_timeline.json', () => {
  it('20 tapes per machine (+ one jackpot tape each) → identical canonical timelines', () => {
    for (const m of MACHINE_IDS) expect(timelines.filter((v) => decodeTape(v.tape).machine === m)).toHaveLength(21);
    for (const v of timelines) expect(timelineOf(v.tape, v.sharedPct, v.localPct, v.reduceMotion, v.seed)).toBe(v.timeline);
  });
  it('the spin roll-up comes before the jackpot beats (the jackpot is the climax)', () => {
    const withJackpot = timelines.filter((v) => decodeTape(v.tape).jackpots.length > 0);
    expect(withJackpot.length).toBeGreaterThanOrEqual(MACHINE_IDS.length);
    for (const v of withJackpot) {
      const t = decodeTape(v.tape);
      const beats = buildSlotTimeline(t, defaultMachine(t.machine), profile(v.sharedPct), profile(v.localPct, v.reduceMotion), v.seed).beats;
      const roll = beats.findIndex((b) => b.kind === 'slots.rollup');
      const jp = beats.findIndex((b) => b.kind === 'slots.jackpot');
      if (t.totalFifths > 0) expect(roll).toBeGreaterThanOrEqual(0);
      if (roll >= 0) expect(roll).toBeLessThan(jp);
      if (roll >= 0) expect(beats[roll]!.at + beats[roll]!.dur).toBeLessThanOrEqual(beats[jp]!.at);
    }
  });
  it('shared profile default', () => {
    expect(SHARED_PROFILE.speedPct).toBe(100);
  });
});
