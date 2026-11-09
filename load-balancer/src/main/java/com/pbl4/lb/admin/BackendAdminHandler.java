package com.pbl4.lb.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pbl4.lb.health.BackendMaintenance;
import com.pbl4.lb.health.HealthChecker;
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

// API bao tri backend (cung cong 8090, cung X-Admin-Token voi API chan IP):
//   GET  /admin/backends                 -> trang thai tung backend: up, draining, inFlight
//   POST /admin/backends/{ten}/drain     -> ngung gui request moi vao backend (vd ten = web-1)
//   POST /admin/backends/{ten}/undrain   -> nhan request tro lai
public class BackendAdminHandler implements HttpHandler {

    private static final String PREFIX = "/admin/backends";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final List<String> backends;
    private final HealthChecker health;
    private final BackendMaintenance maintenance;
    private final byte[] token;

    public BackendAdminHandler(List<String> backends, HealthChecker health,
                               BackendMaintenance maintenance, String token) {
        this.backends = backends;
        this.health = health;
        this.maintenance = maintenance;
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
                send(ex, 200, Map.of("backends", backends.stream().map(this::status).toList()));
                return;
            }

            // /admin/backends/{ten}/drain | /undrain
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
                    // Khong cho drain backend cuoi cung dang phuc vu -> tranh tu tay lam sap he thong
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
        return m;
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