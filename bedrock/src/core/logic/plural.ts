/**
 * Language-agnostic plural selection. PURE.
 *
 * The server cannot know a client's language (rawtext is translated on the client),
 * so we pick a *key suffix* from the number alone. The suffix set is the finest
 * partition of integers needed by all supported languages (EN + RU):
 *
 *   p1  : |n| === 1                          EN singular, RU "one"  (1 фишка)
 *   p21 : |n|%10===1 && |n|%100!==11, n!==1  EN plural,   RU "one"  (21 фишка)
 *   p2  : |n|%10 in 2..4, |n|%100 not 12..14  EN plural,   RU "few"  (2 фишки)
 *   p5  : everything else (0, 5-20, 11-14...) EN plural,   RU "many" (5 фишек)
 *
 * Every plural base key must define all four suffixes in every language
 * (enforced by scripts/lang.mjs). Adding a language with different rules means
 * refining this partition (e.g. Polish needs 21 -> "many": already separable via p21).
 */
export const PLURAL_SUFFIXES = ['p1', 'p21', 'p2', 'p5'] as const;
export type PluralSuffix = (typeof PLURAL_SUFFIXES)[number];

export function pluralSuffix(n: number): PluralSuffix {
  const a = Math.abs(Math.trunc(n));
  if (a === 1) return 'p1';
  const m10 = a % 10;
  const m100 = a % 100;
  if (m10 === 1 && m100 !== 11) return 'p21';
  if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) return 'p2';
  return 'p5';
}

export function pluralKey(baseKey: string, n: number): string {
  return `${baseKey}.${pluralSuffix(n)}`;
}
