package works.repka.rysquests.client.integration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.RYSQuests;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.client.screen.BookScreen;
import works.repka.rysquests.client.screen.QuestWords;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.TaskDef;
import works.repka.rysquests.progress.QuestLogic;
import works.repka.rysquests.progress.QuestPlace;
import works.repka.rysquests.progress.QuestStatus;
import works.repka.rysquests.task.VisitTask;
import works.repka.ryscore.map.MapApi;
import works.repka.ryscore.ui.Icons;
import works.repka.ryscore.world.Labels;

/**
 * The book on the family's map (RYSMap plan, step T3; RYSCore 1.13 {@code map.MapApi} and {@code world.Labels}): the layer
 * "Цели квестов" with a mark at the place of every open quest that sends the player somewhere ({@link QuestPlace}), its panel with
 * "Открыть квест" and "Сохранить точкой", the layer's search by quest names, and a label in the world for the goals of pinned quests
 * only, so that the screen stays clear.
 *
 * <p>Everything comes from the client's copies of the book and of the progress; nothing is sent. The goals are worked out again only
 * when the book or the progress changed ({@link #revision}), the map and the labels read the result. Without RYSMap the core keeps the
 * layer and the source and nobody reads them, and the book shows no "На карте".
 *
 * <p>A goal is shown while its quest is open (available or started), on a chapter the book shows, not a secret yet, not behind a stage
 * the player has not reached, and while its place is still ahead: a place already visited drops off, the next one of the quest takes
 * its turn.
 */
