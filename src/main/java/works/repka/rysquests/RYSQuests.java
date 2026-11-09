package works.repka.rysquests;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
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
import works.repka.rysquests.integration.ftbq.ImportedPack;
import works.repka.rysquests.migrate.MigrationRunner;
import works.repka.rysquests.network.ModNetwork;
import works.repka.rysquests.progress.ProgressService;
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
        modBus.addListener(ModNetwork::register);
        DevExamplePack.register(modBus);
        ImportedPack.register(modBus);

        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> event.addListener(new BookLoader(event.getRegistryAccess())));
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> QuestCommands.register(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener(RYSQuests::onDatapackSync);
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> MigrationRunner.applyNew(event.getServer()));
        NeoForge.EVENT_BUS.addListener(RYSQuests::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(RYSQuests::onLivingDeath);
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
            PacketDistributor.sendToPlayer(player, BookHolder.current().payload());
            ProgressService.sendAll(player);
        });
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ProgressService.tick(player);
        }
    }

    private static void onLivingDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            ProgressService.onKill(player, BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()));
        }
    }
}
