package works.repka.rysquests.progress;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;
import works.repka.ryscore.team.TeamsApi;
import works.repka.rysquests.RYSQuests;
import works.repka.rysquests.shop.ShopLimits;
import works.repka.rysquests.shop.ShopService;

/** Moves quest progress when a team mod, through RYSCore, reports that teams changed. */
public final class TeamBridge implements TeamsApi.Listener {
    private static final ResourceLocation SOURCE = ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "quests");

    /** Each side of a merge as it was, for the short window in which a merge can be undone. */
    private final Map<UUID, Map<ResourceLocation, QuestProgress>> beforeMerge = new HashMap<>();
    /** What each side had used of the shop's limits before a merge. */
    private final Map<UUID, Map<String, ShopLimits.Usage>> usageBeforeMerge = new HashMap<>();

    public static void register() {
        TeamsApi.listen(new TeamBridge());
    }

    @Nullable
    private static ProgressStore store() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : ProgressStore.get(server);
    }

    private static void resync() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            ProgressService.invalidateAll();
            server.getPlayerList().getPlayers().forEach(ProgressService::sendAll);
        }
    }

    @Override
    public void onJoined(UUID team, UUID player, boolean carryProgress) {
        ProgressStore store = store();
        if (store != null && carryProgress) {
            store.mergeInto(player, team);
        }
        // Limits move whatever the team's rule on progress says: they guard against buying twice, not progress.
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            ShopService.joinedTeam(server, team, player);
        }
        resync();
    }

    @Override
    public void onLeft(UUID team, UUID player, boolean keepCopy) {
        ProgressStore store = store();
        if (store != null && keepCopy) {
            store.copyTeam(team, player);
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            ShopService.leftTeam(server, team, player);
        }
        resync();
    }

    @Override
    public List<TeamsApi.MergeLine> previewMerge(UUID from, UUID into) {
        ProgressStore store = store();
        int quests = store == null ? 0 : store.team(from).size();
        return List.of(new TeamsApi.MergeLine(SOURCE, Component.translatable("rysquests.team.merge_preview", quests), false));
    }

    @Override
    public void onMerged(UUID from, UUID into) {
        ProgressStore store = store();
        if (store != null) {
            beforeMerge.put(from, new HashMap<>(store.team(from)));
            beforeMerge.put(into, new HashMap<>(store.team(into)));
            usageBeforeMerge.put(from, store.copyShopUsage(from));
            usageBeforeMerge.put(into, store.copyShopUsage(into));
            store.mergeInto(from, into);
            ShopService.mergedTeams(ServerLifecycleHooks.getCurrentServer(), from, into);
        }
        resync();
    }

    @Override
    public void onMergeUndone(UUID from, UUID into) {
        ProgressStore store = store();
        if (store != null && beforeMerge.containsKey(from) && beforeMerge.containsKey(into)) {
            store.replaceTeam(from, beforeMerge.remove(from));
            store.replaceTeam(into, beforeMerge.remove(into));
        }
        if (store != null && usageBeforeMerge.containsKey(from) && usageBeforeMerge.containsKey(into)) {
            store.replaceShopUsage(from, usageBeforeMerge.remove(from));
            store.replaceShopUsage(into, usageBeforeMerge.remove(into));
        }
        resync();
    }
}
