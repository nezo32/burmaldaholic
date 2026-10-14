# MK — shared motion kit (Phase 2b) handoff

Branch `p2/mk` (from main v0.1.3). MKa announced at `51dd811`. MK is new files plus small additive hooks in owned
client files (`AnimClock`, `FxSounds`, `TableClock`, `TableGfx`). It does not touch game, logic or BlockEntity code,
so there is no money/RNG diff.

## API summary

Pure package `dev.nezo.burmaldaholic.core.anim.motion` (common source set, no MC imports). Time is in ms and rates are
per second, unless a javadoc says otherwise.

| Class | What it does |
|---|---|
| `Track` | Multi-channel closed-form segments: `HOLD, LINEAR, CONST_ACCEL, BALLISTIC, EXP_DECAY, DAMPED_OSC, PIVOT`. Built with `Track.builder().start(ms, p0...)`, then `.seg(dur).<kind>(ch, ...)...done()`. Shortcuts: `hold`, `linearTo`, `ballisticTo`. Each segment starts at the previous end value, so the track is C0 by construction. `sample(ms, out)`, `sample(ms, ch)`, `velocity(...)` and `segmentAt(ms)` are O(log n), allocation-free and pure in t. `PIVOT` (`pivot`/`pivotBy`) rotates two channels about an edge, so the die centre rises to √2/2 |
| `Ballistic` | `fallTime(vy0, dy, g)`, `vxFor(dx, dt)`, `v0For`, `pos`, `vzForApex`, `apexTime`, `apexOfHop`, `hopTime`, `flightTime(z0, h, g)`. Times are in seconds |
| `DecaySolver` | `solve(ωS, ωD, T[s], target, kNat, kMin, kMax)` returns a `Solution(k, ok, theta, residual, ...)` with `omega(s)`, `angle(s)`, `decel(s)`. It also has `theta`, `reach`, `bigG` and `kForDecel` (k_nat from the stage-A deceleration, RO §2.3). Out of reach gives `ok=false`, k at the bound and a `residual`; the caller then shifts t_D (RO §2.4 step 4) |
| `BounceChain` | `plan(start, v0, target, Lattice, Params, MotionRng)` gives n hops with decaying airtimes and apexes. Landings snap to `Lattice.pockets()` and alternate FRET/FLOOR; the last landing is exactly `target`. Optional kick-back of −1 off a fret; skids of 0–90 ms. Presets: `Params.roulette()`, `plinkoBin()`, `chipSettle()`, plus `withTimeScale(c)`. Also `sample(ms, out[4])`, `appendTo(Track.Builder, chAlong, chZ)`, and per-hop `landMs/landAt/landClass/isKick/apex/airSpeed/landVz` |
| `MotionEvent` | `record(tMs, kind, obj, strength, x, y, aux)` with `sort`, `sorted`, `shifted` |
| `EventCursor` | `advance(nowMs, Listener(e, silent))` fires each event once and in order. `reset(toMs, silent)` covers skip, late join and rewind. `bind(events)` reuses a pooled cursor. Hitch rule: an event more than `LATE_MS = 150` late fires silently; the constructor takes `lateMs` (e.g. 120 for the PL/DI audio rule) |
| `MotionRng` | `of(seed, stream, idx[, sub])` = `SeedMix.FxRng(SeedMix.mix(seed, SeedMix.hash(stream), idx[, sub]))`, so it has Bedrock parity. Methods: `range`, `rangeInt`, `chance`, `sign`, `jitter`, `weighted` |
| `Streak` | `samples(Sampler, t, frameDt, maxGapPx, outXY)` gives shutter samples at most `maxGapPx` apart (≤ 8, shutter 8–33 ms) and none below a 3 px arc. Also `arc`, `alpha` |
| `HeightShadow` | `apply([Style,] z, k, Out)` gives sprite lift, shadow offset, alpha, size and a 3-size index. Styles: `ROULETTE` (RO §3.5), `DICE` (DI §4.2), `CRAPS` (CS §4.2) |
| `ArtScale` | `pick(artW, artH, boxW, boxH, k)` returns `Result(r, n, integral, scale, w, h)` with `originX/originY` floored to 1/k. `pickNative(sizes, avail, k)` covers the roulette focus wheel (largest native size, then integer ×n) |
| `PixelSnap` | `snap(gui, k)`, `floor`, `phys`, `at(origin, art, scale, k)`, `snapF` |
| `StageMap` | The CS `ArenaMap` (PHASE2B §3.1): a per-axis affine map plus `zScale`, `mirrored(w)` for the right duel lane, and inverses |
| `Oscillation` | `dampedSin`, `dampedCos`, `envelope`, and `shake` (cut to exactly 0 when finished) |
| `SpeedPolicy` | `SHARED` / `LOCAL` with `scale(ms, speed, clientPostsResult)`, `duration`, `factor` and `speedOf(pct)` (D1) |
| `FxGate` | The reduced-motion and flashes gate, with `Kind {SHAKE, PARTICLES, FLASH, STREAK, MOTION}`, a `Recorder`, and a `Counter` for tests |
| `SoundMixer` | The pure voicing rules: a 30 ms merge window (+0.1 volume per merged offer), per-id `limit(id, n/s)`, `always(id)`, and `leadVoicing(crowdAbove, othersEvery)` + `crowd(n, lead)` |

