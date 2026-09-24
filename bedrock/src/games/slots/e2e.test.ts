/**
 * Slots v2 end to end (cut-over S-B5): the REAL service (lane B-L8) + the machine-form host (`form.ts`) + the REAL
 * B-L9 `SlotPresenter` + the B-L10 cabinet planner, on a fake @minecraft/server clock and a fake module context.
 * Every machine spins through a plain base game, free spins, its bonus game (Treasure Hunt / Piglin's Hoard /
 * Dragon Wheel) and a jackpot. For every round:
 *  - the presenter settles once and its last frame is the terminal screen, whose amount is the settled amount;
 *  - the cabinet ends on the same stops as the last reel spin of the tape (and lands at the REEL_LAND ends);
 *  - money is conserved: balance = start − stake + settled wager return + pool money.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';

const clock = vi.hoisted(() => {
  const c = {
    tick: 1000,
    nextId: 1,
    runs: new Map<number, { fn: () => void; period: number; next: number; once: boolean }>(),
    advance(n: number): void {
      for (let i = 0; i < n; i++) {
        c.tick++;
        for (const [id, r] of [...c.runs]) {
          if (!c.runs.has(id) || r.next > c.tick) continue;
          if (r.once) c.runs.delete(id);
          else r.next += r.period;
          r.fn();
        }
      }
    },
  };
  return c;
});
const worldProps = vi.hoisted(() => new Map<string, unknown>());

vi.mock('@minecraft/server', () => ({
  world: {
    getDynamicProperty: (k: string) => worldProps.get(k),
    setDynamicProperty: (k: string, v: unknown) => (v === undefined ? worldProps.delete(k) : worldProps.set(k, v)),
    getDynamicPropertyIds: () => [...worldProps.keys()],
    getAllPlayers: () => [],
    getAbsoluteTime: () => clock.tick,
    sendMessage: () => {},
    afterEvents: new Proxy({}, { get: () => ({ subscribe: () => {} }) }),
    beforeEvents: new Proxy({}, { get: () => ({ subscribe: () => {} }) }),
    scoreboard: { getObjective: () => undefined, addObjective: () => undefined },
  },
  system: {
    get currentTick() {
      return clock.tick;
    },
    runInterval: (fn: () => void, period = 1) => {
      const id = clock.nextId++;
      clock.runs.set(id, { fn, period, next: clock.tick + period, once: false });
      return id;
    },
    runTimeout: (fn: () => void, d = 1) => {
      const id = clock.nextId++;
      clock.runs.set(id, { fn, period: d, next: clock.tick + Math.max(1, d), once: true });
      return id;
    },
    run: (fn: () => void) => fn(),
    runJob: () => 1,
    clearRun: (id: number) => clock.runs.delete(id),
    afterEvents: new Proxy({}, { get: () => ({ subscribe: () => {} }) }),
    beforeEvents: new Proxy({}, { get: () => ({ subscribe: () => {} }) }),
  },
  EasingType: { InOutSine: 'InOutSine' },
  MolangVariableMap: class {
    setFloat(): void {}
  },
  EntityComponentTypes: {},
  ItemComponentTypes: {},
  ItemStack: class {},
}));
vi.mock('@minecraft/server-ui', () => ({ ActionFormData: class {}, ModalFormData: class {}, MessageFormData: class {}, FormCancelationReason: {}, uiManager: { closeAllForms: () => {} } }));

const { SlotsV2Service, ROUNDS_PROP } = await import('./service');
const { SlotsFormHost } = await import('./form');
const { SlotPresenter } = await import('./v2/present/ddui-form');
const { terminalScreen } = await import('./v2/present/features');
const { CABINET, landTimesFromBeats, planCabinet, stateAt } = await import('./v2/present/cabinet-driver');
const { defaultMachine } = await import('./v2/logic/config');
const { drawSpin, evaluateSpin } = await import('./v2/logic/engine');
const { emptyPools, poolValues } = await import('./v2/logic/jackpots');
const { fxSlotRng } = await import('./v2/logic/test-rng');
const { SLOT_BEAT } = await import('./v2/logic/timeline');
type SpinTape = import('./v2/logic/types').SpinTape;
type MachineId = import('./v2/logic/types').MachineId;
type CabinetSpinData = import('./v2/logic/cabinet-spin').CabinetSpinData;

const START = 1_000_000;
const VARIANT: Record<MachineId, string> = { overworld: 'copper', nether: 'gold', end: 'netherite' };
const BET: Record<MachineId, number> = { overworld: 10, nether: 20, end: 100 };

class FakePlayer {
  isValid = true;
  isSneaking = false;
  props = new Map<string, unknown>();
  messages: unknown[] = [];
  dimension = { id: 'minecraft:overworld', spawnParticle: () => {}, playSound: () => {} };
  location = { x: 0, y: 64, z: 0 };
  onScreenDisplay = { setTitle: () => {}, updateSubtitle: () => {}, setActionBar: () => {} };
  camera = { fade: () => {}, setCamera: () => {}, clear: () => {} };
  constructor(
    readonly id: string,
    readonly name: string,
  ) {}
  getDynamicProperty(k: string): unknown {
    return this.props.get(k);
  }
  setDynamicProperty(k: string, v: unknown): void {
    if (v === undefined) this.props.delete(k);
    else this.props.set(k, v);
  }
  sendMessage(m: unknown): void {
    this.messages.push(m);
  }
  playSound(): void {}
  playMusic(): void {}
  stopMusic(): void {}
  runCommand(): void {}
}

function fakeCtx() {
  const bal = new Map<string, number>();
  const settled: number[] = [];
  const pool: number[] = [];
  const stakes: number[] = [];
  let seq = 0;
  const balance = (id: string): number => bal.get(id) ?? START;
  const ctx = {
    config: {
      get: (k: string) => {
        if (k === 'slots.enabled' || k === 'slots.validateRtp') return true;
        throw new Error(`unknown ${k}`);
      },
      onChange: () => {},
    },
    economy: {
      balance: (p: FakePlayer) => balance(p.id),
      credit: (p: FakePlayer, n: number) => {
        pool.push(n);
        bal.set(p.id, balance(p.id) + n);
        return n;
      },
    },
    wagers: {
      place: (p: FakePlayer, o: { stake: { amount: number } }) => {
        stakes.push(o.stake.amount);
        bal.set(p.id, balance(p.id) - o.stake.amount);
        return { ok: true, ticket: { id: `t${++seq}`, playerId: p.id, value: o.stake.amount } };
      },
      draw: () => {},
      settle: (_tk: unknown, p: FakePlayer | undefined, n: number) => {
        settled.push(n);
        if (p) bal.set(p.id, balance(p.id) + n);
      },
      resolveHouse: () => ({ kind: 'bank' }),
      limitsFor: (_p: unknown, _g: unknown, base: unknown) => base,
      check: () => undefined,
    },
    limits: { tier: () => 5, tierName: () => 'x', tierMax: () => 5000 },
    odds: { draw: <T,>(_id: string, _rtp: number, _rng: unknown, drawFn: () => T) => ({ result: drawFn(), rerolled: false }) },
    hud: { actionbar: () => {}, clear: () => {}, title: () => {} },
    achievements: { unlock: () => true, has: () => false },
    services: { get: () => undefined, provide: () => {} },
    tables: { register: () => {} },
    admin: { addAction: () => {} },
    log: { info: () => {}, warn: () => {}, error: (m: string, e?: unknown) => console.error(m, e) },
    isCasinoEnabled: () => true,
    guard: (fn: unknown) => fn,
  };
  return { ctx, balance, settled, pool, stakes };
}

function fakeSession(p: FakePlayer, machine: MachineId) {
  const timers = new Map<string, number>();
  let active = true;
  return {
    player: p,
    playerId: p.id,
    table: { key: `overworld|1,64,${machine.length}`, game: 'slots', variant: VARIANT[machine], dimension: { id: 'minecraft:overworld', getBlock: () => undefined }, location: { x: 1, y: 64, z: 1 } },
    seat: 1,
    data: {} as Record<string, unknown>,
    setTimer: (id: string, ticks: number, fn: () => void) => {
      const old = timers.get(id);
      if (old !== undefined) clock.runs.delete(old);
      const rid = clock.nextId++;
      clock.runs.set(rid, { fn, period: ticks, next: clock.tick + Math.max(1, ticks), once: true });
      timers.set(id, rid);
    },
    clearTimer: (id: string) => {
      const rid = timers.get(id);
      if (rid !== undefined) clock.runs.delete(rid);
      timers.delete(id);
    },
    leave: () => (active = false),
    isActive: () => active,
    closeForms: () => {},
  };
}

/** The first seed whose draw at this machine's default bet satisfies `want` (the service draws with the same rng). */
function seedFor(m: MachineId, want: (t: SpinTape) => boolean, from = 1): number {
  const def = defaultMachine(m);
  const pools = poolValues(def.jackpot, emptyPools());
  for (let s = from; s < from + 3_000_000; s++) if (want(drawSpin({ def, bet: BET[m], buy: false, owned: false, pools }, fxSlotRng(s)))) return s;
  throw new Error(`no seed for ${m}`);
}

