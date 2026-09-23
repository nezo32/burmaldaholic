/**
 * Rawtext rendering of a blackjack table for form bodies and chat (UI.md §0.3, §4):
 *   Dealer: [A♠] [?]  Total: 11
 *   Seat 2 — Alex ◀
 *     ▶ [8♦] [8♠]  Total: 16  Bet: 100 chips
 * Cards are text "glyphs" (rank letter + suit symbol, red suits in §c) until the shared card
 * font sheet (U+E110–U+E14F) exists in core.
 */
import {
  type Card,
  type Raw,
  chips,
  color,
  formatSigned,
  join,
  joinWith,
  lines,
  lit,
  rankLabel,
  t,
} from '../../core';
import { type BlackjackRound, type Hand, type SeatState, displayTotals } from './logic';

const SUIT_SYMBOL: Record<Card['suit'], string> = { S: '♠', H: '♥', D: '♦', C: '♣' };
const RED = new Set<Card['suit']>(['H', 'D']);
const GAP = lit('  ');

export function cardRaw(c: Card): Raw {
  return join(lit(RED.has(c.suit) ? '§c[' : '['), rankLabel(c.rank), lit(SUIT_SYMBOL[c.suit] + ']§r'));
}

export const hiddenCardRaw = (): Raw => lit('§7[?]§r');

export function cardsRaw(cards: readonly Card[], hidden = 0): Raw {
  const parts: Raw[] = cards.map(cardRaw);
  for (let i = 0; i < hidden; i++) parts.push(hiddenCardRaw());
  return joinWith(lit(' '), parts);
}

export function totalRaw(cards: readonly Card[]): Raw {
  const d = displayTotals(cards);
  return d.low !== undefined ? t('gui.burmaldaholic.blackjack.total_soft', d.low, d.high) : t('gui.burmaldaholic.blackjack.total', d.high);
}

/** Result banner of one settled hand ("Blackjack! +15", "Bust"...). */
export function outcomeRaw(h: Hand): Raw | undefined {
  const profit = h.ret - h.bet;
  switch (h.outcome) {
    case undefined:
      return undefined;
    case 'blackjack':
      return color('§a', t('gui.burmaldaholic.blackjack.result.blackjack', profit));
    case 'win':
      return color('§a', t('gui.burmaldaholic.blackjack.result.win', profit));
    case 'dealer_bust':
      return color('§a', t('gui.burmaldaholic.blackjack.result.dealer_bust', profit));
    case 'even_money':
      return color('§a', t('gui.burmaldaholic.blackjack.result.even_money', profit));
    case 'push':
      return color('§7', t('gui.burmaldaholic.blackjack.result.push'));
    case 'surrender':
      return color('§7', t('gui.burmaldaholic.blackjack.result.surrender'));
    case 'bust':
      return color('§c', t('gui.burmaldaholic.blackjack.result.bust'));
    case 'lose':
      return color('§c', t('gui.burmaldaholic.blackjack.result.lose'));
    case 'dealer_blackjack':
      return color('§c', t('gui.burmaldaholic.blackjack.result.dealer_blackjack'));
  }
}

/** Insurance result once the hole card is known. */
export function insuranceRaw(r: BlackjackRound, s: SeatState): Raw | undefined {
  if (!s.insurance) return undefined;
  if (!r.peeked) return t('gui.burmaldaholic.blackjack.insurance_line', chips(s.insurance));
  return s.insuranceReturn > 0
    ? color('§a', t('gui.burmaldaholic.blackjack.result.insurance_paid', s.insuranceReturn - s.insurance))
    : color('§c', t('gui.burmaldaholic.blackjack.result.insurance_lost'));
}

export function dealerRaw(r: BlackjackRound): Raw {
  const shown = r.holeRevealed ? r.dealer : [r.upCard];
  return t('gui.burmaldaholic.blackjack.dealer_line', cardsRaw(shown, r.holeRevealed ? 0 : 1), totalRaw(shown));
}

function handRaw(h: Hand, index: number, count: number, active: boolean): Raw {
  return join(
    lit(active ? '§e▶§r ' : '   '),
    joinWith(GAP, [
      count > 1 ? t('gui.burmaldaholic.blackjack.hand', index + 1) : undefined,
      cardsRaw(h.cards),
      totalRaw(h.cards),
      t('gui.burmaldaholic.common.bet_amount', chips(h.bet)),
      outcomeRaw(h),
    ]),
  );
}

/** One seat: header line plus one line per hand (and insurance). */
export function seatRaw(r: BlackjackRound, s: SeatState, name: string, mine: boolean): Raw {
  const cur = r.current();
  const acting = cur?.seat === s;
  const header = t('gui.burmaldaholic.blackjack.seat_player', s.seat, lit(name));
  const out: (Raw | undefined)[] = [join(mine ? color('§e', header) : header, lit(acting ? ' §e◀§r' : ''))];
  s.hands.forEach((h, i) => out.push(handRaw(h, i, s.hands.length, acting && cur?.handIndex === i)));
  out.push(insuranceRaw(r, s));
  return lines(...out);
}

/** Whole table: dealer line, then every seat in the round. */
export function tableRaw(r: BlackjackRound, names: ReadonlyMap<number, string>, viewerSeat?: number): Raw {
  return lines(dealerRaw(r), lit(''), ...r.seats.map((s) => seatRaw(r, s, names.get(s.seat) ?? '?', s.seat === viewerSeat)));
}

/** "Net: +15" colored by sign. */
export function netRaw(net: number): Raw {
  return color(net > 0 ? '§a' : net < 0 ? '§c' : '§7', t('gui.burmaldaholic.common.result.net', formatSigned(net)));
}

/** Chat summary of one seat: "Blackjack: Win +10 · Bust · Net: 0". */
export function summaryRaw(r: BlackjackRound, s: SeatState): Raw {
  const parts = [...s.hands.map(outcomeRaw), insuranceRaw(r, s), netRaw(r.returnOf(s.seat) - r.stakedOf(s.seat))];
  return t('msg.burmaldaholic.blackjack.result_line', joinWith(lit(' · '), parts));
}
