# Changelog

Формат: версия, дата, изменения. Первая цифра версии меняется только по явной просьбе автора.

## Unreleased
- Проект инициализирован документами, код не начат.
- M0: проект собирается и запускается на NeoForge 21.1.265, подключены JUnit 5 и dev-загрузка примера книги.
- M0.1: автотест в игре: сам открывает отдельный мир, проходит сценарий, снимает скриншот и выходит.
- M1: книга квестов загружается из датапаков, проверяется и передаётся клиентам; команды `/rysquests reload` и `validate`.
- Первая играбельная версия: прогресс, сдача и награды, экран книги по клавише K, импорт книги и прогресса из FTB Quests (`/rysquests import ftbq`).
- Книга как в макетах: Поток, Карта с автораскладкой, Что дальше, Список, Лента, закрепление и стикеры, поиск, кнопка в панели FTB.
- Миграции прогресса, команды, стадии, лут-таблицы, жидкости, кулдауны повторяемых квестов.
- Рецепты из книги через JEI и RYScope, инструменты разработчика: вкладка Dev, отчёты в JSON и CSV, офлайн-проверка книги на Python.
- Все экраны и стикеры приведены к макетам на RYSCore 1.4: шрифт Inter, рейка глав, колонка чтения с рецептом, воронка в Dev, доска справа сверху.
- Разделы книги и гайды: рейка разделов, библиотека гайдов, страницы с предметами, рецептами и картинками, отметка прочитанного, кнопка «Как это работает» у квеста.
- Кастомные вкладки на шаблонах Матрица, Карточки и Страницы, закрепление способа на HUD, правка файла вкладки с `/rysquests reload`.
- Редактор гайдов и вкладок «Страницы» в игре, в одиночной игре и на сервере: страницы, вставки, картинки из снимков экрана, отмена и повтор, предпросмотр.
- Конструктор вкладок в игре: порядок рейки, новые вкладки из шаблонов, таблица Матрицы и Карточки в режиме правки, просмотр глазами игрока.
- Магазин: валюта на сервере и награда `currency`, покупка и продажа, лимиты на игрока и команду, казна команды, история операций, отчёт о сделках, печатающих деньги.
- Достижения: уровни с наградами, скрытые достижения, редкость по серверу, титулы перед именем, закрепление на экране.
- Команды кошелька, казны, лимитов магазина, титулов и достижений; `RYSQuestsWallet` в KubeJS для зачисления, списания и обмена физических монет.
- Редактор квестов в игре: глава на карте, зависимости перетаскиванием, задачи и награды, автораскладка, правка JSON, подсветка внешних изменений.
- Импорт прогресса FTB Quests учитывает, кто из отряда уже забрал награду.
- Страницы показывают настоящие рецепты JEI, включая машины Create, и открывают размышления Ponder.
- 3D-структуры на страницах: вращение, слои, список блоков, голограмма, импорт книг Patchouli.
- Тихий звук при получении награды, покупке и продаже, крупные значки в матрице, цвета медалей одинаковы во всех темах, кнопка выхода из Dev.
- Награды на выбор: кнопка просит выбрать награду, «Забрать всё» выдаёт остальное и открывает квест с выбором. Клик по главе больше не перехватывает квест на карте.
- Книга рисуется быстрее, текст на отдалённой карте не расплывается (RYSCore 1.7.1).
- Квесты в строке «требует» под названием квеста стали ссылками на эти квесты.
- История переходов: Backspace и кнопка со стрелкой на странице гайда возвращают туда, откуда пришёл.
- Закреплённые квесты отмечены булавкой на карте, кнопка закрепления с булавкой, переключатель карты подписан глаголом.
- Карта главы: подглавы раскладываются блоками выше и ниже главного пути, длинные столбцы переносятся, глава стоит по центру, подписи рамок не уходят под квесты, список глав прокручивается перетаскиванием.
- Награды в карточке квеста переносятся на новый ряд, опыт показан пузырьком, кнопка «Забрать» не перекрывается.
- Ссылки на квесты других глав на карте, задача на опыт и задача на крафт, импорт ссылок и этих задач из FTB Quests.
- Квесты после повторяемого не закрываются между раундами, награды команды в повторяемых квестах не теряются в следующем раунде.
- Награды-команды и таблицы добычи видны и забираются с карты, жидкость и опыт сдаются кнопкой, «Сдать всё готовое» сообщает итог.
- Книга работает быстрее: текст не раскладывается заново каждый кадр, счётчики и подписи считаются только при изменениях.
- Обрезанные названия показывают полный текст при наведении.
- Импорт из FTB Quests переносит команды, таблицы добычи, стадии, достижения, жидкости, биомы и места; команды понимают подстановки FTB.
- 0.2.1: квест, у которого все задачи уже выполнены, а сам он не засчитан (прогресс, перенесённый из FTB Quests), засчитывается сам при входе и через секунду после открытия; на спирали последняя эпоха вьётся к финалу книги в центре, разделители эпох стоят посередине промежутков; наведение на главу по её настоящей форме, клик по всей ячейке значка; подложка под значками квестов; переключатель «Побочные связи» в легенде карты; полёт к главе без рывка.
- 0.2.3 (черновик): значки предметов на карте больше не сдвигаются и не дрожат относительно своих квестов, когда карту перетаскивают: плитка квеста и её значок ставятся на целые пиксели экрана от одной точки.
  EN (draft): item icons on the map no longer shift or jitter against their quests while the map is dragged: a quest tile and its icon are placed on whole screen pixels from one point.
