# DCb — Dice Duel motion + PvP dice duel (Phase 2b) handoff

Branch `p2/dcb` (from `feature/v020-table-ui` @292cbe5; merged `p2-e2-freeze` 691939e, `p2/k2`, `p2/dca` twice (last
@5c5c309a, the oblique atlas / rounded pips), `p2/mk` @3bbdbbab). Scope PHASE2B §4 DCb (DI §3–§9).

## What changed

| Area | Files | What |
|---|---|---|
| Timeline | `core/anim/dice/DuelTimeline.java` | New beats THROW 480, YOU_LAND 1680, THEM_LAND 2030, COMPARE 2230, CHIPS 2380, END 3080, SWEEP_AT 2580, ROUND_PERIOD 2900, T_DUEL 1200. `rollSeed(seed, round, sideAB)` (PLAYER-side keying). Cup alignment: `SHAKE_LEAD` (the wind-up starts on shake reversal 2 so the flick releases die 0 exactly at THROW), `shakeOffset(ready)` (house: the first reversal after the result), `windUp(order, offset)`. `pipMs/countUpMs` (count-up budgets so both totals are known at COMPARE). Nested pure `Cup`: Shake until the wind-up, flick, **holds TIP70 until die 1 has poured** (`roll.releaseMs(1)` + 40 ms, DCa note), then recovers. Old `cupFrame/waitingCupFrame` removed. |
| World plan | `games/extras/logic/DuelStagePlan.java` | Rewritten on `DiceSolver` + `DiceTray.worldLane(len)` + `ThrowSpec.chest(h)`, same keys as the screens, A at `throwAt(r,0)`, B at `throwAt(r,1)`; MC model quaternion `Ry(yaw)·trayToMc(model)`; billboard at rest + count-up; tied rounds picked up at the sweep. |
| Server | `server/DuelStage.java`, `server/DiceGame.java`, `net/DuelStageSync.java` (new) | PvP result carries `side` ("a"/"b") and `start` (duel game tick). `DuelStageSync` (≈120 B, once per duel, late arrivals within 48 blocks every 10 ticks) to modded players. Item-display dice are an anonymous `DieDisplay` whose `broadcastToPlayer(p)` is `!modded(p)`, never saved, posed every **2** ticks to the plan's quaternion (`left_rotation`), no packets while resting. Fallback sounds (throw / first floor / settle) only to unmodded bystanders. Test hooks `DuelStage.modded/soundSink/syncSink`, `DiceGame.PVP_RESULT_SINK`, `DiceGame.pendingTicks`. |
| Layout | `present/DiceStage.java` (new) | ArtScale for the 360×140 arena under the PvP strip; origin snapped to 1/k; ROOMY flag. Replaces `ExtrasLayout.dice` in DiceScreen (`ExtrasLayout.dice` itself left for Q to delete; `ExtrasFlowTest` still audits it). |
| Screen | `client/DiceScreen.java` (rewritten), `client/DiceArena.java` (new), `client/DiceScreenProbe.java` (new, tests) | House clock = local ms since Roll × LOCAL speed (SLOW/TURBO ok); PvP clock = `levelMs − start·50` (SHARED, never scaled). Rolls via `DiceRollCache`: house = LEFT tray you / RIGHT tray dealer; PvP = LEFT-lane roll per player side, the opponent's drawn with the mirrored StageMap (A matches on both screens). Cups (shake counter-phase, wind-up, flick, pour hold, recover; dice emerge from behind the cup), atlas cell 32 + shadows + after-image (only ω > 13 rad/s, α 0.2), stateless dust / clack sparks / rail glints from events, vs badge tilt on bumper hits, pip count-up (gold pips on the atlas pips, generator `upPipCentres` port), plaques on the front rail (count, pop, crack), winner hop + 15° wiggle + gold pips + lane glow, loser dim + sink, push shake, house stamp slam (1.6→1, OUT_BOUNCE) + dealer-half shake + flash + dust, PvP tie banner on the front rail + sweep back into the own cup, previous result gathered into the cups on Roll, chips. Sounds: DiceAudio cursors per pair (contacts), cup rattle cursors, beats (drumroll, pip ticks, hop, stamp, outcome); skip/late open = silent. Reduced motion: static cups, dice fade in at rest 300 ms after the throw, pips lit at once, static glow/dim, no hop/shake/particles/flash/after-image, sweep = fade. Kit banner hidden on a new throw; its region is the free strip (non-PvP) else the arena middle. |
| World render | `client/DuelStageRenderer.java` (new) | `LevelRenderEvents.COLLECT_SUBMITS`; LOD ≤8 full + WHITE_ASH dust + positional contact sounds, 8–24 motion (settle/clack sounds ≤16), 24–48 rest pose only after rest, >48 culled; silent for the duellists. `DiceWorldDraw` cube + blob shadow. |
| Strings | STRINGS.md `### extras › dice motion` | `gui.burmaldaholic.extras.dice.motion.total` (ROOMY plate line 2). gen_lang run, LF kept. |

