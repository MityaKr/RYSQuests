package works.repka.rysquests.client;

import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import works.repka.rysquests.client.integration.Ponders;
import works.repka.rysquests.client.screen.BookScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import works.repka.rysquests.RYSQuests;
import works.repka.rysquests.client.hud.StickerBoard;
import works.repka.rysquests.client.input.QuestKeys;
import works.repka.rysquests.client.screen.BookScreen;
import works.repka.rysquests.network.ModNetwork;
import works.repka.ryscore.dock.DockButton;
import works.repka.ryscore.ui.Icons;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/** Client-only entry point; never loaded on a dedicated server. */
@Mod(value = RYSQuests.MODID, dist = Dist.CLIENT)
public final class RYSQuestsClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(RYSQuestsClient.class);

    public RYSQuestsClient(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        modBus.addListener(RYSQuestsClient::onClientSetup);
        modBus.addListener(QuestKeys::register);
        modBus.addListener(StickerBoard::register);
        modBus.addListener((RegisterClientReloadListenersEvent event) -> event.registerReloadListener((ResourceManagerReloadListener) manager -> ClientAssets.applyLanguage()));
        ModNetwork.setClientBookHandler(ClientBook::receive);
        ModNetwork.setClientProgressHandler(ClientProgress::receive);
        ModNetwork.setClientPinnedHandler(ClientProgress::receivePinned);
        ModNetwork.setClientGuidePagesHandler(ClientProgress::receiveGuidePages);
        ModNetwork.setClientTeamInfoHandler(ClientProgress::receiveTeamInfo);
        ModNetwork.setClientStageLocksHandler(ClientProgress::receiveStageLocks);
        ModNetwork.setClientDevReportHandler(ClientDev::receive);
        ModNetwork.setClientAchievementHandler(ClientAchievements::receive);
        ModNetwork.setClientShopHandlers(ClientShop::receive, payload -> BookScreen.onShopResult(payload.ok(), payload.reason()));
        ModNetwork.setClientEditorResultHandler(payload -> BookScreen.onEditorResult(payload.ok(), payload.reason(), payload.args()));
        ModNetwork.setClientMessageHandler(ClientMessages::receive);
        ModNetwork.setClientLayoutBakeHandler(ClientLayout::onBakeRequest);
        ModNetwork.setClientImportHandlers(ClientImport::receiveProgress, ClientImport::receiveReport, ClientImport::receiveError);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> QuestKeys.tick());
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> Ponders.tick());
        NeoForge.EVENT_BUS.addListener(RYSQuestsClient::onClientCommands);
        NeoForge.EVENT_BUS.addListener(RYSQuestsClient::onSound);
        NeoForge.EVENT_BUS.addListener((ClientChatReceivedEvent.Player event) -> QuestLinks.onChat(event));
        NeoForge.EVENT_BUS.addListener((ClientChatReceivedEvent.System event) -> QuestLinks.onChat(event));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            ClientBook.clear();
            ClientProgress.clear();
            ClientShop.clear();
            ClientAchievements.clear();
            ClientMessages.clear();
            ClientLayout.clear();
            ClientImport.clear();
        });
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(RYSQuestsClient::registerDockButton);
        LOGGER.info("RYSQuests client setup complete");
    }

    /**
     * "Квесты" in the family's panel of buttons (RYSCore 1.9, plan section 4): without FTB Library the panel stands left of the
     * inventory; with FTB Library our button stays in FTB's own panel ({@code sidebar_buttons/book.json}) unless a host replaces it.
     */
    private static void registerDockButton() {
        DockButton.builder("rysquests_book")
                .label(() -> Component.translatable("rysquests.dock.label"))
                .description(() -> Component.translatable("rysquests.dock.description", QuestKeys.label(QuestKeys.OPEN_BOOK)))
                .tile(0xFFE9A83A)
                .icon(Icons.Shape.BOOK)
                .key(QuestKeys.OPEN_BOOK)
                .order(0)
                .replacesFtb("ftbquests_quests")
                .action(BookScreen::open)
                .register();
    }

    /** The FTB sidebar button runs this command: buttons there can only run commands or fire FTB's own events. */
    /** "Звук награды" off in the book's settings: the reward chime the server plays to the player stays silent. */
    private static void onSound(PlaySoundEvent event) {
        var sound = event.getSound();
        if (sound != null && !ClientConfig.REWARD_SOUND.get() && sound.getSource() == SoundSource.PLAYERS
                && SoundEvents.AMETHYST_BLOCK_CHIME.getLocation().equals(sound.getLocation())) {
            event.setSound(null);
        }
    }

    private static void onClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("rysquests_book")
                .executes(context -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(BookScreen::open);
                    return 1;
                })
                .then(Commands.literal("open").then(Commands.argument("quest", ResourceLocationArgument.id()).executes(context -> {
                    ResourceLocation quest = ResourceLocationArgument.getId(context, "quest");
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> BookScreen.openAt(quest));
                    return 1;
                }))));
    }
}
