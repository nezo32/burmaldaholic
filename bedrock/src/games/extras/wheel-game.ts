/**
 * Wheel of Fortune block (GAME_DESIGN §11.2, UI.md §9). Single bet 1…tier max × maxBetFraction
 * (pawn stakes allowed); the pointer lands on one of the Appendix B segments. ActionForm with
 * the segment legend → stake → spin (actionbar animation of segment names) → result.
 * The round is settled when drawn; the animation is cosmetic.
 */
import { ActionFormData } from '@minecraft/server-ui';
import { type Raw, type TableLimits, type TableSession, HudPriority, chips, color, join, lines, lit, mathRng, showForm, t } from '../../core';
import { resultForm } from './coin-flip';
import { type WheelCode, type WheelSetup, maxWheelMultiplier, segmentKey, spinFrames, spinWheel, wheelFromConfig, wheelLegend, wheelReturn, wheelRtp, SEGMENT_COLORS } from './logic';
import { animate, chooseStake, ctx, gameEnabled, resultLine, triggerChaos } from './shared';

export const WHEEL_GAME = 'wheel';
const TITLE = 'gui.burmaldaholic.extras.wheel.title';

export function wheelSetup(): WheelSetup {
  const c = ctx().config;
  return wheelFromConfig(c.json('extras.wheel.segments'), c.json('extras.wheel.multipliers'));
}

const segName = (code: WheelCode): Raw => color(SEGMENT_COLORS[code], t(segmentKey(code)));

function legend(w: WheelSetup): Raw {
  return lines(...wheelLegend(w).map((r) => t('gui.burmaldaholic.extras.wheel.legend', segName(r.code), r.multiplier, r.count)));
}

interface WheelData {
  last?: number;
  busy?: boolean;
  /** settled result not yet revealed (shown by onLeave if the session ends mid-animation) */
  pending?: { text: Raw; code: WheelCode };
}

export async function wheelFlow(s: TableSession, rejoined: boolean): Promise<void> {
  const data = s.data as WheelData;
  if (rejoined && data.busy) return;
  data.busy = true;
  const p = s.player;
  let error: Raw | undefined;
  try {
    for (;;) {
      if (!gameEnabled('extras.wheel.enabled')) return void p.sendMessage(t('gui.burmaldaholic.error.disabled'));
      const c = ctx();
      const w = wheelSetup();
      const limits: TableLimits = { tierMultiplier: c.config.num('extras.wheel.maxBetFraction') };
      const range = c.limits.range(p, limits);
      const intro = new ActionFormData()
        .title(t(TITLE))
        .body(lines(error ? color('§c', error) : undefined, legend(w), t('gui.burmaldaholic.common.balance', chips(c.economy.balance(p))), t('gui.burmaldaholic.common.limits', chips(range.min), chips(range.max))))
        .button(t('gui.burmaldaholic.common.spin'))
        .button(t('gui.burmaldaholic.common.close'));
      error = undefined;
      const res = await showForm(p, intro);
      if (!res || res.canceled || res.selection !== 0 || !s.isActive()) return;

      let again = true;
      while (again) {
        const choice = await chooseStake(p, { title: t(TITLE), limits, pawnAllowed: true, last: data.last, error });
        error = undefined;
        if (!choice || !s.isActive()) return;
        if (choice.amount) data.last = choice.amount;
        const worst = choice.stake.kind === 'chips' ? wheelReturn(choice.stake.amount, { multiplier: maxWheelMultiplier(w) }) : undefined;
        const r = c.wagers.place(p, { game: 'wheel', stake: choice.stake, limits, pawnAllowed: true, worstCase: worst, notify: false });
        if (!r.ok) {
          error = r.error;
          continue;
        }
        const { result } = c.odds.draw(p.id, wheelRtp(w), mathRng, () => spinWheel(mathRng, w), (x) => wheelReturn(r.ticket.value, x) < r.ticket.value);
        const ev = c.wagers.settle(r.ticket, p, wheelReturn(r.ticket.value, result));
        const text = t('gui.burmaldaholic.extras.wheel.result', segName(result.code), ev ? resultLine(ev.net) : lit(''));
        data.pending = { text, code: result.code };

        const n = w.segments.length;
        const frames = spinFrames(result.index, n).map((f) => {
          const at = (k: number) => w.segments[(((f.index + k) % n) + n) % n] as WheelCode;
          return { raw: join(lit('§8'), t(segmentKey(at(-1))), lit(' §7‹ '), segName(at(0)), lit(' §7› §8'), t(segmentKey(at(1)))), delay: f.delay };
        });
        ctx().hud.actionbar(p, 'extras.wheel', t('gui.burmaldaholic.extras.wheel.spinning'), HudPriority.game, 20);
        const done = await animate(s, 'wheel', frames);
        revealPending(s);
        if (!done) return;
        again = await resultForm(p, lines(text, t('gui.burmaldaholic.common.balance', chips(c.economy.balance(p)))), t(TITLE));
        if (!s.isActive()) return;
      }
    }
  } finally {
    data.busy = false;
    if (s.isActive()) s.leave();
  }
}

/** Chat line + Creeper side effect once the result is revealed (or the player walked away). */
function revealPending(s: TableSession): void {
  const data = s.data as WheelData;
  const pending = data.pending;
  data.pending = undefined;
  const p = s.player;
  if (!pending || !p.isValid) return;
  p.sendMessage(pending.text);
  if (pending.code === 'C') {
    p.sendMessage(color('§2', t('msg.burmaldaholic.extras.wheel.creeper')));
    triggerChaos(p, 'mob_wave', 'wheel');
  }
}

/** onLeave: a settled but unrevealed spin still gets its chat line. */
export function wheelLeave(s: TableSession): void {
  revealPending(s);
}
