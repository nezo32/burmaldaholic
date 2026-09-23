/**
 * Form helpers (UI.md §0.3): show with UserBusy retry, "is a casino form open" tracking (chaos
 * defers events while it is), ModalForm value reading that tolerates label slots, and the
 * shared amount-entry form (slider + exact amount text field) every game uses for bets.
 */
import { type Player, system } from '@minecraft/server';
import {
  type ActionFormData,
  type ActionFormResponse,
  FormCancelationReason,
  type MessageFormData,
  type MessageFormResponse,
  ModalFormData,
  type ModalFormResponse,
} from '@minecraft/server-ui';
import { parseAmount, sliderStep } from './logic/bet';
import { type Raw, chips, color, t } from './logic/rawtext';

const open = new Set<string>();

/** True while a core-shown form is on this player's screen (chaos defers events). */
export const isFormOpen = (player: Player): boolean => open.has(player.id);

type AnyForm = ActionFormData | ModalFormData | MessageFormData;
type ResponseOf<F> = F extends ActionFormData ? ActionFormResponse : F extends ModalFormData ? ModalFormResponse : MessageFormResponse;

const wait = (ticks: number) => new Promise<void>((r) => system.runTimeout(() => r(), ticks));

/**
 * Show a form. If the player is busy (chat or another UI open) retry every 10 ticks for up
 * to 5 s; returns undefined when it could never be shown (treat as "no action").
 */
export async function showForm<F extends AnyForm>(player: Player, form: F): Promise<ResponseOf<F> | undefined> {
  for (let attempt = 0; attempt < 10; attempt++) {
    if (!player.isValid) return undefined;
    open.add(player.id);
    let res: ResponseOf<F>;
    try {
      res = (await (form as ActionFormData).show(player)) as ResponseOf<F>;
    } catch {
      open.delete(player.id);
      return undefined;
    } finally {
      open.delete(player.id);
    }
    if (res.canceled && res.cancelationReason === FormCancelationReason.UserBusy) {
      await wait(10);
      continue;
    }
    return res;
  }
  return undefined;
}

/** Tracks which ModalFormData rows are interactive so values can be read by control index. */
export class ModalLayout {
  private total = 0;
  private readonly interactive: number[] = [];

  /** Call for every label/header/divider added. */
  passive(): void {
    this.total++;
  }
  /** Call for every toggle/slider/dropdown/textField added; returns its control index. */
  control(): number {
    this.interactive.push(this.total++);
    return this.interactive.length - 1;
  }
  /** Value of the i-th interactive control (works whether or not labels take array slots). */
  value(res: ModalFormResponse, i: number): string | number | boolean | undefined {
    const v = res.formValues;
    if (!v) return undefined;
    const pos = v.length === this.total ? this.interactive[i] : i;
    return pos === undefined ? undefined : v[pos];
  }
}

export interface AmountPrompt {
  title: Raw;
  /** lines shown above the controls (balance, limits...) */
  info?: Raw[];
  min: number;
  max: number;
  default?: number;
  /** slider label, default `gui.burmaldaholic.common.bet` */
  sliderLabel?: Raw;
  submit?: Raw;
  /** Return an error text to re-show the form with it, or undefined to accept. */
  validate?(amount: number): Raw | undefined;
}

/**
 * ModalForm with a slider (≤ 100 steps) and an "or type an exact amount" field. Re-shows with
 * a red error line until the amount validates. Returns undefined if the player closes it.
 */
export async function promptAmount(player: Player, p: AmountPrompt): Promise<number | undefined> {
  let error: Raw | undefined;
  let last = p.default ?? p.min;
  for (;;) {
    const layout = new ModalLayout();
    const form = new ModalFormData().title(p.title);
    if (error) {
      form.label(color('§c', error));
      layout.passive();
    }
    for (const line of p.info ?? []) {
      form.label(line);
      layout.passive();
    }
    const step = sliderStep(p.min, p.max);
    const sliderMax = Math.max(p.min, p.max);
    form.slider(p.sliderLabel ?? t('gui.burmaldaholic.common.bet'), p.min, sliderMax, {
      valueStep: step,
      defaultValue: Math.min(sliderMax, Math.max(p.min, last)),
    });
    const iSlider = layout.control();
    form.textField(t('gui.burmaldaholic.common.exact_amount'), t('gui.burmaldaholic.common.amount'));
    const iText = layout.control();
    form.submitButton(p.submit ?? t('gui.burmaldaholic.common.confirm'));
    const res = await showForm(player, form);
    if (!res || res.canceled) return undefined;
    const typed = String(layout.value(res, iText) ?? '').trim();
    let amount: number | undefined = Number(layout.value(res, iSlider));
    if (typed) amount = parseAmount(typed);
    if (amount === undefined || !Number.isFinite(amount)) {
      error = t('gui.burmaldaholic.error.invalid_amount');
      continue;
    }
    last = amount;
    error = amount < p.min ? t('gui.burmaldaholic.error.bet_too_low', chips(p.min)) : amount > p.max ? t('gui.burmaldaholic.error.table_max', chips(p.max)) : p.validate?.(amount);
    if (!error) return amount;
  }
}
