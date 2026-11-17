package works.repka.rysquests.network;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import works.repka.rysquests.RYSQuests;
import works.repka.rysquests.editor.EditorService;
import works.repka.rysquests.integration.ftbq.FtbqImportService;
import works.repka.rysquests.shop.ShopService;
import works.repka.rysquests.progress.AchievementService;
import works.repka.rysquests.progress.ProgressService;

/** Registers payloads. Client handlers are injected, so that this class never refers to client code. */
public final class ModNetwork {
    // 11: the shop's state carries what the team delivered, and a request to deliver (0.2.7).
    private static final String VERSION = "11";

    private static BiConsumer<BookSyncPayload, RegistryAccess> clientBookHandler = (payload, registries) -> {
    };
    private static Consumer<ProgressPayload> clientProgressHandler = payload -> {
    };
    private static Consumer<PinnedPayload> clientPinnedHandler = payload -> {
    };
    private static Consumer<TeamInfoPayload> clientTeamInfoHandler = payload -> {
    };
    private static Consumer<StageLocksPayload> clientStageLocksHandler = payload -> {
    };
    private static Consumer<DevReportPayload> clientDevReportHandler = payload -> {
    };

    private static Consumer<GuidePagesPayload> clientGuidePagesHandler = payload -> {
    };

    private static Consumer<EditorResultPayload> clientEditorResultHandler = payload -> {
    };

    private static Consumer<ShopStatePayload> clientShopStateHandler = payload -> {
    };
    private static Consumer<ShopResultPayload> clientShopResultHandler = payload -> {
    };

    private static Consumer<AchievementStatePayload> clientAchievementHandler = payload -> {
    };

    private static Consumer<BookMessagePayload> clientMessageHandler = payload -> {
    };
    private static Consumer<LayoutBakeRequestPayload> clientLayoutBakeHandler = payload -> {
    };
    private static Consumer<FtbqImportProgressPayload> clientImportProgressHandler = payload -> {
    };
    private static Consumer<FtbqImportReportPayload> clientImportReportHandler = payload -> {
    };
    private static Consumer<FtbqImportErrorPayload> clientImportErrorHandler = payload -> {
    };

    private ModNetwork() {
    }

    public static void setClientBookHandler(BiConsumer<BookSyncPayload, RegistryAccess> handler) {
        clientBookHandler = handler;
    }

    public static void setClientProgressHandler(Consumer<ProgressPayload> handler) {
        clientProgressHandler = handler;
    }

    public static void setClientDevReportHandler(Consumer<DevReportPayload> handler) {
        clientDevReportHandler = handler;
    }

    public static void setClientStageLocksHandler(Consumer<StageLocksPayload> handler) {
        clientStageLocksHandler = handler;
    }

    public static void setClientTeamInfoHandler(Consumer<TeamInfoPayload> handler) {
        clientTeamInfoHandler = handler;
    }

    public static void setClientGuidePagesHandler(Consumer<GuidePagesPayload> handler) {
        clientGuidePagesHandler = handler;
    }

    public static void setClientEditorResultHandler(Consumer<EditorResultPayload> handler) {
        clientEditorResultHandler = handler;
    }

    public static void setClientShopHandlers(Consumer<ShopStatePayload> state, Consumer<ShopResultPayload> result) {
        clientShopStateHandler = state;
        clientShopResultHandler = result;
    }

    public static void setClientAchievementHandler(Consumer<AchievementStatePayload> handler) {
        clientAchievementHandler = handler;
    }

    public static void setClientPinnedHandler(Consumer<PinnedPayload> handler) {
        clientPinnedHandler = handler;
    }

    /** The server's word about what the player did, for the book's message line. */
    public static void setClientMessageHandler(Consumer<BookMessagePayload> handler) {
        clientMessageHandler = handler;
    }

    /** The answer to {@code /rysquests layout bake}: lay the map out and send it back. */
    public static void setClientLayoutBakeHandler(Consumer<LayoutBakeRequestPayload> handler) {
        clientLayoutBakeHandler = handler;
    }

