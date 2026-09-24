/**
 * `FxService` — the Bedrock celebration kit and FX front door (global.md §2.6 / §3.2, docs/architecture/animation.md
 * §2.10 and §4). Games call it instead of `hud.title` + `playSound`:
 *
 * - `celebrate(player, request)` — the per-game tier API (lead decision L2): the caller passes its server tier, its
 *   threshold table (`DEFAULT_TIERS` / `SLOT_TIERS` / own), its words and stems. The pure plan
 *   (`logic/anim/celebration.ts`) becomes a LOCAL timeline played by the shared scheduler (one interval per
 *   celebration, 2 t cadence): title + `updateSubtitle` roll-up, word upgrades, stems, count ticks, particles, camera
 *   fade/shake. Sneaking skips to the exact final frame. `done` resolves when the result form may open.
 * - `sound`, `soundAt`, `burst` — catalog sounds (× effects volume, layers, ≤ 20/s) and budgeted particles.
 * - `toast`, `chaos`, `hudPulse`, `titleFlipbook`, `actionbarRollUp`, `chatAtLanding` — shared helpers
 *   (global.md §4.10, BX1).
 *
 * Every visual has a text baseline; settings are read per call (`anim.*` player properties).
 */
import { type Dimension, type Player, PlayerPermissionLevel, type Vector3, system, world } from '@minecraft/server';
import {
  CEL_BEAT,
  CEL_PARTICLES,
  type CelebrationProfile,
  type CelebrationRequest,
  CORE_TIER_STEMS,
  MODE_TITLE,
  PARTICLE_FOUNTAIN,
  buildCelebration,
  celebrationFrame,
  tierWordKey,
} from '../logic/anim/celebration';
import { rollUpValue, tickPitch } from '../logic/anim/rollup';
import { planFlipbook } from '../logic/anim/sound-plan';
import { LOCAL, Timeline, type Beat } from '../logic/anim/timeline';
import { DEFAULT_TIERS, SLOT_TIERS, type WinTier, WIN_TIERS } from '../logic/anim/win-tier';
import { type Raw, color, decimal, join, lit, t } from '../logic/rawtext';
import { fade, shake } from './camera';
import { ParticleBudget, burst, type BurstOpts } from './particles';
import { type PresentationSession, playTimeline } from './scheduler';
import { type FxSettings, fxSettings } from './settings';
import { forgetSoundHistory, playAt, playCasinoSound, type SoundOpts } from './sound';
import { runDduiSpike } from './ddui-spike';
import { revealAfter } from './table-fx';

/** The slice of the core HUD the kit needs (`core/hud.ts` `Hud`). */
export interface FxHud {
  actionbar(player: Player, channel: string, message: Raw, priority?: number, ttlTicks?: number): void;
}

export interface CelebrateOptions {
  /** Where the particles burst (default: 1 block above the player). */
  at?: Vector3;
  /** Dimension of `at` (default: the player's). */
  dimension?: Dimension;
}

export interface CelebrationHandle {
  readonly timeline: Timeline;
  /** Resolves when the result form may open (end of the plan, a skip, or an interrupt). */
  readonly done: Promise<void>;
  /** Jump to the final frame (sneak does the same). */
  skip(): void;
}

export type ChaosMood = 'good' | 'bad' | 'neutral';

/** Operator scriptevent id (spike harness, celebration preview). */
export const FX_SCRIPT_EVENT = 'burmaldaholic:fx';
/** HUD action-bar channel of the kit. */
export const FX_CHANNEL = 'core.fx';
const PRIORITY_GAME = 10;
const PRIORITY_ALERT = 20;

const TITLE_COLOR: Partial<Record<WinTier, string>> = { NICE: '§e', BIG: '§6', MEGA: '§6§l', EPIC: '§6§l', JACKPOT: '§e§l' };

const amountRaw = (n: number): Raw => join(lit('§a+'), lit(n));

/** ×M of the stake (integer from 10×, one decimal below, locale separator via `decimal`). */
function multiplierRaw(ret: number, stake: number): Raw {
  const m = stake > 0 ? ret / stake : 0;
  return join(lit('§7×'), m >= 10 ? lit(Math.floor(m)) : decimal(Math.floor(m * 10) / 10));
}

export class FxService {
  private readonly active = new Map<string, PresentationSession>();
  private pulse: ((p: Player, segmentId: string) => void) | undefined;

  constructor(private readonly hud: FxHud) {}

  /** The player's FX settings (global.md §2.8). */
  settings(p: Player): FxSettings {
    return fxSettings(p);
  }

