/**
 * Rawtext builders. PURE: the RawMessage shape here is structurally compatible with
 * @minecraft/server RawMessage, so results can be passed straight to sendMessage,
 * setActionBar, setTitle, ActionFormData.title/body/button, etc.
 *
 * ALL player-facing text must be built with these helpers (scripts/check-strings.mjs).
 */
import { pluralKey } from './plural';

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

function formatArg(a: string | number): string {
  if (typeof a === 'string') return a;
  return Number.isInteger(a) ? String(a) : a.toFixed(2);
}

/**
 * Translate `key`, filling %1$s, %2$s, ... with args.
 * Uses the `with: { rawtext: [...] }` form so args may themselves be translated.
 */
export function t(key: string, ...args: Arg[]): Raw {
  if (args.length === 0) return { translate: key };
  return { translate: key, with: { rawtext: args.map(toRaw) } };
}

/**
 * Plural-aware translation: picks `<baseKey>.p1|p21|p2|p5` from n and passes n as %1$s,
 * followed by extra args as %2$s, %3$s...
 * Example lang: `msg.burmaldaholic.core.chips.p5=%1$s chips` / `msg.burmaldaholic.core.chips.p5=%1$s фишек`.
 */
export function plural(baseKey: string, n: number, ...extra: Arg[]): Raw {
  return t(pluralKey(baseKey, n), n, ...extra);
}

/** Concatenate several messages into one. */
export function join(...parts: Arg[]): Raw {
  return { rawtext: parts.map(toRaw) };
}

/**
 * Literal, NON-translated text (numbers, player names, formatting codes like §6).
 * Never use for words - the string check flags letters passed here.
 */
export function lit(s: string | number): Raw {
  return { text: formatArg(s) };
}
