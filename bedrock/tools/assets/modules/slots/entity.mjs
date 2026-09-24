// `burmaldaholic:slot_reels` prop files (animation/slots.md §6.6, task BS6): BP entity, RP client entity, per-machine
// geometry, animations, animation controllers and render controllers — generated, no Blockbench. Every number
// that the script also uses comes from `CABINET` (src/games/slots/v2/present/cabinet-driver.ts), bundled by
// slots.mjs, so the entity and the driver can never disagree.
//
// Model space: px, entity at the block's top centre, front = −Z (north faces), model −X = the viewer's left.
// Molang conventions: `v.spin<r>` (reel r shows the blur loop), `v.land_t<r>` (land time for the bounce), watcher
// controllers stamp `v.*_t` when a property changes (win blink, tumble pop, sticky grow, coin pop, wheel spins).
import { CAB_UV, OVL_UV, WHEEL_UV, WHEEL_WEDGES } from './world.mjs';

const FV_GEO = '1.16.0';
const FV_RP = '1.10.0';
const FV_ANIM = '1.10.0';
const ID = 'slot_reels';
const NS = 'burmaldaholic';

// window geometry (px)
export const WIN = { x0: -6.5, y0: 3.4, cell: 2.6, zReel: -6.1, zFinal: -6.16, zSticky: -6.2, zCoin: -6.22, zFrame: -6.26, zGlass: -6.4 };
const cellX = (r) => WIN.x0 + r * WIN.cell;
/** row 0 = top of the window */
const cellY = (row) => WIN.y0 + (2 - row) * WIN.cell;

const face = (uv, size) => ({ uv, uv_size: size });
const quad = (origin, size, uv, uvSize) => ({ origin, size: [size[0], size[1], 0], uv: { north: face(uv, uvSize) } });
const boxUv = (U, w, h, d) => ({
  north: face(U.front.slice(0, 2), [w, h]),
  south: face(U.back.slice(0, 2), [w, h]),
  east: face(U.side.slice(0, 2), [d, h]),
  west: face(U.side.slice(0, 2), [d, h]),
  up: face(U.top.slice(0, 2), [w, d]),
  down: face(U.top.slice(0, 2), [w, d]),
});
const trimUv = () => Object.fromEntries(['north', 'south', 'east', 'west', 'up', 'down'].map((f) => [f, face(CAB_UV.trim.slice(0, 2), [1, 1])]));

const prop = (C, k) => `q.property('${C.prop[k]}')`;
const P = (name) => `q.property('${name}')`;

