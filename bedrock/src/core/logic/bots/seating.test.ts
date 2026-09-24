import { describe, expect, it } from 'vitest';
import { dailyCap, pokerPotNet, pvpRakeSplit, vipCredit } from './economy';
import { sfc32 } from './rng';
import { drawName } from './roster';
import { wantedBots } from './seating';
import type { BotSettings } from './types';

const POKER: BotSettings = { policy: 'MIXED', count: 5, difficulty: 'MIXED', keepFree: true, chatter: true, speed: 'NORMAL' };

// BOTS.md §12.4 S1/S2, §12.5 (same vectors as Java SeatingMathTest)
describe('bot seating and economy', () => {
  it('MIXED keep-free', () => {
    expect(wantedBots(POKER, 6, 1, 0)).toBe(4);
    expect(wantedBots(POKER, 6, 2, 0)).toBe(3);
    expect(wantedBots(POKER, 6, 0, 0)).toBe(0);
    expect(wantedBots({ ...POKER, policy: 'BOTS_ONLY', count: 3 }, 6, 1, 0)).toBe(3);
    expect(wantedBots({ ...POKER, policy: 'HUMANS_ONLY' }, 6, 1, 0)).toBe(0);
  });
  it('economy vectors', () => {
    expect(pokerPotNet(290, 100, 100, 0, 300)).toBe(96);
    expect(vipCredit(400, 0.5, 0.75)).toBe(250);
    expect(dailyCap(1000, 500, 5)).toBe(5000);
    expect(pvpRakeSplit(6, 100, 200)).toEqual({ bank: 3, bankroll: 3 });
  });
  it('unique names per table', () => {
    const rng = sfc32(1, 2, 3, 4);
    const used = new Set<string>();
    for (let i = 0; i < 7; i++) {
      const id = drawName(rng, 'any', used);
      expect(used.has(id)).toBe(false);
      used.add(id);
    }
  });
});
