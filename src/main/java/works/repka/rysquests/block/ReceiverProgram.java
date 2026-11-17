package works.repka.rysquests.block;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.RYSQuests;
import works.repka.rysquests.data.ShopOffer;

/**
 * The mode of an intake block (shop plan, sections 4 and 4.7): which trades it works with, what it takes from a pipe and what it does
 * once a second. A block saves only the id of its program, so a new mode (the coming "trials") is one more registered program, with no
 * change to the block's format and no new block to place.
 */
public interface ReceiverProgram {
    ResourceLocation id();

    /** Whether this program works with a trade: the window lists only those, and a block refuses the others. */
    boolean fits(ShopOffer offer);

    /**
     * Takes what it can of a stack a pipe offers, honestly when only simulating, so that nothing a pipe is told is taken is ever lost.
     *
     * @return what is left of the stack
     */
    ItemStack insert(Receiver receiver, ReceiverMarket market, ShopOffer offer, ItemStack stack, boolean simulate);

    /** What the block does once a second after items came in: sells whole lots, or nothing. @return the lang key of a refusal, or null */
    @Nullable
    String settle(Receiver receiver, ReceiverMarket market, ShopOffer offer);

    /** Whether the trade could go on, changing nothing: what the lamp says while nothing comes in. @return a lang key, or null */
    @Nullable
    String check(Receiver receiver, ReceiverMarket market, ShopOffer offer);

    /** The id the window's third, greyed mode stands for. Not registered: a block that somehow has it does nothing. */
    ResourceLocation TRIAL = ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "trial");

    /** The programs by id, in the order of the window's switch. */
    final class Registry {
        private static final Map<ResourceLocation, ReceiverProgram> PROGRAMS = new LinkedHashMap<>();

        private Registry() {
        }

        public static <P extends ReceiverProgram> P register(P program) {
            PROGRAMS.put(program.id(), program);
            return program;
        }

        public static @Nullable ReceiverProgram get(ResourceLocation id) {
            return PROGRAMS.get(id);
        }

        public static Collection<ReceiverProgram> all() {
            return Collections.unmodifiableCollection(PROGRAMS.values());
        }
    }
}