/** Per-machine geometry. */
export function geometry(C, machine, stripLengths) {
  void stripLengths;
  const bones = [];
  bones.push({ name: 'root', pivot: [0, 0, 0] });
  // cabinet body + marquee box + window bezel
  bones.push({
    name: 'cabinet',
    parent: 'root',
    pivot: [0, 0, 0],
    cubes: [
      { origin: [-7, 0, -6], size: [14, 13, 12], uv: boxUv(CAB_UV, 14, 13, 12) },
      {
        origin: [-7.5, 13, -6.5],
        size: [15, 4, 13],
        uv: {
          north: face(CAB_UV.mqFront.slice(0, 2), [15, 4]),
          south: face(CAB_UV.mqBack.slice(0, 2), [15, 4]),
          east: face(CAB_UV.mqSide.slice(0, 2), [13, 4]),
          west: face(CAB_UV.mqSide.slice(0, 2), [13, 4]),
          up: face(CAB_UV.mqTop.slice(0, 2), [15, 13]),
          down: face(CAB_UV.mqTop.slice(0, 2), [15, 13]),
        },
      },
      { origin: [-7, 11.2, -6.7], size: [14, 0.6, 0.7], uv: trimUv() },
      { origin: [-7, 2.8, -6.7], size: [14, 0.6, 0.7], uv: trimUv() },
      { origin: [-7, 3.4, -6.7], size: [0.5, 7.8, 0.7], uv: trimUv() },
      { origin: [6.5, 3.4, -6.7], size: [0.5, 7.8, 0.7], uv: trimUv() },
      ...(machine === 'end' ? [{ origin: [-1, 17, -1], size: [2, 3, 2], uv: Object.fromEntries(['north', 'south', 'east', 'west', 'up', 'down'].map((f) => [f, face(CAB_UV.stand.slice(0, 2), [2, 2])])) }] : []),
    ],
  });
  // marquee panel (animated texture)
  bones.push({ name: 'marquee', parent: 'root', pivot: [0, 15, -6.6], cubes: [quad([-7, 13.5, -6.6], [14, 3], [0, 0], [64, 64])] });
  // reels: one quad each, full texture (uv_anim selects the window)
  for (let r = 0; r < 5; r++) bones.push({ name: `reel${r}`, parent: 'root', pivot: [cellX(r) + WIN.cell / 2, WIN.y0, WIN.zReel], cubes: [quad([cellX(r), WIN.y0, WIN.zReel], [WIN.cell, WIN.cell * 3], [0, 0], [64, 64])] });
  // overlay: glass, win frames, tumble pops, plates; machine extras
  const ov = [];
  ov.push({ name: 'glass', parent: 'root', pivot: [0, 0, 0], cubes: [quad([WIN.x0, WIN.y0, WIN.zGlass], [WIN.cell * 5, WIN.cell * 3], OVL_UV.glass.slice(0, 2), OVL_UV.glass.slice(2))] });
  for (let r = 0; r < 5; r++)
    for (let row = 0; row < 3; row++) {
      const id = `${r}${row}`;
      ov.push({ name: `win_${id}`, parent: 'root', pivot: [0, 0, 0], cubes: [quad([cellX(r), cellY(row), WIN.zFrame], [WIN.cell, WIN.cell], OVL_UV.win.slice(0, 2), [16, 16])] });
      ov.push({ name: `pop_${id}`, parent: 'root', pivot: [0, 0, 0], cubes: [quad([cellX(r), cellY(row), WIN.zFrame - 0.02], [WIN.cell, WIN.cell], OVL_UV.pop.slice(0, 2), [16, 16])] });
    }
  if (machine === 'nether') {
    C.multPlates.forEach((v, i) => ov.push({ name: `plate_${v}`, parent: 'root', pivot: [0, 0, 0], cubes: [quad([-2.4, 0.9, WIN.zFrame], [4.8, 1.8], OVL_UV.plate(i).slice(0, 2), OVL_UV.plate(i).slice(2))] }));
    for (let r = 0; r < 5; r++)
      for (let row = 0; row < 3; row++) {
        const id = `${r}${row}`;
        const pivot = [cellX(r) + WIN.cell / 2, cellY(row) + WIN.cell / 2, WIN.zCoin];
        ov.push({ name: `coin_${id}`, parent: 'root', pivot, cubes: [quad([cellX(r), cellY(row), WIN.zCoin], [WIN.cell, WIN.cell], OVL_UV.coin.slice(0, 2), [16, 16])] });
        ov.push({ name: `empty_${id}`, parent: 'root', pivot: [0, 0, 0], cubes: [quad([cellX(r), cellY(row), WIN.zCoin + 0.01], [WIN.cell, WIN.cell], OVL_UV.empty.slice(0, 2), [16, 16])] });
      }
  }
  if (machine === 'end')
    for (let r = 1; r <= 3; r++)
      ov.push({ name: `sticky${r}`, parent: 'root', pivot: [cellX(r) + WIN.cell / 2, WIN.y0 + WIN.cell * 1.5, WIN.zSticky], cubes: [quad([cellX(r), WIN.y0, WIN.zSticky], [WIN.cell, WIN.cell * 3], OVL_UV.sticky.slice(0, 2), OVL_UV.sticky.slice(2))] });
  bones.push(...ov);
  // Nether NICE (D3): final-window overlay, 15 cells × 11 symbols
  if (machine === 'nether')
    for (let r = 0; r < 5; r++)
      for (let row = 0; row < 3; row++)
        for (let s = 0; s < 11; s++)
          bones.push({ name: `fin_${r}${row}_${s}`, parent: 'root', pivot: [0, 0, 0], cubes: [quad([cellX(r), cellY(row), WIN.zFinal], [WIN.cell, WIN.cell], [(s % 4) * 16, Math.floor(s / 4) * 16], [16, 16])] });
  // End: Dragon Wheel on top
  if (machine === 'end') {
    const cy = 26;
    const ringQuad = (name, size, z, uv) => ({ name, parent: 'wheel', pivot: [0, cy, z], cubes: [quad([-size / 2, cy - size / 2, z], [size, size], uv.slice(0, 2), uv.slice(2))] });
    bones.push({ name: 'wheel', parent: 'root', pivot: [0, cy, -1.5] });
    bones.push(ringQuad('ring0', 14, -1.6, WHEEL_UV.outer));
    bones.push(ringQuad('ring1', 10.5, -1.7, WHEEL_UV.middle));
    bones.push(ringQuad('ring2', 7, -1.8, WHEEL_UV.core));
    bones.push({ name: 'pointer', parent: 'wheel', pivot: [0, cy + 7, -1.9], cubes: [quad([-1.5, cy + 6.2, -1.9], [3, 3], WHEEL_UV.pointer.slice(0, 2), WHEEL_UV.pointer.slice(2))] });
  }
  return {
    format_version: FV_GEO,
    'minecraft:geometry': [
      {
        description: { identifier: `geometry.${NS}.${ID}.${machine}`, texture_width: 64, texture_height: 64, visible_bounds_width: 3, visible_bounds_height: 3.5, visible_bounds_offset: [0, 1.25, 0] },
        bones,
      },
    ],
  };
}

