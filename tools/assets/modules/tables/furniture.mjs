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

const T = `${JAVA_ASSETS}/textures/block/core/tables`;
const M = `${JAVA_ASSETS}/models/block/tables`;
const DATA = 'src/main/resources/data/burmaldaholic';

/** Felt / rail heights (px) — FeltFrame.FELT_Y = 14/16, RAIL_TOP = 15/16. */
export const FELT_TOP = 14;
export const RAIL_TOP = 15;
const RAIL = 2; // rail width (px) = FeltFrame.RAIL 0.125

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
    felt: ['#1E6A3E', '#1A5E37', '#22744A', '#175331'],
    wood: ['#B07A44', '#9C6A3A', '#865A30', '#6E4826', '#59391E'],
    rail: ['#6A3C1E', '#5A321A', '#4A2814', '#38200F'],
    trim: ['#D8A848', '#B88830', '#8A6420'],
    tray: '#2A1A10',
  },
  high_roller: {
    felt: ['#3A1E5E', '#331A54', '#43246A', '#2B1648'],
    wood: ['#4E3220', '#43291A', '#382216', '#2C1A10', '#21130C'],
    rail: ['#1E1A1E', '#181418', '#120E12', '#0A080A'],
    trim: ['#FFE070', '#F0C040', '#B88A18'],
    tray: '#120C08',
  },
  player_banked: {
    felt: ['#8A1C22', '#7A181E', '#9A2228', '#68141A'],
    wood: ['#7A3A22', '#6C321C', '#5C2A18', '#4C2212', '#3C1A0E'],
    rail: ['#241414', '#1C1010', '#140A0A', '#0C0606'],
    trim: ['#E0B860', '#C09840', '#8C6A24'],
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
  // lacquered hardwood: long horizontal grain streaks, faint seams every 8 rows (seamless both ways)
  for (let y = 0; y < 16; y++) {
    const phase = (y * 7) % 16;
    for (let x = 0; x < 16; x++) {
      const streak = Math.sin(((x + phase) / 16) * Math.PI * 2) + 0.35 * Math.sin(((x * 3 + y * 5) / 16) * Math.PI * 2);
      let k = 1;
      if (streak > 1.05) k = 0;
      else if (streak < -1.0) k = 2;
      if (r() < 0.025) k = 2;
      if (y % 8 === 7) k = 3;
      put(img, x, y, c(t.wood[k]));
    }
  }
  return img;
}

