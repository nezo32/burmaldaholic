import { describe, expect, it } from 'vitest';
import {
  CABINET, type CabinetCue, CabinetPlayer, type CabinetSpin, type CabinetTarget, cellBit, landTimesFromBeats, multPlate, packRow, planCabinet, stateAt, wheelValue,
  writesPerTick,
} from './cabinet-driver';

const P = CABINET.prop;
const LAND = [600, 750, 900, 1050, 1200];

const base = (over: Partial<CabinetSpin> = {}): CabinetSpin => ({
  machine: 'overworld',
  spins: [{ stops: [3, 17, 0, 39, 22], startMs: 0, landMs: LAND, winMask: cellBit(0, 1) | cellBit(1, 1) | cellBit(2, 0) }],
  endMs: 3000,
  ...over,
});

describe('cabinet driver: planCabinet', () => {
  it('writes all five stops, seq and state in the spin-start tick, within the 7-write budget', () => {
    const cues = planCabinet(base(), { seq: 0 });
    const start = cues[0]!;
    expect(start.at).toBe(0);
    expect(start.props).toMatchObject({ [P.state]: 'spin', [P.seq]: 1, [P.reels[0]]: 3, [P.reels[4]]: 22 });
    expect(Object.keys(start.props)).toHaveLength(7);
    for (const [, n] of writesPerTick(cues)) expect(n).toBeLessThanOrEqual(7);
  });

  it('fires each reel land exactly once at its REEL_LAND time (anticipation times are taken as given)', () => {
    const anticipated = [600, 750, 1750, 2750, 3750];
    const cues = planCabinet(base({ spins: [{ stops: [1, 2, 3, 4, 5], startMs: 0, landMs: anticipated, winMask: 0 }], endMs: 5000 }));
    const lands = cues.filter((c) => c.land).flatMap((c) => c.land!.map((r) => [r, c.at]));
    expect(lands.sort((a, b) => a[0]! - b[0]!)).toEqual(anticipated.map((t, r) => [r, t]));
  });

  it('ends on the paid result: last stops, last win mask, idle state (terminal == result)', () => {
    const spin = base();
    const end = stateAt(planCabinet(spin));
    expect(spin.spins[0]!.stops.map((_, r) => end[P.reels[r]!])).toEqual(spin.spins[0]!.stops);
    expect(end[P.win]).toBe(spin.spins[0]!.winMask);
    expect(end[P.state]).toBe('idle');
  });

  it('never shows a later free spin before its start (no early spoilers)', () => {
    const fs: CabinetSpin = {
      machine: 'end',
      spins: [
        { stops: [0, 0, 0, 0, 0], startMs: 0, landMs: LAND, winMask: 0 },
        { stops: [5, 6, 7, 8, 9], startMs: 4000, landMs: LAND.map((t) => 4000 + t * 0.8), winMask: 0, stickyMask: 2 },
        { stops: [9, 8, 7, 6, 5], startMs: 7000, landMs: LAND.map((t) => 7000 + t * 0.8), winMask: cellBit(4, 2), stickyMask: 3 },
      ],
      featureMs: 2000,
      endMs: 10000,
    };
    const cues = planCabinet(fs);
    for (const [i, s] of fs.spins.entries())
      for (const c of cues) if (c.props[P.reels[0]!] === s.stops[0] && c.props[P.reels[1]!] === s.stops[1]) expect(c.at).toBeGreaterThanOrEqual(fs.spins[i]!.startMs);
    const end = stateAt(cues);
    expect(end[P.sticky]).toBe(3);
    expect(end[P.reels[2]!]).toBe(7);
    // seq advances once per spin
    expect(stateAt(cues)[P.seq]).toBe(3);
  });

  it('Nether tumbles: first window kept, each step pulses the win frames + plate, NICE final-window rows packed', () => {
    const finalWindow = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 0, 1, 2, 3];
    const spin: CabinetSpin = {
      machine: 'nether',
      spins: [{ stops: [1, 2, 3, 4, 5], startMs: 0, landMs: LAND, winMask: 0b111, tumbles: [{ atMs: 1500, winMask: 0b111, mult: 2 }, { atMs: 2250, winMask: 0b111000, mult: 3 }], finalWindow }],
      endMs: 6000,
    };
    const cues = planCabinet(spin);
    const steps = cues.filter((c) => c.particle === CABINET.particles.tumble);
    expect(steps.map((c) => c.props[P.mult])).toEqual([2, 3]);
    const end = stateAt(cues);
    expect(end[P.reels[0]!]).toBe(1); // reels never move to another strip window during the chain (D3)
    for (let row = 0; row < 3; row++) {
      const v = end[P.rows[row]!] as number;
      for (let r = 0; r < 5; r++) expect(Math.floor(v / 16 ** r) % 16).toBe(finalWindow[r * 3 + row]! + 1);
    }
    // every Nether ladder value has a plate on the overlay
    for (const m of [1, 2, 3, 5, 2, 4, 6, 10]) expect(CABINET.multPlates).toContain(multPlate(m));
  });

  it('encodes the wheel as ring × 100 + segment + 1 and rejects segments outside the ring', () => {
    expect(wheelValue(0, 0)).toBe(1);
    expect(wheelValue(2, 11)).toBe(212);
    expect(() => wheelValue(1, 16)).toThrow();
    const cues = planCabinet(base({ machine: 'end', wheel: [{ atMs: 2000, ring: 0, segment: 1 }, { atMs: 7000, ring: 1, segment: 7 }], endMs: 12000 }));
    expect(stateAt(cues)[P.wheel]).toBe(108);
  });

  it('packs rows below 2^20 (exact in Molang floats) and wraps seq at 256', () => {
    expect(packRow(Array(15).fill(10), 0)).toBeLessThan(2 ** 20);
    const mem = { seq: 255 };
    planCabinet(base(), mem);
    expect(mem.seq).toBe(0);
  });

  it('never leaves `spin` while a spin is still rolling: feature / celebration states wait for the last land', () => {
    const fsLand = LAND.map((t) => 4000 + t);
    const spin: CabinetSpin = {
      machine: 'end',
      spins: [
        { stops: [0, 1, 2, 3, 4], startMs: 0, landMs: LAND, winMask: 0 },
        { stops: [9, 8, 7, 6, 5], startMs: 4000, landMs: fsLand, winMask: cellBit(0, 0), stickyMask: 1 },
      ],
      featureMs: 4000, // same tick as the first free spin's start
      celebrate: { atMs: 4600, state: 'big' }, // inside the free spin window
      endMs: 8000,
    };
    const cues = planCabinet(spin);
    for (const [i, s] of spin.spins.entries()) {
      const landed = Math.max(...s.landMs);
      for (let t = s.startMs; t < landed; t += 50) expect(stateAt(cues, t)[P.state], `spin ${i} at ${t}`).toBe('spin');
    }
    expect(stateAt(cues, Math.max(...fsLand))[P.state]).toBe('big');
    expect(stateAt(cues)).toMatchObject({ [P.state]: 'idle', [P.reels[0]!]: 9, [P.win]: cellBit(0, 0), [P.sticky]: 1 });
  });

  it('keeps the per-tick write budget over a whole Nether free-spin run with tumbles and a final window', () => {
    const spins = [0, 1, 2, 3].map((i) => {
      const t0 = i * 5000;
      return {
        stops: [i, i + 1, i + 2, i + 3, 31],
        startMs: t0,
        landMs: LAND.map((t) => t0 + t),
        winMask: 0b111,
        baseMult: i ? 2 : 0,
        tumbles: [{ atMs: t0 + 1600, winMask: 0b111, mult: i ? 4 : 2 }, { atMs: t0 + 2400, winMask: 0b111000, mult: i ? 6 : 3 }],
        finalWindow: Array.from({ length: 15 }, (_, k) => k % 11),
      };
    });
    const cues = planCabinet({ machine: 'nether', spins, featureMs: 3500, celebrate: { atMs: 19_000, state: 'win' }, endMs: 20_000 });
    for (const [, n] of writesPerTick(cues)) expect(n).toBeLessThanOrEqual(7);
    const end = stateAt(cues);
    expect(end[P.mult]).toBe(6);
    expect(end[P.reels[4]!]).toBe(31);
    expect(end[P.rows[0]!]).toBe(packRow(spins[3]!.finalWindow, 0));
  });

  it('reads land times from REEL_LAND beats (lane = reel)', () => {
    const beats = [0, 1, 2, 3, 4].map((r) => ({ at: LAND[r]!, kind: 'slots.reel_land', lane: r })).concat([{ at: 10, kind: 'slots.spin_up', lane: -1 }]);
    expect(landTimesFromBeats(beats, 0)).toEqual(LAND);
  });
});

