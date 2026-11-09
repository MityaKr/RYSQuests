package works.repka.rysquests.client.screen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.client.layout.CollapsedBranches;
import works.repka.rysquests.client.layout.LayeredLayout;
import works.repka.rysquests.client.layout.LayeredLayout.Metrics;
import works.repka.rysquests.client.layout.LayeredLayout.Node;
import works.repka.rysquests.client.layout.LayeredLayout.Placed;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.progress.QuestProgress;
import works.repka.rysquests.progress.QuestStatus;
import works.repka.ryscore.ui.Draw;
import works.repka.ryscore.ui.Loading;

/** The chapter as a graph, laid out automatically in the background. Only what is on screen is drawn. */
final class MapView implements BookView {
    private static final int NODE_W = 160;
    private static final int NODE_H = 47;
    private static final int ACTIVE_W = 228;
    private static final int ACTIVE_H = 74;
    private static final int GAP_X = 48;
    private static final int GAP_Y = 24;
    private static final int DOT_STEP = 24;
    private static final Metrics METRICS = new Metrics(NODE_W, NODE_H, GAP_X, GAP_Y, 1);
    private static final float MIN_ZOOM = 0.35f;
    private static final float MAX_ZOOM = 1.6f;
    private static final float DOTS_MIN_ZOOM = 0.6f;
    private static final int MINIMAP_W = 152;
    private static final int MINIMAP_H = 86;
    private static final int MINIMAP_PAD = 8;
    private static final int MARGIN_LEFT = 36;
    private static final int MARGIN_TOP = 84;
    private static final int MARGIN_END = 40;
    private static final float LOCKED_OPACITY = 0.6f;

    private record Layout(String key, Map<String, Placed> placed, CollapsedBranches.Result collapsed, int width, int height) {
    }

    private final BookScreen screen;
    @Nullable
    private volatile Layout layout;
    @Nullable
    private String requested;
    private float zoom = 1f;
    private float panX;
    private float panY;
    private boolean expandAll;
    private Set<ResourceLocation> focusPath = Set.of();
    private int viewX;
    private int viewY;
    private int viewW;
    private int viewH;
    private boolean recenter = true;

    MapView(BookScreen screen) {
        this.screen = screen;
    }

    private void requestLayout(List<Quest> quests) {
        Set<String> done = new HashSet<>();
        for (Quest quest : quests) {
            if (ClientProgress.get(quest.id()).completed()) {
                done.add(quest.id().toString());
            }
        }
        String key = ClientBook.current().hash() + BookState.chapter + done.hashCode() + expandAll;
        if (key.equals(requested)) {
            return;
        }
        requested = key;
        boolean foldDone = !expandAll;
        CompletableFuture.supplyAsync(() -> compute(key, quests, done, foldDone), Util.backgroundExecutor())
                .thenAcceptAsync(result -> {
                    if (key.equals(requested)) {
                        boolean chapterChanged = layout == null || !sameChapter(layout.key, key);
                        layout = result;
                        if (chapterChanged) {
                            recenter = true;
                        }
                    }
                }, Minecraft.getInstance());
    }

    private static boolean sameChapter(String a, String b) {
        int prefix = ClientBook.current().hash().length() + String.valueOf(BookState.chapter).length();
        return a.length() >= prefix && b.length() >= prefix && a.substring(0, prefix).equals(b.substring(0, prefix));
    }

