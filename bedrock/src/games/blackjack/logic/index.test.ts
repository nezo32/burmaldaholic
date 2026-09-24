import { describe, expect, it } from 'vitest';
import { parseCard } from '../../../core/logic/cards';
import { seededRng } from '../../../core/logic/rng';
import {
  type BlackjackRules,
  BlackjackRound,
  DEFAULT_RULES,
  Shoe,
  StackedSource,
  basicStrategy,
  blackjackReturn,
  displayTotals,
  handValue,
  isNatural,
  isPair,
  normalizeRules,
  standAllReturns,
  surrenderReturn,
} from './index';

const cards = (...ids: string[]) => ids.map(parseCard);
/** Deal order: seat1 c1, seat2 c1…, dealer up, seat1 c2…, dealer hole, then draws. */
const round = (deal: string[], bets: number[] = [10], rules: Partial<BlackjackRules> = {}) =>
  new BlackjackRound(
    { ...DEFAULT_RULES, ...rules },
    new StackedSource(cards(...deal), new Shoe(seededRng(0), 6)),
    bets.map((bet, i) => ({ seat: i + 1, id: `p${i + 1}`, bet })),
  );

describe('hand values', () => {
  it('counts soft 21', () => expect(handValue(['A', 'K'])).toEqual({ total: 21, soft: true }));
  it('demotes aces', () => expect(handValue(['A', 'A', '9'])).toEqual({ total: 21, soft: true }));
  it('hard total', () => expect(handValue(['K', 'Q', '5'])).toEqual({ total: 25, soft: false }));
  it('two aces are 12 soft', () => expect(handValue(['A', 'A'])).toEqual({ total: 12, soft: true }));
  it('soft becomes hard', () => expect(handValue(['A', '6', '9'])).toEqual({ total: 16, soft: false }));
  it('natural only on two unsplit cards', () => {
    expect(isNatural(cards('AS', 'KD'))).toBe(true);
    expect(isNatural(cards('AS', 'KD'), true)).toBe(false);
    expect(isNatural(cards('7S', '7D', '7C'))).toBe(false);
  });
  it('pairs need the same rank', () => {
    expect(isPair(cards('KS', 'KD'))).toBe(true);
    expect(isPair(cards('KS', 'QD'))).toBe(false);
  });
  it('display totals', () => {
    expect(displayTotals(cards('AS', '6D'))).toEqual({ low: 7, high: 17 });
    expect(displayTotals(cards('AS', 'KD'))).toEqual({ high: 21 });
    expect(displayTotals(cards('9S', '6D'))).toEqual({ high: 15 });
  });
});

describe('payouts', () => {
  it('3:2 floored', () => {
    expect(blackjackReturn(10, 1.5)).toBe(25);
    expect(blackjackReturn(5, 1.5)).toBe(12); // +7
    expect(blackjackReturn(1, 1.5)).toBe(2);
    expect(blackjackReturn(10, 1.2)).toBe(22); // 6:5
  });
  it('surrender returns half, floored', () => {
    expect(surrenderReturn(10)).toBe(5);
    expect(surrenderReturn(5)).toBe(2);
  });
  it('rules are clamped', () => {
    const r = normalizeRules({ decks: 20, penetration: 0.99, maxHands: 9, blackjackPayout: 5 });
    expect(r).toMatchObject({ decks: 8, penetration: 0.9, maxHands: 4, blackjackPayout: 2 });
  });
});

