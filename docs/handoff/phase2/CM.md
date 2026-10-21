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

### R3 / S1: craps on a bad connection (owner playtest 1)
- **`CrapsRollClock`** (pure, `games/craps/present`): one clock per table on this client, shared by the screen and the in-world
  board through `client/CrapsRollClocks` (registry by BlockPos). Roll pressed → the hand rattles with NO time cap until the
  result (or a refusal: `showError`, or the Roll's ack answered without a new roll). The throw's t = 0 is fixed ONCE per roll
  counter on the local clock; bunched packets / TableClock re-syncs never move it. Spectators start the throw from home on
  arrival (late = delayed, never jumped into); only a first sight (screen opened / table came into view) or a roll older than
  6 s takes the server's age. Clock source is a `Source` (`CrapsRollClocks.source(...)`) for SY's SmoothClock.
- **`CrapsHand`** (pure), owner's Dice house-mode pattern: the rattle loop passes through ONE release pose every 120 ms cycle;
  when the result is in, the hand keeps rattling to the first exit ≥ max(arrival + 80 ms, 240 ms) (wait ≤ one cycle + 80 ms)
  while it settles into the plain solver's pose at t = 0 (one fixed turn `start·exit⁻¹` applied by a growing share, the pair
  spread 0.6 u apart mid-settle so turning corners never touch), so the exit pose IS the solver start pose (C0) and the normal
  deterministic `CrapsThrow.solve` throw follows, identical on every client. No flight blend (DCa's `Initial[]` overload was
  removed on p2/dca @e54a9129, merged here).
