package works.repka.rysquests.client.dev;

import static works.repka.rysquests.client.dev.AutoTest.command;
import static works.repka.rysquests.client.dev.AutoTest.expect;
import static works.repka.rysquests.client.dev.AutoTest.fail;
import static works.repka.rysquests.client.dev.AutoTest.mc;
import static works.repka.rysquests.client.dev.AutoTest.moveMouse;
import static works.repka.rysquests.client.dev.AutoTest.openWorld;
import static works.repka.rysquests.client.dev.AutoTest.pressKey;
import static works.repka.rysquests.client.dev.AutoTest.screenshot;
import static works.repka.rysquests.client.dev.AutoTest.step;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import works.repka.ryscore.theme.ThemeManager;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientConfig;
import works.repka.rysquests.client.ClientMessages;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.client.hud.StickerBoard;
import works.repka.rysquests.client.integration.Recipes;
import works.repka.rysquests.client.screen.BookRole;
import works.repka.rysquests.client.screen.BookScreen;
import works.repka.rysquests.client.screen.EditorTesting;
import works.repka.rysquests.client.screen.PanelShowcase;
import works.repka.rysquests.client.screen.RepysHooks;
import works.repka.rysquests.data.BookHolder;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.ViewMode;
import works.repka.rysquests.network.QuestActionPayload;
import works.repka.rysquests.task.ItemTask;

/**
 * The new book on a copy of the real pack (run-repys: the mods of RepYS, its kubejs with the book from _audit/book2/repys-data).
 *
 * <p>{@code repys}: every tab as a player in the middle of epoch II, the map on all its levels with the pack's own icons, the quest
 * panel and its "Как сделать" into RYScope, the shop, the achievements, the guides, the search, the HUD, the themes and the compact
 * layout, then the editor as an operator; times the opening of the book and the frame of the whole map.
 *
 * <p>{@code upgrade_new}: the second half of the update check. The world {@code upgrade_old} filled on RYSQuests 0.1.0 with the old
 * book opens on the new mod: everything the player had must still be there, the old book must look decent without its new fields; then
 * the new book replaces it in the same world and the progress must survive a {@code /reload}.
 */
final class RepysScenario {
    private static final Logger LOGGER = LoggerFactory.getLogger(RepysScenario.class);
    private static final ResourceLocation HERE_CHAPTER = ResourceLocation.parse("repys:b03_e2_current");
    private static final ResourceLocation HERE = ResourceLocation.parse("repys:b03_e2_current/electric_mechanism");
    private static final ResourceLocation OFFER = ResourceLocation.parse("repys:e1_andesite_alloy");
    private static final ResourceLocation ACHIEVEMENT = ResourceLocation.parse("repys:mass_production_brass_mechanism");
    private static final ResourceLocation GUIDE = ResourceLocation.parse("repys:create");
    private static final ResourceLocation PINNED_OLD = ResourceLocation.parse("repys:b00_e1_andesite/basin");

    private static final List<ResourceLocation> WAITING = new ArrayList<>();
    private static final ResourceLocation[] SHORT = new ResourceLocation[1];
    private static final String[] HASH = {""};

    private static boolean timing;
    private static long frames;
    private static long canvasNanos;
    private static long canvasWorst;
    private static long lastFrame;
    private static long frameNanos;
    private static long frameWorst;
    private static long openedAt;

    private RepysScenario() {
    }

