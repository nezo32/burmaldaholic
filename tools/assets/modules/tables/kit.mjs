// Shared table kit (docs/design/visual/tables.md §2): themed felt, rail frame, room backdrops, plates, seat and bot
// plates, text buttons, big round action buttons, icon buttons, chips (tray / layout / grey for seat tint),
// banners and the bet timer. No words: every label is drawn by the screen with the font.
import { BONE, GOLD, INK, THEME } from './theme.mjs';
import { CHIPS } from '../../lib/palette.mjs';
import {
  P, R, blend, c, desaturate, disc, ellipseFill, grid, image, inked, lightAt, line, over, put, ramp, ring, rng, rrect, textC, vgrad,
} from './draw.mjs';

// ---- felt -----------------------------------------------------------------------------------------------------------
/** Tileable felt 64 × 64: base + sparse seeded fibres (lighter and darker), no visible grid. */
export function felt(theme) {
  const t = THEME[theme];
  const img = image(64, 64);
  R(img, 0, 0, 64, 64, t.felt);
  const r = rng(theme.length * 131 + 7);
  for (let i = 0; i < 300; i++) {
    const x = (r() * 64) | 0;
    const y = (r() * 64) | 0;
    const v = r();
    P(img, x, y, v < 0.55 ? t.feltLight : t.feltDark);
    if (v > 0.9) P(img, (x + 1) & 63, y, t.feltDark); // short fibres
  }
  return img;
}

// ---- rail frame (nine-slice 48 × 48, border 14) ------------------------------------------------------------------------
/** Padded rail: ink edge, cushion lit from the top-left, gold (theme trim) inlay, brass studs, inner felt shadow. Centre transparent. */
export function rail(theme) {
  const t = THEME[theme];
  const S = 48;
  const img = image(S, S);
  for (let y = 0; y < S; y++)
    for (let x = 0; x < S; x++) {
      const dl = x;
      const dt = y;
      const dr = S - 1 - x;
      const db = S - 1 - y;
      const d = Math.min(dl, dt, dr, db);
      // rounded outer corners (radius 3)
      const cx = Math.min(dl, dr);
      const cy = Math.min(dt, db);
      if (cx < 3 && cy < 3 && Math.hypot(3 - cx - 0.5, 3 - cy - 0.5) > 3.2) continue;
      const side = d === dt ? 1 : d === dl ? 0.45 : d === dr ? -0.45 : -1; // light by facing side
      let hex;
      if (d === 0 || (cx < 3 && cy < 3 && Math.hypot(3 - cx - 0.5, 3 - cy - 0.5) > 2.2)) hex = INK;
      else if (d <= 9) {
        const p = (d - 1) / 8; // across the cushion, 0 = outside
        const v = Math.cos((p - 0.32) * Math.PI) * 0.75 + side * 0.35 - 0.1;
        hex = ramp(t.rail, v);
      } else if (d === 10) hex = side > 0 ? t.trimLight : t.trim;
      else if (d === 11) hex = side > 0 ? t.trim : t.trimDark;
      else if (d === 12) hex = INK;
      else if (d === 13) {
        blend(img, x, y, c('#000000'), 0.45);
        continue;
      } else continue;
      put(img, x, y, c(hex));
    }
  // studs at the cushion crest: corners + the middle of each edge (edges tile by 20 px)
  for (const [x, y] of [[5, 5], [42, 5], [5, 42], [42, 42], [24, 4], [24, 43], [4, 24], [43, 24]]) {
    P(img, x, y, t.railStud);
    P(img, x - 1, y, t.railStud);
    P(img, x, y - 1, t.railStud);
    P(img, x - 1, y - 1, '#FFFFFF', 0.8);
    P(img, x, y + 1, t.rail[3]);
    P(img, x + 1, y, t.rail[3]);
  }
  return img;
}

// ---- backdrops (640 × 360; the centre 427 × 240 is the GUI-scale-2 view) ----------------------------------------------
const BW = 640;
const BH = 360;

function lantern(img, x, y, glow = '#FFB040') {
  for (let r = 24; r > 0; r -= 4) disc(img, x + 2, y + 6, r, c(glow), 0.05);
  line(img, x + 2, y - 30, x + 2, y - 2, c('#3A3A44'));
  R(img, x, y - 2, 5, 2, '#3A3A44');
  R(img, x - 1, y, 7, 9, '#2A2A30');
  R(img, x, y + 1, 5, 7, '#FFD080');
  R(img, x + 1, y + 2, 3, 5, '#FFF4C0');
  R(img, x - 1, y + 9, 7, 1, '#2A2A30');
}

