package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.markup.Markup;
import works.repka.rysquests.data.Guide;
import works.repka.rysquests.data.GuideRef;
import works.repka.rysquests.data.QuestBook;
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
        return entries;
    }

    private static void key(String subject, String key, List<DevReports.Entry> entries) {
        if (key.indexOf(' ') < 0 && key.indexOf('.') > 0 && !I18n.exists(key)) {
            entries.add(new DevReports.Entry(subject, key));
        }
    }

    private static void link(QuestBook book, String subject, Markup.Span span, List<DevReports.Entry> entries) {
        if (span.isLink() && !valid(book, span)) {
            entries.add(new DevReports.Entry(subject, "[[" + span.kind() + ":" + span.target() + "]]"));
        }
    }

    private static boolean valid(QuestBook book, Markup.Span span) {
        if (span.kind().equals("guide")) {
            return GuideRef.parse(span.target()).map(ref -> {
                Guide guide = book.guides().get(ref.guide());
                return guide != null && ref.page().map(page -> guide.pageIndex(page) >= 0).orElse(true);
            }).orElse(false);
        }
        ResourceLocation id = ResourceLocation.tryParse(span.target());
        if (id == null) {
            return false;
        }
        return switch (span.kind()) {
            case "item", "recipe" -> BuiltInRegistries.ITEM.containsKey(id);
            case "quest" -> book.quests().containsKey(id);
            case "image" -> Minecraft.getInstance().getResourceManager().getResource(id).isPresent();
            default -> true;
        };
    }
}
