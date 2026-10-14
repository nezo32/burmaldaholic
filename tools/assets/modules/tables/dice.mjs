// Dice, puck and craps / duel props (docs/design/visual/tables.md §4, §5): 16-px die art drawn ×1 (craps) and
// ×2 in 40-px cells (Dice Duel), 3D tumble frames with no readable pips, win / idle / land frames, the puck flip,
// the stickman's stick, total badges, the dice cup, the vs badge, crack overlays and the house stamp.
import { BONE, GOLD, INK } from './theme.mjs';
import { P, R, affine, blend, c, disc, grid, halo, image, inked, over, pad, polygon, ring, rrect, scaleUp, shine, star, vstrip, hstrip } from './draw.mjs';

const PIP = '#2A1A3C';
const PIPS = {
  1: [],
  2: [[10, 2], [2, 10]],
  3: [[10, 2], [6, 6], [2, 10]],
  4: [[2, 2], [10, 2], [2, 10], [10, 10]],
  5: [[2, 2], [10, 2], [6, 6], [2, 10], [10, 10]],
  6: [[2, 2], [10, 2], [2, 6], [10, 6], [2, 10], [10, 10]],
};

/** Die face art 16 × 16 (15 × 15 face + 1 px depth to the lower right), not inked. Pips are rounded 3 × 3. */
export function dieArt(face, { red1 = true } = {}) {
  const img = image(16, 16);
  rrect(img, 1, 1, 15, 15, '#A090C0', 2);
  rrect(img, 0, 0, 15, 15, BONE, 2);
  R(img, 2, 0, 11, 1, '#FFFFFF');
  R(img, 0, 2, 1, 11, '#FFFFFF');
  R(img, 2, 14, 11, 1, '#D0C4E4');
  R(img, 14, 2, 1, 11, '#D0C4E4');
  for (const [x, y] of PIPS[face]) {
    R(img, x, y + 1, 3, 1, PIP);
    R(img, x + 1, y, 1, 3, PIP);
    for (const [dx, dy] of [[0, 0], [2, 0], [0, 2]]) P(img, x + dx, y + dy, '#8C7CA8');
    P(img, x + 2, y + 2, '#6A5A7A');
  }
  if (face === 1) {
    const col = red1 ? ['#D83440', '#8C1834', '#FF6E6A'] : [PIP, '#140A20', '#6A5A7A'];
    rrect(img, 5, 5, 5, 5, col[0], 1);
    R(img, 5, 6, 1, 3, col[1]);
    R(img, 6, 5, 3, 1, col[1]);
    P(img, 8, 8, col[2]);
    P(img, 9, 7, col[2]);
  }
  return img;
}

/** Small die for the craps felt: 18 × 18 (inked). */
export const dieSmall = (face) => inked(dieArt(face), INK, 1);

/** Big die cell 40 × 40: the 16-px art ×2, inked, with a soft drop shadow. */
export function dieBig(face, art = dieArt(face)) {
  const big = inked(scaleUp(art, 2), INK, 1);
  const cell = image(40, 40);
  // shadow
  for (let y = 0; y < 6; y++) for (let x = 0; x < 30; x++) if (Math.hypot((x + 0.5 - 15) / 15, (y + 0.5 - 3) / 3) <= 1) blend(cell, 6 + x, 32 + y, c(INK), 0.35);
  over(cell, big, 3, 2);
  return cell;
}

/** Big die sheet row: base, land ×2 (squash, bright rim), win ×6 (gold halo pulse + orbiting glint), idle ×4 (shine). */
export function dieBigFrames(face) {
  const base = dieArt(face);
  const frames = [dieBig(face)];
  // land: squash 0.85 / 1.1, then a bright rim
  const sq = affine(inked(scaleUp(base, 2), INK, 1), 40, 40, { sx: 1.1, sy: 0.85, oy: 36, ox: 20.5 });
  frames.push(sq);
  frames.push(halo(dieBig(face), c('#FFFFFF'), 1, 0.9));
  for (let f = 0; f < 6; f++) {
    const k = 0.55 + 0.45 * Math.sin((f / 6) * Math.PI * 2);
    const img = halo(dieBig(face), c(GOLD[1]), 2, k);
    const a = (f / 6) * Math.PI * 2;
    star(img, Math.round(20 + 15 * Math.cos(a)), Math.round(18 + 15 * Math.sin(a)), 2, c(GOLD[0]), 1);
    frames.push(img);
  }
  for (let f = 0; f < 4; f++) frames.push(shine(dieBig(face), (f + 1) / 5, 6, 0.55));
  return frames;
}

// ---- 3D tumble (no readable pips) ------------------------------------------------------------------------------------
function rot(v, axis, ang) {
  const [x, y, z] = v;
  const [u, w, s] = axis;
  const cs = Math.cos(ang);
  const sn = Math.sin(ang);
  const d = u * x + w * y + s * z;
  return [
    u * d * (1 - cs) + x * cs + (-s * y + w * z) * sn,
    w * d * (1 - cs) + y * cs + (s * x - u * z) * sn,
    s * d * (1 - cs) + z * cs + (-w * x + u * y) * sn,
  ];
}

