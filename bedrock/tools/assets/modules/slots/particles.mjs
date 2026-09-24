// Slots-owned particles (animation/slots.md §9.3; architecture §2.3: `ember_burst`, `void_motes` belong to slots;
// coin_burst / sparkle / confetti / jackpot_burst are core's). Bedrock particle JSON; `variable.count` (set with a
// MolangVariableMap by the spawner) scales the burst, capped at the budget (≤ 60 per burst).
const ATLAS = 'textures/particle/burmaldaholic_slots';

function effect(id, { count, cap, shape, speed, life, accel, drag, size, row, fps, colours }) {
  return {
    format_version: '1.10.0',
    particle_effect: {
      description: { identifier: `burmaldaholic:${id}`, basic_render_parameters: { material: 'particles_blend', texture: ATLAS } },
      components: {
        'minecraft:emitter_rate_instant': { num_particles: `math.min(variable.count > 0 ? variable.count : ${count}, ${cap})` },
        'minecraft:emitter_lifetime_once': { active_time: 0.1 },
        'minecraft:emitter_shape_box': { half_dimensions: shape, direction: 'outwards' },
        'minecraft:particle_initial_speed': speed,
        'minecraft:particle_lifetime_expression': { max_lifetime: life },
        'minecraft:particle_motion_dynamic': { linear_acceleration: accel, linear_drag_coefficient: drag },
        'minecraft:particle_appearance_billboard': {
          size: [size, size],
          facing_camera_mode: 'rotate_xyz',
          uv: { texture_width: 64, texture_height: 64, flipbook: { base_UV: [0, row * 8], size_UV: [8, 8], step_UV: [8, 0], frames_per_second: fps, max_frame: 4, stretch_to_lifetime: true, loop: false } },
        },
        'minecraft:particle_appearance_tinting': { color: { interpolant: 'variable.particle_age / variable.particle_lifetime', gradient: colours } },
      },
    },
  };
}

export const PARTICLES = {
  ember_burst: effect('ember_burst', {
    count: 12,
    cap: 60,
    shape: [0.45, 0.25, 0.05],
    speed: 1.5,
    life: '0.6 + variable.particle_random_1 * 0.4',
    accel: [0, 1.2, 0],
    drag: 1.5,
    size: '0.05 + variable.particle_random_2 * 0.04',
    row: 0,
    fps: 6,
    colours: { '0.0': '#FFFFF0C0', '0.4': '#FFFF9A30', '1.0': '#00C02000' },
  }),
  void_motes: effect('void_motes', {
    count: 8,
    cap: 60,
    shape: [0.2, 0.5, 0.05],
    speed: 0.2,
    life: '1.6 + variable.particle_random_1 * 0.8',
    accel: [0, 0.4, 0],
    drag: 0.6,
    size: '0.04 + variable.particle_random_2 * 0.03',
    row: 1,
    fps: 3,
    colours: { '0.0': '#00B040FF', '0.2': '#FFD090FF', '1.0': '#006A2A9A' },
  }),
};
