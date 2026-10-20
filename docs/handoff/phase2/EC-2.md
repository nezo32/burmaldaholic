# EC-2 handoff: comps, free-bet tokens, daily bonus, visible cashback

Branch `p2/ec2`, based on `feature/v020-table-ui`, with `p2/ec1` merged at its skeleton commit `9c1bed42`. Spec:
`.burmaldaholic-tools/findings/EC-SPEC.md` §4–6, plus the coordinator decision that comps pay 10–25 % of the
theoretical loss.

## Done

### New comps module (`comps/*`)
- Module id `comps`, registered in `ModuleList` and `namespaces.properties`
  (`comps=free_bet_token,daily,rental`). Its lang fragment is `lang/comps`.
- `comps/logic/CompsRules` (pure):
  - points: `round(1000 × stake × edge × rate)` milli-points, capped at 10M points;
  - rates: `rate()` applies the comp + cashback ≤ 0.35 clamp;
  - the `State` attachment record with the day roll;
  - the reward catalogue (§4.2), with `cost()` (suite: 50 / 30 at Gold+ / free once at Diamond+) and `lock()`
    (tier, points, limit, dimension);
  - the daily-bonus gates.
- `comps/logic/CompsLayout` (pure): the Comps tab layout. A unit test checks fit and no overlap for bodies from
  320×180 to 480×240, and that nothing covers the shell's result line.
- `CompsService`:
  - persistent `burmaldaholic:comps` player attachment (`copyOnDeath`);
  - a `PLAY_RESOLVED` listener. Rounds earn points only when house-banked, not an owned casino, a CHIPS stake, not
    vs bots and not PvP;
  - the activity gate: movement > 0.5 block, or any use/attack callback, within the last 60 s;
  - daily claim: lifetime wager ≥ 1000 (read through `vip/wagered` ObjectShare), active minutes, IP gate
    (dedicated servers only), then a `toast.burmaldaholic.daily.*` toast;
  - redemptions: food plates, suite credit, tokens, fireworks with flight 2, rentals;
  - `CompsSyncPayload` snapshot, which includes the cashback estimate from the new `vip/cashback_estimate`
    ObjectShare.
- `CompsConfig`: module config section `comps`. It is shown on the VIP config page.
- `CompsWorldData` (SavedData): the day, salted IP hashes, and tokens returned to offline players.
- `FreeBets`:
  - the `free_bet_token` item (stack 16);
  - components `free_bet_token_value`, `free_bet_token_bound_to`, `free_bet_token_expires`;
  - the core `FreeBetTokens.Provider`;
  - expired tokens are swept from inventories every 5 s.
- Art: `tools/assets/modules/comps.mjs` generates `item/comps/free_bet_token.png`. The model and item JSON are
  hand-written.
- `Rentals`:
  - horse (Overworld) or strider plus a tagged warped fungus on a stick (Nether);
  - the `burmaldaholic:rental` entity attachment;
  - the entity is discarded on expiry, on the owner's logout, or on death (no drops);
  - no inventory or sneak interaction, and only the owner can use it.
- `SuiteNight`: a `UseBlockCallback` on beds inside a casino. With the day's credit, the player checks in once:
  forced respawn, Regeneration II, Saturation, and Absorption at Gold+. Overworld beds then continue to vanilla sleep;
  Nether and End beds return SUCCESS, so they never explode.
- Net: `CompsActionPayload` (C2S: sync / redeem / claim_daily) and `CompsSyncPayload` (S2C).
  - `CompsModule.handle` is the receiver. Redeem and claim need `CounterSessions.validate` and the shared
    `tryAct` cooldown, and report back through `CounterSessions.result`.
  - The `comps` tab is registered with `CounterSessions.registerTab(TAB_COMPS, 20, any)`.
- `/casino comps points <player> <n>` (op) sets a player's points, for testing.
- Client:
  - `client/comps/CompsClient` registers `CounterTabs.register("comps", CompsTab::new)`, the sync receiver (which
    calls `relayout`), and a days-left line on the token tooltip;
  - `client/comps/CompsTab` is the tab itself.

### Token settlement
- Pure `core/wager/TokenSettle`: a win pays `max(0, return − covered)`, a push returns the token plus any chips added
  on top, a loss pays nothing, and slots use `fullWin`.

