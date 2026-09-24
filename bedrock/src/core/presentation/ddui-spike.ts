/**
 * Spike B-S0 in-game harness (docs/architecture/animation.md §11). Operators run
 * `/scriptevent burmaldaholic:fx spike` and look; the script side logs what it can measure itself (writes issued,
 * frame cost, close reason, whether DDUI failed and fell back). What only the client can show — coalescing/flicker
 * at 2 t, glyph size in a label vs a header, `§` tinting of glyphs, title glyph scale — is read off the screen and
 * recorded in the report's checklist.
 *
 * The form: title with glyphs; a header and a label each holding 3 rows × 5 slot glyphs that scroll one strip row
 * every 2 t for 10 s; a tint row (`§f §8 §e §c` before the same glyph); a counter label; a Close button whose
 * `disabled` flag toggles every second. After it closes, a 6-frame title flipbook with glyphs (V6).
 */
import type { Player } from '@minecraft/server';
import { lit, t } from '../logic/rawtext';
import { LOCAL, Timeline } from '../logic/anim/timeline';
import type { FxService } from './fx';
import { createLiveForm } from './live-form';
import { playTimeline } from './scheduler';

/** Overworld slot glyph plane E200–E20A (SLOTS.md §2) — the densest real use of glyphs in a label. */
const STRIP = Array.from({ length: 11 }, (_, i) => String.fromCharCode(0xe200 + i));
const glyphRows = (offset: number): string =>
  [0, 1, 2].map((r) => [0, 1, 2, 3, 4].map((c) => STRIP[(offset + r + c * 3) % STRIP.length]!).join('  ')).join('\n');

/** Component ids (constants keep the id strings out of the UI-call string check). */
const ID = { rowsH: 'rowsH', rowsL: 'rowsL', tint: 'tint', n: 'n', close: 'close' } as const;
const titleGlyphs = (): string => [STRIP[0], STRIP[5], STRIP[10]].join(' ');
const tintRow = (): string => ['§f', '§8', '§e', '§c'].map((c) => c + STRIP[1]).join(' ') + '§r';
const triple = (i: number): string => STRIP[i]!.repeat(3);

export interface SpikeReport {
  kind: 'ddui' | 'classic';
  frames: number;
  writes: number;
  maxFrameMs: number;
  closeReason: string;
}

/** Runs the harness for `p`; resolves with the script-side measurements. */
export async function runDduiSpike(p: Player, fx: FxService, log: (msg: string) => void): Promise<SpikeReport> {
  const form = createLiveForm(p, lit(titleGlyphs()), { preferDdui: true, actionbar: () => {} });
  form
    .header(ID.rowsH, lit(glyphRows(0)))
    .label(ID.rowsL, lit(glyphRows(0)))
    .label(ID.tint, lit(tintRow()))
    .label(ID.n, lit(0))
    .button(ID.close, t('gui.burmaldaholic.common.close'), () => form.close());
  const report: SpikeReport = { kind: form.kind, frames: 0, writes: 0, maxFrameMs: 0, closeReason: '' };
  const tl = Timeline.builder('fx.spike', 0).clock(LOCAL).add(0, 10_000, 'fx.spike', -1).build();
  let lastOffset = -1;
  const session = playTimeline(
    tl,
    {
      frame: (tMs) => {
        const started = Date.now();
        const offset = Math.floor(tMs / 100);
        if (offset !== lastOffset) {
          lastOffset = offset;
          form.set(ID.rowsH, lit(glyphRows(offset)));
          form.set(ID.rowsL, lit(glyphRows(offset)));
          form.set(ID.n, lit(offset));
          form.setDisabled(ID.close, Math.floor(tMs / 1000) % 2 === 1);
          report.frames++;
        }
        report.maxFrameMs = Math.max(report.maxFrameMs, Date.now() - started);
      },
      end: () => form.close(),
    },
    { alive: () => p.isValid },
  );
  report.closeReason = await form.show();
  session.stop();
  report.writes = form.writes;
  log(`B-S0 spike: ${JSON.stringify(report)}`);
  if (p.isValid) await fx.titleFlipbook(p, [0, 2, 4, 6, 8, 10].map((i) => lit(triple(i))), { frameTicks: 3, holdTicks: 20 });
  return report;
}
