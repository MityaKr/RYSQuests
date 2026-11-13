package works.repka.rysquests.client.screen;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import works.repka.ryscore.ui.Icons;
import works.repka.rysquests.data.RewardDef;
import works.repka.rysquests.data.TaskDef;

/**
 * "Что нужно сделать?" (picture 34): the kinds of tasks in plain words on the left, the form of the chosen kind on the right with the
 * line "Игрок увидит: …", and "Добавить задачу". The same window adds a reward ("Что дать?"). Items are found by name; on the right of
 * each the mod it comes from, or "сборка" for the pack's own items, never a raw id.
 */
final class TaskPicker {
    static final int W = 640;
    static final int H = 520;
    private static final int LIST_W = 258;
    private static final int MAX_FOUND = 40;

    /** A kind of task or reward with its words and how its form looks. */
    private record Kind(String id, Icons.Shape icon, Form form) {
    }

    private enum Form {
        ITEM, FLUID, ENTITY, PLACE, STAT, XP, CHECK, ADVANCEMENT, SCRIPT, COINS, TITLE, COMMAND
    }

    private static final List<Kind> TASKS = List.of(
            new Kind("get", Icons.Shape.HAND, Form.ITEM),
            new Kind("submit", Icons.Shape.ARROW, Form.ITEM),
            new Kind("fluid", Icons.Shape.DOT, Form.FLUID),
            new Kind("kill", Icons.Shape.SKULL, Form.ENTITY),
            new Kind("visit", Icons.Shape.COMPASS, Form.PLACE),
            new Kind("stat", Icons.Shape.CHART, Form.STAT),
            new Kind("xp", Icons.Shape.SPARK, Form.XP),
            new Kind("checkbox", Icons.Shape.CHECK, Form.CHECK),
            new Kind("advancement", Icons.Shape.STAR, Form.ADVANCEMENT),
            new Kind("script", Icons.Shape.GEAR, Form.SCRIPT));
    private static final List<Kind> REWARDS = List.of(
            new Kind("xp", Icons.Shape.SPARK, Form.XP),
            new Kind("coins", Icons.Shape.DOT, Form.COINS),
            new Kind("item", Icons.Shape.HAND, Form.ITEM),
            new Kind("title", Icons.Shape.FLAG, Form.TITLE),
            new Kind("command", Icons.Shape.GEAR, Form.COMMAND));

    private final EditorPage page;
    private boolean open;
    private boolean reward;
    private Consumer<Object> done = value -> { };
    private int kind;
    @Nullable
    private ResourceLocation item;
    private int count = 1;
    private boolean levels = true;
    private int place;
    private String text = "";
    private String text2 = "";
    private String query = "";
    private String lastQuery = "\0";
    private List<ItemStack> found = List.of();

    TaskPicker(EditorPage page) {
        this.page = page;
    }

    boolean isOpen() {
        return open;
    }

    /** Opens on adding a task; the task made goes to {@code add}. */
    void openTask(Consumer<TaskDef> add) {
        open(false, value -> add.accept((TaskDef) value));
    }

    void openReward(Consumer<RewardDef> add) {
        open(true, value -> add.accept((RewardDef) value));
    }

    private void open(boolean forReward, Consumer<Object> then) {
        open = true;
        reward = forReward;
        done = then;
        kind = 0;
        item = null;
        count = 1;
        levels = !forReward;
        place = 0;
        text = "";
        text2 = "";
        query = "";
        lastQuery = "\0";
        page.fields.focus("picker-search");
    }

    void close() {
        open = false;
        page.fields.blur();
    }

    private List<Kind> kinds() {
        return reward ? REWARDS : TASKS;
    }

    private String word(String suffix) {
        return EditorBook.tr("rysquests.ed." + (reward ? "reward" : "task") + "." + kinds().get(kind).id() + suffix);
    }

    // --- drawing ---

