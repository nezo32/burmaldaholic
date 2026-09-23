import { describe, expect, it } from 'vitest';
import {
  CROUPIER_OFFERS,
  MIN_MISSING_TICKS,
  PRESETS,
  SHULKER_OFFERS,
  casinoBox,
  checkPurchase,
  decodeCasino,
  encodeCasino,
  findCasinoAt,
  inBox,
  newCasinoId,
  respawnDecision,
  tableSlotAt,
} from './casinos';
import { BLOCK, layout } from './layouts';

const parlor = {
  id: newCasinoId('piglin_parlor', { x: 100, y: 40, z: -50 }),
  layout: 'piglin_parlor',
  kind: 'piglin_parlor' as const,
  dim: 'minecraft:nether',
  origin: { x: 100, y: 40, z: -50 },
};

describe('casino records', () => {
  it('round-trips the persisted form', () => {
    expect(parlor.id).toBe('p100_40_-50');
    expect(encodeCasino(parlor)).toBe('p100_40_-50|piglin_parlor|minecraft:nether|100|40|-50');
    expect(decodeCasino(encodeCasino(parlor))).toEqual(parlor);
    expect(decodeCasino('x|no_such_layout|minecraft:nether|1|2|3')).toBeUndefined();
    expect(decodeCasino('x|piglin_parlor|minecraft:nether|1|a|3')).toBeUndefined();
  });

  it('knows its bounds', () => {
    const b = casinoBox(parlor, layout('piglin_parlor')!);
    expect(b).toEqual({ min: { x: 100, y: 40, z: -50 }, max: { x: 120, y: 51, z: -30 } });
    expect(inBox(b, { x: 120.9, y: 41, z: -30.1 })).toBe(true);
    expect(inBox(b, { x: 121, y: 41, z: -40 })).toBe(false);
    expect(inBox(b, { x: 121, y: 41, z: -40 }, 1)).toBe(true);
    expect(findCasinoAt([parlor], 'minecraft:nether', { x: 110, y: 42, z: -40 })).toBe(parlor);
    expect(findCasinoAt([parlor], 'minecraft:overworld', { x: 110, y: 42, z: -40 })).toBeUndefined();
  });

  it('exposes fixed table presets for worldgen tables', () => {
    const poker = layout('piglin_parlor')!.tables.find((t) => t.block === BLOCK.poker)!;
    const slot = tableSlotAt(parlor, { x: 100 + poker.pos.x + 0.5, y: 41.2, z: -50 + poker.pos.z + 0.5 });
    expect(slot?.block).toBe(BLOCK.poker);
    expect(PRESETS[slot!.preset]).toEqual({ id: 'parlor_poker', house: true, poker: { stakes: 'low', bots: 3, botMix: 'regular_heavy' } });
    expect(tableSlotAt(parlor, { x: 101, y: 41, z: -49 })).toBeUndefined();
    expect(PRESETS.high_roller_blackjack).toMatchObject({ minBet: 100, tierMultiplier: 2, minTier: 2 });
    expect(PRESETS.high_roller_roulette).toMatchObject({ minBet: 100 });
    for (const p of Object.values(PRESETS)) expect(p.house).toBe(true);
  });
});

describe('NPC respawn', () => {
  it('respawns only after the configured delay and two observations', () => {
    let d = respawnDecision(false, undefined, 1000, 24000);
    expect(d).toEqual({ missingSince: 1000, respawn: false });
    const s = d.missingSince;
    d =respawnDecision(false, s, 20000, 24000);
    expect(d.respawn).toBe(false);
    d = respawnDecision(false, s, 25000, 24000);
    expect(d).toEqual({ missingSince: undefined, respawn: true });
    // a present NPC resets the clock
    expect(respawnDecision(true, 1000, 99999, 24000)).toEqual({ missingSince: undefined, respawn: false });
    // respawnTicks 0 still waits for a second look (chunk/entity load race)
    expect(respawnDecision(false, undefined, 0, 0).respawn).toBe(false);
    expect(respawnDecision(false, 0, MIN_MISSING_TICKS - 1, 0).respawn).toBe(false);
    expect(respawnDecision(false, 0, MIN_MISSING_TICKS, 0).respawn).toBe(true);
  });
});

describe('NPC shops', () => {
  it('sells scratch cards 10/100 and Lucky Coins 25 (GAME_DESIGN §16.1, §11.3)', () => {
    expect(CROUPIER_OFFERS.map((o) => [o.item, o.price, o.minTier])).toEqual([
      ['burmaldaholic:scratch_card', 10, 0],
      ['burmaldaholic:scratch_card_gold', 100, 1],
      ['burmaldaholic:lucky_coin', 25, 0],
    ]);
    expect(SHULKER_OFFERS.map((o) => o.item)).toEqual(['burmaldaholic:scratch_card_gold']);
  });

  it('checks VIP tier before balance', () => {
    const gold = CROUPIER_OFFERS[1]!;
    expect(checkPurchase(gold, 5000, 0)).toBe('vip');
    expect(checkPurchase(gold, 99, 1)).toBe('funds');
    expect(checkPurchase(gold, 100, 1)).toBe('ok');
    expect(checkPurchase(CROUPIER_OFFERS[0]!, 10, 0)).toBe('ok');
  });
});
