# Burmaldaholic — Master String List (EN / RU)

Source of truth for every player-facing string. Language files for both editions
(`assets/burmaldaholic/lang/en_us.json`, `ru_ru.json`; Bedrock `texts/en_US.lang`, `ru_RU.lang`)
must contain exactly these keys with exactly these values (Bedrock: `%N$s` → `%N`, see
LOCALIZATION.md §2). Each module's team owns its section.

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

### Mod, creative tab, keybinds, game rule

| Key | EN | RU |
|-----|----|----|
| `modmenu.nameTranslation.burmaldaholic` | Burmaldaholic | Бурмалдоголик |
| `modmenu.summaryTranslation.burmaldaholic` | Turns survival into a casino. The house always wins. Almost. | Выживание превращается в казино. Заведение всегда в плюсе. Почти. |
| `itemGroup.burmaldaholic.main` | Burmaldaholic | Бурмалдоголик |
| `key.category.burmaldaholic` | Burmaldaholic | Бурмалдоголик |
| `key.burmaldaholic.open_menu` | Open Casino Menu | Открыть меню казино |
| `gamerule.burmaldaholic.casino_mode` | Casino mode | Режим казино |
| `gamerule.burmaldaholic.casino_mode.description` | Chips, tables, loans, chaos events and Last Chance. Turning it off keeps all saved data. | Фишки, столы, займы, хаос-события и «Последний шанс». При отключении все данные сохраняются. |
| `gui.burmaldaholic.core.create_world.casino_mode` | Casino Mode: %1$s | Режим казино: %1$s |
| `gui.burmaldaholic.core.create_world.casino_mode.tooltip` | Adds the Burmaldaholic casino economy to this world. Does not change difficulty. | Добавляет в мир экономику казино Бурмалдоголик. Сложность не меняется. |
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
| `block.burmaldaholic.slot_machine_copper` | Copper Bandit | Медный бандит |
| `block.burmaldaholic.slot_machine_gold` | Golden Reels | Золотые барабаны |
| `block.burmaldaholic.slot_machine_netherite` | Netherite High Roller | Незеритовый хайроллер |
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
| `tooltip.burmaldaholic.slot_machine_copper` | 1 payline · no jackpot | 1 линия · без джекпота |
| `tooltip.burmaldaholic.slot_machine_gold` | 3 paylines · progressive jackpot | 3 линии · накопительный джекпот |
| `tooltip.burmaldaholic.slot_machine_netherite` | 5 paylines · progressive jackpot · Gold VIP | 5 линий · накопительный джекпот · ВИП «Золото» |
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

### Messages

| Key | EN | RU |
|-----|----|----|
| `msg.burmaldaholic.core.welcome` | Welcome to Burmaldaholic! Here's your Casino Card and %1$s for luck. | Добро пожаловать в Бурмалдоголик! Держите клубную карту и %1$s на удачу. |
| `msg.burmaldaholic.core.welcome_hint` | Mine, fight and trade to earn chips. Spend them at the tables. Try not to spend all of them. | Копайте, сражайтесь и торгуйте — за это дают фишки. Тратьте их за столами. Желательно не все. |
| `msg.burmaldaholic.core.mode_enabled` | Casino mode is ON. The doors are open. | Режим казино ВКЛЮЧЁН. Двери открыты. |
| `msg.burmaldaholic.core.mode_disabled` | Casino mode is OFF. Your chips are safe until it reopens. | Режим казино ВЫКЛЮЧЕН. Фишки сохранятся до открытия. |
| `msg.burmaldaholic.core.earned` | +%1$s (%2$s) | +%1$s (%2$s) |
| `msg.burmaldaholic.core.source.trade` | Trade | Торговля |
| `msg.burmaldaholic.core.source.contract` | Contract | Заказ |
| `msg.burmaldaholic.core.source.cashback` | VIP cashback | ВИП-кешбэк |
| `msg.burmaldaholic.core.source.golden_hour` | Golden Hour bonus | Бонус «Золотого часа» |
| `msg.burmaldaholic.core.balance_capped` | Your balance is at the maximum. The rest went to charity. | Баланс на максимуме. Остальное ушло на благотворительность. |
| `msg.burmaldaholic.core.deposited` | Deposited %1$s. Balance: %2$s | Внесено: %1$s. Баланс: %2$s |
| `msg.burmaldaholic.core.withdrawn` | Withdrew %1$s. Balance: %2$s | Снято: %1$s. Баланс: %2$s |
| `msg.burmaldaholic.core.no_chips_to_deposit` | You have no chips to deposit | Вам нечего внести — фишек нет |
| `msg.burmaldaholic.core.bought_chips` | Bought %1$s for %2$s | Куплено: %1$s за %2$s |
| `msg.burmaldaholic.core.sold_chips` | Sold %1$s for %2$s | Продано: %1$s за %2$s |
| `msg.burmaldaholic.core.big_win` | Big win: %1$s takes %2$s at %3$s! | Крупный выигрыш: %1$s забирает %2$s — %3$s! |
| `msg.burmaldaholic.core.round_refunded` | The server restarted mid-round. Your bet of %1$s was refunded. | Сервер перезапустился посреди раунда. Ставка %1$s возвращена. |
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
| `gui.burmaldaholic.vip.perk.netherite_slots` | Netherite High Roller slots | Автомат «Незеритовый хайроллер» |
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

