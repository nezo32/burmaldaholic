# Visual redesign: side games, PvP and the Casino Menu shell (Java)

Owner: visual design (pixel-art game UI). Status: art generated and committed, ready for the screen lanes (v1, 2026-09-24).
Scope: **Coin Flip, Wheel of Fortune, Plinko, Scratch Cards**; the **PvP screens** (hub, lobby, match, result,
taunts) and the Showdown modes (Coin Flip Duel, Wheel Party, Plinko Battle, Scratch Showdown); the **Casino Menu**
and its shell (cashier, wallet, contracts, Loan Shark, achievements, challenges, My Casino, rules); the **HUD chip
counter** and the **toasts**.

**Java only.** Bedrock support was dropped on 2026-09-24. This spec has no Bedrock mapping, no glyph planes, no forms
and no Bedrock textures. The generator module still lives in `bedrock/tools/assets/modules/` until the generator moves
to `tools/assets/`, and it writes Java outputs only.

Builds on: `docs/design/animation/extras-pvp.md` (choreography, timings, fidelity rules), `docs/design/animation/global.md`
(palette §2.1, panels and `CasinoButton` §2.2, celebration kit §2.6, HUD §4.1, menu §4.2, toasts §4.10) and the slots
look (`docs/design/animation/slots.md` §3, `textures/gui/slots/*`). Where this spec changes a size or a choreography
beat, the change is also made in those files (§13).

Deliverables:

| What | Where |
|---|---|
| Generator (extras + PvP) | `bedrock/tools/assets/modules/extras.mjs` + helpers in `modules/extras/` (`kit`, `icons`, `coin`, `wheel`, `plinko`, `scratch`, `pvp`, `scenes`, tests in `extras-art.test.mjs`) |
| Generator (menu shell, additive to core) | `bedrock/tools/assets/modules/core/menu.mjs`, wired from `core.mjs` (`menuShell()`) |
| Art (Java) | `textures/gui/extras/*`, `textures/gui/pvp/*`, `textures/gui/core/menu/*`, sprites under `textures/gui/sprites/burmaldaholic/{extras,pvp}/*` and `textures/gui/sprites/core/{menu,hud,toast}/*`, `textures/item/extras/lucky_coin_{heads,tails}.png`, `textures/entity/extras/*`, `textures/particle/extras/foil_flake_*.png` |
| Mockups | `docs/design/visual/mockups/extras_*.png` (854 × 480), made by `render_extras.py` (+ `mcfont.py`) from the real PNGs |

Regenerate: `cd bedrock && node tools/gen-assets.mjs` (checked by `npm run lint` through `gen-assets --check`), then
`python3 docs/design/visual/mockups/render_extras.py`.

---

## 0. The problem, and the bar

Today (see the client game-test screenshots `0018_jtest_ru_wheel`, `0019_jtest_ru_plinko`, `0021…0027_jtest_ru_menu_*`,
`0028_jtest_ru_coin_flip`, `0038…0049_jtest_pvp_*`) these screens are flat, dark rectangles with a thin gold line and
vanilla grey buttons, with text as the only content. The wheel is 54 rotated rectangles, Plinko is white dots, the
Scratch card is grey boxes, PvP is a list, and the Casino Menu is a row of grey buttons over a grey page.

The new slots screens set the bar, and every screen in this spec now meets it:

1. **A themed backdrop** (400 × 240, 1 art px = 1 GUI px) that says where you are.
2. **A framed play area**: a 64² nine-slice frame (border 12) with the house gold trim in the game's material.
3. **Crisp pixel art**: 16-px art drawn at 2× (32 px) with a 1 px ink outline and a 2 px, 40 % ink drop shadow,
   in 40-px (or 20-px) cells, with win/idle frames where they matter.
4. **Casino buttons** (`global.md` §2.2 `CasinoButton`: secondary, primary gold, danger red) instead of vanilla grey.
5. **Tier celebrations** from the shared kit (`global.md` §2.6), started by the game's own landing flourish.

No text is baked into any texture: plaques and banners are blank, and code draws the translated strings (§14).
Shapes, never colour alone, tell outcomes apart (coin faces, wheel segment icons, bin tiers carry the number).

---

## 1. House style (applies to every asset here)

| Rule | Detail |
|---|---|
| Palette | `docs/branding/branding.md` + `global.md` §2.1 tokens; game hexes in §2.1 below. Text on dark: `bone` or `gold`, never `frame` purple. |
| Icons | 16 × 16 string grids (`modules/extras/icons.mjs`, `core/menu.mjs`), Minecraft item silhouettes, light from the top left. Shipped at 16 (raw), 20 (1× + outline + 1 px shadow) and 40 (2× + outline + 2 px shadow). |
| Big objects | drawn at "board" density (1 art px = 1 GUI px) when they are large and rotate or move per pixel: wheel face, rim, Plinko board, tickets, backdrops. The coin is 32-px art at 2× (64 px), so it matches the symbol density. |
| Outline | 1 px `ink #180A28`, outside the silhouette. |
| Shadow | 2 px down-right, `ink` at 40 % (1 px on 20-px icons). |
| Frames | nine-slice 64², border 12: ink, 3 px gold bevel (`#FFF4B0`/`#FFD640`/`#E8B830`/`#B07010`), theme material band, inner bevel, ink; corner studs; edge lamps every 8 px (symmetric, so tiled edges repeat cleanly). |
| Plaques | blank; ink edge, coloured edge, face with a 1 px top sheen and a 1 px dark bottom line; 1 px rounded corners. |
| Animation | vertical strips; Java `.mcmeta` `animation` with `height` when frames are not square. Code-driven frame sets (spin, bins, pips) are horizontal sheets blitted by UV. |
| Motion | from `extras-pvp.md` (timings, curves, fidelity). The art never implies an outcome (no near-miss frames). |

### 1.1 Rendering order (every extras / PvP screen)

backdrop → frame (nine-slice) → marquee strip → title banner → play-area objects (wheel, board, ticket, cards) →
side columns (inset / casino panels, text) → widgets (`CasinoButton`) → stratum 2 overlays (pop-outs, banners, bubbles)
→ stratum 3 `CelebrationOverlay` → tooltips. Same strata as slots.md §4.

---

## 2. Shared kit

### 2.1 Game colours (on top of `global.md` §2.1)

| Use | Hex |
|---|---|
| Coin gold field / rim light / emboss / edge grooves | `#FFD640` / `#FFE87A` / `#DCA424`, `#FFF8C8`, `#7A4A08` / `#D09A20`, `#8A5A08` |
| Heat rim red / soul blue | `#C81A08` `#FF6020` `#FFD080` / `#1A6AFF` `#40D0FF` `#C8F8FF` |
| Wheel wedges (extras-pvp §3.2) | B `#3A3A3A`, C `#3FA535`, H `#6C8EBF`, M `#3D5A80`, D `#2E7D32`, T `#9C27B0`, E `#00C853`, X `#4FC3F7`, each with a light rim band and a dark inner band |
| Wheel rim lacquer | `#8C1834` `#A0203C` `#7A1430`, lights `#C0304A`, shade `#5A0E24` |
| Plinko cabinet | indigo `#1C1448` → `#0E0A26`, lattice `#3A2A7A`, neon `#40E0FF` / `#FF40C0` |
| Plinko bin tiers | < 1 `#C83A3A`, = 1 `#3D6AB0`, 1.4–3 `#2E9A3E`, 4–33 `#E8B830`, ≥ 100 `#9C27B0` |
| Foil: silver / gold / holo | `#B4B4B4` `#D8D8D8` `#8C8C8C` / `#E0B040` `#FFE070` `#A87818` / `#B8A8D8` + pink-cyan-gold bands |
| Ticket paper: basic / golden / showdown | `#F4ECD8` / `#FFF0C8` / `#ECE4F8` |
| Grudge | `#8B0000` → `#FF2A2A` (extras-pvp §0.2) |
| Plates: you / rival / bot | gold on `#3A1A5C` / `#D83440` on `#3A0E1C` / `cool #8FA8C8` on `#1C2438` |
| Loan Shark steel | `#1A2A2E` `#22363A` `#0E1A1E`, rivets `#8A9AA8`, hazard `#D83440` |
| Ledger leather | `#5A0E24` `#6A1430` `#4A0A1C`, stitching `#C8903C` |