  /** Profile a celebration is built with for this player. */
  profile(p: Player): CelebrationProfile {
    const s = fxSettings(p);
    return { speedPct: s.speedPct, reduceMotion: s.reduceMotion, flashes: s.flashes, celebrations: s.celebrations };
  }

  // ---------------------------------------------------------------------------------------------- celebrate

  /** The word a tier shows (caller's words; `%1` = amount, or the jackpot sub-tier word). */
  word(req: CelebrationRequest, tier: WinTier, amount: number): Raw {
    const key = tierWordKey(req.words, tier);
    if (tier === 'JACKPOT') return req.subTierWord ? t(key, t(req.subTierWord)) : t(key, lit(''));
    return t(key, amount);
  }

  private subtitle(req: CelebrationRequest, amount: number, final: boolean): Raw {
    const parts: Raw[] = [];
    if (final && req.maxWin && req.words?.maxWin) parts.push(color('§c§l', t(req.words.maxWin)), lit(' '));
    parts.push(amountRaw(amount));
    if (req.tier === 'JACKPOT') parts.push(lit(' '), multiplierRaw(req.ret, req.stake));
    return join(...parts);
  }

  private actionbarLine(req: CelebrationRequest): Raw {
    const w = this.word(req, req.tier, req.ret);
    switch (req.tier) {
      case 'LOSS':
        return color('§c', w);
      case 'PUSH':
      case 'RETURN':
        return color('§7', w);
      case 'WIN':
        return amountRaw(req.ret);
      default:
        return join(color('§e', w), lit(' '), amountRaw(req.ret));
    }
  }

  /**
   * Plays the celebration for `p` (the server tier is final; the plan ends on it). A running celebration of the
   * same player is finished first (overrun rule).
   */
  celebrate(p: Player, req: CelebrationRequest, opts: CelebrateOptions = {}): CelebrationHandle {
    this.active.get(p.id)?.finishNow();
    const profile = this.profile(p);
    const tl = buildCelebration(req, profile);
    const stems = req.stems ?? CORE_TIER_STEMS;
    const budget = new ParticleBudget();
    let mode = 0;
    let started = false;
    let fades: readonly number[] = [0, 0, 0];
    let shownWord: WinTier = req.tier;
    let shownAmount = -1;
    let resolve: () => void = () => {};
    const done = new Promise<void>((r) => (resolve = r));

    const setTitle = (tier: WinTier, amount: number, fadeIn: number, stay: number, final = false): void => {
      try {
        p.onScreenDisplay.setTitle(color(TITLE_COLOR[tier] ?? '§6', this.word(req, tier, amount)), {
          fadeInDuration: fadeIn,
          stayDuration: stay,
          fadeOutDuration: fades[2]!,
          subtitle: this.subtitle(req, amount, final),
        });
        shownWord = tier;
        shownAmount = amount;
      } catch {
        /* player left */
      }
    };
    const updateAmount = (amount: number, final = false): void => {
      // the final frame may add the MAX WIN plate to an amount the roll-up already showed
      const plate = final && !!req.maxWin && !!req.words?.maxWin;
      if (mode !== MODE_TITLE || (amount === shownAmount && !plate)) return;
      shownAmount = amount;
      try {
        p.onScreenDisplay.updateSubtitle(this.subtitle(req, amount, final));
      } catch {
        /* player left */
      }
    };
    const at = (): Vector3 => opts.at ?? { x: p.location.x, y: p.location.y + 1, z: p.location.z };
    const remainingStay = (tMs: number): number => Math.max(10, Math.ceil((tl.endMs() - tMs) / 50) + 10);

    const beat = (b: Beat, tMs: number): void => {
      const a = b.args;
      switch (b.kind) {
        case CEL_BEAT.START:
          started = true;
          mode = a[2]!;
          fades = [a[3]!, a[4]!, a[5]!];
          if (mode === MODE_TITLE) setTitle(WIN_TIERS[a[1]!]!, 0, a[3]!, a[4]!);
          else this.hud.actionbar(p, FX_CHANNEL, this.actionbarLine(req), PRIORITY_GAME, Math.ceil(tl.endMs() / 50) + 20);
          break;
        case CEL_BEAT.WORD: {
          const frame = celebrationFrame(req, tl, tMs);
          setTitle(WIN_TIERS[a[0]!]!, frame.amount, 0, remainingStay(tMs));
          break;
        }
        case CEL_BEAT.STEM: {
          const id = stems[WIN_TIERS[a[0]!]!];
          if (id) this.sound(p, id);
          break;
        }
        case CEL_BEAT.TICK:
          if (stems.tick) this.sound(p, stems.tick, { pitch: tickPitch(a[0]!) });
          break;
        case CEL_BEAT.BURST: {
          const dim = opts.dimension ?? p.dimension;
          const particle = CEL_PARTICLES[a[0]!];
          if (particle) burst(dim, particle, at(), { count: a[1]!, owner: p, celebration: true, tint: a[0] === PARTICLE_FOUNTAIN ? [1, 0.84, 0.25] : undefined }, budget);
          break;
        }
        case CEL_BEAT.FADE:
          fade(p, { red: a[0]! / 255, green: a[1]! / 255, blue: a[2]! / 255, inS: a[3]! / 1000, holdS: a[4]! / 1000, outS: a[5]! / 1000 });
          break;
        case CEL_BEAT.SHAKE:
          shake(p, a[0]! / 100, a[1]! / 1000);
          break;
        default:
          break;
      }
    };

    const session = playTimeline(
      tl,
      {
        beat,
        frame: (tMs) => updateAmount(celebrationFrame(req, tl, tMs).amount),
        end: () => {
          if (this.active.get(p.id) === session) this.active.delete(p.id);
          // skipped (sneak, overrun) before the START beat played: the terminal frame still needs its title / line
          const start = started ? undefined : tl.beats.find((b) => b.kind === CEL_BEAT.START);
          if (start && p.isValid) {
            mode = start.args[2]!;
            fades = [start.args[3]!, start.args[4]!, start.args[5]!];
            if (mode !== MODE_TITLE) this.hud.actionbar(p, FX_CHANNEL, this.actionbarLine(req), PRIORITY_GAME, 40);
          }
          // terminal frame: the exact server amount under the server tier word (also after a skip)
          if (mode === MODE_TITLE && p.isValid) {
            if (start || shownWord !== req.tier) setTitle(req.tier, req.ret, 0, 20, true);
            else updateAmount(req.ret, true);
          }
          resolve();
        },
      },
      { alive: () => p.isValid, skipWhen: () => p.isValid && p.isSneaking },
    );
    this.active.set(p.id, session);
    return { timeline: tl, done, skip: () => session.skip() };
  }

