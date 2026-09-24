# Visual redesign: European Roulette, Craps, Dice Duel (Java)

Status: art + spec ready, 2026-09-24. **Java only**: Bedrock support was dropped, so this spec has no
Bedrock mapping, glyph plane, Bedrock texture or entity. The motion (timelines, ball path, throw path,
sounds, tiers) stays in [`docs/design/animation/tables.md`](../animation/tables.md), which this spec
builds on. Where the look changed the choreography, that doc was edited too (§9).

The quality bar is the slots v2 redesign (`modules/slots.mjs`, animation/slots.md §3). Each screen gets:
- a themed room backdrop;
- a framed play area with a padded rail and a gold inlay;
- crisp pixel art with win and idle frames;
- casino buttons;
- the shared tier celebrations (global.md §2.6).

Today's screens are plain green panels with vanilla grey buttons and flat coloured boxes (client gametest
screenshots `0012_jtest_ru_roulette`, `0013_jtest_ru_craps`, `0029_jtest_ru_dice`).

- Art generator: `tools/assets/modules/tables.mjs` + `modules/tables/*.mjs`. It emits Java outputs
  only.
- Regenerate with `cd tools && node assets/gen-assets.mjs --module tables`, and check with
  `npm run check:assets` (in `tools/`).
- Mockups: `docs/design/visual/mockups/tables_*.png`, composed from the real PNGs by
  `docs/design/visual/render_tables.py` (Pillow). §8 lists them.

Contents: §1 rules · §2 themes and the shared kit · §3 roulette · §4 craps · §5 dice duel ·
§6 screen layouts (GUI 427 × 240 and compact) · §7 in-world (BER) and particles · §8 file inventory and
mockups · §9 changes to the animation spec · §10 lang keys · §11 developer notes.

---

## 1. Rules

1. **No words in textures.** Digits, `:` and `-` are allowed; they are language-neutral, as with the slots
   ladder and wheel plates. These are baked as digits:
   - roulette: the layout numbers, `2:1`, `1-12` / `13-24` / `25-36`, `1-18` / `19-36`, and the wheel and
     racetrack numbers;
   - craps: the place and field numbers and `2:1` / `3:1`;
   - chips: the denomination values.

   Everything else is runtime text in the rects §3–§5 list: EVEN, ODD, COME, FIELD, PASS LINE, the racetrack
   sections, ON/OFF on the puck, and every name and amount.
2. **Pixel density.** The UI chrome (rails, plates, layouts) uses 1 GUI px detail, like vanilla widgets.
   Hero objects are **16-px art drawn ×2** in 40 px cells, with a 1 px ink outline and a soft shadow, like the
   slots symbols; the Dice Duel dice and cup follow this rule. The wheel is rendered per frame at 1 GUI px.
   **Nothing is scaled by a fraction at runtime.** Choreography that used 0.62 or 0.5 scales now uses
   dedicated sizes (§9).
3. **Light comes from the top-left** everywhere, in screen space. The wheel frames re-light each rotation, so
   the specular never spins.
4. **Palette.** The brand tokens come from `docs/branding/branding.md` (`lib/palette.mjs`):
   - ink `#180A28`
   - gold `#FFD640` / `#B07010`
   - chip red `#D83440` / `#FF6E6A` / `#8C1834`
   - bone `#F4ECF8`
   - felt `#1E5E3A`
   - frame / glint `#783CBE` / `#BE5AFF`

   The pocket colours are fixed across themes: red `#D83440`, black `#26202C`, green `#2E9A48`, each with a
   light / shade / deep ramp in `tables/theme.mjs`. Seat tints follow animation/tables.md §0.4.
5. **Accessibility.** Colour is never the only cue:
   - the history pills carry a shape (red = disc, black = ring, zero = diamond);
   - the outside RED / BLACK boxes are diamonds;
   - the natural / craps / point badges differ in ring colour **and** in the event text;
   - reduced motion uses frame 0 of every loop.

## 2. Themes and the shared kit

### 2.1 Location themes

