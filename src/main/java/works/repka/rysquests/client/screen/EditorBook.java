package works.repka.rysquests.client.screen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientDrafts;
import works.repka.rysquests.client.ClientLayout;
import works.repka.rysquests.client.ClientMessages;
import works.repka.rysquests.data.Achievement;
import works.repka.rysquests.data.BookHash;
import works.repka.rysquests.data.BookMeta;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.Guide;
import works.repka.rysquests.data.MapForm;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.SectionDef;
import works.repka.rysquests.data.ShopOffer;
import works.repka.rysquests.data.TaskDef;
import works.repka.rysquests.editor.BookDraft;
import works.repka.rysquests.editor.BookDraft.Work;
import works.repka.rysquests.editor.QuestEdits;
import works.repka.rysquests.network.BookEditsSavePayload;
import works.repka.rysquests.network.EditorResultPayload;
import works.repka.rysquests.network.EditorSavePayload;
import works.repka.rysquests.network.QuestEditorSavePayload;

/**
 * The editor's draft on this client (plan, section 6): one for the book, whatever editor page is open. Every edit lands in the draft
 * at once and on disk ({@link ClientDrafts#saveBook}), with a line in words for the question of picture 33b; "Сохранить · N" sends
 * the draft part by part and checks each chapter's version on the server, so that two operators never save over each other.
 *
 * <p>Pages read the book through {@link #data()} (lang keys, as it will be saved) or {@link #view()} (the typed strings in place of
 * their keys, for drawing), and change it through {@link #edit}.
 */
final class EditorBook {
    private static final Logger LOGGER = LoggerFactory.getLogger(EditorBook.class);
    private static final int UNDO_LIMIT = 200;
    private static final long TYPING_PAUSE_MS = 1500;
    /** Between the scope of a log line and its words, see {@link #edit}. */
    private static final char SCOPE = '\t';

    @Nullable
    private static EditorBook instance;
    /** The editor is the open mode of the book; it stays on between openings of the book. */
    static boolean active;

    private final Path folder;
    private final Deque<BookDraft> undo = new ArrayDeque<>();
    private BookDraft draft;
    /** The draft came from disk and the author has not said yet whether to keep it. */
    private boolean restored;
    @Nullable
    private String typingKey;
    private long typedAt;
    @Nullable
    private QuestBook cachedBase;
    @Nullable
    private BookDraft cachedDraft;
    @Nullable
    private QuestBook cachedData;
    @Nullable
    private QuestBook cachedView;
    // --- saving ---
    private final List<Part> queue = new ArrayList<>();
    @Nullable
    private Part sending;
    private boolean bakeAfter;
    @Nullable
    private Runnable afterSave;

    /** One payload of a save, and the scope of the draft that leaves it once the server took it. */
    private record Part(String scope, String title, java.util.function.Function<Boolean, net.minecraft.network.protocol.common.custom.CustomPacketPayload> payload) {
    }

    private EditorBook(Path folder) {
        this.folder = folder;
        Optional<BookDraft> fromDisk = ClientDrafts.loadBook();
        draft = fromDisk.filter(found -> !found.isEmpty()).orElseGet(() -> BookDraft.empty(language()));
        restored = !draft.isEmpty();
    }

    /** The draft of the world or server this client is in. */
    static EditorBook get() {
        Path folder = ClientDrafts.folder();
        if (instance == null || !instance.folder.equals(folder)) {
            instance = new EditorBook(folder);
        }
        return instance;
    }

    /** Forgets the draft in memory, as a restart of the game does: the next {@link #get} reads it from disk again. For tests. */
    static void forgetForTest() {
        instance = null;
    }

    static String language() {
        return Minecraft.getInstance().getLanguageManager().getSelected();
    }

    static DynamicOps<JsonElement> ops() {
        return RegistryOps.create(JsonOps.INSTANCE, Minecraft.getInstance().level.registryAccess());
    }

