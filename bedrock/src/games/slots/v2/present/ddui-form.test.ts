/**
 * SlotPresenter / DDUI view with a fake @minecraft/server clock: one session per spin, `presented` exactly once
 * (end = gate, or interrupt = reveal), skip compresses, the hunt reveals entry i on pick i, the DDUI form writes
 * only changed labels and flips Spin ↔ Stop.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';

const clock = vi.hoisted(() => {
  const c = {
    tick: 0,
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

const forms = vi.hoisted(() => ({ list: [] as Array<{ labels: Map<string, unknown>; buttons: Map<string, () => void>; writes: string[]; showing: boolean; close: () => void }>, obsWrites: [] as Array<{ o: unknown; tick: number; v: unknown }> }));

vi.mock('@minecraft/server', () => ({
  world: {
    getDynamicProperty: () => undefined,
    setDynamicProperty: () => {},
    getDynamicPropertyIds: () => [],
    getAllPlayers: () => [],
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

vi.mock('@minecraft/server-ui', () => {
  class Obs {
    constructor(public v: unknown) {}
    setData(v: unknown): void {
      forms.obsWrites.push({ o: this, tick: clock.tick, v });
      this.v = v;
    }
  }
  class CustomForm {
    labels = new Map<string, unknown>();
    buttons = new Map<string, () => void>();
    writes: string[] = [];
    showing = false;
    private order: Obs[] = [];
    constructor() {
      forms.list.push(this as never);
    }
    label(o: Obs): this {
      this.order.push(o);
      return this;
    }
    button(o: Obs, fn: () => void): this {
      this.order.push(o);
      this.buttons.set(String(this.buttons.size), fn);
      return this;
    }
    show(): Promise<void> {
      this.showing = true;
      return new Promise(() => {});
    }
    close(): void {
      this.showing = false;
    }
    isShowing(): boolean {
      return this.showing;
    }
  }
  return { CustomForm, ObservableString: Obs, ObservableUIRawMessage: Obs, ActionFormData: class {}, ModalFormData: class {}, MessageFormData: class {}, FormCancelationReason: {}, uiManager: { closeAllForms: () => {} } };
});

const { SlotPresenter, DduiSlotForm } = await import('./ddui-form');
const { fakeDef, fakeRound, fakeTape, timelineOf } = await import('./fixtures.test-util');
const { SLOT_BEAT } = await import('../logic/timeline');
const { terminalScreen } = await import('./features');
const { stubSlotTimeline } = await import('./frames');
const { SHARED_PROFILE } = await import('../../../../core/logic/anim/timeline');

class FakePlayer {
  isValid = true;
  isSneaking = false;
  location = { x: 0, y: 0, z: 0 };
  props = new Map<string, unknown>();
  sounds: string[] = [];
  commands: string[] = [];
  onScreenDisplay = { setTitle: () => {}, updateSubtitle: () => {}, setActionBar: () => {} };
  camera = { fade: () => {}, setCamera: () => {}, clear: () => {} };
  getDynamicProperty(k: string): unknown {
    return this.props.get(k);
  }
  setDynamicProperty(k: string, v: unknown): void {
    this.props.set(k, v);
  }
  playSound(id: string): void {
    this.sounds.push(id);
  }
  playMusic(id: string): void {
    this.sounds.push(`music:${id}`);
  }
  stopMusic(): void {
    this.sounds.push('music:stop');
  }
  runCommand(c: string): void {
    this.commands.push(c);
  }
}

function view() {
  const shown: unknown[] = [];
  const done: boolean[] = [];
  const v = {
    alive: true,
    show: (s: unknown) => shown.push(s),
    jackpots: () => {},
    alive_: true,
    huntMode: vi.fn(),
    suspended: vi.fn(),
    finished: (_s: unknown, i: boolean) => done.push(i),
  };
  return { v: { ...v, alive: () => v.alive_ }, shown, done, ctl: v };
}

function host(round: ReturnType<typeof fakeRound>, extra: Record<string, unknown> = {}) {
  const presented: Array<{ interrupted: boolean }> = [];
  const picks: number[] = [];
  const h = {
    title: { text: 'x' },
    jackpotLine: () => ({ text: '' }),
    spinLabel: () => ({ translate: 'gui.burmaldaholic.slots.spin' }),
    betLabel: () => ({ text: '' }),
    spin: () => ({ round, timeline: timelineOf(round) }),
    presented: (_s: unknown, interrupted: boolean) => presented.push({ interrupted }),
    huntPick: (_s: unknown, i: number) => {
      picks.push(i);
      return round.tape.hunt!.entries[i];
    },
    betDown: () => {},
    betUp: () => {},
    auto: () => {},
    paytable: () => {},
    leave: () => {},
    ...extra,
  };
  return { h, presented, picks };
}

beforeEach(() => {
  clock.tick = 0;
  clock.runs.clear();
  forms.list.length = 0;
  forms.obsWrites.length = 0;
});

describe('SlotPresenter', () => {
  const def = fakeDef('overworld');

  it('plays the timeline with one interval, settles once at the end and shows the terminal screen', () => {
    const round = fakeRound(def, fakeTape(def, [1, 5, 9, 13, 17]));
    const tl = timelineOf(round);
    const p = new FakePlayer();
    const { h, presented } = host(round);
    const { v, shown, done } = view();
    const pr = new SlotPresenter(p as never, h as never, v as never);
    pr.play({ round, timeline: tl });
    expect(clock.runs.size).toBe(1);
    clock.advance(Math.ceil(tl.endMs() / 50) + 4);
    expect(presented).toEqual([{ interrupted: false }]);
    expect(done).toEqual([false]);
    expect(JSON.stringify(shown[shown.length - 1])).toBe(JSON.stringify(terminalScreen(round)));
    expect(p.sounds.filter((s) => s === 'burmaldaholic.slots.reel_stop').length).toBe(5);
    expect(clock.runs.size).toBe(0);
  });

  it('interrupt (form closed) = reveal: settles at once with interrupted = true', () => {
    const round = fakeRound(def, fakeTape(def, [2, 4, 6, 8, 10]));
    const p = new FakePlayer();
    const { h, presented } = host(round);
    const { v, shown, ctl } = view();
    const pr = new SlotPresenter(p as never, h as never, v as never);
    pr.play({ round, timeline: timelineOf(round) });
    clock.advance(6);
    ctl.alive_ = false;
    clock.advance(4);
    expect(presented).toEqual([{ interrupted: true }]);
    expect(JSON.stringify(shown[shown.length - 1])).toBe(JSON.stringify(terminalScreen(round)));
  });

  it('skip compresses groups but never skips the result; repeated skips reach the end', () => {
    const round = fakeRound(def, fakeTape(def, [3, 3, 3, 3, 3]));
    const tl = timelineOf(round);
    const p = new FakePlayer();
    const { h, presented } = host(round);
    const { v, shown } = view();
    const pr = new SlotPresenter(p as never, h as never, v as never);
    pr.play({ round, timeline: tl });
    clock.advance(2);
    pr.skip();
    clock.advance(6);
    for (let i = 0; i < 20 && presented.length === 0; i++) {
      pr.skip();
      clock.advance(2);
    }
    expect(presented.length).toBe(1);
    expect(JSON.stringify(shown[shown.length - 1])).toBe(JSON.stringify(terminalScreen(round)));
    expect(clock.tick).toBeLessThan(Math.ceil(tl.endMs() / 50));
  });

  it('Treasure Hunt: pauses after the intro; pick i asks the server for entry i; resumes to the end', () => {
    const round = fakeRound(def, fakeTape(def, [1, 2, 3, 4, 5], { hunt: [5, 2, 0] }));
    const tl = timelineOf(round);
    const p = new FakePlayer();
    const { h, presented, picks } = host(round);
    const { v, ctl } = view();
    const pr = new SlotPresenter(p as never, h as never, v as never);
    let ready = 0;
    pr.onPickReady = () => ready++;
    pr.play({ round, timeline: tl });
    const intro = tl.beats.find((b) => b.kind === SLOT_BEAT.BONUS_INTRO)!;
    clock.advance(Math.ceil((intro.at + intro.dur) / 50) + 4);
    expect(pr.inHunt()).toBe(true);
    expect(ctl.huntMode).toHaveBeenCalledWith(true);
    expect(ready).toBe(1);
    clock.advance(100);
    expect(presented.length).toBe(0); // a required choice is never auto-made
    pr.pick(false);
    clock.advance(20);
    expect(picks).toEqual([0]);
    pr.pick(true); // open all: continues on its own until the creeper
    clock.advance(200);
    expect(picks).toEqual([0, 1, 2]);
    clock.advance(Math.ceil(tl.endMs() / 50));
    expect(presented).toEqual([{ interrupted: false }]);
  });
});

describe('DduiSlotForm', () => {
  it('declares J/H/R/S labels + buttons; Spin → Stop while spinning; labels written only on change', () => {
    const def = fakeDef('nether');
    const round = fakeRound(def, fakeTape(def, [1, 2, 3, 4, 5]));
    const p = new FakePlayer();
    const { h, presented } = host(round);
    const f = new DduiSlotForm(p as never, h as never);
    expect(f.open()).toBe(true);
    const form = forms.list[0]!;
    expect(form.showing).toBe(true);
    const spin = form.buttons.get('0')!;
    spin();
    clock.advance(4);
    expect(f.presenter.running()).toBe(true);
    clock.advance(400);
    expect(presented.length).toBe(1);
  });
});

// ---------------------------------------------------------------------------------------------------------
// Adversarial (review of lane B-L9): late join into a pending hunt, leaving mid-spin / mid-cinematic,
// write budgets, and nothing past the hunt gate before the picks.
// ---------------------------------------------------------------------------------------------------------

describe('SlotPresenter (adversarial)', () => {
  const def = fakeDef('overworld');
  const textOfAny = (x: unknown): string => JSON.stringify(x);

  it('hunt: nothing after the board intro (roll-up, tier, title, sounds) plays before the picks (F6/F7)', () => {
    // hunt prize 25× the bet: the roll-up after the hunt is a BIG win
    const round = fakeRound(def, fakeTape(def, [1, 2, 3, 4, 5], { hunt: [25, 0], totalFifths: 5 * 25 }), 'BIG');
    const tl = timelineOf(round);
    const p = new FakePlayer();
    const titles: unknown[] = [];
    p.onScreenDisplay.setTitle = (...a: unknown[]) => void titles.push(a);
    const { h, presented } = host(round);
    const { v, shown } = view();
    const pr = new SlotPresenter(p as never, h as never, v as never);
    pr.play({ round, timeline: tl });
    const intro = tl.beats.find((b) => b.kind === SLOT_BEAT.BONUS_INTRO)!;
    // advance in odd steps so a frame lands strictly after the intro end
    clock.advance(Math.ceil((intro.at + intro.dur) / 50) + 3);
    expect(pr.inHunt()).toBe(true);
    const all = shown.map(textOfAny).join('\n');
    expect(all).not.toContain('gui.burmaldaholic.slots.tier.');
    expect(all).not.toContain('gui.burmaldaholic.slots.win"');
    expect(titles.length).toBe(0);
    expect(p.sounds.some((s) => /rollup|big_win|win_nice/.test(s))).toBe(false);
    expect(presented.length).toBe(0);
  });

  it('late join / resume past the intro of a pending hunt still stops for the picks', () => {
    const round = fakeRound(def, fakeTape(def, [1, 2, 3, 4, 5], { hunt: [5, 2, 0] }));
    const tl = timelineOf(round);
    const intro = tl.beats.find((b) => b.kind === SLOT_BEAT.BONUS_INTRO)!;
    const p = new FakePlayer();
    const { h, presented, picks } = host(round);
    const { v } = view();
    const pr = new SlotPresenter(p as never, h as never, v as never);
    let ready = 0;
    pr.onPickReady = () => ready++;
    pr.play({ round, timeline: tl, startMs: intro.at + intro.dur + 400 });
    clock.advance(4);
    expect(pr.inHunt()).toBe(true);
    expect(ready).toBe(1);
    expect(presented.length).toBe(0);
    pr.pick(true);
    clock.advance(400);
    expect(picks).toEqual([0, 1, 2]);
    clock.advance(Math.ceil(tl.endMs() / 50));
    expect(presented).toEqual([{ interrupted: false }]);
  });

  it('a player who leaves mid-spin settles exactly once (interrupted) and leaves no timers behind', () => {
    for (const leaveAt of [1, 5, 12, 20, 30]) {
      clock.runs.clear();
      const round = fakeRound(def, fakeTape(def, [2, 4, 6, 8, 10]));
      const p = new FakePlayer();
      const { h, presented } = host(round);
      const { v } = view();
      const pr = new SlotPresenter(p as never, h as never, v as never);
      pr.play({ round, timeline: timelineOf(round) });
      clock.advance(leaveAt);
      p.isValid = false;
      clock.advance(400);
      expect(presented.length).toBe(1);
      expect(pr.running()).toBe(false);
      expect(clock.runs.size).toBe(0);
    }
  });

  it('a player who leaves during a Grand cinematic settles exactly once', () => {
    const round = fakeRound(def, fakeTape(def, [1, 5, 9, 13, 17], { jackpots: [{ tier: 4, chips: 50000, owned: true }] }));
    const tl = timelineOf(round);
    const jp = tl.beats.find((b) => b.kind === SLOT_BEAT.JACKPOT)!;
    const p = new FakePlayer();
    const { h, presented } = host(round);
    const { v, ctl } = view();
    const pr = new SlotPresenter(p as never, h as never, v as never);
    pr.play({ round, timeline: tl });
    clock.advance(Math.ceil(jp.at / 50) + 3);
    expect(ctl.suspended).toHaveBeenCalledWith(true);
    p.isValid = false;
    clock.advance(2000);
    expect(presented.length).toBe(1);
    expect(pr.running()).toBe(false);
  });

  it('DDUI: every label is written at most once per 2 ticks, even with Stop spam; the jackpot label ≤ 1/s', () => {
    for (const m of ['overworld', 'nether', 'end'] as const) {
      forms.obsWrites.length = 0;
      forms.list.length = 0;
      clock.runs.clear();
      const d = fakeDef(m);
      const round = fakeRound(d, fakeTape(d, [3, 7, 11, 15, 19]));
      const p = new FakePlayer();
      let jp = 0;
      const { h, presented } = host(round, { jackpotLine: () => ({ text: `jp ${jp++}` }) });
      const f = new DduiSlotForm(p as never, h as never);
      expect(f.open()).toBe(true);
      const spin = forms.list[0]!.buttons.get('0')!;
      spin();
      for (let i = 0; i < 400 && presented.length === 0; i++) {
        clock.advance(1);
        if (i % 3 === 1) spin(); // Stop spam
      }
      expect(presented.length).toBe(1);
      const byObs = new Map<unknown, number[]>();
      for (const w of forms.obsWrites) byObs.set(w.o, [...(byObs.get(w.o) ?? []), w.tick]);
      for (const ticks of byObs.values()) {
        // the settle tick (terminal screen + between-spins labels) is one final update: ignore its duplicate
        const live = [...new Set(ticks)].length === ticks.length ? ticks : ticks.slice(0, -1);
        for (let i = 1; i < live.length; i++) expect(live[i]! - live[i - 1]!).toBeGreaterThanOrEqual(2);
      }
      const jpWrites = forms.obsWrites.filter((w) => JSON.stringify(w.v ?? '').includes('jp ')).map((w) => w.tick);
      for (let i = 1; i < jpWrites.length - 1; i++) expect(jpWrites[i]! - jpWrites[i - 1]!).toBeGreaterThanOrEqual(20);
    }
  });
});

describe('SlotPresenter: coordinator decisions (4) sneak-skip cinematic, (5) jackpots after the roll-up', () => {
  const def = fakeDef('end');

  const setup = (jackpotsFirst: boolean) => {
    // a BIG base win (tier from the server) + a Grand jackpot
    const round = fakeRound(def, fakeTape(def, [1, 2, 3, 4, 5], { totalFifths: 5 * 20, jackpots: [{ tier: 4, chips: 501220, owned: true }] }), 'BIG');
    const tl = stubSlotTimeline(round, SHARED_PROFILE, SHARED_PROFILE, 1, jackpotsFirst);
    const p = new FakePlayer();
    const titles: Array<{ tick: number; key: string; sub: string }> = [];
    p.onScreenDisplay.setTitle = ((title: unknown, o: { subtitle?: unknown }) =>
      void titles.push({ tick: clock.tick, key: JSON.stringify(title), sub: JSON.stringify(o?.subtitle ?? null) })) as never;
    const { h, presented } = host(round);
    const { v, shown, ctl } = view();
    const pr = new SlotPresenter(p as never, h as never, v as never);
    return { round, tl, p, titles, presented, shown, ctl, pr };
  };

  it('spec order: the Big title settles first, then the jackpot is the climax; both orders settle once on the terminal', () => {
    for (const first of [false, true]) {
      clock.runs.clear();
      const s = setup(first);
      s.pr.play({ round: s.round, timeline: s.tl });
      clock.advance(Math.ceil(s.tl.endMs() / 50) + 200);
      expect(s.presented).toEqual([{ interrupted: false }]);
      expect(JSON.stringify(s.shown[s.shown.length - 1])).toBe(JSON.stringify(terminalScreen(s.round)));
      const big = s.titles.findIndex((x) => x.key.includes('slots.tier.'));
      const jp = s.titles.findIndex((x) => x.key.includes('slots.jackpot.won'));
      expect(big).toBeGreaterThanOrEqual(0);
      expect(jp).toBeGreaterThanOrEqual(0);
      if (!first) {
        expect(big).toBeLessThan(jp);
        // the spin title printed its exact total before the jackpot took the screen
        const beforeJp = s.titles.slice(0, jp).filter((x) => x.key.includes('slots.tier.'));
        expect(beforeJp.length).toBeGreaterThan(0);
      }
      expect(clock.runs.size).toBe(0);
    }
  });

  it('sneaking during the Grand cinematic skips it after 1.5 s and prints the exact jackpot', () => {
    const base = setup(false);
    base.pr.play({ round: base.round, timeline: base.tl });
    clock.advance(Math.ceil(base.tl.endMs() / 50) + 200);
    const fullTicks = clock.tick;

    clock.tick = 0;
    clock.runs.clear();
    const s = setup(false);
    s.pr.play({ round: s.round, timeline: s.tl });
    const jpBeat = s.tl.beats.find((b) => b.kind === SLOT_BEAT.JACKPOT)!;
    let settledAt = -1;
    for (let i = 0; i < 2000 && s.presented.length === 0; i++) {
      clock.advance(1);
      if (clock.tick === Math.ceil(jpBeat.at / 50) + 3) {
        expect(s.ctl.suspended).toHaveBeenCalledWith(true);
        s.p.isSneaking = true;
      }
      if (s.presented.length) settledAt = clock.tick;
    }
    expect(s.presented).toEqual([{ interrupted: false }]);
    expect(settledAt).toBeLessThan(fullTicks);
    const last = s.titles.filter((x) => x.key.includes('slots.jackpot.won')).pop()!;
    expect(last.sub.replace(/"translate":"[^"]*"/g, '').replace(/\D/g, '')).toBe('501220');
    expect(JSON.stringify(s.shown[s.shown.length - 1])).toBe(JSON.stringify(terminalScreen(s.round)));
  });
});
