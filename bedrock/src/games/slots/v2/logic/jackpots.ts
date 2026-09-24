/** Progressive jackpot maths (SLOTS.md §5.2); twin of Java `Jackpots`. Integers: safe below 2^53. */
export const MINI = 1;
export const MINOR = 2;
export const MAJOR = 3;
export const GRAND = 4;

/** floor((seed + increment) × min(bet, ref) / ref) */
export const jackpotAward = (seed: number, increment: number, bet: number, ref: number): number => Math.floor(((seed + increment) * Math.min(bet, ref)) / ref);

/** increment × (1 − r), floored */
export const incrementAfter = (increment: number, bet: number, ref: number): number => Math.floor((increment * (ref - Math.min(bet, ref))) / ref);
