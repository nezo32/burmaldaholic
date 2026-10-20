# HX: big-win chat font (B2) + JEI/REI/EMI over table screens (B3)

Branch `p2/hx` (from `feature/v020-table-ui`).

## Done
### B2: big-win chat line rendered as tofu
- `core/fx/BigWinBroadcast.java`: the line is now built by `line(name, net, gameId)` as
  `Component.empty().append(glyph.withFont(GLYPHS)).append(" ").append(translatable)`. The glyph is a sibling,
  so the text keeps the default font.
- Grep for the same pattern (`withFont` on a parent that gets text appended): this was the only bad case.
  - `bots/BotAvatars.plateText` already uses siblings.
  - `client/table/cards/CardSprites.rankText` puts `core/card_index` on the rank text on purpose. That font covers
    `0-9 A J Q K X Т В Д К` (EN+RU ranks). A new language whose rank letters fall outside that set would need glyphs
    added to the font.
- Test `core/fx/GlyphFontScopeTest`:
  - walks the component tree (siblings plus translatable args) with the inherited `Style` and asserts that only PUA
    glyph nodes carry our font;
  - checks that the walker flags the old shape;
  - scans `src/main` and `src/client` for `withFont(..)).append(`. Verified against the old HEAD line (1 hit).

### B3: JEI/REI/EMI drawing over our screens
- `client/table/CasinoTableScreen` is now `Screen implements MenuAccess<CasinoTableMenu>`, no longer
  `AbstractContainerScreen`. The base re-implements what tables used from the container screen:
  - `menu`/`getMenu`;
  - `imageWidth/imageHeight`, `leftPos/topPos` (centred in `init`, same order as before);
  - `titleLabelX/Y`, plus `inventoryLabelY` kept so subclasses that still set it compile;
  - labels drawn translated to image space;
  - `final tick()` → `containerTick()`, or `closeContainer()` when the player is dead or removed;
  - the inventory key and Esc close the screen and send the close packet;
  - `removed()` → `menu.removed`;
  - no pause; `isInGameUi` true;
  - `mouseScrolled` returns false, as the container screen did.
- Dropped (all slot-only and no-ops for a slot-less menu): slot hover/click/drag, quick-craft, carried item, and the
  `-999` THROW packet that was sent on outside clicks.
- Menu sync (`ClientTableCache`, state payloads), close-on-distance (`CasinoTableMenu.stillValid`, server close
  packet) and `MenuScreens.register` do not depend on the screen class. No change was needed in any subclass:
  V2TableScreen, all games, CardTableScreen, CashierScreen, SlotMachineV2Screen, Plinko and Wheel.
- 6 in-world renderers checked `screen instanceof AbstractContainerScreen`. They now check `instanceof MenuAccess<?>`:
  - CardTableRenderer
  - CrapsBoardRenderer
  - Plinko renderer
  - Wheel renderer
  - RouletteBoardRenderer
  - SlotCabinetRenderer

  Without this change they would have stopped hiding or locking while the screen is open. These files belong to
  other packages: a one-line change in each.
- Test `client/table/ScreenContractTest`: loads every class in `src/client/java` without initialising it and asserts
  that no Screen is an `AbstractContainerScreen`. It found at least 10 table screens. It also asserts that the base
  is a `Screen` + `MenuAccess`.
- No compat plugins were needed. The default JEI/REI/EMI overlays attach only to container screens.

## Verified (exact commands)
- `./gradlew --no-daemon compileJava compileClientJava compileGametestJava`: BUILD SUCCESSFUL (26.2).
- `./gradlew --no-daemon compileJava compileClientJava compileGametestJava compileTestJava -Pmc=26.3`: BUILD SUCCESSFUL.
- `./gradlew --no-daemon test --tests 'dev.nezo.burmaldaholic.core.fx.*' --tests 'dev.nezo.burmaldaholic.client.table.*' --tests '*RawInputGuardTest'`
  passed on 26.2: GlyphFontScopeTest 3/3, ScreenContractTest 2/2, and RawInputGuardTest. `bots.*` also passed.
- Client lane (26.3, real SDL3 input), entrypoints temporarily narrowed to RealInputClientGameTests (restored, not committed):
  `BURMALDAHOLIC_CGT=INPUT with-client-lock.sh with-heavy-lock.sh ./gradlew --no-daemon --max-workers=4 runClientGameTest -Pmc=26.3`
  -> BUILD SUCCESSFUL (17m55s); blackjack/UTH/baccarat/roulette/craps all "ok": tables open via the block, real clicks and keys
  bet/unbet/deal, asserted on the server.

## Not verified
- Not tested with JEI/REI/EMI actually installed. The fix relies on their documented default of attaching only to
  `AbstractContainerScreen`.
- No full build or full client suite (TEST ONLY WHAT CHANGED).

## Requests / risks
- Owners of the 6 renderers: keep `MenuAccess<?>` (not `AbstractContainerScreen`) in "is my table's screen open" checks.
- Any future table screen must extend `CasinoTableScreen`. `ScreenContractTest` fails if it extends a container screen.
- Behaviour change: the mouse release after an unhandled click now reaches the screen. The container screen used to
  swallow it via `skipNextRelease`. Widgets now always get their release, which is more correct.
