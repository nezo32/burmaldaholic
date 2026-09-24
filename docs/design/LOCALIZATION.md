# Burmaldaholic — Localization Specification

> **Java-only (2026-09-24).** Bedrock support was dropped: Bedrock sections, lanes and tasks were removed. An inline
> note that still names Bedrock (the former TypeScript twin) is historical context and does not apply.

Languages at launch: **English (`en_us`)** and **Russian (`ru_ru` / `ru_RU`)**.
The master text lives in `STRINGS.md`; language files are generated from / checked against it.

## 1. One key set

- Every player-facing string has exactly one key, used in the `lang/*.json` files.
- Namespace: `burmaldaholic` (identifiers use `burmaldaholic:<id>`; translation keys use the
  dotted form `…burmaldaholic.…`).
- Parity test (Wave 4): the set of keys in `en_us.json` = `ru_ru.json` = the keys in `STRINGS.md`
  (plus `java/tools/lang_java_only.json`).

### 1.1 Key grammar

```
<category>.burmaldaholic.<module>.<name>[.<sub>…][.<plural-suffix>|.<variant-number>]
```
- lowercase ASCII, digits and `_` only in segments; segments separated by `.`.
- `module` is the owning module (`core, blackjack, poker, slots, roulette, craps, baccarat, uth,
  extras, loan, chaos, lastchance, worldgen, vip, multiplayer, streak, contracts, wager`) or a
  shared UI area (`common`, `error`, `card`, `menu`, `cashier`, `charter`, `croupier`, `achievements`). STRINGS.md
  lists which team owns each area.

| Category | Used for | Example |
|----------|----------|---------|
| `item` | item names (no module segment — vanilla convention) | `item.burmaldaholic.chip_1` |
| `block` | block names | `block.burmaldaholic.cashier` |
| `entity` | entity names | `entity.burmaldaholic.loan_shark` |
| `tooltip` | item/block tooltips | `tooltip.burmaldaholic.chip.value` |
| `gui` | screen/form titles, buttons, labels | `gui.burmaldaholic.blackjack.hit` |
| `hud` | HUD lines | `hud.burmaldaholic.balance` |
| `msg` | chat, action bar, titles | `msg.burmaldaholic.loan.taken` |
| `dialog` | NPC lines with random variants | `dialog.burmaldaholic.loan.greeting.3` |
| `advancement` | titles/descriptions | `advancement.burmaldaholic.jackpot.title` |
| `death.attack` | death messages (Java damage types) | `death.attack.burmaldaholic.debt_collection` |
| `config` | config labels/tooltips | `config.burmaldaholic.chaos.ambientChance` |
| `unit` | counted nouns (plural bases) | `unit.burmaldaholic.chip` |
| `key` / `key.category` | Java keybinds | `key.burmaldaholic.open_menu` |
| `itemGroup` | creative tab | `itemGroup.burmaldaholic.main` |
| `sound` / `subtitles` | subtitles | `subtitles.burmaldaholic.chip_place` |
| `modmenu` | Mod Menu name/summary (Java) | `modmenu.nameTranslation.burmaldaholic` |

Item/block/entity keys omit the module (they are global ids); everything else includes it.
Config keys append the **exact config key** (camelCase preserved) after `config.burmaldaholic.`.

### 1.2 Random variants

NPC lines with variants use numbered suffixes `.1` … `.N` with **no gaps**. The code holds the
count per base key as a constant (`VARIANTS = {"dialog.burmaldaholic.loan.greeting": 5, …}`,
taken from STRINGS.md) and picks uniformly. Every language must define all N variants.

## 2. Placeholders

- **Master notation (STRINGS.md, Java files): positional `%1$s`, `%2$s`, …** Always positional
  when a string has ≥ 2 arguments (word order differs in RU). Single-argument strings may use
  `%1$s` too — prefer it everywhere for uniformity.
- All arguments are passed as **strings** (numbers pre-formatted, §4) or as nested translatable
  components. Never use `%d`/`%f`.
- Literal percent: `%%`.
- Nested components: `Text.translatable(key, Text.translatable(pluralKey, n))`.
- Formatting codes `§` are allowed in values only for: `§l`, `§o`, `§r` and colors, and only when
  STRINGS.md shows them. Prefer applying color in code.

## 3. Plurals — number-class suffixes (lead decision)

### 3.1 Why not locale-aware `.one/.few/.many/.other`

