// Table-game art for European Roulette, Craps and Dice Duel — JAVA ONLY (docs/design/visual/tables.md; the
// choreography is docs/design/animation/tables.md). Bedrock support was dropped: this module emits no Bedrock files.
//
// Java outputs (all under assets/burmaldaholic/textures/; ownership = a `core/`, `roulette/`, `craps/` or `extras/`
// path segment, gradle checkAssetOwnership):
//   gui/tables/<owner>/*          UV-blitted sheets: backdrops, felt tiles, wheel bowl / head frame sets / mini wheels,
//                                 layouts (big + compact), racetrack, dice sheets, arena floors
//   gui/sprites/tables/<owner>/*  atlas sprites → ids `burmaldaholic:tables/<owner>/<name>` (nine-slices, buttons,
//                                 chips, plates, highlights, dolly, puck, badges, cup …; `.mcmeta` for loops)
//   entity/roulette/wheel_<theme>.png, entity/craps/dice.png   BER textures (in-world wheel, dice, puck)
//   particle/{roulette,craps,extras}/<id>_<n>.png              particle frames (providers: the game lanes)
//
// Helpers live in ./tables/ (not loaded as modules by the driver). Deterministic; `--check` keeps them honest.
import { JAVA_ASSETS, json, mcmeta, png } from '../lib/emit.mjs';
import { THEME_ORDER } from './tables/theme.mjs';
import { P, R, blend, c, disc, hstrip, image, over, put, star, vstrip } from './tables/draw.mjs';
import * as K from './tables/kit.mjs';
import * as W from './tables/wheel.mjs';
import * as Ro from './tables/roulette.mjs';
import * as D from './tables/dice.mjs';
import * as Cr from './tables/craps.mjs';

const T = `${JAVA_ASSETS}/textures`;
const GUI = `${T}/gui/tables`;
const SPR = `${T}/gui/sprites/tables`;

/** Wheel head rotation set: 74 frames (half-pocket steps of 360/74°), grid of 10 columns. */
export const HEAD_FRAMES = 74;
export const HEAD_COLS = 10;
export const HEAD_BLUR_FRAMES = 4;

function sheet(frames, cols) {
  const w = frames[0].w;
  const h = frames[0].h;
  const img = image(w * cols, h * Math.ceil(frames.length / cols));
  frames.forEach((f, i) => over(img, f, (i % cols) * w, Math.floor(i / cols) * h));
  return img;
}

// ---- world + particles ----------------------------------------------------------------------------------------------
/** Dice/puck BER atlas 64 × 16: faces 1–6 (8 × 8) at x = 8(k−1); puck OFF (48,0), ON (56,0); wall pyramids (0,8,16,4). */
export function diceWorld() {
  const img = image(64, 16);
  const pips = { 1: [[3, 3]], 2: [[5, 1], [1, 5]], 3: [[5, 1], [3, 3], [1, 5]], 4: [[1, 1], [5, 1], [1, 5], [5, 5]], 5: [[1, 1], [5, 1], [3, 3], [1, 5], [5, 5]], 6: [[1, 1], [5, 1], [1, 3], [5, 3], [1, 5], [5, 5]] };
  for (let f = 1; f <= 6; f++) {
    const x0 = (f - 1) * 8;
    R(img, x0, 0, 8, 8, '#F4ECF8');
    R(img, x0, 7, 8, 1, '#C0B0DC');
    R(img, x0 + 7, 0, 1, 8, '#C0B0DC');
    for (const [x, y] of pips[f]) R(img, x0 + x, y, 2, 2, f === 1 ? '#D83440' : '#2A1A3C');
  }
  for (const [x0, on] of [[48, false], [56, true]]) {
    disc(img, x0 + 4, 4, 4, c(on ? '#F4ECF8' : '#26202C'));
    for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) {
      const d = Math.hypot(x + 0.5 - 4, y + 0.5 - 4);
      if (d > 2.2 && d <= 3) P(img, x0 + x, y, on ? '#26202C' : '#F4ECF8');
    }
  }
  const wall = D.backWall();
  for (let y = 0; y < 4; y++) for (let x = 0; x < 16; x++) {
    const o = ((y * 2) * 16 + x) * 4;
    put(img, x, 8 + y, [wall.data[o], wall.data[o + 1], wall.data[o + 2], 255]);
  }
  return img;
}