    static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    // --- reading ---

    BookDraft draft() {
        return draft;
    }

    QuestBook base() {
        return ClientBook.current();
    }

    /** The book as it will be saved: lang keys. */
    QuestBook data() {
        refresh();
        return cachedData;
    }

    /** The book for drawing: the strings typed in the draft stand in place of their keys. */
    QuestBook view() {
        refresh();
        return cachedView;
    }

    private void refresh() {
        QuestBook base = base();
        if (cachedBase == base && cachedDraft == draft && cachedData != null) {
            return;
        }
        cachedBase = base;
        cachedDraft = draft;
        cachedData = draft.apply(base);
        cachedView = literal(cachedData);
    }

    private QuestBook literal(QuestBook book) {
        List<Chapter> chapters = book.chapters().values().stream().map(chapter -> {
            Chapter out = chapter.withTitle(text(chapter.title()));
            return chapter.subtitle().map(this::text).map(text -> new Chapter(out.id(), out.title(), Optional.of(text), out.icon(), out.epoch(), out.defaultView(),
                    out.groups(), out.requires(), out.hidden(), out.section(), out.links(), out.offMap(), out.preComplete())).orElse(out);
        }).toList();
        List<Quest> quests = book.quests().values().stream().map(quest -> QuestEdits.withTexts(quest, text(quest.title()),
                quest.subtitle().map(key -> text(key)), quest.text().map(key -> text(key)))).toList();
        List<ShopOffer> offers = book.offers().values().stream().map(offer -> new ShopOffer(offer.id(), offer.title().map(this::text), offer.icon(), offer.category(),
                offer.cost(), offer.gives(), offer.limit(), offer.stage(), offer.stageTitle(), offer.quest(), offer.lockText().map(this::text), offer.delivery())).toList();
        List<Achievement> achievements = book.achievements().values().stream().map(a -> new Achievement(a.id(), a.title().map(key -> text(key)),
                a.text().map(key -> text(key)), a.hint(), a.icon(), a.category(), a.hidden(), a.tiers(), a.archived())).toList();
        BookMeta meta = book.meta().title().map(this::text).map(text -> book.meta().withTitleAndIcon(Optional.of(text), book.meta().icon())).orElse(book.meta());
        return QuestBook.create(meta, chapters, quests, List.copyOf(book.guides().values()), List.copyOf(book.sections().values()), book.hash())
                .withAssets(book.assets()).withOffers(offers).withAchievements(achievements).withStructures(List.copyOf(book.structures().values())).withLayout(book.layout());
    }

    /** What a lang key says now: the string typed in the draft, else the game's language, else the key itself (a literal title). */
    String text(String key) {
        return draft.shown(key).orElseGet(() -> Language.getInstance().getOrDefault(key));
    }

    /** A chapter as the draft holds it, or as the book has it when it was not touched yet. Null when there is no such chapter. */
    @Nullable
    Work work(ResourceLocation chapter) {
        Work own = draft.chapters().get(chapter);
        if (own != null) {
            return own;
        }
        QuestBook base = base();
        Chapter found = base.chapters().get(chapter);
        if (found == null) {
            return null;
        }
        List<Quest> quests = base.quests().values().stream().filter(quest -> quest.chapter().filter(chapter::equals).isPresent()).toList();
        return new Work(found, quests, BookHash.chapterVersion(base, chapter, ops()), false);
    }

    /** The quest as the draft has it. */
    @Nullable
    Quest quest(ResourceLocation id) {
        return data().quests().get(id);
    }

    /** The N of "Сохранить · N". */
    int unsaved() {
        return draft.count();
    }

    /** The edits in words, newest last, for the question of picture 33b. */
    List<String> lines() {
        return draft.log().stream().map(line -> line.substring(line.indexOf(SCOPE) + 1)).toList();
    }

    boolean restoredFromDisk() {
        return restored;
    }

    boolean saving() {
        return sending != null;
    }

