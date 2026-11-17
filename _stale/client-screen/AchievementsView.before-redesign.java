package works.repka.rysquests.client.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.client.ClientAchievements;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.client.ClientProgress;
import works.repka.rysquests.data.Achievement;
import works.repka.rysquests.data.Achievement.Tier;
import works.repka.rysquests.data.RewardDef;
import works.repka.rysquests.network.QuestActionPayload;
import works.repka.rysquests.network.SelectTitlePayload;
import works.repka.rysquests.progress.QuestProgress;
import works.repka.rysquests.reward.CurrencyReward;
import works.repka.rysquests.reward.TitleReward;
import works.repka.ryscore.ui.Icons;
import works.repka.ryscore.ui.Numbers;

/** Achievements, from {@code design/Achievements.dc.html}. */
final class AchievementsView implements BookView {
    private static final int PANEL_W = 320;
    private static final int PAD_X = 20;
    private static final int PAD_Y = 16;
    private static final int GAP = 12;
    private static final int HEADER_H = 47;
    private static final int COLUMNS = 3;
    private static final int CARD_H = 113;
    private static final int CARD_GAP = 10;
    private static final int TIER_H = 50;
    private static final int WHEEL = 48;
    private static final String HIDDEN_FILTER = "\0hidden";
    /** Medal colors are the metals of design/Achievements.dc.html and stay the same in every theme (ADR-41). */
    private static final int GOLD = 0xFFF0C53D;
    private static final int SILVER = 0xFFC9C9CF;
    private static final int BRONZE = 0xFFB87333;

    private final Scroll gridScroll = new Scroll();
    private String category = "";
    private boolean byProgress = true;
    @Nullable
    private ResourceLocation selectedId;

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    private static String text(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private static boolean reached(Achievement achievement, Tier tier) {
        return ClientProgress.get(achievement.questId(tier)).completed();
    }

    /** The highest tier reached, or null. */
    @Nullable
    private static Tier best(Achievement achievement) {
        Tier found = null;
        for (Tier tier : achievement.tiers()) {
            if (reached(achievement, tier)) {
                found = tier;
            }
        }
        return found;
    }

    @Nullable
    private static Tier next(Achievement achievement) {
        for (Tier tier : achievement.tiers()) {
            if (!reached(achievement, tier)) {
                return tier;
            }
        }
        return null;
    }

    private static int progress(Achievement achievement) {
        int most = 0;
        for (Tier tier : achievement.tiers()) {
            QuestProgress own = ClientProgress.get(achievement.questId(tier));
            most = Math.max(most, own.completed() ? tier.amount() : own.task(0));
        }
        return most;
    }

    private static float ratio(Achievement achievement) {
        Tier next = next(achievement);
        return next == null ? 1 : Math.min(1, (float) progress(achievement) / next.amount());
    }

    private static boolean concealed(Achievement achievement) {
        return achievement.hidden() && best(achievement) == null;
    }

    private static int medal(Ui ui, @Nullable Tier tier) {
        if (tier == null) {
            return ui.c("card.border");
        }
        return switch (tier.tier()) {
            case "gold" -> GOLD;
            case "silver" -> SILVER;
            default -> BRONZE;
        };
    }

    private static String tierName(Tier tier) {
        String key = "rysquests.achievements.tier." + tier.tier();
        return Language.getInstance().has(key) ? tr(key) : tier.tier();
    }

    private static ItemStack icon(Achievement achievement) {
        return achievement.icon().map(TaskText::stackOf).orElse(ItemStack.EMPTY);
    }

    private static String name(Achievement achievement) {
        return concealed(achievement) ? tr("rysquests.achievements.unknown") : tr(achievement.titleKey());
    }

    private static String description(Achievement achievement) {
        if (concealed(achievement)) {
            return Language.getInstance().has(achievement.hintKey()) ? text("rysquests.achievements.hint", tr(achievement.hintKey())) : "";
        }
        return tr(achievement.textKey());
    }

    private List<Achievement> shown() {
        List<Achievement> list = new ArrayList<>();
        for (Achievement achievement : ClientBook.current().achievements().values()) {
            boolean match = category.isEmpty() || category.equals(HIDDEN_FILTER) && achievement.hidden() || achievement.category().filter(category::equals).isPresent();
            if (match) {
                list.add(achievement);
            }
        }
        if (byProgress) {
            list.sort(Comparator.comparing((Achievement achievement) -> next(achievement) == null).thenComparing(achievement -> -ratio(achievement)));
        } else {
            list.sort(Comparator.comparing(AchievementsView::name));
        }
        return list;
    }

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        int panelLeft = x + w - PANEL_W;
        List<Achievement> shown = shown();
        Achievement selected = selectedId == null ? null : ClientBook.current().achievements().get(selectedId);
        if (selected == null && !shown.isEmpty()) {
            selected = shown.getFirst();
        }
        renderPanel(ui, selected, panelLeft, y, PANEL_W, h);
        float left = x + PAD_X;
        float inner = panelLeft - x - PAD_X * 2;
        float cursor = y + PAD_Y;
        renderHeader(ui, left, cursor, inner);
        cursor += HEADER_H + GAP;
        cursor = renderFilters(ui, left, cursor, inner) + GAP;
        renderGrid(ui, shown, selected, left, cursor, inner, y + h - PAD_Y - cursor);
    }