const noFeature = (t: SpinTape): boolean => !t.freeSpins && !t.hunt && !t.hoard && !t.wheel && !t.jackpots.length;
const SCENARIOS: Record<MachineId, Array<[string, (t: SpinTape) => boolean]>> = {
  overworld: [
    ['base win', (t) => noFeature(t) && t.totalFifths >= 5],
    ['free spins', (t) => !!t.freeSpins && !t.hunt && !t.jackpots.length],
    ['Treasure Hunt', (t) => !!t.hunt && !t.jackpots.length && !t.freeSpins],
    ['jackpot', (t) => t.jackpots.length > 0],
  ],
  nether: [
    ['base win with tumbles', (t) => noFeature(t) && (evaluateSpin(defaultMachine('nether'), t.stops, false).chain?.steps.length ?? 0) >= 3],
    ['free spins', (t) => !!t.freeSpins && !t.hoard && !t.jackpots.length],
    ["Piglin's Hoard (hold & spin)", (t) => !!t.hoard && !t.jackpots.length && !t.freeSpins],
    ['jackpot', (t) => t.jackpots.length > 0],
  ],
  end: [
    ['base win', (t) => noFeature(t) && t.totalFifths >= 5],
    ['Void Walker free spins (sticky wilds)', (t) => !!t.freeSpins && t.freeSpins.spins.some((s) => s.stickyMaskAfter > 0) && !t.wheel && !t.jackpots.length],
    ['Dragon Wheel', (t) => !!t.wheel && !t.jackpots.length && !t.freeSpins],
    ['jackpot', (t) => t.jackpots.length > 0],
  ],
};