Ranks 2–9 use the digit directly (no key). Card glyphs carry the rank letter of the **English**
deck on the texture; the `rank.*` letters are used only for text-only rendering (Bedrock body text
fallback, narration).

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

---

## slots

| Key | EN | RU |
|-----|----|----|
| `gui.burmaldaholic.slots.spin` | Spin (%1$s) | Крутить (%1$s) |
| `gui.burmaldaholic.slots.auto` | Auto ×10 | Авто ×10 |
| `gui.burmaldaholic.slots.stop_auto` | Stop | Стоп |
| `gui.burmaldaholic.slots.line_bet` | Line bet: %1$s | Ставка на линию: %1$s |
| `gui.burmaldaholic.slots.lines` | Paylines: %1$s | Линий: %1$s |
| `gui.burmaldaholic.slots.jackpot` | JACKPOT %1$s | ДЖЕКПОТ %1$s |
| `gui.burmaldaholic.slots.last_win` | Last win: %1$s | Прошлый выигрыш: %1$s |
| `gui.burmaldaholic.slots.no_win` | No win this time | В этот раз мимо |
| `gui.burmaldaholic.slots.line_win` | Line %1$s: ×%2$s → %3$s | Линия %1$s: ×%2$s → %3$s |
| `gui.burmaldaholic.slots.spin_total` | Spin result: %1$s | Итог вращения: %1$s |
| `gui.burmaldaholic.slots.auto_summary` | %1$s: bet %2$s, won %3$s | %1$s: поставлено %2$s, выиграно %3$s |
| `gui.burmaldaholic.slots.auto_stopped_big_win` | Auto-spin stopped: big win! | Автоигра остановлена: крупный выигрыш! |
| `gui.burmaldaholic.slots.auto_stopped_funds` | Auto-spin stopped: not enough chips | Автоигра остановлена: не хватает фишек |
| `gui.burmaldaholic.slots.paytable.three` | Three %1$s: ×%2$s | Три «%1$s»: ×%2$s |
| `gui.burmaldaholic.slots.paytable.berry_1` | Sweet Berries on reel 1: ×%1$s | Ягода на первом барабане: ×%1$s |
| `gui.burmaldaholic.slots.paytable.berry_2` | Sweet Berries on reels 1–2: ×%1$s | Ягоды на первых двух барабанах: ×%1$s |
| `gui.burmaldaholic.slots.paytable.wild` | Totem is wild: it replaces any fruit, gem or seven | Тотем — вайлд: заменяет любой фрукт, камень или семёрку |
| `gui.burmaldaholic.slots.paytable.star` | Three Nether Stars: progressive jackpot | Три звезды Незера: накопительный джекпот |
| `gui.burmaldaholic.slots.paytable.star_owned` | Three Nether Stars: ×%1$s | Три звезды Незера: ×%1$s |
| `gui.burmaldaholic.slots.paytable.chaos` | %1$s: something happens… | %1$s: что-то случится… |
| `gui.burmaldaholic.slots.paytable.max_bet_jackpot` | Full jackpot needs the maximum bet; smaller bets win a share | Весь джекпот — только при максимальной ставке; меньшая ставка даёт долю |
| `gui.burmaldaholic.slots.symbol.berry` | Sweet Berries | Сладкие ягоды |
| `gui.burmaldaholic.slots.symbol.apple` | Apple | Яблоко |
| `gui.burmaldaholic.slots.symbol.carrot` | Golden Carrot | Золотая морковь |
| `gui.burmaldaholic.slots.symbol.emerald` | Emerald | Изумруд |
| `gui.burmaldaholic.slots.symbol.diamond` | Diamond | Алмаз |
| `gui.burmaldaholic.slots.symbol.seven` | Redstone Seven | Редстоуновая семёрка |
| `gui.burmaldaholic.slots.symbol.wild` | Totem (Wild) | Тотем (вайлд) |
| `gui.burmaldaholic.slots.symbol.creeper` | Creeper | Крипер |
| `gui.burmaldaholic.slots.symbol.tnt` | TNT | Динамит |
| `gui.burmaldaholic.slots.symbol.pearl` | Ender Pearl | Жемчуг Края |
| `gui.burmaldaholic.slots.symbol.clock` | Clock | Часы |
| `gui.burmaldaholic.slots.symbol.star` | Nether Star | Звезда Незера |
| `msg.burmaldaholic.slots.three_creepers` | Three creepers. That hissing isn't the machine. | Три крипера. Это шипит не автомат. |
| `msg.burmaldaholic.slots.three_tnt` | Three TNT! The machine calls in some friends. | Три динамита! Автомат зовёт друзей. |
| `msg.burmaldaholic.slots.three_pearls` | Three pearls — pack your bags! | Три жемчужины — пакуйте чемоданы! |
| `msg.burmaldaholic.slots.three_clocks` | Three clocks! It's Golden Hour o'clock! | Трое часов! Пробил «Золотой час»! |
| `msg.burmaldaholic.slots.three_clocks_cooldown` | Three clocks! Golden Hour is recharging, but the payout is yours. | Трое часов! «Золотой час» перезаряжается, но выигрыш ваш. |
| `msg.burmaldaholic.slots.jackpot_self` | JACKPOT! You won %1$s! | ДЖЕКПОТ! Вы сорвали %1$s! |
| `msg.burmaldaholic.slots.jackpot_broadcast` | JACKPOT! %1$s hit %2$s on %3$s! | ДЖЕКПОТ! %1$s срывает %2$s на автомате «%3$s»! |
| `msg.burmaldaholic.slots.seven_title` | Triple seven! | Три семёрки! |

