import { describe, expect, it } from 'vitest';
import { cardId, newShoe, parseCard } from './cards';
import { HudQueue } from './hud-queue';
import { applyDelta, toScore } from './ledger';
import { resolveCasinoEnabled } from './mode';
import { OddsService } from './odds';
import { join, t } from './rawtext';
import { randInt, seededRng, shuffle, weightedPick } from './rng';

describe('rawtext', () => {
  it('t without args', () => expect(t('a.b')).toEqual({ translate: 'a.b' }));
  it('t nests raw args', () =>
    expect(t('a', t('b'), 3)).toEqual({ translate: 'a', with: { rawtext: [{ translate: 'b' }, { text: '3' }] } }));
  it('join', () => expect(join(t('x'), 1)).toEqual({ rawtext: [{ translate: 'x' }, { text: '1' }] }));
});

describe('mode', () => {
  it('defaults to enabled', () => expect(resolveCasinoEnabled(undefined, {})).toBe(true));
  it('pack setting applies', () => expect(resolveCasinoEnabled(undefined, { 'burmaldaholic:casino_mode': false })).toBe(false));
  it('world override wins', () => expect(resolveCasinoEnabled(true, { 'burmaldaholic:casino_mode': false })).toBe(true));
});

describe('ledger', () => {
  it('rejects overdraft', () => expect(applyDelta(5, -6)).toEqual({ ok: false, reason: 'insufficient' }));
  it('rejects fractions', () => expect(applyDelta(5, 0.5).ok).toBe(false));
  it('adds', () => expect(applyDelta(5, 10)).toEqual({ ok: true, balance: 15 }));
  it('clamps score', () => expect(toScore(1e12)).toBe(2_147_483_647));
});

describe('rng', () => {
  it('is deterministic', () => {
    const a = seededRng(42);
    const b = seededRng(42);
    expect([a.next(), a.next()]).toEqual([b.next(), b.next()]);
  });
  it('randInt in range', () => {
    const r = seededRng(1);
    for (let i = 0; i < 1000; i++) {
      const v = randInt(r, 1, 6);
      expect(v >= 1 && v <= 6).toBe(true);
    }
  });
  it('shuffle keeps elements', () => expect(shuffle(seededRng(3), [1, 2, 3, 4]).sort()).toEqual([1, 2, 3, 4]));
  it('weightedPick ignores zero weight', () => {
    const r = seededRng(9);
    for (let i = 0; i < 100; i++) expect(weightedPick(r, [['a', 0], ['b', 1]] as const)).toBe('b');
  });
});

describe('odds', () => {
  it('applies modifiers in order and clamps', () => {
    const o = new OddsService(0, 0.9);
    o.addModifier('double', (_q, p) => p * 2, 10);
    o.addModifier('plus', (_q, p) => p + 0.1, 20);
    expect(o.probability('p', 'slots', 0.3)).toBeCloseTo(0.7);
    expect(o.probability('p', 'slots', 0.5)).toBe(0.9);
  });
  it('tracks streaks (memory source)', () => {
    const o = new OddsService();
    o.recordResult('p', 'win');
    expect(o.recordResult('p', 'win')).toBe(2);
    expect(o.recordResult('p', 'loss')).toBe(-1);
    o.addModifier('hot', (q, p) => (q.streak <= -1 ? p + 0.2 : p));
    expect(o.probability('p', 'x', 0.1)).toBeCloseTo(0.3);
  });
});

describe('hud queue', () => {
  it('shows highest priority live entry', () => {
    const q = new HudQueue<string>();
    q.post('a', 'low', 1, 0, 100);
    q.post('b', 'high', 5, 0, 10);
    expect(q.current(5)?.message).toBe('high');
    expect(q.current(20)?.message).toBe('low');
    expect(q.current(200)).toBeUndefined();
  });
});

describe('cards', () => {
  it('builds a full shoe', () => {
    const shoe = newShoe(seededRng(1), 2);
    expect(shoe).toHaveLength(104);
    expect(new Set(shoe.map(cardId)).size).toBe(52);
  });
  it('round-trips ids', () => expect(cardId(parseCard('10H'))).toBe('10H'));
});