## Verified
- Compile `compileJava compileClientJava compileGametestJava` on 26.2 and `-Pmc=26.3`: OK (see the final commit).
- Unit (`test --tests '*DuelTimelineTest' '*DuelStagePlanTest' '*DiceStageTest' '*DiceThrowPathTest' '*RawInputGuardTest'`):
  DuelTimelineTest 9, DuelStagePlanTest 5, DiceStageTest 2, RawInputGuardTest 2 — all green. Covers DI §10.1-10 (cup /
  rattle counter-phase, wind-up on a reversal), -11 (PvP keying + exact mirror x' = 360 − x, same height, top face kept),
  -12 (chat ticks ≥ reveal, their rest before the silent beat, their cup tipped only around its own flick, THROW+100 cup
  still shaking), -13 (2000 seeds × 4 dice: every 2-tick display step < 170°), -14 (ArtScale over 22 windows incl. 4K GUI
  2–9, 1366×768, ultrawide; inside the PlayArea, clear of the strip, origin on a physical px). World plan: faces at rest
  for 36 pairs × 4 seeds × 2 sides, each pair on its own half near the line.
- Server GameTests (`runGameTest`, the filter value did not match so ALL 232 ran): "All 232 required tests passed",
  including DuelGameTests `pvpResultCarriesSideAndStartAndChatWaitsForTheCompare` (side a/b, start == game tick, one
  seed, every round's lines at start + revealTick(round)) and `moddedViewersGetTheSyncButNoEntitiesOrSoundPackets`
  (modded viewer: sync yes, `broadcastToPlayer` false, 0 sound packets; vanilla viewer: entities + throw/settle sounds).
- Client CGT=DD on 26.3 (`BURMALDAHOLIC_CGT=DD … runClientGameTest -Pmc=26.3` under both locks), shots in
  `.burmaldaholic-tools/shots/DCb/`: real flow (dice item, chip, Roll at real px, balance == banner net), house roll at
  427/480/640/960/1280-framed × 10 states + RU 480/640, house tie (stamp), PvP tie (banner, sweep, round 2), PvP mirror
  (A's dice on A's and B's screens: rest + air positions mirror exactly), reduced motion (0 effects pass the gate), in-world
  at 3 blocks with a frozen AnimClock (LOD full, no item-display entities on the modded client). See "Client runs" below.

## Client runs
FILLED IN BELOW AFTER THE FINAL RUN.

## Not verified / open
- Two real clients (true two-player PvP) not run: the mirror test renders one duel as side A then side B on one client
  and compares the drawn positions + shots.
- 26.2 client run not done (compile only).
- Sounds verified by code path only (no audio capture).
- In-world totals billboard timing (server TextDisplay) checked by unit test only.

## Shared touches / requests
- `ExtrasModule` (+1 line: register `DuelStageSync`), `ExtrasClientModule` (+1 line: `DuelStageRenderer.register()`).
- `gametest/extras/DuelGameTests.java` (+2 tests; DuelStage's own test class) — no fabric.mod.json change.
- `DiceThrowPathTest` (−1 line: the removed `DuelTimeline.cupFrame`).
- DCa: `DiceDraw` blitF arg order was fixed locally after MK-2 and the same fix arrived with p2/dca (merged clean).
- DCa/art (request, not blocking): the 6-frame `dice_duel_cup` has no back/front layers or TIP70/RECOVER frames (DI §8);
  the screen maps Shake frames onto it and lets dice emerge from behind the cup body. The arena has no divider ridge
  art (the dashed centre line reads as the divider).
- Q: delete `ExtrasLayout.dice`, `DiceThrowPath` (craps still uses it until CM lands).

## Risks
- `DuelStage.modded` uses `ServerPlayNetworking.canSend(p, DuelStageSync.TYPE)`; a modded client of an older build
  (without the payload) correctly falls back to entities.
- PvP screen clock uses the client level time; a client far behind the server tick shows the first frame late (beats
  still aligned to the chat ticks).