Translatable text is resolved on the client, so the server does not know the client's language.
The server therefore cannot pick a
language-specific plural category. Instead the server picks a **number class** that depends only
on `n`, and **every language file defines all four classes**. The four classes are exactly the
union of the English and Russian distinctions, so each language can map them to its own forms.

### 3.2 Contract

```
plural(baseKey: String, n: long) -> String       // returns a translation key

  a   = |n|
  m10 = a % 10
  m100 = a % 100
  if a == 1                                   -> baseKey + ".p1"    // 1
  if m10 == 1 && m100 != 11                   -> baseKey + ".p21"   // 21, 31, 101, 1001 …
  if m10 in 2..4 && !(m100 in 12..14)         -> baseKey + ".p2"    // 2–4, 22–24, 102 …
  else                                        -> baseKey + ".p5"    // 0, 5–20, 25–30, 111–114 …
```
- Java: `static String plural(String baseKey, long n)` in `burmaldaholic.common.i18n.Plural`.
- The number itself is always passed as `%1$s` to the resulting key (formatted per §4).
- Shared unit tests (identical vectors):
  `0→p5, 1→p1, 2→p2, 4→p2, 5→p5, 11→p5, 12→p5, 14→p5, 21→p21, 22→p2, 25→p5, 101→p21, 111→p5,
  112→p5, 121→p21, 1001→p21, 1011→p5, −1→p1, −21→p21`.

### 3.3 What each language puts in the four keys

| Class | Example n | English | Russian (CLDR form) |
|-------|-----------|---------|---------------------|
| `.p1` | 1 | singular: "%1$s chip" | one: «%1$s фишка» |
| `.p21` | 21, 31, 101 | plural: "%1$s chips" | one: «%1$s фишка» |
| `.p2` | 2–4, 22–24 | plural: "%1$s chips" | few: «%1$s фишки» |
| `.p5` | 0, 5–20, 11–14 | plural: "%1$s chips" | many: «%1$s фишек» |

Every plural string in STRINGS.md lists all four keys for both languages. A future language adds
its mapping the same way (Ukrainian = Russian mapping; Polish: p1 one, p21 many, p2 few, p5 many;
German: p1 singular, rest plural). A language whose rules cannot be expressed with these four
classes (e.g. French singular for 0) must phrase count strings to avoid the ambiguous case
("Jetons : %1$s"); if that is impossible, extend the classes for everyone — never add locale
logic on the server.

### 3.4 Rules for writers

- Any string whose grammar depends on a number **must** be a plural base (4 keys). If the
  number is only a label value ("Balance: 12 500"), use a colon form instead and no plural.
- Prefer small unit bases reused as nested components: `unit.burmaldaholic.chip`,
  `chip_acc`, `heart`, `level`, `day`, `minute`, `minute_acc`, `second`, `second_acc`,
  `emerald`, `gold_ingot`, `player`, `line`, `spin`, `hand`, `block` (all in STRINGS.md §core).
  Example: `gui.burmaldaholic.common.result.win` = "Win! +%1$s" with `%1$s` =
  translate(plural("unit.burmaldaholic.chip", 150), "150") → "Win! +150 chips" /
  «Выигрыш! +150 фишек».
- Russian case matters: the accusative of «фишка» is «фишку» («Вы выиграли 1 фишку»,
  «21 фишку»), which differs from the nominative. Therefore units get case-specific bases: `unit.burmaldaholic.chip` (nominative:
  «1 фишка»), `unit.burmaldaholic.chip_acc` (accusative: «1 фишку / 2 фишки / 5 фишек»).
  English files define `chip_acc` identical to `chip`. Use `_acc` after verbs "win/lose/pay/
  bet/get/take/borrow" (выиграть, проиграть, заплатить, поставить, получить, взять, одолжить).
  Genitive after "of/from/out of" is avoided by rephrasing.

## 4. Numbers, time, names

- Numbers: integer, grouped with a regular space from 10 000 (`12 500`, `1 000 000`); below
  10 000 no grouping (`2500`). Implemented once: `formatChips(long) -> String`.
- Multipliers: `×` (U+00D7) **before** the number, `×10`, in both languages.
- Percent: `5%` EN, `5 %` RU (narrow spacing is fine as a regular space) → percent strings are
  keys with `%1$s%%` (EN) and `%1$s %%` (RU).
- Durations: `Xd HH:MM` on the HUD (not translated letters: use keys `hud.burmaldaholic.time.dhm`
  = "%1$sd %2$s" / «%1$sд %2$s»).
