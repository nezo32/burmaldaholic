// Core GUI art (global.md §2.2, §4.1, §4.10, §5.1–5.2): nine-slice panels, button families, HUD sprites,
// toast, contract stamp. Pure drawing: returns images, core.mjs names the files.
import { alpha, mix, shade } from '../../lib/palette.mjs';
import { drawGrid, ellipse, frame, image, line, paint, rect, setPx } from '../../lib/grid.mjs';

/** Casino panel 32² (border 6): bg.darkest outer, frame, glint top-left bevel, bg.deep 90 %, gold rivets. */
export function panelCasino() {
  const img = image(32, 32);
  rect(img, 0, 0, 31, 31, alpha('bg.deep', 0.9));
  frame(img, 0, 0, 31, 31, 'bg.darkest');
  frame(img, 1, 1, 30, 30, 'frame');
  line(img, 2, 2, 29, 2, 'glint');
  line(img, 2, 2, 2, 29, 'glint');
  for (const [x, y] of [[3, 3], [27, 3], [3, 27], [27, 27]]) {
    rect(img, x, y, x + 1, y + 1, 'gold');
    setPx(img, x + 1, y + 1, 'gold.shade');
  }
  return img;
}

/** Felt panel 32² (border 6): wood rim (2 tones), gold inner line, felt with a 2-tone dither. */
export function panelFelt() {
  const img = image(32, 32);
  paint(img, (x, y) => ((x + y) % 2 === 0 && (x * 7 + y * 3) % 5 === 0 ? shade('felt', 1.12) : 'felt'));
  for (let i = 0; i < 5; i++) frame(img, i, i, 31 - i, 31 - i, i === 0 || i === 4 ? 'wood.dark' : 'wood.light');
  // wood grain
  for (let x = 2; x < 30; x += 5) setPx(img, x, 2, 'wood.dark');
  for (let y = 3; y < 30; y += 6) setPx(img, 2, y, 'wood.dark');
  frame(img, 5, 5, 26, 26, 'gold');
  return img;
}

/** Inset well 16² (border 3): bg.darkest 80 %, frame highlight bottom-right. */
export function panelInset() {
  const img = image(16, 16);
  rect(img, 0, 0, 15, 15, alpha('bg.darkest', 0.8));
  line(img, 0, 0, 15, 0, alpha('ink', 0.9));
  line(img, 0, 0, 0, 15, alpha('ink', 0.9));
  line(img, 1, 15, 15, 15, 'frame');
  line(img, 15, 1, 15, 15, 'frame');
  return img;
}

/** HUD panel 16² (border 3): bg.darkest 70 %, 1 px border (frame 60 % or gold for Golden Hour). */
export function panelHud(golden = false) {
  const img = image(16, 16);
  rect(img, 1, 1, 14, 14, alpha('bg.darkest', 0.7));
  frame(img, 0, 0, 15, 15, golden ? 'gold' : alpha('frame', 0.6));
  if (golden) for (const [x, y] of [[0, 0], [15, 0], [0, 15], [15, 15]]) setPx(img, x, y, 'gold.shade');
  return img;
}

/** Casino Menu tab 32 × 16 (border 4). Selected: bg.deep, glint top, gold underline. */
export function panelTab(selected) {
  const img = image(32, 16);
  rect(img, 0, 0, 31, 15, selected ? alpha('bg.deep', 0.95) : 'btn.fill');
  line(img, 1, 0, 30, 0, selected ? 'glint' : 'frame');
  line(img, 0, 1, 0, 15, 'frame');
  line(img, 31, 1, 31, 15, 'frame');
  if (selected) rect(img, 1, 14, 30, 15, 'gold');
  else line(img, 0, 15, 31, 15, 'frame');
  return img;
}

/**
 * Button 200 × 20 (nine-slice border 3). family: secondary | primary | danger; state: normal | highlighted | disabled.
 */
