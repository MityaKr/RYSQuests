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
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.client.screen.BookScreen;
import works.repka.rysquests.data.BookHolder;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.ViewMode;
import works.repka.rysquests.dev.DevExamplePack;
import works.repka.rysquests.network.QuestActionPayload;
import works.repka.rysquests.progress.ProgressService;
import works.repka.rysquests.progress.ProgressStore;
import works.repka.rysquests.client.ClientAssets;
import works.repka.rysquests.network.EditorSavePayload;
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
    private static final int EXAMPLE_QUESTS = 7;
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
        } else if ("repys".equals(scenario)) {
            buildRepysScenario();
        } else if ("design".equals(scenario) || "design_sizes".equals(scenario)) {
            buildDesignScenario();
        } else if ("guides".equals(scenario)) {
            buildGuidesScenario();
        } else if ("editor".equals(scenario)) {
            buildEditorScenario();
        } else if ("sections".equals(scenario)) {
            buildSectionsScenario();
        } else if ("sections_design".equals(scenario)) {
            buildSectionsDesignScenario();
        } else if ("guides_design".equals(scenario)) {
            buildGuidesDesignScenario();
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
        addEditorOnServerSteps();
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
        expect("the saved guide reached the other player", () -> ClientBook.current().guides().containsKey(SERVER_GUIDE));
        expect("its text came with the book", () -> Component.translatable(ClientBook.current().guides().get(SERVER_GUIDE).title()).getString().equals("Written on the server"));
        expect("its picture came with the book", () -> ClientAssets.imageSize(SERVER_IMAGE) != null);
        step("try to save without rights", () -> PacketDistributor.sendToServer(new EditorSavePayload(Optional.of(ClientBook.current().guides().get(SERVER_GUIDE)), Optional.empty(), "en_us", Map.of(), Map.of())), 20);
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
        step("open the book", () -> pressKey(GLFW.GLFW_KEY_K), 30);
        expect("book screen open", () -> mc().screen instanceof BookScreen);
        for (String[] view : new String[][] {{"design:mining", "design_mining"}, {"design:atlas", "design_atlas"}, {"design:bosses", "design_cards"}, {"design:rules", "design_pages"}}) {
            step("show " + view[0], () -> BookScreen.showSectionForTest(view[0]), 40);
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
            step("back to the size of the mockups", () -> {
                mc().getWindow().setWindowed(1280, 800);
                mc().options.guiScale().set(2);
                mc().resizeDisplay();
                ThemeManager.select("ryscore:slate");
            }, 10);
        }
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
        step("close the book", () -> mc().setScreen(null), 5);
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
