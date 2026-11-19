package works.repka.rysquests.client.screen;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import works.repka.ryscore.ui.Icons;
import works.repka.ryscore.ui.Numbers;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.RewardDef;
import works.repka.rysquests.data.TaskDef;
import works.repka.rysquests.editor.QuestEdits;
import works.repka.rysquests.reward.ChoiceReward;
import works.repka.rysquests.reward.CurrencyReward;
import works.repka.rysquests.reward.ItemReward;
import works.repka.rysquests.reward.RandomReward;
import works.repka.rysquests.reward.TitleReward;
import works.repka.rysquests.reward.XpReward;
import works.repka.rysquests.task.AdvancementTask;
import works.repka.rysquests.task.CheckboxTask;
import works.repka.rysquests.task.FluidTask;
import works.repka.rysquests.task.ItemTask;
import works.repka.rysquests.task.KillTask;
import works.repka.rysquests.task.ScriptTask;
import works.repka.rysquests.task.StatTask;
import works.repka.rysquests.task.VisitTask;
import works.repka.rysquests.task.XpTask;

/** Tasks, rewards and types of quests in the editor's plain words (pictures 33, 34, 35b): "Получить: Медная катушка ×1", "25 опыта". */
final class EdText {
    private EdText() {
    }

    static String tr(String key, Object... args) {
        return EditorBook.tr(key, args);
    }

    static String item(ResourceLocation id) {
        return TaskText.stackOf(id).getHoverName().getString();
    }

    /** What the task asks, as the task row and the table say it. */
    static String task(TaskDef task) {
        if (task.title().isPresent()) {
            return EditorBook.get().text(task.title().get());
        }
        return switch (task) {
            case ItemTask item -> {
                String name = item.itemRefs().isEmpty() ? "?" : item.itemRefs().getFirst().tag() ? "#" + item.itemRefs().getFirst().id() : item(item.itemRefs().getFirst().id());
                String key = switch (item.mode()) {
                    case CONSUME -> "rysquests.ed.task.text.submit";
                    case CRAFT -> "rysquests.ed.task.text.craft";
                    default -> "rysquests.ed.task.text.get";
                };
                yield tr(key, name) + (item.count() > 1 ? " ×" + item.count() : "");
            }
            case FluidTask fluid -> tr("rysquests.ed.task.text.fluid", BuiltInRegistries.FLUID.get(fluid.fluid()).getFluidType().getDescription().getString(), fluid.amount());
            case KillTask kill -> tr("rysquests.ed.task.text.kill", BuiltInRegistries.ENTITY_TYPE.get(kill.entity()).getDescription().getString()) + (kill.count() > 1 ? " ×" + kill.count() : "");
            case VisitTask visit -> tr("rysquests.ed.task.text.visit", visit.structure().map(Object::toString).or(() -> visit.biome().map(Object::toString))
                    .or(() -> visit.dimension().map(Object::toString)).orElse("?"));
            case StatTask stat -> tr("rysquests.ed.task.text.stat", stat.entry().getPath(), stat.count());
            case XpTask xp -> tr(xp.points() ? "rysquests.ed.task.text.xp_points" : "rysquests.ed.task.text.xp_levels", xp.amount());
            case CheckboxTask box -> tr("rysquests.ed.task.text.checkbox");
            case AdvancementTask advancement -> tr("rysquests.ed.task.text.advancement", advancement.advancement().getPath());
            case ScriptTask script -> tr("rysquests.ed.task.text.script", script.script().toString());
            case works.repka.rysquests.task.CollectionTask collection -> tr("rysquests.ed.task.text.collection", Plural.full("rysquests.plural.item", collection.items().size()));
            case works.repka.rysquests.task.ClaimTask claims -> tr("rysquests.task.claims", claims.count());
            default -> task.type().toString();
        };
    }

