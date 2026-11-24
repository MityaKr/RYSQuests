package works.repka.rysquests.client.dev;

import static works.repka.rysquests.client.dev.AutoTest.command;
import static works.repka.rysquests.client.dev.AutoTest.expect;
import static works.repka.rysquests.client.dev.AutoTest.mc;
import static works.repka.rysquests.client.dev.AutoTest.step;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import works.repka.ryscore.map.MapApi;
import works.repka.ryscore.theme.ThemeManager;
import works.repka.ryscore.ui.Kit;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientMessages;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.client.screen.BookRole;
import works.repka.rysquests.client.screen.BookScreen;
import works.repka.rysquests.client.screen.EditorTesting;
import works.repka.rysquests.client.screen.PanelShowcase;
import works.repka.rysquests.client.screen.RepysHooks;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.MapForm;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.ViewMode;
import works.repka.rysquests.network.QuestActionPayload;
import works.repka.rysquests.network.ShopDeliverPayload;

/**
 * {@code cfshots}: pictures for the CurseForge pages of the six family mods (tasks in
 * {@code _shared/_audit/curseforge/<mod>/shots.md}). The client of run-repys joins the test copy of the server
 * (localhost:25566) as Dev, an operator there, in a 1920×1080 window, builds each scene and saves
 * {@code cf_<mod>_<nn>_<name>.png}. Scenes live only on the test copy: Dev's team and land beside the base, quests done
 * by command. What one client cannot show (a team of five, a server full of
 * players, reports with chat) is put into the client's view state only; nothing of it reaches the server. The other
 * mods are called by reflection, as {@link CheckScenario} does, so this scenario needs none of them to compile.
 * A step that throws is logged as {@code [cf] FAIL} and the pass goes on. The last step stops the test server.
 */
final class CfShotsScenario {
    private static final Logger LOGGER = LoggerFactory.getLogger(CfShotsScenario.class);
    private static final String SERVER = "localhost:25566";
    private static final ResourceKey<Level> OVERWORLD = Level.OVERWORLD;
    /** The middle of the VENIK base of the test world (its land: chunks 324..342 × 1463..1475). */
    private static final int BASE_X = 5351, BASE_Z = 23496;
    /** Dev's own land, east of the base: the middle chunk of the first plot. */
    private static final int HOME_CX = 347, HOME_CZ = 1467;
    /** Stops of the flight that fills the map around the base. */
    private static final int[][] TOUR = {{5050, 23300}, {5350, 23300}, {5650, 23300}, {5650, 23650}, {5350, 23650}, {5050, 23650}, {5500, 23480}};

    private static final ResourceLocation HERE_CHAPTER = ResourceLocation.parse("repys:b03_e2_current");
    private static final ResourceLocation HERE = ResourceLocation.parse("repys:b03_e2_current/electric_mechanism");
    private static final ResourceLocation TROPHY = ResourceLocation.parse("repys:trophy_ignitium");
    private static final ResourceLocation COLLECTION = ResourceLocation.parse("repys:collection_boss_fragments");

    private static final String[] TEAM = {"Alder_Fox", "Brynn", "Corvo_K", "Mira_Sun"};
    private static int shots;
    private static int waited;
    private static int quiet;
    private static String lastShot = "";

    private CfShotsScenario() {
    }

    static void build() {
        join();
        softWait("index", CfShotsScenario::indexReady, 20 * 300);
        setup();
        exploreMap();
        teamAndLand();
        claimsShots();
        mapShots();
        indexShots();
        questsShots();
        helmShots();
        teamsShots();
        safe("finish", () -> {
            ThemeManager.select("ryscore:slate");
            BookRole.forceForTest(null);
            mc().setScreen(null);
            log("done", "INFO", shots + " pictures");
        }, 20);
        safe("stop the test server", () -> command("stop"), 100);
    }

    // ------------------------------------------------------------------ setup

    private static void join() {
        expect("title", () -> mc().screen != null && mc().level == null && mc().getOverlay() == null);
        step("connect", () -> {
            mc().options.pauseOnLostFocus = false;
            mc().options.onboardAccessibility = false;
            if (!(mc().screen instanceof TitleScreen)) {
                mc().setScreen(new TitleScreen());
            }
            // the family windows show this name in their header
            ConnectScreen.startConnecting(mc().screen, mc(), ServerAddress.parseString(SERVER), new ServerData("RepYS", SERVER, ServerData.Type.OTHER), false, null);
        }, 20);
        expect("joined", () -> mc().player != null && mc().level != null && mc().screen == null);
        step("joined", () -> log("join", "OK", mc().player.getGameProfile().getName() + " lang " + mc().options.languageCode), 200);
    }

    private static void setup() {
        safe("window 1920x1080", () -> {
            mc().getWindow().setWindowed(1920, 1080);
            mc().options.guiScale().set(3);
            mc().options.hideGui = false;
            mc().options.renderDistance().set(12);
            mc().resizeDisplay();
        }, 40);
        safe("world for pictures", () -> {
            command("gamemode creative");
            command("gamerule sendCommandFeedback false");
            command("gamerule doDaylightCycle false");
            command("gamerule doWeatherCycle false");
            command("time set 6000");
            command("weather clear");
            command("clear @s");
            BookRole.forceForTest(false);
            ThemeManager.select("ryscore:slate");
            ClientMessages.dismiss();
        }, 40);
        safe("server name", () -> {
            CompoundTag a = new CompoundTag();
            a.putString("serverName", "RepYS");
            call(cls("works.repka.ryshelm.client.HelmClient"), "ask", "s.serverName", "RepYS", "settings", a);
        }, 10);
        // goals pinned in RYSIndex by earlier runs of this client would stand in the corner of every picture
        safe("no goals", () -> {
            Class<?> board = cls("works.repka.rysindex.runtime.Board");
            for (Object g : new ArrayList<>((List<?>) call(board, "goals"))) {
                call(board, "remove", get(g, "stack"));
            }
        }, 10);
    }

    /** Puts the player on the ground at x z (the server finds it), then runs {@code then} with the place the player stands on. */
    private static void ground(String name, int x, int z, java.util.function.Consumer<net.minecraft.core.BlockPos> then, int waitAfter) {
        safe(name + " ground", () -> {
            mc().player.getAbilities().flying = false;
            command(String.format(Locale.ROOT, "tp @s %d 200 %d", x, z));
        }, 60);
        safe(name + " spread", () -> command(String.format(Locale.ROOT, "spreadplayers %d %d 0 1 false @s", x, z)), 60);
        safe(name, () -> {
            log("ground " + name, "INFO", mc().player.blockPosition().toShortString());
            then.accept(mc().player.blockPosition());
        }, waitAfter);
    }

    /** Server places and the "back" history are real data of the test world: kept off the map's list in pictures. */
    private static void quietPlaces() {
        try {
            Object data = call(cls("works.repka.rysmap.client.PlacesClient"), "data");
            set(data, "places", List.of());
            set(data, "back", List.of());
        } catch (RuntimeException e) {
            log("places", "FAIL", e.toString());
        }
    }

    private static void mouseAway() {
        AutoTest.moveMouse(1, 1);
    }

    /** The map only knows chunks this client loaded: a flight over the base fills it, and the cave floor 3 under it. */
    private static void exploreMap() {
        for (int[] at : TOUR) {
            safe("tour " + at[0] + " " + at[1], () -> {
                mc().player.getAbilities().flying = true;
                command(String.format(Locale.ROOT, "tp @s %d 180 %d 0 30", at[0], at[1]));
            }, 100);
            softWait("map scan " + at[0] + " " + at[1], CfShotsScenario::mapSettled, 20 * 90);
        }
    }

