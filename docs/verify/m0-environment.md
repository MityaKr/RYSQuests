# M0 · Среда и подключение примера

Проверено 2026-11-09 на ПК автора.

## Доступ к репозиториям

Все адреса ответили 200: `maven.neoforged.net/releases`, `plugins.gradle.org/m2`, `repo.maven.apache.org/maven2`, `piston-meta.mojang.com`, `libraries.minecraft.net`, `services.gradle.org`, `maven.minecraftforge.net`. Обходы не понадобились. `maven.minecraftforge.net` для ModDevGradle не нужен: плагин лежит на plugin portal и `maven.neoforged.net`.

## Версии

| Что | Версия | Откуда |
|---|---|---|
| NeoForge | 21.1.265 | `maven-metadata.xml`, последняя 21.1.x без квалификатора |
| ModDevGradle | 2.0.148 | `maven-metadata.xml` |
| JUnit | 5.14.4 | Maven Central, последняя 5.x |
| Gradle wrapper | 9.2.1 | как в RYScope, уже в кэше |
| JDK | Temurin 21.0.13 | локально |

## Форматы паков 1.21.1

`version.json` клиента: `pack_version.resource = 34`, `pack_version.data = 48`.

`PackMetadataSection` принимает необязательный `supported_formats`. `Pack.getDeclaredPackVersions` использует его, если `pack_format` входит в диапазон, поэтому один `pack.mcmeta` с `"pack_format": 48, "supported_formats": [34, 48]` совместим с обоими типами.

## Подключение паков

`AddPackFindersEvent` (mod bus): `addRepositorySource(RepositorySource)`, `getPackType()`. Встроенный `addPackFinders` ищет только внутри файла мода, поэтому для папки на диске пак собирается вручную:

- `Pack.readMetaAndCreate(PackLocationInfo, Pack.ResourcesSupplier, PackType, PackSelectionConfig)`;
- `BuiltInPackSource.fromName(info -> new PathPackResources(info, root))`;
- `PackSelectionConfig(required, position, fixedPosition)`, при `required = true` пак выбирается автоматически.

Реализация: `works.repka.rysquests.dev.DevExamplePack`, включается системным свойством `rysquests.dev.examplePack`, которое задаёт только запуск `client` в `build.gradle`. В jar пример не попадает.

## Юнит-тесты

ModDevGradle: `neoForge { unitTest { enable(); testedMod = mods.<id> } }`, блок после `mods`. Классы Minecraft доступны в тестах. Перед чтением метаданных пака нужен `SharedConstants.tryDetectVersion()`.

## Тег из примера

Тега `c:plates/copper` в NeoForge 21.1.265 нет (в `universal.jar` 277 тегов `data/c/tags/item/`, про медь только `ingots/copper`, поле `PLATES` в `Tags.java` отсутствует). Квест `example:power/electric_mechanism` в чистом клиенте сейчас непроходим.

## Что проверено и как

| Проверка | Способ | Результат |
|---|---|---|
| Пак совместим с обоими типами | JUnit | да |
| `PackRepository` выбирает пак, он отдаёт `book.json` и `lang/en_us.json` | JUnit | да |
| Пак добавлен в реальном клиенте (`CLIENT_RESOURCES`) | `run/logs/latest.log` | да |
| Пак добавлен при загрузке мира (`SERVER_DATA`) | `/datapack list` в тестовом мире `run/saves/`, проверил автор | да, `rysquests/dev_example (built-in)` включён |
