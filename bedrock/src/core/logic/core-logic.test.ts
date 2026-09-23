import { describe, expect, it } from 'vitest';
import { cardId, newShoe, parseCard } from './cards';
import { HudQueue } from './hud-queue';
import { applyDelta, toScore } from './ledger';
import { modeFlip, resolveCasinoEnabled } from './mode';
import { OddsService } from './odds';
import { decimal, join, lit, t } from './rawtext';
import { randInt, seededRng, shuffle, weightedPick } from './rng';
import { advanceDormancy, rebasePenalties } from './wager-math';

describe('rawtext', () => {
  it('t without args', () => expect(t('a.b')).toEqual({ translate: 'a.b' }));
  it('t nests raw args', () =>
    expect(t('a', t('b'), 3)).toEqual({ translate: 'a', with: { rawtext: [{ translate: 'b' }, { text: '3' }] } }));
  it('join', () => expect(join(t('x'), 1)).toEqual({ rawtext: [{ translate: 'x' }, { text: '1' }] }));
});

describe('mode', () => {
  it('flip detection (m6: players online when the mode turns on get the first-join grant)', () => {
    expect(modeFlip(false, true)).toBe('on');
    expect(modeFlip(true, false)).toBe('off');
    expect(modeFlip(true, true)).toBeUndefined();
    expect(modeFlip(false, false)).toBeUndefined();
  });
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

describe('decimal (review m7: RU "1,5", EN "1.5")', () => {
  it('translates the separator client-side', () => {
    expect(decimal(1.5)).toEqual(join(lit('1'), t('unit.burmaldaholic.decimal_separator'), lit('5')));
    expect(decimal(0.5)).toEqual(join(lit('0'), t('unit.burmaldaholic.decimal_separator'), lit('5')));
  });
  it('whole numbers stay plain', () => {
    expect(decimal(2)).toEqual(lit('2'));
    expect(decimal(2.04)).toEqual(lit('2'));
  });
});

describe('heart penalties pause while casino mode is off (review M2)', () => {
  it('dormancy counts only gaps above the threshold', () => {
    expect(advanceDormancy({ total: 0 }, 1000)).toEqual({ last: 1000, total: 0 });
    expect(advanceDormancy({ last: 1000, total: 0 }, 1010)).toEqual({ last: 1010, total: 0 });
    expect(advanceDormancy({ last: 1000, total: 50 }, 6000)).toEqual({ last: 6000, total: 5050 });
  });
  it('expiry moves by the dormant time since the last rebase', () => {
    const r = rebasePenalties([{ hearts: 2, until: 5000 }, { hearts: 1, until: 9000, d: 300 }], 1000);
    expect(r.changed).toBe(true);
    expect(r.list).toEqual([
      { hearts: 2, until: 6000, d: 1000 },
      { hearts: 1, until: 9700, d: 1000 },
    ]);
    expect(rebasePenalties(r.list, 1000).changed).toBe(false);
  });
});
