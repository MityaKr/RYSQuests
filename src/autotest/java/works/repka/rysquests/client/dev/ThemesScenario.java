package works.repka.rysquests.client.dev;

import static works.repka.rysquests.client.dev.AutoTest.command;
import static works.repka.rysquests.client.dev.AutoTest.expect;
import static works.repka.rysquests.client.dev.AutoTest.mc;
import static works.repka.rysquests.client.dev.AutoTest.moveMouse;
import static works.repka.rysquests.client.dev.AutoTest.openWorld;
import static works.repka.rysquests.client.dev.AutoTest.screenshot;
import static works.repka.rysquests.client.dev.AutoTest.step;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import works.repka.ryscore.theme.ThemeManager;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientConfig;
import works.repka.rysquests.client.ClientMessages;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.client.ImportShowcase;
import works.repka.rysquests.client.hud.StickerBoard;
import works.repka.rysquests.client.layout.MapBook;
import works.repka.rysquests.client.screen.BookScreen;
import works.repka.rysquests.client.screen.EditorTesting;
import works.repka.rysquests.client.screen.ExtrasShowcase;
import works.repka.rysquests.client.screen.FirstRunHooks;
import works.repka.rysquests.client.screen.PanelShowcase;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.ViewMode;
import works.repka.rysquests.network.QuestActionPayload;
import works.repka.rysquests.progress.QuestProgress;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Plan step 12: every screen of the book in the themes slate, glass and vanilla at the size of the mockups, and once more in the
 * compact layout (a window of 1100×700, the base 480×270 at 100 %). The panel book (src/autotest/panel_pack) gives "Now", the list
 * and the quest panel; the showcase of the shop, the achievements, the guides and the search lies over it and is what the editor
 * edits (nothing is saved); the map is the book of RepYS on all its levels; the first run is an empty book, a book from nothing and
 * the import wizard on the report and the failure of an ATM10 import. Screenshots: {@code run/screenshots/rysquests_themes_<screen>_<pass>.png}.
 */
final class ThemesScenario {
    private static final String NS = "panel:";
    private static final List<String> PASSES = List.of("slate", "glass", "vanilla", "compact");

    private record State(String name, Runnable setup, int ticks, BooleanSupplier ready) {
    }

    private static final QuestBook[] REPYS = new QuestBook[1];
    private static final Map<ResourceLocation, QuestProgress> REPYS_PROGRESS = new HashMap<>();
    private static final ResourceLocation[] HERE = new ResourceLocation[1];
    private static Map<ResourceLocation, QuestProgress> panelProgress = Map.of();
    private static Optional<ResourceLocation> panelLast = Optional.empty();
    private static boolean editorItemsMade;