| | Village casino (`village`) | Piglin Parlor (`bastion`) | High Roller Lounge (`end`) |
|---|---|---|---|
| Room backdrop | dark oak planks, beams, green and gold bunting, lanterns, daylight windows | polished blackstone bricks with gilded flecks, gold-block pillars, lava falls, piglin banners, heat glow | panoramic void window with an aurora, end islands and a far dragon; purpur pillars, end-rod lamps, chorus plants |
| Felt | green `#1E5E3A` | crimson `#5A1418` | royal violet `#2A1A4C` |
| Layout lines | chalk cream `#EDE2C4` | gold `#FFD640` | pearl `#F4ECF8` |
| Rail | padded brown leather, gold inlay, brass studs | polished blackstone, gold inlay, ember studs | obsidian, platinum inlay `#E8F4FF`, pearl studs |
| Wheel bowl | walnut rim, maple track | blackstone rim, gold track | purpur rim, end-stone track |
| Arena (duel) | cobblestone rim, torches | blackstone rim, soul torches | end-stone rim, end rods |

The theme follows the casino the table stands in: worldgen's `village_casino`, `piglin_parlor` and
`high_roller_lounge`. A player-placed table uses `village`, except the `roulette_table_high_roller`
variant, which uses `end`. The theme is resolved client-side from the block entity (`theme` byte in the
update tag, set by worldgen; default by block). No mechanics depend on it.

### 2.2 Shared kit (`textures/gui/sprites/tables/core/`, ids `burmaldaholic:tables/core/<name>`)

| Sprite | Size | Use |
|---|---|---|
| `rail_<theme>` | 48² nine-slice, border 14 | The play-area frame. The ink edge, cushion, inlay (rows 10–11) and an inner felt shadow (row 13) sit in the border; the centre is transparent. Draw the felt tile first at inset 10, then the rail. |
| `plate_<theme>`, `plate_gold_<theme>` | 24 × 16 nine-slice, border 5 | Title plaque and balance plaque (gold frame), labels such as `+1 750` over winning stacks |
| `seat_{other,you,active,bot}_<theme>` | 32 × 16 nine-slice, border 5 | **Seat plates.** The 11 px well on the left holds `seat_stripe` tinted by the seat colour, or `bot_badge`. `you` has a lilac frame; `active` (shooter, current duel player) has a gold frame with corner glints; `bot` has a steel frame. |
| `seat_stripe` | 9 × 10 | Grey chip; the screen multiplies it by the seat colour (§0.4) |
| `bot_badge` | 11 × 10 | Brass automaton head: bots are always marked (BOTS.md) |
| `button_<theme>[_hover,_pressed,_disabled]` | 200 × 20 nine-slice, border 4 | Themed text buttons (Accept, Decline, Place, Max) with a trim top line and a 2 px lip. Pressed drops 1 px and loses the lip. |
| `spin_button[...]`, `roll_button[...]` | 48 × 48 | **Big casino-chip buttons.** A red chip with a gold rim, six bone inserts, a gloss spot, and a wheel or two-dice icon. States: idle, hover (brighter, glint ring), pressed (1 px down), disabled (grey). They sit on the rail's lower-right corner (§6). |
| `icon_button_<theme>[...]` + `icon/<name>` | 20 × 20 + 14 × 14 | Icon buttons: `undo`, `clear`, `rebet`, `double`, `racetrack`, `rules`, `leave`. Tooltips carry the words. |
| `chip_<d>`, `chip_grey` | 24 × 24 | Tray chips (top view, edge inserts, dashed ring, value digits) |
| `chip_well_<theme>` | 26² | The recess under each tray chip |
| `chip_select` | 28², 4 frames (loop) | Gold ring with a travelling glint around the selected chip. The selected chip also rises 2 px. |
| `stack/chip_<d>`, `stack/chip_grey`, `stack/chip_invalid` | 12 × 11 | Chips on the felt (top ellipse + edge band). A stack draws each disc **3 px** higher, up to 5 discs. Other players use `chip_grey` × seat tint. `chip_invalid` is the red ghost for the §1.4 invalid shake. |
| `banner_<theme>`, `banner_win`, `banner_lose` | 48 × 24 nine-slice, border 10 | Result and phase banners with corner bulbs; the text is runtime, coloured by tier |
| `timer`, `timer_urgent` | 16 × 16 × 13 frames (code-picked) | Bet window ring: frame = segments used (12 → 0). Use `urgent` for the last 3 s. |
| `spark` | 7 × 7 × 4 frames (code-picked) | Deflector kick, dice wall hit |

