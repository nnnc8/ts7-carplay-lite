package io.ts7diag.tool;

import java.util.List;
import java.util.Map;

/** Small dependency-free JSON encoder for the API 21 target. */
public final class JsonEncoder {
    private JsonEncoder() {
    }

    public static String encode(Object value) {
        StringBuilder out = new StringBuilder();
        append(out, value, 0, false);
        return out.toString();
    }

    public static String encodePretty(Object value) {
        StringBuilder out = new StringBuilder();
        append(out, value, 0, true);
        return out.toString();
    }

    private static void append(StringBuilder out, Object value, int depth, boolean pretty) {
        if (value == null) {
            out.append("null");
            return;
        }
        if (value instanceof String || value instanceof Character) {
            appendString(out, String.valueOf(value));
            return;
        }
        if (value instanceof Boolean || value instanceof Integer || value instanceof Long
                || value instanceof Short || value instanceof Byte) {
            out.append(value);
            return;
        }
        if (value instanceof Float || value instanceof Double) {
            double number = ((Number) value).doubleValue();
            if (Double.isNaN(number) || Double.isInfinite(number)) {
                out.append("null");
            } else {
                out.append(value);
            }
            return;
        }
        if (value instanceof Map) {
            appendMap(out, (Map<?, ?>) value, depth, pretty);
            return;
        }
        if (value instanceof List) {
            appendList(out, (List<?>) value, depth, pretty);
            return;
        }
        appendString(out, String.valueOf(value));
    }

    private static void appendMap(StringBuilder out, Map<?, ?> map, int depth, boolean pretty) {
        out.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String)) {
                continue;
            }
            if (!first) {
                out.append(',');
            }
            first = false;
            newlineAndIndent(out, depth + 1, pretty);
            appendString(out, (String) entry.getKey());
            out.append(pretty ? ": " : ":");
            append(out, entry.getValue(), depth + 1, pretty);
        }
        newlineAndIndent(out, depth, pretty && !first);
        out.append('}');
    }

    private static void appendList(StringBuilder out, List<?> list, int depth, boolean pretty) {
        out.append('[');
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            newlineAndIndent(out, depth + 1, pretty);
            append(out, list.get(i), depth + 1, pretty);
        }
        newlineAndIndent(out, depth, pretty && !list.isEmpty());
        out.append(']');
    }

    private static void newlineAndIndent(StringBuilder out, int depth, boolean pretty) {
        if (!pretty) {
            return;
        }
        out.append('\n');
        for (int i = 0; i < depth * 2; i++) {
            out.append(' ');
        }
    }

    private static void appendString(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\b':
                    out.append("\\b");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        String hex = Integer.toHexString(c);
                        out.append("\\u0000".substring(0, 6 - hex.length())).append(hex);
                    } else {
                        out.append(c);
                    }
            }
        }
        out.append('"');
    }
}
