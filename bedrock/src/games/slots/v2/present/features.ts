/**
 * Slots v2 features in the form (animation/slots.md §6.2 "Nether tumbles … Dragon Wheel", task BS4; lane B-L9).
 * PURE: the whole machine screen at timeline time t — reels (frames.ts) or a feature board (Piglin's Hoard,
 * Dragon Wheel, Treasure Hunt), the header line H (tier word / feature banner / counters) and the status line
 * S (roll-ups) — plus the sound/particle cues between two frames. The DDUI form and the classic fallback only
 * render what this file returns, so both show the same thing and the fidelity tests cover both.
 *
 * Honesty: every board value is read from the tape at the moment its beat reveals it (F6/F7): hoard coins
 * appear only when their respin lands, the wheel lands on the tape's wedge, a hunt chest shows entry i only
 * when chest i is opened (the i-th pick reveals the i-th entry, SLOTS.md §1.2).
 */
import { ease } from '../../../../core/logic/anim/ease';
import { rollUpValue, tickPitch } from '../../../../core/logic/anim/rollup';
import { type Beat, type Timeline, type TimingProfile, beatEnd, scaleMs } from '../../../../core/logic/anim/timeline';
import { type WinTier, tierOrdinal } from '../../../../core/logic/anim/win-tier';
import { type Raw, chips, join, lines, lit, plural, t } from '../../../../core/logic/rawtext';
import { SLOT_BEAT } from '../logic/timeline';
import { REELS, ROWS } from '../logic/types';
import { JACKPOT_TIER_KEYS, SLOT_PARTICLE, TIER_WORD_KEYS, rollupPoint, slotCelebration, tierColor, tierWordAt } from './celebrate';
import {
  type FrameOptions,
  type ReelFrame,
  type SlotRound,
  DEFAULT_FRAME_OPTIONS,
  MACHINE_GLYPH_BASE,
  PLANE_BASE,
  PLANE_WIN,
  SLOT_GLYPH,
  SYMBOL_CODES,
  CELL_GAP,
  cellIndex,
  isReturned,
  onPlane,
  popcount,
  reelFrame,
  renderRows,
  terminalFrame,
} from './frames';

const K = 'gui.burmaldaholic.slots.';

export type Board = 'reels' | 'hoard' | 'wheel' | 'hunt';

export interface SlotScreen {
  readonly board: Board;
  /** glyph rows of label R (no letters except translated wedge words on the wheel) */
  readonly rows: Raw;
  /** header line H (empty = undefined) */
  readonly header?: Raw;
  /** status line S */
  readonly status?: Raw;
}

export type ScreenOptions = FrameOptions;

// ---------------------------------------------------------------------------------------------------------
// Timeline helpers
// ---------------------------------------------------------------------------------------------------------

const active = (b: Beat, now: number): boolean => now >= b.at && now < b.at + Math.max(1, b.dur);

function find(tl: Timeline, kind: string, now: number, pred: (b: Beat) => boolean = () => true): Beat | undefined {
  let hit: Beat | undefined;
  for (const b of tl.beats) {
    if (b.at > now) break;
    if (b.kind === kind && pred(b) && active(b, now)) hit = b;
  }
  return hit;
}

function lastStarted(tl: Timeline, kind: string, now: number): Beat | undefined {
  let hit: Beat | undefined;
  for (const b of tl.beats) {
    if (b.at > now) break;
    if (b.kind === kind) hit = b;
  }
  return hit;
}

function count(tl: Timeline, kind: string, now: number): number {
  let n = 0;
  for (const b of tl.beats) {
    if (b.at > now) break;
    if (b.kind === kind) n++;
  }
  return n;
}

const all = (tl: Timeline, kind: string): Beat[] => tl.beats.filter((b) => b.kind === kind);
const progress = (b: Beat, now: number): number => (b.dur <= 0 ? 1 : Math.max(0, Math.min(1, (now - b.at) / b.dur)));
const chipsOf = (round: SlotRound, fifths: number): number => (fifths * round.bet) / 5;

// ---------------------------------------------------------------------------------------------------------
// Free spins bookkeeping
// ---------------------------------------------------------------------------------------------------------

interface FsInfo {
  /** FS_SPIN beats */
  starts: Beat[];
  /** per free spin: the time its window has fully landed */
  landed: number[];
  intro?: Beat;
  outro?: Beat;
}

const fsCache = new WeakMap<Timeline, FsInfo>();

function fsInfo(tl: Timeline): FsInfo {
  const hit = fsCache.get(tl);
  if (hit) return hit;
  const starts = all(tl, SLOT_BEAT.FS_SPIN);
  const landed = starts.map((s, i) => {
    const next = starts[i + 1]?.at ?? Number.POSITIVE_INFINITY;
    let end = s.at;
    for (const b of tl.beats) if (b.kind === SLOT_BEAT.REEL_LAND && b.at >= s.at && b.at < next) end = Math.max(end, beatEnd(b));
    return end;
  });
  const info = { starts, landed, intro: all(tl, SLOT_BEAT.FS_INTRO)[0], outro: all(tl, SLOT_BEAT.FS_OUTRO)[0] };
  fsCache.set(tl, info);
  return info;
}

