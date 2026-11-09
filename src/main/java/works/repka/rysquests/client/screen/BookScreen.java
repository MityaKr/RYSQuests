package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.client.input.QuestKeys;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.RewardDef;
import works.repka.rysquests.data.TaskDef;
import works.repka.rysquests.network.QuestActionPayload;
import works.repka.rysquests.progress.QuestLogic;
import works.repka.rysquests.progress.QuestProgress;
import works.repka.rysquests.progress.QuestStatus;
import works.repka.rysquests.reward.ChoiceReward;
import works.repka.rysquests.reward.CommandReward;
import works.repka.rysquests.reward.ItemReward;
import works.repka.rysquests.reward.RandomReward;
import works.repka.rysquests.reward.XpReward;
import works.repka.rysquests.task.AdvancementTask;
import works.repka.rysquests.task.CheckboxTask;
import works.repka.rysquests.task.ItemRef;
import works.repka.rysquests.task.ItemTask;
import works.repka.rysquests.task.KillTask;
import works.repka.rysquests.task.StatTask;
import works.repka.rysquests.task.VisitTask;
import works.repka.ryscore.theme.Theme;
import works.repka.ryscore.theme.ThemeManager;
import works.repka.ryscore.ui.Draw;

/** The quest book: chapters, the quests of the open chapter in dependency order, and the selected quest. */
public final class BookScreen extends Screen {
    private static final int PAD = 6;
    private static final int ROW = 20;
    private static final int SLOT = 18;
    private static final int BUTTON_H = 16;
    private static final int LINE = 10;

    /** Remembered across openings, and when coming back from another screen. */
    @Nullable
    private static ResourceLocation openChapter;
    @Nullable
    private static ResourceLocation selectedQuest;

    private record Hit(int x, int y, int w, int h, Runnable action) {
    }

    private final List<Hit> hits = new ArrayList<>();
    private final Map<Integer, Integer> choices = new HashMap<>();
    private final Map<ResourceLocation, List<Quest>> orderCache = new HashMap<>();
    @Nullable
    private QuestBook cachedFor;
    @Nullable
    private ItemStack hovered;
    private int chapterScroll;
    private int questScroll;
    private int cardScroll;
    private int chaptersX;
    private int questsX;
    private int cardX;

