package com.pbl4.lb.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static net.logstash.logback.argument.StructuredArguments.kv;

public class HealthChecker {
    private static final Logger log = LoggerFactory.getLogger(HealthChecker.class);
    private static final String HEALTH_PATH = "/actuator/health";

    private final List<String> backends;
    private final long intervalSeconds;
    private final int failThreshold;
    private final int passThreshold;
    private final long startupGraceSeconds;
    private final long startedAtNanos = System.nanoTime();
    private volatile Predicate<String> inMaintenance = b -> false;

    private final HttpClient client;
    private final ScheduledExecutorService scheduler;

    private final Map<String, Boolean> up = new HashMap<>();
    private final Map<String, Integer> failStreak = new HashMap<>();
    private final Map<String, Integer> passStreak = new HashMap<>();
    private final Map<String, Boolean> everUp = new HashMap<>();
    private final Map<String, Boolean> downReported = new HashMap<>();

    private volatile List<String> healthy;

    public HealthChecker(List<String> backends, long intervalSeconds,
                         int failThreshold, int passThreshold) {
        this(backends, intervalSeconds, failThreshold, passThreshold, 180);
    }

    public HealthChecker(List<String> backends, long intervalSeconds,
                         int failThreshold, int passThreshold, long startupGraceSeconds) {
        this.backends = backends;
        this.startupGraceSeconds = startupGraceSeconds;
        this.intervalSeconds = intervalSeconds;
        this.failThreshold = failThreshold;
        this.passThreshold = passThreshold;

        for (String b : backends) {
            up.put(b, true);
            failStreak.put(b, 0);
            passStreak.put(b, 0);
            everUp.put(b, false);
            downReported.put(b, false);
        }
        this.healthy = List.copyOf(backends);

        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "health-checker");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        scheduler.scheduleWithFixedDelay(this::checkAll, 0, intervalSeconds, TimeUnit.SECONDS);
    }

    public List<String> healthyBackends() {
        return healthy;
    }

    public void setMaintenanceCheck(Predicate<String> inMaintenance) {
        this.inMaintenance = inMaintenance;
    }

    private void checkAll() {
        try {
            for (String b : backends) {
                update(b, ping(b));
                reportIfNeverStarted(b);
            }
            healthy = backends.stream().filter(up::get).toList();
        } catch (Exception e) {
            log.error("Loi khong mong doi trong vong health check", e);
        }
    }

    private boolean ping(String backend) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(backend + HEALTH_PATH))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();

            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private void update(String backend, boolean ok) {
        if (ok) {
            failStreak.put(backend, 0);
            int passes = passStreak.merge(backend, 1, Integer::sum);
            if (!up.get(backend) && passes >= passThreshold) {
                up.put(backend, true);
                boolean firstTime = !everUp.get(backend);
                logState("backend_up", backend, backend + (firstTime ? " SAN SANG" : " UP")
                        + " (" + passes + " lan thanh cong lien tiep)", passes);
            }
            if (up.get(backend)) {
                everUp.put(backend, true);
                downReported.put(backend, false);
            }
        } else {
            passStreak.put(backend, 0);
            int fails = failStreak.merge(backend, 1, Integer::sum);
            if (up.get(backend) && fails >= failThreshold) {
                up.put(backend, false);
                if (inMaintenance.test(backend)) {
                    logState("backend_maintenance", backend,
                            backend + " dang bao tri (drain), tam DOWN theo ke hoach", fails);
                } else if (everUp.get(backend) || !inStartupGrace()) {
                    downReported.put(backend, true);
                    logState("backend_down", backend,
                            backend + " DOWN (" + fails + " lan that bai lien tiep)", fails);
                } else {
                    logState("backend_waiting", backend,
                            backend + " chua san sang, cho khoi dong (toi da " + startupGraceSeconds + "s)", fails);
                }
            }
        }
    }

    private void reportIfNeverStarted(String backend) {
        if (!up.get(backend) && !everUp.get(backend) && !downReported.get(backend) && !inStartupGrace()) {
            downReported.put(backend, true);
            logState("backend_down", backend, backend + " DOWN (khong khoi dong duoc sau "
                    + startupGraceSeconds + "s)", failStreak.get(backend));
        }
    }

    private boolean inStartupGrace() {
        return System.nanoTime() - startedAtNanos < TimeUnit.SECONDS.toNanos(startupGraceSeconds);
    }

    private void logState(String event, String backend, String msg, int streak) {
        if ("backend_down".equals(event)) {
            log.warn(msg, kv("event", event), kv("backend", backend), kv("streak", streak));
        } else {
            log.info(msg, kv("event", event), kv("backend", backend), kv("streak", streak));
        }
    }
}
