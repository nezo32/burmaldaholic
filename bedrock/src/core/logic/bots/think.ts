/** Bot think delays (BOTS.md §7.3). PURE; draws from the BOT rng. */
import { type Rng, randInt } from '../rng';
import type { BotProfile, BotSpeed } from './types';

export interface ThinkConfig {
  /** `bots.think.minTicks` (poker: `poker.botThinkMinTicks`) */
  minTicks: number;
  maxTicks: number;
  /** `bots.think.fastFactor` */
  fastFactor: number;
  /** `bots.think.tankTicks` */
  tankTicks: number;
}

/** `big` = HARD poker decision with to-call > 25 % of stack; `humanTimer` = the game's human timer (0 = none). */
export function thinkTicks(rng: Rng, c: ThinkConfig, bot: BotProfile, speed: BotSpeed, big: boolean, humanTimer: number): number {
  if (speed === 'INSTANT') return 0;
  let t = randInt(rng, Math.min(c.minTicks, c.maxTicks), Math.max(c.minTicks, c.maxTicks));
  if (bot.level === 'HARD' && big) t += c.tankTicks + randInt(rng, 0, 40);
  if (bot.level === 'EASY' && rng.next() < 0.1) t += 20;
  if (speed === 'FAST') t = Math.round(t * c.fastFactor);
  if (humanTimer > 0) t = Math.min(t, Math.floor(humanTimer / 2));
  return Math.max(0, t);
}