public final class QuestGoals implements MapApi.Layer, Labels.Source {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "goals");
    /** The map's own amber of goals (RYSMap mockup 19, MapApi.Highlight's default): the marks of a quest read as goals on any theme. */
    static final int COLOR = 0xFFE4B23C;
    private static final QuestGoals INSTANCE = new QuestGoals();

    /** A quest with the place still ahead of it. */
    private record Goal(Quest quest, QuestPlace place, ResourceKey<Level> dimension, UUID id) {
    }

    private long builtAt = -1;
    private List<Goal> goals = List.of();

    private QuestGoals() {
    }

    /** At the start of the client: the layer and the source wait in the core's registries until RYSMap reads them. */
    public static void register() {
        MapApi.get().layer(INSTANCE);
        Labels.source(INSTANCE);
    }

    /** Whether the map is there to show a place on: RYSMap is installed. */
    public static boolean mapPresent() {
        return MapApi.get().present();
    }

    /** The place of a quest's task on the map, or empty when the task names no position. */
    public static Optional<QuestPlace> place(Quest quest, int task) {
        if (task < 0 || task >= quest.tasks().size() || !(quest.tasks().get(task) instanceof VisitTask visit) || visit.pos().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(QuestPlace.of(visit, task));
    }

    /** "На карте" in the book: opens the map on the place of the quest's task. */
    public static void show(Quest quest, int task) {
        place(quest, task).ifPresent(place -> MapApi.get().open(target(quest, place)));
    }

    // --- the layer ---

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public Component name() {
        return Component.translatable("rysquests.map.goals");
    }

    @Override
    public Icons.Shape icon() {
        return Icons.Shape.TARGET;
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public long revision() {
        return (long) ClientBook.version() << 32 | ClientProgress.version() & 0xFFFFFFFFL;
    }

    @Override
    public Collection<MapApi.Marker> markers(ResourceKey<Level> dimension) {
        List<MapApi.Marker> out = new ArrayList<>();
        for (Goal goal : goals()) {
            if (goal.dimension.equals(dimension)) {
                out.add(marker(goal));
            }
        }
        return out;
    }

    @Override
    public List<MapApi.Hit> search(String query) {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return List.of();
        }
        List<MapApi.Hit> out = new ArrayList<>();
        for (Goal goal : goals()) {
            Component title = Component.translatable(goal.quest.title());
            if (title.getString().toLowerCase(Locale.ROOT).contains(needle)) {
                out.add(MapApi.Hit.builder(title, target(goal.quest, goal.place)).note(QuestWords.chapter(goal.quest))
                        .item(QuestWords.icon(goal.quest)).color(COLOR).build());
            }
        }
        return out;
    }

    // --- the labels in the world ---

    @Override
    public Collection<Labels.Label> labels(ResourceKey<Level> dimension) {
        List<ResourceLocation> pinned = ClientProgress.pinned();
        if (pinned.isEmpty()) {
            return List.of();
        }
        List<Labels.Label> out = new ArrayList<>();
        for (Goal goal : goals()) {
            if (goal.dimension.equals(dimension) && pinned.contains(goal.quest.id())) {
                out.add(Labels.Label.builder(goal.id).pos(goal.dimension, goal.place.x(), goal.place.y(), goal.place.z()).kind(Labels.Kind.GOAL)
                        .item(QuestWords.icon(goal.quest)).color(COLOR).text(Component.translatable(goal.quest.title())).always(true).build());
            }
        }
        return out;
    }

    // --- the goals ---

    /** The goals of the current book and progress, worked out again only after either changed. Main thread. */
    private List<Goal> goals() {
        long now = revision();
        if (now != builtAt) {
            builtAt = now;
            goals = build(ClientBook.current());
        }
        return goals;
    }

    private static List<Goal> build(QuestBook book) {
        Set<ResourceLocation> chapters = ItemLinkIndex.shownChapters(book);
        List<Goal> out = new ArrayList<>();
        for (Quest quest : book.quests().values()) {
            if (!ItemLinkIndex.listed(book, chapters, quest) || !ItemLinkIndex.shownNow(quest, ClientProgress::get, ClientProgress::isStageLocked)) {
                continue;
            }
            QuestStatus status = QuestLogic.status(quest, ClientProgress::get);
            if (status != QuestStatus.AVAILABLE && status != QuestStatus.IN_PROGRESS) {
                continue;
            }
            QuestPlace.ahead(quest, ClientProgress.get(quest.id()).tasks()).ifPresent(place -> {
                ResourceLocation dimension = place.dimension();
                out.add(new Goal(quest, place, ResourceKey.create(Registries.DIMENSION, dimension),
                        UUID.nameUUIDFromBytes(("rysquests:goal/" + quest.id()).getBytes(java.nio.charset.StandardCharsets.UTF_8))));
            });
        }
        return out;
    }

    private static MapApi.Marker marker(Goal goal) {
        Quest quest = goal.quest;
        QuestPlace place = goal.place;
        TaskDef task = quest.tasks().get(place.task());
        return MapApi.Marker.builder(goal.id).pos(goal.dimension, place.x(), place.y(), place.z()).kind(MapApi.Kind.GOAL)
                .item(QuestWords.icon(quest)).color(COLOR).label(Component.translatable(quest.title()))
                .note(Component.literal(QuestWords.task(quest, task))).radius(place.radius())
                .panel(() -> panel(quest, place))
                .build();
    }

    /** The right panel of a goal (RYSMap mockup 19): what the quest is, where it stands, and the two ways on. */
    static MapApi.Panel panel(Quest quest, QuestPlace place) {
        MapApi.Panel.Builder panel = MapApi.Panel.builder(Component.translatable(quest.title()))
                .subtitle(Component.translatable("rysquests.map.goal.subtitle"))
                .text(quest.subtitle().<Component>map(Component::translatable).orElse(null));
        Component chapter = QuestWords.chapter(quest);
        if (!chapter.getString().isEmpty()) {
            panel.row(Component.translatable("rysquests.map.goal.chapter"), chapter);
        }
        panel.row(Component.translatable("rysquests.map.goal.task"), Component.literal(QuestWords.task(quest, quest.tasks().get(place.task()))));
        String rewards = QuestWords.rewards(quest);
        if (!rewards.isEmpty()) {
            panel.row(Component.translatable("rysquests.map.goal.reward"), Component.literal(rewards));
        }
        ResourceLocation id = quest.id();
        return panel
                .action(MapApi.Action.builder(Component.translatable("rysquests.map.goal.open"), () -> BookScreen.openAt(id)).primary(true).build())
                .action(MapApi.Action.builder(Component.translatable("rysquests.map.goal.save"), () -> MapApi.get().newPoint(target(quest, place))).build())
                .build();
    }

    static MapApi.Target target(Quest quest, QuestPlace place) {
        return MapApi.Target.builder(ResourceKey.create(Registries.DIMENSION, place.dimension()), place.x(), place.y(), place.z())
                .radius(Math.max(3, place.radius())).label(Component.translatable(quest.title())).build();
    }

    /** The goals the layer shows now, by quest id, for the autotest. */
    public static List<ResourceLocation> goalsForTest() {
        return INSTANCE.goals().stream().map(goal -> goal.quest.id()).toList();
    }

    /** The panel of a quest's goal, for the autotest; null when the quest has no goal on the map now. */
    public static MapApi.@Nullable Panel panelForTest(ResourceLocation quest) {
        return INSTANCE.goals().stream().filter(goal -> goal.quest.id().equals(quest)).findFirst().map(goal -> panel(goal.quest, goal.place)).orElse(null);
    }

    /** The labels in the world of a dimension, for the autotest. */
    public static Collection<Labels.Label> labelsForTest(ResourceKey<Level> dimension) {
        return INSTANCE.labels(dimension);
    }

    /** The layer's search, for the autotest. */
    public static List<MapApi.Hit> searchForTest(String query) {
        return INSTANCE.search(query);
    }

    /** The marks of a dimension, for the autotest. */
    public static Collection<MapApi.Marker> markersForTest(ResourceKey<Level> dimension) {
        return INSTANCE.markers(dimension);
    }
}
