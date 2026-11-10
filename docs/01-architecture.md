# 01 · Архитектура

## Обзор

Один Gradle-проект, один пакет `works.repka.rysquests`. Темы и рисование берутся из отдельного мода RYSCore (`ryscore`), он вложен в наш jar через jarJar и объявлен обязательной зависимостью (ADR-23). Своего пакета `works.repka.ryscore` у нас нет.

Сервер авторитетен: книга загружается из датапаков на сервере, прогресс хранится и проверяется на сервере, клиент получает книгу один раз и прогресс диффами. В одиночной игре всё то же через встроенный сервер.

```
датапаки ──► BookLoader ──► QuestBook (неизменяемый граф) ──► BookValidator ──► отчёт
                                 │
                                 ├──► BookSyncPayload (при входе, /reload) ──► ClientBook
                                 │
TaskChecker ◄── события игры     │
     │                           │
     ▼                           ▼
ProgressStore (SavedData) ──► ProgressDiffPayload ──► ClientProgress ──► экраны, HUD
     ▲
     └── MigrationEngine (при загрузке мира, /rysquests migrate)
```

## Пакеты и ответственность

### `rysquests` (общее, common)

| Пакет | Что внутри |
|---|---|
| `RYSQuests` | точка входа мода: регистрация реестров, конфигов, сетевых пакетов, команд |
| `data` | записи модели: `Chapter`, `Quest`, `TaskDef`, `RewardDef`, `Layout`, `BookMeta`; кодеки; `QuestBook` (граф с индексами); `BookLoader` (reload listener); `BookValidator` |
| `task` | `TaskType<T extends TaskDef>` реестр; реализации: `item`, `checkbox`, `advancement`, `stat`, `visit`, `kill`, `fluid`; `TaskChecker` (подписки на события, редкий тик) |
| `reward` | `RewardType<R extends RewardDef>` реестр; реализации: `item`, `choice`, `xp`, `command`, `loot_table`, `stage` |
| `progress` | `ProgressStore` (SavedData), `QuestProgress`, `TaskProgress`, `ProgressService` (бизнес-логика: доступность, сдача, выдача наград), `ProgressDiff` |
| `migrate` | `Migration` (rename/merge/remove), `MigrationEngine`, `DryRunReport` |
| `team` | `TeamId` (UUID игрока в режиме solo или UUID команды), `TeamManager` (минимальный: solo и серверный режим в MVP; команды в M5) |
| `stage` | `StageProvider` интерфейс; `AdvancementStageProvider` встроенный |
| `network` | payload'ы и обработчики |
| `command` | `/rysquests reload | validate | migrate | import | progress` |
| `integration` | `RecipeViewerBridge` (интерфейс + реализация для JEI за `ModList.isLoaded`), `ftbq` импортёр |
| `dev` | отчёты: циклы, недостижимые, битые предметы, переводы; экспорт CSV |
| `api` | публичные интерфейсы и события для других модов (см. `03-api.md`) |

### `rysquests.client`

| Пакет | Что внутри |
|---|---|
| `ClientBook`, `ClientProgress` | клиентские копии, обновляются из пакетов |
| `screen` | `BookScreen` (контейнер видов), виды `HomeView`, `FlowView`, `MapView`, `ListView`; `QuestCard` |
| `widget` | виджеты книги: `Panel`, `Slot`, `ProgressBar`, `Button`, `Tabs`, `ScrollList` (виртуализованный), `Tooltip`, `TextBlock` |
| `layout` | `LayeredLayout` (автораскладка), `LayoutCache` |
| `hud` | `StickerBoard` и интерфейс `Sticker` (id, высота, отрисовка, приоритет), стикеры квестов |
| `input` | `KeyMapping`: открыть книгу (K), сдать готовое (G), закрепить (A) |
| `markup` | парсер `[[kind:id|label]]` с синтаксисом RYScope, `**жирный**`, предупреждения; рендер с иконками предметов и кликами |
| `util` | масштаб интерфейса, анимации с флагом «без анимаций» |

### RYSCore (отдельный мод, папка только для чтения)

| Класс | Что даёт |
|---|---|
| `works.repka.ryscore.theme.Theme` | токены темы и примитивы `panel`, `card`, `plate`, `slot`, `hover`, `button`, `input`, `tab`, `chip`, `tooltipBox` |
| `works.repka.ryscore.theme.ThemeManager` | загрузка тем из `assets/<ns>/ryscore_themes/*.json`, общий для модов семьи выбор темы |
| `works.repka.ryscore.ui.Draw` | скругления в пикселях устройства, текст, стрелки, замок, значки `info` и `question` |
| `works.repka.ryscore.ui.Loading` | индикатор расчёта внутри окна |