function villageBackdrop() {
  const t = THEME.village;
  const img = image(BW, BH);
  // oak plank wall
  const planks = ['#6E4422', '#643E20', '#76482A', '#5E3A1E'];
  for (let y = 0; y < BH; y++) {
    const row = Math.floor(y / 8);
    const off = (row * 37) % 48;
    for (let x = 0; x < BW; x++) {
      let hex = planks[(row + Math.floor((x + off) / 48)) % 4];
      if (y % 8 === 7) hex = '#3A2410';
      else if ((x + off) % 48 === 0) hex = '#4A2E16';
      else if ((x * 7 + y * 13 + row) % 23 === 0) hex = '#5A361A';
      put(img, x, y, c(hex));
    }
  }
  // dark-oak beams and wainscot
  for (const x of [96, 544]) {
    R(img, x - 6, 0, 12, BH, '#4A2E16');
    R(img, x - 6, 0, 2, BH, '#5E3A1C');
    R(img, x + 5, 0, 1, BH, '#2E1C0C');
  }
  R(img, 0, 0, BW, 16, '#3E2610');
  R(img, 0, 16, BW, 2, '#2A180A');
  R(img, 0, 290, BW, 70, '#4A2E16');
  for (let x = 0; x < BW; x += 32) R(img, x, 290, 1, 70, '#2E1C0C');
  R(img, 0, 290, BW, 2, '#6E4422');
  // windows (daylight, hills) left and right of the table
  for (const wx of [18, 562]) {
    R(img, wx - 3, 60, 66, 86, '#3E2610');
    vgrad(img, wx, 63, 60, 80, c('#7EC0EE'), c('#CFE8FF'), 8);
    for (let x = 0; x < 60; x++) {
      const h = Math.round(118 + 8 * Math.sin((x + wx) * 0.09) + 4 * Math.sin((x + wx) * 0.23));
      for (let y = h; y < 143; y++) put(img, wx + x, y, c(y < h + 2 ? '#7CC468' : '#4FA83A'));
    }
    disc(img, wx + 16, 80, 6, c('#FFFFFF'), 0.9);
    disc(img, wx + 24, 78, 7, c('#FFFFFF'), 0.9);
    R(img, wx + 29, 63, 2, 80, '#5E381C');
    R(img, wx, 101, 60, 2, '#5E381C');
    R(img, wx - 4, 146, 68, 4, '#6E4422');
  }
  // bunting: green and gold pennants along the top
  for (const by of [18, 58]) {
    for (let x = 0; x < BW; x += 16) {
      const col = (x / 16) % 2 ? t.accent : GOLD[1];
      for (let j = 0; j < 6; j++) R(img, x + 2 + j, by + 1 + j, Math.max(1, 12 - 2 * j), 1, col);
    }
    line(img, 0, by, BW, by, c('#E8DCC0'));
  }
  for (const x of [150, 250, 390, 490]) lantern(img, x, 34);
  // warm light pool + vignette
  for (let y = 0; y < BH; y++)
    for (let x = 0; x < BW; x++) {
      const v = Math.hypot((x - BW / 2) / (BW * 0.62), (y - BH * 0.45) / (BH * 0.7));
      if (v > 0.55) blend(img, x, y, c('#140822'), Math.min(0.55, (v - 0.55) * 1.1));
    }
  return img;
}

