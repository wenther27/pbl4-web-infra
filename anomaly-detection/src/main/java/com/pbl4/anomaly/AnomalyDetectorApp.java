package com.pbl4.anomaly;

import com.pbl4.anomaly.alert.AlertNotifier;
import com.pbl4.anomaly.alert.AutoBlocker;
import com.fasterxml.jackson.databind.JsonNode;
import com.pbl4.anomaly.alert.Alert;
import com.pbl4.anomaly.client.ElasticsearchClient;
import com.pbl4.anomaly.config.RulesConfigLoader;
import com.pbl4.anomaly.config.RulesConfigLoader.DetectorConfig;
import com.pbl4.anomaly.rule.RuleEngine;
import com.pbl4.anomaly.rule.Snapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static net.logstash.logback.argument.StructuredArguments.kv;

public class AnomalyDetectorApp {
    private static final Logger log = LoggerFactory.getLogger(AnomalyDetectorApp.class);

    private static final String QUERY_TEMPLATE = """
            {
              "size": 0,
              "query": { "bool": { "filter": [
                { "term":  { "instance": "lb" } },
                { "range": { "@timestamp": { "gte": "now-%ds" } } }
              ]}},
              "aggs": {
                "req": {
                  "filter": { "term": { "event": "request" } },
                  "aggs": {
                    "by_ip":      { "terms": { "field": "clientIp", "size": 20 } },
                    "errors_4xx": { "filter": { "range": { "status": { "gte": 400, "lt": 500 } } } },
                    "errors_5xx": { "filter": { "range": { "status": { "gte": 500 } } } },
                    "latency":    { "percentiles": { "field": "durationMs", "percents": [95] } },
                    "scan": {
                      "filter": { "terms": { "status": [403, 404] } },
                      "aggs": {
                        "by_ip": {
                          "terms": { "field": "clientIp", "size": 20 },
                          "aggs": { "paths": { "cardinality": { "field": "path" } } }
                        }
                      }
                    },
                    "login_fail": {
                                          "filter": { "bool": { "filter": [
                                            { "term": { "path": "/api/auth/login" } },
                                            { "term": { "status": 401 } }
                                          ]}},
                                          "aggs": { "by_ip": { "terms": { "field": "clientIp", "size": 20 } } }
                                        }
                  }
                },
                "down": {
                  "filter": { "term": { "event": "backend_down" } },
                  "aggs": { "by_backend": { "terms": { "field": "backend", "size": 10 } } }
                }
              }
            }
            """;
    private static final String BASELINE_TEMPLATE = """
            {
              "size": 0,
              "query": { "bool": { "filter": [
                { "term":  { "instance": "lb" } },
                { "term":  { "event": "request" } },
                { "range": { "@timestamp": { "gte": "now-%1$dm/m", "lt": "now-1m/m" } } }
              ]}},
              "aggs": {
                "per_minute": {
                  "date_histogram": {
                    "field": "@timestamp",
                    "fixed_interval": "1m",
                    "min_doc_count": 0,
                    "extended_bounds": { "min": "now-%1$dm/m", "max": "now-2m/m" }
                  }
                }
              }
            }
            """;

    private static final int RECENT_MAX = 5000;
    private static final String RECENT_TEMPLATE = """
            {
              "size": %d,
              "track_total_hits": true,
              "_source": ["@timestamp", "clientIp", "path", "query", "body", "userAgent", "status"],
              "sort": [ { "@timestamp": "desc" } ],
              "query": { "bool": { "filter": [
                { "term":  { "instance": "lb" } },
                { "term":  { "event": "request" } },
                { "range": { "@timestamp": { "gte": "now-%ds" } } }
              ]}}
            }
            """;

    public static void main(String[] args) {
        String esUrl = env("ES_URL", "http://localhost:9200");
        DetectorConfig config = RulesConfigLoader.load();
        int window = config.windowSeconds();

        String baselineQuery = BASELINE_TEMPLATE.formatted(config.baselineMinutes() + 1);

        ElasticsearchClient es = new ElasticsearchClient(esUrl);
        RuleEngine engine = new RuleEngine(config.rules());
        boolean needRecent = engine.ruleIds().contains("ATTACK_SIGNATURE")
                || engine.ruleIds().contains("ML_IDS");
        String recentQuery = RECENT_TEMPLATE.formatted(RECENT_MAX, Math.max(window, config.intervalSeconds() + 5));
        AlertNotifier notifier = new AlertNotifier(es, config.cooldownSeconds(), window);
        AutoBlocker autoBlocker = AutoBlocker.create(config.autoBlock(),
                env("LB_ADMIN_URL", "http://localhost:8090"), System.getenv("LB_ADMIN_TOKEN"));
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        log.info("Anomaly detector: ES=" + esUrl + ", cua so " + window + "s, kiem tra moi "
                        + config.intervalSeconds() + "s, cooldown " + config.cooldownSeconds()
                        + "s, luat: " + engine.ruleIds(),
                kv("event", "detector_start"),
                kv("rules", engine.ruleIds()));

        scheduler.scheduleWithFixedDelay(() -> {
            try {
                notifier.prepare();

                JsonNode res = es.search("weblogs-*", QUERY_TEMPLATE.formatted(window));
                JsonNode baselineRes = es.search("weblogs-*", baselineQuery);
                JsonNode recentRes = needRecent ? es.search("weblogs-*", recentQuery) : null;
                if (recentRes != null
                        && recentRes.path("hits").path("total").path("value").asLong() > RECENT_MAX) {
                    log.warn("R8 chi xet " + RECENT_MAX + " request moi nhat (luu luong qua lon)",
                            kv("event", "r8_truncated"));
                }
                Snapshot snapshot = Snapshot.from(res, baselineRes, recentRes, window);
                log.info(snapshot.summary(), kv("event", "snapshot"), kv("total", snapshot.total()));

                List<Alert> alerts = engine.evaluate(snapshot);
                List<Alert> published = notifier.notify(alerts);
                autoBlocker.handle(published);
            } catch (Exception e) {
                log.error("Loi trong vong phat hien: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }, 0, config.intervalSeconds(), TimeUnit.SECONDS);
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v.trim();
    }
}