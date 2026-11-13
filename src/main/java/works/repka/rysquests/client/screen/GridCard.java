package works.repka.rysquests.client.screen;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import works.repka.ryscore.ui.Draw;
import works.repka.ryscore.ui.Numbers;

/**
 * A card of a grid: a shop offer, an achievement, a guide, a map form (mockup {@code .gc}, pictures 25, 28). The frame and its
 * states are shared, the inside is either the standard shop layout or the caller's own:
 *
 * <pre>
 * // shop offer
 * GridCard.of("Андезитовый сплав").icon(stack, 64).meta("нужно для квеста").price(4).button("Купить", () -> buy(offer))
 *         .selected(offer == chosen).onClick(() -> chosen = offer).render(ui, x, y, w, h);
 * // anything else: the frame, then draw inside {@code GridCard.inner(x, y, w, h)}
 * GridCard.of("").selected(on).body((ui2, ix, iy, iw, ih) -> drawAchievement(...)).render(ui, x, y, w, h);
 * </pre>
 */
final class GridCard {
    static final int PAD = 12;
    static final int GAP = 10;
    private static final int ICON = 40;
    private static final float NAME_PX = 13;
    private static final float META_PX = 11.5f;

    /** The caller's own inside of the card, in the padded rectangle. */
    @FunctionalInterface
    interface Body {
        void render(Ui ui, float x, float y, float w, float h);
    }

    private final String title;
    @Nullable
    private ItemStack icon;
    private int count;
    @Nullable
    private String meta;
    private String metaToken = "text.muted";
    private long price = -1;
    private boolean short_;
    private boolean gain;
    private boolean buttonOff;
    @Nullable
    private String button;
    @Nullable
    private Runnable buttonAction;
    private boolean selected;
    private boolean off;
    @Nullable
    private Runnable click;
    @Nullable
    private Body body;

    private GridCard(String title) {
        this.title = title;
    }

    static GridCard of(String title) {
        return new GridCard(title);
    }

    GridCard icon(@Nullable ItemStack stack, int stackCount) {
        icon = stack;
        count = stackCount;
        return this;
    }

    /** The line under the name, such as a limit ("1 из 3 сегодня"); the name takes up to two lines above it. */
    GridCard meta(@Nullable String line) {
        meta = line;
        return this;
    }

    GridCard meta(@Nullable String line, String colorToken) {
        meta = line;
        metaToken = colorToken;
        return this;
    }

    /** A price in coins at the bottom left, in short form ("2.4к"); {@code notEnough} paints it as missing. */
    GridCard price(long coins, boolean notEnough) {
        price = coins;
        short_ = notEnough;
        return this;
    }

    GridCard button(String label, Runnable action) {
        button = label;
        buttonAction = action;
        buttonOff = false;
        return this;
    }

    /** A button that cannot be pressed now, faded, its label the reason ("Не хватает", "Закрыто", "Нечего продать"). */
    GridCard buttonOff(String label) {
        button = label;
        buttonAction = null;
        buttonOff = true;
        return this;
    }

    /** What a sale pays, written as a gain with the coin after it ("+1 ●", picture 27). */
    GridCard gain(long coins) {
        price = coins;
        short_ = false;
        gain = true;
        return this;
    }

    /** Selected: light border and plate, never the accent (plan, section 1). */
    GridCard selected(boolean on) {
        selected = on;
        return this;
    }

    /** Faded to half: closed for now. */
    GridCard off(boolean on) {
        off = on;
        return this;
    }

    GridCard onClick(@Nullable Runnable action) {
        click = action;
        return this;
    }

    GridCard body(@Nullable Body inside) {
        body = inside;
        return this;
    }

    void render(Ui ui, float x, float y, float w, float h) {
        ui.box(x, y, w, h, 12, ui.c(selected ? "tab.active.bg" : "card.bg"), ui.c(selected ? "text.secondary" : "panel.border"));
        if (click != null) {
            ui.hit(x, y, w, h, click);
        }
        if (body != null) {
            body.render(ui, x + PAD, y + PAD, w - PAD * 2, h - PAD * 2);
        } else {
            renderStandard(ui, x + PAD, y + PAD, w - PAD * 2, h - PAD * 2);
        }
        if (off) {
            ui.fill(x, y, w, h, 12, Draw.alpha(ui.c("panel.bg"), 0.5f));
        }
    }

    private void renderStandard(Ui ui, float x, float y, float w, float h) {
        float textX = x;
        if (icon != null) {
            ui.box(x, y, ICON, ICON, 6, ui.c("slot.bg"), ui.c("slot.border"));
            ui.item(icon, x + 6, y + 6, 28);
            if (count > 1) {
                ui.count(count, x + ICON - 1, y + ICON + 2);
            }
            textX = x + ICON + GAP;
        }
        float textW = x + w - textX;
        float nameH = Ui.lineHeight(NAME_PX);
        java.util.List<String> lines = ui.wrap(title, textW, NAME_PX, Ui.SEMIBOLD, 2);
        float blockH = lines.size() * nameH + (meta == null ? 0 : Ui.lineHeight(META_PX));
        float top = y + (ICON - blockH) / 2f;
        for (int i = 0; i < lines.size(); i++) {
            ui.label(lines.get(i), textX, top + nameH * i + nameH / 2f, NAME_PX, Ui.SEMIBOLD, ui.c("text.primary"));
        }
        if (!lines.isEmpty() && (lines.size() > 1 ? !String.join(" ", lines).equals(title) : !lines.getFirst().equals(title))) {
            ui.tooltipIfOver(textX, top, textW, nameH * lines.size(), net.minecraft.network.chat.Component.literal(title));
        }
        if (meta != null) {
            ui.labelFit(meta, textX, top + nameH * lines.size() + Ui.lineHeight(META_PX) / 2f, textW, META_PX, Ui.REGULAR, ui.c(metaToken));
        }
        float bottom = y + h;
        if (price >= 0) {
            float cy = bottom - 14;
            if (gain) {
                String text = "+" + Numbers.compact(price);
                ui.label(text, x, cy, 15, Ui.DISPLAY, ui.c("text.primary"));
                ui.circle(x + ui.width(text, 15, Ui.DISPLAY) + 6, cy - 5.5f, 11, ui.c("coin"));
            } else {
                ui.circle(x, cy - 5.5f, 11, ui.c("coin"));
                ui.label(Numbers.compact(price), x + 17, cy, 15, Ui.DISPLAY, ui.c(short_ ? "state.bad" : "text.primary"));
            }
        }
        if (button != null && buttonAction != null) {
            float bw = ui.buttonWidth(button, 12, Ui.MEDIUM, 10);
            ui.button(button, x + w - bw, bottom - 28, bw, 28, 9, 12, Ui.MEDIUM, 10, Ui.Style.PLAIN, buttonAction);
        } else if (button != null && buttonOff) {
            float bw = Math.min(w - 50, ui.buttonWidth(button, 12, Ui.MEDIUM, 10));
            ui.box(x + w - bw, bottom - 28, bw, 28, 9, Draw.alpha(ui.c("button.bg"), 0.5f), Draw.alpha(ui.c("button.border"), 0.5f));
            ui.labelCentered(ui.fit(button, bw - 8, 12, Ui.MEDIUM), x + w - bw / 2f, bottom - 14, 12, Ui.MEDIUM, Draw.alpha(ui.c("text.secondary"), 0.5f));
        }
    }
}
