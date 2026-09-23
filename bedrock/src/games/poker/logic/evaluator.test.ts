import { describe, expect, it } from 'vitest';
import { pcs } from './cards';
import { CAT, categoryOf, evaluate, handName, ranksOf } from './evaluator';

const ev = (s: string) => evaluate(pcs(s));
const cat = (s: string) => categoryOf(ev(s));

describe('evaluator: categories', () => {
  it.each([
    ['As Ks Qs Js Ts 2d 3c', 'royal_flush'],
    ['9h 8h 7h 6h 5h Ac Ad', 'straight_flush'],
    ['Ah 2h 3h 4h 5h Kd Kc', 'straight_flush'],
    ['7s 7h 7d 7c Kd 2c 3h', 'four_of_a_kind'],
    ['Qs Qh Qd 9c 9d 2c 3h', 'full_house'],
    ['2s 8s Js Ks 4s Ad Ac', 'flush'],
    ['Ts 9h 8d 7c 6s 2d 2c', 'straight'],
    ['As 2h 3d 4c 5s Kd Qc', 'straight'],
    ['8s 8h 8d Kc 2s 4d 6c', 'three_of_a_kind'],
    ['Js Jh 4d 4c As 7d 2c', 'two_pair'],
    ['Ts Th 4d 8c As 7d 2c', 'pair'],
    ['As Jh 8d 6c 4s 3d 2c', 'high_card'],
  ])('%s -> %s', (cards, name) => expect(handName(ev(cards))).toBe(name));

  it('category order is strictly increasing', () => {
    const order = [
      'As Jh 8d 6c 4s',
      'Ts Th 4d 8c As',
      'Js Jh 4d 4c As',
      '8s 8h 8d Kc 2s',
      'Ts 9h 8d 7c 6s',
      '2s 8s Js Ks 4s',
      'Qs Qh Qd 9c 9d',
      '7s 7h 7d 7c Kd',
      '9h 8h 7h 6h 5h',
      'As Ks Qs Js Ts',
    ].map(ev);
    for (let i = 1; i < order.length; i++) expect(order[i]).toBeGreaterThan(order[i - 1]!);
  });

  it('packs category << 20 | 5 ranks × 4 bits', () => {
    const v = ev('Ks Kh 9d 9c 4s');
    expect(v).toBe((CAT.two_pair << 20) | (13 << 16) | (13 << 12) | (9 << 8) | (9 << 4) | 4);
    expect(ranksOf(v)).toEqual([13, 13, 9, 9, 4]);
  });

  it('wheel is the lowest straight, ace packed low', () => {
    const wheel = ev('As 2h 3d 4c 5s');
    expect(ranksOf(wheel)).toEqual([5, 4, 3, 2, 1]);
    expect(wheel).toBeLessThan(ev('2s 3h 4d 5c 6s'));
    expect(ev('Ts Jh Qd Kc As')).toBeGreaterThan(ev('9s Th Jd Qc Ks'));
  });

  it('steel wheel is the lowest straight flush', () => {
    expect(ev('Ad 2d 3d 4d 5d')).toBeLessThan(ev('2c 3c 4c 5c 6c'));
    expect(handName(ev('Ad 2d 3d 4d 5d'))).toBe('straight_flush');
  });

  it('no wrap-around straights', () => expect(cat('Qs Kh Ad 2c 3s')).toBe(CAT.high_card));

  it('rejects wrong card counts', () => {
    expect(() => evaluate(pcs('As Ks Qs Js'))).toThrow();
    expect(() => evaluate(pcs('As Ks Qs Js Ts 9s 8s 7s'))).toThrow();
  });
});

