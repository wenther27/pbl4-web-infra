package com.pbl4.lb.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pbl4.lb.security.RateLimiter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;

// API gioi han toc do (cung cong 8090, cung X-Admin-Token):
//   GET  /admin/ratelimit   -> cau hinh hien tai + top IP bi tra 429 nhieu nhat
//   POST /admin/ratelimit   -> doi nguong luc dang chay  body: {"ratePerSecond":10,"burst":20}
//                              ratePerSecond = 0 -> tat gioi han
public class RateLimitAdminHandler implements HttpHandler {

    private static final String PATH = "/admin/ratelimit";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final RateLimiter limiter;
    private final byte[] token;

    public RateLimitAdminHandler(RateLimiter limiter, String token) {
        this.limiter = limiter;
        this.token = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            if (!authorized(ex)) {
                send(ex, 401, Map.of("error", "Thieu hoac sai X-Admin-Token"));
                return;
            }
            String method = ex.getRequestMethod();
            if (!ex.getRequestURI().getPath().equals(PATH)) {
                send(ex, 404, Map.of("error", "Khong co API " + method + " " + ex.getRequestURI().getPath()));
                return;
            }

            if (method.equals("GET")) {
                send(ex, 200, limiter.snapshot());
                return;
            }

            if (method.equals("POST")) {
                JsonNode body;
                try {
                    body = JSON.readTree(ex.getRequestBody().readNBytes(4096));
                } catch (IOException e) {
                    send(ex, 400, Map.of("error", "Body phai la JSON"));
                    return;
                }
                if (body == null || !body.hasNonNull("ratePerSecond") || !body.get("ratePerSecond").isNumber()) {
                    send(ex, 400, Map.of("error", "Thieu ratePerSecond (so)"));
                    return;
                }
                double rps = body.get("ratePerSecond").asDouble();
                // Khong gui burst -> giu burst hien tai
                int burst = body.hasNonNull("burst") ? body.get("burst").asInt() : limiter.limits().burst();
                String by = Optional.ofNullable(ex.getRequestHeaders().getFirst("X-Admin-User")).orElse("admin");
                try {
                    limiter.setLimits(rps, burst, by);
                } catch (IllegalArgumentException e) {
                    send(ex, 400, Map.of("error", e.getMessage()));
                    return;
                }
                send(ex, 200, limiter.snapshot());
                return;
            }

            send(ex, 405, Map.of("error", "Chi ho tro GET va POST"));
        } finally {
            ex.close();
        }
    }

    // Giong AdminHandler: so sanh token thoi gian hang so
    private boolean authorized(HttpExchange ex) {
        String given = ex.getRequestHeaders().getFirst("X-Admin-Token");
        return given != null && MessageDigest.isEqual(token, given.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange ex, int status, Object body) throws IOException {
        byte[] bytes = JSON.writeValueAsBytes(body);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