Sheets (`textures/gui/tables/core/`): `backdrop_<theme>.png` 640 × 360 and `felt_<theme>.png` (a 64² tile).

## 3. European Roulette

### 3.1 The wheel: a spinnable sprite set (`gui/tables/roulette/`)

The wheel is **rendered per frame by the generator**, never rotated by the GPU. Each frame is exact pixel
art: pockets come from the true angle, frets are 1 px brass, the **pocket numbers are upright digits**
(readable at any angle) and the turret arms turn with the head.

| File | Size | Content |
|---|---|---|
| `wheel_head.png` | 1520 × 1216 = 74 frames of 152² in a 10-column grid | The rotating head: gold lip, **number ring** (37 coloured cells with upright digits), pockets with brass frets, mahogany cone, 4-arm turret with a fixed specular spot. Frame `f` = head rotated `f × 360/74°` clockwise; frame 0 has pocket 0 at the top. |
| `wheel_head_blur.png` | 152 × 608 (4 frames) | Tangential motion blur with no digits, used while the head spins faster than 180°/s. It cycles every 2 frames. |
| `wheel_bowl_<theme>.png` | 208² | Static bowl: ink edge, rim, inlay line, ball track (concave shading), 8 brass deflector diamonds at r = 84 alternating radial and tangential, and a shadow gap. The head goes at offset (28, 28). |
| `wheel_shadow.png` | 216² | Soft ink disc under the bowl in the spin view, at offset (−4, −4) from the bowl |
| `wheel_mini_<theme>.png` | 720 × 288 = 37 frames of 72² (10 columns) | **Idle mini wheel** (bowl + head + ball) for the betting view. Frame `k` has pocket `WHEEL_ORDER[k]` under the ball at the top, so it shows the last result. The wheel is static while idle (global §4.13). |
| `wheel_mini_head.png` + `wheel_mini_bowl_<theme>.png` | 37 × 56² + 72² | Compact spin view (§6.3): 37 pocket-step head frames, head at bowl offset (8, 8) |

Radii (big view, px from the centre):

| Element | Radius |
|---|---|
| lip | 73.5–76 |
| number ring | 63.5–73.5 (digits at r = 68.6) |
| pockets | 49.5–61.5; the ball rests at r = 55.5 |
| cone | < 47.5 |
| turret | 10.5, arms to 33 |
| bowl rim | 93–101 |
| track | 79–91.8 |
| **Rw** (the ball-path unit, animation §1.3) | **92** |

- So the ball orbits at 0.96 Rw ≈ 88 and settles at 0.62 Rw ≈ 57, the pocket-ring centre.
- The pixel positions (the `HEAD_BIG` / `BOWL_BIG` constants) are exported from `tables/wheel.mjs`.

Frame selection (Java `RouletteWheelWidget`):
- Head frame = `floor(mod(headAngle, 360) / (360/74) + 0.5) mod 74`.
- If the angular speed is above 180°/s, draw `wheel_head_blur` frame `(tick/2) mod 4` instead.
- The ball (`roulette/ball`, 7² × 3 frames: rest, flash, in-pocket) is drawn at `(cx + r·sin a, cy − r·cos a)`,
  the exact float position, rounded to the GUI px.
- Trail: 3 copies at the previous 7° steps, at alpha 60/40/20 %, while the ball moves faster than 360°/s.
- Pocket glow: `roulette/pocket_glow` (20², 4-frame loop) is centred on the result pocket at r = 55.5 while
  the ball is settled.
- With the head moving in 4.9° steps and the ball moving smoothly, the eye reads a continuous spin. At the
  final coast, the step is one half-pocket.

### 3.2 The felt layout (`layout_<theme>.png` 278 × 98, `layout_compact_<theme>.png` 194 × 67)

The layout is a transparent overlay drawn on the felt tile. The cell geometry is exported
(`LAYOUT_BIG` / `LAYOUT_COMPACT`, `numberRect(g, n)` in `tables/roulette.mjs`):

