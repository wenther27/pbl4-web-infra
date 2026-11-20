package com.example.cinehub.service;

import com.example.cinehub.dto.request.BlockIpRequest;
import com.example.cinehub.dto.request.RateLimitRequest;
import com.example.cinehub.dto.response.SuspiciousIpDTO;
import com.example.cinehub.exception.BadRequestException;
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
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SecurityAdminService {

    private static final List<String> IP_RULES =
            List.of("HIGH_RATE_IP", "SCAN_PATHS", "BRUTE_FORCE_LOGIN", "ATTACK_SIGNATURE", "ML_IDS");

    private final RestClient es;
    private final RestClient lb;
    private final RestClient mlIds;
    private final RestClient mlIdsEval;
    private final ObjectMapper json;

    public SecurityAdminService(@Value("${app.security.es-url}") String esUrl,
                                @Value("${app.security.lb-admin-url}") String lbAdminUrl,
                                @Value("${app.security.lb-admin-token}") String lbAdminToken,
                                @Value("${app.security.ml-ids-url}") String mlIdsUrl,
                                ObjectMapper json) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(2000);
        timeouts.setReadTimeout(5000);

        this.es = RestClient.builder().baseUrl(esUrl).requestFactory(timeouts).build();
        this.lb = RestClient.builder().baseUrl(lbAdminUrl).requestFactory(timeouts)
                .defaultHeader("X-Admin-Token", lbAdminToken)
                .build();
        this.mlIds = RestClient.builder().baseUrl(mlIdsUrl).requestFactory(timeouts).build();
        SimpleClientHttpRequestFactory slow = new SimpleClientHttpRequestFactory();
        slow.setConnectTimeout(2000);
        slow.setReadTimeout(60000);
        this.mlIdsEval = RestClient.builder().baseUrl(mlIdsUrl).requestFactory(slow).build();
        this.json = json;
    }

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

    public JsonNode blockedIps() {
        return callLb(() -> lb.get().uri("/admin/blocked").retrieve().body(String.class));
    }

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

    public JsonNode unblock(String ip, String admin) {
        return callLb(() -> lb.delete().uri("/admin/blocked/{ip}", ip)
                .header("X-Admin-User", admin)
                .retrieve().body(String.class));
    }

    public JsonNode infraOverview() {
        JsonNode backends = callLb(() -> lb.get().uri("/admin/backends").retrieve().body(String.class));
        JsonNode rateLimit = null;
        try {
            rateLimit = callLb(() -> lb.get().uri("/admin/ratelimit").retrieve().body(String.class));
        } catch (UpstreamServiceException ignored) {
        }
        var result = json.createObjectNode();
        result.set("lb", backends);
        result.set("rateLimit", rateLimit);
        return result;
    }

    public JsonNode mlIdsStatus() {
        var result = json.createObjectNode();
        try {
            String body = mlIds.get().uri("/health").retrieve().body(String.class);
            JsonNode health = json.readTree(body == null ? "{}" : body);
            result.put("up", true);
            result.set("model", health.path("model"));
            result.set("stats", health.path("stats"));
            result.put("pendingFeedback", pendingFeedback(health.path("model").path("trainedAt").asText("")));
        } catch (RestClientException | JsonProcessingException e) {
            result.put("up", false);
            result.put("error", "Khong ket noi duoc dich vu ml-ids");
        }
        try {
            result.set("detections", detections24h());
        } catch (UpstreamServiceException e) {
            result.putNull("detections");
        }
        return result;
    }

    public JsonNode evaluateMlIds(Double threshold) {
        if (threshold != null && !(threshold > 0 && threshold < 1)) {
            throw new BadRequestException("Nguong phai nam trong khoang (0, 1)");
        }
        Map<String, Object> body = new HashMap<>();
        if (threshold != null) {
            body.put("threshold", threshold);
        }
        try {
            String res = mlIdsEval.post().uri("/evaluate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve().body(String.class);
            return json.readTree(res == null ? "{}" : res);
        } catch (ResourceAccessException e) {
            throw new UpstreamServiceException(503, "Dich vu ml-ids khong phan hoi");
        } catch (RestClientResponseException e) {
            throw new UpstreamServiceException(e.getStatusCode().value(), lbErrorMessage(e.getResponseBodyAsString()));
        } catch (JsonProcessingException e) {
            throw new UpstreamServiceException(502, "ml-ids tra ve du lieu khong doc duoc");
        }
    }

    private JsonNode detections24h() {
        String query = """
                {
                  "size": 1000,
                  "_source": ["rule", "target"],
                  "query": { "bool": { "filter": [
                    { "terms": { "rule": ["ATTACK_SIGNATURE", "ML_IDS"] } },
                    { "range": { "@timestamp": { "gte": "now-24h" } } }
                  ]}}
                }
                """;
        Set<String> r8 = new HashSet<>();
        Set<String> r9 = new HashSet<>();
        for (JsonNode hit : callEs(query).path("hits").path("hits")) {
            JsonNode a = hit.path("_source");
            ("ML_IDS".equals(a.path("rule").asText()) ? r9 : r8).add(a.path("target").asText());
        }
        Set<String> onlyR9 = new HashSet<>(r9);
        onlyR9.removeAll(r8);
        var d = json.createObjectNode();
        d.put("r8Ips", r8.size());
        d.put("r9Ips", r9.size());
        d.put("onlyR9Ips", onlyR9.size());
        return d;
    }

    public JsonNode reportFalsePositive(String ip, String admin) {
        String cleanIp = ip.trim();
        double threshold = mlThreshold();

        String query = """
                {
                  "size": 300,
                  "sort": [ { "@timestamp": "desc" } ],
                  "_source": ["path", "query", "body"],
                  "query": { "bool": { "filter": [
                    { "term": { "instance": "lb" } },
                    { "term": { "event": "request" } },
                    { "term": { "clientIp": "%s" } },
                    { "range": { "@timestamp": { "gte": "now-60m" } } }
                  ]}}
                }
                """.formatted(cleanIp);
        Map<String, Map<String, String>> unique = new LinkedHashMap<>();
        for (JsonNode hit : callEs("/weblogs-*/_search", query).path("hits").path("hits")) {
            JsonNode src = hit.path("_source");
            String path = src.path("path").asText("");
            String q = src.path("query").asText("");
            String b = src.path("body").asText("");
            if (!q.isBlank() || !b.isBlank()) {
                unique.putIfAbsent(path + "?" + q + "#" + b, Map.of("path", path, "query", q, "body", b));
            }
        }

        List<Map<String, String>> items = new ArrayList<>(unique.values());
        List<Map<String, String>> flagged = new ArrayList<>();
        if (!items.isEmpty()) {
            var request = json.createObjectNode();
            var arr = request.putArray("requests");
            for (Map<String, String> it : items) {
                String q = it.get("query");
                String b = it.get("body");
                arr.addObject().put("path", it.get("path"))
                        .put("query", q.isEmpty() ? b : b.isEmpty() ? q : q + "&" + b);
            }
            JsonNode scores;
            try {
                String body = mlIds.post().uri("/predict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(toJson(request))
                        .retrieve().body(String.class);
                scores = json.readTree(body == null ? "{}" : body).path("scores");
            } catch (RestClientException | JsonProcessingException e) {
                throw new UpstreamServiceException(503, "Dich vu ml-ids khong phan hoi");
            }
            for (int i = 0; i < items.size() && i < scores.size(); i++) {
                if (scores.get(i).asDouble() >= threshold) {
                    flagged.add(items.get(i));
                }
            }
        }

        if (!flagged.isEmpty()) {
            String now = Instant.now().toString();
            StringBuilder bulk = new StringBuilder();
            for (Map<String, String> it : flagged) {
                Map<String, Object> doc = new LinkedHashMap<>(it);
                doc.put("@timestamp", now);
                doc.put("ip", cleanIp);
                doc.put("by", admin);
                bulk.append("{\"index\":{}}\n").append(toJson(doc)).append('\n');
            }
            try {
                es.post().uri("/ids-feedback/_bulk?refresh=true")
                        .contentType(MediaType.parseMediaType("application/x-ndjson"))
                        .body(bulk.toString())
                        .retrieve().body(String.class);
            } catch (RestClientException e) {
                throw new UpstreamServiceException(503, "Khong ghi duoc phan hoi vao Elasticsearch");
            }
        }

        boolean unblocked = false;
        try {
            unblock(cleanIp, admin);
            unblocked = true;
        } catch (UpstreamServiceException ignored) {
        }

        var result = json.createObjectNode();
        result.put("ip", cleanIp);
        result.put("checked", items.size());
        result.put("saved", flagged.size());
        result.put("unblocked", unblocked);
        result.put("threshold", threshold);
        return result;
    }

    private double mlThreshold() {
        try {
            String body = mlIds.get().uri("/health").retrieve().body(String.class);
            return json.readTree(body == null ? "{}" : body).path("model").path("threshold").asDouble(0.9);
        } catch (RestClientException | JsonProcessingException e) {
            throw new UpstreamServiceException(503, "Dich vu ml-ids khong phan hoi");
        }
    }

    private long pendingFeedback(String trainedAt) {
        String query = trainedAt.isBlank() ? "{}" : """
                { "query": { "range": { "@timestamp": { "gt": "%s", "format": "yyyy-MM-dd HH:mm:ss" } } } }
                """.formatted(trainedAt);
        try {
            String body = es.post().uri("/ids-feedback*/_count")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(query)
                    .retrieve().body(String.class);
            return json.readTree(body == null ? "{}" : body).path("count").asLong(0);
        } catch (RestClientException | JsonProcessingException e) {
            return 0;
        }
    }

    public JsonNode drainBackend(String name, boolean drain, String admin) {
        if (!name.matches("[A-Za-z0-9._-]{1,63}")) {
            throw new UpstreamServiceException(400, "Ten backend khong hop le");
        }
        return callLb(() -> lb.post().uri("/admin/backends/{name}/{action}", name, drain ? "drain" : "undrain")
                .header("X-Admin-User", admin)
                .retrieve().body(String.class));
    }

    public JsonNode updateRateLimit(RateLimitRequest req, String admin) {
        Map<String, Object> body = new HashMap<>();
        body.put("ratePerSecond", req.ratePerSecond());
        body.put("burst", req.burst());
        return callLb(() -> lb.post().uri("/admin/ratelimit")
                .header("X-Admin-User", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().body(String.class));
    }

    private Map<String, String> blockedUntilByIp() {
        Map<String, String> map = new HashMap<>();
        try {
            for (JsonNode e : blockedIps().path("blocked")) {
                map.put(e.path("ip").asText(), e.path("until").asText());
            }
        } catch (UpstreamServiceException ignored) {
        }
        return map;
    }

    private JsonNode callEs(String query) {
        return callEs("/alerts-*/_search", query);
    }

    private JsonNode callEs(String uri, String query) {
        try {
            String body = es.post().uri(uri)
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