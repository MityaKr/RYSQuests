# RYSQuests для авторов сборок и их инструментов

Документ для автора сборки и для ИИ, который ведёт её код. Формат полей: `02-data-format.md`.

## Где лежит книга

Книга это файлы, редактировать можно любым инструментом. После правки: `/reload` или `/rysquests reload`.

| Что | Где |
|---|---|
| Книга в датапаке | `data/<ns>/rysquests/` |
| Книга из импорта FTB | `config/rysquests/import/` (пак подключается сам) |
| Соответствие id FTB → наши | `config/rysquests/import/ftbq-ids.json` |
| Переводы импорта | `config/rysquests/import/assets/<ns>/lang/` |
| Отчёт импорта | `config/rysquests/import/import-report.md` |

Важно про импорт: `/rysquests import ftbq` пересоздаёт `data/` и `assets/` внутри папки импорта. Правки, которые должны жить дальше, делайте в FTB и импортируйте заново либо перенесите книгу в свой датапак и больше не импортируйте. Чтобы id квеста не менялся, не удаляйте `ftbq-ids.json`.

Адаптация сборки, рекомендуемый порядок:

1. Выгрузить id пака: `/rysquests dev registry`, файл `logs/rysquests-registry.json`.
2. Импортировать: `/rysquests import ftbq repys`, затем `/rysquests import ftbq progress`.
3. Перенести `config/rysquests/import/data` и `assets` в датапак сборки или в Python-генератор и дальше менять там.
4. Проверять без игры: `tools/rysquests_check.py`.
5. Проверять в игре: `/reload`, затем `/rysquests dev report`.
6. Переименование квеста: запись `rename` в `migrations.json`, иначе прогресс игроков потеряется.

## Команды

Уровень прав 2, кроме `team`.

| Команда | Что делает | Результат |
|---|---|---|
| `/rysquests reload` | штатная перезагрузка датапаков | чат |
| `/rysquests validate` | проверка книги и тегов | чат, `logs/rysquests-validate.txt` |
| `/rysquests dev report` | полный отчёт для инструментов | `logs/rysquests-report.json`, `.csv` |
| `/rysquests dev registry` | все id пака | `logs/rysquests-registry.json` |
| `/rysquests import ftbq [ns]` | книга из FTB Quests, по умолчанию `ns` = `ftbq` | `config/rysquests/import/` |
| `/rysquests import ftbq progress` | прогресс из `world/ftbquests/` | чат |
| `/rysquests migrate --dry-run` | что изменят миграции | чат, `logs/rysquests-migrate.txt` |
| `/rysquests migrate --apply` | применить, сначала бэкап | бэкап в `world/data/` |
| `/rysquests progress <игрок> reset\|complete <квест>` | правка прогресса | |
| `/rysquests team create\|invite <игрок>\|join\|leave` | команды игроков | |

Новые миграции применяются сами при старте мира и после `/reload`.

## Проверка без игры

Нужен только Python 3:

```
python tools/rysquests_check.py <корень пака> --registry logs/rysquests-registry.json --lang ru_ru
python tools/rysquests_check.py <корень пака> --registry logs/rysquests-registry.json --json
```

`<корень пака>` содержит `data/<ns>/rysquests/`. Проверяет: JSON, обязательные поля, повторы id, отсутствующие зависимости и главы, циклы, типы задач и наград, предметы, теги, существ, продвижения, переводы. Код выхода 1, если есть ошибки. `--json` печатает результат для других программ: `{chapters, quests, errors, warnings, issues: [{severity, code, subject, detail}]}`.

Без `--registry` id предметов не проверяются.

## Отчёт `rysquests-report.json`

```json
{ "chapters": 54, "quests": 2381,
  "sections": [ { "id": "errors", "entries": [ { "subject": "repys:a/b", "detail": "missing_dependency: repys:a/c" } ] } ] }
```

Секции: `errors`, `warnings`, `unreachable` (квест нельзя открыть: зависимостей меньше, чем нужно, или стадию ничто не выдаёт), `no_recipe` (предмет задачи без известного рецепта). Рецепты машин некоторых модов сюда не попадают как результат, поэтому `no_recipe` это кандидаты, а не приговор.

Коды ошибок: `parse_error`, `duplicate_quest`, `duplicate_chapter`, `unknown_chapter`, `missing_dependency`, `dependency_cycle`, `unknown_item`, `empty_reward`; предупреждения: `broken_dependency`, `unknown_group`, `unknown_chapter_dependency`, `unknown_chapter_order`, `multiple_books`, `empty_tag`.

Сломанный квест не валит книгу: он выпадает вместе с зависимыми, остальное загружается.

## Из кода мода и скриптов

- `works.repka.rysquests.api.QuestsApi`: книга, отметка квеста, регистрация стадий.
- `QuestCompletedEvent` на игровой шине NeoForge.
- Свои типы задач и наград: реестры `rysquests:task_type` и `rysquests:reward_type`.
- Из KubeJS: `NativeEvents.onEvent('works.repka.rysquests.api.QuestCompletedEvent', event => { ... })`. Отдельного плагина KubeJS с регистрацией задач пока нет.
- Команда как награда: `{ "type": "rysquests:command", "command": "ryscope stage grant @s config:iron_age", "as_server": true }`.

Рецепт для ссылки в тексте: `[[item:minecraft:stone]]`, `[[quest:repys:a/b|подпись]]`, полный синтаксис в `02-data-format.md`.
