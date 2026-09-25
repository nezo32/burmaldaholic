# Burmaldaholic — Slots v2: 243-Ways Video Slots [slots]

> **Java-only (2026-09-24).** Bedrock support was dropped: Bedrock sections, lanes and tasks were removed. An inline
> note that still names Bedrock (the former TypeScript twin) is historical context and does not apply.

Status: **v2.0 draft, implementation-ready after review**. Owner: slots game design.
Audience: Java (Fabric) team, testers, localization.

> **Supersedes `GAME_DESIGN.md` §8 (Slots), Appendix A (slot test vectors), the slot rows of §17,
> the slot parts of §13.1 rule 2, §18.2 (slot worst case), §19 (`three_sevens`), `UI.md` §6,
> the `slots` sections of `CONFIG.md` / `STRINGS.md`, and `PVP.md` §5 (Slot Showdown) and §10.2
> (Jackpot Race target).** Everything else in those files still applies (wager lifecycle §4.1,
> VIP §12, chaos safety §13.4, streak §14, owned casinos §18.2). Where this file and an older
> file disagree about slots, this file wins. When the doc owner merges, §8 of `GAME_DESIGN.md`
> becomes a one-line pointer to this file.

Inputs: `docs/research/animation.md` (what the engine can animate; modern slot mechanics). The
presentation plan (§10) stays inside what that research lists as **stable** (Java: GUI sprites,
scissor, BER, particles, custom sounds; Bedrock: DDUI `CustomForm` + Observables with a classic
form fallback, one `slot_reels` entity per cabinet, particles, titles, camera fade/shake).

Conventions are those of `GAME_DESIGN.md` (chips are integers, RTP = return / staked, all
randomness server-side, 20 ticks = 1 s).

---

## 0. Summary

The 3×3 payline machines are replaced by **three 5×3, 243-ways video slots**, each a different
Minecraft dimension with its own symbols, reel strips, paytable, volatility and feature set.
**Block ids stay the same** (no world migration, recipes and structures unchanged); only their
machine, name and model change:

| Block id (unchanged) | New machine (`machine id`) | Volatility | RTP (house) | Hit freq | Signature features | VIP | Bets (chips) |
|---|---|---|---|---|---|---|---|
| `slot_machine_copper` | **Overworld Riches** (`overworld`) | low | **95.074 %** | 1 in 2.55 | stacked Totem wilds · Compass free spins (all wins ×2) · **Treasure Hunt** pick-me chests | — | 5 – 100 |
| `slot_machine_gold` | **Nether Inferno** (`nether`) | medium | **95.394 %** | 1 in 2.68 | **Tumbling reels** with multiplier ladder ×1→×2→×3→×5 (free spins ×2→×4→×6→×10) · **Piglin's Hoard** Hold & Spin coins · buy feature | — | 10 – 500 |
| `slot_machine_netherite` | **End Void** (`end`) | high | **96.534 %** | 1 in 2.79 | **Void Walker** free spins with **expanding sticky** Dragon-Egg wilds · **Dragon Wheel** 3-ring bonus wheel · buy feature | Gold | 50 – 5 000 |

Every machine has four **progressive jackpots** (Mini · Minor · Major · Grand) funded by a share
of every bet, awarded only through its bonus game. Every machine has a **max-win cap** per spin
(500× / 2 000× / 5 000× the bet), which is also the owned-casino reservation.

Numbers developers test against are in §7 (exact enumeration totals, closed-form feature values,
Monte-Carlo tolerances). The generator/verification scripts that produced them are described in
§7.6 so the numbers can be reproduced.

---

## 1. Common rules (all three machines)

### 1.1 Grid, reels and the 243 ways

- 5 reels × 3 rows. Each reel `r` (1…5) has an explicit, circular **reel strip** `S_r` of length
  `L_r` (§3, Appendix A). A spin draws one **stop** `t_r` uniformly from `0 … L_r − 1` per reel,
  independently. Row `y` (0 = top, 2 = bottom) of reel `r` shows `S_r[(t_r + y) mod L_r]`.
- **Ways evaluation.** For every paying symbol `P` (never Wild/Scatter/Bonus/Coin):
  `n_r(P)` = number of the 3 cells of reel `r` showing `P` **or a Wild** (Wild only counts on
  reels where it can appear, which is always reels 2–4). Let `k` = the largest number such that
  `n_1 … n_k` are all ≥ 1. If `k ≥ 3` and the paytable has a pay `pay(P, k)`, the symbol wins
  `ways(P) = n_1 × n_2 × … × n_k` ways, and pays `pay(P, k) × ways(P) × bet`.
  All winning symbols pay (sum). Maximum 243 ways per symbol per evaluation.
- Wilds never land on reels 1 and 5, so every way starts with a real symbol on reel 1 and there
  are no "wild-only" ways and no double counting.
- **Scatter**: counts anywhere on the window; at most one per reel per window on the base strips
  (strips enforce a cyclic spacing ≥ 3). **Bonus symbols** (chest, crystal) only count on the
  reels listed for their machine; **Coins** (Nether) count anywhere.
- **Pays are multiples of 0.2 × the total bet** (tables below list them as `×bet`). All bet
  levels are multiples of 5, so every way win, scatter pay, feature prize and multiplied win is a
  **whole number of chips — no rounding** anywhere except progressive jackpot awards (§5).
  Configured bet lists that are not multiples of 5 are rejected on load (§12 validation).
- The **total bet** (`bet`) is the only stake. There are no lines to choose.

### 1.2 One spin = one drawn "tape"

A spin is resolved completely at the moment it is confirmed (GAME_DESIGN §4.1 "drawn"):

```
CONFIRM (debit bet, contribute to jackpots) → DRAW TAPE → apply streak re-draw (§8.2)
→ reserve/award jackpots (§5.3) → PERSIST {tape, total, jackpotAwards} → PRESENT (§10) → SETTLE (credit)
```

The tape contains every random value of the spin: the 5 base stops (plus the tumble chain, which
is a deterministic function of the stops), every free spin's stops, the bonus board (Treasure Hunt
contents in reveal order, Hold & Spin respin draws, wheel results), and coin values. Nothing is
drawn later; the presentation only replays the tape. Consequences:

- A disconnect, a closed screen, a server stop or casino mode turning off never changes the
  result: the round is settled from the tape (offline-safe, GAME_DESIGN §4.1).
- **Pick-me honesty.** The Treasure Hunt contents are drawn as an i.i.d. sequence; the *i*-th
  chest the player opens reveals the *i*-th entry, whichever chest is clicked. Because the
  contents are i.i.d., this is identical in distribution to fixed hidden contents, and the paytable
  help says so plainly (`gui.burmaldaholic.slots.help.pick_fair`). Chests left closed at the end
  reveal the remaining entries of the sequence (real draws, dimmed) — never invented values.
- Owned-casino reservation (§8.6) and the max-win cap are computed on the tape before the round
  is shown.

### 1.3 Max-win cap

`capTotal = cap × bet` with cap = 500 (Overworld), 2 000 (Nether), 5 000 (End)
(`slots.<m>.maxWinMultiple`). The spin's total **excluding house progressive jackpot awards**
(which are paid from the pool) is `min(total, capTotal)`. When the cap is reached during a feature
the feature ends at once (remaining free spins/respins are forfeited) and the presentation shows
**MAX WIN**. At owned machines fixed jackpot prizes are inside the cap (§8.6). The cap's effect on
RTP is measured in §7.4 (< 0.003 %; Overworld and Nether never reached it in 10⁹ simulated spins).

### 1.4 Presentation policies (normative)

1. **Server decides first, then animates** (research §1). Filler symbols in the scrolling reels
   come from the **real strip** (the reel scrolls through `S_r` and lands on `t_r`), so the last
   three cells are always the paid result.
2. **Honest anticipation only** (§10.3): a reel slows down only because of symbols already visible
   on earlier reels. No near-miss weighting, no fake scatters.
3. **No loss disguised as a win.** A total return below the bet is shown as
   "Returned %1$s" with a muted tick, no fanfare, no win tier (`gui.burmaldaholic.slots.returned`).
4. **Skip never skips the result** — it compresses time.
5. No text baked into textures; every word and number comes from lang keys or digit glyphs.

---

## 2. Symbols

Each machine has 8 paying symbols (4 high, 4 low), one Wild, one Scatter and one Bonus or Coin
symbol. Codes are used in the strips (Appendix A). Glyphs are for Bedrock text/DDUI and Java
fallback text (`UI.md` §0.1 rule: code prepends glyphs as separate components).
Glyph sheets: normal `U+E2xx` (`font/glyph_E2.png`), win-glow `U+E3xx` (same offsets),
motion-blur `U+E4xx` (same offsets).

### 2.1 Overworld Riches

| Code | Symbol id | Name (EN) | Kind | Glyph |
|---|---|---|---|---|
| WD | `totem` | Totem (Wild) | Wild, reels 2–4, stacked 2-high | U+E200 |
| SC | `compass` | Compass | Scatter, all reels | U+E201 |
| BN | `chest` | Treasure Chest | Bonus, reels 1, 3, 5 only | U+E202 |
| DI | `diamond` | Diamond | high | U+E203 |
| EM | `emerald` | Emerald | high | U+E204 |
| GO | `gold_ingot` | Gold Ingot | high | U+E205 |
| IR | `iron_ingot` | Iron Ingot | high | U+E206 |
| AP | `apple` | Apple | low | U+E207 |
| CA | `carrot` | Carrot | low | U+E208 |
| WH | `wheat` | Wheat | low | U+E209 |
| BE | `sweet_berries` | Sweet Berries | low | U+E20A |

### 2.2 Nether Inferno

| Code | Symbol id | Name (EN) | Kind | Glyph |
|---|---|---|---|---|
| WD | `lava_bucket` | Lava Bucket (Wild) | Wild, reels 2–4 | U+E210 |
| SC | `ghast_tear` | Ghast Tear | Scatter, all reels | U+E211 |
| CN | `piglin_coin` | Piglin Coin | Coin (carries a value), all reels, stacked 2-high | U+E212 |
| SK | `wither_skull` | Wither Skeleton Skull | high | U+E213 |
| BR | `blaze_rod` | Blaze Rod | high | U+E214 |
| MC | `magma_cream` | Magma Cream | high | U+E215 |
| QZ | `quartz` | Nether Quartz | high | U+E216 |
| NW | `nether_wart` | Nether Wart | low | U+E217 |
| CF | `crimson_fungus` | Crimson Fungus | low | U+E218 |
| WF | `warped_fungus` | Warped Fungus | low | U+E219 |
| GD | `glowstone` | Glowstone Dust | low | U+E21A |

### 2.3 End Void

| Code | Symbol id | Name (EN) | Kind | Glyph |
|---|---|---|---|---|
| WD | `dragon_egg` | Dragon Egg (Wild) | Wild, reels 2–4; expands and sticks in free spins | U+E220 |
| SC | `ender_eye` | Eye of Ender | Scatter, all reels | U+E221 |
| BN | `end_crystal` | End Crystal | Bonus, reels 2, 3, 4 only | U+E222 |
| DH | `dragon_head` | Dragon Head | high, stacked 2-high | U+E223 |
| EL | `elytra` | Elytra | high | U+E224 |
| SS | `shulker_shell` | Shulker Shell | high | U+E225 |
| CH | `chorus_fruit` | Chorus Fruit | high | U+E226 |
| EP | `ender_pearl` | Ender Pearl | low | U+E227 |
| PU | `purpur` | Purpur Block | low | U+E228 |
| ER | `end_rod` | End Rod | low | U+E229 |
| ES | `end_stone` | End Stone | low | U+E22A |

Shared glyphs: jackpot badges Mini/Minor/Major/Grand `U+E230–U+E233`, chest closed/open
`U+E234/U+E235`, creeper face `U+E236`, empty Hold & Spin cell `U+E237`, wheel pointer `U+E238`,
sticky-wild frame `U+E239`, multiplier plate `U+E23A`.

---

## 3. The machines

Paytables are **× total bet per way** (3 / 4 / 5 of a kind). Scatter pays are × total bet,
anywhere, and are added to the spin (also during free spins, where the free-spin multiplier
applies).

### 3.1 Overworld Riches (`overworld`, block `slot_machine_copper`) — low volatility

Strips: 5 × 40 stops (Appendix A.1). Symbol counts per reel:

| Reel | WD | SC | BN | DI | EM | GO | IR | AP | CA | WH | BE |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | – | 2 | 3 | 3 | 3 | 4 | 4 | 5 | 5 | 5 | 6 |
| 2 | 4 (2 stacks of 2) | 1 | – | 3 | 3 | 4 | 4 | 5 | 5 | 5 | 6 |
| 3 | 4 (2 stacks of 2) | 1 | 2 | 3 | 3 | 4 | 4 | 5 | 5 | 4 | 5 |
| 4 | 4 (2 stacks of 2) | 1 | – | 3 | 3 | 4 | 4 | 5 | 5 | 5 | 6 |
| 5 | – | 2 | 3 | 3 | 3 | 4 | 4 | 5 | 5 | 5 | 6 |

| Symbol | 3 | 4 | 5 |
|---|---|---|---|
| Diamond | 0.8 | 2.0 | 4.0 |
| Emerald | 0.6 | 1.2 | 2.4 |
| Gold Ingot | 0.4 | 0.8 | 1.6 |
| Iron Ingot | 0.4 | 0.8 | 1.6 |
| Apple, Carrot, Wheat, Sweet Berries | 0.2 | 0.4 | 0.8 |
| Compass (scatter, anywhere) | 1× | 10× | 50× |

**Features**
- **Free spins ("Night Watch")**: 3 / 4 / 5 Compasses → **8 / 10 / 15 free spins**, all wins **×2**
  (incl. scatter pays). 3+ Compasses during free spins **retrigger +8**. At most 50 free spins
  awarded per feature (`fsCap`). Free spins use the base strips; chests are inert.
- **Treasure Hunt** (pick-me): a Chest on each of reels 1, 3 and 5 → a board of 15 chests. The
  player opens chests one at a time until a **Creeper** is revealed (the hunt ends, the creeper
  pays nothing) or all 15 are open. Each opened chest reveals one draw from:

  | Content | Prize | Weight |
  |---|---|---|
  | Coins | 1× bet | 30 000 |
  | Coins | 2× | 22 000 |
  | Coins | 3× | 14 000 |
  | Coins | 5× | 9 000 |
  | Coins | 10× | 3 500 |
  | Coins | 25× | 800 |
  | MINI jackpot gem | Mini | 600 |
  | MINOR jackpot gem | Minor | 150 |
  | MAJOR jackpot gem | Major | 20 |
  | GRAND jackpot gem | Grand | 3 |
  | Creeper | ends the hunt | 22 000 |
  | **Total** | | **102 073** |

  Several gems of the same tier in one hunt each award (the second from the reset pool).

### 3.2 Nether Inferno (`nether`, block `slot_machine_gold`) — medium volatility

