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
import works.repka.rysquests.client.screen.FirstRunHooks;
import works.repka.rysquests.client.screen.EditorTesting;
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
    /** The first run carries a whole FTB book over and lays out four maps of it: longer than the other scenarios. */
    private static final boolean LONG_SCENARIO = List.of("first", "themes", "panel_notify").contains(System.getProperty(SCENARIO_PROPERTY));
    private static final int TOTAL_TICK_LIMIT = 20 * (AS_PLAYER || LONG_SCENARIO ? 1200 : 300);
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
        } else if ("extras".equals(scenario)) {
            buildExtrasScenario();
        } else if ("first".equals(scenario)) {
            buildFirstRunScenario();
        } else if ("dock".equals(scenario)) {
            buildDockScenario();
        } else if ("repys".equals(scenario)) {
            RepysScenario.check();
        } else if ("migrate".equals(scenario)) {
            MigrateScenario.check();
        } else if ("items".equals(scenario)) {
            ItemsScenario.check();
        } else if ("shop_schematic".equals(scenario)) {
            ShopSchematicScenario.check();
        } else if ("links".equals(scenario)) {
            ItemLinksScenario.check();
        } else if ("shop_trophy".equals(scenario)) {
            ShopTrophyScenario.check();
        } else if ("shop_delivery".equals(scenario)) {
            ShopDeliveryScenario.check();
        } else if ("shop_receiver".equals(scenario)) {
            ShopReceiverScenario.check();
        } else if ("shop_dispenser".equals(scenario)) {
            ShopDispenserScenario.check();
        } else if ("shop_editor".equals(scenario)) {
            ShopEditorScenario.check();
        } else if ("upgrade_new".equals(scenario)) {
            RepysScenario.upgrade();
        } else if ("repys_ftb".equals(scenario)) {
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
        } else if ("stat_team".equals(scenario)) {
            StatTeamScenario.build();
        } else if ("collection".equals(scenario)) {
            CollectionScenario.build();
        } else if ("constructor".equals(scenario)) {
            buildConstructorScenario();
        } else if ("sections".equals(scenario)) {
            buildSectionsScenario();
        } else if ("sections_design".equals(scenario)) {
            buildSectionsDesignScenario();
        } else if ("guides_design".equals(scenario)) {
            buildGuidesDesignScenario();
        } else if ("canvas".equals(scenario)) {
            buildCanvasScenario();
        } else if ("quest_menu".equals(scenario)) {
            QuestMenuScenario.build();
        } else if ("map".equals(scenario)) {
            buildMapScenario();
        } else if ("map_drift".equals(scenario)) {
            buildMapDriftScenario();
        } else if ("repys_data".equals(scenario)) {
            buildRepysDataScenario();
        } else if ("layout".equals(scenario)) {
            buildLayoutScenario();
        } else if ("themes".equals(scenario)) {
            ThemesScenario.build();
        } else if ("panel_notify".equals(scenario)) {
            NoticesScenario.build();
        } else if ("book_editor".equals(scenario)) {
            EditorScenario.build(false);
        } else if ("book_editor_restart".equals(scenario)) {
            EditorScenario.build(true);
        } else if ("chronicle".equals(scenario)) {
            ChronicleScenario.build();
        } else if ("team_a".equals(scenario)) {
            buildTeamLeaderScenario();
        } else if ("team_b".equals(scenario)) {
            buildTeamMemberScenario();
        } else {
            // Reported from the first tick: the client cannot be stopped yet during mod construction.
            step("check scenario name", () -> fail("unknown scenario " + scenario), 0);
        }
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Render.Post event) -> {
            if (mapTiming && mc().screen instanceof BookScreen) {
                works.repka.rysquests.client.canvas.MapCanvas.Stats stats = BookScreen.mapCanvasStatsForTest();
                if (stats != null) {
                    mapFrames++;
                    mapNanos += stats.nanos();
                    mapWorst = Math.max(mapWorst, stats.nanos());
                }
            }
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
    /** The RepYS book from before it was the pack's own: imported from FTB Quests with the progress of a real player. */
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
        expect("the own quest's notice stands in the toasts' column", () -> works.repka.ryscore.ui.Notices.activeForTest().stream()
                .anyMatch(line -> line.startsWith("quests/done:" + TEAM_QUEST + " CARD on")));
        step("let it slide in", () -> works.repka.ryscore.ui.Notices.playedForTest().forEach(line -> LOGGER.info("[autotest] notice sound: {}", line)), 14);
        screenshot("team_a_notice_own");
        step("claim first", () -> PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.CLAIM, TEAM_QUEST, 0, List.of())), 20);
        expect("got the reward for each and the single one", () -> count(Items.BREAD) == 1 && count(Items.DIAMOND) == 1);
        teamScreens("a");
        addShopOnServerSteps();
        addTreasuryLeaderSteps();
        addRoundLeaderSteps();
        addEditorOnServerSteps();
        step("import FTB progress where the other player took the reward", AutoTest::writeFtbqPartyFile, 5);
        step("run the import", () -> command("rysquests import ftbq progress"), 20);
        // FTB's party file is named by the owner's id, which is our own team's id; a team of RYSTeams has an id of its own.
        expect("the leader has not taken what the other player took", () -> USE_RYSTEAMS || ClientProgress.get(TEAM_IMPORTED).completed() && ClientProgress.get(TEAM_IMPORTED).claimed().isEmpty());
        // the publication lock: a closed book does not reach a player, an operator keeps it with the line "closed"
        expect("the other player is ready for the lock", () -> ClientProgress.get(TEAM_READY).completed());
        step("close the book to players", () -> PacketDistributor.sendToServer(new works.repka.rysquests.network.PublishPayload(false)), 40);
        expect("the book is closed, the operator still has it", () -> !ClientBook.current().meta().published() && ClientBook.current().quests().containsKey(TEAM_QUEST));
        step("open the closed book", () -> {
            mc().setScreen(new BookScreen());
            BookScreen.openTabForTest("map");
        }, 20);
        screenshot("team_a_closed_operator");
        step("hold it closed while the other player looks", () -> mc().setScreen(null), 200);
        step("open the book to players again", () -> PacketDistributor.sendToServer(new works.repka.rysquests.network.PublishPayload(true)), 40);
        expect("the book is open again", () -> ClientBook.current().meta().published());
        expect("the other player has left", () -> players() == 1);
        step("stop the server", () -> command("stop"), 100);
    }

    /**
     * The quest panel and "Now" in a team of two: "выполнен командой", "Каждый участник забирает сам" for the reward each takes,
     * "Уже получена командой" for the one the first took, and the team's line on "Now".
     */
    private static void teamScreens(String who) {
        step("open the shared quest (" + who + ")", () -> {
            mc().setScreen(new BookScreen());
            BookScreen.openTabForTest("map");
            BookScreen.showForTest(ViewMode.MAP);
            BookScreen.selectForTest(ResourceLocation.parse("teamtest:main"), TEAM_QUEST);
            BookScreen.mapFlyForTest(null, 0, TEAM_QUEST);
        }, 30);
        // the game's own toasts (chat verification, tutorial, recipes) would lie over the panel
        step("mouse away (" + who + ")", () -> {
            moveMouse(1, 1);
            mc().getToasts().clear();
        }, 3);
        screenshot("team_" + who + "_panel");
        step("open now (" + who + ")", () -> {
            BookScreen.openTabForTest("now");
            mc().getToasts().clear();
        }, 15);
        screenshot("team_" + who + "_now");
        step("close the book (" + who + ")", () -> mc().setScreen(null), 5);
    }

    /** The guide the leader wrote in the editor on the server, by the title of its page: the editor gives it its id and name. */
    @org.jetbrains.annotations.Nullable
    private static works.repka.rysquests.data.Guide serverGuide() {
        return ClientBook.current().guides().values().stream()
                .filter(guide -> !guide.pages().isEmpty() && Component.translatable(guide.pages().get(0).title()).getString().equals("Written on the server"))
                .findFirst().orElse(null);
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
        expect("the teammate's quest came as a notice with a sound", () -> works.repka.ryscore.ui.Notices.activeForTest().stream()
                .anyMatch(line -> line.startsWith("quests/done:" + TEAM_QUEST + " CARD on")) && !works.repka.ryscore.ui.Notices.playedForTest().isEmpty());
        step("let it slide in", () -> works.repka.ryscore.ui.Notices.playedForTest().forEach(line -> LOGGER.info("[autotest] notice sound: {}", line)), 14);
        screenshot("team_b_notice_mate");
        expect("the single reward is taken by the first", () -> ClientProgress.get(TEAM_QUEST).claimed().contains(1) && !ClientProgress.get(TEAM_QUEST).claimed().contains(0));
        teamScreens("b_before_claim");
        step("claim second", () -> PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.CLAIM, TEAM_QUEST, 0, List.of())), 20);
        expect("got only the reward for each", () -> count(Items.BREAD) == 1 && count(Items.DIAMOND) == 0);
        teamScreens("b");
        addShopMemberSteps();
        addTreasuryMemberSteps();
        addRoundMemberSteps();
        // the book's editor names a new guide itself (guide_1); it saves no pictures and no sections of the old constructor
        expect("the saved guide reached the other player", () -> serverGuide() != null);
        expect("the import knows this player took the reward", () -> USE_RYSTEAMS || ClientProgress.get(TEAM_IMPORTED).completed() && ClientProgress.get(TEAM_IMPORTED).claimed().contains(0));
        step("try to save without rights", () -> PacketDistributor.sendToServer(new EditorSavePayload(Optional.of(serverGuide()), Optional.empty(), "en_us", Map.of(), Map.of(), List.of())), 20);
        expect("the server refused", () -> "rysquests.editor.error.denied".equals(BookScreen.lastEditorResultForTest()));
        step("say ready for the lock", () -> PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.CHECK, TEAM_READY, 0, List.of())), 20);
        expect("the closed book left this player", () -> !ClientBook.current().meta().published() && ClientBook.current().quests().isEmpty());
        step("open the book while it is closed", () -> pressKey(GLFW.GLFW_KEY_K), 20);
        screenshot("team_b_closed_player");
        step("close it", () -> mc().setScreen(null), 5);
        expect("the book came back when it opened", () -> ClientBook.current().meta().published() && ClientBook.current().quests().containsKey(TEAM_QUEST));
        step("log the progress after the lock", () -> LOGGER.info("[autotest] after the lock: shared {}, imported {}", ClientProgress.get(TEAM_QUEST), ClientProgress.get(TEAM_IMPORTED)), 20);
        // the FTB import of the leader replaced the team's progress with the file's: the imported quest is what the team has now
        expect("and the progress with it", () -> USE_RYSTEAMS || ClientProgress.get(TEAM_IMPORTED).completed() && ClientProgress.get(TEAM_IMPORTED).claimed().contains(0));
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

    // --- the canvas of the map ---

    private static final works.repka.rysquests.data.QuestBook[] CANVAS_BOOKS = new works.repka.rysquests.data.QuestBook[2];
    private static final works.repka.rysquests.client.layout.MapBook[] CANVAS_MAPS = new works.repka.rysquests.client.layout.MapBook[2];
    private static final java.util.List<java.util.Set<ResourceLocation>> CANVAS_DONE = new ArrayList<>(java.util.Arrays.asList(null, null));
    private static final ResourceLocation[] CANVAS_HERE = new ResourceLocation[2];
    private static CanvasTestScreen canvasScreen;

    /**
     * The canvas on its own test screen: RepYS as a serpentine on all four levels, with side quests hidden and a route lit, as a
     * spiral and as a generator; ATM10 as a galaxy; and the loading page. Every view is shot and its frame timed into the log.
     */
    private static void buildCanvasScenario() {
        openWorld();
        step("match the window of the mockups", () -> {
            mc().options.guiScale().set(2);
            mc().options.languageCode = "ru_ru";
            mc().getLanguageManager().setSelected("ru_ru");
            mc().resizeDisplay();
            mc().reloadResourcePacks();
        }, 40);
        expect("language applied", () -> mc().getOverlay() == null);
        step("read the books of RepYS and ATM10", () -> {
            try {
                CANVAS_BOOKS[0] = CanvasBooks.repys(Path.of(System.getProperty("rysquests.autotest.repysBook")), Path.of(System.getProperty("rysquests.autotest.repysLang")));
                CANVAS_BOOKS[1] = CanvasBooks.atm10(Path.of(System.getProperty("rysquests.autotest.atm10Quests")));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            for (int i = 0; i < 2; i++) {
                CANVAS_MAPS[i] = works.repka.rysquests.client.layout.MapBook.of(CANVAS_BOOKS[i]);
            }
            works.repka.rysquests.client.layout.MapBook repys = CANVAS_MAPS[0];
            CANVAS_HERE[0] = repys.quests().stream().map(works.repka.rysquests.client.layout.MapBook.Q::id)
                    .filter(id -> id.getPath().equals("b03_e2_current/electric_mechanism")).findFirst()
                    .orElse(repys.quests().stream().filter(q -> repys.chapters().get(q.chapter()).epoch() == 2 && !q.optional()).findFirst().orElseThrow().id());
            CANVAS_DONE.set(0, CanvasBooks.repysProgress(CANVAS_BOOKS[0], repys, CANVAS_HERE[0]));
            CANVAS_DONE.set(1, CanvasBooks.atmProgress(CANVAS_BOOKS[1], CANVAS_MAPS[1]));
            LOGGER.info("[autotest] canvas books: RepYS {} quests in {} chapters on the map, ATM10 {} quests in {} chapters", repys.quests().size(), repys.chapters().size(),
                    CANVAS_MAPS[1].quests().size(), CANVAS_MAPS[1].chapters().size());
        }, 1);
        step("time the layouts", () -> {
            for (int i = 0; i < 2; i++) {
                for (works.repka.rysquests.data.MapForm form : List.of(works.repka.rysquests.data.MapForm.SERPENTINE, works.repka.rysquests.data.MapForm.SPIRAL,
                        works.repka.rysquests.data.MapForm.GENERATOR, works.repka.rysquests.data.MapForm.GALAXY)) {
                    long start = System.nanoTime();
                    works.repka.rysquests.client.layout.CanvasLayout layout = new works.repka.rysquests.client.layout.LayoutEngine().lay(CANVAS_MAPS[i], form);
                    LOGGER.info("[autotest] canvas layout {} {}: {} ms, {} quests, box {} x {}", i == 0 ? "RepYS" : "ATM10", form.getSerializedName(), (System.nanoTime() - start) / 1_000_000,
                            layout.x.length, Math.round(layout.maxX - layout.minX), Math.round(layout.maxY - layout.minY));
                }
            }
        }, 1);
        step("open the canvas screen", () -> {
            canvasScreen = new CanvasTestScreen();
            mc().setScreen(canvasScreen);
        }, 5);
        expect("canvas screen open", () -> mc().screen == canvasScreen);

        // RepYS as a serpentine, its own form
        showCanvas(0, works.repka.rysquests.data.MapForm.SERPENTINE);
        canvasShot("repys_serp_book", () -> {});
        canvasShot("repys_serp_hover", () -> pointAtChapter(0, "b04_e2_oil"));
        canvasShot("repys_serp_epoch", () -> canvasScreen.canvas.flyToEpoch(2));
        canvasShot("repys_serp_chapter", () -> canvasScreen.canvas.flyToChapter(ResourceLocation.parse("repys:b03_e2_current")));
        canvasShot("repys_serp_close", () -> {
            canvasScreen.canvas.select(CANVAS_HERE[0]);
            canvasScreen.canvas.flyToQuest(CANVAS_HERE[0]);
        });
        canvasShot("repys_serp_main_only", () -> {
            canvasScreen.canvas.select(null);
            canvasScreen.canvas.setShowSide(false);
            canvasScreen.canvas.flyToChapter(ResourceLocation.parse("repys:b03_e2_current"));
        });
        canvasShot("repys_serp_route", () -> {
            canvasScreen.canvas.setShowSide(true);
            works.repka.rysquests.client.layout.MapBook repys = CANVAS_MAPS[0];
            ResourceLocation goal = repys.quests().stream().filter(q -> q.milestone() && repys.chapters().get(q.chapter()).id().getPath().equals("b03_e2_current")
                    && !CANVAS_DONE.get(0).contains(q.id())).map(works.repka.rysquests.client.layout.MapBook.Q::id).findFirst().orElse(CANVAS_HERE[0]);
            canvasScreen.canvas.setRoute(CanvasBooks.route(CANVAS_BOOKS[0], CANVAS_DONE.get(0), goal));
            canvasScreen.canvas.select(goal);
            canvasScreen.canvas.flyToChapter(ResourceLocation.parse("repys:b03_e2_current"));
        });
        step("clear the route", () -> {
            canvasScreen.canvas.setRoute(List.of());
            canvasScreen.canvas.select(null);
        }, 1);
        // RepYS as a spiral and as a generator
        showCanvas(0, works.repka.rysquests.data.MapForm.SPIRAL);
        canvasShot("repys_spiral_book", () -> {});
        canvasShot("repys_spiral_epoch", () -> canvasScreen.canvas.flyToEpoch(2));
        showCanvas(0, works.repka.rysquests.data.MapForm.GENERATOR);
        canvasShot("repys_gen_book", () -> {});
        // ATM10 as a galaxy, its own form
        showCanvas(1, works.repka.rysquests.data.MapForm.GALAXY);
        canvasShot("atm_galaxy_book", () -> {});
        canvasShot("atm_galaxy_hover", () -> pointAtChapter(1, "mekanism"));
        canvasShot("atm_galaxy_chapter", () -> {
            canvasScreen.pointer = new int[] {-100, -100};
            canvasScreen.canvas.flyToChapter(ResourceLocation.parse("atm:create"));
        });
        canvasShot("atm_galaxy_close", () -> {
            works.repka.rysquests.client.layout.MapBook atm = CANVAS_MAPS[1];
            ResourceLocation open = atm.quests().stream().filter(q -> atm.chapters().get(q.chapter()).id().getPath().equals("create") && !CANVAS_DONE.get(1).contains(q.id()))
                    .map(works.repka.rysquests.client.layout.MapBook.Q::id).findFirst().orElseThrow();
            canvasScreen.canvas.select(open);
            canvasScreen.canvas.flyToQuest(open);
        });
        // ATM10 at full size again, measured longer: the frame budget of picture 47
        canvasShot("atm_galaxy_book_again", () -> {
            canvasScreen.canvas.select(null);
            canvasScreen.canvas.flyToBook();
        });
        step("check the frame budget", () -> {
            double ms = canvasScreen.averageMs();
            LOGGER.info("[autotest] canvas budget: ATM10 whole book {} ms a frame on the CPU, the goal is 4", String.format(java.util.Locale.ROOT, "%.2f", ms));
        }, 1);
        // the loading page, picture 49
        step("show the loading page", () -> {
            canvasScreen.pointer = new int[] {-100, -100};
            canvasScreen.canvas.setLayout(CANVAS_BOOKS[1], CANVAS_MAPS[1], null);
            canvasScreen.canvas.setLoading(() -> 0.62, () -> {});
        }, 10);
        screenshot("canvas_loading");
    }

    // --- the map screen in the book ---

    private static boolean mapTiming;
    private static long mapFrames;
    private static long mapNanos;
    private static long mapWorst;

    /**
     * The map tab of the real book screen, on RepYS (serpentine and spiral) and ATM10 (galaxy) given to the client with a made up
     * progress: every picture of the map, 13 to 18, 22, 41, 43, 46, 47 and 49, with the canvas frame timed into the log.
     */
    private static void buildMapScenario() {
        openWorld();
        step("match the window of the mockups", () -> {
            mc().options.guiScale().set(2);
            mc().options.languageCode = "ru_ru";
            mc().getLanguageManager().setSelected("ru_ru");
            mc().resizeDisplay();
            mc().reloadResourcePacks();
        }, 40);
        expect("language applied", () -> mc().getOverlay() == null);
        step("read the books of RepYS and ATM10", () -> {
            try {
                CANVAS_BOOKS[0] = CanvasBooks.repys(Path.of(System.getProperty("rysquests.autotest.repysBook")), Path.of(System.getProperty("rysquests.autotest.repysLang")));
                CANVAS_BOOKS[1] = CanvasBooks.atm10(Path.of(System.getProperty("rysquests.autotest.atm10Quests")));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            for (int i = 0; i < 2; i++) {
                CANVAS_MAPS[i] = works.repka.rysquests.client.layout.MapBook.of(CANVAS_BOOKS[i]);
            }
            works.repka.rysquests.client.layout.MapBook repys = CANVAS_MAPS[0];
            CANVAS_HERE[0] = repys.quests().stream().map(works.repka.rysquests.client.layout.MapBook.Q::id)
                    .filter(id -> id.getPath().equals("b03_e2_current/electric_mechanism")).findFirst().orElseThrow();
            CANVAS_DONE.set(0, CanvasBooks.repysProgress(CANVAS_BOOKS[0], repys, CANVAS_HERE[0]));
            CANVAS_DONE.set(1, CanvasBooks.atmProgress(CANVAS_BOOKS[1], CANVAS_MAPS[1]));
        }, 1);
        mapBook(0, ResourceLocation.parse("repys:b03_e2_current"));
        step("mouse out of the way, no messages", () -> {
            moveMouse(1, 1);
            ClientMessages.dismiss();
            ThemeManager.select("ryscore:slate");
        }, 2);
        step("open the book", () -> pressKey(GLFW.GLFW_KEY_K), 10);
        expect("book screen open", () -> mc().screen instanceof BookScreen);
        step("open the map tab", () -> {
            BookScreen.openTabForTest("map");
            BookScreen.showForTest(ViewMode.MAP);
        }, 10);
        expect("the map is laid out", BookScreen::mapReadyForTest);
        mapShot("13_map_book", () -> {
            // opening a screen puts the cursor in the middle of the window
            moveMouse(1, 1);
            BookScreen.mapFlyForTest(null, 0, null);
        });
        mapShot("14_map_book_collapsed", () -> BookScreen.foldSidebarForTest(true));
        step("unfold the sidebar", () -> BookScreen.foldSidebarForTest(false), 5);
        mapShot("15_map_hover_chapter", () -> pointAt(ResourceLocation.parse("repys:b05_e3_osmium")));
        mapShot("15b_map_epoch", () -> {
            moveMouse(1, 1);
            BookScreen.mapFlyForTest(null, 2, null);
        });
        mapShot("16_map_chapter", () -> BookScreen.mapFlyForTest(ResourceLocation.parse("repys:b03_e2_current"), 0, null));
        mapShot("17_map_close_quest", () -> BookScreen.mapFlyForTest(null, 0, CANVAS_HERE[0]));
        mapShot("18_map_main_only_legend", () -> {
            BookScreen.selectForTest(ResourceLocation.parse("repys:b03_e2_current"), null);
            BookScreen.mapFlyForTest(null, 0, null);
            BookScreen.mapSideForTest(false);
            BookScreen.mapLegendForTest(true);
        });
        mapShot("22_map_route_to_goal", () -> {
            BookScreen.mapLegendForTest(false);
            BookScreen.mapSideForTest(true);
            BookScreen.mapFlyForTest(ResourceLocation.parse("repys:b03_e2_current"), 0, null);
            BookScreen.selectForTest(ResourceLocation.parse("repys:b03_e2_current"), ResourceLocation.parse("repys:b03_e2_current/heavy_engineering_block"));
        });
        step("log you are here", () -> LOGGER.info("[autotest] map here: {}", BookScreen.mapHereForTest()), 1);
        // the compact layout: a window narrower than 1280
        step("narrow the window", () -> {
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc().getWindow().getWindow(), 1100, 680);
        }, 20);
        expect("the book is compact", BookScreen::compactForTest);
        mapShot("46_small_screen_compact", () -> BookScreen.mapFlyForTest(null, 0, CANVAS_HERE[0]));
        step("widen the window again", () -> {
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc().getWindow().getWindow(), 1280, 800);
        }, 20);
        expect("the book is not compact", () -> !BookScreen.compactForTest());
        // RepYS as a spiral
        step("give RepYS as a spiral", () -> {
            try {
                CANVAS_BOOKS[0] = CanvasBooks.repys(Path.of(System.getProperty("rysquests.autotest.repysBook")), Path.of(System.getProperty("rysquests.autotest.repysLang")), "spiral");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            ClientBook.overrideForTest(CANVAS_BOOKS[0], true);
            BookScreen.selectForTest(ResourceLocation.parse("repys:b03_e2_current"), null);
        }, 5);
        expect("the spiral is laid out", BookScreen::mapReadyForTest);
        mapShot("41_map_form_spiral", () -> BookScreen.mapFlyForTest(null, 0, null));
        MapFixChecks.build(() -> works.repka.rysquests.client.layout.MapBook.of(CANVAS_BOOKS[0]));
        // ATM10 as a galaxy
        mapBook(1, null);
        step("open the map of ATM10", () -> {
            BookScreen.selectForTest(null, null);
            BookScreen.openTabForTest("map");
            BookScreen.showForTest(ViewMode.MAP);
        }, 10);
        expect("the galaxy is laid out", BookScreen::mapReadyForTest);
        mapShot("47_map_far_budget", () -> {
            moveMouse(1, 1);
            BookScreen.mapFlyForTest(null, 0, null);
        });
        step("check the frame budget", () -> LOGGER.info("[autotest] map budget: ATM10 whole book {} ms a frame on the CPU, the goal is 4",
                String.format(java.util.Locale.ROOT, "%.2f", mapFrames == 0 ? 0 : mapNanos / 1e6 / mapFrames)), 1);
        mapShot("43_map_form_galaxy", () -> pointAt(ResourceLocation.parse("atm:mekanism")));
        step("hold the map on loading", () -> {
            moveMouse(1, 1);
            works.repka.rysquests.client.canvas.MapLayouts.pauseForTest(true);
        }, 10);
        screenshot("map_49_map_loading");
        step("let the map go", () -> works.repka.rysquests.client.canvas.MapLayouts.pauseForTest(false), 10);
        step("give the book back", () -> ClientBook.overrideForTest(null, null), 1);
    }

    /** Pans of the drift scenario in mockup pixels: fractions of a pixel, as a drag at a scale other than 100 % or a zoom gives. */
    private static final double[][] DRIFT_PANS = {{0.5, 0.25}, {0.75, 0.5}, {1.0, 0.75}, {1.25, 0.33}, {0.6, 1.1}, {3.4, -2.7}};
    private static float[] driftBase;
    /** The quest the drift scenario moves the map around; its book wears vanilla icons (CanvasBooks.repysVanillaIcons). */
    private static ResourceLocation driftQuest;
    private static final float[] DRIFT_WORST = new float[2];

    /**
     * Items on the map stay in their tiles while the map moves (the author's report after 0.2.2: items "floated" against their nodes on a
     * drag). Opens RepYS close on a quest, pans the map by fractions of a pixel in steps with a screenshot after each, and compares where
     * every item stands against its own tile: the way of 0.2.2 first (the "before" shots, which must show the drift), then the fixed one,
     * where nothing may move by even a hundredth of a pixel.
     */
    private static void buildMapDriftScenario() {
        openWorld();
        step("match the window of the mockups", () -> {
            mc().options.guiScale().set(2);
            mc().options.languageCode = "ru_ru";
            mc().getLanguageManager().setSelected("ru_ru");
            mc().resizeDisplay();
            mc().reloadResourcePacks();
        }, 40);
        expect("language applied", () -> mc().getOverlay() == null);
        step("read the book of RepYS", () -> {
            try {
                CANVAS_BOOKS[0] = CanvasBooks.repysVanillaIcons(Path.of(System.getProperty("rysquests.autotest.repysBook")), Path.of(System.getProperty("rysquests.autotest.repysLang")));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            CANVAS_MAPS[0] = works.repka.rysquests.client.layout.MapBook.of(CANVAS_BOOKS[0]);
            CANVAS_HERE[0] = CANVAS_MAPS[0].quests().stream().map(works.repka.rysquests.client.layout.MapBook.Q::id)
                    .filter(id -> id.getPath().equals("b03_e2_current/electric_mechanism")).findFirst().orElseThrow();
            CANVAS_DONE.set(0, CanvasBooks.repysProgress(CANVAS_BOOKS[0], CANVAS_MAPS[0], CANVAS_HERE[0]));
            driftQuest = CANVAS_HERE[0];
            LOGGER.info("[autotest] drift: the map moves around {}", driftQuest);
        }, 1);
        mapBook(0, ResourceLocation.parse("repys:b03_e2_current"));
        step("mouse out of the way, no messages", () -> {
            moveMouse(1, 1);
            ClientMessages.dismiss();
            ThemeManager.select("ryscore:slate");
        }, 2);
        step("open the book", () -> pressKey(GLFW.GLFW_KEY_K), 10);
        expect("book screen open", () -> mc().screen instanceof BookScreen);
        step("open the map tab", () -> {
            BookScreen.openTabForTest("map");
            BookScreen.showForTest(ViewMode.MAP);
        }, 10);
        expect("the map is laid out", BookScreen::mapReadyForTest);
        for (int run = 0; run < 2; run++) {
            boolean old = run == 0;
            String name = old ? "before" : "after";
            step("draw items the " + name + " way", () -> {
                works.repka.rysquests.client.canvas.MapCanvas.oldIconSnapForTest(old);
                moveMouse(1, 1);
                BookScreen.mapFlyForTest(null, 0, driftQuest);
            }, 25);
            expect("the flight to the quest is over (" + name + ")", BookScreen::mapReadyForTest);
            step("settle (" + name + ")", () -> {}, 10);
            step("start the worst of the drift (" + name + ")", () -> DRIFT_WORST[old ? 0 : 1] = 0, 1);
            screenshot("drift_" + name + "_open");
            expect("items are on screen (" + name + ")", () -> {
                driftBase = BookScreen.mapCanvasForTest().iconOffsetsForTest();
                for (float v : driftBase) {
                    if (!Float.isNaN(v)) {
                        return true;
                    }
                }
                return false;
            });
            screenshot("drift_" + name + "_0");
            for (int i = 0; i < DRIFT_PANS.length; i++) {
                double[] pan = DRIFT_PANS[i];
                int shot = i + 1;
                step("pan " + pan[0] + ", " + pan[1] + " (" + name + ")", () -> BookScreen.mapCanvasForTest().panForTest(pan[0], pan[1]), 5);
                step("compare the items (" + name + " " + shot + ")", () -> {
                    float[] now = BookScreen.mapCanvasForTest().iconOffsetsForTest();
                    float worst = 0;
                    int compared = 0;
                    for (int j = 0; j < Math.min(now.length, driftBase.length); j++) {
                        if (!Float.isNaN(now[j]) && !Float.isNaN(driftBase[j])) {
                            worst = Math.max(worst, Math.abs(now[j] - driftBase[j]));
                            compared++;
                        }
                    }
                    DRIFT_WORST[old ? 0 : 1] = Math.max(DRIFT_WORST[old ? 0 : 1], worst);
                    LOGGER.info("[autotest] drift {} step {}: {} item coordinates compared, the most an item moved against its tile {} px",
                            name, shot, compared, String.format(java.util.Locale.ROOT, "%.3f", worst));
                }, 1);
                screenshot("drift_" + name + "_" + shot);
            }
        }
        step("log the drift", () -> LOGGER.info("[autotest] drift: before {} px, after {} px",
                String.format(java.util.Locale.ROOT, "%.3f", DRIFT_WORST[0]), String.format(java.util.Locale.ROOT, "%.3f", DRIFT_WORST[1])), 1);
        expect("the old way shows the drift (the scenario sees the bug)", () -> DRIFT_WORST[0] >= 0.5f);
        expect("items stay in their tiles while the map moves", () -> DRIFT_WORST[1] < 0.01f);
        step("give the book back", () -> {
            works.repka.rysquests.client.canvas.MapCanvas.oldIconSnapForTest(false);
            ClientBook.overrideForTest(null, null);
        }, 1);
    }

    /**
     * The RepYS book made ready for the new book (plan step 13), from a copy given by {@code -PrepysBook} and {@code -PrepysLang}: it
     * names its nine epochs, comes to the serpentine on its own and brings a fresh {@code layout.json}, so the map is there at once
     * without laying anything out. Shoots the whole book with the sidebar of epoch names and the level of epoch V.
     */
    private static void buildRepysDataScenario() {
        openWorld();
        step("match the window of the mockups", () -> {
            mc().options.guiScale().set(2);
            mc().options.languageCode = "ru_ru";
            mc().getLanguageManager().setSelected("ru_ru");
            mc().resizeDisplay();
            mc().reloadResourcePacks();
        }, 40);
        expect("language applied", () -> mc().getOverlay() == null);
        step("read the copy of the RepYS book", () -> {
            try {
                CANVAS_BOOKS[0] = CanvasBooks.repys(Path.of(System.getProperty("rysquests.autotest.repysBook")), Path.of(System.getProperty("rysquests.autotest.repysLang")));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            CANVAS_MAPS[0] = works.repka.rysquests.client.layout.MapBook.of(CANVAS_BOOKS[0]);
            CANVAS_HERE[0] = CANVAS_MAPS[0].quests().stream().map(works.repka.rysquests.client.layout.MapBook.Q::id)
                    .filter(id -> id.getPath().equals("b03_e2_current/electric_mechanism")).findFirst().orElseThrow();
            CANVAS_DONE.set(0, CanvasBooks.repysProgress(CANVAS_BOOKS[0], CANVAS_MAPS[0], CANVAS_HERE[0]));
            LOGGER.info("[autotest] repys data: epochs {}, form {}, {} chapters and {} quests on the map, layout {}", CANVAS_BOOKS[0].meta().epochs().stream()
                    .map(works.repka.rysquests.data.BookMeta.Epoch::title).toList(), works.repka.rysquests.data.MapForms.choose(CANVAS_BOOKS[0]),
                    CANVAS_MAPS[0].chapters().size(), CANVAS_MAPS[0].quests().size(), CANVAS_BOOKS[0].freshLayout().isPresent() ? "fresh" : "missing or stale");
        }, 1);
        expect("nine named epochs", () -> CANVAS_BOOKS[0].meta().epochs().size() == 9
                && CANVAS_BOOKS[0].meta().epochs().stream().noneMatch(epoch -> epoch.title().startsWith("book.")));
        expect("the serpentine by the rule", () -> works.repka.rysquests.data.MapForms.choose(CANVAS_BOOKS[0]).form() == works.repka.rysquests.data.MapForm.SERPENTINE);
        expect("the baked layout fits the book", () -> CANVAS_BOOKS[0].freshLayout().isPresent());
        boolean[] fromFile = new boolean[1];
        // ready at the first asking: read from layout.json, since a layout worked out now would take a while
        step("ask for the map once", () -> fromFile[0] = works.repka.rysquests.client.canvas.MapLayouts.get(CANVAS_BOOKS[0]).ready(), 1);
        expect("the map comes from the file, nothing is laid out", () -> fromFile[0]);
        mapBook(0, ResourceLocation.parse("repys:b03_e2_current"));
        step("mouse out of the way, no messages", () -> {
            moveMouse(1, 1);
            ClientMessages.dismiss();
            ThemeManager.select("ryscore:slate");
        }, 2);
        step("open the book", () -> pressKey(GLFW.GLFW_KEY_K), 10);
        expect("book screen open", () -> mc().screen instanceof BookScreen);
        step("open the map tab", () -> {
            BookScreen.openTabForTest("map");
            BookScreen.showForTest(ViewMode.MAP);
        }, 10);
        expect("the map is laid out", BookScreen::mapReadyForTest);
        mapShot("repys_data_book", () -> {
            moveMouse(1, 1);
            BookScreen.mapFlyForTest(null, 0, null);
        });
        mapShot("repys_data_epoch_v", () -> BookScreen.mapFlyForTest(null, 5, null));
        step("give the book back", () -> ClientBook.overrideForTest(null, null), 1);
    }

    /** Gives the client a book and its made up progress. */
    private static void mapBook(int book, @org.jetbrains.annotations.Nullable ResourceLocation last) {
        step("give the client book " + book, () -> {
            ClientBook.overrideForTest(CANVAS_BOOKS[book], true);
            Map<ResourceLocation, works.repka.rysquests.progress.QuestProgress> quests = new java.util.HashMap<>();
            CANVAS_DONE.get(book).forEach(id -> quests.put(id, new works.repka.rysquests.progress.QuestProgress(List.of(), 1L, List.of())));
            works.repka.rysquests.client.ClientProgress.receiveForTest(quests, Optional.ofNullable(last));
        }, 5);
    }

    /** Points the real mouse at the middle of a chapter's area. */
    private static void pointAt(ResourceLocation chapter) {
        float[] area = BookScreen.mapChapterForTest(chapter);
        if (area != null) {
            moveMouse((area[0] + area[2] / 2) / 2, (area[1] + area[3] * 0.62) / 2);
        }
    }

    /** Does something on the map, lets the flight end, shoots it and times the canvas. */
    private static void mapShot(String name, Runnable action) {
        step("map " + name, action, 25);
        screenshot("map_" + name);
        step("time " + name, () -> {
            mapFrames = 0;
            mapNanos = 0;
            mapWorst = 0;
            mapTiming = true;
        }, 60);
        step("log " + name, () -> {
            mapTiming = false;
            LOGGER.info("[autotest] map frame {}: canvas {} ms (worst {} ms) over {} frames; {}", name, String.format(java.util.Locale.ROOT, "%.2f", mapFrames == 0 ? 0 : mapNanos / 1e6 / mapFrames),
                    String.format(java.util.Locale.ROOT, "%.2f", mapWorst / 1e6), mapFrames, BookScreen.mapStatsForTest());
        }, 1);
    }

    /** Lays the book out in the form through the client's layouts and gives it to the canvas with the made up progress. */
    private static void showCanvas(int book, works.repka.rysquests.data.MapForm form) {
        step("lay out " + book + " as " + form.getSerializedName(), () -> works.repka.rysquests.client.canvas.MapLayouts.get(CANVAS_BOOKS[book], form), 1);
        expect("laid out " + book + " as " + form.getSerializedName(), () -> works.repka.rysquests.client.canvas.MapLayouts.get(CANVAS_BOOKS[book], form).ready());
        step("show " + book + " as " + form.getSerializedName(), () -> {
            works.repka.rysquests.client.canvas.MapLayouts.State state = works.repka.rysquests.client.canvas.MapLayouts.get(CANVAS_BOOKS[book], form);
            CanvasBooks.Progress progress = CanvasBooks.progress(CANVAS_BOOKS[book], CANVAS_DONE.get(book));
            canvasScreen.pointer = new int[] {-100, -100};
            canvasScreen.canvas.setLoading(null, null);
            canvasScreen.canvas.setShowSide(true);
            canvasScreen.canvas.setLayout(CANVAS_BOOKS[book], state.map(), state.layout());
            canvasScreen.canvas.setStatus(progress.status(), () -> 1);
            canvasScreen.canvas.setHere(book == 0 ? CANVAS_HERE[0] : null);
        }, 10);
    }

    /** Does something to the canvas, lets the flight end, shoots it and times its frames. */
    private static void canvasShot(String name, Runnable action) {
        step("canvas " + name, () -> {
            // the pointer stays only for the hover views, which set it in their action
            canvasScreen.pointer = new int[] {-100, -100};
            action.run();
        }, 25);
        screenshot("canvas_" + name);
        step("time " + name, () -> canvasScreen.resetTiming(), 60);
        step("log " + name, () -> LOGGER.info("[autotest] canvas frame {}: {}", name, canvasScreen.timing()), 1);
    }

    private static void pointAtChapter(int book, String path) {
        works.repka.rysquests.client.layout.MapBook map = CANVAS_MAPS[book];
        works.repka.rysquests.client.layout.MapBook.Chap chapter = map.chapters().stream().filter(c -> c.id().getPath().equals(path)).findFirst().orElse(map.chapters().get(map.chapters().size() / 2));
        works.repka.rysquests.client.layout.CanvasLayout.Region region = canvasScreen.canvas.layout().regions[chapter.index()];
        works.repka.rysquests.client.layout.CanvasLayout.Label label = canvasScreen.canvas.layout().labels[chapter.index()];
        float[] at = region.kind() == works.repka.rysquests.client.layout.CanvasLayout.RegionKind.RECT
                ? canvasScreen.canvas.screenOf(region.centerX(), region.centerY() + (region.maxY() - region.minY()) * 0.3)
                : canvasScreen.canvas.screenOf(label.x(), label.y());
        canvasScreen.pointer = new int[] {Math.round(at[0]), Math.round(at[1])};
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
        step("open the editor's tabs on the mining atlas", () -> {
            EditorTesting.open("tabs");
            EditorTesting.tab(ResourceLocation.parse("design:mining"));
            EditorTesting.cell(0, 1);
        }, 20);
        screenshot("design_constructor");
        step("leave the editor", EditorTesting::leave, 8);
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
    private static final ResourceLocation TEAM_READY = ResourceLocation.parse("teamtest:ready");

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

    private static final ResourceLocation TEAM_DAILY = ResourceLocation.parse("teamtest:daily");

    /**
     * A repeatable quest in a team (ADR-52), the leader's side: it finishes round one and takes it first, so the other player has
     * not taken theirs when round two starts; then it waits until the other player takes round two first and takes what is left.
     */
    private static void addRoundLeaderSteps() {
        int[] base = new int[1];
        step("count the apples before", () -> {
            base[0] = count(Items.APPLE);
            LOGGER.info("[autotest] apples before the daily quest: {}", base[0]);
        }, 0);
        step("finish round one of the daily quest", () -> PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.CHECK, TEAM_DAILY, 0, List.of())), 20);
        expect("round one is done", () -> ClientProgress.get(TEAM_DAILY).completed());
        step("take round one first", () -> PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.CLAIM, TEAM_DAILY, 0, List.of())), 20);
        step("log the daily quest after round one", () -> LOGGER.info("[autotest] daily after round one: {}, apples {}", ClientProgress.get(TEAM_DAILY), count(Items.APPLE)), 0);
        expect("round one paid, round two started", () -> count(Items.APPLE) == base[0] + 1 && !ClientProgress.get(TEAM_DAILY).completed() && ClientProgress.get(TEAM_DAILY).times() == 1);
        // The other player takes round two first: round three starts, and round two waits here as done.
        expect("round two waits for this player after the other started round three", () -> ClientProgress.get(TEAM_DAILY).completed()
                && ClientProgress.get(TEAM_DAILY).times() == 2 && ClientProgress.get(TEAM_DAILY).claimed().isEmpty());
        step("take round two late", () -> PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.CLAIM, TEAM_DAILY, 0, List.of())), 20);
        expect("round two paid late, the team's round three shows", () -> count(Items.APPLE) == base[0] + 2 && !ClientProgress.get(TEAM_DAILY).completed());
        step("try to take it again", () -> PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.CLAIM, TEAM_DAILY, 0, List.of())), 20);
        expect("nothing is paid twice", () -> count(Items.APPLE) == base[0] + 2);
    }

    /** The other player's side of the daily quest: round one waits for them although round two has started. */
    private static void addRoundMemberSteps() {
        expect("round one waits for this player while the team is on round two", () -> ClientProgress.get(TEAM_DAILY).completed()
                && ClientProgress.get(TEAM_DAILY).times() == 1 && ClientProgress.get(TEAM_DAILY).claimed().isEmpty());
        step("take round one late", () -> PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.CLAIM, TEAM_DAILY, 0, List.of())), 20);
        expect("round one paid late, the team's round two shows", () -> count(Items.APPLE) == 1 && !ClientProgress.get(TEAM_DAILY).completed());
        step("try to take it again", () -> PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.CLAIM, TEAM_DAILY, 0, List.of())), 20);
        expect("nothing is paid twice", () -> count(Items.APPLE) == 1);
        step("finish round two", () -> PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.CHECK, TEAM_DAILY, 0, List.of())), 20);
        expect("round two is done", () -> ClientProgress.get(TEAM_DAILY).completed());
        step("take round two first", () -> PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.CLAIM, TEAM_DAILY, 0, List.of())), 20);
        expect("round two paid, round three started", () -> count(Items.APPLE) == 2 && !ClientProgress.get(TEAM_DAILY).completed() && ClientProgress.get(TEAM_DAILY).times() == 2);
    }

    /** The treasury needs RYSTeams' right "spend_treasury": the leader has it by default. */
    private static void addTreasuryLeaderSteps() {
        if (!USE_RYSTEAMS) {
            return;
        }
        step("fill the treasury", () -> command("rysquests wallet treasury add TestA coin 50"), 20);
        expect("the leader may pay from the treasury", () -> ClientShop.treasury("coin") == 50 && ClientShop.spendTreasury());
        step("buy bread from the treasury", () -> tradeFrom(TEAM_BREAD, true), 15);
        expect("the treasury paid", () -> ClientShop.treasury("coin") == 45);
    }

    /** A plain member has no right to the treasury by default: the book does not offer it and the server refuses. */
    private static void addTreasuryMemberSteps() {
        if (!USE_RYSTEAMS) {
            return;
        }
        expect("the treasury is filled, but not for this player", () -> ClientShop.treasury("coin") == 45 && !ClientShop.spendTreasury());
        step("try to pay from the treasury", () -> {
            BookScreen.clearShopResultForTest();
            tradeFrom(TEAM_BREAD, true);
        }, 15);
        expect("the server refuses without the right", () -> "rysquests.shop.error.no_right".equals(BookScreen.lastShopResultForTest()));
        expect("the treasury is untouched", () -> ClientShop.treasury("coin") == 45);
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
        ResourceLocation[] guide = new ResourceLocation[1];
        step("write a guide in the editor", () -> {
            EditorTesting.open("guides");
            EditorTesting.newGuide("Written on the server", "Written at " + System.currentTimeMillis());
            guide[0] = EditorTesting.guide();
        }, 5);
        step("save it", EditorTesting::save, 40);
        expect("the server saved it", () -> !EditorTesting.saving() && EditorTesting.unsaved() == 0);
        expect("the file is on the server", () -> Files.isRegularFile(mc().gameDirectory.toPath().resolve("../run-server/config/rysquests/pack/data/" + guide[0].getNamespace()
                + "/rysquests/guides/" + guide[0].getPath() + ".json")));
        step("close the book", () -> {
            EditorTesting.leave();
            mc().setScreen(null);
        }, 5);
    }

    private static final ResourceLocation SHOP_IRON = ResourceLocation.parse("example:iron");
    private static final ResourceLocation SHOP_WHEAT = ResourceLocation.parse("example:wheat");

    private static void trade(ResourceLocation offer) {
        tradeFrom(offer, false);
    }

    private static void tradeFrom(ResourceLocation offer, boolean treasury) {
        PacketDistributor.sendToServer(new ShopTradePayload(offer, 1, treasury));
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

    static void click(double x, double y) {
        mc().screen.mouseClicked(x / 2, y / 2, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    static void dragTo(double x, double y) {
        mc().screen.mouseDragged(x / 2, y / 2, GLFW.GLFW_MOUSE_BUTTON_LEFT, 0, 0);
    }

    static void release(double x, double y) {
        mc().screen.mouseReleased(x / 2, y / 2, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    /** The acceptance of the quest editor: a chapter of ten quests with dependencies, made in the game, read back from its files. */
    private static void buildQuestEditorScenario() {
        // The old full-window quest editor is in _stale/client-screen-editor; its acceptance lives on in the book editor's scenario.
        EditorScenario.build(false);
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
        // The section constructor became the editor's "Вкладки"; the book editor's scenario covers it.
        EditorScenario.build(false);
    }

    /** A guide made in the editor and read back, in a single player world. */
    private static void buildEditorScenario() {
        // The guide editor became the editor's "Гайды"; the book editor's scenario covers it.
        EditorScenario.build(false);
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
    /**
     * The shop, the achievements, the guides, the search and the HUD of the redesigned book (plan steps 8 and 9, pictures 23 to 31b),
     * each state with a screenshot to lay over its mockup. The showcase book and states come from {@code ExtrasShowcase}.
     */
    private static void buildExtrasScenario() {
        ResourceLocation tools = ResourceLocation.parse("example:start/first_tools");
        ResourceLocation furnace = ResourceLocation.parse("example:start/first_furnace");
        ResourceLocation dynamo = ResourceLocation.parse("example:power/dynamo");
        openWorld();
        expect("example book reached the client", () -> ClientBook.current().quests().size() == EXAMPLE_QUESTS);
        step("match the window of the mockups", () -> {
            mc().options.guiScale().set(2);
            mc().options.languageCode = "ru_ru";
            mc().getLanguageManager().setSelected("ru_ru");
            mc().resizeDisplay();
            mc().reloadResourcePacks();
        }, 40);
        expect("language applied", () -> mc().getOverlay() == null);
        step("reset the world", () -> {
            moveMouse(1, 1);
            ClientMessages.dismiss();
            ThemeManager.select("ryscore:slate");
            command("gamerule sendCommandFeedback false");
            command("time set day");
            command("clear @s");
            ClientBook.current().quests().keySet().forEach(id -> command("rysquests progress @s reset " + id));
        }, 30);
        step("things to sell", () -> {
            command("give @s minecraft:andesite 212");
            command("give @s minecraft:copper_ingot 96");
            command("give @s minecraft:coal 64");
            command("give @s minecraft:iron_ingot 20");
        }, 20);

        step("the first entry: the key hint", () -> {
            works.repka.rysquests.client.ClientConfig.KEY_HINT_SHOWN.set(false);
            ClientProgress.pinnedForTest(List.of());
            works.repka.rysquests.client.hud.StickerBoard.clearForTest();
        }, 10);
        screenshot("extras_hud_first_hint");
        step("pinned quests, one waiting for its reward, and a message under them", () -> {
            works.repka.rysquests.client.ClientConfig.KEY_HINT_SHOWN.set(true);
            ClientProgress.progressForTest(furnace, new works.repka.rysquests.progress.QuestProgress(List.of(1, 1), System.currentTimeMillis(), List.of()));
            ClientProgress.pinnedForTest(List.of(furnace, tools, ResourceLocation.parse("example:_achievements/iron_hoard/silver")));
            works.repka.rysquests.client.hud.StickerBoard.celebrateForTest(ClientBook.current().quests().get(dynamo));
        }, 10);
        expect("three cards", () -> works.repka.rysquests.client.hud.StickerBoard.cardsForTest() == 3);
        screenshot("extras_hud");
        step("a low window", () -> {
            mc().getWindow().setWindowed(960, 520);
            mc().resizeDisplay();
        }, 20);
        expect("two cards when the window is low", () -> works.repka.rysquests.client.hud.StickerBoard.cardsForTest() == 2);
        screenshot("extras_hud_low");
        step("back to the mockups' window", () -> {
            mc().getWindow().setWindowed(1280, 800);
            mc().resizeDisplay();
            works.repka.rysquests.client.hud.StickerBoard.clearForTest();
            ClientProgress.pinnedForTest(List.of());
            ClientProgress.progressForTest(furnace, works.repka.rysquests.progress.QuestProgress.EMPTY);
        }, 20);

        step("open the book", () -> pressKey(GLFW.GLFW_KEY_K), 10);
        expect("book screen open", () -> mc().screen instanceof BookScreen);
        step("the showcase book", works.repka.rysquests.client.screen.ExtrasShowcase::install, 10);
        expect("the hint is gone once the book was opened", () -> works.repka.rysquests.client.ClientConfig.KEY_HINT_SHOWN.get());
        step("shop: buy two tokens", () -> works.repka.rysquests.client.screen.ExtrasShowcase.shop("token", "all", 2, false, false), 10);
        expect("the shop is open", () -> "shop".equals(BookScreen.bodyForTest()));
        screenshot("extras_shop_buy");
        step("shop: a section from the list", () -> {
            works.repka.rysquests.client.screen.ExtrasShowcase.walletOnly(214);
            works.repka.rysquests.client.screen.ExtrasShowcase.shop("quartz", "IV Энергия и разум", 1, false, true);
        }, 10);
        screenshot("extras_shop_sections");
        step("shop: only available, the tip", () -> {
            works.repka.rysquests.client.screen.ExtrasShowcase.shop(null, "all", 1, false, false);
            works.repka.rysquests.client.screen.ExtrasShowcase.shopOnlyAvailable(true);
        }, 10);
        screenshot("extras_shop_only_available");
        step("shop: not enough coins", () -> {
            works.repka.rysquests.client.screen.ExtrasShowcase.shopOnlyAvailable(false);
            works.repka.rysquests.client.screen.ExtrasShowcase.walletOnly(6);
            works.repka.rysquests.client.screen.ExtrasShowcase.shop("steel", "all", 1, false, false);
        }, 10);
        screenshot("extras_shop_not_enough");
        step("shop: nothing for quests", () -> {
            works.repka.rysquests.client.screen.ExtrasShowcase.walletOnly(14);
            ClientProgress.progressForTest(tools, new works.repka.rysquests.progress.QuestProgress(List.of(8, 1), 0, List.of()));
            works.repka.rysquests.client.screen.ExtrasShowcase.shop(null, "quests", 1, false, false);
        }, 10);
        screenshot("extras_shop_filter_empty");
        step("shop: sell", () -> {
            ClientProgress.progressForTest(tools, works.repka.rysquests.progress.QuestProgress.EMPTY);
            works.repka.rysquests.client.screen.ExtrasShowcase.shop("brass", "all", 3, false, false);
        }, 10);
        screenshot("extras_shop_sell");
        step("shop: history", works.repka.rysquests.client.screen.ExtrasShowcase::shopHistory, 10);
        screenshot("extras_shop_history");

        step("achievements", () -> works.repka.rysquests.client.screen.ExtrasShowcase.achievements("miner", 0), 10);
        expect("the achievements are open", () -> "achievements".equals(BookScreen.bodyForTest()));
        screenshot("extras_achievements");
        step("achievements in vanilla", () -> ThemeManager.select("ryscore:vanilla"), 10);
        screenshot("extras_achievements_vanilla");
        step("back to slate", () -> ThemeManager.select("ryscore:slate"), 5);

        step("guides: the library", () -> {
            BookScreen.openTabForTest("guides");
            works.repka.rysquests.client.screen.ExtrasShowcase.library(0);
        }, 10);
        expect("the guides are open", () -> "guides".equals(BookScreen.bodyForTest()));
        screenshot("extras_guides_library");
        step("guides: a page", () -> works.repka.rysquests.client.screen.ExtrasShowcase.guidePage("create", 1), 10);
        screenshot("extras_guide_page");

        step("search: results", () -> {
            BookScreen.openTabForTest("map");
            works.repka.rysquests.client.screen.ExtrasShowcase.search("ящик");
        }, 20);
        screenshot("extras_search");
        step("search: nothing found", () -> works.repka.rysquests.client.screen.ExtrasShowcase.search("кирак"), 10);
        screenshot("extras_search_empty");
        step("search: done", () -> {
            works.repka.rysquests.client.screen.ExtrasShowcase.search("");
            pressKey(GLFW.GLFW_KEY_ESCAPE);
        }, 5);

        step("a small window", () -> {
            mc().getWindow().setWindowed(1100, 700);
            mc().resizeDisplay();
            works.repka.rysquests.client.screen.ExtrasShowcase.shop("token", "all", 2, false, false);
        }, 20);
        expect("compact layout", BookScreen::compactForTest);
        screenshot("extras_compact_shop");
        step("compact achievements", () -> works.repka.rysquests.client.screen.ExtrasShowcase.achievements(null, 0), 10);
        screenshot("extras_compact_achievements");
        step("compact achievement detail", () -> works.repka.rysquests.client.screen.ExtrasShowcase.achievements("miner", 0), 10);
        screenshot("extras_compact_achievement_detail");
        step("compact guides", () -> {
            BookScreen.openTabForTest("guides");
            works.repka.rysquests.client.screen.ExtrasShowcase.library(0);
        }, 10);
        screenshot("extras_compact_guides");
        step("back to the real book", () -> {
            mc().getWindow().setWindowed(1280, 800);
            mc().resizeDisplay();
            works.repka.rysquests.client.screen.ExtrasShowcase.restore();
            mc().setScreen(null);
        }, 20);
    }

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

    // --- the first run (plan, sections 4 and 5) ---

    private static final String FTB_BROKEN = "zz_autotest_broken.snbt";

    /**
     * Moves what earlier runs made out of the way (into {@code _stale}, nothing is deleted) and lays a copy of the ATM10 book where
     * FTB Quests keeps it, with one broken chapter: the scenario starts on an empty book each time.
     */
    private static void prepareFirstRun() {
        Path game = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get();
        Path stale = game.resolve("_stale").resolve("first-" + STARTED_AT);
        try {
            for (String folder : List.of("config/rysquests/import", "config/rysquests/pack", "config/ftbquests/quests")) {
                Path from = game.resolve(folder);
                if (Files.exists(from)) {
                    Path to = stale.resolve(folder);
                    Files.createDirectories(to.getParent());
                    Files.move(from, to);
                }
            }
            Path source = Path.of(System.getProperty("rysquests.autotest.atm10Quests"));
            Path target = game.resolve("config/ftbquests/quests");
            try (var files = Files.walk(source)) {
                for (Path file : files.toList()) {
                    Path to = target.resolve(source.relativize(file).toString());
                    if (Files.isDirectory(file)) {
                        Files.createDirectories(to);
                    } else {
                        Files.copy(file, to, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            Files.writeString(target.resolve("chapters").resolve(FTB_BROKEN), "{\n\tid: \"7A11ED0000000001\"\n\tfilename: \"zz_autotest_broken\"\n\tquests: [\n\t\t{\n\t\t\tid: \"7A11ED0000000002\"\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void buildFirstRunScenario() {
        prepareFirstRun();
        openWorld();
        step("match the window of the mockups", () -> {
            mc().options.guiScale().set(2);
            mc().options.languageCode = "ru_ru";
            mc().getLanguageManager().setSelected("ru_ru");
            mc().resizeDisplay();
            mc().reloadResourcePacks();
            ThemeManager.select("ryscore:slate");
        }, 40);
        expect("language applied", () -> mc().getOverlay() == null);
        expect("an empty book reached the client", () -> ClientBook.received() && ClientBook.current().quests().isEmpty());
        // 01: the panel of the family left of the inventory, without FTB Library, the invitation of the empty book in the tooltip
        dockShot("first_dock_no_ftb");
        step("close the inventory", () -> mc().setScreen(null), 5);
        // 09a: a book from scratch
        step("open the book", () -> pressKey(GLFW.GLFW_KEY_K), 10);
        expect("the empty book", () -> "empty".equals(BookScreen.bodyForTest()));
        step("build from scratch", () -> FirstRunHooks.open("new", false), 5);
        expect("the new book page", () -> "NewBookView".equals(FirstRunHooks.pageName()));
        step("point away", () -> moveMouse(1, 1), 5);
        screenshot("first_new_book");
        step("answer: a main line and mods around", () -> FirstRunHooks.newBookAnswer("CORE"), 5);
        screenshot("first_new_book_answered");
        step("create the book", FirstRunHooks::createBook, 5);
        expect("the server made the book, closed, as a galaxy", () -> ClientBook.current().meta().title().filter("Моя сборка"::equals).isPresent()
                && !ClientBook.current().meta().published() && ClientBook.current().meta().mapForm() == works.repka.rysquests.data.MapForm.GALAXY);
        expect("the editor opens on the table of the new book's first chapter", () -> works.repka.rysquests.client.screen.EditorTesting.on()
                && works.repka.rysquests.client.screen.EditorTesting.chapter() != null && FirstRunHooks.pageName().isEmpty());
        expect("the draft keeps the answer: a galaxy, no epochs", () -> works.repka.rysquests.client.screen.EditorTesting.draftMeta().mapForm() == works.repka.rysquests.data.MapForm.GALAXY
                && works.repka.rysquests.client.screen.EditorTesting.draftMeta().epochs().isEmpty());
        step("point away", () -> moveMouse(1, 1), 5);
        screenshot("first_new_book_table");
        step("put the new book aside for the import", () -> {
            works.repka.rysquests.client.screen.EditorTesting.leave();
            works.repka.rysquests.client.screen.EditorTesting.forgetDraft();
            mc().setScreen(null);
            try {
                Path game = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get();
                Path aside = game.resolve("_stale").resolve("first-" + STARTED_AT);
                Files.createDirectories(aside);
                Files.move(game.resolve("config/rysquests/pack"), aside.resolve("new-book-pack"));
                if (Files.exists(game.resolve("rysquests/drafts"))) {
                    Files.move(game.resolve("rysquests/drafts"), aside.resolve("new-book-drafts"));
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            command("reload");
        }, 40);
        expect("the book is empty again", () -> ClientBook.current().meta().title().isEmpty() && ClientBook.current().quests().isEmpty());
        // 05, then 50: the import stops on the broken chapter
        step("open the empty book with FTB", () -> {
            BookScreen.ftbForTest(true);
            pressKey(GLFW.GLFW_KEY_K);
        }, 10);
        screenshot("first_empty_ftb");
        step("carry over from FTB", () -> FirstRunHooks.open("import", true), 2);
        screenshot("first_import_running");
        expect("the import stopped on the broken chapter", () -> "error".equals(FirstRunHooks.wizardState()));
        step("log the failure", () -> LOGGER.info("[autotest] import failure: {}", works.repka.rysquests.client.ClientImport.error()), 5);
        expect("it names the broken file", () -> works.repka.rysquests.client.ClientImport.error().failure().file().endsWith(FTB_BROKEN)
                && works.repka.rysquests.client.ClientImport.error().failure().skippable());
        screenshot("first_import_error");
        // 06: carried over without that chapter
        step("carry over without this chapter", works.repka.rysquests.client.ClientImport::skipFailedFile, 5);
        expect("the report came", () -> "report".equals(FirstRunHooks.wizardState()));
        // Rewards are item stacks, which a game without the mods of ATM10 cannot read: about half of the quests load here, all chapters do.
        expect("the ATM10 book reached the client", () -> ClientBook.current().quests().size() > 2000 && ClientBook.current().chapters().size() >= 67);
        step("log the report", () -> LOGGER.info("[autotest] import report: {}", works.repka.rysquests.client.ClientImport.report()), 40);
        expect("the translations of the new book are loaded", () -> mc().getOverlay() == null);
        expect("the skipped file is in the report", () -> works.repka.rysquests.client.ClientImport.report().skippedFiles().stream().anyMatch(file -> file.endsWith(FTB_BROKEN)));
        expect("the carried book is closed to players", () -> !ClientBook.current().meta().published());
        screenshot("first_import_report");
        // 07b and 07: the map form
        // On this half loaded book the forms take about a second each: the picture is taken while they are still being laid out.
        step("next: the map form", FirstRunHooks::wizardNext, 1);
        screenshot("first_form_calculating");
        expect("the advised form is laid out first", FirstRunHooks::advisedDone);
        expect("all four previews laid out", FirstRunHooks::previewsDone);
        step("log the advice", () -> LOGGER.info("[autotest] advised form: {}", FirstRunHooks.advisedForm()), 20);
        expect("ATM10 is advised a galaxy", () -> "galaxy".equals(FirstRunHooks.advisedForm()));
        screenshot("first_form");
        step("look at the spiral", () -> FirstRunHooks.selectForm("spiral"), 20);
        screenshot("first_form_spiral");
        step("back to the advised galaxy", () -> FirstRunHooks.selectForm("galaxy"), 10);
        step("choose the galaxy", FirstRunHooks::chooseForm, 10);
        // 08: the book, closed to players
        expect("the book opened on the map", () -> "map".equals(BookScreen.bodyForTest()));
        expect("the layout was saved for players", () -> ClientBook.current().freshLayout().filter(layout -> layout.form() == works.repka.rysquests.data.MapForm.GALAXY).isPresent());
        step("point away", () -> moveMouse(1, 1), 20);
        screenshot("first_book_closed");
        // 10: open it to players
        step("ask to open the book", FirstRunHooks::askToPublish, 5);
        expect("the question is open", BookScreen::dialogOpenForTest);
        screenshot("first_publish_ask");
        step("open to players", () -> {
            pressKey(GLFW.GLFW_KEY_ESCAPE);
            ClientBook.requestPublish(true);
        }, 10);
        expect("the book is open to players", () -> ClientBook.current().meta().published());
        step("settle", () -> moveMouse(1, 1), 20);
        screenshot("first_book_open");
        step("close the book", () -> {
            BookScreen.ftbForTest(null);
            mc().setScreen(null);
        }, 5);
    }

    /** Opens the inventory, points at the first button of the panel of the family and takes a screenshot (pictures 01, 02, 02b). */
    private static void dockShot(String name) {
        step("open the inventory", () -> mc().setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc().player)), 20);
        step("point at the first button of the panel", () -> {
            var screen = mc().screen;
            LOGGER.info("[autotest] dock: standalone {}, hosted {}, replaces FTB {}, FTB visible {}, buttons {}", works.repka.ryscore.dock.Dock.standalone(screen),
                    works.repka.ryscore.dock.Dock.hosted(screen), works.repka.ryscore.dock.Dock.replaces(screen), works.repka.ryscore.dock.Dock.ftbVisible(screen),
                    works.repka.ryscore.dock.DockButtons.all().stream().map(works.repka.ryscore.dock.DockButton::id).toList());
            int[] at = dockCorner();
            if (at != null) {
                moveMouse(at[0] + 14, at[1] + 14);
            } else if (works.repka.ryscore.dock.Dock.ftbVisible(screen)) {
                // FTB Library draws its own panel: our button stands third in its second row here (picture 02).
                moveMouse(26, 25);
            } else {
                moveMouse(1, 1);
            }
        }, 10);
        screenshot(name);
    }

    /** The top left corner of the panel of the family in GUI units, or null when there is none on screen. */
    private static int[] dockCorner() {
        int w = mc().getWindow().getGuiScaledWidth();
        int h = mc().getWindow().getGuiScaledHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (works.repka.ryscore.dock.Dock.contains(x + 0.5, y + 0.5)) {
                    return new int[] {x, y};
                }
            }
        }
        return null;
    }

    /** The panel in a pack with FTB Library ({@code run-ftb}): its own panel (02), or replaced by the panel of RYScope (02b). */
    private static void buildDockScenario() {
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
        expect("the book reached the client", ClientBook::received);
        dockShot("dock" + (tag.isEmpty() ? "" : "_" + tag));
        step("open the book with the button of the panel", () -> {
            if (dockCorner() != null) {
                clickMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            }
        }, 10);
        step("log what opened", () -> LOGGER.info("[autotest] after the click: {}", mc().screen == null ? "nothing" : mc().screen.getClass().getSimpleName()), 5);
        step("close", () -> mc().setScreen(null), 5);
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

    static void step(String name, Runnable action, int waitAfter) {
        STEPS.add(new Step(name, () -> true, action, waitAfter));
    }

    static void expect(String name, BooleanSupplier condition) {
        STEPS.add(new Step(name, condition, () -> {}, 0));
    }

    static void openWorld() {
        // A fresh game directory opens the accessibility onboarding instead of the title screen.
        STEPS.add(new Step("open world", () -> mc().screen != null && mc().level == null && mc().getOverlay() == null && total > 40, () -> {
            mc().options.pauseOnLostFocus = false;
            mc().options.onboardAccessibility = false;
            mc().options.tutorialStep = TutorialSteps.NONE;
            mc().getTutorial().setStep(TutorialSteps.NONE);
            if (!(mc().screen instanceof TitleScreen)) {
                mc().setScreen(new TitleScreen());
            }
            cleanStart();
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
        }, AutoTest::cleanWorld, 40));
    }

    /**
     * Every scenario starts as on a fresh game directory, whatever the one before it left: the FTB import of "first", the editor's
     * pack and the drafts of the editor go aside into {@code _stale} (nothing is deleted). Only "book_editor_restart" keeps them: it
     * picks up what "book_editor" left on purpose.
     */
    private static void cleanStart() {
        if ("book_editor_restart".equals(scenario)) {
            return;
        }
        Path game = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get();
        Path stale = game.resolve("_stale").resolve("left-before-" + scenario + "-" + STARTED_AT);
        for (String folder : List.of("config/rysquests/import", "config/rysquests/pack", "rysquests/drafts")) {
            Path from = game.resolve(folder);
            if (Files.exists(from)) {
                try {
                    Path to = stale.resolve(folder);
                    Files.createDirectories(to.getParent());
                    Files.move(from, to);
                    LOGGER.info("[autotest] put aside {} left by an earlier run", folder);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
    }

    /** The world is shared by the scenarios: command answers go to chat again, as in a new world ("panel" turns them off). */
    private static void cleanWorld() {
        var server = mc().getSingleplayerServer();
        if (server != null) {
            server.execute(() -> server.getGameRules().getRule(GameRules.RULE_SENDCOMMANDFEEDBACK).set(true, server));
        }
    }

    static void screenshot(String name) {
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
        if (total == 0) {
            // tests play without sound (the author's request); the game may have written its volume back into options.txt
            mc().options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.0);
            mc().options.save();
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

    static void fail(String reason) {
        LOGGER.error("[autotest] FAIL: {}", reason);
        finished = true;
        mc().stop();
    }

    // --- game access ---

    static Minecraft mc() {
        return Minecraft.getInstance();
    }

    /** One world per mod set, reused between runs: opening a world with other mods stops on a game prompt. */
    static String worldName() {
        String fixed = System.getProperty("rysquests.autotest.world", "");
        if (!fixed.isBlank()) {
            return fixed;
        }
        int hash = ModList.get().getMods().stream().map(mod -> mod.getModId() + mod.getVersion()).sorted().toList().hashCode();
        return "rysquests_autotest_" + Integer.toHexString(hash);
    }

    private static void skipBackupQuestion() {
        if (mc().screen instanceof BackupConfirmScreen screen) {
            clickButton(screen.children(), "selectWorld.backupJoinSkipButton", true);
        }
    }

    static void command(String command) {
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
    static void pressKey(int key) {
        long window = mc().getWindow().getWindow();
        int scan = GLFW.glfwGetKeyScancode(key);
        mc().keyboardHandler.keyPress(window, key, scan, GLFW.GLFW_PRESS, 0);
        mc().keyboardHandler.keyPress(window, key, scan, GLFW.GLFW_RELEASE, 0);
    }

    static void moveMouse(double guiX, double guiY) {
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

    static void clickMouse(int button) {
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
