/**
 * Live forms (docs/architecture/animation.md §2.8, spike B-S0 report §11): one abstraction over the DDUI
 * `CustomForm` + Observables (server-ui 2.1.0, stable) with a classic fallback, so games write their screen once.
 *
 * - `DduiLiveForm`: headers, labels and buttons bound to Observables; `set()` coalesces writes to at most one
 *   `setData` per component per `minWriteTicks` (default 2: the budget of global.md §2.9 is enforced HERE, whatever
 *   the caller's cadence); `setDisabled` / `setVisible` bind `ButtonOptions` / `TextOptions` Observables, so the
 *   Spin ↔ Stop swap and a disabled bet row need no reopen.
 * - `ClassicLiveForm`: no live updates inside the form; live text goes to the action bar through the supplied
 *   `actionbar` callback (the game's HUD channel), and `show()` renders a classic ActionForm between steps
 *   (hidden and disabled buttons are left out).
 *
 * Choice (spike decision, §11.3): DDUI when `preferDdui` (config such as `slots.bedrock.ddui`, default true) AND the
 * runtime has not failed DDUI before; classic otherwise. Failure detection is automatic: a constructor throw, a
 * `show()` rejection or a `setData` throw marks DDUI broken for the rest of the world session (`dduiHealthy()`), and
 * the caller's `show()` result says `fallback` so it can reopen the same screen as classic without losing state.
 */
import type { Player } from '@minecraft/server';
import { system } from '@minecraft/server';
import { ActionFormData, CustomForm, DataDrivenScreenClosedReason, FormCancelationReason, ObservableBoolean, ObservableUIRawMessage } from '@minecraft/server-ui';
import { type Raw, lines, lit } from '../logic/rawtext';

export type LiveText = string | Raw;

/** How a `show()` ended. `fallback`: DDUI failed, reopen with a classic form. `busy`: another UI was open. */
export type LiveCloseReason = 'closed' | 'server' | 'busy' | 'fallback' | 'selected';

export interface LiveButtonOptions {
  disabled?: boolean;
  visible?: boolean;
}

export interface LiveForm {
  readonly kind: 'ddui' | 'classic';
  /** Declares a header line (larger text; before `show`). */
  header(id: string, initial: LiveText): this;
  /** Declares a label (before `show`). */
  label(id: string, initial: LiveText): this;
  /** Declares a button (before `show`); `onClick` runs on the server thread. */
  button(id: string, initial: LiveText, onClick: () => void, opts?: LiveButtonOptions): this;
  /** Updates a header, label or button text (no-op when unchanged; coalesced to ≤ 1 write / 2 t). */
  set(id: string, value: LiveText): void;
  /** Greys out a button (DDUI) / leaves it out (classic). */
  setDisabled(id: string, disabled: boolean): void;
  /** Hides a component. */
  setVisible(id: string, visible: boolean): void;
  show(): Promise<LiveCloseReason>;
  close(): void;
  isShowing(): boolean;
  /** Observable writes made so far (budget accounting / tests). */
  readonly writes: number;
}

const same = (a: LiveText | undefined, b: LiveText): boolean => (typeof a === 'string' || typeof b === 'string' ? a === b : JSON.stringify(a) === JSON.stringify(b));

let dduiBroken = false;
/** False once DDUI failed in this world session (every later live form is classic). */
export const dduiHealthy = (): boolean => !dduiBroken;
/** Test hook / admin reset. */
export function resetDduiHealth(): void {
  dduiBroken = false;
}

const toRaw = (v: LiveText): Raw => (typeof v === 'string' ? lit(v) : v);

class DduiLiveForm implements LiveForm {
  readonly kind = 'ddui' as const;
  private readonly form: CustomForm;
  private readonly obs = new Map<string, ObservableUIRawMessage>();
  private readonly flags = new Map<string, { disabled?: ObservableBoolean; visible: ObservableBoolean }>();
  private readonly values = new Map<string, LiveText>();
  private readonly pending = new Map<string, LiveText>();
  private readonly lastWrite = new Map<string, number>();
  private flushId: number | undefined;
  private showing = false;
  writes = 0;

