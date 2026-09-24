/**
 * Slots v2 DDUI machine form (animation/slots.md §6.2, SLOTS.md §10.6; task BS2 on the core `LiveForm`; lane
 * B-L9) and the presentation session shared with the classic fallback (`classic.ts`).
 *
 * `SlotPresenter` plays one spin's `Timeline` with ONE `playTimeline` session (2 t frames) and pushes each
 * frame to a `SlotView`: the DDUI form here (labels J / H / R / S + Spin ↔ Stop) or the action bar (classic).
 * Everything shown comes from the pure `features.screenAt` / `cuesBetween`, so both views tell the same story.
 *
 * The service (lane B-L8, `SlotHost`) owns the money: it debits, draws and persists the tape, then calls
 * `play`. The presenter calls `host.presented(start, interrupted)` exactly once — at the end of the timeline
 * (the gate) or at once on skip-to-end, close, leave (F8: the result is already persisted; interrupt = reveal).
 *
 * Budgets (slots.md §2.6): ≤ 1 Observable write per 2 t per label (LiveForm.set writes only changes),
 * jackpot label ≤ 1/s, one interval per active session, sounds ≤ 20/s.
 */
import { type Dimension, EasingType, MolangVariableMap, type Player, type Vector3, system } from '@minecraft/server';
import { uiManager } from '@minecraft/server-ui';
import { type Raw, anim, detach, join, lit, presentation, t } from '../../../../core';
import { SLOT_BEAT } from '../logic/timeline';
import { type CelebrateOptions, type CelebrateRuntime, type SlotFx, celebrateSpin, jackpotCinematic, jackpotInForm, slotCelebration } from './celebrate';
import { type Cue, type SlotScreen, cuesBetween, huntEndMs, huntOpenMs, huntOver, huntScreen, reelsMoving, screenAt, terminalScreen } from './features';
import type { SlotRound } from './frames';
import { type SlotConfigFlags, type SlotSettings, frameOptions, particleCount, slotLocalProfile, slotSettings, soundVolume, wantsSneakSkip } from './settings';

type Timeline = anim.Timeline;
type Beat = anim.Beat;
type LiveForm = presentation.LiveForm;
type PresentationSession = presentation.PresentationSession;

const K = 'gui.burmaldaholic.slots.';

/** Vanilla free camera preset (not in the id lists the pack validator knows). */
export const FREE_CAMERA = 'minecraft:' + 'free';

/** Skip compresses the rest of the current group into this much time (slots.md §2.2 DUR_SKIP). */
export const SKIP_REELS_MS = 200;
export const SKIP_OTHER_MS = 150;
/** Jackpot meter label ≤ 1 update per second. */
export const JACKPOT_LABEL_TICKS = 20;
/** `slots.spin_loop` is re-played every 20 t while the reels move (Bedrock has no looping sound instances). */
export const LOOP_TICKS = 20;
/** Sneak-to-skip is checked at most every 10 t. */
export const SNEAK_SKIP_TICKS = 10;
/** Autoplay opens the next chest after this many ticks. */
export const AUTO_PICK_TICKS = 10;

export interface SlotAnchor {
  readonly dimension: Dimension;
  /** centre of the reel face */
  readonly location: Vector3;
  /** outward unit direction of the face (x/z) */
  readonly facing: { x: number; z: number };
}

/** What the service hands the presenter for one spin. */
export interface SpinStart {
  readonly round: SlotRound;
  /** built with the same pure builder the server uses for its settle timer (S-B3) */
  readonly timeline: Timeline;
  /** ms already elapsed (late open, catch-up rule of docs/architecture/animation.md §3.3) */
  readonly startMs?: number;
}

/** The slots service side of the machine form (lane B-L8, S-B5). All labels are translated `Raw`. */
export interface SlotHost {
  readonly title: Raw;
  jackpotLine(): Raw;
  /** the last settled screen (e.g. `terminalScreen` of the previous round) shown when the form opens */
  lastScreen?(): SlotScreen | undefined;
  spinLabel(): Raw;
  betLabel(): Raw;
  /** undefined: no buy button (Overworld, disabled) */
  buyLabel?(): Raw | undefined;
  /** Debit + draw + persist; returns the round to present, or an error line for the header. */
  spin(buy: boolean): SpinStart | Raw;
  /** The presentation ended (gate) or was interrupted: settle now. Called exactly once per spin. */
  presented(start: SpinStart, interrupted: boolean): void;
  /** Treasure Hunt pick i: persist `opened = i + 1`, return entry i (D6); undefined when refused. */
  huntPick(start: SpinStart, i: number): number | undefined;
  betDown(): void;
  betUp(): void;
  auto(): void;
  paytable(): void;
  leave(): void;
  toggleTurbo?(): void;
  autoplaying?(): boolean;
  readonly anchor?: SlotAnchor;
  readonly flags?: SlotConfigFlags;
  /** core FxService (lane B-L1) when installed */
  readonly fx?: SlotFx;
}