function bastionBackdrop() {
  const img = image(BW, BH);
  // polished blackstone bricks with gilded speckles
  const r = rng(99);
  for (let y = 0; y < BH; y++) {
    const row = Math.floor(y / 10);
    const off = row % 2 ? 12 : 0;
    for (let x = 0; x < BW; x++) {
      let hex = ((x + off) >> 4) % 3 === 0 ? '#2E2834' : ((x + off) >> 4) % 3 === 1 ? '#342E3A' : '#2A2430';
      if (y % 10 === 9 || (x + off) % 24 === 0) hex = '#18141C';
      else if (y % 10 === 0) hex = '#3C3440';
      put(img, x, y, c(hex));
    }
  }
  for (let i = 0; i < 260; i++) P(img, (r() * BW) | 0, (r() * BH) | 0, r() < 0.7 ? '#B07010' : '#FFD640');
  // lava falls at both sides
  for (const lx of [40, 580]) {
    for (let y = 0; y < BH; y++)
      for (let x = -14; x < 34; x++) {
        const w = 10 + Math.round(2 * Math.sin(y * 0.11 + lx));
        if (x >= 0 && x < w) {
          const s = (y * 3 + x * 7 + lx) % 17;
          put(img, lx + x, y, c(s < 3 ? '#FFE070' : s < 9 ? '#FF9A30' : '#FF7A1A'));
        } else if (x > -12 && x < w + 12) blend(img, lx + x, y, c('#FF5A10'), 0.18 * (1 - Math.abs(x < 0 ? x : x - w) / 12));
      }
  }
  // gold block pillars
  for (const px of [104, 520]) {
    R(img, px, 0, 16, BH, '#E8A820');
    for (let y = 0; y < BH; y += 16) {
      R(img, px, y, 16, 1, '#FFF1A0');
      R(img, px, y + 15, 16, 1, '#B07010');
      R(img, px + 2, y + 3, 3, 1, '#FFF1A0');
    }
    R(img, px, 0, 1, BH, '#FFF1A0');
    R(img, px + 15, 0, 1, BH, '#8A5410');
  }
  // chains and piglin banners (black with a gold snout motif)
  for (const bx of [128, 478]) {
    for (let y = 0; y < 50; y += 3) R(img, bx + 16, y, 2, 2, (y / 3) % 2 ? '#3A3A44' : '#5A5A66');
    R(img, bx, 20, 34, 70, '#1C1418');
    void 0;
    R(img, bx + 1, 21, 32, 68, '#5A1418');
    R(img, bx + 1, 21, 32, 3, '#FFD640');
    for (let k = 0; k < 4; k++) R(img, bx + 1 + k * 8, 86, 8 - (k % 2) * 2, 4, '#5A1418');
    over(img, grid(['..YYYYYY..', '.YppppppY.', 'YppkppkppY', 'YppppppppY', '.YYYYYYYY.'], { Y: '#FFD640', p: '#F9A8B8', k: '#5A1418' }), bx + 12, 44);
  }
  // heat glow from below + vignette
  for (let y = 0; y < BH; y++)
    for (let x = 0; x < BW; x++) {
      if (y > BH * 0.6) blend(img, x, y, c('#FF5A10'), ((y - BH * 0.6) / (BH * 0.4)) * 0.22);
      const v = Math.hypot((x - BW / 2) / (BW * 0.62), (y - BH * 0.45) / (BH * 0.7));
      if (v > 0.55) blend(img, x, y, c('#140408'), Math.min(0.6, (v - 0.55) * 1.2));
    }
  return img;
}

