package works.repka.rysquests;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import works.repka.rysquests.command.QuestCommands;
import works.repka.rysquests.data.BookHolder;
import works.repka.rysquests.data.BookLoader;
import works.repka.rysquests.dev.DevExamplePack;
import works.repka.rysquests.editor.EditorFolder;
import works.repka.rysquests.integration.ftbq.ImportedPack;
import works.repka.rysquests.migrate.MigrationRunner;
import works.repka.rysquests.network.ModNetwork;
import works.repka.rysquests.progress.AchievementService;
import works.repka.rysquests.progress.ProgressService;
import works.repka.rysquests.progress.TeamBridge;
import works.repka.rysquests.reward.RewardTypes;
import works.repka.rysquests.task.TaskTypes;

/** Mod entry point shared by both physical sides. */
@Mod(RYSQuests.MODID)
public final class RYSQuests {
    public static final String MODID = "rysquests";

    private static final Logger LOGGER = LoggerFactory.getLogger(RYSQuests.class);

    public RYSQuests(IEventBus modBus, ModContainer container) {
        TaskTypes.REGISTER.register(modBus);
        RewardTypes.REGISTER.register(modBus);
        works.repka.rysquests.block.ModBlocks.register(modBus);
        modBus.addListener(ModNetwork::register);
        DevExamplePack.register(modBus);
        ImportedPack.register(modBus);
        EditorFolder.register(modBus);
        container.registerConfig(ModConfig.Type.COMMON, CommonConfig.SPEC);
        TeamBridge.register();
        works.repka.ryscore.migrate.Migrations.register(new works.repka.rysquests.integration.ftbq.QuestsMigration());

        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> event.addListener(new BookLoader(event.getRegistryAccess())));
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> QuestCommands.register(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener(RYSQuests::onDatapackSync);
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> MigrationRunner.applyNew(event.getServer()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent event) -> ProgressService.onServerStopped());
        NeoForge.EVENT_BUS.addListener(RYSQuests::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(works.repka.rysquests.shop.ShopService::onServerTick);
        NeoForge.EVENT_BUS.addListener(AchievementService::onNameFormat);
        NeoForge.EVENT_BUS.addListener(AchievementService::onTabListName);
        NeoForge.EVENT_BUS.addListener(RYSQuests::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(RYSQuests::onCrafted);
        NeoForge.EVENT_BUS.addListener(RYSQuests::onSmelted);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                ProgressService.forget(player);
            }
        });
        LOGGER.info("RYSQuests loaded");
    }

    private static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) {
            MigrationRunner.applyNew(event.getPlayerList().getServer());
        }
        event.getRelevantPlayers().forEach(player -> {
            PacketDistributor.sendToPlayer(player, BookHolder.current().payloadFor(player));
            ProgressService.sendAll(player);
        });
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ProgressService.tick(player);
        }
    }

    private static void onCrafted(net.neoforged.neoforge.event.entity.player.PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof net.neoforged.neoforge.common.util.FakePlayer)) {
            ProgressService.onCrafted(player, event.getCrafting());
        }
    }

    private static void onSmelted(net.neoforged.neoforge.event.entity.player.PlayerEvent.ItemSmeltedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof net.neoforged.neoforge.common.util.FakePlayer)) {
            ProgressService.onCrafted(player, event.getSmelting());
        }
    }

    private static void onLivingDeath(LivingDeathEvent event) {
        // Mob grinders and deployers kill as fake players: their kills belong to no one's quests.
        if (event.getSource().getEntity() instanceof ServerPlayer player && !(player instanceof net.neoforged.neoforge.common.util.FakePlayer)) {
            ProgressService.onKill(player, BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()));
        }
    }
}
