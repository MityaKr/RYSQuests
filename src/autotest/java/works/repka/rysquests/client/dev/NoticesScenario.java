package works.repka.rysquests.client.dev;

import static works.repka.rysquests.client.dev.AutoTest.clickMouse;
import static works.repka.rysquests.client.dev.AutoTest.command;
import static works.repka.rysquests.client.dev.AutoTest.expect;
import static works.repka.rysquests.client.dev.AutoTest.mc;
import static works.repka.rysquests.client.dev.AutoTest.moveMouse;
import static works.repka.rysquests.client.dev.AutoTest.openWorld;
import static works.repka.rysquests.client.dev.AutoTest.pressKey;
import static works.repka.rysquests.client.dev.AutoTest.screenshot;
import static works.repka.rysquests.client.dev.AutoTest.step;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.gui.components.toasts.RecipeToast;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import works.repka.ryscore.theme.ThemeManager;
import works.repka.ryscore.ui.Notice;
import works.repka.ryscore.ui.Notices;
import works.repka.ryscore.ui.UiScale;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientConfig;
import works.repka.rysquests.client.ClientMessages;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.client.hud.StickerBoard;
import works.repka.rysquests.client.screen.BookScreen;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestOptions;
import works.repka.rysquests.data.RewardDef;
import works.repka.rysquests.data.TeamMode;
import works.repka.rysquests.network.ProgressPayload;
import works.repka.rysquests.progress.QuestLogic;
import works.repka.rysquests.progress.QuestProgress;
import works.repka.rysquests.reward.ChoiceReward;
import works.repka.rysquests.reward.CurrencyReward;
import works.repka.rysquests.reward.ItemReward;
import works.repka.rysquests.reward.XpReward;

/**
 * The family's notices about finished quests (design {@code _audit/notify}, variant A, with the author's place: the game's column of
 * toasts, top right), on the panel book that copies RepYS's "Ток": every kind of card, a series, a milestone, a closed chapter, a new
 * epoch, beside the game's own toasts, over an open window with the mouse on a card, a click and the K key, three themes and a small
 * window. The sounds are checked by what the core says it played: the test runs without sound.
 */
final class NoticesScenario {
    private static final Logger LOGGER = LoggerFactory.getLogger(NoticesScenario.class);
    private static final String NS = "panel:";
    private static final UUID MATE = UUID.fromString("5b1f3c2e-0d3a-4c7e-9a52-2f6f1b2c7a11");
    private static final int SHOWN = 24;
    private static final List<String> SOUNDS = new ArrayList<>();
    private static Map<ResourceLocation, QuestProgress> saved = Map.of();

