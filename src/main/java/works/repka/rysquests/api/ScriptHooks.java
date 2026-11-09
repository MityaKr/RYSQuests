package works.repka.rysquests.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** How a scripting mod answers for script tasks and rewards. Without one, they do nothing. */
public final class ScriptHooks {
    public interface Handler {
        /** @return the task's progress for this player */
        int taskProgress(ServerPlayer player, ResourceLocation script, int current);

        void reward(ServerPlayer player, ResourceLocation script);
    }

    private static final Handler NONE = new Handler() {
        @Override
        public int taskProgress(ServerPlayer player, ResourceLocation script, int current) {
            return current;
        }

        @Override
        public void reward(ServerPlayer player, ResourceLocation script) {
        }
    };
    private static volatile Handler handler = NONE;

    private ScriptHooks() {
    }

    public static void set(Handler newHandler) {
        handler = newHandler;
    }

    public static Handler get() {
        return handler;
    }
}
