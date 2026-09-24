import type { Player } from '@minecraft/server';
import { describe, expect, it } from 'vitest';
import type { Raw } from '../../../../core/logic/rawtext';
import { CINEMATIC_MIN_SKIP_TICKS, CINEMATIC_SKIP_POLL_TICKS, type CelebrateRuntime, celebrateSpin, cinematicCamera, cinematicPlan, escalationProgress, jackpotCinematic, rollupPoint, slotCelebration, tierWordAt, titleWinRollUp } from './celebrate';

const P = { id: 'p' } as unknown as Player;

/** Fake runtime with a tick clock; records every call with its tick. */
function fakeRuntime(opts: { cameraOk?: boolean } = {}) {
  let now = 0;
  const queue: Array<{ at: number; fn: () => void }> = [];
  const log: Array<{ tick: number; what: string; arg?: unknown }> = [];
  const rt: CelebrateRuntime = {
    after: (ticks, fn) => queue.push({ at: now + Math.max(1, ticks), fn }),
    setTitle: (_p, title, subtitle) => log.push({ tick: now, what: 'title', arg: { title, subtitle } }),
    updateSubtitle: (_p, s) => log.push({ tick: now, what: 'sub', arg: s }),
    fadeGold: () => (log.push({ tick: now, what: 'fade' }), true),
    shake: () => (log.push({ tick: now, what: 'shake' }), true),
    particle: (id, n) => log.push({ tick: now, what: 'particle', arg: [id, n] }),
    closeForms: () => log.push({ tick: now, what: 'close' }),
    setCamera: () => (log.push({ tick: now, what: 'camera' }), opts.cameraOk ?? true),
    clearCamera: () => log.push({ tick: now, what: 'clear' }),
    isValid: () => true,
  };
  const run = (ticks: number): void => {
    for (let i = 0; i < ticks; i++) {
      now++;
      for (const q of queue.filter((x) => x.at === now)) q.fn();
    }
  };
  return { rt, log, run, tick: () => now };
}