    private static void renderHeader(Ui ui, float x, float y, float w) {
        ui.label(tr("rysquests.achievements.title"), x, y + 16, 19, Ui.DISPLAY, ui.c("text.primary"));
        ui.labelFit(tr("rysquests.achievements.subtitle"), x, y + 35.5f, w * 0.5f, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        var all = ClientBook.current().achievements().values();
        int got = (int) all.stream().filter(achievement -> best(achievement) != null).count();
        int[] medals = new int[3];
        for (Achievement achievement : all) {
            Tier tier = best(achievement);
            if (tier != null) {
                medals[tier.tier().equals("gold") ? 0 : tier.tier().equals("silver") ? 1 : 2]++;
            }
        }
        float boxW = 320;
        float boxX = x + w - boxW;
        float cy = y + HEADER_H / 2f;
        ui.box(boxX, y, boxW, HEADER_H, 10, ui.c("card.bg"), ui.c("panel.border"));
        String count = String.valueOf(got);
        ui.label(count, boxX + 14, cy - 7, 16, Ui.DISPLAY, ui.c("text.primary"));
        ui.label(text("rysquests.achievements.of", all.size()), boxX + 14 + ui.width(count, 16, Ui.DISPLAY) + 4, cy - 7, 12, Ui.REGULAR, ui.c("button.fg"));
        ui.bar(boxX + 14, cy + 8, 160, 5, 3, all.isEmpty() ? 0 : (float) got / all.size(), ui.c("accent"));
        ui.rect(boxX + 188, cy - 14, 1, 28, ui.c("panel.border"));
        float pen = boxX + 202;
        String[] tiers = {"gold", "silver", "bronze"};
        for (int i = 0; i < 3; i++) {
            ui.circle(pen, cy - 5, 10, medal(ui, new Tier(tiers[i], 1, List.of())));
            pen += 14;
            ui.label(String.valueOf(medals[i]), pen, cy, 12, Ui.REGULAR, ui.c("button.fg"));
            pen += ui.width(String.valueOf(medals[i]), 12, Ui.REGULAR) + 14;
        }
    }

    /** @return the y below the filters */
    private float renderFilters(Ui ui, float x, float y, float w) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("", 0);
        int hidden = 0;
        for (Achievement achievement : ClientBook.current().achievements().values()) {
            counts.merge("", 1, Integer::sum);
            achievement.category().ifPresent(found -> counts.merge(found, 1, Integer::sum));
            hidden += achievement.hidden() ? 1 : 0;
        }
        List<Chips.Chip> chips = new ArrayList<>();
        counts.forEach((key, count) -> chips.add(new Chips.Chip(key, key.isEmpty() ? tr("rysquests.guides.category_all") : tr(key), count)));
        if (hidden > 0) {
            chips.add(new Chips.Chip(HIDDEN_FILTER, tr("rysquests.achievements.hidden"), hidden));
        }
        String sort = tr(byProgress ? "rysquests.achievements.sort_progress" : "rysquests.guides.sort_name");
        float sortW = ui.width(sort, 12, Ui.REGULAR) + 24 + 14;
        float[] end = Chips.render(ui, chips, category, key -> category = key, x, y, w - sortW - 6);
        float sortX = x + w - sortW;
        ui.box(sortX, y, sortW, Chips.HEIGHT, Chips.HEIGHT / 2f, ui.over(sortX, y, sortW, Chips.HEIGHT) ? ui.c("button.bg") : 0, ui.c("panel.border"));
        ui.label(sort, sortX + 12, y + Chips.HEIGHT / 2f, 12, Ui.REGULAR, ui.c("text.muted"));
        ui.icon(Icons.Shape.CHEVRON_DOWN, sortX + sortW - 21, y + Chips.HEIGHT / 2f - 4.5f, 9, ui.c("text.muted"));
        ui.hit(sortX, y, sortW, Chips.HEIGHT, () -> byProgress = !byProgress);
        return end[1] + Chips.HEIGHT;
    }

