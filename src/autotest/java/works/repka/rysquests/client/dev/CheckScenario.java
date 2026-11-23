package works.repka.rysquests.client.dev;

import static works.repka.rysquests.client.dev.AutoTest.chatLines;
import static works.repka.rysquests.client.dev.AutoTest.command;
import static works.repka.rysquests.client.dev.AutoTest.expect;
import static works.repka.rysquests.client.dev.AutoTest.mc;
import static works.repka.rysquests.client.dev.AutoTest.step;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import works.repka.ryscore.action.Actions;
import works.repka.ryscore.dock.DockButton;
import works.repka.ryscore.dock.DockButtons;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.screen.BookScreen;
import works.repka.rysquests.client.screen.RepysHooks;
import works.repka.rysquests.data.Achievement;
import works.repka.rysquests.data.Quest;

/**
 * {@code check}: the in-game pass over a deployment of RepYS on the test copy of the server (localhost:25566), as the list
 * {@code _shared/_audit/CHECK-*.md} lays it out. The client of run-repys joins as Dev (an operator there), looks at the family's
 * windows, the book, the recipe viewer, item names, the apiary limit, the dummy, commands and a foreign claim, then leaves.
 * Every finding is a {@code [check]} log line; screenshots are named {@code check_<lang>_<nn>_<name>.png}. The game language of
 * the run decides the language of the pass, so the same scenario runs once in Russian and once in English.
 * {@code -Dryscheck.stop=true} stops the test server at the end (the last run).
 */
final class CheckScenario {
    private static final Logger LOGGER = LoggerFactory.getLogger(CheckScenario.class);
    private static final String SERVER = "localhost:25566";
    /** A spot in the air outside every claim of the test world, near the claim of Chuzhoy_Test. */
    private static final BlockPos PAD_AIR = new BlockPos(5824, 200, 26260);
    /** Where the player stands after the fall from {@link #PAD_AIR}: the floor of the test pad is under it. */
    private static BlockPos pad = PAD_AIR;
    private static final BlockPos FOREIGN = new BlockPos(5824, 98, 26097);
    private static final String[] OURS = {"ryscore", "rysindex", "rysclaims", "rysmap", "rysteams", "rysquests", "ryshelm", "ryscope"};
    private static final String[][] QUESTS = {
            {"mill", "repys:b12_e5_garden/essence_mill"},
            {"millstone_master", "repys:b16_e7_plasma/millstone_master"},
            {"oil_scanner", "repys:b04_e2_oil/oil_scanner"},
            {"overclock_iii", "repys:b21_e8_stellar/overclock_module_iii"}};
    private static final String[] ACHIEVEMENTS = {"repys:explorer_the_nether", "repys:explorer_the_end", "repys:explorer_mining_dimension",
            "repys:explorer_the_beyond", "repys:explorer_the_other"};
    private static final Object[][] GUIDES = {{"mystical", 3}, {"create", 8}, {"oritech", 4}, {"oritech", 12}, {"oritech", 17}};
    /** Recipe viewer pages: item, mode (r = recipes, u = uses). */
    private static final String[][] VIEWS = {
            {"mmr:repys:essence_mill", "u"}, {"kubejs:quintessence", "u"}, {"kubejs:millstone_stone", "r"}, {"kubejs:millstone_master", "r"},
            {"mysticalagriculture:iron_essence", "u"}, {"oritechaddonsone:extension_addon_1", "r"}, {"oritechaddonsone:extension_addon_3", "r"},
            {"applied_oritech:me_dock", "r"}, {"productivelib:upgrade_productivity_4", "r"}, {"draconicevolution:draconium_core", "r"},
            {"draconicevolution:awakened_draconium_ingot", "r"}, {"dummmmmmy:target_dummy", "r"}};
    private static final String[] NAMED = {"kubejs:quintessence", "kubejs:vital_essence", "kubejs:dread_essence", "kubejs:deep_essence",
            "kubejs:ether_essence", "kubejs:millstone_stone", "kubejs:millstone_metal", "kubejs:millstone_gem", "kubejs:millstone_life",
            "kubejs:millstone_soul", "kubejs:millstone_charm", "kubejs:millstone_master", "oritechaddonsone:extension_addon_1",
            "oritechaddonsone:extension_addon_2", "oritechaddonsone:extension_addon_3", "oritechaddonsone:wireless_extension_addon_1",
            "oritechaddonsone:transfer_addon", "oritechaddonsone:warehouse_addon", "oritechaddonsone:tank_addon",
            "oritechaddonsone:chunk_anchor_addon", "dummmmmmy:target_dummy", "productivelib:upgrade_productivity_4", "applied_oritech:me_dock",
            "draconicevolution:draconium_core"};