    void render(Ui ui, float x, float y) {
        if (!open) {
            return;
        }
        ui.aboveItems(() -> {
            ui.hit(0, 0, 100000, 100000, () -> { });
            ui.shadow(x, y, W, H, 12, 1f);
            ui.box(x, y, W, H, 12, ui.c("sidebar.bg"), ui.c("button.border"));
        });
        ui.aboveItems(() -> {
            float lx = x + 14;
            float ly = y + 14;
            ui.label(EditorBook.tr(reward ? "rysquests.ed.reward.question" : "rysquests.ed.task.question"), lx, ly + 10, 14, Ui.DISPLAY, ui.c("text.primary"));
            float pen = ly + 20 + 6;
            for (int i = 0; i < kinds().size(); i++) {
                Kind k = kinds().get(i);
                boolean on = i == kind;
                boolean over = ui.over(lx, pen, LIST_W, 44);
                if (on || over) {
                    ui.fill(lx, pen, LIST_W, 44, 10, ui.c(on ? "tab.active.bg" : "button.bg"));
                }
                ui.box(lx + 6, pen + 6, 32, 32, 6, ui.c("slot.bg"), ui.c("slot.border"));
                ui.icon(k.icon(), lx + 6 + 9, pen + 6 + 9, 14, ui.c("text.secondary"));
                String prefix = "rysquests.ed." + (reward ? "reward" : "task") + "." + k.id();
                ui.label(EditorBook.tr(prefix), lx + 6 + 32 + 11, pen + 15, 13, Ui.MEDIUM, ui.c("text.primary"));
                ui.label(EditorBook.tr(prefix + ".sub"), lx + 6 + 32 + 11, pen + 31, 11.5f, Ui.REGULAR, ui.c("text.muted"));
                int index = i;
                ui.hit(lx, pen, LIST_W, 44, () -> {
                    kind = index;
                    page.fields.focus(kinds().get(index).form() == Form.ITEM ? "picker-search" : "picker-text");
                });
                pen += 44 + 2;
            }
            float fx = lx + LIST_W + 14;
            ui.rect(fx, y + 14, 1, H - 28, ui.c("panel.border"));
            renderForm(ui, fx + 14, y + 14, x + W - 14 - (fx + 14), y + H - 14);
        });
    }

