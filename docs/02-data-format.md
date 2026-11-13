# 02 · Формат данных

Всё в датапаке под `data/<namespace>/rysquests/`. Namespace обычно сборки (`repys`), не мода. Все id это `ResourceLocation`. Все строки, которые видит игрок, это lang-ключи либо literal; literal допускается и переводится через lang, если такой ключ есть.

## Книга: `book.json`

```json
{
  "progress_mode": "team",            // solo | team | server
  "default_view": "flow",             // home | flow | map | list
  "default_theme": "ryscore:slate",
  "title": "book.repys.title",
  "icon": "kubejs:brass_mechanism",
  "chapter_order": ["repys:01_create", "repys:02_power"],
  "hud": { "max_pinned": 3 },
  "sections": ["story", "guides", "repys:mining_atlas"], // рейка разделов, без поля её нет
  "epochs": ["book.repys.epoch.1", "book.repys.epoch.2"], // названия эпох, первое для "epoch": 1
  "map_form": "auto",                 // auto | serpentine | spiral | generator | galaxy
  "published": true                   // книга открыта игрокам
}
```

Если `book.json` отсутствует, значения по умолчанию: `solo`, `flow`, `ryscore:slate`, `auto`, открыта.

Новые поля необязательные, старые книги читаются как раньше:

- `epochs`: названия эпох по порядку, ключ lang или литерал; глава с `epoch` за концом списка остаётся без названия. Эпоха, которую создал импорт FTB из группы глав, пишется объектом `{ "title": "...", "imported": true }`: это раздел книги, а не эпоха автора, и автовыбор формы её не считает (раздел 5 плана). Эпоха автора пишется строкой.
- `map_form`: форма карты. `auto` выбирает по правилу `MapForms`: большинство глав на карте с эпохой автора → `serpentine`; меньше 5 % связей между квестами ведут из главы в главу и глав 8 и больше → `galaxy`; иначе `generator`. `spiral` только по выбору автора.
- `published`: замок публикации. Книгу без поля читаем открытой, чтобы уже выпущенные книги, как RepYS, не закрылись. Закрытая книга игрокам не отправляется вовсе: они получают пустую книгу с названием и значком, сервер не считает их прогресс и не принимает их действия и покупки. Операторы (права 2) видят книгу целиком. Импорт FTB пишет `false`, открывает книгу оператор из книги (пакет `PublishPayload`).

Книга одна на сервер: если `book.json` есть в нескольких namespace, берётся первый по алфавиту, об остальных предупреждает `/rysquests validate` (ADR-21). Главы из `chapter_order` идут первыми, остальные после них по id.

`default_theme` пока не применяется: выбор темы один на все моды семьи и хранится в RYSCore (ADR-23).

## Глава: `chapters/<dir>/_chapter.json`

```json
{
  "id": "repys:02_power",
  "title": "chapter.repys.power.title",
  "subtitle": "chapter.repys.power.subtitle",
  "icon": "kubejs:electric_mechanism",
  "epoch": 2,                          // необязательно, для ленты эпох
  "default_view": "map",               // переопределяет книгу
  "groups": [                          // подглавы для рамок на карте
    { "id": "basics", "title": "chapter.repys.power.basics" } ],
  "requires": ["repys:01_create"],     // главы-зависимости (все)
  "hidden": false,
  "section": "story",                  // раздел книги, по умолчанию story
  "off_map": false                     // не на карте: блок «Вне карты», открывается списком
}
```

`off_map` необязательное: глава остаётся в книге и в счёте, но на карте её нет, она в блоке «Вне карты» сайдбара (как «Начало» и «Летопись» RepYS). Автовыбор формы её не считает.

Поле `section` заложено для разделов из `09-sections.md`. Сейчас книга показывает только главы с `story`. Глава с другим значением загружается, но в книге не видна.

Прогресс квеста хранит необязательное `times`: сколько раундов повторяемого квеста уже пройдено. Квесты после повторяемого открыты, если он выполнен сейчас или раньше (ADR-46). Старые сохранения читаются как `times: 0`.

