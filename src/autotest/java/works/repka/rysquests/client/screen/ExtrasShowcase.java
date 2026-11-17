package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.client.ClientAchievements;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.client.ClientShop;
import works.repka.rysquests.data.Achievement;
import works.repka.rysquests.data.BookMeta;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.Guide;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.RewardDef;
import works.repka.rysquests.data.ShopOffer;
import works.repka.rysquests.data.TeamMode;
import works.repka.rysquests.network.AchievementStatePayload;
import works.repka.rysquests.network.ShopStatePayload;
import works.repka.rysquests.progress.ProgressStore;
import works.repka.rysquests.progress.QuestProgress;
import works.repka.rysquests.reward.CurrencyReward;
import works.repka.rysquests.reward.TitleReward;
import works.repka.rysquests.reward.XpReward;

/**
 * The shop, the achievements, the guides and the search with the words and numbers of the mockups (pictures 23 to 31b), for the
 * autotest's screenshots: the example book with offers, achievements and guides of its own, and the money, levels and read pages the
 * server would send. Part of the autotest only, never of the mod.
 */
public final class ExtrasShowcase {
    private static final String NS = "showcase";

    private ExtrasShowcase() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NS, path);
    }

    private static ResourceLocation mc(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }

    private static ShopOffer buy(String path, String title, String item, int count, int price, String category, ShopOffer.Limit limit, String stageTitle) {
        return new ShopOffer(id(path), Optional.ofNullable(title), Optional.empty(), Optional.of(category),
                List.of(new ShopOffer.Entry(ShopOffer.Kind.CURRENCY, "coin", price)), List.of(new ShopOffer.Entry(ShopOffer.Kind.ITEM, "minecraft:" + item, count)),
                Optional.ofNullable(limit), stageTitle == null ? Optional.empty() : Optional.of("showcase:closed"), Optional.ofNullable(stageTitle), Optional.empty());
    }

    private static ShopOffer sell(String path, String item, int count, int price, String category) {
        return new ShopOffer(id(path), Optional.empty(), Optional.empty(), Optional.of(category),
                List.of(new ShopOffer.Entry(ShopOffer.Kind.ITEM, "minecraft:" + item, count)), List.of(new ShopOffer.Entry(ShopOffer.Kind.CURRENCY, "coin", price)),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    static List<ShopOffer> offers() {
        String tokens = "Жетоны";
        String e1 = "I Механика";
        String e2 = "II Индустрия";
        String e3 = "III Машины";
        ShopOffer.Limit daily = new ShopOffer.Limit(ShopOffer.Per.PLAYER, 3, ShopOffer.Reset.DAILY);
        ShopOffer.Limit weekly = new ShopOffer.Limit(ShopOffer.Per.PLAYER, 2, ShopOffer.Reset.WEEKLY);
        return List.of(
                buy("pickaxe", "Деревянная кирка", "wooden_pickaxe", 1, 4, e1, null, null),
                buy("token", "Жетон инженера", "gold_nugget", 1, 1, tokens, daily, null),
                buy("xp", "Пузырьки опыта", "experience_bottle", 24, 4, tokens, null, null),
                buy("copper", "Ящик меди", "raw_copper_block", 8, 6, e1, null, null),
                buy("iron", "Ящик железа", "raw_iron_block", 8, 6, e1, null, null),
                buy("zinc", "Ящик цинка", "cobblestone", 8, 8, e2, null, null),
                buy("ration", "Сухпаёк", "bread", 64, 10, e2, weekly, null),
                buy("blaze", "Пироги ифрита", "pumpkin_pie", 4, 10, e2, null, null),
                buy("coke", "Коксовый уголь", "coal", 64, 6, e1, null, null),
                buy("gold", "Ящик золота", "raw_gold_block", 6, 8, e2, null, null),
                buy("steel", "Стальные слитки", "iron_ingot", 16, 10, e2, null, null),
                buy("ender", "Набор Края", "ender_pearl", 32, 15, e3, null, "эпоха III"),
                buy("quartz", "Истинный кварц", "quartz", 32, 8, "IV Энергия и разум", null, null),
                buy("star", "Звезда Незера", "nether_star", 1, 2400, "IX Творение", null, null),
                sell("andesite", "andesite", 32, 1, e1),
                sell("brass", "copper_ingot", 32, 1, e1),
                sell("coke_sell", "coal", 64, 1, e1),
                sell("steel_sell", "iron_ingot", 32, 2, e2));
    }

    private static Achievement achievement(String path, String title, String text, String item, String category, int[] amounts, String titleReward, boolean hidden) {
        List<Achievement.Tier> tiers = new ArrayList<>();
        String[] names = {"t1", "t2", "t3", "t4"};
        for (int i = 0; i < amounts.length; i++) {
            List<RewardDef> rewards = new ArrayList<>();
            rewards.add(new CurrencyReward("coin", 2 + i * 5, TeamMode.EACH));
            rewards.add(new XpReward(Optional.empty(), Optional.of(100 + i * 150), TeamMode.EACH));
            if (titleReward != null && i == amounts.length - 1) {
                rewards.add(new TitleReward(titleReward, TeamMode.EACH));
            }
            tiers.add(new Achievement.Tier(names[i], amounts[i], rewards));
        }
        return new Achievement(id(path), Optional.of(title), Optional.of(text), Optional.empty(), Optional.of(mc(item)), Optional.of(category), hidden, tiers, false);
    }

    static List<Achievement> achievements() {
        return List.of(
                achievement("miner", "Шахтёр", "Добывай камень своими руками. Считается весь камень, добытый тобой с начала игры.", "diamond_pickaxe", "Жизнь", new int[] {1000, 10000, 50000}, "Шахтёр", false),
                achievement("mechanism", "Латунный механизм", "Держи в инвентаре много штук сразу. Ничего не забирается.", "clock", "Массовое производство", new int[] {100, 500, 1000}, null, false),
                achievement("wanderer", "Странник", "Иди пешком.", "leather_boots", "Путешествия", new int[] {5000, 20000, 50000}, null, false),
                achievement("wither", "Иссушитель", "Победи этого босса.", "nether_star", "Боссы", new int[] {1}, null, false),
                achievement("hunter", "Охотник", "Побеждай любых мобов.", "iron_sword", "Бой", new int[] {100, 1000, 5000}, null, false),
                achievement("coil", "Медная катушка", "Держи в инвентаре много штук сразу. Ничего не забирается.", "copper_ingot", "Массовое производство", new int[] {16, 64, 256}, null, false),
                achievement("storekeeper", "Кладовщик", "Открывай сундуки. Да, считается каждый раз.", "chest", "Жизнь", new int[] {1000, 5000, 25000}, "Кладовщик", false),
                achievement("veteran", "Ветеран", "Время в этом мире.", "totem_of_undying", "Жизнь", new int[] {5, 12, 24, 100}, null, false),
                achievement("sweet", "Сладкоежка", "Съешь сто тортов.", "cake", "Секреты", new int[] {100}, null, true));
    }

    private static final Map<String, Integer> VALUES = Map.of("miner", 4400, "mechanism", 312, "wanderer", 18400, "wither", 1, "hunter", 132,
            "coil", 41, "storekeeper", 5200, "veteran", 13, "sweet", 0);

    private static Guide guide(String path, String title, String item, String category, int pages, String stage) {
        List<Guide.Page> list = new ArrayList<>();
        for (int i = 0; i < pages; i++) {
            list.add(new Guide.Page("p" + i, "Страница " + (i + 1), "Текст страницы."));
        }
        return new Guide(id(path), title, Optional.of(mc(item)), Optional.of(category), Optional.ofNullable(stage), Optional.empty(), list);
    }

    static List<Guide> guides() {
        List<Guide.Page> create = new ArrayList<>();
        String[] titles = {"С чего начать", "Вращение и нагрузка", "Пресс, чаша и смешиватель", "Нагрев и латунь", "Последовательная сборка",
            "Конвейеры и логистика", "Контрапции и жидкости", "Что дальше", "Первые шаги", "Обработка и нагрев", "Логистика", "Контрапции", "Жидкости"};
        for (int i = 0; i < titles.length; i++) {
            String text = i == 1
                    ? "У сети есть **скорость** и **нагрузка**. Источники дают нагрузку, машины её тратят. Вдвое выше скорость означает вдвое больше нагрузки от машины.\n"
                    + "!!Если машины просят больше, чем дают источники, встаёт вся сеть.!!\n"
                    + "**Посмотри в движении.**\n"
                    + "[[ponder:minecraft:water_bucket|Водяное колесо: первый источник]]\n"
                    + "[[ponder:minecraft:white_wool|Ветряная мельница: больше нагрузки]]\n"
                    + "Большая шестерня, которая крутит малую, удваивает скорость. Смешивателю нужно не меньше 30 об/мин.\n"
                    + "[[recipe:minecraft:furnace]]\n"
                    + "Квест про первые инструменты: [[quest:example:start/first_tools]]."
                    : "Текст страницы «" + titles[i] + "».";
            create.add(new Guide.Page("p" + i, titles[i], text));
        }
        List<Guide> out = new ArrayList<>();
        out.add(guide("book", "Как устроена книга", "writable_book", "Книга", 5, null));
        out.add(new Guide(id("create"), "Create", Optional.of(mc("crafting_table")), Optional.of("Техника"), Optional.empty(), Optional.empty(), create));
        out.add(guide("ie", "Immersive Engineering", "iron_axe", "Техника", 11, null));
        out.add(guide("tfmg", "TFMG и Diesel Generators", "blast_furnace", "Техника", 8, "showcase:open"));
        out.add(guide("energy", "Энергия по эпохам", "orange_wool", "Энергия", 10, "showcase:open"));
        out.add(guide("comfort", "Удобство и хранение", "barrel", "Хранение и логистика", 11, null));
        out.add(guide("mekanism", "Mekanism", "iron_block", "Техника", 19, null));
        out.add(guide("enderio", "Ender IO", "furnace", "Техника", 8, null));
        out.add(guide("ae2", "Applied Energistics 2", "glass", "Хранение и логистика", 16, null));
        out.add(guide("powah", "Powah и Flux Networks", "obsidian", "Энергия", 13, null));
        out.add(guide("botania", "Botania", "poppy", "Магия", 7, null));
        out.add(guide("ars", "Ars Nouveau", "gold_block", "Магия", 8, null));
        out.add(guide("bees", "Пчёлы и деревья", "beehive", "Природа", 7, null));
        out.add(guide("mystical", "Mystical Agriculture", "wheat_seeds", "Природа", 7, null));
        out.add(guide("oritech", "Oritech", "oak_planks", "Техника", 12, "showcase:locked"));
        out.add(guide("draconic", "Draconic Evolution", "dragon_egg", "Энергия", 10, null));
        return out;
    }

    /** Puts the showcase book in place of the example book, with the money, levels, pages and titles the server would send. */
    public static void install() {
        QuestBook book = ClientBook.current();
        Map<ResourceLocation, Chapter> chapters = new LinkedHashMap<>();
        int epoch = 1;
        for (Chapter chapter : book.chapters().values()) {
            chapters.put(chapter.id(), chapter.isStory() ? chapter.withEpoch(Optional.of(epoch++)) : chapter);
        }
        Map<ResourceLocation, Guide> guides = new LinkedHashMap<>();
        guides().forEach(guide -> guides.put(guide.id(), guide));
        BookMeta meta = book.meta().withEpochs(List.of(new BookMeta.Epoch("Механика", false), new BookMeta.Epoch("Индустрия", false)));
        QuestBook shown = new QuestBook(meta, chapters, book.quests(), guides, book.sections(), book.unlocks(), book.byItem(), book.hash() + "-showcase",
                book.assets(), Map.of(), Map.of(), book.structures(), book.layoutHash(), book.layout()).withOffers(offers()).withAchievements(achievements());
        ClientBook.overrideForTest(shown, true);
        long now = System.currentTimeMillis();
        long minute = 60_000L;
        long day = 24 * 60 * minute;
        List<ProgressStore.ShopLine> history = List.of(
                new ProgressStore.ShopLine(now - 5 * minute, 1, "coin", "example:start/first_furnace", 1, false),
                new ProgressStore.ShopLine(now - 26 * minute, -6, "coin", id("copper").toString(), 1, false),
                new ProgressStore.ShopLine(now - 54 * minute, 20, "coin", "example:power/electric_mechanism", 1, true),
                new ProgressStore.ShopLine(now - 101 * minute, -20, "coin", id("ration").toString(), 2, true),
                new ProgressStore.ShopLine(now - day, 3, "coin", id("brass").toString(), 3, false),
                new ProgressStore.ShopLine(now - day - 34 * minute, -1, "coin", id("token").toString(), 1, false),
                new ProgressStore.ShopLine(now - day - 132 * minute, 2, "coin", id("miner").withPath("_achievements/miner/t1").toString(), 1, false),
                new ProgressStore.ShopLine(now - day - 164 * minute, -6, "coin", id("coke").toString(), 1, false));
        wallet(14, history);
        ClientAchievements.showForTest(new AchievementStatePayload(Map.of(), List.of("Кладовщик"), "Кладовщик"));
        for (Achievement achievement : achievements()) {
            int value = VALUES.getOrDefault(achievement.id().getPath(), 0);
            for (Achievement.Tier tier : achievement.tiers()) {
                boolean reached = value >= tier.amount();
                ClientProgress.progressForTest(achievement.questId(tier), new QuestProgress(List.of(Math.min(value, tier.amount())), reached ? now - day : 0, List.of()));
            }
        }
        List<String> read = new ArrayList<>();
        Map<String, Integer> pages = Map.of("book", 5, "create", 7, "ie", 3, "comfort", 2);
        for (Guide guide : guides()) {
            int count = pages.getOrDefault(guide.id().getPath(), 0);
            for (int i = 0; i < count && i < guide.pages().size(); i++) {
                read.add(guide.readKey(guide.pages().get(i == 1 && guide.id().getPath().equals("create") ? 7 : i)));
            }
        }
        ClientProgress.guidePagesForTest(read, Set.of(id("oritech")));
        BookState.guide = id("create");
        SearchPopover.remember("сталь");
        SearchPopover.remember("пресс");
        SearchPopover.remember("медная катушка");
    }

    /** The money the shop shows: own coins, a treasury of 52, a team, the history. */
    public static void wallet(long coins, List<ProgressStore.ShopLine> history) {
        ClientShop.showForTest(new ShopStatePayload(Map.of("coin", coins), Map.of("coin", 52L), true, Map.of(id("token").toString(), 1), history));
    }

    public static void walletOnly(long coins) {
        wallet(coins, ClientShop.history());
    }

    private static BookScreen screen() {
        return (BookScreen) Minecraft.getInstance().screen;
    }

    /** The shop on an offer: the filter, the count, the wallet, whether the list of sections is open. */
    public static void shop(String offer, String filter, int count, boolean treasury, boolean list) {
        screen().openTab(BookTabs.byId(BookTabs.SHOP));
        ShopView view = screen().shopView();
        if (offer != null) {
            view.selectForTest(id(offer));
        }
        view.stateForTest(filter, count, treasury, list);
    }

    public static void shopOnlyAvailable(boolean on) {
        screen().shopView().onlyAvailableForTest(on);
    }

    public static void shopHistory() {
        screen().shopView().showHistoryForTest();
    }

    public static void achievements(String selected, int filter) {
        screen().openTab(BookTabs.byId(BookTabs.ACHIEVEMENTS));
        AchievementsView view = screen().achievementsView();
        view.showForTest(filter, "");
        if (selected != null) {
            view.selectForTest(id(selected));
        } else {
            view.closePanelForTest();
        }
    }

    public static void library(int filter) {
        BookState.openLibrary();
        screen().guidesTab().library.showForTest(filter);
    }

    public static void guidePage(String guide, int page) {
        Guide found = ClientBook.current().guides().get(id(guide));
        BookState.openGuide(found, page);
    }

    public static void search(String query) {
        screen().setSearch(query);
        screen().focusSearch();
    }

    public static void restore() {
        ClientBook.overrideForTest(null, null);
    }
}
