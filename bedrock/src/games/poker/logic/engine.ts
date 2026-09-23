/**
 * No-Limit Texas Hold'em hand state machine (GAME_DESIGN §7.3). PURE.
 *
 *   startHand() -> [betting: applyAction()...] -> street transitions (burn + deal) ->
 *   showdown / uncontested -> state.result (stacks already updated)
 *
 * Players are indexed clockwise (only those dealt in). Heads-up: the button posts the small
 * blind and acts first preflop. Min bet = BB; min raise = the last full bet/raise increment of
 * the street (≥ BB); an all-in short of a full raise does not reopen betting for players who
 * already acted. The uncalled excess is returned before pots are built.
 */
import { type Rng } from '../../../core/logic/rng';
import { type PCard, shuffledDeck } from './cards';
import { evaluate } from './evaluator';
import { type Pot, type RakeConfig, buildPots, orderFromButton, rakeFor, splitPot, uncalledBet } from './pots';

export type Street = 'preflop' | 'flop' | 'turn' | 'river';
export const STREETS: readonly Street[] = ['preflop', 'flop', 'turn', 'river'];

export interface HandPlayerInit {
  id: string;
  human: boolean;
  stack: number;
}

export interface HandPlayer extends HandPlayerInit {
  readonly startStack: number;
  /** chips put in during the current street */
  bet: number;
  /** chips put in during the whole hand */
  total: number;
  hole: PCard[];
  folded: boolean;
  allIn: boolean;
  /** acted since the last full raise (reopen rule) */
  acted: boolean;
  /** voluntarily put chips in preflop (call/raise) */
  vpip: boolean;
  /** raised preflop */
  pfr: boolean;
}

export type ActionType = 'fold' | 'check' | 'call' | 'bet' | 'raise';

export type HandEvent =
  | { type: 'blind'; player: number; blind: 'sb' | 'bb'; amount: number; allIn: boolean }
  /** amount: chips added for a call, the total street bet for bet/raise */
  | { type: 'action'; player: number; action: ActionType; amount: number; allIn: boolean }
  | { type: 'street'; street: Street; cards: PCard[] };

/** Player input. `raise` covers bets too; `to` is the total street bet after the action. */
export type Action = { type: 'fold' } | { type: 'check' } | { type: 'call' } | { type: 'raise'; to: number } | { type: 'allin' };

export interface PotResult extends Pot {
  rake: number;
  winners: number[];
  /** chips each winner receives (same order as winners) */
  shares: number[];
  /** best hand value among eligible players (0 when uncontested) */
  value: number;
}

export interface HandResult {
  uncontested: boolean;
  uncalled?: { player: number; amount: number };
  pots: PotResult[];
  /** chips won per player (excluding the returned uncalled bet) */
  won: number[];
  /** net result per player vs. the start of the hand */
  net: number[];
  /** hand value per player (0 = not evaluated / folded) */
  values: number[];
  /** players who show their cards at showdown, in showing order */
  shown: number[];
  rake: number;
}

export interface HandState {
  players: HandPlayer[];
  button: number;
  sbIndex: number;
  bbIndex: number;
  sb: number;
  bb: number;
  street: Street;
  deck: PCard[];
  board: PCard[];
  /** index of the player to act, -1 when nobody (hand complete) */
  toAct: number;
  currentBet: number;
  minRaise: number;
  /** last bettor/raiser of the current street, -1 */
  aggressor: number;
  /** last aggressor of the last street that had a bet (showdown order) */
  lastAggressor: number;
  /** last preflop raiser, -1 */
  preflopRaiser: number;
  sawFlop: boolean;
  /** increments on every applied action / transition (stale form guard) */
  seq: number;
  events: HandEvent[];
  complete: boolean;
  result?: HandResult;
  rake: RakeConfig;
}

export interface StartOptions {
  sb: number;
  bb: number;
  /** index (into players) of the button */
  button: number;
  rake: RakeConfig;
  /** pre-arranged deck for tests (top = index 0) */
  deck?: PCard[];
}

