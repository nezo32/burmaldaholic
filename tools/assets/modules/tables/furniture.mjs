// Multi-block casino table furniture (MT-DESIGN §2): every 3×2 table block gets
//   blockstates/<name>.json                       facing × part (part=single keeps the v0.1 cube model)
//   models/block/tables/<name>/<part>.json         baked per-part models (chunk-meshed; no BER needed for the body)
//   models/block/tables/<name>/item.json           3×2 miniature for the item
//   items/<name>.json                              item model definition → the miniature
//   textures/block/core/tables/<theme>/*.png           seamless felt, wood, leather rail, trim, chip tray, bowl
//   data/.../loot_table/blocks/<name>.json         one item, only from part ∈ {main, single}
//
// Geometry is authored ONCE in the footprint-local pixel frame of the Java side (TableFootprint / FeltFrame):
// X = players' right (main block X∈[0,16]), Z = toward the players (main block Z∈[0,16]), Y up; a 3×2 table
// spans X∈[-16,32], Z∈[-16,16] with the felt centred on (8, 0). Each element is clipped per cell and mapped into
// that cell's block for facing=north (players on the north side: local +X → block −x, local +Z → block −z);
// the blockstate rotates the model for the other facings. Felt top = 14 px (FeltFrame.FELT_Y), rail top 15 px.
// Pure + deterministic.
import { JAVA_ASSETS, json, png } from '../../lib/emit.mjs';
import { R, P, c, image, put, rng } from './draw.mjs';
import { machineOutputs } from './machines.mjs';
import * as G from './table_geom.mjs';

const T = `${JAVA_ASSETS}/textures/block/core/tables`;
const M = `${JAVA_ASSETS}/models/block/tables`;
const DATA = 'src/main/resources/data/burmaldaholic';

/** Felt / rail heights (px) — FeltFrame.FELT_Y = 14/16, RAIL_TOP = 15.5/16. */
export { FELT_TOP, RAIL_TOP } from './table_geom.mjs';

export const PARTS = ['single', 'main', 'left', 'right', 'back_left', 'back', 'back_right'];
/** (r, f) per part — TablePart. */
const CELL = { main: [0, 0], left: [-1, 0], right: [1, 0], back_left: [-1, -1], back: [0, -1], back_right: [1, -1] };
const FACINGS = { north: 0, east: 90, south: 180, west: 270 };

/** Shape per game (TableGame.FeltShape). */
const SHAPE = { blackjack: 'crescent', baccarat: 'crescent', uth: 'crescent', poker: 'oval', roulette: 'roulette', craps: 'rect' };

/** Every 3×2 table block: registry name → game, theme and the v0.1 (compact) model it keeps for part=single. */
export const TABLES = [
  ['blackjack_table', 'blackjack', 'standard', 'burmaldaholic:block/blackjack/table'],
  ['blackjack_table_high_roller', 'blackjack', 'high_roller', 'burmaldaholic:block/blackjack/table_high_roller'],
  ['baccarat_table', 'baccarat', 'standard', 'burmaldaholic:block/baccarat/table'],
  ['baccarat_table_high_roller', 'baccarat', 'high_roller', 'burmaldaholic:block/baccarat/table_high_roller'],
  ['baccarat_table_player_banked', 'baccarat', 'player_banked', 'burmaldaholic:block/baccarat/table_player_banked'],
  ['uth_table', 'uth', 'standard', 'burmaldaholic:block/uth/table'],
  ['uth_table_high_roller', 'uth', 'high_roller', 'burmaldaholic:block/uth/table_high_roller'],
  ['uth_table_player_banked', 'uth', 'player_banked', 'burmaldaholic:block/uth/table_player_banked'],
  ['poker_table', 'poker', 'standard', 'burmaldaholic:block/poker/poker_table'],
  ['roulette_table', 'roulette', 'standard', 'burmaldaholic:block/roulette/roulette_table'],
  ['roulette_table_high_roller', 'roulette', 'high_roller', 'burmaldaholic:block/roulette/roulette_table_high_roller'],
  ['craps_table', 'craps', 'standard', 'burmaldaholic:block/craps/craps_table'],
].map(([name, game, theme, single]) => ({ name, game, theme, single, shape: SHAPE[game] }));

