package com.pbl4.anomaly;

import com.fasterxml.jackson.databind.JsonNode;
import com.pbl4.anomaly.client.ElasticsearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AnomalyDetectorApp {
    private static final Logger log = LoggerFactory.getLogger(AnomalyDetectorApp.class);

    // Chi lay log "request" cua LB: 1 request = 1 dong, khong bi dem trung voi log backend
    private static final String QUERY_TEMPLATE = """
            {
              "size": 0,
              "query": { "bool": { "filter": [
                { "term":  { "instance": "lb" } },
                { "term":  { "event": "request" } },
                { "range": { "@timestamp": { "gte": "now-%ds" } } }
              ]}},
              "aggs": {
                "top_ip":     { "terms": { "field": "clientIp", "size": 5 } },
                "errors_4xx": { "filter": { "range": { "status": { "gte": 400, "lt": 500 } } } },
                "errors_5xx": { "filter": { "range": { "status": { "gte": 500 } } } },
                "latency":    { "percentiles": { "field": "durationMs", "percents": [95] } }
              }
            }
            """;

    public static void main(String[] args) {
        String esUrl = env("ES_URL", "http://localhost:9200");
        int windowSeconds = Integer.parseInt(env("WINDOW_SECONDS", "60"));
        int intervalSeconds = Integer.parseInt(env("INTERVAL_SECONDS", "10"));

        ElasticsearchClient es = new ElasticsearchClient(esUrl);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        log.info("Anomaly detector: ES=" + esUrl + ", cua so " + windowSeconds
                + "s, kiem tra moi " + intervalSeconds + "s");

        scheduler.scheduleWithFixedDelay(() -> {
            try {
                JsonNode res = es.search("weblogs-*", QUERY_TEMPLATE.formatted(windowSeconds));
                printSnapshot(res, windowSeconds);
            } catch (Exception e) {
                // Khong de 1 lan loi (ES chua san sang...) giet ca lich chay dinh ky
                log.error("Loi khi truy van Elasticsearch: " + e.getMessage());
            }
        }, 0, intervalSeconds, TimeUnit.SECONDS);
    }

    private static void printSnapshot(JsonNode res, int windowSeconds) {
        long total = res.path("hits").path("total").path("value").asLong();
        JsonNode aggs = res.path("aggregations");
        long err4xx = aggs.path("errors_4xx").path("doc_count").asLong();
        long err5xx = aggs.path("errors_5xx").path("doc_count").asLong();
        JsonNode p95Node = aggs.path("latency").path("values").path("95.0");
        String p95 = p95Node.isNumber() ? String.format("%.0fms", p95Node.asDouble()) : "-";

        StringBuilder topIp = new StringBuilder();
        for (JsonNode b : aggs.path("top_ip").path("buckets")) {
            topIp.append(b.path("key").asText()).append("=").append(b.path("doc_count").asLong()).append(" ");
        }

        log.info(windowSeconds + "s qua: " + total + " request, 4xx=" + err4xx + ", 5xx=" + err5xx
                + ", p95=" + p95 + ", top IP: " + (topIp.isEmpty() ? "-" : topIp.toString().trim()));
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v.trim();
    }
}