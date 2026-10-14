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
