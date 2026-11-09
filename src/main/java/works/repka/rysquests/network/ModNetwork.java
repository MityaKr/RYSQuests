package works.repka.rysquests.network;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import works.repka.rysquests.RYSQuests;
import works.repka.rysquests.progress.ProgressService;

/** Registers payloads. Client handlers are injected, so that this class never refers to client code. */
public final class ModNetwork {
    private static final String VERSION = "1";

    private static BiConsumer<BookSyncPayload, RegistryAccess> clientBookHandler = (payload, registries) -> {
    };
    private static Consumer<ProgressPayload> clientProgressHandler = payload -> {
    };
    private static Consumer<PinnedPayload> clientPinnedHandler = payload -> {
    };

    private ModNetwork() {
    }

    public static void setClientBookHandler(BiConsumer<BookSyncPayload, RegistryAccess> handler) {
        clientBookHandler = handler;
    }

    public static void setClientProgressHandler(Consumer<ProgressPayload> handler) {
        clientProgressHandler = handler;
    }

    public static void setClientPinnedHandler(Consumer<PinnedPayload> handler) {
        clientPinnedHandler = handler;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(RYSQuests.MODID).versioned(VERSION)
                .playToClient(BookSyncPayload.TYPE, BookSyncPayload.STREAM_CODEC,
                        (payload, context) -> clientBookHandler.accept(payload, context.player().registryAccess()))
                .playToClient(ProgressPayload.TYPE, ProgressPayload.STREAM_CODEC,
                        (payload, context) -> clientProgressHandler.accept(payload))
                .playToClient(PinnedPayload.TYPE, PinnedPayload.STREAM_CODEC,
                        (payload, context) -> clientPinnedHandler.accept(payload))
                .playToServer(QuestActionPayload.TYPE, QuestActionPayload.STREAM_CODEC,
                        (payload, context) -> ProgressService.handle((ServerPlayer) context.player(), payload));
    }
}