### 2.2 Screen skeleton (full layout, 400 × 240)

```
┌──────────────────────── frame (nine-slice 64, border 12) ────────────────────────┐
│ ▪▪▪▪▪▪▪▪ marquee ▪▪▪▪▪▪▪▪ ╔═══ title banner (48×24, b10) ═══╗ ▪▪▪▪ marquee ▪▪▪▪▪ │ y 12
│                                                                                  │
│  side column         PLAY AREA (the game object on the backdrop)     side column │
│  (inset panel)                                                        (inset)    │
│                                                                                  │
│  [⛁ 12,250] chip counter    [ primary action ]              [ Leave ]            │ y 206–226
└──────────────────────────────────────────────────────────────────────────────────┘
```

- Panel: centred, 400 × 240, `x = (guiW − 400) / 2`, `y = (guiH − 240) / 2`. Backdrop drawn at the panel origin, the
  frame on top with a transparent centre, so the backdrop is the play area.
- Title banner: `<game>_banner` stretched to `max(110, textWidth + 32)` × 22, centred on the top border (y 3).
  Title text `gold`, 1×, centred at y 10.
- Marquee: `<game>_marquee` (128 × 12 × 4 frames, frametime 3) tiled along y 12 inside the frame (Coin Flip; the wheel
  uses its canopy backdrop instead; Plinko and Scratch have none).
- Side columns: `core/panel/inset` wells (text on dark) or `core/panel/casino` for a solid card.
- Bottom bar: the **chip counter** (§9) at the bottom left, the primary `CasinoButton` centred, *Leave* at the right.

### 2.3 Scenes (backdrop + frame + marquee + banner per theme)

| Theme | Backdrop (`textures/gui/…`) | Frame material | Lamps | Mood |
|---|---|---|---|---|
| Coin Flip | `extras/coin_backdrop.png`: green damask back room, brass lamp with a dithered light cone, red velvet curtains with gold tassels, wainscot, baize table edge | walnut planks + gold, felt-green inner line | gold | intimate card room |
| Wheel | `extras/wheel_backdrop.png`: night carnival, red/cream scalloped canopy, catenary string lights, far ferris wheel, tents, boardwalk | red lacquer + gold, gold inner line | warm gold | fairground |
| Plinko | `extras/plinko_backdrop.png`: indigo arcade, glowing cabinets, neon wall strip, perspective neon floor grid | indigo lacquer + gold, cyan inner line | cyan | arcade |
| Scratch | `extras/scratch_backdrop.png`: corner kiosk, striped wallpaper, a shelf of ticket rolls, horseshoe, text-free posters, glass counter | teal enamel + gold, cream inner line | warm | shop counter |
| PvP | `pvp/arena_backdrop.png`: stage with blue and red spotlights, pennants, gold floor ring, crowd silhouettes | gunmetal + gold, glint-purple inner line | gold | arena |
| PvP grudge | `pvp/arena_backdrop_grudge.png`: the same, red-lit, with claw rips | blood iron + red trim, claw marks at two corners | red | grudge |
| Casino Menu | `core/menu/lobby_backdrop.png`: art-deco wall (gold sunburst arcs), pillars, chandelier glow, carpet | burgundy leather + gold filigree (menu shell, §8) | — | casino lobby |
| Loan Shark | `core/menu/loan_backdrop.png`: harbour at night, sick moon, fog, pier, a red lamp, circling fins | gunmetal + rivets + hazard red (menu shell, loan variant) | red | threat |

### 2.4 GUI scales and smaller screens

The mockups are 854 × 480 at GUI scale 2, which is a **427 × 240** GUI. Minecraft's *Auto* GUI scale always leaves at
least **320 × 240**. Three layouts:

| Layout | When | Panel | Rules |
|---|---|---|---|
| **L** (full) | `guiW ≥ 416` and `guiH ≥ 240` | 400 × 240 | as in the mockups |
| **M** (compact) | `320 ≤ guiW < 416` | 320 × 240 | The game object keeps its size (pixel art is never scaled by a non-integer). Side columns merge into one 104 px right column. Buttons drop their labels and use 20 × 20 icon buttons with tooltips (icons from `tab_icons`, `mode_icons`, `coin_mini`, the `+`/`−` glyphs). The backdrop is cropped at the centre, not scaled. Coin: the history column becomes a single row of 6 pips under the pad. Wheel: legend becomes icons with counts only. Plinko: the board (272) sits at x 8, the column is 32 px icon buttons. Scratch: the prize table moves into a tooltip on the `?` button. |
| **S** (forced) | `guiW < 320` or `guiH < 240` (only with a forced GUI scale) | 300 × 200 minimum | Frame border stays 12. The title banner shrinks to the text. Side panels become a drawer toggled with *Tab* (a 12 px tab on the right edge). If the game object does not fit, the screen asks for a smaller GUI scale (`gui.burmaldaholic.visual.scale_hint`) under the object and keeps working. |

Pixel density: GUI coordinates are integers; draw every sprite at integer positions; text at 1× (body), 2× (hero
numbers, pop-out names) or 3× (celebration words only), as `global.md` §2.2 says.

---

## 3. Coin Flip (solo), Coin Flip Duel and Double-or-nothing

### 3.1 Art

| Asset | Size | Content |
|---|---|---|
| `gui/extras/coin_spin.png` | 768 × 64 (12 × 64² H) | The Lucky Coin as a 3 px thick cylinder turning about the vertical axis: frame 0 heads, 1–5 tilting, **6 edge-on** (the reeded edge, grooves every 2 px, lit at the top), 7–10 tilting, 11 tails. Face: bevelled rim (light top-left, dark bottom-right, inner wall reversed), a bead ring of 28 beads, a gold field with a crescent sheen, and the **embossed emblem**: heads = a crowned blocky head in profile, tails = crossed pickaxes over a ring. The face darkens (70 % → 100 %) as it turns away. |
| `gui/extras/coin_glint.png` | 256 × 64 (4 H) | white diagonal band clipped to the face (overlay on frame 0 / 11) |
| `gui/extras/coin_heat.png` | 128 × 64 (2 H) | 0 red-hot rim, 1 soul-blue rim (heat 3–5, Soul Wager) |
| `sprites/…/extras/coin_shadow` | 48 × 12 | dithered ink ellipse, stretched by code (shrinks to 50 % at the apex) |
| `sprites/…/extras/coin_pad` | 144 × 40 | oval baize landing pad, walnut rim, gold stitching: the "table line" of extras-pvp §1.2 |
| `sprites/…/extras/coin_flame`, `coin_flame_soul` | 16 × 24 × 8 (V, frametime 2, height 24) | licking flames with side tongues and a breaking ember (heat 4 / 5) |
| `gui/extras/chain_pips.png` | 96 × 16 (6 H) | 0 hollow (future link), 1 current (gold ring), 2 heads won, 3 heads lost, 4 tails won, 5 tails lost (won = `bonus` ring, lost = `chip.red` ring + dimmed coin) |
| `gui/extras/coin_mini.png` | 28 × 14 (2 H) | mini heads / tails with a 2:1 emblem: call buttons, nameplates, history |
| `item/extras/lucky_coin_heads.png`, `…_tails.png` | 16² | item-model variants for the in-world `ItemDisplay` toss (extras-pvp §1.3) |
| `sprites/…/extras/coin_frame`, `coin_marquee`, `coin_banner` | 64² b12 / 128 × 12 × 4 / 48 × 24 b10 | the scene |