    private void renderGrid(Ui ui, List<Achievement> shown, @Nullable Achievement selected, float x, float y, float w, float h) {
        float cardW = (w - (COLUMNS - 1) * CARD_GAP) / COLUMNS;
        int rows = (shown.size() + COLUMNS - 1) / COLUMNS;
        gridScroll.content(rows * CARD_H + Math.max(0, rows - 1) * CARD_GAP, Math.round(h));
        int offset = gridScroll.offset();
        ui.clip(Math.round(x), Math.round(y), Math.round(w), Math.round(h));
        for (int i = 0; i < shown.size(); i++) {
            float cy = y + (i / COLUMNS) * (CARD_H + CARD_GAP) - offset;
            if (cy + CARD_H > y && cy < y + h) {
                renderCard(ui, shown.get(i), shown.get(i) == selected, x + (i % COLUMNS) * (cardW + CARD_GAP), cy, cardW);
            }
        }
        ui.unclip();
    }

    private static void medalIcon(Ui ui, ItemStack icon, int color, float x, float y, float size, float stroke, float iconSize) {
        ui.circle(x, y, size, ui.c("slot.bg"));
        ui.ring(x, y, size, stroke, color);
        ui.item(icon, x + (size - iconSize) / 2f, y + (size - iconSize) / 2f, iconSize);
    }

    private void renderCard(Ui ui, Achievement achievement, boolean selected, float x, float y, float w) {
        Tier best = best(achievement);
        boolean concealed = concealed(achievement);
        ui.box(x, y, w, CARD_H, 12, ui.c("card.bg"), ui.c("panel.border"));
        if (selected) {
            ui.strokeBold(x, y, w, CARD_H, 12, ui.c("accent"));
        }
        medalIcon(ui, concealed ? ItemStack.EMPTY : icon(achievement), medal(ui, best), x + 13, y + 13, 44, 3, 20);
        float textX = x + 13 + 44 + 12;
        float inner = w - 13 - 13 - 44 - 12;
        String tier = concealed ? tr("rysquests.achievements.concealed") : best != null ? tierName(best) : "";
        float tierW = tier.isEmpty() ? 0 : ui.width(tier, 10.5f, Ui.REGULAR) + 6;
        ui.labelFitTip(name(achievement), textX, y + 13 + 8, inner - tierW, 13, Ui.SEMIBOLD, ui.c(concealed ? "text.muted" : "text.primary"));
        if (!tier.isEmpty()) {
            ui.labelRight(tier, textX + inner, y + 13 + 8, 10.5f, Ui.REGULAR, best != null ? medal(ui, best) : ui.c("text.muted"));
        }
        List<String> lines = ui.wrap(description(achievement), inner, 11.5f, Ui.REGULAR, 2);
        float textY = y + 13 + 16 + 4;
        ui.lines(lines, textX, textY + lines.size() * 7.75f, 15.5f, 11.5f, Ui.REGULAR, ui.c(concealed ? "text.muted" : "button.fg"));
        float rowY = textY + lines.size() * 15.5f + 4;
        Tier next = next(achievement);
        String progress = next == null ? tr("rysquests.achievements.got") : concealed ? "" : Ui.ratio(progress(achievement), next.amount());
        float progressW = progress.isEmpty() ? 0 : ui.width(progress, 11, Ui.REGULAR) + 8;
        ui.bar(textX, rowY + 5, inner - progressW, 4, 2, ratio(achievement), best != null ? medal(ui, best) : ui.c("text.muted"));
        ui.labelRight(progress, textX + inner, rowY + 7, 11, Ui.REGULAR, ui.c("text.muted"));
        ui.labelFit(rarityLine(achievement), textX, rowY + 14 + 4 + 6, inner, 10.5f, Ui.REGULAR, ui.c("text.dim"));
        ui.hit(x, y, w, CARD_H, () -> selectedId = achievement.id());
    }

