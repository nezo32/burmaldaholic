/**
 * THE decision interface every game implements for its bots (PURE, in the game's own `logic/`):
 * poker, chemin de fer, UTH, blackjack, the atmosphere bettors of roulette / craps / baccarat and the
 * PvP modes with decisions. `V` = what a human in that seat sees (never hidden cards, shoe order or a
 * tape); `A` = the same action type human input produces. `legalize` is the mandatory last step
 * (drivers call `botAct`). Java twin: core/bots/logic/BotPolicy.java.
 */
import type { Rng } from '../rng';
import type { BotProfile } from './types';

/** Resumable heavy work (Monte-Carlo, enumeration); driven by core/bots/jobs.ts under `system.runJob`. */
export interface BotWork {
  /** do at most `budget` units; returns units used */
  step(budget: number): number;
  done(): boolean;
  /** current (possibly partial) result, handed to `decide` */
  result(): unknown;
  /** units completed (the ≥ 50-sample rule of BOTS.md §4.3) */
  progress(): number;
}

export interface BotPolicy<V, A> {
  /** `work` = finished (or deadline-cut) BotWork result, undefined when none / failed */
  decide(bot: BotProfile, view: V, work: unknown, rng: Rng): A;
  legalize(view: V, action: A): A;
  /** optional heavy job for this decision */
  work?(bot: BotProfile, view: V, rng: Rng): BotWork | undefined;
}

/** decide + legalize: the only entry point drivers use. */
export function botAct<V, A>(policy: BotPolicy<V, A>, bot: BotProfile, view: V, work: unknown, rng: Rng): A {
  return policy.legalize(view, policy.decide(bot, view, work, rng));
}
