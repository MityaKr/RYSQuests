# 03 · API

Публичный API живёт в `works.repka.rysquests.api`. Всё остальное считается внутренним и может меняться.

## Реестры

Два реестра через `DeferredRegister` на собственных `Registry`:

```java
public static final ResourceKey<Registry<TaskType<?>>> TASK_TYPES = ...;   // rysquests:task_type
public static final ResourceKey<Registry<RewardType<?>>> REWARD_TYPES = ...; // rysquests:reward_type
```

Тип задачи:

```java
public interface TaskType<T extends TaskDef> {
    MapCodec<T> codec();                                  // для dispatch по "type"
    TaskChecker<T> checker();                             // как проверять
    Component describe(T def, TaskProgress progress);     // текст для карточки
    default ItemStack icon(T def) { return ItemStack.EMPTY; }
}

public interface TaskChecker<T extends TaskDef> {
    /** Пересчёт по текущему состоянию игрока. Вызывается редким тиком. */
    default Optional<TaskProgress> poll(ServerPlayer player, T def, TaskProgress current) { return Optional.empty(); }
    /** Попытка сдать вручную (consume). */
    default SubmitResult submit(ServerPlayer player, T def, TaskProgress current) { return SubmitResult.unsupported(); }
    /** Нужен ли этому типу периодический опрос. */
    default boolean polls() { return false; }
}
```

События для событийных задач подписываются внутри мода, но тип может объявить интерес:

```java
public interface EventDrivenTask<T extends TaskDef, E extends Event> {
    Class<E> eventType();
    Optional<TaskProgress> onEvent(ServerPlayer player, T def, TaskProgress current, E event);
}
```

Тип награды:

```java
public interface RewardType<R extends RewardDef> {
    MapCodec<R> codec();
    void grant(ServerPlayer player, R def, RewardContext ctx);  // ctx: выбор для choice, команда
    Component describe(R def);
    List<ItemStack> preview(R def);                             // для иконок в карточке
}
```

## Сервисы

```java
public interface QuestsApi {
    static QuestsApi get();                      // через ServiceLoader или статический холдер
    QuestBook book(MinecraftServer server);      // текущая книга
    ProgressService progress(MinecraftServer server);
    void registerStageProvider(StageProvider provider);
    void registerRecipeViewer(RecipeViewerBridge bridge);
}

public interface ProgressService {
    QuestState state(TeamId team, ResourceLocation quest);
    boolean isAvailable(TeamId team, ResourceLocation quest);
    SubmitResult submit(ServerPlayer player, ResourceLocation quest, int taskIndex);
    SubmitResult submitAllReady(ServerPlayer player);
    ClaimResult claim(ServerPlayer player, ResourceLocation quest, int[] choices);
    void complete(TeamId team, ResourceLocation quest, @Nullable ServerPlayer by);   // для команд и скриптов
    void reset(TeamId team, ResourceLocation quest);
}
```

## События (NeoForge event bus)

```java
QuestAvailableEvent(team, quest)          // стал доступен
TaskProgressEvent(team, quest, index, before, after)
QuestCompletedEvent(team, quest, player?) // все задачи выполнены
RewardClaimedEvent(team, quest, player, rewards)
BookReloadedEvent(book)                   // после загрузки датапаков
```

Все события серверные. Отменяемых нет в MVP.

## Стадии

```java
public interface StageProvider {
    ResourceLocation id();
    boolean has(ServerPlayer player, String stage);
    void grant(ServerPlayer player, String stage);
}
```

Встроенный `AdvancementStageProvider`: стадия это id продвижения. Другие моды (Game Stages, KubeJS stages) регистрируют свой провайдер; при нескольких провайдерах `stage_required` проверяется как «любой подтвердил».

## Мост к вьюеру рецептов

