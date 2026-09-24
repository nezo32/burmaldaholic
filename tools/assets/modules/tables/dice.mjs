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
