import { describe, expect, it } from 'vitest';
import { seededRng } from '../../../core/logic/rng';
import { type BotTier, botView, decideBot, opponentRanges, samplesFor } from './bots';
import { applyAction, coerce, legal } from './engine';
import { equity } from './equity';
import { TableModel, buyInRange, smallBlind } from './table';

const RAKE = { percent: 0.05, capBb: 3, noFlopNoDrop: true };
const table = (maxSeats = 6, bb = 10) => new TableModel({ maxSeats, bb, rake: RAKE });
const BOTS = { enabled: true, mix: [50, 40, 10], buyIn: 1000 };

describe('stakes and buy-in', () => {
  it('SB = BB / 2 floored, min 1', () => {
    expect(smallBlind(2)).toBe(1);
    expect(smallBlind(3)).toBe(1);
    expect(smallBlind(200)).toBe(100);
  });
  it('buy-in 40–100 BB clamped by balance', () => {
    expect(buyInRange(10, 40, 100, 5000)).toEqual({ min: 400, max: 1000 });
    expect(buyInRange(10, 40, 100, 700)).toEqual({ min: 400, max: 700 });
    const broke = buyInRange(10, 40, 100, 300);
    expect(broke.max).toBeLessThan(broke.min);
  });
  it('top-up only up to the max buy-in in total', () => {
    expect(buyInRange(10, 40, 100, 5000, 650)).toEqual({ min: 1, max: 350 });
    expect(buyInRange(10, 40, 100, 5000, 1200).max).toBe(0);
  });
});

describe('seating and bots', () => {
  it('bots fill max seats − humans − 1, at least one opponent for a lone human', () => {
    const m = table(6);
    expect(m.botTarget(true)).toBe(0);
    m.addHuman('h1', 'Alex', 400);
    expect(m.botTarget(true)).toBe(4);
    expect(m.botTarget(false)).toBe(0);
    const m2 = table(2);
    m2.addHuman('h1', 'Alex', 400);
    expect(m2.botTarget(true)).toBe(1);
  });

  it('fillBots adds bots with unique names and removes busted / surplus ones', () => {
    const m = table(6);
    m.addHuman('h1', 'Alex', 400);
    let id = 0;
    const r = m.fillBots(BOTS, seededRng(1), () => `bot:${++id}`);
    expect(r.joined).toHaveLength(4);
    expect(new Set(m.bots().map((b) => b.name)).size).toBe(4);
    expect(m.seats.filter((s) => !s)).toHaveLength(1);
    m.bots()[0]!.stack = 0;
    m.addHuman('h2', 'Bea', 400);
    const r2 = m.fillBots(BOTS, seededRng(2), () => `bot:${++id}`);
    expect(r2.left.length).toBeGreaterThanOrEqual(1);
    expect(m.bots()).toHaveLength(3);
    expect(m.bots().every((b) => b.stack > 0)).toBe(true);
  });

  it('makeRoom frees a bot seat for a walk-in when the table is full', () => {
    const m = table(2);
    m.addHuman('h1', 'Alex', 400);
    m.fillBots(BOTS, seededRng(1), () => 'bot:1');
    expect(m.addHuman('h2', 'Bea', 400)).toBe(-1);
    expect(m.makeRoom()?.kind).toBe('bot');
    expect(m.addHuman('h2', 'Bea', 400)).toBeGreaterThanOrEqual(0);
  });

  it('the button moves one dealt-in seat clockwise every hand', () => {
    const m = table(6);
    m.addHuman('a', 'A', 100);
    m.addHuman('b', 'B', 100);
    m.addHuman('c', 'C', 100);
    m.removeSeat('b'); // seat 1 empty
    const rng = seededRng(1);
    const buttons: number[] = [];
    for (let k = 0; k < 3; k++) {
      const h = m.startHand(rng)!;
      buttons.push(m.button);
      while (!h.complete) applyAction(h, coerce(h, { type: 'fold' }));
      m.settleHand();
    }
    expect(buttons).toEqual([0, 2, 0]);
  });

  it('needs a human with chips and 2 dealt-in players to start', () => {
    const m = table(6);
    expect(m.canStart()).toBe(false);
    m.addHuman('a', 'A', 100);
    expect(m.canStart()).toBe(false);
    m.fillBots(BOTS, seededRng(1), () => 'bot:1');
    expect(m.canStart()).toBe(true);
    m.seatOf('a')!.leaving = true;
    expect(m.canStart()).toBe(false);
  });

  it('abortHand restores the start stacks', () => {
    const m = table(6);
    m.addHuman('a', 'A', 100);
    m.addHuman('b', 'B', 100);
    const h = m.startHand(seededRng(1))!;
    applyAction(h, { type: 'raise', to: 50 });
    m.abortHand();
    expect(m.hand).toBeUndefined();
    expect(m.humans().map((s) => s.stack)).toEqual([100, 100]);
  });
});

