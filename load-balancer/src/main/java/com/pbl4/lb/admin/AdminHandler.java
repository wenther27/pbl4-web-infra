package com.pbl4.lb.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pbl4.lb.security.BlockList;
import com.pbl4.lb.security.BlockList.Entry;
import com.pbl4.lb.security.BlockList.ProtectedIpException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// API quan tri cua LB, chay tren cong RIENG (LB_ADMIN_PORT, mac dinh 8090), khong qua ProxyHandler.
//   GET    /admin/blocked          -> danh sach IP dang bi chan
//   POST   /admin/blocked          -> chan IP   body: {"ip":"..","minutes":30,"reason":"..","by":".."}
//   DELETE /admin/blocked/{ip}     -> go chan
// Moi request phai co header X-Admin-Token dung bang LB_ADMIN_TOKEN.
public class AdminHandler implements HttpHandler {

    private static final String PREFIX = "/admin/blocked";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final BlockList blockList;
    private final byte[] token;

    public AdminHandler(BlockList blockList, String token) {
        this.blockList = blockList;
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

            if (path.equals(PREFIX) && method.equals("GET")) {
                List<Map<String, Object>> items = blockList.list().stream().map(AdminHandler::toJson).toList();
                send(ex, 200, Map.of("blocked", items, "neverBlock", blockList.neverBlock()));

            } else if (path.equals(PREFIX) && method.equals("POST")) {
                JsonNode body = JSON.readTree(ex.getRequestBody());
                Entry e = blockList.block(
                        body.path("ip").asText(""),
                        body.path("minutes").asInt(30),
                        body.path("reason").asText(""),
                        body.path("by").asText("admin"));
                send(ex, 201, toJson(e));

            } else if (path.startsWith(PREFIX + "/") && method.equals("DELETE")) {
                String ip = path.substring(PREFIX.length() + 1);
                String by = ex.getRequestHeaders().getFirst("X-Admin-User");
                if (blockList.unblock(ip, by)) {
                    send(ex, 200, Map.of("unblocked", ip));
                } else {
                    send(ex, 404, Map.of("error", "IP " + ip + " khong bi chan"));
                }

            } else {
                send(ex, 404, Map.of("error", "Khong co API " + method + " " + path));
            }
        } catch (ProtectedIpException e) {
            send(ex, 409, Map.of("error", e.getMessage()));     // 409: IP duoc bao ve
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            send(ex, 400, Map.of("error", "Body khong phai JSON hop le"));
        } catch (IllegalArgumentException e) {
            send(ex, 400, Map.of("error", e.getMessage()));     // 400: du lieu sai
        } finally {
            ex.close();
        }
    }

    // So sanh token bang MessageDigest.isEqual: thoi gian so sanh khong phu thuoc
    // vi tri ky tu sai dau tien -> khong doan token qua thoi gian phan hoi duoc
    private boolean authorized(HttpExchange ex) {
        String given = ex.getRequestHeaders().getFirst("X-Admin-Token");
        return given != null && MessageDigest.isEqual(token, given.getBytes(StandardCharsets.UTF_8));
    }

    // Instant -> chuoi ISO, khong can them module jackson-datatype-jsr310
    private static Map<String, Object> toJson(Entry e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ip", e.ip());
        m.put("since", e.since().toString());
        m.put("until", e.until().toString());
        m.put("reason", e.reason());
        m.put("by", e.by());
        return m;
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