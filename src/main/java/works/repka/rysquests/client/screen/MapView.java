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
    private static final int NODE_W = 86;
    private static final int NODE_H = 24;
    private static final int GAP_X = 24;
    private static final int GAP_Y = 11;
    private static final int DOT = 11;
    private static final Metrics METRICS = new Metrics(NODE_W, NODE_H, GAP_X, GAP_Y, 1);
    private static final float MIN_ZOOM = 0.35f;
    private static final float MAX_ZOOM = 1.6f;
    private static final float DOTS_MIN_ZOOM = 0.6f;
    private static final int MINIMAP_W = 64;
    private static final int MINIMAP_H = 40;
    private static final int MARGIN = 20;

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
        ui.clip(x, y, w, h);
        Draw.round(ui.g, x, y, w, h, 6f, ui.color("sidebar.bg"));
        Layout current = layout;
        if (current == null) {
            Loading.render(ui.g, ui.theme, x, y, w, h, "rysquests.map.loading", quests.size());
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
        ui.g.pose().translate(x - panX * zoom, y - panY * zoom, 0);
        ui.g.pose().scale(zoom, zoom, 1f);
        Map<ResourceLocation, Quest> byId = new HashMap<>();
        quests.forEach(quest -> byId.put(quest.id(), quest));
        renderGroups(ui, current, quests);
        renderEdges(ui, current, quests, byId);
        for (Quest quest : quests) {
            Placed placed = current.placed.get(quest.id().toString());
            if (placed != null && isOnScreen(placed)) {
                renderNode(ui, quest, placed, current);
            }
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
            panX = -MARGIN;
            panY = -MARGIN;
            return;
        }
        panX = placed.x() + NODE_W / 2f - viewW / (2 * zoom);
        panY = placed.y() + NODE_H / 2f - viewH / (2 * zoom);
    }

    private void clampPan(Layout current) {
        float visibleW = viewW / zoom;
        float visibleH = viewH / zoom;
        panX = clampAxis(panX, current.width, visibleW);
        panY = clampAxis(panY, current.height, visibleH);
    }

    private static float clampAxis(float pan, int content, float visible) {
        float low = -MARGIN;
        float high = content + MARGIN - visible;
        return high < low ? (low + high) / 2 : Mth.clamp(pan, low, high);
    }

    private boolean isOnScreen(Placed placed) {
        return placed.x() + NODE_W >= panX && placed.x() <= panX + viewW / zoom && placed.y() + NODE_H >= panY && placed.y() <= panY + viewH / zoom;
    }

    private void renderDots(Ui ui) {
        if (zoom < DOTS_MIN_ZOOM) {
            return;
        }
        int color = ui.color("slot.border");
        float step = DOT * zoom;
        float startX = viewX - (panX * zoom) % step;
        float startY = viewY - (panY * zoom) % step;
        for (float px = startX; px < viewX + viewW; px += step) {
            for (float py = startY; py < viewY + viewH; py += step) {
                Draw.rect(ui.g, Math.round(px), Math.round(py), 1, 1, color);
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
                dashed(ui, minX - 6, minY - 8, maxX - minX + 12, maxY - minY + 14);
                ui.small(Component.translatable(group.title()).getString(), minX - 2, minY - 12, "text.dim");
            }
        }
    }

    private static void dashed(Ui ui, int x, int y, int w, int h) {
        int color = ui.color("card.border");
        for (int i = 0; i < w; i += 6) {
            Draw.rect(ui.g, x + i, y, Math.min(3, w - i), 1, color);
            Draw.rect(ui.g, x + i, y + h, Math.min(3, w - i), 1, color);
        }
        for (int i = 0; i < h; i += 6) {
            Draw.rect(ui.g, x, y + i, 1, Math.min(3, h - i), color);
            Draw.rect(ui.g, x + w, y + i, 1, Math.min(3, h - i), color);
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
                    renderEdge(ui, from, to, edgeToken(quest, dependency));
                }
            }
        }
    }

    private String edgeToken(Quest target, ResourceLocation dependency) {
        boolean dependencyDone = ClientProgress.get(dependency).completed();
        boolean targetDone = ClientProgress.get(target.id()).completed();
        if (!focusPath.isEmpty() && !(focusPath.contains(target.id()) && focusPath.contains(dependency))) {
            return "panel.border";
        }
        if (dependencyDone && targetDone) {
            return "state.ok";
        }
        return dependencyDone ? "text.muted" : "panel.border";
    }

    private void renderEdge(Ui ui, Placed from, Placed to, String token) {
        int x1 = from.x() + NODE_W;
        int y1 = from.y() + NODE_H / 2;
        int x2 = to.x();
        int y2 = to.y() + NODE_H / 2;
        if (Math.max(x1, x2) < panX || Math.min(x1, x2) > panX + viewW / zoom) {
            return;
        }
        int mid = x2 - GAP_X / 2;
        int color = ui.color(token);
        Draw.rect(ui.g, x1, y1, Math.max(1, mid - x1), 1, color);
        Draw.rect(ui.g, mid, Math.min(y1, y2), 1, Math.abs(y2 - y1) + 1, color);
        Draw.rect(ui.g, mid, y2, Math.max(1, x2 - mid), 1, color);
    }

    private void renderNode(Ui ui, Quest quest, Placed placed, Layout current) {
        QuestStatus status = BookState.status(quest);
        boolean selected = quest.id().equals(BookState.quest);
        boolean dimmed = !focusPath.isEmpty() && !focusPath.contains(quest.id());
        int x = placed.x();
        int y = placed.y();
        ui.theme.card(ui.g, x, y, NODE_W, NODE_H);
        if (selected || status != QuestStatus.LOCKED && !dimmed) {
            Draw.roundBorder(ui.g, x, y, NODE_W, NODE_H, 5f, ui.color(selected ? "text.primary" : borderToken(status)), selected ? 1f : 0.5f);
        }
        quest.icon().map(TaskText::stackOf).ifPresent(stack -> ui.plated(stack, x + 3, y + 4));
        boolean locked = status == QuestStatus.LOCKED || dimmed;
        ui.text(Draw.ellipsize(Component.translatable(quest.title()).getString(), NODE_W - 24), x + 21, y + 4, locked ? "text.dim" : "text.primary");
        ui.small(subline(quest, status, current), x + 21, y + 14, subToken(status));
        if (status == QuestStatus.LOCKED) {
            Draw.lock(ui.g, x + NODE_W - 10, y + 13, ui.color("text.dim"));
        }
        int sx = Math.round(viewX + (x - panX) * zoom);
        int sy = Math.round(viewY + (y - panY) * zoom);
        int sw = Math.round(NODE_W * zoom);
        int sh = Math.round(NODE_H * zoom);
        ui.hit(sx, sy, sw, sh, () -> BookState.select(quest));
    }

    private static String borderToken(QuestStatus status) {
        return status == QuestStatus.CLAIMED ? "state.ok" : "text.muted";
    }

    private static String subToken(QuestStatus status) {
        return switch (status) {
            case CLAIMED -> "state.ok";
            case LOCKED -> "text.dim";
            default -> "text.muted";
        };
    }

    private static String subline(Quest quest, QuestStatus status, Layout current) {
        int folded = current.collapsed.foldedInto().getOrDefault(quest.id().toString(), 0);
        if (folded > 0) {
            return Component.translatable("rysquests.map.folded", folded).getString();
        }
        return switch (status) {
            case CLAIMED -> Component.translatable("rysquests.map.done").getString();
            case COMPLETED -> Component.translatable("rysquests.map.claim").getString();
            case LOCKED -> "";
            default -> {
                QuestProgress own = ClientProgress.get(quest.id());
                int done = 0;
                for (int i = 0; i < quest.tasks().size(); i++) {
                    if (own.task(i) >= quest.tasks().get(i).target()) {
                        done++;
                    }
                }
                yield done + " / " + quest.tasks().size();
            }
        };
    }

    private void renderOverlay(Ui ui, Layout current, List<Quest> quests) {
        Component toggle = Component.translatable(expandAll ? "rysquests.map.fold" : "rysquests.map.unfold", current.collapsed.hidden().size());
        if (expandAll || !current.collapsed.hidden().isEmpty()) {
            ui.button(toggle, viewX + 4, viewY + 4, 14, false, () -> expandAll = !expandAll);
        }
        Component focus = Component.translatable(focusPath.isEmpty() ? "rysquests.map.focus" : "rysquests.map.unfocus");
        int focusW = ui.font.width(focus) + 14;
        ui.button(focus, viewX + viewW - focusW - 4, viewY + 4, 14, false, this::toggleFocus);
        renderMinimap(ui, current, quests);
    }

    private void renderMinimap(Ui ui, Layout current, List<Quest> quests) {
        if (current.width <= viewW / zoom && current.height <= viewH / zoom) {
            return;
        }
        int x = viewX + viewW - MINIMAP_W - 4;
        int y = viewY + viewH - MINIMAP_H - 4;
        ui.theme.card(ui.g, x, y, MINIMAP_W, MINIMAP_H);
        float scale = Math.min((MINIMAP_W - 6f) / Math.max(1, current.width), (MINIMAP_H - 6f) / Math.max(1, current.height));
        for (Quest quest : quests) {
            Placed placed = current.placed.get(quest.id().toString());
            if (placed != null) {
                String token = switch (BookState.status(quest)) {
                    case CLAIMED -> "state.ok";
                    case LOCKED -> "text.dim";
                    default -> "text.primary";
                };
                Draw.rect(ui.g, x + 3 + Math.round(placed.x() * scale), y + 3 + Math.round(placed.y() * scale), 2, 1, ui.color(token));
            }
        }
        int frameX = x + 3 + Math.round(panX * scale);
        int frameY = y + 3 + Math.round(panY * scale);
        int frameW = Math.round(viewW / zoom * scale);
        int frameH = Math.round(viewH / zoom * scale);
        Draw.rect(ui.g, frameX, frameY, frameW, 1, ui.color("text.muted"));
        Draw.rect(ui.g, frameX, frameY + frameH, frameW, 1, ui.color("text.muted"));
        Draw.rect(ui.g, frameX, frameY, 1, frameH, ui.color("text.muted"));
        Draw.rect(ui.g, frameX + frameW, frameY, 1, frameH, ui.color("text.muted"));
        ui.hit(x, y, MINIMAP_W, MINIMAP_H, () -> {
            panX = (ui.mouseX - x - 3) / scale - viewW / (2 * zoom);
            panY = (ui.mouseY - y - 3) / scale - viewH / (2 * zoom);
        });
    }

    /** Highlights the chain of dependencies leading to the selected quest and brings it to the middle. */
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
            panY -= (float) (amount * 24 / zoom);
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