    private ThemesScenario() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.parse(NS + path);
    }

    private static BookScreen book() {
        if (!(mc().screen instanceof BookScreen)) {
            mc().setScreen(new BookScreen());
        }
        return (BookScreen) mc().screen;
    }

    static void build() {
        openWorld();
        expect("the panel book reached the client", () -> ClientBook.current().quests().containsKey(id("current/electric_mechanism")));
        step("match the window of the mockups", () -> {
            mc().getWindow().setWindowed(1280, 800);
            mc().options.guiScale().set(2);
            mc().options.languageCode = "ru_ru";
            mc().getLanguageManager().setSelected("ru_ru");
            mc().resizeDisplay();
            mc().reloadResourcePacks();
        }, 40);
        expect("language applied", () -> mc().getOverlay() == null);
        progress();
        step("read the book of RepYS", () -> {
            try {
                REPYS[0] = CanvasBooks.repys(Path.of(System.getProperty("rysquests.autotest.repysBook")), Path.of(System.getProperty("rysquests.autotest.repysLang")));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            MapBook map = MapBook.of(REPYS[0]);
            HERE[0] = map.quests().stream().map(MapBook.Q::id).filter(quest -> quest.getPath().equals("b03_e2_current/electric_mechanism")).findFirst().orElseThrow();
            Set<ResourceLocation> done = CanvasBooks.repysProgress(REPYS[0], map, HERE[0]);
            done.forEach(quest -> REPYS_PROGRESS.put(quest, new QuestProgress(List.of(), 1L, List.of())));
        }, 1);

        List<State> states = states();
        for (String pass : PASSES) {
            step("pass " + pass, () -> {
                boolean compact = "compact".equals(pass);
                ThemeManager.select("ryscore:" + (compact ? "slate" : pass));
                mc().getWindow().setWindowed(compact ? 1100 : 1280, compact ? 700 : 800);
                mc().resizeDisplay();
                book();
            }, 20);
            expect("the layout of the pass " + pass, () -> BookScreen.compactForTest() == "compact".equals(pass));
            for (State state : states) {
                step(state.name + " (" + pass + ")", () -> {
                    state.setup.run();
                }, state.ticks);
                expect(state.name + " is ready (" + pass + ")", state.ready);
                // the book's message on two screens, to see where it lies; out of the way on the others
                boolean message = List.of("now", "shop_buy", "achievements").contains(state.name);
                step("point away", () -> {
                    moveMouse(1, 1);
                    if (message) {
                        ClientMessages.show("rysquests.reward.error.inventory_full", List.of(), false);
                    } else {
                        ClientMessages.dismiss();
                    }
                }, 3);
                screenshot("themes_" + state.name + "_" + pass);
            }
        }
        step("back to slate and the mockups' window", () -> {
            ThemeManager.select("ryscore:slate");
            mc().getWindow().setWindowed(1280, 800);
            mc().resizeDisplay();
            StickerBoard.clearForTest();
            ClientProgress.pinnedForTest(List.of());
            mc().setScreen(null);
        }, 20);
    }

    /** The progress of the panel scenario: the mockups' book in the middle of epoch II, two rewards waiting, two quests pinned. */
    private static void progress() {
        step("mouse out of the way, every quest reset", () -> {
            moveMouse(1, 1);
            ClientMessages.dismiss();
            command("gamerule sendCommandFeedback false");
            command("clear @s");
            command("xp set @s 0 levels");
            for (ResourceLocation quest : ClientBook.current().quests().keySet()) {
                command("rysquests progress @s reset " + quest);
            }
            for (ResourceLocation quest : ClientProgress.pinned()) {
                PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.PIN, quest, 0, List.of()));
            }
        }, 30);
        step("progress as in the mockups", () -> {
            for (String done : List.of("andesite/andesite_alloy", "andesite/water_wheel", "andesite/shaft", "andesite/andesite_2", "brass/brass_ingot",
                    "brass/brass_casing", "current/tok", "current/copper_wire", "current/copper_wire_2", "current/copper_wire_3", "current/iron_rod",
                    "current/copper_wire_coil", "current/ie_wires", "current/lv_wire_coil", "current/steel_wire_coil", "chronicle/old_mineshaft",
                    "chronicle/villages", "chronicle/nether_expedition", "chronicle/explorer_the_nether", "start/welcome")) {
                command("rysquests progress @s complete " + NS + done);
            }
        }, 30);
        step("open the book, claim what needs no choice", () -> {
            book();
            BookScreen.claimAllForTest();
        }, 30);
        step("two quests wait for their rewards, items in the inventory, pins", () -> {
            command("rysquests progress @s complete " + NS + "oil/plastic");
            command("rysquests progress @s complete " + NS + "current/copper_coil");
            command("give @s minecraft:polished_andesite 64");
            command("give @s minecraft:clock 20");
            command("give @s minecraft:oak_planks 16");
            command("give @s minecraft:iron_ingot 6");
            command("give @s minecraft:andesite 212");
            command("give @s minecraft:copper_ingot 96");
            PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.PIN, id("andesite/automation_64"), 0, List.of()));
            PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.PIN, id("chronicle/all_items"), 0, List.of()));
        }, 40);
        expect("two pins came back", () -> ClientProgress.pinned().size() == 2);
    }

    private static State state(String name, Runnable setup) {
        return new State(name, setup, 10, () -> true);
    }

    private static State state(String name, Runnable setup, int ticks, BooleanSupplier ready) {
        return new State(name, setup, ticks, ready);
    }

    private static List<State> states() {
        Function<String, ResourceLocation> q = ThemesScenario::id;
        List<State> out = new ArrayList<>();
        // --- the panel book ---
        out.add(state("now", () -> {
            book();
            PanelShowcase.closePage();
            PanelShowcase.now(false);
        }));
        out.add(state("list_chapter", () -> PanelShowcase.list(q.apply("current"), 0, "ALL", q.apply("current/copper_coil"), true)));
        out.add(state("list_book", () -> PanelShowcase.list(null, 0, "CAN", q.apply("current/electric_mechanism"), false)));
        out.add(state("panel_tasks", () -> PanelShowcase.sheet(q.apply("chronicle/all_items"), q.apply("chronicle/all_counters"), q.apply("chronicle/all_actions"))));
        out.add(state("panel_rewards", () -> {
            PanelShowcase.sheet(q.apply("chronicle/all_rewards"), q.apply("current/heavy_engineering_block"), q.apply("chronicle/scheme"));
            PanelShowcase.pick(0, 1);
        }));
        out.add(state("settings", () -> {
            PanelShowcase.closePage();
            BookScreen.openTabForTest("now");
            BookScreen.toggleSettingsForTest();
        }));
        // --- the showcase over it: shop, achievements, guides, search, and the editor ---
        out.add(state("shop_buy", () -> {
            book();
            PanelShowcase.closePage();
            ExtrasShowcase.install();
            ExtrasShowcase.shop("token", "all", 2, false, false);
        }, 10, () -> "shop".equals(BookScreen.bodyForTest())));
        out.add(state("shop_sell", () -> ExtrasShowcase.shop("brass", "all", 3, false, false)));
        out.add(state("shop_history", ExtrasShowcase::shopHistory));
        out.add(state("achievements", () -> ExtrasShowcase.achievements("miner", 0)));
        out.add(state("guides", () -> {
            BookScreen.openTabForTest("guides");
            ExtrasShowcase.library(0);
        }));
        out.add(state("guide_page", () -> ExtrasShowcase.guidePage("create", 1)));
        out.add(state("search", () -> {
            BookScreen.openTabForTest("map");
            ExtrasShowcase.search("ящик");
        }, 20, () -> true));
        out.add(state("search_empty", () -> ExtrasShowcase.search("кирак")));
        out.add(state("editor_quest", () -> {
            ExtrasShowcase.search("");
            EditorTesting.open("map");
            EditorTesting.canvas();
            EditorTesting.select(q.apply("current/electric_mechanism"));
            EditorTesting.flyTo(q.apply("current/electric_mechanism"));
        }, 40, EditorTesting::canvasReady));
        out.add(state("editor_add_task", () -> {
            EditorTesting.pickTask(q.apply("current/electric_mechanism"), "get", null, 1, "");
            EditorTesting.pickerQuery("медн");
        }));
        out.add(state("editor_chapter", () -> {
            if (EditorTesting.pickerOpen()) {
                AutoTest.pressKey(GLFW.GLFW_KEY_ESCAPE);
            }
            EditorTesting.chapterForm(q.apply("current"));
        }));
        out.add(state("editor_table", () -> EditorTesting.table(q.apply("current"))));
        out.add(state("editor_map_form", EditorTesting::mapForm, 120, () -> true));
        out.add(state("editor_guide", () -> {
            EditorTesting.open("guides");
            if (!editorItemsMade) {
                EditorTesting.newGuide("Первые шаги", "Срубите **дерево** и сделайте [[item:minecraft:crafting_table]].\n!!Без кирки камень не добыть.!!");
            }
        }, 20, () -> true));
        out.add(state("editor_shop", () -> {
            EditorTesting.open("shop");
            if (!editorItemsMade) {
                EditorTesting.newOffer();
            }
        }, 20, () -> true));
        out.add(state("editor_achievements", () -> {
            EditorTesting.open("achievements");
            if (!editorItemsMade) {
                EditorTesting.newAchievement();
            }
        }, 20, () -> true));
        out.add(state("editor_tabs", () -> {
            EditorTesting.open("tabs");
            if (!editorItemsMade) {
                EditorTesting.newTab("matrix");
            }
            EditorTesting.cell(0, 1);
            editorItemsMade = true;
        }, 20, () -> true));
        out.add(state("editor_check", () -> {
            EditorTesting.open("check");
            EditorTesting.checkIssues();
        }, 30, () -> true));
        out.add(state("editor_stuck", EditorTesting::checkStuck, 30, () -> true));
        // --- the map: the book of RepYS on all its levels ---
        out.add(state("map_book", () -> {
            EditorTesting.leave();
            ExtrasShowcase.restore();
            panelProgress = ClientProgress.snapshotForTest();
            panelLast = ClientProgress.lastChapter();
            ClientBook.overrideForTest(REPYS[0], true);
            ClientProgress.receiveForTest(REPYS_PROGRESS, Optional.of(ResourceLocation.parse("repys:b03_e2_current")));
            BookScreen.openTabForTest("map");
            BookScreen.showForTest(ViewMode.MAP);
            BookScreen.selectForTest(null, null);
            BookScreen.mapFlyForTest(null, 0, null);
        }, 30, BookScreen::mapReadyForTest));
        out.add(state("map_epoch", () -> BookScreen.mapFlyForTest(null, 2, null), 25, () -> true));
        out.add(state("map_chapter", () -> BookScreen.mapFlyForTest(ResourceLocation.parse("repys:b03_e2_current"), 0, null), 25, () -> true));
        out.add(state("map_quest", () -> BookScreen.mapFlyForTest(null, 0, HERE[0]), 25, () -> true));
        out.add(state("map_list", () -> BookScreen.showForTest(ViewMode.LIST), 10, () -> true));
        // --- the first run ---
        out.add(state("first_empty", () -> {
            BookScreen.showForTest(ViewMode.MAP);
            BookScreen.selectForTest(null, null);
            ClientBook.overrideForTest(null, null);
            ClientProgress.receiveForTest(panelProgress, panelLast);
            ClientBook.overrideForTest(QuestBook.EMPTY, true);
            BookScreen.ftbForTest(true);
        }, 10, () -> "empty".equals(BookScreen.bodyForTest())));
        out.add(state("first_new_book", () -> FirstRunHooks.open("new", false)));
        out.add(state("first_import_error", () -> {
            PanelShowcase.closePage();
            ImportShowcase.error();
            FirstRunHooks.open("import", false);
        }));
        out.add(state("first_import_report", () -> {
            PanelShowcase.closePage();
            ClientBook.overrideForTest(null, null);
            BookScreen.ftbForTest(null);
            ImportShowcase.report();
            FirstRunHooks.open("import", false);
        }, 10, () -> "report".equals(FirstRunHooks.wizardState())));
        out.add(state("first_import_forms", FirstRunHooks::wizardNext, 60, FirstRunHooks::previewsDone));
        // --- the HUD over the world ---
        out.add(state("hud", () -> {
            PanelShowcase.closePage();
            ImportShowcase.clear();
            mc().setScreen(null);
            ClientConfig.KEY_HINT_SHOWN.set(true);
            StickerBoard.celebrateForTest(ClientBook.current().quests().get(q.apply("current/copper_coil")));
        }, 10, () -> mc().screen == null));
        return out;
    }
}
