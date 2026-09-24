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

describe('slots v2 service: owner settings at owned machines (SLOTS.md §8.6)', () => {
  it('the owner can switch the bonus buy and autoplay off; house machines keep the config', () => {
    const { svc, ctx } = service();
    const p = new FakePlayer('-1', 'Alex');
    const s = fakeSession(p, 'gold');
    const host = new SlotsV2Host(svc, s as never, ((_d: unknown, t: unknown) => t) as never);
    expect(host.buyLabel()).toBeDefined();
    expect(svc.autoplayAllowed(s as never)).toBe(true);
    (ctx.services as { get: (id: string) => unknown }).get = (id: string) => (id === 'multiplayer' ? { tableInfo: () => ({ slotsBuy: false, slotsAutoplay: false }) } : undefined);
    expect(host.buyLabel()).toBeUndefined();
    const r = svc.start(s as never, true) as { translate?: string };
    expect(r.translate).toBe('gui.burmaldaholic.slots.error.buy_disabled');
    expect(svc.autoplayAllowed(s as never)).toBe(false);
    host.auto();
    expect(JSON.stringify(p.messages)).toContain('gui.burmaldaholic.slots.error.autoplay_disabled');
  });
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
    // an interrupt (Stop / close) settles at once and jumps the cabinet to the result; a second call is a no-op
    host.presented(start as never, true);
    host.presented(start as never, false);
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

// ---- review (lane B-L8): conservation, restart mid-feature, skip, concurrency, migration, validateRtp ----------

const { tapeTotalChips, tapePoolChips, defaultMachine } = await import('./v2/logic');

describe('review: money safety', () => {
  const readPools = (): Record<string, Array<{ inc: number; rem: number }>> => JSON.parse(String(worldProps.get(POOLS_PROP)));
  const LADDER = { overworld: [5, 10, 20, 50, 100], nether: [10, 20, 50, 100, 250, 500], end: [50, 100, 250, 500, 1000, 2500, 5000] } as const;

  it('conservation over mixed spins, buys and forced jackpots: no chip minted or lost beyond the seed share', () => {
    const q: number[] = [];
    const f = fakeCtx(1e12);
    const svc = new SlotsV2Service(f.ctx as never, presenter() as never, scriptedRng(q, 5));
    svc.loadConfig();
    svc.loadPools();
    const p = new FakePlayer('-1', 'Alex');
    const sessions = { overworld: fakeSession(p, 'copper'), nether: fakeSession(p, 'gold'), end: fakeSession(p, 'netherite') };
    let stakes = 0;
    let settledSum = 0;
    let poolPaid = 0;
    let awards = 0;
    for (let i = 0; i < 450; i++) {
      const m = (['overworld', 'nether', 'end'] as const)[i % 3]!;
      const def = defaultMachine(m);
      const s = sessions[m];
      const bet = LADDER[m][i % LADDER[m].length]!;
      (s.data as Record<string, unknown>).slotsV2 = { machine: m, bet, busy: false, notice: [] };
      if (m === 'end' && i % 30 === 2) q.push(0, 21, 5, 24, 0, 1, 7, 4); // wheel → Grand
      if (m === 'end' && i % 30 === 17) q.push(0, 21, 5, 24, 0, 4); // wheel → Mini on the outer ring
      if (m === 'overworld' && i % 30 === 3) q.push(13, 0, 6, 0, 9, 79_400, 79_950, 102_000); // hunt: Mini, Minor, creeper
      const buy = m !== 'overworld' && i % 7 === 0 && q.length === 0;
      const before = readPools()[m]!;
      const nSettled = f.settled.length;
      const nCredits = f.credits.length;
      svc.spin(s as never, buy);
      const rec = Object.values(rounds())[0]!;
      const tape = decodeTape(rec.tape);
      const stake = buy ? (def.buyPriceFifths * bet) / 5 : bet;
      expect(f.placed.at(-1)).toMatchObject({ stake: { amount: stake }, worstCase: def.capMultiple * bet });
      stakes += stake;
      expect(rec.total).toBe(tapeTotalChips(tape));
      svc.skip(s as never);
      expect(f.settled.slice(nSettled).map((x) => x[1])).toEqual([rec.total]);
      settledSum += rec.total;
      const credited = f.credits.slice(nCredits).reduce((a, c) => a + c[1], 0);
      expect(credited).toBe(tapePoolChips(tape));
      poolPaid += credited;
      // pools: after = before + contribution − increment debited; award − debit = minted seed share (±1 per award)
      const after = readPools()[m]!;
      let minted = 0;
      let expectMint = 0;
      for (let t = 1; t <= 4; t++) {
        const rate = Math.round(def.jackpot.contribution[t]! * 1e6);
        const acc = before[t]!.rem + stake * rate;
        const contributed = Math.floor(acc / 1e6);
        expect(after[t]!.rem).toBe(acc - contributed * 1e6);
        const debit = before[t]!.inc + contributed - after[t]!.inc;
        expect(debit).toBeGreaterThanOrEqual(0);
        const won = tape.jackpots.filter((j) => j.tier === t);
        if (!won.length) expect(debit).toBe(0);
        minted += won.reduce((a, j) => a + j.chips, 0) - debit;
        for (const _ of won) expectMint += (def.jackpot.seeds[t]! * Math.min(bet, def.jackpot.ref)) / def.jackpot.ref;
        awards += won.length;
      }
      expect(Math.abs(minted - expectMint)).toBeLessThanOrEqual(tape.jackpots.length + 1e-6);
    }
    expect(awards).toBeGreaterThanOrEqual(30);
    // the player's balance is exactly: stakes out, settlements and pool money in
    expect(f.bal.get('-1')).toBe(1e12 - stakes + settledSum + poolPaid);
    expect(rounds()).toEqual({});
  }, 60_000);

  it('restart mid Treasure Hunt: drawn ticket at the full result, no re-roll, pool money owed exactly once', () => {
    // hunt: 1×, 1×, Mini gem, creeper
    const a = service([13, 0, 6, 0, 9, 0, 0, 79_400, 102_000]);
    const p = new FakePlayer('-2', 'Cy');
    const s = fakeSession(p, 'copper');
    a.svc.spin(s as never, false);
    const live = a.svc.liveOf('-2')!;
    s.timers.get('slots.v2.gate')!.fn(); // the board is up
    a.svc.pick(s as never); // one pick, then the server dies
    const rec = rounds()['overworld|copper'] as unknown as { tape: string; total: number; opened: number; jackpotAwards: Array<{ chips: number }> };
    expect(rec.opened).toBe(1);
    const tape = decodeTape(rec.tape);
    expect(tape.hunt!.entries.slice(0, 4)).toEqual([1, 1, -1, 0]);
    expect(tape.totalFifths).toBe(evaluateBaseFifths(tape) + 10); // base + 1× + 1×, whatever was picked so far
    expect((live.ticket as unknown as Ticket).drawn).toBe(rec.total); // core settles this, never refunds it
    let draws = 0;
    const f = fakeCtx();
    const b = new SlotsV2Service(f.ctx as never, presenter() as never, { nextInt: () => (draws++, 0) });
    b.loadConfig();
    b.loadPools();
    b.recoverRounds();
    b.recoverRounds();
    expect(draws).toBe(0);
    expect(rounds()).toEqual({});
    expect(JSON.parse(String(worldProps.get(OWED_PROP)))['-2']).toBe(rec.jackpotAwards[0]!.chips);
    b.payOwed(p as never);
    b.payOwed(p as never);
    expect(f.credits).toEqual([['-2', rec.jackpotAwards[0]!.chips, 'slots.jackpot']]);
  });

  it('skip never changes the result: gate vs skip vs leave settle the same amounts', () => {
    const runs = (['gate', 'skip', 'leave'] as const).map((how) => {
      worldProps.clear();
      const x = service(undefined, 21);
      const p = new FakePlayer('-3', 'Di');
      const s = fakeSession(p, 'gold');
      for (let i = 0; i < 40; i++) {
        x.svc.spin(s as never, i % 9 === 0);
        if (how === 'gate') s.timers.get('slots.v2.gate')!.fn();
        else if (how === 'skip') x.svc.skip(s as never);
        else x.svc.onLeave(s as never);
        expect(x.svc.liveCount()).toBe(0);
      }
      return x.settled.map((e) => e[1]);
    });
    expect(runs[1]).toEqual(runs[0]);
    expect(runs[2]).toEqual(runs[0]);
  });

  it('one player cannot run two rounds; two players racing for one Grand are served in draw order', () => {
    const { svc, placed, credits } = service([0, 21, 5, 24, 0, 1, 7, 4, 0, 21, 5, 24, 0, 1, 7, 4]);
    const p1 = new FakePlayer('-4', 'Ed');
    const p2 = new FakePlayer('-5', 'Flo');
    const s1 = fakeSession(p1, 'netherite');
    const s2 = fakeSession(p2, 'netherite');
    s2.table.key = 'overworld|netherite2';
    const s1b = fakeSession(p1, 'netherite');
    s1b.table.key = 'overworld|netherite3';
    for (const s of [s1, s2, s1b]) (s.data as Record<string, unknown>).slotsV2 = { machine: 'end', bet: 5000, busy: false, notice: [] };
    const r1 = svc.start(s1 as never, false);
    expect('ticket' in (r1 as object)).toBe(true);
    svc.begin(r1 as never);
    // same player on another cabinet (or a double click): refused before any stake
    expect('ticket' in (svc.start(s1b as never, false) as object)).toBe(false);
    expect(placed).toHaveLength(1);
    const shown = svc.meters('end')[4]!;
    const r2 = svc.start(s2 as never, false);
    svc.begin(r2 as never);
    expect(svc.meters('end')[4]).toBe(shown); // other players still see the pre-award value
    svc.skip(s1 as never);
    svc.skip(s2 as never);
    const contrib = Math.floor(5000 * 0.009);
    // bet = ref: the first takes the whole pool, the second only the fresh seed + its own contribution
    expect(credits.map((c) => c[1])).toEqual([12_500_000 + contrib, 12_500_000 + contrib]);
    expect(svc.meters('end')[4]).toBe(12_500_000);
  });

  it('v1 migration uses a customised v1 seed from the stored config after its key left the catalog', () => {
    worldProps.set('burmaldaholic:config', JSON.stringify({ 'slots.jackpot.seed.gold': 20_000 }));
    worldProps.set(V1_POOLS_PROP, JSON.stringify({ gold: { pool: 61_240, rem: 0 } }));
    const { svc } = service();
    expect(svc.meters('nether')[4]).toBe(500_000 + 41_240);
    expect(svc.meters('end')[4]).toBe(12_500_000);
  });

  it('validateRtp in the service: a cheap Nether buy price is flagged exactly (not skipped by the sample path)', () => {
    const f = fakeCtx();
    (f.ctx.config as { get: (k: string) => unknown }).get = (k: string) => {
      if (k === 'slots.enabled' || k === 'slots.validateRtp') return true;
      if (k === 'slots.nether.buy.price') return 12;
      throw new Error('unknown');
    };
    const svc = new SlotsV2Service(f.ctx as never, presenter() as never, fxSlotRng(1));
    svc.loadConfig();
    expect(svc.rtpOf('nether')!.method).toBe('defaults');
    expect(svc.warnings.some((w) => w.includes('nether') && w.includes('buy RTP'))).toBe(true);
  });
});

function evaluateBaseFifths(tape: { stops: number[] }): number {
  return evaluateSpinV2(defaultMachine('overworld'), tape.stops, false).payFifths;
}
const { evaluateSpin: evaluateSpinV2 } = await import('./v2/logic');
