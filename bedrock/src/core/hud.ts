/**
 * HUD service: actionbar arbitration between modules + title helpers.
 * Channels are free-form strings; use `<module>.<name>` (e.g. 'slots.spin').
 */
import { type Player, system, world } from '@minecraft/server';
import { HudQueue } from './logic/hud-queue';
import type { Raw } from './logic/rawtext';

export const HudPriority = { ambient: 0, game: 10, alert: 20, critical: 30 } as const;

export class Hud {
  private readonly queues = new Map<string, HudQueue<Raw>>();

  private queue(player: Player): HudQueue<Raw> {
    let q = this.queues.get(player.id);
    if (!q) this.queues.set(player.id, (q = new HudQueue<Raw>()));
    return q;
  }

  /** Show `message` on the actionbar for ttlTicks unless something more important is up. */
  actionbar(player: Player, channel: string, message: Raw, priority: number = HudPriority.game, ttlTicks = 60): void {
    this.queue(player).post(channel, message, priority, system.currentTick, ttlTicks);
    this.render(player);
  }

  clear(player: Player, channel: string): void {
    this.queues.get(player.id)?.clear(channel);
  }

  title(player: Player, title: Raw, subtitle?: Raw, fadeIn = 5, stay = 40, fadeOut = 10): void {
    player.onScreenDisplay.setTitle(title, { subtitle, fadeInDuration: fadeIn, stayDuration: stay, fadeOutDuration: fadeOut });
  }

  private render(player: Player): void {
    const e = this.queues.get(player.id)?.current(system.currentTick);
    if (e) player.onScreenDisplay.setActionBar(e.message);
  }

  /** Re-send the current line periodically (vanilla actionbar fades after ~3s). */
  start(): void {
    system.runInterval(() => {
      for (const p of world.getAllPlayers()) this.render(p);
    }, 20);
    world.afterEvents.playerLeave.subscribe((e) => this.queues.delete(e.playerId));
  }
}
