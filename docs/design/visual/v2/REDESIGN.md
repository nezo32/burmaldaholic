# Table UI v2 — "the screen is the table" (Java, v0.2.0)

Status: design spec, ready to implement. Scope: every game screen except the slot machine (blackjack, baccarat /
chemmy, Texas Hold'em, UTH, roulette, craps, Plinko, Wheel of Fortune, scratch cards, Dice Duel, Coin Flip, the PvP
invite / duel screens) and the in-world view of every table. Replaces the screen-layout parts of
`visual/cards.md` §6, `visual/tables.md` §6 and `visual/extras.md` §2.2–2.4; their art sections (card faces, felt
prints, wheel heads, coin art) stay valid.

Files in this folder:

| File | What |
|---|---|
| `before/*.jpg` | contact sheets of the current build (v0.1.1, `9485e37`) captured by the client GameTest below |
| `v2_*_480.png` | mockups at 480 × 270 GUI px (1920 × 1080 at GUI scale 4, the most common real setup), shown ×4 |
| `v2_*_640.png` | the same screens at 640 × 360 GUI px (2560 × 1440 at scale 4, or 1920 × 1080 at scale 3), shown ×4 |
| `v2_grid_*.png`, `v2_components.png` | the grid and the component sheet |
| `world_*_concept.png` | in-world board concepts, a player 3 blocks from the table |
| `render_v2.py` | renders all of the above from the real textures (`python3 docs/design/visual/v2/render_v2.py`) |

Capture of the current state: `java/src/gametest/java/dev/nezo/burmaldaholic/gametest/design/RedesignCaptureClientGameTests.java`
(opt-in, asserts nothing). Run with `BURMALDAHOLIC_CAPTURE=1` (screens at 1920 × 1080 and 2560 × 1440, GUI 4, plus the
world) or `BURMALDAHOLIC_CAPTURE=world`, e.g.
`BURMALDAHOLIC_CAPTURE=1 flock … xvfb-run -a -s "-screen 0 2560x1440x24" ./gradlew --no-daemon runClientGameTest`.
Shots land in `java/build/run/clientGameTest/screenshots/cap_*`. Use it again after the redesign for the before/after.

---

## 1. Critique of the current build (candid)

Screens (see `before/screens_*.jpg`):

1. **Nothing uses the screen.** Every screen is a fixed canvas (427 × 240 card tables, 400 × 240 extras) centred on
   the GUI. At 480 × 270 the card tables get dark letterbox bars; at 640 × 360 the panel covers ~60 % of the width and
   everything is small. Extras panels float over the world with the HUD sidebar and chat bleeding out from under them.
2. **Five visual languages.** Purple wallpaper + wood rail (cards), brick tavern + bunting (roulette / craps / dice),
   neon arcade, red carnival, teal kiosk (extras), gunmetal arena (PvP). Each has its own buttons, chip rack and
   balance widget. It reads as five mods, not one casino.
3. **Betting is inconsistent and undiscoverable.** Cards: chips with **no values on them**, then click a felt circle
   that is not highlighted; *Deal* looks disabled with no reason given (matches the "can't place a bet" reports).
   Roulette / craps: chip rack + click the layout. Extras: `Bet − / Bet + / Max` buttons and a tiny `● 1`. UTH:
   chips + a separate `Table settings…` button where the primary action should be; `Bet 4x` floats above the dock.
4. **No clear primary action.** Primary is sometimes a 48 px round chip half off the felt (roulette / craps), sometimes
   one of four equal buttons, sometimes a disabled-looking button. Poker has an unlabelled icon-only raise button.
5. **Numbers are hard to read.** Bets on the felt use half-size digits on top of the chips; hand totals float away
   from their cards (dealer "8" 80 px left of the cards); the balance is a small pill; the extras bet is `● 1`.
6. **Collisions everywhere.** Seat plates cover cards (blackjack, baccarat), plates overlap each other, names are cut
   (`Lady Luckl…`, `Redstone …`), status text overflows plates (`Watching`), `You squeeze…` is drawn over the cards,
   `B. PAIR 11:1` is clipped by the rail, the UTH hole cards are cut by the dock, an empty black box (bead road with
   no data) sits mid-table in baccarat, the craps status line runs over the Undo / Clear buttons, tooltips stick.