describe('evaluator: best 5 of 7', () => {
  it('prefers the straight flush over a higher flush', () => expect(cat('5s 6s 7s 8s 9s As Ks')).toBe(CAT.straight_flush));
  it('flush beats straight', () => expect(cat('2h 6h 7h 8h 9h Td Js')).toBe(CAT.flush));
  it('two trips make a full house with the higher trips', () => {
    const v = ev('9s 9h 9d 4c 4s 4d Ac');
    expect(categoryOf(v)).toBe(CAT.full_house);
    expect(ranksOf(v)).toEqual([9, 9, 9, 4, 4]);
  });
  it('full house takes the best pair', () => expect(ranksOf(ev('5s 5h 5d Kc Ks 2d 2c'))).toEqual([5, 5, 5, 13, 13]));
  it('three pairs: best two + best kicker (counterfeit)', () => expect(ranksOf(ev('Ks Kh 7d 7c 3s 3d Qc'))).toEqual([13, 13, 7, 7, 12]));
  it('quads kicker can be a paired board card', () => expect(ranksOf(ev('8s 8h 8d 8c Js Jd 2c'))).toEqual([8, 8, 8, 8, 11]));
  it('flush uses the five highest suited cards', () => expect(ranksOf(ev('Ah 3h 7h 9h Jh Qh 2c'))).toEqual([14, 12, 11, 9, 7]));
  it('six-card straight uses the top', () => expect(ranksOf(ev('4s 5h 6d 7c 8s 9d 2c'))).toEqual([9, 8, 7, 6, 5]));
  it('ace-high straight with a wheel present', () => expect(ranksOf(ev('As 2h 3d 4c 5s 6d Kc'))).toEqual([6, 5, 4, 3, 2]));
});

describe('evaluator: kickers and ties', () => {
  it('pair kickers decide', () => {
    expect(ev('As Ah Kd 9c 5s 3d 2c')).toBeGreaterThan(ev('Ad Ac Qd Jc 9s 3h 2d'));
    expect(ev('As Ah Kd 9c 6s 3d 2c')).toBeGreaterThan(ev('Ad Ac Kh 9d 5s 3h 2d'));
  });
  it('fifth kicker only counts for high card / pair lengths', () => {
    // two pair: only one kicker plays
    expect(ev('Ks Kh 7d 7c Qs 3d 2c')).toBe(ev('Kd Kc 7s 7h Qd 4d 2d'));
  });
  it('high card compares all five', () => expect(ev('As Kh 9d 7c 4s')).toBeGreaterThan(ev('Ad Kc 9h 7s 3d')));
  it('trips kickers', () => expect(ev('8s 8h 8d Ac 2s')).toBeGreaterThan(ev('8c 8h 8d Kc Qs')));
  it('full house compares trips first', () => expect(ev('3s 3h 3d 2c 2s')).toBeGreaterThan(ev('2d 2h 2c As Ad')));
  it('suits never break ties', () => expect(ev('As Kh Qd Jc 9s')).toBe(ev('Ah Ks Qc Jd 9h')));
  it('board plays: identical best five = tie', () => {
    const board = 'Ts Js Qd Kc Ah';
    expect(ev(`2c 3d ${board}`)).toBe(ev(`4h 5h ${board}`));
  });
  it('straight vs straight by top card, kickers irrelevant', () => expect(ev('Ts 9h 8d 7c 6s Ad Kc')).toBe(ev('Th 9d 8s 7h 6d 2c 3c')));
  it('flush vs flush by ranks in order', () => expect(ev('Ks Js 8s 6s 2s')).toBeGreaterThan(ev('Kh Jh 8h 5h 4h')));
});

describe('evaluator: exhaustive 5-card distribution', () => {
  it('matches the known counts over all 2 598 960 hands', () => {
    const counts = new Array<number>(10).fill(0);
    const h = [0, 0, 0, 0, 0];
    for (let a = 0; a < 52; a++)
      for (let b = a + 1; b < 52; b++)
        for (let c = b + 1; c < 52; c++)
          for (let d = c + 1; d < 52; d++)
            for (let e = d + 1; e < 52; e++) {
              h[0] = a;
              h[1] = b;
              h[2] = c;
              h[3] = d;
              h[4] = e;
              const v = evaluate(h);
              const k = categoryOf(v);
              counts[k === CAT.straight_flush && ranksOf(v)[0] === 14 ? 9 : k]!++;
            }
    expect(counts).toEqual([1302540, 1098240, 123552, 54912, 10200, 5108, 3744, 624, 36, 4]);
  }, 60_000);
});
