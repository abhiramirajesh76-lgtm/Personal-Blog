package com.blog;

import java.util.*;

/**
 * A small, dependency-free JSON reader/writer. Just enough to handle the
 * flat objects and arrays this app needs — no external library required.
 */
public final class Json {

    private Json() {}

    // ---------------- Writing ----------------

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(value, sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(Object value, StringBuilder sb) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            writeString(s, sb);
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value.toString());
        } else if (value instanceof Map) {
            writeObject((Map<String, Object>) value, sb);
        } else if (value instanceof List) {
            writeArray((List<Object>) value, sb);
        } else {
            writeString(value.toString(), sb);
        }
    }

    private static void writeObject(Map<String, Object> map, StringBuilder sb) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            writeString(e.getKey(), sb);
            sb.append(':');
            writeValue(e.getValue(), sb);
        }
        sb.append('}');
    }

    private static void writeArray(List<Object> list, StringBuilder sb) {
        sb.append('[');
        boolean first = true;
        for (Object o : list) {
            if (!first) sb.append(',');
            first = false;
            writeValue(o, sb);
        }
        sb.append(']');
    }

    private static void writeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    public static Map<String, Object> obj(Object... kv) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }

    // ---------------- Parsing ----------------

    public static Object parse(String text) {
        Parser p = new Parser(text);
        Object v = p.parseValue();
        p.skipWhitespace();
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object v = parse(text);
        if (v instanceof Map) return (Map<String, Object>) v;
        throw new IllegalArgumentException("Expected a JSON object");
    }

    private static final class Parser {
        private final String s;
        private int i = 0;

        Parser(String s) { this.s = s; }

        void skipWhitespace() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        Object parseValue() {
            skipWhitespace();
            if (i >= s.length()) throw new IllegalArgumentException("Unexpected end of JSON");
            char c = s.charAt(i);
            return switch (c) {
                case '{' -> parseObjectInternal();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't', 'f' -> parseBoolean();
                case 'n' -> parseNull();
                default -> parseNumber();
            };
        }

        Map<String, Object> parseObjectInternal() {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            expect('{');
            skipWhitespace();
            if (peek() == '}') { i++; return map; }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                map.put(key, value);
                skipWhitespace();
                char n = peek();
                if (n == ',') { i++; continue; }
                if (n == '}') { i++; break; }
                throw new IllegalArgumentException("Malformed object at position " + i);
            }
            return map;
        }

        List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            expect('[');
            skipWhitespace();
            if (peek() == ']') { i++; return list; }
            while (true) {
                Object value = parseValue();
                list.add(value);
                skipWhitespace();
                char n = peek();
                if (n == ',') { i++; continue; }
                if (n == ']') { i++; break; }
                throw new IllegalArgumentException("Malformed array at position " + i);
            }
            return list;
        }

        String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') break;
                if (c == '\\') {
                    char esc = s.charAt(i++);
                    switch (esc) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> {
                            String hex = s.substring(i, i + 4);
                            sb.append((char) Integer.parseInt(hex, 16));
                            i += 4;
                        }
                        default -> sb.append(esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        Boolean parseBoolean() {
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            throw new IllegalArgumentException("Invalid literal at position " + i);
        }

        Object parseNull() {
            if (s.startsWith("null", i)) { i += 4; return null; }
            throw new IllegalArgumentException("Invalid literal at position " + i);
        }

        Number parseNumber() {
            int start = i;
            if (peek() == '-') i++;
            while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
            boolean isDouble = false;
            if (i < s.length() && s.charAt(i) == '.') {
                isDouble = true;
                i++;
                while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
            }
            if (i < s.length() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
                isDouble = true;
                i++;
                if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) i++;
                while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
            }
            String num = s.substring(start, i);
            if (num.isEmpty() || "-".equals(num)) throw new IllegalArgumentException("Invalid number at position " + start);
            return isDouble ? Double.parseDouble(num) : (Number) Long.parseLong(num);
        }

        char peek() {
            if (i >= s.length()) throw new IllegalArgumentException("Unexpected end of JSON");
            return s.charAt(i);
        }

        void expect(char c) {
            if (i >= s.length() || s.charAt(i) != c) {
                throw new IllegalArgumentException("Expected '" + c + "' at position " + i);
            }
            i++;
        }
    }

    public static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : v.toString();
    }

    public static long longVal(Object o) {
        if (o instanceof Number n) return n.longValue();
        return Long.parseLong(o.toString());
    }
}
