package works.repka.rysquests.client.screen;

import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.data.PagesSection;
import works.repka.rysquests.data.SectionDef;

/**
 * Where the pencils of the player's pages lead: into the editor of the book ({@link EditorPages}) on what they point at. The old
 * full-window editors are in {@code _stale/client-screen-editor}; the calls stay so that pages need not change.
 */
final class BookEditors {
    private final BookScreen screen;

    BookEditors(BookScreen screen) {
        this.screen = screen;
    }

    /** A guide, or a tab of pages, opens on the editor's "Гайды" or "Вкладки". */
    void openGuide(GuideDraft draft, boolean isNew) {
        EditorBook.active = true;
        screen.closePage();
        if (draft.section) {
            EditorPages.current = EditorPages.TABS;
            ((TabsEditorView) screen.editor.page(EditorPages.TABS)).select(draft.id);
        } else {
            EditorPages.current = EditorPages.GUIDES;
            ((GuideEditorView) screen.editor.page(EditorPages.GUIDES)).select(draft.id, 0);
        }
    }

    /** A tab of the author: "Вкладки" with it chosen. */
    void openSection(@Nullable SectionDef def) {
        EditorBook.active = true;
        screen.closePage();
        if (def instanceof PagesSection pages) {
            openGuide(GuideDraft.of(pages), false);
            return;
        }
        EditorPages.current = EditorPages.TABS;
        if (def != null) {
            ((TabsEditorView) screen.editor.page(EditorPages.TABS)).select(def.id());
        }
    }

    /** The quest editor: the editor's "Карта". */
    void openQuest() {
        EditorBook.active = true;
        screen.closePage();
        EditorPages.current = EditorPages.MAP;
    }
}
