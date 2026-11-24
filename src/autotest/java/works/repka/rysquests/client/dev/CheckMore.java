package works.repka.rysquests.client.dev;

import static works.repka.rysquests.client.dev.AutoTest.command;
import static works.repka.rysquests.client.dev.AutoTest.mc;
import static works.repka.rysquests.client.dev.AutoTest.step;
import static works.repka.rysquests.client.dev.CheckScenario.FOREIGN;
import static works.repka.rysquests.client.dev.CheckScenario.commandShot;
import static works.repka.rysquests.client.dev.CheckScenario.commandShotDyn;
import static works.repka.rysquests.client.dev.CheckScenario.item;
import static works.repka.rysquests.client.dev.CheckScenario.log;
import static works.repka.rysquests.client.dev.CheckScenario.pad;
import static works.repka.rysquests.client.dev.CheckScenario.shot;
import static works.repka.rysquests.client.dev.CheckScenario.softWait;
import static works.repka.rysquests.client.dev.CheckScenario.use;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import works.repka.ryscore.action.Action;
import works.repka.ryscore.action.Actions;
import works.repka.ryscore.action.Ring;
import works.repka.ryscore.map.MapApi;
import works.repka.ryscore.ui.UiScale;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.screen.BookScreen;
import works.repka.rysquests.client.screen.RepysHooks;
import works.repka.rysquests.data.Quest;

/**
 * The second half of {@code check} (24.11): the points the list of 23.11 left to the author, tried on the test copy of the live
 * world. The base of VENIK (land, MMR machines, the old apiary with 4 omegas), the Gates pages and the Help of RYSIndex, the
 * wheel held and released, 20 clicks of the recipe viewer in a chest, the server window (journal, reports, settings, white
 * list), the windows of Oritech addons, a burner and a breaker, a guest on foreign land, a player without OP and the mod list.
 * Every step is guarded: a failure is a {@code [check] FAIL} line and the pass goes on.
 */
final class CheckMore {
    /** The middle of the VENIK base on the live world: every MMR controller is inside the simulation distance from here. */
    private static final BlockPos BASE = new BlockPos(5355, 120, 23496);
    private static final BlockPos APIARY = new BlockPos(5312, 64, 23468);
    private static final int[][] CONTROLLERS = {
            {5390, 64, 23449}, {5428, 70, 23489}, {5388, 63, 23537}, {5302, 64, 23455}, {5328, 63, 23533}, {5328, 63, 23543},
            {5275, 63, 23455}, {5362, 64, 23543}, {5333, 65, 23517}, {5333, 65, 23511}, {5333, 65, 23505}, {5345, 64, 23521},
            {5289, 63, 23455}, {5386, 64, 23507}, {5386, 71, 23517}, {5386, 71, 23507}, {5386, 64, 23517}, {5435, 64, 23471},
            {5345, 64, 23507}};
    private static final String[] OUR_MODS = {"rysindex", "rysclaims", "rysmap", "rysteams", "rysquests", "ryshelm"};
    private static final String[][] GATE_QUESTS = {
            {"first_gate", "repys:b26_e9_creation/first_gate_fragment"}, {"primordial_flame", "repys:b25_e9_primordial/primordial_flame"}};
    private static final String[] HELP_ITEMS = {"mmr:repys:creation_engine", "mmr:repys:logic_cluster", "mmr:repys:ae2_bulk_assembler",
            "kubejs:gate_fragment", "kubejs:genesis_core", "kubejs:matter_singularity"};
    private static final String[] ADDONS = {"oritechaddonsone:transfer_addon", "oritechaddonsone:warehouse_addon",
            "oritechaddonsone:tank_addon", "oritechaddonsone:extension_addon_2"};
    /** A platform in the air over the claim of Chuzhoy_Test (chunks x 5808..5839, z 26080..26111). */
    private static final BlockPos GUEST = new BlockPos(5824, 160, 26097);

    private static int dropsBefore;
    private static int clicks;
    private static Object wheel;
    private static int settingsValue;
    private static String settingsKey = "";

    private CheckMore() {
    }

    // ------------------------------------------------------------------ the mod list, before joining

    static void modList() {
        AutoTest.expect("title for the mod list", () -> mc().screen != null && mc().level == null && mc().getOverlay() == null);
        for (String id : OUR_MODS) {
            st("mod list " + id, () -> {
                Screen title = mc().screen instanceof TitleScreen ? mc().screen : new TitleScreen();
                Screen list = (Screen) Class.forName("net.neoforged.neoforge.client.gui.ModListScreen").getConstructor(Screen.class).newInstance(title);
                mc().setScreen(list);
                EditBox search = (EditBox) field(list, "search");
                search.setValue(id);
            }, 10);
            st("mod list select " + id, () -> {
                Screen list = mc().screen;
                Object widget = field(list, "modList");
                List<?> entries = (List<?>) widget.getClass().getMethod("children").invoke(widget);
                Object pick = null;
                for (Object e : entries) {
                    Object info = e.getClass().getMethod("getInfo").invoke(e);
                    if (id.equals(info.getClass().getMethod("getModId").invoke(info))) {
                        pick = e;
                    }
                }
                if (pick == null) {
                    log("A.1 mod list " + id, "FAIL", "no entry, " + entries.size() + " shown");
                    return;
                }
                for (Method m : list.getClass().getMethods()) {
                    if (m.getName().equals("setSelected") && m.getParameterCount() == 1) {
                        m.invoke(list, pick);
                    }
                }
                Object info = pick.getClass().getMethod("getInfo").invoke(pick);
                Object logo = info.getClass().getMethod("getLogoFile").invoke(info);
                Object blur = info.getClass().getMethod("getLogoBlur").invoke(info);
                log("A.1 mod list " + id, "INFO", "logo " + logo + ", blur " + blur);
            }, 20);
            shot("modlist_" + id);
        }
        st("mod list close", () -> mc().setScreen(new TitleScreen()), 20);
    }

    // ------------------------------------------------------------------ the pass in the world

    static void build() {
        base();
        gatesAndHelp();
        wheel();
        indexClicks();
        helm();
        oritech();
        mechanics();
        guest();
        noOp();
        themes();
    }