    private static String towards(String kind, Tier tier, Object... args) {
        String own = "rysquests.achievements." + kind + "." + tier.tier();
        if (Language.getInstance().has(own)) {
            return text(own, args);
        }
        List<Object> all = new ArrayList<>(List.of(tierName(tier)));
        all.addAll(List.of(args));
        return text("rysquests.achievements." + kind, all.toArray());
    }

    private static String rarityLine(Achievement achievement) {
        Tier best = best(achievement);
        Tier next = next(achievement);
        if (best != null && next != null && ratio(achievement) > 0.5f) {
            return towards("left", next, Numbers.compact(next.amount() - progress(achievement)));
        }
        Tier shown = best != null ? best : achievement.tiers().getFirst();
        int percent = ClientAchievements.rarity(achievement.questId(shown));
        if (percent == 0) {
            return tr("rysquests.achievements.nobody");
        }
        return best == null || achievement.tiers().size() == 1 ? text("rysquests.achievements.has", percent) : text("rysquests.achievements.tier_has", tierName(best), percent);
    }

    private static String rewards(Tier tier) {
        List<String> parts = new ArrayList<>();
        for (RewardDef reward : tier.rewards()) {
            if (reward instanceof CurrencyReward currency) {
                String key = "rysquests.currency." + currency.id();
                parts.add("+" + Numbers.compact(currency.amount()) + " " + (Language.getInstance().has(key) ? tr(key) : currency.id()));
            } else if (reward instanceof TitleReward title) {
                parts.add(text("rysquests.achievements.title_reward", tr(title.id())));
            }
        }
        return String.join(" · ", parts);
    }

    private void renderPanel(Ui ui, @Nullable Achievement achievement, int x, int y, int w, int h) {
        ui.fillCorners(x, y, w, h, 13, ui.c("card.bg"), false, true, true, false);
        ui.rect(x, y, 1, h, ui.c("panel.border"));
        if (achievement == null) {
            return;
        }
        float left = x + 17;
        float inner = w - 33;
        float cursor = y + 16;
        Tier best = best(achievement);
        boolean concealed = concealed(achievement);
        medalIcon(ui, concealed ? ItemStack.EMPTY : icon(achievement), medal(ui, best), left, cursor, 56, 4, 26);
        ui.labelFitTip(name(achievement), left + 68, cursor + 10 + 11, inner - 68, 17, Ui.DISPLAY, ui.c("text.primary"));
        String category = achievement.category().map(AchievementsView::tr).orElse("");
        String state = best != null ? text("rysquests.achievements.tier_got", tierName(best)) : tr("rysquests.achievements.not_yet");
        ui.labelFit(category.isEmpty() ? state : category + " · " + state, left + 68, cursor + 10 + 22 + 7, inner - 68, 11.5f, Ui.REGULAR, ui.c("text.muted"));
        cursor += 56 + 12;
        List<String> lines = ui.wrap(description(achievement), inner, 13, Ui.REGULAR, 6);
        ui.lines(lines, left, cursor + lines.size() * 9.75f, 19.5f, 13, Ui.REGULAR, ui.readingFg());
        cursor += lines.size() * 19.5f + 12;
        if (!concealed) {
            ui.caps(tr("rysquests.achievements.tiers"), left, cursor + 7, ui.c("text.dim"));
            cursor += 14 + 6;
            for (Tier tier : achievement.tiers()) {
                renderTier(ui, achievement, tier, left, cursor, inner);
                cursor += TIER_H + 6;
            }
            cursor += 6;
            cursor = renderNext(ui, achievement, left, cursor, inner);
        }
        renderButtons(ui, achievement, left, y + h - 16 - 34, inner);
    }

