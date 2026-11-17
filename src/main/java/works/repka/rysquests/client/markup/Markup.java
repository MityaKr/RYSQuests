package works.repka.rysquests.client.markup;

import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses quest text: {@code [[kind:target|label]]} links as in RYScope, {@code **bold**}, {@code __italic__},
 * {@code !!warning!!} blocks and {@code >>spoiler<<}. Works on plain strings, so it can be tested without a client.
 *
 * <p>A web address is a link of kind {@code url} written three ways: {@code [[url:https://discord.gg/x|discord.gg/x]]},
 * {@code [Discord](https://discord.gg/x)} as in Markdown, or bare {@code https://discord.gg/x}. Only http and https open.
 */
public final class Markup {
    private static final Pattern LINK = Pattern.compile("\\[\\[([a-z_]+):([^\\]|]+)(?:\\|([^\\]]+))?]]");
    private static final Pattern INLINE = Pattern.compile("\\*\\*(.+?)\\*\\*|__(.+?)__|>>(.+?)<<");
    private static final String WARNING = "!!";
    /** A Markdown link to a web address, or a bare one; a bare one ends before a space or a closing bracket or quote. */
    private static final Pattern WEB = Pattern.compile("\\[([^\\[\\]]+)]\\((https?://[^\\s()]+)\\)|(https?://[^\\s<>\"«»()\\[\\]]+)");
    /** What a sentence puts after an address rather than in it. */
    private static final String TRAILING = ".,;:!?'";
    public static final String URL = "url";

    /** @param kind empty for plain text, otherwise the link kind; @param target link target */
    public record Span(String text, String kind, String target, boolean bold, boolean italic, boolean spoiler) {
        static Span plain(String text, boolean bold, boolean italic, boolean spoiler) {
            return new Span(text, "", "", bold, italic, spoiler);
        }

        public boolean isLink() {
            return !kind.isEmpty();
        }
    }

    public record Paragraph(List<Span> spans, boolean warning) {
    }

    private Markup() {
    }

    public static List<Paragraph> parse(String text) {
        List<Paragraph> paragraphs = new ArrayList<>();
        for (String line : text.split("\n", -1)) {
            String trimmed = line.trim();
            boolean warning = trimmed.length() > 4 && trimmed.startsWith(WARNING) && trimmed.endsWith(WARNING);
            String body = warning ? trimmed.substring(2, trimmed.length() - 2) : line;
            paragraphs.add(new Paragraph(inline(body), warning));
        }
        return paragraphs;
    }

    /** The item of a {@code [[recipe:]]} target, which may name a viewer's category: {@code create:brass_ingot#create:mixing}. */
    public static String recipeItem(String target) {
        int mark = target.indexOf('#');
        return mark < 0 ? target : target.substring(0, mark);
    }

    /** @return the category a {@code [[recipe:]]} target names, or null */
    public static ResourceLocation recipeCategory(String target) {
        int mark = target.indexOf('#');
        return mark < 0 ? null : ResourceLocation.tryParse(target.substring(mark + 1));
    }

    /** The insertion that fills a whole line, such as a picture. @return null for ordinary text */
    public static Span block(Paragraph paragraph) {
        Span only = null;
        for (Span span : paragraph.spans()) {
            if (span.isLink() || !span.text().isBlank()) {
                if (only != null) {
                    return null;
                }
                only = span;
            }
        }
        boolean block = only != null && !paragraph.warning() && (only.kind().equals("image") || only.kind().equals("recipe") || only.kind().equals("structure"));
        return block ? only : null;
    }

    private static List<Span> inline(String text) {
        List<Span> spans = new ArrayList<>();
        Matcher matcher = INLINE.matcher(text);
        int end = 0;
        while (matcher.find()) {
            links(text.substring(end, matcher.start()), false, false, false, spans);
            if (matcher.group(1) != null) {
                links(matcher.group(1), true, false, false, spans);
            } else if (matcher.group(2) != null) {
                links(matcher.group(2), false, true, false, spans);
            } else {
                links(matcher.group(3), false, false, true, spans);
            }
            end = matcher.end();
        }
        links(text.substring(end), false, false, false, spans);
        return spans;
    }

    private static void links(String text, boolean bold, boolean italic, boolean spoiler, List<Span> out) {
        Matcher matcher = LINK.matcher(text);
        int end = 0;
        while (matcher.find()) {
            addText(text.substring(end, matcher.start()), bold, italic, spoiler, out);
            String label = matcher.group(3) == null ? "" : matcher.group(3);
            out.add(new Span(label, matcher.group(1), matcher.group(2), bold, italic, spoiler));
            end = matcher.end();
        }
        addText(text.substring(end), bold, italic, spoiler, out);
    }

    /** Plain text with the web addresses in it made links: Markdown {@code [label](url)} and bare ones. */
    private static void addText(String text, boolean bold, boolean italic, boolean spoiler, List<Span> out) {
        Matcher matcher = WEB.matcher(text);
        int end = 0;
        while (matcher.find()) {
            String url = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
            int stop = matcher.end();
            if (matcher.group(3) != null) {
                while (!url.isEmpty() && TRAILING.indexOf(url.charAt(url.length() - 1)) >= 0) {
                    url = url.substring(0, url.length() - 1);
                    stop--;
                }
            }
            if (!isWebUrl(url)) {
                continue;
            }
            plain(text.substring(end, matcher.start()), bold, italic, spoiler, out);
            out.add(new Span(matcher.group(1) != null ? matcher.group(1) : "", URL, url, bold, italic, spoiler));
            end = stop;
        }
        plain(text.substring(end), bold, italic, spoiler, out);
    }

    private static void plain(String text, boolean bold, boolean italic, boolean spoiler, List<Span> out) {
        if (!text.isEmpty()) {
            out.add(Span.plain(text, bold, italic, spoiler));
        }
    }

    /** Whether an address may be opened from a page: http or https with a host, as the game's chat allows. */
    public static boolean isWebUrl(String url) {
        try {
            java.net.URI uri = new java.net.URI(url);
            String scheme = uri.getScheme();
            return scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")) && uri.getHost() != null;
        } catch (java.net.URISyntaxException notAnAddress) {
            return false;
        }
    }

    /** How an address with no label reads on the page: without its scheme and a lone closing slash ({@code discord.gg/x}). */
    public static String shortUrl(String url) {
        String bare = url.replaceFirst("(?i)^https?://", "");
        return bare.endsWith("/") && bare.indexOf('/') == bare.length() - 1 ? bare.substring(0, bare.length() - 1) : bare;
    }
}
