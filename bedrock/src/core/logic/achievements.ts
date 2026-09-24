/**
 * Achievements (GAME_DESIGN §19). PURE. Java has real advancements; Bedrock has no custom
 * advancements, so core keeps per-player unlocks (dynamic property), shows a toast and lists
 * them on the Casino Menu "Achievements" page. Same ids and lang keys on both editions:
 * `advancement.burmaldaholic.<id>.title` / `.description`.
 */
export type AchievementFrame = 'task' | 'goal' | 'challenge';

export interface AchievementDef {
  readonly id: string;
  readonly parent?: string;
  readonly frame: AchievementFrame;
}

/** Spec order (also the menu order). */
export const ACHIEVEMENTS: readonly AchievementDef[] = [
  { id: 'root', frame: 'task' },
  { id: 'first_bet', parent: 'root', frame: 'task' },
  { id: 'beginners_luck', parent: 'first_bet', frame: 'task' },
  { id: 'natural', parent: 'beginners_luck', frame: 'task' },
  { id: 'split_personality', parent: 'natural', frame: 'goal' },
  { id: 'royal_flush', parent: 'beginners_luck', frame: 'challenge' },
  { id: 'shark_hunter', parent: 'beginners_luck', frame: 'goal' },
  { id: 'three_sevens', parent: 'beginners_luck', frame: 'goal' },
  { id: 'jackpot', parent: 'three_sevens', frame: 'challenge' },
  { id: 'zero_hero', parent: 'beginners_luck', frame: 'goal' },
  { id: 'hot_shooter', parent: 'beginners_luck', frame: 'goal' },
  { id: 'plinko_edge', parent: 'beginners_luck', frame: 'challenge' },
  { id: 'scratch_top', parent: 'first_bet', frame: 'challenge' },
  { id: 'on_fire', parent: 'beginners_luck', frame: 'goal' },
  { id: 'black_cat', parent: 'first_bet', frame: 'goal' },
  { id: 'loan_taken', parent: 'root', frame: 'task' },
  { id: 'knock_knock', parent: 'loan_taken', frame: 'task' },
  { id: 'hostile_takeover', parent: 'knock_knock', frame: 'goal' },
  { id: 'clean_slate', parent: 'loan_taken', frame: 'task' },
  { id: 'not_today', parent: 'root', frame: 'goal' },
  { id: 'scarred', parent: 'not_today', frame: 'challenge' },
  { id: 'heart_on_the_line', parent: 'first_bet', frame: 'task' },
  { id: 'devils_deal', parent: 'heart_on_the_line', frame: 'challenge' },
  { id: 'golden_hour', parent: 'root', frame: 'task' },
  { id: 'beam_me_up', parent: 'root', frame: 'task' },
  { id: 'vip_silver', parent: 'root', frame: 'task' },
  { id: 'vip_gold', parent: 'vip_silver', frame: 'task' },
  { id: 'vip_platinum', parent: 'vip_gold', frame: 'goal' },
  { id: 'vip_diamond', parent: 'vip_platinum', frame: 'goal' },
  { id: 'vip_netherite', parent: 'vip_diamond', frame: 'challenge' },
  { id: 'the_house', parent: 'root', frame: 'goal' },
  { id: 'house_always_wins', parent: 'the_house', frame: 'challenge' },
  { id: 'bankrupt', parent: 'the_house', frame: 'task' },
  { id: 'piglin_parlor', parent: 'root', frame: 'task' },
  { id: 'high_roller', parent: 'piglin_parlor', frame: 'goal' },
  { id: 'baccarat_natural', parent: 'beginners_luck', frame: 'task' },
  { id: 'tie_streak', parent: 'baccarat_natural', frame: 'goal' },
  { id: 'uth_four_x', parent: 'beginners_luck', frame: 'task' },
  { id: 'banco', parent: 'baccarat_natural', frame: 'goal' },
  { id: 'bank_holder', parent: 'banco', frame: 'challenge' },
  { id: 'uth_house_seat', parent: 'uth_four_x', frame: 'goal' },
  { id: 'uth_royal', parent: 'uth_four_x', frame: 'challenge' },
];

export type AchievementId = string;

const IDS = new Set(ACHIEVEMENTS.map((a) => a.id));
export const isAchievement = (id: string): boolean => IDS.has(id);

export const achievementTitleKey = (id: string): string => `advancement.burmaldaholic.${id}.title`;
export const achievementDescKey = (id: string): string => `advancement.burmaldaholic.${id}.description`;

/** Streak thresholds (§19 on_fire / black_cat). */
export const STREAK_ACHIEVEMENT = 10;
/** Owned casino net profit for house_always_wins. */
export const HOUSE_PROFIT_ACHIEVEMENT = 10_000;

/**
 * Add `id` to a stored unlock list. Returns the new list and whether it was newly unlocked.
 * Unknown ids are ignored; duplicates never re-toast.
 */
export function unlockInList(list: readonly string[], id: string): { list: string[]; added: boolean } {
  if (!isAchievement(id) || list.includes(id)) return { list: [...list], added: false };
  return { list: [...list, id], added: true };
}

/** Achievements unlocked by a settled wager on its own (core-observable conditions). */
export function wagerAchievements(e: { staked: number; net: number; stakeKind: string; goldenHour: boolean }): string[] {
  const out: string[] = [];
  if (e.staked < 1) return out;
  out.push('first_bet');
  if (e.net > 0) {
    out.push('beginners_luck');
    if (e.stakeKind === 'hearts') out.push('heart_on_the_line');
    if (e.stakeKind === 'soul') out.push('devils_deal');
    if (e.goldenHour) out.push('golden_hour');
  }
  return out;
}

/** Streak achievements for a new streak value. */
export function streakAchievements(streak: number): string[] {
  if (streak >= STREAK_ACHIEVEMENT) return ['on_fire'];
  if (streak <= -STREAK_ACHIEVEMENT) return ['black_cat'];
  return [];
}
