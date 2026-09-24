/**
 * Slots v2 service with a fake @minecraft/server and a fake module context (SLOTS.md §1.2, §5, §8; §15 items
 * 11, 13, 14): stake → draw → persist → gate → settle, skip / leave, restart recovery, pool money outside the
 * wager, owned reservation, no streak re-draw on buys, v1 pool migration.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';

const worldProps = new Map<string, unknown>();
let tick = 100;
vi.mock('@minecraft/server', () => ({
  world: {
    getDynamicProperty: (k: string) => worldProps.get(k),
    setDynamicProperty: (k: string, v: unknown) => (v === undefined ? worldProps.delete(k) : worldProps.set(k, v)),
    getDynamicPropertyIds: () => [...worldProps.keys()],
    getAllPlayers: () => [],
    getAbsoluteTime: () => tick,
    sendMessage: () => {},
    afterEvents: { playerSpawn: { subscribe: () => {} } },
  },
  system: {
    runInterval: () => 1,
    runTimeout: () => 1,
    run: () => 1,
    runJob: () => 1,
    clearRun: () => {},
    get currentTick() {
      return tick;
    },
  },
  EntityComponentTypes: {},
  ItemComponentTypes: {},
  ItemStack: class {},
}));
vi.mock('@minecraft/server-ui', () => ({ ActionFormData: class {}, ModalFormData: class {}, MessageFormData: class {}, FormCancelationReason: {}, uiManager: {} }));

const { SlotsV2Service, SlotsV2Host, ROUNDS_PROP, POOLS_PROP, OWED_PROP, V1_POOLS_PROP, machineOfTable } = await import('./service');
const { decodeTape } = await import('./v2/logic');
const { scriptedRng, fxSlotRng } = await import('./v2/logic/test-rng');

class FakePlayer {
  isValid = true;
  props = new Map<string, unknown>();
  messages: unknown[] = [];
  dimension = { id: 'minecraft:overworld' };
  location = { x: 0, y: 64, z: 0 };
  constructor(
    readonly id: string,
    readonly name: string,
  ) {}
  getDynamicProperty(k: string) {
    return this.props.get(k);
  }
  setDynamicProperty(k: string, v: unknown) {
    if (v === undefined) this.props.delete(k);
    else this.props.set(k, v);
  }
  sendMessage(m: unknown) {
    this.messages.push(m);
  }
  playSound() {}
}

interface Ticket {
  id: string;
  playerId: string;
  value: number;
  drawn?: number;
}

function fakeCtx(balance = 100_000) {
  const bal = new Map<string, number>();
  const log: string[] = [];
  const placed: Array<Record<string, unknown>> = [];
  const settled: Array<[string, number]> = [];
  const credits: Array<[string, number, string]> = [];
  let oddsCalls = 0;
  let seq = 0;
  const ctx = {
    config: {
      get: (k: string) => {
        if (k === 'slots.enabled' || k === 'slots.validateRtp') return true;
        throw new Error('unknown');
      },
      onChange: () => {},
    },
    economy: {
      balance: (p: FakePlayer) => bal.get(p.id) ?? balance,
      credit: (p: FakePlayer, n: number, why: string) => {
        credits.push([p.id, n, why]);
        bal.set(p.id, (bal.get(p.id) ?? balance) + n);
        return n;
      },
    },
    wagers: {
      place: (p: FakePlayer, o: Record<string, unknown>) => {
        placed.push(o);
        const amount = (o.stake as { amount: number }).amount;
        bal.set(p.id, (bal.get(p.id) ?? balance) - amount);
        return { ok: true, ticket: { id: `t${++seq}`, playerId: p.id, value: amount } as Ticket };
      },
      draw: (tk: Ticket, n: number) => (tk.drawn = n),
      settle: (tk: Ticket, p: FakePlayer | undefined, n: number) => {
        settled.push([tk.id, n]);
        if (p) bal.set(p.id, (bal.get(p.id) ?? balance) + n);
      },
      resolveHouse: () => ({ kind: 'bank' }),
      limitsFor: (_p: unknown, _g: unknown, base: unknown) => base,
      check: () => undefined,
    },
    limits: { tier: () => 5, tierName: () => 'x', tierMax: () => 5000 },
    odds: {
      draw: <T,>(_id: string, _rtp: number, _rng: unknown, drawFn: () => T) => {
        oddsCalls++;
        return { result: drawFn(), rerolled: false };
      },
    },
    hud: { actionbar: () => {}, clear: () => {} },
    achievements: { unlock: () => true, has: () => false },
    services: { get: () => undefined, provide: () => {} },
    tables: { register: () => {} },
    admin: { addAction: () => {} },
    log: { info: (m: string) => log.push(m), warn: (m: string) => log.push(m), error: (m: string) => log.push(m) },
    isCasinoEnabled: () => true,
    guard: (fn: unknown) => fn,
  };
  return { ctx, bal, log, placed, settled, credits, odds: () => oddsCalls };
}

function fakeSession(p: FakePlayer, variant: string) {
  const timers = new Map<string, { ticks: number; fn: () => void }>();
  let active = true;
  return {
    player: p,
    playerId: p.id,
    table: { key: `overworld|${variant}`, game: 'slots', variant, dimension: { id: 'minecraft:overworld' }, location: { x: 1, y: 64, z: 1 } },
    seat: 1,
    data: {} as Record<string, unknown>,
    timers,
    setTimer: (id: string, ticks: number, fn: () => void) => void timers.set(id, { ticks, fn }),
    clearTimer: (id: string) => void timers.delete(id),
    leave: () => (active = false),
    isActive: () => active,
    closeForms: () => {},
  };
}

function presenter() {
  const calls: string[] = [];
  return {
    calls,
    machine: async () => undefined,
    play: () => void calls.push('play'),
    picked: (_r: unknown, i: number) => void calls.push(`pick${i}`),
    settled: () => void calls.push('settled'),
    error: () => void calls.push('error'),
  };
}

function service(rngValues?: number[], seed = 7, deps: Record<string, unknown> = {}) {
  const f = fakeCtx();
  const pr = presenter();
  const svc = new SlotsV2Service(f.ctx as never, pr as never, rngValues ? scriptedRng(rngValues, seed) : fxSlotRng(seed), deps as never);
  svc.loadConfig();
  svc.loadPools();
  return { svc, pr, ...f };
}

const rounds = (): Record<string, { tape: string; total: number; jackpotAwards: unknown[] }> => JSON.parse(String(worldProps.get(`${ROUNDS_PROP}:0`) ?? '{}'));

beforeEach(() => {
  worldProps.clear();
  tick = 100;
});

describe('slots v2 service: round lifecycle', () => {
  it('block variants map to the machines (block ids unchanged)', () => {
    expect(machineOfTable({ variant: 'copper' })).toBe('overworld');
    expect(machineOfTable({ blockTypeId: 'burmaldaholic:slot_machine_gold' })).toBe('nether');
    expect(machineOfTable({ variant: 'netherite' })).toBe('end');
  });

  it('CONFIRM → DRAW → PERSIST → gate → SETTLE', () => {
    const { svc, pr, placed, settled } = service();
    const p = new FakePlayer('-1', 'Alex');
    const s = fakeSession(p, 'gold');
    svc.spin(s as never, false);
    expect(placed[0]).toMatchObject({ game: 'slots', stake: { kind: 'chips', amount: 20 }, worstCase: 2000 * 20 });
    const rec = Object.values(rounds())[0]!;
    const tape = decodeTape(rec.tape);
    expect(rec.total).toBe((tape.totalFifths * 20) / 5);
    expect(pr.calls).toEqual(['play']);
    const gate = s.timers.get('slots.v2.gate')!;
    expect(gate.ticks).toBeGreaterThan(20);
    expect(settled).toEqual([]);
    gate.fn();
    expect(settled).toEqual([['t1', rec.total]]);
    expect(rounds()).toEqual({});
    expect(pr.calls).toEqual(['play', 'settled']);
    expect(p.props.has('burmaldaholic:slots.stats')).toBe(true);
    expect(svc.liveCount()).toBe(0);
  });

  it('skip and leave settle at once from the persisted tape', () => {
    const { svc, settled } = service();
    const p = new FakePlayer('-1', 'Alex');
    const s = fakeSession(p, 'copper');
    svc.spin(s as never, false);
    svc.skip(s as never);
    expect(settled).toHaveLength(1);
    svc.spin(s as never, false);
    p.isValid = false;
    svc.onLeave(s as never);
    expect(settled).toHaveLength(2);
    expect(rounds()).toEqual({});
  });

  it('contributions go to the pools; a jackpot is debited at draw time and shown as pending until the reveal', () => {
    // End: crystals on reels 2–4 (R2 21, R3 5, R4 24), wheel outer UP (1), middle UP (7), core GRAND (4)
    const { svc, settled, credits } = service([0, 21, 5, 24, 0, 1, 7, 4]);
    const p = new FakePlayer('-1', 'Alex');
    const s = fakeSession(p, 'netherite');
    (s.data as Record<string, unknown>).slotsV2 = { machine: 'end', bet: 5000, busy: false, notice: [] };
    const before = svc.meters('end')[4]!;
    svc.spin(s as never, false);
    const rec = Object.values(rounds())[0]!;
    expect(rec.jackpotAwards).toEqual([{ tier: 4, chips: 12_500_000 + Math.floor(5000 * 0.009) }]);
    // other players still see the pool from before the award until the reveal
    expect(svc.meters('end')[4]).toBe(before + Math.floor(5000 * 0.009));
    expect(before).toBe(12_500_000);
    const stored = JSON.parse(String(worldProps.get(POOLS_PROP)));
    expect(stored.end[4].inc).toBe(0);
    svc.skip(s as never);
    // pool money is paid next to the wager, not through it (Golden Hour excludes it)
    expect(settled[0]![1]).toBe(rec.total);
    expect(credits).toEqual([['-1', 12_500_045, 'slots.jackpot']]);
    expect(svc.meters('end')[4]).toBe(12_500_000);
  });

  it('bought features skip the streak re-draw and the table limits; base spins use it', () => {
    const { svc, placed, odds } = service();
    const p = new FakePlayer('-1', 'Alex');
    const s = fakeSession(p, 'gold');
    svc.spin(s as never, true);
    expect(odds()).toBe(0);
    expect(placed[0]).toMatchObject({ skipLimits: true, stake: { amount: 368 }, worstCase: 40_000 });
    svc.skip(s as never);
    svc.spin(s as never, false);
    expect(odds()).toBe(1);
  });

  it('refuses bets off the ladder and buys above tier max × 25', () => {
    const { svc, pr, placed } = service();
    const p = new FakePlayer('-1', 'Alex');
    const s = fakeSession(p, 'gold');
    (s.data as Record<string, unknown>).slotsV2 = { machine: 'nether', bet: 15, busy: false, notice: [] };
    expect('ticket' in (svc.start(s as never, false) as object)).toBe(false);
    expect(placed).toHaveLength(0);
    expect(pr.calls).toEqual([]);
  });

  it('manual Treasure Hunt: the gate waits for the picks; the i-th pick reveals entry i', () => {
    // chests on reels 1, 3, 5: stops 13 / 6 / 9; entries x1 x1 creeper …
    const { svc, pr, settled } = service([13, 0, 6, 0, 9, 0, 0, 102_000]);
    const p = new FakePlayer('-1', 'Alex');
    const s = fakeSession(p, 'copper');
    svc.spin(s as never, false);
    const rec = Object.values(rounds())[0]!;
    const tape = decodeTape(rec.tape);
    expect(tape.hunt!.entries.slice(0, 3)).toEqual([1, 1, 0]);
    s.timers.get('slots.v2.gate')!.fn(); // reaching the hunt: no settlement yet
    expect(settled).toHaveLength(0);
    svc.pick(s as never);
    expect(pr.calls).toContain('pick0');
    svc.pick(s as never, true);
    expect(pr.calls.filter((c) => c.startsWith('pick'))).toEqual(['pick0', 'pick1', 'pick2']);
    s.timers.get('slots.v2.gate')!.fn();
    expect(settled).toHaveLength(1);
  });
});

describe('B-L9 SlotHost adapter and B-L10 cabinet wiring', () => {
  it('spin → round + timeline; presented settles once; cabinet played and finished', () => {
    const cab: string[] = [];
    const deps = {
      roundFromTape: (_d: unknown, tape: { bet: number }, o: { tier: string }) => ({ bet: tape.bet, tier: o.tier }),
      playCabinet: (_dim: unknown, _pos: unknown, spin: { spins: Array<{ landMs: number[] }> }) => cab.push(`play:${spin.spins[0]!.landMs.join(',')}`),
      finishCabinet: () => cab.push('finish'),
    };
    const { svc, settled, pr } = service(undefined, 3, deps);
    const p = new FakePlayer('-1', 'Alex');
    const s = fakeSession(p, 'copper');
    const host = new SlotsV2Host(svc, s as never, deps.roundFromTape as never);
    expect(host.autoplaying()).toBe(false);
    host.betUp();
    const start = host.spin(false) as { round: { bet: number; tier: string }; timeline: { beats: Array<{ kind: string; at: number; dur: number; lane: number }> } };
    expect(start.round.bet).toBe(20);
    const lands = start.timeline.beats.filter((b) => b.kind === 'slots.reel_land' && b.at < 2000).map((b) => b.at + b.dur);
    expect(cab[0]).toBe(`play:${lands.slice(0, 5).join(',')}`);
    host.presented(start as never, false);
    host.presented(start as never, true);
    expect(settled).toHaveLength(1);
    expect(cab).toContain('finish');
    expect(pr.calls).toEqual([]); // the host form presents, not the fallback presenter
    expect(host.buyLabel()).toBeUndefined(); // Overworld has no buy
  });
  it('hunt picks through the host: in order, idempotent, no skipping ahead', () => {
    const deps = { roundFromTape: () => ({}) };
    const { svc, settled } = service([13, 0, 6, 0, 9, 0, 0, 102_000], 7, deps);
    const p = new FakePlayer('-1', 'Alex');
    const s = fakeSession(p, 'copper');
    const host = new SlotsV2Host(svc, s as never, deps.roundFromTape as never);
    const start = host.spin(false);
    expect(host.huntPick(start as never, 1)).toBeUndefined();
    expect(host.huntPick(start as never, 0)).toBe(1);
    expect(host.huntPick(start as never, 0)).toBe(1);
    expect(host.huntPick(start as never, 1)).toBe(1);
    expect(host.huntPick(start as never, 2)).toBe(0);
    expect(host.huntPick(start as never, 3)).toBeUndefined(); // the Creeper ended the hunt
    expect(JSON.parse(String(worldProps.get(`${ROUNDS_PROP}:0`)))['overworld|copper'].opened).toBe(3);
    s.timers.get('slots.v2.gate')!.fn();
    expect(settled).toHaveLength(1);
  });
});

describe('restart and migration', () => {
  it('a persisted round with pool money becomes owed and is paid on the next spawn', () => {
    const a = service([0, 21, 5, 24, 0, 1, 7, 4]);
    const p = new FakePlayer('-9', 'Bo');
    const s = fakeSession(p, 'netherite');
    (s.data as Record<string, unknown>).slotsV2 = { machine: 'end', bet: 100, busy: false, notice: [] };
    a.svc.spin(s as never, false);
    expect(Object.keys(rounds())).toHaveLength(1);
    // server stops here; a new service recovers
    const b = service();
    b.svc.recoverRounds();
    expect(rounds()).toEqual({});
    const owed = JSON.parse(String(worldProps.get(OWED_PROP)));
    expect(owed['-9']).toBeGreaterThan(0);
    b.svc.payOwed(p as never);
    expect(b.credits[0]![0]).toBe('-9');
    expect(worldProps.get(OWED_PROP)).toBe('{}');
  });

  it('v1 pools migrate once into the Nether / End Grand increments (SLOTS.md §5.3)', () => {
    worldProps.set(V1_POOLS_PROP, JSON.stringify({ gold: { pool: 61_240, rem: 0 }, netherite: { pool: 52_000, rem: 0 } }));
    const { svc } = service();
    expect(svc.meters('nether')[4]).toBe(500_000 + 56_240);
    expect(svc.meters('end')[4]).toBe(12_500_000 + 2000);
    const again = service();
    expect(again.svc.meters('nether')[4]).toBe(500_000 + 56_240);
  });

  it('validateRtp: the defaults raise no warning', () => {
    const { svc } = service();
    expect(svc.warnings).toEqual([]);
    expect(svc.rtpOf('end')!.method).toBe('factorised');
    expect(svc.rtpOf('nether')!.method).toBe('defaults');
  });
});
