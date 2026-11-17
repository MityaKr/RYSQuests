package works.repka.rysquests.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;

/**
 * The dispenser "Выдача" (shop plan, section 4.2): buys the trade chosen in its window for the owner's coins or the team's treasury and
 * puts the goods into the inventory on its output side, or into its own slot for pipes to take. Once a second, offset by its position
 * so that a hundred blocks do not all count in one tick. Its lamp and a comparator say how it stands.
 */
public final class DispenserBlock extends BaseEntityBlock {
    public static final MapCodec<DispenserBlock> CODEC = simpleCodec(DispenserBlock::new);

    public DispenserBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ReceiverBlock.LAMP, ReceiverLamp.OFF));
    }

    @Override
    protected MapCodec<DispenserBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ReceiverBlock.LAMP);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DispenserBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlocks.DISPENSER_ENTITY.get(), (lvl, pos, st, entity) -> entity.tick());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        // A block a machine places belongs to nobody until a player opens it.
        if (placer instanceof ServerPlayer player && !(player instanceof FakePlayer) && level.getBlockEntity(pos) instanceof DispenserBlockEntity dispenser) {
            dispenser.claim(player);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        // Automation goes as a fake player: it neither opens the window nor changes a setting.
        if (!(player instanceof ServerPlayer server) || player instanceof FakePlayer || !(level.getBlockEntity(pos) instanceof DispenserBlockEntity dispenser)) {
            return InteractionResult.PASS;
        }
        dispenser.open(server);
        return InteractionResult.CONSUME;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /** The lamp or the fill of the own slot, as the window's setting says. */
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof DispenserBlockEntity dispenser ? dispenser.dispenser().signal() : 0;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof DispenserBlockEntity dispenser) {
            dispenser.broken();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