  /** Finishes a running celebration of `p` at once (e.g. the game opens a form now). */
  finishCelebration(p: Player): void {
    this.active.get(p.id)?.finishNow();
  }

  // ---------------------------------------------------------------------------------------------- sounds, particles

  /** Catalog sound for one player (`win_big`, `slots.reel_stop` …). */
  sound(p: Player, id: string, opts: SoundOpts = {}): boolean {
    return playCasinoSound(p, id, opts);
  }

  /** Positional sound for everyone nearby (table sounds). */
  soundAt(dim: Dimension, pos: Vector3, id: string, opts: Omit<SoundOpts, 'location'> = {}, radius = 24): number {
    return playAt(dim, pos, id, opts, radius);
  }

  /** Budgeted particle burst (see `particles.ts`). */
  burst(dim: Dimension, particle: string, at: Vector3, opts: BurstOpts = {}, budget?: ParticleBudget): number {
    return burst(dim, particle, at, opts, budget);
  }

  // ---------------------------------------------------------------------------------------------- small helpers

  /** Casino toast (global.md §4.10): action-bar baseline + `toast` sound; the JSON UI panel upgrades it (B11). */
  toast(p: Player, title: Raw, body?: Raw): void {
    this.hud.actionbar(p, 'core.toast', body ? join(color('§6', title), lit(' §7· §r'), body) : color('§6', title), PRIORITY_ALERT, 80);
    this.sound(p, 'toast');
  }

  /** Chaos event card baseline (global.md §4.6): mood-coloured title + mood sound; per-event FX are lane B-L3's. */
  chaos(p: Player, mood: ChaosMood, title: Raw, subtitle?: Raw): void {
    const c = mood === 'good' ? '§a' : mood === 'bad' ? '§c' : '§d';
    try {
      p.onScreenDisplay.setTitle(color(`${c}§l`, title), { fadeInDuration: 5, stayDuration: 50, fadeOutDuration: 10, subtitle });
    } catch {
      /* player left */
    }
    this.sound(p, mood === 'good' ? 'chaos_good' : mood === 'bad' ? 'chaos_bad' : 'toast');
  }

  /** Installed by the HUD panel (lane B-L2, JSON UI sentinel); without it a pulse is a no-op. */
  setHudPulseHandler(fn: (p: Player, segmentId: string) => void): void {
    this.pulse = fn;
  }

  /** Emphasise one HUD segment (colour/scale, never blinking — global.md §1.3). */
  hudPulse(p: Player, segmentId: string): void {
    this.pulse?.(p, segmentId);
  }

