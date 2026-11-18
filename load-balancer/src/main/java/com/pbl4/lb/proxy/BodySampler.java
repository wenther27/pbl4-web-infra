package com.pbl4.lb.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class BodySampler {

    static final int MAX_BODY_BYTES = 16 * 1024;
    static final int MAX_SAMPLE_CHARS = 1000;
    private static final Set<String> METHODS = Set.of("POST", "PUT", "PATCH");
    private static final Pattern SENSITIVE = Pattern.compile(
            "pass|pwd|secret|token|otp|card|cvv|authorization|api[_-]?key", Pattern.CASE_INSENSITIVE);
    private static final ObjectMapper JSON = new ObjectMapper();

    private BodySampler() {
    }

    public static String sample(String method, String contentType, byte[] body) {
        if (body == null || body.length == 0 || body.length > MAX_BODY_BYTES || method == null
                || contentType == null || !METHODS.contains(method.toUpperCase(Locale.ROOT))) {
            return null;
        }
        String type = contentType.toLowerCase(Locale.ROOT);
        List<String> pairs = new ArrayList<>();
        try {
            if (type.startsWith("application/json")) {
                collect("", JSON.readTree(body), pairs);
            } else if (type.startsWith("application/x-www-form-urlencoded")) {
                for (String part : new String(body, StandardCharsets.UTF_8).split("&")) {
                    if (part.isEmpty()) {
                        continue;
                    }
                    int eq = part.indexOf('=');
                    String key = URLDecoder.decode(eq < 0 ? part : part.substring(0, eq), StandardCharsets.UTF_8);
                    String value = eq < 0 ? "" : URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
                    add(key, value, pairs);
                }
            } else {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
        if (pairs.isEmpty()) {
            return null;
        }
        String joined = String.join("&", pairs);
        return joined.length() > MAX_SAMPLE_CHARS ? joined.substring(0, MAX_SAMPLE_CHARS) : joined;
    }

    private static void collect(String key, JsonNode node, List<String> out) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                collect(key.isEmpty() ? field.getKey() : key + "." + field.getKey(), field.getValue(), out);
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                collect(key, item, out);
            }
        } else {
            add(key, node.asText(), out);
        }
    }

    private static void add(String key, String value, List<String> out) {
        if (SENSITIVE.matcher(key).find()) {
            return;
        }
        out.add(URLEncoder.encode(key, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8));
    }
}
