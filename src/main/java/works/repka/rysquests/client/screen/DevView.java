package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientDev;
import works.repka.rysquests.client.input.QuestKeys;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.ViewMode;
import works.repka.rysquests.dev.DevReports;
import works.repka.rysquests.network.QuestActionPayload;

/** Book reports and maintenance for operators, from {@code design/Dev.dc.html}. Everything here also exists as a command. */
final class DevView implements BookView {
    private static final int WHEEL = 48;
    private static final int PAD_X = 20;
    private static final int PAD_Y = 18;
    private static final float LEFT_SHARE = 1.4f / 2.4f;
    private static final int GAP = 16;
    private static final int FUNNEL_ROW = 22;
    private static final int REPORT_ROWS = 5;
    private static final int BOTTLENECK_MIN_FALL = 15;
    private static final int NOTE_MIN = 90;

    private enum Tab {
        FUNNEL, REPORTS, MIGRATIONS, IMPORT
    }

    private record Step(Quest quest, int percent) {
    }

    private Tab tab = Tab.FUNNEL;
    private boolean requested;
    private String openSection = "";
    private final Scroll scroll = new Scroll();

    void renderBar(Ui ui, int x, int y, int w, int barH) {
        ui.fillCorners(x, y, w, barH, 13, ui.c("sidebar.bg"), true, true, false, false);
        ui.rect(x, y + barH - 1, w, 1, ui.c("panel.border"));
        float cy = y + (barH - 1) / 2f;
        BookScreen.renderBrand(ui, x + 18, cy);
        ui.hit(x + 18, cy - 13, 120, 26, () -> BookState.view = ViewMode.FLOW);
        float chipX = x + 18 + 36 + ui.width("RYSQuests", 16, Ui.DISPLAY) + 10;
        float chipW = ui.pill("Dev", chipX, cy, 11, Ui.SEMIBOLD, 8, 18, 9, ui.c("chip.mod.bg"), ui.c("chip.mod.fg"));
        float cursor = chipX + chipW + 22;
        String constructor = Component.translatable("rysquests.editor.open_constructor").getString();
        float constructorW = ui.buttonWidth(constructor, 11.5f, Ui.REGULAR, 10) + 2;
        ui.button(constructor, x + w - 18 - constructorW, cy - 15, constructorW, 30, 8, 11.5f, Ui.REGULAR, 10, Ui.Style.PLAIN, () -> {
            if (net.minecraft.client.Minecraft.getInstance().screen instanceof BookScreen screen) {
                screen.openSectionEditor(null);
            }
        });
        DevReports.Report report = ClientDev.report();
        for (Tab option : Tab.values()) {
            boolean active = option == tab;
            String label = Component.translatable("rysquests.dev.tab." + option.name().toLowerCase(java.util.Locale.ROOT)).getString();
            var weight = active ? Ui.MEDIUM : Ui.REGULAR;
            int problems = option == Tab.REPORTS && report != null ? problems(report) : 0;
            float badgeW = problems > 0 ? ui.width(String.valueOf(problems), 10.5f, Ui.SEMIBOLD) + 12 : 0;
            float tabW = ui.width(label, 12.5f, weight) + 24 + badgeW;
            if (active || ui.over(cursor, cy - 13.5f, tabW, 27)) {
                ui.fill(cursor, cy - 13.5f, tabW, 27, 8, ui.c("button.bg"));
            }
            ui.label(label, cursor + 12, cy, 12.5f, weight, ui.c(active ? "text.primary" : "tab.inactive.fg"));
            if (problems > 0) {
                ui.pill(String.valueOf(problems), cursor + tabW - 12 - badgeW, cy, 10.5f, Ui.SEMIBOLD, 6, 13, 6.5f, ui.mix("slot.bg", "state.bad", 0.19f), ui.c("state.bad"));
            }
            ui.hit(cursor, cy - 13.5f, tabW, 27, () -> {
                tab = option;
                scroll.reset();
            });
            cursor += tabW + 4;
        }
        String export = Component.translatable("rysquests.dev.export").getString();
        float exportW = ui.buttonWidth(export, 11.5f, Ui.REGULAR, 11);
        ui.button(export, x + w - 18 - exportW, cy - 15, exportW, 30, 8, 11.5f, Ui.REGULAR, 11, Ui.Style.PLAIN, () -> command("rysquests dev report"));
        if (report != null) {
            String summary = Component.translatable("rysquests.dev.summary", report.chapters(), report.quests()).getString();
            if (x + w - 18 - exportW - 12 - ui.width(summary, 11.5f, Ui.REGULAR) >= cursor + 12) {
                ui.labelRight(summary, x + w - 18 - exportW - 12, cy, 11.5f, Ui.REGULAR, ui.c("text.muted"));
            }
        }
    }

