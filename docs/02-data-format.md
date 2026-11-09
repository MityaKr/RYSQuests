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
  "hud": { "max_pinned": 3 }
}
```

Если `book.json` отсутствует, значения по умолчанию: `solo`, `flow`, `ryscore:slate`.

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
  "hidden": false
}
```

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
  "size": 1,                           // 1 обычный, 2 крупный узел (веха)
  "optional": false,
  "hidden": false,                     // не отправлять клиенту, пока зависимости не выполнены
  "repeatable": null,                  // null или {"cooldown_minutes": 60}
  "page": false,                       // true: страница без задач, засчитывается прочтением
  "tags": ["milestone"]                // milestone: веха в Ленте, key_item: ключевой предмет эпохи
}
```

Поля `chapter` можно опускать: тогда берётся из папки. `requires` ссылаются только на квесты; зависимость от главы задаётся на главе.

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

Правила выдачи: награды выдаются по действию «Забрать» (не автоматически), в инвентарь; если места нет, остаются «к получению». `choice` требует выбора клиентом. Для `team` режима на награде есть `team_mode`: `each` (каждому игроку) | `once` (один раз на команду, забирает первый).

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

## Lang

`assets/<ns>/lang/ru_ru.json`, `en_us.json`. Ключи по шаблону `quest.<ns>.<chapter>.<quest>.title|subtitle|text`, `chapter.<ns>.<chapter>.title`, `task.<…>`. Валидатор сообщает о ключах без перевода в активном языке.

## Разметка текста

Используется в `text`, `subtitle`, страницах:

- `[[item:minecraft:redstone]]` иконка + имя, клик → рецепты во вьюере; `[[item:...|подпись]]`.
- `[[quest:repys:power/wires]]` ссылка на квест.
- `[[recipe:kubejs:electric_mechanism]]` раскрывающийся рецепт (если есть мост к вьюеру).
- `[[guide:create:guide#4]]`, `[[ponder:create:mechanical_crafter]]`, `[[url:https://…]]`.
- `**жирный**`, `__курсив__`, `!!предупреждение!!` (янтарный блок), `>>спойлер<<`.
- Переносы строк `\n`, абзац пустой строкой.

Парсер в `rysquests.client.markup`, синтаксис как в RYScope; кандидат на перенос в RYSCore.

## Канонический порядок полей

При записи (редактор, импортёр) поля идут в порядке, в котором перечислены в этом документе. Это делает диффы в git читаемыми. Загрузчик порядок не требует.

## Хэш книги

SHA-256 от канонического JSON всех квестов и глав после линковки. Используется для кэша раскладки и проверки совпадения клиент/сервер.

## Прогресс (внутренний формат, NBT через Codec)

```
ProgressStore {
  version: int,
  applied_migrations: [string],
  teams: { <uuid>: {
    quests: { <quest id>: { tasks: [int], completed: long, claimed: [int] } },   // состояние вычисляется из них
    pinned: [<quest id>],
    archive: { <old id>: {...} }
  } }
}
```

Формат может меняться между версиями мода с собственной миграцией (`version`).
