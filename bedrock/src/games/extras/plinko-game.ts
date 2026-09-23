/**
 * Plinko machine block (GAME_DESIGN §11.4, UI.md §9). ModalForm (risk dropdown + amount) →
 * actionbar path animation "◀ ▶ ▶ ◀ …" (12 steps × 4 ticks, the exact drawn path) → result.
 * Chips only; bet 1…tier max × maxBetFraction. Settled when drawn; animation is cosmetic.
 */
import { ModalFormData } from '@minecraft/server-ui';
import {
  type Raw,
  type TableLimits,
  type TableSession,
  ModalLayout,
  chips,
  color,
  formatMultiplier,
  join,
  lines,
  lit,
  mathRng,
  parseAmount,
  showForm,
  sliderStep,
  t,
} from '../../core';
import { resultForm } from './coin-flip';
import { type PlinkoRisk, PLINKO_RISKS, PLINKO_ROWS, dropBall, maxPlinkoMultiplier, plinkoReturn, plinkoRtp, plinkoTable } from './logic';
import { animate, betInfo, ctx, gameEnabled, resultLine } from './shared';

export const PLINKO_GAME = 'plinko';
const TITLE = 'gui.burmaldaholic.extras.plinko.title';

export function plinkoTableFor(risk: PlinkoRisk): readonly number[] {
  return plinkoTable(ctx().config.json(`extras.plinko.${risk}`), risk);
}

const binsLine = (table: readonly number[]): Raw => t('gui.burmaldaholic.extras.plinko.bins', lit(table.map((m) => formatMultiplier(m)).join(' ')));

interface PlinkoData {
  last?: number;
  risk?: number;
  busy?: boolean;
  pending?: Raw;
}

export async function plinkoFlow(s: TableSession, rejoined: boolean): Promise<void> {
  const data = s.data as PlinkoData;
  if (rejoined && data.busy) return;
  data.busy = true;
  const p = s.player;
  let error: Raw | undefined;
  try {
    for (;;) {
      if (!gameEnabled('extras.plinko.enabled')) return void p.sendMessage(t('gui.burmaldaholic.error.disabled'));
      const c = ctx();
      const limits: TableLimits = { tierMultiplier: c.config.num('extras.plinko.maxBetFraction') };
      const range = c.limits.range(p, limits);
      const max = Math.max(range.min, range.max);

      const layout = new ModalLayout();
      const form = new ModalFormData().title(t(TITLE));
      if (error) {
        form.label(color('§c', error));
        layout.passive();
      }
      for (const line of betInfo(p, range)) {
        form.label(line);
        layout.passive();
      }
      form.dropdown(
        t('gui.burmaldaholic.extras.plinko.risk'),
        PLINKO_RISKS.map((r) => t(`gui.burmaldaholic.extras.plinko.risk.${r}`)),
        { defaultValueIndex: data.risk ?? 0 },
      );
      const iRisk = layout.control();
      form.slider(t('gui.burmaldaholic.common.bet'), range.min, max, { valueStep: sliderStep(range.min, max), defaultValue: Math.min(max, Math.max(range.min, data.last ?? range.min)) });
      const iSlider = layout.control();
      form.textField(t('gui.burmaldaholic.common.exact_amount'), t('gui.burmaldaholic.common.amount'));
      const iText = layout.control();
      form.submitButton(t('gui.burmaldaholic.extras.plinko.drop'));
      error = undefined;
      const res = await showForm(p, form);
      if (!res || res.canceled || !s.isActive()) return;

      const riskIndex = Number(layout.value(res, iRisk)) || 0;
      const risk = PLINKO_RISKS[riskIndex] ?? 'low';
      const typed = String(layout.value(res, iText) ?? '').trim();
      const amount = typed ? parseAmount(typed) : Number(layout.value(res, iSlider));
      if (amount === undefined || !Number.isSafeInteger(amount)) {
        error = t('gui.burmaldaholic.error.invalid_amount');
        continue;
      }
      data.risk = riskIndex;
      data.last = amount;

      let again = true;
      while (again) {
        const table = plinkoTableFor(risk);
        const r = c.wagers.place(p, { game: 'plinko', stake: { kind: 'chips', amount }, limits, worstCase: plinkoReturn(amount, { multiplier: maxPlinkoMultiplier(table) }), houseEdge: Math.max(0, 1 - plinkoRtp(table)), notify: false });
        if (!r.ok) {
          error = r.error;
          break;
        }
        const { result } = c.odds.draw(p.id, plinkoRtp(table), mathRng, () => dropBall(mathRng, table), (x) => plinkoReturn(amount, x) < amount);
        const ev = c.wagers.settle(r.ticket, p, plinkoReturn(amount, result));
        if (risk === 'high' && (result.bin === 0 || result.bin === PLINKO_ROWS)) c.achievements.unlock(p, 'plinko_edge');
        const text = t('gui.burmaldaholic.extras.plinko.result', result.multiplier, ev ? resultLine(ev.net) : lit(''));
        data.pending = text;

        const arrows: string[] = [];
        const frames = [
          { raw: t('gui.burmaldaholic.extras.plinko.dropping'), delay: 8 },
          ...result.path.map((right) => {
            arrows.push(right ? '▶' : '◀');
            return { raw: join(lit('§e'), lit(arrows.join(' '))), delay: 4 };
          }),
        ];
        const done = await animate(s, 'plinko', frames);
        if (data.pending) {
          data.pending = undefined;
          if (p.isValid) p.sendMessage(text);
        }
        if (!done) return;
        again = await resultForm(p, lines(text, binsLine(table), t('gui.burmaldaholic.common.balance', chips(c.economy.balance(p)))), t(TITLE));
        if (!s.isActive()) return;
      }
      if (!error) return;
    }
  } finally {
    data.busy = false;
    if (s.isActive()) s.leave();
  }
}

export function plinkoLeave(s: TableSession): void {
  const data = s.data as PlinkoData;
  if (data.pending && s.player.isValid) s.player.sendMessage(data.pending);
  data.pending = undefined;
}
