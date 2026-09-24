// Slots presentation art for BOTH editions (docs/architecture/animation.md §5, lane B-L10; animation/slots.md §9):
// symbol sheets 40/32/16 px (D1, D2), glyph planes E2/E3/E4, the in-world `slot_reels` prop (geometry, animations,
// controllers, render controllers, BP entity), strip textures read from the v2 strips, cabinets, marquees, the End
// wheel, block flipbooks, particles, Bedrock sounds, Java GUI sprites (cabinet frames, marquees, banners,
// anticipation frames, feature sprites, backdrops, Showdown sprites) and Bedrock form icons.
//
// Helpers live in ./slots/ (not loaded as modules by the driver: only *.mjs files directly in modules/ are).
// Deterministic: re-running writes byte-identical files; `--check` (npm run check:assets) keeps them honest.
// Strips: the v2 engine's `MachineDef.strips` when lane B-L8's config exists, else SLOTS.md Appendix A.
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { JAVA_ASSETS, json, mcmeta, png } from '../lib/emit.mjs';
import { animationControllers, animations, clientEntity, geometry, renderControllers, serverEntity } from './slots/entity.mjs';
import { guiOutputs } from './slots/gui.mjs';
import { PARTICLES } from './slots/particles.mjs';
import { landSheet, machineSheet, miniSheet, plane, planeCells, tallEgg } from './slots/sheets.mjs';
import { soundDefinitions } from './slots/sounds.mjs';
import { loadStrips } from './slots/strips.mjs';
import { CODES, MACHINE_ORDER, MACHINE_SYMBOLS, validateArt } from './slots/symbols.mjs';
import { SHARED_ICONS } from './slots/icons.mjs';
import { THEME } from './slots/theme.mjs';
import { crop, vstrip } from './slots/raster.mjs';
import {
  blockFront, blockSide, blockTop, blurTexture, cabinetTexture, cellsAtlas, emberFrames, marqueeTexture, moteFrames, overlayTexture, particleAtlas,
  stripTexture, wheelTexture,
} from './slots/world.mjs';

const BP = 'packs/slots/BP';
const RP = 'packs/slots/RP';
const J = JAVA_ASSETS;

