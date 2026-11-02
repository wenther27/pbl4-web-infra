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

// Tu dong chan IP: nhan cac canh bao VUA PHAT (da qua cooldown), canh bao nao thuoc luat
// duoc phep tu dong chan thi goi API quan tri cua LB (cong 8090) de chan IP do.
//
// Chi bat cho luat co do chinh xac cao (mac dinh R8, R3). R1, R2 de admin quyet vi de chan oan
// (nhieu nguoi dung chung 1 IP sau NAT, bot tim kiem gap 404...).
//
// Chot an toan:
//  - enabled / dry_run trong rules.yaml (dry_run: chi ghi log "le ra da chan", khong chan that)
//  - chan co thoi han (minutes), LB tu go khi het han
//  - toi da max_per_minute IP/phut; vuot -> tam dung tu dong chan, bao admin qua log
//  - IP duoc bao ve (LB_NEVER_BLOCK) -> LB tra 409, bo qua
//  - by = "auto-detector" -> trang Bao mat phan biet duoc voi admin chan tay, admin van go duoc
public class AutoBlocker {
    private static final Logger log = LoggerFactory.getLogger(AutoBlocker.class);

    public static final String BY = "auto-detector";

    // Cau hinh doc tu muc auto_block trong rules.yaml
    public record Config(boolean enabled, boolean dryRun, Set<String> rules,
                         int minutes, int maxPerMinute) {
        public static Config disabled() {
            return new Config(false, false, Set.of(), 15, 10);
        }
    }

    // Cach goi LB. Tach thanh interface de test khong can LB that.
    @FunctionalInterface
    public interface LbClient {
        // Tra ve ma HTTP cua LB: 201 da chan, 409 IP duoc bao ve, ...
        int block(String ip, int minutes, String reason) throws Exception;
    }

    private final Config config;
    private final LbClient lb;
    private final Deque<Instant> recentBlocks = new ArrayDeque<>();   // cac lan chan trong 60s gan nhat
    private Instant lastPauseWarning = Instant.EPOCH;

    public AutoBlocker(Config config, LbClient lb) {
        this.config = config;
        this.lb = lb;
    }

    // Tao AutoBlocker goi LB that qua HTTP. Thieu token -> tat, ghi log canh bao.
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

    // published: cac canh bao AlertNotifier vua phat (da loc trung)
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
                // Qua nhieu IP bi chan trong 1 phut -> co the luat dang sai -> dung lai cho admin xem
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
                // LB loi thi van con canh bao, admin chan tay duoc -> khong lam chet vong phat hien
                log.error("Khong goi duoc LB de tu dong chan " + ip + ": "
                                + e.getClass().getSimpleName() + ": " + e.getMessage(),
                        kv("event", "auto_block_failed"), kv("clientIp", ip));
            }
        }
    }

    // R6 co target la URL backend, R4/R5/R7 la "global" -> chi nhan chuoi trong giong IP
    static boolean looksLikeIp(String target) {
        return target != null && target.matches("[0-9a-fA-F.:]{2,45}");
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    // Goi POST /admin/blocked cua LB, giong backend o buoi 2 nhung by = auto-detector
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
