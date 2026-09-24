/**
 * Advancement and chaos triggers of a settled seat (GAME_DESIGN §21.7, §19). PURE.
 *  - `uth_four_x`: the seat bet ×4 preflop and its Play bet won.
 *  - `uth_royal`: the seat's hand is a royal flush and the Blind or the Trips paid on it.
 *  - royal flush paying the Blind → `diamond_rain` + server-wide broadcast.
 *  - `uth_house_seat`: dealer seat, round finished with a net profit against ≥ 2 seated players.
 */
import { handName } from '../../poker/logic/evaluator';
import type { Settlement } from './settle';

export interface SeatHooks {
  achievements: ('uth_four_x' | 'uth_royal')[];
  /** royal flush paid by the Blind: diamond_rain for the player + broadcast */
  royalBlind: boolean;
}

export function seatHooks(multiple: number, s: Settlement, playerValue: number): SeatHooks {
  const achievements: SeatHooks['achievements'] = [];
  if (multiple === 4 && s.play > 0) achievements.push('uth_four_x');
  const royal = handName(playerValue) === 'royal_flush';
  if (royal && (s.blind > 0 || s.trips > 0)) achievements.push('uth_royal');
  return { achievements, royalBlind: royal && s.blindHand === 'royal' && s.blind > 0 };
}

/** `uth_house_seat`: the bank's result after rake is positive with at least two seats. */
export const houseSeatEarned = (bankDelta: number, seats: number): boolean => bankDelta > 0 && seats >= 2;
