package works.repka.rysquests.client.dev;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.client.ClientAchievements;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.client.ClientShop;
import works.repka.rysquests.progress.ProgressStore;
import works.repka.rysquests.progress.QuestProgress;

/**
 * What a player keeps across an update, as the client sees it: quests done and claimed, pins, the wallet, the shop's history and the
 * active title. The same file lives in the autotest of RYSQuests 0.1.0 and of the new book, so that the two can be compared line by line.
 */
final class UpgradeSnapshot {
    private UpgradeSnapshot() {
    }

    static List<String> lines() {
        List<String> out = new ArrayList<>();
        ClientBook.current().quests().keySet().stream().sorted(Comparator.comparing(ResourceLocation::toString)).forEach(id -> {
            QuestProgress progress = ClientProgress.get(id);
            if (progress.completedAt() > 0 || !progress.claimed().isEmpty() || progress.times() > 0) {
                out.add("quest " + id + " done=" + progress.completed() + " claimed=" + progress.claimed() + " times=" + progress.times());
            }
        });
        out.add("pinned " + ClientProgress.pinned());
        out.add("wallet coin " + ClientShop.balance("coin"));
        for (ProgressStore.ShopLine line : ClientShop.history()) {
            out.add("shop " + line.ref() + " delta=" + line.delta() + " " + line.currency() + " times=" + line.times() + " at=" + line.time());
        }
        out.add("title " + ClientAchievements.activeTitle());
        return out;
    }

    static Path file(String tag) {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("logs").resolve("rysquests-upgrade-" + tag + ".txt");
    }

    static void write(String tag) {
        try {
            Files.write(file(tag), lines(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<String> read(String tag) {
        try {
            return Files.readAllLines(file(tag), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
