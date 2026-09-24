/**
 * Runtime tests of the Bedrock presentation kit against a fake engine clock (`system` tick by tick): the scheduler
 * (lag, skip, late start, finishNow), live forms (write coalescing, DDUI failure → classic), `fx.celebrate` (final
 * frame exact after a sneak skip, overrun, settings), the table sound chain and `revealAfter`.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';

// ---------------------------------------------------------------------------------------------- fake engine
type Job = { id: number; at: number; every?: number; fn: () => void };
let tick = 0;
let jobs: Job[] = [];
let nextId = 1;
const system = {
  get currentTick() {
    return tick;
  },
  runInterval(fn: () => void, every = 1) {
    const j = { id: nextId++, at: tick + every, every, fn };
    jobs.push(j);
    return j.id;
  },
  runTimeout(fn: () => void, delay = 1) {
    const j = { id: nextId++, at: tick + Math.max(1, delay), fn };
    jobs.push(j);
    return j.id;
  },
  clearRun(id: number) {
    jobs = jobs.filter((j) => j.id !== id);
  },
  afterEvents: { scriptEventReceive: { subscribe: () => {} } },
};
function advance(n: number): void {
  for (let i = 0; i < n; i++) {
    tick++;
    for (const j of jobs.filter((x) => x.at === tick)) {
      if (!jobs.includes(j)) continue;
      if (j.every) j.at += j.every;
      else jobs = jobs.filter((x) => x !== j);
      j.fn();
    }
  }
}

let dduiThrows = false;
let setDataThrows = false;
const shown: string[] = [];
class Observable<T> {
  constructor(public data: T) {}
  getData() {
    return this.data;
  }
  setData(d: T) {
    if (setDataThrows) throw new Error('boom');
    this.data = d;
  }
}
vi.mock('@minecraft/server', () => ({
  system,
  world: { afterEvents: { playerLeave: { subscribe: () => {} } } },
  MolangVariableMap: class {
    setFloat() {}
    setColorRGB() {}
  },
  PlayerPermissionLevel: { Operator: 2 },
}));
vi.mock('@minecraft/server-ui', () => ({
  CustomForm: class {
    open = false;
    constructor() {
      if (dduiThrows) throw new Error('no ddui');
    }
    label() {
      return this;
    }
    header() {
      return this;
    }
    button() {
      return this;
    }
    show() {
      this.open = true;
      shown.push('ddui');
      return new Promise((r) => setTimeout(() => r('ClientClosed'), 0));
    }
    isShowing() {
      return this.open;
    }
    close() {
      this.open = false;
    }
  },
  ObservableUIRawMessage: Observable,
  ObservableBoolean: Observable,
  ActionFormData: class {
    title() {
      return this;
    }
    body() {
      return this;
    }
    button() {
      return this;
    }
    show() {
      shown.push('classic');
      return Promise.resolve({ canceled: true, cancelationReason: 'UserClosed' });
    }
  },
  DataDrivenScreenClosedReason: { ClientClosed: 'ClientClosed', ServerClosed: 'ServerClosed', UserBusy: 'UserBusy' },
  FormCancelationReason: { UserBusy: 'UserBusy', UserClosed: 'UserClosed' },
}));

const { playTimeline } = await import('./scheduler');
const { createLiveForm, dduiHealthy, resetDduiHealth } = await import('./live-form');
const { FxService } = await import('./fx');
const { revealAfter, soundTimeline } = await import('./table-fx');
const { Timeline, LOCAL, SHARED } = await import('../logic/anim/timeline');
const { DEFAULT_TIERS } = await import('../logic/anim/win-tier');

// ---------------------------------------------------------------------------------------------- fake player
interface Call {
  what: string;
  arg?: unknown;
}
function fakePlayer(props: Record<string, unknown> = {}) {
  const calls: Call[] = [];
  const p = {
    id: 'p1',
    isValid: true,
    isSneaking: false,
    location: { x: 0, y: 64, z: 0 },
    playerPermissionLevel: 0,
    calls,
    getDynamicProperty: (k: string) => props[k],
    playSound: (id: string, o: unknown) => calls.push({ what: 'sound', arg: [id, o] }),
    runCommand: (c: string) => calls.push({ what: 'cmd', arg: c }),
    camera: { fade: (o: unknown) => calls.push({ what: 'fade', arg: o }) },
    onScreenDisplay: {
      setTitle: (t: unknown, o: { subtitle?: unknown }) => calls.push({ what: 'title', arg: [t, o.subtitle] }),
      updateSubtitle: (s: unknown) => calls.push({ what: 'subtitle', arg: s }),
    },
    spawnParticle: () => calls.push({ what: 'particle' }),
    sendMessage: (m: unknown) => calls.push({ what: 'chat', arg: m }),
    dimension: {} as Record<string, unknown>,
  };
  p.dimension = {
    getPlayers: () => [p],
    spawnParticle: (id: string) => calls.push({ what: 'particle', arg: id }),
  };
  return p;
}
const json = (x: unknown) => JSON.stringify(x);
/** Literal text of a raw message (translate keys kept as `{key}`), e.g. `§a+6123`. */
const flat = (x: unknown): string =>
  x === null || typeof x !== 'object' ? '' : Array.isArray(x) ? x.map(flat).join('') : 'text' in x ? String((x as { text: unknown }).text) : 'translate' in x ? `{${String((x as { translate: unknown }).translate)}}` + flat((x as { with?: unknown }).with) : flat((x as { rawtext?: unknown }).rawtext);