// ---- themes -----------------------------------------------------------------------------------------------------
// village oak + green felt; high roller dark oak + gold trim + royal felt; player-banked mahogany + red felt.
export const THEMES = {
  standard: {
    felt: ['#1E6A3E', '#22744A', '#1A5E37', '#175331'],
    wood: ['#C08A50', '#A6703E', '#8C5A30', '#6E4424', '#55331A'],
    rail: ['#7A4424', '#5A2E16', '#4A2412', '#341808'],
    stitch: '#3A1C0C',
    trim: ['#F0C860', '#D0A040', '#8A6420'],
    tray: '#2A1A10',
  },
  // VQA r1: HR felt lifted to a royal purple that stays purple under hall light; black leather with gold stitching
  high_roller: {
    felt: ['#4A2C86', '#5A38A0', '#3E2470', '#3E2470'],
    wood: ['#5E3E28', '#4E3220', '#3E2618', '#2E1C10', '#21130C'],
    rail: ['#34303A', '#1C1A20', '#141218', '#0A090C'],
    stitch: '#C8A040',
    trim: ['#FFE070', '#F0C040', '#B88A18'],
    tray: '#120C08',
  },
  // VQA r1: player-banked felt deep burgundy-brown (separates from red carpet), oxblood leather
  player_banked: {
    felt: ['#5E1A2A', '#6E2234', '#4E1422', '#4E1422'],
    wood: ['#86442A', '#723A22', '#5E2E1A', '#4C2414', '#3A1A0E'],
    rail: ['#5A2024', '#3A1012', '#2C0A0C', '#1C0608'],
    stitch: '#1A0406',
    trim: ['#F0C870', '#D0A048', '#8C6A24'],
    tray: '#1A0C08',
  },
};

// ---- textures (16 × 16, seamless) ------------------------------------------------------------------------------
export function feltTexture(theme) {
  const t = THEMES[theme];
  const img = image(16, 16);
  const r = rng(0x5eed + theme.length * 97);
  for (let y = 0; y < 16; y++)
    for (let x = 0; x < 16; x++) {
      const v = r();
      // fine woven baize: mostly the base tone, a light nap and a faint shade, no dark specks
      put(img, x, y, c(v < 0.74 ? t.felt[0] : v < 0.9 ? t.felt[1] : t.felt[2]));
    }
  return img;
}

export function woodTexture(theme) {
  const t = THEMES[theme];
  const img = image(16, 16);
  const r = rng(0xb00d + theme.length * 31);
  // lacquered hardwood: 3-tone long grain + a highlight streak, seamless (no baked seam: curved aprons are many short
  // segments, each starting its UV at 0, so a seam column would repeat every 3 px)
  for (let y = 0; y < 16; y++) {
    const phase = (y * 7) % 16;
    for (let x = 0; x < 16; x++) {
      const streak = Math.sin(((x + phase) / 16) * Math.PI * 2) + 0.35 * Math.sin(((x * 3 + y * 5) / 16) * Math.PI * 2);
      let k = 1;
      if (streak > 1.1) k = 0;
      else if (streak < -0.55) k = 2;
      if (streak < -1.15) k = 3;
      if (r() < 0.02) k = 3;
      put(img, x, y, c(t.wood[k]));
    }
  }
  return img;
}

/**
 * Padded leather (isotropic, the rail runs both ways): base tone with a soft lit centre band, button tufts every 8 px,
 * stitch dots every 4 px along both edges (gold on the high-roller rail).
 */
