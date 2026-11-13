package works.repka.rysquests.client.dev;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import com.mojang.blaze3d.platform.NativeImage;
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
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.network.chat.Component;
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
import works.repka.rysquests.client.ClientMessages;
import works.repka.rysquests.client.screen.BookRole;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.client.screen.BookScreen;
import works.repka.rysquests.client.screen.PanelShowcase;
import works.repka.rysquests.data.BookHolder;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.ViewMode;
import works.repka.rysquests.dev.DevExamplePack;
import works.repka.rysquests.network.QuestActionPayload;
import works.repka.rysquests.progress.ProgressService;
import works.repka.rysquests.progress.ProgressStore;
import works.repka.rysquests.client.ClientAssets;
import works.repka.rysquests.network.EditorSavePayload;
import works.repka.rysquests.client.ClientShop;
import works.repka.rysquests.client.ClientAchievements;
import works.repka.rysquests.network.ShopTradePayload;
import works.repka.ryscore.theme.ThemeManager;
import works.repka.ryscore.ui.Type;

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
    private static final int EXAMPLE_QUESTS = 14;
    private static final int SWITCHED_CHAPTERS = 4;
    private static final List<ItemStack> RECIPE_REQUESTS = new ArrayList<>();
    private static final ResourceLocation SERVER_GUIDE = ResourceLocation.parse("teamtest:editor_guide");
    private static final ResourceLocation SERVER_IMAGE = ResourceLocation.parse("teamtest:textures/guides/editor_guide_1.png");
    private static final boolean USE_RYSTEAMS = Boolean.getBoolean("rysquests.autotest.rysteams");
    private static final int TEAM_COLOR = 0x2E8B57;
    private static final String DRAWN_AS_ICONS = "\u2713\u2714\u2715\u2716\u2717\u2605\u2192\u2198\u2193\u2199\u2190\u2196\u2191\u2197";
    private static final String TEST_SERVER = "localhost:25577";
    private static final ResourceLocation TEAM_QUEST = ResourceLocation.parse("teamtest:shared");
    private static final ResourceLocation SCRIPT_QUEST = ResourceLocation.parse("repys:autotest/script");

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
        } else if ("frame".equals(scenario)) {
            buildFrameScenario();
        } else if ("panel".equals(scenario)) {
            buildPanelScenario();
        } else if ("repys".equals(scenario)) {
            buildRepysScenario();
        } else if ("design".equals(scenario) || "design_sizes".equals(scenario)) {
            buildDesignScenario();
        } else if ("guides".equals(scenario)) {
            buildGuidesScenario();
        } else if ("editor".equals(scenario)) {
            buildEditorScenario();
        } else if ("shop".equals(scenario)) {
            buildShopScenario();
        } else if ("integrations".equals(scenario)) {
            buildIntegrationsScenario();
        } else if ("quest_editor".equals(scenario)) {
            buildQuestEditorScenario();
        } else if ("kubejs".equals(scenario)) {
            buildKubeJsScenario();
        } else if ("achievements".equals(scenario)) {
            buildAchievementsScenario();
        } else if ("constructor".equals(scenario)) {
            buildConstructorScenario();
        } else if ("sections".equals(scenario)) {
            buildSectionsScenario();
        } else if ("sections_design".equals(scenario)) {
            buildSectionsDesignScenario();
        } else if ("guides_design".equals(scenario)) {
            buildGuidesDesignScenario();
        } else if ("layout".equals(scenario)) {
            buildLayoutScenario();
        } else if ("team_a".equals(scenario)) {
            buildTeamLeaderScenario();
        } else if ("team_b".equals(scenario)) {
            buildTeamMemberScenario();
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
        step("command answers go to chat, other scenarios turn them off", () -> command("gamerule sendCommandFeedback true"), 5);
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
        step("add a quest driven by a script", AutoTest::writeScriptQuest, 5);
        step("reload the book and scripts", () -> command("reload"), 60);
        expect("script quest reached the client", () -> ClientBook.current().quests().containsKey(SCRIPT_QUEST));
        expect("script completed the task", () -> ClientProgress.get(SCRIPT_QUEST).completed());
        step("claim the script reward", () -> PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.CLAIM, SCRIPT_QUEST, 0, List.of())), 20);
        expect("script received every event", () -> scriptLogHas("rysquests-kjs completed") && scriptLogHas("rysquests-kjs task") && scriptLogHas("rysquests-kjs reward"));
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
        step("scroll the list", () -> mc().screen.mouseScrolled(mc().getWindow().getGuiScaledWidth() * 0.6, mc().getWindow().getGuiScaledHeight() * 0.5, 0, -3), 15);
        screenshot("repys_list_scrolled");
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

    /** Only ever writes into the copy of the pack: this scenario does not run anywhere else. */
    private static void writeScriptQuest() {
        Path game = mc().gameDirectory.toPath();
        try {
            Files.writeString(game.resolve("kubejs/server_scripts/rysquests_autotest.js"), """
                    RYSQuestsEvents.completed('repys:autotest/script', e => console.info('rysquests-kjs completed ' + e.quest));
                    RYSQuestsEvents.task('repys:autotest_task', e => { e.setProgress(1); console.info('rysquests-kjs task'); });
                    RYSQuestsEvents.reward('repys:autotest_reward', e => console.info('rysquests-kjs reward'));
                    """);
            Files.writeString(game.resolve("config/rysquests/import/data/repys/rysquests/chapters/a01_map/autotest_script.json"), """
                    { "id": "repys:autotest/script", "chapter": "repys:a01_map", "title": "Script test", "icon": "minecraft:paper",
                      "tasks": [ { "type": "rysquests:script", "script": "repys:autotest_task", "title": "Script" } ],
                      "rewards": [ { "type": "rysquests:script", "script": "repys:autotest_reward" } ] }
                    """);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean scriptLogHas(String text) {
        try {
            return Files.readString(mc().gameDirectory.toPath().resolve("logs/kubejs/server.log")).contains(text);
        } catch (IOException e) {
            return false;
        }
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

    /** Every window size, GUI scale and theme the family checks, on the main screens. */
    private static void addSizeSteps() {
        for (ViewMode view : List.of(ViewMode.FLOW, ViewMode.MAP, ViewMode.HOME)) {
            for (int[] size : new int[][] {{2560, 1440}, {1920, 1080}}) {
                for (int gui : new int[] {2, 3, 4, 0}) {
                    for (String theme : List.of("slate", "glass")) {
                        step("show " + view + " " + size[0] + " gui " + gui + " " + theme, () -> {
                            mc().getWindow().setWindowed(size[0], size[1]);
                            mc().options.guiScale().set(gui);
                            mc().resizeDisplay();
                            ThemeManager.select("ryscore:" + theme);
                            BookScreen.showForTest(view);
                        }, 12);
                        screenshot("sizes_" + view.getSerializedName() + "_" + size[0] + "_gui" + gui + "_" + theme);
                    }
                }
            }
        }
        step("back to the size of the mockups", () -> {
            mc().getWindow().setWindowed(1280, 800);
            mc().options.guiScale().set(2);
            mc().resizeDisplay();
            ThemeManager.select("ryscore:slate");
        }, 10);
    }

    private static void checkSymbols() {
        for (String language : List.of("ru_ru", "en_us")) {
            try (var in = AutoTest.class.getResourceAsStream("/assets/rysquests/lang/" + language + ".json")) {
                String text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                text.codePoints().filter(point -> point > 126 && !Type.supports(point) && DRAWN_AS_ICONS.indexOf(point) < 0).distinct().forEach(point ->
                        fail("symbol outside the font in " + language + ": U+" + Integer.toHexString(point)));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static void joinServer() {
        STEPS.add(new Step("connect to the test server", () -> mc().screen != null && mc().level == null && mc().getOverlay() == null && total > 40, () -> {
            mc().options.pauseOnLostFocus = false;
            mc().options.onboardAccessibility = false;
            if (!(mc().screen instanceof TitleScreen)) {
                mc().setScreen(new TitleScreen());
            }
            ConnectScreen.startConnecting(mc().screen, mc(), ServerAddress.parseString(TEST_SERVER), new ServerData("test", TEST_SERVER, ServerData.Type.OTHER), false, null);
        }, 20));
        STEPS.add(new Step("joined the server", () -> mc().player != null && mc().level != null && mc().screen == null, () -> {}, 40));
    }

    /** RYSTeams is only there in the run that installs it, so it is reached by name. */
    private static void rysTeamsAction(String action, CompoundTag args) {
        try {
            Class.forName("works.repka.rysteams.network.ModNetwork").getMethod("action", String.class, CompoundTag.class).invoke(null, action, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("RYSTeams is not reachable", e);
        }
    }

    private static UUID rysTeamsInvite() {
        try {
            List<?> invites = (List<?>) Class.forName("works.repka.rysteams.client.TeamClient").getField("invites").get(null);
            return invites.isEmpty() ? null : (UUID) invites.getFirst().getClass().getMethod("id").invoke(invites.getFirst());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("RYSTeams is not reachable", e);
        }
    }

    private static int players() {
        return mc().getConnection().getOnlinePlayers().size();
    }

    private static int count(net.minecraft.world.item.Item item) {
        return mc().player.getInventory().countItem(item);
    }

    /** Leads a team of two on a dedicated server and closes the server when the other player has left. */
    private static void buildTeamLeaderScenario() {
        joinServer();
        expect("the book came over the network", () -> ClientBook.current().quests().containsKey(TEAM_QUEST));
        expect("the other player is here", () -> players() == 2);
        if (USE_RYSTEAMS) {
            step("create a team in RYSTeams", () -> {
                CompoundTag args = new CompoundTag();
                args.putString("name", "Repka");
                args.putInt("color", TEAM_COLOR);
                args.putString("icon", "minecraft:emerald");
                rysTeamsAction("create", args);
            }, 20);
            step("invite through RYSTeams", () -> {
                CompoundTag args = new CompoundTag();
                args.putUUID("player", mc().getConnection().getPlayerInfo("TestB").getProfile().getId());
                rysTeamsAction("invite", args);
            }, 10);
        } else {
            step("create a team", () -> command("rysquests team create"), 20);
            step("invite the other player", () -> command("rysquests team invite TestB"), 10);
        }
        expect("the other player joined", () -> ClientProgress.teamSize() == 2);
        step("open the book on the server", () -> {
            BookScreen.selectForTest(ResourceLocation.parse("teamtest:main"), TEAM_QUEST);
            pressKey(GLFW.GLFW_KEY_K);
        }, 20);
        expect("book screen open", () -> mc().screen instanceof BookScreen);
        step("switch to the map", () -> BookScreen.showForTest(ViewMode.MAP), 20);
        screenshot("server_team");
        step("close the book", () -> pressKey(GLFW.GLFW_KEY_ESCAPE), 10);
        step("confirm the shared task", () -> PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.CHECK, TEAM_QUEST, 0, List.of())), 20);
        expect("quest completed", () -> ClientProgress.get(TEAM_QUEST).completed());
        step("claim first", () -> PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.CLAIM, TEAM_QUEST, 0, List.of())), 20);
        expect("got the reward for each and the single one", () -> count(Items.BREAD) == 1 && count(Items.DIAMOND) == 1);
        addShopOnServerSteps();
        addEditorOnServerSteps();
        step("import FTB progress where the other player took the reward", AutoTest::writeFtbqPartyFile, 5);
        step("run the import", () -> command("rysquests import ftbq progress"), 20);
        expect("the leader has not taken what the other player took", () -> ClientProgress.get(TEAM_IMPORTED).completed() && ClientProgress.get(TEAM_IMPORTED).claimed().isEmpty());
        expect("the other player has left", () -> players() == 1);
        step("stop the server", () -> command("stop"), 100);
    }

    /** Joins the team by invitation and shows what the second member gets. */
    private static void buildTeamMemberScenario() {
        joinServer();
        if (USE_RYSTEAMS) {
            expect("invited in RYSTeams", () -> rysTeamsInvite() != null);
            step("accept in RYSTeams", () -> {
                CompoundTag args = new CompoundTag();
                args.putUUID("team", rysTeamsInvite());
                args.putString("answer", "accept");
                rysTeamsAction("invite_reply", args);
            }, 20);
        } else {
            expect("invited", () -> CHAT.stream().anyMatch(line -> line.contains("/rysquests team join")));
            step("join the team", () -> command("rysquests team join"), 20);
        }
        expect("team of two on this client", () -> ClientProgress.teamSize() == 2);
        expect("progress is shared", () -> ClientProgress.get(TEAM_QUEST).completed());
        expect("the single reward is taken by the first", () -> ClientProgress.get(TEAM_QUEST).claimed().contains(1) && !ClientProgress.get(TEAM_QUEST).claimed().contains(0));
        step("claim second", () -> PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.CLAIM, TEAM_QUEST, 0, List.of())), 20);
        expect("got only the reward for each", () -> count(Items.BREAD) == 1 && count(Items.DIAMOND) == 0);
        addShopMemberSteps();
        expect("the saved guide reached the other player", () -> ClientBook.current().guides().containsKey(SERVER_GUIDE));
        expect("its text came with the book", () -> Component.translatable(ClientBook.current().guides().get(SERVER_GUIDE).title()).getString().equals("Written on the server"));
        expect("its picture came with the book", () -> ClientAssets.imageSize(SERVER_IMAGE) != null);
        expect("the section reached the other player", () -> ClientBook.current().sections().values().stream().anyMatch(section -> Component.translatable(section.title()).getString().equals("Server atlas")));
        expect("the rail came too", () -> ClientBook.current().meta().sections().size() >= 2);
        expect("the import knows this player took the reward", () -> ClientProgress.get(TEAM_IMPORTED).completed() && ClientProgress.get(TEAM_IMPORTED).claimed().contains(0));
        step("try to save without rights", () -> PacketDistributor.sendToServer(new EditorSavePayload(Optional.of(ClientBook.current().guides().get(SERVER_GUIDE)), Optional.empty(), "en_us", Map.of(), Map.of(), List.of())), 20);
        expect("the server refused", () -> "rysquests.editor.error.denied".equals(BookScreen.lastEditorResultForTest()));
        step("leave the server", () -> {}, 5);
    }

    /** Puts the book into the state of the mockups and photographs every screen, for comparing with them pixel by pixel. */
    /** What a player does with a guide, on the example pack. */
    private static void buildGuidesScenario() {
        ResourceLocation furnace = ResourceLocation.parse("example:furnace");
        openWorld();
        step("match the window of the mockups", () -> {
            mc().options.guiScale().set(2);
            mc().resizeDisplay();
        }, 10);
        step("listen for recipe requests", () -> Recipes.set(new Recipes.Bridge() {
            @Override
            public void showRecipes(ItemStack stack) {
                RECIPE_REQUESTS.add(stack);
            }

            @Override
            public void showUsages(ItemStack stack) {
            }
        }), 1);
        step("open the book", () -> pressKey(GLFW.GLFW_KEY_K), 30);
        expect("book screen open", () -> mc().screen instanceof BookScreen);
        step("click the guides on the rail", () -> mc().screen.mouseClicked(41.5, 95, GLFW.GLFW_MOUSE_BUTTON_LEFT), 5);
        expect("the library is open", () -> BookScreen.inGuidesForTest() && BookScreen.guidePageForTest() == -1);
        step("select the guide", () -> BookScreen.selectGuideForTest(furnace), 5);
        screenshot("guides_example_library");
        step("click its first page", () -> mc().screen.mouseClicked(504, 72, GLFW.GLFW_MOUSE_BUTTON_LEFT), 15);
        expect("the page is open", () -> BookScreen.guidePageForTest() == 0);
        expect("the page counts as read", () -> isRead(furnace, 0));
        screenshot("guides_example_page");
        step("click the item in the text", () -> mc().screen.mouseClicked(169.5, 56, GLFW.GLFW_MOUSE_BUTTON_LEFT), 3);
        expect("the item opens its recipe", () -> RECIPE_REQUESTS.stream().anyMatch(stack -> stack.is(net.minecraft.world.item.Items.FURNACE)));
        step("turn the page", () -> pressKey(GLFW.GLFW_KEY_RIGHT), 40);
        expect("the next page is open", () -> BookScreen.guidePageForTest() == 1);
        expect("that page counts as read too", () -> isRead(furnace, 1));
        screenshot("guides_example_recipe");
        step("the quest's button leads to its page", () -> BookScreen.openRefForTest(ClientBook.current().quests().get(ResourceLocation.parse("example:start/first_furnace")).guide().orElseThrow()), 5);
        expect("the button's page is open", () -> BookScreen.guidePageForTest() == 0);
        expect("the guides have no broken links", () -> BookScreen.guideIssuesForTest().isEmpty());
        step("back to the story", () -> {
            BookScreen.showStoryForTest();
            BookScreen.selectForTest(ResourceLocation.parse("example:01_start"), ResourceLocation.parse("example:start/first_furnace"));
            BookScreen.showForTest(ViewMode.FLOW);
        }, 15);
        expect("the story is open", () -> !BookScreen.inGuidesForTest());
        screenshot("guides_example_flow");
        ResourceLocation furnaceQuest = ResourceLocation.parse("example:start/first_furnace");
        step("press how it works", () -> BookScreen.openRefForTest(ClientBook.current().quests().get(furnaceQuest).guide().orElseThrow()), 10);
        step("pick another page in the list", () -> BookScreen.openGuideForTest(furnace, 1), 10);
        expect("the other page is open", () -> BookScreen.inGuidesForTest() && BookScreen.guidePageForTest() == 1);
        screenshot("guides_back_button");
        step("press Backspace", () -> pressKey(GLFW.GLFW_KEY_BACKSPACE), 10);
        expect("back on the page the quest led to", () -> BookScreen.inGuidesForTest() && BookScreen.guidePageForTest() == 0);
        step("press Backspace again", () -> pressKey(GLFW.GLFW_KEY_BACKSPACE), 10);
        expect("back at the quest", () -> !BookScreen.inGuidesForTest() && furnaceQuest.equals(BookScreen.selectedQuestForTest()));
        step("close the book", () -> pressKey(GLFW.GLFW_KEY_ESCAPE), 8);
    }

    private static boolean isRead(ResourceLocation guide, int page) {
        var found = ClientBook.current().guides().get(guide);
        return ClientProgress.isRead(found, found.pages().get(page));
    }

    /** The guide screens with the contents of their mockups, at the sizes a screen is handed over at. */
    private static void buildGuidesDesignScenario() {
        ResourceLocation create = ResourceLocation.parse("design:create");
        openWorld();
        step("match the window of the mockups", () -> {
            mc().options.guiScale().set(2);
            mc().options.languageCode = "ru_ru";
            mc().getLanguageManager().setSelected("ru_ru");
            mc().resizeDisplay();
            mc().reloadResourcePacks();
        }, 40);
        expect("language applied", () -> mc().getOverlay() == null);
        step("read what the mockup shows as read", () -> {
            sendRead(create, 7);
            sendRead(ResourceLocation.parse("design:farmer"), 6);
            sendRead(ResourceLocation.parse("design:storage"), 4);
        }, 20);
        step("open the book", () -> pressKey(GLFW.GLFW_KEY_K), 30);
        expect("book screen open", () -> mc().screen instanceof BookScreen);
        step("show the library", () -> BookScreen.selectGuideForTest(create), 10);
        screenshot("design_guides");
        step("open a page", () -> BookScreen.openGuideForTest(create, 2), 40);
        screenshot("design_guidepage");
        for (boolean page : List.of(false, true)) {
            for (int[] size : new int[][] {{2560, 1440}, {1920, 1080}}) {
                for (String theme : List.of("slate", "glass")) {
                    step("show " + (page ? "page " : "library ") + size[0] + " " + theme, () -> {
                        mc().getWindow().setWindowed(size[0], size[1]);
                        mc().options.guiScale().set(0);
                        mc().resizeDisplay();
                        ThemeManager.select("ryscore:" + theme);
                        if (page) {
                            BookScreen.openGuideForTest(create, 2);
                        } else {
                            BookScreen.selectGuideForTest(create);
                        }
                    }, 15);
                    screenshot("guides_" + (page ? "page" : "library") + "_" + size[0] + "_" + theme);
                }
            }
        }
        step("back to the size of the mockups", () -> {
            mc().getWindow().setWindowed(1280, 800);
            mc().options.guiScale().set(2);
            mc().resizeDisplay();
            ThemeManager.select("ryscore:slate");
        }, 10);
        step("close the book", () -> pressKey(GLFW.GLFW_KEY_ESCAPE), 8);
    }

    private static final double RAIL_X = 41.5;

    private static double railY(int index) {
        return (37 + 58 + 66 * index + 30) / 2.0;
    }

    /** Custom sections on the example pack, and that a section file can be edited by hand. */
    private static void buildSectionsScenario() {
        ResourceLocation ores = ResourceLocation.parse("example:ores");
        ResourceLocation bosses = ResourceLocation.parse("example:bosses");
        ResourceLocation rules = ResourceLocation.parse("example:rules");
        openWorld();
        step("match the window of the mockups", () -> {
            mc().options.guiScale().set(2);
            mc().resizeDisplay();
        }, 10);
        step("command answers go to chat", () -> command("gamerule sendCommandFeedback true"), 5);
        step("take the stage back", () -> command("advancement revoke @s only minecraft:story/mine_stone"), 120);
        expect("the middle column is closed", () -> !ClientProgress.hasStage("minecraft:story/mine_stone"));
        step("open the book", () -> pressKey(GLFW.GLFW_KEY_K), 30);
        expect("book screen open", () -> mc().screen instanceof BookScreen);
        step("click the matrix on the rail", () -> mc().screen.mouseClicked(RAIL_X, railY(2), GLFW.GLFW_MOUSE_BUTTON_LEFT), 5);
        expect("the matrix is open", () -> ores.toString().equals(BookScreen.currentSectionForTest()));
        screenshot("sections_example_matrix_before");
        step("reach the stage", () -> command("advancement grant @s only minecraft:story/mine_stone"), 120);
        expect("the current column follows the stage", () -> ClientProgress.hasStage("minecraft:story/mine_stone"));
        screenshot("sections_example_matrix");
        step("start with nothing pinned, the world is reused", () -> List.copyOf(ClientProgress.pinnedWays()).forEach(key ->
                PacketDistributor.sendToServer(new works.repka.rysquests.network.PinWayPayload(key))), 10);
        expect("nothing is pinned", () -> ClientProgress.pinnedWays().isEmpty());
        step("pin the way", () -> mc().screen.mouseClicked(509.5, 365, GLFW.GLFW_MOUSE_BUTTON_LEFT), 10);
        expect("the way is pinned", () -> ClientProgress.pinnedWays().size() == 1);
        step("click the cards on the rail", () -> mc().screen.mouseClicked(RAIL_X, railY(3), GLFW.GLFW_MOUSE_BUTTON_LEFT), 5);
        expect("the cards are open", () -> bosses.toString().equals(BookScreen.currentSectionForTest()));
        expect("the condition marks the card", () -> ClientProgress.get(ResourceLocation.parse("example:_cards/bosses/first_stone")).completed());
        screenshot("sections_example_cards");
        step("click the pages on the rail", () -> mc().screen.mouseClicked(RAIL_X, railY(4), GLFW.GLFW_MOUSE_BUTTON_LEFT), 25);
        expect("the pages are open", () -> rules.toString().equals(BookScreen.currentSectionForTest()));
        step("turn the page", () -> pressKey(GLFW.GLFW_KEY_RIGHT), 5);
        expect("the next page is open", () -> BookScreen.sectionPageForTest(rules) == 1);
        screenshot("sections_example_pages");
        expect("the sections have no broken links", () -> BookScreen.guideIssuesForTest().isEmpty());
        step("write a section file by hand", () -> writeLiveSection("Before"), 1);
        step("let the game find the pack", () -> command("reload"), 40);
        step("switch it on", () -> command("datapack enable \"file/autotest_section\""), 40);
        expect("the file is in the book", () -> liveTitle().equals("Before"));
        step("edit the file", () -> writeLiveSection("After"), 1);
        step("reload the book", () -> command("rysquests reload"), 40);
        expect("the edit is in the book", () -> liveTitle().equals("After"));
        step("close the book", () -> pressKey(GLFW.GLFW_KEY_ESCAPE), 8);
        expect("book closed", () -> mc().screen == null);
        step("wait for the board", () -> {}, 5);
        screenshot("sections_example_hud");
    }

    private static String liveTitle() {
        var section = ClientBook.current().sections().get(ResourceLocation.parse("autotest:live"));
        return section == null ? "" : section.title();
    }

    private static void writeLiveSection(String title) {
        try {
            java.nio.file.Path pack = mc().getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.DATAPACK_DIR).resolve("autotest_section");
            java.nio.file.Path file = pack.resolve("data/autotest/rysquests/sections/live.json");
            java.nio.file.Files.createDirectories(file.getParent());
            java.nio.file.Files.writeString(pack.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":48,\"description\":\"autotest\"}}");
            java.nio.file.Files.writeString(file, "{\"template\":\"pages\",\"title\":\"" + title + "\",\"pages\":[{\"id\":\"a\",\"title\":\"A\",\"text\":\"x\"}]}");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Chapters of the RepYS book on the map, whole and at full size, to judge the automatic layout. */
    private static void buildLayoutScenario() {
        String tag = System.getProperty("rysquests.autotest.tag", "");
        openWorld();
        step("match the window of the mockups", () -> {
            mc().options.guiScale().set(2);
            mc().options.languageCode = "ru_ru";
            mc().getLanguageManager().setSelected("ru_ru");
            mc().resizeDisplay();
            mc().reloadResourcePacks();
        }, 40);
        expect("language applied", () -> mc().getOverlay() == null);
        expect("the RepYS book is loaded", () -> ClientBook.current().chapters().containsKey(ResourceLocation.parse("repys:b00_e1_andesite")));
        List<String> chapters = List.of("a01_map", "a2_chronicle", "b09_e4_logistics", "b01_e1_brass", "b00_e1_andesite");
        // Hidden quests show once their dependencies are done: everything is done, and unfolded below, so that all of it is laid out.
        step("finish every quest of the chapters", () -> {
            command("gamerule sendCommandFeedback false");
            ClientBook.current().quests().values().stream()
                    .filter(quest -> quest.chapter().filter(id -> chapters.contains(id.getPath())).isPresent())
                    .forEach(quest -> command("rysquests progress @s complete " + quest.id()));
        }, 80);
        step("open the book on the map", () -> {
            BookScreen.showStoryForTest();
            BookScreen.showForTest(ViewMode.MAP);
            pressKey(GLFW.GLFW_KEY_K);
        }, 30);
        expect("book screen open", () -> mc().screen instanceof BookScreen);
        step("unfold everything", () -> BookScreen.mapUnfoldForTest(true), 5);
        for (String mode : List.of("before", "after")) {
            step("lay out as " + mode, () -> BookScreen.mapLegacyLayoutForTest(mode.equals("before")), 1);
            for (String chapter : chapters) {
                ResourceLocation id = ResourceLocation.parse("repys:" + chapter);
                step("open " + chapter + " " + mode, () -> {
                    BookScreen.selectForTest(id, null);
                    BookScreen.mapZoomForTest(1f);
                }, 40);
                step("log " + chapter + " " + mode, () -> LOGGER.info("[autotest] layout {} {}: {}", mode, chapter, BookScreen.mapStatsForTest()), 1);
                screenshot("layout_" + chapter + "_full_" + mode + tag);
                step("fit " + chapter + " " + mode, BookScreen::mapFitForTest, 10);
                screenshot("layout_" + chapter + "_whole_" + mode + tag);
            }
        }
        step("back to the new layout", () -> BookScreen.mapLegacyLayoutForTest(false), 1);
        // The epoch quest of the author's picture: experience, coins, a choice of four and one more item.
        step("open the epoch quest", () -> BookScreen.selectForTest(ResourceLocation.parse("repys:a01_map"), ResourceLocation.parse("repys:a01_map/iii_machines")), 30);
        screenshot("layout_rewards" + tag);
        step("open andesite again", () -> BookScreen.selectForTest(ResourceLocation.parse("repys:b00_e1_andesite"), null), 20);
        ResourceLocation open = ResourceLocation.parse("repys:b00_e1_andesite");
        step("hold the chapter list and pull it up", () -> {
            mc().screen.mouseClicked(120, 250, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            mc().screen.mouseDragged(120, 200, GLFW.GLFW_MOUSE_BUTTON_LEFT, 0, -50);
            mc().screen.mouseDragged(120, 150, GLFW.GLFW_MOUSE_BUTTON_LEFT, 0, -50);
            mc().screen.mouseReleased(120, 150, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        }, 10);
        expect("the list scrolled and no chapter opened", () -> BookScreen.chapterScrollForTest() > 0 && open.equals(BookScreen.chapterForTest()));
        step("click a chapter", () -> {
            mc().screen.mouseClicked(120, 250, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            mc().screen.mouseReleased(120, 250, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        }, 10);
        step("log the click", () -> LOGGER.info("[autotest] after the click: chapter {}, list at {}", BookScreen.chapterForTest(), BookScreen.chapterScrollForTest()), 1);
        expect("the click opened another chapter", () -> !open.equals(BookScreen.chapterForTest()));
        step("close the book", () -> pressKey(GLFW.GLFW_KEY_ESCAPE), 8);
    }

    /** The custom sections with the contents of their mockups. */
    private static void buildSectionsDesignScenario() {
        openWorld();
        step("match the window of the mockups", () -> {
            mc().options.guiScale().set(2);
            mc().options.languageCode = "ru_ru";
            mc().getLanguageManager().setSelected("ru_ru");
            mc().resizeDisplay();
            mc().reloadResourcePacks();
        }, 40);
        expect("language applied", () -> mc().getOverlay() == null);
        step("reach the middle stage", () -> command("advancement grant @s only minecraft:story/mine_stone"), 120);
        step("have the coins of the mockup", () -> {
            if (ClientShop.balance("coin") == 0) {
                command("rysquests wallet add @s coin 248");
            }
        }, 20);
        step("have the items of the achievements mockup", () -> {
            command("clear @s");
            command("give @s minecraft:cobblestone 312");
            command("give @s minecraft:dirt 440");
            command("give @s minecraft:nether_star 1");
            command("give @s minecraft:amethyst_shard 2");
            command("give @s minecraft:sand 18");
            command("give @s minecraft:emerald 1");
            command("give @s minecraft:redstone 1");
        }, 60);
        step("open the book", () -> pressKey(GLFW.GLFW_KEY_K), 30);
        expect("book screen open", () -> mc().screen instanceof BookScreen);
        for (String[] view : new String[][] {{"design:mining", "design_mining"}, {"design:atlas", "design_atlas"}, {"design:bosses", "design_cards"}, {"design:rules", "design_pages"}, {"shop", "design_shop"}, {"achievements", "design_achievements"}}) {
            step("show " + view[0], () -> {
                BookScreen.showSectionForTest(view[0]);
                BookScreen.achievementSelectForTest(ResourceLocation.parse("design:factory"));
            }, 40);
            screenshot(view[1]);
            for (int[] size : new int[][] {{2560, 1440}, {1920, 1080}}) {
                for (String theme : List.of("slate", "glass")) {
                    step("show " + view[0] + " " + size[0] + " " + theme, () -> {
                        mc().getWindow().setWindowed(size[0], size[1]);
                        mc().options.guiScale().set(0);
                        mc().resizeDisplay();
                        ThemeManager.select("ryscore:" + theme);
                    }, 15);
                    screenshot("sections_" + view[1] + "_" + size[0] + "_" + theme);
                }
            }
            // GUI scale 3 puts mockup pixels on half screen pixels, where shapes that round apart drift.
            step("show " + view[0] + " at gui 3", () -> {
                mc().getWindow().setWindowed(1920, 1080);
                mc().options.guiScale().set(3);
                mc().resizeDisplay();
                ThemeManager.select("ryscore:slate");
            }, 15);
            screenshot("sections_" + view[1] + "_gui3");
            step("back to the size of the mockups", () -> {
                mc().getWindow().setWindowed(1280, 800);
                mc().options.guiScale().set(2);
                mc().resizeDisplay();
                ThemeManager.select("ryscore:slate");
            }, 10);
        }
        step("open the constructor on the mining atlas", () -> {
            BookScreen.openConstructorOnForTest(ResourceLocation.parse("design:mining"));
            BookScreen.selectWayForTest(0, 1, 0);
        }, 20);
        screenshot("design_constructor");
        step("leave the constructor", () -> pressKey(GLFW.GLFW_KEY_ESCAPE), 8);
        step("close the book", () -> pressKey(GLFW.GLFW_KEY_ESCAPE), 8);
    }

    private static byte[] testPicture() {
        try (NativeImage image = new NativeImage(16, 9, false)) {
            image.fillRect(0, 0, 16, 9, 0xFF3366AA);
            return image.asByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final ResourceLocation TEAM_IMPORTED = ResourceLocation.parse("teamtest:imported");

    /** The format FTB Quests writes, for a party whose second member took a reward. Only the dedicated server's folder is touched. */
    private static void writeFtbqPartyFile() {
        try {
            Path server = mc().gameDirectory.toPath().resolve("../run-server");
            java.util.Properties properties = new java.util.Properties();
            try (var reader = Files.newBufferedReader(server.resolve("server.properties"))) {
                properties.load(reader);
            }
            String team = mc().player.getUUID().toString().replace("-", "");
            String other = mc().getConnection().getPlayerInfo("TestB").getProfile().getId().toString().replace("-", "");
            Path saves = server.resolve(properties.getProperty("level-name")).resolve("ftbquests");
            Files.createDirectories(saves);
            Files.writeString(saves.resolve(team + ".snbt"), "{\n\tversion: 1\n\tuuid: \"" + team + "\"\n\tcompleted: {\n\t\tAAAAAAAAAAAAAAAA: 1793285774000L\n\t}\n\tclaimed_rewards: {\n\t\t\"" + other + ":BBBBBBBBBBBBBBBB\": 1793285774481L\n\t}\n}\n");
            Path ids = server.resolve("config/rysquests/import/ftbq-ids.json");
            Files.createDirectories(ids.getParent());
            Files.writeString(ids, "{\"quests\":{\"AAAAAAAAAAAAAAAA\":\"teamtest:imported\"},\"tasks\":{},\"rewards\":{\"BBBBBBBBBBBBBBBB\":{\"quest\":\"teamtest:imported\",\"index\":0}}}");
        } catch (IOException e) {
            fail("could not write the FTB file: " + e);
        }
    }

    private static final ResourceLocation TEAM_BREAD = ResourceLocation.parse("teamtest:bread");
    private static final ResourceLocation TEAM_ONCE = ResourceLocation.parse("teamtest:once");
    private static final ResourceLocation TEAM_WHEAT = ResourceLocation.parse("teamtest:wheat");
    private static final ResourceLocation TEAM_ACHIEVEMENT = ResourceLocation.parse("teamtest:_achievements/shared/gold");
    private static final String TEAM_TITLE = "title.teamtest.shared";

    /** The first player buys and sells, and the report catches the pair of trades that prints money. */
    private static void addShopOnServerSteps() {
        step("give both players coins", () -> {
            command("rysquests wallet add TestA coin 100");
            command("rysquests wallet add TestB coin 100");
            command("give TestA minecraft:wheat 16");
        }, 20);
        expect("coins arrived", () -> ClientShop.balance("coin") == 100);
        step("buy bread", () -> trade(TEAM_BREAD), 15);
        expect("bread was paid for", () -> ClientShop.balance("coin") == 95 && mc().player.getInventory().countItem(net.minecraft.world.item.Items.BREAD) >= 4);
        step("buy the one for the team", () -> trade(TEAM_ONCE), 15);
        expect("team limit is used", () -> ClientShop.used(TEAM_ONCE) == 1);
        step("sell wheat", () -> trade(TEAM_WHEAT), 15);
        expect("the sale paid", () -> ClientShop.balance("coin") == 93);
        step("ask for the reports", () -> PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.DEV_REPORT, QuestActionPayload.ALL, 0, List.of())), 20);
        expect("the report caught the printing pair", () -> ClientDev.report() != null && ClientDev.report().sections().stream().anyMatch(section -> section.id().equals("shop_cycles") && section.entries().size() == 1));
        step("reach an achievement of the team", () -> command("give TestA minecraft:diamond 1"), 20);
        expect("the achievement is reached on the server", () -> ClientProgress.get(TEAM_ACHIEVEMENT).completed() && ClientAchievements.owns(TEAM_TITLE));
    }

    private static void addShopMemberSteps() {
        expect("coins reached the other player", () -> ClientShop.balance("coin") == 100);
        expect("the team limit is shared", () -> ClientShop.used(TEAM_ONCE) == 1);
        step("try the team limit", () -> {
            BookScreen.clearShopResultForTest();
            trade(TEAM_ONCE);
        }, 15);
        expect("the server refuses the second member", () -> "rysquests.shop.error.limit".equals(BookScreen.lastShopResultForTest()));
        step("buy for oneself", () -> trade(TEAM_BREAD), 15);
        expect("a personal limit is personal", () -> ClientShop.used(TEAM_BREAD) == 1 && ClientShop.balance("coin") == 95);
        expect("the achievement of the team reached the other player", () -> ClientProgress.get(TEAM_ACHIEVEMENT).completed() && ClientAchievements.owns(TEAM_TITLE));
    }

    /** The operator writes a guide on a dedicated server. The other client checks what reached it. */
    private static void addEditorOnServerSteps() {
        step("open the book", () -> mc().setScreen(new BookScreen()), 10);
        step("write a guide", () -> {
            BookScreen.editGuideForTest(SERVER_GUIDE);
            BookScreen.writeGuideForTest("Written on the server", ResourceLocation.parse("minecraft:book"));
            BookScreen.addImageForTest(SERVER_IMAGE, testPicture());
            BookScreen.writePageForTest("First", "Picture below\n[[image:" + SERVER_IMAGE + "]]");
        }, 5);
        step("save it", BookScreen::editorSaveForTest, 5);
        expect("the server saved it", BookScreen::editorSavedForTest);
        expect("the file is on the server", () -> Files.isRegularFile(mc().gameDirectory.toPath().resolve("../run-server/config/rysquests/pack/data/teamtest/rysquests/guides/editor_guide.json")));
        step("make a section on the server", () -> {
            BookScreen.openConstructorForTest();
            BookScreen.newMatrixForTest();
            BookScreen.buildMatrixForTest("Server atlas", List.of("Iron"), List.of("Early", "Middle", "Late"), ResourceLocation.parse("minecraft:iron_ingot"));
        }, 5);
        step("save the section", BookScreen::constructorSaveForTest, 5);
        expect("the server saved the section", BookScreen::constructorSavedForTest);
        step("leave the constructor for the quest editor", () -> mc().setScreen(new BookScreen()), 5);
        step("make a chapter on the server", () -> {
            BookScreen.openQuestEditorForTest();
            BookScreen.questNewChapterForTest("Server chapter");
            BookScreen.questAddForTest("Server quest");
            BookScreen.questSaveForTest();
        }, 40);
        expect("the server saved the chapter", () -> !BookScreen.questDirtyForTest());
        expect("the chapter file is on the server", () -> Files.isRegularFile(mc().gameDirectory.toPath().resolve("../run-server/config/rysquests/pack/data/" + BookScreen.questChapterForTest().getNamespace() + "/rysquests/chapters/" + BookScreen.questChapterForTest().getPath() + "/_chapter.json")));
        step("close the book", () -> mc().setScreen(null), 5);
    }

    private static final ResourceLocation SHOP_IRON = ResourceLocation.parse("example:iron");
    private static final ResourceLocation SHOP_WHEAT = ResourceLocation.parse("example:wheat");

    private static void trade(ResourceLocation offer) {
        PacketDistributor.sendToServer(new ShopTradePayload(offer, 1, false));
    }

    /** Buying and selling, and that leaving the world does not bring a used limit back. */
    private static void buildShopScenario() {
        openWorld();
        step("match the window of the mockups", () -> {
            mc().options.guiScale().set(2);
            mc().resizeDisplay();
        }, 10);
        step("get coins and wheat", () -> {
            command("rysquests wallet add @s coin 100");
            command("give @s minecraft:wheat 16");
            command("clear @s minecraft:iron_ingot");
        }, 20);
        expect("the coins arrived", () -> ClientShop.balance("coin") >= 100);
        step("open the book on the shop", () -> {
            mc().setScreen(new BookScreen());
            BookScreen.showSectionForTest("shop");
        }, 20);
        expect("the shop is open", () -> "shop".equals(BookScreen.currentSectionForTest()));
        screenshot("shop_example");
        long[] before = new long[1];
        step("use up the limit", () -> {
            before[0] = ClientShop.balance("coin");
            trade(SHOP_IRON);
            trade(SHOP_IRON);
        }, 20);
        expect("the limit is used up", () -> ClientShop.used(SHOP_IRON) == 2);
        step("try once more", () -> {
            BookScreen.clearShopResultForTest();
            trade(SHOP_IRON);
        }, 10);
        expect("the server refuses", () -> "rysquests.shop.error.limit".equals(BookScreen.lastShopResultForTest()));
        step("sell the wheat", () -> trade(SHOP_WHEAT), 15);
        expect("the sale paid", () -> mc().player.getInventory().countItem(net.minecraft.world.item.Items.WHEAT) == 0);
        step("show the history", BookScreen::shopHistoryForTest, 10);
        screenshot("shop_example_history");
        step("leave the world", () -> {
            mc().setScreen(null);
            mc().level.disconnect();
            mc().disconnect(new TitleScreen());
        }, 40);
        openWorld();
        expect("the shop state came back", () -> ClientShop.balance("coin") > 0);
        expect("the limit is still used up", () -> ClientShop.used(SHOP_IRON) == 2);
        step("try after coming back", () -> {
            BookScreen.clearShopResultForTest();
            trade(SHOP_IRON);
        }, 10);
        expect("the server still refuses", () -> "rysquests.shop.error.limit".equals(BookScreen.lastShopResultForTest()));
    }

    private static final String[][] QUEST_EDITOR_NODES = {
            {"Латунь", "minecraft:gold_ingot", "0", "24"}, {"Шестерни", "minecraft:oak_planks", "0", "120"}, {"Провода", "minecraft:copper_ingot", "168", "24"}, {"Динамо", "minecraft:iron_block", "168", "120"},
            {"Электрический механизм", "minecraft:clock", "336", "72"}, {"Первая машина", "minecraft:emerald_block", "384", "168"}, {"Солнечные панели", "minecraft:daylight_detector", "384", "312"},
            {"Насос и трубы", "minecraft:lapis_block", "0", "240"}, {"Котёл", "minecraft:furnace", "168", "240"}, {"Турбина", "minecraft:piston", "384", "240"}};

    private static void click(double x, double y) {
        mc().screen.mouseClicked(x / 2, y / 2, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    private static void dragTo(double x, double y) {
        mc().screen.mouseDragged(x / 2, y / 2, GLFW.GLFW_MOUSE_BUTTON_LEFT, 0, 0);
    }

    private static void release(double x, double y) {
        mc().screen.mouseReleased(x / 2, y / 2, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    /** The acceptance of the quest editor: a chapter of ten quests with dependencies, made in the game, read back from its files. */
    private static void buildQuestEditorScenario() {
        List<ResourceLocation> ids = new ArrayList<>();
        ResourceLocation[] chapter = new ResourceLocation[1];
        openWorld();
        step("match the window of the mockups", () -> {
            mc().options.guiScale().set(2);
            mc().options.languageCode = "ru_ru";
            mc().getLanguageManager().setSelected("ru_ru");
            mc().resizeDisplay();
            mc().reloadResourcePacks();
        }, 40);
        expect("language applied", () -> mc().getOverlay() == null);
        step("open the book", () -> mc().setScreen(new BookScreen()), 20);
        step("open the quest editor", BookScreen::openQuestEditorForTest, 10);
        expect("the editor is open", BookScreen::questEditorOpenForTest);
        step("make a chapter of ten quests", () -> {
            BookScreen.questNewChapterForTest("Индустрия и ток");
            BookScreen.questEpochForTest(2);
            chapter[0] = BookScreen.questChapterForTest();
            for (String[] node : QUEST_EDITOR_NODES) {
                ResourceLocation id = BookScreen.questAddForTest(node[0]);
                ids.add(id);
                BookScreen.questMoveForTest(id, Integer.parseInt(node[2]), Integer.parseInt(node[3]));
                BookScreen.questItemTaskForTest(ResourceLocation.parse(node[1]), 3);
                BookScreen.questCoinRewardForTest(5);
            }
        }, 10);
        step("link most of them", () -> {
            BookScreen.questLinkForTest(ids.get(0), ids.get(2));
            BookScreen.questLinkForTest(ids.get(1), ids.get(3));
            BookScreen.questLinkForTest(ids.get(2), ids.get(4));
            BookScreen.questLinkForTest(ids.get(3), ids.get(4));
            BookScreen.questLinkForTest(ids.get(7), ids.get(8));
            BookScreen.questLinkForTest(ids.get(8), ids.get(9));
            BookScreen.questSelectForTest(ids.get(4));
        }, 10);
        expect("a circle is refused", () -> {
            BookScreen.questLinkForTest(ids.get(4), ids.get(0));
            return BookScreen.questDraftForTest(ids.get(0)).requires().isEmpty() && BookScreen.questStatusForTest() != null;
        });
        step("pull a dependency with the mouse, and hold it", () -> {
            float[] node = BookScreen.questRectForTest(ids.get(4));
            float[] target = BookScreen.questRectForTest(ids.get(5));
            click(node[0] + node[2] + 12, node[1] + node[3] / 2f);
            dragTo(target[0] - 8, target[1] + 22);
        }, 4);
        screenshot("design_quest_editor");
        step("drop it on the quest", () -> {
            float[] target = BookScreen.questRectForTest(ids.get(5));
            dragTo(target[0] + 20, target[1] + 20);
            release(target[0] + 20, target[1] + 20);
        }, 4);
        expect("the mouse added the dependency", () -> BookScreen.questDraftForTest(ids.get(5)).requires().contains(ids.get(4)));
        step("drag a quest with the mouse", () -> {
            float[] node = BookScreen.questRectForTest(ids.get(6));
            click(node[0] + 30, node[1] + 15);
            dragTo(node[0] + 30 + 48, node[1] + 15 + 24);
            release(node[0] + 30 + 48, node[1] + 15 + 24);
        }, 4);
        expect("it moved along the grid", () -> BookScreen.questDraftForTest(ids.get(6)).options().layout().x().orElse(0) == 432 && BookScreen.questDraftForTest(ids.get(6)).options().layout().y().orElse(0) == 336);
        step("show the json tab", () -> {
            BookScreen.questSelectForTest(ids.get(4));
            BookScreen.questTabForTest("JSON");
        }, 4);
        screenshot("quest_editor_json");
        step("back to properties and save", () -> {
            BookScreen.questTabForTest("PROPS");
            BookScreen.questSaveForTest();
        }, 40);
        expect("the save went through", () -> !BookScreen.questDirtyForTest());
        expect("the chapter is in the book", () -> ClientBook.current().chapters().containsKey(chapter[0]));
        expect("all ten are in the book", () -> ids.stream().allMatch(ClientBook.current().quests()::containsKey));
        Path[] file = new Path[1];
        expect("the files match the format", () -> {
            Path folder = mc().gameDirectory.toPath().resolve("config/rysquests/pack/data/" + chapter[0].getNamespace() + "/rysquests/chapters/" + chapter[0].getPath());
            file[0] = folder.resolve("quest_5.json");
            try {
                String text = Files.readString(file[0]);
                var json = com.google.gson.JsonParser.parseString(text).getAsJsonObject();
                List<String> order = new ArrayList<>(json.keySet());
                var quest = works.repka.rysquests.data.Quest.CODEC.parse(net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, mc().level.registryAccess()), json).getOrThrow();
                return Files.isRegularFile(folder.resolve("_chapter.json"))
                        && order.indexOf("id") == 0 && order.indexOf("title") < order.indexOf("tasks") && order.indexOf("tasks") < order.indexOf("rewards")
                        && json.get("tasks").getAsJsonArray().get(0).getAsJsonObject().keySet().iterator().next().equals("type")
                        && quest.requires().size() == 2 && quest.tasks().size() == 1 && quest.rewards().size() == 1
                        && Files.readString(mc().gameDirectory.toPath().resolve("config/rysquests/pack/assets/" + chapter[0].getNamespace() + "/lang/ru_ru.json")).contains("Электрический механизм");
            } catch (java.io.IOException e) {
                return false;
            }
        });
        step("change a file outside the game", () -> {
            try {
                Files.writeString(file[0], Files.readString(file[0]).replace("\"chapter\"", "\"icon\": \"minecraft:diamond\",\n  \"chapter\""));
            } catch (java.io.IOException e) {
                fail("could not change the file: " + e);
            }
            command("reload");
        }, 60);
        expect("the map followed the file", () -> BookScreen.questDraftForTest(ids.get(4)).icon().filter(ResourceLocation.parse("minecraft:diamond")::equals).isPresent() && !BookScreen.questDirtyForTest());
        step("look at the highlight", () -> BookScreen.questSelectForTest(ids.get(0)), 10);
        screenshot("quest_editor_external");
        for (int[] size : new int[][] {{2560, 1440}, {1920, 1080}}) {
            for (String theme : List.of("slate", "glass")) {
                step("show the editor " + size[0] + " " + theme, () -> {
                    mc().getWindow().setWindowed(size[0], size[1]);
                    mc().options.guiScale().set(0);
                    mc().resizeDisplay();
                    ThemeManager.select("ryscore:" + theme);
                }, 15);
                screenshot("quest_editor_" + size[0] + "_" + theme);
            }
        }
        step("back to the size of the mockups", () -> {
            mc().getWindow().setWindowed(1280, 800);
            mc().options.guiScale().set(2);
            mc().resizeDisplay();
            ThemeManager.select("ryscore:slate");
        }, 15);
        step("leave the editor", () -> pressKey(GLFW.GLFW_KEY_ESCAPE), 10);
        expect("the editor closed", () -> !BookScreen.questEditorOpenForTest());
        step("take the chapter away, other scenarios count the quests of the example", () -> {
            try {
                Path game = mc().gameDirectory.toPath();
                Files.createDirectories(game.resolve("_stale"));
                Files.move(file[0].getParent(), game.resolve("_stale/quest_editor_" + System.currentTimeMillis()));
            } catch (java.io.IOException e) {
                fail("could not move the chapter: " + e);
            }
            command("reload");
        }, 60);
        expect("the example is back to its own quests", () -> ClientBook.current().quests().size() == EXAMPLE_QUESTS);
    }

    /** Laid out and formatted as the book of a real pack. Only the test folder is touched. */
    private static void writePatchouliBook() {
        try {
            Path book = mc().gameDirectory.toPath().resolve("patchouli_books/testbook/en_us");
            Files.createDirectories(book.resolve("categories"));
            Files.createDirectories(book.resolve("entries"));
            Files.writeString(book.resolveSibling("book.json"), "{ \"name\": \"Test book\", \"landing_text\": \"Test\" }");
            Files.writeString(book.resolve("categories/machines.json"), "{ \"name\": \"Machines\", \"description\": \"All of them\", \"icon\": \"minecraft:clock\" }");
            Files.writeString(book.resolve("entries/tower.json"), "{ \"name\": \"Tower\", \"icon\": \"minecraft:stone\", \"category\": \"patchouli:machines\", \"pages\": [ { \"type\": \"patchouli:text\", \"text\": \"$(l)Tower$()$(br)Tall.\" }, { \"type\": \"patchouli:multiblock\", \"name\": \"Tower\", \"multiblock\": { \"mapping\": { \"S\": \"minecraft:stone\", \"0\": \"minecraft:iron_block\" }, \"pattern\": [ [ \"S\" ], [ \"0\" ] ] } } ] }");
        } catch (IOException e) {
            fail("could not write the book: " + e);
        }
    }

    private static net.minecraft.world.item.ItemStack integrationBrass() {
        return new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("create:brass_ingot")));
    }

    /** Recipes drawn by JEI and a scene of Ponder, with both really loaded from run-int/mods. */
    private static void buildIntegrationsScenario() {
        ResourceLocation guide = ResourceLocation.parse("inttest:machines");
        ResourceLocation gearbox = ResourceLocation.parse("create:gearbox");
        openWorld();
        step("match the window of the mockups", () -> {
            mc().options.guiScale().set(2);
            mc().resizeDisplay();
        }, 10);
        expect("JEI is ready", works.repka.rysquests.client.integration.Recipes::available);
        expect("JEI draws the machine", () -> works.repka.rysquests.client.integration.Recipes.layout(integrationBrass(), ResourceLocation.parse("create:mixing")) != null);
        expect("JEI draws a plain recipe", () -> works.repka.rysquests.client.integration.Recipes.layout(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FURNACE), null) != null);
        expect("an unknown category finds nothing", () -> works.repka.rysquests.client.integration.Recipes.layout(integrationBrass(), ResourceLocation.parse("create:nothing")) == null);
        expect("Ponder knows the scene", () -> works.repka.rysquests.client.integration.Ponders.exists(gearbox));
        expect("an unknown scene is told apart", () -> !works.repka.rysquests.client.integration.Ponders.exists(ResourceLocation.parse("create:nothing")));
        step("open the page", () -> {
            mc().setScreen(new BookScreen());
            BookScreen.openGuideForTest(guide, 0);
        }, 40);
        expect("the page is open", () -> BookScreen.guidePageForTest() == 0);
        expect("the structure is ready", () -> BookScreen.structureReadyForTest(ResourceLocation.parse("inttest:hall")));
        step("look at the structure", () -> {
            for (int i = 0; i < 3; i++) {
                mc().screen.mouseScrolled(380, 200, 0, -3);
            }
        }, 30);
        screenshot("integrations_page");
        step("show the bottom layer", () -> BookScreen.structureLayerForTest(ResourceLocation.parse("inttest:hall")), 30);
        screenshot("integrations_layer");
        step("turn it with the mouse", () -> {
            mc().screen.mouseClicked(312, 270, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            mc().screen.mouseDragged(352, 270, GLFW.GLFW_MOUSE_BUTTON_LEFT, 40, 0);
            mc().screen.mouseReleased(352, 270, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        }, 10);
        step("open the block list", () -> mc().screen.mouseClicked(384, 356, GLFW.GLFW_MOUSE_BUTTON_LEFT), 10);
        screenshot("integrations_turned");
        step("show the hologram", () -> works.repka.rysquests.client.integration.Multiblocks.show(ClientBook.current().structures().get(ResourceLocation.parse("inttest:hall")), Component.literal("Hall")), 10);
        expect("Patchouli holds the hologram", () -> vazkii.patchouli.api.PatchouliAPI.get().getCurrentMultiblock() != null);
        step("take the hologram away", () -> vazkii.patchouli.api.PatchouliAPI.get().clearMultiblock(), 5);
        step("write a book of Patchouli", AutoTest::writePatchouliBook, 2);
        step("import it", () -> command("rysquests import patchouli testbook inttest"), 100);
        expect("the structure of the book arrived", () -> ClientBook.current().structures().containsKey(ResourceLocation.parse("inttest:testbook/tower")));
        expect("its guide arrived", () -> ClientBook.current().guides().containsKey(ResourceLocation.parse("inttest:testbook/machines")));
        step("back on the page", () -> {
            mc().setScreen(new BookScreen());
            BookScreen.openGuideForTest(guide, 0);
        }, 40);
        step("open the scene", () -> works.repka.rysquests.client.integration.Ponders.open(gearbox), 40);
        expect("Ponder is on screen", () -> mc().screen != null && mc().screen.getClass().getSimpleName().equals("PonderUI"));
        screenshot("integrations_ponder");
        step("come back", () -> pressKey(GLFW.GLFW_KEY_ESCAPE), 60);
        expect("back in the book", () -> mc().screen instanceof BookScreen);
    }

    /** Commands of the wallet, and a real click on the exchanger of examples/kubejs with KubeJS running. */
    private static void buildKubeJsScenario() {
        long[] coins = new long[1];
        step("put the example script where KubeJS looks", () -> {
            try {
                Path game = mc().gameDirectory.toPath();
                Path target = game.resolve("kubejs/server_scripts/rysquests_coin_exchange.js");
                Files.createDirectories(target.getParent());
                Files.copy(game.resolve("../examples/kubejs/server_scripts/rysquests_coin_exchange.js"), target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.io.IOException e) {
                fail("could not copy the script: " + e);
            }
        }, 1);
        openWorld();
        step("set the balance", () -> command("rysquests wallet set @s coin 50"), 20);
        expect("set works", () -> ClientShop.balance("coin") == 50);
        step("add", () -> command("rysquests wallet add @s coin 7"), 20);
        expect("add works", () -> ClientShop.balance("coin") == 57);
        step("take", () -> command("rysquests wallet take @s coin 40"), 20);
        expect("take works", () -> ClientShop.balance("coin") == 17);
        step("take too much", () -> command("rysquests wallet take @s coin 40"), 20);
        expect("taking too much changes nothing", () -> ClientShop.balance("coin") == 17);
        step("give a title", () -> command("rysquests title give @s test.title"), 20);
        expect("the title is owned", () -> ClientAchievements.owns("test.title"));
        step("take the title", () -> command("rysquests title take @s test.title"), 20);
        expect("the title is gone", () -> !ClientAchievements.owns("test.title"));
        step("use a limit", () -> {
            command("give @s minecraft:wheat 16");
            command("rysquests wallet add @s coin 100");
            command("rysquests shop reset @s");
        }, 20);
        step("buy", () -> trade(SHOP_IRON), 20);
        expect("the limit is used", () -> ClientShop.used(SHOP_IRON) == 1);
        step("reset the limits", () -> command("rysquests shop reset @s example:iron"), 20);
        expect("the limit is free", () -> ClientShop.used(SHOP_IRON) == 0);
        step("reset the achievement", () -> command("rysquests achievement reset @s example:chicken_hunter"), 20);
        expect("it is open", () -> !ClientProgress.get(ACH_CHICKEN).completed());
        step("complete the achievement", () -> command("rysquests achievement complete @s example:chicken_hunter"), 20);
        expect("it is complete and paid", () -> claimed(ACH_CHICKEN, 1));
        step("get old coins and stand on the exchanger", () -> {
            command("clear @s");
            command("setblock ~ ~-1 ~ minecraft:lodestone");
            command("give @s minecraft:gold_nugget 10");
            mc().player.getInventory().selected = 0;
        }, 30);
        expect("the coins are in the hand", () -> mc().player.getMainHandItem().is(net.minecraft.world.item.Items.GOLD_NUGGET));
        step("right click the exchanger", () -> {
            coins[0] = ClientShop.balance("coin");
            var pos = mc().player.blockPosition().below();
            mc().gameMode.useItemOn(mc().player, net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos).add(0, 0.5, 0), net.minecraft.core.Direction.UP, pos, false));
        }, 30);
        expect("three batches were paid", () -> ClientShop.balance("coin") == coins[0] + 3);
        expect("what makes no batch stayed", () -> mc().player.getInventory().countItem(net.minecraft.world.item.Items.GOLD_NUGGET) == 1);
        expect("the history says exchange", () -> "rysquests:exchange".equals(ClientShop.history().getFirst().ref()));
    }

    private static final ResourceLocation ACH_IRON_BRONZE = ResourceLocation.parse("example:_achievements/iron_hoard/bronze");
    private static final ResourceLocation ACH_IRON_SILVER = ResourceLocation.parse("example:_achievements/iron_hoard/silver");
    private static final ResourceLocation ACH_TIME_BRONZE = ResourceLocation.parse("example:_achievements/old_timer/bronze");
    private static final ResourceLocation ACH_CHICKEN = ResourceLocation.parse("example:_achievements/chicken_hunter/gold");
    private static final String ACH_TITLE = "achievement.example.iron_hoard.title";

    private static boolean claimed(ResourceLocation quest, int rewards) {
        return ClientProgress.get(quest).completed() && ClientProgress.get(quest).claimed().size() == rewards;
    }

    /** The acceptance of achievements: three of them from the example, one hidden, on the example pack. */
    private static void buildAchievementsScenario() {
        long[] coins = new long[1];
        openWorld();
        step("match the window of the mockups", () -> {
            mc().options.guiScale().set(2);
            mc().resizeDisplay();
        }, 10);
        step("start from nothing", () -> {
            command("clear @s minecraft:iron_ingot");
            command("kill @e[type=minecraft:chicken]");
            for (ResourceLocation quest : List.of(ACH_IRON_BRONZE, ACH_IRON_SILVER, ACH_CHICKEN)) {
                command("rysquests progress @s reset " + quest);
            }
        }, 30);
        expect("nothing is reached", () -> !ClientProgress.get(ACH_IRON_BRONZE).completed() && !ClientProgress.get(ACH_CHICKEN).completed());
        step("open the book on achievements", () -> {
            mc().setScreen(new BookScreen());
            BookScreen.showSectionForTest("achievements");
            coins[0] = ClientShop.balance("coin");
        }, 20);
        expect("achievements are open", () -> "achievements".equals(BookScreen.currentSectionForTest()));
        screenshot("achievements_hidden");
        step("get the bronze of iron", () -> command("give @s minecraft:iron_ingot 8"), 40);
        expect("bronze is reached and given without a click", () -> claimed(ACH_IRON_BRONZE, 1) && ClientShop.balance("coin") == coins[0] + 5);
        expect("silver goes on counting", () -> ClientProgress.get(ACH_IRON_SILVER).task(0) == 8);
        step("get the silver of iron", () -> command("give @s minecraft:iron_ingot 24"), 40);
        expect("the title arrived", () -> claimed(ACH_IRON_SILVER, 1) && ClientAchievements.owns(ACH_TITLE));
        expect("playing time counted a tier", () -> ClientProgress.get(ACH_TIME_BRONZE).completed());
        step("defeat chickens", () -> {
            for (int i = 0; i < 3; i++) {
                command("summon minecraft:chicken ~ ~ ~2");
            }
        }, 10);
        step("as a player", () -> {
            for (int i = 0; i < 3; i++) {
                command("damage @e[type=minecraft:chicken,limit=1,sort=nearest] 100 minecraft:player_attack by @s");
            }
        }, 30);
        expect("the hidden one is reached", () -> claimed(ACH_CHICKEN, 1));
        expect("the server counted rarity", () -> ClientAchievements.rarity(ACH_CHICKEN) > 0);
        step("select the hidden one", () -> BookScreen.achievementSelectForTest(ResourceLocation.parse("example:chicken_hunter")), 10);
        screenshot("achievements_revealed");
        step("wear the title", () -> PacketDistributor.sendToServer(new works.repka.rysquests.network.SelectTitlePayload(ACH_TITLE)), 20);
        expect("the title is worn", () -> ACH_TITLE.equals(ClientAchievements.activeTitle()));
        expect("a title that is not owned is refused", () -> {
            PacketDistributor.sendToServer(new works.repka.rysquests.network.SelectTitlePayload("nope"));
            return ACH_TITLE.equals(ClientAchievements.activeTitle());
        });
        step("free the board of earlier runs", () -> {
            ClientProgress.pinned().forEach(quest -> PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.PIN, quest, 0, List.of())));
            ClientProgress.pinnedWays().forEach(way -> PacketDistributor.sendToServer(new works.repka.rysquests.network.PinWayPayload(way)));
        }, 20);
        step("pin the next tier", () -> {
            if (!ClientProgress.pinned().contains(ACH_IRON_GOLD)) {
                PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.PIN, ACH_IRON_GOLD, 0, List.of()));
            }
        }, 20);
        expect("it is pinned", () -> ClientProgress.pinned().contains(ACH_IRON_GOLD));
        step("close the book to see the HUD", () -> mc().setScreen(null), 20);
        screenshot("achievements_hud");
        step("unpin", () -> PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.PIN, ACH_IRON_GOLD, 0, List.of())), 20);
        long[] after = new long[1];
        step("remember the coins and leave", () -> {
            after[0] = ClientShop.balance("coin");
            mc().level.disconnect();
            mc().disconnect(new TitleScreen());
        }, 40);
        openWorld();
        step("wait for the state", () -> {
        }, 40);
        expect("coming back gives nothing twice", () -> ClientShop.balance("coin") == after[0]);
        expect("the title stayed", () -> ACH_TITLE.equals(ClientAchievements.activeTitle()));
    }

    private static final ResourceLocation ACH_IRON_GOLD = ResourceLocation.parse("example:_achievements/iron_hoard/gold");

    /** The acceptance of the constructor: a mining atlas of five resources and three stages, made in the game. */
    private static void buildConstructorScenario() {
        List<String> resources = List.of("Iron", "Copper", "Gold", "Diamond", "Coal");
        List<String> stages = List.of("Early", "Middle", "Late");
        ResourceLocation[] made = new ResourceLocation[1];
        openWorld();
        step("match the window of the mockups", () -> {
            mc().options.guiScale().set(2);
            mc().resizeDisplay();
        }, 10);
        step("open the book", () -> pressKey(GLFW.GLFW_KEY_K), 30);
        expect("book screen open", () -> mc().screen instanceof BookScreen);
        step("open the constructor", BookScreen::openConstructorForTest, 5);
        expect("the constructor is open", BookScreen::constructorOpenForTest);
        step("make a matrix from the template", BookScreen::newMatrixForTest, 5);
        step("fill it", () -> {
            BookScreen.buildMatrixForTest("Mining atlas", resources, stages, ResourceLocation.parse("minecraft:iron_ingot"));
            BookScreen.selectWayForTest(0, 1, 0);
            made[0] = BookScreen.constructorSectionForTest();
        }, 30);
        screenshot("constructor_matrix");
        step("click save", () -> mc().screen.mouseClicked(556, 31, GLFW.GLFW_MOUSE_BUTTON_LEFT), 5);
        expect("the save went through", BookScreen::constructorSavedForTest);
        expect("the section is in the book", () -> ClientBook.current().sections().get(made[0]) instanceof works.repka.rysquests.data.MatrixSection matrix && matrix.rows().size() == 5 && matrix.columns().size() == 3);
        expect("it is on the rail", () -> ClientBook.current().meta().sections().contains(made[0].toString()));
        expect("its texts came with the book", () -> Component.translatable(ClientBook.current().sections().get(made[0]).title()).getString().equals("Mining atlas"));
        expect("every text resolves", () -> BookScreen.guideIssuesForTest().isEmpty());
        step("leave the constructor", () -> pressKey(GLFW.GLFW_KEY_ESCAPE), 5);
        expect("the constructor is closed", () -> !BookScreen.constructorOpenForTest());
        step("open the section as a player", () -> BookScreen.showSectionForTest(made[0].toString()), 30);
        expect("the section is open", () -> made[0].toString().equals(BookScreen.currentSectionForTest()));
        screenshot("constructor_result");
        step("close the book", () -> pressKey(GLFW.GLFW_KEY_ESCAPE), 8);
    }

    /** A guide made in the editor and read back, in a single player world. */
    private static void buildEditorScenario() {
        ResourceLocation id = ResourceLocation.parse("example:autotest_editor");
        ResourceLocation picture = ResourceLocation.parse("example:textures/guides/autotest_editor_1.png");
        openWorld();
        step("match the window of the mockups", () -> {
            mc().options.guiScale().set(2);
            mc().resizeDisplay();
        }, 10);
        step("open the book", () -> pressKey(GLFW.GLFW_KEY_K), 30);
        expect("book screen open", () -> mc().screen instanceof BookScreen);
        step("open the editor", () -> BookScreen.editGuideForTest(id), 5);
        expect("the editor is open", BookScreen::editorOpenForTest);
        step("write the first page", () -> {
            BookScreen.writeGuideForTest("Autotest guide", ResourceLocation.parse("minecraft:furnace"));
            BookScreen.addImageForTest(picture, testPicture());
            BookScreen.writePageForTest("First page", "Build a [[item:minecraft:furnace]] from **cobblestone**.\n[[recipe:minecraft:furnace]]\n[[image:" + picture + "|A picture]]");
        }, 40);
        screenshot("editor_page");
        String before = "Build";
        step("type a letter", () -> mc().screen.charTyped('Z', 0), 25);
        expect("the letter is in the text", () -> BookScreen.editorPageTextForTest().startsWith("Z" + before));
        step("undo it", BookScreen::editorUndoForTest, 3);
        expect("the letter is gone", () -> BookScreen.editorPageTextForTest().startsWith(before));
        step("add a screenshot", () -> BookScreen.editorAddPictureForTest(mc().gameDirectory.toPath().resolve("screenshots/rysquests_editor_page.png")), 20);
        expect("the screenshot is in the text", () -> BookScreen.editorPageTextForTest().contains("autotest_editor_2.png"));
        step("go to the second page", () -> {
            if (BookScreen.editorPagesForTest() < 2) {
                BookScreen.addPageForTest();
            } else {
                BookScreen.editorSelectPageForTest(1);
            }
            BookScreen.writePageForTest("Second page", "Written at " + System.currentTimeMillis());
        }, 5);
        step("click save", () -> mc().screen.mouseClicked(556, 31, GLFW.GLFW_MOUSE_BUTTON_LEFT), 5);
        expect("the save went through", BookScreen::editorSavedForTest);
        expect("the book has the guide", () -> ClientBook.current().guides().containsKey(id));
        expect("its pages are in order", () -> ClientBook.current().guides().get(id).pages().size() == 2);
        expect("its title is translated", () -> Component.translatable(ClientBook.current().guides().get(id).title()).getString().equals("Autotest guide"));
        expect("its picture is known", () -> ClientAssets.imageSize(picture) != null);
        expect("the guide has no broken links", () -> BookScreen.guideIssuesForTest().isEmpty());
        expect("the file is in the editor's folder", () -> Files.isRegularFile(mc().gameDirectory.toPath().resolve("config/rysquests/pack/data/example/rysquests/guides/autotest_editor.json")));
        screenshot("editor_saved");
        step("leave the editor", () -> pressKey(GLFW.GLFW_KEY_ESCAPE), 5);
        expect("the editor is closed", () -> !BookScreen.editorOpenForTest());
        step("open the saved page", () -> BookScreen.openGuideForTest(id, 0), 40);
        screenshot("editor_result");
        step("close the book", () -> pressKey(GLFW.GLFW_KEY_ESCAPE), 8);
    }

    private static void sendRead(ResourceLocation guide, int pages) {
        for (int i = 0; i < pages; i++) {
            PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.READ_GUIDE_PAGE, guide, i, List.of()));
        }
    }

    private static void buildDesignScenario() {
        String mechanism = "design:02/mechanism";
        ResourceLocation chapter = ResourceLocation.parse("design:02");
        ResourceLocation quest = ResourceLocation.parse(mechanism);
        openWorld();
        step("every symbol of the interface is in the font", AutoTest::checkSymbols, 1);
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
        // A title cut to fit the node shows in full under the cursor, the map's zoom included (verdict item 6).
        ResourceLocation solar = ResourceLocation.parse("design:02/solar");
        step("zoom out a little and point at a cut title", () -> {
            BookScreen.mapZoomForTest(0.85f);
        }, 5);
        step("move the cursor onto the title", () -> {
            double[] node = BookScreen.mapNodeForTest(solar);
            if (node != null) {
                moveMouse(works.repka.ryscore.ui.UiScale.toGui(node[0] + node[2] * 0.4), works.repka.ryscore.ui.UiScale.toGui(node[1] + node[3] * 0.33));
            }
        }, 5);
        expect("the whole title shows in a tooltip", () -> "Солнечные панели".equals(BookScreen.hoveredTextForTest()));
        screenshot("design_map_tooltip");
        step("move the cursor away and zoom back", () -> {
            moveMouse(2, 2);
            BookScreen.mapZoomForTest(1f);
        }, 5);
        step("zoom the map all the way out", () -> BookScreen.mapZoomForTest(0.35f), 10);
        screenshot("design_map_far");
        step("zoom the map back", () -> BookScreen.mapZoomForTest(1f), 5);
        step("click the list tab", () -> mc().screen.mouseClicked(150, 33.5, GLFW.GLFW_MOUSE_BUTTON_LEFT), 5);
        expect("the tab switched the view", () -> BookScreen.viewForTest() == ViewMode.LIST);
        step("switch to the list", () -> BookScreen.showForTest(ViewMode.LIST), 10);
        screenshot("design_list");
        step("search the whole book", () -> BookScreen.searchForTest("глава:II мех"), 25);
        screenshot("design_search");
        step("clear the search", () -> BookScreen.searchForTest(""), 2);
        step("switch to what next", () -> BookScreen.showForTest(ViewMode.HOME), 25);
        screenshot("design_home");
        step("switch to the timeline", () -> BookScreen.showForTest(ViewMode.TIMELINE), 25);
        screenshot("design_timeline");
        step("switch to dev", () -> BookScreen.showForTest(ViewMode.DEV), 20);
        expect("dev report arrived", () -> ClientDev.report() != null);
        step("wait for the report on screen", () -> {}, 10);
        screenshot("design_dev");
        step("show dev as wide as a player's screen", () -> {
            mc().getWindow().setWindowed(2000, 1060);
            mc().options.guiScale().set(0);
            mc().resizeDisplay();
        }, 15);
        screenshot("design_dev_wide");
        step("back to the size of the mockups", () -> {
            mc().getWindow().setWindowed(1280, 800);
            mc().options.guiScale().set(2);
            mc().resizeDisplay();
        }, 10);
        if ("design_sizes".equals(scenario)) {
            addSizeSteps();
        }
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
        step("link a quest in chat", () -> command("tellraw @s \"look: [[quest:design:02/solar]]\""), 10);
        step("open chat for the link", () -> pressKey(GLFW.GLFW_KEY_T), 10);
        screenshot("design_chat");
        step("close chat", () -> pressKey(GLFW.GLFW_KEY_ESCAPE), 5);
        step("finish the quest with a choice and open the map", () -> {
            command("rysquests progress @s complete " + mechanism);
            BookScreen.selectForTest(chapter, ResourceLocation.parse("design:02/brass"));
            BookScreen.showForTest(ViewMode.MAP);
            pressKey(GLFW.GLFW_KEY_K);
        }, 30);
        expect("book screen open", () -> mc().screen instanceof BookScreen);
        step("claim everything that needs no choice", BookScreen::claimAllForTest, 30);
        expect("only the choice is left", BookScreen::onlyPicksLeftForTest);
        step("the claim button opens the quest to pick in", BookScreen::claimAllForTest, 10);
        expect("the quest with the choice is selected", () -> quest.equals(BookScreen.selectedQuestForTest()));
        screenshot("design_pick");
        expect("the quest is still pinned", () -> ClientProgress.pinned().contains(quest));
        step("zoom out until the pinned quest is in plain sight", () -> BookScreen.mapZoomForTest(0.75f), 10);
        screenshot("design_pinned_map");
        step("zoom the map back", () -> BookScreen.mapZoomForTest(1f), 5);
        step("pick the second option and claim", () -> PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.CLAIM, quest, 0, List.of(1))), 20);
        expect("the choice was claimed", () -> ClientProgress.get(quest).claimed().contains(0) && !BookScreen.onlyPicksLeftForTest());
        // Chapter III links the mechanism of chapter II: it stands on III's map too, and a click opens it in II.
        step("open chapter III on the map", () -> BookScreen.selectForTest(ResourceLocation.parse("design:03"), null), 30);
        expect("the linked quest is on the map", () -> BookScreen.mapStatsForTest().startsWith("quests 2"));
        screenshot("design_link");
        step("close the book", () -> pressKey(GLFW.GLFW_KEY_ESCAPE), 8);
    }

    /** Plays the first example quest through the real keys and the real packets. */
    /**
     * The frame of the redesigned book (plan step 4): header and tabs, sidebar and its rail, settings, the message line, the
     * closed-book line and its question, loading, the empty book for an operator, a player and with FTB, a first-run page, and the
     * compact layout. Each state is a screenshot to lay over its mockup in {@code _audit/book2/final/img}.
     */
    private static void buildFrameScenario() {
        openWorld();
        expect("example book reached the client", () -> ClientBook.current().quests().size() == EXAMPLE_QUESTS);
        step("mouse out of the way, no messages", () -> {
            moveMouse(1, 1);
            ClientMessages.dismiss();
            ThemeManager.select("ryscore:slate");
        }, 2);
        step("open the book with its key", () -> pressKey(GLFW.GLFW_KEY_K), 10);
        expect("book screen open", () -> mc().screen instanceof BookScreen);
        expect("the first opening is on Now", () -> "now".equals(BookScreen.bodyForTest()));
        expect("tabs with content only", () -> BookScreen.tabsForTest().contains("now") && BookScreen.tabsForTest().contains("map"));
        step("log the tabs", () -> LOGGER.info("[autotest] tabs: {}", BookScreen.tabsForTest()), 2);
        screenshot("frame_now");
        step("open the map tab", () -> BookScreen.openTabForTest("map"), 20);
        expect("the map tab is open", () -> "map".equals(BookScreen.bodyForTest()));
        screenshot("frame_map");
        step("fold the sidebar", () -> BookScreen.foldSidebarForTest(true), 5);
        screenshot("frame_map_rail");
        step("unfold the sidebar, open the list", () -> {
            BookScreen.foldSidebarForTest(false);
            BookScreen.showForTest(ViewMode.LIST);
        }, 10);
        screenshot("frame_list");
        for (String tab : List.of("guides", "shop", "achievements")) {
            step("open the " + tab + " tab", () -> BookScreen.openTabForTest(tab), 10);
            expect("the " + tab + " tab is open", () -> tab.equals(BookScreen.bodyForTest()));
            screenshot("frame_" + tab);
        }
        step("open the settings", BookScreen::toggleSettingsForTest, 5);
        expect("the settings are a page", () -> "page".equals(BookScreen.bodyForTest()));
        screenshot("frame_settings");
        step("back from the settings", () -> pressKey(GLFW.GLFW_KEY_ESCAPE), 5);
        expect("the settings closed, the book stayed", () -> mc().screen instanceof BookScreen && "achievements".equals(BookScreen.bodyForTest()));
        step("a message from the server", () -> {
            BookScreen.openTabForTest("now");
            ClientMessages.show("rysquests.reward.error.inventory_full", List.of(), false);
        }, 5);
        screenshot("frame_message");
        step("the shared components on one page", works.repka.rysquests.client.screen.FrameShowcase::open, 10);
        screenshot("frame_components");
        step("back from the components", () -> {
            pressKey(GLFW.GLFW_KEY_ESCAPE);
            ClientMessages.dismiss();
        }, 5);
        step("a closed book", () -> {
            QuestBook book = ClientBook.current();
            ClientBook.overrideForTest(new QuestBook(book.meta().withPublished(false), book.chapters(), book.quests(), book.guides(), book.sections(),
                    book.unlocks(), book.byItem(), book.hash(), book.assets(), book.offers(), book.achievements(), book.structures(), book.layoutHash(),
                    book.layout()), null);
            BookScreen.openTabForTest("map");
        }, 10);
        screenshot("frame_closed");
        step("ask to open it", BookScreen::askToOpenForTest, 5);
        expect("the question is open", BookScreen::dialogOpenForTest);
        screenshot("frame_closed_ask");
        step("answer no with Esc", () -> pressKey(GLFW.GLFW_KEY_ESCAPE), 5);
        expect("the question closed, the book stayed", () -> !BookScreen.dialogOpenForTest() && mc().screen instanceof BookScreen);
        step("the book still loading", () -> ClientBook.overrideForTest(QuestBook.EMPTY, false), 5);
        expect("loading state", () -> "loading".equals(BookScreen.bodyForTest()));
        screenshot("frame_loading");
        step("an empty book, operator", () -> {
            ClientBook.overrideForTest(QuestBook.EMPTY, true);
            BookScreen.ftbForTest(false);
        }, 5);
        expect("empty state", () -> "empty".equals(BookScreen.bodyForTest()));
        screenshot("frame_empty_operator");
        step("an empty book, player", () -> BookRole.forceForTest(false), 5);
        screenshot("frame_empty_player");
        step("an empty book with FTB", () -> {
            BookRole.forceForTest(null);
            BookScreen.ftbForTest(true);
        }, 5);
        screenshot("frame_empty_ftb");
        step("build the book", () -> BookScreen.openEmptyPageForTest(false), 5);
        expect("a first-run page", () -> "page".equals(BookScreen.bodyForTest()));
        screenshot("frame_new_book");
        step("back to the real book", () -> {
            pressKey(GLFW.GLFW_KEY_ESCAPE);
            BookScreen.ftbForTest(null);
            ClientBook.overrideForTest(null, null);
        }, 5);
        expect("the book is back", () -> List.of("now", "map").contains(BookScreen.bodyForTest()));
        step("a small window", () -> {
            mc().getWindow().setWindowed(1100, 700);
            mc().resizeDisplay();
            BookScreen.openTabForTest("map");
        }, 20);
        expect("compact layout", BookScreen::compactForTest);
        screenshot("frame_compact_map");
        step("compact now", () -> BookScreen.openTabForTest("now"), 10);
        screenshot("frame_compact_now");
        step("compact settings", BookScreen::toggleSettingsForTest, 5);
        screenshot("frame_compact_settings");
        step("back to the size of the mockups", () -> {
            pressKey(GLFW.GLFW_KEY_ESCAPE);
            mc().getWindow().setWindowed(1280, 800);
            mc().resizeDisplay();
        }, 20);
        expect("normal layout again", () -> !BookScreen.compactForTest());
        step("close the book", () -> pressKey(GLFW.GLFW_KEY_ESCAPE), 5);
        expect("book closed", () -> mc().screen == null);
    }

    /**
     * The quest panel, "Now" and the list (plan steps 5 and 6) on a book shaped like the mockups (src/autotest/panel_pack): the first
     * opening, the next step, every kind of task and reward, a closed quest with its way, "any one of", a repeatable quest, the map
     * with the panel, the list of a chapter, of a chapter off the map, of an epoch and of the whole book, a filter left empty, the
     * compact layout and a finished book. With -PftbqBook=<FTB Quests folder> the list is measured on that book too (ATM10).
     */
    private static void buildPanelScenario() {
        String ns = "panel:";
        java.util.function.Function<String, ResourceLocation> id = path -> ResourceLocation.parse(ns + path);
        openWorld();
        expect("the panel book reached the client", () -> ClientBook.current().quests().containsKey(id.apply("current/electric_mechanism")));
        step("mouse out of the way, slate, every quest reset", () -> {
            moveMouse(1, 1);
            ClientMessages.dismiss();
            ThemeManager.select("ryscore:slate");
            command("gamerule sendCommandFeedback false");
            command("clear @s");
            command("xp set @s 0 levels");
            for (ResourceLocation quest : ClientBook.current().quests().keySet()) {
                command("rysquests progress @s reset " + quest);
            }
            // The mark of the last completed chapter outlives a reset: point it at the first chapter, as for a new player.
            command("rysquests progress @s complete " + ns + "andesite/andesite_alloy");
            command("rysquests progress @s reset " + ns + "andesite/andesite_alloy");
            for (ResourceLocation quest : ClientProgress.pinned()) {
                PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.PIN, quest, 0, List.of()));
            }
        }, 30);
        expect("nothing is done", () -> ClientBook.current().quests().keySet().stream().noneMatch(quest -> ClientProgress.get(quest).everDone()));
        step("open the book", () -> pressKey(GLFW.GLFW_KEY_K), 10);
        expect("book screen open", () -> mc().screen instanceof BookScreen);
        step("Now at the first opening", () -> PanelShowcase.now(true), 10);
        step("log the next step", () -> LOGGER.info("[autotest] next step {}, last chapter {}", PanelShowcase.nextStep(), ClientProgress.lastChapter()), 2);
        expect("the first step is the first main quest", () -> id.apply("andesite/andesite_alloy").equals(PanelShowcase.nextStep()));
        screenshot("panel_now_first");

        step("progress as in the mockups", () -> {
            for (String done : List.of("andesite/andesite_alloy", "andesite/water_wheel", "andesite/shaft", "andesite/andesite_2", "brass/brass_ingot",
                    "brass/brass_casing", "current/tok", "current/copper_wire", "current/copper_wire_2", "current/copper_wire_3", "current/iron_rod",
                    "current/copper_wire_coil", "current/ie_wires", "current/lv_wire_coil", "current/steel_wire_coil", "chronicle/old_mineshaft",
                    "chronicle/villages", "chronicle/nether_expedition", "chronicle/explorer_the_nether", "start/welcome")) {
                command("rysquests progress @s complete " + ns + done);
            }
        }, 30);
        step("claim what needs no choice", BookScreen::claimAllForTest, 30);
        step("two quests wait for their rewards, items in the inventory", () -> {
            // The last completed quest marks the chapter of the next step: "Ток" last.
            command("rysquests progress @s complete " + ns + "oil/plastic");
            command("rysquests progress @s complete " + ns + "current/copper_coil");
            command("give @s minecraft:polished_andesite 40");
            command("give @s minecraft:clock 12");
        }, 30);
        step("hand in part of two tasks", () -> {
            PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.SUBMIT, id.apply("andesite/automation_64"), 0, List.of()));
            PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.SUBMIT, id.apply("chronicle/all_items"), 1, List.of()));
        }, 20);
        step("more in the inventory, pins", () -> {
            command("give @s minecraft:polished_andesite 24");
            command("give @s minecraft:clock 8");
            command("give @s minecraft:oak_planks 16");
            command("give @s minecraft:iron_ingot 6");
            command("give @s minecraft:lava_bucket 1");
            command("give @s minecraft:lava_bucket 1");
            command("give @s minecraft:water_bucket 1");
            command("give @s minecraft:gold_nugget 1");
            command("xp add @s 12 levels");
            PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.PIN, id.apply("andesite/automation_64"), 0, List.of()));
            PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.PIN, id.apply("chronicle/all_items"), 0, List.of()));
        }, 40);
        expect("part of the andesite was handed in", () -> ClientProgress.get(id.apply("andesite/automation_64")).task(0) == 40);
        step("Now with the next step", () -> PanelShowcase.now(false), 10);
        step("log the next step", () -> LOGGER.info("[autotest] next step {}, last chapter {}", PanelShowcase.nextStep(), ClientProgress.lastChapter()), 2);
        expect("the next step is the electric mechanism", () -> id.apply("current/electric_mechanism").equals(PanelShowcase.nextStep()));
        screenshot("panel_now");
        step("a quest in work chosen on Now", () -> PanelShowcase.nowChoose(id.apply("andesite/automation_64")), 10);
        screenshot("panel_now_work");

        step("every kind of task", () -> PanelShowcase.sheet(id.apply("chronicle/all_items"), id.apply("chronicle/all_counters"),
                id.apply("chronicle/all_actions")), 10);
        screenshot("panel_tasks");
        step("every kind of reward, a choice picked", () -> {
            PanelShowcase.sheet(id.apply("chronicle/all_rewards"), id.apply("oil/plastic"), id.apply("current/copper_coil"));
            PanelShowcase.pick(0, 1);
        }, 10);
        screenshot("panel_rewards");
        step("closed with its way, any one of, repeatable", () -> PanelShowcase.sheet(id.apply("current/heavy_engineering_block"),
                id.apply("current/capacitor"), id.apply("chronicle/scheme")), 10);
        screenshot("panel_locked");

        step("the map with the panel", () -> PanelShowcase.map(id.apply("current"), id.apply("current/electric_mechanism")), 20);
        screenshot("panel_map_quest");
        step("the map with a closed quest", () -> PanelShowcase.map(id.apply("current"), id.apply("current/heavy_engineering_block")), 20);
        screenshot("panel_map_route");

        step("the list of a chapter", () -> PanelShowcase.list(id.apply("current"), 0, "ALL", id.apply("current/copper_coil"), true), 10);
        screenshot("panel_list_chapter");
        step("the list of a chapter off the map", () -> PanelShowcase.list(id.apply("chronicle"), 0, "ALL", id.apply("chronicle/wither"), false), 10);
        screenshot("panel_list_off_map");
        step("the whole book, what can be done", () -> PanelShowcase.list(null, 0, "CAN", id.apply("current/electric_mechanism"), false), 10);
        screenshot("panel_list_book");
        step("an epoch", () -> PanelShowcase.list(null, 2, "ALL", null, false), 10);
        screenshot("panel_list_epoch");
        step("a filter left nothing", () -> PanelShowcase.list(id.apply("machines"), 0, "DONE", null, false), 10);
        screenshot("panel_list_empty");

        step("a small window", () -> {
            mc().getWindow().setWindowed(1100, 700);
            mc().resizeDisplay();
            PanelShowcase.now(false);
        }, 20);
        expect("compact layout", BookScreen::compactForTest);
        screenshot("panel_compact_now");
        step("compact list", () -> PanelShowcase.list(id.apply("current"), 0, "ALL", id.apply("current/copper_coil"), true), 10);
        screenshot("panel_compact_list");
        step("back to the size of the mockups", () -> {
            mc().getWindow().setWindowed(1280, 800);
            mc().resizeDisplay();
        }, 20);

        step("every main quest done", () -> {
            for (var quest : ClientBook.current().quests().values()) {
                if (!quest.options().optional()) {
                    command("rysquests progress @s complete " + quest.id());
                }
            }
        }, 40);
        step("claim it all", BookScreen::claimAllForTest, 30);
        step("Now in a finished book", () -> PanelShowcase.now(false), 10);
        expect("no next step", () -> PanelShowcase.nextStep() == null);
        screenshot("panel_now_done");

        String ftbq = System.getProperty("rysquests.autotest.ftbqBook", "");
        if (!ftbq.isBlank()) {
            step("the ATM10 book in the client", () -> {
                try {
                    QuestBook atm = PanelShowcase.ftbqBook(Path.of(ftbq));
                    LOGGER.info("[autotest] ftbq book: {} chapters, {} quests", atm.chapters().size(), atm.quests().size());
                    ClientBook.overrideForTest(atm, true);
                } catch (java.io.IOException e) {
                    fail("could not read the FTB book: " + e);
                }
            }, 20);
            step("Now on the large book", () -> PanelShowcase.now(false), 20);
            screenshot("panel_atm_now");
            step("the list of the whole book, every chapter open in full", () -> {
                PanelShowcase.list(null, 0, "ALL", null, false);
                PanelShowcase.listOpenAll();
            }, 20);
            step("log the size of the list", () -> LOGGER.info("[autotest] atm list: {}", PanelShowcase.listStats()), 2);
            screenshot("panel_atm_list");
            step("measure the frame while scrolling", () -> worstFrame = 0, 2);
            for (int i = 0; i < 40; i++) {
                step("scroll the large list " + i, () -> PanelShowcase.listScroll(-25), 2);
            }
            step("report the frame", () -> LOGGER.info("[autotest] worst frame while scrolling the whole ATM10 list: {} ms, {}", worstFrame / 1_000_000,
                    PanelShowcase.listStats()), 2);
            screenshot("panel_atm_list_scrolled");
            step("what can be done in the large book", () -> PanelShowcase.list(null, 0, "CAN", null, false), 20);
            screenshot("panel_atm_list_can");
            step("back to the real book", () -> ClientBook.overrideForTest(null, null), 10);
        }
        step("close the book", () -> pressKey(GLFW.GLFW_KEY_ESCAPE), 5);
        expect("book closed", () -> mc().screen == null);
    }

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