    public BookScreen() {
        super(Component.translatable("rysquests.book.title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static QuestBook book() {
        return ClientBook.current();
    }

    private static Function<ResourceLocation, QuestProgress> progress() {
        return ClientProgress::get;
    }

    private List<Quest> questsOf(ResourceLocation chapter) {
        if (cachedFor != book()) {
            cachedFor = book();
            orderCache.clear();
        }
        return orderCache.computeIfAbsent(chapter, id -> ChapterOrder.of(book(), id).stream()
                .filter(quest -> !quest.options().hidden() || QuestLogic.isUnlocked(quest, progress()))
                .toList());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        Theme theme = ThemeManager.get();
        hits.clear();
        hovered = null;
        int panelW = Math.round(width * 0.94f);
        int panelH = Math.round(height * 0.92f);
        int panelX = (width - panelW) / 2;
        int panelY = (height - panelH) / 2;
        theme.panel(g, panelX, panelY, panelW, panelH);

        int top = panelY + PAD;
        int bottom = panelY + panelH - PAD;
        int chaptersW = Math.min(112, panelW / 4);
        int cardW = Math.min(190, panelW * 2 / 5);
        chaptersX = panelX + PAD;
        questsX = chaptersX + chaptersW + PAD;
        cardX = panelX + panelW - PAD - cardW;
        int questsW = cardX - PAD - questsX;

        if (openChapter == null || !book().chapters().containsKey(openChapter)) {
            openChapter = book().chapters().keySet().stream().findFirst().orElse(null);
        }
        renderChapters(g, theme, top, bottom, chaptersW, mouseX, mouseY);
        if (openChapter != null) {
            renderQuests(g, theme, top, bottom, questsW, mouseX, mouseY);
        }
        Quest quest = selectedQuest == null ? null : book().quests().get(selectedQuest);
        if (quest != null) {
            renderCard(g, theme, quest, top, bottom, cardW, mouseX, mouseY);
        }
        if (hovered != null) {
            g.renderTooltip(font, hovered, mouseX, mouseY);
        }
    }

    private void renderChapters(GuiGraphics g, Theme theme, int top, int bottom, int w, int mouseX, int mouseY) {
        List<Chapter> chapters = List.copyOf(book().chapters().values());
        chapterScroll = clampScroll(chapterScroll, chapters.size() * ROW, bottom - top);
        g.enableScissor(chaptersX, top, chaptersX + w, bottom);
        int first = chapterScroll / ROW;
        int last = Math.min(chapters.size(), first + (bottom - top) / ROW + 2);
        for (int i = first; i < last; i++) {
            Chapter chapter = chapters.get(i);
            int y = top + i * ROW - chapterScroll;
            boolean open = chapter.id().equals(openChapter);
            boolean over = inside(mouseX, mouseY, chaptersX, y, w, ROW - 2) && mouseY >= top && mouseY < bottom;
            theme.tab(g, chaptersX, y, w, ROW - 2, open, over);
            chapter.icon().map(BookScreen::stackOf).ifPresent(stack -> g.renderItem(stack, chaptersX + 1, y + 1));
            int color = theme.c(open ? "tab.active.fg" : "tab.inactive.fg");
            Draw.text(g, Draw.ellipsize(Component.translatable(chapter.title()).getString(), w - 23), chaptersX + 20, y + 5, color);
            hits.add(new Hit(chaptersX, Math.max(y, top), w, ROW - 2, () -> {
                openChapter = chapter.id();
                questScroll = 0;
            }));
        }
        g.disableScissor();
    }

    private void renderQuests(GuiGraphics g, Theme theme, int top, int bottom, int w, int mouseX, int mouseY) {
        List<Quest> quests = questsOf(openChapter);
        questScroll = clampScroll(questScroll, quests.size() * ROW, bottom - top);
        g.enableScissor(questsX, top, questsX + w, bottom);
        int first = questScroll / ROW;
        int last = Math.min(quests.size(), first + (bottom - top) / ROW + 2);
        for (int i = first; i < last; i++) {
            Quest quest = quests.get(i);
            int y = top + i * ROW - questScroll;
            boolean selected = quest.id().equals(selectedQuest);
            boolean over = inside(mouseX, mouseY, questsX, y, w, ROW - 2) && mouseY >= top && mouseY < bottom;
            if (selected || over) {
                theme.card(g, questsX, y, w, ROW - 2);
            }
            QuestStatus status = QuestLogic.status(quest, progress());
            quest.icon().map(BookScreen::stackOf).ifPresent(stack -> g.renderItem(stack, questsX + 1, y + 1));
            if (status == QuestStatus.LOCKED) {
                Draw.lock(g, questsX + w - 11, y + 5, theme.c("text.dim"));
            } else if (status == QuestStatus.COMPLETED) {
                Draw.round(g, questsX + w - 11, y + 5, 7, 7, 3.5f, theme.c("accent"));
            }
            Draw.text(g, Draw.ellipsize(Component.translatable(quest.title()).getString(), w - 34), questsX + 20, y + 5, statusColor(theme, status));
            hits.add(new Hit(questsX, Math.max(y, top), w, ROW - 2, () -> select(quest)));
        }
        g.disableScissor();
    }

    private void select(Quest quest) {
        selectedQuest = quest.id();
        cardScroll = 0;
        choices.clear();
    }

    private static int statusColor(Theme theme, QuestStatus status) {
        return theme.c(switch (status) {
            case LOCKED -> "text.dim";
            case AVAILABLE -> "text.primary";
            case IN_PROGRESS, COMPLETED -> "accent";
            case CLAIMED -> "state.ok";
        });
    }

    private void renderCard(GuiGraphics g, Theme theme, Quest quest, int top, int bottom, int w, int mouseX, int mouseY) {
        theme.card(g, cardX, top, w, bottom - top);
        int inner = w - PAD * 2;
        int x = cardX + PAD;
        QuestProgress own = ClientProgress.get(quest.id());
        QuestStatus status = QuestLogic.status(quest, progress());
        boolean open = status == QuestStatus.AVAILABLE || status == QuestStatus.IN_PROGRESS;

        g.enableScissor(cardX, top + 1, cardX + w, bottom - 1);
        int y = top + PAD - cardScroll;
        for (FormattedCharSequence line : font.split(Component.translatable(quest.title()), inner)) {
            g.drawString(font, line, x, y, theme.c("text.primary"), false);
            y += LINE;
        }
        y += 3;
        if (quest.text().isPresent()) {
            for (FormattedCharSequence line : font.split(Component.translatable(quest.text().get()), inner)) {
                g.drawString(font, line, x, y, theme.c("text.body"), false);
                y += LINE;
            }
            y += 4;
        }
        if (status == QuestStatus.LOCKED) {
            Draw.text(g, Component.translatable("rysquests.book.locked"), x, y, theme.c("text.dim"));
            y += LINE + 4;
        }

        boolean canSubmit = false;
        for (int i = 0; i < quest.tasks().size(); i++) {
            TaskDef task = quest.tasks().get(i);
            int value = Math.min(own.task(i), task.target());
            boolean done = own.completed() || value >= task.target();
            ItemStack icon = taskIcon(task);
            if (!icon.isEmpty()) {
                drawSlot(g, theme, icon, x, y, mouseX, mouseY, false, top, bottom);
            }
            int textX = x + SLOT + 3;
            Draw.text(g, Draw.ellipsize(describe(task).getString(), inner - SLOT - 3), textX, y, theme.c(done ? "state.ok" : "text.body"));
            Draw.textScaled(g, value + " / " + task.target(), textX, y + 10, 0.75f, theme.c("text.muted"));
            if (task instanceof CheckboxTask && open && !done) {
                int index = i;
                hits.add(new Hit(x, y, inner, SLOT, () -> send(QuestActionPayload.CHECK, quest, index, List.of())));
            }
            canSubmit |= open && !done && task instanceof ItemTask item && item.mode() == ItemTask.Mode.CONSUME;
            y += SLOT + 4;
        }
        if (canSubmit) {
            y = button(g, theme, Component.translatable("rysquests.book.submit"), x, y, inner, true, mouseX, mouseY, top, bottom,
                    () -> send(QuestActionPayload.SUBMIT, quest, 0, List.of()));
        }
        if (!quest.rewards().isEmpty()) {
            y += 3;
            for (int i = 0; i < quest.rewards().size(); i++) {
                y = renderReward(g, theme, quest.rewards().get(i), i, own.claimed().contains(i), x, y, inner, mouseX, mouseY, top, bottom);
            }
        }
        if (status == QuestStatus.COMPLETED) {
            y += 2;
            y = button(g, theme, Component.translatable("rysquests.book.claim"), x, y, inner, true, mouseX, mouseY, top, bottom, () -> claim(quest));
        }
        int content = y + cardScroll - top;
        g.disableScissor();
        cardScroll = clampScroll(cardScroll, content, bottom - top);
    }

    private void claim(Quest quest) {
        List<Integer> picks = new ArrayList<>();
        for (int i = 0; i < quest.rewards().size(); i++) {
            picks.add(choices.getOrDefault(i, -1));
        }
        send(QuestActionPayload.CLAIM, quest, 0, picks);
    }

    private int renderReward(GuiGraphics g, Theme theme, RewardDef reward, int index, boolean claimed, int x, int y, int w, int mouseX, int mouseY, int top, int bottom) {
        int muted = theme.c(claimed ? "state.ok" : "text.muted");
        switch (reward) {
            case ItemReward item -> {
                drawSlot(g, theme, rewardStack(item), x, y, mouseX, mouseY, false, top, bottom);
                Draw.text(g, Draw.ellipsize(rewardStack(item).getHoverName().getString(), w - SLOT - 3), x + SLOT + 3, y + 5, muted);
                return y + SLOT + 3;
            }
            case XpReward xp -> {
                Component text = xp.levels().isPresent()
                        ? Component.translatable("rysquests.reward.levels", xp.levels().get())
                        : Component.translatable("rysquests.reward.points", xp.points().orElse(0));
                Draw.text(g, text, x, y, muted);
                return y + LINE + 2;
            }
            case ChoiceReward choice -> {
                return renderOptions(g, theme, choice.of(), index, Component.translatable("rysquests.reward.choose"), true, x, y, w, mouseX, mouseY, top, bottom);
            }
            case RandomReward random -> {
                return renderOptions(g, theme, random.of(), index, Component.translatable("rysquests.reward.random"), false, x, y, w, mouseX, mouseY, top, bottom);
            }
            case CommandReward ignored -> {
                return y;
            }
            default -> {
                return y;
            }
        }
    }

    private int renderOptions(GuiGraphics g, Theme theme, List<RewardDef> options, int index, Component label, boolean pickable, int x, int y, int w, int mouseX, int mouseY, int top, int bottom) {
        Draw.text(g, label, x, y, theme.c("text.muted"));
        y += LINE;
        int perRow = Math.max(1, w / (SLOT + 2));
        for (int i = 0; i < options.size(); i++) {
            int slotX = x + (i % perRow) * (SLOT + 2);
            int slotY = y + (i / perRow) * (SLOT + 2);
            if (options.get(i) instanceof ItemReward item) {
                drawSlot(g, theme, rewardStack(item), slotX, slotY, mouseX, mouseY, pickable && choices.getOrDefault(index, -1) == i, top, bottom);
            }
            if (pickable) {
                int option = i;
                hits.add(new Hit(slotX, slotY, SLOT, SLOT, () -> choices.put(index, option)));
            }
        }
        return y + ((options.size() + perRow - 1) / perRow) * (SLOT + 2) + 3;
    }

    private void drawSlot(GuiGraphics g, Theme theme, ItemStack stack, int x, int y, int mouseX, int mouseY, boolean accent, int top, int bottom) {
        boolean over = inside(mouseX, mouseY, x, y, SLOT, SLOT) && mouseY >= top && mouseY < bottom;
        theme.slot(g, x, y, SLOT, over || accent);
        g.renderItem(stack, x + 1, y + 1);
        g.renderItemDecorations(font, stack, x + 1, y + 1);
        if (over) {
            hovered = stack;
        }
    }

    private int button(GuiGraphics g, Theme theme, Component label, int x, int y, int w, boolean accent, int mouseX, int mouseY, int top, int bottom, Runnable action) {
        boolean over = inside(mouseX, mouseY, x, y, w, BUTTON_H) && mouseY >= top && mouseY < bottom;
        theme.button(g, x, y, w, BUTTON_H, over, accent);
        Draw.textCentered(g, label.getString(), x + w / 2, y + 4, theme.c(accent ? "accent.text" : "text.body"));
        hits.add(new Hit(x, y, w, BUTTON_H, action));
        return y + BUTTON_H + 4;
    }

    private static void send(int action, Quest quest, int value, List<Integer> picks) {
        PacketDistributor.sendToServer(new QuestActionPayload(action, quest.id(), value, picks));
    }

    private static ItemStack stackOf(ResourceLocation id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(id));
    }

    private static ItemStack rewardStack(ItemReward reward) {
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(reward.item()), reward.count());
        reward.components().ifPresent(stack::applyComponents);
        return stack;
    }