/** Tumbling die frame (size px square, 8 frames per turn): a flat-shaded cube with blank faces and a smudge. */
export function tumble(f, size = 18, edge = 5.2) {
  const img = image(size, size);
  const n = Math.hypot(1, 0.7, 0.35);
  const axis = [1 / n, 0.7 / n, 0.35 / n];
  const ang = (f / 8) * Math.PI * 2 + 0.4;
  const faces = [
    [[1, 0, 0], [0, 1, 0], [0, 0, 1]],
    [[-1, 0, 0], [0, 1, 0], [0, 0, 1]],
    [[0, 1, 0], [1, 0, 0], [0, 0, 1]],
    [[0, -1, 0], [1, 0, 0], [0, 0, 1]],
    [[0, 0, 1], [1, 0, 0], [0, 1, 0]],
    [[0, 0, -1], [1, 0, 0], [0, 1, 0]],
  ];
  const L = [-0.5, -0.65, 0.58]; // light from the top-left, toward the viewer
  const cx = size / 2;
  const cy = size / 2;
  const drawn = [];
  for (const [nrm, a, b] of faces) {
    const N = rot(nrm, axis, ang);
    if (N[2] <= 0.02) continue;
    const corners = [[1, 1], [1, -1], [-1, -1], [-1, 1]].map(([p, q]) => {
      const v = [0, 1, 2].map((i) => (nrm[i] + a[i] * p + b[i] * q) * edge);
      const r = rot(v, axis, ang);
      return [cx + r[0], cy + r[1]];
    });
    const lum = Math.max(0, N[0] * L[0] + N[1] * L[1] + N[2] * L[2]);
    drawn.push({ z: N[2], corners, lum, centre: rot(nrm.map((v) => v * edge), axis, ang) });
  }
  drawn.sort((p, q) => p.z - q.z);
  for (const d of drawn) {
    const col = d.lum > 0.8 ? '#FFFFFF' : d.lum > 0.55 ? BONE : d.lum > 0.3 ? '#C0B0DC' : '#8C7CA8';
    polygon(img, d.corners, col);
    // an unreadable smudge where the pips would be
    blend(img, Math.round(cx + d.centre[0] - 0.5), Math.round(cy + d.centre[1] - 0.5), c('#8C7CA8'), 0.45);
  }
  return inked(img, INK, 1);
}

/** Motion streak behind a flying die (code rotates by 90° steps to face away from the motion): 12 × 18, 2 frames. */
export function streak() {
  return hstrip([0, 1].map((f) => {
    const img = image(12, 18);
    for (const [y, len, k] of [[4, 10, 0.5], [9, 12, 0.7], [14, 8, 0.4]]) for (let x = 0; x < len - f * 2; x++) blend(img, 12 - x - 1, y, c(BONE), k * (1 - x / len));
    return img;
  }));
}

/** Die shadow 18 × 6. */
export function dieShadow() {
  const img = image(18, 6);
  for (let y = 0; y < 6; y++) for (let x = 0; x < 18; x++) if (Math.hypot((x + 0.5 - 9) / 9, (y + 0.5 - 3) / 3) <= 1) blend(img, x, y, c(INK), 0.45);
  return img;
}

// ---- puck --------------------------------------------------------------------------------------------------------------
/** Puck flip strip, 18 × 18 each: off, flip (off side), edge, flip (on side), on. The ON/OFF word is runtime text. */
export function puckFrames() {
  const disc16 = (on, sx) => {
    const img = image(16, 16);
    const face = on ? [BONE, '#C0B0DC', '#FFFFFF'] : ['#26202C', '#0C0A10', '#4A4050'];
    const ringC = on ? '#26202C' : BONE;
    for (let y = 0; y < 16; y++)
      for (let x = 0; x < 16; x++) {
        const dx = (x + 0.5 - 8) / (7.5 * sx);
        const dy = (y + 0.5 - 8) / 7.5;
        const d = Math.hypot(dx, dy);
        if (d > 1) continue;
        let col = face[0];
        if (d > 0.86) col = face[1];
        else if (d > 0.7 && d <= 0.8) col = ringC;
        if (d > 0.86 && dx + dy < -0.6) col = face[2];
        P(img, x, y, col);
      }
    return img;
  };
  const edge = image(16, 16);
  rrect(edge, 6, 0, 4, 16, '#8C7CA8', 1);
  R(edge, 7, 1, 1, 14, '#FFFFFF');
  return [disc16(false, 1), disc16(false, 0.5), edge, disc16(true, 0.5), disc16(true, 1)].map((i) => inked(i, INK, 1));
}

// ---- stick, back wall, badges, flame ----------------------------------------------------------------------------------
/** Stickman's stick 64 × 10: wooden dowel, brass ferrule, a hook at the left end. */
export function stick() {
  return inked(grid([
    '.YYYY.........................................................',
    'Yo..Yww.......................................................',
    'Y....wWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWGG',
    'Yo...wwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwGg',
    '.YY...dddddddddddddddddddddddddddddddddddddddddddddddddddddddgg',
    '..YY..........................................................',
    '...YY.........................................................',
    '....Y.........................................................',
  ], { Y: GOLD[1], o: GOLD[3], W: '#C8903C', w: '#9C6430', d: '#6E4422', G: GOLD[1], g: GOLD[3] }), INK, 0.9);
}

/** Back wall tile 16 × 8: rubber pyramids lit from the top-left (tiles horizontally). */
export function backWall() {
  const img = image(16, 8);
  R(img, 0, 0, 16, 8, '#26202C');
  for (let py = 0; py < 2; py++)
    for (let px = 0; px < 4; px++) {
      const x0 = px * 4 + (py ? 2 : 0);
      const y0 = py * 4;
      for (let y = 0; y < 4; y++)
        for (let x = 0; x < 4; x++) {
          const u = x - 1.5;
          const v = y - 1.5;
          const col = Math.abs(u) + Math.abs(v) > 2.6 ? '#1A1420' : u + v < 0 ? (u < v ? '#6A5A7A' : '#524460') : u > v ? '#3A3048' : '#2A2236';
          P(img, (x0 + x) & 15, y0 + y, col);
        }
    }
  R(img, 0, 7, 16, 1, '#0C0A10');
  return img;
}