function endBackdrop() {
  const img = image(BW, BH);
  // the void through a panoramic window (whole wall), stars, end islands, a far dragon silhouette
  vgrad(img, 0, 0, BW, BH, c('#05030A'), c('#1A0E2E'), 10);
  const r = rng(7);
  for (let i = 0; i < 420; i++) P(img, (r() * BW) | 0, (r() * BH) | 0, r() < 0.12 ? '#FFFFFF' : r() < 0.5 ? '#9A8AC8' : '#4A3A6A');
  for (let x = 0; x < BW; x++) {
    const top = 70 + Math.round(22 * Math.sin(x * 0.012) + 7 * Math.sin(x * 0.05));
    for (let d = 0; d < 40; d++) blend(img, x, top + d, c(d < 14 ? '#60F0B0' : '#B040FF'), 0.22 * (1 - d / 40));
  }
  const island = (cx, cy, w, depth) => {
    for (let x = cx - w; x <= cx + w; x++) {
      const u = (x - cx) / w;
      const top = cy - Math.round(5 * Math.cos(u * Math.PI * 0.5));
      const bottom = cy + Math.round(depth * (1 - u * u) * (0.75 + 0.25 * Math.sin(x * 1.3)));
      for (let y = top; y <= bottom; y++) put(img, x, y, c(y === top ? '#FAF6CE' : y < top + 3 ? '#E8E4A8' : y > cy + 3 ? '#8E8A5A' : '#C8C080'));
    }
  };
  island(180, 150, 40, 14);
  island(470, 128, 28, 10);
  // dragon silhouette (far)
  over(img, grid(['k.........k', 'kk.......kk', '.kkk...kkk.', '..kkkkkkk..', '....kkk....', '.....k.....'], { k: '#0A0612' }), 330, 60);
  // purpur pillars framing the window, end rods as lights
  for (const px of [0, 88, 536, 616]) {
    for (let y = 0; y < BH; y++)
      for (let x = 0; x < 24; x++) put(img, px + x, y, c(x % 8 === 0 || y % 8 === 0 ? '#C8A0C8' : x % 8 === 7 || y % 8 === 7 ? '#8E648E' : '#A77BA7'));
    R(img, px, 0, 1, BH, '#D8B8D8');
  }
  R(img, 0, 0, BW, 12, '#140C1C');
  R(img, 0, 300, BW, 60, '#140C1C');
  for (let x = 0; x < BW; x += 16) R(img, x, 300, 1, 60, '#2A2036');
  for (const ex of [140, 320, 500]) {
    for (let rr = 18; rr > 0; rr -= 3) disc(img, ex + 1, 26, rr, c('#F4ECF8'), 0.05);
    R(img, ex, 12, 2, 24, '#F4ECF8');
    R(img, ex - 1, 12, 4, 2, '#B8A8C8');
  }
  // chorus plants in the corners
  for (const [x0, dir] of [[40, 1], [598, -1]]) {
    const stalks = [[0, 0, 0, -40], [0, -20, 10 * dir, -20], [10 * dir, -20, 10 * dir, -34], [0, -30, -8 * dir, -30], [-8 * dir, -30, -8 * dir, -44]];
    for (const [ax, ay, bx, by] of stalks) {
      const x1 = x0 + Math.min(ax, bx);
      const y1 = 300 + Math.min(ay, by);
      R(img, x1, y1, Math.abs(bx - ax) + 4, Math.abs(by - ay) + 4, '#6E4A8A');
      R(img, x1 + 1, y1 + 1, Math.abs(bx - ax) + 2, Math.abs(by - ay) + 2, '#8E6AAA');
    }
    for (const [fx, fy] of [[0, -40], [10 * dir, -34], [-8 * dir, -44]]) {
      R(img, x0 + fx - 1, 300 + fy - 4, 6, 6, '#C8A0E8');
      R(img, x0 + fx, 300 + fy - 3, 2, 2, '#F4ECF8');
    }
  }
  for (let y = 0; y < BH; y++)
    for (let x = 0; x < BW; x++) {
      const v = Math.hypot((x - BW / 2) / (BW * 0.62), (y - BH * 0.45) / (BH * 0.7));
      if (v > 0.55) blend(img, x, y, c('#05030A'), Math.min(0.6, (v - 0.55) * 1.2));
    }
  return img;
}

export const backdrop = (theme) => ({ village: villageBackdrop, bastion: bastionBackdrop, end: endBackdrop })[theme]();

// ---- plates (nine-slice) ------------------------------------------------------------------------------------------
/** Dark plaque with a theme-trim frame (title, balance, info): 24 × 16, border 5. `gold` = balance/gold frame. */
export function plate(theme, { gold = false } = {}) {
  const t = THEME[theme];
  const img = image(24, 16);
  rrect(img, 0, 0, 24, 16, INK, 1);
  rrect(img, 1, 1, 22, 14, gold ? GOLD[1] : t.trimDark, 1);
  R(img, 2, 1, 20, 1, gold ? GOLD[0] : t.trim);
  R(img, 2, 2, 20, 12, gold ? '#2A1A08' : t.plate);
  R(img, 2, 2, 20, 1, gold ? '#4A3210' : t.plateLight);
  R(img, 2, 13, 20, 1, '#000000', 0.35);
  return img;
}

/** Seat plate 32 × 16 (border 5): a player at the table. Variants: seat | you | active (shooter/current) | bot. */
export function seatPlate(theme, kind) {
  const t = THEME[theme];
  const img = image(32, 16);
  const frameCol = kind === 'active' ? GOLD[1] : kind === 'you' ? '#BE5AFF' : kind === 'bot' ? '#8FA8C8' : t.trimDark;
  rrect(img, 0, 0, 32, 16, INK, 1);
  rrect(img, 1, 1, 30, 14, frameCol, 1);
  R(img, 2, 2, 28, 12, kind === 'active' ? '#3A2A08' : t.plate);
  R(img, 2, 2, 28, 1, kind === 'active' ? '#6A4A10' : t.plateLight);
  // avatar / seat-colour well on the left (the screen tints `seat_stripe` into it)
  R(img, 2, 2, 11, 12, '#000000', 0.35);
  R(img, 13, 2, 1, 12, frameCol);
  if (kind === 'active') for (const [x, y] of [[1, 1], [30, 1], [1, 14], [30, 14]]) P(img, x, y, '#FFFFFF');
  return img;
}