/** Free-spin total (chips) shown at time `now`: each spin's pay rolls in over `WIN_ROLL_MS` after its window lands. */
export const WIN_ROLL_MS = 900;
function fsTotalAt(round: SlotRound, tl: Timeline, now: number): number {
  const info = fsInfo(tl);
  let total = 0;
  round.free.forEach((spin, i) => {
    const at = info.landed[i];
    if (at === undefined || now < at) return;
    const c = chipsOf(round, spin.payFifths);
    total += now >= at + WIN_ROLL_MS ? c : rollUpValue(c, (now - at) / WIN_ROLL_MS);
  });
  return total;
}

const fsTotalChips = (round: SlotRound): number => chipsOf(round, round.tape.freeSpins?.payFifths ?? round.free.reduce((s, x) => s + x.payFifths, 0));

// ---------------------------------------------------------------------------------------------------------
// Piglin's Hoard board
// ---------------------------------------------------------------------------------------------------------

export const HOARD_EMBER_MS = 500;
export const HOARD_LAND_SPAN_MS = 300;
export const HOARD_NEW_COIN_MS = 300;

/** Reading order (row-major) → cell index (reel × 3 + row). */
export const readingOrder = (q: number): number => cellIndex(q % REELS, Math.floor(q / REELS));

interface HoardCell {
  value: number;
  landedAt: number;
}

/** Hoard cells known at time `now` (only coins whose respin has landed), with respins left and the collect sweep. */
export function hoardState(round: SlotRound, tl: Timeline, now: number): { cells: Map<number, HoardCell>; respinsLeft: number; spinning: Set<number>; collected: number; lit: Set<number> } {
  const h = round.tape.hoard!;
  const cells = new Map<number, HoardCell>();
  const intro = all(tl, SLOT_BEAT.BONUS_INTRO).find((b) => b.args[0] === 2) ?? all(tl, SLOT_BEAT.BONUS_INTRO)[0];
  const introAt = intro?.at ?? 0;
  h.initialCells.forEach((c, i) => cells.set(c, { value: h.initialValues[i] ?? 0, landedAt: introAt }));
  const spinning = new Set<number>();
  let respinsLeft = 3;
  all(tl, SLOT_BEAT.HOARD_RESPIN).forEach((b, i) => {
    if (b.at > now) return;
    const newCells = h.respinCells[i] ?? [];
    const values = h.respinValues[i] ?? [];
    const empties: number[] = [];
    for (let q = 0; q < REELS * ROWS; q++) if (!cells.has(readingOrder(q))) empties.push(readingOrder(q));
    const landAll = b.at + Math.min(HOARD_EMBER_MS + HOARD_LAND_SPAN_MS, b.dur);
    empties.forEach((c, q) => {
      const at = b.at + Math.min(HOARD_EMBER_MS, b.dur) + Math.floor((Math.min(HOARD_LAND_SPAN_MS, b.dur) * q) / Math.max(1, empties.length));
      const k = newCells.indexOf(c);
      if (now < at) spinning.add(c);
      else if (k >= 0) cells.set(c, { value: values[k] ?? 0, landedAt: at });
    });
    if (now >= landAll || now >= beatEnd(b)) respinsLeft = newCells.length > 0 ? 3 : respinsLeft - 1;
  });
  const collect = all(tl, SLOT_BEAT.HOARD_COLLECT)[0];
  const lit = new Set<number>();
  let collected = 0;
  if (collect && now >= collect.at) {
    const order: number[] = [];
    for (let q = 0; q < REELS * ROWS; q++) if (cells.has(readingOrder(q))) order.push(readingOrder(q));
    const n = Math.max(1, order.length);
    order.forEach((c, q) => {
      if (now >= collect.at + Math.floor((collect.dur * q) / n)) {
        lit.add(c);
        const v = cells.get(c)!.value;
        if (v > 0) collected += v;
      }
    });
  }
  return { cells, respinsLeft: Math.max(0, respinsLeft), spinning, collected, lit };
}

const coinGlyph = (): number => MACHINE_GLYPH_BASE.nether + SYMBOL_CODES.nether.indexOf('CN');

function hoardCellText(value: number, plane: number): string {
  if (value < 0) return String.fromCodePoint(onPlane(SLOT_GLYPH.BADGE[Math.min(3, -value - 1)]!, plane));
  return String.fromCodePoint(onPlane(coinGlyph(), plane)) + `§6${value}×`;
}

function hoardScreen(round: SlotRound, tl: Timeline, now: number, o: ScreenOptions): SlotScreen {
  const s = hoardState(round, tl, now);
  const rows: string[] = [];
  for (let y = 0; y < ROWS; y++) {
    const parts: string[] = [];
    for (let r = 0; r < REELS; r++) {
      const c = cellIndex(r, y);
      const coin = s.cells.get(c);
      if (coin) {
        const fresh = !o.reduceMotion && now - coin.landedAt < HOARD_NEW_COIN_MS && coin.landedAt > 0;
        const plane = fresh || s.lit.has(c) ? PLANE_WIN : PLANE_BASE;
        parts.push('§f' + hoardCellText(coin.value, plane));
      } else if (s.spinning.has(c) && !o.reduceMotion) parts.push('§f' + String.fromCodePoint(SLOT_GLYPH.EMBER));
      else parts.push('§f' + String.fromCodePoint(SLOT_GLYPH.EMPTY));
    }
    rows.push(parts.join(CELL_GAP) + '§r');
  }
  const pips = String.fromCodePoint(SLOT_GLYPH.PIP_FULL).repeat(s.respinsLeft) + String.fromCodePoint(SLOT_GLYPH.PIP_EMPTY).repeat(3 - s.respinsLeft);
  const full = s.cells.size >= REELS * ROWS;
  const header = full ? t(`${K}hold.full`) : join(t(`${K}bonus.hold`), lit('  §e' + pips + '§r'));
  const collect = all(tl, SLOT_BEAT.HOARD_COLLECT)[0];
  const status = collect && now >= collect.at ? t(`${K}bonus.total`, chips(s.collected * round.bet)) : t(`${K}hold.coins`, s.cells.size);
  return { board: 'hoard', rows: lines(...rows.map((r) => lit(r))), header, status };
}

