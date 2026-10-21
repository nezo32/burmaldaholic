// Layered painter of the casino NPC skins (DL-SPEC §3): base skin -> shirt -> vest/jacket -> tie -> face/hair ->
// overlay, each layer only touching the faces of the model's boxes (uv.mjs). Deterministic: the only "noise" is a
// fixed per-pixel hash used for a subtle cloth / skin grain.
import { image, setPx } from '../../lib/grid.mjs';
import { shade } from '../../lib/palette.mjs';
import { parts } from './uv.mjs';

/** Light per face so the cubes read in-world (top lit, bottom dark). */
const LIGHT = { top: 1.08, bottom: 0.82, right: 0.93, front: 1, left: 0.93, back: 0.9 };

const grain = (x, y) => {
  let h = (x * 374761393 + y * 668265263) >>> 0;
  h = ((h ^ (h >>> 13)) * 1274126177) >>> 0;
  return ((h >>> 16) & 0xff) / 255; // 0..1
};

/** A painter over `model`: every call addresses (box, side) face-local pixels. */
export function canvas(model) {
  const img = image(model.w, model.h);
  const P = parts(model);
  const rect = (box, side) => {
    const b = P[box];
    if (!b) throw new Error(`npc paint: no box '${box}'`);
    return b.faces[side];
  };
  const put = (box, side, x, y, c, flat = false) => {
    if (!c) return;
    const r = rect(box, side);
    if (x < 0 || y < 0 || x >= r.w || y >= r.h) return;
    const px = r.x + x;
    const py = r.y + y;
    const k = flat ? 1 : LIGHT[side] * (0.97 + 0.06 * grain(px, py));
    setPx(img, px, py, k === 1 ? c : shade(c, k));
  };
  const api = {
    img,
    model,
    put,
    /** Every pixel of a face: `fn(x, y, w, h)` returns a colour or null (keep). */
    face(box, side, fn) {
      const r = rect(box, side);
      for (let y = 0; y < r.h; y++) for (let x = 0; x < r.w; x++) put(box, side, x, y, typeof fn === 'function' ? fn(x, y, r.w, r.h) : fn);
      return api;
    },
    /** Every face of a box (or the listed sides). */
    fill(box, c, sides = ['top', 'bottom', 'right', 'front', 'left', 'back']) {
      for (const s of sides) api.face(box, s, c);
      return api;
    },
    /** Rows y0..y1 of the four side faces (a band round a limb / the body). */
    band(box, y0, y1, c, sides = ['right', 'front', 'left', 'back']) {
      for (const s of sides) api.face(box, s, (x, y) => (y >= y0 && y <= y1 ? c : null));
      return api;
    },
    size(box, side) {
      return rect(box, side);
    },
  };
  return api;
}
