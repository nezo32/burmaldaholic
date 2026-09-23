import { describe, expect, it } from 'vitest';
import { ACHIEVEMENTS, isAchievement, streakAchievements, unlockInList, wagerAchievements } from './achievements';
import { HOUSE_EDGE, houseEdgeOf, theoreticalLoss } from './house-edge';
import { OFFLINE_CAPS, emptyOffline, isEmptyOffline, normalizeOffline, planRecovery, withChips, withMessage, withResolved, withSettled } from './offline';

const ticket = (id: string, boot: number) => ({ id, boot, value: 100 });

describe('offline settlement (§4.1)', () => {
  it('a round settled offline is never refunded again after a restart (no double pay)', () => {
    // boot 1: player bets (ticket 1.5 persisted on the player), disconnects; table settles it offline.
    let entry = withResolved(withChips(emptyOffline(), 200), '1.5');
    // server restarts (boot 2); player joins: stored tickets still contain 1.5 (could not be rewritten).
    const stored = [ticket('1.5', 1), ticket('1.6', 1)];
    const plan = planRecovery(stored, entry.resolved, 2);
    expect(plan.dropped.map((w) => w.id)).toEqual(['1.5']);
    expect(plan.refund.map((w) => w.id)).toEqual(['1.6']);
    expect(plan.keep).toEqual([]);
    // what the player receives: the settlement (200) + the stale refund of 1.6 (100), once each.
    const credited = entry.chips + plan.refund.reduce((s, w) => s + w.value, 0);
    expect(credited).toBe(300);
    entry = withResolved(entry, '1.5');
    expect(entry.resolved).toEqual(['1.5']);
  });

  it('tickets still open in this run are kept, not refunded', () => {
    const plan = planRecovery([ticket('3.1', 3), ticket('2.9', 2)], [], 3);
    expect(plan.keep.map((w) => w.id)).toEqual(['3.1']);
    expect(plan.refund.map((w) => w.id)).toEqual(['2.9']);
  });

  it('refund-then-settle races: resolved is idempotent', () => {
    const e = withResolved(withResolved(emptyOffline(), 'a'), 'a');
    expect(e.resolved).toEqual(['a']);
  });

  it('entries accumulate chips and cap lists', () => {
    let e = emptyOffline();
    expect(isEmptyOffline(e)).toBe(true);
    for (let i = 0; i < 100; i++) {
      e = withChips(e, 10);
      e = withMessage(e, { text: String(i) });
      e = withSettled(e, { game: 'roulette', staked: 10, totalReturn: 0, stakeKind: 'chips', house: { kind: 'bank' }, theoreticalLoss: 0.27 });
    }
    expect(e.chips).toBe(1000);
    expect(e.messages).toHaveLength(OFFLINE_CAPS.messages);
    expect(e.settled).toHaveLength(OFFLINE_CAPS.settled);
    expect(withChips(e, -5).chips).toBe(1000);
  });

  it('normalizes corrupt storage', () => {
    expect(normalizeOffline(undefined)).toEqual(emptyOffline());
    expect(normalizeOffline('x')).toEqual(emptyOffline());
    const n = normalizeOffline({ chips: -3, xp: 7.9, items: [{ typeId: 'minecraft:diamond', amount: 2 }, { bad: 1 }], resolved: ['a', 5] });
    expect(n.chips).toBe(0);
    expect(n.xp).toBe(7);
    expect(n.items).toEqual([{ typeId: 'minecraft:diamond', amount: 2 }]);
    expect(n.resolved).toEqual(['a']);
  });
});

describe('house edge / theoretical loss', () => {
  it('every game has a non-negative edge below 100 %', () => {
    for (const [g, e] of Object.entries(HOUSE_EDGE)) {
      expect(e, g).toBeGreaterThanOrEqual(0);
      expect(e, g).toBeLessThan(1);
    }
    expect(houseEdgeOf('poker')).toBe(0);
    expect(houseEdgeOf('unknown')).toBe(0);
    expect(theoreticalLoss(1000, houseEdgeOf('roulette'))).toBeCloseTo(27, 6);
    expect(theoreticalLoss(-5, 0.1)).toBe(0);
  });
});

describe('achievements (§19)', () => {
  it('ids are unique and every parent exists earlier in the list', () => {
    const seen = new Set<string>();
    for (const a of ACHIEVEMENTS) {
      expect(seen.has(a.id), a.id).toBe(false);
      if (a.parent) expect(seen.has(a.parent), `${a.id} -> ${a.parent}`).toBe(true);
      seen.add(a.id);
    }
    expect(ACHIEVEMENTS).toHaveLength(35);
  });

  it('unlocks once, ignores unknown ids', () => {
    const a = unlockInList([], 'first_bet');
    expect(a).toEqual({ list: ['first_bet'], added: true });
    expect(unlockInList(a.list, 'first_bet').added).toBe(false);
    expect(unlockInList(a.list, 'nope').added).toBe(false);
    expect(isAchievement('vip_netherite')).toBe(true);
  });

  it('wager and streak conditions', () => {
    expect(wagerAchievements({ staked: 0, net: 5, stakeKind: 'chips', goldenHour: true })).toEqual([]);
    expect(wagerAchievements({ staked: 10, net: -10, stakeKind: 'chips', goldenHour: true })).toEqual(['first_bet']);
    expect(wagerAchievements({ staked: 10, net: 10, stakeKind: 'hearts', goldenHour: false })).toEqual(['first_bet', 'beginners_luck', 'heart_on_the_line']);
    expect(wagerAchievements({ staked: 10, net: 10, stakeKind: 'soul', goldenHour: true })).toEqual(['first_bet', 'beginners_luck', 'devils_deal', 'golden_hour']);
    expect(streakAchievements(10)).toEqual(['on_fire']);
    expect(streakAchievements(-10)).toEqual(['black_cat']);
    expect(streakAchievements(9)).toEqual([]);
  });
});