// ---------------------------------------------------------------------------------------------------------
// Dragon Wheel board
// ---------------------------------------------------------------------------------------------------------

/** Wedges per ring in clockwise order (SLOTS.md §3.3): n > 0 = n × bet, 0 = UP, −1…−4 = Mini…Grand. */
export const WHEEL_RINGS: readonly (readonly number[])[] = [
  [10, 0, 12, 15, -1, 10, 20, 12, 25, 10, 40, 15, 0, 12, 20, -1, 15, 10, 75, 25],
  [30, 50, -2, 75, 30, 100, 50, 0, 30, 75, -2, 50, 100, 30, 75, 50],
  [150, -3, 250, 150, -4, 250, -3, 150, 500, 250, -3, 150],
];
export const WHEEL_RING_KEYS = ['outer', 'middle', 'core'] as const;
/** Visible wedges (odd; the middle one is under the pointer). */
export const WHEEL_VISIBLE = 7;
/** Extra wedges travelled beyond two full turns (never ≡ 0, so a static reduce-motion ring does not spoil). */
export const WHEEL_EXTRA = 3;

/** Wedge index under the pointer at progress p ∈ [0, 1] of a spin that ends on `final` (outCubic). */
export function wheelCenter(ring: number, final: number, p: number, reduceMotion: boolean): number {
  const n = WHEEL_RINGS[ring]!.length;
  const travel = 2 * n + WHEEL_EXTRA;
  const done = p >= 1 ? travel : reduceMotion ? 0 : Math.floor(travel * ease('outCubic', p));
  return (((final - travel + done) % n) + n) % n;
}

function wedgeRaw(v: number): Raw {
  if (v === 0) return t(`${K}fx.wheel_up_wedge`);
  if (v < 0) return t(`${K}jackpot.tier.${JACKPOT_TIER_KEYS[Math.min(3, -v - 1)]}`);
  return lit(String(v) + '×');
}

/** Spin part of a WHEEL_SPIN beat (the rest is the result glow). */
export const wheelSpinMs = (b: Beat): number => b.dur - Math.min(600, Math.floor(b.dur / 5));

export function wheelState(round: SlotRound, tl: Timeline, now: number, o: ScreenOptions): { ring: number; center: number; landed: boolean; won: number[] } {
  const spins = all(tl, SLOT_BEAT.WHEEL_SPIN);
  const segs = round.tape.wheel?.segments ?? [];
  let ring = 0;
  let center = 0;
  let landed = false;
  const won: number[] = [];
  spins.forEach((b, i) => {
    if (b.at > now) return;
    ring = i;
    const final = segs[i] ?? 0;
    const p = Math.min(1, (now - b.at) / Math.max(1, wheelSpinMs(b)));
    center = wheelCenter(i, final, p, o.reduceMotion);
    landed = p >= 1;
    if (landed) won.push(WHEEL_RINGS[i]![final]!);
  });
  if (spins.length === 0 || spins[0]!.at > now) center = wheelCenter(0, segs[0] ?? 0, 0, true);
  return { ring, center, landed, won };
}

function wheelScreen(round: SlotRound, tl: Timeline, now: number, o: ScreenOptions): SlotScreen {
  const s = wheelState(round, tl, now, o);
  const ring = WHEEL_RINGS[s.ring]!;
  const n = ring.length;
  const half = (WHEEL_VISIBLE - 1) / 2;
  const parts: Raw[] = [lit('│')];
  for (let k = -half; k <= half; k++) {
    const v = ring[(((s.center + k) % n) + n) % n]!;
    const hot = k === 0 && s.landed;
    parts.push(lit(hot ? '§e§l' : k === 0 ? '§f' : '§7'), wedgeRaw(v), lit('§r│'));
  }
  const pointer = lit(`${' '.repeat(3 * half * 2)}§e${String.fromCodePoint(SLOT_GLYPH.POINTER)}§r`);
  const up = find(tl, SLOT_BEAT.WHEEL_UP, now);
  const header = up ? t(`${K}wheel.up`) : join(t(`${K}bonus.wheel`), lit(' · '), t(`${K}wheel.ring.${WHEEL_RING_KEYS[s.ring]}`));
  const prize = s.won.filter((v) => v > 0).reduce((a, b) => a + b, 0);
  const status = s.landed || s.won.length > 0 ? t(`${K}bonus.total`, chips(prize * round.bet)) : undefined;
  return { board: 'wheel', rows: lines(pointer, join(...parts)), header, status };
}

// ---------------------------------------------------------------------------------------------------------
// Treasure Hunt board (interactive: driven by picks, not by the spin timeline)
// ---------------------------------------------------------------------------------------------------------

export const HUNT_OPEN_MS = 300;
export const HUNT_CREEPER_MS = 1500;
export const HUNT_SWELL_BLINK_MS = 500;
export const HUNT_END_STEP_MS = 100;

