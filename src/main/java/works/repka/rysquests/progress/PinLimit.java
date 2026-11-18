package works.repka.rysquests.progress;

import java.util.List;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.data.Quest;

/**
 * The limit of pinned cards ({@code hud.max_pinned} of the book), one rule for the server that refuses and the book that greys the pin
 * out beforehand. Quests and ways of matrix sections share it; a finished quest leaves the board and frees its place, a repeatable one
 * keeps it, since it comes back after its round.
 */
public final class PinLimit {
    private PinLimit() {
    }

    /** The pinned quests that still take a place on the board. */
    public static int taken(List<ResourceLocation> pinned, Function<ResourceLocation, @Nullable Quest> quests, Function<ResourceLocation, QuestProgress> progress) {
        int count = 0;
        for (ResourceLocation id : pinned) {
            Quest quest = quests.apply(id);
            if (quest != null && (quest.options().repeatable().isPresent() || !progress.apply(id).completed())) {
                count++;
            }
        }
        return count;
    }

    /**
     * Whether a press of the pin would pin this quest: a pinned one is unpinned whatever the limit, another needs a free place.
     *
     * @param ways how many ways of matrix sections are pinned
     */
    public static boolean canPin(ResourceLocation quest, List<ResourceLocation> pinned, int ways, int limit, Function<ResourceLocation, @Nullable Quest> quests,
            Function<ResourceLocation, QuestProgress> progress) {
        return pinned.contains(quest) || taken(pinned, quests, progress) + ways < limit;
    }
}
