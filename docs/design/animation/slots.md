# Burmaldaholic — Animation Spec: Slots v2 (243 ways) and Slot Showdown

> **Java-only (2026-09-24).** Bedrock support was dropped: Bedrock sections, lanes and tasks were removed. An inline
> note that still names Bedrock (the former TypeScript twin) is historical context and does not apply.

Status: implementation-ready draft (2026-09-24). Owner: animation design (slots).
Audience: Java client team, Bedrock script/RP team, asset-script author, testers.

**What this file is.** `docs/design/SLOTS.md` (the slots v2 game design) fixes the *what*: machines,
features, math, the tape, and a presentation baseline in its §10 (tiers, reel timeline,
anticipation rule, bonus timelines, Java layout, Bedrock DDUI, sound ids). This file is the
detailed *how*, down to frames, curves, sprite sheets, Molang, properties, and task split. Where the
two disagree on a slots-presentation detail, **SLOTS.md wins on rules and numbers** (tiers,
thresholds, timings in its §10.2/§10.4). This file wins on visual and implementation detail. The
deviations I propose are listed in §0.3 and need the slots designer's OK before they are built.

**Shared kit.** `docs/design/animation/global.md` provides the palette (§2.1), panels and
`CasinoButton` (§2.2), easing names (§2.5), `CelebrationOverlay` / `fx.celebrate` (§2.6), sound
conventions (§2.7), the FX settings `anim.*` (§2.8), budgets (§2.9) and the asset generator S1.
`extras-pvp.md` §9 provides the PvP Final Reveal, countdown and lobby motion that Slot Showdown
reuses. This file does not redefine them. It only states the slot-specific use and the overrides.

Inputs: `docs/research/animation.md` (capabilities, marked **[V]** where unverified), current code
(§1), `PVP.md` §3.11 and §5, `UI.md` §0 and §13, and `STRINGS.md` conventions.

Contents
0. Scope, dependencies, deviations
1. Audit: what exists today
2. Slot-wide rules (fidelity, timeline-as-data, settings, tiers, budgets)
3. Visual language (machines, symbols, cabinet, backgrounds)
4. Java screen storyboards (base spin, anticipation, wins, tumbles, wilds, free spins, bonuses, jackpots, big wins)
5. Java in-world (spectators)
7. Slot Showdown presentation
8. Sound design (MUST vanilla composites, NICE custom)
9. Assets to create (counts)
10. New strings (EN / RU)
11. Developer task breakdown (Java, Bedrock, shared)
12. MUST vs NICE summary and open questions

---

## 0. Scope, dependencies, deviations

### 0.1 In scope

The three 5×3, 243-ways machines of SLOTS.md: **Overworld Riches** (`slot_machine_copper`),
**Nether Inferno** (`slot_machine_gold`), **End Void** (`slot_machine_netherite`), with:
- reel spin, blur, bounce stop, staggered stops and honest anticipation;
- symbol idle and win animations, and way-highlight paths;
- Nether tumbles and the multiplier ladder, End expanding sticky wilds, Overworld stacked Totems;
- free-spins intro, counter, retrigger and outro (three themes);
- bonus screens: Treasure Hunt (pick-me), Piglin's Hoard (hold & spin), Dragon Wheel (3 rings);
- jackpot celebrations Mini, Minor, Major and Grand, plus the jackpot meters;
- Returned / Win / Nice / Big / Mega / Epic / Max Win sequences with the roll-up;
- the in-world cabinet for spectators (Java BER, Bedrock `slot_reels` entity);
- Slot Showdown v2 (SLOTS.md §9, PVP.md §5) presentation.

Out of scope: the HUD, toasts and server-wide announcements (global.md §4.7 and §4.10), the shared
Final Reveal (extras-pvp.md §9.4), chaos events (global.md §4.6).

### 0.2 Dependencies (task ids from other specs)

| Needed from | Id | What slots uses |
|---|---|---|
| global.md | S1 | the `scripts/gen-fx-assets.py` generator (slots adds module `scripts/fx_assets/slots.py`) |
| global.md | S3 | `WinTier` (slots keeps its own thresholds, §2.5 here) |
| global.md | J1 / B1 | `Ease`, `Tween`, `FxSettings`, GUI particle pool / `FxService` |
| global.md | J2 / B2 | sound registration pattern (slots registers its own `slots.*` ids, §8) |
| global.md | J4 | nine-slice panels, `CasinoButton` |
| global.md | J7 / B1 | `CelebrationOverlay` and `fx.celebrate` (slots calls them with slot tiers) |
| global.md | B3 | particle atlas and definitions (slots adds 6 particle types, §9.3) |
| extras-pvp.md | §9.4 / §10 | Final Reveal, countdown, lobby motion, `wheel_fx` Molang pattern |
| SLOTS.md | engine | tape, `anticipationPlan`, tumble chain, feature steps (pure logic) |

### 0.3 Deviations proposed to SLOTS.md (need the slots designer's OK)

> **Status: ACCEPTED (lead decision 2026-09-24)** — all six are applied in `SLOTS.md` (marked ⚠ CHANGED) and
> recorded in `docs/architecture/animation.md` §1. D4's API change is in `global.md` §2.6 (callers pass their
> own tier words and threshold table).

| # | SLOTS.md says | Proposal here | Why |
|---|---|---|---|
| D1 | Java symbol sprites 48 × 48 drawn in 44 px cells | sprites **40 × 40** (16 × 16 pixel art scaled ×2 plus a 4 px effect margin), drawn 1:1 in the 44 px cell; compact mode uses a separate **32 × 32** sheet | Minecraft GUI blits use nearest filtering: 48 → 44 drops pixel rows and shimmers during scrolling |
| D2 | Showdown mini-cells 14 px | **16 px** cells (5 × 16 = 80 px, fits the 124 px panel with a 40 px score column) using the 16 × 16 base art 1:1 | same reason (no fractional scaling) |
| D3 | Bedrock in-world: tumbles not animated, "the final grid is shown when the spin ends" | the cabinet's reel strip cannot show a post-tumble grid (it is no longer a strip window). **MUST:** the cabinet keeps the first landed window and shows tumbles as plate + particles. **NICE:** 3 packed row properties `g0…g2` + overlay bones show the final window (§6.6.3) | a strip-window entity would otherwise show a wrong grid, which breaks fidelity |
| D4 | slot tier words `gui.burmaldaholic.slots.tier.*`, `slots.max_win`, `slots.returned` (SLOTS §13.3) | **use the SLOTS keys** on slot screens (they carry the "!" and the RU «НЕПЛОХО!»); `CelebrationOverlay` must therefore accept a caller-supplied tier word instead of always using `gui.burmaldaholic.fx.tier.*` | global.md owners: one small API change (J7 / B1) |
| D5 | Hoard mini-spin filler not specified | empty cells spin through a **neutral ember blur** and never scroll a coin past the window | a coin sliding past an empty cell is a fake near-miss |
| D6 | Treasure Hunt open = 400 ms flipbook | the chest **rattles until the server confirms** the reveal (1 round trip), then opens | the reveal is the *i*-th tape entry, sent only on the *i*-th pick (confidentiality, §2.1 F7) |

---

## 1. Audit: what exists today

### 1.1 Java solo (`games/slots/client/SlotMachineScreen.java`, 639 lines; `SlotSymbols.java`)

| Area | Finding | Verdict |
|---|---|---|
| Layout | 3 × 3 window of 44 px cells, flat `fill` rectangles in beige `0xFFEFE6D2`, a 3 px frame coloured by tier, and flat vanilla buttons | Looks like a debug screen. No cabinet, no depth, no drum shading. |
| Symbols | vanilla item renders at 2× (`g.item` + `pose().scale(2)`) | Blurry at GUI scale 2–3 because the item is 32 px drawn over 44. Every symbol is a full item render, which is expensive. No win or blur variants are possible. |
| Reel motion | constant 25 cells/s (`elapsed / 40.0`); stops at 50 / 75 / 100 % of `spinTicks` | **No acceleration, no deceleration, no bounce.** The reel **hard-snaps** from mid-scroll to the result. |
| Filler | `ThreadLocalRandom` strips of 24 paytable symbols | Not the real reel distribution. Different on every client (spectators would disagree). |
| Wins | winning paylines drawn as 2 px lines that blink at 4 Hz for 3 s | Blinking the whole overlay at 4 Hz breaks the ≤ 3 flashes/s rule. No cell highlight or pulse. |
| Roll-up | linear 900 ms count in the status line | No easing, no tick sound, and the text is small. |
| Tiers | none; `levelup` sound for ≥ 20× or sevens; `challenge_complete` for jackpot | No big-win sequence at all. |
| Sounds | `slot_spin` (vanilla alias) at rising pitch per stop | Only one sound. No spin loop, no win stems. |
| Skip / turbo / reduce motion | none | — |
| Latency | the reels wait for the state packet before moving | 50–150 ms of dead button. |

### 1.3 In-world (both)

The three blocks are `minecraft:block/orientable` cubes with static 16 × 16 textures (Java) or
static `material_instances` (Bedrock). There are no BERs anywhere in the Java client, no `.mcmeta`
animation, no Bedrock flipbooks and no entities. A player spinning next to you is invisible.

### 1.4 Slot Showdown (branches)

- **Java** `SlotShowdownScreen` (branch `worktree-agent-a4ac73d2f6431474f`): a felt fill with
  panels. Spinning columns show `ThreadLocalRandom.nextInt(6)` **re-rolled every frame**, which is
  flicker, not motion, and differs per viewer. KABOOM = a red panel fill that jitters ±1 px. SWAP
  flashes both panels. No totals animation, no rank movement, no hazard icons, and the HOT symbol
  is a 2 px orange underline.
- Both are 3 × 3. SLOTS.md §9 moves Showdown to the 5 × 3 machines.

### 1.5 Summary of the gap

Everything the user asked for is missing: there is no motion design, no symbol art, no cabinet, no
celebrations and no spectator view. The v2 machines need all of it from scratch, so this spec treats
the current screens as throwaway and specifies the v2 presentation directly.

---

## 2. Slot-wide rules

### 2.1 Fidelity contract (MUST; extends SLOTS.md §1.4 and global.md §1)

- **F1 — Tape first.** No frame starts before the client has the tape section it animates
  (Java: `spin` state with stops). The only exception is
  the Java **outcome-free spin-up**: the reels may accelerate on the click, and if the tape has not
  arrived when reel 1 would begin its landing phase, all reels keep spinning at full speed and the
  schedule shifts (research §2.11). If the action is rejected, the reels decelerate onto the
  **previous** window in 300 ms with no bounce, then the invalid feedback plays (§4.14).
