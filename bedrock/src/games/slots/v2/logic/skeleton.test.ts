import { describe, expect, it } from 'vitest';
import { baseStopTimes } from './anticipation';
import { incrementAfter, jackpotAward } from './jackpots';
import { showdownPoints } from './showdown';
import { slotTier } from './tiers';

describe('slots v2 skeleton (mirrors Java SlotsV2SkeletonTest)', () => {
  it('jackpot award is proportional to the bet', () => {
    expect(jackpotAward(1000, 234, 100, 100)).toBe(1234);
    expect(jackpotAward(1000, 234, 50, 100)).toBe(617);
    expect(incrementAfter(234, 50, 100)).toBe(117);
    expect(incrementAfter(234, 500, 100)).toBe(0);
  });
  it('tiers and points', () => {
    expect(slotTier(3)).toBe('RETURN');
    expect(slotTier(200)).toBe('MEGA');
    expect(showdownPoints(5)).toBe(10);
    expect(baseStopTimes()).toEqual([600, 750, 900, 1050, 1200]);
  });
});
