# Пример датапака

Живая документация формата из `docs/02-data-format.md`. Используется в тестах (JUnit читает отсюда) и подключается в `run/` для ручной проверки.

```
examples/
  example_pack/
    pack.mcmeta
    data/example/rysquests/book.json
    data/example/rysquests/migrations.json
    data/example/rysquests/chapters/01_start/_chapter.json
    data/example/rysquests/chapters/01_start/first_tools.json
    data/example/rysquests/chapters/01_start/read_guide.json
    data/example/rysquests/chapters/01_start/first_furnace.json
    data/example/rysquests/chapters/02_power/_chapter.json
    data/example/rysquests/chapters/02_power/wires.json
    data/example/rysquests/chapters/02_power/dynamo.json
    data/example/rysquests/chapters/02_power/electric_mechanism.json
    data/example/rysquests/guides/furnace.json       гайд со всеми вставками, на него ссылается квест first_furnace
    data/example/rysquests/guides/tools.json
    data/example/rysquests/guides/nether.json        закрыт до стадии
    assets/example/textures/guides/furnace.png       картинка для гайда
    data/example/rysquests/sections/ores.json        кастомная вкладка на шаблоне Матрица
    data/example/rysquests/sections/bosses.json      кастомная вкладка на шаблоне Карточки
    data/example/rysquests/sections/rules.json       кастомная вкладка на шаблоне Страницы
    data/example/rysquests/shop/*.json               покупка с лимитом, без лимита, продажа, закрытая до стадии
    assets/example/lang/en_us.json
    assets/example/lang/ru_ru.json
  broken/                      намеренно сломанная книга для теста валидатора
    pack.mcmeta
    data/example_broken/rysquests/chapters/x/_chapter.json
    data/example_broken/rysquests/chapters/x/a.json   (зависит от b)
    data/example_broken/rysquests/chapters/x/b.json   (зависит от a: цикл)
    data/example_broken/rysquests/chapters/x/c.json   (зависит от несуществующего d; предмет не существует)
```

Предметы в примере только ванильные, чтобы датапак работал в чистом `runClient`. Квест `electric_mechanism` в примере использует `minecraft:redstone_block` как замена предмету из сборки.
