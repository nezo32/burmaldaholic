import { describe, expect, it } from 'vitest';
import {
  applyCredit,
  breakdown,
  buyChips,
  chipValueOf,
  detectTrade,
  difficultyMultiplier,
  fitsUnderCap,
  isChipAmount,
  sellCount,
  mobReward,
  mobRewardKey,
  oreInfo,
  orePays,
  pickaxeTier,
  recordKill,
  sellChips,
  tradeReward,
  withdrawable,
} from './economy-math';
import { applyDelta, bankrollAvailable, planTransaction, settleBankroll, tryReserve } from './ledger';

describe('chips', () => {
  it('values chip items', () => {
    expect(chipValueOf('burmaldaholic:chip_500')).toBe(500);
    expect(chipValueOf('burmaldaholic:chip_7')).toBe(0);
    expect(chipValueOf('minecraft:emerald')).toBe(0);
  });
  it('greedy breakdown', () => expect(breakdown(631)).toEqual([{ value: 500, count: 1 }, { value: 100, count: 1 }, { value: 25, count: 1 }, { value: 5, count: 1 }, { value: 1, count: 1 }]));
  it('preferred denomination, exact total', () => {
    const b = breakdown(131, 25);
    expect(b[0]).toEqual({ value: 25, count: 5 });
    expect(b.reduce((s, x) => s + x.value * x.count, 0)).toBe(131);
  });
});

describe('balance', () => {
  it('caps credits', () => expect(applyCredit(990, 20, 1000)).toEqual({ balance: 1000, credited: 10, capped: true }));
  it('withdrawable', () => {
    expect(withdrawable(1000, 600, false)).toBe(400);
    expect(withdrawable(100, 600, false)).toBe(0);
    expect(withdrawable(1000, 0, true)).toBe(0);
  });
  it('exchange', () => {
    expect(buyChips(3, 8)).toBe(24);
    expect(sellChips(25, 10)).toEqual({ emeralds: 2, cost: 20 });
  });
  it('ledger rejects overdraft and fractions', () => {
    expect(applyDelta(5, -6)).toEqual({ ok: false, reason: 'insufficient' });
    expect(applyDelta(5, 0.5).ok).toBe(false);
  });
});

describe('transactions', () => {
  const view = (a: string) => (a === 'bank' ? { balance: Infinity } : a === 'bankroll:c' ? { balance: 100, locked: 60 } : { balance: 50, max: 60 });
  it('all-or-nothing', () => {
    const p = planTransaction([{ account: 'player:a', delta: -30 }, { account: 'player:b', delta: 30 }], view);
    expect(p.ok && [...p.balances]).toEqual([['player:a', 20], ['player:b', 60]]);
    expect(planTransaction([{ account: 'player:a', delta: -60 }, { account: 'bank', delta: 60 }], view).ok).toBe(false);
  });
  it('respects bankroll reservations and caps', () => {
    expect(planTransaction([{ account: 'bankroll:c', delta: -50 }], view).ok).toBe(false);
    const p = planTransaction([{ account: 'player:a', delta: 20 }], view);
    expect(p.ok && p.dropped.get('player:a')).toBe(10);
  });
  it('bankroll reservation rule', () => {
    const b = { balance: 1000, reserved: 0 };
    const r = tryReserve(b, 800)!;
    expect(tryReserve(r, 300)).toBeUndefined();
    expect(bankrollAvailable(r)).toBe(200);
    expect(settleBankroll(r, { reserved: 800, stake: 100, payout: 800 })).toEqual({ balance: 300, reserved: 0 });
  });
});

describe('ores', () => {
  it('maps deepslate and lit variants', () => {
    expect(oreInfo('minecraft:deepslate_diamond_ore')?.key).toBe('economy.ore.diamond');
    expect(oreInfo('minecraft:lit_deepslate_redstone_ore')?.canonical).toBe('redstone_ore');
    expect(oreInfo('minecraft:gilded_blackstone')).toBeUndefined();
  });
  it('needs the right tool, no silk touch', () => {
    const d = oreInfo('minecraft:diamond_ore')!;
    expect(orePays({ minTier: d.minTier, toolTier: pickaxeTier('minecraft:iron_pickaxe'), silkTouch: false, creative: false, placedDebris: false })).toBe(true);
    expect(orePays({ minTier: d.minTier, toolTier: pickaxeTier('minecraft:stone_pickaxe'), silkTouch: false, creative: false, placedDebris: false })).toBe(false);
    expect(orePays({ minTier: d.minTier, toolTier: 4, silkTouch: true, creative: false, placedDebris: false })).toBe(false);
  });
});

