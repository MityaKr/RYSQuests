# RYSQuests: полный гайд по возможностям

Документ для автора сборки и для ИИ, который ведёт её код. Здесь есть всё, что умеет мод, и рабочие примеры. Каждый блок JSON с пометкой типа проверяется автотестом `GuideExamplesTest`, так что примеры соответствуют коду.

Что нужно: Minecraft 1.21.1, NeoForge 21.1.x. Других модов не требует. По желанию: JEI или RYSIndex (рецепты на страницах), Ponder, Patchouli, KubeJS, RYSTeams (общий прогресс команды).

Короче и с порядком действий для перехода готовой сборки: `for-pack-authors.md`. Каждое поле подробно: `02-data-format.md`.

## Содержание

1. [Куда класть файлы](#1-куда-класть-файлы)
2. [Книга и вкладки](#2-книга-и-вкладки)
3. [Сюжет: главы и квесты](#3-сюжет-главы-и-квесты)
4. [Задачи](#4-задачи)
5. [Награды](#5-награды)
6. [Стадии](#6-стадии)
7. [Команды игроков и режимы прогресса](#7-команды-игроков-и-режимы-прогресса)
8. [Разметка текста](#8-разметка-текста)
9. [Вкладки книги, закреплённое, клавиши](#9-вкладки-книги-закреплённое-клавиши)
10. [Гайды](#10-гайды)
11. [Кастомные вкладки: Матрица, Карточки, Страницы](#11-кастомные-вкладки)
12. [Магазин и валюта](#12-магазин-и-валюта)
13. [Достижения и титулы](#13-достижения-и-титулы)
14. [Монеты из скриптов: блок-обменник](#14-монеты-из-скриптов-блок-обменник)
15. [Редакторы в игре](#15-редакторы-в-игре)
16. [Импорт из FTB Quests](#16-импорт-из-ftb-quests)
17. [Миграции](#17-миграции)
18. [KubeJS и Java API](#18-kubejs-и-java-api)
19. [Проверка книги](#19-проверка-книги)
20. [Все команды](#20-все-команды)
21. [Настройки и переводы](#21-настройки-и-переводы)
22. [Ограничения](#22-ограничения)

## 1. Куда класть файлы

Книга это обычные файлы датапака. Подойдёт любой датапак, в том числе папка `kubejs/data/`. После правки выполни `/reload`.

```
data/<ns>/rysquests/
  book.json                     настройки книги и вкладки
  chapters/<папка>/_chapter.json   глава
  chapters/<папка>/<квест>.json    квест, один файл на квест
  guides/<id>.json              гайд
  sections/<id>.json            кастомная вкладка, id берётся из пути
  shop/<id>.json                сделка магазина
  achievements/<id>.json        достижение
  structures/<id>.json          3D-структура для страниц, id берётся из пути
  migrations.json               переименования квестов
assets/<ns>/lang/ru_ru.json     тексты
assets/<ns>/textures/guides/    картинки гайдов
```

`<ns>` это пространство имён сборки, например `repys`. Все id строковые: `repys:power/wires`. Id нельзя менять без записи в `migrations.json`, иначе игроки потеряют прогресс.

Любая строка для игрока это ключ lang или готовый текст. Ключи удобнее для перевода, готовый текст быстрее. Ниже встречается и то и другое.

Рабочий пример всего сразу лежит в `examples/example_pack/`.

## 2. Книга и вкладки

```json book
{
  "progress_mode": "team",
  "default_view": "home",
  "title": "book.repys.title",
  "icon": "minecraft:writable_book",
  "chapter_order": ["repys:01_create", "repys:02_power"],
  "hud": { "max_pinned": 3 },
  "sections": ["story", "guides", "repys:build_atlas", "repys:mining_atlas", "shop", "achievements"],
  "epochs": ["epoch.repys.1", "epoch.repys.2"],
  "map_form": "auto",
  "published": true
}
```

| Поле | Значение |
|---|---|
| `progress_mode` | `solo`, `team` или `server`, см. раздел 7 |
| `default_view` | с чего открывается книга: `home` вкладка «Сейчас», `map` карта, `list` список квестов. Старые значения `flow`, `timeline`, `dev` читаются как `home` |
| `title`, `icon` | название и значок книги |
| `chapter_order` | порядок глав, остальные идут после них по id |
| `hud.max_pinned` | сколько квестов можно закрепить на экране |
| `sections` | какие вкладки и разделы видны игроку, см. ниже |
| `epochs` | названия эпох по порядку: первая для глав с `"epoch": 1`. Эпоха объектом `{ "title": "...", "quest": "<id квеста>" }` получает награду: награды этого квеста забираются на карте у эпохи |
| `map_form` | форма карты: `auto`, `serpentine` (Серпантин), `spiral` (Спираль), `galaxy` (Галактика), `generator` (Генератор). `auto` выбирает по устройству книги |
| `published` | `false` закрывает книгу от игроков, пока автор её собирает. Без поля книга открыта |
| `about` | страницы «О книге»: название книги в шапке становится кнопкой. Подробно в `for-pack-authors.md` |
| `pre_complete` | предварительное выполнение: задачи закрытых квестов засчитываются заранее. Глава и квест могут переопределить |

**Вкладки.** В шапке книги вкладки «Сейчас», «Карта», «Гайды», «Магазин», «Достижения». Строки в `sections`: `story` для «Сейчас» и «Карты», `guides`, `shop`, `achievements`; строка вида `repys:mining_atlas` это кастомная вкладка, она живёт внутри «Гайдов» отдельной группой.

Игрок видит вкладку, только если выполнены оба условия:

1. она разрешена: записана в `sections` или поля `sections` нет совсем;
2. в ней есть содержимое: для `story` хотя бы одна видимая глава, для `guides` гайд или кастомная вкладка, для `shop` сделка, для `achievements` достижение.

Значит, вкладку, которую автор ещё не наполнил, игрок не видит. Без поля `sections` видно всё, где есть содержимое.

В игре вкладки настраиваются в редакторе, страница «Вкладки».

## 3. Сюжет: главы и квесты

### Глава

Файл `chapters/02_power/_chapter.json`:

```json chapter
{
  "id": "repys:02_power",
  "title": "chapter.repys.power.title",
  "subtitle": "chapter.repys.power.subtitle",
  "icon": "minecraft:redstone",
  "epoch": 2,
  "default_view": "map",
  "groups": [ { "id": "basics", "title": "chapter.repys.power.basics" } ],
  "requires": ["repys:01_create"],
  "links": ["repys:01_create/brass"],
  "hidden": false
}
```

| Поле | Что делает |
|---|---|
| `title` | название, римский номер интерфейс добавит сам, поэтому не пиши «01. Андезит» |
| `epoch` | номер эпохи: на карте и в сайдбаре главы с одним номером стоят вместе, название берётся из `epochs` книги |
| `default_view` | с чего открывается глава: `map` или `list`; без поля как у книги |
| `off_map` | глава остаётся в книге и в счёте, но на карте её нет: она в блоке «Вне карты» сайдбара |
| `groups` | подглавы, пунктирные рамки на карте |
| `requires` | какие главы идут раньше. Проверка книги предупредит, если квест этой главы зависит от квеста главы вне этой цепочки. Главу оно не закрывает |
| `hidden` | скрыть главу |
| `links` | квесты других глав, которые видны и на карте этой главы, как ссылки FTB Quests; клик открывает квест в его главе |

### Квест

Файл `chapters/02_power/electric_mechanism.json`. Показаны все поля, обязательны только `id` и `title`:

```json quest
{
  "id": "repys:power/electric_mechanism",
  "chapter": "repys:02_power",
  "group": "basics",
  "title": "quest.repys.power.electric_mechanism.title",
  "subtitle": "quest.repys.power.electric_mechanism.subtitle",
  "text": "quest.repys.power.electric_mechanism.text",
  "icon": "minecraft:clock",
  "requires": ["repys:power/wires", "repys:power/dynamo"],
  "requires_mode": "all",
  "stage_required": "minecraft:story/mine_stone",
  "guide": "repys:create#rotation",
  "tasks": [
    { "type": "rysquests:item", "item": "minecraft:clock", "count": 3, "mode": "consume" }
  ],
  "rewards": [
    { "type": "rysquests:currency", "id": "coin", "amount": 25 }
  ],
  "layout": { "pin": true, "x": 240, "y": 72 },
  "optional": false,
  "hidden": false,
  "repeatable": { "cooldown_minutes": 60 },
  "milestone": true,
  "tags": ["power"]
}
```

| Поле | Что делает |
|---|---|
| `chapter` | глава; если нет, берётся из папки |
| `group` | подглава из `groups` главы |
| `title` | узел карты, в нём помещается около 17 символов |
| `subtitle` | короткая строка в списке квестов и в панели квеста |
| `text` | описание «как сделать», поддерживает разметку |
| `icon` | заполняй всегда |
| `requires`, `requires_mode` | зависимости: `"all"`, `"any"` или `{ "n": 2 }` (любые две) |
| `stage_required` | закрыт до стадии |
| `guide` | гайд или его страница, даёт кнопку «Как это работает» |
| `tasks`, `rewards` | см. разделы 4 и 5. Квест без задач засчитывается сам, как только откроется |
| `layout` | ручное место на карте; без него раскладка автоматическая: квесты без подглавы идут главным путём по центру, подглава блоком-сеткой выше или ниже шага, от которого зависит |
| `optional` | не обязателен для завершения главы |
| `hidden` | не показывается, пока зависимости не выполнены |
| `repeatable` | повторяемый, с перезарядкой в минутах. Квесты после него остаются открытыми между раундами. Задачи на статистику, достижение и посещение выполнятся сразу после сброса, проверка книги предупредит |
| `milestone` | веха книги: крупный узел на карте |
| `page` | страница без задач, засчитывается прочтением |
| `draft`, `archived` | черновик игроки не видят, пока автор его не доделает; квест в архиве убран из книги без удаления файла |
| `tags` | метки: `milestone` то же, что `"milestone": true`; `epoch` значит, что квест открывает новую эпоху, и уведомление о нём говорит об этом. Остальные метки книга не трогает |

Маленькую книгу можно держать массивом квестов в одном файле `_quests.json`. Такие квесты редактор в игре не пишет.

## 4. Задачи

Общие поля: `type`, необязательный `title` вместо автоматической подписи.

**Предмет.** `mode`: `consume` забирает кнопкой «Сдать», `observe` проверяет наличие, `accumulate` устарел и работает так же, как `observe` (проверка книги предупредит), `craft` считает скрафченное и выплавленное после открытия квеста.

```json task
{ "type": "rysquests:item", "item": "minecraft:copper_ingot", "count": 16, "mode": "consume" }
```

```json task
{ "type": "rysquests:item", "item": "#c:ingots/copper", "count": 16, "mode": "observe" }
```

```json task
{ "type": "rysquests:item", "any_of": ["minecraft:oak_log", "minecraft:birch_log"], "count": 8 }
```

```json task
{ "type": "rysquests:item", "item": "minecraft:torch", "count": 32, "mode": "craft" }
```

**Опыт.** `levels` или `points`. С `consume` опыт сдаётся кнопкой «Сдать», без него достаточно иметь.

```json task
{ "type": "rysquests:xp", "levels": 10 }
```

```json task
{ "type": "rysquests:xp", "points": 500, "consume": true }
```

**Галочка.** Игрок сам подтверждает, годится для «прочитано».

```json task
{ "type": "rysquests:checkbox", "title": "task.read_and_confirm" }
```

**Достижение игры.**

```json task
{ "type": "rysquests:advancement", "advancement": "minecraft:story/mine_stone" }
```

**Статистика игры.** Видит и то, что было до появления квеста.

```json task
{ "type": "rysquests:stat", "stat": "minecraft:mined", "target": "minecraft:iron_ore", "count": 12 }
```

**Посещение.** Любое сочетание измерения, биома, структуры, точки с радиусом.

```json task
{ "type": "rysquests:visit", "dimension": "minecraft:the_nether" }
```

```json task
{ "type": "rysquests:visit", "biome": "#minecraft:is_desert" }
```

```json task
{ "type": "rysquests:visit", "structure": "minecraft:desert_pyramid" }
```

```json task
{ "type": "rysquests:visit", "pos": [100, 64, -200], "radius": 16, "dimension": "minecraft:overworld" }
```

**Убийство.**

```json task
{ "type": "rysquests:kill", "entity": "minecraft:skeleton", "count": 10 }
```

**Жидкость.**

```json task
{ "type": "rysquests:fluid", "fluid": "minecraft:lava", "amount": 4000, "mode": "consume" }
```

**Скрипт KubeJS.** См. раздел 18.

```json task
{ "type": "rysquests:script", "script": "repys:looked_at_boiler", "count": 1, "title": "Посмотреть на бойлер" }
```

## 5. Награды

Выдаются кнопкой «Забрать». Исключение: награды достижений приходят сами.

Предмет:

```json reward
{ "type": "rysquests:item", "item": "minecraft:gold_ingot", "count": 8 }
```

На выбор, игрок выбирает один вариант:

```json reward
{ "type": "rysquests:choice", "of": [
  { "type": "rysquests:item", "item": "minecraft:gold_ingot", "count": 8 },
  { "type": "rysquests:item", "item": "minecraft:diamond", "count": 2 }
] }
```

Случайная, `weights` необязательны:

```json reward
{ "type": "rysquests:random", "of": [
  { "type": "rysquests:item", "item": "minecraft:iron_ingot", "count": 4 },
  { "type": "rysquests:item", "item": "minecraft:diamond" }
], "weights": [3, 1] }
```

Опыт, `levels` или `points`:

```json reward
{ "type": "rysquests:xp", "levels": 2 }
```

Команда. Выполняется от игрока, `as_server` повышает права. `level` задаёт уровень прав для `as_server`, по умолчанию 4. Подстановки FTB Quests: `{p}` имя игрока, `{x}` `{y}` `{z}` блок, где он стоит, `{quest}` и `{chapter}` id квеста и главы, `{team}`, `{team_id}`, `{long_team_id}` id команды, `{member_count}`, `{online_member_count}`. В FTB Quests `{team}` это название команды, а `{quest}` его шестнадцатеричный код, здесь id:

```json reward
{ "type": "rysquests:command", "command": "rysindex stage grant @s config:iron_age", "as_server": true }
```

Таблица добычи:

```json reward
{ "type": "rysquests:loot_table", "table": "minecraft:chests/simple_dungeon", "rolls": 1 }
```

Стадия:

```json reward
{ "type": "rysquests:stage", "stage": "minecraft:story/mine_stone" }
```

Монеты. С `"team_mode": "once"` идут в казну команды:

```json reward
{ "type": "rysquests:currency", "id": "coin", "amount": 50 }
```

Титул, показывается перед именем:

```json reward
{ "type": "rysquests:title", "id": "title.repys.engineer" }
```

Скрипт KubeJS:

```json reward
{ "type": "rysquests:script", "script": "repys:unlock" }
```

В режимах команды у награды есть `team_mode`: `each` каждому (по умолчанию) или `once` один раз на команду.

## 6. Стадии

Стадия это строка, с которой считается закрытым квест, столбец Матрицы, карточка, гайд, сделка. Без других модов стадия это id достижения игры: `minecraft:story/mine_stone`. Другие провайдеры регистрируются через API, при нескольких достаточно подтверждения любого.

Стадии RYSIndex выдаются наградой `rysquests:command`, как показано выше.

## 7. Команды игроков и режимы прогресса

| `progress_mode` | Прогресс |
|---|---|
| `solo` | у каждого игрока свой |
| `team` | общий на команду |
| `server` | общий на весь сервер |

Команды: `/rysquests team create`, `invite <игрок>`, `join`, `leave`. Если установлен RYSTeams, команды берутся из него. Личный кошелёк есть всегда, казна только у команды.

## 8. Разметка текста

Работает в `text`, `subtitle`, страницах гайдов, описании способов и карточек.

| Запись | Что покажет |
|---|---|
| `[[item:minecraft:redstone]]`, `[[item:id\|подпись]]` | значок и имя, клик открывает рецепт |
| `[[recipe:id]]` | одна в строке страницы: настоящий рецепт из JEI, включая машины Create с анимацией |
| `[[recipe:create:brass_ingot#create:mixing]]` | то же, с выбором категории JEI после `#`; без неё берётся первая, где предмет получается |
| `[[structure:repys:assembly_hall\|подпись]]` | одна в строке: 3D-структура, см. раздел 10 |
| `[[ponder:create:gearbox]]`, `[[ponder:id\|подпись]]` | ссылка, открывает размышление Ponder, выход возвращает на страницу |
| `[[image:repys:textures/guides/wheel.png\|подпись]]` | картинка, одна в строке |
| `[[quest:repys:power/wires]]`, `[[quest:id\|подпись]]` | ссылка на квест |
| `[[guide:repys:create#rotation]]` | ссылка на гайд или его страницу |
| `**жирный**`, `__курсив__` | начертание |
| `!!предупреждение!!` | янтарный блок |
| `>>спойлер<<` | спойлер |

Новая строка `\n`, абзац это пустая строка.

## 9. Вкладки книги, закреплённое, клавиши

| Вкладка | Что показывает |
|---|---|
| Сейчас (`home`) | следующий шаг книги, награды к получению, квесты в работе и то, что можно делать в текущей главе |
| Карта (`map`) | вся книга на одном полотне: эпохи, главы, связи квестов; слева сайдбар эпох и глав, справа панель квеста. Переключатель «Карта \| Список» |
| Список (`list`) | те же квесты списком по главам и подглавам |
| Гайды | библиотека гайдов и кастомные вкладки автора |
| Магазин | покупка и продажа за валюту книги |
| Достижения | уровни, коллекции, титулы |

Вкладка без содержимого игроку не видна. Оператор видит в шапке кнопку «Редактор», см. раздел 15. Поиск по книге открывается клавишей `/` или Ctrl+F.

Закреплённые квесты, способы Матрицы и уровни достижений видны на экране карточками, число квестов ограничивает `hud.max_pinned`. Клавиша по умолчанию `K` открывает книгу; пока на экране уведомление о выполненном квесте, она открывает книгу на нём. «Закрепить» и «Свернуть закреплённое» есть в управлении игры без клавиши по умолчанию. «Сдать всё готовое» это кнопка книги.

Тема выбирается в настройках книги и общая для всех модов семьи RYS.

## 10. Гайды

Справочник внутри книги. Включается строкой `guides` в `sections`. Файл `guides/create.json`:

```json guide
{
  "id": "repys:create",
  "title": "guide.repys.create.title",
  "icon": "minecraft:clock",
  "category": "guide.repys.category.tech",
  "stage": "minecraft:story/mine_stone",
  "stage_title": "guide.repys.stage.epoch_3",
  "pages": [
    { "id": "rotation", "title": "guide.repys.create.rotation.title", "text": "guide.repys.create.rotation.text" }
  ]
}
```

`id` страницы стабилен, по нему хранится прочитанность. Поля `category`, `stage`, `stage_title` необязательны.

Панель справа собирается сама: квесты с полем `guide` на эту страницу, упомянутые в тексте и те, что просят предметы со страницы, а также все предметы из текста. Если гайд нужен открытому квесту, он помечается «нужен сейчас».

### Рецепты, размышления и 3D на страницах

**Рецепт.** `[[recipe:create:brass_ingot]]` одной строкой показывает рецепт так, как его рисует JEI: машины Create видны с анимацией. Чтобы выбрать способ, укажи категорию JEI: `[[recipe:create:brass_ingot#create:mixing]]`. В карточке квеста JEI рисует только то, что книга не может прочитать сама.

**Размышление.** `[[ponder:create:gearbox|Как работает редуктор]]` открывает экран Ponder, выход из него возвращает на страницу. Id это предмет, про который есть сцена.

**3D-структура.** Файл `structures/assembly_hall.json`, поля как у мультиблока Patchouli, его можно скопировать как есть:

```json structure
{
  "title": "Зал сборки",
  "mapping": {
    "S": "minecraft:stone_bricks",
    "G": "minecraft:glass",
    "T": "minecraft:oak_stairs[facing=north]",
    "L": "#minecraft:logs",
    "0": "minecraft:iron_block"
  },
  "pattern": [
    [ "TTT", "TLT", "TTT" ],
    [ "GGG", "G G", "GGG" ],
    [ "SSS", "S0S", "SSS" ]
  ]
}
```

- `pattern` идёт сверху вниз: первый список это верхний слой. Все слои одного размера.
- Пробел это воздух, `_` это «что угодно», оба ничего не рисуют. `0` это опорный блок, он нужен для голограммы и должен быть один.
- Значение в `mapping` это блок, блок со свойствами или тег, от тега показывается первый блок.
- На страницу структура ставится строкой `[[structure:repys:assembly_hall|Зал сборки]]`.

Игрок вращает структуру мышью, масштабирует Ctrl+колесом, смотрит по слоям и открывает список блоков с тем, сколько у него есть. Кнопка «Показать в мире» ставит голограмму, она есть при установленном Patchouli и если в структуре один `0`. Блок-сущности (сундуки и подобные) в 3D пока не рисуются.

**Импорт книги Patchouli.** `/rysquests import patchouli machines repys` читает `patchouli_books/machines/` и пишет в папку редактора гайды, страницы, структуры и переводы всех языков. Одна категория книги становится одним гайдом, запись становится страницей, форматирование Patchouli переводится в нашу разметку. Страницы других типов, например `crafting`, пропускаются, их число видно в чате, а список в логе на уровне DEBUG. Файлы Patchouli не меняются, повторный импорт перезаписывает только то, что создал.

## 11. Кастомные вкладки

Файл `sections/<id>.json`, поле `template` выбирает вид. Id вкладки это путь файла: файл `data/repys/rysquests/sections/mining_atlas.json` даёт `repys:mining_atlas`, эту строку и пиши в `sections` книги. Мод ничего не знает о рудах и эпохах: что автор написал, то игрок и видит.

### Матрица

Таблица «строки × столбцы». В ячейке ноль и больше **способов**.

| Поле вкладки | Что делает |
|---|---|
| `title`, `short_title`, `subtitle`, `icon` | заголовок, короткое название, подзаголовок, значок |
| `spoilers` | закрытые столбцы: `show`, `dim` (по умолчанию), `hide` |
| `compact` | `true` рисует ячейки значками, для широких таблиц |
| `search`, `row_title` | подсказка поиска, заголовок первого столбца |
| `groups` | группы строк, чипы фильтра |
| `columns` | столбцы: `id`, `title`, `hint`, необязательная `stage` |
| `rows` | строки: `id`, `title`, `icon`, `group`, `cells` |

Текущим считается последний столбец, стадия которого открыта, он подсвечен.

Способ: `id`, `name`, `text`, `icon`, `mark` (`auto` или `manual`), `best` (лучший на стадии), `needs` (предметы), `links`. Ссылка: `{ "type": "quest" | "recipe" | "guide", "id": "..." }`. Способ можно закрепить на HUD.

**Пример: Атлас добычи.** Строки это ресурсы, столбцы это стадии игры.

```json section
{
  "template": "matrix",
  "title": "Атлас добычи",
  "short_title": "Добыча",
  "subtitle": "откуда брать ресурс на каждой стадии игры",
  "icon": "minecraft:iron_pickaxe",
  "search": "Найти ресурс",
  "row_title": "Ресурс",
  "spoilers": "dim",
  "groups": ["metal", "gem", "liquid"],
  "columns": [
    { "id": "early", "title": "Ранняя", "hint": "эпохи I–II" },
    { "id": "mid", "title": "Средняя", "hint": "эпохи III–V", "stage": "minecraft:story/mine_stone" },
    { "id": "late", "title": "Поздняя", "hint": "эпохи VI–IX", "stage": "minecraft:end/kill_dragon" }
  ],
  "rows": [
    {
      "id": "iron",
      "title": "Железо",
      "icon": "minecraft:iron_ingot",
      "group": "metal",
      "cells": {
        "early": [
          { "id": "y16", "name": "Шахта, Y 16", "icon": "minecraft:stone_pickaxe" }
        ],
        "mid": [
          {
            "id": "crush",
            "name": "Дробление руды",
            "text": "Сырая руда идёт через дробильные колёса, потом в печь. Выход выше обычной плавки.",
            "icon": "minecraft:grindstone",
            "mark": "manual",
            "best": true,
            "needs": ["minecraft:grindstone", "minecraft:clock"],
            "links": [
              { "type": "quest", "id": "repys:industry/crushing" },
              { "type": "recipe", "id": "minecraft:grindstone" },
              { "type": "guide", "id": "repys:create#crushing" }
            ]
          }
        ],
        "late": [
          { "id": "golems", "name": "Ферма големов", "icon": "minecraft:iron_block", "mark": "auto" }
        ]
      }
    }
  ]
}
```

**Пример: Атлас сборки.** Та же Матрица: строки это направления, столбцы это эпохи, способ это мод. `compact` рисует ячейки значками.

```json section
{
  "template": "matrix",
  "title": "Атлас сборки",
  "short_title": "Атлас",
  "subtitle": "9 эпох · 6 направлений",
  "icon": "minecraft:map",
  "compact": true,
  "spoilers": "dim",
  "groups": ["tech", "energy", "magic"],
  "columns": [
    { "id": "I", "title": "I", "hint": "Create" },
    { "id": "II", "title": "II", "hint": "Индустрия", "stage": "minecraft:story/mine_stone" },
    { "id": "III", "title": "III", "hint": "Машины", "stage": "minecraft:story/enter_the_nether" }
  ],
  "rows": [
    {
      "id": "tech",
      "title": "Техника",
      "group": "tech",
      "cells": {
        "I": [
          { "id": "create", "name": "Create", "text": "Механизмы на вращении.", "icon": "minecraft:clock" }
        ],
        "II": [
          { "id": "wires", "name": "Провода", "icon": "minecraft:copper_ingot",
            "links": [ { "type": "guide", "id": "repys:create" } ] }
        ]
      }
    }
  ]
}
```

Если хочешь тексты в lang: не пиши `name` и `text`, тогда берутся ключи `<title>.<row>.<column>.<way>.name` и `.text`, название строки `<title>.<row>.name`, группы `<title>.group.<id>`.

### Карточки

Каталог с фильтрами и поиском, например боссы или модули.

```json section
{
  "template": "cards",
  "title": "section.repys.bosses",
  "short_title": "section.repys.bosses.short",
  "icon": "minecraft:wither_skeleton_skull",
  "groups": ["overworld", "nether"],
  "cards": [
    {
      "id": "wither",
      "icon": "minecraft:nether_star",
      "group": "nether",
      "art": "#2a1c1c",
      "done": { "type": "rysquests:kill", "entity": "minecraft:wither" },
      "needs": [ { "item": "minecraft:soul_sand", "count": 4 } ],
      "gives": ["minecraft:nether_star"],
      "page": "repys:rules#safety",
      "quest": "repys:nether/wither",
      "stage": "minecraft:story/enter_the_nether",
      "stage_title": "guide.repys.stage.nether"
    }
  ]
}
```

`art` это цвет фона. `done` это условие отметки, тот же формат, что задача; считается как квест, общим на команду. `page` ведёт на гайд или вкладку «Страницы», `quest` на квест, `stage` закрывает карточку.

Тексты: `<title>.<card>.name`, `.desc`, `.meta`, `.text`, `.tip`, `.needs_title`, `.gives_title`. Заменить общие слова вкладки можно ключами `<title>.done`, `.open`, `.locked`, `.done_count`.

### Страницы

Книга из страниц: правила сервера, лор, список изменений. Тот же формат и редактор, что у гайдов.

```json section
{
  "template": "pages",
  "title": "section.repys.rules",
  "short_title": "section.repys.rules.short",
  "icon": "minecraft:writable_book",
  "pages": [
    { "id": "welcome", "title": "section.repys.rules.welcome.title", "text": "section.repys.rules.welcome.text" },
    { "id": "safety", "title": "section.repys.rules.safety.title", "text": "section.repys.rules.safety.text" }
  ]
}
```

## 12. Магазин и валюта

Включается строкой `shop` в `sections`. Валюта это число на сервере, не предмет. Валют может быть несколько, имя любое. Отображаемое название берётся из ключа `rysquests.currency.<id>`. Покупка:

```json offer
{
  "id": "repys:iron_bundle",
  "icon": "minecraft:iron_ingot",
  "category": "shop.repys.resources",
  "cost": [ { "type": "currency", "id": "coin", "amount": 40 } ],
  "gives": [ { "type": "item", "id": "minecraft:iron_ingot", "count": 32 } ],
  "limit": { "per": "player", "count": 3, "reset": "daily" },
  "stage": "minecraft:story/mine_stone",
  "stage_title": "shop.repys.epoch_2",
  "quest": "repys:power/wires"
}
```

Продажа это сделка, которая даёт только валюту:

```json offer
{
  "id": "repys:sell_wheat",
  "cost": [ { "type": "item", "id": "minecraft:wheat", "count": 16 } ],
  "gives": [ { "type": "currency", "id": "coin", "amount": 2 } ]
}
```

`limit.per` это `player` или `team`, `limit.reset` это `never`, `daily` или `weekly`. Лимиты сбрасываются в полночь по времени сервера. Сервер сам проверяет баланс, лимит, стадию, квест и место в инвентаре.

Откуда берутся монеты: награда `rysquests:currency`, продажи, команды, скрипты. Отчёт книги (`/rysquests dev report` и страница «Проверка» редактора) показывает цепочки сделок, которые печатают деньги.

## 13. Достижения и титулы

Включается строкой `achievements` в `sections`. Достижение не блокирует сюжет. Одна задача, несколько уровней.

```json achievement
{
  "id": "repys:factory_worker",
  "category": "achievement.repys.tech",
  "icon": "minecraft:crafter",
  "task": { "type": "rysquests:stat", "stat": "minecraft:custom", "target": "minecraft:jump" },
  "tiers": [
    { "tier": "bronze", "amount": 10, "rewards": [ { "type": "rysquests:currency", "id": "coin", "amount": 10 } ] },
    { "tier": "silver", "amount": 100, "rewards": [ { "type": "rysquests:title", "id": "title.repys.worker" } ] },
    { "tier": "gold", "amount": 1000 }
  ]
}
```

Скрытое показывает «???» и подсказку, пока не получено:

```json achievement
{
  "id": "repys:chicken_hunter",
  "icon": "minecraft:feather",
  "hidden": true,
  "task": { "type": "rysquests:kill", "entity": "minecraft:chicken" },
  "tiers": [ { "tier": "gold", "amount": 3 } ]
}
```

- Количество в `task` не пиши, его подставляет `amount`. Пороги уровней должны расти.
- Подходят `kill`, `stat`, `item` (без `mode` это `observe`) и `advancement` (один уровень, `amount: 1`). Задача со сдачей или галочкой сама не считается, валидатор предупредит.
- `tier`: `bronze`, `silver`, `gold` дают свои медали, другие имена допустимы.
- Тексты: ключи `achievement.<ns>.<id>.name`, `.text`, `.hint` либо поля `title`, `text`, `hint`.
- Награды приходят сами. Кого не было в игре, получит при следующем входе.
- Редкость считается на сервере и показывается процентом.

Титул надевается кнопкой в панели достижения и виден перед именем в чате и списке игроков. Над головой его нет.

## 14. Монеты из скриптов: блок-обменник

В серверных скриптах KubeJS доступен объект `RYSQuestsWallet`.

| Метод | Что делает | Возврат |
|---|---|---|
| `balance(player, currency)` | баланс игрока | число |
| `treasury(player, currency)` | баланс казны, `0` без команды | число |
| `deposit(player, currency, amount)` | зачислить игроку | нет |
| `depositTreasury(player, currency, amount)` | зачислить в казну | `false` без команды |
| `withdraw(player, currency, amount)` | списать | `false`, если не хватает, ничего не списано |
| `exchange(player, item, currency, items, coins)` | обменять предметы со всего инвентаря, кроме брони | сколько монет зачислено |
| `exchangeHeld(player, item, currency, items, coins)` | то же только из главной руки | сколько монет зачислено |

`item` это id предмета или тег `#c:coins`. За каждые `items` предметов игрок получает `coins` монет, остаток меньше партии остаётся у него.

**Готовый блок.** Файл `examples/kubejs/server_scripts/rysquests_coin_exchange.js`, скопируй в `kubejs/server_scripts/` сборки. Правый клик по блоку с монетой в руке меняет её на квестовые:

```js
const EXCHANGER = 'minecraft:lodestone'           // блок обменника, может быть из KubeJS или любого мода
const CURRENCY = 'coin'                           // имя валюты из сделок и наград
const RATES = {
  'minecraft:gold_nugget': { items: 3, coins: 1 }, // старая монета: сколько штук и сколько это монет
  'minecraft:gold_ingot': { items: 1, coins: 3 }
}

BlockEvents.rightClicked(EXCHANGER, event => {
  const id = String(event.item.id)
  const rate = RATES[id]
  if (!rate) return

  const coins = RYSQuestsWallet.exchangeHeld(event.player, id, CURRENCY, rate.items, rate.coins)
  if (coins <= 0) return

  event.player.tell(Text.gold('+' + coins + ' ' + CURRENCY))
  event.cancel()
})
```

Обмен проходит на сервере и попадает в историю магазина как «обмен». Чтобы обменивать всё сразу, замени `exchangeHeld` на `exchange`.

## 15. Редакторы в игре

Нужны права оператора (уровень 2). Работают в одиночной игре и на выделенном сервере: файлы пишет сервер в папку `editorFolder`, по умолчанию `config/rysquests/pack`, раскладка как у пака. Если книга сборки лежит в KubeJS, укажи `kubejs`. Сохранённое сразу видят все игроки.

Редактор живёт внутри книги: оператор нажимает кнопку «Редактор» в шапке, и вкладки меняются на страницы автора: Карта, Гайды, Магазин, Достижения, Вкладки, Проверка. Все правки сначала идут в черновик, в шапке видно «Сохранить · N» (сколько правок ждут) и отмену. Черновик хранится на диске клиента, закрытая игра его не теряет. Пустую книгу редактор начинает со страницы «Новая книга»: название, значок и форма карты.

- **Карта.** Слева эпохи и главы, в середине полотно черновика или глава таблицей (квест, тип, что сделать, награда, после). Связь рисуется перетаскиванием от квеста к квесту, подсказка словами говорит, что откроется после чего; связь, которая замкнула бы круг, не ставится. Квесты руками не двигаются: карта раскладывается сама по связям. Справа форма квеста: название и значок, тип «Основной | Побочный | Веха», «+ Следующий», «+ Побочный», описание, задачи, награды, «Ещё» для редких настроек. Вместо удаления «В архив», рядом «Дублировать». «Вид карты» в низу сайдбара выбирает форму карты.
- **Гайды.** Гайд со страницами, текст с разметкой и вставками предмета, рецепта, картинки, квеста, ссылки.
- **Магазин.** Сделки той же сеткой, что у игрока, «Купить | Продать», «+ Товар»; справа цена, товар, условия.
- **Достижения.** Сетка по категориям, «+ Достижение», справа значок, название, «Секретное», условие и уровни.
- **Вкладки.** Какие вкладки видит игрок, кастомные вкладки внутри «Гайдов», новые вкладки из шаблонов, таблица Матрицы и Карточки.
- **Проверка.** «Что исправить» (ошибки, на которые наткнётся игрок, с вариантами исправления), «Где команды застревают», «Архив», книга в числах.

Пока книга закрыта (`"published": false`), в шапке есть «Открыть игрокам». Запись идёт в тот же файл, где квест уже лежит, новые квесты попадают в папку главы. Порядок полей постоянный, файлы удобно сравнивать в git.

## 16. Импорт из FTB Quests

1. `/rysquests dev registry` выгружает все id пака в `logs/rysquests-registry.json`.
2. `/rysquests import ftbq repys` создаёт книгу в `config/rysquests/import/`.
3. `/rysquests import ftbq progress` переносит прогресс из `world/ftbquests/`.
4. Перенеси `data/` и `assets/` из папки импорта в датапак сборки. Файл `ftbq-ids.json` оставь: по нему импортируется прогресс.

Что переносится. Задачи: предмет (с `only_from_crafting` в режиме `craft`), галочка, опыт, статистика, убийство, структура, измерение, биом, место (та же область блоков), достижение, жидкость. Задача стадии становится `stage_required` квеста. Награды: предмет, опыт, на выбор, случайная, таблица добычи (как случайная), команда (подстановки и уровень прав сохраняются), стадия, достижение (как команда). Флаг `hide_until_deps_complete` делает квест скрытым, `hide_until_deps_visible` не переносится: здесь скрытый квест ждёт выполнения зависимостей, а не их появления. Не переносятся: задачи observation, custom, энергия, убийство по тегу мобов (фильтр по имени или данным теряется с заметкой), награды toast, all_table, currency, custom, снятие стадии, пустой вес таблицы добычи (здесь бросок всегда что-то даёт), критерий достижения (проверяется всё достижение). Отчёт начинается с итога по каждому типу: сколько перенесено и сколько пропущено. Квест, у которого не перенеслась ни одна задача, получает задачу-заглушку и ждёт ручной правки.

Id стадий переносятся как есть: их должен знать провайдер стадий сборки.

Забранные награды переносятся по игрокам: в отряде FTB каждый видит забранным только то, что забрал сам. Награда `team_reward` становится наградой `team_mode: once`.

Импорт только читает файлы FTB. Это снимок, а не синхронизация: повторный импорт перезапишет папку импорта, а повторный импорт прогресса заменит прогресс RYSQuests. Пока включены оба мода, награду можно забрать дважды, перед выпуском FTB Quests нужно убрать.

## 17. Миграции

Переименование и объединение квестов без потери прогресса. Файл `migrations.json`:

```json migrations
{
  "migrations": [
    { "id": "2026-11-01-rename-el-mech", "op": "rename", "from": "repys:power/el_mech", "to": "repys:power/electric_mechanism" },
    { "id": "2026-11-02-merge-wires", "op": "merge", "from": ["repys:power/wires_a", "repys:power/wires_b"], "to": "repys:power/wires", "strategy": "max" },
    { "id": "2026-11-03-remove-old-test", "op": "remove", "from": "repys:power/old_test" }
  ]
}
```

Каждая применяется один раз, сама при старте мира и после `/reload`. `/rysquests migrate --dry-run` показывает, что изменится, `--apply` применяет с бэкапом.

## 18. KubeJS и Java API

Серверные скрипты:

```js
RYSQuestsEvents.completed(e => { console.info(e.quest + ' ' + e.player.username) })
RYSQuestsEvents.completed('repys:power/electric_mechanism', e => { e.server.runCommandSilent('...') })
RYSQuestsEvents.task('repys:looked_at_boiler', e => { if (true) e.setProgress(1) })
RYSQuestsEvents.reward('repys:unlock', e => { e.player.give('minecraft:diamond') })
```

`task` вызывается примерно раз в секунду, пока квест открыт, держи его дешёвым. Кошелёк описан в разделе 14.

Java: `works.repka.rysquests.api.QuestsApi` (книга, отметка квеста, регистрация стадий), `WalletApi` (монеты), событие `QuestCompletedEvent`, реестры типов `rysquests:task_type` и `rysquests:reward_type`.

## 19. Проверка книги

Без игры, нужен только Python 3:

```bash
python tools/rysquests_check.py <корень пака> --registry logs/rysquests-registry.json --lang ru_ru
```

`<корень пака>` содержит `data/<ns>/rysquests/`. Проверяет главы и квесты: JSON, поля, повторы, зависимости, циклы, типы, предметы, переводы. `--json` даёт результат для программ, код выхода 1 при ошибках.

В игре: `/rysquests validate` (файл `logs/rysquests-validate.txt`), `/rysquests dev report` (`logs/rysquests-report.json` и `.csv`), страница «Проверка» редактора. Сломанный квест не валит книгу: он выпадает вместе с зависимыми, остальное загружается.

## 20. Все команды

Нужен уровень прав 2, кроме `team`.

| Команда | Что делает |
|---|---|
| `/rysquests reload` | перезагрузка датапаков |
| `/rysquests validate` | проверка книги и тегов |
| `/rysquests dev report` | полный отчёт в файлы |
| `/rysquests dev registry` | все id пака в файл |
| `/rysquests import ftbq [ns]` | книга из FTB Quests |
| `/rysquests import ftbq progress` | прогресс из FTB Quests |
| `/rysquests import patchouli <книга> [ns]` | гайды и 3D-структуры из `patchouli_books/<книга>` |
| `/rysquests migrate --dry-run\|--apply` | миграции |
| `/rysquests progress <игрок> reset\|complete <квест>` | правка прогресса квеста |
| `/rysquests wallet get <игрок> <валюта>` | баланс; результат команды равен балансу, удобно для `/execute` |
| `/rysquests wallet add\|take\|set <игроки> <валюта> <сумма>` | зачислить, списать (не больше, чем есть), поставить |
| `/rysquests wallet treasury get\|add\|take\|set ...` | то же для казны команды игрока |
| `/rysquests shop reset <игроки> [сделка]` | сбросить лимиты, личные и команды |
| `/rysquests title give\|take <игроки> <ключ>` | выдать или снять титул |
| `/rysquests achievement complete <игрок> <id> [уровень]` | засчитать вместе с нижними уровнями, награды выдаются |
| `/rysquests achievement reset <игрок> <id> [уровень]` | сбросить вместе с верхними уровнями, выданное не забирается |
| `/rysquests team create\|invite <игрок>\|join\|leave` | команды игроков |

## 21. Настройки и переводы

`config/rysquests-common.toml`: `editorFolder`, куда пишут редакторы. Клиентский конфиг: `hudInCombat`, показывать ли закреплённые карточки в бою.

Шаблоны ключей lang:

| Что | Ключ |
|---|---|
| Квест | `quest.<ns>.<путь через точки>.title`, `.subtitle`, `.text` |
| Глава | `chapter.<ns>.<id>.title` |
| Гайд | `guide.<ns>.<id>.title` |
| Достижение | `achievement.<ns>.<id>.name`, `.text`, `.hint` |
| Валюта | `rysquests.currency.<id>` |
| Вкладка | см. раздел 11 |

Переводы и картинки из папки редактора идут клиентам вместе с книгой, клиентский ресурспак не нужен.

## 22. Ограничения

- Главы показываются только с `section: story`. Другое значение загружает главу, но в книге её нет.
- Поля `size` квеста, `page` квеста и `default_theme` книги читаются, но пока ни на что не влияют.
- Сохранённый квест редактор не удаляет: убери файл сам и добавь `remove` в миграции.
- Квесты из общего файла `_quests.json` редактор не пишет.
- Сделки и достижения правятся только файлами.
- Анимация Ponder внутри страницы не показывается, размышление открывается своим экраном.
- `tools/rysquests_check.py` не проверяет структуры, их проверяет `/rysquests validate`.
- Без JEI `[[recipe:]]` рисует простую сетку и понимает только рецепты, которые сообщают результат.
- `tools/rysquests_check.py` проверяет главы и квесты, но не вкладки, сделки и достижения. Их проверяет `/rysquests validate`.
