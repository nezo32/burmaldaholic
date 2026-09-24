import { describe, expect, it } from 'vitest';
import { EASE_IDS, ease } from '../../../src/core/logic/anim/ease';
import { flipbookUv, packRows, particleJson } from './atlas.mjs';
import { glyphPage, javaBitmapProvider } from './font.mjs';
import { bone, cube, geometry, quad } from './geo.mjs';
import { drawGrid, ellipse, getPx, image, rect } from './grid.mjs';
import { easeExpr, evalMolang, keyframes, piecewise } from './molang.mjs';
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

describe('molang', () => {
  it('ease expressions match core/logic/anim/ease.ts', () => {
    for (const id of EASE_IDS) {
      let e;
      try {
        e = easeExpr(id);
      } catch {
        continue; // outElastic / outBounce / shake are baked with keyframes()
      }
      for (let i = 0; i <= 20; i++) {
        const t = i / 20;
        expect(evalMolang(e, { t })).toBeCloseTo(ease(id, t), 9);
      }
    }
  });
  it('evaluates operators, ternaries, functions and q./v. aliases', () => {
    expect(evalMolang('1 + 2 * 3 - -1')).toBe(8);
    expect(evalMolang('v.a > 1 ? 10 : 20', { 'v.a': 2 })).toBe(10);
    expect(evalMolang('math.clamp(query.anim_time * 2, 0, 1)', { 'q.anim_time': 0.25 })).toBe(0.5);
    expect(evalMolang('math.sin(90)')).toBeCloseTo(1, 12);
  });
  it('piecewise and keyframes bake curves deterministically', () => {
    const e = piecewise([[0, 0], [1, 10], [2, 0]]);
    expect(evalMolang(e, { 'q.anim_time': 0.5 })).toBe(5);
    expect(evalMolang(e, { 'q.anim_time': 1.5 })).toBe(5);
    expect(evalMolang(e, { 'q.anim_time': 3 })).toBe(0);
    const k = keyframes((t) => t * 2, 0.1, 0.05);
    expect(Object.keys(k)).toEqual(['0', '0.05', '0.1']);
    expect(k['0.1']).toEqual([0, 0.2, 0]);
  });
});

describe('geo + atlas', () => {
  it('builds geometry with parent-order checks', () => {
    const g = geometry('burmaldaholic.test', { texW: 64, texH: 64, bones: [bone('root'), bone('reel', { parent: 'root', cubes: [quad(0, 0, 0, 8, 8, [0, 0, 8, 8]), cube([0, 0, 0], [1, 1, 1], [0, 0])] })] });
    expect(g['minecraft:geometry'][0].bones[1].cubes[0].uv.north.uv_size).toEqual([8, 8]);
    expect(() => geometry('x', { texW: 1, texH: 1, bones: [bone('a', { parent: 'b' })] })).toThrow();
  });
  it('packs flipbook rows and describes their UVs', () => {
    const f = ellipse(image(8, 8), 4, 4, 3, 3, '#FFFFFF');
    const { img, uv } = packRows([{ name: 'a', frames: [f, f] }, { name: 'b', frames: [f] }]);
    expect(uv.b).toEqual({ x: 0, y: 8, w: 8, h: 8, frames: 1 });
    expect(getPx(img, 12, 4)[3]).toBe(255);
    expect(flipbookUv(uv.a).flipbook.max_frame).toBe(2);
    expect(particleJson('burmaldaholic:x', { texture: 't', uv: uv.a }).particle_effect.description.identifier).toBe('burmaldaholic:x');
  });
});