    /** How the FTB Quests import goes: its steps, its report, or where it stopped. */
    public static void setClientImportHandlers(Consumer<FtbqImportProgressPayload> progress, Consumer<FtbqImportReportPayload> report, Consumer<FtbqImportErrorPayload> error) {
        clientImportProgressHandler = progress;
        clientImportReportHandler = report;
        clientImportErrorHandler = error;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(RYSQuests.MODID).versioned(VERSION)
                .playToClient(BookSyncPayload.TYPE, BookSyncPayload.STREAM_CODEC,
                        (payload, context) -> clientBookHandler.accept(payload, context.player().registryAccess()))
                .playToClient(ProgressPayload.TYPE, ProgressPayload.STREAM_CODEC,
                        (payload, context) -> clientProgressHandler.accept(payload))
                .playToClient(PinnedPayload.TYPE, PinnedPayload.STREAM_CODEC,
                        (payload, context) -> clientPinnedHandler.accept(payload))
                .playToClient(TeamInfoPayload.TYPE, TeamInfoPayload.STREAM_CODEC,
                        (payload, context) -> clientTeamInfoHandler.accept(payload))
                .playToClient(StageLocksPayload.TYPE, StageLocksPayload.STREAM_CODEC,
                        (payload, context) -> clientStageLocksHandler.accept(payload))
                .playToClient(GuidePagesPayload.TYPE, GuidePagesPayload.STREAM_CODEC,
                        (payload, context) -> clientGuidePagesHandler.accept(payload))
                .playToClient(DevReportPayload.TYPE, DevReportPayload.STREAM_CODEC,
                        (payload, context) -> clientDevReportHandler.accept(payload))
                .playToClient(EditorResultPayload.TYPE, EditorResultPayload.STREAM_CODEC,
                        (payload, context) -> clientEditorResultHandler.accept(payload))
                .playToClient(ShopStatePayload.TYPE, ShopStatePayload.STREAM_CODEC,
                        (payload, context) -> clientShopStateHandler.accept(payload))
                .playToClient(ShopResultPayload.TYPE, ShopResultPayload.STREAM_CODEC,
                        (payload, context) -> clientShopResultHandler.accept(payload))
                .playToServer(ShopTradePayload.TYPE, ShopTradePayload.STREAM_CODEC,
                        (payload, context) -> ShopService.handle((ServerPlayer) context.player(), payload))
                .playToServer(ShopDeliverPayload.TYPE, ShopDeliverPayload.STREAM_CODEC,
                        (payload, context) -> ShopService.handleDeliver((ServerPlayer) context.player(), payload))
                .playToClient(AchievementStatePayload.TYPE, AchievementStatePayload.STREAM_CODEC,
                        (payload, context) -> clientAchievementHandler.accept(payload))
                .playToServer(SelectTitlePayload.TYPE, SelectTitlePayload.STREAM_CODEC,
                        (payload, context) -> AchievementService.select((ServerPlayer) context.player(), payload.title()))
                .playToServer(QuestEditorSavePayload.TYPE, QuestEditorSavePayload.STREAM_CODEC,
                        (payload, context) -> EditorService.handleQuests((ServerPlayer) context.player(), payload))
                .playToServer(EditorSavePayload.TYPE, EditorSavePayload.STREAM_CODEC,
                        (payload, context) -> EditorService.handle((ServerPlayer) context.player(), payload))
                .playToServer(BookEditsSavePayload.TYPE, BookEditsSavePayload.STREAM_CODEC,
                        (payload, context) -> EditorService.handleBookEdits((ServerPlayer) context.player(), payload))
                .playToServer(PinWayPayload.TYPE, PinWayPayload.STREAM_CODEC,
                        (payload, context) -> ProgressService.togglePinnedWay((ServerPlayer) context.player(), payload.key()))
                .playToServer(QuestActionPayload.TYPE, QuestActionPayload.STREAM_CODEC,
                        (payload, context) -> ProgressService.handle((ServerPlayer) context.player(), payload))
                .playToClient(BookMessagePayload.TYPE, BookMessagePayload.STREAM_CODEC,
                        (payload, context) -> clientMessageHandler.accept(payload))
                .playToServer(LayoutSavePayload.TYPE, LayoutSavePayload.STREAM_CODEC,
                        (payload, context) -> EditorService.handleLayout((ServerPlayer) context.player(), payload))
                .playToClient(LayoutBakeRequestPayload.TYPE, LayoutBakeRequestPayload.STREAM_CODEC,
                        (payload, context) -> clientLayoutBakeHandler.accept(payload))
                .playToServer(PublishPayload.TYPE, PublishPayload.STREAM_CODEC,
                        (payload, context) -> EditorService.handlePublish((ServerPlayer) context.player(), payload))
                .playToServer(MapFormPayload.TYPE, MapFormPayload.STREAM_CODEC,
                        (payload, context) -> EditorService.handleMapForm((ServerPlayer) context.player(), payload))
                .playToServer(NewBookPayload.TYPE, NewBookPayload.STREAM_CODEC,
                        (payload, context) -> EditorService.handleNewBook((ServerPlayer) context.player(), payload))
                .playToServer(FtbqImportStartPayload.TYPE, FtbqImportStartPayload.STREAM_CODEC,
                        (payload, context) -> FtbqImportService.handle((ServerPlayer) context.player(), payload))
                .playToClient(FtbqImportProgressPayload.TYPE, FtbqImportProgressPayload.STREAM_CODEC,
                        (payload, context) -> clientImportProgressHandler.accept(payload))
                .playToClient(FtbqImportReportPayload.TYPE, FtbqImportReportPayload.STREAM_CODEC,
                        (payload, context) -> clientImportReportHandler.accept(payload))
                .playToClient(FtbqImportErrorPayload.TYPE, FtbqImportErrorPayload.STREAM_CODEC,
                        (payload, context) -> clientImportErrorHandler.accept(payload));
    }
}
