package works.repka.rysquests.client.screen;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.RYSQuests;
import works.repka.rysquests.data.PagesSection;
import works.repka.rysquests.data.SectionDef;

/**
 * The editors as they are today: the guide editor, the section constructor and the quest editor, each a whole window with its own
 * header. While one is open the book draws it instead of itself. The editor screens (plan step 10) replace them with pages under the
 * book's header in editor mode ({@link EditorSession}).
 */
final class BookEditors {
    private final BookScreen screen;
    @Nullable
    EditorView guide;
    @Nullable
    SectionEditorView section;
    @Nullable
    QuestEditorView quest;

    BookEditors(BookScreen screen) {
        this.screen = screen;
    }

    boolean any() {
        return guide != null || section != null || quest != null;
    }

    void openGuide(GuideDraft draft, boolean isNew) {
        GuidesTab tab = screen.guidesTab();
        guide = new EditorView(screen, tab.guides, tab.guidePage, draft, isNew);
    }

    /** A new guide goes into the namespace of the book. */
    void newGuide() {
        ResourceLocation any = BookData.book().chapters().keySet().stream().findFirst().orElse(ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "guide"));
        int number = BookData.book().guides().size() + 1;
        ResourceLocation id = any.withPath("guide_" + number);
        while (BookData.book().guides().containsKey(id)) {
            number++;
            id = any.withPath("guide_" + number);
        }
        openGuide(GuideDraft.newGuide(id), true);
    }

    void openSection(@Nullable SectionDef def) {
        section = new SectionEditorView(screen, def);
    }

    void openPages(PagesSection pages) {
        section = null;
        openGuide(GuideDraft.of(pages), false);
    }

    void openNewPages(ResourceLocation id) {
        section = null;
        openGuide(GuideDraft.newPages(id), true);
    }

    void openQuest() {
        quest = new QuestEditorView(screen);
    }

    void render(Ui ui, int x, int y, int w, int h) {
        if (quest != null) {
            quest.render(ui, x, y, w, h);
        } else if (guide != null) {
            guide.render(ui, x, y, w, h);
        } else if (section != null) {
            section.render(ui, x, y, w, h);
        }
    }

    void pointer(double x, double y) {
        if (guide != null) {
            guide.pointer(x, y);
        }
        if (section != null) {
            section.pointer(x, y);
        }
        if (quest != null) {
            quest.pointer(x, y);
        }
    }

    boolean release(double x, double y) {
        if (quest != null) {
            quest.release(x, y);
            return true;
        }
        return false;
    }

    void drag(double x, double y) {
        if (quest != null) {
            quest.drag(x, y);
        } else if (guide != null) {
            guide.drag(x, y);
        } else if (section != null) {
            section.drag(x, y);
        }
    }

    void scroll(double x, double y, double amount) {
        if (quest != null) {
            quest.scroll(x, y, amount);
        } else if (guide != null) {
            guide.scroll(x, y, amount);
        } else if (section != null) {
            section.scroll(x, y, amount);
        }
    }

    boolean typed(char c) {
        if (quest != null) {
            return quest.typed(c);
        }
        if (section != null) {
            return section.typed(c);
        }
        return guide != null && guide.typed(c);
    }

    boolean key(int key) {
        if (quest != null) {
            return quest.key(key);
        }
        if (guide != null) {
            return guide.key(key);
        }
        return section != null && section.key(key);
    }

    void onResult(boolean ok, String reason, List<String> args) {
        if (guide != null) {
            guide.onResult(ok, reason);
        }
        if (section != null) {
            section.onResult(ok, reason);
        }
        if (quest != null) {
            quest.onResult(ok, reason, args);
        }
    }
}
