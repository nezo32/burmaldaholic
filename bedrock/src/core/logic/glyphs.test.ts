import { describe, expect, it } from 'vitest';
import { RANKS, SUITS } from './cards';
import { CARD_BACK_GLYPH, cardCodePoint, dieGlyph, suitGlyph, vipBadgeGlyph } from './glyphs';

describe('glyph code points (UI.md §0.1)', () => {
  it('52 distinct cards in U+E110–U+E143, back at U+E144', () => {
    const cps = SUITS.flatMap((suit) => RANKS.map((rank) => cardCodePoint({ suit, rank })));
    expect(new Set(cps).size).toBe(52);
    expect(Math.min(...cps)).toBe(0xe110);
    expect(Math.max(...cps)).toBe(0xe143);
    expect(CARD_BACK_GLYPH.codePointAt(0)).toBe(0xe144);
  });
  it('suits, dice, badges', () => {
    expect(suitGlyph('C').codePointAt(0)).toBe(0xe153);
    expect(dieGlyph(1).codePointAt(0)).toBe(0xe160);
    expect(dieGlyph(6).codePointAt(0)).toBe(0xe165);
    expect(dieGlyph(9).codePointAt(0)).toBe(0xe165);
    expect(vipBadgeGlyph(5).codePointAt(0)).toBe(0xe185);
  });
});
