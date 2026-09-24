// Output helpers: every asset module returns a list of outputs; the driver writes or checks them.
// Output = { edition: 'java' | 'bedrock', path: <relative to that edition's root>, bytes: Buffer }.
import { Buffer } from 'node:buffer';
import { encodePng } from './png.mjs';

export const png = (edition, path, img) => ({ edition, path, bytes: encodePng(img) });
export const json = (edition, path, value) => ({ edition, path, bytes: Buffer.from(`${JSON.stringify(value, null, 2)}\n`) });

/** Java sprite `.mcmeta`: animation strip and/or GUI nine-slice. */
export function mcmeta(path, { frametime, interpolate, nineSlice } = {}) {
  const v = {};
  if (frametime) v.animation = { frametime, ...(interpolate ? { interpolate: true } : {}) };
  if (nineSlice) v.gui = { scaling: { type: 'nine_slice', width: nineSlice.width, height: nineSlice.height, border: nineSlice.border } };
  return json('java', `${path}.mcmeta`, v);
}

/** Bedrock JSON UI nine-slice sidecar (`<texture>.json` next to the PNG): `nineslice_size` + `base_size`. */
export const bedrockNineSlice = (path, { width, height, border }) =>
  json('bedrock', `${path}.json`, { nineslice_size: border, base_size: [width, height] });

/** The same image for both editions (the glyph sheet, particle frames that Java needs as files…). */
export const both = (javaPath, bedrockPath, img) => [png('java', javaPath, img), png('bedrock', bedrockPath, img)];

export const JAVA_ASSETS = 'src/main/resources/assets/burmaldaholic';