  constructor(
    player: Player,
    title: LiveText,
    private readonly minWriteTicks: number,
  ) {
    this.form = new CustomForm(player, toRaw(title));
  }
  private observable(id: string, v: LiveText): ObservableUIRawMessage {
    this.values.set(id, v);
    const o = new ObservableUIRawMessage(toRaw(v));
    this.obs.set(id, o);
    return o;
  }
  private visibility(id: string, visible = true): ObservableBoolean {
    const v = new ObservableBoolean(visible);
    this.flags.set(id, { ...this.flags.get(id), visible: v });
    return v;
  }
  header(id: string, initial: LiveText): this {
    this.form.header(this.observable(id, initial), { visible: this.visibility(id) });
    return this;
  }
  label(id: string, initial: LiveText): this {
    this.form.label(this.observable(id, initial), { visible: this.visibility(id) });
    return this;
  }
  button(id: string, initial: LiveText, onClick: () => void, opts: LiveButtonOptions = {}): this {
    const disabled = new ObservableBoolean(opts.disabled ?? false);
    const visible = this.visibility(id, opts.visible ?? true);
    this.flags.set(id, { disabled, visible });
    this.form.button(this.observable(id, initial), onClick, { disabled, visible });
    return this;
  }
  private write(id: string, value: LiveText): void {
    const o = this.obs.get(id);
    if (!o) return;
    try {
      o.setData(toRaw(value));
      this.writes++;
      this.lastWrite.set(id, system.currentTick);
    } catch {
      dduiBroken = true;
    }
  }
  set(id: string, value: LiveText): void {
    if (same(this.pending.get(id) ?? this.values.get(id), value)) return;
    this.values.set(id, value);
    const last = this.lastWrite.get(id);
    if (last === undefined || system.currentTick - last >= this.minWriteTicks) {
      this.pending.delete(id);
      this.write(id, value);
      return;
    }
    this.pending.set(id, value);
    if (this.flushId === undefined) {
      this.flushId = system.runTimeout(() => {
        this.flushId = undefined;
        for (const [k, v] of this.pending) this.write(k, v);
        this.pending.clear();
      }, Math.max(1, this.minWriteTicks - (system.currentTick - last)));
    }
  }
  setDisabled(id: string, disabled: boolean): void {
    const f = this.flags.get(id)?.disabled;
    if (f && f.getData() !== disabled) f.setData(disabled);
  }
  setVisible(id: string, visible: boolean): void {
    const f = this.flags.get(id)?.visible;
    if (f && f.getData() !== visible) f.setData(visible);
  }
  async show(): Promise<LiveCloseReason> {
    this.showing = true;
    try {
      const r = await this.form.show();
      return r === DataDrivenScreenClosedReason.UserBusy ? 'busy' : r === DataDrivenScreenClosedReason.ServerClosed ? 'server' : 'closed';
    } catch {
      dduiBroken = true;
      return 'fallback';
    } finally {
      this.showing = false;
      if (this.flushId !== undefined) system.clearRun(this.flushId);
      this.flushId = undefined;
    }
  }
  close(): void {
    try {
      if (this.form.isShowing()) this.form.close();
    } catch {
      /* already closed (FormVisibilityError) */
    }
  }
  isShowing(): boolean {
    try {
      return this.showing && this.form.isShowing();
    } catch {
      return false;
    }
  }
}

class ClassicLiveForm implements LiveForm {
  readonly kind = 'classic' as const;
  private readonly order: Array<{ id: string; header?: boolean; button?: () => void }> = [];
  private readonly values = new Map<string, LiveText>();
  private readonly hidden = new Set<string>();
  private readonly disabled = new Set<string>();
  private showing = false;
  writes = 0;

  constructor(
    private readonly player: Player,
    private readonly title: LiveText,
    private readonly actionbar: (id: string, text: LiveText) => void,
  ) {}
  header(id: string, initial: LiveText): this {
    this.order.push({ id, header: true });
    this.values.set(id, initial);
    return this;
  }
  label(id: string, initial: LiveText): this {
    this.order.push({ id });
    this.values.set(id, initial);
    return this;
  }
  button(id: string, initial: LiveText, onClick: () => void, opts: LiveButtonOptions = {}): this {
    this.order.push({ id, button: onClick });
    this.values.set(id, initial);
    if (opts.disabled) this.disabled.add(id);
    if (opts.visible === false) this.hidden.add(id);
    return this;
  }
  set(id: string, value: LiveText): void {
    if (same(this.values.get(id), value)) return;
    this.values.set(id, value);
    if (!this.showing && !this.hidden.has(id)) {
      this.writes++;
      this.actionbar(id, value);
    }
  }
  setDisabled(id: string, disabled: boolean): void {
    if (disabled) this.disabled.add(id);
    else this.disabled.delete(id);
  }
  setVisible(id: string, visible: boolean): void {
    if (visible) this.hidden.delete(id);
    else this.hidden.add(id);
  }
  async show(): Promise<LiveCloseReason> {
    const f = new ActionFormData().title(toRaw(this.title));
    const texts = this.order.filter((e) => !e.button && !this.hidden.has(e.id)).map((e) => toRaw(this.values.get(e.id)!));
    if (texts.length > 0) f.body(lines(...texts));
    const buttons = this.order.filter((e) => e.button && !this.hidden.has(e.id) && !this.disabled.has(e.id));
    for (const b of buttons) f.button(toRaw(this.values.get(b.id)!));
    this.showing = true;
    try {
      const r = await f.show(this.player);
      if (r.canceled) return r.cancelationReason === FormCancelationReason.UserBusy ? 'busy' : 'closed';
      if (r.selection !== undefined) buttons[r.selection]?.button?.();
      return 'selected';
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
  /** Minimum ticks between two writes of one component (default 2, global.md §2.9). */
  minWriteTicks?: number;
}

/** DDUI when preferred, healthy and constructible, else classic. */
export function createLiveForm(player: Player, title: LiveText, opts: LiveFormOptions): LiveForm {
  if (opts.preferDdui && !dduiBroken) {
    try {
      return new DduiLiveForm(player, title, Math.max(1, opts.minWriteTicks ?? 2));
    } catch {
      dduiBroken = true; // DDUI unavailable on this runtime: classic fallback (always kept, SLOTS.md §10.6)
    }
  }
  return new ClassicLiveForm(player, title, opts.actionbar);
}