describe('round: naturals, peek, insurance', () => {
  it('player blackjack vs 9 pays 3:2 at once, dealer does not draw', () => {
    const r = round(['AS', '9D', 'KH', '7C']);
    expect(r.phase).toBe('done');
    expect(r.seats[0]!.hands[0]!.outcome).toBe('blackjack');
    expect(r.returnOf(1)).toBe(25);
    expect(r.dealer).toHaveLength(2);
  });

  it('dealer blackjack under a ten: others lose, player blackjack pushes', () => {
    const r = round(['AS', '9C', 'KD', 'KH', '7C', 'AH'], [10, 10]);
    expect(r.peeked).toBe(true);
    expect(r.dealerBlackjack).toBe(true);
    expect(r.phase).toBe('done');
    expect(r.seats[0]!.hands[0]!.outcome).toBe('push');
    expect(r.returnOf(1)).toBe(10);
    expect(r.seats[1]!.hands[0]!.outcome).toBe('dealer_blackjack');
    expect(r.returnOf(2)).toBe(0);
  });

  it('ace up opens insurance; insurance pays 2:1 on dealer blackjack', () => {
    const r = round(['9S', 'AD', '9H', 'KC'], [10]);
    expect(r.phase).toBe('insurance');
    expect(r.insuranceOffer(1)).toBe('insurance');
    r.decideInsurance(1, true);
    expect(r.phase).toBe('done');
    expect(r.seats[0]!.insurance).toBe(5);
    expect(r.stakedOf(1)).toBe(15);
    expect(r.returnOf(1)).toBe(15); // main lost, insurance 5 -> 15
  });

  it('insurance is lost when the dealer has no blackjack, play continues', () => {
    const r = round(['9S', 'AD', '9H', '5C', 'KS'], [10]);
    r.decideInsurance(1, true);
    expect(r.phase).toBe('turns');
    expect(r.current()?.seat.seat).toBe(1);
    r.act(1, 'stand'); // 18 vs A+5 -> dealer draws K -> 16 -> draws
    expect(r.phase).toBe('done');
    expect(r.seats[0]!.insuranceReturn).toBe(0);
  });

  it('even money pays 1:1 immediately', () => {
    const r = round(['AS', 'AD', 'KH', 'KC'], [10]);
    expect(r.insuranceOffer(1)).toBe('even_money');
    r.decideInsurance(1, true);
    expect(r.seats[0]!.settled).toBe(true);
    expect(r.returnOf(1)).toBe(20);
    expect(r.seats[0]!.hands[0]!.outcome).toBe('even_money');
  });

  it('declined even money vs dealer blackjack is a push', () => {
    const r = round(['AS', 'AD', 'KH', 'KC'], [10]);
    r.decideInsurance(1, false);
    expect(r.returnOf(1)).toBe(10);
  });

  it('no insurance offered on a 1-chip bet, and none when disabled', () => {
    expect(round(['9S', 'AD', '9H', '5C', 'KS'], [1]).phase).toBe('turns');
    expect(round(['9S', 'AD', '9H', '5C', 'KS'], [10], { insurance: false }).phase).toBe('turns');
  });

  it('standAll declines pending insurance', () => {
    const r = round(['9S', '8S', 'AD', '9H', '8H', '5C'], [10, 10]);
    r.standAll(1);
    expect(r.phase).toBe('insurance');
    r.decideInsurance(2, false);
    expect(r.phase).toBe('turns');
    expect(r.current()?.seat.seat).toBe(2);
  });
});