- 0.2.4 (черновик): выход из команды больше не возвращает награды, которые игрок уже забрал в ней; одна повреждённая запись в сохранении прогресса больше не стирает прогресс остальных.
  EN (draft): leaving a team no longer makes the rewards already taken in it claimable again; one damaged record in the progress save no longer wipes everyone else's progress.
- 0.2.5: повторяемый квест в команде больше не забирает незабранные награды круга у тех, кто не успел: они ждут каждого, пока он их не заберёт, даже когда команда уже на новом круге. Тратить казну команды в магазине теперь может только тот, кому RYSTeams даёт право «Тратить казну команды» (по умолчанию лидер и офицеры).
  EN: a repeatable quest in a team no longer takes away a round's rewards from those who had not claimed them yet: they wait for each player until claimed, even after the team moved on to the next round. Paying from the team treasury in the shop now needs the RYSTeams permission "Spend the team treasury" (leader and officers by default).
- 0.2.6 (черновик): магазин может продавать предметы с особыми свойствами (именем, зачарованием, данными мода), и игрок получает их как есть; карточка и подсказка показывают настоящий предмет. Покупка «1 раз на команду» видна сразу: так и написано на карточке, а после покупки там стоит «куплено командой». Выход из команды, роспуск и повторное вступление больше не дают купить такой товар второй раз.
  EN (draft): the shop can sell items with special properties (a name, an enchantment, a mod's data), and the player gets them as they are; the card and the tooltip show the real item. A purchase "once per team" says so on its card and shows "bought by your team" afterwards. Leaving a team, disbanding it or joining again no longer lets such an item be bought a second time.
- 0.2.7: большое обновление магазина.
  - Трофеи боссов. Товар может ждать победы над боссом: он открывается всей команде после первой победы кого-то из неё, навсегда, и закрытая карточка так и пишет: «победи: Игнис». Победа засчитывается, если последний удар нанёс игрок, а не механизм.
  - Поставки. Дорогой товар можно оплатить частями: предметы сдаются кнопкой «Сдать из инвентаря» в общий счёт команды, карточка показывает, сколько собрано. Когда всё собрано, товар забирает любой участник, и монеты списываются в этот момент (свои или из казны, если есть право). Сданное не уходит из команды вместе с игроком и не засчитывается дважды.
  - Новый блок «Приёмщик». Продаёт всё, что приходит в него по трубам, воронкам и из хоппера, или сдаёт предметы в поставку команды. Берёт только нужное и только недостающее, остальное остаётся в трубе. Монеты идут в свой кошелёк или в казну команды.
  - Новый блок «Выдача». Сам покупает выбранный товар за свои монеты или из казны (нужно право тратить казну) и кладёт его в соседний инвентарь или в свой слот, откуда его забирают трубы. Можно ограничить траты, оставить запас монет на счету, держать в инвентаре не больше заданного числа и включать блок сигналом редстоуна. Монеты списываются, только когда товару есть место.
  - У обоих блоков лампа и строка в окне показывают, работает ли блок. Есть сигнал компаратора и уведомление владельцу, когда блок встал: не чаще раза в минуту, выключается в окне. Открывают и ломают блоки только владелец, его команда и оператор. Блоки крафтятся по умолчанию из ванильных предметов (железо, изумруд, воронка или раздатчик), сборка может заменить рецепт.
  - Для авторов сборок: новые поля сделки `lock_text` (своя строка закрытой карточки) и `delivery` (поставка). Редактор книги умеет всё это: поставку с количествами, открытие после победы над боссом, строку закрытой карточки. При правке товара редактор больше не теряет поля, которых не показывает.
  - Награда эпохи теперь на карте. Когда эпоха пройдена, на её плашке и в строке сайдбара появляется значок, а клик по плашке открывает карточку эпохи: что это за эпоха, какие ветки она открывает, награда и кнопка «Забрать награду». Награда ждёт и на «Сейчас». Кто уже забрал её раньше, второй раз не получит, кто не забрал, ничего не теряет.
  - Страницы «О книге». Название книги слева вверху стало кнопкой: оно открывает страницы о том, как устроена книга, поверх текущей вкладки. Пока ни одна страница не прочитана, на значке горит точка.
  - «Атлас сборки» и «Атлас добычи» теперь видят все игроки во вкладке «Гайды», раньше их видел только оператор.
  - Для авторов сборок: эпоха в `book.json` может назвать квест своей награды (`"quest"`), поле `"about"` задаёт страницы «О книге» (гайд книги или свои страницы).
  EN: a big update of the shop.
  - Boss trophies. A trade can wait for a boss: it opens for the whole team, for good, after the first win over that boss by any member, and its closed card says so: "defeat: Ignis". A win counts when a player, not a machine, lands the last blow.
  - Deliveries. An expensive trade can be paid in parts: items go into one count of the team with "Deliver from inventory", and the card shows how far it has come. Once everything is in, any member takes the goods and the coins are paid then (own coins, or the treasury with the right). What was delivered does not leave with a player and never counts twice.
  - A new block, the Shop Receiver. It sells everything that comes in through pipes, funnels or a hopper, or brings the items into a team delivery. It takes only what is needed and only what is still missing, everything else stays in the pipe. The coins go to your own wallet or to the team treasury.
  - A new block, the Shop Dispenser. It buys the chosen trade by itself with your own coins or from the treasury (needs the right to spend it) and puts the goods into the inventory next to it, or into its own slot for pipes to take. Spending can be limited, a reserve of coins kept on the account, the stock in the inventory capped, and the block switched by redstone. Coins are taken only when there is room for the goods.
  - Both blocks show with a lamp and a line in their window whether they work. They give a comparator signal and tell the owner when they stop: at most once a minute, it can be switched off in the window. Only the owner, their team and an operator can open or break them. Both blocks are crafted by default from vanilla items (iron, an emerald, a hopper or a dispenser), and a modpack can replace the recipe.
  - For pack authors: new trade fields `lock_text` (the closed card's own line) and `delivery`. The book editor handles all of it: deliveries with counts, opening after a boss, the closed card's line. Editing a trade no longer loses the fields the editor does not show.
  - The epoch reward now lives on the map. Once an epoch is passed, its plate and its row in the sidebar get a mark, and a click on the plate opens the epoch's card: what the epoch is about, which branches it opens, the reward and "Claim reward". The reward also waits on "Now". Whoever took it before will not get it twice, and whoever did not loses nothing.
  - About the book. The book's name at the top left is now a button: it opens pages on how the book works over the current tab. Until a page is read, a dot lights on the icon.
  - "Build Atlas" and "Mining Atlas" are now visible to every player in the Guides tab; before, only an operator saw them.
  - For pack authors: an epoch in `book.json` can name the quest of its reward (`"quest"`), and the `"about"` field sets the About pages (a guide of the book or pages of their own).
- 0.2.8 (черновик):
  - Достижения на статистике игры в команде считают всех её участников. Раньше счётчик команды был числом одного лучшего участника, и остальные не могли его сдвинуть: «Парашютист» стоял на 19.7 из 20 км напарника, сколько бы ни падал другой игрок. Теперь падения, прыжки, смерти, пройденные километры и остальная статистика складываются по всей команде, включая тех, кто сейчас не в игре.
  - «Время без смерти» и «время без сна» считаются по одной жизни, как и написано: это самая длинная текущая жизнь среди участников команды, и после смерти счёт начинается заново. Раньше счётчик навсегда запоминал самый долгий прошлый отрезок. Полученные уровни остаются.
  - У закреплённого на экране уровня достижения появилась тонкая полоска прогресса до этого уровня, его цветом: бронза, серебро или золото, как в окне достижений.
  - Ссылки на сайты на страницах гайдов и «О книге» открываются: при наведении ссылка подсвечивается, по клику игра спрашивает, открыть ли её, как в чате. Ссылку можно записать как `[[url:https://…|подпись]]`, как `[подпись](https://…)` или просто адресом.
  EN (draft):
  - Achievements on game statistics count every member of a team. Before, a team's counter was the number of its best member alone, and nobody else could move it: "Skydiver" stood at a teammate's 19.7 of 20 km however far another player fell. Now falls, jumps, deaths, distances and every other statistic add up over the whole team, members who are offline included.
  - "Time since death" and "time since rest" count one life, as their text says: the longest current life among the members, starting again after a death. Before, the counter kept the longest past stretch for good. Levels already reached stay.
  - A level of an achievement pinned to the screen has a thin progress bar up to that level, in its color: bronze, silver or gold, as in the achievements window.
  - Web links on the pages of guides and About the book open: a link lights up under the pointer, and a click asks whether to open it, as in chat. A link can be written as `[[url:https://…|label]]`, as `[label](https://…)` or as a bare address.
