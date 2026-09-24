/**
 * Slot rendering helpers: symbol glyphs (RP font sheet `font/glyph_E2.png`, v1 cells at U+E2F0 + index in
 * SYMBOLS order), symbol names, grid rows, paytable lines.
 */
import { type Raw, color, join, lines, lit, t } from '../../core';
import { type Grid, type SlotTable, type Sym, type Tier, REGULAR, SYMBOLS } from './logic';

/** v1 legacy cells in the slots reserve U+E2F0… (v2 owns U+E200…E244; lane B-L10 generator, legacy-v1.mjs). */
const GLYPH_BASE = 0xe2f0;

/** Private-use glyph of a symbol (drawn by packs/slots/RP/font/glyph_E2.png). */
export const glyph = (s: Sym): string => String.fromCodePoint(GLYPH_BASE + SYMBOLS.indexOf(s));

/** STRINGS.md symbol key suffix per config symbol id. */
const NAME_KEY: Record<Sym, string> = {
  berries: 'berry',
  apple: 'apple',
  golden_carrot: 'carrot',
  emerald: 'emerald',
  diamond: 'diamond',
  seven: 'seven',
  wild: 'wild',
  creeper: 'creeper',
  tnt: 'tnt',
  pearl: 'pearl',
  clock: 'clock',
  star: 'star',
};
export const symbolName = (s: Sym): Raw => t(`gui.burmaldaholic.slots.symbol.${NAME_KEY[s]}`);

export const machineName = (tier: Tier): Raw => t(`block.burmaldaholic.slot_machine_${tier}`);

/** One row of glyphs: "§f[a] [b] [c]". */
export function gridRow(row: readonly Sym[]): Raw {
  const glyphs = '§f' + row.map(glyph).join('  ') + '§r';
  return lit(glyphs);
}

/** Three glyph rows (form body / actionbar). */
export const gridRaw = (g: Grid): Raw => lines(...g.map(gridRow));

/** Paytable body lines for a machine (UI.md §6). */
export function paytableLines(table: SlotTable): Raw[] {
  const out: Raw[] = [];
  const has = (s: Sym) => table.weights[s] > 0;
  const row = (s: Sym, text: Raw) => {
    const icon = glyph(s) + ' ';
    return join(lit(icon), text);
  };
  for (const s of [...REGULAR].reverse()) if (has(s)) out.push(row(s, t('gui.burmaldaholic.slots.paytable.three', symbolName(s), table.pays[s])));
  if (has('berries')) {
    out.push(row('berries', t('gui.burmaldaholic.slots.paytable.berry_2', table.berryPartial[1])));
    out.push(row('berries', t('gui.burmaldaholic.slots.paytable.berry_1', table.berryPartial[0])));
  }
  if (has('wild')) {
    out.push(row('wild', t('gui.burmaldaholic.slots.paytable.three', symbolName('wild'), table.pays.wild)));
    out.push(row('wild', t('gui.burmaldaholic.slots.paytable.wild')));
  }
  if (has('star')) {
    if (table.progressive) {
      out.push(row('star', color('§6', t('gui.burmaldaholic.slots.paytable.star'))));
      out.push(color('§7', t('gui.burmaldaholic.slots.paytable.max_bet_jackpot')));
    } else out.push(row('star', t('gui.burmaldaholic.slots.paytable.star_owned', table.pays.star)));
  }
  for (const s of ['clock', 'pearl'] as const) {
    if (!has(s)) continue;
    out.push(row(s, t('gui.burmaldaholic.slots.paytable.three', symbolName(s), table.pays[s])));
    out.push(row(s, t('gui.burmaldaholic.slots.paytable.chaos', symbolName(s))));
  }
  for (const s of ['tnt', 'creeper'] as const) {
    if (!has(s)) continue;
    if (table.pays[s] > 0) out.push(row(s, t('gui.burmaldaholic.slots.paytable.three', symbolName(s), table.pays[s])));
    out.push(row(s, t('gui.burmaldaholic.slots.paytable.chaos', symbolName(s))));
  }
  return out;
}
