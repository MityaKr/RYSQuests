package works.repka.rysquests.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;

/**
 * The intake block "Приёмщик" (shop plan, section 4.1): takes items from any side and sells them by the trade chosen in its window, or
 * gives them to a delivery of the owner's team. It has no slots and no ticker: an insertion is a sum, and one scheduled tick a second
 * after items came in makes the trade. Its lamp says how it stands.
 */
public final class ReceiverBlock extends BaseEntityBlock {
    public static final MapCodec<ReceiverBlock> CODEC = simpleCodec(ReceiverBlock::new);
    public static final EnumProperty<ReceiverLamp> LAMP = EnumProperty.create("lamp", ReceiverLamp.class);

    public ReceiverBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LAMP, ReceiverLamp.OFF));
    }

    @Override
    protected MapCodec<ReceiverBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LAMP);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ReceiverBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        // A block a machine places belongs to nobody until a player opens it.
        if (placer instanceof ServerPlayer player && !(player instanceof FakePlayer) && level.getBlockEntity(pos) instanceof ReceiverBlockEntity receiver) {
            receiver.claim(player);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        // Automation goes as a fake player: it neither opens the window nor changes a setting.
        if (!(player instanceof ServerPlayer server) || player instanceof FakePlayer || !(level.getBlockEntity(pos) instanceof ReceiverBlockEntity receiver)) {
            return InteractionResult.PASS;
        }
        receiver.open(server);
        return InteractionResult.CONSUME;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof ReceiverBlockEntity receiver) {
            receiver.settle();
        }
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /** The lamp or the buffer's fill, as the window's setting says. */
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ReceiverBlockEntity receiver ? receiver.signal() : 0;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ReceiverBlockEntity receiver) {
            receiver.broken();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