- Hand spacing: DCa's `ThrowSpec.hand` (p2/dca @b28b83a4, merged) is the single source: dice 3.5 u apart, die 1 toward the table
  centre. CM: `RELEASE_INSET = 1.8` u (die 0 outboard), release y capped at `ARENA_H − 2.4` (die 1 stays R_PLANE off the
  shooter's rail), `CrapsLayout` dice home `ceil(2.75·die)+2` wide. Golden `dice_roll.json` regenerated ONCE from the merged
  code (includes CM's crapsCm fixtures).
- `DiceRollCache` keys → `CrapsThrow.cacheKey(...)` (DCa's full key: faces, target, tray).
- Screen: `rollPressedAt` / the 900 ms cap and `stale()` are gone; storyboard t = the shared clock; the board's result line no
  longer shows while t < 0 (that is now "hand still settling", not clock skew).
- Under a client GameTest frozen clock the origin is pinned to `roll_time · 50` (`CrapsRollClocks.pinUnderTest`), so the
  storyboard frames of `CrapsAnimClientGameTests` stay deterministic.

### R3 / T-D2 proofs (tests, no screenshots)
V1 `CrapsLayoutTest.valuePillsStayOffThePrint`; V2 + 9 frozen strings `StatusFitTest` (craps list empty); V4
`CrapsLayout.puckWordScale` + `puckWordFitsTheFace` (also fixed: tiny puck at low k overflowed, now the exact fitting scale);
V5 `feltEndsAboveTheHintStrip`; V6 `CrapsFlowTest.theDockKeepsTheStakeWhileTheThrowIsShown`; P-1 `CrapsFlow.minRaised` +
`aChipUnderTheMinimumNamesTheMinimum`; V8 server GameTest `vipPresetRefusesTheSeatBelowTheTier`; G1 natural roll in
`botBetsOnTheBoardOnTheirSeats`. V3 (COME word hidden while the banner is up) and V7 (ring follows the puck) are screen-state
code with no pure seam; not separately asserted.

## Verified (26.3 only, per owner)
- `./gradlew --no-daemon -Pmc=26.3 test --tests 'dev.nezo.burmaldaholic.games.craps.*' --tests 'dev.nezo.burmaldaholic.core.anim.dice.*'
  --tests '*StatusFitTest' --tests '*RawInputGuardTest'` → all green (craps 125 incl. `CrapsRollClockTest` 7: result 0/300/800/1500 ms
  late + bunched/re-synced packets → origin fixed, per-ms motion within the throw's own, exact solve + server faces at rest;
  release pose = solver start pose (C0); any arrival phase waits ≤ cycle + settle; no overlap: hand/settle SAT on the cubes, and the
  whole solved flight keeps centres ≥ 2 u (asserted, FAILS below), 24 seeds × arrival phases; no time cap (9 s); refusal cancels; clock-source swap keeps the throw).
- `-Pmc=26.3 compileJava compileClientJava compileGametestJava runGameTest -Dfabric-api.gametest.filter=craps` → BUILD SUCCESSFUL,
  "All 16 required tests passed" (re-run after the b28b83a4 merge: 16/16) (incl. the new V8 `vipPresetRefusesTheSeatBelowTheTier` and G1).

## Not verified / open
- Client lane (CM / D) NOT run this round (machine load; tester to run `BURMALDAHOLIC_CGT=D,CM`). New client assert in
  `CrapsV2ClientGameTests.rollOnce`: the throw leaves this client's hand on the shared clock (screen == board clock), not snapped.
- No lag-injection client test yet (SY's NetLag hook is not on this branch).
- V3 / V7 have no pure seam (screen state); not separately asserted.

## Requests
- **SY**: swap the clock with `CrapsRollClocks.source(SmoothClock::nowMs)`; the server lead (5 ticks) is not added here
  (`CrapsTableBlockEntity` roll_time), spectators could then use the SY anchor instead of "start on arrival".

## Fix round after T-CM (FAIL → this round)
Tester commits cherry-picked first (`30f1b489`, `43e34f31`, `64994879` from `test/cm`).
- **M1 / M1b (P1)** the NEXT shooter's side is synced: `CrapsTableBlockEntity.nextSide()` (the current shooter's human seat →
  `sideOfSeat`; no shooter = stay) in the state (`next_side`) and in `CrapsSync` **v3** (16 ints; v2 decodes next = shooter side).
  `CrapsRollClock.nextSide(side)` owns dice home: the first value is taken as is, a change moves home along ONE fixed
  non-crossing path over 1100 ms (smoothstep, ≤ 0.07 u/ms; reversals continue from where it is). Screen, board and the hand all
  read `rc.home(...)` / `rc.homePose(...)`; the stick drags to it. The hand's home is frozen at the result. Desync fallback
  (a throw from the other end than the hand, e.g. a fresh table with no side known): the settle lasts `dist·1.5/0.045` ms and one
  die passes behind the other (`CrapsPair`), so no jump and ≥ 2 u.
- **M2 (P1)** `CrapsStick`: line-up in REST order; hook and drag move both dice on one ease with a `CrapsPair` push (the smallest
  sideways push, toward the back wall, keeping centres ≥ min(1.1 die, end gaps)). Tester: 0/40 overlap, closest 2.21 u; mine:
  600 cases (3 scales × 200 seeds, home at either end) ≥ 2 u every ms, closest 2.13 u.
- **M5 (P1)** `CrapsScreen.mouseClicked`: a click in the chip beats skips them and, on an ENABLED hit target (felt spot, chip,
  button), also acts on the settled table (K2 rule). Lane D "Pass again" no longer needs a wait.
- **M3 (P2)** chosen: DISABLE with a reason. `feltClosed()` = throw shown OR this client's hand busy (Roll pressed → throw
  leaves the hand): the view is `rolling`, so felt clicks flash `reason.rolling` and the status says the dice are rolling; refreshed
  right at the press.
- **M4 / P3** `CrapsRollClocks` keyed by (dimension, pos), cleared on disconnect; a roll counter going backwards = a new table
  (first sight). Screen / board cache their clock ref (no per-frame key). Allocations removed: `CrapsHand.home/shake/settle`
  (thread-local scratch), `CrapsThrow.release` (precomputed, `release(side, die, out)`), `CrapsScreen.chips` tags list,
  `drawBets` (arrays instead of map + string keys), `prevBets()` (parsed once per state), `CrapsBoardRenderer.stacks` (no list /
  `Spot` records / rail arrays).
- Tester test change: `CrapsCmTesterTest.shooterOnTheOtherSideDoesNotJump` spectator half rewritten to the new API (it computed
  `home(RIGHT)` vs the LEFT throw statically, unfixable by design); it now asserts the synced home move (no jump, ≥ 2 u, ends at
  the throw's start). Shooter half unchanged (passes via the fallback). New: `CrapsCmFixTest` (5), `CrapsSyncTest` v2 case,
  GameTest `nextSideFollowsTheNextShooter`.
- Under a frozen client-test clock `CrapsRollClocks.pinUnderTest` also ends a dice-home move (`finishHomeMove`), else the
  move never progresses (client run 1 failed `probeHome` on that; product behaviour unchanged).

### Verified (26.3 only)
- `test --tests 'dev.nezo.burmaldaholic.games.craps.*' --tests '...core.anim.dice.*' --tests '*StatusFitTest' --tests '*RawInputGuardTest'
  --tests '*CrapsSpecTest'` → 159/159 green, including all 8 `CrapsCmTesterTest` (M1, M1b, M2 now pass) and the 5 `CrapsCmFixTest` tests.
- `runGameTest -Dfabric-api.gametest.filter=craps` → "All 20 required tests passed" (19 + `nextSideFollowsTheNextShooter`).
- Client `BURMALDAHOLIC_CGT=D,CM runClientGameTest` (2 runs; logs `.burmaldaholic-tools/CM-fix-client{,2}.log`). Run 1: lane D
  all green (M5 fixed), CM `probeHome` failed (the frozen clock, above). Run 2 @027955cc → **BUILD SUCCESSFUL**, both lanes green:
  V7 ring gold 76/76 when on, 0/76 after the seven-out; late result ready 2505 ms → release 2640 ms, fromHand.
- **Not verified:** V3 passed without testing anything. The probe logs "V3 not exercised: no banner at the seven-out shot", so the
  COME-under-banner check never actually ran. The probe needs a frame where the banner is up.

## Re-test round 2 (T-CM M1c) — tester commit 624cb220 cherry-picked (CRLF normalised to LF)
- **M1c** `CrapsRollClock.onRoll` spectator path (not a first sight, not stale): the dice glide from where they rest at dice
  home NOW (also mid-slide, or with the side change bunched into the roll) into the throw's start pose, then the throw.
  The glide takes `max(120, dist·1.5/0.045)` ms and uses a `CrapsPair` passing path when the order swaps. Dice home's slide
  ends at that roll. This also fixes the old 1.6 u lift jump from rest to the hand pose at every spectator throw. `pin()`
  (tests) drops the glide. Dev test `spectatorsStartFromHome…` now expects the ≥ 120 ms glide (t < 0 on arrival).
  New `CrapsCmFixTest.aSpectatorRollDuringTheHomeSlideGlidesIn` (gaps 0–1200 ms: ≤ 0.14 u/ms, ≥ 2 u, exact faces).
- **V3 at 480: left as intended.** `CrapsLayout.bannerRegion()` is the free band of the COME row left of the dice. By contract
  (ui-v2 §2.1) it may cover the printed COME word and never a number, a stack or the dice, and the word is hidden while the
  banner is up. Bet labels, point numbers and stacks stay readable.
- Verified (26.3): unit set 161 tests green (`DiceKitTest` "worst solve" timing failed once under load, then passed when re-run
  alone). Server craps: 20/20. Client D,CM @ the head (`CM-fix-client3.log`): BUILD SUCCESSFUL. V3 forced banner: COME ink 0 with
  the banner, 252 without. V7: ring 76/76 gold when on, 0/76 after the seven-out. Late result: release 2640 ms.
