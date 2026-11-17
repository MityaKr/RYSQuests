package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.data.BookMeta;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.RewardDef;
import works.repka.rysquests.data.TeamMode;
import works.repka.rysquests.network.QuestActionPayload;
import works.repka.rysquests.progress.EpochRewards;
import works.repka.rysquests.progress.QuestProgress;
import works.repka.rysquests.reward.ChoiceReward;
import works.repka.rysquests.reward.ItemReward;
import works.repka.rysquests.reward.RandomReward;
import works.repka.ryscore.ui.Icons;

/**
 * The reward of an epoch on the map (mockup {@code _audit/chronicle/04_epoch_reward.png}, ADR-60): the epoch's plate and its row in
 * the sidebar carry a mark while the reward waits, a click on the plate opens this card. The card says what the epoch is (the text
 * of its quest), what it gives, and takes it with "Забрать награду". Underneath it is the claim of the epoch's quest, so a reward
 * taken before, in the old chapter "Начало", stays taken.
 */
final class EpochCards {
    static final int WIDTH = 330;
    private static final int PAD = 14;
    private static final int SLOT = 34;
    private static final int GAP = 6;
    private static final int BUTTON_H = 32;
    private static final float TEXT_PX = 12.5f;
    private static final float TEXT_LINE = 18;
    private static final int TEXT_LINES = 6;

    /** The options picked in the card, per reward index, for the epoch whose card is open. */
    private final Map<Integer, Integer> picks = new HashMap<>();
    private int picksFor;

    // --- what the book knows of epochs' rewards ---

    /** The live quest that is the reward of an epoch, or null. */
    @Nullable
    static Quest questOf(int epoch) {
        QuestBook book = BookData.book();
        return book.meta().epochReward(epoch).map(id -> book.quests().get(id)).filter(quest -> quest.options().live()).orElse(null);
    }

    static EpochRewards.State state(Quest quest) {
        return EpochRewards.state(quest, ClientProgress::get);
    }

    /** Whether the quest is an epoch's reward that can be taken now. */
    static boolean waits(Quest quest) {
        return BookData.book().isEpochReward(quest.id()) && state(quest) == EpochRewards.State.READY;
    }

    /** Whether the epoch's reward can be taken now: its plate and its row show the mark. */
    static boolean waiting(int epoch) {
        Quest quest = questOf(epoch);
        return quest != null && state(quest) == EpochRewards.State.READY;
    }

    /** The epochs whose reward waits, in book order. */
    static List<Integer> waitingEpochs() {
        List<Integer> out = new ArrayList<>();
        for (int epoch = 1; epoch <= BookData.book().meta().epochs().size(); epoch++) {
            if (waiting(epoch)) {
                out.add(epoch);
            }
        }
        return out;
    }

    /** "Эпоха II. Индустрия". */
    static String title(int epoch) {
        String name = BookData.book().meta().epoch(epoch).map(BookMeta.Epoch::title).map(key -> Component.translatable(key).getString()).orElse("");
        return name.isEmpty() ? t("rysquests.epoch.card.title_bare", Ui.roman(epoch)) : t("rysquests.epoch.card.title", Ui.roman(epoch), name);
    }