/** Seat stripe 9 × 10: light grey chip-in-well the screen multiplies by the seat colour (§0.4 tints). */
export function seatStripe() {
  const img = image(9, 10);
  disc(img, 4.5, 5, 4.4, c('#9A9A9A'));
  disc(img, 4.5, 5, 3.3, c('#E8E8E8'));
  ring(img, 4.5, 5, 2.4, 1, c('#BDBDBD'));
  P(img, 3, 3, '#FFFFFF');
  return img;
}

/** Bot badge 11 × 11: a little brass automaton head (BOTS.md: bots are marked, never hidden). */
export function botBadge() {
  return inked(grid([
    '....Y....',
    '....y....',
    '.GGGGGGG.',
    'GgggggggG',
    'GgCgggCgG',
    'GgggggggG',
    'Ggg###ggG',
    '.GGGGGGG.',
  ], { Y: '#FF5A4A', y: '#B07010', G: '#8FA8C8', g: '#C8C8D8', C: '#40E0FF', '#': '#5A5A6A' }), INK, 0.9);
}

// ---- text buttons (nine-slice 200 × 20, border 4) ------------------------------------------------------------------
export function button(theme, state) {
  const t = THEME[theme];
  const img = image(200, 20);
  const ramp3 = state === 'hover' ? t.btnHover : t.btn;
  const disabled = state === 'disabled';
  const pressed = state === 'pressed';
  rrect(img, 0, 0, 200, 20, INK, 1);
  const top = pressed ? 2 : 1;
  rrect(img, 1, top, 198, 18 - (pressed ? 1 : 0), disabled ? '#3A3040' : ramp3[1], 1);
  if (!pressed) R(img, 2, 16, 196, 2, disabled ? '#2A2030' : ramp3[2]); // lip
  R(img, 2, top, 196, 1, disabled ? '#4A4050' : state === 'hover' ? t.trimLight : t.trim); // trim line
  R(img, 2, top + 1, 196, 1, disabled ? '#3A3040' : ramp3[0]);
  if (state === 'hover') {
    R(img, 1, 1, 1, 18, t.trim);
    R(img, 198, 1, 1, 18, t.trim);
  }
  return img;
}

// ---- big round action buttons (48 × 48): spin (wheel icon) and roll (two dice) ------------------------------------------
export function actionButton(icon, state) {
  const img = image(48, 48);
  const dis = state === 'disabled';
  const dy = state === 'pressed' ? 1 : 0;
  const body = dis ? ['#8A8490', '#6A6470', '#4A4450'] : state === 'hover' ? ['#FF8A80', '#E84450', '#A8202E'] : ['#FF6E6A', '#D83440', '#8C1834'];
  // drop shadow + base
  disc(img, 24, 25.5, 22.5, c(INK), 0.55);
  disc(img, 24, 24 + dy, 22, c(INK));
  disc(img, 24, 24 + dy, 21, c(dis ? '#9A9AA0' : GOLD[3]));
  disc(img, 24, 23.5 + dy, 20.4, c(dis ? '#B8B8C0' : GOLD[1]));
  disc(img, 24, 24 + dy, 18.6, c(body[2]));
  disc(img, 24, 23.4 + dy, 18.2, c(body[1]));
  // chip edge spots (6 bone inserts)
  for (let k = 0; k < 6; k++) {
    const a = (k * 60 + 30) * Math.PI / 180;
    for (let s = -1; s <= 1; s++) {
      const aa = a + s * 0.09;
      for (let rr = 15.2; rr <= 18; rr += 0.5) P(img, Math.round(24 + rr * Math.sin(aa) - 0.5), Math.round(24 + dy - rr * Math.cos(aa) - 0.5), dis ? '#D8D8DC' : BONE);
    }
  }
  disc(img, 24, 24 + dy, 13.6, c(body[2]));
  disc(img, 24, 23.6 + dy, 13, c(body[0]));
  disc(img, 24, 24.2 + dy, 12.4, c(body[1]));
  // top-left gloss
  for (let y = 0; y < 48; y++)
    for (let x = 0; x < 48; x++) {
      const d = Math.hypot(x + 0.5 - 17, y + 0.5 - 13 - dy);
      if (d < 5 && Math.hypot(x + 0.5 - 24, y + 0.5 - 24 - dy) < 18) blend(img, x, y, c('#FFFFFF'), d < 2.5 ? 0.45 : 0.18);
    }
  const ic = icon === 'spin' ? spinIcon(dis) : rollIcon(dis);
  over(img, ic, 24 - (ic.w >> 1), 24 + dy - (ic.h >> 1));
  if (state === 'hover') ring(img, 24, 24, 23.5, 1, c('#FFF1A0'), 0.8);
  return dis ? desaturate(img, 0.5) : img;
}

