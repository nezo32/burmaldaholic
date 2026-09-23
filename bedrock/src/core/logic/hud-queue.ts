/**
 * Actionbar arbitration. PURE. Only one actionbar line is visible per player, so modules
 * post messages on named channels with a priority and time-to-live; the HUD service shows
 * the highest-priority live entry (latest wins on ties).
 */
export interface HudEntry<M> {
  channel: string;
  message: M;
  priority: number;
  expiresAt: number;
  seq: number;
}

export class HudQueue<M> {
  private readonly entries = new Map<string, HudEntry<M>>();
  private seq = 0;

  post(channel: string, message: M, priority: number, now: number, ttl: number): void {
    this.entries.set(channel, { channel, message, priority, expiresAt: now + ttl, seq: this.seq++ });
  }

  clear(channel: string): void {
    this.entries.delete(channel);
  }

  /** Drop expired entries and return the one to display, if any. */
  current(now: number): HudEntry<M> | undefined {
    let best: HudEntry<M> | undefined;
    for (const [k, e] of this.entries) {
      if (e.expiresAt <= now) {
        this.entries.delete(k);
        continue;
      }
      if (!best || e.priority > best.priority || (e.priority === best.priority && e.seq > best.seq)) best = e;
    }
    return best;
  }
}