    private static String lang = "ru";
    private static int shots = 1;
    private static int waited;
    private static BlockState foreignWas;
    private static BlockPos foreignPos;

    private CheckScenario() {
    }

    static void build() {
        boolean stop = Boolean.getBoolean("ryscheck.stop");
        step("language", () -> {
            lang = mc().options.languageCode.startsWith("ru") ? "ru" : "en";
            log("lang", "OK", mc().options.languageCode);
        }, 1);
        join();
        softWait("index", () -> indexReady(), 20 * 240);
        step("versions", () -> {
            StringBuilder out = new StringBuilder();
            for (String id : OURS) {
                out.append(id).append('=').append(net.neoforged.fml.ModList.get().getModContainerById(id)
                        .map(c -> c.getModInfo().getVersion().toString()).orElse("-")).append(' ');
            }
            log("1.2 versions", "INFO", out.toString().trim());
        }, 1);
        step("pad", () -> {
            command("gamemode creative");
            command("time set noon");
            command("weather clear");
            command(String.format(Locale.ROOT, "tp @s %d %d %d 180 20", PAD_AIR.getX(), PAD_AIR.getY(), PAD_AIR.getZ()));
        }, 160);
        step("pad floor", () -> {
            pad = mc().player.blockPosition();
            log("pad", "INFO", pad.toShortString());
            command(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:air", pad.getX() - 4, pad.getY(), pad.getZ() - 4,
                    pad.getX() + 4, pad.getY() + 4, pad.getZ() + 4));
            command(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:glass", pad.getX() - 4, pad.getY() - 1, pad.getZ() - 4,
                    pad.getX() + 4, pad.getY() - 1, pad.getZ() + 4));
            command(String.format(Locale.ROOT, "tp @s %d %d %d 180 20", pad.getX(), pad.getY(), pad.getZ()));
        }, 40);
        shot("hud");

        // --- 1. family windows from the panel of buttons, the keys window and the wheel ---
        step("inventory", () -> mc().setScreen(new InventoryScreen(mc().player)), 30);
        shot("dock");
        for (int i = 0; i < 8; i++) {
            final int n = i;
            step("dock button " + n, () -> {
                List<DockButton> buttons = DockButtons.all().stream().filter(b -> b.visible().getAsBoolean()).toList();
                if (n >= buttons.size()) {
                    return;
                }
                DockButton b = buttons.get(n);
                log("dock", "INFO", n + " " + b.id() + " \"" + b.label().get().getString() + "\"");
                mc().setScreen(null);
                b.action().run();
            }, 60);
            shot("dock_" + n);
            step("close", () -> mc().setScreen(null), 10);
        }
        step("keys window", () -> Actions.openKeys(null), 40);
        shot("keys");
        step("close", () -> mc().setScreen(null), 10);
        step("wheel", Actions::openWheel, 30);
        shot("wheel");
        step("close", () -> mc().setScreen(null), 10);

        // --- 2. the book ---
        for (String[] q : QUESTS) {
            step("quest " + q[0], () -> {
                ResourceLocation id = ResourceLocation.parse(q[1]);
                Quest quest = ClientBook.current().quests().get(id);
                if (quest == null) {
                    log("2 quest " + q[0], "FAIL", "no quest " + id);
                    return;
                }
                log("2 quest " + q[0], "INFO", I18n.get(quest.title()) + " | " + quest.text().map(I18n::get).orElse("<no text>").replace('\n', ' '));
                BookScreen.openAt(id);
            }, 60);
            shot("quest_" + q[0]);
        }
        step("achievements", () -> {
            for (String a : ACHIEVEMENTS) {
                Achievement achievement = ClientBook.current().achievements().get(ResourceLocation.parse(a));
                if (achievement == null) {
                    log("2.4 " + a, "FAIL", "no achievement");
                    continue;
                }
                log("2.4 " + a, "INFO", achievement.title().map(I18n::get).orElse("-") + " | "
                        + achievement.text().map(I18n::get).orElse("-").replace('\n', ' '));
            }
            BookScreen.open();
        }, 60);
        step("achievement panel", () -> RepysHooks.achievements(ResourceLocation.parse(ACHIEVEMENTS[0])), 40);
        shot("achievement");
        for (Object[] g : GUIDES) {
            step("guide " + g[0] + " " + g[1], () -> {
                if (!(mc().screen instanceof BookScreen)) {
                    BookScreen.open();
                }
                if (!RepysHooks.guide(ResourceLocation.parse("repys:" + g[0]), (Integer) g[1])) {
                    log("2 guide " + g[0], "FAIL", "no guide");
                }
            }, 50);
            shot("guide_" + g[0] + "_" + g[1]);
        }
        step("close", () -> mc().setScreen(null), 10);

        // --- 3. recipe viewer and data from the server ---
        for (String[] v : VIEWS) {
            step("recipes " + v[0], () -> openRecipes(v[0], "u".equals(v[1])), 60);
            shot("rv_" + v[0].replaceAll("[^a-z0-9_]", "_"));
            step("close", () -> mc().setScreen(null), 10);
        }
        step("recipe data", CheckScenario::recipeData, 1);
        step("names", CheckScenario::names, 1);

        // --- 4. apiary: one productivity upgrade per controller ---
        step("apiary place", () -> {
            command("clear @s");
            command(String.format(Locale.ROOT, "setblock %d %d %d mystianapiary:upgrade_block", pad.getX() + 2, pad.getY(), pad.getZ() - 2));
            command("give @s productivelib:upgrade_productivity_4 2");
        }, 40);
        step("apiary stack", () -> {
            Item omega = item("productivelib:upgrade_productivity_4");
            int stacks = 0;
            int total = 0;
            for (ItemStack s : mc().player.getInventory().items) {
                if (s.is(omega)) {
                    stacks++;
                    total += s.getCount();
                }
            }
            log("3.1 omega stack", stacks == 2 && total == 2 ? "OK" : "FAIL", "stacks " + stacks + ", total " + total
                    + ", max stack " + new ItemStack(omega).getMaxStackSize());
            ItemStack shown = new ItemStack(omega);
            log("3.1 omega tooltip", "INFO", String.join(" / ", shown.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(mc().level),
                    mc().player, net.minecraft.world.item.TooltipFlag.Default.NORMAL).stream().map(c -> c.getString()).toList()));
        }, 2);
        step("apiary open", () -> use(new BlockPos(pad.getX() + 2, pad.getY(), pad.getZ() - 2)), 40);
        step("apiary insert 1", () -> insertOmega(), 10);
        step("apiary insert 2", () -> insertOmega(), 40);
        shot("apiary");
        step("apiary result", () -> {
            Item omega = item("productivelib:upgrade_productivity_4");
            int inBlock = 0;
            int inPlayer = 0;
            for (Slot s : mc().player.containerMenu.slots) {
                if (s.getItem().is(omega)) {
                    if (s.container instanceof Inventory) {
                        inPlayer += s.getItem().getCount();
                    } else {
                        inBlock += s.getItem().getCount();
                    }
                }
            }
            inPlayer += mc().player.containerMenu.getCarried().is(omega) ? mc().player.containerMenu.getCarried().getCount() : 0;
            log("3.2 apiary limit", inBlock == 1 ? "OK" : "FAIL", "in controller " + inBlock + ", with player " + inPlayer
                    + ", screen " + (mc().screen == null ? "-" : mc().screen.getClass().getSimpleName()));
            mc().setScreen(null);
        }, 20);
        commandShot("clear @s productivelib:upgrade_productivity_4 0", "3.2 apiary player count");
        step("apiary data", () -> {}, 1);
        commandShotDyn(() -> String.format(Locale.ROOT, "data get block %d %d %d", pad.getX() + 2, pad.getY(), pad.getZ() - 2), "3.2 apiary block data");

        // --- 5. dummy ---
        step("dummy give", () -> {
            command("clear @s");
            command("give @s dummmmmmy:target_dummy");
        }, 20);
        step("dummy place", () -> {
            mc().player.getInventory().selected = 0;
            for (int i = 0; i < 9; i++) {
                if (mc().player.getInventory().getItem(i).is(item("dummmmmmy:target_dummy"))) {
                    mc().player.getInventory().selected = i;
                }
            }
            BlockPos on = new BlockPos(pad.getX() - 2, pad.getY() - 1, pad.getZ() - 2);
            mc().gameMode.useItemOn(mc().player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(on).add(0, 0.5, 0), Direction.UP, on, false));
        }, 40);
        step("dummy hit", () -> {
            Entity dummy = dummy();
            log("3.5 dummy placed", dummy != null ? "OK" : "FAIL", dummy == null ? "no entity" : dummy.getType().toString());
            if (dummy != null) {
                mc().gameMode.attack(mc().player, dummy);
            }
        }, 30);
        shot("dummy");

        // --- 6. commands ---
        commandShot("rys migrate status", "6.7 migrate");
        commandShot("rys admin kits create check1123 once", "7.3 kit create");
        commandShot("rys kit check1123", "7.3 kit first");
        commandShot("rys kit check1123", "7.3 kit second");
        commandShot("rys admin journal 10", "7.4 journal");
        commandShot("sc check1123 test message", "7.5 sc");

        // --- 7. a foreign claim ---
        step("foreign tp", () -> {
            command("gamemode creative");
            command(String.format(Locale.ROOT, "tp @s %d %d %d", FOREIGN.getX(), FOREIGN.getY() + 3, FOREIGN.getZ()));
        }, 80);
        step("foreign break", () -> {
            int top = mc().level.getHeight(Heightmap.Types.WORLD_SURFACE, FOREIGN.getX() + 2, FOREIGN.getZ() + 2) - 1;
            foreignPos = new BlockPos(FOREIGN.getX() + 2, top, FOREIGN.getZ() + 2);
            foreignWas = mc().level.getBlockState(foreignPos);
            log("7.6 foreign block", "INFO", foreignPos.toShortString() + " " + foreignWas);
            mc().gameMode.startDestroyBlock(foreignPos, Direction.UP);
        }, 40);
        step("foreign kept", () -> {
            BlockState now = mc().level.getBlockState(foreignPos);
            log("7.6 foreign protected", now.equals(foreignWas) && !now.isAir() ? "OK" : "FAIL", "now " + now);
            command("claims admin bypass");
        }, 40);
        chatShot("claims_bypass_on");
        step("foreign break bypass", () -> mc().gameMode.startDestroyBlock(foreignPos, Direction.UP), 40);
        step("foreign bypass result", () -> {
            BlockState now = mc().level.getBlockState(foreignPos);
            log("7.6 bypass breaks", now.isAir() ? "OK" : "FAIL", "now " + now);
            command(String.format(Locale.ROOT, "setblock %d %d %d %s", foreignPos.getX(), foreignPos.getY(), foreignPos.getZ(),
                    BuiltInRegistries.BLOCK.getKey(foreignWas.getBlock())));
            command("claims admin bypass");
        }, 40);
        chatShot("claims_bypass_off");
        step("survival", () -> command("gamemode survival"), 10);
        if (stop) {
            step("stop the test server", () -> command("stop"), 100);
        }
    }

    private static void join() {
        expect("title", () -> mc().screen != null && mc().level == null && mc().getOverlay() == null);
        step("connect", () -> {
            mc().options.pauseOnLostFocus = false;
            mc().options.onboardAccessibility = false;
            if (!(mc().screen instanceof TitleScreen)) {
                mc().setScreen(new TitleScreen());
            }
            ConnectScreen.startConnecting(mc().screen, mc(), ServerAddress.parseString(SERVER), new ServerData("test", SERVER, ServerData.Type.OTHER), false, null);
        }, 20);
        expect("joined", () -> mc().player != null && mc().level != null && mc().screen == null);
        step("joined", () -> log("1.1 join", "OK", mc().player.getGameProfile().getName()), 200);
    }

    /** Waits for the condition, but never longer than {@code limit} ticks: a slow index must not end the pass. */
    private static void softWait(String name, BooleanSupplier condition, int limit) {
        step("wait " + name, () -> waited = 0, 1);
        expect("wait " + name, () -> condition.getAsBoolean() || ++waited > limit);
        step("waited " + name, () -> log("wait " + name, condition.getAsBoolean() ? "OK" : "FAIL", waited + " ticks"), 40);
    }

    private static boolean indexReady() {
        try {
            Class<?> indexer = Class.forName("works.repka.rysindex.runtime.Indexer");
            return "READY".equals(String.valueOf(indexer.getMethod("state").invoke(null)));
        } catch (ReflectiveOperationException e) {
            return true;
        }
    }

    private static void openRecipes(String id, boolean uses) {
        try {
            ItemStack stack;
            if (id.startsWith("mmr:")) {
                stack = (ItemStack) Class.forName("es.degrassi.mmreborn.common.item.ControllerItem")
                        .getMethod("makeMachineItem", ResourceLocation.class).invoke(null, ResourceLocation.parse(id.substring(4)));
            } else {
                stack = new ItemStack(item(id));
            }
            Class<?> stackClass = Class.forName("works.repka.rysindex.model.Stack");
            Object res = Class.forName("works.repka.rysindex.model.ItemRes").getConstructor(ItemStack.class).newInstance(stack);
            Class<?> mode = Class.forName("works.repka.rysindex.runtime.Lookup$Mode");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object m = Enum.valueOf((Class<Enum>) mode, uses ? "USES" : "RECIPES");
            Object opened = Class.forName("works.repka.rysindex.ui.RecipeScreen").getMethod("open", stackClass, mode).invoke(null, res, m);
            log("3 recipes " + id, Boolean.TRUE.equals(opened) ? "OK" : "FAIL", (uses ? "uses" : "recipes") + " of "
                    + stack.getHoverName().getString() + ", opened " + opened);
        } catch (ReflectiveOperationException | RuntimeException e) {
            log("3 recipes " + id, "FAIL", e.toString());
        }
    }

    /** What the client got from the server: the mill, the twins, the essences without a workbench recipe. */
    private static void recipeData() {
        var manager = mc().getConnection().getRecipeManager();
        int mill = 0;
        int all = 0;
        List<String> essenceCrafts = new ArrayList<>();
        Item[] watched = {item("mysticalagriculture:iron_essence"), item("mysticalagriculture:diamond_essence"),
                item("mysticalagriculture:redstone_essence"), item("mysticalagriculture:zombie_essence")};
        for (RecipeHolder<?> h : manager.getRecipes()) {
            all++;
            String id = h.id().toString();
            if (id.startsWith("kubejs:mmr/essence_mill/")) {
                mill++;
            }
            if (h.value().getType() == RecipeType.CRAFTING) {
                for (var ing : h.value().getIngredients()) {
                    for (Item w : watched) {
                        if (!ing.isEmpty() && ing.test(new ItemStack(w))) {
                            essenceCrafts.add(id + " <- " + BuiltInRegistries.ITEM.getKey(w).getPath());
                        }
                    }
                }
            }
        }
        log("4.1 mill recipes", mill >= 264 ? "OK" : "FAIL", mill + " of " + all + " recipes on the client");
        // a crafting recipe that only turns 8 essences into the resource would show here; mixed ones (with other items) are fine
        log("4.5 essence crafting", "INFO", essenceCrafts.size() + ": " + String.join(", ", essenceCrafts.stream().limit(20).toList()));
    }

    /** Item names in the game language: no raw keys anywhere in the registry, and the new items by name. */
    private static void names() {
        List<String> raw = new ArrayList<>();
        int items = 0;
        for (Item it : BuiltInRegistries.ITEM) {
            items++;
            ItemStack s = new ItemStack(it);
            String name = s.getHoverName().getString();
            if (name.equals(it.getDescriptionId()) || name.matches("(item|block)\\.[a-z0-9_]+\\.[a-z0-9_.]+")) {
                raw.add(BuiltInRegistries.ITEM.getKey(it).toString());
            }
        }
        log("8.4 raw item names", raw.isEmpty() ? "OK" : "INFO", raw.size() + " of " + items + ": " + String.join(", ", raw.stream().limit(60).toList()));
        StringBuilder out = new StringBuilder();
        for (String id : NAMED) {
            Item it = item(id);
            out.append(id).append(" = \"").append(it == net.minecraft.world.item.Items.AIR ? "<missing>" : new ItemStack(it).getHoverName().getString()).append("\"; ");
        }
        log("3.3 names", "INFO", out.toString());
    }

    private static void use(BlockPos pos) {
        mc().gameMode.useItemOn(mc().player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    /** Picks one omega from the player's slots and drops it into the controller slot that accepts it, as a click would. */
    private static void insertOmega() {
        Item omega = item("productivelib:upgrade_productivity_4");
        var menu = mc().player.containerMenu;
        Slot from = null;
        Slot to = null;
        for (Slot s : menu.slots) {
            if (from == null && s.container instanceof Inventory && s.getItem().is(omega)) {
                from = s;
            }
            if (to == null && !(s.container instanceof Inventory) && s.mayPlace(new ItemStack(omega))) {
                to = s;
            }
        }
        if (from == null || to == null) {
            log("3.2 apiary click", "FAIL", "from " + from + ", to " + to + ", menu " + menu.getClass().getSimpleName());
            return;
        }
        mc().gameMode.handleInventoryMouseClick(menu.containerId, from.index, 0, ClickType.PICKUP, mc().player);
        mc().gameMode.handleInventoryMouseClick(menu.containerId, to.index, 0, ClickType.PICKUP, mc().player);
        log("3.2 apiary click", "INFO", "slot " + from.index + " -> " + to.index);
    }

    private static Entity dummy() {
        AABB box = new AABB(pad).inflate(8);
        for (Entity e : mc().level.getEntities((Entity) null, box, e -> true)) {
            if (BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace().equals("dummmmmmy")) {
                return e;
            }
        }
        return null;
    }

    private static void commandShot(String cmd, String check) {
        final int[] before = {0};
        step("command " + cmd, () -> {
            before[0] = chatLines().size();
            mc().gui.getChat().clearMessages(false);
            command(cmd);
        }, 40);
        step("answer " + cmd, () -> {
            List<String> lines = chatLines();
            List<String> answer = lines.subList(Math.min(before[0], lines.size()), lines.size());
            log(check, answer.isEmpty() ? "FAIL" : "INFO", "/" + cmd + " -> " + String.join(" || ", answer).replace('\n', ' '));
            mc().setScreen(new net.minecraft.client.gui.screens.ChatScreen(""));
        }, 10);
        shotRaw(check.replaceAll("[^a-z0-9]", "_"));
        step("close", () -> mc().setScreen(null), 5);
    }

    private static void commandShotDyn(java.util.function.Supplier<String> cmd, String check) {
        final int[] before = {0};
        final String[] sent = {""};
        step("command " + check, () -> {
            before[0] = chatLines().size();
            sent[0] = cmd.get();
            command(sent[0]);
        }, 40);
        step("answer " + check, () -> {
            List<String> lines = chatLines();
            List<String> answer = lines.subList(Math.min(before[0], lines.size()), lines.size());
            log(check, answer.isEmpty() ? "FAIL" : "INFO", "/" + sent[0] + " -> " + String.join(" || ", answer).replace('\n', ' '));
        }, 5);
    }

    private static void chatShot(String name) {
        step("chat " + name, () -> mc().setScreen(new net.minecraft.client.gui.screens.ChatScreen("")), 10);
        shotRaw(name);
        step("close", () -> mc().setScreen(null), 5);
    }

    private static void shot(String name) {
        step("clean " + name, () -> mc().getToasts().clear(), 2);
        shotRaw(name);
    }

    private static void shotRaw(String name) {
        step("shot " + name, () -> {
            String file = String.format(Locale.ROOT, "check_%s_%02d_%s.png", lang, shots++, name);
            Screenshot.grab(mc().gameDirectory, file, mc().getMainRenderTarget(), message -> {});
        }, 5);
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    }

    private static void log(String check, String verdict, String detail) {
        LOGGER.info("[check] {} {}: {}", verdict, check, detail);
    }
}
