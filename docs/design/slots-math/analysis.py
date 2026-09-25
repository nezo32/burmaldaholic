#!/usr/bin/env python3
import json, subprocess, hashlib, os, sys
from functools import lru_cache
from fractions import Fraction
import gen

HERE = os.path.dirname(os.path.abspath(__file__))
os.chdir(HERE)
h = None
def prep():
    global h
    gen.write_header('machines.h')
    open('strips.md', 'w').write(gen.strips_md())
    nh = hashlib.md5(open('machines.h', 'rb').read() + open('engine.c', 'rb').read()).hexdigest()[:10]
    if nh != h:
        subprocess.run(['gcc', '-O3', '-march=native', '-o', 'engine', 'engine.c'], check=True)
    h = nh
CACHE = 'cache.json'
cache = json.load(open(CACHE)) if os.path.exists(CACHE) else {}
NAMES = ['overworld', 'nether', 'end']

def run_many(jobs):
    todo = [j for j in jobs if f'{h}:{j}' not in cache]
    procs = []
    for j in todo:
        procs.append((j, subprocess.Popen(['./engine', *map(str, j)], stdout=subprocess.PIPE)))
        if len(procs) >= 4:
            for jj, p in procs:
                cache[f'{h}:{jj}'] = json.loads(p.communicate()[0]); json.dump(cache, open(CACHE, 'w'))
            procs = []
    for jj, p in procs:
        cache[f'{h}:{jj}'] = json.loads(p.communicate()[0]); json.dump(cache, open(CACHE, 'w'))
    return [cache[f'{h}:{j}'] for j in jobs]

def job(mi, mode, pre=0):
    return (mi, mode, pre)

JPT = ['MINI', 'MINOR', 'MAJOR', 'GRAND']

def fs_spins_iid(n, pr, R, C):
    @lru_cache(None)
    def f(rem, aw):
        if rem == 0: return 0.0
        add = min(R, C - aw)
        return 1 + pr * f(rem - 1 + add, aw + add) + (1 - pr) * f(rem - 1, aw)
    return f(n, n)