    private void renderForm(Ui ui, float x, float y, float w, float bottom) {
        Kind k = kinds().get(kind);
        ui.label(word(""), x, y + 10, 14, Ui.DISPLAY, ui.c("text.primary"));
        float pen = y + 20 + 10;
        switch (k.form()) {
            case ITEM -> pen = renderItem(ui, x, pen, w);
            case ENTITY -> pen = renderSearchText(ui, x, pen, w, EditorBook.tr("rysquests.ed.picker.entity"), "minecraft:zombie") + 10;
            case FLUID -> pen = renderSearchText(ui, x, pen, w, EditorBook.tr("rysquests.ed.picker.fluid"), "minecraft:water") + 10;
            case PLACE -> {
                pen += EdUi.wide(ui, x, pen, w, List.of(EditorBook.tr("rysquests.ed.picker.biome"), EditorBook.tr("rysquests.ed.picker.dimension"),
                        EditorBook.tr("rysquests.ed.picker.structure")), List.of(), place, i -> place = i) + 10;
                pen = renderSearchText(ui, x, pen, w, "", place == 0 ? "minecraft:plains" : place == 1 ? "minecraft:the_nether" : "minecraft:village_plains") + 10;
            }
            case STAT -> {
                pen = renderSearchText(ui, x, pen, w, EditorBook.tr("rysquests.ed.picker.stat"), "minecraft:mined") + 10;
                pen += EdUi.field(ui, page.fields, "picker-text2", EditorBook.tr("rysquests.ed.picker.stat_target"), x, pen, w, EdUi.INPUT_H, text2, "minecraft:stone",
                        value -> text2 = value) + 10;
            }
            case XP -> {
                pen += EdUi.wide(ui, x, pen, w, List.of(EditorBook.tr("rysquests.ed.picker.levels"), EditorBook.tr("rysquests.ed.picker.points")), List.of(),
                        levels ? 0 : 1, i -> levels = i == 0) + 10;
            }
            case CHECK, TITLE, COMMAND, ADVANCEMENT, SCRIPT -> pen = renderSearchText(ui, x, pen, w, word(".field"), k.form() == Form.ADVANCEMENT ? "minecraft:story/mine_stone"
                    : k.form() == Form.SCRIPT ? "mypack:my_check" : k.form() == Form.COMMAND ? "say @s" : "") + 10;
            default -> {
            }
        }
        if (k.form() != Form.CHECK && k.form() != Form.TITLE && k.form() != Form.COMMAND && k.form() != Form.ADVANCEMENT && k.form() != Form.SCRIPT && k.form() != Form.PLACE) {
            ui.label(EditorBook.tr("rysquests.ed.picker.count"), x, pen + 17, 12.5f, Ui.REGULAR, ui.c("text.muted"));
            stepper(ui, x + w - 108, pen, count, value -> count = value);
            pen += 34 + 10;
        }
        if (!reward && k.form() == Form.ITEM) {
            ui.label(EditorBook.tr("rysquests.ed.picker.item_mode"), x, pen + 17, 12.5f, Ui.REGULAR, ui.c("text.muted"));
            List<Segments.Item> parts = List.of(Segments.item(EditorBook.tr("rysquests.ed.task.sub.kept.short")), Segments.item(EditorBook.tr("rysquests.ed.task.sub.taken.short")));
            int sel = k.id().equals("submit") ? 1 : 0;
            float sw = Segments.width(parts, sel);
            Segments.render(ui, x + w - sw, pen, parts, sel, i -> kind = i == 0 ? 0 : 1);
            pen += 34 + 10;
        }
        Object made = make();
        String sees = made instanceof TaskDef task ? EdText.task(task) : made instanceof RewardDef rewardDef ? EdText.rewardShort(rewardDef) : "…";
        pen += EdUi.note(ui, x, pen, w, Icons.Shape.EYE, EditorBook.tr("rysquests.ed.picker.sees", sees));
        float by = bottom - EdUi.BTN_H;
        String add = EditorBook.tr(reward ? "rysquests.ed.reward.add" : "rysquests.ed.task.add");
        float addW = EdUi.buttonWidth(ui, add, null, false);
        EdUi.button(ui, add, null, x + w - addW, by, false, false, true, made == null ? null : () -> {
            Object value = make();
            if (value != null) {
                close();
                done.accept(value);
            }
        });
        String cancel = EditorBook.tr("rysquests.ed.picker.cancel");
        float cancelW = EdUi.buttonWidth(ui, cancel, null, false);
        EdUi.button(ui, cancel, null, x + w - addW - 8 - cancelW, by, false, false, false, this::close);
    }

    private float renderItem(Ui ui, float x, float y, float w) {
        ItemStack chosen = item == null ? ItemStack.EMPTY : TaskText.stackOf(item);
        EdUi.plate(ui, chosen, Icons.Shape.HAND, x, y, 44);
        float fx = x + 44 + 10;
        float fw = w - 44 - 10;
        TextArea area = page.fields.area("picker-search", false, 13, Ui.REGULAR, query, value -> query = value);
        ui.box(fx, y, fw, EdUi.INPUT_H, 8, ui.c("input.bg"), ui.c(area.focused() ? "text.secondary" : "panel.border"));
        ui.icon(Icons.Shape.MAGNIFIER, fx + 10, y + 10, 14, ui.c("text.muted"));
        area.render(ui, fx + 32, y + 8, fw - 42, 18, ui.c("text.primary"), EditorBook.tr("rysquests.ed.picker.search"));
        ui.hit(fx, y, fw, EdUi.INPUT_H, () -> page.fields.click(area));
        float by = y + EdUi.INPUT_H + 6;
        float bw = EdUi.button(ui, EditorBook.tr("rysquests.ed.picker.from_hand"), Icons.Shape.HAND, fx, by, true, false, false, this::fromHand);
        EdUi.button(ui, EditorBook.tr("rysquests.ed.picker.any_tag"), null, fx + bw + 6, by, true, false, false, () -> {
            if (item != null) {
                query = "#" + item;
            }
        });
        float pen = y + 44 + 6 + 28 + 10;
        search();
        int rows = Math.min(found.size(), 5);
        for (int i = 0; i < rows; i++) {
            ItemStack stack = found.get(i);
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            boolean on = id.equals(item);
            boolean over = ui.over(x, pen, w, 40);
            if (on || over) {
                ui.box(x, pen, w, 40, 10, ui.c(on ? "tab.active.bg" : "button.bg"), ui.c(on ? "button.border" : "button.bg"));
            }
            ui.box(x + 6, pen + 7, 26, 26, 5, ui.c("slot.bg"), ui.c("slot.border"));
            ui.item(stack, x + 6 + 5, pen + 12, 16);
            String mod = modName(id);
            float modW = ui.width(mod, 12, Ui.REGULAR);
            ui.labelRight(mod, x + w - 10, pen + 20, 12, Ui.REGULAR, ui.c("text.muted"));
            ui.labelFitTip(stack.getHoverName().getString(), x + 6 + 26 + 11, pen + 20, w - 6 - 26 - 11 - modW - 20, 13, Ui.MEDIUM, ui.c("text.primary"));
            ui.hit(x, pen, w, 40, () -> item = id);
            pen += 40 + 4;
        }
        return pen + 6;
    }