    private static void renderTier(Ui ui, Achievement achievement, Tier tier, float x, float y, float w) {
        boolean got = reached(achievement, tier);
        boolean isNext = tier == next(achievement);
        ui.box(x, y, w, TIER_H, 9, ui.c("status.bg"), isNext ? ui.warmBorder(0.34f) : ui.c("panel.border"));
        float cy = y + TIER_H / 2f;
        int color = medal(ui, tier);
        ui.ring(x + 11, cy - 8, 16, 3, color);
        ui.labelFit(StringUtils.capitalize(tierName(tier)) + " · " + Numbers.compact(tier.amount()), x + 37, cy - 8, w - 37 - 80, 12.5f, Ui.REGULAR, ui.nameFg());
        ui.labelFit(rewards(tier), x + 37, cy + 9, w - 37 - 80, 11, Ui.REGULAR, ui.c("text.muted"));
        String status = got ? tr("rysquests.achievements.got") : Math.round(100 * Math.min(1f, (float) progress(achievement) / tier.amount())) + " %";
        ui.labelRight(status, x + w - 11, cy, 11, Ui.REGULAR, ui.c(got ? "state.ok" : isNext ? "accent" : "text.muted"));
    }

    private static float renderNext(Ui ui, Achievement achievement, float x, float y, float w) {
        Tier next = next(achievement);
        if (next == null) {
            return y;
        }
        float h = 66;
        ui.fill(x, y, w, h, 10, ui.c("status.bg"));
        ui.label(towards("until", next), x + 12, y + 12 + 7, 12, Ui.REGULAR, ui.c("button.fg"));
        ui.labelRight(Ui.ratio(progress(achievement), next.amount()), x + w - 12, y + 12 + 7, 12, Ui.REGULAR, ui.c("text.primary"));
        ui.bar(x + 12, y + 32, w - 24, 6, 3, ratio(achievement), medal(ui, next));
        int percent = ClientAchievements.rarity(achievement.questId(next));
        ui.label(percent > 0 ? text("rysquests.achievements.tier_has", tierName(next), percent) : tr("rysquests.achievements.nobody"), x + 12, y + 44 + 7, 11, Ui.REGULAR, ui.c("text.muted"));
        return y + h + 12;
    }

    private static void renderButtons(Ui ui, Achievement achievement, float x, float y, float w) {
        Tier next = next(achievement);
        TitleReward title = ownedTitle(achievement);
        boolean canPin = next != null && !concealed(achievement);
        float buttonW = canPin && title != null ? (w - 6) / 2f : w;
        float pen = x;
        if (canPin) {
            ResourceLocation quest = achievement.questId(next);
            boolean pinned = ClientProgress.pinned().contains(quest);
            ui.button(tr(pinned ? "rysquests.book.unpin" : "rysquests.achievements.pin"), pen, y, buttonW, 34, 9, 12, Ui.REGULAR, 6, Ui.Style.PLAIN,
                    () -> PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.PIN, quest, 0, List.of())));
            pen += buttonW + 6;
        }
        if (title != null) {
            boolean worn = title.id().equals(ClientAchievements.activeTitle());
            String label = worn ? tr("rysquests.achievements.take_off") : text("rysquests.achievements.wear", tr(title.id()));
            String target = worn ? "" : title.id();
            ui.button(label, pen, y, buttonW, 34, 9, 12, Ui.REGULAR, 6, Ui.Style.PLAIN, () -> PacketDistributor.sendToServer(new SelectTitlePayload(target)));
        }
    }

    /** The best title of this achievement the player has. */
    @Nullable
    private static TitleReward ownedTitle(Achievement achievement) {
        TitleReward found = null;
        for (Tier tier : achievement.tiers()) {
            for (RewardDef reward : tier.rewards()) {
                if (reward instanceof TitleReward title && ClientAchievements.owns(title.id())) {
                    found = title;
                }
            }
        }
        return found;
    }

    @Override
    public void scroll(double mouseX, double mouseY, double amount) {
        gridScroll.wheel(amount, WHEEL);
    }

    void selectForTest(ResourceLocation id) {
        selectedId = id;
    }
}
