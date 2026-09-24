// String grid → 16 × 16 image, centred on its bounding box (so every symbol sits in the middle of its cell).
import { drawGrid, image } from '../../lib/grid.mjs';
import { bounds, crop, over } from './raster.mjs';

const cache = new Map();

/** 16 × 16 art of a symbol or icon definition ({grid, pal, glow}); `lit` applies the glow palette. */
export function art(def, { lit = false, centre = true } = {}) {
  const key = `${def.id}|${lit}|${centre}`;
  if (cache.has(key)) return cache.get(key);
  const raw = drawGrid(image(16, 16), def.grid, lit ? { ...def.pal, ...def.glow } : def.pal);
  let out = raw;
  if (centre) {
    const b = bounds(raw);
    const w = b.x1 - b.x0 + 1;
    const h = b.y1 - b.y0 + 1;
    out = over(image(16, 16), crop(raw, b.x0, b.y0, w, h), Math.floor((16 - w) / 2), Math.floor((16 - h) / 2));
  }
  cache.set(key, out);
  return out;
}