    private float renderSearchText(Ui ui, float x, float y, float w, String label, String hint) {
        if (label.isEmpty()) {
            return y + EdUi.input(ui, page.fields, "picker-text", x, y, w, EdUi.INPUT_H, 13, Ui.REGULAR, text, hint, value -> text = value);
        }
        return y + EdUi.field(ui, page.fields, "picker-text", label, x, y, w, EdUi.INPUT_H, text, hint, value -> text = value);
    }

    private static void stepper(Ui ui, float x, float y, int value, java.util.function.IntConsumer set) {
        ui.box(x, y, 108, 34, 9, ui.c("button.bg"), ui.c("button.border"));
        boolean overMinus = ui.over(x, y, 32, 34);
        boolean overPlus = ui.over(x + 76, y, 32, 34);
        ui.icon(Icons.Shape.MINUS, x + 10, y + 10, 14, ui.c(overMinus ? "text.primary" : "text.secondary"));
        ui.labelCentered(String.valueOf(value), x + 54, y + 17, 14, Ui.SEMIBOLD, ui.c("text.primary"));
        ui.icon(Icons.Shape.PLUS, x + 76 + 9, y + 10, 14, ui.c(overPlus ? "text.primary" : "text.secondary"));
        ui.hit(x, y, 32, 34, () -> set.accept(Math.max(1, value - (net.minecraft.client.gui.screens.Screen.hasShiftDown() ? 10 : 1))));
        ui.hit(x + 76, y, 32, 34, () -> set.accept(value + (net.minecraft.client.gui.screens.Screen.hasShiftDown() ? 10 : 1)));
    }

    /** The mod of an item in words: its name, or "сборка" for the pack's own (KubeJS, the book's namespace). */
    static String modName(ResourceLocation id) {
        String ns = id.getNamespace();
        if (ns.equals("kubejs") || ns.equals(EditorBook.namespace(EditorBook.get().data()))) {
            return EditorBook.tr("rysquests.ed.picker.pack");
        }
        return net.neoforged.fml.ModList.get().getModContainerById(ns).map(container -> container.getModInfo().getDisplayName()).orElse(ns);
    }

