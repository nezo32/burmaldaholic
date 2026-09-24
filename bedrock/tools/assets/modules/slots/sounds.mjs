// Bedrock sound definitions for slots (animation/slots.md §8, SLOTS.md §10.7; task SX3 Bedrock half).
// MUST phase = vanilla sound files. A "+" composite of the spec is split: the main event keeps the spec id, the
// second layer is `<id>.layer` (the presenter plays both in the same tick). Pitch ladders are applied by code.
// NICE custom .ogg files later replace the `name`s under the same ids. Category `player` (research §8).
const S = (name, pitch = 1, volume = 1) => ({ name, pitch, volume });

export const SLOT_SOUNDS = {
  spin_loop: [S('sounds/minecart/inside', 1.6, 0.25)],
  reel_stop: [S('sounds/random/click', 0.7, 0.5)],
  'reel_stop.layer': [S('sounds/note/pling', 1, 0.35)],
  scatter_land: [S('sounds/block/amethyst/resonate1', 1, 0.8), S('sounds/block/amethyst/resonate2', 1, 0.8)],
  'scatter_land.layer': [S('sounds/note/bell', 1, 0.6)],
  bonus_land: [S('sounds/random/chestclosed', 1.3, 0.7)],
  anticipation: [S('sounds/block/beacon/ambient', 1, 0.7)],
  returned: [S('sounds/note/hat', 0.8, 0.3)],
  win_small: [S('sounds/random/orb', 1.2, 0.6)],
  'win_small.layer': [S('sounds/note/icechime', 1.5, 0.4)],
  win_nice: [S('sounds/note/icechime', 1, 0.6)],
  'win_nice.layer': [S('sounds/random/orb', 1.1, 0.5)],
  big_win: [S('sounds/random/levelup', 1, 0.8)],
  'big_win.layer': [S('sounds/block/amethyst/resonate3', 1.2, 0.7)],
  mega_win: [S('sounds/random/toast', 1, 0.7)],
  'mega_win.layer': [S('sounds/block/bell/bell_use01', 1.5, 0.4)],
  epic_win: [S('sounds/random/toast', 0.9, 0.9)],
  'epic_win.layer': [S('sounds/fireworks/largeBlast1', 1, 0.5)],
  max_win: [S('sounds/random/anvil_land', 1.2, 0.3)],
  'max_win.layer': [S('sounds/fireworks/largeBlast1', 0.9, 0.6)],
  rollup_tick: [S('sounds/note/hat', 1, 0.35)],
  rollup_end: [S('sounds/note/bell', 1.5, 0.5)],
  fs_intro: [S('sounds/block/beacon/activate', 1.3, 0.8)],
  'fs_intro.layer': [S('sounds/block/amethyst/resonate4', 1, 0.7)],
  fs_outro: [S('sounds/block/beacon/deactivate', 1.2, 0.8)],
  'fs_outro.layer': [S('sounds/note/icechime', 1, 0.5)],
  // free-spin "music" (lane B-L9 plays `player.playMusic('burmaldaholic.slots.fs_music.<m>', {loop})`): category
  // `music`, streamed ambient beds from vanilla files (vanilla music tracks are not addressable by file); NICE loops
  // replace the names later under the same ids
  'fs_music.overworld': [S('sounds/note/harp', 0.8, 0.3)],
  'fs_music.nether': [S('sounds/ambient/nether/crimson_forest/mood1', 1, 0.5), S('sounds/ambient/nether/crimson_forest/mood2', 1, 0.5)],
  'fs_music.end': [S('sounds/block/beacon/ambient', 0.7, 0.5)],
  wild_expand: [S('sounds/block/respawn_anchor/charge1', 1.2, 0.8), S('sounds/block/respawn_anchor/charge2', 1.2, 0.8)],
  wild_stick: [S('sounds/step/chain1', 0.8, 0.8), S('sounds/step/chain2', 0.8, 0.8)],
  'wild_stick.layer': [S('sounds/block/amethyst/place1', 1, 0.7)],
  tumble: [S('sounds/random/fizz', 1.4, 0.4)],
  'tumble.layer': [S('sounds/fire/ignite', 1, 0.3)],
  mult_up: [S('sounds/note/pling', 1, 0.6)],
  'mult_up.layer': [S('sounds/block/campfire/crackle1', 1, 0.6), S('sounds/block/campfire/crackle2', 1, 0.6)],
  chest_open: [S('sounds/random/chestopen', 1.1, 0.8)],
  creeper_hiss: [S('sounds/random/fuse', 1, 0.8)],
  coin_land: [S('sounds/step/chain3', 1.6, 0.7), S('sounds/step/chain4', 1.6, 0.7)],
  'coin_land.layer': [S('sounds/random/orb', 0.7, 0.4)],
  respin_reset: [S('sounds/note/icechime', 1.5, 0.6)],
  wheel_tick: [S('sounds/random/wood_click', 1.4, 0.5)],
  wheel_up: [S('sounds/block/beacon/power1', 1.2, 0.8), S('sounds/block/beacon/power2', 1.2, 0.8)],
};

export function soundDefinitions() {
  const defs = {};
  for (const [id, sounds] of Object.entries(SLOT_SOUNDS)) {
    const music = id.startsWith('fs_music');
    defs[`burmaldaholic.slots.${id}`] = {
      category: music ? 'music' : 'player',
      sounds: sounds.map((s) => ({ name: s.name, ...(s.pitch !== 1 ? { pitch: s.pitch } : {}), ...(s.volume !== 1 ? { volume: s.volume } : {}), ...(music ? { stream: true } : {}) })),
    };
  }
  return { format_version: '1.20.20', sound_definitions: defs };
}