export function railTexture(theme) {
  const t = THEMES[theme];
  const img = image(16, 16);
  for (let y = 0; y < 16; y++)
    for (let x = 0; x < 16; x++) {
      const dy = Math.abs(y + 0.5 - 8);
      const dx = Math.abs(x + 0.5 - 8);
      const k = Math.min(dx, dy) < 2 ? 0 : Math.min(dx, dy) < 5 ? 1 : 2;
      put(img, x, y, c(t.rail[k]));
    }
  for (const [x, y] of [[3, 3], [11, 11], [3, 11], [11, 3]]) {
    R(img, x, y, 2, 2, t.rail[3]);
    P(img, x, y, t.rail[2]);
  }
  for (let i = 1; i < 16; i += 4) {
    P(img, i, 0, t.stitch);
    P(img, i, 15, t.stitch);
    P(img, 0, i, t.stitch);
    P(img, 15, i, t.stitch);
  }
  return img;
}

export function trimTexture(theme) {
  const t = THEMES[theme];
  const img = image(16, 16);
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(img, x, y, c(t.trim[(y % 4 === 0 ? 0 : y % 4 === 3 ? 2 : 1)]));
  for (let x = 0; x < 16; x += 5) for (let y = 1; y < 16; y += 4) P(img, x, y, t.trim[0]);
  return img;
}

/** Dealer chip tray: columns of chips (cosmetic), seen from above and the side. */
export function trayTexture(theme) {
  const t = THEMES[theme];
  const img = image(16, 16);
  R(img, 0, 0, 16, 16, t.tray);
  const chips = ['#E8E2D0', '#D23A3A', '#2E7AD8', '#2E9A48', '#1A1A1A', '#8A3AC8', '#E8B030', '#E8E2D0'];
  for (let i = 0; i < 8; i++) {
    const x = i * 2;
    for (let y = 0; y < 16; y++) {
      const stripe = (y + i) % 3 === 0;
      put(img, x, y, c(stripe ? '#F4F0E4' : chips[i]));
      put(img, x + 1, y, c(chips[i]));
    }
  }
  return img;
}

/**
 * Roulette bowl seen from above, a static wheel (pocket ring red / black with one green, ball track, wooden cone,
 * brass turret) so the table reads as roulette while the renderer's rotor is idle. The bowl is centred on the cell
 * boundary between the two wheel-column blocks, so the art is centred on (8, 0) and wraps vertically: each block's
 * UV half lands on its own half of the circle.
 */
export function bowlTexture(theme) {
  const t = THEMES[theme];
  const img = image(16, 16);
  for (let y = 0; y < 16; y++)
    for (let x = 0; x < 16; x++) {
      const dx = x + 0.5 - 8;
      const yy = y + 0.5;
      const dy = Math.min(yy, 16 - yy) * (yy < 8 ? 1 : -1);
      const d = Math.hypot(dx, dy);
      const ang = Math.atan2(dy, dx);
      let col;
      if (d > 6.5) col = t.wood[1];
      else if (d > 5) {
        const k = Math.floor(((ang + Math.PI) / (2 * Math.PI)) * 37) % 37;
        col = k === 0 ? '#1E8A3C' : k % 2 ? '#C8242C' : '#1A1418';
      } else if (d > 4) col = '#D8C49A';
      else if (d > 1.5) col = Math.abs(Math.sin(ang * 2)) > 0.92 ? t.trim[1] : t.wood[2];
      else col = t.trim[0];
      put(img, x, y, c(col));
    }
  return img;
}

/** Table-limit placard: dark plaque, brass frame, a chip and two lit pips (no words: the limits live in the UI). */
export function placardTexture(theme) {
  const t = THEMES[theme];
  const img = image(16, 16);
  R(img, 0, 0, 16, 16, t.trim[2]);
  R(img, 1, 1, 14, 14, t.trim[0]);
  R(img, 2, 2, 12, 12, '#141018');
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const d = Math.hypot(x + 0.5 - 8, y + 0.5 - 7);
    if (d < 4) put(img, x, y, c(d < 2.2 ? '#F4EEE0' : (Math.floor(Math.atan2(y - 7, x - 8) * 3) & 1) ? '#D23A3A' : '#F4EEE0'));
  }
  R(img, 4, 12, 3, 1, '#FFD640');
  R(img, 9, 12, 3, 1, '#5FE07A');
  return img;
}

