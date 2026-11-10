package works.repka.rysquests.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import works.repka.rysquests.data.BookAssets;

/** Puts the translations and pictures that came with the book on top of the client's own resources (ADR-35). */
public final class ClientAssets {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientAssets.class);
    private static final String FALLBACK_LANGUAGE = "en_us";
    private static final Map<ResourceLocation, int[]> SIZES = new HashMap<>();

    private static BookAssets current = BookAssets.EMPTY;

    private ClientAssets() {
    }

    /** Language files reload with the resources, which drops the overlay; a listener puts it back through this. */
    private static final class Overlay extends Language {
        private final Language base;
        private final Map<String, String> texts;

        Overlay(Language base, Map<String, String> texts) {
            this.base = base;
            this.texts = texts;
        }

        @Override
        public String getOrDefault(String key, String fallback) {
            String text = texts.get(key);
            return text != null ? text : base.getOrDefault(key, fallback);
        }

        @Override
        public boolean has(String key) {
            return texts.containsKey(key) || base.has(key);
        }

        @Override
        public boolean isDefaultRightToLeft() {
            return base.isDefaultRightToLeft();
        }

        @Override
        public FormattedCharSequence getVisualOrder(FormattedText text) {
            return base.getVisualOrder(text);
        }
    }

    static void apply(BookAssets assets) {
        Minecraft mc = Minecraft.getInstance();
        SIZES.keySet().stream().filter(id -> !assets.images().containsKey(id)).toList().forEach(id -> {
            mc.getTextureManager().release(id);
            SIZES.remove(id);
        });
        current = assets;
        assets.images().forEach((id, bytes) -> {
            byte[] copy = new byte[bytes.remaining()];
            bytes.duplicate().get(copy);
            addImage(id, copy);
        });
        applyLanguage();
    }

    /** Shows a picture before it is saved, in the editor's preview. */
    public static void addImage(ResourceLocation id, byte[] png) {
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(png));
            SIZES.put(id, new int[] {image.getWidth(), image.getHeight()});
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
        } catch (IOException e) {
            LOGGER.warn("Could not read picture {}", id, e);
        }
    }

    /** @return the width and height of a picture that came with the book, or null */
    @Nullable
    public static int[] imageSize(ResourceLocation id) {
        return SIZES.get(id);
    }

    /** A string can exist in another language only; better to show that than a raw key. */
    public static void applyLanguage() {
        Language language = Language.getInstance();
        Language base = language instanceof Overlay overlay ? overlay.base : language;
        Map<String, String> texts = new HashMap<>();
        current.texts().forEach((code, strings) -> strings.forEach(texts::putIfAbsent));
        texts.putAll(current.texts().getOrDefault(FALLBACK_LANGUAGE, Map.of()));
        texts.putAll(current.texts().getOrDefault(Minecraft.getInstance().getLanguageManager().getSelected(), Map.of()));
        Language.inject(texts.isEmpty() ? base : new Overlay(base, texts));
    }
}