/** Where a presenter draws. */
export interface SlotView {
  show(screen: SlotScreen, spinning: boolean): void;
  jackpots(line: Raw): void;
  /** false: the player closed the form / left → interrupt = reveal */
  alive(): boolean;
  /** Treasure Hunt buttons on/off */
  huntMode(on: boolean): void;
  /** a Major/Grand cinematic closed the form; `resume` re-opens it */
  suspended(on: boolean): void;
  /** the spin finished (view may re-open its between-spins form) */
  finished(start: SpinStart, interrupted: boolean): void;
}

// ---------------------------------------------------------------------------------------------------------
// Runtime primitives for celebrations
// ---------------------------------------------------------------------------------------------------------

export function celebrateRuntime(player: Player, s: SlotSettings, anchor?: SlotAnchor): CelebrateRuntime {
  return {
    after: (ticks, fn) => {
      system.runTimeout(fn, Math.max(1, Math.floor(ticks)));
    },
    setTitle: (p, title, subtitle, times) => {
      try {
        p.onScreenDisplay.setTitle(title, { subtitle, fadeInDuration: times[0], stayDuration: times[1], fadeOutDuration: times[2] });
      } catch {
        /* left */
      }
    },
    updateSubtitle: (p, sub) => {
      try {
        p.onScreenDisplay.updateSubtitle(sub);
      } catch {
        /* left */
      }
    },
    fadeGold: (p, i, h, o) => presentation.fade(p, { red: 1, green: 0.84, blue: 0.25, inS: i, holdS: h, outS: o }, s.fx),
    shake: (p, intensity, seconds) => presentation.shake(p, intensity, seconds, s.fx),
    particle: (id, count) => spawn(anchor ?? player, id, count),
    closeForms: (p) => {
      try {
        uiManager.closeAllForms(p);
      } catch {
        /* nothing open */
      }
    },
    setCamera: (p, location, facingLocation, ease) => {
      if (s.fx.reduceMotion) return false;
      try {
        p.camera.setCamera(FREE_CAMERA, { location, facingLocation, easeOptions: { easeTime: ease, easeType: EasingType.InOutSine } });
        return true;
      } catch {
        return false;
      }
    },
    clearCamera: (p) => {
      try {
        p.camera.clear();
      } catch {
        /* left */
      }
    },
    isValid: (p) => p.isValid,
  };
}

/** One `spawnParticle` per event with `variable.count` (Molang; ≤ 60 per burst). */
function spawn(at: SlotAnchor | Player, id: string, count: number): void {
  try {
    const vars = new MolangVariableMap();
    vars.setFloat('variable.count', Math.min(60, Math.max(1, count)));
    const loc = 'facing' in at ? { x: at.location.x + at.facing.x * 0.6, y: at.location.y, z: at.location.z + at.facing.z * 0.6 } : { x: at.location.x, y: at.location.y + 1.2, z: at.location.z };
    at.dimension.spawnParticle(id, loc, vars);
  } catch {
    /* unknown particle (NICE assets not in yet) or unloaded chunk */
  }
}

// ---------------------------------------------------------------------------------------------------------
// Presenter
// ---------------------------------------------------------------------------------------------------------

export class SlotPresenter {
  private start?: SpinStart;
  private session?: PresentationSession;
  private restarting = false;
  private lastT = -1;
  private loopTick = -1;
  private jpTick = -1000;
  private sneakTick = -1000;
  private suspendedNow = false;
  private musicOn = false;
  private cancelCelebration?: () => void;
  private hunt?: { opened: number; resumeAt: number; busy: boolean; all: boolean };
  private huntDone = false;
  settings: SlotSettings;
  /** Called when a Treasure Hunt pick is possible (classic view shows its pick form). */
  onPickReady?: () => void;

  constructor(
    private readonly player: Player,
    private readonly host: SlotHost,
    private view: SlotView,
  ) {
    this.settings = slotSettings(player, host.flags);
  }

