/**
 * Rawtext builders for roulette screens (all words come from lang/roulette/*.lang).
 */
import { type Raw, chips, join, joinWith, lit, t } from '../../core';
import { type Bet, type BetType, type Spot, outsideIndex, pocketColor, spotLabel, wheelWindow } from './logic';

const K = 'gui.burmaldaholic.roulette';

/** § color per pocket. Black pockets are drawn white so they stay readable on dark forms. */
const COLOR: Record<'red' | 'black' | 'green', string> = { red: '§c', black: '§f', green: '§a' };

/** A pocket number in its color: "§c17". */
export const pocketRaw = (n: number): Raw => lit(COLOR[pocketColor(n)] + String(n) + '§r');

/** Color word of a pocket: Red / Black / Zero. */
export const pocketColorName = (n: number): Raw => t(`${K}.color.${pocketColor(n)}`);

/** Bet type button label: "Split (17:1)". */
export const betTypeLabel = (type: BetType): Raw => t(`${K}.bet.${type}`);

/** Human description of a spot: "Split 17-20", "2nd dozen (13–24)", "Red (1:1)". */
export function spotRaw(s: Spot): Raw {
  switch (s.type) {
    case 'straight':
    case 'split':
    case 'street':
    case 'trio':
    case 'corner':
    case 'six_line':
      return t(`${K}.desc.${s.type}`, spotLabel(s));
    case 'dozen':
    case 'column':
      return t(`${K}.desc.${s.type}.${outsideIndex(s)}`);
    default:
      return betTypeLabel(s.type);
  }
}

/** Dropdown entry for a position: the numbers for inside bets, the name for dozens/columns. */
export function positionRaw(s: Spot): Raw {
  if (s.type === 'dozen' || s.type === 'column') return t(`${K}.desc.${s.type}.${outsideIndex(s)}`);
  if (s.type === 'straight') return pocketRaw(s.numbers[0]!);
  return lit(spotLabel(s));
}

/** "Split 17-20 — 10 chips". */
export const betLine = (b: Bet): Raw => t(`${K}.desc.with_amount`, spotRaw(b), chips(b.amount));

/** "History: 17 0 32 …" (undefined when empty). */
export function historyRaw(history: readonly number[]): Raw | undefined {
  if (!history.length) return undefined;
  return t(`${K}.history_line`, joinWith(lit(' '), history.map(pocketRaw)));
}

/** Result line for one player: "17 Black — you win 180 chips" / "— not this time" / plain. */
export function resultRaw(result: number, staked: number, totalReturn: number): Raw {
  const n = pocketRaw(result);
  const c = pocketColorName(result);
  if (staked <= 0) return t(`${K}.result`, n, c);
  if (totalReturn > 0) return t(`${K}.result_win`, n, c, chips(totalReturn));
  return t(`${K}.result_lose`, n, c);
}

/** Action-bar strip of the wheel around `index`, the ball pocket in brackets. */
export function stripRaw(index: number): Raw {
  const nums = wheelWindow(index, 3);
  const parts: Raw[] = [];
  nums.forEach((n, i) => {
    if (i === 3) parts.push(join(lit('§e[§r'), pocketRaw(n), lit('§e]§r')));
    else parts.push(pocketRaw(n));
  });
  return joinWith(lit('  '), parts);
}
