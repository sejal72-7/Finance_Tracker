package com.financetracker.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Zero-dependency JSON serializer, parser, and HTTP servlet response helper.
 */
public final class JsonUtil {

    private JsonUtil() {
    }

    public static void sendJson(HttpServletResponse resp, int status, Object payload) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        PrintWriter out = resp.getWriter();
        out.write(toJson(payload));
        out.flush();
    }

    public static void sendError(HttpServletResponse resp, int status, String message) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error", message != null ? message : "Request failed");
        sendJson(resp, status, body);
    }

    /**
     * Reads JSON payload from the request body, or falls back to URL-encoded form parameters.
     */
    public static Map<String, Object> readRequestData(HttpServletRequest req) throws IOException {
        String contentType = req.getContentType();
        String body = readBody(req);
        if ((contentType != null && contentType.toLowerCase().contains("application/json"))
                || (body != null && body.trim().startsWith("{"))) {
            if (body == null || body.isBlank()) {
                return new LinkedHashMap<>();
            }
            return parseObject(body);
        }

        Map<String, Object> params = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> entry : req.getParameterMap().entrySet()) {
            String[] values = entry.getValue();
            if (values != null && values.length > 0) {
                params.put(entry.getKey(), values[0]);
            }
        }
        return params;
    }

    public static String getString(Map<String, Object> data, String key) {
        Object val = data.get(key);
        return val == null ? null : val.toString();
    }

    public static Integer getInt(Map<String, Object> data, String key) {
        Object val = data.get(key);
        if (val == null) {
            return null;
        }
        if (val instanceof Number num) {
            return num.intValue();
        }
        return ValidationUtil.parsePositiveInt(val.toString());
    }

    private static String readBody(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(line);
            }
        }
        return sb.toString();
    }

    public static String toJson(Object value) {
        StringBuilder sb = new StringBuilder();
        appendJson(sb, value);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void appendJson(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Boolean || value instanceof Integer || value instanceof Long) {
            sb.append(value.toString());
        } else if (value instanceof BigDecimal bd) {
            sb.append(bd.toPlainString());
        } else if (value instanceof Number num) {
            double d = num.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                sb.append("0");
            } else {
                sb.append(num.toString());
            }
        } else if (value instanceof LocalDate || value instanceof Instant || value instanceof Enum<?>) {
            appendEscapedString(sb, value.toString());
        } else if (value instanceof CharSequence cs) {
            appendEscapedString(sb, cs.toString());
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                appendEscapedString(sb, String.valueOf(entry.getKey()));
                sb.append(':');
                appendJson(sb, entry.getValue());
            }
            sb.append('}');
        } else if (value instanceof Collection<?> col) {
            sb.append('[');
            boolean first = true;
            for (Object item : col) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                appendJson(sb, item);
            }
            sb.append(']');
        } else {
            appendEscapedString(sb, value.toString());
        }
    }

    private static void appendEscapedString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
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

    /**
     * Parses a JSON object string into a LinkedHashMap.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Empty JSON input");
        }
        Parser parser = new Parser(json);
        Object parsed = parser.parseValue();
        parser.skipWhitespace();
        if (!parser.isAtEnd()) {
            throw new IllegalArgumentException("Unexpected trailing JSON content");
        }
        if (!(parsed instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("Expected a JSON object");
        }
        return (Map<String, Object>) parsed;
    }

    private static final class Parser {
        private final String src;
        private int pos = 0;

        Parser(String src) {
            this.src = src;
        }

        boolean isAtEnd() {
            return pos >= src.length();
        }

        void skipWhitespace() {
            while (!isAtEnd()) {
                char c = src.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        Object parseValue() {
            skipWhitespace();
            if (isAtEnd()) {
                throw new IllegalArgumentException("Unexpected end of JSON");
            }
            char c = src.charAt(pos);
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> {
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        yield parseNumber();
                    }
                    throw new IllegalArgumentException("Unexpected JSON character at position " + pos);
                }
            };
        }

        private Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (peek('}')) {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object val = parseValue();
                map.put(key, val);
                skipWhitespace();
                if (peek(',')) {
                    pos++;
                } else if (peek('}')) {
                    pos++;
                    return map;
                } else {
                    throw new IllegalArgumentException("Expected ',' or '}' at position " + pos);
                }
            }
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (peek(']')) {
                pos++;
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWhitespace();
                if (peek(',')) {
                    pos++;
                } else if (peek(']')) {
                    pos++;
                    return list;
                } else {
                    throw new IllegalArgumentException("Expected ',' or ']' at position " + pos);
                }
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (!isAtEnd()) {
                char c = src.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (isAtEnd()) {
                        throw new IllegalArgumentException("Unterminated escape sequence");
                    }
                    char esc = src.charAt(pos++);
                    switch (esc) {
                        case '"', '\\', '/' -> sb.append(esc);
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (pos + 4 > src.length()) {
                                throw new IllegalArgumentException("Invalid unicode escape");
                            }
                            String hex = src.substring(pos, pos + 4);
                            pos += 4;
                            sb.append((char) Integer.parseInt(hex, 16));
                        }
                        default -> throw new IllegalArgumentException("Invalid escape character: \\" + esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("Unterminated JSON string");
        }

        private Object parseLiteral(String literal, Object value) {
            if (!src.startsWith(literal, pos)) {
                throw new IllegalArgumentException("Invalid literal at position " + pos);
            }
            pos += literal.length();
            return value;
        }

        private BigDecimal parseNumber() {
            int start = pos;
            if (src.charAt(pos) == '-') {
                pos++;
            }
            while (!isAtEnd() && Character.isDigit(src.charAt(pos))) {
                pos++;
            }
            if (!isAtEnd() && src.charAt(pos) == '.') {
                pos++;
                if (isAtEnd() || !Character.isDigit(src.charAt(pos))) {
                    throw new IllegalArgumentException("Invalid decimal number at position " + start);
                }
                while (!isAtEnd() && Character.isDigit(src.charAt(pos))) {
                    pos++;
                }
            }
            if (!isAtEnd() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
                pos++;
                if (!isAtEnd() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) {
                    pos++;
                }
                if (isAtEnd() || !Character.isDigit(src.charAt(pos))) {
                    throw new IllegalArgumentException("Invalid exponent at position " + start);
                }
                while (!isAtEnd() && Character.isDigit(src.charAt(pos))) {
                    pos++;
                }
            }
            String numStr = src.substring(start, pos);
            try {
                return new BigDecimal(numStr);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid number: " + numStr);
            }
        }

        private boolean peek(char expected) {
            return !isAtEnd() && src.charAt(pos) == expected;
        }

        private void expect(char expected) {
            if (isAtEnd() || src.charAt(pos) != expected) {
                throw new IllegalArgumentException("Expected '" + expected + "' at position " + pos);
            }
            pos++;
        }
    }
}

