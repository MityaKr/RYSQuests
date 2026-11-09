# 08 · Проверить по чужому коду до написания своего

Каждый пункт: что узнать, где смотреть, куда записать. Результаты в `docs/verify/<тема>.md`.

## NeoForge 21.1 (исходники через ModDevGradle)

- Регистрация payload'ов: `RegisterPayloadHandlersEvent`, `PayloadRegistrar.playToClient/playToServer`, `CustomPacketPayload.Type`, `StreamCodec`. Лимит размера пакета и как разбивать.
- `AddReloadListenerEvent` и `SimplePreparableReloadListener` для серверных датапаков; как получить `RegistryAccess` для кодеков с реестрами.
- `SavedData` и `SavedData.Factory` в 1.21; `DimensionDataStorage.computeIfAbsent`.
- События: `PlayerTickEvent.Post`, `AdvancementEvent.AdvancementEarnEvent`, `LivingDeathEvent`, `PlayerEvent.PlayerLoggedInEvent`, `OnDatapackSyncEvent` (отправка книги при входе и reload).
- Клиент: `RegisterKeyMappingsEvent`, `RegisterGuiLayersEvent` для HUD, `Screen` жизненный цикл, `ScrollWidget`. Рисование не исследуем заново: `Draw` и `Theme` берём из RYSCore (ADR-14, ADR-23).
- Конфиг: `ModConfigSpec`, регистрация через `ModContainer.registerConfig`.
- `DeferredRegister.create(ResourceKey<Registry<T>>, modid)` для собственных реестров и `NewRegistryEvent`.
- Кодеки: `Codec.dispatch` по `type`, `ResourceLocation.CODEC`, `ItemStack.CODEC` и `DataComponentPatch` для компонентов в наградах, `TagKey` для `#tags`.

## FTB Quests (1.21.1, декомпиляция CFR или исходники GitHub)

- Структура `config/ftbquests/quests/`: `data.snbt`, `chapter_groups.snbt`, `chapters/*.snbt`, `reward_tables/*.snbt`. Поля квеста: `id` (hex), `x`, `y`, `dependencies`, `tasks[]` с `type`, `rewards[]`, `title`, `subtitle`, `description[]`, `icon`, `size`, `shape`, `hide`, `optional`, `min_required_dependencies`.
- Типы задач и наград и их поля: `item` (с `consume_items`, `only_from_crafting`), `advancement`, `checkmark`, `stat`, `kill`, `location`, `dimension`, `biome`, `structure`, `fluid`, `forge_energy`, `xp`, `observation`, `stage`; награды `item`, `xp`, `xp_levels`, `command`, `loot`, `choice`, `stage`, `random`, `custom`.
- Прогресс: `world/data/ftbquests/` (team data, `data/ftbteams`), формат записей выполнения, как привязаны к FTB Teams.
- Как FTB Quests синхронизирует (целиком или диффами) и чем страдает производительность на больших главах — чтобы не повторить.

Результат: `docs/verify/ftbquests-format.md` с таблицей соответствия типов FTB → RYSQuests и списком непереносимого.

## Heracles (для сравнения формата JSON и раскладки)

- Как хранит квесты (JSON?), есть ли строковые id, как делает зависимости и раскладку. Что взять, чего избежать.

## JEI 1.21.1

- API для открытия рецептов и использований из своего экрана (`IJeiRuntime`, `IRecipesGui.show`), регистрация плагина, maven-координаты для `compileOnly`.
- Как сообщить JEI о предмете под курсором на нашем экране: через этот путь RYScope определяет предмет на чужих экранах (`Overlay.foreignStack`), и в книге заработают его клавиши и Линза. Перехват `A` и `G` в этом случае учесть.
- С RYScope JEI стартует в потоке `RYScope-JEI`: сверить с `runtime/JeiAsync.java`, что можно делать в `onRuntimeAvailable`.

## Производительность

- Стоимость перебора инвентаря 2000 игроков раз в секунду (микробенчмарк в JUnit на синтетике) и хэша инвентаря.
- Размер сжатой книги на 2300 квестов (оценить на импорте RepYS).