const MACHINES = ['overworld', 'nether', 'end'];
const texKey = (m, what) => `${m}_${what}`;

/** Molang: strip length incl. padding for reel r by machine. */
function paddedLen(C, strips, r) {
  const L = MACHINES.map((m) => strips[m][r].length + C.stripPadTop + C.stripPadBottom);
  return `(${P(C.prop.machine)} == 0 ? ${L[0]} : (${P(C.prop.machine)} == 1 ? ${L[1]} : ${L[2]}))`;
}

/** Land bounce (F4) in cells as a function of u = seconds since the land animation fired. */
function bounceExpr(C, r) {
  const a = C.landArriveSeconds;
  const s = C.landSettleSeconds;
  const o = C.landOvershootCells;
  const u = `(q.life_time - v.land_t${r})`;
  return `(${u} < ${a} ? (-0.8 + ${(0.8 + o).toFixed(2)} * (1 - math.pow(1 - ${u} / ${a}, 3))) : (${u} < ${s} ? ${o} * (1 - (${u} - ${a}) / ${(s - a).toFixed(2)}) : 0))`;
}

const spinning = (C, r) => `(v.spin${r} > 0 && ${P(C.prop.state)} == 'spin')`;

export function renderControllers(C, strips) {
  const rc = {};
  const machineIdx = P(C.prop.machine);
  rc[`controller.render.${NS}.${ID}.cabinet`] = {
    arrays: { geometries: { 'Array.geo': MACHINES.map((m) => `Geometry.${m}`) }, textures: { 'Array.tex': MACHINES.map((m) => `Texture.${texKey(m, 'cabinet')}`) } },
    geometry: `Array.geo[${machineIdx}]`,
    materials: [{ '*': 'Material.default' }],
    textures: [`Array.tex[${machineIdx}]`],
    part_visibility: [{ '*': false }, { cabinet: true }],
  };
  for (let r = 0; r < 5; r++) {
    const blurCells = C.blurLoop + 3;
    const Lp = paddedLen(C, strips, r);
    rc[`controller.render.${NS}.${ID}.reel${r}`] = {
      arrays: {
        geometries: { 'Array.geo': MACHINES.map((m) => `Geometry.${m}`) },
        textures: { 'Array.tex': [...MACHINES.map((m) => `Texture.${texKey(m, `strip${r}`)}`), ...MACHINES.map((m) => `Texture.${texKey(m, 'blur')}`)] },
      },
      geometry: `Array.geo[${machineIdx}]`,
      materials: [{ '*': 'Material.default' }],
      textures: [`Array.tex[${machineIdx} + (${spinning(C, r)} ? 3 : 0)]`],
      part_visibility: [{ '*': false }, { [`reel${r}`]: true }],
      uv_anim: {
        offset: [0, `${spinning(C, r)} ? math.mod(q.life_time * ${C.blurCellsPerSecond} + ${r * 1.7}, ${C.blurLoop}) / ${blurCells} : (${P(C.prop.reels[r])} + ${C.stripPadTop} + ${bounceExpr(C, r)}) / ${Lp}`],
        scale: [1, `${spinning(C, r)} ? 3 / ${blurCells} : 3 / ${Lp}`],
      },
    };
  }
  // overlay: glass always; win frames (blink 2 Hz for the first seconds, then steady); tumble pops; plates; Hoard; sticky
  const vis = [{ '*': false }, { glass: true }];
  const winBits = P(C.prop.win);
  const notSpin = `${P(C.prop.state)} != 'spin'`;
  const blink = `(q.life_time - v.win_t > ${C.winBlinkSeconds} || math.mod(math.floor((q.life_time - v.win_t) * 4), 2) == 0)`;
  const popOn = `(q.life_time - v.pop_t < ${C.popSeconds})`;
  const hoard = `${P(C.prop.hold)} > 0`;
  for (let r = 0; r < 5; r++)
    for (let row = 0; row < 3; row++) {
      const id = `${r}${row}`;
      const bit = r * 3 + row;
      const has = (expr) => `math.mod(math.floor(${expr} / ${2 ** bit}), 2) == 1`;
      vis.push({ [`win_${id}`]: `${notSpin} && ${has(winBits)} && ${blink}` });
      vis.push({ [`pop_${id}`]: `${notSpin} && ${has(winBits)} && ${popOn}` });
      vis.push({ [`coin_${id}`]: `${hoard} && ${has(P(C.prop.hold))}` });
      vis.push({ [`empty_${id}`]: `${hoard} && !(${has(P(C.prop.hold))})` });
    }
  for (const v of C.multPlates) vis.push({ [`plate_${v}`]: `${P(C.prop.mult)} == ${v}` });
  for (let r = 1; r <= 3; r++) vis.push({ [`sticky${r}`]: `math.mod(math.floor(${P(C.prop.sticky)} / ${2 ** (r - 1)}), 2) == 1` });
  rc[`controller.render.${NS}.${ID}.overlay`] = {
    arrays: { geometries: { 'Array.geo': MACHINES.map((m) => `Geometry.${m}`) }, textures: { 'Array.tex': MACHINES.map((m) => `Texture.${texKey(m, 'overlay')}`) } },
    geometry: `Array.geo[${machineIdx}]`,
    materials: [{ '*': 'Material.default' }],
    textures: [`Array.tex[${machineIdx}]`],
    part_visibility: vis,
  };
  // marquee: 8-frame flipbook, frame chosen by state; texture variant normal / free spins / jackpot
  const st = P(C.prop.state);
  const frame = `(${st} == 'spin' ? math.mod(math.floor(q.life_time * 20), 4) : (${st} == 'win' ? 4 + math.mod(math.floor(q.life_time * 4), 2) : (${st} == 'big' ? 6 + math.mod(math.floor(q.life_time * 8), 2) : (${st} == 'jackpot' ? math.mod(math.floor(q.life_time * 12), 4) : (${st} == 'feature' ? math.mod(math.floor(q.life_time * 6), 4) : math.mod(math.floor(q.life_time * 6.67), 4))))))`;
  rc[`controller.render.${NS}.${ID}.marquee`] = {
    arrays: {
      geometries: { 'Array.geo': MACHINES.map((m) => `Geometry.${m}`) },
      textures: { 'Array.tex': MACHINES.flatMap((m) => ['marquee', 'marquee_fs', 'marquee_jackpot'].map((k) => `Texture.${texKey(m, k)}`)) },
    },
    geometry: `Array.geo[${machineIdx}]`,
    materials: [{ '*': 'Material.default' }],
    textures: [`Array.tex[${machineIdx} * 3 + (${st} == 'jackpot' ? 2 : (${st} == 'feature' ? 1 : 0))]`],
    part_visibility: [{ '*': false }, { marquee: true }],
    uv_anim: { offset: [0, `${frame} / 8`], scale: [1, '1 / 8'] },
  };
  // End wheel
  rc[`controller.render.${NS}.${ID}.wheel`] = {
    geometry: 'Geometry.end',
    materials: [{ '*': 'Material.default' }],
    textures: ['Texture.end_wheel'],
    part_visibility: [{ '*': false }, { ring0: true }, { ring1: true }, { ring2: true }, { pointer: true }],
  };
  // Nether final window (NICE D3): cell (r,row) shows symbol s when the packed row value matches
  const fin = [{ '*': false }];
  for (let row = 0; row < 3; row++)
    for (let r = 0; r < 5; r++)
      for (let s = 0; s < 11; s++) fin.push({ [`fin_${r}${row}_${s}`]: `${notSpin} && math.mod(math.floor(${P(C.prop.rows[row])} / ${16 ** r}), 16) == ${s + 1}` });
  rc[`controller.render.${NS}.${ID}.final`] = {
    geometry: 'Geometry.nether',
    materials: [{ '*': 'Material.default' }],
    textures: ['Texture.nether_cells'],
    part_visibility: fin,
  };
  return { format_version: FV_RP, render_controllers: rc };
}