Strips: 5 × 32 stops (Appendix A.2). Symbol counts per reel:

| Reel | WD | SC | CN | SK | BR | MC | QZ | NW | CF | WF | GD |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | – | 1 | 2 | 2 | 3 | 3 | 3 | 5 | 5 | 4 | 4 |
| 2 | 2 | 1 | 2 | 2 | 2 | 3 | 3 | 4 | 4 | 5 | 4 |
| 3 | 2 | 1 | 3 | 2 | 2 | 3 | 3 | 4 | 4 | 4 | 4 |
| 4 | 2 | 1 | 2 | 2 | 2 | 3 | 3 | 4 | 4 | 5 | 4 |
| 5 | – | 1 | 2 | 2 | 3 | 3 | 3 | 5 | 5 | 4 | 4 |

| Symbol | 3 | 4 | 5 |
|---|---|---|---|
| Wither Skeleton Skull | 0.8 | 2.0 | 8.0 |
| Blaze Rod | 0.6 | 1.2 | 4.0 |
| Magma Cream | 0.4 | 0.8 | 2.0 |
| Nether Quartz | 0.4 | 0.8 | 1.6 |
| Nether Wart, Crimson Fungus | 0.2 | 0.4 | 0.6 |
| Warped Fungus, Glowstone Dust | 0.2 | 0.2 | 0.6 |

Ghast Tears do not pay by themselves (they only trigger).

**Tumble (cascade) — exact algorithm**
1. Evaluate ways on the window. If there is no win, the spin's reel phase ends.
2. Pay `win × ladder[step]` where `step` = 0 for the first evaluation, 1 for the first tumble, …;
   `ladder` = **[×1, ×2, ×3, ×5]** in the base game (×5 for every later step) and
   **[×2, ×4, ×6, ×10]** in free spins.
3. Remove every cell that took part in any winning way (the paying symbol and any Wild on reels
   1…k of that symbol). Scatters and Coins never explode.
4. Per reel, the remaining cells fall down (keeping order) and the `m` empty cells are refilled
   **from the strip above the window**: with `top_r` the strip index of the current top cell,
   the new top cells are `S_r[top_r − m] … S_r[top_r − 1]` (mod `L_r`), then `top_r −= m`.
   The whole chain is therefore a deterministic function of the 5 stops (exactly enumerable).
5. Go to 1.

Triggers are checked on the **final window** (when tumbling stops).

**Features**
- **Free spins ("Inferno Spins")**: 3 / 4 / ≥5 Ghast Tears → **12 / 15 / 20 free spins** with the
  free-spin ladder ×2/×4/×6/×10 (resets every free spin). 3+ Tears on a free spin's final window
  **retrigger +5**; cap 60 awarded. Coins are inert in free spins.
- **Piglin's Hoard** (Hold & Spin): **6 or more Coins** on the final base window. Every Coin
  carries a value drawn when it lands:

  | Coin | Value | Weight |
  |---|---|---|
  | 1× | 1× bet | 400 |
  | 2× | 2× | 250 |
  | 3× | 3× | 150 |
  | 5× | 5× | 100 |
  | 10× | 10× | 50 |
  | 25× | 25× | 12 |
  | MINI | Mini jackpot | 8 |
  | MINOR | Minor jackpot | 2 |
  | MAJOR | Major jackpot | 0.3 |

  (Weights total 972.3; implement them ×10 as integers: 4000/2500/1500/1000/500/120/80/20/3.)
  The coins lock; the other cells are cleared. **3 respins**: on each respin every empty cell
  independently lands a coin with probability **p = 0.04** (`slots.nether.hold.coinChance`). Any
  new coin resets the respins to 3. The feature ends when respins reach 0 or all 15 cells are
  full. Pays the sum of all coin values + jackpots; **filling all 15 cells also wins the Grand**.
  Coins that show on base spins without a trigger are decoration only (they are still real draws).
- If both trigger on the same spin: Hoard first, then free spins.
- **Buy feature**: pay **18.4 × bet** to start Inferno Spins with 12 spins (§6.3).

### 3.3 End Void (`end`, block `slot_machine_netherite`) — high volatility, Gold VIP

Strips: 5 × 45 stops (Appendix A.3). Symbol counts per reel:

| Reel | WD | SC | BN | DH | EL | SS | CH | EP | PU | ER | ES |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | – | 1 | – | 2 (1 stack) | 3 | 4 | 5 | 7 | 7 | 8 | 8 |
| 2 | 1 | 1 | 2 | 2 (1 stack) | 3 | 4 | 5 | 6 | 6 | 7 | 8 |
| 3 | 1 | 1 | 3 | 2 (1 stack) | 3 | 4 | 4 | 6 | 7 | 7 | 7 |
| 4 | 1 | 1 | 2 | 2 (1 stack) | 3 | 4 | 5 | 6 | 6 | 7 | 8 |
| 5 | – | 1 | – | 2 (1 stack) | 3 | 4 | 5 | 7 | 7 | 8 | 8 |

| Symbol | 3 | 4 | 5 |
|---|---|---|---|
| Dragon Head | 2.0 | 8.0 | 30.0 |
| Elytra | 1.0 | 4.0 | 12.0 |
| Shulker Shell | 0.8 | 2.0 | 6.0 |
| Chorus Fruit | 0.6 | 1.6 | 5.0 |
| Ender Pearl, Purpur Block | 0.4 | 0.6 | 2.0 |
| End Rod, End Stone | 0.2 | 0.6 | 1.2 |
| Eye of Ender (scatter, anywhere) | 2× | 10× | 50× |

**Features**
- **Void Walker free spins**: 3 / 4 / 5 Eyes → **9 / 11 / 14 free spins**. Any Dragon Egg that
  lands on reel 2, 3 or 4 **expands** to fill its reel **before evaluation** and **stays (sticky)
  for the rest of the feature**. A sticky reel shows WWW (a scatter under it is covered and does
  not count). 3+ Eyes retrigger **+4**; cap 40 awarded. Crystals are inert in free spins.
  The feature state is the 3-bit mask of sticky reels (§7.3 uses it for the exact value).
- **Dragon Wheel** (bonus wheel): an End Crystal on each of reels 2, 3 and 4 → a three-ring wheel.
  Rings are physically divided into equal segments; the pointer stops on one segment (uniform).
  `UP` moves to the next ring and spins it.

  | Ring | Segments (count) | Total |
  |---|---|---|
  | Outer "End Stone" | 10× (4), 12× (3), 15× (3), 20× (2), 25× (2), 40× (1), 75× (1), **MINI** (2), **UP** (2) | 20 |
  | Middle "Purpur" | 30× (4), 50× (4), 75× (3), 100× (2), **MINOR** (2), **UP** (1) | 16 |
  | Core "Dragon" | 150× (4), 250× (3), 500× (1), **MAJOR** (3), **GRAND** (1) | 12 |

  Wedge order (clockwise from the pointer at rest; tests assert the counts):
  outer `10 UP 12 15 MINI 10 20 12 25 10 40 15 UP 12 20 MINI 15 10 75 25`;
  middle `30 50 MINOR 75 30 100 50 UP 30 75 MINOR 50 100 30 75 50`;
  core `150 MAJOR 250 150 GRAND 250 MAJOR 150 500 250 MAJOR 150`.
- **Buy feature**: pay **109 × bet** to start Void Walker with 9 spins (§6.3).

---

## 4. Feature summaries (defaults, exact where stated)

| | Overworld Riches | Nether Inferno | End Void |
|---|---|---|---|
| Free spins trigger (3 / 4 / 5 scatters), 1 in | 116.05 / 2 180 / 105 350 (any: **110.07**) | 109.89 / 2 079 / 105 850 (any: **104.29**) | 387.44 / 10 848 / 759 375 (any: **373.92**) |
| Free spins awarded | 8 / 10 / 15, ×2, +8 retrigger | 12 / 15 / 20, ladder ×2–×10, +5 | 9 / 11 / 14, sticky expanding wilds, +4 |
| Expected spins played per trigger (3 / 4 / 5) | 8.6270 / 10.7837 / 16.1754 | 12.6044 / 15.7555 / 21.0073 | 9.0400 / 11.0449 / 14.0506 |
| Mean free-spin win per trigger (3 / 4 / 5), × bet | 12.8589 / 16.0737 / 24.1102 | 17.1981 / 21.4977 / 28.6636 | 102.4669 / 170.1760 / 311.7943 |
| Retrigger chance per free spin | 0.908 5 % | 0.959 0 % | 0.164 % (all reels free) |
| Bonus game | Treasure Hunt, 1 in **131.69** | Piglin's Hoard, 1 in **260.28** | Dragon Wheel, 1 in **281.25** |
| Mean bonus win per trigger (× bet, excl. jackpots) | 9.5629 | 21.3886 | 21.9187 |
| Bonus notes | expected chests opened 3.5443; creeper on first pick 21.55 % | mean final coins 7.8468; all 15 filled 0.04437 % of triggers | UP to middle 10 %; to core 0.625 % |
| Jackpot 1 in … spins (Mini / Minor / Major / Grand) | 4 959 / 19 834 / 148 756 / 991 709 | 4 031 / 16 126 / 107 505 / 586 578 | 2 812 / 22 500 / 180 000 / 540 000 |
| Void Walker: all three reels sticky by the end | — | — | 9.99 % / 15.16 % / 23.91 % of features |

---

## 5. Progressive jackpots

### 5.1 Pools

- **Four pools per machine type per world** (12 in total), stored in world data
  (Java `SavedData` `burmaldaholic_slots`; Bedrock world dynamic property
  `burmaldaholic:slots:jp`). Each pool is `seed + increment`, kept as two numbers plus the hidden
  fractional remainder of contributions.
- **Reference bet** `ref` = the machine's top bet (`slots.<m>.jackpot.refBet`, default 100 / 500 /
  5 000). Seeds are multiples of `ref`:

| Machine | Mini | Minor | Major | Grand | Contribution per bet (Mini / Minor / Major / Grand = total) |
|---|---|---|---|---|---|
| Overworld (ref 100) | 10× = 1 000 | 25× = 2 500 | 100× = 10 000 | 500× = 50 000 | 0.4 % / 0.3 % / 0.2 % / 0.1 % = **1.0 %** |
| Nether (ref 500) | 10× = 5 000 | 30× = 15 000 | 150× = 75 000 | 1 000× = 500 000 | 0.5 % / 0.4 % / 0.35 % / 0.25 % = **1.5 %** |
| End (ref 5 000) | 15× = 75 000 | 50× = 250 000 | 250× = 1 250 000 | 2 500× = 12 500 000 | 0.5 % / 0.5 % / 0.6 % / 0.9 % = **2.5 %** |

- Every paid spin and every buy-feature purchase adds `contribution × stake` to each tier's
  increment (fractions accumulate in the remainder, as in the old §8.5).

### 5.2 Award

- `r = min(1, bet / ref)`. A jackpot hit awards `floor((seed + increment) × r)`. After the award
  the increment becomes `increment × (1 − r)` (the unpaid share stays) and the seed part is
  unchanged (the bank mints `seed × r`). Several hits in one spin are applied in tape order.
- Why this is exactly fair to every bet size: per unit staked, the seed part pays
  `P(hit) × seedMult` for every `bet ≤ ref`, and the increment part returns exactly the
  contribution rate in the long run (conservation: everything contributed is eventually paid).
  So the jackpot RTP is `Σ P(hit_t) × seedMult_t + Σ contribution_t` — the numbers in §7.1.
- **Timing.** The award is computed and the pool debited at **draw** time (inside the persisted
  round, so two players can never win the same money). The meter shown to *other* players keeps
  showing `pool + pending unrevealed awards` until the winner's reveal plays, so nobody sees the
  meter drop before the winner does.
- Announcement and chaos (Major, Grand): server-wide chat + title for the winner; §8.4.
- Admin: Casino Menu → Admin → "Reset jackpot" resets a chosen machine's four pools to seed
  (increments are removed from the economy; logged).
- **Owned casinos**: no progressive (as before). Jackpot results pay **fixed** multiples of the
  bet: Overworld 10 / 25 / 100 / 250, Nether 10 / 30 / 150 / 500, End 15 / 50 / 250 / 1 000
  (`slots.<m>.jackpot.owned`), inside the max-win cap, and no contribution is taken.

### 5.3 Migration from v1 pools

On first load of v2: the old Golden Reels pool's increment (`pool − old seed`) is added to the
Nether Grand increment; the old Netherite pool's increment to the End Grand increment. Old seeds
are dropped (they were bank money). Logged once.

---

## 6. Bets, ante, buy feature, autoplay

### 6.1 Bet levels (VIP-gated)

| Machine | Bet ladder (`slots.<m>.bets`) | Default bet | Requirement |
|---|---|---|---|
| Overworld | 5, 10, 20, 50, 100 | 10 | none |
| Nether | 10, 20, 50, 100, 250, 500 | 20 | none |
| End | 50, 100, 250, 500, 1 000, 2 500, 5 000 | 100 | Gold VIP (`slots.end.minVipTier` = 2) |

Offered levels are those `≤ min(VIP tier max, owner max)` and `≥ owner min` (GAME_DESIGN §4.2,
§18.2). Bronze reaches 100, Silver 250, Gold 1 000, Platinum 2 500, Diamond+ 5 000. Owners pick
their min/max from the same ladder.

### 6.2 Ante / feature boost — **not offered** (decision)

An ante doubles the number of strip sets, the math tables and the Bedrock forms for little extra
fun; the buy feature below covers the "I want the feature" wish with RTP parity. Revisit later.

### 6.3 Buy feature (Nether, End)

| Machine | Price | What you get | EV of what you get | Buy RTP (incl. contribution) | Machine RTP |
|---|---|---|---|---|---|
| Nether | **18.4 × bet** | Inferno Spins, 12 spins (as a 3-Tear trigger, no scatter pay) | 17.1981 × bet | **94.968 %** | 95.394 % |
| End | **109 × bet** | Void Walker, 9 spins (as a 3-Eye trigger) | 102.4669 × bet | **96.506 %** | 96.534 % |

Rules: parity means the buy RTP must be ≤ the machine RTP and within 0.5 % of it (`slots.validateRtp`
checks it). The price is one wager (`stake = price`): it must be ≤ `tier max × slots.buyFeature.tierMaxMultiple`
(default 25) and the underlying bet must be on the ladder. It contributes to jackpots, counts for
VIP lifetime wagered, cashback (`houseEdge` = 1 − buy RTP), contracts (`wager`; one `spin_slots`
spin) and statistics. **No streak re-draw** on bought features (§8.2). The max-win cap is relative
to the underlying bet. Owned machines: allowed; the reservation is the same `cap × bet`.
Overworld has no buy (low-volatility, entry-level machine). Setting `slots.buyFeature.enabled` false
hides the button everywhere.

### 6.4 Autoplay, turbo, skip