/** Total badge 24 × 24 for the runtime total: neutral | natural (green) | craps (red) | point (gold). */
export function totalBadge(kind) {
  const ringC = { neutral: ['#BE5AFF', '#783CBE'], natural: ['#80FF40', '#2E7A1E'], craps: ['#FF6E6A', '#8C1834'], point: [GOLD[0], GOLD[3]] }[kind];
  const img = image(24, 24);
  disc(img, 12, 12.8, 11.6, c(INK), 0.5);
  disc(img, 12, 12, 11.5, c(INK));
  disc(img, 12, 12, 10.5, c(ringC[1]));
  disc(img, 12, 11.5, 10, c(ringC[0]));
  disc(img, 12, 12, 8.4, c(INK));
  disc(img, 12, 12, 7.6, c('#26103C'));
  disc(img, 12, 11, 6.6, c('#3A1A5C'));
  P(img, 8, 6, '#FFFFFF', 0.7);
  return img;
}

/** Point glow around a place box (48 × 40), 4 pulse frames. */
export function pointGlow() {
  return vstrip([0, 1, 2, 3].map((f) => {
    const img = image(48, 40);
    const k = [0.45, 0.75, 1, 0.75][f];
    for (let d = 0; d < 3; d++) {
      const a = (d === 1 ? 1 : 0.45) * k;
      const col = d === 1 ? GOLD[1] : GOLD[0];
      R(img, d, d, 48 - 2 * d, 1, col, a);
      R(img, d, 39 - d, 48 - 2 * d, 1, col, a);
      R(img, d, d, 1, 40 - 2 * d, col, a);
      R(img, 47 - d, d, 1, 40 - 2 * d, col, a);
    }
    for (const [x, y] of [[1, 1], [46, 1], [1, 38], [46, 38]]) P(img, x, y, '#FFFFFF', k);
    return img;
  }));
}

/** Hot-shooter flame 32 × 10, 3 frames (animated). */
export function flame() {
  return vstrip([0, 1, 2].map((f) => {
    const img = image(32, 10);
    for (let x = 0; x < 32; x++) {
      const h = 3 + Math.round(3 * Math.abs(Math.sin(x * 0.55 + f * 1.4)) + ((x + f) % 5 === 0 ? 1 : 0));
      for (let y = 10 - h; y < 10; y++) P(img, x, y, y === 10 - h ? '#FFE070' : y < 10 - h + 2 ? '#FFB030' : y < 8 ? '#FF7A1A' : '#C83A08');
    }
    return img;
  }));
}

// ---- Dice Duel props ----------------------------------------------------------------------------------------------
const CUP = [
  '....oooooooo....',
  '..ooYYYYYYYYoo..',
  '.oYyyyyyyyyyyYo.',
  '.oYYYYYYYYYYYYo.',
  '..LlllllllllL...',
  '..LlLllllllllL..',
  '..LlLlllllllL...',
  '..LlLllllllllL..',
  '...LlLllllllL...',
  '...LlLlllllll...',
  '...LlLllllllL...',
  '...GgGgggggGG...',
  '...LlLlllllL....',
  '....LlLllllL....',
  '....LLLLLLLL....',
  '................',
];
/** Leather dice cup (16-px art ×2 in 40 cells): rest, shake L, shake R, shake up, tip 1 (20°), tip 2 (35°). */
export function cupFrames() {
  const art = grid(CUP, { o: INK, Y: '#8E5A34', y: '#3A2010', L: '#6E3E22', l: '#9C5A30', G: GOLD[1], g: GOLD[3] });
  const big = inked(scaleUp(art, 2), INK, 1);
  const place = (tf) => affine(big, 40, 40, { ox: 20, oy: 37, px: big.w / 2, py: big.h, ...tf });
  return [place({}), place({ dx: -2, rot: -6 }), place({ dx: 2, rot: 6 }), place({ dy: -3 }), place({ rot: 20, dx: 2 }), place({ rot: 35, dx: 4 })];
}

/** "vs" badge without letters: two crossed dice on a round plaque (32 × 32); frames: level, tilt L, tilt R. */
export function vsBadge() {
  const base = image(32, 32);
  disc(base, 16, 16.8, 15.6, c(INK), 0.5);
  disc(base, 16, 16, 15.5, c(INK));
  disc(base, 16, 16, 14.5, c(GOLD[3]));
  disc(base, 16, 15.5, 14, c(GOLD[1]));
  disc(base, 16, 16, 11.6, c('#3A1A5C'));
  disc(base, 16, 16, 10.8, c('#26103C'));
  const d1 = affine(inked(dieArt(5), INK, 1), 22, 22, { rot: -25, px: 9, py: 9, ox: 11, oy: 11 });
  const d2 = affine(inked(dieArt(2), INK, 1), 22, 22, { rot: 25, px: 9, py: 9, ox: 11, oy: 11 });
  const frame = (r) => {
    const img = affine(base, 32, 32, { rot: r, px: 16, py: 16, ox: 16, oy: 16 });
    over(img, d1, 2, 5);
    over(img, d2, 9, 5);
    return img;
  };
  return [frame(0), frame(-12), frame(12)];
}

/** Crack overlay for a losing total badge (32 × 24), 2 frames (spreading). */
export function crackFrames() {
  const paths = [[[16, 0], [14, 5], [17, 9], [13, 14], [15, 19]], [[14, 5], [8, 7], [4, 6]], [[17, 9], [23, 11], [28, 10]], [[13, 14], [9, 18], [6, 23]], [[15, 19], [19, 23]]];
  return [2, 5].map((n) => {
    const img = image(32, 24);
    for (const p of paths.slice(0, n))
      for (let i = 0; i + 1 < p.length; i++) {
        const [ax, ay] = p[i];
        const [bx, by] = p[i + 1];
        const steps = Math.max(Math.abs(bx - ax), Math.abs(by - ay));
        for (let s = 0; s <= steps; s++) {
          const x = Math.round(ax + ((bx - ax) * s) / steps);
          const y = Math.round(ay + ((by - ay) * s) / steps);
          P(img, x, y, INK);
          P(img, x + 1, y, '#FFFFFF', 0.35);
        }
      }
    return img;
  });
}

