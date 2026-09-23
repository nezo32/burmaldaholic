/**
 * HUD status line composition. PURE.
 * Modules register segments (balance, streak, VIP, Golden Hour, loan...) with an order; the
 * HUD asks each for its current text and joins the visible ones with ' · '.
 */
export interface Segment<P, M> {
  id: string;
  /** lower = further left. Core uses 0 (balance), 10 (streak), 20 (vip), 30 (golden hour). */
  order: number;
  render(player: P): M | undefined;
}

export class SegmentList<P, M> {
  private segs: Segment<P, M>[] = [];

  /** Add or replace (same id) a segment. */
  add(seg: Segment<P, M>): void {
    this.segs = this.segs.filter((s) => s.id !== seg.id);
    this.segs.push(seg);
    this.segs.sort((a, b) => a.order - b.order || (a.id < b.id ? -1 : 1));
  }

  remove(id: string): void {
    this.segs = this.segs.filter((s) => s.id !== id);
  }

  /** Visible parts in order; a throwing segment is skipped (reported through `onError`). */
  render(player: P, onError?: (id: string, e: unknown) => void): M[] {
    const out: M[] = [];
    for (const s of this.segs) {
      try {
        const m = s.render(player);
        if (m !== undefined) out.push(m);
      } catch (e) {
        onError?.(s.id, e);
      }
    }
    return out;
  }

  ids(): string[] {
    return this.segs.map((s) => s.id);
  }
}