/** Animations: per-reel land (timeline stamps), and the always-on `fx` loop (sticky grow, coin pop, wheel angles). */
export function animations(C) {
  const anims = {};
  for (let r = 0; r < 5; r++)
    anims[`animation.${NS}.${ID}.land${r}`] = { animation_length: C.landSettleSeconds, timeline: { '0.0': [`v.spin${r} = 0; v.land_t${r} = q.life_time;`] } };
  const bones = {};
  for (let r = 1; r <= 3; r++) bones[`sticky${r}`] = { scale: [1, `math.clamp(0.34 + 0.66 * (q.life_time - v.sticky_t${r}) / ${C.stickyGrowSeconds}, 0.34, 1)`, 1] };
  for (let r = 0; r < 5; r++)
    for (let row = 0; row < 3; row++) bones[`coin_${r}${row}`] = { scale: `(q.life_time - v.hold_t < ${C.coinPopSeconds}) ? 1.3 - 0.3 * (q.life_time - v.hold_t) / ${C.coinPopSeconds} : 1` };
  const angle = (ring) => {
    const k = `math.min((q.life_time - v.wheel_t${ring}) / ${C.wheelSpinSeconds[ring]}, 1)`;
    // outCubic from the previous angle to the target (3 extra turns); the tape's segment ends under the pointer
    return `(v.wheel_from${ring} + (v.wheel_to${ring} - v.wheel_from${ring}) * (1 - math.pow(1 - ${k}, 3)))`;
  };
  for (let ring = 0; ring < 3; ring++) bones[`ring${ring}`] = { rotation: [0, 0, angle(ring)] };
  // pointer flapper (extras-pvp §10.2 pattern): a sawtooth kick at every outer-ring wedge crossing while it spins
  const wedge = 360 / C.wheelRings[0];
  bones.pointer = { rotation: [0, 0, `(q.life_time - v.wheel_t0 < ${C.wheelSpinSeconds[0]}) ? 12 * (1 - math.mod(math.abs(${angle(0)}) / ${wedge}, 1)) : 0`] };
  anims[`animation.${NS}.${ID}.fx`] = { loop: true, bones };
  return { format_version: FV_ANIM, animations: anims };
}