Чего в коре нет, пишем у себя (`client.widget`, `client.hud`, `client.markup`). Когда это понадобится второму моду, переносим в кор через блок «Запрос к RYSCore». Правила вида и контракт: `RYSCore/CLAUDE.md`.

## Модель данных в памяти

`QuestBook` строится один раз на загрузку и неизменяем:

- `Map<ResourceLocation, Chapter>`, `Map<ResourceLocation, Quest>`, порядок глав.
- Индексы: `requires` (квест → его зависимости) и обратный `unlocks`; `byItem` (предмет → квесты, где он в задачах) для интеграции с вьюером рецептов.
- `hash` книги (SHA-256 канонического JSON) для кэша раскладки и для проверки, что клиент и сервер совпадают.

`QuestProgress` на пару (team, quest): состояние `LOCKED | AVAILABLE | IN_PROGRESS | COMPLETED | CLAIMED`, по задачам `TaskProgress` (счётчик, выполнена), выбранные награды, временные метки начала и завершения, кто выполнил (для команд).

Доступность квеста считается на сервере функцией от прогресса зависимостей и `requires_mode` (`all` | `any` | `n`) плюс стадии. Клиент получает уже посчитанный статус, но имеет ту же функцию для оптимистичного отображения.

## Загрузка книги

`BookLoader extends SimplePreparableReloadListener<RawBook>`, регистрируется через `AddReloadListenerEvent` (серверные датапаки). Читает:

- `data/<ns>/rysquests/chapters/<chapter>/_chapter.json` — метаданные главы;
- `data/<ns>/rysquests/chapters/<chapter>/*.json` — по квесту на файл (или массив в одном файле);
- `data/<ns>/rysquests/migrations.json`;
- `data/<ns>/rysquests/book.json` — настройки книги (режим прогресса, тема по умолчанию, вид по умолчанию).

Этапы: parse (Codec) → link (разрешение зависимостей, ошибки на отсутствующие id) → validate (циклы через DFS, дубли, предметы без регистрации, награды без предметов) → build индексы → hash. Ошибки валидации не валят загрузку: сломанные квесты помечаются и попадают в отчёт `/rysquests validate`, книга грузится без них.

## Проверка задач (TaskChecker)

Принцип: по событиям, не по тикам, где событие есть; редкий тик с разносом, где его нет.

| Тип | Как проверяется |
|---|---|
| `item` (observe) | `PlayerTickEvent.Post` каждые 20 тиков с разносом по игрокам: пересчёт по инвентарю только для активных квестов игрока, где есть item-задачи. Кэш «инвентарь не менялся» через сравнение хэша содержимого. |
| `item` (consume) | только по команде игрока «Сдать»: сервер проверяет и изымает |
| `advancement` | `AdvancementEvent.AdvancementEarnEvent` |
| `stat` | тот же редкий тик, чтение `ServerStatsCounter` |
| `visit` | редкий тик: измерение, биом (`level.getBiome(pos)`), структура (`StructureManager.getStructureWithPieceAt`) |
| `kill` | `LivingDeathEvent` с источником урона игрока |
| `fluid` | редкий тик: capability жидкости блока перед игроком или контейнера в инвентаре |
| `checkbox` | по команде игрока |

Все проверки на сервере. Результат меняет `ProgressStore` и формирует `ProgressDiff`.

## Хранение прогресса

`ProgressStore extends SavedData`, файл `data/rysquests_progress.dat` в основном измерении. Структура: `teams: Map<UUID, TeamProgress>`, `TeamProgress.quests: Map<ResourceLocation, QuestProgress>`, `archive: Map<ResourceLocation, QuestProgress>` для удалённых квестов. Запись через Codec в NBT, `setDirty()` при изменении, сохранение штатным механизмом мира.

Пороги размера: 2300 квестов × 50 игроков ≈ 115 тыс. записей, в NBT это единицы мегабайт; приемлемо. Если вырастет, разбить на файл на команду (ADR при необходимости).

## Миграции

При загрузке мира `MigrationEngine` сравнивает `appliedMigrations` в `ProgressStore` со списком в `migrations.json` и применяет новые по порядку: `rename` переносит записи, `merge` объединяет (прогресс = максимум по задачам, статус = лучший), `remove` переносит в архив. `--dry-run` считает то же без записи и печатает отчёт. Бэкап файла прогресса перед `--apply`.

## Сеть

Все payload'ы через `PayloadRegistrar` (`RegisterPayloadHandlersEvent`), `StreamCodec`.

