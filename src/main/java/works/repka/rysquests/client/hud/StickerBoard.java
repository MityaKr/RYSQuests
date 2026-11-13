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
import works.repka.rysquests.client.screen.BookScreen;
import works.repka.rysquests.client.screen.Ui;
import works.repka.rysquests.data.MatrixSection;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.progress.QuestLogic;
import works.repka.rysquests.progress.QuestStatus;
import works.repka.ryscore.ui.Anchors;
import works.repka.ryscore.ui.Kit;
import works.repka.ryscore.ui.UiScale;

/**
 * Everything the book draws over the game, in one place (plan, section 3, pictures 31, 31b): one stack of pinned cards in the corner
 * {@link Anchors} gives, the message about a finished quest under it. The top middle (the boss bar) and the room over the hotbar (the
 * item's name, the action line) stay free. The scale never goes under 75 %; when the window is low (under 540 pixels) the stack holds
 * two cards instead of three. A pinned quest that is done stays and says "награда ждёт · K" instead of raising a message. On the very
 * first entry, before the book was ever opened, a hint of its key stands where the stack will be.
 */
public final class StickerBoard {
    static final int WIDTH = 250;
    private static final int MARGIN = 14;
    private static final int GAP = 10;
    private static final int RECENT_DAMAGE_TICKS = 100;
    private static final long DONE_MS = 5000;
    private static final double MIN_PIXELS = 1.5;
    private static final int LOW_WINDOW = 540;
    private static final int CARDS = 3;
    private static final int CARDS_LOW = 2;
    private static final int TOASTS = 2;

    private record Done(QuestToast card, long until) {
    }

    private static final List<Done> DONE = new ArrayList<>();
    private static boolean collapsed;

    private StickerBoard() {
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "stickers"), StickerBoard::render);
    }

    /** A quest got finished. A pinned one raises no message: its card changes its line by itself. */
    public static void celebrate(Quest quest) {
        if (ClientProgress.pinned().contains(quest.id())) {
            return;
        }
        DONE.add(new Done(new QuestToast(quest), System.currentTimeMillis() + DONE_MS));
    }

    public static void toggleCollapsed() {
        collapsed = !collapsed;
    }

    /** How many cards the stack holds: two when the window is low. */
    static int capacity() {
        int most = Minecraft.getInstance().getWindow().getHeight() < LOW_WINDOW ? CARDS_LOW : CARDS;
        int book = ClientBook.current().meta().hud().maxPinned();
        return book > 0 ? Math.min(most, book) : most;
    }

    /** The cards of the stack, in their order, at most {@link #capacity}. */
    static List<Sticker> cards() {
        List<Sticker> stickers = new ArrayList<>();
        if (ClientConfig.HUD_PINNED.get()) {
            for (ResourceLocation id : ClientProgress.pinned()) {
                Quest quest = ClientBook.current().quests().get(id);
                if (quest != null && QuestLogic.status(quest, ClientProgress::get) != QuestStatus.CLAIMED) {
                    stickers.add(new QuestSticker(quest));
                }
            }
            for (String key : ClientProgress.pinnedWays()) {
                MatrixSection.findPinned(ClientBook.current(), key).ifPresent(pinned -> stickers.add(new WaySticker(key, pinned)));
            }
        }
        stickers.sort(Comparator.comparingInt(Sticker::priority).reversed());
        return stickers.size() > capacity() ? List.copyOf(stickers.subList(0, capacity())) : stickers;
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof BookScreen && !ClientConfig.KEY_HINT_SHOWN.get()) {
            ClientConfig.KEY_HINT_SHOWN.set(true);
            ClientConfig.KEY_HINT_SHOWN.save();
        }
        if (mc.screen != null || mc.options.hideGui || mc.player == null) {
            return;
        }
        boolean fighting = mc.player.tickCount - mc.player.getLastHurtByMobTimestamp() < RECENT_DAMAGE_TICKS && mc.player.getLastHurtByMob() != null;
        if (fighting && !ClientConfig.HUD_IN_COMBAT.get()) {
            return;
        }
        long now = System.currentTimeMillis();
        DONE.removeIf(done -> done.until < now);
        List<Sticker> stack = new ArrayList<>();
        if (!ClientConfig.KEY_HINT_SHOWN.get() && !ClientBook.current().quests().isEmpty()) {
            stack.add(new KeyHint());
        }
        stack.addAll(cards());
        List<Sticker> toasts = new ArrayList<>();
        if (ClientConfig.COMPLETION_TOAST.get()) {
            for (int i = Math.max(0, DONE.size() - TOASTS); i < DONE.size(); i++) {
                toasts.add(DONE.get(i).card);
            }
        }
        if (stack.isEmpty() && toasts.isEmpty()) {
            return;
        }
        UiScale.render(g, scaled -> draw(scaled, stack, toasts));
    }

    private static void draw(GuiGraphics g, List<Sticker> stack, List<Sticker> toasts) {
        // The family's scale, but never under 75 %: the cards stay readable on a small window.
        double pixels = UiScale.pixels();
        float k = pixels < MIN_PIXELS ? (float) (MIN_PIXELS / pixels) : 1f;
        g.pose().pushPose();
        g.pose().scale(k, k, 1f);
        Ui ui = new Ui(g, 0, 0, List.of(), List.of(), new Kit.Hits());
        int height = 0;
        int widest = WIDTH;
        for (Sticker sticker : stack) {
            height += sticker.height(ui, WIDTH, collapsed) + GAP;
        }
        for (Sticker toast : toasts) {
            height += toast.height(ui, toast.width(ui), false) + GAP;
            widest = Math.max(widest, toast.width(ui));
        }
        int[] place = Anchors.place(Anchors.Element.PINNED_QUESTS, Math.round(widest * k / 2f), Math.round(height * k / 2f), MARGIN / 2, MARGIN / 2);
        float screenW = g.guiWidth() * 2 / k;
        int x = ClientConfig.HUD_RIGHT.get() ? Math.round(screenW - widest - MARGIN) : Math.round(place[0] * 2 / k);
        int y = Math.round(place[1] * 2 / k);
        for (Sticker sticker : stack) {
            sticker.render(ui, x, y, WIDTH, collapsed);
            y += sticker.height(ui, WIDTH, collapsed) + GAP;
        }
        for (Sticker toast : toasts) {
            int w = toast.width(ui);
            toast.render(ui, ClientConfig.HUD_RIGHT.get() ? x + widest - w : x, y, w, false);
            y += toast.height(ui, w, false) + GAP;
        }
        g.pose().popPose();
    }

    /** For the in-game autotest: a finished quest's message without finishing it (picture 31). */
    public static void celebrateForTest(Quest quest) {
        DONE.add(new Done(new QuestToast(quest), System.currentTimeMillis() + DONE_MS * 4));
    }

    /** For the in-game autotest: how many cards the stack shows now. */
    public static int cardsForTest() {
        return cards().size();
    }

    public static void clearForTest() {
        DONE.clear();
    }
}