    private static boolean mapSettled() {
        try {
            Object session = call(cls("works.repka.rysmap.client.ClientEntry"), "session");
            if (session == null) {
                return false;
            }
            int queued = (Integer) call(session, "queued");
            quiet = queued == 0 ? quiet + 1 : 0;
            return quiet > 40;
        } catch (RuntimeException e) {
            return true;
        }
    }

    // ------------------------------------------------------------------ team and land (real, on the test copy)

    private static void teamAndLand() {
        safe("own team", () -> {
            Object team = get(cls("works.repka.rysteams.client.TeamClient"), "team");
            if (team != null) {
                log("team", "INFO", "Dev is in " + get(team, "name"));
                return;
            }
            CompoundTag a = new CompoundTag();
            a.putString("name", "Northwind");
            a.putInt("color", 0x3A7BD5);
            a.putString("icon", "minecraft:compass");
            call(cls("works.repka.rysteams.network.ModNetwork"), "action", "create", a);
            log("team", "INFO", "created Northwind");
        }, 60);
        // the first plot: 3x3 around the home chunk and three chunks east of it, three of them force-loaded
        landAt("plot home", HOME_CX, HOME_CZ, "claims claim 1", "claims load 0", "claims name Homestead");
        landAt("plot home e1", HOME_CX + 2, HOME_CZ - 1, "claims claim 0", null, null);
        landAt("plot home e2", HOME_CX + 2, HOME_CZ, "claims claim 0", "claims load 0", null);
        landAt("plot home e3", HOME_CX + 2, HOME_CZ + 1, "claims claim 0", "claims load 0", null);
        // a farm of four chunks, a mine and a watchtower of one
        landAt("plot farm 1", HOME_CX, HOME_CZ + 5, "claims claim 0", null, null);
        landAt("plot farm 2", HOME_CX + 1, HOME_CZ + 5, "claims claim 0", null, null);
        landAt("plot farm 3", HOME_CX, HOME_CZ + 6, "claims claim 0", null, null);
        landAt("plot farm 4", HOME_CX + 1, HOME_CZ + 6, "claims claim 0", null, "claims name Farm");
        landAt("plot mine", HOME_CX + 5, HOME_CZ - 7, "claims claim 0", null, "claims name Mine");
        landAt("plot tower", HOME_CX - 3, HOME_CZ + 11, "claims claim 0", null, "claims name Watchtower");
        safe("land list", () -> command("claims list"), 40);
    }

    private static void landAt(String name, int cx, int cz, String claim, String load, String rename) {
        safe(name, () -> {
            mc().player.getAbilities().flying = true;
            command(String.format(Locale.ROOT, "tp @s %d 160 %d", cx * 16 + 8, cz * 16 + 8));
        }, 40);
        safe(name + " claim", () -> {
            command(claim);
            if (load != null) {
                command(load);
            }
            if (rename != null) {
                command(rename);
            }
        }, 30);
    }

    // ------------------------------------------------------------------ RYSClaims

    private static void claimsShots() {
        safe("land window", () -> {
            mc().player.getAbilities().flying = true;
            command(String.format(Locale.ROOT, "tp @s %d 150 %d", HOME_CX * 16 + 8, HOME_CZ * 16 + 8));
        }, 60);
        safe("land window open", () -> {
            Object screen = call(cls("works.repka.rysclaims.client.LandScreen"), "window", OVERWORLD, (double) (HOME_CX * 16 + 8), (double) (HOME_CZ * 16 + 8));
            mc().setScreen((net.minecraft.client.gui.screens.Screen) screen);
        }, 40);
        safe("select free chunks", () -> {
            Class<?> view = cls("works.repka.rysclaims.client.LandView");
            set(view, "selectionDim", "minecraft:overworld");
            Object sel = get(view, "SELECTION");
            for (int[] c : new int[][] {{HOME_CX + 3, HOME_CZ - 1}, {HOME_CX + 3, HOME_CZ}, {HOME_CX + 3, HOME_CZ + 1}, {HOME_CX + 4, HOME_CZ}}) {
                call(sel, "add", net.minecraft.world.level.ChunkPos.asLong(c[0], c[1]));
            }
        }, 30);
        shot("rysclaims", 1, "grid");
        safe("rights", () -> call(mc().screen, "rightsForTest"), 20);
        shot("rysclaims", 2, "rights_simple");
        safe("rights detail", () -> set(get(mc().screen, "view"), "rightsDetail", true), 20);
        shot("rysclaims", 2, "rights_detail");
        safe("plots", () -> {
            Object view = get(mc().screen, "view");
            Object plots = enumOf("works.repka.rysclaims.client.LandView$Page", "PLOTS");
            call(view, "setPage", plots);
        }, 20);
        shot("rysclaims", 3, "plots");
        safe("plot menu", () -> {
            Object view = get(mc().screen, "view");
            @SuppressWarnings("unchecked")
            java.util.Map<Integer, Kit.Dropdown> menus = (java.util.Map<Integer, Kit.Dropdown>) get(view, "menus");
            Kit.Dropdown d = new Kit.Dropdown();
            d.open = true;
            menus.put(1, d);
        }, 20);
        shot("rysclaims", 3, "plots_menu");
        // the same land on the big map: the "Claims" mode with the grid and the limits
        safe("claims mode on the map", () -> {
            mc().setScreen(null);
            call(cls("works.repka.rysclaims.client.LandScreen"), "open", OVERWORLD, HOME_CX + 3, HOME_CZ, true);
        }, 60);
        safe("claims mode selection", () -> {
            Object sel = get(cls("works.repka.rysclaims.client.LandView"), "SELECTION");
            for (int[] c : new int[][] {{HOME_CX + 3, HOME_CZ - 1}, {HOME_CX + 3, HOME_CZ + 1}, {HOME_CX + 4, HOME_CZ}}) {
                call(sel, "add", net.minecraft.world.level.ChunkPos.asLong(c[0], c[1]));
            }
            call(mc().screen, "setZoom", 3.0);
        }, 40);
        shot("rysclaims", 1, "map_mode");
        safe("close land", () -> {
            call(get(cls("works.repka.rysclaims.client.LandView"), "SELECTION"), "clear");
            mc().setScreen(null);
        }, 10);
    }

    // ------------------------------------------------------------------ RYSMap