/** Watcher controllers: stamp times when properties change (initialised so late viewers never replay). */
export function animationControllers(C, strips) {
  void strips;
  const ac = {};
  const watch = (name, cond, entry) => {
    ac[`controller.animation.${NS}.${ID}.${name}`] = {
      initial_state: 'default',
      states: {
        default: { transitions: [{ changed: cond }] },
        changed: { on_entry: entry, transitions: [{ default: '1' }] },
      },
    };
  };
  const reelsOn = [0, 1, 2, 3, 4].map((r) => `v.spin${r} = 1;`).join(' ');
  watch('spin', `${P(C.prop.seq)} != v.seq`, [`v.seq = ${P(C.prop.seq)}; ${reelsOn}`]);
  watch('win', `${P(C.prop.win)} != v.win_seen`, [`v.win_seen = ${P(C.prop.win)}; v.win_t = q.life_time;`]);
  watch('mult', `${P(C.prop.mult)} != v.mult_seen`, [`v.mult_seen = ${P(C.prop.mult)}; v.pop_t = q.life_time;`]);
  const stickyBit = (expr, r) => `(math.mod(math.floor(${expr} / ${2 ** (r - 1)}), 2) == 1)`;
  watch('sticky', `${P(C.prop.sticky)} != v.sticky_seen`, [
    [1, 2, 3].map((r) => `v.sticky_t${r} = (${stickyBit(P(C.prop.sticky), r)} && !${stickyBit('v.sticky_seen', r)}) ? q.life_time : v.sticky_t${r};`).join(' ') + ` v.sticky_seen = ${P(C.prop.sticky)};`,
  ]);
  watch('hold', `${P(C.prop.hold)} != v.hold_seen`, [`v.hold_seen = ${P(C.prop.hold)}; v.hold_t = q.life_time;`]);
  // wheel: ring = floor((w − 1) / 100), segment = mod(w − 1, 100); target angle puts the segment centre at 12 o'clock
  const w = P(C.prop.wheel);
  const ringOf = `math.floor((${w} - 1) / 100)`;
  const segOf = `math.mod(${w} - 1, 100)`;
  const n = C.wheelRings;
  const target = (ring) => `-((${segOf} + 0.5) * ${360 / n[ring]}) - ${360 * C.wheelTurns}`;
  watch(
    'wheel',
    `${w} != v.wheel_seen`,
    [
      `v.wheel_seen = ${w};`,
      ...[0, 1, 2].map((ring) => `v.wheel_from${ring} = (${w} > 0 && ${ringOf} == ${ring}) ? math.mod(v.wheel_to${ring}, 360) : v.wheel_from${ring}; v.wheel_to${ring} = (${w} > 0 && ${ringOf} == ${ring}) ? ${target(ring)} : v.wheel_to${ring}; v.wheel_t${ring} = (${w} > 0 && ${ringOf} == ${ring}) ? q.life_time : v.wheel_t${ring};`),
    ],
  );
  return { format_version: FV_ANIM, animation_controllers: ac };
}

