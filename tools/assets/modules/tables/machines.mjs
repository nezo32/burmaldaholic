// MT-3: upright multi-block machines (Java MachineKind / BoardFrame / MachineShapes). Blockstates (facing × part),
// per-part baked models and loot tables of the slot cabinets (1 × 2), Wheel of Fortune and Plinko (3 × 3) and the
// cashier booths (3 × 2). The main block is the bottom centre; part=single keeps the v0.1 cube model.
//
// Local pixel frame (like furniture.mjs, plus height): X = players' right (main block X∈[0,16]), Y up (main block
// Y∈[0,16]), Z toward the players (main block Z∈[0,16]); mapped per cell for facing=north (players on the north side).
// The slot's upper cabinet and the wheel are drawn by their v0.1 renderers (now standing on real blocks), so those
// cells carry no geometry of their own; Plinko and the cashier are fully modelled. Pure + deterministic.
import { JAVA_ASSETS, json, png } from '../../lib/emit.mjs';
import * as A from './machine_art.mjs';

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
    bulbs: `${MT}/bulbs`,
  };
  if (m.kind === 'slot') {
    const tier = tierOf(m);
    for (const k of ['cab', 'reels', 'topper', 'base', 'deck']) out[k] = `${MT}/slot_${tier}_${k}`;
    out.particle = out.cab;
  }
  if (m.kind === 'cashier') {
    const v = m.name === 'nether_cashier' ? 'nether_' : '';
    for (const k of ['marble', 'panel', 'grille', 'shelves', 'sign', 'lamp']) out[k] = `${MT}/${v}cashier_${k}`;
    out.particle = out.panel;
  }
  return out;
}

/** Machine textures written by this module (16 × 16 each). */
export function machineTextures() {
  const list = [['bulbs', A.bulbs()]];
  for (const tier of Object.keys(A.SLOT_TIERS)) {
    list.push([`slot_${tier}_cab`, A.slotSide(tier)], [`slot_${tier}_reels`, A.slotReels(tier)], [`slot_${tier}_topper`, A.slotTopper(tier)],
      [`slot_${tier}_base`, A.slotBase(tier)], [`slot_${tier}_deck`, A.slotDeck(tier)]);
  }
  const oak = ['#B07A44', '#9C6A3A', '#865A30', '#6E4826'];
  const crimson = ['#8A3A4A', '#6E2E3C', '#56222E', '#3E1822'];
  for (const [v, wood, dark] of [['', oak, false], ['nether_', crimson, true]]) {
    list.push([`${v}cashier_marble`, A.marble(dark)], [`${v}cashier_panel`, A.panel(wood)], [`${v}cashier_grille`, A.grille()],
      [`${v}cashier_shelves`, A.shelves()], [`${v}cashier_sign`, A.signPlaque()], [`${v}cashier_lamp`, A.lamp()]);
  }
  return list;
}

/**
 * Local boxes of a whole machine: {mat, x0, x1, y0, y1, z0, z1, front?, frontTex?, upTex?, emit?} (px). `front` maps
 * a texture over a local rectangle {tex, X0, X1, Y0, Y1} on the players' face (stretched, so the v0.1 face art lines
 * up with its renderer); `frontTex` / `upTex` swap one face's texture; `emit` = light_emission (lit at night).
 */
