package works.repka.rysquests.client.screen;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.ViewMode;
import works.repka.rysquests.progress.QuestLogic;
import works.repka.rysquests.progress.QuestStatus;

/** What the book shows. Static, so that it survives closing the book and coming back from the recipe window. */
final class BookState {
    @Nullable
    static ResourceLocation chapter;
    @Nullable
    static ResourceLocation quest;
    @Nullable
    static ViewMode view;
    static String search = "";
    static boolean showDone;
    static boolean showRest;
    /** Picks of choice rewards by reward index, for the selected quest. */
    static final Map<Integer, Integer> PICKS = new HashMap<>();
    static final Set<String> REVEALED = new HashSet<>();

    private BookState() {
    }

    static void select(Quest selected) {
        if (!selected.id().equals(quest)) {
            PICKS.clear();
        }
        quest = selected.id();
        selected.chapter().ifPresent(id -> chapter = id);
    }

    static void openChapter(ResourceLocation id) {
        chapter = id;
        showDone = false;
        showRest = false;
    }

    static ViewMode currentView() {
        if (view != null) {
            return view;
        }
        var open = chapter == null ? null : ClientBook.current().chapters().get(chapter);
        return open == null ? ClientBook.current().meta().defaultView() : open.defaultView().orElse(ClientBook.current().meta().defaultView());
    }

    static QuestStatus status(Quest target) {
        QuestStatus status = QuestLogic.status(target, ClientProgress::get);
        boolean open = status == QuestStatus.AVAILABLE || status == QuestStatus.IN_PROGRESS;
        return open && ClientProgress.isStageLocked(target.id()) ? QuestStatus.LOCKED : status;
    }
}