    /** The base of VENIK on the copy of the live world: land, loading, machines, the old apiary. */
    private static void base() {
        st("base tp", () -> {
            command("gamemode creative");
            command(String.format(Locale.ROOT, "tp @s %d %d %d 0 30", BASE.getX(), BASE.getY(), BASE.getZ()));
        }, 40);
        st("base fly", () -> {
            mc().player.getAbilities().flying = true;
            mc().player.onUpdateAbilities();
        }, 5);
        softWait("base chunks", () -> mc().level.hasChunk(APIARY.getX() >> 4, APIARY.getZ() >> 4)
                && mc().level.hasChunk(5435 >> 4, 23471 >> 4) && mc().level.hasChunk(5275 >> 4, 23455 >> 4), 20 * 60);
        // the apiary script drops what is over one omega 2 s after the chunk loads
        st("base settle", () -> {}, 200);
        commandShot("claims info", "B.2 claims info at the base");
        commandShot("claims admin for VENIK list", "B.2 VENIK land");
        commandShot("claims admin loaded", "B.2 loaded land");
        st("base map", () -> MapApi.get().openMode(ResourceLocation.fromNamespaceAndPath("rysclaims", "claims"),
                MapApi.Target.builder(Level.OVERWORLD, BASE.getX(), 64, BASE.getZ()).build()), 80);
        shot("map_claims_venik");
        st("close", () -> mc().setScreen(null), 10);
        st("apiary drops", () -> {
            Item omega = item("productivelib:upgrade_productivity_4");
            int lying = 0;
            for (Entity e : mc().level.getEntities((Entity) null, new AABB(APIARY).inflate(6), e -> e instanceof ItemEntity)) {
                ItemStack s = ((ItemEntity) e).getItem();
                if (s.is(omega)) {
                    lying += s.getCount();
                }
            }
            log("B.4 apiary extras on the ground", lying == 3 ? "OK" : "FAIL", lying + " omegas lying within 6 blocks of " + APIARY.toShortString());
        }, 5);
        commandShotDyn(() -> String.format(Locale.ROOT, "data get block %d %d %d inv.Items", APIARY.getX(), APIARY.getY(), APIARY.getZ()),
                "B.4 apiary controller slots");
        st("apiary look", () -> command(String.format(Locale.ROOT, "tp @s %d %d %d 90 35", APIARY.getX() + 6, APIARY.getY() + 4, APIARY.getZ())), 40);
        shot("apiary_old");
        st("base back", () -> command(String.format(Locale.ROOT, "tp @s %d %d %d 0 30", BASE.getX(), BASE.getY(), BASE.getZ())), 100);
        for (int[] c : CONTROLLERS) {
            commandShotDyn(() -> String.format(Locale.ROOT, "data get block %d %d %d machine", c[0], c[1], c[2]), "B.3 machine " + c[0] + " " + c[1] + " " + c[2]);
            commandShotDyn(() -> String.format(Locale.ROOT, "data get block %d %d %d status", c[0], c[1], c[2]), "B.3 status 1 " + c[0] + " " + c[1] + " " + c[2]);
        }
        st("machines run", () -> {}, 20 * 30);
        for (int[] c : CONTROLLERS) {
            commandShotDyn(() -> String.format(Locale.ROOT, "data get block %d %d %d status", c[0], c[1], c[2]), "B.3 status 2 " + c[0] + " " + c[1] + " " + c[2]);
        }
    }

    /** The Gates pages in the guide of the pack's mechanics, the two quests with a button to them, the Help tab of RYSIndex. */
    private static void gatesAndHelp() {
        for (String[] q : GATE_QUESTS) {
            st("gate quest " + q[0], () -> {
                ResourceLocation id = ResourceLocation.parse(q[1]);
                Quest quest = ClientBook.current().quests().get(id);
                if (quest == null) {
                    log("G.2 quest " + q[0], "FAIL", "no quest " + id);
                    return;
                }
                log("G.2 quest " + q[0], "INFO", I18n.get(quest.title()) + " | " + quest.text().map(I18n::get).orElse("<no text>").replace('\n', ' '));
                BookScreen.openAt(id);
            }, 60);
            shot("gate_quest_" + q[0]);
        }
        for (int page : new int[] {6, 7}) {
            st("gate guide " + page, () -> {
                if (!(mc().screen instanceof BookScreen)) {
                    BookScreen.open();
                }
                log("G.1 guide machines " + page, RepysHooks.guide(ResourceLocation.parse("repys:machines/guide"), page) ? "OK" : "FAIL", "page " + page);
            }, 50);
            shot("gate_guide_" + page);
        }
        st("close", () -> mc().setScreen(null), 10);
        for (String id : HELP_ITEMS) {
            st("help " + id, () -> {
                ItemStack stack = stackOf(id);
                Class<?> stackClass = Class.forName("works.repka.rysindex.model.Stack");
                Object res = Class.forName("works.repka.rysindex.model.ItemRes").getConstructor(ItemStack.class).newInstance(stack);
                Class<?> mode = Class.forName("works.repka.rysindex.runtime.Lookup$Mode");
                @SuppressWarnings({"unchecked", "rawtypes"})
                Object m = Enum.valueOf((Class<Enum>) mode, "RECIPES");
                Object opened = Class.forName("works.repka.rysindex.ui.RecipeScreen").getMethod("open", stackClass, mode).invoke(null, res, m);
                Screen s = mc().screen;
                if (!Boolean.TRUE.equals(opened) || s == null || !s.getClass().getSimpleName().equals("RecipeScreen")) {
                    log("G.3 help " + id, "FAIL", "not opened: " + opened + ", screen " + (s == null ? "-" : s.getClass().getSimpleName()));
                    return;
                }
                Object sec = s.getClass().getMethod("sectionsForTest").invoke(s);
                Object current = sec.getClass().getMethod("current").invoke(sec);
                Object counts = sec.getClass().getMethod("counts").invoke(sec);
                List<?> help = (List<?>) sec.getClass().getMethod("help").invoke(sec);
                String verdict = help.size() == 1 ? "OK" : help.isEmpty() ? "FAIL" : "INFO";
                log("G.3 help " + id, verdict, stack.getHoverName().getString() + ": current " + current + ", counts " + counts + ", help pages " + help.size());
                s.getClass().getMethod("switchSectionForTest", String.class).invoke(s, "help");
            }, 40);
            shot("help_" + id.replaceAll("[^a-z0-9_]", "_"));
            st("close", () -> mc().setScreen(null), 10);
        }
    }

