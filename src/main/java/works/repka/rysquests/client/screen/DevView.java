package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientDev;
import works.repka.rysquests.client.input.QuestKeys;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.dev.DevReports;
import works.repka.rysquests.network.QuestActionPayload;
import works.repka.ryscore.ui.Draw;

/** Book reports and maintenance buttons for operators. Everything here also exists as a command. */
final class DevView implements BookView {
    private static final int LINE = 12;
    private static final int WHEEL = 24;
    private static final int BUTTON = 16;

    private int scroll;
    private boolean requested;
    private String openSection = "";

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        if (!requested) {
            requested = true;
            QuestKeys.sendAll(QuestActionPayload.DEV_REPORT);
        }
        int cursor = x;
        cursor += ui.button(Component.translatable("rysquests.dev.recheck"), cursor, y, BUTTON, true, () -> QuestKeys.sendAll(QuestActionPayload.DEV_REPORT)) + 3;
        cursor += command(ui, "rysquests.dev.dry_run", "rysquests migrate --dry-run", cursor, y) + 3;
        cursor += command(ui, "rysquests.dev.migrate", "rysquests migrate --apply", cursor, y) + 3;
        cursor += command(ui, "rysquests.dev.import", "rysquests import ftbq", cursor, y) + 3;
        command(ui, "rysquests.dev.registry", "rysquests dev registry", cursor, y);

        int listY = y + BUTTON + 4;
        int listH = h - BUTTON - 4;
        ui.clip(x, listY, w, listH);
        int line = listY - scroll;
        DevReports.Report report = ClientDev.report();
        if (report == null) {
            ui.text(Component.translatable("rysquests.dev.waiting"), x, line, "text.muted");
        } else {
            ui.small(Component.translatable("rysquests.dev.summary", report.chapters(), report.quests()).getString(), x, line, "text.muted");
            line += LINE;
            for (DevReports.Section section : report.sections()) {
                line = renderSection(ui, section, x, line, w);
            }
            line = renderSection(ui, untranslated(), x, line, w);
        }
        ui.unclip();
        scroll = Mth.clamp(scroll, 0, Math.max(0, line + scroll - listY - listH));
    }

    private static int command(Ui ui, String label, String command, int x, int y) {
        return ui.button(Component.translatable(label), x, y, BUTTON, false, () -> Minecraft.getInstance().player.connection.sendCommand(command));
    }

    private int renderSection(Ui ui, DevReports.Section section, int x, int y, int w) {
        boolean open = section.id().equals(openSection);
        int rowH = 16;
        ui.theme.card(ui.g, x, y, w, rowH);
        String count = String.valueOf(section.entries().size());
        ui.theme.chip(ui.g, x + 3, y + 3, ui.font.width(count) + 8, 10, section.entries().isEmpty() ? "tag" : "mod");
        ui.text(count, x + 7, y + 4, section.entries().isEmpty() ? "chip.tag.fg" : "chip.mod.fg");
        ui.text(Component.translatable("rysquests.dev.section." + section.id()), x + ui.font.width(count) + 16, y + 4, "text.body");
        ui.hit(x, y, w, rowH, () -> openSection = open ? "" : section.id());
        y += rowH + 2;
        if (open) {
            for (DevReports.Entry entry : section.entries()) {
                ui.small(Draw.ellipsize(entry.subject() + "  " + entry.detail(), (int) (w / 0.75f)), x + 4, y, "text.muted");
                y += Ui.LINE;
            }
        }
        return y;
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
        scroll -= (int) (amount * WHEEL);
    }
}