Client package `dev.nezo.burmaldaholic.client.fx.motion`:

| Class | What it does |
|---|---|
| `MotionClock` | `levelMs(partial)`, `msSince(startTick, partial)`, `msSince(TableClock, ...)` and `localMs()`. `testOverride` is a `LongSupplier` of level ms (null in production). `TableClock.nowTicks` honours it too |
| `MotionFx` | `shake/particles/flash/streak/motion(what)` go through `FxGate` with `FxSettings.reduceMotion()/flashes()`. `recorder` is the test hook |
| `GuiShake` | `push(Matrix3x2fStack, a, tauMs, hz, tMs, k[, what])` / `pop` (`what` names the effect for the test recorder); `offset(...)`. Gated, and snapped to physical px. The 26.x GUI pose is a `Matrix3x2fStack`, not a `PoseStack` |
| `SnapDraw` | `blitF(g, sprite, x, y, w, h, k[, argb])`, `regionF(...)` and `frameF(...)`: float positions snapped to 1/k, then a 1:1 blit. Scale overloads `blitF(g, sprite, x, y, w, h, scale, k, argb)`, `regionF(..., argb, scale, k)`, `frameF(..., argb, scale, k)` draw on an ArtScale stage (Plinko ball 8/9 at 4K GUI 9, dice 8/9 or 5/2, roulette ball ×2): snap first, then scale, so texel edges land on `round(x·k) + i·n` physical px (`PixelSnap.texelEdge`). Sizes are in art px. `TableGfx.regionF/frameF` delegate here (RO §3.1) |
| `SoundCursor` | Wraps `EventCursor` and `SoundMixer` and sends to `FxSounds.play/playAt`. Per frame: `crowd()`, then `advance(cursor, now, offset, Voicing, visualListener)` for each object, then `flush()`. Silent (late) events reach the visual listener only. `muted()` covers the `viewingOwnScreen` case |

Other API pieces:
- `FxSounds.loop(id, DoubleSupplier pitch, DoubleSupplier volume, BooleanSupplier alive)` returns a `Handle` (`active()`, `release()`). It is a tickable looping instance whose volume is × `FxSettings.volume()` every tick, with pitch clamped to 0.5–2. `activeLoops()` reports the count.
- **`MAX_LOOPS = 2` is CLIENT-WIDE.** Every screen and renderer shares these two slots: the roulette screen's `roulette_roll`, an in-world roll loop, RO §5's `roulette_rake` drag loop, any Plinko anticipation loop. A third `loop()` returns `Handle.NONE` and stays silent. RO/PL: start a loop only while it is audible, `release()` it as soon as it ends, keep the in-world loop to the nearest table only (RO §4), and treat `NONE` as "no loop". Slots are freed at once by `release()`, by `alive` turning false, and when the sound engine drops the sound (disconnect, `/stopsound`, sound reload): `FxSounds` prunes any loop the engine no longer reports `isActive` (pure bookkeeping in `core/anim/motion/LoopBudget`, tested by `LoopBudgetTest`; fixes T-MK D1).
- `AnimClock.freezeForTest(levelMs)`, `unfreezeForTest()` and `frozen()`: `levelMs` and `sharedMs` honour the freeze.

## Requirement checklist per spec

PL = PLINKO-ANIM-SPEC, RO = ROULETTE, DI = DICE, CS = CRAPS. ✓ = provided by MK and tested; ◐ = provided, and the game package must use it; ✗ = not MK's (owner noted).

