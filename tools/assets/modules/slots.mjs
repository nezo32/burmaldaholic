// Slots presentation art (docs/architecture/animation.md §5; animation/slots.md §9): symbol sheets 40/32/16 px
// (D1, D2), land keys, the tall End egg, in-world reel strip / blur / cabinet / overlay / marquee / wheel textures
// read from the real strips, themed block textures, particle frames, GUI sprites (cabinet frames, marquees,
// banners, anticipation frames, feature sprites, backdrops) and the Showdown sprites.
//
// Helpers live in ./slots/ (not loaded as modules by the driver: only *.mjs files directly in modules/ are).
// Deterministic: re-running writes byte-identical files; `--check` (npm run check:assets) keeps them honest.
import { JAVA_ASSETS, mcmeta, png } from '../lib/emit.mjs';
import { guiOutputs } from './slots/gui.mjs';
import { landSheet, machineSheet, miniSheet, tallEgg } from './slots/sheets.mjs';
import { loadStrips } from './slots/strips.mjs';
import { CODES, MACHINE_ORDER, MACHINE_SYMBOLS, validateArt } from './slots/symbols.mjs';
import { SHARED_ICONS } from './slots/icons.mjs';
import { THEME } from './slots/theme.mjs';
import { vstrip } from './slots/raster.mjs';
import {
  blockFront, blockSide, blockTop, blurTexture, cabinetTexture, emberFrames, marqueeTexture, moteFrames, overlayTexture, stripTexture, wheelTexture,
} from './slots/world.mjs';

const J = JAVA_ASSETS;

/** In-world reel prop contract: distinct cells of the looping blur texture, Nether ladder plates on the overlay. */
export const CABINET = {
  blurLoop: 4,
  /** base ×1/2/3/5, free spins ×2/4/6/10 */
  multPlates: [1, 2, 3, 4, 5, 6, 10],
};

export default async function generate(ctx) {
  for (const m of MACHINE_ORDER) validateArt(MACHINE_SYMBOLS[m]);
  validateArt(SHARED_ICONS);
  const { machines } = loadStrips(ctx.root);
  // map strip symbol codes onto the art order
  const strips = {};
  for (const m of MACHINE_ORDER) {
    const codes = machines[m].codes;
    strips[m] = machines[m].strips.map((s) =>
      s.map((i) => {
        const k = CODES[m].indexOf(codes[i]);
        if (k < 0) throw new Error(`slots art: strip code ${codes[i]} (${m}) has no art`);
        return k;
      }),
    );
  }
  const out = [];

  // ---- symbol sheets (code-indexed) ----
  for (const m of MACHINE_ORDER) {
    out.push(png(`${J}/textures/gui/slots/${m}_symbols.png`, machineSheet(m, 40)));
    out.push(png(`${J}/textures/gui/slots/${m}_symbols_32.png`, machineSheet(m, 32)));
    out.push(png(`${J}/textures/gui/slots/${m}_symbols_16.png`, miniSheet(m)));
    out.push(png(`${J}/textures/gui/slots/${m}_land.png`, landSheet(m)));
  }
  out.push(png(`${J}/textures/gui/slots/end_egg_tall.png`, tallEgg()));

  // ---- in-world: strips, blur, cabinet, overlay, marquees, wheel ----
  for (const m of MACHINE_ORDER) {
    strips[m].forEach((s, r) => out.push(png(`${J}/textures/entity/slots/${m}_strip_${r}.png`, stripTexture(m, s, 0, 0))));
    out.push(png(`${J}/textures/entity/slots/${m}_blur.png`, blurTexture(m, CABINET.blurLoop, 0)));
    out.push(png(`${J}/textures/entity/slots/${m}_cabinet.png`, cabinetTexture(m)));
    out.push(png(`${J}/textures/entity/slots/${m}_overlay.png`, overlayTexture(m, CABINET.multPlates)));
    for (const v of ['normal', 'fs', 'jackpot']) {
      const suffix = v === 'normal' ? '' : `_${v}`;
      out.push(png(`${J}/textures/entity/slots/${m}_marquee${suffix}.png`, marqueeTexture(m, v)));
    }
  }
  out.push(png(`${J}/textures/entity/slots/end_wheel.png`, wheelTexture()));

  // ---- blocks: themed textures, animated front (.mcmeta) ----
  const tpf = { overworld: 4, nether: 3, end: 2 };
  for (const m of MACHINE_ORDER) {
    const tier = THEME[m].tier;
    out.push(png(`${J}/textures/block/slots/slot_machine_${tier}_front.png`, vstrip([0, 1, 2, 3].map((f) => blockFront(m, f)))));
    out.push(mcmeta(`${J}/textures/block/slots/slot_machine_${tier}_front.png`, { frametime: tpf[m] }));
    out.push(png(`${J}/textures/block/slots/slot_machine_${tier}_side.png`, blockSide(m)));
    out.push(png(`${J}/textures/block/slots/slot_machine_${tier}_top.png`, blockTop(m)));
  }

  // ---- particles (slots-owned ids) ----
  emberFrames().forEach((f, i) => out.push(png(`${J}/textures/particle/slots/ember_burst_${i}.png`, f)));
  moteFrames().forEach((f, i) => out.push(png(`${J}/textures/particle/slots/void_motes_${i}.png`, f)));

  // ---- GUI sprites + backdrops + Showdown sprites ----
  out.push(...guiOutputs());
  return out;
}
