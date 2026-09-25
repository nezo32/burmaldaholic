#!/usr/bin/env python3
"""Slot designer: builds reel strips from counts, writes machines.h for the C engine and strips.md."""
import random, json, sys

# ---------------------------------------------------------------- machine definitions
# pays are multiples of the TOTAL bet per way, given in FIFTHS (1 = 0.2x bet)
M = {}

M['overworld'] = dict(
    syms=['WD','SC','BN','DI','EM','GO','IR','AP','CA','WH','BE'],
    wild='WD', scatter='SC', bonus='BN', coin=None,
    wild_reels=[1,2,3], bonus_reels=[0,2,4],
    L=[40,40,40,40,40],
    counts=[
        dict(SC=2,BN=3,DI=3,EM=3,GO=4,IR=4,AP=5,CA=5,WH=5,BE=6),
        dict(WD=4,SC=1,DI=3,EM=3,GO=4,IR=4,AP=5,CA=5,WH=5,BE=6),
        dict(WD=4,SC=1,BN=2,DI=3,EM=3,GO=4,IR=4,AP=5,CA=5,WH=4,BE=5),
        dict(WD=4,SC=1,DI=3,EM=3,GO=4,IR=4,AP=5,CA=5,WH=5,BE=6),
        dict(SC=2,BN=3,DI=3,EM=3,GO=4,IR=4,AP=5,CA=5,WH=5,BE=6),
    ],
    stacks=dict(WD=2),
    spaced=['SC','BN'],
    pays=dict(DI=(4,10,20),EM=(3,6,12),GO=(2,4,8),IR=(2,4,8),AP=(1,2,4),CA=(1,2,4),WH=(1,2,4),BE=(1,2,4)),
    scat_pay=(5,50,250),   # fifths of total bet: 2x,10x,50x
    cascade=0, ladder_base=[1,1,1,1], ladder_fs=[1,1,1,1],
    seed=11,
)

M['nether'] = dict(
    syms=['WD','SC','CN','SK','BR','MC','QZ','NW','CF','WF','GD'],
    wild='WD', scatter='SC', bonus=None, coin='CN',
    wild_reels=[1,2,3], bonus_reels=[],
    L=[32,32,32,32,32],
    counts=[
        dict(SC=1,CN=2,SK=2,BR=3,MC=3,QZ=3,NW=5,CF=5,WF=4,GD=4),
        dict(WD=2,SC=1,CN=2,SK=2,BR=2,MC=3,QZ=3,NW=4,CF=4,WF=5,GD=4),
        dict(WD=2,SC=1,CN=3,SK=2,BR=2,MC=3,QZ=3,NW=4,CF=4,WF=4,GD=4),
        dict(WD=2,SC=1,CN=2,SK=2,BR=2,MC=3,QZ=3,NW=4,CF=4,WF=5,GD=4),
        dict(SC=1,CN=2,SK=2,BR=3,MC=3,QZ=3,NW=5,CF=5,WF=4,GD=4),
    ],
    stacks=dict(CN=2),
    spaced=['SC'],
    pays=dict(SK=(4,10,40),BR=(3,6,20),MC=(2,4,10),QZ=(2,4,8),NW=(1,2,3),CF=(1,2,3),WF=(1,1,3),GD=(1,1,3)),
    scat_pay=(0,0,0),
    cascade=1, ladder_base=[1,2,3,5], ladder_fs=[2,4,6,10],
    seed=23,
)

M['end'] = dict(
    syms=['WD','SC','BN','DH','EL','SS','CH','EP','PU','ER','ES'],
    wild='WD', scatter='SC', bonus='BN', coin=None,
    wild_reels=[1,2,3], bonus_reels=[1,2,3],
    L=[45,45,45,45,45],
    counts=[
        dict(SC=1,DH=2,EL=3,SS=4,CH=5,EP=7,PU=7,ER=8,ES=8),
        dict(WD=1,SC=1,BN=2,DH=2,EL=3,SS=4,CH=5,EP=6,PU=6,ER=7,ES=8),
        dict(WD=1,SC=1,BN=3,DH=2,EL=3,SS=4,CH=4,EP=6,PU=7,ER=7,ES=7),
        dict(WD=1,SC=1,BN=2,DH=2,EL=3,SS=4,CH=5,EP=6,PU=6,ER=7,ES=8),
        dict(SC=1,DH=2,EL=3,SS=4,CH=5,EP=7,PU=7,ER=8,ES=8),
    ],    stacks=dict(DH=2),
    spaced=['SC','BN','WD'],
    pays=dict(DH=(10,40,150),EL=(5,20,60),SS=(4,10,30),CH=(3,8,25),EP=(2,3,10),PU=(2,3,10),ER=(1,3,6),ES=(1,3,6)),
    scat_pay=(10,50,250),
    cascade=0, ladder_base=[1,1,1,1], ladder_fs=[1,1,1,1],
    seed=37,
)