function spinIcon(dis) {
  // a mini wheel: rim, 8 spokes alternately red/black, gold hub, with a curved arrow
  const img = image(19, 19);
  disc(img, 9.5, 9.5, 9.4, c(INK));
  disc(img, 9.5, 9.5, 8.4, c(dis ? '#DDDDDD' : BONE));
  for (let y = 0; y < 19; y++)
    for (let x = 0; x < 19; x++) {
      const d = Math.hypot(x + 0.5 - 9.5, y + 0.5 - 9.5);
      if (d > 7.2 || d < 3) continue;
      const a = (Math.atan2(x + 0.5 - 9.5, -(y + 0.5 - 9.5)) / Math.PI) * 180 + 360;
      put(img, x, y, c(Math.floor(a / 30) % 2 ? '#26202C' : '#D83440'));
    }
  disc(img, 9.5, 9.5, 3, c(GOLD[1]));
  P(img, 8, 8, '#FFFFFF');
  return img;
}

function rollIcon() {
  const die = (pips) => {
    const d = image(11, 11);
    rrect(d, 0, 0, 11, 11, INK, 1);
    rrect(d, 1, 1, 9, 9, BONE, 1);
    R(d, 2, 9, 7, 1, '#C0B0DC');
    R(d, 9, 2, 1, 7, '#C0B0DC');
    for (const [x, y] of pips) R(d, x, y, 2, 2, x === 4 && y === 4 && pips.length === 1 ? '#D83440' : INK);
    return d;
  };
  const img = image(22, 20);
  over(img, die([[2, 2], [6, 6], [4, 4]]), 0, 7);
  over(img, die([[2, 2], [6, 2], [2, 6], [6, 6]]), 10, 0);
  return img;
}

// ---- icon buttons (20 × 20) and icons (12 × 12) ----------------------------------------------------------------------
export function iconButton(theme, state) {
  const t = THEME[theme];
  const img = image(20, 20);
  const dis = state === 'disabled';
  const pr = state === 'pressed';
  const r3 = state === 'hover' ? t.btnHover : t.btn;
  rrect(img, 0, 0, 20, 20, INK, 2);
  rrect(img, 1, pr ? 2 : 1, 18, pr ? 17 : 18, dis ? '#3A3040' : r3[1], 2);
  if (!pr) R(img, 2, 16, 16, 2, dis ? '#2A2030' : r3[2]);
  R(img, 3, pr ? 2 : 1, 14, 1, dis ? '#4A4050' : state === 'hover' ? t.trimLight : t.trim);
  return img;
}

