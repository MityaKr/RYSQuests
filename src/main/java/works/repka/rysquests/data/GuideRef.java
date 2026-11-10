package works.repka.rysquests.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** A link to a guide, or to one of its pages: {@code example:furnace} or {@code example:furnace#fuel}. */
public record GuideRef(ResourceLocation guide, Optional<String> page) {
    private static final char PAGE = '#';

    public static final Codec<GuideRef> CODEC = Codec.STRING.comapFlatMap(
            text -> parse(text).map(DataResult::success).orElseGet(() -> DataResult.error(() -> "Not a guide link: " + text)),
            GuideRef::toString);

    public static Optional<GuideRef> parse(String text) {
        int mark = text.indexOf(PAGE);
        String guide = mark < 0 ? text : text.substring(0, mark);
        String page = mark < 0 ? "" : text.substring(mark + 1);
        ResourceLocation id = ResourceLocation.tryParse(guide);
        if (id == null || (mark >= 0 && page.isEmpty())) {
            return Optional.empty();
        }
        return Optional.of(new GuideRef(id, page.isEmpty() ? Optional.empty() : Optional.of(page)));
    }

    @Override
    public String toString() {
        return guide + page.map(id -> PAGE + id).orElse("");
    }
}
