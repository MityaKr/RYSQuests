package works.repka.rysquests.network;

import java.util.function.BiConsumer;
import net.minecraft.core.RegistryAccess;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import works.repka.rysquests.RYSQuests;

/** Registers payloads. Client handlers are injected, so that this class never refers to client code. */
public final class ModNetwork {
    private static final String VERSION = "1";

    private static BiConsumer<BookSyncPayload, RegistryAccess> clientBookHandler = (payload, registries) -> {
    };

    private ModNetwork() {
    }

    public static void setClientBookHandler(BiConsumer<BookSyncPayload, RegistryAccess> handler) {
        clientBookHandler = handler;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(RYSQuests.MODID).versioned(VERSION).playToClient(BookSyncPayload.TYPE, BookSyncPayload.STREAM_CODEC,
                (payload, context) -> clientBookHandler.accept(payload, context.player().registryAccess()));
    }
}
