package com.pbl4.anomaly.config;

import com.pbl4.anomaly.rule.BackendDownRule;
import com.pbl4.anomaly.rule.ErrorRate5xxRule;
import com.pbl4.anomaly.rule.HighLatencyRule;
import com.pbl4.anomaly.rule.HighRateIpRule;
import com.pbl4.anomaly.rule.Rule;
import com.pbl4.anomaly.rule.ScanPathsRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Doc rules.yaml -> cau hinh + danh sach luat dang bat.
// Mac dinh doc file trong classpath (src/main/resources/rules.yaml).
// Dat bien RULES_FILE=<duong dan> de doc file ben ngoai (dung cho Docker o buoi 5).
public class RulesConfigLoader {
    private static final Logger log = LoggerFactory.getLogger(RulesConfigLoader.class);

    public record DetectorConfig(int windowSeconds, int intervalSeconds, int cooldownSeconds,
                                 List<Rule> rules) {}

    public static DetectorConfig load() {
        Map<String, Object> root = readYaml();

        int window = intOf(env("WINDOW_SECONDS"), root.get("window_seconds"), 60);
        int interval = intOf(env("INTERVAL_SECONDS"), root.get("interval_seconds"), 10);
        int cooldown = intOf(env("COOLDOWN_SECONDS"), root.get("cooldown_seconds"), 300);
        
        Set<String> whitelist = new HashSet<>();
        if (root.get("whitelist_ips") instanceof List<?> list) {
            for (Object o : list) {
                whitelist.add(String.valueOf(o));
            }
        }

        List<Rule> rules = new ArrayList<>();
        if (root.get("rules") instanceof Map<?, ?> ruleMap) {
            for (Map.Entry<?, ?> e : ruleMap.entrySet()) {
                String id = String.valueOf(e.getKey());
                @SuppressWarnings("unchecked")
                Map<String, Object> params = (e.getValue() instanceof Map<?, ?> m)
                        ? (Map<String, Object>) m : Map.of();

                if (Boolean.FALSE.equals(params.get("enabled"))) {
                    log.info("Luat " + id + " dang tat (enabled: false)");
                    continue;
                }
                Rule rule = create(id, params, whitelist);
                if (rule != null) {
                    rules.add(rule);
                }
            }
        }
        return new DetectorConfig(window, interval, cooldown, rules);
    }

    // "Nha may" tao luat theo ma. Them luat moi: them 1 dong case.
    private static Rule create(String id, Map<String, Object> params, Set<String> whitelist) {
        return switch (id) {
            case "HIGH_RATE_IP"   -> new HighRateIpRule(params, whitelist);
            case "SCAN_PATHS"     -> new ScanPathsRule(params, whitelist);
            case "ERROR_RATE_5XX" -> new ErrorRate5xxRule(params);
            case "HIGH_LATENCY"   -> new HighLatencyRule(params);
            case "BACKEND_DOWN"   -> new BackendDownRule(params);
            default -> {
                log.warn("Bo qua luat khong biet: " + id);
                yield null;
            }
        };
    }

    private static Map<String, Object> readYaml() {
        String file = env("RULES_FILE");
        try (InputStream in = (file != null)
                ? Files.newInputStream(Path.of(file))
                : RulesConfigLoader.class.getResourceAsStream("/rules.yaml")) {
            if (in == null) {
                throw new IllegalStateException("Khong tim thay rules.yaml");
            }
            Map<String, Object> data = new Yaml().load(in);
            return (data != null) ? data : Map.of();
        } catch (IOException e) {
            throw new UncheckedIOException("Khong doc duoc rules.yaml", e);
        }
    }

    // Uu tien bien moi truong, roi den YAML, cuoi cung la mac dinh
    private static int intOf(String envValue, Object yamlValue, int defaultValue) {
        if (envValue != null) {
            return Integer.parseInt(envValue);
        }
        return (yamlValue instanceof Number n) ? n.intValue() : defaultValue;
    }

    private static String env(String key) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? null : v.trim();
    }
}