export interface HuntView {
  /** chests opened so far (entry i revealed by the i-th pick) */
  readonly opened: number;
  /** the chest being opened now and the ms since its reveal was confirmed (D6) */
  readonly opening?: { readonly i: number; readonly ms: number };
  /** ms since the hunt ended (creeper or all opened): the rest opens dimmed, 2 per frame */
  readonly endMs?: number;
}

const huntPrize = (round: SlotRound, e: number): Raw => (e < 0 ? t(`${K}jackpot.tier.${JACKPOT_TIER_KEYS[Math.min(3, -e - 1)]}`) : chips(e * round.bet));

/** The hunt board at a pick state. Reads entries only up to the chest being opened (F7), then the rest at the end. */
export function huntScreen(round: SlotRound, v: HuntView, o: ScreenOptions = DEFAULT_FRAME_OPTIONS): SlotScreen {
  const entries = round.tape.hunt?.entries ?? [];
  const rows: string[] = [];
  const revealEnd = v.endMs === undefined ? -1 : Math.floor(v.endMs / HUNT_END_STEP_MS) * 2;
  const creeperAt = entries.slice(0, v.opened + (v.opening ? 1 : 0)).indexOf(0);
  for (let y = 0; y < ROWS; y++) {
    const parts: string[] = [];
    for (let r = 0; r < REELS; r++) {
      const q = y * REELS + r;
      let g: number = SLOT_GLYPH.CHEST_CLOSED;
      let plane = PLANE_BASE;
      let tint = '§f';
      if (q < v.opened) g = entries[q] === 0 ? SLOT_GLYPH.CREEPER : SLOT_GLYPH.CHEST_OPEN;
      else if (v.opening && q === v.opening.i) {
        const ms = v.opening.ms;
        if (ms < 100 && !o.reduceMotion) plane = PLANE_WIN;
        else if (ms >= 200 || o.reduceMotion) {
          g = entries[q] === 0 ? SLOT_GLYPH.CREEPER : SLOT_GLYPH.CHEST_OPEN;
          if (entries[q] === 0 && !o.reduceMotion && ms < 200 + HUNT_CREEPER_MS) tint = Math.floor((ms - 200) / HUNT_SWELL_BLINK_MS) % 2 === 0 ? '§f' : '§7';
        }
      } else if (revealEnd >= 0 && q - v.opened < revealEnd) {
        g = SLOT_GLYPH.CHEST_OPEN;
        tint = '§8';
      }
      if (creeperAt >= 0 && q === creeperAt && q < v.opened) g = SLOT_GLYPH.CREEPER;
      parts.push(tint + String.fromCodePoint(onPlane(g, plane)));
    }
    rows.push(parts.join(CELL_GAP) + '§r');
  }
  const shown = v.opening ? v.opening.ms >= 200 || o.reduceMotion : true;
  const last = v.opening && shown ? v.opening.i : v.opened - 1;
  const lastEntry = last >= 0 ? entries[last] : undefined;
  let header: Raw = t(`${K}bonus.pick`);
  let status: Raw = t(`${K}pick.hint`);
  if (lastEntry !== undefined) {
    if (lastEntry === 0) {
      const swellDone = !v.opening || o.reduceMotion || v.opening.ms >= 200 + HUNT_CREEPER_MS;
      if (swellDone) header = t(`${K}pick.creeper`);
      status = t(`${K}pick.opened`, last + 1);
    } else status = t(`${K}pick.prize`, huntPrize(round, lastEntry));
  }
  return { board: 'hunt', rows: lines(...rows.map((r) => lit(r))), header, status };
}

/** The pick after `opened` is the next chest in reading order; the hunt ends on a Creeper or after 15. */
export function huntOver(round: SlotRound, opened: number): boolean {
  const e = round.tape.hunt?.entries ?? [];
  return opened >= Math.min(15, e.length) || e.slice(0, opened).includes(0);
}

/** Local timeline of one chest opening (after the server confirmed the reveal, D6). */
export function huntOpenMs(entry: number, local: TimingProfile): number {
  return scaleMs(local, HUNT_OPEN_MS + (entry === 0 ? HUNT_CREEPER_MS : 0));
}

/** Duration of the end reveal (remaining chests open dimmed, 2 per frame). */
export const huntEndMs = (remaining: number): number => Math.ceil(remaining / 2) * HUNT_END_STEP_MS;

// ---------------------------------------------------------------------------------------------------------
// The screen
// ---------------------------------------------------------------------------------------------------------

const TIER_COLOR_PERIOD_MS = 500;

/** Header with the tier word for a rolled amount (Nice+ only; alternates §6 ↔ §e every 10 t, not a flash). */
function tierHeader(round: SlotRound, shown: number, now: number, o: ScreenOptions, final: boolean): Raw | undefined {
  const c = slotCelebration(round.totalChips, round.bet, round.tier, round.tape.capHit);
  const tier: WinTier = final ? c.tier : tierWordAt(c, shown);
  if (final && round.tape.capHit) return join(lit('§6§l'), t(`${K}max_win`), lit('§r'));
  if (tierOrdinal(tier) < tierOrdinal('NICE') || tier === 'JACKPOT') return undefined;
  const alt = !o.reduceMotion && Math.floor(now / TIER_COLOR_PERIOD_MS) % 2 === 1;
  return join(lit(alt ? '§e§l' : tierColor(tier)), t(TIER_WORD_KEYS[tier]), lit('§r'));
}

