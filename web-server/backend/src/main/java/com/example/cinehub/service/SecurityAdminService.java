package com.example.cinehub.service;

import com.example.cinehub.dto.request.BlockIpRequest;
import com.example.cinehub.dto.response.SuspiciousIpDTO;
import com.example.cinehub.exception.UpstreamServiceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Cau noi giua trang Admin va 2 he thong ben ngoai:
//  - Elasticsearch: doc canh bao (alerts-*) cua bo phat hien bat thuong
//  - Load Balancer: API quan tri cong 8090 de chan / go chan IP
@Service
public class SecurityAdminService {

    // Chi cac luat co doi tuong la IP (R4, R5, R7 la "global", R6 la backend)
    private static final List<String> IP_RULES = List.of("HIGH_RATE_IP", "SCAN_PATHS", "BRUTE_FORCE_LOGIN");

    private final RestClient es;
    private final RestClient lb;
    private final ObjectMapper json;

    public SecurityAdminService(@Value("${app.security.es-url}") String esUrl,
                                @Value("${app.security.lb-admin-url}") String lbAdminUrl,
                                @Value("${app.security.lb-admin-token}") String lbAdminToken,
                                ObjectMapper json) {
        // Timeout ngan: ES/LB cham thi bao loi nhanh, khong treo thread cua Tomcat
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(2000);
        timeouts.setReadTimeout(5000);

        this.es = RestClient.builder().baseUrl(esUrl).requestFactory(timeouts).build();
        this.lb = RestClient.builder().baseUrl(lbAdminUrl).requestFactory(timeouts)
                .defaultHeader("X-Admin-Token", lbAdminToken)
                .build();
        this.json = json;
    }

    // ------------------------------------------------------------------
    // 1. IP bi canh bao trong 'minutes' phut qua, gop theo IP, kem trang thai dang bi chan
    // ------------------------------------------------------------------
    public List<SuspiciousIpDTO> suspiciousIps(int minutes) {
        String query = """
                {
                  "size": 500,
                  "sort": [ { "@timestamp": "desc" } ],
                  "query": { "bool": { "filter": [
                    { "terms": { "rule": %s } },
                    { "range": { "@timestamp": { "gte": "now-%dm" } } }
                  ]}}
                }
                """.formatted(toJson(IP_RULES), minutes);

        JsonNode hits = callEs(query).path("hits").path("hits");
        Map<String, String> blockedUntil = blockedUntilByIp();

        // Gop cac canh bao theo IP. Ket qua da sap xep moi nhat truoc,
        // nen canh bao dau tien gap cua moi IP chinh la canh bao gan nhat.
        Map<String, Group> groups = new LinkedHashMap<>();
        for (JsonNode hit : hits) {
            JsonNode a = hit.path("_source");
            String ip = a.path("target").asText();
            Group g = groups.computeIfAbsent(ip, k -> new Group(a));
            g.count++;
            g.rules.add(a.path("rule").asText());
            if ("HIGH".equals(a.path("severity").asText())) {
                g.severity = "HIGH";
            }
        }

        List<SuspiciousIpDTO> result = new ArrayList<>();
        groups.forEach((ip, g) -> result.add(new SuspiciousIpDTO(
                ip, g.count, List.copyOf(g.rules), g.severity, g.lastSeen, g.lastMessage,
                blockedUntil.containsKey(ip), blockedUntil.get(ip))));
        return result;
    }

    // ------------------------------------------------------------------
    // 2. Danh sach IP dang bi chan tai LB (chuyen nguyen JSON cua LB ve)
    // ------------------------------------------------------------------
    public JsonNode blockedIps() {
        return callLb(() -> lb.get().uri("/admin/blocked").retrieve().body(String.class));
    }

    // ------------------------------------------------------------------
    // 3. Chan IP. 'admin' la ten tai khoan dang dang nhap -> LB ghi vao log (ai chan)
    // ------------------------------------------------------------------
    public JsonNode block(BlockIpRequest req, String admin) {
        Map<String, Object> body = new HashMap<>();
        body.put("ip", req.ip().trim());
        body.put("minutes", req.minutes());
        body.put("reason", req.reason() == null ? "" : req.reason());
        body.put("by", admin);
        return callLb(() -> lb.post().uri("/admin/blocked")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().body(String.class));
    }

    // ------------------------------------------------------------------
    // 4. Go chan IP
    // ------------------------------------------------------------------
    public JsonNode unblock(String ip, String admin) {
        return callLb(() -> lb.delete().uri("/admin/blocked/{ip}", ip)
                .header("X-Admin-User", admin)
                .retrieve().body(String.class));
    }

    // ================== ham phu ==================

    // ip -> thoi diem het han chan; LB loi thi coi nhu khong IP nao bi chan (van hien danh sach canh bao)
    private Map<String, String> blockedUntilByIp() {
        Map<String, String> map = new HashMap<>();
        try {
            for (JsonNode e : blockedIps().path("blocked")) {
                map.put(e.path("ip").asText(), e.path("until").asText());
            }
        } catch (UpstreamServiceException ignored) {
            // Trang van dung duoc khi LB tam thoi khong tra loi
        }
        return map;
    }

    private JsonNode callEs(String query) {
        try {
            String body = es.post().uri("/alerts-*/_search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(query)
                    .retrieve().body(String.class);
            return json.readTree(body);
        } catch (ResourceAccessException e) {
            throw new UpstreamServiceException(503, "Khong ket noi duoc Elasticsearch");
        } catch (RestClientResponseException e) {
            throw new UpstreamServiceException(502, "Elasticsearch tra loi loi " + e.getStatusCode().value());
        } catch (JsonProcessingException e) {
            throw new UpstreamServiceException(502, "Elasticsearch tra ve du lieu khong doc duoc");
        }
    }

    // Goi LB va doi loi cua LB thanh loi co nghia cho admin:
    //   400 / 404 / 409 cua LB -> chuyen nguyen ma + thong bao
    //   401 -> backend cau hinh sai token (khong phai loi cua admin) -> 502
    //   khong ket noi duoc -> 503
    private JsonNode callLb(LbCall call) {
        try {
            String body = call.run();
            return json.readTree(body == null ? "{}" : body);
        } catch (ResourceAccessException e) {
            throw new UpstreamServiceException(503, "Khong ket noi duoc API quan tri cua Load Balancer");
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 401) {
                throw new UpstreamServiceException(502, "Backend cau hinh sai LB_ADMIN_TOKEN");
            }
            throw new UpstreamServiceException(status, lbErrorMessage(e.getResponseBodyAsString()));
        } catch (JsonProcessingException e) {
            throw new UpstreamServiceException(502, "Load Balancer tra ve du lieu khong doc duoc");
        }
    }

    private String lbErrorMessage(String body) {
        try {
            return json.readTree(body).path("error").asText("Load Balancer tu choi yeu cau");
        } catch (JsonProcessingException e) {
            return "Load Balancer tu choi yeu cau";
        }
    }

    private String toJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    @FunctionalInterface
    private interface LbCall {
        String run();
    }

    // Du lieu gop tam thoi cua 1 IP
    private static class Group {
        int count;
        final Set<String> rules = new LinkedHashSet<>();
        String severity;
        final String lastSeen;
        final String lastMessage;

        Group(JsonNode newest) {
            this.severity = newest.path("severity").asText("MEDIUM");
            this.lastSeen = newest.path("@timestamp").asText();
            this.lastMessage = newest.path("message").asText();
        }
    }
}