package works.repka.rysquests.client.screen;

import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.network.QuestActionPayload;
import works.repka.rysquests.progress.QuestLogic;
import works.repka.rysquests.progress.QuestProgress;
import works.repka.rysquests.task.ItemTask;

/** Lookups into the client's book that every page of the book shares. Static: none of them needs the open screen. */
final class BookData {
    private BookData() {
    }

    static QuestBook book() {
        return ClientBook.current();
    }

    @Nullable
    private static QuestBook chaptersOf;
    private static List<Chapter> chapters = List.of();

    /**
     * The chapters the book shows, in book order: story chapters that are not hidden, and not a chapter that only keeps the rewards
     * of epochs, which show at their epochs on the map (ADR-60).
     */
    static List<Chapter> chapters() {
        QuestBook book = book();
        if (chaptersOf != book) {
            java.util.Set<ResourceLocation> service = works.repka.rysquests.client.layout.MapBook.rewardsOnly(book.meta(), book.quests().values());
            chapters = book.chapters().values().stream().filter(chapter -> !chapter.hidden() && chapter.isStory() && !service.contains(chapter.id())).toList();
            chaptersOf = book;
        }
        return chapters;
    }

    /** Whether a quest belongs to what the book shows: chapters of other sections stay out of it. */
    static boolean inStory(Quest quest) {
        return quest.chapter().map(id -> book().chapters().get(id)).map(Chapter::isStory).orElse(true);
    }

    /** The chapter's number in the book, as the old views show it (a roman numeral there). */
    static int chapterNumber(@Nullable ResourceLocation id) {
        int index = 0;
        for (Chapter chapter : chapters()) {
            index++;
            if (chapter.id().equals(id)) {
                return index;
            }
        }
        return 0;
    }

    /** How many fitting items the player holds. */
    static int held(ItemTask task) {
        Inventory inventory = Minecraft.getInstance().player.getInventory();
        int have = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && task.matches(stack)) {
                have += stack.getCount();
            }
        }
        return have;
    }

    /** What a locked quest still needs, in words. */
    static Component waitingFor(Quest quest) {
        QuestProgress own = ClientProgress.get(quest.id());
        if (own.coolingDown()) {
            long minutes = Math.max(1, (own.nextAt() - System.currentTimeMillis() + 59_999) / 60_000);
            return Component.translatable("rysquests.flow.cooldown", minutes);
        }
        List<Quest> missing = quest.requires().stream()
                .filter(id -> !ClientProgress.get(id).everDone())
                .map(id -> book().quests().get(id))
                .filter(Objects::nonNull)
                .toList();
        if (missing.isEmpty()) {
            if (ClientProgress.isStageLocked(quest.id()) && quest.stageRequired().isPresent()) {
                return Component.translatable("rysquests.flow.needs_stage", quest.stageRequired().get());
            }
            return Component.translatable("rysquests.flow.opens_after");
        }
        Quest first = missing.getFirst();
        return Component.translatable("rysquests.flow.waiting", shown(first) ? Component.translatable(first.title()) : Component.literal("???"));
    }

    /** Whether the player may know the quest: a hidden one stays a secret until its dependencies are done. */
    static boolean shown(Quest quest) {
        return !quest.options().hidden() || QuestLogic.isUnlocked(quest, ClientProgress::get);
    }

    static void togglePin(ResourceLocation quest) {
        PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.PIN, quest, 0, List.of()));
    }
}