Режим `accumulate` у задач на предмет и жидкость устарел: он читается и работает как `observe`, проверка книги предупреждает (ADR-48). Поле главы `requires` проверяется: квест главы не должен зависеть от квеста главы вне цепочки её `requires`; замка на главе нет. Команда награды: необязательный `level` (0–4, по умолчанию 4) для `as_server`; подстановки FTB Quests `{p}`, `{x}`, `{y}`, `{z}`, `{quest}`, `{chapter}`, `{team}`, `{team_id}`, `{long_team_id}`, `{member_count}`, `{online_member_count}`, неизвестные остаются как есть. Задача посещения с `size` [w, h, d] проверяет область блоков от `pos` до `pos + size`, без него `pos` это центр шара с `radius`.

Задача `rysquests:xp`: `levels` или `points`, необязательное `consume` (сдать кнопкой). У задачи на предмет режим `craft` считает скрафченное и выплавленное.

Поле главы `links`: список id квестов других глав, которые показываются и на её карте (ADR-46). Узел-ссылка подписан главой квеста, клик открывает квест в его главе. Ссылка не меняет счёт главы и прогресс.

## Квест: `chapters/<dir>/<quest>.json`

Один файл на квест. Допускается массив квестов в файле `_quests.json` для маленьких книг.

```json
{
  "id": "repys:power/electric_mechanism",
  "chapter": "repys:02_power",
  "group": "basics",
  "title": "quest.repys.power.electric_mechanism.title",
  "subtitle": "quest.repys.power.electric_mechanism.subtitle",
  "text": "quest.repys.power.electric_mechanism.text",
  "icon": "kubejs:electric_mechanism",
  "requires": ["repys:power/wires", "repys:power/dynamo"],
  "requires_mode": "all",              // all | any | {"n": 2}
  "stage_required": null,              // строка стадии или null
  "tasks": [ ... ],
  "rewards": [ ... ],
  "layout": { "pin": false, "x": null, "y": null },
  "milestone": false,                  // веха: крупный узел; старое "size": 2 читается как веха
  "optional": false,
  "hidden": false,                     // не отправлять клиенту, пока зависимости не выполнены
  "repeatable": null,                  // null или {"cooldown_minutes": 60}
  "page": false,                       // true: страница без задач, засчитывается прочтением
  "guide": "repys:create#rotation",    // гайд или его страница, даёт кнопку «Как это работает»
  "tags": ["milestone"],               // milestone: веха в Ленте, key_item: ключевой предмет эпохи
  "draft": false,                      // черновик: игроки его не видят, пока автор не поправит
  "archived": false                    // в архиве: убран из книги без удаления файла
}
```

- `milestone`: веха. Старое `size: 2` читается как веха, при записи пишется `milestone`, а `size` больше не пишется.
- `optional`: необязательный квест не входит в счёт главы, эпохи и книги; его считают отдельно («побочные», `BookCounts`).
- `draft`: черновик. Сервер не отправляет его игрокам и не считает, операторы видят его в редакторе. Так импорт FTB оставляет квест, у которого задачу пришлось заменить галочкой или награду выбросить. Снять флаг должен автор: правка в редакторе.
- `archived`: архив вместо удаления. Игрокам не отправляется, не считается. Квест, который ждёт черновик или квест в архиве, игроки не откроют: проверка книги предупреждает (`requires_draft`, `requires_archived`).
- `layout`: с `pin: true` координаты `x`, `y` ставят квест на ручное место на карте главы (их пишет редактор). Без `pin` координаты только запомнены и карта их не читает: так импорт FTB сохраняет положение квеста из FTB, одна клетка FTB равна 24 единицам, шагу сетки редактора.

Поля `chapter` можно опускать: тогда берётся из папки. `requires` ссылаются только на квесты; зависимость от главы задаётся на главе.