    /** The wheel: held and released over a sector runs it; «Transfer» → «Server places», Esc back. */
    private static void wheel() {
        st("wheel rings", () -> {
            StringBuilder out = new StringBuilder();
            for (Ring r : Actions.availableRings()) {
                out.append(r.id()).append(": ");
                List<Action> slots = Actions.slots(r.id());
                for (int i = 0; i < slots.size(); i++) {
                    Action a = slots.get(i);
                    out.append(i + 1).append('=').append(a == null ? "-" : a.id() + (a.opensSub() ? "[" + a.sub().size() + "]" : "")).append(' ');
                }
                out.append("| ");
            }
            log("W.0 wheel", "INFO", "hold mode " + Actions.holdMode() + "; " + out);
        }, 2);
        // held: the wheel opened by the held key, the mouse over the sector of the map, the key let go after 250 ms
        // all in one step: the first tick of the wheel lets the hold go, so the time is moved back and the mouse is on the sector
        // before it; the frame after this step sees the mouse on the sector, the next tick finds the key up after 250 ms
        st("wheel hold open", () -> {
            int slot = mapSlot();
            if (slot < 0) {
                log("W.1 wheel hold", "FAIL", "no quest book sector in the first ring");
                return;
            }
            Class<?> c = Class.forName("works.repka.ryscore.action.WheelScreen");
            var ctor = c.getDeclaredConstructor(boolean.class, Action.class);
            ctor.setAccessible(true);
            Screen w = (Screen) ctor.newInstance(true, null);
            wheel = w;
            mc().setScreen(w);
            aimSector(w, slot);
            setLong(w, "openedAt", System.currentTimeMillis() - 1000);
            // the same sector the next frame finds under the mouse, in case a second tick comes before that frame
            Field hovered = c.getDeclaredField("hovered");
            hovered.setAccessible(true);
            hovered.setInt(w, slot);
        }, 30);
        st("wheel hold result", () -> {
            if (wheel == null) {
                return;
            }
            String now = mc().screen == null ? "-" : mc().screen.getClass().getSimpleName();
            log("W.1 wheel hold and release on the quest book sector", now.equals("BookScreen") ? "OK" : "FAIL", "screen after release: " + now);
        }, 5);
        shot("wheel_hold_result");
        st("close", () -> {
            mc().setScreen(null);
            wheel = null;
        }, 10);
        // «Transfer…» and its list of server places, Esc back step by step
        st("wheel sub open", () -> {
            Actions.openWheel();
        }, 10);
        st("wheel sub transfer", () -> {
            int slot = subSlot();
            if (slot < 0) {
                log("W.2 wheel transfer", "FAIL", "no sector with a submenu");
                return;
            }
            press(GLFW.GLFW_KEY_1 + slot);
            log("W.2 wheel transfer", "INFO", "sector " + (slot + 1) + " " + firstRing().get(slot).id() + ", open subs " + subs());
        }, 15);
        shot("wheel_sub_transfer");
        st("wheel sub places", () -> {
            Screen w = mc().screen;
            Action owner = top(w);
            if (owner == null) {
                log("W.2 wheel places", "FAIL", "no submenu open");
                return;
            }
            Action places = null;
            for (Action a : owner.sub()) {
                if (a.opensSub() && a.id().getPath().contains("place")) {
                    places = a;
                }
            }
            if (places == null) {
                for (Action a : owner.sub()) {
                    if (a.opensSub()) {
                        places = a;
                    }
                }
            }
            if (places == null) {
                log("W.2 wheel places", "FAIL", "no list in " + owner.id() + ": " + owner.sub().stream().map(Action::id).toList());
                return;
            }
            Method drill = w.getClass().getDeclaredMethod("drill", Action.class);
            drill.setAccessible(true);
            drill.invoke(w, places);
            log("W.2 wheel places", subs() == 2 ? "OK" : "FAIL", places.id() + " \"" + places.label().getString() + "\" rows " + places.sub().size() + ", open subs " + subs());
        }, 15);
        shot("wheel_sub_places");
        st("wheel esc 1", () -> press(GLFW.GLFW_KEY_ESCAPE), 10);
        st("wheel esc 1 result", () -> log("W.2 wheel esc back to the set", subs() == 1 ? "OK" : "FAIL", "open subs " + subs()), 2);
        shot("wheel_sub_back");
        st("wheel esc 2", () -> press(GLFW.GLFW_KEY_ESCAPE), 10);
        st("wheel esc 2 result", () -> log("W.2 wheel esc back to the wheel", subs() == 0 && mc().screen != null ? "OK" : "FAIL",
                "open subs " + subs() + ", screen " + (mc().screen == null ? "-" : mc().screen.getClass().getSimpleName())), 2);
        st("wheel esc 3", () -> press(GLFW.GLFW_KEY_ESCAPE), 10);
        st("wheel esc 3 result", () -> log("W.2 wheel esc closes", mc().screen == null ? "OK" : "FAIL",
                "screen " + (mc().screen == null ? "-" : mc().screen.getClass().getSimpleName())), 2);
    }

    /** RYSIndex in a chest, give mode: 20 clicks on one item of the list, each held a little, nothing falls to the ground. */
    private static void indexClicks() {
        BlockPos chest = new BlockPos(pad.getX(), pad.getY(), pad.getZ() - 3);
        st("chest place", () -> {
            command("gamemode creative");
            command(String.format(Locale.ROOT, "tp @s %d %d %d 180 20", pad.getX(), pad.getY(), pad.getZ()));
            command("clear @s");
            command(String.format(Locale.ROOT, "setblock %d %d %d minecraft:chest", chest.getX(), chest.getY(), chest.getZ()));
            command("kill @e[type=item,distance=..12]");
        }, 30);
        st("chest open", () -> use(chest), 30);
        st("index clicks start", () -> {
            dropsBefore = drops();
            clicks = 0;
            log("I.0 chest", mc().screen instanceof AbstractContainerScreen ? "INFO" : "FAIL", "screen " + (mc().screen == null ? "-" : mc().screen.getClass().getSimpleName()));
        }, 5);
        for (int i = 0; i < 20; i++) {
            final int hold = 1 + i % 4;
            st("index click " + i + " press", () -> {
                double[] at = listCell(3);
                if (at == null) {
                    return;
                }
                moveMouse(UiScale.toGui(at[0]), UiScale.toGui(at[1]));
                button(GLFW.GLFW_PRESS);
            }, hold);
            st("index click " + i + " release", () -> {
                button(GLFW.GLFW_RELEASE);
                clicks++;
            }, 4);
        }
        st("index clicks result", () -> {
            ItemStack carried = mc().player.containerMenu.getCarried();
            int dropped = drops() - dropsBefore;
            log("I.1 RYSIndex 20 clicks in a chest", dropped == 0 && !carried.isEmpty() ? "OK" : "FAIL",
                    clicks + " clicks, on the cursor " + carried.getCount() + " " + BuiltInRegistries.ITEM.getKey(carried.getItem()) + ", fell to the ground " + dropped);
        }, 20);
        shot("index_clicks");
        st("index clicks clean", () -> {
            command("clear @s");
            command("kill @e[type=item,distance=..12]");
        }, 10);
        st("close", () -> mc().setScreen(null), 10);
        st("chest remove", () -> command(String.format(Locale.ROOT, "setblock %d %d %d minecraft:air", chest.getX(), chest.getY(), chest.getZ())), 10);
    }