/** Roulette tote board: black screen with a column of last numbers as red / black / green tiles and lit digits. */
export function toteTexture(theme) {
  const t = THEMES[theme];
  const img = image(16, 16);
  R(img, 0, 0, 16, 16, t.trim[1]);
  R(img, 1, 1, 14, 14, '#0C0A10');
  const cols = ['#D8343C', '#2A2430', '#D8343C', '#1E8A3C', '#2A2430', '#D8343C'];
  cols.forEach((col, i) => {
    const y = 2 + i * 2;
    const right = i % 2 === 0;
    R(img, right ? 9 : 3, y, 4, 2, col);
    R(img, (right ? 9 : 3) + 1, y, 2, 1, '#FFF4C8');
  });
  R(img, 2, 14, 12, 1, t.trim[0]);
  return img;
}

// ---- geometry (table_geom.mjs) → per-cell boxes + rotated segments → block elements ----------------------------

const clampI = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
/** Column index of an x (px): anything just outside the footprint belongs to the outer cells. */
const colOf = (x) => clampI(Math.floor(x / 16), -1, 1);
const rowOf = (z) => clampI(Math.floor(z / 16), -1, 0);
const cellOf = (b) => [colOf((b.x0 + b.x1) / 2), rowOf((b.z0 + b.z1) / 2)];
const r2 = (v) => Math.round(v * 1000) / 1000;

/** Axis-aligned boxes of a table split at cell boundaries (x −16/0/16/32, z 0) and merged along x inside a cell. */
export function tableBoxes(shape) {
  const { boxes } = G.tableGeometry(shape);
  const split = [];
  for (const b of boxes) {
    const xs = [b.x0, ...[0, 16].filter((v) => v > b.x0 && v < b.x1), b.x1];
    const zs = [b.z0, ...[0].filter((v) => v > b.z0 && v < b.z1), b.z1];
    for (let i = 0; i + 1 < xs.length; i++)
      for (let j = 0; j + 1 < zs.length; j++) split.push({ ...b, x0: xs[i], x1: xs[i + 1], z0: zs[j], z1: zs[j + 1] });
  }
  const keyOf = (b) => `${b.mat}|${b.y0}|${b.y1}|${b.z0}|${b.z1}|${b.emit ?? 0}|${colOf(b.x0 + 0.5)}`;
  const open = new Map();
  const merged = [];
  for (const b of split.sort((u, v) => u.x0 - v.x0 || u.y0 - v.y0 || u.z0 - v.z0)) {
    const k = keyOf(b);
    const prev = open.get(k);
    if (prev && prev.x1 === b.x0) prev.x1 = b.x1;
    else {
      const nb = { ...b };
      open.set(k, nb);
      merged.push(nb);
    }
  }
  return merged;
}

/** Rotated segments of a table (curved rails, aprons, bowl rim, pedestal feet). */
export function tableSegs(shape) {
  return G.tableGeometry(shape).segs;
}

/** Local box in cell (r, f) → block element for facing=north (x' = 16 − (X − 16r), z' = 16 − (Z − 16f)). */
function element(b, r, f, scale) {
  let x0 = 16 - (b.x1 - 16 * r);
  let x1 = 16 - (b.x0 - 16 * r);
  let z0 = 16 - (b.z1 - 16 * f);
  let z1 = 16 - (b.z0 - 16 * f);
  const uvTop = [x0, z0, x1, z1].map((v) => r2(clampI(v, 0, 16)));
  const vy = [16 - Math.min(16, b.y1 % 16 || 16), 16 - (b.y0 % 16)].map(r2);
  let y0 = b.y0;
  let y1 = b.y1;
  if (scale) {
    x0 = (32 - b.x1) / 3;
    x1 = (32 - b.x0) / 3;
    z0 = 8 - b.z1 / 3;
    z1 = 8 - b.z0 / 3;
    y0 = b.y0 * 0.5 + 2;
    y1 = b.y1 * 0.5 + 2;
  }
  const tex = `#${b.mat}`;
  const v0 = Math.min(vy[0], vy[1]);
  const v1 = Math.max(vy[0], vy[1]);
  const side = (u0, u1) => ({ uv: [r2(Math.min(u0, u1)), v0, r2(Math.max(u0, u1)), v1 === v0 ? v0 + 0.5 : v1], texture: tex });
  const faces = {
    up: { uv: uvTop, texture: tex },
    down: { uv: uvTop, texture: tex, ...(b.y0 === 0 && !scale ? { cullface: 'down' } : {}) },
    north: side(16 - uvTop[2], 16 - uvTop[0]),
    south: side(uvTop[0], uvTop[2]),
    west: side(uvTop[1], uvTop[3]),
    east: side(16 - uvTop[3], 16 - uvTop[1]),
  };
  return { from: [x0, y0, z0].map(r2), to: [x1, y1, z1].map(r2), ...(b.emit && !scale ? { light_emission: b.emit } : {}), faces };
}