## Гайд: `guides/<id>.json`

Один файл на гайд, тексты в lang. Подробно в `09-sections.md`, раздел «Гайды».

```json
{
  "id": "repys:create",
  "title": "guide.repys.create.title",
  "icon": "create:cogwheel",
  "category": "guide.repys.category.tech",   // необязательно, чип в библиотеке
  "stage": "repys:epoch_3",                  // необязательно, закрыт до стадии
  "stage_title": "guide.repys.stage.epoch_3", // необязательно, название стадии для игрока
  "pages": [
    { "id": "rotation", "title": "guide.repys.create.rotation.title", "text": "guide.repys.create.rotation.text" }
  ]
}
```

## Кастомная вкладка: `sections/<id>.json`

Один файл на вкладку, поле `template`: `matrix`, `cards` или `pages`. Id вкладки это путь файла. Формат шаблонов в `09-sections.md`, примеры в `examples/example_pack`.

## Сделка магазина: `shop/<id>.json`

```json
{
  "id": "repys:iron_bundle",
  "icon": "minecraft:iron_ingot",
  "category": "shop.repys.resources",
  "cost":  [ { "type": "currency", "id": "coin", "amount": 40 } ],
  "gives": [ { "type": "item", "id": "minecraft:iron_ingot", "count": 32 } ],
  "limit": { "per": "player", "count": 3, "reset": "daily" },  // per: player | team; reset: never | daily | weekly
  "stage": "repys:epoch_2",
  "stage_title": "shop.repys.epoch_2",
  "quest": "repys:power/wires"                                  // закрыта до выполнения квеста
}
```

Продажа это сделка, которая даёт только валюту. Название валюты берётся из ключа `rysquests.currency.<id>`. Награда квеста: `{ "type": "rysquests:currency", "id": "coin", "amount": 50 }`, с `team_mode: once` идёт в казну команды. Раздел включается строкой `shop` в `sections` файла `book.json`.

## Достижение: `achievements/<id>.json`

```json
{
  "id": "repys:factory_worker",
  "category": "achievement.repys.tech",
  "icon": "minecraft:crafter",
  "hidden": false,
  "task": { "type": "rysquests:stat", "stat": "minecraft:custom", "target": "minecraft:jump" },
  "tiers": [
    { "tier": "bronze", "amount": 10,   "rewards": [ { "type": "rysquests:currency", "id": "coin", "amount": 10 } ] },
    { "tier": "silver", "amount": 100,  "rewards": [ { "type": "rysquests:title", "id": "title.repys.worker" } ] },
    { "tier": "gold",   "amount": 1000 }
  ]
}
```

Необязательное `archived: true` убирает достижение в архив: игрокам оно не отправляется, его уровни не считаются. Цвет уровня задаёт место, а не имя: последний уровень золотой, перед ним серебряный, все более ранние бронзовые (`Achievement.medal`); `tier` теперь только id уровня.

Количество в `task` задавать не нужно: его подставляет `amount` уровня. Подходят `kill`, `stat`, `item` (без `mode` это `observe`) и `advancement` (у неё один уровень с `amount: 1`); задача со сдачей или галочкой не считается сама, валидатор предупредит. Тексты: ключи `achievement.<ns>.<id>.name`, `.text`, `.hint` (подсказка скрытого), либо поля `title`, `text`, `hint`. Раздел включается строкой `achievements` в `sections`.

## Структура: `structures/<id>.json`

Поля `mapping` и `pattern` как у мультиблока Patchouli, необязательное `title`. Id это путь файла. Слои сверху вниз, пробел воздух, `_` что угодно, `0` опорный блок. Вставка на страницу: `[[structure:repys:assembly_hall|подпись]]`. Примеры и подробности в `GUIDE.md`, раздел 10.

## Задачи

Все задачи имеют `type`, `count` (по умолчанию 1), `mode` где применимо (`consume` | `observe` | `accumulate`), необязательный `title`.

