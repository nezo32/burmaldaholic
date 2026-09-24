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
const ui = vi.hoisted(() => ({ shown: [] as Array<{ title: unknown; body: unknown; buttons: unknown[] }>, answer: [] as Array<number | undefined> }));

vi.mock('@minecraft/server', () => {
  const events = new Proxy({}, { get: () => ({ subscribe: () => {} }) });
  return {
    world: { afterEvents: events, beforeEvents: events, getAllPlayers: () => [] },
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
      afterEvents: events,
      beforeEvents: events,
    },
    EasingType: { InOutSine: 'InOutSine' },
    MolangVariableMap: class {
      setFloat(): void {}
    },
    EntityComponentTypes: {},
    ItemComponentTypes: {},
    ItemStack: class {},
  };
});
vi.mock('@minecraft/server-ui', () => {
  class ActionFormData {
    rec = { title: undefined as unknown, body: undefined as unknown, buttons: [] as unknown[] };
    title(x: unknown): this {
      this.rec.title = x;
      return this;
    }
    body(x: unknown): this {
      this.rec.body = x;
      return this;
    }
    button(x: unknown): this {
      this.rec.buttons.push(x);
      return this;
    }
    show(): Promise<{ canceled: boolean; selection?: number }> {
      ui.shown.push(this.rec);
      const a = ui.answer.shift();
      return a === undefined ? new Promise(() => {}) : Promise.resolve({ canceled: false, selection: a });
    }
  }
  return { ActionFormData, ModalFormData: class {}, MessageFormData: class {}, FormCancelationReason: {}, uiManager: { closeAllForms: () => {} }, CustomForm: class {} };
});

const { ClassicSlotView, actionbarRaw, bannerKey, reopenDelay } = await import('./classic');
const { fakeDef, fakeRound, fakeTape, timelineOf } = await import('./fixtures.test-util');

class FakePlayer {
  isValid = true;
  isSneaking = false;
  location = { x: 0, y: 0, z: 0 };
  onScreenDisplay = { setTitle: () => {}, updateSubtitle: () => {}, setActionBar: () => {} };
  camera = { fade: () => {}, setCamera: () => {}, clear: () => {} };
  getDynamicProperty(): unknown {
    return undefined;
  }
  setDynamicProperty(): void {}
  playSound(): void {}
  playMusic(): void {}
  stopMusic(): void {}
  runCommand(): void {}
}

beforeEach(() => {
  clock.tick = 0;
  clock.runs.clear();
  ui.shown.length = 0;
  ui.answer.length = 0;
});

describe('classic fallback (BS3)', () => {
  it('re-open delays follow global.md §2.6; banners are recognised; the action bar holds rows + lines', () => {
    expect(reopenDelay('EPIC', false, false)).toBe(60);
    expect(reopenDelay('BIG', false, false)).toBe(40);
    expect(reopenDelay('MEGA', false, false)).toBe(50);
    expect(reopenDelay('WIN', false, false)).toBe(20);
    expect(reopenDelay('WIN', true, false)).toBe(60);
    expect(reopenDelay('EPIC', true, true)).toBe(1);
    expect(bannerKey({ rawtext: [{ text: '§6' }, { translate: 'gui.burmaldaholic.slots.fs.title' }] })).toBe('gui.burmaldaholic.slots.fs.title');
    expect(bannerKey({ translate: 'gui.burmaldaholic.slots.fs.left' })).toBeUndefined();
    const raw = actionbarRaw({ board: 'reels', rows: { text: 'R' }, header: { text: 'H' }, status: { text: 'S' } });
    expect(JSON.stringify(raw)).toContain('"R"');
    expect(JSON.stringify(raw)).toContain('"S"');
  });

  it('Spin closes the form, the action bar carries the reels every 2 t, the form re-opens after the result', async () => {
    const def = fakeDef('overworld');
    const round = fakeRound(def, fakeTape(def, [1, 5, 9, 13, 17]));
    const tl = timelineOf(round);
    const posts: unknown[] = [];
    const titles: unknown[] = [];
    const hud = { actionbar: (_p: unknown, _c: string, m: unknown) => posts.push(m), clear: () => {}, title: (_p: unknown, t: unknown) => titles.push(t) };
    const presented: boolean[] = [];
    const host = {
      title: { text: 'm' },
      jackpotLine: () => ({ text: 'jp' }),
      spinLabel: () => ({ text: 'spin' }),
      betLabel: () => ({ text: 'bet' }),
      spin: () => ({ round, timeline: tl }),
      presented: (_s: unknown, i: boolean) => presented.push(i),
      huntPick: () => undefined,
      betDown: () => {},
      betUp: () => {},
      auto: () => {},
      paytable: () => {},
      leave: () => {},
    };
    ui.answer.push(0); // press Spin
    const v = new ClassicSlotView(new FakePlayer() as never, host as never, hud as never);
    v.openMachine();
    await Promise.resolve();
    await Promise.resolve();
    expect(ui.shown.length).toBe(1);
    clock.advance(20);
    expect(posts.length).toBeGreaterThanOrEqual(9);
    clock.advance(Math.ceil(tl.endMs() / 50) + 5);
    expect(presented).toEqual([false]);
    clock.advance(70);
    expect(ui.shown.length).toBe(2); // machine form again, with the last window in the body
    expect(JSON.stringify(ui.shown[1]!.body)).toContain('jp');
  });
});
