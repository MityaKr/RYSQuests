package works.repka.rysquests.client.hud;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.input.QuestKeys;
import works.repka.rysquests.client.screen.TaskText;
import works.repka.rysquests.client.screen.Ui;

/**
 * The key of the book on the very first entry (picture 31b): where the stack will stand, once, and gone after the book was first opened
 * ({@code ClientConfig.KEY_HINT_SHOWN}, kept on this computer).
 */
final class KeyHint implements Sticker {
    private static final int PAD_LEFT = 8;
    private static final int PAD_RIGHT = 12;
    private static final int PAD_Y = 8;
    private static final int ICON = 28;
    private static final int GAP = 11;

    @Override
    public String id() {
        return "key_hint";
    }

    @Override
    public int width(Ui ui) {
        float text = Math.max(ui.width(title(), 13, Ui.SEMIBOLD), ui.width(line(), 11.5f, Ui.REGULAR));
        return Math.round(PAD_LEFT + ICON + GAP + text + 8 + 6 + HudLook.keyWidth(ui, QuestKeys.OPEN_BOOK) + PAD_RIGHT);
    }

    private static String title() {
        return Component.translatable("rysquests.hud.hint_title").getString();
    }

    private static String line() {
        return Component.translatable("rysquests.hud.hint_line").getString();
    }

    @Override
    public int height(Ui ui, int width, boolean folded) {
        return PAD_Y * 2 + ICON;
    }

    @Override
    public void render(Ui ui, int x, int y, int width, boolean folded) {
        int w = width(ui);
        int h = height(ui, w, folded);
        HudLook.plate(ui, x, y, w, h, 0.95f, true);
        float cy = y + h / 2f;
        float pen = x + PAD_LEFT;
        ItemStack icon = ClientBook.current().meta().icon().map(TaskText::stackOf).orElseGet(() -> new ItemStack(Items.WRITABLE_BOOK));
        ui.item(icon, pen, cy - ICON / 2f, ICON);
        pen += ICON + GAP;
        float t1 = Ui.lineHeight(13);
        float t2 = Ui.lineHeight(11.5f);
        float top = cy - (t1 + t2) / 2f;
        ui.label(title(), pen, top + t1 / 2f, 13, Ui.SEMIBOLD, HudLook.primary(ui));
        ui.label(line(), pen, top + t1 + t2 / 2f, 11.5f, Ui.REGULAR, HudLook.muted(ui));
        HudLook.key(ui, QuestKeys.OPEN_BOOK, x + w - PAD_RIGHT, cy);
    }
}