| | Big | Compact |
|---|---|---|
| Zero cell | x 0–17, the full grid height; a rounded green plate | x 0–11 |
| Number cell `n` | x = 18 + 20·col, y = 22·row, 20 × 22 (col = ⌊(n−1)/3⌋, row = 2 − (n−1) mod 3) | 14 × 15, starting at x 12 |
| Plates | Rounded red or black plates inset 2 px, light top edge, deep bottom edge, bold 5 × 7 digits with shadow | Inset 1, small 3 × 5 digits |
| Column bets `2:1` | x 258–277 | x 180–193 |
| Dozens | y 66–81, 3 × 80 wide | y 45–55, 3 × 56 |
| Outside | y 82–97, 6 × 40: `1-18`, [EVEN], red diamond, black diamond, [ODD], `19-36` | y 56–66, 6 × 28 |

- Split, street, corner and line hit areas are the shared edges and intersections of these rects (`Layout.hit`).
- The ghost chip sits exactly on the edge or intersection.
- Chips stack at the drop point: the chip centre sits 5 px below the point, so the stack rises from the line.

Highlights (`gui/sprites/tables/roulette/`):

| Sprite | Size | Use |
|---|---|---|
| `cell_hover` | 12² nine-slice, border 3 | Covered cells on hover: gold rim + 25 % white wash |
| `cell_neighbour` | 12² nine-slice, border 3 | Lilac rim for racetrack / call-bet coverage (neighbours of 17 lights 25-17-34-6 …) |
| `cell_win` | 12² nine-slice, border 4, 4-frame pulse, frametime 3 | The winning number and every covering outside box (dozen, column, colour, parity, half) |

### 3.3 Racetrack (`racetrack_<theme>.png`, 278 × 44)

- A stadium of 37 cells in wheel order: top row 24 … 26 (17 cells), right cap 0 / 32, bottom row 15 … 23
  (16 cells), left cap 10 / 5.
- The inner field is split into Tier | Orphelins | Voisins | Zero at x = 84, 150 and 222; the section names
  are runtime text.
- Cells come from `racetrackCells()`, so the screen hit-tests the same shapes the art draws.
- Clicking a number places a **neighbours** bet (n ± 2); hovering shows `cell_neighbour` on the 5 layout cells.
  Clicking a section places its call bet. The call bets are a NICE extension of GAME_DESIGN §9; each one is
  split into standard bets server-side, so the rules and payouts are unchanged.

### 3.4 Win marker, history, chips

- **Dolly**: `dolly` is 12 × 14, with 4 shine frames (frametime 4). It is a gold pawn on a purple felt foot.
  `dolly_shadow` is 12 × 4. The dolly stands **on top** of the chips of the winning number, offset −10 x /
  −12 y from the cell centre, so the stack stays readable. Drop: from 24 px above with `outBounce`; the shadow
  alpha goes 30 → 100 %.
- **History pills**: `pill_{red,black,green}` (22 × 11, with a shape marker on the left and the runtime number
  on the right) and `pill_*_new` (gold outline, the newest result). Two columns of 4 go under the mini wheel.
- **Payout labels**: `+N` on a `plate_<theme>` nine-slice (13 px tall), centred over the winning stack.

## 4. Craps

### 4.1 The felt (`gui/tables/craps/layout_<theme>.png` 386 × 162, `layout_compact_<theme>.png` 264 × 117)

The rects come from `crapsRects(g)` in `tables/craps.mjs`, image-local, big layout:

| Area | Rect (x, y, w, h) | Art | Runtime label |
|---|---|---|---|
| Back wall | 0, 0, 386, 6 | Pyramid rubber (`back_wall` 16 × 8 tile) with a trim top line | — |
| Don't Come | 0, 9, 64, 71 | Dark box | DON'T COME (2 lines) |
| Place boxes 4 5 6 8 9 10 | 66 + 53k, 9, 52, 36 | ×2 bold digits, and a faint chip-spot ring in the lower part | — |
| Come | 66, 48, 320, 32 | Light wash | COME (×2 text) |
| Field | 0, 83, 386, 30 | Tinted band; 2 and 12 circled in gold with `2:1` / `3:1` | FIELD (×2) in the 85 px label cell |
| Don't Pass Bar | 0, 116, 386, 16 | Dark band plus a double-six hint (bar 12) | DON'T PASS BAR |
| Pass line | 0, 135, 386, 18 | Lighter felt with a trim frame and a double inner line | PASS LINE |
| Odds lane | 0, 154, 386, 8 | Dashed baseline with chevrons, "behind the line" | ODDS (small, left) |