const ICON_ROWS = {
  clear: ['............', '.RR......RR.', '.RRR....RRR.', '..RRR..RRR..', '...RRRRRR...', '....RRRR....', '....RRRR....', '...RRRRRR...', '..RRR..RRR..', '.RRR....RRR.', '.RR......RR.', '............'],
  rebet: ['............', '....GGGG....', '..GG....GG..', '.G........G.', '.G.......GGG', 'G.........G.', 'G...........', 'G.........G.', '.G.......G..', '..GG...GG...', '....GGG.....', '............'],
  undo: ['............', '...W........', '..WW........', '.WWWWWWWW...', 'WWWWWWWWWW..', '.WWW.....WW.', '..W.......W.', '..........W.', '.........WW.', '.......WWW..', '............', '............'],
  double: ['............', '....CCCCC...', '...CcCcCcC..', '...CCCCCCC..', '..CCCCC.....', '.CcCcCcC....', '.CCCCCCC....', '.CCCCCC.....', 'CcCcCcC.....', 'CCCCCCC.....', '.cccccc.....', '............'],
  leave: ['............', '.DDDDD......', '.DdddD......', '.DdddD...W..', '.DdddD...WW.', '.DddYDWWWWWW', '.DdddDWWWWWW', '.DdddD...WW.', '.DdddD...W..', '.DdddD......', '.DDDDD......', '............'],
  rules: ['............', '.BBBBB.BBBB.', 'BwwwwwBwwwwB', 'BwkkwwBwkkwB', 'BwwwwwBwwwwB', 'BwkkkwBwkkwB', 'BwwwwwBwwwwB', 'BwkkwwBwkkwB', 'BwwwwwBwwwwB', 'BBBBBBBBBBBB', '.bbbbbbbbbb.', '............'],
  racetrack: ['............', '..TTTTTTTT..', '.TrkrkrkrkT.', 'Tk........rT', 'Tr........kT', 'Tk........rT', 'Tr........kT', 'Tk........rT', '.TkrkrkgkrT.', '..TTTTTTTT..', '............', '............'],
};
const ICON_PAL = {
  R: '#FF6E6A', G: '#80FF40', W: '#F4ECF8', C: '#D83440', c: '#8C1834', D: '#6E4422', d: '#2A180A', Y: '#FFD640',
  B: '#783CBE', b: '#3A1A5C', w: '#F4ECF8', k: '#8C7CA8', T: '#FFD640', r: '#D83440', g: '#2E9A48',
};
export const ICON_NAMES = Object.keys(ICON_ROWS);
export const icon = (name) => inked(grid(ICON_ROWS[name], ICON_PAL), INK, 0.8);

// ---- chips ------------------------------------------------------------------------------------------------------------
const DENOMS = [1, 5, 25, 100, 500];
export { DENOMS };
const chipCol = (d) => (d === 'grey' ? { base: '#E0E0E0', stripe: '#8A8A8A' } : CHIPS[d]);
const darker = (hex, k) => {
  const [r, g, b] = c(hex);
  return `#${[r, g, b].map((v) => Math.round(v * k).toString(16).padStart(2, '0')).join('')}`;
};

/** Tray chip 24 × 24 (top view): edge inserts, dashed inner ring, value digits in the centre (digits only). */
export function trayChip(d) {
  const { base, stripe } = chipCol(d);
  const img = image(24, 24);
  disc(img, 12, 12.6, 11.6, c(INK), 0.6);
  disc(img, 12, 12, 11.5, c(INK));
  disc(img, 12, 12, 10.5, c(base));
  for (let y = 0; y < 24; y++)
    for (let x = 0; x < 24; x++) {
      const dd = Math.hypot(x + 0.5 - 12, y + 0.5 - 12);
      if (dd > 10.5) continue;
      const a = ((Math.atan2(x + 0.5 - 12, -(y + 0.5 - 12)) / Math.PI) * 180 + 360) % 360;
      if (dd > 7.6 && Math.floor((a + 15) / 30) % 2 === 0) put(img, x, y, c(stripe));
      else if (dd > 6.6 && dd <= 7.6) put(img, x, y, c(Math.floor(a / 20) % 2 ? stripe : darker(base, 0.8)));
    }
  disc(img, 12, 12, 6.6, c(darker(base, 0.85)));
  disc(img, 12, 11.6, 6.1, c(base));
  // light: top-left rim highlight, bottom-right shade
  for (let y = 0; y < 24; y++)
    for (let x = 0; x < 24; x++) {
      const dd = Math.hypot(x + 0.5 - 12, y + 0.5 - 12);
      if (dd > 9.6 && dd <= 10.5) {
        const L = lightAt(((Math.atan2(x + 0.5 - 12, -(y + 0.5 - 12)) / Math.PI) * 180 + 360) % 360);
        if (L > 0.5) blend(img, x, y, c('#FFFFFF'), 0.45);
        else if (L < -0.5) blend(img, x, y, c('#000000'), 0.3);
      }
    }
  if (d !== 'grey') {
    const label = String(d);
    const ink = d === 100 || d === 500 ? '#F4ECF8' : d === 1 ? '#1A3A8A' : '#180A28';
    textC(img, label, 12, 12, ink, { font: 'small' });
  }
  return img;
}