    private static void mapShots() {
        safe("map points", () -> {
            Object session = call(cls("works.repka.rysmap.client.ClientEntry"), "session");
            Object store = call(session, "points");
            // points of earlier runs go first, then the set of this run
            for (Object p : new ArrayList<>((List<?>) call(store, "points"))) {
                if (List.of("Base", "Iron mine", "Nether portal", "Farm", "Lookout", "Village", "Ancient city").contains((String) get(p, "name"))) {
                    call(store, "remove", get(p, "id"));
                }
            }
            addPoint(store, "Base", BASE_X, 72, BASE_Z, "home", 0, true);
            addPoint(store, "Iron mine", 5126, -21, 23290, "pick", 3, false);
            addPoint(store, "Nether portal", 5440, 70, 23590, "spark", 4, true);
            addPoint(store, "Farm", HOME_CX * 16 + 16, 70, (HOME_CZ + 5) * 16 + 16, "chest", 2, false);
            addPoint(store, "Lookout", 5230, 75, 23660, "eye", 1, false);
            addPoint(store, "Village", 4960, 70, 23380, "flag", 5, false);
            addPoint(store, "Ancient city", 5600, -40, 23260, "skull", 7, false);
            call(session, "savePoints");
        }, 10);
        safe("map layers", () -> {
            layer("rysmap:points", true);
            layer("rysmap:players", true);
            layer("rysmap:mobs", true);
            layer("rysmap:places", false);
            layer("rysmap:heights", false);
        }, 10);
        // a few animals by the farm: one icon with a number on the map
        ground("animals", HOME_CX * 16 + 12, (HOME_CZ + 5) * 16 + 12, at -> {
            String[] kinds = {"cow", "cow", "cow", "cow", "cow", "cow", "sheep", "sheep", "sheep", "sheep", "chicken", "chicken", "chicken", "pig", "pig"};
            for (int i = 0; i < kinds.length; i++) {
                command(String.format(Locale.ROOT, "execute at @s run summon minecraft:%s ~%d ~1 ~%d {NoAI:1b,PersistenceRequired:1b,Silent:1b}",
                        kinds[i], 3 + (i % 5) * 2, 3 + (i / 5) * 2));
            }
        }, 80);
        // the cave floors under the base: the player goes underground, the map scans the floors around him
        safe("underground", () -> {
            command("gamemode spectator");
            command(String.format(Locale.ROOT, "tp @s %d -10 %d", BASE_X, BASE_Z));
        }, 200);
        softWait("floors scanned", CfShotsScenario::mapSettled, 20 * 120);
        safe("underground 2", () -> command(String.format(Locale.ROOT, "tp @s %d -10 %d", BASE_X - 150, BASE_Z - 120)), 200);
        softWait("floors scanned 2", CfShotsScenario::mapSettled, 20 * 120);
        safe("back up", () -> {
            command("gamemode creative");
            command(String.format(Locale.ROOT, "tp @s %d 150 %d", HOME_CX * 16 + 8, HOME_CZ * 16 + 8));
            mc().player.getAbilities().flying = true;
        }, 60);
        safe("map open", () -> MapApi.get().open(MapApi.Target.builder(OVERWORLD, BASE_X + 60, 80, BASE_Z).build()), 60);
        safe("map zoom", () -> call(mc().screen, "setZoom", 2.0), 20);
        safe("map point", () -> log("map select", "INFO", String.valueOf(call(mc().screen, "selectForTest", "Base"))), 20);
        safe("map zoom again", () -> call(mc().screen, "setZoom", 2.0), 60);
        shot("rysmap", 1, "map");
        for (int floor : new int[] {-1, -2, 0}) {
            safe("map caves " + floor, () -> {
                call(mc().screen, "cavesForTest", true, floor);
                call(mc().screen, "setZoom", 2.0);
            }, 40);
            softWait("caves drawn " + floor, CfShotsScenario::mapSettled, 20 * 60);
            safe("caves point " + floor, () -> call(mc().screen, "selectForTest", floor == -2 ? "Iron mine" : "Base"), 20);
            safe("caves floor again " + floor, () -> {
                call(mc().screen, "cavesForTest", true, floor);
                call(mc().screen, "setZoom", 2.0);
            }, 40);
            shot("rysmap", 3, "caves_" + floor);
        }
        safe("caves off", () -> {
            call(mc().screen, "cavesForTest", false, 0);
            call(mc().screen, "clearSelectionForTest");
            layer("rysmap:heights", true);
        }, 20);
        safe("close up", () -> {
            mc().setScreen(null);
            MapApi.get().open(MapApi.Target.builder(OVERWORLD, HOME_CX * 16 + 12, 80, (HOME_CZ + 5) * 16 + 8).build());
        }, 60);
        safe("close up zoom", () -> call(mc().screen, "setZoom", 3.0), 80);
        shot("rysmap", 4, "layers");
        safe("heights off", () -> {
            layer("rysmap:heights", false);
            mc().setScreen(null);
        }, 10);
        // first person: round minimap that turns with the view, labels of points in the world
        vantage();
        safe("view settings", () -> {
            Object cfg = call(cls("works.repka.rysmap.client.ClientConfig"), "get");
            set(cfg, "minimapCircle", true);
            set(cfg, "minimapRotate", true);
            set(cfg, "worldLabels", "all");
        }, 120);
        safe("labels", () -> {
            clean();
            Object key = get(cls("works.repka.rysmap.client.ClientEntry"), "SHOW_POINTS");
            ((net.minecraft.client.KeyMapping) key).setDown(true);
        }, 30);
        shot("rysmap", 2, "minimap");
        safe("labels off", () -> {
            Object key = get(cls("works.repka.rysmap.client.ClientEntry"), "SHOW_POINTS");
            ((net.minecraft.client.KeyMapping) key).setDown(false);
        }, 30);
        shot("rysmap", 2, "minimap_plain");
    }

    /** A view over the base from the east, in the air: the first-person pictures and the world behind the windows. */
    private static void vantage() {
        int vx = BASE_X + 170, vz = BASE_Z + 70;
        ground("vantage", vx, vz, at -> {
            float yaw = (float) Math.toDegrees(Math.atan2(-(BASE_X - vx), BASE_Z - vz));
            mc().player.getAbilities().flying = true;
            mc().player.onUpdateAbilities();
            command(String.format(Locale.ROOT, "tp @s %d %d %d %.1f 14", vx, at.getY() + 26, vz, yaw));
        }, 40);
        safe("vantage fly", () -> {
            mc().player.getAbilities().flying = true;
            mc().player.setDeltaMovement(0, 0, 0);
        }, 100);
    }

    private static void addPoint(Object store, String name, int x, int y, int z, String icon, int color, boolean world) {
        Object p = newOf("works.repka.rysmap.point.PointStore$Point");
        set(p, "name", name);
        set(p, "x", x);
        set(p, "y", y);
        set(p, "z", z);
        set(p, "dimension", "minecraft:overworld");
        set(p, "icon", icon);
        set(p, "color", color);
        set(p, "world", world);
        call(store, "add", p);
    }

    private static void layer(String id, boolean on) {
        Class<?> layers = cls("works.repka.rysmap.layer.Layers");
        Object layer = call(layers, "find", ResourceLocation.parse(id));
        if (layer == null) {
            log("layer " + id, "FAIL", "not found");
            return;
        }
        call(layers, "setVisible", layer, on);
    }

    // ------------------------------------------------------------------ RYSIndex

