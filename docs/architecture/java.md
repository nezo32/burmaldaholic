# Burmaldaholic — Java Edition (Fabric) architecture

Owner of this document and of `java/`: R1 (Java architect). Game rules, numbers and strings live in
`docs/design/`; this file covers **how** the Java edition is built and how ~12 developers work in
parallel without stepping on each other.

---

## 1. Toolchain (researched 2026-09-23)

| Thing | Version | Notes / source |
|---|---|---|
| Minecraft | **26.2** and **26.3** (one jar, `"minecraft": ">=26.2 <26.4"`) | `meta.fabricmc.net/v2/versions/game` |
| JDK | **25** (Temurin 25.0.4 used locally) | Minecraft 26.x and Loom 1.18 require Java 25. JDK 21 **cannot** even configure the build (Loom is compiled for Java 25). |
| Gradle | **9.7.1** (wrapper committed, SHA-256 pinned) | Loom 1.18 declares `org.gradle.plugin.api-version=9.7.0`; Gradle 9.5 fails to resolve it. |
| Fabric Loom | **1.18.2**, plugin id **`net.fabricmc.fabric-loom`** | Since MC 26.1 the game is **unobfuscated**: the new plugin id has no remapping step and **no mappings** (`mappings` line is gone). Classes use Mojang names (`net.minecraft.world.level.block.Block`, `Identifier` — formerly `ResourceLocation`). The old id `fabric-loom` is for legacy obfuscated versions. |
| Fabric Loader | **0.19.5** | |
| Fabric API | `0.161.0+26.2` / `0.161.0+26.3` | maven `net.fabricmc.fabric-api:fabric-api`. Note the renamed modules: `fabric-menu-api-v1` (was screen-handler), `fabric-creative-tab-api-v1` (was item-group), payload registries are `PayloadTypeRegistry.serverboundPlay()/clientboundPlay()`. |
| Mod Menu (optional) | `20.0.2` (26.2) / `21.0.0-beta.1` (26.3) | `https://maven.terraformersmc.com/releases/`, `com.terraformersmc:modmenu`. Compile-only; not required at runtime. |
| JUnit | Jupiter 5.14.4 | pure-logic unit tests |
| Mixin / MixinExtras | bundled with Loader (MixinExtras 0.5.5) | `compatibilityLevel: JAVA_25` |

Reference template: <https://github.com/FabricMC/fabric-example-mod> (branch for 26.3), versions page
<https://fabricmc.net/develop>.

### Gotchas found while building the skeleton
- **26.2 → 26.3 API drift exists**: block codecs were removed in 26.3 (`Block.simpleCodec`,
  `BlockBehaviour.codec()` gone). 26.2 still declares `BaseEntityBlock.codec()` abstract. Pattern that
  works on both (see `CasinoTableBlock`): implement `protected MapCodec<...> codec() { return MapCodec.unit(this); }`
  **without `@Override`** and without `simpleCodec`.
- Current screen: `Minecraft.getInstance().gui.screen()` / `gui.setScreen(...)` (the old
  `minecraft.screen` field / `setScreen` are gone). GUI drawing is `extractRenderState(GuiGraphicsExtractor, ...)`
  (identical in 26.2 and 26.3).
