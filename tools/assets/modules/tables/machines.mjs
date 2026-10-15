// MT-3: upright multi-block machines (Java MachineKind / BoardFrame / MachineShapes). Blockstates (facing × part),
// per-part baked models and loot tables of the slot cabinets (1 × 2), Wheel of Fortune and Plinko (3 × 3) and the
// cashier booths (3 × 2). The main block is the bottom centre; part=single keeps the v0.1 cube model.
//
// Local pixel frame (like furniture.mjs, plus height): X = players' right (main block X∈[0,16]), Y up (main block
// Y∈[0,16]), Z toward the players (main block Z∈[0,16]); mapped per cell for facing=north (players on the north side).
// Every machine is complete without its renderer (slot cabinets, wheel stand + rim, Plinko frame, cashier booth); the
// renderers draw the moving parts (reels, wheel disc, Plinko board / balls). Lit parts use light_emission. Pure.
import { JAVA_ASSETS, json, png } from '../../lib/emit.mjs';
import * as A from './machine_art.mjs';
import * as H from './art_hd.mjs';

const M = `${JAVA_ASSETS}/models/block/core/machines`;
const DATA = 'src/main/resources/data/burmaldaholic';
const FACINGS = { north: 0, east: 90, south: 180, west: 270 };
const r2 = (v) => Math.round(v * 1000) / 1000;

/** (r, u) of every part name — TablePart. */
const PART_CELL = {
  main: [0, 0], left: [-1, 0], right: [1, 0],
  upper_left: [-1, 1], upper: [0, 1], upper_right: [1, 1],
  top_left: [-1, 2], top: [0, 2], top_right: [1, 2],
};

const KINDS = {
  slot: { width: 1, height: 2 },
  wheel: { width: 3, height: 3 },
  plinko: { width: 3, height: 3 },
  cashier: { width: 3, height: 2 },
};

export function partsOf(kind) {
  const { width, height } = KINDS[kind];
  const half = (width - 1) / 2;
  const out = [];
  for (const [name, [r, u]] of Object.entries(PART_CELL)) if (Math.abs(r) <= half && u < height) out.push(name);
  return out;
}

/** Every machine block: name, kind, v0.1 model (part=single, and slot main), texture set. */
export const MACHINES = [
  ['slot_machine_copper', 'slot', 'burmaldaholic:block/slots/slot_machine_copper', 'burmaldaholic:block/slots/slot_machine_copper_side'],
  ['slot_machine_gold', 'slot', 'burmaldaholic:block/slots/slot_machine_gold', 'burmaldaholic:block/slots/slot_machine_gold_side'],
  ['slot_machine_netherite', 'slot', 'burmaldaholic:block/slots/slot_machine_netherite', 'burmaldaholic:block/slots/slot_machine_netherite_side'],
  ['wheel_of_fortune', 'wheel', 'burmaldaholic:block/extras/wheel_of_fortune', 'burmaldaholic:block/extras/wheel_of_fortune_side'],
  ['plinko_machine', 'plinko', 'burmaldaholic:block/extras/plinko_machine', 'burmaldaholic:block/extras/plinko_machine_side'],
  ['cashier', 'cashier', 'burmaldaholic:block/core/cashier', 'burmaldaholic:block/core/cashier_side'],
  ['nether_cashier', 'cashier', 'burmaldaholic:block/core/nether_cashier', 'burmaldaholic:block/core/nether_cashier_side'],
].map(([name, kind, single, side]) => ({ name, kind, single, side }));

const MT = 'burmaldaholic:block/core/machines';
const tierOf = (m) => m.name.replace('slot_machine_', '');

