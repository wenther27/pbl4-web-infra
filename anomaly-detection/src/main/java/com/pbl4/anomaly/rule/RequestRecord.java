package com.pbl4.anomaly.rule;

public record RequestRecord(
        String timestamp,
        String clientIp,
        String path,
        String query,
        String userAgent,
        int status,
        String body
) {
    public RequestRecord(String timestamp, String clientIp, String path, String query, String userAgent, int status) {
        this(timestamp, clientIp, path, query, userAgent, status, "");
    }

    public String payload() {
        String q = query == null ? "" : query;
        String b = body == null ? "" : body;
        return q.isEmpty() ? b : b.isEmpty() ? q : q + "&" + b;
    }

    public String display() {
        String p = path == null ? "" : path;
        String q = query == null ? "" : query;
        String b = body == null ? "" : body;
        return p + (q.isEmpty() ? "" : "?" + q) + (b.isEmpty() ? "" : " [body] " + b);
    }
}
