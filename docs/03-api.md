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

## KubeJS (этап M7+, через отдельный плагин KubeJS)

```js
RYSQuestsEvents.task('mypack:looked_at_boiler', e => {
  e.title('Посмотреть на бойлер');
  e.poll(player => player.rayTraceBlock(8).block.id == 'create:fluid_tank' ? 1 : 0);
});
RYSQuestsEvents.completed(e => {
  if (e.quest == 'repys:power/electric_mechanism') e.server.runCommandSilent('...');
});
RYSQuestsEvents.reward('mypack:unlock', e => { e.grant((player, def) => { ... }); });
```

Регистрируется как `KubeJSPlugin`, биндинги только когда KubeJS загружен (`ModList`).

## Совместимость и стабильность

- Пакет `api` следует семантическому версионированию отдельно от мода: `api_version` в `neoforge.mods.toml` как свойство.
- Кодеки данных обратно совместимы в пределах мажорной версии мода; поломки сопровождаются миграцией формата (`version` в прогрессе) и записью в `02-data-format.md`.
