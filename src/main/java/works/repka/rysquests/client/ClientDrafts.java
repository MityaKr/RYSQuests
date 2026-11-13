package works.repka.rysquests.client;

import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.FMLPaths;
import works.repka.rysquests.editor.EditorDrafts;

/**
 * Where this client keeps the quest editor's unsaved work: {@code rysquests/drafts/<server or world>/} in the game folder, so that
 * drafts of one server never turn up on another. The editor writes a draft as the author works and drops it once the chapter is
 * saved or the changes are thrown away.
 */
public final class ClientDrafts {
    private ClientDrafts() {
    }

    /** The folder of the server or the singleplayer world this client is in. */
    public static Path folder() {
        Minecraft minecraft = Minecraft.getInstance();
        String place;
        if (minecraft.getSingleplayerServer() != null) {
            place = "world_" + minecraft.getSingleplayerServer().getWorldPath(LevelResource.ROOT).normalize().getFileName();
        } else if (minecraft.getCurrentServer() != null) {
            place = "server_" + minecraft.getCurrentServer().ip;
        } else {
            place = "unknown";
        }
        return FMLPaths.GAMEDIR.get().resolve("rysquests").resolve("drafts").resolve(place.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "_"));
    }

    public static void save(EditorDrafts.ChapterDraft draft) throws IOException {
        EditorDrafts.save(folder(), draft, ops());
    }

    public static Optional<EditorDrafts.ChapterDraft> load(ResourceLocation chapter) {
        return EditorDrafts.load(folder(), chapter, ops());
    }

    public static List<ResourceLocation> chapters() throws IOException {
        return EditorDrafts.chapters(folder());
    }

    public static void discard(ResourceLocation chapter) throws IOException {
        EditorDrafts.discard(folder(), chapter);
    }

    private static DynamicOps<JsonElement> ops() {
        return RegistryOps.create(JsonOps.INSTANCE, Minecraft.getInstance().level.registryAccess());
    }
}