    private static void listen() {
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Render.Post event) -> {
            if (!(event.getScreen() instanceof BookScreen)) {
                return;
            }
            long now = System.nanoTime();
            if (openedAt != 0) {
                LOGGER.info("[autotest] first frame of the book {} ms after opening", (now - openedAt) / 1_000_000);
                openedAt = 0;
            }
            if (!timing) {
                lastFrame = 0;
                return;
            }
            var stats = BookScreen.mapCanvasStatsForTest();
            if (stats != null) {
                canvasNanos += stats.nanos();
                canvasWorst = Math.max(canvasWorst, stats.nanos());
            }
            if (lastFrame != 0) {
                frameNanos += now - lastFrame;
                frameWorst = Math.max(frameWorst, now - lastFrame);
                frames++;
            }
            lastFrame = now;
        });
    }

    private static String ms(double nanos) {
        return String.format(Locale.ROOT, "%.2f", nanos / 1e6);
    }

    /** Shoots a state, then times its frames for three seconds. */
    private static void timed(String name, Runnable action) {
        step(name, action, 30);
        screenshot("repys_" + name);
        step("time " + name, () -> {
            frames = 0;
            canvasNanos = 0;
            canvasWorst = 0;
            frameNanos = 0;
            frameWorst = 0;
            timing = true;
        }, 60);
        step("log " + name, () -> {
            timing = false;
            LOGGER.info("[autotest] frame {}: {} fps, frame {} ms (worst {}), map canvas {} ms (worst {}) over {} frames; {}", name,
                    frames == 0 ? 0 : Math.round(1e9 * frames / frameNanos), ms(frames == 0 ? 0 : (double) frameNanos / frames), ms(frameWorst),
                    ms(frames == 0 ? 0 : (double) canvasNanos / frames), ms(canvasWorst), frames, BookScreen.mapStatsForTest());
        }, 1);
    }

    private static void shot(String name, Runnable action, int ticks) {
        step(name, action, ticks);
        step("point away " + name, () -> moveMouse(1, 1), 3);
        screenshot("repys_" + name);
    }

    private static BookScreen book() {
        if (!(mc().screen instanceof BookScreen)) {
            mc().setScreen(new BookScreen());
        }
        return (BookScreen) mc().screen;
    }

    private static void mockupWindow() {
        step("match the window of the mockups", () -> {
            mc().getWindow().setWindowed(1280, 800);
            mc().options.guiScale().set(2);
            mc().options.languageCode = "ru_ru";
            mc().getLanguageManager().setSelected("ru_ru");
            mc().resizeDisplay();
            mc().reloadResourcePacks();
        }, 60);
        expect("language applied", () -> mc().getOverlay() == null);
    }

    private static void logReport(String when) {
        step("report " + when, () -> {
            var loaded = BookHolder.current();
            LOGGER.info("[autotest] {}: book {} chapters, {} quests, {} milestones, layout {}, report {} errors {} warnings", when,
                    ClientBook.current().chapters().size(), ClientBook.current().quests().size(),
                    ClientBook.current().quests().values().stream().filter(quest -> quest.options().milestone()).count(),
                    ClientBook.current().layout().isPresent() ? "baked" : "none", loaded.report().errors(), loaded.report().warnings());
            loaded.report().issues().stream().limit(40).forEach(issue -> LOGGER.info("[autotest] issue {}", issue));
        }, 2);
    }

    // --- repys ---

    static void check() {
        listen();
        openWorld();
        expect("the RepYS book reached the client", () -> ClientBook.current().quests().size() > 1300);
        mockupWindow();
        logReport("repys");
        expect("the baked layout came with the book", () -> ClientBook.current().layout().isPresent());
        expect("no stale layout", () -> BookHolder.current().report().issues().stream().noneMatch(issue -> issue.code().equals("layout_stale")));
        progress();

        step("as a player", () -> {
            BookRole.forceForTest(false);
            ThemeManager.select("ryscore:slate");
            ClientMessages.dismiss();
        }, 2);
        step("measure opening the book", () -> {
            mc().setScreen(null);
            long start = System.nanoTime();
            openedAt = start;
            mc().setScreen(new BookScreen());
            LOGGER.info("[autotest] opening the book took {} ms", (System.nanoTime() - start) / 1_000_000);
        }, 30);
        step("measure opening it again", () -> {
            mc().setScreen(null);
            long start = System.nanoTime();
            openedAt = start;
            mc().setScreen(new BookScreen());
            LOGGER.info("[autotest] opening the book again took {} ms", (System.nanoTime() - start) / 1_000_000);
        }, 30);
        expect("book screen open", () -> mc().screen instanceof BookScreen);
        step("tabs a player gets", () -> LOGGER.info("[autotest] tabs: {}", BookScreen.tabsForTest()), 1);

        shot("now", () -> {
            book();
            PanelShowcase.now(false);
        }, 20);
        // the map on all its levels
        step("open the map", () -> {
            BookScreen.openTabForTest("map");
            BookScreen.showForTest(ViewMode.MAP);
            BookScreen.selectForTest(null, null);
        }, 10);
        expect("the map is laid out", BookScreen::mapReadyForTest);
        timed("map_book", () -> {
            moveMouse(1, 1);
            BookScreen.mapFlyForTest(null, 0, null);
        });
        timed("map_epoch_2", () -> BookScreen.mapFlyForTest(null, 2, null));
        timed("map_epoch_5", () -> BookScreen.mapFlyForTest(null, 5, null));
        timed("map_chapter", () -> BookScreen.mapFlyForTest(HERE_CHAPTER, 0, null));
        timed("map_quest", () -> {
            BookScreen.selectForTest(HERE_CHAPTER, HERE);
            BookScreen.mapFlyForTest(null, 0, HERE);
        });
        step("log you are here", () -> LOGGER.info("[autotest] map here: {}", BookScreen.mapHereForTest()), 1);
        shot("map_book_folded", () -> {
            BookScreen.selectForTest(null, null);
            BookScreen.mapFlyForTest(null, 0, null);
            BookScreen.foldSidebarForTest(true);
        }, 30);
        step("unfold the sidebar", () -> BookScreen.foldSidebarForTest(false), 5);
        shot("map_off_map_chapter", () -> {
            BookScreen.selectForTest(ResourceLocation.parse("repys:a2_chronicle"), null);
            BookScreen.mapFlyForTest(ResourceLocation.parse("repys:a2_chronicle"), 0, null);
        }, 30);
        // the quest panel with the pack's items, and its "Как сделать" into RYScope
        shot("panel_short", () -> {
            BookScreen.selectForTest(ClientBook.current().quests().get(SHORT[0]).chapter().orElseThrow(), SHORT[0]);
            BookScreen.mapFlyForTest(null, 0, SHORT[0]);
        }, 30);
        expect("recipe viewer connected", Recipes::available);
        step("main button of the panel", () -> LOGGER.info("[autotest] main button of {}: {}", SHORT[0], RepysHooks.mainAction(SHORT[0])), 1);
        expect("the main button says how to make it", () -> {
            String label = RepysHooks.mainAction(SHORT[0]);
            return label != null && label.startsWith("Как сделать");
        });
        step("press how to make it", () -> {
            if (!RepysHooks.pressMainAction(SHORT[0])) {
                fail("the main button did nothing");
            }
        }, 30);
        expect("a recipe window over the book", () -> mc().screen != null && !(mc().screen instanceof BookScreen));
        step("log the recipe window", () -> LOGGER.info("[autotest] recipe window: {}", mc().screen.getClass().getName()), 1);
        screenshot("repys_recipe");
        step("leave the recipe window", () -> pressKey(GLFW.GLFW_KEY_ESCAPE), 20);
        expect("back in the book", () -> mc().screen instanceof BookScreen);
        shot("panel_waiting", () -> {
            ResourceLocation quest = WAITING.get(0);
            BookScreen.selectForTest(ClientBook.current().quests().get(quest).chapter().orElseThrow(), quest);
            BookScreen.mapFlyForTest(null, 0, quest);
        }, 30);
        shot("list_chapter", () -> PanelShowcase.list(HERE_CHAPTER, 0, "ALL", HERE, true), 15);
        shot("list_book", () -> PanelShowcase.list(null, 0, "ALL", null, false), 15);
        shot("list_epoch", () -> PanelShowcase.list(null, 2, "ALL", null, false), 15);
        // shop, achievements, guides, search
        shot("shop", () -> RepysHooks.shop(null), 15);
        shot("shop_offer", () -> RepysHooks.shop(OFFER), 10);
        shot("shop_history", RepysHooks::shopHistory, 10);
        shot("achievements", () -> RepysHooks.achievements(null), 15);
        shot("achievement", () -> RepysHooks.achievements(ACHIEVEMENT), 10);
        shot("guides", RepysHooks::library, 15);
        step("open a guide page", () -> {
            if (!RepysHooks.guide(GUIDE, 1)) {
                fail("no guide " + GUIDE);
            }
        }, 30);
        screenshot("repys_guide_page");
        shot("search", () -> {
            BookScreen.openTabForTest("map");
            RepysHooks.search("сталь");
        }, 20);
        shot("search_items", () -> RepysHooks.search("андезит"), 20);
        shot("search_empty", () -> RepysHooks.search("кирак"), 20);
        step("clear the search", () -> RepysHooks.search(""), 5);
        shot("settings", () -> {
            BookScreen.openTabForTest("now");
            BookScreen.toggleSettingsForTest();
        }, 10);
        step("close the settings", BookScreen::toggleSettingsForTest, 5);
        // themes and the compact layout on the map and the panel
        for (String theme : List.of("glass", "vanilla")) {
            shot("map_quest_" + theme, () -> {
                ThemeManager.select("ryscore:" + theme);
                book();
                BookScreen.openTabForTest("map");
                BookScreen.showForTest(ViewMode.MAP);
                BookScreen.selectForTest(HERE_CHAPTER, HERE);
                BookScreen.mapFlyForTest(null, 0, HERE);
            }, 30);
            shot("now_" + theme, () -> PanelShowcase.now(false), 15);
            shot("shop_" + theme, () -> RepysHooks.shop(OFFER), 15);
        }
        step("back to slate, a small window", () -> {
            ThemeManager.select("ryscore:slate");
            mc().getWindow().setWindowed(1100, 700);
            mc().resizeDisplay();
        }, 30);
        expect("the book is compact", BookScreen::compactForTest);
        shot("compact_map", () -> {
            book();
            BookScreen.openTabForTest("map");
            BookScreen.showForTest(ViewMode.MAP);
            BookScreen.selectForTest(HERE_CHAPTER, HERE);
            BookScreen.mapFlyForTest(null, 0, HERE);
        }, 30);
        shot("compact_now", () -> PanelShowcase.now(false), 15);
        step("the mockups' window again", () -> {
            mc().getWindow().setWindowed(1280, 800);
            mc().resizeDisplay();
        }, 30);
        // the HUD over the world
        shot("hud", () -> {
            mc().setScreen(null);
            ClientConfig.KEY_HINT_SHOWN.set(true);
        }, 20);
        shot("hud_done", () -> StickerBoard.celebrateForTest(ClientBook.current().quests().get(WAITING.get(0))), 10);
        step("clear the stickers", StickerBoard::clearForTest, 5);
        // the editor on the big book, as an operator
        step("as an operator", () -> {
            BookRole.forceForTest(null);
            book();
        }, 10);
        step("open the editor on the map", () -> {
            long start = System.nanoTime();
            EditorTesting.open("map");
            EditorTesting.canvas();
            LOGGER.info("[autotest] opening the editor took {} ms", (System.nanoTime() - start) / 1_000_000);
        }, 10);
        expect("the editor's canvas is ready", EditorTesting::canvasReady);
        timed("editor_canvas", () -> {
            EditorTesting.select(HERE);
            EditorTesting.flyTo(HERE);
        });
        shot("editor_table", () -> EditorTesting.table(HERE_CHAPTER), 20);
        shot("editor_chapter", () -> EditorTesting.chapterForm(HERE_CHAPTER), 15);
        shot("editor_check", () -> {
            EditorTesting.open("check");
            EditorTesting.checkIssues();
        }, 40);
        step("log the editor's issues", () -> LOGGER.info("[autotest] editor issues: {}", EditorTesting.issues()), 1);
        shot("editor_shop", () -> EditorTesting.open("shop"), 20);
        shot("editor_guides", () -> EditorTesting.open("guides"), 20);
        step("leave the editor, nothing saved", () -> {
            if (EditorTesting.unsaved() > 0) {
                LOGGER.info("[autotest] editor has {} unsaved changes, left as they are", EditorTesting.unsaved());
            }
            EditorTesting.leave();
        }, 10);
        step("close the book", () -> mc().setScreen(null), 10);
        step("open the inventory for the panel of buttons", () -> pressKey(GLFW.GLFW_KEY_E), 30);
        screenshot("repys_dock");
        step("close the inventory", () -> pressKey(GLFW.GLFW_KEY_ESCAPE), 10);
    }

    /** A player in the middle of epoch II: epoch I done and claimed, part of "Ток" done, two rewards waiting, two pins. */
    private static void progress() {
        step("done: epoch I and the start of epoch II", () -> {
            command("gamerule sendCommandFeedback false");
            Set<ResourceLocation> done = new LinkedHashSet<>();
            for (Quest quest : sortedQuests()) {
                Optional<Chapter> chapter = quest.chapter().map(ClientBook.current().chapters()::get);
                if (chapter.isPresent() && chapter.get().epoch().orElse(0) == 1 && !quest.options().optional()) {
                    done.add(quest.id());
                }
            }
            List<Quest> current = sortedQuests().stream().filter(quest -> quest.chapter().map(HERE_CHAPTER::equals).orElse(false)).toList();
            int added = 0;
            boolean grew = true;
            while (grew && added < 14) {
                grew = false;
                for (Quest quest : current) {
                    if (added >= 14) {
                        break;
                    }
                    if (!done.contains(quest.id()) && !quest.id().equals(HERE) && done.containsAll(quest.requires())) {
                        done.add(quest.id());
                        added++;
                        grew = true;
                    }
                }
            }
            LOGGER.info("[autotest] completing {} quests", done.size());
            done.forEach(id -> command("rysquests progress @s complete " + id));
        }, 80);
        step("claim what needs no choice", () -> {
            book();
            BookScreen.claimAllForTest();
        }, 60);
        step("two more wait for their rewards", () -> {
            mc().setScreen(null);
            List<Quest> open = sortedQuests().stream().filter(quest -> quest.chapter().map(HERE_CHAPTER::equals).orElse(false))
                    .filter(quest -> !ClientProgress.get(quest.id()).completed() && !quest.id().equals(HERE) && !quest.rewards().isEmpty())
                    .filter(quest -> quest.requires().stream().allMatch(id -> ClientProgress.get(id).completed())).limit(2).toList();
            open.forEach(quest -> {
                WAITING.add(quest.id());
                command("rysquests progress @s complete " + quest.id());
            });
            LOGGER.info("[autotest] waiting for their rewards: {}", WAITING);
        }, 40);
        expect("two rewards wait", () -> WAITING.size() == 2 && WAITING.stream().allMatch(id -> ClientProgress.get(id).completed()));
        step("a quest short of items, some of them in the inventory", () -> {
            for (Quest quest : sortedQuests()) {
                if (ClientProgress.get(quest.id()).completed() || !quest.requires().stream().allMatch(id -> ClientProgress.get(id).completed())) {
                    continue;
                }
                if (quest.tasks().size() == 1 && quest.tasks().get(0) instanceof ItemTask task && task.count() >= 4 && task.item().isPresent()
                        && !task.item().get().tag()) {
                    SHORT[0] = quest.id();
                    command("give @s " + task.item().get().id() + " " + task.count() / 2);
                    LOGGER.info("[autotest] short of items: {} ({} of {} {})", quest.id(), task.count() / 2, task.count(), task.item().get().id());
                    return;
                }
            }
            fail("no quest short of items found");
        }, 20);
        step("pin two quests, coins for the shop", () -> {
            for (ResourceLocation quest : ClientProgress.pinned()) {
                PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.PIN, quest, 0, List.of()));
            }
        }, 10);
        step("pin", () -> {
            PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.PIN, HERE, 0, List.of()));
            PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.PIN, SHORT[0], 0, List.of()));
            command("rysquests wallet set @s coin 14");
        }, 20);
        expect("two pins came back", () -> ClientProgress.pinned().size() == 2);
    }

    private static List<Quest> sortedQuests() {
        List<ResourceLocation> order = new ArrayList<>(ClientBook.current().chapters().keySet());
        return ClientBook.current().quests().values().stream()
                .sorted(Comparator.<Quest>comparingInt(quest -> quest.chapter().map(order::indexOf).orElse(order.size()))
                        .thenComparing(quest -> quest.id().toString()))
                .toList();
    }

    // --- upgrade_new ---

    static void upgrade() {
        listen();
        openWorld();
        expect("the old RepYS book reached the client", () -> ClientBook.current().quests().size() > 1000);
        expect("the progress of 0.1.0 reached the client", () -> ClientProgress.get(ResourceLocation.parse("repys:b00_e1_andesite/andesite")).completed());
        mockupWindow();
        logReport("old book on the new mod");
        step("let achievements and the shop settle", () -> {}, 60);
        compare("old book");
        expect("the map lays the old book out by itself", () -> ClientBook.current().layout().isEmpty());
        upgradeScreens("oldbook");
        step("put the new book in place of the old one", RepysScenario::swapBook, 5);
        step("reload the data", () -> {
            HASH[0] = ClientBook.current().hash();
            mc().setScreen(null);
            command("reload");
        }, 100);
        expect("the new book reached the client", () -> !ClientBook.current().hash().equals(HASH[0]));
        step("reload the texts", () -> mc().reloadResourcePacks(), 60);
        expect("texts reloaded", () -> mc().getOverlay() == null);
        logReport("new book after /reload");
        expect("all 73 milestones", () -> ClientBook.current().quests().values().stream().filter(quest -> quest.options().milestone()).count() == 73);
        expect("the baked layout came with it", () -> ClientBook.current().layout().isPresent()
                && BookHolder.current().report().issues().stream().noneMatch(issue -> issue.code().equals("layout_stale")));
        step("let it settle", () -> {}, 40);
        compare("new book");
        upgradeScreens("newbook");
    }

    private static void upgradeScreens(String tag) {
        step("open the book (" + tag + ")", () -> {
            BookRole.forceForTest(false);
            ThemeManager.select("ryscore:slate");
            ClientMessages.dismiss();
            book();
        }, 20);
        shot("upgrade_" + tag + "_now", () -> PanelShowcase.now(false), 20);
        step("open the map (" + tag + ")", () -> {
            BookScreen.openTabForTest("map");
            BookScreen.showForTest(ViewMode.MAP);
            BookScreen.selectForTest(null, null);
        }, 10);
        expect("the map is laid out (" + tag + ")", BookScreen::mapReadyForTest);
        timed("upgrade_" + tag + "_map_book", () -> {
            moveMouse(1, 1);
            BookScreen.mapFlyForTest(null, 0, null);
        });
        shot("upgrade_" + tag + "_map_epoch", () -> BookScreen.mapFlyForTest(null, 1, null), 30);
        shot("upgrade_" + tag + "_map_pinned", () -> {
            BookScreen.selectForTest(ResourceLocation.parse("repys:b00_e1_andesite"), PINNED_OLD);
            BookScreen.mapFlyForTest(null, 0, PINNED_OLD);
        }, 30);
        shot("upgrade_" + tag + "_map_start", () -> {
            BookScreen.selectForTest(ResourceLocation.parse("repys:a01_map"), null);
            BookScreen.mapFlyForTest(ResourceLocation.parse("repys:a01_map"), 0, null);
        }, 30);
        shot("upgrade_" + tag + "_list", () -> PanelShowcase.list(ResourceLocation.parse("repys:b00_e1_andesite"), 0, "ALL", PINNED_OLD, false), 15);
        shot("upgrade_" + tag + "_shop_history", () -> {
            RepysHooks.shop(null);
            RepysHooks.shopHistory();
        }, 15);
        shot("upgrade_" + tag + "_achievement", () -> RepysHooks.achievements(ACHIEVEMENT), 15);
        step("close the book (" + tag + ")", () -> mc().setScreen(null), 10);
        shot("upgrade_" + tag + "_hud", () -> ClientConfig.KEY_HINT_SHOWN.set(true), 10);
    }

    /** Every line of what the player had on 0.1.0 must be there now; anything else is reported. */
    private static void compare(String when) {
        step("compare with 0.1.0 (" + when + ")", () -> {
            List<String> before = UpgradeSnapshot.read("old");
            List<String> now = UpgradeSnapshot.lines();
            Set<String> nowSet = new HashSet<>(now);
            Set<String> beforeSet = new HashSet<>(before);
            List<String> lost = before.stream().filter(line -> !nowSet.contains(line)).toList();
            List<String> added = now.stream().filter(line -> !beforeSet.contains(line)).toList();
            UpgradeSnapshot.write("new-" + when.replace(' ', '-'));
            LOGGER.info("[autotest] upgrade {}: {} lines before, {} now", when, before.size(), now.size());
            lost.forEach(line -> LOGGER.info("[autotest] upgrade {}: lost   {}", when, line));
            added.forEach(line -> LOGGER.info("[autotest] upgrade {}: added  {}", when, line));
            List<String> lostKept = lost.stream().filter(line -> line.startsWith("quest ") || line.startsWith("pinned ") || line.startsWith("shop ")).toList();
            if (!lostKept.isEmpty()) {
                fail("the update lost " + lostKept.size() + " line(s) of progress, first: " + lostKept.get(0));
            }
        }, 2);
    }

    /** Moves the old book of the copy aside (never deleted) and copies the new one in from _audit/book2/repys-data. */
    private static void swapBook() {
        Path game = mc().gameDirectory.toPath();
        Path data = Path.of(System.getProperty("rysquests.autotest.repysData"));
        Path stale = game.resolve("_stale").resolve("upgrade-old-book-" + System.currentTimeMillis());
        try {
            Files.createDirectories(stale.resolve("lang"));
            Files.move(game.resolve("kubejs/data/repys/rysquests"), stale.resolve("rysquests"));
            for (String lang : List.of("ru_ru.json", "en_us.json")) {
                Files.move(game.resolve("kubejs/assets/repys/lang").resolve(lang), stale.resolve("lang").resolve(lang));
                Files.copy(data.resolve("assets/repys/lang").resolve(lang), game.resolve("kubejs/assets/repys/lang").resolve(lang));
            }
            copyTree(data.resolve("rysquests"), game.resolve("kubejs/data/repys/rysquests"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        LOGGER.info("[autotest] the old book went to {}", stale);
    }

    private static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> files = Files.walk(from)) {
            for (Path file : files.toList()) {
                Path target = to.resolve(from.relativize(file).toString());
                if (Files.isDirectory(file)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