/** Total plaque for the duel (32 × 24): neutral | win (gold) | lose (grey); the number is runtime text. */
export function duelPlaque(kind) {
  const col = { neutral: ['#BE5AFF', '#783CBE', '#26103C'], win: [GOLD[0], GOLD[3], '#3A2A08'], lose: ['#8C7CA8', '#4A3A5A', '#1C1622'] }[kind];
  const img = image(32, 24);
  rrect(img, 0, 1, 32, 23, INK, 3);
  rrect(img, 0, 0, 32, 23, INK, 3);
  rrect(img, 1, 1, 30, 21, col[1], 3);
  rrect(img, 1, 1, 30, 20, col[0], 3);
  rrect(img, 3, 3, 26, 16, col[2], 2);
  R(img, 4, 3, 24, 1, '#FFFFFF', 0.15);
  return img;
}

/** House stamp (40 × 40): a red wax seal holding two small dice showing 3 and 4 (the house tie on 7). */
export function houseStamp() {
  const img = image(40, 40);
  for (let k = 0; k < 16; k++) {
    const a = (k / 16) * Math.PI * 2;
    disc(img, 20 + 16.5 * Math.cos(a), 20 + 16.5 * Math.sin(a), 3.2, c('#8C1834'));
  }
  disc(img, 20, 20, 17, c('#8C1834'));
  disc(img, 20, 19.5, 16, c('#D83440'));
  ring(img, 20, 20, 13, 1, c('#FF6E6A'));
  ring(img, 20, 20, 11.5, 1, c('#8C1834'));
  const small = (f) => inked(dieArt(f, { red1: false }), '#5A0A18', 1);
  over(img, affine(small(3), 20, 20, { rot: -12, px: 9, py: 9, ox: 10, oy: 10 }), 3, 10);
  over(img, affine(small(4), 20, 20, { rot: 12, px: 9, py: 9, ox: 10, oy: 10 }), 18, 10);
  P(img, 12, 7, '#FFFFFF', 0.6);
  return inked(img, INK, 0.7);
}

export { pad };

// ---- dice kit (DCa, PHASE2B §3.1/§3.3, CS §4.2): orientation atlas, shadows, rest glint, dust, spark -------------
// The atlas is a ray-cast cube seen through an OBLIQUE projection (screen u = x, v = y − κ·z; tray frame: x right, y toward
// the viewer, z up): the top face of a resting die maps texel-for-texel onto the screen, so its pips are exactly the
// pixel-designed pips of the face texture, and the face toward the viewer shows as a shaded band under it (a cube seen
// from the front and above). Faces seen at a grazing angle show their bevelled bone colour without pips (no squashed
// marks). Every face texel is lit by the face normal (Lambert, light from the top-left front), its outer ring by a normal
// tilted toward the edge (a rounded, lit bevel), silhouette corners are rounded by 1 px and the whole die is inked.
// Frame order is shared with Java (core/anim/dice/DieAtlasTable, generated by the dicekit module): frame = 16·g + j,
// g = CubeGroup element (Rz(k·90°)·Up(face), index 4·(face−1)+k), j = world offset: yaw {0, −15°, +15°} × tilt {none,
// Rx +30°, Rx −30°, Ry +30°, Ry −30°} (j = 5·yaw + tilt), j = 15 a 45° yaw. Rest poses (j = 0, 5, 10) are exact frames.

export const ATLAS_CELLS = [14, 18, 22, 30, 32];
export const ATLAS_COLS = 20;
export const ATLAS_FRAMES = 384;
/** Oblique depth factor: a unit of height moves κ px up the screen (the front band of a resting die is κ·edge tall). */
export const ATLAS_KAPPA = 0.22;

const SQ = Math.sqrt(0.5);
const qmul = (a, b) => [
  a[0] * b[0] - a[1] * b[1] - a[2] * b[2] - a[3] * b[3],
  a[0] * b[1] + a[1] * b[0] + a[2] * b[3] - a[3] * b[2],
  a[0] * b[2] - a[1] * b[3] + a[2] * b[0] + a[3] * b[1],
  a[0] * b[3] + a[1] * b[2] - a[2] * b[1] + a[3] * b[0],
];
const qaxis = (x, y, z, rad) => {
  const n = Math.hypot(x, y, z);
  if (!n || !rad) return [1, 0, 0, 0];
  const s = Math.sin(rad / 2) / n;
  return [Math.cos(rad / 2), x * s, y * s, z * s];
};
const snapQ = (q) =>
  q.map((v) => {
    const a = Math.abs(v);
    const s = Math.sign(v);
    return a < 0.25 ? 0 : a < 0.6 ? s * 0.5 : a < 0.85 ? s * SQ : s;
  });
const canon = (q) => {
  const i = q.findIndex((v) => Math.abs(v) > 1e-12);
  return i >= 0 && q[i] < 0 ? q.map((v) => -v + 0) : q.map((v) => v + 0);
};
/** Face label → outward normal on the canonical die (CubeGroup): 1 +z, 6 −z, 2 −y, 5 +y, 3 +x, 4 −x. */
export const FACE_NORMAL = { 1: [0, 0, 1], 2: [0, -1, 0], 3: [1, 0, 0], 4: [-1, 0, 0], 5: [0, 1, 0], 6: [0, 0, -1] };
const FACE_OF_AXIS = { '0,1': 3, '0,-1': 4, '1,1': 5, '1,-1': 2, '2,1': 1, '2,-1': 6 };

/** The 24 cube rotations in CubeGroup order. */
export function cubeGroup() {
  const out = [];
  for (let u = 1; u <= 6; u++) {
    const n = FACE_NORMAL[u];
    const up = n[2] === 1 ? [1, 0, 0, 0] : n[2] === -1 ? qaxis(1, 0, 0, Math.PI) : qaxis(n[1], -n[0], 0, Math.PI / 2);
    for (let k = 0; k < 4; k++) out.push(canon(snapQ(qmul(qaxis(0, 0, 1, (k * Math.PI) / 2), up))));
  }
  return out;
}

