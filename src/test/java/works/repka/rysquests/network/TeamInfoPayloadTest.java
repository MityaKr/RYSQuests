package works.repka.rysquests.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

/** The team's size and name for "Сейчас" cross the wire as they are; a very long name is cut to the room of the line. */
class TeamInfoPayloadTest {
    @Test
    void sizeAndNameCrossTheWire() {
        ByteBuf buf = Unpooled.buffer();
        TeamInfoPayload.STREAM_CODEC.encode(buf, new TeamInfoPayload(3, "Репка"));
        assertEquals(new TeamInfoPayload(3, "Репка"), TeamInfoPayload.STREAM_CODEC.decode(buf));
    }

    @Test
    void aLongNameIsCut() {
        assertEquals(TeamInfoPayload.MAX_NAME, new TeamInfoPayload(2, "x".repeat(200)).name().length());
        assertEquals("", new TeamInfoPayload(1, "").name());
    }
}