describe('mobs', () => {
  it('categories', () => {
    expect(mobRewardKey('minecraft:husk')).toBe('economy.mob.common');
    expect(mobRewardKey('minecraft:cow')).toBeUndefined();
    expect(mobRewardKey('minecraft:slime', { size: 1 })).toBeUndefined();
    expect(mobRewardKey('minecraft:ender_dragon', { dragonKilledBefore: true })).toBe('economy.mob.enderDragonRepeat');
    expect(mobRewardKey('burmaldaholic:debt_collector')).toBeUndefined();
  });
  it('diminishing returns and difficulty', () => {
    const cfg = { full: 20, reduced: 60, factor: 0.25 };
    expect(mobReward(4, 20, 1, cfg)).toBe(4);
    expect(mobReward(4, 21, 1.25, cfg)).toBe(1);
    expect(mobReward(2, 21, 1, cfg)).toBe(0);
    expect(mobReward(4, 61, 1, cfg)).toBe(0);
    expect(difficultyMultiplier('normal', true, 1.25)).toBe(1.25);
    expect(difficultyMultiplier('easy', false, 1.25)).toBe(1);
  });
  it('sliding window', () => {
    const w: number[] = [];
    recordKill(w, 0, 100);
    recordKill(w, 50, 100);
    expect(recordKill(w, 120, 100)).toBe(2);
  });
});

describe('trades', () => {
  const cfg = { base: 1, perEmerald: 1, perTradeCap: 10, dailyCap: 200 };
  it('per-trade and daily caps', () => {
    expect(tradeReward(3, cfg, 0)).toBe(4);
    expect(tradeReward(64, cfg, 0)).toBe(10);
    expect(tradeReward(5, cfg, 198)).toBe(2);
  });
});

describe('trade detection (review m2)', () => {
  const open = { tradeOpen: true, tainted: false };
  it('a real trade: emeralds for items, or items for emeralds', () => {
    expect(detectTrade({ em: 20, blocks: 0, other: 5 }, { em: 10, blocks: 0, other: 6 }, open)).toBe(10);
    expect(detectTrade({ em: 0, blocks: 0, other: 30 }, { em: 1, blocks: 0, other: 8 }, open)).toBe(1);
  });
  it('crafting / uncrafting an emerald block is not a trade, even with the trade screen open', () => {
    expect(detectTrade({ em: 9, blocks: 0, other: 5 }, { em: 0, blocks: 1, other: 5 }, open)).toBe(0);
    expect(detectTrade({ em: 0, blocks: 1, other: 5 }, { em: 9, blocks: 0, other: 5 }, open)).toBe(0);
  });
  it('needs an open trade session and no drop / pickup around the player', () => {
    const a = { em: 20, blocks: 0, other: 5 };
    const b = { em: 10, blocks: 0, other: 6 };
    expect(detectTrade(a, b, { tradeOpen: false, tainted: false })).toBe(0);
    expect(detectTrade(a, b, { tradeOpen: true, tainted: true })).toBe(0);
  });
  it('same-direction or one-sided changes are not trades', () => {
    expect(detectTrade({ em: 5, blocks: 0, other: 5 }, { em: 6, blocks: 0, other: 6 }, open)).toBe(0);
    expect(detectTrade({ em: 5, blocks: 0, other: 5 }, { em: 3, blocks: 0, other: 5 }, open)).toBe(0);
  });
});

describe('amount hardening, cashier re-check and loan cap (review m3, m5, m9)', () => {
  it('isChipAmount: safe integers > 0 only', () => {
    expect(isChipAmount(5)).toBe(true);
    for (const x of [0, -5, 1.5, Number.NaN, Infinity, 2 ** 60]) expect(isChipAmount(x)).toBe(false);
  });
  it('sell count is re-clamped by what is withdrawable at submit', () => {
    expect(sellCount(10, 10, 1000, 10)).toBe(10);
    expect(sellCount(10, 10, 55, 10)).toBe(5);
    expect(sellCount(10, 10, 0, 10)).toBe(0); // defaulted while the form was open
    expect(sellCount(10, 10, 1000, 0)).toBe(0);
  });
  it('a loan must fit under the balance cap in full', () => {
    expect(fitsUnderCap(900, 100, 1000)).toBe(true);
    expect(fitsUnderCap(901, 100, 1000)).toBe(false);
  });
});
