import type { Player } from '@minecraft/server';
import { describe, expect, it } from 'vitest';
import type { Raw } from '../../../../core/logic/rawtext';
import { type CelebrateRuntime, celebrateSpin, cinematicCamera, cinematicPlan, jackpotCinematic, slotCelebration, tierWordAt, titleWinRollUp } from './celebrate';

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
