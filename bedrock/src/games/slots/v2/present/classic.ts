/**
 * Slots v2 classic fallback (animation/slots.md §6.3, SLOTS.md §10.6 "Fallback"; task BS3; lane B-L9) and the
 * machine entry point `openSlotMachine` (DDUI first, classic when `slots.bedrock.ddui` is false or DDUI failed).
 *
 * Classic: an `ActionFormData` between spins (jackpot line, the last window with its win glow, the result line,
 * the bet; Spin · Bet − · Bet + · Buy · Auto · Turbo · Paytable · Leave). During the spin the form is closed and
 * the action bar carries the reels: the same frames as DDUI (blur plane, land flash, anticipation tint/arrow),
 * updated every 2 t with `HudPriority.game`; feature banners also go to the title; the hunt uses an
 * ActionForm ("Open a chest" / "Open all"). The form re-opens 20 / 40 / 50 / 60 t after WIN / BIG / MEGA /
 * EPIC+ (global.md §2.6), at once after an interrupt.
 *
 * NICE "watch the cabinet" (§6.5): with `slots.fx.cabinet_view` on, Spin closes the form and eases the camera
 * to the cabinet front for the round (never under reduce motion); the classic view is used for that round.
 */
import { EasingType, type Player } from '@minecraft/server';
import { ActionFormData } from '@minecraft/server-ui';
import { type Hud, HudPriority, type Raw, anim, detach, join, lines, lit, showForm, t } from '../../../../core';
import { cinematicCamera } from './celebrate';
import { DduiSlotForm, FREE_CAMERA, type SlotHost, SlotPresenter, type SlotView, type SpinStart } from './ddui-form';
import { type SlotScreen, terminalScreen } from './features';
import { frameOptions, slotSettings } from './settings';

const K = 'gui.burmaldaholic.slots.';
export const HUD_CHANNEL = 'slots.spin';
/** Action-bar frames live 20 t (refreshed every 2 t while the session runs). */
export const ACTIONBAR_TTL = 20;
/** A closed hunt form is offered again after this many ticks. */
export const HUNT_REASK_TICKS = 20;

/** Ticks before the machine form re-opens after the result (global.md §2.6 Bedrock). */
export function reopenDelay(tier: anim.WinTier, jackpot: boolean, interrupted: boolean): number {
  if (interrupted) return 1;
  if (jackpot) return 60;
  switch (tier) {
    case 'EPIC':
      return 60;
    case 'MEGA':
      return 50;
    case 'BIG':
      return 40;
    case 'NICE':
    case 'WIN':
      return 20;
    default:
      return 10;
  }
}

/** Header keys that are feature banners: shown as a title once when they appear (classic only). */
export const BANNER_KEYS = [`${K}fs.title`, `${K}fs.retrigger`, `${K}fs.end`, `${K}bonus.hold`, `${K}bonus.wheel`, `${K}bonus.pick`, `${K}wheel.up`, `${K}hold.full`, `${K}max_win`, `${K}pick.creeper`] as const;

/** PURE: the banner key of a header (first translate key when it is a banner). */
export function bannerKey(header: Raw | undefined): string | undefined {
  let found: string | undefined;
  const walk = (r: Raw): void => {
    if (found) return;
    if (r.translate && (BANNER_KEYS as readonly string[]).includes(r.translate)) found = r.translate;
    for (const c of r.rawtext ?? []) walk(c);
  };
  if (header) walk(header);
  return found;
}

/** PURE: action-bar message of a frame (reels + header + status lines). */
export function actionbarRaw(s: SlotScreen): Raw {
  const parts: Raw[] = [s.rows];
  if (s.header) parts.push(s.header);
  if (s.status) parts.push(s.status);
  return lines(...parts);
}

export class ClassicSlotView implements SlotView {
  readonly presenter: SlotPresenter;
  private last?: SlotScreen;
  private error?: Raw;
  private banner?: string;
  private hunting = false;
  private cameraOn = false;
  private closed = false;

  constructor(
    private readonly player: Player,
    private readonly host: SlotHost,
    private readonly hud: Hud,
    presenter?: SlotPresenter,
  ) {
    this.presenter = presenter ?? new SlotPresenter(player, host, this);
    this.presenter.setView(this);
    this.presenter.onPickReady = () => this.huntForm();
  }

