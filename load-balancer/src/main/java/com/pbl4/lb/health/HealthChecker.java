package com.pbl4.lb.health;

//Dinh ky goi /actuator/health cua tung BE
//BE that bai lien tiep failThreshold lan -> DOWN, bi loai khoi vong quay
//BE dang DOWN thanh cong lien tiep passThreshold lan -> UP, duoc dua tro lai

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HealthChecker {
    private static final String HEALTH_PATH = "/actuator/health";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final List<String> backends;
    private final long intervalSeconds;
    private final int failThreshold;
    private final int passThreshold;

    private final HttpClient client;
    private final ScheduledExecutorService scheduler;

    //3 map nay chi do luong health-checker doc va ghi -> khong can dong bo
    private final Map<String, Boolean> up = new HashMap<>();
    private final Map<String, Integer> failStreak = new HashMap<>();
    private final Map<String, Integer> passStreak = new HashMap<>();

    //50 luong xu ly request chi doc bien nay
    private volatile List<String> healthy; //volatile đảm bảo: luồng health check vừa gán danh sách mới,
                                            // mọi luồng khác đọc ngay sau đó chắc chắn thấy bản mới

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

        this.client = HttpClient.newBuilder().
                connectTimeout(Duration.ofSeconds(2))
                .build();

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "health-checker");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        //Chay ngay lan dau (delay 0), sau do cu xong mot vong thi nghi intervalSeconds
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
            //dung danh sach moi roi gan mot lan -> nguoi doc luon thay ban day du
            healthy = backends.stream().filter(up::get).toList();
        } catch (Exception e) {
            System.err.println("[HEALTH] Loi khong mong doi: " + e);
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
        if(ok) {
            failStreak.put(backend, 0);
            int passes = passStreak.merge(backend, 1, Integer::sum);
            if(!up.get(backend) && passes >= passThreshold) {
                up.put(backend, true);
                log(backend + " UP (" +passes + " lan thanh cong lien tiep)");
            }
        } else  {
            passStreak.put(backend, 0);
            int fails = failStreak.merge(backend, 1, Integer::sum);
            if (up.get(backend) && fails >= failThreshold) {
                up.put(backend, false);
                log(backend + " DOWN (" + fails +" lan that bai lien tiep)");
            }
        }
    }

    private void log(String msg) {
        System.out.println(LocalTime.now().format(TIME) + " [HEALTH]" + msg);
    }
}