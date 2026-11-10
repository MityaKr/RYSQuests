package works.repka.rysquests.data;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.data.BookReport.Issue;
import works.repka.rysquests.data.BookReport.Severity;

/** Checks custom sections. A section that makes no sense is left out; a broken link inside one is only reported. */
final class SectionValidator {
    private static final Pattern COLOR = Pattern.compile("#[0-9a-fA-F]{6}");

    private final Map<ResourceLocation, Quest> quests;
    private final Map<ResourceLocation, Guide> guides;
    private final Map<ResourceLocation, SectionDef> all = new LinkedHashMap<>();
    private final Predicate<ResourceLocation> itemExists;
    private final List<Issue> issues;

    private SectionValidator(Map<ResourceLocation, Quest> quests, Map<ResourceLocation, Guide> guides, Predicate<ResourceLocation> itemExists, List<Issue> issues) {
        this.quests = quests;
        this.guides = guides;
        this.itemExists = itemExists;
        this.issues = issues;
    }

    static Map<ResourceLocation, SectionDef> check(List<SectionDef> sections, Map<ResourceLocation, Quest> quests, Map<ResourceLocation, Guide> guides,
            Predicate<ResourceLocation> itemExists, List<Issue> issues) {
        SectionValidator validator = new SectionValidator(quests, guides, itemExists, issues);
        sections.forEach(section -> validator.all.put(section.id(), section));
        Map<ResourceLocation, SectionDef> valid = new LinkedHashMap<>();
        for (SectionDef section : sections) {
            if (validator.isValid(section)) {
                valid.put(section.id(), section);
            }
        }
        return valid;
    }

    private void warn(String code, SectionDef section, String detail) {
        issues.add(new Issue(Severity.WARNING, code, section.id().toString(), detail));
    }

    private boolean fail(String code, SectionDef section, String detail) {
        issues.add(new Issue(Severity.ERROR, code, section.id().toString(), detail));
        return false;
    }

    private void item(SectionDef section, ResourceLocation id) {
        if (!itemExists.test(id)) {
            warn("unknown_item", section, id.toString());
        }
    }

    private boolean isValid(SectionDef section) {
        section.icon().ifPresent(icon -> item(section, icon));
        return switch (section) {
            case MatrixSection matrix -> isValid(matrix);
            case CardsSection cards -> isValid(cards);
            case PagesSection pages -> isValid(pages);
        };
    }

    private boolean isValid(PagesSection section) {
        if (section.pages().isEmpty()) {
            return fail("empty_section", section, "");
        }
        Set<String> ids = new HashSet<>();
        for (Guide.Page page : section.pages()) {
            if (!ids.add(page.id())) {
                return fail("duplicate_section_id", section, page.id());
            }
        }
        return true;
    }

    private boolean isValid(MatrixSection section) {
        if (section.columns().isEmpty() || section.rows().isEmpty()) {
            return fail("empty_section", section, "");
        }
        Set<String> columns = new HashSet<>();
        for (MatrixSection.Column column : section.columns()) {
            if (!columns.add(column.id())) {
                return fail("duplicate_section_id", section, column.id());
            }
        }
        Set<String> rows = new HashSet<>();
        for (MatrixSection.Row row : section.rows()) {
            if (!rows.add(row.id())) {
                return fail("duplicate_section_id", section, row.id());
            }
            row.icon().ifPresent(icon -> item(section, icon));
            row.group().filter(group -> !section.groups().contains(group)).ifPresent(group -> warn("unknown_section_group", section, group));
            for (Map.Entry<String, List<MatrixSection.Way>> cell : row.cells().entrySet()) {
                if (!columns.contains(cell.getKey())) {
                    warn("unknown_section_column", section, row.id() + "." + cell.getKey());
                }
                Set<String> ways = new HashSet<>();
                for (MatrixSection.Way way : cell.getValue()) {
                    if (!ways.add(way.id())) {
                        return fail("duplicate_section_id", section, row.id() + "." + cell.getKey() + "." + way.id());
                    }
                    way.icon().ifPresent(icon -> item(section, icon));
                    way.needs().forEach(need -> item(section, need));
                    way.links().forEach(link -> link(section, link));
                }
            }
        }
        return true;
    }

    private void link(SectionDef section, MatrixSection.Link link) {
        boolean known = switch (link.type()) {
            case QUEST -> ResourceLocation.tryParse(link.id()) != null && quests.containsKey(ResourceLocation.parse(link.id()));
            case RECIPE -> ResourceLocation.tryParse(link.id()) != null && itemExists.test(ResourceLocation.parse(link.id()));
            case GUIDE -> GuideRef.parse(link.id()).filter(this::known).isPresent();
        };
        if (!known) {
            warn("unknown_section_link", section, link.type().getSerializedName() + " " + link.id());
        }
    }

    /** A link can lead to a guide or to a section on the pages template. */
    private boolean known(GuideRef ref) {
        Guide guide = guides.get(ref.guide());
        if (guide == null && all.get(ref.guide()) instanceof PagesSection pages) {
            guide = pages.asGuide();
        }
        Guide found = guide;
        return found != null && ref.page().map(page -> found.pageIndex(page) >= 0).orElse(true);
    }

    private boolean isValid(CardsSection section) {
        if (section.cards().isEmpty()) {
            return fail("empty_section", section, "");
        }
        Set<String> ids = new HashSet<>();
        for (CardsSection.Card card : section.cards()) {
            if (!ids.add(card.id())) {
                return fail("duplicate_section_id", section, card.id());
            }
            card.icon().ifPresent(icon -> item(section, icon));
            card.needs().forEach(need -> item(section, need.item()));
            card.gives().forEach(item -> item(section, item));
            card.group().filter(group -> !section.groups().contains(group)).ifPresent(group -> warn("unknown_section_group", section, group));
            card.art().filter(art -> !COLOR.matcher(art).matches()).ifPresent(art -> warn("bad_section_color", section, card.id() + " " + art));
            card.page().filter(ref -> !known(ref)).ifPresent(ref -> warn("unknown_section_link", section, "page " + ref));
            card.quest().filter(quest -> !quests.containsKey(quest)).ifPresent(quest -> warn("unknown_section_link", section, "quest " + quest));
        }
        return true;
    }
}