### 3.2 Layout (L, mockup `extras_coin_flip.png`)

| Element | Position (panel-local) | Notes |
|---|---|---|
| Coin (64²) | centre x 200, rest y 116 (top-left 168, 84); apex 46 px higher | spin frame by `CoinAnim.frame(h)` (extras-pvp §1.2), ghosts of the 2 previous positions at 28 % / 14 % while airborne |
| Pad | 144 × 40 at (128, 148) | the coin bounces on its top ellipse; shadow on the pad centre (y 164) |
| "Your call" column | inset 104 × 128 at (18, 38) | *Heads* / *Tails* `CasinoButton`s 96 × 20 with the mini coins; the chosen call is **primary highlighted**; bet well + `−` `+` *Max* |
| "Last flips" column | inset 106 × 128 at (272, 38) | 2 rows × 5 chain pips (the last 10 results, won/lost ring), tally line, streak line with the HUD flame, *Pays 1.96×*, *Win: N* preview |
| Status line | centred at y 194 | `extras.coin.flipping` in `lilac` while airborne; the result line after landing |
| Bottom bar | chip counter (16, 210); *Flip again* primary 100 × 20 centred (disabled while airborne); *Leave* | |

**Landing** (extras-pvp §1.2 timings): frame fixed on the true face at first contact; squash 1.12 × 0.88; `coin_glint`
sweeps once on WIN only; the face name pops under the coin (`outBack`, 2× text, `gold` on WIN, `bone` on LOSS). The
pips row shifts left and the new pip flips in (180 ms X-scale).

**Soul Wager**: the same sheet tinted `0xFFB05050` + `coin_heat` frame 0 at 0.8; the pad becomes charred (tint
`#3A1A10`), the curtains' light cone turns red (`vignette_red`), the hold bar is a `core/menu/progress` frame with
`progress_fill_red` and an ember at the head.

### 3.3 Coin Flip Duel (extras-pvp §2) — versus layout

Uses the PvP skeleton (§7): arena backdrop, two seat plates (you / rival) sliding in from the edges, the `vs_badge`
between them, the coin on the pad in the centre, the **pot** (§7.4) above the coin. Chain tracker = `chain_pips`
(5, under the pot). Heat per link (extras-pvp §2.2 table): link 2 warm glint, 3 `coin_heat` 0 at 0.5, 4 at 0.8 +
`coin_flame` licking the rim (4 copies around it, rotated 0/90/180/270), 5 `coin_flame_soul` + `coin_heat` 1. The
winner's plate swaps to `plate_winner`, the loser's to `plate_loser`.

---

## 4. Wheel of Fortune and Wheel Party

### 4.1 Art

| Asset | Size | Content |
|---|---|---|
| `gui/extras/wheel_face.png` | 160² | The **rotating layer** for the Appendix B list: 54 wedges in their kind colour (light rim band, dark inner band, 1 px dark separators, a faint sheen on each leading half), an 8 × 8 icon per wedge at r 64 **turned radially** (so the landed icon is upright under the pointer), 54 brass pegs at r 75 on the boundaries, a gold lip, and a dark lacquer medallion (r 22) under the hub. This is also the reference for `WheelFaceTexture.bake` (extras-pvp §3.2), which rebuilds it at run time when the server's list differs. |
| `gui/extras/wheel_rim.png` | 184² | The **static** ring (r 80–92): red lacquer with gold lips, 24 dark bulb sockets, the flapper bracket at the top. Transparent centre. |
| `gui/extras/wheel_bulbs.png` | 24 × 8 (3 H) | off, on (warm), on (gold flash); positions from `wheel.mjs BULBS` (r 85.5, every 15° + 7.5°) |
| `sprites/…/extras/wheel_hub` | 28 × 28 × 6 (V) | gold dome, 6 bolts, ruby gem; `.mcmeta` frames `[0×54, 1, 2, 3, 4, 5]` = a glint every 3 s |
| `sprites/…/extras/wheel_flapper` | 14 × 24 | red leather tongue, stitched, gold pivot at (7, 4); code rotates it (δ(φ), extras-pvp §3.3) |
| `sprites/…/extras/wheel_stand` | 128 × 52 | lacquer A-frame legs, brace, plinth with gold studs; behind the rim |
| `sprites/…/extras/wheel_pop` | 48² | gold burst with a dark disc: the stop-beat pop-out behind the 40 px icon |
| `gui/extras/wheel_icons.png` / `_8` / `_40` | 128 × 16 / 64 × 8 / 320 × 40 | segment icons B C H M D T E X: cracked grey chip, creeper, half chip + dotted ghost half, one chip, two chips, three chips (pyramid), emerald, diamond. 16 for the legend, 8 for the face, 40 for the pop-out. |
| `gui/extras/wheel_parts.png` | 64 × 16 | rim tile, peg, bulb off, bulb on (for the run-time bake and the BER) |
| `entity/extras/wheel_bulbs.png` | 8 × 4 | BER bulb quads |

### 4.2 Layout (L, mockup `extras_wheel_landing.png`)

- Wheel block at (22, 32): rim 184² at (0, 0), face at (12, 12) rotated about its centre, bulbs on the rim, hub at the
  centre (78, 78), flapper at the top (pivot at rim-local (92, 5)), stand under it at (28, 168) (it runs into the frame,
  which hides the plinth: the wheel stands "in" the booth).
- Right column at x 212: *Segments* inset 180 × 110 (8 rows: 8 px icon, `name ×mult`, `count/54` right-aligned; the
  landed kind in `gold`), bet/result inset 180 × 34, then `Bet −` `Bet +` *Max* (58 × 20) and *Spin* (primary 116 ×
  20) + *Leave*.
- **Stop beat** (extras-pvp §3.3): the landed wedge +30 % white, all other wedges ×0.62 (dimmed in the face texture
  copy or with a darkening overlay drawn through a wedge mask), bulbs alternate gold / warm twice, the pop-out burst
  at rim-local (68, 12) with the 40 px icon and the name on a `pvp/mode_banner` plaque under it. LOSS / RETURN / PUSH:
  no burst; a 1 px `bone.shade` outline on the wedge instead (no win disguise).
- Creeper: the swell of extras-pvp §3.3 steps through whole-pixel sizes only: the 40-px pop-out icon, then the 16-px
  `wheel_icons.png` C drawn at 3× (48 px) on the second flash. No fractional scales.

### 4.3 Wheel Party

Same rim, hub, flapper, stand and backdrop. The face is baked at run time from the stake shares: wedges in the
joiners' dye colours (`DYES`), 1 px dark seams, the player's head (8 px on arcs ≥ 6°, 12 px on shares ≥ 20 %) at the
arc centre inside a 1 px ink + 1 px gold square drawn by code (the 20-px `head_frame_*` sprites are for plates). The
legend rows are `pvp/lobby_row` plaques with a dye swatch, head, name and share. *No more bets*
uses `pvp/mode_banner`.

---

## 5. Plinko and Plinko Battle

