// Core particle sprite sets (global.md §5.1 #37, docs/architecture/animation.md §2.3): 8 × 8 frames.
// Java: one PNG per frame `textures/particle/core/<id>_<n>.png` (particle JSON + providers: lane J-L1).
// Bedrock: one flipbook row per set in `textures/particle/burmaldaholic_fx.png` (particle JSON: lane B-L1),
// plus the extras-owned `foil_flake` row (extras-pvp.md §10.4 asks for a row in this atlas).
// Tintable sets (sparkle, confetti) are drawn in white/grey.
import { CHIPS, alpha } from '../../lib/palette.mjs';
import { ellipse, frame, image, line, rect, setPx } from '../../lib/grid.mjs';
import { rotate } from '../../lib/transforms.mjs';

const px = (img, pts, c) => pts.forEach(([x, y]) => setPx(img, x, y, c));
const n8 = () => image(8, 8);

function chipPop(f) {
  const img = n8();
  const { base, stripe } = CHIPS[5];
  const rx = [3.5, 2.5, 1.2, 3][f];
  const ry = [3.5, 3.5, 3.5, 1.5][f];
  ellipse(img, 4, 4, rx, ry, (x, y, d) => (d > 0.7 ? stripe : base));
  return img;
}

function star(f, core, mid, max = 3) {
  const img = n8();
  const arm = [1, 2, max, 2][f];
  for (let i = 1; i <= arm; i++) px(img, [[4 + i, 4], [4 - i, 4], [4, 4 + i], [4, 4 - i]], i === arm ? alpha(mid, 0.6) : mid);
  setPx(img, 4, 4, core);
  if (f === 2) px(img, [[3, 3], [5, 5], [3, 5], [5, 3]], alpha(mid, 0.5));
  return img;
}

function ring(f, c, frames = 4) {
  const img = n8();
  const r = 1.8 + (f / (frames - 1)) * 2.1;
  ellipse(img, 4, 4, r, r, (x, y, d) => (d > 0.62 ? alpha(c, 1 - f / (frames + 1)) : null));
  return img;
}

function goldBurst(f) {
  const img = ring(f, '#FFD640');
  if (f < 2) setPx(img, 4, 4, '#FFF3A0');
  return img;
}

function goldenMote(f) {
  const img = n8();
  ellipse(img, 4, 4, f ? 1.5 : 2, f ? 1.5 : 2, (x, y, d) => (d < 0.5 ? '#FFF3A0' : alpha('#FFD640', 0.7)));
  return img;
}

function curseWisp(f) {
  const img = n8();
  const rows = [
    ['...c....', '..cC....', '..CC.c..', '.cCCcC..', '.CCCCC..', '..CCC...', '...C....', '........'],
    ['....c...', '...Cc...', '.c.CC...', '.CcCCc..', '..CCCC..', '..CCC...', '...C....', '........'],
    ['........', '....c...', '...c....', '..cC.c..', '..CCc...', '..cC....', '........', '........'],
  ][f];
  rows.forEach((r, y) => [...r].forEach((ch, x) => ch !== '.' && setPx(img, x, y, ch === 'C' ? '#6FA86A' : alpha('#3A1450', 0.85))));
  return img;
}

function summonRune(f) {
  const img = n8();
  frame(img, 1, 1, 6, 6, alpha('#BE5AFF', 0.5 + f * 0.15));
  line(img, 1, 1, 6, 6, '#D696FF');
  line(img, 6, 1, 1, 6, '#D696FF');
  return rotate(img, f * 22.5);
}

function smoke(f) {
  const img = n8();
  const r = 1.5 + f * 0.8;
  ellipse(img, 4, 5 - f * 0.5, r, r * 0.85, (x, y, d) => alpha(d < 0.5 ? '#6A6070' : '#3A3440', 0.9 - f * 0.18));
  return img;
}

function coinBurst(f) {
  const img = n8();
  const rx = [3.5, 2, 0.8, 2][f];
  ellipse(img, 4, 4, rx, 3.5, (x, y, d) => (d > 0.75 ? '#B07010' : f === 3 ? '#D8A020' : '#FFD640'));
  return img;
}

function confetti(f) {
  const img = n8();
  if (f === 0) rect(img, 3, 3, 5, 5, '#FFFFFF');
  if (f === 1) px(img, [[2, 5], [3, 4], [4, 3], [5, 2], [3, 5], [4, 4], [5, 3]], '#FFFFFF');
  if (f === 2) rect(img, 2, 4, 6, 4, '#DDDDDD');
  if (f === 3) px(img, [[2, 2], [3, 3], [4, 4], [5, 5], [3, 2], [4, 3], [5, 4]], '#EEEEEE');
  return img;
}

