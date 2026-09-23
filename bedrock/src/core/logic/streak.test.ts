import { describe, expect, it } from 'vitest';
import { OddsService, memoryStreaks } from './odds';
import { seededRng } from './rng';
import { DEFAULT_STREAK, GAME_RTP, decay, drawWithReroll, outcomeOfNet, record, rerollCap, rerollChance, streakMessageKey } from './streak';

const cfg = DEFAULT_STREAK;

describe('streak update (§14)', () => {
  it('wins/losses/push', () => {
    let s = { s: 0, t: 0 };
    s = record(s, 'win', 10, cfg);
    s = record(s, 'win', 20, cfg);
    expect(s.s).toBe(2);
    s = record(s, 'loss', 30, cfg);
    expect(s.s).toBe(-1);
    expect(record(s, 'push', 40, cfg).s).toBe(-1);
  });
  it('caps at ±max', () => {
    let s = { s: 9, t: 0 };
    s = record(s, 'win', 1, cfg);
    s = record(s, 'win', 2, cfg);
    expect(s.s).toBe(10);
    expect(record({ s: -10, t: 0 }, 'loss', 1, cfg).s).toBe(-10);
  });
  it('decays one step per decayTicks toward 0', () => {
    expect(decay({ s: 4, t: 0 }, 12000 * 2 + 5, cfg)).toEqual({ s: 2, t: 24000 });
    expect(decay({ s: -1, t: 0 }, 100000, cfg).s).toBe(0);
    expect(decay({ s: 3, t: 0 }, 11999, cfg).s).toBe(3);
    expect(decay({ s: 3, t: 0 }, 10 ** 9, { decayTicks: 0 }).s).toBe(3);
  });
  it('decay applies before recording', () => expect(record({ s: 3, t: 0 }, 'win', 36000, cfg).s).toBe(1));
  it('outcome from net', () => expect([outcomeOfNet(5), outcomeOfNet(-1), outcomeOfNet(0)]).toEqual(['win', 'loss', 'push']));
});

describe('re-draw chance', () => {
  it('matches the spec examples', () => {
    expect(rerollCap(GAME_RTP.coin_flip, 0.01)).toBeCloseTo(0.0102, 4);
    expect(rerollCap(GAME_RTP.slots_copper, 0.01)).toBeCloseTo(0.103, 3);
    expect(rerollChance(10, GAME_RTP.slots_copper, cfg)).toBeCloseTo(0.05);
    expect(rerollChance(10, GAME_RTP.coin_flip, cfg)).toBeCloseTo(0.0102, 4);
    expect(rerollChance(-10, GAME_RTP.slots_copper, cfg)).toBeCloseTo(0.03);
    expect(rerollChance(0, 0.9, cfg)).toBe(0);
    expect(rerollChance(5, 0.9, { ...cfg, enabled: false })).toBe(0);
  });
  it('house edge never below minHouseEdge', () => {
    for (const rtp of Object.values(GAME_RTP)) {
      const r = rerollChance(10, rtp, cfg);
      expect(rtp * (1 + r)).toBeLessThanOrEqual(1 - cfg.minHouseEdge + 1e-12);
    }
  });
  it('minHouseEdge is floored at 0.005', () => expect(rerollCap(0.99, 0)).toBeCloseTo(0.995 / 0.99 - 1));
  it('re-draws only losing outcomes', () => {
    let n = 0;
    const rng = seededRng(1);
    const r = drawWithReroll(rng, 1, () => ++n, (x) => x === 1);
    expect(r).toEqual({ result: 2, rerolled: true });
    expect(drawWithReroll(rng, 1, () => 5, () => false).rerolled).toBe(false);
  });
  it('odds service draw uses the streak', () => {
    const src = memoryStreaks();
    const o = new OddsService(0, 0.95, src);
    for (let i = 0; i < 10; i++) o.recordResult('p', 'win');
    expect(o.streakOf('p')).toBe(10);
    expect(o.rerollChance('p', GAME_RTP.slots_copper)).toBeCloseTo(0.05);
  });
  it('monte-carlo: lucky coin flip RTP stays ≤ 99 %', () => {
    const rng = seededRng(7);
    const r = rerollChance(10, GAME_RTP.coin_flip, cfg);
    let ret = 0;
    const N = 200_000;
    for (let i = 0; i < N; i++) ret += drawWithReroll(rng, r, () => (rng.next() < 0.5 ? 1.96 : 0), (x) => x < 1).result;
    expect(ret / N).toBeLessThan(0.995);
    expect(ret / N).toBeGreaterThan(0.975);
  });
});

describe('streak messages', () => {
  it('announces milestones and breaks', () => {
    expect(streakMessageKey(4, 5)).toBe('msg.burmaldaholic.streak.lucky_5');
    expect(streakMessageKey(-9, -10)).toBe('msg.burmaldaholic.streak.unlucky_10');
    expect(streakMessageKey(6, -1)).toBe('msg.burmaldaholic.streak.broken_lucky');
    expect(streakMessageKey(-5, 1)).toBe('msg.burmaldaholic.streak.broken_unlucky');
    expect(streakMessageKey(2, 3)).toBeUndefined();
  });
});
