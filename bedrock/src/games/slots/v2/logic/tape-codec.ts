/**
 * Compact tape string, identical to Java `TapeCodec` (SLOTS.md §8.1; ≤ 1 200 chars worst case). PURE. Lane S-B2.
 *
 * Grammar (numbers in lower-case base 36, negative with a leading '-'; fields separated by ';'):
 *   2;<m>;<bet>;<flags>;<total>;<stops>;<fs>;<hunt>;<hoard>;<wheel>;<jackpots>
 *   m        o | n | e                         flags   'b' bought, 'c' cap hit (in that order), or empty
 *   stops    s.s.s.s.s  (empty when bought)
 *   fs       <awarded>:<spin>/<spin>…           spin    s.s.s.s.s,<stickyMaskAfter>,<retrigger 0|1>,<pay>
 *   hunt     <entry>.<entry>…:<opened>
 *   hoard    <cells>,<values>/<cells>,<values>… (first pair = locked coins, then one pair per respin;
 *            cells/values '.'-joined, empty for a respin without a new coin)
 *   wheel    seg.seg.seg
 *   jackpots <tier>.<chips>.<owned 0|1>/…
 * Empty optional sections are empty strings. Decoding validates the shape and throws on anything else.
 */
import { type FreeSpin, type JackpotAward, type MachineId, type SpinTape, TAPE_VERSION } from './types';

const M: Record<MachineId, string> = { overworld: 'o', nether: 'n', end: 'e' };
const MR: Record<string, MachineId> = { o: 'overworld', n: 'nether', e: 'end' };

const n36 = (n: number): string => {
  if (!Number.isSafeInteger(n)) throw new RangeError(`tape: not an integer: ${n}`);
  return n.toString(36);
};
const list = (a: readonly number[]): string => a.map(n36).join('.');

const p36 = (s: string): number => {
  if (!/^-?[0-9a-z]+$/.test(s)) throw new SyntaxError(`tape: bad number '${s}'`);
  const v = parseInt(s, 36);
  if (!Number.isSafeInteger(v)) throw new SyntaxError(`tape: number out of range '${s}'`);
  return v;
};
const plist = (s: string): number[] => (s === '' ? [] : s.split('.').map(p36));

export function encodeTape(t: SpinTape): string {
  const flags = (t.bought ? 'b' : '') + (t.capHit ? 'c' : '');
  const fs = t.freeSpins
    ? `${n36(t.freeSpins.awarded)}:${t.freeSpins.spins.map((s) => `${list(s.stops)},${n36(s.stickyMaskAfter)},${s.retrigger ? 1 : 0},${n36(s.payFifths)}`).join('/')}`
    : '';
  const hunt = t.hunt ? `${list(t.hunt.entries)}:${n36(t.hunt.opened)}` : '';
  const hoard = t.hoard
    ? [`${list(t.hoard.initialCells)},${list(t.hoard.initialValues)}`, ...t.hoard.respinCells.map((c, i) => `${list(c)},${list(t.hoard!.respinValues[i]!)}`)].join('/')
    : '';
  const wheel = t.wheel ? list(t.wheel.segments) : '';
  const jp = t.jackpots.map((j) => `${n36(j.tier)}.${n36(j.chips)}.${j.owned ? 1 : 0}`).join('/');
  return [TAPE_VERSION, M[t.machine], n36(t.bet), flags, n36(t.totalFifths), list(t.stops), fs, hunt, hoard, wheel, jp].join(';');
}

export function decodeTape(s: string): SpinTape {
  const f = s.split(';');
  if (f.length !== 11 || f[0] !== String(TAPE_VERSION)) throw new SyntaxError('tape: not a v2 tape');
  const machine = MR[f[1]!];
  if (!machine) throw new SyntaxError(`tape: unknown machine '${f[1]}'`);
  if (!/^b?c?$/.test(f[3]!)) throw new SyntaxError(`tape: bad flags '${f[3]}'`);
  const t: SpinTape = {
    machine,
    bet: p36(f[2]!),
    bought: f[3]!.includes('b'),
    stops: plist(f[5]!),
    jackpots: [],
    totalFifths: p36(f[4]!),
    capHit: f[3]!.includes('c'),
  };
  if (!t.bought && t.stops.length !== 5) throw new SyntaxError('tape: 5 stops expected');
  if (f[6]) {
    const [aw, body] = f[6].split(':') as [string, string | undefined];
    if (body === undefined) throw new SyntaxError('tape: bad free spins');
    const spins: FreeSpin[] = body === '' ? [] : body.split('/').map((x) => {
      const [st, mask, rt, pay] = x.split(',');
      if (pay === undefined || (rt !== '0' && rt !== '1')) throw new SyntaxError('tape: bad free spin');
      const stops = plist(st!);
      if (stops.length !== 5) throw new SyntaxError('tape: 5 free-spin stops expected');
      return { stops, stickyMaskAfter: p36(mask!), retrigger: rt === '1', payFifths: p36(pay) };
    });
    t.freeSpins = { awarded: p36(aw), spins, payFifths: spins.reduce((a, x) => a + x.payFifths, 0) };
  }
  if (f[7]) {
    const [e, o] = f[7].split(':');
    if (o === undefined) throw new SyntaxError('tape: bad hunt');
    t.hunt = { entries: plist(e!), opened: p36(o) };
  }
  if (f[8]) {
    const parts = f[8].split('/').map((x) => {
      const [c, v] = x.split(',');
      if (v === undefined) throw new SyntaxError('tape: bad hoard');
      const cells = plist(c!);
      const values = plist(v);
      if (cells.length !== values.length) throw new SyntaxError('tape: hoard cells/values mismatch');
      return { cells, values };
    });
    t.hoard = {
      initialCells: parts[0]!.cells,
      initialValues: parts[0]!.values,
      respinCells: parts.slice(1).map((p) => p.cells),
      respinValues: parts.slice(1).map((p) => p.values),
    };
  }
  if (f[9]) t.wheel = { segments: plist(f[9]) };
  if (f[10])
    t.jackpots = f[10].split('/').map((x): JackpotAward => {
      const [tier, chips, owned] = x.split('.');
      const tv = p36(tier!);
      if (tv < 1 || tv > 4 || (owned !== '0' && owned !== '1')) throw new SyntaxError('tape: bad jackpot');
      return { tier: tv as 1 | 2 | 3 | 4, chips: p36(chips!), owned: owned === '1' };
    });
  return t;
}
