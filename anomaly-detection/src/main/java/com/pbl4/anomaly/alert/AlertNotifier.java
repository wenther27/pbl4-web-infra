package com.pbl4.anomaly.alert;

import com.pbl4.anomaly.client.ElasticsearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static net.logstash.logback.argument.StructuredArguments.kv;

public class AlertNotifier {
    private static final Logger log = LoggerFactory.getLogger(AlertNotifier.class);

    private static final DateTimeFormatter INDEX_DATE =
            DateTimeFormatter.ofPattern("yyyy.MM.dd").withZone(ZoneOffset.UTC);

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

    private final Map<String, Instant> lastSent = new HashMap<>();
    private boolean templateReady = false;

    public AlertNotifier(ElasticsearchClient es, int cooldownSeconds, int windowSeconds) {
        this.es = es;
        this.cooldown = Duration.ofSeconds(cooldownSeconds);
        this.windowSeconds = windowSeconds;
    }

    public void prepare() throws Exception {
        if (!templateReady) {
            es.putIndexTemplate("alerts", ALERTS_TEMPLATE);
            templateReady = true;
            log.info("Da tao index template alerts-*", kv("event", "alerts_template_ready"));
        }
    }

    public List<Alert> notify(List<Alert> alerts) {
        Instant now = Instant.now();
        lastSent.entrySet().removeIf(e -> Duration.between(e.getValue(), now).compareTo(cooldown) >= 0);

        List<Alert> sent = new ArrayList<>();
        int suppressed = 0;
        for (Alert a : alerts) {
            if (lastSent.containsKey(a.key())) {
                suppressed++;
                continue;
            }
            lastSent.put(a.key(), now);
            publish(a, now);
            sent.add(a);
        }
        if (suppressed > 0) {
            log.info("Bo qua " + suppressed + " canh bao trung (cooldown " + cooldown.toSeconds() + "s)",
                    kv("event", "alert_suppressed"), kv("count", suppressed));
        }
        return sent;
    }

    private void publish(Alert a, Instant now) {
        log.warn("[" + a.severity() + "] " + a.rule() + ": " + a.message(),
                kv("event", "alert"),
                kv("rule", a.rule()),
                kv("severity", a.severity()),
                kv("target", a.target()),
                kv("value", a.value()),
                kv("threshold", a.threshold()));

        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("@timestamp", now.toString());
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
            log.error("Khong ghi duoc canh bao vao Elasticsearch: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}