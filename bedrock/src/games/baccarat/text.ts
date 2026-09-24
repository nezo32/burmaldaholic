/**
 * Rawtext rendering for baccarat forms, chat and the action bar (UI.md §14):
 *   Player: [8♠] [K♥]  8 · Banker: [9♦] [7♣]  6
 * Cards use the shared core card glyphs (font sheet glyph_E1.png, U+E110–U+E144); glyphs are
 * never put into lang strings.
 */
import { CARD_BACK_GLYPH, type Card, type Raw, cardGlyph, chips, color, decimal, glyphRaw, join, joinWith, lit, t } from '../../core';
import { type Bead, type Box, type Coup, type PayRules, type SlipError, type ChemmyBetError, handTotal } from './logic';

export const K = 'gui.burmaldaholic.baccarat';

const cardRaw = (c: Card): Raw => glyphRaw(cardGlyph(c));

/** Cards with `hidden` face-down placeholders after them. */
export function cardsRaw(cards: readonly Card[], hidden = 0): Raw {
  const parts: Raw[] = cards.map(cardRaw);
  for (let i = 0; i < hidden; i++) parts.push(glyphRaw(CARD_BACK_GLYPH));
  return joinWith(lit(' '), parts);
}

export const sideName = (side: 'player' | 'banker'): Raw => t(`${K}.${side}`);

/** "Player: [8♠] [K♥]  8" — `shown` cards of the hand, the rest face down (2 minimum). */
export function handRaw(side: 'player' | 'banker', cards: readonly Card[], shown = cards.length): Raw {
  const vis = cards.slice(0, shown);
  const hidden = Math.max(0, Math.min(2, cards.length) - vis.length);
  const total = vis.length ? join(lit('  '), lit(handTotal(vis))) : undefined;
  return t(`${K}.hand`, sideName(side), join(cardsRaw(vis, hidden), ...(total ? [total] : [])));
}

export function coupRaw(c: Coup, shownPlayer = c.player.length, shownBanker = c.banker.length): Raw {
  return join(handRaw('player', c.player, shownPlayer), lit(' §7·§r '), handRaw('banker', c.banker, shownBanker));
}

/** Action-bar summary for spectators: "Player [8♠][K♥] 8 · Banker [9♦][?]". */
export function actionbarRaw(c: Coup, shownPlayer: number, shownBanker: number): Raw {
  const side = (cards: readonly Card[], n: number) => {
    const vis = cards.slice(0, n);
    return join(cardsRaw(vis, Math.max(0, Math.min(2, cards.length) - vis.length)), vis.length ? join(lit(' '), lit(handTotal(vis))) : lit(''));
  };
  return t(`${K}.actionbar`, side(c.player, shownPlayer), side(c.banker, shownBanker));
}

/** "Banker wins 7 to 5" / "Tie at 6". */
export function resultRaw(c: Coup): Raw {
  if (c.winner === 'tie') return t(`${K}.result.tie`, c.playerTotal);
  return c.winner === 'player' ? t(`${K}.result.player`, c.playerTotal, c.bankerTotal) : t(`${K}.result.banker`, c.bankerTotal, c.playerTotal);
}

export function pairsRaw(c: Coup): Raw | undefined {
  const parts: Raw[] = [];
  if (c.playerPair) parts.push(t(`${K}.result.pair_player`));
  if (c.bankerPair) parts.push(t(`${K}.result.pair_banker`));
  return parts.length ? color('§6', joinWith(lit(' '), parts)) : undefined;
}

export const boxName = (b: Box): Raw => t(`${K}.${b}`);

const pct = (c: number): Raw => decimal(Math.round(c * 1000) / 10);

/** Button label with the payout ("Banker (1:1 −5 %)"). */
export function boxButton(b: Box, r: PayRules): Raw {
  switch (b) {
    case 'player':
      return t(`${K}.bet.player`);
    case 'banker':
      return t(`${K}.bet.banker`, pct(r.commission));
    case 'tie':
      return t(`${K}.bet.tie`, r.tiePays);
    case 'player_pair':
      return t(`${K}.bet.player_pair`, r.pairPays);
    case 'banker_pair':
      return t(`${K}.bet.banker_pair`, r.pairPays);
  }
}

export const commissionPct = pct;

export const betLine = (b: Box, amount: number): Raw => t(`${K}.bet_line`, boxName(b), chips(amount));

/** Per-bet result line: "Tie: +40", "Player: −50", "Banker: returned". */
export function betResultLine(name: Raw, stake: number, ret: number): Raw {
  if (ret > stake) return color('§a', t(`${K}.line.win`, name, chips(ret - stake)));
  if (ret === stake) return color('§7', t(`${K}.line.push`, name));
  return color('§c', t(`${K}.line.lose`, name, chips(stake - ret)));
}

const BEAD_COLOR = { player: '§9', banker: '§c', tie: '§a' } as const;

/** Bead letters with colors, oldest first; a pair adds a gold dot after the letter. */
export function beadsRaw(beads: readonly Bead[]): Raw | undefined {
  if (!beads.length) return undefined;
  return joinWith(
    lit(' '),
    beads.map((b) => join(color(BEAD_COLOR[b.winner], t(`${K}.bead.${b.winner}`)), lit(b.playerPair || b.bankerPair ? '§6·§r' : ''))),
  );
}

export function slipErrorText(e: SlipError): Raw {
  switch (e.code) {
    case 'invalid_amount':
      return t('gui.burmaldaholic.error.invalid_amount');
    case 'pairs_off':
      return t(`${K}.error.pairs_off`);
    case 'bet_too_low':
      return t('gui.burmaldaholic.error.bet_too_low', chips(e.min));
    case 'banker_step':
      return t(`${K}.error.banker_step`, e.step);
    case 'side_max':
      return t(`${K}.error.side_max`, chips(e.max));
    case 'total_max':
      return t(`${K}.error.total_max`, chips(e.max));
    case 'min_total':
      return t(`${K}.error.min_total`, chips(e.min));
  }
}

export function chemmyErrorText(e: ChemmyBetError): Raw {
  switch (e.code) {
    case 'invalid_amount':
      return t('gui.burmaldaholic.error.invalid_amount');
    case 'bet_too_low':
      return t('gui.burmaldaholic.error.bet_too_low', chips(e.min));
    case 'total_max':
      return t(`${K}.error.total_max`, chips(e.max));
    case 'coverage':
      return t(`${K}.chemmy.error.coverage`, chips(e.open));
    case 'banco_taken':
      return t(`${K}.no_more_bets`);
    case 'banco_funds':
      return t(`${K}.chemmy.error.banco_funds`, chips(e.need));
  }
}
