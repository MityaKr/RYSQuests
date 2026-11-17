package works.repka.rysquests.client.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import works.repka.ryscore.ui.Notice;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientConfig;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.client.input.QuestKeys;
import works.repka.rysquests.client.screen.BookScreen;
import works.repka.rysquests.client.screen.TaskText;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.RewardDef;
import works.repka.rysquests.data.TeamMode;
import works.repka.rysquests.network.ProgressPayload;
import works.repka.rysquests.progress.QuestLogic;
import works.repka.rysquests.reward.ChoiceReward;
import works.repka.rysquests.reward.CurrencyReward;
import works.repka.rysquests.reward.ItemReward;
import works.repka.rysquests.reward.LootTableReward;
import works.repka.rysquests.reward.RandomReward;
import works.repka.rysquests.reward.XpReward;

/**
 * Messages about finished quests through the family's notices (RYSCore {@link Notice}, design {@code _audit/notify}, variant A): they
 * stand in the game's own column of toasts, top right. An ordinary quest gives a quiet card, a milestone, a finished chapter and a
 * new epoch give a larger one; the third and later quests of a series fold into a summary. A pinned quest of the player's own raises
 * no card: its card on the left changes its line. K and a click open the book on the quest.
 */
public final class QuestNotices {
    private static final String MILESTONE_TAG = "milestone";
    private static final String EPOCH_TAG = "epoch";
    private static final List<String> EPOCH_GROUPS = List.of("eras", "epochs");
    private static final Pattern NUMBERED = Pattern.compile("^([IVXLC]+)\\.\\s*(.+)$");
    private static final int CHIPS = 4;
    private static final long TEAMMATE_MS = 3500;
    private static final long EPOCH_MS = 10_000;

    private QuestNotices() {
    }

    /** Quests one update of progress has just completed, after the update is in: a chapter they close counts as closed. */
    public static void completed(List<Quest> quests, Map<ResourceLocation, ProgressPayload.Done> done) {
        if (!ClientConfig.COMPLETION_TOAST.get()) {
            return;
        }
        QuestBook book = ClientBook.current();
        for (Quest quest : quests) {
            ProgressPayload.Done info = done.getOrDefault(quest.id(), new ProgressPayload.Done(Optional.empty(), "", reward(quest)));
            show(book, quest, info);
        }
    }

    private static int reward(Quest quest) {
        return quest.rewards().isEmpty() ? ProgressPayload.Done.NONE : ProgressPayload.Done.WAITING;
    }

    private static void show(QuestBook book, Quest quest, ProgressPayload.Done info) {
        UUID self = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getUUID();
        boolean mate = info.by().isPresent() && !info.by().get().equals(self);
        if (mate && !ClientConfig.TEAM_QUEST_TOASTS.get()) {
            return;
        }
        // an epoch's reward is completed by taking it on its card (ADR-60): the player has just seen it
        if (book.isEpochReward(quest.id())) {
            return;
        }
        int epoch = epochOf(book, quest);
        if (epoch > 0) {
            epoch(book, quest, epoch);
            return;
        }
        Optional<Chapter> closed = closedChapter(book, quest);
        boolean waits = info.reward() == ProgressPayload.Done.WAITING;
        // One event, one card: a chapter closed by a quest whose reward does not wait shows only the chapter.
        if (closed.isEmpty() || waits) {
            if (milestone(quest)) {
                milestoneCard(quest, info, mate);
            } else if (mate || !ClientProgress.pinned().contains(quest.id())) {
                card(quest, info, mate);
            }
        }
        closed.ifPresent(chapter -> chapter(book, chapter, quest));
    }

    // --- the ordinary card ---

