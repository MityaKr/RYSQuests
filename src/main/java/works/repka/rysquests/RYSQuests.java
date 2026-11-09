package works.repka.rysquests;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import works.repka.rysquests.dev.DevExamplePack;

/** Mod entry point shared by both physical sides. */
@Mod(RYSQuests.MODID)
public final class RYSQuests {
    public static final String MODID = "rysquests";

    private static final Logger LOGGER = LoggerFactory.getLogger(RYSQuests.class);

    public RYSQuests(IEventBus modBus, ModContainer container) {
        DevExamplePack.register(modBus);
        LOGGER.info("RYSQuests loaded");
    }
}
