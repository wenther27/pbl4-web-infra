package com.pbl4.anomaly.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pbl4.anomaly.alert.Alert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static net.logstash.logback.argument.StructuredArguments.kv;

public class MlIdsRule extends ThresholdRule {
    private static final Logger log = LoggerFactory.getLogger(MlIdsRule.class);

    private static final int CACHE_MAX = 50_000;
    private static final int BATCH = 500;
    private static final int SAMPLE_MAX = 120;
    private static final long WARN_EVERY_MS = 60_000;

    public interface Scorer {
        List<Double> score(List<RequestRecord> batch) throws Exception;
    }

    private final double threshold;
    private final double minMatches;
    private final Scorer scorer;
    private final Map<String, Double> cache = new LinkedHashMap<>(1024, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Double> eldest) {
            return size() > CACHE_MAX;
        }
    };
    private long lastWarn;
    private boolean available = true;

    public MlIdsRule(Map<String, Object> params) {
        this(params, new HttpScorer(url(params), (int) numberOf(params, "timeout_ms", 3000)));
    }

    MlIdsRule(Map<String, Object> params, Scorer scorer) {
        super("ML_IDS", params);
        this.threshold = number("threshold", 0.9);
        this.minMatches = number("min_matches", 1);
        this.scorer = scorer;
    }

    @Override
    public List<Alert> evaluate(Snapshot s) {
        List<RequestRecord> todo = new ArrayList<>();
        Map<String, Boolean> queued = new LinkedHashMap<>();
        for (RequestRecord r : s.recentRequests()) {
            String key = key(r);
            if (skip(r) || cache.containsKey(key) || queued.containsKey(key)) {
                continue;
            }
            queued.put(key, true);
            todo.add(r);
        }

        try {
            for (int i = 0; i < todo.size(); i += BATCH) {
                List<RequestRecord> batch = todo.subList(i, Math.min(todo.size(), i + BATCH));
                List<Double> scores = scorer.score(batch);
                for (int j = 0; j < batch.size(); j++) {
                    cache.put(key(batch.get(j)), scores.get(j));
                }
            }
            if (!available) {
                available = true;
                log.info("ML-IDS hoat dong tro lai", kv("event", "ml_ids_up"));
            }
        } catch (Exception e) {
            available = false;
            long now = System.currentTimeMillis();
            if (now - lastWarn >= WARN_EVERY_MS) {
                lastWarn = now;
                log.warn("Khong goi duoc ML-IDS (" + e.getClass().getSimpleName() + ": " + e.getMessage()
                        + ") -> tam bo qua R9", kv("event", "ml_ids_unavailable"));
            }
        }

        Map<String, Hit> byIp = new LinkedHashMap<>();
        for (RequestRecord r : s.recentRequests()) {
            String ip = r.clientIp();
            if (ip == null || ip.isBlank() || skip(r)) {
                continue;
            }
            Double score = cache.get(key(r));
            if (score == null || score < threshold) {
                continue;
            }
            Hit hit = byIp.computeIfAbsent(ip, k -> new Hit());
            hit.count++;
            if (score > hit.maxScore) {
                hit.maxScore = score;
                hit.sample = sample(r);
            }
        }

        List<Alert> alerts = new ArrayList<>();
        byIp.forEach((ip, hit) -> {
            if (hit.count >= minMatches) {
                alerts.add(alert(ip, String.format(Locale.ROOT,
                        "IP %s gui %d request bi mo hinh IDS danh gia la tan cong (diem cao nhat %.2f, nguong %.2f), vd: %s",
                        ip, hit.count, hit.maxScore, threshold, hit.sample),
                        hit.count, minMatches));
            }
        });
        return alerts;
    }

    private static boolean skip(RequestRecord r) {
        return r.payload().isBlank();
    }

    private static String key(RequestRecord r) {
        return nz(r.path()) + "?" + r.payload();
    }

    private static String sample(RequestRecord r) {
        String text = AttackSignatureRule.decode(r.display()).replaceAll("\\p{Cntrl}", " ");
        return text.length() > SAMPLE_MAX ? text.substring(0, SAMPLE_MAX) + "..." : text;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String url(Map<String, Object> params) {
        String env = System.getenv("ML_IDS_URL");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return String.valueOf(params.getOrDefault("url", "http://ml-ids:8000"));
    }

    private static double numberOf(Map<String, Object> params, String key, double def) {
        return (params.get(key) instanceof Number n) ? n.doubleValue() : def;
    }

    private static final class Hit {
        int count;
        double maxScore = -1;
        String sample = "";
    }

    static final class HttpScorer implements Scorer {
        private static final ObjectMapper JSON = new ObjectMapper();

        private final URI predictUri;
        private final Duration timeout;
        private final HttpClient http;

        HttpScorer(String baseUrl, int timeoutMs) {
            this.predictUri = URI.create(baseUrl.replaceAll("/+$", "") + "/predict");
            this.timeout = Duration.ofMillis(timeoutMs);
            this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
        }

        @Override
        public List<Double> score(List<RequestRecord> batch) throws Exception {
            ObjectNode body = JSON.createObjectNode();
            ArrayNode arr = body.putArray("requests");
            for (RequestRecord r : batch) {
                arr.addObject().put("path", nz(r.path())).put("query", r.payload());
            }
            HttpRequest req = HttpRequest.newBuilder(predictUri)
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                throw new IllegalStateException("HTTP " + res.statusCode() + ": " + res.body());
            }
            JsonNode scores = JSON.readTree(res.body()).path("scores");
            if (!scores.isArray() || scores.size() != batch.size()) {
                throw new IllegalStateException("ML-IDS tra ve sai so luong diem");
            }
            List<Double> out = new ArrayList<>(batch.size());
            scores.forEach(n -> out.add(n.asDouble()));
            return out;
        }
    }
}
