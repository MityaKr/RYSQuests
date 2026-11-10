package works.repka.rysquests.progress;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.data.Achievement;
import works.repka.rysquests.data.BookHolder;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.network.AchievementStatePayload;

/** Titles and how rare each tier is. Rarity goes through every record, so it is kept for a minute. */
public final class AchievementService {
    private static final long RARITY_MS = 60_000;

    @Nullable
    private static QuestBook rarityBook;
    private static Map<String, Integer> rarity = Map.of();
    private static long rarityAt;

    private AchievementService() {
    }

    /**
     * @return the share of players or teams who reached each tier, in percent. A record with nothing counted is left
     *         out: a team member's own record only says which rewards they took.
     */
    static Map<String, Integer> computeRarity(QuestBook book, Map<UUID, Map<ResourceLocation, QuestProgress>> records) {
        Map<String, Integer> out = new HashMap<>();
        long owners = records.values().stream().filter(AchievementService::counted).count();
        for (Achievement achievement : book.achievements().values()) {
            for (Achievement.Tier tier : achievement.tiers()) {
                var quest = achievement.questId(tier);
                long reached = records.values().stream().filter(own -> own.getOrDefault(quest, QuestProgress.EMPTY).completed()).count();
                out.put(quest.toString(), owners == 0 ? 0 : (int) Math.ceil(reached * 100.0 / owners));
            }
        }
        return out;
    }

    private static boolean counted(Map<ResourceLocation, QuestProgress> own) {
        return own.values().stream().anyMatch(row -> row.completed() || row.tasks().stream().anyMatch(value -> value > 0));
    }

    private static Map<String, Integer> rarity(MinecraftServer server) {
        QuestBook book = BookHolder.current().book();
        long now = System.currentTimeMillis();
        if (book != rarityBook || now - rarityAt > RARITY_MS) {
            rarityBook = book;
            rarityAt = now;
            rarity = computeRarity(book, ProgressStore.get(server).allProgress());
        }
        return rarity;
    }

    /** The next look at rarity counts again, after someone has reached a tier. */
    static void invalidateRarity() {
        rarityAt = 0;
    }

    public static void send(ServerPlayer player) {
        ProgressStore store = ProgressStore.get(player.server);
        PacketDistributor.sendToPlayer(player, new AchievementStatePayload(rarity(player.server), List.copyOf(store.titles(player.getUUID())), store.activeTitle(player.getUUID())));
    }

    public static void grantTitle(ServerPlayer player, String title) {
        ProgressStore.get(player.server).addTitle(player.getUUID(), title);
        send(player);
    }

    public static void takeTitle(ServerPlayer player, String title) {
        ProgressStore.get(player.server).removeTitle(player.getUUID(), title);
        player.refreshDisplayName();
        player.refreshTabListName();
        send(player);
    }

    /** Only a title the player owns can be worn. */
    public static void select(ServerPlayer player, String title) {
        ProgressStore store = ProgressStore.get(player.server);
        Set<String> owned = store.titles(player.getUUID());
        if (title.isEmpty() || owned.contains(title)) {
            store.setActiveTitle(player.getUUID(), title);
            player.refreshDisplayName();
            player.refreshTabListName();
            send(player);
        }
    }

    private static Component decorated(Component name, ServerPlayer player) {
        String title = ProgressStore.get(player.server).activeTitle(player.getUUID());
        return title.isEmpty() ? name : Component.literal("[").append(Component.translatable(title)).append("] ").append(name);
    }

    public static void onNameFormat(PlayerEvent.NameFormat event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            event.setDisplayname(decorated(event.getDisplayname(), player));
        }
    }

    public static void onTabListName(PlayerEvent.TabListNameFormat event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Component base = event.getDisplayName() != null ? event.getDisplayName() : player.getName();
            String title = ProgressStore.get(player.server).activeTitle(player.getUUID());
            if (!title.isEmpty()) {
                event.setDisplayName(decorated(base, player));
            }
        }
    }
}
