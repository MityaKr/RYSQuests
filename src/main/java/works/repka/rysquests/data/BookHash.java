package works.repka.rysquests.data;

import com.google.common.hash.Hashing;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;

/** SHA-256 of the canonical JSON of the linked book: sorted entries, sorted object keys. */
public final class BookHash {
    private BookHash() {
    }

    /**
     * The version of what the map is laid out from, so that a baked {@code layout.json} can be checked against the book: the
     * map form, the epochs, the story chapters in book order with their epoch, groups and links, and every live quest of them
     * with its chapter, group, dependencies, size and pinned place. Texts, tasks and rewards do not move anything and are left
     * out, so fixing a typo does not make the layout stale. Drafts and archived quests are left out too: players are never sent
     * them, and their client must arrive at the same value from what it has.
     */
    public static String layout(BookMeta meta, List<Chapter> chapters, List<Quest> quests) {
        StringBuilder out = new StringBuilder("form ").append(meta.mapForm().getSerializedName()).append('\n');
        for (BookMeta.Epoch epoch : meta.epochs()) {
            out.append("epoch ").append(epoch.imported()).append('\n');
        }
        Map<ResourceLocation, Chapter> story = new HashMap<>();
        for (Chapter chapter : chapters) {
            if (!chapter.isStory()) {
                continue;
            }
            story.put(chapter.id(), chapter);
            out.append("chapter ").append(chapter.id()).append(' ').append(chapter.epoch().orElse(0)).append(' ').append(chapter.offMap()).append(' ').append(chapter.hidden())
                    .append(" groups ").append(chapter.groups().stream().map(Chapter.Group::id).toList())
                    .append(" links ").append(chapter.links()).append('\n');
        }
        quests.stream()
                .filter(quest -> quest.options().live() && quest.chapter().map(story::containsKey).orElse(false))
                .sorted(Comparator.comparing(Quest::id))
                .forEach(quest -> out.append("quest ").append(quest.id()).append(' ').append(quest.chapter().get()).append(' ').append(quest.group().orElse(""))
                        .append(" requires ").append(quest.requires()).append(' ').append(quest.requiresMode())
                        .append(' ').append(quest.options().milestone()).append(' ').append(quest.options().optional()).append(' ').append(quest.options().hidden())
                        .append(" at ").append(quest.options().layout().fixedX().orElse(Integer.MIN_VALUE)).append(',').append(quest.options().layout().fixedY().orElse(Integer.MIN_VALUE))
                        .append('\n'));
        return Hashing.sha256().hashString(out.toString(), StandardCharsets.UTF_8).toString();
    }

    /**
     * The version of one chapter as the editor sees it: the chapter and all its quests, canonical JSON. The editor sends the
     * version it began from; the server compares it with the chapter as it is now and refuses to save over another operator's
     * changes. A chapter that does not exist yet has the version {@code ""}.
     */
    public static String chapterVersion(QuestBook book, ResourceLocation chapter, DynamicOps<JsonElement> ops) {
        Chapter found = book.chapters().get(chapter);
        if (found == null) {
            return "";
        }
        return chapterVersion(found, book.quests().values().stream().filter(quest -> quest.chapter().filter(chapter::equals).isPresent()).toList(), ops);
    }

    /** @param quests the quests of the chapter, in any order */
    public static String chapterVersion(Chapter chapter, java.util.Collection<Quest> quests, DynamicOps<JsonElement> ops) {
        JsonArray root = new JsonArray();
        root.add(canonical(Chapter.CODEC, chapter, ops));
        quests.stream().sorted(Comparator.comparing(Quest::id)).forEach(quest -> root.add(canonical(Quest.CODEC, quest, ops)));
        return Hashing.sha256().hashString(root.toString(), StandardCharsets.UTF_8).toString();
    }

    static String compute(List<Chapter> chapters, List<Quest> quests, List<Guide> guides, List<SectionDef> sections, List<ShopOffer> offers, List<Achievement> achievements, List<StructureDef> structures, DynamicOps<JsonElement> ops) {
        JsonArray root = new JsonArray();
        chapters.stream().sorted(Comparator.comparing(Chapter::id)).forEach(chapter -> root.add(canonical(Chapter.CODEC, chapter, ops)));
        quests.stream().sorted(Comparator.comparing(Quest::id)).forEach(quest -> root.add(canonical(Quest.CODEC, quest, ops)));
        guides.stream().sorted(Comparator.comparing(Guide::id)).forEach(guide -> root.add(canonical(Guide.CODEC, guide, ops)));
        sections.stream().sorted(Comparator.comparing(SectionDef::id)).forEach(section -> root.add(canonical(SectionDef.CODEC, section, ops)));
        offers.stream().sorted(Comparator.comparing(ShopOffer::id)).forEach(offer -> root.add(canonical(ShopOffer.CODEC, offer, ops)));
        achievements.stream().sorted(Comparator.comparing(Achievement::id)).forEach(achievement -> root.add(canonical(Achievement.CODEC, achievement, ops)));
        structures.stream().sorted(Comparator.comparing(StructureDef::id)).forEach(structure -> root.add(canonical(StructureDef.CODEC, structure, ops)));
        return Hashing.sha256().hashString(root.toString(), StandardCharsets.UTF_8).toString();
    }

    private static <A> JsonElement canonical(Codec<A> codec, A value, DynamicOps<JsonElement> ops) {
        return sortKeys(codec.encodeStart(ops, value).getOrThrow());
    }

    private static JsonElement sortKeys(JsonElement element) {
        if (element.isJsonObject()) {
            JsonObject sorted = new JsonObject();
            new TreeMap<>(element.getAsJsonObject().asMap()).forEach((key, value) -> sorted.add(key, sortKeys(value)));
            return sorted;
        }
        if (element.isJsonArray()) {
            JsonArray array = new JsonArray();
            element.getAsJsonArray().forEach(item -> array.add(sortKeys(item)));
            return array;
        }
        return element;
    }
}