7. **Results are whispers.** A blackjack loss is a red `Lost 50` in the dock; no stamp on the hand, no banner, the
   balance changes silently. The roulette spin overlay hides every bet while the ball runs.
8. What works and stays: the card faces, felt textures, roulette wheel art, the dice, the Wheel of Fortune art, the
   slot screen's discipline (one frame, big readable values, one big primary button, everything aligned to a grid).

In the world (see `before/world.jpg`, 3–4 blocks away, a round running):

1. **Blackjack and baccarat show nothing** — no cards, no chips (no table renderer); a green-topped block.
2. Poker / UTH cards are 2–3 screen px each from 3 blocks; the table is a 1 × 1 block seen at a ~10° grazing angle,
   so anything lying flat is unreadable.
3. Bot name tags float 1.5 blocks up, are huge when close, and **overlap into garbage** when bots sit side by side.
4. Craps: one die bigger than the layout and a lone number; roulette: the wheel reads, bets and ball do not; Plinko:
   a few dots on a block face. The **Wheel of Fortune is the one that works**: big, upright, facing the room.

---

## 2. Direction

**"The screen is the table."** One frame for every game: the felt fills the whole GUI edge to edge, a slim top bar
for identity and money, a dock at the bottom for everything you press. You always sit at the bottom centre. Every
number you care about is at least 1× vanilla font on a solid backing, the ones that matter most are 2×. One gold
primary button, always in the same place, always saying what it does and why it is disabled. The same BetBar in
every game. And the table in the world becomes a **readable board** — upright, labelled, legible from 8 blocks —
following the Wheel of Fortune, the one in-world object players already like.

---

## 3. Layout system

### 3.1 Tiers (by GUI height `H`; `W` follows the window)

| Tier | When | Typical | Hero card | Seat card | Tray chip | Felt chip | Top bar | Dock | Margin |
|---|---|---|---|---|---|---|---|---|---|
| T1 compact | `H < 255` | 427 × 240, 320 × 240 | M 21 × 29 | S 13 × 17 | 20 | 10 | 18 | 36 | 4 |
| **T2 standard** | `255 ≤ H < 330` | **480 × 270** (1080p @4), 455 × 256 | **L 37 × 49** | **M 21 × 29** | **24** | **12** | **22** | **44** | **6** |
| **T3 large** | `H ≥ 330` | **640 × 360** (1440p @4, 1080p @3) | **XL 49 × 65** | **L 37 × 49** | **32** | **16** | **26** | **56** | **8** |

