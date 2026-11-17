package works.repka.rysquests.client.dev;

import static works.repka.rysquests.client.dev.AutoTest.command;
import static works.repka.rysquests.client.dev.AutoTest.expect;
import static works.repka.rysquests.client.dev.AutoTest.fail;
import static works.repka.rysquests.client.dev.AutoTest.mc;
import static works.repka.rysquests.client.dev.AutoTest.openWorld;
import static works.repka.rysquests.client.dev.AutoTest.pressKey;
import static works.repka.rysquests.client.dev.AutoTest.screenshot;
import static works.repka.rysquests.client.dev.AutoTest.step;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.screen.BookScreen;
import works.repka.rysquests.client.screen.EditorTesting;
import works.repka.rysquests.data.MapForm;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.RewardDef;
import works.repka.rysquests.data.TaskDef;
import works.repka.rysquests.editor.QuestEdits;

/**
 * The acceptance of the book's editor (plan step 10), in two runs on the same world:
 * <ol>
 *     <li>{@code book_editor}: a book from nothing, its first chapter typed into the table, quests linked (a circle refused), the save
 *     and its files, the question on closing with edits, a save refused because the chapter changed meanwhile and saved over, the
 *     other tabs; it ends with an edit left in the draft;</li>
 *     <li>{@code book_editor_restart}: the game started again, the draft is still there, it is saved, and the files are put aside.</li>
 * </ol>
 * Screenshots of every screen of the editor go to {@code run/screenshots/rysquests_editor_*.png}.
 */
final class EditorScenario {
    private static final String NAMESPACE = "pack";

    private EditorScenario() {
    }

    private static Path game() {
        return mc().gameDirectory.toPath();
    }

    private static Path pack() {
        return game().resolve("config/rysquests/pack");
    }

    private static Path chapterFolder(ResourceLocation chapter) {
        return pack().resolve("data/" + chapter.getNamespace() + "/rysquests/chapters/" + chapter.getPath());
    }

    private static RegistryOps<com.google.gson.JsonElement> ops() {
        return RegistryOps.create(JsonOps.INSTANCE, mc().level.registryAccess());
    }

    private static TaskDef task(String json) {
        return TaskDef.CODEC.parse(ops(), JsonParser.parseString(json)).getOrThrow();
    }

    private static RewardDef reward(String json) {
        return RewardDef.CODEC.parse(ops(), JsonParser.parseString(json)).getOrThrow();
    }

    /** What a run leaves in the editor's pack and drafts goes aside into {@code run/_stale}: nothing is deleted. */
    private static void putAside(Path folder, String name) {
        if (!Files.exists(folder)) {
            return;
        }
        try {
            Path target = game().resolve("_stale/" + name + "_" + System.currentTimeMillis());
            Files.createDirectories(target.getParent());
            Files.move(folder, target);
        } catch (IOException e) {
            fail("could not put aside " + folder + ": " + e);
        }
    }

    private static void sizeOfMockups() {
        step("match the window of the mockups", () -> {
            mc().getWindow().setWindowed(1280, 800);
            mc().options.guiScale().set(2);
            mc().options.languageCode = "ru_ru";
            mc().getLanguageManager().setSelected("ru_ru");
            mc().resizeDisplay();
            mc().reloadResourcePacks();
        }, 40);
        expect("language applied", () -> mc().getOverlay() == null);
    }

    static void build(boolean restart) {
        if (restart) {
            buildRestart();
        } else {
            buildFirst();
        }
    }

