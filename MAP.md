# Карта проекта RYSQuests

Собрана `_shared/tools/make-map.py` 2026-11-17. Руками не править: после структурных правок пересобрать.

Java: 202 файлов, 31777 строк. Переводы: `examples/example_pack/assets/example/lang/en_us.json`, `examples/example_pack/assets/example/lang/ru_ru.json`, `src/autotest/guides_pack/assets/design/lang/ru_ru.json`, `src/main/resources/assets/rysquests/lang/en_us.json`.
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
- `run-a/` — 19 файлов
- `run-b/` — 13 файлов
- `src/` — 293 файлов
- `tools/` — 3 файлов

## `src/autotest/java` — пакет `works.repka.rysquests.client.dev`

**(корень)** (1)
- `AutoTest` 1839 — Drives the real client through a scenario: opens its own world, sends real input, takes screenshots and quits.

## `src/main/java` — пакет `works.repka.rysquests`

**(корень)** (2)
- `CommonConfig` 20 — Settings that the client and the server both read.
- `RYSQuests` 104 — Mod entry point shared by both physical sides.

**api** (8)
- `QuestCompletedEvent` 32 — Fired on the game bus when all tasks of a quest are done.
- `QuestRegistries` 18 — Keys of the registries other mods add task and reward types to.
- `QuestsApi` 34 — What other mods and scripts can do with quests.
- `RewardType` 10 — A kind of quest reward, selected by the type field of its JSON.
- `ScriptHooks` 38 — How a scripting mod answers for script tasks and rewards.
- `StageProvider` 14 — A source of progression stages.
- `TaskType` 10 — A kind of quest task, selected by the type field of its JSON.
- `WalletApi` 61 — Coins for other mods and scripts, on the server only.

**client** (9)
- `ClientAchievements` 36 — What the server says about achievements beyond their own progress.
- `ClientAssets` 114 — Puts the translations and pictures that came with the book on top of the client's own resources (ADR-35).
- `ClientBook` 64 — The client's copy of the book.
- `ClientConfig` 18 — Client settings.
- `ClientDev` 24 — The last book report sent by the server to this operator.
- `ClientProgress` 134 — The client's copy of its own progress.
- `ClientShop` 44 — The client's copy of its money.
- `QuestLinks` 61 — Turns [[quest:id]] in chat into a link that opens the book on that quest.
- `RYSQuestsClient` 84 — Client-only entry point; never loaded on a dedicated server.

**client.hud** (5)
- `QuestSticker` 107 — A pinned quest, from design/Hud.dc.html: its tasks and a progress bar.
- `QuestToast` 43 — "Quest complete", a short-lived card on the board, from design/Hud.dc.html.
- `Sticker` 18 — One card on the board over the world, in mockup pixels.
- `StickerBoard` 108 — Cards over the world, from design/Hud.dc.html.
- `WaySticker` 50 — A pinned way of a matrix section: what it is and where in the section it comes from.

**client.input** (1)
- `QuestKeys` 59 — Key bindings of the mod.

**client.integration** (6)
- `JeiQuestPlugin` 150 — Found by JEI itself, so this class is never loaded without JEI.
- `Multiblocks` 43 — Blocks of a structure, and its hologram in the world, which only Patchouli can draw.
- `PatchouliBridge` 38 — The only class that touches Patchouli.
- `PonderBridge` 26 — The only class that touches Ponder.
- `Ponders` 55 — Scenes of Ponder, the mod Create brings along.
- `Recipes` 68 — The recipe viewer, if any.

**client.layout** (3)
- `ChapterLayout` 386 — The automatic layout of a chapter on the map (ADR-44).
- `CollapsedBranches` 68 — Finds finished branches to fold on the map.
- `LayeredLayout` 140 — Layered graph layout (a simplified Sugiyama): layers by longest path, crossings reduced by barycenters over four sweeps.

**client.markup** (2)
- `GuideTopics` 64 — What a guide page is about, gathered from its text: nobody has to list it by hand.
- `Markup` 110 — Parses quest text: [[kind:target|label]] links as in RYScope, **bold**, __italic__, !!warning!!