- **Autoplay**: 10 / 25 / 50 / 100 spins. Stop conditions (checked after each settled spin):
  - always: a jackpot, a MAX WIN, not enough balance for the next spin, the machine closes, casino
    mode off, the player walks > 8 blocks away or opens another screen;
  - "Stop on feature" (default on): free spins or bonus triggered;
  - "Stop on single win ≥" off / 10× / 50× / 100× (default 50×);
  - **Loss limit (mandatory)**: stop when `startBalance − balance ≥ limit`; choices 10 / 25 / 50 /
    100 × bet (default 25×). Autoplay cannot start without one.
  - Treasure Hunt during autoplay opens chests automatically (one every 600 ms); because contents
    are i.i.d. in reveal order this changes nothing.
- **Turbo** (per-player preference, `slots.turboAllowed`): all reel/feature timings × 0.5.
- **Skip / slam stop**: pressing Spin (Java: Space/click) during a spin brings every reel to its stop within 200 ms (bounce kept); during a
  roll-up it jumps to the final amount; during features it fast-forwards the current step. It
  never skips the result or a required choice.

---

## 7. The math (normative numbers and how to reproduce them)

### 7.1 RTP by contribution (house machines, defaults)

All values are percent of the total amount staked. "Exact" = full enumeration or closed form
(no sampling). The only non-exact item is the max-win cap effect (§7.4), which is below 0.003 %.

| Component | Overworld Riches | Nether Inferno | End Void | Method |
|---|---|---|---|---|
| Base game ways wins (incl. tumbles for Nether) | 73.159 400 | 68.222 821 | 56.211 147 | exact enumeration of all stops |
| Scatter pays (base) | 1.367 930 | — | 0.614 979 | exact enumeration |
| Free spins | 11.840 624 | 16.710 770 | 28.057 181 | enumeration of one free spin × Markov chain (§7.3) |
| Bonus game (non-jackpot prizes) | 7.260 196 | 8.216 694 | 7.793 704 | closed form / Markov chain (§7.3) |
| Jackpots — seed part (`Σ P(hit) × seedMult`) | 0.445 359 | 0.744 099 | 1.357 407 | closed form |
| Jackpots — contributions (increment part) | 1.000 000 | 1.500 000 | 2.500 000 | conservation (§5.2) |
| **Total RTP** | **95.073 509** | **95.394 383** | **96.534 419** | |
| House edge | 4.926 % | 4.606 % | 3.466 % | |
| Owned-casino RTP (fixed jackpots, no pools) | 94.048 300 | 93.809 143 | 93.756 641 | same, §5.2 fixed values |
| Streak `r_cap = 0.99 / RTP − 1` (§8.2) | 0.041 300 | 0.037 797 | 0.025 541 | GAME_DESIGN §14 |

Replaces the three slot rows of GAME_DESIGN §17 and the per-tier `houseEdge` used by cashback
(§12): cashback uses 1 − the house RTP above (4.926 % / 4.606 % / 3.466 %), and for a buy-feature
purchase 1 − its buy RTP (5.032 % / 3.494 %).

### 7.2 Volatility profile (Monte-Carlo, 10⁹ spins per machine; End 2 × 10⁹)

| | Overworld | Nether | End |
|---|---|---|---|
| Volatility class | low | medium | high |
| Hit frequency (any pay > 0), exact | 39.212 % (1 in 2.55) | 37.357 % (1 in 2.68) | 35.854 % (1 in 2.79) |
| Hit or feature, exact | 39.850 % | 38.264 % | 36.094 % |
| Std. dev. of spin return (× bet) | 3.14 | 3.77 | 14.63 |
| Any feature, 1 in (exact) | 60.0 | 74.5 | 160.5 |
| Win < 1× (shown as "Returned"), 1 in | 4.9 | 5.0 | 5.1 |
| 1× – < 5× (plain win), 1 in | 6.6 | 7.0 | 7.0 |
| **Nice Win** 5× – < 15×, 1 in | 32 | 34 | 59 |
| **Big Win** 15× – < 40×, 1 in | 118 | 101 | 255 |
| **Mega Win** 40× – < 100×, 1 in | 1 133 | 761 | 926 |
| **Epic Win** ≥ 100×, 1 in | 45 000 | 22 000 | 1 420 |
| Largest win seen (× bet) | 650 (incl. a Grand at seed) | 1 206 | 5 000 (cap) |
| Max-win cap reached, 1 in | never | never | 17 000 000 |
| Void Walker feature win: mean / std. dev. (× bet) | — | — | 102.47 / 243 |

### 7.3 Feature values — closed forms developers implement in tests

Notation: `N` = number of stop combinations, `P(k)` = probability of a trigger with `k` scatters.

**Free spins with independent spins** (Overworld, Nether). Let `e` = mean win of one free spin
(× bet, multiplier applied), `ρ` = P(retrigger on a free spin), `R` = spins added, `C` = cap.
Expected spins played `f(n, n)` with
`f(0, a) = 0`, `f(m, a) = 1 + ρ·f(m − 1 + d, a + d) + (1 − ρ)·f(m − 1, a)`, `d = min(R, C − a)`.
Feature value = `e × f(n, n)` (per-spin wins do not change which later spins are played, so
linearity applies).

| | Overworld | Nether |
|---|---|---|
| `e` | 1.490 546 60 (= 2 × base mean incl. scatter pays) | 1.364 456 42 (enumeration with the ×2/×4/×6/×10 ladder: Σpay = 228 917 800 fifths over 33 554 432 stops) |
| `ρ` | 0.009 085 08 (= base P(≥3 scatters)) | 0.009 590 09 |

**Void Walker** (End). State = sticky mask `s ∈ {0…7}` over reels 2–4. For each `s`, enumerate all
45⁵ stops with the sticky reels forced to WWW and every other Wild expanded: gives `E[s]` (mean
spin win) and `T[s](s', retrig)`. Then
`V(s, m, a) = E[s] + Σ T[s](s', rt) · V(s', m − 1 + rt·d, a + rt·d)`, `V(·, 0, ·) = 0`.
Per-state mean spin win (× bet): E[0] 1.1102, E[1] 6.2018, E[2] 6.3516, E[3] 37.6412,
E[4] 4.8395, E[5] 28.1880, E[6] 28.8070, E[7] 178.3606 (exact sums in §7.5). These per-state means are
rounded for reading; tests use the exact sums (e.g. E[3] from its exact sum is 37.6408, not the 37.6412
printed here).
Feature values: `V(0, 9, 9)` = 102.466 892, `V(0, 11, 11)` = 170.175 953, `V(0, 14, 14)` = 311.794 284.

**Treasure Hunt.** `q` = P(not creeper) = 80 073 / 102 073. Expected chests opened
`EN = Σ_{j=1..15} q^j = 3.544 250 32`. Mean prize per non-creeper chest = 216 000 / 80 073 bet.
Value = `EN × 216 000 / 80 073` = **9.560 752** bet. Jackpot gems per hunt = `EN × w_t / 80 073`.

**Piglin's Hoard.** Markov chain on (coins `n`, respins `r`): from `(n, r)` with `n < 15, r > 0`,
`k ~ Binomial(15 − n, 0.04)`; `k > 0 → (n + k, 3)`, else `(n, r − 1)`. Start distribution from the
base enumeration (6: 108 845, 7: 16 483, 8: 3 316, 9: 240, 10: 32 of 33 554 432 stops).
Mean final coins 7.846 836; P(all 15) = 0.000 443 73 per trigger. Mean coin value =
`Σ_{value coins} v·w / Σ_all w` = 2 650 / 972.3 = 2.725 496 bet (jackpot coins count 0 here). Value = mean final coins × that = **21.386 522** bet.

**Dragon Wheel.** `EV = EV_outer + P(UP_o)·(EV_middle + P(UP_m)·EV_core)`
= 16.3 + 0.1 × (46.5625 + 0.0625 × 154.1667) = **21.919 792** bet
(outer 326/20, middle 745/16, core 1850/12 in bet units; jackpot segments count 0 here and are in the jackpot rows).

### 7.4 The max-win cap

Measured by Monte-Carlo (the only sampled number): End loses 0.003 0 % RTP to the cap (59 spins
in 10⁹ reached it); Overworld and Nether never reached it. Tests treat the §7.1 totals as the
**uncapped** exact values and allow the cap in the Monte-Carlo tolerance.

### 7.5 Test vectors (exact integers; fifths of the bet, i.e. 5 = 1 × bet)

Full enumeration of the base game = every stop combination on the Appendix A strips, evaluated
with §1.1 (+ §3.2 tumbles for Nether), scatter pays included, no features.

| | Overworld | Nether | End |
|---|---|---|---|
| Stop combinations `N` | 102 400 000 | 33 554 432 | 184 528 125 |
| Σ spin pay (fifths) | 381 579 930 | 114 458 900 | 524 300 931 |
| Spins with pay > 0 | 40 153 130 | 12 534 784 | 66 159 981 |
| Spins with pay > 0 or a trigger | 40 806 404 | 12 839 101 | 66 603 861 |
| Scatter count 0/1/2/3/4/5 (final window) | 58 554 868 / 34 909 500 / 8 005 320 / 882 360 / 46 980 / 972 | 18 942 904 / 11 569 517 / 2 720 221 / 305 336 / 16 137 / 317 | 130 691 232 / 46 675 440 / 6 667 920 / 476 280 / 17 010 / 243 |
| Bonus triggers | 777 600 | 128 916 (≥ 6 coins) | 656 100 |
| Largest base spin pay | 92.8 × bet | 165 × bet | 960 × bet |
| Nether tumbles 0/1/…/8 | | 21 019 648 / 8 840 192 / 2 651 136 / 760 832 / 192 512 / 70 656 / 16 384 / 1 024 / 2 048 | |
| 5-of-a-kind of the top symbol (spins) | Diamond 238 140 | Wither Skull 37 969 (any tumble step) | Dragon Head 5 488 |

End free-spin enumeration per start mask `s` (45⁵ stops each): Σ pay (fifths) =
s0 1 024 280 910 · s1 5 722 030 350 · s2 5 860 237 815 · s3 34 728 903 900 · s4 4 465 132 290 ·
s5 26 007 038 550 · s6 26 578 191 825 · s7 164 562 181 875. From s0 the new mask is 0/1/2/3/4/5/6/7
with counts (no retrigger | retrigger) 149 752 557|275 643, 10 702 935|13 365, 10 709 577|6 723,
765 207|243, 10 709 577|6 723, 765 207|243, 765 369|81, 54 675|0.

**Tolerances.**
- Enumeration totals: **exact integer equality** (Java `long`, TypeScript `number` is exact below
  2⁵³; accumulate in fifths).
- Closed forms (§7.3): relative error < 10⁻⁹ (double precision).
- Monte-Carlo of the whole game (CI nightly, reference RNG): 10⁸ spins per machine;
  assert `|RTP_mc − (total − contributions)| < 4 × σ/√n`, i.e. < 0.13 % (Overworld),
  0.15 % (Nether), 0.59 % (End). Feature-only runs (10⁷ Void Walker features) must hit
  102.467 ± 0.31.
- `slots.validateRtp` on load: recompute §7.1 with the configured tables (the enumeration takes
  < 30 s single-threaded in Java for the default strips; Bedrock uses the per-reel factorised form
  `E[pay] = Σ_P Σ_k pay(P,k) · Π_{r≤k} E[n_r] · P(n_{k+1}=0)`, which is exact for non-tumbling
  machines, and for Nether ships the default numbers and re-checks only if the Nether tables were
  changed, by a 10⁶-spin sample). Warn loudly (log + admin page) if any machine's RTP > 0.99 or a
  buy RTP > its machine RTP; never auto-fix.

### 7.6 Reproducing the numbers

The design scripts (Python generator + C enumerator/simulator, ≈ 600 lines) are not part of the
build. Their algorithm is fully described above: strips from Appendix A; §1.1 evaluation; §3.2
tumble algorithm; §7.3 chains. Any implementation that matches §7.5 exactly is correct.

---

## 8. Integration with the rest of the game

### 8.1 Wager lifecycle, disconnects, persistence

GAME_DESIGN §4.1 applies with "drawn" = the whole tape (§1.2). The persisted record per machine
is `{v:2, machine, bet, price?, tape, total, jackpotAwards[], startTick}`; tapes are compact (base
stops; per free spin 5 stops; bonus draws) — worst case (End, 40 free spins) < 1 200 characters.
Records written by v1 (3×3 grids) are settled at their stored payout by the kept v1 evaluator
(`LegacySlots`, removed one release later).

### 8.2 Streak (§14)

