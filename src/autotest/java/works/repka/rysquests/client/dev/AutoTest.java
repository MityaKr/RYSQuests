package works.repka.rysquests.client.dev;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.SharedConstants;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import works.repka.rysquests.client.ClientDev;
import works.repka.rysquests.client.integration.Recipes;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import works.repka.rysquests.RYSQuests;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.client.screen.BookScreen;
import works.repka.rysquests.data.BookHolder;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.ViewMode;
import works.repka.rysquests.dev.DevExamplePack;
import works.repka.rysquests.network.QuestActionPayload;
import works.repka.rysquests.progress.ProgressService;
import works.repka.rysquests.progress.ProgressStore;

/**
 * Drives the real client through a scenario: opens its own world, sends real input, takes screenshots and quits.
 *
 * <p>Inert unless {@value #SCENARIO_PROPERTY} is set, which only the {@code runAutotest} Gradle task does.
 * The verdict is the {@code [autotest] PASS} log line that the task checks after the game exits.
 */
@Mod(value = RYSQuests.MODID, dist = Dist.CLIENT)
public final class AutoTest {
    private static final String SCENARIO_PROPERTY = "rysquests.autotest";
    private static final Logger LOGGER = LoggerFactory.getLogger(AutoTest.class);
    private static final boolean AS_PLAYER = Boolean.getBoolean("rysquests.autotest.asPlayer");
    private static final int TOTAL_TICK_LIMIT = 20 * (AS_PLAYER ? 1200 : 300);
    private static final int STEP_TICK_LIMIT = 20 * (AS_PLAYER ? 600 : 120);
    private static final int REPYS_MIN_QUESTS = 2000;
    private static final UUID REPYS_PLAYER = UUID.fromString("216ea99a-d6eb-45be-8a5b-3da7852588a5");
    private static final long STARTED_AT = System.currentTimeMillis();
    private static final int EXAMPLE_QUESTS = 6;
    private static final int SWITCHED_CHAPTERS = 4;

    private record Step(String name, BooleanSupplier ready, Runnable action, int waitAfter) {}

    private static final List<Step> STEPS = new ArrayList<>();
    private static final List<String> CHAT = new ArrayList<>();
    private static String scenario;
    private static int index;
    private static int wait;
    private static int total;
    private static int stuck;
    private static boolean finished;
    private static String bookHash = "";
    private static long lastFrame;
    private static long worstFrame;

