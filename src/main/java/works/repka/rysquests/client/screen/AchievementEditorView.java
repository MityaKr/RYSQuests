package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import works.repka.ryscore.ui.Draw;
import works.repka.ryscore.ui.Icons;
import works.repka.ryscore.ui.Numbers;
import works.repka.rysquests.data.Achievement;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.RewardDef;
import works.repka.rysquests.data.TaskDef;

/**
 * The editor's "Достижения" (picture 39b): the player's grid by category with "+ Достижение" last; on the right the chosen one: icon,
 * name, "Секретное", description, the one condition "что считать", the tiers with their threshold and reward, "+ Уровень", and the line
 * "Игрок увидит". Tiers are named by their number; their colour comes from their place (the last gold). "В архив" at the foot.
 */
final class AchievementEditorView extends EditorPage {
    private static final int SIDE_W = 244;
    private static final int PANEL_W = 360;
    private static final int CARD_H = 154;

    @Nullable
    private String category;
    private int filter;
    @Nullable
    private ResourceLocation chosen;
    private final Scroll scroll = new Scroll();
    private final Scroll panelScroll = new Scroll();
    private final TaskPicker picker = new TaskPicker(this);

    AchievementEditorView(BookScreen screen) {
        super(screen);
    }

    void select(ResourceLocation id) {
        chosen = id;
        fields.clear();
        panelScroll.reset();
    }

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        EditorBook editor = book();
        QuestBook book = editor.view();
        List<Achievement> all = book.achievements().values().stream().filter(a -> !a.archived()).toList();
        renderSide(ui, editor, all, x, y, h);
        int mx = x + SIDE_W;
        int mw = w - SIDE_W - (chosen == null ? 0 : PANEL_W);
        List<Achievement> shown = all.stream().filter(a -> category == null || a.category().filter(category::equals).isPresent())
                .filter(a -> switch (filter) {
                    case 1 -> a.tiers().stream().allMatch(t -> t.rewards().isEmpty());
                    case 2 -> a.hidden();
                    default -> true;
                }).toList();
        float px = mx + 16;
        float pw = mw - 32;
        float pen = y + 12;
        String title = Ui.counted("rysquests.ed.ach.count", shown.size());
        ui.label(title, px, pen + 11, 19, Ui.DISPLAY, ui.c("text.primary"));
        float tw = ui.width(title, 19, Ui.DISPLAY);
        Draw.info(ui.g, Math.round((px + tw + 8) / 2f), Math.round((pen + 4) / 2f), ui.c("text.muted"));
        ui.tooltipIfOver(px + tw + 6, pen, 18, 22, Component.translatable("rysquests.ed.ach.help"));
        ui.label(tr("rysquests.ed.ach.sub"), px, pen + 32, 12, Ui.REGULAR, ui.c("text.muted"));
        long noRewards = all.stream().filter(a -> a.tiers().stream().allMatch(t -> t.rewards().isEmpty())).count();
        long secret = all.stream().filter(Achievement::hidden).count();
        List<String> chips = List.of(tr("rysquests.ed.ach.in_order"), tr("rysquests.ed.ach.no_rewards", noRewards), tr("rysquests.ed.ach.secret", secret));
        float cw = EdUi.chipsWidth(ui, chips);
        EdUi.chips(ui, px + pw - cw, pen + 4, chips, filter, i -> filter = i);
        pen += 50;
        int columns = 3;
        float gap = 10;
        float cardW = (pw - gap * (columns - 1)) / columns;
        ui.clip(mx, Math.round(pen), mw, Math.round(y + h - pen));
        int offset = scroll.offset();
        float top = pen;
        pen -= offset;
        int i = 0;
        for (Achievement achievement : shown) {
            float cx = px + (i % columns) * (cardW + gap);
            float cy = pen + (i / columns) * (CARD_H + gap);
            card(ui, achievement, cx, cy, cardW);
            i++;
        }
        float cx = px + (i % columns) * (cardW + gap);
        float cy = pen + (i / columns) * (CARD_H + gap);
        boolean over = ui.over(cx, cy, cardW, CARD_H);
        ui.dashed(cx, cy, cardW, CARD_H, ui.c(over ? "text.secondary" : "button.border"));
        ui.icon(Icons.Shape.PLUS, cx + cardW / 2f - 8, cy + CARD_H / 2f - 20, 16, ui.c(over ? "text.primary" : "text.secondary"));
        ui.labelCentered(tr("rysquests.ed.ach.add"), cx + cardW / 2f, cy + CARD_H / 2f + 10, 12.5f, Ui.REGULAR, ui.c(over ? "text.primary" : "text.secondary"));
        ui.hit(cx, cy, cardW, CARD_H, this::newAchievement);
        ui.unclip();
        scroll.content(cy + CARD_H + offset - top + 12, y + h - top);
        if (chosen != null) {
            Achievement data = editor.data().achievements().get(chosen);
            if (data == null) {
                chosen = null;
            } else {
                renderPanel(ui, editor, data, x + w - PANEL_W, y, PANEL_W, h);
            }
        }
        picker.render(ui, x + Math.max(0, (w - TaskPicker.W) / 2f), y + 40);
        choices.render(ui, y + h);
    }

    private void renderSide(Ui ui, EditorBook editor, List<Achievement> all, int x, int y, int h) {
        ui.rect(x, y, SIDE_W, h, ui.c("sidebar.bg"));
        ui.rect(x + SIDE_W - 1, y, 1, h, ui.c("panel.border"));
        ui.caps(tr("rysquests.ed.ach.categories"), x + 14, y + 20, ui.c("text.dim"));
        Map<String, Integer> counts = new LinkedHashMap<>();
        all.forEach(a -> a.category().ifPresent(c -> counts.merge(c, 1, Integer::sum)));
        float px = x + 8;
        float pw = SIDE_W - 17;
        float pen = y + 40;
        pen += catRow(ui, tr("rysquests.ed.ach.all"), all.size(), px, pen, pw, category == null, false, () -> category = null) + 5;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            String key = entry.getKey();
            pen += catRow(ui, editor.text(key), entry.getValue(), px, pen, pw, key.equals(category), true, () -> category = key) + 5;
        }
        boolean over = ui.over(px, pen, pw, 30);
        int fg = ui.c(over ? "text.primary" : "text.muted");
        ui.icon(Icons.Shape.PLUS, px + 12, pen + 9, 12, fg);
        ui.label(tr("rysquests.ed.ach.add_category"), px + 32, pen + 15, 12.5f, Ui.REGULAR, fg);
        ui.hit(px, pen, pw, 30, this::newCategory);
    }

    private static float catRow(Ui ui, String label, int count, float x, float y, float w, boolean on, boolean grip, Runnable action) {
        boolean over = ui.over(x, y, w, 30);
        if (on || over) {
            ui.fill(x, y, w, 30, 7, ui.c(on ? "tab.active.bg" : "button.bg"));
        }
        if (grip) {
            ui.icon(Icons.Shape.GRIP, x + 4, y + 9, 12, ui.c("text.dim"));
        }
        ui.labelFitTip(label, x + 24, y + 15, w - 24 - 40, 12.5f, on ? Ui.MEDIUM : Ui.REGULAR, ui.c(on || over ? "text.primary" : "text.secondary"));
        ui.labelRight(String.valueOf(count), x + w - 10, y + 15, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        ui.hit(x, y, w, 30, action);
        return 30;
    }

    private void card(Ui ui, Achievement achievement, float x, float y, float w) {
        ResourceLocation id = achievement.id();
        GridCard.of("").selected(id.equals(chosen)).onClick(() -> select(id)).body((u, ix, iy, iw, ih) -> {
        }).render(ui, x, y, w, CARD_H);
        ItemStack icon = achievement.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY);
        AchievementRing.render(ui, x + 12, y + 12, 34, icon, null);
        int tiers = achievement.tiers().size();
        float[] fills = new float[tiers];
        LevelSegments.render(ui, x + 12 + 34 + 12, y + 12 + 14, w - 24 - 34 - 12, fills);
        ui.labelFitTip(achievement.title().orElse(id.getPath()), x + 12, y + 12 + 34 + 16, w - 24, 13.5f, Ui.SEMIBOLD, ui.c("text.primary"));
        List<String> lines = ui.wrap(achievement.text().orElse(""), w - 24, 12, Ui.REGULAR, 2);
        for (int i = 0; i < lines.size(); i++) {
            ui.label(lines.get(i), x + 12, y + 12 + 34 + 16 + 20 + i * 16, 12, Ui.REGULAR, ui.c("text.muted"));
        }
        String amounts = String.join(" · ", achievement.tiers().stream().map(t -> Numbers.compact(t.amount())).toList());
        ui.labelFit(amounts, x + 12, y + CARD_H - 18, w - 40, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        ui.icon(Icons.Shape.GRIP, x + w - 22, y + CARD_H - 24, 12, ui.c("text.dim"));
    }

    private void renderPanel(Ui ui, EditorBook editor, Achievement a, int x, int y, int w, int h) {
        ui.rect(x, y, w, h, ui.c("panel.bg"));
        ui.rect(x, y, 1, h, ui.c("panel.border"));
        float bx = x + 1 + 18;
        float bw = w - 1 - 36;
        float footY = y + h - EdUi.FOOT_H;
        ui.clip(x + 1, y, w - 1, Math.round(footY - y));
        int offset = panelScroll.offset();
        float pen = y + 16 - offset;
        String rubric = tr("rysquests.ed.ach.rubric") + a.category().map(c -> " · " + editor.text(c)).orElse("");
        DetailPanel.rubric(ui, bx, pen, bw - 30, rubric, null);
        EdUi.close(ui, bx + bw - 22, pen - 7, () -> chosen = null);
        pen += Ui.lineHeight(11.5f) + 10;
        AchievementRing.render(ui, bx, pen, 46, a.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY), null);
        EdUi.input(ui, fields, "ach-title:" + a.id(), bx + 56, pen + 6, bw - 56, EdUi.INPUT_H, 13, Ui.SEMIBOLD, a.title().map(editor::text).orElse(""), "",
                value -> typeString(a, "title", value));
        pen += 46 + 10;
        EdUi.button(ui, tr("rysquests.ed.chapter.icon"), Icons.Shape.HAND, bx, pen, true, false, false, () -> iconFromHand(a));
        float secretW = ui.width(tr("rysquests.ed.ach.secret_toggle"), 12.5f, Ui.REGULAR);
        ui.label(tr("rysquests.ed.ach.secret_toggle"), bx + bw - 34 - 10 - secretW, pen + 14, 12.5f, Ui.REGULAR, ui.c("text.body"));
        EdUi.toggle(ui, bx + bw - 34, pen + 4, a.hidden(), () -> put(a, withHidden(a, !a.hidden()), tr("rysquests.ed.log.ach_secret", name(editor, a))));
        pen += EdUi.SM_H + 12;
        pen += EdUi.field(ui, fields, "ach-text:" + a.id(), tr("rysquests.ed.ach.description"), bx, pen, bw, 54, a.text().map(editor::text).orElse(""), "",
                value -> typeString(a, "text", value)) + 12;
        // the one condition
        pen += DetailPanel.rubric(ui, bx, pen, bw, tr("rysquests.ed.ach.what"), tr("rysquests.ed.ach.one")) + 6;
        Optional<TaskDef> task = editor.achievementTask(a.id());
        if (task.isPresent()) {
            ItemStack stack = EdText.taskIcon(task.get());
            pen += EdUi.row(ui, bx, pen, bw, stack.isEmpty() ? null : stack, stack.isEmpty() ? (EdText.taskGlyph(task.get()) == null ? Icons.Shape.CHART : EdText.taskGlyph(task.get())) : null,
                    EdText.task(task.get()), tr("rysquests.ed.ach.counted"), null, () -> picker.openTask(t -> setTask(a, t))) + 12;
        } else {
            pen += EdUi.add(ui, tr("rysquests.ed.ach.add_condition"), bx, pen, bw, false, () -> picker.openTask(t -> setTask(a, t))) + 12;
        }
        // the tiers
        pen += DetailPanel.rubric(ui, bx, pen, bw, tr("rysquests.ed.ach.tiers"), tr("rysquests.ed.ach.tiers_note")) + 6;
        for (int i = 0; i < a.tiers().size(); i++) {
            pen += tierRow(ui, editor, a, i, bx, pen, bw) + 6;
        }
        pen += EdUi.add(ui, tr("rysquests.ed.ach.add_tier"), bx, pen, bw, false, () -> addTier(a)) + 12;
        int tiers = a.tiers().size();
        String sees = tr("rysquests.ed.ach.sees", Ui.counted("rysquests.ed.ach.segments", tiers));
        pen += EdUi.note(ui, bx, pen, bw, Icons.Shape.EYE, sees);
        ui.unclip();
        panelScroll.content(pen + offset - y - 16 + 12, footY - y - 16);
        float by = EdUi.foot(ui, x + 1, footY, w - 1);
        EdUi.ghost(ui, tr("rysquests.ed.quest.archive"), Icons.Shape.ARCHIVE, bx - 14, by, () -> {
            put(a, new Achievement(a.id(), a.title(), a.text(), a.hint(), a.icon(), a.category(), a.hidden(), a.tiers(), true), tr("rysquests.ed.log.archive", name(editor, a)));
            chosen = null;
        });
    }

    private float tierRow(Ui ui, EditorBook editor, Achievement a, int index, float x, float y, float w) {
        Achievement.Tier tier = a.tiers().get(index);
        float h = 44;
        ui.box(x, y, w, h, 9, ui.c("card.bg"), ui.c("panel.border"));
        String token = LevelSegments.token(index, a.tiers().size());
        ui.ring(x + 10, y + (h - 14) / 2f, 14, 1.5f, ui.c(token));
        ui.label(tr("rysquests.ed.ach.tier", index + 1), x + 34, y + 14, 12.5f, Ui.SEMIBOLD, ui.c("text.primary"));
        String rewards = tier.rewards().isEmpty() ? tr("rysquests.ed.ach.no_reward") : String.join(" · ", tier.rewards().stream().map(r -> "+" + EdText.rewardShort(r)).toList());
        ui.labelFitTip(rewards, x + 34, y + 30, w - 34 - 86 - 40, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        ui.hit(x + 34, y, w - 34 - 86 - 40, h, () -> picker.openReward(r -> addTierReward(a, index, r)));
        float ix = x + w - 30 - 86;
        ui.box(ix, y + 7, 86, 30, 7, ui.c("input.bg"), ui.c("panel.border"));
        TextArea area = fields.area("ach-tier:" + a.id() + ":" + index, false, 13, Ui.SEMIBOLD, Sidebar.full(tier.amount()), value -> setAmount(a, index, value));
        area.render(ui, ix + 10, y + 13, 66, 18, ui.c("text.primary"), "");
        ui.hit(ix, y + 7, 86, 30, () -> fields.click(area));
        boolean overX = ui.over(x + w - 22, y + 15, 16, 16);
        ui.icon(Icons.Shape.CROSS, x + w - 20, y + 15, 13, ui.c(overX ? "text.primary" : "text.dim"));
        ui.hit(x + w - 24, y + 11, 20, 22, () -> removeTier(a, index));
        return h;
    }

    // --- changes ---

    private static String name(EditorBook editor, Achievement a) {
        return a.title().map(editor::text).orElse(a.id().getPath());
    }

    private static Achievement withHidden(Achievement a, boolean hidden) {
        return new Achievement(a.id(), a.title(), a.text(), a.hint(), a.icon(), a.category(), hidden, a.tiers(), a.archived());
    }

    private static Achievement withTiers(Achievement a, List<Achievement.Tier> tiers) {
        return new Achievement(a.id(), a.title(), a.text(), a.hint(), a.icon(), a.category(), a.hidden(), List.copyOf(tiers), a.archived());
    }

    private void put(Achievement old, Achievement changed, String line) {
        EditorBook editor = book();
        editor.putAchievement(changed, editor.achievementTask(old.id()), line);
    }

    private void typeString(Achievement a, String which, String value) {
        EditorBook editor = book();
        Optional<String> current = which.equals("title") ? a.title() : a.text();
        String key = current.filter(EditorBook::isKey).orElse("achievement." + a.id().getNamespace() + "." + a.id().getPath().replace('/', '.') + "." + which);
        Achievement changed = new Achievement(a.id(), which.equals("title") ? Optional.of(key) : a.title(), which.equals("text") ? Optional.of(key) : a.text(), a.hint(), a.icon(),
                a.category(), a.hidden(), a.tiers(), a.archived());
        Optional<TaskDef> task = editor.achievementTask(a.id());
        editor.type("ach-" + which + ":" + a.id(), "achievement:" + a.id(), tr("rysquests.ed.log.ach_" + which, name(editor, a)),
                d -> d.withAchievement(new works.repka.rysquests.editor.BookDraft.AchievementWork(changed, task)).withText(key, value));
    }

    private void iconFromHand(Achievement a) {
        var player = Minecraft.getInstance().player;
        if (player == null || player.getMainHandItem().isEmpty()) {
            works.repka.rysquests.client.ClientMessages.show("rysquests.ed.hand_empty", List.of(), false);
            return;
        }
        ResourceLocation item = BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem());
        put(a, new Achievement(a.id(), a.title(), a.text(), a.hint(), Optional.of(item), a.category(), a.hidden(), a.tiers(), a.archived()),
                tr("rysquests.ed.log.ach_icon", name(book(), a)));
    }

    private void setTask(Achievement a, TaskDef task) {
        book().putAchievement(a, Optional.of(task), tr("rysquests.ed.log.ach_condition", name(book(), a), EdText.task(task)));
    }

    private void addTier(Achievement a) {
        List<Achievement.Tier> tiers = new ArrayList<>(a.tiers());
        int amount = tiers.isEmpty() ? 10 : tiers.getLast().amount() * 5;
        tiers.add(new Achievement.Tier("tier_" + (tiers.size() + 1), amount, List.of()));
        put(a, withTiers(a, tiers), tr("rysquests.ed.log.ach_tier", name(book(), a), tiers.size()));
    }

    private void removeTier(Achievement a, int index) {
        if (a.tiers().size() <= 1) {
            return;
        }
        List<Achievement.Tier> tiers = new ArrayList<>(a.tiers());
        tiers.remove(index);
        put(a, withTiers(a, tiers), tr("rysquests.ed.log.ach_tier_removed", name(book(), a)));
    }

    private void addTierReward(Achievement a, int index, RewardDef reward) {
        List<Achievement.Tier> tiers = new ArrayList<>(a.tiers());
        Achievement.Tier tier = tiers.get(index);
        List<RewardDef> rewards = new ArrayList<>(tier.rewards());
        rewards.add(reward);
        tiers.set(index, new Achievement.Tier(tier.tier(), tier.amount(), List.copyOf(rewards)));
        put(a, withTiers(a, tiers), tr("rysquests.ed.log.ach_reward", name(book(), a), EdText.rewardShort(reward)));
    }

    private void setAmount(Achievement a, int index, String value) {
        int amount;
        try {
            amount = Integer.parseInt(value.replaceAll("[\\s ]", ""));
        } catch (NumberFormatException e) {
            return;
        }
        if (amount <= 0) {
            return;
        }
        EditorBook editor = book();
        List<Achievement.Tier> tiers = new ArrayList<>(a.tiers());
        Achievement.Tier tier = tiers.get(index);
        tiers.set(index, new Achievement.Tier(tier.tier(), amount, tier.rewards()));
        Achievement changed = withTiers(a, tiers);
        Optional<TaskDef> task = editor.achievementTask(a.id());
        editor.type("ach-tier:" + a.id() + ":" + index, "achievement:" + a.id(), tr("rysquests.ed.log.ach_amount", name(editor, a), index + 1),
                d -> d.withAchievement(new works.repka.rysquests.editor.BookDraft.AchievementWork(changed, task)));
    }

    private void newAchievement() {
        EditorBook editor = book();
        QuestBook data = editor.data();
        String namespace = EditorBook.namespace(data);
        int number = data.achievements().size() + 1;
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, "achievement_" + number);
        while (data.achievements().containsKey(id)) {
            number++;
            id = ResourceLocation.fromNamespaceAndPath(namespace, "achievement_" + number);
        }
        String prefix = "achievement." + namespace + ".achievement_" + number;
        String title = tr("rysquests.ed.ach.new_title");
        Achievement made = new Achievement(id, Optional.of(prefix + ".title"), Optional.empty(), Optional.empty(), Optional.empty(), Optional.ofNullable(category), false,
                List.of(new Achievement.Tier("tier_1", 10, List.of())), false);
        Optional<TaskDef> task = TaskDef.CODEC.parse(EditorBook.ops(), com.google.gson.JsonParser.parseString("{\"type\":\"rysquests:stat\",\"stat\":\"minecraft:mined\",\"target\":\"minecraft:stone\"}"))
                .result();
        editor.edit("achievement:" + id, tr("rysquests.ed.log.new_achievement", title),
                d -> d.withAchievement(new works.repka.rysquests.editor.BookDraft.AchievementWork(made, task)).withText(prefix + ".title", title));
        select(id);
    }

    private void newCategory() {
        EditorBook editor = book();
        String namespace = EditorBook.namespace(editor.data());
        int number = 1;
        String key = "achievement." + namespace + ".category_" + number;
        while (editor.data().achievements().values().stream().anyMatch(a -> a.category().isPresent())
                && editor.data().achievements().values().stream().map(Achievement::category).flatMap(Optional::stream).toList().contains("achievement." + namespace + ".category_" + number)) {
            number++;
            key = "achievement." + namespace + ".category_" + number;
        }
        category = key;
        String made = key;
        editor.type("ach-category:" + key, "meta", tr("rysquests.ed.log.new_category", tr("rysquests.ed.ach.new_category", number)),
                d -> d.withText(made, tr("rysquests.ed.ach.new_category", made.substring(made.lastIndexOf('_') + 1))));
        editor.endTyping();
    }

    @Override
    public boolean key(int key) {
        return picker.key(key) || super.key(key);
    }

    @Override
    public void scroll(double mouseX, double mouseY, double amount) {
        if (picker.isOpen()) {
            return;
        }
        scroll.wheel(amount, 50);
        panelScroll.wheel(amount, 48);
    }

    @Override
    public int[] messageArea(int x, int y, int w, int h) {
        return new int[] {x + SIDE_W, y, w - SIDE_W - (chosen == null ? 0 : PANEL_W), h};
    }

    void newForTest() {
        newAchievement();
    }

    @Nullable
    ResourceLocation chosenForTest() {
        return chosen;
    }
}