export function clientEntity(C, strips) {
  const textures = {};
  for (const m of MACHINES) {
    textures[texKey(m, 'cabinet')] = `textures/entity/slots/${m}_cabinet`;
    textures[texKey(m, 'overlay')] = `textures/entity/slots/${m}_overlay`;
    textures[texKey(m, 'blur')] = `textures/entity/slots/${m}_blur`;
    for (const k of ['marquee', 'marquee_fs', 'marquee_jackpot']) textures[texKey(m, k)] = `textures/entity/slots/${m}_${k}`;
    for (let r = 0; r < 5; r++) textures[texKey(m, `strip${r}`)] = `textures/entity/slots/${m}_strip_${r}`;
  }
  textures.end_wheel = 'textures/entity/slots/end_wheel';
  textures.nether_cells = 'textures/entity/slots/nether_cells';
  const init = [
    `v.seq = ${P(C.prop.seq)};`,
    ...[0, 1, 2, 3, 4].map((r) => `v.spin${r} = (${P(C.prop.state)} == 'spin') ? 1 : 0; v.land_t${r} = -10;`),
    `v.win_seen = ${P(C.prop.win)}; v.win_t = -10; v.mult_seen = ${P(C.prop.mult)}; v.pop_t = -10;`,
    `v.sticky_seen = ${P(C.prop.sticky)}; v.sticky_t1 = -10; v.sticky_t2 = -10; v.sticky_t3 = -10; v.hold_seen = ${P(C.prop.hold)}; v.hold_t = -10;`,
    `v.wheel_seen = ${P(C.prop.wheel)};`,
    ...[0, 1, 2].map((ring) => `v.wheel_from${ring} = 0; v.wheel_to${ring} = 0; v.wheel_t${ring} = -10;`),
  ];
  const acNames = ['spin', 'win', 'mult', 'sticky', 'hold', 'wheel'];
  const animationsMap = { fx: `animation.${NS}.${ID}.fx`, ...Object.fromEntries(acNames.map((n) => [`watch_${n}`, `controller.animation.${NS}.${ID}.${n}`])) };
  for (let r = 0; r < 5; r++) animationsMap[`land${r}`] = `animation.${NS}.${ID}.land${r}`;
  const rcBase = `controller.render.${NS}.${ID}`;
  void strips;
  return {
    format_version: FV_RP,
    'minecraft:client_entity': {
      description: {
        identifier: C.entity,
        materials: { default: 'entity_alphatest' },
        textures,
        geometry: Object.fromEntries(MACHINES.map((m) => [m, `geometry.${NS}.${ID}.${m}`])),
        animations: animationsMap,
        scripts: { initialize: init, animate: ['fx', ...acNames.map((n) => `watch_${n}`)] },
        render_controllers: [
          `${rcBase}.cabinet`,
          ...[0, 1, 2, 3, 4].map((r) => `${rcBase}.reel${r}`),
          `${rcBase}.overlay`,
          `${rcBase}.marquee`,
          { [`${rcBase}.wheel`]: `${P(C.prop.machine)} == 2` },
          { [`${rcBase}.final`]: `${P(C.prop.machine)} == 1` },
        ],
      },
    },
  };
}