  /** The between-spins machine form. */
  openMachine(): void {
    if (!this.player.isValid || this.closed) return;
    const body: Raw[] = [this.host.jackpotLine()];
    if (this.last) body.push(this.last.rows);
    if (this.last?.header) body.push(this.last.header);
    if (this.last?.status) body.push(this.last.status);
    body.push(this.host.betLabel());
    if (this.error) body.push(join(lit('§c'), this.error, lit('§r')));
    const buy = this.host.buyLabel?.();
    const actions: Array<[Raw, () => void]> = [
      [this.host.spinLabel(), () => this.spin(false)],
      [t(`${K}bet_down`), () => this.after(() => this.host.betDown())],
      [t(`${K}bet_up`), () => this.after(() => this.host.betUp())],
    ];
    if (buy) actions.push([buy, () => this.spin(true)]);
    actions.push(
      [t(`${K}auto`), () => this.host.auto()],
      [join(t(`${K}turbo`), lit(this.presenter.settings.turbo ? ' §a●§r' : ' §8○§r')), () => this.after(() => this.host.toggleTurbo?.())],
      [t('gui.burmaldaholic.common.paytable'), () => this.host.paytable()],
      [t('gui.burmaldaholic.common.leave'), () => this.host.leave()],
    );
    const form = new ActionFormData().title(this.host.title).body(lines(...body));
    for (const [label] of actions) form.button(label);
    detach(
      showForm(this.player, form).then((res) => {
        if (!res || res.canceled || res.selection === undefined) return;
        actions[res.selection]?.[1]();
      }),
      () => {},
    );
  }

  /** Runs an action, then shows the machine form again. */
  private after(fn: () => void): void {
    fn();
    this.presenter.settings = slotSettings(this.player, this.host.flags);
    this.openMachine();
  }

  spin(buy: boolean): void {
    const r = this.host.spin(buy);
    if (!('round' in r)) {
      this.error = r;
      this.openMachine();
      return;
    }
    this.error = undefined;
    this.banner = undefined;
    this.cabinetCamera(true);
    this.presenter.play(r);
  }

  private cabinetCamera(on: boolean): void {
    const a = this.host.anchor;
    const s = this.presenter.settings;
    try {
      if (on && s.cabinetView && !s.fx.reduceMotion && a) {
        const cam = cinematicCamera(a.location, a.facing);
        this.player.camera.setCamera(FREE_CAMERA, { location: cam.location, facingLocation: cam.facingLocation, easeOptions: { easeTime: 0.6, easeType: EasingType.InOutSine } });
        this.cameraOn = true;
      } else if (!on && this.cameraOn) {
        this.player.camera.clear();
        this.cameraOn = false;
      }
    } catch {
      this.cameraOn = false;
    }
  }

  show(s: SlotScreen, _spinning: boolean): void {
    this.last = s;
    if (!this.player.isValid) return;
    this.hud.actionbar(this.player, HUD_CHANNEL, actionbarRaw(s), HudPriority.game, ACTIONBAR_TTL);
    const b = bannerKey(s.header);
    if (b && b !== this.banner) {
      this.hud.title(this.player, s.header!, s.status, 5, 40, 10);
    }
    this.banner = b;
  }

  jackpots(_line: Raw): void {
    /* shown in the between-spins body */
  }

  alive(): boolean {
    return this.player.isValid && !this.closed;
  }

  huntMode(on: boolean): void {
    this.hunting = on;
  }

  /** "Open a chest" / "Open all" between picks (called by the presenter when a pick is possible). */
  private huntForm(): void {
    if (!this.hunting || !this.player.isValid) return;
    this.hud.clear(this.player, HUD_CHANNEL);
    const body: Raw[] = [];
    if (this.last) body.push(this.last.rows);
    if (this.last?.status) body.push(this.last.status);
    const form = new ActionFormData()
      .title(t(`${K}bonus.pick`))
      .body(lines(...body, t(`${K}pick.hint`)))
      .button(t(`${K}pick.open`))
      .button(t(`${K}pick.open_all`));
    detach(
      showForm(this.player, form).then((res) => {
        if (!this.hunting) return;
        // closing the form is not a choice: ask again later (a required choice is never auto-made, F8)
        if (!res || res.canceled || res.selection === undefined) {
          this.presenter.after(HUNT_REASK_TICKS, () => this.huntForm());
          return;
        }
        this.presenter.pick(res.selection === 1);
      }),
      () => {},
    );
  }

  suspended(_on: boolean): void {
    /* the classic form is already closed during the spin */
  }

  finished(start: SpinStart, interrupted: boolean): void {
    this.hunting = false;
    this.cabinetCamera(false);
    this.last = terminalScreen(start.round, frameOptions(this.presenter.settings));
    const delay = reopenDelay(start.round.tier, start.round.tape.jackpots.length > 0, interrupted);
    this.presenter.after(delay, () => {
      this.hud.clear(this.player, HUD_CHANNEL);
      this.openMachine();
    });
  }

  close(): void {
    this.closed = true;
  }
}

export interface OpenDeps {
  hud: Hud;
}

/**
 * Opens a machine for the player: DDUI when enabled and available, else the classic form. With the NICE
 * cabinet view on, the classic view is used (the form closes during the spin and the camera watches the
 * cabinet). Returns the presenter (the service may call `skip()` / `finish()` on disconnect, casino off …).
 */
export function openSlotMachine(player: Player, host: SlotHost, deps: OpenDeps): SlotPresenter {
  const s = slotSettings(player, host.flags);
  if (s.ddui && !s.cabinetView) {
    const f = new DduiSlotForm(player, host);
    if (f.open()) return f.presenter;
  }
  const c = new ClassicSlotView(player, host, deps.hud);
  c.openMachine();
  return c.presenter;
}