- Player names, bot names, table owner names: never translated.

## 5. Mod display name in Russian — decision

**Decision: «Бурмалдоголик»** in all Russian player-facing text (advancement tab, creative tab,
Mod Menu name translation, splash/toasts). **"Burmaldaholic"** stays the
technical/brand id everywhere else (mod id, CurseForge listing, logs, file names, keys).

Justification:
1. The name is built on the Russian internet slang **«бурмалда»** (casino gambling, from the
   streamer meme «бурмалдёж»). Written in Latin letters, Russian players read it as a foreign
   word and miss the joke; in Cyrillic it reads instantly.
2. The suffix follows the productive Russian pattern **-о-голик** (трудоголик, шопоголик),
   so «Бурмалдоголик» sounds like a real word ("a casino-holic"). A literal transliteration
   «Бурмалдахолик» sounds machine-made — Russians never say «-холик».
3. Minecraft itself localizes names that are wordplay (e.g. item and advancement names), so
   a localized title is consistent with the platform.
4. Keeping the Latin id for the listing avoids splitting search results and keeps one brand.

## 6. Glossary EN → RU

Rule: use these terms consistently in every string. Casino jargon is used in its **Russian
casino/poker-room form** (what players hear in Russian poker rooms and streams), not calques.

### 6.1 Core / economy