/** The 16 world-frame offsets (see the header). */
export function atlasOffsets() {
  const out = [];
  const deg = Math.PI / 180;
  for (const yaw of [0, -15, 15]) {
    const Y = qaxis(0, 0, 1, yaw * deg);
    for (const t of [null, [1, 0, 0, 30], [1, 0, 0, -30], [0, 1, 0, 30], [0, 1, 0, -30]]) out.push(t ? qmul(qaxis(t[0], t[1], t[2], t[3] * deg), Y) : Y);
  }
  out.push(qaxis(0, 0, 1, 45 * deg));
  return out;
}

/** The 384 frame quaternions {w, x, y, z} (frame = 16·g + j). */
export function atlasQuats() {
  const g = cubeGroup();
  const o = atlasOffsets();
  const out = [];
  for (let i = 0; i < 24; i++) for (let j = 0; j < 16; j++) out.push(qmul(o[j], g[i]));
  return out;
}

export const qrot = (q, v) => {
  const [w, x, y, z] = q;
  const tx = 2 * (y * v[2] - z * v[1]);
  const ty = 2 * (z * v[0] - x * v[2]);
  const tz = 2 * (x * v[1] - y * v[0]);
  return [v[0] + w * tx + (y * tz - z * ty), v[1] + w * ty + (z * tx - x * tz), v[2] + w * tz + (x * ty - y * tx)];
};
const conj = (q) => [q[0], -q[1], -q[2], -q[3]];

/** Edge of the die in px (the face texture size): the cell minus the 1-px ink outline on each side. */
export const atlasEdge = (cell) => cell - 2;

/** Frame size (px) of a cell: room for the cube's longest oblique extent plus ink and margin, even. */
export const atlasFrame = (cell) => {
  const f = Math.ceil(Math.sqrt(3) * atlasEdge(cell) * Math.sqrt(1 + ATLAS_KAPPA * ATLAS_KAPPA)) + 4;
  return f + (f & 1);
};

// ---- face textures ---------------------------------------------------------------------------------------------
/** Standard layouts in face texel space (col, row ∈ {0, 1, 2} = left/top, centre, right/bottom); 2, 3 run top-left →
 * bottom-right, 6 is two columns. Opposite faces sum to 7 (labels of FACE_NORMAL). */
export const PIP_LAYOUT = {
  1: [[1, 1]],
  2: [[0, 0], [2, 2]],
  3: [[0, 0], [1, 1], [2, 2]],
  4: [[0, 0], [2, 0], [0, 2], [2, 2]],
  5: [[0, 0], [2, 0], [1, 1], [0, 2], [2, 2]],
  6: [[0, 0], [2, 0], [0, 1], [2, 1], [0, 2], [2, 2]],
};
// texel codes: 0 bone, 1 pip, 2 pip shade (top-left, the hollow's inner shadow), 3 pip light (bottom-right), 4–6 red
const nearestParity = (x, parity) => {
  const a = Math.floor(x);
  const c = [a - 1, a, a + 1, a + 2].filter((v) => v >= 2 && ((v & 1) === parity));
  return c.reduce((b, v) => (Math.abs(v - x) < Math.abs(b - x) ? v : b), c[0]);
};
/** Pip size (px) and the three grid positions (top-left texel of a pip) for an edge of E px; symmetric and centred. */
export function pipGrid(E, big = false) {
  let d = nearestParity(E * (big ? 0.36 : 0.2), E & 1);
  let a = Math.round(E * 0.25 - d / 2);
  // three rows (the 6) need a clear gap between pips: pull the outer rows out, then shrink the pip
  while (!big && (E - d) / 2 - a < d + 1) {
    if (a > 1) a--;
    else d -= 2;
  }
  return { d, pos: [a, (E - d) / 2, E - d - a] };
}
/** Pip mask of size d: a pixel disc (corners clipped from 4 px up). */
function pipMask(d) {
  const m = [];
  for (let y = 0; y < d; y++)
    for (let x = 0; x < d; x++) {
      const cx = x + 0.5 - d / 2;
      const cy = y + 0.5 - d / 2;
      if (d < 4 || Math.hypot(cx, cy) <= d / 2 + 0.2) m.push([x, y, cx, cy]);
    }
  return m;
}
const TEX = new Map();
/** Face texture codes (Uint8Array E·E, row-major) of label {@code face}. */
export function faceTexture(E, face) {
  const key = `${E}:${face}`;
  if (TEX.has(key)) return TEX.get(key);
  const t = new Uint8Array(E * E);
  const red = face === 1;
  const { d, pos } = pipGrid(E, red);
  const mask = pipMask(d);
  for (const [col, row] of PIP_LAYOUT[face]) {
    const x0 = red ? (E - d) / 2 : pos[col];
    const y0 = red ? (E - d) / 2 : pos[row];
    for (const [mx, my, cx, cy] of mask) {
      // a hollow: the outer top-left arc is the inner shadow (from 4 px up); the rest is the pip colour
      const code = d >= 4 && Math.hypot(cx, cy) > d / 2 - 1 && cx + cy < -0.5 ? 2 : 1;
      t[(y0 + my) * E + x0 + mx] = red ? code + 3 : code;
    }
  }
  TEX.set(key, t);
  return t;
}

// ---- shading ---------------------------------------------------------------------------------------------------
const SHADES = ['#FFFFFF', '#F4ECF8', '#E2D6EE', '#C8BADC', '#A898C4', '#84749E', '#655680'];
const PIP_C = ['#2A1A3C', '#160C22', '#4C3C62'];
const RED_C = ['#D83440', '#8C1834', '#FF6E6A'];
const LIGHT = (() => {
  const v = [-0.45, 0.25, 0.86];
  const n = Math.hypot(...v);
  return v.map((x) => x / n);
})();
const lambert = (n) => Math.max(0, n[0] * LIGHT[0] + n[1] * LIGHT[1] + n[2] * LIGHT[2]);
const shadeIndex = (i) => (i > 0.95 ? 0 : i > 0.8 ? 1 : i > 0.62 ? 2 : i > 0.45 ? 3 : i > 0.3 ? 4 : i > 0.16 ? 5 : 6);
/** Faces seen flatter than this (|cos| between normal and view) are drawn without pips. */
const PIP_MIN_FACING = 0.45;

