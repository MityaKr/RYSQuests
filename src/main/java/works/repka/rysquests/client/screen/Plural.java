package works.repka.rysquests.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Word forms for a number written in full ("2 400 монет", "1 монета"), with the {@code .one}, {@code .few}, {@code .many} keys that
 * {@link Ui#counted} reads. Unlike it the number goes in already written, so thousands keep their space.
 */
final class Plural {
    private Plural() {
    }

    static String of(String key, long count, String shown) {
        boolean russian = Minecraft.getInstance().getLanguageManager().getSelected().startsWith("ru");
        long tens = Math.abs(count) % 100;
        long units = Math.abs(count) % 10;
        String form;
        if (!russian) {
            form = Math.abs(count) == 1 ? "one" : "many";
        } else if (tens >= 11 && tens <= 14) {
            form = "many";
        } else if (units == 1) {
            form = "one";
        } else if (units >= 2 && units <= 4) {
            form = "few";
        } else {
            form = "many";
        }
        return Component.translatable(key + "." + form, shown).getString();
    }

    /** The number in full with its word: "4 400 монет". */
    static String full(String key, long count) {
        return of(key, count, Sidebar.full(count));
    }
}
