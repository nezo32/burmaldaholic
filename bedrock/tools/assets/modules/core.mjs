// Core presentation art (global.md §5, docs/architecture/animation.md §5 / §6), lane X-L0. Writes both editions.
//
// Java (all under assets/burmaldaholic/textures/; ownership = a `core/` folder, see gradle checkAssetOwnership):
//   gui/sprites/core/panel/*        nine-slice panels          → sprite ids `burmaldaholic:core/panel/<name>`
//   gui/sprites/core/widget/*       button families (3 states)  → `burmaldaholic:core/widget/casino_button[_primary|_danger][_highlighted|_disabled]`
//   gui/sprites/core/hud/*          flame_0..2, cloud_0..2, badge_shine_0..3
//   gui/sprites/core/fx/*           rays, coin_spin (8-frame strip), sparkle (4-frame strip), chip_<d>, chip_side_<d>
//   gui/sprites/core/menu/stamp, gui/sprites/core/toast/casino
//   gui/core/fx/*                   confetti sheet (UV-blitted), vignette_{gold,red,curse} (256², stretched)
//   font/core/glyph_e1.png          glyph sheet E1 (same bytes as Bedrock glyph_E1.png)
//   item/core/chip_<d>_{few,stack,tower}.png
//   particle/core/<id>_<n>.png      core particle frames (particle JSON / providers: lane J-L1)
// Bedrock (packs/core/RP/):
//   font/glyph_E1.png, textures/burmaldaholic/ui/*, textures/burmaldaholic/icons/*,
//   textures/particle/burmaldaholic_fx.png (one flipbook row per set, rows = B-L1's particle-atlas.ts ATLAS_ROWS)
import { bedrockNineSlice, json, mcmeta, png, JAVA_ASSETS } from '../lib/emit.mjs';
import { hstrip, strip } from '../lib/grid.mjs';
import { packRows } from '../lib/atlas.mjs';
import { buildGlyphSheet } from './core/glyphs.mjs';
import { bell, button, hudBadgeShine, hudCloud, hudFlame, panelCasino, panelFelt, panelHud, panelInset, panelTab, stamp, toast, trophy } from './core/ui.mjs';
import { chipSide, chipTop, coinSpinFrames, confettiSheet, rays, sparkle, vignette } from './core/fx.mjs';
import { BEDROCK_ATLAS_ROWS, PARTICLE_SETS } from './core/particles.mjs';
import { CHIP_DENOMS, STACK_KINDS, chipStack } from './core/items.mjs';
import { ICON_NAMES, icon } from './core/icons.mjs';
import * as menu from './core/menu.mjs';

const T = `${JAVA_ASSETS}/textures`;
const SPR = `${T}/gui/sprites/core`;
const RP = 'packs/core/RP';

/** Atlas layout of `textures/particle/burmaldaholic_fx.png` (for the Bedrock particle JSON of B-L1 / B-L7). */
export function atlasRows() {
  return packRows(BEDROCK_ATLAS_ROWS, { w: 128, h: 128 }).uv;
}

export default function generate() {
  const out = [];
  const sprite = (path, img, meta) => {
    out.push(png('java', `${SPR}/${path}.png`, img));
    if (meta) out.push(mcmeta(`${SPR}/${path}.png`, meta));
  };

  // ---- glyph sheet E1 (both editions, same PNG) ----
  const glyphs = buildGlyphSheet();
  out.push(png('bedrock', `${RP}/font/glyph_E1.png`, glyphs.img));
  out.push(png('java', `${T}/font/core/glyph_e1.png`, glyphs.img));

  // ---- panels + buttons (Java nine-slice) ----
  const nine = (w, h, border) => ({ nineSlice: { width: w, height: h, border } });
  sprite('panel/casino', panelCasino(), nine(32, 32, 6));
  sprite('panel/felt', panelFelt(), nine(32, 32, 6));
  sprite('panel/inset', panelInset(), nine(16, 16, 3));
  sprite('panel/hud', panelHud(false), nine(16, 16, 3));
  sprite('panel/hud_golden', panelHud(true), nine(16, 16, 3));
  sprite('panel/tab', panelTab(false), nine(32, 16, 4));
  sprite('panel/tab_selected', panelTab(true), nine(32, 16, 4));
  for (const family of ['secondary', 'primary', 'danger'])
    for (const state of ['normal', 'highlighted', 'disabled']) {
      const name = `casino_button${family === 'secondary' ? '' : `_${family}`}${state === 'normal' ? '' : `_${state}`}`;
      sprite(`widget/${name}`, button(family, state), nine(200, 20, 3));
    }

  // ---- HUD, toast, stamp ----
  for (let f = 0; f < 3; f++) {
    sprite(`hud/flame_${f}`, hudFlame(f));
    sprite(`hud/cloud_${f}`, hudCloud(f));
  }
  for (let f = 0; f < 4; f++) sprite(`hud/badge_shine_${f}`, hudBadgeShine(f));
  sprite('toast/casino', toast());
  sprite('menu/stamp', stamp());

  // ---- fx sprites ----
  sprite('fx/rays', rays());
  sprite('fx/coin_spin', strip(coinSpinFrames()), { frametime: 2 });
  sprite('fx/sparkle', strip([0, 1, 2, 3].map(sparkle)), { frametime: 2 });
  for (const d of CHIP_DENOMS) {
    sprite(`fx/chip_${d}`, chipTop(d));
    sprite(`fx/chip_side_${d}`, chipSide(d));
  }
  out.push(png('java', `${T}/gui/core/fx/confetti.png`, confettiSheet()));
  for (const [name, c] of [['gold', 'gold'], ['red', 'chip.red'], ['curse', 'curse.bg']])
    out.push(png('java', `${T}/gui/core/fx/vignette_${name}.png`, vignette(c)));

  // ---- chip stack items (Java; Bedrock keeps single chip textures, global.md §4.4) ----
  for (const d of CHIP_DENOMS)
    for (const k of STACK_KINDS) out.push(png('java', `${T}/item/core/chip_${d}_${k}.png`, chipStack(d, k)));

  // ---- particles: Java frame files + Bedrock atlas ----
  for (const s of PARTICLE_SETS) {
    if (s.java === false) continue;
    s.frames.forEach((f, i) => out.push(png('java', `${T}/particle/core/${s.name}_${i}.png`, f)));
  }
  out.push(png('bedrock', `${RP}/textures/particle/burmaldaholic_fx.png`, packRows(BEDROCK_ATLAS_ROWS, { w: 128, h: 128 }).img));

  // ---- Bedrock JSON UI textures + form icons ----
  const UI = `${RP}/textures/burmaldaholic/ui`;
  out.push(png('bedrock', `${UI}/hud_panel.png`, panelHud(false)), bedrockNineSlice(`${UI}/hud_panel`, { width: 16, height: 16, border: 3 }));
  out.push(png('bedrock', `${UI}/hud_panel_golden.png`, panelHud(true)), bedrockNineSlice(`${UI}/hud_panel_golden`, { width: 16, height: 16, border: 3 }));
  out.push(png('bedrock', `${UI}/flame.png`, strip([0, 1, 2].map(hudFlame))));
  out.push(png('bedrock', `${UI}/bell.png`, strip([0, 1].map(bell))));
  out.push(png('bedrock', `${UI}/toast.png`, toast()));
  out.push(png('bedrock', `${UI}/trophy.png`, trophy()));
  for (const name of ICON_NAMES) out.push(png('bedrock', `${RP}/textures/burmaldaholic/icons/${name}.png`, icon(name)));

  out.push(...menuShell(sprite, nine));
  return out;
}

