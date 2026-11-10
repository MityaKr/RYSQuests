package works.repka.rysquests.client.screen;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.client.ClientAssets;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.client.markup.GuideTopics;
import works.repka.rysquests.data.Guide;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.progress.QuestStatus;

/** What the guide screens need to know about guides. Lives as long as the book window, so translations stay current. */
final class Guides {
    private static final int WORDS_PER_MINUTE = 180;
    private static final int PNG_SIZE_OFFSET = 16;

    enum State {
        NEEDED, DONE, OPEN, LOCKED
    }

    /** A guide page an open quest points at. */
    record Focus(Quest quest, Guide guide, int page) {
    }

    @Nullable
    private QuestBook cachedFor;
    private final Map<ResourceLocation, List<Quest>> referencing = new HashMap<>();
    private final Map<ResourceLocation, String> searchText = new HashMap<>();
    private final Map<ResourceLocation, Integer> minutes = new HashMap<>();
    private final Map<ResourceLocation, Optional<int[]>> images = new HashMap<>();

    private void refresh() {
        QuestBook book = ClientBook.current();
        if (book == cachedFor) {
            return;
        }
        cachedFor = book;
        referencing.clear();
        searchText.clear();
        minutes.clear();
        for (Quest quest : book.quests().values()) {
            quest.guide().ifPresent(ref -> referencing.computeIfAbsent(ref.guide(), id -> new ArrayList<>()).add(quest));
        }
    }

    List<Guide> all() {
        return List.copyOf(ClientBook.current().guides().values());
    }

    /** The quests that point at a guide. */
    List<Quest> referencing(ResourceLocation guide) {
        refresh();
        return referencing.getOrDefault(guide, List.of());
    }

    private static boolean active(Quest quest) {
        QuestStatus status = BookState.status(quest);
        return status == QuestStatus.AVAILABLE || status == QuestStatus.IN_PROGRESS;
    }

    State state(Guide guide) {
        if (ClientProgress.isGuideLocked(guide.id())) {
            return State.LOCKED;
        }
        if (ClientProgress.readPages(guide) >= guide.pages().size()) {
            return State.DONE;
        }
        return referencing(guide.id()).stream().anyMatch(Guides::active) ? State.NEEDED : State.OPEN;
    }

    int neededCount() {
        return (int) all().stream().filter(guide -> state(guide) == State.NEEDED).count();
    }

    /** The page for the player's quest: a pinned quest first, then any open one. */
    @Nullable
    Focus focus() {
        QuestBook book = ClientBook.current();
        for (ResourceLocation id : ClientProgress.pinned()) {
            Quest quest = book.quests().get(id);
            Focus focus = quest == null ? null : focusOf(quest);
            if (focus != null) {
                return focus;
            }
        }
        for (Guide guide : all()) {
            for (Quest quest : referencing(guide.id())) {
                Focus focus = active(quest) ? focusOf(quest) : null;
                if (focus != null) {
                    return focus;
                }
            }
        }
        return null;
    }

    @Nullable
    private static Focus focusOf(Quest quest) {
        if (quest.guide().isEmpty()) {
            return null;
        }
        Guide guide = ClientBook.current().guides().get(quest.guide().get().guide());
        if (guide == null || ClientProgress.isGuideLocked(guide.id())) {
            return null;
        }
        int page = quest.guide().get().page().map(guide::pageIndex).filter(index -> index >= 0).orElse(BookState.firstUnread(guide));
        return new Focus(quest, guide, page);
    }

    /** The page of a guide that an open quest points at, or -1. */
    int questPage(Guide guide) {
        for (Quest quest : referencing(guide.id())) {
            if (active(quest) && quest.guide().get().page().isPresent()) {
                int page = guide.pageIndex(quest.guide().get().page().get());
                if (page >= 0) {
                    return page;
                }
            }
        }
        return -1;
    }

    /** A rough reading time, at least a minute. */
    int minutes(Guide guide) {
        refresh();
        return minutes.computeIfAbsent(guide.id(), id -> {
            int words = 0;
            for (Guide.Page page : guide.pages()) {
                words += Component.translatable(page.text()).getString().split("\\s+").length;
            }
            return Math.max(1, words / WORDS_PER_MINUTE);
        });
    }

    boolean matches(Guide guide, String query) {
        refresh();
        return searchText.computeIfAbsent(guide.id(), id -> searchable(guide)).contains(query.toLowerCase(Locale.ROOT));
    }

    private static String searchable(Guide guide) {
        StringBuilder text = new StringBuilder(Component.translatable(guide.title()).getString());
        for (Guide.Page page : guide.pages()) {
            String body = Component.translatable(page.text()).getString();
            text.append(' ').append(Component.translatable(page.title()).getString()).append(' ').append(body);
            GuideTopics.items(body).forEach(item -> text.append(' ').append(TaskText.stackOf(item).getHoverName().getString()));
        }
        return text.toString().toLowerCase(Locale.ROOT);
    }

    /** The size of a picture in the resources, read from its header. @return null when there is no such file */
    @Nullable
    int[] imageSize(ResourceLocation texture) {
        int[] fromBook = ClientAssets.imageSize(texture);
        return fromBook != null ? fromBook : images.computeIfAbsent(texture, Guides::readImageSize).orElse(null);
    }

    private static Optional<int[]> readImageSize(ResourceLocation texture) {
        var resource = Minecraft.getInstance().getResourceManager().getResource(texture);
        if (resource.isEmpty()) {
            return Optional.empty();
        }
        try (DataInputStream in = new DataInputStream(resource.get().open())) {
            in.skipNBytes(PNG_SIZE_OFFSET);
            return Optional.of(new int[] {in.readInt(), in.readInt()});
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
