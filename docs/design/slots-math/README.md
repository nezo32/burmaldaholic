# slots-math — reproduces the numbers in `../SLOTS.md`

Design scripts, not part of the mod build. They need **python3 (stdlib only)** and **a C compiler
(gcc or clang)**, because enumerating 10⁸ stop combinations in pure Python is too slow. A pure-Python path
(`fact.py`, `end_fs.py`) covers every machine without tumbling reels.

| File | What it does |
|---|---|
| `gen.py` | **Single source of the design.** Symbols, reel counts, paytables, features and jackpots. Builds the reel strips deterministically (seeded) and writes `machines.h`, `features.h`, `strips.md` and `design.json`. |
| `engine.c` | Exact enumeration of every stop combination (§1.1 ways, §3.2 tumble algorithm). `./engine <machine 0/1/2> <mode> [mask]`: mode 0 = base game, 2 = Nether free spin, 3 = End free spin with the sticky mask. Output is JSON. |
| `analysis.py` | Runs `engine` (compiles it, caches results in `cache.json`) and applies the closed forms and Markov chains of §7.3 to print the full RTP breakdown. |
| `fact.py`, `end_fs.py` | Pure-Python factorised exact EV (for machines without tumbling) and the Void Walker sticky-wild chain. |
| `sim.c`, `runmc.sh` | Full-game Monte-Carlo, used for volatility, win tiers and the cap. `./sim <m> <spins> <seed>`; with a 4th argument `fs`, it runs Void Walker free spins only. |

## How to run

```sh
python3 gen.py                  # strips + headers (instant); strips.md must equal SLOTS.md Appendix A
python3 analysis.py             # exact numbers, all machines (~3–4 min on 4 cores; cached after)
python3 analysis.py end         # one machine
python3 end_fs.py               # End base + Void Walker values, pure Python (<1 s)
gcc -O3 -march=native -o sim sim.c -lm
./runmc.sh                      # 10^9 spins per machine, 4 processes (~4 min per machine)
./sim 2 25000000 1 fs           # Void Walker feature-only check (~1 min)
```

Enumeration sizes: Overworld 40⁵ = 1.02·10⁸ (~10 s), Nether 32⁵ = 3.4·10⁷ with tumbles (~5 s),
End 45⁵ = 1.85·10⁸ × 9 runs (base + 8 sticky masks, ~2–3 min).

## What reproduces which part of SLOTS.md

| SLOTS.md | Produced by |
|---|---|
| §3 counts and paytables; Appendix A strips | `gen.py` (`strips.md`) |
| §4 trigger rates, expected spins, feature and bonus means, jackpot frequencies | `analysis.py` |
| §7.1 RTP contribution table, owned RTP, `r_cap` | `analysis.py` (`total`, `total_owned`) |
| §7.2 hit frequency (exact) | `analysis.py` (`hit_pay`, `hit_any`) |
| §7.2 std. dev., win tiers, largest win; §7.4 cap | `runmc.sh` (combine the `mc_*.json` files) |
| §7.3 `e`, `ρ`, E[s], V(0,n,n), Treasure Hunt, Hoard and Wheel values | `analysis.py`, `end_fs.py` |
| §7.5 exact integer test vectors | raw `engine` JSON (`sumPay`, `hitPay`, `hitAny`, `scat`, `coins`, `tumb`, `five`, `post`) |
| §6.3 buy-feature RTP | `fs_ev_trig[3] / price + contribution` from `analysis.py` |
| §9.3 Showdown mean points | 10 × `total_owned` |

Changing a number in `gen.py` and rerunning `analysis.py` gives the new §7.1 table. Any changed
value must then be copied into SLOTS.md, including Appendix A if the counts or seeds change the strips.