/**
 * Dice Duel in-world die (animation/tables.md §3.4, item model `extras/dice_display`): 32 × 16, faces 1–6 as 8 × 8
 * cells, face k at (8((k−1) mod 4), 8⌊(k−1)/4⌋) — the same pips as the BER dice, with a darker edge all round so
 * the cube reads as a cube from any side.
 */
export function diceDisplay() {
  const img = image(32, 16);
  const src = diceWorld();
  for (let f = 1; f <= 6; f++) {
    const sx = (f - 1) * 8;
    const dx = ((f - 1) % 4) * 8;
    const dy = Math.floor((f - 1) / 4) * 8;
    for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) {
      const o = (y * src.w + sx + x) * 4;
      put(img, dx + x, dy + y, [src.data[o], src.data[o + 1], src.data[o + 2], 255]);
    }
    for (let i = 0; i < 8; i++) {
      P(img, dx + i, dy, '#C0B0DC');
      P(img, dx, dy + i, '#C0B0DC');
      P(img, dx + i, dy + 7, '#9A88BC');
      P(img, dx + 7, dy + i, '#9A88BC');
    }
  }
  return img;
}

function particle(kind, f) {
  const img = image(8, 8);
  if (kind === 'dice_dust') {
    const r = [1.5, 2.6, 3.4, 3.8][f];
    const k = [0.8, 0.6, 0.4, 0.2][f];
    disc(img, 4, 4.5, r, c('#E8DCC0'), k);
    disc(img, 3.5, 4, r * 0.5, c('#FFFFFF'), k * 0.6);
  } else if (kind === 'ball_spark') {
    star(img, 4, 4, [3, 2, 1][f], c('#FFD640'), [1, 0.8, 0.5][f]);
  } else if (kind === 'dolly_twinkle') {
    const r = [1, 2, 3, 1][f];
    star(img, 4, 4, r, c(f === 2 ? '#FFFFFF' : '#FFF1A0'), 1);
  } else {
    // duel_clash: a gold star burst with four chips of light
    const r = [1, 2, 3, 3][f];
    star(img, 4, 4, Math.min(3, r), c('#FFD640'), [1, 1, 0.7, 0.35][f]);
    for (const [dx, dy] of [[-3, -3], [3, -3], [-3, 3], [3, 3]]) if (f >= 1) blend(img, 4 + Math.sign(dx) * (f + 0), 4 + Math.sign(dy) * (f + 0), c('#FF6E6A'), 0.8 - f * 0.2);
  }
  return img;
}
export const PARTICLES = { roulette: { ball_spark: 3, dolly_twinkle: 4 }, craps: { dice_dust: 4 }, extras: { duel_clash: 4 } };