describe('timeouts and sitting out', () => {
  it('2 consecutive timeouts -> sitting out; an action resets the count', () => {
    const m = table();
    m.addHuman('a', 'A', 100);
    expect(m.recordTimeout('a', 2)).toBe(false);
    m.recordAction('a');
    expect(m.recordTimeout('a', 2)).toBe(false);
    expect(m.recordTimeout('a', 2)).toBe(true);
    expect(m.seatOf('a')!.sittingOut).toBe(true);
    m.sitIn('a');
    expect(m.seatOf('a')!.sittingOut).toBe(false);
  });

  it('removed after 3 hands sitting out (blinds still posted)', () => {
    const m = table();
    m.addHuman('a', 'A', 1000);
    m.addHuman('b', 'B', 1000);
    m.sitOut('a');
    const rng = seededRng(4);
    for (let k = 0; k < 3; k++) {
      expect(m.toRemove(3)).toHaveLength(0);
      const h = m.startHand(rng)!;
      expect(h.players.map((p) => p.id)).toContain('a');
      while (!h.complete) applyAction(h, coerce(h, { type: 'fold' }));
      m.settleHand();
    }
    expect(m.toRemove(3).map((s) => s.id)).toEqual(['a']);
  });

  it('VPIP history feeds the shark (last 20 hands)', () => {
    const m = table();
    m.addHuman('a', 'A', 100_000);
    m.addHuman('b', 'B', 100_000);
    const rng = seededRng(9);
    for (let k = 0; k < 25; k++) {
      const h = m.startHand(rng)!;
      while (!h.complete) {
        const p = h.players[h.toAct]!;
        applyAction(h, coerce(h, p.id === 'a' ? { type: 'call' } : { type: 'check' }));
      }
      m.settleHand();
    }
    expect(m.seatOf('a')!.vpipHistory).toHaveLength(20);
    expect(m.vpipMap().get('a')).toBeGreaterThan(0.4);
  });
});

// ---- Monte-Carlo simulations -----------------------------------------------------------------

interface SimOptions {
  tiers: BotTier[];
  humans: boolean[];
  hands: number;
  seed: number;
  samples: number;
}

/** Play bot-vs-bot hands, resetting stacks to 100 BB each hand. Returns net per seat and rake stats. */
function simulate(o: SimOptions): { net: number[]; rake: number; rakedPots: number; potTotal: number } {
  const rng = seededRng(o.seed);
  const m = table(o.tiers.length, 10);
  o.tiers.forEach((tier, i) => {
    m.seats[i] = { id: `s${i}`, name: `S${i}`, kind: o.humans[i] ? 'human' : 'bot', tier, stack: 1000, sittingOut: false, timeouts: 0, sitOutHands: 0, leaving: false, disconnected: false, vpipHistory: [] };
  });
  const net = o.tiers.map(() => 0);
  let rake = 0;
  let rakedPots = 0;
  let potTotal = 0;
  for (let n = 0; n < o.hands; n++) {
    for (const s of m.occupied()) s.stack = 1000;
    const h = m.startHand(rng)!;
    let guard = 0;
    while (!h.complete) {
      if (++guard > 200) throw new Error('stuck');
      const i = h.toAct;
      const tier = m.seats[m.handSeats[i]!]!.tier!;
      const v = botView(h, i, m.vpipMap());
      const samples = Math.min(o.samples, samplesFor(tier, h.street, { regularSamples: o.samples, sharkSamples: o.samples }));
      const e = samples ? equity({ hole: v.hole, board: v.board, opponents: v.opponents, samples, ranges: tier === 'shark' ? opponentRanges(h, i) : undefined }, rng) : undefined;
      applyAction(h, coerce(h, decideBot(tier, v, e, rng)));
      expect(legal(h).toCall).toBeGreaterThanOrEqual(0);
    }
    h.players.forEach((p, k) => (net[m.handSeats[k]!]! += p.stack - p.startStack));
    for (const pot of h.result!.pots) {
      potTotal += pot.amount;
      if (pot.rake > 0) {
        rake += pot.rake;
        rakedPots += pot.amount;
      }
    }
    m.settleHand();
  }
  return { net, rake, rakedPots, potTotal };
}

describe('Monte-Carlo: rake is the house edge on PvP pots', () => {
  it('rake ≈ 3–5 % of raked pots, never above 5 %, chips conserved', () => {
    const r = simulate({ tiers: ['regular', 'regular', 'fish', 'shark', 'fish', 'regular'], humans: [true, true, true, true, true, true], hands: 1500, seed: 11, samples: 40 });
    const edge = r.rake / r.rakedPots;
    expect(r.rakedPots).toBeGreaterThan(0);
    expect(edge).toBeLessThanOrEqual(0.05);
    expect(edge).toBeGreaterThan(0.02);
    // everything the players lost went to the rake
    expect(r.net.reduce((a, b) => a + b, 0)).toBe(-r.rake);
  }, 120_000);

  it('a lone human vs bots is never raked', () => {
    const r = simulate({ tiers: ['regular', 'fish', 'shark'], humans: [true, false, false], hands: 400, seed: 12, samples: 30 });
    expect(r.rake).toBe(0);
    expect(r.net.reduce((a, b) => a + b, 0)).toBe(0);
  }, 120_000);
});

describe('Monte-Carlo: bot skill ordering', () => {
  it('sharks and regulars beat fish over many hands', () => {
    const r = simulate({ tiers: ['fish', 'regular', 'shark', 'fish'], humans: [true, false, false, false], hands: 3000, seed: 21, samples: 40 });
    const [fish1, reg, shark, fish2] = r.net as [number, number, number, number];
    const fishAvg = (fish1 + fish2) / 2;
    expect(shark).toBeGreaterThan(fishAvg);
    expect(reg).toBeGreaterThan(fishAvg);
    expect(fishAvg).toBeLessThan(0);
  }, 180_000);
});