function spinStatus(round: SlotRound, shown: number, final: boolean): Raw | undefined {
  if (round.totalChips <= 0) return final ? t(`${K}no_win`) : undefined;
  if (round.totalChips < round.bet) return join(lit('§7'), t(`${K}returned`, chips(shown)), lit('§r'));
  return join(lit('§a'), t(`${K}win`, chips(shown)), lit('§r'));
}

function reelsRows(f: ReelFrame, o: ScreenOptions): Raw {
  return lines(...renderRows(f, o.tinting).map((r) => lit(r)));
}

/** The machine screen at timeline time `now` (ms). `now ≥ tl.endMs()` gives the settled screen (== `terminalScreen`). */
export function screenAt(round: SlotRound, tl: Timeline, now: number, o: ScreenOptions = DEFAULT_FRAME_OPTIONS): SlotScreen {
  const end = tl.endMs();
  if (now >= end) return terminalScreen(round, o);

  // jackpots (local, after the spin's steps)
  const jp = find(tl, SLOT_BEAT.JACKPOT, now);
  if (jp) {
    const idx = all(tl, SLOT_BEAT.JACKPOT).indexOf(jp);
    const award = round.tape.jackpots[idx];
    const tier = award?.tier ?? jp.args[0] ?? 1;
    const badge = String.fromCodePoint(SLOT_GLYPH.BADGE[Math.min(3, tier - 1)]!);
    const amount = award ? rollUpValue(award.chips, o.reduceMotion ? 1 : Math.min(1, (now - jp.at) / Math.max(1, jp.dur * 0.6))) : 0;
    return {
      board: 'reels',
      rows: reelsRows(terminalFrame(round), o),
      header: join(lit(badge + ' §e§l'), t(`${K}jackpot.won`, t(`${K}jackpot.tier.${JACKPOT_TIER_KEYS[Math.min(3, tier - 1)]}`)), lit('§r')),
      status: join(lit('§6'), chips(amount), lit('§r')),
    };
  }

  // spin roll-up (local): tier word upgrades as the amount passes the thresholds
  const roll = lastStarted(tl, SLOT_BEAT.ROLLUP, now);
  if (roll) {
    const pt = rollupAt(round, tl, roll, now, o);
    // the way cycle runs next to the roll-up (local beats); once the amount has settled it takes the status line
    const cycle = now >= beatEnd(roll) && find(tl, SLOT_BEAT.WAY_CYCLE, now) ? baseStatus(round, tl, now) : undefined;
    return { board: 'reels', rows: reelsRows(terminalFrame(round), o), header: tierHeader(round, pt.esc, now, o, false), status: cycle ?? spinStatus(round, pt.shown, false) };
  }

  const maxWin = find(tl, SLOT_BEAT.MAX_WIN, now);
  const fs = fsInfo(tl);

  // bonus boards
  const intro = lastStarted(tl, SLOT_BEAT.BONUS_INTRO, now);
  const firstFs = fs.intro ?? fs.starts[0];
  const inBonus = intro && (!firstFs || now < firstFs.at || firstFs.at < intro.at);
  if (inBonus && !maxWin) {
    if (round.tape.hoard && (intro.args[0] === 2 || (!round.tape.wheel && !round.tape.hunt))) return hoardScreen(round, tl, now, o);
    if (round.tape.wheel && (intro.args[0] === 3 || !round.tape.hunt)) return wheelScreen(round, tl, now, o);
    if (round.tape.hunt) return huntScreen(round, { opened: 0 }, o);
  }

  const frame = reelFrame(round, tl, now, o);
  const rows = reelsRows(frame, o);
  if (maxWin) return { board: 'reels', rows, header: join(lit('§6§l'), t(`${K}max_win`), lit('§r')), status: t(`${K}fs.total`, chips(fsTotalAt(round, tl, now))) };

  // free spins
  if (fs.outro && active(fs.outro, now)) {
    const total = fsTotalChips(round);
    const rollMs = Math.max(1, fs.outro.dur - 1500);
    const shown = o.reduceMotion ? total : rollUpValue(total, (now - fs.outro.at) / rollMs);
    return { board: 'reels', rows, header: join(lit('§6§l'), t(`${K}fs.end`, chips(shown)), lit('§r')), status: t(`${K}fs.total`, chips(shown)) };
  }
  const re = find(tl, SLOT_BEAT.FS_RETRIGGER, now);
  if (re) return { board: 'reels', rows, header: join(lit('§d§l'), t(`${K}fs.retrigger`, re.args[0] ?? round.fsRetrigger), lit('§r')), status: t(`${K}fs.total`, chips(fsTotalAt(round, tl, now))) };
  if (fs.intro && active(fs.intro, now)) {
    return {
      board: 'reels',
      rows,
      header: join(lit('§6§l'), t(`${K}fs.title`), lit('§r · §e'), t(`${K}fs.name.${round.machine}`), lit('§r')),
      status: t(`${K}fs.awarded`, round.fsAwarded),
    };
  }
  const fsIdx = count(tl, SLOT_BEAT.FS_SPIN, now) - 1;
  if (fsIdx >= 0 && (!fs.outro || now < fs.outro.at)) {
    const spin = round.free[fsIdx];
    const retriggers = all(tl, SLOT_BEAT.FS_RETRIGGER).filter((b) => b.at <= now).length;
    const totalSpins = Math.min(round.free.length, round.fsAwarded + retriggers * round.fsRetrigger);
    const parts: Raw[] = [t(`${K}fs.left`, fsIdx + 1, Math.max(fsIdx + 1, totalSpins))];
    if (round.machine === 'nether') {
      const k = Math.min(count(tl, SLOT_BEAT.TUMBLE_EXPLODE, now) - countBefore(tl, SLOT_BEAT.TUMBLE_EXPLODE, fs.starts[fsIdx]!.at), (spin?.evals.length ?? 1) - 1);
      parts.push(lit(' · '), t(`${K}fs.multiplier`, round.ladderFree[Math.min(Math.max(0, k), round.ladderFree.length - 1)] ?? 1));
    }
    if (round.machine === 'end' && spin) {
      const expands = all(tl, SLOT_BEAT.WILD_EXPAND).filter((b) => b.at >= fs.starts[fsIdx]!.at && beatEnd(b) <= now).length;
      const sticky = popcount(spin.stickyBefore) + Math.min(expands, popcount(spin.stickyAfter & ~spin.stickyBefore));
      parts.push(lit(' · '), t(`${K}sticky`, sticky));
    }
    return { board: 'reels', rows, header: join(...parts), status: t(`${K}fs.total`, chips(fsTotalAt(round, tl, now))) };
  }

  // base game: win show, way cycle, tumbles
  return { board: 'reels', rows, header: undefined, status: baseStatus(round, tl, now) };
}

