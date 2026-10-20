// Dice kit art (DCa, PHASE2B §3.3): the shared die orientation atlas for craps (cells 14/18/22/30) and the duel (32),
// its JSON (frame quaternions + rest-frame top-face quads), the generated Java table, die shadows (3 sizes per cell),
// the rest glint, felt dust and the die spark. Core-owned (both games use it). Helpers live in ./tables/dice.mjs.
import { Buffer } from 'node:buffer';
import { JAVA_ASSETS, json, png } from '../lib/emit.mjs';
import * as D from './tables/dice.mjs';

const T = `${JAVA_ASSETS}/textures`;
const GUI = `${T}/gui/tables/core`;
const SPR = `${T}/gui/sprites/tables/core/dice`;
export const ATLAS_JAVA = 'src/main/java/dev/nezo/burmaldaholic/core/anim/dice/DieAtlasTable.java';

export default function generate() {
  const out = [];
  const data = D.dieAtlasData();
  for (const cell of D.ATLAS_CELLS) {
    out.push(png(`${GUI}/dice_atlas_${cell}.png`, D.dieAtlas(cell)));
    for (let s = 0; s < 3; s++) out.push(png(`${SPR}/shadow_${cell}_${s}.png`, D.dieShadowCell(cell, s)));
    out.push(png(`${SPR}/rest_glint_${cell}.png`, D.dieRestGlint(cell)));
  }
  out.push(png(`${SPR}/dust.png`, D.diceDust()));
  out.push(png(`${T}/entity/core/dice_world.png`, D.diceWorldTexture()));
  out.push(png(`${SPR}/spark.png`, D.diceSpark()));
  out.push(json(`${GUI}/dice_atlas.json`, data));
  out.push({ path: ATLAS_JAVA, bytes: Buffer.from(D.dieAtlasJava(data)) });
  return out;
}