def analyse(name, verbose=True):
    prep()
    mi = NAMES.index(name); m = gen.M[name]
    jobs = [job(mi, 0)]
    if m['fs_mode'] in (0, 1, 2): jobs.append(job(mi, m['fs_mode']))
    else: jobs += [job(mi, 3, s) for s in range(8)]
    res = run_many(jobs)
    base = res[0]; N = base['N']
    out = {}
    rtp_base = base['sumPay'] / 5 / N
    sc = base['scat']
    p_sc = {3: sc[3] / N, 4: sc[4] / N, 5: sum(sc[5:]) / N}
    scat_pay_rtp = sum(p_sc[k] * m['scat_pay'][k - 3] / 5 for k in (3, 4, 5))
    out['base_ways'] = rtp_base - scat_pay_rtp
    out['scatter_pay'] = scat_pay_rtp
    out['p_fs'] = sum(p_sc.values())
    out['hit_pay'] = base['hitPay'] / N
    out['hit_any'] = base['hitAny'] / N
    out['sd_base'] = ((base['sumPay2'] / 25 / N) - rtp_base ** 2) ** 0.5
    # ---- free spins
    C = m['fs_cap']; R = m['fs_retrig']
    if m['fs_mode'] in (0, 1, 2):
        fs = res[1]; e = fs['sumPay'] / 5 / fs['N'] * m['fs_mult']
        pr = sum(fs['scat'][3:]) / fs['N']
        ev_trig = {k: e * fs_spins_iid(m['fs_spins'][k], pr, R, C) for k in (3, 4, 5)}
        out['fs_spin_ev'] = e; out['fs_retrig_p'] = pr
        out['fs_exp_spins'] = {k: fs_spins_iid(m['fs_spins'][k], pr, R, C) for k in (3, 4, 5)}
    else:
        E = {}; P = {}
        for s in range(8):
            r = res[1 + s]; E[s] = r['sumPay'] / 5 / r['N']
            P[s] = {(post, rt): r['post'][post][rt] / r['N'] for post in range(8) for rt in (0, 1) if r['post'][post][rt]}
        @lru_cache(None)
        def V(mask, rem, aw):
            if rem == 0: return 0.0
            add = min(R, C - aw)
            v = E[mask]
            for (post, rt), p in P[mask].items():
                v += p * (V(post, rem - 1 + add, aw + add) if rt else V(post, rem - 1, aw))
            return v
        @lru_cache(None)
        def S(mask, rem, aw):  # expected spins
            if rem == 0: return 0.0
            add = min(R, C - aw)
            v = 1.0
            for (post, rt), p in P[mask].items():
                v += p * (S(post, rem - 1 + add, aw + add) if rt else S(post, rem - 1, aw))
            return v
        @lru_cache(None)
        def W3(mask, rem, aw):  # probability of ending with all 3 wild reels
            if rem == 0: return 1.0 if mask == 7 else 0.0
            add = min(R, C - aw)
            v = 0.0
            for (post, rt), p in P[mask].items():
                v += p * (W3(post, rem - 1 + add, aw + add) if rt else W3(post, rem - 1, aw))
            return v
        ev_trig = {k: V(0, m['fs_spins'][k], m['fs_spins'][k]) for k in (3, 4, 5)}
        out['fs_state_ev'] = E
        out['fs_exp_spins'] = {k: S(0, m['fs_spins'][k], m['fs_spins'][k]) for k in (3, 4, 5)}
        out['fs_p_all3'] = {k: W3(0, m['fs_spins'][k], m['fs_spins'][k]) for k in (3, 4, 5)}
        out['fs_retrig_p0'] = sum(p for (post, rt), p in P[0].items() if rt)
        out['fs_land'] = {s: sum(p for (post, rt), p in P[0].items() if post == s) for s in range(8)}
    out['fs_ev_trig'] = ev_trig
    out['fs_rtp'] = sum(p_sc[k] * ev_trig[k] for k in (3, 4, 5))
    out['fs_avg_win'] = out['fs_rtp'] / out['p_fs']
    # ---- bonus
    jp_hits = {t: 0.0 for t in JPT}   # expected awards per spin
    bonus_rtp = 0.0
    if 'pick' in m:
        pk = m['pick']; W = sum(w for _, _, w in pk['table']); pc = [w for n, v, w in pk['table'] if n == 'CREEPER'][0] / W
        q = 1 - pc; EN = sum(q ** j for j in range(1, pk['board'] + 1))
        p_trig = base['bon'] / N
        ev_val = sum(w / W * v / 5 for n, v, w in pk['table'] if isinstance(v, int))
        out['p_bonus'] = p_trig; out['pick_EN'] = EN
        out['bonus_ev_trig'] = EN * ev_val / q * q  # EN counts non-creeper picks; ev_val is per pick (unconditional) / q
        out['bonus_ev_trig'] = EN * (ev_val / q)
        bonus_rtp = p_trig * out['bonus_ev_trig']
        for n, v, w in pk['table']:
            if n in JPT: jp_hits[n] += p_trig * EN * (w / W) / q
        out['pick_p_first_creeper'] = pc
    if 'hold' in m:
        hd = m['hold']; W = sum(w for _, _, w in hd['table']); p = hd['p']
        from math import comb
        @lru_cache(None)
        def F(n, r):  # expected final count, P(full)
            if n == 15: return (15.0, 1.0)
            if r == 0: return (float(n), 0.0)
            e = 0.0; pf = 0.0; free = 15 - n
            for k in range(0, free + 1):
                pk_ = comb(free, k) * p ** k * (1 - p) ** (free - k)
                a, b = F(n + k, hd['respins']) if k else F(n, r - 1)
                e += pk_ * a; pf += pk_ * b
            return (e, pf)
        coins = base['coins']; out['p_bonus'] = sum(coins[hd['trigger']:]) / N
        ev_val = sum(w / W * v / 5 for n, v, w in hd['table'] if isinstance(v, int))
        ev = 0.0; efin = 0.0; pfull = 0.0
        for n in range(hd['trigger'], 16):
            if not coins[n]: continue
            pn = coins[n] / N; a, b = F(n, hd['respins'])
            ev += pn * a * ev_val; efin += pn * a; pfull += pn * b
            for nm, v, w in hd['table']:
                if nm in JPT: jp_hits[nm] += pn * a * w / W
        jp_hits['GRAND'] += pfull
        bonus_rtp = ev
        out['bonus_ev_trig'] = ev / out['p_bonus']; out['hold_final'] = efin / out['p_bonus']; out['hold_pfull_trig'] = pfull / out['p_bonus']
        out['hold_start'] = {n: coins[n] / N for n in range(hd['trigger'], 16) if coins[n]}
    if 'wheel' in m:
        rings = m['wheel']['rings']
        def ring_ev(i, reach):
            W = sum(w for _, _, w in rings[i]); ev = 0.0
            for n, v, w in rings[i]:
                p = w / W
                if isinstance(v, int): ev += p * v / 5
                elif n in JPT: jp_hits[n] += p_trig * reach * p
                elif n == 'UP': ev += p * ring_ev(i + 1, reach * p)
            return ev
        p_trig = base['bon'] / N
        ev = ring_ev(0, 1.0)
        out['p_bonus'] = p_trig; out['bonus_ev_trig'] = ev; bonus_rtp = p_trig * ev
    out['bonus_rtp'] = bonus_rtp
    jp = m['jp']
    out['jp_hits'] = jp_hits
    out['jp_seed_rtp'] = sum(jp_hits[t] * jp['seed'][t] for t in JPT)
    out['jp_contrib'] = sum(jp['contrib'].values())
    out['jp_owned_rtp'] = sum(jp_hits[t] * jp['owned'][t] for t in JPT)
    out['total'] = out['base_ways'] + out['scatter_pay'] + out['fs_rtp'] + out['bonus_rtp'] + out['jp_seed_rtp'] + out['jp_contrib']
    out['total_owned'] = out['base_ways'] + out['scatter_pay'] + out['fs_rtp'] + out['bonus_rtp'] + out['jp_owned_rtp']
    out['base_raw'] = base
    if verbose:
        print(f"== {name}: TOTAL {out['total']*100:.4f}%  owned {out['total_owned']*100:.4f}%")
        for k in ['base_ways', 'scatter_pay', 'fs_rtp', 'bonus_rtp', 'jp_seed_rtp', 'jp_contrib']:
            print(f"   {k:12s} {out[k]*100:8.4f}%")
        print(f"   hit_pay {out['hit_pay']:.4f} (1 in {1/out['hit_pay']:.2f})  hit_any {out['hit_any']:.4f}  sd_base {out['sd_base']:.3f}")
        print(f"   FS 1 in {1/out['p_fs']:.1f}, avg FS win {out['fs_avg_win']:.2f}x ; bonus 1 in {1/out['p_bonus']:.1f} avg {out['bonus_ev_trig']:.2f}x")
        print('   jp 1 in', {t: round(1 / v) if v else None for t, v in jp_hits.items()})
        for k in ['fs_spin_ev', 'fs_retrig_p', 'fs_exp_spins', 'fs_p_all3', 'fs_retrig_p0', 'pick_EN', 'hold_final', 'hold_pfull_trig', 'fs_ev_trig']:
            if k in out: print('  ', k, out[k])
        if 'tumb' in base: print('   tumbles', base['tumb'][:10])
    return out

if __name__ == '__main__':
    names = sys.argv[1:] or NAMES
    R = {n: analyse(n) for n in names}
    json.dump({n: {k: v for k, v in r.items()} for n, r in R.items()}, open('results.json', 'w'), indent=1, default=str)