A spin (base + all features + jackpots) is one outcome. If its total return < bet, the whole
spin is re-drawn once with probability `r = min(r_raw, r_cap)` (§7.1 row; `RTP_game` = the
house total RTP). Bought features are **never** re-drawn (their RTP already equals the
machine's). The bound `RTP' ≤ 0.99` therefore holds for every machine.

### 8.3 Golden Hour (§13.3)

Net win = spin return − stake (buy: − price), **excluding progressive jackpot awards** (pool
money). Bonus = `floor(netWin × (m − 1))`, per-player cap as before, paid by the bank (also at
owned machines).

### 8.4 Chaos triggers (replaces §13.1 rule 2 for slots)

At most **one** event per spin, after settlement, by priority; safety rules §13.4 and the
per-player cooldown apply (if blocked, the payout still happens; nothing is rerolled).

| Priority | Outcome | Event | Frequency (default) |
|---|---|---|---|
| 1 | Major or Grand jackpot | `diamond_rain` (winner) + `chip_shower` for players ≤ 16 blocks + server announcement | Overworld 1 in 129 000 · Nether 1 in 90 000 · End 1 in 135 000 |
| 2 | Mini or Minor jackpot | `chip_shower` (winner) | ≈ 1 in 4 000 / 3 200 / 2 500 |
| 3 | Free spins triggered with **5 scatters** | `golden_hour` (server-wide; cooldown respected, otherwise nothing) | 1 in 105 350 / 105 850 / 759 375 |
| 4 | Overworld: Treasure Hunt ends on its **first** chest (Creeper) | `mob_wave` ("the creeper brought friends") | 1 in 611 |
| 4 | Nether: 5 Wither Skulls in any tumble step | `mob_wave` (Nether composition) | 1 in 884 |
| 4 | Nether: a base spin with ≥ 6 tumbles | `lucky_buff` | 1 in 1 725 |
| 4 | End: 5 Dragon Heads | `random_teleport` ("the dragon flings you") | 1 in 33 600 |
| 4 | End: Void Walker ends with all three reels sticky | `xp_fountain` | 1 in 3 676 |
| 5 | Big-win rule §13.1-3 (net ≥ 50× and ≥ 500 chips) | 30 % `lucky_buff` | as before |

`chaos.event.<event>.enabled` off disables the trigger. PvP never fires chaos (PVP.md §3.4).
An event while the slot screen/form is open is deferred as §13.4 says (max 600 t, then skipped);
the machine screen counts as a casino screen, also during autoplay.

### 8.5 VIP

End Void requires Gold (`slots.end.minVipTier`); refused with `gui.burmaldaholic.error.vip_required`.
The bet ladder is cut at the tier max (§6.1). VIP perk text `gui.burmaldaholic.vip.perk.netherite_slots`
is renamed in meaning to "End Void slots" (§13). Netherite-tier win particles (§12) play on
Mega/Epic wins.

### 8.6 Owned casinos (§18.2)

- Worst case per spin = **`cap × bet`** (500× / 2 000× / 5 000×), which already includes free
  spins, bonus prizes, and the fixed owned jackpot prizes, because the cap bounds the whole spin.
  The same for a buy-feature purchase (cap × underlying bet). `reserved += cap × bet` before the
  stake is accepted; released at settlement.
- Insolvency threshold = `cap × owner min bet` of the cheapest linked slot.
- Stakes and payouts go through the bankroll; jackpots are the fixed amounts of §5.2 (no pool,
  no contribution). Golden Hour and cashback remain bank-paid.
- Owner settings: min/max bet (from the ladder), open/closed, **buy feature on/off** (default on),
  autoplay on/off (default on). These are per-table owner settings stored with the owned table (not config
  keys); labels `gui.burmaldaholic.charter.table_slots_buy` / `.table_slots_autoplay`, refusals
  `gui.burmaldaholic.slots.error.buy_disabled` / `.error.autoplay_disabled` (Bedrock: `OwnedTableInfo.slotsBuy`
  / `.slotsAutoplay`).

### 8.7 Contracts

- `spin_slots` (existing id): counts **paid** spins and buy-feature purchases, not free spins or
  respins. Text unchanged.
- **New** `slots_feature`: "Trigger 2 slot features (free spins or a bonus game)", target base 2,
  reward base 45, weight 4 (a bought feature does not count).
- Contract pool weight sum grows by 4 (GAME_DESIGN §3.4.4 table gets one row).

### 8.8 Statistics

Per player per machine: spins, wagered, returned, features, best win (× bet), jackpots by tier.
Shown on the machine's Paytable → "My stats" page.

### 8.9 Bots

None. Slots are solo; Slot Showdown bots are unchanged in behaviour (§9.5).

---

## 9. Slot Showdown v2 (replaces PVP.md §5; Jackpot Race target §10.2)

### 9.1 Rules

- Anchor: a slot machine; its **machine type** (Overworld / Nether / End) is the match machine.
  Joiners use a machine of the same type within `pvp.slots.linkRadius`. End needs Gold VIP.
  Entry, spins per player N and lobby rules are unchanged (PVP.md §3, §5.1).
- Each participant gets N spins **drawn exactly like a solo spin at bet 1 unit**, including
  tumbles, free spins and the bonus game (Treasure Hunt contents are revealed automatically). No
  money moves per spin; the result becomes **points**:
  `spinPoints = 10 × (spin win ÷ bet)` (always an even integer), with jackpots replaced by fixed
  points = 10 × the owned fixed multiples (Overworld 100 / 250 / 1 000 / 2 500; Nether 100 / 300 /
  1 500 / 5 000; End 150 / 500 / 2 500 / 10 000). Max-win cap applies in points
  (10 × cap). No streak, no chaos, no pools, no contribution (PVP.md §3.4).
- Features in a match play at **compressed speed**: free spins shown as a 3-second fast reel
  montage with a running total, bonus as its summary banner (≤ 60 t in total, skippable).

### 9.2 Scoring modifiers

- **HOT symbol**: each round the tape holds one of the machine's 8 paying symbols (uniform). Ways
  wins of that symbol score ×2 (in the base spin and in that spin's free spins).
- **Hazards** (new; replace the old symbol-bound specials): each spin also draws one hazard from
  `pvp.slots.hazardWeights` = none 94 / **KABOOM** 2 / **SWAP** 2 / **TIME WARP** 2. The hazard
  "drops" onto the cabinet after the reels land (a creeper, an ender pearl or a clock icon).
  KABOOM halves your total before this spin; SWAP swaps with the leader after the round; TIME WARP
  doubles your next spin. The round resolution order of PVP.md §5.2 steps 1–5 is unchanged.
  `pvp.slots.kaboom` / `pearlSwap` off set that hazard's weight to 0 (the weight moves to "none").
- **Underdog Boost** unchanged.
- Winner: highest total; tie-break 1 = more spins with points > 0; tie-break 2 = best single
  spin; then split.

### 9.3 Fairness and reference numbers

All draws are i.i.d. per participant and symmetric, so Lemma 1 holds (EV = −rake/N). Mean points
per spin without HOT or hazards (= 10 × owned RTP): Overworld **9.404 830**, Nether
**9.380 914**, End **9.375 664**. P(KABOOM) per spin = 0.02.

### 9.4 Tape

Per spin: 5 base stops, hazard, total points, feature code + compressed feature result (free-spin
count and points, bonus points). Settlement uses only the points; the rest drives presentation.
Largest tape (6 players × 10 spins) < 3 000 characters (fits one Bedrock dynamic property).

### 9.5 UI

As PVP.md §5.5, with 5×3 mini-reels (Java panels 124 × 76: 5 × 3 cells at **16 px**, using the 16 × 16 base art 1:1 — ⚠ CHANGED (D2, `animation/slots.md` §0.3): was 14 px, which forced fractional scaling), a feature badge
("FS 12", "HOARD", "WHEEL") and the hazard icon on each panel. Bedrock: a 5×3 grid does not fit the one-line action bar,
so the line shows your **middle row** plus your points; the full grid is in the Standings form.

### 9.6 Jackpot Race (NICE) target

`pvp.race.target` default becomes `feature`: the first player(s) whose spin triggers any free spins
or bonus game win. Other values: `jackpot` (any jackpot result), `five_top` (5 of the top symbol).

---
## 10. Presentation

> ⚠ CHANGED 2026-09-24 (lead decision, `docs/architecture/animation.md` §1): six presentation-only changes
> from `docs/design/animation/slots.md` §0.3 are accepted and applied below — D1 symbol sprites 40 × 40 at 1:1
> (+ 32 × 32 compact sheet), D2 Showdown mini-cells 16 px (§9.5), D3 in-world tumbles keep the first window
> (§10.6), D4 slot tier words passed to the shared celebration API (§10.1), D5 Hoard filler never scrolls a
> coin (§10.4), D6 Treasure Hunt chest rattles until the server confirms (§10.4). No rule, number or RTP
> changes. `animation/slots.md` wins on frame-level detail; this file still wins on rules and numbers.

All timings are at normal speed; **turbo = × 0.5**; **reduce motion** (`anim.reduceMotion`,
research §8) replaces reel scrolling with a 300 ms cross-fade, removes shake/flash/blur/bounce and
makes roll-ups instant. Bedrock rounds every time **up to whole ticks** (50 ms).

### 10.1 Win tiers (by total spin win ÷ bet; `slots.bigWinTiers` = [5, 15, 40, 100])

| Tier | Range | Banner key | Java | Sound |
|---|---|---|---|---|
| Returned | 0 < win < 1× | `slots.returned` ("Returned 40") | amount only, no pulse | `slots.returned` (muted tick) |
| Win | 1× – < 5× | `slots.win` | winning cells pulse, roll-up | `slots.win_small` |
| **Nice Win** | 5× – < 15× | `slots.tier.nice` | small banner pop 250 ms, coin particles (20) | `slots.win_nice` |
| **Big Win** | 15× – < 40× | `slots.tier.big` | banner on a new stratum, blur behind, coins (40) | `slots.big_win` |
| **Mega Win** | 40× – < 100× | `slots.tier.mega` | + flash (≤ 30 % alpha, once), confetti (60) | `slots.mega_win` |
| **Epic Win** | ≥ 100× | `slots.tier.epic` | + GUI shake 400 ms, fireworks at the cabinet (BER) | `slots.epic_win` |
| **Max Win** | = cap | `slots.max_win` | Epic presentation + "MAX WIN" plate | `slots.max_win` |
| **Jackpot** | any tier | `slots.jackpot.won` | own 3 s celebration after the spin's roll-up | `jackpot` |

⚠ CHANGED (D4, `animation/slots.md` §0.3): slot screens show the **slot keys** of this table (`slots.tier.*`,
`slots.returned`, `slots.max_win`), not the generic `gui.burmaldaholic.fx.tier.*` words. The shared
`CelebrationOverlay` takes the caller's tier words and the caller's threshold
table (`WinTierTable.SLOTS` = these 5 / 15 / 40 / 100) — `docs/architecture/animation.md` §4.

The roll-up **upgrades the banner as it passes each threshold** (Nice → Big → Mega → Epic) — the
main excitement beat. Roll-up duration `d = clamp(600 + 900 × log10(1 + win/bet), 600, 8000)` ms,
value `shown = win × outCubic(t)`, coin tick every step crossing, ≤ 15 ticks/s, pitch +1 % per
tick (cap +40 %). Free-spin and bonus totals get their own roll-up at feature end, then the spin
total roll-up. Skip jumps to the end.

### 10.2 Reel timeline (base and free spins)

| Step | Time (ms) | Easing / detail |
|---|---|---|
| Spin-up | 0–120 | −0.15 cell back-kick, then accelerate (research §2.4 `reelPos`) |
| Full speed | until stop | Java 25 cells/s with `_blur` sprites |
| Reel r stops | 600 + 150 × (r − 1) → 600 / 750 / 900 / 1 050 / 1 200 | last 350 ms `outBack(1.2)`; stop sound at u ≈ 0.8 |
| Anticipation | see §10.3 | +1 000 ms between later stops, glow frame, `slots.anticipation` loop rising in pitch |
| Win display starts | last stop + 150 | non-winning cells dim to 40 % in 150 ms |
| Symbol win cycle | 700 per winning symbol | pulse `1 + 0.08 sin(4πt)`; label "Diamond ×5 · 12 ways · 48" |
| Nether tumble step | 750 | explode 250 (scale 1 → 1.25, alpha → 0, `ember` particles) · fall 300 (gravity + `outBounce`) · pause 200; multiplier plate steps up 200 ms `outBack` |
| End expanding wild | 300 | column grows scale-Y 1/3 → 1 `outBack`; sticky frame fades in 200 ms, `void_mote` particles loop on sticky reels |
| Free spins intro | 2 000 (skippable after 500) | banner + spin count; theme swap (Overworld night, Nether deep red, End starfield); music `slots.fs_music.<m>` |
| Each free spin | 0.8 × base timings | plates: spins left, multiplier (ladder for Nether), feature total |
| Free spins outro | roll-up + 1 500 hold | "FREE SPINS WIN %1$s" |

Server-side: the spin is settled **after** the timeline ends (Java: server timer from the tape's
computed length; Bedrock: `system.runTimeout`), or immediately on skip/close/disconnect (the
result is already persisted).

### 10.3 Honest anticipation — `anticipationPlan(tape) → stopTimes[]` (pure)

After reel k has stopped, if the cells **already visible** on reels 1…k contain:
- ≥ 2 scatters and at least 1 later reel could still add a scatter; or
- (Overworld) chests on reels 1 and 3 and reel 5 not stopped; (End) crystals on reels 2 and 3 and
  reel 4 not stopped; or
- (Nether) ≥ 4 coins and the remaining reels could still reach 6,

then every later reel stops 1 000 ms after the previous one (instead of 150 ms). Nothing else ever
changes stop times. Unit test: for 10⁶ random tapes, (a) the shown grid equals the
paid grid, (b) anticipation happens **iff** the condition above holds on already-stopped reels,
(c) the scrolling filler of reel r is the strip sequence ending at `t_r`.

### 10.4 Bonus-game presentation

| Feature | Timeline |
|---|---|
| Treasure Hunt | board intro 600 ms (15 chests drop, 40 ms stagger); each open: the clicked chest **rattles until the server confirms the reveal** (one round trip; the *i*-th entry is sent only on the *i*-th pick, §1.2), then opens in 400 ms (6-frame lid flipbook) — ⚠ CHANGED (D6, `animation/slots.md` §0.3; was a fixed 400 ms flipbook started on click) + prize pop 300 ms `outBack` + `slots.chest_open`; Creeper: 600 ms swell + hiss, white flash (not with reduce motion), puff particles, no damage; end: remaining chests open dimmed (50 %), 80 ms stagger; total roll-up. Jackpot gem: gem glyph flies to its meter 500 ms. |
| Piglin's Hoard | intro 800 ms (non-coins fade out, coins lock with gold frame); respin 900 ms (empty cells mini-spin 500 ms, 30 ms stagger, through a **neutral ember blur** that never scrolls a coin past the window — ⚠ CHANGED (D5, `animation/slots.md` §0.3): a coin sliding past an empty cell would be a fake near-miss); new coin: `slots.coin_land` + counter dots flash back to 3 (200 ms); end: collect sweep 120 ms per coin into the total; all 15 filled: GRAND 3 000 ms. |
| Dragon Wheel | intro 700 ms (wheel rises); outer spin 4 500 ms `outCubic`, peg ticks with pointer deflect 12° `outElastic`; **UP** → zoom 800 ms `inOutSine` into the next ring; middle 4 000 ms; core 5 000 ms; result glow 600 ms. The wheel's final angle is the tape's segment (drawn), plus a uniform offset inside the wedge for looks. |
| Jackpot | 3 000 ms: meter explodes into coins, title, fireworks, broadcast |

### 10.5 Java — `SlotMachineScreen` v2 (client screen, server-driven)

Panel **400 × 240** (compact < 400 × 240 window: 320 × 220). Sprites from
per-machine symbol sheets `textures/gui/slots/<machine>_symbols.png` with **40 × 40** frames (16 × 16 pixel art ×2 + a 4 px effect margin) drawn **1:1 in the 44 px cells**, and a separate **32 × 32** sheet for compact mode (base, `blur`, 8 `win`, 6 `idle` frames) — ⚠ CHANGED (D1, `animation/slots.md` §0.3 and §9.1): was 48 × 48 sprites scaled into 44 px cells, which drops pixel rows and shimmers while scrolling under nearest filtering;
cabinet frame nine-slice per machine, drum gradient (research §2.4).

```
┌ JACKPOTS ─ [MINI 1 040] [MINOR 2 612] [MAJOR 10 480] [GRAND 51 220] ─────────────┐ y 4–22, 4 × 94 px
│ ┌ feature ┐  ┌──────── reels 5 × 44 px = 220 × 132 ────────┐  ┌ win ──────────┐ │
│ │ FS 7/12 │  │  ▣ ▣ ▣ ▣ ▣                                   │  │ WIN 1 240      │ │ side panels 80 px
│ │ ×4      │  │  ▣ ▣ ▣ ▣ ▣                                   │  │ Last 60        │ │
│ │ ×2 ×4 ×6│  │  ▣ ▣ ▣ ▣ ▣                                   │  │ Paytable       │ │
│ └─────────┘  └──────────────────────────────────────────────┘  └────────────────┘ │
│ [−] Bet 50 [+]   [Buy 920]  [Auto]  [Turbo]            ( SPIN )  56 × 40        │ y 190–236
│ ⛁ 12 500                                              🔥 Lucky ×4 · ◆ Gold     │ BalanceBar
└────────────────────────────────────────────────────────────────────────────────┘
```
- **Russian fit**: every label width = `max(min, textWidth + 8)` (UI.md §0.1), budgeted at 1.45 ×
  the English width: meters 94 px hold «ГРАНД 12 500 000» at the default font (≈ 88 px); side
  panels wrap at 76 px; buttons flow-wrap to a second row if needed; banners are nine-slice
  panels sized to the text (`slots.tier.mega` RU «МЕГАВЫИГРЫШ!» ≈ 1.4 × EN). Banner typography
  uses the `burmaldaholic:banner` bitmap font (Latin + Cyrillic), never baked words.
- Compact (320 × 220): cells 32 px (160 × 96), side panels become one line above the reels,
  meters show Grand + Major (others in the meter tooltip).
- Treasure Hunt: 5 × 3 chest grid in place of the reels (click any chest). Hoard: the reel window
  becomes 15 mini-reels. Wheel: overlay stratum, 200 px wheel drawn with `pose().rotate` wedges.
- Paytable: scrollable overlay: symbol sprite + pays computed from config × current bet, feature
  rules, jackpot rules, `help.pick_fair`, RTP line ("Return to player: 95.07 %"), my stats.
- Input: Space/Enter = Spin/Skip; A = autoplay dialog; T = turbo; Esc closes (never cancels a
  confirmed spin).
- Client may **start the spin-up before the result packet** (research §2.11); lands only on the
  received tape.

**Java in-world (spectators): `SlotCabinetRenderer` (BER)** on all three blocks. Sync once per spin
(`SpinSync`: startTick, stops, tumble grids, per-free-spin stops + sticky mask, feature summary,
anticipation stop ticks) via `getUpdatePacket`; spectators within 32 blocks see the reels on the
cabinet face (5 × 3 item-model symbols on scrolling drums), tumbles, sticky reels, a Hoard grid,
the wheel on top of the End cabinet, win tier text (`submitText`), and particles. Lands at the same
tick as the player's screen. Idle "attract mode": marquee `.mcmeta` lights; never fake wins.
Jackpot meters: a `TextDisplay`-free BER text line above progressive cabinets, updated ≤ 1/s.
Block state `win=none|small|big|jackpot` swaps the marquee texture for 3 s.

### 10.7 Sound cues (same event ids in custom .ogg, vanilla aliases until delivered)

| Event id | When | Notes |
|---|---|---|
| `slots.spin_loop` | reels at speed | quiet whirr, stops with the last reel (replaces `slot_spin`; the old id stays as alias) |
| `slots.reel_stop` | each reel stop | pitch ladder 1.0 / 1.12 / 1.26 / 1.5 / 1.68 |
| `slots.scatter_land` | scatter lands | pitch 1.0 / 1.26 / 1.5 for the 1st / 2nd / 3rd+ |
| `slots.bonus_land` | chest / crystal / coin lands | coin: short clink |
| `slots.anticipation` | anticipation | rising loop, stopped on land |
| `slots.returned` / `slots.win_small` / `slots.win_nice` / `slots.big_win` / `slots.mega_win` / `slots.epic_win` / `slots.max_win` | tiers §10.1 | fanfare stem upgrades with the tier |
| `slots.rollup_tick`, `slots.rollup_end` | roll-up | ≤ 15/s |
| `slots.fs_intro`, `slots.fs_outro`, `slots.fs_music.<machine>` | free spins | music is a stream loop |
| `slots.wild_expand`, `slots.wild_stick` | End expanding/sticky | |
| `slots.tumble`, `slots.mult_up` | Nether tumble step, ladder step | |
| `slots.chest_open`, `slots.creeper_hiss` | Treasure Hunt | hiss has no explosion sound |
| `slots.coin_land`, `slots.respin_reset` | Hoard | |
| `slots.wheel_tick`, `slots.wheel_up` | Dragon Wheel | tick pitch rises as it slows |
| `jackpot` (existing) | jackpot | |

Loss is silent. All in one sound category (research §8).

---

## 11. Blocks, worldgen, crafting, migration

- Block ids, recipes and structure placements are **unchanged**; only names, models, textures and
  behaviour change (table §0). Village Casino: Overworld Riches ×3 + Nether Inferno ×1; Piglin
  Parlor: Nether Inferno ×2; End City lounge: End Void ×2 — each now matches its dimension.
- Models: cabinet 1 × 2 blocks visual (hitbox 1 block, as before), 5-reel face; End cabinet has a
  crystal wheel on top. Marquee `.mcmeta`.
- Persisted v1 rounds: §8.1. Jackpot pools: §5.3. Config: v1 `slots.*` keys are ignored with one
  warning listing them.
- Advancement `three_sevens` is retired; players who had it are granted `top_five` on join.

---

## 12. Config keys

Replaces the `slots` section of `CONFIG.md`. `<m>` ∈ `overworld`, `nether`, `end`. Family keys use
the templates in §13.

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `slots.enabled` | bool | true | — | All slot machines. |
| `slots.<m>.enabled` | bool | true | — | One machine type (disabled cabinets show "Out of order"). |
| `slots.<m>.bets` | list<int> | §6.1 | 1–8 entries, each 5–10⁶, multiples of 5, sorted | Bet ladder. Not a multiple of 5 → the list is rejected (default kept). |
| `slots.<m>.defaultBet` | int | 10 / 20 / 100 | on the ladder | Clamped to the nearest ladder value. |
| `slots.end.minVipTier` | int | 2 | 0–5 | 2 = Gold. |
| `slots.nether.minVipTier` / `slots.overworld.minVipTier` | int | 0 | 0–5 | |
| `slots.<m>.maxWinMultiple` | int | 500 / 2000 / 5000 | 50–100 000 | Max-win cap and owned reservation (§1.3, §8.6). |
| `slots.<m>.strips` | list<string> | Appendix A | exactly 5 strings of codes | Advanced. Validated: known codes, Wild only on reels 2–4, bonus only on its reels, scatter spacing ≥ 3. |
| `slots.<m>.pays` | map<symbol,list<double>> | §3 tables | each 0–10 000, multiples of 0.2 | 3/4/5-of-a-kind × bet per way. |
| `slots.<m>.scatterPays` | list<double> | [1,10,50] / [0,0,0] / [2,10,50] | each 0–10 000, integers | × bet for 3/4/5 scatters. |
| `slots.<m>.freeSpins` | list<int> | [8,10,15] / [12,15,20] / [9,11,14] | each 0–100 | Spins for 3/4/5 scatters. |
| `slots.<m>.freeSpins.retrigger` | int | 8 / 5 / 4 | 0–100 | |
| `slots.<m>.freeSpins.cap` | int | 50 / 60 / 40 | 1–500 | Max spins awarded per feature. |
| `slots.overworld.freeSpins.multiplier` | int | 2 | 1–10 | |
| `slots.nether.tumble.ladder` | list<int> | [1,2,3,5] | 4 entries, each 1–100 | Base game. |
| `slots.nether.tumble.ladderFree` | list<int> | [2,4,6,10] | 4 entries, each 1–100 | Free spins. |
| `slots.overworld.pick.board` | int | 15 | 3–30 | Chests on the board. |
| `slots.overworld.pick.weights` | map<string,int> | §3.1 table | each 0–10⁷ | Keys `x1 x2 x3 x5 x10 x25 mini minor major grand creeper`. |
| `slots.nether.hold.trigger` | int | 6 | 3–15 | |
| `slots.nether.hold.respins` | int | 3 | 1–10 | |
| `slots.nether.hold.coinChance` | double | 0.04 | 0.0–0.5 | Per empty cell per respin. |
| `slots.nether.hold.coinWeights` | map<string,int> | ×10 of §3.2 | each 0–10⁷ | Keys `x1 x2 x3 x5 x10 x25 mini minor major`. |
| `slots.end.wheel.outer` / `.middle` / `.core` | list<string> | §3.3 wedge orders | 4–32 entries each | Tokens: an integer multiple, `MINI MINOR MAJOR GRAND`, `UP` (not in core). |
| `slots.<m>.jackpot.refBet` | int | 100 / 500 / 5000 | 5–10⁶ | Full jackpot at this bet or above. |
| `slots.<m>.jackpot.seed` | map<tier,int> | §5.1 | each 0–100 000 | Multiples of `refBet`. |
| `slots.<m>.jackpot.contribution` | map<tier,double> | §5.1 | each 0.0–0.05 | Fraction of every stake. |
| `slots.<m>.jackpot.owned` | map<tier,int> | §5.2 | each 0–100 000 | Fixed × bet at owned machines. |
| `slots.jackpot.announceMinTier` | enum(MINI, MINOR, MAJOR, GRAND) | MAJOR | — | Server-wide chat from this tier up. |
| `slots.buyFeature.enabled` | bool | true | — | |
| `slots.nether.buy.price` / `slots.end.buy.price` | double | 18.4 / 109 | 1–10 000, multiples of 0.2 | × bet. |
| `slots.buyFeature.tierMaxMultiple` | int | 25 | 1–1 000 | Price ≤ tier max × this. |
| `slots.autoplay.enabled` | bool | true | — | |
| `slots.autoplay.counts` | list<int> | [10,25,50,100] | 1–1 000 each | |
| `slots.autoplay.lossLimits` | list<int> | [10,25,50,100] | 1–10 000 each | × bet; one is mandatory. |
| `slots.turboAllowed` | bool | true | — | |
| `slots.anticipation` | bool | true | — | Off: reels always stop on the base schedule. |
| `slots.bigWinTiers` | list<int> | [5,15,40,100] | 4 increasing entries, 1–10 000 | Nice/Big/Mega/Epic thresholds (× bet). |
| `slots.inWorld.enabled` | bool | true | — | Java BER entity. |
| `slots.inWorld.radius` | int | 24 | 0–64 | Spectator range. |
| `slots.validateRtp` | bool | true | — | §7.5. |
| `pvp.slots.hazardWeights` | list<int> | [94,2,2,2] | 4 entries, each 0–1 000 | none / KABOOM / SWAP / TIME WARP. |
| `pvp.race.target` | enum(feature, jackpot, five_top) | feature | — | Jackpot Race (NICE). |

**Removed**: `slots.copper.maxLineBet`, `slots.gold.maxLineBet`, `slots.netherite.minLineBet`,
`slots.netherite.maxLineBet`, `slots.netherite.minVipTier`, `slots.<tier>.weights`,
`slots.<tier>.pays`, `slots.<tier>.berryPartial`, `slots.jackpot.contribution.*`,
`slots.jackpot.seed.*`, `slots.ownedStarPays`, `slots.spinTicks`, `pvp.slots.starPoints`.

Per-player presentation preferences (turbo, reduce motion, flashes, volume) are the `anim.*`
settings proposed in research §8/§9 and are stored per player, not in this config.

---
## 13. Strings (EN + RU)

Same format and rules as `STRINGS.md` (generator-parsed rows; `%N$s` placeholders; `%%` literal;
never a player name as the subject of a Russian past-tense verb). These rows **replace** the
`## slots` section of `STRINGS.md`, the listed block/tooltip/VIP rows, the Slot Showdown rows noted
below and the slot config labels. Argument notes: `machine` = nested
`gui.burmaldaholic.slots.machine.*`; `symbol` = nested `gui.burmaldaholic.slots.symbol.*`;
`tier` = nested `gui.burmaldaholic.slots.jackpot.tier.*`; `chips` as in STRINGS.md; `num` a
formatted number; `mult` a formatted multiplier without "×".

### 13.1 Blocks, tooltips, VIP perk (changed values)

| Key | EN | RU |
|-----|----|----|
| `block.burmaldaholic.slot_machine_copper` | Overworld Riches | Богатства Верхнего мира |
| `block.burmaldaholic.slot_machine_gold` | Nether Inferno | Пекло Незера |
| `block.burmaldaholic.slot_machine_netherite` | End Void | Пустота Края |
| `tooltip.burmaldaholic.slot_machine_copper` | 243 ways · free spins · Treasure Hunt | 243 способа · фриспины · охота за сокровищами |
| `tooltip.burmaldaholic.slot_machine_gold` | 243 ways · tumbling reels · Piglin's Hoard | 243 способа · обвалы · клад пиглинов |
| `tooltip.burmaldaholic.slot_machine_netherite` | 243 ways · sticky wilds · Dragon Wheel · Gold VIP | 243 способа · липкие вайлды · колесо дракона · ВИП «Золото» |
| `gui.burmaldaholic.vip.perk.netherite_slots` | End Void slot machine | Автомат «Пустота Края» |
| `gui.burmaldaholic.contracts.task.slots_feature` | Trigger slot features: %1$s | Запустить бонусы на автоматах: %1$s |

### 13.2 Machines and symbols

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.slots.machine.overworld` | Overworld Riches | Богатства Верхнего мира |
| `gui.burmaldaholic.slots.machine.nether` | Nether Inferno | Пекло Незера |
| `gui.burmaldaholic.slots.machine.end` | End Void | Пустота Края |
| `gui.burmaldaholic.slots.symbol.totem` | Totem (Wild) | Тотем (вайлд) |
| `gui.burmaldaholic.slots.symbol.compass` | Compass | Компас |
| `gui.burmaldaholic.slots.symbol.chest` | Treasure Chest | Сундук с сокровищами |
| `gui.burmaldaholic.slots.symbol.diamond` | Diamond | Алмаз |
| `gui.burmaldaholic.slots.symbol.emerald` | Emerald | Изумруд |
| `gui.burmaldaholic.slots.symbol.gold_ingot` | Gold Ingot | Золотой слиток |
| `gui.burmaldaholic.slots.symbol.iron_ingot` | Iron Ingot | Железный слиток |
| `gui.burmaldaholic.slots.symbol.apple` | Apple | Яблоко |
| `gui.burmaldaholic.slots.symbol.carrot` | Carrot | Морковь |
| `gui.burmaldaholic.slots.symbol.wheat` | Wheat | Пшеница |
| `gui.burmaldaholic.slots.symbol.sweet_berries` | Sweet Berries | Сладкие ягоды |
| `gui.burmaldaholic.slots.symbol.lava_bucket` | Lava Bucket (Wild) | Ведро лавы (вайлд) |
| `gui.burmaldaholic.slots.symbol.ghast_tear` | Ghast Tear | Слеза гаста |
| `gui.burmaldaholic.slots.symbol.piglin_coin` | Piglin Coin | Монета пиглинов |
| `gui.burmaldaholic.slots.symbol.wither_skull` | Wither Skeleton Skull | Череп скелета-иссушителя |
| `gui.burmaldaholic.slots.symbol.blaze_rod` | Blaze Rod | Огненный стержень |
| `gui.burmaldaholic.slots.symbol.magma_cream` | Magma Cream | Сгусток магмы |
| `gui.burmaldaholic.slots.symbol.quartz` | Nether Quartz | Кварц Незера |
| `gui.burmaldaholic.slots.symbol.nether_wart` | Nether Wart | Незерский нарост |
| `gui.burmaldaholic.slots.symbol.crimson_fungus` | Crimson Fungus | Багровый гриб |
| `gui.burmaldaholic.slots.symbol.warped_fungus` | Warped Fungus | Искажённый гриб |
| `gui.burmaldaholic.slots.symbol.glowstone` | Glowstone Dust | Светокаменная пыль |
| `gui.burmaldaholic.slots.symbol.dragon_egg` | Dragon Egg (Wild) | Яйцо дракона (вайлд) |
| `gui.burmaldaholic.slots.symbol.ender_eye` | Eye of Ender | Око Края |
| `gui.burmaldaholic.slots.symbol.end_crystal` | End Crystal | Кристалл Края |
| `gui.burmaldaholic.slots.symbol.dragon_head` | Dragon Head | Голова дракона |
| `gui.burmaldaholic.slots.symbol.elytra` | Elytra | Элитры |
| `gui.burmaldaholic.slots.symbol.shulker_shell` | Shulker Shell | Панцирь шалкера |
| `gui.burmaldaholic.slots.symbol.chorus_fruit` | Chorus Fruit | Плод коруса |
| `gui.burmaldaholic.slots.symbol.ender_pearl` | Ender Pearl | Жемчуг Края |
| `gui.burmaldaholic.slots.symbol.purpur` | Purpur Block | Пурпурный блок |
| `gui.burmaldaholic.slots.symbol.end_rod` | End Rod | Стержень Края |
| `gui.burmaldaholic.slots.symbol.end_stone` | End Stone | Эндерняк |
| `gui.burmaldaholic.slots.symbol.creeper` | Creeper | Крипер |

### 13.3 Machine screen / form

Args: `spin` %1$s chips · `bet` %1$s chips · `win`/`returned`/`last_win`/`spin_total` %1$s chips ·
`symbol_win` %1$s symbol, %2$s num (count), %3$s num (ways), %4$s chips · `scatter_win` %1$s num,
%2$s chips · `auto_left` %1$s num · `auto_summary` %1$s num (spins), %2$s chips, %3$s chips.

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.slots.spin` | Spin (%1$s) | Крутить (%1$s) |
| `gui.burmaldaholic.slots.stop` | Stop | Стоп |
| `gui.burmaldaholic.slots.skip` | Skip | Пропустить |
| `gui.burmaldaholic.slots.bet` | Bet: %1$s | Ставка: %1$s |
| `gui.burmaldaholic.slots.bet_down` | Lower bet | Меньше |
| `gui.burmaldaholic.slots.bet_up` | Raise bet | Больше |
| `gui.burmaldaholic.slots.ways` | 243 ways | 243 способа |
| `gui.burmaldaholic.slots.win` | Win %1$s | Выигрыш %1$s |
| `gui.burmaldaholic.slots.returned` | Returned %1$s | Возвращено %1$s |
| `gui.burmaldaholic.slots.last_win` | Last win: %1$s | Прошлый выигрыш: %1$s |
| `gui.burmaldaholic.slots.no_win` | No win this time | В этот раз мимо |
| `gui.burmaldaholic.slots.spin_total` | Spin result: %1$s | Итог вращения: %1$s |
| `gui.burmaldaholic.slots.symbol_win` | %1$s ×%2$s · %3$s ways · %4$s | %1$s ×%2$s · способов: %3$s · %4$s |
| `gui.burmaldaholic.slots.scatter_win` | Scatters: %1$s · %2$s | Скаттеры: %1$s · %2$s |
| `gui.burmaldaholic.slots.playing` | Now playing: %1$s | Сейчас играет: %1$s |
| `gui.burmaldaholic.slots.turbo` | Turbo | Турбо |
| `gui.burmaldaholic.slots.tier.nice` | NICE WIN! | НЕПЛОХО! |
| `gui.burmaldaholic.slots.tier.big` | BIG WIN! | КРУПНЫЙ ВЫИГРЫШ! |
| `gui.burmaldaholic.slots.tier.mega` | MEGA WIN! | МЕГАВЫИГРЫШ! |
| `gui.burmaldaholic.slots.tier.epic` | EPIC WIN! | ЭПИЧЕСКИЙ ВЫИГРЫШ! |
| `gui.burmaldaholic.slots.max_win` | MAX WIN! | МАКСИМАЛЬНЫЙ ВЫИГРЫШ! |
| `gui.burmaldaholic.slots.out_of_order` | Out of order | Не работает |
| `gui.burmaldaholic.slots.auto` | Auto… | Авто… |
| `gui.burmaldaholic.slots.stop_auto` | Stop | Стоп |
| `gui.burmaldaholic.slots.auto.title` | Autoplay | Автоигра |
| `gui.burmaldaholic.slots.auto.count` | Spins | Вращений |
| `gui.burmaldaholic.slots.auto.loss_limit` | Stop if I lose (× bet) | Стоп при проигрыше (× ставки) |
| `gui.burmaldaholic.slots.auto.stop_feature` | Stop on a feature | Стоп на бонусе |
| `gui.burmaldaholic.slots.auto.stop_win` | Stop on a win of at least | Стоп при выигрыше от |
| `gui.burmaldaholic.slots.auto.off` | Off | Выкл. |
| `gui.burmaldaholic.slots.auto.start` | Start autoplay | Запустить |
| `gui.burmaldaholic.slots.auto_left` | Auto: %1$s left | Авто: осталось %1$s |
| `gui.burmaldaholic.slots.auto_summary` | Spins %1$s: bet %2$s, won %3$s | Вращений %1$s: поставлено %2$s, выиграно %3$s |
| `gui.burmaldaholic.slots.auto_stopped_big_win` | Autoplay stopped: big win! | Автоигра остановлена: крупный выигрыш! |
| `gui.burmaldaholic.slots.auto_stopped_funds` | Autoplay stopped: not enough chips | Автоигра остановлена: не хватает фишек |
| `gui.burmaldaholic.slots.auto_stopped_feature` | Autoplay stopped: bonus! | Автоигра остановлена: бонус! |
| `gui.burmaldaholic.slots.auto_stopped_loss` | Autoplay stopped: loss limit reached | Автоигра остановлена: достигнут лимит проигрыша |
| `gui.burmaldaholic.slots.auto_stopped_jackpot` | Autoplay stopped: jackpot! | Автоигра остановлена: джекпот! |
| `gui.burmaldaholic.slots.error.loss_limit_required` | Choose a loss limit first | Сначала выберите лимит проигрыша |
| `gui.burmaldaholic.slots.error.bet_unavailable` | That bet is not available on this machine | Такой ставки на этом автомате нет |
| `gui.burmaldaholic.slots.error.buy_disabled` | Buying bonuses is off here | Покупка бонусов здесь отключена |
| `gui.burmaldaholic.slots.error.buy_limit` | This purchase is above your VIP limit | Покупка превышает ваш ВИП-лимит |

### 13.4 Features, jackpots, buy feature

Args: `fs.awarded` %1$s num · `fs.retrigger` %1$s num · `fs.left` %1$s num, %2$s num ·
`fs.multiplier`/`tumble.mult` %1$s mult · `fs.total`/`fs.end`/`bonus.total` %1$s chips ·
`tumble.count` %1$s num · `sticky` %1$s num · `pick.prize` %1$s chips · `pick.opened` %1$s num ·
`hold.respins` %1$s num · `hold.coins` %1$s num · `jackpot.meter` %1$s tier, %2$s chips ·
`jackpot.won` %1$s tier · `buy.button` %1$s chips · `buy.confirm_title` %1$s feature name ·
`buy.confirm_body` %1$s chips, %2$s feature name, %3$s num, %4$s num (percent).

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.slots.fs.title` | FREE SPINS | БЕСПЛАТНЫЕ ВРАЩЕНИЯ |
| `gui.burmaldaholic.slots.fs.name.overworld` | Night Watch | Ночной дозор |
| `gui.burmaldaholic.slots.fs.name.nether` | Inferno Spins | Адские вращения |
| `gui.burmaldaholic.slots.fs.name.end` | Void Walker | Странник пустоты |
| `gui.burmaldaholic.slots.fs.awarded` | Free spins: %1$s! | Бесплатных вращений: %1$s! |
| `gui.burmaldaholic.slots.fs.retrigger` | More spins: +%1$s! | Ещё вращений: +%1$s! |
| `gui.burmaldaholic.slots.fs.left` | Spin %1$s of %2$s | Вращение %1$s из %2$s |
| `gui.burmaldaholic.slots.fs.multiplier` | Multiplier ×%1$s | Множитель ×%1$s |
| `gui.burmaldaholic.slots.fs.total` | Bonus win: %1$s | Выигрыш в бонусе: %1$s |
| `gui.burmaldaholic.slots.fs.end` | FREE SPINS WIN %1$s | ИТОГ ВРАЩЕНИЙ: %1$s |
| `gui.burmaldaholic.slots.tumble.mult` | Tumble ×%1$s | Обвал ×%1$s |
| `gui.burmaldaholic.slots.tumble.count` | Tumbles: %1$s | Обвалов: %1$s |
| `gui.burmaldaholic.slots.sticky` | Sticky reels: %1$s/3 | Липкие барабаны: %1$s/3 |
| `gui.burmaldaholic.slots.bonus.pick` | Treasure Hunt | Охота за сокровищами |
| `gui.burmaldaholic.slots.bonus.hold` | Piglin's Hoard | Клад пиглинов |
| `gui.burmaldaholic.slots.bonus.wheel` | Dragon Wheel | Колесо дракона |
| `gui.burmaldaholic.slots.bonus.total` | Bonus total: %1$s | Итог бонуса: %1$s |
| `gui.burmaldaholic.slots.pick.hint` | Open chests until a Creeper jumps out | Открывайте сундуки, пока не выскочит крипер |
| `gui.burmaldaholic.slots.pick.open` | Open a chest | Открыть сундук |
| `gui.burmaldaholic.slots.pick.open_all` | Open all | Открыть все |
| `gui.burmaldaholic.slots.pick.prize` | Chest: %1$s | В сундуке: %1$s |
| `gui.burmaldaholic.slots.pick.creeper` | A Creeper! The hunt is over. | Крипер! Охота окончена. |
| `gui.burmaldaholic.slots.pick.opened` | Chests opened: %1$s | Открыто сундуков: %1$s |
| `gui.burmaldaholic.slots.hold.respins` | Respins: %1$s | Повторов: %1$s |
| `gui.burmaldaholic.slots.hold.coins` | Coins: %1$s/15 | Монеты: %1$s/15 |
| `gui.burmaldaholic.slots.hold.reset` | New coin! Back to 3 respins | Новая монета! Снова 3 повтора |
| `gui.burmaldaholic.slots.hold.full` | ALL 15 FILLED! | ВСЕ 15 ЗАПОЛНЕНЫ! |
| `gui.burmaldaholic.slots.wheel.spin` | Spin the wheel | Крутить колесо |
| `gui.burmaldaholic.slots.wheel.up` | UP! On to the next ring | ВВЕРХ! На следующее кольцо |
| `gui.burmaldaholic.slots.wheel.ring.outer` | End Stone ring | Кольцо эндерняка |
| `gui.burmaldaholic.slots.wheel.ring.middle` | Purpur ring | Пурпурное кольцо |
| `gui.burmaldaholic.slots.wheel.ring.core` | Dragon Core | Сердце дракона |
| `gui.burmaldaholic.slots.jackpot.tier.mini` | MINI | МИНИ |
| `gui.burmaldaholic.slots.jackpot.tier.minor` | MINOR | МИНОР |
| `gui.burmaldaholic.slots.jackpot.tier.major` | MAJOR | МАЖОР |
| `gui.burmaldaholic.slots.jackpot.tier.grand` | GRAND | ГРАНД |
| `gui.burmaldaholic.slots.jackpot.meter` | %1$s %2$s | %1$s %2$s |
| `gui.burmaldaholic.slots.jackpot.won` | %1$s JACKPOT! | ДЖЕКПОТ %1$s! |
| `gui.burmaldaholic.slots.buy.button` | Buy bonus (%1$s) | Купить бонус (%1$s) |
| `gui.burmaldaholic.slots.buy.confirm_title` | Buy %1$s? | Купить «%1$s»? |
| `gui.burmaldaholic.slots.buy.confirm_body` | Pay %1$s to start %2$s with %3$s free spins. Return to player: %4$s%%. | Заплатить %1$s и начать «%2$s»: бесплатных вращений — %3$s. Возврат игроку: %4$s %%. |
| `gui.burmaldaholic.slots.buy.confirm` | Buy | Купить |

### 13.5 Paytable and help

Args: `paytable.row` %1$s symbol, %2$s–%4$s chips (pays at the current bet) · `paytable.wild`
%1$s symbol, %2$s symbol, %3$s symbol · `paytable.scatter` %1$s symbol, %2$s–%4$s chips ·
`paytable.rtp` %1$s num · `paytable.max_win` %1$s num · `paytable.jackpot_share` %1$s chips ·
`help.fs` %1$s symbol, %2$s–%4$s num, %5$s num · `help.fs_mult` %1$s mult · `help.tumble` %1$s
text ("×1 ×2 ×3 ×5"), %2$s text · `help.sticky`/`help.pick`/`help.hold`/`help.wheel` %1$s symbol ·
`stats.line` %1$s num, %2$s chips, %3$s chips, %4$s mult.

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.slots.paytable.title` | Paytable | Таблица выплат |
| `gui.burmaldaholic.slots.paytable.per_way` | Wins pay for every way: matching symbols on adjacent reels from the left, in any row | Выплата за каждый способ: одинаковые символы на соседних барабанах слева направо, в любом ряду |
| `gui.burmaldaholic.slots.paytable.row` | %1$s: 3 → %2$s · 4 → %3$s · 5 → %4$s | %1$s: 3 → %2$s · 4 → %3$s · 5 → %4$s |
| `gui.burmaldaholic.slots.paytable.wild` | %1$s appears on reels 2–4 and replaces every symbol except %2$s and %3$s | %1$s появляется на барабанах 2–4 и заменяет все символы, кроме «%2$s» и «%3$s» |
| `gui.burmaldaholic.slots.paytable.scatter` | %1$s anywhere: 3 → %2$s · 4 → %3$s · 5 → %4$s | %1$s в любом месте: 3 → %2$s · 4 → %3$s · 5 → %4$s |
| `gui.burmaldaholic.slots.paytable.rtp` | Return to player: %1$s%% | Возврат игроку: %1$s %% |
| `gui.burmaldaholic.slots.paytable.max_win` | Max win per spin: ×%1$s the bet | Максимальный выигрыш за вращение: ×%1$s от ставки |
| `gui.burmaldaholic.slots.paytable.jackpot_share` | Full jackpot at a bet of %1$s or more; smaller bets win a share | Весь джекпот — при ставке от %1$s; меньшая ставка даёт долю |
| `gui.burmaldaholic.slots.paytable.my_stats` | My stats | Моя статистика |
| `gui.burmaldaholic.slots.stats.line` | Spins %1$s · wagered %2$s · returned %3$s · best ×%4$s | Вращений %1$s · поставлено %2$s · возвращено %3$s · лучший ×%4$s |
| `gui.burmaldaholic.slots.help.fs` | 3, 4 or 5 %1$s anywhere: %2$s, %3$s or %4$s free spins. 3 more during free spins: +%5$s. | 3, 4 или 5 символов «%1$s» в любом месте: %2$s, %3$s или %4$s бесплатных вращений. Ещё 3 во время вращений: +%5$s. |
| `gui.burmaldaholic.slots.help.fs_mult` | During free spins all wins are multiplied by %1$s | Во время бесплатных вращений все выигрыши умножаются на %1$s |
| `gui.burmaldaholic.slots.help.tumble` | Winning symbols burn away and new ones fall in. Each tumble raises the multiplier: %1$s. In free spins: %2$s. | Выигрышные символы сгорают, сверху падают новые. Каждый обвал повышает множитель: %1$s. В бесплатных вращениях: %2$s. |
| `gui.burmaldaholic.slots.help.sticky` | In Void Walker every %1$s fills its reel and stays until the feature ends | В «Страннике пустоты» каждый символ «%1$s» заполняет свой барабан и остаётся до конца бонуса |
| `gui.burmaldaholic.slots.help.pick` | 3 %1$s on reels 1, 3 and 5 start the Treasure Hunt: open chests for coins and jackpot gems until a Creeper appears | 3 символа «%1$s» на барабанах 1, 3 и 5 запускают охоту за сокровищами: открывайте сундуки с монетами и джекпотами, пока не появится крипер |
| `gui.burmaldaholic.slots.help.pick_fair` | Prizes are drawn when the hunt starts. Which chest you open does not change your chances. | Призы разыгрываются в начале охоты. Выбор сундука не влияет на шансы. |
| `gui.burmaldaholic.slots.help.hold` | 6 or more %1$s start Piglin's Hoard: coins lock, you get 3 respins and every new coin resets them. Fill all 15 cells for the Grand jackpot. | 6 и больше символов «%1$s» запускают клад пиглинов: монеты фиксируются, даются 3 повтора, каждая новая монета их обновляет. Заполните все 15 ячеек — джекпот «Гранд». |
| `gui.burmaldaholic.slots.help.wheel` | %1$s on reels 2, 3 and 4 spin the Dragon Wheel. UP takes you to a richer ring; the Grand waits in the Dragon Core. | Символы «%1$s» на барабанах 2, 3 и 4 запускают колесо дракона. «Вверх» ведёт на кольцо побогаче; «Гранд» ждёт в сердце дракона. |
| `gui.burmaldaholic.slots.help.anticipation` | Reels slow down only when the symbols you already see could complete a feature | Барабаны замедляются, только если уже видимые символы могут запустить бонус |

### 13.6 Messages (chat, titles, chaos flavour)

Args: `jackpot_self` %1$s tier, %2$s chips · `jackpot_broadcast` %1$s name, %2$s tier, %3$s chips,
%4$s machine · `jackpot_pool` %1$s machine, %2$s chips · `tumble_chain` %1$s num ·
`big_win_broadcast` %1$s name, %2$s mult, %3$s machine.

| Key | EN | RU |
|-----|----|----|
| `msg.burmaldaholic.slots.jackpot_self` | %1$s JACKPOT! You won %2$s! | ДЖЕКПОТ %1$s! Ваш выигрыш: %2$s! |
| `msg.burmaldaholic.slots.jackpot_broadcast` | JACKPOT! %1$s hits the %2$s: %3$s on %4$s! | ДЖЕКПОТ! %1$s — %2$s: %3$s на автомате «%4$s»! |
| `msg.burmaldaholic.slots.jackpot_pool` | %1$s: Grand jackpot %2$s | %1$s: джекпот «Гранд» %2$s |
| `msg.burmaldaholic.slots.big_win_broadcast` | Epic win: %1$s, ×%2$s on %3$s! | Эпический выигрыш: %1$s, ×%2$s на автомате «%3$s»! |
| `msg.burmaldaholic.slots.golden_scatters` | Five scatters! Golden Hour strikes! | Пять скаттеров! Бьёт «Золотой час»! |
| `msg.burmaldaholic.slots.golden_scatters_cooldown` | Five scatters! Golden Hour is recharging, but the spins are yours. | Пять скаттеров! «Золотой час» перезаряжается, но вращения ваши. |
| `msg.burmaldaholic.slots.creeper_friends` | That creeper brought friends! | Этот крипер привёл друзей! |
| `msg.burmaldaholic.slots.wither_skulls` | Five Wither Skulls. The Nether wants them back. | Пять черепов иссушителя. Незер хочет их обратно. |
| `msg.burmaldaholic.slots.tumble_chain` | %1$s tumbles in a row! The fire likes you. | Обвалов подряд: %1$s! Огонь к вам благосклонен. |
| `msg.burmaldaholic.slots.dragon_fling` | Five Dragon Heads! The dragon flings you away… gently. | Пять голов дракона! Дракон отшвыривает вас… бережно. |
| `msg.burmaldaholic.slots.void_walker` | All three reels are Dragon Eggs! | Все три барабана — яйца дракона! |
| `msg.burmaldaholic.slots.rtp_ok` | All slot machines pay back less than 99%% | Все автоматы возвращают меньше 99 %% |
| `msg.burmaldaholic.slots.jackpots_migrated` | Slot jackpots moved to the new machines: %1$s | Джекпоты перенесены на новые автоматы: %1$s |

### 13.7 Slot Showdown (replaces PVP.md rows `pvp.slots.rules.1`–`.5`, `msg…pvp.slots.swap`, `msg…pvp.slots.time_warp`, `msg…pvp.slots.star`)

Args: `rules.4` %1$s num (percent) · `rules.5` %1$s–%4$s num · `swap` %1$s name, %2$s name, %3$s num,
%4$s num · `time_warp` %1$s name · `jackpot` %1$s name, %2$s tier, %3$s num · `feature` %1$s feature
name, %2$s num.

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.pvp.slots.rules.1` | Everyone spins the same machine. Each spin's win becomes points (10 per 1× bet); the most points takes the pot. | Все крутят один и тот же автомат. Выигрыш вращения превращается в очки (10 за 1× ставки); у кого больше очков, тот забирает банк. |
| `gui.burmaldaholic.pvp.slots.rules.2` | Each round one symbol is HOT: its wins score double. | В каждом раунде один символ — горячий: его выигрыши дают двойные очки. |
| `gui.burmaldaholic.pvp.slots.rules.3` | Free spins and bonus games play out fast and count in full. | Бесплатные вращения и бонусы разыгрываются быстро и засчитываются полностью. |
| `gui.burmaldaholic.pvp.slots.rules.4` | Surprises (%1$s%% each): KABOOM halves your score, SWAP trades it with the leader, TIME WARP doubles your next spin. | Сюрпризы (по %1$s %%): БАБАХ делит ваши очки пополам, РОКИРОВКА меняет их с лидером, ПЕТЛЯ ВРЕМЕНИ удваивает следующее вращение. |
| `gui.burmaldaholic.pvp.slots.rules.5` | Jackpots give fixed points: Mini %1$s · Minor %2$s · Major %3$s · Grand %4$s. | Джекпоты дают фиксированные очки: мини %1$s · минор %2$s · мажор %3$s · гранд %4$s. |
| `gui.burmaldaholic.pvp.slots.time_warp_title` | TIME WARP! | ПЕТЛЯ ВРЕМЕНИ! |
| `msg.burmaldaholic.pvp.slots.swap` | An Ender Pearl drops! %1$s swaps scores with the leader %2$s: %3$s ⇄ %4$s | Выпал жемчуг Края! %1$s меняется очками с лидером (%2$s): %3$s ⇄ %4$s |
| `msg.burmaldaholic.pvp.slots.time_warp` | Time warp! %1$s's next spin counts double | Петля времени! Следующее вращение игрока %1$s — с двойными очками |
| `msg.burmaldaholic.pvp.slots.jackpot` | %1$s: %2$s jackpot, +%3$s points | %1$s: джекпот %2$s, очки: +%3$s |
| `gui.burmaldaholic.pvp.slots.feature` | %1$s: +%2$s | %1$s: +%2$s |

### 13.8 Config labels (replace the slot rows of STRINGS.md §config)

Family templates (`%1$s` = machine name):

| Key | EN | RU |
|-----|----|----|
| `config.burmaldaholic.slots.machine.enabled` | %1$s: enabled | %1$s: вкл. |
| `config.burmaldaholic.slots.bets` | %1$s: bet levels | %1$s: уровни ставок |
| `config.burmaldaholic.slots.defaultBet` | %1$s: default bet | %1$s: ставка по умолчанию |
| `config.burmaldaholic.slots.minVipTier` | %1$s: required VIP tier | %1$s: нужный ВИП-статус |
| `config.burmaldaholic.slots.maxWinMultiple` | %1$s: max win (× bet) | %1$s: макс. выигрыш (× ставки) |
| `config.burmaldaholic.slots.strips` | %1$s: reel strips | %1$s: ленты барабанов |
| `config.burmaldaholic.slots.pays` | %1$s: payouts | %1$s: выплаты |
| `config.burmaldaholic.slots.scatterPays` | %1$s: scatter payouts | %1$s: выплаты за скаттеры |
| `config.burmaldaholic.slots.freeSpins` | %1$s: free spins for 3/4/5 | %1$s: фриспины за 3/4/5 |
| `config.burmaldaholic.slots.freeSpins.retrigger` | %1$s: extra free spins | %1$s: доп. фриспины |
| `config.burmaldaholic.slots.freeSpins.cap` | %1$s: max free spins | %1$s: макс. фриспинов |
| `config.burmaldaholic.slots.jackpot.refBet` | %1$s: full-jackpot bet | %1$s: ставка для полного джекпота |
| `config.burmaldaholic.slots.jackpot.seed` | %1$s: jackpot seeds (× bet) | %1$s: стартовые джекпоты (× ставки) |
| `config.burmaldaholic.slots.jackpot.contribution` | %1$s: jackpot contributions | %1$s: отчисления в джекпоты |
| `config.burmaldaholic.slots.jackpot.owned` | %1$s: fixed jackpots at player casinos | %1$s: фиксированные джекпоты в казино игроков |
| `config.burmaldaholic.slots.buy.price` | %1$s: bonus price (× bet) | %1$s: цена бонуса (× ставки) |

Single keys:

| Key | EN | RU |
|-----|----|----|
| `config.burmaldaholic.slots.enabled` | Slot machines | Игровые автоматы |
| `config.burmaldaholic.slots.overworld.freeSpins.multiplier` | Overworld Riches: free-spin multiplier | Богатства Верхнего мира: множитель фриспинов |
| `config.burmaldaholic.slots.nether.tumble.ladder` | Nether Inferno: tumble multipliers | Пекло Незера: множители обвалов |
| `config.burmaldaholic.slots.nether.tumble.ladderFree` | Nether Inferno: tumble multipliers in free spins | Пекло Незера: множители обвалов во фриспинах |
| `config.burmaldaholic.slots.overworld.pick.board` | Treasure Hunt: chests | Охота за сокровищами: число сундуков |
| `config.burmaldaholic.slots.overworld.pick.weights` | Treasure Hunt: chest contents | Охота за сокровищами: содержимое сундуков |
| `config.burmaldaholic.slots.nether.hold.trigger` | Piglin's Hoard: coins to start | Клад пиглинов: монет для запуска |
| `config.burmaldaholic.slots.nether.hold.respins` | Piglin's Hoard: respins | Клад пиглинов: число повторов |
| `config.burmaldaholic.slots.nether.hold.coinChance` | Piglin's Hoard: coin chance per cell | Клад пиглинов: шанс монеты в ячейке |
| `config.burmaldaholic.slots.nether.hold.coinWeights` | Piglin's Hoard: coin values | Клад пиглинов: номиналы монет |
| `config.burmaldaholic.slots.end.wheel.outer` | Dragon Wheel: outer ring | Колесо дракона: внешнее кольцо |
| `config.burmaldaholic.slots.end.wheel.middle` | Dragon Wheel: middle ring | Колесо дракона: среднее кольцо |
| `config.burmaldaholic.slots.end.wheel.core` | Dragon Wheel: core | Колесо дракона: сердце |
| `config.burmaldaholic.slots.jackpot.announceMinTier` | Announce jackpots from | Объявлять джекпоты от |
| `config.burmaldaholic.slots.buyFeature.enabled` | Allow buying bonuses | Разрешить покупку бонусов |
| `config.burmaldaholic.slots.buyFeature.tierMaxMultiple` | Bonus price limit (× VIP max bet) | Лимит цены бонуса (× макс. ставки ВИП) |
| `config.burmaldaholic.slots.autoplay.enabled` | Allow autoplay | Разрешить автоигру |
| `config.burmaldaholic.slots.autoplay.counts` | Autoplay spin options | Варианты числа автовращений |
| `config.burmaldaholic.slots.autoplay.lossLimits` | Autoplay loss limits (× bet) | Лимиты проигрыша автоигры (× ставки) |
| `config.burmaldaholic.slots.turboAllowed` | Allow turbo spins | Разрешить турбо |
| `config.burmaldaholic.slots.anticipation` | Slow reels when a feature is close | Замедлять барабаны, когда близок бонус |
| `config.burmaldaholic.slots.bigWinTiers` | Win tiers (× bet) | Пороги выигрышей (× ставки) |
| `config.burmaldaholic.slots.inWorld.enabled` | Show reels on cabinets | Показывать барабаны на автоматах |
| `config.burmaldaholic.slots.inWorld.radius` | Cabinet reels view distance | Дальность показа барабанов |
| `config.burmaldaholic.slots.validateRtp` | Warn about RTP above 99%% | Предупреждать об RTP выше 99 %% |
| `config.burmaldaholic.pvp.slots.hazardWeights` | Slot Showdown: surprise weights | Битва автоматов: веса сюрпризов |
| `config.burmaldaholic.pvp.race.target` | Jackpot Race: target | Гонка за джекпотом: цель |

### 13.9 Sound subtitles

| Key | EN | RU |
|-----|----|----|
| `subtitles.burmaldaholic.slots.spin_loop` | Reels whirr | Жужжат барабаны |
| `subtitles.burmaldaholic.slots.reel_stop` | Reel stops | Останавливается барабан |
| `subtitles.burmaldaholic.slots.scatter_land` | Scatter lands | Выпадает скаттер |
| `subtitles.burmaldaholic.slots.bonus_land` | Bonus symbol lands | Выпадает бонусный символ |
| `subtitles.burmaldaholic.slots.anticipation` | Tension rises | Напряжение растёт |
| `subtitles.burmaldaholic.slots.returned` | Coins trickle back | Возвращаются монеты |
| `subtitles.burmaldaholic.slots.win_small` | Small win chimes | Звенит выигрыш |
| `subtitles.burmaldaholic.slots.win_nice` | Nice win chimes | Звенит хороший выигрыш |
| `subtitles.burmaldaholic.slots.big_win` | Big win fanfare | Фанфары крупного выигрыша |
| `subtitles.burmaldaholic.slots.mega_win` | Mega win fanfare | Фанфары мегавыигрыша |
| `subtitles.burmaldaholic.slots.epic_win` | Epic win fanfare | Фанфары эпического выигрыша |
| `subtitles.burmaldaholic.slots.max_win` | Max win fanfare | Фанфары максимального выигрыша |
| `subtitles.burmaldaholic.slots.rollup_tick` | Counter ticks | Щёлкает счётчик |
| `subtitles.burmaldaholic.slots.rollup_end` | Counter stops | Счётчик останавливается |
| `subtitles.burmaldaholic.slots.fs_intro` | Free spins begin | Начинаются бесплатные вращения |
| `subtitles.burmaldaholic.slots.fs_outro` | Free spins end | Бесплатные вращения окончены |
| `subtitles.burmaldaholic.slots.fs_music` | Bonus music plays | Играет бонусная музыка |
| `subtitles.burmaldaholic.slots.wild_expand` | Wild expands | Вайлд растёт |
| `subtitles.burmaldaholic.slots.wild_stick` | Wild locks in place | Вайлд закрепляется |
| `subtitles.burmaldaholic.slots.tumble` | Symbols burn away | Сгорают символы |
| `subtitles.burmaldaholic.slots.mult_up` | Multiplier rises | Растёт множитель |
| `subtitles.burmaldaholic.slots.chest_open` | Chest opens | Открывается сундук |
| `subtitles.burmaldaholic.slots.creeper_hiss` | Creeper hisses | Шипит крипер |
| `subtitles.burmaldaholic.slots.coin_land` | Coin clinks | Звякает монета |
| `subtitles.burmaldaholic.slots.respin_reset` | Respins reset | Повторы обновились |
| `subtitles.burmaldaholic.slots.wheel_tick` | Wheel clicks | Щёлкает колесо |
| `subtitles.burmaldaholic.slots.wheel_up` | Wheel rises | Колесо поднимается |

(`fs_music.<machine>` sound events share the one `slots.fs_music` subtitle.)

### 13.10 Removed keys

`gui.burmaldaholic.slots.line_bet`, `.lines`, `.jackpot`, `.line_win`, `.paytable.three`,
`.paytable.berry_1`, `.paytable.berry_2`, `.paytable.star`, `.paytable.star_owned`,
`.paytable.chaos`, `.paytable.max_bet_jackpot`, `.symbol.berry`, `.symbol.seven`, `.symbol.wild`,
`.symbol.tnt`, `.symbol.pearl`, `.symbol.clock`, `.symbol.star`;
`msg.burmaldaholic.slots.three_creepers`, `.three_tnt`, `.three_pearls`, `.three_clocks`,
`.three_clocks_cooldown`, `.seven_title`; `gui.burmaldaholic.pvp.slots.rules` keys are replaced as
above; `msg.burmaldaholic.pvp.slots.star`; `config.burmaldaholic.slots.copper.maxLineBet`,
`.gold.maxLineBet`, `.netherite.minLineBet`, `.netherite.maxLineBet`, `.netherite.minVipTier`,
`.jackpot.contribution.gold`, `.jackpot.contribution.netherite`, `.jackpot.seed.gold`,
`.jackpot.seed.netherite`, `.ownedStarPays`, `.spinTicks`, `.weights`, `.berryPartial`,
`config.burmaldaholic.pvp.slots.starPoints`; `advancement.burmaldaholic.three_sevens.*`.
(The key `gui.burmaldaholic.slots.paytable.wild` is kept with the new value above;
`gui.burmaldaholic.slots.symbol.apple/.diamond/.emerald/.creeper` are kept; `.symbol.carrot` changes
from "Golden Carrot" to "Carrot".)

---

## 14. Advancements (replace `three_sevens`; `jackpot` condition changes)

| Id | Parent | Condition | Frame |
|----|--------|-----------|-------|
| `top_five` | beginners_luck | 5 of a kind of a machine's top symbol (Diamond, Wither Skeleton Skull, Dragon Head) | goal |
| `jackpot` | top_five | Win a **Major or Grand** slot jackpot | challenge |
| `mini_jackpot` | beginners_luck | Win any slot jackpot | task |
| `free_spins` | first_bet | Trigger free spins on any machine (not bought) | task |
| `treasure_hunter` | free_spins | Open 10 chests in one Treasure Hunt (8.8 % of hunts) | goal |
| `tumble_six` | free_spins | 6 or more tumbles in one Nether Inferno spin | goal |
| `hoard_full` | tumble_six | Fill all 15 cells in Piglin's Hoard | challenge |
| `void_walker` | free_spins | All three reels sticky in Void Walker | goal |
| `dragon_core` | free_spins | Reach the Dragon Core of the Dragon Wheel | goal |
| `epic_win` | beginners_luck | An Epic Win (≥ 100× the bet) on one spin | goal |
| `max_win` | epic_win | Hit a machine's max win | challenge |

| Key | EN | RU |
|-----|----|----|
| `advancement.burmaldaholic.top_five.title` | Top of the Reels | Лучшие на барабанах |
| `advancement.burmaldaholic.top_five.description` | Land five of a machine's top symbol | Соберите пять главных символов автомата |
| `advancement.burmaldaholic.jackpot.title` | Jackpot! | Джекпот! |
| `advancement.burmaldaholic.jackpot.description` | Win a Major or Grand slot jackpot | Выиграйте джекпот «Мажор» или «Гранд» |
| `advancement.burmaldaholic.mini_jackpot.title` | Small Fortune | Маленькое состояние |
| `advancement.burmaldaholic.mini_jackpot.description` | Win any slot jackpot | Выиграйте любой джекпот на автомате |
| `advancement.burmaldaholic.free_spins.title` | On the House | За счёт заведения |
| `advancement.burmaldaholic.free_spins.description` | Trigger free spins on a slot machine | Запустите бесплатные вращения |
| `advancement.burmaldaholic.treasure_hunter.title` | Treasure Hunter | Кладоискатель |
| `advancement.burmaldaholic.treasure_hunter.description` | Open 10 chests in one Treasure Hunt | Откройте 10 сундуков за одну охоту |
| `advancement.burmaldaholic.tumble_six.title` | Chain Reaction | Цепная реакция |
| `advancement.burmaldaholic.tumble_six.description` | Get 6 tumbles in one Nether Inferno spin | Соберите 6 обвалов за одно вращение в «Пекле Незера» |
| `advancement.burmaldaholic.hoard_full.title` | Piglin Royalty | Король пиглинов |
| `advancement.burmaldaholic.hoard_full.description` | Fill all 15 cells in Piglin's Hoard | Заполните все 15 ячеек клада пиглинов |
| `advancement.burmaldaholic.void_walker.title` | Void Walker | Странник пустоты |
| `advancement.burmaldaholic.void_walker.description` | Make all three reels sticky in Void Walker free spins | Сделайте все три барабана липкими в «Страннике пустоты» |
| `advancement.burmaldaholic.dragon_core.title` | Heart of the Dragon | Сердце дракона |
| `advancement.burmaldaholic.dragon_core.description` | Reach the Dragon Core of the Dragon Wheel | Доберитесь до сердца колеса дракона |
| `advancement.burmaldaholic.epic_win.title` | Epic! | Эпично! |
| `advancement.burmaldaholic.epic_win.description` | Win at least 100 times your bet on one spin | Выиграйте за одно вращение не меньше 100 ставок |
| `advancement.burmaldaholic.max_win.title` | Broke the Machine | Автомат сломался |
| `advancement.burmaldaholic.max_win.description` | Hit a slot machine's max win | Сорвите максимальный выигрыш автомата |

Holders of the retired `three_sevens` are granted `top_five` on join (§11). PvP spins never grant
slot advancements.

---

## 15. Test plan

**Pure logic (CI, must pass before any UI work)**
1. Strip data = Appendix A exactly (per-reel counts of §3 asserted; scatter spacing ≥ 3 cyclic;
   Wild only on reels 2–4; bonus only on its reels).
2. Ways evaluator unit cases: hand-built windows incl. multi-wild ways (e.g. reel counts 2,3,1 →
   6 ways), a symbol stopping at reel 3, several symbols paying at once, wilds not counting on
   reels 1/5, no wild-only ways.
3. Tumble algorithm: golden tapes (5 stops → list of grids) for 10 fixed stop vectors
   produced by the reference (shared JSON fixture `slots_tumble_fixtures.json`), incl. a 7- and an
   8-tumble chain.
4. **Full enumeration** (§7.5): exact integer equality of Σpay, hit counts, scatter histograms,
   bonus counts, tumble histogram, top-symbol 5-kinds. Java: JUnit (< 60 s); Bedrock: Node test
   with the same TypeScript logic (nightly if > 60 s).
5. End free-spin state enumerations (8 × 45⁵) exact sums; Markov chains §7.3 to 10⁻⁹.
6. Closed forms for Treasure Hunt, Hoard, Wheel; wheel wedge counts; Hoard chain.
7. §7.1 totals reproduced by `validateRtp` from config; changing one pay changes the result and the
   warning fires above 0.99 and when a buy RTP exceeds its machine RTP.
8. Monte-Carlo 10⁸ spins/machine (nightly) within §7.5 tolerances; 10⁷ Void Walker features.
9. Integer money: property test over random tapes at every ladder bet: every intermediate win is an
   integer; jackpot award = floor rule; conservation of pool money (Σ contributions + minted seeds
   = Σ awards + pools).
10. Max-win cap: forced tapes above the cap end the feature at the cap and set MAX WIN.
11. Streak: re-draw only for total < bet; never on buys; r ≤ r_cap (§7.1).
12. Anticipation plan property test (§10.3).
13. Persistence: kill the server after DRAW for each feature type → the round settles from the
    tape on restart with the same total; v1 record fixture settles with `LegacySlots`.
14. Owned casino: reservation = cap × bet (incl. buy); insolvency threshold; fixed jackpots; no
    contribution; owner cannot play.
15. Chaos mapping: each §8.4 outcome fires exactly its event, one per spin, by priority.
16. Slot Showdown: tape → points (10 × win/bet), HOT doubling, hazards weights, mean points per
    spin 9.404 830 / 9.380 914 / 9.375 664 by enumeration + chains; Lemma 1 symmetry test.
17. Autoplay stop conditions, including the mandatory loss limit.

**Presentation (manual + screenshot tests)**
18. Java 400 × 240 and compact 320 × 220 at GUI scale 2/3/4 on 1080p, in EN and RU (1.45×), all
    banners, meters with 8-digit amounts, buttons wrap not truncate.
20. Spectator view: BER/entity lands on the same tick as the player's reveal; tumbles (Java) and
    first-drop + bursts (Bedrock); 20 cabinets in view within the research §7 budgets.
21. Reduce motion, flashes cap, turbo, skip at every stage; no loss shown as a win.
22. Lang parity: every key in §13 in all four lang files; removed keys absent.

---

## Appendix A — Reel strips (normative)

Index 0 first; the window of stop `t` shows indices `t, t+1, t+2` (mod L). Codes: §2.

### A.1 Overworld Riches (5 × 40)

```
R1: AP CA GO BE BE AP AP CA IR CA AP SC DI BN GO WH GO BN BE CA BE EM IR DI EM EM AP BN BE BE WH CA IR WH DI SC WH IR GO WH
R2: CA AP CA BE WH BE WH GO WH GO IR SC GO CA EM AP EM BE BE IR IR WD WD WH WH CA EM DI DI CA IR WD WD BE AP DI AP AP GO BE
R3: AP EM BE IR WH WH BN DI CA DI DI BE GO EM WD WD IR CA BE AP GO AP CA IR CA WD WD GO WH IR WH BE GO AP EM SC AP BE CA BN
R4: AP WH BE AP CA EM BE WH GO AP GO BE WD WD AP WD WD AP CA GO CA IR GO BE EM WH IR IR DI DI CA BE WH DI SC CA WH BE EM IR
R5: BE WH WH BE BE WH EM GO CA BN BE AP IR CA IR AP SC DI EM BE AP DI AP BN GO CA CA BE AP WH GO DI BN EM IR IR SC GO WH CA
```

### A.2 Nether Inferno (5 × 32)

```
R1: NW BR BR WF CF SC NW WF GD BR CF CF MC QZ NW NW MC QZ GD QZ CF WF WF MC GD CN CN NW GD SK SK CF
R2: SK SC BR MC MC WF CF NW WF NW MC NW QZ CF GD GD BR WF WD WF QZ CF GD WD NW SK QZ CN CN CF WF GD
R3: WF NW MC MC CF WF CN QZ QZ NW CF WD CF WF WD GD BR CF SK NW GD CN CN SC GD MC GD WF SK NW QZ BR
R4: CF BR NW QZ NW WF BR QZ CF MC SK CF NW SC WF GD MC SK WD CN CN WF WD WF CF GD WF GD GD QZ MC NW
R5: SK WF CF CN CN SK GD NW WF NW BR CF MC NW BR GD QZ BR CF MC GD QZ WF QZ GD MC NW CF NW CF SC WF
```

### A.3 End Void (5 × 45)

```
R1: EL PU ES CH SS PU EP CH ES ES PU SC EP EP SS ER PU EP ER CH CH EP ER ER ES DH DH PU EL EP CH ES PU EP ER SS ER ER PU EL ER ES SS ES ES
R2: ER EP ES SS PU PU ER SS ER PU PU CH EL CH ES EP ER EL PU ES ES BN PU ES ES EP ER SS EL DH DH CH ES EP EP BN ES ER CH EP CH WD SC SS ER
R3: CH ER ES ER EP BN PU PU EP CH PU ES EP ES BN ER SS EP EL PU ES CH SS DH DH PU ER ES PU ES BN ES EP WD PU SS EL CH EL EP ER SS ER SC ER
R4: DH DH PU PU CH EL SS PU ES ES ER EP ES ER EL ES SS SS PU EL ES ER ES ER BN CH ER BN SS ER EP PU CH EP EP CH PU CH SC ER ES EP WD ES EP
R5: DH DH PU ER PU CH SC SS PU ER SS EP PU ES EP SS ER CH ER CH ER EP SS EL CH EP EL EP PU ER CH PU ES EP EP ES EL ES ES ER ES PU ES ES ER
```