describe('round: player actions', () => {
  it('hit, bust and dealer skips drawing when everyone is bust', () => {
    const r = round(['KS', '6D', '6H', '5C', 'QH'], [10]);
    expect(r.legal(1)).toEqual(['hit', 'stand', 'double']);
    r.act(1, 'hit');
    expect(r.phase).toBe('done');
    expect(r.seats[0]!.hands[0]!.outcome).toBe('bust');
    expect(r.dealer).toHaveLength(2);
  });

  it('auto-stands at 21', () => {
    const r = round(['KS', '6D', '5H', 'KC', '6C', '2D'], [10]);
    r.act(1, 'hit'); // 21
    expect(r.phase).toBe('done');
    expect(r.seats[0]!.hands[0]!.outcome).toBe('win');
    expect(r.returnOf(1)).toBe(20);
  });

  it('double: one card, bet doubled', () => {
    const r = round(['6S', '9D', '5H', '7C', 'KH', '10D'], [10]);
    expect(r.extraStake(1, 'double')).toBe(10);
    r.act(1, 'double'); // 21 vs 16 -> dealer draws 10 -> bust
    const h = r.seats[0]!.hands[0]!;
    expect(h.cards).toHaveLength(3);
    expect(h.bet).toBe(20);
    expect(h.outcome).toBe('dealer_bust');
    expect(r.returnOf(1)).toBe(40);
  });

  it('double/split need enough balance', () => {
    const r = round(['8S', '9D', '8H', '7C'], [10]);
    expect(r.legal(1, 9)).toEqual(['hit', 'stand']);
    expect(r.legal(1, 10)).toEqual(['hit', 'stand', 'double', 'split']);
  });

  it('K+Q cannot be split', () => {
    const r = round(['KS', '9D', 'QH', '7C'], [10]);
    expect(r.legal(1)).not.toContain('split');
  });

  it('split, re-split up to 4 hands, double after split', () => {
    // p: 8,8 vs 6; split -> h1 8+8, h2 8+3 ; resplit h1 -> 8+8, 8+2 ; resplit -> 8+10, 8+9 (4 hands max)
    const r = round(['8S', '6D', '8H', 'KC', '8D', '3C', '8C', '2S', '10S', '9S'], [10]);
    r.act(1, 'split');
    expect(r.seats[0]!.hands).toHaveLength(2);
    r.act(1, 'split');
    expect(r.seats[0]!.hands).toHaveLength(3);
    expect(r.legal(1)).toContain('split');
    r.act(1, 'split');
    expect(r.seats[0]!.hands).toHaveLength(4);
    expect(r.legal(1)).not.toContain('split'); // 4 hands: no more splits
    const totals = r.seats[0]!.hands.map((h) => h.cards.map((c) => c.rank).join('+'));
    expect(totals).toEqual(['8+10', '8+9', '8+2', '8+3']);
    r.act(1, 'stand'); // 18
    r.act(1, 'stand'); // 17
    expect(r.legal(1)).toContain('double'); // DAS on 8+2
    r.act(1, 'double'); // 8+2+x
    expect(r.current()?.handIndex).toBe(3);
  });

  it('no double after split when DAS is off', () => {
    const r = round(['8S', '6D', '8H', 'KC', '2D', '3C'], [10], { doubleAfterSplit: false });
    r.act(1, 'split');
    expect(r.legal(1)).toEqual(['hit', 'stand']);
  });

  it('split aces: one card each, no hit, A+10 pays 1:1 (not blackjack)', () => {
    const r = round(['AS', '6D', 'AH', 'KC', 'KD', '9C', '10H'], [10]);
    r.act(1, 'split');
    expect(r.phase).toBe('done'); // both ace hands closed automatically
    const [h1, h2] = r.seats[0]!.hands;
    expect(h1!.cards.map((c) => c.rank)).toEqual(['A', 'K']);
    expect(h1!.outcome).toBe('dealer_bust'); // dealer 16 + 10 = bust
    expect(h1!.ret).toBe(20); // 1:1, not 3:2
    expect(h2!.cards.map((c) => c.rank)).toEqual(['A', '9']);
    expect(r.returnOf(1)).toBe(40);
  });

  it('no re-split of aces by default; allowed with resplitAces', () => {
    const noRsa = round(['AS', '6D', 'AH', 'KC', 'AD', '9C', '10H'], [10]);
    noRsa.act(1, 'split');
    expect(noRsa.phase).toBe('done');
    const rsa = round(['AS', '6D', 'AH', 'KC', 'AD', '9C', '5H', '7H', '10H'], [10], { resplitAces: true });
    rsa.act(1, 'split');
    expect(rsa.legal(1)).toEqual(['stand', 'split']);
    rsa.act(1, 'split');
    expect(rsa.seats[0]!.hands).toHaveLength(3);
  });

  it('surrender only when enabled, only on the first two cards', () => {
    expect(round(['10S', 'KD', '6H', '7C'], [10]).legal(1)).not.toContain('surrender');
    const r = round(['10S', 'KD', '6H', '7C'], [10], { lateSurrender: true });
    expect(r.legal(1)).toContain('surrender');
    r.act(1, 'surrender');
    expect(r.phase).toBe('done');
    expect(r.returnOf(1)).toBe(5);
    expect(r.dealer).toHaveLength(2);
  });

  it('push returns the stake', () => {
    const r = round(['10S', 'KD', '8H', '8C'], [10]);
    r.act(1, 'stand');
    expect(r.seats[0]!.hands[0]!.outcome).toBe('push');
    expect(r.returnOf(1)).toBe(10);
  });

  it('dealer stands on soft 17 (S17) and hits it with H17', () => {
    const s17 = round(['10S', 'AD', '8H', '6C'], [10], { insurance: false });
    s17.act(1, 'stand');
    expect(s17.dealer).toHaveLength(2);
    expect(s17.seats[0]!.hands[0]!.outcome).toBe('win');
    const h17 = round(['10S', 'AD', '8H', '6C', '4D'], [10], { insurance: false, dealerHitsSoft17: true });
    h17.act(1, 'stand');
    expect(h17.dealer).toHaveLength(3);
    expect(h17.seats[0]!.hands[0]!.outcome).toBe('lose'); // dealer 21
  });

  it('multi-seat: deal order, turn order and standAll on timeout', () => {
    const r = round(['2S', '3S', '4S', '9D', '5S', '6S', '7S', '8D'], [10, 20, 30]);
    expect(r.seats.map((s) => s.hands[0]!.cards.map((c) => c.rank).join())).toEqual(['2,5', '3,6', '4,7']);
    expect(r.upCard.rank).toBe('9');
    expect(r.current()?.seat.seat).toBe(1);
    expect(r.act(2, 'stand')).toBe(false); // not seat 2's turn
    r.standAll(1);
    expect(r.current()?.seat.seat).toBe(2);
    r.act(2, 'stand');
    r.standAll(3);
    expect(r.phase).toBe('done');
    expect(r.dealer).toHaveLength(2); // 17
    expect(r.seats.map((s) => s.hands[0]!.outcome)).toEqual(['lose', 'lose', 'lose']);
  });
});