/** Deal a new hand. `players` must be ≥ 2, clockwise, all with stack > 0. */
export function startHand(players: readonly HandPlayerInit[], rng: Rng, o: StartOptions): HandState {
  const n = players.length;
  if (n < 2) throw new Error('need at least 2 players');
  const ps: HandPlayer[] = players.map((p) => {
    if (!(p.stack > 0)) throw new Error(`player ${p.id} has no chips`);
    return { ...p, startStack: p.stack, bet: 0, total: 0, hole: [], folded: false, allIn: false, acted: false, vpip: false, pfr: false };
  });
  const button = ((o.button % n) + n) % n;
  const sbIndex = n === 2 ? button : (button + 1) % n;
  const bbIndex = (sbIndex + 1) % n;
  const s: HandState = {
    players: ps,
    button,
    sbIndex,
    bbIndex,
    sb: o.sb,
    bb: o.bb,
    street: 'preflop',
    deck: o.deck ? o.deck.slice() : shuffledDeck(rng),
    board: [],
    toAct: -1,
    currentBet: o.bb,
    minRaise: o.bb,
    aggressor: -1,
    lastAggressor: -1,
    preflopRaiser: -1,
    sawFlop: false,
    seq: 0,
    events: [],
    complete: false,
    rake: o.rake,
  };
  postBlind(s, sbIndex, o.sb, 'sb');
  postBlind(s, bbIndex, o.bb, 'bb');
  // Deal two hole cards each, one at a time starting left of the button.
  for (let round = 0; round < 2; round++) {
    for (let k = 1; k <= n; k++) ps[(button + k) % n]!.hole.push(draw(s));
  }
  const first = n === 2 ? sbIndex : (bbIndex + 1) % n;
  s.toAct = findNext(s, first);
  if (s.toAct < 0) endStreet(s);
  return s;
}

function draw(s: HandState): PCard {
  const c = s.deck.shift();
  if (c === undefined) throw new Error('deck exhausted');
  return c;
}

function put(p: HandPlayer, amount: number): number {
  const a = Math.max(0, Math.min(p.stack, Math.floor(amount)));
  p.stack -= a;
  p.bet += a;
  p.total += a;
  if (p.stack === 0) p.allIn = true;
  return a;
}

function postBlind(s: HandState, i: number, amount: number, blind: 'sb' | 'bb'): void {
  const p = s.players[i]!;
  const paid = put(p, amount);
  s.events.push({ type: 'blind', player: i, blind, amount: paid, allIn: p.allIn });
}

const canAct = (p: HandPlayer): boolean => !p.folded && !p.allIn;

function needsAction(s: HandState, p: HandPlayer): boolean {
  if (!canAct(p)) return false;
  if (p.bet < s.currentBet) return true;
  if (p.acted) return false;
  // Nobody else can respond to a bet: no point acting when already matched.
  const others = s.players.filter((q) => q !== p && canAct(q)).length;
  return others > 0;
}

/** First player from `start` (inclusive, clockwise) who needs to act, or -1. */
function findNext(s: HandState, start: number): number {
  const n = s.players.length;
  for (let k = 0; k < n; k++) {
    const i = (start + k) % n;
    if (needsAction(s, s.players[i]!)) return i;
  }
  return -1;
}

const live = (s: HandState): number[] => s.players.flatMap((p, i) => (p.folded ? [] : [i]));

export interface Legal {
  toCall: number;
  canCheck: boolean;
  canRaise: boolean;
  /** min total street bet for a raise (all-in if the stack is shorter) */
  minRaiseTo: number;
  /** max total street bet (all-in) */
  maxRaiseTo: number;
  /** true when currentBet is 0 (the raise is a "bet") */
  isBet: boolean;
}

/** Legal options for the player to act. */
export function legal(s: HandState, i = s.toAct): Legal {
  const p = s.players[i];
  if (!p || s.complete) return { toCall: 0, canCheck: false, canRaise: false, minRaiseTo: 0, maxRaiseTo: 0, isBet: false };
  const toCall = Math.min(p.stack, Math.max(0, s.currentBet - p.bet));
  const maxRaiseTo = p.bet + p.stack;
  const othersCanAct = s.players.some((q, j) => j !== i && canAct(q));
  const canRaise = !p.acted && maxRaiseTo > s.currentBet && othersCanAct;
  return {
    toCall,
    canCheck: p.bet >= s.currentBet,
    canRaise,
    minRaiseTo: Math.min(s.currentBet + s.minRaise, maxRaiseTo),
    maxRaiseTo,
    isBet: s.currentBet === 0,
  };
}