    private void fromHand() {
        var player = Minecraft.getInstance().player;
        if (player != null && !player.getMainHandItem().isEmpty()) {
            item = BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem());
            count = Math.max(1, player.getMainHandItem().getCount());
        }
    }

    private void search() {
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.equals(lastQuery)) {
            return;
        }
        lastQuery = q;
        List<ItemStack> out = new ArrayList<>();
        if (q.length() >= 2) {
            for (Item each : BuiltInRegistries.ITEM) {
                ItemStack stack = new ItemStack(each);
                if (stack.isEmpty()) {
                    continue;
                }
                String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
                if (name.contains(q) || BuiltInRegistries.ITEM.getKey(each).getPath().contains(q)) {
                    out.add(stack);
                    if (out.size() >= MAX_FOUND) {
                        break;
                    }
                }
            }
            // the pack's own items first: they are the ones a pack's book is about
            out.sort(java.util.Comparator.comparingInt(stack -> modName(BuiltInRegistries.ITEM.getKey(stack.getItem())).equals(EditorBook.tr("rysquests.ed.picker.pack")) ? 0 : 1));
        }
        found = out;
        if (item == null && !out.isEmpty()) {
            item = BuiltInRegistries.ITEM.getKey(out.getFirst().getItem());
        }
    }

    /** The task or reward the form describes now, or null while it is not complete. */
    @Nullable
    private Object make() {
        Kind k = kinds().get(kind);
        JsonObject json = new JsonObject();
        String typed = text.trim();
        switch (k.form()) {
            case ITEM -> {
                if (item == null) {
                    return null;
                }
                json.addProperty("type", "rysquests:item");
                json.addProperty("item", item.toString());
                json.addProperty("count", count);
                if (!reward) {
                    json.addProperty("mode", k.id().equals("submit") ? "consume" : "observe");
                }
            }
            case FLUID -> {
                json.addProperty("type", "rysquests:fluid");
                json.addProperty("fluid", typed.isEmpty() ? "minecraft:water" : typed);
                json.addProperty("amount", count * 1000);
            }
            case ENTITY -> {
                json.addProperty("type", "rysquests:kill");
                json.addProperty("entity", typed.isEmpty() ? "minecraft:zombie" : typed);
                json.addProperty("count", count);
            }
            case PLACE -> {
                json.addProperty("type", "rysquests:visit");
                if (typed.isEmpty()) {
                    return null;
                }
                json.addProperty(place == 0 ? "biome" : place == 1 ? "dimension" : "structure", typed);
            }
            case STAT -> {
                json.addProperty("type", "rysquests:stat");
                json.addProperty("stat", typed.isEmpty() ? "minecraft:mined" : typed);
                json.addProperty("target", text2.isBlank() ? "minecraft:stone" : text2.trim());
                json.addProperty("count", count);
            }
            case XP -> {
                json.addProperty("type", "rysquests:xp");
                json.addProperty(levels ? "levels" : "points", count);
                if (!reward && k.id().equals("xp")) {
                    json.addProperty("consume", true);
                }
            }
            case CHECK -> {
                json.addProperty("type", "rysquests:checkbox");
            }
            case ADVANCEMENT -> {
                if (typed.isEmpty()) {
                    return null;
                }
                json.addProperty("type", "rysquests:advancement");
                json.addProperty("advancement", typed);
            }
            case SCRIPT -> {
                if (typed.isEmpty()) {
                    return null;
                }
                json.addProperty("type", "rysquests:script");
                json.addProperty("script", typed);
            }
            case COINS -> {
                json.addProperty("type", "rysquests:currency");
                json.addProperty("id", "coin");
                json.addProperty("amount", count);
            }
            case TITLE -> {
                if (typed.isEmpty()) {
                    return null;
                }
                json.addProperty("type", "rysquests:title");
                json.addProperty("id", typed);
            }
            case COMMAND -> {
                if (typed.isEmpty()) {
                    return null;
                }
                json.addProperty("type", "rysquests:command");
                json.addProperty("command", typed);
            }
        }
        try {
            Optional<?> parsed = reward ? RewardDef.CODEC.parse(EditorBook.ops(), json).result() : TaskDef.CODEC.parse(EditorBook.ops(), json).result();
            return parsed.orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Enter adds what the form describes. */
    boolean key(int key) {
        if (!open) {
            return false;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            Object value = make();
            if (value != null) {
                close();
                done.accept(value);
            }
            return true;
        }
        return false;
    }

    // --- tests ---

    void chooseForTest(String kindId, @Nullable ResourceLocation newItem, int newCount, String newText) {
        for (int i = 0; i < kinds().size(); i++) {
            if (kinds().get(i).id().equals(kindId)) {
                kind = i;
            }
        }
        item = newItem;
        count = newCount;
        text = newText;
    }

    void confirmForTest() {
        key(GLFW.GLFW_KEY_ENTER);
    }

    void queryForTest(String q) {
        query = q;
    }
}