    private static String t(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    // --- the card ---

    /** The card's height for its epoch, at {@link #WIDTH}. */
    float height(Ui ui, int epoch) {
        return ui.measure(() -> Math.round(render(ui, epoch, -10_000, -10_000)));
    }

    /**
     * Draws the card of an epoch whose reward is a quest of the book, inside a frame the caller has drawn.
     *
     * @return the height taken
     */
    float render(Ui ui, int epoch, float x, float y) {
        Quest quest = questOf(epoch);
        if (quest == null) {
            return 0;
        }
        if (picksFor != epoch) {
            picks.clear();
            picksFor = epoch;
        }
        EpochRewards.State state = state(quest);
        QuestProgress own = ClientProgress.get(quest.id());
        float inner = WIDTH - PAD * 2;
        float left = x + PAD;
        float pen = y + PAD;
        // the head: a mark, the epoch's name, where it stands
        String stateText = t(switch (state) {
            case LOCKED -> "rysquests.epoch.card.locked";
            case READY -> "rysquests.epoch.card.open";
            case CLAIMED -> "rysquests.epoch.card.claimed";
        });
        float stateW = ui.width(stateText, 11.5f, Ui.REGULAR);
        ui.icon(Icons.Shape.SPARK, left, pen + 3, 14, ui.c(state == EpochRewards.State.READY ? "coin" : "text.muted"));
        ui.labelFit(title(epoch), left + 22, pen + 10, inner - 22 - stateW - 10, 14.5f, Ui.DISPLAY, ui.c("text.primary"));
        ui.labelRight(stateText, left + inner, pen + 10, 11.5f, Ui.REGULAR, ui.c(state == EpochRewards.State.CLAIMED ? "state.ok" : "text.muted"));
        pen += 20 + 10;
        // the epoch's own words: what it is and which branches it opens
        String text = quest.text().map(key -> QuestFacts.plain(Component.translatable(key).getString())).orElse("");
        if (!text.isBlank()) {
            for (String line : ui.wrap(text, inner, TEXT_PX, Ui.REGULAR, TEXT_LINES)) {
                ui.label(line, left, pen + TEXT_LINE / 2f, TEXT_PX, Ui.REGULAR, ui.c("text.body"));
                pen += TEXT_LINE;
            }
            pen += 12;
        }
        pen = rewards(ui, quest, own, state, left, pen, inner);
        // what to do now
        if (state == EpochRewards.State.READY) {
            boolean picked = !awaitsPick(quest, own);
            String label = t(picked ? "rysquests.epoch.card.claim" : "rysquests.epoch.card.pick");
            float bw = ui.buttonWidth(label, 13, Ui.SEMIBOLD, 16);
            ui.button(label, left, pen, bw, BUTTON_H, 8, 13, Ui.SEMIBOLD, 16, picked ? Ui.Style.ACCENT : Ui.Style.PLAIN, picked ? () -> claim(quest) : () -> {
            });
            pen += BUTTON_H;
        } else if (state == EpochRewards.State.LOCKED) {
            for (String line : ui.wrap(BookData.waitingFor(quest).getString(), inner, 12, Ui.REGULAR, 2)) {
                ui.label(line, left, pen + 8.5f, 12, Ui.REGULAR, ui.c("text.muted"));
                pen += 17;
            }
        } else {
            ui.check(left, pen + 3, 12, ui.c("state.ok"));
            ui.label(t("rysquests.epoch.card.claimed_note"), left + 18, pen + 8.5f, 12, Ui.REGULAR, ui.c("state.ok"));
            pen += 17;
        }
        return pen + PAD - y;
    }

    /** The rewards in two groups, as the mockup has them: the plain ones as chips, then each choice as a row of slots. */
    private float rewards(Ui ui, Quest quest, QuestProgress own, EpochRewards.State state, float x, float y, float w) {
        boolean team = ClientProgress.teamSize() > 1;
        List<RewardChip> chips = new ArrayList<>();
        boolean plainTaken = false;
        List<Integer> choices = new ArrayList<>();
        for (int i = 0; i < quest.rewards().size(); i++) {
            RewardDef reward = quest.rewards().get(i);
            if (reward instanceof ChoiceReward) {
                choices.add(i);
                continue;
            }
            RewardChip chip = reward instanceof RandomReward ? RewardChip.glyph(Icons.Shape.SPARK, t("rysquests.qpanel.reward_random"))
                    : QuestPanel.chip(reward, team && reward.teamMode() == TeamMode.ONCE);
            if (chip != null) {
                chips.add(chip);
                plainTaken |= own.claimed().contains(i);
            }
        }
        float pen = y;
        if (!chips.isEmpty()) {
            pen += DetailPanel.rubric(ui, x, pen, w, t("rysquests.epoch.card.reward"), team ? t("rysquests.qpanel.each") : null) + 8;
            float top = pen;
            pen += RewardChip.row(ui, x, pen, w, chips);
            if (plainTaken && state != EpochRewards.State.LOCKED) {
                ui.veil(x, top, w, pen - top, 9, ui.c("panel.bg"), 0.45f);
            }
            pen += 12;
        }
        for (int index : choices) {
            ChoiceReward choice = (ChoiceReward) quest.rewards().get(index);
            boolean taken = own.claimed().contains(index);
            pen += DetailPanel.rubric(ui, x, pen, w, t("rysquests.epoch.card.choice"), t("rysquests.epoch.card.one_of", choice.of().size())) + 8;
            float top = pen;
            int perRow = Math.max(1, (int) ((w + GAP) / (SLOT + GAP)));
            int picked = taken ? -1 : picks.getOrDefault(index, -1);
            for (int option = 0; option < choice.of().size(); option++) {
                float sx = x + (option % perRow) * (SLOT + GAP);
                float sy = pen + (option / perRow) * (SLOT + GAP);
                RewardDef candidate = choice.of().get(option);
                boolean on = option == picked;
                boolean over = ui.over(sx, sy, SLOT, SLOT);
                ui.box(sx, sy, SLOT, SLOT, 7, ui.c(over && !taken && state == EpochRewards.State.READY ? "slot.hover" : "slot.bg"), ui.c(on ? "text.primary" : "slot.border"));
                if (on) {
                    ui.stroke(sx - 1, sy - 1, SLOT + 2, SLOT + 2, 8, ui.c("text.primary"));
                }
                if (candidate instanceof ItemReward item) {
                    ItemStack stack = QuestFacts.stack(item);
                    ui.item(stack, sx + 7, sy + 7, 20);
                    ui.count(item.count(), sx + SLOT - 2, sy + SLOT - 1);
                    // a click picks the reward: the item keeps its tooltip and usages
                    ui.hoverItem(stack, sx, sy, SLOT, SLOT);
                } else {
                    ui.icon(Icons.Shape.SPARK, sx + 10, sy + 10, 14, ui.c("text.secondary"));
                    ui.tooltipIfOver(sx, sy, SLOT, SLOT, Component.literal(QuestPanel.phrase(candidate, false)));
                }
                int which = option;
                if (!taken && state == EpochRewards.State.READY) {
                    ui.hit(sx, sy, SLOT, SLOT, () -> picks.put(index, which));
                }
            }
            int rows = (choice.of().size() + perRow - 1) / perRow;
            pen += rows * SLOT + Math.max(0, rows - 1) * GAP;
            if (taken) {
                ui.veil(x, top, w, pen - top, 9, ui.c("panel.bg"), 0.45f);
            }
            pen += 12;
        }
        return pen;
    }

    private boolean awaitsPick(Quest quest, QuestProgress own) {
        for (int i = 0; i < quest.rewards().size(); i++) {
            if (quest.rewards().get(i) instanceof ChoiceReward choice && !own.claimed().contains(i)) {
                int pick = picks.getOrDefault(i, -1);
                if (pick < 0 || pick >= choice.of().size()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Takes the reward: the server ticks what the epoch's quest still lacks and claims it, checking everything again. */
    void claim(Quest quest) {
        List<Integer> chosen = new ArrayList<>();
        for (int i = 0; i < quest.rewards().size(); i++) {
            chosen.add(picks.getOrDefault(i, -1));
        }
        QuestFacts.send(QuestActionPayload.CLAIM_EPOCH, quest, 0, chosen);
        BookTallies.invalidate();
    }

    /** The autotest picks an option as a click would. */
    void pickForTest(int reward, int option) {
        picks.put(reward, option);
    }

    /** The epochs' reward quests by id, for lookups that do not need the card. */
    static Map<ResourceLocation, Integer> all() {
        return BookData.book().meta().epochRewards();
    }
}