### 5.1 Geometry (board-local GUI px; **changed** from extras-pvp §5.3, see §13)

| Constant | Value |
|---|---|
| Board | 272 × 204 (`gui/extras/plinko_board.png`) |
| Peg (row r, j = 0…r) | x = 136 + (j − r/2)·20, y = 30 + r·12 (12 rows) |
| Bin k (0…12) | centre x = 136 + (k − 6)·20, caps at y 176 (18 × 14), brass dividers between bins |
| Chute | 28 × 16 at (122, 4) |

The pitch goes from 22 × 8 to **20 × 12**: the triangle is steeper, reads as a real pegboard and leaves room for
14-px bin caps with the multiplier inside. `PlinkoAnim` uses these constants (y_r = 30 + 12r, hop 3.5·0.93ʳ px).

### 5.2 Art

| Asset | Size | Content |
|---|---|---|
| `gui/extras/plinko_board.png` | 272 × 204 | indigo cabinet field with a diamond lattice, a light pool under the chute, star dust, a lilac guide line along the triangle, **pre-drilled peg sockets** (dark rings at every peg), the bin bay with brass dividers, cyan (left) and magenta (right) neon side strips, walnut + gold frame with corner studs, the chute mount |
| `gui/extras/plinko_peg.png` | 21 × 7 (3 H) | idle silver pin, hit (white-hot), afterglow (gold) |
| `gui/extras/plinko_ball.png` | 36 × 18 (4 H × 2 rows) | glossy chip-red ball with a white stripe band that rolls round (4 frames); row 2 = the underdog gold ball |
| `gui/extras/plinko_bins.png` | 90 × 28 (5 H × 2 rows) | caps per tier (loss red, even blue, win green, big gold, top purple); row 1 unlit, row 2 lit (brighter face, white rim, glow bleed above). A dark label well; the multiplier is text (`bone`, white when lit). |
| `gui/extras/plinko_chute.png` | 56 × 16 (2 H) | brass funnel, gate closed / open |
| `sprites/…/extras/plinko_bin_hidden` | 18 × 14 × 6 (V, height 14) | foil cap with a shimmer (Final Ball) |
| `gui/extras/plinko_mini_board.png` | 56² | Plinko Battle mini board: 12 rows at a 4 px pitch, 13 lamp dots in the Medium tier colours, walnut base; the 3 × 3 ball is drawn by code |
| `sprites/…/extras/edge_glow` | 32² b12 | soft gold edge glow for the EDGE eruption (flash-safe) |
| `entity/extras/plinko_lamp.png`, `plinko_ball.png` | 12 × 2, 4 × 2 | BER lamp strip and ball quad |

### 5.3 Layout (L, mockup `extras_plinko_drop.png`)

Board at (14, 28). Right column at x 296 (96 wide): *Risk* inset with three 14-px-high `CasinoButton`s (the chosen risk
highlighted), bet well with *Top: ×33*, `−` `+`, *Drop* (primary; disabled while the ball falls), *Last balls* (4 lit
caps with their multipliers), the chip counter. During a drop: the hit peg shows frame 1 for 100 ms then frame 2 for
300 ms; three ghost balls (alpha 0.45 / 0.25 / 0.1) trail; the landed cap switches to its lit frame and presses 2 px.

**Plinko Battle**: your board at (14, 28) as in solo (the 0.7 scale of extras-pvp §6.2 is dropped: non-integer
scales blur pixel art); the ranking column becomes the seat plates (§7.2, compact 110 × 30) on the right, and the
other players' boards are `plinko_mini_board` tiles (56²) in a 2-column grid under them. Final ball: every cap is
`plinko_bin_hidden` until its reveal cue.

---

## 6. Scratch Cards and Scratch Showdown

### 6.1 Ticket themes

| Theme | Texture | Look | Foil |
|---|---|---|---|
| **Basic — "Lucky Miner"** | `gui/extras/scratch_ticket_basic.png` 212 × 196 | cream paper with a guilloche wave, silver rope border, perforated sides, an emerald header band with a diamond and an emerald flanking a blank title plaque, an emerald swallow-tail ribbon for the hint, a text-free barcode | brushed silver, embossed coin-slot logo, two sparkles |
| **Golden — "Gold Rush"** | `…_gold.png` 212 × 196 | warm gold paper, gold rope border, ruby header band with a crown and a gold ingot, ruby ribbon | brushed gold |
| **Showdown — "Holo Duel"** | `gui/extras/scratch_card_showdown.png` 86 × 98 | lilac paper, purple border, a dark header strip with blue and red corner marks (the duel) | holographic lilac with pink / cyan / gold bands |

Cells: solo **3 × 3 of 60 × 44** (gap 4) at ticket (12, 36); Showdown **3 × 3 of 24 × 24** (gap 2) at (6, 16).
Cells are inset wells (`cellBg` with a dotted pattern); the symbol sits in the top of a solo cell (40-px icon at
cell (10, −3)) with the amount centred at y + 35 in ink brown; Showdown cells hold the 20-px icon at (2, 2).

### 6.2 Art

| Asset | Size | Content |
|---|---|---|
| `gui/extras/scratch_foil.png` | 60 × 88 (2 V: basic, gold) | solo cell foil |
| `gui/extras/scratch_foil_showdown.png` | 24² | Showdown cell foil |
| `sprites/…/extras/scratch_foil_shimmer` | 60 × 44 × 8 (V, frametime 7, height 44) | the sheen sweep (hover: draw a second copy 4 frames ahead) |
| `sprites/…/extras/scratch_foil_shimmer_small` | 24² × 8 | the same for Showdown cells |
| `gui/extras/scratch_edges.png` | 64 × 12 (16 variants × 4 px, rows basic / gold / showdown) | **scratch-edge autotile** (§6.4) |
| `gui/extras/scratch_scuff.png` | 60 × 44 | faint scrape marks left on a revealed cell |
| `gui/extras/scratch_symbols.png` / `_20` / `_40` / `_40_win` | 144 × 16 / 180 × 20 / 360 × 40 / 360 × 40 | coal, iron ingot, gold ingot, emerald, diamond, nether star, creeper, rabbit's foot, charred (prize rank order + Showdown extras); `_40_win` = lit palette + gold halo for the trio |
| `sprites/…/extras/trio_frame` | 16² b4 | gold frame with white corner gems: trios, hover outline, current Showdown cell |
| `gui/extras/torn_corner.png` | 72 × 24 (3 H) | corner fold on a losing card |
| `gui/extras/explosion_puff.png` | 64 × 16 (4 H) | burn impact |
| `gui/extras/flakes.png` | 32 × 4 | 4 foil flakes, 2 embers, 2 sparks (GUI particles) |
| `sprites/…/extras/foil_final` | 24² × 6 | gold foil with a travelling shimmer and a dark well for the "?" (text) |
| `sprites/…/extras/charred` | 24² × 2 (frametime 10) | burnt cell with a flickering ember rim |
| `sprites/…/extras/scraper` | 16² | the Lucky Coin on edge, tilted 30°: the cursor over the ticket |
| `sprites/…/extras/char_vignette` | 32² b12 | creeper-card burn creeping in from the edges |
| `sprites/…/extras/ticket_shadow` | 16² b6 | soft shadow under the ticket (slide in/out) |
| `particle/extras/foil_flake_0..3.png` | 4² | world particle frames (`burmaldaholic:foil_flake`) |

### 6.3 Layout (L, mockup `extras_scratch_half.png`)

