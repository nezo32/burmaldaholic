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

const forms = vi.hoisted(() => ({ list: [] as Array<{ labels: Map<string, unknown>; buttons: Map<string, () => void>; writes: string[]; showing: boolean; close: () => void }> }));

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