- Chips:
  - flat bets sit at the right third of their area;
  - **odds stack in the odds lane directly below their flat bet**;
  - place bets sit on the ring in their box;
  - a come bet travels into its number's box (animation §2.4).
- The point box gets `point_glow` (48 × 40, 4-frame loop) and the puck on its top-right corner.
- The dice rest in the Come area (x 40–200), per animation §2.3. The back wall is part of the layout, so
  `BOX_Y` moves by the wall height (§9).

### 4.2 Dice, puck and props (`gui/tables/craps/dice_small.png` + sprites `craps/`)

| Asset | Size | Content |
|---|---|---|
| `dice_small.png` | 144 × 36: row 0 = faces 1–6 (18² inked), row 1 = 8 tumble frames | A 16-px die: 15² bone face, 1 px lower-right depth, rounded 3 × 3 pips (red 5 × 5 one-pip), top-left highlight |
| Tumble frames | 18² | A **real 3D cube** projected per frame, rotating about a skew axis, flat-shaded from the top-left. The faces are blank apart from one unreadable smudge, so no false numbers show (animation §0.6.2). |
| `die_shadow` | 18 × 6 | Soft ellipse; scale its alpha with height z |
| `streak` | 2 frames of 12 × 18 | Motion lines behind a flying die |
| `puck` | 18 × 90 = 5 frames | OFF (black, bone ring) → flip (squashed) → **edge** → flip (white side) → ON (white, black ring). ON/OFF text is drawn at runtime at 0.5 scale on frames 0 and 4. |
| `total_{neutral,natural,craps,point}` | 24² | Total badge; the runtime number goes in the dark centre |
| `point_glow` | 48 × 40 × 4, frametime 3 | Point box ring |
| `stick` | 64 × 10 | Stickman's hooked stick for the stick-back beat |
| `flame` | 32 × 10 × 3, frametime 3 | Hot-shooter flame along the bottom edge of the active seat plate |
| `back_wall` | 16 × 8 | Tile (also baked into the layout) |

## 5. Dice Duel: an arena

### 5.1 Look

The duel becomes a small **arena**: an octagonal stone pit with a felt floor (`gui/tables/extras/
dice_duel_arena_<theme>.png`, 360 × 140).
- There is a dashed centre line.
- Two **lanes** are marked in the seat colours: yours on the left in blue, the dealer or opponent on the
  right in red. The lane rects are at x 40 / x 196, y 58, 124 × 56.
- Torches (theme-lit) stand on the four corners.

The dice are the hero objects, drawn ×2 at 40 px.

| Asset | Size | Content |
|---|---|---|
| `dice_duel_dice_big.png` | 520 × 240: 6 rows (faces 1–6) × 13 columns of 40² | col 0 base; 1–2 **land** (squash 1.1/0.85, bright rim); 3–8 **win loop** (gold halo pulse + orbiting glint); 9–12 **idle** shine sweep |
| `dice_duel_tumble_big.png` | 8 frames of 42² | The 3D tumble at duel size |
| `dice_duel_cup` | 40 × 240 = 6 frames | Leather cup with a gold band: rest, shake L, shake R, shake up, tip 20°, tip 35°. Mirror it for the right lane. |
| `dice_duel_vs` | 32 × 96 = 3 frames | The "vs" plaque with no letters (two crossed dice): level, tilt L, tilt R (toward the loser) |
| `dice_duel_plaque_{neutral,win,lose}` | 32 × 24 | Total plaques; the number is runtime text at ×2 |
| `dice_duel_crack` | 32 × 48 = 2 frames | Crack spreading over the losing plaque (animation §3.3 crush) |
| `dice_duel_house_stamp` | 42² | Red wax seal with dice 3 and 4: the house tie on 7 |

The **invite screen** uses:
- the cup (frames 1/2 rattle once per second);
- `timer` / `timer_urgent` as the countdown ring;
- `button_<theme>` for Accept (with a 1 px `#80FF40` glow drawn by the code) and Decline;
- a `stack/chip_*` stack for the stake.