```json
{ "type": "rysquests:item", "item": "#c:plates/copper", "count": 16, "mode": "consume" }
{ "type": "rysquests:item", "item": "kubejs:electric_mechanism", "count": 3, "mode": "observe",
  "nbt": { "strict": false, "components": { ... } } }
{ "type": "rysquests:item", "any_of": ["minecraft:oak_log", "minecraft:birch_log"], "count": 8 }
{ "type": "rysquests:checkbox", "title": "task.read_and_confirm" }
{ "type": "rysquests:advancement", "advancement": "repys:electrician" }
{ "type": "rysquests:stat", "stat": "minecraft:mined", "target": "minecraft:iron_ore", "count": 12, "mode": "accumulate" }
{ "type": "rysquests:visit", "dimension": "minecraft:the_nether" }
{ "type": "rysquests:visit", "biome": "#minecraft:is_desert" }
{ "type": "rysquests:visit", "structure": "minecraft:desert_pyramid" }
{ "type": "rysquests:visit", "pos": [100, 64, -200], "radius": 16, "dimension": "minecraft:overworld" }
{ "type": "rysquests:kill", "entity": "minecraft:skeleton", "count": 10 }
{ "type": "rysquests:fluid", "fluid": "minecraft:lava", "amount": 4000, "mode": "consume" }
```

`item.item` принимает id предмета, тег `#…`, или массив через `any_of`. `mode` для `item`: `consume` изымает при сдаче (по умолчанию), `observe` засчитывает наличие, `accumulate` считает всё, что когда-либо прошло через инвентарь (по событиям подбора/крафта). Для `stat` только `accumulate`.

Расширение типов: `type` диспетчеризуется через реестр `TaskType` (Codec dispatch по `type`). Моды и KubeJS регистрируют свои.

## Награды

```json
{ "type": "rysquests:item", "item": "minecraft:gold_ingot", "count": 8 }
{ "type": "rysquests:item", "item": "minecraft:diamond_sword", "components": { "minecraft:enchantments": { ... } } }
{ "type": "rysquests:choice", "of": [ {...item...}, {...item...} ] }
{ "type": "rysquests:random", "of": [ {...item...}, {...item...} ], "weights": [3, 1] }   // weights необязательны
{ "type": "rysquests:xp", "levels": 2 }           // или "points": 100
{ "type": "rysquests:command", "command": "give @s minecraft:emerald 1", "as_server": true }
{ "type": "rysquests:loot_table", "table": "repys:quests/power_tier", "rolls": 1, "show_chances": true }
{ "type": "rysquests:stage", "stage": "2" }
```

Правила выдачи: награды выдаются по действию «Забрать» (не автоматически), в инвентарь; если места нет, остаются «к получению». `choice` требует выбора клиентом: «Забрать всё» выдаёт остальные награды, а квест с выбором ждёт, пока игрок откроет его и выберет. Для `team` режима на награде есть `team_mode`: `each` (каждому игроку) | `once` (один раз на команду, забирает первый).

Награда `command` выполняется от игрока, который её забирает, в его позиции: `@s` и `@p` указывают на него. `as_server: true` повышает только права команды, источник остаётся тем же. Пример стадии RYScope: `ryscope stage grant @s config:iron_age`.

## Миграции: `migrations.json`

```json
{
  "migrations": [
    { "id": "2026-11-01-rename-el-mech", "op": "rename",
      "from": "repys:power/el_mech", "to": "repys:power/electric_mechanism" },
    { "id": "2026-11-02-merge-wires", "op": "merge",
      "from": ["repys:power/wires_a", "repys:power/wires_b"], "to": "repys:power/wires",
      "strategy": "max" },
    { "id": "2026-11-03-remove-old-test", "op": "remove", "from": "repys:power/old_test" },
    { "id": "2026-11-05-ftbq-import", "op": "map",
      "from_ftbq": { "1A2B3C4D": "repys:power/electric_mechanism", "...": "..." } }
  ]
}
```