beforeEach(() => {
  tick = 0;
  jobs = [];
  shown.length = 0;
  dduiThrows = false;
  setDataThrows = false;
  resetDduiHealth();
});

describe('scheduler', () => {
  const tl = Timeline.builder('t', 0).clock(LOCAL).add(0, 0, 'a', -1).add(100, 0, 'b', -1).add(150, 0, 'c', -1).add(400, 0, 'd', -1).build();

  it('fires every beat once and in order, even when the server lags (frames skipped)', () => {
    const seen: string[] = [];
    let ended: [number, boolean] | undefined;
    playTimeline(tl, { beat: (b) => seen.push(b.kind), end: (t, i) => (ended = [t, i]) }, { periodTicks: 4 });
    advance(20);
    expect(seen).toEqual(['a', 'b', 'c', 'd']);
    expect(ended).toEqual([400, false]);
    expect(jobs).toHaveLength(0);
  });

  it('late start and skip pass beats over without replaying them', () => {
    const seen: string[] = [];
    playTimeline(tl, { beat: (b) => seen.push(b.kind) }, { startMs: 120 });
    advance(20);
    expect(seen).toEqual(['c', 'd']);
    const seen2: string[] = [];
    const s = playTimeline(tl, { beat: (b) => seen2.push(b.kind) });
    advance(2);
    s.skip();
    advance(10);
    expect(seen2).toEqual(['a', 'b', 'd']);
  });

  it('never skips a shared beat; finishNow ends once with the terminal time; alive=false interrupts', () => {
    const shared = Timeline.builder('s', 0).clock(SHARED).add(0, 1000, 'spin', 0).build();
    const s = playTimeline(shared, {});
    advance(2);
    s.skip();
    expect(s.now()).toBe(100);
    const ends: Array<[number, boolean]> = [];
    const s2 = playTimeline(tl, { end: (t, i) => ends.push([t, i]) });
    s2.finishNow();
    s2.finishNow();
    expect(ends).toEqual([[400, false]]);
    let alive = true;
    const ends3: boolean[] = [];
    playTimeline(tl, { end: (_t, i) => ends3.push(i) }, { alive: () => alive });
    alive = false;
    advance(2);
    expect(ends3).toEqual([true]);
  });
});

describe('live form (spike B-S0 decisions)', () => {
  it('DDUI coalesces writes to ≤ 1 per component per 2 t and keeps the latest value', () => {
    const f = createLiveForm(fakePlayer() as never, 'T', { preferDdui: true, actionbar: () => {} });
    expect(f.kind).toBe('ddui');
    f.label('r', 'a');
    for (let i = 0; i < 10; i++) f.set('r', `v${i}`); // same tick
    expect(f.writes).toBe(1);
    advance(2);
    expect(f.writes).toBe(2);
    f.set('r', 'v9'); // unchanged: no write
    advance(4);
    expect(f.writes).toBe(2);
  });

  it('falls back to classic when DDUI cannot be built or a write throws, for the rest of the session', () => {
    dduiThrows = true;
    expect(createLiveForm(fakePlayer() as never, 'T', { preferDdui: true, actionbar: () => {} }).kind).toBe('classic');
    expect(dduiHealthy()).toBe(false);
    dduiThrows = false;
    expect(createLiveForm(fakePlayer() as never, 'T', { preferDdui: true, actionbar: () => {} }).kind).toBe('classic');
    resetDduiHealth();
    const f = createLiveForm(fakePlayer() as never, 'T', { preferDdui: true, actionbar: () => {} });
    f.label('r', 'a');
    setDataThrows = true;
    f.set('r', 'b');
    expect(dduiHealthy()).toBe(false);
    expect(createLiveForm(fakePlayer() as never, 'T', { preferDdui: false, actionbar: () => {} }).kind).toBe('classic');
  });

  it('classic routes live text to the action bar while closed and leaves hidden/disabled buttons out', async () => {
    const bar: string[] = [];
    const f = createLiveForm(fakePlayer() as never, 'T', { preferDdui: false, actionbar: (id) => bar.push(id) });
    f.label('s', 'x').button('spin', 'Spin', () => {}).button('buy', 'Buy', () => {}, { disabled: true });
    f.set('s', 'y');
    f.setVisible('s', false);
    f.set('s', 'z');
    expect(bar).toEqual(['s']);
    expect(await f.show()).toBe('closed');
    expect(shown).toEqual(['classic']);
  });
});

