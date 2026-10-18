package com.pbl4.lb.health;

// Dinh ky goi /actuator/health cua tung backend
// Backend that bai lien tiep failThreshold lan -> DOWN, bi loai khoi vong quay
// Backend dang DOWN thanh cong lien tiep passThreshold lan -> UP, duoc dua tro lai

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

import static net.logstash.logback.argument.StructuredArguments.kv;

public class HealthChecker {
    private static final Logger log = LoggerFactory.getLogger(HealthChecker.class);
    private static final String HEALTH_PATH = "/actuator/health";

    private final List<String> backends;
    private final long intervalSeconds;
    private final int failThreshold;
    private final int passThreshold;

    private final HttpClient client;
    private final ScheduledExecutorService scheduler;

    // 3 map nay chi do luong health-checker doc va ghi -> khong can dong bo
    private final Map<String, Boolean> up = new HashMap<>();
    private final Map<String, Integer> failStreak = new HashMap<>();
    private final Map<String, Integer> passStreak = new HashMap<>();

    // 50 luong xu ly request chi doc bien nay.
    // volatile: luong health check vua gan danh sach moi,
    // moi luong khac doc ngay sau do chac chan thay ban moi
    private volatile List<String> healthy;

    public HealthChecker(List<String> backends, long intervalSeconds,
                         int failThreshold, int passThreshold) {
        this.backends = backends;
        this.intervalSeconds = intervalSeconds;
        this.failThreshold = failThreshold;
        this.passThreshold = passThreshold;

        for (String b : backends) {
            up.put(b, true);        // luc dau tin tat ca deu song
            failStreak.put(b, 0);
            passStreak.put(b, 0);
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
        // Chay ngay lan dau (delay 0), sau do cu xong mot vong thi nghi intervalSeconds
        scheduler.scheduleWithFixedDelay(this::checkAll, 0, intervalSeconds, TimeUnit.SECONDS);
    }

    public List<String> healthyBackends() {
        return healthy;
    }

    private void checkAll() {
        try {
            for (String b : backends) {
                update(b, ping(b));
            }
            // Dung danh sach moi roi gan mot lan -> nguoi doc luon thay ban day du
            healthy = backends.stream().filter(up::get).toList();
        } catch (Exception e) {
            // Truyen e vao cuoi -> logback in ca stack trace (truong "stack_trace" trong JSON)
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
            return false; // Khong ket noi duoc, timeout,... deu tinh la that bai
        }
    }

    private void update(String backend, boolean ok) {
        if (ok) {
            failStreak.put(backend, 0);
            int passes = passStreak.merge(backend, 1, Integer::sum);
            if (!up.get(backend) && passes >= passThreshold) {
                up.put(backend, true);
                logState("backend_up", backend,
                        backend + " UP (" + passes + " lan thanh cong lien tiep)", passes);
            }
        } else {
            passStreak.put(backend, 0);
            int fails = failStreak.merge(backend, 1, Integer::sum);
            if (up.get(backend) && fails >= failThreshold) {
                up.put(backend, false);
                logState("backend_down", backend,
                        backend + " DOWN (" + fails + " lan that bai lien tiep)", fails);
            }
        }
    }

    // DOWN la su co -> WARN; UP la phuc hoi -> INFO
    private void logState(String event, String backend, String msg, int streak) {
        if ("backend_down".equals(event)) {
            log.warn(msg, kv("event", event), kv("backend", backend), kv("streak", streak));
        } else {
            log.info(msg, kv("event", event), kv("backend", backend), kv("streak", streak));
        }
    }
}