For `H ≥ 540` (4K at scale 4 = 960 × 540): pick the tier for `H / k` with `k = floor(H / 270)` and draw the whole
screen with `pose().scale(k)` (integer only, same as today's `FitScaled`, mouse mapped by the existing mixin).
Never scale pixel art by a non-integer factor.

### 3.2 Regions (all GUI px, absolute)

```
y 0 .. TB              TopBar     full width
y TB .. H-DB           PlayArea   full width, felt full bleed (no letterbox, no centred panel)
y H-DB .. H            Dock       full width
```

| Region | T2 @ 480 × 270 | T3 @ 640 × 360 | Formula |
|---|---|---|---|
| TopBar | 0, 0, 480 × 22 | 0, 0, 640 × 26 | `(0, 0, W, TB)` |
| PlayArea | 0, 22, 480 × 204 | 0, 26, 640 × 278 | `(0, TB, W, H − TB − DB)` = `(0, py0, W, PH)` |
| Dock | 0, 226, 480 × 44 | 0, 304, 640 × 56 | `(0, H − DB, W, DB)` |
| Primary button | 382, 233, 92 × 30 | 500, 314, 132 × 36 | `(W − m − PW, H − DB + (DB − BH)/2, PW, BH)`, PW = 92 / 132, BH = 30 / 36 |
| BetBar (chips) | 8, 236 → 5 × (24 + 2) | 10, 316 → 5 × (32 + 4) | `x = m + 2`, `y = H − DB + (DB − chip)/2` |
| Bet tools | after chips + 6: 4 × 20², pitch 22 | 4 × 24², pitch 26 | Undo, Clear, Rebet, ×2 — always this order |
| Bet readout | after tools + 8 | same | label `Bet` 1× muted at `H − DB + 8`, value 2× gold at `H − DB + 19` |
| Status | after readout + 52, until the primary − 6 | same | two lines (1× gold / 1× muted), clipped with "…" |

A 12-column grid (`margin m`, gutter 4, column = `(W − 2m − 44) / 12`) is used inside the PlayArea for anything that
is not a seat (see `v2_grid_480.png`, `v2_grid_640.png`). Seats are placed by fractions of `W` and `PH`
(§7) so the table grows with the screen instead of floating in it.

### 3.3 Fixed rules

- Nothing is ever drawn under the Dock or TopBar except the felt vignette; cards stop at `H − DB − 4`.
- Plates never overlap cards: a seat column is, top to bottom, *total pill → cards → bet stack + value pill → plate*.
- Status text, labels and names are measured (`font.width`) and ellipsised; nothing wraps into a neighbour.
- No text below 1× vanilla font, anywhere (the half-size digits on chips go away).
- The empty state is designed: open seats show a dashed spot + `Open` in felt print; empty history shows nothing,
  never an empty black box.
- Tooltips only on hover of a felt spot, never sticky after the click; the hint goes to the status line instead.

---

## 4. Components (Java, `client/table/v2/`)

> **Amended by `docs/architecture/ui-v2.md` §2, §4** (binding): the kit lives in `client/ui/v2/` (pure layout in
> `core/ui/v2/`); stamps show the word only, in the seat's outcome slot above the cards, and the net goes into the value
> pill beside the stack; `RoundBanner` is `ResultBanner`, placed only in a free region; no "Open" empty seat (the printed
> betting box, `Sit` on hover); the value pill is beside the stack, never above it.

| Class | Replaces | Does |
|---|---|---|
| `TableLayout` | `TableChrome`, `CardTableScreen` canvas math, `FitScaled` for tables | tier + regions of §3 from `(W, H)`; `seatAnchor(i, n, shape)`; `k` for big GUIs |
| `TableFrame` | the 5 backdrops / frames | draws felt (tiled `felt_<theme>`) full bleed + vignette, dealer rail, TopBar, Dock |
| `TopBar` | title plaques, `CasinoHud` copy, icon buttons | title pill (gold), limits pill, seats pill; right: Rules `?`, Settings, Leave icons (14², pitch 16), balance pill with chip icon, delta pill (+/− for 2 s) |
| `BetBar` | `ChipButton` rows, `BetSelector`, `BetControl`, `Bet −/+/Max` | chip tray (5 denominations filtered by table limits; selected chip lifts 3 px + gold ring + underline), tools Undo / Clear / Rebet / ×2, bet readout. **Single-spot games (blackjack, coin, dice, wheel, Plinko, scratch): clicking a chip adds it to your bet directly.** Multi-spot games: the chip arms, the felt spots light up, the status says "Click the layout". Right-click a chip = remove one of it. Keys 1–5 select chips. |
| `PrimaryButton` | round spin/roll chips, `TableButton` primary | gold 92 × 30 / 132 × 36, label + key hint plate (`Space`). Disabled = dim + the status line says why ("Place a bet first", "Waiting for Alex") |
| `ActionRow` | blackjack / poker / UTH buttons | equal 70 × 30 (T2) / 84 × 36 (T3) buttons right-aligned in the Dock, each with its key (H S D P, F C R, …); danger style for Fold / Surrender |
| `SeatPlate` | `SeatPlate`, `PlateRow` | 74 × 18 (T2) / 80 × 18 (T3): avatar 14², one line: the name (ellipsised). Edge: cyan = you, gold = acting, purple = others. **No numbers in plates.** |
| `TotalPill` | `badge/total` | dark pill, 1× text for others, 2× for you and the dealer; sits above (others) or left (hero) of the cards |
| `FeltStack` | `ChipStackView` | ≤ 4 stacked discs (12 / 16 px) + **value pill above-right** (cyan for yours, panel for others) |
| `Stamp` | `TableStamp` | WIN +x (green), LOSE / BUST (red), PUSH (slate), BLACKJACK / NATURAL (gold); 1× on seats, 2× for your hand |
| `RoundBanner` | celebration toasts on tables | 232 × 28 (T2) / 300 × 36 (T3) between dealer and you: `YOU WIN` 2× gold left, `+125` 2× green right (or `LOST 50` red); 1.5 s, then it folds into the status line; never covers your cards |
| `TimerBar` | `TableClock` text | 3 px bar under the status (gold → red under 5 s) |

Keyboard everywhere: `Space` = primary, `1–5` chips, `Backspace` undo, `R` rebet, `Esc` leave; game keys on the
ActionRow buttons.

---

## 5. Colour tokens (extend `client/fx/CasinoPalette.java`)

| Token | Hex | Use |
|---|---|---|
| `ink` | `#180A28` | outlines, Dock / TopBar fill |
| `panel` / `panel.hi` | `#22122F` / `#341E48` | pills, plates, banner |
| `edge` | `#783CBE` | other players' plate edge, normal button border |
| `text` / `muted` | `#F4ECF8` / `#C0B0DC` | body / captions |
| `gold` / `gold.shade` | `#FFD640` / `#B07010` | primary button, money values, acting seat, rail trim |
| `win` / `lose` / `push` | `#80FF40` / `#FF6E6A` / `#8FA8C8` | outcome text, stamps (stamp fills `#2E8C28`, `#B0242E`, `#4A5A78`) |
| `you` | `#5CE8E0` | your plate edge, your felt value pills, your name |
| `felt` / `felt.print` | `#1E5E3A` / `#E8D9A0` (55–80 % alpha) | felt base / printed lines and words |
| `rail` / `rail.dark` / `rail.hi` | `#5A3418` / `#3A2010` / `#8A5628` | rails |
| `btn` / `btn.hi` | `#4E287C` / `#7046A4` | normal buttons (lighter than today so they read on the dark Dock) |

Themes (village / bastion / end) only swap `felt`, `rail*` and `felt.print`; UI tokens never change per theme.

## 6. Typography

Vanilla font only (RU-safe). Caption 1× `muted`; body 1× `text`; numbers on the felt 1× on a pill; hero numbers 2×
(your bet, your total, dealer total, pot, balance delta, payout); display 2× (T2) / 3× (T3) for `YOU WIN`,
`BLACKJACK`. Shadow on everything except text on gold. Line height 11 (1×), 20 (2×). RU strings must fit the same
boxes; plates and status lines ellipsise, buttons are sized to the longest of EN/RU (`STRINGS.md`).

---

## 7. Per-game layouts (T2 numbers; T3 by the same fractions)

> **Amended by `docs/architecture/ui-v2.md` §2** (stamps, roulette value tags only for the hovered spot / the last bet,
> no "Open" seats, hero anchor 0.86).

### 7.1 Blackjack — `v2_blackjack_betting_480.png`, `v2_blackjack_turn_480.png` / `_640`, `v2_blackjack_result_480.png`

- Dealer rail: `(0, py0, W, 8)`; chip rack centred on it; shoe at `(W − m − 52, py0 + 12)`; discard tray `(m + 10, py0 + 12)`.
- Dealer cards (hero size) centred at `x = W/2`, top `py0 + 14`, step `cw/2 + 2`; dealer TotalPill 2× left of the fan
  (`fanLeft − 6`, right-aligned, `top + 6`); `Dealer stands on 17` 1× muted right of the fan.
- Felt prints centred at `py0 + 14 + ch + 10` and `+ 11`.
- Seat anchors (bottom-centre of the seat column), 5 seats, you = index 2 always (rotate the server seat order):
  fractions `(0.10, 0.56) (0.27, 0.74) (0.50, 0.80) (0.73, 0.74) (0.90, 0.56)` of `(W, PH)` from `(0, py0)`.
  T2: (48,136) (129,172) (240,185) (350,172) (432,136). T3: (64,181) (172,231) (320,248) (467,231) (576,181).
- Seat column: cards bottom at `anchor.y − 18`, FeltStack centred at `(anchor.x − 6, anchor.y − 9)`, plate top at
  `anchor.y + 6` (`+ 10` for you). You use hero cards with a gold glow when acting; others seat cards.
- Splits: hands side by side, 4 px apart, the acting hand glows; the Dock status says `hand 2 of 2`.
- Betting: your spot pulses (gold ring 36 px + outer ring), `Your bet` pill above it; chips go straight to your bet.
  Dock: BetBar · `Bet 50` · status `Chips add to your bet / Deal in 12 s` · primary **Deal** (disabled until a bet,
  status then says `Place a bet first`).
- Decision: Dock left = `Your turn` + `Bet 50 – hand 1 of 1` + TimerBar; ActionRow Hit H · Stand S · Double D ·
  Split P (· Surrender / Insurance appear in the same row when legal; never more than 5).
- Result: Stamps on every hand; RoundBanner under the dealer cards; TopBar delta pill; primary becomes **Rebet 50**,
  secondary **Leave**.

### 7.2 Baccarat / Chemin de fer

Same frame. Upper half: two boxes PLAYER (left, blue edge) and BANKER (right, red edge), each `0.42 W × 0.36 PH`,
hero cards, TotalPill 2× in the box header. Lower half: bet boxes row P.PAIR · PLAYER · TIE · BANKER · B.PAIR,
widths `0.14 / 0.22 / 0.14 / 0.22 / 0.14 W` with the payout printed under the name **inside** the box (no clipping).
Bead road / big road: only in the Rules overlay or as a 6-row strip on the right of the box row when it has data.
Seats: plates along the bottom rail (5 slots at `x = 0.1 … 0.9 W`, `y = H − DB − 22`). Squeeze text goes in the
status line, never on the cards.

### 7.3 Texas Hold'em — `v2_poker_turn_480.png` / `_640`

- Oval felt centred at `(W/2, py0 + PH/2 − 4)`, radii `0.43 W × 0.40 PH`, rail 8 px; outside the oval is the dimmed room.
- Board: 5 hero cards, gap 4, centred, top `cy − ch/2 − 8`; empty slots outlined in felt print. Pot under the board:
  `Pot` 1× muted + value 2× gold.
- You: hero cards bottom-centre, bottom at `H − DB − 4`; your plate to their left at `y = H − DB − 22`, your stack
  pill above it; hand-strength pill (`Two pair`) right of your cards; your bet right of the cards.
- Others (6-max): plates at the oval fractions `(0.10,0.72) (0.10,0.18) (0.50,−0.05) (0.90,0.18) (0.90,0.72)` with
  the stack pill right of the plate (`Fold` in muted instead of a number); their 2 seat-size backs inside the rail;
  their bets 38 % of the way from the seat to the centre. Dealer button 12² next to the dealer seat.
- Dock: status `Your move – to call 10` + TimerBar; ActionRow **Fold F** (danger) · **Check/Call C** · raise stepper
  (`−  40  +`, 66 wide, scroll wheel works) · primary **Raise to 40 R**. Nothing icon-only.

### 7.4 Ultimate Texas Hold'em

Crescent frame of blackjack; dealer cards top, board 5 hero cards centred at `py0 + 0.42 PH`, your 2 hero cards
bottom-centre (bottom `H − DB − 4`), your bet circles Trips · Ante · Blind · Play as a row right of your cards
(FeltStack each, labels in felt print above). Paytable is the `?` overlay, not a felt panel. Dock: betting = BetBar +
primary **Deal**; decisions = ActionRow (`Check` · `Bet 3×` · primary `Bet 4×` pre-flop; `Check` · primary `Bet 2×`
on the flop; `Fold` · primary `Bet 1×` on the river). `Table settings…` moves to the TopBar settings icon.

### 7.5 Roulette — `v2_roulette_betting_480.png` / `_640`, `v2_roulette_spin_480.png`

- Left column width `0.25 W` (T3 `0.26 W`): wheel (radius `col/2 − 8`) at `(m + col/2, py0 + 10 + r + 4)`; under it
  `Last numbers` 2 × 4 pills 23 × 13 (newest first, gold tick).
- Layout from `lx = m + col + 6`: cell width `min(23 | 30, (W − m − lx − 4) / 14)`, cell height
  `min(1.8 × cw, (PH − 10 − 2·oh − 34) / 3)` with `oh = 17 | 22`. T2: 23 × 41, T3: 30 × 54. Zero column, 12 × 3
  numbers (red / black tiles with 1× numbers), 2:1 column, dozens row, outside row; call bets
  (Tiers / Orphelins / Voisins / Zero) as a 4-button row under the layout (the racetrack becomes the Rules overlay).
- Bets: FeltStack on the cell / line / corner, value pill above-right (never overlapping the number it sits on more
  than the stack itself).
- Spin: **the layout stays** (dimmed 25 %), the wheel spins in its column, the winning cell gets a gold outline and
  your winning stacks glow; Dock shows `No more bets`, `Your bets 55` 2×, primary disabled `Spinning`. The
  full-screen wheel overlay is removed.

### 7.6 Craps — `v2_craps_point_480.png`

Felt layout fills the PlayArea minus `m + 4`: place row (`DON'T COME` box `0.17 w`, six numbers 2× / 3×, the ON puck
in the point box, gold outline), COME row with the dice (18² / 22²), the total pill 2× and `point` caption, FIELD row
(2× word, numbers 1× (2× on T3), 2 and 12 gold with `x2` / `x3`), DON'T PASS BAR, PASS LINE (gold edge), odds stack
next to the pass bet with an `odds` caption. Dock: BetBar · `On table 140` · status `Point is 8 / Roll in 18 s` ·
primary **Roll**.

### 7.7 Extras — Plinko `v2_plinko_480.png` (the pattern for Wheel, Scratch, Coin Flip, Dice Duel)

Extras use the same TopBar + Dock + BetBar; their PlayArea is their own scene (the backdrop art becomes the PlayArea
fill, no frame/marquee, no panel over the world). Layout: the game object centred in columns 1–9 (Plinko board
272 wide, bins 18 px tall with 1× multipliers colour-coded: gold top, green > 1, cyan = 1, red < 1); columns 10–12 =
options (Risk as three 20 px buttons, the selected one gold), `Last balls` pills, `Top prize` 2×. Wheel: wheel left
(cols 1–7), segment legend right with odds; Scratch: ticket centred, prize table right; Coin Flip: coin centred,
Heads / Tails as two big toggle buttons in the Dock's left in place of the tools; Dice Duel: arena full width,
plates above each lane. Stake toggle (Chips / Items) is a TopBar pill, not a button row.

### 7.8 PvP invite and duel

Invite: a centred 232 × 96 card on a scrim (not a floating box over the world): challenger head 32², `Alex challenges
you`, the game + stake 2× gold, TimerBar, **Accept** (primary) and **Decline** (normal) with keys `Y` / `N`. Duel
screens use the frame with `PvP` title pill; the two plates face each other across the PlayArea top (cyan you, red
rival), the pot 2× in the middle; Dock: Taunt (normal) · primary context action (Ready / Rematch).

---

## 8. Results and payouts (all games)

1. On settle, each hand / bet gets its Stamp (1× seats, 2× yours) — the stamp is the outcome, not the dock text.
2. Chips animate: wins fly from the rack to the spot (existing timelines), losses slide to the rack.
3. RoundBanner for 1.5 s with the net result (`YOU WIN +125` / `LOST 50` / `PUSH`), placed in the gap between dealer
   and you (cards / board never covered). Big wins (≥ 10× bet) hand over to the existing `CelebrationOverlay` tiers.
4. TopBar balance ticks up/down over 0.6 s with a delta pill for 2 s.
5. The status line keeps the summary (`Blackjack! +75`) until the next round starts; primary becomes `Rebet <n>`.

---

## 9. In-world board (block entity renderers)

> **Amended by `docs/architecture/ui-v2.md` §2.4, §7** (plates at the rail edge, laid out in the camera plane,
> collision-free; yaw-only board; text scale grows with distance up to 8 blocks).

The table stays a 1 × 1 block for collision and placement; its renderer draws a **board** around it.
See `world_blackjack_concept.png`, `world_roulette_concept.png`.

### 9.1 Geometry

| Part | Size / place | Notes |
|---|---|---|
| Felt top | 2.25 × 1.25 blocks (crescent tables), 2.0 × 1.4 (roulette / craps), 2.0 × 2.0 (poker oval), y = 1.0 | render-only overhang centred on the block, along `FACING`; `getRenderBoundingBox` grown to 3 × 2 × 3. The casino worldgen keeps one free block on both long sides; a player-placed table whose overhang hits a solid block falls back to the 1 × 1 top (checked on neighbour update, cached in the BE). |
| Rail | 0.08 block lip around the felt, rail texture | |
| Cards | **1.5 × real** (0.22 × 0.30 block), lifted 1/32, tilted 20° towards the seat they belong to (dealer cards towards the room) | 16 × 22 texel faces from the existing W atlas, drawn at 2× sampling (new `faces_w2.png`, 32 × 44 per card) |
| Chips | stacks of discs (0.14 block Ø, 0.025 high), ≤ 6 discs per stack, one stack per denomination step | top quad + 8-quad band |
| Dice | 0.12 block cubes (craps: two; Dice Duel unchanged) | today's die is ~0.4 block: shrink |
| Roulette | bowl 1.0 block Ø on the left of the felt, head rotating, **ball visible** (0.03 block sphere-billboard) running and resting in the pocket; the layout printed on the right | |
| Wheel of Fortune / Plinko | keep the Wheel; Plinko gets an upright 1 × 1.5 board above the block (like the wheel) with pegs, the ball and the bins' multipliers as 1/80-block text | |

### 9.2 Holo text (the readable part)

- **Table board** (one per table): billboard, centre 1.35 blocks above the felt centre, ≤ 1.6 blocks wide, dark 60 %
  backing, full-bright, depth-tested. Lines: title + limits (gold) · state (`Dealer 10`, `26 BLACK`, `Point 8`,
  `Pot 120`) · whose turn + seconds (muted) · on settle the result line (`Steve +350` green) for 3 s.
- **Seat plates**: one per occupied seat, billboard at the seat's rail edge, 0.3 block above the felt (y ≈ 1.30),
  two lines: name (cyan for the viewer, white others) and `bet · total/outcome` (gold / green / red). Replaces the
  current 2-block-high `[BOT] …` tags at tables (bots keep their tag only away from tables).
- Scale: **1 text px = 1/80 block** (8 px glyph = 0.1 block) for the table board, **1/100** for seat plates. At 4
  blocks a 0.1-block glyph is ~19 screen px at 1080p (FOV 70), still ≥ 10 px at 8 blocks — the readability floor.
- Overlap resolver: each frame project the plates of a table, sort nearest first, move a plate that overlaps one
  already placed up by the overlap + 2 px (screen space, ≤ 12 plates, O(n²) is fine). The concept renders use it.
- Distance LOD: ≤ 8 blocks everything; 8–16 blocks the table board (state + result lines only) and cards / chips;
  16–32 blocks felt, cards as backs/faces, no text; > 32 blocks the block model only.

### 9.3 What each table shows

| Table | Felt | Board lines | Seat plates |
|---|---|---|---|
| Blackjack | dealer cards (hole card face-down until revealed), each seat's hand + stack, shoe | `Dealer 10` / turn + timer / result | name · `50 · 15` → `50 · BUST` / `+75 BLACKJACK` |
| Baccarat / chemmy | Player and Banker hands, bet stacks on P / B / T | `Player 4 · Banker 7` / `BANKER WINS 7:4` | name · bet + box |
| Hold'em | board, pot stack, backs for each seat, folded seats without cards, dealer button | `Pot 120 · Turn` / acting player / winner + hand | name · stack · last action |
| UTH | dealer + board, viewer bet circles | street / result | name · ante · outcome |
| Roulette | wheel, ball, layout with all bet stacks (coloured by player) | `No more bets` / `26 BLACK` / top win | name · total on table · net |
| Craps | puck ON/OFF, dice, pass / field / odds stacks | `Point 8` / last roll `3 + 5 = 8` | name · on table · net |
| Plinko / Wheel | upright boards | last result multiplier | the player at the machine only |

### 9.4 Dealer actions

The dealer NPC (existing `DealerRenderer` gestures) cues on the same shared timeline: deal = arm sweep + card flight
from the shoe; reveal = flip; pay = chip slide from the rack; collect = sweep to the rack. The table board mirrors the
cue as text (`Dealer draws`), so the action reads even without the NPC in view.

### 9.5 Performance budget (per table, per frame)

≤ 32 card quads, ≤ 96 chip quads (12 stacks), ≤ 14 text lines (2 board + up to 6 plates × 2), 1 wheel head + 1 ball.
Text: `FormattedCharSequence` built only when the synced state changes, never per frame. All tables within 16 blocks
animate; beyond that the BER draws the settled state. Target ≤ 0.15 ms per table on the render thread; 20 tables in
view ≤ 3 ms. Sync: the existing PUBLIC tag only (no new packets except seat names already sent); hole cards stay hidden.

---

## 10. Sprites to generate (`tools/assets`, never hand-edited)

> **Amended by `docs/architecture/ui-v2.md` §9 (D11)**: kit sprites are generated by `tools/assets/modules/ui2.mjs` into
> `textures/gui/sprites/core/v2/` (ids `burmaldaholic:core/v2/<name>`), not `tables/v2/`.

New module `tools/assets/modules/tablesv2/` (ids `burmaldaholic:tables/v2/<name>`), all three themes where marked θ:

| Sprite | Size | Notes |
|---|---|---|
| `chip_<d>` d ∈ 1,5,25,100,500,1k,5k | 24² and `chip_<d>_l` 32² | value printed in the centre in 1× font (digits only, language-neutral), 8 edge stripes, dark inner ring |
| `chip_selected_ring` | 28², 36² | gold ring + underline |
| `felt_disc_<d>` | 12², 16² | top-down discs for FeltStack (replace `stack/chip_*` 12 × 11) |
| `value_pill`, `value_pill_you` | nine-slice 12 × 11 | dark / cyan pill for felt values |
| `plate` θ-less | nine-slice 24 × 18 (edge variants you / acting / other) | replaces `seat_*_θ` and `cards/seat/plate_*` |
| `stamp_win`, `stamp_lose`, `stamp_push`, `stamp_gold` | nine-slice 24 × 16 | |
| `button_primary` (+hover, pressed, disabled) | nine-slice 32 × 30 | gold; `button` (+states) purple 32 × 30; `button_danger` (+states) |
| `key_hint` | nine-slice 10 × 10 | the `Space`, `H` plates |
| `icon_button` (+states) | 20² and 24² | tools; glyphs `icon/undo, clear, rebet, double, rules, settings, leave` at 12² and 16² |
| `topbar`, `dock` | nine-slice 32 × 22 / 32 × 44 | ink fill, gold.shade + rail.dark trim |
| `banner` | nine-slice 48 × 28 | RoundBanner, gold edge |
| `felt_vignette` | 64² nine-slice | the felt edge darkening |
| `rail_arc_θ` | generated per tier: T2 520 × 210, T3 690 × 285 | the padded crescent rail (transparent centre) |
| `rail_oval_θ` | T2 430 × 175, T3 570 × 235 | poker oval rail |
| `faces_xl.png`, `faces_xl_classic.png` | 13 × 49 by 4 × 65 (637 × 260) | the XL card atlas for T3 (drawn natively, not upscaled) |
| `backs_xl` | 6 × (49 × 65) | |
| `roulette/wheel_bowl_m_θ`, `wheel_head_m` | 104 × 104 (+ head frames) | T2 wheel column; T3 uses 144 × 144 |
| `roulette/history_pill_{red,black,green}` | 23 × 13 | |
| `faces_w2.png` | 32 × 44 per card | in-world 2× card faces |
| `entity/core/holo_backing.png` | 4² | in-world text backing |

Deprecated after the switch (delete with their generators): `gui/tables/core/backdrop_θ`, `gui/core/cards/backdrop_θ`,
`gui/core/cards/table_*_compact`, `tables/core/spin_button*`, `roll_button*`, `chip_well_θ`, the extras frames /
marquees / banners (`*_frame`, `*_marquee`, `*_banner`) once their screens use `TableFrame`.

## 11. Implementation order and tests

1. `TableLayout` + `TableFrame` + `TopBar` + `Dock` + `BetBar` + `PrimaryButton` (no game changes yet) with a kit
   gallery screen (`gametest/core/KitGalleryScreen` style).
2. Blackjack (it also carries the bet-flow fix), then roulette and craps (same BetBar), baccarat, UTH, poker.
3. Extras and PvP.
4. In-world: blackjack + baccarat renderers (none today), then the board/plates for all, then LOD.

Every step ships with a **real-flow client GameTest** (v0.1.2 rule): open the table by using the block, click the chip
widget and the felt spot / the primary button by position at 480 × 270 and 640 × 360 (resize the window, GUI 4),
assert the server state (bet placed, round dealt) and the next screen state; and screenshot it with the capture test
for the before/after. Layout tests (unit, no MC): for both tiers, no widget outside the screen, no two widgets
overlapping, every label ≤ its box in EN and RU.
