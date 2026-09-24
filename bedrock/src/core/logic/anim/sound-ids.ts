/**
 * Every casino sound id of the animation wave (twin of Java `core.sound.SoundCatalog`; the same list, in the
 * same order, is in the shared vector file). Bedrock id = `burmaldaholic.<id>`, defined in
 * `packs/<owner>/RP/sounds/sound_definitions.json` (MUST: vanilla sound paths; NICE: .ogg files, same ids).
 */
export type SoundSourceKind = 'player' | 'block' | 'ambient';

export interface SoundDef {
  readonly id: string;
  readonly owner: string;
  readonly source: SoundSourceKind;
  readonly exists: boolean;
  readonly spec: string;
}

const defs: SoundDef[] = [];
const add = (owner: string, source: SoundSourceKind, exists: boolean, spec: string, ...ids: string[]): void => {
  for (const id of ids) defs.push({ id, owner, source, exists, spec });
};

// global.md §2.7
add('core', 'player', true, 'global', 'chip_place');
add('core', 'player', false, 'global', 'chip_stack', 'chip_count', 'win_small', 'win_nice', 'win_big', 'win_mega', 'jackpot', 'push', 'ui_deny', 'toast', 'streak_up', 'streak_break', 'collector_arrive', 'heartbeat', 'coin_land', 'attract_chime');
add('core', 'player', true, 'global', 'win', 'lose', 'collector_knock', 'card_shuffle', 'slot_spin', 'wheel_tick', 'scratch');
add('vip', 'player', false, 'global', 'vip_tier_up');
add('chaos', 'player', true, 'global', 'golden_hour');
add('chaos', 'ambient', false, 'global', 'golden_hour_end', 'chaos_good', 'chaos_bad', 'chaos_teleport');
add('lastchance', 'player', true, 'global', 'last_chance');
// cards.md §7 / tables.md §0.8
add('core', 'player', true, 'cards', 'card_deal');
add('core', 'block', false, 'cards/tables', 'card_slide', 'card_flip', 'card_squeeze', 'card_gather', 'card_sting', 'chip_push', 'pot_win', 'table_knock', 'chip_sweep');
add('roulette', 'block', false, 'tables', 'roulette_ball_roll', 'roulette_ball_drop', 'roulette_ball_bounce', 'roulette_ball_settle', 'roulette_bell', 'roulette_dolly');
add('roulette', 'player', true, 'tables', 'roulette_spin');
add('core', 'block', false, 'tables', 'dice_throw', 'dice_bounce', 'dice_cup', 'dice_wall');
add('craps', 'block', false, 'tables', 'craps_puck');
// extras-pvp.md §10.3
add('extras', 'player', true, 'extras-pvp', 'coin_flip', 'plinko_peg', 'dice_roll');
add('extras', 'player', false, 'extras-pvp', 'plinko_bin');
add('core', 'player', false, 'extras-pvp', 'coin_whoosh', 'wheel_stop', 'burn');
add('pvp', 'player', true, 'extras-pvp', 'pvp_drumroll', 'pvp_victory');
// slots.md §8 / SLOTS.md §10.7
add(
  'slots',
  'player',
  false,
  'slots',
  ...[
    'spin_loop', 'reel_stop', 'scatter_land', 'bonus_land', 'anticipation', 'returned', 'win_small', 'win_nice', 'big_win', 'mega_win', 'epic_win', 'max_win',
    'rollup_tick', 'rollup_end', 'fs_intro', 'fs_outro', 'fs_music.overworld', 'fs_music.nether', 'fs_music.end', 'wild_expand', 'wild_stick', 'tumble',
    'mult_up', 'chest_open', 'creeper_hiss', 'coin_land', 'respin_reset', 'wheel_tick', 'wheel_up',
  ].map((s) => `slots.${s}`),
);

export const SOUND_DEFS: readonly SoundDef[] = defs;

/** `burmaldaholic.<id>` */
export const soundId = (id: string): string => `burmaldaholic.${id}`;
