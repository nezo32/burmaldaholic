# RO — realistic roulette motion (Phase 2b) — handoff

Branch `p2/ro`, worktree `E:/projects/mods/minecraft/bw-p2-ro`. Continues C2 (inherits all C2 files).
Merged: `p2/c2` @79cde259, `feature/v020-table-ui` @292cbe5a (motion kit, lane gates), `p2/k2` (already in), `p2/w2` @d770e7e0
(its resolved MT/UI merge was reused instead of re-resolving the same conflicts), `p2/mt` @11df787a, `p2/mk` @3bbdbbab
(MK-2 + the AnimClock fix).

## Done

### Logic (pure, `games/roulette/logic`)
- **`RouletteSpin`** (replaces `RouletteBallPath`, which stays for Q to delete): ROULETTE-ANIM-SPEC §2 on the motion kit.
  - Pickup (0–150 ms) → launch at 250 ms against the rotor (seeded ±20°, 756°/s ±6 %) → stage-A exponential orbit
    (result-free) → stage-B orbit on `DecaySolver` (put on a `Track` with `Seg.decay`) → hunting (rising wobble, roll
    grains) → drop spiral (constant-accel fall down the stator, angular whip) with a seeded deflector hit (p 0.55, exactly
    on a diamond, angle dip windowed to 0 at contact) → rattle on `BounceChain` in pocket units (4–7 hops, frets/floors,
    kick-back, scatter 5–16 pockets) → settle rock (1–2 clicks) → riding the rotor.
  - Solve: tries drop times nearest the natural one (±150 ms), then later drop times whose actual rattle still rests in
    time, then a tighter tail (×0.85, ×0.72) for the shortest spins, and only then a wider scatter (the fallback; never
    used in the full sweep).
  - Deterministic maths (`StrictMath` / `DetMath`), allocation-free `sample()`, events as `MotionEvent`s.
- **`RotorModel`**: closed-form rotor — push (outCubic, 400 ms) from the launch speed to ±150°/s, then coasting toward
  ±36°/s (τ 14 s). Never resets; direction alternates each launch.
- **`RouletteDealer`**: the launch bookkeeping, quantised to the wire (server and clients sample the same model).
- **`RouletteSync` v2** (17 ints): + launch tick, rotor angle (centi-deg), rotor speed (centi-deg/s), direction
  (±1 pushed / ±2 coasting). v1 (12 ints) still decodes (launch = spin start − 20, rotor from the seed, dir −1).
- **Server** (`RouletteTableBlockEntity`): seed + launch at NO_MORE_BETS (`dealer.launch`); the result is still drawn at
  NO_MORE_BETS → SPIN by the same single `OddsService.fair().nextInt(37)` in `RouletteRound` (no RNG/payout hunk). The
  screen tag gets `launch_tick`, `rotor_a`, `rotor_w`, `dir`, `pushed`. Timing unchanged (overlay + chat at the end of
  SPIN). **D2: `spinTicks` default 140** (`RouletteConfig`, `RouletteRound.Timings.DEFAULT`, CONFIG.md).

### Screen (`RouletteScreen`, `RouletteWheelView`, new `RouletteChipFx`, `RouletteTextures`)
- Every frame samples the spin at ms since the launch (`TableClock`; `testClock` / `testResultClock` for tests). The
  spin is built at the launch (pre-result) and rebuilt with the result (identical before the draw).
- Wheel: 222-frame heads at 104/144 (frame index shared by all sizes, ball rides the drawn frame); the 208 head is ONE
  digitless frame turned by the pose with the numbers drawn upright per frame (`roulette/wheel_digits`) — **D8**:
  104+144 sheets = 16.1 MB VRAM, lazily loaded by the texture manager, released when the screen closes; the 208 set
  would have been +20.5 MB, so it is the runtime path. Jar +~350 KB.
- Ball: height lift + contact shadow (`HeightShadow.ROULETTE`), shutter streak (`Streak`), HIT / POCKET frames,
  physical-px snapping (`SnapDraw`); deflector diamond flash + 4 gold sparks at the ACTUAL diamond (fixes the 84 px bug);
  2-frame sparks on every rattle landing; pocket glow per size; result badge per size.
- Spin focus: the wheel slides/grows to the largest native size × integer (`RouletteFocus`, `ArtScale.pickNative`)
  during NO_MORE_BETS (0–350 ms) and cross-fades to the native sprite (350–470); returns in RESULT 0–400. The layout dims
  under the spin; the gold winning cell stays bright. Loupe at rest: the result pocket upright with its number and
  neighbours, the ball in it.
- Sounds: an `EventCursor` over the spin's events (rotor push, launch, roll grains, deflector, contact, frets, floors,
  settle clicks, rest; hitch rule silent > 150 ms; late join valid at any t), and the `roulette_roll` loop
  (`FxSounds.loop` SCREEN priority, pitch/volume from the ball speed, fades at the drop). New sound ids in
  `sounds/roulette/sounds.json` + `SoundCatalog` + subtitles (EN/RU).
- Chips: placement flight from the armed Dock chip (Bézier, 180 ms, drop + dust puff, `chip_place` on landing), call bets
  fly to each spot, rebet/×2 staggered, undo/clear fly back, others'/bots' stacks drop with outBounce, own-stack growth
  drop, 1 Hz shimmer during the spin.
- RESULT beats (local time): focus return 0–400, dolly 400–650 (sound on contact), rake 650–1070 (losers ride the bar,
  fade), payout discs 1100–1500 (≤ 4 `chip_stack`), +N pill, flight to the balance 1500–1920, banner/celebration at 1920,
  history pill 2600. Reduced motion: static wheel (last result on top), ball hidden until it fades into the pocket at
  rest, no focus/streak/sparks/loupe/shimmer, dolly without drop, rake as a fade, balance at once.
