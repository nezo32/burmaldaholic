// Output helpers: every asset module returns a list of outputs; the driver writes or checks them.
// Output = { path: <relative to java/>, bytes: Buffer }.
import { Buffer } from 'node:buffer';
import { encodePng } from './png.mjs';

export const png = (path, img) => ({ path, bytes: encodePng(img) });
export const json = (path, value) => ({ path, bytes: Buffer.from(`${JSON.stringify(value, null, 2)}\n`) });

/**
 * Java sprite `.mcmeta`: animation strip and/or GUI nine-slice. A strip of non-square frames must give its frame size
 * (`frame: [width, height]`), otherwise vanilla assumes square frames and rejects the sprite.
 */
export function mcmeta(path, { frametime, interpolate, frame, nineSlice } = {}) {
  const v = {};
  if (frametime) {
    v.animation = { frametime, ...(interpolate ? { interpolate: true } : {}) };
    if (frame && frame[0] !== frame[1]) Object.assign(v.animation, { width: frame[0], height: frame[1] });
  }
  if (nineSlice) v.gui = { scaling: { type: 'nine_slice', width: nineSlice.width, height: nineSlice.height, border: nineSlice.border } };
  return json(`${path}.mcmeta`, v);
}

export const JAVA_ASSETS = 'src/main/resources/assets/burmaldaholic';
