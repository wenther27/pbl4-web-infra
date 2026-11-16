package com.pbl4.lb.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pbl4.lb.health.BackendMaintenance;
import com.pbl4.lb.health.HealthChecker;
import com.pbl4.lb.strategy.LoadBalancingStrategy;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class BackendAdminHandler implements HttpHandler {

    private static final String PREFIX = "/admin/backends";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final List<String> backends;
    private final HealthChecker health;
    private final BackendMaintenance maintenance;
    private final LoadBalancingStrategy strategy;
    private final byte[] token;

    public BackendAdminHandler(List<String> backends, HealthChecker health,
                               BackendMaintenance maintenance, String token) {
        this(backends, health, maintenance, null, token);
    }

    public BackendAdminHandler(List<String> backends, HealthChecker health, BackendMaintenance maintenance,
                               LoadBalancingStrategy strategy, String token) {
        this.backends = backends;
        this.health = health;
        this.maintenance = maintenance;
        this.strategy = strategy;
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
            String path = ex.getRequestURI().getPath();
            String by = Optional.ofNullable(ex.getRequestHeaders().getFirst("X-Admin-User")).orElse("admin");

            if (path.equals(PREFIX) && method.equals("GET")) {
                Map<String, Map<String, Object>> stats = strategy == null ? Map.of() : strategy.snapshot(backends);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("strategy", strategy == null ? null : strategy.name());
                body.put("timestamp", System.currentTimeMillis());
                body.put("backends", backends.stream().map(b -> {
                    Map<String, Object> m = status(b);
                    m.putAll(stats.getOrDefault(b, Map.of()));
                    return m;
                }).toList());
                send(ex, 200, body);
                return;
            }

            String[] parts = path.substring(Math.min(path.length(), PREFIX.length())).split("/");
            if (parts.length == 3 && method.equals("POST")
                    && (parts[2].equals("drain") || parts[2].equals("undrain"))) {
                Optional<String> backend = BackendMaintenance.resolve(parts[1], backends);
                if (backend.isEmpty()) {
                    send(ex, 404, Map.of("error", "Khong co backend " + parts[1]));
                    return;
                }
                String b = backend.get();
                if (parts[2].equals("drain")) {
                    List<String> serving = health.healthyBackends().stream()
                            .filter(x -> !x.equals(b) && !maintenance.isDraining(x)).toList();
                    if (serving.isEmpty()) {
                        send(ex, 409, Map.of("error", "Khong the drain " + BackendMaintenance.nameOf(b)
                                + ": khong con backend nao khac dang phuc vu"));
                        return;
                    }
                    maintenance.drain(b, by);
                } else {
                    maintenance.undrain(b, by);
                }
                send(ex, 200, status(b));
                return;
            }

            send(ex, 404, Map.of("error", "Khong co API " + method + " " + path));
        } finally {
            ex.close();
        }
    }

    private Map<String, Object> status(String b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", BackendMaintenance.nameOf(b));
        m.put("url", b);
        m.put("up", health.healthyBackends().contains(b));
        m.put("draining", maintenance.isDraining(b));
        m.put("inFlight", maintenance.inFlight(b));
        m.put("served", maintenance.served(b));
        return m;
    }

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