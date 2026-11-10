package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.client.integration.Ponders;
import works.repka.rysquests.client.ClientAssets;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.markup.Markup;
import works.repka.rysquests.data.CardsSection;
import works.repka.rysquests.data.Guide;
import works.repka.rysquests.data.GuideRef;
import works.repka.rysquests.data.MatrixSection;
import works.repka.rysquests.data.PagesSection;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.SectionDef;
import works.repka.rysquests.dev.DevReports;

/** Checks the part of the guides only a client can see: translations, pictures and the links inside the text. */
final class GuideCheck {
    private static final String SECTION = "guides";

    @Nullable
    private static QuestBook checkedFor;
    private static List<DevReports.Entry> found = List.of();

    private GuideCheck() {
    }

    static DevReports.Section section() {
        QuestBook book = ClientBook.current();
        if (book != checkedFor) {
            checkedFor = book;
            found = check(book);
        }
        return new DevReports.Section(SECTION, found);
    }

    private static List<DevReports.Entry> check(QuestBook book) {
        List<DevReports.Entry> entries = new ArrayList<>();
        for (Guide guide : book.guides().values()) {
            String subject = guide.id().toString();
            key(subject, guide.title(), entries);
            guide.category().ifPresent(category -> key(subject, category, entries));
            guide.stageTitle().ifPresent(stage -> key(subject, stage, entries));
            for (Guide.Page page : guide.pages()) {
                String where = subject + "#" + page.id();
                key(where, page.title(), entries);
                key(where, page.text(), entries);
                Markup.parse(Component.translatable(page.text()).getString()).forEach(paragraph -> {
                    for (Markup.Span span : paragraph.spans()) {
                        link(book, where, span, entries);
                    }
                });
            }
        }
        for (SectionDef section : book.sections().values()) {
            String subject = section.id().toString();
            key(subject, section.title(), entries);
            section.subtitle().ifPresent(text -> key(subject, text, entries));
            section.shortTitle().ifPresent(text -> key(subject, text, entries));
            switch (section) {
                case MatrixSection matrix -> checkMatrix(subject, matrix, entries);
                case CardsSection cards -> cards.cards().forEach(card -> {
                    key(subject + "/" + card.id(), cards.cardKey(card, "name"), entries);
                    card.stageTitle().ifPresent(text -> key(subject + "/" + card.id(), text, entries));
                });
                case PagesSection pages -> pages.pages().forEach(page -> {
                    String where = subject + "#" + page.id();
                    key(where, page.title(), entries);
                    key(where, page.text(), entries);
                    Markup.parse(Component.translatable(page.text()).getString()).forEach(paragraph ->
                            paragraph.spans().forEach(span -> link(book, where, span, entries)));
                });
            }
        }
        return entries;
    }

    private static void checkMatrix(String subject, MatrixSection matrix, List<DevReports.Entry> entries) {
        matrix.columns().forEach(column -> {
            key(subject, column.title(), entries);
            column.hint().ifPresent(hint -> key(subject, hint, entries));
        });
        matrix.groups().forEach(group -> key(subject, matrix.groupTitle(group), entries));
        for (MatrixSection.Row row : matrix.rows()) {
            key(subject + "/" + row.id(), matrix.rowTitle(row), entries);
            for (MatrixSection.Column column : matrix.columns()) {
                for (MatrixSection.Way way : row.ways(column)) {
                    key(subject + "/" + row.id() + "/" + column.id(), matrix.wayName(row, column, way), entries);
                }
            }
        }
    }

    private static void key(String subject, String key, List<DevReports.Entry> entries) {
        if (key.indexOf(' ') < 0 && key.indexOf('.') > 0 && !Language.getInstance().has(key)) {
            entries.add(new DevReports.Entry(subject, key));
        }
    }

    private static void link(QuestBook book, String subject, Markup.Span span, List<DevReports.Entry> entries) {
        if (span.isLink() && !valid(book, span)) {
            entries.add(new DevReports.Entry(subject, "[[" + span.kind() + ":" + span.target() + "]]"));
        }
    }

    static boolean valid(QuestBook book, Markup.Span span) {
        if (span.kind().equals("guide")) {
            return GuideRef.parse(span.target()).map(ref -> {
                Guide guide = book.guides().get(ref.guide());
                if (guide == null && book.sections().get(ref.guide()) instanceof PagesSection pages) {
                    guide = pages.asGuide();
                }
                Guide found = guide;
                return found != null && ref.page().map(page -> found.pageIndex(page) >= 0).orElse(true);
            }).orElse(false);
        }
        ResourceLocation id = ResourceLocation.tryParse(span.kind().equals("recipe") ? Markup.recipeItem(span.target()) : span.target());
        if (id == null) {
            return false;
        }
        return switch (span.kind()) {
            case "item", "recipe" -> BuiltInRegistries.ITEM.containsKey(id);
            case "ponder" -> !Ponders.installed() || Ponders.exists(id);
            case "quest" -> book.quests().containsKey(id);
            case "image" -> ClientAssets.imageSize(id) != null || Minecraft.getInstance().getResourceManager().getResource(id).isPresent();
            default -> true;
        };
    }
}
