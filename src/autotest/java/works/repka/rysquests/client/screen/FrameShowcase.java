package works.repka.rysquests.client.screen;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import works.repka.ryscore.ui.Icons;

/**
 * Every shared component of the frame on one page, with the words of the mockups, for the autotest's screenshot and as an example of
 * their use. Part of the autotest only, never of the mod.
 */
public final class FrameShowcase implements BookView {
    private final DetailPanel panel = new DetailPanel();
    private int segment;

    /** Opens the showcase as a page of the open book. */
    public static void open() {
        if (Minecraft.getInstance().screen instanceof BookScreen screen) {
            screen.openPage(new FrameShowcase());
        }
    }

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        int panelW = DetailPanel.WIDTH;
        int left = x + 20;
        int columnW = (w - panelW - 20 * 3) / 2;
        float pen = y + 12;
        Segments.render(ui, left, pen, List.of(Segments.item("Карта", Icons.Shape.MAP), Segments.item("Список", Icons.Shape.LIST)), segment,
                index -> segment = index);
        pen += Segments.HEIGHT + 14;
        pen += DetailPanel.rubric(ui, left, pen, columnW, "Забрать награды · 2", null) + 8;
        pen += ListRow.of("Медная катушка").icon(new ItemStack(Items.COPPER_INGOT)).sub("Ток · 25 опыта и 1 монета").link("Забрать", () -> { })
                .render(ui, left, pen, columnW) + 8;
        pen += ListRow.of("Цель автоматизации: 64× Андезитовый сплав").icon(new ItemStack(Items.ANDESITE)).sub("Андезит · побочный · сдано 40 из 64")
                .progress(0.62f).right(Icons.Shape.PIN, null, "text.muted").render(ui, left, pen, columnW) + 8;
        pen += ListRow.of("Медная катушка").icon(new ItemStack(Items.COPPER_INGOT)).sub("шаг 6 · побочных 4 из 5").right("награда ждёт")
                .selected(true).render(ui, left, pen, columnW) + 8;
        pen += ListRow.of("Катушка медного провода").icon(new ItemStack(Items.COPPER_BLOCK)).right(Icons.Shape.CHECK, null, "text.muted")
                .look(ListRow.Look.CHILD).render(ui, left, pen, columnW) + 8;
        pen += ListRow.of("Медный провод").icon(new ItemStack(Items.REDSTONE)).sub("шаг 2").right(Icons.Shape.CHECK, "выполнен", "state.ok")
                .render(ui, left, pen, columnW) + 8;
        pen += ListRow.of("Трансформатор").icon(new ItemStack(Items.IRON_BLOCK)).sub("с эпохи III").locked(true).chevron()
                .render(ui, left, pen, columnW) + 12;
        ui.box(left, pen, columnW, y + h - 12 - pen, 12, 0, ui.c("panel.border"));
        EmptyFilter.render(ui, left, pen, columnW, y + h - 12 - pen, "Полученных достижений пока нет", "Показать все", () -> { });

        int mid = left + columnW + 20;
        float grid = y + 12 + Segments.HEIGHT + 14;
        float cardW = (columnW - 10) / 2f;
        GridCard.of("Андезитовый сплав").icon(new ItemStack(Items.ANDESITE), 64).meta("нужно для квеста", "text.secondary").price(4, false)
                .button("Купить", () -> { }).render(ui, mid, grid, cardW, 120);
        GridCard.of("Жетон инженера").icon(new ItemStack(Items.GOLD_NUGGET), 1).meta("0 из 2 в неделю").price(2400, true)
                .button("Купить", () -> { }).selected(true).render(ui, mid + cardW + 10, grid, cardW, 120);
        grid += 130;
        achievement(ui, mid, grid, cardW, "Шахтёр", "Добывай камень своими руками.", "4.4к из 10к", new float[] {1f, 0.37f, 0f}, 0, true);
        achievement(ui, mid + cardW + 10, grid, cardW, "Ветеран", "Время в этом мире.", "13 из 24 ч", new float[] {1f, 0.3f, 0f, 0f}, 0, false);
        grid += 160;
        achievement(ui, mid, grid, cardW, "Иссушитель", "Победи этого босса.", "все уровни получены", new float[] {1f}, 0, false);
        achievement(ui, mid + cardW + 10, grid, cardW, "Медная катушка", "Держи в инвентаре много штук сразу. Ничего не забирается.", "41 из 64",
                new float[] {0.64f, 0f, 0f}, -1, false);