    private static void card(Quest quest, ProgressPayload.Done info, boolean mate) {
        boolean waits = info.reward() == ProgressPayload.Done.WAITING;
        Notice notice = Notice.card(Notice.QUESTS, "done:" + quest.id())
                .item(icon(quest))
                .title(Component.translatable(quest.title()).getString())
                .lines(lines(quest, info, mate))
                .hiddenWhen(QuestNotices::bookOpen)
                .sound(Notice.Sound.QUEST)
                .summary(count -> t("rysquests.notice.more", word("rysquests.plural.quest", count)), t("rysquests.notice.more_line"));
        if (mate) {
            notice.markHead(info.by().get()).millis(TEAMMATE_MS);
        } else {
            notice.check();
        }
        if (info.reward() == ProgressPayload.Done.RECEIVED) {
            notice.bright().millis(TEAMMATE_MS);
        }
        if (waits) {
            notice.key(QuestNotices::keyLabel).action(() -> BookScreen.openAt(quest.id()));
        }
        notice.show();
    }

    /** The reward line from the longest wording to the shortest: the core takes the first that fits. */
    private static String[] lines(Quest quest, ProgressPayload.Done info, boolean mate) {
        boolean waits = info.reward() == ProgressPayload.Done.WAITING;
        if (mate) {
            return waits ? new String[] {t("rysquests.notice.mate_waiting", info.name()), t("rysquests.notice.mate", info.name())}
                    : new String[] {t("rysquests.notice.mate", info.name())};
        }
        return switch (info.reward()) {
            case ProgressPayload.Done.NONE -> new String[] {t("rysquests.notice.done")};
            case ProgressPayload.Done.RECEIVED -> {
                String words = words(quest);
                yield words.isEmpty() ? new String[] {t("rysquests.notice.received_plain")} : new String[] {t("rysquests.notice.received", words)};
            }
            case ProgressPayload.Done.TEAM_TAKEN -> new String[] {t("rysquests.notice.team_taken")};
            default -> {
                Optional<ChoiceReward> choice = quest.rewards().stream().filter(ChoiceReward.class::isInstance).map(ChoiceReward.class::cast).findFirst();
                if (choice.isPresent() && quest.rewards().size() == 1) {
                    yield new String[] {t("rysquests.notice.choice", choice.get().of().size()), t("rysquests.notice.waiting_plain")};
                }
                String words = words(quest);
                boolean team = quest.rewards().stream().allMatch(reward -> reward.teamMode() == TeamMode.ONCE);
                if (words.isEmpty()) {
                    yield new String[] {t(team ? "rysquests.notice.team_waiting_plain" : "rysquests.notice.waiting_plain")};
                }
                yield team ? new String[] {t("rysquests.notice.team_waiting", words), t("rysquests.notice.team_waiting_short", words), t("rysquests.notice.waiting_plain")}
                        : new String[] {t("rysquests.notice.waiting", words), t("rysquests.notice.waiting_short", words), t("rysquests.notice.waiting_plain")};
            }
        };
    }

    /** At most two rewards in words ("25 опыта и 1 монета"); more, or a kind without words, leaves it empty. */
    private static String words(Quest quest) {
        List<String> parts = new ArrayList<>();
        for (RewardDef reward : quest.rewards()) {
            if (reward instanceof XpReward xp && xp.points().isPresent()) {
                parts.add(word("rysquests.plural.xp", xp.points().get()));
            } else if (reward instanceof XpReward xp && xp.levels().isPresent()) {
                parts.add(word("rysquests.plural.level", xp.levels().get()));
            } else if (reward instanceof CurrencyReward coins) {
                parts.add("coin".equals(coins.id()) ? word("rysquests.plural.coin", coins.amount()) : coins.amount() + " " + coins.id());
            } else if (reward instanceof ItemReward item) {
                parts.add(TaskText.stackOf(item.item()).getHoverName().getString() + (item.count() > 1 ? " ×" + item.count() : ""));
            } else {
                return "";
            }
        }
        if (parts.isEmpty() || parts.size() > 2) {
            return "";
        }
        return parts.size() == 1 ? parts.getFirst() : parts.getFirst() + t("rysquests.hud.and") + parts.getLast();
    }

    // --- the large cards ---

