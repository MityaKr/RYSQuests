package works.repka.rysquests.task;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import works.repka.rysquests.RYSQuests;
import works.repka.rysquests.api.QuestRegistries;
import works.repka.rysquests.api.TaskType;
import works.repka.rysquests.data.TaskDef;

/** The task type registry and the built-in task types. */
public final class TaskTypes {
    public static final DeferredRegister<TaskType<?>> REGISTER = DeferredRegister.create(QuestRegistries.TASK_TYPES, RYSQuests.MODID);
    public static final Registry<TaskType<?>> REGISTRY = REGISTER.makeRegistry(builder -> {
    });

    public static final DeferredHolder<TaskType<?>, TaskType<ItemTask>> ITEM = register("item", ItemTask.CODEC);
    public static final DeferredHolder<TaskType<?>, TaskType<CheckboxTask>> CHECKBOX = register("checkbox", CheckboxTask.CODEC);
    public static final DeferredHolder<TaskType<?>, TaskType<AdvancementTask>> ADVANCEMENT = register("advancement", AdvancementTask.CODEC);

    public static final DeferredHolder<TaskType<?>, TaskType<StatTask>> STAT = register("stat", StatTask.CODEC);
    public static final DeferredHolder<TaskType<?>, TaskType<KillTask>> KILL = register("kill", KillTask.CODEC);
    public static final DeferredHolder<TaskType<?>, TaskType<VisitTask>> VISIT = register("visit", VisitTask.CODEC);

    public static final DeferredHolder<TaskType<?>, TaskType<FluidTask>> FLUID = register("fluid", FluidTask.CODEC);
    public static final DeferredHolder<TaskType<?>, TaskType<ScriptTask>> SCRIPT = register("script", ScriptTask.CODEC);
    public static final DeferredHolder<TaskType<?>, TaskType<XpTask>> XP = register("xp", XpTask.CODEC);
    /** Only in an achievement: its levels count the entries collected (ADR-62). */
    public static final DeferredHolder<TaskType<?>, TaskType<CollectionTask>> COLLECTION = register("collection", CollectionTask.CODEC);
    /** Chunks the team holds, with RYSClaims (plan of the map, section 8). */
    public static final DeferredHolder<TaskType<?>, TaskType<ClaimTask>> CLAIMS = register("claims", ClaimTask.CODEC);

    private TaskTypes() {
    }

    private static <T extends TaskDef> DeferredHolder<TaskType<?>, TaskType<T>> register(String name, MapCodec<T> codec) {
        TaskType<T> type = () -> codec;
        return REGISTER.register(name, () -> type);
    }
}
