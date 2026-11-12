package works.repka.rysquests.progress;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Placeholders in reward commands, written as FTB Quests writes them: {@code {p}} for the player's name, {@code {x}},
 * {@code {y}}, {@code {z}} for where they stand, {@code {quest}}, {@code {chapter}}, {@code {team}}, {@code {member_count}}
 * and {@code {online_member_count}}. An unknown name stays as it is, so JSON text in a command is left alone.
 */
public final class CommandPlaceholders {
    private static final Pattern NAME = Pattern.compile("\\{(\\w+)}");

    private CommandPlaceholders() {
    }

    /** @param values a placeholder's value, or null to leave it */
    public static String fill(String command, Function<String, String> values) {
        Matcher matcher = NAME.matcher(command);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String value = values.apply(matcher.group(1));
            matcher.appendReplacement(out, Matcher.quoteReplacement(value == null ? matcher.group() : value));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
