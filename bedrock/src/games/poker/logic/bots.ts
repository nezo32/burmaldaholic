/**
 * Poker bots: Fish / Regular / Shark (GAME_DESIGN §7.4). PURE.
 * A bot sees only its own cards and public information (no collusion). The game builds a
 * BotView with `botView()`, runs the Monte-Carlo equity (spread over ticks) when
 * `needsEquity()` says so, then calls `decideBot()`.
 */
import { type Rng, weightedPick } from '../../../core/logic/rng';
import { type PCard, rankOf, suitOf } from './cards';
import { type Action, type HandState, type Street, legal, livePlayers, potTotal } from './engine';
import { TOP40_CHEN, chen } from './equity';
import { categoryOf, evaluate } from './evaluator';

export const BOT_TIERS = ['fish', 'regular', 'shark'] as const;
export type BotTier = (typeof BOT_TIERS)[number];

/** Fixed, non-localized bot names (GAME_DESIGN §7.4). */
export const BOT_NAMES = [
  'Lucky Steve',
  'Grandpa Pavel',
  'Creeper42',
  'Mr. Blocksworth',
  'Diamond Dave',
  'Aunt Zoya',
  'Redstone Rick',
  'Nether Nick',
  'Emerald Emma',
  'Sir Oinksalot',
  'Baba Valya',
  'Enderman Ed',
] as const;

export type Position = 'early' | 'middle' | 'late' | 'sb' | 'bb';

export interface BotView {
  hole: PCard[];
  board: PCard[];
  street: Street;
  /** all chips in the middle (pots + current street bets) */
  pot: number;
  toCall: number;
  stack: number;
  bet: number;
  currentBet: number;
  bb: number;
  canCheck: boolean;
  canRaise: boolean;
  minRaiseTo: number;
  maxRaiseTo: number;
  position: Position;
  /** preflop: players who just called the big blind */
  limpers: number;
  /** preflop: someone raised above the big blind */
  facingRaise: boolean;
  /** this bot made the last preflop raise */
  preflopRaiser: boolean;
  /** opponents still in the hand */
  opponents: number;
  /** highest VPIP (0..1) among live human opponents (sharks adapt), if known */
  loosestHumanVpip?: number;
}

/** Preflop position of player i (early = first 2 to act, late = cutoff/button). */
export function positionOf(s: HandState, i: number): Position {
  const n = s.players.length;
  if (n === 2) return i === s.button ? 'sb' : 'bb';
  if (i === s.sbIndex) return 'sb';
  if (i === s.bbIndex) return 'bb';
  if (i === s.button) return 'late';
  const first = (s.bbIndex + 1) % n;
  const order = (i - first + n) % n;
  if (order < 2) return 'early';
  if ((i + 1) % n === s.button) return 'late';
  return 'middle';
}

/** Public view for the bot in seat i. `vpip` maps player id -> VPIP of humans (0..1). */
export function botView(s: HandState, i: number, vpip?: ReadonlyMap<string, number>): BotView {
  const p = s.players[i]!;
  const l = legal(s, i);
  const liveOpp = livePlayers(s).filter((j) => j !== i);
  let loosest: number | undefined;
  if (vpip) {
    for (const j of liveOpp) {
      const q = s.players[j]!;
      const v = q.human ? vpip.get(q.id) : undefined;
      if (v !== undefined) loosest = Math.max(loosest ?? 0, v);
    }
  }
  return {
    hole: p.hole.slice(),
    board: s.board.slice(),
    street: s.street,
    pot: potTotal(s),
    toCall: l.toCall,
    stack: p.stack,
    bet: p.bet,
    currentBet: s.currentBet,
    bb: s.bb,
    canCheck: l.canCheck,
    canRaise: l.canRaise,
    minRaiseTo: l.minRaiseTo,
    maxRaiseTo: l.maxRaiseTo,
    position: positionOf(s, i),
    limpers: s.street === 'preflop' && s.currentBet <= s.bb ? s.players.filter((q, j) => j !== i && q.vpip && !q.pfr).length : 0,
    facingRaise: s.street === 'preflop' && s.currentBet > s.bb,
    preflopRaiser: s.preflopRaiser === i,
    opponents: liveOpp.length,
    loosestHumanVpip: loosest,
  };
}