/**
 * Rotated segment (local centre cx/cz, length along tangent (tx, tz), thickness) → block element: the 180° flip of
 * the north mapping keeps rotation angles; JOML rotation about +Y turns +X toward −Z, so the box's +X follows the
 * tangent with angle = atan2(−tz, tx) (folded into [−90°, 90°)).
 */
function segElement(sg, r, f, scale) {
  let cx = 16 - (sg.cx - 16 * r);
  let cz = 16 - (sg.cz - 16 * f);
  let y0 = sg.y0;
  let y1 = sg.y1;
  let len = sg.len;
  let thick = sg.thick;
  if (scale) {
    cx = (32 - sg.cx) / 3;
    cz = 8 - sg.cz / 3;
    y0 = sg.y0 * 0.5 + 2;
    y1 = sg.y1 * 0.5 + 2;
    len /= 3;
    thick /= 3;
  }
  let ang = (Math.atan2(-(-sg.tz), -sg.tx) * 180) / Math.PI;
  while (ang >= 90) ang -= 180;
  while (ang < -90) ang += 180;
  const tex = `#${sg.mat}`;
  const U = Math.min(16, len);
  const T = Math.min(16, Math.max(0.25, thick));
  const vy0 = 16 - Math.min(16, y1 % 16 || 16);
  const vy1 = Math.max(vy0 + 0.25, 16 - (y0 % 16));
  const faces = {
    up: { uv: [0, 0, r2(U), r2(T)], texture: tex },
    down: { uv: [0, 0, r2(U), r2(T)], texture: tex },
    north: { uv: [0, r2(vy0), r2(U), r2(vy1)], texture: tex },
    south: { uv: [0, r2(vy0), r2(U), r2(vy1)], texture: tex },
    west: { uv: [0, r2(vy0), r2(T), r2(vy1)], texture: tex },
    east: { uv: [0, r2(vy0), r2(T), r2(vy1)], texture: tex },
  };
  return {
    from: [cx - len / 2, y0, cz - thick / 2].map(r2),
    to: [cx + len / 2, y1, cz + thick / 2].map(r2),
    rotation: { origin: [r2(cx), r2((y0 + y1) / 2), r2(cz)], axis: 'y', angle: r2(ang) },
    ...(sg.emit && !scale ? { light_emission: sg.emit } : {}),
    faces,
  };
}

function textures(theme) {
  const base = `burmaldaholic:block/core/tables/${theme}`;
  return {
    particle: `${base}/wood`,
    felt: `${base}/felt`,
    wood: `${base}/wood`,
    rail: `${base}/rail`,
    trim: `${base}/trim`,
    tray: `${base}/tray`,
    bowl: `${base}/bowl`,
    placard: `${base}/placard`,
    tote: `${base}/tote`,
  };
}

