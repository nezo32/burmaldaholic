// Roulette wheel art (docs/design/visual/tables.md §3.1): a top-down European wheel as a SPINNABLE SPRITE SET.
// The head is rendered per frame (pockets from the true angle, frets as 1 px brass, upright pocket numbers,
// turret arms), never rotated at runtime, so every frame is crisp pixel art. Light is fixed top-left in screen space.
// One parametric renderer draws the big GUI wheel, the mini wheel and the in-world (BER) texture.
import { GOLD, INK, POCKET, THEME, WHEEL_ORDER, pocketColour } from './theme.mjs';
import { R, c, disc, image, inked, lightAt, put, ramp, ring, textC, textWidth } from './draw.mjs';

const PITCH = 360 / 37;
const DEG = Math.PI / 180;
const angOf = (x, y, cx, cy) => {
  const a = Math.atan2(x + 0.5 - cx, -(y + 0.5 - cy)) / DEG;
  return (a + 360) % 360;
};
const mod = (a, m) => ((a % m) + m) % m;

/** Geometry presets (radii in px, measured from the centre). */
export const HEAD_BIG = { size: 152, lip: [73.5, 76], num: [63.5, 73.5], ring2: [61.5, 63.5], pocket: [49.5, 61.5], ring3: [47.5, 49.5], turret: 10.5, arm: 33, armW: 2.1, knob: 2.8, digits: 68.6, sep: 0.55, fret: 0.9 };
export const HEAD_WORLD = { size: 64, lip: [30.5, 32], num: [26.5, 30.5], ring2: [25.5, 26.5], pocket: [20, 25.5], ring3: [19, 20], turret: 4.5, arm: 14, armW: 1, knob: 1.4, digits: 0, sep: 0.3, fret: 0.45 };
export const HEAD_MINI = { size: 56, lip: [26.5, 28], num: [22.5, 26.5], ring2: [21.5, 22.5], pocket: [16.5, 21.5], ring3: [15.5, 16.5], turret: 4, arm: 11, armW: 0.9, knob: 1.3, digits: 0, sep: 0, fret: 0.3 };
export const BOWL_BIG = { size: 208, head: HEAD_BIG, edge: [101, 104], rim: [93, 101], inlay: [91.8, 93], track: [79, 91.8], gap: [76, 79], deflector: 84, deflectorR: 3.4 };
export const BOWL_WORLD = { size: 64, head: null, edge: [31, 32], rim: [28, 31], inlay: [27.4, 28], track: [22.5, 27.4], gap: [21, 22.5], deflector: 25, deflectorR: 1.3 };
export const BOWL_MINI = { size: 72, head: HEAD_MINI, edge: [34.5, 36], rim: [32, 34.5], inlay: [31.4, 32], track: [29, 31.4], gap: [28, 29], deflector: 30.2, deflectorR: 1.1 };
/** Wheel radius Rw used by the ball path fractions (tables animation §1.3): the outer wall of the ball track. */
export const WHEEL_RADIUS_BIG = 92;

const within = (d, [a, b]) => d >= a && d < b;

/**
 * The rotating head at `angle` degrees (clockwise; 0 = pocket 0 at the top). `blur` > 0 averages ±blur degrees
 * tangentially (spin-speed frames; no digits). Returns a size² image with transparent corners.
 */
export function head(g, angle, { blur = 0, theme = 'village' } = {}) {
  const t = THEME[theme];
  const img = image(g.size, g.size);
  const cx = g.size / 2;
  const cy = g.size / 2;
  const sample = (d, th) => headPixel(g, t, d, th, angle);
  for (let y = 0; y < g.size; y++)
    for (let x = 0; x < g.size; x++) {
      const d = Math.hypot(x + 0.5 - cx, y + 0.5 - cy);
      if (d >= g.lip[1]) continue;
      const th = angOf(x, y, cx, cy);
      if (!blur || d < g.ring3[0]) {
        put(img, x, y, c(sample(d, th)));
        continue;
      }
      // tangential motion blur: average 9 samples, quantised to 16 levels (keeps a pixel-art feel)
      const acc = [0, 0, 0];
      const n = 9;
      for (let i = 0; i < n; i++) {
        const v = c(sample(d, th + ((i / (n - 1)) * 2 - 1) * blur));
        for (let k = 0; k < 3; k++) acc[k] += v[k];
      }
      put(img, x, y, [...acc.map((v) => Math.round(v / n / 12) * 12), 255].map((v) => Math.min(255, v)));
    }
  if (g.digits && !blur) {
    for (let i = 0; i < 37; i++) {
      const n = WHEEL_ORDER[i];
      const a = (angle + i * PITCH) * DEG;
      const px = cx + g.digits * Math.sin(a);
      const py = cy - g.digits * Math.cos(a);
      textC(img, String(n), px - 0.5 + (textWidth(String(n), 'small') % 2 ? 0 : 0.5), py - 0.5, '#F4ECF8', { font: 'small', shadow: POCKET[pocketColour(n)][3] });
    }
  }
  return img;
}