Миграции применяются по порядку, каждая один раз (`id` запоминается в прогрессе). `remove` переносит в архив, не удаляет. `map` используется импортёром FTB Quests и переносит прогресс по числовым id FTB.

`last_chapter` хранит главу квеста, который игрок выполнил последним своим действием (сдача, отметка, проверка его инвентаря, крафт, убийство). Выполнение командой оператора её не меняет. Сервер шлёт её только этому игроку в `ProgressPayload`; товарищи по команде получают свою. Нет записи: клиент берёт первую незавершённую главу. Строки истории магазина без `treasury` считаются личным кошельком.

## Черновик редактора

Несохранённая работа редактора квестов живёт на диске клиента между сеансами: `rysquests/drafts/<сервер или мир>/<namespace>~<путь главы>.json` в папке игры, файл на главу (`EditorDrafts`, `ClientDrafts`). В файле глава, все её квесты, набранные тексты, язык, версия главы, с которой начата правка, признак новой главы и время записи. После сохранения в книгу или отказа от правок черновик удаляется.

## Импорт FTB Quests

Импорт пишет книгу закрытой (`published: false`), группы глав FTB как эпохи с `imported: true`, положение квеста из FTB в `layout.x/y` без `pin`, `size` FTB 2 и больше как `milestone`. Задача, которой в книге нет, становится галочкой с заголовком `rysquests.import.unconverted_task`, а квест черновиком; квест, у которого выброшена награда, тоже черновик. Файл, который не прочитался, останавливает импорт до записи: отчёт называет файл и строку, импорт можно повторить без этого файла (пакеты `FtbqImport*Payload`, класс `FtbqImportService`). Отчёт по видам: перенесено, куда попало, нужна ручная правка (`ImportReport`), полный список в `config/rysquests/import/import-report.md`.

## Папка редактора

Редактор квестов пишет файлы квестов и глав сюда же, в тот же относительный путь, где квест лежит в книге, и файлы по формату этого документа (ADR-39). Настройка `editorFolder` (общий конфиг), по умолчанию `config/rysquests/pack`. Внутри раскладка пака. Переводы и картинки из неё, на которые ссылается книга, идут клиентам вместе с книгой и важнее клиентских ресурсов (ADR-35).

## Lang

`assets/<ns>/lang/ru_ru.json`, `en_us.json`. Ключи по шаблону `quest.<ns>.<chapter>.<quest>.title|subtitle|text`, `chapter.<ns>.<chapter>.title`, `task.<…>`. Валидатор сообщает о ключах без перевода в активном языке.

## Разметка текста

Используется в `text`, `subtitle`, страницах:

- `[[item:minecraft:redstone]]` иконка + имя, клик → рецепты во вьюере; `[[item:...|подпись]]`.
- `[[quest:repys:power/wires]]` ссылка на квест.
- `[[recipe:kubejs:electric_mechanism]]` одна в строке страницы гайда: рецепт как его рисует JEI, после `#` можно указать категорию (`[[recipe:create:brass_ingot#create:mixing]]`); в тексте: как `item`.
- `[[guide:repys:create#rotation]]` ссылка на гайд или его страницу.
- `[[image:repys:textures/guides/wheel.png|подпись]]` картинка, одна в строке.
- `[[ponder:create:mechanical_crafter]]` открывает размышление Ponder, без него ссылка становится текстом.
- `[[url:https://…]]`: зарезервировано, пока не работает.
- `**жирный**`, `__курсив__`, `!!предупреждение!!` (янтарный блок), `>>спойлер<<`.
- Переносы строк `\n`, абзац пустой строкой.

Парсер в `rysquests.client.markup`, синтаксис как в RYScope; кандидат на перенос в RYSCore.

## Канонический порядок полей

При записи (редактор, импортёр) поля идут в порядке, в котором перечислены в этом документе. Это делает диффы в git читаемыми. Загрузчик порядок не требует.

## Раскладка карты: `layout.json`