### PL
- ✓ §2.2 ballistic segments (exact parabola, x linear): `Ballistic.fallTime/vxFor`, `Track.ballisticTo`.
- ✓ §2.2 bin settle with 2 bounces at e = 0.35 (3.5 px, then ≈ 0.43 px, g ≈ 900): `BounceChain.Params.plinkoBin()`. The critically damped roll to the bin centre is `Track.accelToStop` or `osc` (◐ PL).
- ✓ §2.3 O(log n), allocation-free, pure-in-t sampling: `Track` (TrackTest: 0 bytes over 10k samples).
- ✓ §2.1 seeded jitter: `MotionRng.of(seed, "PEG", row)` replaces the local xorshift (◐ PL picks the streams).
- ✓ §2.5 anticipation dilation (`g/k²`, `vy0/k`): the closed forms take any g (◐ PL).
- ✓ §2.4/§7 TURBO allowed, SLOW never slows shared time: `SpeedPolicy.SHARED.scale(ms, speed, true)`.
- ✓ §3.1 ArtScale rows (427×240 k9 → n 8 … 960×540 k4 → 8), origin on 1/G, physical-px snap of moving things: `ArtScale`, `PixelSnap`, `SnapDraw`.
- ✓ §3.3 trail: `Streak(6, 60, 60, …)` or a fixed sampler; dots snapped with `SnapDraw`.
- ✓ §3.4 peg wobble `A·e^(−t/40)·cos(2πt/90)`: `Oscillation.dampedCos`.
- ✓ §3.5 tier shake `A·e^(−t/τ)·sin(2π·22t)`, x and y/2, snapped: `GuiShake`.
- ✓ §3.4/§3.5/§3.8 flashes off and reduced-motion gates: `MotionFx`.
- ✓ §3.9 event cursor per ball, render plus containerTick fallback, hitch (lateMs 120): `EventCursor`.
- ✓ §6 voicing: `plinko_peg` ≤ 12/s (`limit`), multi-ball merge within 35 ms (`new SoundMixer(35, 0.1f, 15)`), lead ball + every second contact of the others (`leadVoicing(2, 2)`), bins always pass (`always`).
- ✓ §8.1-5 frame-rate independence and §8.1-8 ArtScale rows (ArtScaleTest).
- ✓ §8.1-9 reduced-motion recorder: `MotionFx.recorder` / `FxGate.Counter` (◐ the `PlinkoFx` facade).
- ✓ §8.2 test clock / in-world freeze: `MotionClock.testOverride`, `AnimClock.freezeForTest`.
- ✗ PlinkoPath, the sprites, the server `due`, multi-ball: PL's.

### RO
- ✓ §2.2 rotor `ω_idle + (ω_a − ω_idle)·e^(−t/τ)` in closed form: `Track` `EXP_DECAY`, with the push as `CONST_ACCEL` or a separate segment.
- ✓ §2.3 stage A exponential orbit: `EXP_DECAY`. Stage B: `DecaySolver` (g_k profile, Θ(k) monotone on [0.2, 6], k_nat via `kForDecel`).
- ✓ §2.4 bisection to 1e-9 with a bound path (`ok=false` plus a residual, so RO shifts t_D): DecaySolverTest (10k random cases).
- ✓ §2.3 drop spiral radius `r_D − ½as²`: `CONST_ACCEL`. Deflector dip `−A·s·e^(−s/30)`: RO composes it (◐).
- ✓ §2.3 rattle: `BounceChain` with `Lattice.pockets()`, 4–7 hops, T₀ 220 ±15 %, e_n 0.66–0.76, e_t 0.45–0.62, z₀ 4 px, alternating fret/floor, kick-back p 0.3 in the last 3 hops, skid ≤ 90 ms, exact final pocket, `withTimeScale(c)` for the §2.4 tail compression. RO picks `s` and the target (BounceChainTest).
- ✓ §2.3 settle `0.22·e^(−t/70)·cos(2πt/110)`: `Track.osc` or `Oscillation`.
- ✓ §3.1 focus wheel native × integer n (427×240 → 144×1 … 960×540 → 208×2): `ArtScale.pickNative`. `TableGfx.regionF/frameF` were added.
- ✓ §3.5 height and shadow `(−0.3z, −0.7z)·k`, shadow (+1, +1), α 0.45(1 − z/8): `HeightShadow.ROULETTE`.
- ✓ §3.5 streak, Δ = clamp(frameDt, 8, 33), ≤ 1.5 px spacing, ≤ 8 samples, only above a 3 px arc: `Streak()` defaults.
- ✓ §3.9 EventCursor in render, hitch 150 ms, late join (`reset(t, true)` or `reset(t, false)`).
- ✓ §6 `FxSounds.loop` for `roulette_roll` (≤ 2 loops, respects volume). Rattle budget via `SoundMixer`.
- ✓ §3.10/§9.1-10 reduced-motion recorder; §9.2 `testClock` / AnimClock freeze.
- ✗ RouletteSpin, RotorModel, sync v2, the sprites: RO's.