/** The prop contract from the TypeScript driver (single source of truth for property ids, padding, rings…). */
async function loadCabinetContract(root) {
  const esbuild = await import('esbuild');
  const out = await esbuild.build({ entryPoints: [path.join(root, 'src/games/slots/v2/present/cabinet-driver.ts')], bundle: true, write: false, format: 'esm', platform: 'neutral', logLevel: 'silent' });
  const dir = fs.mkdtempSync(path.join(root, 'node_modules/.cache-slots-contract-'));
  try {
    const file = path.join(dir, 'cabinet-driver.mjs');
    fs.writeFileSync(file, out.outputFiles[0].text);
    return (await import(pathToFileURL(file).href)).CABINET;
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
}

export default async function generate(ctx) {
  for (const m of MACHINE_ORDER) validateArt(MACHINE_SYMBOLS[m]);
  validateArt(SHARED_ICONS);
  const C = await loadCabinetContract(ctx.root);
  const { source, machines } = await loadStrips(ctx.root);
  // map engine symbol indices onto the art order by code (the engine is authoritative for the strips)
  const strips = {};
  for (const m of MACHINE_ORDER) {
    const codes = machines[m].codes;
    strips[m] = machines[m].strips.map((s) =>
      s.map((i) => {
        const k = CODES[m].indexOf(codes[i]);
        if (k < 0) throw new Error(`slots art: engine code ${codes[i]} (${m}) has no art`);
        return k;
      }),
    );
  }
  const out = [];
  const both = (javaPath, bedrockPath, img) => {
    if (javaPath) out.push(png('java', javaPath, img));
    if (bedrockPath) out.push(png('bedrock', bedrockPath, img));
  };

  // ---- symbol sheets (Java, code-indexed) + glyph planes (both) ----
  for (const m of MACHINE_ORDER) {
    out.push(png('java', `${J}/textures/gui/slots/${m}_symbols.png`, machineSheet(m, 40)));
    out.push(png('java', `${J}/textures/gui/slots/${m}_symbols_32.png`, machineSheet(m, 32)));
    out.push(png('java', `${J}/textures/gui/slots/${m}_symbols_16.png`, miniSheet(m)));
    out.push(png('java', `${J}/textures/gui/slots/${m}_land.png`, landSheet(m)));
  }
  out.push(png('java', `${J}/textures/gui/slots/end_egg_tall.png`, tallEgg()));
  const cells = planeCells();
  for (const [k, kind] of [['E2', 'base'], ['E3', 'win'], ['E4', 'blur']]) both(`${J}/textures/gui/slots/glyph_${k.toLowerCase()}.png`, `${RP}/font/glyph_${k}.png`, plane(cells, kind));

  // ---- in-world: strips, blur, cabinet, overlay, marquees, wheel, cells ----
  for (const m of MACHINE_ORDER) {
    strips[m].forEach((s, r) => {
      out.push(png('bedrock', `${RP}/textures/entity/slots/${m}_strip_${r}.png`, stripTexture(m, s, C.stripPadTop, C.stripPadBottom)));
      out.push(png('java', `${J}/textures/entity/slots/${m}_strip_${r}.png`, stripTexture(m, s, 0, 0)));
    });
    out.push(png('bedrock', `${RP}/textures/entity/slots/${m}_blur.png`, blurTexture(m, C.blurLoop, 3)));
    out.push(png('java', `${J}/textures/entity/slots/${m}_blur.png`, blurTexture(m, C.blurLoop, 0)));
    both(`${J}/textures/entity/slots/${m}_cabinet.png`, `${RP}/textures/entity/slots/${m}_cabinet.png`, cabinetTexture(m));
    both(`${J}/textures/entity/slots/${m}_overlay.png`, `${RP}/textures/entity/slots/${m}_overlay.png`, overlayTexture(m, C.multPlates));
    for (const v of ['normal', 'fs', 'jackpot']) {
      const suffix = v === 'normal' ? '' : `_${v}`;
      both(`${J}/textures/entity/slots/${m}_marquee${suffix}.png`, `${RP}/textures/entity/slots/${m}_marquee${suffix}.png`, marqueeTexture(m, v));
    }
  }
  both(`${J}/textures/entity/slots/end_wheel.png`, `${RP}/textures/entity/slots/end_wheel.png`, wheelTexture());
  out.push(png('bedrock', `${RP}/textures/entity/slots/nether_cells.png`, cellsAtlas('nether')));
  // Fallback window cuts (§6.6.3 [V]): only if the B-S0 spike shows per-controller `uv_anim` fails. Opt-in:
  // SLOTS_WINDOW_CUTS=1 node tools/gen-assets.mjs --module slots (16 × 48 per stop; the render controller then
  // switches to a texture array indexed by `r<r>` — not wired while uv_anim is the default).
  if (globalThis.process?.env?.SLOTS_WINDOW_CUTS === '1')
    for (const m of MACHINE_ORDER)
      strips[m].forEach((s, r) => {
        const tex = stripTexture(m, s, 0, 2);
        for (let t = 0; t < s.length; t++) out.push(png('bedrock', `${RP}/textures/entity/slots/win/${m}_${r}_${t}.png`, crop(tex, 0, t * 16, 16, 48)));
      });

  // ---- slot_reels entity (Bedrock) ----
  for (const m of MACHINE_ORDER) out.push(json('bedrock', `${RP}/models/entity/slots/slot_reels_${m}.geo.json`, geometry(C, m, strips)));
  out.push(json('bedrock', `${RP}/entity/slots/slot_reels.entity.json`, clientEntity(C, strips)));
  out.push(json('bedrock', `${RP}/animations/slots/slot_reels.animation.json`, animations(C)));
  out.push(json('bedrock', `${RP}/animation_controllers/slots/slot_reels.ac.json`, animationControllers(C, strips)));
  out.push(json('bedrock', `${RP}/render_controllers/slots/slot_reels.rc.json`, renderControllers(C, strips)));
  out.push(json('bedrock', `${BP}/entities/slots/slot_reels.json`, serverEntity(C, strips)));

  // ---- blocks: themed textures, animated front (Java .mcmeta / Bedrock flipbook) ----
  const flipbooks = [];
  const tpf = { overworld: 4, nether: 3, end: 2 };
  for (const m of MACHINE_ORDER) {
    const tier = THEME[m].tier;
    const front = vstrip([0, 1, 2, 3].map((f) => blockFront(m, f)));
    both(`${J}/textures/block/slots/slot_machine_${tier}_front.png`, `${RP}/textures/blocks/slots/${tier}_front.png`, front);
    out.push(mcmeta(`${J}/textures/block/slots/slot_machine_${tier}_front.png`, { frametime: tpf[m] }));
    both(`${J}/textures/block/slots/slot_machine_${tier}_side.png`, `${RP}/textures/blocks/slots/${tier}_side.png`, blockSide(m));
    both(`${J}/textures/block/slots/slot_machine_${tier}_top.png`, `${RP}/textures/blocks/slots/${tier}_top.png`, blockTop(m));
    flipbooks.push({ flipbook_texture: `textures/blocks/slots/${tier}_front`, atlas_tile: `burmaldaholic_slots_${tier}_front`, ticks_per_frame: tpf[m], ...(m === 'end' ? { blend_frames: true } : {}) });
  }
  out.push(json('bedrock', `${RP}/textures/flipbook_textures.json`, flipbooks));

  // ---- particles (slots-owned ids) ----
  out.push(png('bedrock', `${RP}/textures/particle/burmaldaholic_slots.png`, particleAtlas()));
  for (const [id, def] of Object.entries(PARTICLES)) out.push(json('bedrock', `${RP}/particles/slots/${id}.json`, def));
  emberFrames().forEach((f, i) => out.push(png('java', `${J}/textures/particle/slots/ember_burst_${i}.png`, f)));
  moteFrames().forEach((f, i) => out.push(png('java', `${J}/textures/particle/slots/void_motes_${i}.png`, f)));

  // ---- sounds (Bedrock half of SX3) ----
  out.push(json('bedrock', `${RP}/sounds/sound_definitions.json`, soundDefinitions()));

  // ---- Java GUI sprites + backdrops + Showdown sprites; Bedrock form icons ----
  out.push(...guiOutputs());

  void source; // 'engine' once lane B-L8's MachineDef defaults exist, else 'SLOTS.md Appendix A'
  return out;
}