function headPixel(g, t, d, th, angle) {
  const local = mod(th - angle, 360);
  const k = Math.floor(mod(local + PITCH / 2, 360) / PITCH) % 37;
  const inPocket = mod(local + PITCH / 2, PITCH);
  const edgePx = Math.min(inPocket, PITCH - inPocket) * DEG * d; // distance to the nearest fret line in px
  const pc = POCKET[pocketColour(WHEEL_ORDER[k])];
  const L = lightAt(th); // convex light
  if (within(d, g.lip)) return ramp(GOLD, d > g.lip[1] - 1 ? L * 0.8 - 0.2 : L * 0.9 + 0.1);
  if (within(d, g.num)) {
    if (edgePx < g.sep) return GOLD[3];
    if (d > g.num[1] - 1 && L > 0.35) return pc[0];
    return pc[1];
  }
  if (within(d, g.ring2)) return ramp(GOLD, -L * 0.6);
  if (within(d, g.pocket)) {
    // frets: brass walls between pockets, lit on the side that faces the light
    if (edgePx < g.fret) return ramp(GOLD, L * 0.7 + 0.2);
    const depth = (d - g.pocket[0]) / (g.pocket[1] - g.pocket[0]); // 0 = inner edge
    if (depth > 0.8) return pc[3]; // shadow under the number ring
    if (depth < 0.18) return L > 0 ? pc[1] : pc[2];
    return pc[2];
  }
  if (within(d, g.ring3)) return ramp(GOLD, L * 0.8);
  // cone: mahogany dome lit top-left, fine radial grain every 15°
  const localArm = mod(local - 45, 90);
  const armDist = Math.min(localArm, 90 - localArm) * DEG * d;
  if (d >= g.turret && d < g.arm && armDist < g.armW) return ramp(GOLD, L * 0.8 + (armDist < 0.6 ? 0.2 : -0.3));
  if (d >= g.turret && d < g.arm + g.knob + 1) {
    // knobs at the arm ends
    for (let q = 0; q < 4; q++) {
      const a = (angle + 45 + q * 90) * DEG;
      const kx = g.arm * Math.sin(a);
      const ky = -g.arm * Math.cos(a);
      const px = d * Math.sin(th * DEG);
      const py = -d * Math.cos(th * DEG);
      const kd = Math.hypot(px - kx, py - ky);
      if (kd < g.knob) return kd < g.knob * 0.45 && px - kx < 0 && py - ky < 0 ? GOLD[0] : ramp(GOLD, lightAt(angOf(px - kx, py - ky, 0, 0)) * 0.6);
    }
  }
  if (d < g.turret) {
    // brass dome with a fixed top-left specular spot
    const px = d * Math.sin(th * DEG);
    const py = -d * Math.cos(th * DEG);
    const spec = Math.hypot(px + g.turret * 0.38, py + g.turret * 0.38);
    if (spec < g.turret * 0.26) return '#FFFFFF';
    if (spec < g.turret * 0.5) return GOLD[0];
    return ramp(GOLD, L * (d / g.turret) * 0.9 + 0.1);
  }
  const grain = mod(local, 15) < 15 * (0.6 / Math.max(1, d)) * 3 ? -0.25 : 0;
  const rel = (d - g.turret) / (g.ring3[0] - g.turret);
  return ramp(t.cone, L * (0.25 + 0.75 * rel) + grain);
}

/** The static bowl (wood rim, gold inlay, ball track, 8 deflector diamonds, shadow gap), per theme. */
export function bowl(b, theme) {
  const t = THEME[theme];
  const img = image(b.size, b.size);
  const cx = b.size / 2;
  for (let y = 0; y < b.size; y++)
    for (let x = 0; x < b.size; x++) {
      const d = Math.hypot(x + 0.5 - cx, y + 0.5 - cx);
      if (d >= b.edge[1]) continue;
      const th = angOf(x, y, cx, cx);
      const L = lightAt(th);
      let hex;
      if (within(d, b.edge)) hex = INK;
      else if (within(d, b.rim)) hex = ramp(t.wood, L * (d > b.rim[1] - 2 ? 0.9 : 0.5) + (d < b.rim[0] + 1.2 ? -0.5 : 0));
      else if (within(d, b.inlay)) hex = ramp([t.trimLight, t.trim, t.trimDark], L);
      else if (within(d, b.track)) {
        const rel = (d - b.track[0]) / (b.track[1] - b.track[0]);
        hex = ramp(t.track, -L * 0.55 + (rel - 0.5) * 0.9); // concave: lit on the far side, brighter toward the wall
      } else if (within(d, b.gap)) hex = d > b.gap[1] - 1 ? '#0C0A10' : '#06040A';
      else hex = '#0C0A10';
      put(img, x, y, c(hex));
    }
  // deflectors: brass diamonds, alternately radial and tangential (8, at 22.5° + k·45°)
  for (let k = 0; k < 8; k++) {
    const a = (22.5 + k * 45) * Math.PI / 180;
    const px = cx + b.deflector * Math.sin(a);
    const py = cx - b.deflector * Math.cos(a);
    const r = b.deflectorR;
    const long = k % 2 === 0 ? 1.7 : 1;
    for (let y = Math.floor(py - r * 2); y <= py + r * 2; y++)
      for (let x = Math.floor(px - r * 2); x <= px + r * 2; x++) {
        const ux = x + 0.5 - px;
        const uy = y + 0.5 - py;
        // diamond oriented along the radius
        const rr = ux * Math.sin(a) - uy * Math.cos(a);
        const tt = ux * Math.cos(a) + uy * Math.sin(a);
        const m = Math.abs(rr) / (r * long) + Math.abs(tt) / r;
        if (m > 1) continue;
        put(img, x, y, c(m > 0.72 ? GOLD[3] : ux + uy < -0.5 ? GOLD[0] : GOLD[1]));
      }
  }
  return img;
}