    private NoticesScenario() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.parse(NS + path);
    }

    private static Quest quest(String path) {
        return ClientBook.current().quests().get(id(path));
    }

    private static Quest copy(Quest q, String path, String title, List<RewardDef> rewards, QuestOptions options, List<ResourceLocation> requires) {
        return new Quest(id(path), q.chapter(), q.group(), title, q.subtitle(), q.text(), q.icon(), requires, q.requiresMode(), q.stageRequired(),
                q.tasks(), rewards, q.guide(), options);
    }

    private static ProgressPayload.Done own(int reward) {
        return new ProgressPayload.Done(Optional.of(mc().player.getUUID()), mc().player.getGameProfile().getName(), reward);
    }

    private static void done(Quest quest, ProgressPayload.Done info) {
        StickerBoard.celebrateForTest(quest, info);
    }

    private static boolean on(String key) {
        return Notices.activeForTest().stream().anyMatch(line -> line.startsWith(Notice.QUESTS + "/" + key) && line.endsWith(" on"));
    }

    private static void clear() {
        Notices.clearForTest();
    }

    static void build() {
        openWorld();
        expect("the panel book reached the client", () -> ClientBook.current().quests().containsKey(id("current/copper_coil")));
        step("the mockups' window, slate, nothing pinned, no hint", () -> {
            mc().getWindow().setWindowed(1280, 800);
            mc().options.guiScale().set(2);
            mc().resizeDisplay();
            UiScale.set(UiScale.AUTO);
            moveMouse(1, 1);
            ClientMessages.dismiss();
            ThemeManager.select("ryscore:slate");
            ClientConfig.KEY_HINT_SHOWN.set(true);
            ClientConfig.COMPLETION_TOAST.set(true);
            ClientConfig.TEAM_QUEST_TOASTS.set(true);
            ClientProgress.pinnedForTest(List.of());
            command("gamerule sendCommandFeedback false");
            command("time set day");
            Notices.setSoundOn(true);
            saved = ClientProgress.snapshotForTest();
            clear();
        }, 30);

        // --- the ordinary card and what its line says ---
        step("a quest of the player's own, the reward waits", () -> done(quest("current/copper_coil"), own(ProgressPayload.Done.WAITING)), SHOWN);
        expect("the card stands in the toasts' column", () -> on("done:" + NS + "current/copper_coil"));
        expect("it rang once, at the normal volume", () -> Notices.playedForTest().stream().anyMatch(line -> line.startsWith("minecraft:block.note_block.chime volume 1.0")));
        screenshot("notify_card_slate");
        step("the card is at the right edge, under the map zone (core 1.17)", () -> {
            int[] b = Notices.boundsForTest(Notice.QUESTS, "done:" + NS + "current/copper_coil");
            int zone = works.repka.ryscore.ui.Anchors.topInset(works.repka.ryscore.ui.Anchors.Corner.TOP_RIGHT);
            double top = zone > 0 ? works.repka.ryscore.ui.UiScale.toGui(zone) + 2 : 0;
            LOGGER.info("[autotest] card bounds {} of {}x{}, map zone {} -> top {}", java.util.Arrays.toString(b), mc().getWindow().getGuiScaledWidth(), mc().getWindow().getGuiScaledHeight(), zone, top);
            if (b == null || Math.abs(b[1] - top) > 1.5 || Math.abs(b[0] + b[2] - mc().getWindow().getGuiScaledWidth()) > 1) {
                AutoTest.fail("the card is not at the right edge under the map zone: " + java.util.Arrays.toString(b));
            }
        }, 2);
        step("the card slides in from above: half way it is higher", () -> {
            clear();
            done(quest("current/copper_coil"), own(ProgressPayload.Done.WAITING));
        }, 6);
        step("half way down", () -> {
            int[] b = Notices.boundsForTest(Notice.QUESTS, "done:" + NS + "current/copper_coil");
            LOGGER.info("[autotest] sliding card bounds {}", java.util.Arrays.toString(b));
        }, 0);
        screenshot("notify_sliding");
        step("place: centre", () -> {
            clear();
            Notices.setPlace(Notices.Place.CENTER);
            done(quest("current/copper_coil"), own(ProgressPayload.Done.WAITING));
            done(quest("current/capacitor"), own(ProgressPayload.Done.RECEIVED));
        }, SHOWN);
        step("the centre cards stand in the middle, one under another", () -> {
            int[] a = Notices.boundsForTest(Notice.QUESTS, "done:" + NS + "current/copper_coil");
            int[] b = Notices.boundsForTest(Notice.QUESTS, "done:" + NS + "current/capacitor");
            int w = mc().getWindow().getGuiScaledWidth();
            LOGGER.info("[autotest] centre bounds {} {} of {}", java.util.Arrays.toString(a), java.util.Arrays.toString(b), w);
            if (a == null || b == null || Math.abs(a[0] + a[2] / 2.0 - w / 2.0) > 1.5 || a[1] > 1 || b[1] < a[1] + a[3]) {
                AutoTest.fail("the centre stack is wrong: " + java.util.Arrays.toString(a) + " " + java.util.Arrays.toString(b));
            }
        }, 2);
        screenshot("notify_place_center");
        step("place: left", () -> {
            clear();
            Notices.setPlace(Notices.Place.LEFT);
            done(quest("current/copper_coil"), own(ProgressPayload.Done.WAITING));
        }, SHOWN);
        step("the left card is flush with the left edge", () -> {
            int[] a = Notices.boundsForTest(Notice.QUESTS, "done:" + NS + "current/copper_coil");
            LOGGER.info("[autotest] left bounds {}", java.util.Arrays.toString(a));
            if (a == null || a[0] != 0) AutoTest.fail("the left card is not at the left edge: " + java.util.Arrays.toString(a));
        }, 2);
        screenshot("notify_place_left");
        step("place: right again", () -> {
            clear();
            Notices.setPlace(Notices.Place.RIGHT);
            done(quest("current/copper_coil"), own(ProgressPayload.Done.WAITING));
        }, SHOWN);
        step("received by itself, below it", () -> done(quest("current/capacitor"), own(ProgressPayload.Done.RECEIVED)), SHOWN);
        screenshot("notify_cards_waiting_received");

        step("a choice and a reward for the team", () -> {
            clear();
            Quest heavy = quest("current/heavy_engineering_block");
            RewardDef choice = heavy.rewards().stream().filter(ChoiceReward.class::isInstance).findFirst().orElseThrow();
            done(copy(quest("current/electric_mechanism"), "current/electric_mechanism", quest("current/electric_mechanism").title(), List.of(choice),
                    QuestOptions.DEFAULT, List.of()), own(ProgressPayload.Done.WAITING));
            done(copy(quest("current/electrum_ingot"), "current/electrum_ingot", quest("current/electrum_ingot").title(),
                    List.of(new CurrencyReward("coin", 8, TeamMode.ONCE)), QuestOptions.DEFAULT, List.of()), own(ProgressPayload.Done.WAITING));
        }, SHOWN);
        screenshot("notify_cards_choice_team");

        step("a teammate's quest and one without a reward", () -> {
            clear();
            done(quest("current/iron_rod"), new ProgressPayload.Done(Optional.of(MATE), "Stepan_K", ProgressPayload.Done.WAITING));
            done(copy(quest("current/ie_wires"), "current/ie_wires", quest("current/ie_wires").title(), List.of(), QuestOptions.DEFAULT, List.of()),
                    own(ProgressPayload.Done.NONE));
        }, SHOWN);
        expect("the teammate's quest rang too", () -> Notices.playedForTest().size() >= 2);
        screenshot("notify_cards_mate_none");

        // --- a series: two cards and a summary ---
        step("a long title and five more quests at once", () -> {
            clear();
            Quest mech = quest("current/electric_mechanism");
            done(copy(mech, "current/automation", "Цель автоматизации: 32× Электромеханизм",
                    List.of(new XpReward(Optional.empty(), Optional.of(300), TeamMode.EACH), new CurrencyReward("coin", 4, TeamMode.EACH)),
                    QuestOptions.DEFAULT, List.of()), own(ProgressPayload.Done.WAITING));
            for (String path : List.of("current/copper_wire", "current/kinetic_dynamo", "current/lv_accumulator", "current/mv_accumulator", "current/electrum_ingot")) {
                done(quest(path), own(ProgressPayload.Done.WAITING));
            }
        }, SHOWN);
        expect("two cards and one summary", () -> Notices.activeForTest().stream().filter(line -> line.contains(" CARD ")).count() == 2
                && Notices.activeForTest().stream().anyMatch(line -> line.contains("summary SUMMARY")));
        screenshot("notify_series");

        // --- the large cards ---
        step("a milestone", () -> {
            clear();
            done(quest("current/heavy_engineering_block"), own(ProgressPayload.Done.WAITING));
        }, SHOWN);
        expect("a milestone card", () -> on("milestone:" + NS + "current/heavy_engineering_block"));
        screenshot("notify_milestone");
        step("the main quests of Ток done: the chapter is closed", () -> {
            clear();
            for (Quest q : ClientBook.current().quests().values()) {
                if (q.chapter().filter(id("current")::equals).isPresent() && QuestLogic.isLive(q) && !q.options().optional()) {
                    ClientProgress.progressForTest(q.id(), new QuestProgress(List.of(1), System.currentTimeMillis(), List.of()));
                }
            }
            done(quest("current/copper_coil"), own(ProgressPayload.Done.RECEIVED));
        }, SHOWN);
        expect("only the chapter, its last quest gave its reward", () -> on("chapter:" + NS + "current") && !on("done:" + NS + "current/copper_coil"));
        screenshot("notify_chapter");
        step("a new epoch", () -> {
            ClientProgress.receiveForTest(saved, ClientProgress.lastChapter());
            clear();
            Quest start = quest("start/welcome");
            QuestOptions epoch = new QuestOptions(start.options().layout(), false, false, false, Optional.empty(), false, List.of("epoch"), false, false);
            Quest era = new Quest(id("start/era_ii"), start.chapter(), Optional.empty(), "II. Индустрия", Optional.empty(), Optional.empty(),
                    Optional.of(ResourceLocation.parse("minecraft:clock")), List.of(id("brass/brass_casing")), start.requiresMode(), Optional.empty(),
                    start.tasks(), List.of(new XpReward(Optional.empty(), Optional.of(300), TeamMode.EACH), new CurrencyReward("coin", 3, TeamMode.EACH),
                    new ItemReward(ResourceLocation.parse("minecraft:clock"), 1, Optional.empty(), TeamMode.EACH),
                    quest("current/heavy_engineering_block").rewards().stream().filter(ChoiceReward.class::isInstance).findFirst().orElseThrow()),
                    Optional.empty(), epoch);
            done(era, own(ProgressPayload.Done.WAITING));
        }, SHOWN);
        expect("an epoch card", () -> on("epoch:" + NS + "start/era_ii"));
        expect("the epoch sounded richer: four notes and a bell", () -> Notices.playedForTest().stream().anyMatch(line -> line.startsWith("minecraft:block.note_block.bell")));
        screenshot("notify_epoch");

        // --- with the game's own toasts: one column ---
        step("the game's toasts first, then a quest and a line of RYSTeams", () -> {
            clear();
            AdvancementHolder stone = mc().getSingleplayerServer().getAdvancements().get(ResourceLocation.parse("minecraft:story/mine_stone"));
            if (stone != null) {
                mc().getToasts().addToast(new AdvancementToast(stone));
            }
            mc().level.getRecipeManager().getRecipes().stream().findFirst().ifPresent(recipe -> RecipeToast.addOrUpdate(mc().getToasts(), recipe));
            SystemToast.add(mc().getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, Component.literal("Квест выполнен!"),
                    Component.literal("Медная катушка"));
            done(quest("current/copper_coil"), own(ProgressPayload.Done.WAITING));
            Notice.row(Notice.TEAMS, "joined").icon(works.repka.ryscore.ui.Icons.Shape.USERS).title("Lena_R вошла в команду").millis(6000).show();
        }, SHOWN + 10);
        screenshot("notify_with_game_toasts");

        // --- over an open window: the mouse stops the time, a click opens the quest ---
        step("the inventory open, a card over it", () -> {
            clear();
            mc().setScreen(new InventoryScreen(mc().player));
            done(quest("current/copper_coil"), own(ProgressPayload.Done.WAITING));
        }, SHOWN);
        screenshot("notify_over_inventory");
        step("the mouse on the card", () -> {
            int[] b = Notices.boundsForTest(Notice.QUESTS, "done:" + NS + "current/copper_coil");
            moveMouse(b[0] + b[2] / 2.0, b[1] + b[3] / 2.0);
        }, 10);
        screenshot("notify_hover");
        step("longer than the card lives", () -> {
        }, 110);
        expect("the mouse kept it", () -> on("done:" + NS + "current/copper_coil"));
        step("click the card", () -> clickMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT), 20);
        expect("the book opened on the quest", () -> mc().screen instanceof BookScreen && id("current/copper_coil").equals(BookScreen.selectedQuestForTest()));
        expect("the card left", () -> !on("done:" + NS + "current/copper_coil"));
        step("the cross takes a card away and the window under it gets nothing", () -> {
            mc().setScreen(new InventoryScreen(mc().player));
            done(quest("current/capacitor"), own(ProgressPayload.Done.WAITING));
        }, SHOWN);
        step("on the cross", () -> {
            int[] b = Notices.boundsForTest(Notice.QUESTS, "done:" + NS + "current/capacitor");
            double k = UiScale.toGui(1);
            // the cross: 10 units at the right edge minus 6, its middle on the title's line
            moveMouse(b[0] + b[2] - (6 + 5) * k, b[1] + (b[3] / k - 16) / 2 * k + 4.25 * k);
        }, 6);
        screenshot("notify_hover_cross");
        step("click the cross", () -> clickMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT), 10);
        expect("the card left, the inventory stayed", () -> !on("done:" + NS + "current/capacitor") && mc().screen instanceof InventoryScreen);
        step("close the inventory", () -> {
            mc().setScreen(null);
            moveMouse(1, 1);
        }, 5);

        // --- K in the world ---
        step("a card in the world", () -> done(quest("current/electric_mechanism"), own(ProgressPayload.Done.WAITING)), SHOWN);
        step("press K", () -> pressKey(GLFW.GLFW_KEY_K), 20);
        expect("K opened the book on that quest", () -> mc().screen instanceof BookScreen && id("current/electric_mechanism").equals(BookScreen.selectedQuestForTest()));
        step("over the open book a quest's card does not show", () -> done(quest("current/iron_rod"), own(ProgressPayload.Done.WAITING)), SHOWN);
        expect("hidden over the book", () -> !on("done:" + NS + "current/iron_rod"));
        step("close the book", () -> mc().setScreen(null), 5);

        // --- glass and vanilla ---
        for (String theme : List.of("glass", "vanilla")) {
            step(theme + ": a card and a milestone", () -> {
                clear();
                ThemeManager.select("ryscore:" + theme);
                done(quest("current/copper_coil"), own(ProgressPayload.Done.WAITING));
                done(quest("current/heavy_engineering_block"), own(ProgressPayload.Done.WAITING));
            }, SHOWN);
            screenshot("notify_" + theme);
        }

        // --- a small window ---
        step("960×520 at GUI Scale 3 with a game toast", () -> {
            clear();
            ThemeManager.select("ryscore:slate");
            mc().getWindow().setWindowed(960, 520);
            mc().options.guiScale().set(3);
            mc().resizeDisplay();
            SystemToast.add(mc().getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, Component.literal("Новые рецепты!"),
                    Component.literal("Смотри книгу рецептов"));
            done(quest("current/copper_coil"), own(ProgressPayload.Done.WAITING));
        }, SHOWN + 10);
        screenshot("notify_small");
        step("back to the mockups' window, the sounds in the log", () -> {
            clear();
            mc().getWindow().setWindowed(1280, 800);
            mc().options.guiScale().set(2);
            mc().resizeDisplay();
            ClientProgress.receiveForTest(saved, ClientProgress.lastChapter());
            SOUNDS.addAll(Notices.playedForTest());
            SOUNDS.forEach(line -> LOGGER.info("[autotest] notice sound: {}", line));
            LOGGER.info("[autotest] master volume {}", mc().options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER));
        }, 10);
        expect("no notice sound is quieter than normal", () -> SOUNDS.stream().allMatch(line -> line.contains("volume 1.0")));
    }
}
