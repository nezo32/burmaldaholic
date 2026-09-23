/**
 * Rawtext rendering for the poker forms, action bar and chat (UI.md §5). All words come from
 * lang keys; literals here are only § codes, suit symbols, separators and player/bot names.
 */
import { type Card, type Raw, cardGlyph, chips, glyphRaw, color, join, joinWith, lines, lit, t } from '../../core';
import { type PCard, toCard } from './logic/cards';
import { type HandEvent, type HandState, currentPots, legal } from './logic/engine';
import { evaluate, handName } from './logic/evaluator';
import { type Seat, type TableModel } from './logic/table';

const SEP = lit(' · ');
/** Card glyph (core font sheet glyph_E1.png, U+E110–U+E143). */
export function toCardRaw(c: Card): Raw {
  return glyphRaw(cardGlyph(c));
}

export function cardsRaw(cards: readonly PCard[]): Raw {
  return joinWith(lit(' '), cards.map((c) => toCardRaw(toCard(c))));
}

export const handNameRaw = (value: number): Raw => t(`gui.burmaldaholic.poker.hand.${handName(value)}`);
export const tierRaw = (tier: string): Raw => {
  const code = tier === 'fish' ? '§b' : tier === 'shark' ? '§c' : '§e';
  return color(code, t(`gui.burmaldaholic.poker.bot.${tier}`));
};

/** "Name" for humans, "Name [Fish]" for bots. */
export function seatName(s: Pick<Seat, 'name' | 'kind' | 'tier'>): Raw {
  if (s.kind === 'bot' && s.tier) return join(lit(s.name), lit(' ['), tierRaw(s.tier), lit(']'));
  return lit(s.name);
}

export const streetRaw = (street: string): Raw => t(`gui.burmaldaholic.poker.${street}`);

/** Body text of the table screen, from the point of view of `viewerId`. */
export function tableBody(m: TableModel, viewerId: string, extra: (Raw | undefined)[] = []): Raw {
  const h = m.hand;
  const out: (Raw | undefined)[] = [];
  out.push(joinWith(SEP, [t('gui.burmaldaholic.poker.blinds', m.sb, m.bb), h ? join(lit('#'), m.handNo) : undefined, h && !h.complete ? streetRaw(h.street) : undefined]));
  if (h && h.board.length) out.push(join(t('gui.burmaldaholic.poker.board'), lit(': '), cardsRaw(h.board)));
  const me = h ? h.players.findIndex((p) => p.id === viewerId) : -1;
  if (h && me >= 0) {
    const p = h.players[me]!;
    const name = h.board.length >= 3 && !p.folded ? join(lit('  §7('), handNameRaw(evaluate([...p.hole, ...h.board])), lit(')§r')) : undefined;
    out.push(join(t('gui.burmaldaholic.poker.your_cards'), lit(': '), cardsRaw(p.hole), ...(name ? [name] : [])));
  }
  if (h && !h.complete) {
    const pots = currentPots(h);
    pots.forEach((pot, k) =>
      out.push(k === 0 ? t('gui.burmaldaholic.poker.pot', pot.amount) : t('gui.burmaldaholic.poker.side_pot', k, pot.amount)),
    );
  }
  out.push(lit('§8────────────§r'));
  for (let i = 0; i < m.seats.length; i++) {
    const s = m.seats[i];
    if (!s) continue;
    out.push(seatLine(m, h, i, s, viewerId));
  }
  for (const e of extra) if (e) out.push(e);
  return lines(...out);
}

function seatLine(m: TableModel, h: HandState | undefined, seatIdx: number, s: Seat, viewerId: string): Raw {
  const k = h ? h.players.findIndex((p) => p.id === s.id) : -1;
  const p = k >= 0 ? h!.players[k] : undefined;
  const marker = h && !h.complete && k === h.toAct ? lit('§a▶ §r') : lit('  ');
  const button = seatIdx === m.button ? lit(' §e(●)§r') : lit('');
  const who = s.id === viewerId ? color('§l', t('gui.burmaldaholic.common.you')) : seatName(s);
  const parts: (Raw | undefined)[] = [join(marker, who, button), t('gui.burmaldaholic.poker.stack', p ? p.stack : s.stack)];
  if (p && p.bet > 0 && !h!.complete) parts.push(t('gui.burmaldaholic.common.bet_amount', p.bet));
  if (p?.folded) parts.push(color('§8', t('gui.burmaldaholic.poker.folded')));
  else if (p?.allIn && !h!.complete) parts.push(color('§6', t('gui.burmaldaholic.poker.all_in_tag')));
  if (s.sittingOut) parts.push(color('§7', t('gui.burmaldaholic.poker.sitting_out')));
  return joinWith(SEP, parts);
}

