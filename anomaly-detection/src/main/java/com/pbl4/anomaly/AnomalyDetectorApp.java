package com.pbl4.anomaly;

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

    // Mot truy van lay du so lieu cho moi luat:
    //  - loc chung: log cua LB trong cua so thoi gian
    //  - "req":  chi dong event=request -> R1 (by_ip), R2 (scan), R4 (errors_5xx), R5 (latency)
    //  - "down": chi dong event=backend_down -> R6
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

    public static void main(String[] args) {
        String esUrl = env("ES_URL", "http://localhost:9200");
        DetectorConfig config = RulesConfigLoader.load();
        int window = config.windowSeconds();

        ElasticsearchClient es = new ElasticsearchClient(esUrl);
        RuleEngine engine = new RuleEngine(config.rules());
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        log.info("Anomaly detector: ES=" + esUrl + ", cua so " + window + "s, kiem tra moi "
                        + config.intervalSeconds() + "s, luat: " + engine.ruleIds(),
                kv("event", "detector_start"),
                kv("rules", engine.ruleIds()));

        scheduler.scheduleWithFixedDelay(() -> {
            try {
                JsonNode res = es.search("weblogs-*", QUERY_TEMPLATE.formatted(window));
                Snapshot snapshot = Snapshot.from(res, window);
                log.info(snapshot.summary(), kv("event", "snapshot"), kv("total", snapshot.total()));

                List<Alert> alerts = engine.evaluate(snapshot);
                for (Alert a : alerts) {
                    log.warn("[" + a.severity() + "] " + a.rule() + ": " + a.message(),
                            kv("event", "alert"),
                            kv("rule", a.rule()),
                            kv("severity", a.severity()),
                            kv("target", a.target()),
                            kv("value", a.value()),
                            kv("threshold", a.threshold()));
                }
            } catch (Exception e) {
                // In ca ten loai loi: "ConnectException: null" de hieu hon "null"
                log.error("Loi trong vong phat hien: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }, 0, config.intervalSeconds(), TimeUnit.SECONDS);
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v.trim();
    }
}