/**
 * THE contract of a PvP mode (PVP.md §3.14). PURE, engine-free; identical test vectors in both
 * editions (PVP.md §16). Java twin: core/pvp/logic/PvpMode.java.
 * A mode lives in the module that owns its solo game (extras: coin / wheel / plinko / scratch; slots:
 * slots) and is registered with `ctx.pvp.registerMode(mode)` in that module's onWorldLoad. The core engine
 * (core/pvp) owns everything else: invites, lobbies, escrow, rake, persistence, play-out, rivalry,
 * taunts, rematch, reveal choreography, bots and seating.
 */
import type { Rng } from '../rng';
import type { BotLevel } from '../bots/types';

export type PvpModeId = 'coin' | 'slots' | 'wheel' | 'plinko' | 'scratch';
export type AnchorKind = 'none' | 'slot_machine' | 'wheel_of_fortune' | 'plinko_machine';

/** Fair game randomness for tapes (never odds-adjusted, never the bot rng). Production: mathRng. */
export type PvpRng = Rng;

/** A mode event (kaboom, swap, time_warp, underdog, edge, creeper, foot, by_a_hair …). */
export interface PvpEvent {
  kind: string;
  /** participant index, -1 = none */
  seat: number;
  /** 0-based round / step, -1 = the match */
  round: number;
  data?: Record<string, number>;
}

export interface Outcome {
  points: number[];
  /** participant indices best → worst */
  rankOrder: number[];
  /** participants sharing the pot (≥ 1) */
  winners: number[];
  /** the tape's seat order (odd chips, tie-breaks) */
  seatOrder: number[];
  events: PvpEvent[];
}

/** One reveal step; only revealed data leaves the engine (PVP.md §3.6 tape confidentiality). */
export interface Step {
  kind: string;
  ticks: number;
  round: number;
  /** wait up to `ticks` or until every online human pressed Spin! / Drop! / Scratch! */
  waitForAll: boolean;
  data?: Record<string, unknown>;
}

/**
 * Public view for one bot decision (PVP.md §3.15.4, BOTS.md §4.8). Decisions:
 * `coin.don_offer` (1 = double, 0 = walk away), `coin.side` (0 heads, 1 tails), `coin.let_it_ride`
 * (1 ride, 0 take), `wheel.stake` (chips), `wheel.top_up` (extra chips, 0 = none).
 */
export interface DecisionView {
  decision: string;
  seat: number;
  link: number;
  deficit: number;
  minStake: number;
  cap: number;
  currentStake: number;
  medianHumanStake: number;
  ticksLeft: number;
}

export interface PvpMode<P, T> {
  readonly id: PvpModeId;
  readonly minPlayers: number;
  /** current max (reads `pvp.<id>.maxPlayers` via the provided config getter) */
  maxPlayers(): number;
  readonly anchor: AnchorKind;
  /** false only for Wheel Party */
  readonly equalStakes: boolean;
  /** `pvp.enabled && pvp.<id>.enabled` */
  enabled(): boolean;
  /** error translation key, or undefined */
  validate(params: P): string | undefined;
  defaults(): P;
  /** ALL randomness incl. the seat order, from the FAIR game rng; independent of which seats are bots */
  draw(rng: PvpRng, players: number, params: P): T;
  score(tape: T, stakes: readonly number[], params: P): Outcome;
  timeline(tape: T, outcome: Outcome, params: P): Step[];
  /** only modes with decisions (coin, wheel); bot rng + public view only */
  botDecide?(view: DecisionView, level: BotLevel, rng: Rng): number;
  /** JSON-safe persistence (the whole match record stays < 2 000 chars) */
  encodeParams(params: P): unknown;
  decodeParams(json: unknown): P;
  encodeTape(tape: T): unknown;
  decodeTape(json: unknown): T;
}

/** Config access handed to a mode factory (ctx.config satisfies it; tests pass a stub). */
export interface PvpConfigReader {
  int(key: string): number;
  bool(key: string): boolean;
}

/** Type-erased mode for registries. */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type AnyPvpMode = PvpMode<any, any>;