describe('cabinet driver: CabinetPlayer', () => {
  function fake(startAt = 0) {
    let now = startAt;
    const timers: Array<{ at: number; fn: () => void; dead: boolean }> = [];
    const log: string[] = [];
    const props: Record<string, number | string> = {};
    const target: CabinetTarget = {
      write: (p) => {
        Object.assign(props, p);
        log.push(`w${Object.keys(p).length}@${now}`);
        return Object.keys(p).length;
      },
      playLand: (r) => log.push(`land${r}@${now}`),
      particle: (id) => log.push(`${id}@${now}`),
      isValid: () => true,
    };
    const clock = {
      nowMs: () => now,
      after: (ms: number, fn: () => void) => {
        const t = { at: now + ms, fn, dead: false };
        timers.push(t);
        return () => (t.dead = true);
      },
    };
    const run = (until: number) => {
      for (;;) {
        const next = timers.filter((t) => !t.dead && t.at <= until).sort((a, b) => a.at - b.at)[0];
        if (!next) break;
        next.dead = true;
        now = next.at;
        next.fn();
      }
      now = until;
    };
    return { target, clock, run, log, props };
  }

  it('plays the cues in order at their times and ends on the terminal state', () => {
    const cues = planCabinet(base());
    const f = fake();
    const p = new CabinetPlayer(cues, f.target, f.clock).play();
    f.run(10_000);
    expect(p.isDone()).toBe(true);
    expect(f.log.filter((l) => l.startsWith('land'))).toEqual(LAND.map((t, r) => `land${r}@${t}`));
    expect(f.props).toEqual(stateAt(cues));
  });

  it('finish() (skip / close) jumps to the terminal state without replaying land animations', () => {
    const cues: CabinetCue[] = planCabinet(base());
    const f = fake();
    const p = new CabinetPlayer(cues, f.target, f.clock).play();
    f.run(700);
    p.finish();
    expect(f.props).toEqual(stateAt(cues));
    expect(f.log.filter((l) => l.startsWith('land'))).toEqual(['land0@600']);
  });

  it('skip during a free-spin run reveals the paid terminal state at once (F8), and a second finish is a no-op', () => {
    const cues = planCabinet({
      machine: 'end',
      spins: [
        { stops: [1, 1, 1, 1, 1], startMs: 0, landMs: LAND, winMask: 0 },
        { stops: [7, 7, 7, 7, 7], startMs: 3000, landMs: LAND.map((t) => 3000 + t), winMask: 0, stickyMask: 4 },
      ],
      featureMs: 2000,
      endMs: 6000,
    });
    const f = fake();
    const p = new CabinetPlayer(cues, f.target, f.clock).play();
    f.run(700);
    expect(f.props[P.reels[0]!]).toBe(1);
    p.finish();
    expect(f.props).toEqual(stateAt(cues));
    const writes = f.log.length;
    p.finish();
    f.run(10_000);
    expect(f.log).toHaveLength(writes);
  });

  it('a late start folds past cues into one write (no bursts); cues ≤ 100 ms late still fire, then on time', () => {
    const cues = planCabinet(base());
    const f = fake(1000);
    new CabinetPlayer(cues, f.target, f.clock).play();
    expect(f.log[0]).toMatch(/^w\d+@1000$/);
    f.run(10_000);
    expect(f.log.filter((l) => l.startsWith('land'))).toEqual(['land2@1000', 'land3@1050', 'land4@1200']);
    expect(f.log.some((l) => l.startsWith('land0') || l.startsWith('land1'))).toBe(false);
    expect(f.props).toEqual(stateAt(cues));
  });
});