| Payload | Направление | Когда |
|---|---|---|
| `BookSyncPayload` | S→C | вход игрока, `/reload`; книга целиком, GZIP поверх NBT; большие пакеты режет транспорт NeoForge (ADR-22) |
| `ProgressSnapshotPayload` | S→C | вход игрока: весь прогресс его команды |
| `ProgressDiffPayload` | S→C | при любом изменении: список изменённых `QuestProgress` |
| `SubmitTaskPayload` | C→S | «Сдать» задачу или «Сдать всё готовое» |
| `ClaimRewardPayload` | C→S | забрать награду, с выбором для `choice` |
| `CheckboxPayload` | C→S | отметить чекбокс |
| `PinQuestPayload` | C→S | закрепить/снять (хранится на сервере, чтобы переживало перезаход) |
| `MigrationReportPayload` | S→C | ответ на dry-run для админа |

Сервер валидирует каждый запрос (доступность квеста, наличие предметов), клиент никогда не меняет прогресс сам.

## Клиент

`ClientBook` и `ClientProgress` обновляются только из пакетов. `BookScreen` открывается по K на текущем квесте (последний закреплённый или первый доступный незавершённый). Виды переключаются табами; состав виджетов общий, меняется раскладка.

Раскладка карты: `LayeredLayout` (упрощённый Sugiyama: слои по длиннейшему пути от корней, минимизация пересечений барицентром за 4 прохода, фиксированные узлы из `layout.x/y` не двигаются) считается в фоне при первом открытии главы и кэшируется в `config/rysquests/layout-cache/<bookHash>/<chapter>.json`. Рендер только видимых узлов и рёбер.

HUD: стикеры закреплённых квестов регистрируются в нашем `client.hud.StickerBoard`; доска прячется в бою (по конфигу) и когда открыт любой экран. У RYScope своя доска целей (x=4, y=4, ширина 132, только без открытого экрана); наша с ней не пересекается. Контракт `Sticker` рассчитан на перенос доски в кор.

## Потоки

- Главный серверный тик: события, редкие проверки, применение результатов.
- Фон (`Util.backgroundExecutor()`): валидация книги, отчёты, dry-run миграций, импорт FTB Quests.
- Клиентский рендер-поток: только рендер и ввод.
- Клиентский фон: раскладка графа, парсинг разметки длинных текстов. Окно при этом открывается сразу, внутри индикатор `Loading`. Работа с JEI идёт в главном потоке порциями по несколько миллисекунд за кадр: плагины JEI в фоновом потоке ломаются.

Правило: результат фоновой работы передаётся в тик через `server.execute(...)` / `Minecraft.getInstance().execute(...)`, никаких общих мутабельных структур.

## Интеграции

- `RecipeViewerBridge`: `showRecipes(ItemStack)`, `showUsages(ItemStack)`, `renderRecipePreview(...)`; реализации `JeiBridge` (за `ModList.get().isLoaded("jei")`), `NoopBridge`. EMI позже. Отдельный мост к RYScope не нужен (ADR-20).
- `StageProvider`: `boolean has(ServerPlayer, String stage)`, `grant(...)`. Встроенный: продвижения (`stage` = id продвижения). KubeJS/Game Stages через API.
- Команды: режим `team` спрашивает `TeamsApi` из RYSCore (ADR-26).
- RYScope: зависимости от него нет, общая тема приходит через RYSCore (ADR-23). Стадия RYScope как награда сначала выдаётся командой `/ryscope stage grant @s <id>`, id файла из папки сборки это `config:<имя>`. Перед своим миксином сверяться с `ryscope.mixins.json`: там уже есть вмешательства в `GuiGraphics`, `Slot`, `CraftingMenu` и другие классы.
- Импорт FTB Quests: читает SNBT из `config/ftbquests/quests/**` (формат 1.21: `chapters/*.snbt`, `reward_tables`, `chapter_groups`, `data.snbt`) и прогресс из `world/data/ftbquests/`. Соответствие числовых id → строковые ключи по названиям с дедупликацией; сохраняется как первая миграция и как `import-report.md`. Точный формат проверить по коду FTB Quests (см. `08-verify-first.md`).

## Конфиг

`ModConfigSpec`: клиент (вид по умолчанию, анимации, HUD в бою), сервер (режим прогресса по умолчанию, интервал проверки задач, лимит размера пакета книги).

## Безопасность и границы

- Клиент не доверен: любой запрос проверяется на сервере.
- Книга скрытых квестов: квесты с `hidden_until` не отправляются клиенту до выполнения условия (режим прогрессии как в концепте, но в MVP только флаг `hidden`).
- Команды: `/rysquests` требует уровень прав 2, кроме `progress me`.
