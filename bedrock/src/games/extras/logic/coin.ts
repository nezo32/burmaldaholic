/**
 * Coin Flip (GAME_DESIGN §11.1). PURE.
 * The player picks a side; win pays `payout`:1 (floor), i.e. 1.96× total at the default 0.96.
 * RTP = 0.5 × (1 + payout) = 98 %. The Soul Wager (§4.4) pays 1:1.
 */
import { type Rng } from '../../../core/logic/rng';
import { floorPay } from './payout';

export type CoinSide = 'heads' | 'tails';
export const COIN_SIDES: readonly CoinSide[] = ['heads', 'tails'];

export interface CoinResult {
  pick: CoinSide;
  landed: CoinSide;
  win: boolean;
}

export const otherSide = (s: CoinSide): CoinSide => (s === 'heads' ? 'tails' : 'heads');

/**
 * Flip for a player who picked `pick`. `pWin` is the (possibly modified) win probability,
 * default a fair 0.5. The landed side follows from the win so the animation shows it.
 */
export function flipCoin(rng: Rng, pick: CoinSide, pWin = 0.5): CoinResult {
  const win = rng.next() < pWin;
  return { pick, landed: win ? pick : otherSide(pick), win };
}

/** Total return (stake included) for a chip / pawn stake of value `stake`. */
export function coinReturn(stake: number, win: boolean, payout: number): number {
  return win ? stake + floorPay(stake, payout) : 0;
}

/** Total return of a Soul Wager worth V (1:1). */
export const soulReturn = (value: number, win: boolean): number => (win ? 2 * value : 0);

/** RTP of the coin flip at a given payout (before flooring). */
export const coinRtp = (payout: number, pWin = 0.5): number => pWin * (1 + payout);

/** Words accepted by the Soul Wager type-to-confirm field (EN / RU of soul_confirm_word). */
export const SOUL_CONFIRM_WORDS = ['deal', 'сделка'];
export const isSoulConfirmWord = (typed: string): boolean => SOUL_CONFIRM_WORDS.includes(typed.trim().toLowerCase());