/** Per-part model JSON of a table. */
export function partModel(table, part) {
  const [r, f] = CELL[part];
  const elements = tableBoxes(table.shape)
    .filter((b) => {
      const [cr, cf] = cellOf(b);
      return cr === r && cf === f;
    })
    .map((b) => element(b, r, f, false));
  for (const sg of tableSegs(table.shape)) if (colOf(sg.cx) === r && rowOf(sg.cz) === f) elements.push(segElement(sg, r, f, false));
  return { parent: 'minecraft:block/block', textures: textures(table.theme), elements };
}

/** Item miniature (whole table, scaled into the item cube). */
export function itemModel(table) {
  const elements = tableBoxes(table.shape).map((b) => {
    const [r, f] = cellOf(b);
    return element(b, r, f, true);
  });
  for (const sg of tableSegs(table.shape)) elements.push(segElement(sg, 0, 0, true));
  return {
    parent: 'minecraft:block/block',
    textures: textures(table.theme),
    elements,
    display: {
      gui: { rotation: [30, 225, 0], translation: [0, 1, 0], scale: [0.8, 0.8, 0.8] },
      ground: { rotation: [0, 0, 0], translation: [0, 3, 0], scale: [0.4, 0.4, 0.4] },
      fixed: { rotation: [0, 0, 0], translation: [0, 0, 0], scale: [0.6, 0.6, 0.6] },
      thirdperson_righthand: { rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: [0.375, 0.375, 0.375] },
      firstperson_righthand: { rotation: [0, 45, 0], translation: [0, 0, 0], scale: [0.5, 0.5, 0.5] },
      firstperson_lefthand: { rotation: [0, 225, 0], translation: [0, 0, 0], scale: [0.5, 0.5, 0.5] },
    },
  };
}

export function blockstate(table) {
  const variants = {};
  for (const [facing, y] of Object.entries(FACINGS))
    for (const part of PARTS) {
      const model = part === 'single' ? table.single : `burmaldaholic:block/tables/${table.name}/${part}`;
      variants[`facing=${facing},part=${part}`] = y ? { model, y } : { model };
    }
  return { variants };
}

export function lootTable(table) {
  const id = `burmaldaholic:${table.name}`;
  return {
    type: 'minecraft:block',
    pools: [
      {
        rolls: 1.0,
        conditions: [
          { condition: 'minecraft:survives_explosion' },
          {
            condition: 'minecraft:any_of',
            terms: ['main', 'single'].map((part) => ({ condition: 'minecraft:block_state_property', block: id, properties: { part } })),
          },
        ],
        entries: [{ type: 'minecraft:item', name: id }],
      },
    ],
    random_sequence: `burmaldaholic:blocks/${table.name}`,
  };
}

export default function generate() {
  const out = [];
  for (const theme of Object.keys(THEMES)) {
    out.push(png(`${T}/${theme}/felt.png`, feltTexture(theme)));
    out.push(png(`${T}/${theme}/wood.png`, woodTexture(theme)));
    out.push(png(`${T}/${theme}/rail.png`, railTexture(theme)));
    out.push(png(`${T}/${theme}/trim.png`, trimTexture(theme)));
    out.push(png(`${T}/${theme}/tray.png`, trayTexture(theme)));
    out.push(png(`${T}/${theme}/bowl.png`, bowlTexture(theme)));
    out.push(png(`${T}/${theme}/placard.png`, placardTexture(theme)));
    out.push(png(`${T}/${theme}/tote.png`, toteTexture(theme)));
  }
  for (const table of TABLES) {
    out.push(json(`${JAVA_ASSETS}/blockstates/${table.name}.json`, blockstate(table)));
    for (const part of PARTS.slice(1)) out.push(json(`${M}/${table.name}/${part}.json`, partModel(table, part)));
    out.push(json(`${M}/${table.name}/item.json`, itemModel(table)));
    out.push(json(`${JAVA_ASSETS}/items/${table.name}.json`, { model: { type: 'minecraft:model', model: `burmaldaholic:block/tables/${table.name}/item` } }));
    out.push(json(`${DATA}/loot_table/blocks/${table.name}.json`, lootTable(table)));
  }
  out.push(...machineOutputs()); // MT-3 upright machines
  return out;
}