function textures(m) {
  const theme = m.name === 'nether_cashier' ? 'player_banked' : m.kind === 'plinko' || m.kind === 'slot' ? 'high_roller' : 'standard';
  const t = `burmaldaholic:block/core/tables/${theme}`;
  const base = m.single.replace(/\/[^/]+$/, `/${m.name}`);
  const out = {
    particle: m.side,
    side: m.side,
    front: `${base}_front`,
    wood: `${t}/wood`,
    trim: `${t}/trim`,
    dark: `${t}/rail`,
    bulbs: `${MT}/bulbs2`,
    ppanel: `${MT}/plinko_side`,
    safe: `${MT}/cashier_safe`,
    chrome: `${MT}/chrome`,
  };
  if (m.kind === 'slot') {
    const tier = tierOf(m);
    for (const k of ['cab', 'reels', 'topper', 'base', 'deck', 'glow']) out[k] = `${MT}/slot_${tier}_${k}`;
    out.ball = `${MT}/lever_ball`;
    out.particle = out.cab;
  }
  if (m.kind === 'cashier') {
    const nether = m.name === 'nether_cashier';
    const v = nether ? 'nether_' : '';
    for (const k of ['panel', 'lamp']) out[k] = `${MT}/${v}cashier_${k}`;
    out.shelves = `${MT}/cashier_wall`;
    out.marble = nether ? 'minecraft:block/polished_blackstone' : 'minecraft:block/quartz_block_top';
    out.trim = nether ? 'minecraft:block/gilded_blackstone' : out.trim;
    out.glass = nether ? 'minecraft:block/red_stained_glass' : 'minecraft:block/white_stained_glass';
    out.grille = `${MT}/cashier_grille`;
    out.sign = `${MT}/cashier_sign`;
    out.particle = out.panel;
  }
  if (m.kind === 'plinko') {
    out.board = `${MT}/plinko_board_back`;
    out.marquee = `${MT}/plinko_marquee`;
    out.pbase = `${MT}/plinko_base`;
  }
  if (m.kind === 'wheel') out.red = `${MT}/red_leather`;
  return out;
}

/** Machine textures written by this module (16 × 16 each). */
export function machineTextures() {
  const list = [['bulbs', A.bulbs()], ['bulbs2', H.bulbs2()], ['plinko_side', H.cabinetSide(['#5A3A7A', '#3A1E50', '#2A1440'])],
    ['cashier_wall', H.cashWallHd()], ['cashier_safe', H.safeHd()], ['chrome', A.chrome()], ['lever_ball', A.ball()], ['cashier_grille', A.grille()],
    ['cashier_sign', H.cashierPlateHd()], ['plinko_board_back', A.boardBack()], ['plinko_marquee', A.plinkoMarquee()],
    ['plinko_base', A.plinkoBase()], ['red_leather', A.redLeather()]];
  for (const tier of Object.keys(A.SLOT_TIERS)) {
    list.push([`slot_${tier}_cab`, A.slotSide(tier)], [`slot_${tier}_reels`, H.slotReelsHd(tier, A.SLOT_TIERS[tier].accent)],
      [`slot_${tier}_topper`, H.slotTopperHd(tier, A.SLOT_TIERS[tier])],
      [`slot_${tier}_base`, A.slotBase(tier)], [`slot_${tier}_deck`, A.slotDeck(tier)], [`slot_${tier}_glow`, A.glow(tier)]);
  }
  const oak = ['#B07A44', '#9C6A3A', '#865A30', '#6E4826'];
  const crimson = ['#8A3A4A', '#6E2E3C', '#56222E', '#3E1822'];
  for (const [v, wood, soul] of [['', oak, false], ['nether_', crimson, true]]) {
    list.push([`${v}cashier_panel`, A.panel(wood)], [`${v}cashier_shelves`, A.shelves()], [`${v}cashier_lamp`, A.lampShade(soul)]);
  }
  return list;
}

/**
 * Local geometry of a whole machine: axis boxes {mat, x0, x1, y0, y1, z0, z1, front?, frontTex?, upTex?, emit?} and
 * rotated segments {axis 'z', mat, cx, cy, z0, z1, len, thick, tx, ty, emit?} (px). `front` maps a texture over a local
 * rectangle {tex, X0, X1, Y0, Y1} on the players' face; `frontTex` / `upTex` swap one face's texture; `emit` =
 * light_emission (lit at night; same model format on 26.2 and 26.3).
 */