/** Padded leather: highlight band, tuft lines every 4 px, darker underside. Rows run along the rail. */
export function railTexture(theme) {
  const t = THEMES[theme];
  const img = image(16, 16);
  for (let y = 0; y < 16; y++)
    for (let x = 0; x < 16; x++) {
      const band = y % 8;
      let k = band <= 1 ? 0 : band <= 4 ? 1 : band <= 6 ? 2 : 3;
      if (x % 4 === 3 && band >= 2 && band <= 5) k = Math.min(3, k + 1);
      put(img, x, y, c(t.rail[k]));
    }
  for (let x = 1; x < 16; x += 4) P(img, x, 1, t.rail[0]);
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

/** Roulette bowl: polished wood rim + darker cone floor (the rotor itself is drawn by the table renderer). */
export function bowlTexture(theme) {
  const t = THEMES[theme];
  const img = image(16, 16);
  for (let y = 0; y < 16; y++)
    for (let x = 0; x < 16; x++) {
      const d = Math.hypot(x + 0.5 - 8, y + 0.5 - 8);
      const k = d > 7 ? 4 : d > 5.5 ? 1 : d > 3 ? 2 : 3;
      put(img, x, y, c(t.wood[k]));
    }
  for (let a = 0; a < 16; a++) {
    const ang = (a / 16) * Math.PI * 2;
    P(img, Math.round(8 + Math.cos(ang) * 6), Math.round(8 + Math.sin(ang) * 6), t.trim[1]);
  }
  return img;
}

// ---- footprint outline (rounded rectangle) ---------------------------------------------------------------------
const OUT = { cx: 8, hx: 24, hz: 16 };

function radii(shape) {
  if (shape === 'crescent') return { front: 16, back: 0 };
  if (shape === 'oval') return { front: 16, back: 16 };
  return { front: 0, back: 0 };
}

/** Z interval [z0, z1] of a rounded rect inset by `inset` at x (px), or null. */
function span(x, shape, inset) {
  const { front, back } = radii(shape);
  const hx = OUT.hx - inset;
  const hz = OUT.hz - inset;
  const ax = Math.abs(x - OUT.cx);
  if (ax > hx) return null;
  const edge = (rc) => {
    const r = Math.max(0, rc - inset);
    if (r <= 0 || ax <= hx - r) return hz;
    const d = ax - (hx - r);
    return hz - r + Math.sqrt(Math.max(0, r * r - d * d));
  };
  return [-edge(back), edge(front)];
}

const q = (v) => Math.round(v * 2) / 2; // half-pixel steps: smooth enough, few elements

/** Interval set minus interval set. */
function minus(a, b) {
  let out = a.slice();
  for (const [b0, b1] of b) {
    const next = [];
    for (const [a0, a1] of out) {
      if (b1 <= a0 || b0 >= a1) next.push([a0, a1]);
      else {
        if (b0 > a0) next.push([a0, b0]);
        if (b1 < a1) next.push([b1, a1]);
      }
    }
    out = next;
  }
  return out.filter(([z0, z1]) => z1 - z0 > 1e-6);
}
const inter = (a, b) => {
  const out = [];
  for (const [a0, a1] of a) for (const [b0, b1] of b) {
    const z0 = Math.max(a0, b0);
    const z1 = Math.min(a1, b1);
    if (z1 > z0) out.push([z0, z1]);
  }
  return out;
};
const iv = (s) => (s && q(s[1]) - q(s[0]) > 1e-6 ? [[q(s[0]) || 0, q(s[1]) || 0]] : []);

/** Roulette: wheel bowl circle (FeltFrame wheel centre (-0.5, 0) blocks, Ø1.0) and betting layout rectangle. */
const BOWL = { cx: -8, cz: 0, r: 8 };
const LAYOUT = { x0: 0, x1: 30, z0: -14, z1: 14 };

function circleSpan(x, r) {
  const d = x - BOWL.cx;
  if (Math.abs(d) >= r) return [];
  const h = Math.sqrt(r * r - d * d);
  return [[q(BOWL.cz - h), q(BOWL.cz + h)]];
}

/**
 * Material layers of one 1-px strip at x (centre xm): list of {mat, y0, y1, zs: [[z0,z1]…]}.
 * Stack: legs (separate), apron 8..11, slab 11..12.5, trim 12.5..13, felt 13..14, rail 13..15 (rounded profile),
 * crescent dealer ledge 13..14 (wood), roulette deck 13..14 + bowl rim 13..15.5 + bowl floor 13..13.25.
 */
function strip(xm, shape) {
  const out = [];
  const outer = iv(span(xm, shape, 0));
  if (!outer.length) return out;
  const inner = iv(span(xm, shape, RAIL));
  const apron = iv(span(xm, shape, 1.5));
  out.push({ mat: 'wood', y0: 8, y1: 11, zs: apron });
  out.push({ mat: 'rail', y0: 7.5, y1: 8, zs: iv(span(xm, shape, 2)) }); // dark kick band under the apron
  out.push({ mat: 'wood', y0: 11, y1: 12.5, zs: outer });
  out.push({ mat: 'trim', y0: 12.5, y1: 13, zs: outer });
  let felt = inner;
  let deck = [];
  let bowlRing = [];
  let bowlFloor = [];
  if (shape === 'roulette') {
    // wheel housing: a raised polished-wood cabinet over column r-1 with the bowl sunk into it (the rotor is drawn
    // by the table renderer at the felt height), a brass ring round the bowl; the betting layout is felt
    const layout = xm > LAYOUT.x0 && xm < LAYOUT.x1 ? [[LAYOUT.z0, LAYOUT.z1]] : [];
    felt = inter(inner, layout);
    const bowl = circleSpan(xm, BOWL.r - 1.5);
    bowlRing = minus(circleSpan(xm, BOWL.r - 1), bowl);
    bowlFloor = bowl;
    deck = minus(minus(inner, felt), bowl);
  }
  let rail = minus(outer, inner);
  let ledge = [];
  if (shape === 'crescent' && inner.length) {
    // dealer side: a low wooden ledge instead of the padded rail along the straight back edge
    ledge = rail.filter(([z0, z1]) => z1 <= inner[0][0] + 1e-6);
    rail = rail.filter(([z0, z1]) => z1 > inner[0][0] + 1e-6);
  }
  out.push({ mat: 'felt', y0: 13, y1: FELT_TOP, zs: felt });
  if (deck.length) out.push({ mat: 'wood', y0: 13, y1: shape === 'roulette' ? 15 : 14, zs: deck });
  if (ledge.length) out.push({ mat: 'wood', y0: 13, y1: 14, zs: ledge });
  if (rail.length) {
    const high = shape === 'craps' ? 16 : RAIL_TOP;
    out.push({ mat: 'rail', y0: 13, y1: high - 0.5, zs: rail });
    // rounded top of the padded rail: half a pixel narrower on each side
    const top = rail.map(([z0, z1]) => [z0 + 0.5, z1 - 0.5]).filter(([z0, z1]) => z1 > z0);
    out.push({ mat: 'rail', y0: high - 0.5, y1: high, zs: top });
  }
  if (bowlRing.length) out.push({ mat: 'trim', y0: 15, y1: 15.25, zs: bowlRing });
  if (bowlFloor.length) out.push({ mat: 'bowl', y0: 12.5, y1: 13.5, zs: bowlFloor });
  return out;
}

/** Legs (px, local): corner legs inset along the outline + middle legs on the long sides. */
function legs(shape) {
  const { front, back } = radii(shape);
  const list = [];
  const corner = (sx, sz, rc) => {
    if (rc > 0) {
      const cx = OUT.cx + sx * (OUT.hx - rc);
      const cz = sz * (OUT.hz - rc);
      const d = (rc - 4.5) / Math.SQRT2;
      list.push([cx + sx * d, cz + sz * d]);
    } else list.push([OUT.cx + sx * (OUT.hx - 3.5), sz * (OUT.hz - 3.5)]);
  };
  corner(-1, 1, front);
  corner(1, 1, front);
  corner(-1, -1, back);
  corner(1, -1, back);
  list.push([OUT.cx, OUT.hz - 3.5], [OUT.cx, -(OUT.hz - 3.5)]);
  // turned legs: square upper post, slimmer lower post, dark foot
  const box = (x, z, h, mat, y0, y1) => ({ mat, x0: q(x - h), x1: q(x + h), z0: q(z - h), z1: q(z + h), y0, y1 });
  return list.flatMap(([x, z]) => [box(x, z, 1.5, 'wood', 4, 7.5), box(x, z, 1, 'wood', 1, 4), box(x, z, 1.5, 'rail', 0, 1)]);
}

/** Dealer chip tray (card tables and poker): recessed rack in the back-centre cell. */
function tray(shape) {
  if (shape !== 'crescent' && shape !== 'oval') return [];
  return [{ mat: 'tray', x0: 1, x1: 15, z0: -13.5, z1: -10.5, y0: 13, y1: 14.25 }];
}

/** All boxes of a table in local px: {mat, x0, x1, y0, y1, z0, z1}. Strips are merged when identical. */
export function tableBoxes(shape) {
  const boxes = [];
  // strips per 1 px column, clipped to cell columns so no box crosses a block boundary
  const cols = [];
  for (let x = -16; x < 32; x++) cols.push({ x0: x, x1: x + 1, layers: strip(x + 0.5, shape) });
  // merge identical neighbouring columns inside the same cell column
  const merged = [];
  for (const col of cols) {
    const prev = merged[merged.length - 1];
    const sameCell = prev && Math.floor(prev.x0 / 16) === Math.floor(col.x0 / 16);
    if (sameCell && JSON.stringify(prev.layers) === JSON.stringify(col.layers)) prev.x1 = col.x1;
    else merged.push({ ...col });
  }
  for (const col of merged)
    for (const l of col.layers)
      for (const [z0, z1] of l.zs) {
        // split at the cell row boundary z = 0
        const cuts = z0 < 0 && z1 > 0 ? [[z0, 0], [0, z1]] : [[z0, z1]];
        for (const [a, b] of cuts) boxes.push({ mat: l.mat, x0: col.x0, x1: col.x1, y0: l.y0, y1: l.y1, z0: a, z1: b });
      }
  // merge boxes that continue each other along x inside one cell (e.g. the square back of a crescent)
  const keyOf = (b) => `${b.mat}|${b.y0}|${b.y1}|${b.z0}|${b.z1}|${Math.floor(b.x0 / 16)}`;
  const open = new Map();
  const mergedBoxes = [];
  for (const b of boxes.sort((a, z) => a.x0 - z.x0)) {
    const k = keyOf(b);
    const prev = open.get(k);
    if (prev && prev.x1 === b.x0) prev.x1 = b.x1;
    else {
      const nb = { ...b };
      open.set(k, nb);
      mergedBoxes.push(nb);
    }
  }
  boxes.length = 0;
  boxes.push(...mergedBoxes);
  for (const b of [...legs(shape), ...tray(shape)]) {
    // split legs / tray at cell boundaries
    const xs = [b.x0, ...[0, 16].filter((v) => v > b.x0 && v < b.x1), b.x1];
    const zs = [b.z0, ...[0].filter((v) => v > b.z0 && v < b.z1), b.z1];
    for (let i = 0; i + 1 < xs.length; i++)
      for (let j = 0; j + 1 < zs.length; j++) boxes.push({ ...b, x0: xs[i], x1: xs[i + 1], z0: zs[j], z1: zs[j + 1] });
  }
  return boxes;
}

/** Cell (r, f) of a local box (boxes never cross cells). */
const cellOf = (b) => [Math.floor((b.x0 + b.x1) / 2 / 16), Math.floor((b.z0 + b.z1) / 2 / 16)];

const r2 = (v) => Math.round(v * 1000) / 1000;

/** Local box in cell (r, f) → block element for facing=north (x' = 16 − (X − 16r), z' = 16 − (Z − 16f)). */
function element(b, r, f, scale) {
  let x0 = 16 - (b.x1 - 16 * r);
  let x1 = 16 - (b.x0 - 16 * r);
  let z0 = 16 - (b.z1 - 16 * f);
  let z1 = 16 - (b.z0 - 16 * f);
  const uvTop = [x0, z0, x1, z1].map(r2);
  const vy = [16 - b.y1, 16 - b.y0].map(r2);
  let y0 = b.y0;
  let y1 = b.y1;
  if (scale) {
    // item miniature: the whole 48 × 32 footprint squeezed into the item cube
    x0 = (32 - b.x1) / 3;
    x1 = (32 - b.x0) / 3;
    z0 = 8 - b.z1 / 3;
    z1 = 8 - b.z0 / 3;
    y0 = b.y0 * 0.5 + 2;
    y1 = b.y1 * 0.5 + 2;
  }
  const tex = `#${b.mat}`;
  const side = (u0, u1) => ({ uv: [r2(u0), vy[0], r2(u1), vy[1]], texture: tex });
  const faces = {
    up: { uv: uvTop, texture: tex },
    down: { uv: uvTop, texture: tex, ...(b.y0 === 0 && !scale ? { cullface: 'down' } : {}) },
    north: side(16 - uvTop[2], 16 - uvTop[0]),
    south: side(uvTop[0], uvTop[2]),
    west: side(uvTop[1], uvTop[3]),
    east: side(16 - uvTop[3], 16 - uvTop[1]),
  };
  return { from: [x0, y0, z0].map(r2), to: [x1, y1, z1].map(r2), faces };
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
  return { parent: 'minecraft:block/block', textures: textures(table.theme), elements };
}

/** Item miniature (whole table, scaled into the item cube). */
export function itemModel(table) {
  const elements = tableBoxes(table.shape).map((b) => {
    const [r, f] = cellOf(b);
    return element(b, r, f, true);
  });
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