Ticket at (20, 30) with `ticket_shadow` (+3, +4). Title text on the header plaque (`extras.scratch.ticket.*`, ink-green /
ruby, bold). Hint on the ribbon (`anim.scratch.drag_hint`, wraps to two lines in RU inside 180 px). Right column at x 244
(148 wide): prize table on a `core/panel/casino` card (20-px icon, prize, odds right-aligned, the creeper rule),
*Basic* / *Golden* toggles, *Scratch all* (primary), *New (price)*, *Leave*. The scraper follows the cursor over the
ticket; flakes (`flakes.png` 0–3) spray from the drag point.

### 6.4 The scratch-reveal mask (how the renderer uses the autotile)

Each cell keeps a coverage mask of 4 × 4 sub-tiles (solo 15 × 11, Showdown 6 × 6) (extras-pvp §7.3 with the new
cell sizes). To draw a covered or partly scratched cell:

1. Draw the revealed content (symbol, amount, scuff) under the cell.
2. For every sub-tile still covered, blit the foil's matching 4 × 4 region.
3. Compute `m` = the bitmask of **scratched** neighbours (N 1, E 2, S 4, W 8). If `m ≠ 0`, blit variant `m` of the
   theme's row of `scratch_edges.png` over the sub-tile: a dark curled lip on the scratched sides with a few bright torn
   specks. Where the edge variant is transparent in a corner shared by two scratched sides, **skip that corner pixel of
   the foil** too, so the torn edge is ragged rather than stair-stepped.
4. Draw the shimmer only on cells with no scratched sub-tile.

That is 9 × 165 sub-tiles at most per card, one `blit` each for foil and edge: well inside the 600-blit budget of
extras-pvp §0.8 because covered runs can be merged per row when `m = 0`.

### 6.5 Scratch Showdown (mockup `extras_pvp_match.png`)

Cards are 86 × 98 (was 100-wide mini cards with 20-px cells). Up to 3 cards in a row at (22, 157, 292) + 12 px under
their seat plates; 4–6 players use two rows of 3 with 64-px compact plates (head + score only). Current cell: foil +
fast shimmer + `trio_frame` outline. Trios: `trio_frame` around each cell + the connecting line (code, 1 px `gold`).
Burn: `charred`, puff, spark (flakes 4–5). Foot: rabbit's foot icon + a ×2 / ×4 chip. Final cell: `foil_final` with a
bold `?`.

---

## 7. PvP presentation

### 7.1 Versus layout (Showdown match, 2–6 seats)

```
 Scratch Showdown                    ╔═ GRUDGE MATCH ═╗                  Step 4/9
                     (pot pile) [ Pot 600 ]            💬 It's rigged!
 [head You 320 ×2 ALL-IN 🔥]  [head Creeper42 150 ▣••]  [head Notch 210 3-5]
 ┌card┐                      ┌card┐                     ┌card┐
 └────┘                      └────┘                     └────┘
          [ Scratch! ]  [ Taunt… ]  [ Close ]
```

- Seats left to right in seat order; **you are always the first seat** drawn with `plate_you`. Humans use `plate_rival`
  (or `plate_grudge` in a grudge match for the grudge rival), bots `plate_bot`.
- Plate content: `head_frame_*` (20²) with the 16 px face at (7, 7); name (`gold` for you, `bone` otherwise) at
  (29, 6); score at (29, 17); badges right-aligned at y 16: `bot_<difficulty>` (bots), `record_chip_<lead|trail|even>`
  with the head-to-head record as text, the nemesis skull (`gui/pvp/badges.png` 2), the ALL-IN tag (`pvp/all_in` +
  text), the streak flame (`flame_red`/`flame_gold`), the ready tick (`badges.png` 4) above the plate once that seat
  pressed the step button.
- The **pot** sits between the banner and the plates: `pot_glow` + `gui/pvp/pot_chips.png` (s / m / l by pot size:
  < 10× stake s, < 50× m, else l) + `pot_plaque` with `lobby.pot`.
- **Grudge banner**: `grudge_left` / `grudge_right` (128 × 40, b12) stretched to 102 × 28 each, clashing at the centre;
  `grudge.title` in bold `bone` with a dark red outline; `pvp/claw` over the underdog's plate corner.
- **Taunt bubbles**: `bubble_friendly` / `bubble_cheeky` (24² b8) sized to the text + 30, `bubble_tail_*` under the
  sender's plate (pointing down at it), the pictogram (`gui/pvp/taunt_icons.png`, order gg, luck, wow, rigged, again,
  steel, bye, respect) at (5, 3), the text at (24, 7). In the world the taunt stays a `TextDisplay` (extras-pvp §9.7).
- **Bot plates**: steel `plate_bot`, bolted `head_frame_bot`, the difficulty badge (easy 1 pip green, normal 2 amber,
  hard 3 red, mixed purple die) and `[BOT]` stays in the name only where the existing strings add it.

### 7.2 Other PvP screens

| Screen | Look |
|---|---|
| **Hub** (Casino Menu → PvP tab) | page with a 2 × 3 grid of `mode_card` (48² b12, `mode_card_selected` on hover) holding `gui/pvp/mode_icons_40.png` (coin, wheel, plinko, scratch, slots) + the mode name; your record line with a record chip; the nemesis line with the skull; open lobbies as `lobby_row` plaques |
| **Lobby** | seat rows (`lobby_row`, the host `lobby_row_host` + `crown`), `seat_empty` dashed seats breathing, bot icon in empty MIXED seats, the timer ring (code, `gold` → `chip.red`), *Start now* primary |
| **Countdown / VS intro** | the two (or n) plates slide in; `vs_badge` (48 × 32, split blue/red starburst, "VS" drawn as text) pops between two plates; big digits are text at 4× |
| **Final Reveal** | `plaque_back` (card-back lattice) flips to `plaque_face`, the winner to `plaque_gold`; `rank_medals` 1–6 on the left of each plaque |
| **Result** (mockup `extras_pvp_result.png`) | `core/fx/rays` behind a `winner_banner` (80 × 32 b12, winged gold plaque on crimson) with `result.winner_title`; the **podium** (`gui/pvp/podium.png`, 2nd / 1st / 3rd, places as text) with 24-px heads, the `crown` on the winner; confetti; standings as revealed plaques with medals; the payout roll-up at 2× in `bonus`; *Rematch* (primary) / *Taunt…* / *Close* |
| **Mode banners** | No more bets, FINAL BALL, ALL SQUARE, UNDERDOG, EDGE: `mode_banner` (64 × 24 b8) with the text at 2× |
| **Streaks / revenge** | `flame_red` / `flame_gold` (16² × 8), `flame_snuff_0..2`, `broken_chain_0..1` over the winner banner |

---

## 8. Casino Menu shell — "the casino ledger"

The menu is a leather-bound casino ledger lying open in the lobby: a burgundy leather binding with gold filigree
corners and stitching (the **shell**), ruled dark pages, and **bookmark tabs** along the top edge. The Loan Shark's tab
turns the whole book into a gunmetal dossier on a night harbour.

### 8.1 Art (all `core`-owned)

