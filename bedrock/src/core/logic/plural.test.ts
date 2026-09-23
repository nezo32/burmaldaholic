import { describe, expect, it } from 'vitest';
import { pluralKey, pluralSuffix } from './plural';
import { plural } from './rawtext';

describe('pluralSuffix (EN+RU partition)', () => {
  const cases: [number, string][] = [
    [1, 'p1'],
    [2, 'p2'],
    [5, 'p5'],
    [11, 'p5'],
    [21, 'p21'],
    [111, 'p5'],
    [0, 'p5'],
    [3, 'p2'],
    [4, 'p2'],
    [12, 'p5'],
    [14, 'p5'],
    [22, 'p2'],
    [101, 'p21'],
    [112, 'p5'],
    [1001, 'p21'],
    [-1, 'p1'],
    [-21, 'p21'],
  ];
  it.each(cases)('%i -> %s', (n, s) => expect(pluralSuffix(n)).toBe(s));

  it('builds keys', () => expect(pluralKey('msg.burmaldaholic.core.chips', 21)).toBe('msg.burmaldaholic.core.chips.p21'));
});

describe('plural rawtext', () => {
  it('passes n as %1$s and extras after it', () => {
    expect(plural('msg.burmaldaholic.core.chips', 5, 'Bob')).toEqual({
      translate: 'msg.burmaldaholic.core.chips.p5',
      with: { rawtext: [{ text: '5' }, { text: 'Bob' }] },
    });
  });
});
