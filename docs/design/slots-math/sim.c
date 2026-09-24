// Full-game Monte Carlo (cross-check of the closed forms; volatility, tiers, cap).
// Build: gcc -O3 -march=native -DSIM -o sim sim.c   (includes engine.c pieces)
#define main engine_main
#include "engine.c"
#undef main
#include <math.h>

static uint64_t s[4];
static inline uint64_t rotl(uint64_t x, int k) { return (x << k) | (x >> (64 - k)); }
static uint64_t nxt(void) { uint64_t r = rotl(s[1] * 5, 7) * 9, t = s[1] << 17; s[2] ^= s[0]; s[3] ^= s[1]; s[1] ^= s[2]; s[0] ^= s[3]; s[2] ^= t; s[3] = rotl(s[3], 45); return r; }
static int rnd(int n) { return (int)(((nxt() >> 11) * (1.0 / 9007199254740992.0)) * n); }
static double u01(void) { return (nxt() >> 11) * (1.0 / 9007199254740992.0); }

// feature tables (fifths of bet; jackpot codes negative: -1 MINI -2 MINOR -3 MAJOR -4 GRAND; 0 = creeper/UP)
#include "features.h"

static double jpSeed[3][5], jpOwned[3][5]; // index by code 1..4
static double jpHits[5]; static double lastRaw;

static double pickTable(const int *val, const double *w, int n) { double t = 0; for (int i = 0; i < n; i++) t += w[i]; double x = u01() * t; for (int i = 0; i < n; i++) { if (x < w[i]) return i; x -= w[i]; } return n - 1; }

// returns spin win in bets (house: jackpots valued at seed), also owned-valued via pointer
static double doSpin(int mi, double *owned, int *fsTrig, int *bonTrig, int *maxTumb) {
  const Machine *m = &MACH[mi]; int st[5];
  for (int r = 0; r < 5; r++) st[r] = rnd(m->L[r]);
  Res R = spin(m, st, 0, 0);
  double win = R.pay / 5.0, jpH = 0, jpO = 0;
  *maxTumb = R.tumbles; *fsTrig = 0; *bonTrig = 0;
  int sc = R.scat > 5 ? 5 : R.scat;
  if (sc >= 3) {
    *fsTrig = 1;
    int rem = FS_SPINS[mi][sc], aw = rem, mask = 0; double fw = 0;
    while (rem > 0) {
      for (int r = 0; r < 5; r++) st[r] = rnd(m->L[r]);
      Res F = spin(m, st, FS_MODE[mi], mask);
      if (FS_MODE[mi] == 3) mask = F.postMask;
      fw += F.pay / 5.0 * FS_MULT[mi];
      rem--;
      if (F.scat >= 3) { int add = FS_RETRIG[mi]; if (add > FS_CAP[mi] - aw) add = FS_CAP[mi] - aw; rem += add; aw += add; }
      if (F.tumbles > *maxTumb) *maxTumb = F.tumbles;
    }
    win += fw;
  }
  int bon = (mi == 1) ? (R.coins >= HOLD_TRIG) : R.bon;
  if (bon) {
    *bonTrig = 1;
    if (mi == 0) { // pick until creeper
      for (int j = 0; j < PICK_BOARD; j++) {
        int k = pickTable(PICK_VAL, PICK_W, PICK_N);
        int v = PICK_VAL[k];
        if (v == 0) break;
        if (v > 0) win += v / 5.0; else { int c = -v; jpHits[c]++; jpH += jpSeed[mi][c]; jpO += jpOwned[mi][c]; }
      }
    } else if (mi == 1) { // hold & spin
      int n = R.coins, r = HOLD_RESPINS;
      while (n < 15 && r > 0) { int k = 0; for (int c = 0; c < 15 - n; c++) if (u01() < HOLD_P) k++; if (k) { n += k; r = HOLD_RESPINS; } else r--; }
      for (int c = 0; c < n; c++) { int k = pickTable(HOLD_VAL, HOLD_W, HOLD_N); int v = HOLD_VAL[k]; if (v > 0) win += v / 5.0; else { jpHits[-v]++; jpH += jpSeed[mi][-v]; jpO += jpOwned[mi][-v]; } }
      if (n == 15) { jpHits[4]++; jpH += jpSeed[mi][4]; jpO += jpOwned[mi][4]; }
    } else { // wheel rings
      int ring = 0;
      for (;;) {
        int k = pickTable(WHEEL_VAL[ring], WHEEL_W[ring], WHEEL_N[ring]); int v = WHEEL_VAL[ring][k];
        if (v == 0) { ring++; continue; }
        if (v > 0) win += v / 5.0; else { jpHits[-v]++; jpH += jpSeed[mi][-v]; jpO += jpOwned[mi][-v]; }
        break;
      }
    }
  }
  double cap = CAP[mi];
  double ownedWin = win + jpO; if (ownedWin > cap) ownedWin = cap;
  *owned = ownedWin;
  lastRaw = win;
  double capped = win > cap ? cap : win;
  return capped + jpH;
}

