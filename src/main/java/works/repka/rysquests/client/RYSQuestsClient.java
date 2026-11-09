package works.repka.rysquests.client;

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

/** Client-only entry point; never loaded on a dedicated server. */
@Mod(value = RYSQuests.MODID, dist = Dist.CLIENT)
public final class RYSQuestsClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(RYSQuestsClient.class);

    public RYSQuestsClient(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        modBus.addListener(RYSQuestsClient::onClientSetup);
        modBus.addListener(QuestKeys::register);
        modBus.addListener(StickerBoard::register);
        ModNetwork.setClientBookHandler(ClientBook::receive);
        ModNetwork.setClientProgressHandler(ClientProgress::receive);
        ModNetwork.setClientPinnedHandler(ClientProgress::receivePinned);
        ModNetwork.setClientTeamInfoHandler(ClientProgress::receiveTeamInfo);
        ModNetwork.setClientStageLocksHandler(ClientProgress::receiveStageLocks);
        ModNetwork.setClientDevReportHandler(ClientDev::receive);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> QuestKeys.tick());
        NeoForge.EVENT_BUS.addListener(RYSQuestsClient::onClientCommands);
        NeoForge.EVENT_BUS.addListener((ClientChatReceivedEvent.Player event) -> QuestLinks.onChat(event));
        NeoForge.EVENT_BUS.addListener((ClientChatReceivedEvent.System event) -> QuestLinks.onChat(event));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            ClientBook.clear();
            ClientProgress.clear();
        });
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        LOGGER.info("RYSQuests client setup complete");
    }

    /** The FTB sidebar button runs this command: buttons there can only run commands or fire FTB's own events. */
    private static void onClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("rysquests_book")
                .executes(context -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> mc.setScreen(new BookScreen()));
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
