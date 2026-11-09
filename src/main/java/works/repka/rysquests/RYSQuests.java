package works.repka.rysquests;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import works.repka.rysquests.command.QuestCommands;
import works.repka.rysquests.data.BookHolder;
import works.repka.rysquests.data.BookLoader;
import works.repka.rysquests.dev.DevExamplePack;
import works.repka.rysquests.integration.ftbq.ImportedPack;
import works.repka.rysquests.network.ModNetwork;
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
        LOGGER.info("RYSQuests loaded");
    }

    private static void onDatapackSync(OnDatapackSyncEvent event) {
        event.getRelevantPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, BookHolder.current().payload()));
    }
}
