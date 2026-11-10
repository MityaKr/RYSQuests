package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.client.integration.Recipes;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.progress.QuestStatus;
import works.repka.ryscore.ui.Icons;
import works.repka.ryscore.ui.Numbers;

/** The right side of the flow: how to do the quest, its recipe, and why what comes next is closed. */
final class ReadingColumn {
    private static final int PAD_X = 26;
    private static final int PAD_Y = 22;
    private static final int GAP = 18;
    private static final float BLOCKED_LINE = 20.9f;

    private final BookScreen screen;
    private final Scroll scroll = new Scroll();

    ReadingColumn(BookScreen screen) {
        this.screen = screen;
    }

    void scroll(double notches, float step) {
        scroll.wheel(notches, step);
    }

    void render(Ui ui, int x, int y, int w, int h) {
        Quest quest = BookState.quest == null ? null : ClientBook.current().quests().get(BookState.quest);
        if (quest == null) {
            return;
        }
        float left = x + PAD_X;
        float inner = w - PAD_X * 2;
        ui.clip(x, y, w - 1, h);
        int offset = scroll.offset();
        float top = y + PAD_Y - offset;
        float cursor = top;
        if (quest.text().isPresent()) {
            ui.caps(Component.translatable("rysquests.reading.how").getString(), left, cursor + 7, ui.c("text.dim"));
            cursor += 14 + GAP;
            cursor = TextBlock.render(ui, Component.translatable(quest.text().get()).getString(), left, cursor, inner,
                    new TextBlock.Look(14.5f, 23.9f, ui.readingFg()), Recipes::showRecipes) + GAP;
        }
        float belowButton = QuestCard.guideButton(ui, quest, left, cursor);
        cursor = belowButton > cursor ? belowButton + GAP - 8 : cursor;
        cursor = renderRecipe(ui, quest, left, cursor, inner);
        cursor = renderBlocked(ui, quest, left, cursor, inner);
        ui.unclip();
        scroll.content(cursor + offset - y + PAD_Y, h);
    }

    private float renderRecipe(Ui ui, Quest quest, float x, float y, float w) {
        ItemStack target = QuestCard.recipeTarget(quest);
        RecipePreview.Shown recipe = RecipePreview.of(target);
        if (recipe == null) {
            return y;
        }
        float h = 105;
        ui.box(x, y, w, h, 12, ui.c("status.bg"), ui.flowBorder());
        float cy = y + 22.5f;
        ui.label(Component.translatable("rysquests.reading.recipe", RecipePreview.viewerName()).getString(), x + 15, cy, 12, Ui.REGULAR, ui.c("text.muted"));
        ItemStack missing = RecipePreview.firstMissing(recipe);
        if (!missing.isEmpty()) {
            String need = missing.getHoverName().getString() + " ×" + Numbers.compact(missing.getCount());
            float needW = ui.width(need, 12, Ui.REGULAR);
            ui.labelRight(need, x + w - 15, cy, 12, Ui.REGULAR, ui.c("text.primary"));
            ui.labelRight(Component.translatable("rysquests.reading.missing").getString(), x + w - 15 - needW - 4, cy, 12, Ui.REGULAR, ui.c("text.muted"));
        }
        float rowY = y + 40;
        float cellY = rowY + 4;
        float cursor = x + 15;
        String where = missing.isEmpty() ? "" : Component.translatable("rysquests.reading.where", missing.getHoverName().getString()).getString();
        float buttonW = missing.isEmpty() ? 0 : ui.buttonWidth(where, 13, Ui.REGULAR, 12) + 2;
        float room = w - 30 - buttonW - 16 - 16 - 50;
        int cells = Math.max(1, (int) ((room + 8) / 50f));
        for (int i = 0; i < Math.min(cells, recipe.inputs().size()); i++) {
            ItemStack input = recipe.inputs().get(i);
            boolean have = RecipePreview.held(input) >= input.getCount();
            ui.cell(input, cursor, cellY, 42, 42, 9, 24, ui.c("slot.bg"), have ? ui.c("slot.border") : ui.warmBorder(0.19f));
            ui.count(input.getCount(), cursor + 38, cellY + 40);
            if (!have) {
                ui.veil(cursor, cellY, 42, 42, 9, ui.c("status.bg"), 0.5f);
            }
            cursor += 50;
        }
        ui.icon(Icons.Shape.ARROW, cursor + 2, rowY + 17, 16, ui.c("text.dim"));
        cursor += 27.5f;
        ui.cell(recipe.output(), cursor, rowY, 50, 50, 11, 28, ui.c("slot.bg"), ui.c("card.border"));
        if (!missing.isEmpty()) {
            ui.button(where, x + w - 15 - buttonW, rowY + 7, buttonW, 36, 9, 13, Ui.REGULAR, 12, Ui.Style.PLAIN, () -> Recipes.showRecipes(missing));
        }
        return y + h + GAP;
    }