    private static void milestoneCard(Quest quest, ProgressPayload.Done info, boolean mate) {
        boolean waits = info.reward() == ProgressPayload.Done.WAITING;
        String line = mate ? t("rysquests.notice.mate", info.name())
                : t(waits ? "rysquests.notice.waiting_plain" : info.reward() == ProgressPayload.Done.NONE ? "rysquests.notice.done" : "rysquests.notice.received_plain");
        Notice notice = Notice.big(Notice.QUESTS, "milestone:" + quest.id())
                .rank(Notice.Rank.GOLD).item(icon(quest))
                .label(t("rysquests.notice.milestone")).title(Component.translatable(quest.title()).getString()).titleSize(14)
                .lines(line).sound(Notice.Sound.MILESTONE);
        if (mate) {
            notice.markHead(info.by().get());
        } else {
            notice.check();
        }
        chips(notice, quest);
        if (waits) {
            notice.key(QuestNotices::keyLabel).action(() -> BookScreen.openAt(quest.id()));
        }
        notice.show();
    }

    private static void chapter(QuestBook book, Chapter chapter, Quest last) {
        List<Quest> main = mainQuests(book, chapter.id());
        Notice notice = Notice.big(Notice.QUESTS, "chapter:" + chapter.id())
                .rank(Notice.Rank.GREEN).check()
                .item(chapter.icon().map(TaskText::stackOf).orElse(icon(last)))
                .label(t("rysquests.notice.chapter")).title(Component.translatable(chapter.title()).getString()).titleSize(15)
                .lines(t("rysquests.notice.chapter_line", main.size(), main.size()))
                .key(QuestNotices::keyLabel).action(() -> BookScreen.openAt(last.id()))
                .sound(Notice.Sound.CHAPTER);
        nextChapter(book, chapter).ifPresent(next -> notice.note(t("rysquests.notice.chapter_next", Component.translatable(next.title()).getString())));
        notice.show();
    }

    private static void epoch(QuestBook book, Quest quest, int number) {
        String title = Component.translatable(quest.title()).getString();
        String numeral = roman(number);
        Matcher numbered = NUMBERED.matcher(title);
        if (numbered.matches()) {
            numeral = numbered.group(1);
            title = numbered.group(2);
        }
        String name = book.meta().epoch(number).map(epoch -> Component.translatable(epoch.title()).getString()).orElse(title);
        List<String> chapters = new ArrayList<>();
        for (Chapter chapter : book.chapters().values()) {
            if (chapter.epoch().filter(n -> n == number).isPresent() && !chapter.hidden() && chapter.isStory()) {
                chapters.add(Component.translatable(chapter.title()).getString());
            }
        }
        Notice notice = Notice.big(Notice.QUESTS, "epoch:" + quest.id())
                .rank(Notice.Rank.GOLD).check().item(icon(quest))
                .label(t("rysquests.notice.epoch", numeral)).title(name).titleSize(16)
                .lines(chapters.isEmpty() ? new String[] {t("rysquests.notice.done")}
                        : new String[] {t("rysquests.notice.epoch_line", String.join(", ", chapters)), t("rysquests.notice.epoch_line", chapters.getFirst() + "…")})
                .millis(EPOCH_MS).sound(Notice.Sound.EPOCH);
        chips(notice, quest);
        if (!quest.rewards().isEmpty()) {
            notice.key(QuestNotices::keyLabel).action(() -> BookScreen.openAt(quest.id()));
        }
        notice.show();
    }

    /** The rewards as plates: experience, coins, items, a random one, a choice. */
    private static void chips(Notice notice, Quest quest) {
        for (RewardDef reward : quest.rewards()) {
            Notice.Chip chip = switch (reward) {
                case XpReward xp when xp.points().isPresent() -> Notice.Chip.xp(String.valueOf(xp.points().get()));
                case XpReward xp when xp.levels().isPresent() -> Notice.Chip.xp(word("rysquests.plural.level", xp.levels().get()));
                case CurrencyReward coins -> Notice.Chip.coins(String.valueOf(coins.amount()));
                case ItemReward item -> Notice.Chip.item(TaskText.stackOf(item.item()), item.count() > 1 ? "×" + item.count() : "");
                case RandomReward random -> Notice.Chip.random();
                case LootTableReward loot -> Notice.Chip.random();
                case ChoiceReward choice -> Notice.Chip.choice(t("rysquests.notice.choice_chip", choice.of().size()));
                default -> null;
            };
            if (chip != null) {
                notice.chip(chip);
            }
        }
    }