  setView(v: SlotView): void {
    this.view = v;
  }

  running(): boolean {
    return this.start !== undefined;
  }

  inHunt(): boolean {
    return this.hunt !== undefined;
  }

  /** Starts presenting a drawn spin (F1: the tape exists before the first frame). */
  play(start: SpinStart): void {
    if (this.start) this.finish(true);
    this.settings = slotSettings(this.player, this.host.flags);
    this.start = start;
    this.huntDone = false;
    this.lastT = (start.startMs ?? 0) - 1;
    const tl = start.timeline;
    // late join: past 85 % of the shared part → settled state at once (catch-up rule)
    const huntPending = start.round.tape.hunt !== undefined && !huntOver(start.round, start.round.tape.hunt.opened);
    if (start.startMs && !huntPending && anim.showSettled(start.startMs, tl.sharedEndMs())) {
      this.finish(false);
      return;
    }
    this.run(start.startMs ?? 0);
  }

  private run(fromMs: number): void {
    const start = this.start!;
    this.restarting = false;
    this.session = presentation.playTimeline(
      start.timeline,
      {
        frame: (ms) => this.frame(ms),
        end: (_ms, interrupted) => {
          if (this.restarting) return;
          this.finish(interrupted);
        },
      },
      { periodTicks: 2, startMs: fromMs, alive: () => this.suspendedNow || (this.player.isValid && this.view.alive()) },
    );
  }

  private restartAt(ms: number): void {
    this.restarting = true;
    this.session?.stop();
    this.session = undefined;
    this.run(ms);
  }

  private frame(ms: number): void {
    const start = this.start;
    if (!start || this.hunt || this.suspendedNow) return;
    const { round, timeline: tl } = start;
    const fo = frameOptions(this.settings);
    const now = system.currentTick;
    this.view.show(screenAt(round, tl, ms, fo), reelsMoving(tl, ms));
    for (const c of cuesBetween(round, tl, this.lastT, ms, fo)) this.cue(c);
    for (const b of tl.beats) {
      if (b.at > ms) break;
      if (b.at > this.lastT) this.beatStarted(b, ms);
    }
    const prev = this.lastT;
    this.lastT = ms;
    if (this.loopTick >= 0 && now - this.loopTick >= LOOP_TICKS) this.sfx('slots.spin_loop', 1, 0.25, now);
    if (now - this.jpTick >= JACKPOT_LABEL_TICKS) {
      this.jpTick = now;
      this.view.jackpots(this.host.jackpotLine());
    }
    if (now - this.sneakTick >= SNEAK_SKIP_TICKS && wantsSneakSkip(this.player, this.host.anchor?.location)) {
      this.sneakTick = now;
      this.skip();
      return;
    }
    // Treasure Hunt: the timeline pauses after the board intro until the picks are done
    const intro = tl.beats.find((b) => b.kind === SLOT_BEAT.BONUS_INTRO && b.args[0] === 1);
    if (intro && round.tape.hunt && !this.huntDone && prev < anim.beatEnd(intro) && ms >= anim.beatEnd(intro)) this.enterHunt(anim.beatEnd(intro));
  }

  private beatStarted(b: Beat, _ms: number): void {
    const start = this.start!;
    const { round } = start;
    const o = this.celebrateOptions();
    if (b.kind === SLOT_BEAT.ROLLUP) {
      const c = slotCelebration(round.totalChips, round.bet, round.tier, round.tape.capHit);
      this.cancelCelebration = celebrateSpin(this.player, c, celebrateRuntime(this.player, this.settings, this.host.anchor), o);
    } else if (b.kind === SLOT_BEAT.JACKPOT) {
      const idx = start.timeline.beats.filter((x) => x.kind === SLOT_BEAT.JACKPOT).indexOf(b);
      const award = round.tape.jackpots[idx];
      if (!award) return;
      const jp = { tier: award.tier, chips: award.chips, face: this.host.anchor?.location, facing: this.host.anchor?.facing };
      const rt = celebrateRuntime(this.player, this.settings, this.host.anchor);
      if (award.tier >= 3 && this.settings.fx.celebrations !== 'off') {
        this.suspendedNow = true;
        this.view.suspended(true);
        const resumeAt = anim.beatEnd(b);
        this.restarting = true;
        this.session?.stop();
        this.session = undefined;
        jackpotCinematic(this.player, jp, rt, o, () => {
          if (!this.start) return;
          this.suspendedNow = false;
          this.view.suspended(false);
          this.lastT = resumeAt - 1;
          this.run(resumeAt);
        });
      } else jackpotInForm(jp, rt, o);
    }
  }