/** Hit of one sub-sample: −1 background, else {face, i, j} packed as face·100000 + j·300 + i. */
function hit(qi, u, v, hp, E) {
  // oblique ray through (u, v): all points (u, v + κt, t) project to (u, v); direction into the scene −(0, κ, 1)
  const ob = qrot(qi, [u, v, 0]);
  const wb = qrot(qi, [0, -ATLAS_KAPPA, -1]);
  let t0 = -1e9;
  let t1 = 1e9;
  let axis = -1;
  let sign = 0;
  for (let i = 0; i < 3; i++) {
    if (Math.abs(wb[i]) < 1e-12) {
      if (Math.abs(ob[i]) > hp) return -1;
      continue;
    }
    let a = (-hp - ob[i]) / wb[i];
    let b = (hp - ob[i]) / wb[i];
    let s = -1;
    if (a > b) {
      [a, b] = [b, a];
      s = 1;
    }
    if (a > t0) {
      t0 = a;
      axis = i;
      sign = s;
    }
    if (b < t1) t1 = b;
  }
  if (t0 > t1 || axis < 0) return -1;
  const p = [ob[0] + wb[0] * t0, ob[1] + wb[1] * t0, ob[2] + wb[2] * t0];
  const face = FACE_OF_AXIS[`${axis},${sign}`];
  const [e1, e2] = FACE_BASIS[face];
  const s = (p[0] * e1[0] + p[1] * e1[1] + p[2] * e1[2]) / hp;
  const t = (p[0] * e2[0] + p[1] * e2[1] + p[2] * e2[2]) / hp;
  const i = Math.min(E - 1, Math.max(0, Math.floor(((s + 1) / 2) * E)));
  const j = Math.min(E - 1, Math.max(0, Math.floor(((t + 1) / 2) * E)));
  return face * 100000 + j * 300 + i;
}
// face texture axes (texel col along e1, row along e2): the top face of a resting die is col = x, row = y on screen
export const FACE_BASIS = {
  1: [[1, 0, 0], [0, 1, 0]],
  6: [[1, 0, 0], [0, -1, 0]],
  3: [[0, -1, 0], [0, 0, -1]],
  4: [[0, 1, 0], [0, 0, -1]],
  5: [[1, 0, 0], [0, 0, -1]],
  2: [[-1, 0, 0], [0, 0, -1]],
};

/** One atlas frame (F × F) for cell size {@code cell} and orientation q. */
export function dieFrame(cell, q, { sub = 3 } = {}) {
  const F = atlasFrame(cell);
  const E = atlasEdge(cell);
  const hp = E / 2;
  const qi = conj(q);
  const W = F - 2;
  const cls = new Int32Array(W * W).fill(-1);
  const counts = new Map();
  for (let y = 0; y < W; y++)
    for (let x = 0; x < W; x++) {
      counts.clear();
      let hits = 0;
      for (let j = 0; j < sub; j++)
        for (let i = 0; i < sub; i++) {
          const k = hit(qi, x + 1 + (i + 0.5) / sub - F / 2, y + 1 + (j + 0.5) / sub - F / 2, hp, E);
          if (k < 0) continue;
          hits++;
          counts.set(k, (counts.get(k) ?? 0) + 1);
        }
      if (hits * 2 <= sub * sub) continue;
      let best = -1;
      let bn = -1;
      for (const [k, n] of counts) if (n > bn || (n === bn && k < best)) [best, bn] = [k, n];
      cls[y * W + x] = best;
    }
  // rounded silhouette corners: drop an opaque pixel with background on two adjacent sides and the diagonal
  if (cell >= 18) {
    const bg = (x, y) => x < 0 || y < 0 || x >= W || y >= W || cls[y * W + x] < 0;
    const drop = [];
    for (let y = 0; y < W; y++)
      for (let x = 0; x < W; x++) {
        if (cls[y * W + x] < 0) continue;
        for (const [dx, dy] of [[1, 1], [1, -1], [-1, 1], [-1, -1]])
          if (bg(x + dx, y) && bg(x, y + dy) && bg(x + dx, y + dy) && !bg(x - dx, y) && !bg(x, y - dy)) drop.push(y * W + x);
      }
    for (const o of drop) cls[o] = -1;
  }
  const view = (() => {
    const v = [0, ATLAS_KAPPA, 1];
    const n = Math.hypot(...v);
    return v.map((c) => c / n);
  })();
  const inner = image(W, W);
  for (let y = 0; y < W; y++)
    for (let x = 0; x < W; x++) {
      const k = cls[y * W + x];
      if (k < 0) continue;
      const face = Math.floor(k / 100000);
      const tj = Math.floor((k % 100000) / 300);
      const ti = (k % 100000) % 300;
      const n = qrot(q, FACE_NORMAL[face]);
      const facing = n[0] * view[0] + n[1] * view[1] + n[2] * view[2];
      // bevel: the outer texel ring is lit with the normal tilted toward its edge
      const ring = Math.min(ti, tj, E - 1 - ti, E - 1 - tj);
      let lit = lambert(n);
      if (ring === 0) {
        const [e1, e2] = FACE_BASIS[face];
        const ds = ti === 0 ? -1 : ti === E - 1 ? 1 : 0;
        const dt = tj === 0 ? -1 : tj === E - 1 ? 1 : 0;
        const bodyTilt = [0, 1, 2].map((a) => FACE_NORMAL[face][a] + 0.9 * (ds * e1[a] + dt * e2[a]));
        const len = Math.hypot(...bodyTilt);
        lit = lambert(qrot(q, bodyTilt.map((c) => c / len)));
      }
      let sh = shadeIndex(0.18 + 0.82 * lit);
      if (ring === 0) sh = Math.min(6, sh);
      let hex = SHADES[sh];
      if (ring > 0 && facing >= PIP_MIN_FACING) {
        const code = faceTexture(E, face)[tj * E + ti];
        const dim = sh >= 4;
        if (code >= 1 && code <= 3) hex = code === 2 && !dim ? PIP_C[1] : PIP_C[0];
        else if (code >= 4) hex = code === 5 && !dim ? RED_C[1] : RED_C[0];
      }
      P(inner, x, y, hex);
    }
  return inked(inner, INK, 1);
}