- Block/item properties need `.setId(ResourceKey)` (done for you by `ModRegistrar`).
- Block entity persistence uses `saveAdditional(ValueOutput)` / `loadAdditional(ValueInput)`.
- Game rules are a registry now (`net.minecraft.world.level.gamerules`; Fabric's `GameRuleBuilder`).
  The mod registers **none**: casino mode is world saved data (`core.mode`, see §7). Game rules live in
  `data/minecraft/game_rules.dat`; vanilla skips unknown entries there with a log line.
- Fabric data attachments add `getAttached/setAttached` to entities via **interface injection**
  (class tweaker) — they are not in the vanilla jar; the linkage check knows this.
- `Player#drop(ItemStack, boolean)` does not exist in 26.3 → use `core.util.Inventories.giveOrDrop`.
- 26.2 moved the entity-type constants to `EntityTypes`; compare registry ids
  (`BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath()`) or tags instead of constants. Most ore tags
  (`diamond_ores`…) are not constants in `BlockTags` either: `TagKey.create(Registries.BLOCK, Identifier.withDefaultNamespace(...))`.
- `SavedDataType(id, ctor, codec, null)` works (Fabric patches the null data-fix type); `server.getDataStorage()`
  is the world-global storage.
- 26.3's client uses SDL3; under plain `xvfb-run` it fails with "Couldn't find matching GLX visual".
  Client game tests therefore run locally only on 26.2 so far (see §6).

---

## 2. Build & test commands

All from `java/`, with `JAVA_HOME` pointing at a JDK 25.

```bash
./gradlew build                     # compile vs 26.2, all checks, unit tests, server GameTests, checkLinkage vs 26.3
./gradlew build -Pmc=26.3           # compile + test against 26.3 (CI matrix)
./gradlew build -Pmod_version=1.2.3 # release version (CI contract, docs/ci/RELEASING.md); $MOD_VERSION also honoured
./gradlew test                      # JUnit only (fast)
./gradlew runGameTest               # headless server GameTests (also part of `check`)
xvfb-run -a ./gradlew runClientGameTest   # real client tests (needs a display; NOT part of `check`)
./gradlew runClient [-Pmc=26.3] [-PwithModMenu]   # dev client, run dir run/<mc>/client
./gradlew runServer [-Pmc=26.3]                   # dev server,  run dir run/<mc>/server
./gradlew mergeLang checkNoLiterals checkAssetOwnership checkLinkage   # individual checks
```

Output: exactly one release jar `build/libs/burmaldaholic-<version>.jar` (+ `-sources.jar`).

Local JDK: this environment had only JDK 21; Temurin 25 was unpacked to `~/jdk-25.0.4.1+1`
(`curl -L https://api.adoptium.net/v3/binary/latest/25/ga/linux/x64/jdk/hotspot/normal/eclipse | tar xz -C ~`)
and used via `JAVA_HOME`. CI uses `actions/setup-java` with 25.

### Multi-version strategy
One source tree, **one release jar compiled against the lowest supported version (26.2)**, declared
for `>=26.2 <26.4`. Three safety nets:
1. `checkLinkage` (in `check`, `gradle/linkage-check.gradle`): downloads Mojang's official
   unobfuscated 26.3 client jar + Fabric API 26.3 and verifies with ASM that every class/method/field
   our bytecode references exists there (including inherited calls and newly-abstract methods).
   It caught the `simpleCodec` break above. `-PskipLinkage` to skip offline.
2. CI builds and runs unit + server GameTests with `-Pmc=26.3` (source compatibility + runtime).
3. The version table in `gradle.properties` (`supported_mc_versions`, `fabric_api_<v>`, `modmenu_<v>`).

If some API truly differs, put the two variants behind a tiny interface in `core` and pick the
implementation at runtime by checking the Minecraft version (`FabricLoader.getInstance().getModContainer("minecraft")`),
calling the version-specific one reflectively or via a class that is only loaded on that version.
Only if that becomes widespread switch to per-version jars (would require changing the CI contract).

Adding a Minecraft version: add it to `supported_mc_versions` + `supported_mc_range`, add
`fabric_api_<v>` / `modmenu_<v>`, add it to the CI variable `JAVA_MC_VERSIONS`.

---

## 3. Source layout

```
java/
  build.gradle, gradle.properties, settings.gradle, gradlew(.bat), gradle/wrapper/*
  gradle/casino-checks.gradle     lang merge + validation, literal ban, asset ownership   (core-owned)
  gradle/linkage-check.gradle     binary compatibility vs other MC versions               (core-owned)
  config/namespaces.properties    which module owns which names                           (core-owned)
  config/literal-whitelist.txt    files allowed to call Component.literal                 (core-owned)
  src/main/java/dev/nezo/burmaldaholic/      common code (both sides)
  src/client/java/dev/nezo/burmaldaholic/    client-only code (Loom split source sets)
  src/main/lang/<module>/{en_us,ru_ru}.json  lang fragments  -> merged into assets/burmaldaholic/lang/
  src/main/sounds/<module>/sounds.json       sound fragments -> merged into assets/burmaldaholic/sounds.json
  src/main/resources/                        fabric.mod.json, burmaldaholic.<module>.mixins.json, assets/, data/
  src/test/java/                             JUnit (pure logic)
  src/gametest/java/, src/gametest/resources/fabric.mod.json   GameTests (server + client)
```

### Packages (`dev.nezo.burmaldaholic.*`)

| Package | Module id | Owner | Contents |
|---|---|---|---|
| `Burmaldaholic`, `ModuleList` | – | core | entrypoint + the (pre-filled) module list |
| `core` | `core` | J-core | `CoreModule`, sub-packages below |
| `core.module` | | core | `CasinoModule`, `ModuleContext`, `ModuleLoader`, `Namespaces` |
| `core.registry` | | core | `ModRegistrar` (blocks/items/BE/menus/sounds), `CasinoCreativeTab` |
| `core.mode` | | core | `CasinoMode` (`isEnabled` guard, `set`, `onChange` listeners, SERVER_STARTING bootstrap), `CasinoModeData` (`data/burmaldaholic/mode.dat`), `PendingCasinoMode` (Create World hand-off) |
| `core.config` | | core | `CasinoConfig` (typed access to every CONFIG.md key), `ConfigManager`, `ConfigBinder`, `@Range/@Size/@Family/@Member`, `sections/*Config` |
| `core.economy` | | core | `Economy` API (+ `Batch`, `Bankrolls`, `CreditHook`), `AccountId`, `Economies` locator, `Ledger` (pure), `LedgerEconomy` |
| `core.data` | | core | `CasinoWorldData` (world saved data: balances, bankrolls, player records), `PlayerRecord` |
| `core.chips` / `core.cashier` | | core | chip items, `ChipMath`, Casino Card (opens the Casino Menu), `CashierBlockEntity`, `CashierShop` (Shop tab offers) |
| `core.earnings` | | core | ores / mobs / trades (`Earnings`, pure `RewardRules`), merged action-bar toasts |
| `core.wager` | | core | `Stakes` (chips, item, XP, hearts, soul), `BetLimits`, `Wagers` + `WagerVeto` (wager gate), `HouseEdges` (§17), `HeartPenalties`, `PawnRules` (pure) |
| `core.service` | | core | `CoreServices` providers (`VipTierProvider`, `DebtProvider`, `GoldenHourProvider`, `TableOwnershipProvider`, `ClaimProvider`), `VipTiers` |
| `core.command` | | core | `/casino` (alias `/burmaldaholic`) + `CasinoCommands.extend` |
| `core.util` | | core | `Result<T>`, `Inventories` |
| `core.events` | | core | `CasinoEvents` (`PLAY_RESOLVED`, `BALANCE_CHANGED`, `TABLE_LEFT`, `RAKE_COLLECTED`), `PlayResults` (fire + offline queue) — cross-module bus |
| `core.advancement` | | core | `CasinoAdvancements` (§19 grants, offline queue; JSON from `java/tools/gen_advancements.py`) |
| `core.menu` | | core | `CasinoMenu` (server pages of the Casino Menu), `CoreMenu` (payloads, Achievements page) |
| `core.network` | | core | `Payloads` helper, generic `TableActionPayload`/`TableSyncPayload`, `CasinoModeSyncPayload` |
| `core.rng` | | core | `OddsService` (fair RNG + streak re-draw `play`), `CasinoRng`, `OddsModifier`, `OddsContext`, `StreakRules` (pure), `StreakTracker` (persistent) |
| `core.text` | | core | `Plural` (p1/p21/p2/p5), `Texts` (numbers, plural components) |
| `core.table` | | core | `CasinoTableBlock` (facing, ticker), `CasinoTableBlockEntity` (seats, bets, timers, refunds), `TableSeats`, `CasinoTableMenu`, `TableType`, `TableRegistrar` |
| `core.mixin[.client]` | | core | e.g. `LevelStorageAccessMixin` + `MinecraftServerStorageAccessor` (pending mode), client `CreateWorldGameTabMixin` (button), `WorldCreationUiStateMixin` (choice), `CreateWorldScreenMixin` (hand-off) |
| `core.pvp`, `core.pvp.logic` | | core | PvP engine (`Pvp.service()`), mode contract `PvpMode`, `PvpModes`, persistence — see `pvp-bots.md` |
| `core.bots`, `core.bots.logic` | | core | Seats & Bots: `SeatOccupant`, `BotPolicy`, `TableBots`, purses, jobs, heat ledger — see `pvp-bots.md` |
| `client` (client set) | | core | `BurmaldaholicClient`, `ClientModuleList`, `CoreClientModule`, `ClientCasinoState`, `client.hud` (`CasinoHud`, `HudSegment`), `client.table.CasinoTableScreen`, `ClientTableCache`, `client.cashier`, `client.config` (generated Mod Menu screen) |
| `games.blackjack` | `blackjack` | dev | |
| `games.poker` | `poker` | dev | |
| `games.slots` | `slots` | dev | |
| `games.roulette` | `roulette` | dev | |
| `games.craps` | `craps` | dev | |
| `games.extras` | `extras` | dev | coin flip, wheel, scratch cards, plinko, dice duel |
| `loan` | `loan` | dev | loan shark, debt, collectors |
| `chaos` | `chaos` | dev | chaos events, golden hour (implements `GoldenHourProvider`); streak math itself is core |
| `lastchance` | `lastchance` | dev | |
| `worldgen` | `worldgen` | dev | structures (+ data pack) |
| `vip` | `vip` | dev | |
| `multiplayer` | `multiplayer` | dev | ownership, house cut, shared tables |
| `pvp` | `pvp` | dev | PvP hub, lobby/result screens, presenter, `/casino pvp` (modes live in extras / slots) |
| `bots` | `bots` | dev | table settings, private tables, `/casino table …`, `/casino bots …`, chatter, avatars |

Each feature package `X` contains `XModule` (common) and, in the client source set,
`X.client.XClientModule`. Both already exist as stubs and are already listed in `ModuleList` /
`ClientModuleList` — **nobody edits those lists**. Suggested internal layout per feature:
`logic/` (pure Java rules, unit-tested), `block/`, `item/`, `net/`, `mixin/`, `client/`.

---

## 4. Module contract

```java
public interface CasinoModule {            // one per feature, e.g. BlackjackModule
    String id();                           // "blackjack" — prefix of everything the module owns
    void register(ModuleContext ctx);      // runs once at mod init, both physical sides
}
public interface CasinoClientModule {      // client source set, e.g. BlackjackClientModule
    String id();
    void registerClient(ClientModuleContext ctx);
}
```

Rules:
- Constructors are trivial (unit tests instantiate `ModuleList`). Register everything inside
  `register`; keep references in `static` fields of your own classes.
- `register` must not touch worlds or config *values* (config is loaded on `SERVER_STARTING`).
- Every gameplay path checks **`CasinoMode.isEnabled(server | level | player)`**. Generic table
  interaction and `ctx.payloads().serverbound(...)` handlers are already gated by core. To react to
  an on/off switch (command or test), register `CasinoMode.onChange((server, enabled) -> ...)`
  (server thread, fired only when the value changes); core uses it for the client sync and the
  first-join welcome.
- Modules never import another feature's package. Talk through `core` APIs:
  `Economies.get()` for chips, `CasinoEvents` for notifications, `OddsService.get().addModifier(...)`
  for odds effects. Need something new in core? Ask J-core (it's a shared package).
- All randomness through `OddsService.get().rng(new OddsContext(player.getUUID(), ID, bet))`:
  `nextInt/shuffle` are fair and never modified; `chance(p)` / `weighted(..., favourable)` pass the
  player-favourable probability through modifiers (order: 200 vip, 300 chaos, 400 lastchance). RNG games
  (slots, wheel, plinko, scratch, coin flip) draw through `OddsService.play(...)`, which applies the §14 streak re-draw.
- After settling a bet report it with `PlayResults.fire(player, PlayResult.of(ID, bet, payout)...)` — never the
  `PLAY_RESOLVED` invoker directly (feeds streaks, VIP progress/cashback, contracts, Golden Hour, statistics,
  advancements; queued for offline players). Table `settle`/`settleBet` and `Stakes.settle` do it for you.

### `ModuleContext` API
| Call | Does |
|---|---|
| `ctx.id("blackjack_table")` | `burmaldaholic:blackjack_table`, **throws** if the name is not owned by the module |
| `ctx.registry().block/blockWithItem/item/blockEntity/menu/sound(...)` | vanilla registration with `setId`, creative-tab auto-add |
| `ctx.tables().register("blackjack_table", BlackjackTableBlockEntity::new)` | block + item + BE type + menu type → `TableType` |
| `ctx.payloads().serverbound(name, codec, handler)` / `clientbound(name, codec)` | custom payloads (C2S handler gated by casino mode) |
| `CasinoConfig.blackjack().decks` | every CONFIG.md key (core registers all sections); `ctx.config("blackjack_x", ...)` only for extra, non-spec sections |
| `ctx.hudSegment(...)` (client) | add lines to the casino HUD |
| `ctx.key("gui", "hit")` | `gui.burmaldaholic.<module>.hit` |

### Ownership (`java/config/namespaces.properties`)
A module owns a name if it equals, or starts with `<ns>_`, `<ns>.` or `<ns>/`, where `<ns>` is the
module id or one of its extra namespaces (from the design ids, e.g. `slots=slot_machine,jackpot`,
`chaos=streak,golden_hour`). `core=*`. The same file drives the runtime `ctx.id()` check and the
Gradle checks, so id collisions between developers are impossible.

---

## 5. Recipes

### A table game (server-authoritative)
```java
// games/blackjack/BlackjackModule.java
public static TableType<BlackjackTableBlockEntity> TABLE;
public void register(ModuleContext ctx) {
    TABLE = ctx.tables().register("blackjack_table", BlackjackTableBlockEntity::new);
}
// games/blackjack/BlackjackTableBlockEntity.java
public class BlackjackTableBlockEntity extends CasinoTableBlockEntity {
    public BlackjackTableBlockEntity(TableType<BlackjackTableBlockEntity> t, BlockPos p, BlockState s) { super(t, p, s); }
    @Override public void onAction(ServerPlayer player, String action, CompoundTag args) { /* validate, mutate, */ syncViewers(); }
    @Override public CompoundTag writeClientState(ServerPlayer viewer) { /* only what viewer may see */ }
}
// client: games/blackjack/client/BlackjackClientModule.java
ctx.tableScreen(BlackjackModule.TABLE, BlackjackScreen::new);   // BlackjackScreen extends CasinoTableScreen
```
Flow: right-click → core checks casino mode → opens `CasinoTableMenu` (carries only `BlockPos`) →
BE sends `TableSyncPayload` (cached client-side, so ordering doesn't matter) → screen renders
`state()` → buttons call `sendAction("hit", args)` → core validates the player has *this* table open
and is in range → `onAction`. The client never decides outcomes.

### Blocks / items
`ctx.registry().blockWithItem("slot_machine_copper", SlotMachineBlock::new, props)`. A custom
`BaseEntityBlock` must use the cross-version `codec()` pattern from §1.

### Assets and data
Put files under `src/main/resources/assets/burmaldaholic/...` or `data/burmaldaholic/...` with an
owned file name (`blockstates/blackjack_table.json`, `textures/block/blackjack_table.png`) or inside
an owned folder (`textures/gui/blackjack/cards.png`). `checkAssetOwnership` fails the build otherwise.
Never create `assets/burmaldaholic/lang/*` or `sounds.json` — they are generated.

### Lang
Fragments are **generated** from `docs/design/STRINGS.md` by `python3 java/tools/gen_lang.py` (§9.1) and already
contain every key of your module. Edit only `src/main/lang/<module>/en_us.json` **and** `ru_ru.json` (same keys,
same placeholders) and only for keys that are not (yet) in STRINGS.md — the generator keeps those. `mergeLang` fails on: missing/extra keys between languages,
placeholder mismatch (`%s`, `%1$s`; anything else like `%d` is rejected), duplicate keys across modules,
keys outside the module's namespaces, non-string values, incomplete plural sets.
Key shape: `<category>.burmaldaholic.<owned-name>...`, e.g. `block.burmaldaholic.blackjack_table`,
`gui.burmaldaholic.blackjack.hit`, `config.burmaldaholic.streak.max`.

**Plurals**: define `<base>.p1`, `.p21`, `.p2`, `.p5` in both languages and use
`Texts.chips(n)` / `Texts.plural("unit.burmaldaholic.heart", n)` (or `Plural.key(base, n)`). p1: n==1; p21: n%10==1 &&
n%100!=11 (21, 101…); p2: n%10∈2..4 && n%100∉12..14; p5: everything else.

**No hardcoded text**: `checkNoLiterals` bans `Component.literal(`, bare `literal(`,
`Component.nullToEmpty(` in main/client sources. Use `Component.translatable` or `Texts.number(n)`.
Language-neutral exceptions: a line tagged `// literal-ok: <reason>` (reviewed) or a file in
`config/literal-whitelist.txt` (core only).

### Sounds
`ctx.registry().sound("slots_spin")` + `src/main/sounds/slots/sounds.json` with `{"slots_spin": {...}}`.

### Payloads
Most games need none (generic table payloads). Otherwise: a record implementing
`CustomPacketPayload` with a `StreamCodec`, registered via `ctx.payloads()`; client receivers via
`ClientPlayNetworking.registerGlobalReceiver` in your client module. Validate every field server-side.

### Config
Every key of `docs/design/CONFIG.md` already exists (see §9.4): read it with `CasinoConfig.<section>()`, e.g.
`CasinoConfig.blackjack().decks`, `CasinoConfig.chaos().weight.get("mob_wave")`. Always call the accessor
again (values change on reload / world override / client sync). New keys: ask core.

### Mixins
Each module has its own pre-registered config `src/main/resources/burmaldaholic.<module>.mixins.json`
with package `dev.nezo.burmaldaholic.<pkg>.mixin`; add class names to `mixins` (common),
`client` (put the class in the client source set, sub-package `client.`) or `server`.
Access wideners/class tweakers are a shared file — request from core.

### Tests
- Pure rules in `games/<x>/logic` → JUnit under `src/test/java/...` (no Minecraft bootstrap; don't
  touch registries). Use `new OddsService(new SplittableRandom(seed))` for deterministic RNG.
- In-world behaviour → `src/gametest/java/dev/nezo/burmaldaholic/gametest/<module>/…Tests.java`
  with `@net.fabricmc.fabric.api.gametest.v1.GameTest` methods taking `GameTestHelper`; add the class to
  `fabric-gametest` in `src/gametest/resources/fabric.mod.json`. Client flows implement
  `FabricClientGameTest` (`fabric-client-gametest` entrypoint).

---

## 6. Rules for feature developers (checklist)

1. Touch only: your package(s) `dev/nezo/burmaldaholic/<pkg>/**` in `src/main/java` and
   `src/client/java`, `src/main/lang/<module>/`, `src/main/sounds/<module>/`, owned files under
   `assets|data/burmaldaholic/`, your `burmaldaholic.<module>.mixins.json`, your tests under
   `src/test/.../<pkg>/` and `src/gametest/.../gametest/<module>/`.
2. Shared files (ask core): `ModuleList`, `ClientModuleList`, `core/**`, `client/` root package,
   `build.gradle`, `gradle.properties`, `gradle/*.gradle`, `config/*`, `fabric.mod.json`,
   `src/gametest/resources/fabric.mod.json` (one-line additions are fine in your PR, flag them).
3. Guard with `CasinoMode.isEnabled`; server decides, client renders.
4. Only translatable text, EN + RU together, plurals as p1/p21/p2/p5.
5. Money only through `Economies.get()`, randomness only through `OddsService`, cross-module
   communication only through `CasinoEvents` / core APIs.
6. Must compile and link on every supported Minecraft version: run `./gradlew build` (includes
   `checkLinkage`) and, when touching Minecraft APIs, `./gradlew build -Pmc=26.3`.

---

## 7. Manual testing

- `./gradlew runClient` → Singleplayer → Create New World: the **Game** tab has a
  **"Casino Mode: ON/OFF"** button directly below "Difficulty" (default OFF). It is **not** a game
  rule: the choice is stored in `WorldCreationUiState` (duck `client.CasinoModeCreationState`), handed
  to the new world's `LevelStorageAccess` when Create is pressed, and written to
  `<world>/data/burmaldaholic/mode.dat` on SERVER_STARTING with an immediate save. In game (operators,
  permission level 2): `/casino mode on|off|status`. Worlds without `mode.dat` start OFF; an
  unreadable file logs a warning and resets to OFF. The client GameTest checks the placement, the saved
  file, Cancel, re-open, and writes `jmode_create_world_{en_us,ru_ru}.png`. Server GameTests turn the
  mode on in `GameTestCasinoMode`; client tests opt in with `ClientTestWorlds.casino`.
- `./gradlew runClient -PwithModMenu` adds Mod Menu → Mods → Burmaldaholic → config screen.
- `./gradlew runServer` then `runClient` and connect to `localhost` for multiplayer checks
  (dev server has `online-mode` handled by Loom's dev launch; accept the EULA in `run/<mc>/server/eula.txt`).
- Worlds/configs live in `java/run/<mc>/{client,server}` (git-ignored). Config file:
  `run/<mc>/client/config/burmaldaholic.json`.
- Client game tests: `xvfb-run -a ./gradlew runClientGameTest` (26.2 verified here; 26.3's SDL3
  window needs a GLX-capable virtual display — open item for CI/testers).

---

## 8. What exists

Core (this wave): lang fragments generated from STRINGS.md for all 13 modules (1 380 keys, `java/tools/gen_lang.py`),
chips / Casino Card / Cashier + Nether Cashier (textures from `java/tools/gen_core_assets.py`, models, loot, recipes),
world-saved ledger with atomic batches and bankroll accounts, earnings (ores, mobs, trades), every CONFIG.md key with
clamping, per-world override, `/casino config`, client sync and a generated Mod Menu screen, HUD with extension point,
player status sync, persistent streak + §14 re-draw, stake service (chips / item / XP / hearts / soul), table framework
(seats, bets, bankroll exposure, timers, disconnect/restart refunds), `/casino balance`. Tests: unit tests for all pure
logic (incl. a CONFIG.md coverage test), server GameTests for economy/cashier/stakes/trades, and the client GameTest
(screenshots of the config screen, HUD and cashier in `build/run/clientGameTest/screenshots`).

Integration pass (after all feature modules merged): enriched `PlayResult` + offline queue (`PlayResults`), wager
gate (`Wagers`, replaces loan's `BetLimits` mixin), claim provider, owned-table bots flag, rake hook, table-left event,
per-bet stakes (craps migrated) and silent refunds, broken tables play their rounds out (review B1), heart penalties
pause while casino mode is off (review M2), §19 advancements (data-driven, `CasinoAdvancements`), the general Casino
Menu (vip screen + server pages: Loan, Achievements, Challenges, My Casino, Rules), Cashier Shop tab, core sound
events mapped to vanilla sounds (`src/main/sounds/core/sounds.json`).

Still open in core: chip font glyphs (U+E100…, UI.md §0.1 — the HUD uses the chip item icon; Java screens render
cards/dice natively), per-player HUD settings / Settings & Admin menu pages, custom .ogg sounds (optional).

---

## 9. Core API for feature devs

Everything below is server-side and server-thread only unless marked *client*. All of it is guarded by
casino mode already where noted; your own entry points (items, blocks, events) must still check
`CasinoMode.isEnabled(...)`.

### 9.1 Lang (`java/tools/gen_lang.py`)
```bash
python3 java/tools/gen_lang.py           # regenerate src/main/lang/<module>/{en_us,ru_ru}.json from docs/design/STRINGS.md
python3 java/tools/gen_lang.py --check   # CI/pre-commit: exit 1 if a fragment differs from the spec
python3 java/tools/gen_lang.py --prune   # also drop keys that are not in STRINGS.md
```
- Every STRINGS.md row goes to exactly one module: the feature module that **owns the key's name**
  per `config/namespaces.properties` (e.g. `block.burmaldaholic.blackjack_table` → blackjack,
  `hud.burmaldaholic.streak.lucky` → chaos because chaos owns `streak`, `advancement.burmaldaholic.jackpot.*` → slots),
  otherwise core. So your fragment already holds all your keys — **use them, don't rename them**.
- Re-running overwrites spec keys with the spec text and **keeps** extra keys (warning) unless `--prune`.
  New player-facing text goes into STRINGS.md first (design), then re-run. Java-only keys that cannot be in
  STRINGS.md go into `java/tools/lang_java_only.json` (core).
- Numbers: `Texts.number(n)` ("12 500"); counted nouns: `Texts.chips(n)` (nominative, arg type `chips`),
  `Texts.chipsAcc(n)` (`chips_acc`), `Texts.plural("unit.burmaldaholic.heart", n)`. Ids/symbols only: `Texts.raw(s)`.

### 9.2 Money — `Economies.get()` (`core.economy.Economy`)
```java
long   balance(ServerPlayer p)                         long balance(MinecraftServer s, UUID offline)
boolean tryWithdraw(ServerPlayer p, long n, Transaction why)   // atomic; false = changed nothing
long   deposit(ServerPlayer p, long n, Transaction why)       // returns chips actually added (cap, garnishment)
long   deposit(MinecraftServer s, UUID offline, long n, Transaction why)
void   setBalance(MinecraftServer s, UUID p, long n, Transaction why)   // admin
TxResult transfer(MinecraftServer s, AccountId from, AccountId to, long n, Transaction why)
Batch  batch(MinecraftServer s)   // .debit(acc, n).credit(acc, n)...commit(why) — all legs or none
Bankrolls bankrolls(MinecraftServer s)  // open(id, owner) get(id) reserve(id, n) release(id, n) close(id)
void   addCreditHook(CreditHook h)      // loan: garnish PAYOUT/EARNING credits
```
- `AccountId.player(uuid)`, `AccountId.bankroll("multiplayer:charter/...")`, `AccountId.HOUSE` (infinite bank).
- `Transaction.bet(gameId)`, `.payout(gameId)`, `.refund(gameId)`, `.earning(detail)`, `Transaction.of(module, detail)`;
  `Kind` decides garnishment (`PAYOUT`, `EARNING` are garnishable).
- Balances live in world saved data (`CasinoWorldData`): survive death, work offline, capped at `economy.maxBalance`
  (excess lost + `msg.burmaldaholic.core.balance_capped`). `CasinoEvents.BALANCE_CHANGED` fires for online players.
```java
// PvP dice duel: both stakes escrowed atomically, winner paid minus rake
eco.batch(server).debit(AccountId.player(a), s).debit(AccountId.player(b), s).credit(AccountId.HOUSE, 2 * s).commit(Transaction.bet("extras"));
```

### 9.3 Randomness — `OddsService.get()`
```java
CasinoRng rng = OddsService.get().rng(new OddsContext(player.getUUID(), "roulette", bet)); // table games: fair only
int pocket = rng.nextInt(37);  rng.shuffle(deck);  rng.nextDouble();
// RNG games (slots, wheel, plinko, scratch, coin flip): §14 streak re-draw built in
Spin s = OddsService.get().play(ctx, 0.8976 /* RTP §17 */, () -> machine.spin(rng), r -> r.payout() < bet);
OddsService.get().addModifier("chaos", 300, (ctx, p) -> p);   // optional tilt of rng.chance()/weighted()
```
`StreakTracker.get(uuid)` reads the persistent streak (decays lazily); core updates it from `PLAY_RESOLVED` and sends
the §14 streak messages. `debug.fixedSeed ≠ 0` makes all draws deterministic (tests).

### 9.4 Config — `CasinoConfig`
All 19 CONFIG.md sections are registered by core (`core/config/sections/*Config.java`): `CasinoConfig.core()`,
`economy()`, `contracts()`, `wager()`, `vip()`, `blackjack()`, `poker()`, `slots()`, `roulette()`, `craps()`,
`extras()`, `loan()`, `chaos()`, `streak()`, `lastChance()`, `worldgen()`, `ownership()`, `multiplayer()`, `debug()`.
Families are maps: `chaos().weight.get("mob_wave")`, `chaos().event.get("curse").enabled`,
`contracts().weight`, `wager().appraisal` (item id → chips), `slots().copper.weights/pays/berryPartial`.
- Files: `config/burmaldaholic.json` (global, rewritten complete) + `<world>/data/burmaldaholic_config.json` (override,
  wins). Wrong type → that field's default, out of range → clamped (`@Range`), unknown → dropped; all logged.
- `/casino config get|set|reset <key> [value]`, `/casino config reload` (level 2; `set` writes the world override).
- Clients receive the effective config on join/change (`ConfigSyncPayload`), so `CasinoConfig.x()` on the client
  shows the server's limits. Mod Menu: generated editor for the global file (labels `config.burmaldaholic.<key>`).
- Adding a key = add a field (with `@Range`) to the section class + the row to CONFIG.md/STRINGS.md; ask core.

### 9.5 Bets and stakes — `core.wager`
```java
Component err = BetLimits.validate(player, amount, min, tableMax);   // null = ok; min ≤ bet ≤ min(tableMax, VIP max) ≤ balance
long max = BetLimits.maxBet(player, tableMax);
Result<Stake> r = Stakes.chips(player, "extras", amount, min, tableMax); // validates + debits (house-banked)
Result<Stake> r = Stakes.heldItem(player, "extras");      // appraisal table, undamaged/unenchanted/unnamed, escrowed
Result<Stake> r = Stakes.xp(player, "extras", levels);    // V = floor(points / wager.xp.pointsPerChip), escrowed
Result<Stake> r = Stakes.hearts(player, "extras", h);     // −2h max health for wager.hearts.durationTicks on loss
Result<Stake> r = Stakes.soul(player, "extras");          // Hardcore + wager.hardcoreSoulWager; coin flip only
if (!r.isOk()) { show(r.error()); return; }
Stakes.settle(player, r.value(), Stakes.Outcome.WIN, (long) Math.floor(r.value().value() * 0.96)); // fires PLAY_RESOLVED
Stakes.refund(player, stake);                             // cancelled round
```
`Stake.value()` is V in chips (what you pay against and what counts as wagered). Only offer pawn stakes where §4.3 allows.

### 9.6 Tables — `CasinoTableBlockEntity`
```java
public class BlackjackTableBlockEntity extends CasinoTableBlockEntity {
    @Override protected int seatCount() { return CasinoConfig.blackjack().seats; }
    @Override protected long minBet() { return CasinoConfig.blackjack().minBet; }
    @Override public void onAction(ServerPlayer p, String action, CompoundTag args) {
        switch (action) {
            case "bet" -> {
                long amount = args.getLongOr("amount", 0);
                if (placeBet(p, amount, 8 * amount /* worst-case payout, §18.2 */).isOk()) { setPhase("betting"); startTimer("bet", CasinoConfig.blackjack().betTimerTicks); }
            }
            case "double" -> placeBet(p, stakeOf(p.getUUID()), minBet(), 0, 0, false); // doubles may exceed the max
        }
        syncViewers();
    }
    @Override protected void onTimer(String id) { if (id.equals("bet")) deal(); }
    @Override protected void onPlayerLeft(UUID id, LeaveReason why) { standAndSettle(id); } // §4.1 auto-complete
    void finish(UUID id, long totalReturn) { settle(id, totalReturn); }  // pays house/bankroll, PLAY_RESOLVED, streak
    @Override public CompoundTag writeClientState(ServerPlayer viewer) { CompoundTag t = baseState(viewer); /* + your fields */ return t; }
}
```
- Free: `"sit"` / `"leave"` actions, seats (`seats()`, `sit`, `leave`, `isSeated`), distance/disconnect removal
  (`multiplayer.tableLeaveDistance`), `placeBet` (validation incl. owned-table rules: owner can't play, closed,
  owner min/max, bankroll reservation → `house_broke`/`exposure`), `settle(uuid, payout)` / `refund(uuid)` (offline-safe),
  open stakes saved with the block entity and **refunded on reload** only after a crash (`core.roundTimeoutRefund`); a broken table
  plays its rounds out first (§9.11), `setPhase`/`startTimer`/`ticksLeft`/`onTimer`, `sendError(player, component)` (red line on the screen),
  `baseState(viewer)` (phase, timers, seats, seat, stake, balance, min, max). Default `onPlayerLeft` refunds.
- Blocks face the placer (`CasinoTableBlock.FACING`): blockstate needs `facing=north|east|south|west` variants.
- *client* `CasinoTableScreen`: `state()`, `sendAction`, `balance()/minBet()/maxBet()/myStake()/mySeat()/phase()`,
  `timerSeconds(id)`, `seatNames()`, `limitsLine()`, `button(label, x, y, minWidth, onPress)` (auto-width for RU),
  `showError`, felt background. Register with `ctx.tableScreen(MyModule.TABLE, MyScreen::new)`.

### 9.7 Providers — `CoreServices` (implement once, in your module's `register`)
| Module | Call | Core uses it for |
|---|---|---|
| vip | `CoreServices.setVip((server, uuid) -> tier)` (`VipTierProvider`, may override `maxBet`) | bet limits, HUD badge, emerald rate, stakes |
| loan | `CoreServices.setDebt(DebtProvider)` (`owed`, `inDefault`, `ticksToDeadline`) + `Economies.get().addCreditHook(...)` | cashier withdrawable/freeze, HUD loan line, garnishment |
| chaos | `CoreServices.setGoldenHour(server -> remainingTicks)` | HUD timer, gold balance |
| multiplayer | `CoreServices.setTableOwnership((level, pos) -> Optional.of(new OwnedTable(owner, bankrollId, min, max, open)))` | bets/payouts via bankroll, exposure, owner rules |
`VipTiers.name(tier)` (colored translated name), `VipTiers.maxBet(tier)` (config).

### 9.8 Events, earnings, commands, HUD
- `PlayResults.fire(server, uuid, result)` / `fire(player, result)` — report every settled wager if you don't use
  `settle`/`settleBet`/`Stakes.settle` (they do). Offline player → stored in the world data and fired on the next join
  with `result.deferred() == true` (chips were already credited at settlement; listeners must not pay again — e.g.
  Golden Hour pays a deferred round only if `result.goldenHour()` was stamped at settlement; VIP skips particles).
  Listen with `CasinoEvents.PLAY_RESOLVED`. `CasinoEvents.BALANCE_CHANGED` — online balance changes.
- `CasinoEvents.TABLE_LEFT` (level, pos, player, `LeaveReason`) — a seated player left a table (core seats; poker fires
  it on cash-out). `CasinoEvents.RAKE_COLLECTED` (level, pos, rake, bankroll) — PvP rake collected (owned casinos).
- `Earnings.markNoReward(entity)` — chaos-wave mobs and debt collectors never pay kill rewards (spawner mobs are
  marked automatically).
- `CasinoCommands.extend(root -> root.then(Commands.literal("debt")...))` — sub-commands under `/casino`.
- *client* `ctx.hudSegment("loan_hud", 250, (hud, out) -> out.accept(HudLine.of(text)))` — HUD lines; read
  `ClientCasinoState` / your own synced data. Core segments: 0 balance, 100 streak + VIP, 200 loan, 300 Golden Hour.
- `CoreContent.CASHIER`, `Chips.item(100)`, `ChipItem.valueOf(stack)`, `Inventories.giveOrDrop(player, stack)`
  (use it instead of `Player#drop`, which changed in 26.3).

### 9.9 Settled rounds — `CasinoEvents.PlayResult`
```java
PlayResult r = PlayResult.of("roulette", staked, totalReturn)   // house-banked chips, edge = HouseEdges.of(game)
    .withEdge(HouseEdges.CRAPS_PASS)       // the bet's own §17 edge (slots per tier, plinko per risk, craps Odds 0 %)
    .withTags("red", "straight")           // bet details (roulette bet types, craps kind, slots tier, "natural"...)
    .withKind(Stake.Kind.HEARTS)           // pawn stakes (Stakes.settle sets it)
    .withTable(level, pos, bankrollId)     // table position + owned-casino bankroll ("" = world bank)
    .pvp();                                // PvP pot / duel: houseBanked=false, edge 0
PlayResults.fire(player, r);
```
Fields: `gameId, bet, payout, houseBanked, stakeKind, houseEdge, tags, table, bankroll, deferred, goldenHour`; helpers
`won() lost() net() pawn() ownedCasino() theoreticalLoss()`. Consumers: VIP cashback = `rate × Σ bet × houseEdge` over
world-bank chip rounds only (`houseBanked && !ownedCasino && !pawn`, GAME_DESIGN §12 CHANGED); Golden Hour uses
`houseBanked` (not game ids); multiplayer statistics use `table`/`bankroll`; advancements use `stakeKind`/`goldenHour`.

### 9.10 Wager gate — `Wagers` / `WagerVeto`
Every NEW stake passes `Wagers.check(player, new WagerVeto.Context(gameId, kind, tablePos, pvp))`: casino mode, owned-table
rules (owner can't play, closed/insolvent casino) and module vetoes. Called by `BetLimits.validate(..., ctx)`, the table
`placeBet` (not when raising an already open bet: doubles, odds), `Stakes.heldItem/xp/hearts/soul(..., tablePos)` and PvP
entries (poker buy-in, dice duels). Modules add vetoes: `Wagers.addVeto((player, ctx) -> frozen ? error : null)` (loan
Asset Freeze).

### 9.11 Tables: per-bet stakes, refunds, removal, rake
- `placeBet(player, betId, amount, min, tableMax, worstCase, checkLimits)` / `settleBet(uuid, betId, payout, detail)` /
  `refundBet(uuid, betId, silent)` / `stakeOf(uuid, betId)` — bets that resolve one by one (craps: one per bet id). The
  aggregate API (`placeBet(player, amount, ...)`, `settle(uuid, payout[, detail])`, `refund(uuid[, silent])`) keeps working;
  `settle` closes ALL of the player's bets as one round. `silent = true`: no "round refunded" line (roulette Clear).
- `houseEdge()` — override for the table's own edge (slots per tier).
- Broken table (`preRemoveSideEffects`): everybody leaves with `REMOVED` (treat it like a disconnect: default action),
  then `playOutForRemoval(level)` plays rounds in play out (default: fast-forwards the game's timers; roulette spins
  now, poker plays the hand out with check/fold + bots, craps rolls the bets out); only bets of a round that has not
  drawn yet are refunded (review B1).
- The same play-out runs whenever a table stops running (review M1, `CasinoTableBlockEntity#playOutNow`, driven by
  `core.table.TableLifecycle`): its chunk unloading (`FULL_CHUNK_STATUS_CHANGE` → `INACCESSIBLE`, which vanilla fires
  before the chunk is saved) and `SERVER_STOPPING` (before players are removed and the world is saved; players get
  `msg.burmaldaholic.core.round_played_out`). Games with rounds that are not core stakes override `hasRoundInPlay()`
  (poker). Casino mode off: craps and poker call `playOutNow` too. Only a crash still leaves stakes to refund on load.
- Open stakes keep the bankroll their round started with (review M2): raises and further bets of a player's open round
  go to, and are reserved on, that bankroll even if the table was linked/unlinked since.
- `pay` returns the stake as a non-garnishable `TRANSFER` (`stake_return`) and only the winnings as `PAYOUT`
  (review m8; `Stakes.settle` likewise).
- `collectRake(rake)` — PvP rake: moves it from the bank to the owner's bankroll at owned tables and fires
  `RAKE_COLLECTED`. `OwnedTable.bots()` — owner's poker-bots switch. `firePlayerLeft(uuid, reason)` for own seat models.

### 9.12 Claims, advancements, Casino Menu, Cashier Shop, sounds
- `CoreServices.claims()` (`ClaimProvider`: `isClaimed(level, pos)`, `ownerAt(level, pos)`; multiplayer implements it).
  Chaos skips mob waves / teleports in claims and never teleports or spawns into one; loan collectors never spawn in
  another player's casino.
- `CoreServices.tablePresets()` (`TablePresetProvider`, worldgen implements it): fixed settings of generated tables —
  `TablePreset.parlorPoker()` (Low stakes, max 3 bots), `highRollerBlackjack()` / `highRollerRoulette()` (the tables act
  as High-Roller tables; a bet there grants `high_roller`). Read with `CasinoTableBlockEntity#preset()`.
- `CasinoAdvancements.grant(player, "hot_shooter")` / `grant(server, uuid, id)` (offline-safe). Ids = GAME_DESIGN §19,
  advancement files `data/burmaldaholic/advancement/core/<id>.json` (`python3 java/tools/gen_advancements.py`), each with
  one `minecraft:impossible` criterion. Core grants root / first_bet / beginners_luck / heart_on_the_line / devils_deal /
  golden_hour / on_fire / black_cat itself. Worldgen should call `grant(player, "piglin_parlor")` / `"high_roller"`.
- `CasinoMenu.register(new CasinoMenu.Page() {...})` — a server-rendered Casino Menu tab (lines + buttons, optional
  amount field, actions validated server-side; `client:advancements` opens the vanilla advancement screen).
  `CasinoMenu.open(player, pageId)` opens the menu (Casino Card, Diamond Card; key B on the client). The screen lives
  in the vip client module (it also draws Wallet / VIP / Contracts); order: wallet 10, contracts 20, loan 30,
  achievements 40, challenges 50, my_casino 60, rules 70.
- `CashierShop.add(new CashierShop.Offer(id, itemSupplier, priceSupplier, minTier, enabledSupplier))` — Cashier Shop tab
  (extras registers the scratch cards; Gold is VIP-gated).
- `CoreSounds.CHIP_PLACE / CARD_DEAL / CARD_SHUFFLE / SLOT_SPIN / WIN / LOSE / WHEEL_TICK / SCRATCH / COLLECTOR_KNOCK` —
  mapped to vanilla sound events in `src/main/sounds/core/sounds.json` (a resource pack may replace them).
- `HeartPenalties` pause while casino mode is off (modifiers removed, expiry shifted by the dormant time, review M2).