## 6. Screen layouts

### 6.1 Frame at GUI 427 × 240 (854 × 480, GUI scale 2)

All three screens share one frame (`CasinoTableScreen`):

```
y 0–20    top bar on the backdrop: title plate (x 4) · seat plates · rules/leave icon buttons · balance plate (right)
y 21–205  rail nine-slice at (4, 21, 419, 184); felt tile at (14, 31, 399, 164) under it
y 206–240 bottom bar on the backdrop: chip tray (5 wells × 27 px from x 8, y 212) · icon buttons (x 146) ·
          phase + summary text (centre x ≈ 296) · bet timer (x 352) · big action button 48² at (373, 189)
          overlapping the rail corner
```

- The backdrop (640 × 360) is drawn centred and cropped. At larger GUI sizes it fills the space around the
  frame. A screen wider than 640 GUI px repeats the edge columns (the edges are calm wall).
- The frame stays at 427 × 240 and is centred.

### 6.2 Per game (GUI coordinates)

| Screen | Content |
|---|---|
| Roulette, betting | mini wheel (72²) at (22, 34); history pills at (22, 112) in 2 × 4; layout at (110, 36); racetrack at (110, 142) |
| Roulette, spin | The layout stays and dims (35 % ink wipe; chips desaturate). `wheel_shadow`, the bowl and the head rise centred at (213, 124). The phase banner is centred at y 24. |
| Roulette, result | Back to the betting view: dolly on the number, win outlines, payout labels, result banner centred over the racetrack at (184, 148, 150 × 26) |
| Craps | layout at (20, 33): exactly fills the felt height; dice in the Come area, badge 24 px right of the dice |
| Dice Duel | arena at (33, 34) on the backdrop (no rail: the arena is its own frame); name plates centred over the lanes at y + 16; cups at the lane ends; plaques at y + 40; vs badge centred at y + 54; outcome banner at (130, 178) |

### 6.3 Compact (GUI < 400 × 240, e.g. 854 × 480 at GUI scale 3 = 284 × 160)

- The rail is at (2, 13, 280, 124), the top bar is 12 px, and the bottom bar is 24 px. The action button
  still overlaps the rail corner.
- **Roulette:**
  - `layout_compact` at (64, 32) with a single column of 6 history pills at x 20;
  - the racetrack is **behind the racetrack toggle** (`icon/racetrack`), replacing the layout while it is on;
  - the spin view uses `wheel_mini_bowl` + `wheel_mini_head` (37 frames, one per pocket step) centred on the
    felt. The ball is drawn at the same fractions of Rw_mini = 31.4.
- **Craps:** `layout_compact` (264 × 117) fills the felt; the place digits are ×1; the dice use
  `dice_small`.
- **Dice Duel:** the arena is drawn 1:1 and clipped at the lane ends (the torches go); the dice are
  `dice_small` in each lane.
- Below 284 × 160: the vanilla fallback of UI.md (text panel), unchanged.

## 7. In-world (BER) and particles

| File | Size | Use |
|---|---|---|
| `entity/roulette/wheel_<theme>.png` | 128 × 80 | `RouletteTableRenderer`. Head (0, 0, 64², no digits; the BER rotates it continuously in 3D), bowl (64, 0, 64²), ball 4² at (0, 64), dolly billboard 8 × 12 at (8, 64), turret cap 8² at (16, 64). Two quads on the table top: the bowl is static; the head spins per animation §1.3 / §1.5. |
| `entity/craps/dice.png` | 64 × 16 | `CrapsTableRenderer` and the duel dice. Faces 1–6 (8² at x = 8(k−1)), puck OFF (48, 0) / ON (56, 0), rail pyramids (0, 8, 16 × 4). |
| `particle/craps/dice_dust_0..3` | 8² | Felt dust puff at each die landing |
| `particle/roulette/ball_spark_0..2` | 8² | Deflector kick at the table |
| `particle/roulette/dolly_twinkle_0..3` | 8² | Twinkle around the in-world result dolly |
| `particle/extras/duel_clash_0..3` | 8² | The duel dice landing next to each other (PvP in-world duel) |

