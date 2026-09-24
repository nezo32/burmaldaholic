// Exact enumeration engine for Burmaldaholic 243-ways slots.
// Units: pays in FIFTHS of the total bet (1 = 0.2x bet).
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>

#define MAXL 64
#define MAXS 16
typedef struct {
  const char *name; int nsym; int L[5]; int strip[5][MAXL];
  int wild, scatter, bonus, coin; int wildReels, bonusReels;
  int pay[MAXS][6]; int scatPay[6]; int cascade; int ladBase[4]; int ladFs[4];
  const char *sym[MAXS];
} Machine;
#include "machines.h"

typedef struct { int c[5][3]; } Grid;

// ways evaluation; returns pay in fifths, sets removal mask (bit r*3+row)
static int64_t evalWays(const Machine *m, const Grid *g, int *rem) {
  int64_t pay = 0; int mask = 0;
  for (int s = 0; s < m->nsym; s++) {
    if (m->pay[s][3] == 0 && m->pay[s][4] == 0 && m->pay[s][5] == 0) continue;
    int64_t ways = 1; int k = 0; int n[5];
    for (int r = 0; r < 5; r++) {
      int cnt = 0;
      for (int y = 0; y < 3; y++) { int x = g->c[r][y]; if (x == s || (x == m->wild && r > 0)) cnt++; }
      if (!cnt) break; n[r] = cnt; ways *= cnt; k++;
    }
    if (k >= 3 && m->pay[s][k] > 0) {
      pay += ways * m->pay[s][k];
      for (int r = 0; r < k; r++) for (int y = 0; y < 3; y++) { int x = g->c[r][y]; if (x == s || (x == m->wild && r > 0)) mask |= 1 << (r*3+y); }
    }
  }
  *rem = mask; return pay;
}
static int countSym(const Grid *g, int s) { int n = 0; if (s < 0) return 0; for (int r = 0; r < 5; r++) for (int y = 0; y < 3; y++) if (g->c[r][y] == s) n++; return n; }
static int bonusTrig(const Machine *m, const Grid *g) {
  if (m->bonus < 0) return 0;
  for (int r = 0; r < 5; r++) if (m->bonusReels >> r & 1) { int f = 0; for (int y = 0; y < 3; y++) if (g->c[r][y] == m->bonus) f = 1; if (!f) return 0; }
  return 1;
}

// result of one full spin (base-game part only; features tallied separately)
typedef struct { int64_t pay; int scat, coins, bon, tumbles, postMask; int five; } Res;

// mode: 0 base, 1 fs-expanding (overworld), 2 fs-cascade (nether), 3 fs-sticky (end, uses preMask)
static Res spin(const Machine *m, const int st[5], int mode, int preMask) {
  Res R; memset(&R, 0, sizeof R);
  Grid g; int top[5];
  for (int r = 0; r < 5; r++) { top[r] = st[r]; for (int y = 0; y < 3; y++) g.c[r][y] = m->strip[r][(st[r]+y) % m->L[r]]; }
  if (mode == 1 || mode == 3) {
    int post = 0;
    for (int r = 1; r <= 3; r++) {
      int has = (mode == 3 && (preMask >> (r-1) & 1));
      for (int y = 0; y < 3; y++) if (g.c[r][y] == m->wild) has = 1;
      if (has) { for (int y = 0; y < 3; y++) g.c[r][y] = m->wild; post |= 1 << (r-1); }
    }
    R.postMask = post;
  }
  const int *lad = (mode == 2) ? m->ladFs : m->ladBase;
  int64_t total = 0; int step = 0;
  for (;;) {
    int rem; int64_t p = evalWays(m, &g, &rem);
    for (int s5 = 0; s5 < m->nsym; s5++) { if (!m->pay[s5][5]) continue; int ok = 1; for (int r = 0; r < 5 && ok; r++) { int f = 0; for (int y = 0; y < 3; y++) { int x = g.c[r][y]; if (x == s5 || (x == m->wild && r > 0)) f = 1; } ok = f; } if (ok) R.five |= 1 << s5; }
    if (!p) break;
    int mul = m->cascade ? lad[step < 3 ? step : 3] : 1;
    total += p * mul;
    if (!m->cascade) break;
    step++;
    // tumble
    for (int r = 0; r < 5; r++) {
      int keep[3], nk = 0, nr = 0;
      for (int y = 0; y < 3; y++) if (rem >> (r*3+y) & 1) nr++; else keep[nk++] = g.c[r][y];
      if (!nr) continue;
      int col[3], ci = 0;
      for (int j = nr; j >= 1; j--) col[ci++] = m->strip[r][((top[r]-j) % m->L[r] + m->L[r]) % m->L[r]];
      for (int j = 0; j < nk; j++) col[ci++] = keep[j];
      top[r] = ((top[r]-nr) % m->L[r] + m->L[r]) % m->L[r];
      for (int y = 0; y < 3; y++) g.c[r][y] = col[y];
    }
    if (step > 100) { fprintf(stderr, "runaway\n"); exit(1); }
  }
  R.tumbles = step;
  R.scat = countSym(&g, m->scatter);
  R.coins = countSym(&g, m->coin);
  R.bon = bonusTrig(m, &g);
  if (R.scat >= 3 && m->scatPay[R.scat > 5 ? 5 : R.scat]) total += m->scatPay[R.scat > 5 ? 5 : R.scat];
  R.pay = total;
  return R;
}