**client.screen** (35)
- `AchievementsView` 419 — Achievements, from design/Achievements.dc.html.
- `BookScreen` 1582 — The quest book window.
- `BookState` 266 — What the book shows.
- `BookView` 17 — A way of showing the open chapter.
- `CardsView` 351 — The cards template, from design/Cards.dc.html: a grid with filters and the selected card on the right.
- `ChapterOrder` 50 — Orders a chapter's quests so that each comes after the ones it depends on; ties keep book order.
- `Chips` 45 — The row of filter chips the section screens share.
- `DevView` 414 — Book reports and maintenance for operators, from design/Dev.dc.html.
- `EditorView` 852 — Edits a guide or a section of pages: the shell of design/PageEditor.dc.html with a list of pages, the page as text with insert buttons, and a preview on the right, as docs/11-next-…
- `Epochs` 108 — The book as eras for the timeline: chapters that share an epoch, and each chapter without one on its own.
- `FlowView` 449 — The chapter as a ribbon, from design/Comfort.dc.html: finished quests folded into one line, the current quest opened in full, then what comes next.
- `GuideCheck` 138 — Checks the part of the guides only a client can see: translations, pictures and the links inside the text.
- `GuideDraft` 199 — A guide or a section of pages while it is being edited: the texts themselves, next to the keys they are saved under.
- `GuideLibraryView` 362 — The library of guides, from design/Guides.dc.html: cards, and the contents of the selected guide on the right.
- `GuidePageView` 511 — A guide from the inside, from design/GuidePage.dc.html without what ADR-33 removed: the list of pages, the page itself, and a panel that gathers itself from the page's text.
- `Guides` 198 — What the guide screens need to know about guides.
- `HomeView` 354 — "What next", from design/Home.dc.html: recommendations, rewards waiting, the latest news and overall progress.
- `ListView` 348 — The chapter as a plain list, from design/List.dc.html.
- `MapView` 738 — The chapter as a graph, laid out automatically in the background.
- `MatrixView` 612 — The matrix template: design/Mining.dc.html, and with compact the grid of design/Atlas.dc.html.
- `QuestCard` 697 — The quest card beside the map, from design/Main.dc.html, and what the flow shares with it.
- `QuestEditorView` 1967 — Edits a chapter on its map, from design/Editor.dc.html.
- `ReadingColumn` 158 — The right side of the flow: how to do the quest, its recipe, and why what comes next is closed.
- `RecipeLayoutView` 46 — A recipe as the viewer itself draws it, machines and their animations included, fitted to a width of the book.
- `RecipePreview` 152 — The recipe of a quest's item, for the small preview in the book.
- `Scroll` 44 — A list's scroll position in mockup pixels, on top of the family's smooth Scroller.
- `SearchIndex` 142 — Search over the whole book: quest names, the items of their tasks and rewards, and chapter names.
- `SectionEditorView` 1583 — The constructor of custom sections, from design/SectionEditor.dc.html: the rail on the left, the table or cards, properties on the right.
- `ShopView` 615 — The shop, from design/Shop.dc.html.
- `StructureBlock` 195 — A build on a page, from the structure block of design/GuidePage.dc.html: turned with the mouse, shown layer by layer.
- `TaskText` 89 — How tasks read and look on the screen and on the board.
- `TextArea` 389 — An editable text in the interface's own font, in one line or wrapped.
- `TextBlock` 370 — Lays out parsed quest text with word wrap, inline item icons, clickable links and spoilers.
- `TimelineView` 183 — The whole book by eras, from design/Timeline.dc.html: five at a time, the wheel moves through the rest.
- `Ui` 734 — One frame of drawing.

**command** (1)
- `QuestCommands` 495 — The /rysquests command tree.