    private float renderBlocked(Ui ui, Quest quest, float x, float y, float w) {
        List<Quest> blocked = new ArrayList<>();
        for (ResourceLocation id : ClientBook.current().unlocks().getOrDefault(quest.id(), List.of())) {
            Quest next = ClientBook.current().quests().get(id);
            if (next != null && BookState.status(next) == QuestStatus.LOCKED) {
                blocked.add(next);
            }
        }
        if (BookState.status(quest) == QuestStatus.LOCKED) {
            ui.caps(Component.translatable("rysquests.reading.locked").getString(), x, y + 7, ui.c("text.dim"));
            y += 14 + 8;
            y = row(ui, Component.translatable(quest.title()).getString(), BookScreen.waitingFor(quest).getString(), x, y, w) + GAP;
        }
        if (!blocked.isEmpty()) {
            ui.caps(Component.translatable("rysquests.reading.next_locked").getString(), x, y + 7, ui.c("text.dim"));
            y += 14 + 8;
            for (Quest next : blocked) {
                y = row(ui, Component.translatable(next.title()).getString(), reason(next, quest), x, y, w) + 6;
            }
            y += GAP - 6;
        }
        return y;
    }

    private static String reason(Quest next, Quest current) {
        boolean onlyThis = next.requires().stream().allMatch(id -> id.equals(current.id()) || ClientProgress.get(id).completed());
        boolean stage = ClientProgress.isStageLocked(next.id()) && next.stageRequired().isPresent();
        if (onlyThis && !stage) {
            return Component.translatable("rysquests.reading.only_this").getString();
        }
        return BookScreen.waitingFor(next).getString();
    }

    private static float row(Ui ui, String name, String why, float x, float y, float w) {
        float[] widths = Ui.shrink(w, 10, new float[] {ui.width(name, 13.5f, Ui.REGULAR), ui.width(why, 13.5f, Ui.REGULAR)},
                new float[] {ui.minWidth(name, 13.5f, Ui.REGULAR), ui.minWidth(why, 13.5f, Ui.REGULAR)});
        List<String> left = ui.wrap(name, widths[0] + 1, 13.5f, Ui.REGULAR, 6);
        List<String> right = ui.wrap(why, widths[1] + 1, 13.5f, Ui.REGULAR, 6);
        for (int i = 0; i < left.size(); i++) {
            ui.label(left.get(i), x, y + BLOCKED_LINE * (i + 0.5f), 13.5f, Ui.REGULAR, ui.c("text.muted"));
        }
        for (int i = 0; i < right.size(); i++) {
            ui.label(right.get(i), x + widths[0] + 10, y + BLOCKED_LINE * (i + 0.5f), 13.5f, Ui.REGULAR, ui.c("button.fg"));
        }
        return y + BLOCKED_LINE * Math.max(left.size(), right.size());
    }
}