def build_strip(L, counts, stacks, spaced, rng):
    total = sum(counts.values())
    assert total == L, (total, L, counts)
    for attempt in range(200000):
        blocks = []
        for s, c in counts.items():
            st = stacks.get(s, 1)
            n = c
            while n > 0:
                k = min(st, n); blocks.append([s]*k); n -= k
        rng.shuffle(blocks)
        strip = [x for b in blocks for x in b]
        ok = True
        # spaced symbols: cyclic distance >= 3 between any two spaced-symbol cells of same type
        for sp in spaced:
            pos = [i for i, x in enumerate(strip) if x == sp]
            for a in pos:
                for b in pos:
                    if a != b and min((a-b) % L, (b-a) % L) < 3:
                        ok = False
        # no regular symbol 3 in a row (cyclic), except stacked ones
        for i in range(L):
            a, b, c = strip[i], strip[(i+1) % L], strip[(i+2) % L]
            if a == b == c and a not in stacks:
                ok = False
        if ok:
            return strip
    raise RuntimeError('no strip')

def build(name):
    m = M[name]
    rng = random.Random(m['seed'])
    strips = []
    for r in range(5):
        strips.append(build_strip(m['L'][r], m['counts'][r], m['stacks'], m['spaced'], rng))
    m['strips'] = strips
    return m

def write_header(path):
    out = []
    out.append('#define NMACH 3')
    out.append('static Machine MACH[NMACH] = {')
    for name in ['overworld','nether','end']:
        m = build(name)
        sid = {s: i for i, s in enumerate(m['syms'])}
        def g(k):
            return sid[m[k]] if m[k] else -1
        strips = ','.join('{' + ','.join(str(sid[x]) for x in st) + '}' for st in m['strips'])
        pays = []
        for s in m['syms']:
            p = m['pays'].get(s, (0,0,0))
            pays.append('{0,0,0,%d,%d,%d}' % p)
        wr = sum(1 << r for r in m['wild_reels'])
        br = sum(1 << r for r in m['bonus_reels'])
        out.append('{"%s",%d,{%s},{%s},%d,%d,%d,%d,%d,%d,{%s},{0,0,0,%d,%d,%d},%d,{%s},{%s},{%s}},' % (
            name, len(m['syms']), ','.join(map(str, m['L'])), strips, g('wild'), g('scatter'), g('bonus'), g('coin'),
            wr, br, ','.join(pays), *m['scat_pay'], m['cascade'],
            ','.join(map(str, m['ladder_base'])), ','.join(map(str, m['ladder_fs'])),
            ','.join('"%s"' % s for s in m['syms'])))
    out.append('};')
    open(path, 'w').write('\n'.join(out) + '\n')

def strips_md():
    lines = []
    for name in ['overworld','nether','end']:
        m = M[name]
        lines.append(f'### {name}')
        for r, st in enumerate(m['strips']):
            lines.append(f'R{r+1} ({len(st)}): ' + ' '.join(st))
    return '\n'.join(lines)

if __name__ == '__main__':
    write_header(sys.argv[1] if len(sys.argv) > 1 else 'machines.h')
    open('strips.md', 'w').write(strips_md())
    json.dump({k: {kk: vv for kk, vv in v.items()} for k, v in M.items()}, open('design.json', 'w'), indent=1)

