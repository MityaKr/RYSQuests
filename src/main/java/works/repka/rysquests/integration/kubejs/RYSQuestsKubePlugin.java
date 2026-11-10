package works.repka.rysquests.integration.kubejs;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.event.EventTargetType;
import dev.latvian.mods.kubejs.event.TargetedEventHandler;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import works.repka.rysquests.api.QuestCompletedEvent;
import works.repka.rysquests.api.ScriptHooks;
import works.repka.rysquests.api.WalletApi;

/** {@code RYSQuestsEvents} for server scripts. Named in {@code kubejs.plugins.txt}, so it loads only with KubeJS. */
public final class RYSQuestsKubePlugin implements KubeJSPlugin {
    private static final EventGroup GROUP = EventGroup.of("RYSQuestsEvents");
    private static final TargetedEventHandler<ResourceLocation> COMPLETED =
            GROUP.server("completed", () -> QuestCompletedKubeEvent.class).supportsTarget(EventTargetType.ID);
    private static final TargetedEventHandler<ResourceLocation> TASK =
            GROUP.server("task", () -> ScriptTaskKubeEvent.class).requiredTarget(EventTargetType.ID);
    private static final TargetedEventHandler<ResourceLocation> REWARD =
            GROUP.server("reward", () -> ScriptRewardKubeEvent.class).requiredTarget(EventTargetType.ID);

    @Override
    public void init() {
        ScriptHooks.set(new ScriptHooks.Handler() {
            @Override
            public int taskProgress(ServerPlayer player, ResourceLocation script, int current) {
                if (!TASK.hasListeners(script)) {
                    return current;
                }
                ScriptTaskKubeEvent event = new ScriptTaskKubeEvent(player, script, current);
                TASK.post(ScriptType.SERVER, script, event);
                return event.getProgress();
            }

            @Override
            public void reward(ServerPlayer player, ResourceLocation script) {
                if (REWARD.hasListeners(script)) {
                    REWARD.post(ScriptType.SERVER, script, new ScriptRewardKubeEvent(player, script));
                }
            }
        });
        NeoForge.EVENT_BUS.addListener((QuestCompletedEvent event) -> {
            if (COMPLETED.hasListeners(event.quest()) || COMPLETED.hasListeners()) {
                COMPLETED.post(ScriptType.SERVER, event.quest(), new QuestCompletedKubeEvent(event));
            }
        });
    }

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(GROUP);
    }

    @Override
    public void registerBindings(BindingRegistry bindings) {
        if (bindings.type().isServer()) {
            bindings.add("RYSQuestsWallet", WalletApi.class);
        }
    }
}
