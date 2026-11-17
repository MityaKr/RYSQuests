package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import works.repka.ryscore.ui.Icons;
import works.repka.rysquests.data.BookMeta;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.editor.BookDraft;
import works.repka.rysquests.editor.QuestEdits;

/**
 * The form of a chapter (picture 35): its place in the book, name and icon, subtitle, epoch, "Показывать на карте" (off, it goes to
 * "Вне карты"), "Скрыта, пока не открыта", and what is in it. "Закрепить место" is gone: a chapter's place is its order.
 */
final class ChapterForm {
    private final EditorPage page;
    private final Runnable close;
    private final Scroll scroll = new Scroll();

    ChapterForm(EditorPage page, Runnable close) {
        this.page = page;
        this.close = close;
    }

    void scroll(double amount) {
        scroll.wheel(amount, 48);
    }

    void render(Ui ui, float x, float y, float w, float h, ResourceLocation id) {
        EditorBook editor = EditorBook.get();
        QuestBook book = editor.view();
        Chapter chapter = book.chapters().get(id);
        Chapter data = editor.data().chapters().get(id);
        if (chapter == null || data == null) {
            close.run();
            return;
        }
        ui.rect(x, y, w, h, ui.c("panel.bg"));
        ui.rect(x, y, 1, h, ui.c("panel.border"));
        float footY = y + h - EdUi.FOOT_H;
        float bx = x + 1 + 18;
        float bw = w - 1 - 36;
        ui.clip(Math.round(x + 1), Math.round(y), Math.round(w - 1), Math.round(footY - y));
        int offset = scroll.offset();
        float top = y + 16 - offset;
        float pen = top;
        int number = 0;
        for (Chapter each : EditorSidebar.ordered(book)) {
            if (each.isStory() && !each.offMap()) {
                number++;
            }
            if (each.id().equals(id)) {
                break;
            }
        }
        String rubric = chapter.offMap() ? EdText.tr("rysquests.ed.chapter.rubric_off") : EdText.tr("rysquests.ed.chapter.rubric", String.format("%02d", number));
        DetailPanel.rubric(ui, bx, pen, bw - 30, rubric, null);
        EdUi.close(ui, bx + bw - 22, pen - 7, close);
        pen += Ui.lineHeight(11.5f) + 10;
        ItemStack icon = chapter.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY);
        EdUi.plate(ui, icon, Icons.Shape.BOOK, bx, pen, 46);
        EdUi.input(ui, page.fields, "chapter-title:" + id, bx + 56, pen + 6, bw - 56, EdUi.INPUT_H, 13, Ui.SEMIBOLD, chapter.title(), "",
                value -> editor.typeChapterText(id, "title", value));
        pen += 46 + 10;
        pen += EdUi.button(ui, EdText.tr("rysquests.ed.chapter.icon"), Icons.Shape.HAND, bx, pen, true, false, false, () -> iconFromHand(id, chapter)) * 0 + EdUi.SM_H + 10;
        pen += EdUi.field(ui, page.fields, "chapter-sub:" + id, EdText.tr("rysquests.ed.chapter.subtitle"), bx, pen, bw, 54, chapter.subtitle().orElse(""), "",
                value -> editor.typeChapterText(id, "subtitle", value)) + 10;
        pen += EdUi.label(ui, EdText.tr("rysquests.ed.chapter.epoch"), bx, pen);
        BookMeta meta = book.meta();
        String epochName = chapter.epoch().filter(n -> n >= 1).flatMap(meta::epoch).map(e -> Ui.roman(chapter.epoch().get()) + " " + editor.text(e.title()))
                .orElse(EdText.tr("rysquests.ed.chapter.no_epoch"));
        float sy = pen;
        pen += EdUi.select(ui, bx, pen, bw, epochName, page.choices.isOpen("epoch"), () -> {
            List<String> labels = new ArrayList<>();
            labels.add(EdText.tr("rysquests.ed.chapter.no_epoch"));
            for (int i = 0; i < meta.epochs().size(); i++) {
                labels.add(Ui.roman(i + 1) + " " + editor.text(meta.epochs().get(i).title()));
            }
            page.choices.toggle("epoch", bx, sy + EdUi.INPUT_H + 4, bw, labels, chapter.epoch().orElse(0), index -> editor.editChapter(id,
                    EdText.tr("rysquests.ed.log.chapter_epoch", editor.text(chapter.title()), labels.get(index)), work -> work.withChapter(work.chapter()
                            .withEpoch(index == 0 ? Optional.empty() : Optional.of(index)))));
        }) + 12;
        pen += EdUi.setting(ui, bx, pen, bw, EdText.tr("rysquests.ed.chapter.on_map"), EdText.tr("rysquests.ed.chapter.on_map_sub"), !chapter.offMap(),
                () -> editor.editChapter(id, EdText.tr("rysquests.ed.log.chapter_map", editor.text(chapter.title())), work -> work.withChapter(work.chapter().withOffMap(!work.chapter().offMap())))) + 8;
        pen += EdUi.setting(ui, bx, pen, bw, EdText.tr("rysquests.ed.chapter.hidden"), EdText.tr("rysquests.ed.chapter.hidden_sub"), chapter.hidden(),
                () -> editor.editChapter(id, EdText.tr("rysquests.ed.log.chapter_hidden", editor.text(chapter.title())), work -> work.withChapter(hidden(work.chapter(), !work.chapter().hidden())))) + 12;
        pen += DetailPanel.rubric(ui, bx, pen, bw, EdText.tr("rysquests.ed.chapter.inside"), null) + 6;
        pen += summary(ui, book, id, bx, pen, bw);
        ui.unclip();
        scroll.content(pen + offset - (y + 16) + 12, footY - y - 16);
        float by = EdUi.foot(ui, x + 1, footY, w - 1);
        EdUi.ghost(ui, EdText.tr("rysquests.ed.chapter.archive"), Icons.Shape.ARCHIVE, bx - 14, by, () -> archive(id, chapter));
    }

    private static Chapter hidden(Chapter c, boolean value) {
        return new Chapter(c.id(), c.title(), c.subtitle(), c.icon(), c.epoch(), c.defaultView(), c.groups(), c.requires(), value, c.section(), c.links(), c.offMap());
    }

    /** "В главе": quests, main and side, milestones, links to other chapters. */
    private static float summary(Ui ui, QuestBook book, ResourceLocation id, float x, float y, float w) {
        List<Quest> quests = book.quests().values().stream().filter(q -> q.chapter().filter(id::equals).isPresent() && !q.options().archived()).toList();
        long side = quests.stream().filter(q -> q.options().optional()).count();
        long milestones = quests.stream().filter(q -> q.options().milestone()).count();
        long cross = 0;
        for (Quest quest : book.quests().values()) {
            boolean inside = quest.chapter().filter(id::equals).isPresent();
            for (ResourceLocation dependency : quest.requires()) {
                Quest other = book.quests().get(dependency);
                if (other != null && inside != other.chapter().filter(id::equals).isPresent()) {
                    cross++;
                }
            }
        }
        String[][] rows = {
                {EdText.tr("rysquests.ed.chapter.sum.quests"), String.valueOf(quests.size())},
                {EdText.tr("rysquests.ed.chapter.sum.main_side"), EdText.tr("rysquests.ed.chapter.sum.and", quests.size() - side, side)},
                {EdText.tr("rysquests.ed.chapter.sum.milestones"), String.valueOf(milestones)},
                {EdText.tr("rysquests.ed.chapter.sum.links"), String.valueOf(cross)}};
        float h = 10 * 2 + rows.length * 18 + (rows.length - 1) * 5;
        ui.box(x, y, w, h, 10, ui.c("status.bg"), ui.c("panel.border"));
        float pen = y + 10;
        for (String[] row : rows) {
            ui.label(row[0], x + 12, pen + 9, 12.5f, Ui.REGULAR, ui.c("text.muted"));
            ui.labelRight(row[1], x + w - 12, pen + 9, 12.5f, Ui.MEDIUM, ui.c("text.primary"));
            pen += 18 + 5;
        }
        return h;
    }

    private static void iconFromHand(ResourceLocation id, Chapter chapter) {
        var player = Minecraft.getInstance().player;
        if (player == null || player.getMainHandItem().isEmpty()) {
            works.repka.rysquests.client.ClientMessages.show("rysquests.ed.hand_empty", List.of(), false);
            return;
        }
        ResourceLocation item = BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem());
        EditorBook editor = EditorBook.get();
        editor.editChapter(id, EdText.tr("rysquests.ed.log.chapter_icon", editor.text(chapter.title())), work -> {
            Chapter c = work.chapter();
            return work.withChapter(new Chapter(c.id(), c.title(), c.subtitle(), Optional.of(item), c.epoch(), c.defaultView(), c.groups(), c.requires(), c.hidden(), c.section(),
                    c.links(), c.offMap()));
        });
    }

    /** "Главу в архив": its quests go to the archive and the chapter hides; nothing is deleted and progress stays. */
    private void archive(ResourceLocation id, Chapter chapter) {
        EditorBook editor = EditorBook.get();
        editor.editChapter(id, EdText.tr("rysquests.ed.log.chapter_archive", editor.text(chapter.title())), work -> {
            BookDraft.Work out = work.withChapter(hidden(work.chapter(), true));
            for (Quest quest : work.quests()) {
                out = out.withQuest(QuestEdits.withArchived(quest, true));
            }
            return out;
        });
        close.run();
    }
}