    private static ItemStack taskIcon(TaskDef task) {
        if (!(task instanceof ItemTask item) || item.itemRefs().isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemRef ref = item.itemRefs().getFirst();
        if (!ref.tag()) {
            return stackOf(ref.id());
        }
        return BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, ref.id()))
                .filter(tag -> tag.size() > 0)
                .map(tag -> new ItemStack(tag.get(0)))
                .orElse(ItemStack.EMPTY);
    }

    private static Component describe(TaskDef task) {
        if (task.title().isPresent()) {
            return Component.translatable(task.title().get());
        }
        return switch (task) {
            case ItemTask item -> taskIcon(item).isEmpty() ? Component.literal(item.itemRefs().getFirst().toString()) : taskIcon(item).getHoverName();
            case KillTask kill -> Component.translatable("rysquests.task.kill", BuiltInRegistries.ENTITY_TYPE.get(kill.entity()).getDescription());
            case StatTask stat -> Component.translatable("rysquests.task.stat", stat.entry().toString());
            case VisitTask visit -> Component.translatable("rysquests.task.visit",
                    visit.structure().map(ItemRef::toString).or(() -> visit.dimension().map(ResourceLocation::toString)).orElse(""));
            case AdvancementTask advancement -> Component.translatable("rysquests.task.advancement", advancement.advancement().toString());
            default -> Component.translatable("rysquests.task.checkbox");
        };
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static int clampScroll(int scroll, int content, int view) {
        return Mth.clamp(scroll, 0, Math.max(0, content - view));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit hit = hits.get(i);
                if (inside(mouseX, mouseY, hit.x, hit.y, hit.w, hit.h)) {
                    hit.action.run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int delta = (int) (-scrollY * ROW);
        if (mouseX >= cardX) {
            cardScroll += delta;
        } else if (mouseX >= questsX) {
            questScroll += delta;
        } else {
            chapterScroll += delta;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (QuestKeys.OPEN_BOOK.matches(key, scan)) {
            Minecraft.getInstance().setScreen(null);
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    public static void selectForTest(ResourceLocation chapter, ResourceLocation quest) {
        openChapter = chapter;
        selectedQuest = quest;
    }
}
