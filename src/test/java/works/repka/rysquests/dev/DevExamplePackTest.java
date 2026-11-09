package works.repka.rysquests.dev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class DevExamplePackTest {
    private static final Path EXAMPLE_PACK =
            Path.of(System.getProperty("rysquests.examplesDir"), "example_pack");

    @BeforeAll
    static void detectGameVersion() {
        SharedConstants.tryDetectVersion();
    }

    @ParameterizedTest
    @EnumSource(PackType.class)
    void examplePackIsCompatibleWithBothPackTypes(PackType type) {
        Pack pack = DevExamplePack.create(EXAMPLE_PACK, type);

        assertNotNull(pack, "pack.mcmeta must be readable");
        assertEquals(PackCompatibility.COMPATIBLE, pack.getCompatibility());
    }

    @ParameterizedTest
    @CsvSource({
        "SERVER_DATA, rysquests/book.json",
        "CLIENT_RESOURCES, lang/en_us.json"
    })
    void repositorySelectsRequiredPackAndServesItsFiles(PackType type, String path) {
        Pack pack = DevExamplePack.create(EXAMPLE_PACK, type);
        assertNotNull(pack);

        PackRepository repository = new PackRepository(consumer -> consumer.accept(pack));
        repository.reload();

        assertTrue(repository.getSelectedPacks().contains(pack), "required pack must be selected");
        try (PackResources resources = pack.open()) {
            assertNotNull(resources.getResource(type, ResourceLocation.fromNamespaceAndPath("example", path)));
        }
    }
}