function countBefore(tl: Timeline, kind: string, now: number): number {
  let n = 0;
  for (const b of tl.beats) if (b.kind === kind && b.at < now) n++;
  return n;
}

/** End of the base game's beats: the first feature beat (free spins or a bonus game), else infinity. */
function baseEndMs(tl: Timeline): number {
  for (const b of tl.beats) if (b.kind === SLOT_BEAT.FS_INTRO || b.kind === SLOT_BEAT.FS_SPIN || b.kind === SLOT_BEAT.BONUS_INTRO) return b.at;
  return Number.POSITIVE_INFINITY;
}

/** Amount of the base Win line at `now` (completed evaluations + the current one rolling over its WIN_SHOW). */
function baseWinShown(round: SlotRound, tl: Timeline, now: number): number | undefined {
  const spin = round.base;
  if (!spin || isReturned(round)) return undefined;
  const baseEnd = baseEndMs(tl);
  const cut = Math.min(now, baseEnd);
  const shows = all(tl, SLOT_BEAT.WIN_SHOW).filter((b) => b.at <= cut && b.at < baseEnd);
  const j = shows.length - 1;
  if (j < 0) return undefined;
  let done = 0;
  for (let x = 0; x < j && x < spin.evals.length; x++) done += chipsOf(round, spin.evals[x]!.payFifths);
  const cur = chipsOf(round, spin.evals[Math.min(j, spin.evals.length - 1)]!.payFifths);
  return done + rollUpValue(cur, progress(shows[j]!, cut));
}

/**
 * The Win line the ROLLUP beat starts from (F5 continuity): what the base win show last put on screen, capped
 * at the spin total. 0 for bought features and Returned spins.
 */
export function rollupFrom(round: SlotRound, tl: Timeline, roll: Beat): number {
  // at the roll-up start the last base WIN_SHOW has just ended (the builder puts the gate at its end)
  return Math.max(0, Math.min(round.totalChips, baseWinShown(round, tl, roll.at) ?? 0));
}

/** Win line (`shown`) and tier-word driver (`esc`) of the spin roll-up at `now`. */
export function rollupAt(round: SlotRound, tl: Timeline, roll: Beat, now: number, o: ScreenOptions): { shown: number; esc: number } {
  const c = slotCelebration(round.totalChips, round.bet, round.tier, round.tape.capHit);
  const p = o.reduceMotion ? (now >= roll.at + roll.dur / 2 ? 1 : 0) : progress(roll, now);
  return rollupPoint(c, rollupFrom(round, tl, roll), p);
}

/** "1 way" / «1 способ», "21 ways" / «21 способ», «3 способа», «5 способов». */
export const waysRaw = (n: number): Raw => plural(`${K}ways`, n);

function baseStatus(round: SlotRound, tl: Timeline, now: number): Raw | undefined {
  const spin = round.base;
  if (!spin || isReturned(round)) return undefined; // F9: the Returned line waits for the roll-up beat
  const cyc = find(tl, SLOT_BEAT.WAY_CYCLE, now);
  const shows = all(tl, SLOT_BEAT.WIN_SHOW).filter((b) => b.at <= now);
  const j = shows.length - 1;
  const k = count(tl, SLOT_BEAT.TUMBLE_EXPLODE, now);
  const ev = spin.evals[Math.max(0, Math.min(j, spin.evals.length - 1))];
  if (cyc && ev) {
    const sym = cyc.args[0] ?? ev.wins[0]?.symbol ?? 0;
    const wins = ev.wins.filter((w) => w.symbol === sym);
    const kk = Math.max(0, ...wins.map((w) => w.k));
    const ways = wins.reduce((s, w) => s + w.ways, 0);
    const pay = wins.reduce((s, w) => s + w.payFifths, 0);
    return t(`${K}symbol_win`, t(`${K}symbol.${round.symbolIds[sym] ?? 'creeper'}`), kk, waysRaw(ways), chips(chipsOf(round, pay * ev.multiplier)));
  }
  if (j < 0) return undefined;
  // running base win: completed evaluations + the current one rolling in over its WIN_SHOW
  const shown = baseWinShown(round, tl, now) ?? 0;
  const win = join(lit('§a'), t(`${K}win`, chips(shown)), lit('§r'));
  if (round.machine === 'nether' && k > 0) return join(win, lit(' · '), t(`${K}tumble.mult`, spin.evals[Math.min(k, spin.evals.length - 1)]!.multiplier));
  return win;
}