  private celebrateOptions(): CelebrateOptions {
    const s = this.settings;
    return { reduceMotion: s.fx.reduceMotion, celebrations: s.fx.celebrations, fx: this.host.fx, seed: this.start?.timeline.seed ?? 0, scale: (n) => particleCount(s, n) };
  }

  private sfx(id: string, pitch: number, volume: number, now = system.currentTick): void {
    const v = soundVolume(this.settings, volume);
    if (id === 'slots.spin_loop') this.loopTick = now;
    if (v <= 0) return;
    try {
      this.player.playSound(`burmaldaholic.${id}`, { pitch, volume: v });
    } catch {
      /* left */
    }
  }

  private worldSound(id: string, pitch: number, volume: number): void {
    const a = this.host.anchor;
    const v = soundVolume(this.settings, volume);
    if (!a || v <= 0) return this.sfx(id, pitch, volume);
    try {
      a.dimension.playSound(`burmaldaholic.${id}`, a.location, { pitch, volume: v });
    } catch {
      /* unloaded */
    }
  }

  private stopSound(id: string): void {
    try {
      this.player.runCommand(`stopsound @s burmaldaholic.${id}`);
    } catch {
      /* left */
    }
  }

  private cue(c: Cue): void {
    switch (c.kind) {
      case 'sound':
        if (c.world) this.worldSound(c.id, c.pitch, c.volume);
        else this.sfx(c.id, c.pitch, c.volume);
        break;
      case 'stop':
        this.stopSound(c.id);
        break;
      case 'loop':
        if (c.on) this.sfx(c.id, 1, 0.25);
        else {
          this.loopTick = -1;
          this.stopSound(c.id);
        }
        break;
      case 'music':
        this.music(c.id, c.on);
        break;
      case 'particle':
        if (this.host.anchor) spawn(this.host.anchor, c.id, particleCount(this.settings, c.count));
        break;
    }
  }

  private music(id: string, on: boolean): void {
    try {
      if (on && soundVolume(this.settings, 0.5) > 0) {
        this.player.playMusic(`burmaldaholic.${id}`, { loop: true, fade: 1, volume: soundVolume(this.settings, 0.5) });
        this.musicOn = true;
      } else if (!on && this.musicOn) {
        this.player.stopMusic();
        this.musicOn = false;
      }
    } catch {
      /* left */
    }
  }

  /** Stop button / sneak: compress the current group (reels keep their landing), never skips a result. */
  skip(): void {
    const start = this.start;
    if (!start || this.hunt || this.suspendedNow) return;
    const tl = start.timeline;
    const ms = Math.max(0, this.lastT);
    const g = tl.groupAt(ms);
    if (g < 0) return;
    const end = tl.groupEnd(g);
    const target = reelsMoving(tl, ms) ? Math.max(ms, end - SKIP_REELS_MS) : end;
    this.cancelCelebration?.();
    this.cancelCelebration = undefined;
    if (target >= tl.endMs()) {
      this.finish(false);
      return;
    }
    this.stopSound('slots.anticipation');
    this.lastT = target - (reelsMoving(tl, ms) ? SKIP_REELS_MS + 1 : 1);
    this.restartAt(target);
  }

  /** Ends the presentation: terminal screen, sounds off, settle through the host (once). */
  finish(interrupted: boolean): void {
    const start = this.start;
    if (!start) return;
    this.start = undefined;
    this.hunt = undefined;
    this.restarting = true;
    this.session?.stop();
    this.session = undefined;
    this.suspendedNow = false;
    this.loopTick = -1;
    this.stopSound('slots.spin_loop');
    this.stopSound('slots.anticipation');
    this.music('', false);
    if (interrupted) this.cancelCelebration?.();
    this.cancelCelebration = undefined;
    if (this.player.isValid) {
      this.view.huntMode(false);
      this.view.show(terminalScreen(start.round, frameOptions(this.settings)), false);
    }
    try {
      this.host.presented(start, interrupted);
    } finally {
      this.view.finished(start, interrupted);
    }
  }

  // ---- Treasure Hunt ----