/** Digits of every `text` in a rawtext (the amounts of a status line). */
function amountOf(raw: unknown): number {
  const texts: string[] = [];
  const walk = (r: unknown): void => {
    if (!r || typeof r !== 'object') return;
    const o = r as { text?: string; rawtext?: unknown[]; with?: { rawtext?: unknown[] } | unknown[] };
    if (typeof o.text === 'string') texts.push(o.text.replace(/§./g, ''));
    for (const c of o.rawtext ?? []) walk(c);
    const w = o.with;
    for (const c of (Array.isArray(w) ? w : (w?.rawtext ?? [])) as unknown[]) walk(c);
  };
  walk(raw);
  return Number(texts.join('').replace(/\D/g, '') || '0');
}

beforeEach(() => {
  worldProps.clear();
  clock.runs.clear();
  clock.tick = 1000;
});

describe('slots v2 end to end: service → form presenter → cabinet', () => {
  for (const m of ['overworld', 'nether', 'end'] as MachineId[]) {
    for (const [name, want] of SCENARIOS[m]) {
      it(`${m}: ${name}`, () => {
        const seed = seedFor(m, want);
        const f = fakeCtx();
        const cabinet: Array<{ spin: CabinetSpinData; startTick: number }> = [];
        const finished: number[] = [];
        const svc = new SlotsV2Service(f.ctx as never, undefined, fxSlotRng(seed), {
          playCabinet: (_d: unknown, _p: unknown, spin: CabinetSpinData, startTick: number) => cabinet.push({ spin, startTick }),
          finishCabinet: () => finished.push(clock.tick),
        });
        svc.loadConfig();
        svc.loadPools();
        const p = new FakePlayer('-7', 'Alex');
        const s = fakeSession(p, m);
        const host = new SlotsFormHost(svc, s as never);
        const shown: unknown[] = [];
        const done: boolean[] = [];
        let alive = true;
        const view = {
          show: (x: unknown) => void shown.push(x),
          jackpots: () => {},
          alive: () => alive,
          huntMode: () => {},
          suspended: () => {},
          finished: (_x: unknown, i: boolean) => void done.push(i),
        };
        const pr = new SlotPresenter(p as never, host, view);
        pr.onPickReady = () => pr.pick(true); // "Open all" (reveal order = draw order, D6)

        const start = host.spin(false);
        expect('round' in start).toBe(true);
        if (!('round' in start)) return;
        const tape = start.round.tape;
        expect(want(tape)).toBe(true); // the service drew the scenario's tape
        const rec = Object.values(JSON.parse(String(worldProps.get(`${ROUNDS_PROP}:0`) ?? '{}')) as Record<string, { total: number; jackpotAwards: Array<{ chips: number }> }>)[0]!;
        expect(rec.total).toBe((tape.totalFifths * tape.bet) / 5);
        pr.play(start);
        clock.advance(Math.ceil(start.timeline.endMs() / 50) + 3000);
        alive = false;

        // presenter: settled once, final frame = terminal screen = the settled amount
        expect(done).toEqual([false]);
        expect(svc.liveCount()).toBe(0);
        expect(f.settled).toEqual([rec.total]);
        const last = shown[shown.length - 1] as ReturnType<typeof terminalScreen>;
        expect(JSON.stringify(last)).toBe(JSON.stringify(terminalScreen(start.round)));
        if (rec.total > 0) expect(amountOf(last.status)).toBe(rec.total);
        if (tape.hunt) expect(tape.hunt.opened).toBeGreaterThan(0);

        // cabinet: one planned round, it rests on the stops of the tape's last reel spin, lands at the beat ends
        expect(cabinet).toHaveLength(1);
        expect(finished).toEqual([]); // settled at the gate: the cabinet plays on to the timeline end
        const end = stateAt(planCabinet(cabinet[0]!.spin as never));
        const lastStops = tape.freeSpins?.spins.at(-1)?.stops ?? tape.stops;
        expect(CABINET.prop.reels.map((k) => end[k])).toEqual(lastStops);
        expect(end[CABINET.prop.state]).toBe('idle');
        if (!tape.bought) {
          const next = start.timeline.beats.find((b) => b.kind === SLOT_BEAT.FS_SPIN)?.at;
          expect(landTimesFromBeats(start.timeline.beats, 0, next)).toEqual([...cabinet[0]!.spin.spins[0]!.landMs]);
        }

        // money: start − stake + settled wager return + pool money (paid outside the wager)
        const poolChips = rec.jackpotAwards.reduce((a, j) => a + j.chips, 0);
        expect(f.pool.reduce((a, b) => a + b, 0)).toBe(poolChips);
        if (tape.jackpots.length) expect(poolChips).toBeGreaterThan(0);
        expect(f.stakes).toEqual([BET[m]]);
        expect(f.balance(p.id)).toBe(START - BET[m] + rec.total + poolChips);
      });
    }
  }

  it('a jackpot round: the roll-up beat ends before the first jackpot beat (the jackpot is the climax)', () => {
    const seed = seedFor('overworld', (t) => t.jackpots.length > 0 && t.totalFifths > 0);
    const f = fakeCtx();
    const svc = new SlotsV2Service(f.ctx as never, undefined, fxSlotRng(seed), {});
    svc.loadConfig();
    svc.loadPools();
    const p = new FakePlayer('-8', 'Steve');
    const host = new SlotsFormHost(svc, fakeSession(p, 'overworld') as never);
    const start = host.spin(false);
    if (!('round' in start)) throw new Error('refused');
    const beats = start.timeline.beats;
    const roll = beats.find((b) => b.kind === SLOT_BEAT.ROLLUP)!;
    const jp = beats.find((b) => b.kind === SLOT_BEAT.JACKPOT)!;
    expect(roll.at + roll.dur).toBeLessThanOrEqual(jp.at);
  });
});
