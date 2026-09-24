/**
 * Live forms (docs/architecture/animation.md §2.8): one abstraction over the DDUI `CustomForm` + Observables
 * (server-ui 2.1.0) with a classic fallback, so games write their screen once.
 *
 * - `DduiLiveForm`: labels/buttons bound to Observables; `set()` writes only changed values (≤ 1 write per
 *   2 t per label is the caller's cadence via the scheduler).
 * - `ClassicLiveForm`: no live updates inside the form; live text goes to the action bar through the
 *   supplied `actionbar` callback (HUD channel), and `show()` renders a classic ActionForm between steps.
 *
 * Which one is used: `createLiveForm` tries DDUI when `preferDdui` (config such as `slots.bedrock.ddui`) is
 * true AND the runtime supports it (spike B-S0 confirms rate limits and glyph size); otherwise classic.
 * SKELETON: API final-shape; not used by any game yet.
 */
import type { Player } from '@minecraft/server';
import { ActionFormData, CustomForm, ObservableString, ObservableUIRawMessage } from '@minecraft/server-ui';
import { type Raw, lines, lit } from '../logic/rawtext';

export type LiveText = string | Raw;

export interface LiveForm {
  readonly kind: 'ddui' | 'classic';
  /** Declares a label (before `show`). */
  label(id: string, initial: LiveText): this;
  /** Declares a button (before `show`); `onClick` runs on the server thread. */
  button(id: string, initial: LiveText, onClick: () => void): this;
  /** Updates a label or button text (no-op when unchanged). */
  set(id: string, value: LiveText): void;
  show(): Promise<void>;
  close(): void;
  isShowing(): boolean;
}

const same = (a: LiveText | undefined, b: LiveText): boolean => (typeof a === 'string' || typeof b === 'string' ? a === b : JSON.stringify(a) === JSON.stringify(b));

class DduiLiveForm implements LiveForm {
  readonly kind = 'ddui' as const;
  private readonly form: CustomForm;
  private readonly strings = new Map<string, ObservableString>();
  private readonly raws = new Map<string, ObservableUIRawMessage>();
  private readonly values = new Map<string, LiveText>();

  constructor(player: Player, title: LiveText) {
    this.form = new CustomForm(player, title);
  }
  private observable(id: string, v: LiveText): ObservableString | ObservableUIRawMessage {
    this.values.set(id, v);
    if (typeof v === 'string') {
      const o = new ObservableString(v);
      this.strings.set(id, o);
      return o;
    }
    const o = new ObservableUIRawMessage(v);
    this.raws.set(id, o);
    return o;
  }
  label(id: string, initial: LiveText): this {
    this.form.label(this.observable(id, initial));
    return this;
  }
  button(id: string, initial: LiveText, onClick: () => void): this {
    this.form.button(this.observable(id, initial), onClick);
    return this;
  }
  set(id: string, value: LiveText): void {
    if (same(this.values.get(id), value)) return;
    this.values.set(id, value);
    if (typeof value === 'string') this.strings.get(id)?.setData(value);
    else this.raws.get(id)?.setData(value);
  }
  async show(): Promise<void> {
    await this.form.show();
  }
  close(): void {
    this.form.close();
  }
  isShowing(): boolean {
    return this.form.isShowing();
  }
}

class ClassicLiveForm implements LiveForm {
  readonly kind = 'classic' as const;
  private readonly order: Array<{ id: string; button?: () => void }> = [];
  private readonly values = new Map<string, LiveText>();
  private showing = false;

  constructor(
    private readonly player: Player,
    private readonly title: LiveText,
    private readonly actionbar: (id: string, text: LiveText) => void,
  ) {}
  label(id: string, initial: LiveText): this {
    this.order.push({ id });
    this.values.set(id, initial);
    return this;
  }
  button(id: string, initial: LiveText, onClick: () => void): this {
    this.order.push({ id, button: onClick });
    this.values.set(id, initial);
    return this;
  }
  set(id: string, value: LiveText): void {
    if (same(this.values.get(id), value)) return;
    this.values.set(id, value);
    if (!this.showing) this.actionbar(id, value);
  }
  async show(): Promise<void> {
    const f = new ActionFormData().title(this.title);
    const labels = this.order.filter((e) => !e.button).map((e) => this.values.get(e.id)!);
    if (labels.length > 0) f.body(lines(...labels.map((l) => (typeof l === 'string' ? lit(l) : l))));
    const buttons = this.order.filter((e) => e.button);
    for (const b of buttons) f.button(this.values.get(b.id)!);
    this.showing = true;
    try {
      const r = await f.show(this.player);
      if (!r.canceled && r.selection !== undefined) buttons[r.selection]?.button?.();
    } finally {
      this.showing = false;
    }
  }
  close(): void {
    this.showing = false;
  }
  isShowing(): boolean {
    return this.showing;
  }
}

export interface LiveFormOptions {
  preferDdui: boolean;
  /** Where live text goes when the classic form is not showing (HUD channel of the game). */
  actionbar: (id: string, text: LiveText) => void;
}

/** DDUI when preferred and constructible, else classic. */
export function createLiveForm(player: Player, title: LiveText, opts: LiveFormOptions): LiveForm {
  if (opts.preferDdui) {
    try {
      return new DduiLiveForm(player, title);
    } catch {
      // DDUI unavailable on this runtime: classic fallback (always kept, SLOTS.md §10.6)
    }
  }
  return new ClassicLiveForm(player, title, opts.actionbar);
}