    /** The server window: the journal in words, a report in the reports tab, settings answer at once, the white list. */
    private static void helm() {
        commandShot("report MityaYS check1124 test report", "H.11 report");
        st("helm journal", () -> openHelm("journal"), 60);
        shot("helm_journal");
        st("helm reports", () -> openHelm("reports"), 60);
        st("helm reports data", () -> {
            Object reports = Class.forName("works.repka.ryshelm.client.View").getField("reports").get(null);
            String text = String.valueOf(reports);
            log("H.11 report in the reports tab", text.contains("check1124") ? "OK" : "FAIL", "view.reports has check1124: " + text.contains("check1124"));
        }, 5);
        shot("helm_reports");
        st("helm settings", () -> openHelm("settings"), 60);
        shot("helm_settings");
        // the stepper of the newbie hours: the click shows the new value before the server answers
        st("helm stepper plus", () -> {
            int[][] rects = settingsHits();
            int[] plus = null;
            for (int[] r : rects) {
                if (r[2] < 40 && r[3] >= 14 && plus == null && r[1] > rects[0][1] + 10) {
                    // first stepper row below the first toggle; the stepper's two halves, take the right one
                    plus = r;
                }
            }
            if (plus == null) {
                log("H.10 settings stepper", "FAIL", "no stepper hits: " + describe(rects));
                return;
            }
            int[] right = plus;
            for (int[] r : rects) {
                if (r[1] == plus[1] && r[0] > right[0]) {
                    right = r;
                }
            }
            settingsKey = "s.newbieHours";
            settingsValue = settingInt("newbieHours");
            clickUi(right[0] + right[2] / 2.0, right[1] + right[3] / 2.0);
            int shown = pendingInt(settingsKey, settingsValue);
            log("H.10 settings stepper shows at once", shown == settingsValue + 1 ? "OK" : "FAIL",
                    "newbieHours " + settingsValue + " -> shown " + shown + " in the same tick; hits " + describe(rects));
        }, 30);
        st("helm stepper server", () -> {
            int now = settingInt("newbieHours");
            log("H.10 settings stepper saved", now == settingsValue + 1 ? "OK" : "FAIL", "server value " + now);
            if (now != settingsValue) {
                setSetting("newbieHours", settingsValue);
            }
        }, 30);
        // the toggle of anti-spam: the third toggle in the column (rules on join, white list, anti-spam)
        st("helm toggle", () -> {
            List<int[]> toggles = toggles();
            if (toggles.size() < 3) {
                log("H.10 settings toggle", "FAIL", "toggles " + toggles.size());
                return;
            }
            int[] t = toggles.get(2);
            boolean was = settingBool("antiSpam");
            clickUi(t[0] + t[2] / 2.0, t[1] + t[3] / 2.0);
            boolean shown = pendingBool("s.antiSpam", was);
            settingsValue = was ? 1 : 0;
            log("H.10 settings toggle shows at once", shown != was ? "OK" : "FAIL", "antiSpam " + was + " -> shown " + shown + " in the same tick");
        }, 30);
        st("helm toggle server", () -> {
            boolean now = settingBool("antiSpam");
            boolean was = settingsValue == 1;
            log("H.10 settings toggle saved", now != was ? "OK" : "FAIL", "server value " + now);
            if (now != was) {
                setSettingBool("antiSpam", was);
            }
        }, 30);
        shot("helm_settings_after");
        // white list on with the player online and not on it: nobody is kicked; add and remove a name in the window
        st("helm whitelist on", () -> {
            List<int[]> toggles = toggles();
            if (toggles.size() < 2) {
                log("H.10 white list toggle", "FAIL", "toggles " + toggles.size());
                return;
            }
            int[] t = toggles.get(1);
            clickUi(t[0] + t[2] / 2.0, t[1] + t[3] / 2.0);
        }, 100);
        st("helm whitelist online", () -> log("S.6 white list does not kick the online player", mc().player != null && mc().getConnection() != null
                && settingBool("whitelist") ? "OK" : "FAIL", "whitelist " + settingBool("whitelist") + ", still in the world " + (mc().player != null)), 5);
        st("helm whitelist add", () -> {
            Object box = field(mc().screen, "wlName");
            box.getClass().getMethod("setValue", String.class).invoke(box, "Check_Tester");
            Method add = mc().screen.getClass().getDeclaredMethod("addWhitelist");
            add.setAccessible(true);
            add.invoke(mc().screen);
        }, 40);
        st("helm whitelist added", () -> log("H.10 white list add", names().stream().anyMatch("Check_Tester"::equalsIgnoreCase) ? "OK" : "FAIL", "names " + names()), 5);
        shot("helm_whitelist_added");
        st("helm whitelist remove", () -> {
            net.minecraft.nbt.CompoundTag a = new net.minecraft.nbt.CompoundTag();
            a.putString("remove", "Check_Tester");
            Class.forName("works.repka.ryshelm.client.HelmClient").getMethod("ask", String.class, Object.class, String.class, net.minecraft.nbt.CompoundTag.class)
                    .invoke(null, "wl.rm.Check_Tester", null, "whitelist", a);
        }, 40);
        st("helm whitelist removed", () -> log("H.10 white list remove", names().stream().noneMatch("Check_Tester"::equalsIgnoreCase) ? "OK" : "FAIL", "names " + names()), 5);
        st("helm whitelist off", () -> {
            net.minecraft.nbt.CompoundTag a = new net.minecraft.nbt.CompoundTag();
            a.putBoolean("on", false);
            Class.forName("works.repka.ryshelm.client.HelmClient").getMethod("ask", String.class, Object.class, String.class, net.minecraft.nbt.CompoundTag.class)
                    .invoke(null, "wl.on", false, "whitelist", a);
        }, 40);
        st("helm whitelist off result", () -> log("H.10 white list off", !settingBool("whitelist") ? "OK" : "FAIL", "whitelist " + settingBool("whitelist")), 5);
        st("close", () -> mc().setScreen(null), 10);
    }