    private static void indexShots() {
        safe("index items", () -> {
            command("gamemode survival");
            command("clear @s");
            for (String g : new String[] {"minecraft:iron_ingot 64", "minecraft:redstone 64", "mekanism:ingot_osmium 32", "minecraft:glass 16",
                    "create:andesite_alloy 64", "create:shaft 32", "create:cogwheel 16", "minecraft:oak_planks 64", "create:iron_sheet 24",
                    "create:brass_ingot 16", "minecraft:copper_ingot 32", "minecraft:stick 32"}) {
                command("give @s " + g);
            }
        }, 60);
        // the card of a machine: some of its parts are in the inventory, some are not
        safe("recipe card", () -> openRecipes("mekanism:metallurgic_infuser", false), 60);
        shot("rysindex", 1, "recipe");
        safe("recipe card 2", () -> {
            mc().setScreen(null);
            openRecipes("create:mechanical_press", false);
        }, 60);
        shot("rysindex", 1, "recipe_press");
        safe("recipe card 3", () -> {
            mc().setScreen(null);
            openRecipes("mekanism:basic_control_circuit", false);
        }, 60);
        shot("rysindex", 1, "recipe_circuit");
        // survival: the plain inventory, not the creative tabs
        safe("inventory list", () -> {
            mc().setScreen(null);
            mc().setScreen(new InventoryScreen(mc().player));
        }, 30);
        safe("search", () -> {
            Class<?> overlay = cls("works.repka.rysindex.ui.Overlay");
            call(overlay, "setFilter", 0);
            call(overlay, "setSearch", "@create craftable");
        }, 40);
        shot("rysindex", 2, "list_craftable");
        safe("search 2", () -> call(cls("works.repka.rysindex.ui.Overlay"), "setSearch", "@create"), 40);
        shot("rysindex", 2, "list_create");
        safe("search 3", () -> call(cls("works.repka.rysindex.ui.Overlay"), "setSearch", "@mekanism circuit"), 40);
        shot("rysindex", 2, "list_mekanism");
        safe("search clear", () -> call(cls("works.repka.rysindex.ui.Overlay"), "setSearch", ""), 10);
        for (String item : new String[] {"mekanism:steel_casing", "mekanism:basic_control_circuit", "create:electron_tube",
                "create:mechanical_crafter", "mekanism:metallurgic_infuser", "create:precision_mechanism"}) {
            safe("tree " + item, () -> {
                mc().setScreen(new InventoryScreen(mc().player));
                call(cls("works.repka.rysindex.ui.TreeScreen"), "open", stack(item));
            }, 80);
            shot("rysindex", 3, "tree_" + item.substring(item.indexOf(':') + 1));
        }
        for (String item : new String[] {"mekanism:ingot_steel", "create:brass_ingot", "mekanism:alloy_infused", "mekanism:basic_control_circuit",
                "mekanism:steel_casing"}) {
            safe("planner " + item, () -> {
                mc().setScreen(new InventoryScreen(mc().player));
                call(cls("works.repka.rysindex.ui.PlannerScreen"), "open", stack(item));
            }, 40);
            safe("planner rate " + item, () -> {
                Object planner = get(mc().screen, "planner");
                call(planner, "setRate", 600L);
            }, 60);
            shot("rysindex", 4, "planner_" + item.substring(item.indexOf(':') + 1));
        }
        // the lens: a wall of stone with an osmium ore in it, built where the player stands; he looks at the ore
        ground("lens wall", HOME_CX * 16 + 2, HOME_CZ * 16 - 40, at -> {
            mc().setScreen(null);
            command("execute at @s run fill ~-3 ~ ~-4 ~3 ~4 ~-3 minecraft:stone");
            command("execute at @s run fill ~-3 ~ ~-2 ~3 ~4 ~1 minecraft:air");
            command("execute at @s run fill ~-3 ~-1 ~-2 ~3 ~-1 ~1 minecraft:grass_block");
            command("execute at @s run setblock ~-2 ~3 ~-3 minecraft:andesite");
            command("execute at @s run setblock ~2 ~1 ~-3 minecraft:tuff");
            command("execute at @s run setblock ~-2 ~ ~-3 minecraft:coal_ore");
            command("execute at @s run setblock ~1 ~3 ~-3 minecraft:gravel");
            command("execute at @s run setblock ~ ~1 ~-3 mekanism:osmium_ore");
            command(String.format(Locale.ROOT, "tp @s %.1f %d %.1f 180 4", at.getX() + 0.5, at.getY(), at.getZ() + 0.5));
        }, 60);
        safe("lens on", () -> {
            clean();
            set(cls("works.repka.rysindex.client.Keys"), "forceLens", true);
        }, 30);
        shot("rysindex", 5, "lens");
        safe("lens off", () -> set(cls("works.repka.rysindex.client.Keys"), "forceLens", false), 10);
        safe("creative again", () -> command("gamemode creative"), 20);
    }

    private static Object stack(String id) {
        ItemStack s = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
        if (s.isEmpty()) {
            log("item " + id, "FAIL", "no such item");
        }
        return newOf("works.repka.rysindex.model.ItemRes", s);
    }

    private static void openRecipes(String id, boolean uses) {
        Object mode = enumOf("works.repka.rysindex.runtime.Lookup$Mode", uses ? "USES" : "RECIPES");
        log("recipes " + id, "INFO", String.valueOf(call(cls("works.repka.rysindex.ui.RecipeScreen"), "open", stack(id), mode)));
    }

    // ------------------------------------------------------------------ RYSQuests

    private static final List<ResourceLocation> WAITING = new ArrayList<>();
    private static final ResourceLocation[] SHORT = new ResourceLocation[1];