describe('fx.celebrate', () => {
  const hudCalls: unknown[] = [];
  const fx = new FxService({ actionbar: (_p, ch, m) => hudCalls.push([ch, m]) });
  const lastSubtitle = (p: ReturnType<typeof fakePlayer>) => {
    const c = p.calls.filter((x) => x.what === 'subtitle' || x.what === 'title').pop()!;
    return flat(c.what === 'title' ? (c.arg as unknown[])[1] : c.arg);
  };

  it('rolls the subtitle up to the exact return, upgrades the word, and resolves done at the form delay', async () => {
    const p = fakePlayer();
    const h = fx.celebrate(p as never, { tier: 'EPIC', ret: 6123, stake: 100, game: 't', seed: 1, table: DEFAULT_TIERS });
    let done = false;
    void h.done.then(() => (done = true));
    advance(30);
    const titles = p.calls.filter((c) => c.what === 'title').map((c) => json((c.arg as unknown[])[0]));
    expect(titles[0]).toContain('gui.burmaldaholic.fx.tier.big');
    expect(titles.some((x) => x.includes('fx.tier.mega'))).toBe(true);
    expect(titles[titles.length - 1]).toContain('fx.tier.epic');
    expect(p.calls.some((c) => c.what === 'fade')).toBe(true);
    expect(p.calls.some((c) => c.what === 'cmd' && String(c.arg).startsWith('camerashake'))).toBe(true);
    expect(lastSubtitle(p)).toContain('+6123');
    advance(40);
    await Promise.resolve();
    expect(done).toBe(true);
    const subs = p.calls.filter((c) => c.what === 'subtitle').map((c) => Number(/\+(\d+)/.exec(flat(c.arg))![1]));
    for (let i = 1; i < subs.length; i++) expect(subs[i]).toBeGreaterThan(subs[i - 1]!);
  });

  it('sneak skips to the final word and amount at once', async () => {
    const p = fakePlayer();
    const h = fx.celebrate(p as never, { tier: 'MEGA', ret: 3000, stake: 100, game: 't', seed: 1 });
    advance(2);
    p.isSneaking = true;
    advance(4);
    await h.done;
    const last = p.calls.filter((c) => c.what === 'title').pop()!;
    expect(json((last.arg as unknown[])[0])).toContain('fx.tier.mega');
    expect(lastSubtitle(p)).toContain('+3000');
    expect(jobs.filter((j) => j.every)).toHaveLength(0);
  });

  it('reduce motion: no camera; effects volume 0: silent; overrun finishes the previous one', async () => {
    const p = fakePlayer({ 'burmaldaholic:anim.reduceMotion': true, 'burmaldaholic:anim.volume': 0 });
    const first = fx.celebrate(p as never, { tier: 'EPIC', ret: 6000, stake: 100, game: 't', seed: 1 });
    fx.celebrate(p as never, { tier: 'BIG', ret: 1200, stake: 100, game: 't', seed: 1 });
    await first.done;
    advance(80);
    expect(p.calls.some((c) => c.what === 'fade' || c.what === 'cmd' || c.what === 'sound')).toBe(false);
    expect(lastSubtitle(p)).toContain('+1200');
  });

  it('WIN uses the action-bar channel only', () => {
    hudCalls.length = 0;
    const p = fakePlayer();
    fx.celebrate(p as never, { tier: 'WIN', ret: 40, stake: 20, game: 't', seed: 1 });
    advance(2);
    expect(hudCalls).toHaveLength(1);
    expect(flat((hudCalls[0] as unknown[])[1])).toBe('§a+40');
    expect(p.calls.some((c) => c.what === 'title')).toBe(false);
  });
});

describe('table helpers', () => {
  it('soundTimeline plays each slot at its tick through one timeout chain', () => {
    const p = fakePlayer();
    const h = soundTimeline(p.dimension as never, { x: 0, y: 0, z: 0 }, [
      { at: 0, id: 'roulette_ball_roll' },
      { at: 250, id: 'roulette_ball_roll', pitch: 0.9 },
      { at: 1000, id: 'roulette_ball_settle' },
    ]);
    expect(p.calls.filter((c) => c.what === 'sound')).toHaveLength(1);
    expect(jobs).toHaveLength(1);
    advance(5);
    expect(p.calls.filter((c) => c.what === 'sound')).toHaveLength(2);
    advance(15);
    const ids = p.calls.filter((c) => c.what === 'sound').map((c) => (c.arg as unknown[])[0]);
    expect(ids).toEqual(['burmaldaholic.roulette_ball_roll', 'burmaldaholic.roulette_ball_roll', 'burmaldaholic.roulette_ball_settle']);
    expect(h.slots).toHaveLength(3);
  });

  it('revealAfter runs once after the delay and can be cancelled', () => {
    let n = 0;
    revealAfter(3, () => n++);
    const cancel = revealAfter(3, () => (n += 10));
    cancel();
    advance(5);
    expect(n).toBe(1);
    revealAfter(0, () => n++);
    expect(n).toBe(2);
  });
});