/** A full wheel image: bowl + head at `angle` (+ optional ball resting in the top pocket). */
export function composed(b, theme, angle, { ballTop = false } = {}) {
  const img = bowl(b, theme);
  if (b.head) {
    const h = head(b.head, angle, { theme });
    const o = (b.size - h.w) >> 1;
    for (let y = 0; y < h.h; y++)
      for (let x = 0; x < h.w; x++) {
        const i = (y * h.w + x) * 4;
        if (h.data[i + 3]) put(img, x + o, y + o, [h.data[i], h.data[i + 1], h.data[i + 2], 255]);
      }
    if (ballTop) {
      const r = (b.head.pocket[0] + b.head.pocket[1]) / 2;
      disc(img, b.size / 2, b.size / 2 - r, 1.8, c('#F4ECE0'));
      put(img, Math.floor(b.size / 2) - 1, Math.floor(b.size / 2 - r) - 1, c('#FFFFFF'));
    }
  }
  return img;
}

/** The ball: 7 × 7 inked ivory; frame 0 rest, 1 flash (hop landing), 2 in-pocket (shaded). */
export function ball(frame) {
  const b = image(5, 5);
  const body = frame === 2 ? ['#D8D0C4', '#A89C90'] : frame === 1 ? ['#FFFFFF', '#E8E0D8'] : ['#F4ECE0', '#C0B0A0'];
  disc(b, 2.5, 2.5, 2.6, c(body[0]));
  for (const [x, y] of [[3, 4], [4, 3], [2, 4], [4, 2]]) put(b, x, y, c(body[1]));
  put(b, 1, 1, c('#FFFFFF'));
  if (frame === 1) put(b, 2, 1, c('#FFFFFF'));
  return inked(b, INK, 0.85);
}

/** Pocket glow: a gold halo ring (20 × 20), 4 pulse frames. */
export function pocketGlow(f) {
  const img = image(20, 20);
  const k = [0.55, 0.8, 1, 0.8][f];
  ring(img, 10, 10, 9.5, 2, c('#FFD640'), 0.5 * k);
  ring(img, 10, 10, 7.5, 2, c('#FFF1A0'), 0.85 * k);
  ring(img, 10, 10, 5.5, 1, c('#FFFFFF'), 0.5 * k);
  return img;
}

/** Wheel world texture (BER): 128 × 80 atlas — head (0,0,64,64), bowl (64,0,64,64), ball 4², dolly 8 × 12. */
export function worldTexture(theme) {
  const img = image(128, 80);
  const h = head(HEAD_WORLD, 0, { theme });
  const bw = bowl(BOWL_WORLD, theme);
  const blitOpaque = (src, ox, oy) => {
    for (let y = 0; y < src.h; y++)
      for (let x = 0; x < src.w; x++) {
        const i = (y * src.w + x) * 4;
        if (src.data[i + 3]) put(img, ox + x, oy + y, [src.data[i], src.data[i + 1], src.data[i + 2], src.data[i + 3]]);
      }
  };
  blitOpaque(h, 0, 0);
  blitOpaque(bw, 64, 0);
  // ball (4 × 4 at 0,64): ivory cube faces
  R(img, 0, 64, 4, 4, '#F4ECE0');
  put(img, 0, 64, c('#FFFFFF'));
  R(img, 3, 65, 1, 3, '#C0B0A0');
  R(img, 1, 67, 3, 1, '#C0B0A0');
  // dolly billboard (8 × 12 at 8,64): gold pawn
  const rows = ['...YY...', '..YWYY..', '..YYYO..', '...YO...', '...YO...', '..YYYO..', '.YYYYYO.', 'PPPPPPPP', 'pppppppp', '........', '........', '........'];
  rows.forEach((row, j) => [...row].forEach((ch, i) => ch !== '.' && put(img, 8 + i, 64 + j, c({ Y: GOLD[1], W: '#FFFFFF', O: GOLD[3], P: '#783CBE', p: '#3A1A5C' }[ch]))));
  // turret cap (8 × 8 at 16,64) for the BER's raised turret cube
  disc(img, 20, 68, 3.6, c(GOLD[2]));
  disc(img, 19.5, 67.5, 2.2, c(GOLD[1]));
  put(img, 18, 66, c('#FFFFFF'));
  return img;
}