describe('shoe', () => {
  it('has decks × 52 cards and a cut card', () => {
    const s = new Shoe(seededRng(1), 6);
    expect(s.size).toBe(312);
    for (let i = 0; i < 233; i++) s.draw();
    expect(s.needsShuffle(0.75)).toBe(false);
    s.draw();
    expect(s.needsShuffle(0.75)).toBe(true);
    s.shuffle();
    expect(s.dealt).toBe(0);
  });
  it('reshuffles if it ever runs dry', () => {
    const s = new Shoe(seededRng(2), 1);
    for (let i = 0; i < 60; i++) s.draw();
    expect(s.dealt).toBe(8);
  });
});

/** Play `n` rounds with basic strategy; returns house edge (fraction of initial bets). */
function simulate(n: number, rules: BlackjackRules, seed: number, insure = false) {
  const rng = seededRng(seed);
  const shoe = new Shoe(rng, rules.decks);
  const bet = 1000;
  let wagered = 0;
  let net = 0;
  let insured = 0;
  let insuranceNet = 0;
  for (let i = 0; i < n; i++) {
    if (shoe.needsShuffle(rules.penetration)) shoe.shuffle();
    const r = new BlackjackRound(rules, shoe, [{ seat: 1, id: 'p', bet }]);
    if (r.phase === 'insurance') {
      const offer = r.insuranceOffer(1);
      r.decideInsurance(1, insure && offer === 'insurance');
    }
    for (let guard = 0; r.phase === 'turns' && guard < 50; guard++) {
      const c = r.current()!;
      r.act(1, basicStrategy(c.hand.cards, r.upCard, r.legal(1)));
    }
    const s = r.seats[0]!;
    if (s.insurance) {
      insured++;
      insuranceNet += s.insuranceReturn - s.insurance;
    }
    wagered += bet;
    net += r.returnOf(1) - r.stakedOf(1) - (s.insuranceReturn - s.insurance);
  }
  return { edge: -net / wagered, insuranceEdge: insured ? -insuranceNet / (insured * (bet / 2)) : 0 };
}

