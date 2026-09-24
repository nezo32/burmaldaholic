/**
 * Full enumeration of the base game (SLOTS.md §7.5: every stop combination, §1.1 evaluation + §3.2 tumbles,
 * scatter pays included, no features). PURE; used by the slow exact tests (`SLOTS_ENUM=1`) and by nobody at
 * runtime (it takes tens of seconds). Non-tumbling machines use incremental per-reel products; the tumbling
 * machine runs the real chain (`runTumbles`) whenever the first window wins.
 */
import { bonusTriggered, runTumbles, scatterOf, wildOf } from './engine';
import type { MachineDef } from './types';
import { REELS, ROWS, symbolAt } from './types';

export interface EnumStats {
  N: number;
  /** Σ spin pay, fifths */
  sum: number;
  /** spins with pay > 0 */
  hits: number;
  /** spins with pay > 0 or a trigger (free spins or bonus) */
  hitsOrTrigger: number;
  /** scatter count histogram on the final window */
  scatters: number[];
  bonusTriggers: number;
  /** tumbles per spin histogram (tumbling machines) */
  tumbles: number[];
  /** spins with 5 of a kind of `topSymbol` (any tumble step) */
  topFive: number;
  /** largest spin pay, fifths */
  maxPay: number;
}

export function enumerateBase(def: MachineDef, topSymbol: number): EnumStats {
  return def.ladder.length > 0 ? enumerateTumbling(def, topSymbol) : enumerateWays(def, topSymbol);
}

function enumerateWays(def: MachineDef, top: number): EnumStats {
  const wd = wildOf(def);
  const sc = scatterOf(def);
  const pays: number[] = [];
  for (let p = 0; p < def.roles.length; p++) if (def.roles[p] === 'PAY' && def.paysFifths[p]!.some((x) => x > 0)) pays.push(p);
  const P = pays.length;
  const L = def.strips.map((s) => s.length);
  // cnt[r][t * P + i] = n_r(pays[i]); sc[r][t]; bonus[r][t]
  const cnt = L.map((len, r) => {
    const a = new Int32Array(len * P);
    for (let t = 0; t < len; t++)
      for (let i = 0; i < P; i++) {
        let n = 0;
        for (let y = 0; y < ROWS; y++) {
          const s = symbolAt(def, r, t, y);
          if (s === pays[i] || s === wd) n++;
        }
        a[t * P + i] = n;
      }
    return a;
  });
  const scat = L.map((len, r) => Int32Array.from({ length: len }, (_, t) => [0, 1, 2].filter((y) => symbolAt(def, r, t, y) === sc).length));
  const bn = def.roles.indexOf('BONUS');
  const bon = L.map((len, r) => Int32Array.from({ length: len }, (_, t) => (bn >= 0 && def.bonusReelsMask & (1 << r) && [0, 1, 2].some((y) => symbolAt(def, r, t, y) === bn) ? 1 : 0)));
  const need = [...Array<number>(REELS).keys()].filter((r) => def.bonusReelsMask & (1 << r)).length;
  const pay3 = pays.map((p) => def.paysFifths[p]!);
  const topI = pays.indexOf(top);
  const st: EnumStats = { N: 0, sum: 0, hits: 0, hitsOrTrigger: 0, scatters: new Array<number>(16).fill(0), bonusTriggers: 0, tumbles: [], topFive: 0, maxPay: 0 };
  const w3 = new Float64Array(P);
  const w4 = new Float64Array(P);
  for (let t0 = 0; t0 < L[0]!; t0++)
    for (let t1 = 0; t1 < L[1]!; t1++)
      for (let t2 = 0; t2 < L[2]!; t2++) {
        let any3 = false;
        for (let i = 0; i < P; i++) {
          const w = cnt[0]![t0 * P + i]! * cnt[1]![t1 * P + i]! * cnt[2]![t2 * P + i]!;
          w3[i] = w;
          if (w) any3 = true;
        }
        const s3 = scat[0]![t0]! + scat[1]![t1]! + scat[2]![t2]!;
        const b3 = bon[0]![t0]! + bon[1]![t1]! + bon[2]![t2]!;
        for (let t3 = 0; t3 < L[3]!; t3++) {
          let base4 = 0;
          let any4 = false;
          if (any3)
            for (let i = 0; i < P; i++) {
              const n4 = cnt[3]![t3 * P + i]!;
              const w = w3[i]!;
              if (!w) {
                w4[i] = 0;
                continue;
              }
              if (n4 === 0) {
                w4[i] = 0;
                base4 += pay3[i]![0]! * w;
              } else {
                w4[i] = w * n4;
                any4 = true;
              }
            }
          const s4 = s3 + scat[3]![t3]!;
          const b4 = b3 + bon[3]![t3]!;
          for (let t4 = 0; t4 < L[4]!; t4++) {
            let pay = base4;
            let five = false;
            if (any4)
              for (let i = 0; i < P; i++) {
                const w = w4[i]!;
                if (!w) continue;
                const n5 = cnt[4]![t4 * P + i]!;
                if (n5 === 0) pay += pay3[i]![1]! * w;
                else {
                  pay += pay3[i]![2]! * w * n5;
                  if (i === topI) five = true;
                }
              }
            const s = s4 + scat[4]![t4]!;
            if (s >= 3) pay += def.scatterFifths[Math.min(s, 5) - 3]!;
            const trig = s >= 3 || (need > 0 && b4 + bon[4]![t4]! >= need);
            st.scatters[s]!++;
            if (need > 0 && b4 + bon[4]![t4]! >= need) st.bonusTriggers++;
            st.sum += pay;
            if (pay > 0) st.hits++;
            if (pay > 0 || trig) st.hitsOrTrigger++;
            if (five) st.topFive++;
            if (pay > st.maxPay) st.maxPay = pay;
          }
        }
      }
  st.N = L.reduce((a, b) => a * b, 1);
  return st;
}

function enumerateTumbling(def: MachineDef, top: number): EnumStats {
  const L = def.strips.map((s) => s.length);
  const st: EnumStats = { N: 0, sum: 0, hits: 0, hitsOrTrigger: 0, scatters: new Array<number>(16).fill(0), bonusTriggers: 0, tumbles: new Array<number>(16).fill(0), topFive: 0, maxPay: 0 };
  const stops = [0, 0, 0, 0, 0];
  for (stops[0] = 0; stops[0] < L[0]!; stops[0]++)
    for (stops[1] = 0; stops[1] < L[1]!; stops[1]++)
      for (stops[2] = 0; stops[2] < L[2]!; stops[2]++)
        for (stops[3] = 0; stops[3] < L[3]!; stops[3]++)
          for (stops[4] = 0; stops[4] < L[4]!; stops[4]++) {
            const chain = runTumbles(def, stops, def.ladder);
            const last = chain.steps[chain.steps.length - 1]!.result;
            const s = last.scatters;
            const pay = chain.payFifths + (s >= 3 ? def.scatterFifths[Math.min(s, 5) - 3]! : 0);
            const bonus = bonusTriggered(def, last);
            st.scatters[s]!++;
            st.tumbles[chain.steps.length - 1]!++;
            if (bonus) st.bonusTriggers++;
            st.sum += pay;
            if (pay > 0) st.hits++;
            if (pay > 0 || s >= 3 || bonus) st.hitsOrTrigger++;
            if (chain.steps.some((x) => x.result.wins.some((w) => w.symbol === top && w.k === 5))) st.topFive++;
            if (pay > st.maxPay) st.maxPay = pay;
          }
  st.N = L.reduce((a, b) => a * b, 1);
  return st;
}
