// MT table geometry (visual pass 2, VQA round 1): smooth rounded rails / aprons from ROTATED segments (6 per quarter
// arc — 26.x model elements take any rotation angle, same `axis`/`angle` format on 26.2 and 26.3), a padded rail
// cross-section (brass lip, cushion with bevelled cap, outer drop), proud brass band, deep apron with a dark kick band,
// 4-segment turned legs or (poker) twin pedestals, a dealer tray with chip columns, raised roulette bowl rim.
//
// Local pixel frame as in furniture.mjs: X = players' right (main block X∈[0,16]), Y up, Z toward the players (main
// block Z∈[0,16]); a 3×2 table spans X∈[-16,32], Z∈[-16,16]. Output: axis-aligned boxes (split per cell later) and
// rotated segments (each owned by the cell holding its centre; it may poke a little into the neighbour cell's space,
// which block models allow). Pure + deterministic.

export const OUT = { cx: 8, hx: 24, hz: 16 };
/** Felt top / rail top (px): FeltFrame.FELT_Y = 14/16, FeltFrame.RAIL_TOP = 15.5/16. */
export const FELT_TOP = 14;
export const RAIL_TOP = 15.5;
const SEGS = 6; // segments per quarter arc

export function radii(shape) {
  if (shape === 'crescent') return { front: 16, back: 0 };
  if (shape === 'oval') return { front: 16, back: 16 };
  return { front: 0, back: 0 };
}

const q = (v) => Math.round(v * 4) / 4;