/** The atlas sheet of one cell: 384 frames in a 20-column grid. */
export function dieAtlas(cell) {
  const F = atlasFrame(cell);
  const qs = atlasQuats();
  const img = image(F * ATLAS_COLS, F * Math.ceil(ATLAS_FRAMES / ATLAS_COLS));
  qs.forEach((q, i) => over(img, dieFrame(cell, q), (i % ATLAS_COLS) * F, Math.floor(i / ATLAS_COLS) * F));
  return img;
}

/** The face label showing up (+z) under q. */
export function faceUp(q) {
  let best = 1;
  let bz = -2;
  for (let f = 1; f <= 6; f++) {
    const z = qrot(q, FACE_NORMAL[f])[2];
    if (z > bz) {
      bz = z;
      best = f;
    }
  }
  return best;
}

/** Oblique screen point (frame px) of a body-frame point rotated by q. */
const project = (cell, q, body) => {
  const F = atlasFrame(cell);
  const w = qrot(q, body);
  return [w[0] + F / 2, w[1] - ATLAS_KAPPA * w[2] + F / 2];
};

/** Projected corners of the up face of a frame, in frame px {[u, v] × 4} (face texture order: (0,0), (E,0), (E,E), (0,E)). */
export function topQuad(cell, q) {
  const hp = atlasEdge(cell) / 2;
  const f = faceUp(q);
  const n = FACE_NORMAL[f];
  const [e1, e2] = FACE_BASIS[f];
  return [[-1, -1], [1, -1], [1, 1], [-1, 1]].map(([a, b]) => {
    const [u, v] = project(cell, q, [0, 1, 2].map((i) => (n[i] + a * e1[i] + b * e2[i]) * hp));
    return [Math.round(u * 1000) / 1000, Math.round(v * 1000) / 1000];
  });
}

/** Screen position (frame px, fractional) of every pip centre of the up face of q, in PIP_LAYOUT order. */
export function upPipCentres(cell, q) {
  const E = atlasEdge(cell);
  const hp = E / 2;
  const f = faceUp(q);
  const n = FACE_NORMAL[f];
  const [e1, e2] = FACE_BASIS[f];
  const red = f === 1;
  const { d, pos } = pipGrid(E, red);
  return PIP_LAYOUT[f].map(([col, row]) => {
    const cx = (red ? (E - d) / 2 : pos[col]) + d / 2;
    const cy = (red ? (E - d) / 2 : pos[row]) + d / 2;
    const s = (cx / E) * 2 - 1;
    const t = (cy / E) * 2 - 1;
    return project(cell, q, [0, 1, 2].map((i) => (n[i] + s * e1[i] + t * e2[i]) * hp));
  });
}

/** Soft elliptic die shadow in INK (alpha is applied at runtime); size 0 = 100 %, 1 = 80 %, 2 = 60 %. */
export function dieShadowCell(cell, size) {
  const k = [1, 0.8, 0.6][size];
  const w = Math.max(4, Math.round(cell * k));
  const h = Math.max(2, Math.round(cell * 0.42 * k));
  const img = image(w, h);
  for (let y = 0; y < h; y++)
    for (let x = 0; x < w; x++) {
      const d = Math.hypot((x + 0.5 - w / 2) / (w / 2), (y + 0.5 - h / 2) / (h / 2));
      if (d <= 1) blend(img, x, y, c(INK), d < 0.6 ? 1 : d < 0.85 ? 0.7 : 0.4);
    }
  return img;
}

/** Rim highlight over the identity rest frame: a white 1-px rim on the top face plus a glint, F × F. */
export function dieRestGlint(cell) {
  const F = atlasFrame(cell);
  const img = image(F, F);
  const quad = topQuad(cell, [1, 0, 0, 0]);
  const x0 = Math.round(quad[0][0]);
  const x1 = Math.round(quad[1][0]) - 1;
  const y0 = Math.round(quad[0][1]);
  const y1 = Math.round(quad[2][1]) - 1;
  for (let x = x0; x <= x1; x++) {
    P(img, x, y0, '#FFFFFF', 0.9);
    P(img, x, y1, '#FFFFFF', 0.5);
  }
  for (let y = y0 + 1; y < y1; y++) {
    P(img, x0, y, '#FFFFFF', 0.9);
    P(img, x1, y, '#FFFFFF', 0.5);
  }
  star(img, x0 + 2, y0 + 2, cell >= 22 ? 2 : 1, c('#FFFFFF'), 1);
  return img;
}

/** Felt dust puff 5 × 5, 4 frames (white; tinted at runtime). */
export function diceDust() {
  return vstrip([0, 1, 2, 3].map((f) => {
    const img = image(5, 5);
    const r = [1.2, 1.8, 2.3, 2.5][f];
    const k = [0.9, 0.7, 0.45, 0.2][f];
    disc(img, 2.5, 2.5, r, c('#FFFFFF'), k);
    if (f < 2) P(img, 2, 2, '#FFFFFF');
    return img;
  }));
}

