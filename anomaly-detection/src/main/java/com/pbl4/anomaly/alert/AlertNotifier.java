package com.pbl4.anomaly.alert;

import com.pbl4.anomaly.client.ElasticsearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static net.logstash.logback.argument.StructuredArguments.kv;

// Nhan danh sach Alert tu RuleEngine:
//  1. Loc trung (cooldown): cung luat + cung doi tuong da bao gan day -> bo qua
//  2. Phat: in log WARN + ghi vao Elasticsearch index alerts-YYYY.MM.DD
public class AlertNotifier {
    private static final Logger log = LoggerFactory.getLogger(AlertNotifier.class);

    // Moi ngay 1 index, theo gio UTC giong weblogs-* cua Filebeat
    private static final DateTimeFormatter INDEX_DATE =
            DateTimeFormatter.ofPattern("yyyy.MM.dd").withZone(ZoneOffset.UTC);

    // Kieu du lieu cho alerts-*: keyword de loc/dem, double de ve bieu do
    private static final String ALERTS_TEMPLATE = """
            {
              "index_patterns": ["alerts-*"],
              "priority": 200,
              "template": {
                "settings": { "number_of_shards": 1, "number_of_replicas": 0 },
                "mappings": {
                  "properties": {
                    "@timestamp":    { "type": "date" },
                    "rule":          { "type": "keyword" },
                    "severity":      { "type": "keyword" },
                    "target":        { "type": "keyword" },
                    "message":       { "type": "text" },
                    "value":         { "type": "double" },
                    "threshold":     { "type": "double" },
                    "windowSeconds": { "type": "integer" }
                  }
                }
              }
            }
            """;

    private final ElasticsearchClient es;
    private final Duration cooldown;
    private final int windowSeconds;

    // Khoa canh bao (vd "HIGH_RATE_IP|203.0.113.66") -> lan phat gan nhat.
    // Chi luong detector doc/ghi -> HashMap thuong la du.
    private final Map<String, Instant> lastSent = new HashMap<>();
    private boolean templateReady = false;

    public AlertNotifier(ElasticsearchClient es, int cooldownSeconds, int windowSeconds) {
        this.es = es;
        this.cooldown = Duration.ofSeconds(cooldownSeconds);
        this.windowSeconds = windowSeconds;
    }

    // Goi dau moi vong: tao template alerts-* neu chua co.
    // ES chua san sang thi nem loi, vong sau thu lai.
    public void prepare() throws Exception {
        if (!templateReady) {
            es.putIndexTemplate("alerts", ALERTS_TEMPLATE);
            templateReady = true;
            log.info("Da tao index template alerts-*", kv("event", "alerts_template_ready"));
        }
    }

    // Tra ve so canh bao da phat that su (sau khi loc trung)
    public int notify(List<Alert> alerts) {
        Instant now = Instant.now();
        // Don cac khoa da het cooldown cho Map khong phinh to mai
        lastSent.entrySet().removeIf(e -> Duration.between(e.getValue(), now).compareTo(cooldown) >= 0);

        int sent = 0;
        int suppressed = 0;
        for (Alert a : alerts) {
            if (lastSent.containsKey(a.key())) {
                suppressed++;          // da bao trong cooldown -> cung mot su co
                continue;
            }
            lastSent.put(a.key(), now);
            publish(a, now);
            sent++;
        }
        if (suppressed > 0) {
            log.info("Bo qua " + suppressed + " canh bao trung (cooldown " + cooldown.toSeconds() + "s)",
                    kv("event", "alert_suppressed"), kv("count", suppressed));
        }
        return sent;
    }

    private void publish(Alert a, Instant now) {
        // Kenh 1: log (console, va Kibana neu detector chay trong Docker)
        log.warn("[" + a.severity() + "] " + a.rule() + ": " + a.message(),
                kv("event", "alert"),
                kv("rule", a.rule()),
                kv("severity", a.severity()),
                kv("target", a.target()),
                kv("value", a.value()),
                kv("threshold", a.threshold()));

        // Kenh 2: Elasticsearch -> Kibana doc tu day
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("@timestamp", now.toString());       // ISO-8601 UTC, vd 2026-10-19T09:30:00Z
        doc.put("rule", a.rule());
        doc.put("severity", a.severity());
        doc.put("target", a.target());
        doc.put("message", a.message());
        doc.put("value", a.value());
        doc.put("threshold", a.threshold());
        doc.put("windowSeconds", windowSeconds);
        try {
            es.index("alerts-" + INDEX_DATE.format(now), doc);
        } catch (Exception e) {
            // ES loi thi van con canh bao tren console, khong lam chet vong phat hien
            log.error("Khong ghi duoc canh bao vao Elasticsearch: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}