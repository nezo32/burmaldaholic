// Glyph sheets (docs/architecture/animation.md §2.2, §6): `glyph_xx.png` pages are 16 × 16 cells, the glyph for
// U+XXRC sits in row R, column C. Java reads the PNG through a `bitmap` font provider
// (`assets/burmaldaholic/font/default.json`, lane J-L1) whose `chars` rows this module also builds.
import { blit, drawGrid, image } from './grid.mjs';

/**
 * A glyph page for code points U+PP00–U+PPFF. `cell` px per glyph (16 for E1, 32 for the slot planes).
 * `draw(cp, fn)` hands `fn` a cell-local painter; `used` records every drawn code point.
 */
export function glyphPage(page, cell = 16) {
  const img = image(cell * 16, cell * 16);
  const used = new Set();
  const origin = (cp) => {
    if (cp >> 8 !== page) throw new Error(`glyph U+${cp.toString(16)} is not on page ${page.toString(16)}`);
    return [(cp & 0x0f) * cell, ((cp >> 4) & 0x0f) * cell];
  };
  return {
    img,
    cell,
    used,
    /** Blits a cell-sized (or smaller, centred) image into the cell of `cp`. */
    put(cp, glyph) {
      const [ox, oy] = origin(cp);
      if (used.has(cp)) throw new Error(`glyph U+${cp.toString(16)} drawn twice`);
      used.add(cp);
      blit(img, glyph, ox + ((cell - glyph.w) >> 1), oy + ((cell - glyph.h) >> 1));
    },
    /**
     * A cell-local painter image view for multi-pass drawing: returns an image of `cell`² that is copied into the
     * sheet by `commit()`. Using `put` afterwards on the same code point throws.
     */
    canvas(cp) {
      origin(cp);
      const c = image(cell, cell);
      return { img: c, commit: () => this.put(cp, c) };
    },
    /** Draws a string grid centred in the cell, scaled by the largest integer that keeps a 1 px margin (or `scale`). */
    grid(cp, rows, legend, scale) {
      const w = rows[0].length;
      const h = rows.length;
      const k = scale ?? Math.max(1, Math.floor((cell - 2) / Math.max(w, h)));
      this.put(cp, drawGrid(image(w * k, h * k), rows, legend, 0, 0, k));
    },
  };
}

const hex4 = (cp) => cp.toString(16).toUpperCase().padStart(4, '0');

/**
 * Java bitmap provider for a glyph page: `chars` = 16 strings of 16 code points, unused cells = U+0000
 * (Java skips them). Pass the page's `used` set so only drawn glyphs are mapped.
 */
export function javaBitmapProvider(page, used, { file, height = 8, ascent = 7 }) {
  const chars = [];
  for (let r = 0; r < 16; r++) {
    let row = '';
    for (let c = 0; c < 16; c++) {
      const cp = (page << 8) | (r << 4) | c;
      row += used.has(cp) ? String.fromCodePoint(cp) : '\u0000';
    }
    chars.push(row);
  }
  return { type: 'bitmap', file, height, ascent, chars };
}

/** Human-readable list of the drawn code points (for docs / reports). */
export const describeUsed = (used) =>
  [...used]
    .sort((a, b) => a - b)
    .map(hex4)
    .join(' ');
