package works.repka.rysquests.client.screen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.client.ClientConfig;
import works.repka.rysquests.data.BookParser;
import works.repka.rysquests.data.BookValidator;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.ViewMode;
import works.repka.rysquests.integration.ftbq.FtbqFiles;
import works.repka.rysquests.integration.ftbq.FtbqImporter;

/**
 * Hooks of the "panel" autotest into the quest panel, "Now" and the list (plan steps 5 and 6), and a page with three panels side by
 * side as the mockup sheets 20, 21 and 21b lay them out. Part of the autotest only, never of the mod.
 */
public final class PanelShowcase implements BookView {
    private final List<ResourceLocation> quests;
    private final List<QuestPanel> panels = new ArrayList<>();

    private PanelShowcase(List<ResourceLocation> quests) {
        this.quests = quests;
        quests.forEach(id -> panels.add(new QuestPanel()));
    }

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        ui.rect(x, y, w, h, ui.c("sidebar.bg"));
        float gap = 14;
        float colW = (w - gap * (quests.size() + 1)) / quests.size();
        for (int i = 0; i < quests.size(); i++) {
            Quest quest = BookData.book().quests().get(quests.get(i));
            if (quest == null) {
                continue;
            }
            float cx = x + gap + i * (colW + gap);
            ui.box(cx, y + gap - 1, colW + 1, h - gap * 2 + 2, 12, 0, ui.c("panel.border"));
            panels.get(i).render(ui, cx, y + gap, colW, h - gap * 2, quest, QuestPanel.Look.NOW);
        }
    }

    @Override
    public void scroll(double mouseX, double mouseY, double amount) {
        panels.forEach(panel -> {
            if (panel.contains(mouseX, mouseY)) {
                panel.scroll(amount);
            }
        });
    }

    private static BookScreen screen() {
        return Minecraft.getInstance().screen instanceof BookScreen screen ? screen : null;
    }

    /** Three panels side by side, as a page of the open book. */
    public static void sheet(ResourceLocation... ids) {
        BookScreen screen = screen();
        if (screen != null) {
            screen.openPage(new PanelShowcase(List.of(ids)));
        }
    }

    public static void closePage() {
        BookScreen screen = screen();
        if (screen != null) {
            screen.closePage();
        }
    }

    /** "Now", with or without the three lines of the first opening, the next step in the panel. */
    public static void now(boolean intro) {
        ClientConfig.NOW_INTRO_SEEN.set(!intro);
        BookScreen screen = screen();
        if (screen != null) {
            screen.closePage();
            screen.openTab(BookTabs.byId(BookTabs.NOW));
            ((NowView) screen.view(BookTabs.NOW)).chooseForTest(null);
        }
    }

    public static void nowChoose(ResourceLocation quest) {
        BookScreen screen = screen();
        if (screen != null) {
            ((NowView) screen.view(BookTabs.NOW)).chooseForTest(quest);
        }
    }

    /** Picks an option of a choice reward, by the reward's index. */
    public static void pick(int reward, int option) {
        BookState.PICKS.put(reward, option);
    }

    /** The next step "Now" shows, or null. */
    public static ResourceLocation nextStep() {
        return NowView.nextStep().map(result -> result.quest().id()).orElse(null);
    }

    /** The map tab on a chapter with a quest selected. */
    public static void map(ResourceLocation chapter, ResourceLocation quest) {
        BookScreen screen = screen();
        if (screen != null) {
            screen.closePage();
        }
        BookState.section = Chapter.STORY;
        BookState.view = ViewMode.MAP;
        BookState.openChapter(chapter);
        QuestListView.showChapter();
        BookState.quest = quest;
    }

    /** The list: a chapter (chapter given), an epoch (epoch above 0) or the whole book, with a filter and a quest selected. */
    public static void list(ResourceLocation chapter, int epoch, String filter, ResourceLocation quest, boolean expand) {
        BookScreen screen = screen();
        if (screen != null) {
            screen.closePage();
        }
        BookState.section = Chapter.STORY;
        BookState.view = ViewMode.LIST;
        if (chapter != null) {
            BookState.openChapter(chapter);
            QuestListView.showChapter();
        } else if (epoch > 0) {
            QuestListView.showEpoch(epoch);
        } else {
            QuestListView.showBook();
        }
        QuestListView.filterForTest(QuestListView.Filter.valueOf(filter));
        BookState.quest = quest;
        if (expand && quest != null) {
            QuestListView.expandForTest(quest);
        }
    }

    /** Every chapter of the whole book unfolded in full: the longest list the book can make. */
    public static void listOpenAll() {
        QuestListView.openAllForTest();
    }

    private static QuestListView listView() {
        BookScreen screen = screen();
        return screen == null ? null : screen.mapTab().list;
    }

    /** "rows N content M": how many rows the list laid out and how tall they are together. */
    public static String listStats() {
        QuestListView list = listView();
        return list == null ? "" : "rows " + list.rowsForTest() + " content " + Math.round(list.contentForTest());
    }

    public static void listScroll(double notches) {
        QuestListView list = listView();
        if (list != null) {
            list.scrollForTest(notches);
        }
    }

    private static void stripComponents(JsonElement element) {
        if (element.isJsonObject()) {
            element.getAsJsonObject().remove("components");
            element.getAsJsonObject().remove("nbt");
            element.getAsJsonObject().asMap().values().forEach(PanelShowcase::stripComponents);
        } else if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(PanelShowcase::stripComponents);
        }
    }

    /**
     * Reads an FTB Quests book and converts it as the import does, without writing anything: the quests carry their names from the
     * book's lang so that the screenshots read. For measuring the list on a large book (ATM10: 4 853 quests).
     */
    public static QuestBook ftbqBook(Path quests) throws java.io.IOException {
        FtbqImporter.Result result = FtbqImporter.convert(FtbqFiles.read(quests), "atm", FtbqImporter.Ids.empty());
        Map<String, String> lang = new HashMap<>(result.lang().getOrDefault("en_us", Map.of()));
        lang.putAll(result.lang().getOrDefault("ru_ru", Map.of()));
        Map<ResourceLocation, JsonElement> files = new HashMap<>();
        result.files().forEach((path, json) -> {
            String[] parts = path.split("/", 4);
            if (parts.length < 4 || !parts[2].equals("rysquests")) {
                return;
            }
            String relative = parts[3].substring(0, parts[3].length() - ".json".length());
            JsonObject copy = json.deepCopy();
            // Component types of mods that are not loaded here cannot be read; in the real pack they resolve.
            stripComponents(copy);
            for (String field : List.of("title", "subtitle", "text")) {
                if (copy.has(field) && copy.get(field).isJsonPrimitive() && lang.containsKey(copy.get(field).getAsString())) {
                    copy.addProperty(field, lang.get(copy.get(field).getAsString()));
                }
            }
            files.put(ResourceLocation.fromNamespaceAndPath(parts[1], relative), copy);
        });
        BookValidator.Result loaded = BookValidator.validate(BookParser.parse(files, JsonOps.INSTANCE), id -> true, JsonOps.INSTANCE);
        org.slf4j.LoggerFactory.getLogger(PanelShowcase.class).info("[autotest] ftbq import: {} quests converted, {} loaded, {} errors",
                result.quests(), loaded.book().quests().size(), loaded.report().errors());
        return loaded.book();
    }
}
