package works.repka.rysquests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** Placeholder proving that JUnit runs with Minecraft classes on the test classpath. */
class ModSetupTest {
    @Test
    void resourceLocationCodecRoundTrips() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "power/electric_mechanism");

        var json = ResourceLocation.CODEC.encodeStart(JsonOps.INSTANCE, id).getOrThrow();
        var decoded = ResourceLocation.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();

        assertEquals(id, decoded);
    }
}
