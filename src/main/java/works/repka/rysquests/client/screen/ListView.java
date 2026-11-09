package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.ViewMode;
import works.repka.rysquests.network.QuestActionPayload;
import works.repka.rysquests.progress.QuestProgress;
import works.repka.rysquests.progress.QuestStatus;
import works.repka.rysquests.task.ItemTask;

/** The chapter as a plain list, from {@code design/List.dc.html}. Rows outside the view are not drawn. */
final class ListView implements BookView {
    private static final int PAD_X = 20;
    private static final int ROW_H = 60;
    private static final int ROW_GAP = 6;
    private static final int GROUP_GAP = 10;
    private static final int GROUP_HEADER = 22;
    private static final int WHEEL = 48;

    private enum Sort {
        STATUS, ORDER, GROUP
    }

    private record Group(String title, String token, String hint) {
    }

    private final BookScreen screen;
    private final List<Quest> shown = new ArrayList<>();
    private Sort sort = Sort.STATUS;
    private boolean hideFinished;
    private boolean expandFinished;
    private int scroll;

    ListView(BookScreen screen) {
        this.screen = screen;
    }

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        int left = x + PAD_X;
        int inner = w - PAD_X * 2;
        renderHead(ui, left, y + 16, inner);
        int listY = y + 16 + 26 + 10;
        int footer = y + h - 36;
        ui.clip(x, listY, w, footer - listY);
        int cursor = listY - scroll;
        shown.clear();
        Map<Group, List<Quest>> groups = groups();
        boolean first = true;
        for (Map.Entry<Group, List<Quest>> entry : groups.entrySet()) {
            if (!first) {
                cursor += GROUP_GAP;
            }
            first = false;
            cursor = renderGroup(ui, entry.getKey(), entry.getValue(), left, cursor, inner, listY, footer);
        }
        ui.unclip();
        scroll = BookScreen.clampScroll(scroll, cursor + scroll - listY, footer - listY);
        ui.label(Component.translatable("rysquests.list.hint").getString(), left, y + h - 23, 11, Ui.REGULAR, ui.c("text.dim"));
    }

    private void renderHead(Ui ui, float x, float y, float w) {
        float cy = y + 13;
        Chapter chapter = BookState.chapter == null ? null : ClientBook.current().chapters().get(BookState.chapter);
        if (chapter != null) {
            String title = Ui.roman(BookScreen.chapterNumber(chapter.id())) + " · " + Component.translatable(chapter.title()).getString();
            ui.label(title, x, cy, 16, Ui.DISPLAY, ui.c("text.primary"));
            ui.label(Ui.counted("rysquests.plural.quest", screen.visibleQuests().size()), x + ui.width(title, 16, Ui.DISPLAY) + 10, cy, 12, Ui.REGULAR, ui.c("text.muted"));
        }
        String toggle = Component.translatable(hideFinished ? "rysquests.list.show_finished" : "rysquests.list.hide_finished").getString();
        float toggleW = ui.width(toggle, 11.5f, Ui.REGULAR) + 22;
        float right = x + w;
        ui.box(right - toggleW, y, toggleW, 26, 7, ui.over(right - toggleW, y, toggleW, 26) ? ui.c("button.bg") : 0, ui.c("panel.border"));
        ui.label(toggle, right - toggleW + 11, cy, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        ui.hit(right - toggleW, y, toggleW, 26, () -> hideFinished = !hideFinished);
        right -= toggleW + 4;
        Sort[] sorts = Sort.values();
        for (int i = sorts.length - 1; i >= 0; i--) {
            Sort option = sorts[i];
            boolean active = option == sort;
            String label = Component.translatable("rysquests.list.sort." + option.name().toLowerCase(Locale.ROOT)).getString();
            var weight = active ? Ui.MEDIUM : Ui.REGULAR;
            float tabW = ui.width(label, 11.5f, weight) + 20;
            if (active || ui.over(right - tabW, y, tabW, 26)) {
                ui.fill(right - tabW, y, tabW, 26, 7, ui.c("button.bg"));
            }
            ui.label(label, right - tabW + 10, cy, 11.5f, weight, ui.c(active ? "text.primary" : "tab.inactive.fg"));
            ui.hit(right - tabW, y, tabW, 26, () -> sort = option);
            right -= tabW + 4;
        }
    }

    private boolean matches(Quest quest) {
        return BookState.search.isBlank() || Component.translatable(quest.title()).getString().toLowerCase(Locale.ROOT).contains(BookState.search.toLowerCase(Locale.ROOT));
    }

    private Map<Group, List<Quest>> groups() {
        Map<Group, List<Quest>> groups = new LinkedHashMap<>();
        List<Quest> quests = screen.visibleQuests().stream().filter(this::matches).toList();
        if (sort == Sort.STATUS) {
            add(groups, statusGroup("active"), quests, QuestStatus.COMPLETED, QuestStatus.IN_PROGRESS);
            add(groups, statusGroup("available"), quests, QuestStatus.AVAILABLE);
            add(groups, statusGroup("locked"), quests, QuestStatus.LOCKED);
            add(groups, statusGroup("finished"), quests, QuestStatus.CLAIMED);
        } else if (sort == Sort.ORDER) {
            if (!quests.isEmpty()) {
                groups.put(new Group(Component.translatable("rysquests.list.sort.order").getString(), "text.dim", ""), quests);
            }
        } else {
            Chapter chapter = BookState.chapter == null ? null : ClientBook.current().chapters().get(BookState.chapter);
            if (chapter != null) {
                for (Chapter.Group group : chapter.groups()) {
                    List<Quest> members = quests.stream().filter(quest -> quest.group().filter(group.id()::equals).isPresent()).toList();
                    if (!members.isEmpty()) {
                        groups.put(new Group(Component.translatable(group.title()).getString(), "chip.mod.fg", ""), members);
                    }
                }
            }
            List<Quest> others = quests.stream().filter(quest -> quest.group().isEmpty()).toList();
            if (!others.isEmpty()) {
                groups.put(new Group(Component.translatable("rysquests.timeline.other").getString(), "text.dim", ""), others);
            }
        }
        return groups;
    }

    private static Group statusGroup(String id) {
        String token = switch (id) {
            case "active" -> "accent";
            case "available" -> "chip.mod.fg";
            case "finished" -> "state.ok";
            default -> "text.dim";
        };
        return new Group(Component.translatable("rysquests.list.group." + id).getString(), token, id);
    }

    private void add(Map<Group, List<Quest>> groups, Group group, List<Quest> quests, QuestStatus... statuses) {
        List<Quest> members = new ArrayList<>();
        for (Quest quest : quests) {
            QuestStatus status = BookState.status(quest);
            for (QuestStatus wanted : statuses) {
                if (status == wanted) {
                    members.add(quest);
                }
            }
        }
        boolean finished = "finished".equals(group.hint);
        if (!members.isEmpty() && !(finished && hideFinished)) {
            if (finished) {
                members.sort(Comparator.comparingLong((Quest quest) -> ClientProgress.get(quest.id()).completedAt()).reversed());
            }
            groups.put(group, members);
        }
    }

    private int renderGroup(Ui ui, Group group, List<Quest> quests, int x, int y, int w, int top, int bottom) {
        boolean finished = "finished".equals(group.hint);
        if (y + GROUP_HEADER > top && y < bottom) {
            float cy = y + 11;
            ui.circle(x + 2, cy - 4, 8, ui.c(group.token));
            ui.caps(group.title, x + 18, cy, ui.c(group.token));
            ui.label(String.valueOf(quests.size()), x + 18 + ui.capsWidth(group.title) + 8, cy, 11, Ui.MEDIUM, ui.c("text.dim"));
            String hint = hintOf(group, finished);
            ui.labelRight(hint, x + w - 2, cy, 11, Ui.REGULAR, ui.c("text.dim"));
            if (finished) {
                ui.hit(x, y, w, GROUP_HEADER, () -> expandFinished = !expandFinished);
            }
        }
        int cursor = y + GROUP_HEADER + ROW_GAP;
        int count = finished && !expandFinished ? Math.min(1, quests.size()) : quests.size();
        for (int i = 0; i < count; i++) {
            shown.add(quests.get(i));
            if (cursor + ROW_H > top && cursor < bottom) {
                renderRow(ui, quests.get(i), x, cursor, w);
            }
            cursor += ROW_H + (i + 1 < count ? ROW_GAP : 0);
        }
        return cursor;
    }

    private String hintOf(Group group, boolean finished) {
        if (finished) {
            return Component.translatable(expandFinished ? "rysquests.list.hint.finished_open" : "rysquests.list.hint.finished").getString();
        }
        return group.hint.isEmpty() ? "" : Component.translatable("rysquests.list.hint." + group.hint).getString();
    }

    private void renderRow(Ui ui, Quest quest, float x, float y, float w) {
        QuestStatus status = BookState.status(quest);
        QuestProgress own = ClientProgress.get(quest.id());
        boolean finished = status == QuestStatus.CLAIMED;
        boolean active = status == QuestStatus.IN_PROGRESS || status == QuestStatus.COMPLETED;
        boolean selected = quest.id().equals(BookState.quest);
        boolean over = ui.over(x, y, w, ROW_H);
        int bg = finished ? ui.mix("card.bg", "panel.bg", 0.6f) : over || selected ? ui.currentBg() : ui.c("card.bg");
        ui.box(x, y, w, ROW_H, 10, bg, active ? ui.warmBorder(0.19f) : ui.c("panel.border"));
        ItemStack icon = quest.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY);
        ui.cell(icon, x + 13, y + 11, 38, 38, 8, 22, ui.c("slot.bg"), ui.c("card.border"));
        if (finished) {
            ui.veil(x + 13, y + 11, 38, 38, 8, bg, 0.45f);
        }
        float textX = x + 63;
        float nameCy = y + 21.5f;
        String title = Component.translatable(quest.title()).getString();
        float titleW = Math.min(420, ui.width(title, 13, Ui.SEMIBOLD));
        ui.labelFit(title, textX, nameCy, titleW, 13, Ui.SEMIBOLD, ui.c(finished ? "text.muted" : "text.primary"));
        ui.labelFit(subtitle(quest, status, own), textX + titleW + 8, nameCy, w - 63 - titleW - 8 - 170, 11, Ui.REGULAR, ui.c("text.muted"));
        float ratio = taskRatio(quest, own, finished);
        String barToken = status == QuestStatus.LOCKED ? "text.muted" : finished ? "state.ok" : active ? "accent" : "chip.tag.fg";
        ui.bar(textX, y + 37.5f, 180, 4, 2, ratio, finished ? ui.c("slot.have.border") : ui.c(barToken));
        if (finished) {
            ui.check(textX + 188, y + 33.5f, 12, ui.c("state.ok"));
        } else if (!quest.tasks().isEmpty()) {
            ui.label(QuestCard.tasksDone(quest, own) + " / " + quest.tasks().size(), textX + 188, y + 39.5f, 11, Ui.REGULAR, ui.c(barToken));
        }
        float slotX = x + w - 13 - 84 - 12 - 28;
        List<ItemStack> rewards = QuestCard.rewardStacks(quest, 1);
        if (!rewards.isEmpty()) {
            ui.cell(rewards.getFirst(), slotX, y + 16, 28, 28, 6, 15, ui.c("slot.bg"), ui.c("slot.border"));
        }
        ui.hit(x, y, w, ROW_H, () -> BookState.select(quest));
        action(ui, quest, status, x + w - 13, y + 15);
        ui.rightHit(x, y, w, ROW_H, () -> BookScreen.togglePin(quest.id()));
    }

    private static float taskRatio(Quest quest, QuestProgress own, boolean finished) {
        if (finished) {
            return 1f;
        }
        return quest.tasks().isEmpty() ? 0 : (float) QuestCard.tasksDone(quest, own) / quest.tasks().size();
    }

    private static String subtitle(Quest quest, QuestStatus status, QuestProgress own) {
        if (status == QuestStatus.CLAIMED) {
            return Component.translatable("rysquests.list.done_at", HomeView.ago(own.completedAt())).getString();
        }
        if (status == QuestStatus.LOCKED) {
            return BookScreen.waitingFor(quest).getString();
        }
        return readyToSubmit(quest, own) ? Component.translatable("rysquests.home.in_inventory").getString() : quest.subtitle().map(key -> Component.translatable(key).getString()).orElse("");
    }

    private static boolean readyToSubmit(Quest quest, QuestProgress own) {
        boolean any = false;
        for (int i = 0; i < quest.tasks().size(); i++) {
            if (quest.tasks().get(i) instanceof ItemTask item && item.mode() == ItemTask.Mode.CONSUME && own.task(i) < item.count()) {
                if (BookScreen.held(item) < item.count() - own.task(i)) {
                    return false;
                }
                any = true;
            }
        }
        return any;
    }

    private static void action(Ui ui, Quest quest, QuestStatus status, float right, float y) {
        if (status == QuestStatus.CLAIMED) {
            return;
        }
        String key;
        Ui.Style style = Ui.Style.FLAT;
        Runnable click;
        QuestProgress own = ClientProgress.get(quest.id());
        if (status == QuestStatus.COMPLETED) {
            key = "rysquests.book.claim_short";
            style = Ui.Style.ACCENT;
            click = () -> QuestCard.claim(quest);
        } else if (status != QuestStatus.LOCKED && readyToSubmit(quest, own)) {
            key = "rysquests.book.submit";
            style = Ui.Style.ACCENT;
            click = () -> QuestCard.send(QuestActionPayload.SUBMIT, quest, 0, List.of());
        } else {
            key = "rysquests.home.open";
            click = () -> {
                BookState.select(quest);
                BookState.view = ViewMode.FLOW;
            };
        }
        String label = Component.translatable(key).getString();
        float w = ui.buttonWidth(label, 11.5f, Ui.SEMIBOLD, 12);
        boolean flat = style == Ui.Style.FLAT;
        if (flat) {
            ui.fill(right - w, y, w, 30, 8, ui.c("button.bg"));
        }
        ui.button(label, right - w, y, w, 30, 8, 11.5f, Ui.SEMIBOLD, 12, style, click);
    }

    @Override
    public void scroll(double mouseX, double mouseY, double amount) {
        scroll -= (int) (amount * WHEEL);
    }

    @Override
    public boolean key(int key) {
        int index = -1;
        for (int i = 0; i < shown.size(); i++) {
            if (shown.get(i).id().equals(BookState.quest)) {
                index = i;
            }
        }
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            if (!shown.isEmpty()) {
                int next = Math.max(0, Math.min(shown.size() - 1, index + (key == GLFW.GLFW_KEY_UP ? -1 : 1)));
                BookState.select(shown.get(next));
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER && index >= 0) {
            BookState.view = ViewMode.FLOW;
            return true;
        }
        if (key == GLFW.GLFW_KEY_SPACE && index >= 0) {
            BookScreen.togglePin(shown.get(index).id());
            return true;
        }
        return false;
    }
}