/** Normalize a requested action into one that is legal (used for bots/timeouts). */
export function coerce(s: HandState, a: Action): Action {
  const l = legal(s);
  switch (a.type) {
    case 'fold':
      return l.canCheck ? { type: 'check' } : a;
    case 'check':
      return l.canCheck ? a : { type: 'fold' };
    case 'call':
      return l.canCheck ? { type: 'check' } : a;
    case 'allin':
      if (l.canRaise) return a;
      return l.toCall > 0 ? { type: 'call' } : { type: 'check' };
    case 'raise': {
      if (!l.canRaise) return l.canCheck ? { type: 'check' } : { type: 'call' };
      const to = Math.max(l.minRaiseTo, Math.min(l.maxRaiseTo, Math.floor(a.to)));
      return { type: 'raise', to };
    }
  }
}

/** Apply the current player's action. Throws on an illegal action. */
export function applyAction(s: HandState, a: Action): void {
  if (s.complete || s.toAct < 0) throw new Error('no player to act');
  const i = s.toAct;
  const p = s.players[i]!;
  const l = legal(s, i);
  const pre = s.street === 'preflop';
  switch (a.type) {
    case 'fold':
      p.folded = true;
      s.events.push({ type: 'action', player: i, action: 'fold', amount: 0, allIn: false });
      break;
    case 'check':
      if (!l.canCheck) throw new Error('cannot check');
      s.events.push({ type: 'action', player: i, action: 'check', amount: 0, allIn: false });
      break;
    case 'call': {
      if (l.toCall <= 0) throw new Error('nothing to call');
      const added = put(p, l.toCall);
      if (pre) p.vpip = true;
      s.events.push({ type: 'action', player: i, action: 'call', amount: added, allIn: p.allIn });
      break;
    }
    case 'allin':
    case 'raise': {
      const to = a.type === 'allin' ? l.maxRaiseTo : Math.floor(a.to);
      if (a.type === 'allin' && to <= s.currentBet) {
        // all-in that does not exceed the bet = a call
        const added = put(p, to - p.bet);
        if (pre) p.vpip = true;
        s.events.push({ type: 'action', player: i, action: 'call', amount: added, allIn: p.allIn });
        break;
      }
      if (!l.canRaise) throw new Error('cannot raise');
      if (to > l.maxRaiseTo) throw new Error('raise exceeds stack');
      if (to < l.minRaiseTo) throw new Error(`raise below minimum ${l.minRaiseTo}`);
      if (to <= s.currentBet) throw new Error('raise must exceed the current bet');
      const increment = to - s.currentBet;
      const isBet = s.currentBet === 0;
      put(p, to - p.bet);
      if (increment >= s.minRaise) {
        s.minRaise = increment;
        for (const q of s.players) if (q !== p) q.acted = false;
      }
      s.currentBet = to;
      s.aggressor = i;
      if (pre) {
        p.vpip = true;
        p.pfr = true;
        s.preflopRaiser = i;
      }
      s.events.push({ type: 'action', player: i, action: isBet ? 'bet' : 'raise', amount: to, allIn: p.allIn });
      break;
    }
  }
  p.acted = true;
  s.seq++;
  if (live(s).length === 1) return finish(s);
  const next = findNext(s, (i + 1) % s.players.length);
  if (next >= 0) s.toAct = next;
  else endStreet(s);
}

function endStreet(s: HandState): void {
  if (s.aggressor >= 0) s.lastAggressor = s.aggressor;
  for (;;) {
    if (live(s).length <= 1) return finish(s);
    if (s.street === 'river') return finish(s);
    // collect bets, deal the next street
    for (const p of s.players) {
      p.bet = 0;
      p.acted = false;
    }
    s.currentBet = 0;
    s.minRaise = s.bb;
    s.aggressor = -1;
    draw(s); // burn
    const next = STREETS[STREETS.indexOf(s.street) + 1]!;
    const count = next === 'flop' ? 3 : 1;
    const cards: PCard[] = [];
    for (let k = 0; k < count; k++) cards.push(draw(s));
    s.board.push(...cards);
    s.street = next;
    if (next === 'flop') s.sawFlop = true;
    s.events.push({ type: 'street', street: next, cards });
    s.seq++;
    const first = findNext(s, (s.button + 1) % s.players.length);
    if (first >= 0) {
      s.toAct = first;
      return;
    }
    // nobody can act (all-in run-out): keep dealing
  }
}