    public AutoTest() {
        scenario = System.getProperty(SCENARIO_PROPERTY);
        if (scenario == null || scenario.isBlank()) {
            return;
        }
        LOGGER.info("[autotest] scenario: {}", scenario);
        if (AS_PLAYER) {
            // Dev runs enable NeoForge game tests, and some mods of large packs crash on them.
            SharedConstants.IS_RUNNING_IN_IDE = false;
            System.setProperty("neoforge.enableGameTest", "false");
        }
        if ("datapack".equals(scenario)) {
            buildDatapackScenario();
        } else if ("validate".equals(scenario)) {
            buildValidateScenario();
        } else if ("book".equals(scenario)) {
            buildBookScenario();
        } else if ("repys".equals(scenario)) {
            buildRepysScenario();
        } else if ("design".equals(scenario)) {
            buildDesignScenario();
        } else {
            // Reported from the first tick: the client cannot be stopped yet during mod construction.
            step("check scenario name", () -> fail("unknown scenario " + scenario), 0);
        }
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Render.Post event) -> {
            long now = System.nanoTime();
            if (lastFrame != 0) {
                worstFrame = Math.max(worstFrame, now - lastFrame);
            }
            lastFrame = now;
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick());
        NeoForge.EVENT_BUS.addListener((ClientChatReceivedEvent.System event) -> CHAT.add(event.getMessage().getString()));
    }

    private static void buildDatapackScenario() {
        openWorld();
        step("run /datapack list", () -> command("datapack list"), 10);
        expect("example pack listed in chat", () -> CHAT.stream().anyMatch(line -> line.contains("rysquests/dev_example")));
        screenshot("datapack_list");

        step("press chat key", () -> pressKey(GLFW.GLFW_KEY_T), 5);
        expect("chat screen open", () -> mc().screen instanceof ChatScreen);
        step("press escape", () -> pressKey(GLFW.GLFW_KEY_ESCAPE), 5);
        expect("chat screen closed", () -> mc().screen == null);

        step("open pause menu", () -> pressKey(GLFW.GLFW_KEY_ESCAPE), 5);
        expect("pause menu open", () -> mc().screen instanceof PauseScreen);
        step("click back to game", AutoTest::clickBackToGame, 5);
        expect("pause menu closed by mouse", () -> mc().screen == null);
    }

    /** The clean example must report no errors; turning the broken book on must report its three errors. */
    private static void buildValidateScenario() {
        String enable = "datapack enable \"" + DevExamplePack.BROKEN_PACK_ID + "\"";
        String disable = "datapack disable \"" + DevExamplePack.BROKEN_PACK_ID + "\"";
        openWorld();
        step("make sure the broken pack is off", () -> command(disable), 60);
        expect("example book reached the client", () -> ClientBook.current().quests().size() == EXAMPLE_QUESTS);

        step("validate the clean book", () -> {
            CHAT.clear();
            command("rysquests validate");
        }, 10);
        expect("report file says 0 errors", () -> reportStartsWith("errors: 0,"));
        screenshot("validate_clean");

        step("enable the broken pack", () -> {
            bookHash = ClientBook.current().hash();
            command(enable);
        }, 20);
        expect("client got the reloaded book", () -> !ClientBook.current().hash().equals(bookHash));
        step("validate the broken book", () -> {
            CHAT.clear();
            command("rysquests validate");
        }, 10);
        expect("report file says 3 errors", () -> reportStartsWith("errors: 3,"));
        expect("cycle in chat", () -> chatHas("example_broken:x/a"));
        expect("missing dependency in chat", () -> chatHas("example_broken:x/d"));
        expect("unknown item in chat", () -> chatHas("notamod:does_not_exist"));
        expect("valid quests still loaded", () -> ClientBook.current().quests().size() == EXAMPLE_QUESTS);
        screenshot("validate_broken");

        step("turn the broken pack off", () -> {
            bookHash = ClientBook.current().hash();
            command(disable);
        }, 20);
        expect("client went back to the clean book", () -> !ClientBook.current().hash().equals(bookHash));
    }

    /** The whole migration on a copy of the real pack: import the book, import the owner's progress, open the book. */
    private static void buildRepysScenario() {
        openWorld();
        step("import the FTB book", () -> command("rysquests import ftbq repys"), 40);
        expect("imported book reached the client", () -> ClientBook.current().chapters().size() > 1);
        step("write the full validation report", () -> command("rysquests validate"), 40);
        expect("no quest was left out", () -> ClientBook.current().quests().size() > REPYS_MIN_QUESTS);
        expect("translations reloaded", () -> mc().getOverlay() == null
                && I18n.exists(ClientBook.current().chapters().values().iterator().next().title()));
        step("copy FTB progress into the world", AutoTest::copyFtbqProgress, 5);
        step("import FTB progress", () -> command("rysquests import ftbq progress"), 20);
        expect("progress of the real player imported", () -> !ProgressStore.get(mc().getSingleplayerServer()).team(REPYS_PLAYER).isEmpty());
        step("adopt that progress for the test player", () -> {
            MinecraftServer server = mc().getSingleplayerServer();
            ServerPlayer player = server.getPlayerList().getPlayer(mc().player.getUUID());
            ProgressStore store = ProgressStore.get(server);
            store.replaceTeam(player.getUUID(), store.team(REPYS_PLAYER));
            ProgressService.invalidateAll();
            ProgressService.sendAll(player);
        }, 20);
        expect("client shows completed quests", () -> completedQuest() != null);
        step("report", () -> {
            long done = ClientBook.current().quests().values().stream().filter(quest -> ClientProgress.get(quest.id()).completed()).count();
            LOGGER.info("[autotest] book {} chapters, {} quests, completed {}, report: {}", ClientBook.current().chapters().size(),
                    ClientBook.current().quests().size(), done, BookHolder.current().report().errors() + " errors " + BookHolder.current().report().warnings() + " warnings");
            BookHolder.current().report().issues().stream().limit(30).forEach(issue -> LOGGER.info("[autotest] issue {}", issue));
        }, 2);
        step("open the book on a finished quest", () -> {
            Quest quest = completedQuest();
            BookScreen.selectForTest(quest.chapter().orElseThrow(), quest.id());
            pressKey(GLFW.GLFW_KEY_K);
        }, 20);
        expect("book screen open", () -> mc().screen instanceof BookScreen);
        screenshot("repys_book");
        step("switch to the map", () -> BookScreen.showForTest(ViewMode.MAP), 40);
        screenshot("repys_map");
        step("switch to what next", () -> BookScreen.showForTest(ViewMode.HOME), 25);
        screenshot("repys_home");
        step("switch to the list", () -> BookScreen.showForTest(ViewMode.LIST), 10);
        screenshot("repys_list");
        step("switch to the timeline", () -> BookScreen.showForTest(ViewMode.TIMELINE), 25);
        screenshot("repys_timeline");
        step("switch to dev", () -> BookScreen.showForTest(ViewMode.DEV), 10);
        expect("dev report arrived", () -> ClientDev.report() != null);
        step("log the report", () -> ClientDev.report().sections().forEach(section -> LOGGER.info("[autotest] section {}: {}", section.id(), section.entries().size())), 10);
        screenshot("repys_dev");
        step("back to the flow", () -> BookScreen.showForTest(ViewMode.FLOW), 5);

        step("measure opening the book", () -> {
            mc().setScreen(null);
            long start = System.nanoTime();
            mc().setScreen(new BookScreen());
            LOGGER.info("[autotest] opening the book took {} ms", (System.nanoTime() - start) / 1_000_000);
            worstFrame = 0;
        }, 30);
        // The same chapters twice: the second pass shows the cost of drawing them without first-time work.
        for (int pass = 1; pass <= 2; pass++) {
            for (int chapter = 0; chapter < SWITCHED_CHAPTERS; chapter++) {
                int number = chapter;
                step("switch through chapters", () -> showChapter(number), 5);
            }
            int finishedPass = pass;
            step("report frame time", () -> {
                LOGGER.info("[autotest] worst frame while switching chapters, pass {}: {} ms", finishedPass, worstFrame / 1_000_000);
                worstFrame = 0;
            }, 2);
        }

        expect("recipe viewer connected", Recipes::available);
        step("open a recipe from the book", () -> Recipes.showRecipes(new ItemStack(Items.IRON_INGOT)), 20);
        expect("recipe window open", () -> mc().screen != null && !(mc().screen instanceof BookScreen));
        screenshot("repys_recipe");
        step("leave the recipe window", () -> pressKey(GLFW.GLFW_KEY_ESCAPE), 15);
        expect("back in the book", () -> mc().screen instanceof BookScreen);
        step("close the book", () -> pressKey(GLFW.GLFW_KEY_ESCAPE), 10);
        expect("book closed", () -> mc().screen == null);
        step("write the id list for tools", () -> command("rysquests dev registry"), 20);
        expect("id list written", () -> writtenThisRun(mc().gameDirectory.toPath().resolve("logs").resolve("rysquests-registry.json")));
        step("open the inventory for the FTB panel", () -> pressKey(GLFW.GLFW_KEY_E), 30);
        screenshot("repys_ftb_panel");
        step("close the inventory", () -> pressKey(GLFW.GLFW_KEY_ESCAPE), 10);
    }

    private static void showChapter(int number) {
        List<Quest> quests = ClientBook.current().quests().values().stream().sorted(java.util.Comparator.comparing(quest -> quest.id().toString())).toList();
        Quest quest = quests.get(number * quests.size() / SWITCHED_CHAPTERS);
        BookScreen.selectForTest(quest.chapter().orElseThrow(), quest.id());
    }

    private static Quest completedQuest() {
        return ClientBook.current().quests().values().stream().filter(quest -> ClientProgress.get(quest.id()).completed()).findFirst().orElse(null);
    }

    private static void copyFtbqProgress() {
        Path source = Path.of(System.getProperty("rysquests.autotest.ftbqSaves"));
        Path target = mc().getSingleplayerServer().getWorldPath(LevelResource.ROOT).resolve("ftbquests");
        try (var files = Files.list(source)) {
            Files.createDirectories(target);
            for (Path file : files.toList()) {
                Files.copy(file, target.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Puts the book into the state of the mockups and photographs every screen, for comparing with them pixel by pixel. */
    private static void buildDesignScenario() {
        String mechanism = "design:02/mechanism";
        ResourceLocation chapter = ResourceLocation.parse("design:02");
        ResourceLocation quest = ResourceLocation.parse(mechanism);
        openWorld();
        step("match the window of the mockups", () -> {
            mc().options.guiScale().set(2);
            mc().options.languageCode = "ru_ru";
            mc().getLanguageManager().setSelected("ru_ru");
            mc().resizeDisplay();
            mc().reloadResourcePacks();
        }, 40);
        expect("language applied", () -> mc().getOverlay() == null);
        step("reset the world", () -> {
            command("gamerule sendCommandFeedback false");
            command("clear @s");
            ClientBook.current().quests().keySet().forEach(id -> command("rysquests progress @s reset " + id));
        }, 20);
        step("finish the earlier quests", () -> {
            for (String id : List.of("design:02/brass", "design:02/gears", "design:02/wires", "design:02/dynamo", "design:01/gift_a", "design:01/gift_b")) {
                command("rysquests progress @s complete " + id);
            }
        }, 20);
        step("give what the current quest takes", () -> {
            command("give @s minecraft:copper_ingot 16");
            command("give @s minecraft:comparator 1");
            command("give @s minecraft:redstone_torch 3");
        }, 40);
        step("hand it in", () -> PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.SUBMIT, quest, 0, List.of())), 10);
        step("confirm the second task", () -> PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.CHECK, quest, 1, List.of())), 10);
        step("give one more", () -> command("give @s minecraft:comparator 1"), 30);
        expect("quest is two thirds done", () -> ClientProgress.get(quest).task(0) == 16 && ClientProgress.get(quest).task(2) == 1);
        step("open the book", () -> {
            BookScreen.selectForTest(chapter, quest);
            BookScreen.showForTest(ViewMode.FLOW);
            pressKey(GLFW.GLFW_KEY_K);
        }, 30);
        expect("book screen open", () -> mc().screen instanceof BookScreen);
        screenshot("design_flow");
        step("switch to the map", () -> BookScreen.showForTest(ViewMode.MAP), 40);
        screenshot("design_map");
        step("switch to the list", () -> BookScreen.showForTest(ViewMode.LIST), 10);
        screenshot("design_list");
        step("switch to what next", () -> BookScreen.showForTest(ViewMode.HOME), 25);
        screenshot("design_home");
        step("switch to the timeline", () -> BookScreen.showForTest(ViewMode.TIMELINE), 25);
        screenshot("design_timeline");
        step("switch to dev", () -> BookScreen.showForTest(ViewMode.DEV), 20);
        expect("dev report arrived", () -> ClientDev.report() != null);
        step("wait for the report on screen", () -> {}, 10);
        screenshot("design_dev");
        step("back to the flow", () -> BookScreen.showForTest(ViewMode.FLOW), 5);
        step("pin the quest", () -> {
            if (!ClientProgress.pinned().contains(quest)) {
                pressKey(GLFW.GLFW_KEY_P);
            }
        }, 10);
        step("close the book", () -> pressKey(GLFW.GLFW_KEY_ESCAPE), 8);
        expect("book closed", () -> mc().screen == null);
        step("finish a quest for the card", () -> command("rysquests progress @s complete design:02/pump"), 20);
        screenshot("design_hud");
    }

    /** Plays the first example quest through the real keys and the real packets. */
    private static void buildBookScenario() {
        ResourceLocation chapter = ResourceLocation.parse("example:01_start");
        ResourceLocation quest = ResourceLocation.parse("example:start/first_tools");
        openWorld();
        expect("example book reached the client", () -> ClientBook.current().quests().size() == EXAMPLE_QUESTS);
        step("reset the quest", () -> {
            command("gamerule sendCommandFeedback false");
            command("clear @s");
            command("rysquests progress @s reset " + quest);
        }, 10);
        expect("quest is reset", () -> !ClientProgress.get(quest).completed());
        step("give the required items", () -> {
            command("give @s minecraft:oak_log 8");
            command("give @s minecraft:wooden_pickaxe");
        }, 45);
        expect("observed item counted by polling", () -> ClientProgress.get(quest).task(1) == 1);
        step("open the book with its key", () -> {
            BookScreen.selectForTest(chapter, quest);
            pressKey(GLFW.GLFW_KEY_K);
        }, 10);
        expect("book screen open", () -> mc().screen instanceof BookScreen);
        screenshot("book_open");
        step("unpin leftovers of earlier runs", () -> {
            if (ClientProgress.pinned().contains(quest)) {
                pressKey(GLFW.GLFW_KEY_P);
            }
        }, 10);
        expect("quest not pinned", () -> !ClientProgress.pinned().contains(quest));
        step("pin with its key", () -> pressKey(GLFW.GLFW_KEY_P), 10);
        expect("quest pinned", () -> ClientProgress.pinned().contains(quest));
        step("close the book to see the board", () -> pressKey(GLFW.GLFW_KEY_ESCAPE), 8);
        expect("book closed for the board", () -> mc().screen == null);
        screenshot("sticker");
        step("reopen the book", () -> pressKey(GLFW.GLFW_KEY_K), 10);
        expect("book open again", () -> mc().screen instanceof BookScreen);
        step("submit logs", () -> PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.SUBMIT, quest, 0, List.of())), 10);
        expect("quest completed", () -> ClientProgress.get(quest).completed());
        expect("logs consumed", () -> mc().player.getInventory().countItem(Items.OAK_LOG) == 0);
        screenshot("book_completed");
        step("claim rewards", () -> PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.CLAIM, quest, 0, List.of())), 10);
        expect("reward received", () -> mc().player.getInventory().countItem(Items.BREAD) == 4);
        expect("quest claimed", () -> ClientProgress.get(quest).claimed().size() == 2);
        screenshot("book_claimed");
        step("close the book", () -> pressKey(GLFW.GLFW_KEY_ESCAPE), 5);
        expect("book closed", () -> mc().screen == null);
    }

    private static boolean chatHas(String text) {
        return CHAT.stream().anyMatch(line -> line.contains(text));
    }

    private static boolean reportStartsWith(String prefix) {
        Path report = mc().gameDirectory.toPath().resolve("logs").resolve("rysquests-validate.txt");
        try {
            return writtenThisRun(report) && Files.readString(report).startsWith(prefix);
        } catch (IOException e) {
            return false;
        }
    }

    // --- steps ---

    private static void step(String name, Runnable action, int waitAfter) {
        STEPS.add(new Step(name, () -> true, action, waitAfter));
    }

    private static void expect(String name, BooleanSupplier condition) {
        STEPS.add(new Step(name, condition, () -> {}, 0));
    }

    private static void openWorld() {
        // A fresh game directory opens the accessibility onboarding instead of the title screen.
        STEPS.add(new Step("open world", () -> mc().screen != null && mc().level == null && mc().getOverlay() == null && total > 40, () -> {
            mc().options.pauseOnLostFocus = false;
            mc().options.onboardAccessibility = false;
            mc().options.tutorialStep = TutorialSteps.NONE;
            mc().getTutorial().setStep(TutorialSteps.NONE);
            if (!(mc().screen instanceof TitleScreen)) {
                mc().setScreen(new TitleScreen());
            }
            String world = worldName();
            if (mc().getLevelSource().levelExists(world)) {
                mc().createWorldOpenFlows().openWorld(world, () -> fail("world did not open"));
            } else {
                LevelSettings settings = new LevelSettings(world, GameType.SURVIVAL, false, Difficulty.PEACEFUL, true,
                        new GameRules(), WorldDataConfiguration.DEFAULT);
                mc().createWorldOpenFlows().createFreshLevel(world, settings, new WorldOptions(42L, false, false),
                        registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                        mc().screen);
            }
        }, 20));
        STEPS.add(new Step("world loaded", () -> {
            skipBackupQuestion();
            return mc().player != null && mc().level != null && mc().screen == null;
        }, () -> {}, 40));
    }

    private static void screenshot(String name) {
        String file = "rysquests_" + name + ".png";
        step("screenshot " + name, () -> Screenshot.grab(mc().gameDirectory, file, mc().getMainRenderTarget(), message -> {}), 5);
        expect("screenshot written " + name, () -> writtenThisRun(mc().gameDirectory.toPath().resolve("screenshots").resolve(file)));
    }

    private static boolean writtenThisRun(Path file) {
        try {
            return Files.isRegularFile(file) && Files.getLastModifiedTime(file).toMillis() >= STARTED_AT;
        } catch (IOException e) {
            return false;
        }
    }

    private static void tick() {
        if (finished) {
            return;
        }
        Step step = STEPS.get(index);
        if (++total > TOTAL_TICK_LIMIT) {
            fail("total time limit reached at step: " + step.name);
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        if (!isReady(step)) {
            if (++stuck > STEP_TICK_LIMIT) {
                fail("step timed out: " + step.name);
            }
            return;
        }
        stuck = 0;
        LOGGER.info("[autotest] {}", step.name);
        try {
            step.action.run();
        } catch (RuntimeException e) {
            LOGGER.error("[autotest] step failed: {}", step.name, e);
            fail("step threw: " + step.name);
            return;
        }
        wait = step.waitAfter;
        if (!finished && ++index >= STEPS.size()) {
            LOGGER.info("[autotest] PASS: {}", scenario);
            finished = true;
            mc().stop();
        }
    }

    private static boolean isReady(Step step) {
        try {
            return step.ready.getAsBoolean();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static void fail(String reason) {
        LOGGER.error("[autotest] FAIL: {}", reason);
        finished = true;
        mc().stop();
    }

    // --- game access ---

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    /** One world per mod set, reused between runs: opening a world with other mods stops on a game prompt. */
    private static String worldName() {
        int hash = ModList.get().getMods().stream().map(mod -> mod.getModId() + mod.getVersion()).sorted().toList().hashCode();
        return "rysquests_autotest_" + Integer.toHexString(hash);
    }

    private static void skipBackupQuestion() {
        if (mc().screen instanceof BackupConfirmScreen screen) {
            clickButton(screen.children(), "selectWorld.backupJoinSkipButton", true);
        }
    }

    private static void command(String command) {
        mc().player.connection.sendCommand(command);
    }

    private static void clickBackToGame() {
        if (!clickButton(mc().screen.children(), "menu.returnToGame", false)) {
            fail("back to game button not found");
        }
    }

    private static boolean clickButton(List<?> children, String translationKey, boolean direct) {
        for (Object child : children) {
            if (child instanceof Button button
                    && button.getMessage().getContents() instanceof TranslatableContents text
                    && translationKey.equals(text.getKey())) {
                if (direct) {
                    button.onPress();
                } else {
                    moveMouse(button.getX() + button.getWidth() / 2.0, button.getY() + button.getHeight() / 2.0);
                    clickMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
                }
                return true;
            }
        }
        return false;
    }

    /** Feeds the game the same input the window system does. */
    private static void pressKey(int key) {
        long window = mc().getWindow().getWindow();
        int scan = GLFW.glfwGetKeyScancode(key);
        mc().keyboardHandler.keyPress(window, key, scan, GLFW.GLFW_PRESS, 0);
        mc().keyboardHandler.keyPress(window, key, scan, GLFW.GLFW_RELEASE, 0);
    }

    private static void moveMouse(double guiX, double guiY) {
        double scale = mc().getWindow().getGuiScale();
        double x = guiX * scale * mc().getWindow().getScreenWidth() / mc().getWindow().getWidth();
        double y = guiY * scale * mc().getWindow().getScreenHeight() / mc().getWindow().getHeight();
        try {
            var onMove = MouseHandler.class.getDeclaredMethod("onMove", long.class, double.class, double.class);
            onMove.setAccessible(true);
            onMove.invoke(mc().mouseHandler, mc().getWindow().getWindow(), x, y);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("MouseHandler.onMove is not accessible", e);
        }
    }

    private static void clickMouse(int button) {
        long window = mc().getWindow().getWindow();
        try {
            var onPress = MouseHandler.class.getDeclaredMethod("onPress", long.class, int.class, int.class, int.class);
            onPress.setAccessible(true);
            onPress.invoke(mc().mouseHandler, window, button, GLFW.GLFW_PRESS, 0);
            onPress.invoke(mc().mouseHandler, window, button, GLFW.GLFW_RELEASE, 0);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("MouseHandler.onPress is not accessible", e);
        }
    }
}