### Shared-file hunks (each is small and isolated)
| File | Change |
|---|---|
| `core/wager/Stake` | `Kind.TOKEN` |
| `core/wager/Stakes` | `token(player, game, table)`; TOKEN cases in `settle` / `refund` / `giveBack` |
| `core/wager/FreeBetTokens` (new) | core seam: `GAMES`, `Held`, `Provider` |
| `core/table/CasinoTableBlockEntity` | `OpenStake.token` (`Token{value, covered, expires, fullWin}`, optional codec field, 5-arg ctor kept); `tokenOffer`, `placeTokenBet`, `payToken`; raises / settle-merge keep the token; refund returns the token; `refundPart` refuses token stakes; token rounds fire `withKind(TOKEN)` |
| `games/extras/server/ExtrasGames.takeStake` | `stake:"token"`, or chips with a valid token in the main hand → `Stakes.token` (coin flip) |
| `games/extras/block/WheelBlockEntity`, `PlinkoBlockEntity` | token in hand → `placeTokenBet` (≈6 lines each) |
| `games/blackjack/BlackjackTableBlockEntity.bet` | token → `placeTokenBet`; `mainBets` stores the open stake (`r.value()`) |
| `games/slots/SlotMachineBlockEntity.startV2` (**SLT**) | token (not buy/autoplay, house only) → largest ladder bet ≤ token, `placeTokenBet(…, fullWin=true)` |
| `core/rng/StreakTracker.counts` | TOKEN rounds never count |
| `worldgen/WorldgenModule` | publishes `burmaldaholic:worldgen/casino_at` (ObjectShare) for suite night |
| `client/config/CasinoConfigScreen` | the VIP page also shows section `comps` |
| `ModuleList`, `ClientModuleList`, `namespaces.properties` | one line each |
| vip (owned): `VipService` (no VIP/contract credit for TOKEN, cashback toast, `cashbackEstimate`), `VipApi` (`vip/cashback_estimate`), `VipRules` (`store_gold_shelf` replaces `emerald_rate`; `comps` perk line; `compRate`; `PerkParams` 6th arg, 5-arg ctor kept), `VipConfig` (doc fix; `compRate.*` + ≤ 0.35 validation), `CasinoMenuScreen` (passes comp rates) |

### Strings and docs
- STRINGS.md has a new `## comps` section (EN and RU), plus the vip perk rows and the `config.*` rows.
- `gen_lang` has been run. Its CRLF output was normalised to LF.
- GAME_DESIGN §12: the Gold perk text is updated, cashback is now visible, and there is a new §12.1 Comps.
- CONFIG.md: `vip.compRate.*` and a `comps` section.
- LOCALIZATION.md: the module list includes `comps`.

## Verified (MC 26.3 only, per the owner decision of 2026-10-20; JAVA_HOME=jdk-25.0.4.1+1, all `--no-daemon`)
- `./gradlew compileJava compileClientJava compileGametestJava -Pmc=26.3`: BUILD SUCCESSFUL. 26.2 also compiled
  before support was dropped.
- `./gradlew -Pmc=26.3 test --tests 'dev.nezo.burmaldaholic.comps.*' --tests 'dev.nezo.burmaldaholic.core.wager.*'
  --tests 'dev.nezo.burmaldaholic.vip.*' --tests '*RawInputGuardTest'`: 10 suites, 50 tests, 0 failures. Covers
  CompsRulesTest, DailyBonusRulesTest, CompsLayoutTest, TokenSettleTest and VipRulesTest (with the new perk test).
- `with-heavy-lock.sh ./gradlew -Pmc=26.3 runGameTest -Dfabric-api.gametest.filter=comps_game_tests`: all 6 passed.
  - Token coin flip: a win pays winnings only; no VIP credit or points.
  - Token push returns the token; a foreign token is not accepted as a stake.
  - Roulette accrues 2.7 points; PvP, owned-casino, token and pawn rounds accrue 0.
  - Day roll sends the cashback toast.
  - Daily bonus: refused without a counter session, paid once (same-tick cooldown and a later second claim).
  - Token redemptions: limit L5, Gold gate on the feast.
- `... runGameTest -Dfabric-api.gametest.filter=vip_game_tests -Pmc=26.3`: all 3 passed.
- `tools`: `npm ci && npm run gen:assets && npm run check:assets && npm test`: assets OK, 59 tests passed.
  `python java/tools/gen_lang.py --check`: OK.
- Note: the filter must be the snake_case test id (`comps_game_tests`). `CompsGameTests` selected the whole suite;
  that run crashed in vanilla `DistanceManager.runAllUpdates` (NPE in fastutil). The crash did not involve comps code
  and no rental entities existed. A tester should check whether it happens on the base branch too.
- The daily-bonus GameTest turns `comps.daily.onePerIp` off while it runs: mock players share an address, and the
  test world keeps `CompsWorldData` between runs. The IP gate is covered by `DailyBonusRulesTest`.

## Not verified / known limits
- No client GameTest was run, and there is no client real-flow proof of the Comps tab yet. The layout is covered by
  `CompsLayoutTest`.
- Blackjack, wheel, plinko and slots token paths are covered only by compiling and by `TokenSettleTest`. No
  table-level GameTest exists for them yet.
- The blackjack split approximation: when the token hand and a chip hand net to exactly the total stake, the round
  counts as a push and the token comes back.
- Rentals and suite night have no GameTest. They need a generated casino or real entities.
- `gui.burmaldaholic.vip.perk.emerald_rate` is now unused but was left in STRINGS (`--prune` is not allowed here).
- GAME_DESIGN §3.2 rates are left to EC-1, which owns the rate change.

## Requests to other packages
- **EC-1:** the cashier and merchant should list `comps` (the cashier list `CASHIER_TABS` already does). If the exchange
  tab deposits items, it must not accept `free_bet_token`; it has no appraisal and is not a chip, so it should already
  be safe.
- **SLT:** please keep the token hunk in `SlotMachineBlockEntity.startV2` (marked `EC-2`) when you rework the bet / coin
  multiplier.
- **DL / HR:** no changes to your files.
- **Roulette, baccarat, craps, UTH (v0.2.1):** to accept tokens, add the game id to `FreeBetTokens.GAMES` and call
  `tokenOffer` / `placeTokenBet` for the main bet.
