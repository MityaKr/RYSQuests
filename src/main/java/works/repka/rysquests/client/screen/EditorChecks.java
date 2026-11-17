package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import works.repka.ryscore.ui.Icons;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.Guide;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.SectionDef;
import works.repka.rysquests.editor.QuestEdits;

/**
 * What "Проверка" finds in the draft (pictures 37, 37b), in the author's words, each with ways to fix it: errors a player runs into
 * (a link to a page that is not there, a quest waiting for one that is gone or in a later epoch) and remarks that work but look odd
 * (quests without tasks, two main quests of one name, texts that name a tab hidden from players, quests hidden until the author
 * looks). Worked out on the client from the draft, so a fix shows at once.
 */
final class EditorChecks {
    enum Level {
        ERROR, WARN, INFO
    }

    /** A way to fix an issue. @param primary the one the panel's main button does */
    record Fix(String title, @Nullable String sub, @Nullable Icons.Shape icon, Runnable action, boolean primary) {
    }

    /**
     * One issue.
     *
     * @param quest the quest it is about, shown on the right, or null for one about many
     */
    record Issue(Level level, String title, String sub, @Nullable ResourceLocation quest, String detail, List<Fix> fixes) {
    }

    private EditorChecks() {
    }

    private static String tr(String key, Object... args) {
        return EditorBook.tr(key, args);
    }