function jackpotBurst(f) {
  const img = star(f, '#FFFFFF', '#FFD640');
  if (f >= 2) px(img, [[1, 1], [7, 1], [1, 7], [7, 7]].slice(0, f === 2 ? 4 : 2), '#FFF3A0');
  return img;
}

function foilFlake(f) {
  const img = n8();
  const shapes = [
    [[3, 3], [4, 3], [3, 4], [4, 4]],
    [[3, 3], [4, 4]],
    [[2, 4], [3, 4], [4, 4], [5, 4]],
    [[4, 3], [3, 4]],
  ][f];
  px(img, shapes, f % 2 ? '#D8D8D8' : '#B4B4B4');
  return img;
}

const seq = (n, fn) => Array.from({ length: n }, (_, f) => fn(f));

/** Sets in atlas row order. `java: false` = no Java frame files (owned elsewhere). */
export const PARTICLE_SETS = [
  { name: 'chip_pop', frames: seq(4, chipPop) },
  { name: 'chip_glint', frames: seq(4, (f) => star(f, '#FFFFFF', '#F4ECF8', 2)) },
  { name: 'sparkle', frames: seq(4, (f) => star(f, '#FFFFFF', '#E8E8E8')) },
  { name: 'gold_burst', frames: seq(4, goldBurst) },
  { name: 'golden_mote', frames: seq(2, goldenMote) },
  { name: 'diamond_glint', frames: seq(4, (f) => star(f, '#D1FFF8', '#4AEDD9')) },
  { name: 'curse_wisp', frames: seq(3, curseWisp) },
  { name: 'summon_rune', frames: seq(4, summonRune) },
  { name: 'teleport_ring', frames: seq(4, (f) => ring(f, '#D696FF')) },
  { name: 'collector_smoke', frames: seq(4, smoke) },
  { name: 'coin_burst', frames: seq(4, coinBurst) },
  { name: 'confetti', frames: seq(4, confetti) },
  { name: 'jackpot_burst', frames: seq(4, jackpotBurst) },
  { name: 'foil_flake', frames: seq(4, foilFlake), java: false },
];

/** Neutral (white/grey) chip for `variable.tint` denominations: face, ¾, edge, ¾ (a tumbling chip). */
function chipNeutral(f) {
  const img = n8();
  const rx = [3.5, 2.4, 1, 2.4][f];
  ellipse(img, 4, 4, rx, 3.5, (x, y, d) => (d > 0.72 ? (f === 2 ? '#B8B8B8' : '#FFFFFF') : d > 0.4 ? '#D8D8D8' : '#FFFFFF'));
  return img;
}

/** 8-frame coin spin at 8 × 8 (width follows |cos|, back half darker) — `coin_burst`, `jackpot_burst`. */
function coinSpin8(f) {
  const img = n8();
  const c = Math.cos((f / 8) * 2 * Math.PI);
  const back = c < 0;
  ellipse(img, 4, 4, Math.max(0.8, Math.abs(c) * 3.5), 3.5, (x, y, d) => (d > 0.75 ? (back ? '#8A5A0C' : '#B07010') : back ? '#D8A020' : '#FFD640'));
  return img;
}

/** 8-frame white confetti piece tumbling (tinted per particle). */
function confetti8(f) {
  const img = n8();
  const w = [3, 2, 1, 2, 3, 2, 1, 2][f];
  const piece = image(8, 8);
  rect(piece, 4 - Math.ceil(w / 2), 3, 3 + Math.floor(w / 2) + (w === 1 ? 1 : 0), 4, f % 4 === 2 ? '#D0D0D0' : '#FFFFFF');
  const r = rotate(piece, f * 45);
  for (let i = 0; i < r.data.length; i++) img.data[i] = r.data[i];
  return img;
}

/**
 * Bedrock atlas rows = `ATLAS_ROWS` of `src/core/logic/anim/particle-atlas.ts` (lane B-L1; row order matters, a
 * test in tools/assets/core.test.mjs compares them). Rows 13–15 are reserve.
 */
export const BEDROCK_ATLAS_ROWS = [
  { name: 'chip', frames: seq(4, chipNeutral) },
  ...PARTICLE_SETS.filter((s) => ['chip_glint', 'sparkle', 'gold_burst', 'golden_mote', 'diamond_glint', 'curse_wisp', 'summon_rune', 'teleport_ring', 'collector_smoke'].includes(s.name)),
  { name: 'coin', frames: seq(8, coinSpin8) },
  { name: 'confetti', frames: seq(8, confetti8) },
  PARTICLE_SETS.find((s) => s.name === 'foil_flake'),
];