/** The settled screen: last window with its win glow, final tier word and exact amounts (F5, F8). */
export function terminalScreen(round: SlotRound, o: ScreenOptions = DEFAULT_FRAME_OPTIONS): SlotScreen {
  return {
    board: 'reels',
    rows: reelsRows(terminalFrame(round), o),
    header: tierHeader(round, round.totalChips, 0, { ...o, reduceMotion: true }, true),
    status: spinStatus(round, round.totalChips, true),
  };
}

// ---------------------------------------------------------------------------------------------------------
// Cues (sounds, music, particles) between two frames
// ---------------------------------------------------------------------------------------------------------

export type Cue =
  | { readonly kind: 'sound'; readonly id: string; readonly pitch: number; readonly volume: number; readonly world?: boolean }
  | { readonly kind: 'stop'; readonly id: string }
  | { readonly kind: 'loop'; readonly id: string; readonly on: boolean }
  | { readonly kind: 'music'; readonly id: string; readonly on: boolean }
  | { readonly kind: 'particle'; readonly id: string; readonly count: number };

/** Reel stops walk up C major pentatonic (animation/slots.md §8). */
export const REEL_PITCH = [1.0, 1.12, 1.26, 1.5, 1.68] as const;
export const SCATTER_PITCH = [1.0, 1.26, 1.5, 1.68, 2.0] as const;
export const ANTIC_PITCH = [1.0, 1.15, 1.3] as const;
export const JACKPOT_PITCH = [1.3, 1.15, 1.0, 1.0] as const;
/** Roll-up ticks ≤ 15/s (frames are 10/s, so every frame may tick). */
export const ROLLUP_TICK_MS = 100;

const snd = (id: string, pitch = 1, volume = 1, world = false): Cue => ({ kind: 'sound', id, pitch, volume, world });

/** Cues for (t0, t1]; pass t0 = −1 on the first frame. Pure and allocation-light (called every 2 t). */
export function cuesBetween(round: SlotRound, tl: Timeline, t0: number, t1: number, o: ScreenOptions = DEFAULT_FRAME_OPTIONS): Cue[] {
  const out: Cue[] = [];
  const within = (x: number): boolean => x > t0 && x <= t1;
  let scatterN = 0;
  let anticN = 0;
  const returned = isReturned(round);
  for (const b of tl.beats) {
    const start = within(b.at);
    const end = beatEnd(b);
    switch (b.kind) {
      case SLOT_BEAT.SPIN_UP:
        if (start) out.push({ kind: 'loop', id: 'slots.spin_loop', on: true });
        // the scatter / anticipation pitch ladders restart with every spin
        scatterN = 0;
        anticN = 0;
        break;
      case SLOT_BEAT.REEL_LAND:
        if (within(end)) {
          out.push(snd('slots.reel_stop', REEL_PITCH[b.lane] ?? 1));
          if (b.lane === REELS - 1 || !tl.beats.some((x) => x.kind === SLOT_BEAT.REEL_LAND && x.at > b.at && x.at < end + 2000)) out.push({ kind: 'loop', id: 'slots.spin_loop', on: false });
        }
        break;
      case SLOT_BEAT.SYMBOL_LAND: {
        const role = round.roles[b.args[1] ?? -1];
        if (role === 'SCATTER') {
          if (start) out.push(snd('slots.scatter_land', SCATTER_PITCH[Math.min(scatterN, 4)]!));
          scatterN++;
        } else if (start) out.push(snd('slots.bonus_land'));
        break;
      }
      case SLOT_BEAT.ANTICIPATE:
        if (start) out.push(snd('slots.anticipation', ANTIC_PITCH[Math.min(anticN, 2)]!));
        if (within(end)) out.push({ kind: 'stop', id: 'slots.anticipation' });
        anticN++;
        break;
      case SLOT_BEAT.WIN_SHOW:
        if (within(end) && !returned) out.push(snd('slots.win_small', 1, 0.6));
        break;
      case SLOT_BEAT.WAY_CYCLE:
        if (start && !returned) out.push(snd('slots.win_small', 1, 0.4));
        break;
      case SLOT_BEAT.TUMBLE_EXPLODE:
        if (start) out.push(snd('slots.tumble'), { kind: 'particle', id: SLOT_PARTICLE.ember, count: o.reduceMotion ? 4 : 12 });
        break;
      case SLOT_BEAT.MULT_UP:
        if (start) out.push(snd('slots.mult_up', REEL_PITCH[Math.min(4, Math.max(0, b.args[0] === undefined ? 0 : round.ladder.indexOf(b.args[0])))] ?? 1));
        break;
      case SLOT_BEAT.WILD_EXPAND:
        if (start) out.push(snd('slots.wild_expand'), { kind: 'particle', id: SLOT_PARTICLE.voidMotes, count: o.reduceMotion ? 3 : 8 });
        break;
      case SLOT_BEAT.WILD_STICK:
        if (start) out.push(snd('slots.wild_stick'));
        break;
      case SLOT_BEAT.FS_INTRO:
        if (start) out.push(snd('slots.fs_intro'), { kind: 'music', id: `slots.fs_music.${round.machine}`, on: true });
        break;
      case SLOT_BEAT.FS_RETRIGGER:
        if (start) out.push(snd('slots.fs_intro', 1.2));
        break;
      case SLOT_BEAT.FS_OUTRO:
        if (start) out.push(snd('slots.fs_outro'), { kind: 'music', id: `slots.fs_music.${round.machine}`, on: false });
        break;
      case SLOT_BEAT.BONUS_INTRO:
        if (start) out.push(snd(b.args[0] === 3 ? 'slots.wheel_up' : 'slots.bonus_land', 1.2));
        break;
      case SLOT_BEAT.HOARD_RESPIN:
        if (start || (t0 < end && t1 > b.at)) hoardCues(round, tl, b, t0, t1, out);
        break;
      case SLOT_BEAT.WHEEL_SPIN:
        if (t1 > b.at && t0 < end) wheelCues(round, tl, b, t0, t1, o, out);
        break;
      case SLOT_BEAT.WHEEL_UP:
        if (start) out.push(snd('slots.wheel_up'));
        break;
      case SLOT_BEAT.JACKPOT:
        if (start) out.push(snd('jackpot', JACKPOT_PITCH[Math.min(3, (b.args[0] ?? 1) - 1)]!, 1, true));
        break;
      case SLOT_BEAT.MAX_WIN:
        if (start) out.push(snd('slots.max_win', 1, 1, true));
        break;
      case SLOT_BEAT.ROLLUP:
        if (t1 > b.at && t0 < end) rollupCues(round, tl, b, t0, t1, o, out);
        break;
      default:
        break;
    }
  }
  return out;
}

