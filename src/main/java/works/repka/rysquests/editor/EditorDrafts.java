package works.repka.rysquests.editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.Quest;

/**
 * Unsaved work of the quest editor, kept on disk between sessions so that closing the game loses nothing (plan, section 6). One
 * file per chapter in a folder the caller gives: the client keeps one folder per server or world. Knows nothing about the game,
 * so that it can be tested.
 */
public final class EditorDrafts {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String SUFFIX = ".json";

    private EditorDrafts() {
    }

    /**
     * One chapter as the editor holds it.
     *
     * @param quests every quest of the chapter as edited
     * @param texts strings typed in the author's language, by lang key
     * @param baseVersion the version of the chapter the editing began from, sent with the save so that the server can tell
     *                    whether another operator changed it meanwhile
     * @param newChapter the chapter is not in the book yet
     * @param savedAt when the draft was written, from {@link System#currentTimeMillis()}
     */
    public record ChapterDraft(Chapter chapter, List<Quest> quests, Map<String, String> texts, String language, String baseVersion, boolean newChapter, long savedAt) {
        public static final Codec<ChapterDraft> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Chapter.CODEC.fieldOf("chapter").forGetter(ChapterDraft::chapter),
                Quest.CODEC.listOf().fieldOf("quests").forGetter(ChapterDraft::quests),
                Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("texts", Map.of()).forGetter(ChapterDraft::texts),
                Codec.STRING.fieldOf("language").forGetter(ChapterDraft::language),
                Codec.STRING.optionalFieldOf("base_version", "").forGetter(ChapterDraft::baseVersion),
                Codec.BOOL.optionalFieldOf("new_chapter", false).forGetter(ChapterDraft::newChapter),
                Codec.LONG.fieldOf("saved_at").forGetter(ChapterDraft::savedAt)
        ).apply(instance, ChapterDraft::new));
    }

    /** Through a temporary file, so that a crash never leaves half a draft. */
    public static void save(Path folder, ChapterDraft draft, DynamicOps<JsonElement> ops) throws IOException {
        Path file = file(folder, draft.chapter().id());
        Files.createDirectories(folder);
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, GSON.toJson(ChapterDraft.CODEC.encodeStart(ops, draft).getOrThrow()) + "\n", StandardCharsets.UTF_8);
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
    }

    /** The draft of a chapter; empty when there is none or it cannot be read any more, as after a mod that it named was removed. */
    public static Optional<ChapterDraft> load(Path folder, ResourceLocation chapter, DynamicOps<JsonElement> ops) {
        Path file = file(folder, chapter);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            return ChapterDraft.CODEC.parse(ops, json).result();
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /** The chapters that have a draft. */
    public static List<ResourceLocation> chapters(Path folder) throws IOException {
        List<ResourceLocation> found = new ArrayList<>();
        if (!Files.isDirectory(folder)) {
            return found;
        }
        try (Stream<Path> files = Files.list(folder)) {
            files.map(path -> path.getFileName().toString()).filter(name -> name.endsWith(SUFFIX)).sorted()
                    .forEach(name -> Optional.ofNullable(chapterOf(name.substring(0, name.length() - SUFFIX.length()))).ifPresent(found::add));
        }
        return found;
    }

    /** The work was saved to the book or thrown away: the draft is no longer needed. */
    public static void discard(Path folder, ResourceLocation chapter) throws IOException {
        Files.deleteIfExists(file(folder, chapter));
    }

    /** {@code ns:a/b} is kept as {@code ns~a~b.json}: neither character is allowed in an id, so the name reads back. */
    static Path file(Path folder, ResourceLocation chapter) {
        return folder.resolve(chapter.getNamespace() + "~" + chapter.getPath().replace('/', '~') + SUFFIX);
    }

    private static ResourceLocation chapterOf(String name) {
        int split = name.indexOf('~');
        return split <= 0 ? null : ResourceLocation.tryBuild(name.substring(0, split), name.substring(split + 1).replace('~', '/'));
    }
}
