/**
 * Golden Hour state (GAME_DESIGN §13.3). The chaos module decides when it starts and pays the
 * bonus; core only keeps the shared server-wide state (persisted, world time) so the HUD,
 * games and other modules can read it.
 */
import { worldJson, worldTick } from './store';

const PROP = 'burmaldaholic:core.golden_hour';

export class GoldenHour {
  private until = -1;
  private loaded = false;

  private load(): void {
    if (this.loaded) return;
    this.loaded = true;
    this.until = worldJson.read<{ until?: number }>(PROP, {}).until ?? -1;
  }

  /** Start (or extend) Golden Hour for `durationTicks` of world time. */
  start(durationTicks: number): void {
    this.load();
    this.until = worldTick() + Math.max(0, durationTicks);
    worldJson.write(PROP, { until: this.until });
  }

  stop(): void {
    this.until = -1;
    this.loaded = true;
    worldJson.write(PROP, undefined);
  }

  isActive(): boolean {
    return this.remainingTicks() > 0;
  }

  remainingTicks(): number {
    this.load();
    return Math.max(0, this.until - worldTick());
  }
}
