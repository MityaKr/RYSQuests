# RYSQuests

Квест-мод для Minecraft 1.21.1 на NeoForge 21.1.x. Конкурент FTB Quests: книга-данные в JSON со стабильными строковыми id, миграции прогресса, импорт из FTB Quests, карта с автораскладкой, вид «Поток», HUD-стикеры, темы. Семья RYS (RYScope, RYSQuests, RYSPulse, библиотека RYSCore).

- Автор: Dumitru (MityaYS), RepkaWorks
- modid: `rysquests`, пакет `works.repka.rysquests`
- Общая основа семьи: отдельный мод RYSCore (`ryscore`), вложен через jarJar
- Лицензия: решить до публикации (рекомендация: MIT для API, LGPL или All Rights Reserved для остального)

## Эта папка

Пакет для разработки в режиме кода. Код ещё не написан. Порядок чтения для новой сессии:

1. `CLAUDE.md` — правила работы в этом репозитории (читается автоматически).
2. `docs/00-vision.md` — что строим и зачем, кратко.
3. `docs/05-plan.md` — этапы M0–M8 с критериями приёмки. Работа идёт по нему.
4. `docs/01-architecture.md` — модули, потоки данных, клиент и сервер, потоки, сеть.
5. `docs/02-data-format.md` — формат книги, задач, наград, миграций, lang.
6. `docs/03-api.md` — Java API, события, KubeJS.
7. `docs/04-ui.md` — экраны, виджеты, темы, раскладка.
8. `docs/06-constraints.md` — среда, сборка, тестирование, что нельзя.
9. `docs/07-decisions.md` — принятые архитектурные решения (ADR).
10. `docs/08-verify-first.md` — что проверить по чужому коду до написания своего.
11. `docs/09-sections.md` — словарь терминов, разделы книги и кастомные вкладки после MVP: гайды, атлас сборки, атлас добычи, магазин, достижения, конструктор вкладок (в M1 только поле `section`).
12. `docs/10-design-rules.md` — строгость дизайна: токены, оболочки, запреты, как отступать. Обязателен для любого экрана.
13. `examples/` — пример датапака: две главы, миграции, lang.
14. `design/` — макеты и концепт-страница первой версии (эталон визуала, см. `docs/10-design-rules.md`).
15. `START_PROMPT.md` — промпт для первой сессии в режиме кода.

## Структура репозитория после M0

```
RYSQuests/
  CLAUDE.md
  README.md
  START_PROMPT.md
  docs/
  design/
  examples/            пример датапака (копируется в run/ для тестов)
  build.gradle         ModDevGradle
  settings.gradle
  gradle.properties    версии: minecraft, neoforge, mod_version
  gradlew, gradle/
  src/main/java/works/repka/rysquests/...
  src/main/resources/META-INF/neoforge.mods.toml
  src/main/resources/assets/rysquests/...
  src/main/resources/data/rysquests/...
  src/test/java/...    JUnit: кодеки, граф, раскладка, миграции
  run/                 инстанс для runClient (в .gitignore)
  _stale/              вместо удаления
```

## Версионирование

Первая цифра версии меняется только по явной просьбе автора. Остальное: минор для фич, патч для исправлений, даже если ломается совместимость данных (с миграцией).
