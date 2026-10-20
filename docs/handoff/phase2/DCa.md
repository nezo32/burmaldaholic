# DCa — shared dice kit (Phase 2b) handoff

Branch `p2/dca` (from `p2/mk` @1dc7372 = main v0.1.3 + MK). DCa-A announced at `83068d9`. Pure core in
`core/anim/dice`, client in `client/table/fx/dice`, generator module `tools/assets/modules/dicekit.mjs` (helpers in
`tables/dice.mjs`). No game, logic, BlockEntity, odds or RNG code touched: no money/RNG diff.

## API summary

Pure package `dev.nezo.burmaldaholic.core.anim.dice` (tray units: 1 u = die half-edge; tray frame x right/east,
y toward the viewer/south, z up; roll clock 0 = die 0 released).

| Class | What it does |
|---|---|
| `Quat` | `double[4]` maths (mul, conj, rotate, axisAngle, expMap, slerp, angleTo = rotation angle, arcTo = S³ arc, support heights). `StrictMath` (bit-identical on every JVM) plus `…Fast` variants for sampling. `mirrorX` / `mirrorXKeepUp` (right lane from a left-lane roll, top number kept), `trayToMc` (tray → MC model frame). |
| `CubeGroup` | The 24 exact elements (`element(4·(face−1)+quarter)`, components exactly 0/±½/±√½/±1), `faceUp`, `nearest`, `isElement`, `relabel(bodyUp, want, k)`, `compose`, `index`. Canonical labels = `DiceFaces.layout(1)` (1 +z, 6 −z, 2 −y, 5 +y, 3 +x, 4 −x). |
| `DiceTray` | `record (g, floor, walls, bumpers, restZones, keepOuts, divider, restSep)` + `Wall(ax,ay,bx,by,restitution,pitch,mustHit)` (interior on the +90° side of a→b), `Bumper`, `Box`, `Poly`, `Side`. Factories `duelLane(Side)` (felt octagon of `dice_duel_arena_*`, divider x 178 px, vs badge bumper, h = 16 px), `worldLane(lenBlocks)` (0.1 block/u, soft divider at the midpoint, g 220). `mirrored(w)`, `allWalls()`, `restZone(die)`. Constants `R_PLANE 1.25`, `DUEL_PX/W/H`, `G_DUEL 112`, `G_WORLD 220`. |
| `ThrowSpec` | `record` of release point/height, direction cone, preferred speed / vz / spin ranges, lateral spacing and release stagger. `cup(Side)`, `hand(side, x, y)` (craps shooter, toward −y), `chest(heightBlocks)` / `chest()`. |
| `DiceSolver` | `solve(tray, spec, seed, faces, targetMs | NATURAL)`. Contacts first: specular path P0 → 0–2 banks → R (mirror images; bumper by a root search), vertical plan (release flight + 1–3 hops, one g, apexes strictly decrease), ground plan (settle ≈ 100 ms with the landing absorbing speed, 1–3 quarter-rolls about the leading edge with the centre on the pivot circle, ~8 % teeters, slide with yaw square-up, rock 12–18°, exact group-element REST on R). The target is met by solving launch speed and slide deceleration together (R slides along the last leg), then roll counts, then bounce apexes; no time scaling. Faces only choose `L`. Dice keep `DIE_GAP` apart near the floor (one ≤ 50 ms brush allowed → CLACK). 16 seeded attempts (strict 5, relaxed 6, very relaxed 5) then a deterministic fallback (targeted with a brush allowed, then natural pace held to the target). |
| `DiceRoll` | `dice, restMs(), restMs(die), restTime(die), releaseMs(die), wallMs(die), restPoint(die), faces, events(), sample(die, ms, DieSample), topFace(die, ms[, scratch]), teeters(die), attempt(), fallback(), label(die)`. `sample` is pure in t and allocation-free. |
| `DieSample` | `x, y, zLow, zc, vz, q (MODEL), omega, speed, phase` (HELD AIR ROLL TEETER SLIDE ROCK REST). |
| `DiceEvent` | RATTLE, RELEASE, FLOOR (aux landing #), WALL (aux wall·1000 + diamond), BUMPER, CLACK (obj −1, aux later die), TIP (aux roll #), TEETER (aux ±1), ROCK, REST (aux 0: events never depend on the result). |
| `Shake` | DI §3.2 cup shake: reversals 150 ms ×0.9 → 80 ms floor, offsets/lean/lift/frame per ms, `windUpAt(readyMs)` on a reversal ≥ 420 ms, `releaseAt(windUp)`, counter-phase = negation, `rattle(untilMs, dice)` events (per-die hits ≥ 45 ms apart, silence during the wind-up). |
| `DiceRollCache` | Solve-once LRU keyed by `key(seq/round, side, seed)`; never solve in a render/sample path. |
| `DieAtlasTable` | GENERATED (do not edit): 384 frame quaternions (frame = 16·g + j), `UP`, `CELLS {14,18,22,30,32}`, `FRAME`, `REST_FRAMES`, `TOP` (rest-frame up-face quads per cell), `frame(q)` (nearest, allocation-free). |
| `DiceFaces` | now documented as a view on `CubeGroup`; `groupIndex(top)`. `DiceThrowPath` kept unchanged as the deprecated adapter (javadoc only, no `@Deprecated` annotation to avoid 26 lint warnings); Q deletes it. |

Client `dev.nezo.burmaldaholic.client.table.fx.dice`:

| Class | What it does |
|---|---|
| `DieAtlas` | sheet per cell (`textures/gui/tables/core/dice_atlas_<cell>.png`), `frameSize`, `blit(g, cell, frame|q, cx, cy, k, argb)` (1:1 region at a float position snapped to 1/k). |
| `DiceDraw` | `stage(ox, oy, cell)` (StageMap matching the art: 1 u = half-edge px, y × cos 22°, height × sin 22°), `shadow` (HeightShadow.CRAPS: offset (+0.25z, +0.15z), α 0.45 → 0.2, 3 sizes), `die`, `centre`, `restGlint`, `roll(g, roll, die, ms, map, cell, k, Scratch)` = shadow + after-image (|ω| > 9 rad/s, t − 14 ms, α 0.35, gated by `MotionFx.streak`) + die. Mirrored maps mirror the orientation (top number kept). |
| `DiceWorldDraw` | `die(pose, out, sample, blocksPerU, surfaceY, light)`: `dice_display` cube rotated by the model quaternion (tray → MC), centred at `zc` (no corner sinks); `shadow(...)`: blob quad, α 0.35 → 0.12, size 100 → 60 %, offset (+0.2, +0.1)·z. Pose = tray origin, tray x along model X, tray y along model Z. |
| `DiceAudio` | `gui()` / `world().at(x,y,z)`, `cursor(events)` (EventCursor with the 120 ms hitch rule), `advance(cursor, now, offset, visual)`; 30 ms merge, ≤ 10/s, `dice_settle` always passes; preset table in the javadoc. `volume(0.7)` for the opponent's cup. |

Generator (`dicekit.mjs` → `tables/dice.mjs`): `dieAtlas(cell)` for 14/18/22/30/32 (20 × 20 grid, frame
`atlasFrame(cell)` = 26/32/40/54/56 px), `dice_atlas.json` (frames, `up`, rest frames, per-cell rest top-face quads),
`DieAtlasTable.java`, `gui/sprites/tables/core/dice/{shadow_<cell>_<0..2>, rest_glint_<cell>, dust (5×5×4), spark (3×3×3)}`.
New sound ids (core): `dice_clack, dice_tip, dice_teeter, dice_settle, dice_pip, dice_stamp` (sounds/core/sounds.json,
SoundCatalog, STRINGS.md subsection "core › dice kit sounds", gen_lang → extras lang fragments by the namespace rule).

## Notes for dependents (CM, DCb)
- Release stagger of the cup is 140–200 ms (die 1 leaves `roll.releaseMs(1)`); a tighter pour makes the two dice run in
  tandem in the small duel lane. `Shake.releaseAt(windUp)` is die 0; time the flick of die 1 from the roll.
- The duel rest zone is the lane box inset by half a die (x 52–150, y 66–104 px) so two dice fit side by side.
- Prefer `duelLane(RIGHT)` + `cup(RIGHT)` over a mirrored map; for PvP (one roll shown in both lanes) a mirrored StageMap
  works: `DiceDraw` mirrors the orientation and keeps the top number.
- Craps: build the tray with the back wall `Wall(..., restitution 0.58, pitch = diamond spacing in u, mustHit true)`,
  keep-outs for stacks/puck/pill, the COME-row rest zone, `ThrowSpec.hand(side, homeX, homeY)`; `NATURAL` gives
  `restMs` for `revealTicks`. Fixture examples: `DiceFixtures.crapsBig/crapsCompact` (tests).
- World: `DiceTray.worldLane(len)` + `ThrowSpec.chest()` at `T_DUEL` = 1200 works for 2/4/8-block lanes.
- Cache: `DiceRollCache` per screen / BER / server stage.

## Verified (JDK 25.0.4.1, in `java/`)
- `./gradlew compileJava compileClientJava compileGametestJava test` (26.2) and the same with `-Pmc=26.3`: BUILD
  SUCCESSFUL, 175 test classes, 1349 tests, 0 failures each (RawInputGuardTest included). After the last client-only
  commit (b1d7349) both versions recompiled (`compileJava compileClientJava compileGametestJava`, SUCCESSFUL).
- DCa unit tests (`core/anim/dice`): `CubeGroupTest` (group closure, exact components, faceUp, relabel, mirror/MC
  conjugation), `DiceRollTest` at the full 36 pairs × 256 seeds × 7 trays {duel L, duel R (mirrored tray), world 2/4/8,
  craps BIG/COMPACT}: faces at rest, group element ±1e-12, rest point == restPoint and inside the zone, no OBB overlap,
  relabel invariance bit-equal (x, y, zLow, zc, phase every 7 ms, events, rest times), 1-ms sweep: no floor/wall/bumper
  penetration, keep-outs, one g per AIR, energy non-increasing at contacts, hops strictly decrease, angle jump < 0.5° and
  position continuous at every phase change, last face change ≥ 120 ms before rest (teeter fall ≥ 80 ms), mustHit hit,
  target ±0.5 ms, dice apart near the floor except one announced CLACK; ≥ 92 % of rolls show a quarter-roll (measured
  ~95 %, teeters ~8 %); `naturalTimingAndDeterminism`; `DiceRollVectorsTest` (golden `fx/vectors/dice_roll.json`, 16
  cases); `DiceKitTest` (tray normals, mirror, Shake DI §10.1-10, event cursor at 4.2/8.3/16.7/33/500 ms steps, sample
  pure + 0 bytes over 10k samples, 10k sampled frames solve 0 times + DiceRollCache, worst solve (best of 3) < 20 ms over
  10.5k rolls — measured 1.9 ms); `DieAtlasTableTest` (JSON parity, frames = offsets × Java group elements, rest pose →
  exact frame 24 × 3, solved rolls rest on a yaw-0 frame of their face).
- Tools: `npm run check:assets` OK (8 modules), `npm test` 10 files / 62 tests green; `dice-atlas.test.mjs`: 384 unit
  frames, covering radius ≤ 22° (quaternion arc; measured ~19°) over 20k random rotations, every rest pose present, pip
  clusters == face on all 24 rest frames of every cell, die width at rest == cell px, JSON == Java table, deterministic.
- Client CGT=DK on 26.3 (one run under `with-client-lock.sh` + `with-heavy-lock.sh`, temporary entrypoint swap restored
  with `git checkout`): 1280×720 GUI3 → 427×240 k3, 960×540 GUI2 → 480×270 k2, 1920×1080 GUI2 → 960×540 k2; cells 14/22/32:
  resting die width == cell·k physical px, top-face pip clusters == the face, airborne die's shadow ≥ 20 % darker than
  the felt — all pass. Shots `p2b_dca_dice_kit_en_{427,480,960}.png` in `.burmaldaholic-tools/shots/DCa/` (looked at:
  crisp 1:1 texels, readable pips, shadows down-right). An earlier run of the same test failed on two probe bugs (quad
  corner order, shadow hidden under the die), fixed in b1d7349; one attempt died of host OOM before starting.

## Measured solve times (warm, best of 2 runs per roll, 1000 rolls per tray, shared machine)
| Tray | p50 µs | p99 µs | max µs |
|---|---|---|---|
| duel L | 247 | 1119 | 2673 |
| duel R | 277 | 1317 | 1936 |
| world 2 | 77 | 248 | 355 |
| world 4 | 41 | 120 | 234 |
| world 8 | 36 | 107 | 156 |
| craps BIG | 97 | 440 | 824 |
| craps COMPACT | 57 | 186 | 297 |

**Deviation (accepted by the coordinator):** PHASE2B §4 asks p99 ≤ 50 µs; the planner (separation of two dice in the small
duel lane needs several seeded attempts) measures p99 ≈ 0.1–1.3 ms. `DiceRollTest.solveIsFast` asserts p99 ≤ 5 ms and
`DiceKitTest` worst < 20 ms. Solve once per roll per side and cache (`DiceRollCache`); never in a render/sample path.

## Not verified
- No in-world render of `DiceWorldDraw` (compile-verified only; DCb/CM wire it into their renderers).
- `DiceAudio` voicing compile-verified only (pure mixing rules are MK's `SoundCursorTest`).
- `./gradlew build` (javadoc + jar) not run; only the gate tasks above. 26.2 client run not done (DK ran on 26.3).

## Ownership notes / shared touches
- New top-level generator module `tools/assets/modules/dicekit.mjs` (instead of editing `tables.mjs`; it also writes
  `core/anim/dice/DieAtlasTable.java`), test at `tools/assets/test/dice-atlas.test.mjs`.
- `SoundCatalog` (+6 core ids), `core_anim.json` sound list regenerated (only those 6 lines), STRINGS.md own subsection,
  gen_lang output lands in `lang/extras/*` (namespace rule for `dice_*`), LF kept.
- `DieAtlasTable` lives in core (pure, unit-testable) rather than `client/table/fx/dice`; the client `DieAtlas` wraps it.
- Gametest `fabric.mod.json`: `DiceKitClientGameTests` registered (the UI branch registered the same class name as a
  stub; on merge keep this implementation).

## Requests
None blocking. CM: build the craps tray with `DiceTray` records as in `DiceFixtures.crapsBig`; DCb: `DuelTimeline` should
time die 1's pour from `roll.releaseMs(1)` (140–200 ms after die 0).

## Visual rework after the coordinator review (2026-10-15)
Review points (shots of the first pass in `.burmaldaholic-tools/shots/DCa/before/`) and fixes (`after/`):
1. Inconsistent square/round pips → pips are pixel-designed in the face texture (`faceTexture(E, face)`): one size per
   cell (2 px at 14, 4 px at 18/22, 6 px at 30/32; red 1 bigger), rounded from 4 px, a dark inner-shadow arc on the
   top-left, symmetric and centred, ≥ 1 px apart (the 6 pulls its rows outward or shrinks its pips to keep the gap).
2. Garbled squashed pips on the front band → the atlas uses an OBLIQUE projection (`u = x, v = y − κz`, κ = 0.22): a
   resting top face is mapped texel for texel (identical pips on every face); faces seen flatter than |cos| 0.45 show
   bevelled bone without pips (no dashes).
3. Flat tall tile → lower band (κ 0.22: 7 px at 32), lit bevel ring (normal tilted toward each edge), rounded 1-px
   silhouette corners (cells ≥ 18), Lambert from the top-left front; runtime contact shadow peeks out down-right and is
   darker at rest; stronger height lift (0.9 half-edge per u) so airborne dice separate from their shadows.
4. 14 px: 2 × 2 pips, clear bevel and outline; the red 1 is a 4 × 4 rounded pip.
5. Standard faces: opposite faces sum to 7, standard layouts (2 and 3 diagonal, 6 in two columns). New tests in
   `dice-atlas.test.mjs`: layouts / symmetry / gaps / bevel ring per cell, and on every rest frame of every cell the
   pip clusters' count AND centres (≤ 1 px from the predicted rotated layout).
In-world: `DiceWorldDraw` now uses a generated `textures/entity/core/dice_world.png` (16-px faces with the same pips)
instead of the 8-px `dice_display` item texture. `DieAtlasTable.PITCH_DEG` became `KAPPA` (oblique); `DiceDraw.stage`
maps tray y 1:1.

Re-verified (test only what changed): tools `check:assets` OK, `npm test` 63/63; `compileJava compileClientJava
compileGametestJava` + `test --tests '*core.anim.dice*' '*RawInputGuardTest' '*PresentationCoreTest'
'*CoreAnimVectorsTest'` on 26.2 and the dice + guard tests on `-Pmc=26.3`: BUILD SUCCESSFUL, 0 failures. DK client run on
26.3 (with p2/mk @3bbdbbab merged): all probes pass; shots `after/0000…0002` (427/480/960) and
`after/0003_p2b_dca_dice_world_en_960.png` (+ `world_zoom.png`).

### R3 world-texture nits (coordinator review of @4da2b48)
Fixed in the generator only (`tools/assets/modules/tables/dice.mjs` `diceWorldTexture`):
- '+'-shaped pips: faces are now 32 px (28-px pip area inside a 2-px rounded bevel), so the atlas pips are 6-px discs
  (corners cut, inner-shadow arc) and the red 1 is a 10-px disc — the same pip code as the screen dice.
- Stray glyph on a side face: faces sit in 34-px cells with a 1-px gutter that repeats the face edge (no texel of a
  neighbouring face can be sampled at the cube edges); texture 136 × 68, cells 7–8 fully transparent.
  `DiceWorldDraw.cell/texture size` updated to match (u = 34·((k−1) mod 4)+1, v = 34·⌊(k−1)/4⌋+1, 32).
- New pixel check `dice-atlas.test.mjs` "in-world die texture": decodes the PNG; per face asserts only bone/pip colours
  inside the bevel, exactly k 8-connected pip components (no stray texel), each pip a square-bounded disc ≥ 6 px with
  cut corners, 78–93 % fill, single-run symmetric convex rows/columns (rejects '+' and squares), one pip size per face,
  gutters = edge, unused cells transparent, committed PNG == generator output. Mutation-tested: '+' pips and a single
  stray texel both fail it.
Not re-run: client run (no screenshots per owner rule; texture change is UV-only on the Java side).

### R3 extra: DiceSolver hand-off overload (for CM, SY-SPEC §3 Craps row) — additive
```java
public record DiceSolver.Initial(double x, double y, double zLow, double vx, double vy, double vz, double[] q, double wx, double wy, double wz)
public static DiceRoll DiceSolver.solve(DiceTray tray, ThrowSpec spec, int seed, int[] faces, int targetMs, Initial[] initial)
public static final double DiceSolver.BLEND_MS = 120
```
Tray frame/units; `zLow`/`vz` of the lowest point (as `DieSample`); `q` = MODEL orientation (as `DiceRoll.sample`);
ω world-frame rad/s. Roll time 0 = hand-off (the rattle's exit when the result arrives; sample the rattle there).
Same contacts/events/rest times/rest points/faces as the 5-arg solve; each die starts in `initial[i]` at 0 and blends
(new `Seg.BLEND` AIR sub-kind: constant-rate extrapolations of hand and flight mixed by 3s²−2s³, non-flipping slerp) into
its solved flight by `releaseMs(i) + min(120, 0.6·first flight)` ms; HELD in the initial pose before 0. Existing
signatures/behaviour unchanged. Test `DiceHandoffTest` (all fixtures × 12 seeds, random hand states): C0 at 0 and
before, C1 at 0 (finite diff vs hand v/ω), C0+C1 at the blend end, identical to the plain roll after the blend, exact
faces at rest, no per-ms pops; mutation (linear h) fails C1. Not covered: die–die separation during the blend (the
blend is short, before the first floor contact).
