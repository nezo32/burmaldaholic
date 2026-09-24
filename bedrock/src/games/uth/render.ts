/**
 * Rawtext rendering for Ultimate Texas Hold'em forms, chat and the action bar (UI.md §15).
 * Cards use the shared card glyphs (core font sheet, U+E110–U+E144); hand names reuse
 * `gui.burmaldaholic.poker.hand.*`, street names `gui.burmaldaholic.poker.preflop / flop`.
 */
import { CARD_BACK_GLYPH, type Raw, cardGlyph, color, decimal, formatSigned, glyphRaw, join, joinWith, lines, lit, t } from '../../core';
import { type HandName, PAY_HANDS, PAY_HAND_NAME, type PCard, type PayHand, type Paytable, type SeatState, type Settlement, type Street, handName, toCard } from './logic';

export const cardRaw = (c: PCard): Raw => glyphRaw(cardGlyph(toCard(c)));
const backRaw = (): Raw => glyphRaw(CARD_BACK_GLYPH);

/** Cards face up, then `hidden` backs. */
export function cardsRaw(cards: readonly PCard[], hidden = 0): Raw {
  const parts: Raw[] = cards.map(cardRaw);
  for (let i = 0; i < hidden; i++) parts.push(backRaw());
  return joinWith(lit(' '), parts);
}

export const handRaw = (name: HandName): Raw => t(`gui.burmaldaholic.poker.hand.${name}`);
export const valueRaw = (value: number): Raw => handRaw(handName(value));

export function streetRaw(s: Street): Raw {
  switch (s) {
    case 'preflop':
      return t('gui.burmaldaholic.poker.preflop');
    case 'flop':
      return t('gui.burmaldaholic.poker.flop');
    case 'river':
      return t('gui.burmaldaholic.uth.turn_river');
    default:
      return t('gui.burmaldaholic.uth.showdown');
  }
}

/** Odds text "500:1", "3:2" (fractions of a half), else "1,25:1" with the localized separator. */
export function oddsRaw(x: number): Raw {
  if (Number.isInteger(x)) return join(lit(x), lit(':1'));
  if (Number.isInteger(x * 2)) return join(lit(x * 2), lit(':2'));
  return join(decimal(x, 2), lit(':1'));
}

/** Public seat tag (UI.md §15: Deciding… / Checked / Play ×N / Folded). */
export function seatTagRaw(s: SeatState, deciding: boolean): Raw {
  if (s.folded) return color('§c', t('gui.burmaldaholic.uth.tag.folded'));
  if (s.multiple > 0) return color('§a', t('gui.burmaldaholic.uth.play_multiple', s.multiple));
  if (deciding) return color('§e', t('gui.burmaldaholic.uth.tag.deciding'));
  return color('§7', t('gui.burmaldaholic.uth.tag.checked'));
}

/** Bets of a seat: "Ante: 10 · Blind: 10 · Trips: 5 · Play: 40". */
export function betsRaw(ante: number, trips: number, play: number): Raw {
  return joinWith(lit(' · '), [
    t('gui.burmaldaholic.uth.ante_amount', ante),
    t('gui.burmaldaholic.uth.blind_amount', ante),
    trips > 0 ? t('gui.burmaldaholic.uth.trips_amount', trips) : undefined,
    play > 0 ? t('gui.burmaldaholic.uth.play_amount', play) : undefined,
  ]);
}

const betLine = (name: string, delta: number, placed: boolean): Raw | undefined => {
  if (!placed) return undefined;
  const label = t(`gui.burmaldaholic.uth.${name}`);
  if (delta > 0) return color('§a', t('gui.burmaldaholic.uth.line.win', label, delta));
  if (delta < 0) return color('§c', t('gui.burmaldaholic.uth.line.lose', label, -delta));
  return color('§7', t('gui.burmaldaholic.uth.line.push', label));
};

/** Per-bet result lines (Play · Ante · Blind · Trips) and the headline. */
export function settlementLines(s: Settlement, bets: { ante: number; trips: number; play: number }, playerValue: number, dealerValue: number, pays: { blind: Readonly<Paytable>; trips: Readonly<Paytable> }): Raw {
  const head =
    s.outcome === 'win'
      ? color('§a', t('gui.burmaldaholic.uth.result.win', valueRaw(playerValue)))
      : s.outcome === 'lose'
        ? color('§c', t('gui.burmaldaholic.uth.result.lose', valueRaw(dealerValue)))
        : s.outcome === 'tie'
          ? color('§7', t('gui.burmaldaholic.uth.result.tie'))
          : color('§c', t('gui.burmaldaholic.uth.result.folded'));
  const blind =
    s.blind > 0 && s.blindHand
      ? color('§a', t('gui.burmaldaholic.uth.line.blind_bonus', handRaw(PAY_HAND_NAME[s.blindHand]), oddsRaw(pays.blind[s.blindHand] ?? 0), s.blind))
      : betLine('blind', s.blind, true);
  const trips =
    s.trips > 0 && s.tripsHand
      ? color('§a', t('gui.burmaldaholic.uth.line.trips_bonus', handRaw(PAY_HAND_NAME[s.tripsHand]), oddsRaw(pays.trips[s.tripsHand] ?? 0), s.trips))
      : betLine('trips', s.trips, bets.trips > 0);
  return lines(
    head,
    betLine('play', s.play, bets.play > 0 && s.outcome !== 'folded'),
    betLine('ante', s.ante, true),
    blind,
    trips,
    netRaw(s.net),
  );
}

export function netRaw(net: number): Raw {
  return color(net > 0 ? '§a' : net < 0 ? '§c' : '§7', t('gui.burmaldaholic.common.result.net', formatSigned(net)));
}

/** Paytable overlay (UI.md §15): Blind and Trips tables from the effective config. */
export function paytableRaw(blind: Readonly<Paytable>, trips: Readonly<Paytable>): Raw {
  const rows = (p: Readonly<Paytable>): Raw[] =>
    PAY_HANDS.filter((h: PayHand) => (p[h] ?? 0) > 0).map((h) => t('gui.burmaldaholic.uth.paytable.row', handRaw(PAY_HAND_NAME[h]), oddsRaw(p[h]!)));
  return lines(
    color('§e', t('gui.burmaldaholic.uth.paytable.blind')),
    ...rows(blind),
    color('§7', t('gui.burmaldaholic.uth.paytable.blind_lower')),
    lit(''),
    color('§e', t('gui.burmaldaholic.uth.paytable.trips')),
    ...rows(trips),
  );
}

/** Rules summary (bet form / paytable page). */
export function rulesRaw(allow3x: boolean): Raw {
  return lines(
    t('gui.burmaldaholic.uth.rules.1'),
    t(allow3x ? 'gui.burmaldaholic.uth.rules.2' : 'gui.burmaldaholic.uth.rules.2_no3x'),
    t('gui.burmaldaholic.uth.rules.3'),
    t('gui.burmaldaholic.uth.rules.4'),
    t('gui.burmaldaholic.uth.rules.5'),
  );
}
