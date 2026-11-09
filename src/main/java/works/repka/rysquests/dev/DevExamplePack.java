package works.repka.rysquests.dev;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.BuiltInPackSource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Development-only packs that serve the example books straight from the source tree.
 *
 * <p>Active only when {@value #PATH_PROPERTY} or {@value #BROKEN_PATH_PROPERTY} is set, which the Gradle dev runs do.
 * The example is always on and registered for both pack types, so its data and lang files load without touching
 * a world's datapacks folder. The broken book is a data pack that stays off until {@code /datapack enable}.
 */
public final class DevExamplePack {
    public static final String PATH_PROPERTY = "rysquests.dev.examplePack";
    public static final String BROKEN_PATH_PROPERTY = "rysquests.dev.brokenPack";
    public static final String BROKEN_PACK_ID = "rysquests/dev_broken";

    private static final Logger LOGGER = LoggerFactory.getLogger(DevExamplePack.class);
    private static final String PACK_ID = "rysquests/dev_example";

    private DevExamplePack() {
    }

    public static void register(IEventBus modBus) {
        Path example = configuredPack(PATH_PROPERTY);
        Path broken = configuredPack(BROKEN_PATH_PROPERTY);
        if (example == null && broken == null) {
            return;
        }
        modBus.addListener((AddPackFindersEvent event) -> {
            if (example != null) {
                addPack(event, create(example, event.getPackType()), example);
            }
            if (broken != null && event.getPackType() == PackType.SERVER_DATA) {
                addPack(event, create(broken, event.getPackType(), BROKEN_PACK_ID, PackSource.FEATURE, false), broken);
            }
        });
    }

    @Nullable
    private static Path configuredPack(String property) {
        String configured = System.getProperty(property);
        if (configured == null || configured.isBlank()) {
            return null;
        }
        Path root = Path.of(configured);
        if (!Files.isRegularFile(root.resolve("pack.mcmeta"))) {
            LOGGER.warn("Dev pack not found at {}", root);
            return null;
        }
        return root;
    }

    private static void addPack(AddPackFindersEvent event, @Nullable Pack pack, Path root) {
        if (pack == null) {
            LOGGER.warn("Dev pack at {} has unreadable metadata", root);
            return;
        }
        event.addRepositorySource(consumer -> consumer.accept(pack));
        LOGGER.info("Dev pack {} added for {}", pack.getId(), event.getPackType());
    }

    @Nullable
    static Pack create(Path root, PackType type) {
        return create(root, type, PACK_ID, PackSource.BUILT_IN, true);
    }

    @Nullable
    private static Pack create(Path root, PackType type, String id, PackSource source, boolean required) {
        PackLocationInfo location = new PackLocationInfo(id, Component.literal(id), source, Optional.empty());
        return Pack.readMetaAndCreate(
                location,
                BuiltInPackSource.fromName(info -> new PathPackResources(info, root)),
                type,
                new PackSelectionConfig(required, Pack.Position.TOP, false));
    }
}