/**
 * Opponent ranges for the shark's equity: live opponents who raised preflop are assumed to
 * hold a top-40 % Chen hand.
 */
export function opponentRanges(s: HandState, i: number): (number | undefined)[] {
  return livePlayers(s)
    .filter((j) => j !== i)
    .map((j) => (s.players[j]!.pfr ? TOP40_CHEN() : undefined));
}

/** Monte-Carlo samples a tier needs on this street (0 = no simulation). */
export function samplesFor(tier: BotTier, street: Street, cfg: { regularSamples: number; sharkSamples: number }): number {
  if (tier === 'fish' || street === 'preflop') return 0;
  return tier === 'regular' ? cfg.regularSamples : cfg.sharkSamples;
}

/** Pick a tier from Fish/Regular/Shark percentages (normalized; all zero = Regular). */
export function pickTier(rng: Rng, mix: readonly number[]): BotTier {
  const w = BOT_TIERS.map((t, k) => [t, Math.max(0, Number(mix[k]) || 0)] as const);
  if (!w.some(([, x]) => x > 0)) return 'regular';
  return weightedPick(rng, w);
}

/** Total street bet for a bet/raise of `fraction` of the pot. */
export function sizeTo(v: BotView, fraction: number): number {
  if (v.currentBet === 0) return Math.max(v.bb, Math.round(v.pot * fraction));
  return Math.round(v.currentBet + fraction * (v.pot + v.toCall));
}

const raise = (to: number): Action => ({ type: 'raise', to: Math.floor(to) });
const passive = (v: BotView): Action => (v.canCheck ? { type: 'check' } : { type: 'fold' });
const callOrCheck = (v: BotView): Action => (v.toCall > 0 ? { type: 'call' } : { type: 'check' });
const potOdds = (v: BotView): number => (v.toCall > 0 ? v.toCall / (v.pot + v.toCall) : 0);

/** Made-hand category that uses at least one hole card (0 = nothing beyond the board). */
export function madeCategory(hole: readonly PCard[], board: readonly PCard[]): number {
  if (board.length < 3) return 0;
  const all = categoryOf(evaluate([...hole, ...board]));
  return all > boardCategory(board) ? all : 0;
}

function boardCategory(board: readonly PCard[]): number {
  if (board.length >= 5) return categoryOf(evaluate(board.slice(0, 7)));
  const counts = new Map<number, number>();
  for (const c of board) counts.set(rankOf(c), (counts.get(rankOf(c)) ?? 0) + 1);
  const n = [...counts.values()].sort((a, b) => b - a);
  if (n[0] === 4) return 7;
  if (n[0] === 3) return n[1] === 2 ? 6 : 3;
  if (n[0] === 2) return n[1] === 2 ? 2 : 1;
  return 0;
}

const isPocketPair = (h: readonly PCard[]): boolean => rankOf(h[0]!) === rankOf(h[1]!);
const isSuited = (h: readonly PCard[]): boolean => suitOf(h[0]!) === suitOf(h[1]!);
const isConnected = (h: readonly PCard[]): boolean => Math.abs(rankOf(h[0]!) - rankOf(h[1]!)) === 1;
const hasAce = (h: readonly PCard[]): boolean => rankOf(h[0]!) === 14 || rankOf(h[1]!) === 14;

// ---- Fish ---------------------------------------------------------------------------------

function fish(v: BotView, rng: Rng): Action {
  const jitter = 0.9 + rng.next() * 0.2;
  if (v.street === 'preflop') {
    const c = chen(v.hole[0]!, v.hole[1]!);
    if (c >= 12 && v.currentBet < 3 * v.bb) return raise(3 * v.bb * jitter);
    if (v.facingRaise) {
      if (isPocketPair(v.hole) && v.currentBet <= 10 * v.bb) return callOrCheck(v);
      if (c >= 6 && v.toCall <= 0.2 * (v.stack + v.bet)) return callOrCheck(v);
      return passive(v);
    }
    if (c >= 4) return callOrCheck(v);
    return passive(v);
  }
  const made = madeCategory(v.hole, v.board);
  if (v.toCall > 0) return made >= 1 && v.toCall <= v.pot ? { type: 'call' } : { type: 'fold' };
  if (made >= 2) return raise(sizeTo(v, 0.5 * jitter));
  if (made === 0 && rng.next() < 0.05) return raise(sizeTo(v, 0.5 * jitter));
  return { type: 'check' };
}