export function machineGeometry(kind) {
  const B = [];
  const S = [];
  const box = (mat, x0, y0, z0, x1, y1, z1, extra = {}) => B.push({ mat, x0, x1, y0, y1, z0, z1, ...extra });
  if (kind === 'slot') {
    // lower cabinet: dark plinth, tier body with belly art, chrome front edges, lit side strips, coin tray, lit
    // button deck, pull lever on the right (chrome stick + red ball)
    box('dark', 0.5, 0, 1.5, 15.5, 1, 14.5);
    box('cab', 1, 1, 2, 15, 16, 14, { frontTex: 'base' });
    box('chrome', 1, 1, 13.75, 1.75, 16, 14.25);
    box('chrome', 14.25, 1, 13.75, 15, 16, 14.25);
    box('glow', 0.75, 2, 10.5, 1, 15, 13.5, { emit: 12 });
    box('glow', 15, 2, 10.5, 15.25, 15, 13.5, { emit: 12 });
    box('chrome', 3, 2, 14, 13, 3, 15.75);
    box('dark', 3.5, 2.75, 14.25, 12.5, 3.05, 15.5);
    box('cab', 1, 11, 14, 15, 12.5, 16.25, { upTex: 'deck', emit: 8 });
    box('chrome', 15, 12, 7.5, 17, 15, 9.5);
    const lever = { axis: 'x', angle: -15, origin: [16, 15, 8.5] };
    box('chrome', 15.5, 15, 8, 16.5, 22, 9, { rot: lever });
    box('ball', 15, 22, 7.5, 17, 24, 9.5, { rot: lever });
    // upper cabinet (the slot renderer's animated cabinet sits just outside when synced): body, chrome edges, lit
    // strips, reel window (aligned with the renderer's reels, y 19..28) behind a proud chrome bezel, lit topper
    box('cab', 1.1, 16, 2.1, 14.9, 29, 13.6);
    box('chrome', 1.1, 16, 13.6, 1.6, 29, 14.1);
    box('chrome', 14.4, 16, 13.6, 14.9, 29, 14.1);
    box('glow', 0.85, 16, 10.5, 1.1, 28, 13.5, { emit: 12 });
    box('glow', 14.9, 16, 10.5, 15.15, 28, 13.5, { emit: 12 });
    box('reels', 2, 19, 13.6, 14, 28, 13.85, { emit: 9, frontUv: [2, 4, 14, 13] });
    box('chrome', 1.75, 18.25, 13.6, 14.25, 19, 14.35);
    box('chrome', 1.75, 28, 13.6, 14.25, 28.75, 14.35);
    box('chrome', 1.75, 19, 13.6, 2, 28, 14.35);
    box('chrome', 14, 19, 13.6, 14.25, 28, 14.35);
    box('topper', 1.5, 29, 3, 14.5, 34, 13, { emit: 14, frontUv: [1.5, 5, 14.5, 10] });
    box('chrome', 1.75, 34, 3.5, 14.25, 34.5, 12.5);
  }
  if (kind === 'wheel') {
    // 4 px bevelled plinth with a lit bulb strip, rear A-frame to the hub, brass rim ring (3.5 px deep) round the
    // renderer's disc (Ø 2.625 at y 1.625, plane z 9), hub cap and a flapper mount at 12 o'clock
    box('dark', -14, 0, 3, 30, 3, 13);
    box('trim', -13.5, 3, 3.5, 29.5, 4, 12.5);
    box('bulbs', -12, 1, 13, 28, 2.5, 13.5, { emit: 15 });
    const hub = { x: 8, y: 26 };
    for (const sx of [-1, 1]) {
      const fx = hub.x + sx * 8;
      const dx = hub.x - fx;
      const dy = hub.y - 4;
      const L = Math.hypot(dx, dy);
      S.push({ axis: 'z', mat: 'wood', cx: (fx + hub.x) / 2, cy: (4 + hub.y) / 2, z0: 4, z1: 6, len: L, thick: 2, tx: dx / L, ty: dy / L });
    }
    box('wood', 3, 12, 4, 13, 13.5, 6);
    for (const sx of [-1, 1]) for (const sy of [-1, 1]) {
      for (const seg of ringZ(hub.x, hub.y, 22.5, 21, sx, sy, 7, 10.5, 'trim', { emit: 10 })) S.push(seg);
    }
    box('trim', 6, 24, 10, 10, 28, 12);
    box('trim', 7, 46, 7, 9, 48, 11);
    box('red', 7.5, 43, 10.5, 8.5, 46, 11);
  }
  if (kind === 'plinko') {
    // cabinet FRAMES the Plinko board (BoardFrame.of(PLINKO, MAIN): x −14..30, y 8..41, face z 15.5, drawn by the
    // Plinko renderer): base with coin slot + bin-tray lip, lit bulb pillars, crown marquee (sign art, lit)
    box('dark', -16, 0, 0, 32, 1, 16);
    box('wood', -16, 1, 0, 32, 8, 15, { frontTex: 'pbase', sideTex: 'ppanel' });
    box('pbase', -14, 2, 15, 30, 6, 15.25, { emit: 11 }); // lit bin lights + coin plate (day and night)
    box('trim', -14, 7.5, 15, 30, 8.5, 16.5);
    box('board', -14, 8, 4, 30, 41, 15);
    box('wood', -16, 8, 4, -14, 41, 16, { sideTex: 'ppanel' });
    box('bulbs', -16, 8, 15.75, -14, 41, 16.25, { emit: 15 });
    box('wood', 30, 8, 4, 32, 41, 16, { sideTex: 'ppanel' });
    box('bulbs', 30, 8, 15.75, 32, 41, 16.25, { emit: 15 });
    box('trim', -14, 40.5, 15, 30, 41, 16);
    box('dark', -16, 41, 4, 32, 47.5, 16, { frontTex: 'marquee', sideTex: 'ppanel', emit: 15 });
    box('trim', -16, 47.5, 4, 32, 48, 16);
  }
  if (kind === 'cashier') {
    // counter 15/16 high: brass kick, three raised panels, quartz top with a brass edge, a money tray under the window,
    // two banker's lamps on the counter ends
    box('trim', -16, 0, 0, 32, 1, 14.5);
    box('panel', -16, 1, 0, 32, 14, 14.5);
    box('marble', -16, 14, 0, 32, 15, 15.5);
    box('trim', -16, 14, 15.5, 32, 15, 16);
    box('dark', 4, 15, 11, 12, 15.25, 14.5);
    box('trim', 3.5, 15, 14.5, 12.5, 15.5, 15);
    for (const x of [-12, 28]) {
      box('trim', x - 1, 15, 10.5, x + 1, 15.5, 12.5);
      box('trim', x - 0.25, 15.5, 11.25, x + 0.25, 18, 11.75);
      box('lamp', x - 2, 18, 10, x + 2, 19.5, 13, { emit: 14 });
    }
    // booth: back wall with chip shelves, side walls, glass partition in a brass frame with an arched brass grille
    // window over the centre, header with the lit sign plate (the word is drawn by the cashier sign renderer)
    box('panel', -16, 15, 0, 32, 29, 2);
    box('shelves', -15, 16, 2, 31, 28, 2.5);
    box('panel', -16, 15, 2, -15, 29, 9);
    box('panel', 31, 15, 2, 32, 29, 9);
    box('glass', -15, 15.5, 9, 1, 29, 9.5);
    box('glass', 15, 15.5, 9, 31, 29, 9.5);
    box('grille', 1, 15.5, 9, 15, 25, 9.5);
    for (const [x0, x1, y0] of [[1, 3, 25], [3, 5, 26.5], [11, 13, 26.5], [13, 15, 25]]) box('trim', x0, y0, 8.75, x1, 29, 9.75);
    box('trim', 5, 28, 8.75, 11, 29, 9.75);
    for (const x of [-16, 0.5, 15, 31]) box('trim', x, 15, 8.5, x + 1, 29, 10);
    box('dark', -16, 29, 0, 32, 32, 11);
    box('sign', -6, 28.5, 11, 22, 32, 11.5, { emit: 12, frontUv: [0, 0, 16, 16] });
    box('dark', 4, 15, 2.5, 12, 21, 7);
    box('safe', 4.5, 15.5, 7, 11.5, 20.5, 7.25, { frontUv: [0, 0, 16, 16] });
  }
  return { boxes: B, segs: S };
}