/** Die–die / win spark 3 × 3, 3 frames. */
export function diceSpark() {
  return vstrip([0, 1, 2].map((f) => {
    const img = image(3, 3);
    if (f === 0) {
      R(img, 0, 1, 3, 1, '#FFFFFF');
      R(img, 1, 0, 1, 3, '#FFFFFF');
    } else if (f === 1) {
      P(img, 1, 1, '#FFFFFF');
      for (const [x, y] of [[0, 0], [2, 0], [0, 2], [2, 2]]) P(img, x, y, '#FFF1A0', 0.8);
    } else {
      P(img, 1, 1, '#FFF1A0', 0.6);
    }
    return img;
  }));
}

/** Everything the atlas JSON holds (also feeds the Java table). */
export function dieAtlasData() {
  const qs = atlasQuats();
  const restFrames = [];
  for (let g = 0; g < 24; g++) for (const j of [0, 5, 10]) restFrames.push(16 * g + j);
  return {
    version: 2,
    projection: 'oblique',
    kappa: ATLAS_KAPPA,
    cols: ATLAS_COLS,
    frames: qs.map((q, i) => ({ i, q, up: faceUp(q) })),
    restFrames,
    cells: ATLAS_CELLS.map((cell) => ({
      cell,
      frame: atlasFrame(cell),
      edge: atlasEdge(cell),
      texture: `gui/tables/core/dice_atlas_${cell}.png`,
      restTop: restFrames.map((i) => ({ i, quad: topQuad(cell, qs[i]) })),
    })),
  };
}

const jnum = (v) => {
  if (Object.is(v, -0) || v === 0) return '0';
  const s = String(v);
  return s.includes('.') || s.includes('e') ? s : `${s}.0`;
};

/** Generated Java constants (core/anim/dice/DieAtlasTable.java) from {@link dieAtlasData}. */
export function dieAtlasJava(data) {
  const q = data.frames.map((f) => f.q.map(jnum).join(', ')).join(',\n\t\t');
  const tops = data.cells.map((c) => `{\n\t\t\t${c.restTop.map((r) => `{${r.quad.flat().map(jnum).join(', ')}}`).join(',\n\t\t\t')}}`).join(',\n\t\t');
  const lines = [
    'package dev.nezo.burmaldaholic.core.anim.dice;',
    '',
    '// GENERATED by tools/assets/modules/dicekit.mjs (tables/dice.mjs dieAtlasJava): do not edit, run (cd tools && npm run gen:assets).',
    '',
    '/**',
    ' * The die orientation atlas (PHASE2B §3.1, CS §4.2): the frame quaternions in the tray frame (frame = 16·g + j; g =',
    ' * {@link CubeGroup} element; j = world offset: yaw {0, −15°, +15°} × tilt {none, Rx +30°, Rx −30°, Ry +30°, Ry −30°} as',
    ' * 5·yaw + tilt, 15 = 45° yaw), the sheet layout and the rest-frame top-face quads. Oblique projection: screen',
    ' * {@code u = x, v = y − KAPPA·z} (a resting top face is texel-exact). Sheets:',
    ' * {@code textures/gui/tables/core/dice_atlas_<cell>.png}, {@link #COLS} columns of {@link #FRAME} px frames, the die',
    ' * centre at the frame centre. Pure data plus an allocation-free nearest-frame lookup.',
    ' */',
    'public final class DieAtlasTable {',
    `\tpublic static final int FRAMES = ${data.frames.length};`,
    `\tpublic static final int COLS = ${data.cols};`,
    `\t/** Oblique depth factor of the rendered frames: height z moves the image KAPPA·z px up. */`,
    `\tpublic static final double KAPPA = ${jnum(data.kappa)};`,
    `\t/** Cell sizes: the die's width at rest in px, including the ink outline. */`,
    `\tpublic static final int[] CELLS = {${data.cells.map((c) => c.cell).join(', ')}};`,
    `\t/** Frame size (px) per cell. */`,
    `\tpublic static final int[] FRAME = {${data.cells.map((c) => c.frame).join(', ')}};`,
    `\t/** Frame quaternions {w, x, y, z} per frame. */`,
    `\tpublic static final double[] Q = {`,
    `\t\t${q}};`,
    `\t/** Face label showing up per frame. */`,
    `\tpublic static final int[] UP = {${data.frames.map((f) => f.up).join(', ')}};`,
    `\t/** Rest frames (j = 0, 5, 10 of each element), the order of {@link #TOP}. */`,
    `\tpublic static final int[] REST_FRAMES = {${data.restFrames.join(', ')}};`,
    `\t/** Per cell, per rest frame: the up face corners {u0, v0, …, u3, v3} in frame px. */`,
    `\tpublic static final double[][][] TOP = {`,
    `\t\t${tops}};`,
    '',
    '\tprivate DieAtlasTable() {}',
    '',
    '\t/** Index of a cell size in {@link #CELLS}; −1 when unknown. */',
    '\tpublic static int cellIndex(int cell) {',
    '\t\tfor (int i = 0; i < CELLS.length; i++) if (CELLS[i] == cell) return i;',
    '\t\treturn -1;',
    '\t}',
    '',
    '\t/** The frame nearest to orientation q (largest |q·f|); allocation-free. */',
    '\tpublic static int frame(double[] q) {',
    '\t\tint best = 0;',
    '\t\tdouble bd = -1;',
    '\t\tfor (int i = 0; i < FRAMES; i++) {',
    '\t\t\tint o = 4 * i;',
    '\t\t\tdouble d = Math.abs(q[0] * Q[o] + q[1] * Q[o + 1] + q[2] * Q[o + 2] + q[3] * Q[o + 3]);',
    '\t\t\tif (d > bd) {',
    '\t\t\t\tbd = d;',
    '\t\t\t\tbest = i;',
    '\t\t\t}',
    '\t\t}',
    '\t\treturn best;',
    '\t}',
    '}',
    '',
  ];
  return lines.join('\n');
}
