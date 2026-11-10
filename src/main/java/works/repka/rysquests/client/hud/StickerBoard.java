package works.repka.rysquests.client.hud;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import works.repka.rysquests.RYSQuests;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientConfig;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.client.input.QuestKeys;
import works.repka.rysquests.client.screen.Ui;
import works.repka.rysquests.data.MatrixSection;
import works.repka.rysquests.data.Quest;
import works.repka.ryscore.ui.Anchors;
import works.repka.ryscore.ui.Draw;
import works.repka.ryscore.ui.Kit;
import works.repka.ryscore.ui.UiScale;

/** Cards over the world, from {@code design/Hud.dc.html}. The corner is the family's shared setting. */
public final class StickerBoard {
    private static final int WIDTH = 300;
    private static final int MARGIN = 20;
    private static final int HEADER = 24;
    private static final int GAP = 10;
    private static final int RECENT_DAMAGE_TICKS = 100;
    private static final long DONE_MS = 5000;

    private record Done(QuestToast card, long until) {
    }

    private static final List<Done> DONE = new ArrayList<>();
    private static boolean collapsed;

    private StickerBoard() {
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "stickers"), StickerBoard::render);
    }

    public static void celebrate(Quest quest) {
        DONE.add(new Done(new QuestToast(quest), System.currentTimeMillis() + DONE_MS));
    }

    public static void toggleCollapsed() {
        collapsed = !collapsed;
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
        long now = System.currentTimeMillis();
        DONE.removeIf(done -> done.until < now);
        List<Sticker> stickers = new ArrayList<>();
        for (var id : ClientProgress.pinned()) {
            Quest quest = ClientBook.current().quests().get(id);
            if (quest != null && !ClientProgress.get(id).completed()) {
                stickers.add(new QuestSticker(quest));
            }
        }
        for (String key : ClientProgress.pinnedWays()) {
            MatrixSection.findPinned(ClientBook.current(), key).ifPresent(pinned -> stickers.add(new WaySticker(key, pinned)));
        }
        stickers.sort(Comparator.comparingInt(Sticker::priority).reversed());
        DONE.forEach(done -> stickers.add(done.card));
        if (stickers.isEmpty()) {
            return;
        }
        UiScale.render(g, scaled -> draw(scaled, stickers));
    }

    private static void draw(GuiGraphics g, List<Sticker> stickers) {
        Ui ui = new Ui(g, 0, 0, List.of(), List.of(), new Kit.Hits());
        int height = HEADER;
        if (!collapsed) {
            for (Sticker sticker : stickers) {
                height += sticker.height(ui, WIDTH) + GAP;
            }
        }
        int[] place = Anchors.place(Anchors.Element.PINNED_QUESTS, WIDTH / 2, (height + 1) / 2, MARGIN / 2, MARGIN / 2);
        int x = place[0] * 2;
        int y = place[1] * 2;
        int headerColor = Draw.alpha(0xFFFFFFFF, 0.85f);
        ui.label(Component.translatable("rysquests.board.count", stickers.size()).getString(), x + 4, y + 7, 11, Ui.REGULAR, headerColor);
        ui.labelRight(Component.translatable("rysquests.board.keys", QuestKeys.label(QuestKeys.OPEN_BOOK), QuestKeys.label(QuestKeys.BOARD)).getString(), x + WIDTH - 4, y + 7, 11, Ui.REGULAR, headerColor);
        if (collapsed) {
            return;
        }
        y += HEADER;
        for (Sticker sticker : stickers) {
            sticker.render(ui, x, y, WIDTH);
            y += sticker.height(ui, WIDTH) + GAP;
        }
    }
}
