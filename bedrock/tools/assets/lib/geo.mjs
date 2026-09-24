// Bedrock entity geometry builders (docs/architecture/animation.md §2.9): generated `*.geo.json` instead of
// Blockbench files, so props (slot reels, wheels, dice, coins, card hands) come from code with UVs that match
// the generated textures. Format 1.12.0 (per-face UV supported; `uv` = [u, v] box UV or per-face map).

const r4 = (v) => Math.round(v * 1e4) / 1e4 || 0;
const vec = (a) => a.map(r4);

/** A cube: `origin` = min corner [x,y,z] in model pixels, `size` [w,h,d], `uv` box UV [u,v] or per-face {north:{uv,uv_size}…}. */
export function cube(origin, size, uv, { inflate, pivot, rotation, mirror } = {}) {
  const c = { origin: vec(origin), size: vec(size), uv: Array.isArray(uv) ? vec(uv) : uv };
  if (inflate) c.inflate = r4(inflate);
  if (pivot) c.pivot = vec(pivot);
  if (rotation) c.rotation = vec(rotation);
  if (mirror) c.mirror = true;
  return c;
}

/**
 * A flat quad facing north (−Z) — a zero-depth cube with only a north face UV (reel windows, card faces,
 * wheel faces). `x, y` = bottom-left corner, `w, h` size, `z` depth position, `uv` = [u, v, uw, uh] on the texture.
 */
export function quad(x, y, z, w, h, [u, v, uw, uh], face = 'north') {
  return cube([x, y, z], [w, h, 0], { [face]: { uv: vec([u, v]), uv_size: vec([uw, uh]) } });
}

/** A bone. `pivot` [x,y,z]; `cubes` from cube()/quad(). */
export function bone(name, { parent, pivot = [0, 0, 0], rotation, cubes = [] } = {}) {
  const b = { name };
  if (parent) b.parent = parent;
  b.pivot = vec(pivot);
  if (rotation) b.rotation = vec(rotation);
  if (cubes.length) b.cubes = cubes;
  return b;
}

/**
 * A complete geometry file: `id` without the `geometry.` prefix, texture size, visible bounds (blocks), bones.
 */
export function geometry(id, { texW, texH, bones, boundsW = 2, boundsH = 2, boundsOffset = [0, 1, 0] }) {
  const names = new Set();
  for (const b of bones) {
    if (names.has(b.name)) throw new Error(`geo ${id}: duplicate bone ${b.name}`);
    if (b.parent && !names.has(b.parent)) throw new Error(`geo ${id}: bone ${b.name} before its parent ${b.parent}`);
    names.add(b.name);
  }
  return {
    format_version: '1.12.0',
    'minecraft:geometry': [
      {
        description: {
          identifier: `geometry.${id}`,
          texture_width: texW,
          texture_height: texH,
          visible_bounds_width: boundsW,
          visible_bounds_height: boundsH,
          visible_bounds_offset: vec(boundsOffset),
        },
        bones,
      },
    ],
  };
}
