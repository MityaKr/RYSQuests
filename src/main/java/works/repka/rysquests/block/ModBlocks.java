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
 * The mod's blocks (shop plan, step M6): the intake block "Приёмщик", its item, block entity and window. No recipe here: a pack gives
 * one (RepYS, step Г9); without it the block comes from the creative inventory or {@code /give}.
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
    }

    /** Pipes, hoppers and networks see an inventory that only takes (shop plan, section 4.1), from every side. */
    private static void onCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, RECEIVER_ENTITY.get(), (entity, side) -> entity.handler());
    }

    private static void onCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(RECEIVER_ITEM);
        }
    }

    /** Only the owner, the owner's team and an operator break the block (shop plan, section 4.4). */
    private static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getState().is(RECEIVER.get()) && event.getPlayer() instanceof ServerPlayer player
                && event.getLevel().getBlockEntity(event.getPos()) instanceof ReceiverBlockEntity receiver && !receiver.mayBreak(player)) {
            event.setCanceled(true);
            player.displayClientMessage(receiver.foreignMessage(), true);
        }
    }
}