/** Layout chip 12 × 11 (seen from above at the table angle: top ellipse + edge band); stacks offset 3 px up. */
export function layoutChip(d) {
  const { base, stripe } = chipCol(d);
  const img = image(12, 11);
  ellipseFill(img, 6, 6.5, 6, 4.3, INK);
  ellipseFill(img, 6, 6.2, 5.2, 3.6, darker(base, 0.7)); // edge band
  for (const x of [1, 4, 7, 10]) {
    P(img, x, 7, stripe);
    P(img, x, 8, stripe);
  }
  ellipseFill(img, 6, 4.6, 6, 4.2, INK);
  ellipseFill(img, 6, 4.5, 5.2, 3.5, base);
  ellipseFill(img, 6, 4.5, 3.2, 1.9, darker(base, 0.88));
  for (const [x, y] of [[1, 4], [10, 4], [5, 1], [6, 8], [3, 2], [8, 7]]) P(img, x, y, stripe);
  P(img, 3, 3, '#FFFFFF', 0.6);
  return img;
}

/** Invalid ghost: a red outline chip (the code shakes it). */
export function layoutChipInvalid() {
  const img = image(12, 11);
  ellipseFill(img, 6, 5, 6, 4.5, '#FF3040');
  ellipseFill(img, 6, 5, 4.8, 3.4, '#FF304040');
  line(img, 3, 3, 8, 7, c('#FF3040'));
  line(img, 8, 3, 3, 7, c('#FF3040'));
  return img;
}

/** Selection ring around the selected tray chip: 28 × 28, 4 glint frames. */
export function chipSelect(f) {
  const img = image(28, 28);
  ring(img, 14, 14, 13.5, 1.6, c(GOLD[1]));
  ring(img, 14, 14, 12, 0.8, c(GOLD[3]), 0.7);
  const a = (f * 90 + 315) * Math.PI / 180;
  disc(img, 14 + 13 * Math.sin(a), 14 - 13 * Math.cos(a), 1.6, c('#FFFFFF'));
  return img;
}

/** Tray well 26 × 26 under each tray chip. */
export function chipWell(theme) {
  const t = THEME[theme];
  const img = image(26, 26);
  disc(img, 13, 13, 12.9, c(INK));
  disc(img, 13, 13, 12, c(t.plate));
  disc(img, 13, 14, 11.2, c('#000000'), 0.35);
  return img;
}

// ---- banners (nine-slice 48 × 24, border 10): marquee plaque with corner bulbs -----------------------------------------
export function banner(theme, tier) {
  const t = THEME[theme];
  const img = image(48, 24);
  const frameCol = tier === 'win' ? [GOLD[0], GOLD[1], GOLD[3]] : tier === 'lose' ? ['#C0B0DC', '#8C7CA8', '#4A3A5A'] : [t.trimLight, t.trim, t.trimDark];
  rrect(img, 0, 0, 48, 24, INK, 2);
  rrect(img, 1, 1, 46, 22, frameCol[2], 2);
  rrect(img, 2, 2, 44, 20, frameCol[1], 1);
  R(img, 3, 2, 42, 1, frameCol[0]);
  R(img, 4, 4, 40, 16, INK);
  R(img, 5, 5, 38, 14, tier === 'win' ? '#3A2A08' : tier === 'lose' ? '#1C1622' : t.plate);
  R(img, 5, 5, 38, 1, '#FFFFFF', 0.12);
  for (const [x, y] of [[3, 3], [44, 3], [3, 20], [44, 20]]) {
    P(img, x, y, '#FFFFFF');
    P(img, x + (x < 24 ? 1 : -1), y, '#FFF1A0', 0.7);
  }
  return img;
}

// ---- bet timer: 16 × 16 ring, 13 frames (12 → 0 segments left), code picks the frame -----------------------------------
export function timer(urgent) {
  const frames = [];
  for (let f = 0; f <= 12; f++) {
    const img = image(16, 16);
    disc(img, 8, 8, 7.9, c(INK));
    for (let y = 0; y < 16; y++)
      for (let x = 0; x < 16; x++) {
        const d = Math.hypot(x + 0.5 - 8, y + 0.5 - 8);
        if (d > 7 || d < 4.2) continue;
        const a = ((Math.atan2(x + 0.5 - 8, -(y + 0.5 - 8)) / Math.PI) * 180 + 360) % 360;
        const seg = Math.floor(a / 30);
        const edge = a % 30 < 30 * (0.9 / Math.max(1, d)) * 1.5;
        put(img, x, y, c(edge ? INK : seg >= f ? (urgent ? '#FF5A4A' : GOLD[1]) : '#3A2A4A'));
      }
    disc(img, 8, 8, 3.2, c(urgent ? '#8C1834' : '#3A1A5C'));
    P(img, 7, 7, '#FFFFFF', 0.6);
    frames.push(img);
  }
  return frames;
}