/** Quarter of a ring in the XY plane (rotated about Z), like table_geom.arc. */
function ringZ(cx, cy, ro, ri, sx, sy, z0, z1, mat, extra = {}, n = 6) {
  const out = [];
  const step = 90 / n;
  const t = ro - ri;
  const len = 2 * ro * Math.tan(((step / 2) * Math.PI) / 180) * 1.04;
  for (let k = 0; k < n; k++) {
    const phi = ((k + 0.5) * step * Math.PI) / 180;
    const nx = sx * Math.cos(phi);
    const ny = sy * Math.sin(phi);
    const rm = ro - t / 2;
    out.push({ axis: 'z', mat, cx: cx + nx * rm, cy: cy + ny * rm, z0, z1, len, thick: t, tx: -ny, ty: nx, ...extra });
  }
  return out;
}

/** Kept for tests / old callers: the axis boxes only. */
export function machineBoxes(kind) {
  return machineGeometry(kind).boxes;
}

/** Splits boxes at cell boundaries INSIDE the footprint (x 0 / 16 for 3-wide machines, y 16 / 32 for tall ones). */
function cellsOf(kind, boxes) {
  const { width, height } = KINDS[kind];
  const xCuts = width === 3 ? [0, 16] : [];
  const yCuts = [16, 32].filter((v) => v < height * 16);
  const out = [];
  for (const b of boxes) {
    const xs = [b.x0, ...xCuts.filter((v) => v > b.x0 && v < b.x1), b.x1];
    const ys = [b.y0, ...yCuts.filter((v) => v > b.y0 && v < b.y1), b.y1];
    for (let i = 0; i + 1 < xs.length; i++)
      for (let j = 0; j + 1 < ys.length; j++) out.push({ ...b, x0: xs[i], x1: xs[i + 1], y0: ys[j], y1: ys[j + 1] });
  }
  return out;
}

