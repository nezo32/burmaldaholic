// Checks of the card-table art module (docs/design/visual/cards.md §9): Java-only outputs, core-owned paths,
// determinism, atlas geometry, distinct faces, the index font, nine-slice metadata and the texture budgets.
import { Buffer } from 'node:buffer';
import { describe, expect, it } from 'vitest';
import generate, { TABLE_SIZES } from '../cards.mjs';
import { INDEX_GLYPHS, INDEX_ROWS, RANKS, SIZES, faceAtlas } from './faces.mjs';
import { SHAPES } from './suits.mjs';

const out = generate();
const byPath = new Map(out.map((o) => [o.path, o]));
const pngSize = (bytes) => [bytes.readUInt32BE(16), bytes.readUInt32BE(20)];

describe('cards asset module', () => {
  it('is deterministic, writes each file once, and only for Java', () => {
    expect(byPath.size).toBe(out.length);
    const again = generate();
    expect(again.every((o, i) => o.path === out[i].path && o.bytes.equals(out[i].bytes))).toBe(true);
    expect(out.every((o) => o.edition === 'java')).toBe(true);
  });

  it('keeps every file core-owned (gradle checkAssetOwnership: a `core/` segment)', () => {
    for (const o of out) expect(o.path.split('/').slice(4, -1), o.path).toContain('core');
  });

  it('gives every nine-slice / animated sprite its .mcmeta', () => {
    const metas = out.filter((o) => o.path.endsWith('.mcmeta'));
    for (const m of metas) expect(byPath.has(m.path.replace(/\.mcmeta$/, '')), m.path).toBe(true);
    for (const name of ['seat/plate_normal', 'panel/console_village', 'button/table_end_highlighted', 'stamp/gold', 'fx/glow_l', 'bot/thinking'])
      expect(byPath.has(`src/main/resources/assets/burmaldaholic/textures/gui/sprites/core/cards/${name}.png.mcmeta`), name).toBe(true);
  });

  it('lays the face atlases out as 13 ranks × 4 suits at the documented sizes', () => {
    for (const size of ['l', 'm', 's']) {
      const o = byPath.get(`src/main/resources/assets/burmaldaholic/textures/gui/core/cards/faces_${size}.png`);
      expect(pngSize(o.bytes)).toEqual([SIZES[size][0] * 13, SIZES[size][1] * 4]);
    }
    expect(SIZES.l).toEqual([37, 49]);
    for (const shape of ['crescent', 'oval']) {
      const o = byPath.get(`src/main/resources/assets/burmaldaholic/textures/gui/core/cards/table_${shape}_village.png`);
      expect(pngSize(o.bytes)).toEqual(TABLE_SIZES.full);
    }
  });

  it('draws 52 distinct L faces (four-colour) and keeps classic and four-colour decks different only in ♦ ♣', () => {
    const img = faceAtlas('l', true);
    const [w, h] = SIZES.l;
    const cells = new Set();
    for (let r = 0; r < 4; r++)
      for (let c = 0; c < 13; c++) {
        let key = '';
        for (let y = 0; y < h; y++) key += Buffer.from(img.data.subarray(((r * h + y) * img.w + c * w) * 4, ((r * h + y) * img.w + c * w + w) * 4)).toString('base64');
        cells.add(key);
      }
    expect(cells.size).toBe(52);
    const classic = faceAtlas('l', false);
    const rowBytes = (im, r) => Buffer.from(im.data.subarray(r * h * im.w * 4, (r + 1) * h * im.w * 4));
    expect(rowBytes(img, 0).equals(rowBytes(classic, 0))).toBe(true);
    expect(rowBytes(img, 1).equals(rowBytes(classic, 1))).toBe(true);
    expect(rowBytes(img, 2).equals(rowBytes(classic, 2))).toBe(false);
  });

  it('has an index glyph for every EN and RU rank (RU Т/К/Д/В), each at most 5 px wide and 6 px tall', () => {
    const chars = new Set(INDEX_ROWS.join(''));
    for (const r of [...RANKS, 'Т', 'К', 'Д', 'В']) for (const ch of r) expect(chars.has(ch), ch).toBe(true);
    for (const [ch, g] of Object.entries(INDEX_GLYPHS)) {
      expect(g, ch).toHaveLength(6);
      expect(Math.max(...g.map((row) => row.length)), ch).toBeLessThanOrEqual(5);
    }
    // "10" must fit the 6 px index gutter of an L card
    expect(INDEX_GLYPHS[1][0].length + 1 + INDEX_GLYPHS[0][0].length).toBeLessThanOrEqual(6);
  });

  it('suit shapes differ from each other (shape carries the suit without colour)', () => {
    const sample = (f) => {
      let s = '';
      for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) s += f((x + 0.5) / 8 - 1, (y + 0.5) / 8 - 1) ? '#' : '.';
      return s;
    };
    expect(new Set(Object.values(SHAPES).map(sample)).size).toBe(4);
  });

  it('stays inside the texture budget (sprites ≤ 16 KB, atlases and pictures ≤ 64 KB)', () => {
    for (const o of out.filter((x) => x.path.endsWith('.png'))) {
      const limit = o.path.includes('/sprites/') ? 16 * 1024 : 64 * 1024;
      expect(o.bytes.length, o.path).toBeLessThanOrEqual(limit);
    }
  });
});
