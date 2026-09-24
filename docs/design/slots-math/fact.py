"""Factorised exact EV for non-cascading 243-ways machines (independent reels)."""
from itertools import product

def windows(strip):
    L = len(strip)
    return [tuple(strip[(t + y) % L] for y in range(3)) for t in range(L)]

def reel_stats(win_list, sym, wild, wild_ok, transform=None):
    """returns (E[n], P(n=0)) of symbol sym on a reel, n = cells showing sym or wild"""
    En = 0.0; P0 = 0.0; L = len(win_list)
    for w in win_list:
        if transform: w = transform(w)
        n = sum(1 for x in w if x == sym or (wild_ok and x == wild))
        En += n / L
        if n == 0: P0 += 1 / L
    return En, P0

def ways_ev(m, strips, transforms=(None,) * 5):
    wins = [windows(s) for s in strips]
    ev = 0.0
    for S, (p3, p4, p5) in m['pays'].items():
        st = [reel_stats(wins[r], S, m['wild'], r in m['wild_reels'], transforms[r]) for r in range(5)]
        pr = [p3, p4, p5]
        prod_ = 1.0
        for k in range(1, 6):
            prod_ *= st[k - 1][0]
            if k >= 3:
                tail = st[k][1] if k < 5 else 1.0
                ev += pr[k - 3] / 5 * prod_ * tail
    return ev

def scatter_dist(m, strips, transforms=(None,) * 5):
    """distribution of scatter count (reels independent)"""
    dist = {0: 1.0}
    for r in range(5):
        wl = windows(strips[r]); L = len(wl)
        per = {}
        for w in wl:
            if transforms[r]: w = transforms[r](w)
            c = sum(1 for x in w if x == m['scatter'])
            per[c] = per.get(c, 0) + 1 / L
        nd = {}
        for a, pa in dist.items():
            for b, pb in per.items():
                nd[a + b] = nd.get(a + b, 0) + pa * pb
        dist = nd
    return dist

def p_bonus(m, strips):
    p = 1.0
    for r in m['bonus_reels']:
        wl = windows(strips[r])
        p *= sum(1 for w in wl if m['bonus'] in w) / len(wl)
    return p
