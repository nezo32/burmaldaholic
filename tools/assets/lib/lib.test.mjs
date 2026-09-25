import { describe, expect, it } from 'vitest';
import { glyphPage, javaBitmapProvider } from './font.mjs';
import { drawGrid, getPx, image, rect } from './grid.mjs';
import { alpha, mix, rgba, shade, toHex } from './palette.mjs';
import { blurV, clone, crop, outline, recolor, rotate90, scale, shineBand, squash, tint } from './transforms.mjs';

const px = (img, x, y) => getPx(img, x, y).join(',');

describe('palette', () => {
  it('resolves tokens and mixes colours', () => {
    expect(rgba('gold')).toEqual([255, 214, 64, 255]);
    expect(toHex(rgba('#12345680'))).toBe('#12345680');
    expect(shade('#808080', 0.5)).toBe('#404040');
    expect(mix('#000000', '#FFFFFF', 0.5)).toBe('#808080');
    expect(alpha('bone', 0.5)).toBe('#F4ECF880');
    expect(() => rgba('nope')).toThrow();
  });
});

describe('transforms', () => {
  const a = drawGrid(image(3, 3), ['r..', '.g.', '..b'], { r: '#FF0000', g: '#00FF00', b: '#0000FF' });
  it('never mutates the input', () => {
    const before = clone(a);
    recolor(a, { '#FF0000': '#FFFFFF' });
    tint(a, '#808080');
    blurV(a, 1);
    expect(a.data).toEqual(before.data);
  });
  it('palette swap, tint, crop, scale, rotate', () => {
    expect(px(recolor(a, { '#FF0000': '#FFFFFF' }), 0, 0)).toBe('255,255,255,255');
    expect(px(tint(a, '#808080'), 1, 1)).toBe('0,128,0,255');
    expect(crop(a, 1, 1, 2, 2).w).toBe(2);
    expect(px(scale(a, 2), 5, 5)).toBe('0,0,255,255');
    expect(px(rotate90(a), 2, 0)).toBe('255,0,0,255');
  });
  it('outline surrounds opaque pixels only', () => {
    const o = outline(drawGrid(image(3, 3), ['...', '.w.', '...'], { w: '#FFFFFF' }), '#000000');
    expect(px(o, 1, 0)).toBe('0,0,0,255');
    expect(px(o, 0, 0)).toBe('0,0,0,0');
    expect(px(o, 1, 1)).toBe('255,255,255,255');
  });
  it('vertical blur spreads alpha and wraps for reel strips', () => {
    const s = rect(image(1, 4), 0, 0, 0, 0, '#FFFFFF');
    expect(getPx(blurV(s, 1), 0, 1)[3]).toBe(85);
    expect(getPx(blurV(s, 1, true), 0, 3)[3]).toBe(85);
    expect(getPx(blurV(s, 1), 0, 3)[3]).toBe(0);
  });
  it('shine band brightens only inside the band; squash keeps the bottom row', () => {
    const g = rect(image(8, 8), 0, 0, 7, 7, '#404040');
    const s = shineBand(g, 4, 2);
    expect(getPx(s, 2, 2)[0]).toBeGreaterThan(64);
    expect(getPx(s, 7, 7)[0]).toBe(64);
    const sq = squash(g, 1, 0.5);
    expect(getPx(sq, 3, 7)[3]).toBe(255);
    expect(getPx(sq, 3, 0)[3]).toBe(0);
  });
});

describe('font', () => {
  it('places code points by row/column and builds the Java provider rows', () => {
    const p = glyphPage(0xe1, 16);
    p.grid(0xe123, ['#'], { '#': '#FFFFFF' });
    expect(getPx(p.img, 3 * 16 + 1, 2 * 16 + 1)[3]).toBe(255);
    expect(getPx(p.img, 3 * 16, 2 * 16)[3]).toBe(0);
    expect(() => p.grid(0xe123, ['#'], { '#': '#FFFFFF' })).toThrow();
    expect(() => p.grid(0xe223, ['#'], { '#': '#FFFFFF' })).toThrow();
    const prov = javaBitmapProvider(0xe1, p.used, { file: 'burmaldaholic:font/core/glyph_e1.png' });
    expect(prov.chars).toHaveLength(16);
    expect([...prov.chars[2]][3]).toBe('');
    expect([...prov.chars[2]][4]).toBe('\u0000');
    expect(prov.height).toBe(8);
  });
});