  private enterHunt(resumeAt: number): void {
    const start = this.start!;
    this.restarting = true;
    this.session?.stop();
    this.session = undefined;
    this.hunt = { opened: start.round.tape.hunt?.opened ?? 0, resumeAt, busy: false, all: false };
    this.view.huntMode(true);
    this.view.show(huntScreen(start.round, { opened: this.hunt.opened }, frameOptions(this.settings)), false);
    if (this.host.autoplaying?.()) system.runTimeout(() => this.pick(true), AUTO_PICK_TICKS);
    else this.onPickReady?.();
  }

  /** `system.runTimeout` (a continuation of the session, never a new entry point). */
  after(ticks: number, fn: () => void): void {
    system.runTimeout(fn, Math.max(1, Math.floor(ticks)));
  }

  /** "Open a chest" (next chest in reading order — honest, SLOTS.md §1.2) or "Open all". */
  pick(all: boolean): void {
    const start = this.start;
    const h = this.hunt;
    if (!start || !h || h.busy) return;
    h.all = h.all || all;
    const i = h.opened;
    const entry = this.host.huntPick(start, i); // server confirms: entry i is revealed only now (D6)
    if (entry === undefined) return;
    h.busy = true;
    const local = slotLocalProfile(this.settings);
    const fo = frameOptions(this.settings);
    const dur = huntOpenMs(entry, local);
    const tl = anim.Timeline.builder('slots.hunt', i).clock(anim.LOCAL).add(0, dur, SLOT_BEAT.HUNT_OPEN, i, entry).build();
    let opened = false;
    presentation.playTimeline(
      tl,
      {
        frame: (ms) => {
          this.view.show(huntScreen(start.round, { opened: i, opening: { i, ms: (ms * 100) / local.speedPct } }, fo), false);
          if (!opened && ms * (100 / local.speedPct) >= 200) {
            opened = true;
            this.sfx(entry === 0 ? 'slots.creeper_hiss' : 'slots.chest_open', 1.1, 1);
            if (entry === 0 && this.host.anchor) spawn(this.host.anchor, 'minecraft:large_explosion', 1);
          }
        },
        end: () => this.picked(i),
      },
      { periodTicks: 2, alive: () => this.player.isValid },
    );
  }

  private picked(i: number): void {
    const start = this.start;
    const h = this.hunt;
    if (!start || !h) return;
    h.opened = i + 1;
    h.busy = false;
    const fo = frameOptions(this.settings);
    if (!huntOver(start.round, h.opened)) {
      this.view.show(huntScreen(start.round, { opened: h.opened }, fo), false);
      if (h.all || this.host.autoplaying?.()) system.runTimeout(() => this.pick(h.all), h.all ? 2 : AUTO_PICK_TICKS);
      else this.onPickReady?.();
      return;
    }
    // end: the remaining chests open dimmed (real entries, never invented), then the timeline resumes
    const remaining = 15 - h.opened;
    const dur = huntEndMs(Math.max(0, remaining));
    const tl = anim.Timeline.builder('slots.hunt_end', i).clock(anim.LOCAL).add(0, Math.max(50, dur), SLOT_BEAT.HUNT_OPEN, -1).build();
    presentation.playTimeline(
      tl,
      {
        frame: (ms) => this.view.show(huntScreen(start.round, { opened: h.opened, endMs: ms }, fo), false),
        end: () => {
          if (this.start !== start) return;
          this.hunt = undefined;
          this.huntDone = true;
          this.view.huntMode(false);
          this.lastT = h.resumeAt - 1;
          this.run(h.resumeAt);
        },
      },
      { periodTicks: 2, alive: () => this.player.isValid },
    );
  }
}

// ---------------------------------------------------------------------------------------------------------
// DDUI view
// ---------------------------------------------------------------------------------------------------------

const EMPTY: Raw = lit('');

/** LiveForm component ids (labels J / H / R / S and the buttons). */
export const FORM_ID = {
  jp: 'jp',
  hdr: 'hdr',
  reels: 'reels',
  status: 'status',
  spin: 'spin',
  bet_down: 'bet_down',
  bet_up: 'bet_up',
  buy: 'buy',
  auto: 'auto',
  turbo: 'turbo',
  paytable: 'paytable',
  leave: 'leave',
} as const;

/** The DDUI machine form. `open()` returns false when DDUI is unavailable (caller falls back to classic). */
export class DduiSlotForm implements SlotView {
  readonly presenter: SlotPresenter;
  private form?: LiveForm;
  private hunting = false;
  private reopening = false;
  private error?: Raw;

  constructor(
    private readonly player: Player,
    private readonly host: SlotHost,
  ) {
    this.presenter = new SlotPresenter(player, host, this);
  }