// ---- Regular / Shark preflop ---------------------------------------------------------------

function openThreshold(pos: Position): number {
  switch (pos) {
    case 'early':
      return 8;
    case 'middle':
    case 'bb':
      return 7;
    case 'late':
    case 'sb':
      return 6;
  }
}

function preflopSolid(v: BotView, rng: Rng, shark: boolean): Action {
  const c = chen(v.hole[0]!, v.hole[1]!);
  const d = shark ? 1 : 0;
  const widen = shark && (v.loosestHumanVpip ?? 0) > 0.4 ? 1 : 0;
  if (!v.facingRaise) {
    if (c >= openThreshold(v.position) - d) return raise(3 * v.bb + v.limpers * v.bb);
    return passive(v);
  }
  if (c >= 11 - d) return raise(3 * v.currentBet);
  if (shark && isSuited(v.hole) && (isConnected(v.hole) || hasAce(v.hole)) && rng.next() < 0.08) return raise(3 * v.currentBet);
  if (c >= 9 - d - widen) return callOrCheck(v);
  return passive(v);
}

// ---- Regular / Shark postflop --------------------------------------------------------------

function regularPost(v: BotView, e: number, rng: Rng): Action {
  if (e >= 0.65) return raise(sizeTo(v, 0.66));
  if (v.toCall > 0) return e >= potOdds(v) + 0.05 ? { type: 'call' } : { type: 'fold' };
  if (v.street === 'flop' && v.preflopRaiser && rng.next() < 0.3) return raise(sizeTo(v, 0.5));
  return { type: 'check' };
}

function sharkPost(v: BotView, e: number, rng: Rng): Action {
  const size = () => sizeTo(v, 0.5 + rng.next() * 0.25);
  if (e >= 0.85) {
    const r = rng.next();
    if (r < 0.2) return { type: 'allin' };
    if (r < 0.35) return callOrCheck(v);
    return raise(sizeTo(v, 0.75));
  }
  if (e >= 0.6) return v.toCall > 0 ? { type: 'call' } : raise(size());
  if (v.street === 'flop' && e >= 0.3 && rng.next() < 0.4) return raise(size());
  if (v.toCall > 0) return e >= potOdds(v) ? { type: 'call' } : { type: 'fold' };
  return { type: 'check' };
}

/** One step less aggressive: raise -> call/check, call -> fold/check. */
function lower(v: BotView, a: Action): Action {
  if (a.type === 'raise' || a.type === 'allin') return callOrCheck(v);
  if (a.type === 'call') return passive(v);
  return a;
}

/** Make an action legal for the view (clamp sizes, raise -> call when raising is closed). */
export function legalize(v: BotView, a: Action): Action {
  switch (a.type) {
    case 'raise':
    case 'allin': {
      if (!v.canRaise) return callOrCheck(v);
      if (a.type === 'allin') return a;
      const to = Math.max(v.minRaiseTo, Math.min(v.maxRaiseTo, Math.floor(a.to)));
      if (to <= v.currentBet) return callOrCheck(v);
      return to >= v.maxRaiseTo ? { type: 'allin' } : { type: 'raise', to };
    }
    case 'call':
      return v.toCall > 0 ? a : { type: 'check' };
    case 'check':
      return v.canCheck ? a : { type: 'fold' };
    case 'fold':
      return v.canCheck ? { type: 'check' } : a;
  }
}

/**
 * The bot's action. `equity` is the Monte-Carlo result (required for Regular/Shark after the
 * flop; pass undefined otherwise).
 */
export function decideBot(tier: BotTier, v: BotView, equity: number | undefined, rng: Rng): Action {
  let a: Action;
  if (tier === 'fish') a = fish(v, rng);
  else if (v.street === 'preflop') a = preflopSolid(v, rng, tier === 'shark');
  else if (tier === 'regular') a = regularPost(v, equity ?? 0, rng);
  else a = sharkPost(v, equity ?? 0, rng);
  if (tier === 'regular' && rng.next() < 0.1) a = lower(v, a);
  return legalize(v, a);
}