    /** Oritech: a pulverizer facing north, one addon at a time on its slot (south side), the machine window opened. */
    private static void oritech() {
        BlockPos machine = new BlockPos(pad.getX() + 3, pad.getY(), pad.getZ() + 1);
        BlockPos slot = machine.south();
        for (String addon : ADDONS) {
            String name = addon.substring(addon.indexOf(':') + 1);
            st("oritech " + name + " place", () -> {
                command("gamemode creative");
                command(String.format(Locale.ROOT, "tp @s %d %d %d 0 20", pad.getX() + 1, pad.getY(), pad.getZ() + 3));
                command(String.format(Locale.ROOT, "setblock %d %d %d minecraft:air", slot.getX(), slot.getY(), slot.getZ()));
                command(String.format(Locale.ROOT, "setblock %d %d %d minecraft:air", machine.getX(), machine.getY(), machine.getZ()));
                command(String.format(Locale.ROOT, "setblock %d %d %d oritech:pulverizer_block[facing=north]", machine.getX(), machine.getY(), machine.getZ()));
                command("clear @s");
                command("give @s " + addon);
            }, 30);
            st("oritech " + name + " addon", () -> {
                selectHotbar(item(addon));
                BlockPos floor = slot.below();
                mc().gameMode.useItemOn(mc().player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false));
            }, 30);
            st("oritech " + name + " open", () -> {
                log("O.7 " + name + " placed", BuiltInRegistries.BLOCK.getKey(mc().level.getBlockState(slot).getBlock()).toString().equals(addon) ? "OK" : "FAIL",
                        "at the slot " + mc().level.getBlockState(slot));
                use(machine);
            }, 40);
            st("oritech " + name + " window", () -> {
                Screen s = mc().screen;
                List<String> widgets = new ArrayList<>();
                if (s != null) {
                    for (var c : s.children()) {
                        if (c instanceof AbstractWidget w) {
                            widgets.add(w.getClass().getSimpleName() + "\"" + w.getMessage().getString() + "\"@" + w.getX() + "," + w.getY());
                        } else {
                            widgets.add(c.getClass().getSimpleName());
                        }
                    }
                }
                log("O.7 " + name + " window", s != null ? "INFO" : "FAIL", (s == null ? "no screen" : s.getClass().getName()) + " children " + widgets);
            }, 5);
            shot("oritech_" + name);
            // the "Addons" button at the bottom of the details panel (one line lower when the tank adds a line)
            final int by = name.equals("tank_addon") ? 452 : 410;
            st("oritech " + name + " addons", () -> {
                double k = mc().getWindow().getGuiScaledWidth() / (double) mc().getWindow().getWidth();
                moveMouse(1005 * k, by * k);
                button(GLFW.GLFW_PRESS);
                button(GLFW.GLFW_RELEASE);
            }, 20);
            st("oritech " + name + " addons screen", () -> log("O.7 " + name + " addons", "INFO",
                    "screen " + (mc().screen == null ? "-" : mc().screen.getClass().getName())), 2);
            shot("oritech_" + name + "_addons");
            st("close", () -> mc().setScreen(null), 10);
        }
        st("oritech clean", () -> {
            command(String.format(Locale.ROOT, "setblock %d %d %d minecraft:air", slot.getX(), slot.getY(), slot.getZ()));
            command(String.format(Locale.ROOT, "setblock %d %d %d minecraft:air", machine.getX(), machine.getY(), machine.getZ()));
            command("clear @s");
            command("kill @e[type=item,distance=..12]");
        }, 10);
    }

    /** A Create burner fed fire essence; an Actually Additions breaker at the border of foreign land and one in the open. */
    private static void mechanics() {
        BlockPos burner = new BlockPos(pad.getX() - 3, pad.getY(), pad.getZ() + 2);
        st("burner place", () -> {
            command(String.format(Locale.ROOT, "tp @s %d %d %d 0 20", pad.getX(), pad.getY(), pad.getZ()));
            command(String.format(Locale.ROOT, "setblock %d %d %d create:blaze_burner[blaze=smouldering]", burner.getX(), burner.getY(), burner.getZ()));
            command("clear @s");
            command("give @s mysticalagriculture:fire_essence 4");
            command("gamemode survival");
        }, 30);
        st("burner feed", () -> {
            selectHotbar(item("mysticalagriculture:fire_essence"));
            log("M.12 burner before", "INFO", String.valueOf(mc().level.getBlockState(burner)));
            mc().gameMode.useItemOn(mc().player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(burner).add(0, 0.5, 0), Direction.UP, burner, false));
        }, 30);
        st("burner result", () -> {
            String state = String.valueOf(mc().level.getBlockState(burner));
            log("M.12 burner on fire essence", state.contains("seething") ? "OK" : "FAIL", state + ", essence left " + count(item("mysticalagriculture:fire_essence")));
            command("gamemode creative");
        }, 10);
        shot("burner");
        st("burner clean", () -> command(String.format(Locale.ROOT, "setblock %d %d %d minecraft:air", burner.getX(), burner.getY(), burner.getZ())), 5);
        // control in the open (on the pad), then at the border: the breaker outside, the block inside the claim
        BlockPos open = new BlockPos(pad.getX() - 4, pad.getY() + 2, pad.getZ());
        breaker("open", open, true);
        BlockPos border = new BlockPos(5807, 150, 26095);
        st("breaker border tp", () -> command(String.format(Locale.ROOT, "tp @s %d %d %d 90 20", border.getX() - 3, border.getY(), border.getZ())), 80);
        breaker("border", border, false);
    }

    private static void breaker(String where, BlockPos at, boolean expectBroken) {
        BlockPos target = at.east();
        BlockPos power = at.west();
        st("breaker " + where + " place", () -> {
            command(String.format(Locale.ROOT, "setblock %d %d %d minecraft:air", power.getX(), power.getY(), power.getZ()));
            command(String.format(Locale.ROOT, "setblock %d %d %d actuallyadditions:breaker[facing=east]", at.getX(), at.getY(), at.getZ()));
            command(String.format(Locale.ROOT, "setblock %d %d %d minecraft:stone", target.getX(), target.getY(), target.getZ()));
        }, 20);
        st("breaker " + where + " power", () -> command(String.format(Locale.ROOT, "setblock %d %d %d minecraft:redstone_block", power.getX(), power.getY(), power.getZ())), 60);
        st("breaker " + where + " result", () -> {
            boolean broken = mc().level.getBlockState(target).isAir();
            String detail = "breaker " + mc().level.getBlockState(at) + ", target " + mc().level.getBlockState(target) + " at " + target.toShortString();
            log(expectBroken ? "M.12 breaker in the open breaks (control)" : "M.12 breaker at the border of foreign land does not break",
                    broken == expectBroken ? "OK" : "FAIL", detail);
            command(String.format(Locale.ROOT, "setblock %d %d %d minecraft:air", power.getX(), power.getY(), power.getZ()));
            command(String.format(Locale.ROOT, "setblock %d %d %d minecraft:air", at.getX(), at.getY(), at.getZ()));
            command(String.format(Locale.ROOT, "setblock %d %d %d minecraft:air", target.getX(), target.getY(), target.getZ()));
        }, 10);
    }

    /** A guest (not a member, no bypass, survival) on the land of Chuzhoy_Test: eats, drinks, shoots, blocks, hits; opens nothing. */
    private static void guest() {
        BlockPos chest = GUEST.west(2);
        st("shield control", () -> {
            command(String.format(Locale.ROOT, "tp @s %d %d %d 0 -10", pad.getX(), pad.getY(), pad.getZ()));
            command("clear @s");
            command("item replace entity @s weapon.offhand with minecraft:shield");
            command("gamemode survival");
        }, 30);
        st("shield control use", () -> {
            mc().player.getInventory().selected = 8;
            mc().options.keyUse.setDown(true);
        }, 15);
        st("shield control result", () -> {
            boolean blocking = mc().player.isUsingItem() && mc().player.getUseItem().is(item("minecraft:shield"));
            mc().options.keyUse.setDown(false);
            log("S.3 shield outside claims (control)", blocking ? "OK" : "FAIL", "using " + mc().player.getUseItem() + ", offhand "
                    + mc().player.getOffhandItem());
            command("gamemode creative");
        }, 10);
        st("guest platform", () -> {
            command("gamemode creative");
            command(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:glass", GUEST.getX() - 4, GUEST.getY() - 1, GUEST.getZ() - 4,
                    GUEST.getX() + 4, GUEST.getY() - 1, GUEST.getZ() + 4));
            command(String.format(Locale.ROOT, "tp @s %d %d %d 90 -90", GUEST.getX(), GUEST.getY(), GUEST.getZ()));
        }, 80);
        commandShot("claims info", "S.3 claims info on the guest platform");
        st("guest kit", () -> {
            command("clear @s");
            command(String.format(Locale.ROOT, "setblock %d %d %d minecraft:chest", chest.getX(), chest.getY(), chest.getZ()));
            command("give @s minecraft:golden_apple 2");
            command("give @s minecraft:potion[potion_contents={potion:\"minecraft:water\"}]");
            command("give @s minecraft:bow");
            command("give @s minecraft:arrow 8");
            command("give @s minecraft:iron_sword");
            command("item replace entity @s weapon.offhand with minecraft:shield");
            command("gamemode survival");
        }, 30);
        // eat: hold use for 2 s looking at the sky
        useFor("eat", "minecraft:golden_apple", 45, () -> {
            int left = count(item("minecraft:golden_apple"));
            log("S.3 guest eats", left == 1 ? "OK" : "FAIL", "golden apples left " + left + ", absorption " + mc().player.getAbsorptionAmount());
        });
        useFor("drink", "minecraft:potion", 45, () -> {
            int bottles = count(item("minecraft:glass_bottle"));
            log("S.3 guest drinks", bottles == 1 ? "OK" : "FAIL", "glass bottles " + bottles + ", potions " + count(item("minecraft:potion")));
        });
        st("guest look ahead", () -> command(String.format(Locale.ROOT, "tp @s %d %d %d 0 -10", GUEST.getX(), GUEST.getY(), GUEST.getZ())), 10);
        useFor("bow", "minecraft:bow", 25, () -> {
            int arrows = 0;
            for (Entity e : mc().level.getEntities((Entity) null, new AABB(GUEST).inflate(48), e -> e instanceof AbstractArrow)) {
                arrows++;
            }
            log("S.3 guest shoots a bow", arrows > 0 || count(item("minecraft:arrow")) < 8 ? "OK" : "FAIL", "arrows flying " + arrows + ", left " + count(item("minecraft:arrow")));
        });
        st("guest shield", () -> {
            mc().player.getInventory().selected = 8;
            command("clear @s minecraft:iron_sword");
            mc().options.keyUse.setDown(true);
        }, 15);
        st("guest shield result", () -> {
            boolean blocking = mc().player.isUsingItem() && mc().player.getUseItem().is(item("minecraft:shield"));
            mc().options.keyUse.setDown(false);
            log("S.3 guest raises a shield", blocking ? "OK" : "FAIL", "using " + mc().player.getUseItem() + ", blocking " + mc().player.isBlocking()
                    + ", offhand " + mc().player.getOffhandItem() + ", main " + mc().player.getMainHandItem());
        }, 10);
        st("guest hoglin", () -> {
            command("give @s minecraft:iron_sword");
            command(String.format(Locale.ROOT, "summon minecraft:hoglin %d %d %d {NoAI:1b,PersistenceRequired:1b}", GUEST.getX() + 2, GUEST.getY(), GUEST.getZ()));
        }, 30);
        st("guest hit", () -> {
            selectHotbar(item("minecraft:iron_sword"));
            LivingEntity hoglin = hoglin();
            if (hoglin == null) {
                log("S.3 guest hits a hoglin", "FAIL", "no hoglin");
                return;
            }
            mc().gameMode.attack(mc().player, hoglin);
        }, 20);
        st("guest hit result", () -> {
            LivingEntity hoglin = hoglin();
            log("S.3 guest hits a hoglin", hoglin != null && hoglin.getHealth() < hoglin.getMaxHealth() ? "OK" : "FAIL",
                    hoglin == null ? "no hoglin" : "health " + hoglin.getHealth() + " of " + hoglin.getMaxHealth());
        }, 5);
        st("guest chest", () -> use(chest), 30);
        st("guest chest result", () -> {
            Screen s = mc().screen;
            log("S.3 guest cannot open a chest", !(s instanceof AbstractContainerScreen) ? "OK" : "FAIL", "screen " + (s == null ? "-" : s.getClass().getSimpleName()));
            mc().setScreen(null);
        }, 10);
        CheckScenario.chatShot("guest_chat");
        st("guest clean", () -> {
            command("gamemode creative");
            command("kill @e[type=minecraft:hoglin,distance=..20]");
            command("kill @e[type=minecraft:arrow,distance=..64]");
            command("clear @s");
            command(String.format(Locale.ROOT, "setblock %d %d %d minecraft:air", chest.getX(), chest.getY(), chest.getZ()));
            command(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:air", GUEST.getX() - 4, GUEST.getY() - 1, GUEST.getZ() - 4,
                    GUEST.getX() + 4, GUEST.getY() - 1, GUEST.getZ() + 4));
            command(String.format(Locale.ROOT, "tp @s %d %d %d 180 20", pad.getX(), pad.getY(), pad.getZ()));
        }, 60);
    }

    /** A player without OP: no operator ring in the wheel, no staff tabs in the server window. OP comes back by a chat line (zz_check1124.js). */
    private static void noOp() {
        st("deop", () -> command("deop Dev"), 40);
        st("noop wheel", () -> {
            List<String> rings = new ArrayList<>();
            for (Ring r : Actions.availableRings()) {
                rings.add(r.id().toString());
            }
            log("S.5 no operator ring without OP", !rings.contains(Ring.OPERATOR.toString()) ? "OK" : "FAIL",
                    "permission " + (mc().player.hasPermissions(2) ? "op" : "player") + ", rings " + rings);
            Actions.openWheel();
        }, 20);
        shot("noop_wheel");
        st("close", () -> mc().setScreen(null), 10);
        st("noop helm", () -> openHelm(null), 40);
        st("noop helm tabs", () -> {
            Screen s = mc().screen;
            if (s == null || !s.getClass().getSimpleName().equals("ServerScreen")) {
                log("S.5 server window without OP", "INFO", "screen " + (s == null ? "-" : s.getClass().getSimpleName()));
                return;
            }
            List<String> shown = new ArrayList<>();
            Class<?> tab = Class.forName("works.repka.ryshelm.client.ServerScreen$Tab");
            Method visible = null;
            for (Method m : s.getClass().getDeclaredMethods()) {
                if (m.getName().equals("tabVisible") && m.getParameterCount() == 1) {
                    visible = m;
                }
            }
            if (visible != null) {
                visible.setAccessible(true);
                for (Object t : tab.getEnumConstants()) {
                    if (Boolean.TRUE.equals(visible.invoke(java.lang.reflect.Modifier.isStatic(visible.getModifiers()) ? null : s, t))) {
                        shown.add(String.valueOf(t));
                    }
                }
            }
            log("S.5 server window tabs without OP", shown.stream().noneMatch(x -> x.equals("ROLES") || x.equals("SETTINGS") || x.equals("JOURNAL")) ? "OK" : "FAIL",
                    "tabs " + shown);
        }, 5);
        shot("noop_helm");
        st("close", () -> mc().setScreen(null), 10);
        st("reop", () -> mc().player.connection.sendChat("check1124 reop"), 60);
        st("reop result", () -> log("S.5 OP back", mc().player.hasPermissions(2) ? "OK" : "FAIL", "permission " + mc().player.hasPermissions(2)), 5);
    }

    /**
     * The book's header in the three themes of the core (the author 24.11: «табы выглядят плохо и поиск чёрный»): «Now» and the map
     * with a quest open, the search empty and with text, in a 1920x1080 window like the CurseForge pictures (frame 8).
     */
    private static void themes() {
        ResourceLocation here = ResourceLocation.parse("repys:b03_e2_current/electric_mechanism");
        st("themes window", () -> mc().getWindow().setWindowed(1920, 1080), 60);
        for (String theme : new String[] {"slate", "glass", "vanilla"}) {
            st("theme " + theme, () -> {
                works.repka.ryscore.theme.ThemeManager.select("ryscore:" + theme);
                if (!(mc().screen instanceof BookScreen)) {
                    mc().setScreen(new BookScreen());
                }
                works.repka.rysquests.client.screen.PanelShowcase.now(false);
            }, 60);
            shot("tabs_" + theme + "_now");
            st("theme map " + theme, () -> BookScreen.openAt(here), 60);
            shot("tabs_" + theme + "_map");
            st("theme search " + theme, () -> {
                Object box = field(mc().screen, "searchBox");
                box.getClass().getMethod("setFocused", boolean.class).invoke(box, true);
                box.getClass().getMethod("setValue", String.class).invoke(box, "mech");
            }, 20);
            shot("tabs_" + theme + "_search");
            st("theme search clear " + theme, () -> {
                Object box = field(mc().screen, "searchBox");
                box.getClass().getMethod("setValue", String.class).invoke(box, "");
                box.getClass().getMethod("setFocused", boolean.class).invoke(box, false);
            }, 10);
        }
        st("themes back", () -> {
            works.repka.ryscore.theme.ThemeManager.select("ryscore:slate");
            mc().setScreen(null);
            mc().getWindow().setWindowed(1280, 800);
            command("gamerule sendCommandFeedback false");
        }, 60);
    }

    // ------------------------------------------------------------------ helpers

    interface Body {
        void run() throws Exception;
    }

    /** A step that never ends the pass: whatever it throws is a FAIL line. */
    static void st(String name, Body body, int wait) {
        step(name, () -> {
            try {
                body.run();
            } catch (Throwable e) {
                Throwable cause = e instanceof java.lang.reflect.InvocationTargetException ite && ite.getCause() != null ? ite.getCause() : e;
                log("step " + name, "FAIL", cause.toString());
            }
        }, wait);
    }

    private static void useFor(String what, String id, int ticks, Body check) {
        st("guest " + what, () -> {
            selectHotbar(item(id));
            mc().options.keyUse.setDown(true);
        }, ticks);
        st("guest " + what + " stop", () -> mc().options.keyUse.setDown(false), 15);
        st("guest " + what + " result", check, 2);
    }

    private static LivingEntity hoglin() {
        for (Entity e : mc().level.getEntities((Entity) null, new AABB(GUEST).inflate(8), e -> e.getType() == net.minecraft.world.entity.EntityType.HOGLIN)) {
            return (LivingEntity) e;
        }
        return null;
    }

    private static int count(Item it) {
        int n = 0;
        for (ItemStack s : mc().player.getInventory().items) {
            if (s.is(it)) {
                n += s.getCount();
            }
        }
        for (ItemStack s : mc().player.getInventory().offhand) {
            if (s.is(it)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static void selectHotbar(Item it) {
        for (int i = 0; i < 9; i++) {
            if (mc().player.getInventory().getItem(i).is(it)) {
                mc().player.getInventory().selected = i;
                return;
            }
        }
    }

    private static int drops() {
        int n = 0;
        for (Entity e : mc().level.getEntities((Entity) null, mc().player.getBoundingBox().inflate(12), e -> e instanceof ItemEntity)) {
            n += ((ItemEntity) e).getItem().getCount();
        }
        return n;
    }

    private static ItemStack stackOf(String id) throws ReflectiveOperationException {
        if (id.startsWith("mmr:")) {
            return (ItemStack) Class.forName("es.degrassi.mmreborn.common.item.ControllerItem")
                    .getMethod("makeMachineItem", ResourceLocation.class).invoke(null, ResourceLocation.parse(id.substring(4)));
        }
        return new ItemStack(item(id));
    }

    /** Centre of a cell of the RYSIndex list, in its interface units, or null (with a FAIL line) when the list is not shown. */
    private static double[] listCell(int index) throws ReflectiveOperationException {
        Class<?> overlay = Class.forName("works.repka.rysindex.ui.Overlay");
        Field f = overlay.getDeclaredField("LIST");
        f.setAccessible(true);
        Object grid = f.get(null);
        Class<?> g = grid.getClass();
        boolean visible = (Boolean) g.getMethod("visible").invoke(grid);
        if (!visible) {
            log("I.1 RYSIndex list", "FAIL", "the list is not shown");
            return null;
        }
        int gx = g.getField("gridX").getInt(grid), gy = g.getField("gridY").getInt(grid), cell = g.getField("cell").getInt(grid), cols = g.getField("cols").getInt(grid);
        int col = index % cols, row = index / cols;
        return new double[] {gx + col * cell + cell / 2.0, gy + row * cell + cell / 2.0};
    }

    /** Moves the cursor as the window system does; coordinates in game GUI units (the same as RYSIndex's autotest). */
    private static void moveMouse(double guiX, double guiY) throws ReflectiveOperationException {
        double scale = mc().getWindow().getGuiScale();
        Method m = net.minecraft.client.MouseHandler.class.getDeclaredMethod("onMove", long.class, double.class, double.class);
        m.setAccessible(true);
        double sx = guiX * scale * mc().getWindow().getScreenWidth() / mc().getWindow().getWidth();
        double sy = guiY * scale * mc().getWindow().getScreenHeight() / mc().getWindow().getHeight();
        m.invoke(mc().mouseHandler, mc().getWindow().getWindow(), sx, sy);
    }

    private static void button(int action) throws ReflectiveOperationException {
        Method m = net.minecraft.client.MouseHandler.class.getDeclaredMethod("onPress", long.class, int.class, int.class, int.class);
        m.setAccessible(true);
        m.invoke(mc().mouseHandler, mc().getWindow().getWindow(), 0, action, 0);
    }

    /** A click at a point given in the interface units of the family's windows. */
    private static void clickUi(double x, double y) throws ReflectiveOperationException {
        moveMouse(UiScale.toGui(x), UiScale.toGui(y));
        button(GLFW.GLFW_PRESS);
        button(GLFW.GLFW_RELEASE);
    }

    private static void press(int key) {
        long w = mc().getWindow().getWindow();
        int scan = GLFW.glfwGetKeyScancode(key);
        mc().keyboardHandler.keyPress(w, key, scan, GLFW.GLFW_PRESS, 0);
        mc().keyboardHandler.keyPress(w, key, scan, GLFW.GLFW_RELEASE, 0);
    }

    private static List<Action> firstRing() {
        List<Ring> rings = Actions.availableRings();
        return rings.isEmpty() ? List.of() : Actions.slots(rings.get(0).id());
    }

    private static int mapSlot() {
        List<Action> slots = firstRing();
        for (int i = 0; i < slots.size(); i++) {
            Action a = slots.get(i);
            if (a != null && !a.opensSub() && a.id().toString().contains("rysquests_book") && a.state().usable()) {
                return i;
            }
        }
        return -1;
    }

    private static int subSlot() {
        List<Action> slots = firstRing();
        for (int i = 0; i < slots.size(); i++) {
            Action a = slots.get(i);
            if (a != null && a.opensSub()) {
                for (Action r : a.sub()) {
                    if (r.opensSub()) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    private static void aimSector(Screen w, int slot) throws ReflectiveOperationException {
        Class<?> c = w.getClass();
        Field ir = c.getDeclaredField("IR");
        Field r = c.getDeclaredField("R");
        ir.setAccessible(true);
        r.setAccessible(true);
        double radius = (((Number) ir.get(null)).doubleValue() + ((Number) r.get(null)).doubleValue()) / 2;
        double angle = Math.toRadians(-90 + 45 * slot);
        double x = w.width / 2.0 + radius * Math.cos(angle);
        double y = w.height / 2.0 - 6 + radius * Math.sin(angle);
        moveMouse(UiScale.toGui(x), UiScale.toGui(y));
    }

    private static int subs() {
        try {
            Screen w = mc().screen;
            if (w == null || !w.getClass().getSimpleName().equals("WheelScreen")) {
                return -1;
            }
            return ((java.util.Collection<?>) field(w, "subs")).size();
        } catch (ReflectiveOperationException e) {
            return -2;
        }
    }

    private static Action top(Screen w) throws ReflectiveOperationException {
        java.util.Deque<?> d = (java.util.Deque<?>) field(w, "subs");
        return (Action) d.peekFirst();
    }

    private static void openHelm(String tab) throws ReflectiveOperationException {
        Class.forName("works.repka.ryshelm.client.ServerScreen").getMethod("open", String.class).invoke(null, tab);
    }

    private static net.minecraft.nbt.CompoundTag settings() throws ReflectiveOperationException {
        return (net.minecraft.nbt.CompoundTag) Class.forName("works.repka.ryshelm.client.View").getField("settings").get(null);
    }

    private static int settingInt(String key) throws ReflectiveOperationException {
        return settings().getInt(key);
    }

    private static boolean settingBool(String key) throws ReflectiveOperationException {
        return settings().getBoolean(key);
    }

    private static List<String> names() throws ReflectiveOperationException {
        List<String> out = new ArrayList<>();
        for (net.minecraft.nbt.Tag t : settings().getList("whitelistNames", net.minecraft.nbt.Tag.TAG_STRING)) {
            out.add(t.getAsString());
        }
        return out;
    }

    private static works.repka.ryscore.ui.Pending pending() throws ReflectiveOperationException {
        return (works.repka.ryscore.ui.Pending) Class.forName("works.repka.ryshelm.client.HelmClient").getField("PENDING").get(null);
    }

    private static int pendingInt(String key, int fallback) throws ReflectiveOperationException {
        return pending().value(key, (Integer) fallback);
    }

    private static boolean pendingBool(String key, boolean fallback) throws ReflectiveOperationException {
        return pending().value(key, (Boolean) fallback);
    }

    private static void setSetting(String name, int v) throws ReflectiveOperationException {
        net.minecraft.nbt.CompoundTag a = new net.minecraft.nbt.CompoundTag();
        a.putInt(name, v);
        Class.forName("works.repka.ryshelm.client.HelmClient").getMethod("ask", String.class, Object.class, String.class, net.minecraft.nbt.CompoundTag.class)
                .invoke(null, "s." + name, v, "settings", a);
    }

    private static void setSettingBool(String name, boolean v) throws ReflectiveOperationException {
        net.minecraft.nbt.CompoundTag a = new net.minecraft.nbt.CompoundTag();
        a.putBoolean(name, v);
        Class.forName("works.repka.ryshelm.client.HelmClient").getMethod("ask", String.class, Object.class, String.class, net.minecraft.nbt.CompoundTag.class)
                .invoke(null, "s." + name, v, "settings", a);
    }

    /** The click areas of the settings column (left of the split), in interface units, sorted from top to bottom. */
    private static int[][] settingsHits() throws ReflectiveOperationException {
        Screen s = mc().screen;
        Object hits = field(s, "hits");
        List<?> list = (List<?>) field(hits, "hits");
        int px = field(s, "px") instanceof Integer i ? i : 0;
        List<int[]> out = new ArrayList<>();
        for (Object h : list) {
            int[] r = new int[4];
            String[] names = {"x", "y", "w", "h"};
            for (int k = 0; k < 4; k++) {
                Method m = h.getClass().getDeclaredMethod(names[k]);
                m.setAccessible(true);
                r[k] = (Integer) m.invoke(h);
            }
            // the controls of a settings row sit at the right edge of the 360-wide column that starts at px + 18
            if (r[0] >= px + 18 + 360 - 80 && r[0] < px + 404) {
                out.add(r);
            }
        }
        out.sort((a, b) -> a[1] != b[1] ? Integer.compare(a[1], b[1]) : Integer.compare(a[0], b[0]));
        return out.toArray(new int[0][]);
    }

    private static List<int[]> toggles() throws ReflectiveOperationException {
        List<int[]> out = new ArrayList<>();
        for (int[] r : settingsHits()) {
            if (r[2] == 18 && r[3] == 12) {
                out.add(r);
            }
        }
        return out;
    }

    private static String describe(int[][] rects) {
        StringBuilder b = new StringBuilder();
        for (int[] r : rects) {
            b.append('[').append(r[0]).append(',').append(r[1]).append(' ').append(r[2]).append('x').append(r[3]).append(']');
        }
        return b.toString();
    }

    static Object field(Object o, String name) throws ReflectiveOperationException {
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(o);
            } catch (NoSuchFieldException e) {
                // look higher
            }
        }
        throw new NoSuchFieldException(o.getClass().getName() + "." + name);
    }

    private static void setLong(Object o, String name, long value) throws ReflectiveOperationException {
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                f.setLong(o, value);
                return;
            } catch (NoSuchFieldException e) {
                // look higher
            }
        }
        throw new NoSuchFieldException(o.getClass().getName() + "." + name);
    }

    @SuppressWarnings("unused")
    private static ResourceKey<Level> overworld() {
        return Level.OVERWORLD;
    }
}