    private static int problems(DevReports.Report report) {
        int count = 0;
        for (DevReports.Section section : report.sections()) {
            if (isShownSection(section)) {
                count += section.entries().size();
            }
        }
        return count;
    }

    private static boolean isShownSection(DevReports.Section section) {
        return !section.id().startsWith(DevReports.FUNNEL_PREFIX) && !section.id().equals(DevReports.MIGRATIONS);
    }

    private static void command(String command) {
        Minecraft.getInstance().player.connection.sendCommand(command);
    }

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        if (!requested) {
            requested = true;
            QuestKeys.sendAll(QuestActionPayload.DEV_REPORT);
        }
        DevReports.Report report = ClientDev.report();
        ui.clip(x, y, w, h - 12);
        if (report == null) {
            ui.label(Component.translatable("rysquests.dev.waiting").getString(), x + PAD_X, y + PAD_Y + 8, 12, Ui.REGULAR, ui.c("text.muted"));
        } else {
            switch (tab) {
                case FUNNEL -> renderFunnelPage(ui, report, x, y, w, h);
                case REPORTS -> renderReportsPage(ui, report, x, y, w, h);
                case MIGRATIONS -> renderMigrationsPage(ui, report, x, y, w, h);
                default -> renderImportPage(ui, x, y, w);
            }
        }
        ui.unclip();
    }

    private void renderFunnelPage(Ui ui, DevReports.Report report, int x, int y, int w, int h) {
        float content = w - PAD_X * 2 - GAP;
        float leftW = content * LEFT_SHARE;
        float rightW = content - leftW;
        float left = x + PAD_X;
        float top = y + PAD_Y;
        renderFunnel(ui, report, left, top, leftW, h - PAD_Y * 2);
        float rightX = left + leftW + GAP;
        float boxH = renderReportsBox(ui, report, rightX, top, rightW);
        renderMigrationsBox(ui, report, rightX, top + boxH + 12, rightW, h - PAD_Y * 2 - boxH - 12);
    }

    private static List<Step> steps(DevReports.Report report) {
        List<Step> steps = new ArrayList<>();
        String wanted = DevReports.FUNNEL_PREFIX + BookState.chapter;
        for (DevReports.Section section : report.sections()) {
            if (section.id().equals(wanted)) {
                for (DevReports.Entry entry : section.entries()) {
                    Quest quest = ClientBook.current().quests().get(ResourceLocation.tryParse(entry.subject()));
                    if (quest != null) {
                        steps.add(new Step(quest, Integer.parseInt(entry.detail())));
                    }
                }
            }
        }
        return steps;
    }

    private void renderFunnel(Ui ui, DevReports.Report report, float x, float y, float w, float h) {
        String number = Ui.roman(BookScreen.chapterNumber(BookState.chapter));
        ui.label(Component.translatable("rysquests.dev.funnel_title", number).getString(), x, y + 10.5f, 16, Ui.DISPLAY, ui.c("text.primary"));
        ui.labelRight(Component.translatable("rysquests.dev.funnel_hint").getString(), x + w, y + 12, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        List<Step> steps = steps(report);
        float boxY = y + 31;
        int rows = Math.min(steps.size(), Math.max(1, (int) ((h - 31 - 30 - 130) / FUNNEL_ROW)));
        float boxH = rows * FUNNEL_ROW - 6 + 30;
        ui.box(x, boxY, w, Math.max(boxH, 60), 12, ui.c("card.bg"), ui.c("panel.border"));
        Step worst = worst(steps);
        float barX = x + 195;
        float barW = w - 195 - 15 - 10 - 52 - 10 - 90;
        for (int i = 0; i < rows; i++) {
            Step step = steps.get(i);
            float rowY = boxY + 15 + i * FUNNEL_ROW;
            float cy = rowY + 8;
            boolean bad = worst != null && step == worst;
            int fg = ui.c(bad ? "state.bad" : "text.body");
            ui.labelFit(Component.translatable(step.quest.title()).getString(), x + 15, cy, 170, 12, Ui.REGULAR, fg);
            ui.fill(barX, rowY, barW, 16, 4, ui.c("slot.bg"));
            int color = bad ? ui.c("state.bad") : step.percent >= 50 ? ui.c("state.ok") : i == rows - 1 ? ui.c("chip.mod.fg") : ui.c("accent");
            if (step.percent > 0) {
                ui.fill(barX, rowY, barW * step.percent / 100f, 16, 4, color);
            }
            ui.labelRight(step.percent + "%", barX + barW + 10 + 52, cy, 12, Ui.REGULAR, fg);
        }
        if (steps.isEmpty()) {
            ui.label(Component.translatable("rysquests.dev.funnel_empty").getString(), x + 15, boxY + 30, 12, Ui.REGULAR, ui.c("text.muted"));
        }
        if (worst != null) {
            renderBottleneck(ui, steps, worst, x, boxY + boxH + 10, w);
        }
    }

    /** The step that loses the largest share of players compared to the one before it. */
    private static Step worst(List<Step> steps) {
        Step worst = null;
        int fall = BOTTLENECK_MIN_FALL;
        for (int i = 1; i < steps.size(); i++) {
            int drop = steps.get(i - 1).percent - steps.get(i).percent;
            if (drop > fall) {
                fall = drop;
                worst = steps.get(i);
            }
        }
        return worst;
    }

    private void renderBottleneck(Ui ui, List<Step> steps, Step worst, float x, float y, float w) {
        ui.box(x, y, w, 118, 12, ui.warmBorder(0.11f), ui.warmBorder(0.35f));
        ui.fill(x + 15, y + 13, 8, 92, 4, ui.c("accent"));
        String title = Component.translatable(worst.quest.title()).getString();
        ui.labelFit(Component.translatable("rysquests.dev.bottleneck", title).getString(), x + 35, y + 21.5f, w - 50, 12, Ui.SEMIBOLD, ui.c("text.primary"));
        int before = steps.get(steps.indexOf(worst) - 1).percent;
        String body = Component.translatable("rysquests.dev.bottleneck_text", before, worst.percent).getString();
        ui.lines(ui.wrap(body, w - 50, 12, Ui.REGULAR, 2), x + 35, y + 52.5f, 16.8f, 12, Ui.REGULAR, ui.c("text.body"));
        String open = Component.translatable("rysquests.dev.open_quest").getString();
        float openW = ui.buttonWidth(open, 11, Ui.SEMIBOLD, 10);
        ui.button(open, x + 35, y + 79, openW, 26, 7, 11, Ui.SEMIBOLD, 10, Ui.Style.ACCENT, () -> {
            BookState.select(worst.quest);
            BookState.view = ViewMode.FLOW;
        });
    }

    private List<DevReports.Section> sections(DevReports.Report report) {
        List<DevReports.Section> sections = new ArrayList<>(report.sections().stream().filter(DevView::isShownSection).toList());
        sections.add(untranslated());
        sections.add(GuideCheck.section());
        return sections;
    }

    private float renderReportsBox(Ui ui, DevReports.Report report, float x, float y, float w) {
        List<DevReports.Section> sections = sections(report);
        int rows = Math.min(REPORT_ROWS, sections.size());
        float h = 15 + 24 + 8 + rows * 32 + (rows - 1) * 8 + 15;
        ui.box(x, y, w, h, 12, ui.c("card.bg"), ui.c("panel.border"));
        ui.caps(Component.translatable("rysquests.dev.reports").getString(), x + 15, y + 27, ui.c("text.dim"));
        String recheck = Component.translatable("rysquests.dev.recheck").getString();
        float recheckW = ui.buttonWidth(recheck, 11, Ui.REGULAR, 9);
        ui.button(recheck, x + w - 15 - recheckW, y + 15, recheckW, 24, 6, 11, Ui.REGULAR, 9, Ui.Style.PLAIN, () -> QuestKeys.sendAll(QuestActionPayload.DEV_REPORT));
        for (int i = 0; i < rows; i++) {
            DevReports.Section section = sections.get(i);
            float rowY = y + 47 + i * 40;
            float rowW = w - 30;
            float cy = rowY + 16;
            ui.box(x + 15, rowY, rowW, 32, 8, ui.c("status.bg"), ui.c("panel.border"));
            badge(ui, section, x + 25, cy);
            ui.labelFit(sectionTitle(section), x + 73, cy, rowW - 58 - 30, 12, Ui.REGULAR, ui.c("text.body"));
            ui.chevron(false, x + 15 + rowW - 18, cy, 9, ui.c("chip.mod.fg"));
            ui.hit(x + 15, rowY, rowW, 32, () -> {
                tab = Tab.REPORTS;
                openSection = section.id();
            });
        }
        return h;
    }

    private static String sectionTitle(DevReports.Section section) {
        return Component.translatable("rysquests.dev.section." + section.id()).getString();
    }

    private static void badge(Ui ui, DevReports.Section section, float x, float cy) {
        int count = section.entries().size();
        boolean severe = section.id().equals("errors") || section.id().equals("unreachable");
        int bg = count == 0 ? ui.c("chip.tag.bg") : severe ? ui.mix("slot.bg", "state.bad", 0.19f) : ui.warmBorder(0.17f);
        int fg = count == 0 ? ui.c("state.ok") : severe ? ui.c("state.bad") : ui.c("accent");
        ui.fill(x, cy - 8, 38, 16, 8, bg);
        ui.labelCentered(String.valueOf(count), x + 19, cy, 11, Ui.SEMIBOLD, fg);
    }

    private void renderMigrationsBox(Ui ui, DevReports.Report report, float x, float y, float w, float h) {
        ui.box(x, y, w, h, 12, ui.c("card.bg"), ui.c("panel.border"));
        ui.caps(Component.translatable("rysquests.dev.migrations_title").getString(), x + 15, y + 22, ui.c("text.dim"));
        List<DevReports.Entry> entries = migrations(report);
        String file = Component.translatable("rysquests.dev.migrations_file", entries.size()).getString();
        if (ui.capsWidth(Component.translatable("rysquests.dev.migrations_title").getString()) + ui.width(file, 11, Ui.REGULAR) + 12 <= w - 30) {
            ui.labelRight(file, x + w - 15, y + 22, 11, Ui.REGULAR, ui.c("text.muted"));
        }
        float boxY = y + 37;
        float boxH = Math.min(h - 37 - 15 - 32 - 10, 122);
        ui.box(x + 15, boxY, w - 30, boxH, 8, ui.c("slot.bg"), ui.c("slot.border"));
        migrationLines(ui, report, entries, x + 26, boxY + 19, w - 52, boxH - 12);
        float rowY = y + h - 15 - 32;
        String apply = Component.translatable("rysquests.dev.migrate").getString();
        String open = Component.translatable("rysquests.dev.open_file").getString();
        float applyW = ui.buttonWidth(apply, 12, Ui.SEMIBOLD, 14);
        float openW = ui.buttonWidth(open, 12, Ui.REGULAR, 12) + 2;
        ui.button(apply, x + 15, rowY, applyW, 32, 8, 12, Ui.SEMIBOLD, 14, Ui.Style.ACCENT, () -> command("rysquests migrate --apply"));
        ui.button(open, x + 15 + applyW + 8, rowY, openW, 32, 8, 12, Ui.REGULAR, 12, Ui.Style.PLAIN, () -> command("rysquests migrate --dry-run"));
        float noteX = x + 15 + applyW + 8 + openW + 8;
        if (x + w - 15 - noteX >= NOTE_MIN) {
            ui.lines(ui.wrap(Component.translatable("rysquests.dev.backup_note").getString(), x + w - 15 - noteX, 11, Ui.REGULAR, 2), noteX, rowY + 16, 14, 11, Ui.REGULAR, ui.c("text.muted"));
        }
    }

    private static List<DevReports.Entry> migrations(DevReports.Report report) {
        for (DevReports.Section section : report.sections()) {
            if (section.id().equals(DevReports.MIGRATIONS)) {
                return section.entries();
            }
        }
        return List.of();
    }

    private static void migrationLines(Ui ui, DevReports.Report report, List<DevReports.Entry> entries, float x, float y, float w, float h) {
        float cursor = y;
        int shown = Math.max(0, Math.min(entries.size(), (int) (h / 21.6f) - 1));
        for (int i = 0; i < shown; i++) {
            DevReports.Entry entry = entries.get(i);
            int color = switch (entry.subject()) {
                case "rename" -> ui.c("chip.mod.fg");
                case "merge" -> ui.c("chip.fn.fg");
                case "remove" -> ui.c("state.bad");
                default -> ui.c("text.muted");
            };
            ui.label(entry.subject(), x, cursor, 11, Ui.REGULAR, color);
            ui.labelFit(entry.detail(), x + 46, cursor, w - 46, 11, Ui.REGULAR, ui.c("button.fg"));
            cursor += 21.6f;
        }
        ui.labelFit(Component.translatable("rysquests.dev.unchanged", Math.max(0, report.quests() - entries.size())).getString(), x, cursor, w, 11, Ui.REGULAR, ui.c("state.ok"));
    }

    private void renderReportsPage(Ui ui, DevReports.Report report, int x, int y, int w, int h) {
        float left = x + PAD_X;
        float inner = w - PAD_X * 2;
        int offset = scroll.offset();
        float cursor = y + PAD_Y - offset;
        for (DevReports.Section section : sections(report)) {
            boolean open = section.id().equals(openSection);
            float cy = cursor + 16;
            ui.box(left, cursor, inner, 32, 8, ui.c("status.bg"), ui.c("panel.border"));
            badge(ui, section, left + 10, cy);
            ui.labelFit(sectionTitle(section), left + 58, cy, inner - 88, 12, Ui.REGULAR, ui.c("text.body"));
            ui.chevron(open, left + inner - 18, cy, 9, ui.c("chip.mod.fg"));
            ui.hit(left, cursor, inner, 32, () -> openSection = open ? "" : section.id());
            cursor += 40;
            if (open) {
                for (DevReports.Entry entry : section.entries()) {
                    ui.labelFit(entry.subject() + "  " + entry.detail(), left + 10, cursor + 7, inner - 20, 11.5f, Ui.REGULAR, ui.c("text.muted"));
                    cursor += 20;
                }
                cursor += 8;
            }
        }
        scroll.content(cursor + offset - y + PAD_Y, h - 12);
    }

    private void renderMigrationsPage(Ui ui, DevReports.Report report, int x, int y, int w, int h) {
        renderMigrationsBox(ui, report, x + PAD_X, y + PAD_Y, w - PAD_X * 2, h - PAD_Y * 2 - 12);
    }

    private void renderImportPage(Ui ui, int x, int y, int w) {
        float left = x + PAD_X;
        float top = y + PAD_Y;
        ui.label(Component.translatable("rysquests.dev.import_title").getString(), left, top + 10.5f, 16, Ui.DISPLAY, ui.c("text.primary"));
        ui.lines(ui.wrap(Component.translatable("rysquests.dev.import_text").getString(), w - PAD_X * 2, 12, Ui.REGULAR, 3), left, top + 45, 17, 12, Ui.REGULAR, ui.c("text.muted"));
        float rowY = top + 72;
        float pen = left;
        String[][] actions = {
            {"rysquests.dev.import", "rysquests import ftbq"},
            {"rysquests.dev.import_progress", "rysquests import ftbq progress"},
            {"rysquests.dev.registry", "rysquests dev registry"}
        };
        for (String[] action : actions) {
            String label = Component.translatable(action[0]).getString();
            float bw = ui.buttonWidth(label, 12, Ui.REGULAR, 14) + 2;
            String command = action[1];
            ui.button(label, pen, rowY, bw, 32, 8, 12, Ui.REGULAR, 14, Ui.Style.PLAIN, () -> command(command));
            pen += bw + 8;
        }
    }

    /** Language files live on the client, so this report is made here. */
    private static DevReports.Section untranslated() {
        List<DevReports.Entry> entries = new ArrayList<>();
        for (Chapter chapter : ClientBook.current().chapters().values()) {
            check(chapter.id().toString(), chapter.title(), entries);
        }
        for (Quest quest : ClientBook.current().quests().values()) {
            check(quest.id().toString(), quest.title(), entries);
            quest.text().ifPresent(text -> check(quest.id().toString(), text, entries));
        }
        return new DevReports.Section("untranslated", entries);
    }

    private static void check(String subject, String key, List<DevReports.Entry> entries) {
        if (looksLikeKey(key) && !I18n.exists(key)) {
            entries.add(new DevReports.Entry(subject, key));
        }
    }

    private static boolean looksLikeKey(String text) {
        return text.indexOf(' ') < 0 && text.indexOf('.') > 0;
    }

    @Override
    public void scroll(double mouseX, double mouseY, double amount) {
        scroll.wheel(amount, WHEEL);
    }
}