    // --- what the book says about it ---

    private static boolean milestone(Quest quest) {
        return quest.options().milestone() || quest.options().tags().contains(MILESTONE_TAG);
    }

    /**
     * The epoch a quest opens, or 0: a quest tagged {@code epoch}, or one in a group of eras ({@code eras}, as in RepYS's map
     * chapter) of a chapter that is itself in no epoch. Its number is one past the latest epoch of the quests it needs.
     */
    static int epochOf(QuestBook book, Quest quest) {
        boolean tagged = quest.options().tags().contains(EPOCH_TAG);
        Chapter own = quest.chapter().map(book.chapters()::get).orElse(null);
        boolean grouped = quest.group().filter(EPOCH_GROUPS::contains).isPresent() && (own == null || own.epoch().isEmpty());
        if (!tagged && !grouped) {
            return 0;
        }
        int latest = 0;
        for (ResourceLocation id : quest.requires()) {
            Quest needed = book.quests().get(id);
            if (needed != null) {
                latest = Math.max(latest, needed.chapter().map(book.chapters()::get).flatMap(Chapter::epoch).orElse(0));
            }
        }
        return latest + 1;
    }

    /** The chapter this quest has just closed: its last main quest (not optional) is done. */
    private static Optional<Chapter> closedChapter(QuestBook book, Quest quest) {
        if (quest.options().optional() || quest.chapter().isEmpty()) {
            return Optional.empty();
        }
        Chapter chapter = book.chapters().get(quest.chapter().get());
        if (chapter == null || !chapter.isStory() || chapter.hidden() || chapter.offMap()) {
            return Optional.empty();
        }
        List<Quest> main = mainQuests(book, chapter.id());
        boolean all = !main.isEmpty() && main.stream().allMatch(q -> ClientProgress.get(q.id()).everDone());
        return all ? Optional.of(chapter) : Optional.empty();
    }

    private static List<Quest> mainQuests(QuestBook book, ResourceLocation chapter) {
        return book.quests().values().stream()
                .filter(q -> q.chapter().filter(chapter::equals).isPresent() && QuestLogic.isLive(q) && !q.options().optional())
                .toList();
    }

    /** The next story chapter in the book's order. */
    private static Optional<Chapter> nextChapter(QuestBook book, Chapter chapter) {
        List<ResourceLocation> order = new ArrayList<>(book.meta().chapterOrder());
        for (ResourceLocation id : book.chapters().keySet()) {
            if (!order.contains(id)) {
                order.add(id);
            }
        }
        boolean after = false;
        for (ResourceLocation id : order) {
            Chapter next = book.chapters().get(id);
            if (after && next != null && next.isStory() && !next.hidden() && !next.offMap()) {
                return Optional.of(next);
            }
            after |= id.equals(chapter.id());
        }
        return Optional.empty();
    }

    private static boolean bookOpen() {
        return Minecraft.getInstance().screen instanceof BookScreen;
    }

    private static ItemStack icon(Quest quest) {
        return quest.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY);
    }

    private static String keyLabel() {
        return QuestKeys.label(QuestKeys.OPEN_BOOK).getString();
    }

    private static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII"};
        return n < r.length ? r[n] : String.valueOf(n);
    }

    private static String t(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    /** "25 опыта", "1 монета": the word forms the book uses. */
    static String word(String key, int count) {
        boolean russian = Minecraft.getInstance().getLanguageManager().getSelected().startsWith("ru");
        int tens = count % 100;
        int units = count % 10;
        String form = !russian ? count == 1 ? "one" : "many" : tens >= 11 && tens <= 14 ? "many" : units == 1 ? "one" : units >= 2 && units <= 4 ? "few" : "many";
        String full = key + "." + form;
        return Language.getInstance().has(full) ? Component.translatable(full, count).getString() : String.valueOf(count);
    }
}