    boolean canUndo() {
        return !undo.isEmpty() && sending == null;
    }

    // --- editing ---

    /**
     * One edit: the draft before it goes on the undo stack, the change and a line in words land in the draft, and the draft is written
     * to disk.
     *
     * @param scope what the edit belongs to, so that a refused part can be dropped with its lines: {@code chapter:<id>}, {@code meta},
     *              {@code offer:<id>}, {@code achievement:<id>}, {@code guide:<id>}, {@code section:<id>}
     */
    void edit(String scope, String line, UnaryOperator<BookDraft> change) {
        if (sending != null) {
            return;
        }
        restored = false;
        typingKey = null;
        push();
        draft = change.apply(draft).logged(scope + SCOPE + line);
        write();
    }

    /**
     * Typing into a field: the first change of a field is an edit with its line; the following keystrokes join it, until another field
     * is touched or the author paused, so that one word typed is one edit and one step of undo.
     */
    void type(String field, String scope, String line, UnaryOperator<BookDraft> change) {
        if (sending != null) {
            return;
        }
        long now = Util.getMillis();
        if (field.equals(typingKey) && now - typedAt < TYPING_PAUSE_MS * 20) {
            draft = change.apply(draft);
            typedAt = now;
            write();
            return;
        }
        edit(scope, line, change);
        typingKey = field;
        typedAt = now;
    }

    /** The next keystroke starts a new edit even in the same field. */
    void endTyping() {
        typingKey = null;
    }

    private void push() {
        undo.push(draft);
        if (undo.size() > UNDO_LIMIT) {
            undo.removeLast();
        }
    }

    void undo() {
        if (canUndo()) {
            draft = undo.pop();
            typingKey = null;
            write();
        }
    }

    /** "Не сохранять": the draft is thrown away, its file kept beside as {@code .old}. */
    void discard() {
        push();
        draft = BookDraft.empty(language());
        restored = false;
        try {
            ClientDrafts.discardBook();
        } catch (IOException e) {
            LOGGER.warn("Could not put the editor's draft aside", e);
        }
    }

    /** The draft from disk is kept: the question of the restore is answered. */
    void keepRestored() {
        restored = false;
    }

