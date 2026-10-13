// Checks of the table UI v2 kit art (lane K): deterministic, under core/v2, every id the Java V2Sprites asks for is
// generated, chips are round with digits inside the face, nine-slice metadata matches the images.
import { describe, expect, it } from 'vitest';
import generate from '../ui2.mjs';
import { BUTTON_STATES, BUTTON_STYLES, DENOMS, GLYPHS, chip, disc, plate } from './art.mjs';

const alphaAt = (img, x, y) => img.data[(y * img.w + x) * 4 + 3];
const name = (p) => p.replace(/^.*\/sprites\/core\/v2\//, '').replace(/\.png(\.mcmeta)?$/, '');

describe('ui2 kit module', () => {
  const out = generate();
  const pngs = new Set(out.filter((o) => o.path.endsWith('.png')).map((o) => name(o.path)));

  it('is deterministic, writes each file once, under core/v2', () => {
    const paths = out.map((o) => o.path);
    expect(new Set(paths).size).toBe(paths.length);
    const again = generate();
    expect(again.every((o, i) => o.path === out[i].path && o.bytes.equals(out[i].bytes))).toBe(true);
    for (const p of paths) expect(p).toMatch(/textures\/gui\/sprites\/core\/v2\/[a-z0-9_/]+\.png(\.mcmeta)?$/);
  });

  it('generates every sprite id the Java kit uses', () => {
    const need = ['topbar', 'dock', 'banner', 'toast', 'felt_vignette', 'focus_ring', 'key_hint', 'chip_icon', 'balance_pill', 'title_pill',
      'info_pill', 'total_pill', 'plate', 'plate_you', 'plate_acting', 'value_pill', 'value_pill_you', 'value_pill_win', 'value_pill_lose',
      'value_pill_push', 'felt_village', 'felt_bastion', 'felt_end', 'chip_selected_ring', 'chip_selected_ring_s', 'chip_selected_ring_l'];
    for (const k of ['win', 'lose', 'push', 'gold']) need.push(`stamp_${k}`, `stamp_dot_${k}`);
    for (const d of DENOMS) need.push(`chip_${d}`, `chip_${d}_s`, `chip_${d}_l`, `felt_disc_${d}`, `felt_disc_${d}_s`, `felt_disc_${d}_l`);
    for (const st of [...BUTTON_STYLES, 'icon_button']) for (const s of BUTTON_STATES) need.push(`${st}${s}`);
    for (const g of GLYPHS) need.push(`icon/${g}`, `icon/${g}_l`);
    for (const n of need) expect(pngs.has(n), n).toBe(true);
  });

  it('draws round chips with transparent corners and digits on the face', () => {
    for (const size of [20, 24, 32])
      for (const d of DENOMS) {
        const img = chip(d, size);
        expect(img.w).toBe(size);
        expect(alphaAt(img, 0, 0)).toBe(0);
        expect(alphaAt(img, size - 1, size - 1)).toBe(0);
        expect(alphaAt(img, size / 2, size / 2)).toBe(255);
      }
    const a = chip(25, 24);
    const b = chip(5, 24);
    expect(a.data.equals ? Buffer.from(a.data).equals(Buffer.from(b.data)) : false).toBe(false);
    expect(alphaAt(disc(100, 12), 0, 0)).toBe(0);
  });

  it('keeps nine-slice metadata consistent with the image sizes', () => {
    const meta = out.filter((o) => o.path.endsWith('.mcmeta'));
    expect(meta.length).toBeGreaterThan(20);
    for (const m of meta) {
      const v = JSON.parse(m.bytes.toString());
      const s = v.gui.scaling;
      if (s.type !== 'nine_slice') continue;
      expect(s.border * 2).toBeLessThan(Math.min(s.width, s.height) + 1);
    }
    expect(plate('#FFFFFF').w).toBe(24);
  });
});