/** BP entity: AI-free, no collision, not pushable, invulnerable, not summonable by players (the script spawns it). */
export function serverEntity(C, strips) {
  const maxStop = Math.max(...MACHINES.flatMap((m) => strips[m].map((s) => s.length - 1)));
  if (maxStop > C.maxStop) throw new Error(`slot_reels: strip length ${maxStop + 1} exceeds the r* property range 0..${C.maxStop}`);
  const int = (lo, hi) => ({ type: 'int', range: [lo, hi], default: lo, client_sync: true });
  const properties = {
    [C.prop.machine]: int(0, 2),
    ...Object.fromEntries(C.prop.reels.map((p) => [p, int(0, C.maxStop)])),
    [C.prop.state]: { type: 'enum', values: [...C.states], default: 'idle', client_sync: true },
    [C.prop.seq]: int(0, C.seqModulo - 1),
    [C.prop.win]: int(0, 32767),
    [C.prop.sticky]: int(0, 7),
    [C.prop.hold]: int(0, 32767),
    [C.prop.mult]: int(0, C.maxMult),
    [C.prop.wheel]: int(0, 399),
    ...Object.fromEntries(C.prop.rows.map((p) => [p, int(0, 1048575)])),
  };
  return {
    format_version: '1.26.30',
    'minecraft:entity': {
      description: { identifier: C.entity, is_spawnable: false, is_summonable: true, properties },
      component_groups: {},
      components: {
        'minecraft:type_family': { family: [C.family, 'inanimate'] },
        'minecraft:collision_box': { width: 0.01, height: 0.01 },
        'minecraft:physics': { has_gravity: false, has_collision: false },
        'minecraft:damage_sensor': { triggers: [{ cause: 'all', deals_damage: 'no' }] },
        'minecraft:knockback_resistance': { value: 1.0 },
        'minecraft:fire_immune': {},
        'minecraft:health': { value: 1, max: 1 },
        'minecraft:conditional_bandwidth_optimization': { default_values: { max_optimized_distance: 48.0, max_dropped_ticks: 5, use_motion_prediction_hints: false } },
      },
      events: {},
    },
  };
}

export { WHEEL_WEDGES, prop };
