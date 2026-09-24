/**
 * Per-seat decision interface (PURE). Every seat decision - preflop / flop / river Play bets
 * and folds - goes through a `DecisionPolicy`, so a human (forms) and a future bot policy
 * (docs/design/BOTS.md, shared core bot framework) plug into the same table code.
 *
 * The runtime asks humans through forms and times them out to `UthRound.defaultDecision`;
 * a bot policy answers synchronously from this view (it sees only what a player would see).
 */
import type { PCard } from '../../poker/logic/cards';
import type { Decision, LegalOption, Street } from './round';
import { flopBet2, preflopBet4, riverBet1 } from './strategy';
import type { Paytable } from './paytable';

export interface SeatView {
  street: Street;
  hole: readonly PCard[];
  /** board cards visible now (0, 3 or 5) */
  board: readonly PCard[];
  ante: number;
  trips: number;
  /** legal options with amounts / affordability */
  options: readonly LegalOption[];
  blindPays: Readonly<Paytable>;
}

export interface DecisionPolicy {
  readonly id: string;
  /** Pick one of `view.options` (affordable ones only). */
  decide(view: SeatView): Decision;
}

/** Keep only affordable options; the fallback is the street's no-risk option. */
function choose(view: SeatView, wanted: Decision, fallback: Decision): Decision {
  const ok = view.options.find((o) => o.decision === wanted && o.affordable);
  return ok ? wanted : fallback;
}

/** Reference strategy R (§21.3) as a policy (tests; a natural "Normal" bot later). */
export const referencePolicy: DecisionPolicy = {
  id: 'reference',
  decide(view) {
    switch (view.street) {
      case 'preflop':
        return preflopBet4(view.hole) ? choose(view, 'bet4', 'check') : 'check';
      case 'flop':
        return flopBet2(view.hole, view.board) ? choose(view, 'bet2', 'check') : 'check';
      case 'river':
        return riverBet1(view.hole, view.board, view.blindPays) ? choose(view, 'bet1', 'fold') : 'fold';
      default:
        return 'check';
    }
  },
};