| Asset | Size | Content |
|---|---|---|
| `gui/core/menu/lobby_backdrop.png` | 400 × 240 | art-deco lobby (§2.3) |
| `gui/core/menu/loan_backdrop.png` | 400 × 240 | the harbour (§2.3) |
| `sprites/core/menu/shell`, `shell_loan` | 64² b16 | ledger binding / gunmetal dossier with rivets and hazard marks |
| `sprites/core/menu/page`, `page_loan` | 32² b6 | ruled page (a rule every 10 px, a red margin rule); the centre tiles |
| `sprites/core/menu/tab`, `tab_hover`, `tab_selected`, `tab_loan`, `tab_loan_selected` | 32 × 24 b6 | bookmarks; the selected one is 2 px taller and opens into the page (no bottom edge) |
| `gui/core/menu/tab_icons.png` / `_20` / `_40` | 176 × 16 / 220 × 20 / 440 × 40 | wallet, VIP (diamond badge), contracts (sealed scroll), loan (shark fin), achievements (trophy), challenges (target), PvP (crossed swords), My Casino (casino building), rules (open book), cashier (chip stack), settings (gear) |
| `sprites/core/menu/header` | 64 × 20 b8 | brass nameplate with two bulbs (menu title) |
| `sprites/core/menu/balance` | 32 × 20 b8 | gold-framed well (chip icon + balance) |
| `sprites/core/menu/row`, `row_alt`, `row_highlight`, `row_loan` | 32 × 14 b4 | ledger rows (alternate shading; hover has gold side rules) |
| `sprites/core/menu/progress` + `progress_fill_{gold,green,red,lilac}` | 32 × 10 b4 + 8 × 6 (`tile` scaling) | bars (VIP, contracts, debt) |
| `sprites/core/menu/ach_plate_{locked,unlocked,gold}` | 48 × 24 b8 | achievement plates with a medal well |
| `gui/core/menu/ach_medals.png` | 96 × 24 (4 H) | locked (grey + padlock), bronze, silver, gold (star emboss, reeded rim) |
| `gui/core/menu/shark.png` | 72² | **the Loan Shark**: a shark in a fedora and a pinstripe suit, grinning, one gold tooth, a cigar with a live ember |
| `sprites/core/menu/debt_meter` + `debt_skull` | 32 × 12 b5 + 12² | the debt bar (fill = `progress_fill_red`) ending in a skull |
| `sprites/core/menu/stamp_overdue` | 72 × 28 | red double-ring rubber stamp, slightly rotated (the word is text) |
| `sprites/core/menu/contract` | 32² b8 | parchment with a torn top edge (loan contract, *Sign here*) |
| `sprites/core/menu/offer`, `offer_locked` | 32 × 24 b8 | loan product cards (red stripe; locked = dark, chained) |

### 8.2 Shell layout (L, mockups `extras_menu_wallet.png`, `extras_menu_loan.png`)

- Backdrop at the panel origin, `shell` nine-slice 400 × 240.
- Header plate 112 × 20 at (16, 4) with `menu.title`; balance plaque 90 × 20 at (294, 4) with the chip icon and the
  balance (the same ticker as the HUD, §9).
- Tabs from (16, 28): unselected tabs are **icon-only** 26 × 20 bookmarks (tooltip = the tab name, the existing
  `menu.*` keys); the selected tab is 22 px high at y 26, shows its icon **and** its name, and opens into the page. The
  Loan tab uses `tab_loan*` and its name turns `chip.light`. This fits 9 tabs + the selected label in 368 px, in RU
  too (the longest RU tab name, «Достижения», is 60 px).
- Page: `page` nine-slice at (16, 47), 368 × 177. Content padding 8.

### 8.3 Tabs

| Tab | Content on the page |
|---|---|
| **Wallet** | Balance hero at 2× `gold` with a 1 px ink outline; ledger rows (*Wagered, all time*, *Net today* in `bonus`/`chip.red`, *Biggest win*, *Streak* with the flame); VIP line (20-px badge icon, tier name in the tier colour, *Next: …*, `progress` + `progress_fill_gold`, `3,500 / 5,000`, the perks line); on the right an inset "In your pocket" with chip columns (`core/fx/chip_side_<d>` stacked 2 px apart, `chip_<d>` on top, denomination under each); *Cashier* and *Settings* buttons with their tab icons |
| **VIP** | the six VIP badges (`global.md` §2.3, 16 px at 2×) on a row of `ach_plate` wells, the current one gold, locked ones `ach_plate_locked`; perks as ledger rows |
| **Contracts** | each contract a `row` pair: name + reward, a `progress` bar; completed rows get `menu/stamp` (existing) |
| **Loan** | the dark look (§8.4) |
| **Achievements** | a 2-column grid of `ach_plate_*` (medal from `ach_medals` in the well, title, one-line description; hidden ones `locked` with `???`), a summary line with a gold progress bar |
| **Challenges / PvP** | the PvP hub (§7.2) |
| **My Casino** | ledger rows for the owner's bankroll, edge earned today (green), tables owned; the `my_casino` icon at 40 px |
| **Rules** | ledger rows with the rule names; the difficulty line uses the existing keys |
| **Cashier** (own screen) | the same shell with the lobby backdrop; exchange rows use `row` + chip icons; the breakdown uses the mini chip sprites (`global.md` §4.3) |

### 8.4 The Loan Shark's dark look (mockup `extras_menu_loan.png`)

Selecting the Loan tab swaps backdrop → `loan_backdrop`, shell → `shell_loan`, page → `page_loan`, rows → `row_loan`
(cross-fade 200 ms; reduced motion: instant). Layout: the **shark portrait** (72²) at page (8, 7); a cheeky speech
bubble (`pvp/bubble_cheeky`) with a line that depends on the state (`loan.shark.*`, §14); the name in bold `chip.light`;
the status (`loan.status.*`, existing); the **debt meter** 200 × 12 (fill ∝ owed / (principal × 1.5), capped) with the
skull; the collectors line; the active contract on `contract` parchment with the terms, a signature rule and — when
overdue — `stamp_overdue` with `loan.stamp.overdue` in bold red. Right column: *Borrow* header, product `offer` cards
(`loan.product.*` + `borrow → repay`), locked products as `offer_locked` with the padlock and the VIP requirement,
*Pay all (N)* as a **danger** button, *Pay…* and *Close*.

---

## 9. HUD chip counter and toasts (mockup `extras_hud.png`)

- **Chip counter**: `core/hud/chip_counter` (32 × 16 b6; `_golden` during Golden Hour) sized to the number + 26; the
  animated `core/hud/chip_icon` (12² × 4, a shine sweeping every 16 t) at (3, 2); the balance at (18, 4) in `gold`,
  the `global.md` §4.1.1 ticker. The floating delta sits in a `delta_up` / `delta_down` pill (16 × 10 b4) right of the
  counter and floats up 6 px while fading (existing timing).
- Streak line under it: the HUD flame + `Lucky ×N` in `bonus` (or the cloud + `cool` for unlucky), unchanged content.
- **Toasts** (`global.md` §4.10 `CasinoToast`): three new backgrounds (160 × 32): `toast/achievement` (gold edge, a
  dotted gold rule), `toast/pvp` (lilac edge split blue/red at the top, used by `ChallengeToast` with the depleting
  gold bar along the bottom), `toast/loan` (steel with a red edge). Icon well 24² on the left; title line in
  `gold` (loan: `chip.light`), second line `bone`.

---

## 10. Asset catalogue (Java paths under `assets/burmaldaholic/textures/`)

Sprites live in the GUI atlas; their ids are `burmaldaholic:burmaldaholic/extras/<name>`,
`burmaldaholic:burmaldaholic/pvp/<name>` and `burmaldaholic:core/<dir>/<name>`. Sheets (not in the atlas) are drawn with
`blit(RenderPipelines.GUI_TEXTURED, id, x, y, u, v, w, h, texW, texH)`.