export function button(family, state) {
  const img = image(200, 20);
  let fill;
  let border;
  let bottom;
  let top;
  if (state === 'disabled') {
    fill = 'btn.disabled';
    border = 'btn.disabled.border';
  } else if (family === 'primary') {
    fill = state === 'highlighted' ? 'gold' : 'btn.primary';
    border = 'gold.shade';
    bottom = 'gold.shade';
    top = state === 'highlighted' ? 'bone' : shade('btn.primary', 1.15);
  } else if (family === 'danger') {
    fill = state === 'highlighted' ? 'chip.light' : 'chip.red';
    border = 'chip.dark';
    bottom = 'chip.dark';
    top = state === 'highlighted' ? mix('chip.light', 'bone', 0.4) : 'chip.light';
  } else {
    fill = state === 'highlighted' ? 'btn.hover' : 'btn.fill';
    border = state === 'highlighted' ? 'glint' : 'frame';
    top = state === 'highlighted' ? mix('btn.hover', 'glint', 0.35) : mix('btn.fill', 'frame', 0.35);
  }
  rect(img, 1, 1, 198, 18, fill);
  if (bottom) rect(img, 1, 17, 198, 18, bottom);
  if (top) line(img, 1, 1, 198, 1, top);
  frame(img, 0, 0, 199, 19, border);
  for (const [x, y] of [[0, 0], [199, 0], [0, 19], [199, 19]]) img.data.fill(0, (y * 200 + x) * 4, (y * 200 + x) * 4 + 4);
  return img;
}

// ---- HUD sprites (8 × 8) ---------------------------------------------------------------------------------

const FLAME = [
  ['...o....', '..oo..o.', '..ooo.o.', '.oooooo.', '.ooyyoo.', 'ooyyyyoo', 'oyyyyyyo', '.oyyyyo.'],
  ['....o...', '.o.oo...', '.o.ooo..', '.oooooo.', '.ooyyoo.', 'ooyyyyoo', 'oyyyyyyo', '.oyyyyo.'],
  ['..o..o..', '..oo.o..', '.ooooo..', '.oooooo.', '.oyyyoo.', 'ooyyyyoo', 'oyyyyyyo', '.oyyyyo.'],
];
const CLOUD = [
  ['..gggg..', '.gggggg.', 'gggggggg', 'gggggggg', '.gggggg.', '..b.....', '........', '........'],
  ['..gggg..', '.gggggg.', 'gggggggg', 'gggggggg', '.gggggg.', '.....b..', '..b.....', '........'],
  ['..gggg..', '.gggggg.', 'gggggggg', 'gggggggg', '.gggggg.', '..b.....', '.....b..', '..b.....'],
];
const HUD_LEGEND = { o: '#FF7A1A', y: '#FFD54F', g: '#B0BEC5', b: '#4FC3F7' };

export const hudFlame = (f) => drawGrid(image(8, 8), FLAME[f], HUD_LEGEND);
export const hudCloud = (f) => drawGrid(image(8, 8), CLOUD[f], HUD_LEGEND);

/** VIP badge shine overlay frame f of 4: a white diagonal band sweeping over the 8 × 8 badge area. */
export function hudBadgeShine(f) {
  const band = image(8, 8);
  for (let y = 0; y < 8; y++)
    for (let x = 0; x < 8; x++) {
      const d = Math.abs(x + y - (f * 5 - 1));
      // only inside the badge diamond (same silhouette as the E180 glyph at half size)
      if (d <= 1 && Math.abs(x - 3.5) + Math.abs(y - 3) <= 4) setPx(band, x, y, alpha('#FFFFFF', d === 0 ? 0.8 : 0.4));
    }
  return band;
}

/** Toast background 160 × 32: bg.deep, gold 1 px frame, 24 × 24 icon well on the left. */
export function toast() {
  const img = image(160, 32);
  rect(img, 1, 1, 158, 30, alpha('bg.deep', 0.95));
  frame(img, 0, 0, 159, 31, 'gold');
  rect(img, 4, 4, 27, 27, alpha('bg.darkest', 0.85));
  frame(img, 4, 4, 27, 27, 'frame');
  line(img, 1, 1, 158, 1, 'gold.shade');
  return img;
}

/** Contract stamp 16²: red ring with a bonus-green check (menu/stamp). */
export function stamp() {
  const img = image(16, 16);
  ellipse(img, 8, 8, 7.5, 7.5, (x, y, d) => (d > 0.8 ? 'chip.red' : d > 0.65 ? null : alpha('chip.red', 0.25)));
  drawGrid(img, ['......vv', '.....vv.', 'v...vv..', 'vv.vv...', '.vvv....', '..v.....'], { v: 'bonus' }, 4, 5);
  return img;
}