Готовая раскладка лежит рядом с `book.json`: `data/<namespace>/rysquests/layout.json`. Сервер читает её вместе с книгой (из того же namespace, что `book.json`) и отправляет игрокам внутри книги, так что игрок карту не считает.

```json
{
  "form": "serpentine",                // форма, в которой разложено, не auto
  "hash": "9f2c…",                     // layoutHash книги, для которой разложено
  "nodes": {                           // центр узла квеста на полотне, [x, y]
    "repys:b00_e1_andesite/andesite_alloy": [120, 48]
  },
  "areas": {                           // прямоугольники [x, y, w, h]: главы по id, эпохи как epoch:<номер>
    "repys:b00_e1_andesite": [0, 0, 960, 540],
    "epoch:1": [-40, -40, 2400, 620]
  }
}
```

Раскладку считает только клиент. Файл пишет сервер по пакету `LayoutSavePayload` от клиента оператора: его шлют «Сохранить» в редакторе, «Сохранить раскладку» в «Проверке» и ответ на команду `/rysquests layout bake` (сервер просит клиент оператора пакетом `LayoutBakeRequestPayload`). Сервер принимает раскладку только для книги, какая она сейчас (хеш совпал), и пишет её через папку редактора. Файл пишется построчно: узел или область на строку, по алфавиту, чтобы большая книга читалась в диффе. Раскладка с чужим хешем всё равно уходит игрокам, но клиент считает карту сам, а проверка книги пишет замечание `layout_stale`.

## Хэш книги

- `hash`: SHA-256 от канонического JSON всех квестов, глав, гайдов, вкладок, сделок, достижений и структур после линковки. Версия книги целиком, для проверки совпадения клиент/сервер.
- `layoutHash` (`BookHash.layout`): версия того, от чего зависит карта: форма, эпохи (только признак импорта), главы раздела `story` по порядку книги с эпохой, `off_map`, `hidden`, подглавами и ссылками, и живые квесты этих глав (не черновики и не архив) с главой, подглавой, зависимостями, режимом зависимостей, вехой, `optional`, `hidden` и ручным местом. Тексты, задачи и награды не входят: исправленная опечатка не делает раскладку устаревшей. Игрок получает книгу без черновиков и приходит к тому же хешу.
- `chapterVersion` (`BookHash.chapterVersion`): версия одной главы для редактора, канонический JSON главы и всех её квестов. Редактор отправляет в `QuestEditorSavePayload` версию, с которой начата правка; если глава с тех пор изменилась, сервер отклоняет сохранение причиной `rysquests.editor.error.conflict` (с именем оператора, который сохранил главу последним) или `…conflict_files`, если её изменили файлами. Повторное сохранение с `force` пишет поверх. Глава, которой ещё нет, имеет версию `""`.

## Прогресс (внутренний формат, NBT через Codec)

```
ProgressStore {
  version: int,
  applied_migrations: [string],
  guide_pages: { <uuid игрока>: ["<guide id>#<page id>"] },
  pinned_ways: { <uuid игрока>: ["<вкладка>#<строка>#<столбец>#<способ>"] },
  wallets: { <uuid игрока или команды>: { <валюта>: long } },
  shop_usage: { <uuid игрока или команды>: { <сделка>: { period: long, count: int } } },
  shop_history: { <uuid игрока>: [ { time, delta, currency, ref, times, treasury } ] },  // treasury: из казны команды
  last_chapter: { <uuid игрока>: <chapter id> },   // глава последнего выполнения, личная
  titles: { <uuid игрока>: [<ключ титула>] },
  active_title: { <uuid игрока>: <ключ титула> },
  teams: { <uuid>: {
    quests: { <quest id>: { tasks: [int], completed: long, claimed: [int] } },   // состояние вычисляется из них
    pinned: [<quest id>],
    archive: { <old id>: {...} }
  } }
}
```

Формат может меняться между версиями мода с собственной миграцией (`version`).