The particle JSON / provider registration is done by the game lanes (animation.md §2.3). The ids are
`burmaldaholic:dice_dust`, `ball_spark`, `dolly_twinkle` and `duel_clash`.

## 8. File inventory and mockups

`generate()` returns **223 files** (Java): 40 `gui/tables` sheets, 164 sprite files (118 PNGs and 46
`.mcmeta`), 4 entity textures and 15 particle frames. That is about 0.69 MB. The largest file is `wheel_head.png` (213 KB).
A vitest (`tables/tables-art.test.mjs`) checks:
- ownership and Java-only output;
- determinism;
- the wheel order;
- racetrack order;
- the documented sizes;
- that nine-slice and animation `.mcmeta` sizes match the PNGs;
- the total size.

Mockups (854 × 480 unless noted), all composed from the generated PNGs:

| File | Shows |
|---|---|
| `tables_roulette_betting.png` | Village, betting: chips down (straight, split, corner, red, dozen), another seat's tinted chips, a hovered split with the ghost chip, the selected chip, the timer |
| `tables_roulette_spin.png` | Village, spin mid-frame: dimmed layout, big wheel (head frame 29), ball + trail on the track, a deflector spark, the "No more bets" banner |
| `tables_roulette_win.png` | Village, result: dolly on 17, win outlines on 17 and its outside boxes, payout discs and `+N` labels, result banner, the history with the new pill, and the mini wheel on 17 |
| `tables_craps_point.png` | Village, point ON 8: puck ON + glow, pass line + odds behind it, a come bet moved to 5, another seat's place bet on 6, a field bet, dice 3 + 5 with the point badge, the active shooter plate |
| `tables_dice_duel_reveal.png` | Village arena: your 4 + 5 in the win loop with a gold plaque, the dealer's 2 + 4 with a cracked plaque, the vs tilted, the dealer's chips pushed across, the outcome banner |
| `tables_roulette_bastion.png`, `tables_roulette_spin_bastion.png` | Piglin Parlor theme |
| `tables_craps_end.png`, `tables_dice_duel_end.png` | High Roller Lounge theme |
| `tables_roulette_compact_end.png` | 852 × 480 at GUI scale 3 (284 × 160), compact layout |

The text in the mockups uses a stand-in 5 × 7 bitmap font. The game uses the Minecraft font; the RU strings
are checked against the rects by the J-L2 fit test.

## 9. Changes to the animation spec (edited in `docs/design/animation/tables.md`)

1. **Roulette NO_MORE_BETS / SPIN:**
   - The layout no longer scales to 0.62; it stays in place and dims.
   - The big wheel rises over it (fade + 8 px rise, 400 ms, `inOutCubic`) and leaves the same way at RESULT.
   - The wheel is never drawn at 0.5 scale. The betting / result view shows the mini wheel frame of the
     result instead.
2. **Wheel rendering:**
   - Pre-rendered frames: 74 head frames plus 4 blur frames.
   - The pocket numbers are baked, upright digits, not runtime font rotated with the head.
   - The fallback F1 (8 rotation frames) is superseded.
3. **Dolly:** it stands on top of the winning stack (§3.4). The in-world badge uses `wheel_<theme>.png`'s
   dolly cell.
4. **Craps:**
   - The back wall is part of the generated layout, so the 232 × 6 strip and the `BOX_Y` shift are now the
     layout rects of §4.1.
   - Odds chips sit in the odds lane below the flat bet, not offset diagonally.
5. **Dice Duel:**
   - The panel becomes the full-screen arena (§5, §6), not 260 × 216.
   - The house stamp and the vs badge are the sprites of §5.
7. §4 asset inventory is superseded by §8 here. The paths are `gui/tables/…` and `gui/sprites/tables/…`,
   not `gui/sprites/burmaldaholic/<game>/…`.

**Glyph plane:** none is claimed. The tables need no private-use glyphs any more, because the Bedrock action
bar / form strip was their only consumer. E7xx and E8xx stay free (animation.md §6), and E1C0–E1CB stays
reserved for the Java chat lines only if a later task wants pocket or dice glyphs.

## 10. Lang keys

No new keys are needed for the art itself. The runtime labels above reuse existing keys where they exist.
These are the keys the redesign needs (EN / RU), added to `src/main/lang/<module>/`:

