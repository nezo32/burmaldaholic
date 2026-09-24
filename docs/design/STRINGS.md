# Burmaldaholic — Master String List (EN / RU)

Source of truth for every player-facing string. The language files
(`assets/burmaldaholic/lang/en_us.json`, `ru_ru.json`, merged from `java/src/main/lang/<module>/` fragments that
`java/tools/gen_lang.py` generates from this file) must contain exactly these keys with exactly these values
(LOCALIZATION.md §2). Each module's team owns its section.

Format rules for this file (a generator script parses it):
- Every string row is `| \`key\` | English | Russian |`. Only rows whose first cell is a
  back-quoted key are strings; everything else is commentary.
- No `|` characters and no line breaks inside values.
- Placeholders: positional `%1$s`, `%2$s`… Literal percent `%%`.
- `(plural)` groups: four rows `.p1`, `.p21`, `.p2`, `.p5` (LOCALIZATION.md §3).
- `(variants N)`: rows `.1` … `.N`; the code picks one at random.
- Arguments noted as `%1$s=chips` mean "a nested plural component built from
  `unit.burmaldaholic.chip`" (nominative); `chips_acc` means `unit.burmaldaholic.chip_acc`
  (accusative, after verbs). `num` = a plain formatted number. `name` = player/entity name.
  `game` = nested `gui.burmaldaholic.common.game.*` component.

Russian-specific writing rule: never put a player name as the subject of a past-tense verb
(Russian past tense is gendered). Use noun phrases or present tense: «Крупный выигрыш: %1$s»,
not «%1$s выиграл». Exception: `death.attack.*`, which follows the vanilla Russian convention.

---

## core

### Mod, creative tab, keybinds, casino mode switch

Casino mode lives in world saved data (no game rule); the create-world toggle uses `gui.burmaldaholic.core.create_world.casino_mode`.

| Key | EN | RU |
|-----|----|----|
| `modmenu.nameTranslation.burmaldaholic` | Burmaldaholic | Бурмалдоголик |
| `modmenu.summaryTranslation.burmaldaholic` | Turns survival into a casino. The house always wins. Almost. | Выживание превращается в казино. Заведение всегда в плюсе. Почти. |
| `itemGroup.burmaldaholic.main` | Burmaldaholic | Бурмалдоголик |
| `key.category.burmaldaholic` | Burmaldaholic | Бурмалдоголик |
| `key.burmaldaholic.open_menu` | Open Casino Menu | Открыть меню казино |
| `gui.burmaldaholic.core.create_world.casino_mode` | Casino Mode: %1$s | Режим казино: %1$s |
| `gui.burmaldaholic.core.create_world.casino_mode.tooltip` | Adds the Burmaldaholic casino to this world: chips, tables, loans, chaos events. Off by default and saved with the world; operators can switch it later with /casino mode on or /casino mode off. Does not change difficulty. | Добавляет в мир казино Бурмалдоголик: фишки, столы, займы, хаос-события. По умолчанию выключен и сохраняется вместе с миром; позже оператор может переключить его командой /casino mode on или /casino mode off. Сложность не меняется. |
| `gui.burmaldaholic.core.setup.title` | Casino setup | Настройка казино |
| `gui.burmaldaholic.core.setup.intro` | The add-on is active. Choose how this world plays. You can change it later in Casino Card → Admin. | Аддон подключён. Выберите правила для этого мира — потом их можно поменять в «Клубная карта → Админ». |
| `gui.burmaldaholic.core.setup.casino_mode` | Casino mode | Режим казино |
| `gui.burmaldaholic.core.setup.chaos` | Chaos events | Хаос-события |
| `gui.burmaldaholic.core.setup.hardcore_last_chance` | Last Chance in Hardcore | «Последний шанс» в хардкоре |
| `gui.burmaldaholic.core.setup.submit` | Open the casino | Открыть казино |

### Items

| Key | EN | RU |
|-----|----|----|
| `item.burmaldaholic.chip_1` | White Chip | Белая фишка |
| `item.burmaldaholic.chip_5` | Red Chip | Красная фишка |
| `item.burmaldaholic.chip_25` | Green Chip | Зелёная фишка |
| `item.burmaldaholic.chip_100` | Black Chip | Чёрная фишка |
| `item.burmaldaholic.chip_500` | Purple Chip | Фиолетовая фишка |
| `item.burmaldaholic.casino_card` | Casino Card | Клубная карта |
| `item.burmaldaholic.lucky_coin` | Lucky Coin | Счастливая монетка |
| `item.burmaldaholic.dice` | Dice | Игральные кости |
| `item.burmaldaholic.scratch_card` | Scratch Card | Моментальная лотерея |
| `item.burmaldaholic.scratch_card_gold` | Golden Scratch Card | Золотая лотерея |
| `item.burmaldaholic.scratch_card_used` | Scratched Card | Стёртый билет |
| `item.burmaldaholic.overdue_notice` | Overdue Notice | Уведомление о просрочке |
| `item.burmaldaholic.ledger_page` | Ledger Page | Страница из гроссбуха |
| `item.burmaldaholic.golden_chip` | Golden Chip | Золотая фишка |
| `item.burmaldaholic.loan_shark_spawn_egg` | Loan Shark Spawn Egg | Яйцо призыва ростовщика |
| `item.burmaldaholic.piglin_moneylender_spawn_egg` | Piglin Moneylender Spawn Egg | Яйцо призыва пиглина-ростовщика |
| `item.burmaldaholic.croupier_spawn_egg` | Croupier Spawn Egg | Яйцо призыва крупье |
| `item.burmaldaholic.piglin_dealer_spawn_egg` | Piglin Dealer Spawn Egg | Яйцо призыва пиглина-дилера |
| `item.burmaldaholic.shulker_croupier_spawn_egg` | Shulker Croupier Spawn Egg | Яйцо призыва шалкера-крупье |
| `item.burmaldaholic.debt_collector_spawn_egg` | Debt Collector Spawn Egg | Яйцо призыва коллектора |
| `item.burmaldaholic.repo_man_spawn_egg` | Repo Man Spawn Egg | Яйцо призыва выбивалы |
| `item.burmaldaholic.accountant_spawn_egg` | Accountant Spawn Egg | Яйцо призыва бухгалтера |
| `item.burmaldaholic.enforcer_spawn_egg` | Enforcer Spawn Egg | Яйцо призыва громилы |

### Blocks

| Key | EN | RU |
|-----|----|----|
| `block.burmaldaholic.cashier` | Cashier | Касса |
| `block.burmaldaholic.nether_cashier` | Nether Cashier | Незерская касса |
| `block.burmaldaholic.blackjack_table` | Blackjack Table | Стол для блэкджека |
| `block.burmaldaholic.blackjack_table_high_roller` | High Roller Blackjack Table | Стол хайроллеров: блэкджек |
| `block.burmaldaholic.poker_table` | Poker Table | Покерный стол |
| `block.burmaldaholic.roulette_table` | Roulette Table | Стол для рулетки |
| `block.burmaldaholic.roulette_table_high_roller` | High Roller Roulette Table | Стол хайроллеров: рулетка |
| `block.burmaldaholic.craps_table` | Craps Table | Стол для крэпса |
| `block.burmaldaholic.slot_machine_copper` | Overworld Riches | Богатства Верхнего мира |
| `block.burmaldaholic.slot_machine_gold` | Nether Inferno | Пекло Незера |
| `block.burmaldaholic.slot_machine_netherite` | End Void | Пустота Края |
| `block.burmaldaholic.wheel_of_fortune` | Wheel of Fortune | Колесо фортуны |
| `block.burmaldaholic.plinko_machine` | Plinko Machine | Автомат «Плинко» |
| `block.burmaldaholic.casino_charter` | Casino Charter | Лицензия казино |

### Entities

| Key | EN | RU |
|-----|----|----|
| `entity.burmaldaholic.loan_shark` | Loan Shark | Ростовщик |
| `entity.burmaldaholic.piglin_moneylender` | Piglin Moneylender | Пиглин-ростовщик |
| `entity.burmaldaholic.croupier` | Croupier | Крупье |
| `entity.burmaldaholic.piglin_dealer` | Piglin Dealer | Пиглин-дилер |
| `entity.burmaldaholic.shulker_croupier` | Shulker Croupier | Шалкер-крупье |
| `entity.burmaldaholic.debt_collector` | Debt Collector | Коллектор |
| `entity.burmaldaholic.repo_man` | Repo Man | Выбивала |
| `entity.burmaldaholic.accountant` | Accountant | Бухгалтер |
| `entity.burmaldaholic.enforcer` | Enforcer | Громила |

### Tooltips

| Key | EN | RU |
|-----|----|----|
| `tooltip.burmaldaholic.chip.value` | Value: %1$s | Номинал: %1$s |
| `tooltip.burmaldaholic.chip.hint` | Deposit at a Cashier | Сдайте в кассу, чтобы пополнить баланс |
| `tooltip.burmaldaholic.casino_card` | Use to open the Casino Menu | Используйте, чтобы открыть меню казино |
| `tooltip.burmaldaholic.casino_card.owner` | Member: %1$s | Владелец: %1$s |
| `tooltip.burmaldaholic.lucky_coin` | Use to flip a coin against the house | Используйте, чтобы сыграть с заведением в монетку |
| `tooltip.burmaldaholic.dice` | Use on air: duel the house. Use on a player: challenge them | В воздух — дуэль с заведением. На игрока — вызов на дуэль |
| `tooltip.burmaldaholic.scratch_card` | Use to scratch. Top prize: %1$s | Используйте, чтобы стереть. Главный приз: %1$s |
| `tooltip.burmaldaholic.scratch_card.progress` | Scratched: %1$s of 9 | Стёрто: %1$s из 9 |
| `tooltip.burmaldaholic.scratch_card_used` | Better luck next time | Повезёт в следующий раз |
| `tooltip.burmaldaholic.overdue_notice` | FINAL WARNING. Stamped three times. | ПОСЛЕДНЕЕ ПРЕДУПРЕЖДЕНИЕ. Проштамповано трижды. |
| `tooltip.burmaldaholic.ledger_page` | Someone owes a lot of chips. Hopefully not you. | Кто-то должен кучу фишек. Надеемся, не вы. |
| `tooltip.burmaldaholic.golden_chip` | Worth nothing. Priceless. | Не стоит ничего. Бесценна. |
| `tooltip.burmaldaholic.cashier` | Deposit, withdraw and exchange chips | Внести, снять и обменять фишки |
| `tooltip.burmaldaholic.nether_cashier` | Also exchanges gold ingots | Меняет ещё и золотые слитки |
| `tooltip.burmaldaholic.blackjack_table` | Up to 5 players vs the dealer | До 5 игроков против дилера |
| `tooltip.burmaldaholic.high_roller` | Minimum bet %1$s · Gold VIP and above | Минимальная ставка %1$s · ВИП-статус «Золото» и выше |
| `tooltip.burmaldaholic.poker_table` | Texas Hold'em, up to 6 seats | Техасский холдем, до 6 мест |
| `tooltip.burmaldaholic.roulette_table` | European roulette, single zero | Европейская рулетка с одним зеро |
| `tooltip.burmaldaholic.craps_table` | Pass, Come, Field and odds | Пасс-лайн, кам, филд и коэффициенты |
| `tooltip.burmaldaholic.slot_machine_copper` | 243 ways · free spins · Treasure Hunt | 243 способа · фриспины · охота за сокровищами |
| `tooltip.burmaldaholic.slot_machine_gold` | 243 ways · tumbling reels · Piglin's Hoard | 243 способа · обвалы · клад пиглинов |
| `tooltip.burmaldaholic.slot_machine_netherite` | 243 ways · sticky wilds · Dragon Wheel · Gold VIP | 243 способа · липкие вайлды · колесо дракона · ВИП «Золото» |
| `tooltip.burmaldaholic.wheel_of_fortune` | Up to ×10 your bet | До ×10 от ставки |
| `tooltip.burmaldaholic.plinko_machine` | Drop the ball. Up to ×170 | Бросьте шарик. До ×170 |
| `tooltip.burmaldaholic.casino_charter` | Claims a casino. License fee: %1$s | Открывает своё казино. Лицензионный сбор: %1$s |
| `tooltip.burmaldaholic.linked` | Owned by %1$s | Заведение игрока %1$s |

### Units (plural)

| Key | EN | RU |
|-----|----|----|
| `unit.burmaldaholic.chip.p1` | %1$s chip | %1$s фишка |
| `unit.burmaldaholic.chip.p21` | %1$s chips | %1$s фишка |
| `unit.burmaldaholic.chip.p2` | %1$s chips | %1$s фишки |
| `unit.burmaldaholic.chip.p5` | %1$s chips | %1$s фишек |
| `unit.burmaldaholic.chip_acc.p1` | %1$s chip | %1$s фишку |
| `unit.burmaldaholic.chip_acc.p21` | %1$s chips | %1$s фишку |
| `unit.burmaldaholic.chip_acc.p2` | %1$s chips | %1$s фишки |
| `unit.burmaldaholic.chip_acc.p5` | %1$s chips | %1$s фишек |
| `unit.burmaldaholic.heart.p1` | %1$s heart | %1$s сердце |
| `unit.burmaldaholic.heart.p21` | %1$s hearts | %1$s сердце |
| `unit.burmaldaholic.heart.p2` | %1$s hearts | %1$s сердца |
| `unit.burmaldaholic.heart.p5` | %1$s hearts | %1$s сердец |
| `unit.burmaldaholic.level.p1` | %1$s level | %1$s уровень |
| `unit.burmaldaholic.level.p21` | %1$s levels | %1$s уровень |
| `unit.burmaldaholic.level.p2` | %1$s levels | %1$s уровня |
| `unit.burmaldaholic.level.p5` | %1$s levels | %1$s уровней |
| `unit.burmaldaholic.day.p1` | %1$s day | %1$s день |
| `unit.burmaldaholic.day.p21` | %1$s days | %1$s день |
| `unit.burmaldaholic.day.p2` | %1$s days | %1$s дня |
| `unit.burmaldaholic.day.p5` | %1$s days | %1$s дней |
| `unit.burmaldaholic.minute.p1` | %1$s minute | %1$s минута |
| `unit.burmaldaholic.minute.p21` | %1$s minutes | %1$s минута |
| `unit.burmaldaholic.minute.p2` | %1$s minutes | %1$s минуты |
| `unit.burmaldaholic.minute.p5` | %1$s minutes | %1$s минут |
| `unit.burmaldaholic.minute_acc.p1` | %1$s minute | %1$s минуту |
| `unit.burmaldaholic.minute_acc.p21` | %1$s minutes | %1$s минуту |
| `unit.burmaldaholic.minute_acc.p2` | %1$s minutes | %1$s минуты |
| `unit.burmaldaholic.minute_acc.p5` | %1$s minutes | %1$s минут |
| `unit.burmaldaholic.second.p1` | %1$s second | %1$s секунда |
| `unit.burmaldaholic.second.p21` | %1$s seconds | %1$s секунда |
| `unit.burmaldaholic.second.p2` | %1$s seconds | %1$s секунды |
| `unit.burmaldaholic.second.p5` | %1$s seconds | %1$s секунд |
| `unit.burmaldaholic.second_acc.p1` | %1$s second | %1$s секунду |
| `unit.burmaldaholic.second_acc.p21` | %1$s seconds | %1$s секунду |
| `unit.burmaldaholic.second_acc.p2` | %1$s seconds | %1$s секунды |
| `unit.burmaldaholic.second_acc.p5` | %1$s seconds | %1$s секунд |
| `unit.burmaldaholic.emerald.p1` | %1$s emerald | %1$s изумруд |
| `unit.burmaldaholic.emerald.p21` | %1$s emeralds | %1$s изумруд |
| `unit.burmaldaholic.emerald.p2` | %1$s emeralds | %1$s изумруда |
| `unit.burmaldaholic.emerald.p5` | %1$s emeralds | %1$s изумрудов |
| `unit.burmaldaholic.gold_ingot.p1` | %1$s gold ingot | %1$s золотой слиток |
| `unit.burmaldaholic.gold_ingot.p21` | %1$s gold ingots | %1$s золотой слиток |
| `unit.burmaldaholic.gold_ingot.p2` | %1$s gold ingots | %1$s золотых слитка |
| `unit.burmaldaholic.gold_ingot.p5` | %1$s gold ingots | %1$s золотых слитков |
| `unit.burmaldaholic.player.p1` | %1$s player | %1$s игрок |
| `unit.burmaldaholic.player.p21` | %1$s players | %1$s игрок |
| `unit.burmaldaholic.player.p2` | %1$s players | %1$s игрока |
| `unit.burmaldaholic.player.p5` | %1$s players | %1$s игроков |
| `unit.burmaldaholic.line.p1` | %1$s line | %1$s линия |
| `unit.burmaldaholic.line.p21` | %1$s lines | %1$s линия |
| `unit.burmaldaholic.line.p2` | %1$s lines | %1$s линии |
| `unit.burmaldaholic.line.p5` | %1$s lines | %1$s линий |
| `unit.burmaldaholic.spin.p1` | %1$s spin | %1$s вращение |
| `unit.burmaldaholic.spin.p21` | %1$s spins | %1$s вращение |
| `unit.burmaldaholic.spin.p2` | %1$s spins | %1$s вращения |
| `unit.burmaldaholic.spin.p5` | %1$s spins | %1$s вращений |
| `unit.burmaldaholic.block.p1` | %1$s block | %1$s блок |
| `unit.burmaldaholic.block.p21` | %1$s blocks | %1$s блок |
| `unit.burmaldaholic.block.p2` | %1$s blocks | %1$s блока |
| `unit.burmaldaholic.block.p5` | %1$s blocks | %1$s блоков |
| `unit.burmaldaholic.hand.p1` | %1$s hand | %1$s раздача |
| `unit.burmaldaholic.hand.p21` | %1$s hands | %1$s раздача |
| `unit.burmaldaholic.hand.p2` | %1$s hands | %1$s раздачи |
| `unit.burmaldaholic.hand.p5` | %1$s hands | %1$s раздач |
| `unit.burmaldaholic.point.p1` | %1$s point | %1$s очко |
| `unit.burmaldaholic.point.p21` | %1$s points | %1$s очко |
| `unit.burmaldaholic.point.p2` | %1$s points | %1$s очка |
| `unit.burmaldaholic.point.p5` | %1$s points | %1$s очков |
| `unit.burmaldaholic.win.p1` | %1$s win | %1$s победа |
| `unit.burmaldaholic.win.p21` | %1$s wins | %1$s победа |
| `unit.burmaldaholic.win.p2` | %1$s wins | %1$s победы |
| `unit.burmaldaholic.win.p5` | %1$s wins | %1$s побед |
| `unit.burmaldaholic.match.p1` | %1$s match | %1$s матч |
| `unit.burmaldaholic.match.p21` | %1$s matches | %1$s матч |
| `unit.burmaldaholic.match.p2` | %1$s matches | %1$s матча |
| `unit.burmaldaholic.match.p5` | %1$s matches | %1$s матчей |
| `unit.burmaldaholic.ball.p1` | %1$s ball | %1$s шарик |
| `unit.burmaldaholic.ball.p21` | %1$s balls | %1$s шарик |
| `unit.burmaldaholic.ball.p2` | %1$s balls | %1$s шарика |
| `unit.burmaldaholic.ball.p5` | %1$s balls | %1$s шариков |
| `unit.burmaldaholic.bot.p1` | %1$s bot | %1$s бот |
| `unit.burmaldaholic.bot.p21` | %1$s bots | %1$s бот |
| `unit.burmaldaholic.bot.p2` | %1$s bots | %1$s бота |
| `unit.burmaldaholic.bot.p5` | %1$s bots | %1$s ботов |

### Common UI (shared by all modules)

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.common.on` | ON | ВКЛ |
| `gui.burmaldaholic.common.off` | OFF | ВЫКЛ |
| `gui.burmaldaholic.common.yes` | Yes | Да |
| `gui.burmaldaholic.common.no` | No | Нет |
| `gui.burmaldaholic.common.ok` | OK | ОК |
| `gui.burmaldaholic.common.cancel` | Cancel | Отмена |
| `gui.burmaldaholic.common.confirm` | Confirm | Подтвердить |
| `gui.burmaldaholic.common.back` | Back | Назад |
| `gui.burmaldaholic.common.close` | Close | Закрыть |
| `gui.burmaldaholic.common.next` | Next | Далее |
| `gui.burmaldaholic.common.leave` | Leave table | Покинуть стол |
| `gui.burmaldaholic.common.leave_seat` | Press Sneak to leave the seat | Нажмите «Красться», чтобы встать |
| `gui.burmaldaholic.common.play_again` | Play again | Ещё раз |
| `gui.burmaldaholic.common.change_bet` | Change bet | Сменить ставку |
| `gui.burmaldaholic.common.bet` | Bet | Ставка |
| `gui.burmaldaholic.common.bet_amount` | Bet: %1$s | Ставка: %1$s |
| `gui.burmaldaholic.common.exact_amount` | Or type an exact amount | Или введите точную сумму |
| `gui.burmaldaholic.common.amount` | Amount | Сумма |
| `gui.burmaldaholic.common.clear` | Clear | Сбросить |
| `gui.burmaldaholic.common.rebet` | Rebet | Повторить |
| `gui.burmaldaholic.common.max` | Max | Макс. |
| `gui.burmaldaholic.common.min` | Min | Мин. |
| `gui.burmaldaholic.common.deal` | Deal | Раздать |
| `gui.burmaldaholic.common.spin` | Spin | Крутить |
| `gui.burmaldaholic.common.place_bet` | Place bet | Поставить |
| `gui.burmaldaholic.common.ready` | Ready | Готово |
| `gui.burmaldaholic.common.balance` | Balance: %1$s | Баланс: %1$s |
| `gui.burmaldaholic.common.limits` | Min %1$s · Max %2$s | Мин. %1$s · Макс. %2$s |
| `gui.burmaldaholic.common.total_bet` | Total bet: %1$s | Общая ставка: %1$s |
| `gui.burmaldaholic.common.timer` | Time left: %1$s | Осталось: %1$s |
| `gui.burmaldaholic.common.auto_action` | Auto-%1$s in %2$s | Авто: «%1$s» через %2$s |
| `gui.burmaldaholic.common.waiting_players` | Waiting for other players… | Ждём остальных игроков… |
| `gui.burmaldaholic.common.seat` | Seat %1$s | Место %1$s |
| `gui.burmaldaholic.common.seat_empty` | Empty seat | Свободно |
| `gui.burmaldaholic.common.you` | You | Вы |
| `gui.burmaldaholic.common.history` | History | История |
| `gui.burmaldaholic.common.rules` | Rules | Правила |
| `gui.burmaldaholic.common.paytable` | Paytable | Выплаты |
| `gui.burmaldaholic.common.locked` | Locked | Закрыто |
| `gui.burmaldaholic.common.requires_vip` | Requires %1$s VIP | Нужен ВИП-статус «%1$s» |
| `gui.burmaldaholic.common.stake_type` | Stake type | Что ставим |
| `gui.burmaldaholic.common.stake_chips` | Chips | Фишки |
| `gui.burmaldaholic.common.stake_item` | Held item | Предмет в руке |
| `gui.burmaldaholic.common.stake_xp` | Experience levels | Уровни опыта |
| `gui.burmaldaholic.common.stake_hearts` | Hearts | Сердца |
| `gui.burmaldaholic.common.result.win` | Win! +%1$s | Выигрыш! +%1$s |
| `gui.burmaldaholic.common.result.loss` | Lost %1$s | Проигрыш: −%1$s |
| `gui.burmaldaholic.common.result.push` | Push — bet returned | Ничья — ставка возвращена |
| `gui.burmaldaholic.common.result.net` | Net: %1$s | Итог: %1$s |
| `gui.burmaldaholic.common.game.blackjack` | Blackjack | Блэкджек |
| `gui.burmaldaholic.common.game.poker` | Texas Hold'em | Техасский холдем |
| `gui.burmaldaholic.common.game.slots` | Slots | Автоматы |
| `gui.burmaldaholic.common.game.roulette` | Roulette | Рулетка |
| `gui.burmaldaholic.common.game.craps` | Craps | Крэпс |
| `gui.burmaldaholic.common.game.coin_flip` | Coin Flip | Монетка |
| `gui.burmaldaholic.common.game.wheel` | Wheel of Fortune | Колесо фортуны |
| `gui.burmaldaholic.common.game.scratch` | Scratch Cards | Моментальная лотерея |
| `gui.burmaldaholic.common.game.plinko` | Plinko | Плинко |
| `gui.burmaldaholic.common.game.dice_duel` | Dice Duel | Дуэль на костях |

### Errors (shared)

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.error.insufficient_funds` | Not enough chips. Balance: %1$s | Не хватает фишек. На балансе: %1$s |
| `gui.burmaldaholic.error.bet_too_low` | Minimum bet here is %1$s | Минимальная ставка здесь — %1$s |
| `gui.burmaldaholic.error.bet_too_high` | Maximum bet for your VIP tier (%2$s) is %1$s | Максимальная ставка для статуса «%2$s» — %1$s |
| `gui.burmaldaholic.error.table_max` | This table's maximum is %1$s | Лимит этого стола — %1$s |
| `gui.burmaldaholic.error.invalid_amount` | Enter a whole number | Введите целое число |
| `gui.burmaldaholic.error.not_your_turn` | Not your turn | Сейчас не ваш ход |
| `gui.burmaldaholic.error.table_full` | The table is full | Все места заняты |
| `gui.burmaldaholic.error.vip_required` | Requires %1$s VIP or higher | Нужен ВИП-статус «%1$s» или выше |
| `gui.burmaldaholic.error.in_default` | Not while your loan is overdue | Нельзя, пока займ просрочен |
| `gui.burmaldaholic.error.casino_off` | Casino mode is off in this world | В этом мире казино закрыто |
| `gui.burmaldaholic.error.too_far` | You are too far from the table | Вы слишком далеко от стола |
| `gui.burmaldaholic.error.busy` | Finish your current game first | Сначала доиграйте текущую партию |
| `gui.burmaldaholic.error.round_in_progress` | Wait for the current round to finish | Дождитесь конца раунда |
| `gui.burmaldaholic.error.inventory_full` | Inventory full — the rest was dropped at your feet | Инвентарь полон — остальное упало под ноги |
| `gui.burmaldaholic.error.owner_cannot_play` | You can't play at your own tables | За своими столами играть нельзя |
| `gui.burmaldaholic.error.table_closed` | This table is closed | Стол закрыт |
| `gui.burmaldaholic.error.house_broke` | Closed — the house is broke | Закрыто — заведение на мели |
| `gui.burmaldaholic.error.exposure` | The house can't cover a bet that large right now | Сейчас заведение не потянет такую ставку |
| `gui.burmaldaholic.error.disabled` | This game is disabled on this server | Эта игра отключена на сервере |
| `gui.burmaldaholic.error.no_permission` | You don't have permission to do that | У вас нет прав на это |
| `gui.burmaldaholic.error.cooldown` | Available again in %1$s | Снова доступно через %1$s |
| `gui.burmaldaholic.error.pawn_not_accepted` | The house doesn't take this item as a stake | Заведение не принимает этот предмет в залог |
| `gui.burmaldaholic.error.pawn_damaged` | Only undamaged, unenchanted, unnamed items | Только целые предметы без чар и переименований |
| `gui.burmaldaholic.error.pawn_too_valuable` | Stake worth %1$s is over your maximum bet | Залог стоимостью %1$s больше вашей максимальной ставки |
| `gui.burmaldaholic.error.hearts_cap` | You can't wager away more hearts right now | Больше сердец сейчас не поставить |
| `gui.burmaldaholic.error.xp_not_enough` | You don't have that many levels | У вас нет столько уровней |
| `gui.burmaldaholic.error.invalid_bet_position` | That bet isn't possible here | Такую ставку здесь сделать нельзя |
| `gui.burmaldaholic.error.form_busy` | Close chat or other menus to see the casino screen | Закройте чат и другие меню, чтобы увидеть экран казино |

### HUD

| Key | EN | RU |
|-----|----|----|
| `hud.burmaldaholic.balance` | %1$s | %1$s |
| `hud.burmaldaholic.streak.lucky` | Lucky ×%1$s | Везёт ×%1$s |
| `hud.burmaldaholic.streak.unlucky` | Unlucky ×%1$s | Не везёт ×%1$s |
| `hud.burmaldaholic.vip` | %1$s | %1$s |
| `hud.burmaldaholic.loan` | Loan: %1$s · owed %2$s | Займ: %1$s · долг %2$s |
| `hud.burmaldaholic.loan_default` | OVERDUE · owed %1$s | ПРОСРОЧКА · долг %1$s |
| `hud.burmaldaholic.golden_hour` | Golden Hour %1$s | «Золотой час» %1$s |
| `hud.burmaldaholic.time.dhm` | %1$sd %2$s | %1$sд %2$s |
| `hud.burmaldaholic.actionbar` | %1$s · %2$s · %3$s | %1$s · %2$s · %3$s |
| `hud.burmaldaholic.golden_hour.over` | Golden Hour is over | «Золотой час» окончен |
| `hud.burmaldaholic.collectors` | Collectors: %1$s m | Коллекторы: %1$s м |

### Win tiers and celebrations (animation wave; `docs/design/animation/global.md` §9, `docs/architecture/animation.md` §4)

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.fx.tier.win` | WIN | ВЫИГРЫШ |
| `gui.burmaldaholic.fx.tier.nice` | NICE WIN | НЕПЛОХО! |
| `gui.burmaldaholic.fx.tier.big` | BIG WIN | КРУПНЫЙ ВЫИГРЫШ |
| `gui.burmaldaholic.fx.tier.mega` | MEGA WIN | МЕГАВЫИГРЫШ |
| `gui.burmaldaholic.fx.tier.epic` | EPIC WIN | ЭПИЧЕСКИЙ ВЫИГРЫШ |
| `gui.burmaldaholic.fx.tier.jackpot` | JACKPOT | ДЖЕКПОТ |
| `gui.burmaldaholic.fx.tier.push` | PUSH | НИЧЬЯ |
| `gui.burmaldaholic.fx.tier.loss` | NO WIN | БЕЗ ВЫИГРЫША |
| `gui.burmaldaholic.fx.returned` | Returned %1$s | Возвращено: %1$s |
| `gui.burmaldaholic.fx.amount` | +%1$s | +%1$s |
| `gui.burmaldaholic.fx.multiplier` | ×%1$s your bet | ×%1$s от ставки |
| `gui.burmaldaholic.fx.skip` | Click to skip | Нажмите, чтобы пропустить |
| `gui.burmaldaholic.fx.nearby_float` | %1$s +%2$s | %1$s +%2$s |

### Messages

| Key | EN | RU |
|-----|----|----|
| `msg.burmaldaholic.core.welcome` | Welcome to Burmaldaholic! Here's your Casino Card and %1$s for luck. | Добро пожаловать в Бурмалдоголик! Держите клубную карту и %1$s на удачу. |
| `msg.burmaldaholic.core.welcome_hint` | Mine, fight and trade to earn chips. Spend them at the tables. Try not to spend all of them. | Копайте, сражайтесь и торгуйте — за это дают фишки. Тратьте их за столами. Желательно не все. |
| `msg.burmaldaholic.core.mode_enabled` | Casino mode is ON. The doors are open. | Режим казино ВКЛЮЧЁН. Двери открыты. |
| `msg.burmaldaholic.core.mode_disabled` | Casino mode is OFF. Your chips are safe until it reopens. | Режим казино ВЫКЛЮЧЕН. Фишки сохранятся до открытия. |
| `msg.burmaldaholic.core.command.mode_set_on` | Casino mode is now ON for this world | Режим казино в этом мире теперь ВКЛЮЧЁН |
| `msg.burmaldaholic.core.command.mode_set_off` | Casino mode is now OFF for this world | Режим казино в этом мире теперь ВЫКЛЮЧЕН |
| `msg.burmaldaholic.core.command.mode_status_on` | Casino mode is ON in this world | В этом мире режим казино ВКЛЮЧЁН |
| `msg.burmaldaholic.core.command.mode_status_off` | Casino mode is OFF in this world | В этом мире режим казино ВЫКЛЮЧЕН |
| `msg.burmaldaholic.core.earned` | +%1$s (%2$s) | +%1$s (%2$s) |
| `msg.burmaldaholic.core.source.trade` | Trade | Торговля |
| `msg.burmaldaholic.core.source.contract` | Contract | Заказ |
| `msg.burmaldaholic.core.source.cashback` | VIP cashback | ВИП-кешбэк |
| `msg.burmaldaholic.core.source.golden_hour` | Golden Hour bonus | Бонус «Золотого часа» |
| `msg.burmaldaholic.core.balance_capped` | Your balance is at the maximum. The rest went to charity. | Баланс на максимуме. Остальное ушло на благотворительность. |
| `msg.burmaldaholic.core.bankroll_late_return` | %1$s came back to your closed casino and was paid to you. | В кассу закрытого казино вернулось %1$s — они выплачены вам. |
| `msg.burmaldaholic.core.deposited` | Deposited %1$s. Balance: %2$s | Внесено: %1$s. Баланс: %2$s |
| `msg.burmaldaholic.core.withdrawn` | Withdrew %1$s. Balance: %2$s | Снято: %1$s. Баланс: %2$s |
| `msg.burmaldaholic.core.no_chips_to_deposit` | You have no chips to deposit | Вам нечего внести — фишек нет |
| `msg.burmaldaholic.core.bought_chips` | Bought %1$s for %2$s | Куплено: %1$s за %2$s |
| `msg.burmaldaholic.core.sold_chips` | Sold %1$s for %2$s | Продано: %1$s за %2$s |
| `msg.burmaldaholic.core.big_win` | Big win: %1$s takes %2$s at %3$s! | Крупный выигрыш: %1$s забирает %2$s — %3$s! |
| `msg.burmaldaholic.core.round_refunded` | The server restarted mid-round. Your bet of %1$s was refunded. | Сервер перезапустился посреди раунда. Ставка %1$s возвращена. |
| `msg.burmaldaholic.core.round_played_out` | The server restarted mid-round. Your %1$s round was already decided, so it was played out: %2$s | Сервер перезапустился посреди раунда. Исход раунда «%1$s» уже был определён, поэтому он доигран: %2$s |
| `msg.burmaldaholic.core.auto_completed` | You left mid-round. The dealer finished it for you: %1$s | Вы ушли посреди раунда. Дилер доиграл за вас: %1$s |
| `msg.burmaldaholic.core.card_given` | You received a Casino Card | Вы получили клубную карту |

### Casino Menu

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.menu.title` | Casino Menu | Меню казино |
| `gui.burmaldaholic.menu.wallet` | Wallet | Кошелёк |
| `gui.burmaldaholic.menu.contracts` | Contracts | Заказы |
| `gui.burmaldaholic.menu.loan` | Loan | Займ |
| `gui.burmaldaholic.menu.achievements` | Achievements | Достижения |
| `gui.burmaldaholic.menu.challenges` | Challenges | Вызовы |
| `gui.burmaldaholic.menu.my_casino` | My Casino | Моё казино |
| `gui.burmaldaholic.menu.rules` | Rules | Правила |
| `gui.burmaldaholic.menu.settings` | Settings | Настройки |
| `gui.burmaldaholic.menu.admin` | Admin | Админ |
| `gui.burmaldaholic.menu.wallet.lifetime` | Wagered all-time: %1$s | Поставлено за всё время: %1$s |
| `gui.burmaldaholic.menu.wallet.today` | Today's result: %1$s | Итог за сегодня: %1$s |
| `gui.burmaldaholic.menu.wallet.streak` | Streak: %1$s | Серия: %1$s |
| `gui.burmaldaholic.menu.wallet.streak_none` | No streak | Серии нет |
| `gui.burmaldaholic.menu.rules.difficulty` | Difficulty effects: %1$s | Влияние сложности: %1$s |
| `gui.burmaldaholic.menu.rules.last_chance` | Last Chance: %1$s chance, cooldown %2$s | «Последний шанс»: вероятность %1$s, перезарядка %2$s |
| `gui.burmaldaholic.menu.rules.last_chance_ready` | Last Chance is ready | «Последний шанс» готов |
| `gui.burmaldaholic.menu.rules.last_chance_cooldown` | Last Chance recharges in %1$s | «Последний шанс» перезарядится через %1$s |
| `gui.burmaldaholic.menu.rules.last_chance_off` | Last Chance is disabled in this world | «Последний шанс» в этом мире отключён |
| `gui.burmaldaholic.menu.rules.hardcore_high_stakes` | Hardcore: High-Stakes Last Chance (costs all chips and a heart forever) | Хардкор: «Последний шанс» по-крупному (все фишки и одно сердце навсегда) |
| `gui.burmaldaholic.menu.rules.chaos` | Chaos events: %1$s | Хаос-события: %1$s |
| `gui.burmaldaholic.menu.rules.disclaimer` | This is a game. Real gambling can hurt — play for fun, not money. | Это игра. Настоящий азарт может навредить — играйте ради веселья, а не денег. |
| `gui.burmaldaholic.menu.settings.hud` | Show HUD | Показывать панель |
| `gui.burmaldaholic.menu.settings.hud_corner` | HUD corner | Угол панели |
| `gui.burmaldaholic.menu.settings.corner.top_left` | Top left | Слева сверху |
| `gui.burmaldaholic.menu.settings.corner.top_right` | Top right | Справа сверху |
| `gui.burmaldaholic.menu.settings.corner.bottom_left` | Bottom left | Слева снизу |
| `gui.burmaldaholic.menu.settings.corner.bottom_right` | Bottom right | Справа снизу |
| `gui.burmaldaholic.menu.settings.sounds` | Casino sounds | Звуки казино |
| `gui.burmaldaholic.menu.settings.auto_muck` | Poker: auto-muck losing hands | Покер: автосброс проигравших рук |
| `gui.burmaldaholic.menu.admin.world_settings` | World settings | Настройки мира |
| `gui.burmaldaholic.menu.admin.give_chips` | Give chips | Выдать фишки |
| `gui.burmaldaholic.menu.admin.take_chips` | Take chips | Забрать фишки |
| `gui.burmaldaholic.menu.admin.clear_debt` | Clear debt | Списать долг |
| `gui.burmaldaholic.menu.admin.reset_jackpots` | Reset jackpots | Сбросить джекпоты |
| `gui.burmaldaholic.menu.admin.player` | Player | Игрок |
| `gui.burmaldaholic.menu.admin.done` | Done: %1$s | Готово: %1$s |
| `gui.burmaldaholic.menu.admin.rtp_warning` | Warning: %1$s has RTP %2$s — above 99%% | Внимание: у «%1$s» RTP %2$s — выше 99 %% |
| `gui.burmaldaholic.menu.settings.reduce_motion` | Reduce motion | Меньше анимации |
| `gui.burmaldaholic.menu.settings.flashes` | Screen flashes | Вспышки экрана |
| `gui.burmaldaholic.menu.settings.anim_speed` | Animation speed | Скорость анимации |
| `gui.burmaldaholic.menu.settings.anim_speed.slow` | Slow | Медленно |
| `gui.burmaldaholic.menu.settings.anim_speed.normal` | Normal | Обычно |
| `gui.burmaldaholic.menu.settings.anim_speed.turbo` | Turbo | Турбо |
| `gui.burmaldaholic.menu.settings.celebrations` | Win celebrations | Празднование выигрышей |
| `gui.burmaldaholic.menu.settings.celebrations.all` | Everyone's | Все |
| `gui.burmaldaholic.menu.settings.celebrations.mine` | Only mine | Только мои |
| `gui.burmaldaholic.menu.settings.celebrations.off` | Off | Выкл. |
| `gui.burmaldaholic.menu.settings.fx_volume` | Effects volume: %1$s%% | Громкость эффектов: %1$s %% |
| `gui.burmaldaholic.menu.settings.hud_panel` | Styled HUD panel | Фирменная панель |
| `gui.burmaldaholic.menu.settings.squeeze_peel` | Baccarat: peel cards myself | Баккара: вскрывать карты самому |
| `gui.burmaldaholic.menu.settings.spin_camera` | Spin camera | Обзор колеса |
| `gui.burmaldaholic.menu.settings.spin_camera.tooltip` | Roulette: the camera looks at the wheel during your spin. Any movement cancels it. | Рулетка: во время вашего вращения камера смотрит на колесо. Любое движение её отключает. |

### Cashier

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.cashier.title` | Cashier | Касса |
| `gui.burmaldaholic.cashier.deposit_all` | Deposit all chips | Внести все фишки |
| `gui.burmaldaholic.cashier.deposit_held` | Deposit held stack | Внести фишки из руки |
| `gui.burmaldaholic.cashier.withdraw` | Withdraw | Снять |
| `gui.burmaldaholic.cashier.withdrawable` | Withdrawable: %1$s | Доступно к снятию: %1$s |
| `gui.burmaldaholic.cashier.withdrawable_loan` | Withdrawable: %1$s (loan %2$s) | Доступно к снятию: %1$s (займ %2$s) |
| `gui.burmaldaholic.cashier.denomination` | Denomination | Номинал |
| `gui.burmaldaholic.cashier.denomination.auto` | Auto | Авто |
| `gui.burmaldaholic.cashier.buy` | Buy %1$s for 1 emerald | Купить %1$s за 1 изумруд |
| `gui.burmaldaholic.cashier.sell` | Sell %1$s for 1 emerald | Продать %1$s за 1 изумруд |
| `gui.burmaldaholic.cashier.buy_gold` | Buy %1$s for 1 gold ingot | Купить %1$s за 1 золотой слиток |
| `gui.burmaldaholic.cashier.sell_gold` | Sell %1$s for 1 gold ingot | Продать %1$s за 1 золотой слиток |
| `gui.burmaldaholic.cashier.times` | ×%1$s | ×%1$s |
| `gui.burmaldaholic.cashier.emeralds_slider` | Emeralds: %1$s | Изумруды: %1$s |
| `gui.burmaldaholic.cashier.shop` | Shop | Лавка |
| `gui.burmaldaholic.cashier.shop.buy_item` | Buy %1$s — %2$s | Купить «%1$s» — %2$s |
| `gui.burmaldaholic.cashier.withdraw_blocked` | Withdrawals are frozen while your loan is overdue | Пока займ просрочен, снятие заморожено |
| `gui.burmaldaholic.cashier.counting` | Counting… | Считаем… |
| `gui.burmaldaholic.cashier.deposited` | Deposited %1$s | Внесено: %1$s |
| `gui.burmaldaholic.cashier.withdrawn` | Withdrawn %1$s | Выдано: %1$s |
| `gui.burmaldaholic.cashier.dropped` | (%1$s dropped at your feet) | (%1$s — у ваших ног) |
| `gui.burmaldaholic.cashier.exchanged` | Exchanged: %1$s | Обмен: %1$s |

### Contracts

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.contracts.title` | Today's contracts | Заказы дня |
| `gui.burmaldaholic.contracts.progress` | %1$s/%2$s | %1$s/%2$s |
| `gui.burmaldaholic.contracts.reward` | Reward: %1$s | Награда: %1$s |
| `gui.burmaldaholic.contracts.reroll` | Reroll (%1$s) | Сменить (%1$s) |
| `gui.burmaldaholic.contracts.rerolled_already` | Already rerolled today | Сегодня уже меняли |
| `gui.burmaldaholic.contracts.done` | Done | Готово |
| `gui.burmaldaholic.contracts.resets_in` | New contracts in %1$s | Новые заказы через %1$s |
| `gui.burmaldaholic.contracts.task.mine_iron` | Mine iron ore: %1$s | Добыть железную руду: %1$s |
| `gui.burmaldaholic.contracts.task.mine_coal` | Mine coal ore: %1$s | Добыть угольную руду: %1$s |
| `gui.burmaldaholic.contracts.task.mine_diamond` | Mine diamond ore: %1$s | Добыть алмазную руду: %1$s |
| `gui.burmaldaholic.contracts.task.kill_zombie` | Defeat zombies: %1$s | Победить зомби: %1$s |
| `gui.burmaldaholic.contracts.task.kill_skeleton` | Defeat skeletons: %1$s | Победить скелетов: %1$s |
| `gui.burmaldaholic.contracts.task.kill_creeper` | Defeat creepers: %1$s | Победить криперов: %1$s |
| `gui.burmaldaholic.contracts.task.kill_any` | Defeat hostile mobs: %1$s | Победить враждебных мобов: %1$s |
| `gui.burmaldaholic.contracts.task.trade` | Trade with villagers: %1$s | Сделки с жителями: %1$s |
| `gui.burmaldaholic.contracts.task.fish` | Catch fish: %1$s | Поймать рыбу: %1$s |
| `gui.burmaldaholic.contracts.task.harvest` | Harvest crops: %1$s | Собрать урожай: %1$s |
| `gui.burmaldaholic.contracts.task.wager` | Wager chips in total: %1$s | Поставить фишек в сумме: %1$s |
| `gui.burmaldaholic.contracts.task.win_blackjack` | Win blackjack hands: %1$s | Выиграть раздач в блэкджек: %1$s |
| `gui.burmaldaholic.contracts.task.spin_slots` | Spin slot machines: %1$s | Прокрутить автоматы: %1$s |
| `gui.burmaldaholic.contracts.task.roulette_red` | Win bets on red: %1$s | Выиграть ставок на красное: %1$s |
| `gui.burmaldaholic.contracts.task.play_poker` | Play poker hands: %1$s | Сыграть покерных раздач: %1$s |
| `gui.burmaldaholic.contracts.task.explore_nether` | Travel blocks in the Nether: %1$s | Пройти блоков в Незере: %1$s |
| `gui.burmaldaholic.contracts.task.smelt` | Smelt items: %1$s | Переплавить предметов: %1$s |
| `msg.burmaldaholic.contracts.new` | New contracts are in. The casino has errands for you. | Новые заказы на сегодня. У заведения есть для вас поручения. |
| `msg.burmaldaholic.contracts.completed` | Contract done: %1$s — +%2$s | Заказ выполнен: %1$s — +%2$s |
| `msg.burmaldaholic.contracts.rerolled` | Contract swapped for %1$s | Заказ заменён за %1$s |
| `msg.burmaldaholic.contracts.all_done` | All contracts done. Go treat yourself (responsibly). | Все заказы выполнены. Можно себя побаловать (в меру). |
| `gui.burmaldaholic.contracts.task.slots_feature` | Trigger slot features: %1$s | Запустить бонусы на автоматах: %1$s |

### Pawn wagers (non-chip stakes)

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.wager.title` | Stake something else | Поставить в залог |
| `gui.burmaldaholic.wager.item_value` | %1$s — appraised at %2$s | %1$s — оценка: %2$s |
| `gui.burmaldaholic.wager.xp_levels` | Levels to stake | Уровней на кон |
| `gui.burmaldaholic.wager.xp_value` | %1$s — worth %2$s | %1$s — это %2$s |
| `gui.burmaldaholic.wager.hearts` | Hearts to stake | Сердец на кон |
| `gui.burmaldaholic.wager.hearts_value` | %1$s for %2$s — worth %3$s | %1$s на %2$s — это %3$s |
| `gui.burmaldaholic.wager.hearts_warning` | If you lose, your max health drops for one day | Проиграете — максимум здоровья уменьшится на сутки |
| `msg.burmaldaholic.wager.item_lost` | The house keeps your %1$s | Заведение оставляет себе: %1$s |
| `msg.burmaldaholic.wager.item_returned` | Your %1$s is returned, plus %2$s | Возвращаем вам: %1$s — и сверху %2$s |
| `msg.burmaldaholic.wager.xp_lost` | You lost %1$s | Вы потеряли %1$s |
| `msg.burmaldaholic.wager.hearts_lost` | You lost %1$s until tomorrow | Минус %1$s до завтра |
| `msg.burmaldaholic.wager.hearts_restored` | Your borrowed hearts have been returned | Одолженные сердца вернулись на место |
| `msg.burmaldaholic.wager.soul_cooldown` | Your soul is still being processed. Try again in %1$s | Ваша душа ещё на рассмотрении. Попробуйте через %1$s |

### Toasts (animation wave, `animation/global.md` §4.10)

| Key | EN | RU |
|-----|----|----|
| `toast.burmaldaholic.jackpot.title` | Jackpot on the server! | Джекпот на сервере! |
| `toast.burmaldaholic.jackpot.body` | %1$s · %2$s | %1$s · %2$s |
| `toast.burmaldaholic.big_win.title` | Big win | Крупный выигрыш |
| `toast.burmaldaholic.big_win.body` | %1$s · %2$s | %1$s · %2$s |
| `toast.burmaldaholic.contract.title` | Contract complete | Заказ выполнен |
| `toast.burmaldaholic.contract.body` | Reward: %1$s | Награда: %1$s |
| `toast.burmaldaholic.cashback.title` | Cashback | Кешбэк |
| `toast.burmaldaholic.cashback.body` | %1$s returned | Возвращено: %1$s |
| `toast.burmaldaholic.vip.title` | New VIP tier | Новый уровень ВИП |
| `toast.burmaldaholic.vip.body` | %1$s · max bet %2$s | %1$s · ставка до %2$s |
| `toast.burmaldaholic.vip_netherite.title` | Netherite VIP | Незеритовый ВИП |
| `toast.burmaldaholic.golden_hour.ending` | Golden Hour ends in %1$s | «Золотой час» закончится через %1$s |
| `toast.burmaldaholic.achievement.title` | Achievement unlocked | Достижение получено |

---

## streak

| Key | EN | RU |
|-----|----|----|
| `msg.burmaldaholic.streak.lucky_5` | You're on a roll! Streak ×5 | Попёрло! Серия ×5 |
| `msg.burmaldaholic.streak.lucky_10` | ON FIRE! Streak ×10 — luck is on your side (a little) | В УДАРЕ! Серия ×10 — удача на вашей стороне (немножко) |
| `msg.burmaldaholic.streak.unlucky_5` | Rough patch. Losing streak ×5 — the house feels a bit sorry for you | Чёрная полоса. Серия неудач ×5 — заведению вас даже жалко |
| `msg.burmaldaholic.streak.unlucky_10` | Black cat crossed your path. Losing streak ×10 | Вам перешла дорогу чёрная кошка. Серия неудач ×10 |
| `msg.burmaldaholic.streak.broken_lucky` | Your lucky streak is over | Серия удачи прервалась |
| `msg.burmaldaholic.streak.broken_unlucky` | The losing streak is broken! | Чёрная полоса позади! |
| `gui.burmaldaholic.streak.tooltip` | Streaks slightly tilt slot machines, the wheel, Plinko, scratch cards and coin flips. Table games are always honest. | Серия чуть-чуть влияет на автоматы, колесо, Плинко, лотерею и монетку. Настольные игры всегда честные. |

---

## vip

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.vip.tier.bronze` | Bronze | Бронза |
| `gui.burmaldaholic.vip.tier.silver` | Silver | Серебро |
| `gui.burmaldaholic.vip.tier.gold` | Gold | Золото |
| `gui.burmaldaholic.vip.tier.platinum` | Platinum | Платина |
| `gui.burmaldaholic.vip.tier.diamond` | Diamond | Алмаз |
| `gui.burmaldaholic.vip.tier.netherite` | Netherite | Незерит |
| `gui.burmaldaholic.vip.title` | VIP status | ВИП-статус |
| `gui.burmaldaholic.vip.current` | Your tier: %1$s | Ваш статус: %1$s |
| `gui.burmaldaholic.vip.progress` | To %1$s: %2$s / %3$s wagered | До статуса «%1$s»: поставлено %2$s из %3$s |
| `gui.burmaldaholic.vip.max_tier` | Top tier reached. The house bows. | Высший статус. Заведение кланяется. |
| `gui.burmaldaholic.vip.max_bet` | Max bet: %1$s | Макс. ставка: %1$s |
| `gui.burmaldaholic.vip.perk.poker_micro` | Micro-stakes poker | Покер на микролимитах |
| `gui.burmaldaholic.vip.perk.poker_low` | Low-stakes poker | Покер на низких лимитах |
| `gui.burmaldaholic.vip.perk.poker_mid` | Mid-stakes poker | Покер на средних лимитах |
| `gui.burmaldaholic.vip.perk.poker_high` | High-stakes poker | Покер на высоких лимитах |
| `gui.burmaldaholic.vip.perk.scratch_gold` | Golden scratch cards | Золотая лотерея |
| `gui.burmaldaholic.vip.perk.loan` | Loans up to %1$s | Займы до %1$s |
| `gui.burmaldaholic.vip.perk.contract_bonus` | +%1$s%% contract rewards | +%1$s %% к наградам за заказы |
| `gui.burmaldaholic.vip.perk.contract_slots` | %1$s contract slots | Слотов для заказов: %1$s |
| `gui.burmaldaholic.vip.perk.netherite_slots` | End Void slot machine | Автомат «Пустота Края» |
| `gui.burmaldaholic.vip.perk.high_roller` | High Roller tables | Столы хайроллеров |
| `gui.burmaldaholic.vip.perk.emerald_rate` | Better emerald rate at the Cashier | Выгодный курс изумрудов в кассе |
| `gui.burmaldaholic.vip.perk.cashback` | %1$s%% daily cashback on expected losses | Ежедневный кешбэк %1$s %% от ожидаемого проигрыша |
| `gui.burmaldaholic.vip.perk.loan_discount` | −2%% loan interest | −2 %% по займам |
| `gui.burmaldaholic.vip.perk.cosmetic_name` | Colored name at tables | Цветное имя за столами |
| `gui.burmaldaholic.vip.perk.cosmetic_particles` | Win particles | Эффекты при выигрыше |
| `gui.burmaldaholic.vip.perk.cosmetic_title` | Chat title | Титул в чате |
| `gui.burmaldaholic.vip.perk.cosmetic_card` | Diamond Casino Card | Алмазная клубная карта |
| `gui.burmaldaholic.vip.perk.cosmetic_aura` | Netherite aura | Незеритовая аура |
| `msg.burmaldaholic.vip.promoted` | VIP status upgraded: %1$s! New max bet: %2$s | Новый ВИП-статус: %1$s! Максимальная ставка теперь %2$s |
| `msg.burmaldaholic.vip.promoted_title` | VIP: %1$s | ВИП: %1$s |
| `msg.burmaldaholic.vip.netherite_broadcast` | %1$s has reached Netherite VIP. Someone roll out the red carpet. | Незеритовый ВИП-статус у игрока %1$s. Несите красную дорожку. |
| `msg.burmaldaholic.vip.cashback` | VIP cashback for yesterday: +%1$s | ВИП-кешбэк за вчера: +%1$s |
| `msg.burmaldaholic.vip.chat_title` | [%1$s] | [%1$s] |
| `item.burmaldaholic.vip_diamond_casino_card` | Diamond Casino Card | Алмазная клубная карта |
| `key.category.burmaldaholic.vip` | Burmaldaholic | Burmaldaholic |
| `hud.burmaldaholic.vip.next` | Next: %1$s %2$s%% | Далее: %1$s — %2$s %% |
| `hud.burmaldaholic.vip.contracts` | Contracts: %1$s/%2$s | Заказы: %1$s/%2$s |
| `gui.burmaldaholic.vip.tier_line` | %1$s — at %2$s wagered · max bet %3$s | %1$s — порог %2$s · макс. ставка %3$s |
| `gui.burmaldaholic.vip.contracts_off` | Contracts are disabled in this world | Заказы в этом мире отключены |
| `gui.burmaldaholic.vip.contract_unavailable` | This contract can't be swapped anymore | Этот заказ уже нельзя заменить |
| `gui.burmaldaholic.vip.loading` | Loading… | Загрузка… |
| `gui.burmaldaholic.vip.perk_bullet` | • %1$s | • %1$s |
| `gui.burmaldaholic.vip.contract_status` | %1$s · %2$s | %1$s · %2$s |
| `gui.burmaldaholic.vip.tier_up` | VIP tier up! | Новый уровень ВИП! |

---

## blackjack

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.blackjack.title` | Blackjack | Блэкджек |
| `gui.burmaldaholic.blackjack.title_high_roller` | Blackjack — High Rollers | Блэкджек — хайроллеры |
| `gui.burmaldaholic.blackjack.bet_title` | Blackjack — place your bet | Блэкджек — ваша ставка |
| `gui.burmaldaholic.blackjack.dealer` | Dealer | Дилер |
| `gui.burmaldaholic.blackjack.hit` | Hit | Ещё |
| `gui.burmaldaholic.blackjack.stand` | Stand | Хватит |
| `gui.burmaldaholic.blackjack.double` | Double | Удвоить |
| `gui.burmaldaholic.blackjack.split` | Split | Разделить |
| `gui.burmaldaholic.blackjack.surrender` | Surrender | Сдаться |
| `gui.burmaldaholic.blackjack.insurance` | Insurance | Страховка |
| `gui.burmaldaholic.blackjack.hit.tooltip` | Take another card | Взять ещё карту |
| `gui.burmaldaholic.blackjack.stand.tooltip` | Keep your hand | Остановиться на этих картах |
| `gui.burmaldaholic.blackjack.double.tooltip` | Double your bet, take exactly one card | Дабл: удвоить ставку и взять ровно одну карту |
| `gui.burmaldaholic.blackjack.split.tooltip` | Split a pair into two hands (same bet on each) | Сплит: разбить пару на две руки с такой же ставкой на каждой |
| `gui.burmaldaholic.blackjack.surrender.tooltip` | Give up and get half your bet back | Сдаться и вернуть половину ставки |
| `gui.burmaldaholic.blackjack.insurance_prompt` | The dealer shows an Ace. Insurance? | У дилера туз. Страхуемся? |
| `gui.burmaldaholic.blackjack.insure` | Insure (%1$s) | Застраховать (%1$s) |
| `gui.burmaldaholic.blackjack.no_insurance` | No insurance | Без страховки |
| `gui.burmaldaholic.blackjack.even_money_prompt` | You have blackjack and the dealer shows an Ace. Take even money? | У вас блэкджек, у дилера туз. Забрать выплату 1:1 сразу? |
| `gui.burmaldaholic.blackjack.even_money` | Take even money | Забрать 1:1 |
| `gui.burmaldaholic.blackjack.decline_even_money` | Play it out | Играть до конца |
| `gui.burmaldaholic.blackjack.total` | Total: %1$s | Сумма: %1$s |
| `gui.burmaldaholic.blackjack.total_soft` | Total: %1$s/%2$s | Сумма: %1$s/%2$s |
| `gui.burmaldaholic.blackjack.hand` | Hand %1$s | Рука %1$s |
| `gui.burmaldaholic.blackjack.your_turn` | Your turn | Ваш ход |
| `gui.burmaldaholic.blackjack.betting_open` | Place your bets | Делайте ставки |
| `gui.burmaldaholic.blackjack.shuffling` | Shuffling the shoe… | Перемешиваем шуз… |
| `gui.burmaldaholic.blackjack.dealer_peeks` | The dealer checks for blackjack… | Дилер проверяет блэкджек… |
| `gui.burmaldaholic.blackjack.rules.1` | 6 decks · Dealer stands on soft 17 · Blackjack pays 3:2 | 6 колод · Дилер стоит на мягких 17 · Блэкджек платит 3:2 |
| `gui.burmaldaholic.blackjack.rules.2` | Double on any two cards, also after split · Split up to 4 hands | Дабл на любых двух картах, в том числе после сплита · До 4 рук |
| `gui.burmaldaholic.blackjack.rules.3` | Split aces get one card each · Insurance pays 2:1 | На разделённых тузов — по одной карте · Страховка платит 2:1 |
| `gui.burmaldaholic.blackjack.rules.h17` | Dealer hits soft 17 | Дилер берёт на мягких 17 |
| `gui.burmaldaholic.blackjack.result.blackjack` | Blackjack! +%1$s | Блэкджек! +%1$s |
| `gui.burmaldaholic.blackjack.result.win` | You win +%1$s | Победа +%1$s |
| `gui.burmaldaholic.blackjack.result.push` | Push | Ничья |
| `gui.burmaldaholic.blackjack.result.bust` | Bust | Перебор |
| `gui.burmaldaholic.blackjack.result.lose` | Dealer wins | Дилер выиграл |
| `gui.burmaldaholic.blackjack.result.dealer_bust` | Dealer busts! +%1$s | У дилера перебор! +%1$s |
| `gui.burmaldaholic.blackjack.result.dealer_blackjack` | Dealer has blackjack | У дилера блэкджек |
| `gui.burmaldaholic.blackjack.result.surrender` | Surrendered, half returned | Сдались, половина возвращена |
| `gui.burmaldaholic.blackjack.result.insurance_paid` | Insurance pays +%1$s | Страховка сыграла: +%1$s |
| `gui.burmaldaholic.blackjack.result.insurance_lost` | Insurance lost | Страховка сгорела |
| `gui.burmaldaholic.blackjack.result.even_money` | Even money paid +%1$s | Выплата 1:1: +%1$s |
| `msg.burmaldaholic.blackjack.auto_stand` | Time's up — you stand | Время вышло — остаётесь при своих картах |
| `msg.burmaldaholic.blackjack.player_joined` | %1$s sits down at the table | За стол садится %1$s |
| `msg.burmaldaholic.blackjack.player_left` | %1$s leaves the table | Из-за стола встаёт %1$s |
| `msg.burmaldaholic.blackjack.round_starts_in` | Dealing in %1$s | Раздача через %1$s |

### Cards (shared with poker)

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.card.hidden` | Hidden card | Закрытая карта |
| `gui.burmaldaholic.card.rank.a` | A | Т |
| `gui.burmaldaholic.card.rank.k` | K | К |
| `gui.burmaldaholic.card.rank.q` | Q | Д |
| `gui.burmaldaholic.card.rank.j` | J | В |
| `gui.burmaldaholic.card.rank.10` | 10 | 10 |
| `gui.burmaldaholic.card.suit.spades` | Spades | Пики |
| `gui.burmaldaholic.card.suit.hearts` | Hearts | Черви |
| `gui.burmaldaholic.card.suit.diamonds` | Diamonds | Бубны |
| `gui.burmaldaholic.card.suit.clubs` | Clubs | Трефы |
| `gui.burmaldaholic.card.narration` | %1$s of %2$s | %1$s, %2$s |
| `gui.burmaldaholic.card.name.a` | Ace | Туз |
| `gui.burmaldaholic.card.name.k` | King | Король |
| `gui.burmaldaholic.card.name.q` | Queen | Дама |
| `gui.burmaldaholic.card.name.j` | Jack | Валет |
| `entity.burmaldaholic.blackjack_dealer` | Blackjack Dealer | Дилер блэкджека |
| `item.burmaldaholic.blackjack_dealer_spawn_egg` | Blackjack Dealer Spawn Egg | Яйцо призыва дилера блэкджека |
| `gui.burmaldaholic.blackjack.sit` | Take a seat | Сесть за стол |
| `gui.burmaldaholic.blackjack.no_bet` | No bet yet | Ещё без ставки |
| `gui.burmaldaholic.blackjack.turn_of` | %1$s is playing… | Ходит %1$s… |
| `msg.burmaldaholic.blackjack.no_table` | The dealer needs a blackjack table right next to them | Дилеру нужен стол для блэкджека совсем рядом |
| `gui.burmaldaholic.cards.narrate.dealt` | %1$s receives %2$s | %1$s: карта %2$s |
| `gui.burmaldaholic.cards.narrate.shows` | %1$s shows %2$s | %1$s открывает: %2$s |
| `gui.burmaldaholic.cards.narrate.dealt_all` | Cards dealt | Карты розданы |
| `gui.burmaldaholic.cards.narrate.card` | %1$s of %2$s | %1$s, %2$s |
| `gui.burmaldaholic.cards.hidden_card` | face-down card | закрытая карта |

Ranks 2–9 use the digit directly (no key). Card glyphs carry the rank letter of the **English**
deck on the texture; the `rank.*` letters are used only for text-only rendering (narration).

### Presentation (animation wave, `animation/cards.md` §11)

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.blackjack.fx.blackjack` | BLACKJACK! | БЛЭКДЖЕК! |
| `gui.burmaldaholic.blackjack.fx.bust` | BUST | ПЕРЕБОР |
| `gui.burmaldaholic.blackjack.fx.twenty_one` | 21! | 21! |
| `gui.burmaldaholic.blackjack.fx.dealer_reveals` | Dealer reveals the hole card | Дилер открывает закрытую карту |
| `gui.burmaldaholic.blackjack.fx.dealer_draws` | Dealer draws | Дилер берёт карту |
| `gui.burmaldaholic.blackjack.fx.dealer_stands` | Dealer stands on %1$s | Дилер останавливается на %1$s |
| `gui.burmaldaholic.blackjack.fx.dealer_busts` | Dealer busts with %1$s! | У дилера перебор: %1$s! |
| `gui.burmaldaholic.blackjack.fx.dealer_blackjack` | Dealer blackjack | Блэкджек у дилера |
| `gui.burmaldaholic.blackjack.fx.doubled` | Doubled | Удвоено |
| `gui.burmaldaholic.blackjack.actionbar` | Dealer %1$s · %2$s | Дилер %1$s · %2$s |
| `gui.burmaldaholic.blackjack.actionbar_seat` | %1$s %2$s | %1$s %2$s |

---

## poker

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.poker.title` | Texas Hold'em | Техасский холдем |
| `gui.burmaldaholic.poker.stakes.micro` | Micro (%1$s/%2$s) | Микро (%1$s/%2$s) |
| `gui.burmaldaholic.poker.stakes.low` | Low (%1$s/%2$s) | Низкие (%1$s/%2$s) |
| `gui.burmaldaholic.poker.stakes.mid` | Mid (%1$s/%2$s) | Средние (%1$s/%2$s) |
| `gui.burmaldaholic.poker.stakes.high` | High (%1$s/%2$s) | Высокие (%1$s/%2$s) |
| `gui.burmaldaholic.poker.choose_stakes` | Choose stakes | Выберите лимит |
| `gui.burmaldaholic.poker.buy_in` | Buy-in | Бай-ин |
| `gui.burmaldaholic.poker.buy_in_range` | Buy-in: %1$s – %2$s | Бай-ин: %1$s – %2$s |
| `gui.burmaldaholic.poker.sit_down` | Sit down | Сесть |
| `gui.burmaldaholic.poker.top_up` | Top up | Докупить |
| `gui.burmaldaholic.poker.stand_up` | Stand up | Встать |
| `gui.burmaldaholic.poker.sit_out` | Sit out | Отойти |
| `gui.burmaldaholic.poker.sit_in` | I'm back | Вернуться |
| `gui.burmaldaholic.poker.next_hand` | Next hand | Новая раздача |
| `gui.burmaldaholic.poker.fold` | Fold | Пас |
| `gui.burmaldaholic.poker.fold.tooltip` | Fold — give up this hand | Фолд — сбросить карты |
| `gui.burmaldaholic.poker.check` | Check | Чек |
| `gui.burmaldaholic.poker.call` | Call %1$s | Колл %1$s |
| `gui.burmaldaholic.poker.bet` | Bet | Бет |
| `gui.burmaldaholic.poker.raise` | Raise | Рейз |
| `gui.burmaldaholic.poker.raise_to` | Raise to %1$s | Рейз до %1$s |
| `gui.burmaldaholic.poker.all_in` | All-in %1$s | Олл-ин %1$s |
| `gui.burmaldaholic.poker.half_pot` | ½ pot | ½ банка |
| `gui.burmaldaholic.poker.three_quarter_pot` | ¾ pot | ¾ банка |
| `gui.burmaldaholic.poker.pot_size` | Pot | Банк |
| `gui.burmaldaholic.poker.pot` | Pot: %1$s | Банк: %1$s |
| `gui.burmaldaholic.poker.side_pot` | Side pot %1$s: %2$s | Побочный банк %1$s: %2$s |
| `gui.burmaldaholic.poker.to_call` | To call: %1$s | Для колла: %1$s |
| `gui.burmaldaholic.poker.stack` | Stack: %1$s | Стек: %1$s |
| `gui.burmaldaholic.poker.blinds` | Blinds %1$s/%2$s | Блайнды %1$s/%2$s |
| `gui.burmaldaholic.poker.button` | Dealer button | Баттон |
| `gui.burmaldaholic.poker.your_cards` | Your cards | Ваши карты |
| `gui.burmaldaholic.poker.board` | Board | Стол |
| `gui.burmaldaholic.poker.preflop` | Preflop | Префлоп |
| `gui.burmaldaholic.poker.flop` | Flop | Флоп |
| `gui.burmaldaholic.poker.turn` | Turn | Тёрн |
| `gui.burmaldaholic.poker.river` | River | Ривер |
| `gui.burmaldaholic.poker.showdown` | Showdown | Вскрытие |
| `gui.burmaldaholic.poker.your_turn` | Your move | Ваш ход |
| `gui.burmaldaholic.poker.waiting_hand` | Waiting for the next hand… | Ждём следующую раздачу… |
| `gui.burmaldaholic.poker.sitting_out` | Sitting out | Пропускает |
| `gui.burmaldaholic.poker.folded` | Folded | Пас |
| `gui.burmaldaholic.poker.all_in_tag` | All-in | Олл-ин |
| `gui.burmaldaholic.poker.show` | Show cards | Показать карты |
| `gui.burmaldaholic.poker.muck` | Muck | Не показывать |
| `gui.burmaldaholic.poker.bots` | Bots | Боты |
| `gui.burmaldaholic.poker.bot.fish` | Fish | Рыба |
| `gui.burmaldaholic.poker.bot.regular` | Regular | Регуляр |
| `gui.burmaldaholic.poker.bot.shark` | Shark | Акула |
| `gui.burmaldaholic.poker.rake_info` | Rake: %1$s%% of the pot, max %2$s. No flop, no rake. | Рейк: %1$s %% банка, не больше %2$s. Нет флопа — нет рейка. |
| `gui.burmaldaholic.poker.hand.high_card` | High card | Старшая карта |
| `gui.burmaldaholic.poker.hand.pair` | One pair | Пара |
| `gui.burmaldaholic.poker.hand.two_pair` | Two pair | Две пары |
| `gui.burmaldaholic.poker.hand.three_of_a_kind` | Three of a kind | Сет |
| `gui.burmaldaholic.poker.hand.straight` | Straight | Стрит |
| `gui.burmaldaholic.poker.hand.flush` | Flush | Флеш |
| `gui.burmaldaholic.poker.hand.full_house` | Full house | Фулл-хаус |
| `gui.burmaldaholic.poker.hand.four_of_a_kind` | Four of a kind | Каре |
| `gui.burmaldaholic.poker.hand.straight_flush` | Straight flush | Стрит-флеш |
| `gui.burmaldaholic.poker.hand.royal_flush` | Royal flush | Роял-флеш |
| `msg.burmaldaholic.poker.action.fold` | %1$s folds | %1$s: пас |
| `msg.burmaldaholic.poker.action.check` | %1$s checks | %1$s: чек |
| `msg.burmaldaholic.poker.action.call` | %1$s calls %2$s | %1$s: колл %2$s |
| `msg.burmaldaholic.poker.action.bet` | %1$s bets %2$s | %1$s: бет %2$s |
| `msg.burmaldaholic.poker.action.raise` | %1$s raises to %2$s | %1$s: рейз до %2$s |
| `msg.burmaldaholic.poker.action.all_in` | %1$s is ALL-IN for %2$s | %1$s: ОЛЛ-ИН на %2$s |
| `msg.burmaldaholic.poker.action.small_blind` | %1$s posts small blind %2$s | %1$s ставит малый блайнд %2$s |
| `msg.burmaldaholic.poker.action.big_blind` | %1$s posts big blind %2$s | %1$s ставит большой блайнд %2$s |
| `msg.burmaldaholic.poker.wins_pot` | %1$s wins %2$s with %3$s | %1$s забирает банк (%2$s) — %3$s |
| `msg.burmaldaholic.poker.wins_uncontested` | %1$s wins %2$s — everyone folded | %1$s забирает банк (%2$s) — все спасовали |
| `msg.burmaldaholic.poker.wins_side_pot` | %1$s wins side pot %2$s with %3$s | %1$s забирает побочный банк (%2$s) — %3$s |
| `msg.burmaldaholic.poker.split_pot` | Split pot: %1$s each | Банк делится: по %1$s |
| `msg.burmaldaholic.poker.timeout` | Time's up — auto %1$s | Время вышло — авто-%1$s |
| `msg.burmaldaholic.poker.sat_out` | You were moved to sitting out after missed turns | Вы долго не ходили — теперь вы пропускаете раздачи |
| `msg.burmaldaholic.poker.removed` | You left the table. %1$s returned to your balance | Вы встали из-за стола. На баланс возвращено: %1$s |
| `msg.burmaldaholic.poker.bot_joins` | %1$s (%2$s) joins the table | За стол садится %1$s (%2$s) |
| `msg.burmaldaholic.poker.bot_busts` | %1$s is out of chips and leaves | У игрока %1$s кончились фишки — место освобождается |
| `msg.burmaldaholic.poker.new_hand` | Hand #%1$s — blinds %2$s/%3$s | Раздача №%1$s — блайнды %2$s/%3$s |
| `msg.burmaldaholic.poker.leaving_after_hand` | You'll stand up when this hand ends | Вы встанете из-за стола после этой раздачи |

### Presentation (animation wave, `animation/cards.md` §11)

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.poker.tag.check` | Check | Чек |
| `gui.burmaldaholic.poker.tag.call` | Call %1$s | Колл %1$s |
| `gui.burmaldaholic.poker.tag.bet` | Bet %1$s | Бет %1$s |
| `gui.burmaldaholic.poker.tag.raise` | Raise to %1$s | Рейз до %1$s |
| `gui.burmaldaholic.poker.tag.fold` | Fold | Пас |
| `gui.burmaldaholic.poker.tag.small_blind` | SB %1$s | МБ %1$s |
| `gui.burmaldaholic.poker.tag.big_blind` | BB %1$s | ББ %1$s |
| `gui.burmaldaholic.poker.tag.shows` | Shows | Открывает |
| `gui.burmaldaholic.poker.tag.mucks` | Mucks | Сбрасывает |
| `gui.burmaldaholic.poker.fx.all_in_runout` | All in — running the board | Олл-ин — открываем стол |
| `gui.burmaldaholic.poker.fx.big_pot` | Big pot! | Крупный банк! |
| `gui.burmaldaholic.poker.fx.monster_pot` | Monster pot! | Огромный банк! |
| `gui.burmaldaholic.poker.fx.best_hand` | Best hand: %1$s | Лучшая рука: %1$s |
| `gui.burmaldaholic.poker.fx.wins` | %1$s wins %2$s | %1$s забирает %2$s |
| `gui.burmaldaholic.poker.fx.split` | Split pot: %1$s each | Банк делится: по %1$s |
| `gui.burmaldaholic.poker.fx.returned` | Returned %1$s | Возврат: %1$s |
| `gui.burmaldaholic.poker.actionbar` | Board %1$s · Pot %2$s | Стол %1$s · Банк %2$s |

---

## slots

Rows follow `SLOTS.md` §13 (slots v2). Argument notes: `machine` = nested `gui.burmaldaholic.slots.machine.*`,
`symbol` = nested `gui.burmaldaholic.slots.symbol.*`, `tier` = nested `gui.burmaldaholic.slots.jackpot.tier.*`,
`mult` = a formatted multiplier without "×".

### Machines and symbols (`SLOTS.md` §13.2)

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.slots.machine.overworld` | Overworld Riches | Богатства Верхнего мира |
| `gui.burmaldaholic.slots.machine.nether` | Nether Inferno | Пекло Незера |
| `gui.burmaldaholic.slots.machine.end` | End Void | Пустота Края |
| `gui.burmaldaholic.slots.symbol.totem` | Totem (Wild) | Тотем (вайлд) |
| `gui.burmaldaholic.slots.symbol.compass` | Compass | Компас |
| `gui.burmaldaholic.slots.symbol.chest` | Treasure Chest | Сундук с сокровищами |
| `gui.burmaldaholic.slots.symbol.diamond` | Diamond | Алмаз |
| `gui.burmaldaholic.slots.symbol.emerald` | Emerald | Изумруд |
| `gui.burmaldaholic.slots.symbol.gold_ingot` | Gold Ingot | Золотой слиток |
| `gui.burmaldaholic.slots.symbol.iron_ingot` | Iron Ingot | Железный слиток |
| `gui.burmaldaholic.slots.symbol.apple` | Apple | Яблоко |
| `gui.burmaldaholic.slots.symbol.carrot` | Carrot | Морковь |
| `gui.burmaldaholic.slots.symbol.wheat` | Wheat | Пшеница |
| `gui.burmaldaholic.slots.symbol.sweet_berries` | Sweet Berries | Сладкие ягоды |
| `gui.burmaldaholic.slots.symbol.lava_bucket` | Lava Bucket (Wild) | Ведро лавы (вайлд) |
| `gui.burmaldaholic.slots.symbol.ghast_tear` | Ghast Tear | Слеза гаста |
| `gui.burmaldaholic.slots.symbol.piglin_coin` | Piglin Coin | Монета пиглинов |
| `gui.burmaldaholic.slots.symbol.wither_skull` | Wither Skeleton Skull | Череп скелета-иссушителя |
| `gui.burmaldaholic.slots.symbol.blaze_rod` | Blaze Rod | Огненный стержень |
| `gui.burmaldaholic.slots.symbol.magma_cream` | Magma Cream | Сгусток магмы |
| `gui.burmaldaholic.slots.symbol.quartz` | Nether Quartz | Кварц Незера |
| `gui.burmaldaholic.slots.symbol.nether_wart` | Nether Wart | Незерский нарост |
| `gui.burmaldaholic.slots.symbol.crimson_fungus` | Crimson Fungus | Багровый гриб |
| `gui.burmaldaholic.slots.symbol.warped_fungus` | Warped Fungus | Искажённый гриб |
| `gui.burmaldaholic.slots.symbol.glowstone` | Glowstone Dust | Светокаменная пыль |
| `gui.burmaldaholic.slots.symbol.dragon_egg` | Dragon Egg (Wild) | Яйцо дракона (вайлд) |
| `gui.burmaldaholic.slots.symbol.ender_eye` | Eye of Ender | Око Края |
| `gui.burmaldaholic.slots.symbol.end_crystal` | End Crystal | Кристалл Края |
| `gui.burmaldaholic.slots.symbol.dragon_head` | Dragon Head | Голова дракона |
| `gui.burmaldaholic.slots.symbol.elytra` | Elytra | Элитры |
| `gui.burmaldaholic.slots.symbol.shulker_shell` | Shulker Shell | Панцирь шалкера |
| `gui.burmaldaholic.slots.symbol.chorus_fruit` | Chorus Fruit | Плод коруса |
| `gui.burmaldaholic.slots.symbol.ender_pearl` | Ender Pearl | Жемчуг Края |
| `gui.burmaldaholic.slots.symbol.purpur` | Purpur Block | Пурпурный блок |
| `gui.burmaldaholic.slots.symbol.end_rod` | End Rod | Стержень Края |
| `gui.burmaldaholic.slots.symbol.end_stone` | End Stone | Эндерняк |
| `gui.burmaldaholic.slots.symbol.creeper` | Creeper | Крипер |

### Machine screen / form (`SLOTS.md` §13.3; argument notes there)

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.slots.spin` | Spin (%1$s) | Крутить (%1$s) |
| `gui.burmaldaholic.slots.stop` | Stop | Стоп |
| `gui.burmaldaholic.slots.skip` | Skip | Пропустить |
| `gui.burmaldaholic.slots.bet` | Bet: %1$s | Ставка: %1$s |
| `gui.burmaldaholic.slots.bet_down` | Lower bet | Меньше |
| `gui.burmaldaholic.slots.bet_up` | Raise bet | Больше |
| `gui.burmaldaholic.slots.ways` | 243 ways | 243 способа |
| `gui.burmaldaholic.slots.ways.p1` | %1$s way | %1$s способ |
| `gui.burmaldaholic.slots.ways.p21` | %1$s ways | %1$s способ |
| `gui.burmaldaholic.slots.ways.p2` | %1$s ways | %1$s способа |
| `gui.burmaldaholic.slots.ways.p5` | %1$s ways | %1$s способов |
| `gui.burmaldaholic.slots.last_win` | Last win: %1$s | Прошлый выигрыш: %1$s |
| `gui.burmaldaholic.slots.no_win` | No win this time | В этот раз мимо |
| `gui.burmaldaholic.slots.spin_total` | Spin result: %1$s | Итог вращения: %1$s |
| `gui.burmaldaholic.slots.symbol_win` | %1$s ×%2$s · %3$s · %4$s | %1$s ×%2$s · %3$s · %4$s |
| `gui.burmaldaholic.slots.scatter_win` | Scatters: %1$s · %2$s | Скаттеры: %1$s · %2$s |
| `gui.burmaldaholic.slots.playing` | Now playing: %1$s | Сейчас играет: %1$s |
| `gui.burmaldaholic.slots.turbo` | Turbo | Турбо |
| `gui.burmaldaholic.slots.out_of_order` | Out of order | Не работает |
| `gui.burmaldaholic.slots.auto` | Auto… | Авто… |
| `gui.burmaldaholic.slots.stop_auto` | Stop | Стоп |
| `gui.burmaldaholic.slots.auto.title` | Autoplay | Автоигра |
| `gui.burmaldaholic.slots.auto.count` | Spins | Вращений |
| `gui.burmaldaholic.slots.auto.loss_limit` | Stop if I lose (× bet) | Стоп при проигрыше (× ставки) |
| `gui.burmaldaholic.slots.auto.stop_feature` | Stop on a feature | Стоп на бонусе |
| `gui.burmaldaholic.slots.auto.stop_win` | Stop on a win of at least | Стоп при выигрыше от |
| `gui.burmaldaholic.slots.auto.off` | Off | Выкл. |
| `gui.burmaldaholic.slots.auto.start` | Start autoplay | Запустить |
| `gui.burmaldaholic.slots.auto_left` | Auto: %1$s left | Авто: осталось %1$s |
| `gui.burmaldaholic.slots.auto_summary` | Spins %1$s: bet %2$s, won %3$s | Вращений %1$s: поставлено %2$s, выиграно %3$s |
| `gui.burmaldaholic.slots.auto_stopped_big_win` | Autoplay stopped: big win! | Автоигра остановлена: крупный выигрыш! |
| `gui.burmaldaholic.slots.auto_stopped_funds` | Autoplay stopped: not enough chips | Автоигра остановлена: не хватает фишек |
| `gui.burmaldaholic.slots.auto_stopped_feature` | Autoplay stopped: bonus! | Автоигра остановлена: бонус! |
| `gui.burmaldaholic.slots.auto_stopped_loss` | Autoplay stopped: loss limit reached | Автоигра остановлена: достигнут лимит проигрыша |
| `gui.burmaldaholic.slots.auto_stopped_jackpot` | Autoplay stopped: jackpot! | Автоигра остановлена: джекпот! |
| `gui.burmaldaholic.slots.error.loss_limit_required` | Choose a loss limit first | Сначала выберите лимит проигрыша |
| `gui.burmaldaholic.slots.error.bet_unavailable` | That bet is not available on this machine | Такой ставки на этом автомате нет |
| `gui.burmaldaholic.slots.error.buy_disabled` | Buying bonuses is off here | Покупка бонусов здесь отключена |
| `gui.burmaldaholic.slots.error.buy_limit` | This purchase is above your VIP limit | Покупка превышает ваш ВИП-лимит |
| `gui.burmaldaholic.slots.error.autoplay_disabled` | Autoplay is off here | Автоигра здесь отключена |

### Features, jackpots, buy feature (`SLOTS.md` §13.4)

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.slots.fs.title` | FREE SPINS | БЕСПЛАТНЫЕ ВРАЩЕНИЯ |
| `gui.burmaldaholic.slots.fs.name.overworld` | Night Watch | Ночной дозор |
| `gui.burmaldaholic.slots.fs.name.nether` | Inferno Spins | Адские вращения |
| `gui.burmaldaholic.slots.fs.name.end` | Void Walker | Странник пустоты |
| `gui.burmaldaholic.slots.fs.awarded` | Free spins: %1$s! | Бесплатных вращений: %1$s! |
| `gui.burmaldaholic.slots.fs.retrigger` | More spins: +%1$s! | Ещё вращений: +%1$s! |
| `gui.burmaldaholic.slots.fs.left` | Spin %1$s of %2$s | Вращение %1$s из %2$s |
| `gui.burmaldaholic.slots.fs.multiplier` | Multiplier ×%1$s | Множитель ×%1$s |
| `gui.burmaldaholic.slots.fs.total` | Bonus win: %1$s | Выигрыш в бонусе: %1$s |
| `gui.burmaldaholic.slots.fs.end` | FREE SPINS WIN %1$s | ИТОГ ВРАЩЕНИЙ: %1$s |
| `gui.burmaldaholic.slots.tumble.mult` | Tumble ×%1$s | Обвал ×%1$s |
| `gui.burmaldaholic.slots.tumble.count` | Tumbles: %1$s | Обвалов: %1$s |
| `gui.burmaldaholic.slots.sticky` | Sticky reels: %1$s/3 | Липкие барабаны: %1$s/3 |
| `gui.burmaldaholic.slots.bonus.pick` | Treasure Hunt | Охота за сокровищами |
| `gui.burmaldaholic.slots.bonus.hold` | Piglin's Hoard | Клад пиглинов |
| `gui.burmaldaholic.slots.bonus.wheel` | Dragon Wheel | Колесо дракона |
| `gui.burmaldaholic.slots.bonus.total` | Bonus total: %1$s | Итог бонуса: %1$s |
| `gui.burmaldaholic.slots.pick.hint` | Open chests until a Creeper jumps out | Открывайте сундуки, пока не выскочит крипер |
| `gui.burmaldaholic.slots.pick.open` | Open a chest | Открыть сундук |
| `gui.burmaldaholic.slots.pick.open_all` | Open all | Открыть все |
| `gui.burmaldaholic.slots.pick.prize` | Chest: %1$s | В сундуке: %1$s |
| `gui.burmaldaholic.slots.pick.creeper` | A Creeper! The hunt is over. | Крипер! Охота окончена. |
| `gui.burmaldaholic.slots.pick.opened` | Chests opened: %1$s | Открыто сундуков: %1$s |
| `gui.burmaldaholic.slots.hold.respins` | Respins: %1$s | Повторов: %1$s |
| `gui.burmaldaholic.slots.hold.coins` | Coins: %1$s/15 | Монеты: %1$s/15 |
| `gui.burmaldaholic.slots.hold.reset` | New coin! Back to 3 respins | Новая монета! Снова 3 повтора |
| `gui.burmaldaholic.slots.hold.full` | ALL 15 FILLED! | ВСЕ 15 ЗАПОЛНЕНЫ! |
| `gui.burmaldaholic.slots.wheel.spin` | Spin the wheel | Крутить колесо |
| `gui.burmaldaholic.slots.wheel.up` | UP! On to the next ring | ВВЕРХ! На следующее кольцо |
| `gui.burmaldaholic.slots.wheel.ring.outer` | End Stone ring | Кольцо эндерняка |
| `gui.burmaldaholic.slots.wheel.ring.middle` | Purpur ring | Пурпурное кольцо |
| `gui.burmaldaholic.slots.wheel.ring.core` | Dragon Core | Сердце дракона |
| `gui.burmaldaholic.slots.jackpot.tier.mini` | MINI | МИНИ |
| `gui.burmaldaholic.slots.jackpot.tier.minor` | MINOR | МИНОР |
| `gui.burmaldaholic.slots.jackpot.tier.major` | MAJOR | МАЖОР |
| `gui.burmaldaholic.slots.jackpot.tier.grand` | GRAND | ГРАНД |
| `gui.burmaldaholic.slots.jackpot.meter` | %1$s %2$s | %1$s %2$s |
| `gui.burmaldaholic.slots.buy.button` | Buy bonus (%1$s) | Купить бонус (%1$s) |
| `gui.burmaldaholic.slots.buy.confirm_title` | Buy %1$s? | Купить «%1$s»? |
| `gui.burmaldaholic.slots.buy.confirm_body` | Pay %1$s to start %2$s with %3$s free spins. Return to player: %4$s%%. | Заплатить %1$s и начать «%2$s»: бесплатных вращений — %3$s. Возврат игроку: %4$s %%. |
| `gui.burmaldaholic.slots.buy.confirm` | Buy | Купить |

### Paytable and help (`SLOTS.md` §13.5)

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.slots.paytable.title` | Paytable | Таблица выплат |
| `gui.burmaldaholic.slots.paytable.per_way` | Wins pay for every way: matching symbols on adjacent reels from the left, in any row | Выплата за каждый способ: одинаковые символы на соседних барабанах слева направо, в любом ряду |
| `gui.burmaldaholic.slots.paytable.row` | %1$s: 3 → %2$s · 4 → %3$s · 5 → %4$s | %1$s: 3 → %2$s · 4 → %3$s · 5 → %4$s |
| `gui.burmaldaholic.slots.paytable.wild` | %1$s appears on reels 2–4 and replaces every symbol except %2$s and %3$s | %1$s появляется на барабанах 2–4 и заменяет все символы, кроме «%2$s» и «%3$s» |
| `gui.burmaldaholic.slots.paytable.scatter` | %1$s anywhere: 3 → %2$s · 4 → %3$s · 5 → %4$s | %1$s в любом месте: 3 → %2$s · 4 → %3$s · 5 → %4$s |
| `gui.burmaldaholic.slots.paytable.rtp` | Return to player: %1$s%% | Возврат игроку: %1$s %% |
| `gui.burmaldaholic.slots.paytable.max_win` | Max win per spin: ×%1$s the bet | Максимальный выигрыш за вращение: ×%1$s от ставки |
| `gui.burmaldaholic.slots.paytable.jackpot_share` | Full jackpot at a bet of %1$s or more; smaller bets win a share | Весь джекпот — при ставке от %1$s; меньшая ставка даёт долю |
| `gui.burmaldaholic.slots.paytable.my_stats` | My stats | Моя статистика |
| `gui.burmaldaholic.slots.stats.line` | Spins %1$s · wagered %2$s · returned %3$s · best ×%4$s | Вращений %1$s · поставлено %2$s · возвращено %3$s · лучший ×%4$s |
| `gui.burmaldaholic.slots.help.fs` | 3, 4 or 5 %1$s anywhere: %2$s, %3$s or %4$s free spins. 3 more during free spins: +%5$s. | 3, 4 или 5 символов «%1$s» в любом месте: %2$s, %3$s или %4$s бесплатных вращений. Ещё 3 во время вращений: +%5$s. |
| `gui.burmaldaholic.slots.help.fs_mult` | During free spins all wins are multiplied by %1$s | Во время бесплатных вращений все выигрыши умножаются на %1$s |
| `gui.burmaldaholic.slots.help.tumble` | Winning symbols burn away and new ones fall in. Each tumble raises the multiplier: %1$s. In free spins: %2$s. | Выигрышные символы сгорают, сверху падают новые. Каждый обвал повышает множитель: %1$s. В бесплатных вращениях: %2$s. |
| `gui.burmaldaholic.slots.help.sticky` | In Void Walker every %1$s fills its reel and stays until the feature ends | В «Страннике пустоты» каждый символ «%1$s» заполняет свой барабан и остаётся до конца бонуса |
| `gui.burmaldaholic.slots.help.pick` | 3 %1$s on reels 1, 3 and 5 start the Treasure Hunt: open chests for coins and jackpot gems until a Creeper appears | 3 символа «%1$s» на барабанах 1, 3 и 5 запускают охоту за сокровищами: открывайте сундуки с монетами и джекпотами, пока не появится крипер |
| `gui.burmaldaholic.slots.help.pick_fair` | Prizes are drawn when the hunt starts. Which chest you open does not change your chances. | Призы разыгрываются в начале охоты. Выбор сундука не влияет на шансы. |
| `gui.burmaldaholic.slots.help.hold` | 6 or more %1$s start Piglin's Hoard: coins lock, you get 3 respins and every new coin resets them. Fill all 15 cells for the Grand jackpot. | 6 и больше символов «%1$s» запускают клад пиглинов: монеты фиксируются, даются 3 повтора, каждая новая монета их обновляет. Заполните все 15 ячеек — джекпот «Гранд». |
| `gui.burmaldaholic.slots.help.wheel` | %1$s on reels 2, 3 and 4 spin the Dragon Wheel. UP takes you to a richer ring; the Grand waits in the Dragon Core. | Символы «%1$s» на барабанах 2, 3 и 4 запускают колесо дракона. «Вверх» ведёт на кольцо побогаче; «Гранд» ждёт в сердце дракона. |
| `gui.burmaldaholic.slots.help.anticipation` | Reels slow down only when the symbols you already see could complete a feature | Барабаны замедляются, только если уже видимые символы могут запустить бонус |

### Messages (`SLOTS.md` §13.6)

| Key | EN | RU |
|-----|----|----|
| `msg.burmaldaholic.slots.jackpot_self` | %1$s JACKPOT! You won %2$s! | ДЖЕКПОТ %1$s! Ваш выигрыш: %2$s! |
| `msg.burmaldaholic.slots.jackpot_broadcast` | JACKPOT! %1$s hits the %2$s: %3$s on %4$s! | ДЖЕКПОТ! %1$s — %2$s: %3$s на автомате «%4$s»! |
| `msg.burmaldaholic.slots.jackpot_pool` | %1$s: Grand jackpot %2$s | %1$s: джекпот «Гранд» %2$s |
| `msg.burmaldaholic.slots.big_win_broadcast` | Epic win: %1$s, ×%2$s on %3$s! | Эпический выигрыш: %1$s, ×%2$s на автомате «%3$s»! |
| `msg.burmaldaholic.slots.golden_scatters` | Five scatters! Golden Hour strikes! | Пять скаттеров! Бьёт «Золотой час»! |
| `msg.burmaldaholic.slots.golden_scatters_cooldown` | Five scatters! Golden Hour is recharging, but the spins are yours. | Пять скаттеров! «Золотой час» перезаряжается, но вращения ваши. |
| `msg.burmaldaholic.slots.creeper_friends` | That creeper brought friends! | Этот крипер привёл друзей! |
| `msg.burmaldaholic.slots.wither_skulls` | Five Wither Skulls. The Nether wants them back. | Пять черепов иссушителя. Незер хочет их обратно. |
| `msg.burmaldaholic.slots.tumble_chain` | %1$s tumbles in a row! The fire likes you. | Обвалов подряд: %1$s! Огонь к вам благосклонен. |
| `msg.burmaldaholic.slots.dragon_fling` | Five Dragon Heads! The dragon flings you away… gently. | Пять голов дракона! Дракон отшвыривает вас… бережно. |
| `msg.burmaldaholic.slots.void_walker` | All three reels are Dragon Eggs! | Все три барабана — яйца дракона! |
| `msg.burmaldaholic.slots.rtp_ok` | All slot machines pay back less than 99%% | Все автоматы возвращают меньше 99 %% |
| `msg.burmaldaholic.slots.jackpots_migrated` | Slot jackpots moved to the new machines: %1$s | Джекпоты перенесены на новые автоматы: %1$s |

### Presentation (animation wave, `animation/slots.md` §10)

| Key | EN | RU |
|-----|----|----|
| `entity.burmaldaholic.slot_reels` | Slot machine reels | Барабаны автомата |
| `gui.burmaldaholic.slots.fx.meter_won` | WON! | ВЫИГРАН! |
| `gui.burmaldaholic.slots.fx.times` | ×%1$s | ×%1$s |
| `gui.burmaldaholic.slots.fx.wheel_up_wedge` | UP | ВВЕРХ |
| `gui.burmaldaholic.slots.fx.step_win` | +%1$s ×%2$s | +%1$s ×%2$s |
| `gui.burmaldaholic.slots.fx.cabinet_view` | Watch the machine while spinning | Смотреть на автомат при вращении |
| `gui.burmaldaholic.slots.fx.cabinet_view.tooltip` | Closes the window during a spin and turns the camera to the machine | Во время вращения закрывает окно и поворачивает камеру к автомату |

### Sound subtitles (`SLOTS.md` §13.9; `fs_music.<machine>` events share `slots.fs_music`)

| Key | EN | RU |
|-----|----|----|
| `subtitles.burmaldaholic.slots.spin_loop` | Reels whirr | Жужжат барабаны |
| `subtitles.burmaldaholic.slots.reel_stop` | Reel stops | Останавливается барабан |
| `subtitles.burmaldaholic.slots.scatter_land` | Scatter lands | Выпадает скаттер |
| `subtitles.burmaldaholic.slots.bonus_land` | Bonus symbol lands | Выпадает бонусный символ |
| `subtitles.burmaldaholic.slots.anticipation` | Tension rises | Напряжение растёт |
| `subtitles.burmaldaholic.slots.returned` | Coins trickle back | Возвращаются монеты |
| `subtitles.burmaldaholic.slots.win_small` | Small win chimes | Звенит выигрыш |
| `subtitles.burmaldaholic.slots.win_nice` | Nice win chimes | Звенит хороший выигрыш |
| `subtitles.burmaldaholic.slots.big_win` | Big win fanfare | Фанфары крупного выигрыша |
| `subtitles.burmaldaholic.slots.mega_win` | Mega win fanfare | Фанфары мегавыигрыша |
| `subtitles.burmaldaholic.slots.epic_win` | Epic win fanfare | Фанфары эпического выигрыша |
| `subtitles.burmaldaholic.slots.max_win` | Max win fanfare | Фанфары максимального выигрыша |
| `subtitles.burmaldaholic.slots.rollup_tick` | Counter ticks | Щёлкает счётчик |
| `subtitles.burmaldaholic.slots.rollup_end` | Counter stops | Счётчик останавливается |
| `subtitles.burmaldaholic.slots.fs_intro` | Free spins begin | Начинаются бесплатные вращения |
| `subtitles.burmaldaholic.slots.fs_outro` | Free spins end | Бесплатные вращения окончены |
| `subtitles.burmaldaholic.slots.fs_music` | Bonus music plays | Играет бонусная музыка |
| `subtitles.burmaldaholic.slots.wild_expand` | Wild expands | Вайлд растёт |
| `subtitles.burmaldaholic.slots.wild_stick` | Wild locks in place | Вайлд закрепляется |
| `subtitles.burmaldaholic.slots.tumble` | Symbols burn away | Сгорают символы |
| `subtitles.burmaldaholic.slots.mult_up` | Multiplier rises | Растёт множитель |
| `subtitles.burmaldaholic.slots.chest_open` | Chest opens | Открывается сундук |
| `subtitles.burmaldaholic.slots.creeper_hiss` | Creeper hisses | Шипит крипер |
| `subtitles.burmaldaholic.slots.coin_land` | Coin clinks | Звякает монета |
| `subtitles.burmaldaholic.slots.respin_reset` | Respins reset | Повторы обновились |
| `subtitles.burmaldaholic.slots.wheel_tick` | Wheel clicks | Щёлкает колесо |
| `subtitles.burmaldaholic.slots.wheel_up` | Wheel rises | Колесо поднимается |

---

### Slots v2 tier words (early import of `SLOTS.md` §13.3 / §13.4 rows used by the shared celebration API)

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.slots.win` | Win %1$s | Выигрыш %1$s |
| `gui.burmaldaholic.slots.returned` | Returned %1$s | Возвращено %1$s |
| `gui.burmaldaholic.slots.tier.nice` | NICE WIN! | НЕПЛОХО! |
| `gui.burmaldaholic.slots.tier.big` | BIG WIN! | КРУПНЫЙ ВЫИГРЫШ! |
| `gui.burmaldaholic.slots.tier.mega` | MEGA WIN! | МЕГАВЫИГРЫШ! |
| `gui.burmaldaholic.slots.tier.epic` | EPIC WIN! | ЭПИЧЕСКИЙ ВЫИГРЫШ! |
| `gui.burmaldaholic.slots.max_win` | MAX WIN! | МАКСИМАЛЬНЫЙ ВЫИГРЫШ! |
| `gui.burmaldaholic.slots.jackpot.won` | %1$s JACKPOT! | ДЖЕКПОТ %1$s! |

## roulette

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.roulette.title` | European Roulette | Европейская рулетка |
| `gui.burmaldaholic.roulette.title_high_roller` | Roulette — High Rollers | Рулетка — хайроллеры |
| `gui.burmaldaholic.roulette.place_bets` | Place your bets | Делайте ваши ставки |
| `gui.burmaldaholic.roulette.no_more_bets` | No more bets | Ставок больше нет |
| `gui.burmaldaholic.roulette.add_bet` | Add bet | Ещё ставка |
| `gui.burmaldaholic.roulette.clear_bets` | Clear bets | Убрать ставки |
| `gui.burmaldaholic.roulette.your_bets` | Your bets | Ваши ставки |
| `gui.burmaldaholic.roulette.no_bets` | No bets yet | Ставок пока нет |
| `gui.burmaldaholic.roulette.inside` | Inside bets | Внутренние ставки |
| `gui.burmaldaholic.roulette.outside` | Outside bets | Внешние ставки |
| `gui.burmaldaholic.roulette.position` | Position | Позиция |
| `gui.burmaldaholic.roulette.bet.straight` | Straight (35:1) | На номер (35:1) |
| `gui.burmaldaholic.roulette.bet.split` | Split (17:1) | Сплит (17:1) |
| `gui.burmaldaholic.roulette.bet.street` | Street (11:1) | Улица (11:1) |
| `gui.burmaldaholic.roulette.bet.trio` | Trio (11:1) | Трио (11:1) |
| `gui.burmaldaholic.roulette.bet.corner` | Corner (8:1) | Угол (8:1) |
| `gui.burmaldaholic.roulette.bet.first_four` | First Four (8:1) | Первые четыре (8:1) |
| `gui.burmaldaholic.roulette.bet.six_line` | Six Line (5:1) | Линия (5:1) |
| `gui.burmaldaholic.roulette.bet.dozen` | Dozen (2:1) | Дюжина (2:1) |
| `gui.burmaldaholic.roulette.bet.column` | Column (2:1) | Колонна (2:1) |
| `gui.burmaldaholic.roulette.bet.red` | Red (1:1) | Красное (1:1) |
| `gui.burmaldaholic.roulette.bet.black` | Black (1:1) | Чёрное (1:1) |
| `gui.burmaldaholic.roulette.bet.odd` | Odd (1:1) | Нечёт (1:1) |
| `gui.burmaldaholic.roulette.bet.even` | Even (1:1) | Чёт (1:1) |
| `gui.burmaldaholic.roulette.bet.low` | 1–18 (1:1) | 1–18, малые (1:1) |
| `gui.burmaldaholic.roulette.bet.high` | 19–36 (1:1) | 19–36, большие (1:1) |
| `gui.burmaldaholic.roulette.desc.straight` | Straight %1$s | На номер %1$s |
| `gui.burmaldaholic.roulette.desc.split` | Split %1$s | Сплит %1$s |
| `gui.burmaldaholic.roulette.desc.street` | Street %1$s | Улица %1$s |
| `gui.burmaldaholic.roulette.desc.trio` | Trio %1$s | Трио %1$s |
| `gui.burmaldaholic.roulette.desc.corner` | Corner %1$s | Угол %1$s |
| `gui.burmaldaholic.roulette.desc.six_line` | Six Line %1$s | Линия %1$s |
| `gui.burmaldaholic.roulette.desc.dozen.1` | 1st dozen (1–12) | Первая дюжина (1–12) |
| `gui.burmaldaholic.roulette.desc.dozen.2` | 2nd dozen (13–24) | Вторая дюжина (13–24) |
| `gui.burmaldaholic.roulette.desc.dozen.3` | 3rd dozen (25–36) | Третья дюжина (25–36) |
| `gui.burmaldaholic.roulette.desc.column.1` | 1st column | Первая колонна |
| `gui.burmaldaholic.roulette.desc.column.2` | 2nd column | Вторая колонна |
| `gui.burmaldaholic.roulette.desc.column.3` | 3rd column | Третья колонна |
| `gui.burmaldaholic.roulette.desc.with_amount` | %1$s — %2$s | %1$s — %2$s |
| `gui.burmaldaholic.roulette.color.red` | Red | Красное |
| `gui.burmaldaholic.roulette.color.black` | Black | Чёрное |
| `gui.burmaldaholic.roulette.color.green` | Zero | Зеро |
| `gui.burmaldaholic.roulette.result` | %1$s %2$s | %1$s, %2$s |
| `gui.burmaldaholic.roulette.result_win` | %1$s %2$s — you win %3$s | %1$s, %2$s — ваш выигрыш %3$s |
| `gui.burmaldaholic.roulette.result_lose` | %1$s %2$s — not this time | %1$s, %2$s — не в этот раз |
| `gui.burmaldaholic.roulette.la_partage` | Zero! Even-money bets lose only half (la partage) | Зеро! Ставки 1:1 теряют только половину (ла партаж) |
| `gui.burmaldaholic.roulette.rules.1` | Single zero · all bets have a 2.7%% house edge | Одно зеро · преимущество заведения 2,7 %% на любой ставке |
| `gui.burmaldaholic.roulette.rules.2` | On zero, all outside bets lose | При выпадении зеро внешние ставки проигрывают |
| `msg.burmaldaholic.roulette.zero_hero` | Zero! The croupier nods respectfully. | Зеро! Крупье уважительно кивает. |
| `msg.burmaldaholic.roulette.spinning` | The ball is spinning… | Шарик катится… |
| `gui.burmaldaholic.roulette.error.inside_max` | Inside bets are limited to %1$s per position | Внутренняя ставка — не больше %1$s на позицию |
| `gui.burmaldaholic.roulette.error.total_max` | Your bets per spin are limited to %1$s | Все ставки на один спин — не больше %1$s |
| `gui.burmaldaholic.roulette.error.min_total` | At this table, bet at least %1$s per spin | За этим столом ставка на спин — от %1$s |
| `gui.burmaldaholic.roulette.ready_count` | Ready: %1$s of %2$s | Готовы: %1$s из %2$s |
| `gui.burmaldaholic.roulette.short.odd` | Odd | Нечёт |
| `gui.burmaldaholic.roulette.short.even` | Even | Чёт |

### Presentation (animation wave, `animation/tables.md` §6)

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.roulette.fx.straight_up` | Straight up! | Прямое попадание! |
| `gui.burmaldaholic.roulette.fx.last_bets` | Last bets! | Последние ставки! |
| `gui.burmaldaholic.roulette.fx.bet_placed` | %1$s on %2$s | %1$s на «%2$s» |

### Table redesign (lane J-L6, `docs/design/visual/tables.md` §10: felt labels, racetrack, icon tooltips)

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.roulette.layout.even` | EVEN | ЧЁТ |
| `gui.burmaldaholic.roulette.layout.odd` | ODD | НЕЧЕТ |
| `gui.burmaldaholic.roulette.racetrack.tier` | Tier | Тьер |
| `gui.burmaldaholic.roulette.racetrack.orphelins` | Orphelins | Орфелен |
| `gui.burmaldaholic.roulette.racetrack.voisins` | Voisins | Вуазен |
| `gui.burmaldaholic.roulette.racetrack.zero` | Zero | Зеро |
| `gui.burmaldaholic.roulette.racetrack.toggle` | Racetrack | Трек |
| `gui.burmaldaholic.roulette.racetrack.neighbours` | Neighbours of %1$s | Соседи %1$s |
| `gui.burmaldaholic.roulette.button.undo` | Undo last chip | Отменить фишку |
| `gui.burmaldaholic.roulette.button.double` | Double all bets | Удвоить ставки |
| `gui.burmaldaholic.roulette.fx.straight_up_win` | Straight up! +%1$s | Прямое попадание! +%1$s |

---

## craps

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.craps.title` | Craps | Крэпс |
| `gui.burmaldaholic.craps.pass` | Pass Line | Пасс-лайн |
| `gui.burmaldaholic.craps.dont_pass` | Don't Pass | Не-пасс |
| `gui.burmaldaholic.craps.come` | Come | Кам |
| `gui.burmaldaholic.craps.dont_come` | Don't Come | Не-кам |
| `gui.burmaldaholic.craps.field` | Field | Филд |
| `gui.burmaldaholic.craps.odds` | Odds | Коэффициент |
| `gui.burmaldaholic.craps.odds_on` | Odds on %1$s | Коэффициент на %1$s |
| `gui.burmaldaholic.craps.roll` | Roll the dice | Бросить кости |
| `gui.burmaldaholic.craps.shooter` | Shooter: %1$s | Бросает: %1$s |
| `gui.burmaldaholic.craps.you_shoot` | You're the shooter | Бросаете вы |
| `gui.burmaldaholic.craps.point_on` | Point: %1$s | Поинт: %1$s |
| `gui.burmaldaholic.craps.point_off` | Come-out roll | Первый бросок |
| `gui.burmaldaholic.craps.last_roll` | Last roll: %1$s + %2$s = %3$s | Последний бросок: %1$s + %2$s = %3$s |
| `gui.burmaldaholic.craps.come_point` | Come bet on %1$s | Кам-ставка на %1$s |
| `gui.burmaldaholic.craps.odds_multiple` | Odds must be a multiple of %1$s | Ставка на коэффициент должна быть кратна %1$s |
| `gui.burmaldaholic.craps.odds_max` | Max odds: %1$s | Макс. коэффициент: %1$s |
| `gui.burmaldaholic.craps.line_only_come_out` | Line bets only on the come-out roll | Ставки на линию — только на первом броске |
| `gui.burmaldaholic.craps.need_line_bet` | The shooter needs a Pass or Don't Pass bet | Бросающему нужна ставка на пасс-лайн или не-пасс |
| `gui.burmaldaholic.craps.rules.1` | Pass: 7 or 11 wins, 2, 3, 12 loses. Otherwise hit the point before a 7. | Пасс-лайн: 7 или 11 — победа, 2, 3, 12 — проигрыш. Иначе нужно выбросить поинт раньше семёрки. |
| `gui.burmaldaholic.craps.rules.2` | Field: 3, 4, 9, 10, 11 pay 1:1 · 2 pays 2:1 · 12 pays 3:1 | Филд: 3, 4, 9, 10, 11 платят 1:1 · 2 платит 2:1 · 12 платит 3:1 |
| `gui.burmaldaholic.craps.rules.3` | Odds pay true odds with no house edge: up to 3-4-5× | Коэффициент платит по честным шансам без преимущества заведения: до 3-4-5× |
| `msg.burmaldaholic.craps.natural` | %1$s — natural! Pass wins | %1$s — натуральный бросок! Пасс-лайн выигрывает |
| `msg.burmaldaholic.craps.craps` | %1$s — craps! Pass loses | %1$s — крэпс! Пасс-лайн проигрывает |
| `msg.burmaldaholic.craps.bar_12` | 12 — Don't Pass pushes | 12 — ставка не-пасс остаётся при своих |
| `msg.burmaldaholic.craps.point_set` | The point is %1$s | Поинт — %1$s |
| `msg.burmaldaholic.craps.point_made` | %1$s — point made! Pass wins | %1$s — поинт выпал! Пасс-лайн выигрывает |
| `msg.burmaldaholic.craps.seven_out` | Seven out! Next shooter | Семёрка — серия окончена! Кости переходят дальше |
| `msg.burmaldaholic.craps.new_shooter` | New shooter: %1$s | Новый бросающий: %1$s |
| `msg.burmaldaholic.craps.come_moved` | Your Come bet moves to %1$s | Ваша кам-ставка переходит на %1$s |
| `msg.burmaldaholic.craps.field_win` | Field pays %1$s | Филд приносит %1$s |
| `msg.burmaldaholic.craps.odds_returned` | Come odds were off on the come-out roll and are returned | Коэффициент на кам не играл на первом броске — ставка возвращена |
| `msg.burmaldaholic.craps.auto_roll` | The shooter took too long — the dice roll themselves | Бросающий задумался — кости бросились сами |
| `gui.burmaldaholic.craps.dont_come_point` | Don't Come bet on %1$s | Ставка не-кам на %1$s |
| `gui.burmaldaholic.craps.bet_flat` | %1$s — %2$s | %1$s — %2$s |
| `gui.burmaldaholic.craps.bet_with_odds` | %1$s — %2$s + odds %3$s | %1$s — %2$s + коэффициент %3$s |
| `gui.burmaldaholic.craps.odds_off` | (odds off on the come-out roll) | (коэффициент не работает на первом броске) |
| `gui.burmaldaholic.craps.bet_window` | Bets close in %1$s | Приём ставок закроется через %1$s |
| `gui.burmaldaholic.craps.auto_roll_in` | The dice roll themselves in %1$s | Кости бросятся сами через %1$s |
| `gui.burmaldaholic.craps.waiting_shooter` | Waiting for %1$s to roll | Ждём броска: %1$s |
| `gui.burmaldaholic.craps.amount_with_odds` | %1$s + %2$s | %1$s + %2$s |
| `gui.burmaldaholic.craps.field_label` | Field: 2 (%1$s:1) 3 4 9 10 11 12 (%2$s:1) | Филд: 2 (%1$s:1) 3 4 9 10 11 12 (%2$s:1) |
| `gui.burmaldaholic.craps.others_short` | (+%1$s) | (+%1$s) |
| `gui.burmaldaholic.craps.others` | Other players: %1$s | Другие игроки: %1$s |
| `gui.burmaldaholic.craps.click_to_bet` | Click to bet %1$s | Нажмите, чтобы поставить %1$s |
| `gui.burmaldaholic.craps.click_odds` | Click to add odds: %1$s | Нажмите, чтобы добавить коэффициент: %1$s |
| `msg.burmaldaholic.craps.rolled` | %1$s rolls: %2$s + %3$s = %4$s | %1$s бросает: %2$s + %3$s = %4$s |
| `msg.burmaldaholic.craps.bet_result` | %1$s: %2$s | %1$s: %2$s |
| `msg.burmaldaholic.craps.bets_played_out` | You left the craps table — your bets were played out. Net: %1$s | Вы ушли из-за стола для крэпса — ваши ставки доиграны. Итог: %1$s |
| `msg.burmaldaholic.craps.bets_refunded` | The craps table closed — your bets were returned | Стол для крэпса закрыт — ваши ставки возвращены |
| `msg.burmaldaholic.craps.dont_come_moved` | Your Don't Come bet moves to %1$s | Ваша ставка не-кам переходит на %1$s |

### Presentation (animation wave, `animation/tables.md` §6)

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.craps.fx.hot_shooter` | Hot shooter! %1$s points in a row | Горячая рука! Поинтов подряд: %1$s |
| `gui.burmaldaholic.craps.fx.new_shooter` | New shooter: %1$s | Новый бросающий: %1$s |
| `gui.burmaldaholic.craps.fx.bar_push` | Bar 12 — push | Бар на 12 — возврат |
| `gui.burmaldaholic.craps.fx.throwing` | %1$s throws… | Бросает %1$s… |

### Table redesign (lane J-L6, `docs/design/visual/tables.md` §10: felt labels)

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.craps.layout.pass_line` | PASS LINE | ПАСС-ЛАЙН |
| `gui.burmaldaholic.craps.layout.dont_pass` | DON'T PASS BAR | НЕ-ПАСС |
| `gui.burmaldaholic.craps.layout.come` | COME | КАМ |
| `gui.burmaldaholic.craps.layout.dont_come` | DON'T COME | НЕ-КАМ |
| `gui.burmaldaholic.craps.layout.field` | FIELD | ФИЛД |
| `gui.burmaldaholic.craps.layout.odds` | ODDS | ОДДС |

---

## baccarat

⚠ Added 2026-09 (GAME_DESIGN §20, UI.md §14). Hand/box names «Игрок» and «Банкир» are capitalized
when they mean the hand or the bet box; people at the table are never called «игрок» in these
strings (see LOCALIZATION.md §6.7). Advancement strings are in §advancements, config labels in
§config (usual places). Arguments: `%1$s=chips` etc. as in the header.

| Key | EN | RU |
|-----|----|----|
| `block.burmaldaholic.baccarat_table` | Baccarat Table | Стол для баккара |
| `block.burmaldaholic.baccarat_table_high_roller` | High Roller Baccarat Table | Стол хайроллеров: баккара |
| `block.burmaldaholic.baccarat_table_player_banked` | Chemin de Fer Table | Стол для шмен-де-фер |
| `tooltip.burmaldaholic.baccarat_table` | Punto Banco: bet on Player, Banker or Tie | Пунто банко: ставьте на Игрока, Банкира или ничью |
| `tooltip.burmaldaholic.baccarat_table_player_banked` | Baccarat where players hold the bank. The house takes %1$s%% of banker wins | Баккара, где банк держат сами игроки. Заведение берёт %1$s %% с выигрышей банка |
| `entity.burmaldaholic.baccarat_dealer` | Baccarat Dealer | Дилер баккара |
| `item.burmaldaholic.baccarat_dealer_spawn_egg` | Baccarat Dealer Spawn Egg | Яйцо призыва дилера баккара |
| `gui.burmaldaholic.common.game.baccarat` | Baccarat | Баккара |
| `gui.burmaldaholic.common.game.chemmy` | Chemin de Fer | Шмен-де-фер |
| `gui.burmaldaholic.baccarat.title` | Baccarat | Баккара |
| `gui.burmaldaholic.baccarat.title_high_roller` | Baccarat — High Rollers | Баккара — хайроллеры |
| `gui.burmaldaholic.baccarat.title_chemmy` | Chemin de Fer | Шмен-де-фер |
| `gui.burmaldaholic.baccarat.player` | Player | Игрок |
| `gui.burmaldaholic.baccarat.banker` | Banker | Банкир |
| `gui.burmaldaholic.baccarat.tie` | Tie | Ничья |
| `gui.burmaldaholic.baccarat.player_pair` | Player Pair | Пара игрока |
| `gui.burmaldaholic.baccarat.banker_pair` | Banker Pair | Пара банкира |
| `gui.burmaldaholic.baccarat.bet.player` | Player (1:1) | Игрок (1:1) |
| `gui.burmaldaholic.baccarat.bet.banker` | Banker (1:1 −%1$s%%) | Банкир (1:1 −%1$s %%) |
| `gui.burmaldaholic.baccarat.bet.tie` | Tie (%1$s:1) | Ничья (%1$s:1) |
| `gui.burmaldaholic.baccarat.bet.player_pair` | Player Pair (%1$s:1) | Пара игрока (%1$s:1) |
| `gui.burmaldaholic.baccarat.bet.banker_pair` | Banker Pair (%1$s:1) | Пара банкира (%1$s:1) |
| `gui.burmaldaholic.baccarat.bet.banker.tooltip` | Wins a bit more often, so the house keeps %1$s%% of the win | Выигрывает чуть чаще, поэтому заведение берёт %1$s %% комиссии с выигрыша |
| `gui.burmaldaholic.baccarat.bet.tie.tooltip` | Both hands end on the same total. Player and Banker bets are returned | Обе руки набирают одинаково. Ставки на Игрока и Банкира возвращаются |
| `gui.burmaldaholic.baccarat.bet.pair.tooltip` | The first two cards of that hand are the same rank | Первые две карты этой руки одного достоинства |
| `gui.burmaldaholic.baccarat.bet_form_title` | Baccarat — %1$s | Баккара — %1$s |
| `gui.burmaldaholic.baccarat.side_bets` | Side bets | Доп. ставки |
| `gui.burmaldaholic.baccarat.your_bets` | Your bets | Ваши ставки |
| `gui.burmaldaholic.baccarat.no_bets` | No bets yet | Ставок пока нет |
| `gui.burmaldaholic.baccarat.clear_bets` | Clear bets | Убрать ставки |
| `gui.burmaldaholic.baccarat.same_bets` | Same bets again | Те же ставки |
| `gui.burmaldaholic.baccarat.change_bets` | Change bets | Изменить ставки |
| `gui.burmaldaholic.baccarat.bet_line` | %1$s — %2$s | %1$s — %2$s |
| `gui.burmaldaholic.baccarat.place_bets` | Place your bets | Делайте ваши ставки |
| `gui.burmaldaholic.baccarat.no_more_bets` | No more bets | Ставок больше нет |
| `gui.burmaldaholic.baccarat.bets_close_in` | Bets close in %1$s | Приём ставок закроется через %1$s |
| `gui.burmaldaholic.baccarat.ready_count` | Ready: %1$s of %2$s | Готовы: %1$s из %2$s |
| `gui.burmaldaholic.baccarat.seated` | At the table: %1$s | За столом: %1$s |
| `gui.burmaldaholic.baccarat.waiting_next` | You're seated — you'll play from the next coup | Место за вами — играете со следующей раздачи |
| `gui.burmaldaholic.baccarat.limits_banker` | Banker bets in steps of %1$s | Ставка на Банкира — кратно %1$s |
| `gui.burmaldaholic.baccarat.hand` | %1$s: %2$s | %1$s: %2$s |
| `gui.burmaldaholic.baccarat.natural` | Natural %1$s! | Натуральная %1$s! |
| `gui.burmaldaholic.baccarat.player_draws` | Player draws a third card | Игрок берёт третью карту |
| `gui.burmaldaholic.baccarat.banker_draws` | Banker draws a third card | Банкир берёт третью карту |
| `gui.burmaldaholic.baccarat.player_stands` | Player stands on %1$s | Игрок останавливается на %1$s |
| `gui.burmaldaholic.baccarat.banker_stands` | Banker stands on %1$s | Банкир останавливается на %1$s |
| `gui.burmaldaholic.baccarat.result.player` | Player wins %1$s to %2$s | Выигрывает Игрок: %1$s против %2$s |
| `gui.burmaldaholic.baccarat.result.banker` | Banker wins %1$s to %2$s | Выигрывает Банкир: %1$s против %2$s |
| `gui.burmaldaholic.baccarat.result.tie` | Tie at %1$s | Ничья: по %1$s |
| `gui.burmaldaholic.baccarat.result.pair_player` | Player Pair! | Пара игрока! |
| `gui.burmaldaholic.baccarat.result.pair_banker` | Banker Pair! | Пара банкира! |
| `gui.burmaldaholic.baccarat.line.win` | %1$s: +%2$s | %1$s: +%2$s |
| `gui.burmaldaholic.baccarat.line.lose` | %1$s: −%2$s | %1$s: −%2$s |
| `gui.burmaldaholic.baccarat.line.push` | %1$s: returned | %1$s: возврат |
| `gui.burmaldaholic.baccarat.line.commission` | Commission: %1$s | Комиссия: %1$s |
| `gui.burmaldaholic.baccarat.shuffling` | Shuffling the shoe… | Перемешиваем шуз… |
| `gui.burmaldaholic.baccarat.burned.p1` | %1$s card burned | Сожжена %1$s карта |
| `gui.burmaldaholic.baccarat.burned.p21` | %1$s cards burned | Сожжена %1$s карта |
| `gui.burmaldaholic.baccarat.burned.p2` | %1$s cards burned | Сожжены %1$s карты |
| `gui.burmaldaholic.baccarat.burned.p5` | %1$s cards burned | Сожжено %1$s карт |
| `gui.burmaldaholic.baccarat.shoe_left.p1` | Shoe: %1$s card left | В шузе осталась %1$s карта |
| `gui.burmaldaholic.baccarat.shoe_left.p21` | Shoe: %1$s cards left | В шузе осталась %1$s карта |
| `gui.burmaldaholic.baccarat.shoe_left.p2` | Shoe: %1$s cards left | В шузе осталось %1$s карты |
| `gui.burmaldaholic.baccarat.shoe_left.p5` | Shoe: %1$s cards left | В шузе осталось %1$s карт |
| `gui.burmaldaholic.baccarat.history` | This shoe | Этот шуз |
| `gui.burmaldaholic.baccarat.bead.player` | P | И |
| `gui.burmaldaholic.baccarat.bead.banker` | B | Б |
| `gui.burmaldaholic.baccarat.bead.tie` | T | Н |
| `gui.burmaldaholic.baccarat.stats` | Player %1$s · Banker %2$s · Tie %3$s | Игрок %1$s · Банкир %2$s · Ничья %3$s |
| `gui.burmaldaholic.baccarat.actionbar` | Player %1$s · Banker %2$s | Игрок %1$s · Банкир %2$s |
| `gui.burmaldaholic.baccarat.snapped` | Banker bet rounded down to %1$s | Ставка на Банкира округлена вниз до %1$s |
| `gui.burmaldaholic.baccarat.rules.1` | Decks: %1$s · Player 1:1 · Banker 1:1 minus %2$s%% · Tie %3$s:1 | Колод: %1$s · Игрок 1:1 · Банкир 1:1 минус %2$s %% · Ничья %3$s:1 |
| `gui.burmaldaholic.baccarat.rules.2` | The hand closest to 9 wins. Tens and faces count 0, aces 1; only the last digit of the total counts. | Побеждает рука, которая ближе к 9. Десятки и картинки — 0, туз — 1; от суммы берётся последняя цифра. |
| `gui.burmaldaholic.baccarat.rules.3` | Third cards are drawn automatically by fixed rules. You only choose where to bet. | Третьи карты сдаются автоматически по строгим правилам. От вас — только ставка. |
| `gui.burmaldaholic.baccarat.rules.4` | On a tie, Player and Banker bets are returned. | При ничьей ставки на Игрока и Банкира возвращаются. |
| `gui.burmaldaholic.baccarat.rules.5` | Pairs pay %1$s:1 when the first two cards of that hand are the same rank. | Пара платит %1$s:1, если первые две карты руки одного достоинства. |
| `gui.burmaldaholic.baccarat.rules.natural` | 8 or 9 on the first two cards is a natural: nobody draws. | 8 или 9 с первых двух карт — натуральная: никто не добирает. |
| `gui.burmaldaholic.baccarat.rules.player` | Player draws on 0–5 and stands on 6–7. | Игрок берёт при 0–5 и останавливается при 6–7. |
| `gui.burmaldaholic.baccarat.rules.banker_no_draw` | If Player stood, Banker draws on 0–5 and stands on 6–7. | Если Игрок не брал, Банкир берёт при 0–5 и останавливается при 6–7. |
| `gui.burmaldaholic.baccarat.rules.banker_title` | If Player drew, Banker's move by its total and Player's third card: | Если Игрок брал, Банкир решает по своей сумме и третьей карте Игрока: |
| `gui.burmaldaholic.baccarat.rules.banker_0_2` | 0–2: always draws | 0–2: берёт всегда |
| `gui.burmaldaholic.baccarat.rules.banker_3` | 3: draws unless the card is an 8 | 3: берёт, если это не 8 |
| `gui.burmaldaholic.baccarat.rules.banker_4` | 4: draws on 2–7 | 4: берёт при 2–7 |
| `gui.burmaldaholic.baccarat.rules.banker_5` | 5: draws on 4–7 | 5: берёт при 4–7 |
| `gui.burmaldaholic.baccarat.rules.banker_6` | 6: draws on 6–7 | 6: берёт при 6–7 |
| `gui.burmaldaholic.baccarat.rules.banker_7` | 7: stands | 7: не берёт |
| `gui.burmaldaholic.baccarat.rules.banker_step` | Banker bets go in steps of %1$s so the commission comes out exact. | Ставка на Банкира кратна %1$s — так комиссия считается без остатка. |
| `gui.burmaldaholic.baccarat.error.banker_step` | Banker bets must be a multiple of %1$s | Ставка на Банкира должна быть кратна %1$s |
| `gui.burmaldaholic.baccarat.error.side_max` | Tie and pair bets are limited to %1$s each | Ставки на ничью и пары — не больше %1$s каждая |
| `gui.burmaldaholic.baccarat.error.total_max` | Your bets per coup are limited to %1$s | Все ставки на одну раздачу — не больше %1$s |
| `gui.burmaldaholic.baccarat.error.min_total` | At this table, bet at least %1$s per coup | За этим столом ставка на раздачу — от %1$s |
| `gui.burmaldaholic.baccarat.error.pairs_off` | Pair bets are turned off on this server | Ставки на пары на этом сервере отключены |
| `gui.burmaldaholic.baccarat.error.pvp_owing` | You can't play against other players while you owe the Loan Shark | Пока вы должны ростовщику, играть против других игроков нельзя |
| `msg.burmaldaholic.baccarat.coup` | Coup #%1$s: %2$s | Раздача №%1$s: %2$s |
| `msg.burmaldaholic.baccarat.natural_nine` | A natural nine! The dealer allows a small smile. | Натуральная девятка! Дилер позволяет себе улыбнуться. |
| `msg.burmaldaholic.baccarat.tie_run.p1` | %1$s tie in a row! The shoe showers the brave with chips. | %1$s ничья подряд! Шуз осыпает смельчаков фишками. |
| `msg.burmaldaholic.baccarat.tie_run.p21` | %1$s ties in a row! The shoe showers the brave with chips. | %1$s ничья подряд! Шуз осыпает смельчаков фишками. |
| `msg.burmaldaholic.baccarat.tie_run.p2` | %1$s ties in a row! The shoe showers the brave with chips. | %1$s ничьи подряд! Шуз осыпает смельчаков фишками. |
| `msg.burmaldaholic.baccarat.tie_run.p5` | %1$s ties in a row! The shoe showers the brave with chips. | %1$s ничьих подряд! Шуз осыпает смельчаков фишками. |
| `msg.burmaldaholic.baccarat.new_shoe` | New shoe — shuffled and burned. Good luck! | Новый шуз — перемешан, карты сожжены. Удачи! |
| `msg.burmaldaholic.baccarat.player_joined` | %1$s joins the baccarat table | К столу для баккара подсаживается %1$s |
| `msg.burmaldaholic.baccarat.player_left` | %1$s leaves the baccarat table | Из-за стола для баккара встаёт %1$s |
| `msg.burmaldaholic.baccarat.left_refunded` | You left the table — your bets were returned | Вы ушли из-за стола — ставки возвращены |
| `msg.burmaldaholic.baccarat.bets_refunded` | The baccarat table closed before the deal — your bets were returned | Стол для баккара закрылся до раздачи — ставки возвращены |
| `msg.burmaldaholic.baccarat.no_table` | The dealer needs a baccarat table right next to them | Дилеру нужен стол для баккара совсем рядом |

### Chemin de fer (player-banked)

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.baccarat.chemmy.bank` | Bank: %1$s | Банк: %1$s |
| `gui.burmaldaholic.baccarat.chemmy.banker_is` | Banker: %1$s | Банк держит: %1$s |
| `gui.burmaldaholic.baccarat.chemmy.coverage` | Covers %1$s · open %2$s | Покрывает %1$s · свободно %2$s |
| `gui.burmaldaholic.baccarat.chemmy.offer` | You are offered the bank | Вам предлагают держать банк |
| `gui.burmaldaholic.baccarat.chemmy.take` | Take the bank (%1$s) | Взять банк (%1$s) |
| `gui.burmaldaholic.baccarat.chemmy.take_other` | Other amount… | Другая сумма… |
| `gui.burmaldaholic.baccarat.chemmy.bank_amount` | Bank amount (min %1$s) | Сумма банка (от %1$s) |
| `gui.burmaldaholic.baccarat.chemmy.pass` | Pass | Пас |
| `gui.burmaldaholic.baccarat.chemmy.keep` | Keep the bank (%1$s) | Оставить банк (%1$s) |
| `gui.burmaldaholic.baccarat.chemmy.pass_bank` | Pass the bank | Передать банк |
| `gui.burmaldaholic.baccarat.chemmy.bet_player` | Bet on Player… | Ставка на Игрока… |
| `gui.burmaldaholic.baccarat.chemmy.banco` | Banco (%1$s) | Банко (%1$s) |
| `gui.burmaldaholic.baccarat.chemmy.banco.tooltip` | Match the whole bank alone. Other bets are returned | Сыграть против всего банка в одиночку. Остальные ставки вернутся |
| `gui.burmaldaholic.baccarat.chemmy.waiting_offer` | Waiting for %1$s to decide on the bank | Ждём решения по банку: %1$s |
| `gui.burmaldaholic.baccarat.chemmy.you_bank` | You hold the bank. The punters are betting… | Банк у вас. Понтёры делают ставки… |
| `gui.burmaldaholic.baccarat.chemmy.house_coup` | Nobody took the bank — the house deals this coup | Банк никто не взял — эту раздачу ведёт заведение |
| `gui.burmaldaholic.baccarat.chemmy.rules.1` | One player holds the bank and plays the Banker hand; the others bet on Player against the bank. | Один игрок держит банк и играет за Банкира; остальные ставят на Игрока против банка. |
| `gui.burmaldaholic.baccarat.chemmy.rules.2` | Bets are accepted until the bank is covered. Banco matches the whole bank alone. | Ставки принимаются, пока банк не покрыт. «Банко» — игра против всего банка в одиночку. |
| `gui.burmaldaholic.baccarat.chemmy.rules.3` | A winning banker may keep the bank. A losing banker passes it on. The house takes %1$s%% of the banker's wins. | Выигравший банкир может оставить банк, проигравший передаёт его дальше. Заведение берёт %1$s %% с выигрышей банка. |
| `gui.burmaldaholic.baccarat.chemmy.error.coverage` | The bank only covers %1$s more | Банк покрывает ещё только %1$s |
| `gui.burmaldaholic.baccarat.chemmy.error.banco_funds` | Banco needs %1$s | Для «Банко» нужно %1$s |
| `gui.burmaldaholic.baccarat.chemmy.error.min_bank` | The smallest bank is %1$s | Минимальный банк — %1$s |
| `msg.burmaldaholic.baccarat.chemmy.took_bank` | %1$s takes the bank: %2$s | Банк берёт %1$s: %2$s |
| `msg.burmaldaholic.baccarat.chemmy.passed_bank` | %1$s passes the bank | %1$s передаёт банк |
| `msg.burmaldaholic.baccarat.chemmy.banco_called` | Banco! %1$s plays the whole bank | Банко! %1$s играет против всего банка |
| `msg.burmaldaholic.baccarat.chemmy.bank_wins` | The bank wins %1$s (house commission %2$s) | Банк выигрывает %1$s (комиссия заведения %2$s) |
| `msg.burmaldaholic.baccarat.chemmy.bank_pays` | The bank pays %1$s | Банк выплачивает %1$s |
| `msg.burmaldaholic.baccarat.bank_returned` | Your bank of %1$s was returned to your balance | Ваш банк (%1$s) вернулся на баланс |

### Presentation (animation wave, `animation/cards.md` §11)

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.baccarat.fx.squeezing` | %1$s squeezes… | %1$s вскрывает… |
| `gui.burmaldaholic.baccarat.fx.you_squeeze` | You squeeze… | Вы вскрываете… |
| `gui.burmaldaholic.baccarat.fx.player_card` | Player's card… | Карта игрока… |
| `gui.burmaldaholic.baccarat.fx.banker_card` | Banker's card… | Карта банкира… |
| `gui.burmaldaholic.baccarat.fx.tie_pays` | Tie pays! | Ничья сыграла! |
| `gui.burmaldaholic.baccarat.fx.banco` | Banco! | Банко! |
| `gui.burmaldaholic.baccarat.fx.shoe_passes` | The shoe passes to %1$s | Шуз переходит к игроку %1$s |

---

## uth

⚠ Added 2026-09 (GAME_DESIGN §21, UI.md §15). Hand names reuse `gui.burmaldaholic.poker.hand.*` and
street names `gui.burmaldaholic.poker.preflop` / `.flop` (nested as `%1$s=hand`). Advancement strings
are in §advancements, config labels in §config. «Ультимейт Техас Холдем» is written without quotes
in these values; running text in other modules that embeds the game name adds «» itself.

| Key | EN | RU |
|-----|----|----|
| `block.burmaldaholic.uth_table` | Ultimate Texas Hold'em Table | Стол «Ультимейт Техас Холдем» |
| `block.burmaldaholic.uth_table_high_roller` | High Roller Ultimate Texas Hold'em Table | Стол хайроллеров: Ультимейт Техас Холдем |
| `block.burmaldaholic.uth_table_player_banked` | Player-Banked Ultimate Hold'em Table | Стол «Ультимейт холдем» с банком игроков |
| `tooltip.burmaldaholic.uth_table` | Up to 6 players vs the dealer · Ante, Blind, Trips | До 6 игроков против дилера · анте, блайнд, трипс |
| `tooltip.burmaldaholic.uth_table_player_banked` | A player may take the dealer seat and bank the table. The house takes %1$s%% of the bank's winnings | Место дилера может занять игрок и держать банк стола. Заведение берёт %1$s %% с выигрыша банка |
| `entity.burmaldaholic.uth_dealer` | Hold'em Dealer | Дилер холдема |
| `item.burmaldaholic.uth_dealer_spawn_egg` | Hold'em Dealer Spawn Egg | Яйцо призыва дилера холдема |
| `gui.burmaldaholic.common.game.uth` | Ultimate Texas Hold'em | Ультимейт Техас Холдем |
| `gui.burmaldaholic.uth.title` | Ultimate Texas Hold'em | Ультимейт Техас Холдем |
| `gui.burmaldaholic.uth.title_high_roller` | Ultimate Hold'em — High Rollers | Ультимейт холдем — хайроллеры |
| `gui.burmaldaholic.uth.title_player_banked` | Ultimate Hold'em — player bank | Ультимейт холдем — банк игроков |
| `gui.burmaldaholic.uth.bet_title` | Ultimate Hold'em — your bets | Ультимейт холдем — ваши ставки |
| `gui.burmaldaholic.uth.ante` | Ante | Анте |
| `gui.burmaldaholic.uth.blind` | Blind | Блайнд |
| `gui.burmaldaholic.uth.trips` | Trips | Трипс |
| `gui.burmaldaholic.uth.play` | Play | Плей |
| `gui.burmaldaholic.uth.ante_amount` | Ante: %1$s | Анте: %1$s |
| `gui.burmaldaholic.uth.blind_amount` | Blind: %1$s | Блайнд: %1$s |
| `gui.burmaldaholic.uth.trips_amount` | Trips: %1$s | Трипс: %1$s |
| `gui.burmaldaholic.uth.play_amount` | Play: %1$s | Плей: %1$s |
| `gui.burmaldaholic.uth.play_multiple` | Play ×%1$s | Плей ×%1$s |
| `gui.burmaldaholic.uth.blind_equals_ante` | The Blind always equals the Ante | Блайнд всегда равен анте |
| `gui.burmaldaholic.uth.trips_optional` | Trips (optional, 0 = no bet) | Трипс (по желанию, 0 — без ставки) |
| `gui.burmaldaholic.uth.ante_limits` | Ante %1$s – %2$s (6 × Ante + Trips ≤ %3$s) | Анте %1$s – %2$s (6 × анте + трипс ≤ %3$s) |
| `gui.burmaldaholic.uth.at_risk` | At risk: up to %1$s | На кону: до %1$s |
| `gui.burmaldaholic.uth.check` | Check | Чек |
| `gui.burmaldaholic.uth.fold` | Fold | Пас |
| `gui.burmaldaholic.uth.bet_4x` | Bet ×4 (%1$s) | Ставка ×4 (%1$s) |
| `gui.burmaldaholic.uth.bet_3x` | Bet ×3 (%1$s) | Ставка ×3 (%1$s) |
| `gui.burmaldaholic.uth.bet_2x` | Bet ×2 (%1$s) | Ставка ×2 (%1$s) |
| `gui.burmaldaholic.uth.bet_1x` | Bet ×1 (%1$s) | Ставка ×1 (%1$s) |
| `gui.burmaldaholic.uth.check.tooltip` | Bet nothing now — you can still bet later | Пока не ставить — поставить можно будет позже |
| `gui.burmaldaholic.uth.bet_4x.tooltip` | Play bet of 4 Antes. No more decisions this round | Плей-ставка в 4 анте. Больше решений в этом раунде не будет |
| `gui.burmaldaholic.uth.bet_3x.tooltip` | Play bet of 3 Antes. No more decisions this round | Плей-ставка в 3 анте. Больше решений в этом раунде не будет |
| `gui.burmaldaholic.uth.bet_2x.tooltip` | Play bet of 2 Antes after the flop | Плей-ставка в 2 анте после флопа |
| `gui.burmaldaholic.uth.bet_1x.tooltip` | Last chance to play: a bet of 1 Ante | Последняя возможность сыграть: ставка в 1 анте |
| `gui.burmaldaholic.uth.fold.tooltip` | Give up: Ante and Blind are lost, Trips still plays | Сдаться: анте и блайнд проиграны, трипс ещё играет |
| `gui.burmaldaholic.uth.unaffordable` | Not enough chips for this bet | Не хватает фишек на эту ставку |
| `gui.burmaldaholic.uth.dealer` | Dealer | Дилер |
| `gui.burmaldaholic.uth.board` | Board | Общие карты |
| `gui.burmaldaholic.uth.your_cards` | Your cards | Ваши карты |
| `gui.burmaldaholic.uth.your_hand` | Your hand: %1$s | Ваша комбинация: %1$s |
| `gui.burmaldaholic.uth.dealer_hand` | Dealer: %1$s | Дилер: %1$s |
| `gui.burmaldaholic.uth.turn_river` | Turn and river | Тёрн и ривер |
| `gui.burmaldaholic.uth.showdown` | Showdown | Вскрытие |
| `gui.burmaldaholic.uth.your_decision` | Your decision | Ваше решение |
| `gui.burmaldaholic.uth.tag.deciding` | Deciding… | Думает… |
| `gui.burmaldaholic.uth.tag.checked` | Checked | Чек |
| `gui.burmaldaholic.uth.tag.folded` | Folded | Пас |
| `gui.burmaldaholic.uth.waiting_showdown` | %1$s — waiting for the showdown | %1$s — ждём вскрытия |
| `gui.burmaldaholic.uth.still_deciding` | Still deciding: %1$s | Ещё решают: %1$s |
| `gui.burmaldaholic.uth.waiting_next` | You're seated — you'll play from the next round | Место за вами — играете со следующего раунда |
| `gui.burmaldaholic.uth.seats_free.p1` | %1$s seat free | Свободно %1$s место |
| `gui.burmaldaholic.uth.seats_free.p21` | %1$s seats free | Свободно %1$s место |
| `gui.burmaldaholic.uth.seats_free.p2` | %1$s seats free | Свободно %1$s места |
| `gui.burmaldaholic.uth.seats_free.p5` | %1$s seats free | Свободно %1$s мест |
| `gui.burmaldaholic.uth.qualifies` | Dealer qualifies | Дилер квалифицируется |
| `gui.burmaldaholic.uth.not_qualifies` | Dealer does not qualify — Ante pushes | Дилер не квалифицируется — анте возвращается |
| `gui.burmaldaholic.uth.result.win` | You win with %1$s | Победа: %1$s |
| `gui.burmaldaholic.uth.result.lose` | Dealer wins with %1$s | Дилер выигрывает: %1$s |
| `gui.burmaldaholic.uth.result.tie` | Tie — Ante, Blind and Play are returned | Ничья — анте, блайнд и плей возвращаются |
| `gui.burmaldaholic.uth.result.folded` | Folded — Ante and Blind lost | Пас — анте и блайнд проиграны |
| `gui.burmaldaholic.uth.line.win` | %1$s: +%2$s | %1$s: +%2$s |
| `gui.burmaldaholic.uth.line.lose` | %1$s: −%2$s | %1$s: −%2$s |
| `gui.burmaldaholic.uth.line.push` | %1$s: returned | %1$s: возврат |
| `gui.burmaldaholic.uth.line.blind_bonus` | Blind, %1$s (%2$s): +%3$s | Блайнд, %1$s (%2$s): +%3$s |
| `gui.burmaldaholic.uth.line.trips_bonus` | Trips, %1$s (%2$s): +%3$s | Трипс, %1$s (%2$s): +%3$s |
| `gui.burmaldaholic.uth.paytable.blind` | Blind pays (only when you win) | Выплаты по блайнду (только при победе) |
| `gui.burmaldaholic.uth.paytable.trips` | Trips pays (on your hand, win or lose) | Выплаты по трипсу (за вашу комбинацию, при любом исходе) |
| `gui.burmaldaholic.uth.paytable.row` | %1$s — %2$s | %1$s — %2$s |
| `gui.burmaldaholic.uth.paytable.blind_lower` | Anything lower: Blind is returned | Всё, что ниже: блайнд возвращается |
| `gui.burmaldaholic.uth.rules.1` | Ante and Blind are required and equal. Trips is optional. | Анте и блайнд обязательны и равны. Трипс — по желанию. |
| `gui.burmaldaholic.uth.rules.2` | Before the flop: check or bet ×3 or ×4 the Ante. After the flop: check or bet ×2. After the river: bet ×1 or fold. | До флопа: чек или ставка ×3 или ×4 от анте. После флопа: чек или ×2. После ривера: ×1 или пас. |
| `gui.burmaldaholic.uth.rules.2_no3x` | Before the flop: check or bet ×4 the Ante. After the flop: check or bet ×2. After the river: bet ×1 or fold. | До флопа: чек или ставка ×4 от анте. После флопа: чек или ×2. После ривера: ×1 или пас. |
| `gui.burmaldaholic.uth.rules.3` | You make one Play bet per round at most. Your best five of your two cards and the five on the board count. | Плей-ставку можно сделать только один раз за раунд. Играют лучшие пять из ваших двух и пяти общих карт. |
| `gui.burmaldaholic.uth.rules.4` | The dealer qualifies with a pair or better. If not, the Ante is returned. | Дилер квалифицируется с парой и выше. Если нет — анте возвращается. |
| `gui.burmaldaholic.uth.rules.5` | Play and Ante pay 1:1. The Blind pays extra on a win with a straight or better. Trips pays on your hand even if you fold. | Плей и анте платят 1:1. Блайнд доплачивает при победе со стритом и выше. Трипс платит за вашу комбинацию даже после паса. |
| `gui.burmaldaholic.uth.error.worst_case_max` | 6 × Ante plus Trips must not exceed %1$s | 6 × анте плюс трипс — не больше %1$s |
| `gui.burmaldaholic.uth.error.ante_min` | The minimum Ante here is %1$s | Минимальное анте здесь — %1$s |
| `gui.burmaldaholic.uth.error.keep_for_river` | Keep at least %1$s on your balance for a river bet | Оставьте на балансе хотя бы %1$s на ставку после ривера |
| `gui.burmaldaholic.uth.error.trips_needs_ante` | Trips needs an Ante | Трипс — только вместе с анте |
| `gui.burmaldaholic.uth.error.trips_off` | Trips is turned off on this server | Трипс на этом сервере отключён |
| `gui.burmaldaholic.uth.actionbar` | %1$s · Board %2$s · %3$s | %1$s · Стол %2$s · %3$s |
| `msg.burmaldaholic.uth.auto_check` | Time's up — you check | Время вышло — чек |
| `msg.burmaldaholic.uth.auto_fold` | Time's up — you fold | Время вышло — пас |
| `msg.burmaldaholic.uth.auto_play` | Time's up — with %1$s on the table, you bet ×1 automatically | Время вышло — с такой комбинацией («%1$s») ставка ×1 сделана автоматически |
| `msg.burmaldaholic.uth.round_starts_in` | Dealing in %1$s | Раздача через %1$s |
| `msg.burmaldaholic.uth.player_joined` | %1$s sits down at the hold'em table | За стол холдема садится %1$s |
| `msg.burmaldaholic.uth.player_left` | %1$s leaves the hold'em table | Из-за стола холдема встаёт %1$s |
| `msg.burmaldaholic.uth.dealer_reveals` | The dealer shows %1$s | Дилер открывает: %1$s |
| `msg.burmaldaholic.uth.royal_broadcast` | ROYAL FLUSH! %1$s takes %2$s at Ultimate Texas Hold'em! | РОЯЛ-ФЛЕШ! %1$s забирает %2$s в «Ультимейт Техас Холдем»! |
| `msg.burmaldaholic.uth.bets_refunded` | The table closed before the deal — your bets were returned | Стол закрылся до раздачи — ставки возвращены |
| `msg.burmaldaholic.uth.left_refunded` | You left before the deal — your bets were returned | Вы ушли до раздачи — ставки возвращены |
| `msg.burmaldaholic.uth.no_table` | The dealer needs a hold'em table right next to them | Дилеру нужен стол для холдема совсем рядом |

### Player-banked table

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.uth.pvp.dealer_seat` | Dealer seat: %1$s | Место дилера: %1$s |
| `gui.burmaldaholic.uth.pvp.the_house` | the house | заведение |
| `gui.burmaldaholic.uth.pvp.bank` | Bank %1$s · reserved %2$s | Банк %1$s · в резерве %2$s |
| `gui.burmaldaholic.uth.pvp.take_seat` | Take the dealer seat… | Занять место дилера… |
| `gui.burmaldaholic.uth.pvp.take_seat_submit` | Take the seat | Занять место |
| `gui.burmaldaholic.uth.pvp.bank_amount` | Bank (min %1$s) | Банк (от %1$s) |
| `gui.burmaldaholic.uth.pvp.leave_seat` | Leave the dealer seat | Уйти с места дилера |
| `gui.burmaldaholic.uth.pvp.leaving_after_round` | Leaving after this round | Уходите после этого раунда |
| `gui.burmaldaholic.uth.pvp.you_bank` | You are the dealer. Your bank covers every seat | Вы — дилер. Ваш банк отвечает за все места |
| `gui.burmaldaholic.uth.pvp.round_result` | Bank this round: %1$s (house rake %2$s) | Банк за раунд: %1$s (рейк заведения %2$s) |
| `gui.burmaldaholic.uth.pvp.covers_up_to` | The bank covers an Ante up to %1$s | Банк покрывает анте до %1$s |
| `gui.burmaldaholic.uth.pvp.rules.1` | A player in the dealer seat covers every bet from their bank; the dealer hand plays by the usual rules. | Игрок на месте дилера отвечает за все ставки своим банком; рука дилера играет по обычным правилам. |
| `gui.burmaldaholic.uth.pvp.rules.2` | Each seat reserves its worst case (%1$s × Ante + %2$s × Trips) from the bank. The house takes %3$s%% of the bank's net win per round. | Каждое место резервирует в банке худший случай (%1$s × анте + %2$s × трипс). Заведение берёт %3$s %% с чистого выигрыша банка за раунд. |
| `gui.burmaldaholic.uth.error.bank_cover` | The bank can't cover that. Largest Ante it covers now: %1$s | Банк это не покроет. Сейчас он покрывает анте до %1$s |
| `gui.burmaldaholic.uth.error.min_bank` | The smallest bank is %1$s | Минимальный банк — %1$s |
| `gui.burmaldaholic.uth.error.seat_taken` | The dealer seat is taken | Место дилера занято |
| `gui.burmaldaholic.uth.error.seat_next_round` | Bets are already in — you can take the dealer seat from the next round | Ставки уже сделаны — место дилера можно занять со следующего раунда |
| `gui.burmaldaholic.uth.error.pvp_owing` | You can't bank for other players while you owe the Loan Shark | Пока вы должны ростовщику, держать банк для других игроков нельзя |
| `msg.burmaldaholic.uth.pvp.took_seat` | %1$s takes the dealer seat with a bank of %2$s | Место дилера занимает %1$s с банком %2$s |
| `msg.burmaldaholic.uth.pvp.left_seat` | %1$s leaves the dealer seat — the house deals | %1$s уходит с места дилера — дальше сдаёт заведение |
| `msg.burmaldaholic.uth.pvp.seat_offered` | The dealer seat is free. Take it? | Место дилера свободно. Займёте? |
| `msg.burmaldaholic.uth.pvp.bank_too_low` | Your bank can't cover a minimum bet any more — you leave the dealer seat after this round | Ваш банк больше не покрывает даже минимальную ставку — после этого раунда вы уходите с места дилера |
| `msg.burmaldaholic.uth.bank_returned` | Your bank of %1$s was returned to your balance | Ваш банк (%1$s) вернулся на баланс |

### Presentation (animation wave, `animation/cards.md` §11)

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.uth.tag.play` | Play ×%1$s | Плей ×%1$s |
| `gui.burmaldaholic.uth.fx.blind_bonus` | Blind bonus! | Бонус блайнда! |
| `gui.burmaldaholic.uth.fx.trips_bonus` | Trips bonus! | Бонус трипс! |
| `gui.burmaldaholic.uth.fx.royal` | ROYAL FLUSH! | РОЯЛ-ФЛЕШ! |

---

## extras

### Coin Flip

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.extras.coin.title` | Coin Flip | Монетка |
| `gui.burmaldaholic.extras.coin.heads` | Heads | Орёл |
| `gui.burmaldaholic.extras.coin.tails` | Tails | Решка |
| `gui.burmaldaholic.extras.coin.payout` | Win pays %1$s | Выигрыш: %1$s |
| `gui.burmaldaholic.extras.coin.flipping` | The coin is in the air… | Монетка в воздухе… |
| `gui.burmaldaholic.extras.coin.result_win` | %1$s! You win %2$s | %1$s! Ваш выигрыш: %2$s |
| `gui.burmaldaholic.extras.coin.result_lose` | %1$s. The house wins | %1$s. Выигрывает заведение |
| `gui.burmaldaholic.extras.soul.button` | Wager your soul | Поставить душу |
| `gui.burmaldaholic.extras.soul.warning_title` | Soul Wager | Ставка на душу |
| `gui.burmaldaholic.extras.soul.warning` | Win: +%1$s. Lose: you die. In Hardcore. For real. No Last Chance. | Выигрыш: +%1$s. Проигрыш — смерть. В хардкоре. По-настоящему. Без «Последнего шанса». |
| `gui.burmaldaholic.extras.soul.confirm_prompt` | Type %1$s to confirm | Введите %1$s для подтверждения |
| `gui.burmaldaholic.extras.soul_confirm_word` | DEAL | СДЕЛКА |
| `gui.burmaldaholic.extras.soul.hold` | Hold for 5 seconds to sign | Удерживайте 5 секунд, чтобы подписать |
| `gui.burmaldaholic.extras.soul.cancelled` | Wise choice. Or cowardly. Either way — alive. | Мудрое решение. Или трусливое. Главное — живы. |
| `msg.burmaldaholic.extras.soul.won` | The coin spares you. +%1$s. Don't push it. | Монетка вас пощадила. +%1$s. Не искушайте судьбу. |
| `msg.burmaldaholic.extras.soul.broadcast_won` | %1$s bet their soul on a coin — and kept it | Игрок %1$s поставил душу на монетку — и сохранил её |

### Wheel of Fortune

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.extras.wheel.title` | Wheel of Fortune | Колесо фортуны |
| `gui.burmaldaholic.extras.wheel.segment.bust` | Bust | Мимо |
| `gui.burmaldaholic.extras.wheel.segment.creeper` | Creeper | Крипер |
| `gui.burmaldaholic.extras.wheel.segment.half` | Half back | Половина |
| `gui.burmaldaholic.extras.wheel.segment.money_back` | Money back | При своих |
| `gui.burmaldaholic.extras.wheel.segment.double` | Double | Удвоение |
| `gui.burmaldaholic.extras.wheel.segment.triple` | Triple | Утроение |
| `gui.burmaldaholic.extras.wheel.segment.emerald` | Emerald ×5 | Изумруд ×5 |
| `gui.burmaldaholic.extras.wheel.segment.diamond` | Diamond ×10 | Алмаз ×10 |
| `gui.burmaldaholic.extras.wheel.legend` | %1$s — ×%2$s (%3$s of 54) | %1$s — ×%2$s (%3$s из 54) |
| `gui.burmaldaholic.extras.wheel.legend_of` | %1$s — ×%2$s (%3$s of %4$s) | %1$s — ×%2$s (%3$s из %4$s) |
| `gui.burmaldaholic.extras.wheel.segment_name.emerald` | Emerald | Изумруд |
| `gui.burmaldaholic.extras.wheel.segment_name.diamond` | Diamond | Алмаз |
| `gui.burmaldaholic.extras.wheel.result` | The wheel stops on %1$s: %2$s | Колесо остановилось: %1$s — %2$s |
| `gui.burmaldaholic.extras.wheel.spinning` | Round and round it goes… | Крутится-вертится… |
| `msg.burmaldaholic.extras.wheel.creeper` | The wheel stops on the Creeper. Something is hissing behind you… | Колесо остановилось на крипере. Кто-то шипит у вас за спиной… |

### Scratch Cards

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.extras.scratch.title` | Scratch Card | Моментальная лотерея |
| `gui.burmaldaholic.extras.scratch.title_gold` | Golden Scratch Card | Золотая лотерея |
| `gui.burmaldaholic.extras.scratch.next` | Scratch next | Стереть следующую |
| `gui.burmaldaholic.extras.scratch.all` | Scratch all | Стереть всё |
| `gui.burmaldaholic.extras.scratch.hint` | Find three matching prizes | Найдите три одинаковых приза |
| `gui.burmaldaholic.extras.scratch.win` | Three of a kind! You win %1$s | Три одинаковых! Ваш выигрыш: %1$s |
| `gui.burmaldaholic.extras.scratch.lose` | No match. The card goes in the bin | Совпадений нет. Билет — в мусорку |
| `gui.burmaldaholic.extras.scratch.creeper` | Three creepers?! That's not a prize… | Три крипера?! Это не приз… |
| `gui.burmaldaholic.extras.scratch.top_prize` | TOP PRIZE! %1$s! | ГЛАВНЫЙ ПРИЗ! %1$s! |
| `gui.burmaldaholic.extras.scratch.buy` | Buy for %1$s | Купить за %1$s |

### Plinko

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.extras.plinko.title` | Plinko | Плинко |
| `gui.burmaldaholic.extras.plinko.risk` | Risk | Риск |
| `gui.burmaldaholic.extras.plinko.risk.low` | Low | Низкий |
| `gui.burmaldaholic.extras.plinko.risk.medium` | Medium | Средний |
| `gui.burmaldaholic.extras.plinko.risk.high` | High | Высокий |
| `gui.burmaldaholic.extras.plinko.drop` | Drop ball | Бросить шарик |
| `gui.burmaldaholic.extras.plinko.bins` | Bins: %1$s | Лунки: %1$s |
| `gui.burmaldaholic.extras.plinko.result` | Bin ×%1$s — %2$s | Лунка ×%1$s — %2$s |
| `gui.burmaldaholic.extras.plinko.dropping` | Plink… plink… plink… | Плинк… плинк… плинк… |

### Dice Duel

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.extras.dice.title` | Dice Duel | Дуэль на костях |
| `gui.burmaldaholic.extras.dice.vs_house` | Duel the house | Дуэль с заведением |
| `gui.burmaldaholic.extras.dice.challenge_player` | Challenge a player | Вызвать игрока |
| `gui.burmaldaholic.extras.dice.rules` | Higher total wins 1:1. Ties push — except a tie on 7, which the house takes. | Больше сумма — выигрыш 1:1. Ничья — ставка возвращается, но ничья на 7 уходит заведению. |
| `gui.burmaldaholic.extras.dice.roll` | Roll | Бросить |
| `gui.burmaldaholic.extras.dice.your_roll` | You: %1$s + %2$s = %3$s | Вы: %1$s + %2$s = %3$s |
| `gui.burmaldaholic.extras.dice.their_roll` | %1$s: %2$s + %3$s = %4$s | %1$s: %2$s + %3$s = %4$s |
| `gui.burmaldaholic.extras.dice.dealer` | Dealer | Дилер |
| `gui.burmaldaholic.extras.dice.win` | You win %1$s | Ваш выигрыш: %1$s |
| `gui.burmaldaholic.extras.dice.lose` | You lose %1$s | Вы проиграли %1$s |
| `gui.burmaldaholic.extras.dice.tie` | Tie — bets returned | Ничья — ставки возвращены |
| `gui.burmaldaholic.extras.dice.tie_seven` | Tie on 7 — the house takes it | Ничья на семёрке — забирает заведение |
| `gui.burmaldaholic.extras.dice.target` | Opponent | Соперник |
| `gui.burmaldaholic.extras.dice.no_targets` | Nobody is close enough to challenge | Рядом нет никого, кого можно вызвать |
| `gui.burmaldaholic.extras.dice.accept` | Accept | Принять |
| `gui.burmaldaholic.extras.dice.decline` | Decline | Отказаться |
| `gui.burmaldaholic.extras.dice.pending` | Pending challenges | Ожидающие вызовы |
| `msg.burmaldaholic.extras.dice.challenge_sent` | Challenge sent to %1$s for %2$s | Вызов отправлен: %1$s, ставка %2$s |
| `msg.burmaldaholic.extras.dice.challenge_received` | %1$s challenges you to a Dice Duel for %2$s! Open the Casino Menu to answer. | %1$s вызывает вас на дуэль на костях, ставка %2$s! Ответьте в меню казино. |
| `msg.burmaldaholic.extras.dice.declined` | %1$s declined your challenge | %1$s: вызов отклонён |
| `msg.burmaldaholic.extras.dice.expired` | The challenge expired | Время на вызов истекло |
| `msg.burmaldaholic.extras.dice.pvp_result` | Dice Duel: %1$s beats %2$s and takes %3$s | Дуэль на костях: %1$s обыгрывает соперника (%2$s) и забирает %3$s |
| `msg.burmaldaholic.extras.dice.pvp_refund` | Three ties in a row — the dice refuse to decide. Stakes returned. | Три ничьи подряд — кости отказываются решать. Ставки возвращены. |
| `msg.burmaldaholic.extras.dice.already_pending` | You already have a challenge pending | У вас уже есть неотвеченный вызов |
| `msg.burmaldaholic.extras.dice.self` | Dueling yourself? Bold, but no. | Дуэль с собственным отражением? Смело, но нет. |
| `gui.burmaldaholic.extras.add` | +%1$s | +%1$s |
| `gui.burmaldaholic.extras.stake_kind` | Stake: %1$s | Ставим: %1$s |
| `gui.burmaldaholic.extras.stake_item_value` | held item (%1$s) | предмет в руке (%1$s) |
| `gui.burmaldaholic.extras.labeled` | %1$s: %2$s | %1$s: %2$s |
| `gui.burmaldaholic.extras.dice.challenge_amount` | Challenge for %1$s | Вызвать на %1$s |
| `gui.burmaldaholic.extras.dice.invite` | %1$s challenges you to a Dice Duel for %2$s | %1$s вызывает вас на дуэль на костях, ставка %2$s |
| `gui.burmaldaholic.extras.dice.accept_from` | Accept: %1$s, %2$s | Принять: %1$s, %2$s |
| `gui.burmaldaholic.extras.dice.decline_from` | Decline: %1$s | Отказать: %1$s |
| `gui.burmaldaholic.extras.dice.challenge_target` | Challenge %1$s | Вызвать: %1$s |
| `gui.burmaldaholic.extras.dice.fx.tie_reroll` | Tie! Re-roll %1$s of %2$s | Ничья! Переброс %1$s из %2$s |
| `gui.burmaldaholic.extras.dice.fx.round` | Round %1$s | Раунд %1$s |
| `gui.burmaldaholic.extras.dice.fx.victory` | Victory! | Победа! |
| `gui.burmaldaholic.extras.dice.fx.defeat` | Defeat | Поражение |
| `gui.burmaldaholic.extras.dice.fx.shake` | Shake the cup… | Трясём стакан… |

### Presentation (animation wave, `animation/extras-pvp.md` §15; `anim.*` keys are shared by extras and PvP)

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.anim.invite.hint` | Press %1$s to answer | Нажмите %1$s, чтобы ответить |
| `gui.burmaldaholic.anim.scratch.drag_hint` | Hold and drag to scratch, or click a cell | Зажмите и ведите, чтобы стереть, или щёлкните по клетке |
| `gui.burmaldaholic.anim.lobby.full` | Table full — starting! | Мест нет — начинаем! |
| `gui.burmaldaholic.anim.plinko.edge_banner` | EDGE! | КРАЙ! |

---

## pvp

⚠ Added 2026-09 (PVP.md §15, pre-merged by the architect; PVP.md stays normative for arguments and notes).

### Hub, new match, invites, lobby, match, results, rematch, errors

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.pvp.hub.title` | PvP Arena | Арена PvP |
| `gui.burmaldaholic.pvp.hub.record` | Your record: %1$s–%2$s · net %3$s | Ваш счёт: %1$s–%2$s · итог %3$s |
| `gui.burmaldaholic.pvp.hub.nemesis` | Nemesis: %1$s (%2$s–%3$s) | Заклятый соперник: %1$s (%2$s–%3$s) |
| `gui.burmaldaholic.pvp.hub.no_record` | No PvP matches yet | PvP-матчей пока не было |
| `gui.burmaldaholic.pvp.hub.pending` | Pending | Ждут ответа |
| `gui.burmaldaholic.pvp.hub.accept` | Accept: %1$s · %2$s | Принять: %1$s · %2$s |
| `gui.burmaldaholic.pvp.hub.decline` | Decline: %1$s | Отказать: %1$s |
| `gui.burmaldaholic.pvp.hub.nearby` | Open lobbies nearby (%1$s) | Лобби поблизости (%1$s) |
| `gui.burmaldaholic.pvp.hub.no_lobbies` | No open lobbies nearby | Поблизости нет открытых лобби |
| `gui.burmaldaholic.pvp.hub.lobby_row` | %1$s · %2$s · %3$s/%4$s | %1$s · %2$s · %3$s/%4$s |
| `gui.burmaldaholic.pvp.hub.join` | Join | Войти |
| `gui.burmaldaholic.pvp.hub.new_match` | New match… | Новый матч… |
| `gui.burmaldaholic.pvp.hub.rivals` | Head-to-head | Личные встречи |
| `gui.burmaldaholic.pvp.hub.machine_hint` | Slot Showdown, Plinko Battle and Wheel Party start at their machines | Битва автоматов, битва Плинко и «Колесо на всех» начинаются у самих автоматов |
| `gui.burmaldaholic.pvp.new.title` | New match | Новый матч |
| `gui.burmaldaholic.pvp.new.opponent` | Opponent | Соперник |
| `gui.burmaldaholic.pvp.new.open_lobby` | Open lobby (anyone nearby) | Открытое лобби (все, кто рядом) |
| `gui.burmaldaholic.pvp.new.stake` | Stake per player | Ставка с каждого |
| `gui.burmaldaholic.pvp.new.side` | Your side | Ваша сторона |
| `gui.burmaldaholic.pvp.new.submit` | Throw down the gauntlet | Бросить вызов |
| `gui.burmaldaholic.pvp.new.no_players` | Nobody is close enough — open a lobby instead | Рядом никого — лучше откройте лобби |
| `gui.burmaldaholic.pvp.game.coin` | Coin Flip Duel | Дуэль на монетке |
| `gui.burmaldaholic.pvp.game.slots` | Slot Showdown | Битва автоматов |
| `gui.burmaldaholic.pvp.game.wheel` | Wheel Party | Колесо на всех |
| `gui.burmaldaholic.pvp.game.plinko` | Plinko Battle | Битва Плинко |
| `gui.burmaldaholic.pvp.game.scratch` | Scratch Showdown | Лотерейная битва |
| `msg.burmaldaholic.pvp.invite.sent` | Challenge sent to %1$s: %2$s for %3$s | Вызов отправлен игроку %1$s: %2$s на %3$s |
| `msg.burmaldaholic.pvp.invite.received` | %1$s challenges you: %2$s for %3$s! | %1$s бросает вам вызов: %2$s на %3$s! |
| `gui.burmaldaholic.pvp.invite.accept_button` | [Accept] | [Принять] |
| `gui.burmaldaholic.pvp.invite.decline_button` | [Decline] | [Отказать] |
| `gui.burmaldaholic.pvp.invite.title` | Challenge! | Вызов! |
| `gui.burmaldaholic.pvp.invite.body` | %1$s challenges you: %2$s. Stake: %3$s each. | %1$s бросает вам вызов: %2$s. Ставка: %3$s с каждого. |
| `gui.burmaldaholic.pvp.invite.side` | You'd play %1$s | Ваша сторона: %1$s |
| `gui.burmaldaholic.pvp.invite.record` | Head-to-head vs %1$s: %2$s–%3$s | Личные встречи (%1$s): %2$s–%3$s |
| `gui.burmaldaholic.pvp.invite.record_none` | You've never played %1$s | С игроком %1$s вы ещё не играли |
| `gui.burmaldaholic.pvp.invite.expires` | Expires in %1$s | Истекает через %1$s |
| `gui.burmaldaholic.pvp.invite.accept` | Accept | Принять |
| `gui.burmaldaholic.pvp.invite.decline` | Decline | Отказаться |
| `msg.burmaldaholic.pvp.invite.accepted` | %1$s accepts! Stakes are in. | %1$s принимает вызов! Ставки сделаны. |
| `msg.burmaldaholic.pvp.invite.declined` | %1$s declines. Maybe next time. | %1$s отказывается. Может, в другой раз. |
| `msg.burmaldaholic.pvp.invite.expired` | No answer from %1$s — the challenge expired | %1$s не отвечает — вызов сгорел |
| `msg.burmaldaholic.pvp.invite.withdrawn` | The challenge from %1$s was withdrawn | Вызов от игрока %1$s отозван |
| `msg.burmaldaholic.pvp.invite.failed` | The stakes couldn't be collected — challenge cancelled | Собрать ставки не удалось — вызов отменён |
| `gui.burmaldaholic.pvp.lobby.title` | %1$s — lobby | %1$s — лобби |
| `gui.burmaldaholic.pvp.lobby.entry` | Entry: %1$s | Взнос: %1$s |
| `gui.burmaldaholic.pvp.lobby.pot` | Pot: %1$s | Банк: %1$s |
| `gui.burmaldaholic.pvp.lobby.rake` | House cut: %1$s | Комиссия заведения: %1$s |
| `gui.burmaldaholic.pvp.percent` | %1$s%% | %1$s %% |
| `gui.burmaldaholic.pvp.lobby.players` | Players: %1$s/%2$s | Игроки: %1$s/%2$s |
| `gui.burmaldaholic.pvp.lobby.host` | %1$s (host) | %1$s (организатор) |
| `gui.burmaldaholic.pvp.lobby.record` | Head-to-head %1$s–%2$s | Личные встречи %1$s–%2$s |
| `gui.burmaldaholic.pvp.lobby.starts_in` | Starts in %1$s | Старт через %1$s |
| `gui.burmaldaholic.pvp.lobby.need_more` | Waiting for at least one more player | Ждём ещё хотя бы одного игрока |
| `gui.burmaldaholic.pvp.lobby.start` | Start now | Начать |
| `gui.burmaldaholic.pvp.lobby.join` | Join (%1$s) | Войти (%1$s) |
| `gui.burmaldaholic.pvp.lobby.join_confirm` | Entry %1$s · %2$s · Pot %3$s | Взнос %1$s · %2$s · банк %3$s |
| `gui.burmaldaholic.pvp.lobby.leave` | Leave lobby | Выйти из лобби |
| `gui.burmaldaholic.pvp.lobby.open` | Open lobby | Открыть лобби |
| `gui.burmaldaholic.pvp.lobby.waiting_bar` | %1$s · %2$s/%3$s · starts in %4$s | %1$s · %2$s/%3$s · старт через %4$s |
| `msg.burmaldaholic.pvp.lobby.opened` | %1$s opens %2$s at a %3$s — entry %4$s. Use a machine nearby to join! | %1$s открывает «%2$s» (%3$s), взнос %4$s. Подходите к автомату рядом! |
| `msg.burmaldaholic.pvp.lobby.opened_here` | %1$s opens %2$s nearby — entry %3$s. Join from the Casino Menu! | %1$s открывает «%2$s» неподалёку, взнос %3$s. Вход — через меню казино! |
| `msg.burmaldaholic.pvp.lobby.joined` | %1$s joins (%2$s/%3$s) | %1$s в игре (%2$s/%3$s) |
| `msg.burmaldaholic.pvp.lobby.left` | %1$s leaves the lobby | %1$s выходит из лобби |
| `msg.burmaldaholic.pvp.lobby.new_host` | %1$s is the host now | Теперь организатор — %1$s |
| `msg.burmaldaholic.pvp.lobby.cancelled` | Not enough players — lobby closed | Не набралось игроков — лобби закрыто |
| `msg.burmaldaholic.pvp.lobby.refunded` | Your entry of %1$s was returned | Ваш взнос возвращён: %1$s |
| `msg.burmaldaholic.pvp.match.start` | %1$s begins! Pot: %2$s | «%1$s» начинается! Банк: %2$s |
| `msg.burmaldaholic.pvp.match.all_in` | %1$s goes ALL-IN! | %1$s идёт ва-банк! |
| `gui.burmaldaholic.pvp.all_in_tag` | ALL-IN | ВА-БАНК |
| `gui.burmaldaholic.pvp.match.go` | Go! | Поехали! |
| `gui.burmaldaholic.pvp.match.bar` | %1$s/%2$s · You %3$s (#%4$s) · %5$s %6$s | %1$s/%2$s · Вы %3$s (№%4$s) · %5$s %6$s |
| `gui.burmaldaholic.pvp.match.bar_leader` | %1$s/%2$s · You %3$s (#1) · lead %4$s | %1$s/%2$s · Вы %3$s (№1) · отрыв %4$s |
| `gui.burmaldaholic.pvp.match.standings` | Standings | Таблица |
| `gui.burmaldaholic.pvp.match.row` | #%1$s %2$s — %3$s | №%1$s %2$s — %3$s |
| `gui.burmaldaholic.pvp.match.hidden` | ??? | ??? |
| `gui.burmaldaholic.pvp.match.final` | Final results… | Итоги… |
| `gui.burmaldaholic.pvp.match.place` | #%1$s: %2$s — %3$s | №%1$s: %2$s — %3$s |
| `gui.burmaldaholic.pvp.result.title` | Result | Итог |
| `gui.burmaldaholic.pvp.result.winner_title` | %1$s WINS! | ПОБЕДА: %1$s! |
| `gui.burmaldaholic.pvp.result.dead_heat_title` | DEAD HEAT! | НОЗДРЯ В НОЗДРЮ! |
| `gui.burmaldaholic.pvp.result.you_win` | You take the pot: +%1$s | Банк ваш: +%1$s |
| `gui.burmaldaholic.pvp.result.you_lose` | Not this time: −%1$s | Не в этот раз: −%1$s |
| `gui.burmaldaholic.pvp.result.split` | The pot is split: +%1$s for you | Банк поделён: вам +%1$s |
| `gui.burmaldaholic.pvp.result.pot_line` | Pot %1$s · house cut %2$s · paid out %3$s | Банк %1$s · комиссия %2$s · выплачено %3$s |
| `msg.burmaldaholic.pvp.result.broadcast` | %1$s: %2$s takes %3$s! Opponents: %4$s | %1$s: %2$s забирает %3$s! Соперников: %4$s |
| `msg.burmaldaholic.pvp.result.broadcast_split` | %1$s: dead heat — %2$s split %3$s | %1$s: ноздря в ноздрю — %2$s делят %3$s |
| `msg.burmaldaholic.pvp.result.offline` | While you were away, your %1$s match was played out: %2$s | Пока вас не было, матч «%1$s» доигран: %2$s |
| `msg.burmaldaholic.pvp.result.away` | You left, but the match plays on — the result is already decided | Вы ушли, но матч продолжается — исход уже решён |
| `msg.burmaldaholic.pvp.result.casino_off` | Casino mode was switched off — your %1$s match was settled at once: %2$s | Режим казино выключен — матч «%1$s» рассчитан сразу: %2$s |
| `gui.burmaldaholic.pvp.rematch` | Rematch | Реванш |
| `gui.burmaldaholic.pvp.rematch.waiting` | Rematch? Ready: %1$s/%2$s | Реванш? Готовы: %1$s/%2$s |
| `msg.burmaldaholic.pvp.rematch.requested` | %1$s wants a rematch! | %1$s требует реванша! |
| `msg.burmaldaholic.pvp.rematch.start` | Rematch! Same stakes, fresh luck. | Реванш! Ставки те же, удача новая. |
| `msg.burmaldaholic.pvp.rematch.expired` | No rematch this time | Реванша не будет |
| `msg.burmaldaholic.pvp.rematch.dropped` | %1$s can't cover the rematch and sits this one out | %1$s не тянет реванш и пропускает эту партию |
| `gui.burmaldaholic.pvp.error.debt` | No PvP while you owe the Loan Shark | Пока вы должны Ростовщику, PvP недоступно |
| `gui.burmaldaholic.pvp.error.target_unavailable` | %1$s can't play PvP right now | %1$s сейчас не может играть в PvP |
| `gui.burmaldaholic.pvp.error.spectator` | Not in Spectator mode | В режиме наблюдателя нельзя |
| `gui.burmaldaholic.pvp.error.self` | You can't challenge yourself | Вызвать самого себя нельзя |
| `gui.burmaldaholic.pvp.error.busy_target` | %1$s is in another game right now | %1$s сейчас в другой игре |
| `gui.burmaldaholic.pvp.error.pending` | You already have a challenge waiting for an answer | У вас уже есть вызов без ответа |
| `gui.burmaldaholic.pvp.error.too_far` | %1$s is too far away (max %2$s) | %1$s слишком далеко (макс. %2$s) |
| `gui.burmaldaholic.pvp.error.too_far_anchor` | You're too far from the host's machine (max %1$s) | Вы слишком далеко от автомата организатора (макс. %1$s) |
| `gui.burmaldaholic.pvp.error.other_dimension` | %1$s is in another dimension | %1$s в другом измерении |
| `gui.burmaldaholic.pvp.error.no_invites` | %1$s isn't taking challenges right now | %1$s сейчас не принимает вызовы |
| `gui.burmaldaholic.pvp.error.cooldown_target` | %1$s just said no. Give them a moment. | Игрок %1$s только что отказал. Дайте ему минутку. |
| `gui.burmaldaholic.pvp.error.cant_afford_target` | %1$s can't cover that stake | Игроку %1$s не хватает на такую ставку |
| `gui.burmaldaholic.pvp.error.over_target_max` | That's over %1$s's VIP limit (%2$s) | Это выше ВИП-лимита игрока %1$s (%2$s) |
| `gui.burmaldaholic.pvp.error.stake_min` | The minimum PvP stake is %1$s | Минимальная ставка в PvP — %1$s |
| `gui.burmaldaholic.pvp.error.lobby_full` | The lobby is full | В лобби нет мест |
| `gui.burmaldaholic.pvp.error.lobby_started` | The match has already started | Матч уже начался |
| `gui.burmaldaholic.pvp.error.lobby_gone` | That lobby is closed | Это лобби уже закрыто |
| `gui.burmaldaholic.pvp.error.wrong_machine` | Join from a %1$s near the host's machine (radius: %2$s) | Войти можно только с автомата «%1$s» рядом с организатором (радиус: %2$s) |
| `gui.burmaldaholic.pvp.error.owner` | Owners can't play PvP at their own machines | Владелец не может играть в PvP на своих автоматах |
| `gui.burmaldaholic.pvp.error.needs_opponent` | A duel needs an opponent — pick a player | Для дуэли нужен соперник — выберите игрока |
| `gui.burmaldaholic.pvp.error.over_cap` | The cap here is %1$s per player | Здесь не больше %1$s с игрока |
| `gui.burmaldaholic.pvp.error.no_more_bets` | No more bets — the wheel is about to spin | Ставок больше нет — колесо вот-вот закрутится |
| `gui.burmaldaholic.pvp.settings.invites` | Accept PvP challenges | Принимать вызовы игроков |
| `gui.burmaldaholic.pvp.admin.matches` | PvP matches | PvP-матчи |
| `gui.burmaldaholic.pvp.admin.match_row` | %1$s · %2$s · %3$s · pot %4$s | %1$s · %2$s · %3$s · банк %4$s |
| `gui.burmaldaholic.pvp.admin.cancel` | Cancel / settle now | Отменить / рассчитать |
| `gui.burmaldaholic.pvp.admin.state.lobby` | lobby | лобби |
| `gui.burmaldaholic.pvp.admin.state.drawn` | in play | идёт игра |
| `gui.burmaldaholic.pvp.admin.state.settled` | settled | рассчитан |
| `gui.burmaldaholic.pvp.admin.rake_warning` | Warning: the PvP house cut (%1$s) is below the cheapest house game — VIP can be farmed cheaply | Внимание: комиссия PvP (%1$s) ниже преимущества самой дешёвой игры заведения — ВИП можно накрутить задёшево |
| `gui.burmaldaholic.pvp.charter.rake` | PvP house cut collected: %1$s | Комиссия с PvP-матчей: %1$s |
| `gui.burmaldaholic.pvp.toggle` | %1$s: %2$s | %1$s: %2$s |
| `gui.burmaldaholic.pvp.hub.current` | Your match: %1$s | Ваш матч: %1$s |
| `gui.burmaldaholic.pvp.hub.show` | Show match | Показать матч |
| `gui.burmaldaholic.pvp.hub.withdraw` | Withdraw challenge | Отозвать вызов |
| `gui.burmaldaholic.pvp.new.pick_game` | Pick a game | Выберите игру |
| `gui.burmaldaholic.pvp.new.no_games` | No duel games are available right now | Сейчас нет доступных дуэлей |
| `gui.burmaldaholic.pvp.new.stake_hint` | Type the stake in the box next to your opponent (%1$s – %2$s) | Впишите ставку в поле рядом с соперником (%1$s – %2$s) |
| `gui.burmaldaholic.pvp.admin.none` | No PvP matches right now | Сейчас PvP-матчей нет |
| `gui.burmaldaholic.pvp.admin.state.invited` | challenge | вызов |
| `gui.burmaldaholic.pvp.match.step` | Step %1$s | Этап %1$s |
| `gui.burmaldaholic.pvp.match.live` | %1$s · pot %2$s | %1$s · банк %2$s |
| `gui.burmaldaholic.pvp.result.standings` | Final standings | Итоговая таблица |
| `msg.burmaldaholic.pvp.command.no_invite` | You have no challenge to answer | Отвечать не на что — вызовов нет |
| `msg.burmaldaholic.pvp.command.no_match` | You're not in a PvP match | Вы не участвуете в PvP-матче |
| `msg.burmaldaholic.pvp.command.unknown_game` | Unknown PvP game: %1$s | Неизвестная PvP-игра: %1$s |
| `msg.burmaldaholic.pvp.command.unknown_match` | No PvP match with id %1$s | PvP-матча с номером %1$s нет |
| `msg.burmaldaholic.pvp.command.unknown_taunt` | Unknown line. Pick one of: %1$s | Такой фразы нет. Выберите: %1$s |
| `msg.burmaldaholic.pvp.command.cancelled` | Match %1$s: cancelled or settled | Матч %1$s отменён или рассчитан |
| `tooltip.burmaldaholic.lucky_coin.pvp` | Use on a player: challenge them to a Coin Flip Duel | На игрока — вызов на дуэль на монетке |

### Rivalry, win streaks, grudge matches

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.pvp.rivals.title` | Head-to-head | Личные встречи |
| `gui.burmaldaholic.pvp.rivals.row` | %1$s: %2$s–%3$s · net %4$s | %1$s: %2$s–%3$s · итог %4$s |
| `gui.burmaldaholic.pvp.rivals.none` | No rivals yet. Time to make some. | Соперников пока нет. Самое время завести. |
| `msg.burmaldaholic.pvp.streak.heating` | %1$s is heating up: %2$s in a row! | %1$s разогревается: %2$s подряд! |
| `msg.burmaldaholic.pvp.streak.rampage` | RAMPAGE! %1$s — %2$s in a row. Somebody stop them! | РАЗНОС! %1$s — %2$s подряд. Остановите это кто-нибудь! |
| `msg.burmaldaholic.pvp.streak.legendary` | LEGENDARY! %1$s — %2$s in a row! | ЛЕГЕНДА! %1$s — %2$s подряд! |
| `msg.burmaldaholic.pvp.streak.broken` | %1$s ends %2$s's run of %3$s! | %1$s прерывает серию игрока %2$s: %3$s подряд! |
| `gui.burmaldaholic.pvp.grudge.title` | GRUDGE MATCH | ДЕЛО ПРИНЦИПА |
| `gui.burmaldaholic.pvp.grudge.subtitle` | %1$s won the last %2$s | Последние %2$s — за игроком %1$s |
| `msg.burmaldaholic.pvp.grudge.revenge` | Sweet revenge! %1$s finally beats %2$s | Сладкая месть! %1$s наконец обыгрывает соперника — %2$s |

### Taunts

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.pvp.taunt.button` | Taunt… | Подколоть… |
| `gui.burmaldaholic.pvp.taunt.title` | Say something | Сказать пару слов |
| `msg.burmaldaholic.pvp.taunt.say` | %1$s: “%2$s” | %1$s: «%2$s» |
| `gui.burmaldaholic.pvp.taunt.limit` | That's enough talking for one match | Для одного матча слов достаточно |
| `gui.burmaldaholic.pvp.taunt.gg` | GG, well played | GG, красиво сыграно |
| `gui.burmaldaholic.pvp.taunt.luck` | Good luck. You'll need it | Удачи. Пригодится |
| `gui.burmaldaholic.pvp.taunt.wow` | No way! | Да ладно?! |
| `gui.burmaldaholic.pvp.taunt.rigged` | It's rigged! | Да тут подкрутка! |
| `gui.burmaldaholic.pvp.taunt.again` | Again. Right now. | Ещё раз. Сейчас же. |
| `gui.burmaldaholic.pvp.taunt.steel` | Nerves of steel | Нервы — стальные |
| `gui.burmaldaholic.pvp.taunt.bye` | Say bye to your chips | Прощайся с фишками |
| `gui.burmaldaholic.pvp.taunt.respect` | Respect | Моё почтение |

### Coin Flip Duel

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.pvp.coin.title` | Coin Flip Duel | Дуэль на монетке |
| `gui.burmaldaholic.pvp.coin.setup_title` | Coin Flip Duel vs %1$s | Дуэль на монетке: %1$s |
| `gui.burmaldaholic.pvp.coin.vs` | %1$s vs %2$s | %1$s против %2$s |
| `gui.burmaldaholic.pvp.coin.sides` | %1$s: %2$s · %3$s: %4$s | %1$s: %2$s · %3$s: %4$s |
| `gui.burmaldaholic.pvp.coin.landed` | %1$s! | %1$s! |
| `gui.burmaldaholic.pvp.coin.takes` | %1$s takes %2$s | %1$s забирает %2$s |
| `gui.burmaldaholic.pvp.coin.chain` | Chain: %1$s down %2$s | Цепочка: %1$s в минусе на %2$s |
| `gui.burmaldaholic.pvp.coin.don_heads` | Double or nothing — Heads | Всё или ничего — Орёл |
| `gui.burmaldaholic.pvp.coin.don_tails` | Double or nothing — Tails | Всё или ничего — Решка |
| `gui.burmaldaholic.pvp.coin.walk_away` | Walk away | Уйти |
| `gui.burmaldaholic.pvp.coin.don_explain` | Stake %1$s each. Win it and you're all square; lose it and you're down %2$s. | Ставка с каждого: %1$s. Выиграете — будете квиты, проиграете — минус %2$s. |
| `gui.burmaldaholic.pvp.coin.don_request_title` | Double or nothing? | Всё или ничего? |
| `gui.burmaldaholic.pvp.coin.don_request` | %1$s wants double or nothing: %2$s each, calling %3$s. You're up %4$s. | %1$s предлагает «всё или ничего»: с каждого %2$s, выбор — %3$s. Вы в плюсе на %4$s. |
| `gui.burmaldaholic.pvp.coin.let_it_ride` | Let it ride | Рискнуть |
| `gui.burmaldaholic.pvp.coin.take_money` | Take the money | Забрать выигрыш |
| `gui.burmaldaholic.pvp.coin.don_limit` | The chain is at its limit — no more doubling | Цепочка на пределе — удваивать больше нельзя |
| `gui.burmaldaholic.pvp.coin.don_unaffordable` | Double or nothing needs %1$s from each of you | Для «всё или ничего» нужно %1$s с каждого |
| `msg.burmaldaholic.pvp.coin.don_called` | Double or nothing #%1$s: %2$s on the line! | «Всё или ничего» №%1$s: на кону %2$s! |
| `msg.burmaldaholic.pvp.coin.all_square` | ALL SQUARE! %1$s and %2$s are even again (minus the house's cut) | КВИТЫ! %1$s и %2$s снова при своих (за вычетом комиссии) |
| `msg.burmaldaholic.pvp.coin.cashed_out` | %1$s takes the money and walks | %1$s забирает выигрыш и уходит |
| `msg.burmaldaholic.pvp.coin.walked_away` | %1$s walks away. Wise, or just broke? | %1$s уходит. Мудро — или просто на мели? |
| `gui.burmaldaholic.pvp.coin.spectate` | %1$s vs %2$s — %3$s on the line | %1$s против %2$s — на кону %3$s |
| `gui.burmaldaholic.pvp.coin.all_square_title` | ALL SQUARE! | КВИТЫ! |
| `gui.burmaldaholic.anim.pvp.waiting_for` | Waiting for %1$s… | Ждём: %1$s… |

### Slot Showdown

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.pvp.slots.title` | Slot Showdown | Битва автоматов |
| `gui.burmaldaholic.pvp.slots.host` | Start a Slot Showdown | Начать битву автоматов |
| `gui.burmaldaholic.pvp.slots.join` | Join Showdown: %1$s · %2$s/%3$s | В битву: %1$s · %2$s/%3$s |
| `gui.burmaldaholic.pvp.slots.spins` | Spins per player | Вращений у каждого |
| `gui.burmaldaholic.pvp.slots.spins_value` | Spins each: %1$s | Вращений у каждого: %1$s |
| `gui.burmaldaholic.pvp.slots.round` | Spin %1$s/%2$s | Вращение %1$s/%2$s |
| `gui.burmaldaholic.pvp.slots.final_spin` | FINAL SPIN | ФИНАЛЬНОЕ ВРАЩЕНИЕ |
| `gui.burmaldaholic.pvp.slots.spin_now` | Spin! | Крутить! |
| `gui.burmaldaholic.pvp.slots.auto_in` | Auto-spin in %1$s | Автовращение через %1$s |
| `gui.burmaldaholic.pvp.slots.hot` | HOT: %1$s ×2 | ГОРЯЧО: %1$s ×2 |
| `msg.burmaldaholic.pvp.slots.hot` | Hot symbol this spin: %1$s scores double! | Горячий символ вращения: %1$s — очки ×2! |
| `gui.burmaldaholic.pvp.slots.spin_points` | +%1$s this spin | +%1$s за вращение |
| `gui.burmaldaholic.pvp.slots.kaboom_title` | KABOOM! | БАБАХ! |
| `msg.burmaldaholic.pvp.slots.kaboom` | KABOOM! %1$s's score is blown in half: %2$s → %3$s | БАБАХ! Очки игрока %1$s — пополам: %2$s → %3$s |
| `gui.burmaldaholic.pvp.slots.swap_title` | SWAP! | РОКИРОВКА! |
| `msg.burmaldaholic.pvp.slots.swap` | An Ender Pearl drops! %1$s swaps scores with the leader %2$s: %3$s ⇄ %4$s | Выпал жемчуг Края! %1$s меняется очками с лидером (%2$s): %3$s ⇄ %4$s |
| `msg.burmaldaholic.pvp.slots.time_warp` | Time warp! %1$s's next spin counts double | Петля времени! Следующее вращение игрока %1$s — с двойными очками |
| `gui.burmaldaholic.pvp.slots.underdog` | UNDERDOG BOOST | ФОРА АУТСАЙДЕРУ |
| `msg.burmaldaholic.pvp.slots.underdog` | Underdog boost: %1$s scores double on the final spin! | Фора аутсайдеру: %1$s получает двойные очки в финальном вращении! |
| `msg.burmaldaholic.pvp.slots.underdog_you` | You're in last place — your final spin counts double! | Вы на последнем месте — финальное вращение даст двойные очки! |
| `gui.burmaldaholic.pvp.slots.rules.1` | Everyone spins the same machine. Each spin's win becomes points (10 per 1× bet); the most points takes the pot. | Все крутят один и тот же автомат. Выигрыш вращения превращается в очки (10 за 1× ставки); у кого больше очков, тот забирает банк. |
| `gui.burmaldaholic.pvp.slots.rules.2` | Each round one symbol is HOT: its wins score double. | В каждом раунде один символ — горячий: его выигрыши дают двойные очки. |
| `gui.burmaldaholic.pvp.slots.rules.3` | Free spins and bonus games play out fast and count in full. | Бесплатные вращения и бонусы разыгрываются быстро и засчитываются полностью. |
| `gui.burmaldaholic.pvp.slots.rules.4` | Surprises (%1$s%% each): KABOOM halves your score, SWAP trades it with the leader, TIME WARP doubles your next spin. | Сюрпризы (по %1$s %%): БАБАХ делит ваши очки пополам, РОКИРОВКА меняет их с лидером, ПЕТЛЯ ВРЕМЕНИ удваивает следующее вращение. |
| `gui.burmaldaholic.pvp.slots.rules.5` | Jackpots give fixed points: Mini %1$s · Minor %2$s · Major %3$s · Grand %4$s. | Джекпоты дают фиксированные очки: мини %1$s · минор %2$s · мажор %3$s · гранд %4$s. |
| `gui.burmaldaholic.pvp.slots.rules.6` | Last place before the final spin scores double on it. | Последнее место перед финальным вращением получает в нём двойные очки. |
| `gui.burmaldaholic.pvp.slots.tiebreak` | Tie-break: more winning lines, then the best single spin | При равенстве: больше выигрышных линий, затем лучшее вращение |
| `gui.burmaldaholic.pvp.slots.time_warp_title` | TIME WARP! | ПЕТЛЯ ВРЕМЕНИ! |
| `msg.burmaldaholic.pvp.slots.jackpot` | %1$s: %2$s jackpot, +%3$s points | %1$s: джекпот %2$s, очки: +%3$s |
| `gui.burmaldaholic.pvp.slots.feature` | %1$s: +%2$s | %1$s: +%2$s |
| `gui.burmaldaholic.pvp.slots.fx.next_x2` | Next ×2 | Следующее ×2 |
| `gui.burmaldaholic.pvp.slots.fx.boost_x2` | Boost ×2 | Фора ×2 |

### Wheel Party

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.pvp.wheel.title` | Wheel Party | Колесо на всех |
| `gui.burmaldaholic.pvp.wheel.host` | Start a Wheel Party | Начать «Колесо на всех» |
| `gui.burmaldaholic.pvp.wheel.join` | Join the party: %1$s/%2$s | Присоединиться: %1$s/%2$s |
| `gui.burmaldaholic.pvp.wheel.add` | Add to my slice | Увеличить свой кусок |
| `gui.burmaldaholic.pvp.wheel.cap` | Max stake per player | Макс. ставка с игрока |
| `gui.burmaldaholic.pvp.wheel.your_stake` | Your stake | Ваша ставка |
| `gui.burmaldaholic.pvp.wheel.rules` | Buy a slice of the wheel: the bigger your stake, the bigger your slice. One spin — the winner takes the pot. | Купите кусок колеса: чем больше ставка, тем больше кусок. Одно вращение — победитель забирает банк. |
| `gui.burmaldaholic.pvp.wheel.slice` | %1$s — %2$s (%3$s) | %1$s — %2$s (%3$s) |
| `gui.burmaldaholic.pvp.wheel.chance` | Win chance: %1$s | Шанс на победу: %1$s |
| `gui.burmaldaholic.pvp.wheel.spin_now` | Spin the wheel! | Крутить колесо! |
| `gui.burmaldaholic.pvp.wheel.spins_in` | Spins in %1$s | Вращение через %1$s |
| `gui.burmaldaholic.pvp.wheel.bar` | Pot %1$s · your slice %2$s · spins in %3$s | Банк %1$s · ваш кусок %2$s · вращение через %3$s |
| `gui.burmaldaholic.pvp.wheel.underdog_tag` | UNDERDOG | ТЁМНАЯ ЛОШАДКА |
| `msg.burmaldaholic.pvp.wheel.top_up` | %1$s adds %2$s — now %3$s of the wheel | %1$s добавляет %2$s — теперь это %3$s колеса |
| `msg.burmaldaholic.pvp.wheel.no_more_bets` | No more bets! | Ставок больше нет! |
| `msg.burmaldaholic.pvp.wheel.lands` | The wheel stops on %1$s's slice! | Колесо останавливается на куске игрока %1$s! |
| `msg.burmaldaholic.pvp.wheel.by_a_hair` | By a hair! Just past %1$s's slice… | На волоске! Чуть-чуть мимо куска игрока %1$s… |
| `msg.burmaldaholic.pvp.wheel.underdog` | UNDERDOG! %1$s wins with just %2$s of the wheel! | ТЁМНАЯ ЛОШАДКА! %1$s побеждает всего с %2$s колеса! |

### Plinko Battle

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.pvp.plinko.title` | Plinko Battle | Битва Плинко |
| `gui.burmaldaholic.pvp.plinko.host` | Start a Plinko Battle | Начать битву Плинко |
| `gui.burmaldaholic.pvp.plinko.join` | Join the battle: %1$s · %2$s/%3$s | В битву: %1$s · %2$s/%3$s |
| `gui.burmaldaholic.pvp.plinko.balls` | Balls per player | Шариков у каждого |
| `gui.burmaldaholic.pvp.plinko.balls_value` | Balls each: %1$s | Шариков у каждого: %1$s |
| `gui.burmaldaholic.pvp.plinko.round` | Ball %1$s/%2$s | Шарик %1$s/%2$s |
| `gui.burmaldaholic.pvp.plinko.final_ball` | FINAL BALL | ФИНАЛЬНЫЙ ШАРИК |
| `gui.burmaldaholic.pvp.plinko.drop_now` | Drop! | Бросить! |
| `gui.burmaldaholic.pvp.plinko.auto_in` | Auto-drop in %1$s | Автоброс через %1$s |
| `gui.burmaldaholic.pvp.plinko.ball_result` | Bin ×%1$s — %2$s | Лунка ×%1$s — %2$s |
| `gui.burmaldaholic.pvp.plinko.rules.1` | Everyone drops at the same time, at the same risk. Bin ×1 = 10 points. The most points takes the pot. | Все бросают одновременно и с одним риском. Лунка ×1 = 10 очков. У кого больше очков, тот забирает банк. |
| `gui.burmaldaholic.pvp.plinko.rules.2` | Last place before the final ball scores double on it. | Последнее место перед финальным шариком получает за него двойные очки. |
| `gui.burmaldaholic.pvp.plinko.tiebreak` | Tie-break: the best single ball | При равенстве решает лучший шарик |
| `msg.burmaldaholic.pvp.plinko.edge` | EDGE! %1$s hits the ×%2$s bin! | КРАЙ! %1$s попадает в лунку ×%2$s! |
| `msg.burmaldaholic.pvp.plinko.underdog` | Underdog boost: %1$s's final ball counts double! | Фора аутсайдеру: финальный шарик игрока %1$s — с двойными очками! |

### Scratch Showdown

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.pvp.scratch.title` | Scratch Showdown | Лотерейная битва |
| `gui.burmaldaholic.pvp.scratch.cell` | Cell %1$s/9 | Клетка %1$s/9 |
| `gui.burmaldaholic.pvp.scratch.scratch_now` | Scratch! | Стереть! |
| `gui.burmaldaholic.pvp.scratch.auto_in` | Auto-scratch in %1$s | Автостирание через %1$s |
| `gui.burmaldaholic.pvp.scratch.multiplier` | ×%1$s | ×%1$s |
| `gui.burmaldaholic.pvp.scratch.score` | Score: %1$s | Счёт: %1$s |
| `gui.burmaldaholic.pvp.scratch.rules.1` | Everyone gets a card. Nine cells are scratched for all at once, one at a time. The highest score takes the pot. | Каждому — по билету. Девять клеток стираются у всех одновременно, по одной. У кого больше очков, тот забирает банк. |
| `gui.burmaldaholic.pvp.scratch.rules.2` | Coal 1 · Iron 2 · Gold 3 · Emerald 5 · Diamond 10 · Nether Star 25 | Уголь 1 · Железо 2 · Золото 3 · Изумруд 5 · Алмаз 10 · Звезда Незера 25 |
| `gui.burmaldaholic.pvp.scratch.rules.3` | Three or more of a kind score double. Rabbit's Foot: the whole card ×2 (max ×4). | Три одинаковых и больше — очки ×2. Кроличья лапка: весь билет ×2 (максимум ×4). |
| `gui.burmaldaholic.pvp.scratch.rules.4` | Creeper: blows up your best cell so far. | Крипер: взрывает вашу лучшую открытую клетку. |
| `gui.burmaldaholic.pvp.scratch.tiebreak` | Tie-break: the best single cell | При равенстве решает лучшая клетка |
| `gui.burmaldaholic.pvp.scratch.symbol.coal` | Coal | Уголь |
| `gui.burmaldaholic.pvp.scratch.symbol.iron` | Iron | Железо |
| `gui.burmaldaholic.pvp.scratch.symbol.gold` | Gold | Золото |
| `gui.burmaldaholic.pvp.scratch.symbol.emerald` | Emerald | Изумруд |
| `gui.burmaldaholic.pvp.scratch.symbol.diamond` | Diamond | Алмаз |
| `gui.burmaldaholic.pvp.scratch.symbol.star` | Nether Star | Звезда Незера |
| `gui.burmaldaholic.pvp.scratch.symbol.creeper` | Creeper | Крипер |
| `gui.burmaldaholic.pvp.scratch.symbol.foot` | Rabbit's Foot | Кроличья лапка |
| `gui.burmaldaholic.pvp.scratch.symbol.burned` | Charred | Сгорело |
| `msg.burmaldaholic.pvp.scratch.creeper` | Creeper! %1$s loses a cell: %2$s | Крипер! У игрока %1$s сгорает клетка: %2$s |
| `msg.burmaldaholic.pvp.scratch.fizzle` | Creeper! …but %1$s has nothing to blow up | Крипер! …но у игрока %1$s взрывать нечего |
| `msg.burmaldaholic.pvp.scratch.foot` | Rabbit's Foot! %1$s's card is now worth ×%2$s | Кроличья лапка! Билет игрока %1$s теперь ×%2$s |

### NICE — spectator side bets

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.pvp.side.title` | Side bets | Ставки зрителей |
| `gui.burmaldaholic.pvp.side.open` | Bet on this match | Поставить на матч |
| `gui.burmaldaholic.pvp.side.pick` | Back a player | На кого ставим |
| `gui.burmaldaholic.pvp.side.odds` | %1$s — pays about ×%2$s | %1$s — выплата около ×%2$s |
| `gui.burmaldaholic.pvp.side.pool` | Side pool: %1$s | Банк зрителей: %1$s |
| `gui.burmaldaholic.pvp.side.closed` | Side bets are closed | Ставки зрителей закрыты |
| `gui.burmaldaholic.pvp.side.none` | No matches to bet on nearby | Поблизости нет матчей для ставок |
| `gui.burmaldaholic.pvp.side.error_player` | Players can't bet on their own match | Участники не ставят на свой матч |
| `gui.burmaldaholic.pvp.side.error_mode` | Side bets aren't available for this game | Для этой игры ставки зрителей недоступны |
| `msg.burmaldaholic.pvp.side.placed` | You back %1$s with %2$s | Вы ставите на игрока %1$s: %2$s |
| `msg.burmaldaholic.pvp.side.won` | Your pick won! +%1$s | Ваш игрок победил! +%1$s |
| `msg.burmaldaholic.pvp.side.lost` | Your pick lost: −%1$s | Ваш игрок проиграл: −%1$s |
| `msg.burmaldaholic.pvp.side.refunded` | Nobody backed the winner — side bets returned | На победителя никто не ставил — ставки зрителей возвращены |
| `msg.burmaldaholic.pvp.side.announce` | Side bets open: %1$s. Casino Menu → Challenges | Открыты ставки зрителей: %1$s. «Меню казино → Вызовы» |

### NICE — tournaments

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.pvp.tournament.title` | Tournaments | Турниры |
| `gui.burmaldaholic.pvp.tournament.none` | No tournaments right now | Сейчас турниров нет |
| `gui.burmaldaholic.pvp.tournament.row` | %1$s · %2$s · entry %3$s · %4$s/%5$s | %1$s · %2$s · взнос %3$s · %4$s/%5$s |
| `gui.burmaldaholic.pvp.tournament.format.knockout` | Knockout | На вылет |
| `gui.burmaldaholic.pvp.tournament.format.leaderboard` | Leaderboard | Таблица лидеров |
| `gui.burmaldaholic.pvp.tournament.register` | Register (%1$s) | Записаться (%1$s) |
| `gui.burmaldaholic.pvp.tournament.unregister` | Withdraw | Отказаться от участия |
| `gui.burmaldaholic.pvp.tournament.prize_pool` | Prize pool: %1$s | Призовой фонд: %1$s |
| `gui.burmaldaholic.pvp.tournament.prizes` | Prizes: %1$s | Призы: %1$s |
| `gui.burmaldaholic.pvp.tournament.starts_in` | Starts in %1$s | Старт через %1$s |
| `gui.burmaldaholic.pvp.tournament.ends_in` | Ends in %1$s | Финиш через %1$s |
| `gui.burmaldaholic.pvp.tournament.bracket` | Bracket | Сетка |
| `gui.burmaldaholic.pvp.tournament.round` | Round %1$s | Раунд %1$s |
| `gui.burmaldaholic.pvp.tournament.semifinal` | Semi-final | Полуфинал |
| `gui.burmaldaholic.pvp.tournament.final` | Final | Финал |
| `gui.burmaldaholic.pvp.tournament.match_row` | %1$s vs %2$s | %1$s против %2$s |
| `gui.burmaldaholic.pvp.tournament.bye` | %1$s goes through without a match | %1$s проходит дальше без игры |
| `gui.burmaldaholic.pvp.tournament.your_best` | Your best: %1$s (#%2$s) | Ваш лучший результат: %1$s (№%2$s) |
| `gui.burmaldaholic.pvp.tournament.enter_run` | Play a run (%1$s) | Сыграть попытку (%1$s) |
| `gui.burmaldaholic.pvp.tournament.runs_left` | Runs left: %1$s | Осталось попыток: %1$s |
| `gui.burmaldaholic.pvp.tournament.create` | Create tournament | Создать турнир |
| `gui.burmaldaholic.pvp.tournament.name` | Name | Название |
| `gui.burmaldaholic.pvp.tournament.added_prize` | Added prize (paid by the bank) | Добавка к призу (за счёт банка) |
| `gui.burmaldaholic.pvp.tournament.max_players` | Max players | Макс. участников |
| `gui.burmaldaholic.pvp.tournament.duration` | Duration | Длительность |
| `msg.burmaldaholic.pvp.tournament.announce` | Tournament! %1$s — %2$s. Entry %3$s. Register in Casino Menu → Challenges! | Турнир! %1$s — %2$s. Взнос %3$s. Запись — «Меню казино → Вызовы»! |
| `msg.burmaldaholic.pvp.tournament.starting` | The %1$s tournament begins! Players: %2$s | Турнир «%1$s» начинается! Участников: %2$s |
| `msg.burmaldaholic.pvp.tournament.cancelled` | Tournament cancelled — not enough players. Entries returned. | Турнир отменён — мало участников. Взносы возвращены. |
| `msg.burmaldaholic.pvp.tournament.next_match` | Your next match: vs %1$s, in %2$s | Ваш следующий матч — против игрока %1$s, через %2$s |
| `msg.burmaldaholic.pvp.tournament.eliminated` | Knocked out by %1$s. Thanks for playing! | Вы выбываете — сильнее оказался %1$s. Спасибо за игру! |
| `msg.burmaldaholic.pvp.tournament.champion` | CHAMPION! %1$s wins the %2$s tournament and %3$s! | ЧЕМПИОН! %1$s выигрывает турнир «%2$s» и %3$s! |
| `msg.burmaldaholic.pvp.tournament.prize` | Prize for #%1$s: %2$s | Приз за №%1$s: %2$s |
| `msg.burmaldaholic.pvp.tournament.leader` | New leader: %1$s with %2$s | Новый лидер: %1$s — %2$s |
| `item.burmaldaholic.pvp_trophy` | Champion's Trophy | Кубок чемпиона |
| `tooltip.burmaldaholic.pvp_trophy` | %1$s · %2$s · day %3$s | %1$s · %2$s · день %3$s |

### NICE — more modes, owner settings

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.pvp.game.series` | Coin Series | Серия на монетке |
| `gui.burmaldaholic.pvp.game.race` | Jackpot Race | Гонка за джекпотом |
| `gui.burmaldaholic.pvp.game.heist` | Wheel Heist | Налёт на колесо |
| `gui.burmaldaholic.pvp.game.scratch_poker` | Scratch Poker | Лотерейный покер |
| `gui.burmaldaholic.pvp.series.format` | Series: first to %1$s wins | Серия: нужно побед — %1$s |
| `gui.burmaldaholic.pvp.series.score` | Series %1$s–%2$s | Счёт серии %1$s–%2$s |
| `msg.burmaldaholic.pvp.series.match_point` | Match point for %1$s! | Матчбол у игрока %1$s! |
| `gui.burmaldaholic.pvp.race.rules` | Everyone spins together. The first to hit %1$s on a line takes the pot. | Все крутят одновременно. Кто первым соберёт на линии %1$s, тот забирает банк. |
| `gui.burmaldaholic.pvp.race.target` | three Diamonds or better | три алмаза или лучше |
| `gui.burmaldaholic.pvp.race.fee` | Per spin: %1$s from each | За вращение: %1$s с каждого |
| `msg.burmaldaholic.pvp.race.hit` | %1$s hits it! | %1$s срывает куш! |
| `msg.burmaldaholic.pvp.race.timeout` | Nobody hit it in %1$s — the most points wins | За %1$s никто не собрал — побеждает больше очков |
| `gui.burmaldaholic.pvp.heist.rules` | Three rounds, everyone spins. Grab loot, steal it or lose it all. The most loot takes the pot. | Три круга, крутят все. Хватайте добычу, крадите её или теряйте всё. У кого больше добычи, тот забирает банк. |
| `gui.burmaldaholic.pvp.heist.loot` | Loot +%1$s | Добыча +%1$s |
| `gui.burmaldaholic.pvp.heist.steal` | Steal | Кража |
| `gui.burmaldaholic.pvp.heist.bankrupt` | Bankrupt | Банкрот |
| `gui.burmaldaholic.pvp.heist.double` | Double | Удвоение |
| `gui.burmaldaholic.pvp.heist.shield` | Shield | Щит |
| `gui.burmaldaholic.pvp.heist.pick_victim` | Steal from whom? | У кого крадём? |
| `gui.burmaldaholic.pvp.heist.loot_line` | %1$s — loot %2$s | %1$s — добыча %2$s |
| `msg.burmaldaholic.pvp.heist.stole` | %1$s steals %2$s loot from %3$s! | %1$s крадёт у игрока %3$s добычу: %2$s! |
| `msg.burmaldaholic.pvp.heist.bankrupt` | BANKRUPT! %1$s loses all the loot | БАНКРОТ! %1$s теряет всю добычу |
| `msg.burmaldaholic.pvp.heist.shielded` | %1$s's shield blocks it! | Щит игрока %1$s отражает удар! |
| `gui.burmaldaholic.pvp.plinko.bumper` | Place bumper on… | Поставить отбойник… |
| `msg.burmaldaholic.pvp.plinko.bumper_placed` | %1$s puts a bumper on %2$s's next ball! | %1$s ставит отбойник на следующий шарик игрока %2$s! |
| `gui.burmaldaholic.pvp.scratch_poker.rules` | Hidden cards. Four cells, then bet; four more, then bet; the last cell at the showdown. | Билеты скрыты. Четыре клетки — торговля, ещё четыре — торговля, последняя — на вскрытии. |
| `msg.burmaldaholic.pvp.scratch_poker.folds` | %1$s folds | %1$s пасует |
| `gui.burmaldaholic.pvp.charter.allow` | Allow PvP matches | Разрешить PvP-матчи |

### Bots in PvP (policy and difficulty names come from BOTS.md)

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.pvp.bots.opponent` | Bot — %1$s | Бот — %1$s |
| `gui.burmaldaholic.pvp.bots.seats` | Seats | Кто играет |
| `gui.burmaldaholic.pvp.bots.difficulty` | Bot difficulty | Сложность ботов |
| `gui.burmaldaholic.pvp.bots.table_size` | Table size | Мест за столом |
| `gui.burmaldaholic.pvp.bots.table_size_value` | Seats: %1$s | Мест: %1$s |
| `gui.burmaldaholic.pvp.bots.will_fill` | Empty seats at the start: bots fill %1$s | Свободные места при старте займут боты: %1$s |
| `gui.burmaldaholic.pvp.bots.start_with_bots` | Start with bots | Начать с ботами |
| `gui.burmaldaholic.pvp.bots.tagged` | %1$s [%2$s] | %1$s [%2$s] |
| `msg.burmaldaholic.pvp.bots.joined` | %1$s joins the game | В игру вступает %1$s |
| `msg.burmaldaholic.pvp.bots.no_bots_here` | Bots can't play at this machine — humans only | На этом автомате боты не играют — только люди |

### Slot Showdown v1 legacy (removed with Slot Showdown v2, `SLOTS.md` §13.7 / §13.10)

| Key | EN | RU |
|-----|----|----|
| `msg.burmaldaholic.pvp.slots.star` | THREE STARS! %1$s scores %2$s | ТРИ ЗВЕЗДЫ! %1$s получает %2$s |

## loan

### Loan screen

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.loan.title` | Loan Shark | Ростовщик |
| `gui.burmaldaholic.loan.product.pocket` | Pocket money | На карманные |
| `gui.burmaldaholic.loan.product.rent` | Rent | На аренду |
| `gui.burmaldaholic.loan.product.business` | Business | На бизнес |
| `gui.burmaldaholic.loan.product.serious` | Serious | Серьёзный разговор |
| `gui.burmaldaholic.loan.product.life_changing` | Life-changing | Изменит вашу жизнь |
| `gui.burmaldaholic.loan.offer` | Borrow %1$s → repay %2$s within %3$s | Взять %1$s → вернуть %2$s за %3$s |
| `gui.burmaldaholic.loan.interest` | Interest: %1$s%% | Проценты: %1$s %% |
| `gui.burmaldaholic.loan.locked_count` | Bigger loans unlock with higher VIP status (%1$s locked) | Крупные займы открываются с ВИП-статусом (закрыто: %1$s) |
| `gui.burmaldaholic.loan.take` | Take loan | Взять займ |
| `gui.burmaldaholic.loan.confirm_title` | Sign here | Подпишите здесь |
| `gui.burmaldaholic.loan.confirm` | You get %1$s now and owe %2$s. Deadline: %3$s. Miss it and my associates will visit. | Вы получаете %1$s сейчас и должны %2$s. Срок: %3$s. Опоздаете — к вам заглянут мои коллеги. |
| `gui.burmaldaholic.loan.sign` | Sign | Подписать |
| `gui.burmaldaholic.loan.status.none` | You owe nothing. For now. | Вы ничего не должны. Пока. |
| `gui.burmaldaholic.loan.status.active` | You owe %1$s. Due in %2$s | Долг: %1$s. Вернуть через %2$s |
| `gui.burmaldaholic.loan.status.default` | OVERDUE: you owe %1$s. Late fees are adding up | ПРОСРОЧКА: долг %1$s. Пени капают |
| `gui.burmaldaholic.loan.status.cooldown` | No new loans for %1$s | Новых займов не будет ещё %1$s |
| `gui.burmaldaholic.loan.pay` | Pay | Заплатить |
| `gui.burmaldaholic.loan.pay_all` | Pay all (%1$s) | Погасить всё (%1$s) |
| `gui.burmaldaholic.loan.pay_amount` | Amount to pay | Сколько платим |
| `gui.burmaldaholic.loan.good_standing` | On-time loans: %1$s (−%2$s%% interest) | Займов погашено вовремя: %1$s (−%2$s %% к ставке) |
| `gui.burmaldaholic.loan.negotiate.title` | The collectors are here | Пришли коллекторы |
| `gui.burmaldaholic.loan.negotiate.pay_all` | Pay in full (%1$s) | Погасить полностью (%1$s) |
| `gui.burmaldaholic.loan.negotiate.pay_half` | Pay half (%1$s) | Отдать половину (%1$s) |
| `gui.burmaldaholic.loan.negotiate.refuse` | Refuse | Отказаться |
| `gui.burmaldaholic.loan.negotiate.timer` | They won't wait long: %1$s | Ждать они не будут: %1$s |
| `gui.burmaldaholic.loan.admin.info` | Loan (%1$s): %2$s · on-time loans: %3$s | Займ (%1$s): %2$s · погашено вовремя: %3$s |
| `gui.burmaldaholic.loan.admin.wave_sent` | Debt collectors sent to %1$s | Коллекторы отправлены: %1$s |
| `gui.burmaldaholic.loan.admin.wave_failed` | No wave: the player is not overdue, already has a squad, collectors are off, or there is no safe spot nearby | Волны не будет: у игрока нет просрочки, бригада уже в пути, коллекторы выключены или рядом нет безопасного места |

### Loan messages

| Key | EN | RU |
|-----|----|----|
| `msg.burmaldaholic.loan.taken` | Loan taken: +%1$s. You owe %2$s by the end of day %3$s | Займ получен: +%1$s. Вернуть %2$s до конца дня %3$s |
| `msg.burmaldaholic.loan.paid_partial` | Paid %1$s. Still owed: %2$s | Выплачено %1$s. Осталось: %2$s |
| `msg.burmaldaholic.loan.repaid` | Loan repaid. You're free! | Займ погашен. Вы свободны! |
| `msg.burmaldaholic.loan.repaid_on_time` | Repaid on time. The Shark likes you now. Slightly lower interest next time. | Погашено вовремя. Ростовщик к вам потеплел — в следующий раз проценты чуть ниже. |
| `msg.burmaldaholic.loan.warning_day` | Loan reminder: %1$s due in 1 day | Напоминание: %1$s нужно вернуть через день |
| `msg.burmaldaholic.loan.warning_final` | Your loan of %1$s is due in 2 minutes. Tick tock. | Займ %1$s нужно вернуть через 2 минуты. Тик-так. |
| `msg.burmaldaholic.loan.defaulted` | You missed the deadline. The loan is overdue: %1$s. Expect visitors. | Срок вышел. Займ просрочен: %1$s. Ждите гостей. |
| `msg.burmaldaholic.loan.defaulted_title` | OVERDUE | ПРОСРОЧКА |
| `msg.burmaldaholic.loan.late_fee` | Late fee: +%1$s. Now owed: %2$s | Пени: +%1$s. Теперь долг: %2$s |
| `msg.burmaldaholic.loan.garnished` | %1$s went straight to your debt | В счёт долга удержано: %1$s |
| `msg.burmaldaholic.loan.one_at_a_time` | One loan at a time. The Shark isn't a charity. | Один займ за раз. Ростовщик — не благотворительный фонд. |
| `msg.burmaldaholic.loan.cooldown` | After your last stunt, no loans for %1$s | После вашей прошлой выходки займов не будет ещё %1$s |
| `msg.burmaldaholic.loan.asset_freeze` | Asset freeze: %1$s seized from your balance. Wagering is blocked until the debt is paid. | Арест счёта: с баланса списано %1$s. Ставки заблокированы, пока долг не погашен. |
| `msg.burmaldaholic.loan.wave_incoming` | Debt collectors are on their way. You owe %1$s | Коллекторы уже в пути. Ваш долг: %1$s |
| `msg.burmaldaholic.loan.wave_title` | Knock knock | Тук-тук |
| `msg.burmaldaholic.loan.wave_subtitle` | Debt collectors are coming | Идут коллекторы |
| `msg.burmaldaholic.loan.wave_queued` | Collectors came by while you were away. They'll be back shortly. | Пока вас не было, заходили коллекторы. Скоро вернутся. |
| `msg.burmaldaholic.loan.paid_in_full_collectors` | Debt cleared. The collectors lose interest in you. | Долг закрыт. Коллекторы теряют к вам интерес. |
| `msg.burmaldaholic.loan.partial_collectors` | Paid %1$s. The squad leaves — for today. Still owed: %2$s | Выплачено %1$s. Бригада уходит — на сегодня. Осталось: %2$s |
| `msg.burmaldaholic.loan.repossessed` | Repossessed: %1$s from your balance and your %2$s | Изъято: %1$s с баланса и %2$s |
| `msg.burmaldaholic.loan.repossessed_chips_only` | Repossessed: %1$s from your balance | Изъято с баланса: %1$s |
| `msg.burmaldaholic.loan.squad_defeated` | The squad is down. The debt isn't. | Бригада повержена. Долг — нет. |
| `msg.burmaldaholic.loan.balance_cap` | Your balance can't hold this loan: room for only %1$s. Spend some chips first. | Баланс не вместит этот займ: места только на %1$s. Сначала потратьте фишки. |

### Loan Shark dialogue

All variants of one base receive the same arguments; a variant may ignore them.

| Key | EN | RU |
|-----|----|----|
| `dialog.burmaldaholic.loan.greeting.1` | Step into my office. Mind the sharks. | Проходите в кабинет. Акулы не кусаются. Почти. |
| `dialog.burmaldaholic.loan.greeting.2` | Short on chips? Happens to the best of us. Mostly to the rest of us. | Фишек не хватает? Бывает с лучшими. Чаще — с остальными. |
| `dialog.burmaldaholic.loan.greeting.3` | I believe in you. I also believe in interest. | Я в вас верю. А ещё я верю в проценты. |
| `dialog.burmaldaholic.loan.greeting.4` | Everybody wins eventually. Until then — me. | Рано или поздно выигрывают все. А пока — я. |
| `dialog.burmaldaholic.loan.greeting.5` | Friendly rates for friendly faces. Yours will do. | Дружеский процент для дружеского лица. Ваше сойдёт. |
| `dialog.burmaldaholic.loan.given.1` | Pleasure doing business. Don't make me miss you. | Приятно иметь дело. Не заставляйте меня скучать. |
| `dialog.burmaldaholic.loan.given.2` | Spend it wisely. Or at least quickly. | Потратьте с умом. Ну или хотя бы быстро. |
| `dialog.burmaldaholic.loan.given.3` | That's the easy part. The hard part is giving it back. | Это была лёгкая часть. Сложная — вернуть. |
| `dialog.burmaldaholic.loan.given.4` | Red or black — just come back. | Хоть на красное, хоть на чёрное — главное, возвращайтесь. |
| `dialog.burmaldaholic.loan.repaid.1` | Paid in full. A rare and beautiful thing. | Погашено полностью. Редкое и прекрасное зрелище. |
| `dialog.burmaldaholic.loan.repaid.2` | You're a person of your word. I'll remember that. | Вы человек слова. Я это запомню. |
| `dialog.burmaldaholic.loan.repaid.3` | And here I was, sharpening my pencil. | А я уже карандаш заточил. |
| `dialog.burmaldaholic.loan.repaid.4` | Come back anytime. I mean it. Financially. | Заходите ещё. Я серьёзно. В финансовом смысле. |
| `dialog.burmaldaholic.loan.overdue.1` | Well, well. Look who remembered where I live. | Так-так. Смотри-ка, кто вспомнил дорогу. |
| `dialog.burmaldaholic.loan.overdue.2` | You're late. I don't like late. My associates like it even less. | Платёж просрочен. Я этого не люблю. Мои ребята — ещё больше. |
| `dialog.burmaldaholic.loan.overdue.3` | We were on "you" terms. Now we're on "you owe" terms. | Мы были на «вы». Теперь на «ты». И на «где мои фишки». |
| `dialog.burmaldaholic.loan.overdue.4` | Every day you're late, the number gets bigger. Math is cruel. | Каждый день просрочки — число растёт. Математика жестока. |
| `dialog.burmaldaholic.loan.overdue.5` | Pay up and we forget this ever happened. Mostly. | Плати — и забудем об этом. Почти. |
| `dialog.burmaldaholic.loan.refuse_vip.1` | That kind of money is for people with a better card than yours. | Такие суммы — для тех, у кого карта покруче вашей. |
| `dialog.burmaldaholic.loan.refuse_vip.2` | Play a bit more first. I like to know my clients. | Сначала поиграйте побольше. Я люблю знать своих клиентов. |
| `dialog.burmaldaholic.loan.refuse_vip.3` | Ambitious. I like it. The answer's still no. | Амбициозно. Мне нравится. Но ответ — нет. |
| `dialog.burmaldaholic.loan.piglin_greeting.1` | Gold? Chips? I lend both. Returns are… mandatory. | Золото? Фишки? Одалживаю и то, и другое. Возврат… обязателен. |
| `dialog.burmaldaholic.loan.piglin_greeting.2` | Snort. You look like someone who needs a loan. | Хрю. Выглядишь как тот, кому нужен займ. |

### Debt Collector dialogue

| Key | EN | RU |
|-----|----|----|
| `dialog.burmaldaholic.collector.demand.1` | Evening. We're from the bank. You know why we're here. | Добрый вечер. Мы из банка. Знаешь ведь, зачем мы пришли. |
| `dialog.burmaldaholic.collector.demand.2` | %1$s. That's the number. Say it with us. | %1$s. Вот такая цифра. Повтори за нами. |
| `dialog.burmaldaholic.collector.demand.3` | Nice house. Would be a shame if we had to wait inside it. | Хороший дом. Жалко будет, если придётся ждать внутри. |
| `dialog.burmaldaholic.collector.demand.4` | The boss says hi. The boss also says pay. | Шеф передаёт привет. И ещё шеф говорит: плати. |
| `dialog.burmaldaholic.collector.demand.5` | We can do this the easy way, or the other way. We brought equipment for both. | Можно по-хорошему, можно по-другому. Инструмент есть для обоих вариантов. |
| `dialog.burmaldaholic.collector.hostile.1` | Wrong answer. | Неправильный ответ. |
| `dialog.burmaldaholic.collector.hostile.2` | Okay. The other way it is. | Ладно. Значит, по-другому. |
| `dialog.burmaldaholic.collector.hostile.3` | Boys, we've got a non-payer. | Парни, платить не хочет. |
| `dialog.burmaldaholic.collector.hostile.4` | Running only raises the late fee. | Бегать бесполезно — только пени набегут. |
| `dialog.burmaldaholic.collector.hostile.5` | Nothing personal. It's accounting. | Ничего личного. Это бухгалтерия. |
| `dialog.burmaldaholic.collector.paid.1` | Pleasure doing business. We were never here. | Приятно было иметь дело. Нас тут не было. |
| `dialog.burmaldaholic.collector.paid.2` | See? Nobody got hurt. Well, your wallet. | Видишь? Никто не пострадал. Ну, кроме кошелька. |
| `dialog.burmaldaholic.collector.paid.3` | Account closed. Have a nice day. | Счёт закрыт. Хорошего дня. |
| `dialog.burmaldaholic.collector.paid.4` | Boss will be pleased. Let's go, boys. | Шеф будет доволен. Уходим, парни. |
| `dialog.burmaldaholic.collector.partial.1` | Half now, half later. We'll be back tomorrow. | Половина сейчас, половина потом. Завтра зайдём. |
| `dialog.burmaldaholic.collector.partial.2` | It's a start. We know where you live. | Для начала сойдёт. Адрес мы знаем. |
| `dialog.burmaldaholic.collector.partial.3` | Good faith payment accepted. Don't make it the last one. | Жест доброй воли принят. Пусть он будет не последним. |
| `dialog.burmaldaholic.collector.repossess.1` | We'll take that. And that. Thanks for your cooperation. | Это мы заберём. И это. Спасибо за сотрудничество. |
| `dialog.burmaldaholic.collector.repossess.2` | Consider it a down payment. | Считай, первый взнос. |
| `dialog.burmaldaholic.collector.repossess.3` | Should've paid. Would've been cheaper. | Надо было платить. Вышло бы дешевле. |
| `dialog.burmaldaholic.accountant.audit.1` | Let's go over your expenses. | Давай пройдёмся по твоим расходам. |
| `dialog.burmaldaholic.accountant.audit.2` | According to my records, you're in trouble. | Судя по моим записям, у тебя проблемы. |
| `dialog.burmaldaholic.accountant.audit.3` | Debit: you. Credit: also you. | Дебет: ты. Кредит: тоже ты. |
| `dialog.burmaldaholic.enforcer.line.1` | Hrrm. Pay. | Кхм. Плати. |
| `dialog.burmaldaholic.enforcer.line.2` | I don't do math. I do follow-ups. | Я не считаю. Я напоминаю. |
| `dialog.burmaldaholic.enforcer.line.3` | Boss sent me. That's bad news for you. | Меня прислал шеф. Для тебя это плохая новость. |

### Death messages

| Key | EN | RU |
|-----|----|----|
| `death.attack.burmaldaholic.debt_collection` | %1$s was collected | %1$s был взыскан |
| `death.attack.burmaldaholic.debt_collection.player` | %1$s was collected by %2$s | %1$s был взыскан: %2$s |
| `death.attack.burmaldaholic.audit` | %1$s was audited to death | %1$s не пережил аудит |
| `death.attack.burmaldaholic.audit.player` | %1$s was audited to death by %2$s | %1$s не пережил аудит: %2$s |
| `death.attack.burmaldaholic.soul_wager` | %1$s bet their soul on a coin flip and lost | %1$s поставил душу на монетку и проиграл |
| `death.attack.burmaldaholic.soul_wager.player` | %1$s bet their soul on a coin flip and lost | %1$s поставил душу на монетку и проиграл |

---

## chaos

| Key | EN | RU |
|-----|----|----|
| `msg.burmaldaholic.chaos.chip_shower.title` | Chip shower! | Фишкопад! |
| `msg.burmaldaholic.chaos.chip_shower.subtitle` | The house is feeling generous | Заведение сегодня щедрое |
| `msg.burmaldaholic.chaos.chip_shower.chat` | Chips rain down around you: %1$s | Вокруг вас сыплются фишки: %1$s |
| `msg.burmaldaholic.chaos.lucky_buff.title` | Blessed! | Благословение! |
| `msg.burmaldaholic.chaos.lucky_buff.subtitle` | %1$s for %2$s | %1$s на %2$s |
| `msg.burmaldaholic.chaos.diamond_rain.title` | Diamond rain! | Алмазный дождь! |
| `msg.burmaldaholic.chaos.diamond_rain.subtitle` | Quick, grab them! | Хватайте скорее! |
| `msg.burmaldaholic.chaos.xp_fountain.title` | XP fountain! | Фонтан опыта! |
| `msg.burmaldaholic.chaos.xp_fountain.subtitle` | Knowledge is power. Also levels. | Знание — сила. И уровни. |
| `msg.burmaldaholic.chaos.curse.title` | Cursed! | Проклятие! |
| `msg.burmaldaholic.chaos.curse.subtitle` | %1$s for %2$s | %1$s на %2$s |
| `msg.burmaldaholic.chaos.mob_wave.title` | Mob wave! | Нашествие мобов! |
| `msg.burmaldaholic.chaos.mob_wave.subtitle` | Uninvited guests incoming | Незваные гости на подходе |
| `msg.burmaldaholic.chaos.mob_wave.chat` | The house sent some bouncers. They didn't check your VIP status. | Заведение прислало вышибал. ВИП-статус они не проверяют. |
| `msg.burmaldaholic.chaos.random_teleport.title` | Whoosh! | Вжух! |
| `msg.burmaldaholic.chaos.random_teleport.subtitle` | You've been moved %1$s | Вас перенесло на %1$s |
| `msg.burmaldaholic.chaos.random_teleport.chat` | Chaos teleported you to %1$s | Хаос перенёс вас в точку %1$s |
| `msg.burmaldaholic.chaos.weather.title` | The sky changes its mind | Небо передумало |
| `msg.burmaldaholic.chaos.weather.clear` | The clouds part | Тучи расходятся |
| `msg.burmaldaholic.chaos.weather.rain` | It starts to rain | Начинается дождь |
| `msg.burmaldaholic.chaos.weather.thunder` | Thunder rolls in | Надвигается гроза |
| `msg.burmaldaholic.chaos.deferred` | Something is waiting for you to finish your game… | Что-то ждёт, пока вы доиграете… |
| `msg.burmaldaholic.chaos.golden_hour.title` | GOLDEN HOUR | «ЗОЛОТОЙ ЧАС» |
| `msg.burmaldaholic.chaos.golden_hour.subtitle` | Winnings ×%1$s for %2$s | Выигрыши ×%1$s · время: %2$s |
| `msg.burmaldaholic.chaos.golden_hour.start` | Golden Hour has begun! All casino winnings are multiplied by %1$s for %2$s. | Начался «Золотой час»! На %2$s все выигрыши в казино умножаются на %1$s. |
| `msg.burmaldaholic.chaos.golden_hour.start_by` | %1$s rang in Golden Hour with three clocks! | «Золотой час» объявлен — трое часов у игрока %1$s! |
| `msg.burmaldaholic.chaos.golden_hour.ending` | Golden Hour ends in %1$s | «Золотой час» закончится через %1$s |
| `msg.burmaldaholic.chaos.golden_hour.end` | Golden Hour is over. Back to regular odds. | «Золотой час» окончен. Шансы снова обычные. |
| `msg.burmaldaholic.chaos.golden_hour.bonus` | Golden Hour bonus: +%1$s | Бонус «Золотого часа»: +%1$s |
| `msg.burmaldaholic.chaos.golden_hour.cap` | You've hit this Golden Hour's bonus limit (%1$s) | Лимит бонусов этого «Золотого часа» исчерпан (%1$s) |
| `msg.burmaldaholic.chaos.big_win_buff` | Luck rubs off: %1$s | Удача заразительна: %1$s |
| `msg.burmaldaholic.chaos.admin.result.started` | Chaos event started | Хаос-событие запущено |
| `msg.burmaldaholic.chaos.admin.result.deferred` | Chaos event deferred until the menu closes | Хаос-событие отложено до закрытия меню |
| `msg.burmaldaholic.chaos.admin.result.skipped` | Chaos event skipped: not safe here right now | Хаос-событие пропущено: здесь сейчас небезопасно |
| `msg.burmaldaholic.chaos.admin.result.cooldown` | Chaos event is on cooldown | Хаос-событие перезаряжается |
| `msg.burmaldaholic.chaos.admin.result.disabled` | Chaos events are disabled | Хаос-события отключены |
| `msg.burmaldaholic.chaos.admin.result_for` | %1$s: %2$s | %1$s: %2$s |
| `msg.burmaldaholic.chaos.admin.unknown_event` | Unknown chaos event: %1$s | Нет такого хаос-события: %1$s |
| `msg.burmaldaholic.chaos.admin.golden_hour_stopped` | Golden Hour stopped | «Золотой час» остановлен |
| `msg.burmaldaholic.chaos.admin.golden_hour_inactive` | Golden Hour is not active | «Золотой час» сейчас не идёт |

---

## lastchance

| Key | EN | RU |
|-----|----|----|
| `msg.burmaldaholic.lastchance.flip_title` | Last Chance… | «Последний шанс»… |
| `msg.burmaldaholic.lastchance.heads_title` | HEADS! | ОРЁЛ! |
| `msg.burmaldaholic.lastchance.heads_subtitle` | Death blinked first | Смерть моргнула первой |
| `msg.burmaldaholic.lastchance.tails_title` | TAILS… | РЕШКА… |
| `msg.burmaldaholic.lastchance.tails_subtitle` | Not this time | Не в этот раз |
| `msg.burmaldaholic.lastchance.success` | You flipped a coin with Death and won. Revived at half health. Fee: %1$s | Вы сыграли со Смертью в монетку и выиграли. Воскрешение с половиной здоровья. Комиссия: %1$s |
| `msg.burmaldaholic.lastchance.failure` | You flipped a coin with Death and lost. | Вы сыграли со Смертью в монетку и проиграли. |
| `msg.burmaldaholic.lastchance.broadcast_success` | %1$s flipped a coin with Death — and won! | Монетка против Смерти — %1$s остаётся в живых! |
| `msg.burmaldaholic.lastchance.broadcast_failure` | %1$s flipped a coin with Death. Death called tails. | Монетка против Смерти — у игрока %1$s выпала решка. |
| `msg.burmaldaholic.lastchance.ready` | Last Chance has recharged | «Последний шанс» перезарядился |
| `msg.burmaldaholic.lastchance.cooldown_started` | Last Chance recharges in %1$s | «Последний шанс» перезарядится через %1$s |
| `msg.burmaldaholic.lastchance.hardcore.title` | HIGH STAKES | ВА-БАНК |
| `msg.burmaldaholic.lastchance.hardcore.success` | You live. The price: every chip you had and one heart, forever. | Вы живы. Цена — все ваши фишки и одно сердце навсегда. |
| `msg.burmaldaholic.lastchance.hardcore.scar` | Your maximum health is permanently reduced by %1$s | Максимум здоровья навсегда уменьшен на %1$s |
| `msg.burmaldaholic.lastchance.hardcore.not_eligible` | Too poor or too scarred for another deal with Death | Для новой сделки со Смертью не хватает фишек или здоровья |
| `msg.burmaldaholic.lastchance.hardcore.broadcast` | %1$s bought their life back with everything they had | Игрок %1$s выкупил свою жизнь за всё, что имел |

---

## worldgen

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.worldgen.village_casino` | Lucky Villager Casino | Казино «Счастливый житель» |
| `gui.burmaldaholic.worldgen.piglin_parlor` | Piglin Parlor | Пиглинский салон |
| `gui.burmaldaholic.worldgen.high_roller` | High Roller Lounge | Лаунж хайроллеров |
| `msg.burmaldaholic.worldgen.entered` | Welcome to %1$s | Добро пожаловать в %1$s |
| `msg.burmaldaholic.worldgen.entered_subtitle` | Please gamble responsibly. Or at least entertainingly. | Играйте ответственно. Ну или хотя бы зрелищно. |
| `gui.burmaldaholic.croupier.title` | Croupier | Крупье |
| `gui.burmaldaholic.croupier.shop` | Buy scratch cards and coins | Купить лотерею и монетки |
| `dialog.burmaldaholic.croupier.greeting.1` | Welcome! The tables are warm and the odds are… fair. Mostly. | Добро пожаловать! Столы разогреты, шансы… честные. В основном. |
| `dialog.burmaldaholic.croupier.greeting.2` | Feeling lucky? A scratch card is a gentle start. | Чувствуете удачу? Начните с лотереи — это не больно. |
| `dialog.burmaldaholic.croupier.greeting.3` | Hrm. (The croupier adjusts his bow tie meaningfully.) | Хм. (Крупье многозначительно поправляет бабочку.) |
| `dialog.burmaldaholic.piglin_dealer.greeting.1` | Snort! Gold on the table, gold in the pocket. Mine, mostly. | Хрю! Золото на стол, золото в карман. В основном в мой. |
| `dialog.burmaldaholic.piglin_dealer.greeting.2` | You wear no gold. Bold. The dice don't care. | На тебе нет золота. Смело. Костям без разницы. |
| `dialog.burmaldaholic.piglin_dealer.greeting.3` | House rules: no mining the tables. Everything else — allowed. | Правила заведения: столы не копать. Остальное — можно. |
| `dialog.burmaldaholic.shulker_croupier.greeting.1` | (The shulker opens its shell just enough to take your bet.) | (Шалкер приоткрывает панцирь ровно настолько, чтобы принять ставку.) |
| `dialog.burmaldaholic.shulker_croupier.greeting.2` | High rollers only. You'll float out if you lose. Joke. Mostly. | Только хайроллеры. Проиграете — улетите. Шутка. В основном. |
| `gui.burmaldaholic.worldgen.shop.offer` | %1$s — %2$s | %1$s — %2$s |
| `msg.burmaldaholic.worldgen.bought` | Bought %1$s for %2$s | Куплено: %1$s за %2$s |

---

## multiplayer

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.charter.title` | Casino Charter | Лицензия казино |
| `gui.burmaldaholic.charter.overview` | Overview | Обзор |
| `gui.burmaldaholic.charter.tables` | Tables | Столы |
| `gui.burmaldaholic.charter.bankroll` | Bankroll | Касса |
| `gui.burmaldaholic.charter.stats` | Stats | Статистика |
| `gui.burmaldaholic.charter.bankroll_value` | Bankroll: %1$s | Касса заведения: %1$s |
| `gui.burmaldaholic.charter.reserved` | Reserved for open bets: %1$s | Зарезервировано под ставки: %1$s |
| `gui.burmaldaholic.charter.available` | Available: %1$s | Доступно: %1$s |
| `gui.burmaldaholic.charter.deposit` | Deposit to bankroll | Пополнить кассу |
| `gui.burmaldaholic.charter.withdraw` | Withdraw from bankroll | Забрать из кассы |
| `gui.burmaldaholic.charter.status_open` | Status: open | Статус: открыто |
| `gui.burmaldaholic.charter.status_broke` | Status: closed — bankroll too low | Статус: закрыто — в кассе пусто |
| `gui.burmaldaholic.charter.today` | Today: handle %1$s · paid out %2$s · profit %3$s | Сегодня: оборот %1$s · выплачено %2$s · прибыль %3$s |
| `gui.burmaldaholic.charter.total` | All time: handle %1$s · paid out %2$s · profit %3$s | Всего: оборот %1$s · выплачено %2$s · прибыль %3$s |
| `gui.burmaldaholic.charter.rake` | Poker rake collected: %1$s | Собрано рейка: %1$s |
| `gui.burmaldaholic.charter.table_row` | %1$s at %2$s | %1$s — %2$s |
| `gui.burmaldaholic.charter.table_open` | Open | Открыт |
| `gui.burmaldaholic.charter.table_min` | Minimum bet | Мин. ставка |
| `gui.burmaldaholic.charter.table_max` | Maximum bet | Макс. ставка |
| `gui.burmaldaholic.charter.table_bots` | Poker bots | Покерные боты |
| `gui.burmaldaholic.charter.table_slots_buy` | Slots: bonus buy | Автоматы: покупка бонуса |
| `gui.burmaldaholic.charter.table_slots_autoplay` | Slots: autoplay | Автоматы: автоигра |
| `gui.burmaldaholic.charter.link_tables` | Link unlinked tables in range | Привязать столы в зоне |
| `gui.burmaldaholic.charter.no_tables` | No tables yet. Place some inside your claim. | Столов пока нет. Поставьте их в своей зоне. |
| `msg.burmaldaholic.multiplayer.charter_placed` | Casino licensed! License fee: %1$s. Your claim: %2$s around the charter. | Казино получило лицензию! Сбор: %1$s. Ваша зона — %2$s вокруг лицензии. |
| `msg.burmaldaholic.multiplayer.charter_fee_missing` | The license fee is %1$s. Come back with money. | Лицензионный сбор — %1$s. Возвращайтесь с деньгами. |
| `msg.burmaldaholic.multiplayer.charter_overlap` | Too close to another casino or spawn | Слишком близко к другому казино или к точке спавна |
| `msg.burmaldaholic.multiplayer.charter_limit` | You already own the maximum number of casinos | У вас уже максимум казино |
| `msg.burmaldaholic.multiplayer.charter_removed` | Charter removed. %1$s returned from the bankroll; your tables are now inactive. | Лицензия снята. Из кассы возвращено %1$s; ваши столы неактивны. |
| `msg.burmaldaholic.multiplayer.table_linked` | %1$s is now part of your casino | %1$s теперь часть вашего казино |
| `msg.burmaldaholic.multiplayer.table_protected` | This table belongs to %1$s's casino | Этот стол принадлежит казино игрока %1$s |
| `msg.burmaldaholic.multiplayer.table_inactive` | This table has no license. Its owner must link it to a charter. | У этого стола нет лицензии. Владелец должен привязать его к лицензии казино. |
| `msg.burmaldaholic.multiplayer.casino_broke` | Your casino is closed: the bankroll can't cover the minimum bets. Top it up to reopen. | Ваше казино закрыто: в кассе не хватает даже на минимальные ставки. Пополните, чтобы открыться. |
| `msg.burmaldaholic.multiplayer.casino_reopened` | Your casino is open again | Ваше казино снова открыто |
| `msg.burmaldaholic.multiplayer.bankroll_deposit` | Bankroll +%1$s (now %2$s) | Касса +%1$s (теперь %2$s) |
| `msg.burmaldaholic.multiplayer.bankroll_withdraw` | Bankroll −%1$s (now %2$s) | Касса −%1$s (теперь %2$s) |
| `msg.burmaldaholic.multiplayer.bankroll_reserved` | You can only withdraw %1$s — the rest covers open bets | Можно забрать только %1$s — остальное покрывает открытые ставки |
| `msg.burmaldaholic.multiplayer.entered_casino` | You enter %1$s's casino | Вы входите в казино игрока %1$s |
| `msg.burmaldaholic.multiplayer.seat_taken` | That seat is taken | Это место занято |
| `msg.burmaldaholic.multiplayer.table_left_distance` | You walked away from the table | Вы отошли от стола |
| `msg.burmaldaholic.multiplayer.charter_foreign` | This charter belongs to %1$s's casino | Это лицензия казино игрока %1$s |
| `msg.burmaldaholic.multiplayer.casinos_disabled` | Player casinos are disabled on this server | Казино игроков на этом сервере отключены |
| `msg.burmaldaholic.multiplayer.table_saved` | %1$s: settings saved | %1$s: настройки сохранены |
| `msg.burmaldaholic.multiplayer.linked_none` | No unlinked tables in your claim | В вашей зоне нет непривязанных столов |
| `msg.burmaldaholic.multiplayer.linked_count` | Tables linked: %1$s | Привязано столов: %1$s |
| `gui.burmaldaholic.multiplayer.owner` | Owner: %1$s | Владелец: %1$s |
| `gui.burmaldaholic.multiplayer.claim` | Claim: %1$s around %2$s | Зона: %1$s вокруг %2$s |
| `gui.burmaldaholic.multiplayer.tables_count` | Tables: %1$s | Столы: %1$s |
| `gui.burmaldaholic.multiplayer.limit_hint` | Leave Min / Max empty to use the game's own limits. Max is capped at %1$s. | Оставьте мин. и макс. пустыми — будут лимиты самой игры. Максимум — не выше %1$s. |
| `gui.burmaldaholic.multiplayer.error_min_max` | The minimum bet can't be above the maximum | Минимальная ставка не может быть больше максимальной |
| `gui.burmaldaholic.multiplayer.error_over_global` | Bet limits can't be above %1$s | Лимиты ставок не могут быть выше %1$s |
| `gui.burmaldaholic.multiplayer.rounds` | Rounds played: %1$s today, %2$s all time | Сыграно раундов: сегодня %1$s, всего %2$s |
| `gui.burmaldaholic.multiplayer.toggle` | %1$s: %2$s | %1$s: %2$s |
| `gui.burmaldaholic.multiplayer.save` | Save | Сохранить |
| `gui.burmaldaholic.multiplayer.page` | Page %1$s of %2$s | Страница %1$s из %2$s |
| `gui.burmaldaholic.multiplayer.negative` | −%1$s | −%1$s |
| `gui.burmaldaholic.multiplayer.spectate` | %1$s: players %2$s/%3$s · in play %4$s | %1$s: игроков %2$s/%3$s · в игре %4$s |

---

## bots

⚠ Added 2026-09 (BOTS.md §11, pre-merged by the architect; BOTS.md stays normative for arguments and notes).

### UI

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.bots.display` | [BOT] %1$s | [БОТ] %1$s |
| `gui.burmaldaholic.bots.display_level` | [BOT] %1$s · %2$s | [БОТ] %1$s · %2$s |
| `gui.burmaldaholic.bots.level_poker` | %1$s (%2$s) | %1$s (%2$s) |
| `gui.burmaldaholic.bots.level.easy` | Easy | Лёгкий |
| `gui.burmaldaholic.bots.level.normal` | Normal | Нормальный |
| `gui.burmaldaholic.bots.level.hard` | Hard | Сложный |
| `gui.burmaldaholic.bots.level.mixed` | Mixed | Вперемешку |
| `gui.burmaldaholic.bots.level.easy.short` | E | Л |
| `gui.burmaldaholic.bots.level.normal.short` | N | Н |
| `gui.burmaldaholic.bots.level.hard.short` | H | С |
| `gui.burmaldaholic.bots.style.easy` | Wild | Азартный |
| `gui.burmaldaholic.bots.style.normal` | Steady | Ровный |
| `gui.burmaldaholic.bots.style.hard` | Cool-headed | Хладнокровный |
| `gui.burmaldaholic.bots.style.mixed` | Mixed | Вперемешку |
| `gui.burmaldaholic.bots.personality.rock` | Rock | Скала |
| `gui.burmaldaholic.bots.personality.rock.desc` | rarely bluffs | блефует редко |
| `gui.burmaldaholic.bots.personality.station` | Calling Station | Колл-станция |
| `gui.burmaldaholic.bots.personality.station.desc` | calls everything | коллирует всё подряд |
| `gui.burmaldaholic.bots.personality.maniac` | Maniac | Маньяк |
| `gui.burmaldaholic.bots.personality.maniac.desc` | raises everything | рейзит всё подряд |
| `gui.burmaldaholic.bots.personality.tag` | Tight-Aggressive | Тайт-агрессор |
| `gui.burmaldaholic.bots.personality.tag.desc` | plays by the book | играет по учебнику |
| `gui.burmaldaholic.bots.personality.lag` | Loose-Aggressive | Луз-агрессор |
| `gui.burmaldaholic.bots.personality.lag.desc` | keeps up the pressure | давит без передышки |
| `gui.burmaldaholic.bots.personality_line` | %1$s — %2$s | %1$s — %2$s |
| `gui.burmaldaholic.bots.betstyle.roulette.rock` | Red Lover | Любитель красного |
| `gui.burmaldaholic.bots.betstyle.roulette.station` | Dozen Grinder | Дюжинщик |
| `gui.burmaldaholic.bots.betstyle.roulette.maniac` | Sprinkler | Разбрасыватель |
| `gui.burmaldaholic.bots.betstyle.roulette.tag` | Lucky Number | Счастливое число |
| `gui.burmaldaholic.bots.betstyle.roulette.lag` | Martingale Fan | Фанат мартингейла |
| `gui.burmaldaholic.bots.betstyle.craps.rock` | Dark Side | Тёмная сторона |
| `gui.burmaldaholic.bots.betstyle.craps.station` | Field Fan | Любитель филда |
| `gui.burmaldaholic.bots.betstyle.craps.maniac` | Come Ladder | Лестница кам-ставок |
| `gui.burmaldaholic.bots.betstyle.craps.tag` | Right-Way Grinder | Честный пасс-лайн |
| `gui.burmaldaholic.bots.betstyle.craps.lag` | Pass and Field | Пасс и филд |
| `gui.burmaldaholic.bots.betstyle.baccarat.rock` | Banker Only | Только Банкир |
| `gui.burmaldaholic.bots.betstyle.baccarat.station` | Trend Follower | Идёт за серией |
| `gui.burmaldaholic.bots.betstyle.baccarat.maniac` | Tie Hunter | Охотник за ничьей |
| `gui.burmaldaholic.bots.betstyle.baccarat.tag` | Banker, No Frills | Банкир без изысков |
| `gui.burmaldaholic.bots.betstyle.baccarat.lag` | Chop Chaser | Против серии |
| `gui.burmaldaholic.bots.settings.open` | Table settings… | Настройки стола… |
| `gui.burmaldaholic.bots.settings.info` | Table info | Об этом столе |
| `gui.burmaldaholic.bots.settings.title` | Table settings — %1$s | Настройки стола — %1$s |
| `gui.burmaldaholic.bots.settings.defaults_title` | Table defaults — %1$s | Настройки по умолчанию — %1$s |
| `gui.burmaldaholic.bots.settings.host` | Host: %1$s | Хозяин стола: %1$s |
| `gui.burmaldaholic.bots.settings.players` | Players | Кто играет |
| `gui.burmaldaholic.bots.policy.humans_only` | Humans only | Только люди |
| `gui.burmaldaholic.bots.policy.mixed` | Humans + bots | Люди и боты |
| `gui.burmaldaholic.bots.policy.bots_only` | Just me and bots | Только я и боты |
| `gui.burmaldaholic.bots.settings.count` | Bots | Боты |
| `gui.burmaldaholic.bots.settings.count_max` | max %1$s | макс. %1$s |
| `gui.burmaldaholic.bots.settings.difficulty` | Bot difficulty | Уровень ботов |
| `gui.burmaldaholic.bots.settings.style` | Bot style | Стиль ботов |
| `gui.burmaldaholic.bots.settings.keep_free` | Keep a seat free for walk-ins | Держать место для новых игроков |
| `gui.burmaldaholic.bots.settings.chatter` | Bot chatter | Болтовня ботов |
| `gui.burmaldaholic.bots.settings.speed` | Bot speed | Темп ботов |
| `gui.burmaldaholic.bots.speed.normal` | Normal | Обычный |
| `gui.burmaldaholic.bots.speed.fast` | Fast | Быстрый |
| `gui.burmaldaholic.bots.speed.instant` | Instant | Мгновенный |
| `gui.burmaldaholic.bots.settings.speed_hint` | Fast and Instant work only in Just me and bots | Быстрый и мгновенный темп — только в режиме «Только я и боты» |
| `gui.burmaldaholic.bots.settings.save_defaults` | Save as table defaults | Сохранить как настройки стола |
| `gui.burmaldaholic.bots.settings.save` | Save — applies from the next round | Сохранить — со следующего раунда |
| `gui.burmaldaholic.bots.settings.save_short` | Save | Сохранить |
| `gui.burmaldaholic.bots.summary.humans_only` | Humans only · %1$s | Только люди · %1$s |
| `gui.burmaldaholic.bots.summary.mixed` | Humans + %1$s · %2$s · %3$s | Люди + %1$s · %2$s · %3$s |
| `gui.burmaldaholic.bots.summary.bots_only` | %1$s + %2$s · %3$s · private | %1$s + %2$s · %3$s · закрытый |
| `gui.burmaldaholic.bots.summary.private` | Private | Закрытый |
| `gui.burmaldaholic.bots.summary.open` | Open to all | Открытый |
| `gui.burmaldaholic.bots.pending` | New settings from the next round | Новые настройки — со следующего раунда |
| `gui.burmaldaholic.bots.seat_line` | Seat %1$s: %2$s · %3$s · %4$s | Место %1$s: %2$s · %3$s · %4$s |
| `gui.burmaldaholic.bots.seat_reserved` | Reserved for %1$s | Занято для игрока %1$s |
| `gui.burmaldaholic.bots.seat_leaving` | Leaves after this round | Уйдёт после раунда |
| `gui.burmaldaholic.bots.thinking` | Thinking… | Думает… |
| `gui.burmaldaholic.bots.watching` | Watching | Наблюдает |
| `gui.burmaldaholic.bots.virtual_bets` | Bots bet (for fun): %1$s | Ставки ботов (понарошку): %1$s |
| `gui.burmaldaholic.bots.virtual_tooltip` | Bot chips here are not real: they never win or lose anyone's money | Фишки ботов здесь ненастоящие: они не выигрывают и не проигрывают ничьих денег |
| `gui.burmaldaholic.bots.luck_only` | Luck only — bots play exactly like you | Только удача — боты играют так же, как вы |
| `gui.burmaldaholic.bots.uth_edge` | Costs this bot about %1$s of the Ante | Обходится этому боту примерно в %1$s от анте |
| `gui.burmaldaholic.bots.heat_hard_only` | Only Hard bots will play you today | Сегодня с вами играют только сложные боты |
| `gui.burmaldaholic.bots.wallet_line` | Winnings from bots today: %1$s / %2$s | Выигрыш у ботов сегодня: %1$s / %2$s |
| `gui.burmaldaholic.bots.stats_line` | Vs bots: %1$s rounds · net %2$s | С ботами: раундов %1$s · итог %2$s |
| `gui.burmaldaholic.bots.rules.fair` | Bots see only what you see and never touch the shuffle, the dice or the wheel. | Боты видят только то, что видите вы, и никак не влияют на тасовку, кости и колесо. |
| `gui.burmaldaholic.bots.rules.tell` | Tip: Easy bots think longer when they hold a strong hand. | Подсказка: лёгкие боты думают дольше, когда у них сильная рука. |
| `gui.burmaldaholic.bots.rules.money` | Bot rounds: half VIP credit, no cashback, no Golden Hour bonus, no streak. | Игра с ботами: оборот для ВИП засчитывается наполовину, без кешбэка, без бонуса «Золотого часа» и без серий. |
| `gui.burmaldaholic.bots.private.open` | Private table… | Закрытый стол… |
| `gui.burmaldaholic.bots.private.toggle` | Private table | Закрытый стол |
| `gui.burmaldaholic.bots.private.status` | Private: %1$s · Invited: %2$s | Закрытый: %1$s · Приглашены: %2$s |
| `gui.burmaldaholic.bots.private.yes` | yes | да |
| `gui.burmaldaholic.bots.private.no` | no | нет |
| `gui.burmaldaholic.bots.private.make_private` | Make private | Сделать закрытым |
| `gui.burmaldaholic.bots.private.make_public` | Make public | Сделать открытым |
| `gui.burmaldaholic.bots.private.invite` | Invite a player… | Пригласить игрока… |
| `gui.burmaldaholic.bots.private.invite_submit` | Invite | Пригласить |
| `gui.burmaldaholic.bots.private.uninvite` | Remove an invite… | Отозвать приглашение… |
| `gui.burmaldaholic.bots.private.uninvite_submit` | Remove | Отозвать |
| `gui.burmaldaholic.bots.private.manage` | Invited… | Приглашённые… |
| `gui.burmaldaholic.bots.private.none_invited` | Nobody invited yet | Пока никто не приглашён |
| `gui.burmaldaholic.bots.private.none_nearby` | No players nearby to invite | Рядом нет игроков для приглашения |
| `gui.burmaldaholic.bots.private.confirm` | Invite %1$s to your %2$s table? | Пригласить игрока %1$s за ваш стол (%2$s)? |
| `gui.burmaldaholic.bots.let_in` | Let %1$s in (Humans + bots) | Пустить: %1$s («Люди и боты») |
| `gui.burmaldaholic.bots.pvp.opponents` | Opponents | Соперники |
| `gui.burmaldaholic.bots.pvp.a_player` | A player | Игрок |
| `gui.burmaldaholic.bots.pvp.a_bot` | A bot | Бот |
| `gui.burmaldaholic.bots.pvp.play_vs_bots` | Play vs bots… | Игра с ботами… |
| `gui.burmaldaholic.bots.pvp.fill_now` | Fill with bots | Добавить ботов |
| `gui.burmaldaholic.bots.charter.bots` | Bots | Боты |
| `gui.burmaldaholic.bots.charter.mode.off` | Off | Выкл. |
| `gui.burmaldaholic.bots.charter.mode.atmosphere` | Atmosphere only | Только для атмосферы |
| `gui.burmaldaholic.bots.charter.mode.allowed` | Allowed (paid from the bankroll) | Разрешены (за счёт кассы) |
| `gui.burmaldaholic.bots.charter.host_may_change` | Players may change seating | Игроки могут менять состав |
| `gui.burmaldaholic.bots.charter.max_bots` | Max bots | Макс. ботов |
| `gui.burmaldaholic.bots.charter.allow_private` | Allow private tables | Разрешить закрытые столы |
| `gui.burmaldaholic.bots.charter.defaults` | Table defaults… | Настройки по умолчанию… |
| `gui.burmaldaholic.bots.charter.stacks_out` | Bot stacks out: %1$s | Фишки у ботов: %1$s |
| `gui.burmaldaholic.bots.charter.results_today` | Bot results today: %1$s | Итог ботов за сегодня: %1$s |
| `gui.burmaldaholic.bots.charter.bankroll_short` | Bots can't afford a seat: the bankroll is too low | Ботам не на что сесть: в кассе мало денег |
| `gui.burmaldaholic.menu.settings.bot_chatter` | Bot chatter | Болтовня ботов |
| `gui.burmaldaholic.bots.admin.title` | Bots | Боты |
| `gui.burmaldaholic.bots.admin.line` | %1$s at %2$s · %3$s · %4$s | %1$s — %2$s · %3$s · %4$s |
| `gui.burmaldaholic.bots.admin.clear_all` | Send all bots home | Отправить ботов домой |
| `gui.burmaldaholic.bots.admin.reset_heat` | Reset a player's bot limits… | Сбросить лимиты игрока… |
| `gui.burmaldaholic.bots.nameplate` | %1$s · %2$s | %1$s · %2$s |
| `gui.burmaldaholic.bots.menu.tab` | Bots | Боты |
| `gui.burmaldaholic.bots.menu.chatter_on` | Bot chatter: on | Болтовня ботов: включена |
| `gui.burmaldaholic.bots.menu.chatter_off` | Bot chatter: muted | Болтовня ботов: выключена |
| `gui.burmaldaholic.bots.menu.mute` | Mute bot chatter | Заглушить ботов |
| `gui.burmaldaholic.bots.menu.unmute` | Hear bot chatter | Слушать ботов |
| `gui.burmaldaholic.bots.menu.table` | %1$s: %2$s | %1$s: %2$s |
| `gui.burmaldaholic.bots.settings.access` | Who may sit | Кто может сесть |
| `gui.burmaldaholic.bots.settings.limits` | Owner limits | Ограничения владельца |
| `gui.burmaldaholic.bots.settings.seated` | At the table | За столом |
| `gui.burmaldaholic.bots.settings.none_seated` | No bots at the table | Ботов за столом нет |

### Bot names (`gui.burmaldaholic.bots.name.<id>`; theme in the comment column is data for the code)

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.bots.name.lucky_steve` | Lucky Steve | Везунчик Стив |
| `gui.burmaldaholic.bots.name.grandpa_pavel` | Grandpa Pavel | Дед Павел |
| `gui.burmaldaholic.bots.name.creeper42` | Creeper42 | Крипер42 |
| `gui.burmaldaholic.bots.name.mr_blocksworth` | Mr. Blocksworth | Мистер Блоксворт |
| `gui.burmaldaholic.bots.name.diamond_dora` | Diamond Dora | Алмазная Дора |
| `gui.burmaldaholic.bots.name.aunt_zoya` | Aunt Zoya | Тётя Зоя |
| `gui.burmaldaholic.bots.name.redstone_rick` | Redstone Rick | Редстоун Рик |
| `gui.burmaldaholic.bots.name.emerald_emma` | Emerald Emma | Изумрудная Эмма |
| `gui.burmaldaholic.bots.name.sir_oinksalot` | Sir Oinksalot | Сэр Хрюкенс |
| `gui.burmaldaholic.bots.name.baba_valya` | Baba Valya | Баба Валя |
| `gui.burmaldaholic.bots.name.uncle_grisha` | Uncle Grisha | Дядя Гриша |
| `gui.burmaldaholic.bots.name.kuzmich` | Old Kuzmich | Кузьмич |
| `gui.burmaldaholic.bots.name.cobble_carl` | Cobblestone Carl | Булыжник Карл |
| `gui.burmaldaholic.bots.name.slime_sam` | Slimy Sam | Слизняк Сэм |
| `gui.burmaldaholic.bots.name.brewing_bella` | Brewing Bella | Зельеварка Белла |
| `gui.burmaldaholic.bots.name.captain_boat` | Captain Boat | Капитан Лодка |
| `gui.burmaldaholic.bots.name.bee_bea` | Bea the Beekeeper | Пчеловод Беа |
| `gui.burmaldaholic.bots.name.torch_tanya` | Torch Tanya | Таня-Факел |
| `gui.burmaldaholic.bots.name.axolotl_al` | Axolotl Al | Аксолотль Ал |
| `gui.burmaldaholic.bots.name.lady_luckless` | Lady Luckless | Леди Невезуха |
| `gui.burmaldaholic.bots.name.iron_ivan` | Iron Ivan | Железный Иван |
| `gui.burmaldaholic.bots.name.nether_nick` | Nether Nick | Незер Ник |
| `gui.burmaldaholic.bots.name.goldie_nuggets` | Goldie Nuggets | Голди Самородок |
| `gui.burmaldaholic.bots.name.piglin_pete` | Piglin Pete | Пиглин Петя |
| `gui.burmaldaholic.bots.name.bartering_boris` | Bartering Boris | Борис-Бартер |
| `gui.burmaldaholic.bots.name.madame_crimson` | Madame Crimson | Мадам Багрянка |
| `gui.burmaldaholic.bots.name.tusk_tony` | Tony Tusks | Тони Клык |
| `gui.burmaldaholic.bots.name.enderman_ed` | Enderman Ed | Эндермен Эд |
| `gui.burmaldaholic.bots.name.madame_ender` | Madame Ender | Мадам Эндер |
| `gui.burmaldaholic.bots.name.shulker_shura` | Shulker Shura | Шалкер Шура |
| `gui.burmaldaholic.bots.name.pearl_polly` | Pearly Polly | Жемчужная Полли |
| `gui.burmaldaholic.bots.name.void_viktor` | Viktor the Void | Виктор Пустота |

### Quips (`dialog.burmaldaholic.bots.<event>.N`, variants; `%1$s` = human name where used)

| Key | EN | RU |
|-----|----|----|
| `dialog.burmaldaholic.bots.join.1` | Deal me in. I brought my lucky redstone. | Сдавайте. Я со своим счастливым редстоуном. |
| `dialog.burmaldaholic.bots.join.2` | Evening, everyone. Chips are warm, cards are cold. | Всем добрый вечер. Фишки тёплые, карты холодные. |
| `dialog.burmaldaholic.bots.join.3` | Beep boop. Just kidding. Or am I? | Бип-буп. Шучу. Или нет? |
| `dialog.burmaldaholic.bots.join.4` | Mind if I sit? The villagers won't let me into their game. | Не возражаете? Жители не пускают меня в свою игру. |
| `dialog.burmaldaholic.bots.yield.1` | Take my seat, %1$s. Humans first, house rules. | Садитесь, %1$s. Люди вперёд — правила заведения. |
| `dialog.burmaldaholic.bots.yield.2` | My shift is over. Good luck, %1$s! | Моя смена окончена. Удачи, %1$s! |
| `dialog.burmaldaholic.bots.yield.3` | The seat is warm for you, %1$s. Don't lose it all at once. | Место нагрето, %1$s. Не проиграйте всё сразу. |
| `dialog.burmaldaholic.bots.leave.1` | I'm off to mine some more chips. | Пойду накопаю ещё фишек. |
| `dialog.burmaldaholic.bots.leave.2` | That's enough excitement for one circuit board. | Для одной платы волнений достаточно. |
| `dialog.burmaldaholic.bots.leave.3` | See you at the next table. I'll be the one in copper. | Увидимся за другим столом. Ищите того, кто в меди. |
| `dialog.burmaldaholic.bots.win_big.1` | Ka-ching! Somebody call the Loan Shark. For you, not me. | Дзынь! Позовите Ростовщика. Вам, не мне. |
| `dialog.burmaldaholic.bots.win_big.2` | The house thanks you. Well, I do. | Заведение благодарит. Ну, то есть я. |
| `dialog.burmaldaholic.bots.win_big.3` | Running hot! Somebody pour water on my circuits. | Пошла карта! Полейте мне схемы водой. |
| `dialog.burmaldaholic.bots.win_big.4` | Stack it up, stack it up! | Складываем, складываем! |
| `dialog.burmaldaholic.bots.bust.1` | Out of chips. Out of pride. Out of here. | Нет фишек. Нет гордости. Нет меня. |
| `dialog.burmaldaholic.bots.bust.2` | I'll be back. With a loan. | Я вернусь. С займом. |
| `dialog.burmaldaholic.bots.bust.3` | Error 404: stack not found. | Ошибка 404: фишки не найдены. |
| `dialog.burmaldaholic.bots.bust.4` | Tell my motherboard I love her. | Передайте материнской плате: люблю. |
| `dialog.burmaldaholic.bots.bad_beat.1` | The river hates me. Personally. | Ривер меня ненавидит. Лично. |
| `dialog.burmaldaholic.bots.bad_beat.2` | Statistically, that never happens. Emotionally, it happens every day. | По статистике такого не бывает. По ощущениям — каждый день. |
| `dialog.burmaldaholic.bots.bad_beat.3` | Recalculating… nope, still lost. | Пересчитываю… нет, всё равно проигрыш. |
| `dialog.burmaldaholic.bots.fold_to_shove.1` | Too rich for my redstone. | Слишком дорого для моего редстоуна. |
| `dialog.burmaldaholic.bots.fold_to_shove.2` | I fold. Strategically. Definitely not scared. | Пас. Стратегически. Точно не от страха. |
| `dialog.burmaldaholic.bots.fold_to_shove.3` | Take it, %1$s. My chips are waiting for a better story. | Забирайте, %1$s. Мои фишки ждут истории получше. |
| `dialog.burmaldaholic.bots.hero_call.1` | I could smell that bluff from the Nether, %1$s. | Этот блеф чувствуется даже из Незера, %1$s. |
| `dialog.burmaldaholic.bots.hero_call.2` | Nice try, %1$s. My sensors are calibrated. | Хорошая попытка, %1$s. Мои датчики откалиброваны. |
| `dialog.burmaldaholic.bots.hero_call.3` | A call on a hunch. The hunch delivers. | Колл по наитию. Наитие не подводит. |
| `dialog.burmaldaholic.bots.human_wins.1` | Well played, %1$s. | Хорошо сыграно, %1$s. |
| `dialog.burmaldaholic.bots.human_wins.2` | %1$s, are you counting cards? Kidding. Mostly. | %1$s, вы считаете карты? Шучу. Почти. |
| `dialog.burmaldaholic.bots.human_wins.3` | Enjoy it, %1$s. The house wants it back. | Наслаждайтесь, %1$s. Заведение хочет это обратно. |
| `dialog.burmaldaholic.bots.human_wins.4` | Someone is on fire tonight, and it isn't the torch. | Кто-то сегодня в ударе, и это не факел. |
| `dialog.burmaldaholic.bots.all_in.1` | All in! Fortune favours the rusty. | Олл-ин! Фортуна любит ржавых. |
| `dialog.burmaldaholic.bots.all_in.2` | Everything on the table. Even my warranty. | Всё на стол. Даже гарантийный талон. |
| `dialog.burmaldaholic.bots.all_in.3` | Go big or go back to mining. | По-крупному — или назад в шахту. |
| `dialog.burmaldaholic.bots.blackjack.1` | Blackjack! I'd high-five you, but no hands. | Блэкджек! Пять бы дать, да рук нет. |
| `dialog.burmaldaholic.bots.blackjack.2` | Twenty-one! Math is beautiful. | Двадцать одно! Математика прекрасна. |
| `dialog.burmaldaholic.bots.blackjack.3` | Ace and a face, my favourite couple. | Туз и картинка — моя любимая пара. |
| `dialog.burmaldaholic.bots.seven_out.1` | Seven out! Who brought the black cat? | Семёрка! Кто принёс чёрную кошку? |
| `dialog.burmaldaholic.bots.seven_out.2` | Pass the dice. And the tissues. | Передайте кости. И платочки. |
| `dialog.burmaldaholic.bots.seven_out.3` | The dice have spoken. Rudely. | Кости сказали своё слово. Грубо. |
| `dialog.burmaldaholic.bots.natural.1` | Natural nine! Like a diamond on the first swing. | Натуральная девятка! Как алмаз с первого удара. |
| `dialog.burmaldaholic.bots.natural.2` | Eight, no third card. Elegant. | Восьмёрка — без третьей карты. Изящно. |
| `dialog.burmaldaholic.bots.natural.3` | Banker, Player, whatever: nine is nine. | Банкир, Игрок — неважно: девять есть девять. |
| `dialog.burmaldaholic.bots.bank_take.1` | I'll hold the bank. Try to take it from me. | Банк держу я. Попробуйте отнять. |
| `dialog.burmaldaholic.bots.bank_take.2` | The banker's seat: my natural habitat. | Место банкира — моя естественная среда. |
| `dialog.burmaldaholic.bots.bank_take.3` | Bets, please. The bank is open. | Ставки, пожалуйста. Банк открыт. |
| `dialog.burmaldaholic.bots.banco.1` | Banco! All of it, please. | Банко! На всё, пожалуйста. |
| `dialog.burmaldaholic.bots.banco.2` | Banco. Go big or go home. | Банко. Гулять так гулять. |
| `dialog.burmaldaholic.bots.banco.3` | I'll take the whole bank, thanks. | Беру весь банк, спасибо. |
| `dialog.burmaldaholic.bots.pvp_win.1` | Luck is a skill. Mine, today. | Удача — это навык. Сегодня мой. |
| `dialog.burmaldaholic.bots.pvp_win.2` | Good game, %1$s. The coin likes copper. | Хорошая игра, %1$s. Монетка любит медь. |
| `dialog.burmaldaholic.bots.pvp_win.3` | Victory tastes like… machine oil. Lovely. | Победа на вкус как… машинное масло. Прекрасно. |
| `dialog.burmaldaholic.bots.pvp_loss.1` | Rematch? My circuits demand it. | Реванш? Мои схемы требуют. |
| `dialog.burmaldaholic.bots.pvp_loss.2` | Well played, %1$s. Next time it's mine. | Хорошо сыграно, %1$s. В следующий раз отыграюсь. |
| `dialog.burmaldaholic.bots.pvp_loss.3` | Rigged! …Just kidding, it's perfectly fair. | Подкручено! …Шучу, всё честно. |
| `dialog.burmaldaholic.bots.duel_accept.1` | Challenge accepted. May the best dice win. | Вызов принят. Пусть победит лучший рандом. |
| `dialog.burmaldaholic.bots.duel_accept.2` | You're on, %1$s. | Идёт, %1$s. |
| `dialog.burmaldaholic.bots.duel_accept.3` | A duel? They built me for this. Well, for sorting chests, but still. | Дуэль? Меня для этого и собирали. Ну, для сортировки сундуков, но всё же. |
| `dialog.burmaldaholic.bots.duel_decline.1` | Not today, %1$s. My purse says no. | Не сегодня, %1$s. Кошелёк против. |
| `dialog.burmaldaholic.bots.duel_decline.2` | Enough of you for today, %1$s. Come back tomorrow. | На сегодня с вас хватит, %1$s. Приходите завтра. |
| `dialog.burmaldaholic.bots.word_got_around.1` | Word got around about you, %1$s. The Sharks are coming. | Слухи о вас разошлись, %1$s. Идут акулы. |
| `dialog.burmaldaholic.bots.word_got_around.2` | Easy money is closed for today. Meet the professionals. | Лёгкие деньги на сегодня закончились. Знакомьтесь с профессионалами. |
| `dialog.burmaldaholic.bots.word_got_around.3` | We've read your file, %1$s. | Мы изучили ваше досье, %1$s. |
| `dialog.burmaldaholic.bots.sulk.1` | We're not playing with you anymore, %1$s. Not today. | С вами мы больше не играем, %1$s. Сегодня — точно. |
| `dialog.burmaldaholic.bots.sulk.2` | The bots have gone on strike. Come back tomorrow. | Боты объявили забастовку. Приходите завтра. |
| `dialog.burmaldaholic.bots.sulk.3` | You've taken enough of our chips for one day. | На сегодня вы забрали достаточно наших фишек. |
| `dialog.burmaldaholic.bots.idle.1` | Anyone else hear a creeper? | Кто-нибудь ещё слышит крипера? |
| `dialog.burmaldaholic.bots.idle.2` | Fun fact: the house always wins. Almost. | Интересный факт: заведение всегда в плюсе. Почти. |
| `dialog.burmaldaholic.bots.idle.3` | Shuffle up and deal, none of us is getting any younger. | Тасуйте и сдавайте, никто из нас не молодеет. |
| `dialog.burmaldaholic.bots.idle.4` | Stare at the chips long enough and they stare back. | Если долго смотреть на фишки, фишки начинают смотреть на вас. |

### Messages and errors

| Key | EN | RU |
|-----|----|----|
| `msg.burmaldaholic.bots.say` | %1$s: %2$s | %1$s: %2$s |
| `msg.burmaldaholic.bots.joined` | %1$s sits down | За стол садится %1$s |
| `msg.burmaldaholic.bots.joined_many` | Bots sit down: %1$s | За стол садятся боты: %1$s |
| `msg.burmaldaholic.bots.left` | %1$s leaves the table | %1$s уходит из-за стола |
| `msg.burmaldaholic.bots.seat_after_round` | A bot will give you its seat after this round | Бот уступит вам место после этого раунда |
| `msg.burmaldaholic.bots.seat_ready` | Your seat is ready | Ваше место готово |
| `msg.burmaldaholic.bots.host_now` | You are now the host of this table: you choose who plays | Теперь вы хозяин стола: вы решаете, кто играет |
| `msg.burmaldaholic.bots.settings_pending` | New table settings from %1$s, from the next round: %2$s | Новые настройки от игрока %1$s — со следующего раунда: %2$s |
| `msg.burmaldaholic.bots.settings_applied` | Table settings now: %1$s | Настройки стола теперь: %1$s |
| `msg.burmaldaholic.bots.invited` | %1$s invites you to a private %2$s table at %3$s | %1$s приглашает вас за закрытый стол (%2$s): %3$s |
| `msg.burmaldaholic.bots.invite_sent` | Invite sent: %1$s | Приглашение отправлено: %1$s |
| `msg.burmaldaholic.bots.uninvited` | Invite withdrawn: %1$s | Приглашение отозвано: %1$s |
| `msg.burmaldaholic.bots.let_in_request` | %1$s wants to join your table. Open the table to let them in. | Игрок %1$s хочет сесть за ваш стол. Откройте стол, чтобы пустить. |
| `msg.burmaldaholic.bots.let_in_click` | [Let them in] | [Пустить] |
| `msg.burmaldaholic.bots.regulars_gone` | The regulars went home. No more bots at this table today. | Завсегдатаи разошлись по домам. Сегодня ботов за этим столом больше не будет. |
| `msg.burmaldaholic.bots.word_got_around` | Word got around about %1$s: only Hard bots at this table today | Слухи об игроке %1$s разошлись: сегодня за этим столом только сложные боты |
| `msg.burmaldaholic.bots.sulking` | The bots are sulking: no more games with %1$s today | Боты обиделись: сегодня с игроком %1$s они больше не играют |
| `msg.burmaldaholic.bots.none_available` | No bots are free right now: the casino floor is packed | Свободных ботов сейчас нет: в казино аншлаг |
| `msg.burmaldaholic.bots.session_ended` | The bots pack up their chips. See you next time! | Боты собирают фишки. До встречи! |
| `msg.burmaldaholic.bots.heat_reset` | Bot limits reset for %1$s | Лимиты ботов для игрока %1$s сброшены |
| `msg.burmaldaholic.bots.private_on` | This table is now private | Теперь этот стол закрытый |
| `msg.burmaldaholic.bots.private_off` | This table is open to all again | Этот стол снова открыт для всех |
| `msg.burmaldaholic.bots.defaults_saved` | Saved as the table defaults | Сохранено как настройки стола по умолчанию |
| `msg.burmaldaholic.bots.limits_saved` | Table limits saved | Ограничения стола сохранены |
| `msg.burmaldaholic.bots.list_empty` | No bots are seated right now | Сейчас за столами нет ботов |
| `msg.burmaldaholic.bots.cleared` | Bots leave after the current round. Tables: %1$s | Боты уйдут после текущего раунда. Столов: %1$s |
| `gui.burmaldaholic.bots.error.bots_only_table` | %1$s is playing against bots here. Ask them to let you in. | Здесь %1$s играет с ботами. Попросите пустить вас. |
| `gui.burmaldaholic.bots.error.others_seated` | Other players are seated, so Just me and bots is not available | За столом другие игроки — режим «Только я и боты» недоступен |
| `gui.burmaldaholic.bots.error.owner_off` | The casino owner has turned bots off at this table | Владелец казино отключил ботов за этим столом |
| `gui.burmaldaholic.bots.error.owner_locked` | The casino owner does not let players change the seating here | Владелец казино не разрешает менять состав за этим столом |
| `gui.burmaldaholic.bots.error.host_locked` | Only the host can change these settings | Менять эти настройки может только хозяин стола |
| `gui.burmaldaholic.bots.error.private_forbidden` | Private tables are not allowed in this casino | В этом казино закрытые столы запрещены |
| `gui.burmaldaholic.bots.error.private_table` | This table is private. Ask %1$s for an invite. | Это закрытый стол. Попросите приглашение у игрока %1$s. |
| `gui.burmaldaholic.bots.error.capped` | The bots won't play you until tomorrow (in %1$s) | Боты не сядут с вами до завтра (через %1$s) |
| `gui.burmaldaholic.bots.error.easy_stake` | Easy bots don't play at %1$s stakes | Лёгкие боты не играют на ставках «%1$s» |
| `gui.burmaldaholic.bots.error.invites_full` | The invite list is full (%1$s) | Список приглашённых полон (%1$s) |
| `gui.burmaldaholic.bots.error.not_host` | Sit at the table as its host first | Сначала сядьте за стол как его хозяин |
| `gui.burmaldaholic.bots.error.disabled` | Bots are turned off on this server | Боты на этом сервере отключены |
| `gui.burmaldaholic.bots.error.debt` | While you owe the Loan Shark you can only play against the house's own bots | Пока вы должны Ростовщику, можно играть только с ботами заведения |
| `gui.burmaldaholic.bots.error.no_table` | Sit at a table or look at one first | Сначала сядьте за стол или посмотрите на него |

## advancements

Shown in the advancement tab. Titles are short (≤ 24 chars RU where possible).

| Key | EN | RU |
|-----|----|----|
| `advancement.burmaldaholic.root.title` | Burmaldaholic | Бурмалдоголик |
| `advancement.burmaldaholic.root.description` | Get your first chips. It starts innocently. | Получите первые фишки. Всё начинается невинно. |
| `advancement.burmaldaholic.first_bet.title` | Place Your Bets | Делайте ставки |
| `advancement.burmaldaholic.first_bet.description` | Settle your first wager | Сыграйте первую ставку |
| `advancement.burmaldaholic.beginners_luck.title` | Beginner's Luck | Новичкам везёт |
| `advancement.burmaldaholic.beginners_luck.description` | Win a wager | Выиграйте ставку |
| `advancement.burmaldaholic.natural.title` | Winner Winner | Двадцать одно! |
| `advancement.burmaldaholic.natural.description` | Get a blackjack | Соберите блэкджек |
| `advancement.burmaldaholic.split_personality.title` | Split Personality | Раздвоение личности |
| `advancement.burmaldaholic.split_personality.description` | Play four split hands in one blackjack round | Сыграйте четыре руки после сплитов за один раунд |
| `advancement.burmaldaholic.royal_flush.title` | Royalty | Королевская особа |
| `advancement.burmaldaholic.royal_flush.description` | Win a poker pot with a royal flush | Выиграйте банк с роял-флешем |
| `advancement.burmaldaholic.shark_hunter.title` | Bigger Fish | Рыбка покрупнее |
| `advancement.burmaldaholic.shark_hunter.description` | Take every chip from a Hard (Shark) poker bot | Оставьте сложного бота-акулу без фишек |
| `advancement.burmaldaholic.top_five.title` | Top of the Reels | Лучшие на барабанах |
| `advancement.burmaldaholic.top_five.description` | Land five of a machine's top symbol | Соберите пять главных символов автомата |
| `advancement.burmaldaholic.jackpot.title` | Jackpot! | Джекпот! |
| `advancement.burmaldaholic.jackpot.description` | Win a Major or Grand slot jackpot | Выиграйте джекпот «Мажор» или «Гранд» |
| `advancement.burmaldaholic.mini_jackpot.title` | Small Fortune | Маленькое состояние |
| `advancement.burmaldaholic.mini_jackpot.description` | Win any slot jackpot | Выиграйте любой джекпот на автомате |
| `advancement.burmaldaholic.free_spins.title` | On the House | За счёт заведения |
| `advancement.burmaldaholic.free_spins.description` | Trigger free spins on a slot machine | Запустите бесплатные вращения |
| `advancement.burmaldaholic.treasure_hunter.title` | Treasure Hunter | Кладоискатель |
| `advancement.burmaldaholic.treasure_hunter.description` | Open 10 chests in one Treasure Hunt | Откройте 10 сундуков за одну охоту |
| `advancement.burmaldaholic.tumble_six.title` | Chain Reaction | Цепная реакция |
| `advancement.burmaldaholic.tumble_six.description` | Get 6 tumbles in one Nether Inferno spin | Соберите 6 обвалов за одно вращение в «Пекле Незера» |
| `advancement.burmaldaholic.hoard_full.title` | Piglin Royalty | Король пиглинов |
| `advancement.burmaldaholic.hoard_full.description` | Fill all 15 cells in Piglin's Hoard | Заполните все 15 ячеек клада пиглинов |
| `advancement.burmaldaholic.void_walker.title` | Void Walker | Странник пустоты |
| `advancement.burmaldaholic.void_walker.description` | Make all three reels sticky in Void Walker free spins | Сделайте все три барабана липкими в «Страннике пустоты» |
| `advancement.burmaldaholic.dragon_core.title` | Heart of the Dragon | Сердце дракона |
| `advancement.burmaldaholic.dragon_core.description` | Reach the Dragon Core of the Dragon Wheel | Доберитесь до сердца колеса дракона |
| `advancement.burmaldaholic.epic_win.title` | Epic! | Эпично! |
| `advancement.burmaldaholic.epic_win.description` | Win at least 100 times your bet on one spin | Выиграйте за одно вращение не меньше 100 ставок |
| `advancement.burmaldaholic.max_win.title` | Broke the Machine | Автомат сломался |
| `advancement.burmaldaholic.max_win.description` | Hit a slot machine's max win | Сорвите максимальный выигрыш автомата |
| `advancement.burmaldaholic.zero_hero.title` | Zero Hero | Герой зеро |
| `advancement.burmaldaholic.zero_hero.description` | Win a straight bet on zero | Выиграйте ставку на зеро |
| `advancement.burmaldaholic.hot_shooter.title` | Hot Shooter | Горячая рука |
| `advancement.burmaldaholic.hot_shooter.description` | Make three points in a row as the craps shooter | Выбросьте три поинта подряд в крэпсе |
| `advancement.burmaldaholic.plinko_edge.title` | Edge Case | Крайний случай |
| `advancement.burmaldaholic.plinko_edge.description` | Land the ball in an outermost bin on High risk | Попадите шариком в крайнюю лунку на высоком риске |
| `advancement.burmaldaholic.scratch_top.title` | Scratch That | Счастливый билет |
| `advancement.burmaldaholic.scratch_top.description` | Win the top prize on a scratch card | Выиграйте главный приз в лотерею |
| `advancement.burmaldaholic.on_fire.title` | On Fire | В ударе |
| `advancement.burmaldaholic.on_fire.description` | Reach a lucky streak of 10 | Дойдите до серии удачи ×10 |
| `advancement.burmaldaholic.black_cat.title` | Black Cat | Чёрная кошка |
| `advancement.burmaldaholic.black_cat.description` | Reach a losing streak of 10. Hugs. | Дойдите до серии неудач ×10. Обнимаем. |
| `advancement.burmaldaholic.loan_taken.title` | Friendly Neighborhood Shark | Добрый сосед-ростовщик |
| `advancement.burmaldaholic.loan_taken.description` | Take a loan | Возьмите займ |
| `advancement.burmaldaholic.knock_knock.title` | Knock Knock | Тук-тук |
| `advancement.burmaldaholic.knock_knock.description` | Miss a loan deadline | Просрочьте займ |
| `advancement.burmaldaholic.hostile_takeover.title` | Hostile Takeover | Враждебное поглощение |
| `advancement.burmaldaholic.hostile_takeover.description` | Defeat an entire debt collector squad | Одолейте целую бригаду коллекторов |
| `advancement.burmaldaholic.clean_slate.title` | Clean Slate | Чист перед законом |
| `advancement.burmaldaholic.clean_slate.description` | Repay a loan on time | Верните займ вовремя |
| `advancement.burmaldaholic.not_today.title` | Not Today | Не сегодня |
| `advancement.burmaldaholic.not_today.description` | Survive thanks to Last Chance | Выживите благодаря «Последнему шансу» |
| `advancement.burmaldaholic.scarred.title` | Scarred for Life | Шрам на всю жизнь |
| `advancement.burmaldaholic.scarred.description` | Survive a High-Stakes Last Chance in Hardcore | Выживите в хардкоре, сыграв «Последний шанс» ва-банк |
| `advancement.burmaldaholic.heart_on_the_line.title` | Heart on the Line | Сердце на кону |
| `advancement.burmaldaholic.heart_on_the_line.description` | Win a bet staked with your hearts | Выиграйте ставку, поставив сердца |
| `advancement.burmaldaholic.devils_deal.title` | Devil's Deal | Сделка с дьяволом |
| `advancement.burmaldaholic.devils_deal.description` | Win a Soul Wager | Выиграйте ставку на душу |
| `advancement.burmaldaholic.golden_hour.title` | Golden Touch | Золотые руки |
| `advancement.burmaldaholic.golden_hour.description` | Win during Golden Hour | Выиграйте во время «Золотого часа» |
| `advancement.burmaldaholic.beam_me_up.title` | Beam Me Up | Вжух — и я там |
| `advancement.burmaldaholic.beam_me_up.description` | Get teleported by chaos | Попадите под телепортацию хаоса |
| `advancement.burmaldaholic.vip_silver.title` | Silver Spoon | Серебряная ложка |
| `advancement.burmaldaholic.vip_silver.description` | Reach Silver VIP | Получите ВИП-статус «Серебро» |
| `advancement.burmaldaholic.vip_gold.title` | Gold Member | Золотой клиент |
| `advancement.burmaldaholic.vip_gold.description` | Reach Gold VIP | Получите ВИП-статус «Золото» |
| `advancement.burmaldaholic.vip_platinum.title` | Platinum Club | Платиновый клуб |
| `advancement.burmaldaholic.vip_platinum.description` | Reach Platinum VIP | Получите ВИП-статус «Платина» |
| `advancement.burmaldaholic.vip_diamond.title` | Shine Bright | Бриллиантовая рука |
| `advancement.burmaldaholic.vip_diamond.description` | Reach Diamond VIP | Получите ВИП-статус «Алмаз» |
| `advancement.burmaldaholic.vip_netherite.title` | Whale | Кит |
| `advancement.burmaldaholic.vip_netherite.description` | Reach Netherite VIP. The house knows your name. | Получите ВИП-статус «Незерит». Заведение знает вас по имени. |
| `advancement.burmaldaholic.the_house.title` | Be the House | Сам себе казино |
| `advancement.burmaldaholic.the_house.description` | Place a Casino Charter | Установите лицензию казино |
| `advancement.burmaldaholic.house_always_wins.title` | The House Always Wins | Заведение всегда в плюсе |
| `advancement.burmaldaholic.house_always_wins.description` | Earn 10 000 chips of profit with your own casino | Заработайте 10 000 фишек прибыли в своём казино |
| `advancement.burmaldaholic.bankrupt.title` | Bankrupt | Банкрот |
| `advancement.burmaldaholic.bankrupt.description` | Have your casino close because the bankroll ran dry | Доведите своё казино до закрытия из-за пустой кассы |
| `advancement.burmaldaholic.piglin_parlor.title` | Snouts and Ladders | Свинство и азарт |
| `advancement.burmaldaholic.piglin_parlor.description` | Enter a Piglin Parlor | Зайдите в пиглинский салон |
| `advancement.burmaldaholic.high_roller.title` | Out of This World | Не от мира сего |
| `advancement.burmaldaholic.high_roller.description` | Place a bet in the End City High Roller Lounge | Сделайте ставку в лаунже хайроллеров в городе Края |
| `advancement.burmaldaholic.baccarat_natural.title` | La Grande | Натуральная девятка |
| `advancement.burmaldaholic.baccarat_natural.description` | Win a Player or Banker bet with a natural 9 | Выиграйте ставку на Игрока или Банкира с натуральной девяткой |
| `advancement.burmaldaholic.tie_streak.title` | Tied Up | Ничья за ничьей |
| `advancement.burmaldaholic.tie_streak.description` | Win Tie bets on two coups in a row | Выиграйте ставку на ничью две раздачи подряд |
| `advancement.burmaldaholic.banco.title` | Banco! | Банко! |
| `advancement.burmaldaholic.banco.description` | Call Banco at chemin de fer and win the coup | Объявите «Банко» в шмен-де-фер и выиграйте раздачу |
| `advancement.burmaldaholic.bank_holder.title` | Too Big to Fail | Банк не лопнет |
| `advancement.burmaldaholic.bank_holder.description` | Keep one chemin de fer bank through five winning coups in a row | Удержите банк в шмен-де-фер пять выигранных раздач подряд |
| `advancement.burmaldaholic.uth_four_x.title` | Four Times the Nerve | Вчетверо смелее |
| `advancement.burmaldaholic.uth_four_x.description` | Bet ×4 before the flop in Ultimate Texas Hold'em and win | Поставьте ×4 до флопа в «Ультимейт Техас Холдем» и выиграйте |
| `advancement.burmaldaholic.uth_house_seat.title` | Deal Me Out | Сам себе дилер |
| `advancement.burmaldaholic.uth_house_seat.description` | Finish a round in the dealer seat with a profit against at least two players | Закончите раунд на месте дилера в плюсе, играя минимум против двоих |
| `advancement.burmaldaholic.uth_royal.title` | Blind Luck | Слепая удача |
| `advancement.burmaldaholic.uth_royal.description` | Get paid for a royal flush in Ultimate Texas Hold'em | Получите выплату за роял-флеш в «Ультимейт Техас Холдем» |
| `gui.burmaldaholic.achievements.unlocked` | Achievement unlocked: %1$s | Достижение получено: %1$s |
| `gui.burmaldaholic.achievements.progress` | %1$s of %2$s unlocked | Получено %1$s из %2$s |
| `advancement.burmaldaholic.pvp_first_win.title` | Mano a Mano | Один на один |
| `advancement.burmaldaholic.pvp_first_win.description` | Win a match against another player | Выиграйте матч против другого игрока |
| `advancement.burmaldaholic.pvp_all_in.title` | Fortune Favours the Bold | Смелость города берёт |
| `advancement.burmaldaholic.pvp_all_in.description` | Go all-in in a PvP match and win | Сыграйте ва-банк в PvP-матче и победите |
| `advancement.burmaldaholic.pvp_all_square.title` | Even Steven | Квиты |
| `advancement.burmaldaholic.pvp_all_square.description` | Lose a coin duel, call double or nothing and get back to all square | Проиграйте дуэль на монетке, крикните «всё или ничего» и отыграйтесь |
| `advancement.burmaldaholic.pvp_underdog.title` | Dark Horse | Тёмная лошадка |
| `advancement.burmaldaholic.pvp_underdog.description` | Win a Wheel Party holding a tenth of the wheel or less | Выиграйте «Колесо на всех», владея десятой частью колеса или меньше |
| `advancement.burmaldaholic.pvp_phoenix.title` | Rise From the Ashes | Восстать из пепла |
| `advancement.burmaldaholic.pvp_phoenix.description` | Win a Slot Showdown after a KABOOM halved your score | Выиграйте битву автоматов после того, как «БАБАХ» ополовинил ваши очки |
| `advancement.burmaldaholic.pvp_lucky_feet.title` | Lucky Feet | Две лапки на удачу |
| `advancement.burmaldaholic.pvp_lucky_feet.description` | Win a Scratch Showdown with two Rabbit's Feet on your card | Выиграйте лотерейную битву с двумя кроличьими лапками на билете |
| `advancement.burmaldaholic.pvp_full_house.title` | Last One Standing | Один против всех |
| `advancement.burmaldaholic.pvp_full_house.description` | Beat five opponents in a single match | Обыграйте пятерых соперников в одном матче |
| `advancement.burmaldaholic.pvp_revenge.title` | Sweet Revenge | Сладкая месть |
| `advancement.burmaldaholic.pvp_revenge.description` | Win a grudge match against the player who kept beating you | Выиграйте «дело принципа» у того, кто раз за разом вас обыгрывал |
| `advancement.burmaldaholic.pvp_rampage.title` | Rampage | Разнос |
| `advancement.burmaldaholic.pvp_rampage.description` | Win five PvP matches in a row against at least two different players | Выиграйте пять PvP-матчей подряд минимум у двух разных соперников |
| `advancement.burmaldaholic.pvp_champion.title` | Champion | Чемпион |
| `advancement.burmaldaholic.pvp_champion.description` | Win a tournament | Выиграйте турнир |
| `advancement.burmaldaholic.pvp_bookie.title` | Called It | Чуйка |
| `advancement.burmaldaholic.pvp_bookie.description` | Win a side bet that paid ×5 or more | Выиграйте ставку зрителя с выплатой от ×5 |
| `advancement.burmaldaholic.man_vs_machine.title` | Man vs Machine | Человек против машины |
| `advancement.burmaldaholic.man_vs_machine.description` | Win a poker pot at showdown against a Hard bot | Выиграйте банк на вскрытии против сложного бота |
| `advancement.burmaldaholic.clean_sweep.title` | Clean Sweep | Чистая работа |
| `advancement.burmaldaholic.clean_sweep.description` | At a Just me and bots poker table, take every chip from 3 Normal or Hard bots in one sitting | За покерным столом «Только я и боты» оставьте без фишек трёх нормальных или сложных ботов за одну игру |
| `advancement.burmaldaholic.word_got_around.title` | Word Got Around | Слухи расходятся |
| `advancement.burmaldaholic.word_got_around.description` | Win so much from bots in one day that only the Sharks will play you | Выиграйте у ботов за день столько, что с вами сядут играть только акулы |
| `advancement.burmaldaholic.short_circuit.title` | Short Circuit | Короткое замыкание |
| `advancement.burmaldaholic.short_circuit.description` | Call Banco against a bot banker and win the coup | Объявите банко против бота-банкира и выиграйте раздачу |
| `advancement.burmaldaholic.members_only.title` | Members Only | Только для своих |
| `advancement.burmaldaholic.members_only.description` | Play a round at a private table with a player you invited | Сыграйте раунд за закрытым столом с приглашённым вами игроком |
| `advancement.burmaldaholic.no_robots.title` | No Robots Allowed | Роботам вход воспрещён |
| `advancement.burmaldaholic.no_robots.description` | Play a round at a Humans only table with at least 4 players | Сыграйте раунд за столом «Только люди», где сидят не меньше 4 игроков |

---

## config

Labels shown on the config screen.
Key = `config.burmaldaholic.` + the exact config key. **Family templates** (keys with `<…>` in
CONFIG.md) use one template key; the code passes the member's display name as `%1$s`.
`.tooltip` keys exist only where listed.

### Sections and shared

| Key | EN | RU |
|-----|----|----|
| `config.burmaldaholic.title` | Burmaldaholic settings | Настройки Бурмалдоголика |
| `config.burmaldaholic.section.core` | General | Общее |
| `config.burmaldaholic.section.economy` | Economy | Экономика |
| `config.burmaldaholic.section.contracts` | Contracts | Заказы |
| `config.burmaldaholic.section.wager` | Non-chip stakes | Залоги |
| `config.burmaldaholic.section.vip` | VIP | ВИП |
| `config.burmaldaholic.section.blackjack` | Blackjack | Блэкджек |
| `config.burmaldaholic.section.poker` | Poker | Покер |
| `config.burmaldaholic.section.slots` | Slots | Автоматы |
| `config.burmaldaholic.section.roulette` | Roulette | Рулетка |
| `config.burmaldaholic.section.craps` | Craps | Крэпс |
| `config.burmaldaholic.section.extras` | Extras | Прочие игры |
| `config.burmaldaholic.section.loan` | Loans and collectors | Займы и коллекторы |
| `config.burmaldaholic.section.chaos` | Chaos | Хаос |
| `config.burmaldaholic.section.streak` | Streaks | Серии |
| `config.burmaldaholic.section.lastchance` | Last Chance | «Последний шанс» |
| `config.burmaldaholic.section.worldgen` | World generation | Генерация мира |
| `config.burmaldaholic.section.ownership` | Player casinos | Казино игроков |
| `config.burmaldaholic.section.debug` | Debug | Отладка |
| `config.burmaldaholic.reset` | Reset to default | Сбросить к стандартным |
| `config.burmaldaholic.saved` | Settings saved | Настройки сохранены |
| `config.burmaldaholic.clamped` | %1$s was out of range and set to %2$s | Значение %1$s вне диапазона — установлено %2$s |
| `config.burmaldaholic.invalid` | %1$s: invalid value | %1$s: недопустимое значение |
| `config.burmaldaholic.range` | Range: %1$s – %2$s | Диапазон: %1$s – %2$s |
| `config.burmaldaholic.unknown_key` | Unknown setting: %1$s | Неизвестная настройка: %1$s |
| `gui.burmaldaholic.common.difficulty.peaceful` | Peaceful | Мирная |
| `gui.burmaldaholic.common.difficulty.easy` | Easy | Лёгкая |
| `gui.burmaldaholic.common.difficulty.normal` | Normal | Нормальная |
| `gui.burmaldaholic.common.difficulty.hard` | Hard | Сложная |
| `gui.burmaldaholic.common.difficulty.hardcore` | Hardcore | Хардкор |
| `gui.burmaldaholic.chaos.event.chip_shower` | Chip shower | Фишкопад |
| `gui.burmaldaholic.chaos.event.lucky_buff` | Blessing | Благословение |
| `gui.burmaldaholic.chaos.event.diamond_rain` | Diamond rain | Алмазный дождь |
| `gui.burmaldaholic.chaos.event.xp_fountain` | XP fountain | Фонтан опыта |
| `gui.burmaldaholic.chaos.event.curse` | Curse | Проклятие |
| `gui.burmaldaholic.chaos.event.mob_wave` | Mob wave | Нашествие мобов |
| `gui.burmaldaholic.chaos.event.random_teleport` | Random teleport | Случайная телепортация |
| `gui.burmaldaholic.chaos.event.weather_change` | Weather change | Смена погоды |
| `gui.burmaldaholic.chaos.event.golden_hour` | Golden Hour | «Золотой час» |
| `config.burmaldaholic.section.client_fx` | Client effects | Клиентские эффекты |
| `config.burmaldaholic.anim.inWorld` | Game animations in the world | Анимация игр в мире |

### Family templates

| Key | EN | RU |
|-----|----|----|
| `config.burmaldaholic.contracts.weight` | Contract weight: %1$s | Вес заказа: %1$s |
| `config.burmaldaholic.wager.appraisal` | Appraisal: %1$s | Оценка: %1$s |
| `config.burmaldaholic.slots.pays` | %1$s: payouts | %1$s: выплаты |
| `config.burmaldaholic.chaos.weight` | Chance weight: %1$s | Вес события: %1$s |
| `config.burmaldaholic.chaos.event.enabled` | Enabled: %1$s | Включено: %1$s |
| `config.burmaldaholic.slots.machine.enabled` | %1$s: enabled | %1$s: вкл. |
| `config.burmaldaholic.slots.bets` | %1$s: bet levels | %1$s: уровни ставок |
| `config.burmaldaholic.slots.defaultBet` | %1$s: default bet | %1$s: ставка по умолчанию |
| `config.burmaldaholic.slots.minVipTier` | %1$s: required VIP tier | %1$s: нужный ВИП-статус |
| `config.burmaldaholic.slots.maxWinMultiple` | %1$s: max win (× bet) | %1$s: макс. выигрыш (× ставки) |
| `config.burmaldaholic.slots.strips` | %1$s: reel strips | %1$s: ленты барабанов |
| `config.burmaldaholic.slots.scatterPays` | %1$s: scatter payouts | %1$s: выплаты за скаттеры |
| `config.burmaldaholic.slots.freeSpins` | %1$s: free spins for 3/4/5 | %1$s: фриспины за 3/4/5 |
| `config.burmaldaholic.slots.freeSpins.retrigger` | %1$s: extra free spins | %1$s: доп. фриспины |
| `config.burmaldaholic.slots.freeSpins.cap` | %1$s: max free spins | %1$s: макс. фриспинов |
| `config.burmaldaholic.slots.jackpot.refBet` | %1$s: full-jackpot bet | %1$s: ставка для полного джекпота |
| `config.burmaldaholic.slots.jackpot.seed` | %1$s: jackpot seeds (× bet) | %1$s: стартовые джекпоты (× ставки) |
| `config.burmaldaholic.slots.jackpot.contribution` | %1$s: jackpot contributions | %1$s: отчисления в джекпоты |
| `config.burmaldaholic.slots.jackpot.owned` | %1$s: fixed jackpots at player casinos | %1$s: фиксированные джекпоты в казино игроков |
| `config.burmaldaholic.slots.buy.price` | %1$s: bonus price (× bet) | %1$s: цена бонуса (× ставки) |

### core, economy, contracts, wager

| Key | EN | RU |
|-----|----|----|
| `config.burmaldaholic.core.casinoMode` | Casino mode | Режим казино |
| `config.burmaldaholic.core.giveCasinoCardOnJoin` | Give Casino Card on first join | Выдавать клубную карту новичкам |
| `config.burmaldaholic.core.hud.enabled` | HUD panel | Панель на экране |
| `config.burmaldaholic.core.hud.position` | Default HUD corner | Угол панели по умолчанию |
| `config.burmaldaholic.core.hud.position.top_left` | Top left | Слева сверху |
| `config.burmaldaholic.core.hud.position.top_right` | Top right | Справа сверху |
| `config.burmaldaholic.core.hud.position.bottom_left` | Bottom left | Слева снизу |
| `config.burmaldaholic.core.hud.position.bottom_right` | Bottom right | Справа снизу |
| `config.burmaldaholic.core.announceBigWins` | Announce big wins | Объявлять крупные выигрыши |
| `config.burmaldaholic.core.bigWinThreshold` | Big win threshold | Порог крупного выигрыша |
| `config.burmaldaholic.core.roundTimeoutRefund` | Refund rounds after restart | Возвращать ставки после перезапуска |
| `config.burmaldaholic.economy.maxBalance` | Maximum balance | Максимальный баланс |
| `config.burmaldaholic.economy.startingBalance` | Starting balance | Стартовый баланс |
| `config.burmaldaholic.economy.emeraldBuyRate` | Chips per emerald (buy) | Фишек за изумруд (покупка) |
| `config.burmaldaholic.economy.emeraldBuyRateGoldVip` | Chips per emerald (buy, Gold VIP) | Фишек за изумруд (покупка, ВИП «Золото») |
| `config.burmaldaholic.economy.emeraldSellRate` | Chips per emerald (sell) | Фишек за изумруд (продажа) |
| `config.burmaldaholic.economy.goldBuyRate` | Chips per gold ingot (buy) | Фишек за золотой слиток (покупка) |
| `config.burmaldaholic.economy.goldSellRate` | Chips per gold ingot (sell) | Фишек за золотой слиток (продажа) |
| `config.burmaldaholic.economy.ore.coal` | Coal ore reward | Награда: угольная руда |
| `config.burmaldaholic.economy.ore.copper` | Copper ore reward | Награда: медная руда |
| `config.burmaldaholic.economy.ore.iron` | Iron ore reward | Награда: железная руда |
| `config.burmaldaholic.economy.ore.gold` | Gold ore reward | Награда: золотая руда |
| `config.burmaldaholic.economy.ore.redstone` | Redstone ore reward | Награда: редстоуновая руда |
| `config.burmaldaholic.economy.ore.lapis` | Lapis ore reward | Награда: лазуритовая руда |
| `config.burmaldaholic.economy.ore.emerald` | Emerald ore reward | Награда: изумрудная руда |
| `config.burmaldaholic.economy.ore.diamond` | Diamond ore reward | Награда: алмазная руда |
| `config.burmaldaholic.economy.ore.netherQuartz` | Nether quartz ore reward | Награда: незер-кварцевая руда |
| `config.burmaldaholic.economy.ore.netherGold` | Nether gold ore reward | Награда: незерская золотая руда |
| `config.burmaldaholic.economy.ore.ancientDebris` | Ancient debris reward | Награда: древние обломки |
| `config.burmaldaholic.economy.ore.placedDebrisLedgerSize` | Placed debris memory size | Память о поставленных обломках |
| `config.burmaldaholic.economy.mob.common` | Common hostile mob reward | Награда: обычный враждебный моб |
| `config.burmaldaholic.economy.mob.creeper` | Creeper reward | Награда: крипер |
| `config.burmaldaholic.economy.mob.phantom` | Phantom reward | Награда: фантом |
| `config.burmaldaholic.economy.mob.pillager` | Pillager reward | Награда: разбойник |
| `config.burmaldaholic.economy.mob.piglin` | Piglin reward | Награда: пиглин |
| `config.burmaldaholic.economy.mob.enderman` | Enderman reward | Награда: эндермен |
| `config.burmaldaholic.economy.mob.blaze` | Blaze reward | Награда: ифрит |
| `config.burmaldaholic.economy.mob.hoglin` | Hoglin reward | Награда: хоглин |
| `config.burmaldaholic.economy.mob.guardian` | Guardian reward | Награда: страж |
| `config.burmaldaholic.economy.mob.witch` | Witch reward | Награда: ведьма |
| `config.burmaldaholic.economy.mob.vindicator` | Vindicator reward | Награда: поборник |
| `config.burmaldaholic.economy.mob.witherSkeleton` | Wither skeleton reward | Награда: визер-скелет |
| `config.burmaldaholic.economy.mob.creaking` | Creaking reward | Награда: скрипун |
| `config.burmaldaholic.economy.mob.ghast` | Ghast reward | Награда: гаст |
| `config.burmaldaholic.economy.mob.breeze` | Breeze reward | Награда: вихрь |
| `config.burmaldaholic.economy.mob.shulker` | Shulker reward | Награда: шалкер |
| `config.burmaldaholic.economy.mob.piglinBrute` | Piglin brute reward | Награда: пиглин-громила |
| `config.burmaldaholic.economy.mob.evoker` | Evoker reward | Награда: вызыватель |
| `config.burmaldaholic.economy.mob.ravager` | Ravager reward | Награда: разоритель |
| `config.burmaldaholic.economy.mob.elderGuardian` | Elder guardian reward | Награда: древний страж |
| `config.burmaldaholic.economy.mob.warden` | Warden reward | Награда: хранитель |
| `config.burmaldaholic.economy.mob.wither` | Wither reward | Награда: визер |
| `config.burmaldaholic.economy.mob.enderDragonFirst` | Ender dragon reward (first kill) | Награда: дракон Края (первая победа) |
| `config.burmaldaholic.economy.mob.enderDragonRepeat` | Ender dragon reward (later kills) | Награда: дракон Края (повторно) |
| `config.burmaldaholic.economy.mob.hardMultiplier` | Mob reward multiplier on Hard | Множитель наград на сложной |
| `config.burmaldaholic.economy.mob.windowTicks` | Anti-farm window (ticks) | Окно защиты от ферм (тики) |
| `config.burmaldaholic.economy.mob.fullRewardKills` | Full-reward kills per window | Убийств с полной наградой за окно |
| `config.burmaldaholic.economy.mob.reducedRewardKills` | Reduced-reward kill limit | Предел убийств с урезанной наградой |
| `config.burmaldaholic.economy.mob.reducedRewardFactor` | Reduced reward factor | Коэффициент урезанной награды |
| `config.burmaldaholic.economy.mob.spawnerRewards` | Reward spawner mobs | Награда за мобов из спавнеров |
| `config.burmaldaholic.economy.trade.perTradeBase` | Chips per trade | Фишек за сделку |
| `config.burmaldaholic.economy.trade.perEmerald` | Extra chips per emerald traded | Доп. фишек за изумруд в сделке |
| `config.burmaldaholic.economy.trade.perTradeCap` | Max chips per trade | Макс. фишек за сделку |
| `config.burmaldaholic.economy.trade.dailyCap` | Daily trade reward cap | Дневной лимит за торговлю |
| `config.burmaldaholic.contracts.enabled` | Daily contracts | Заказы дня |
| `config.burmaldaholic.contracts.slots` | Contract slots | Слотов для заказов |
| `config.burmaldaholic.contracts.rerollCost` | Reroll cost | Цена смены заказа |
| `config.burmaldaholic.contracts.tierScaling` | Scaling per VIP tier | Рост по ВИП-статусу |
| `config.burmaldaholic.contracts.rewardMultiplier` | Reward multiplier | Множитель наград |
| `config.burmaldaholic.wager.pawnEnabled` | Non-chip stakes | Залоги вместо фишек |
| `config.burmaldaholic.wager.items.enabled` | Item stakes | Ставки предметами |
| `config.burmaldaholic.wager.xp.enabled` | XP level stakes | Ставки уровнями опыта |
| `config.burmaldaholic.wager.xp.maxLevels` | Max levels per stake | Макс. уровней за ставку |
| `config.burmaldaholic.wager.xp.pointsPerChip` | XP points per chip | Очков опыта за фишку |
| `config.burmaldaholic.wager.hearts.enabled` | Heart stakes | Ставки сердцами |
| `config.burmaldaholic.wager.hearts.valuePerHeart` | Chips per heart | Фишек за сердце |
| `config.burmaldaholic.wager.hearts.maxPerBet` | Max hearts per bet | Макс. сердец за ставку |
| `config.burmaldaholic.wager.hearts.maxTotal` | Max hearts lost at once | Макс. потерянных сердец одновременно |
| `config.burmaldaholic.wager.hearts.durationTicks` | Heart loss duration (ticks) | Длительность потери сердец (тики) |
| `config.burmaldaholic.wager.hardcoreSoulWager` | Soul Wager in Hardcore | Ставка на душу в хардкоре |
| `config.burmaldaholic.wager.hardcoreSoulWager.tooltip` | Lets Hardcore players bet their life on a coin flip. Losing kills them for good. | Позволяет в хардкоре ставить жизнь на монетку. Проигрыш — окончательная смерть. |
| `config.burmaldaholic.wager.soul.minValue` | Soul Wager minimum value | Мин. стоимость ставки на душу |
| `config.burmaldaholic.wager.soul.cooldownTicks` | Soul Wager cooldown (ticks) | Перезарядка ставки на душу (тики) |

### vip, games

| Key | EN | RU |
|-----|----|----|
| `config.burmaldaholic.vip.threshold.silver` | Silver threshold (wagered) | Порог «Серебра» (поставлено) |
| `config.burmaldaholic.vip.threshold.gold` | Gold threshold (wagered) | Порог «Золота» (поставлено) |
| `config.burmaldaholic.vip.threshold.platinum` | Platinum threshold (wagered) | Порог «Платины» (поставлено) |
| `config.burmaldaholic.vip.threshold.diamond` | Diamond threshold (wagered) | Порог «Алмаза» (поставлено) |
| `config.burmaldaholic.vip.threshold.netherite` | Netherite threshold (wagered) | Порог «Незерита» (поставлено) |
| `config.burmaldaholic.vip.maxBet.bronze` | Max bet: Bronze | Макс. ставка: Бронза |
| `config.burmaldaholic.vip.maxBet.silver` | Max bet: Silver | Макс. ставка: Серебро |
| `config.burmaldaholic.vip.maxBet.gold` | Max bet: Gold | Макс. ставка: Золото |
| `config.burmaldaholic.vip.maxBet.platinum` | Max bet: Platinum | Макс. ставка: Платина |
| `config.burmaldaholic.vip.maxBet.diamond` | Max bet: Diamond | Макс. ставка: Алмаз |
| `config.burmaldaholic.vip.maxBet.netherite` | Max bet: Netherite | Макс. ставка: Незерит |
| `config.burmaldaholic.vip.cashback.gold` | Cashback: Gold | Кешбэк: Золото |
| `config.burmaldaholic.vip.cashback.platinum` | Cashback: Platinum | Кешбэк: Платина |
| `config.burmaldaholic.vip.cashback.diamond` | Cashback: Diamond | Кешбэк: Алмаз |
| `config.burmaldaholic.vip.cashback.netherite` | Cashback: Netherite | Кешбэк: Незерит |
| `config.burmaldaholic.vip.contractBonus.silver` | Contract bonus: Silver | Бонус к заказам: Серебро |
| `config.burmaldaholic.vip.contractBonus.gold` | Contract bonus: Gold and above | Бонус к заказам: Золото и выше |
| `config.burmaldaholic.vip.announceNetherite` | Announce Netherite VIPs | Объявлять незеритовых ВИП |
| `config.burmaldaholic.blackjack.enabled` | Blackjack | Блэкджек |
| `config.burmaldaholic.blackjack.decks` | Decks in the shoe | Колод в шузе |
| `config.burmaldaholic.blackjack.penetration` | Reshuffle point | Когда перемешивать |
| `config.burmaldaholic.blackjack.dealerHitsSoft17` | Dealer hits soft 17 | Дилер берёт на мягких 17 |
| `config.burmaldaholic.blackjack.blackjackPayout` | Blackjack payout | Выплата за блэкджек |
| `config.burmaldaholic.blackjack.doubleAfterSplit` | Double after split | Дабл после сплита |
| `config.burmaldaholic.blackjack.maxHands` | Max hands after splits | Макс. рук после сплитов |
| `config.burmaldaholic.blackjack.resplitAces` | Re-split aces | Повторный сплит тузов |
| `config.burmaldaholic.blackjack.insurance` | Insurance | Страховка |
| `config.burmaldaholic.blackjack.lateSurrender` | Surrender | Право сдаться |
| `config.burmaldaholic.blackjack.minBet` | Minimum bet | Минимальная ставка |
| `config.burmaldaholic.blackjack.highRollerMinBet` | High Roller minimum bet | Мин. ставка у хайроллеров |
| `config.burmaldaholic.blackjack.highRollerMaxMultiplier` | High Roller max bet multiplier | Множитель макс. ставки хайроллеров |
| `config.burmaldaholic.blackjack.seats` | Seats | Мест за столом |
| `config.burmaldaholic.blackjack.betTimerTicks` | Betting time (ticks) | Время на ставки (тики) |
| `config.burmaldaholic.blackjack.insuranceTimerTicks` | Insurance decision time (ticks) | Время на страховку (тики) |
| `config.burmaldaholic.blackjack.turnTimerTicks` | Turn time (ticks) | Время на ход (тики) |
| `config.burmaldaholic.poker.enabled` | Poker | Покер |
| `config.burmaldaholic.poker.maxSeats` | Seats | Мест за столом |
| `config.burmaldaholic.poker.stakes.micro.bb` | Big blind: Micro | Большой блайнд: микро |
| `config.burmaldaholic.poker.stakes.low.bb` | Big blind: Low | Большой блайнд: низкие |
| `config.burmaldaholic.poker.stakes.mid.bb` | Big blind: Mid | Большой блайнд: средние |
| `config.burmaldaholic.poker.stakes.high.bb` | Big blind: High | Большой блайнд: высокие |
| `config.burmaldaholic.poker.minBuyInBb` | Minimum buy-in (BB) | Мин. бай-ин (ББ) |
| `config.burmaldaholic.poker.maxBuyInBb` | Maximum buy-in (BB) | Макс. бай-ин (ББ) |
| `config.burmaldaholic.poker.actionTimerTicks` | Action time (ticks) | Время на ход (тики) |
| `config.burmaldaholic.poker.timeoutsToSitOut` | Timeouts before sitting out | Пропусков хода до паузы |
| `config.burmaldaholic.poker.sitOutHandsToRemove` | Sit-out hands before removal | Раздач на паузе до удаления |
| `config.burmaldaholic.poker.rakePercent` | Rake | Рейк |
| `config.burmaldaholic.poker.rakeCapBb` | Rake cap (BB) | Потолок рейка (ББ) |
| `config.burmaldaholic.poker.rakeNoFlopNoDrop` | No flop, no rake | Нет флопа — нет рейка |
| `config.burmaldaholic.poker.botsEnabled` | Bots fill empty seats | Боты на свободных местах |
| `config.burmaldaholic.poker.botBuyInBb` | Bot buy-in (BB) | Бай-ин ботов (ББ) |
| `config.burmaldaholic.poker.botThinkMinTicks` | Bot think time min (ticks) | Мин. раздумье бота (тики) |
| `config.burmaldaholic.poker.botThinkMaxTicks` | Bot think time max (ticks) | Макс. раздумье бота (тики) |
| `config.burmaldaholic.poker.bot.regularSamples` | Regular bot simulations | Симуляций у регуляра |
| `config.burmaldaholic.poker.bot.sharkSamples` | Shark bot simulations | Симуляций у акулы |
| `config.burmaldaholic.poker.botMix.micro` | Bot mix: Micro | Состав ботов: микро |
| `config.burmaldaholic.poker.botMix.low` | Bot mix: Low | Состав ботов: низкие |
| `config.burmaldaholic.poker.botMix.mid` | Bot mix: Mid | Состав ботов: средние |
| `config.burmaldaholic.poker.botMix.high` | Bot mix: High | Состав ботов: высокие |
| `config.burmaldaholic.poker.maxDistance` | Max distance from table | Макс. расстояние от стола |
| `config.burmaldaholic.slots.enabled` | Slot machines | Игровые автоматы |
| `config.burmaldaholic.slots.validateRtp` | Warn about RTP above 99%% | Предупреждать об RTP выше 99 %% |
| `config.burmaldaholic.roulette.enabled` | Roulette | Рулетка |
| `config.burmaldaholic.roulette.laPartage` | La partage | Ла партаж |
| `config.burmaldaholic.roulette.laPartage.tooltip` | Even-money bets lose only half when zero comes up | При выпадении зеро ставки 1:1 теряют только половину |
| `config.burmaldaholic.roulette.minBet` | Minimum bet | Минимальная ставка |
| `config.burmaldaholic.roulette.insideMaxFraction` | Inside bet max (share of tier max) | Макс. внутренняя ставка (доля от лимита) |
| `config.burmaldaholic.roulette.highRollerMinTotal` | High Roller minimum per spin | Мин. сумма на спин у хайроллеров |
| `config.burmaldaholic.roulette.highRollerMaxMultiplier` | High Roller max multiplier | Множитель лимита хайроллеров |
| `config.burmaldaholic.roulette.betTimerTicks` | Betting time (ticks) | Время на ставки (тики) |
| `config.burmaldaholic.roulette.spinTicks` | Spin animation (ticks) | Анимация вращения (тики) |
| `config.burmaldaholic.roulette.maxBettors` | Max players | Макс. игроков |
| `config.burmaldaholic.roulette.historyLength` | History length | Длина истории |
| `config.burmaldaholic.craps.enabled` | Craps | Крэпс |
| `config.burmaldaholic.craps.minBet` | Minimum bet | Минимальная ставка |
| `config.burmaldaholic.craps.fieldPays2` | Field pays on 2 (X:1) | Филд платит на 2 (X:1) |
| `config.burmaldaholic.craps.fieldPays12` | Field pays on 12 (X:1) | Филд платит на 12 (X:1) |
| `config.burmaldaholic.craps.maxOdds4_10` | Max odds on 4 and 10 | Макс. коэффициент на 4 и 10 |
| `config.burmaldaholic.craps.maxOdds5_9` | Max odds on 5 and 9 | Макс. коэффициент на 5 и 9 |
| `config.burmaldaholic.craps.maxOdds6_8` | Max odds on 6 and 8 | Макс. коэффициент на 6 и 8 |
| `config.burmaldaholic.craps.betWindowTicks` | Betting window (ticks) | Окно ставок (тики) |
| `config.burmaldaholic.craps.rollTimerTicks` | Roll timer (ticks) | Время на бросок (тики) |
| `config.burmaldaholic.craps.seats` | Seats | Мест за столом |
| `config.burmaldaholic.extras.coinFlip.enabled` | Coin Flip | Монетка |
| `config.burmaldaholic.extras.coinFlip.payout` | Coin Flip payout (X:1) | Выплата в монетке (X:1) |
| `config.burmaldaholic.extras.wheel.enabled` | Wheel of Fortune | Колесо фортуны |
| `config.burmaldaholic.extras.wheel.maxBetFraction` | Wheel max bet (share of tier max) | Макс. ставка на колесе (доля от лимита) |
| `config.burmaldaholic.extras.wheel.segments` | Wheel segments | Сегменты колеса |
| `config.burmaldaholic.extras.wheel.multipliers` | Wheel multipliers | Множители колеса |
| `config.burmaldaholic.extras.scratch.enabled` | Scratch cards | Моментальная лотерея |
| `config.burmaldaholic.extras.scratch.basic.price` | Scratch card price | Цена лотерейного билета |
| `config.burmaldaholic.extras.scratch.basic.prizes` | Scratch card prizes | Призы лотереи |
| `config.burmaldaholic.extras.scratch.gold.price` | Golden scratch card price | Цена золотого билета |
| `config.burmaldaholic.extras.scratch.gold.prizes` | Golden scratch card prizes | Призы золотой лотереи |
| `config.burmaldaholic.extras.scratch.creeperChance` | Creeper card share | Доля «криперных» билетов |
| `config.burmaldaholic.extras.plinko.enabled` | Plinko | Плинко |
| `config.burmaldaholic.extras.plinko.maxBetFraction` | Plinko max bet (share of tier max) | Макс. ставка в Плинко (доля от лимита) |
| `config.burmaldaholic.extras.plinko.low` | Plinko multipliers: Low | Множители Плинко: низкий риск |
| `config.burmaldaholic.extras.plinko.medium` | Plinko multipliers: Medium | Множители Плинко: средний риск |
| `config.burmaldaholic.extras.plinko.high` | Plinko multipliers: High | Множители Плинко: высокий риск |
| `config.burmaldaholic.extras.diceDuel.enabled` | Dice Duel | Дуэль на костях |
| `config.burmaldaholic.extras.diceDuel.houseWinsTieOn` | Tie totals won by the house | Ничьи в пользу заведения |
| `config.burmaldaholic.extras.diceDuel.pvpEnabled` | Player vs player duels | Дуэли между игроками |
| `config.burmaldaholic.extras.diceDuel.pvpRakePercent` | PvP duel rake (%%) | Комиссия за дуэль игроков (%%) |
| `config.burmaldaholic.extras.diceDuel.challengeTimeoutTicks` | Challenge timeout (ticks) | Время на ответ (тики) |
| `config.burmaldaholic.extras.diceDuel.maxDistance` | Max duel distance | Макс. дистанция дуэли |
| `config.burmaldaholic.slots.overworld.freeSpins.multiplier` | Overworld Riches: free-spin multiplier | Богатства Верхнего мира: множитель фриспинов |
| `config.burmaldaholic.slots.nether.tumble.ladder` | Nether Inferno: tumble multipliers | Пекло Незера: множители обвалов |
| `config.burmaldaholic.slots.nether.tumble.ladderFree` | Nether Inferno: tumble multipliers in free spins | Пекло Незера: множители обвалов во фриспинах |
| `config.burmaldaholic.slots.overworld.pick.board` | Treasure Hunt: chests | Охота за сокровищами: число сундуков |
| `config.burmaldaholic.slots.overworld.pick.weights` | Treasure Hunt: chest contents | Охота за сокровищами: содержимое сундуков |
| `config.burmaldaholic.slots.nether.hold.trigger` | Piglin's Hoard: coins to start | Клад пиглинов: монет для запуска |
| `config.burmaldaholic.slots.nether.hold.respins` | Piglin's Hoard: respins | Клад пиглинов: число повторов |
| `config.burmaldaholic.slots.nether.hold.coinChance` | Piglin's Hoard: coin chance per cell | Клад пиглинов: шанс монеты в ячейке |
| `config.burmaldaholic.slots.nether.hold.coinWeights` | Piglin's Hoard: coin values | Клад пиглинов: номиналы монет |
| `config.burmaldaholic.slots.end.wheel.outer` | Dragon Wheel: outer ring | Колесо дракона: внешнее кольцо |
| `config.burmaldaholic.slots.end.wheel.middle` | Dragon Wheel: middle ring | Колесо дракона: среднее кольцо |
| `config.burmaldaholic.slots.end.wheel.core` | Dragon Wheel: core | Колесо дракона: сердце |
| `config.burmaldaholic.slots.jackpot.announceMinTier` | Announce jackpots from | Объявлять джекпоты от |
| `config.burmaldaholic.slots.buyFeature.enabled` | Allow buying bonuses | Разрешить покупку бонусов |
| `config.burmaldaholic.slots.buyFeature.tierMaxMultiple` | Bonus price limit (× VIP max bet) | Лимит цены бонуса (× макс. ставки ВИП) |
| `config.burmaldaholic.slots.autoplay.enabled` | Allow autoplay | Разрешить автоигру |
| `config.burmaldaholic.slots.autoplay.counts` | Autoplay spin options | Варианты числа автовращений |
| `config.burmaldaholic.slots.autoplay.lossLimits` | Autoplay loss limits (× bet) | Лимиты проигрыша автоигры (× ставки) |
| `config.burmaldaholic.slots.turboAllowed` | Allow turbo spins | Разрешить турбо |
| `config.burmaldaholic.slots.anticipation` | Slow reels when a feature is close | Замедлять барабаны, когда близок бонус |
| `config.burmaldaholic.slots.bigWinTiers` | Win tiers (× bet) | Пороги выигрышей (× ставки) |
| `config.burmaldaholic.slots.inWorld.enabled` | Show reels on cabinets | Показывать барабаны на автоматах |
| `config.burmaldaholic.slots.inWorld.radius` | Cabinet reels view distance | Дальность показа барабанов |

### baccarat, uth

| Key | EN | RU |
|-----|----|----|
| `config.burmaldaholic.section.baccarat` | Baccarat | Баккара |
| `config.burmaldaholic.section.uth` | Ultimate Texas Hold'em | Ультимейт Техас Холдем |
| `config.burmaldaholic.baccarat.enabled` | Baccarat | Баккара |
| `config.burmaldaholic.baccarat.decks` | Decks in the shoe | Колод в шузе |
| `config.burmaldaholic.baccarat.penetration` | Reshuffle point | Когда перемешивать |
| `config.burmaldaholic.baccarat.burnCards` | Burn cards after a shuffle | Сжигать карты после перемешивания |
| `config.burmaldaholic.baccarat.bankerCommission` | Banker commission | Комиссия с Банкира |
| `config.burmaldaholic.baccarat.bankerCommission.tooltip` | Also sets the Banker bet step so the commission is exact (5%% → multiples of 20) | Заодно задаёт шаг ставки на Банкира, чтобы комиссия была без остатка (5 %% → кратно 20) |
| `config.burmaldaholic.baccarat.tiePays` | Tie pays (X:1) | Ничья платит (X:1) |
| `config.burmaldaholic.baccarat.pairBets` | Pair side bets | Ставки на пары |
| `config.burmaldaholic.baccarat.pairPays` | Pairs pay (X:1) | Пары платят (X:1) |
| `config.burmaldaholic.baccarat.minBet` | Minimum bet | Минимальная ставка |
| `config.burmaldaholic.baccarat.sideMaxFraction` | Tie/pair max (share of the limit) | Макс. на ничью и пары (доля от лимита) |
| `config.burmaldaholic.baccarat.highRollerMinTotal` | High Roller minimum per coup | Мин. сумма на раздачу у хайроллеров |
| `config.burmaldaholic.baccarat.highRollerMaxMultiplier` | High Roller max multiplier | Множитель лимита хайроллеров |
| `config.burmaldaholic.baccarat.highRollerMinVipTier` | High Roller: required VIP tier | Хайроллеры: нужный ВИП-статус |
| `config.burmaldaholic.baccarat.seats` | Seats | Мест за столом |
| `config.burmaldaholic.baccarat.betTimerTicks` | Betting time (ticks) | Время на ставки (тики) |
| `config.burmaldaholic.baccarat.revealTicks` | Card reveal animation (ticks) | Анимация открытия карт (тики) |
| `config.burmaldaholic.baccarat.historyLength` | Bead plate size | Размер табло результатов |
| `config.burmaldaholic.baccarat.tieStreakChaos` | Ties in a row for a chip shower | Ничьих подряд для фишкопада |
| `config.burmaldaholic.baccarat.chemmy.enabled` | Chemin de fer (player bank) | Шмен-де-фер (банк игроков) |
| `config.burmaldaholic.baccarat.chemmy.minBank` | Chemin de fer: minimum bank | Шмен-де-фер: минимальный банк |
| `config.burmaldaholic.baccarat.chemmy.rakePercent` | Chemin de fer: house commission | Шмен-де-фер: комиссия заведения |
| `config.burmaldaholic.baccarat.chemmy.bankOfferTicks` | Chemin de fer: bank decision time (ticks) | Шмен-де-фер: время решения по банку (тики) |
| `config.burmaldaholic.baccarat.chemmy.idleTicks` | Chemin de fer: idle time before the bank passes (ticks) | Шмен-де-фер: простой до передачи банка (тики) |
| `config.burmaldaholic.baccarat.chemmy.houseCoupWhenNoBanker` | House deals when nobody banks | Без банкира сдаёт заведение |
| `config.burmaldaholic.uth.enabled` | Ultimate Texas Hold'em | Ультимейт Техас Холдем |
| `config.burmaldaholic.uth.seats` | Seats | Мест за столом |
| `config.burmaldaholic.uth.allow3x` | Allow the ×3 preflop bet | Разрешить ставку ×3 до флопа |
| `config.burmaldaholic.uth.minAnte` | Minimum Ante | Минимальное анте |
| `config.burmaldaholic.uth.highRollerMinAnte` | High Roller minimum Ante | Мин. анте у хайроллеров |
| `config.burmaldaholic.uth.highRollerMaxMultiplier` | High Roller max multiplier | Множитель лимита хайроллеров |
| `config.burmaldaholic.uth.highRollerMinVipTier` | High Roller: required VIP tier | Хайроллеры: нужный ВИП-статус |
| `config.burmaldaholic.uth.tripsEnabled` | Trips side bet | Ставка трипс |
| `config.burmaldaholic.uth.blindPays` | Blind paytable | Выплаты по блайнду |
| `config.burmaldaholic.uth.tripsPays` | Trips paytable | Выплаты по трипсу |
| `config.burmaldaholic.uth.validateEdge` | Warn about a Trips edge of 1%% or less | Предупреждать о преимуществе трипса 1 %% и ниже |
| `config.burmaldaholic.uth.betTimerTicks` | Betting time (ticks) | Время на ставки (тики) |
| `config.burmaldaholic.uth.decisionTimerTicks` | Decision time per street (ticks) | Время на решение (тики) |
| `config.burmaldaholic.uth.autoPlayMadeHands` | On timeout, play a straight or better | При тайм-ауте играть стрит и выше |
| `config.burmaldaholic.uth.autoPlayMadeHands.tooltip` | When time runs out after the river, bet ×1 instead of folding if the hand is a straight or better | Если время после ривера вышло, при стрите и выше делается ставка ×1 вместо паса |
| `config.burmaldaholic.uth.pvp.enabled` | Players may take the dealer seat | Игроки могут занять место дилера |
| `config.burmaldaholic.uth.pvp.minBank` | Dealer seat: minimum bank | Место дилера: минимальный банк |
| `config.burmaldaholic.uth.pvp.minBankerVip` | Dealer seat: required VIP tier | Место дилера: нужный ВИП-статус |
| `config.burmaldaholic.uth.pvp.rakePercent` | Dealer seat: house rake | Место дилера: рейк заведения |
| `config.burmaldaholic.uth.pvp.bankerRounds` | Dealer seat: rounds before rotation | Место дилера: раундов до передачи |
| `config.burmaldaholic.uth.pvp.houseRoundsWhenNoBanker` | House deals when nobody banks | Без банкира сдаёт заведение |

### loan, chaos, streak, lastchance, worldgen, ownership, debug

| Key | EN | RU |
|-----|----|----|
| `config.burmaldaholic.loan.enabled` | Loans | Займы |
| `config.burmaldaholic.loan.products` | Loan products | Виды займов |
| `config.burmaldaholic.loan.rate.easy` | Interest: Easy/Peaceful | Проценты: лёгкая/мирная |
| `config.burmaldaholic.loan.rate.normal` | Interest: Normal | Проценты: нормальная |
| `config.burmaldaholic.loan.rate.hard` | Interest: Hard/Hardcore | Проценты: сложная/хардкор |
| `config.burmaldaholic.loan.goodStandingDiscount` | Discount per on-time loan | Скидка за погашение вовремя |
| `config.burmaldaholic.loan.goodStandingMaxSteps` | Max on-time discounts | Макс. число скидок |
| `config.burmaldaholic.loan.minRate` | Minimum interest | Минимальные проценты |
| `config.burmaldaholic.loan.platinumDiscount` | Platinum VIP discount | Скидка для ВИП «Платина» |
| `config.burmaldaholic.loan.lateFee.easy` | Late fee per day: Easy/Peaceful | Пени в день: лёгкая/мирная |
| `config.burmaldaholic.loan.lateFee.normal` | Late fee per day: Normal | Пени в день: нормальная |
| `config.burmaldaholic.loan.lateFee.hard` | Late fee per day: Hard/Hardcore | Пени в день: сложная/хардкор |
| `config.burmaldaholic.loan.lateFeeCapMultiplier` | Debt cap (× amount due) | Потолок долга (× сумма к возврату) |
| `config.burmaldaholic.loan.garnishPercent` | Garnishment when overdue (%%) | Удержание при просрочке (%%) |
| `config.burmaldaholic.loan.defaultCooldownDays` | No-loan days after default | Дней без займов после просрочки |
| `config.burmaldaholic.loan.warningTicks` | Deadline warnings (ticks before) | Напоминания о сроке (тиков до) |
| `config.burmaldaholic.loan.collectors.enabled` | Debt collectors | Коллекторы |
| `config.burmaldaholic.loan.collectors.enabled.tooltip` | If off, overdue debts are handled by an asset freeze on every difficulty | Если выключено, просрочку на любой сложности решает арест счёта |
| `config.burmaldaholic.loan.firstWaveDelayTicks` | First wave delay (ticks) | Задержка первой волны (тики) |
| `config.burmaldaholic.loan.offlineWaveDelayTicks` | Wave delay after joining (ticks) | Задержка волны после входа (тики) |
| `config.burmaldaholic.loan.squadMax` | Max squad size | Макс. размер бригады |
| `config.burmaldaholic.loan.escalationMax` | Max extra collectors per wave | Макс. доп. коллекторов за волну |
| `config.burmaldaholic.loan.approachTicks` | Approach time (ticks) | Время подхода (тики) |
| `config.burmaldaholic.loan.negotiateTicks` | Negotiation time (ticks) | Время на переговоры (тики) |
| `config.burmaldaholic.loan.hostileTicks` | Hostile time before leaving (ticks) | Время агрессии до ухода (тики) |
| `config.burmaldaholic.loan.partialPaymentMin` | Partial payment that sends a wave away | Частичная оплата, при которой бригада уходит |
| `config.burmaldaholic.loan.repossessBalancePercent` | Balance seized on death (%%) | Изымается с баланса при смерти (%%) |
| `config.burmaldaholic.loan.repossessItem` | Seize most valuable item on death | Изымать самый ценный предмет при смерти |
| `config.burmaldaholic.loan.collectorHealthMultiplier.easy` | Collector health: Easy | Здоровье коллекторов: лёгкая |
| `config.burmaldaholic.loan.collectorHealthMultiplier.hard` | Collector health: Hard | Здоровье коллекторов: сложная |
| `config.burmaldaholic.loan.peacefulSeizePercent` | Asset freeze seizure per day (%%) | Арест счёта: списание в день (%%) |
| `config.burmaldaholic.chaos.enabled` | Chaos events | Хаос-события |
| `config.burmaldaholic.chaos.ambientIntervalTicks` | Chaos check interval (ticks) | Интервал проверки хаоса (тики) |
| `config.burmaldaholic.chaos.ambientChance` | Chaos chance per check | Шанс хаоса за проверку |
| `config.burmaldaholic.chaos.playerCooldownTicks` | Per-player cooldown (ticks) | Перезарядка на игрока (тики) |
| `config.burmaldaholic.chaos.bigWin.multiple` | Big win: min multiple of bet | Крупный выигрыш: мин. кратность ставки |
| `config.burmaldaholic.chaos.bigWin.minChips` | Big win: min chips | Крупный выигрыш: мин. фишек |
| `config.burmaldaholic.chaos.bigWin.buffChance` | Big win blessing chance | Шанс благословения за крупный выигрыш |
| `config.burmaldaholic.chaos.chipShower.min` | Chip shower min | Фишкопад: минимум |
| `config.burmaldaholic.chaos.chipShower.max` | Chip shower max | Фишкопад: максимум |
| `config.burmaldaholic.chaos.diamondRain.min` | Diamond rain min | Алмазный дождь: минимум |
| `config.burmaldaholic.chaos.diamondRain.max` | Diamond rain max | Алмазный дождь: максимум |
| `config.burmaldaholic.chaos.xpFountain.min` | XP fountain min | Фонтан опыта: минимум |
| `config.burmaldaholic.chaos.xpFountain.max` | XP fountain max | Фонтан опыта: максимум |
| `config.burmaldaholic.chaos.buff.minTicks` | Blessing min duration (ticks) | Благословение: мин. длительность (тики) |
| `config.burmaldaholic.chaos.buff.maxTicks` | Blessing max duration (ticks) | Благословение: макс. длительность (тики) |
| `config.burmaldaholic.chaos.curse.minTicks` | Curse min duration (ticks) | Проклятие: мин. длительность (тики) |
| `config.burmaldaholic.chaos.curse.maxTicks` | Curse max duration (ticks) | Проклятие: макс. длительность (тики) |
| `config.burmaldaholic.chaos.mobWave.easy` | Mob wave size: Easy | Размер нашествия: лёгкая |
| `config.burmaldaholic.chaos.mobWave.normal` | Mob wave size: Normal | Размер нашествия: нормальная |
| `config.burmaldaholic.chaos.mobWave.hard` | Mob wave size: Hard | Размер нашествия: сложная |
| `config.burmaldaholic.chaos.mobWave.minDistance` | Mob wave min distance | Нашествие: мин. расстояние |
| `config.burmaldaholic.chaos.mobWave.maxDistance` | Mob wave max distance | Нашествие: макс. расстояние |
| `config.burmaldaholic.chaos.mobWave.despawnTicks` | Mob wave lifetime (ticks) | Время жизни нашествия (тики) |
| `config.burmaldaholic.chaos.teleport.minDistance` | Teleport min distance | Телепортация: мин. расстояние |
| `config.burmaldaholic.chaos.teleport.maxDistance` | Teleport max distance | Телепортация: макс. расстояние |
| `config.burmaldaholic.chaos.teleport.attempts` | Safe spot search attempts | Попыток найти безопасное место |
| `config.burmaldaholic.chaos.weather.durationTicks` | Weather change duration (ticks) | Длительность смены погоды (тики) |
| `config.burmaldaholic.chaos.goldenHour.enabled` | Golden Hour | «Золотой час» |
| `config.burmaldaholic.chaos.goldenHour.multiplier` | Golden Hour winnings multiplier | Множитель выигрышей «Золотого часа» |
| `config.burmaldaholic.chaos.goldenHour.durationTicks` | Golden Hour duration (ticks) | Длительность «Золотого часа» (тики) |
| `config.burmaldaholic.chaos.goldenHour.cooldownTicks` | Golden Hour cooldown (ticks) | Перезарядка «Золотого часа» (тики) |
| `config.burmaldaholic.chaos.goldenHour.sunsetChance` | Golden Hour chance at sunset | Шанс «Золотого часа» на закате |
| `config.burmaldaholic.chaos.goldenHour.bonusCap` | Golden Hour bonus cap per player | Лимит бонуса «Золотого часа» на игрока |
| `config.burmaldaholic.chaos.respawnGraceTicks` | Grace after respawn (ticks) | Защита после возрождения (тики) |
| `config.burmaldaholic.chaos.deferMaxTicks` | Max delay while in a casino menu (ticks) | Макс. отсрочка, пока открыто меню (тики) |
| `config.burmaldaholic.chaos.bossSafeRadius` | Boss safety radius | Безопасный радиус от боссов |
| `config.burmaldaholic.streak.enabled` | Streaks affect odds | Серии влияют на шансы |
| `config.burmaldaholic.streak.max` | Max streak | Макс. серия |
| `config.burmaldaholic.streak.luckyPerStep` | Lucky reroll chance per step | Шанс переброса за шаг удачи |
| `config.burmaldaholic.streak.pityPerStep` | Pity reroll chance per step | Шанс переброса за шаг неудач |
| `config.burmaldaholic.streak.minHouseEdge` | Minimum house edge | Мин. преимущество заведения |
| `config.burmaldaholic.streak.minHouseEdge.tooltip` | Streak bonuses are capped so every game keeps at least this edge | Бонусы серий ограничены так, чтобы у заведения всегда оставалось хотя бы это преимущество |
| `config.burmaldaholic.streak.decayTicks` | Streak decay interval (ticks) | Интервал затухания серии (тики) |
| `config.burmaldaholic.lastChance.enabled` | Last Chance | «Последний шанс» |
| `config.burmaldaholic.lastChance.chance.easy` | Success chance: Easy/Peaceful | Шанс успеха: лёгкая/мирная |
| `config.burmaldaholic.lastChance.chance.normal` | Success chance: Normal | Шанс успеха: нормальная |
| `config.burmaldaholic.lastChance.chance.hard` | Success chance: Hard | Шанс успеха: сложная |
| `config.burmaldaholic.lastChance.cooldownTicks` | Cooldown (ticks) | Перезарядка (тики) |
| `config.burmaldaholic.lastChance.costPercent` | Fee on success (%% of balance) | Комиссия при успехе (%% баланса) |
| `config.burmaldaholic.lastChance.hardcoreMode` | Hardcore mode | Режим в хардкоре |
| `config.burmaldaholic.lastChance.hardcoreMode.disabled` | Disabled | Отключён |
| `config.burmaldaholic.lastChance.hardcoreMode.high_stakes` | High stakes | Ва-банк |
| `config.burmaldaholic.lastChance.hardcoreMode.tooltip` | High stakes: survive for all your chips and one heart, forever | Ва-банк: выжить ценой всех фишек и одного сердца навсегда |
| `config.burmaldaholic.lastChance.hardcore.chance` | Hardcore success chance | Шанс успеха в хардкоре |
| `config.burmaldaholic.lastChance.hardcore.cooldownTicks` | Hardcore cooldown (ticks) | Перезарядка в хардкоре (тики) |
| `config.burmaldaholic.lastChance.hardcore.minStake` | Hardcore minimum chips | Мин. фишек для хардкора |
| `config.burmaldaholic.lastChance.hardcore.heartCost` | Permanent health cost | Потеря здоровья навсегда |
| `config.burmaldaholic.lastChance.hardcore.minMaxHealth` | Minimum max health to be eligible | Мин. максимум здоровья для сделки |
| `config.burmaldaholic.worldgen.enabled` | Generate casinos | Генерировать казино |
| `config.burmaldaholic.worldgen.villageCasino.chance` | Village casino chance | Шанс казино в деревне |
| `config.burmaldaholic.worldgen.piglinParlor.chance` | Piglin Parlor chance | Шанс пиглинского салона |
| `config.burmaldaholic.worldgen.highRoller.chance` | High Roller Lounge chance | Шанс лаунжа хайроллеров |
| `config.burmaldaholic.worldgen.loanSharkRespawnTicks` | Loan Shark respawn (ticks) | Возрождение ростовщика (тики) |
| `config.burmaldaholic.ownership.enabled` | Player casinos | Казино игроков |
| `config.burmaldaholic.ownership.licenseFee` | License fee | Лицензионный сбор |
| `config.burmaldaholic.ownership.claimRadius` | Claim radius | Радиус зоны |
| `config.burmaldaholic.ownership.maxPerPlayer` | Casinos per player | Казино на игрока |
| `config.burmaldaholic.ownership.protectTables` | Protect owned tables | Защищать столы владельца |
| `config.burmaldaholic.ownership.explosionProof` | Explosion-proof tables | Столы не взрываются |
| `config.burmaldaholic.multiplayer.tableLeaveDistance` | Leave-table distance | Дистанция выхода из-за стола |
| `config.burmaldaholic.multiplayer.spectatorRadius` | Spectator radius | Радиус зрителей |
| `config.burmaldaholic.debug.logRounds` | Log every round | Записывать каждый раунд |
| `config.burmaldaholic.debug.fixedSeed` | Fixed RNG seed (tests) | Фиксированный сид (для тестов) |
| `config.burmaldaholic.debug.showOdds` | Show odds to operators | Показывать шансы операторам |

---

### pvp

| Key | EN | RU |
|-----|----|----|
| `config.burmaldaholic.section.pvp` | PvP | PvP |
| `config.burmaldaholic.pvp.enabled` | PvP modes | PvP-режимы |
| `config.burmaldaholic.pvp.rakeBasisPoints` | House cut (basis points, 100 = 1%%) | Комиссия заведения (базисные пункты, 100 = 1 %%) |
| `config.burmaldaholic.pvp.minStake` | Minimum stake | Минимальная ставка |
| `config.burmaldaholic.pvp.joinRadius` | Invite and join distance | Дистанция вызова и входа |
| `config.burmaldaholic.pvp.announceRadius` | Spectator radius | Радиус зрителей |
| `config.burmaldaholic.pvp.announceServerWidePot` | Pot announced to everyone | Банк для объявления всем |
| `config.burmaldaholic.pvp.inviteTimeoutTicks` | Time to answer a challenge (ticks) | Время на ответ на вызов (тики) |
| `config.burmaldaholic.pvp.lobbyTimeoutTicks` | Lobby time (ticks) | Время лобби (тики) |
| `config.burmaldaholic.pvp.decisionTimeoutTicks` | Decision time (ticks) | Время на решение (тики) |
| `config.burmaldaholic.pvp.countdownTicks` | Countdown (ticks) | Обратный отсчёт (тики) |
| `config.burmaldaholic.pvp.maxPendingInvites` | Open challenges per player | Открытых вызовов на игрока |
| `config.burmaldaholic.pvp.declineCooldownTicks` | Re-challenge cooldown after a decline (ticks) | Пауза после отказа (тики) |
| `config.burmaldaholic.pvp.historyTicks` | Keep finished matches (ticks) | Хранить сыгранные матчи (тики) |
| `config.burmaldaholic.pvp.affectsStreak` | PvP changes the luck streak | PvP влияет на серию удачи |
| `config.burmaldaholic.pvp.countsTowardVip` | PvP stakes count for VIP | PvP-ставки идут в зачёт ВИП |
| `config.burmaldaholic.pvp.streakAnnounce` | Win streak announcements | Объявления о сериях побед |
| `config.burmaldaholic.pvp.grudgeLosses` | Losses in a row for a grudge match | Поражений подряд для «дела принципа» |
| `config.burmaldaholic.pvp.taunts.enabled` | Taunts | Подколки |
| `config.burmaldaholic.pvp.taunts.cooldownTicks` | Taunt cooldown (ticks) | Пауза между подколками (тики) |
| `config.burmaldaholic.pvp.taunts.maxPerMatch` | Taunts per match | Подколок за матч |
| `config.burmaldaholic.pvp.allowOwnedMachines` | PvP at player casinos | PvP в казино игроков |
| `config.burmaldaholic.pvp.coin.enabled` | Coin Flip Duel | Дуэль на монетке |
| `config.burmaldaholic.pvp.coin.maxDoubles` | Double or nothing: max doublings | «Всё или ничего»: макс. удвоений |
| `config.burmaldaholic.pvp.slots.enabled` | Slot Showdown | Битва автоматов |
| `config.burmaldaholic.pvp.slots.maxPlayers` | Slot Showdown: max players | Битва автоматов: макс. игроков |
| `config.burmaldaholic.pvp.slots.spinChoices` | Slot Showdown: spin options | Битва автоматов: варианты числа вращений |
| `config.burmaldaholic.pvp.slots.linkRadius` | Slot Showdown: machine link radius | Битва автоматов: радиус связи автоматов |
| `config.burmaldaholic.pvp.slots.spinIntervalTicks` | Slot Showdown: auto-spin after (ticks) | Битва автоматов: автовращение через (тики) |
| `config.burmaldaholic.pvp.slots.hotSymbol` | Slot Showdown: hot symbol | Битва автоматов: горячий символ |
| `config.burmaldaholic.pvp.slots.underdogBoost` | Slot Showdown: underdog boost | Битва автоматов: фора аутсайдеру |
| `config.burmaldaholic.pvp.slots.kaboom` | Slot Showdown: KABOOM | Битва автоматов: «БАБАХ» |
| `config.burmaldaholic.pvp.slots.pearlSwap` | Slot Showdown: Ender Pearl swap | Битва автоматов: рокировка |
| `config.burmaldaholic.pvp.wheel.enabled` | Wheel Party | Колесо на всех |
| `config.burmaldaholic.pvp.wheel.maxPlayers` | Wheel Party: max players | Колесо на всех: макс. игроков |
| `config.burmaldaholic.pvp.wheel.countdownTicks` | Wheel Party: countdown (ticks) | Колесо на всех: отсчёт (тики) |
| `config.burmaldaholic.pvp.wheel.noMoreBetsTicks` | Wheel Party: no more bets (ticks) | Колесо на всех: «ставок больше нет» (тики) |
| `config.burmaldaholic.pvp.wheel.underdogShareBasisPoints` | Wheel Party: underdog share (basis points) | Колесо на всех: доля тёмной лошадки (б. п.) |
| `config.burmaldaholic.pvp.plinko.enabled` | Plinko Battle | Битва Плинко |
| `config.burmaldaholic.pvp.plinko.maxPlayers` | Plinko Battle: max players | Битва Плинко: макс. игроков |
| `config.burmaldaholic.pvp.plinko.ballChoices` | Plinko Battle: ball options | Битва Плинко: варианты числа шариков |
| `config.burmaldaholic.pvp.plinko.linkRadius` | Plinko Battle: machine link radius | Битва Плинко: радиус связи автоматов |
| `config.burmaldaholic.pvp.plinko.roundIntervalTicks` | Plinko Battle: auto-drop after (ticks) | Битва Плинко: автоброс через (тики) |
| `config.burmaldaholic.pvp.plinko.underdogBoost` | Plinko Battle: underdog boost | Битва Плинко: фора аутсайдеру |
| `config.burmaldaholic.pvp.scratch.enabled` | Scratch Showdown | Лотерейная битва |
| `config.burmaldaholic.pvp.scratch.maxPlayers` | Scratch Showdown: max players | Лотерейная битва: макс. игроков |
| `config.burmaldaholic.pvp.scratch.revealIntervalTicks` | Scratch Showdown: auto-scratch after (ticks) | Лотерейная битва: автостирание через (тики) |
| `config.burmaldaholic.pvp.scratch.weights` | Scratch Showdown: cell weights | Лотерейная битва: веса клеток |
| `config.burmaldaholic.pvp.scratch.values` | Scratch Showdown: cell values | Лотерейная битва: очки клеток |
| `config.burmaldaholic.pvp.side.enabled` | Spectator side bets | Ставки зрителей |
| `config.burmaldaholic.pvp.side.rakeBasisPoints` | Side bets: house cut (basis points) | Ставки зрителей: комиссия (б. п.) |
| `config.burmaldaholic.pvp.side.windowTicks` | Side bets: extra window (ticks) | Ставки зрителей: доп. время (тики) |
| `config.burmaldaholic.pvp.tournament.enabled` | Tournaments | Турниры |
| `config.burmaldaholic.pvp.tournament.minPlayers` | Tournaments: min players | Турниры: мин. участников |
| `config.burmaldaholic.pvp.tournament.maxPlayers` | Tournaments: max players | Турниры: макс. участников |
| `config.burmaldaholic.pvp.tournament.registrationTicks` | Tournaments: registration (ticks) | Турниры: запись (тики) |
| `config.burmaldaholic.pvp.tournament.roundGapTicks` | Tournaments: pause between rounds (ticks) | Турниры: пауза между раундами (тики) |
| `config.burmaldaholic.pvp.tournament.windowTicks` | Tournaments: leaderboard duration (ticks) | Турниры: длительность таблицы лидеров (тики) |
| `config.burmaldaholic.pvp.tournament.maxRunsPerPlayer` | Tournaments: runs per player | Турниры: попыток на игрока |
| `config.burmaldaholic.pvp.tournament.prizeSplit` | Tournaments: prize split (%%) | Турниры: деление призов (%%) |
| `config.burmaldaholic.pvp.tournament.autoEveryDays` | Scheduled tournament every N days | Турнир по расписанию раз в N дней |
| `config.burmaldaholic.pvp.tournament.autoTimeOfDay` | Scheduled tournament: time of day | Турнир по расписанию: время суток |
| `config.burmaldaholic.pvp.tournament.autoMode` | Scheduled tournament: game | Турнир по расписанию: игра |
| `config.burmaldaholic.pvp.tournament.autoFormat` | Scheduled tournament: format | Турнир по расписанию: формат |
| `config.burmaldaholic.pvp.tournament.autoEntry` | Scheduled tournament: entry | Турнир по расписанию: взнос |
| `config.burmaldaholic.pvp.race.maxSpins` | Jackpot Race: max spins | Гонка за джекпотом: макс. вращений |
| `config.burmaldaholic.pvp.race.intervalTicks` | Jackpot Race: spin interval (ticks) | Гонка за джекпотом: интервал (тики) |
| `config.burmaldaholic.pvp.heist.rounds` | Wheel Heist: rounds | Налёт на колесо: круги |
| `config.burmaldaholic.pvp.plinko.bumpers` | Plinko Battle: bumpers | Битва Плинко: отбойники |
| `config.burmaldaholic.pvp.scratchPoker.enabled` | Scratch Poker | Лотерейный покер |
| `config.burmaldaholic.pvp.series.enabled` | Coin Series (best of 3/5) | Серия на монетке (до 2/3 побед) |
| `config.burmaldaholic.pvp.slots.hazardWeights` | Slot Showdown: surprise weights | Битва автоматов: веса сюрпризов |
| `config.burmaldaholic.pvp.race.target` | Jackpot Race: target | Гонка за джекпотом: цель |

### bots

| Key | EN | RU |
|-----|----|----|
| `config.burmaldaholic.section.bots` | Seats and bots | Места и боты |
| `config.burmaldaholic.bots.enabled` | Bots | Боты |
| `config.burmaldaholic.bots.maxActiveTables` | Max tables with bots | Макс. столов с ботами |
| `config.burmaldaholic.bots.maxActive` | Max bots in the world | Макс. ботов в мире |
| `config.burmaldaholic.bots.maxConcurrentJobs` | Heavy bot jobs at once | Тяжёлых задач ботов одновременно |
| `config.burmaldaholic.bots.difficultyMix` | Bot level mix (Easy/Normal/Hard %%) | Состав ботов (лёгкие/нормальные/сложные, %%) |
| `config.burmaldaholic.bots.think.minTicks` | Bot think time min (ticks) | Мин. раздумье бота (тики) |
| `config.burmaldaholic.bots.think.maxTicks` | Bot think time max (ticks) | Макс. раздумье бота (тики) |
| `config.burmaldaholic.bots.think.tankTicks` | Hard bot extra think time (ticks) | Доп. раздумье сложного бота (тики) |
| `config.burmaldaholic.bots.think.fastFactor` | Fast speed multiplier | Множитель быстрого темпа |
| `config.burmaldaholic.bots.personalities` | Bot personalities | Характеры ботов |
| `config.burmaldaholic.bots.keepFreeSeatDefault` | Keep a seat free by default | Держать место свободным по умолчанию |
| `config.burmaldaholic.bots.showcase.enabled` | Showcase tables (bots play while watched) | Показательные столы (боты играют при зрителях) |
| `config.burmaldaholic.bots.table.policy` | %1$s: default players | %1$s: кто играет по умолчанию |
| `config.burmaldaholic.bots.table.count` | %1$s: default bots | %1$s: ботов по умолчанию |
| `config.burmaldaholic.bots.table.difficulty` | %1$s: default bot level | %1$s: уровень ботов по умолчанию |
| `config.burmaldaholic.bots.table.worldgenPolicy` | %1$s: players at generated tables | %1$s: кто играет за сгенерированными столами |
| `config.burmaldaholic.bots.table.worldgenCount` | %1$s: bots at generated tables | %1$s: ботов за сгенерированными столами |
| `config.burmaldaholic.bots.atmosphere.maxPerTable` | %1$s: max bots per table | %1$s: макс. ботов за столом |
| `config.burmaldaholic.bots.poker.easyMaxStake` | Highest stakes for Easy poker bots | Макс. ставки для лёгких покерных ботов |
| `config.burmaldaholic.bots.poker.easyMaxStake.micro` | Micro | Микро |
| `config.burmaldaholic.bots.poker.easyMaxStake.low` | Low | Низкие |
| `config.burmaldaholic.bots.poker.easyMaxStake.mid` | Mid | Средние |
| `config.burmaldaholic.bots.poker.easyMaxStake.high` | High | Высокие |
| `config.burmaldaholic.bots.chemmy.bankCapMultiple` | Bot bank cap (× table min) | Лимит банка бота (× мин. ставка) |
| `config.burmaldaholic.bots.pvp.fillDelayTicks` | PvP lobbies: bots fill after (ticks) | PvP-лобби: боты заходят через (тики) |
| `config.burmaldaholic.bots.pvp.maxPerMatch` | PvP: max bots per match | PvP: макс. ботов в матче |
| `config.burmaldaholic.bots.tournament.maxFill` | Tournaments: max bot fillers | Турниры: макс. ботов-заполнителей |
| `config.burmaldaholic.bots.tournament.fillToBracket` | Tournaments: fill the bracket with bots | Турниры: заполнять сетку ботами |
| `config.burmaldaholic.bots.owned.funding` | Bots at player casinos | Боты в казино игроков |
| `config.burmaldaholic.bots.owned.funding.owner_bankroll` | Paid from the bankroll | За счёт кассы заведения |
| `config.burmaldaholic.bots.owned.funding.disabled` | Not allowed | Запрещены |
| `config.burmaldaholic.bots.tableBuyInsPerDay` | Bot buy-ins per table per day | Бай-инов ботов на стол в день |
| `config.burmaldaholic.bots.vipWagerWeight` | VIP credit for chips vs bots | Зачёт ставок против ботов в ВИП |
| `config.burmaldaholic.bots.dailyWinCapMin` | Daily bot winnings limit, minimum | Дневной лимит выигрыша у ботов, минимум |
| `config.burmaldaholic.bots.dailyWinCapTierMultiple` | Daily bot winnings limit (× max bet) | Дневной лимит выигрыша у ботов (× макс. ставка) |
| `config.burmaldaholic.bots.sulkMultiplier` | Bots refuse at (× limit) | Боты отказываются играть при (× лимит) |
| `config.burmaldaholic.bots.adaptiveHeat` | Stronger bots for winning players | Сильные боты для выигрывающих игроков |
| `config.burmaldaholic.bots.debtorsMayPlay` | Debtors may play house bots | Должники могут играть с ботами заведения |
| `config.burmaldaholic.bots.avatars.mode` | Bot avatars | Аватары ботов |
| `config.burmaldaholic.bots.avatars.mode.none` | None | Нет |
| `config.burmaldaholic.bots.avatars.mode.nameplate` | Name tags | Таблички с именами |
| `config.burmaldaholic.bots.avatars.mode.entity` | Figures | Фигуры |
| `config.burmaldaholic.bots.avatars.maxEntities` | Max avatar figures | Макс. фигур ботов |
| `config.burmaldaholic.bots.chatter.enabled` | Bot chatter | Болтовня ботов |
| `config.burmaldaholic.bots.chatter.chance` | Bot chatter chance | Вероятность реплики бота |
| `config.burmaldaholic.bots.chatter.botCooldownTicks` | Bot chatter cooldown per bot (ticks) | Пауза между репликами бота (тики) |
| `config.burmaldaholic.bots.chatter.tableCooldownTicks` | Bot chatter cooldown per table (ticks) | Пауза между репликами за столом (тики) |
| `config.burmaldaholic.bots.chatter.maxPerMinute` | Bot lines per table per minute | Реплик ботов за столом в минуту |
| `config.burmaldaholic.bots.private.enabled` | Private tables | Закрытые столы |
| `config.burmaldaholic.bots.private.maxInvites` | Max invites per table | Макс. приглашений на стол |
| `config.burmaldaholic.bots.private.inviteRadius` | Invite radius (0 = anyone online) | Радиус приглашений (0 = любой онлайн) |

### Slot Showdown v1 config label (removed with Slot Showdown v2, `SLOTS.md` §9 / §13.10)

The other v1 slot config labels were removed with the slots v2 cut-over (S-J5).

| Key | EN | RU |
|-----|----|----|
| `config.burmaldaholic.pvp.slots.starPoints` | Slot Showdown: points for three stars | Битва автоматов: очки за три звезды |

## sounds (subtitles, core-owned)

| Key | EN | RU |
|-----|----|----|
| `subtitles.burmaldaholic.chip_place` | Chips clink | Звякают фишки |
| `subtitles.burmaldaholic.card_deal` | Card dealt | Сдаётся карта |
| `subtitles.burmaldaholic.card_shuffle` | Cards shuffle | Тасуются карты |
| `subtitles.burmaldaholic.dice_roll` | Dice roll | Катятся кости |
| `subtitles.burmaldaholic.roulette_spin` | Roulette spins | Крутится рулетка |
| `subtitles.burmaldaholic.slot_spin` | Reels spin | Крутятся барабаны |
| `subtitles.burmaldaholic.win` | Winning jingle | Звон выигрыша |
| `subtitles.burmaldaholic.lose` | Losing thud | Глухой стук проигрыша |
| `subtitles.burmaldaholic.jackpot` | Jackpot fanfare | Фанфары джекпота |
| `subtitles.burmaldaholic.coin_flip` | Coin flips | Подбрасывается монетка |
| `subtitles.burmaldaholic.plinko_peg` | Ball plinks | Шарик стучит по штырькам |
| `subtitles.burmaldaholic.wheel_tick` | Wheel ticks | Щёлкает колесо |
| `subtitles.burmaldaholic.scratch` | Card scratched | Стирается билет |
| `subtitles.burmaldaholic.golden_hour` | Golden bell tolls | Звенит золотой колокол |
| `subtitles.burmaldaholic.collector_knock` | Someone knocks | Кто-то стучит |
| `subtitles.burmaldaholic.loan_shark_idle` | Loan Shark hums | Ростовщик напевает |
| `subtitles.burmaldaholic.last_chance` | Coin spins in the dark | Монетка звенит во тьме |
| `subtitles.burmaldaholic.pvp_challenge` | Challenge horn | Рог вызова |
| `subtitles.burmaldaholic.pvp_drumroll` | Drumroll | Барабанная дробь |
| `subtitles.burmaldaholic.pvp_victory` | Victory fanfare | Победные фанфары |
| `subtitles.burmaldaholic.chip_stack` | Chips stack | Складываются фишки |
| `subtitles.burmaldaholic.chip_count` | Chips counted | Пересчитываются фишки |
| `subtitles.burmaldaholic.big_win` | Big win fanfare | Фанфары крупного выигрыша |
| `subtitles.burmaldaholic.nice_win` | Nice win chime | Сигнал хорошего выигрыша |
| `subtitles.burmaldaholic.mega_win` | Mega win fanfare | Фанфары мегавыигрыша |
| `subtitles.burmaldaholic.push` | Tie chime | Сигнал ничьей |
| `subtitles.burmaldaholic.ui_deny` | Action denied | Действие недоступно |
| `subtitles.burmaldaholic.toast` | Casino notice | Уведомление казино |
| `subtitles.burmaldaholic.streak_up` | Streak grows | Серия растёт |
| `subtitles.burmaldaholic.streak_break` | Streak broken | Серия прервана |
| `subtitles.burmaldaholic.vip_tier_up` | VIP fanfare | Фанфары ВИП |
| `subtitles.burmaldaholic.golden_hour_end` | Golden Hour fades | «Золотой час» угасает |
| `subtitles.burmaldaholic.chaos_good` | Lucky chime | Звон удачи |
| `subtitles.burmaldaholic.chaos_bad` | Ominous chord | Зловещий аккорд |
| `subtitles.burmaldaholic.chaos_teleport` | Teleport whoosh | Свист телепорта |
| `subtitles.burmaldaholic.collector_arrive` | Collectors arrive | Прибывают коллекторы |
| `subtitles.burmaldaholic.heartbeat` | Heartbeat | Стук сердца |
| `subtitles.burmaldaholic.coin_land` | Coin lands | Падает монетка |
| `subtitles.burmaldaholic.attract` | Machine chimes | Звенит автомат |
| `subtitles.burmaldaholic.card_slide` | Card slides | Скользит карта |
| `subtitles.burmaldaholic.card_flip` | Card flips | Переворачивается карта |
| `subtitles.burmaldaholic.card_squeeze` | Card slowly bent | Карту медленно отгибают |
| `subtitles.burmaldaholic.card_gather` | Cards gathered | Собирают карты |
| `subtitles.burmaldaholic.card_sting` | Card flourish | Карточный туш |
| `subtitles.burmaldaholic.chip_push` | Chips pushed in | Фишки уходят в банк |
| `subtitles.burmaldaholic.pot_win` | Pot collected | Забирают банк |
| `subtitles.burmaldaholic.table_knock` | Knock on the table | Стук по столу |
| `subtitles.burmaldaholic.roulette_ball_roll` | Roulette ball rolls | Катится шарик рулетки |
| `subtitles.burmaldaholic.roulette_ball_drop` | Ball hits a deflector | Шарик ударяется о ромб |
| `subtitles.burmaldaholic.roulette_ball_bounce` | Ball clatters | Шарик стучит по лункам |
| `subtitles.burmaldaholic.roulette_ball_settle` | Ball settles | Шарик останавливается |
| `subtitles.burmaldaholic.roulette_bell` | Croupier's bell | Звонок крупье |
| `subtitles.burmaldaholic.roulette_dolly` | Marker placed | Ставится маркер |
| `subtitles.burmaldaholic.chip_sweep` | Chips raked in | Сгребают фишки |
| `subtitles.burmaldaholic.dice_throw` | Dice thrown | Летят кости |
| `subtitles.burmaldaholic.dice_bounce` | Dice bounce | Подпрыгивают кости |
| `subtitles.burmaldaholic.dice_wall` | Dice hit the wall | Кости бьются о борт |
| `subtitles.burmaldaholic.dice_cup` | Dice rattle | Гремят кости в стакане |
| `subtitles.burmaldaholic.craps_puck` | Puck placed | Ставится шайба |
| `subtitles.burmaldaholic.coin_whoosh` | Coin whirs | Вращается монетка |
| `subtitles.burmaldaholic.wheel_stop` | Wheel stops | Останавливается колесо |
| `subtitles.burmaldaholic.plinko_bin` | Ball drops into a bin | Шарик падает в лунку |
| `subtitles.burmaldaholic.burn` | Cell burns | Сгорает клетка |
