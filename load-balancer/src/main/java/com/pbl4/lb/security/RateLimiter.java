package com.pbl4.lb.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static net.logstash.logback.argument.StructuredArguments.kv;

public class RateLimiter {
    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    static final int MAX_TRACKED = 100_000;
    private static final long IDLE_NANOS = 120_000_000_000L;
    private static final long CLEANUP_EVERY_NANOS = 30_000_000_000L;
    private static final long LOG_EVERY_NANOS = 60_000_000_000L;

    public record Limits(double ratePerSecond, int burst) {
        public boolean enabled() {
            return ratePerSecond > 0;
        }
    }

    public record Decision(boolean allowed, int remaining, long retryAfterSeconds) {
        static final Decision UNLIMITED = new Decision(true, -1, 0);
    }

    private static final class Bucket {
        double tokens;
        long lastRefill;
        long rejected;
        boolean logged;
        long lastLog;

        Bucket(double tokens, long now) {
            this.tokens = tokens;
            this.lastRefill = now;
        }
    }

    private volatile Limits limits;
    private final Set<String> exempt;
    private final LongSupplier clock;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong nextCleanup;

    public RateLimiter(Limits limits, Set<String> exempt, LongSupplier clock) {
        this.limits = limits;
        this.exempt = Set.copyOf(exempt);
        this.clock = clock;
        this.nextCleanup = new AtomicLong(clock.getAsLong() + CLEANUP_EVERY_NANOS);
    }

    public static RateLimiter disabled() {
        return new RateLimiter(new Limits(0, 1), Set.of(), System::nanoTime);
    }

    public static RateLimiter fromEnv(Set<String> exempt) {
        double rps = Double.parseDouble(System.getenv().getOrDefault("LB_RATE_LIMIT_RPS", "10").trim());
        int burst = Integer.parseInt(System.getenv().getOrDefault("LB_RATE_LIMIT_BURST", "20").trim());
        Limits limits = validate(rps, burst);
        RateLimiter limiter = new RateLimiter(limits, exempt, System::nanoTime);
        if (limits.enabled()) {
            log.info("Gioi han toc do: " + limits.ratePerSecond() + " request/giay/IP, burst " + limits.burst()
                            + ", khong gioi han: " + exempt,
                    kv("event", "rate_limit_start"),
                    kv("ratePerSecond", limits.ratePerSecond()),
                    kv("burst", limits.burst()));
        } else {
            log.warn("LB_RATE_LIMIT_RPS=0 -> tat gioi han toc do", kv("event", "rate_limit_disabled"));
        }
        return limiter;
    }

    static Limits validate(double rps, int burst) {
        if (Double.isNaN(rps) || rps < 0 || rps > 10_000) {
            throw new IllegalArgumentException("ratePerSecond phai trong khoang 0..10000 (0 = tat)");
        }
        if (burst < 1 || burst > 100_000) {
            throw new IllegalArgumentException("burst phai trong khoang 1..100000");
        }
        return new Limits(rps, burst);
    }

    public Limits limits() {
        return limits;
    }

    public void setLimits(double rps, int burst, String by) {
        Limits next = validate(rps, burst);
        this.limits = next;
        log.warn("Doi gioi han toc do: " + rps + " request/giay/IP, burst " + burst + " (boi " + by + ")",
                kv("event", "rate_limit_changed"),
                kv("ratePerSecond", rps),
                kv("burst", burst),
                kv("by", by));
    }

    public Decision tryAcquire(String ip) {
        Limits l = limits;
        if (!l.enabled() || ip == null || exempt.contains(ip)) {
            return Decision.UNLIMITED;
        }
        long now = clock.getAsLong();
        maybeCleanup(now);

        Bucket b = buckets.get(ip);
        if (b == null) {
            if (buckets.size() >= MAX_TRACKED) {
                return Decision.UNLIMITED;
            }
            b = buckets.computeIfAbsent(ip, k -> new Bucket(l.burst(), now));
        }

        boolean logNow = false;
        Decision decision;
        long rejectedSoFar;
        synchronized (b) {
            double elapsedSeconds = Math.max(0, now - b.lastRefill) / 1_000_000_000.0;
            b.tokens = Math.min(l.burst(), b.tokens + elapsedSeconds * l.ratePerSecond());
            b.lastRefill = now;

            if (b.tokens >= 1) {
                b.tokens -= 1;
                return new Decision(true, (int) Math.floor(b.tokens), 0);
            }

            b.rejected++;
            rejectedSoFar = b.rejected;
            long retryAfter = Math.max(1, (long) Math.ceil((1 - b.tokens) / l.ratePerSecond()));
            decision = new Decision(false, 0, retryAfter);
            if (!b.logged || now - b.lastLog >= LOG_EVERY_NANOS) {
                b.logged = true;
                b.lastLog = now;
                logNow = true;
            }
        }
        if (logNow) {
            log.warn("IP " + ip + " vuot gioi han " + l.ratePerSecond() + " request/giay -> tra 429",
                    kv("event", "rate_limited"),
                    kv("clientIp", ip),
                    kv("ratePerSecond", l.ratePerSecond()),
                    kv("burst", l.burst()),
                    kv("rejectedTotal", rejectedSoFar));
        }
        return decision;
    }

    public Map<String, Object> snapshot() {
        Limits l = limits;
        List<Map<String, Object>> top = buckets.entrySet().stream()
                .filter(e -> e.getValue().rejected > 0)
                .sorted(Comparator.comparingLong((Map.Entry<String, Bucket> e) -> e.getValue().rejected).reversed())
                .limit(10)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("ip", e.getKey());
                    m.put("rejected", e.getValue().rejected);
                    return m;
                })
                .toList();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", l.enabled());
        m.put("ratePerSecond", l.ratePerSecond());
        m.put("burst", l.burst());
        m.put("exempt", exempt);
        m.put("trackedIps", buckets.size());
        m.put("topRejected", top);
        return m;
    }

    int trackedIps() {
        return buckets.size();
    }

    private void maybeCleanup(long now) {
        long due = nextCleanup.get();
        if (now - due < 0 || !nextCleanup.compareAndSet(due, now + CLEANUP_EVERY_NANOS)) {
            return;
        }
        buckets.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                return now - e.getValue().lastRefill >= IDLE_NANOS;
            }
        });
    }
}
