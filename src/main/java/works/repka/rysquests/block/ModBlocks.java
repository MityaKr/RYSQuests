package works.repka.rysquests.block;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import works.repka.rysquests.RYSQuests;

/**
 * The mod's blocks (shop plan, steps M6 and M7): the intake block "Приёмщик" and the dispenser "Выдача", their items, block entities
 * and windows. No recipes here: a pack gives them (RepYS, step Г9); without them the blocks come from the creative inventory or
 * {@code /give}.
 */
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(RYSQuests.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RYSQuests.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, RYSQuests.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, RYSQuests.MODID);

    public static final DeferredBlock<ReceiverBlock> RECEIVER = BLOCKS.registerBlock("shop_receiver", ReceiverBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f, 1200f).sound(SoundType.METAL));
    public static final DeferredItem<BlockItem> RECEIVER_ITEM = ITEMS.registerSimpleBlockItem(RECEIVER);
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ReceiverBlockEntity>> RECEIVER_ENTITY = BLOCK_ENTITIES.register("shop_receiver",
            () -> BlockEntityType.Builder.of(ReceiverBlockEntity::new, RECEIVER.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<ReceiverMenu>> RECEIVER_MENU = MENUS.register("shop_receiver",
            () -> IMenuTypeExtension.create(ReceiverMenu::new));

    public static final DeferredBlock<DispenserBlock> DISPENSER = BLOCKS.registerBlock("shop_dispenser", DispenserBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f, 1200f).sound(SoundType.METAL));
    public static final DeferredItem<BlockItem> DISPENSER_ITEM = ITEMS.registerSimpleBlockItem(DISPENSER);
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DispenserBlockEntity>> DISPENSER_ENTITY = BLOCK_ENTITIES.register("shop_dispenser",
            () -> BlockEntityType.Builder.of(DispenserBlockEntity::new, DISPENSER.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<DispenserMenu>> DISPENSER_MENU = MENUS.register("shop_dispenser",
            () -> IMenuTypeExtension.create(DispenserMenu::new));

    private ModBlocks() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        modBus.addListener(ModBlocks::onCapabilities);
        modBus.addListener(ModBlocks::onCreativeTabs);
        NeoForge.EVENT_BUS.addListener(ModBlocks::onBreak);
        NeoForge.EVENT_BUS.addListener(BlockNotices::onServerTick);
        NeoForge.EVENT_BUS.addListener(BlockNotices::onServerStopped);
    }

    /**
     * Pipes, hoppers and networks see the intake block as an inventory that only takes (shop plan, section 4.1), and the dispenser's own
     * slot as one that only gives (section 4.2), from every side.
     */
    private static void onCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, RECEIVER_ENTITY.get(), (entity, side) -> entity.handler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, DISPENSER_ENTITY.get(), (entity, side) -> entity.handler());
    }

    private static void onCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(RECEIVER_ITEM);
            event.accept(DISPENSER_ITEM);
        }
    }

    /** Only the owner, the owner's team and an operator break a shop block (shop plan, section 4.4). */
    private static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getState().is(RECEIVER.get()) && event.getLevel().getBlockEntity(event.getPos()) instanceof ReceiverBlockEntity receiver && !receiver.mayBreak(player)) {
            event.setCanceled(true);
            player.displayClientMessage(receiver.foreignMessage(), true);
        } else if (event.getState().is(DISPENSER.get()) && event.getLevel().getBlockEntity(event.getPos()) instanceof DispenserBlockEntity dispenser
                && !dispenser.mayBreak(player)) {
            event.setCanceled(true);
            player.displayClientMessage(dispenser.foreignMessage(), true);
        }
    }
}
