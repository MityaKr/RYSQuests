package works.repka.rysquests.dev;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import works.repka.rysquests.data.BookHolder;
import works.repka.rysquests.data.BookReport;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.RequiresMode;
import works.repka.rysquests.data.RewardDef;
import works.repka.rysquests.data.TaskDef;
import works.repka.rysquests.migrate.Migration;
import works.repka.rysquests.progress.ProgressStore;
import works.repka.rysquests.reward.ChoiceReward;
import works.repka.rysquests.reward.RandomReward;
import works.repka.rysquests.reward.StageReward;
import works.repka.rysquests.task.ItemRef;

/** Machine-readable reports about the book, for pack authors and for tools. All of it can run off the main thread. */
public final class DevReports {
    private static final Logger LOGGER = LoggerFactory.getLogger(DevReports.class);
    public static final String REPORT_JSON = "rysquests-report.json";
    public static final String REPORT_CSV = "rysquests-report.csv";
    public static final String REGISTRY_JSON = "rysquests-registry.json";
    public static final String FUNNEL_PREFIX = "funnel:";
    public static final String MIGRATIONS = "migrations";
    private static final int MAX_DEPTH = 256;

    public record Entry(String subject, String detail) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("subject").forGetter(Entry::subject),
                Codec.STRING.fieldOf("detail").forGetter(Entry::detail)
        ).apply(instance, Entry::new));
    }

    public record Section(String id, List<Entry> entries) {
        static final Codec<Section> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Section::id),
                Entry.CODEC.listOf().fieldOf("entries").forGetter(Section::entries)
        ).apply(instance, Section::new));
    }

    public record Report(int chapters, int quests, List<Section> sections) {
        public static final Codec<Report> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("chapters").forGetter(Report::chapters),
                Codec.INT.fieldOf("quests").forGetter(Report::quests),
                Section.CODEC.listOf().fieldOf("sections").forGetter(Report::sections)
        ).apply(instance, Report::new));
    }

    private DevReports() {
    }

    /** What the report needs from the world's progress, copied on the server thread. */
    public record Standing(List<Set<ResourceLocation>> teams, Set<String> appliedMigrations) {
    }

    public static Standing standing(MinecraftServer server) {
        ProgressStore.MigrationSnapshot snapshot = ProgressStore.get(server).snapshot();
        List<Set<ResourceLocation>> teams = new ArrayList<>();
        snapshot.teams().values().forEach(quests -> {
            Set<ResourceLocation> finished = new HashSet<>();
            quests.forEach((id, progress) -> {
                if (progress.completed()) {
                    finished.add(id);
                }
            });
            teams.add(finished);
        });
        return new Standing(teams, new HashSet<>(snapshot.applied()));
    }

    public static Report build(MinecraftServer server, Standing standing) {
        BookHolder.Loaded loaded = BookHolder.current();
        QuestBook book = loaded.book();
        List<Section> sections = new ArrayList<>();
        sections.add(new Section("errors", issues(loaded.report(), BookReport.Severity.ERROR)));
        sections.add(new Section("warnings", issues(loaded.report(), BookReport.Severity.WARNING)));
        sections.add(new Section("unreachable", unreachable(server, book)));
        sections.add(new Section("no_recipe", withoutRecipe(server, book)));
        sections.add(new Section("shop_cycles", shopCycles(book)));
        sections.add(new Section(MIGRATIONS, pendingMigrations(loaded.migrations(), standing)));
        sections.addAll(funnels(book, standing));
        return new Report(book.chapters().size(), book.quests().size(), sections);
    }

    private static List<Entry> pendingMigrations(List<Migration> migrations, Standing standing) {
        List<Entry> entries = new ArrayList<>();
        for (Migration migration : migrations) {
            if (!standing.appliedMigrations().contains(migration.id())) {
                entries.add(new Entry(migration.op(), describe(migration)));
            }
        }
        return entries;
    }

    private static String describe(Migration migration) {
        return switch (migration) {
            case Migration.Rename rename -> rename.from() + " \u2192 " + rename.to();
            case Migration.Merge merge -> merge.from().stream().map(ResourceLocation::toString).collect(java.util.stream.Collectors.joining(" + ")) + " \u2192 " + merge.to();
            case Migration.Remove remove -> remove.from().toString();
            case Migration.FtbqMap map -> map.fromFtbq().size() + " FTB ids";
            default -> migration.id();
        };
    }

    /** For each chapter, the share of teams that finished each quest, in dependency order. */
    private static List<Section> funnels(QuestBook book, Standing standing) {
        List<Section> sections = new ArrayList<>();
        int teams = standing.teams().size();
        if (teams == 0) {
            return sections;
        }
        Map<ResourceLocation, Integer> finished = new HashMap<>();
        standing.teams().forEach(team -> team.forEach(id -> finished.merge(id, 1, Integer::sum)));
        Map<ResourceLocation, List<Quest>> byChapter = new HashMap<>();
        for (Quest quest : book.quests().values()) {
            quest.chapter().ifPresent(chapter -> byChapter.computeIfAbsent(chapter, key -> new ArrayList<>()).add(quest));
        }
        byChapter.forEach((chapter, quests) -> {
            Map<ResourceLocation, Integer> depths = new HashMap<>();
            quests.sort(java.util.Comparator.comparingInt(quest -> depth(quest, book, depths, 0)));
            List<Entry> entries = new ArrayList<>();
            for (Quest quest : quests) {
                entries.add(new Entry(quest.id().toString(), String.valueOf(finished.getOrDefault(quest.id(), 0) * 100 / teams)));
            }
            sections.add(new Section(FUNNEL_PREFIX + chapter, entries));
        });
        return sections;
    }

    private static int depth(Quest quest, QuestBook book, Map<ResourceLocation, Integer> depths, int guard) {
        Integer known = depths.get(quest.id());
        if (known != null) {
            return known;
        }
        int depth = 0;
        if (guard < MAX_DEPTH) {
            for (ResourceLocation id : quest.requires()) {
                Quest required = book.quests().get(id);
                if (required != null) {
                    depth = Math.max(depth, depth(required, book, depths, guard + 1) + 1);
                }
            }
        }
        depths.put(quest.id(), depth);
        return depth;
    }

    private static List<Entry> issues(BookReport report, BookReport.Severity severity) {
        return report.issues().stream()
                .filter(issue -> issue.severity() == severity)
                .map(issue -> new Entry(issue.subject(), issue.code() + (issue.detail().isEmpty() ? "" : ": " + issue.detail())))
                .toList();
    }

    /** Chains of trades that make money from nothing. */
    private static List<Entry> shopCycles(QuestBook book) {
        List<Entry> entries = new ArrayList<>();
        for (List<ResourceLocation> cycle : works.repka.rysquests.shop.ShopCycles.find(book.offers().values())) {
            entries.add(new Entry(cycle.getFirst().toString(), cycle.toString()));
        }
        return entries;
    }

    /** Quests no player can ever open: an impossible dependency count, or a stage nothing can give. */
    private static List<Entry> unreachable(MinecraftServer server, QuestBook book) {
        Set<String> grantedStages = new HashSet<>();
        for (Quest quest : book.quests().values()) {
            quest.rewards().forEach(reward -> collectStages(reward, grantedStages));
        }
        List<Entry> entries = new ArrayList<>();
        for (Quest quest : book.quests().values()) {
            if (quest.requiresMode() instanceof RequiresMode.AtLeast atLeast && atLeast.n() > quest.requires().size()) {
                entries.add(new Entry(quest.id().toString(), "requires " + atLeast.n() + " of " + quest.requires().size() + " dependencies"));
            }
            quest.stageRequired().ifPresent(stage -> {
                ResourceLocation advancement = ResourceLocation.tryParse(stage);
                boolean isAdvancement = advancement != null && server.getAdvancements().get(advancement) != null;
                if (!isAdvancement && !grantedStages.contains(stage)) {
                    entries.add(new Entry(quest.id().toString(), "no reward or advancement gives stage " + stage));
                }
            });
        }
        return entries;
    }

    private static void collectStages(RewardDef reward, Set<String> stages) {
        switch (reward) {
            case StageReward stage -> stages.add(stage.stage());
            case ChoiceReward choice -> choice.of().forEach(option -> collectStages(option, stages));
            case RandomReward random -> random.of().forEach(option -> collectStages(option, stages));
            default -> {
            }
        }
    }

    /** Candidates only: machine recipes of some mods report no result here. */
    private static List<Entry> withoutRecipe(MinecraftServer server, QuestBook book) {
        Set<ResourceLocation> craftable = new HashSet<>();
        server.getRecipeManager().getRecipes().forEach(holder -> {
            ItemStack result = holder.value().getResultItem(server.registryAccess());
            if (!result.isEmpty()) {
                craftable.add(BuiltInRegistries.ITEM.getKey(result.getItem()));
            }
        });
        List<Entry> entries = new ArrayList<>();
        Set<ResourceLocation> seen = new HashSet<>();
        for (Quest quest : book.quests().values()) {
            for (TaskDef task : quest.tasks()) {
                for (ItemRef ref : task.itemRefs()) {
                    if (!ref.tag() && !craftable.contains(ref.id()) && seen.add(ref.id())) {
                        entries.add(new Entry(ref.id().toString(), "first used by " + quest.id()));
                    }
                }
            }
        }
        return entries;
    }

    public static void write(MinecraftServer server, Report report) {
        Path logs = server.getServerDirectory().resolve("logs");
        try {
            Files.createDirectories(logs);
            Files.writeString(logs.resolve(REPORT_JSON),
                    new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(Report.CODEC.encodeStart(JsonOps.INSTANCE, report).getOrThrow()),
                    StandardCharsets.UTF_8);
            List<String> csv = new ArrayList<>();
            csv.add("section,subject,detail");
            report.sections().forEach(section -> section.entries().forEach(entry -> csv.add(section.id() + "," + quote(entry.subject()) + "," + quote(entry.detail()))));
            Files.write(logs.resolve(REPORT_CSV), csv, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Could not write the book report", e);
        }
    }

    private static String quote(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    /** Every id a book may refer to in this pack, for checking a book without starting the game. */
    public static void writeRegistry(MinecraftServer server) {
        JsonObject out = new JsonObject();
        out.add("items", ids(BuiltInRegistries.ITEM));
        out.add("fluids", ids(BuiltInRegistries.FLUID));
        out.add("entities", ids(BuiltInRegistries.ENTITY_TYPE));
        out.add("item_tags", tags(BuiltInRegistries.ITEM.getTagNames().map(key -> key.location()).toList()));
        out.add("dimensions", ids(server.registryAccess().registryOrThrow(Registries.DIMENSION_TYPE)));
        out.add("levels", tags(server.levelKeys().stream().map(ResourceKey::location).toList()));
        out.add("biomes", ids(server.registryAccess().registryOrThrow(Registries.BIOME)));
        out.add("structures", ids(server.registryAccess().registryOrThrow(Registries.STRUCTURE)));
        out.add("advancements", tags(server.getAdvancements().getAllAdvancements().stream().map(holder -> holder.id()).toList()));
        out.add("custom_stats", ids(BuiltInRegistries.CUSTOM_STAT));
        Path file = server.getServerDirectory().resolve("logs").resolve(REGISTRY_JSON);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(out), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Could not write {}", file, e);
        }
    }

    private static <T> JsonArray ids(Registry<T> registry) {
        return tags(new ArrayList<>(registry.keySet()));
    }

    private static JsonArray tags(List<ResourceLocation> ids) {
        JsonArray array = new JsonArray();
        new TreeSet<>(ids).forEach(id -> array.add(id.toString()));
        return array;
    }
}
