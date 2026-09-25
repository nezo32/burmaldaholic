import sys
from functools import lru_cache
import gen, fact

def end_fs(m, strips, verbose=False):
    W = m['wild']
    wins = [fact.windows(s) for s in strips]
    expand = lambda w: (W, W, W) if W in w else w
    allw = lambda w: (W, W, W)
    E = {}; T = {}
    for mask in range(8):
        tr = [None] * 5
        for j, r in enumerate((1, 2, 3)):
            tr[r] = allw if mask >> j & 1 else expand
        ev = fact.ways_ev(m, strips, tr)
        sd = fact.scatter_dist(m, strips, tr)
        ev += sum(p * m['scat_pay'][min(c, 5) - 3] / 5 for c, p in sd.items() if c >= 3)
        E[mask] = ev
        # transitions: joint (post, scat)
        dist = {(mask, 0): 1.0}
        for r in range(5):
            per = {}
            L = len(wins[r])
            for w in wins[r]:
                j = r - 1
                if r in (1, 2, 3) and (mask >> j & 1):
                    key = (1 << j, 0)
                elif r in (1, 2, 3) and W in w:
                    key = (1 << j, 0)
                else:
                    key = (0, sum(1 for x in w if x == m['scatter']))
                per[key] = per.get(key, 0) + 1 / L
            nd = {}
            for (a, sa), pa in dist.items():
                for (b, sb), pb in per.items():
                    k = (a | b, sa + sb); nd[k] = nd.get(k, 0) + pa * pb
            dist = nd
        t = {}
        for (post, sc), p in dist.items():
            k = (post, sc >= 3); t[k] = t.get(k, 0) + p
        T[mask] = t
    R = m['fs_retrig']; C = m['fs_cap']
    @lru_cache(None)
    def V(mask, rem, aw):
        if rem == 0: return 0.0
        add = min(R, C - aw); v = E[mask]
        for (post, rt), p in T[mask].items():
            v += p * (V(post, rem - 1 + add, aw + add) if rt else V(post, rem - 1, aw))
        return v
    @lru_cache(None)
    def V2(mask, rem, aw):  # second moment (approx: ignores within-spin variance) -> skip
        return 0
    return E, T, {k: V(0, n, n) for k, n in m['fs_spins'].items()}

if __name__ == '__main__':
    m = gen.build('end')
    strips = m['strips']
    base = fact.ways_ev(m, strips)
    sd = fact.scatter_dist(m, strips)
    scp = sum(p * m['scat_pay'][min(c, 5) - 3] / 5 for c, p in sd.items() if c >= 3)
    pfs = {k: sum(p for c, p in sd.items() if (c if c < 5 else 5) == k) for k in (3, 4, 5)}
    E, T, ev = end_fs(m, strips)
    fsr = sum(pfs[k] * ev[k] for k in (3, 4, 5))
    pb = fact.p_bonus(m, strips)
    print('base ways %.4f scat %.4f fs %.4f  pFS 1/%.0f  pBonus 1/%.0f' % (base, scp, fsr, 1 / sum(pfs.values()), 1 / pb))
    print('E by mask', {k: round(v, 2) for k, v in E.items()})
    print('ev trig', {k: round(v, 2) for k, v in ev.items()})
