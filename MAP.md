# Карта проекта RYSQuests

Собрана `_shared/tools/make-map.py` 2026-11-18. Руками не править: после структурных правок пересобрать.

Java: 405 файлов, 74438 строк. Переводы: `examples/example_pack/assets/example/lang/en_us.json`, `examples/example_pack/assets/example/lang/ru_ru.json`, `src/autotest/guides_pack/assets/design/lang/ru_ru.json`, `src/main/resources/assets/rysquests/lang/en_us.json`.
Читать сначала: `CLAUDE.md`, `README.md`.

## Точки входа
- `@Mod`: `works.repka.rysquests.client.dev.AutoTest` (`src/autotest/java`)
- `@Mod`: `works.repka.rysquests.RYSQuests` (`src/main/java`)
- `@Mod`: `works.repka.rysquests.client.RYSQuestsClient` (`src/main/java`)

## Папки
- `design/` — 22 файлов
- `docs/` — 18 файлов
- `examples/` — 34 файлов; Пример датапака
- `gradle/` — 2 файлов
- `run-a/` — 43 файлов
- `run-b/` — 37 файлов
- `src/` — 610 файлов
- `tools/` — 3 файлов

## `src/autotest/java` — пакет `works.repka.rysquests.client`

**(корень)** (1)
- `ImportShowcase` 36 — What the server says at the end of an FTB import, without importing anything: the report and the failure of the ATM10 run of the "first" scenario, for the screenshots of the wizard…

