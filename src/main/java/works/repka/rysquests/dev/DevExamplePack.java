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
 * Development-only pack that serves the example book straight from the source tree.
 *
 * <p>Active only when {@value #PATH_PROPERTY} is set, which the Gradle dev runs do. Registered for both
 * pack types so the example's data and its lang files load without touching a world's datapacks folder.
 */
public final class DevExamplePack {
    public static final String PATH_PROPERTY = "rysquests.dev.examplePack";

    private static final Logger LOGGER = LoggerFactory.getLogger(DevExamplePack.class);
    private static final String PACK_ID = "rysquests/dev_example";

    private DevExamplePack() {
    }

    public static void register(IEventBus modBus) {
        String configured = System.getProperty(PATH_PROPERTY);
        if (configured == null || configured.isBlank()) {
            return;
        }
        Path root = Path.of(configured);
        if (!Files.isRegularFile(root.resolve("pack.mcmeta"))) {
            LOGGER.warn("Dev example pack not found at {}", root);
            return;
        }
        modBus.addListener((AddPackFindersEvent event) -> addPack(event, root));
    }

    private static void addPack(AddPackFindersEvent event, Path root) {
        Pack pack = create(root, event.getPackType());
        if (pack == null) {
            LOGGER.warn("Dev example pack at {} has unreadable metadata", root);
            return;
        }
        event.addRepositorySource(consumer -> consumer.accept(pack));
        LOGGER.info("Dev example pack added for {}", event.getPackType());
    }

    @Nullable
    static Pack create(Path root, PackType type) {
        PackLocationInfo location = new PackLocationInfo(
                PACK_ID, Component.literal("RYSQuests dev example"), PackSource.BUILT_IN, Optional.empty());
        return Pack.readMetaAndCreate(
                location,
                BuiltInPackSource.fromName(info -> new PathPackResources(info, root)),
                type,
                new PackSelectionConfig(true, Pack.Position.TOP, false));
    }
}