export default function generate() {
  const out = [];
  const gui = (rel, img) => out.push(png(`${GUI}/${rel}.png`, img));
  const sprite = (rel, img, meta) => {
    out.push(png(`${SPR}/${rel}.png`, img));
    if (meta) out.push(mcmeta(`${SPR}/${rel}.png`, meta));
  };
  /** Animated sprite whose frames are not square: `.mcmeta` carries the frame width / height. */
  const anim = (rel, img, frametime, fw, fh) => {
    out.push(png(`${SPR}/${rel}.png`, img));
    out.push(json(`${SPR}/${rel}.png.mcmeta`, { animation: { frametime, width: fw, height: fh } }));
  };
  const nine = (w, h, border) => ({ nineSlice: { width: w, height: h, border } });

  // ---- shared kit (core) ----
  for (const t of THEME_ORDER) {
    gui(`core/backdrop_${t}`, K.backdrop(t));
    gui(`core/felt_${t}`, K.felt(t));
    sprite(`core/rail_${t}`, K.rail(t), nine(48, 48, 14));
    sprite(`core/plate_${t}`, K.plate(t), nine(24, 16, 5));
    sprite(`core/plate_gold_${t}`, K.plate(t, { gold: true }), nine(24, 16, 5));
    for (const kind of ['other', 'you', 'active', 'bot']) sprite(`core/seat_${kind}_${t}`, K.seatPlate(t, kind), nine(32, 16, 5));
    for (const s of ['idle', 'hover', 'pressed', 'disabled']) {
      const suffix = s === 'idle' ? '' : `_${s}`;
      sprite(`core/button_${t}${suffix}`, K.button(t, s), nine(200, 20, 4));
      sprite(`core/icon_button_${t}${suffix}`, K.iconButton(t, s));
    }
    sprite(`core/chip_well_${t}`, K.chipWell(t));
    sprite(`core/banner_${t}`, K.banner(t, 'theme'), nine(48, 24, 10));
  }
  sprite('core/banner_win', K.banner('village', 'win'), nine(48, 24, 10));
  sprite('core/banner_lose', K.banner('village', 'lose'), nine(48, 24, 10));
  sprite('core/seat_stripe', K.seatStripe());
  sprite('core/bot_badge', K.botBadge());
  for (const icon of ['spin', 'roll'])
    for (const s of ['idle', 'hover', 'pressed', 'disabled']) sprite(`core/${icon}_button${s === 'idle' ? '' : `_${s}`}`, K.actionButton(icon, s));
  for (const n of K.ICON_NAMES) sprite(`core/icon/${n}`, K.icon(n));
  for (const d of K.DENOMS) {
    sprite(`core/chip_${d}`, K.trayChip(d));
    sprite(`core/stack/chip_${d}`, K.layoutChip(d));
  }
  sprite('core/chip_grey', K.trayChip('grey'));
  sprite('core/stack/chip_grey', K.layoutChip('grey'));
  sprite('core/stack/chip_invalid', K.layoutChipInvalid());
  sprite('core/chip_select', vstrip([0, 1, 2, 3].map(K.chipSelect)), { frametime: 4 });
  sprite('core/timer', vstrip(K.timer(false)));
  sprite('core/timer_urgent', vstrip(K.timer(true)));
  sprite('core/spark', Ro.spark());

  // ---- roulette ----
  const pitch = 360 / HEAD_FRAMES;
  gui('roulette/wheel_head', sheet(Array.from({ length: HEAD_FRAMES }, (_, i) => W.head(W.HEAD_BIG, i * pitch)), HEAD_COLS));
  gui('roulette/wheel_head_blur', vstrip(Array.from({ length: HEAD_BLUR_FRAMES }, (_, i) => W.head(W.HEAD_BIG, i * 2.4, { blur: 7 }))));
  gui('roulette/wheel_shadow', Ro.wheelShadow());
  // compact spin view (GUI < 400 × 240): 37 pocket-step head frames at 56 px over a 72 px bowl
  gui('roulette/wheel_mini_head', sheet(Array.from({ length: 37 }, (_, k) => W.head(W.HEAD_MINI, k * (360 / 37))), 10));
  for (const t of THEME_ORDER) {
    gui(`roulette/wheel_bowl_${t}`, W.bowl(W.BOWL_BIG, t));
    gui(`roulette/wheel_mini_bowl_${t}`, W.bowl(W.BOWL_MINI, t));
    // mini wheel: frame k = pocket WHEEL_ORDER[k] under the ball at the top (the last result, idle and static)
    gui(`roulette/wheel_mini_${t}`, sheet(Array.from({ length: 37 }, (_, k) => W.composed(W.BOWL_MINI, t, -k * (360 / 37), { ballTop: true })), 10));
    gui(`roulette/layout_${t}`, Ro.layout(t, Ro.LAYOUT_BIG));
    gui(`roulette/layout_compact_${t}`, Ro.layout(t, Ro.LAYOUT_COMPACT));
    gui(`roulette/racetrack_${t}`, Ro.racetrack(t));
    out.push(png(`${T}/entity/roulette/wheel_${t}.png`, W.worldTexture(t)));
  }
  sprite('roulette/ball', vstrip([0, 1, 2].map(W.ball)));
  sprite('roulette/pocket_glow', vstrip([0, 1, 2, 3].map(W.pocketGlow)), { frametime: 3 });
  anim('roulette/dolly', Ro.dolly(), 4, 12, 14);
  sprite('roulette/dolly_shadow', Ro.dollyShadow());
  for (const col of ['red', 'black', 'green']) {
    sprite(`roulette/pill_${col}`, Ro.pill(col));
    sprite(`roulette/pill_${col}_new`, Ro.pill(col, true));
  }
  sprite('roulette/cell_hover', Ro.cellHover(), nine(12, 12, 3));
  sprite('roulette/cell_neighbour', Ro.cellNeighbour(), nine(12, 12, 3));
  out.push(png(`${SPR}/roulette/cell_win.png`, Ro.cellWin()));
  out.push(mcmeta(`${SPR}/roulette/cell_win.png`, { frametime: 3, nineSlice: { width: 12, height: 12, border: 4 } }));

  // ---- craps ----
  for (const t of THEME_ORDER) {
    gui(`craps/layout_${t}`, Cr.crapsLayout(t, Cr.CRAPS_BIG));
    gui(`craps/layout_compact_${t}`, Cr.crapsLayout(t, Cr.CRAPS_COMPACT));
  }
  // dice_small: row 0 = faces 1–6, row 1 = tumble frames 0–7 (18 × 18 cells)
  gui('craps/dice_small', sheet([...[1, 2, 3, 4, 5, 6].map(D.dieSmall), image(18, 18), image(18, 18), ...Array.from({ length: 8 }, (_, f) => D.tumble(f))], 8));
  sprite('craps/die_shadow', D.dieShadow());
  sprite('craps/streak', D.streak());
  sprite('craps/puck', vstrip(D.puckFrames()));
  sprite('craps/stick', D.stick());
  sprite('craps/back_wall', D.backWall());
  for (const k of ['neutral', 'natural', 'craps', 'point']) sprite(`craps/total_${k}`, D.totalBadge(k));
  anim('craps/point_glow', D.pointGlow(), 3, 48, 40);
  anim('craps/flame', D.flame(), 3, 32, 10);
  out.push(png(`${T}/entity/craps/dice.png`, diceWorld()));
  out.push(png(`${T}/item/extras/dice_display.png`, diceDisplay()));

  // ---- dice duel (extras) ----
  // dice_big: row = face 1–6; columns: base, land ×2, win ×6, idle ×4 (40 × 40 cells); tumble_big: 8 frames
  gui('extras/dice_duel_dice_big', sheet([1, 2, 3, 4, 5, 6].flatMap((f) => D.dieBigFrames(f)), 13));
  gui('extras/dice_duel_tumble_big', hstrip(Array.from({ length: 8 }, (_, f) => D.tumble(f, 40, 11.5))));
  for (const t of THEME_ORDER) gui(`extras/dice_duel_arena_${t}`, Cr.arena(t));
  sprite('extras/dice_duel_cup', vstrip(D.cupFrames()));
  sprite('extras/dice_duel_vs', vstrip(D.vsBadge()));
  sprite('extras/dice_duel_crack', vstrip(D.crackFrames()));
  for (const k of ['neutral', 'win', 'lose']) sprite(`extras/dice_duel_plaque_${k}`, D.duelPlaque(k));
  sprite('extras/dice_duel_house_stamp', D.houseStamp());

  // ---- particles ----
  for (const [owner, set] of Object.entries(PARTICLES))
    for (const [id, n] of Object.entries(set)) for (let f = 0; f < n; f++) out.push(png(`${T}/particle/${owner}/${id}_${f}.png`, particle(id, f)));

  return out;
}
