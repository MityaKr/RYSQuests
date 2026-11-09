# M1 · Загрузка, реестры, сеть

Проверено по исходникам NeoForge 21.1.265, декомпиляция ModDevGradle.

## Загрузка из датапаков

- `AddReloadListenerEvent` (игровая шина): `addListener(PreparableReloadListener)`, `getRegistryAccess()` даёт замороженные реестры текущей перезагрузки.
- `SimplePreparableReloadListener<T>`: `prepare` в фоновом executor, `apply` в игровом. Весь разбор, валидацию и сжатие делаем в `prepare`, в `apply` только подмена ссылки.
- `FileToIdConverter.json("rysquests")`: `listMatchingResources`, `fileToId`.
- Теги привязываются к реестрам после слушателей, поэтому в `prepare` их проверять нельзя. Проверка пустых тегов перенесена в `/rysquests validate`.

## Реестры

- `DeferredRegister.create(ResourceKey, modid)`, `makeRegistry(Consumer<RegistryBuilder<T>>)` возвращает `Registry<T>`, `register(IEventBus)`.
- `registry.byNameCodec().dispatch("type", ...)` для кодеков задач и наград.
- `RecordCodecBuilder.group` принимает не больше 16 полей, у `Quest` их 19. Необязательные вынесены в `QuestOptions`, он вложен как `MapCodec` и остаётся плоским в JSON.

## Сеть

- `RegisterPayloadHandlersEvent.registrar(id)`, `versioned`, `playToClient(type, codec, handler)`; отдельной регистрации клиентского обработчика в 21.1 нет, обработчик передаётся и на сервере. Поэтому в общем коде вызов идёт через подставляемый `BiConsumer`, сервер не импортирует `net.minecraft.client`.
- `OnDatapackSyncEvent`: `getRelevantPlayers()` срабатывает при входе и после `/reload`.
- `PacketDistributor.sendToPlayer`.
- `GenericPacketSplitter` сам режет слишком большие пакеты и собирает их обратно, предел сборки `splitPacketMaxSizeLimitMb` по умолчанию 128 МБ. Свой протокол частей не нужен (ADR-22).
- В соединении внутри одного процесса пакет передаётся объектом, без `StreamCodec`, поэтому распаковка и декодирование книги выполняются в обработчике, а не в кодеке.

## Сериализация

- Книга на сеть: `Codec` в NBT через `RegistryOps`, затем `NbtIo.writeCompressed`.
- `DataComponentPatch.CODEC` требует `RegistryOps`, поэтому загрузчик работает через `registries.createSerializationContext(JsonOps.INSTANCE)`.
- Канонический JSON для хэша: ключи объектов сортируются рекурсивно, главы и квесты сортируются по id.

## Пример

`#c:plates/copper` в NeoForge нет, в примере заменён на `#c:ingots/copper`. Награда `choice` есть в примере, поэтому её кодек сделан в M1, а выдача остаётся в M2.