```java
public interface RecipeViewerBridge {
    boolean available();
    void showRecipes(ItemStack stack);
    void showUsages(ItemStack stack);
    /** Необязательно: нарисовать компактный предпросмотр рецепта в карточке. */
    default boolean renderPreview(GuiGraphics g, ItemStack stack, int x, int y, int w, int h) { return false; }
}
```

Клиентский интерфейс. Реализация для JEI подключается, если JEI загружен; иначе `NoopBridge` и кнопки «Рецепт» не показываются.

## KubeJS

Плагин грузится, только если KubeJS установлен. События в серверных скриптах:

```js
RYSQuestsEvents.completed(e => { console.info(e.quest + ' ' + e.player.username); });
RYSQuestsEvents.completed('repys:power/electric_mechanism', e => { e.server.runCommandSilent('...'); });
RYSQuestsEvents.task('mypack:looked_at_boiler', e => { if (/* условие */ true) e.setProgress(1); });
RYSQuestsEvents.reward('mypack:unlock', e => { e.player.give('minecraft:diamond'); });
```

Скриптовая задача и награда в книге:

```json
{ "type": "rysquests:script", "script": "mypack:looked_at_boiler", "count": 1, "title": "Посмотреть на бойлер" }
{ "type": "rysquests:script", "script": "mypack:unlock" }
```

`task` вызывается примерно раз в секунду, пока квест открыт: держите обработчик дешёвым.

Кошелёк в серверных скриптах это `RYSQuestsWallet`, в Java это `works.repka.rysquests.api.WalletApi`. Методы описаны в `for-pack-authors.md`, пример блока-обменника в `examples/kubejs/`:

```js
BlockEvents.rightClicked('mypack:exchanger', e => {
  const coins = RYSQuestsWallet.exchangeHeld(e.player, 'oldmod:silver_coin', 'coin', 10, 1);
  if (coins > 0) e.cancel();
});
```

Квесты в серверных скриптах это `RYSQuests` (`works.repka.rysquests.api.QuestsApi`). С 0.2.8 в нём `progressOwner(player)` (id команды или игрока, чей прогресс он двигает) и `collect(server, owner, achievement, item)` или `collect(player, achievement, item)`: отметить предмет коллекции достижения, в игре владелец или нет, для переноса отметок из своего скрипта (ADR-62).

С 0.2.9 чтение прогресса: `isCompleted(owner, quest)` и `completed(owner)` (набор id сделанных квестов), то же с игроком вместо владельца. Сделан значит сделан сейчас или в прошлом круге повторяемого квеста, как считают открытие следующих квестов (`QuestProgress.everDone`). Владелец может быть не в игре, читается сохранённый прогресс; без запущенного сервера ответ `false` и пустой набор. Пример: `RYSQuests.isCompleted(RYSQuests.progressOwner(player), 'repys:a2_chronicle/dungeon')`. Старый путь `ProgressStore.get(server).allProgress()` через `Java.loadClass` работает как раньше, но это не API и может меняться.

## Карта семьи (RYSMap)

С 0.2.9 книга сама кладёт на карту слой «Цели квестов» (`rysquests:goals`, контракты RYSCore 1.13 `map.MapApi` и `world.Labels`), ничего настраивать не нужно. Место цели берётся из задачи `rysquests:visit` с `pos` (центр шара или середина коробки с `size`, без `dimension` это верхний мир). Метка видна, пока квест открыт и место ещё не посещено; у панели метки кнопки «Открыть квест» и «Сохранить точкой», поиск окна карты ищет по именам квестов. Подпись в мире только у закреплённого квеста. В книге у такой задачи кнопка «На карте». Без RYSMap ничего этого нет.

## Совместимость и стабильность

- Пакет `api` следует семантическому версионированию отдельно от мода: `api_version` в `neoforge.mods.toml` как свойство.
- Кодеки данных обратно совместимы в пределах мажорной версии мода; поломки сопровождаются миграцией формата (`version` в прогрессе) и записью в `02-data-format.md`.