- **F2 — Real strip.** Every scrolling reel scrolls through `S_r` (SLOTS.md Appendix A) and lands
  on `t_r`. The last three cells are always the paid window. Blur frames are the blur variants of
  those same strip symbols. Seeded cosmetics only use `seed = hash(roundId, reel)`.
- **F3 — Honest anticipation.** Stop times come only from `anticipationPlan(tape)` (SLOTS.md
  §10.3), a pure function shared by the server, the Java screen, the Java BER and Bedrock. The
  client never computes its own. The anticipating reel shows the same strip at the same speed; the
  only differences are the timing, the frame glow and the sound.
- **F4 — Overshoot is physical, not a result.** A reel's bounce overshoots at most 0.18 cell into
  the real next strip cell and settles back in ≤ 200 ms. Bedrock text reels have **no** row
  overshoot (a whole-row jump would read as another result); they use a land flash instead (§6.3).
- **F5 — Monotonic numbers.** Roll-ups only go up (`outCubic`), never overshoot, and end on the
  exact server amount. Showdown totals that go down (KABOOM) count down with `inOutQuad`.
- **F6 — No early spoilers.** The balance delta (global.md J6 `holdBalanceDelta`), the result chat
  line, the win tier and the jackpot meter drop all wait for the reveal moment of their step. Other
  players' jackpot meters keep showing `pool + pending` until the winner's reveal (SLOTS.md §5.2).
- **F7 — Confidentiality.** Treasure Hunt: the client receives entry *i* only when chest *i* is
  opened. Showdown: each round's spins arrive at that round's start. Free-spin tapes may be sent
  per spin or whole. They are never shown before their spin, so either is fine; send per spin to
  keep packets small.
- **F8 — Interrupt = reveal.** Skip, Esc, closing the form, a disconnect or `reduceMotion` jumps
  to the terminal frame of the current step and prints the true amounts. A required choice (the
  next chest) is never auto-made except by autoplay (SLOTS.md §6.4).
- **F9 — No loss disguised as a win.** A spin return below the bet uses the Returned presentation
  (§4.12): no pulse, no coins, no win colours, muted tick.
- **F10 — Same result everywhere.** The player's screen, the cabinet BER / entity and any
  spectator land on the same window at the same server tick (±1 tick).

### 2.2 Timeline as data

Each round is described as a **`SlotTimeline`**: an ordered list of `Beat(tStartMs, durMs, kind,
payload)` built by a pure function `SlotTimeline.build(tape, machine, speed, anticipation)` in each
edition's slots logic package (Java `games/slots/logic/anim/SlotTimeline.java`, Bedrock
`games/slots/logic/timeline.ts`). Kinds:

`SPIN_UP, REEL_LAND(r), ANTICIPATE(r), SYMBOL_LAND(r,y), WIN_SHOW(step), WAY_CYCLE(symbol),
TUMBLE_EXPLODE(step), TUMBLE_FALL(step), MULT_UP(step), WILD_EXPAND(r), WILD_STICK(r),
FS_INTRO, FS_SPIN(i), FS_RETRIGGER, FS_OUTRO, BONUS_INTRO, HUNT_OPEN(i), HOARD_RESPIN(i),
HOARD_COLLECT, WHEEL_SPIN(ring), WHEEL_UP, JACKPOT(tier), ROLLUP(amount, tierPath), MAX_WIN, END`.