  /**
   * Title flipbook (extras-pvp.md BX1): frames ≥ 2 t apart (≤ 18), the last held `holdTicks`; reduce motion shows the
   * last frame only. `actionbar` = the V6 fallback (smaller, same frames). Resolves after the hold.
   */
  titleFlipbook(p: Player, frames: readonly Raw[], opts: { frameTicks?: number; holdTicks?: number; subtitle?: Raw; actionbar?: boolean } = {}): Promise<void> {
    const plan = planFlipbook(frames.length, opts.frameTicks ?? 2, fxSettings(p).reduceMotion);
    if (plan.length === 0) return Promise.resolve();
    const hold = Math.max(0, opts.holdTicks ?? 20);
    const b = Timeline.builder('fx.flipbook', 0).clock(LOCAL);
    for (const f of plan) b.add(f.tick * 50, 0, 'fx.flip', -1, f.frame);
    b.add((plan[plan.length - 1]!.tick + hold) * 50, 0, 'fx.flip_end', -1);
    const tl = b.build();
    const last = plan[plan.length - 1]!.frame;
    return new Promise<void>((resolve) => {
      playTimeline(
        tl,
        {
          beat: (beat) => {
            if (beat.kind !== 'fx.flip') return;
            const frame = frames[beat.args[0]!]!;
            const isLast = beat.args[0] === last;
            if (opts.actionbar) this.hud.actionbar(p, FX_CHANNEL, frame, PRIORITY_GAME, isLast ? hold + 10 : 10);
            else
              try {
                p.onScreenDisplay.setTitle(frame, { fadeInDuration: 0, stayDuration: isLast ? hold + 2 : 4, fadeOutDuration: isLast ? 10 : 0, subtitle: opts.subtitle });
              } catch {
                /* player left */
              }
          },
          end: () => resolve(),
        },
        { alive: () => p.isValid },
      );
    });
  }

  /** Action-bar count-up (balance tickers, payouts): `fmt(value)` every 2 t, exact last value. */
  actionbarRollUp(p: Player, channel: string, total: number, fmt: (amount: number) => Raw, durationMs: number): PresentationSession {
    const s = fxSettings(p);
    const dur = s.reduceMotion ? Math.min(300, durationMs) : Math.floor((durationMs * 100) / s.speedPct);
    const tl = Timeline.builder('fx.rollup', 0).clock(LOCAL).add(0, Math.max(0, dur), 'fx.rollup', -1).build();
    let shown = -1;
    const show = (v: number): void => {
      if (v === shown) return;
      shown = v;
      this.hud.actionbar(p, channel, fmt(v), PRIORITY_GAME, 40);
    };
    return playTimeline(tl, { frame: (tMs) => show(dur <= 0 ? total : rollUpValue(total, tMs / dur)), end: () => p.isValid && show(total) }, { alive: () => p.isValid });
  }

  /** A chat line that trails the landing (tables §0.6.3 "text trails the animation"). */
  chatAtLanding(p: Player, message: Raw, ticks: number): () => void {
    return revealAfter(ticks, () => {
      if (p.isValid) p.sendMessage(message);
    });
  }

  /**
   * World-load wiring: forget per-player state when players leave, and the operator preview scriptevent
   * `/scriptevent burmaldaholic:fx spike` (spike B-S0 harness) or `… demo <TIER> <ret> <stake> [slots]`.
   */
  start(enabled: () => boolean, log: (msg: string) => void = () => {}): void {
    world.afterEvents.playerLeave.subscribe((e) => {
      this.active.get(e.playerId)?.stop();
      this.active.delete(e.playerId);
      forgetSoundHistory(e.playerId);
    });
    system.afterEvents.scriptEventReceive.subscribe(
      (e) => {
        if (e.id !== FX_SCRIPT_EVENT || !enabled()) return;
        const src = e.sourceEntity;
        if (!src || src.typeId !== 'minecraft:player') return;
        const p = src as Player;
        if (p.playerPermissionLevel < PlayerPermissionLevel.Operator) return;
        const [cmd, tier, ret, stake, table] = e.message.trim().split(/\s+/);
        if (cmd === 'spike') void runDduiSpike(p, this, log).catch((err: unknown) => log(`spike failed: ${String(err)}`));
        else if (cmd === 'demo' && tier && (WIN_TIERS as readonly string[]).includes(tier)) {
          this.celebrate(p, { tier: tier as WinTier, ret: Number(ret ?? 0) || 0, stake: Number(stake ?? 1) || 1, game: 'demo', seed: 0, table: table === 'slots' ? SLOT_TIERS : DEFAULT_TIERS });
        }
      },
      { namespaces: ['burmaldaholic'] },
    );
  }
}