/** Complete the hand: return the uncalled bet, build pots, rake, award, update stacks. */
function finish(s: HandState): void {
  s.toAct = -1;
  s.complete = true;
  s.seq++;
  const n = s.players.length;
  const totals = s.players.map((p) => p.total);
  const folded = s.players.map((p) => p.folded);
  const unc = uncalledBet(totals);
  if (unc) {
    totals[unc.player]! -= unc.amount;
    const p = s.players[unc.player]!;
    p.stack += unc.amount;
    p.total -= unc.amount;
    if (p.stack > 0) p.allIn = false;
  }
  const alive = live(s);
  const uncontested = alive.length === 1;
  const values = s.players.map((p) => (!uncontested && !p.folded ? evaluate([...p.hole, ...s.board]) : 0));
  const won = new Array<number>(n).fill(0);
  const pots: PotResult[] = buildPots(totals, folded).map((pot) => {
    const humans = pot.contributors.filter((c) => s.players[c]!.human).length;
    const rake = rakeFor(pot.amount, humans, s.sawFlop, s.bb, s.rake);
    const net = pot.amount - rake;
    let best = 0;
    for (const e of pot.eligible) best = Math.max(best, values[e]!);
    const winners = pot.eligible
      .filter((e) => uncontested || values[e] === best)
      .sort((a, b) => orderFromButton(a, s.button, n) - orderFromButton(b, s.button, n));
    const shares = splitPot(net, winners);
    winners.forEach((w, k) => (won[w]! += shares[k]!));
    return { ...pot, rake, winners, shares, value: uncontested ? 0 : best };
  });
  for (let i = 0; i < n; i++) s.players[i]!.stack += won[i]!;
  const shown = uncontested ? [] : showdownOrder(s, alive, pots);
  s.result = {
    uncontested,
    uncalled: unc,
    pots,
    won,
    net: s.players.map((p) => p.stack - p.startStack),
    values,
    shown,
    rake: pots.reduce((a, p) => a + p.rake, 0),
  };
}

/**
 * Showdown: the last aggressor shows first, else the first live player left of the button;
 * then clockwise. Later players show only if they win (or split) a pot - losers muck.
 */
function showdownOrder(s: HandState, alive: number[], pots: readonly PotResult[]): number[] {
  const n = s.players.length;
  const start = s.lastAggressor >= 0 && !s.players[s.lastAggressor]!.folded ? s.lastAggressor : -1;
  const ordered = alive.slice().sort((a, b) => orderFromButton(a, s.button, n) - orderFromButton(b, s.button, n));
  const first = start >= 0 ? start : ordered[0]!;
  const rest = ordered.filter((i) => i !== first).sort((a, b) => ((a - first + n) % n) - ((b - first + n) % n));
  const winners = new Set(pots.flatMap((p) => p.winners));
  // Everyone all-in before the river must show (no betting left); otherwise losers muck.
  const allInShowdown = alive.filter((i) => !s.players[i]!.allIn).length <= 1;
  return [first, ...rest.filter((i) => allInShowdown || winners.has(i))];
}

/** Total chips in the middle (pots + current street bets). */
export const potTotal = (s: HandState): number => s.players.reduce((a, p) => a + p.total, 0);

/** Pots as they stand now (for display), built from contributions so far. */
export function currentPots(s: HandState): Pot[] {
  return buildPots(
    s.players.map((p) => p.total),
    s.players.map((p) => p.folded),
  );
}

/** Players still in the hand who can act (not folded, not all-in). */
export const activePlayers = (s: HandState): number[] => s.players.flatMap((p, i) => (canAct(p) ? [i] : []));
/** Players still contesting the pot. */
export const livePlayers = live;
