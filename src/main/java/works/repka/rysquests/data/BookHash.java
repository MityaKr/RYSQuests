package works.repka.rysquests.data;

import com.google.common.hash.Hashing;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

/** SHA-256 of the canonical JSON of the linked book: sorted entries, sorted object keys. */
final class BookHash {
    private BookHash() {
    }

    static String compute(List<Chapter> chapters, List<Quest> quests, List<Guide> guides, List<SectionDef> sections, DynamicOps<JsonElement> ops) {
        JsonArray root = new JsonArray();
        chapters.stream().sorted(Comparator.comparing(Chapter::id)).forEach(chapter -> root.add(canonical(Chapter.CODEC, chapter, ops)));
        quests.stream().sorted(Comparator.comparing(Quest::id)).forEach(quest -> root.add(canonical(Quest.CODEC, quest, ops)));
        guides.stream().sorted(Comparator.comparing(Guide::id)).forEach(guide -> root.add(canonical(Guide.CODEC, guide, ops)));
        sections.stream().sorted(Comparator.comparing(SectionDef::id)).forEach(section -> root.add(canonical(SectionDef.CODEC, section, ops)));
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
