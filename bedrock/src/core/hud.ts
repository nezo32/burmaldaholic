/**
 * HUD service (UI.md §1, Bedrock fallback): one shared actionbar line per player.
 *
 * 1. Messages: modules post `actionbar(player, '<module>.<channel>', raw, priority, ttl)`; the
 *    highest-priority live message is shown and refreshed every second.
 * 2. Status line: when no message was posted for 60 ticks, the status line
 *    `balance · streak · VIP · Golden Hour · <module segments>` is shown (refreshed every
 *    40 ticks). Modules add their own parts with `addSegment` (e.g. loan: "Loan: 1d 04:12").
 * Hidden when casino mode is off, `core.hud.enabled` is false or the player hid it.
 */
import { type Player, system, world } from '@minecraft/server';
import { HudQueue } from './logic/hud-queue';
import { type Segment, SegmentList } from './logic/hud-status';
import { type Raw, joinWith, lit } from './logic/rawtext';
import { createLogger } from './log';

export const HudPriority = { ambient: 0, game: 10, alert: 20, critical: 30 } as const;
export type HudSegment = Segment<Player, Raw>;

const HIDE_PROP = 'burmaldaholic:core.hud_hidden';
const log = createLogger('core.hud');

export class Hud {
  private readonly queues = new Map<string, HudQueue<Raw>>();
  private readonly lastPost = new Map<string, number>();
  private readonly lastStatus = new Map<string, number>();
  private readonly segments = new SegmentList<Player, Raw>();
  private enabled: () => boolean = () => true;

  private queue(player: Player): HudQueue<Raw> {
    let q = this.queues.get(player.id);
    if (!q) this.queues.set(player.id, (q = new HudQueue<Raw>()));
    return q;
  }

  /** Show `message` on the actionbar for ttlTicks unless something more important is up. */
  actionbar(player: Player, channel: string, message: Raw, priority: number = HudPriority.game, ttlTicks = 60): void {
    this.queue(player).post(channel, message, priority, system.currentTick, ttlTicks);
    this.lastPost.set(player.id, system.currentTick);
    this.render(player);
  }

  clear(player: Player, channel: string): void {
    this.queues.get(player.id)?.clear(channel);
  }

  title(player: Player, title: Raw, subtitle?: Raw, fadeIn = 5, stay = 40, fadeOut = 10): void {
    player.onScreenDisplay.setTitle(title, { subtitle, fadeInDuration: fadeIn, stayDuration: stay, fadeOutDuration: fadeOut });
  }

  /** Add (or replace by id) a status-line segment. Return undefined from render to hide it. */
  addSegment(seg: HudSegment): void {
    this.segments.add(seg);
  }

  removeSegment(id: string): void {
    this.segments.remove(id);
  }

  /** The status line as it would be shown now (also used by the Casino Menu). */
  statusLine(player: Player): Raw | undefined {
    const parts = this.segments.render(player, (id, e) => log.error(`segment ${id} failed`, e));
    return parts.length ? joinWith(lit(' §7·§r '), parts) : undefined;
  }

  isHiddenFor(player: Player): boolean {
    return player.getDynamicProperty(HIDE_PROP) === true;
  }

  setHiddenFor(player: Player, hidden: boolean): void {
    player.setDynamicProperty(HIDE_PROP, hidden ? true : undefined);
  }

  private render(player: Player): void {
    const e = this.queues.get(player.id)?.current(system.currentTick);
    if (e) player.onScreenDisplay.setActionBar(e.message);
  }

  private tickPlayer(p: Player, now: number): void {
    const e = this.queues.get(p.id)?.current(now);
    if (e) {
      p.onScreenDisplay.setActionBar(e.message);
      return;
    }
    if (!this.enabled() || this.isHiddenFor(p)) return;
    if (now - (this.lastPost.get(p.id) ?? -1000) < 60) return;
    if (now - (this.lastStatus.get(p.id) ?? -1000) < 40) return;
    const line = this.statusLine(p);
    if (!line) return;
    this.lastStatus.set(p.id, now);
    p.onScreenDisplay.setActionBar(line);
  }

  /** Started by core at world load. `enabled` = casino mode on && core.hud.enabled. */
  start(enabled: () => boolean): void {
    this.enabled = enabled;
    system.runInterval(() => {
      const now = system.currentTick;
      for (const p of world.getAllPlayers()) {
        try {
          this.tickPlayer(p, now);
        } catch (err) {
          log.error('hud tick failed', err);
        }
      }
    }, 20);
    world.afterEvents.playerLeave.subscribe((e) => {
      this.queues.delete(e.playerId);
      this.lastPost.delete(e.playerId);
      this.lastStatus.delete(e.playerId);
    });
  }
}