function hoardCues(round: SlotRound, tl: Timeline, b: Beat, t0: number, t1: number, out: Cue[]): void {
  const before = hoardState(round, tl, Math.max(b.at - 1, t0)).cells.size;
  const after = hoardState(round, tl, t1).cells.size;
  if (after > before && before >= 0 && t0 >= b.at - 1) out.push(snd('slots.coin_land'), snd('slots.respin_reset'));
}

function wheelCues(round: SlotRound, tl: Timeline, b: Beat, t0: number, t1: number, o: ScreenOptions, out: Cue[]): void {
  const ring = all(tl, SLOT_BEAT.WHEEL_SPIN).indexOf(b);
  const final = round.tape.wheel?.segments[ring] ?? 0;
  const ms = wheelSpinMs(b);
  const p0 = Math.max(0, Math.min(1, (t0 - b.at) / ms));
  const p1 = Math.max(0, Math.min(1, (t1 - b.at) / ms));
  if (p1 <= p0) return;
  if (wheelCenter(ring, final, p0, o.reduceMotion) !== wheelCenter(ring, final, p1, o.reduceMotion)) out.push(snd('slots.wheel_tick', 0.9 + 0.5 * p1, 0.5));
  if (p0 < 1 && p1 >= 1) out.push(snd('wheel_stop'));
}

/** Upgrade stems of the slot tiers (slots.md §4.12). */
export const TIER_STEM: Partial<Record<WinTier, string>> = { NICE: 'slots.win_nice', BIG: 'slots.big_win', MEGA: 'slots.mega_win', EPIC: 'slots.epic_win' };

function rollupCues(round: SlotRound, tl: Timeline, b: Beat, t0: number, t1: number, o: ScreenOptions, out: Cue[]): void {
  const total = round.totalChips;
  const c = slotCelebration(total, round.bet, round.tier, round.tape.capHit);
  const from = rollupFrom(round, tl, b);
  const at = (x: number): { shown: number; esc: number } => {
    const p = x < b.at ? 0 : o.reduceMotion ? (x >= b.at + b.dur / 2 ? 1 : 0) : progress(b, x);
    return rollupPoint(c, from, p);
  };
  const first = at(b.at);
  if (b.at > t0 && b.at <= t1) {
    if (total < round.bet) {
      out.push(snd('slots.returned', 0.8, 0.3));
      return;
    }
    const w = tierWordAt(c, first.esc);
    const stem = TIER_STEM[w];
    if (stem) out.push(snd(stem, 1, 1, tierOrdinal(w) >= tierOrdinal('MEGA')));
  }
  if (total < round.bet) return;
  const a0 = b.at > t0 ? first : at(t0);
  const a1 = at(t1);
  const p1 = o.reduceMotion ? (t1 >= b.at + b.dur / 2 ? 1 : 0) : progress(b, t1);
  const p0 = b.at > t0 ? 0 : o.reduceMotion ? (t0 >= b.at + b.dur / 2 ? 1 : 0) : progress(b, t0);
  // ticks only while the amount really moves (no recount when the win show already reached the total)
  if (a1.shown > a0.shown && p1 < 1) out.push(snd('slots.rollup_tick', tickPitch(Math.floor((t1 - b.at) / ROLLUP_TICK_MS)), 0.35));
  for (const [tier, amount] of c.upgrades) if (a0.esc < amount && a1.esc >= amount && TIER_STEM[tier]) out.push(snd(TIER_STEM[tier]!, 1, 1, tierOrdinal(tier) >= tierOrdinal('MEGA')));
  if (p0 < 1 && p1 >= 1) out.push(snd(tierOrdinal(c.tier) >= tierOrdinal('BIG') ? 'slots.rollup_end' : 'slots.win_small'));
}

/** True while any reel of the current spin is still moving (Spin button shows Stop). */
export function reelsMoving(tl: Timeline, now: number): boolean {
  for (const b of tl.beats) {
    if (b.at > now + 2000) break;
    if (b.kind === SLOT_BEAT.REEL_LAND && now < beatEnd(b) && now >= b.at - 2000) return true;
  }
  return false;
}