int main(int argc, char **argv) {
  int mi = atoi(argv[1]); double n = atof(argv[2]); uint64_t seed = strtoull(argv[3], 0, 10);
  s[0] = seed * 0x9E3779B97F4A7C15ULL + 1; s[1] = seed ^ 0xDEADBEEF; s[2] = 12345 + seed; s[3] = 0xABCDEF ^ (seed << 7);
  for (int i = 0; i < 20; i++) nxt();
  for (int c = 1; c <= 4; c++) { jpSeed[mi][c] = JP_SEED[mi][c]; jpOwned[mi][c] = JP_OWNED[mi][c]; }
  if (argc > 4) { // feature-only check: n features started with 3-scatter spins
    const Machine *m = &MACH[mi]; double S = 0, S2 = 0; int st[5];
    for (double i = 0; i < n; i++) {
      int rem = FS_SPINS[mi][3], aw = rem, mask = 0; double fw = 0;
      while (rem > 0) { for (int r = 0; r < 5; r++) st[r] = rnd(m->L[r]); Res F = spin(m, st, FS_MODE[mi], mask); if (FS_MODE[mi] == 3) mask = F.postMask; fw += F.pay / 5.0 * FS_MULT[mi]; rem--; if (F.scat >= 3) { int add = FS_RETRIG[mi]; if (add > FS_CAP[mi] - aw) add = FS_CAP[mi] - aw; rem += add; aw += add; } }
      S += fw; S2 += fw * fw; }
    double mu = S / n; printf("fs mean %.5f sd %.3f se %.5f\n", mu, sqrt(S2 / n - mu * mu), sqrt((S2 / n - mu * mu) / n)); return 0; }
  double sum = 0, sum2 = 0, sumO = 0, capHits = 0, uncapped = 0, fs = 0, bo = 0, hit = 0, maxw = 0;
  double tiers[8] = {0}; const double TB[] = {0, 1, 5, 15, 40, 100, 1e18};
  double sessBust = 0; double tumb[32] = {0};
  for (double i = 0; i < n; i++) {
    double o; int f, b, mt; double w = doSpin(mi, &o, &f, &b, &mt);
    sum += w; sum2 += w * w; sumO += o; fs += f; bo += b; if (w > 0) hit++;
    if (lastRaw > CAP[mi]) { capHits++; uncapped += lastRaw - CAP[mi]; }
    if (w > maxw) maxw = w;
    tumb[mt > 31 ? 31 : mt]++;
    for (int t = 0; t < 6; t++) if (w > TB[t] - 1e-12 && w < TB[t + 1] && (t || w > 0)) { tiers[t]++; break; }
  }
  printf("{\"mi\":%d,\"n\":%.0f,\"sum\":%.6f,\"sum2\":%.6f,\"sumOwned\":%.6f,\"capHits\":%.0f,\"capLoss\":%.6f,\"fs\":%.0f,\"bonus\":%.0f,\"hit\":%.0f,\"max\":%.3f,\"jp\":[%.0f,%.0f,%.0f,%.0f],\"tiers\":[",
    mi, n, sum, sum2, sumO, capHits, uncapped, fs, bo, hit, maxw, jpHits[1], jpHits[2], jpHits[3], jpHits[4]);
  for (int t = 0; t < 6; t++) printf("%s%.0f", t ? "," : "", tiers[t]);
  printf("],\"tumb\":["); for (int t = 0; t < 12; t++) printf("%s%.0f", t ? "," : "", tumb[t]); printf("]}\n");
  return 0;
}
