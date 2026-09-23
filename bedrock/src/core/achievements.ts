/**
 * Achievements service (GAME_DESIGN §19, Bedrock edition): persistent per-player unlocks
 * (player property `burmaldaholic:core.achievements`), a toast (chat + action bar + sound) and
 * the Casino Menu "Achievements" page. Modules call `ctx.achievements.unlock(player, id)`;
 * core itself unlocks the wager/streak ones and wires the cross-module hooks
 * (core/achievement-hooks.ts). Unlocks for offline players are queued and applied on join.
 */
import type { Player } from '@minecraft/server';
import { ActionFormData } from '@minecraft/server-ui';
import { showForm } from './forms';
import { HudPriority, type Hud } from './hud';
import { ACHIEVEMENTS, achievementDescKey, achievementTitleKey, isAchievement, unlockInList } from './logic/achievements';
import { type Raw, color, join, lines, lit, t } from './logic/rawtext';
import { createLogger } from './log';
import { onlinePlayer } from './offline';
import { readJson, writeJson } from './store';

const log = createLogger('core.achievements');
const PROP = 'burmaldaholic:core.achievements';
const FRAME_COLOR = { task: '§a', goal: '§b', challenge: '§d' } as const;

export type AchievementListener = (player: Player, id: string) => void;

export class Achievements {
  private readonly listeners: AchievementListener[] = [];
  private queue: ((playerId: string, id: string) => void) | undefined;

  constructor(
    private readonly hud: Hud,
    private readonly enabled: () => boolean,
  ) {}

  /** Core wiring: where unlocks for offline players go (wager service offline mailbox). */
  setOfflineQueue(fn: (playerId: string, id: string) => void): void {
    this.queue = fn;
  }

  onUnlock(l: AchievementListener): void {
    this.listeners.push(l);
  }

  list(player: Player): string[] {
    const v = readJson<unknown>(player, PROP, []);
    return Array.isArray(v) ? v.filter((x): x is string => typeof x === 'string' && isAchievement(x)) : [];
  }

  has(player: Player, id: string): boolean {
    return this.list(player).includes(id);
  }

  /**
   * Unlock `id` for a player (Player or player id; offline ids are queued). Returns true when
   * newly unlocked now. `silent` skips the toast (migrations).
   */
  unlock(who: Player | string, id: string, opts: { silent?: boolean } = {}): boolean {
    if (!isAchievement(id)) {
      log.warn(`unknown achievement ${id}`);
      return false;
    }
    const pid = typeof who === 'string' ? who : who.isValid ? who.id : safeId(who);
    const player = typeof who !== 'string' && who.isValid ? who : pid ? onlinePlayer(pid) : undefined;
    if (!player) {
      if (pid) this.queue?.(pid, id);
      return false;
    }
    if (!this.enabled()) return false;
    const r = unlockInList(this.list(player), id);
    if (!r.added) return false;
    writeJson(player, PROP, r.list);
    if (!opts.silent) this.toast(player, id);
    for (const l of this.listeners) {
      try {
        l(player, id);
      } catch (e) {
        log.error('unlock listener failed', e);
      }
    }
    return true;
  }

  private toast(player: Player, id: string): void {
    const msg = t('gui.burmaldaholic.achievements.unlocked', color('§e', t(achievementTitleKey(id))));
    player.sendMessage(msg);
    this.hud.actionbar(player, 'core.achievement', msg, HudPriority.alert, 80);
    try {
      player.playSound('random.toast', { volume: 0.8, pitch: 1 });
    } catch {
      /* sound missing: ignore */
    }
  }

  /** Casino Menu page: progress + every achievement (unlocked in frame color, locked gray). */
  async open(player: Player): Promise<void> {
    const have = new Set(this.list(player));
    const rows: Raw[] = [t('gui.burmaldaholic.achievements.progress', have.size, ACHIEVEMENTS.length), lit('')];
    for (const a of ACHIEVEMENTS) {
      const got = have.has(a.id);
      const head = got ? color(FRAME_COLOR[a.frame], join(lit('✔ '), t(achievementTitleKey(a.id)))) : color('§8', join(lit('✘ '), t(achievementTitleKey(a.id))));
      rows.push(head, color(got ? '§7' : '§8', join(lit('   '), t(achievementDescKey(a.id)))));
    }
    const form = new ActionFormData().title(t('gui.burmaldaholic.menu.achievements')).body(lines(...rows)).button(t('gui.burmaldaholic.common.close'));
    await showForm(player, form);
  }
}

/** Id of a possibly-invalid Player object (undefined when the engine refuses to read it). */
function safeId(p: Player): string | undefined {
  try {
    return p.id;
  } catch {
    return undefined;
  }
}
