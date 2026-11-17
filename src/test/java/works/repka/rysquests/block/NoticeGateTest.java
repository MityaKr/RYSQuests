package works.repka.rysquests.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * "Your block stopped" (decision on question 17 of the shop plan): one notice per change of a block's state, at most one a minute per
 * owner whatever the number of blocks, nothing for a state that is gone before its notice, for a block with the switch off, for a
 * made-up stock or a block switched off by redstone, and the notice of an owner who is away waits for them.
 */
class NoticeGateTest {
    private final NoticeGate gate = new NoticeGate();
    private final UUID owner = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    private static NoticeGate.Stop stop(int x, ReceiverStatus status) {
        return new NoticeGate.Stop("dispenser", "minecraft:overworld", new BlockPos(x, 64, 0), status, Optional.of(ResourceLocation.parse("t:buy")));
    }

    private List<NoticeGate.Due> due(long now) {
        return gate.due(now, any -> true);
    }

    @Test
    void oneNoticePerChangeOfState() {
        assertTrue(gate.status(owner, stop(0, ReceiverStatus.WAITING), true));
        // The same state told again, every second while the block waits, is no new notice.
        gate.status(owner, stop(0, ReceiverStatus.WAITING), true);
        List<NoticeGate.Due> first = due(0);
        assertEquals(1, first.size());
        assertEquals(ReceiverStatus.WAITING, first.getFirst().stop().status());
        assertEquals(0, first.getFirst().more());

        gate.status(owner, stop(0, ReceiverStatus.WAITING), true);
        assertTrue(due(5000).isEmpty());
    }

    @Test
    void atMostOneNoticeAMinutePerOwner() {
        gate.status(owner, stop(0, ReceiverStatus.WAITING), true);
        gate.status(owner, stop(1, ReceiverStatus.NO_SPACE), true);
        List<NoticeGate.Due> first = due(100);
        assertEquals(1, first.size());
        // Two blocks at once are one notice: the latest leads, "and 1 more".
        assertEquals(ReceiverStatus.NO_SPACE, first.getFirst().stop().status());
        assertEquals(1, first.getFirst().more());

        gate.status(owner, stop(2, ReceiverStatus.CLOSED), true);
        assertTrue(due(200).isEmpty());
        assertTrue(due(100 + NoticeGate.OWNER_PERIOD - 1).isEmpty());
        assertEquals(1, gate.waiting(owner));
        List<NoticeGate.Due> later = due(100 + NoticeGate.OWNER_PERIOD);
        assertEquals(1, later.size());
        assertEquals(ReceiverStatus.CLOSED, later.getFirst().stop().status());

        // Another owner is not held back by the first one's minute.
        gate.status(other, stop(3, ReceiverStatus.BUDGET), true);
        assertEquals(1, due(100 + NoticeGate.OWNER_PERIOD + 20).size());
    }

    @Test
    void aStateGoneBeforeItsNoticeIsNoNotice() {
        gate.status(owner, stop(0, ReceiverStatus.WAITING), true);
        due(0);
        gate.status(owner, stop(0, ReceiverStatus.WORKING), true);
        gate.status(owner, stop(0, ReceiverStatus.NO_SPACE), true);
        gate.status(owner, stop(0, ReceiverStatus.WORKING), true);
        assertEquals(0, gate.waiting(owner));
        assertTrue(due(NoticeGate.OWNER_PERIOD * 3).isEmpty());

        // A new stop after it worked again is a new change: one more notice.
        gate.status(owner, stop(0, ReceiverStatus.LIMIT), true);
        assertEquals(1, due(NoticeGate.OWNER_PERIOD * 4).size());
    }

    @Test
    void theSwitchAMadeUpStockAndRedstoneMakeNoNotice() {
        assertFalse(gate.status(owner, stop(0, ReceiverStatus.NO_RIGHT), false));
        assertFalse(gate.status(owner, stop(1, ReceiverStatus.STOCKED), true));
        assertFalse(gate.status(owner, stop(2, ReceiverStatus.REDSTONE), true));
        assertFalse(gate.status(owner, stop(3, ReceiverStatus.NO_OFFER), true));
        assertTrue(due(0).isEmpty());

        // Switched off while its notice waits: the notice is dropped.
        gate.status(owner, stop(4, ReceiverStatus.WAITING), true);
        gate.status(owner, stop(4, ReceiverStatus.WAITING), false);
        assertTrue(due(0).isEmpty());
    }

    @Test
    void theNoticeOfAnOwnerWhoIsAwayWaitsForThem() {
        gate.status(owner, stop(0, ReceiverStatus.NO_SPACE), true);
        assertTrue(gate.due(0, any -> false).isEmpty());
        assertEquals(1, gate.waiting(owner));
        assertEquals(1, gate.due(40, Set.of(owner)::contains).size());
    }

    @Test
    void aBrokenBlockIsForgotten() {
        gate.status(owner, stop(0, ReceiverStatus.WAITING), true);
        gate.removed(owner, stop(0, ReceiverStatus.WAITING).key());
        assertTrue(due(0).isEmpty());
        // Placed again in the same spot, its first stop is news.
        assertTrue(gate.status(owner, stop(0, ReceiverStatus.WAITING), true));
    }

    @Test
    void everyYellowAndRedStateButAMadeUpStockNotifies() {
        for (ReceiverStatus status : ReceiverStatus.values()) {
            boolean expected = (status.lamp() == ReceiverLamp.YELLOW || status.lamp() == ReceiverLamp.RED) && status != ReceiverStatus.STOCKED;
            assertEquals(expected, status.notifies(), status.name());
        }
    }
}