    /** The line under a task in the form: "предмет остаётся у игрока". */
    @Nullable
    static String taskSub(TaskDef task) {
        return switch (task) {
            case ItemTask item -> tr(item.mode() == ItemTask.Mode.CONSUME ? "rysquests.ed.task.sub.taken" : item.mode() == ItemTask.Mode.CRAFT ? "rysquests.ed.task.sub.crafted"
                    : "rysquests.ed.task.sub.kept");
            case FluidTask fluid -> tr(fluid.mode() == ItemTask.Mode.CONSUME ? "rysquests.ed.task.sub.taken" : "rysquests.ed.task.sub.kept");
            case KillTask kill -> tr("rysquests.ed.task.sub.kill");
            case CheckboxTask box -> tr("rysquests.ed.task.sub.checkbox");
            default -> null;
        };
    }

    static ItemStack taskIcon(TaskDef task) {
        return TaskText.icon(task);
    }

    @Nullable
    static Icons.Shape taskGlyph(TaskDef task) {
        return switch (task) {
            case KillTask kill -> Icons.Shape.SKULL;
            case VisitTask visit -> Icons.Shape.COMPASS;
            case StatTask stat -> Icons.Shape.CHART;
            case CheckboxTask box -> Icons.Shape.CHECK;
            case AdvancementTask advancement -> Icons.Shape.STAR;
            case ScriptTask script -> Icons.Shape.SPARK;
            case works.repka.rysquests.task.ClaimTask claims -> Icons.Shape.SHIELD;
            default -> null;
        };
    }

    /** A reward in short words, as the table's column has it: "25 опыта", "1 мон.". */
    static String rewardShort(RewardDef reward) {
        return switch (reward) {
            case XpReward xp -> xp.levels().map(n -> tr("rysquests.ed.reward.short.levels", n)).orElseGet(() -> tr("rysquests.ed.reward.short.xp", xp.points().orElse(0)));
            case CurrencyReward coins -> tr("rysquests.ed.reward.short.coins", Numbers.compact(coins.amount()));
            case ItemReward item -> tr("rysquests.ed.reward.short.item");
            case ChoiceReward choice -> tr("rysquests.ed.reward.short.choice");
            case RandomReward random -> tr("rysquests.ed.reward.short.random");
            case TitleReward title -> tr("rysquests.ed.reward.short.title");
            case works.repka.rysquests.reward.ClaimLimitReward land -> QuestPanel.landPhrase(land);
            default -> reward.type().toString();
        };
    }

    /** The rewards of a quest as one cell, at most three. Empty when there are none. */
    static String rewards(Quest quest) {
        List<String> out = new ArrayList<>();
        for (RewardDef reward : quest.rewards()) {
            if (out.size() < 3) {
                out.add(rewardShort(reward));
            }
        }
        return String.join(" · ", out);
    }

    /** The type of a quest in words of the table: "основной", "побочный", "веха". */
    static String kind(QuestEdits.Kind kind) {
        return tr("rysquests.ed.kind." + kind.name().toLowerCase(java.util.Locale.ROOT));
    }

    /** The first task of a quest in words, with "+N" for more; empty when it has none. */
    static String tasks(Quest quest) {
        if (quest.tasks().isEmpty()) {
            return "";
        }
        return task(quest.tasks().getFirst()) + (quest.tasks().size() > 1 ? " +" + (quest.tasks().size() - 1) : "");
    }

    /**
     * A reward typed into the table's cell: "10" or "10 опыта" is experience, "5 монет" coins. Empty when the text says neither.
     */
    static Optional<RewardDef> parseReward(String text) {
        String trimmed = text.trim().toLowerCase(java.util.Locale.ROOT);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d+)\\s*(.*)$").matcher(trimmed);
        if (!m.matches()) {
            return Optional.empty();
        }
        int amount = Integer.parseInt(m.group(1));
        if (amount <= 0) {
            return Optional.empty();
        }
        String word = m.group(2);
        JsonObject json = new JsonObject();
        if (word.startsWith("мон") || word.startsWith("coin")) {
            json.addProperty("type", "rysquests:currency");
            json.addProperty("id", "coin");
            json.addProperty("amount", amount);
        } else if (word.startsWith("ур") || word.startsWith("lev")) {
            json.addProperty("type", "rysquests:xp");
            json.addProperty("levels", amount);
        } else {
            json.addProperty("type", "rysquests:xp");
            json.addProperty("points", amount);
        }
        return RewardDef.CODEC.parse(EditorBook.ops(), json).result();
    }
}
