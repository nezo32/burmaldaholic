// Particle atlases (docs/architecture/animation.md §2.3, §2.10): one atlas per owner (core
// `textures/particle/burmaldaholic_fx.png`, slots `burmaldaholic_slots.png`), one flipbook ROW per sprite set,
// frames left to right. Java gets the same frames as separate `textures/particle/<ns>/<id>_<n>.png` files.
import { blit, image } from './grid.mjs';

/**
 * Packs sprite sets into rows. `sets` = [{name, frames: [img…]}] (all frames of a set share one size).
 * Returns {img, uv: {name: {x, y, w, h, frames}}}. Row height = the set's frame height; rows stack from the top.
 */
export function packRows(sets, { w = 128, h = 128 } = {}) {
  const img = image(w, h);
  const uv = {};
  let y = 0;
  for (const s of sets) {
    const fw = s.frames[0].w;
    const fh = s.frames[0].h;
    if (s.frames.some((f) => f.w !== fw || f.h !== fh)) throw new Error(`atlas: frames of ${s.name} differ in size`);
    if (fw * s.frames.length > w) throw new Error(`atlas: ${s.name} is wider than the atlas`);
    if (y + fh > h) throw new Error(`atlas: out of rows at ${s.name}`);
    s.frames.forEach((f, i) => blit(img, f, i * fw, y));
    uv[s.name] = { x: 0, y, w: fw, h: fh, frames: s.frames.length };
    y += fh;
  }
  return { img, uv };
}

/**
 * `minecraft:particle_appearance_billboard` UV block for a flipbook row (Bedrock particle JSON), in texels of an
 * atlas `texW × texH`. `fps` frames per second, `loop` or stretch-to-lifetime.
 */
export function flipbookUv(entry, { texW = 128, texH = 128, fps = 8, loop = false, stretch = !loop } = {}) {
  return {
    texture_width: texW,
    texture_height: texH,
    flipbook: {
      base_UV: [entry.x, entry.y],
      size_UV: [entry.w, entry.h],
      step_UV: [entry.w, 0],
      frames_per_second: fps,
      max_frame: entry.frames,
      stretch_to_lifetime: stretch,
      loop,
    },
  };
}

/**
 * A minimal Bedrock particle definition (single burst, billboard, flipbook). Lanes that own particle files
 * (B-L1 core, B-L10 slots…) build on it; every numeric field is a plain number or a Molang string.
 */
export function particleJson(id, { texture, uv, count = 8, lifetime = 0.6, speed = 2, size = 0.12, gravity = -2, fps = 8, loop = false, tint }) {
  return {
    format_version: '1.10.0',
    particle_effect: {
      description: { identifier: id, basic_render_parameters: { material: 'particles_alpha', texture } },
      components: {
        'minecraft:emitter_rate_instant': { num_particles: count },
        'minecraft:emitter_lifetime_once': { active_time: 0.05 },
        'minecraft:emitter_shape_sphere': { radius: 0.2, direction: 'outwards' },
        'minecraft:particle_lifetime_expression': { max_lifetime: lifetime },
        'minecraft:particle_initial_speed': speed,
        'minecraft:particle_motion_dynamic': { linear_acceleration: [0, gravity, 0] },
        'minecraft:particle_appearance_billboard': { size: [size, size], facing_camera_mode: 'rotate_xyz', uv: flipbookUv(uv, { fps, loop }) },
        ...(tint ? { 'minecraft:particle_appearance_tinting': { color: tint } } : {}),
      },
    },
  };
}