/** "To call: 20" / "Your move" line for the acting player. */
export function toCallRaw(h: HandState): Raw | undefined {
  const l = legal(h);
  return l.toCall > 0 ? t('gui.burmaldaholic.poker.to_call', l.toCall) : undefined;
}

/** Action-bar text for one hand event (opponent actions stream, UI.md §5). */
export function eventRaw(m: TableModel, h: HandState, e: HandEvent): Raw {
  if (e.type === 'street') return join(streetRaw(e.street), lit(': '), cardsRaw(e.cards));
  const s = m.seatOf(h.players[e.player]!.id);
  const name = s ? seatName(s) : lit('?');
  if (e.type === 'blind') return t(e.blind === 'sb' ? 'msg.burmaldaholic.poker.action.small_blind' : 'msg.burmaldaholic.poker.action.big_blind', name, e.amount);
  if (e.allIn && e.action !== 'fold' && e.action !== 'check') return t('msg.burmaldaholic.poker.action.all_in', name, h.players[e.player]!.bet || e.amount);
  switch (e.action) {
    case 'fold':
      return t('msg.burmaldaholic.poker.action.fold', name);
    case 'check':
      return t('msg.burmaldaholic.poker.action.check', name);
    case 'call':
      return t('msg.burmaldaholic.poker.action.call', name, e.amount);
    case 'bet':
      return t('msg.burmaldaholic.poker.action.bet', name, e.amount);
    case 'raise':
      return t('msg.burmaldaholic.poker.action.raise', name, e.amount);
  }
}

/** Chat lines announcing the winners of a finished hand. */
export function resultLines(m: TableModel, h: HandState): Raw[] {
  const r = h.result;
  if (!r) return [];
  const out: Raw[] = [];
  const nameOf = (i: number): Raw => {
    const s = m.seatOf(h.players[i]!.id);
    return s ? seatName(s) : lit('?');
  };
  r.pots.forEach((pot, k) => {
    if (!pot.winners.length) return;
    const total = pot.shares.reduce((a, b) => a + b, 0);
    if (r.uncontested) {
      out.push(t('msg.burmaldaholic.poker.wins_uncontested', nameOf(pot.winners[0]!), chips(total)));
      return;
    }
    if (pot.winners.length > 1) {
      out.push(join(t('msg.burmaldaholic.poker.split_pot', chips(pot.shares[pot.shares.length - 1]!)), lit(' — '), joinWith(lit(', '), pot.winners.map(nameOf)), lit(' — '), handNameRaw(pot.value)));
      return;
    }
    const w = pot.winners[0]!;
    out.push(
      k === 0
        ? t('msg.burmaldaholic.poker.wins_pot', nameOf(w), chips(total), handNameRaw(pot.value))
        : t('msg.burmaldaholic.poker.wins_side_pot', nameOf(w), chips(total), handNameRaw(pot.value)),
    );
  });
  return out;
}

/** Showdown body: every shown hand with its name, then the winners. */
export function showdownBody(m: TableModel, h: HandState, viewerId: string): Raw {
  const r = h.result;
  const out: (Raw | undefined)[] = [];
  out.push(t('gui.burmaldaholic.poker.showdown'));
  if (h.board.length) out.push(join(t('gui.burmaldaholic.poker.board'), lit(': '), cardsRaw(h.board)));
  for (const i of r?.shown ?? []) {
    const p = h.players[i]!;
    const s = m.seatOf(p.id);
    const who = p.id === viewerId ? t('gui.burmaldaholic.common.you') : s ? seatName(s) : lit('?');
    out.push(join(who, lit(': '), cardsRaw(p.hole), lit(' — '), handNameRaw(r!.values[i]!)));
  }
  const me = h.players.findIndex((p) => p.id === viewerId);
  if (me >= 0 && r) {
    const net = r.net[me]!;
    out.push(
      net > 0
        ? color('§a', t('gui.burmaldaholic.common.result.win', net))
        : net < 0
          ? color('§c', t('gui.burmaldaholic.common.result.loss', -net))
          : color('§7', t('gui.burmaldaholic.common.result.net', 0)),
    );
  }
  out.push(lit(''));
  out.push(...resultLines(m, h));
  return lines(...out);
}