  open(): boolean {
    const p = this.player;
    const f = presentation.createLiveForm(p, this.host.title, { preferDdui: true, actionbar: () => {} });
    if (f.kind !== 'ddui') return false;
    const rest = this.restScreen();
    f.label(FORM_ID.jp, this.host.jackpotLine())
      .label(FORM_ID.hdr, rest.header ?? EMPTY)
      .label(FORM_ID.reels, rest.rows)
      .label(FORM_ID.status, rest.status ?? EMPTY)
      .button(FORM_ID.spin, this.host.spinLabel(), () => this.onSpin(false))
      .button(FORM_ID.bet_down, t(`${K}bet_down`), () => !this.presenter.running() && this.host.betDown())
      .button(FORM_ID.bet_up, t(`${K}bet_up`), () => !this.presenter.running() && this.host.betUp());
    const buy = this.host.buyLabel?.();
    if (buy) f.button(FORM_ID.buy, buy, () => this.onSpin(true));
    f.button(FORM_ID.auto, t(`${K}auto`), () => (this.hunting ? this.presenter.pick(true) : this.host.auto()))
      .button(FORM_ID.turbo, this.turboLabel(), () => {
        this.host.toggleTurbo?.();
        this.presenter.settings = slotSettings(p, this.host.flags);
        this.form?.set(FORM_ID.turbo, this.turboLabel());
      })
      .button(FORM_ID.paytable, t('gui.burmaldaholic.common.paytable'), () => !this.presenter.running() && this.host.paytable())
      .button(FORM_ID.leave, t('gui.burmaldaholic.common.leave'), () => {
        this.form?.close();
        this.host.leave();
      });
    this.form = f;
    detach(
      f.show().then(() => {
        if (this.form === f && !this.reopening) this.form = undefined;
      }),
      () => {
        if (this.form === f) this.form = undefined;
      },
    );
    return true;
  }

  private turboLabel(): Raw {
    return join(t(`${K}turbo`), lit(this.presenter.settings.turbo ? ' §a●§r' : ' §8○§r'));
  }

  private restScreen(): SlotScreen {
    const last = this.host.lastScreen?.();
    return { board: 'reels', rows: last?.rows ?? EMPTY, header: this.error ?? last?.header ?? this.host.betLabel(), status: last?.status };
  }

  private onSpin(buy: boolean): void {
    if (this.hunting) return this.presenter.pick(false);
    if (this.presenter.running()) return this.presenter.skip();
    const r = this.host.spin(buy);
    if (!('round' in r)) {
      this.error = r;
      this.form?.set(FORM_ID.hdr, join(lit('§c'), r, lit('§r')));
      return;
    }
    this.error = undefined;
    this.form?.set(FORM_ID.spin, t(`${K}stop`));
    this.presenter.play(r);
  }

  show(s: SlotScreen, spinning: boolean): void {
    const f = this.form;
    if (!f) return;
    f.set(FORM_ID.reels, s.rows);
    f.set(FORM_ID.hdr, s.header ?? EMPTY);
    f.set(FORM_ID.status, s.status ?? EMPTY);
    if (!this.hunting) f.set(FORM_ID.spin, this.presenter.running() || spinning ? t(`${K}stop`) : this.host.spinLabel());
  }

  jackpots(line: Raw): void {
    this.form?.set(FORM_ID.jp, line);
  }

  alive(): boolean {
    return this.form?.isShowing() ?? false;
  }

  huntMode(on: boolean): void {
    this.hunting = on;
    this.form?.set(FORM_ID.spin, on ? t(`${K}pick.open`) : this.presenter.running() ? t(`${K}stop`) : this.host.spinLabel());
    this.form?.set(FORM_ID.auto, on ? t(`${K}pick.open_all`) : t(`${K}auto`));
  }

  suspended(on: boolean): void {
    if (on) {
      this.reopening = true;
      this.form = undefined;
      return;
    }
    this.reopening = false;
    if (this.player.isValid) this.open();
  }

  finished(start: SpinStart, _interrupted: boolean): void {
    const f = this.form;
    if (!f) return;
    f.set(FORM_ID.spin, this.host.spinLabel());
    f.set(FORM_ID.jp, this.host.jackpotLine());
    const s = terminalScreen(start.round, frameOptions(this.presenter.settings));
    if (!s.header) f.set(FORM_ID.hdr, this.host.betLabel());
  }
}