const keyOf = (r: unknown): string | undefined => {
  const walk = (x: Raw): string | undefined => x.translate ?? (x.rawtext ?? []).map(walk).find(Boolean);
  return walk(r as Raw);
};
const digits = (r: unknown): number => Number(JSON.stringify(r).replace(/"translate":"[^"]*"/g, '').replace(/§./g, '').replace(/\D/g, '') || '0');

describe('slot celebration model', () => {
  it('starts at Nice and upgrades at 15× / 40× / 100×, never past the server tier', () => {
    const c = slotCelebration(6500, 50, 'EPIC', false);
    expect(c.startTier).toBe('NICE');
    expect(c.upgrades).toEqual([
      ['BIG', 750],
      ['MEGA', 2000],
      ['EPIC', 5000],
    ]);
    expect([0, 749, 750, 1999, 2000, 4999, 5000, 6500].map((v) => tierWordAt(c, v))).toEqual(['NICE', 'NICE', 'BIG', 'BIG', 'MEGA', 'MEGA', 'EPIC', 'EPIC']);
    const big = slotCelebration(1000, 50, 'BIG', false);
    expect(big.upgrades.map((u) => u[0])).toEqual(['BIG']);
    expect(tierWordAt(big, 1000)).toBe('BIG');
    expect(slotCelebration(300, 50, 'NICE', false).overlay).toBe(false);
    expect(slotCelebration(100, 50, 'WIN', false).startTier).toBe('WIN');
  });

  it('title roll-up: monotonic subtitle, exact last value, words upgrade in order', () => {
    const { rt, log, run } = fakeRuntime();
    const c = slotCelebration(6500, 50, 'EPIC', false);
    titleWinRollUp(P, c, rt, { reduceMotion: false, celebrations: 'all' });
    run(400);
    const words = log.filter((l) => l.what === 'title').map((l) => keyOf((l.arg as { title: Raw }).title));
    expect(words).toEqual(['nice', 'big', 'mega', 'epic'].map((w) => `gui.burmaldaholic.slots.tier.${w}`));
    const values = log.filter((l) => l.what === 'sub' || l.what === 'title').map((l) => digits(l.what === 'sub' ? l.arg : (l.arg as { subtitle: Raw }).subtitle));
    for (let i = 1; i < values.length; i++) expect(values[i]!).toBeGreaterThanOrEqual(values[i - 1]!);
    expect(values[values.length - 1]).toBe(6500);
  });

  it('reduce motion: two steps; cancel jumps to the exact final word and amount', () => {
    const a = fakeRuntime();
    titleWinRollUp(P, slotCelebration(6500, 50, 'EPIC', false), a.rt, { reduceMotion: true, celebrations: 'all' });
    a.run(400);
    expect(a.log.filter((l) => l.what === 'sub' || l.what === 'title').length).toBeLessThanOrEqual(4);
    const b = fakeRuntime();
    const cancel = titleWinRollUp(P, slotCelebration(6500, 50, 'EPIC', false), b.rt, { reduceMotion: false, celebrations: 'all' });
    b.run(4);
    cancel();
    const last = b.log[b.log.length - 1]!;
    expect(last.what).toBe('title');
    expect(keyOf((last.arg as { title: Raw }).title)).toBe('gui.burmaldaholic.slots.tier.epic');
    expect(digits((last.arg as { subtitle: Raw }).subtitle)).toBe(6500);
  });

  it('Mega flash and Epic shake happen once; FxService takes over when installed; celebrations off = no title', () => {
    const a = fakeRuntime();
    celebrateSpin(P, slotCelebration(6500, 50, 'EPIC', false), a.rt, { reduceMotion: false, celebrations: 'all' });
    a.run(400);
    expect(a.log.filter((l) => l.what === 'fade').length).toBe(1);
    expect(a.log.filter((l) => l.what === 'shake').length).toBe(1);
    const calls: unknown[] = [];
    const b = fakeRuntime();
    celebrateSpin(P, slotCelebration(1000, 50, 'BIG', false), b.rt, { reduceMotion: false, celebrations: 'all', fx: { celebrate: (_p, r) => calls.push(r) } });
    b.run(100);
    expect(calls.length).toBe(1);
    expect((calls[0] as { words: { BIG: string } }).words.BIG).toBe('gui.burmaldaholic.slots.tier.big');
    expect(b.log.some((l) => l.what === 'title')).toBe(false);
    const c = fakeRuntime();
    celebrateSpin(P, slotCelebration(1000, 50, 'BIG', false), c.rt, { reduceMotion: false, celebrations: 'off' });
    c.run(100);
    expect(c.log.some((l) => l.what === 'title')).toBe(false);
    expect(c.log.some((l) => l.what === 'particle')).toBe(true);
  });
});

describe('jackpot cinematic (Major / Grand)', () => {
  it('follows the slots.md §6.2 tick table and re-opens the form', () => {
    for (const tier of [3, 4]) {
      const plan = cinematicPlan(tier)!;
      const { rt, log, run } = fakeRuntime();
      let reopened = -1;
      let now = 0;
      jackpotCinematic(P, { tier, chips: 75000, face: { x: 0, y: 64, z: 0 }, facing: { x: 0, z: 1 } }, rt, { reduceMotion: false, celebrations: 'all' }, () => (reopened = now));
      for (let i = 0; i < 100; i++) {
        now++;
        run(1);
      }
      const at = (w: string) => log.find((l) => l.what === w)?.tick;
      expect(at('close')).toBe(0);
      expect(at('camera')).toBe(plan.camera);
      expect(at('title')).toBe(plan.title);
      expect(at('particle')).toBe(plan.burst);
      expect(at('clear')).toBe(plan.clear);
      expect(reopened).toBe(plan.reopen);
      expect(log.some((l) => l.what === 'shake')).toBe(tier === 4);
      const subs = log.filter((l) => l.what === 'sub').map((l) => digits(l.arg));
      expect(subs[subs.length - 1]).toBe(75000);
    }
    expect(cinematicPlan(1)).toBeUndefined();
    expect(cinematicPlan(2)).toBeUndefined();
  });

  it('reduce motion: titles only (no camera, fade or shake), then re-open', () => {
    const { rt, log, run } = fakeRuntime();
    let reopened = false;
    jackpotCinematic(P, { tier: 4, chips: 5000, face: { x: 0, y: 64, z: 0 }, facing: { x: 1, z: 0 } }, rt, { reduceMotion: true, celebrations: 'all' }, () => (reopened = true));
    run(100);
    expect(log.some((l) => ['camera', 'fade', 'shake'].includes(l.what))).toBe(false);
    expect(log.some((l) => l.what === 'title')).toBe(true);
    expect(reopened).toBe(true);
  });

  it('camera sits 2.2 blocks out and 1.4 up, looking at the reel face', () => {
    const c = cinematicCamera({ x: 10, y: 64, z: 5 }, { x: 0, z: -1 });
    expect(c.location).toEqual({ x: 10, y: 65.4, z: 5 - 2.2 });
    expect(c.facingLocation.x).toBe(10);
  });
});

// ---------------------------------------------------------------------------------------------------------
// Coordinator decisions (2) title follows turbo / speed, (1) continuity, (4) sneak-skippable cinematic
// ---------------------------------------------------------------------------------------------------------

describe('review: title roll-up follows the played beat and the Win line', () => {
  const lastValueTick = (log: Array<{ tick: number; what: string; arg?: unknown }>, total: number): number =>
    log.filter((l) => (l.what === 'sub' ? digits(l.arg) : l.what === 'title' ? digits((l.arg as { subtitle: Raw }).subtitle) : -1) === total)[0]!.tick;

  it('the title reaches the exact total when the ROLLUP beat ends (turbo 0.5×, speed 1.5×)', () => {
    const c = slotCelebration(6500, 50, 'EPIC', false);
    for (const k of [1, 0.5, 2 / 3]) {
      const { rt, log, run } = fakeRuntime();
      const ms = Math.round(c.rollupMs * k);
      titleWinRollUp(P, c, rt, { reduceMotion: false, celebrations: 'all', rollupMs: ms });
      run(400);
      expect(Math.abs(lastValueTick(log, 6500) - Math.ceil(ms / 50))).toBeLessThanOrEqual(2);
    }
  });

  it('the Mega flash / Epic shake move with the played duration', () => {
    const c = slotCelebration(6500, 50, 'EPIC', false);
    const at = (ms?: number) => {
      const { rt, log, run } = fakeRuntime();
      celebrateSpin(P, c, rt, { reduceMotion: false, celebrations: 'all', rollupMs: ms });
      run(400);
      return [log.find((l) => l.what === 'fade')!.tick, log.find((l) => l.what === 'shake')!.tick];
    };
    const full = at();
    const half = at(Math.round(c.rollupMs / 2));
    expect(half[0]!).toBeLessThan(full[0]!);
    expect(half[1]!).toBeLessThan(full[1]!);
  });

  it('continuity: the title starts at the Win line; from ≥ total escalates the word without recounting', () => {
    const c = slotCelebration(6500, 50, 'EPIC', false);
    const a = fakeRuntime();
    titleWinRollUp(P, c, a.rt, { reduceMotion: false, celebrations: 'all', from: 1000 });
    a.run(400);
    const first = a.log.find((l) => l.what === 'title')!;
    expect(digits((first.arg as { subtitle: Raw }).subtitle)).toBe(1000);
    expect(keyOf((first.arg as { title: Raw }).title)).toBe('gui.burmaldaholic.slots.tier.big');
    const b = fakeRuntime();
    titleWinRollUp(P, c, b.rt, { reduceMotion: false, celebrations: 'all', from: 6500 });
    b.run(400);
    const words = b.log.filter((l) => l.what === 'title').map((l) => keyOf((l.arg as { title: Raw }).title));
    expect(words).toEqual(['nice', 'big', 'mega', 'epic'].map((w) => `gui.burmaldaholic.slots.tier.${w}`));
    const values = b.log.filter((l) => l.what === 'sub' || l.what === 'title').map((l) => digits(l.what === 'sub' ? l.arg : (l.arg as { subtitle: Raw }).subtitle));
    expect(values.every((v) => v === 6500)).toBe(true);
    for (let p = 0; p <= 1; p += 0.05) {
      const x = rollupPoint(c, 2000, p);
      expect(x.shown).toBeGreaterThanOrEqual(2000);
      expect(x.shown).toBeLessThanOrEqual(6500);
    }
    expect(rollupPoint(c, 2000, 1).shown).toBe(6500);
    expect(escalationProgress(c, 2000, 750)).toBe(0);
  });
});

describe('review: Major / Grand cinematic sneak-to-skip', () => {
  const run = (reduceMotion: boolean, sneakFrom: number) => {
    const f = fakeRuntime();
    let now = 0;
    const rt = { ...f.rt, wantsSkip: () => now >= sneakFrom };
    let reopened = -1;
    jackpotCinematic(P, { tier: 4, chips: 501220, face: { x: 0, y: 64, z: 0 }, facing: { x: 0, z: 1 } }, rt, { reduceMotion, celebrations: 'all' }, () => {
      if (reopened < 0) reopened = now;
    });
    for (let i = 0; i < 120; i++) {
      now++;
      f.run(1);
    }
    return { ...f, reopened };
  };

  it('not before 1.5 s; then the camera clears, the title prints the exact amount and the form reopens once', () => {
    const r = run(false, 0);
    expect(r.reopened).toBeGreaterThanOrEqual(CINEMATIC_MIN_SKIP_TICKS);
    expect(r.reopened).toBeLessThanOrEqual(CINEMATIC_MIN_SKIP_TICKS + CINEMATIC_SKIP_POLL_TICKS);
    expect(r.reopened).toBeLessThan(cinematicPlan(4)!.reopen);
    const clear = r.log.filter((l) => l.what === 'clear');
    expect(clear.length).toBe(1);
    expect(clear[0]!.tick).toBe(r.reopened);
    const lastTitle = r.log.filter((l) => l.what === 'title').pop()!;
    expect(lastTitle.tick).toBe(r.reopened);
    expect(keyOf((lastTitle.arg as { title: Raw }).title)).toBe('gui.burmaldaholic.slots.jackpot.won');
    expect(digits((lastTitle.arg as { subtitle: Raw }).subtitle)).toBe(501220);
    // nothing of the cinematic runs after the skip
    expect(r.log.filter((l) => l.tick > r.reopened).length).toBe(0);
  });

  it('reduce motion: skippable at once; no sneak: runs the full table', () => {
    expect(run(true, 0).reopened).toBeLessThanOrEqual(2);
    expect(run(false, 1000).reopened).toBe(cinematicPlan(4)!.reopen);
  });
});