**data** (31)
- `Achievement` 79 — An achievement: one file in rysquests/achievements/.
- `BookAssets` 128 — The translations and pictures of the editor's folder that the book refers to.
- `BookHash` 49 — SHA-256 of the canonical JSON of the linked book: sorted entries, sorted object keys.
- `BookHolder` 25 — The book currently loaded on the server, replaced as a whole on each reload.
- `BookLoader` 97 — Loads the book from datapacks.
- `BookMeta` 48 — Book-wide settings, from book.json.
- `BookParser` 185 — Turns the files of a datapack into a RawBook.
- `BookReport` 29 — What validation found.
- `BookSerializer` 42 — Converts a book to and from the gzipped NBT sent to clients.
- `BookValidator` 403 — Links a parsed book and reports what is wrong.
- `CardsSection` 99 — The "cards" template: a catalog with filters.
- `Chapter` 57 — Metadata of a chapter, from its _chapter.json.
- `Guide` 53 — A guide: a title, an icon and pages of text.
- `GuideRef` 32 — A link to a guide, or to one of its pages: example:furnace or example:furnace#fuel.
- `Layout` 17 — Manual placement of a quest on the map; empty coordinates leave it to the auto layout.
- `MatrixSection` 201 — The "matrix" template: rows by columns, with ways of doing something in each cell.
- `PagesSection` 50 — The "pages" template: a book of pages in the format of guides, without a library.
- `ProgressMode` 25 — Who shares quest progress.
- `Quest` 48 — One quest.
- `QuestBook` 100 — The immutable, linked book with its indexes.
- `QuestOptions` 36 — Optional quest fields, kept apart because a record codec takes at most 16 fields.
- `RawBook` 10 — A book as parsed from files, before linking.
- `RequiresMode` 41 — How many dependencies must be complete: all, any, or at least n.
- `RewardDef` 30 — One reward of a quest as written in the book.
- `SectionDef` 50 — A custom section of the book: one file in rysquests/sections/, built from a template.
- `SectionValidator` 157 — Checks custom sections.
- `ShopOffer` 119 — A trade of the shop: one file in rysquests/shop/.
- `StructureDef` 71 — A build shown in 3D on a page: one file in rysquests/structures/, its id taken from the path.
- `TaskDef` 28 — One task of a quest as written in the book.
- `TeamMode` 20 — Who gets a reward in a team: every member, or only the first to claim it.
- `ViewMode` 28 — Which view of the book is open.

**dev** (2)
- `DevExamplePack` 92 — Development-only packs that serve the example books straight from the source tree.
- `DevReports` 297 — Machine-readable reports about the book, for pack authors and for tools.

**editor** (5)
- `EditorFiles` 334 — Writes what the editor saves into a folder laid out like a pack.
- `EditorFolder` 54 — The folder the editor writes to.
- `EditorService` 177 — Saves what an author made in the book.
- `QuestEdits` 180 — Changes to quests as new immutable values.
- `SectionEdits` 218 — Changes to a section as new immutable values.