| Group | Sheets (`gui/…`) | Sprites (`gui/sprites/…`) |
|---|---|---|
| Scenes | `extras/{coin,wheel,plinko,scratch}_backdrop`, `pvp/arena_backdrop`, `pvp/arena_backdrop_grudge`, `core/menu/{lobby,loan}_backdrop` | `burmaldaholic/extras/{coin,wheel,plinko,scratch}_{frame,marquee,banner}`, `burmaldaholic/pvp/{frame,marquee,banner}`, `burmaldaholic/pvp/grudge_{frame,marquee,banner}` |
| Coin | `extras/coin_spin`, `coin_glint`, `coin_heat`, `chain_pips`, `coin_mini` | `extras/coin_shadow`, `coin_pad`, `coin_flame`, `coin_flame_soul` |
| Wheel | `extras/wheel_face`, `wheel_rim`, `wheel_bulbs`, `wheel_icons`, `wheel_icons_8`, `wheel_icons_40`, `wheel_parts` | `extras/wheel_hub`, `wheel_flapper`, `wheel_stand`, `wheel_pop` |
| Plinko | `extras/plinko_board`, `plinko_peg`, `plinko_ball`, `plinko_bins`, `plinko_chute`, `plinko_mini_board` | `extras/plinko_bin_hidden`, `edge_glow` |
| Scratch | `extras/scratch_ticket_basic`, `scratch_ticket_gold`, `scratch_card_showdown`, `scratch_foil`, `scratch_foil_showdown`, `scratch_edges`, `scratch_scuff`, `scratch_symbols`, `_20`, `_40`, `_40_win`, `torn_corner`, `explosion_puff`, `flakes` | `extras/scratch_foil_shimmer`, `scratch_foil_shimmer_small`, `foil_final`, `charred`, `trio_frame`, `scraper`, `char_vignette`, `ticket_shadow` |
| PvP | `pvp/pot_chips`, `taunt_icons`, `taunt_icons_20`, `mode_icons`, `mode_icons_40`, `badges` (rival, grudge claw, nemesis, bot, check, padlock), `badges_20`, `rank_medals`, `podium` | `pvp/plate_{you,rival,bot,winner,loser,grudge}`, `head_frame_{you,rival,bot}`, `bot_{easy,normal,hard,mixed}`, `vs_badge`, `pot_plaque`, `pot_glow`, `grudge_left`, `grudge_right`, `record_chip_{lead,trail,even}`, `bubble_{friendly,cheeky}`, `bubble_tail_{friendly,cheeky}`, `crown`, `padlock`, `claw`, `plaque_{back,face,gold}`, `winner_banner`, `mode_banner`, `all_in`, `seat_empty`, `flame_red`, `flame_gold`, `flame_snuff_0..2`, `broken_chain_0..1`, `mode_card`, `mode_card_selected`, `lobby_row`, `lobby_row_host` |
| Menu (core) | `core/menu/tab_icons`, `_20`, `_40`, `ach_medals`, `shark` | `core/menu/{shell,shell_loan,page,page_loan,tab,tab_hover,tab_selected,tab_loan,tab_loan_selected,header,balance,row,row_alt,row_highlight,row_loan,progress,progress_fill_*,ach_plate_*,debt_meter,debt_skull,stamp_overdue,contract,offer,offer_locked}` |
| HUD, toasts (core) | — | `core/hud/{chip_counter,chip_counter_golden,chip_icon,delta_up,delta_down}`, `core/toast/{achievement,pvp,loan}` |
| World | `item/extras/lucky_coin_{heads,tails}`, `entity/extras/{plinko_lamp,plinko_ball,wheel_bulbs}`, `particle/extras/foil_flake_0..3` | — |

Nine-slice borders, frame heights and frame orders are in the `.mcmeta` files next to each sprite (generated).

---

## 11. Mockups

`docs/design/visual/mockups/`, 854 × 480 (GUI 427 × 240 at scale 2), composed by `render_extras.py` from the committed
textures over a blurred, dimmed world, with a vanilla-like 8 px font (`mcfont.py`) standing for translated text and
stand-in faces for `PlayerFaceRenderer`:

| File | Shows |
|---|---|
| `extras_coin_flip.png` | Coin Flip mid-air: spin frame 2 near the apex with motion ghosts, pad and shadow, heads called, history pips, streak |
| `extras_wheel_landing.png` | Wheel stop beat on a Triple: dimmed wedges, gold bulbs, the pop-out with its name plaque, legend, win line |
| `extras_plinko_drop.png` | Plinko drop at row 7 on Medium: hit peg, afterglow, ghost trail, open chute, bin caps with multipliers, risk selector |
| `extras_scratch_half.png` | A Lucky Miner ticket with a half-scratched cell (torn foil autotile, scraper, flakes), prize table |
| `extras_pvp_match.png` | Scratch Showdown grudge match: you, a HARD bot, the grudge rival; banner, pot, taunt bubble, record chip, ALL-IN, current-cell outlines, trios, charred cell, final foil |
| `extras_pvp_result.png` | Result: rays, winner banner, podium with crown, confetti, standings plaques with medals, payout |
| `extras_menu_wallet.png` | Casino Menu, Wallet tab: ledger shell, bookmark tabs, balance hero, rows, VIP bar, chip columns |
| `extras_menu_loan.png` | Loan Shark tab: dark dossier, the shark, debt meter, OVERDUE contract, offers (one locked), danger *Pay all* |
| `extras_hud.png` | HUD chip counter (normal and Golden Hour), delta pill, streak line, the three new toasts |

---

## 12. Implementation notes (Java screen lanes)

- Draw text with the vanilla font and the keys of §14 (1× body, 2× heroes); plaques are sized from the measured text
  (RU budget 1.45× EN, `global.md` §2.2).
- All sheets are code-indexed: frame `k` of `coin_spin` is `u = 64k`; `plinko_bins` tier `t`, lit `l`: `u = 18t, v = 14l`;
  `chain_pips` `u = 16k`; `scratch_symbols_40` symbol `s`: `u = 40s`; `scratch_edges` theme row `r`, variant `m`:
  `u = 4m, v = 4r`; `wheel_icons_*` in the order B C H M D T E X; `tab_icons*` in the §8.1 order; `mode_icons*` in the
  order coin, wheel, plinko, scratch, slots; `taunt_icons*` in the order gg, luck, wow, rigged, again, steel, bye, respect;
  `badges*`: rival, grudge, nemesis, bot, check, padlock; `rank_medals` place 1–6.
- The wheel face rotates about its centre with the GUI pose (`pose().rotateAround`), the rim, bulbs, hub and flapper do
  not. Draw the face with the texture's `blur` off (it is 1:1 pixel art at scale 2; V1 of extras-pvp §0.7 is not needed).
- `wheel_hub` uses a custom frame list in its `.mcmeta`; nothing to do in code.
- No per-frame allocation: all sprites are atlas ids or `Identifier` constants; masks are `long[]` (scratch).
- Reduced motion: sprites keep their static frame 0 (atlas animations still run; they are all ≤ 2 % of the screen and
  flash-safe); code-driven motion follows extras-pvp §0.6.

---

## 13. Changes made to the animation specs

| File | Change |
|---|---|
| `animation/extras-pvp.md` §0 | header note: Java-only; the art is generated by `modules/extras.mjs`; this spec (`visual/extras.md`) is the visual authority and its §10 supersedes §10.1 |
| §1.2 | the coin uses `gui/extras/coin_spin.png` (12 × 64², drawn 1:1), not the 32-px Last Chance sheet at 2×; `coin_pad` is the table line; ghosts while airborne |
| §5.2, §5.3 | Plinko board 272 × 204, pitch **20 × 12**, rows at y = 30 + 12r, peg 7², ball 9², bins 18 × 14, chute 28 × 16 |
| §6.2 | Plinko Battle: no 0.7 scale; mini boards 56² |
| §7.2, §7.3 | tickets 212 × 196, cells 60 × 44, masks 15 × 11 sub-tiles, the scratch-edge autotile |
| §8.1, §8.2 | Showdown cards 86 × 98, cells 24², charred / final foil 24² |
| `animation/global.md` §4.2 | the Casino Menu uses the ledger shell of this spec §8 (bookmark tabs 32 × 24 replace `panel/tab*`) |
| §4.1.1 | the HUD balance uses the chip counter pill and delta pills of §9 |
| §4.10 | three toast backgrounds (achievement, pvp, loan) |