    private static void questsShots() {
        vantage();
        safe("quests progress", () -> {
            command("clear @s");
            Set<ResourceLocation> done = new LinkedHashSet<>();
            for (Quest quest : sortedQuests()) {
                Optional<Chapter> chapter = quest.chapter().map(ClientBook.current().chapters()::get);
                if (chapter.isPresent() && chapter.get().epoch().orElse(0) == 1 && !quest.options().optional()) {
                    done.add(quest.id());
                }
            }
            List<Quest> current = sortedQuests().stream().filter(q -> q.chapter().map(HERE_CHAPTER::equals).orElse(false)).toList();
            int added = 0;
            boolean grew = true;
            while (grew && added < 14) {
                grew = false;
                for (Quest q : current) {
                    if (added >= 14) {
                        break;
                    }
                    if (!done.contains(q.id()) && !q.id().equals(HERE) && done.containsAll(q.requires())) {
                        done.add(q.id());
                        added++;
                        grew = true;
                    }
                }
            }
            log("quests", "INFO", "completing " + done.size());
            done.forEach(id -> command("rysquests progress @s complete " + id));
        }, 200);
        safe("claim rewards", () -> {
            book();
            BookScreen.claimAllForTest();
        }, 80);
        safe("rewards wait", () -> {
            mc().setScreen(null);
            List<Quest> open = sortedQuests().stream()
                    .filter(q -> !ClientProgress.get(q.id()).completed() && !q.id().equals(HERE) && !q.rewards().isEmpty())
                    .filter(q -> q.chapter().isPresent() && !q.id().getPath().startsWith("_"))
                    .filter(q -> q.requires().stream().allMatch(id -> ClientProgress.get(id).completed()))
                    .sorted(Comparator.comparing(q -> q.chapter().map(HERE_CHAPTER::equals).orElse(false) ? 0 : 1))
                    .limit(3).toList();
            open.forEach(q -> {
                WAITING.add(q.id());
                command("rysquests progress @s complete " + q.id());
            });
            for (ResourceLocation q : ClientProgress.pinned()) {
                PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.PIN, q, 0, List.of()));
            }
        }, 40);
        // a quest short of items: half of them in the inventory, so that "In progress" has something
        safe("short of items", () -> {
            for (Quest quest : sortedQuests()) {
                if (ClientProgress.get(quest.id()).completed() || !quest.requires().stream().allMatch(id -> ClientProgress.get(id).completed())) {
                    continue;
                }
                if (quest.tasks().size() == 1 && quest.tasks().get(0) instanceof works.repka.rysquests.task.ItemTask task && task.count() >= 4
                        && task.item().isPresent() && !task.item().get().tag()) {
                    SHORT[0] = quest.id();
                    command("give @s " + task.item().get().id() + " " + task.count() / 2);
                    log("short", "INFO", quest.id() + " " + task.count() / 2 + " of " + task.count());
                    return;
                }
            }
        }, 40);
        safe("pin and coins", () -> {
            PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.PIN, HERE, 0, List.of()));
            if (SHORT[0] != null) {
                PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.PIN, SHORT[0], 0, List.of()));
            }
            command("rysquests wallet set @s coin 2400");
        }, 40);

        // 1: the whole book on one canvas, a quest panel on the right
        safe("map tab", () -> {
            book();
            BookScreen.openTabForTest("map");
            BookScreen.showForTest(ViewMode.MAP);
            BookScreen.selectForTest(null, null);
        }, 10);
        softWait("map laid out", BookScreen::mapReadyForTest, 20 * 120);
        safe("whole book", () -> {
            AutoTest.moveMouse(1, 1);
            BookScreen.mapFlyForTest(null, 0, null);
        }, 40);
        safe("panel on the whole book", () -> BookScreen.selectForTest(HERE_CHAPTER, HERE), 30);
        shot("rysquests", 1, "map_book");
        safe("fly to the quest", () -> BookScreen.mapFlyForTest(null, 0, HERE), 40);
        shot("rysquests", 1, "map_quest");
        // 2: the four forms of the map, canvas only
        for (MapForm form : new MapForm[] {MapForm.SERPENTINE, MapForm.SPIRAL, MapForm.GALAXY, MapForm.GENERATOR}) {
            safe("form " + form, () -> {
                QuestBook b = ClientBook.current();
                ClientBook.overrideForTest(new QuestBook(b.meta().withMapForm(form), b.chapters(), b.quests(), b.guides(), b.sections(), b.unlocks(),
                        b.byItem(), b.hash(), b.assets(), b.offers(), b.achievements(), b.structures(), b.layoutHash(),
                        form == MapForm.SPIRAL ? b.layout() : Optional.empty()), true);
                book();
                BookScreen.openTabForTest("map");
                BookScreen.showForTest(ViewMode.MAP);
                BookScreen.selectForTest(null, null);
            }, 20);
            softWait("form laid out " + form, BookScreen::mapReadyForTest, 20 * 120);
            safe("form whole " + form, () -> {
                BookScreen.foldSidebarForTest(true);
                BookScreen.mapFlyForTest(null, 0, null);
            }, 50);
            shot("rysquests", 2, "form_" + form.name().toLowerCase(Locale.ROOT));
        }
        safe("real book back", () -> {
            ClientBook.overrideForTest(null, null);
            BookScreen.foldSidebarForTest(false);
        }, 30);
        // 3: now
        safe("now", () -> {
            book();
            PanelShowcase.now(false);
        }, 30);
        shot("rysquests", 3, "now");
        // 4: guides with a recipe and a structure
        Object[][] guides = {{"ie", 0}, {"mystical", 1}, {"create", 2}, {"machines/machines", 16}};
        for (Object[] g : guides) {
            safe("guide " + g[0] + " " + g[1], () -> {
                book();
                if (!RepysHooks.guide(ResourceLocation.parse("repys:" + g[0]), (Integer) g[1])) {
                    log("guide " + g[0], "FAIL", "no guide");
                }
            }, 100);
            shot("rysquests", 4, "guide_" + String.valueOf(g[0]).replace('/', '_') + "_" + g[1]);
        }
        // 5: shop with a trophy being delivered and a locked one
        safe("trophy", () -> {
            command("rysquests achievement complete @s repys:slayer_ignis");
            command("give @s mekanism:alloy_reinforced 2000");
            command("give @s mekanism:ultimate_control_circuit 200");
        }, 60);
        safe("deliver", () -> PacketDistributor.sendToServer(new ShopDeliverPayload(TROPHY)), 60);
        safe("shop", () -> {
            book();
            RepysHooks.shop(null);
        }, 30);
        shot("rysquests", 5, "shop");
        safe("shop trophy", () -> RepysHooks.shop(TROPHY), 30);
        shot("rysquests", 5, "shop_trophy");
        safe("shop trophies", () -> {
            RepysHooks.shop(TROPHY);
            works.repka.rysquests.client.screen.CfHooks.shopSection("shop.repys.trophies");
        }, 30);
        shot("rysquests", 5, "shop_trophies");
        safe("shop offer", () -> RepysHooks.shop(ResourceLocation.parse("repys:e2_steel_ingots")), 30);
        shot("rysquests", 5, "shop_offer");
        // 6: achievements, a collection half gathered
        safe("fragments", () -> {
            command("clear @s");
            command("give @s kubejs:abyss_fragment");
            command("give @s kubejs:flame_fragment");
            command("give @s kubejs:storm_fragment");
        }, 120);
        safe("achievements", () -> {
            book();
            RepysHooks.achievements(COLLECTION);
        }, 40);
        shot("rysquests", 6, "achievements");
        // 7: the editor, a quest selected, a link drawn to its neighbour
        safe("as operator", () -> {
            BookRole.forceForTest(null);
            book();
        }, 20);
        safe("editor", () -> {
            EditorTesting.open("map");
            EditorTesting.canvas();
        }, 20);
        softWait("editor canvas", EditorTesting::canvasReady, 20 * 120);
        safe("editor select", () -> {
            EditorTesting.select(HERE);
            EditorTesting.flyTo(HERE);
        }, 50);
        shot("rysquests", 7, "editor");
        safe("leave editor", () -> {
            EditorTesting.leave();
            BookRole.forceForTest(false);
            mc().setScreen(null);
        }, 20);
        // 8: the same screen in three themes
        for (String theme : new String[] {"slate", "glass", "vanilla"}) {
            safe("theme " + theme, () -> {
                ThemeManager.select("ryscore:" + theme);
                book();
                PanelShowcase.now(false);
            }, 40);
            shot("rysquests", 8, "theme_" + theme);
            safe("theme map " + theme, () -> {
                BookScreen.openTabForTest("map");
                BookScreen.showForTest(ViewMode.MAP);
                BookScreen.selectForTest(HERE_CHAPTER, HERE);
                BookScreen.mapFlyForTest(null, 0, HERE);
            }, 40);
            shot("rysquests", 8, "theme_map_" + theme);
        }
        safe("slate again", () -> {
            ThemeManager.select("ryscore:slate");
            mc().setScreen(null);
        }, 20);
    }

    private static void book() {
        if (!(mc().screen instanceof BookScreen)) {
            mc().setScreen(new BookScreen());
        }
    }

    private static List<Quest> sortedQuests() {
        List<ResourceLocation> order = new ArrayList<>(ClientBook.current().chapters().keySet());
        return ClientBook.current().quests().values().stream()
                .sorted(Comparator.<Quest>comparingInt(q -> q.chapter().map(order::indexOf).orElse(order.size()))
                        .thenComparing(q -> q.id().toString()))
                .toList();
    }

    // ------------------------------------------------------------------ RYSHelm

    private static UUID offline(String name) {
        return UUIDUtil.createOfflinePlayerUUID(name);
    }

    private static void helmShots() {
        Class<?> view = cls("works.repka.ryshelm.client.View");
        Class<?> screen = cls("works.repka.ryshelm.client.ServerScreen");
        // the rules and the roles come from the server first (the rules of the test copy are in English)
        safe("helm rules", () -> {
            set(view, "pinned", false);
            call(screen, "open", "rules");
        }, 40);
        safe("helm roles load", () -> call(screen, "open", "roles"), 40);
        safe("helm roles keep", () -> ROLES[0] = ((CompoundTag) get(view, "roles")).copy(), 5);
        // 1: players with a card (the whole list is the client's view only)
        safe("helm players", () -> {
            set(view, "pinned", true);
            set(view, "players", helmPlayers());
            set(view, "card", helmCard());
            set(screen, "selected", offline("Grib_ok"));
            set(screen, "filter", "all");
            call(screen, "open", "players");
        }, 40);
        safe("helm players again", () -> {
            set(view, "players", helmPlayers());
            set(view, "card", helmCard());
        }, 10);
        shot("ryshelm", 1, "players");
        // 2: the punishment window; the ladder line in the words of the server's own lang keys
        safe("helm punish", () -> {
            Object ban = enumOf("works.repka.ryshelm.logic.Kind", "BAN");
            call(mc().screen, "openPunish", ban, offline("Grib_ok"), "Grib_ok");
        }, 10);
        safe("helm punish fields", () -> {
            set(view, "punish", helmPunish());
            set(view, "players", helmPlayers());
            set(view, "card", helmCard());
            set(mc().screen, "punishRule", 3);
            Object reason = get(mc().screen, "reason");
            if (reason != null) {
                ((net.minecraft.client.gui.components.EditBox) reason).setValue("Insulting players again after the mute");
            }
        }, 20);
        shot("ryshelm", 2, "punish");
        safe("helm punish close", () -> call(mc().screen, "closePunish"), 10);
        // 3: reports with the place, the chat and the past of the player
        safe("helm reports", () -> {
            set(view, "reports", helmReports());
            set(screen, "report", 3L);
            call(screen, "open", "reports");
        }, 30);
        safe("helm reports again", () -> set(view, "reports", helmReports()), 10);
        shot("ryshelm", 3, "reports");
        // 4: roles from the server; two moderators of the picture, Dev the only operator
        safe("helm roles", () -> {
            CompoundTag roles = ROLES[0] == null ? new CompoundTag() : ROLES[0].copy();
            ListTag ops = new ListTag();
            ops.add(StringTag.valueOf("Dev"));
            roles.put("operators", ops);
            ListTag mods = new ListTag();
            for (String[] m : new String[][] {{"Lena_K", "true"}, {"Sasha_V", "false"}}) {
                CompoundTag c = new CompoundTag();
                c.putUUID("id", offline(m[0]));
                c.putString("name", m[0]);
                c.putBoolean("online", Boolean.parseBoolean(m[1]));
                mods.add(c);
            }
            roles.put("moderators", mods);
            set(view, "roles", roles);
            call(screen, "open", "roles");
        }, 30);
        safe("helm roles again", () -> {
            CompoundTag roles = ((CompoundTag) get(view, "roles"));
            log("helm roles", "INFO", "moderators " + roles.getList("moderators", 10).size());
        }, 5);
        shot("ryshelm", 4, "roles");
        safe("helm free", () -> {
            set(view, "pinned", false);
            mc().setScreen(null);
        }, 10);
    }

    private static final CompoundTag[] ROLES = new CompoundTag[1];

    private static CompoundTag helmPunish() {
        CompoundTag t = new CompoundTag();
        t.putString("ladder", "Grib_ok has 1 warning and 1 punishment in 2 days. Next step by server rules: ban for 1 day. After that: ban for 7 days.");
        t.putString("nextKind", "ban");
        t.putLong("nextTerm", 86_400_000L);
        t.putLong("muteMax", 0L);
        t.putLong("banMax", 0L);
        for (String k : new String[] {"warn", "mute", "kick", "ban"}) {
            t.putBoolean("can_" + k, true);
        }
        return t;
    }

    private static CompoundTag helmPlayers() {
        CompoundTag t = new CompoundTag();
        t.putString("tab", "players");
        ListTag list = new ListTag();
        long now = System.currentTimeMillis();
        // name, role, team, colour, online, dist, land, warns, reports, muted, banned, newbie
        Object[][] rows = {
                {"Dev", "operator", "Northwind", 0x3A7BD5, true, -1, "Northwind", 0, 0, false, false, false},
                {"Lena_K", "moderator", "Copper Owls", 0xD47C3E, true, 420, "Copper Owls", 0, 0, false, false, false},
                {"Grib_ok", "player", "", 0x8B93A5, true, 96, "Northwind", 1, 2, true, false, false},
                {"Alder_Fox", "player", "Northwind", 0x3A7BD5, true, 35, "Northwind", 0, 0, false, false, false},
                {"Brynn", "player", "Northwind", 0x3A7BD5, true, 1240, "", 0, 0, false, false, false},
                {"Sasha_V", "moderator", "Deepdelvers", 0x7A4FD1, true, 2900, "Deepdelvers", 0, 0, false, false, false},
                {"Mira_Sun", "player", "Copper Owls", 0xD47C3E, true, 610, "", 0, 0, false, false, true},
                {"TorvaldX", "player", "Deepdelvers", 0x7A4FD1, true, -1, "", 0, 0, false, false, false},
                {"Pixel_Pete", "player", "", 0x8B93A5, false, -1, "", 0, 0, false, true, false},
                {"Corvo_K", "player", "Northwind", 0x3A7BD5, false, -1, "", 0, 0, false, false, false}};
        int i = 0;
        for (Object[] r : rows) {
            String name = (String) r[0];
            CompoundTag c = new CompoundTag();
            c.putUUID("id", name.equals("Dev") ? mc().player.getUUID() : offline(name));
            c.putString("name", name);
            c.putString("role", (String) r[1]);
            c.putString("team", (String) r[2]);
            c.putInt("color", (Integer) r[3]);
            c.putBoolean("mine", "Northwind".equals(r[2]));
            boolean online = (Boolean) r[4];
            c.putBoolean("online", online);
            if (online) {
                c.putString("dim", name.equals("TorvaldX") ? "minecraft:the_nether" : "minecraft:overworld");
                if ((Integer) r[5] >= 0) {
                    c.putInt("dist", (Integer) r[5]);
                }
                c.putInt("x", 5300 + i * 37);
                c.putInt("z", 23400 + i * 23);
                c.putString("land", (String) r[6]);
                c.putLong("since", now - (20 + i * 17) * 60_000L);
                c.putBoolean("newbie", (Boolean) r[11]);
            } else {
                c.putLong("seen", now - (3 + i) * 3_600_000L);
            }
            c.putBoolean("muted", (Boolean) r[9]);
            c.putBoolean("banned", (Boolean) r[10]);
            c.putBoolean("frozen", false);
            c.putInt("warns", (Integer) r[7]);
            c.putInt("reports", (Integer) r[8]);
            list.add(c);
            i++;
        }
        t.put("players", list);
        return t;
    }

    private static CompoundTag helmCard() {
        long now = System.currentTimeMillis();
        CompoundTag c = new CompoundTag();
        c.putUUID("id", offline("Grib_ok"));
        c.putString("name", "Grib_ok");
        c.putString("role", "player");
        c.putString("team", "");
        c.putBoolean("ownTeam", true);
        c.putBoolean("online", true);
        c.putLong("played", 31L * 3_600_000L);
        c.putLong("first", now - 12L * 86_400_000L);
        c.putLong("seen", now);
        c.putString("dim", "minecraft:overworld");
        c.putString("coords", "5562 71 23518");
        c.putString("land", "Northwind");
        c.putBoolean("landOwn", false);
        c.putBoolean("fly", false);
        c.putBoolean("god", false);
        c.putBoolean("frozen", false);
        c.putLong("mute", 2L);
        ListTag history = new ListTag();
        Object[][] h = {
                {"report", 11L, now - 25 * 60_000L, "Report from Alder_Fox · new", false},
                {"mute", 2L, now - 40 * 60_000L, "Mute 1 h · rule 3 · Dev", true},
                {"report", 9L, now - 50 * 60_000L, "Report from Mira_Sun · closed", false},
                {"warn", 1L, now - 2 * 86_400_000L, "Warning · rule 3 · Lena_K", false}};
        for (Object[] r : h) {
            CompoundTag x = new CompoundTag();
            x.putString("kind", (String) r[0]);
            x.putLong("id", (Long) r[1]);
            x.putLong("t", (Long) r[2]);
            x.putString("text", (String) r[3]);
            x.putBoolean("active", (Boolean) r[4]);
            x.putBoolean("revoked", false);
            x.putBoolean("canRevoke", (Boolean) r[4]);
            history.add(x);
        }
        c.put("history", history);
        ListTag notes = new ListTag();
        CompoundTag n = new CompoundTag();
        n.putString("text", "Said sorry once, keeps doing it");
        n.putString("by", "Lena_K");
        n.putLong("t", now - 86_400_000L);
        notes.add(n);
        c.put("notes", notes);
        return c;
    }

    private static CompoundTag helmReports() {
        long now = System.currentTimeMillis();
        CompoundTag t = new CompoundTag();
        ListTag list = new ListTag();
        Object[][] rows = {
                {3L, "Alder_Fox", "Grib_ok", 1, "Took items from our chest at the farm", now - 4 * 60_000L, "new", "", "New"},
                {2L, "Mira_Sun", "TorvaldX", 4, "His mob farm lags the whole area", now - 47 * 60_000L, "taken", "Lena_K", "Taken by Lena_K"},
                {1L, "Brynn", "Grib_ok", 3, "Swearing in chat", now - 3 * 3_600_000L, "closed", "Dev", "Punished"}};
        for (Object[] r : rows) {
            CompoundTag x = new CompoundTag();
            x.putLong("id", (Long) r[0]);
            x.putString("from", (String) r[1]);
            x.putString("target", (String) r[2]);
            x.putInt("rule", (Integer) r[3]);
            x.putString("text", (String) r[4]);
            x.putLong("t", (Long) r[5]);
            x.putString("state", (String) r[6]);
            x.putString("by", (String) r[7]);
            x.putString("label", (String) r[8]);
            list.add(x);
        }
        t.put("list", list);
        CompoundTag d = new CompoundTag();
        d.putLong("id", 3L);
        d.putUUID("fromId", offline("Alder_Fox"));
        d.putUUID("targetId", offline("Grib_ok"));
        d.putString("from", "Alder_Fox");
        d.putString("target", "Grib_ok");
        d.putInt("rule", 1);
        d.putString("ruleTitle", "Don't grief or take what isn't yours");
        d.putString("text", "Took items from our chest at the farm");
        d.putLong("t", now - 4 * 60_000L);
        d.putString("state", "new");
        d.putString("by", "");
        d.putBoolean("mine", false);
        d.putString("land", "Northwind");
        d.put("fromPos", spot(5571, 72, 23574));
        d.put("targetPos", spot(5566, 71, 23580));
        ListTag chat = new ListTag();
        String[] lines = {"anyone got spare iron?", "nice farm lol", "its not locked so its free", "ok ok ill put it back later"};
        for (int i = 0; i < lines.length; i++) {
            CompoundTag c = new CompoundTag();
            c.putLong("t", now - (12 - i * 3) * 60_000L);
            c.putString("text", lines[i]);
            chat.add(c);
        }
        d.put("chat", chat);
        ListTag past = new ListTag();
        Object[][] p = {{"mute", now - 40 * 60_000L, "Mute 1 h · rule 3"}, {"warn", now - 2 * 86_400_000L, "Warning · rule 3"},
                {"report", now - 3 * 3_600_000L, "Another report from Brynn"}};
        for (Object[] r : p) {
            CompoundTag x = new CompoundTag();
            x.putString("kind", (String) r[0]);
            x.putLong("t", (Long) r[1]);
            x.putString("text", (String) r[2]);
            past.add(x);
        }
        d.put("past", past);
        t.put("detail", d);
        return t;
    }

    private static CompoundTag spot(int x, int y, int z) {
        CompoundTag s = new CompoundTag();
        s.putString("dim", "minecraft:overworld");
        s.putInt("x", x);
        s.putInt("y", y);
        s.putInt("z", z);
        s.putString("coords", x + " " + y + " " + z);
        return s;
    }

    // ------------------------------------------------------------------ RYSTeams (the client's view only)

    private static void teamsShots() {
        Class<?> client = cls("works.repka.rysteams.client.TeamClient");
        Class<?> screen = cls("works.repka.rysteams.client.TeamScreen");
        safe("team state", () -> {
            call(client, "accept", teamLobby());
            call(client, "accept", teamState());
            call(client, "accept", teamPresence());
            tab(screen, 0);
            mc().setScreen((net.minecraft.client.gui.screens.Screen) newOf("works.repka.rysteams.client.TeamScreen"));
        }, 40);
        safe("team state again", () -> {
            call(client, "accept", teamLobby());
            call(client, "accept", teamState());
            call(client, "accept", teamPresence());
        }, 20);
        shot("rysteams", 1, "team");
        safe("team settings", () -> tab(screen, 4), 30);
        shot("rysteams", 3, "settings");
        // the lobby: no team yet, open teams to join; the icons to choose come from the hotbar
        safe("lobby items", () -> {
            mc().setScreen(null);
            command("clear @s");
            for (String g : new String[] {"minecraft:compass", "minecraft:diamond_pickaxe", "minecraft:golden_apple", "minecraft:oak_sapling", "minecraft:amethyst_shard"}) {
                command("give @s " + g);
            }
        }, 40);
        safe("lobby", () -> {
            CompoundTag none = new CompoundTag();
            none.putString("type", "state");
            call(client, "accept", none);
            call(client, "accept", teamLobby());
            mc().setScreen((net.minecraft.client.gui.screens.Screen) newOf("works.repka.rysteams.client.TeamScreen"));
        }, 30);
        safe("lobby name", () -> {
            Object box = get(mc().screen, "nameBox");
            if (box != null) {
                ((net.minecraft.client.gui.components.EditBox) box).setValue("Northwind");
            }
        }, 20);
        shot("rysteams", 2, "lobby");
        safe("team real again", () -> mc().setScreen(null), 10);
    }

    private static void tab(Class<?> screen, int index) {
        Field f = field(screen, "tab");
        try {
            f.set(null, f.getType().getEnumConstants()[index]);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static CompoundTag teamState() {
        long now = System.currentTimeMillis();
        Object real = get(cls("works.repka.rysteams.client.TeamClient"), "team");
        UUID id = real != null ? (UUID) get(real, "id") : UUID.nameUUIDFromBytes("Northwind".getBytes());
        CompoundTag team = new CompoundTag();
        team.putUUID("id", id);
        team.putString("name", "Northwind");
        team.putInt("color", 0x3A7BD5);
        team.putString("icon", "minecraft:compass");
        team.putLong("created", now - 9L * 86_400_000L);
        team.putString("join", "REQUEST");
        team.putString("rewards", real != null ? String.valueOf(get(real, "rewards")) : "SHARED");
        team.putBoolean("keepCopy", true);
        team.putInt("autoDays", 14);
        ListTag members = new ListTag();
        String[][] who = {{"Dev", "leader"}, {"Alder_Fox", "officer"}, {"Brynn", "member"}, {"Corvo_K", "member"}, {"Mira_Sun", "guest"}};
        int i = 0;
        for (String[] w : who) {
            CompoundTag m = new CompoundTag();
            m.putUUID("id", w[0].equals("Dev") ? mc().player.getUUID() : offline(w[0]));
            m.putString("name", w[0]);
            m.putString("role", w[1]);
            m.putLong("joined", now - (9 - i) * 86_400_000L);
            m.putLong("seen", w[0].equals("Corvo_K") ? now - 5 * 3_600_000L : now);
            members.add(m);
            i++;
        }
        team.put("members", members);
        ListTag req = new ListTag();
        CompoundTag r = new CompoundTag();
        r.putUUID("id", offline("TorvaldX"));
        r.putString("name", "TorvaldX");
        req.add(r);
        team.put("requests", req);
        ListTag feed = new ListTag();
        Object[][] f = {
                {2L, "quests", "Alder_Fox finished the quest «Electric Mechanism»", "create:precision_mechanism", false},
                {9L, "teams", "Mira_Sun joined the team as a guest", "minecraft:player_head", false},
                {17L, "scope", "Brynn set a crafting goal: Steel Casing ×16", "mekanism:steel_casing", false},
                {31L, "quests", "The team bought «Steel Ingots» in the shop", "mekanism:ingot_steel", false},
                {46L, "pulse", "Generator room: power below 20%", "mekanism:energy_tablet", true},
                {75L, "teams", "Corvo_K placed a mark «Diamonds here»", "minecraft:diamond", false},
                {130L, "quests", "Epoch I «Mechanics» is done", "create:andesite_alloy", false}};
        for (Object[] e : f) {
            CompoundTag x = new CompoundTag();
            x.putLong("t", now - (Long) e[0] * 60_000L);
            x.putString("s", (String) e[1]);
            x.putString("x", (String) e[2]);
            x.putString("i", (String) e[3]);
            x.putBoolean("a", (Boolean) e[4]);
            x.putString("d", "");
            feed.add(x);
        }
        team.put("feed", feed);
        team.put("marks", new ListTag());
        team.put("notes", new ListTag());
        team.put("todos", new ListTag());
        team.put("allies", new ListTag());
        CompoundTag state = new CompoundTag();
        state.putString("type", "state");
        state.put("team", team);
        ListTag allies = new ListTag();
        allies.add(info("Copper Owls", 0xD47C3E, "minecraft:copper_ingot", 3, "OPEN"));
        state.put("allies", allies);
        state.putBoolean("deathNotice", true);
        return state;
    }

    private static CompoundTag teamPresence() {
        CompoundTag p = new CompoundTag();
        p.putString("type", "presence");
        ListTag list = new ListTag();
        Object[][] who = {{"Alder_Fox", "minecraft:overworld", 5568, 64, 23590, "farming"}, {"Brynn", "minecraft:the_nether", 690, 34, 2930, "mining"},
                {"Mira_Sun", "minecraft:overworld", 5412, 70, 23502, ""}};
        for (Object[] w : who) {
            CompoundTag c = new CompoundTag();
            c.putUUID("id", offline((String) w[0]));
            c.putString("dim", (String) w[1]);
            c.putBoolean("pos", true);
            c.putInt("x", (Integer) w[2]);
            c.putInt("y", (Integer) w[3]);
            c.putInt("z", (Integer) w[4]);
            c.putString("status", (String) w[5]);
            list.add(c);
        }
        p.put("list", list);
        return p;
    }

    private static CompoundTag teamLobby() {
        CompoundTag l = new CompoundTag();
        l.putString("type", "lobby");
        ListTag teams = new ListTag();
        teams.add(info("Copper Owls", 0xD47C3E, "minecraft:copper_ingot", 3, "OPEN"));
        teams.add(info("Deepdelvers", 0x7A4FD1, "minecraft:iron_pickaxe", 5, "REQUEST"));
        teams.add(info("Greenhollow", 0x2E8B57, "minecraft:wheat", 2, "OPEN"));
        teams.add(info("Starforge", 0xE4B23C, "minecraft:nether_star", 4, "REQUEST"));
        l.put("teams", teams);
        ListTag online = new ListTag();
        for (String name : new String[] {"Mira_Sun", "TorvaldX", "Pixel_Pete"}) {
            CompoundTag c = new CompoundTag();
            c.putUUID("id", offline(name));
            c.putString("name", name);
            c.putBoolean("free", !name.equals("TorvaldX"));
            online.add(c);
        }
        l.put("online", online);
        return l;
    }

    private static CompoundTag info(String name, int color, String icon, int count, String join) {
        CompoundTag c = new CompoundTag();
        c.putUUID("id", UUID.nameUUIDFromBytes(name.getBytes()));
        c.putString("name", name);
        c.putInt("color", color);
        c.putString("icon", icon);
        c.putInt("count", count);
        c.putString("join", join);
        return c;
    }

    // ------------------------------------------------------------------ steps and pictures

    /** A step whose failure is logged and does not end the pass. */
    private static void safe(String name, Runnable action, int waitAfter) {
        step(name, () -> {
            try {
                action.run();
            } catch (RuntimeException | LinkageError e) {
                log(name, "FAIL", e.toString() + (e.getCause() != null ? " / " + e.getCause() : ""));
                LOGGER.warn("[cf] step {} failed", name, e);
            }
        }, waitAfter);
    }

    private static void softWait(String name, BooleanSupplier condition, int limit) {
        step("wait " + name, () -> {
            waited = 0;
            quiet = 0;
        }, 1);
        expect("wait " + name, () -> {
            try {
                return condition.getAsBoolean() || ++waited > limit;
            } catch (RuntimeException e) {
                return ++waited > limit;
            }
        });
        step("waited " + name, () -> log("wait " + name, waited <= limit ? "OK" : "FAIL", waited + " ticks"), 10);
    }

    private static boolean indexReady() {
        try {
            return "READY".equals(String.valueOf(call(cls("works.repka.rysindex.runtime.Indexer"), "state")));
        } catch (RuntimeException e) {
            return true;
        }
    }

    private static void clean() {
        mc().getToasts().clear();
        mc().gui.getChat().clearMessages(false);
    }

    private static void shot(String mod, int nn, String name) {
        step("clean " + mod + " " + name, () -> {
            clean();
            quietPlaces();
            if (mc().screen != null) {
                mouseAway();
            }
        }, 4);
        step("shot " + mod + " " + name, () -> {
            String file = String.format(Locale.ROOT, "cf_%s_%02d_%s.png", mod, nn, name);
            Screenshot.grab(mc().gameDirectory, file, mc().getMainRenderTarget(), message -> {});
            shots++;
            lastShot = file;
            log("shot", "INFO", file + " screen " + (mc().screen == null ? "-" : mc().screen.getClass().getSimpleName()));
        }, 5);
    }

    private static void log(String what, String verdict, String detail) {
        LOGGER.info("[cf] {} {}: {}", verdict, what, detail);
    }

    // ------------------------------------------------------------------ reflection

    private static Class<?> cls(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("no class " + name, e);
        }
    }

    private static Object enumOf(String cls, String name) {
        for (Object c : cls(cls).getEnumConstants()) {
            if (((Enum<?>) c).name().equals(name)) {
                return c;
            }
        }
        throw new IllegalStateException("no " + cls + "." + name);
    }

    private static Object newOf(String cls, Object... args) {
        for (Constructor<?> c : cls(cls).getDeclaredConstructors()) {
            if (c.getParameterCount() == args.length && fits(c.getParameterTypes(), args)) {
                try {
                    c.setAccessible(true);
                    return c.newInstance(args);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("new " + cls, e);
                }
            }
        }
        throw new IllegalStateException("no constructor " + cls + "/" + args.length);
    }

    /** Calls a method by name and arity on an object, or a static one when {@code target} is a Class. */
    private static Object call(Object target, String name, Object... args) {
        Class<?> c = target instanceof Class<?> k ? k : target.getClass();
        boolean statics = target instanceof Class<?>;
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            for (Method m : k.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == args.length && Modifier.isStatic(m.getModifiers()) == statics
                        && fits(m.getParameterTypes(), args)) {
                    try {
                        m.setAccessible(true);
                        return m.invoke(statics ? null : target, args);
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException(c.getSimpleName() + "." + name, e.getCause() != null ? e.getCause() : e);
                    }
                }
            }
        }
        throw new IllegalStateException("no method " + c.getName() + "." + name + "/" + args.length);
    }

    private static boolean fits(Class<?>[] types, Object[] args) {
        for (int i = 0; i < types.length; i++) {
            if (args[i] == null) {
                if (types[i].isPrimitive()) {
                    return false;
                }
                continue;
            }
            Class<?> t = types[i].isPrimitive() ? boxed(types[i]) : types[i];
            if (!t.isInstance(args[i])) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> boxed(Class<?> p) {
        if (p == int.class) {
            return Integer.class;
        }
        if (p == long.class) {
            return Long.class;
        }
        if (p == boolean.class) {
            return Boolean.class;
        }
        if (p == double.class) {
            return Double.class;
        }
        if (p == float.class) {
            return Float.class;
        }
        return p;
    }

    private static Field field(Object target, String name) {
        Class<?> c = target instanceof Class<?> k ? k : target.getClass();
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                Field f = k.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                // look in the superclass
            }
        }
        throw new IllegalStateException("no field " + c.getName() + "." + name);
    }

    private static Object get(Object target, String name) {
        try {
            return field(target, name).get(target instanceof Class<?> ? null : target);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void set(Object target, String name, Object value) {
        try {
            field(target, name).set(target instanceof Class<?> ? null : target, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