    static List<Issue> find(QuestBook book) {
        EditorBook editor = EditorBook.get();
        List<Issue> out = new ArrayList<>();
        Map<ResourceLocation, Quest> quests = book.quests();
        List<Quest> story = quests.values().stream().filter(q -> q.options().live() && q.chapter().map(id -> book.chapters().containsKey(id) && book.chapters().get(id).isStory()).orElse(false)).toList();
        // errors: a link to a guide page that is not there
        for (Quest quest : story) {
            quest.guide().ifPresent(ref -> {
                Guide guide = book.guides().get(ref.guide());
                boolean missing = guide == null || ref.page().filter(page -> guide.pageIndex(page) < 0).isPresent();
                if (missing) {
                    String title = quest.title();
                    String chapter = chapterTitle(book, quest);
                    List<Fix> fixes = new ArrayList<>();
                    Guide guess = guess(book, title);
                    if (guess != null) {
                        fixes.add(new Fix(tr("rysquests.ed.check.fix.guide", guess.title()), tr("rysquests.ed.check.fix.guide_sub"), Icons.Shape.BOOK,
                                () -> setGuide(quest.id(), Optional.of(new works.repka.rysquests.data.GuideRef(guess.id(), Optional.empty()))), true));
                    }
                    fixes.add(new Fix(tr("rysquests.ed.check.fix.other_page"), null, Icons.Shape.LIST, () -> openGuides(), false));
                    fixes.add(new Fix(tr("rysquests.ed.check.fix.no_guide"), null, Icons.Shape.CROSS, () -> setGuide(quest.id(), Optional.empty()), guess == null));
                    out.add(new Issue(Level.ERROR, tr(quest.options().milestone() ? "rysquests.ed.check.guide_missing_milestone" : "rysquests.ed.check.guide_missing", title),
                            tr("rysquests.ed.check.guide_missing_sub", chapter, ref.toString()), quest.id(),
                            tr("rysquests.ed.check.guide_missing_detail", guide == null ? ref.guide().toString() : guide.title(), ref.page().orElse("")), fixes));
                }
            });
        }
        // errors: waiting for a quest that is gone, archived, or in a later epoch
        for (Quest quest : story) {
            for (ResourceLocation dependency : quest.requires()) {
                Quest other = quests.get(dependency);
                if (other == null || !other.options().live()) {
                    out.add(new Issue(Level.ERROR, tr("rysquests.ed.check.waits_gone", quest.title()), tr("rysquests.ed.check.waits_gone_sub", chapterTitle(book, quest), dependency.toString()),
                            quest.id(), tr("rysquests.ed.check.waits_gone_detail"),
                            List.of(new Fix(tr("rysquests.ed.check.fix.unlink"), null, Icons.Shape.CROSS, () -> unlink(quest.id(), dependency), true))));
                    continue;
                }
                int own = epoch(book, quest);
                int theirs = epoch(book, other);
                if (own > 0 && theirs > own) {
                    out.add(new Issue(Level.ERROR, tr("rysquests.ed.check.epoch_order", quest.title(), Ui.roman(own), Ui.roman(theirs)),
                            tr("rysquests.ed.check.epoch_order_sub", chapterTitle(book, quest)), quest.id(),
                            tr("rysquests.ed.check.epoch_order_detail", other.title(), Ui.roman(theirs)),
                            List.of(new Fix(tr("rysquests.ed.check.fix.unlink"), null, Icons.Shape.CROSS, () -> unlink(quest.id(), dependency), true))));
                }
            }
        }
        // remarks: texts that name a tab players do not see, one left out of the book's list of tabs (matrices are shown since ADR-60)
        List<String> listed = book.meta().sections();
        for (SectionDef section : book.sections().values()) {
            if (listed.isEmpty() || listed.contains(section.id().toString())) {
                continue;
            }
            String name = editor.text(section.title());
            if (name.length() < 4) {
                continue;
            }
            List<Quest> naming = story.stream().filter(q -> q.text().map(editor::text).filter(text -> text.toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT))).isPresent()).toList();
            if (!naming.isEmpty()) {
                Quest first = naming.getFirst();
                out.add(new Issue(Level.WARN, tr("rysquests.ed.check.hidden_tab", Plural.full("rysquests.ed.plural.texts_name", naming.size()), name),
                        tr("rysquests.ed.check.hidden_tab_sub", chapterTitle(book, first), first.title(), naming.size() - 1), first.id(),
                        tr("rysquests.ed.check.hidden_tab_detail", name),
                        List.of(new Fix(tr("rysquests.ed.check.fix.to_tabs"), null, Icons.Shape.LIST, () -> openTabs(section.id()), true))));
            }
        }
        // remarks: quests without tasks that are not pages
        List<Quest> empty = story.stream().filter(q -> q.tasks().isEmpty() && !q.options().page()).toList();
        if (!empty.isEmpty()) {
            Quest first = empty.getFirst();
            out.add(new Issue(Level.WARN, tr("rysquests.ed.check.no_tasks", Plural.full("rysquests.plural.quest", empty.size())),
                    tr("rysquests.ed.check.no_tasks_sub", chapterTitle(book, first), first.title(), Math.max(0, empty.size() - 1)), first.id(),
                    tr("rysquests.ed.check.no_tasks_detail"),
                    List.of(new Fix(Plural.full("rysquests.ed.check.fix.make_pages", empty.size()), tr("rysquests.ed.check.fix.make_pages_sub"), Icons.Shape.BOOK, () -> makePages(empty), true),
                            new Fix(tr("rysquests.ed.check.fix.show_first"), null, Icons.Shape.MAP, () -> showOnMap(first.id()), false))));
        }
        // remarks: two main quests of one name in a chapter
        Map<String, List<Quest>> names = new LinkedHashMap<>();
        for (Quest quest : story) {
            if (!quest.options().optional()) {
                names.computeIfAbsent(quest.chapter().map(Object::toString).orElse("") + "\n" + quest.title().toLowerCase(Locale.ROOT), key -> new ArrayList<>()).add(quest);
            }
        }
        names.values().stream().filter(list -> list.size() > 1).forEach(list -> out.add(new Issue(Level.WARN, tr("rysquests.ed.check.same_name", Plural.full("rysquests.ed.plural.main_quest", list.size()), list.getFirst().title()),
                tr("rysquests.ed.check.same_name_sub", chapterTitle(book, list.getFirst())), list.get(1).id(), tr("rysquests.ed.check.same_name_detail"),
                List.of(new Fix(tr("rysquests.ed.check.fix.rename"), null, Icons.Shape.PENCIL, () -> showOnMap(list.get(1).id()), true)))));
        // remarks: quests hidden until the author looks (after an import)
        List<Quest> drafts = quests.values().stream().filter(q -> q.options().draft() && !q.options().archived()).toList();
        if (!drafts.isEmpty()) {
            out.add(new Issue(Level.INFO, Plural.full("rysquests.ed.check.drafts", drafts.size()), tr("rysquests.ed.check.drafts_sub"), drafts.getFirst().id(), tr("rysquests.ed.check.drafts_detail"),
                    List.of(new Fix(tr("rysquests.ed.check.fix.show_draft"), null, Icons.Shape.EYE, () -> undraft(drafts.getFirst().id()), true),
                            new Fix(tr("rysquests.ed.check.fix.show_first"), null, Icons.Shape.MAP, () -> showOnMap(drafts.getFirst().id()), false))));
        }
        return out;
    }

    static int epoch(QuestBook book, Quest quest) {
        return quest.chapter().map(book.chapters()::get).flatMap(Chapter::epoch).orElse(0);
    }

    static String chapterTitle(QuestBook book, Quest quest) {
        return quest.chapter().map(book.chapters()::get).map(Chapter::title).orElse("");
    }

    /** A guide whose title is like the quest's, "похоже, имелся в виду он". */
    @Nullable
    private static Guide guess(QuestBook book, String title) {
        String want = title.toLowerCase(Locale.ROOT);
        EditorBook editor = EditorBook.get();
        return book.guides().values().stream().filter(guide -> {
            String name = editor.text(guide.title()).toLowerCase(Locale.ROOT);
            return name.contains(want) || want.contains(name);
        }).findFirst().orElse(null);
    }

    // --- fixes ---

    private static void setGuide(ResourceLocation quest, Optional<works.repka.rysquests.data.GuideRef> guide) {
        EditorBook editor = EditorBook.get();
        Quest found = editor.quest(quest);
        if (found == null) {
            return;
        }
        editor.editQuest(quest, tr("rysquests.ed.log.guide", editor.text(found.title())), q -> new Quest(q.id(), q.chapter(), q.group(), q.title(), q.subtitle(), q.text(), q.icon(),
                q.requires(), q.requiresMode(), q.stageRequired(), q.tasks(), q.rewards(), guide, q.options()));
    }

    private static void unlink(ResourceLocation quest, ResourceLocation dependency) {
        EditorBook editor = EditorBook.get();
        Quest found = editor.quest(quest);
        if (found != null) {
            editor.editQuest(quest, tr("rysquests.ed.log.unlink", dependency.getPath(), editor.text(found.title())),
                    q -> QuestEdits.withRequires(q, q.requires().stream().filter(id -> !id.equals(dependency)).toList(), q.requiresMode()));
        }
    }

    private static void makePages(List<Quest> quests) {
        EditorBook editor = EditorBook.get();
        Map<ResourceLocation, List<ResourceLocation>> byChapter = new HashMap<>();
        quests.forEach(q -> q.chapter().ifPresent(c -> byChapter.computeIfAbsent(c, key -> new ArrayList<>()).add(q.id())));
        editor.edit("chapter:" + byChapter.keySet().iterator().next(), Plural.full("rysquests.ed.log.pages", quests.size()), d -> {
            var out = d;
            for (var entry : byChapter.entrySet()) {
                var work = out.chapters().containsKey(entry.getKey()) ? out.chapters().get(entry.getKey()) : editor.work(entry.getKey());
                if (work == null) {
                    continue;
                }
                for (ResourceLocation id : entry.getValue()) {
                    Quest q = work.questMap().get(id);
                    if (q != null) {
                        var o = q.options();
                        work = work.withQuest(QuestEdits.withOptions(q, new works.repka.rysquests.data.QuestOptions(o.layout(), o.milestone(), o.optional(), o.hidden(),
                                o.repeatable(), true, o.tags(), o.draft(), o.archived())));
                    }
                }
                out = out.withWork(work);
            }
            return out;
        });
    }

    private static void undraft(ResourceLocation quest) {
        EditorBook editor = EditorBook.get();
        Quest found = editor.quest(quest);
        if (found != null) {
            editor.editQuest(quest, tr("rysquests.ed.log.undraft", editor.text(found.title())), q -> QuestEdits.withOptions(q, q.options().withDraft(false)));
        }
    }

    static void showOnMap(ResourceLocation quest) {
        if (net.minecraft.client.Minecraft.getInstance().screen instanceof BookScreen screen) {
            EditorPages.current = EditorPages.MAP;
            EditorMapView map = screen.editor.map();
            if (map.mode() != EditorMapView.Mode.CANVAS) {
                map.modeForTest(EditorMapView.Mode.CANVAS);
            }
            map.select(quest);
            map.flyToQuestForTest(quest);
        }
    }

    private static void openGuides() {
        EditorPages.current = EditorPages.GUIDES;
    }

    private static void openTabs(ResourceLocation section) {
        if (net.minecraft.client.Minecraft.getInstance().screen instanceof BookScreen screen) {
            EditorPages.current = EditorPages.TABS;
            ((TabsEditorView) screen.editor.page(EditorPages.TABS)).select(section);
        }
    }
}
