import { describe, expect, it } from 'vitest';
import { rake, sliceOwner, split } from './math';

// PVP.md §16.1 C1–C4, §16.4 W1 (same vectors as Java PvpMathTest)
describe('pvp math', () => {
  it('rake C1/C2', () => {
    expect([16, 20, 34, 50, 200, 400, 999, 1000, 10000].map((p) => rake(p, 300))).toEqual([0, 1, 1, 2, 6, 12, 30, 30, 300]);
    expect(rake(12345, 0)).toBe(0);
    expect(rake(30, 500)).toBe(2);
    expect(rake(25, 1000)).toBe(3);
  });
  it('split C3/C4', () => {
    expect(split(97, [0, 1], [1, 0], 2)).toEqual([48, 49]);
    expect(split(100, [0, 1, 2], [2, 0, 1], 3)).toEqual([33, 33, 34]);
  });
  it('wheel W1', () => {
    expect([0, 49, 50, 199, 200, 999].map((u) => sliceOwner([50, 150, 800], u))).toEqual([0, 0, 1, 1, 2, 2]);
  });
});