    private static Layout compute(String key, List<Quest> quests, Set<String> done, boolean foldDone) {
        List<Node> nodes = new ArrayList<>();
        for (Quest quest : quests) {
            var layout = quest.options().layout();
            OptionalInt x = layout.x().map(OptionalInt::of).orElse(OptionalInt.empty());
            OptionalInt y = layout.y().map(OptionalInt::of).orElse(OptionalInt.empty());
            nodes.add(new Node(quest.id().toString(), quest.requires().stream().map(ResourceLocation::toString).toList(), x, y));
        }
        CollapsedBranches.Result collapsed = foldDone
                ? CollapsedBranches.compute(nodes, done::contains)
                : new CollapsedBranches.Result(Set.of(), Map.of());
        List<Node> visible = nodes.stream().filter(node -> !collapsed.hidden().contains(node.id())).toList();
        Map<String, Placed> placed = LayeredLayout.compute(visible, METRICS);
        int width = placed.values().stream().mapToInt(Placed::x).max().orElse(0) + NODE_W;
        int height = placed.values().stream().mapToInt(Placed::y).max().orElse(0) + NODE_H;
        return new Layout(key, placed, collapsed, width, height);
    }

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        viewX = x;
        viewY = y;
        viewW = w;
        viewH = h;
        List<Quest> quests = screen.visibleQuests();
        requestLayout(quests);
        ui.rect(x, y, w, h, ui.mapBg());
        ui.clip(x, y, w, h);
        Layout current = layout;
        if (current == null) {
            Loading.render(ui.g, ui.theme, x / 2, y / 2, w / 2, h / 2, "rysquests.map.loading", quests.size());
            ui.unclip();
            return;
        }
        if (recenter) {
            centerOn(current, firstInteresting(current, quests));
            recenter = false;
        }
        clampPan(current);
        renderDots(ui);
        ui.g.pose().pushPose();
        ui.g.pose().translate((x - panX * zoom) / 2f, (y - panY * zoom) / 2f, 0);
        ui.g.pose().scale(zoom, zoom, 1f);
        Map<ResourceLocation, Quest> byId = new HashMap<>();
        quests.forEach(quest -> byId.put(quest.id(), quest));
        renderGroups(ui, current, quests);
        renderEdges(ui, current, quests, byId);
        Quest selected = null;
        for (Quest quest : quests) {
            Placed placed = current.placed.get(quest.id().toString());
            if (placed != null && isOnScreen(placed)) {
                if (quest.id().equals(BookState.quest)) {
                    selected = quest;
                } else {
                    renderNode(ui, quest, placed, current);
                }
            }
        }
        if (selected != null) {
            renderActive(ui, selected, current.placed.get(selected.id().toString()));
        }
        ui.g.pose().popPose();
        renderOverlay(ui, current, quests);
        ui.unclip();
    }

    @Nullable
    private static Quest firstInteresting(Layout current, List<Quest> quests) {
        Quest selected = BookState.quest == null ? null : ClientBook.current().quests().get(BookState.quest);
        if (selected != null && current.placed.containsKey(selected.id().toString())) {
            return selected;
        }
        return quests.stream()
                .filter(quest -> current.placed.containsKey(quest.id().toString()))
                .filter(quest -> {
                    QuestStatus status = BookState.status(quest);
                    return status == QuestStatus.AVAILABLE || status == QuestStatus.IN_PROGRESS || status == QuestStatus.COMPLETED;
                })
                .findFirst().orElse(null);
    }

    private void centerOn(Layout current, @Nullable Quest quest) {
        Placed placed = quest == null ? null : current.placed.get(quest.id().toString());
        if (placed == null) {
            panX = -MARGIN_LEFT;
            panY = -MARGIN_TOP;
            return;
        }
        panX = fits(placed.x() + (NODE_W + ACTIVE_W) / 2, viewW, MARGIN_LEFT) ? -MARGIN_LEFT : placed.x() + NODE_W / 2f - viewW / (2 * zoom);
        panY = fits(placed.y() + (NODE_H + ACTIVE_H) / 2, viewH, MARGIN_TOP) ? -MARGIN_TOP : placed.y() + NODE_H / 2f - viewH / (2 * zoom);
    }

    /** Whether a position is on screen with the graph at its start, where the mockup has it. */
    private boolean fits(int edge, int view, int margin) {
        return edge + margin <= view / zoom - MARGIN_END;
    }

    private void clampPan(Layout current) {
        panX = clampAxis(panX, current.width, viewW / zoom, MARGIN_LEFT);
        panY = clampAxis(panY, current.height, viewH / zoom, MARGIN_TOP);
    }

    /** A graph that fits stays where the mockup puts it, at the top left. */
    private static float clampAxis(float pan, int content, float visible, int startMargin) {
        float low = -startMargin;
        float high = content + MARGIN_END - visible;
        return high < low ? low : Mth.clamp(pan, low, high);
    }

    private boolean isOnScreen(Placed placed) {
        int extra = ACTIVE_W;
        return placed.x() + NODE_W + extra >= panX && placed.x() - extra <= panX + viewW / zoom
                && placed.y() + ACTIVE_H >= panY && placed.y() - ACTIVE_H <= panY + viewH / zoom;
    }

    private void renderDots(Ui ui) {
        if (zoom < DOTS_MIN_ZOOM) {
            return;
        }
        int color = ui.c("slot.border");
        float step = DOT_STEP * zoom;
        float startX = viewX + 11 - Mth.positiveModulo(panX * zoom, step);
        float startY = viewY + 11 - Mth.positiveModulo(panY * zoom, step);
        for (float px = startX; px < viewX + viewW; px += step) {
            for (float py = startY; py < viewY + viewH; py += step) {
                Draw.rect(ui.g, Math.round(px / 2f), Math.round(py / 2f), 1, 1, color);
            }
        }
    }

    private void renderGroups(Ui ui, Layout current, List<Quest> quests) {
        Chapter chapter = BookState.chapter == null ? null : ClientBook.current().chapters().get(BookState.chapter);
        if (chapter == null) {
            return;
        }
        for (Chapter.Group group : chapter.groups()) {
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            for (Quest quest : quests) {
                Placed placed = current.placed.get(quest.id().toString());
                if (placed != null && quest.group().filter(group.id()::equals).isPresent()) {
                    minX = Math.min(minX, placed.x());
                    minY = Math.min(minY, placed.y());
                    maxX = Math.max(maxX, placed.x() + NODE_W);
                    maxY = Math.max(maxY, placed.y() + NODE_H);
                }
            }
            if (minX != Integer.MAX_VALUE) {
                int x = minX - 12;
                int y = minY - 24;
                ui.dashed(x, y, maxX - minX + 24, maxY - minY + 36, ui.c("card.border"));
                String title = Component.translatable(group.title()).getString();
                float titleW = ui.width(title, 11, Ui.REGULAR);
                ui.rect(x + 8, y - 10, titleW + 12, 14, ui.mapBg());
                ui.label(title, x + 14, y - 3, 11, Ui.REGULAR, ui.c("text.dim"));
            }
        }
    }

    private void renderEdges(Ui ui, Layout current, List<Quest> quests, Map<ResourceLocation, Quest> byId) {
        for (Quest quest : quests) {
            Placed to = current.placed.get(quest.id().toString());
            if (to == null) {
                continue;
            }
            for (ResourceLocation dependency : quest.requires()) {
                Placed from = current.placed.get(dependency.toString());
                if (from != null && byId.containsKey(dependency)) {
                    renderEdge(ui, from, to, edgeColor(ui, quest, dependency));
                }
            }
        }
    }

    private int edgeColor(Ui ui, Quest target, ResourceLocation dependency) {
        if (!focusPath.isEmpty() && !(focusPath.contains(target.id()) && focusPath.contains(dependency))) {
            return ui.c("panel.border");
        }
        boolean dependencyDone = ClientProgress.get(dependency).completed();
        boolean targetDone = ClientProgress.get(target.id()).completed();
        if (dependencyDone && targetDone) {
            return ui.c("slot.have.border");
        }
        return dependencyDone ? ui.c("accent") : ui.idleLine();
    }

    private void renderEdge(Ui ui, Placed from, Placed to, int color) {
        int x1 = from.x() + NODE_W;
        int y1 = from.y() + NODE_H / 2;
        int x2 = to.x();
        int y2 = to.y() + NODE_H / 2;
        if (Math.max(x1, x2) < panX || Math.min(x1, x2) > panX + viewW / zoom) {
            return;
        }
        int mid = x2 - GAP_X / 2;
        ui.rect(x1, y1 - 1, Math.max(2, mid - x1), 2, color);
        ui.rect(mid, Math.min(y1, y2) - 1, 2, Math.abs(y2 - y1) + 2, color);
        ui.rect(mid, y2 - 1, Math.max(2, x2 - mid), 2, color);
    }

    private boolean faded(Quest quest) {
        if (!focusPath.isEmpty() && !focusPath.contains(quest.id())) {
            return true;
        }
        return !BookState.search.isBlank() && !SearchIndex.matches(quest);
    }

    private void addHit(Ui ui, Quest quest, float x, float y, float w, float h, Runnable click) {
        float sx = viewX + (x - panX) * zoom;
        float sy = viewY + (y - panY) * zoom;
        ui.hit(sx, sy, w * zoom, h * zoom, click);
        ui.rightHit(sx, sy, w * zoom, h * zoom, () -> BookScreen.togglePin(quest.id()));
    }

    private void renderNode(Ui ui, Quest quest, Placed placed, Layout current) {
        QuestStatus status = BookState.status(quest);
        boolean locked = status == QuestStatus.LOCKED;
        boolean faint = locked || faded(quest);
        int x = placed.x();
        int y = placed.y();
        int mapBg = ui.mapBg();
        float opacity = faint ? LOCKED_OPACITY : 1f;
        int border = status == QuestStatus.CLAIMED ? ui.doneBorder() : locked ? ui.c("card.border") : ui.c("panel.border");
        boolean overNode = ui.over(viewX + (x - panX) * zoom, viewY + (y - panY) * zoom, NODE_W * zoom, NODE_H * zoom);
        int bg = overNode ? ui.c("slot.hover") : ui.c("panel.bg");
        ui.box(x, y, NODE_W, NODE_H, 9, Draw.mix(mapBg, bg, opacity), Draw.mix(mapBg, border, opacity));
        float cy = y + NODE_H / 2f;
        ItemStack icon = quest.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY);
        ui.item(icon, x + 11, cy - 11, 22);
        if (faint && !icon.isEmpty()) {
            ui.veil(x + 11, cy - 11, 22, 22, 5, mapBg, 1f - opacity);
        }
        float textX = x + 11 + 22 + 8;
        float textW = NODE_W - 11 - 22 - 8 - 11;
        ui.labelFit(Component.translatable(quest.title()).getString(), textX, y + 9 + 7.5f, textW, 12, Ui.REGULAR, Draw.alpha(ui.nameFg(), opacity));
        subline(ui, quest, status, current, textX, y + 25 + 6.5f, textW, opacity);
        addHit(ui, quest, x, y, NODE_W, NODE_H, () -> click(quest, current));
    }

    private void click(Quest quest, Layout current) {
        BookState.select(quest);
        if (current.collapsed.foldedInto().getOrDefault(quest.id().toString(), 0) > 0) {
            expandAll = true;
        }
    }

    private static void subline(Ui ui, Quest quest, QuestStatus status, Layout current, float x, float cy, float maxW, float opacity) {
        int folded = current.collapsed.foldedInto().getOrDefault(quest.id().toString(), 0);
        float px = 10.5f;
        if (status == QuestStatus.CLAIMED || folded > 0) {
            int color = Draw.alpha(ui.c("state.ok"), opacity);
            ui.check(x, cy - 5, 10, color);
            String text = folded > 0 ? Component.translatable("rysquests.map.folded", folded).getString() : Component.translatable("rysquests.map.done").getString();
            ui.labelFit(text, x + 13, cy, maxW - 13, px, Ui.REGULAR, color);
            return;
        }
        if (status == QuestStatus.LOCKED) {
            int color = Draw.alpha(ui.c("text.muted"), opacity);
            ui.lock(x, cy - 5, 10, Draw.alpha(ui.mix("accent", "text.dim", 0.35f), opacity));
            long missing = quest.requires().stream().filter(id -> !ClientProgress.get(id).completed()).count();
            ui.labelFit(Component.translatable("rysquests.map.requires", missing).getString(), x + 13, cy, maxW - 13, px, Ui.REGULAR, color);
            return;
        }
        if (status == QuestStatus.COMPLETED) {
            ui.labelFit(Component.translatable("rysquests.map.claim").getString(), x, cy, maxW, px, Ui.REGULAR, Draw.alpha(ui.c("accent"), opacity));
            return;
        }
        String text = quest.tasks().isEmpty()
                ? Component.translatable("rysquests.map.page").getString()
                : Component.translatable("rysquests.map.available", tasksDone(quest), quest.tasks().size()).getString();
        ui.labelFit(text, x, cy, maxW, px, Ui.REGULAR, Draw.alpha(ui.c("text.muted"), opacity));
    }

    private static int tasksDone(Quest quest) {
        return QuestCard.tasksDone(quest, ClientProgress.get(quest.id()));
    }

    private void renderActive(Ui ui, Quest quest, Placed placed) {
        int x = placed.x() - (ACTIVE_W - NODE_W) / 2;
        int y = placed.y() - (ACTIVE_H - NODE_H) / 2;
        ui.shadow(x, y, ACTIVE_W, ACTIVE_H, 10, 0.4f);
        ui.fill(x, y, ACTIVE_W, ACTIVE_H, 10, ui.c("card.bg"));
        ui.strokeBold(x, y, ACTIVE_W, ACTIVE_H, 10, ui.c("accent"));
        float cy = y + ACTIVE_H / 2f;
        ItemStack icon = quest.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY);
        ui.item(icon, x + 14, cy - 14, 28);
        float textX = x + 14 + 28 + 10;
        float textW = ACTIVE_W - 14 - 28 - 10 - 14;
        String title = Component.translatable(quest.title()).getString();
        List<String> lines = ui.wrap(title, textW, 12.5f, Ui.SEMIBOLD, 2);
        QuestProgress own = ClientProgress.get(quest.id());
        int total = quest.tasks().size();
        int done = QuestCard.tasksDone(quest, own);
        float blockH = lines.size() * 15 + 13 + 4 + 3;
        float top = cy - blockH / 2f;
        for (int i = 0; i < lines.size(); i++) {
            ui.label(lines.get(i), textX, top + 7.5f + i * 15, 12.5f, Ui.SEMIBOLD, ui.c("text.primary"));
        }
        float subY = top + lines.size() * 15;
        ui.labelFit(Component.translatable("rysquests.map.tasks", done, total).getString(), textX, subY + 6.5f, textW, 10.5f, Ui.REGULAR, ui.c("accent"));
        ui.bar(textX, subY + 13 + 4, textW, 3, 2, total == 0 ? 0 : (float) done / total, ui.c("accent"));
        addHit(ui, quest, x, y, ACTIVE_W, ACTIVE_H, () -> BookState.select(quest));
    }

    private void renderOverlay(Ui ui, Layout current, List<Quest> quests) {
        Chapter chapter = BookState.chapter == null ? null : ClientBook.current().chapters().get(BookState.chapter);
        float left = viewX + 16;
        float cy = viewY + 21.5f;
        if (chapter != null) {
            String title = Ui.roman(BookScreen.chapterNumber(chapter.id())) + " · " + Component.translatable(chapter.title()).getString();
            ui.label(title, left, cy, 15, Ui.DISPLAY, ui.c("text.primary"));
            left += ui.width(title, 15, Ui.DISPLAY) + 10;
        }
        int[] counts = BookState.chapter == null ? new int[2] : screen.chapterCounts(BookState.chapter);
        String count = counts[0] + " / " + counts[1];
        ui.label(count, left, cy, 12.5f, Ui.REGULAR, ui.c("text.muted"));
        left += ui.width(count, 12.5f, Ui.REGULAR) + 10;
        String chip = Component.translatable(expandAll ? "rysquests.map.expanded" : "rysquests.map.auto").getString();
        float chipW = ui.pill(chip, left, cy, 11, Ui.REGULAR, 8, 18, 9, ui.c("chip.mod.bg"), ui.c("chip.mod.fg"));
        ui.hit(left, cy - 9, chipW, 18, () -> expandAll = !expandAll);
        String hint = Component.translatable("rysquests.map.hint").getString();
        ui.aboveItems(() -> {
            ui.fill(viewX + 12, viewY + viewH - 30, ui.width(hint, 11, Ui.REGULAR) + 8, 18, 4, Draw.alpha(ui.mapBg(), 0.85f));
            ui.label(hint, viewX + 16, viewY + viewH - 21, 11, Ui.REGULAR, ui.c("text.dim"));
        });
        renderMinimap(ui, current, quests);
    }

    private void renderMinimap(Ui ui, Layout current, List<Quest> quests) {
        float x = viewX + viewW - MINIMAP_W - 14;
        float y = viewY + viewH - MINIMAP_H - 14;
        ui.box(x, y, MINIMAP_W, MINIMAP_H, 8, Draw.alpha(ui.c("panel.bg"), 0.9f), ui.c("card.border"));
        float inner = MINIMAP_W - MINIMAP_PAD * 2f;
        float innerH = MINIMAP_H - MINIMAP_PAD * 2f;
        float scale = Math.min(inner / Math.max(1, current.width), innerH / Math.max(1, current.height));
        for (Quest quest : quests) {
            Placed placed = current.placed.get(quest.id().toString());
            if (placed != null) {
                int color = switch (BookState.status(quest)) {
                    case CLAIMED -> ui.c("state.ok");
                    case LOCKED -> ui.idleLine();
                    default -> ui.c("accent");
                };
                ui.fill(x + MINIMAP_PAD + placed.x() * scale, y + MINIMAP_PAD + placed.y() * scale, 6, 6, 2, color);
            }
        }
        float frameX = x + MINIMAP_PAD + panX * scale;
        float frameY = y + MINIMAP_PAD + panY * scale;
        float frameW = viewW / zoom * scale;
        float frameH = viewH / zoom * scale;
        float clippedX = Math.max(x + 3, frameX);
        float clippedY = Math.max(y + 3, frameY);
        float rightEdge = Math.min(x + MINIMAP_W - 3, frameX + frameW);
        float bottomEdge = Math.min(y + MINIMAP_H - 3, frameY + frameH);
        if (rightEdge > clippedX && bottomEdge > clippedY) {
            ui.stroke(clippedX, clippedY, rightEdge - clippedX, bottomEdge - clippedY, 3, ui.c("accent"));
        }
        ui.hit(x, y, MINIMAP_W, MINIMAP_H, () -> {
            panX = (ui.mouseX - x - MINIMAP_PAD) / scale - viewW / (2 * zoom);
            panY = (ui.mouseY - y - MINIMAP_PAD) / scale - viewH / (2 * zoom);
        });
    }

    /** Highlights the chain of dependencies leading to the selected quest and brings it to the middle. */
    void focusSelected() {
        focusPath = Set.of();
        toggleFocus();
    }

    private void toggleFocus() {
        if (!focusPath.isEmpty() || BookState.quest == null) {
            focusPath = Set.of();
            return;
        }
        Set<ResourceLocation> path = new HashSet<>();
        Deque<ResourceLocation> open = new ArrayDeque<>();
        open.add(BookState.quest);
        while (!open.isEmpty()) {
            ResourceLocation id = open.poll();
            Quest quest = ClientBook.current().quests().get(id);
            if (quest != null && path.add(id)) {
                open.addAll(quest.requires());
            }
        }
        focusPath = path;
        Layout current = layout;
        if (current != null) {
            centerOn(current, ClientBook.current().quests().get(BookState.quest));
        }
    }

    @Override
    public void scroll(double mouseX, double mouseY, double amount) {
        if (GLFW.glfwGetKey(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS) {
            float before = zoom;
            zoom = Mth.clamp(zoom * (amount > 0 ? 1.15f : 1 / 1.15f), MIN_ZOOM, MAX_ZOOM);
            float mx = (float) mouseX - viewX;
            float my = (float) mouseY - viewY;
            panX += mx / before - mx / zoom;
            panY += my / before - my / zoom;
        } else {
            panY -= (float) (amount * 48 / zoom);
        }
    }

    @Override
    public boolean drag(double deltaX, double deltaY) {
        panX -= (float) (deltaX / zoom);
        panY -= (float) (deltaY / zoom);
        return true;
    }

    @Override
    public boolean key(int key) {
        if (key == GLFW.GLFW_KEY_F) {
            toggleFocus();
            return true;
        }
        return false;
    }
}