**dev** (21)
- `AutoTest` 2994 — Drives the real client through a scenario: opens its own world, sends real input, takes screenshots and quits.
- `CanvasBooks` 277 — The two books of the canvas scenario, read on the client without a server: RepYS from its files with the titles of its lang file, and ATM10 carried over from FTB Quests by the impo…
- `CanvasTestScreen` 104 — A bare screen around the canvas for the canvas scenario: the canvas sits where the map area of the mockups is (1280×800 at GUI scale 2), so that screenshots lie over pictures 13 to…
- `ChronicleScenario` 143 — The epoch's reward on the map, the pages "О книге" and the atlases for players (plan _audit/chronicle, B1 and B2; RYSQuests 0.2.7, ADR-60), on the small book of src/autotest/chroni…
- `CollectionScenario` 181 — A collection in the achievements and the limit of pinned cards (RYSQuests 0.2.8, ADR-62), on the small team book of src/autotest/stats_pack: "Сокровища недр" collects eight items (…
- `EditorScenario` 334 — The acceptance of the book's editor (plan step 10), in two runs on the same world: book_editor: a book from nothing, its first chapter typed into the table, quests linked (a circle…
- `ItemLinksScenario` 298 — The book in the recipe viewer's card of an item (RYSCore 1.10 links, plan _audit/ryscope-quests, step 2), on the copy of RepYS with RYScope 0.2.6 or later: the Gate Fragment ("Оско…
- `ItemsScenario` 88 — Items in the book act like items of the recipe viewer (the author's note on 0.2.1): the game's tooltip on hover, the recipe on a click, the usages on the right button; a shop card …
- `MapFixChecks` 191 — The notes of the RepYS server on the map (fb1), on RepYS as a spiral: hovering finds the chapter under the pointer at both ends of every chapter; a click near the edge of a tile ta…
- `NoticesScenario` 298 — The family's notices about finished quests (design _audit/notify, variant A, with the author's place: the game's column of toasts, top right), on the panel book that copies RepYS's…
- `QuestMenuScenario` 82 — The quest's menu "⋯" in the head of its panel (the author's note on 0.2.0): its lines for an operator, "Завершить" completes the quest without its tasks and leaves the reward to ta…
- `RepysScenario` 603 — The new book on a copy of the real pack (run-repys: the mods of RepYS, its kubejs with the book from _audit/book2/repys-data).
- `ShopDeliveryScenario` 236 — A trade paid in parts (shop plan, step M4; RYSQuests 0.2.7).
- `ShopDispenserScenario` 287 — The dispenser "Выдача" (shop plan, step M7; RYSQuests 0.2.7) on a copy of RepYS.
- `ShopEditorScenario` 115 — The editor's shop since 0.2.7 (shop plan, step M9; ADR-58) on the real RepYS book, as an operator: a new offer becomes a delivery of the held iron, its count is typed in thousands,…
- `ShopReceiverScenario` 257 — The intake block "Приёмщик" (shop plan, step M6; RYSQuests 0.2.7) on a copy of RepYS.
- `ShopSchematicScenario` 152 — The shop gives a Create schematic with its file, once per team (shop plan, steps M1 and M2; RYSQuests 0.2.6).
- `ShopTrophyScenario` 230 — A boss trophy opens for the whole team after the first kill of the boss (shop plan, step M3; RYSQuests 0.2.7).
- `StatTeamScenario` 146 — Statistics counted for the whole team and the bar of a pinned level (RYSQuests 0.2.8, ADR-61), on the small team book of src/autotest/stats_pack: "Прыгун" counts jumps (5, 1 000, 1…
- `ThemesScenario` 333 — Plan step 12: every screen of the book in the themes slate, glass and vanilla at the size of the mockups, and once more in the compact layout (a window of 1100×700, the base 480×27…
- `UpgradeSnapshot` 65 — What a player keeps across an update, as the client sees it: quests done and claimed, pins, the wallet, the shop's history and the active title.

**screen** (4)
- `ExtrasShowcase` 277 — The shop, the achievements, the guides and the search with the words and numbers of the mockups (pictures 23 to 31b), for the autotest's screenshots: the example book with offers, …
- `FrameShowcase` 108 — Every shared component of the frame on one page, with the words of the mockups, for the autotest's screenshot and as an example of their use.
- `PanelShowcase` 210 — Hooks of the "panel" autotest into the quest panel, "Now" and the list (plan steps 5 and 6), and a page with three panels side by side as the mockup sheets 20, 21 and 21b lay them …
- `RepysHooks` 117 — Hooks of the "repys" autotest into the open book on the real RepYS book: the shop, the achievements, the guides, the search and the quest panel's main button, as a player reaches t…

## `src/main/java` — пакет `works.repka.rysquests`

**(корень)** (2)
- `CommonConfig` 20 — Settings that the client and the server both read.
- `RYSQuests` 107 — Mod entry point shared by both physical sides.

**api** (8)
- `QuestCompletedEvent` 32 — Fired on the game bus when all tasks of a quest are done.
- `QuestRegistries` 18 — Keys of the registries other mods add task and reward types to.
- `QuestsApi` 69 — What other mods and scripts can do with quests.
- `RewardType` 10 — A kind of quest reward, selected by the type field of its JSON.
- `ScriptHooks` 38 — How a scripting mod answers for script tasks and rewards.
- `StageProvider` 14 — A source of progression stages.
- `TaskType` 10 — A kind of quest task, selected by the type field of its JSON.
- `WalletApi` 61 — Coins for other mods and scripts, on the server only.

**block** (21)
- `BlockNotices` 59 — The server's side of "your block stopped" (decision on question 17 of the shop plan): the blocks tell it every change of their state, it lets the notices out through a NoticeGate o…
- `ComparatorMode` 35 — What a comparator next to a shop block reads (shop plan, step M7), chosen in the block's window.
- `Dispenser` 396 — What a dispenser knows and does (shop plan, section 4.2), with no world in it, so that the tests can drive it: its owner and settings, its own slot, the counters since they were la…
- `DispenserBlock` 102 — The dispenser "Выдача" (shop plan, section 4.2): buys the trade chosen in its window for the owner's coins or the team's treasury and puts the goods into the inventory on its outpu…
- `DispenserBlockEntity` 250 — The dispenser in the world: holds a Dispenser, buys once a second on its own offset, finds the inventory on its output side, gives pipes its own slot to take from, keeps the lamp, …
- `DispenserMenu` 101 — The window of a dispenser: no slots, only what the block shows (DispenserView) and the settings the player changes.
- `DispenserSettings` 99 — What the owner sets in a dispenser's window (shop plan, section 4.2): the trade, where the coins come from, where the goods go, the three limits, the redstone switch, what a compar…
- `DispenserView` 56 — What the window of a dispenser shows, as the server sees it: sent when the window opens and again while it is open, only when something changed (shop plan, section 4.6).
- `ModBlocks` 100 — The mod's blocks (shop plan, steps M6 and M7): the intake block "Приёмщик" and the dispenser "Выдача", their items, block entities and windows.
- `NoticeGate` 115 — When the owner of a shop block hears that it stopped (decision on question 17 of the shop plan), with no world in it, so that the tests can drive it.
- `Receiver` 356 — What an intake block knows and does (shop plan, section 4.1), with no world in it, so that the tests can drive it: its owner, its program, trade and wallet, the buffer of items sho…
- `ReceiverAccess` 52 — Who may do what with an intake block (shop plan, section 4.4).
- `ReceiverBlock` 106 — The intake block "Приёмщик" (shop plan, section 4.1): takes items from any side and sells them by the trade chosen in its window, or gives them to a delivery of the owner's team.
- `ReceiverBlockEntity` 305 — The intake block in the world: holds a Receiver, gives pipes an inventory that only takes, schedules one tick a second after items came in, keeps the lamp in step with the status a…
- `ReceiverLamp` 27 — The colour of the lamp on the intake block (shop plan, section 4.3): the same thing the status line of its window says.
- `ReceiverMarket` 93 — What the shop blocks ask of the shop: its trades and the trade's core for an owner who may be offline.
- `ReceiverMenu` 101 — The window of an intake block: no slots, only what the block shows (ReceiverView) and the settings the player changes.
- `ReceiverProgram` 63 — The mode of an intake block (shop plan, sections 4 and 4.7): which trades it works with, what it takes from a pipe and what it does once a second.
- `ReceiverPrograms` 198 — The two modes of the intake block today (shop plan, section 4.1): "Продажа" and "Поставка".
- `ReceiverStatus` 98 — What a shop block is doing (shop plan, section 4.3), shown three ways that always agree: the lamp of the block, the status line of its window and the comparator.
- `ReceiverView` 67 — What the window of an intake block shows, as the server sees it: sent when the window opens and again while it is open, only when something changed (shop plan, section 4.6).

**client** (13)
- `ClientAchievements` 41 — What the server says about achievements beyond their own progress.
- `ClientAssets` 114 — Puts the translations and pictures that came with the book on top of the client's own resources (ADR-35).
- `ClientBook` 120 — The client's copy of the book.
- `ClientConfig` 68 — Client settings.
- `ClientDev` 24 — The last book report sent by the server to this operator.
- `ClientDrafts` 74 — Where this client keeps the quest editor's unsaved work: rysquests/drafts// in the game folder, so that drafts of one server never turn up on another.
- `ClientImport` 107 — The client's side of the FTB Quests import wizard (plan, pictures 06, 07, 50): it starts the import and keeps what the server said last, for the wizard to draw.
- `ClientLayout` 98 — The baked map on the client.
- `ClientMessages` 52 — The server's latest word about what the player did (plan, picture 48).
- `ClientProgress` 179 — The client's copy of its own progress.
- `ClientShop` 69 — The client's copy of its money.
- `QuestLinks` 61 — Turns [[quest:id]] in chat into a link that opens the book on that quest.
- `RYSQuestsClient` 142 — Client-only entry point; never loaded on a dedicated server.

**client.canvas** (4)
- `Camera` 117 — Where the canvas looks: the canvas point in the middle of the view and the scale in mockup pixels per canvas unit.
- `MapCanvas` 1971 — The canvas of the map ("Полотно"): a book laid out in one form, drawn in a rectangle with a camera over it.
- `MapLayouts` 198 — Where the client gets a book's canvas, by the plan's order: the layout that came with the book when it fits (layout.json), else one laid out before and kept by the book's layout ha…
- `NodeGrid` 123 — Nodes of a layout in square cells, to find the node under the pointer and count nodes under a label without a scan.

**client.hud** (7)
- `HudLook` 50 — What the cards over the world share (mockup .hud, .toast, .kbd): the plate, the key cap, the colors.
- `KeyHint` 64 — The key of the book on the very first entry (picture 31b): where the stack will stand, once, and gone after the book was first opened (ClientConfig.KEY_HINT_SHOWN, kept on this com…
- `QuestNotices` 354 — Messages about finished quests through the family's notices (RYSCore Notice, design _audit/notify, variant A): they stand in the game's own column of toasts, top right.
- `QuestSticker` 186 — A pinned quest (mockup .hud, picture 31): its icon and title, which wraps to a second line rather than being cut (nothing can be hovered over the world), and a row per task with it…
- `Sticker` 24 — One card over the world, in mockup pixels.
- `StickerBoard` 158 — Everything the book draws over the game, in one place (plan, section 3, pictures 31, 31b): one stack of pinned cards in the corner Anchors gives.
- `WaySticker` 46 — A pinned way of a matrix section: what it is and where in the section it comes from.

**client.input** (1)
- `QuestKeys` 61 — Key bindings of the mod: K, P and B.

**client.integration** (8)
- `BookLinks` 179 — The book in the recipe viewer's card of an item (RYSCore 1.10 links.ItemLinks, plan _audit/ryscope-quests, step 2): the shop's offers as a category "Магазин" (an offer that gives t…
- `ItemLinkIndex` 225 — Where an item stands in one book, for the recipe viewer's item links (RYSCore links.ItemLinks, plan _audit/ryscope-quests): the shop offers that give it or take it, the quests that…
- `JeiQuestPlugin` 150 — Found by JEI itself, so this class is never loaded without JEI.
- `Multiblocks` 43 — Blocks of a structure, and its hologram in the world, which only Patchouli can draw.
- `PatchouliBridge` 38 — The only class that touches Patchouli.
- `PonderBridge` 26 — The only class that touches Ponder.
- `Ponders` 55 — Scenes of Ponder, the mod Create brings along.
- `Recipes` 68 — The recipe viewer, if any.

**client.layout** (12)
- `CanvasLayout` 570 — The whole canvas of a book in one form: where every quest stands, the shapes of chapters, the orbits of wheels, the marks and labels of epochs and the trunk's turns.
- `ChapterLayout` 386 — The automatic layout of a chapter on the map (ADR-44).
- `FormFacts` 20 — What the galaxy form makes of a book, for the words of the form preview (picture 07): "4 основные главы и 63 главы по отдельным модам ...
- `Forms` 880 — The four forms of the canvas, ported from the approved Python layouts: serpentine: the story as one band laid in rows that turn, cut at epoch boundaries (quests-layouts-fable, auth…
- `LayeredLayout` 140 — Layered graph layout (a simplified Sugiyama): layers by longest path, crossings reduced by barycenters over four sweeps.
- `LayoutBaker` 21 — Lays the map of a whole book out: the hook the map fills in with setBaker.
- `LayoutEngine` 255 — Lays books out on the canvas, off the main thread.
- `MapBook` 231 — What the canvas is laid out from: the chapters on the map in book order and their live quests, by index.
- `Packer` 170 — Packs wheels along a band, the one step the serpentine, the spiral and the galaxy share (pack of the Python layouts).
- `Polygons` 50 — Whether two simple polygons overlap: an edge of one crosses an edge of the other, or one holds a point of the other.
- `Track` 212 — The centre line of a band that wheels are packed along: at gives the point at length s along it, moved by o across it.
- `Wheels` 417 — Wheels of one chapter, the first step of every form.

**client.markup** (2)
- `GuideTopics` 64 — What a guide page is about, gathered from its text: nobody has to list it by hand.
- `Markup` 158 — Parses quest text: [[kind:target|label]] links as in RYScope, **bold**, __italic__, !!warning!!

**client.screen** (91)
- `AboutView` 131 — The pages "О книге" (mockup _audit/chronicle/01_about_brand, ADR-60): opened by the book's name in the header as a page over the open tab, the way the settings open.
- `AchievementEditorView` 398 — The editor's "Достижения" (picture 39b): the player's grid by category with "+ Достижение" last; on the right the chosen one: icon, name, "Секретное", description, the one conditio…
- `AchievementRing` 35 — The ring around an achievement's icon (mockup .medal, picture 28): 3 wide in the color of the highest level reached, grey while none is; a secret achievement has no icon yet.
- `AchievementsView` 905 — Achievements (plan, section 3, pictures 28, 45, 46d), laid out as the author's sample: categories and the totals on the left, cards in the middle, the selected one with its levels …
- `BlockSettings` 88 — What both shop block windows share (shop plan, step M7): a row with a switch and two lines of words (mockup 06, the limits), and the block's own settings at the bottom of the right…
- `BlockStopNotice` 59 — "Your block stopped" as one of the family's notices (decision on question 17 of the shop plan): the block's icon, what stopped it, the trade and where it stands.
- `BookData` 110 — Lookups into the client's book that every page of the book shares.
- `BookHeader` 371 — The header of the book (mockup .head, pictures 03, 08, 11, 32, 46): the book's icon and name ("Квесты" and the mod's icon when the book names none), the tabs with the badge on "Now…
- `BookMessageLine` 147 — The book's message line (picture 48, plan section 3): the server's answer or refusal as one line over the bottom edge of the page, with a red border, its words, its actions and a c…
- `BookRole` 42 — Who looks at the book.
- `BookScreen` 1132 — The quest book window (plan, sections 2 and 7): one frame for everything.
- `BookState` 291 — What the book shows.
- `BookTabs` 133 — The tabs of the book, in the order of the header: Now, Map, Guides, Shop, Achievements (plan, section 2).
- `BookTallies` 214 — The numbers the frame and the sidebar show: rewards waiting (the badge of "Now"), quests ready to hand in, and per chapter its main quests done and the chapter's state.
- `BookView` 75 — Something the book shows in its window: the content of a tab, a page such as the settings, a first-run step.
- `CardsView` 351 — The cards template, from design/Cards.dc.html: a grid with filters and the selected card on the right.
- `ChapterForm` 165 — The form of a chapter (picture 35): its place in the book, name and icon, subtitle, epoch, "Показывать на карте" (off, it goes to "Вне карты"), "Скрыта, пока не открыта", and what …
- `ChapterOrder` 50 — Orders a chapter's quests so that each comes after the ones it depends on; ties keep book order.
- `CheckView` 519 — "Проверка" (pictures 37, 37b): on the left the pages "Что исправить", "Где команды застревают", "Архив" and the book in numbers, "Раскладка карты устарела" with "Сохранить раскладк…
- `Chips` 45 — The row of filter chips the section screens share.
- `ClosedBookLine` 50 — "Книга закрыта для игроков" (picture 08, plan section 4): a narrow line under the header while the book is closed, for operators only.
- `DetailPanel` 223 — The detail panel on the right: a quest, a chapter, an offer, an achievement, an issue of the check (mockup .panel, pictures 11, 16, 17, 25, 28).
- `DispenserScreen` 644 — The window of the dispenser (shop plan, section 4.2, mockup 06): on the left the trades a dispenser can buy, with a search; on the right where the coins come from, where the goods …
- `EdText` 171 — Tasks, rewards and types of quests in the editor's plain words (pictures 33, 34, 35b): "Получить: Медная катушка ×1", "25 опыта".
- `EdUi` 562 — The small pieces the editor's pages share, at the sizes of the mockups (ui.css): an input (.inp), a field with its label (.fld), buttons (.btn, .btn.sm, .tb), the dashed "+" button…
- `EditorBook` 687 — The editor's draft on this client (plan, section 6): one for the book, whatever editor page is open.
- `EditorChecks` 242 — What "Проверка" finds in the draft (pictures 37, 37b), in the author's words, each with ways to fix it: errors a player runs into (a link to a page that is not there, a quest waiti…
- `EditorMapView` 708 — The editor's "Карта" (pictures 33, 33c, 34, 35, 35b, 09, 46e): the sidebar, and in the middle either the canvas of the draft or the chapter as a table, with the quest's or the chap…
- `EditorPage` 118 — A page of the editor: a view of the book whose unsaved work is the editor's draft (EditorBook), so that the header shows "Сохранить · N", undo and "Открыть игрокам" over it (pictur…
- `EditorPages` 207 — The editor inside the book (plan, section 6): one mode with the author's tabs Карта · Гайды · Магазин · Достижения · Вкладки · Проверка, entered and left by the one "Редактор" togg…
- `EditorSession` 23 — The editor's unsaved work, as the header shows it in editor mode (pictures 09, 10, 33): "Редактор" pressed, "Открыть игрокам" while the book is closed, undo, and the one amber butt…
- `EditorSidebar` 394 — The editor's contents on the left (pictures 33, 35, 09; mockup sidebar(editor=True)): the same rows as the player's Sidebar at its sizes, but over the draft and with what the autho…
- `EditorTesting` 334 — Hooks of the in-game autotest into the editor of the book (EditorPages): what a test needs to drive it without hunting for pixels.
- `EmptyBookView` 207 — A book without quests (pictures 03, 04, 05, plan section 2): one title everywhere, "Квестов пока нет", and a line for the role.
- `EmptyFilter` 42 — A filter left nothing (picture 25d, plan section 2): where the grid or list was, one line of text and a link that brings "All" back.
- `EpochCards` 261 — The reward of an epoch on the map (mockup _audit/chronicle/04_epoch_reward.png, ADR-60): the epoch's plate and its row in the sidebar carry a mark while the reward waits, a click o…
- `Epochs` 108 — The book as eras for the timeline: chapters that share an epoch, and each chapter without one on its own.
- `FilterChips` 55 — A row of filter chips beside a page's first line (mockup .chip, pictures 23, 25, 27b, 28): text chips 28 high, the chosen one on the plate of the active tab.
- `FirstRun` 304 — What the first-run pages say, worked out without drawing (plan, sections 4 and 5): the rows of the import report (picture 06), the reason of each map form and of the advice (07), t…
- `FirstRunHooks` 118 — The in-game autotest's handles on the first-run pages (pictures 06, 07, 09a, 10, 50).
- `FirstRunParts` 387 — The pieces the first-run pages share, from the mockups' ui.css: the step marks of the import (.stepn), report rows (.lrow), rubrics, notes, tags, big buttons and the bar of actions…
- `GridCard` 260 — A card of a grid: a shop offer, an achievement, a guide, a map form (mockup .gc, pictures 25, 28).
- `GuideCheck` 144 — Checks the part of the guides only a client can see: translations, pictures and the links inside the text.
- `GuideDraft` 199 — A guide or a section of pages while it is being edited: the texts themselves, next to the keys they are saved under.
- `GuideEditorView` 439 — The editor's "Гайды" (picture 38): on the left the guide with its pages and "+ Страница" (or the list of guides), in the middle the page's title and its text with markup in a TextA…
- `GuideLibraryView` 281 — The library of guides (plan, section 3, picture 23): to the right of the topics, a line that says what the guides are with the filters "Все | Непрочитанные | Новые", the guide to c…
- `GuidePageView` 556 — A guide from the inside (plan, section 3, picture 24): on the left the way back, the guide and its pages; in the middle one column of reading with its insets (a recipe, a showing i…
- `Guides` 198 — What the guide screens need to know about guides.
- `GuidesTab` 253 — The "Guides" tab (plan, section 3, pictures 23, 24): on the left the topics of the library and, as a group of their own, the author's tabs (cards, pages and matrices); beside them …
- `ImportWizardView` 344 — The FTB Quests import wizard (plan, section 5): two steps, "Перенос" and "Вид карты", in a bare header that names the window.
- `LevelSegments` 50 — The levels of an achievement as segments (mockup .lv, picture 28): one segment per level, the current one filled in part.
- `LinkCards` 303 — The book's cards and mentions for the recipe viewer (RYSCore links.ItemLinks, plan _audit/ryscope-quests, mockups 1 to 3): a shop offer and a quest that gives an item as the viewer…
- `ListRow` 302 — A row of a list: icon, title, a line under it, the state on the right (mockup .lrow, pictures 11, 19, 19c).
- `LoadingView` 39 — The book has not come from the server yet (picture 04b, plan section 2): "Книга загружается", not "Квестов пока нет".
- `MapFormPage` 241 — "Вид карты" (picture 36): the four forms laid out on the author's own quests, dots coloured by epoch, a line of reason under each.
- `MapFormStep` 541 — "Какой будет карта?" (pictures 07, 07b): the four forms laid out on the book's own quests by the map's engine (preview), the advised one first and the others in turn, each with a l…
- `MapTab` 135 — The "Map" tab: the Sidebar on the left and, right of it, the map (MapView, the canvas with its tools and the QuestPanel) or the list (QuestListView with its own panel).
- `MapView` 1144 — The map ("Полотно", pictures 13 to 18, 22, 41 to 47, 49): the MapCanvas with the tools over it and the panel on the right.
- `MatrixView` 612 — The matrix template: design/Mining.dc.html, and with compact the grid of design/Atlas.dc.html.
- `NewBookView` 289 — "Новая книга" (picture 09a): the book's name, an icon from the hand, and one question that may go unanswered, "Как устроена сборка?": the answer picks the map form (FirstRun.Answer…
- `NowView` 684 — "Now" (plan, section 3; pictures 11, 12, 12b, 12c, 46b): the next step of the book in a card, rewards to take and quests in work on the left, what can be done in the current chapte…
- `Plural` 38 — Word forms for a number written in full ("2 400 монет", "1 монета"), with the .one, .few, .many keys that counted reads.
- `PublishQuestion` 125 — "Открыть книгу игрокам?" (picture 10, plan section 4): before the book opens, the question says whether the check found errors, how many chapters and quests players will see and in…
- `QuestFacts` 248 — What the pages of the book need to know about a quest and do with it, without drawing anything: tasks done, what it takes and what the player holds of it, its rewards as items, cla…
- `QuestForm` 378 — The form of a quest on the right of the editor's map (pictures 33, 34, 33c; compact 46e): name and icon, the type as one switch "Основной | Побочный | Веха", "+ Следующий", "+ Побо…
- `QuestListView` 943 — The list mode of the "Map" tab (plan, section 3, "List"; pictures 19, 19b, 19c): the same sidebar, "Карта | Список" above, and the level the sidebar chose.
- `QuestPanel` 1475 — The quest panel (plan, section 3, "Quest"; pictures 17, 20, 21, 21b, 22): one panel for the map, the list and "Now".
- `QuestTable` 696 — A chapter as a table (pictures 35b and 09): a row per quest, columns "Квест, Тип, Что сделать, Награда, После", side quests right under their main one.
- `ReceiverScreen` 579 — The window of the intake block (shop plan, section 4.1, mockups 04 and 05): on the left the trades of its mode with a search, on the right the mode, where the coins go, the trade i…
- `RecipeLayoutView` 46 — A recipe as the viewer itself draws it, machines and their animations included, fitted to a width of the book.
- `RecipePreview` 152 — The recipe of a quest's item, for the small preview in the book.
- `RewardChip` 133 — A reward as a chip (mockup .rw, picture 21): an item with its count and name, experience ("300 опыта", the number green), coins ("● 4 монеты", the coin in its own token, never the …
- `Scroll` 44 — A list's scroll position in mockup pixels, on top of the family's smooth Scroller.
- `SearchIndex` 351 — Search over the whole book: quest names, the items of their tasks and rewards, and chapter names.
- `SearchPopover` 500 — The window of results under the search field (plan, section 3, pictures 30, 30b): groups of quests, items, guides, offers and achievements, three rows each and "ещё N" for the rest…
- `Segments` 105 — A segmented switch: "Map | List", "Buy | Sell | History", "Slate | Glass | Vanilla".
- `SettingsView` 188 — The book's settings as a page of their own (picture 32, plan section 3): one column, no sidebar, "Назад: Сейчас" above.
- `ShopEditorView` 713 — The editor's "Магазин" (picture 39): the offers as the player's grid with "+ Товар" last, "Купить | Продать" and the same "Раздел" as the player has; on the right the chosen offer:…
- `ShopView` 1552 — The shop (plan, section 3, pictures 25 to 27b, 46c).
- `SideList` 80 — The plain left column of a page that is not the map (mockup .side, pictures 23, 24, 28): a rubric on top, rows with a count, group rubrics, a footer.
- `Sidebar` 576 — The book's contents on the left (mockup .side, pictures 13, 14, 14b, 19, 42, 43): "Вся книга" with the count of main quests, epochs that fold, each with its chapters, numbers, a ch…
- `StructureBlock` 195 — A build on a page, from the structure block of design/GuidePage.dc.html: turned with the mouse, shown layer by layer.
- `StubPage` 20 — The body of a page that another screen fills later: its title, one line saying so, and "Назад".
- `TabsEditorView` 488 — The editor's "Вкладки" (picture 40): the sidebar lists the book's tabs as players see them and the author's tabs inside "Гайды", the matrices "Атлас сборки" and "Атлас добычи" amon…
- `TaskPicker` 457 — "Что нужно сделать?" (picture 34): the kinds of tasks in plain words on the left, the form of the chosen kind on the right with the line "Игрок увидит: …", and "Добавить задачу".
- `TaskRow` 223 — A task of a quest in the detail panel (mockup .task, picture 20): the task's action as its title ("Получить: Электромеханизм"), one line of state under it, the progress written onc…
- `TaskText` 93 — How tasks read and look on the screen and on the board.
- `TextArea` 389 — An editable text in the interface's own font, in one line or wrapped.
- `TextBlock` 398 — Lays out parsed quest text with word wrap, inline item icons, clickable links and spoilers.
- `Ui` 835 — One frame of drawing.
- `WebLinks` 49 — Opens a web address from a page of the book as the game's chat opens one: not at all when the player turned links off in the chat settings, through the game's own "Открыть ссылку?"…

**command** (1)
- `QuestCommands` 535 — The /rysquests command tree.

**data** (34)
- `Achievement` 132 — An achievement: one file in rysquests/achievements/.
- `BookAssets` 160 — The translations and pictures of the editor's folder that the book refers to.
- `BookHash` 108 — SHA-256 of the canonical JSON of the linked book: sorted entries, sorted object keys.
- `BookHolder` 37 — The book currently loaded on the server, replaced as a whole on each reload.
- `BookLayout` 101 — The baked map of the book, rysquests/layout.json beside book.json.
- `BookLoader` 105 — Loads the book from datapacks.
- `BookMeta` 191 — Book-wide settings, from book.json.
- `BookParser` 205 — Turns the files of a datapack into a RawBook.
- `BookReport` 29 — What validation found.
- `BookSerializer` 42 — Converts a book to and from the gzipped NBT sent to clients.
- `BookValidator` 514 — Links a parsed book and reports what is wrong.
- `CardsSection` 99 — The "cards" template: a catalog with filters.
- `Chapter` 82 — Metadata of a chapter, from its _chapter.json.
- `Guide` 53 — A guide: a title, an icon and pages of text.
- `GuideRef` 32 — A link to a guide, or to one of its pages: example:furnace or example:furnace#fuel.
- `Layout` 31 — Where a quest stands.
- `MapForm` 31 — The shape of the book's map.
- `MapForms` 106 — The rule of the automatic map form (plan, section 5), in the order the author set: most chapters on the map have an epoch: serpentine; few links between chapters and at least {@val…
- `MatrixSection` 201 — The "matrix" template: rows by columns, with ways of doing something in each cell.
- `PagesSection` 50 — The "pages" template: a book of pages in the format of guides, without a library.
- `ProgressMode` 25 — Who shares quest progress.
- `Quest` 48 — One quest.
- `QuestBook` 166 — The immutable, linked book with its indexes.
- `QuestOptions` 82 — Optional quest fields, kept apart because a record codec takes at most 16 fields.
- `RawBook` 16 — A book as parsed from files, before linking.
- `RequiresMode` 41 — How many dependencies must be complete: all, any, or at least n.
- `RewardDef` 30 — One reward of a quest as written in the book.
- `SectionDef` 50 — A custom section of the book: one file in rysquests/sections/, built from a template.
- `SectionValidator` 157 — Checks custom sections.
- `ShopOffer` 246 — A trade of the shop: one file in rysquests/shop/.
- `StructureDef` 71 — A build shown in 3D on a page: one file in rysquests/structures/, its id taken from the path.
- `TaskDef` 28 — One task of a quest as written in the book.
- `TeamMode` 20 — Who gets a reward in a team: every member, or only the first to claim it.
- `ViewMode` 28 — Which view of the book is open.

**dev** (2)
- `DevExamplePack` 92 — Development-only packs that serve the example books straight from the source tree.
- `DevReports` 300 — Machine-readable reports about the book, for pack authors and for tools.

**editor** (8)
- `BookDraft` 291 — Everything the editor changed in the book and has not saved yet (plan, section 6): whole chapters with their quests, the book's settings, shop offers, achievements, guides, the aut…
- `EditorDrafts` 145 — Unsaved work of the quest editor, kept on disk between sessions so that closing the game loses nothing (plan, section 6).
- `EditorFiles` 505 — Writes what the editor saves into a folder laid out like a pack.
- `EditorFolder` 54 — The folder the editor writes to.
- `EditorService` 391 — Saves what an author made in the book.
- `OfferEdits` 249 — What the editor's "Магазин" (picture 39, ADR-58) changes in a trade, with no screen in it, so that the tests can check that every edit keeps what it does not touch: the line of the…
- `QuestEdits` 225 — Changes to quests as new immutable values.
- `SectionEdits` 237 — Changes to a section as new immutable values.

**integration.ftbq** (7)
- `FtbqFiles` 218 — File side of the FTB Quests import: reads the FTB folder and writes the generated pack.
- `FtbqImportService` 187 — Runs the FTB Quests import for the book's import wizard and for the command: read, convert, write, reload, and the progress of FTB Quests teams if asked.
- `FtbqImporter` 736 — Converts an FTB Quests book to our datapack layout.
- `FtbqProgress` 135 — Reads FTB Quests team progress (world/ftbquests/*.snbt) and maps it to our quest ids.
- `ImportReport` 86 — What an FTB Quests import did, by kind, for the report page of the import (plan, picture 06): what came across, where it went, and what waits for the author's hand.
- `ImportedPack` 46 — Serves the pack generated by the FTB Quests import as an always-on pack for data and for translations.
- `Snbt` 184 — Reads the relaxed SNBT that FTB Library writes: commas are optional and keys may be bare.

**integration.kubejs** (4)
- `QuestCompletedKubeEvent` 34 — RYSQuestsEvents.completed: all tasks of a quest are done.
- `RYSQuestsKubePlugin` 68 — RYSQuestsEvents for server scripts.
- `ScriptRewardKubeEvent` 30 — RYSQuestsEvents.reward: the player claimed a script reward.
- `ScriptTaskKubeEvent` 40 — RYSQuestsEvents.task: asked about once a second while the quest is open.

**integration.patchouli** (1)
- `PatchouliImporter` 215 — Turns a Patchouli book into guides and structures.

**migrate** (3)
- `Migration` 86 — One step of migrations.json.
- `MigrationEngine` 94 — Applies migrations to copies of the progress, so that a dry run is simply a run whose result is thrown away.
- `MigrationRunner` 91 — Runs migrations against the live store: a backup first, then replacing the store's contents.

**network** (35)
- `AchievementStatePayload` 28 — How rare each tier is on this server, in percent, and the titles this player owns.
- `BlockNoticePayload` 41 — A shop block of the player stopped (decision on question 17 of the shop plan): the client shows it as one of the family's notices.
- `BookEditsSavePayload` 45 — The part of the editor's draft that is not a chapter, a guide or a tab: the book's settings (the map's form chosen on picture 36, the title of a new book), shop offers (picture 39)…
- `BookMessagePayload` 33 — A word from the server about something the player did: a reward that was not given, the limit of pinned quests, what a bulk hand-in did.
- `BookSyncPayload` 24 — The whole book, gzipped.
- `DevReportPayload` 24 — The book report for the Dev tab.
- `DispenserConfigPayload` 29 — The settings a dispenser's window asks for (shop plan, step M7), whole, and whether to reset the counters.
- `DispenserStatePayload` 25 — What the open window of a dispenser shows, sent only while it is open and only when it changed (shop plan, step M7).
- `EditorResultPayload` 37 — How a save went.
- `EditorSavePayload` 39 — What the editor saves: a guide or a section, its texts in the author's language and new pictures.
- `FtbqImportErrorPayload` 35 — The FTB Quests import stopped and wrote nothing (plan, picture 50): the file, the line and what was wrong.
- `FtbqImportProgressPayload` 37 — How far the FTB Quests import has come.
- `FtbqImportReportPayload` 24 — The FTB Quests import is done and the new book is loaded: what it carried over, by kind (plan, picture 06).
- `FtbqImportStartPayload` 34 — An operator starts the FTB Quests import from the book.
- `GuidePagesPayload` 25 — The guide pages this player has read: all of them when full, otherwise only the new ones.
- `LayoutBakeRequestPayload` 28 — The server asks an operator's client to lay the map out and send it back with LayoutSavePayload: the answer to /rysquests layout bake.
- `LayoutSavePayload` 28 — The map an operator's client laid out, to be written as rysquests/layout.json and sent to everyone with the book.
- `MapFormPayload` 27 — An operator sets the form of the book's map (the form choice after the FTB Quests import, picture 07); AUTO leaves it to the rule.
- `ModNetwork` 199 — Registers payloads.
- `NewBookPayload` 32 — An operator makes a book from scratch ("Новая книга", picture 09a): its name, an icon and the form the answer to "Как устроена сборка?" picked.
- `PinWayPayload` 23 — Pins a way of a matrix section to the player's screen, or unpins it.
- `PinnedPayload` 25 — The player's pinned quests and ways of matrix sections, in order.
- `ProgressPayload` 67 — Quest progress for the client: everything when full, otherwise only what changed.
- `PublishPayload` 23 — An operator opens the book to players or closes it again; the server answers with EditorResultPayload.
- `QuestActionPayload` 53 — A player's request.
- `QuestEditorSavePayload` 44 — What the quest editor saves: the quests that changed, one chapter's.
- `ReceiverConfigPayload` 42 — The settings an intake block's window asks for (shop plan, step M6): the mode, the trade, where the coins go, and whether to reset the counters, what a comparator reads and whether…
- `ReceiverStatePayload` 25 — What the open window of an intake block shows, sent only while it is open and only when it changed (shop plan, step M6).
- `SelectTitlePayload` 23 — Picks the title shown beside a player's name.
- `ShopDeliverPayload` 20 — A request to deliver what the inventory holds towards a delivery trade (shop plan, step M4).
- `ShopResultPayload` 30 — How a trade went.
- `ShopStatePayload` 58 — A player's money, what the limits have used up and the latest operations.
- `ShopTradePayload` 25 — A request to make a trade.
- `StageLocksPayload` 29 — Quests and guides closed to the player because a stage is missing, and which of the stages named by custom sections the player has: the client has no way to tell by itself.
- `TeamInfoPayload` 23 — How many players are in this player's team, for the chip in the top bar of the book.

**progress** (20)
- `AchievementService` 119 — Titles and how rare each tier is.
- `BookCounts` 50 — The one count of the book: main quests.
- `CollectionMarks` 75 — The rules of an achievement's collection (ADR-62), shared by the server, the window and the tests.
- `CommandPlaceholders` 30 — Placeholders in reward commands, written as FTB Quests writes them: {p} for the player's name, {x}, {y}, {z} for where they stand, {quest}, {chapter}, {team}, {member_count} and {o…
- `DeliveryLedger` 176 — What has been delivered towards a goal that is paid in parts (shop plan, step M4): per owner (a team, or a player alone), per goal (a delivery trade of the shop, later perhaps a tr…
- `EpochRewards` 81 — The reward of an epoch (ADR-60): a quest the epoch names in book.json, shown and claimed at the epoch on the map rather than as a card of its chapter.
- `Feedback` 48 — Quiet sounds that confirm a reward or a trade, heard only by the player who made it, and the book's message line.
- `MemberStats` 74 — The statistics of the players who share progress, for StatCount: an online player's live ones, an offline player's as they were when the player left.
- `NextStep` 80 — The next step on "Now", one rule for every book (plan, section 3, "Now"): the nearest unfinished main quest of the chapter where the player last completed a quest; when there is no…
- `PinLimit` 40 — The limit of pinned cards (hud.max_pinned of the book), one rule for the server that refuses and the book that greys the pin out beforehand.
- `ProgressRemap` 97 — Progress is kept by the position of a task or a reward.
- `ProgressService` 1256 — Server-side quest rules.
- `ProgressStore` 638 — Quest progress of all teams, saved with the world.
- `QuestLogic` 102 — Status rules shared by the server and the client's display.
- `QuestPath` 217 — The way to a quest that is still closed (plan, section 3, "Closed quest"; pictures 21b, 22): every unfinished quest it still needs, in an order in which each comes after what it ne…
- `QuestProgress` 62 — Progress of one quest: value per task, completion time (0 while open), indexes of claimed rewards, for a repeatable quest the time it becomes available again, and how many times it…
- `QuestStatus` 11 — Where a quest stands for a team.
- `StatCount` 61 — How progress that is shared counts a player statistic (ADR-61).
- `TeamBridge` 112 — Moves quest progress when a team mod, through RYSCore, reports that teams changed.
- `TeamRewards` 136 — Which rewards count as taken for a team member: their own, plus those only one member could take.

**reward** (11)
- `ChoiceReward` 34 — Lets the player pick one of several rewards.
- `CommandReward` 25 — Runs a command for the player claiming the reward.
- `CurrencyReward` 24 — Gives currency.
- `ItemReward` 33 — Gives a stack of an item.
- `LootTableReward` 26 — Rolls a loot table for the player.
- `RandomReward` 42 — Gives one of several rewards at random; weights is parallel to of, empty means equal odds.
- `RewardTypes` 42 — The reward type registry and the built-in reward types.
- `ScriptReward` 22 — What the player gets is up to a script.
- `StageReward` 22 — Grants a progression stage through every stage provider.
- `TitleReward` 22 — Gives a title, which a player can then show beside their name.
- `XpReward` 31 — Gives experience, either in levels or in points.

**shop** (8)
- `ShopBroadcast` 50 — When the money of a team changed by a shop block is sent to its members (shop plan, section 4.6): at most once a second per team, whatever the number of its blocks and their trades…
- `ShopCycles` 87 — Finds chains of trades that end up with more than they started with, the classic way of printing money: buy a stack and sell it back for more.
- `ShopLimits` 46 — Counts trades against limits, which reset at midnight of the server's own clock (ADR-37).
- `ShopService` 373 — Makes trades.
- `TradeCore` 306 — The trade itself, with no player in it (shop plan, step M5): a check that changes nothing and an execution of what the check allowed, over a TradeSource the items are paid from and…
- `TradeSink` 165 — Where a trade puts the items it gives (shop plan, step M5): a player's inventory today, the inventory next to a dispenser block later.
- `TradeSource` 101 — Where a trade takes the items it is paid with (shop plan, step M5): a player's inventory today, the buffer of an intake block or a neighbouring inventory later.
- `Trader` 36 — Who a trade is made for (shop plan, step M5): a player at the book, or the owner of a shop block, who may well be offline.

**stage** (1)
- `Stages` 57 — All stage providers.

**task** (12)
- `AdvancementTask` 23 — Completed when the player earns an advancement.
- `CheckboxTask` 21 — Completed when the player ticks it.
- `CollectionTask` 74 — A collection of an achievement (ADR-62): a list of items, each counted once it has been in the inventory of anyone who shares the progress.
- `FluidTask` 45 — Requires an amount of a fluid in containers the player carries, in millibuckets.
- `ItemRef` 37 — An item id or, written with a leading #, an item tag.
- `ItemTask` 106 — Requires an item, a tag, or any of several of them.
- `KillTask` 30 — Completed after killing a number of entities of one type.
- `ScriptTask` 30 — Progress comes from a script, asked on the same schedule as other polled tasks.
- `StatTask` 31 — Completed when a player statistic reaches a value.
- `TaskTypes` 40 — The task type registry and the built-in task types.
- `VisitTask` 38 — Completed by being somewhere: a dimension, a biome or structure (each may be a #tag), or near a position.
- `XpTask` 68 — Experience, in levels or in points, as FTB Quests' and Heracles' experience tasks.

## `src/test/java` — пакет `works.repka.rysquests`

**(корень)** (1)
- `ModSetupTest` 21 — Placeholder proving that JUnit runs with Minecraft classes on the test classpath.

**block** (4)
- `DispenserTest` 457 — The dispenser without a world (shop plan, step M7): purchases into the own slot and into a neighbour, the three limits ("spend at most", "keep on the account", "keep at most"), the…
- `NoticeGateTest` 124 — "Your block stopped" (decision on question 17 of the shop plan): one notice per change of a block's state, at most one a minute per owner whatever the number of blocks, nothing for…
- `ReceiverTest` 466 — The intake block without a world (shop plan, step M6): what a pipe is told and what really goes in, the sale of whole lots once a second, the buffer's ceiling, a delivery that take…
- `ShopRecipesTest` 74 — The shop blocks ship default vanilla recipes; a modpack replaces them by id, so the ids and the files must stay exactly where the docs say.

**client.integration** (1)
- `ItemLinkIndexTest` 180 — The book's index for the recipe viewer's item links (plan _audit/ryscope-quests, step 2): which offers and quests an item leads to, in which role, and the spoiler rule: only what t…

**client.layout** (7)
- `CanvasLayoutTest` 273 — The canvas layout on made up books and on the real books of RepYS and ATM10: chapters never overlap, labels stay in their chapters, every quest on the map is placed inside its chap…
- `ChapterLayoutTest` 217
- `EmptyChapterLayoutTest` 30 — A book from nothing starts with one empty chapter: in every form it has a chapter's shape and room for its label, not a sliver.
- `LayeredLayoutTest` 101
- `LayoutEngineCancelTest` 45 — The form preview stops the forms nobody waits for once one is chosen ("расчёт остальных остановится").
- `RepysBookDataTest` 246 — The RepYS book made ready for the new book (plan, step 13), read from a copy of its rysquests folder given by -PrepysData: it reads without errors and without warnings the instance…
- `SpiralCentreTest` 104 — Notes of the RepYS server on the spiral: the last epoch winds on to the book's finale in the middle; the marks of the epochs stand in the middle of the gap between chapters; the po…

**client.markup** (1)
- `MarkupTest` 141

**client.screen** (3)
- `ExtrasLogicTest` 43 — The logic under the achievements' segments and the search's "может быть".
- `FirstRunTest` 144 — What the first-run pages say (plan, sections 4 and 5), worked out without the game.
- `FramePartsTest` 37 — The frame's small rules that need no game: numbers, the bold sentence of a message, the color of a level.

**data** (8)
- `BookDataTest` 431
- `BookLayoutTest` 124 — layout.json beside the book, the hash it is checked by, and what players are sent.
- `ChapterSectionTest` 38
- `EpochAboutFormatTest` 165 — The fields of book.json that 0.2.7 adds (ADR-60): an epoch names the quest of its reward, and about names the pages "О книге", a guide of the book or pages of their own.
- `GuideExamplesTest` 73 — docs/GUIDE.md promises that its examples work, so each marked block is read with the codec the game uses.
- `MapFormsTest` 152 — The rule of the automatic map form (plan, section 5), on made up books and on the real books of RepYS and ATM10.
- `RedesignFieldsTest` 154 — The optional fields of the redesigned book: old files read as before, new ones round trip, and nothing is written needlessly.
- `TestBooks` 44 — Books for tests: from JSON written in the test, or from a folder laid out like data//rysquests.

**dev** (1)
- `DevExamplePackTest` 56

**editor** (6)
- `BookDraftTest` 127 — The editor's draft of the whole book (plan, section 6): edits land in it, it lays over the book, and it outlives the game on disk.
- `EditorFilesTest` 139
- `EditorVersionTest` 75 — The version the editor's save is checked by, so that two operators do not overwrite each other, and the drafts on disk.
- `OfferEditsTest` 194 — The editor's shop since 0.2.7 (shop plan, step M9; ADR-58): every edit keeps what it does not touch, a delivery and the line of the closed card are edited by the rules the book che…
- `QuestEditsTest` 143
- `SectionEditsTest` 152

**integration.ftbq** (4)
- `FtbqBrokenFileTest` 144 — A broken file stops the import with its file and line, and the import can go on without it; the new data of an import.
- `FtbqImporterTest` 350
- `FtbqProgressAccess` 14 — Exposes package-private importer helpers to tests in other packages.
- `FtbqProgressTest` 113 — FTB keeps a party's claims as ":": one player taking a reward says nothing about another.

**integration.patchouli** (1)
- `PatchouliImporterTest` 105

**migrate** (1)
- `MigrationEngineTest` 103

**progress** (18)
- `AchievementTest` 72
- `CollectionTest` 198 — A collection in the achievements (ADR-62): its marks are a hidden quest of observe tasks, its levels count them, and a team keeps, shares and passes them on as any quest progress.
- `CommandPlaceholdersTest` 21
- `EpochRewardsTest` 204 — The reward of an epoch on the map (ADR-60): it opens when the epoch is passed, is taken once, and moving between teams in any way never makes it claimable again.
- `FullTasksOpenQuestTest` 115 — RepYS, «Тертиевая эссенция»: the server's record of the quest held its one task done (tasks [1]) but no completion, a record taken over from FTB Quests, which counts tasks of a que…
- `GuidePagesStoreTest` 36
- `PinLimitTest` 68 — The limit of pinned cards, one rule for the server that refuses and the achievements window that greys the pin out beforehand.
- `ProgressStoreLoadTest` 35 — One broken record in the save loses only itself: the rest of the progress loads, and is not written over with nothing.
- `QuestLogicTest` 79
- `QuestPathTest` 140 — The way to a closed quest and the next step on "Now" (plan, section 3).
- `RepeatableRoundTest` 75 — Repeatable quests between rounds (ADR-46): what they opened stays open, and a new round clears every member.
- `ReviewFixesTest` 95 — The fixes the review asked for that live in shared rules: optional quests, craft mode, and progress that follows its task.
- `StatCountTest` 85 — A team's counter of a statistic comes from all its members (ADR-61).
- `TeamDeliveryTest` 248 — What a team delivers towards a trade paid in parts (shop plan, step M4) stays with the team, and no way of moving between teams delivers one item twice: leaving, delivering alone a…
- `TeamLeaveCopyTest` 49 — Leaving a team with a copy of its progress never gives back the rewards the player already took in it.
- `TeamRewardsTest` 40
- `TeamRoundOwedTest` 192 — A repeatable quest in a team (ADR-52): the first member to take all their rewards starts the next round, and what the others have not taken of the finished round stays theirs until…
- `TeamShopLimitsTest` 186 — A trade "once per team" stays bought however players move between teams: leaving, buying alone and coming back never buys it twice for one team (the same kind of hole as the reward…

**shop** (6)
- `BossTrophyTest` 241 — A boss trophy opens for the whole team after the first kill of the boss by any member (shop plan, step M3, the author's decision).
- `DeliveryOfferTest` 107 — A trade paid in parts (shop plan, step M4): its file, what it asks for, how far it has come and how much a delivery takes.
- `ShopComponentsTest` 137 — A trade gives an item with components as it is (a Create schematic with its file), and only what the player gets has them.
- `ShopTest` 113
- `TradeCoreTest` 374 — The trade without a player (shop plan, step M5): an owner who is offline, a treasury spent without the right, no room, a trade made as many times as it can, a delivery, the history…
- `TreasuryRightTest` 81 — The team's treasury is spent only with the team's right to it (ADR-52); putting money in needs none.

**task** (1)
- `XpTaskTest` 33