# ---------------------------------------------------------------- feature parameters
M['overworld'].update(
    fs_spins={3: 8, 4: 10, 5: 15}, fs_retrig=8, fs_cap=50, fs_mult=2, fs_mode=0,
    # Treasure Hunt: 15 chests, i.i.d. contents, pick until a Creeper (or all 15 open)
    pick=dict(board=15, table=[('x1', 5, 30000), ('x2', 10, 22000), ('x3', 15, 14000), ('x5', 25, 9000),
                               ('x10', 50, 3500), ('x25', 125, 800), ('MINI', 'MINI', 600), ('MINOR', 'MINOR', 150),
                               ('MAJOR', 'MAJOR', 20), ('GRAND', 'GRAND', 3), ('CREEPER', None, 22000)]),
    jp=dict(ref=100, seed=dict(MINI=10, MINOR=25, MAJOR=100, GRAND=500), contrib=dict(MINI=0.004, MINOR=0.003, MAJOR=0.002, GRAND=0.001),
            owned=dict(MINI=10, MINOR=25, MAJOR=100, GRAND=250)),
    cap=500,
)
M['nether'].update(
    fs_spins={3: 12, 4: 15, 5: 20}, fs_retrig=5, fs_cap=60, fs_mult=1, fs_mode=2,
    hold=dict(trigger=6, respins=3, p=0.04, table=[('x1', 5, 400), ('x2', 10, 250), ('x3', 15, 150), ('x5', 25, 100),
                                                   ('x10', 50, 50), ('x25', 125, 12), ('MINI', 'MINI', 8), ('MINOR', 'MINOR', 2), ('MAJOR', 'MAJOR', 0.3)]),
    jp=dict(ref=500, seed=dict(MINI=10, MINOR=30, MAJOR=150, GRAND=1000), contrib=dict(MINI=0.005, MINOR=0.004, MAJOR=0.0035, GRAND=0.0025),
            owned=dict(MINI=10, MINOR=30, MAJOR=150, GRAND=500)),
    cap=2000,
)
M['end'].update(
    fs_spins={3: 9, 4: 11, 5: 14}, fs_retrig=4, fs_cap=40, fs_mult=1, fs_mode=3,
    wheel=dict(rings=[
        [('x10', 50, 4), ('x12', 60, 3), ('x15', 75, 3), ('x20', 100, 2), ('x25', 125, 2), ('x40', 200, 1), ('x75', 375, 1), ('MINI', 'MINI', 2), ('UP', None, 2)],
        [('x30', 150, 4), ('x50', 250, 4), ('x75', 375, 3), ('x100', 500, 2), ('MINOR', 'MINOR', 2), ('UP', None, 1)],
        [('x150', 750, 4), ('x250', 1250, 3), ('x500', 2500, 1), ('MAJOR', 'MAJOR', 3), ('GRAND', 'GRAND', 1)],
    ]),
    jp=dict(ref=5000, seed=dict(MINI=15, MINOR=50, MAJOR=250, GRAND=2500), contrib=dict(MINI=0.005, MINOR=0.005, MAJOR=0.006, GRAND=0.009),
            owned=dict(MINI=15, MINOR=50, MAJOR=250, GRAND=1000)),
    cap=5000,
)


def write_features(path):
    names = ['overworld', 'nether', 'end']
    code = {'MINI': -1, 'MINOR': -2, 'MAJOR': -3, 'GRAND': -4}
    def val(n, v):
        if v is None: return 0
        if isinstance(v, str): return code[v]
        return v
    L = []
    fs = [[0, 0, 0] + [M[n]['fs_spins'][k] for k in (3, 4, 5)] for n in names]
    L.append('static const int FS_SPINS[3][6] = {%s};' % ','.join('{' + ','.join(map(str, r)) + '}' for r in fs))
    for key, nm in [('fs_mode', 'FS_MODE'), ('fs_mult', 'FS_MULT'), ('fs_retrig', 'FS_RETRIG'), ('fs_cap', 'FS_CAP'), ('cap', 'CAP')]:
        L.append('static const int %s[3] = {%s};' % (nm, ','.join(str(M[n][key]) for n in names)))
    pk = M['overworld']['pick']
    L.append('#define PICK_BOARD %d' % pk['board']); L.append('#define PICK_N %d' % len(pk['table']))
    L.append('static const int PICK_VAL[] = {%s};' % ','.join(str(val(a, b)) for a, b, c in pk['table']))
    L.append('static const double PICK_W[] = {%s};' % ','.join(str(c) for a, b, c in pk['table']))
    hd = M['nether']['hold']
    L.append('#define HOLD_TRIG %d' % hd['trigger']); L.append('#define HOLD_RESPINS %d' % hd['respins']); L.append('#define HOLD_P %r' % hd['p'])
    L.append('#define HOLD_N %d' % len(hd['table']))
    L.append('static const int HOLD_VAL[] = {%s};' % ','.join(str(val(a, b)) for a, b, c in hd['table']))
    L.append('static const double HOLD_W[] = {%s};' % ','.join(str(c) for a, b, c in hd['table']))
    rings = M['end']['wheel']['rings']
    L.append('static const int WHEEL_N[3] = {%s};' % ','.join(str(len(r)) for r in rings))
    L.append('static const int WHEEL_VAL[3][12] = {%s};' % ','.join('{' + ','.join(str(val(a, b)) for a, b, c in r) + '}' for r in rings))
    L.append('static const double WHEEL_W[3][12] = {%s};' % ','.join('{' + ','.join(str(c) for a, b, c in r) + '}' for r in rings))
    for key, nm in [('seed', 'JP_SEED'), ('owned', 'JP_OWNED')]:
        L.append('static const double %s[3][5] = {%s};' % (nm, ','.join('{0,%s}' % ','.join(str(M[n]['jp'][key][t]) for t in ['MINI', 'MINOR', 'MAJOR', 'GRAND']) for n in names)))
    open(path, 'w').write('\n'.join(L) + '\n')

if __name__ == '__main__':
    write_features('features.h')