    private static void buildFirst() {
        ResourceLocation[] chapter = new ResourceLocation[1];
        List<ResourceLocation> quests = new ArrayList<>();
        step("put aside what earlier runs left in the editor's pack and drafts", () -> {
            putAside(pack(), "editor_pack");
            putAside(game().resolve("rysquests/drafts"), "editor_drafts");
        }, 0);
        openWorld();
        sizeOfMockups();
        step("open the book", () -> mc().setScreen(new BookScreen()), 20);
        expect("the book is empty", () -> ClientBook.received() && ClientBook.current().chapters().isEmpty());
        // --- 09: a new book, its first chapter as a table ---
        step("start a book from nothing", () -> EditorTesting.startNewBook("Моя сборка", MapForm.AUTO, false), 10);
        expect("the editor is open on a chapter", () -> EditorTesting.on() && EditorTesting.chapter() != null);
        step("type four quests into the table", () -> {
            chapter[0] = EditorTesting.chapter();
            for (String title : List.of("Первое дерево", "Верстак", "Сундук для вещей", "Каменная кирка")) {
                EditorTesting.typeInTable(title);
            }
            quests.addAll(EditorTesting.quests(chapter[0]));
        }, 10);
        expect("the table has the four quests in order", () -> quests.size() == 4 && EditorTesting.title(quests.get(1)).equals("Верстак"));
        expect("a quest typed after another opens after it", () -> EditorTesting.quest(quests.get(1)).requires().contains(quests.get(0)));
        step("give them tasks and rewards, make the chest a side quest", () -> {
            EditorTesting.pickTask(quests.get(0), "get", ResourceLocation.parse("minecraft:oak_log"), 4, "");
            EditorTesting.confirmPicker();
            EditorTesting.addTask(quests.get(1), task("{\"type\":\"rysquests:item\",\"item\":\"minecraft:crafting_table\",\"mode\":\"observe\"}"));
            EditorTesting.addTask(quests.get(3), task("{\"type\":\"rysquests:item\",\"item\":\"minecraft:stone_pickaxe\",\"mode\":\"observe\"}"));
            EditorTesting.addReward(quests.get(0), reward("{\"type\":\"rysquests:xp\",\"points\":10}"));
            EditorTesting.setKind(quests.get(2), QuestEdits.Kind.SIDE);
            EditorTesting.link(quests.get(3), quests.get(1));
            EditorTesting.link(quests.get(3), quests.get(2));
            EditorTesting.tick(quests.get(1));
        }, 20);
        expect("the picker added its task", () -> !EditorTesting.pickerOpen() && EditorTesting.quest(quests.get(0)).tasks().size() == 1);
        expect("the pickaxe opens after the crafting table only", () -> EditorTesting.quest(quests.get(3)).requires().equals(List.of(quests.get(1))));
        expect("the chest is a side quest", () -> EditorTesting.quest(quests.get(2)).options().optional());
        expect("every edit is in the draft", () -> EditorTesting.unsaved() >= 10 && Files.isRegularFile(EditorTesting.draftFile()));
        screenshot("editor_new_book_table");
        step("link the first quest after the last one", () -> EditorTesting.link(quests.get(0), quests.get(3)), 5);
        expect("a circle is refused", () -> EditorTesting.quest(quests.get(0)).requires().isEmpty());
        // --- 33, 33c: the canvas, a link being drawn ---
        step("show the canvas", EditorTesting::canvas, 10);
        expect("the canvas is laid out", EditorTesting::canvasReady);
        step("look at the crafting table up close", () -> {
            EditorTesting.select(quests.get(1));
            EditorTesting.flyTo(quests.get(1));
        }, 40);
        step("draw a link from the first quest to the chest", () -> {
            if (!EditorTesting.holdLink(quests.get(0), quests.get(2))) {
                fail("the link could not be held");
            }
        }, 4);
        screenshot("editor_quest");
        step("draw a link that would close a circle", () -> {
            pressKey(GLFW.GLFW_KEY_ESCAPE);
            EditorTesting.holdLink(quests.get(3), quests.get(0));
        }, 4);
        screenshot("editor_link_cycle");
        step("let it go", EditorTesting::dropLink, 4);
        expect("the circle was not made", () -> EditorTesting.quest(quests.get(0)).requires().isEmpty());
        // --- 34: "Что нужно сделать?" ---
        step("open the list of tasks", () -> {
            EditorTesting.pickTask(quests.get(1), "get", null, 1, "");
            EditorTesting.pickerQuery("верстак");
        }, 10);
        screenshot("editor_add_task");
        step("close the list", () -> pressKey(GLFW.GLFW_KEY_ESCAPE), 4);
        // --- 35: the chapter's form ---
        step("open the chapter's form", () -> EditorTesting.chapterForm(chapter[0]), 10);
        screenshot("editor_chapter");
        // --- 36: the form of the map ---
        step("open the form of the map", EditorTesting::mapForm, 120);
        screenshot("editor_map_form");
        step("choose the spiral", () -> EditorTesting.setMapForm(MapForm.SPIRAL), 60);
        expect("the choice is in the draft", () -> EditorTesting.draftForm().filter(MapForm.SPIRAL::equals).isPresent());
        screenshot("editor_map_form_spiral");
        step("back to the table", () -> EditorTesting.table(chapter[0]), 10);
        screenshot("editor_chapter_table");
        // --- the other tabs ---
        step("write a guide", () -> {
            EditorTesting.open("guides");
            EditorTesting.newGuide("Первые шаги", "Срубите **дерево** и сделайте [[item:minecraft:crafting_table]].\n!!Без кирки камень не добыть.!!");
        }, 20);
        screenshot("editor_guide_page");
        step("add an offer", () -> {
            EditorTesting.open("shop");
            EditorTesting.newOffer();
        }, 20);
        screenshot("editor_shop");
        step("add an achievement", () -> {
            EditorTesting.open("achievements");
            EditorTesting.newAchievement();
        }, 20);
        screenshot("editor_achievements");
        step("make a matrix and open a cell", () -> {
            EditorTesting.open("tabs");
            EditorTesting.newTab("matrix");
            EditorTesting.cell(0, 1);
        }, 20);
        screenshot("editor_tabs_matrix");
        step("show the check", () -> {
            EditorTesting.open("check");
            EditorTesting.checkIssues();
        }, 30);
        screenshot("editor_check");
        step("show where teams get stuck", EditorTesting::checkStuck, 30);
        screenshot("editor_check_stuck");
        // --- the save and its files ---
        step("save", () -> {
            EditorTesting.open("map");
            EditorTesting.save();
        }, 10);
        expect("the save went through", () -> !EditorTesting.saving() && EditorTesting.unsaved() == 0);
        expect("the book has the chapter and its quests", () -> ClientBook.current().chapters().containsKey(chapter[0]) && quests.stream().allMatch(ClientBook.current().quests()::containsKey));
        expect("the book is closed to players", () -> !ClientBook.current().meta().published() && ClientBook.current().meta().mapForm() == MapForm.SPIRAL);
        expect("the files match the format", () -> {
            try {
                Path folder = chapterFolder(chapter[0]);
                JsonObject second = JsonParser.parseString(Files.readString(folder.resolve(quests.get(1).getPath().substring(quests.get(1).getPath().lastIndexOf('/') + 1) + ".json"))).getAsJsonObject();
                Quest parsed = Quest.CODEC.parse(ops(), second).getOrThrow();
                List<String> order = new ArrayList<>(second.keySet());
                String book;
                try (Stream<Path> files = Files.walk(pack().resolve("data"))) {
                    Path found = files.filter(file -> file.toString().replace(java.io.File.separatorChar, '/').endsWith("rysquests/book.json")).findFirst().orElseThrow();
                    book = Files.readString(found);
                }
                boolean lang;
                try (Stream<Path> files = Files.walk(pack().resolve("assets"))) {
                    lang = files.filter(file -> file.getFileName().toString().equals("ru_ru.json")).anyMatch(file -> {
                        try {
                            return Files.readString(file).contains("Верстак");
                        } catch (IOException e) {
                            return false;
                        }
                    });
                }
                return Files.isRegularFile(folder.resolve("_chapter.json")) && order.indexOf("id") == 0 && parsed.requires().equals(List.of(quests.get(0)))
                        && parsed.tasks().size() == 1 && book.contains("\"map_form\": \"spiral\"") && book.contains("\"published\": false") && lang;
            } catch (IOException | RuntimeException e) {
                return false;
            }
        });
        expect("the guide, the offer and the achievement have their files", () -> {
            try (Stream<Path> files = Files.walk(pack().resolve("data"))) {
                List<String> paths = files.map(Path::toString).map(path -> path.replace('\\', '/')).toList();
                return paths.stream().anyMatch(path -> path.contains("/rysquests/guides/")) && paths.stream().anyMatch(path -> path.contains("/rysquests/shop/"))
                        && paths.stream().anyMatch(path -> path.contains("/rysquests/achievements/"));
            } catch (IOException e) {
                return false;
            }
        });
        expect("the draft is put aside", () -> !Files.isRegularFile(EditorTesting.draftFile()));
        // --- 33b: closing with edits ---
        step("rename the pickaxe and close the book", () -> {
            EditorTesting.canvas();
            EditorTesting.select(quests.get(3));
            EditorTesting.addReward(quests.get(3), reward("{\"type\":\"rysquests:currency\",\"id\":\"coin\",\"amount\":1}"));
            if (!EditorTesting.askClose()) {
                fail("the book did not ask before closing");
            }
        }, 10);
        expect("the question is open", BookScreen::dialogOpenForTest);
        screenshot("editor_close_unsaved");
        step("stay", EditorTesting::dismissQuestion, 4);
        // --- a save refused because the chapter changed meanwhile ---
        step("change the chapter's file behind the editor and reload", () -> {
            try {
                Path file = chapterFolder(chapter[0]).resolve("_chapter.json");
                Files.writeString(file, Files.readString(file).replace("\"title\"", "\"icon\": \"minecraft:oak_log\",\n  \"title\""));
            } catch (IOException e) {
                fail("could not change the file: " + e);
            }
            command("reload");
        }, 80);
        expect("the book changed", () -> ClientBook.current().chapters().get(chapter[0]).icon().isPresent());
        step("save over it", EditorTesting::save, 20);
        expect("the server refused and the book asked", EditorTesting::conflict);
        screenshot("editor_conflict");
        step("save over the other version", EditorTesting::saveOver, 20);
        expect("the second save went through", () -> !EditorTesting.saving() && EditorTesting.unsaved() == 0
                && ClientBook.current().quests().get(quests.get(3)).rewards().size() == 1);
        // --- the compact layout (46e) ---
        step("make the window small", () -> {
            EditorTesting.select(quests.get(1));
            mc().getWindow().setWindowed(1024, 640);
            mc().resizeDisplay();
        }, 30);
        screenshot("editor_compact_quest");
        step("back to the size of the mockups", () -> {
            mc().getWindow().setWindowed(1280, 800);
            mc().resizeDisplay();
        }, 20);
        // --- an edit left in the draft for the next run ---
        step("leave an edit in the draft", () -> EditorTesting.addReward(quests.get(2), reward("{\"type\":\"rysquests:currency\",\"id\":\"coin\",\"amount\":5}")), 10);
        expect("the draft is on the disk", () -> EditorTesting.unsaved() == 1 && Files.isRegularFile(EditorTesting.draftFile()));
        step("forget it in memory, as closing the game does", EditorTesting::forgetDraft, 4);
        expect("it comes back from the disk", () -> EditorTesting.restoredFromDisk() && EditorTesting.unsaved() == 1);
        step("close the book", () -> mc().setScreen(null), 10);
    }

    private static void buildRestart() {
        openWorld();
        sizeOfMockups();
        step("open the book in the editor", () -> {
            mc().setScreen(new BookScreen());
            EditorTesting.open("map");
        }, 20);
        expect("the draft of the last run is there", () -> EditorTesting.restoredFromDisk() && EditorTesting.unsaved() == 1);
        step("ask whether to keep it", EditorTesting::askRestore, 10);
        expect("the question is open", BookScreen::dialogOpenForTest);
        screenshot("editor_restore");
        step("keep it and save", () -> {
            EditorTesting.keepRestored();
            EditorTesting.save();
        }, 20);
        expect("the save went through", () -> !EditorTesting.saving() && EditorTesting.unsaved() == 0);
        expect("the chest got its coins", () -> ClientBook.current().quests().values().stream()
                .anyMatch(quest -> quest.options().optional() && quest.rewards().size() == 1));
        step("put the book aside, other scenarios count the quests of the example", () -> {
            EditorTesting.leave();
            mc().setScreen(null);
            putAside(pack(), "editor_pack");
            command("reload");
        }, 60);
    }
}