- The same builder drives visuals, sounds, the server settle timer (SLOTS.md §10.2 "settled after
  the timeline ends") and the BER / entity schedule, so every consumer agrees.
- Skip = move `t` to the end of the current beat group (spin, tumble chain, free spin, bonus step)
  in `DUR_SKIP` = 200 ms for reels (bounce kept), 150 ms otherwise.
- **Golden test vectors**: 20 fixed tapes per machine with the expected beat list (kind,
  start, duration), stored in `java/src/test/resources/fx/vectors/slots_timeline.json` (replayed by
  `SlotsVectorsTest`; location per `docs/architecture/animation.md` §3.4 — ⚠ CHANGED from `docs/design/animation/`).

### 2.4 Settings behaviour (global.md §2.8 settings, slot specifics)

| Behaviour | `reduceMotion` ON | `flashes` OFF | `speed` 0.5 / 1.5 | `celebrations` Only mine / Off |
|---|---|---|---|---|
| Reel spin | 300 ms cross-fade per reel from blur to the landed window, stops keep their stagger (so anticipation timing still reads) but no scroll, no back-kick, no bounce, no squash | unchanged | durations × 2 / × 0.67 (turbo pref = × 0.5 overrides) | — |
| Anticipation | frame glow becomes a static gold outline; sound kept | glow does not pulse | scaled | — |
| Win pulse | none; winning cells get a static 2 px frame; way paths drawn statically | unchanged | scaled | — |
| Tumble | exploding cells fade out 150 ms, the new window appears (no fall) | no explode flash | scaled | — |
| Wild expand | the column switches to the tall wild at once | unchanged | scaled | — |
| FS intro/outro | banner appears static, no flying scatters, no theme wipe (instant swap) | no screen flash | scaled | — |
| Roll-up | ≤ 300 ms | unchanged | scaled | — |
| Big-win overlay | static banner with the final tier word and amount, no rays, coins or blur | no Mega flash; Epic shake already removed by reduce motion | scaled | Off: every tier uses the in-panel Win banner |
| Jackpot | static plaque + amount; no meter explosion | no flash | scaled | Only mine: nearby cabinets' jackpot FX hidden |

Flash limits (always): at most one full-screen flash per celebration, ≤ 30 % alpha, ≥ 400 ms between
flashes. Blinking elements are cell-sized (< 2 % of the screen) or run at ≤ 2 Hz.

### 2.5 Win tiers for slots

The server computes the slot tier from the **whole spin** (base + features, excluding progressive
jackpot awards) with SLOTS.md §10.1 thresholds `slots.bigWinTiers` = [5, 15, 40, 100] × bet and
sends it with the round. The client never derives it.

| Slot tier | Presentation (this file) | Maps to global kit |
|---|---|---|
| NONE (0) | silence; reels simply rest | LOSS (slots are silent, global.md §2.4) |
| RETURNED (< 1×) | §4.12 muted line | RETURN |
| WIN (1–5×) | cell pulse + panel roll-up | WIN (in-panel, no overlay) |
| NICE (5–15×) | + in-panel banner pop + 20 GUI coins | slot-only (`CelebrationOverlay` "banner" mode) |
| BIG (15–40×) | overlay with blur, 40 coins | BIG |
| MEGA (40–100×) | + one flash, confetti 60 | MEGA |
| EPIC (≥ 100×) | + shake, fireworks at the cabinet | EPIC |
| MAX WIN (= cap) | + MAX WIN plate slam (§4.13) | EPIC + plate |
| JACKPOT (any) | §4.11 per jackpot tier, after the spin roll-up | JACKPOT (sub-tier arg) |

Note for global.md owners: the slot thresholds (5/15/40/100) differ from `core.winTiers`
(10/25/50). The **broadcast** rules (global.md §4.7) keep using the global `WinTier`; only the
on-screen slot sequence uses the slot tiers. Open question Q1.

### 2.6 Performance budget (slots; on top of global.md §2.9)

| Item | Java |
|---|---|
| Screen | ≤ 1.5 ms/frame at GUI scale 3; ≤ 400 blits (15 cells × ≤ 4 layers + frame + plates + ≤ 96 pooled GUI particles); no allocation per frame |
| Reel draw | `blit` from one machine sheet (§9.1), never `g.item` |
| BER | ≤ 0.08 ms per animating cabinet; static window when `now > sync.end + 40 t` or beyond 24 blocks; `getViewDistance()` 32 |
| Network | 1 `SpinSync` block update per spin (+1 per free spin or bonus step for spectators, coalesced to ≥ 10 t apart) |
| Script | — |
| DDUI | — |
| Particles | ≤ 60 per burst, ≤ 150 for Grand, ≤ 3 `sendParticles` per event |
| Sounds | ≤ 20/s per player (roll-up ticks ≤ 15/s) |
| Textures | 3 machine sheets ≤ 100 KB each; all GUI atlas sprites < 64 KB |

---

## 3. Visual language

### 3.1 Machine identities

Palette tokens come from global.md §2.1; each machine adds its own accents (hex for textures, §
codes for Bedrock text).

| | Overworld Riches | Nether Inferno | End Void |
|---|---|---|---|
| Mood | sunny, cosy, meadow | hot, dangerous, heavy | quiet, cosmic, eerie |
| Cabinet | oak planks + copper trim `#C06A3C`, verdigris `#5FA38A` rivets, grass tuft on the marquee | blackstone + gold trim `#FFD640`, lava seams `#FF7A1A` glowing | purpur `#A77BA7` + obsidian `#140C1C`, end-rod lamps `#F4ECF8` |
| Reel background | cream `#F4ECD8` drum with a soft sky tint at the top | netherrack red `#5A1414` → `#2A0808` drum | deep violet `#1A1028` drum with faint stars |
| Base backdrop (behind the cabinet) | sky gradient `#7EC0EE` → `#CFE8FF`, grass hills silhouette, 3 clouds drifting 2 px/s | basalt pillars, a lava lake glow at the bottom pulsing 0.25 Hz, embers rising | void black, end-stone island silhouette, 2 star layers drifting 1 / 2 px/s (parallax) |
| Free-spins theme | "Night Watch": night sky `#10183A`, moon, twinkling stars, lanterns lit on the cabinet | "Inferno": deep red `#3A0000`, heat-haze wobble, fire along the reel frame | "Void Walker": starfield swirls, purple aurora band, sticky reels in amethyst chains |
| Bonus accent | chest wood `#8B5A2B`, gold coins | piglin gold `#FFC400`, blackstone | crystal pink `#FF9AE8`, dragon purple `#6A2A8A` |
| Win colour for way paths | per symbol (§3.2) | per symbol | per symbol |
| Particles | `sparkle`, leaves (vanilla `cherry_leaves` tinted green, NICE) | `ember_burst` | `void_motes` |
| Music (FS) | light marimba loop | low drums + brass loop | pads + choir loop |

### 3.2 Symbols: art, idle and win animations

All symbols are **pixel art drawn as 16 × 16 string grids** in `scripts/fx_assets/slots.py`
(global S1 style, no text). The generator produces, per machine, one Java sheet, one Java compact
sheet, Bedrock glyph cells (3 sheets) and the entity strip textures (§9). Frames are generated by
code transforms of the base grid (shine band, scale, palette swap, particles drawn in), plus a few
hand-drawn frames where noted.

Common frame set per symbol (Java sheet columns, §9.1):

| Frames | Content | Playback |
|---|---|---|
| `base` ×1 | 16 × 16 art ×2, 1 px ink outline, 2 px drop shadow down-right (`ink` 40 %) | static |
| `blur` ×1 | base smeared vertically (3 copies at −6/0/+6 px, alpha 35/100/35 %), then squashed to 40 px | at full speed |
| `win` ×8 | the symbol's win loop (table below) | 12.5 fps (80 ms/frame), loops during `WIN_SHOW` |
| `idle` ×6 | the idle flourish (table below) | 10 fps, once, then back to `base` |
| `land` ×2 | squash key (scale-Y 0.9) and a 1-frame bright rim | on landing (code may use scale instead; the frames are for Bedrock glyph use) |

Idle scheduling (Java screen and BER): when the machine is idle, every 4–7 s (seeded) **one**
visible high or special symbol plays its idle flourish. Never during a spin, a win show or a
feature. Never implies anything: flourishes are identical for all outcomes.

| Machine | Symbol | Idle flourish (6 frames) | Win loop (8 frames) | Way-path colour |
|---|---|---|---|---|
| Overworld | Totem (WD) | wings flutter | wings spread, green-gold glow ring expands | `#FFD640` |
| | Compass (SC) | needle wobbles and spins once | needle spins fast, rim glows red | `#FF5A4A` |
| | Chest (BN) | lid peeks open 2 px, gold glint | lid bounces, coins pop out | `#C8903C` |
| | Diamond | shine band sweeps diagonally | facets sparkle (4 star glints) | `#5CE8E0` |
| | Emerald | shine band | green pulse + sparkles | `#40D060` |
| | Gold Ingot | shine band | ingot tilts ±8°, glints | `#FFC400` |
| | Iron Ingot | shine band | ingot tilts, cool glint | `#D8D8E0` |
| | Apple | leaf sways | bounces (y ±2 px), shine | `#E83030` |
| | Carrot | leaves sway | wiggles | `#FF9020` |
| | Wheat | stalks sway | stalks wave in a gust | `#E0C050` |
| | Sweet Berries | berries jiggle | berries bounce one by one | `#C02050` |
| Nether | Lava Bucket (WD) | lava surface bubbles | lava sloshes, bright orange glow | `#FF7A1A` |
| | Ghast Tear (SC) | drip forms and falls | tear pulses white-blue, 4 glints | `#BFEFFF` |
| | Piglin Coin (CN) | coin edge glint | coin spins (4-frame turn ×2) | `#FFC400` |
| | Wither Skull | eye sockets glow once | eyes blaze, dark smoke wisps | `#8A8A8A` + eyes `#FF3030` |
| | Blaze Rod | ember flicker | rod flares, sparks | `#FFB030` |
| | Magma Cream | surface bubbles | squash and stretch bounce | `#FF6A00` |
| | Nether Quartz | shine band | facets sparkle | `#F4ECE0` |
| | Nether Wart | wobble | bounce | `#A01010` |
| | Crimson Fungus | cap sways | spores puff | `#C02020` |
| | Warped Fungus | cap sways | spores puff (cyan) | `#20B0A0` |
| | Glowstone Dust | twinkle | bright flare | `#FFE070` |
| End | Dragon Egg (WD) | purple motes rise | egg cracks with light, motes swirl | `#B040FF` |
| | Eye of Ender (SC) | pupil looks left-right | eye glows green, iris spins | `#40E0A0` |
| | End Crystal (BN) | cube rotates | crystal spins, beam flicker | `#FF9AE8` |
| | Dragon Head | eye blinks | jaw opens, breath puff | `#6A2A8A` |
| | Elytra | wings fold slightly | wings spread | `#8C8CB0` |
| | Shulker Shell | lid lifts 1 px | lid opens, eye peeks | `#A77BA7` |
| | Chorus Fruit | wobble | pops (scale punch) | `#9A5A9A` |
| | Ender Pearl | shimmer | swirl | `#208070` |
| | Purpur Block | shine band | pulse | `#C8A0C8` |
| | End Rod | glow flicker | light beam pulse | `#F4ECF8` |
| | End Stone | shine band | pulse | `#E8E4A8` |

**Tall sprites** (Java sheet extras, §9.1):
- End **expanded Dragon Egg**: 40 × 132 (3 cells + 2 gaps), 8-frame shimmer loop (motes rising,
  a crack of light), plus a 3-frame **grow** key set used by code with scale-Y.
- End **sticky frame**: 44 × 136 nine-slice amethyst chain frame, 4-frame glint loop (atlas sprite,
  `.mcmeta` frametime 3).
- Overworld **Totem stack bracket**: 44 × 92 gold bracket that links two stacked Totems, 4-frame glow loop.
- Nether **coin lock frame** (Hoard): 44 × 44, 3 frames (clasps close).

Jackpot badges (Mini, Minor, Major, Grand; glyphs U+E230–U+E233): Mini `#80FF40` emerald
gem, Minor `#4080FF` lapis gem, Major `#FFD640` gold crown gem, Grand `#FF40C0` → rainbow cycling
star gem (the rainbow is a 6-frame palette cycle in Java; Bedrock uses the static magenta).

### 3.3 Cabinet frame (Java screen)

- Nine-slice `slots/<m>/cabinet.png` 64 × 64 (border 12) around the reel window; per machine
  (§3.1 materials). Marquee strip on top of the jackpot bar: `slots/<m>/marquee.png` 128 × 12
  with 4 frames (bulbs chase; `.mcmeta` frametime 3). In free spins the marquee uses
  `marquee_fs.png` (frame colour of the FS theme, bulbs flash in pairs).
- **Drum shading**: over each reel, `fillGradient` from `0xB0000000` → `0x00000000` in the top
  14 px and back in the bottom 14 px (research §2.4), plus a 1 px highlight line at 30 % white on
  the left edge of each reel and a 1 px dark divider between reels.
- **Reel window glass**: a static sprite `slots/glass.png` 220 × 132 with a diagonal 8 % white
  sheen band; in NICE, the band slides across once per 8 s (code offset).
- **Lever** (NICE): a 12 × 60 sprite at the right edge of the cabinet; on Spin it pulls down 140 ms
  `inCubic` and returns 260 ms `outBack`.

---
## 4. Java screen storyboards (`SlotMachineScreen` v2)

Layout, sizes and inputs are SLOTS.md §10.5 (400 × 240 panel, 5 × 44 px reels = 220 × 132, jackpot
bar with 4 meters × 94 px, feature panel 80 px on the left, win panel 80 px on the right, controls
row, BalanceBar; compact 320 × 220 with 32 px cells). All times below are at speed 1 and come from
the `SlotTimeline` (§2.2). Coordinates are GUI px relative to the reel window's top-left.

Rendering order (strata): backdrop → cabinet nine-slice → reel drums (scissor per reel) → drum
shading → glass → way paths / frames → plates and panels → widgets → **stratum 2**: FS/bonus/wheel
overlays → **stratum 3**: `CelebrationOverlay` (global J7) → tooltips.

### 4.1 Open, idle, attract

| t (ms) | Beat |
|---|---|
| 0 | global.md §4.14 panel entrance (fade + scale 0.97 → 1, 150 ms). The reels show the last settled window (or the machine's rest window `t_r = 0`). |
| 0–400 | Jackpot meters count up from 90 % of their value to the value (`outCubic`, 400 ms). This is a display tween of the known value, not new information. |
| idle | Backdrop layers drift (§3.1). Marquee bulbs chase (atlas animation). Every 4–7 s one idle flourish (§3.2). The Spin button breathes: glow ring alpha 0.35 ↔ 0.7 at 0.5 Hz. |
| idle ≥ 20 s | "Attract" shimmer: a diagonal sheen crosses the reel glass once (600 ms). No symbols move and no amounts show. |

### 4.2 Base spin (no anticipation)

Reel position function per reel (cells scrolled; research §2.4, adapted to land on the strip):

```
pos_r(t) =                                     // t in ms since SPIN_UP start
  t < 120:  −0.15·sin(π·t/120) + V·t²/(2·120)                     // back-kick then accelerate
  t < STOP_r − 350:  V·(t − 60)                                    // full speed, V = 0.025 cells/ms
  else:  from + (target − from) · outBack((t − (STOP_r − 350)) / 350, 1.2)
target = the strip distance from the start index to t_r, rounded UP to the first index ≥
         from + 4 cells whose value mod L_r equals t_r (so the reel never slows below its path)
```

Drawing: for `k ∈ −1…3`, cell index `i = floor(pos) + k`, symbol `S_r[(start_r − i) mod L_r]`
(reels scroll downward, so the strip is read upward), y = `(k − frac(pos)) × 44`. Speed
`> 12 cells/s` → blur frame; otherwise base frame. `start_r` = the previous window's `t_r`, so
the reel starts where it rested (continuity).

| t (ms) | Visual | Sound |
|---|---|---|
| −∞ (press) | Spin button depresses 2 px for 60 ms; label swaps to **Stop** (`slots.stop`); lever pull (NICE) | `ui.button.click` (vanilla) |
| 0–120 | all 5 reels back-kick up 0.15 cell, then accelerate (the outcome-free spin-up, F1) | `slots.spin_loop` starts, volume fades in 0 → 0.6 over 150 ms |
| 120 → | full speed; blur frames; drum shading makes motion read as a cylinder | loop |
| 250–1 200 | reel r's landing: from `STOP_r − 350` the reel decelerates with `outBack(1.2)`: it passes the target by ≤ 0.18 cell and settles | `slots.reel_stop` at `u ≈ 0.8` of the landing, pitch ladder 1.0 / 1.12 / 1.26 / 1.5 / 1.68 |
| `STOP_r` | the 3 landed symbols squash (`SQUASH`, scale about the cell centre); special symbols (scatter, bonus, coin, wild) play their `land` pop: scale 1 → 1.2 → 1 over 220 ms `outBack` + 6 `sparkle` GUI particles | scatter: `slots.scatter_land` pitch 1.0 / 1.26 / 1.5 by count so far; bonus/coin: `slots.bonus_land` |
| 1 200 | last reel lands; loop fades out over 120 ms | loop stop |
| 1 350 | win show (§4.4) or rest. With no win: the Spin button returns (label `slots.spin`), no sound (loss is silent) | — |

Stacked Totems (Overworld): when a 2-high Totem stack is fully visible after its reel lands, the
**stack bracket** (§3.2) fades in over 150 ms and its glow loop plays while the window rests. A
half-visible stack gets no bracket (it is just one Totem in the window).

### 4.3 Anticipation (honest, SLOTS.md §10.3)

Triggered only by `anticipationPlan(tape)` (F3). When reel k's stop makes the condition true:

| t from reel k's stop | Visual | Sound |
|---|---|---|
| 0 | the trigger symbols already visible (the 2 scatters, the chests on 1 and 3, the crystals on 2 and 3, or the ≥ 4 coins) start a **heartbeat**: scale 1 ↔ 1.08 at 2 Hz, plus their win-glow frame | `slots.anticipation` starts: a rising loop (Java `AbstractTickableSoundInstance`, pitch 0.8 → 1.3 over the gap) |
| 0 | the next reel gets the **anticipation frame**: an animated gold (Overworld) / flame (Nether) / purple (End) border sprite `slots/<m>/anticipation.png` 48 × 140, 8 frames, `.mcmeta` frametime 1; the landed reels dim to 70 % | — |
| 0 → 1 000 | the anticipating reel keeps full speed for 400 ms, then lands with a longer 600 ms `outCubic` + `outBack(1.2)` tail (same strip, same filler) | — |
| landing | if the feature symbol really lands, it pops (220 ms) with a stronger sparkle (12 particles) | scatter/bonus sting; anticipation loop stops |
| landing, no symbol | the frame fades out in 150 ms; nothing else happens (no "so close" sound) | loop stops (fade 100 ms) |
| next reel | if the condition still holds, the frame moves to the next reel (slide 150 ms) and the gap repeats | loop continues |

With `slots.anticipation` = false or reduce motion, the plan is still used for timing if the
server sends it; with `slots.anticipation` = false the server sends base stops only.

### 4.4 Win show: dim, way paths, symbol cycle

Data: the tape's evaluation for the current window (per winning symbol: `k`, the winning cell mask
per reel, `ways`, amount; scatter pay; step multiplier).

| t from last stop | Beat |
|---|---|
| +150 | non-winning cells dim to 40 % brightness (tint `0xFF666666`) over 150 ms |
| +150 → +1 050 | **All wins** overview (900 ms): every winning cell gets a gold 2 px frame (sprite `slots/win_frame.png` 44 × 44, 4-frame glint loop) and plays its `win` loop; **all** way paths are drawn at 50 % alpha; the win panel starts the roll-up (§4.12) |
| +1 050 → | **Symbol cycle**: one winning symbol at a time, highest amount first, 700 ms each; the other winning cells return to 40 %; the current symbol's paths draw at 100 %; label under the reels `slots.symbol_win` ("Diamond ×5 · 12 ways · 48"); a short `slots.win_small` at pitch 1.0 + 0.06·i (i = cycle index, max 6 before repeating at 1.0) at volume 0.4 |
| scatter pay | its own cycle entry: all scatters pulse, label `slots.scatter_win` |
| loop | the cycle repeats until the next spin, a feature starts, or 12 s pass (then all cells return to 100 % and only the frames stay) |

**Way paths** (the 243-ways equivalent of paylines): for a symbol P with `k` reels, connect every
winning cell of reel r to every winning cell of reel r+1 (≤ 9 segments per gap, ≤ 36 per symbol).
Each segment is a 2 px line from cell centre to cell centre in P's path colour (§3.2), drawn as a
rotated `fill` (pose rotate about the start point). A 6 px "flow" dash (lighter colour) travels
along each segment left → right at 90 px/s, so the eye reads the direction of the ways. Segment
ends are hidden under a 10 × 10 path node sprite (`slots/path_node.png`, 2 frames) on each winning
cell. The paths never cross the dimmed cells visually brighter than the frames (alpha 70 %).
Colour-blind aid (UI.md §13): the flow dash shape differs per cycle slot (dash / dot / chevron), and
the label names the symbol.

### 4.5 Nether: tumbles and the multiplier ladder

The feature panel (left) shows the ladder as 4 plates stacked vertically: `×1 ×2 ×3 ×5` (base) or
`×2 ×4 ×6 ×10` (free spins). The active plate is lit (gold rim, 2-frame flicker of flame), the others
are dark. Plate size 72 × 16 nine-slice `slots/nether/ladder_plate.png` (+ `_lit`).

Per tumble step (750 ms, SLOTS.md §10.2), after the step's win show was shortened to the all-wins
overview only (600 ms, no cycle):

| t in step (ms) | Visual | Sound |
|---|---|---|
| 0–250 | **Explode**: every cell that took part in a win (paying symbols and the Wilds on reels 1…k) scales 1 → 1.25 while alpha → 0 (`inCubic`), overlaid by a 5-frame burn sprite `slots/nether/burn.png` 44 × 44 × 5 (flame eats the symbol from the bottom); 4 `ember` GUI particles per cell rise and fade; the step amount floats up from the cluster centre: `+48` then ` ×2` in the ladder colour (600 ms rise 16 px, `outCubic`, fade in the last 200 ms) | `slots.tumble` (once per step) |
| 0–200 | the ladder steps up to the next plate: the new plate pops 1 → 1.2 → 1 (200 ms `outBack`), a flame lick travels up from the old plate to the new one | `slots.mult_up`, pitch 1.0 / 1.12 / 1.26 / 1.5 by step |
| 250–550 | **Fall**: in every reel, the surviving cells fall down to fill the gaps: `y(t) = y0 + ½·g·t²` with g = 0.0022 px/ms² (≈ 44 px in 200 ms), clamped at the target, then a single `outBounce` settle of 4 px over 100 ms. New cells enter from above the window (their start y = −44·m + existing offsets), same gravity, each reel delayed by 30 ms × r, lower cells first | per reel a soft `slots.reel_stop` at pitch 0.8 and volume 0.3 |
| 550–750 | pause; the new window is evaluated; if it wins, the next step starts at 750 | — |
| chain end | the ladder plate stays lit for the whole spin; at the next spin it resets (the lit plate slides back to ×1 over 200 ms) | — |

The ≥ 6-tumble chain chaos message (SLOTS.md §8.4) is shown by the chaos system after settlement.

### 4.6 End: expanding sticky Dragon Eggs

In free spins only (SLOTS.md §3.3). Per Egg that lands on reel 2–4:

| t from the Egg reel's stop (ms) | Visual | Sound |
|---|---|---|
| 0 | the Egg lands (normal squash + land pop) | reel stop |
| 150 | "crack": 3 light-crack frames on the Egg, 120 ms | — |
| 270–570 | **Expand**: the expanded-egg tall sprite (40 × 132) grows from the Egg's cell to the full reel: scale-Y 1/3 → 1 with `outBack(1.4)`, anchored so the Egg's row stays fixed; the three cells beneath are covered; 12 `void_motes` GUI particles burst outwards | `slots.wild_expand` |
| 570–770 | the **sticky frame** (amethyst chains) fades in (alpha 0 → 1) and clamps (scale 1.1 → 1, 200 ms `outBack`) | `slots.wild_stick` |
| rest of the feature | the reel does not spin on later free spins: during others' spin-up it shivers ±1 px once (50 ms) and its chains glint; motes rise slowly (1 particle / 400 ms) | — |

If a sticky reel covers a scatter (SLOTS.md §3.3), the scatter is simply hidden under the tall wild
(no scatter pop), so no scatter is implied.

The feature panel shows `slots.sticky` ("Sticky reels: 2/3") with three small reel pips; a pip fills
with the egg colour at `WILD_STICK`. All three sticky → the frame chains of all three reels pulse
together once and the `void_walker` chaos flavour follows after settlement.

### 4.7 Free spins: intro, counter, retrigger, outro

Intro (`FS_INTRO`, 2 000 ms; skippable after 500 ms):

| t (ms) | Visual | Sound |
|---|---|---|
| 0 | the triggering scatters pop one by one (1 → 1.3 → 1, 120 ms apart), others dim to 30 % | `slots.fs_intro` |
| 300–800 | a dark veil (stratum 2) fades to 50 %; the scatters lift out of the window (`scale 1.3`) and fly along quadratic arcs to the screen centre (500 ms `inOutSine`), leaving 3-particle trails; they merge into the banner emblem (the scatter drawn 3×) | `whoosh` layer (vanilla `entity.breeze.wind_burst`, p 1.4, v 0.4) |
| 600–900 | the **banner** (nine-slice `slots/<m>/banner.png`, theme colours) scales 0 → 1 `outBack`: line 1 `slots.fs.title` in the banner font at 2× (steps down to 1× if RU does not fit, global.md §2.2), line 2 the feature name `slots.fs.name.<m>` at 1×, line 3 `slots.fs.awarded` whose number counts 0 → N in 400 ms (integer steps, a tick per step) | `slots.rollup_tick` per step |
| 1 200–1 800 | **theme swap**: the backdrop cross-fades to the FS theme (§3.1) with a circular iris wipe from the banner (radius 0 → screen diagonal, 600 ms `inOutQuad`); the cabinet frame and marquee swap to their `_fs` sprites; the reel drum tint changes | music `slots.fs_music.<m>` fades in 0 → 0.5 over 800 ms (Java: a looping `AbstractTickableSoundInstance`) |
| 1 800–2 000 | the banner shrinks and flies into the feature panel, becoming the **counter plate** (`slots.fs.left` "Spin 1 of 12") | — |
| 2 000 | the first free spin starts by itself (no click needed) | — |

Per free spin: timings × 0.8 (`FS_SPIN_SCALE`). At each spin start the counter's first number
flips (the old digit slides up and out, the new one slides in from below, 150 ms). Overworld shows a
fixed `×2` multiplier plate that glows during wins; Nether shows the free-spin ladder (§4.5); End
shows the sticky pips (§4.6). A **feature total** plate (`slots.fs.total`) under the counter
accumulates each spin's win with a short roll-up (≤ 600 ms).

Retrigger (`FS_RETRIGGER`, 1 200 ms): the scatters pop together; a `+8` / `+5` / `+4` chip
(`slots.fs.retrigger`) flies from the reels to the counter plate (500 ms arc); the counter punches
1.25 → 1 and its total changes; `slots.fs_intro` at pitch 1.2, volume 0.6.

Outro (`FS_OUTRO`):

| t (ms) | Visual | Sound |
|---|---|---|
| 0 | after the last free spin's win show (overview only), the veil fades to 50 % | music fades out over 600 ms |
| 200 | the outro banner scales in: `slots.fs.end` ("FREE SPINS WIN 1 240"), the amount rolling up with `ROLLUP` duration for the feature total; the banner word upgrades like the big-win sequence if the feature total alone crosses Nice/Big/… (§4.12) | `slots.fs_outro`, roll-up ticks |
| roll-up end + 1 500 | the banner flies into the win panel; the theme swaps back (reverse iris, 600 ms) | — |
| then | the **spin total** roll-up and the spin's tier celebration (§4.12) play if the tier ≥ Nice | tier stem |

### 4.8 Treasure Hunt (Overworld pick-me)

Board: the reel window becomes a 5 × 3 grid of chests (SLOTS.md §10.5). Chest sprite
`slots/overworld/chest.png` 40 × 40 × 6 frames (closed, 4 opening, open) + `chest_dim` variants
generated at 50 %.

| Beat | Visual | Sound |
|---|---|---|
| Intro 600 ms | the three triggering Chest symbols pop; the reels fade to the board: 15 chests drop in from 30 px above, `outBounce` 300 ms each, 40 ms stagger in reading order; `slots.pick.hint` fades in under the board | `slots.bonus_land` per chest at low volume, pitch rising |
| Hover | the chest under the cursor wobbles ±3° at 2 Hz (rotation about its bottom centre) and its lock glints (1 frame); cursor = hand | — |
| Press | the chest squashes (scale-Y 0.92, 60 ms); the action is sent; **until the server replies with entry i**, the chest rattles (±1 px x at 20 Hz, max 1 s; then it keeps rattling slower) — D6 | `ui.button.click` |
| Open 400 ms | the lid flipbook plays (frames 1–5, 80 ms each); light rays (4 thin gold quads) fan out of the chest | `slots.chest_open` |
| Prize pop 300 ms | coins: a coin-pile sprite in 3 sizes (1–3×: small; 5–10×: medium; 25×: large, with glints) pops above the chest (`outBack`); the amount `slots.pick.prize` ("Chest: 50") floats up 12 px; the hunt total plate rolls up by the amount | coin clinks (`slots.coin_land`) × size (1/2/3 at 40 ms) |
| Jackpot gem | the gem badge (§3.2 colours) pops at 2× and **flies to its meter** in the top bar (500 ms arc); the meter flashes its colour (1 flash, ≤ 30 % alpha) and shows `WON` for the rest of the hunt; the jackpot celebration (§4.11) plays after the hunt ends | `slots.win_nice` + the tier stinger |
| Creeper | the creeper sprite rises from the chest, swells 1 → 1.35 over 600 ms (`inCubic`) with a white overlay flickering at 3 Hz (≤ 30 % alpha; with flashes off it is a static white tint that grows); at 600 ms a puff: 16 smoke GUI particles, the creeper face stays with a small crack; text `slots.pick.creeper`; **no explosion sound, no shake beyond 2 px** (reduce motion: none) | `slots.creeper_hiss`, then vanilla `entity.generic.extinguish_fire` p 1.4 v 0.4 for the puff |
| End | the remaining closed chests open **dimmed** (50 % alpha, 80 ms stagger), showing the remaining tape entries (real draws, F2 of SLOTS.md §1.2); dimmed prizes do not add to the total | soft ticks p 0.8 v 0.2 |
| Total | the hunt total plate becomes `slots.bonus.total` with a roll-up; then back to the reels (fade 300 ms) | roll-up ticks |

Autoplay: the next chest in reading order is highlighted (hover state) for 300 ms, then opened
(every 600 ms, SLOTS.md §6.4). **Open all** (Bedrock parity; Java button in the feature panel): opens
the chests one by one at 250 ms intervals until the Creeper.

### 4.9 Piglin's Hoard (Nether hold & spin)

| Beat | Visual | Sound |
|---|---|---|
| Intro 800 ms | non-coin symbols fade out (200 ms) leaving dark **empty cell** sprites (blackstone with a faint lava seam, 44 × 44); the coins scale 1.15 → 1 and their **lock frame** clasps close (3 frames, 150 ms), 60 ms stagger; the respin pips `●●●` (3 × 10 px, `slots/nether/pip.png` on/off) appear above the window; the banner `slots.bonus.hold` slides down from the top 300 ms | `slots.coin_land` per lock, pitch rising |
| Coin label | each coin shows its value as text over the sprite (1× outline, `gold`): the chip amount (value × bet, grouped, e.g. `250`); jackpot coins show the badge glyph + `slots.jackpot.tier.*` word (MINI/MINOR/MAJOR) and a coloured rim | — |
| Respin 900 ms | every empty cell becomes a 1-cell mini reel (own scissor) spinning a **neutral ember blur** (D5; never a coin in the filler), 30 ms stagger in reading order, each landing at `500 + 30·i` ms with a small bounce | a quiet spin loop at pitch 1.3 |
| New coin | lands with a heavy thump: squash 0.85, 6 gold GUI particles, lock frame clasps; the pips **refill** to 3 with a flash (200 ms); `slots.hold.reset` line blinks once under the window | `slots.coin_land` (pitch 1.0) + `slots.respin_reset` |
| No coin | one pip empties (its fill shrinks to 0 over 150 ms) | soft `block.note_block.hat` p 0.7 |
| Collect | when respins reach 0 (or all 15 are full): every coin in reading order lights (120 ms each), and its value flies as a small number to the bonus total plate (300 ms arcs overlapping); the total rolls by each value; jackpot coins pause the sweep for their mini-celebration (§4.11, Mini/Minor/Major) | `slots.rollup_tick` per coin, pitch +3 % per coin (cap +45 %) |
| All 15 filled | before the collect: the grid flashes gold once (≤ 30 %), the 15 lock frames pulse in a wave (diagonal, 40 ms delay), the text `slots.hold.full` slams in (scale 2 → 1, 200 ms), then the **Grand** celebration (§4.11) after the collect | `jackpot` |

### 4.10 Dragon Wheel (End bonus wheel)

Overlay on stratum 2 over a 60 % dark veil. The wheel is 200 px across: three concentric rings of
wedges (outer 20, middle 16, core 12, SLOTS.md §3.3) drawn as rotated wedge sprites
(`slots/end/wedge_{outer,middle,core}.png`, one wedge each, tinted per value band) with the value
text drawn radially (font at 1×, rotated with the wedge; `×N` for multiples, the badge glyph for
jackpots, `UP` arrow sprite for UP). The pointer is a 16 × 20 dragon-claw sprite at the top with a
pivot at its base.

| Beat | Visual | Sound |
|---|---|---|
| Intro 700 ms | the three crystals on reels 2–4 beam up (3 thin pink lines, 200 ms); the wheel rises from below the cabinet (`translate y +140 → 0`, `outBack`) while it turns slowly (30°/s); the inactive rings are dimmed to 40 % | `slots.wheel_up` p 0.8 |
| Ready | `slots.wheel.spin` button (and Space) — or autoplay spins after 800 ms | — |
| Ring spin (outer 4 500, middle 4 000, core 5 000 ms) | angle(t) = θ0 + (3 turns + Δ)·outCubic(t/T) where Δ lands the pointer at a **seeded offset inside the drawn wedge's central 60 %** (F4: never near a boundary); only the active ring turns | — |
| Pegs | each time a wedge boundary passes the pointer, the pointer deflects 12° against the rotation and springs back with `outElastic` (150 ms); ticks only if ≥ 50 ms since the last tick (so ≤ 20/s at speed) | `slots.wheel_tick`, pitch 0.9 → 1.4 as the speed drops |
| Land | the winning wedge brightens (+40 %), gets a pulsing outline for 600 ms, and its value pops out along the pointer (scale 0 → 1.2 → 1) | stinger by band (`slots.win_nice` for ≤ 25×, `slots.big_win` short for ≥ 40×) |
| UP | the UP wedge flashes; the pointer claw grips (2 frames); **zoom** 800 ms `inOutSine`: the whole wheel scales so the next ring fills the old outer size, the finished ring fades to 25 %; the ring title `slots.wheel.ring.middle/core` fades in above | `slots.wheel_up` (pitch 1.0, then 1.2 for the core) |
| Core | the backdrop darkens to 75 %, purple dragon-breath particles drift across (8 / s), the pointer glows | low drum layer |
| Jackpot wedge | the jackpot celebration (§4.11) plays after the wheel prize is shown | — |
| Exit | the wheel sinks (400 ms `inCubic`), the prize joins the bonus total, back to the reels | — |

### 4.11 Jackpots (Mini, Minor, Major, Grand) and the meters

**Meters** (top bar, 4 × 94 px): nine-slice plates in the badge colours (§3.2), badge glyph + tier
word + amount. The server sends pool values at most once per second; the client tweens linearly
between the last two values over 1 000 ms (always upward). When *another* player's jackpot of that
tier is revealed (F6), the plate flashes once (≤ 30 %), shows `slots.fx.meter_won` for 1 500 ms,
and then counts **down** to the reset value in 600 ms `inOutQuad` (the only allowed down-count;
it shows real pool money leaving). The Grand plate has a slow rainbow rim cycle (NICE).

**Celebrations** play after the spin total's roll-up (§2.5: the jackpot is the climax), in the local
part after the reveal gate; the step that awarded the jackpot (the hunt / hoard / wheel prize reveal)
only marks it (gem / coin / wedge). They use `CelebrationOverlay` in JACKPOT mode with a sub-tier:

| | Mini | Minor | Major | Grand |
|---|---|---|---|---|
| Length (skippable after) | 2 000 ms (500) | 2 500 ms (500) | 3 000 ms (800) | 4 000 ms (1 000) |
| Meter | the plate pops out of the bar and flies to the centre (400 ms arc) | same | same + the other three plates shake 1 px | the plate **shatters** into 24 coin sprites that burst outwards, then the Grand badge re-forms at the centre (600 ms) |
| Backdrop | 30 % veil | 40 % + rays in the tier colour, 20°/s | 50 % + blur behind + double rays counter-rotating | 55 % + blur + double rays + gold edge vignette (≤ 30 %, 1 Hz, flashes on only) |
| Word | `slots.jackpot.won` with the tier word, 2×, `outBack` | 3× `outBack` | 3× `outElastic` | 3× `outElastic` + letter wave (per char y = 2·sin(t·8 + i)) |
| Amount | roll-up 1 000 ms | 1 400 ms | 2 000 ms | 3 000 ms |
| GUI particles | 30 coins | 45 coins | 60 coins + 20 confetti | 96 (pool cap): coins + confetti + 8 badge stars |
| Screen flash | none | none | one 25 % gold flash at the word | one 30 % flash at the re-form (flashes on) |
| Shake | none | none | none | 4 px, 400 ms decaying (reduce motion: none) |
| World (server, §5.3) | `coin_burst` 20 | 30 | 40 + fireworks sparks ×3 at the cabinet | 60 + `jackpot_burst` + fireworks sparks ×6 + nearby sound |
| Sound | `jackpot` at p 1.3, v 0.6 | p 1.15 | p 1.0 | p 1.0 + `slots.epic_win` layer + `entity.ender_dragon.growl` p 1.6 v 0.3 (End only) |

Multiple jackpots in one spin play in tape order, each shortened to 70 % after the first.

### 4.12 Roll-up and the big-win sequence (Returned / Win / Nice / Big / Mega / Epic)

The win panel (right) always shows the running amount; overlays add on top by tier (§2.5).

| Tier | Sequence |
|---|---|
| Returned | no dim, no pulse, no frames; the panel shows `slots.returned` in `bone.shade` gray for 1 500 ms; one `slots.returned` muted tick |
| Win | win show (§4.4) + panel roll-up (`ROLLUP`, ≈ 600–1 300 ms, ticks ≤ 15/s at rising pitch) + `slots.win_small` at the end |
| Nice | + a tier plate (nine-slice `slots/tier_plate.png`, 2× `slots.tier.nice`) pops in the cabinet header over the title plate, ending above the reel window so every winning cell stays visible (250 ms `outBack`), 20 GUI coins burst from it; `slots.win_nice`. ⚠ CHANGED (J-L9b): was "over the reels' lower edge", which hid the bottom row |
| Big / Mega / Epic | `CelebrationOverlay` (global J7) with the slot tier word supplied (D4). It **starts at the Nice word** (or Big if the total is > 15× already at the first frame) and **upgrades** at the moments the rolling value passes 15×, 40× and 100× the bet: 150 ms punch (1.25 → 1), rays change colour (gold → orange → magenta), the coin emitter rate steps 20 → 40 → 60 per s, and the tier stem plays (`slots.big_win` → `slots.mega_win` → `slots.epic_win`). The final word always equals the server tier. Mega adds one 30 % flash at its upgrade (flashes on). Epic adds a 4 px 400 ms shake at its upgrade and fireworks at the cabinet (server, §5.3). |
| Hold | at the final exact value: 800 ms hold, then `global` fx skip hint; any key/click dismisses; autoplay dismisses after 1 500 ms |

The roll-up value is `floor(win × outCubic(t))` and the last frame prints the exact server total.
Tick pitch = 1.0 × (1.01)^n, capped at 1.4. The Java HUD balance float (global J6) is held until the
roll-up ends (F6).

### 4.13 Max Win

When the tape says the cap was reached (SLOTS.md §1.3): the current step freezes; remaining
free-spin counter plates grey out and slide away (300 ms); a steel **MAX WIN** plate
(`slots.max_win`, nine-slice `slots/maxwin_plate.png`) slams in from 2× to 1× in 200 ms `inCubic`
with a 4 px shake (not with reduce motion) and `slots.max_win`; then the Epic sequence runs with the
capped total. The plate stays on the win panel until the next spin.

### 4.14 Controls, interaction feedback, buy, autoplay

| Control | Normal / hover | Press | Invalid |
|---|---|---|---|
| **Spin** (round 56 × 40 sprite `slots/spin_button.png`, 5 states: normal, hover, pressed, disabled, stop; the icon is never covered: the cost / Stop is a 1× caption under the button) | breathing ring (0.5 Hz); hover: ring brightens, 1 px lift | 2 px depress 60 ms; during a spin the button shows **Stop** (red square icon) and pressing it = skip (§2.2) | global.md §2.2 shake (±2 px, 240 ms) + `ui_deny` + the error line (e.g. `error.bet_unavailable`) slides in; the reels do **not** move |
| Bet − / + | `CasinoButton` | the bet value flips: the old number slides out upward (+) or downward (−), 120 ms; `chip_place` pitch 1.2 (+) / 0.9 (−) | at the ladder end: the button shakes, no sound other than `ui_deny` |
| Buy (Nether, End) | primary style; hover shows the RTP tooltip | opens a confirm panel (stratum 2, scale-in 150 ms) with `slots.buy.confirm_*`; confirm → the panel folds into the reels and the FS intro starts directly (no base spin) | as above |
| Auto… | `CasinoButton` | opens the autoplay panel; while running, the button shows `slots.auto_left` counting down with a flip per spin | — |
| Turbo | toggle sprite (off/on); on = a small lightning glyph glows | toggle click | — |
| Paytable | `CasinoButton` | overlay slides in from the right (200 ms `outCubic`) | — |

Keyboard: Space/Enter = Spin / Stop / skip; focus rings per global.md §2.2. Narrator: at each step
end the screen narrates the result line (UI.md §13).

---

## 5. Java in-world (spectators)

### 5.1 Cabinet model

SLOTS.md §11: the cabinet is 1 × 2 blocks visually. Block model (static, generated JSON): a
1-block base (`<m>_base`) plus an upper part rendered by the BER as geometry (so no second block is
needed): the reel face, the marquee and (End) the crystal wheel. Front texture layout (entity
texture `textures/entity/slots/<m>_cabinet.png`, 64 × 64): cabinet body, trims, reel window glass,
marquee bulbs (4-frame UV cycle driven by `gameTime`, not `.mcmeta`, so the BER can switch patterns).

### 5.2 `SlotCabinetRenderer` (BER, MUST)

State synced per spin (`SpinSync`, SLOTS.md §10.5): `startTick, stops[5], stopTicks[5]` (already
including anticipation), tumble step count and the final window (15 symbol codes), per-free-spin
stops and sticky mask, feature code, `tier`, `seed`. Extract/submit pattern: research §2.6.

| Element | Rendering |
|---|---|
| Reels | 5 drums, each a 6-quad partial cylinder (60° arc) behind the glass, UV-scrolled over the machine's **strip texture** `textures/entity/slots/<m>_strip_<r>.png` (16 × 16·L_r: Appendix A drawn as 16 px cells) — the same `pos_r(t)` as §4.2 evaluated on `gameTime + partialTick`; blur = a second strip texture `_blur` drawn at full speed |
| Win frames | after the last stop: the winning cells (from the synced evaluation mask) get an emissive gold frame quad (2-frame blink at 2 Hz, 3 s) |
| Tumbles | exploding cells fade (emissive orange quad 250 ms), the column's cells are redrawn from the synced step windows (fall as a y-offset with the same gravity); each step raises a small `×N` text above the reel face (`submitText`, 20 t) |
| Sticky wilds (End FS) | an emissive purple column quad with the tall-egg texture over the reel |
| Hoard | the face shows 15 coin/empty cells from the synced mask (coins as a gold emissive icon) and 3 pip quads |
| Dragon Wheel | the End cabinet's crystal wheel (a 3-ring disc on top, `submitCustomGeometry`) spins to the synced segments with the same curve as §4.10 |
| Marquee | pattern by state: idle chase (1 bulb step / 3 t), spin fast chase (1 / 1 t), win all-blink (2 Hz), big alternating halves (4 Hz), jackpot rainbow cycle, FS theme colour |
| Tier text | Big+ : a floating translated tier word + amount above the cabinet for 60 t (`submitText`, billboard, scale 0.02, rises 0.3 blocks), language resolved per viewer |
| Distance | full animation ≤ 24 blocks (`slots.inWorld.radius`), static rest window beyond; `getViewDistance()` 32 |

Spectators see exactly what the player's screen shows, on the same ticks (F10). The player
themself sees the BER too (behind their screen).

### 5.3 World FX (server)

| Event | Particles (`sendParticles`, one call each) | Sound (positional, `SoundSource.BLOCKS`) |
|---|---|---|
| Spin | none | `slots.spin_loop` v 0.3 (players ≤ 12 blocks) |
| Nice | `burmaldaholic:sparkle` 12 above the marquee | `slots.win_nice` v 0.4 |
| Big / Mega | `coin_burst` 40 / 60 (+ `confetti` for Mega) | tier stem v 0.5 |
| Epic / Max | `coin_burst` 60 + vanilla `firework` sparks 3 × 12 | tier stem v 0.7 |
| Free spins trigger | `sparkle` ring 20 | `slots.fs_intro` v 0.5 |
| Jackpot | §4.11 row "World" | `jackpot` v 0.8 |
| Nether tumble step | `ember_burst` 8 at the reel face | — |
| End sticky | `void_motes` 6 per sticky reel, once | — |

Block state `win=none|small|big|jackpot` (SLOTS.md §10.5) is kept for resource packs and vanilla
fallback; the BER ignores it.

---
## 7. Slot Showdown v2 presentation

Rules and UI frame: SLOTS.md §9 (5 × 3 machines, points, HOT symbol, hazards, montage features)
and PVP.md §5.5 (panels, compact mode, Bedrock flow). The shared lobby, countdown and **Final
Reveal** are extras-pvp.md §9. Timeline per round (server-paced, identical for every viewer;
`anim.speed` never applies): ROUND_WAIT ≤ 100 t → ROUND_SPIN 40 t → feature montage ≤ 60 t (only
when someone triggered one) → ROUND_SCORE 20 t per event.

### 7.1 Java `SlotShowdownScreen` (400 × 240; compact 320 × 220)

Panels 124 × 76 (PVP.md): name row, **5 × 3 mini reels at 16 px (D2)**, total, rank, spin points,
feature badge and hazard slot. Your panel has a gold breathing rim (0.5 Hz).

| Beat | Visual | Sound |
|---|---|---|
| Round start | the header card flips (X-scale 1 → 0 → 1, 180 ms) to show the **HOT** symbol (sprite at 2×) with 6 flame GUI particles licking its edge; `gui.burmaldaholic.pvp.slots.hot` text | vanilla `item.firecharge.use` p 1.2 (PVP.md) |
| ROUND_WAIT | panels of players who pressed *Spin!* get the check glyph (pop `outBack` 200 ms); your *Spin!* button breathes; the countdown ring (extras-pvp §9.4) | `chip_place` per ready |
| ROUND_SPIN (2 000 ms) | every panel spins its **own tape** on the real strip (same `pos_r(t)` as §4.2 with stops compressed to `900 + 200·(r−1)` ms); no anticipation (server-paced); blur frames; all panels stop together per reel (so rows of panels "clunk" in sync) | one `slots.reel_stop` per reel index for all (not per panel) |
| Land | HOT-symbol cells in winning ways get a flame underline sprite (3-frame loop); winning cells glint once | — |
| Feature montage (≤ 3 000 ms) | the panel with a feature grows 1.0 → 1.15 (others dim 60 %); its reels run a fast montage: each free spin = 180 ms blur + land, the badge (`FS 12` / `HOARD` / `WHEEL`) counts down, and `gui.burmaldaholic.pvp.slots.feature` counts up the points; ends with a 300 ms punch | `slots.fs_intro` short, ticks |
| Score (ROUND_SCORE) | `+N` floats up from the panel (600 ms); the total rolls to its new value (400 ms `outCubic`); **rank change**: panels slide to their new slots (300 ms `inOutQuad`), rank badges flip | `slots.rollup_tick` bursts |
| Hazard drop | after the land, the hazard icon falls from the panel's top edge onto the hazard slot (400 ms `outBounce`) | — |
| KABOOM | the creeper swells 1 → 1.4 (500 ms), one 25 % white flash over the panel only, the panel shakes (3 px, 400 ms decaying), a 4-frame crack overlay sprite stays for the round, and the total counts **down** to half (500 ms `inOutQuad`, red) | `slots.creeper_hiss` → vanilla `entity.generic.explode` v 0.5 (PVP.md) |
| SWAP | the two panels' totals lift out (scale 1.3) and fly along two crossing arcs (500 ms `inOutSine`) with purple trail particles, land and punch | `entity.enderman.teleport` |
| TIME WARP | the clock icon spins 360° (600 ms), then a `×2` chip (`pvp.slots.fx.next_x2`) clips onto the panel with a blue glow that stays until the next spin | `block.bell.use` p 1.5 |
| Underdog (before the final round) | the boosted panel's rim turns blue and pulses (1 Hz); a `×2` chip | existing |
| Final round | totals show `???`; the Final Reveal (extras-pvp §9.4) takes over | drumroll |

Compact mode (PVP.md): your panel at 2× (32 px cells = the compact sheet), ranking list on the
right; list rows slide on rank changes; hazard icons appear next to the names.

### 7.3 Fidelity in Showdown

Each round's grids arrive at ROUND_SPIN start (F7); the final round's grids are shown but totals
stay hidden until the Final Reveal (PVP.md §5.3). Hazard icons drop only after the reels land.
Montages replay the real free-spin results (compressed), never a random sequence.

---
## 8. Sound design

Ids are SLOTS.md §10.7 (Java `burmaldaholic:slots.<id>` in `java/src/main/sounds/slots/sounds.json`;
Bedrock `burmaldaholic.slots.<id>` in `packs/slots/RP/sounds/sound_definitions.json`, category
`player`; subtitles SLOTS.md §13.9). **MUST = vanilla composites** (several `sounds` entries played
together are not possible in one event, so "+" below means the code plays two events in the same
tick). **NICE = custom `.ogg`** (global S4 synthesiser: mono, 44.1 kHz, ≤ 100 KB, peaks −3 dBFS),
same ids, so nothing else changes.

Musical frame: everything is in **C major pentatonic**; reel stops walk up the scale (C D E G A:
pitch 1.0 / 1.12 / 1.26 / 1.5 / 1.68), scatters climb a higher ladder, wins resolve on C. Loss is
silent.

| Id | MUST composition (event) | Played by code as |
|---|---|---|
| `slots.spin_loop` | `entity.minecart.riding` p 1.6 v 0.25 / `minecart.inside` | Java: looping `AbstractTickableSoundInstance`, fade in 150 ms / out 120 ms |
| `slots.reel_stop` | `block.lever.click` p 0.7 v 0.5 **+** `block.note_block.pling` at the ladder pitch v 0.35 | per reel |
| `slots.scatter_land` | `block.amethyst_block.chime` v 0.8 **+** `block.note_block.bell` (ladder 1.0 / 1.26 / 1.5) | per scatter |
| `slots.bonus_land` | Overworld `block.chest.locked` p 1.3; Nether `block.chain.place` p 1.8; End `block.end_portal_frame.fill` p 1.2 | per symbol |
| `slots.anticipation` | `block.beacon.ambient` (pitch ramp 0.8 → 1.3) | loop |
| `slots.returned` | `block.note_block.hat` p 0.8 v 0.3 | once |
| `slots.win_small` | `entity.experience_orb.pickup` p 1.2 **+** `block.note_block.chime` p 1.5 v 0.4 | end of roll-up; symbol cycle at v 0.4 |
| `slots.win_nice` | `block.note_block.chime` C-E-G arpeggio (p 1.0/1.26/1.5, 80 ms apart) **+** orb | once |
| `slots.big_win` | `entity.player.levelup` **+** `block.amethyst_block.resonate` p 1.2 | stem 1 |
| `slots.mega_win` | `ui.toast.challenge_complete` v 0.7 **+** `block.bell.use` p 1.5 v 0.4 | stem 2 |
| `slots.epic_win` | `ui.toast.challenge_complete` **+** `entity.firework_rocket.large_blast` v 0.5 **+** `block.bell.use` p 1.0 | stem 3 |
| `slots.max_win` | `epic_win` **+** `block.anvil.land` p 1.2 v 0.3 | plate slam |
| `slots.rollup_tick` | `block.note_block.hat` v 0.35 (pitch by code) | ≤ 15/s |
| `slots.rollup_end` | `block.note_block.bell` p 1.5 v 0.5 | once |
| `slots.fs_intro` | `block.beacon.activate` p 1.3 **+** `block.amethyst_block.resonate` | intro, retrigger (p 1.2) |
| `slots.fs_outro` | `block.beacon.deactivate` p 1.2 **+** `block.note_block.chime` p 1.0 | outro |
| `slots.fs_music.overworld / nether / end` | vanilla music events `music.overworld.meadow` / `music.nether.crimson_forest` / `music.end` (Bedrock `music.game.end`) at v 0.5 **[V ids]**; NICE: 20–30 s custom loops | stream loop |
| `slots.wild_expand` | `block.respawn_anchor.charge` p 1.2 | End expand |
| `slots.wild_stick` | `block.chain.place` p 0.8 **+** `block.amethyst_block.place` | sticky clamp |
| `slots.tumble` | `block.fire.extinguish` p 1.4 v 0.4 **+** `item.firecharge.use` v 0.3 | per step |
| `slots.mult_up` | `block.note_block.pling` (ladder pitch) **+** `block.blastfurnace.fire_crackle` | per step |
| `slots.chest_open` | `block.chest.open` p 1.1 | per chest |
| `slots.creeper_hiss` | `entity.creeper.primed` | creeper |
| `slots.coin_land` | `block.chain.place` p 1.6 **+** `entity.experience_orb.pickup` p 0.7 v 0.4 | coin |
| `slots.respin_reset` | `block.note_block.chime` p 1.5 ×2 (60 ms apart) | reset |
| `slots.wheel_tick` | `block.wooden_button.click_on` p 1.4 v 0.5 (pitch by code 0.9 → 1.4) | per peg, ≤ 20/s |
| `slots.wheel_up` | `block.beacon.power_select` p 1.2 | UP / intro |
| `jackpot` (core) | global.md §2.7 | jackpots (pitch by sub-tier) |

---

## 9. Assets to create

Everything is generated by `scripts/fx_assets/slots.py` (module of global S1; string-grid pixel art,
deterministic, **no text**; digits only in the Nether ladder and wheel plates of the entity overlay).
The strip textures read the strips from the config defaults (Appendix A) so art never drifts from the
maths.

### 9.1 Java

| Asset | Path (`java/src/main/resources/assets/burmaldaholic/…`) | Size | Frames / notes | Count |
|---|---|---|---|---|
| Machine symbol sheet | `textures/gui/slots/<m>_symbols.png` | 640 × 440 (16 cols × 40, 11 rows) | col 0 base, 1 blur, 2–9 win (8), 10–15 idle (6); row = symbol in SLOTS.md §2 order | 3 |
| Compact sheet | `textures/gui/slots/<m>_symbols_32.png` | 512 × 352 | same layout at 32 px | 3 |
| Mini sheet (Showdown) | `textures/gui/slots/<m>_symbols_16.png` | 32 × 176 | base + blur | 3 |
| End tall egg | `textures/gui/slots/end_egg_tall.png` | 440 × 132 | 8 shimmer + 3 grow keys | 1 |
| Backdrops | `textures/gui/slots/<m>_backdrop.png`, `<m>_backdrop_fs.png`, `<m>_parallax.png` | 400 × 240, 400 × 240, 256 × 128 tile | layers drift by code | 9 |
| Cabinet nine-slice | `textures/gui/sprites/burmaldaholic/slots/<m>/cabinet.png` (+ `_fs`) | 64 × 64, border 12 | — | 6 |
| Marquee | `…/slots/<m>/marquee.png`, `marquee_fs.png` | 128 × 48 | 4 frames `.mcmeta` frametime 3 | 6 |
| Banners | `…/slots/<m>/banner.png`, `banner_small.png` | 48 × 48 / 32 × 32 nine-slice | — | 6 |
| Anticipation frame | `…/slots/<m>/anticipation.png` | 48 × 1 120 | 8 frames × 140, frametime 1 | 3 |
| Shared sprites | `…/slots/glass`, `win_frame` (4 fr), `path_node` (2 fr), `spin_button` (5 states), `maxwin_plate`, `jackpot_plate_{mini,minor,major,grand}`, `pip_on/off`, `turbo_on/off`, `lever` (NICE) | see §3.3, §4 | — | 17 |
| Overworld feature | `…/slots/overworld/{chest (6 fr), chest_dim, coin_pile_{s,m,l}, creeper (4 fr), stack_bracket (4 fr)}` | 40 × 40 (bracket 44 × 92) | — | 7 |
| Nether feature | `…/slots/nether/{burn (5 fr), ladder_plate, ladder_plate_lit (2 fr), coin_lock (3 fr), empty_cell, ember_blur}` | 44 × 44, plates 72 × 16 | — | 6 |
| End feature | `…/slots/end/{sticky_frame (4 fr), wedge_outer, wedge_middle, wedge_core, pointer (2 fr), up_arrow}` | frame 44 × 136, wedges ≤ 64 × 64 | — | 6 |
| Showdown | `…/pvp/slots/{hazard_kaboom, hazard_swap, hazard_warp, crack (4 fr), flame_underline (3 fr), chip_x2, check}` | 16 × 16 (crack 124 × 76) | — | 7 |
| BER textures | `textures/entity/slots/<m>_strip_<r>.png` (16 × 16·L_r), `<m>_blur.png`, `<m>_cabinet.png` (64 × 64), `<m>_overlay.png` (64 × 64) | — | — | 24 |
| Particles | `textures/particle/burmaldaholic/{coin_0..7, sparkle_0..3, confetti_0..5, ember_0..3, void_mote_0..3, jackpot_star_0..3}.png` + `particles/*.json` | 8 × 8 | shared with extras (`coin`, `sparkle`: reuse if already registered) | 30 PNG + 6 JSON |
| Block flipbook | `textures/block/slots/slot_machine_<tier>_front.png` + `.mcmeta` | 16 × 64 | 4 frames | 3 |
| Sounds | `sounds/slots/sounds.json` entries | — | §8 | 26 ids |

Java total: **≈ 140 PNG files** (+ 3 `.mcmeta` block, 12 `.mcmeta` GUI), 1 font already planned
(global banner font), 1 `sounds.json`.

### 9.3 Particle definitions (same ids)

| Id | Look | Motion | Budget |
|---|---|---|---|
| `coin_burst` | 8-frame spinning gold coin, 8 px | fountain up 4–7 b/s, gravity 12 b/s², 1.2 s life, bounce 0.3 on blocks | 20–60 |
| `sparkle` | 4-frame 4-point star, gold/lilac tint | still, scale pulse, 0.6 s | 6–20 |
| `confetti` | 6 colour chips, tumbling (UV flip) | fall 1 b/s with sway | ≤ 60 |
| `jackpot_burst` | large 4-frame star + ring | radial 3 b/s, 1.5 s | ≤ 30 |
| `ember_burst` | 4-frame ember, orange → red | rise 1.5 b/s, flicker, 0.8 s | 8–16 |
| `void_motes` | 4-frame purple mote | slow rise 0.4 b/s, 2 s | 6–12 |

---

## 10. New strings (EN / RU)

SLOTS.md §13 already defines almost every slot string (tier words, feature banners, counters, hunt,
hoard, wheel, jackpots, buy, autoplay, Showdown). This file uses those keys and adds only these. RU
fits the 1.45 × budget in every fixed box listed (meter plate 94 px, chips 40 px, wedge 28 px at 1×).

### slots — animation additions

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.slots.fx.meter_won` | WON! | ВЫИГРАН! |
| `gui.burmaldaholic.slots.fx.times` | ×%1$s | ×%1$s |
| `gui.burmaldaholic.slots.fx.wheel_up_wedge` | UP | ВВЕРХ |
| `gui.burmaldaholic.slots.fx.step_win` | +%1$s ×%2$s | +%1$s ×%2$s |
| `gui.burmaldaholic.slots.fx.cabinet_view` | Watch the machine while spinning | Смотреть на автомат при вращении |
| `gui.burmaldaholic.slots.fx.cabinet_view.tooltip` | Closes the window during a spin and turns the camera to the machine | Во время вращения закрывает окно и поворачивает камеру к автомату |

(`fx.times` args: %1$s num. `fx.step_win` args: %1$s num (chips), %2$s mult. The Java hunt's
*Open all* button reuses `gui.burmaldaholic.slots.pick.open_all`; floating `+N` amounts reuse
`gui.burmaldaholic.fx.amount`; hidden Showdown totals reuse `gui.burmaldaholic.pvp.match.hidden`.)

### pvp — Slot Showdown animation additions

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.pvp.slots.fx.next_x2` | Next ×2 | Следующее ×2 |
| `gui.burmaldaholic.pvp.slots.fx.boost_x2` | Boost ×2 | Буст ×2 |

---

## 11. Developer task breakdown

Tasks can run in parallel within an edition once their dependencies land. Ids: `SX` shared,
`JS` Java, `BS` Bedrock. Estimates are rough (one developer).

### 11.1 Shared

| Id | Task | Files | Depends on | Est. |
|---|---|---|---|---|
| SX1 | Slots asset module: 33 symbols as 16 × 16 grids + frame generators (blur, win, idle, land), Java sheets (40/32/16), glyph planes E2/E3/E4 at 512, entity strips from Appendix A, overlays, cabinets, backdrops, all §9 sprites, particle sprites, flipbooks, fallback window cuts (flag) | `scripts/fx_assets/slots.py` | global S1 | 3–4 d |
| SX2 | `SlotTimeline` pure builder (beats of §2.2/§2.3, turbo, reduce-motion variant) + unit tests + golden vectors JSON | `games/slots/logic/anim/SlotTimeline.java`; `docs/design/animation/slots-timeline-vectors.json` | SLOTS engine (tape, `anticipationPlan`, tumble chain) | 1.5 d |
| SX3 | Sound definitions for §8 | `java/src/main/sounds/slots/sounds.json` | global J2/B2 | 0.5 d |
| SX4 | Strings §10 into STRINGS.md and lang files | STRINGS.md, lang | — | 0.2 d |

### 11.2 Java

| Id | Task | Files | Depends on | Est. |
|---|---|---|---|---|
| JS1 | `ReelView`: `pos_r(t)`, strip reading, blur switch, drum shading, squash, land pop, sheet blits, spin-up/reject path | `games/slots/client/reels/ReelView.java`, `SymbolSheet.java` | SX1, SX2, J1 | 1.5 d |
| JS2 | `SlotMachineScreen` v2 shell: 400 × 240 + compact layout, cabinet, backdrop layers, meters (tween), side panels, controls with feedback (§4.14), buy/auto/turbo panels, paytable overlay, strata | `games/slots/client/SlotMachineScreen.java`, `…/client/panels/*` | J4, JS1 | 2 d |
| JS3 | Win show: dim, way paths (rotated fills + flow dashes), symbol cycle + labels, panel roll-up, Returned, Nice banner | `…/client/WinShow.java`, `WayPaths.java` | JS1 | 1.5 d |
| JS4 | Anticipation visuals + tickable sounds (spin loop, anticipation ramp) | `…/client/SlotSounds.java` | JS1, SX3 | 0.5 d |
| JS5 | Nether tumbles (burn, fall with gravity/bounce, refill, float labels) + ladder plates | `…/client/features/TumbleView.java` | JS1, JS3 | 1 d |
| JS6 | End expand + sticky reels; Overworld stack bracket | `…/client/features/WildView.java` | JS1 | 0.75 d |
| JS7 | Free spins: intro (flying scatters, banner, iris theme swap), counter/total plates, retrigger, outro, music | `…/client/features/FreeSpinsView.java` | JS2, JS3 | 1.5 d |
| JS8 | Treasure Hunt board (hover/press/rattle/open/prize/gem flight/creeper/dimmed end) + server "reveal i-th entry on i-th pick" action | `…/client/features/HuntView.java`, server slots handler | JS2 | 1.5 d |
| JS9 | Piglin's Hoard (mini reels, locks, pips, collect sweep, full-grid) | `…/client/features/HoardView.java` | JS1, JS2 | 1.5 d |
| JS10 | Dragon Wheel overlay (rings, radial text, flapper, zoom, core) | `…/client/features/DragonWheelView.java` | JS2 | 1.5 d |
| JS11 | Jackpot celebrations by sub-tier + meter drop for others | `…/client/JackpotFx.java` | J7, JS2 | 1 d |
| JS12 | Big-win integration: tier-word injection into `CelebrationOverlay`, upgrade beats, Max Win plate, HUD delta hold | `…/client/BigWinFx.java`, J7 API change | J7, J6 | 0.75 d |
| JS13 | Protocol: `spin` state carries the tape section + timeline params; server settle timer from `SlotTimeline`; skip action | `games/slots/SlotsModule.java`, `SlotMachineBlockEntity.java` | SX2 | 1 d |
| JS14 | `SlotCabinetRenderer` (BER) + `SpinSync` (+ previous stops) + cabinet model + marquee patterns + tier text | `games/slots/client/SlotCabinetRenderer.java`, `SlotMachineBlockEntity.java` | SX1, SX2 | 2.5 d |
| JS15 | World FX (server) + 6 particle types/providers | `games/slots/SlotsFx.java`, `…/client/SlotsParticles.java`, `particles/*.json` | SX1, J11 pattern | 1 d |
| JS16 | Showdown screen (§7.1) | branch `SlotShowdownScreen.java`, `ShowdownPanel.java` | JS1, extras-pvp Final Reveal | 2 d |
| JS17 | Settings + reduce-motion/flash paths across JS1–JS16 + client gametests (open screen, run a fixed tape, assert terminal window and that the timeline length matches) | tests under `java/src/gametest/…/slots` | J1 | 1 d |

## 12. MUST vs NICE, and open questions

### 12.1 MUST (ship with slots v2)

- Both: fidelity contract F1–F10, `SlotTimeline` with golden vectors, honest anticipation,
  skip, turbo, reduce motion and flash limits, all SLOTS.md §10.7 sounds as vanilla composites,
  particle set, tiers with upgrade beats, jackpot sub-tier celebrations, Max Win.
- Java: sheet-based reels with spin-up, blur, `outBack` landing, squash, drum shading; win show with
  way paths and symbol cycle; tumbles; expanding sticky wilds; stack bracket; free spins intro,
  counters, retrigger, outro and theme swap (music vanilla); Treasure Hunt, Hoard, Dragon Wheel as
  storyboarded; meters; BER cabinet for spectators with tumbles, sticky, Hoard, wheel; world FX;
  Showdown screen §7.1.

### 12.2 NICE

- Custom `.ogg` sounds and machine music loops (S4).
- Java: lever, glass sheen sweep, Grand rainbow rim, parallax extra layer, shader shimmer on banners
  (global phase 3).
- Idle flourishes on the BER / entity (Java screen idle flourishes are MUST: they are cheap).

### 12.3 Open questions

1. **Tier thresholds** (Q1) — **RESOLVED (lead, 2026-09-24):** one shared `WinTier` API with per-game
   threshold tables. Slots use `WinTierTable.SLOTS` (Nice/Big/Mega/Epic at 5/15/40/100 ×); table and extras
   games use the global default (WIN/BIG/MEGA/EPIC at 10/25/50 ×). Server-wide broadcasts and the chaos
   big-win rule keep using the default table (global.md §2.4, §4.7).
2. **DDUI** (BS0): update-rate limit and whether glyphs render at a readable size in a
   `CustomForm` label. If the label is too small, the form keeps only status and buttons, and the
   reels move to the in-world cabinet plus the action bar.
3. **Glyph tinting** by `§` codes (§6.1) and **per-controller `uv_anim`** (§6.6.3) — both have
   fallbacks.
4. **Music ids**: verify the vanilla music event names, or ship the NICE loops.
5. **Deviations D1–D6** (§0.3) — **ACCEPTED** and applied to SLOTS.md; the D4 API change is in global.md §2.6.
