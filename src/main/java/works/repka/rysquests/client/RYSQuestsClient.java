package works.repka.rysquests.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import works.repka.rysquests.RYSQuests;
import works.repka.rysquests.network.ModNetwork;

/** Client-only entry point; never loaded on a dedicated server. */
@Mod(value = RYSQuests.MODID, dist = Dist.CLIENT)
public final class RYSQuestsClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(RYSQuestsClient.class);

    public RYSQuestsClient(IEventBus modBus) {
        modBus.addListener(RYSQuestsClient::onClientSetup);
        ModNetwork.setClientBookHandler(ClientBook::receive);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ClientBook.clear());
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        LOGGER.info("RYSQuests client setup complete");
    }
}
