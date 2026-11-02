package com.pbl4.lb.strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// Dynamic Weighted Least Connection (DWLC)
// Moi request chon backend co diem = soCapNoiDangMo / trongSo thap nhat.
//   trongSo = (latencyThamChieu / latencyEWMA cua backend) * (1 - tiLeLoiEWMA)
public class DwlcStrategy implements LoadBalancingStrategy {

    private static final double ALPHA_LATENCY = 0.2;
    private static final double ALPHA_ERROR = 0.2;
    private static final double MIN_WEIGHT = 0.1;
    private static final double MAX_WEIGHT = 4.0;
    private static final double EPSILON = 1e-9;
    private static final long STALE_NANOS = TimeUnit.SECONDS.toNanos(30);

    private final Map<String, State> states = new ConcurrentHashMap<>();
    private final Object selectLock = new Object();

    @Override
    public String next(List<String> backends) {
        if (backends.isEmpty())
            throw new IllegalStateException("Khong con Backend nao kha dung");

        synchronized (selectLock) {
            long now = System.nanoTime();

            double sum = 0;
            int known = 0;
            for (String b : backends) {
                double lat = state(b).freshLatency(now);
                if (!Double.isNaN(lat)) { sum += lat; known++; }
            }
            double ref = known > 0 ? sum / known : Double.NaN;

            // 1) Least connection co trong so: diem = active / weight, lay diem nho nhat
            double[] weights = new double[backends.size()];
            double best = Double.MAX_VALUE;
            List<Integer> ties = new ArrayList<>();
            for (int i = 0; i < backends.size(); i++) {
                State s = state(backends.get(i));
                weights[i] = s.weight(ref, now);
                double score = s.active.get() / weights[i];
                if (score < best - EPSILON) {
                    best = score;
                    ties.clear();
                    ties.add(i);
                } else if (Math.abs(score - best) <= EPSILON) {
                    ties.add(i);
                }
            }

            // 2) Hoa nhau (vd luc tai thap, moi backend deu active = 0):
            //    Smooth Weighted Round-Robin (nhu Nginx) -> chia theo DUNG ti le trong so.
            //    Backend trong so 0.76 nhan it hon backend 1.0 theo dung ti le, khong bi coi la ngang nhau.
            int chosenIdx = ties.get(0);
            if (ties.size() > 1) {
                double total = 0;
                double bestCurrent = -Double.MAX_VALUE;
                for (int i : ties) {
                    State s = state(backends.get(i));
                    s.current += weights[i];
                    total += weights[i];
                    if (s.current > bestCurrent) {
                        bestCurrent = s.current;
                        chosenIdx = i;
                    }
                }
                state(backends.get(chosenIdx)).current -= total;
            }

            String chosen = backends.get(chosenIdx);
            state(chosen).active.incrementAndGet();
            return chosen;
        }
    }

    @Override
    public void release(String backend, long latencyMs, boolean success) {
        State s = states.get(backend);
        if (s == null) return;
        s.active.updateAndGet(v -> Math.max(0, v - 1));
        s.record(latencyMs, success, System.nanoTime());
    }

    @Override
    public String name() { return "dwlc"; }

    private State state(String backend) {
        return states.computeIfAbsent(backend, k -> new State());
    }

    private static final class State {
        final AtomicInteger active = new AtomicInteger(0);
        double current = 0;             // bo dem cua Smooth WRR, chi dung trong selectLock
        private double latencyEwma = Double.NaN;
        private double errorEwma = 0.0;
        private long lastUpdate = 0;
        private boolean hasData = false;

        synchronized void record(long latencyMs, boolean success, long now) {
            if (success) { // chi do latency khi thanh cong
                double l = Math.max(latencyMs, 1.0);
                latencyEwma = Double.isNaN(latencyEwma)
                        ? l : ALPHA_LATENCY * l + (1 - ALPHA_LATENCY) * latencyEwma;
            }
            errorEwma = ALPHA_ERROR * (success ? 0.0 : 1.0) + (1 - ALPHA_ERROR) * errorEwma;
            lastUpdate = now;
            hasData = true;
        }

        private boolean fresh(long now) { return hasData && now - lastUpdate <= STALE_NANOS; }

        synchronized double freshLatency(long now) { return fresh(now) ? latencyEwma : Double.NaN; }

        synchronized double weight(double ref, long now) {
            if (!fresh(now)) return 1.0;
            double latencyFactor = (Double.isNaN(latencyEwma) || Double.isNaN(ref)) ? 1.0 : ref / latencyEwma;
            double w = latencyFactor * (1.0 - errorEwma);
            return Math.min(MAX_WEIGHT, Math.max(MIN_WEIGHT, w));
        }
    }
}