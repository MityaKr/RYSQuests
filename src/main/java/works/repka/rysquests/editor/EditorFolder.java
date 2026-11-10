package works.repka.rysquests.editor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.BuiltInPackSource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import works.repka.rysquests.CommonConfig;

/** The folder the editor writes to. Unless it is KubeJS's own, which KubeJS serves itself, it is an always-on pack. */
public final class EditorFolder {
    private static final String PACK_ID = "rysquests/editor";
    private static final String KUBEJS = "kubejs";

    private EditorFolder() {
    }

    /** A setting that points outside the game folder is ignored: a config file should not reach the rest of the disk. */
    public static Path root() {
        Path game = FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
        Path configured = game.resolve(CommonConfig.EDITOR_FOLDER.get()).normalize();
        return configured.startsWith(game) && !configured.equals(game) ? configured : game.resolve(CommonConfig.DEFAULT_EDITOR_FOLDER);
    }

    public static boolean servedByKubeJs() {
        return root().endsWith(KUBEJS);
    }

    public static void register(IEventBus modBus) {
        modBus.addListener((AddPackFindersEvent event) -> event.addRepositorySource(consumer -> {
            Path root = root();
            if (servedByKubeJs() || !Files.isRegularFile(root.resolve("pack.mcmeta"))) {
                return;
            }
            Pack pack = Pack.readMetaAndCreate(
                    new PackLocationInfo(PACK_ID, Component.literal(PACK_ID), PackSource.BUILT_IN, Optional.empty()),
                    BuiltInPackSource.fromName(info -> new PathPackResources(info, root)),
                    event.getPackType(),
                    new PackSelectionConfig(true, Pack.Position.TOP, false));
            if (pack != null) {
                consumer.accept(pack);
            }
        }));
    }
}