describe('Monte-Carlo RTP (GAME_DESIGN §6.1, §17)', () => {
  it('basic strategy house edge ≈ 0.41 % (6 decks, S17, DAS, RSP4, no RSA, peek)', () => {
    const { edge } = simulate(1_000_000, DEFAULT_RULES, 20260923);
    // σ ≈ 1.15 / √1e6 = 0.115 %; ± 0.45 % is ~4σ.
    expect(edge).toBeGreaterThan(0.0041 - 0.0045);
    expect(edge).toBeLessThan(0.0041 + 0.0045);
  }, 120_000);

  it('insurance house edge ≈ 7.40 % (1 − 3 × 96/311)', () => {
    const { insuranceEdge } = simulate(400_000, DEFAULT_RULES, 7, true);
    // insurance bets ≈ n/13 ≈ 30 000, σ ≈ 2.8 / √30 000 ≈ 1.6 %
    expect(insuranceEdge).toBeGreaterThan(0.074 - 0.06);
    expect(insuranceEdge).toBeLessThan(0.074 + 0.06);
  }, 120_000);

  it('3:2 vs 6:5 costs the player ≈ 1.4 %', () => {
    const a = simulate(300_000, DEFAULT_RULES, 99).edge;
    const b = simulate(300_000, { ...DEFAULT_RULES, blackjackPayout: 1.2 }, 99).edge;
    expect(b - a).toBeGreaterThan(0.009);
    expect(b - a).toBeLessThan(0.019);
  }, 120_000);
});

describe('drawn outcome projection (review M1)', () => {
  it('standAllReturns = the result of standing now, without touching the round or the shoe', () => {
    // seat 1: 10 + 6 = 16; dealer 9 up, 7 hole = 16, next cards: 5 (dealer 21), then 2
    const src = new StackedSource(cards('10S', '9H', '6D', '7C', '5S', '2S'));
    const r = new BlackjackRound(DEFAULT_RULES, src, [{ seat: 1, id: 'p1', bet: 10 }]);
    expect(r.phase).toBe('turns');
    const fork = new StackedSource(cards('5S', '2S'));
    const proj = standAllReturns(r, fork);
    expect(proj.get(1)).toBe(0); // 16 vs dealer 21
    // the real round is untouched: still the player's turn with 2 cards
    expect(r.phase).toBe('turns');
    expect(r.current()?.hand.cards).toHaveLength(2);
    expect(r.dealer).toHaveLength(2);
    // playing it for real (stand) gives the same result from the same next cards
    r.standAll(1);
    expect(r.returnOf(1)).toBe(0);
  });

  it('Shoe.fork deals the same next cards and leaves the shoe alone', () => {
    const shoe = new Shoe(seededRng(7), 1);
    shoe.draw();
    const f = shoe.fork();
    const a = [f.draw(), f.draw(), f.draw()];
    expect(shoe.dealt).toBe(1);
    expect([shoe.draw(), shoe.draw(), shoe.draw()]).toEqual(a);
  });

  it('projects insurance, doubles and every seat', () => {
    // seat1 11 (6+5), seat2 20 (K+Q); dealer A up, 9 hole (no blackjack) -> insurance phase
    const r = round(['6S', 'KH', 'AD', '5C', 'QS', '9D', '10C', '2H'], [10, 10], { insurance: true });
    expect(r.phase).toBe('insurance');
    const shoe = new StackedSource(cards('10C', '2H'));
    const proj = standAllReturns(r, shoe);
    // everyone declines insurance, stands: dealer A+9 = soft 20 stands; 11 loses, 20 pushes
    expect(proj.get(1)).toBe(0);
    expect(proj.get(2)).toBe(10);
    expect(r.phase).toBe('insurance');
  });
});
