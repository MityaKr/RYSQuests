package works.repka.rysquests.client.integration;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.data.StructureDef;

/** Blocks of a structure, and its hologram in the world, which only Patchouli can draw. Safe to call without it. */
public final class Multiblocks {
    private static final String MOD = "patchouli";

    private Multiblocks() {
    }

    /** @param spec a block as commands and Patchouli write it, with properties, or a tag, of which the first block stands for all */
    @Nullable
    public static BlockState state(String spec) {
        try {
            return BlockStateParser.parseForTesting(BuiltInRegistries.BLOCK.asLookup(), spec, true).map(
                    BlockStateParser.BlockResult::blockState,
                    tag -> tag.tag().stream().findFirst().map(Holder::value).map(Block::defaultBlockState).orElse(null));
        } catch (CommandSyntaxException e) {
            return null;
        }
    }

    public static boolean canShow(StructureDef structure) {
        return ModList.get().isLoaded(MOD) && structure.hasCenter();
    }

    public static void show(StructureDef structure, Component name) {
        if (canShow(structure)) {
            PatchouliBridge.show(structure, name);
        }
    }
}