export function machineBoxes(kind) {
  const B = [];
  const box = (mat, x0, y0, z0, x1, y1, z1, extra = {}) => B.push({ mat, x0, x1, y0, y1, z0, z1, ...extra });
  if (kind === 'slot') {
    // lower cabinet: dark plinth, tier body with speaker / gem / coin-tray front, lit button deck, light strips
    box('dark', 0.5, 0, 1.5, 15.5, 1, 14.5);
    box('cab', 1, 1, 2, 15, 16, 14, { frontTex: 'base' });
    box('cab', 1, 9, 14, 15, 10.5, 16, { upTex: 'deck', emit: 9 });
    box('bulbs', 1, 1, 14, 1.75, 9, 14.75, { emit: 13 });
    box('bulbs', 14.25, 1, 14, 15, 9, 14.75, { emit: 13 });
    // upper cabinet (the renderer draws its animated cabinet just outside this once synced): body, lit reel glass
    // set back behind the renderer's face plane, lit topper sign
    box('cab', 1.1, 16, 2.1, 14.9, 28.5, 13.6);
    box('reels', 2, 17.5, 13.6, 14, 27.5, 13.85, { emit: 9 });
    box('topper', 1.6, 28.5, 3, 14.4, 31.9, 13.4, { emit: 14 });
  }
  if (kind === 'wheel') {
    // lacquered plinth with a lit bulb strip and brass edge, and a post up into the wheel (drawn by the renderer)
    box('dark', -14, 0, 4, 30, 3, 14);
    box('bulbs', -13, 1, 14, 29, 2.5, 14.5, { emit: 12 });
    box('trim', -14, 3, 4, 30, 4, 14);
    box('wood', 6, 4, 4, 10, 16, 8);
  }
  if (kind === 'plinko') {
    // base cabinet with a brass kick, upright board cabinet, board (v0.1 face art stretched to the board height),
    // lit bulb strips on both sides, lit marquee on top
    const boardY0 = 8;
    const boardH = 33; // 2.0625 blocks
    box('dark', -15, 0, 0, 31, 1, 16);
    box('wood', -15, 1, 0, 31, 8, 16);
    box('trim', -15, 7, 0, 31, 8, 16);
    box('wood', -15, 8, 4, 31, 47, 15);
    const bx0 = 8 - boardH / 2;
    box('front', bx0, boardY0, 15, bx0 + boardH, boardY0 + boardH, 15.5, { front: { tex: 'front', X0: bx0, X1: bx0 + boardH, Y0: boardY0, Y1: boardY0 + boardH } });
    box('bulbs', -15, boardY0, 15, bx0, boardY0 + boardH, 16, { emit: 12 });
    box('bulbs', bx0 + boardH, boardY0, 15, 31, boardY0 + boardH, 16, { emit: 12 });
    box('dark', -15, 41, 4, 31, 47, 16, { frontTex: 'bulbs', emit: 10 });
    box('trim', -15, 40, 15, 31, 41, 16);
  }
  if (kind === 'cashier') {
    // counter: dark kick, raised-panel front with brass pilasters, the v0.1 cashier face as the centre panel, marble
    // top overhanging toward the players
    box('dark', -16, 0, 0, 32, 1.5, 15);
    box('panel', -16, 1.5, 0, 32, 14, 14.5);
    box('front', 1, 2, 14.5, 15, 13.5, 15, { front: { tex: 'front', X0: 0, X1: 16, Y0: 0, Y1: 16 } });
    for (const x of [-16, -0.5, 15.5, 31]) box('trim', x, 1.5, 14.5, x + 1, 14, 15);
    box('marble', -16, 14, 0, 32, 16, 16);
    // booth above: back shelves with chip racks, side walls, a brass grille across the front, brass posts,
    // a sign board (the word is drawn by the cashier sign renderer) with two lit lamps
    box('shelves', -15, 16, 2, 31, 29, 4);
    box('panel', -16, 16, 2, -15, 29, 14);
    box('panel', 31, 16, 2, 32, 29, 14);
    box('grille', -15, 17.5, 12, 31, 28.5, 12.5);
    for (const x of [-16, -0.5, 15.5, 31]) box('trim', x, 16, 12.5, x + 1, 29, 13.5);
    box('dark', -16, 29, 2, 32, 32, 14);
    box('sign', -8, 29.25, 14, 24, 31.75, 14.5, { emit: 6 });
    box('lamp', -13, 27, 13.5, -10, 28.5, 15.5, { emit: 15 });
    box('lamp', 26, 27, 13.5, 29, 28.5, 15.5, { emit: 15 });
  }
  return B;
}

/** Splits boxes at cell boundaries (x multiples of 16, y multiples of 16). */
function cellsOf(boxes) {
  const out = [];
  for (const b of boxes) {
    const xs = [b.x0];
    for (let v = -16; v <= 32; v += 16) if (v > b.x0 && v < b.x1) xs.push(v);
    xs.push(b.x1);
    const ys = [b.y0];
    for (let v = 16; v <= 48; v += 16) if (v > b.y0 && v < b.y1) ys.push(v);
    ys.push(b.y1);
    for (let i = 0; i + 1 < xs.length; i++)
      for (let j = 0; j + 1 < ys.length; j++) out.push({ ...b, x0: xs[i], x1: xs[i + 1], y0: ys[j], y1: ys[j + 1] });
  }
  return out;
}

/** Local box in cell (r, u) → block element for facing=north. */
function element(b, r, u) {
  const x0 = 16 - (b.x1 - 16 * r);
  const x1 = 16 - (b.x0 - 16 * r);
  const z0 = 16 - b.z1;
  const z1 = 16 - b.z0;
  const y0 = b.y0 - 16 * u;
  const y1 = b.y1 - 16 * u;
  const top = [x0, z0, x1, z1].map(r2);
  const vy = [16 - y1, 16 - y0].map(r2);
  const tex = `#${b.mat === 'front' ? 'side' : b.mat}`;
  const side = (u0, u1, t = tex) => ({ uv: [r2(u0), vy[0], r2(u1), vy[1]], texture: t });
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
  if (b.front) {
    // players' face (block north for facing=north): the art stretched over the mapped local rectangle
    const f = b.front;
    const U = (x) => ((x - f.X0) / (f.X1 - f.X0)) * 16;
    const V = (y) => ((f.Y1 - y) / (f.Y1 - f.Y0)) * 16;
    faces.north = { uv: [r2(U(b.x0)), r2(V(b.y1)), r2(U(b.x1)), r2(V(b.y0))], texture: `#${f.tex}` };
  }
  return { from: [x0, y0, z0].map(r2), to: [x1, y1, z1].map(r2), ...(b.emit ? { light_emission: b.emit } : {}), faces };
}

export function machinePartModel(m, part) {
  const [r, u] = PART_CELL[part];
  const elements = cellsOf(machineBoxes(m.kind))
    .filter((b) => Math.floor((b.x0 + b.x1) / 2 / 16) === r && Math.floor((b.y0 + b.y1) / 2 / 16) === u)
    .map((b) => element(b, r, u));
  const model = { parent: 'minecraft:block/block', textures: textures(m), elements };
  if (m.kind === 'cashier') model.render_type = 'minecraft:cutout';
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