const clampI = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
const cellR = (kind, x) => {
  const half = (KINDS[kind].width - 1) / 2;
  return clampI(Math.floor(x / 16), -half, half);
};
const cellU = (kind, y) => clampI(Math.floor(y / 16), 0, KINDS[kind].height - 1);

/** Local box in cell (r, u) → block element for facing=north. */
function element(b, r, u) {
  const x0 = 16 - (b.x1 - 16 * r);
  const x1 = 16 - (b.x0 - 16 * r);
  const z0 = 16 - b.z1;
  const z1 = 16 - b.z0;
  const y0 = b.y0 - 16 * u;
  const y1 = b.y1 - 16 * u;
  const cl = (v) => r2(clampI(v, 0, 16));
  const top = [x0, z0, x1, z1].map(cl);
  const vy = [16 - clampI(y1, 0, 16), 16 - clampI(y0, 0, 16)].map(r2);
  if (vy[1] - vy[0] < 0.25) vy[1] = Math.min(16, vy[0] + 0.25);
  const tex = `#${b.mat === 'front' ? 'side' : b.mat}`;
  const side = (u0, u1, t = tex) => ({ uv: [cl(Math.min(u0, u1)), vy[0], cl(Math.max(u0, u1)), vy[1]], texture: t });
  const faces = {
    up: { uv: top, texture: tex },
    down: { uv: top, texture: tex, ...(y0 === 0 ? { cullface: 'down' } : {}) },
    north: side(16 - top[2], 16 - top[0]),
    south: side(top[0], top[2]),
    west: side(top[1], top[3]),
    east: side(16 - top[3], 16 - top[1]),
  };
  if (b.frontTex) faces.north = { ...faces.north, texture: `#${b.frontTex}` };
  if (b.upTex) faces.up = { ...faces.up, texture: `#${b.upTex}` };
  if (b.frontUv) faces.north = { ...faces.north, uv: b.frontUv };
  if (b.sideTex) {
    faces.east = { ...faces.east, texture: `#${b.sideTex}` };
    faces.west = { ...faces.west, texture: `#${b.sideTex}` };
  }
  if (b.front) {
    const f = b.front;
    const U = (x) => ((x - f.X0) / (f.X1 - f.X0)) * 16;
    const V = (y) => ((f.Y1 - y) / (f.Y1 - f.Y0)) * 16;
    faces.north = { uv: [r2(U(b.x0)), r2(V(b.y1)), r2(U(b.x1)), r2(V(b.y0))], texture: `#${f.tex}` };
  }
  // a local rotation (axis x or z) flips sign under the north mapping's 180° turn about Y; the origin maps like a point
  const rotation = b.rot
    ? { origin: [r2(16 - (b.rot.origin[0] - 16 * r)), r2(b.rot.origin[1] - 16 * u), r2(16 - b.rot.origin[2])], axis: b.rot.axis, angle: -b.rot.angle }
    : undefined;
  return { from: [x0, y0, z0].map(r2), to: [x1, y1, z1].map(r2), ...(rotation ? { rotation } : {}), ...(b.emit ? { light_emission: b.emit } : {}), faces };
}

