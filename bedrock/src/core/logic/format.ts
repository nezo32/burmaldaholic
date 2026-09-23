/**
 * Number and time formatting shared by both languages (LOCALIZATION.md §4). PURE.
 * Numbers are passed to lang strings as literal text, so they are formatted here once.
 */

/** 2500 -> '2500', 12500 -> '12 500', 1000000 -> '1 000 000' (regular space from 10 000). */
export function formatNumber(n: number): string {
  if (!Number.isFinite(n)) return String(n);
  const neg = n < 0;
  const a = Math.abs(n);
  const int = Math.trunc(a);
  const frac = a - int;
  let s = String(int);
  if (int >= 10_000) s = s.replace(/\B(?=(\d{3})+(?!\d))/g, ' ');
  if (frac > 0) s += (Math.round(frac * 100) / 100).toFixed(2).slice(1).replace(/0+$/, '');
  return (neg ? '−' : '') + s;
}

/** Signed amount for results: '+150', '−50', '0'. */
export function formatSigned(n: number): string {
  return n > 0 ? `+${formatNumber(n)}` : formatNumber(n);
}

/** Multiplier with the × sign before the number: '×10', '×0.5'. */
export const formatMultiplier = (m: number): string => `×${formatNumber(m)}`;

/** Ticks -> 'MM:SS' (or 'H:MM:SS' above an hour). 20 ticks = 1 s. */
export function formatClock(ticks: number): string {
  const total = Math.max(0, Math.ceil(ticks / 20));
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  const mm = String(m).padStart(2, '0');
  const ss = String(s).padStart(2, '0');
  return h > 0 ? `${h}:${mm}:${ss}` : `${mm}:${ss}`;
}

/**
 * Remaining world ticks -> [days, 'HH:MM'] for `hud.burmaldaholic.time.dhm` (UI.md §1:
 * real-time minutes of the remaining ticks, 1 MCD = "1d").
 */
export function formatDhm(ticks: number): [number, string] {
  const t = Math.max(0, ticks);
  const days = Math.floor(t / 24000);
  const minutes = Math.ceil((t % 24000) / 1200);
  return [days, `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`];
}
