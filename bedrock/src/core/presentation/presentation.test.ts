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
/** Every Observable write (object, tick) — for the ≤ 1 write per component per 2 t budget. */
const writeLog: Array<{ o: unknown; tick: number; data: unknown }> = [];
let lastForm: { open: boolean } | undefined;
/** Keeps a DDUI form open until the server closes it. */
let holdOpen = false;
class Observable<T> {
  constructor(public data: T) {}
  getData() {
    return this.data;
  }
  setData(d: T) {
    if (setDataThrows) throw new Error('boom');
    this.data = d;
    writeLog.push({ o: this, tick, data: d });
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
      lastForm = this; // eslint-disable-line @typescript-eslint/no-this-alias
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
      return new Promise((r) => {
        this.resolve = r;
        setTimeout(() => !holdOpen && this.open && r('ClientClosed'), 0);
      });
    }
    isShowing() {
      return this.open;
    }
    resolve: (r: string) => void = () => {};
    close() {
      this.open = false;
      this.resolve('ServerClosed');
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
const { burst } = await import('./particles');
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
  writeLog.length = 0;
  holdOpen = false;
  lastForm = undefined;
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

// ---------------------------------------------------------------------------------------------- review (adversarial)
describe('review: live form budget and failure', () => {
  const gapsOk = () => {
    const byObj = new Map<unknown, number[]>();
    for (const w of writeLog) byObj.set(w.o, [...(byObj.get(w.o) ?? []), w.tick]);
    for (const ticks of byObj.values()) for (let i = 1; i < ticks.length; i++) expect(ticks[i]! - ticks[i - 1]!).toBeGreaterThanOrEqual(2);
  };

  it('components written on different ticks each keep their own 2 t window', () => {
    const f = createLiveForm(fakePlayer() as never, 'T', { preferDdui: true, actionbar: () => {} });
    f.label('x', 'x0').label('y', 'y0');
    f.set('x', 'x1'); // tick 0
    advance(1);
    f.set('y', 'y1'); // tick 1
    f.set('x', 'x2'); // pending until tick 2
    f.set('y', 'y2'); // pending until tick 3 — must NOT ride along with x at tick 2
    for (let i = 0; i < 20; i++) {
      advance(1);
      f.set('x', `x${i + 3}`);
      f.set('y', `y${i + 3}`);
    }
    advance(4);
    gapsOk();
    const last = (d: string) => writeLog.filter((w) => JSON.stringify(w.data).includes(d)).length;
    expect(last('x22')).toBe(1);
    expect(last('y22')).toBe(1);
    expect(jobs).toHaveLength(0);
  });

  it('a value set back to what the client shows cancels the pending write', () => {
    const f = createLiveForm(fakePlayer() as never, 'T', { preferDdui: true, actionbar: () => {} });
    f.label('r', 'a');
    f.set('r', 'b');
    f.set('r', 'c');
    f.set('r', 'b');
    advance(4);
    expect(f.writes).toBe(1);
  });

  it('a write failure while the form is open closes it and show() says fallback; next forms are classic', async () => {
    holdOpen = true;
    const f = createLiveForm(fakePlayer() as never, 'T', { preferDdui: true, actionbar: () => {} });
    f.label('r', 'a').button('b', 'B', () => {});
    const closed = f.show();
    await Promise.resolve();
    setDataThrows = true;
    f.setDisabled('b', true);
    expect(await closed).toBe('fallback');
    expect(lastForm?.open).toBe(false);
    expect(dduiHealthy()).toBe(false);
    expect(createLiveForm(fakePlayer() as never, 'T', { preferDdui: true, actionbar: () => {} }).kind).toBe('classic');
  });

  it('close() while shown reports server; set on an unknown id is ignored', async () => {
    holdOpen = true;
    const f = createLiveForm(fakePlayer() as never, 'T', { preferDdui: true, actionbar: () => {} });
    f.label('r', 'a');
    const closed = f.show();
    f.set('nope', 'x');
    f.close();
    expect(await closed).toBe('server');
    expect(f.writes).toBe(0);
  });
});

describe('review: scheduler under lag', () => {
  it('a sink exception interrupts once and leaves no interval', () => {
    const tl = Timeline.builder('t', 0).clock(LOCAL).add(0, 0, 'a', -1).add(500, 0, 'b', -1).build();
    const ends: boolean[] = [];
    playTimeline(tl, {
      beat: () => {
        throw new Error('x');
      },
      end: (_t, i) => ends.push(i),
    });
    advance(20);
    expect(ends).toEqual([true]);
    expect(jobs).toHaveLength(0);
  });

  it('one huge lag spike: every beat still fires exactly once, in order, then end', () => {
    const b = Timeline.builder('t', 0).clock(LOCAL);
    for (let i = 0; i < 30; i++) b.add(i * 37, 0, `k${i}`, -1);
    const tl = b.build();
    const seen: string[] = [];
    let ends = 0;
    playTimeline(tl, { beat: (x) => seen.push(x.kind), end: () => ends++ }, { periodTicks: 50 });
    advance(120);
    expect(seen).toEqual(tl.beats.map((x) => x.kind));
    expect(ends).toBe(1);
  });
});

describe('review: fx.celebrate edge cases', () => {
  const hudCalls: unknown[] = [];
  const fx = new FxService({ actionbar: (_p, ch, m) => hudCalls.push([ch, m]) });
  const titles = (p: ReturnType<typeof fakePlayer>) => p.calls.filter((c) => c.what === 'title');

  it('already sneaking when the win lands: the final word and exact amount still show', async () => {
    const p = fakePlayer();
    p.isSneaking = true;
    const h = fx.celebrate(p as never, { tier: 'EPIC', ret: 6123, stake: 100, game: 't', seed: 1 });
    advance(4);
    await h.done;
    const last = titles(p).pop();
    expect(last).toBeDefined();
    expect(json((last!.arg as unknown[])[0])).toContain('fx.tier.epic');
    expect(flat((last!.arg as unknown[])[1])).toContain('+6123');
  });

  it('MAX WIN plate is on the final frame even when the roll-up already showed the total', async () => {
    const p = fakePlayer();
    const h = fx.celebrate(p as never, { tier: 'BIG', ret: 2000, stake: 100, game: 't', seed: 1, maxWin: true, words: { ...(await import('../logic/anim/win-tier')).CORE_TIER_WORDS, maxWin: 'k.max' } });
    advance(80);
    await h.done;
    const c = p.calls.filter((x) => x.what === 'subtitle' || x.what === 'title').pop()!;
    const text = flat(c.what === 'title' ? (c.arg as unknown[])[1] : c.arg);
    expect(text).toContain('{k.max}');
    expect(text).toContain('+2000');
  });

  it('player leaves mid-animation: done resolves, nothing more is drawn, no interval remains', async () => {
    const p = fakePlayer();
    const h = fx.celebrate(p as never, { tier: 'MEGA', ret: 3000, stake: 100, game: 't', seed: 1 });
    advance(6);
    p.isValid = false;
    const n = p.calls.length;
    advance(4);
    await h.done;
    advance(80);
    expect(p.calls.length).toBe(n);
    expect(jobs.filter((j) => j.every)).toHaveLength(0);
  });

  it('celebrations off: no particles or camera, action bar with the real tier word', () => {
    hudCalls.length = 0;
    const p = fakePlayer({ 'burmaldaholic:anim.celebrations': 'off' });
    fx.celebrate(p as never, { tier: 'EPIC', ret: 6000, stake: 100, game: 't', seed: 1 });
    advance(80);
    expect(p.calls.some((c) => ['particle', 'fade', 'cmd', 'title'].includes(c.what))).toBe(false);
    expect(json(hudCalls)).toContain('fx.tier.epic');
  });

  it('flashes off: no fade; speed 150 ends sooner than 100', async () => {
    const p = fakePlayer({ 'burmaldaholic:anim.flashes': false, 'burmaldaholic:anim.speed': 150 });
    const h = fx.celebrate(p as never, { tier: 'EPIC', ret: 6000, stake: 100, game: 't', seed: 1 });
    const p2 = fakePlayer();
    const h2 = fx.celebrate({ ...p2, id: 'p2' } as never, { tier: 'EPIC', ret: 6000, stake: 100, game: 't', seed: 1 });
    expect(h.timeline.endMs()).toBeLessThan(h2.timeline.endMs());
    advance(80);
    await h.done;
    expect(p.calls.some((c) => c.what === 'fade')).toBe(false);
  });

  it("burst: 'off' and 'mine' viewers never get someone else's celebration; the owner still does", () => {
    const owner = fakePlayer();
    const mine = { ...fakePlayer({ 'burmaldaholic:anim.celebrations': 'mine' }), id: 'p2' };
    const off = { ...fakePlayer({ 'burmaldaholic:anim.celebrations': 'off' }), id: 'p3' };
    const all = { ...fakePlayer(), id: 'p4' };
    const got: string[] = [];
    for (const v of [owner, mine, off, all]) (v as { spawnParticle: () => void }).spawnParticle = () => got.push(v.id);
    let dimCalls = 0;
    const dim = { getPlayers: () => [owner, mine, off, all], spawnParticle: () => dimCalls++ };
    expect(burst(dim as never, 'burmaldaholic:sparkle', { x: 0, y: 0, z: 0 }, { owner: owner as never, celebration: true })).toBe(2);
    expect(got.sort()).toEqual(['p1', 'p4']);
    expect(dimCalls).toBe(0);
    got.length = 0;
    // nobody opted out: one dimension call
    const dim2 = { getPlayers: () => [owner, all], spawnParticle: () => dimCalls++ };
    expect(burst(dim2 as never, 'burmaldaholic:sparkle', { x: 0, y: 0, z: 0 }, { owner: owner as never, celebration: true })).toBe(1);
    expect(dimCalls).toBe(1);
  });

  it('each viewer hears a table sound at their own effects volume', () => {
    const a = { ...fakePlayer({ 'burmaldaholic:anim.volume': 50 }), id: 'vol-a' }; // fresh rate-limit history
    const b = { ...fakePlayer({ 'burmaldaholic:anim.volume': 0 }), id: 'p2' };
    const dim = { getPlayers: () => [a, b] };
    fx.soundAt(dim as never, { x: 0, y: 0, z: 0 }, 'roulette_ball_settle');
    const s = a.calls.filter((c) => c.what === 'sound');
    expect(s).toHaveLength(1);
    expect((s[0]!.arg as [string, { volume: number }])[1].volume).toBe(0.5);
    expect(b.calls.some((c) => c.what === 'sound')).toBe(false);
  });
});