| Key | EN | RU |
|---|---|---|
| `gui.burmaldaholic.roulette.layout.even` | EVEN | ЧЁТ |
| `gui.burmaldaholic.roulette.layout.odd` | ODD | НЕЧЕТ |
| `gui.burmaldaholic.roulette.racetrack.tier` | Tier | Тьер |
| `gui.burmaldaholic.roulette.racetrack.orphelins` | Orphelins | Орфелен |
| `gui.burmaldaholic.roulette.racetrack.voisins` | Voisins | Вуазен |
| `gui.burmaldaholic.roulette.racetrack.zero` | Zero | Зеро |
| `gui.burmaldaholic.roulette.racetrack.toggle` | Racetrack | Трек |
| `gui.burmaldaholic.roulette.racetrack.neighbours` | Neighbours of %1$s | Соседи %1$s |
| `gui.burmaldaholic.roulette.button.undo` | Undo last chip | Отменить фишку |
| `gui.burmaldaholic.roulette.button.double` | Double all bets | Удвоить ставки |
| `gui.burmaldaholic.craps.layout.pass_line` | PASS LINE | ПАСС-ЛАЙН |
| `gui.burmaldaholic.craps.layout.dont_pass` | DON'T PASS BAR | НЕ-ПАСС |
| `gui.burmaldaholic.craps.layout.come` | COME | КАМ |
| `gui.burmaldaholic.craps.layout.dont_come` | DON'T COME | НЕ-КАМ |
| `gui.burmaldaholic.craps.layout.field` | FIELD | ФИЛД |
| `gui.burmaldaholic.craps.layout.odds` | ODDS | ОДДС |
| `gui.burmaldaholic.craps.puck.on` | ON | ВКЛ |
| `gui.burmaldaholic.craps.puck.off` | OFF | ВЫКЛ |
| `gui.burmaldaholic.extras.dice.arena.you` | You | Вы |
| `gui.burmaldaholic.extras.dice.arena.dealer` | Dealer | Дилер |
| `gui.burmaldaholic.common.bet_total` | Total bet %1$s | Всего: %1$s |
| `gui.burmaldaholic.common.button.clear` | Clear bets | Убрать ставки |
| `gui.burmaldaholic.common.button.rebet` | Repeat last bets | Повторить ставки |
| `gui.burmaldaholic.common.button.rules` | Rules | Правила |
| `gui.burmaldaholic.common.button.leave` | Leave the table | Покинуть стол |

Notes:
- The icon-button words are tooltips.
- The craps and roulette layout words may already exist in the current screens (the RU screenshot shows
  «Чёт», «Нечет», «Кам», «Филд», «Пасс-лайн», «Не-пасс»). Reuse them if they do.
- RU fits were checked against the rects of §3.2 / §4.1 with the 1.45 × budget: the longest is «ПАСС-ЛАЙН»
  (≈ 54 px in a 386 px band).
- For the 40 px compact outside cells, «НЕЧЕТ» at 1× (≈ 29 px) fits.

## 11. Developer notes

- **Ids:** GUI sprites are `ResourceLocation.fromNamespaceAndPath("burmaldaholic", "tables/<owner>/<name>")`,
  blitted with `guiGraphics.blitSprite`. Sheets (`gui/tables/…`) use `blit(RenderPipelines.GUI_TEXTURED, id,
  x, y, u, v, w, h, texW, texH)` with the frame's UV.
- **Frame math:** `tables.mjs` exports `HEAD_FRAMES = 74` and `HEAD_COLS = 10`. The mini wheel is 37 frames
  in 10 columns. `dice_duel_dice_big` is 13 columns by face.
- **Seat tint:** `setColor` / `ARGB.multiply` on `seat_stripe` and `stack/chip_grey` only; never tint whole
  plates.
- **Reduced motion:** use frame 0 of `chip_select`, `pocket_glow`, `cell_win`, `point_glow`, `flame` and the
  dolly shine. There is no head blur (the head steps frames only at beats), and the tumble shows 2 frames.
- **Performance:** the whole roulette spin is 3–5 blits a frame (shadow, bowl, head frame, ball + trail, glow).