typedef struct {
  double N, sumPay, sumPay2, sumWays; double hitPay, hitAny;
  double scat[16], coins[16], bon, scatAndBon, coinAndScat;
  double tumb[64]; double buckets[12]; double maxPay;
  double post[8][2], postPay[8][2];
  double scatPaySum; double five[MAXS];
} Tally;

static const double BK[] = {0, 1e-9, 1, 2, 5, 10, 25, 50, 100, 250, 500, 1e18}; // in bets: buckets
int main(int argc, char **argv) {
  int mi = atoi(argv[1]); int mode = atoi(argv[2]); int pre = argc > 3 ? atoi(argv[3]) : 0;
  const Machine *m = &MACH[mi];
  Tally T; memset(&T, 0, sizeof T);
  int st[5];
  for (st[0] = 0; st[0] < m->L[0]; st[0]++)
  for (st[1] = 0; st[1] < m->L[1]; st[1]++)
  for (st[2] = 0; st[2] < m->L[2]; st[2]++)
  for (st[3] = 0; st[3] < m->L[3]; st[3]++)
  for (st[4] = 0; st[4] < m->L[4]; st[4]++) {
    Res R = spin(m, st, mode, pre);
    T.N++; T.sumPay += R.pay; T.sumPay2 += (double)R.pay * R.pay;
    int sc = R.scat > 15 ? 15 : R.scat; T.scat[sc]++;
    T.coins[R.coins > 15 ? 15 : R.coins]++;
    if (R.bon) T.bon++;
    if (R.bon && R.scat >= 3) T.scatAndBon++;
    if (R.coins >= 6 && R.scat >= 3) T.coinAndScat++;
    if (R.pay > 0) T.hitPay++;
    if (R.pay > 0 || R.scat >= 3 || R.bon || R.coins >= 6) T.hitAny++;
    T.tumb[R.tumbles > 63 ? 63 : R.tumbles]++;
    double pb = R.pay / 5.0; if (pb > T.maxPay) T.maxPay = pb;
    for (int b = 0; b < 11; b++) if (pb >= BK[b] && pb < BK[b+1]) { T.buckets[b]++; break; }
    for (int q = 0; q < m->nsym; q++) if (R.five >> q & 1) T.five[q]++;
    int rt = R.scat >= 3; T.post[R.postMask][rt]++; T.postPay[R.postMask][rt] += R.pay;
  }
  // JSON out
  printf("{\"machine\":\"%s\",\"mode\":%d,\"pre\":%d,\"N\":%.0f,\"sumPay\":%.0f,\"sumPay2\":%.6e,\"hitPay\":%.0f,\"hitAny\":%.0f,\"bon\":%.0f,\"scatAndBon\":%.0f,\"coinAndScat\":%.0f,\"maxPay\":%.1f,",
    m->name, mode, pre, T.N, T.sumPay, T.sumPay2, T.hitPay, T.hitAny, T.bon, T.scatAndBon, T.coinAndScat, T.maxPay);
  printf("\"scat\":["); for (int i = 0; i < 16; i++) printf("%s%.0f", i ? "," : "", T.scat[i]); printf("],");
  printf("\"coins\":["); for (int i = 0; i < 16; i++) printf("%s%.0f", i ? "," : "", T.coins[i]); printf("],");
  printf("\"tumb\":["); for (int i = 0; i < 20; i++) printf("%s%.0f", i ? "," : "", T.tumb[i]); printf("],");
  printf("\"buckets\":["); for (int i = 0; i < 11; i++) printf("%s%.0f", i ? "," : "", T.buckets[i]); printf("],");
  printf("\"five\":["); for (int i = 0; i < m->nsym; i++) printf("%s%.0f", i ? "," : "", T.five[i]); printf("],");
  printf("\"post\":["); for (int i = 0; i < 8; i++) printf("%s[%.0f,%.0f]", i ? "," : "", T.post[i][0], T.post[i][1]); printf("],");
  printf("\"postPay\":["); for (int i = 0; i < 8; i++) printf("%s[%.0f,%.0f]", i ? "," : "", T.postPay[i][0], T.postPay[i][1]); printf("]}\n");
  return 0;
}