| EN | RU | Notes |
|----|----|-------|
| Chip / chips | фишка / фишки | «фишек» (gen. pl.) |
| Balance | баланс | «Баланс: 12 500» |
| Bet / stake (noun) | ставка | |
| to bet | ставить / поставить | |
| Wager (lifetime) | оборот ставок | "wagered" → «поставлено» |
| Win / payout | выигрыш / выплата | |
| House (the casino) | заведение | «Заведение всегда в плюсе» |
| Bank (world economy) | банк | only for the world economy; NOT for the poker pot |
| House edge | преимущество заведения | |
| RTP | отдача (RTP) | |
| Cashier | касса | block name «Касса» |
| Deposit / Withdraw | внести / снять | |
| Casino Card | Клубная карта | |
| Contract | заказ | "daily contracts" → «заказы дня»; not «контракт» (sounds legal) |
| Reroll | сменить | |
| Pawn stake | залог | "Stake an item" → «Поставить в залог» |
| Max bet | максимальная ставка / макс. ставка | |
| Payout table / paytable | таблица выплат | |
| Push (tie) | ничья / «при своих» | «Ничья — ставка возвращена» |
| Dealer | дилер / крупье | дилер for cards, крупье for roulette/craps NPC |
| Croupier | крупье | |
| Round / hand (deal) | раунд / раздача | |
| Seat | место | |
| Table | стол | |
| High Roller | хайроллер | «Стол хайроллеров» |
| Jackpot | джекпот | |
| Progressive jackpot | накопительный джекпот | |
| Golden Hour | «Золотой час» | always in «» in running text |
| Chaos event | хаос-событие | |
| Lucky streak / Unlucky streak | серия удачи / серия неудач | HUD short: «Везёт ×4» / «Не везёт ×3» |
| Last Chance | «Последний шанс» | |
| VIP tier | ВИП-статус | tiers: Бронза, Серебро, Золото, Платина, Алмаз, Незерит |
| Cashback | кешбэк | |
| Loan | займ | colloquial «займ» (not «заём» – too bookish for the shark's voice; UI uses «займ» consistently) |
| Loan Shark | Ростовщик | |
| Interest | проценты | |
| Deadline | срок | |
| Default (on a loan) | просрочка | "in default" → «просрочка» |
| Debt | долг | |
| Debt Collectors | коллекторы | squad members: Коллектор, Выбивала (Repo Man), Бухгалтер (Accountant), Громила (Enforcer) |
| Repossession | изъятие | |
| Asset Freeze | арест счёта | |
| Garnishment | удержание | |
| Casino Charter | Лицензия казино | block |
| Bankroll (owner) | касса заведения | not «банкролл» in UI; poker players' «банкролл» is fine in flavor text |
| License fee | лицензионный сбор | |
| Owner | владелец | |
| Soul Wager | Ставка на душу | |
| Heart wager | ставка сердцами | |

### 6.2 Blackjack

| EN | RU |
|----|----|
| Blackjack | блэкджек |
| Hit | Ещё |
| Stand | Хватит |
| Double down | Удвоить (дабл) |
| Split | Разделить (сплит) |
| Insurance | Страховка |
| Even money | Один к одному (even money) → «Выплата 1:1» |
| Bust | Перебор |
| Soft hand | мягкая рука |
| Natural | натуральный блэкджек |
| Shoe | шуз |
| Dealer stands on soft 17 | Дилер стоит на мягких 17 |

Buttons use the plain verbs (Ещё/Хватит/Удвоить/Разделить); jargon (дабл/сплит) appears in
tooltips and flavor.

### 6.3 Poker

| EN | RU |
|----|----|
| Texas Hold'em | Техасский холдем |
| Pot | банк |
| Side pot | побочный банк |
| Blinds / small / big blind | блайнды / малый блайнд / большой блайнд |
| Button (dealer) | баттон |
| Fold | фолд — button «Пас» |
| Check | Чек |
| Call | Колл |
| Bet | Бет / Ставка |
| Raise | Рейз |
| All-in | Олл-ин |
| Showdown | вскрытие |
| Flop / Turn / River | флоп / тёрн / ривер |
| Hole cards | карманные карты |
| Community cards | общие карты |
| Buy-in | бай-ин |
| Rake | рейк |
| Sit out | пропустить раздачу |
| Hand ranks | Старшая карта, Пара, Две пары, Сет (Тройка), Стрит, Флеш, Фулл-хаус, Каре, Стрит-флеш, Роял-флеш |
| Fish / Regular / Shark (bots) | Рыба / Регуляр / Акула |

Button choice: «Пас» for Fold (what Russian home-game players say) with tooltip «Фолд — сбросить
карты»; Check «Чек», Call «Колл», Raise «Рейз», All-in «Олл-ин».

### 6.4 Roulette

| EN | RU |
|----|----|
| Roulette (European) | рулетка (европейская) |
| Zero | зеро |
| Straight | ставка на номер (стрейт) |
| Split | сплит (на два номера) |
| Street | улица |
| Trio | трио |
| Corner | угол |
| First Four | первые четыре |
| Six Line | линия (шесть номеров) |
| Dozen | дюжина |
| Column | колонна |
| Red / Black | красное / чёрное |
| Odd / Even | нечёт / чёт |
| Low / High | малые (1–18) / большие (19–36) |
| La partage | ла партаж |
| No more bets | Ставок больше нет |
| Place your bets | Делайте ваши ставки |

### 6.5 Craps

| EN | RU |
|----|----|
| Craps | крэпс |
| Shooter | шутер (бросающий) |
| Come-out roll | первый бросок (кам-аут) |
| Point | поинт |
| Pass Line | пасс-лайн |
| Don't Pass | не-пасс |
| Come / Don't Come | кам / не-кам |
| Field | филд |
| Odds | коэффициент (оддс) |
| Seven-out | семёрка — конец серии |
| Puck ON/OFF | фишка-маркер ВКЛ/ВЫКЛ → «Поинт: 6» / «Поинт не установлен» |

### 6.6 Extras, slots, chaos

| EN | RU |
|----|----|
| Slot machine | игровой автомат / «однорукий бандит» |
| Spin | Крутить |
| Payline | линия выплат |
| Wild | вайлд (дикий символ) |
| Coin Flip / Heads / Tails | Монетка / Орёл / Решка |
| Wheel of Fortune | Колесо фортуны |
| Scratch card | скретч-карта (лотерейный билет) → item «Моментальная лотерея» |
| Plinko | Плинко |
| Dice Duel | Дуэль на костях |
| Diamond rain | Алмазный дождь |
| Chip shower | Фишкопад |
| Mob wave | Нашествие мобов |
| Random teleport | Случайная телепортация |
| Curse / Buff | проклятие / благословение |

### 6.7 Baccarat (⚠ added 2026-09)

| EN | RU | Notes |
|----|----|-------|
| Baccarat | баккара | indeclinable, feminine in speech («сыграть в баккара») |
| Punto Banco | пунто банко | subtitle only |
| Player (hand / bet box) | Игрок | **capitalized** when it means the hand or the box: «ставка на Игрока», «Игрок берёт третью карту». People at the table are never called «игрок» in baccarat strings — use names, «за столом», «понтёры» |
| Banker (hand / bet box) | Банкир | capitalized likewise; in chemin de fer the person holding the bank is «банкир» (lowercase) / «банк держит …» |
| Tie | ничья | «Ничья: по 7», «ставка на ничью» |
| Pair / Player Pair / Banker Pair | пара / пара игрока / пара банкира | box labels: «Пара игрока», «Пара банкира» |
| Commission | комиссия | «Банкир (1:1 −5 %)» |
| Natural | натуральная (девятка / восьмёрка) | «Натуральная девятка!» |
| Third card | третья карта | «берёт третью карту» / «останавливается на 6» |
| Coup | раздача | «Раздача №12» |
| Shoe | шуз | as blackjack |
| Burn (cards) | сжечь (карты) | «Сожжено 7 карт» |
| Bead plate | табло результатов | letters И / Б / Н |
| Chemin de fer | шмен-де-фер | table name «Стол для шмен-де-фер» |
| Bank / hold the bank / pass the bank | банк / держать банк / передать банк | not «банкролл» |
| Punter | понтёр | classic Russian card-room word |
| Banco! | Банко! | the call; quoted in running text: «Банко» |

### 6.8 Ultimate Texas Hold'em (⚠ added 2026-09)

| EN | RU | Notes |
|----|----|-------|
| Ultimate Texas Hold'em | «Ультимейт Техас Холдем» | full name; in running text in «» and not declined («в «Ультимейт Техас Холдем»»); short form for titles and tight labels: «Ультимейт холдем» |
| Ante | анте | indeclinable, neuter: «анте возвращается» |
| Blind | блайнд | as poker |
| Trips (side bet) | трипс | «ставка трипс», «выплаты по трипсу» |
| Play bet | плей / плей-ставка | box label «Плей»; «плей-ставка в 4 анте» |
| Bet ×4 / ×3 / ×2 / ×1 | Ставка ×4 / ×3 / ×2 / ×1 | multiplier before the number (§4) |
| Check / Fold | Чек / Пас | as poker |
| Dealer qualifies / does not qualify | дилер квалифицируется / не квалифицируется | colloquial «у дилера нет игры» allowed only in flavor text |
| Push (Ante returned) | анте возвращается | not «пуш» |
| Dealer seat | место дилера | player-banked table |
| Paytable | таблица выплат / «Выплаты по блайнду» | |
| Straight or better | стрит и выше | |

## 7. Tone guide (Russian)

**Voice:** a slightly cheeky Russian casino host — lively, slangy, self-ironic about gambling,
never preachy, never crude. Think of a croupier who's seen everything and a loan shark who's
polite until he isn't.

Do:
- Write natural spoken Russian: «Ставки сделаны», «Ещё одну?», «Заведение угощает».
- Use casino and internet slang where a native would: «занос» (big win), «додеп» (re-deposit —
  in flavor only), «на мели», «в минус», «отбиться», «фарт», «подкрутка» (joking "rigged").
  Sparingly: max one slang word per line.
- Use «вы» in UI (buttons, errors, rules) — polite, clear. NPCs may use «ты»: the Loan Shark
  switches from «вы» to «ты» once you default (it is a deliberate menace beat); Debt Collectors
  always «ты»; Croupier «вы».
- Use «ё» consistently (всё, ещё, чёрное, чёт).
- Use « » quotes for names of events/modes («Золотой час»), em dash — with spaces.
- Keep humor in flavor text (dialogue, events, advancements, tooltips). Keep buttons and errors
  plain and short.
- Adapt jokes, don't translate them. If an English pun doesn't work, write a new Russian joke of
  the same spirit (see STRINGS.md for examples: "Clean Slate" → «Чист перед законом»).

Don't:
- No machine-translation calques («Сделать ставку на ставку», «Ударить» for Hit).
- No English word order ("Вы выиграли 150 фишек!" is fine; "150 фишек выиграно вами" is not).
- No crude profanity, no real-world gambling brands, no real-casino advertising tone, no
  encouragement to gamble real money. A small disclaimer lives in the Rules page.
- Never make a player name the subject of a past-tense verb — Russian past tense is gendered
  («%1$s выиграл/выиграла»). Use noun phrases («Крупный выигрыш: %1$s»), present tense
  («%1$s забирает банк») or a masculine noun as the grammatical subject («Игрок %1$s …»).
  Exception: `death.attack.*` follows vanilla Russian death messages (masculine).
- Don't capitalize every word in titles (RU titles are sentence case: «Серия удачи», not «Серия Удачи»).

**English tone:** same cheeky host, American casino idiom ("Winner winner", "The house thanks
you", "Hit me"). Title Case for item/block names and advancement titles; sentence case elsewhere.