---

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

---

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

---

## advancements

Java: advancement tab; Bedrock: Achievements page. Titles are short (≤ 24 chars RU where possible).

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
| `advancement.burmaldaholic.shark_hunter.description` | Take every chip from a Shark bot | Оставьте бота-акулу без фишек |
| `advancement.burmaldaholic.three_sevens.title` | Lucky Sevens | Три топора |
| `advancement.burmaldaholic.three_sevens.description` | Hit three Redstone Sevens on a payline | Соберите три семёрки на линии |
| `advancement.burmaldaholic.jackpot.title` | JACKPOT! | ДЖЕКПОТ! |
| `advancement.burmaldaholic.jackpot.description` | Win a progressive jackpot | Сорвите накопительный джекпот |
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
| `gui.burmaldaholic.achievements.unlocked` | Achievement unlocked: %1$s | Достижение получено: %1$s |
| `gui.burmaldaholic.achievements.progress` | %1$s of %2$s unlocked | Получено %1$s из %2$s |

---

## config

Labels shown on the Java config screen and the Bedrock Admin → World settings forms.
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

### Family templates

| Key | EN | RU |
|-----|----|----|
| `config.burmaldaholic.contracts.weight` | Contract weight: %1$s | Вес заказа: %1$s |
| `config.burmaldaholic.wager.appraisal` | Appraisal: %1$s | Оценка: %1$s |
| `config.burmaldaholic.slots.weights` | %1$s — symbol weights | %1$s — веса символов |
| `config.burmaldaholic.slots.pays` | %1$s — payouts | %1$s — выплаты |
| `config.burmaldaholic.slots.berryPartial` | %1$s — berry pays | %1$s — выплаты за ягоды |
| `config.burmaldaholic.chaos.weight` | Chance weight: %1$s | Вес события: %1$s |
| `config.burmaldaholic.chaos.event.enabled` | Enabled: %1$s | Включено: %1$s |

### core, economy, contracts, wager

| Key | EN | RU |
|-----|----|----|
| `config.burmaldaholic.core.casinoMode` | Casino mode | Режим казино |
| `config.burmaldaholic.core.giveCasinoCardOnJoin` | Give Casino Card on first join | Выдавать клубную карту новичкам |
| `config.burmaldaholic.core.hud.enabled` | HUD panel | Панель на экране |
| `config.burmaldaholic.core.hud.position` | Default HUD corner | Угол панели по умолчанию |
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
| `config.burmaldaholic.slots.copper.maxLineBet` | Copper Bandit: max line bet | Медный бандит: макс. ставка на линию |
| `config.burmaldaholic.slots.gold.maxLineBet` | Golden Reels: max line bet | Золотые барабаны: макс. ставка на линию |
| `config.burmaldaholic.slots.netherite.minLineBet` | Netherite: min line bet | Незеритовый: мин. ставка на линию |
| `config.burmaldaholic.slots.netherite.maxLineBet` | Netherite: max line bet | Незеритовый: макс. ставка на линию |
| `config.burmaldaholic.slots.netherite.minVipTier` | Netherite: required VIP tier | Незеритовый: нужный ВИП-статус |
| `config.burmaldaholic.slots.jackpot.contribution.gold` | Jackpot contribution: Golden Reels | Отчисления в джекпот: Золотые барабаны |
| `config.burmaldaholic.slots.jackpot.contribution.netherite` | Jackpot contribution: Netherite | Отчисления в джекпот: Незеритовый |
| `config.burmaldaholic.slots.jackpot.seed.gold` | Jackpot seed: Golden Reels | Стартовый джекпот: Золотые барабаны |
| `config.burmaldaholic.slots.jackpot.seed.netherite` | Jackpot seed: Netherite | Стартовый джекпот: Незеритовый |
| `config.burmaldaholic.slots.ownedStarPays` | Three stars pay at player casinos | Выплата за три звезды в казино игроков |
| `config.burmaldaholic.slots.spinTicks` | Spin animation (ticks) | Анимация вращения (тики) |
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
