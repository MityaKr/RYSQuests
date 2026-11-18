package works.repka.rysquests.api;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.data.Achievement;
import works.repka.rysquests.data.BookHolder;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.progress.ProgressService;
import works.repka.rysquests.progress.ProgressStore;
import works.repka.rysquests.progress.QuestProgress;
import works.repka.rysquests.stage.Stages;

/** What other mods and scripts can do with quests. Everything outside the {@code api} package may change. */
public final class QuestsApi {
    private QuestsApi() {
    }

    public static QuestBook book() {
        return BookHolder.current().book();
    }

    public static void registerStageProvider(StageProvider provider) {
        Stages.register(provider);
    }

    /** Marks a quest done or resets it for the player's team. @return false if there is no such quest */
    public static boolean setCompleted(ServerPlayer player, ResourceLocation quest, boolean completed) {
        Quest target = book().quests().get(quest);
        if (target == null) {
            return false;
        }
        ProgressService.setCompleted(player, target, completed);
        return true;
    }

    /** The owner of the progress the player counts towards: the team, the player alone or the server, by the book's mode. */
    public static UUID progressOwner(ServerPlayer player) {
        return ProgressService.owner(player);
    }

    /**
     * Whether an owner of progress has done a quest: done now, or in an earlier round of a repeatable quest, which is what opens
     * the quests after it. Reads the saved progress, so the owner may be offline. On the server only; false without a running
     * server. KubeJS server scripts see this class as {@code RYSQuests}, so {@code RYSQuests.isCompleted(owner, 'repys:quest')}
     * replaces reading {@code ProgressStore.allProgress()}, which keeps working.
     *
     * @param owner a team or player id as {@link #progressOwner} gives it
     */
    public static boolean isCompleted(UUID owner, ResourceLocation quest) {
        Map<ResourceLocation, QuestProgress> quests = progressOf(owner);
        return quests != null && quests.getOrDefault(quest, QuestProgress.EMPTY).everDone();
    }

    /** The same for the owner of the progress this player counts towards. */
    public static boolean isCompleted(ServerPlayer player, ResourceLocation quest) {
        return isCompleted(progressOwner(player), quest);
    }

    /**
     * Every quest an owner of progress has done, as {@link #isCompleted} counts them: a new set, which the caller may keep or
     * change. Empty for an owner without progress or without a running server.
     */
    public static Set<ResourceLocation> completed(UUID owner) {
        Map<ResourceLocation, QuestProgress> quests = progressOf(owner);
        Set<ResourceLocation> out = new HashSet<>();
        if (quests != null) {
            quests.forEach((id, progress) -> {
                if (progress.everDone()) {
                    out.add(id);
                }
            });
        }
        return out;
    }

    /** The same for the owner of the progress this player counts towards. */
    public static Set<ResourceLocation> completed(ServerPlayer player) {
        return completed(progressOwner(player));
    }

    /** The owner's saved progress without creating a record for an unknown owner, or null. */
    private static @Nullable Map<ResourceLocation, QuestProgress> progressOf(UUID owner) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null || owner == null ? null : ProgressStore.get(server).allProgress().get(owner);
    }

    /**
     * Marks an item of an achievement's collection as collected for an owner of progress, online or not (ADR-62). Meant for carrying
     * over marks a script kept before collections came to the book; marking twice changes nothing. KubeJS server scripts see this
     * class as {@code RYSQuests}.
     *
     * @param owner a team or player id as {@link #progressOwner} gives it, written as a string
     * @param achievement the achievement's id, such as {@code repys:collection_awakened_cores}
     * @param item an item id, or a tag written with {@code #}, as the collection names it
     * @return whether the item is collected now; false when there is no such collection, entry or owner
     */
    public static boolean collect(MinecraftServer server, String owner, String achievement, String item) {
        ResourceLocation id = ResourceLocation.tryParse(achievement);
        Achievement target = id == null ? null : book().achievements().get(id);
        UUID ownerId;
        try {
            ownerId = UUID.fromString(owner);
        } catch (IllegalArgumentException notAnId) {
            return false;
        }
        return target != null && ProgressService.collect(server, ownerId, target, item);
    }

    /** The same for the owner of the progress this player counts towards. */
    public static boolean collect(ServerPlayer player, String achievement, String item) {
        return collect(player.server, progressOwner(player).toString(), achievement, item);
    }
}
