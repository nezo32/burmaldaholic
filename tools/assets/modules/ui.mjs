// Casino UI kit art (lane J-L2; docs/architecture/animation.md §2.12): scene frames, banners, plates and the round
// action button used by client.ui.CasinoScreen / CasinoButton. Java only; outputs under core's `core/ui/` sprite folder
// (atlas ids `burmaldaholic:core/ui/<name>`). Helpers live in ./ui/ (not loaded as modules by the driver).
import { JAVA_ASSETS, mcmeta, png } from '../lib/emit.mjs';
import { THEMES, actionButton, banner, frame, plate } from './ui/art.mjs';

const SPR = `${JAVA_ASSETS}/textures/gui/sprites/core/ui`;
const nine = (w, h, border) => ({ nineSlice: { width: w, height: h, border } });

export default function generate() {
  const out = [];
  const sprite = (name, img, meta) => {
    out.push(png(`${SPR}/${name}.png`, img));
    if (meta) out.push(mcmeta(`${SPR}/${name}.png`, meta));
  };
  for (const theme of Object.keys(THEMES)) {
    sprite(`frame_${theme}`, frame(theme), nine(64, 64, 12));
    sprite(`banner_${theme}`, banner(theme), nine(48, 24, 10));
  }
  sprite('plate', plate(false), nine(24, 16, 5));
  sprite('plate_gold', plate(true), nine(24, 16, 5));
  for (const st of ['normal', 'highlighted', 'pressed', 'disabled'])
    sprite(`action_button${st === 'normal' ? '' : `_${st}`}`, actionButton(st));
  return out;
}