    private void write() {
        try {
            if (draft.isEmpty()) {
                ClientDrafts.discardBook();
            } else {
                ClientDrafts.saveBook(draft);
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not write the editor's draft", e);
        }
    }

    // --- edits the pages share ---

    static String chapterScope(ResourceLocation chapter) {
        return "chapter:" + chapter;
    }

    /** Changes a chapter, which the draft takes whole the first time. */
    void editChapter(ResourceLocation chapter, String line, UnaryOperator<Work> change) {
        Work work = work(chapter);
        if (work != null) {
            edit(chapterScope(chapter), line, d -> d.withWork(change.apply(work)));
        }
    }

    /** Changes a quest in its chapter. */
    void editQuest(ResourceLocation id, String line, UnaryOperator<Quest> change) {
        Quest quest = quest(id);
        if (quest == null || quest.chapter().isEmpty()) {
            return;
        }
        editChapter(quest.chapter().get(), line, work -> work.withQuest(change.apply(work.questMap().getOrDefault(id, quest))));
    }

    /** Types a string of a quest or a chapter; the key is made when the text had none. */
    void typeText(String field, String scope, String line, String key, String value) {
        type(field, scope, line, d -> d.withText(key, value));
    }

    /** Whether a stored string is a lang key rather than the text itself, as books written by hand and imports may have. */
    static boolean isKey(String text) {
        return !text.isEmpty() && (Language.getInstance().has(text) || text.matches("[a-z0-9_.-]+") && text.contains("."));
    }

    /**
     * Types the title, the subtitle or the text of a quest. A quest whose string was the text itself gets a key of its own, so that the
     * save writes the string into the lang file.
     *
     * @param which {@code title}, {@code subtitle} or {@code text}
     */
    void typeQuestText(ResourceLocation id, String which, String value) {
        Quest quest = quest(id);
        if (quest == null || quest.chapter().isEmpty()) {
            return;
        }
        ResourceLocation chapter = quest.chapter().get();
        Optional<String> current = switch (which) {
            case "title" -> Optional.of(quest.title());
            case "subtitle" -> quest.subtitle();
            default -> quest.text();
        };
        String key = current.filter(EditorBook::isKey).orElse(switch (which) {
            case "title" -> QuestEdits.titleKey(id);
            case "subtitle" -> QuestEdits.subtitleKey(id);
            default -> QuestEdits.textKey(id);
        });
        Quest changed = QuestEdits.withTexts(quest, which.equals("title") ? key : quest.title(), which.equals("subtitle") ? Optional.of(key) : quest.subtitle(),
                which.equals("text") ? Optional.of(key) : quest.text());
        String line = tr("rysquests.ed.log." + which, text(quest.title()));
        type("quest-" + which + ":" + id, chapterScope(chapter), line, d -> {
            Work work = d.chapters().containsKey(chapter) ? d.chapters().get(chapter) : work(chapter);
            return work == null ? d : d.withWork(work.withQuest(changed)).withText(key, value);
        });
    }

    /** Types the title or the subtitle of a chapter. @param which {@code title} or {@code subtitle} */
    void typeChapterText(ResourceLocation chapter, String which, String value) {
        Work work = work(chapter);
        if (work == null) {
            return;
        }
        Chapter c = work.chapter();
        Optional<String> current = which.equals("title") ? Optional.of(c.title()) : c.subtitle();
        String dotted = chapter.getNamespace() + "." + chapter.getPath().replace('/', '.');
        String key = current.filter(EditorBook::isKey).orElse(which.equals("title") ? QuestEdits.chapterTitleKey(chapter) : "chapter." + dotted + ".subtitle");
        Chapter changed = which.equals("title") ? c.withTitle(key)
                : new Chapter(c.id(), c.title(), Optional.of(key), c.icon(), c.epoch(), c.defaultView(), c.groups(), c.requires(), c.hidden(), c.section(), c.links(), c.offMap(), c.preComplete());
        String line = tr("rysquests.ed.log.chapter_" + which, text(c.title()));
        type("chapter-" + which + ":" + chapter, chapterScope(chapter), line, d -> {
            Work own = d.chapters().containsKey(chapter) ? d.chapters().get(chapter) : work(chapter);
            return own == null ? d : d.withWork(own.withChapter(changed)).withText(key, value);
        });
    }

    /** A new quest, its title typed already. @return its id */
    ResourceLocation addQuest(ResourceLocation chapter, String title, QuestEdits.Kind kind, List<ResourceLocation> requires, @Nullable ResourceLocation after) {
        Set<ResourceLocation> taken = new java.util.HashSet<>(data().quests().keySet());
        ResourceLocation id = QuestEdits.freshQuest(chapter, taken);
        Quest quest = QuestEdits.newQuest(id, chapter, kind, requires);
        Work work = work(chapter);
        if (work == null) {
            return id;
        }
        Work next = after == null ? work.withQuest(quest) : work.withQuestAfter(quest, after);
        edit(chapterScope(chapter), tr("rysquests.ed.log.new_quest", title), d -> d.withWork(next).withText(quest.title(), title));
        return id;
    }

    /** A new chapter at the end of the book. @return its id */
    ResourceLocation addChapter(String title, Optional<Integer> epoch) {
        QuestBook book = data();
        String namespace = namespace(book);
        ResourceLocation id = QuestEdits.freshChapter(namespace, new java.util.HashSet<>(book.chapters().keySet()));
        Chapter chapter = QuestEdits.newChapter(id).withEpoch(epoch);
        edit(chapterScope(id), tr("rysquests.ed.log.new_chapter", title), d -> d.withWork(new Work(chapter, List.of(), "", true)).withText(chapter.title(), title));
        return id;
    }

    /** The namespace new chapters get: the book's own. */
    static String namespace(QuestBook book) {
        return book.chapters().values().stream().filter(Chapter::isStory).map(chapter -> chapter.id().getNamespace()).findFirst()
                .orElseGet(() -> book.chapters().keySet().stream().map(ResourceLocation::getNamespace).findFirst().orElse("pack"));
    }

    void setMeta(String line, UnaryOperator<BookMeta> change) {
        BookMeta meta = draft.meta().orElse(base().meta());
        edit("meta", line, d -> d.withMeta(change.apply(meta)));
    }

    void setMapForm(MapForm form) {
        setMeta(tr("rysquests.ed.log.map_form", tr("rysquests.ed.form." + form.getSerializedName())), meta -> meta.withMapForm(form));
    }

    void putOffer(ShopOffer offer, String line) {
        edit("offer:" + offer.id(), line, d -> d.withOffer(offer));
    }

    void removeOffer(ResourceLocation id, String line) {
        boolean saved = base().offers().containsKey(id);
        edit("offer:" + id, line, d -> d.withoutOffer(id, saved));
    }

    /** The task an achievement's tiers count: from the draft, else from the first tier quest of the book. */
    Optional<TaskDef> achievementTask(ResourceLocation id) {
        BookDraft.AchievementWork own = draft.achievements().get(id);
        if (own != null) {
            return own.task();
        }
        Achievement achievement = base().achievements().get(id);
        if (achievement == null || achievement.tiers().isEmpty()) {
            return Optional.empty();
        }
        Quest tier = base().quests().get(achievement.questId(achievement.tiers().getFirst()));
        return tier == null || tier.tasks().isEmpty() ? Optional.empty() : Optional.of(tier.tasks().getFirst());
    }

    void putAchievement(Achievement achievement, Optional<TaskDef> task, String line) {
        edit("achievement:" + achievement.id(), line, d -> d.withAchievement(new BookDraft.AchievementWork(achievement, task)));
    }

    void putGuide(Guide guide, String line) {
        edit("guide:" + guide.id(), line, d -> d.withGuide(guide));
    }

    void putSection(SectionDef section, String line) {
        edit("section:" + section.id(), line, d -> d.withSection(section));
    }

    // --- saving ---

    /**
     * "Сохранить · N": the draft goes to the server part by part, one chapter, guide or tab at a time, then the rest of the book in one;
     * each part waits for the answer before the next. When all are in, the map is laid out once and saved for players.
     *
     * @param done runs once everything is saved
     */
    void save(@Nullable BookScreen screen, @Nullable Runnable done) {
        if (sending != null || draft.isEmpty()) {
            return;
        }
        afterSave = done;
        queue.clear();
        String language = language();
        QuestBook before = base();
        for (Work work : draft.chapters().values()) {
            ResourceLocation id = work.chapter().id();
            Chapter old = before.chapters().get(id);
            boolean chapterChanged = work.created() || !work.chapter().equals(old) || draft.shown(work.chapter().title()).isPresent()
                    || work.chapter().subtitle().flatMap(draft::shown).isPresent();
            List<Quest> quests = work.quests().stream().filter(quest -> !quest.equals(before.quests().get(quest.id())) || draft.shown(quest.title()).isPresent()
                    || quest.subtitle().flatMap(draft::shown).isPresent() || quest.text().flatMap(draft::shown).isPresent()).toList();
            Map<String, String> texts = draft.textsOf(BookDraft.keysOf(work));
            queue.add(new Part(chapterScope(id), text(work.chapter().title()),
                    force -> new QuestEditorSavePayload(work.chapter(), chapterChanged, quests, language, texts, work.baseVersion(), force)));
        }
        for (Guide guide : draft.guides().values()) {
            Set<String> keys = new java.util.HashSet<>();
            keys.add(guide.title());
            guide.category().ifPresent(keys::add);
            guide.pages().forEach(page -> {
                keys.add(page.title());
                keys.add(page.text());
            });
            Map<String, String> texts = draft.textsOf(keys);
            queue.add(new Part("guide:" + guide.id(), text(guide.title()),
                    force -> new EditorSavePayload(Optional.of(guide), Optional.empty(), language, texts, Map.of(), List.of())));
        }
        for (SectionDef section : draft.sections().values()) {
            Map<String, String> texts = draft.textsOf(section.textKeys());
            queue.add(new Part("section:" + section.id(), section.id().toString(),
                    force -> new EditorSavePayload(Optional.empty(), Optional.of(section), language, texts, Map.of(), List.of())));
        }
        if (draft.meta().isPresent() || !draft.offers().isEmpty() || !draft.removed().isEmpty() || !draft.achievements().isEmpty()) {
            List<String> achievements = new ArrayList<>();
            Set<String> keys = new java.util.HashSet<>();
            draft.meta().flatMap(BookMeta::title).ifPresent(keys::add);
            for (ShopOffer offer : draft.offers().values()) {
                keys.addAll(offer.textKeys());
            }
            for (BookDraft.AchievementWork work : draft.achievements().values()) {
                JsonObject json = Achievement.CODEC.encodeStart(ops(), work.achievement()).getOrThrow().getAsJsonObject();
                work.task().ifPresent(task -> {
                    JsonObject taskJson = TaskDef.CODEC.encodeStart(ops(), task).getOrThrow().getAsJsonObject();
                    taskJson.remove("count");
                    json.add("task", taskJson);
                });
                achievements.add(json.toString());
                work.achievement().title().ifPresent(keys::add);
                work.achievement().text().ifPresent(keys::add);
            }
            Map<String, String> texts = draft.textsOf(keys);
            BookEditsSavePayload payload = new BookEditsSavePayload(draft.meta(), List.copyOf(draft.offers().values()), draft.removed(), achievements, language, texts);
            // the book's settings go first: a new book is written closed to players before its first chapter lands
            queue.addFirst(new Part("book", tr("rysquests.ed.save.book_part"), force -> payload));
        }
        bakeAfter = true;
        next(screen);
    }

    private void next(@Nullable BookScreen screen) {
        if (queue.isEmpty()) {
            sending = null;
            finish();
            return;
        }
        sending = queue.removeFirst();
        PacketDistributor.sendToServer(sending.payload().apply(false));
    }

    private void finish() {
        if (draft.chapters().isEmpty() && draft.guides().isEmpty() && draft.sections().isEmpty() && draft.meta().isEmpty() && draft.offers().isEmpty()
                && draft.removed().isEmpty() && draft.achievements().isEmpty()) {
            draft = BookDraft.empty(language());
        }
        undo.clear();
        typingKey = null;
        write();
        ClientMessages.show("rysquests.ed.saved", List.of(), true);
        if (bakeAfter) {
            bakeAfter = false;
            // The book that the save made arrives with the answer; laid out on the next tick, so that it is the one baked.
            Minecraft.getInstance().execute(ClientLayout::bakeAndSave);
        }
        if (afterSave != null) {
            Runnable done = afterSave;
            afterSave = null;
            done.run();
        }
    }

    /** @return true when this answer was for a part of the editor's save */
    boolean onResult(@Nullable BookScreen screen, boolean ok, String reason, List<String> args) {
        Part part = sending;
        if (part == null) {
            return false;
        }
        sending = null;
        if (ok) {
            // What the server took is in the book now; the undo steps before it would bring an old version back.
            draft = without(draft, part.scope());
            undo.clear();
            write();
            next(screen);
            return true;
        }
        if (reason.startsWith(EditorResultPayload.CONFLICT) && screen != null) {
            askConflict(screen, part, reason, args);
        } else {
            queue.clear();
            afterSave = null;
            ClientMessages.show("rysquests.ed.save.failed", List.of(part.title(), tr(reason)), false);
        }
        return true;
    }

    /** The part the server refused because another operator saved first, while the question is open. */
    @Nullable
    private Part conflicted;

    /** Another operator saved the chapter first (plan, section 6): keep editing, take theirs, or save over theirs. */
    private void askConflict(BookScreen screen, Part part, String reason, List<String> args) {
        conflicted = part;
        String who = reason.equals(EditorResultPayload.CONFLICT) && !args.isEmpty() ? tr("rysquests.ed.conflict.by", args.getFirst()) : tr("rysquests.ed.conflict.files");
        screen.ask(new works.repka.ryscore.ui.Kit.Dialog(tr("rysquests.ed.conflict.title", part.title()))
                .text(who)
                .text(tr("rysquests.ed.conflict.text"))
                .no(tr("rysquests.ed.conflict.stay"), screen.answer(this::stayOnConflict))
                .button(tr("rysquests.ed.conflict.theirs"), screen.answer(() -> takeTheirs(screen)))
                .yes(tr("rysquests.ed.conflict.over"), screen.answer(this::saveOver))
                .width(270));
    }

    /** "Остаться": the save stops, the draft stays as it is. */
    void stayOnConflict() {
        conflicted = null;
        queue.clear();
        afterSave = null;
    }

    /** "Взять их версию": this part of the draft goes, the rest of the save goes on. */
    void takeTheirs(@Nullable BookScreen screen) {
        if (conflicted == null) {
            return;
        }
        draft = without(draft, conflicted.scope());
        conflicted = null;
        undo.clear();
        write();
        next(screen);
    }

    /** "Сохранить поверх": the part goes again with force, after the author was told whose changes it replaces. */
    void saveOver() {
        if (conflicted == null) {
            return;
        }
        sending = conflicted;
        conflicted = null;
        PacketDistributor.sendToServer(sending.payload().apply(true));
    }

    boolean conflictForTest() {
        return conflicted != null;
    }

    /** The draft without one part, its lines and its strings: saved, or given up for the other operator's version. */
    static BookDraft without(BookDraft draft, String scope) {
        List<String> log = draft.log().stream().filter(line -> !line.startsWith(scope + SCOPE) && !(scope.equals("book") && bookScope(line))).toList();
        Map<ResourceLocation, Work> chapters = new LinkedHashMap<>(draft.chapters());
        Map<String, String> texts = new LinkedHashMap<>(draft.texts());
        Map<ResourceLocation, Guide> guides = new LinkedHashMap<>(draft.guides());
        Map<ResourceLocation, SectionDef> sections = new LinkedHashMap<>(draft.sections());
        Optional<BookMeta> meta = draft.meta();
        Map<ResourceLocation, ShopOffer> offers = draft.offers();
        List<ResourceLocation> removed = draft.removed();
        Map<ResourceLocation, BookDraft.AchievementWork> achievements = draft.achievements();
        if (scope.startsWith("chapter:")) {
            Work work = chapters.remove(ResourceLocation.parse(scope.substring("chapter:".length())));
            if (work != null) {
                BookDraft.keysOf(work).forEach(texts::remove);
            }
        } else if (scope.startsWith("guide:")) {
            guides.remove(ResourceLocation.parse(scope.substring("guide:".length())));
        } else if (scope.startsWith("section:")) {
            sections.remove(ResourceLocation.parse(scope.substring("section:".length())));
        } else if (scope.equals("book")) {
            meta = Optional.empty();
            offers = Map.of();
            removed = List.of();
            achievements = Map.of();
        }
        return new BookDraft(chapters, meta, offers, removed, achievements, guides, sections, texts, log, draft.language());
    }

    private static boolean bookScope(String line) {
        return line.startsWith("meta" + SCOPE) || line.startsWith("offer:") || line.startsWith("achievement:");
    }
}