/**
 * Casino Menu shell (docs/design/visual/extras.md §8–§9), Java only: the "casino ledger" frame, pages, bookmark tabs,
 * plaques, rows, progress bars, the Loan Shark's dark kit, achievement plates + medals, the HUD chip counter and toast
 * variants (atlas sprites under gui/sprites/core/…) and the code-blitted backdrops / sheets under gui/core/menu/.
 */
function menuShell(sprite, nine) {
  const out = [];
  const MENU = `${T}/gui/core/menu`;
  const sheet = (name, img) => out.push(png('java', `${MENU}/${name}.png`, img));
  sheet('lobby_backdrop', menu.lobbyBackdrop());
  sheet('loan_backdrop', menu.loanBackdrop());
  sheet('tab_icons', menu.tabIcons16());
  sheet('tab_icons_20', menu.tabIcons20());
  sheet('tab_icons_40', menu.tabIcons40());
  sheet('ach_medals', hstrip(menu.achMedals()));
  sheet('shark', menu.sharkPortrait());
  sprite('menu/shell', menu.shellFrame(false), nine(64, 64, 16));
  sprite('menu/shell_loan', menu.shellFrame(true), nine(64, 64, 16));
  sprite('menu/page', menu.page(false), nine(32, 32, 6));
  sprite('menu/page_loan', menu.page(true), nine(32, 32, 6));
  for (const st of ['normal', 'hover', 'selected']) {
    const suffix = st === 'normal' ? '' : `_${st}`;
    sprite(`menu/tab${suffix}`, menu.tab(st), nine(32, 24, 6));
    if (st !== 'hover') sprite(`menu/tab_loan${suffix}`, menu.tab(st, true), nine(32, 24, 6));
  }
  sprite('menu/header', menu.headerPlate(), nine(64, 20, 8));
  sprite('menu/balance', menu.balancePlaque(), nine(32, 20, 8));
  for (const k of ['normal', 'alt', 'highlight', 'loan']) sprite(`menu/row${k === 'normal' ? '' : `_${k}`}`, menu.ledgerRow(k), nine(32, 14, 4));
  sprite('menu/progress', menu.progressFrame(), nine(32, 10, 4));
  for (const k of ['gold', 'green', 'red', 'lilac']) {
    out.push(png('java', `${SPR}/menu/progress_fill_${k}.png`, menu.progressFill(k)));
    out.push(json('java', `${SPR}/menu/progress_fill_${k}.png.mcmeta`, { gui: { scaling: { type: 'tile', width: 8, height: 6 } } }));
  }
  for (const k of ['locked', 'unlocked', 'gold']) sprite(`menu/ach_plate_${k}`, menu.achPlate(k), nine(48, 24, 8));
  sprite('menu/debt_meter', menu.debtMeter(), nine(32, 12, 5));
  sprite('menu/debt_skull', menu.debtSkull());
  sprite('menu/stamp_overdue', menu.overdueStamp());
  sprite('menu/contract', menu.contractPaper(), nine(32, 32, 8));
  sprite('menu/offer', menu.offerCard(false), nine(32, 24, 8));
  sprite('menu/offer_locked', menu.offerCard(true), nine(32, 24, 8));
  sprite('hud/chip_counter', menu.chipCounter(false), nine(32, 16, 6));
  sprite('hud/chip_counter_golden', menu.chipCounter(true), nine(32, 16, 6));
  sprite('hud/chip_icon', menu.chipIcon(), { frametime: 4 });
  sprite('hud/delta_up', menu.deltaPill(true), nine(16, 10, 4));
  sprite('hud/delta_down', menu.deltaPill(false), nine(16, 10, 4));
  for (const k of ['achievement', 'pvp', 'loan']) sprite(`toast/${k}`, menu.toastVariant(k));
  return out;
}

