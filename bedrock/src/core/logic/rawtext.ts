/**
 * Rawtext builders. PURE: the RawMessage shape here is structurally compatible with
 * @minecraft/server RawMessage, so results can be passed straight to sendMessage,
 * setActionBar, setTitle, ActionFormData.title/body/button, etc.
 *
 * ALL player-facing text must be built with these helpers (scripts/check-strings.mjs).
 */
import { formatNumber } from './format';
import { pluralKey } from './plural';
import { type Rng, randInt } from './rng';

export interface Raw {
  rawtext?: Raw[];
  text?: string;
  translate?: string;
  with?: string[] | Raw;
  score?: { name?: string; objective?: string };
}

/** Argument for a placeholder: numbers/strings become literal text, Raw is nested (translated) text. */
export type Arg = string | number | Raw;

const toRaw = (a: Arg): Raw => (typeof a === 'object' ? a : { text: formatArg(a) });

/** Numbers are grouped per LOCALIZATION.md §4 (`12 500`); strings pass through unchanged. */
function formatArg(a: string | number): string {
  return typeof a === 'string' ? a : formatNumber(a);
}

/**
 * Translate `key`, filling %1, %2, ... with args.
 * Uses the `with: { rawtext: [...] }` form so args may themselves be translated.
 */
export function t(key: string, ...args: Arg[]): Raw {
  if (args.length === 0) return { translate: key };
  return { translate: key, with: { rawtext: args.map(toRaw) } };
}

/**
 * Plural-aware translation: picks `<baseKey>.p1|p21|p2|p5` from n and passes n as %1,
 * followed by extra args as %2, %3...
 * Example: `plural('unit.burmaldaholic.chip', 5)` -> "5 chips" / «5 фишек».
 */
export function plural(baseKey: string, n: number, ...extra: Arg[]): Raw {
  return t(pluralKey(baseKey, n), n, ...extra);
}

/** Nominative chip count: "1 chip", "12 500 chips" / «21 фишка». */
export const chips = (n: number): Raw => plural('unit.burmaldaholic.chip', n);
/** Accusative chip count, after verbs win/lose/pay/bet (RU «1 фишку»). */
export const chipsAcc = (n: number): Raw => plural('unit.burmaldaholic.chip_acc', n);

/**
 * Units from STRINGS.md §core "Units": chip, chip_acc, heart, level, day, minute, minute_acc,
 * second, second_acc, emerald, gold_ingot, player, line, spin, block, hand.
 */
export type Unit =
  | 'chip' | 'chip_acc' | 'heart' | 'level' | 'day' | 'minute' | 'minute_acc' | 'second' | 'second_acc'
  | 'emerald' | 'gold_ingot' | 'player' | 'line' | 'spin' | 'block' | 'hand';
export const unit = (u: Unit, n: number): Raw => plural(`unit.burmaldaholic.${u}`, n);

/** Duration in ticks as a counted unit: minutes when >= 60 s, else seconds ("3 minutes"). */
export function duration(ticks: number, accusative = false): Raw {
  const s = Math.max(0, Math.ceil(ticks / 20));
  if (s >= 60) return unit(accusative ? 'minute_acc' : 'minute', Math.ceil(s / 60));
  return unit(accusative ? 'second_acc' : 'second', s);
}

/** Random dialogue variant `<baseKey>.1` .. `<baseKey>.<count>` (LOCALIZATION.md §1.2). */
export function variant(rng: Rng, baseKey: string, count: number, ...args: Arg[]): Raw {
  return t(`${baseKey}.${randInt(rng, 1, count)}`, ...args);
}

/** Concatenate several messages into one. */
export function join(...parts: Arg[]): Raw {
  return { rawtext: parts.map(toRaw) };
}

/** Join messages with a separator (e.g. `lit(' · ')`), skipping undefined parts. */
export function joinWith(sep: Arg, parts: readonly (Raw | undefined)[]): Raw {
  const out: Arg[] = [];
  for (const p of parts) {
    if (!p) continue;
    if (out.length) out.push(sep);
    out.push(p);
  }
  return join(...out);
}

/**
 * Literal, NON-translated text (numbers, player names, formatting codes like §6).
 * Never use for words - the string check flags letters passed here.
 */
export function lit(s: string | number): Raw {
  return { text: formatArg(s) };
}

/**
 * A number with a fractional part, with the decimal separator of the reader's language
 * (`unit.burmaldaholic.decimal_separator`: "1.5" / «1,5», review m7). The server cannot know
 * the client language, so the separator is translated client-side. Whole numbers stay literal.
 */
export function decimal(n: number, digits = 1): Raw {
  const s = Math.abs(n).toFixed(digits);
  const [int, frac] = s.split('.');
  const sign = n < 0 && Number(s) !== 0 ? '−' : '';
  const trimmed = (frac ?? '').replace(/0+$/, '');
  if (!trimmed) return lit(sign + formatNumber(Number(int)));
  return join(lit(sign + formatNumber(Number(int))), t('unit.burmaldaholic.decimal_separator'), lit(trimmed));
}

/** Line break inside a message (bodies, tooltips). */
export const NEWLINE: Raw = { text: '\n' }; // i18n-ignore (not a word)

/** Join messages line by line, skipping undefined parts. */
export const lines = (...parts: (Raw | undefined)[]): Raw => joinWith(NEWLINE, parts);

/** Prefix a message with § formatting codes (colors): `color('§a', t(...))`. */
export function color(code: string, msg: Raw): Raw {
  return join(lit(code), msg, lit('§r'));
}