/**
 * Segment rotated about Z (XY plane) → block element. The north mapping flips x and z (180° about Y), which turns a
 * rotation about +Z by a into one by −a; computed directly in block axes: angle = atan2(ty, −tx) (JOML: +X → +Y for
 * a positive angle about +Z), folded into [−90°, 90°).
 */
function segElement(sg, r, u) {
  const cx = 16 - (sg.cx - 16 * r);
  const cy = sg.cy - 16 * u;
  const z0 = 16 - sg.z1;
  const z1 = 16 - sg.z0;
  let ang = (Math.atan2(sg.ty, -sg.tx) * 180) / Math.PI;
  while (ang >= 90) ang -= 180;
  while (ang < -90) ang += 180;
  const tex = `#${sg.mat}`;
  const U = Math.min(16, sg.len);
  const T = Math.min(16, Math.max(0.25, sg.thick));
  const D = Math.min(16, Math.max(0.25, z1 - z0));
  const faces = {
    north: { uv: [0, 0, r2(U), r2(T)], texture: tex },
    south: { uv: [0, 0, r2(U), r2(T)], texture: tex },
    up: { uv: [0, 0, r2(U), r2(D)], texture: tex },
    down: { uv: [0, 0, r2(U), r2(D)], texture: tex },
    west: { uv: [0, 0, r2(D), r2(T)], texture: tex },
    east: { uv: [0, 0, r2(D), r2(T)], texture: tex },
  };
  return {
    from: [cx - sg.len / 2, cy - sg.thick / 2, z0].map(r2),
    to: [cx + sg.len / 2, cy + sg.thick / 2, z1].map(r2),
    rotation: { origin: [r2(cx), r2(cy), r2((z0 + z1) / 2)], axis: 'z', angle: r2(ang) },
    ...(sg.emit ? { light_emission: sg.emit } : {}),
    faces,
  };
}

export function machinePartModel(m, part) {
  const [r, u] = PART_CELL[part];
  const g = machineGeometry(m.kind);
  const elements = cellsOf(m.kind, g.boxes)
    .filter((b) => cellR(m.kind, (b.x0 + b.x1) / 2) === r && cellU(m.kind, (b.y0 + b.y1) / 2) === u)
    .map((b) => element(b, r, u));
  for (const sg of g.segs) if (cellR(m.kind, sg.cx) === r && cellU(m.kind, sg.cy) === u) elements.push(segElement(sg, r, u));
  const model = { parent: 'minecraft:block/block', textures: textures(m), elements };
  if (m.kind === 'cashier') model.render_type = u > 0 ? 'minecraft:translucent' : 'minecraft:cutout';
  return model;
}

function modelRef(m, part) {
  if (part === 'single') return m.single;
  return `burmaldaholic:block/core/machines/${m.name}/${part}`;
}

export function machineBlockstate(m) {
  const variants = {};
  for (const [facing, y] of Object.entries(FACINGS))
    for (const part of ['single', ...partsOf(m.kind)]) {
      const model = modelRef(m, part);
      variants[`facing=${facing},part=${part}`] = y ? { model, y } : { model };
    }
  return { variants };
}

export function machineLoot(m) {
  const id = `burmaldaholic:${m.name}`;
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
    random_sequence: `burmaldaholic:blocks/${m.name}`,
  };
}

export function machineOutputs() {
  const out = [];
  for (const [name, img] of machineTextures()) out.push(png(`${JAVA_ASSETS}/textures/block/core/machines/${name}.png`, img));
  for (const m of MACHINES) {
    out.push(json(`${JAVA_ASSETS}/blockstates/${m.name}.json`, machineBlockstate(m)));
    for (const part of partsOf(m.kind)) {
      out.push(json(`${M}/${m.name}/${part}.json`, machinePartModel(m, part)));
    }
    out.push(json(`${DATA}/loot_table/blocks/${m.name}.json`, machineLoot(m)));
  }
  return out;
}
