# CM — craps motion (Phase 2b) handoff

Branch `p2/cm` (from `p2/d2` @f4e1790). Merged in: `feature/v020-table-ui` (motion kit, lane gates), `p2/k2` (twice; K2a/K2b/RS
+ StatusFitTest), `p2/dca` (twice; dice kit + the atlas / `dice_world` rework), `p2/mt` (3×2 tables), `p2/w2` (board on multi-block
tables: the in-world craps renderer needs `feltPose`), `p2/mk` (MK-2 + the AnimClock recursion fix). Spec: CRAPS-ANIM-SPEC (CS),
PHASE2B §4 CM. Owns all D2 craps files (T-D2 fixes are in this branch too).

## What changed

### Pure core (`games/craps/present`, `games/craps/logic`)
- **`CrapsFeltFractions`** — the one fraction table shared by `CrapsLayout`, `CrapsBoardGeometry`, the generator's
  `printGeometry()` and the dice arena: rows (place 0.27, come 0.245, field 0.18, don't pass 0.115, pass rest), Don't Come 0.17,
  rest zone from 40 % of the COME row to the total pill at 72 %, dice home = the outer 9 % of the Pass line, wall strip heights
  (GUI T1 6, T2 6, T3 10, ROOMY 10; print 6). Arena 52 × 21 tray units (1 u = die half-edge; CS 26 × 10.5 die edges).
- **`CrapsThrow`** — builds the DCa `DiceTray`: the back wall with rubber pyramids (`mustHit`, restitution 0.58, pitch 0.44 u),
  three padded rails, the COME-row rest zone, keep-outs = the total pill / caption / come stacks band and the puck where it sits
  during the throw (one tray per `pointBefore`, cached). `ThrowSpec.hand` at dice home (die 0 inboard so die 1 clears the rail),
  `solve(side, seed, d1, d2, pointBefore)` with a seeded target `targetMs(seed)` ∈ [1560, 1880] ms (CS §5: rest ≈ 1700) and a
  deterministic re-seed guard (≤ 4 derived seeds) when the relaxed solver pass throws faster than 110 u/s.
  `sideOfSeat(seat, seats)`: left half of the seats throws from the left end.
- **`CrapsBeats`** is now `CrapsBeats.of(restMs[, speed])` with the same field names (BADGE rest+80, BANNER +130, PUCK +230,
  CHIPS +330, TRAVEL/PAY CHIPS+300, COLLECT +900, STICK +1000, IDLE +1770); chips/stick/idle are LOCAL (TURBO ×1/1.5, SLOW ×1.4
  capped at IDLE ≤ rest + 2600); throw + puck SHARED. `revealTicks(restMs) = ceil((restMs + 330) / 50)`. Puck: 7 frames
  (rise 80, flip 160, glide 260 on a 6-px arc, land 80 OUT_BOUNCE), `flip` phase for the in-world turn-over.
- **`CrapsSync` v2** (15 ints): `shooterSide`, `restMs`, `animV = 2`; v1 still decoded (side from `shooter_dir == 1`, restMs 0 →
  client solves).
- **`CrapsChipBeats`** — pure plan of the chip motion from `last_res`: take (one wave left to right, 40 ms stagger, raked to the
  bank at the wall's centre, fade in the last 30 %), come/don't-come travel (arc), pay (≤ 4 greedy discs from the bank to a
  column beside the bet, 60 ms stagger), push / odds-off back to the tray, collect to the balance (yours) / slide off the felt
  (others'), net pills. **`CrapsStick`** — reach (160), hook (60, the far die first), drag home (400, faces kept, yaw squares up
  mid-drag), withdraw (140).
- **`CrapsLayout`**: `backWall`, `arena` + `arenaMap()` (StageMap: wall plane = `backWall.bottom()`, shooter rail = felt bottom),
  `diceZone` (no slots, no snap), `diceHomeL/R`, `die` (14/18/22, **30 at ROOMY**), `stick`, ROOMY puck 26 / pill 34, a reserve for
  the Dock's hint strip (`of(grid, reserveBottom)`), and `valueTag(...)` (T-D2 V1: pills off every glyph / dice / total).
- **`CrapsView`**: `settling` (Roll waits until IDLE, D5) + `withBets` / `withPoint`.

### Server (`CrapsTableBlockEntity`) — timing/cosmetic only, no money/RNG hunk
- Every `REVEAL_DELAY_TICKS` use → `revealTicks` (chat lines, bot quip, hot-shooter toast, bet window `lastRollTime + revealTicks
  (+ betWindow)`); the throw is solved once per roll after the RNG draw (cosmetic seed) with the shooter's side from the human
  seat index (never the bot seat model at the roll: that consumed the bots' safe point).
- State tag `shooter_side`, `rest_ms`, `anim_v`; sync v2. At rest: CRIT ×3 on a natural, SMOKE ×4 on a seven-out (clients without
  the mod). T-D2 V8: the preset VIP tier is checked in `sit()`.

### Screen (`CrapsScreen`)
- The dice are the kit atlas, physical-px snapped (`DiceDraw`), shadows, after-image > 9 rad/s, layers per CS §4.2 (airborne dice
  over chips and the puck), rest glint, rattle in the hand at home while Roll is pressed, dust / clack / wall sparks, the hit
  diamond flash (white one frame, gold fade, glow on the neighbours so a die covering the hit pyramid does not hide it), the
  1-px wall jolt, `DiceAudio` through the event cursor (hitch-silent).
- Total pill pops at BADGE, seven-out shake + vignette (flashes), point ring pulses / point-made flash + confetti, puck 7 frames +
  lift shadow + `craps_puck_lift` / `craps_puck`, chip beats + stick + collect flights, placing flight (sound on landing, fly
  back + `ui_deny` when refused), banner at CHIPS, balance / win sound / celebration at COLLECT. Reduced motion: dice fade in at
  rest, static HOT, puck cross-fade, final chip state + pills 1.2 s, stick = cross-fade.
- T-D2: V1 pills (layout), V2 strings, V3 COME word hidden under the banner, V4 puck word scaled to the face, V5 felt above the
  hint strip, V6 the bets as they were while the throw is shown (no "Bet 0"), V7 ring follows the puck, P-1 `reason.min_raised`,
  and the status shows the point BEFORE the throw while the dice fly (it showed the new point: a spoiler).

### In-world (`CrapsBoardRenderer`, `CrapsBoardGeometry`)
- Print 256 × 176 with the wall strip (generator), drawn translucent (the soft shadows / Don't Pass band went black under
  cutout); a raised back-wall box with the pyramid face + padded top + end caps; the hit-diamond flash (emissive); a chip groove on
  the players' rail of a full table; the puck ON on the point's number / OFF in Don't Come, turning over about the axis across its
  travel; DCa `DiceWorldDraw` cubes (support-height centre, blob shadow), the stick dowel; stacks taken / paid / travelled /
  collected from the public stacks before and after the roll (outcomes from the public dice and bet kinds; money is the server's).
- LOD: ≤ 8 full; 8–16 motion, no shadows / stick / turn; 16–32 **no dice before the rest** (spoiler fix) then at rest; the board's
  point line follows the puck (no spoiler).

### Art (`tools/assets/modules/tables/craps_v2.mjs`)
`back_wall_{4,6,8,10}` (IDLE / HIT / HOT), `stick_shaft_{3..6}`, `stick_hook_{3..6}`, `puck_{12,16,20,26}` (7 frames),
`entity/craps/{board_print,wall,puck}.png`; `craps-v2-art.test.mjs`.

### Sounds / strings
`craps_puck_lift`, `craps_stick` (SoundCatalog, `sounds/craps/sounds.json`, `core_anim.json` id list), STRINGS `craps › motion
sounds`; 9 craps status strings shortened (StatusFitTest list emptied of craps); `reason.min_raised`.

## Verified
(see the run log section below)

## Not verified / open

## Requests
