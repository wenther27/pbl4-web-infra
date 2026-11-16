package com.pbl4.anomaly.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.logstash.logback.argument.StructuredArguments.kv;

public class AutoBlocker {
    private static final Logger log = LoggerFactory.getLogger(AutoBlocker.class);

    public static final String BY = "auto-detector";

    public record Config(boolean enabled, boolean dryRun, Set<String> rules,
                         int minutes, int maxPerMinute) {
        public static Config disabled() {
            return new Config(false, false, Set.of(), 15, 10);
        }
    }

    @FunctionalInterface
    public interface LbClient {
        int block(String ip, int minutes, String reason) throws Exception;
    }

    private final Config config;
    private final LbClient lb;
    private final Deque<Instant> recentBlocks = new ArrayDeque<>();
    private Instant lastPauseWarning = Instant.EPOCH;

    public AutoBlocker(Config config, LbClient lb) {
        this.config = config;
        this.lb = lb;
    }

    public static AutoBlocker create(Config config, String lbAdminUrl, String token) {
        if (config.enabled() && (token == null || token.isBlank())) {
            log.warn("auto_block bat nhung thieu LB_ADMIN_TOKEN -> tat tu dong chan",
                    kv("event", "auto_block_disabled"));
            config = new Config(false, config.dryRun(), config.rules(), config.minutes(), config.maxPerMinute());
        }
        if (config.enabled()) {
            log.info("Tu dong chan IP: luat " + config.rules() + ", " + config.minutes() + " phut, toi da "
                            + config.maxPerMinute() + " IP/phut" + (config.dryRun() ? " (DRY RUN - khong chan that)" : ""),
                    kv("event", "auto_block_start"), kv("dryRun", config.dryRun()));
        }
        return new AutoBlocker(config, new HttpLbClient(lbAdminUrl, token));
    }

    public void handle(List<Alert> published) {
        if (!config.enabled()) {
            return;
        }
        Instant now = Instant.now();
        recentBlocks.removeIf(t -> Duration.between(t, now).toSeconds() >= 60);

        for (Alert a : published) {
            if (!config.rules().contains(a.rule()) || !looksLikeIp(a.target())) {
                continue;
            }
            String ip = a.target();

            if (recentBlocks.size() >= config.maxPerMinute()) {
                if (Duration.between(lastPauseWarning, now).toSeconds() >= 60) {
                    lastPauseWarning = now;
                    log.warn("Da tu dong chan " + recentBlocks.size() + " IP trong 60s (toi da "
                                    + config.maxPerMinute() + ") -> tam dung, admin can kiem tra",
                            kv("event", "auto_block_paused"), kv("count", recentBlocks.size()));
                }
                continue;
            }

            String reason = truncate("Tu dong (" + a.rule() + "): " + a.message(), 200);
            if (config.dryRun()) {
                log.info("[DRY RUN] Le ra da chan " + ip + " trong " + config.minutes() + " phut (" + a.rule() + ")",
                        kv("event", "auto_block_dry_run"), kv("rule", a.rule()), kv("clientIp", ip));
                recentBlocks.add(now);
                continue;
            }

            try {
                int status = lb.block(ip, config.minutes(), reason);
                if (status == 201) {
                    recentBlocks.add(now);
                    log.warn("Tu dong chan " + ip + " trong " + config.minutes() + " phut (" + a.rule() + ")",
                            kv("event", "auto_blocked"), kv("rule", a.rule()), kv("clientIp", ip),
                            kv("minutes", config.minutes()));
                } else if (status == 409) {
                    log.info("Khong tu dong chan " + ip + ": IP duoc bao ve",
                            kv("event", "auto_block_skipped"), kv("rule", a.rule()), kv("clientIp", ip));
                } else {
                    log.error("LB tu choi tu dong chan " + ip + ": HTTP " + status,
                            kv("event", "auto_block_failed"), kv("clientIp", ip), kv("status", status));
                }
            } catch (Exception e) {
                log.error("Khong goi duoc LB de tu dong chan " + ip + ": "
                                + e.getClass().getSimpleName() + ": " + e.getMessage(),
                        kv("event", "auto_block_failed"), kv("clientIp", ip));
            }
        }
    }

    static boolean looksLikeIp(String target) {
        return target != null && target.matches("[0-9a-fA-F.:]{2,45}");
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    static final class HttpLbClient implements LbClient {
        private final String baseUrl;
        private final String token;
        private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        private final ObjectMapper json = new ObjectMapper();

        HttpLbClient(String baseUrl, String token) {
            this.baseUrl = baseUrl;
            this.token = token;
        }

        @Override
        public int block(String ip, int minutes, String reason) throws Exception {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ip", ip);
            body.put("minutes", minutes);
            body.put("reason", reason);
            body.put("by", BY);
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/admin/blocked"))
                    .timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/json")
                    .header("X-Admin-Token", token)
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();
            return http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
        }
    }
}