The glyph map (`docs/architecture/animation.md` §6) is unchanged: with Bedrock gone, this spec claims no glyph plane.

---

## 14. Lang keys (new; EN / RU)

Existing keys are reused wherever they exist (`gui.burmaldaholic.extras.*`, `pvp.*`, `menu.*`, `loan.*`, `cashier.*`,
`common.difficulty.*`, the extras-pvp §15 and `global.md` §9 keys). New:

| Key | EN | RU |
|---|---|---|
| `gui.burmaldaholic.extras.coin.your_call` | Your call | Ваш выбор |
| `gui.burmaldaholic.extras.coin.history` | Last flips | Последние броски |
| `gui.burmaldaholic.extras.coin.tally` | Heads %1$s · Tails %2$s | Орёл %1$s · Решка %2$s |
| `gui.burmaldaholic.extras.coin.again` | Flip again | Ещё бросок |
| `gui.burmaldaholic.extras.coin.win_preview` | Win: %1$s | Выигрыш: %1$s |
| `gui.burmaldaholic.extras.wheel.segments` | Segments | Сектора |
| `gui.burmaldaholic.extras.wheel.pop` | %1$s ×%2$s | %1$s ×%2$s |
| `gui.burmaldaholic.extras.wheel.count` | %1$s/%2$s | %1$s/%2$s |
| `gui.burmaldaholic.extras.wheel.you_win` | You win | Выигрыш |
| `gui.burmaldaholic.extras.plinko.top` | Top: ×%1$s | Максимум: ×%1$s |
| `gui.burmaldaholic.extras.plinko.last` | Last balls | Последние шарики |
| `gui.burmaldaholic.extras.scratch.ticket.basic` | LUCKY MINER | ВЕЗУЧИЙ ШАХТЁР |
| `gui.burmaldaholic.extras.scratch.ticket.gold` | GOLD RUSH | ЗОЛОТАЯ ЛИХОРАДКА |
| `gui.burmaldaholic.extras.scratch.match3` | Match 3 to win | Три одинаковых — выигрыш |
| `gui.burmaldaholic.extras.scratch.odds` | 1 in %1$s | 1 из %1$s |
| `gui.burmaldaholic.extras.scratch.creeper_rule` | Three creepers bite! | Три крипера кусаются! |
| `gui.burmaldaholic.extras.scratch.kind.basic` | Basic | Обычный |
| `gui.burmaldaholic.extras.scratch.kind.gold` | Golden | Золотой |
| `gui.burmaldaholic.extras.scratch.new` | New (%1$s) | Новый (%1$s) |
| `gui.burmaldaholic.pvp.vs` | VS | VS |
| `gui.burmaldaholic.pvp.match.step` | Step %1$s/%2$s | Шаг %1$s/%2$s |
| `gui.burmaldaholic.pvp.record_chip` | %1$s–%2$s | %1$s–%2$s |
| `gui.burmaldaholic.pvp.bot_badge.tooltip` | Bot · %1$s | Бот · %1$s |
| `gui.burmaldaholic.pvp.result.standings` | Final standings | Итоговая таблица |
| `gui.burmaldaholic.pvp.result.you_take` | You take the pot | Банк ваш |
| `gui.burmaldaholic.pvp.result.pot_short` | Pot %1$s · cut %2$s | Банк %1$s · доля дома %2$s |
| `gui.burmaldaholic.pvp.result.rematch_ready` | Rematch? %1$s/%2$s | Реванш? %1$s/%2$s |
| `gui.burmaldaholic.menu.wallet.balance` | Balance | Баланс |
| `gui.burmaldaholic.menu.wallet.chips` | chips | фишек |
| `gui.burmaldaholic.menu.wallet.biggest` | Biggest win | Крупнейший выигрыш |
| `gui.burmaldaholic.menu.wallet.biggest_value` | %1$s (%2$s) | %1$s (%2$s) |
| `gui.burmaldaholic.menu.wallet.pocket` | In your pocket | В кармане |
| `gui.burmaldaholic.menu.wallet.next_tier` | Next: %1$s | Далее: %1$s |
| `gui.burmaldaholic.menu.wallet.progress` | %1$s / %2$s | %1$s / %2$s |
| `gui.burmaldaholic.menu.wallet.perks` | Max bet %1$s · Cashback %2$s%% | Макс. ставка %1$s · Кэшбэк %2$s%% |
| `gui.burmaldaholic.menu.pvp` | PvP | PvP |
| `gui.burmaldaholic.menu.cashier` | Cashier | Касса |
| `gui.burmaldaholic.loan.borrow` | Borrow | Взять в долг |
| `gui.burmaldaholic.loan.offer_line` | %1$s → %2$s | %1$s → %2$s |
| `gui.burmaldaholic.loan.locked_vip` | Needs VIP %1$s | Нужен VIP: %1$s |
| `gui.burmaldaholic.loan.overdue_by` | Overdue by %1$s | Просрочено на %1$s |
| `gui.burmaldaholic.loan.collectors_coming` | Collectors are on their way | Коллекторы уже в пути |
| `gui.burmaldaholic.loan.no_new_overdue` | No new loans while you are overdue | Пока есть просрочка, новых займов нет |
| `gui.burmaldaholic.loan.stamp.overdue` | OVERDUE | ПРОСРОЧЕНО |
| `gui.burmaldaholic.loan.contract.due` | Due: %1$s | Срок: %1$s |
| `gui.burmaldaholic.loan.shark.none` | Need a little something? | Нужна мелочь на жизнь? |
| `gui.burmaldaholic.loan.shark.active` | Tick tock, friend. | Тик-так, дружище. |
| `gui.burmaldaholic.loan.shark.default` | Late fees are adding up… | Пени капают… |
| `gui.burmaldaholic.loan.shark.cooldown` | Come back later. | Зайди попозже. |
| `gui.burmaldaholic.visual.scale_hint` | Lower the GUI scale to see the whole table | Уменьшите масштаб интерфейса, чтобы видеть весь стол |
| `toast.burmaldaholic.achievement.title` | Achievement! | Достижение! |
| `toast.burmaldaholic.loan.overdue` | Payment overdue | Платёж просрочен |
| `toast.burmaldaholic.loan.owed` | You owe %1$s | Ваш долг: %1$s |

Length check (1.45× rule): the longest RU labels are «ЗОЛОТАЯ ЛИХОРАДКА» (17 characters ≈ 102 px bold, the ticket
plaque is 110 px), «Три одинаковых — выигрыш» (144 px, the prize card is 140 px wide inside: it wraps to 2 lines,
the card grows by 10 px), «Пока есть просрочка, новых займов нет» (wraps to 2 lines in the 138 px column, as in the
mockup), and the scale hint (wraps under the game object).

---

## 15. Bedrock

Dropped (2026-09-24): no DDUI/ActionForm mapping, no glyph plane (the planned **U+E9xx** plane is not claimed), no
Bedrock textures, entity props or forms. If Bedrock returns, start from this spec's art: every sheet is plain PNG and the
16-px grids are shared.
