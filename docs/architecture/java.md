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
- Game rules are a registry now (`net.minecraft.world.level.gamerules`); use Fabric's
  `GameRuleBuilder`. Lang keys: `gamerule.<ns>.<path>`, `.description`, category `gamerule.category.<ns>.<path>`.
- Fabric data attachments add `getAttached/setAttached` to entities via **interface injection**
  (class tweaker) — they are not in the vanilla jar; the linkage check knows this.
- 26.3's client uses SDL3; under plain `xvfb-run` it fails with "Couldn't find matching GLX visual".
  Client game tests therefore run locally only on 26.2 so far (see §6).

---

## 2. Build & test commands

All from `java/`, with `JAVA_HOME` pointing at a JDK 25.

```bash
./gradlew build                     # compile vs 26.2, all checks, unit tests, server GameTests, checkLinkage vs 26.3
./gradlew build -Pmc=26.3           # compile + test against 26.3 (CI matrix)
./gradlew build -Pmod_version=1.2.3 # release version (CI contract, docs/ci.md); $MOD_VERSION also honoured
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
| `core.mode` | | core | `CasinoMode` (game rule toggle + `isEnabled` guard) |
| `core.config` | | core | `ConfigManager`, `ConfigHandle`, `CoreConfig(s)` |
| `core.economy` | | core | `Economy` API, `Economies` locator, `AttachmentEconomy` default impl |
| `core.events` | | core | `CasinoEvents` (`PLAY_RESOLVED`, `BALANCE_CHANGED`) — cross-module bus |
| `core.network` | | core | `Payloads` helper, generic `TableActionPayload`/`TableSyncPayload`, `CasinoModeSyncPayload` |
| `core.rng` | | core | `OddsService`, `CasinoRng`, `OddsModifier`, `OddsContext`, `StreakTracker` |
| `core.text` | | core | `Plural` (p1/p21/p2/p5), `Texts` (numbers, plural components) |
| `core.table` | | core | `CasinoTableBlock`, `CasinoTableBlockEntity`, `CasinoTableMenu`, `TableType`, `TableRegistrar` |
| `core.mixin[.client]` | | core | e.g. `CreateWorldGameTabMixin` |
| `client` (client set) | | core | `BurmaldaholicClient`, `ClientModuleList`, `CoreClientModule`, `client.table.CasinoTableScreen`, `ClientTableCache`, `client.config` (Mod Menu) |
| `games.blackjack` | `blackjack` | dev | |
| `games.poker` | `poker` | dev | |
| `games.slots` | `slots` | dev | |
| `games.roulette` | `roulette` | dev | |
| `games.craps` | `craps` | dev | |
| `games.extras` | `extras` | dev | coin flip, wheel, scratch cards, plinko, dice duel |
| `loan` | `loan` | dev | loan shark, debt, collectors |
| `chaos` | `chaos` | dev | chaos events, golden hour, **streak odds modifier** |
| `lastchance` | `lastchance` | dev | |
| `worldgen` | `worldgen` | dev | structures (+ data pack) |
| `vip` | `vip` | dev | |
| `multiplayer` | `multiplayer` | dev | ownership, house cut, shared tables |

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
  interaction and `ctx.payloads().serverbound(...)` handlers are already gated by core.
- Modules never import another feature's package. Talk through `core` APIs:
  `Economies.get()` for chips, `CasinoEvents` for notifications, `OddsService.get().addModifier(...)`
  for odds effects. Need something new in core? Ask J-core (it's a shared package).
- All randomness through `OddsService.get().rng(new OddsContext(player.getUUID(), ID, bet))`:
  `nextInt/shuffle` are fair and never modified; `chance(p)` / `weighted(..., favourable)` pass the
  player-favourable probability through modifiers (order: 100 streak, 200 vip, 300 chaos, 400 lastchance).
- After settling a bet fire `CasinoEvents.PLAY_RESOLVED.invoker().onPlayResolved(player, new PlayResult(ID, bet, payout))`
  (feeds streaks, VIP progress, Last Chance, statistics).

### `ModuleContext` API
| Call | Does |
|---|---|
| `ctx.id("blackjack_table")` | `burmaldaholic:blackjack_table`, **throws** if the name is not owned by the module |
| `ctx.registry().block/blockWithItem/item/blockEntity/menu/sound(...)` | vanilla registration with `setId`, creative-tab auto-add |
| `ctx.tables().register("blackjack_table", BlackjackTableBlockEntity::new)` | block + item + BE type + menu type → `TableType` |
| `ctx.payloads().serverbound(name, codec, handler)` / `clientbound(name, codec)` | custom payloads (C2S handler gated by casino mode) |
| `ctx.config(Section.class, Section::new)` / `ctx.config("streak", ...)` | config section (must be an owned name) |
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
Edit only `src/main/lang/<module>/en_us.json` **and** `ru_ru.json` (same keys, same placeholders;
use keys from `docs/design/STRINGS.md`). `mergeLang` fails on: missing/extra keys between languages,
placeholder mismatch (`%s`, `%1$s`; anything else like `%d` is rejected), duplicate keys across modules,
keys outside the module's namespaces, non-string values, incomplete plural sets.
Key shape: `<category>.burmaldaholic.<owned-name>...`, e.g. `block.burmaldaholic.blackjack_table`,
`gui.burmaldaholic.blackjack.hit`, `config.burmaldaholic.streak.max`.

**Plurals** (shared with Bedrock): define `<base>.p1`, `.p21`, `.p2`, `.p5` in both languages and use
`Texts.plural("burmaldaholic.core.chips", n)` (or `Plural.key(base, n)`). p1: n==1; p21: n%10==1 &&
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
```java
public final class BlackjackConfig { public int decks = 6; public Limits limits = new Limits(); ... }
public static ConfigHandle<BlackjackConfig> CONFIG;           // in register(): CONFIG = ctx.config(BlackjackConfig.class, BlackjackConfig::new);
CONFIG.get().decks                                             // always read through get()
```
One file `config/burmaldaholic.json`, top-level object per section, nested objects follow the dotted
keys in `docs/design/CONFIG.md`; missing keys → defaults, unknown keys logged and dropped, file
rewritten complete. Mod Menu opens `CasinoConfigScreen` (J-core turns it into a generated editor with
`config.burmaldaholic.<key>` labels; clamping, per-world overrides and `/casino config` are J-core TODOs).

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
  **"Casino Mode: ON/OFF"** button (default ON); the same rule is under More → Game Rules →
  Burmaldaholic. In game: `/gamerule burmaldaholic:casino_mode false|true`.
- `./gradlew runClient -PwithModMenu` adds Mod Menu → Mods → Burmaldaholic → config screen.
- `./gradlew runServer` then `runClient` and connect to `localhost` for multiplayer checks
  (dev server has `online-mode` handled by Loom's dev launch; accept the EULA in `run/<mc>/server/eula.txt`).
- Worlds/configs live in `java/run/<mc>/{client,server}` (git-ignored). Config file:
  `run/<mc>/client/config/burmaldaholic.json`.
- Client game tests: `xvfb-run -a ./gradlew runClientGameTest` (26.2 verified here; 26.3's SDL3
  window needs a GLX-capable virtual display — open item for CI/testers).

---

## 8. What exists vs. what J-core adds next

Done in the skeleton (builds; 48 unit tests, server GameTests and the client GameTest pass on 26.2;
build + tests pass with `-Pmc=26.3`; linkage vs 26.3 passes): module system + all stubs, namespace
ownership, casino-mode game rule (default ON per GAME_DESIGN §2.1) + Create World toggle + client sync,
config loader, economy API with attachment storage, odds service + streak tracker, events, plural
helper, generic table block/BE/menu/screen + payloads, Mod Menu hook, all build checks.

J-core TODO (per docs/design): chips/cashier/casino card, HUD, `/casino` commands, config clamping,
per-world overrides and client config sync, generated config screen, persistence of streaks,
earnings (ores/mobs/trading), contracts, wagers.
