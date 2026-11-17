package works.repka.rysquests.integration.ftbq;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.regex.Pattern;

/**
 * Reads the relaxed SNBT that FTB Library writes: commas are optional and keys may be bare.
 * The game's own tag parser requires commas. Values become Gson elements, which the importer needs anyway.
 */
final class Snbt {
    private static final Pattern INTEGER = Pattern.compile("-?\\d+[bBsSlL]?");
    private static final Pattern DECIMAL = Pattern.compile("-?(\\d+\\.?\\d*|\\.\\d+)([eE][-+]?\\d+)?[fFdD]?");

    private final String text;
    private int pos;

    private Snbt(String text) {
        this.text = text;
    }

    static JsonElement parse(String text) {
        Snbt parser = new Snbt(text);
        JsonElement value = parser.value();
        parser.skipSeparators();
        if (parser.pos < text.length()) {
            throw parser.error("trailing content");
        }
        return value;
    }

    private JsonElement value() {
        skipSeparators();
        if (pos >= text.length()) {
            throw error("unexpected end");
        }
        char c = text.charAt(pos);
        return switch (c) {
            case '{' -> object();
            case '[' -> array();
            case '"', '\'' -> new JsonPrimitive(quoted());
            default -> literal(bare());
        };
    }

    private JsonObject object() {
        pos++;
        JsonObject object = new JsonObject();
        while (true) {
            skipSeparators();
            if (pos >= text.length()) {
                throw error("unclosed object");
            }
            if (text.charAt(pos) == '}') {
                pos++;
                return object;
            }
            char c = text.charAt(pos);
            String key = c == '"' || c == '\'' ? quoted() : bare();
            skipSeparators();
            if (pos >= text.length() || text.charAt(pos) != ':') {
                throw error("expected ':' after key " + key);
            }
            pos++;
            object.add(key, value());
        }
    }

    private JsonArray array() {
        pos++;
        if (pos + 1 < text.length() && text.charAt(pos + 1) == ';' && "BIL".indexOf(text.charAt(pos)) >= 0) {
            pos += 2;
        }
        JsonArray array = new JsonArray();
        while (true) {
            skipSeparators();
            if (pos >= text.length()) {
                throw error("unclosed array");
            }
            if (text.charAt(pos) == ']') {
                pos++;
                return array;
            }
            array.add(value());
        }
    }

    private String quoted() {
        char quote = text.charAt(pos++);
        StringBuilder out = new StringBuilder();
        while (pos < text.length()) {
            char c = text.charAt(pos++);
            if (c == quote) {
                return out.toString();
            }
            if (c == '\\' && pos < text.length()) {
                out.append(unescape(text.charAt(pos++)));
            } else {
                out.append(c);
            }
        }
        throw error("unclosed string");
    }

    private static char unescape(char c) {
        return switch (c) {
            case 'n' -> '\n';
            case 't' -> '\t';
            case 'r' -> '\r';
            default -> c;
        };
    }

    private String bare() {
        int start = pos;
        while (pos < text.length() && ":,{}[]".indexOf(text.charAt(pos)) < 0 && !Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
        if (pos == start) {
            throw error("expected a value");
        }
        return text.substring(start, pos);
    }

    private static JsonElement literal(String token) {
        if (token.equals("true") || token.equals("false")) {
            return new JsonPrimitive(Boolean.parseBoolean(token));
        }
        // NBT has no boolean type, 0b and 1b are how booleans are written, and JSON codecs reject numbers for them.
        if (token.equals("0b") || token.equals("1b")) {
            return new JsonPrimitive(token.equals("1b"));
        }
        if (INTEGER.matcher(token).matches()) {
            return new JsonPrimitive(Long.parseLong(stripSuffix(token)));
        }
        if (DECIMAL.matcher(token).matches()) {
            return new JsonPrimitive(Double.parseDouble(stripSuffix(token)));
        }
        return new JsonPrimitive(token);
    }

    private static String stripSuffix(String token) {
        return Character.isLetter(token.charAt(token.length() - 1)) ? token.substring(0, token.length() - 1) : token;
    }

    private void skipSeparators() {
        while (pos < text.length() && (Character.isWhitespace(text.charAt(pos)) || text.charAt(pos) == ',' || text.charAt(pos) == '﻿')) {
            pos++;
        }
    }

    private SyntaxError error(String message) {
        int line = 1;
        for (int i = 0; i < Math.min(pos, text.length()); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return new SyntaxError(message, line);
    }

    /** Where a file could not be read: the line, counted from 1, and what was wrong there. */
    static final class SyntaxError extends IllegalArgumentException {
        private final String reason;
        private final int line;

        SyntaxError(String reason, int line) {
            super("SNBT: " + reason + " at line " + line);
            this.reason = reason;
            this.line = line;
        }

        String reason() {
            return reason;
        }

        int line() {
            return line;
        }
    }
}