- Idle (D3): the rotor coasts on the last launch's model and the last ball rides its pocket; glow fades over 1.5 s.

### In-world (`RouletteBoardRenderer`, `RouletteChips`, `RouletteWorld`)
- **VQA-INWORLD RO 1 fixed: one wheel.** The wheel is drawn footprint-local at `FeltFrame.wheelCx/wheelCz`, in the
  furniture's bowl (floor 13.5/16), Rw = the bowl opening: rotor disc (the 208 head art) + turret, stator cone (24
  quads), ball track, track wall with a lip tucked under MT's raised rim, 8 brass diamonds. The rotor stands above the
  felt height, so MT's static floor art is fully covered (no hook needed, per MT). Compact tables keep a low wheel on the
  block top.
- Ball: camera-facing billboard with height and a translucent contact shadow; 2 streak billboards at LOD A when fast;
  diamond flash; pocket glow; dolly billboard; at idle the ball rests in its pocket.
- LOD (camera distance): A ≤ 8 (everything), B ≤ 16 (no streak/flash/chip motion), **C ≤ 32: no ball before rest**
  (D12 spoiler fix; the board line names the result only once the ball rests). Chips: new stacks drop, losers slide
  toward the dealer and fade (translucent, 40 ms stagger).
- Sounds within 24 blocks via the spin's event cursor; the roll loop only for the nearest spinning table ≤ 12 blocks
  (one WORLD loop); muted while this table's screen is open.

### C2 leftovers / C2 tester (T-C2) items fixed here
- V5 / C2 leftover: ROOMY layout grows (cell cap 54, column may hold the 208 wheel) and the layout block is centred on
  the felt.
- V1: RESULT hint "Paying out · bets open next" (never "click the layout"). V2: outside labels (dozens, even-money words,
  red/black diamonds) are drawn left of the stack column; stacks and value tags never cover them (unit-tested at every
  size ≥ 427). V6: the banner prefers the felt under the call row, call bets are obstacles. V7: the spectator banner's
  number + colour word in the pocket colour. V9: casino-mode-off refund line "The casino closed this table…" (not
  "server restarted"). T1/T2: lane C test disables contracts and presses Repeat until it queues after a celebration.

## Verified (exact commands, JDK 25.0.4.1, in `java/`)
- `./gradlew --no-daemon --max-workers=4 -Dorg.gradle.jvmargs=-Xmx3g compileJava compileClientJava compileGametestJava`
  (26.2) and the same with `-Pmc=26.3`: OK.
- `test --tests 'dev.nezo.burmaldaholic.games.roulette.*' --tests '*RawInputGuardTest' --tests '*CoreAnimVectorsTest'`:
  BUILD SUCCESSFUL. New: `RouletteSpinTest` (RO §9.1 1–8), `RouletteRoundTest.launchAtNoMoreBetsDrawOnceAtSpinSettleAtSpinEnd`,
  `RouletteFocusTest` (§9.1-9 sizes, §9.1-10 reduced motion, ROOMY growth), `RouletteWorldTest.theWheelSitsInTheFurnituresBowl`,
  `RouletteLayoutTest.stacksAndTagsNeverCoverOutsideLabels`, `RouletteFlowTest` (result hint).
- Full sweep of §9.1-1 (37 results × 2048 seeds × spinTicks {40,60,100,140,200,300} × dir ±1 × 8 rotor states = 7.3 M
  spins): all end in the drawn pocket, rest ≤ end − 300, no fallback; 48 s with `-Droulette.fullSweep=true` (the default
  test run uses 48 seeds × 2 rotors).
- `CoreAnimVectorsTest` vectors regenerated (`FX_DUMP_VECTORS=1`) for the 5 new sound ids only.
- Tools: `npm run gen:assets && npm run check:assets && npm test` green (70 tests incl. 6 new RO art tests).
- Client run 26.3 (see the bottom of this file for the final run).

## Not done / open
- **V4 (celebration covers the winning cell):** `CelebrationOverlay` is not an RO file; it needs an anchor/obstacle API
  (request below). **V3/V8**: kit (K2/MK). **V9 board text** while the casino is closed: needs a closed pub/sync state
  (C2 pub + W2 board) — the refund line is fixed.
- The in-world number layout print (size/placement) is W2's print item (VQA W2 1); the stacks follow
  `RouletteWorld.PRINT`. If W2 moves the grid into its felt decal, drop the `layout_w_*` quad here and update
  `RouletteWorld.PRINT/LAYOUT_*` to match.
- In-world rotor digits are baked upright on the rotor texture and turn with it (radial digits would need text rotation
  in the generator).
- Not run: 26.2 client (policy: 26.3 only), full `build`.

## Requests to other packages
- MK/K2: `CelebrationRequest` anchor rect / obstacles so a NICE+ celebration never covers the winning cell (T-C2 V4);
  primary/Space should skip AND act (V3).
- W2: roulette felt print scaled to the layout felt (VQA W2 1) — keep `RouletteWorld.PRINT` in sync, or tell RO.
- MT: keep the bowl floor at 13.5/16 and the rim inner radius ≥ 6.5 px (RO's track wall is at 0.40 block, its lip
  0.416); if the rim height changes from 17/16, `RouletteWorld.Y_WALL` follows.
- Q: delete `RouletteBallPath` (+ test), the 74-frame `wheel_head` / `wheel_head_blur` sheets.

## Risks
- `/tick freeze` is used by the client test to hold the server mid-SPIN; the screen's test clocks then pick each beat.
- Loupe uses a scissor in pose space (works in the run; if a future kit changes GUI pose handling, recheck).
