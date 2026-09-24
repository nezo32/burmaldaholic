import { describe, expect, it } from 'vitest';
import { drawGrid, image, strip } from './grid.mjs';
import { encodePng } from './png.mjs';

describe('asset generator primitives', () => {
  it('draws string grids deterministically', () => {
    const a = drawGrid(image(2, 2), ['g.', '.g'], { g: '#FFD640' });
    expect([...a.data.slice(0, 4)]).toEqual([255, 214, 64, 255]);
    expect(a.data[7]).toBe(0);
    expect(encodePng(a).equals(encodePng(drawGrid(image(2, 2), ['g.', '.g'], { g: '#FFD640' })))).toBe(true);
  });
  it('stacks frames into a vertical strip', () => {
    const f = drawGrid(image(1, 1), ['g'], { g: '#000000' });
    expect(strip([f, f, f]).h).toBe(3);
  });
});
