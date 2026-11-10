package works.repka.rysquests.client.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import vazkii.patchouli.api.PatchouliAPI;
import works.repka.rysquests.data.StructureDef;

/** The only class that touches Patchouli. Reached through {@link Multiblocks}, so it never loads without the mod. */
final class PatchouliBridge {
    private PatchouliBridge() {
    }

    /** A hologram on the block the player looks at, or at their feet. */
    static void show(StructureDef structure, Component name) {
        Minecraft mc = Minecraft.getInstance();
        PatchouliAPI.IPatchouliAPI api = PatchouliAPI.get();
        List<Object> targets = new ArrayList<>();
        for (Map.Entry<String, String> symbol : structure.mapping().entrySet()) {
            BlockState state = Multiblocks.state(symbol.getValue());
            if (state != null) {
                targets.add(symbol.getKey().charAt(0));
                targets.add(api.displayOnlyMatcher(state));
            }
        }
        String[][] pattern = structure.pattern().stream().map(layer -> layer.toArray(String[]::new)).toArray(String[][]::new);
        BlockPos at = mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos().above() : mc.player.blockPosition();
        api.showMultiblock(api.makeMultiblock(pattern, targets.toArray()), name, at, Rotation.NONE);
    }
}