**integration.ftbq** (5)
- `FtbqFiles` 125 — File side of the FTB Quests import: reads the FTB folder and writes the generated pack.
- `FtbqImporter` 617 — Converts an FTB Quests book to our datapack layout.
- `FtbqProgress` 135 — Reads FTB Quests team progress (world/ftbquests/*.snbt) and maps it to our quest ids.
- `ImportedPack` 46 — Serves the pack generated by the FTB Quests import as an always-on pack for data and for translations.
- `Snbt` 158 — Reads the relaxed SNBT that FTB Library writes: commas are optional and keys may be bare.

**integration.kubejs** (4)
- `QuestCompletedKubeEvent` 34 — RYSQuestsEvents.completed: all tasks of a quest are done.
- `RYSQuestsKubePlugin` 66 — RYSQuestsEvents for server scripts.
- `ScriptRewardKubeEvent` 30 — RYSQuestsEvents.reward: the player claimed a script reward.
- `ScriptTaskKubeEvent` 40 — RYSQuestsEvents.task: asked about once a second while the quest is open.

**integration.patchouli** (1)
- `PatchouliImporter` 215 — Turns a Patchouli book into guides and structures.

**migrate** (3)
- `Migration` 86 — One step of migrations.json.
- `MigrationEngine` 94 — Applies migrations to copies of the progress, so that a dry run is simply a run whose result is thrown away.
- `MigrationRunner` 91 — Runs migrations against the live store: a backup first, then replacing the store's contents.

**network** (18)
- `AchievementStatePayload` 28 — How rare each tier is on this server, in percent, and the titles this player owns.
- `BookSyncPayload` 24 — The whole book, gzipped.
- `DevReportPayload` 24 — The book report for the Dev tab.
- `EditorResultPayload` 24 — How a save went.
- `EditorSavePayload` 39 — What the editor saves: a guide or a section, its texts in the author's language and new pictures.
- `GuidePagesPayload` 25 — The guide pages this player has read: all of them when full, otherwise only the new ones.
- `ModNetwork` 127 — Registers payloads.
- `PinWayPayload` 23 — Pins a way of a matrix section to the player's screen, or unpins it.
- `PinnedPayload` 25 — The player's pinned quests and ways of matrix sections, in order.
- `ProgressPayload` 27 — Quest progress for the client: everything when full, otherwise only what changed.
- `QuestActionPayload` 42 — A player's request.
- `QuestEditorSavePayload` 37 — What the quest editor saves: the quests that changed, one chapter's.
- `SelectTitlePayload` 23 — Picks the title shown beside a player's name.
- `ShopResultPayload` 24 — How a trade went.
- `ShopStatePayload` 31 — A player's money, what the limits have used up and the latest operations.
- `ShopTradePayload` 25 — A request to make a trade.
- `StageLocksPayload` 29 — Quests and guides closed to the player because a stage is missing, and which of the stages named by custom sections the player has: the client has no way to tell by itself.
- `TeamInfoPayload` 23 — How many players are in this player's team, for the chip in the top bar of the book.

**progress** (10)
- `AchievementService` 119 — Titles and how rare each tier is.
- `CommandPlaceholders` 30 — Placeholders in reward commands, written as FTB Quests writes them: {p} for the player's name, {x}, {y}, {z} for where they stand, {quest}, {chapter}, {team}, {member_count} and {o…
- `Feedback` 30 — Quiet sounds that confirm a reward or a trade, heard only by the player who made it.
- `ProgressService` 913 — Server-side quest rules.
- `ProgressStore` 347 — Quest progress of all teams, saved with the world.
- `QuestLogic` 40 — Status rules shared by the server and the client's display.
- `QuestProgress` 62 — Progress of one quest: value per task, completion time (0 while open), indexes of claimed rewards, for a repeatable quest the time it becomes available again, and how many times it…
- `QuestStatus` 11 — Where a quest stands for a team.
- `TeamBridge` 86 — Moves quest progress when a team mod, through RYSCore, reports that teams changed.
- `TeamRewards` 44 — Which rewards count as taken for a team member: their own, plus those only one member could take.

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

**shop** (3)
- `ShopCycles` 86 — Finds chains of trades that end up with more than they started with, the classic way of printing money: buy a stack and sell it back for more.
- `ShopLimits` 41 — Counts trades against limits, which reset at midnight of the server's own clock (ADR-37).
- `ShopService` 322 — Makes trades.

**stage** (1)
- `Stages` 57 — All stage providers.

**task** (11)
- `AdvancementTask` 23 — Completed when the player earns an advancement.
- `CheckboxTask` 21 — Completed when the player ticks it.
- `FluidTask` 45 — Requires an amount of a fluid in containers the player carries, in millibuckets.
- `ItemRef` 37 — An item id or, written with a leading #, an item tag.
- `ItemTask` 106 — Requires an item, a tag, or any of several of them.
- `KillTask` 30 — Completed after killing a number of entities of one type.
- `ScriptTask` 30 — Progress comes from a script, asked on the same schedule as other polled tasks.
- `StatTask` 31 — Completed when a player statistic reaches a value.
- `TaskTypes` 38 — The task type registry and the built-in task types.
- `VisitTask` 38 — Completed by being somewhere: a dimension, a biome or structure (each may be a #tag), or near a position.
- `XpTask` 68 — Experience, in levels or in points, as FTB Quests' and Heracles' experience tasks.

## `src/test/java` — пакет `works.repka.rysquests`

**(корень)** (1)
- `ModSetupTest` 21 — Placeholder proving that JUnit runs with Minecraft classes on the test classpath.

**client.layout** (2)
- `ChapterLayoutTest` 217
- `LayeredLayoutTest` 101

**client.markup** (1)
- `MarkupTest` 84

**data** (3)
- `BookDataTest` 431
- `ChapterSectionTest` 38
- `GuideExamplesTest` 73 — docs/GUIDE.md promises that its examples work, so each marked block is read with the codec the game uses.

**dev** (1)
- `DevExamplePackTest` 56

**editor** (3)
- `EditorFilesTest` 139
- `QuestEditsTest` 143
- `SectionEditsTest` 138

**integration.ftbq** (3)
- `FtbqImporterTest` 344
- `FtbqProgressAccess` 14 — Exposes package-private importer helpers to tests in other packages.
- `FtbqProgressTest` 113 — FTB keeps a party's claims as ":": one player taking a reward says nothing about another.

**integration.patchouli** (1)
- `PatchouliImporterTest` 105

**migrate** (1)
- `MigrationEngineTest` 103

**progress** (6)
- `AchievementTest` 72
- `CommandPlaceholdersTest` 21
- `GuidePagesStoreTest` 36
- `QuestLogicTest` 79
- `RepeatableRoundTest` 75 — Repeatable quests between rounds (ADR-46): what they opened stays open, and a new round clears every member.
- `TeamRewardsTest` 40

**shop** (1)
- `ShopTest` 113

**task** (1)
- `XpTaskTest` 33