/** Z interval of a rounded rect inset by `inset` at x (px), or null (used for hidden fills only). */
export function span(x, shape, inset) {
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

/**
 * Ring of a rounded rectangle between insets `inO` (outer face, negative = past the footprint) and `inI` (inner face):
 * straight sides as axis-aligned boxes, rounded corners as rotated segments. `sides` drops a straight side (the
 * crescent's dealer edge); square corners belong to the left / right sides.
 */
export function ring(shape, { inO, inI, y0, y1, mat, emit, sides = { front: true, back: true, left: true, right: true } }) {
  const { front, back } = radii(shape);
  const boxes = [];
  const segs = [];
  const extra = emit ? { emit } : {};
  const X0 = OUT.cx - OUT.hx;
  const X1 = OUT.cx + OUT.hx;
  const Z0 = -OUT.hz;
  const Z1 = OUT.hz;
  const rcOf = (sz) => (sz > 0 ? front : back);
  // left / right sides: own the square corners
  for (const sx of [-1, 1]) {
    if (!(sx < 0 ? sides.left : sides.right)) continue;
    const xo = sx < 0 ? X0 + inO : X1 - inO;
    const xi = sx < 0 ? X0 + inI : X1 - inI;
    const zEnd = (sz) => {
      const rc = rcOf(sz);
      if (rc > 0) return sz * (OUT.hz - rc);
      return sz * (OUT.hz - inO);
    };
    const za = zEnd(-1);
    const zb = zEnd(1);
    if (zb > za) boxes.push({ mat, x0: Math.min(xo, xi), x1: Math.max(xo, xi), y0, y1, z0: za, z1: zb, ...extra });
  }
  // front / back sides
  for (const sz of [-1, 1]) {
    if (!(sz < 0 ? sides.back : sides.front)) continue;
    const rc = rcOf(sz);
    const zo = sz < 0 ? Z0 + inO : Z1 - inO;
    const zi = sz < 0 ? Z0 + inI : Z1 - inI;
    let xa;
    let xb;
    if (rc > 0) {
      xa = X0 + rc;
      xb = X1 - rc;
    } else {
      xa = sides.left ? X0 + inI : X0 + inO;
      xb = sides.right ? X1 - inI : X1 - inO;
    }
    if (xb > xa) boxes.push({ mat, x0: xa, x1: xb, y0, y1, z0: Math.min(zo, zi), z1: Math.max(zo, zi), ...extra });
  }
  // rounded corners
  for (const sz of [-1, 1]) {
    const rc = rcOf(sz);
    if (rc <= 0) continue;
    for (const sx of [-1, 1]) {
      const cx = OUT.cx + sx * (OUT.hx - rc);
      const cz = sz * (OUT.hz - rc);
      segs.push(...arc(cx, cz, rc - inO, rc - inI, sx, sz, y0, y1, mat, extra));
    }
  }
  return { boxes, segs };
}

/** Quarter arc (quadrant sx, sz) of a ring with outer radius ro, inner ri around (cx, cz), as rotated segments. */
export function arc(cx, cz, ro, ri, sx, sz, y0, y1, mat, extra = {}, n = SEGS) {
  const out = [];
  const step = 90 / n;
  const t = ro - ri;
  const len = 2 * ro * Math.tan(((step / 2) * Math.PI) / 180) * 1.04;
  for (let k = 0; k < n; k++) {
    const phi = ((k + 0.5) * step * Math.PI) / 180;
    const nx = sx * Math.cos(phi);
    const nz = sz * Math.sin(phi);
    const rm = ro - t / 2;
    out.push({ mat, cx: cx + nx * rm, cz: cz + nz * rm, y0, y1, len, thick: t, tx: -nz, tz: nx, ...extra });
  }
  return out;
}

/** Full circle ring (16-gon × 2 = 24 segments) around (cx, cz). */
export function circle(cx, cz, ro, ri, y0, y1, mat, extra = {}) {
  const out = [];
  for (const sx of [-1, 1]) for (const sz of [-1, 1]) out.push(...arc(cx, cz, ro, ri, sx, sz, y0, y1, mat, extra, 6));
  return out;
}

/** Hidden / flat fills from 1-px columns (felt, underside, decks): {mat, y0, y1, inset, minus?(x)→intervals}. */
function columns(shape, layers) {
  const boxes = [];
  for (let x = -16; x < 32; x++) {
    const xm = x + 0.5;
    for (const l of layers) {
      const s = span(xm, shape, l.inset);
      if (!s) continue;
      let zs = [[q(s[0]), q(s[1])]];
      if (l.clip) zs = l.clip(xm, zs);
      for (const [z0, z1] of zs) if (z1 - z0 > 1e-6) boxes.push({ mat: l.mat, x0: x, x1: x + 1, y0: l.y0, y1: l.y1, z0, z1, ...(l.extra || {}) });
    }
  }
  return boxes;
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
  return out;
}

/** Roulette: bowl (FeltFrame wheel centre (-0.5, 0) blocks, Ø1.0) and the betting layout rectangle. */
export const BOWL = { cx: -8, cz: 0, r: 8 };
const LAYOUT = { x0: 0, x1: 30, z0: -14, z1: 14 };
const circleSpan = (x, r) => {
  const d = x - BOWL.cx;
  if (Math.abs(d) >= r) return [];
  const h = Math.sqrt(r * r - d * d);
  return [[q(BOWL.cz - h), q(BOWL.cz + h)]];
};

/**
 * Roulette bowl rim (VQA r2 item 2), radii in px from the bowl centre: outer face stepped in three rings (8 → 7.5 →
 * 7.25), lit brass top band, dark inner wall, cream ball-track lip on the inner top edge. Top RIM_TOP = 17.5 (2 px
 * above the rail). RO contract (RouletteWorld): nothing inside r 6.5 anywhere, nothing inside r 6.75 below y 17 (the
 * rotor's track wall stands at r 6.4 with its lip to r 6.66 up to y ≈ 16.9, tucked under the cream lip).
 */
export const RIM = { top: 17.5, open: 6.5, wallR: 6.75, lipY: 17 };
export function bowlRim() {
  const { cx, cz } = BOWL;
  return [
    ...circle(cx, cz, 8, 7, 13, 15.5, 'wood'),
    ...circle(cx, cz, 7.5, 7, 15.5, 16.5, 'wood'),
    ...circle(cx, cz, 7.25, RIM.wallR, 16.5, RIM.top, 'trim', { emit: 10 }),
    ...circle(cx, cz, 7, RIM.wallR, 13, 16.5, 'rail'),
    ...circle(cx, cz, RIM.wallR, RIM.open, RIM.lipY, RIM.top, 'track'),
  ];
}

/** Turned leg at (x, z): collar, taper, bulb, brass ferrule, dark foot. */
function leg(x, z) {
  const b = (h, mat, y0, y1) => ({ mat, x0: x - h, x1: x + h, z0: z - h, z1: z + h, y0, y1 });
  return [b(1.5, 'wood', 5, 6.75), b(1, 'wood', 3, 5), b(1.5, 'wood', 1.5, 3), b(1.375, 'trim', 1, 1.5), b(1.25, 'rail', 0, 1)];
}

function legs(shape) {
  const { front, back } = radii(shape);
  const list = [];
  const corner = (sx, sz, rc) => {
    if (rc > 0) {
      const cx = OUT.cx + sx * (OUT.hx - rc);
      const cz = sz * (OUT.hz - rc);
      const d = (rc - 5) / Math.SQRT2;
      list.push([cx + sx * d, cz + sz * d]);
    } else list.push([OUT.cx + sx * (OUT.hx - 4), sz * (OUT.hz - 4)]);
  };
  corner(-1, 1, front);
  corner(1, 1, front);
  corner(-1, -1, back);
  corner(1, -1, back);
  list.push([OUT.cx, OUT.hz - 4], [OUT.cx, -(OUT.hz - 4)]);
  return list.flatMap(([x, z]) => leg(x, z));
}

/** Twin pedestals (poker): column with brass collar on a dark plinth, four splayed feet each with a brass toe. */
function pedestals() {
  const boxes = [];
  const segs = [];
  for (const px of [-4, 20]) {
    boxes.push({ mat: 'wood', x0: px - 3, x1: px + 3, z0: -3, z1: 3, y0: 1.5, y1: 6.75 });
    boxes.push({ mat: 'trim', x0: px - 3.5, x1: px + 3.5, z0: -3.5, z1: 3.5, y0: 5, y1: 5.5 });
    boxes.push({ mat: 'rail', x0: px - 4, x1: px + 4, z0: -4, z1: 4, y0: 0, y1: 1.5 });
    for (const [dx, dz] of [[1, 1], [1, -1], [-1, 1], [-1, -1]]) {
      const n = Math.SQRT1_2;
      segs.push({ mat: 'wood', cx: px + dx * 6, cz: dz * 6, y0: 0, y1: 1.5, len: 9, thick: 2.5, tx: dx * n, tz: dz * n });
      segs.push({ mat: 'trim', cx: px + dx * 10.5, cz: dz * 10.5, y0: 0, y1: 1.75, len: 2, thick: 3, tx: dx * n, tz: dz * n });
    }
  }
  return { boxes, segs };
}

/** Dealer chip tray: brass rim and seven chip columns (the tray texture colours them) sitting on the felt. */
function tray(shape) {
  if (shape !== 'crescent' && shape !== 'oval') return [];
  const out = [
    { mat: 'trim', x0: 0.5, x1: 15.5, z0: -13.75, z1: -13.25, y0: 14, y1: 14.75 },
    { mat: 'trim', x0: 0.5, x1: 15.5, z0: -10.75, z1: -10.25, y0: 14, y1: 14.75 },
    { mat: 'trim', x0: 0.5, x1: 1, z0: -13.25, z1: -10.75, y0: 14, y1: 14.75 },
    { mat: 'trim', x0: 15, x1: 15.5, z0: -13.25, z1: -10.75, y0: 14, y1: 14.75 },
    { mat: 'rail', x0: 1, x1: 15, z0: -13.25, z1: -10.75, y0: 14, y1: 14.1 },
  ];
  for (let i = 0; i < 7; i++) out.push({ mat: 'tray', x0: 1.25 + 2 * i, x1: 2.75 + 2 * i, z0: -13.1, z1: -10.9, y0: 14.1, y1: 14.85 });
  return out;
}

/** Lit props: table-limit placard (dealer side) or the roulette tote board on a brass pole. */
function props(shape) {
  // table-limit placard: brass stanchion 1 × 7 px with a 6 × 4 px white enamel plate facing the players (lit; the
  // limits text is W2's); roulette's sits at the back-right, beside the tote board
  const px = shape === 'roulette' ? 20 : -11;
  const plate = (x) => [
    { mat: 'trim', x0: x - 0.5, x1: x + 0.5, z0: -13.75, z1: -12.75, y0: 14, y1: 21 },
    { mat: 'placard', x0: x - 3, x1: x + 3, z0: -13.5, z1: -13, y0: 19, y1: 23, emit: 10, faceUv: [0, 2.67, 16, 13.33] },
  ];
  if (shape === 'roulette') {
    return [
      ...plate(px),
      { mat: 'trim', x0: 28.5, x1: 29.5, z0: -13.5, z1: -12.5, y0: 15.5, y1: 26 },
      { mat: 'tote', x0: 26, x1: 31.5, z0: -14, z1: -12, y0: 25, y1: 32, emit: 12 },
    ];
  }
  return plate(px);
}

/** The whole table: {boxes, segs} in local px. */
export function tableGeometry(shape) {
  const boxes = [];
  const segs = [];
  const add = (g) => {
    boxes.push(...g.boxes);
    segs.push(...g.segs);
  };
  const crescent = shape === 'crescent';
  const railSides = { front: true, back: !crescent, left: true, right: true };
  const craps = shape === 'craps';
  const top = craps ? 16 : RAIL_TOP;
  // body: dark kick band, deep apron, slab, proud brass band (softly lit)
  add(ring(shape, { inO: 1.25, inI: 2, y0: 6, y1: 6.75, mat: 'rail' }));
  add(ring(shape, { inO: 1, inI: 2, y0: 6.75, y1: 10.5, mat: 'wood' }));
  add(ring(shape, { inO: 0, inI: 2, y0: 10.5, y1: 11.5, mat: 'wood' }));
  add(ring(shape, { inO: -0.5, inI: 1, y0: 11.5, y1: 12.5, mat: 'trim', emit: 'band' }));
  // padded rail: outer drop, cushion, bevelled cap, brass lip on the felt side
  add(ring(shape, { inO: -1, inI: 0, y0: 12.5, y1: 13.5, mat: 'rail', sides: railSides }));
  add(ring(shape, { inO: -1, inI: 2, y0: 13.5, y1: top - 0.5, mat: 'rail', sides: railSides }));
  add(ring(shape, { inO: -0.5, inI: 1.5, y0: top - 0.5, y1: top, mat: 'rail', sides: railSides }));
  add(ring(shape, { inO: 2, inI: 2.5, y0: 13.5, y1: 14.25, mat: 'trim', sides: railSides }));
  if (crescent) {
    // dealer side: a low wooden ledge with a brass lip instead of the padded rail
    boxes.push({ mat: 'wood', x0: -16 + 2, x1: 32 - 2, z0: -16, z1: -14, y0: 12.5, y1: 14.25 });
    boxes.push({ mat: 'trim', x0: -16 + 2, x1: 32 - 2, z0: -14, z1: -13.5, y0: 13.5, y1: 14.25 });
  }
  // hidden fills: underside panel, slab core, felt (its edge tucks under the cushion)
  const roulette = shape === 'roulette';
  const layout = (xm) => (xm > LAYOUT.x0 && xm < LAYOUT.x1 ? [[LAYOUT.z0, LAYOUT.z1]] : []);
  boxes.push(...columns(shape, [
    { mat: 'wood', y0: 6.75, y1: 7.25, inset: 1.75 },
    { mat: 'wood', y0: 10.5, y1: 13, inset: 1.5 },
    { mat: 'felt', y0: 13, y1: FELT_TOP, inset: 1.5, clip: roulette ? (xm, zs) => inter(zs, layout(xm)) : null },
  ]));
  if (roulette) {
    // wheel column: polished deck, raised bowl rim (wood + lit brass band), bowl floor with a static wheel the rotor
    // covers when it draws (rotor Ø ≥ bowl opening)
    boxes.push(...columns(shape, [
      { mat: 'wood', y0: 13, y1: 14.5, inset: 1.5, clip: (xm, zs) => minus(minus(zs, layout(xm)), circleSpan(xm, BOWL.r - 0.5)) },
      { mat: 'bowl', y0: 13, y1: 13.5, inset: 1.5, clip: (xm, zs) => inter(zs, circleSpan(xm, BOWL.r - 1.25)) },
    ]));
    segs.push(...bowlRim());
  }
  if (shape === 'oval') add(pedestals());
  else boxes.push(...legs(shape));
  boxes.push(...tray(shape), ...props(shape));
  return { boxes, segs };
}