### DI
- ✓ PHASE2B §3.1: the forward sim is dropped. Contacts first, then closed forms: `Track` (BALLISTIC, PIVOT quarter tips with the centre rising to √2/2, DAMPED_OSC for rock and teeter), `Ballistic`.
- ✓ §2.3 seeded attempts: `MotionRng.of(seed, "DSIM", attempt)` (= FxRng(mix(seed, hash("DSIM"), attempt))).
- ✓ §3.1 mirrored right lane `x → 360 − x`: `StageMap.mirrored(360)`.
- ✓ §4.1 ArtScale rows (427×240 k9 → 8, 455×256 → fractional 2.87, 960×540 width-bound → 5 or 10) plus 4K GUI 4/5/6/8/9: ArtScaleTest.
- ✓ §4.2 shadow width 1 − z/12 and α 0.45(1 − z/10), lift 0.9: `HeightShadow.DICE`. Dice snapped with `SnapDraw`/`PixelSnap`.
- ✓ §5.2 stamp shake (22 Hz, τ 60 ms, dealer half): `GuiShake`.
- ✓ §7 DiceAudio: 30 ms merge (+0.1), ≤ 10/s per lane (`limit`), REST and outcome always pass, silence after a hitch: `SoundCursor` + `SoundMixer` + `EventCursor(…, 120)`.
- ✓ §3.3/§3.4 SLOW clamped in PvP, allowed in house mode: `SpeedPolicy.SHARED` vs `LOCAL` (◐ DCb chooses per mode).
- ✓ §9/§10.1-15 reduced-motion recorder (`DiceFx` wraps `MotionFx`). §10.2 test clock and in-world `AnimClock.freezeForTest`.
- ✓ §10.1-9 frame-rate independence and 0 alloc (the Track/EventCursor parts are proven here; `DiceRoll` is DCa's).
- ✗ Quat, CubeGroup, DiceTray, DiceSolver, the atlas, DiceAudio ids: DCa/DCb.

### CS
- ✓ §2.1 `DiceEvents`/`EventCursor` shared with Plinko; `ArenaMap` is `StageMap`.
- ✓ §2.3 ballistic flight `Δt = √(2(H−z0)/g) + √(2H/g)`: `Ballistic.flightTime`. Hop apexes: `apexOfHop`/`hopTime`. Slide at constant deceleration: `CONST_ACCEL`. Tips: `PIVOT` (`pivotBy`, centre at √2/2 at 45°). Rock `A·e^(−t/35)·sin(πt/T)`: `DAMPED_OSC` or `Oscillation`.
- ✓ §3.3 throw and puck never scaled; chips/stick/idle are LOCAL: `SpeedPolicy`. Craps uses ×1.4 for SLOW and passes its own factor to `LOCAL.scale`.
- ✓ §4.1 rows: cells 14/18/22/30 drawn 1:1, die width cell·G physical px: ArtScaleTest `crapsRows`.
- ✓ §4.2 shadow at (+0.25z, +0.15z), α 0.45 → 0.2, 3 sizes: `HeightShadow.CRAPS` (`shadowSize`). After-image at t − 14 ms: the sampler plus `MotionFx.streak`.
- ✓ §4.2 snap `round(xGui·G)/G`, never scaled: `SnapDraw`/`PixelSnap`.
- ✓ §4.3 flashes off → no white frame: `MotionFx.flash`. §4.5 pill shake: `GuiShake`.
- ✓ §6/§7 lead die only for FELT/TIP (`leadVoicing(1, 0)`), WALL for both (`always("dice_wall")`), merges of 35–40 ms (`SoundMixer(40, 0.15f, …)`).
- ✓ §9.1-8 cursor at 4.2/8.3/16.7/33/500 ms steps: EventCursorTest.
- ✗ CrapsThrow, CrapsBeats.of, layout, atlas, chips: CM/DCa.

## Verified (exact commands; JDK 25.0.4.1, in `java/`)
- `./gradlew --max-workers=4 -Dorg.gradle.jvmargs=-Xmx3g compileJava compileClientJava compileGametestJava test`: BUILD SUCCESSFUL, 1323 tests, 0 failures/errors (RawInputGuardTest included; re-run after the T-MK fixes).
- The same with `-Pmc=26.3`: BUILD SUCCESSFUL, 1323 tests, 0 failures.
- New MK tests (`core/anim/motion`): TrackTest (5), DecaySolverTest (4, including the 10k-case sweep), BounceChainTest (4, 4000 seeded rattles), EventCursorTest (4), ArtScaleTest (6), PixelSnapTest (3, k 1..12), SpeedPolicyTest (3), SoundCursorTest (4), MotionRngTest (3), MotionKitMiscTest (6), CoreMotionVectorsTest (golden `src/test/resources/fx/vectors/core_motion.json`; regenerate with `FX_DUMP_VECTORS=1`).

## T-MK fixes (re-test round)
- **D1** `FxSounds.loop` slot leak: slots live in `LoopBudget`. A loop is pruned when it is released, stopped, or no longer `SoundManager.isActive` (the engine dropped it on disconnect, `/stopsound` or a reload). `release()` removes the loop from the budget at once and stops the sound. `LoopBudgetTest` covers the pure part.
  - Client smoke on 26.3: a throwaway `MkLoopSmokeClientGameTests`, one filtered `runClientGameTest -Pmc=26.3` under `with-client-lock.sh`. The json swap was restored with `git checkout` and the class removed (not committed).
  - Results: 2 loops start and the third gets `NONE`. After `SoundManager.stop()` the count is 0, three times in a row, and 2 new loops start each time. `release()` frees a slot at once and the slot is reused. `alive=false` stops its loop. With all released the count is 0. BUILD SUCCESSFUL.
- **D2** `SnapDraw` scale overloads (`blitF/regionF/frameF` with `scale`): snap, then scale. `PixelSnap.texelEdge` and `PixelSnapTest.scaledSpritesKeepTexelEdgesOnPhysicalPixels` check every texel is exactly n physical px (k 1..12, n 1..12).
- **D3** `DecaySolverTest.rouletteRangeIncludingExactBounds`: 20k cases drawn from the RO §2.3 distributions, T 0.25–13 s (10 % pinned at 0.25 and 13), k_nat from `kForDecel`, targets exactly at, and 1 ulp inside, both reach bounds, plus out-of-reach bound solves with exact residuals.
- **D4** `BounceChainTest.rouletteBothDirectionsAndTimeScales`: 20k rattles in both directions with time scale 0.6–1. Every rattle has 4–7 hops, lands exactly on the target, stays on the lattice and is monotone along dir; the kick goes against dir. The existing sweep now asserts ≥ 4 hops.
- Minor: `GuiShake.push(..., what)` records under a per-effect id. The DecaySolver `kNat` javadoc is corrected.

## Not verified
- `GuiShake`, `SnapDraw` 1:1 and `SoundCursor`→`FxSounds` were exercised by the tester's 26.3 smoke (T-MK §5), not by a committed client test. The new `SnapDraw` scale overloads are verified by the pure texel-edge test and by compiling only.
- `./gradlew build` (with javadoc and jar) was not run; only the gate tasks above were.

## Notes and risks for dependents
- `SoundCursorTest` tests the pure `SoundMixer` driven exactly as `SoundCursor` drives it. The client class itself has no unit test (client classes are not on the unit-test classpath).
- Merge window: two offers in the same frame merge into one louder sound. An offer one frame after a voiced one inside the window is dropped (it cannot retroactively boost the first). So at 60 fps the "+0.1 louder" applies only to same-frame contacts, and a double click is never heard.
- `BounceChain` with a lattice requires the target to be a FLOOR point. When the natural fractions crowd the tail, landings are clamped so every remaining hop advances at least half a step (rattles of 4–7 hops all occur).
- `ArtScale` origins are rounded DOWN to 1/k so the stage never leaves an integer box. The play-area sizes in ArtScaleTest for rows the specs do not list are approximations (chrome per tier); RS owns the real `ViewportPolicy`.
- `GuiShake.push` takes the GUI `Matrix3x2fStack` (the 26.x GUI pose), not a `PoseStack`.

## Requests to other packages
None blocking. RS: once `ViewportPolicy` exists, pass its layout `k` (physical px per layout px) to `ArtScale`/`PixelSnap`/`SnapDraw`, not the raw GUI scale.
