package works.repka.rysquests.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The translations and pictures of the editor's folder that the book refers to. A server has no way to hand resources
 * to its players, so they travel with the book: what an author saves is on every screen at once (ADR-35).
 */
public record BookAssets(Map<String, Map<String, String>> texts, Map<ResourceLocation, ByteBuffer> images) {
    public static final BookAssets EMPTY = new BookAssets(Map.of(), Map.of());
    public static final int MAX_IMAGE_BYTES = 4 * 1024 * 1024;

    public static final Codec<BookAssets> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Codec.STRING, Codec.STRING)).fieldOf("texts").forGetter(BookAssets::texts),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.BYTE_BUFFER).fieldOf("images").forGetter(BookAssets::images)
    ).apply(instance, BookAssets::new));

    private static final Logger LOGGER = LoggerFactory.getLogger(BookAssets.class);
    private static final int MAX_TOTAL_BYTES = 32 * 1024 * 1024;
    private static final Pattern IMAGE = Pattern.compile("\\[\\[image:([^\\]|]+)");

    /** Reads what the book needs from a folder laid out like a pack. A broken file only costs its own contents. */
    public static BookAssets read(Path root, QuestBook book) {
        Set<String> keys = textKeys(book);
        Set<String> namespaces = new HashSet<>();
        book.guides().keySet().forEach(id -> namespaces.add(id.getNamespace()));
        book.sections().keySet().forEach(id -> namespaces.add(id.getNamespace()));
        Map<String, Map<String, String>> texts = new HashMap<>();
        for (String namespace : namespaces) {
            Path lang = root.resolve("assets").resolve(namespace).resolve("lang");
            if (Files.isDirectory(lang)) {
                try (Stream<Path> files = Files.list(lang)) {
                    files.filter(file -> file.getFileName().toString().endsWith(".json")).forEach(file -> readLang(file, keys, texts));
                } catch (IOException e) {
                    LOGGER.warn("Could not list {}", lang, e);
                }
            }
        }
        return new BookAssets(texts, readImages(root, texts));
    }

    /** The keys of what guides and sections of pages show. A pack's lang files can hold a whole modpack's strings. */
    private static Set<String> textKeys(QuestBook book) {
        Set<String> keys = new HashSet<>();
        for (Guide guide : book.guides().values()) {
            keys.add(guide.title());
            guide.category().ifPresent(keys::add);
            guide.stageTitle().ifPresent(keys::add);
            addPages(guide.pages(), keys);
        }
        for (SectionDef section : book.sections().values()) {
            if (section instanceof PagesSection pages) {
                keys.add(pages.title());
                pages.shortTitle().ifPresent(keys::add);
                pages.subtitle().ifPresent(keys::add);
                addPages(pages.pages(), keys);
            }
        }
        return keys;
    }

    private static void addPages(List<Guide.Page> pages, Set<String> keys) {
        for (Guide.Page page : pages) {
            keys.add(page.title());
            keys.add(page.text());
        }
    }

    private static void readLang(Path file, Set<String> keys, Map<String, Map<String, String>> texts) {
        String name = file.getFileName().toString();
        String language = name.substring(0, name.length() - ".json".length());
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, JsonElement> entry : JsonParser.parseReader(reader).getAsJsonObject().entrySet()) {
                if (keys.contains(entry.getKey()) && entry.getValue().isJsonPrimitive()) {
                    texts.computeIfAbsent(language, key -> new HashMap<>()).put(entry.getKey(), entry.getValue().getAsString());
                }
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not read {}", file, e);
        }
    }

    private static Map<ResourceLocation, ByteBuffer> readImages(Path root, Map<String, Map<String, String>> texts) {
        Map<ResourceLocation, ByteBuffer> images = new HashMap<>();
        long total = 0;
        for (Map<String, String> language : texts.values()) {
            for (String text : language.values()) {
                Matcher matcher = IMAGE.matcher(text);
                while (matcher.find()) {
                    ResourceLocation id = ResourceLocation.tryParse(matcher.group(1));
                    if (id == null || images.containsKey(id)) {
                        continue;
                    }
                    Path file = root.resolve("assets").resolve(id.getNamespace()).resolve(id.getPath()).normalize();
                    if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                        continue;
                    }
                    try {
                        long size = Files.size(file);
                        if (size > MAX_IMAGE_BYTES || total + size > MAX_TOTAL_BYTES) {
                            LOGGER.warn("Picture {} is left out of the book: too large", id);
                            continue;
                        }
                        images.put(id, ByteBuffer.wrap(Files.readAllBytes(file)));
                        total += size;
                    } catch (IOException e) {
                        LOGGER.warn("Could not read {}", file, e);
                    }
                }
            }
        }
        return images;
    }
}