        panel.render(ui, x + w - panelW, y, panelW, h, (u, bx, by, bw) -> {
            float p = by + DetailPanel.header(u, bx, by, bw, new ItemStack(Items.CLOCK), "Электромеханизм", "Ток · основной · шаг 7 из 15", () -> { }, false, null);
            p += DetailPanel.GAP;
            p += DetailPanel.rubric(u, bx, p, bw, "Что сделать", "1 из 4") + 6;
            p += TaskRow.of("Получить: Электромеханизм").item(new ItemStack(Items.CLOCK), 1, TaskRow.Slot.HAVE).line("готово · предмет остался у тебя")
                    .state(TaskRow.State.DONE).render(u, bx, p, bw) + 6;
            p += TaskRow.of("Сдать: Электромеханизм ×32").item(new ItemStack(Items.CLOCK), 32, TaskRow.Slot.MISSING).line("сдано 12 из 32 · в инвентаре 8, можно сдать")
                    .progress(12 / 32f).state(TaskRow.State.READY).render(u, bx, p, bw) + 6;
            p += TaskRow.of("Получить: Стальной слиток ×8").item(new ItemStack(Items.IRON_INGOT), 8, TaskRow.Slot.MISSING).line("в инвентаре 6 из 8")
                    .need("Не хватает 2 шт.", "Как сделать", () -> { }).render(u, bx, p, bw) + 6;
            p += TaskRow.of("Победить: Эндермен ×25").glyph(Icons.Shape.SKULL).line("побед 11 из 25 · считаются победы всей команды").progress(0.44f)
                    .render(u, bx, p, bw) + DetailPanel.GAP;
            p += DetailPanel.rubric(u, bx, p, bw, "Награда", "каждому") + 6;
            p += RewardChip.row(u, bx, p, bw, List.of(RewardChip.xp(25, "опыта"), RewardChip.coins(1, "монета"),
                    RewardChip.item(new ItemStack(Items.IRON_INGOT), 12, "Стальной слиток"), RewardChip.glyph(Icons.Shape.FLAG, "титул «Шахтёр»"))) + 6;
            return p - by;
        }, DetailPanel.Action.button("Как сделать: Электромеханизм", () -> { }));
    }

    private static void achievement(Ui ui, float x, float y, float w, String name, String text, String count, float[] levels, int reached, boolean selected) {
        GridCard.of(name).selected(selected).body((u, ix, iy, iw, ih) -> {
            AchievementRing.render(u, ix, iy, 40, new ItemStack(Items.IRON_PICKAXE), reached >= 0 ? LevelSegments.token(reached, levels.length) : null);
            LevelSegments.render(u, ix + 40 + 12, iy + 16, iw - 52, levels);
            u.labelFit(name, ix, iy + 40 + 3 + 9, iw, 13, Ui.SEMIBOLD, u.c("text.primary"));
            List<String> lines = u.wrap(text, iw, 11.5f, Ui.REGULAR, 2);
            for (int i = 0; i < lines.size(); i++) {
                u.label(lines.get(i), ix, iy + 40 + 3 + 18 + 5 + 8 + i * 15.5f, 11.5f, Ui.REGULAR, u.c("text.muted"));
            }
            u.label(count, ix, iy + ih - 8, 11.5f, Ui.REGULAR, u.c(count.startsWith("все") ? "state.ok" : "text.secondary"));
        }).render(ui, x, y, w, 150);
    }

    @Override
    public void scroll(double mouseX, double mouseY, double amount) {
        if (panel.contains(mouseX, mouseY)) {
            panel.scroll(amount);
        }
    }

    @Override
    public int[] messageArea(int x, int y, int w, int h) {
        return new int[] {x, y, w - DetailPanel.WIDTH, h};
    }
}
