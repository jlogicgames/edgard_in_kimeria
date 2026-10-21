package com.jlogicsoftware.kimeria.localization;

import java.util.LinkedHashMap;
import java.util.Map;

/** Strict flat JSON object reader shared by the game, validator and font baker. No reflection or platform IO. */
public final class Catalog {
    private Catalog() { }

    public static Map<String, String> parse(String language, String source) {
        return new Reader(language, source).read();
    }

    /** Placeholder names and occurrence counts must agree across translations. */
    public static Map<String, Integer> placeholders(String text) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '}') throw new IllegalArgumentException("Unmatched closing placeholder brace");
            if (c != '{') continue;
            int end = text.indexOf('}', i + 1);
            if (end < 0) throw new IllegalArgumentException("Unclosed placeholder");
            String name = text.substring(i + 1, end);
            if (!name.matches("[a-zA-Z_][a-zA-Z_0-9]*"))
                throw new IllegalArgumentException("Invalid placeholder {" + name + "}");
            result.put(name, result.getOrDefault(name, 0) + 1);
            i = end;
        }
        return result;
    }

    public static String format(String text, String... arguments) {
        if (arguments.length % 2 != 0) throw new IllegalArgumentException("Expected placeholder/value pairs");
        Map<String, Integer> expected = placeholders(text);
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < arguments.length; i += 2) {
            if (arguments[i] == null || arguments[i + 1] == null || values.containsKey(arguments[i]))
                throw new IllegalArgumentException("Null or duplicate placeholder argument");
            values.put(arguments[i], arguments[i + 1]);
        }
        if (!values.keySet().equals(expected.keySet()))
            throw new IllegalArgumentException("Expected placeholders " + expected.keySet() + ", got " + values.keySet());
        // Append substituted values once; braces inside values must never be interpreted again.
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) != '{') { result.append(text.charAt(i)); continue; }
            int end = text.indexOf('}', i);
            result.append(values.get(text.substring(i + 1, end)));
            i = end;
        }
        return result.toString();
    }

    private static final class Reader {
        private final String language, source;
        private int position;
        private String key = "<resource>";

        Reader(String language, String source) { this.language = language; this.source = source; }

        Map<String, String> read() {
            Map<String, String> result = new LinkedHashMap<>();
            whitespace(); expect('{'); whitespace();
            if (!take('}')) {
                do {
                    whitespace(); key = "<key>"; key = string();
                    if (result.containsKey(key)) fail("Duplicate key");
                    whitespace(); expect(':'); whitespace();
                    result.put(key, string()); whitespace();
                    if (take('}')) break;
                    expect(',');
                } while (true);
            }
            whitespace();
            if (position != source.length()) fail("Unexpected trailing content");
            return result;
        }

        private String string() {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (position < source.length()) {
                char c = source.charAt(position++);
                if (c == '"') return value.toString();
                if (c < 32) fail("Unescaped control character");
                if (c != '\\') { value.append(c); continue; }
                if (position == source.length()) fail("Unfinished escape");
                c = source.charAt(position++);
                switch (c) {
                    case '"', '\\', '/' -> value.append(c);
                    case 'b' -> value.append('\b');
                    case 'f' -> value.append('\f');
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    case 'u' -> {
                        if (position + 4 > source.length()) fail("Incomplete Unicode escape");
                        int code = 0;
                        for (int i = 0; i < 4; i++) {
                            char digit = source.charAt(position++);
                            int hex = digit >= '0' && digit <= '9' ? digit - '0'
                                : digit >= 'a' && digit <= 'f' ? digit - 'a' + 10
                                : digit >= 'A' && digit <= 'F' ? digit - 'A' + 10 : -1;
                            if (hex < 0) fail("Invalid Unicode escape");
                            code = code * 16 + hex;
                        }
                        value.append((char) code);
                    }
                    default -> fail("Invalid escape");
                }
            }
            fail("Unterminated string");
            return ""; // fail always throws
        }

        private void whitespace() {
            while (position < source.length() && " \n\r\t".indexOf(source.charAt(position)) >= 0) position++;
        }
        private boolean take(char c) {
            if (position < source.length() && source.charAt(position) == c) { position++; return true; }
            return false;
        }
        private void expect(char c) { if (!take(c)) fail("Expected '" + c + "'"); }
        private void fail(String message) {
            int line = 1, column = 1;
            for (int i = 0; i < position; i++) {
                if (source.charAt(i) == '\n') { line++; column = 1; } else column++;
            }
            throw new IllegalArgumentException(language + " [" + key + "] at " + line + ":" + column + ": " + message);
        }
    }
}
