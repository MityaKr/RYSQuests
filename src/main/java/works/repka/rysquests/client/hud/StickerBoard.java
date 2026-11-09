package works.repka.rysquests.client.hud;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import works.repka.rysquests.RYSQuests;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientConfig;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.data.Quest;

/**
 * Cards over the world, on the right edge below the minimap corner. The top edge is fixed and cards grow downwards;
 * RYScope's goal board keeps the left corner.
 */
public final class StickerBoard {
    public static final int WIDTH = 132;
    private static final int MARGIN = 4;
    private static final int GAP = 3;
    private static final int RECENT_DAMAGE_TICKS = 100;

    private StickerBoard() {
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "stickers"), StickerBoard::render);
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.options.hideGui || mc.player == null) {
            return;
        }
        boolean fighting = mc.player.tickCount - mc.player.getLastHurtByMobTimestamp() < RECENT_DAMAGE_TICKS && mc.player.getLastHurtByMob() != null;
        if (fighting && !ClientConfig.HUD_IN_COMBAT.get()) {
            return;
        }
        int x = g.guiWidth() - WIDTH - MARGIN;
        int y = g.guiHeight() / 4;
        List<Sticker> stickers = new ArrayList<>();
        for (var id : ClientProgress.pinned()) {
            Quest quest = ClientBook.current().quests().get(id);
            if (quest != null && !ClientProgress.get(id).completed()) {
                stickers.add(new QuestSticker(quest));
            }
        }
        stickers.sort(Comparator.comparingInt(Sticker::priority).reversed());
        for (Sticker sticker : stickers) {
            sticker.render(g, x, y, WIDTH);
            y += sticker.height(WIDTH) + GAP;
        }
    }
}
