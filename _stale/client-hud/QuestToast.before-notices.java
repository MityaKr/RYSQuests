package works.repka.rysquests.client.hud;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import works.repka.rysquests.client.input.QuestKeys;
import works.repka.rysquests.client.screen.TaskText;
import works.repka.rysquests.client.screen.Ui;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.RewardDef;
import works.repka.rysquests.reward.CurrencyReward;
import works.repka.rysquests.reward.ItemReward;
import works.repka.rysquests.reward.XpReward;

/**
 * "Квест выполнен" (mockup {@code .toast}, picture 31): under the stack, never top middle (the boss bar is there). The quest's icon on
 * its plate, the title, what waits in the book, and the book's key. A pinned quest raises none: its card changes its line instead.
 */
final class QuestToast implements Sticker {
    private static final int PAD_LEFT = 10;
    private static final int PAD_RIGHT = 14;
    private static final int PAD_Y = 10;
    private static final int ICON = 38;
    private static final int GAP = 11;
    private static final int MAX_W = 380;

    private final Quest quest;

    QuestToast(Quest quest) {
        this.quest = quest;
    }

    @Override
    public String id() {
        return "done:" + quest.id();
    }

    private String title() {
        return Component.translatable("rysquests.hud.done_title", Component.translatable(quest.title())).getString();
    }

    private String line() {
        List<String> parts = new ArrayList<>();
        for (RewardDef reward : quest.rewards()) {
            if (reward instanceof XpReward xp && xp.points().isPresent()) {
                parts.add(word("rysquests.plural.xp", xp.points().get()));
            } else if (reward instanceof XpReward xp && xp.levels().isPresent()) {
                parts.add(word("rysquests.plural.level", xp.levels().get()));
            } else if (reward instanceof CurrencyReward coins) {
                parts.add("coin".equals(coins.id()) ? word("rysquests.plural.coin", coins.amount()) : coins.amount() + " " + coins.id());
            } else if (reward instanceof ItemReward item) {
                parts.add(TaskText.stackOf(item.item()).getHoverName().getString() + (item.count() > 1 ? " ×" + item.count() : ""));
            }
        }
        if (parts.isEmpty()) {
            return Component.translatable("rysquests.hud.done_open").getString();
        }
        String joined = parts.size() == 1 ? parts.getFirst()
                : String.join(", ", parts.subList(0, parts.size() - 1)) + Component.translatable("rysquests.hud.and").getString() + parts.getLast();
        return Component.translatable("rysquests.hud.done_waiting", joined).getString();
    }

    /** "25 опыта", "1 монета": the word forms the book uses. */
    private static String word(String key, int count) {
        boolean russian = net.minecraft.client.Minecraft.getInstance().getLanguageManager().getSelected().startsWith("ru");
        int tens = count % 100;
        int units = count % 10;
        String form = !russian ? count == 1 ? "one" : "many" : tens >= 11 && tens <= 14 ? "many" : units == 1 ? "one" : units >= 2 && units <= 4 ? "few" : "many";
        String full = key + "." + form;
        return Language.getInstance().has(full) ? Component.translatable(full, count).getString() : String.valueOf(count);
    }

    @Override
    public int width(Ui ui) {
        float text = Math.max(ui.width(title(), 13, Ui.SEMIBOLD), ui.width(line(), 11.5f, Ui.REGULAR));
        float w = PAD_LEFT + ICON + GAP + text + 8 + 6 + HudLook.keyWidth(ui, QuestKeys.OPEN_BOOK) + PAD_RIGHT;
        return Math.round(Math.min(MAX_W, Math.max(StickerBoard.WIDTH, w)));
    }

    @Override
    public int height(Ui ui, int width, boolean folded) {
        return PAD_Y * 2 + ICON;
    }

    @Override
    public void render(Ui ui, int x, int y, int width, boolean folded) {
        int h = height(ui, width, folded);
        HudLook.plate(ui, x, y, width, h, 0.95f, true);
        float cy = y + h / 2f;
        float pen = x + PAD_LEFT;
        ui.box(pen, cy - ICON / 2f, ICON, ICON, 6, ui.c("slot.bg"), ui.c("slot.border"));
        ItemStack icon = quest.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY);
        ui.item(icon, pen + 5, cy - 14, 28);
        pen += ICON + GAP;
        float keyW = HudLook.keyWidth(ui, QuestKeys.OPEN_BOOK);
        float right = x + width - PAD_RIGHT;
        float textW = right - keyW - 6 - pen;
        float t1 = Ui.lineHeight(13);
        float t2 = Ui.lineHeight(11.5f);
        float top = cy - (t1 + t2) / 2f;
        ui.labelFit(title(), pen, top + t1 / 2f, textW, 13, Ui.SEMIBOLD, HudLook.primary(ui));
        ui.labelFit(line(), pen, top + t1 + t2 / 2f, textW, 11.5f, Ui.REGULAR, HudLook.muted(ui));
        HudLook.key(ui, QuestKeys.OPEN_BOOK, right, cy);
    }
}
